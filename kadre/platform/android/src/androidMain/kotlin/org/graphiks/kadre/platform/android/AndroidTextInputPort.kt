package org.graphiks.kadre.platform.android

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.TextInputCursorCommand
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.internal.runtime.TextInputPort
import org.graphiks.kadre.surface.LogicalRect

/**
 * Le pont IME kadre-old transposé : les événements IME bruts que l'IME livre à la
 * `BaseInputConnection` (SDK — l'implémentation concrète de la connexion vit côté SDK et ne
 * peut être ni testée en JVM hôte ni atteinte sans la décision normative) sont transposés
 * ici en `TextInputObservation` vers la vraie `RuntimeTextInputSession`. INTERNAL, sans
 * chemin d'activation public — la base concrète de la proposition normative (hook
 * host-délégué : la sous-classe de View du HOST override `onCreateInputConnection` /
 * `onCheckIsTextEditor` et délègue à un helper kadre — le fait central de la phase 5 est
 * que kadre ne possède ni ne sous-classe la View de l'hôte, donc AUCUN canal d'intégration
 * conforme n'existe sur la surface d'attach actuelle), garée pour le partenaire utilisateur.
 *
 * La capability reste `Unsupported(TextInput)` TOUJOURS : le réducteur consulte la
 * capability AVANT `open` (`RuntimeSurfaceInput:124-126`) et refuse `openTextInput` on-row
 * sans jamais appeler [open] — le port n'est donc jamais ouvert par la voie publique, et
 * chaque règle qu'il porte est prouvée contre la vraie session par le chemin exact que le
 * runtime construit lui-même (`AndroidTextInputPortTest`, friend du runtime). Les règles
 * sont le miroir EXACT du port web (`WebTextInputPort:277-374`) : commit → Replace,
 * composition en trois faits (départ = substitution vide à la sélection, mise à jour = span
 * précédent, sélection = fin du composé — jamais une sélection de document fabriquée), fin
 * à chaîne finale différente → Replace puis terminal ; le delete SURROUNDING est calculé
 * depuis le document courant, tandis que les deletes PAR TOUCHE
 * (`deleteContentBackward`/`Forward` — des keycodes, pas des appels InputConnection) ne
 * sont PAS mappés : une suppression retire un cluster de graphèmes et kadre n'embarque pas
 * la segmentation — l'honnêteté du web (:264-272).
 */
internal class AndroidTextInputPort(
    /**
     * La source de stamps de la session — le même `source` que le réducteur d'entrée et le
     * moteur d'interactions reçoivent à l'install. Le port lui-même n'est JAMAIS
     * émetteur de stamps (les observations portent la révision, le runtime assigne le
     * stamp — `TextInputPort.kt:56`) : la source voyage ici parce que c'est la SEULE valeur
     * dont l'activation normative aurait besoin au-delà de l'entrée, au même titre que le
     * port web ne porte rien de plus que son accès élément.
     */
    private val stampSource: () -> EventStamp,
) : TextInputPort {
    override val capability: Capability<Unit> = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput))

    /** Le seul owner vivant de ce port. Le runtime n'admet qu'une session par surface avant d'appeler. */
    private var liveOwner: AndroidTextInputOwner? = null

    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
        liveOwner?.let { return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession)) }
        val owner = AndroidTextInputOwner(
            config = command.config,
            onObservation = command.onObservation,
            onClosed = { closed -> if (liveOwner === closed) liveOwner = null },
        )
        liveOwner = owner
        return KadreResult.Success(owner)
    }

    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> {
        val owner = command.owner as? AndroidTextInputOwner
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("textInputOwner"))
        if (liveOwner !== owner || owner.isClosed) return textInputClosedFailure()
        return owner.applyCursor(command.rect, command.documentRevision)
    }

    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> {
        val owner = command.owner as? AndroidTextInputOwner
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("textInputOwner"))
        if (liveOwner !== owner || owner.isClosed) return textInputClosedFailure()
        return owner.applyDocument(command.text, command.selection, command.documentRevision)
    }

    private fun textInputClosedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
}

