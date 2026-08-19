package org.graphiks.kadre.samples.compose.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowCaptureControllerTest {

    @Test
    fun `renderer false throws disposes and requests exit`() = withTempPath { path ->
        val actions = mutableListOf<String>()
        val controller = WindowCaptureController(
            capturePath = path.toString(),
            captureFrameToPng = { actions += "capture"; false },
            pngValidator = { _, _, _ -> actions += "validate" },
            disposeRenderer = { actions += "dispose" },
            requestExit = { actions += "exit" },
        )

        val failure = assertFailsWith<IllegalStateException> { controller.onRedrawRequested() }

        assertContains(failure.message.orEmpty(), path.toString())
        assertContains(failure.message.orEmpty(), "renderer failed")
        assertEquals(listOf("capture", "dispose", "exit"), actions)
        assertFalse(controller.completedSuccessfully)
    }

    @Test
    fun `renderer true without requested file throws disposes and requests exit`() = withTempPath { path ->
        val actions = mutableListOf<String>()
        val controller = WindowCaptureController(
            capturePath = path.toString(),
            captureFrameToPng = { actions += "capture"; true },
            disposeRenderer = { actions += "dispose" },
            requestExit = { actions += "exit" },
        )

        val failure = assertFailsWith<IllegalStateException> { controller.onRedrawRequested() }

        assertContains(failure.message.orEmpty(), path.toString())
        assertContains(failure.message.orEmpty(), "regular file")
        assertEquals(listOf("capture", "dispose", "exit"), actions)
        assertFalse(controller.completedSuccessfully)
    }

    @Test
    fun `success captures validates disposes and exits exactly once`() = withTempPath { path ->
        val actions = mutableListOf<String>()
        var capturedPath: String? = null
        val controller = WindowCaptureController(
            capturePath = path.toString(),
            captureFrameToPng = { requestedPath ->
                actions += "capture"
                capturedPath = requestedPath
                writeValidPng(path)
                true
            },
            pngValidator = { requestedPath, expectedMinWidth, expectedMinHeight ->
                actions += "validate"
                assertEquals(path.toString(), requestedPath)
                assertEquals(640, expectedMinWidth)
                assertEquals(480, expectedMinHeight)
                validatePng(requestedPath, expectedMinWidth, expectedMinHeight)
            },
            disposeRenderer = { actions += "dispose" },
            requestExit = { actions += "exit" },
        )

        controller.onRedrawRequested()
        controller.onRedrawRequested()

        assertEquals(path.toString(), capturedPath)
        assertEquals(listOf("capture", "validate", "dispose", "exit"), actions)
        assertTrue(controller.completedSuccessfully)
    }

    @Test
    fun `validator failure still disposes and requests exit in finally`() = withTempPath { path ->
        val actions = mutableListOf<String>()
        val controller = WindowCaptureController(
            capturePath = path.toString(),
            captureFrameToPng = { actions += "capture"; true },
            pngValidator = { _, _, _ -> actions += "validate"; error("pixel diversity failed") },
            disposeRenderer = { actions += "dispose" },
            requestExit = { actions += "exit" },
        )

        val failure = assertFailsWith<IllegalStateException> { controller.onRedrawRequested() }

        assertContains(failure.message.orEmpty(), "pixel diversity failed")
        assertEquals(listOf("capture", "validate", "dispose", "exit"), actions)
        assertFalse(controller.completedSuccessfully)
    }

    @Test
    fun `renderer exception still disposes and requests exit in finally`() = withTempPath { path ->
        val actions = mutableListOf<String>()
        val controller = WindowCaptureController(
            capturePath = path.toString(),
            captureFrameToPng = { actions += "capture"; error("renderer exploded") },
            disposeRenderer = { actions += "dispose" },
            requestExit = { actions += "exit" },
        )

        val failure = assertFailsWith<IllegalStateException> { controller.onRedrawRequested() }

        assertContains(failure.message.orEmpty(), "renderer exploded")
        assertEquals(listOf("capture", "dispose", "exit"), actions)
        assertFalse(controller.completedSuccessfully)
    }

    private fun writeValidPng(path: Path) {
        val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color(35, 92, 180)
            graphics.fillRect(200, 150, 400, 300)
        } finally {
            graphics.dispose()
        }
        check(ImageIO.write(image, "png", path.toFile()))
    }

    private inline fun withTempPath(block: (Path) -> Unit) {
        val directory = createTempDirectory("kadre-window-capture-")
        try {
            block(directory.resolve("capture.png"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
