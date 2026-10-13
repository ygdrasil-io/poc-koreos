package org.graphiks.kadre.host.android

import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy

/**
 * La façade Java d'attach Android (INTEROP-EXPORTS §4) — le miroir structurel de la façade
 * Swift `platform/uikit/swift/Sources/KadreHost/`. Les deux statiques délèguent aux quatre
 * overloads Kotlin `attachKadre` (P1) et traduisent le `KadreResult` : une admission refusée
 * lève `KadreException` (la failure est intacte, jamais null), un succès rend le billet
 * [KadreSessionHandle]. Aucun type de coroutine ne traverse la surface publique — le scan
 * réflexif `KadreAndroidFacadeTest.noLeakedAsyncTypes` épingle la règle.
 *
 * Contrat de thread de l'attach : le thread principal Android, comme les overloads Kotlin
 * (la violation reste une `IllegalStateException` de programmation, pas un `KadreException`).
 * La main d'œuvre de l'observation et de l'attente de terminaison court sur le dispatcher
 * du host ([AndroidHostFacadeDispatch] — le thread principal Android).
 */
public final class KadreAndroid private constructor() {
    public companion object {
        @JvmStatic
        @JvmOverloads
        @Throws(KadreException::class)
        public fun attach(
            activity: ComponentActivity,
            surfaceView: View,
            applicationFactory: KadreApplicationFactory,
            policy: KadrePolicy = KadrePolicies.Default,
        ): KadreSessionHandle = attachAndHandle { activity.attachKadre(surfaceView, applicationFactory, policy) }

        @JvmStatic
        @JvmOverloads
        @Throws(KadreException::class)
        public fun attach(
            view: View,
            lifecycleOwner: LifecycleOwner,
            applicationFactory: KadreApplicationFactory,
            policy: KadrePolicy = KadrePolicies.Default,
        ): KadreSessionHandle = attachAndHandle { view.attachKadre(lifecycleOwner, applicationFactory, policy) }
    }
}

/** La phase d'une session, transposée Java-safe de [SessionState] (§3 — même orthographe sémantique). */
public enum class KadreSessionPhase {
    Starting,
    Running,
    Stopping,
    Terminated,
}

/**
 * Le snapshot Java-safe d'une session (§4) : l'identifiant opaque alloué par la façade (pas
 * le `SessionId` Kotlin rédigé — le modèle de l'interop web), la phase courante, et
 * l'outcome terminal présent uniquement en phase [KadreSessionPhase.Terminated].
 */
public data class KadreSessionSnapshot(
    public val id: String,
    public val phase: KadreSessionPhase,
    public val outcome: SessionOutcome?,
)

/**
 * La couture de mapping partagée par les deux statiques publics : LE code exact qui entoure
 * `attachKadre`. `Failure` → `KadreException` (le mapping de `getOrThrow`, la failure est
 * intacte, jamais null) ; `Success` → le billet sur le dispatcher du host (le caller de test
 * injecte le sien — aucune View n'est constructible hors émulateur, cf. les fakes P1).
 */
internal fun attachAndHandle(
    dispatch: HandleDispatcher = AndroidHostFacadeDispatch,
    attach: () -> KadreResult<KadreSession>,
): KadreSessionHandle = when (val result = attach()) {
    is KadreResult.Success -> KadreSessionHandle(result.value, dispatch)
    is KadreResult.Failure -> throw KadreException(result.reason)
}

/**
 * Le billet vivant d'une session attachée, verbatim §4 : l'identifiant opaque, le snapshot
 * courant, l'observation (émission immédiate du courant, puis chaque changement
 * séquentiellement dans l'ordre d'inscription), le stage de terminaison stable, l'arrêt
 * motivé et la fermeture idempotente.
 *
 * CONTRATS (ruling P9-0) :
 *  — [observeState] invoque le consumer IMMÉDIATEMENT avec le snapshot courant, puis
 *    séquentiellement pour chaque changement (UN collecteur unique pour le billet —
 *    l'ordre d'inscription fait autorité). Fermer le token retire l'observer sans arrêter
 *    la session. Une exception du consumer désinscrit CET observer et est remise au
 *    handler d'exceptions non capturées du dispatcher host — sans terminer Kadre.
 *  — [termination] retourne toujours LA MÊME [CompletionStage] ; `toCompletableFuture`
 *    arme un waiter DÉRIVÉ par appel : l'annuler ne retire que CE waiter, jamais la
 *    session (le miroir exact du waiter isolé Swift). Les waiters actifs sont complétés
 *    avec l'outcome terminal réel — `Stopped(HostRequested)` après [requestStop].
 *  — [close] est idempotent : arrêt host demandé à la session (le `close()` Kotlin =
 *    `requestHostStop`, le modèle Swift) + résiliation des observations vivantes. Le
 *    waiter de terminaison lui survit : le stage se complète avec l'outcome réel même
 *    après close. Une observation post-close est muette d'office.
 *
 * Le billet n'est pas construit par les consumers : les deux statiques de [KadreAndroid]
 * et la couture interne en ont le monopole (le modèle du handle Swift `init(session:)`).
 */
