package org.graphiks.kadre.platform.android

import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Les sept comportements normatifs du bail d'accès natif (phase 2, tâche 4) : le block reçoit
 * la View de la surface sur le thread host (inline si l'appelant y est déjà, sinon dispatché),
 * le bail unique (`TemporarilyUnavailable(retryable = true)`), le `Closed(Surface)` du terminal,
 * la propagation TELLE QUELLE de l'exception applicative (bail libéré quand même), le refus
 * `Unsupported(PlatformSurfaceAccess)` d'une surface non Android, la complétion d'un callback
 * DÉMARRÉ malgré la cancellation de l'appelant, et l'honnêteté des capabilities après
 * l'installation de l'observation — retirées au plus tard au détached, jamais après révocation.
 *
 * Les View des host tests sont des faux `Any()` — aucune View n'est constructible hors émulateur
 * (le stub android.jar lance à la construction, cf. `AndroidHostSessionResultTest`). Le port porte
 * `Any` en interne (le miroir du `WebElementLeasePort` web) ; le cast `View` vit à la façade
 * publique, dont le chemin nominal est prouvé sur émulateur par le scénario contractuel AND-003
 * (Task 6).
 */
class AndroidViewLeaseTest {

    /** La surface sous test : identité du runtime (friend), clock inerte, coutures injectées. */
    private fun surface(
        hostThread: AndroidHostThreadDispatch = AndroidMainThreadDispatch,
        leasedView: () -> Any? = { null },
    ): AndroidHostSurface = AndroidHostSurface(
        id = RuntimeProcessIds.nextSurfaceId(),
        initialState = initialSurfaceStateForLeaseTest(),
        frameClock = { InertLeaseFrameClock },
        hostThread = hostThread,
        leasedView = leasedView,
    )

    @Test
    fun leaseRunsBlockWithTheViewOnHostThread() {
        TestMainThread().use { main ->
            val fake = Any()
            val surface = surface(hostThread = main.seam, leasedView = { fake })
            val lease = surface as AndroidViewLeasePort
            runBlocking {
                // Appelant HORS thread host : le block est dispatché sur le thread main du
                // test, avec la View de la surface.
                val seen = CompletableDeferred<Pair<Any, Thread>>()
                val dispatched = lease.lease { view ->
                    seen.complete(view to Thread.currentThread())
                    view
                }
                val (seenView, seenThread) = withTimeout(10_000) { seen.await() }
                assertIs<KadreResult.Success<Any>>(dispatched)
                assertSame(fake, seenView, "the block receives the surface's view")
                assertSame(main.thread, seenThread, "a caller off the host thread is dispatched")

                // Appelant DÉJÀ sur le thread host : inline — même thread, aucun transfert.
                withContext(main.dispatcher) {
                    var inlineThread: Thread? = null
                    lease.lease { view ->
                        inlineThread = Thread.currentThread()
                        view
                    }
                    assertSame(main.thread, inlineThread, "an already-host caller runs inline")
                }
            }
        }
    }

    @Test
    fun secondLeaseWhileInFlightIsTemporaryWithoutInvoking() {
        TestMainThread().use { main ->
            val surface = surface(hostThread = main.seam, leasedView = { Any() })
            val lease = surface as AndroidViewLeasePort
            runBlocking {
                val held = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val first = launch {
                    assertIs<KadreResult.Success<Unit>>(
                        lease.lease { _ ->
                            held.complete(Unit)
                            release.await()
                        },
                    )
                }
                withTimeout(10_000) { held.await() }

                var secondInvoked = false
                val second = lease.lease { secondInvoked = true }

                assertEquals(
                    KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
                    second,
                    "a lease started while another is in flight is a retryable temporary failure",
                )
                assertFalse(secondInvoked, "the refused lease never invokes its block")

                // Le premier lease se poursuit : le block suspendu s'achève normalement.
                release.complete(Unit)
                withTimeout(10_000) { first.join() }
            }
        }
    }

    @Test
    fun leaseAfterCloseIsClosedSurfaceWithoutInvoking() {
        val surface = surface(leasedView = { Any() })
        val lease = surface as AndroidViewLeasePort
        surface.close()

        var invoked = false
        val result = runBlocking { lease.lease { invoked = true } }

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
            result,
        )
        assertFalse(invoked, "a closed surface never invokes the block")
    }

    @Test
    fun applicationExceptionPropagatesAsIs() {
        TestMainThread().use { main ->
            val surface = surface(hostThread = main.seam, leasedView = { Any() })
            val lease = surface as AndroidViewLeasePort
            runBlocking {
                val boom = LeaseApplicationException.boom()

                val raised = runCatching { lease.lease { throw boom } }.exceptionOrNull()

                assertSame(
                    boom,
                    raised,
                    "the block exception propagates as-is, never wrapped into a KadreFailure",
                )

                // Le bail est libéré malgré l'exception : un lease suivant réussit.
                assertIs<KadreResult.Success<Any>>(
                    lease.lease { it },
                    "the lease is released before the exception reaches the caller",
                )
            }
        }
    }

    @Test
    @OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
    fun nonAndroidSurfaceIsUnsupportedPlatformAccess() {
        val foreign: HostSurface = ForeignSurface()

        var invoked = false
        val result = runBlocking { foreign.withAndroidView { invoked = true; it } }

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess)),
            result,
            "a surface that is not an android one lends nothing",
        )
        assertFalse(invoked, "the block is never invoked on a non-android surface")
    }

    @Test
    fun startedCallbackCompletesOnHostThreadDespiteCancellation() {
        TestMainThread().use { main ->
            val surface = surface(hostThread = main.seam, leasedView = { Any() })
            val lease = surface as AndroidViewLeasePort
            runBlocking {
                val started = CompletableDeferred<Unit>()
                val proceed = CompletableDeferred<Unit>()
                var completed = false
                val caller = launch {
                    lease.lease { _ ->
                        started.complete(Unit)
                        proceed.await()
                        completed = true
                    }
                }
                withTimeout(10_000) { started.await() }

                // La cancellation de l'appelant pendant le block : le callback DÉMARRÉ n'est
                // PAS interrompu — il s'achève — et l'appelant observe SA cancellation.
                caller.cancel()
                proceed.complete(Unit)
                withTimeout(10_000) { caller.join() }

                assertTrue(completed, "the started callback ran to completion")
                assertTrue(caller.isCancelled, "the caller observed its cancellation")

                // Le bail est libéré malgré la cancellation : un lease suivant réussit.
                assertIs<KadreResult.Success<Any>>(
                    lease.lease { it },
                    "the cancellation released the lease instead of leaving it held",
                )
            }
        }
    }

    @Test
    fun callerCancelledBeforeTheLeaseNeverStartsTheBlock() {
        TestMainThread().use { main ->
            val surface = surface(hostThread = main.seam, leasedView = { Any() })
            val lease = surface as AndroidViewLeasePort
            runBlocking {
                var invoked = false
                var parked: Continuation<Unit>? = null
                val parkedSeen = CompletableDeferred<Unit>()
                val seen = CompletableDeferred<Result<KadreResult<Unit>>>()
                // Le caller s'installe sur le thread host (chemin inline) et s'y gare dans une
                // suspension BRUTE (`suspendCoroutine`, invisible à la cancellation du job) :
                // le test annule SON JOB pendant le park puis le laisse reprendre — l'appel du
                // bail part dans une coroutine DÉJÀ annulée, exactement la « cancellation
                // précoce » de la roadmap (« aucun callback après fermeture ou cancellation
                // précoce »). (Le park dans `withContext(NonCancellable)` masquerait le job du
                // caller : le contexte y porterait le job NonCancellable, toujours actif.)
                val caller = launch(main.dispatcher) {
                    suspendCoroutine<Unit> { cont ->
                        parked = cont
                        parkedSeen.complete(Unit)
                    }
                    seen.complete(runCatching { lease.lease { invoked = true } })
                }
                withTimeout(10_000) { parkedSeen.await() }
                caller.cancel()
                checkNotNull(parked).resumeWith(Result.success(Unit))
                withTimeout(10_000) { caller.join() }

                assertIs<CancellationException>(
                    seen.await().exceptionOrNull(),
                    "the caller cancelled before the lease observes its cancellation",
                )
                assertFalse(
                    invoked,
                    "a caller cancelled before the lease never starts the block",
                )

                // Le bail n'a jamais été posé : un lease suivant réussit.
                assertIs<KadreResult.Success<Any>>(
                    lease.lease { it },
                    "the pre-admission cancellation left no lease held",
                )
            }
        }
    }

    @Test
    fun capabilitiesBecomeHonestAfterObservationInstalled() = runBlocking {
        val surface = surface()
        // Avant l'installation : le snapshot par défaut, aucune annonce.
        assertEquals(unsupportedSurfaceCapabilities(), surface.capabilities.value)

        // L'observation et le scheduling installés (la couture du binding, Task 5) : les
        // capabilities deviennent honnêtes — l'annonce de la phase 2 (`platformAccess`,
        // la rangée Android qui couvre metrics + redraw + l'accès natif borné), les refus
        // d'update inchangés.
        surface.observationInstalled(inputWired = false)
        val honest = surface.capabilities.value
        assertIs<Capability.Supported<Unit>>(
            honest.platformAccess,
            "an installed surface announces what it can now do",
        )
        assertEquals(
            unsupportedSurfaceCapabilities(),
            honest.copy(
                platformAccess = Capability.Unsupported(
                    KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess),
                ),
            ),
            "the announcement adds nothing to the refusals the phase keeps",
        )

        // La fenêtre de teardown, côté M-1 : le binding publie l'instantané détaché canonique
        // AVANT le terminal — une installation qui arrive dans cette fenêtre ne peut pas
        // repeindre `Supported` sur un état déjà détaché (la double garde de l'admission,
        // `detached` OU état publié détaché).
        val late = surface()
        late.publishMetrics(update = { it.copy(attachment = SurfaceAttachmentState.Detached) })
        late.observationInstalled(inputWired = false)
        assertEquals(
            unsupportedSurfaceCapabilities(),
            late.capabilities.value,
            "a late install cannot announce on a detached state",
        )

        // La révocation retire l'annonce au plus tard au détached (l'ordre du terminal),
        // et une installation tardive ne ressuscite rien — jamais après révocation.
        val detachedSeen = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            surface.state.collect { state ->
                if (state.attachment == SurfaceAttachmentState.Detached) detachedSeen.complete(Unit)
            }
        }
        repeat(8) { yield() }
        surface.close()
        withTimeout(10_000) { detachedSeen.await() }
        assertEquals(unsupportedSurfaceCapabilities(), surface.capabilities.value)
        surface.observationInstalled(inputWired = false)
        assertEquals(
            unsupportedSurfaceCapabilities(),
            surface.capabilities.value,
            "nothing resurrects the capabilities after the terminal",
        )
        collector.cancel()
    }
}