/**
 * Le mapping PUR — événements IME bruts → observations. Chaque fonction reçoit les
 * primitives que l'IME a livrées ET l'état courant du document que l'owner tient (l'ombre
 * [AndroidTextInputShadow]), et rend l'observation — ou rien, quand l'événement n'a rien à
 * dire. Aucun état, aucun effet : le owner décide QUAND appeler, le mapper décide CE QUE
 * l'événement signifie (le mot du port web : l'édition que l'événement décrit, calculée
 * contre l'ombre).
 */
internal class AndroidImeEventMapper {

    /**
     * Un `commitText` : le Replace de la range que le commit remplit — la sélection
     * courante quand rien ne se composait, le span de la composition quand l'IME commite
     * par-dessus (le SDK retire le texte composé de lui-même). Les offsets sont des unités
     * UTF-16 du document courant (TextRange = Kotlin String, DESIGN §10.3) ; le texte
     * courant ne fait que porter l'invariant — une sélection hors document est une erreur
     * de programmation de l'owner, jamais une observation à publier.
     */
    public fun commit(
        text: String,
        baseRevision: Long,
        currentText: String,
        selection: TextRange,
    ): TextInputObservation {
        require(selection.endExclusiveUtf16 <= currentText.length) {
            "the commit range must live in the document the port holds"
        }
        return TextInputObservation.Replace(selection, text, TextDocumentRevision(baseRevision))
    }

    /**
     * Un `setComposingText` — le départ ET la mise à jour de la composition dans le seul
     * appel que l'IME livre (le web a deux événements DOM, l'IME Android un seul) :
     *
     * - sans composition courante, la range est la SÉLECTION — la substitution vide de la
     *   sélection est le fait de départ (le miroir web :327-333), et le premier texte
     *   composé arrive substitué à cette même sélection ;
     * - avec une composition courante, la range est son span PRÉCÉDENT : la substitution
     *   que l'application accepte doit remplacer CE span pour atteindre l'instantané que
     *   la session rebasse (le miroir web :335-352).
     *
     * La sélection DANS le texte composé est sa fin : le caret que les IME y posent, et
     * jamais une sélection du document fabriquée (DESIGN §10.3). [end] — la position de
     * curseur que l'IME a nommée (`newCursorPosition`) — voyage pour la fidélité de la
     * forme SDK ; le contrat l'épingle à la fin du composé, exactement comme le web qui ne
     * reçoit pas de curseur du tout.
     */
    public fun composition(
        text: String?,
        end: Int,
        baseRevision: Long,
        currentComposition: TextRange?,
        selection: TextRange,
    ): TextInputObservation {
        val composed = text ?: ""
        val caret = composed.length
        return TextInputObservation.CompositionChanged(
            range = currentComposition ?: selection,
            text = composed,
            selection = TextRange(caret, caret),
            baseRevision = TextDocumentRevision(baseRevision),
        )
    }

    /**
     * Un `deleteSurroundingText(before, after)` : le Replace CALCULÉ des unités que
     * l'événement nomme — avant le caret borné au début du document, après le caret borné
     * à sa fin. Un delete qui ne retire rien ne dit rien (`null`) : une observation vide
     * serait un événement fabriqué. Les deletes PAR TOUCHE restent hors de ce mapping —
     * ils vivent sur le canal des touches, où l'honnêteté des graphèmes du web tient
     * (`WebTextInputPort:264-272`).
     */
    public fun deleteSurrounding(
        before: Int,
        after: Int,
        baseRevision: Long,
        currentText: String,
        selection: TextRange,
    ): TextInputObservation? {
        require(selection.endExclusiveUtf16 <= currentText.length) {
            "the delete range must live in the document the port holds"
        }
        val removalBefore = before.coerceAtLeast(0).coerceAtMost(selection.startUtf16)
        val removalAfter = after.coerceAtLeast(0).coerceAtMost(currentText.length - selection.endExclusiveUtf16)
        val range = TextRange(
            selection.startUtf16 - removalBefore,
            selection.endExclusiveUtf16 + removalAfter,
        )
        if (range.startUtf16 == range.endExclusiveUtf16) return null
        return TextInputObservation.Replace(range, "", TextDocumentRevision(baseRevision))
    }

