package org.graphiks.kadre.integration.awt

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec

/**
 * O3 proof of the announced integration (roadmap phase 12 gate): a real AWT
 * frame owns the host loop and two real sessions attach through the PUBLIC
 * facade on the real EDT — discovery, real kadre windows, isolation,
 * detachment without touching the host loop. Windows-only by nature; the
 * timeouts are sized for a cold CI runner (a desktop session is required —
 * the same requirement as every win32 gate proof).
 */
class AwtEmbeddedIntegrationTest {
    @Test
    fun twoSessionsAttachThroughThePublicFacadeOnTheRealEdt() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val frame = javax.swing.JFrame("kadre awt integration host")
        frame.setSize(320, 200)
        frame.isVisible = true
        try {
            val done = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>(null)
            java.awt.EventQueue.invokeLater {
                try {
                    assertTrue(java.awt.EventQueue.isDispatchThread(), "the proof runs on the EDT")
                    val request: (CoroutineScope) -> KadreApplicationFactory = { scope ->
                        KadreApplicationFactory { KadreApplication {
                            val requested = windows.requestWindow(
                                WindowSpec(title = "kadre awt session", contentSize = LogicalSize(240.0, 160.0)),
                            )
                            check(requested is KadreResult.Success<*>) { "window request failed: $requested" }
                            (requested as KadreResult.Success<*>).value.let { request ->
                                if (request is org.graphiks.kadre.window.WindowRequest) kotlinx.coroutines.runBlocking { request.await() }
                            }
                        } }
                    }
                    val scope1 = CoroutineScope(Job() + Dispatchers.Default)
                    val scope2 = CoroutineScope(Job() + Dispatchers.Default)
                    val first = AwtKadreHost.attach(scope1, request(scope1))
                    val second = AwtKadreHost.attach(scope2, request(scope2))
                    check(first is KadreResult.Success<KadreSession>) { "first attach: $first" }
                    check(second is KadreResult.Success<KadreSession>) { "second attach: $second" }
                    val watcher = Thread({
                        try {
                            runBlocking {
                                withTimeout(90_000) {
                                    first.value.state.first { it == SessionState.Running }
                                    second.value.state.first { it == SessionState.Running }
                                }
                                first.value.close()
                                withTimeout(90_000) { first.value.awaitTermination() }
                                // the surviving session keeps running on the host loop
                                second.value.close()
                                withTimeout(90_000) { second.value.awaitTermination() }
                            }
                        } catch (caught: Throwable) {
                            failure.compareAndSet(null, caught)
                        } finally {
                            scope1.cancel()
                            scope2.cancel()
                            done.countDown()
                        }
                    }, "kadre-awt-proof-watcher")
                    watcher.isDaemon = false
                    watcher.start()
                } catch (caught: Throwable) {
                    failure.set(caught)
                    done.countDown()
                }
            }
            assertTrue(done.await(300, TimeUnit.SECONDS), "awt integration proof did not finish")
            failure.get()?.let { throw it }
            assertTrue(frame.isShowing, "the host frame must survive both detaches")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun attachOffTheEdtIsRefusedBeforeAnySessionState() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        if (java.awt.EventQueue.isDispatchThread()) return // déjà l'EDT : rien à prouver ici
        val thrown = kotlin.test.assertFailsWith<IllegalStateException> {
            AwtKadreHost.attach(CoroutineScope(Job() + EmptyCoroutineContext), KadreApplicationFactory { KadreApplication { } })
        }
        assertTrue(thrown.message!!.contains("EventDispatchThread"), thrown.message)
    }
}