// ----------------------------------------------------------------------- aides du fichier

/**
 * Une exception applicative que le transport kotlinx-coroutines ne peut PAS cloner : la classe
 * n'expose aucun constructeur public de la forme `(String)`/`(Throwable)`/`(String, Throwable)`/`()`
 * que le copieur réflexif du recovery (`ExceptionsConstructor.tryCopyException`) cherche, il
 * renvoie donc `null` et l'instance ORIGINALE traverse les sauts de dispatcher. Le contrat
 * « propagée telle quelle » est ainsi épinglé sur l'identité de l'instance — inline comme
 * dispatché — plutôt que sur les détails du transport (un clone, même fidèle en classe et
 * message, n'est pas « telle quelle »).
 */
private class LeaseApplicationException private constructor() : IllegalStateException("boom") {
    companion object {
        fun boom(): LeaseApplicationException = LeaseApplicationException()
    }
}

/**
 * Une surface qui ne prête rien parce qu'elle n'est pas une surface Android (la façon dont une
 * AppKit ou web l'est) : elle observe et refuse tout. L'identité et l'état viennent d'une vraie
 * surface car ni `SurfaceId` ni `SurfaceRevision` n'ont de constructeur public hors de leur
 * module ; la résolution sous test ne lit ni l'une ni l'autre, et `input` lance fort pour qu'un
 * faux ne puisse pas passer pour une surface qui devrait répondre à sa place.
 */
