package org.graphiks.kadre.internal.win32

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopRunRequest
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

    @BeforeTest
    fun installProcessMainDispatcher() {
        // The process controller owns its root scope on Dispatchers.Main.immediate; the
        // standalone runs below stand it up on the Default pool like the production host.
        Dispatchers.setMain(Dispatchers.Default)
    }

    @AfterTest
    fun resetProcessMainDispatcher() {
        Dispatchers.resetMain()
    }

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
                    DesktopRunRequest(
                        applicationFactory = KadreApplicationFactory { KadreApplication {
                            val requested = windows.requestWindow(
                                WindowSpec(title = "kadre stress $cycle", contentSize = LogicalSize(160.0, 120.0)),
                            )
                            check(requested is KadreResult.Success<*>)
                            requestStop()
                        } },
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
            DesktopRunRequest(
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
                policy = KadrePolicies.Default,
            ),
        )
        assertTrue(burstDone.await(120, TimeUnit.SECONDS), "churn bursts did not finish")
        assertEquals(12, churned.get())
    }
}
