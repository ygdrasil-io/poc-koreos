package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputPurpose
import org.graphiks.kadre.input.TextCapitalization

/**
 * La table PURE config → UIKit du port texte (Task 2, Step 1) : chaque fonction de mapping rend
 * la valeur BRUTE du header — jamais un type `platform.*` — de sorte que ce fichier compile et se
 * teste sans toucher UIKit (les tests `uikitTest` tournent sans appareil), et que le receiver
 * ([UIKitTextInputReceiver]) applique les brutes à la construction. Les brutes sont des `Long`
 * (le brief disait « raw Int » ; l'entier du klib pour `NS_ENUM(NSInteger)` EST le `Long` Kotlin —
 * `UITextField.keyboardType` est typé `Long` côté mapping K/N 2.4.20, vérifié par sonde compile) :
 * convertir en Int ne serait que du bruit aux deux extrémités.
 *
 * Sources des valeurs — header du SDK, `UITextInputTraits.h`
 * (`iPhoneSimulator27.0.sdk/System/Library/Frameworks/UIKit.framework/Headers/UITextInputTraits.h`,
 * lu et documenté au rapport Task 2) :
 *
 * - `UIKeyboardType` : Default=0, ASCIICapable=1, NumbersAndPunctuation=2, URL=3, NumberPad=4,
 *   PhonePad=5, NamePhonePad=6, EmailAddress=7, Twitter=8, DecimalPad=9, WebSearch=10,
 *   ASCIICapableNumberPad=11 ;
 * - `UIReturnKeyType` : Default=0, Go=1, Google=2, Join=3, Next=4, Route=5, Search=6, Send=7,
 *   Yahoo=8, Done=9, EmergencyCall=10, Continue=11 ;
 * - `UITextAutocapitalizationType` : None=0, Words=1, Sentences=2, AllCharacters=3 ;
 * - `UITextAutocorrectionType` : Default=0, No=1, Yes=2.
 *
 * Le brief donnait des brutes pressenties (« Done=1? Go=3? Next=4? Search=6? Send=7? ») — la
 * lecture du header les corrige (Done=9, Go=1 ; Search=6 et Send=7 confirmaient) : ce sont les
 * valeurs du header CI-DESSUS qui font foi, et le test ([UIKitTextInputConfigMappingTest]) les
 * écrit littéralement pour qu'une divergence future échoue avant tout mensonge au champ.
 *
 * « Inconnu → fatal » : les enums Kadre sont clos, le `when` est exhaustif SANS branche `else` —
 * ajouter une constante d'enum sans écrire son mapping est une erreur de COMPILATION (le fatal le
 * plus tôt possible), jamais un `Default` silencieux qui mentirait au champ.
 */

/** `UIKeyboardTypeDefault` — le clavier du method courant (header : cas 0). */
internal const val UI_KEYBOARD_TYPE_DEFAULT = 0L

/** `UIKeyboardTypeNumbersAndPunctuation` — chiffres et ponctuation assortie (header : cas 2). */
internal const val UI_KEYBOARD_TYPE_NUMBERS_AND_PUNCTUATION = 2L

/** `UIKeyboardTypeURL` — optimisé pour les URL, `.com` en évidence (header : cas 3). */
internal const val UI_KEYBOARD_TYPE_URL = 3L

/** `UIKeyboardTypePhonePad` — pavé téléphonique 1-9,*,0,# (header : cas 5). */
internal const val UI_KEYBOARD_TYPE_PHONE_PAD = 5L

/** `UIKeyboardTypeNamePhonePad` — nom ou numéro de téléphone (header : cas 6). */
internal const val UI_KEYBOARD_TYPE_NAME_PHONE_PAD = 6L

/** `UIKeyboardTypeEmailAddress` — optimisé pour le courriel, `@` en évidence (header : cas 7). */
internal const val UI_KEYBOARD_TYPE_EMAIL_ADDRESS = 7L

/** `UIKeyboardTypeDecimalPad` — pavé numérique avec point décimal (header : cas 9). */
internal const val UI_KEYBOARD_TYPE_DECIMAL_PAD = 9L

/**
 * Le clavier du purpose : Text/Password → le clavier par défaut, Email/Url/Telephone/Number/
 * Decimal → le clavier spécialisé du header, Name → `NamePhonePad` (nom OU téléphone — la
 * sémantique du header colle au purpose). `Number` prend `NumbersAndPunctuation` et non
 * `NumberPad` : le pavé chiffres-seuls ne sait pas dire le signe moins ni les séparateurs qu'une
 * application numérique peut vouloir (le brief prescrivait cette case).
 */