private class ForeignSurface : HostSurface {
    override val id: SurfaceId = RuntimeProcessIds.nextSurfaceId()
    override val state: StateFlow<SurfaceState> = MutableStateFlow(initialSurfaceStateForLeaseTest())
    override val capabilities: StateFlow<SurfaceCapabilities> =
        MutableStateFlow(unsupportedSurfaceCapabilities())
    override val events: Flow<SurfaceEvent> = emptyFlow()
    override val input: SurfaceInput get() = error("a foreign surface is never asked for its input")

    override fun requestRedraw(): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestRedraw))

    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
}

/**
 * Le thread « main » du test : un exécuteur dédié à thread unique, le miroir exact de la couture
 * de production ([AndroidMainThreadDispatch] sonde le Looper main, celui-ci un thread étiqueté) —
 * inline si déjà dessus, sinon transfert. Le thread est forcé à la construction pour que
 * [thread] soit toujours lisible.
 */
private class TestMainThread : AutoCloseable {
    @Volatile
    private var threadRef: Thread? = null

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "android-host-main-test").also { threadRef = it }
    }

    /** Le dispatcher du thread main de test. */
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /** Le thread main de test. */
    val thread: Thread
        get() = threadRef ?: error("the test main thread was never started")

    /**
     * La couture que la surface reçoit : inline si l'appelant est déjà sur le thread main de
     * test, sinon transfert — la même décision que la production, sur une sonde de test.
     */
    val seam = object : AndroidHostThreadDispatch {
        override suspend fun <R> dispatch(block: suspend () -> R): R =
            if (Thread.currentThread() === threadRef) {
                block()
            } else {
                withContext(dispatcher) { block() }
            }
    }

    init {
        executor.submit {}
    }

    override fun close() {
        executor.shutdownNow()
    }
}

/** L'instantané initial de surface du test — les mêmes valeurs que la couture, base 1×1. */
private fun initialSurfaceStateForLeaseTest(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Focused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

/** La clock inerte du test : les frames y sont posées et n'y courent jamais (aucun SDK). */
private object InertLeaseFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
