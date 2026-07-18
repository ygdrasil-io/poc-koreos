package org.graphiks.kadre.x11

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.x11.*
import java.io.File
import java.lang.foreign.MemorySegment
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class X11NativeIntegrationTest {

    private fun isLinux(): Boolean =
        System.getProperty("os.name", "").contains("Linux", ignoreCase = true)

    private fun hasDisplay(): Boolean =
        !System.getenv("DISPLAY").isNullOrBlank()

    private fun openDisplayOrNull(): MemorySegment? {
        val display = xOpenDisplay?.invokeExact(MemorySegment.NULL) as? MemorySegment ?: return null
        return if (display == MemorySegment.NULL || display.address() == 0L) null else display
    }

    private fun closeDisplay(display: MemorySegment?) {
        if (display == null || display == MemorySegment.NULL || display.address() == 0L) return
        xCloseDisplay?.invokeExact(display) as? Int
    }

    @Test
    fun `missing display fails explicitly in an isolated process`() {
        val secretMarker = "unrelated-environment-must-not-be-reported"
        val javaExecutable = Path.of(
            System.getProperty("java.home"),
            "bin",
            if (System.getProperty("os.name", "").startsWith("Windows")) "java.exe" else "java",
        )
        val process = ProcessBuilder(
            javaExecutable.toString(),
            "--enable-native-access=ALL-UNNAMED",
            "-cp",
            childJvmClasspath(),
            X11MissingDisplayProbe::class.java.name,
        )
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment()["DISPLAY"] = "definitely-missing"
                environment()["KADRE_UNRELATED_SECRET"] = secretMarker
                System.getenv("LD_LIBRARY_PATH")?.let {
                    environment()["LD_LIBRARY_PATH"] = it
                }
            }
            .start()

        val completed = process.waitFor(30, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly().waitFor()
        val output = process.inputStream.bufferedReader().readText()

        assertTrue(completed, "Missing-display probe timed out:\n$output")
        assertNotEquals(0, process.exitValue(), "runApp returned successfully:\n$output")
        assertContains(output, "backend=X11")
        assertContains(output, "operation=XOpenDisplay")
        assertContains(output, "DISPLAY=definitely-missing")
        assertFalse(output.contains(secretMarker), "Failure dumped unrelated environment:\n$output")
    }

    private fun childJvmClasspath(): String {
        val loaderEntries = generateSequence(Thread.currentThread().contextClassLoader) { it.parent }
            .filterIsInstance<URLClassLoader>()
            .flatMap { it.urLs.asSequence() }
            .filter { it.protocol == "file" }
            .map { Path.of(it.toURI()).toString() }
        val systemEntries = System.getProperty("java.class.path", "")
            .splitToSequence(File.pathSeparator)
        return (loaderEntries + systemEntries)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(File.pathSeparator)
    }

    @Test
    fun `X11 library symbols resolve on Linux`() {
        if (!isLinux()) return
        assertNotNull(xOpenDisplay)
        assertNotNull(xInternAtom)
        assertNotNull(xConvertSelection)
        assertNotNull(xCloseDisplay)
    }

    @Test
    fun `Xdnd atoms are defined correctly`() {
        if (!isLinux() || !hasDisplay()) return
        val display = openDisplayOrNull() ?: return
        try {
            assertTrue(x11DragAndDropAtom(display, "XdndAware") != 0L)
            assertTrue(x11DragAndDropAtom(display, "XdndEnter") != 0L)
            assertTrue(x11DragAndDropAtom(display, "XdndPosition") != 0L)
            assertTrue(x11DragAndDropAtom(display, "XdndDrop") != 0L)
            assertTrue(x11DragAndDropAtom(display, "XdndLeave") != 0L)
            assertTrue(x11DragAndDropAtom(display, "XdndSelection") != 0L)
            assertTrue(x11DragAndDropAtom(display, "XdndStatus") != 0L)
            assertTrue(x11DragAndDropAtom(display, "XdndFinished") != 0L)
            assertTrue(x11DragAndDropAtom(display, "text/uri-list") != 0L)
        } finally {
            closeDisplay(display)
        }
    }

    @Test
    fun `XConvertSelection binding is non-null`() {
        if (!isLinux()) return
        assertNotNull(xConvertSelection)
    }

    @Test
    fun `X11 display connection works when DISPLAY is set`() {
        if (!isLinux() || !hasDisplay()) return
        val display = openDisplayOrNull()
        assertNotNull(display)
        closeDisplay(display)
    }

    @Test
    fun `real X server drives lifecycle redraw wake rearm and terminal close`() {
        if (!isLinux() || !hasDisplay()) return

        val trace = mutableListOf<String>()
        lateinit var window: Window
        var wakeRequests = 0
        var redrawPublished = false
        var redrawAfterRequest = 0
        var closePublished = false
        var destroyed = 0
        fun record(stage: String) {
            trace += stage
        }

        runApp(object : ApplicationHandler {
            override fun resumed(eventLoop: ActiveEventLoop) {
                record("resumed")
            }

            override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                record(if (startCause === StartCause.Init) "newEvents-Init" else "newEvents-$startCause")
            }

            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                record("canCreateSurfaces")
                window = eventLoop.createWindow(WindowAttributes(title = "Kadre Xvfb integration"))
                record("windowCreated")
            }

            override fun aboutToWait(eventLoop: ActiveEventLoop) {
                record("aboutToWait")
                when {
                    destroyed > 0 -> Unit
                    wakeRequests < 3 -> {
                        wakeRequests += 1
                        record("wake-$wakeRequests")
                        eventLoop.createProxy().wakeUp()
                    }
                    !redrawPublished -> {
                        redrawPublished = true
                        record("redrawPublished")
                        repeat(10) { window.requestRedraw() }
                    }
                    redrawAfterRequest > 0 && !closePublished -> {
                        closePublished = true
                        record("closePublished")
                        window.close()
                    }
                }
            }

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) {
                when (event) {
                    WindowEvent.RedrawRequested -> {
                        record("redraw")
                        if (redrawPublished) redrawAfterRequest += 1
                    }
                    WindowEvent.Destroyed -> {
                        record("destroyed")
                        destroyed += 1
                        eventLoop.exit()
                    }
                    else -> Unit
                }
            }

            override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                record("destroySurfaces")
            }

            override fun suspended(eventLoop: ActiveEventLoop) {
                record("suspended")
            }
        })

        assertEquals(3, wakeRequests, trace.toString())
        assertEquals(1, redrawAfterRequest, trace.toString())
        assertEquals(1, destroyed, trace.toString())
        assertEquals(
            listOf(
                "resumed",
                "newEvents-Init",
                "canCreateSurfaces",
                "windowCreated",
                "aboutToWait",
            ),
            trace.take(5),
        )
        assertEquals(listOf("destroySurfaces", "suspended"), trace.takeLast(2))
    }
}

internal object X11MissingDisplayProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val handler = object : ApplicationHandler {
            override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit

            override fun windowEvent(
                eventLoop: ActiveEventLoop,
                windowId: WindowId,
                event: WindowEvent,
            ) = Unit
        }

        val firstFailure = runCatching { runApp(handler) }.exceptionOrNull() ?: return
        check(!x11Running.get()) { "x11Running remained set after the first XOpenDisplay failure" }

        val secondFailure = runCatching { runApp(handler) }.exceptionOrNull()
        check(secondFailure is IllegalStateException) { "A second runApp attempt unexpectedly succeeded" }
        check(secondFailure.message.orEmpty().contains("operation=XOpenDisplay")) {
            "A failed run poisoned the next runApp attempt: ${secondFailure.message}"
        }
        check(!x11Running.get()) { "x11Running remained set after the second XOpenDisplay failure" }
        throw firstFailure
    }
}
