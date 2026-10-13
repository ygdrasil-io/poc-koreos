package org.graphiks.kadre.internal.uikit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.TextInputCursorCommand
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.internal.runtime.TextInputPort
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.LogicalRect
import platform.Foundation.NSThread
import platform.UIKit.UIView
import platform.darwin.dispatch_sync
import platform.darwin.dispatch_get_main_queue

/** Le domaine des failures plateforme du seam texte ; `OPERATION-CONTRACTS.md` §7, miroir web. */
internal const val UIKIT_TEXT_INPUT_SEAM_DOMAIN = "text-input"

/** Le code de l'unique failure d'une installation refuse : le receiver n'a pas pu être posé. */
internal const val UIKIT_TEXT_INPUT_INSTALL_CODE = "install-listeners-failed"

/**
 * Le port texte UIKit (Task 2, Step 3) — le miroir structurel du port web ([WebTextInputPort]
 * — décisions D-X1/D-X2/D-X3) tenu sur le receiver caché [UIKitTextInputReceiver] et l'ombre
 * [UIKitTextInputShadow] (Task 1) :
 *
 * - **capacité STRUCTURELLE** (`Capability.Supported`, D-X2) : l'éditabilité est la frontière du
 *   host, rien de ce que le port peut promettre ou refuser — une session s'ouvre sur toute
 *   surface primaire vivante ;
 * - **open exclusif** : le CAS `liveOwner` répond `AlreadyInUse(TextInputSession)` au second
 *   open (le runtime n'admet de toute façon qu'une session par surface — la CAS du port est la
 *   défense en profondeur du miroir AppKit `KffiViewTextInputAdmission.active`) ;
 * - **l'installation est l'unique effet UIKit de l'open** : receiver créé depuis la config
 *   (mapping appliqué à la construction), posé en sous-vue cachée de la vue du host (il doit
 *   être DANS la hiérarchie pour devenir first responder), amorcé au document de la config
 *   (l'analogue UIKit du fait web « l'élément porte déjà le texte » : ICI le champ démarre vide,
 *   il doit donc être SEMÉ — le shadow et le champ naissent en accord), `becomeFirstResponder`.
 *   Une installation refusee (hierarchie, focus) ou lancee est le `PlatformFailure(UIKit,
 *   "text-input", "install-listeners-failed")` du seam, le owner à moitié posé étant fermé ;
 * - **updateCursor** : accepté au shadow à la révision exacte, AUCUN effet UIKit (limite
 *   documentée, miroir web — UIKit dessine son propre caret dans le champ ; le rect porté sert
 *   au rendu de l'application, pas au champ) ;
 * - **updateDocument** : le write-back (D-X3, ordre web « élément d'abord, ombre ensuite ») —
 *   texte et sélection posés programmatiquement sous la garde de réentrance du receiver, puis
 *   le shadow reconcilié ; ses refus (`Stale`/`CompositionActive`) sont inatteignables derrière
 *   l'admission du runtime et répondent les failures qui les nomment (posture web). Un champ
 *   sorti de la hiérarchie ne peut pas porter le write-back — le write-back EST le contrat : le
 *   owner se ferme et la session répond `Closed(TextInputSession)` (miroir exact du
 *   non-addressable web D-X2) ;
 * - **observations** : `Replace` (édition utilisateur — candidat calculé au `shouldChange`
 *   contre l'ombre, committé au `.editingChange`), `Action` (touche retour). Toute observation
 *   est timbrée de la révision de l'ombre — la dernière acceptée par le runtime, jamais une
 *   révision propre. La composition (marqué IME) est HORS v1 : pendant un marqué du champ,
 *   aucun fait n'est publié (le fait de la composition n'est jamais une observation de sa
 *   propre cuisine — règle web) ; le prochain write-back rétablit l'accord (limite tracée au
 *   rapport, le seam setMarkedText/unmarkText est le prolongement Task 3+).
 *
 * Toute la vie NATIVE du port (installation, write-back, retrait, callbacks du receiver) se
 * joue sur la file principale — [runOnMain] fait le pont (inline sur le main, `dispatch_sync`
 * hors du main pour [open]/[TextInputOwner.close] qui ne sont pas suspendus, `Dispatchers.
 * Main.immediate` sous `NonCancellable` pour les deux commandes suspendues, la forme exacte du
 * bail `UIKitWindowCommandPort.lease`). Les callbacks UIKit reviennent TOUJOURS sur le main :
 * un hook ne peut donc jamais se croiser avec un write-back en cours.
 */
