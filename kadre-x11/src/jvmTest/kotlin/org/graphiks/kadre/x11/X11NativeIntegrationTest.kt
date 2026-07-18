package org.graphiks.kadre.x11

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.x11.*
import org.graphiks.kadre.test.EventLoopConformanceDriver
import org.graphiks.kadre.test.ObservedCallback
import org.graphiks.kadre.test.assertCloseIsTerminal
import org.graphiks.kadre.test.assertRedrawAfterIdle
import org.graphiks.kadre.test.assertWakeUpRearms
import java.io.File
import java.lang.foreign.MemorySegment
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
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
    fun `real X server drives lifecycle redraw and terminal close`() {
        if (!isLinux() || !hasDisplay()) return

        val trace = mutableListOf<String>()
        lateinit var window: Window
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

class X11CommonConformanceTest {
    private fun isLinuxWithDisplay(): Boolean =
        System.getProperty("os.name", "").contains("Linux", ignoreCase = true) &&
            !System.getenv("DISPLAY").isNullOrBlank()

    @Test
    fun `native X11 wake proxy satisfies common conformance`() {
        if (!isLinuxWithDisplay()) return
        lateinit var driver: X11NativeConformanceDriver

        assertWakeUpRearms { X11NativeConformanceDriver().also { driver = it } }

        assertEquals(3, driver.consumedWakeIterations)
        assertEquals(3, driver.wakeCallerThreads.size)
        assertTrue(driver.wakeCallerThreads.all { it !== driver.eventLoopThread })
    }

    @Test
    fun `native X11 redraw satisfies common conformance`() {
        if (!isLinuxWithDisplay()) return
        assertRedrawAfterIdle { X11NativeConformanceDriver() }
    }

    @Test
    fun `native X11 close satisfies common conformance`() {
        if (!isLinuxWithDisplay()) return
        assertCloseIsTerminal { X11NativeConformanceDriver() }
    }
}

private class X11NativeConformanceDriver : EventLoopConformanceDriver {
    override val trace: MutableList<ObservedCallback> = CopyOnWriteArrayList()
    val wakeCallerThreads: MutableList<Thread> = CopyOnWriteArrayList()

    private val failure = AtomicReference<Throwable?>()
    private val stateLock = ReentrantLock()
    private val stateChanged = stateLock.newCondition()
    private val resumeLoop = Semaphore(0)

    @Volatile
    lateinit var eventLoopThread: Thread
        private set

    @Volatile
    private var eventLoop: ActiveEventLoop? = null

    @Volatile
    private var window: Window? = null

    @Volatile
    private var terminalQueued = false

    private var worker: Thread? = null
    private var idleGeneration = 0
    private var targetIdleGeneration = 0
    private var cycleReleased = false
    private var wakeCyclePending = false
    private var redrawQueued = false
    private var finished = false

    var consumedWakeIterations = 0
        private set

    override fun start() {
        check(worker == null) { "X11 conformance driver already started" }
        worker = Thread({
            eventLoopThread = Thread.currentThread()
            try {
                runApp(object : ApplicationHandler {
                    override fun resumed(eventLoop: ActiveEventLoop) {
                        trace += ObservedCallback.Resumed
                    }

                    override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
                        trace += ObservedCallback.NewEvents
                    }

                    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
                        trace += ObservedCallback.CanCreateSurfaces
                        this@X11NativeConformanceDriver.eventLoop = eventLoop
                        window = eventLoop.createWindow(
                            WindowAttributes(title = "Kadre X11 common conformance"),
                        )
                    }

                    override fun windowEvent(
                        eventLoop: ActiveEventLoop,
                        windowId: WindowId,
                        event: WindowEvent,
                    ) {
                        trace += when (event) {
                            WindowEvent.RedrawRequested -> ObservedCallback.RedrawRequested
                            WindowEvent.Destroyed -> {
                                eventLoop.exit()
                                ObservedCallback.Destroyed
                            }
                            else -> ObservedCallback.WindowEvent
                        }
                    }

                    override fun aboutToWait(eventLoop: ActiveEventLoop) {
                        if (terminalQueued) return
                        trace += ObservedCallback.AboutToWait
                        stateLock.withLock {
                            idleGeneration += 1
                            stateChanged.signalAll()
                        }
                        try {
                            resumeLoop.acquire()
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            eventLoop.exit()
                        }
                    }

                    override fun destroySurfaces(eventLoop: ActiveEventLoop) {
                        trace += ObservedCallback.DestroySurfaces
                    }

                    override fun suspended(eventLoop: ActiveEventLoop) {
                        trace += ObservedCallback.Suspended
                        if (terminalQueued) trace += ObservedCallback.Closed
                    }
                })
            } catch (thrown: Throwable) {
                failure.compareAndSet(null, thrown)
            } finally {
                stateLock.withLock {
                    finished = true
                    stateChanged.signalAll()
                }
            }
        }, "kadre-x11-conformance-loop").apply {
            isDaemon = true
            start()
        }

        awaitState("initial X11 idle barrier") {
            idleGeneration >= 1 && eventLoop != null && window != null
        }
    }

    override fun wakeUp() {
        checkExternalThread()
        wakeCallerThreads += Thread.currentThread()
        requireNotNull(eventLoop) { "X11 conformance loop is not started" }
            .createProxy()
            .wakeUp()
        releaseOneIteration(isWakeCycle = true)
    }

    override fun requestRedraw() {
        checkExternalThread()
        requireNotNull(window) { "X11 conformance window is not available" }.requestRedraw()
        stateLock.withLock { redrawQueued = true }
    }

    override fun waitForIdle() {
        var release = false
        val target: Int
        val waitForTerminal: Boolean
        stateLock.withLock {
            waitForTerminal = terminalQueued
            if (!cycleReleased && (redrawQueued || terminalQueued)) {
                targetIdleGeneration = idleGeneration + 1
                cycleReleased = true
                wakeCyclePending = false
                release = true
            }
            if (!cycleReleased) return
            target = targetIdleGeneration
        }
        if (release) resumeLoop.release()

        if (waitForTerminal) {
            awaitState("terminal X11 shutdown") { finished }
        } else {
            awaitState("X11 idle generation $target") { idleGeneration >= target }
        }

        stateLock.withLock {
            if (wakeCyclePending) consumedWakeIterations += 1
            cycleReleased = false
            wakeCyclePending = false
            redrawQueued = false
        }
    }

    override fun closeWindow() {
        checkExternalThread()
        terminalQueued = true
        requireNotNull(window) { "X11 conformance window is not available" }.close()
    }

    override fun shutdown() {
        if (worker == null) return
        if (!stateLock.withLock { finished }) {
            eventLoop?.exit()
            runCatching { eventLoop?.createProxy()?.wakeUp() }
            resumeLoop.release()
        }

        val loopWorker = requireNotNull(worker)
        loopWorker.join(TimeUnit.SECONDS.toMillis(9))
        if (loopWorker.isAlive) {
            loopWorker.interrupt()
            eventLoop?.exit()
            runCatching { eventLoop?.createProxy()?.wakeUp() }
            resumeLoop.release()
            loopWorker.join(TimeUnit.SECONDS.toMillis(1))
        }
        check(!loopWorker.isAlive) { "X11 conformance loop did not stop within 10 seconds" }
        failure.get()?.let { throw AssertionError("X11 conformance loop failed", it) }
    }

    private fun releaseOneIteration(isWakeCycle: Boolean) {
        stateLock.withLock {
            check(!cycleReleased) { "An X11 conformance iteration is already in flight" }
            targetIdleGeneration = idleGeneration + 1
            cycleReleased = true
            wakeCyclePending = isWakeCycle
        }
        resumeLoop.release()
    }

    private fun checkExternalThread() {
        check(!::eventLoopThread.isInitialized || Thread.currentThread() !== eventLoopThread) {
            "X11 conformance commands must originate outside the event-loop thread"
        }
    }

    private fun awaitState(description: String, condition: () -> Boolean) {
        stateLock.withLock {
            var remaining = TimeUnit.SECONDS.toNanos(10)
            while (!condition() && failure.get() == null && remaining > 0L) {
                remaining = stateChanged.awaitNanos(remaining)
            }
            failure.get()?.let { throw AssertionError("X11 conformance loop failed while awaiting $description", it) }
            check(condition()) { "Timed out after 10 seconds awaiting $description; trace=$trace" }
        }
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
