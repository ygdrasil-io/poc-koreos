package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WebHostSessionTest {
    @Test
    fun hostSurfaceInitialAppearanceIsUnknown() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val session = successful(
            WebHostSession(RecordingPort(Any(), snapshot()), WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
            ),
        )
        testScheduler.runCurrent()

        try {
            assertEquals(
                SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
                scopeReady.await().primarySurface.value?.state?.value?.appearance,
            )
        } finally {
            session.requestStop()
            testScheduler.runCurrent()
        }
    }

    @OptIn(DelicateKadreApi::class)
    @Test
    fun hostSurfaceRawInputIsExplicitlyUnsupported() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val session = successful(
            WebHostSession(RecordingPort(Any(), snapshot()), WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
            ),
        )
        testScheduler.runCurrent()

        try {
            assertEquals(
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
                scopeReady.await().primarySurface.value?.input?.requestRawInput(),
            )
        } finally {
            session.requestStop()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun registryUsesReferentialIdentityWhenValuesAreEqual() {
        val registry = WebHostRegistry()
        val first = EqualityCollidingIdentity()
        val second = EqualityCollidingIdentity()

        val firstReservation = assertIs<KadreResult.Success<WebHostReservation>>(registry.reserve(first)).value
        val secondReservation = assertIs<KadreResult.Success<WebHostReservation>>(registry.reserve(second)).value
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            registry.reserve(first),
        )

        firstReservation.release()
        assertIs<KadreResult.Success<WebHostReservation>>(registry.reserve(first)).value.release()
        secondReservation.release()
    }

    @Test
    fun registryRejectsADuplicateReservationAndReleasesItExactlyOnce() {
        val registry = WebHostRegistry()
        val identity = Any()

        val reservation = assertIs<KadreResult.Success<WebHostReservation>>(registry.reserve(identity)).value
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            registry.reserve(identity),
        )

        reservation.release()
        reservation.release()

        assertIs<KadreResult.Success<WebHostReservation>>(registry.reserve(identity)).value.release()
    }

    @Test
    fun disconnectedStopWhenDetachedIsRejectedWithoutLeakingItsReservation() = runTest {
        val registry = WebHostRegistry()
        val identity = Any()
        val disconnected = RecordingPort(identity, snapshot(connected = false))

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("element")),
            WebHostSession(disconnected, registry).attach(this, factory(), KadrePolicies.Default),
        )
        assertEquals(0, disconnected.listenerInstallations)

        val connected = RecordingPort(identity, snapshot())
        val session = successful(
            WebHostSession(connected, registry).attach(this, factory(), KadrePolicies.Default),
        )
        session.requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun duplicateSessionDoesNotInstallAListenerAndReleaseFollowsTermination() = runTest {
        val registry = WebHostRegistry()
        val identity = Any()
        val owner = RecordingPort(identity, snapshot())
        val session = successful(
            WebHostSession(owner, registry).attach(this, factory(), KadrePolicies.Default),
        )
        val duplicate = RecordingPort(identity, snapshot())

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            WebHostSession(duplicate, registry).attach(this, factory(), KadrePolicies.Default),
        )
        assertEquals(1, owner.listenerInstallations)
        assertEquals(0, duplicate.listenerInstallations)

        session.requestStop()
        testScheduler.runCurrent()
        assertEquals(1, owner.releases)

        val replacement = RecordingPort(identity, snapshot())
        successful(WebHostSession(replacement, registry).attach(this, factory(), KadrePolicies.Default)).requestStop()
        testScheduler.runCurrent()
        assertEquals(1, replacement.releases)
    }

    @Test
    fun deliveredDetachTerminatesTheRuntimeAsHostDetached() = runTest {
        val port = RecordingPort(Any(), snapshot())
        val session = successful(
            WebHostSession(port, WebHostRegistry()).attach(this, factory(), KadrePolicies.Default),
        )

        port.deliver(snapshot(connected = false))

        assertEquals(
            SessionOutcome.Stopped(SessionStopReason.HostDetached),
            session.awaitTermination(),
        )
        assertEquals(1, port.releases)
    }

    @Test
    fun pagehideTerminatesAStubbornApplicationWithoutAwaitingShutdownTimeout() = runTest {
        val blocker = CompletableDeferred<Unit>()
        val timeout = 1.seconds
        val port = RecordingPort(Any(), snapshot())
        val policy = KadrePolicies.Default.copy(
            execution = KadrePolicies.Default.execution.copy(shutdownTimeout = timeout),
        )
        val session = successful(
            WebHostSession(port, WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        withContext(NonCancellable) { blocker.await() }
                    }
                },
                policy,
            ),
        )
        testScheduler.runCurrent()
        val expected = SessionOutcome.Stopped(SessionStopReason.HostDetached)

        port.deliver(snapshot(pageHidden = true))

        try {
            assertEquals(SessionState.Terminated(expected), session.state.value)
            assertEquals(expected, session.awaitTermination())
        } finally {
            blocker.complete(Unit)
            testScheduler.runCurrent()
        }
    }

    @Test
    fun ordinaryStopRevokesThePortButRetainsTheReservationUntilTermination() = runTest {
        val blocker = CompletableDeferred<Unit>()
        val registry = WebHostRegistry()
        val identity = Any()
        val port = RecordingPort(identity, snapshot())
        val session = successful(
            WebHostSession(port, registry).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        withContext(NonCancellable) { blocker.await() }
                    }
                },
                KadrePolicies.Default,
            ),
        )
        testScheduler.runCurrent()

        session.requestStop()

        try {
            assertEquals(SessionState.Stopping, session.state.value)
            assertEquals(1, port.releases)
            assertFalse(port.deliver(snapshot(connected = false)))
            assertEquals(
                KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
                WebHostSession(RecordingPort(identity, snapshot()), registry).attach(
                    this,
                    factory(),
                    KadrePolicies.Default,
                ),
            )
        } finally {
            blocker.complete(Unit)
            testScheduler.runCurrent()
        }
        assertIs<SessionState.Terminated>(session.state.value)

        val replacement = RecordingPort(identity, snapshot())
        successful(
            WebHostSession(replacement, registry).attach(this, factory(), KadrePolicies.Default),
        ).requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun cleanupFailureDuringLifecycleInstallationDoesNotEscapeOrLeakReservation() = runTest {
        val registry = WebHostRegistry()
        val identity = Any()
        val failingPort = RecordingPort(
            stableIdentity = identity,
            initialLifecycleSnapshot = snapshot(),
            lifecycleInstallationFailure = IllegalStateException("install"),
            cleanupFailure = IllegalStateException("cleanup"),
        )

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "lifecycle-install-failed"),
            ),
            WebHostSession(failingPort, registry).attach(this, factory(), KadrePolicies.Default),
        )
        assertEquals(1, failingPort.releases)

        successful(WebHostSession(RecordingPort(identity, snapshot()), registry).attach(
            this,
            factory(),
            KadrePolicies.Default,
        )).requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun metricsInstallationFailureIsReportedAndDoesNotLeakTheReservation() = runTest {
        val registry = WebHostRegistry()
        val identity = Any()
        val failingPort = RecordingPort(
            stableIdentity = identity,
            initialLifecycleSnapshot = snapshot(),
            metricsInstallationFailure = IllegalStateException("metrics"),
        )

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "metrics-install-failed"),
            ),
            WebHostSession(failingPort, registry).attach(this, factory(), KadrePolicies.Default),
        )
        assertEquals(1, failingPort.releases)

        successful(WebHostSession(RecordingPort(identity, snapshot()), registry).attach(
            this,
            factory(),
            KadrePolicies.Default,
        )).requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun ownershipReleaseAfterAttachFailureStopsTheGamepadPoll() {
        val dom = CountingGamepadDom()
        val frames = CountingFrameScheduler()
        val hub = WebGamepadHub(dom, frames)
        val ownership = WebHostOwnership(
            RecordingPort(Any(), snapshot()),
            assertIs<KadreResult.Success<WebHostReservation>>(WebHostRegistry().reserve(Any())).value,
        )
        val gamepadPort = hub.openPort()
        val events = mutableListOf<GamepadPortEvent>()
        gamepadPort.installObserver { events += it }
        ownership.observeGamepadPort(gamepadPort)

        ownership.releaseAfterAttachFailure()

        // The opened port was the only holder of the gamepad projection a failed attach would have
        // orphaned, so the release funnel closes it like every other resource: the poll is withdrawn
        // with the port and nothing can be delivered through the projection again.
        assertEquals(2, dom.registrationCloses, "both connect listeners are withdrawn")
        assertEquals(0, dom.listenerCount())
        assertEquals(0, frames.pendingCount(), "no pending frame survives the release")
        assertEquals(1, frames.cancels, "the one pending frame is cancelled exactly once")
        assertTrue(hub.gamepads(gamepadPort).isEmpty(), "the closed port projects nothing")
        assertEquals(0, events.size, "no delivery reaches the closed port's observer")
    }

    @Test
    fun attachFailureStopsTheHubPollingAndACleanStopStopsItAgainExactlyOnce() = runTest {
        val registry = WebHostRegistry()
        val identity = Any()
        val dom = CountingGamepadDom()
        val frames = CountingFrameScheduler()
        val hub = WebGamepadHub(dom, frames)
        val failingPort = RecordingPort(
            stableIdentity = identity,
            initialLifecycleSnapshot = snapshot(),
            lifecycleInstallationFailure = IllegalStateException("install"),
        )

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "lifecycle-install-failed"),
            ),
            WebHostSession(failingPort, registry, gamepads = hub).attach(this, factory(), KadrePolicies.Default),
        )
        assertEquals(1, failingPort.releases)
        // The eager open used to orphan the gamepad port on every attach-failure branch: the port was
        // captured only by a factory closure that never ran, so the page-global hub kept polling —
        // two listeners and one re-armed frame per retry, for a session that never existed.
        assertEquals(2, dom.registrationCloses, "the failed attach withdraws both connect listeners")
        assertEquals(0, dom.listenerCount())
        assertEquals(0, frames.pendingCount(), "the failed attach cancels the pending frame")
        assertEquals(1, frames.cancels)

        // A retry on the same hub opens a fresh port — nothing was closed permanently — and the
        // clean stop of that session stops the poll again: no zombie survives either ending.
        val session = successful(
            WebHostSession(RecordingPort(identity, snapshot()), registry, gamepads = hub).attach(
                this,
                factory(),
                KadrePolicies.Default,
            ),
        )
        assertEquals(4, dom.registrations, "the retry re-registers both listeners on the same hub")
        assertEquals(1, frames.pendingCount())
        session.requestStop()
        testScheduler.runCurrent()
        assertEquals(4, dom.registrationCloses, "the clean stop withdraws the listeners again")
        assertEquals(0, dom.listenerCount())
        assertEquals(0, frames.pendingCount(), "no poll survives the clean stop")
        assertEquals(2, frames.cancels)
    }

    @Test
    fun manualDisconnectedSessionPublishesAttachedBackgroundInactiveLifecycle() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val port = RecordingPort(Any(), snapshot(connected = false))
        val session = successful(
            WebHostSession(port, WebHostRegistry()).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
                WebAttachmentPolicy.Manual,
            ),
        )
        testScheduler.runCurrent()

        assertEquals(AttachmentState.Attached, scopeReady.await().lifecycle.state.value.attachment)
        assertEquals(VisibilityState.Background, scopeReady.await().lifecycle.state.value.visibility)
        assertEquals(ActivationState.Inactive, scopeReady.await().lifecycle.state.value.activation)

        session.requestStop()
        testScheduler.runCurrent()
    }

    private fun factory(): KadreApplicationFactory = KadreApplicationFactory {
        KadreApplication { awaitCancellation() }
    }

    private fun successful(result: KadreResult<KadreSession>): KadreSession =
        assertIs<KadreResult.Success<KadreSession>>(result).value

    private fun snapshot(
        connected: Boolean = true,
        inOriginDocument: Boolean = true,
        documentVisible: Boolean = true,
        browsingContextFocused: Boolean = true,
        subtreeFocused: Boolean = true,
        pageHidden: Boolean = false,
    ): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = connected,
        inOriginDocument = inOriginDocument,
        documentVisible = documentVisible,
        browsingContextFocused = browsingContextFocused,
        subtreeFocused = subtreeFocused,
        pageHidden = pageHidden,
    )

    private class RecordingPort(
        override val stableIdentity: Any,
        override val initialLifecycleSnapshot: WebLifecycleSnapshot,
        private val lifecycleInstallationFailure: Throwable? = null,
        private val metricsInstallationFailure: Throwable? = null,
        private val cleanupFailure: Throwable? = null,
    ) : WebHostPort {
        override val initialSnapshot: WebSurfaceMetrics = WebSurfaceMetrics(
            logicalWidth = 1.0,
            logicalHeight = 1.0,
            scaleFactor = 1.0,
        )

        var listenerInstallations: Int = 0
            private set
        var releases: Int = 0
            private set
        private var lifecycleObserver: ((WebLifecycleSnapshot) -> Unit)? = null

        override fun installLifecycleObserver(observer: (WebLifecycleSnapshot) -> Unit) {
            listenerInstallations += 1
            lifecycleObserver = observer
            lifecycleInstallationFailure?.let { throw it }
        }

        override fun installMetricsObserver(observer: (WebSurfaceMetrics) -> Unit) {
            metricsInstallationFailure?.let { throw it }
        }

        override fun release() {
            releases += 1
            lifecycleObserver = null
            cleanupFailure?.let { throw it }
        }

        fun deliver(snapshot: WebLifecycleSnapshot): Boolean =
            lifecycleObserver?.let {
                it(snapshot)
                true
            } ?: false
    }

    private class EqualityCollidingIdentity {
        override fun equals(other: Any?): Boolean = other is EqualityCollidingIdentity
        override fun hashCode(): Int = 1
    }

    /** The gamepad seam, counted: everything the hub registers, polls and withdraws is visible here. */
    private class CountingGamepadDom : WebGamepadDom {
        var pads: List<WebDomGamepad?> = emptyList()
        var polls: Int = 0
            private set
        var registrations: Int = 0
            private set
        var registrationCloses: Int = 0
            private set
        override val secureContext: Boolean = true
        private val appeared = mutableListOf<() -> Unit>()
        private val disappeared = mutableListOf<() -> Unit>()

        override fun getGamepads(): List<WebDomGamepad?> {
            polls += 1
            return pads
        }

        override fun onGamepadAppeared(listener: () -> Unit): AutoCloseable = register(listener, appeared)

        override fun onGamepadDisappeared(listener: () -> Unit): AutoCloseable = register(listener, disappeared)

        override fun close() = Unit

        fun listenerCount(): Int = appeared.size + disappeared.size

        private fun register(listener: () -> Unit, into: MutableList<() -> Unit>): AutoCloseable {
            registrations += 1
            into += listener
            return AutoCloseable {
                registrationCloses += 1
                into.remove(listener)
            }
        }
    }

    /** The frame cadence by hand, counted: the one pending frame the hub may hold at any moment. */
    private class CountingFrameScheduler : WebFrameScheduler {
        var schedules: Int = 0
            private set
        var cancels: Int = 0
            private set
        private var pending: (() -> Unit)? = null

        override fun schedule(frame: () -> Unit): AutoCloseable {
            schedules += 1
            pending = frame
            return AutoCloseable {
                cancels += 1
                if (pending === frame) pending = null
            }
        }

        fun pendingCount(): Int = if (pending == null) 0 else 1
    }
}