    /**
     * Un `finishComposingText` : le terminal de la composition — range et sélection tous
     * deux nuls — quand une composition existe, et RIEN sinon (aucun événement fabriqué,
     * le cas silencieux du bullet du gate). Le `finishComposingText` de l'IME ne porte
     * jamais de texte : le composé est DÉJÀ dans le document, la fin n'a qu'à terminer.
     */
    public fun finishComposition(
        baseRevision: Long,
        currentComposition: TextRange?,
        currentText: String,
    ): TextInputObservation? {
        val range = currentComposition ?: return null
        require(range.endExclusiveUtf16 <= currentText.length) {
            "the composition span must live in the document the port holds"
        }
        return TextInputObservation.CompositionChanged(null, "", null, TextDocumentRevision(baseRevision))
    }
}

/** Le résultat de la reconciliation d'un instantané runtime avec l'ombre — le miroir web exact. */
internal enum class AndroidTextInputDocumentUpdate {
    Applied,
    Stale,
    CompositionActive,
}

/**
 * L'ombre Kotlin immédiate du document que la surface tient, calculée depuis la config à
 * l'open et avancée par les éditions que le pont observe et les instantanés que
 * l'application accepte — le précédent `WebTextInputShadow` sans l'écriture élément :
 * kadre ne possède pas la View de l'hôte (le fait central de la phase 5), il n'y a RIEN à
 * écrire en retour, et l'ombre est la seule source d'offsets que le port calcule jamais.
 * La révision reste celle de l'application — la dernière acceptée — et chaque observation
 * est marquée d'elle.
 */
internal class AndroidTextInputShadow(config: TextInputConfig) {
    var text: String = config.surroundingText
        private set

    var selection: TextRange = config.selection
        private set

    var documentRevision: TextDocumentRevision = config.documentRevision
        private set

    /** Le rect de curseur que l'application a accepté en dernier — stocké sans effet natif. */
    var cursorRect: LogicalRect? = null
        private set

    /** Le span de la composition dans le texte, ou `null` quand aucune n'est active. */
    var markedRange: TextRange? = null
        private set

    /** Le texte que la composition tient actuellement, ou `null` quand aucune n'est active. */
    val composedText: String?
        get() = markedRange?.let { range -> text.substring(range.startUtf16, range.endExclusiveUtf16) }

    /** Accepte le rect de curseur à la révision exacte courante ; sans effet natif (limite enregistrée). */
    fun applyCursor(rect: LogicalRect, revision: TextDocumentRevision): Boolean {
        if (revision != documentRevision) return false
        cursorRect = rect
        return true
    }

    /**
     * Applique un instantané accepté. Le span de la composition N'EST PAS touché : la
     * session ne peut accepter, pendant une composition, qu'un instantané qui rebasse la
     * composition — la substitution du MÊME texte composé à la MÊME origine — donc le span
     * que l'ombre tient reste le bon (le miroir web, prouvé par le test du rebasage).
     */
    fun applyDocument(
        text: String,
        selection: TextRange,
        revision: TextDocumentRevision,
    ): AndroidTextInputDocumentUpdate {
        if (revision.value < documentRevision.value) return AndroidTextInputDocumentUpdate.Stale
        if (markedRange != null && text != this.text) return AndroidTextInputDocumentUpdate.CompositionActive
        this.text = text
        this.selection = selection
        documentRevision = revision
        return AndroidTextInputDocumentUpdate.Applied
    }

    fun replaceText(range: TextRange, replacement: String): Boolean {
        if (range.endExclusiveUtf16 > text.length) return false
        text = text.replace(range, replacement)
        val insertion = range.startUtf16 + replacement.length
        selection = TextRange(insertion, insertion)
        markedRange = null
        return true
    }

    fun setMarkedText(
        replacementRange: TextRange,
        markedText: String,
        selectedRange: TextRange,
    ): Boolean {
        if (replacementRange.endExclusiveUtf16 > text.length) return false
        if (selectedRange.endExclusiveUtf16 > markedText.length) return false
        text = text.replace(replacementRange, markedText)
        val markedStart = replacementRange.startUtf16
        markedRange = TextRange(markedStart, markedStart + markedText.length)
        selection = TextRange(
            markedStart + selectedRange.startUtf16,
            markedStart + selectedRange.endExclusiveUtf16,
        )
        return true
    }

