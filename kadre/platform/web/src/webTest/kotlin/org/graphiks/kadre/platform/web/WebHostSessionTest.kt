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
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class WebHostSessionTest {
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
        private val cleanupFailure: Throwable? = null,
    ) : WebHostPort {
        override val initialSnapshot: WebSurfaceSnapshot = WebSurfaceSnapshot(
            logicalWidth = 1.0,
            logicalHeight = 1.0,
            physicalWidth = 1,
            physicalHeight = 1,
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

        override fun release() {
            releases += 1
            cleanupFailure?.let { throw it }
        }

        fun deliver(snapshot: WebLifecycleSnapshot) {
            checkNotNull(lifecycleObserver).invoke(snapshot)
        }
    }

    private class EqualityCollidingIdentity {
        override fun equals(other: Any?): Boolean = other is EqualityCollidingIdentity
        override fun hashCode(): Int = 1
    }
}
