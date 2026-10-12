package org.graphiks.kadre.internal.uikit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneProvider
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneRequestResolver
import org.graphiks.kadre.platform.uikit.UIKitWindowSceneRequestSink
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowSpec
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Le domaine de toute défaillance liée au provider de scènes, comme `OPERATION-CONTRACTS.md` §4
 * le nomme pour le web (`WebWindowProvider`) : les exceptions du callback et les failures rendues
 * hors de l'ensemble fermé sont toutes deux des défaillances du provider, dans ce domaine.
 */
internal const val UIKitWindowProviderDomain: String = "UIKitWindowSceneProvider"

/**
 * Whether [failure] is one the closed set of `WindowRequestOutcome.Rejected.failure` admits —
 * the verbatim web gate (`WebHostWindowManager.isAdmittedWindowRequestFailure`), where the
 * contract's `Busy(Host)` is `AlreadyInUse(Host)`, `Limit(Window)` is `ResourceLimitExceeded(Window)`,
 * `Temporary` is `TemporarilyUnavailable` and `Platform` is any [KadreFailure.PlatformFailure].
 * A failure whose kind is not in the set — or whose kind names another resource than the set
 * admits — is refused, and the manager reports it as `invalid-failure` rather than leaking it.
 */
internal fun isAdmittedWindowRequestFailure(failure: KadreFailure): Boolean = when (failure) {
    is KadreFailure.Unsupported -> failure.operation == KadreOperation.RequestWindow
    is KadreFailure.PermissionDenied -> false
    is KadreFailure.UserCancelled -> false
    is KadreFailure.TemporarilyUnavailable -> true
    is KadreFailure.InvalidRequest -> true
    is KadreFailure.AlreadyInUse -> failure.resource == KadreResourceKind.Host
    is KadreFailure.Closed -> failure.resource == KadreResourceKind.Host
    is KadreFailure.ResourceLimitExceeded -> failure.resource == KadreResourceKind.Window
    is KadreFailure.SourceOverflow -> false
    is KadreFailure.StaleRevision -> false
    is KadreFailure.InteractionRequired -> true
    is KadreFailure.UnsupportedPolicy -> false
    KadreFailure.ParentScopeCancelled -> true
    is KadreFailure.ShutdownTimedOut -> false
    is KadreFailure.SourceLost -> false
    KadreFailure.ApplicationFailure -> false
    is KadreFailure.PlatformFailure -> true
}

/** La porte : une failure de l'ensemble fermé passe telle quelle, toute autre devient `invalid-failure`. */
internal fun admittedWindowRequestFailureOrInvalid(failure: KadreFailure): KadreFailure =
    if (isAdmittedWindowRequestFailure(failure)) {
        failure
    } else {
        KadreFailure.PlatformFailure(KadrePlatform.UIKit, UIKitWindowProviderDomain, "invalid-failure")
    }

/** Le throw d'un callback host capturé devient la failure que le contrat promet (web l.298-299). */
internal fun callbackExceptionFailure(): KadreFailure =
    KadreFailure.PlatformFailure(KadrePlatform.UIKit, UIKitWindowProviderDomain, "callback-exception")

/**
 * La capability `requestWindow` publiée, décidée une fois à la construction par le provider :
 * `OpenedInNewSession` seul quand un provider existe (ce manager n'ouvre JAMAIS `OpenedHere` —
 * une scène enfant est sa propre session complète), le refus unsupported sinon (tvOS mono-scène).
 */
internal fun sceneWindowCapability(provider: UIKitWindowSceneProvider?): Capability<Set<WindowCreationMode>> =
    when (provider) {
        null -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow))
        else -> Capability.Supported(setOf(WindowCreationMode.OpenedInNewSession), FeatureAvailability.Available)
    }

