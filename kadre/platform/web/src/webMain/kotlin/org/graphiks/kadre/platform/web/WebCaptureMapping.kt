package org.graphiks.kadre.platform.web

import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureRegion
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.DurationUnit

/** The one format plan a frame's native word and the request's preference produce (decision 6). */
internal sealed interface WebFormatPlan {
    /** The delivered format — the frame's own when its word maps to a promised portable format. */
    val format: PixelFormat

    /**
     * The native word is one of the promised three: the frame is copied in its own format, the
     * request's unmatched preference notwithstanding (decision 6's tier order).
     */
    data class Native(override val format: PixelFormat) : WebFormatPlan

    /**
     * The native word is not portable: the pump converts via `copyTo`'s format option to
     * [format] — the first preference the backend promises, else Rgba8 — and records
     * [requested] (the request's own first word, `null` when it named none) as the requested
     * half of the `BackendFallback` diagnostic the conversion owes the caller.
     */
    data class Converted(override val format: PixelFormat, val requested: PixelFormat?) : WebFormatPlan
}

/**
 * The pure VideoFrame-shape → model mapping of decision 6: the format tiers, the browser's color
 * words, the alpha answers, the picker hints and the timestamp readings. No browser object crosses —
 * every input is a structural seam value, every output a model value, and every branch is total.
 */
internal object WebCaptureMapping {
    /** The formats decision 6 promises: the pump delivers one of these or fails honestly. */
    internal val PROMISED_FORMATS = setOf(PixelFormat.Rgba8, PixelFormat.I420, PixelFormat.Nv12)

    /** The browser's format words that already are the promised portable layouts, word for word. */
    private val PORTABLE_WORDS = mapOf(
        "RGBA" to PixelFormat.Rgba8,
        "I420" to PixelFormat.I420,
        "NV12" to PixelFormat.Nv12,
    )

    /** The promised model format behind a browser format word, or `null` when the word is none of them. */
    internal fun portableFormat(word: String): PixelFormat? = PORTABLE_WORDS[word]

    /** The copyTo format word a promised model format converts to. */
    private val CONVERSION_WORDS = mapOf(
        PixelFormat.Rgba8 to "RGBA",
        PixelFormat.I420 to "I420",
        PixelFormat.Nv12 to "NV12",
    )

    /**
     * Decision 6's tier order: a portable native word is delivered as itself (the request's
     * unmatched preference notwithstanding — tier two); a non-portable native converts to the
     * first preference the backend promises, else to Rgba8, and owes the diagnostic.
     */
    fun targetFormat(preferred: List<PixelFormat>, native: String): WebFormatPlan {
        val portable = PORTABLE_WORDS[native]
        if (portable != null) return WebFormatPlan.Native(portable)
        val target = preferred.firstOrNull { it in PROMISED_FORMATS } ?: PixelFormat.Rgba8
        return WebFormatPlan.Converted(target, requested = preferred.firstOrNull())
    }

    /** The copyTo format word a conversion to [format] asks for; `null` is the frame's own format. */
    fun conversionWord(format: PixelFormat): String? = CONVERSION_WORDS[format]

    /**
     * Decision 8's crop rect: the request's region as the browser's `visibleRect` dictionary
     * shape — the origin and the extent, copied structurally, nothing read back.
     */
    fun visibleRect(region: CaptureRegion): WebVisibleRect =
        WebVisibleRect(
            x = region.rect.origin.x,
            y = region.rect.origin.y,
            width = region.rect.size.width,
            height = region.rect.size.height,
        )

    /**
     * The frame's color words, Unknown-safe: a frame with no `colorSpace` maps to the AppKit
     * unknown form; a present `colorSpace` maps word by word, with every word the spec does not
     * define mapping to Unknown and the metadata the browser never carries answered as absent.
     */
    fun colorEncoding(frameShape: WebVideoFrameShape): ColorEncoding {
        val colorSpace = frameShape.colorSpace ?: return ColorEncoding(
            primaries = ColorPrimaries.Unknown,
            transfer = TransferFunction.Unknown,
            matrix = MatrixCoefficients.Unknown,
            range = ColorRange.Unknown,
            hdr = HdrMetadata.Unknown,
        )
        return ColorEncoding(
            primaries = when (colorSpace.primaries) {
                "bt709" -> ColorPrimaries.Bt709
                "bt470bg", "smpte170m" -> ColorPrimaries.Bt601
                "bt2020" -> ColorPrimaries.Bt2020
                else -> ColorPrimaries.Unknown
            },
            transfer = when (colorSpace.transfer) {
                "bt709", "smpte170m" -> TransferFunction.Bt1886
                "iec61966-2-1" -> TransferFunction.Srgb
                "linear" -> TransferFunction.Linear
                "smpte2084" -> TransferFunction.Pq
                "arib-std-b67" -> TransferFunction.Hlg
                else -> TransferFunction.Unknown
            },
            matrix = when (colorSpace.matrix) {
                "rgb" -> MatrixCoefficients.Identity
                "bt709" -> MatrixCoefficients.Bt709
                "bt470bg", "smpte170m" -> MatrixCoefficients.Bt601
                "bt2020-ncl" -> MatrixCoefficients.Bt2020NonConstant
                else -> MatrixCoefficients.Unknown
            },
            range = when (colorSpace.fullRange) {
                true -> ColorRange.Full
                false -> ColorRange.Limited
                null -> ColorRange.Unknown
            },
            // The VideoFrame colorSpace carries no mastering metadata: a frame that said anything
            // about its colors has said all it has — the HDR metadata is absent, not unknown.
            hdr = HdrMetadata.None,
        )
    }

