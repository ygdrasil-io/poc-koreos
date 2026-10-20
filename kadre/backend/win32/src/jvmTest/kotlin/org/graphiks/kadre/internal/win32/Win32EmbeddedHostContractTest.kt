package org.graphiks.kadre.internal.win32

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import org.graphiks.kadre.application.SessionState
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec

/**
 * Real embedded host proof on the live Windows desktop: a real AWT frame
 * owns the host loop, two real sessions attach on the EDT through the real
 * KFFI port (real control window, real top-level windows), the closing of
 * one session preserves the other and the host loop, and the detach never
 * posts WM_QUIT (the frame stays visible). The EDT is never blocked: the
 * pump ticks and the state watchers run off it, exactly like production.
 * Windows-only by nature.
 */
class Win32EmbeddedHostContractTest {
    @Test
    fun twoEmbeddedSessionsCoexistOnTheRealEdtAndDetachLeavesTheHostLoopAlive() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        Win32HostOwnership.resetForTesting()
        val frame = javax.swing.JFrame("kadre embedded host")
        frame.setSize(320, 200)
        frame.isVisible = true
        try {
            val done = CountDownLatch(1)
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            java.awt.EventQueue.invokeLater {
                try {
                    val provider = Win32BackendProvider()
                    val request: (CoroutineScope) -> DesktopEmbeddedRequest = { scope ->
                        DesktopEmbeddedRequest(
                            parentScope = scope,
                            applicationFactory = KadreApplicationFactory { KadreApplication {
                                val requested = windows.requestWindow(
                                    WindowSpec(title = "kadre embedded session", contentSize = LogicalSize(240.0, 160.0)),
                                )
                                check(requested is KadreResult.Success<*>) { "window request failed: $requested" }
                                (requested as KadreResult.Success<*>).value.let { request ->
                                    if (request is org.graphiks.kadre.window.WindowRequest) runBlocking { request.await() }
                                }
                            } },
                            integration = DesktopIntegrationKind.AwtEventDispatchThread,
                            policy = KadrePolicies.Default,
                        )
                    }
                    val scope1 = CoroutineScope(Job() + EmptyCoroutineContext)
                    val scope2 = CoroutineScope(Job() + EmptyCoroutineContext)
                    val first = provider.attach(request(scope1))
                    val second = provider.attach(request(scope2))
                    check(first is KadreResult.Success<KadreSession>) { "first attach: $first" }
                    check(second is KadreResult.Success<KadreSession>) { "second attach: $second" }
                    val s1: KadreSession = first.value
                    val s2: KadreSession = second.value
                    val watcher = Thread({
                        try {
                            runBlocking {
                                withTimeout(20_000) {
                                    s1.state.first { state -> state == SessionState.Running }
                                    s2.state.first { state -> state == SessionState.Running }
                                }
                                s1.close()
                                withTimeout(20_000) { s1.awaitTermination() }
                                scope1.cancel() // releases via invokeOnCompletion
                                awaitEmbeddedRelease(1)
                                s2.state.first { state -> state == SessionState.Running }
                                s2.close()
                                withTimeout(20_000) { s2.awaitTermination() }
                                scope2.cancel() // releases via invokeOnCompletion
                                awaitEmbeddedRelease(0)
                            }
                        } catch (caught: Throwable) {
                            failure.compareAndSet(null, caught)
                        } finally {
                            scope1.cancel()
                            scope2.cancel()
                            done.countDown()
                        }
                    }, "kadre-embedded-proof-watcher")
                    watcher.isDaemon = false
                    watcher.start()
                } catch (caught: Throwable) {
                    failure.set(caught)
                    done.countDown()
                }
            }
            assertTrue(done.await(120, TimeUnit.SECONDS), "embedded proof did not finish within the bounded wait")
            failure.get()?.let { throw it }
            assertTrue(frame.isShowing, "the host frame must survive the detach")
        } finally {
            frame.dispose()
        }
    }

    private fun awaitEmbeddedRelease(expectedRemaining: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (Win32HostOwnership.embeddedSessions() > expectedRemaining) {
            if (System.currentTimeMillis() > deadline) {
                error("embedded registration release timed out (remaining=${Win32HostOwnership.embeddedSessions()})")
            }
            Thread.sleep(25)
        }
    }
}