internal class UIKitTextInputPort(
    private val hostView: UIView,
) : TextInputPort {
    override val capability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)

    private val lock = RuntimeLock()

    /** L'owner vivant de ce port — la CAS de l'open exclusif, miroir web `liveOwner`. */
    private var liveOwner: UIKitTextInputOwner? = null

    /** Le port est-il fermé (retrait du command port) — plus aucune commande n'est servie. */
    private var closed: Boolean = false

    override fun open(command: TextInputOpenCommand): KadreResult<TextInputOwner> {
        if (lock.withLock { closed || liveOwner != null }) {
            return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession))
        }
        val owner = UIKitTextInputOwner(
            hostView = hostView,
            config = command.config,
            onObservation = command.onObservation,
        ) { closedOwner ->
            lock.withLock { if (liveOwner === closedOwner) liveOwner = null }
        }
        // L'installation est l'unique effet UIKit de l'open, et comme chaque effet de ce seam
        // elle est CONTENUE : un receiver qui refuse sa pose (ou un focus refusé) est le failure
        // plateforme du seam, jamais une exception jetée dans l'appel runtime. L'owner à moitié
        // posé est fermé — le retrait absorbe ce qui a atterri, le callback de close est un
        // no-op parce que l'owner n'a jamais été vivant (miroir web).
        val installed = runCatching { owner.install() }
        if (installed.isFailure || installed.getOrNull() != true) {
            owner.close()
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.UIKit, UIKIT_TEXT_INPUT_SEAM_DOMAIN, UIKIT_TEXT_INPUT_INSTALL_CODE),
            )
        }
        val admitted = lock.withLock {
            if (closed || liveOwner != null) {
                false
            } else {
                liveOwner = owner
                true
            }
        }
        if (!admitted) {
            owner.close()
            return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession))
        }
        UIKitTextInputCount.recordInstall()
        return KadreResult.Success(owner)
    }

    override suspend fun updateCursor(command: TextInputCursorCommand): KadreResult<Unit> {
        val owner = command.owner as? UIKitTextInputOwner
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("textInputOwner"))
        if (!isLive(owner)) return textInputClosedFailure()
        return runOnMainSuspend { owner.applyCursor(command.rect, command.documentRevision) }
    }

    override suspend fun updateDocument(command: TextInputDocumentCommand): KadreResult<Unit> {
        val owner = command.owner as? UIKitTextInputOwner
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("textInputOwner"))
        if (!isLive(owner)) return textInputClosedFailure()
        return runOnMainSuspend { owner.applyDocument(command.text, command.selection, command.documentRevision) }
    }

    /**
     * Le retrait du port (le `finishClose` du command port) : l'owner vivant, s'il en reste un,
     * est fermé — le champ quitte la hiérarchie et le first responder, aucune observation ne
     * survit au port. Idempotent.
     */
    fun shutdown() {
        lock.withLock { closed = true }
        lock.withLock { liveOwner }?.close()
    }

    private fun isLive(owner: UIKitTextInputOwner): Boolean =
        lock.withLock { !closed && liveOwner === owner && !owner.isClosed }

    private fun textInputClosedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
}

/**
 * L'unique owner vivant d'un open : le shadow, le receiver caché et la publication des
 * observations d'une session. Toute la vie du owner est native-main (l'open/close qui viennent
 * d'ailleurs passent par [runOnMain]) ; un callback UIKit toujours porteur d'un document dont
 * les offsets peuvent être crus — une exception dans un hook ferme donc l'owner (miroir exact
 * du `safely` web : l'ombre dont le handler a échoué est une ombre dont les offsets ne peuvent
 * plus être crûs — la session répond `Closed` à partir de là).
 */