/**
 * Les objets host d'une scène corrélée, tels que l'app les remet au moment de la connexion —
 * la seule valeur que la fabrique de session enfant reçoit, avec l'identité de la demande.
 * Les types sont les UIKit réels (Task 2 construit le runtime enfant depuis ces objets) ; le
 * manager ne les touche jamais lui-même — ils transitent opaques vers la fabrique injectée.
 */
internal class UIKitWindowSceneHost(
    internal val windowScene: UIWindowScene,
    internal val window: UIWindow,
    internal val surfaceView: UIView,
    internal val requestId: WindowRequestId,
)

/**
 * Le noyau de bookkeeping du pending de demandes de scènes : la carte des demandes admises, la
 * borne [maxPendingWindowRequests] et l'ordre des refus (clos AVANT borne, miroir web l.219-234).
 *
 * Aucun type UIKit, aucune coroutine, aucun flux — le même état machine tourne sur les deux
 * simulateurs dans uikitTest ; le manager qui le porte reste une coquille fine (partage
 * kernel/binding de la phase 2). Toutes les opérations sont confinées au RuntimeLock du manager.
 * La sémantique fine : `claim` NE retire PAS (le budget reste détenu pendant l'attach de
 * l'enfant), `settle`/`remove` retirent (terminaison de la requête = libération du slot),
 * `drain` ferme l'admission et rend tout le restant.
 */
internal class UIKitWindowScenePending(
    private val maxPendingWindowRequests: Int,
    private val nextRequestId: () -> WindowRequestId,
    private val issueResolver: (WindowRequestId) -> UIKitWindowSceneRequestResolver,
) {
    /** Une demande admise, pendante tant qu'elle est dans la carte. */
    internal class Entry(
        internal val id: WindowRequestId,
        internal val spec: WindowSpec,
        internal val request: WindowRequest,
        internal val resolver: UIKitWindowSceneRequestResolver,
    )

    internal sealed interface Admission {
        data object RefusedClosed : Admission
        data class RefusedLimit(internal val limit: Int) : Admission
        data class Admitted(internal val entry: Entry) : Admission
    }

    private var closed: Boolean = false
    private val entries = LinkedHashMap<WindowRequestId, Entry>()

    internal fun isClosed(): Boolean = closed

    /** Admission d'une demande : id alloué et résolver émis SEULEMENT si la demande est admise. */
    internal fun admit(
        spec: WindowSpec,
        makeRequest: (WindowRequestId, UIKitWindowSceneRequestResolver) -> WindowRequest,
    ): Admission = when {
        closed -> Admission.RefusedClosed
        entries.size >= maxPendingWindowRequests -> Admission.RefusedLimit(maxPendingWindowRequests)
        else -> {
            val id = nextRequestId()
            val resolver = issueResolver(id)
            val entry = Entry(id, spec, makeRequest(id, resolver), resolver)
            entries[id] = entry
            Admission.Admitted(entry)
        }
    }

    /** Le début d'une résolution : l'entrée si elle est encore pendante (elle y RESTE pendant l'attach). */
    internal fun claim(id: WindowRequestId): Entry? = entries[id]

    /** La terminaison par résolution retire l'entrée ; un second settle ne trouve plus rien. */
    internal fun settle(id: WindowRequestId): Entry? = entries.remove(id)

    /** La terminaison par le requérant (cancel/detach/abandon) retire l'entrée. */
    internal fun remove(id: WindowRequestId): Entry? = entries.remove(id)

    /** La fermeture du manager : tout le restant est rendu, l'admission est close pour toujours. */
    internal fun drain(): List<Entry> {
        closed = true
        return entries.values.toList().also { entries.clear() }
    }
}

