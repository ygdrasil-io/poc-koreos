@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.input.TextInputConfig
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSRange
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIReturnKeyType
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol

/**
 * Le receiver natif du port texte (Task 2, Step 2) — une sous-classe d'`UITextField` tenue
 * CACHEE dans la hiérarchie du host, receveur d'abord des touches d'un vrai clavier, puis (Task 3)
 * des stimuli UIKeyInput du driver. Le ruling du spike Task 1 (`KadreUikitTextInputSpikeTests`)
 * décide tout :
 *
 * - **`UITextField` CONFIRMÉ** : le marqué programmatique est réel, la suppression enlève la
 *   paire surrogate entière, le focus est déterministe ;
 * - **deux canaux d'édition distincts** : `shouldChangeCharactersInRange` ne part JAMAIS sur les
 *   appels programmatiques (canal utilisateur seul — le hook nomme le RANGE honnête calculé par
 *   UIKit AVANT la modification) ; l'après-modification arrive par `.editingChanged`
 *   (`addTarget` ci-dessous — le spike a prouvé les trois canaux vivants sur édition
 *   programmatique ; le delegate `UITextFieldDelegateProtocol` n'a PAS de `textFieldDidChange`,
 *   fait Task 1 §8). L'ORDRE du brief est appliqué ICI : l'observation est CALCULÉE au
 *   `shouldChange` (la plage honnête, frontière de graphème UIKit), le champ applique (retour
 *   `true`), puis `.editingChanged` COMMITTE l'observation contre le shadow — le port tient le
 *   candidat en attente, le receiver ne porte aucune sémantique de document ;
 * - **la distinction programmatique/utilisateur** est la garde de réentrance
 *   [writeBackInFlight] : le write-back du port ([writeBack]) pose le drapeau PENDANT ses
 *   écritures directes (`text`/`selectedTextRange`), si bien qu'un `.editingChanged` que UIKit
 *   déclencherait en cours d'écriture est identifié comme l'écho du write-back, jamais comme une
 *   édition utilisateur (qui, elle, a toujours son `shouldChange` derrière elle).
 *
 * Le receiver est INVISIBLE sans être `isHidden` : une vue cachée ne peut pas devenir first
 * responder (fait UIKit), le plan « hidden receiver » se réalise donc en `alpha = 0` sur une vue
 * 1×1 à l'origine — invisible ET exclue du touch par le seuil d'alpha d'UIKit, tout en restant
 * focalisable programmatiquement.
 *
 * Les traits de saisie (`keyboardType`, `returnKeyType`, `autocapitalizationType`,
 * `autocorrectionType`, `secureTextEntry`) sont appliqués À LA CONSTRUCTION depuis la table
 * PURE [uikitKeyboardTypeRaw]/[uikitReturnKeyTypeRaw]/[uikitAutocapitalizationTypeRaw]/
 * [uikitAutocorrectionTypeRaw] — le receiver ne mappe rien lui-même. La conversion brute → type
 * du klib passe par `byValue` (déprécié côté K/N mais le SEUL chemin ; supprimé localement par
 * [applyTraits], documenté au rapport).
 *
 * La conformance `UITextFieldDelegateProtocol` est portée par la sous-classe elle-même
 * (`delegate = self`) — le patron kadre-old (`UIKitGestureRecognizerProxy`, protocole sur
 * NSObject) ; seuls les hooks v1 sont redéfinis : `shouldChangeCharactersInRange` (candidat
 * Replace), `textFieldShouldReturn` (action), `textFieldDidBeginEditing`/`DidEndEditing` (focus).
 * `textFieldDidChangeSelection` n'est PAS observé en v1 : un caret déplacé sans édition n'est
 * pas publié (miroirs web/AppKit — la limite est celle des ombres déjà : « the next write-back
 * restores the agreement »).
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@ExportObjCClass
internal class UIKitTextInputReceiver(
    private val config: TextInputConfig,
    private val hooks: Hooks,
) : UITextField(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)), UITextFieldDelegateProtocol {

    /**
     * La surface de callbacks que le port installe — la même forme que le seam élément du web
     * (`WebTextInputElementAccess`/`WebTextInputCallbacks`) : le receiver ne connaît NI le
     * shadow NI la publication, chaque hook rend au port la question qu'UIKit lui a posée.
     */
    internal class Hooks(
        /** Un `shouldChangeCharactersInRange` utilisateur : la plage brute UIKit + le remplacement. */
        val onShouldChange: (UIKitNsRange, String) -> Boolean,
        /** L'après-modification (action `.editingChanged`) — commit du candidat ou écho programmatique. */
        val onTextDidChange: () -> Unit,
        /** La touche retour (`textFieldShouldReturn`). */
        val onReturn: () -> Boolean,
        /** Le champ est devenu first responder (`textFieldDidBeginEditing`). */
        val onFirstResponderGained: () -> Unit,
        /** Le champ a quitté first responder (`textFieldDidEndEditing`). */
        val onFirstResponderLost: () -> Unit,
    )

    /** La garde de réentrance du write-back — posée pendant les écritures directes ([writeBack]). */
    internal var writeBackInFlight: Boolean = false
        private set

    init {
        applyTraits(config)
        alpha = 0.0
        delegate = this
        addTarget(
            this,
            action = NSSelectorFromString(EDITING_CHANGED_ACTION),
            forControlEvents = UIControlEventEditingChanged,
        )
    }

    /**
     * Les traits de la config, appliqués une fois à la construction : le clavier (brute `Long` —
     * le type même du setter du klib), la touche retour, la capitalisation et l'autocorrection
     * (via `byValue`), le masquage de saisie. Une méthode dédiée porte la suppression locale de
     * la dépréciation de `byValue` (l'unique conversion brute → type du klib ; son retrait K/N
     * casserait ICI et seulement ici).
     */
    @Suppress("DEPRECATION")
    private fun applyTraits(config: TextInputConfig) {
        keyboardType = uikitKeyboardTypeRaw(config.purpose)
        returnKeyType = UIReturnKeyType.byValue(uikitReturnKeyTypeRaw(config.action))
        autocapitalizationType = UITextAutocapitalizationType.byValue(
            uikitAutocapitalizationTypeRaw(config.capitalization),
        )
        autocorrectionType = UITextAutocorrectionType.byValue(uikitAutocorrectionTypeRaw(config.autocorrect))
        secureTextEntry = uikitIsSecureEntry(config.purpose)
    }

    /** Le champ vit-il dans une hiérarchie vivante — l'analogue D-X2 du seam élément web. */
    internal val isAddressable: Boolean
        get() = window != null

    /**
     * Le champ tient-il un texte marqué (composition IME VIVANTE) — l'état du CHAMP, lu à chaque
     * hook : c'est la seule vérité de composition en v1 (l'ombre ne marque jamais — le port n'y
     * écrit pas, la garde sur `shadow.markedRange` serait permanente et donc fausse).
     */
    internal val isMarkingText: Boolean
        get() = markedTextRange != null

    /**
     * La sélection COURANTE du champ en offsets UTF-16 (`offsetFromPosition` depuis le début du
     * document) — l'état que la reconciliation du commit IME ([UIKitTextInputOwner] — décision
     * `CompositionCommit` du routeur) lit pour resynchroniser l'ombre. `null` quand le champ ne
     * dit pas de sélection ou d'offsets incroyables (négatifs, inversés) : l'appelant retombe
     * alors sur la fin du texte.
     */
    internal fun currentSelectionUtf16(): TextRange? {
        val selected = selectedTextRange ?: return null
        val begin = beginningOfDocument ?: return null
        val start = offsetFromPosition(begin, toPosition = selected.start)
        val end = offsetFromPosition(begin, toPosition = selected.end)
        if (start < 0L || end < start) return null
        return TextRange(start.toInt(), end.toInt())
    }

    /**
     * Le write-back du port (Task 2, Step 3) : le texte et la sélection posés PROGRAMMATIQUEMENT
     * sous la garde [writeBackInFlight] — jamais un fait observé, toujours l'état que l'ombre
     * vient d'admettre (la sémantique ordre élément-puis-ombre vit dans le port).
     *
     * La sélection est reconstruite par `positionFromPosition`/`textRangeFromPosition` depuis le
     * début du document ; une position irrésolue (champ sans document) laisse la sélection telle
     * quelle — le write-back suivant la retablira, et l'ombre est de toute façon l'état admis.
     */
    internal fun writeBack(text: String, selectionStart: Long, selectionEnd: Long) {
        writeBackInFlight = true
        try {
            this.text = text
            val begin = beginningOfDocument
            val start = begin?.let { positionFromPosition(it, offset = selectionStart) }
            val end = begin?.let { positionFromPosition(it, offset = selectionEnd) }
            val range = if (start != null && end != null) textRangeFromPosition(start, toPosition = end) else null
            if (range != null) selectedTextRange = range
        } finally {
            writeBackInFlight = false
        }
    }

    /**
     * Le hook `.editingChanged` (cible/action posée à l'init) : le canal après-modification du
     * spike — utilisateur ET programmatique. La sémantique (commit du candidat, écho de
     * write-back, fait non attribué) vit dans le port ([Hooks.onTextDidChange]).
     */
    @ObjCAction
    fun kadreEditingChanged(sender: UITextField) {
        hooks.onTextDidChange()
    }

    // MARK: - UITextFieldDelegateProtocol

    /**
     * Le canal utilisateur SEUL (fait du spike : jamais sur appels programmatiques) : la plage
     * brute d'UIKit — `NSUInteger` non signés côté klib, ramenés signés dans [UIKitNsRange]
     * avant toute question à l'ombre (le contrat Task 1) — et le remplacement, rendus au port.
     * Le retour du port est le retour du delegate (`true` en v1 : le champ applique).
     */
    override fun textField(
        textField: UITextField,
        shouldChangeCharactersInRange: CValue<NSRange>,
        replacementString: String,
    ): Boolean = shouldChangeCharactersInRange.useContents {
        hooks.onShouldChange(UIKitNsRange(location.toLong(), length.toLong()), replacementString)
    }

    /** La touche retour : l'action de la config, publiée par le port. */
    override fun textFieldShouldReturn(textField: UITextField): Boolean = hooks.onReturn()

    /** Le champ est devenu first responder — le fait focus du spike, compté par le port. */
    override fun textFieldDidBeginEditing(textField: UITextField) {
        hooks.onFirstResponderGained()
    }

    /** Le champ a quitté first responder. */
    override fun textFieldDidEndEditing(textField: UITextField) {
        hooks.onFirstResponderLost()
    }
}

/** Le sélecteur de l'action `.editingChanged` — la méthode [UIKitTextInputReceiver.kadreEditingChanged] exportée par [ObjCAction]. */
private const val EDITING_CHANGED_ACTION = "kadreEditingChanged:"
