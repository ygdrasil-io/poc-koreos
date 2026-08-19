package org.graphiks.kadre.samples.compose.desktop

import org.graphiks.kadre.PhysicalSize
import org.graphiks.kadre.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.coroutines.kadreApplication
import org.graphiks.kadre.samples.compose.infra.ComposeWindowRenderer
import org.graphiks.kadre.samples.compose.infra.KeyForwarder
import org.graphiks.kadre.samples.compose.infra.applyWindowEvent
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseApp

internal class WindowCaptureController(
    private val capturePath: String,
    private val captureFrameToPng: (String) -> Boolean,
    private val captureReady: () -> Boolean,
    private val pngValidator: (String, Int, Int) -> Unit = ::validatePng,
    private val disposeRenderer: () -> Unit,
    private val requestExit: () -> Unit,
) {
    private var attempted = false

    var completedSuccessfully: Boolean = false
        private set

    fun onRedrawRequested() {
        if (attempted) return
        if (!captureReady()) return
        attempted = true
        var validated = false
        try {
            check(captureFrameToPng(capturePath)) {
                "window-capture renderer failed: $capturePath"
            }
            pngValidator(capturePath, CAPTURE_MIN_WIDTH, CAPTURE_MIN_HEIGHT)
            validated = true
        } finally {
            try {
                disposeRenderer()
            } finally {
                requestExit()
            }
        }
        completedSuccessfully = validated
    }
}

private fun runShowcase(capturePath: String? = null) {
    var captureCompleted = false
    kadreApplication {
        val keys = KeyForwarder()
        val initialSize = if (capturePath != null) {
            PhysicalSize(WINDOW_CAPTURE_WIDTH, WINDOW_CAPTURE_HEIGHT)
        } else {
            PhysicalSize(900, 700)
        }
        val win = createWindow(
            WindowAttributes("Compose Showcase", initialSize, visible = true, resizable = true),
        )
        val handle = win.window.rawWindowHandle
        val renderer = ComposeWindowRenderer.create(handle, win.window.scaleFactor, dispatcher).getOrElse {
            println("[compose-showcase] Cannot create renderer: ${it.message}")
            exit(); return@kadreApplication
        }
        var rendererDisposed = false
        fun disposeRenderer() {
            if (!rendererDisposed) {
                rendererDisposed = true
                renderer.dispose()
            }
        }

        try {
            renderer.setContent { ShowcaseApp(PlatformContext()) }
            val inner = win.window.innerSize
            renderer.resize(inner.width, inner.height, win.window.scaleFactor)
            val captureController = capturePath?.let { requestedPath ->
                WindowCaptureController(
                    capturePath = requestedPath,
                    captureFrameToPng = renderer::captureFrameToPng,
                    captureReady = {
                        win.window.innerSize.let { size ->
                            size.width >= CAPTURE_MIN_WIDTH && size.height >= CAPTURE_MIN_HEIGHT
                        }
                    },
                    disposeRenderer = ::disposeRenderer,
                    requestExit = { exit() },
                )
            }
            win.window.requestRedraw()

            var lastRenderNanos = 0L
            val frameIntervalNanos = 50_000_000L

            win.events.collect { event ->
                when (event) {
                    is WindowEvent.CloseRequested -> {
                        disposeRenderer()
                        exit()
                    }
                    is WindowEvent.RedrawRequested -> {
                        if (captureController != null) {
                            captureController.onRedrawRequested()
                            captureCompleted = captureController.completedSuccessfully
                        } else {
                            val now = System.nanoTime()
                            if (now - lastRenderNanos >= frameIntervalNanos) {
                                lastRenderNanos = now
                                renderer.applyWindowEvent(event, win.window, keys)
                            }
                        }
                    }
                    is WindowEvent.Resized -> {
                        renderer.applyWindowEvent(event, win.window, keys)
                        if (captureController != null) win.window.requestRedraw()
                    }
                    else -> renderer.applyWindowEvent(event, win.window, keys)
                }
            }
        } finally {
            disposeRenderer()
        }
    }

    check(capturePath == null || captureCompleted) {
        "window-capture did not produce a validated PNG: $capturePath"
    }
}

private fun runKeytest() {
    val typed = keyboardSelfTest("hi")
    println("[compose-showcase] keytest — text field received: '$typed' (expected 'hi')")
    if (typed != "hi") error("keytest FAILED: '$typed' != 'hi'")
    println("[compose-showcase] keytest OK")
}

private fun startCaptureWatchdog() {
    Thread {
        Thread.sleep(30_000)
        System.err.println("[compose-showcase] window-capture watchdog: not done after 30s — forcing exit")
        System.out.flush()
        System.err.flush()
        Runtime.getRuntime().halt(3)
    }.apply { isDaemon = true }.start()
}

fun main(args: Array<String>) {
    if (args.contains("--keytest")) {
        runKeytest()
        return
    }

    val ciHeadlessIndex = args.indexOf("--ci-headless")
    if (ciHeadlessIndex >= 0) {
        val dir = args.getOrNull(ciHeadlessIndex + 1)
            ?: error("--ci-headless requires an output dir: --ci-headless <dir>")
        runKeytest()
        captureShowcaseToPng("$dir/compose-showcase.raster.png")
        return
    }

    val captureIndex = args.indexOf("--capture")
    if (captureIndex >= 0) {
        val path = args.getOrNull(captureIndex + 1)
            ?: error("--capture requires a file path: --capture <path>")
        captureShowcaseToPng(path)
        return
    }

    val windowCaptureIndex = args.indexOf("--window-capture")
    if (windowCaptureIndex >= 0) {
        val path = args.getOrNull(windowCaptureIndex + 1)
            ?: error("--window-capture requires a file path: --window-capture <path>")
        startCaptureWatchdog()
        runShowcase(path)
        return
    }

    println("[compose-showcase] Starting Compose Showcase")
    runShowcase()
    println("[compose-showcase] Done")
}

private const val CAPTURE_MIN_WIDTH = 640
private const val CAPTURE_MIN_HEIGHT = 480
private const val WINDOW_CAPTURE_WIDTH = 800
private const val WINDOW_CAPTURE_HEIGHT = 600