/**
 * Le manager des demandes de fenêtres d'une session UIKit qui porte un [UIKitWindowSceneProvider].
 *
 * Écrit sur le modèle du `WebHostWindowManager` web, mais **asynchrone** là où le web est
 * synchrone : l'ouverture d'une scène passe par FrontBoard, sans timeout synthétique (roadmap
 * l.240) — la demande admise reste `Pending` jusqu'à ce que l'app résolve la scène corrélée
 * ([resolve]), l'abandonne ([abandon]) ou que le requérant la termine (`cancel`/`close`).
 * TOUT le reste délègue au manager runtime ([runtimeManager]) : la fenêtre primaire et son état
 * (phases 1-3 intactes) restent siens — ce manager ne fait AUCUNE admission de fenêtre, aucun
 * reducer de géométrie/close ; il route provider ↔ session enfant et tient le pending.
 *
 * Le flux d'une demande admise, tout arrivant comme **outcomes de la requête** et jamais comme
 * failures de l'appel extérieur :
 *
 * 1. admission — un manager clos refuse avec la failure directe `Closed(Host)` ; un budget
 *    épuisé refuse avec `ResourceLimitExceeded(WindowRequest)` (web l.219-234) ;
 * 2. sans provider (tvOS), la demande est déjà `Rejected(Unsupported(RequestWindow))` — AVANT
 *    toute réservation, l'octet près le comportement de l'`UnsupportedWindowManager` d'antan
 *    (web l.265-266) ;
 * 3. le provider est invoqué avec l'id et la spéc, dans un try/catch : un throw devient
 *    `PlatformFailure(UIKit, "UIKitWindowSceneProvider", "callback-exception")` (web l.268-278) ;
 *    la demande terminée à l'handoff RETIENT son entrée (web-verbatim) — sa résolution tardive
 *    est refusée par [claimResolve], jamais un enfant sans owner ;
 * 4. à la connexion corrélée, [resolve] attache la session enfant par la fabrique injectée
 *    ([childSessionFactory] — le chemin ordinaire de l'attach, miroir `WebChildSessionFactory`) :
 *    succès → `OpenedInNewSession(child.id)` et le slot est libéré ; failure → `Rejected` de
 *    l'ensemble fermé (`invalid-failure` pour tout ce qui en sort) ;
 * 5. une résolution arrivant sur une requête déjà terminale est un no-op (l'outcome d'origine
 *    tient, jamais deux résolutions) ; l'enfant qui vient de s'attacher dans la course est
 *    détaché proprement — jamais de ressource transitoire sans owner (roadmap l.239). Le CAS
 *    de `UIKitSceneWindowRequest.terminate` arbitre les écrivains concurrents sous et hors du
 *    lock : le premier outcome publié tient, quoi qu'il arrive.
 *
 * L'enfant attaché est une session INDÉPENDANTE du requérant (roadmap l.240) : la fermeture du
 * manager ne touche pas les enfants déjà résolus — elle termine les pendants `RequesterDetached`
 * et abandonne leurs résolvers ; la fermeture du manager runtime reste propriété de la chaîne de
 * teardown des components (`UIKitWindowRuntime.close`), jamais un second close d'ici.
 */