public class KadreSessionHandle internal constructor(
    private val session: KadreSession,
    private val dispatch: HandleDispatcher = AndroidHostFacadeDispatch,
    private val reportObserverFailure: (Throwable) -> Unit = ::reportObserverFailureToUncaught,
) : AutoCloseable {

    private val lock = Any()

    /** L'identifiant opaque alloué par la façade — pas le `SessionId` Kotlin (§3, modèle web). */
    private val identity: String = "kadre-host-session-${IDENTITY_COUNTER.incrementAndGet()}"

    private var closed = false

    /** Les observers vivants, dans l'ordre d'inscription — l'ordre de livraison. */
    private val observers = ArrayList<Consumer<KadreSessionSnapshot>>()

    /** Le collecteur unique des changements d'état ; résilié au close. */
    private var fanout: AutoCloseable? = null

    /** L'état dont le snapshot est déjà parti à l'émission immédiate du premier observer. */
    private var seededState: SessionState? = null

    /** Le terminal commun de tous les waiters — complété par le waiter de terminaison. */
    private val terminal = CompletableFuture<SessionOutcome>()

    /** LE stage stable de §4 — le même objet à chaque [termination]. */
    private val stage: CompletionStage<SessionOutcome> = TerminalStage()

    init {
        // Le waiter de terminaison : il survit au close (seul le fanout y est résilié) —
        // la promesse du stage tient même après la fermeture du billet.
        armLaunch {
            try {
                terminal.complete(session.awaitTermination())
            } catch (cause: CancellationException) {
                // Le dispatcher host s'est éteint avec le processus — plus aucun waiter à compléter.
            } catch (cause: Throwable) {
                terminal.completeExceptionally(cause)
            }
        }
    }

    /** L'identifiant opaque de la session alloué par la façade (§3 — jamais structuré). */
    public fun id(): String = identity

    /** Le snapshot COURANT : la valeur du StateFlow Kotlin, transposée Java-safe. */
    public fun state(): KadreSessionSnapshot = snapshotOf(session.state.value)

    /** LE stage de terminaison (§4) : le même [CompletionStage] à chaque appel. */
    public fun termination(): CompletionStage<SessionOutcome> = stage

    /**
     * L'observation (§4) : émission immédiate du snapshot courant au retour de l'appel,
     * puis chaque changement séquentiellement dans l'ordre d'inscription. Le token rendu
     * retire l'observer sans arrêter la session ; après [close], l'observation est muette.
     * Course NARROW du seeding : l'état peut changer entre la lecture du seed (hors verrou)
     * et l'armement du collecteur, qui re-délivrerait alors le courant une fois — bénin sur
     * le thread principal, seul thread d'attach et d'observation, qui sérialise ces instants.
     */
    public fun observeState(observer: Consumer<KadreSessionSnapshot>): AutoCloseable {
        synchronized(lock) {
            if (closed) return AutoCloseable { }
        }
        // L'émission immédiate du courant — le collecteur est SEMÉ avec cet état pour que
        // sa première re-émission (le courant de la souscription) soit dédupliquée. Un
        // consumer lançant à cette émission ne devient JAMAIS un observer enregistré.
        val seed = session.state.value
        val survived = deliverTo(observer, snapshotOf(seed))
        synchronized(lock) {
            if (closed) {
                // close() a passé entre-temps : l'émission immédiate a eu lieu (l'appel a
                // DÉBUTÉ pré-close), la collecte n'est jamais armée — token muet.
                return AutoCloseable { }
            }
            if (survived) {
                observers.add(observer)
                if (fanout == null) {
                    seededState = seed
                    fanout = armFanout()
                }
            }
        }
        return AutoCloseable { unregister(observer) }
    }

    /** L'arrêt motivé (§4) : délégation directe — la terminaison portera `Stopped(HostRequested)`. */
    public fun requestStop() {
        session.requestStop()
    }

    /** La fermeture (§4) — idempotente : arrêt host demandé, observations résiliées. */
    override fun close() {
        val fan = synchronized(lock) {
            if (closed) return
            closed = true
            observers.clear()
            val fan = fanout
            fanout = null
            fan
        }
        fan?.close()
        session.close()
    }

    /** Le collecteur unique : chaque changement d'état part aux observers, dans l'ordre. */
    private suspend fun collectStateChanges() {
        val seed = synchronized(lock) { seededState }
        var first = true
        session.state.collect { state ->
            if (first) {
                first = false
                if (state == seed) return@collect
            }
            deliverAll(snapshotOf(state))
        }
    }

    /** Une livraison aux observers vivants, séquentielle, dans l'ordre d'inscription. */
    private fun deliverAll(snapshot: KadreSessionSnapshot) {
        val current = synchronized(lock) { observers.toList() }
        for (observer in current) deliverTo(observer, snapshot)
    }

    /**
     * Une livraison protégée : une exception du consumer désinscrit CET observer et est
     * remise au handler installé (production : le handler d'exceptions non capturées du
     * thread du dispatcher host) — elle n'atteint jamais la session ni les autres observers.
     * Le retour dit si l'observer a survécu (il ne devient un observer enregistré que s'il
     * a absorbé l'émission immédiate).
     */
    private fun deliverTo(observer: Consumer<KadreSessionSnapshot>, snapshot: KadreSessionSnapshot): Boolean = try {
        observer.accept(snapshot)
        true
    } catch (cause: Throwable) {
        unregister(observer)
        reportObserverFailure(cause)
        false
    }

    private fun unregister(observer: Consumer<KadreSessionSnapshot>) {
        synchronized(lock) { observers.remove(observer) }
    }

    /** La transposition Java-safe d'un état Kotlin (les mêmes faits, aucune coroutine). */
    private fun snapshotOf(state: SessionState): KadreSessionSnapshot = KadreSessionSnapshot(
        id = identity,
        phase = when (state) {
            SessionState.Starting -> KadreSessionPhase.Starting
            SessionState.Running -> KadreSessionPhase.Running
            SessionState.Stopping -> KadreSessionPhase.Stopping
            is SessionState.Terminated -> KadreSessionPhase.Terminated
        },
        outcome = (state as? SessionState.Terminated)?.outcome,
    )

    /**
     * Arme un travail autonome sur le dispatcher host — privé, la couture coroutine ne
     * traverse jamais une signature publique (le scan zéro-fuite la refuserait).
     */
    private fun armLaunch(block: suspend () -> Unit): AutoCloseable {
        val job = CoroutineScope(dispatch.hostContext()).launch { block() }
        return AutoCloseable { job.cancel() }
    }

    /** Le collecteur unique des changements d'état, armé au premier observer survivant. */
    private fun armFanout(): AutoCloseable = armLaunch { collectStateChanges() }

    /**
     * LE stage stable de §4 : le même objet à chaque appel, délégué au terminal commun —
     * SEULE la conversion en future est redéfinie : chaque `toCompletableFuture` arme un
     * waiter dérivé, annulable SANS toucher ni au terminal ni à la session (l'annulation
     * d'un waiter déjà réglé retombe sur un slot déjà pris : no-op, le modèle Swift).
     */
    private inner class TerminalStage : CompletionStage<SessionOutcome> by terminal {
        override fun toCompletableFuture(): CompletableFuture<SessionOutcome> {
            val waiter = CompletableFuture<SessionOutcome>()
            val registration = terminal.whenComplete { outcome, error ->
                if (error != null) waiter.completeExceptionally(error) else waiter.complete(outcome)
            }
            // Un waiter réglé (complété ou annulé) décroche sa registration du terminal.
            waiter.whenComplete { _, _ -> registration.cancel(false) }
            return waiter
        }
    }

    private companion object {
        val IDENTITY_COUNTER = AtomicLong()
    }
}

