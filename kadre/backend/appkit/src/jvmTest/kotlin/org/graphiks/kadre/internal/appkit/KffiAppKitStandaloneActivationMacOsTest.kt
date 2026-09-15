package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kffi.objc.NSApplication
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// This class owns NSApplication activation and is run in an isolated JVM.
class KffiAppKitStandaloneActivationMacOsTest {
    @Test
    fun standaloneFirstPresentationActivatesAnInitiallyInactiveAppKitApplicationOnMacOs() {
        if (!System.getProperty("os.name", "").let { it.contains("Mac", true) || it.contains("Darwin", true) }) return

        val nativeApplication = KffiAppKitNativeApplication()
        val port = KffiAppKitWindowPort()
        var window: AppKitNativeWindowOwner? = null
        val failure = AtomicReference<Throwable?>(null)
        val activated = AtomicBoolean(false)
        val application = NSApplication(NSApplication.sharedApplication())
        application.deactivate()
        assertFalse(application.isActive())
        val worker = Thread.ofPlatform().daemon().name("kadre-standalone-activation-test").start {
            try {
                val deadline = System.nanoTime() + 5_000_000_000L
                while (!nativeApplication.isRunning() && System.nanoTime() < deadline) Thread.sleep(10L)
                check(nativeApplication.isRunning()) { "AppKit event loop did not start" }
                port.onMainThread {
                    port.armProcessActivationOnFirstPresentation()
                    window = port.createWindow(WindowSpec(contentSize = LogicalSize(240.0, 135.0)))
                    port.present(checkNotNull(window))
                }
                repeat(50) {
                    if (KffiAppKitMainThread.call {
                            NSApplication(NSApplication.sharedApplication()).isActive()
                        }
                    ) {
                        activated.set(true)
                        return@repeat
                    }
                    Thread.sleep(20L)
                }
            } catch (problem: Throwable) {
                failure.set(problem)
            } finally {
                nativeApplication.requestStop().await()
            }
        }
        try {
            nativeApplication.run()
            failure.get()?.let { throw AssertionError("AppKit presentation worker failed", it) }
            assertTrue(activated.get(), "standalone first presentation did not activate the existing NSApplication")
        } finally {
            port.onMainThread {
                window?.let { nativeWindow ->
                    port.closeWindow(nativeWindow)
                    nativeWindow.close()
                }
            }
            worker.join(1_000L)
        }
    }
}