internal fun uikitKeyboardTypeRaw(purpose: TextInputPurpose): Long = when (purpose) {
    TextInputPurpose.Text -> UI_KEYBOARD_TYPE_DEFAULT
    TextInputPurpose.Name -> UI_KEYBOARD_TYPE_NAME_PHONE_PAD
    TextInputPurpose.Email -> UI_KEYBOARD_TYPE_EMAIL_ADDRESS
    TextInputPurpose.Url -> UI_KEYBOARD_TYPE_URL
    TextInputPurpose.Telephone -> UI_KEYBOARD_TYPE_PHONE_PAD
    TextInputPurpose.Number -> UI_KEYBOARD_TYPE_NUMBERS_AND_PUNCTUATION
    TextInputPurpose.Decimal -> UI_KEYBOARD_TYPE_DECIMAL_PAD
    TextInputPurpose.Password -> UI_KEYBOARD_TYPE_DEFAULT
}

/**
 * L'entrée sécurisée (`secureTextEntry` du header) : le Password seul la porte — le masquage est
 * la sémantique du purpose, jamais un effet de bord du clavier choisi.
 */
internal fun uikitIsSecureEntry(purpose: TextInputPurpose): Boolean = purpose == TextInputPurpose.Password

/** `UIReturnKeyDefault` (header : cas 0). */
internal const val UI_RETURN_KEY_DEFAULT = 0L

/** `UIReturnKeyGo` (header : cas 1). */
internal const val UI_RETURN_KEY_GO = 1L

/** `UIReturnKeyNext` (header : cas 4). */
internal const val UI_RETURN_KEY_NEXT = 4L

/** `UIReturnKeySearch` (header : cas 6). */
internal const val UI_RETURN_KEY_SEARCH = 6L

/** `UIReturnKeySend` (header : cas 7). */
internal const val UI_RETURN_KEY_SEND = 7L

/** `UIReturnKeyDone` (header : cas 9 — PAS 1, lecture du header contre le pressenti du brief). */
internal const val UI_RETURN_KEY_DONE = 9L

/** Le libellé de la touche retour : l'action Kadre porte son cas exact du header. */
internal fun uikitReturnKeyTypeRaw(action: TextInputAction): Long = when (action) {
    TextInputAction.Default -> UI_RETURN_KEY_DEFAULT
    TextInputAction.Done -> UI_RETURN_KEY_DONE
    TextInputAction.Go -> UI_RETURN_KEY_GO
    TextInputAction.Next -> UI_RETURN_KEY_NEXT
    TextInputAction.Search -> UI_RETURN_KEY_SEARCH
    TextInputAction.Send -> UI_RETURN_KEY_SEND
}

/** `UITextAutocapitalizationTypeNone` (header : cas 0). */
internal const val UI_TEXT_AUTOCAPITALIZATION_TYPE_NONE = 0L

/** `UITextAutocapitalizationTypeWords` (header : cas 1). */
internal const val UI_TEXT_AUTOCAPITALIZATION_TYPE_WORDS = 1L

/** `UITextAutocapitalizationTypeSentences` (header : cas 2). */
internal const val UI_TEXT_AUTOCAPITALIZATION_TYPE_SENTENCES = 2L

/** `UITextAutocapitalizationTypeAllCharacters` (header : cas 3). */
internal const val UI_TEXT_AUTOCAPITALIZATION_TYPE_ALL_CHARACTERS = 3L

/** Le régime de capitalisation demandé : les quatre cas du header, dans leur ordre. */
internal fun uikitAutocapitalizationTypeRaw(capitalization: TextCapitalization): Long = when (capitalization) {
    TextCapitalization.None -> UI_TEXT_AUTOCAPITALIZATION_TYPE_NONE
    TextCapitalization.Words -> UI_TEXT_AUTOCAPITALIZATION_TYPE_WORDS
    TextCapitalization.Sentences -> UI_TEXT_AUTOCAPITALIZATION_TYPE_SENTENCES
    TextCapitalization.Characters -> UI_TEXT_AUTOCAPITALIZATION_TYPE_ALL_CHARACTERS
}

/** `UITextAutocorrectionTypeNo` (header : cas 1). */
internal const val UI_TEXT_AUTOCORRECTION_TYPE_NO = 1L

/** `UITextAutocorrectionTypeYes` (header : cas 2). */
internal const val UI_TEXT_AUTOCORRECTION_TYPE_YES = 2L

/**
 * La demande d'autocorrection : `true` → `Yes` (la demande explicite de l'application, posée
 * telle quelle — PAS `Default`, qui délèguerait aux réglages et effacerait la volonté du
 * config), `false` → `No`.
 */
internal fun uikitAutocorrectionTypeRaw(autocorrect: Boolean): Long =
    if (autocorrect) UI_TEXT_AUTOCORRECTION_TYPE_YES else UI_TEXT_AUTOCORRECTION_TYPE_NO
