package org.graphiks.kadre.internal.win32

import java.net.URLClassLoader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
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
 * Phase 13 reuse qualification: sequential standalone sessions must behave
 * identically when the context classloader changes between them — provider
 * discovery, the process brokers and KFFI callbacks must retain no stale
 * loader without a live owner. Run with the real provider on Windows.
 */
class Win32ClassloaderReuseTest {
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
    fun sequentialSessionsSurviveAContextClassloaderSwap() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        Win32HostOwnership.resetForTesting()
        val provider = Win32BackendProvider()
        val originalLoader = Thread.currentThread().contextClassLoader
        try {
            // Session under the application (original) loader.
            assertTrue(runOne(provider), "first session must complete")
            // Swap to a child loader over the same classpath and repeat.
            URLClassLoader(emptyArray(), originalLoader).use { swapped ->
                Thread.currentThread().contextClassLoader = swapped
                try {
                    assertTrue(runOne(provider), "session under the swapped loader must complete")
                } finally {
                    Thread.currentThread().contextClassLoader = originalLoader
                }
            }
            // A third session proves nothing was poisoned by the swap.
            assertTrue(runOne(provider), "session after loader restoration must complete")
        } finally {
            Thread.currentThread().contextClassLoader = originalLoader
            Win32HostOwnership.resetForTesting()
        }
    }

    private fun runOne(provider: Win32BackendProvider): Boolean {
        val outcome = provider.run(
            DesktopRunRequest(
                applicationFactory = KadreApplicationFactory { KadreApplication {
                    val requested = windows.requestWindow(
                        WindowSpec(title = "kadre loader reuse", contentSize = LogicalSize(160.0, 120.0)),
                    )
                    check(requested is KadreResult.Success<*>)
                    requestStop()
                } },
                policy = KadrePolicies.Default,
            ),
        )
        return outcome is KadreResult.Success<*>
    }
}