internal class UIKitWindowSceneManager(
    private val runtimeManager: WindowManager,
    private val provider: UIKitWindowSceneProvider?,
    policy: KadrePolicy,
    private val applicationFactory: KadreApplicationFactory,
    private val failureReporter: RuntimeFailureReporter,
    private val parentScope: CoroutineScope,
    private val childSessionFactory: (UIKitWindowSceneHost) -> KadreResult<KadreSession>,
) : WindowManager by runtimeManager,
    UIKitWindowSceneRequestSink {

    private val lock = RuntimeLock()
    private val pending = UIKitWindowScenePending(
        maxPendingWindowRequests = policy.resources.maxPendingWindowRequests,
        nextRequestId = RuntimeProcessIds::nextWindowRequestId,
        issueResolver = { id -> UIKitWindowSceneRequestResolver.issue(id, this) },
    )

    /**
     * L'état du runtime, avec la SEULE substitution de ce manager : la capability `requestWindow`
     * ([sceneWindowCapability]). Primaire, fenêtres et révision passent telles quelles.
     */
    override val state: StateFlow<WindowManagerState> =
        UIKitWindowSceneStateFlow(runtimeManager.state, sceneWindowCapability(provider))

    override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> {
        // Sans provider, la demande est déjà terminée avant TOUTE réservation (web l.265-266) —
        // ni budget, ni id de pending consommés (le test maxPending=1 enchaîne deux demandes :
        // une réservation maintiendrait la seconde sous `ResourceLimitExceeded`).
        val localProvider = provider
            ?: return KadreResult.Success(UIKitUnsupportedSceneRequest(RuntimeProcessIds.nextWindowRequestId()))

        lateinit var request: UIKitSceneWindowRequest
        val refusal = lock.withLock {
            when (val decision = pending.admit(spec, ::makeSceneRequest)) {
                is UIKitWindowScenePending.Admission.RefusedClosed -> KadreFailure.Closed(KadreResourceKind.Host)
                is UIKitWindowScenePending.Admission.RefusedLimit -> KadreFailure.ResourceLimitExceeded(
                    KadreResourceKind.WindowRequest,
                    decision.limit.toLong(),
                )

                is UIKitWindowScenePending.Admission.Admitted -> {
                    request = decision.entry.request.sceneRequest
                    null
                }
            }
        }
        if (refusal != null) return KadreResult.Failure(refusal)

        // Le provider est du code host invoqué sur le main, HORS du lock : son throw est capturé
        // ici et devient l'outcome promis, jamais une exception qui s'échappe (web l.268-278).
        // L'entrée RETIENT son slot (web-verbatim) jusqu'au close() du requérant — une
        // résolution tardive de cette demande sera refusée (claimResolve lit l'état de la
        // requête, pas la seule présence dans la carte) et le CAS interdit toute réécriture.
        try {
            localProvider.open(request.id, spec)
        } catch (_: Throwable) {
            request.terminate(WindowRequestOutcome.Rejected(callbackExceptionFailure()))
        }
        // L'outcome n'est PAS connu ici : la demande reste Pending jusqu'à la connexion corrélée.
        return KadreResult.Success(request)
    }

    /**
     * La fermeture de la session : toute demande pendante est terminée `RequesterDetached` et son
     * résolver abandonné (l'app peut renoncer à l'activation de scène), l'admission est close.
     * Idempotent. Les enfants déjà résolus ne sont PAS touchés — sessions indépendantes.
     */
    fun close() {
        val drained = lock.withLock {
            if (pending.isClosed()) return
            pending.drain()
        }
        drained.forEach { entry ->
            // CAS : une entrée retenue dont la demande est déjà terminale (throw du provider)
            // garde son outcome publié — le drain ne le retourne pas en RequesterDetached.
            entry.request.sceneRequest.terminate(WindowRequestOutcome.RequesterDetached)
            entry.resolver.abandon("manager-closed")
        }
    }

    // -- la corrélation (routes du sink, appelées par l'app via le resolver) ---------------------------------

    /**
     * La scène corrélée a connecté : l'enfant est attaché par la fabrique injectée puis la
     * demande est terminée. Toute résolution tardive (requête terminale) est un no-op.
     * Sur le thread principal, comme `UIKitWindowSceneProvider.open`.
     */
    override fun resolve(
        requestId: WindowRequestId,
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
    ) {
        val entry = claimResolve(requestId) ?: return
        completeResolve(entry) { childSessionFactory(UIKitWindowSceneHost(windowScene, window, surfaceView, requestId)) }
    }

    /** Le discard app-side d'une scène qui ne connectera jamais : `Cancelled` si pendante. */
    override fun abandon(requestId: WindowRequestId, reason: String) {
        val entry = lock.withLock { pending.remove(requestId) } ?: return
        entry.request.sceneRequest.terminate(WindowRequestOutcome.Cancelled)
        entry.resolver.abandon(reason)
    }

    /**
     * La couture testable de la résolution : l'entrée si la demande est encore pendante, `null`
     * sinon (résolution tardive — no-op, jamais de second attach). La présence dans la carte ne
     * suffit PAS : une demande terminée à l'handoff (throw du provider) RETIENT son entrée
     * (web-verbatim, jusqu'au `close()` du requérant) — sa résolution est refusée AVANT tout
     * attach, jamais un enfant sans owner (roadmap l.238-239).
     */
    internal fun claimResolve(requestId: WindowRequestId): UIKitWindowScenePending.Entry? =
        lock.withLock {
            val entry = pending.claim(requestId)
            when {
                entry == null || entry.request.sceneRequest.terminalOutcome() != null -> null
                else -> entry
            }
        }

    /**
     * La fin de la résolution : [attach] court HORS du lock (comme l'échelle web), puis le
     * settle arbitre — l'entrée partie (cancel/detach/drain pendant l'attach) OU le CAS de
     * terminaison perdu (la demande était DÉJÀ terminale : throw du provider retenu dans la
     * carte, ou toute terminaison hors lock arrivée pendant l'attach) sont des courses perdues.
     */
    internal fun completeResolve(
        entry: UIKitWindowScenePending.Entry,
        attach: () -> KadreResult<KadreSession>,
    ) {
        val attached = attach()
        val raced = lock.withLock {
            val settled = pending.settle(entry.id)
            when (settled) {
                null -> true
                else -> {
                    val outcome = when (attached) {
                        is KadreResult.Success -> WindowRequestOutcome.OpenedInNewSession(attached.value.id)
                        is KadreResult.Failure ->
                            WindowRequestOutcome.Rejected(admittedWindowRequestFailureOrInvalid(attached.reason))
                    }
                    !settled.request.sceneRequest.terminate(outcome)
                }
            }
        }
        // Course perdue : l'enfant fraîchement attaché est une ressource sans owner (roadmap
        // l.239) — il est détaché et l'outcome terminal d'origine tient, jamais réécrit.
        if (raced && attached is KadreResult.Success) attached.value.requestStop()
    }

    // -- les gestes du requérant -----------------------------------------------------------------------------

    internal fun cancelSceneRequest(request: UIKitSceneWindowRequest): WindowCancellationOutcome {
        val resolver = lock.withLock {
            request.terminalOutcome()?.let { return WindowCancellationOutcome.AlreadyTerminated(it) }
            val entry = pending.remove(request.id)
            // Le CAS arbitre tout écrivain hors lock (throw du provider, drain du close,
            // abandon) : si la publication est perdue, l'outcome publié est celui du gagnant —
            // l'annulation le rapporte honnêtement au lieu de le retourner ni de le réécrire.
            if (!request.terminate(WindowRequestOutcome.Cancelled)) {
                return WindowCancellationOutcome.AlreadyTerminated(checkNotNull(request.terminalOutcome()))
            }
            if (entry == null) {
                // Course drain/terminaison : une requête que personne n'a terminée est annulée
                // pré-handoff — le cas dégénéré mais total du web (l.321-327).
                return WindowCancellationOutcome.CancelledBeforeCommit
            }
            entry.resolver
        }
        // HORS du lock : l'abandon re-rentre dans ce manager (no-op garanti — entrée déjà sortie).
        resolver.abandon("cancelled")
        return WindowCancellationOutcome.CancelledBeforeCommit
    }

    internal fun detachSceneRequest(request: UIKitSceneWindowRequest) {
        val resolver = lock.withLock {
            val entry = pending.remove(request.id)
            if (request.terminalOutcome() == null) {
                // Le CAS garantit de ne jamais réécrire un outcome publié entre-temps par un
                // écrivain hors lock : le vaincu laisse intact le terminal d'origine.
                request.terminate(WindowRequestOutcome.RequesterDetached)
            }
            entry?.resolver
        }
        resolver?.abandon("requester-detached")
    }

    // -- le sink du resolver ---------------------------------------------------------------------------------

    private fun makeSceneRequest(
        id: WindowRequestId,
        resolver: UIKitWindowSceneRequestResolver,
    ): WindowRequest = UIKitSceneWindowRequest(id, resolver, this)

    /**
     * L'état publié : celui du runtime, la capability `requestWindow` substituée une fois pour
     * toutes. Collecter délègue à l'amont — la transformation est pure et conflation-preserving.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class UIKitWindowSceneStateFlow(
        private val upstream: StateFlow<WindowManagerState>,
        private val requestWindow: Capability<Set<WindowCreationMode>>,
    ) : StateFlow<WindowManagerState> {
        override val value: WindowManagerState
            get() = upstream.value.withSceneCapability()

        override val replayCache: List<WindowManagerState>
            get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<WindowManagerState>): Nothing =
            upstream.collect { state -> collector.emit(state.withSceneCapability()) }

        private fun WindowManagerState.withSceneCapability(): WindowManagerState =
            copy(capabilities = WindowManagerCapabilities(requestWindow))
    }
}

/** L'identité concrète de toute requête stockée par le noyau — posée par `makeSceneRequest`. */
private val WindowRequest.sceneRequest: UIKitSceneWindowRequest
    get() = this as UIKitSceneWindowRequest

/**
 * La requête qu'un requérant possède. Son outcome est publié par le manager (résolution, abandon,
 * cancel, close) ; [UIKitSceneWindowRequest.terminate] est un CAS `Pending → Terminated` ATOMIQUE
 * — le premier écrivain gagne, les suivants perdent sans jamais réécrire un terminal publié.
 * C'est ce qui rend sûre la cohabitation d'écrivains sous le lock du manager (cancel, detach,
 * settle de la résolution) et HORS de lui (throw du provider, drain du close, abandon) : sur
 * Kotlin/Native, le check-then-act du miroir web — sûr seulement sur le JS mono-thread —
 * laissait deux écrivains publier, le dernier gagnant retournant l'outcome.
 */
internal class UIKitSceneWindowRequest internal constructor(
    override val id: WindowRequestId,
    internal val resolver: UIKitWindowSceneRequestResolver,
    private val manager: UIKitWindowSceneManager,
) : WindowRequest {
    private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Pending)

    override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

    override fun close() {
        manager.detachSceneRequest(this)
    }

    override suspend fun cancel(): WindowCancellationOutcome = manager.cancelSceneRequest(this)

    override suspend fun await(): WindowRequestOutcome =
        state.filterIsInstance<WindowRequestState.Terminated>().first().outcome

    /**
     * Publie [outcome] si la requête est encore `Pending` — atomiquement. Rend `true` quand CETTE
     * publication a gagné ; `false` signifie « déjà terminale, l'outcome publié est celui-là ».
     */
    internal fun terminate(outcome: WindowRequestOutcome): Boolean =
        mutableState.compareAndSet(WindowRequestState.Pending, WindowRequestState.Terminated(outcome))

    internal fun terminalOutcome(): WindowRequestOutcome? =
        (mutableState.value as? WindowRequestState.Terminated)?.outcome
}

/**
 * La demande sans provider (tvOS) : l'octet près le `UnsupportedWindowRequest` du runtime — une
 * requête déjà `Rejected(Unsupported(RequestWindow))` que le requérant possède et libère.
 */
private class UIKitUnsupportedSceneRequest(
    override val id: WindowRequestId,
) : WindowRequest {
    private val outcome = WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Terminated(outcome))

    override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

    override fun close() = Unit

    override suspend fun cancel(): WindowCancellationOutcome =
        WindowCancellationOutcome.AlreadyTerminated(outcome)

    override suspend fun await(): WindowRequestOutcome = outcome
}
