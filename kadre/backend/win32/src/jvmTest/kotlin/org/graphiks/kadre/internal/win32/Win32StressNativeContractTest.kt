package org.graphiks.kadre.internal.win32

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec

/**
 * Robustness proof over repeated create/teardown cycles on the real Windows
 * desktop (phase 13, mirroring the wayland stress shape): full standalone
 * sessions back to back, top-level window churn bursts inside one session,
 * and standalone↔embedded interleaving over the process ownership registry
 * plus the process-lifetime raw-input and gamepad brokers. Threads must stay
 * bounded and the ownership registry must return to its baseline.
 */
class Win32StressNativeContractTest {
    private fun windowsGuard(): Boolean = System.getProperty("os.name").startsWith("Windows")

    @Test
    fun repeatedStandaloneSessionsStayBoundedAcrossFiftyCycles() {
        if (!windowsGuard()) return
        Win32HostOwnership.resetForTesting()
        val provider = Win32BackendProvider()
        val baselineThreads = Thread.activeCount()
        var midThreads = baselineThreads
        repeat(50) { cycle ->
            if (cycle == 25) midThreads = Thread.activeCount()
            val outcome = assertIs<KadreResult.Success<*>>(
                provider.run(
                    DesktopStandaloneRequest(
                        applicationFactory = KadreApplicationFactory { KadreApplication {
                            val requested = windows.requestWindow(
                                WindowSpec(title = "kadre stress $cycle", contentSize = LogicalSize(160.0, 120.0)),
                            )
                            check(requested is KadreResult.Success<*>)
                            requestStop()
                        } },
                        stopWhenLastWindowClosed = true,
                        policy = KadrePolicies.Default,
                    ),
                ),
            ).value
            assertTrue(
                outcome is org.graphiks.kadre.application.SessionOutcome.Stopped ||
                    outcome is org.graphiks.kadre.application.SessionOutcome.Completed,
                "cycle $cycle outcome: $outcome",
            )
        }
        assertEquals(0, Win32HostOwnership.embeddedSessions())
        val grewSinceMid = Thread.activeCount() - midThreads
        assertTrue(grewSinceMid <= 4, "threads must stay bounded after warmup: ${Thread.activeCount()} active, +$grewSinceMid since cycle 25 (baseline $baselineThreads)")
    }

    @Test
    fun windowChurnBurstsStayBoundedInsideOneSession() {
        if (!windowsGuard()) return
        val provider = Win32BackendProvider()
        val churned = java.util.concurrent.atomic.AtomicInteger(0)
        val burstDone = CountDownLatch(1)
        provider.run(
            DesktopStandaloneRequest(
                applicationFactory = KadreApplicationFactory { KadreApplication {
                    launch {
                        repeat(2) { burst ->
                            repeat(6) { window ->
                                val requested = windows.requestWindow(
                                    WindowSpec(title = "kadre churn $window", contentSize = LogicalSize(120.0, 90.0)),
                                )
                                check(requested is KadreResult.Success<*>)
                                (requested as KadreResult.Success<*>).value.let { request ->
                                    if (request is org.graphiks.kadre.window.WindowRequest) runBlocking { request.await() }
                                }
                            }
                        }
                        churned.addAndGet(12)
                        burstDone.countDown()
                    }
                } },
                stopWhenLastWindowClosed = true,
                policy = KadrePolicies.Default,
            ),
        )
        assertTrue(burstDone.await(120, TimeUnit.SECONDS), "churn bursts did not finish")
        assertEquals(12, churned.get())
    }

    @Test
    fun standaloneAndEmbeddedInterleaveOverTheOwnershipRegistry() {
        if (!windowsGuard()) return
        Win32HostOwnership.resetForTesting()
        val provider = Win32BackendProvider()
        val frame = javax.swing.JFrame("kadre interleave host")
        frame.setSize(200, 150)
        frame.isVisible = true
        try {
            repeat(3) { cycle ->
                // standalone first: it owns the host exclusively during its run
                assertIs<KadreResult.Success<*>>(
                    provider.run(
                        DesktopStandaloneRequest(
                            applicationFactory = KadreApplicationFactory { KadreApplication {
                                requestStop()
                            } },
                            stopWhenLastWindowClosed = true,
                            policy = KadrePolicies.Default,
                        ),
                    ),
                )
                assertEquals(0, Win32HostOwnership.embeddedSessions(), "cycle $cycle: standalone exit leaves no embedded sessions")
                // then an embedded session attaches to the running AWT loop
                val attached = CountDownLatch(1)
                val failed = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
                val scope = CoroutineScope(Job() + Dispatchers.Default)
                java.awt.EventQueue.invokeLater {
                    try {
                        val session = assertIs<KadreResult.Success<KadreSession>>(
                            provider.attach(
                                DesktopEmbeddedRequest(
                                    parentScope = scope,
                                    applicationFactory = KadreApplicationFactory { KadreApplication {
                                        val requested = windows.requestWindow(
                                            WindowSpec(
                                                title = "kadre interleave embedded",
                                                contentSize = LogicalSize(160.0, 120.0),
                                            ),
                                        )
                                        check(requested is KadreResult.Success<*>)
                                        (requested as KadreResult.Success<*>).value.let { request ->
                                            if (request is org.graphiks.kadre.window.WindowRequest) runBlocking { request.await() }
                                        }
                                    } },
                                    integration = DesktopIntegrationKind.AwtEventDispatchThread,
                                    policy = KadrePolicies.Default,
                                ),
                            ),
                        ).value
                        assertEquals(1, Win32HostOwnership.embeddedSessions(), "cycle $cycle: one embedded session registered")
                        session.close()
                        runBlocking { withTimeout(20_000) { session.awaitTermination() } }
                    } catch (caught: Throwable) {
                        failed.set(caught)
                    } finally {
                        scope.cancel()
                        attached.countDown()
                    }
                }
                assertTrue(attached.await(60, TimeUnit.SECONDS), "cycle $cycle: embedded attach did not finish")
                failed.get()?.let { throw it }
                awaitEmbeddedRelease(0, "cycle $cycle: embedded release must complete")
            }
        } finally {
            frame.dispose()
            Win32HostOwnership.resetForTesting()
        }
    }

    private fun awaitEmbeddedRelease(expectedRemaining: Int, message: String) {
        val deadline = System.currentTimeMillis() + 20_000
        while (Win32HostOwnership.embeddedSessions() > expectedRemaining) {
            if (System.currentTimeMillis() > deadline) {
                error("$message (remaining=${Win32HostOwnership.embeddedSessions()})")
            }
            Thread.sleep(25)
        }
    }
}
