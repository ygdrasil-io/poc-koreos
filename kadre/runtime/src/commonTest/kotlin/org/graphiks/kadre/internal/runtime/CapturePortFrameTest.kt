package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CapturePortFrameTest {
    @Test
    fun packedBgraFramesRequireFourBytePixelsAcrossTheCompleteImage() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                format = PixelFormat.Bgra8,
                size = PhysicalSize(2, 1),
                planes = listOf(
                    CapturePortPlane(
                        PixelPlaneLayout(2, 1, 2, 1, 2, 1, 1),
                        byteArrayOf(0, 0),
                    ),
                ),
            )
        }
    }

    @Test
    fun nv12FramesRequireTheirInterleavedChromaPlane() {
        assertFailsWith<IllegalArgumentException> {
            frame(
                format = PixelFormat.Nv12,
                size = PhysicalSize(2, 2),
                planes = listOf(
                    CapturePortPlane(
                        PixelPlaneLayout(2, 2, 2, 1, 4, 1, 1),
                        byteArrayOf(0, 0, 0, 0),
                    ),
                ),
            )
        }
    }
}

private fun frame(
    format: PixelFormat,
    size: PhysicalSize,
    planes: List<CapturePortPlane>,
): CapturePortFrame = CapturePortFrame(
    size = size,
    format = format,
    planes = planes,
    configurationRevision = CaptureConfigurationRevision(0L).value,
    sourceTimestamp = null,
    duration = null,
    discontinuity = null,
    colorEncoding = ColorEncoding(
        ColorPrimaries.Unknown,
        TransferFunction.Unknown,
        MatrixCoefficients.Unknown,
        ColorRange.Unknown,
        HdrMetadata.Unknown,
    ),
    alphaMode = AlphaMode.Unknown,
    orientation = CaptureOrientation.Upright,
)
