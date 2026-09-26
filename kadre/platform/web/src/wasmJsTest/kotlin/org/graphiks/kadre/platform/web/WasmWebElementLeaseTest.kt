package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The element escape hatch called on a real attached node.
 *
 * The shared `WebElementLeaseTest` covers the lifetime core without a DOM reference; this suite is
 * the one place the public extension runs over the SDK element type the facade promises, so the
 * capability that announces it is checked here too.
 */
class WasmWebElementLeaseTest {
    @Test
    @OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
    fun theAttachedElementIsLentWhileAttachedAndNothingAfterTheElementLeaves() = runTest {
        val host = existingHostElement()
        val scopeReady = CompletableDeferred<KadreScope>()
        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attachKadre(this) {
                scopeReady.complete(this)
                awaitCancellation()
            },
        ).value
        testScheduler.runCurrent()

        try {
            val surface = scopeReady.await().primarySurface.value
                ?: error("a web session exposes a primary surface")
            assertIs<Capability.Supported<Unit>>(
                surface.capabilities.value.platformAccess,
                "an attached web surface announces the element it can lend",
            )

            val leased = assertIs<KadreResult.Success<HTMLElement>>(surface.withWebElement { element -> element })

            assertSame(host, leased.value, "the callback receives the attached node itself")

            host.remove()
            awaitSurface("the surface stops admitting once the element leaves the document") {
                surface.state.value.attachment == SurfaceAttachmentState.Detached
            }

            assertIs<Capability.Unsupported>(
                surface.capabilities.value.platformAccess,
                "a surface that stopped admitting announces no element access",
            )
            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
                surface.withWebElement { element -> element },
            )
        } finally {
            session.requestStop()
            testScheduler.runCurrent()
            host.remove()
        }
    }

    private fun existingHostElement(): HTMLElement =
        (document.createElement("div") as HTMLElement).also {
            it.style.width = "320px"
            it.style.height = "180px"
            document.body!!.appendChild(it)
        }

    /**
     * Waits for a DOM-driven observation to reach the surface, alternating the two clocks it needs.
     *
     * The browser delivers the mutation on the real event loop while the runtime that reacts to it
     * advances on the test clock, so neither can be awaited alone: [withContext] hands the browser
     * its turn and the scheduler drains the tasks that turn published.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.awaitSurface(what: String, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + 2.seconds
        while (!condition() && deadline.hasNotPassedNow()) {
            withContext(Dispatchers.Default) { delay(5) }
            testScheduler.runCurrent()
        }
        assertTrue(condition(), "the browser never delivered: $what")
    }
}
