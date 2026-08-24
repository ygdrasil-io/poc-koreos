package org.graphiks.kadre.samples.compose.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal fun validatePng(
    path: String,
    expectedMinWidth: Int,
    expectedMinHeight: Int,
    sizeOf: (Path) -> Long = { Files.size(it) },
    readSignature: (Path) -> ByteArray = ::readPngSignature,
) {
    val filePath = Path.of(path)
    val file = filePath.toFile()
    validateProperty(path, file.isFile, "regular file")
    val fileSize = try {
        sizeOf(filePath)
    } catch (cause: Exception) {
        throw validationFailure(path, "size > 100 bytes", cause)
    }
    validateProperty(path, fileSize > MINIMUM_PNG_BYTES, "size > 100 bytes")

    val signature = try {
        readSignature(filePath)
    } catch (cause: Exception) {
        throw validationFailure(path, "exact eight-byte PNG signature", cause)
    }
    validateProperty(
        path,
        signature.contentEquals(PNG_SIGNATURE),
        "exact eight-byte PNG signature",
    )

    val image = try {
        ImageIO.read(file)
    } catch (cause: Exception) {
        throw validationFailure(path, "ImageIO decode", cause)
    } ?: throw validationFailure(path, "ImageIO decode")

    validateProperty(
        path,
        image.width >= expectedMinWidth && image.height >= expectedMinHeight,
        "dimensions >= ${expectedMinWidth}x$expectedMinHeight",
        "actual=${image.width}x${image.height}",
    )

    var firstColor: Int? = null
    var hasPixelDiversity = false
    var hasNonTransparentPixel = false
    scan@ for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val color = image.getRGB(x, y)
            if (firstColor == null) {
                firstColor = color
            } else if (color != firstColor) {
                hasPixelDiversity = true
            }
            if ((color ushr 24) != 0) {
                hasNonTransparentPixel = true
            }
            if (hasPixelDiversity && hasNonTransparentPixel) break@scan
        }
    }

    validateProperty(path, hasNonTransparentPixel, "at least one non-transparent sampled pixel")
    validateProperty(path, hasPixelDiversity, "at least two sampled colors")
}

private fun readPngSignature(path: Path): ByteArray =
    Files.newInputStream(path).use { input -> input.readNBytes(PNG_SIGNATURE.size) }

private fun validateProperty(path: String, valid: Boolean, property: String, detail: String? = null) {
    if (!valid) throw validationFailure(path, property, detail = detail)
}

private fun validationFailure(
    path: String,
    property: String,
    cause: Throwable? = null,
    detail: String? = null,
): IllegalStateException {
    val suffix = detail?.let { ": $it" }.orEmpty()
    return IllegalStateException("PNG validation failed for '$path': property '$property'$suffix", cause)
}

private const val MINIMUM_PNG_BYTES = 100L

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)