/**
 * La couture du dispatcher host — injectable (les host tests passent le leur : le
 * dispatcher main n'existe pas sur le JVM hôte, le modèle de `AndroidHostThreadDispatch`).
 * La couture ne transporte AUCUN bloc suspend : une interface interne ne mangle pas ses
 * méthodes, une signature porteuse de `Continuation` fuirait dans le bytecode public —
 * seul le [CoroutineContext] (type admis) traverse. Les lancements vivent privés dans le
 * billet (`armLaunch`).
 */
internal fun interface HandleDispatcher {
    /** Le contexte du dispatcher host — celui des observations et des waiters. */
    public fun hostContext(): CoroutineContext
}

/** La couture coroutine : le contexte reçu, tel quel. */
internal class CoroutineContextHandleDispatcher(private val context: CoroutineContext) : HandleDispatcher {
    override fun hostContext(): CoroutineContext = context
}

/**
 * La couture de production : le dispatcher du host Android — le thread principal, celui
 * qui attache (le modèle des overloads P1 ; `lifecycleScope` l'exige déjà des consumers).
 * L'initialisation est paresseuse : le JVM hôte n'a pas de dispatcher main, les host tests
 * n'y touchent jamais.
 */
internal object AndroidHostFacadeDispatch : HandleDispatcher {
    override fun hostContext(): CoroutineContext = Dispatchers.Main.immediate
}

/**
 * Le rapport d'un consumer lançant en production : remis au handler d'exceptions non
 * capturées du thread courant — le thread du dispatcher host (le principal), le canal
 * normal d'un throwable qui échappe au host — sans jamais toucher à la session.
 */
internal fun reportObserverFailureToUncaught(cause: Throwable) {
    val thread = Thread.currentThread()
    val handler = thread.uncaughtExceptionHandler ?: Thread.getDefaultUncaughtExceptionHandler()
    handler?.uncaughtException(thread, cause)
}