    fun clearMarkedText(): Boolean {
        if (markedRange == null) return false
        markedRange = null
        return true
    }
}

/** Remplace [range] par [replacement] dans cette chaîne, par offsets UTF-16. */
private fun String.replace(range: TextRange, replacement: String): String =
    substring(0, range.startUtf16) + replacement + substring(range.endExclusiveUtf16)

/**
 * Le seul owner texte vivant qu'un [open] du port rend : l'ombre, les points d'entrée IME
 * et la publication d'observations d'une ouverture — la forme transposée de la
 * `KadreInputConnection` kadre-old, dont chaque override SDK devient une fonction du
 * owner que la future connexion concrète (côté SDK, décision normative) appellera :
 *
 * | Override `BaseInputConnection` (kadre-old) | Point d'entrée ici          | Observation                  |
 * |--------------------------------------------|-----------------------------|------------------------------|
 * | `commitText`                               | [commitText]                | `Replace`, puis le terminal — le Replace seulement à chaîne finale différente du composé |
 * | `setComposingText`                         | [setComposingText]          | `CompositionChanged`         |
 * | `finishComposingText`                      | [finishComposingText]       | terminal, ou rien            |
 * | `deleteSurroundingText`                    | [deleteSurroundingText]     | `Replace` calculé, ou rien   |
 * | `closeConnection`                          | [closeConnection]           | — le Disable : l'owner ferme |
 *
 * Le `Enabled` posté à la construction kadre-old n'a pas d'homologue : le runtime construit
 * la session APRÈS l'open réussi, l'état actif EST le fait d'ouverture. La callback
 * d'observation appartient à la session de CET open (le holder du runtime) — un owner
 * fermé ne publie plus rien, et les commandes du port répondent `Closed` à partir de là.
 */