internal class UIKitTextInputOwner(
    private val hostView: UIView,
    private val config: TextInputConfig,
    private val onObservation: (TextInputObservation) -> Boolean,
    private val onClosed: (UIKitTextInputOwner) -> Unit,
) : TextInputOwner {
    private val lock = RuntimeLock()
    private val shadow = UIKitTextInputShadow(config)
    private val receiver = UIKitTextInputReceiver(
        config = config,
        hooks = UIKitTextInputReceiver.Hooks(
            onShouldChange = { range, replacement -> safelyAnswered { onShouldChange(range, replacement) } },
            onTextDidChange = { safely { onTextDidChange() } },
            onReturn = { safelyAnswered { onReturn() } },
            onFirstResponderGained = { safely { onFirstResponderGained() } },
            onFirstResponderLost = { safely { onFirstResponderLost() } },
        ),
    )

    /** Le candidat d'édition utilisateur : calculé au `shouldChange`, committé au `.editingChanged`. */
    private class PendingUserEdit(val range: TextRange, val replacement: String)

    private var pendingUserEdit: PendingUserEdit? = null

    /** Le routeur PUR des faits d'édition (écho / composition / commit IME / candidat) — testé sans appareil. */
    private val editRouter = UIKitTextInputEditRouter()

    internal val isClosed: Boolean
        get() = lock.withLock { closed }

    private var closed: Boolean = false

    /**
     * L'installation (main) : pose dans la hiérarchie, AMORÇAGE au document de la config (le
     * champ démarre vide — l'ombre et le champ doivent naître en accord, l'analogue inverse du
     * web où l'élément porte déjà le texte), puis focus. Répond `false` — l'installation a
     * échoué, l'open répondra la failure plateforme du seam — sans jamais jeter.
     */
    fun install(): Boolean {
        if (isClosed) return false
        return runOnMain {
            if (isClosed) return@runOnMain false
            hostView.addSubview(receiver)
            receiver.writeBack(
                config.surroundingText,
                config.selection.startUtf16.toLong(),
                config.selection.endExclusiveUtf16.toLong(),
            )
            val focused = receiver.becomeFirstResponder()
            if (!focused) {
                receiver.removeFromSuperview()
            }
            focused
        }
    }

    override fun close() {
        val already = lock.withLock {
            if (closed) {
                true
            } else {
                closed = true
                pendingUserEdit = null
                false
            }
        }
        if (already) return
        // Le port délivre la CAS AVANT le retrait natif : une session qui ferme pendant que
        // l'open est en vol trouve `liveOwner` déjà nettoyé (l'ordre web : closed → withdraw →
        // onClosed). Les hooks déjà en file au moment du retrait sont rejetés par la garde
        // `closed` du publish, jamais par un état natif à moitié défait.
        onClosed(this)
        runOnMain {
            if (receiver.isFirstResponder) receiver.resignFirstResponder()
            receiver.removeFromSuperview()
        }
        UIKitTextInputCount.recordTeardown()
    }

    /** Accepte le cursor rect dans l'ombre à la révision exacte ; aucun effet UIKit (limite). */
    fun applyCursor(rect: LogicalRect, revision: TextDocumentRevision): KadreResult<Unit> =
        if (shadow.applyCursor(rect, revision)) {
            KadreResult.Success(Unit)
        } else {
            KadreResult.Failure(
                KadreFailure.StaleRevision(shadow.documentRevision.value, revision.value),
            )
        }

    /**
     * Le write-back (main) : le champ d'abord, l'ombre ensuite — l'ordre web (D-X3), pour qu'un
     * écho que le champ provoquerait en cours d'écriture soit jugé contre la révision que le
     * runtime peut encore admettre. Un champ non addressable (sorti de la hiérarchie) ne peut
     * pas porter le write-back — le write-back EST le contrat : owner fermé, `Closed`
     * (miroir D-X2 web), jamais une écriture d'une propriété hors contrat.
     */
    fun applyDocument(
        text: String,
        selection: TextRange,
        revision: TextDocumentRevision,
    ): KadreResult<Unit> {
        if (!receiver.isAddressable) {
            close()
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
        }
        receiver.writeBack(text, selection.startUtf16.toLong(), selection.endExclusiveUtf16.toLong())
        return when (shadow.applyDocument(text, selection, revision)) {
            UIKitTextInputDocumentUpdate.Applied -> KadreResult.Success(Unit)
            UIKitTextInputDocumentUpdate.Stale -> KadreResult.Failure(
                KadreFailure.StaleRevision(shadow.documentRevision.value, revision.value),
            )

            UIKitTextInputDocumentUpdate.CompositionActive -> KadreResult.Failure(KadreFailure.InvalidRequest("text"))
            UIKitTextInputDocumentUpdate.Closed -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
        }
    }

    // MARK: - Les hooks du receiver (main, contenus par [safely])

    /**
     * Un `shouldChangeCharactersInRange` : le RANGE est résolu CONTRE l'ombre TOUT DE SUITE —
     * au moment du hook, le texte du champ est encore le texte de l'ombre (elles sont en accord
     * à chaque instant hors composition, et la composition est exclue ci-dessous) ; le candidat
     * attend l'après-modification pour être committé (l'ordre du brief : observation calculée,
     * champ modifié par UIKit, ombre reconciliée au changement suivant). `UIKIT_NS_NOT_FOUND`
     * (« pas de plage » UIKit) se résout déjà en sélection par [UIKitTextInputShadow.resolveRange].
     * Le port rend toujours `true` : la suppression d'une édition n'est pas du ressort du
     * port v1 — la politique vit au runtime.
     *
     * Fix round 1 : la garde de composition porte sur l'état VIVANT du champ
     * ([UIKitTextInputReceiver.isMarkingText]) — la garde d'origine lisait `shadow.markedRange`,
     * permanent nul en v1 (le port n'y écrit pas), donc toujours fausse : un candidat pouvait
     * être posé PENDANT une composition réelle, puis jeté au changement suivant sans jamais
     * resynchroniser l'ombre.
     */
    private fun onShouldChange(range: UIKitNsRange, replacement: String): Boolean {
        if (lock.withLock { closed }) return true
        // Une composition est vivante DANS LE CHAMP : son fait, pas une observation (règle web) —
        // aucun candidat n'est posé, le champ applique, rien n'est publié.
        if (!editRouter.shouldRecordCandidate(fieldMarked = receiver.isMarkingText)) return true
        val resolved = shadow.resolveRange(range) ?: return true // plage incroyable : rien à observer
        lock.withLock { pendingUserEdit = PendingUserEdit(resolved, replacement) }
        return true
    }

    /**
     * L'après-modification (`.editingChanged`) : la DÉCISION vit dans le routeur PUR
     * ([UIKitTextInputEditRouter] — testé sans appareil), le port l'exécute :
     *
     * - **`EchoDrop`** (fix round 1) : l'écho d'un write-back ([UIKitTextInputReceiver.
     *   writeBackInFlight] en vol) ne consomme JAMAIS le candidat — il le JETTE. Sans cette
     *   garde explicite, un candidat qui traîne (un `shouldChange` sans `editingChanged` de
     *   suite — remplaçement-à-l'identique plausible) aurait été consommé par le prochain
     *   changement, y compris un écho : un `Replace` fantôme à la révision d'avant write-back,
     *   admis par la session. L'ombre, elle, est déjà reconciliée par le write-back lui-même ;
     * - **`CompositionDrop`** : une composition vit dans le champ — le fait est à la
     *     composition, jamais une observation de sa propre cuisine (règle web) ; le candidat
     *     éventuel est jeté, le routeur RETIENT le marqué ;
     * - **`CompositionCommit`** (fix round 1) : le marqué du champ est passé à nul alors que le
     *     routeur le retenait — le commit IME. Rien ne se publie (limite v1 documentée), mais
     *     l'ombre est RESYNCHRONISÉE depuis le champ ([reconcileShadowFromField]) : sans elle,
     *     le texte committé resterait hors de l'ombre et la PLAGE honnête du prochain appui se
     *     résoudrait nulle (édition silencieusement perdue) ou fausse (mauvais `Replace`) —
     *     l'ombre empoisonnée ;
     * - **`CommitPending`** : l'édition utilisateur honnête — le candidat est committé contre
     *     l'ombre (`replaceText` porte la même mutation qu'UIKit vient d'appliquer au champ :
     *     elles étaient en accord, la plage était résolue sur cet accord) et l'observation part
     *     timbrée de la révision de l'ombre ;
     * - **`Nothing`** : un fait non attribué (le stimulus Task 3 l'attribuera) — rien.
     */
    private fun onTextDidChange() {
        val pendingPresent = lock.withLock { pendingUserEdit != null }
        when (
            editRouter.onTextDidChange(
                writeBackInFlight = receiver.writeBackInFlight,
                fieldMarked = receiver.isMarkingText,
                pendingPresent = pendingPresent,
            )
        ) {
            UIKitTextInputEditDecision.EchoDrop,
            UIKitTextInputEditDecision.CompositionDrop,
            -> lock.withLock { pendingUserEdit = null }

            UIKitTextInputEditDecision.CompositionCommit -> {
                lock.withLock { pendingUserEdit = null }
                reconcileShadowFromField()
            }

            UIKitTextInputEditDecision.CommitPending -> commitPendingUserEdit()

            UIKitTextInputEditDecision.Nothing -> Unit
        }
    }

    /** Le commit du candidat utilisateur : la mutation d'ombre, puis l'observation timbrée. */
    private fun commitPendingUserEdit() {
        val pending = lock.withLock {
            val pending = pendingUserEdit
            pendingUserEdit = null
            pending
        } ?: return
        if (shadow.replaceText(pending.range, pending.replacement)) {
            publish(TextInputObservation.Replace(pending.range, pending.replacement, shadow.documentRevision))
        }
    }

    /**
     * La resynchronisation du commit IME : l'ombre adopte la vue COMMITTÉE du champ (texte et
     * sélection lus dans le champ) À LA RÉVISION COURANTE de l'ombre — la même révision, jamais
     * une révision propre (le runtime reste l'autorité ; `applyDocument` l'admet comme le
     * restate idempotent, preuve `UIKitTextInputShadowTest`). Le champ montre ce fait ; l'ombre
     * doit pouvoir y résoudre la prochaine plage honnête. Les refus (`Stale` — inatteignable à
     * révision égale ; `CompositionActive`/`Closed` — l'ombre ne marque jamais en v1) laissent
     * l'ombre en l'état : le prochain write-back rétablit l'accord (posture web inchangée).
     */
    private fun reconcileShadowFromField() {
        val text = receiver.text ?: ""
        val selection = receiver.currentSelectionUtf16() ?: TextRange(text.length, text.length)
        shadow.applyDocument(text, selection, shadow.documentRevision)
    }

    /** La touche retour : l'action de la config — le miroir du keydown Enter web. */
    private fun onReturn(): Boolean {
        if (lock.withLock { closed }) return true
        publish(TextInputObservation.Action(config.action, shadow.documentRevision))
        return true
    }

    private fun onFirstResponderGained() {
        if (!lock.withLock { closed }) UIKitTextInputCount.recordFocusGained()
    }

    private fun onFirstResponderLost() {
        if (!lock.withLock { closed }) UIKitTextInputCount.recordFocusLost()
    }

    /** Publie une observation tant que l'owner vit — jamais après. */
    private fun publish(observation: TextInputObservation) {
        if (lock.withLock { closed }) return
        UIKitTextInputCount.recordObservation()
        onObservation(observation)
    }

    /** Contient un hook à réponse (le delegate attend un Boolean) : une exception ferme l'owner (miroir web). */
    private fun safelyAnswered(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (cause: Throwable) {
            close()
            true
        }

    /** Contient un hook sans réponse (les hooks `Unit` du delegate) — la même posture. */
    private fun safely(block: () -> Unit) {
        try {
            block()
        } catch (cause: Throwable) {
            close()
        }
    }
}

/** Ce que le port exécute pour un fait `.editingChanged` — le verdict PUR du routeur. */
internal enum class UIKitTextInputEditDecision {
    /** Écho d'un write-back : le candidat éventuel est JETÉ, rien ne se publie (l'ombre est déjà au fait). */
    EchoDrop,

    /** Composition IME vivante dans le champ : le candidat éventuel est jeté, rien ne se publie, le marqué est retenu. */
    CompositionDrop,

    /** Le marqué du champ vient de passer à nul (commit IME) : le candidat éventuel est jeté, l'ombre est resynchronisée depuis le champ, rien ne se publie. */
    CompositionCommit,

    /** Édition utilisateur honnête : le candidat est committé contre l'ombre et publié en `Replace`. */
    CommitPending,

    /** Un fait sans candidat ni mémoire de marqué ni write-back en vol — rien. */
    Nothing,
}

/**
 * Le routeur PUR des faits d'édition du receiver (fix round 1) : il tient le SEUL état que la
 * décision exige — la mémoire du marqué du champ au changement précédent — et rend le verdict
 * que le port exécute. Zéro UIKit, testé sans appareil ([UIKitTextInputEditRouterTest]) : les
 * deux hardenings (écho qui ne consomme jamais un candidat ; commit IME qui resynchronise
 * l'ombre) sont des décisions ÉPINGLÉES ici, leur CÂBLAGE aux lectures réelles du champ
 * ([UIKitTextInputReceiver.writeBackInFlight]/[UIKitTextInputReceiver.isMarkingText]) restant
 * de la preuve driver (Task 3).
 *
 * L'ordre des gardes EST la sémantique : l'écho gagne sur tout (le write-back possède le champ,
 * l'ombre vient d'y être écrite — il RESETTE aussi la mémoire de marqué, la transition qu'un
 * write-back absorbe est consommée par le write-back lui-même) ; le marqué vivant retient sa
 * mémoire ; la sortie de marqué (mémoire posée, champ nul) est LE commit IME, exactement une
 * fois ; sans rien de tout cela, un candidat présent est l'édition utilisateur honnête.
 */
internal class UIKitTextInputEditRouter {

    /** Le champ tenait-il un texte marqué au dernier changement. */
    internal var fieldWasMarking: Boolean = false
        private set

    /**
     * Un `shouldChange` enregistre-t-il un candidat ? Jamais pendant une composition vivante
     * DANS LE CHAMP (le fait est à la composition — la garde sur `shadow.markedRange` était
     * permanente-nulle en v1, donc toujours fausse).
     */
    fun shouldRecordCandidate(fieldMarked: Boolean): Boolean = !fieldMarked

    /** Le verdict du changement courant, qui fait aussi avancer la mémoire du marqué. */
    fun onTextDidChange(
        writeBackInFlight: Boolean,
        fieldMarked: Boolean,
        pendingPresent: Boolean,
    ): UIKitTextInputEditDecision = when {
        writeBackInFlight -> {
            fieldWasMarking = false
            UIKitTextInputEditDecision.EchoDrop
        }

        fieldMarked -> {
            fieldWasMarking = true
            UIKitTextInputEditDecision.CompositionDrop
        }

        fieldWasMarking -> {
            fieldWasMarking = false
            UIKitTextInputEditDecision.CompositionCommit
        }

        pendingPresent -> UIKitTextInputEditDecision.CommitPending

        else -> UIKitTextInputEditDecision.Nothing
    }
}

/**
 * Le pont file principale du port : inline sur le main, `dispatch_sync` hors du main pour les
 * entrées NON suspendues ([open]/[close] — le main n'attend jamais leur appelant, l'appelant
 * hors main ne peut donc pas l'y bloquer), la forme suspendue du bail `lease` pour les
 * commandes suspendues : `Dispatchers.Main.immediate` et, une fois l'invocation DÉMARRÉE,
 * `NonCancellable` — un callback commencé s'achève même si la coroutine appelante est annulée
 * pendant lui.
 */
internal fun <R> runOnMain(block: () -> R): R = if (NSThread.isMainThread()) block() else dispatchSyncOnMain(block)

internal suspend fun <R> runOnMainSuspend(block: () -> R): R =
    if (NSThread.isMainThread()) {
        withContext(NonCancellable) { block() }
    } else {
        withContext(NonCancellable + Dispatchers.Main.immediate) { block() }
    }

/** Le pont bloquant : le résultat revient dans une boîte tenue par l'appelant (barrière de sync). */
private class MainHopResult<R> {
    var value: R? = null
}

private fun <R> dispatchSyncOnMain(block: () -> R): R {
    val holder = MainHopResult<R>()
    dispatch_sync(dispatch_get_main_queue()) { holder.value = block() }
    return checkNotNull(holder.value) { "uikit text-input main hop produced no value" }
}

/**
 * Le compteur process-wide du texte (le proxy du driver, pattern [UIKitTouchCount]) : les
 * installations/retraits d'owners, les observations PUBLIÉES (Replace/Action — le fait que le
 * port a servi au driver Task 3, incrémenté à chaque publication passée la garde de close), et
 * les événements focus du receiver. Interne au module ; la lecture du driver passe par la sonde
 * [org.graphiks.kadre.platform.uikit.KadreUikitSurfaceDriver].
 */
internal object UIKitTextInputCount {
    private val lock = RuntimeLock()
    private var installs: Long = 0L
    private var teardowns: Long = 0L
    private var observations: Long = 0L
    private var focusGained: Long = 0L
    private var focusLost: Long = 0L

    fun recordInstall() {
        lock.withLock { installs += 1L }
    }

    fun recordTeardown() {
        lock.withLock { teardowns += 1L }
    }

    fun recordObservation() {
        lock.withLock { observations += 1L }
    }

    fun recordFocusGained() {
        lock.withLock { focusGained += 1L }
    }

    fun recordFocusLost() {
        lock.withLock { focusLost += 1L }
    }

    fun totalInstalls(): Long = lock.withLock { installs }

    fun totalTeardowns(): Long = lock.withLock { teardowns }

    fun totalObservations(): Long = lock.withLock { observations }

    fun totalFocusGained(): Long = lock.withLock { focusGained }

    fun totalFocusLost(): Long = lock.withLock { focusLost }
}
