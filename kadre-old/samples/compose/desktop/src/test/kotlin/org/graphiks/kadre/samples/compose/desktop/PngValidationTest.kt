package org.graphiks.kadre.samples.compose.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PngValidationTest {

    @Test
    fun `missing path reports the regular-file property`() = withTempDirectory { directory ->
        val path = directory.resolve("missing.png")

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(path.toString(), expectedMinWidth = 1, expectedMinHeight = 1)
        }

        assertFailure(failure, path, "regular file")
    }

    @Test
    fun `zero-byte file reports the minimum-size property`() = withTempDirectory { directory ->
        val path = directory.resolve("zero.png")
        Files.createFile(path)

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(path.toString(), expectedMinWidth = 1, expectedMinHeight = 1)
        }

        assertFailure(failure, path, "size > 100 bytes")
    }

    @Test
    fun `non-PNG file reports the signature property`() = withTempDirectory { directory ->
        val path = directory.resolve("not-png.bin")
        path.writeBytes(ByteArray(128) { index -> index.toByte() })

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(path.toString(), expectedMinWidth = 1, expectedMinHeight = 1)
        }

        assertFailure(failure, path, "eight-byte PNG signature")
    }

    @Test
    fun `truncated PNG reports the ImageIO decode property`() = withTempDirectory { directory ->
        val path = directory.resolve("truncated.png")
        path.writeBytes(PNG_SIGNATURE + ByteArray(120))

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(path.toString(), expectedMinWidth = 1, expectedMinHeight = 1)
        }

        assertFailure(failure, path, "ImageIO decode")
    }

    @Test
    fun `size IO failure reports its validation property and cause`() = withTempDirectory { directory ->
        val path = directory.resolve("size-io.png")
        path.writeBytes(ByteArray(128))
        val ioFailure = IOException("size unavailable")

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(
                path.toString(),
                expectedMinWidth = 1,
                expectedMinHeight = 1,
                sizeOf = { throw ioFailure },
            )
        }

        assertFailure(failure, path, "size > 100 bytes")
        assertSame(ioFailure, failure.cause)
    }

    @Test
    fun `signature IO failure reports its validation property and cause`() = withTempDirectory { directory ->
        val path = directory.resolve("signature-io.png")
        path.writeBytes(ByteArray(128))
        val ioFailure = IOException("signature unavailable")

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(
                path.toString(),
                expectedMinWidth = 1,
                expectedMinHeight = 1,
                readSignature = { throw ioFailure },
            )
        }

        assertFailure(failure, path, "exact eight-byte PNG signature")
        assertSame(ioFailure, failure.cause)
    }

    @Test
    fun `undersized PNG reports the dimensions property`() = withTempDirectory { directory ->
        val path = directory.resolve("undersized.png")
        writePng(path, width = 320, height = 240, foreground = true)

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(path.toString(), expectedMinWidth = 640, expectedMinHeight = 480)
        }

        assertFailure(failure, path, "dimensions >= 640x480")
    }

    @Test
    fun `solid-color PNG reports the pixel-diversity property`() = withTempDirectory { directory ->
        val path = directory.resolve("solid.png")
        writePng(path, width = 800, height = 600, foreground = false)

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(path.toString(), expectedMinWidth = 640, expectedMinHeight = 480)
        }

        assertFailure(failure, path, "at least two sampled colors")
    }

    @Test
    fun `fully transparent PNG reports the non-transparent-pixel property`() = withTempDirectory { directory ->
        val path = directory.resolve("transparent.png")
        val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0x00ff0000)
        image.setRGB(799, 599, 0x0000ff00)
        check(ImageIO.write(image, "png", path.toFile()))

        val failure = assertFailsWith<IllegalStateException> {
            validatePng(path.toString(), expectedMinWidth = 640, expectedMinHeight = 480)
        }

        assertFailure(failure, path, "non-transparent sampled pixel")
    }

    @Test
    fun `800x600 PNG with foreground content passes`() = withTempDirectory { directory ->
        val path = directory.resolve("valid.png")
        writePng(path, width = 800, height = 600, foreground = true)

        validatePng(path.toString(), expectedMinWidth = 640, expectedMinHeight = 480)
    }

    private fun assertFailure(failure: IllegalStateException, path: Path, property: String) {
        val message = failure.message.orEmpty()
        assertContains(message, path.toString())
        assertContains(message, property)
    }

    private fun writePng(path: Path, width: Int, height: Int, foreground: Boolean) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(242, 242, 242, 255)
            graphics.fillRect(0, 0, width, height)
            if (foreground) {
                graphics.color = Color(35, 92, 180, 255)
                graphics.fillRect(width / 4, height / 4, width / 2, height / 2)
            }
        } finally {
            graphics.dispose()
        }
        check(ImageIO.write(image, "png", path.toFile()))
    }

    private inline fun withTempDirectory(block: (Path) -> Unit) {
        val directory = createTempDirectory("kadre-png-validation-")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