internal class AndroidTextInputOwner(
    private val config: TextInputConfig,
    private val onObservation: (TextInputObservation) -> Boolean,
    private val onClosed: (AndroidTextInputOwner) -> Unit,
) : TextInputOwner {
    private val shadow = AndroidTextInputShadow(config)
    private val mapper = AndroidImeEventMapper()

    private var closed = false

    val isClosed: Boolean
        get() = closed

    // ---------------------------------------------------------------
    // Les points d'entrée IME — l'ensemble d'overrides transposé
    // ---------------------------------------------------------------

    /**
     * Un `commitText` de l'IME. Sans composition : le Replace de la sélection courante.
     * Par-dessus une composition, le commit EST la fin de composition qui porte une chaîne
     * finale — le miroir exact de la fin web (:355-374) : une chaîne finale qui DIFFÈRE du
     * composé est la dernière édition de l'IME et arrive comme le `Replace` qu'elle est — le
     * span de la composition remplacé, puis le terminal — tandis que la chaîne qui EST le
     * composé (la forme commune) ne change rien : le composé est déjà dans le document,
     * le terminal seul termine.
     */
    public fun commitText(text: String?) {
        if (closed) return
        val committed = text ?: ""
        val marked = shadow.markedRange
        if (marked == null) {
            val range = shadow.selection
            val before = shadow.text
            if (shadow.replaceText(range, committed)) {
                publish(mapper.commit(committed, shadow.documentRevision.value, before, range))
            }
        } else {
            // Le span et le texte d'AVANT l'édition : le replace de l'ombre efface la
            // composition, mais le terminal porte le span qu'elle occupait.
            val before = shadow.text
            if (committed != shadow.composedText) {
                if (shadow.replaceText(marked, committed)) {
                    publish(mapper.commit(committed, shadow.documentRevision.value, before, marked))
                }
            }
            publishTerminal(marked, before)
        }
    }

    /**
     * Un `setComposingText` de l'IME — le départ (aucune composition : la substitution
     * part de la sélection) comme la mise à jour (le span précédent est remplacé). L'ombre
     * est appliquée D'ABORD : une ombre qui refuse (range hors document) ne publie rien —
     * l'observation que le runtime refuserait n'est jamais partie.
     */
    public fun setComposingText(text: String?, newCursorPosition: Int) {
        if (closed) return
        val composed = text ?: ""
        val marked = shadow.markedRange
        val selection = shadow.selection
        val replacementRange = marked ?: selection
        // La sélection dans le composé est sa fin — le contrat du mapper, appliqué à
        // l'ombre pour qu'elle reste l'exact miroir de ce qui est publié.
        if (shadow.setMarkedText(replacementRange, composed, TextRange(composed.length, composed.length))) {
            publish(mapper.composition(composed, newCursorPosition, shadow.documentRevision.value, marked, selection))
        }
    }

    /**
     * Un `finishComposingText` de l'IME : le terminal quand une composition existe, RIEN
     * sinon — le cas silencieux du bullet du gate (aucun événement fabriqué).
     */
    public fun finishComposingText() {
        if (closed) return
        publishTerminal(shadow.markedRange, shadow.text)
    }

    /**
     * Un `deleteSurroundingText(before, after)` de l'IME : le Replace calculé. PENDANT une
     * composition, il ne dit rien — un delete dans la composition est le fait PROPRE de la
     * composition (les IME la raccourcissent par `setComposingText`), le miroir de la
     * règle web : un `beforeinput` intérieur à une composition n'est jamais une
     * observation de son propre chef.
     */
    public fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        if (closed) return
        if (shadow.markedRange != null) return
        val selection = shadow.selection
        val before = shadow.text
        val observation = mapper.deleteSurrounding(
            beforeLength,
            afterLength,
            shadow.documentRevision.value,
            before,
            selection,
        ) ?: return
        // Le delete mapper ne répond qu'un Replace : la range qu'il a calculée EST l'édition.
        val range = (observation as TextInputObservation.Replace).range
        if (shadow.replaceText(range, "")) {
            publish(observation)
        }
    }

    /**
     * Un `closeConnection` de l'IME : le Disable du pont transposé. Aucune observation
     * n'exprime le disable — le owner cesse de servir, la callback est révoquée (le contrat
     * du SPI : `TextInputPort.kt:17`), et les commandes du port répondent `Closed` à
     * partir de là. La forme normative porterait le fait au travers du canal fenêtre.
     */
    public fun closeConnection() {
        close()
    }

    // ---------------------------------------------------------------
    // Les commandes du port — le côté application
    // ---------------------------------------------------------------

    /** Accepte le rect de curseur à la révision exacte de l'ombre ; sans aucun effet natif. */
    public fun applyCursor(rect: LogicalRect, revision: TextDocumentRevision): KadreResult<Unit> =
        if (shadow.applyCursor(rect, revision)) {
            KadreResult.Success(Unit)
        } else {
            KadreResult.Failure(
                KadreFailure.StaleRevision(shadow.documentRevision.value, revision.value),
            )
        }

    /**
     * Applique un instantané accepté à l'ombre. Android n'a AUCUNE écriture élément (kadre
     * ne possède pas la View — le fait central de la phase 5) : l'acceptation EST la mise à
     * jour de l'ombre, et ses refus — `Stale`, `CompositionActive` — sont inatteignables
     * derrière l'admission propre du runtime, répondu par les failures qui les nomment (le
     * précédent web :224-227).
     */
    public fun applyDocument(
        text: String,
        selection: TextRange,
        revision: TextDocumentRevision,
    ): KadreResult<Unit> = when (shadow.applyDocument(text, selection, revision)) {
        AndroidTextInputDocumentUpdate.Applied -> KadreResult.Success(Unit)
        AndroidTextInputDocumentUpdate.Stale -> KadreResult.Failure(
            KadreFailure.StaleRevision(shadow.documentRevision.value, revision.value),
        )

        AndroidTextInputDocumentUpdate.CompositionActive -> KadreResult.Failure(KadreFailure.InvalidRequest("text"))
    }

    // ---------------------------------------------------------------
    // Le cycle de vie
    // ---------------------------------------------------------------

    public override fun close() {
        if (closed) return
        closed = true
        onClosed(this)
    }

    /** Le terminal de la composition — le span porté est celui que la composition occupait. */
    private fun publishTerminal(marked: TextRange?, before: String) {
        shadow.clearMarkedText()
        mapper.finishComposition(shadow.documentRevision.value, marked, before)?.let(::publish)
    }

    /** Publie une observation, jusqu'à ce que l'owner soit fermé. */
    private fun publish(observation: TextInputObservation) {
        if (closed) return
        onObservation(observation)
    }
}
