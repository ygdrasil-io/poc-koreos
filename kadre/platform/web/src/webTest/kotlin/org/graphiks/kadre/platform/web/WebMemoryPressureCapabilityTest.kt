package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.HostSignal
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The memory-pressure capability line of this target, pinned on both browser-test targets.
 *
 * The browser exposes no memory-pressure primitive at all — no event, no callback, no poll — so the
 * session publishes the bare structural `FeatureAvailability.Unsupported` and nothing else. These
 * two cases are the non-invention pair `capabilities/web.md` §2 cites: the exact cell a live session
 * publishes, and the absence of any `HostSignal` across a lifecycle exercise, which is the shape a
 * fabrication would have to take (the runtime refuses `emitMemoryPressure` unless the cell says
 * `Available`, `RuntimeLifecycle.kt`, so a fabricated poll would have to move the cell first).
 */
class WebMemoryPressureCapabilityTest {
    @Test
    fun memoryPressureIsTheBareStructuralUnsupportedEveryStateOfTheSession() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val port = RecordingWebHostPort(initialMetrics())
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
            ),
        )
        testScheduler.runCurrent()

        try {
            // The exact literal, not a family: a cause-carrying `Unavailable(PlatformFailure)` would
            // claim a platform failure that never happened — there is no browser API whose refusal
            // could be reported — and `Available` would claim a source nobody installed.
            assertEquals(
                FeatureAvailability.Unsupported,
                scopeReady.await().lifecycle.capabilities.value.memoryPressure,
            )

            // Every lifecycle transition the port can deliver leaves the cell exactly where it
            // opened: focus loss, a hidden document, then the terminating detach of a pagehide.
            port.deliverLifecycle(snapshot(subtreeFocused = false))
            assertEquals(
                FeatureAvailability.Unsupported,
                scopeReady.await().lifecycle.capabilities.value.memoryPressure,
            )
            port.deliverLifecycle(snapshot(documentVisible = false))
            assertEquals(
                FeatureAvailability.Unsupported,
                scopeReady.await().lifecycle.capabilities.value.memoryPressure,
            )
        } finally {
            session.requestStop()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun noMemoryPressureSignalIsEverEmittedAcrossALifecycleExercise() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val port = RecordingWebHostPort(initialMetrics())
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
            ),
        )
        testScheduler.runCurrent()
        val scope = scopeReady.await()
        val signals = mutableListOf<HostSignal>()
        val collector = backgroundScope.launch { scope.lifecycle.signals.collect { signals += it } }
        testScheduler.runCurrent()

        try {
            // The full non-terminal exercise: the focus loss that neutralises input, the hidden
            // document that suspends, the focus gain that resumes. No signal exists to deliver, and
            // none is invented: the collector stays empty through every transition.
            port.deliverLifecycle(snapshot(subtreeFocused = false))
            port.deliverLifecycle(snapshot(documentVisible = false))
            port.deliverLifecycle(snapshot())
            testScheduler.runCurrent()
            assertEquals(emptyList(), signals)

            // And the terminal one: the pagehide detach ends the session with no last-instant signal.
            port.deliverLifecycle(port.pageHiddenSnapshot())
            testScheduler.advanceUntilIdle()
            assertEquals(emptyList(), signals)
        } finally {
            collector.cancel()
            session.requestStop()
            testScheduler.runCurrent()
        }
        assertTrue(session.state.value is SessionState.Terminated)
    }

    private fun successful(result: KadreResult<KadreSession>): KadreSession =
        assertIs<KadreResult.Success<KadreSession>>(result).value

    private fun initialMetrics(): WebSurfaceMetrics = WebSurfaceMetrics(
        logicalWidth = 1.0,
        logicalHeight = 1.0,
        scaleFactor = 1.0,
    )

    private fun snapshot(
        connected: Boolean = true,
        inOriginDocument: Boolean = true,
        documentVisible: Boolean = true,
        browsingContextFocused: Boolean = true,
        subtreeFocused: Boolean = true,
    ): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = connected,
        inOriginDocument = inOriginDocument,
        documentVisible = documentVisible,
        browsingContextFocused = browsingContextFocused,
        subtreeFocused = subtreeFocused,
    )
}
