package org.graphiks.kadre.capture

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.time.Duration

public sealed interface PixelFormat {
    public data object Rgba8 : PixelFormat
    public data object Bgra8 : PixelFormat
    public data object Bgrx8 : PixelFormat
    public data object Nv12 : PixelFormat
    public data object I420 : PixelFormat

    public data class Opaque(public val code: String, public val planeCount: Int) : PixelFormat {
        init {
            require(code.isNotEmpty() && code.length <= 256 && code.all { it.code in 0x21..0x7e }) {
                "code must be a non-empty ASCII identifier of at most 256 code units"
            }
            require(planeCount > 0) { "planeCount must be positive" }
        }
    }
}

public enum class AlphaMode { Opaque, Straight, Premultiplied, Unknown }
public enum class ColorPrimaries { Bt601, Bt709, Bt2020, DisplayP3, Unknown }
public enum class TransferFunction { Linear, Srgb, Bt1886, Pq, Hlg, Unknown }
public enum class MatrixCoefficients { Identity, Bt601, Bt709, Bt2020NonConstant, Unknown }
public enum class ColorRange { Full, Limited, Unknown }

public class Chromaticity(x: Double, y: Double) {
    public val x: Double = canonicalChromaticity(x, "x")
    public val y: Double = canonicalChromaticity(y, "y")
    public operator fun component1(): Double = x
    public operator fun component2(): Double = y
    public fun copy(x: Double = this.x, y: Double = this.y): Chromaticity = Chromaticity(x, y)
    override fun equals(other: Any?): Boolean = other is Chromaticity && x == other.x && y == other.y
    override fun hashCode(): Int = 31 * x.hashCode() + y.hashCode()
    override fun toString(): String = "Chromaticity(x=$x, y=$y)"
}

public data class MasteringDisplayMetadata(
    public val red: Chromaticity,
    public val green: Chromaticity,
    public val blue: Chromaticity,
    public val whitePoint: Chromaticity,
    public val minimumLuminanceNits: Double,
    public val maximumLuminanceNits: Double,
) {
    init {
        require(
            minimumLuminanceNits.isFinite() &&
                maximumLuminanceNits.isFinite() &&
                minimumLuminanceNits >= 0.0 &&
                maximumLuminanceNits > minimumLuminanceNits,
        ) { "mastering luminance range is invalid" }
    }
}

public sealed interface HdrMetadata {
    public data object None : HdrMetadata

    public data class Static(
        public val masteringDisplay: MasteringDisplayMetadata?,
        public val maximumContentLightLevelNits: Double?,
        public val maximumFrameAverageLightLevelNits: Double?,
    ) : HdrMetadata {
        init {
            require(
                masteringDisplay != null ||
                    maximumContentLightLevelNits != null ||
                    maximumFrameAverageLightLevelNits != null,
            ) { "static HDR metadata must contain at least one value" }
            validateOptionalLuminance(maximumContentLightLevelNits, "maximumContentLightLevelNits")
            validateOptionalLuminance(maximumFrameAverageLightLevelNits, "maximumFrameAverageLightLevelNits")
            require(
                maximumContentLightLevelNits == null ||
                    maximumFrameAverageLightLevelNits == null ||
                    maximumFrameAverageLightLevelNits <= maximumContentLightLevelNits,
            ) { "frame average light level must not exceed content light level" }
        }
    }

    public data object Unknown : HdrMetadata
}

public data class ColorEncoding(
    public val primaries: ColorPrimaries,
    public val transfer: TransferFunction,
    public val matrix: MatrixCoefficients,
    public val range: ColorRange,
    public val hdr: HdrMetadata,
)

public interface CaptureFrame : AutoCloseable {
    public val size: PhysicalSize
    public val format: PixelFormat
    public val planes: List<PixelPlaneLayout>
    public val configurationRevision: CaptureConfigurationRevision
    public val stamp: EventStamp
    public val sourceTimestamp: CaptureSourceInstant?
    public val duration: Duration?
    public val discontinuity: CaptureDiscontinuity?
    public val colorEncoding: ColorEncoding
    public val alphaMode: AlphaMode
    public val orientation: CaptureOrientation

    override fun close()
    public fun copyPlanes(): List<CopiedPixelPlane>
}

public data class PixelPlaneLayout(
    public val width: Int,
    public val height: Int,
    public val rowStride: Int,
    public val pixelStride: Int,
    public val byteCount: Int,
    public val horizontalSubsampling: Int,
    public val verticalSubsampling: Int,
) {
    init {
        require(
            width > 0 &&
                height > 0 &&
                rowStride > 0 &&
                pixelStride > 0 &&
                byteCount > 0 &&
                horizontalSubsampling > 0 &&
                verticalSubsampling > 0,
        ) { "plane layout values must be positive" }
        val lastSampleEnd = (width.toLong() - 1L) * pixelStride + 1L
        val lastRowEnd = (height.toLong() - 1L) * rowStride + lastSampleEnd
        require(rowStride.toLong() >= lastSampleEnd) { "rowStride is too small" }
        require(byteCount.toLong() >= lastRowEnd) { "byteCount is too small" }
    }
}

public class CopiedPixelPlane internal constructor(
    public val layout: PixelPlaneLayout,
    public val bytes: ByteArray,
) {
    init {
        require(bytes.size == layout.byteCount) { "bytes must match layout.byteCount" }
    }
}

private fun validateOptionalLuminance(value: Double?, name: String) {
    require(value == null || value.isFinite() && value >= 0.0) { "$name must be finite and non-negative" }
}

private fun canonicalChromaticity(value: Double, name: String): Double {
    require(value.isFinite() && value in 0.0..1.0) { "$name must be in [0, 1]" }
    return if (value == 0.0) 0.0 else value
}