    /**
     * The alpha answer per delivered format and source: planar chroma formats have none (Opaque),
     * a canvas-sourced Rgba8 is the canvas's own premultiplied compositing, and a display-sourced
     * Rgba8 is the browser's silence — the DOM exposes no alpha word for it, so Unknown.
     */
    fun alphaMode(format: PixelFormat, canvasSourced: Boolean): AlphaMode = when {
        format == PixelFormat.I420 || format == PixelFormat.Nv12 -> AlphaMode.Opaque
        format == PixelFormat.Rgba8 && canvasSourced -> AlphaMode.Premultiplied
        else -> AlphaMode.Unknown
    }

    /** Decision 5's cursor hint: the browser's own picker words for the request's cursor mode. */
    fun cursorHint(cursorMode: CaptureCursorMode): String = when (cursorMode) {
        CaptureCursorMode.Hidden -> "never"
        CaptureCursorMode.Embedded -> "always"
        CaptureCursorMode.EmbeddedWhenAvailable -> "motion"
    }

    /**
     * Decision 5's frameRate hint, read from the request's minimum frame interval: a cap in frames
     * per second is the reciprocal of the smallest gap the request accepts. `null` states nothing.
     */
    fun frameRateHint(minimumFrameInterval: Duration?): Double? =
        minimumFrameInterval?.let { 1.0 / it.toDouble(DurationUnit.SECONDS) }

    /**
     * The presentation timestamp as the model's non-negative duration; the SPI forbids a negative
     * source instant, and a browser timestamp that precedes its epoch simply says nothing.
     */
    fun sourceTimestamp(timestampUs: Long?): Duration? =
        timestampUs?.takeIf { it >= 0L }?.microseconds

    /** The frame duration as the model's positive duration; a non-positive word says nothing. */
    fun duration(durationUs: Long?): Duration? =
        durationUs?.takeIf { it > 0L }?.microseconds

    /**
     * The tightly-packed plane layouts of a whole-visible-rect copy in [format] — the layout a
     * default `copyTo` produces by definition, and the exact shape the SPI's portable-format
     * validation requires (the same ceil-half chroma the `CapturePortFrame` constructor checks).
     */
    fun planeLayouts(format: PixelFormat, size: PhysicalSize): List<PixelPlaneLayout> {
        val width = size.width
        val height = size.height
        val chromaWidth = width.ceilHalf()
        val chromaHeight = height.ceilHalf()
        return when (format) {
            PixelFormat.Rgba8, PixelFormat.Bgra8, PixelFormat.Bgrx8 -> listOf(
                PixelPlaneLayout(
                    width = width,
                    height = height,
                    rowStride = width * 4,
                    pixelStride = 4,
                    byteCount = width * 4 * height,
                    horizontalSubsampling = 1,
                    verticalSubsampling = 1,
                ),
            )

            PixelFormat.Nv12 -> listOf(
                lumaLayout(width, height),
                PixelPlaneLayout(
                    width = chromaWidth,
                    height = chromaHeight,
                    rowStride = chromaWidth * 2,
                    pixelStride = 2,
                    byteCount = chromaWidth * 2 * chromaHeight,
                    horizontalSubsampling = 2,
                    verticalSubsampling = 2,
                ),
            )

            PixelFormat.I420 -> listOf(
                lumaLayout(width, height),
                chromaLayout(chromaWidth, chromaHeight),
                chromaLayout(chromaWidth, chromaHeight),
            )

            is PixelFormat.Opaque -> emptyList()
        }
    }

    private fun lumaLayout(width: Int, height: Int) = PixelPlaneLayout(
        width = width,
        height = height,
        rowStride = width,
        pixelStride = 1,
        byteCount = width * height,
        horizontalSubsampling = 1,
        verticalSubsampling = 1,
    )

    private fun chromaLayout(chromaWidth: Int, chromaHeight: Int) = PixelPlaneLayout(
        width = chromaWidth,
        height = chromaHeight,
        rowStride = chromaWidth,
        pixelStride = 1,
        byteCount = chromaWidth * chromaHeight,
        horizontalSubsampling = 2,
        verticalSubsampling = 2,
    )

    private fun Int.ceilHalf(): Int = this / 2 + this % 2
}
