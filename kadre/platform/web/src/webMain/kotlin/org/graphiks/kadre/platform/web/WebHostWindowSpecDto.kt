package org.graphiks.kadre.platform.web

import org.graphiks.kadre.display.DisplayId
import org.graphiks.kadre.surface.BinaryImage
import org.graphiks.kadre.surface.ImageFormat
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowSystemButtons
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The JSON of the `KadreWindowSpec` DTO the facade's `windowProvider` receives (plan decision D8,
 * `kadre/INTEROP-EXPORTS.md` section 6): the readonly object whose shape that section declares,
 * built here — where the closed value model lives — as the string the target glue materialises into
 * the JavaScript object with `JSON.parse`.
 *
 * `icon.bytes` travels base64-encoded in that string: a Kotlin `ByteArray` cannot cross the Wasm
 * boundary, and the glue rebuilds the payload as a fresh `Uint8Array` — the copy of the requester's
 * bytes the promise states, never the array itself. Everything else is numbers, booleans, the closed
 * names below and `null`s.
 */

/** The closed `decorations` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun WindowDecorations.interopName(): String = when (this) {
    WindowDecorations.System -> "system"
    WindowDecorations.Borderless -> "borderless"
}

/** The closed `systemButtons` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun WindowSystemButtons.interopName(): String = when (this) {
    WindowSystemButtons.All -> "all"
    WindowSystemButtons.CloseOnly -> "closeOnly"
    WindowSystemButtons.None -> "none"
}

/** The closed `level` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun WindowLevel.interopName(): String = when (this) {
    WindowLevel.Normal -> "normal"
    WindowLevel.Floating -> "floating"
    WindowLevel.Modal -> "modal"
}

/** The closed icon `format` union of `kadre/INTEROP-EXPORTS.md` section 6. */
internal fun ImageFormat.interopName(): String = when (this) {
    ImageFormat.Png -> "png"
    ImageFormat.Jpeg -> "jpeg"
    ImageFormat.Webp -> "webp"
    ImageFormat.Rgba8 -> "rgba8"
}

/**
 * The display token the DTO of [spec] carries, resolved per display identity by the interop layer:
 * stable for one display across every request of the page, opaque to the consumer, since the Kotlin
 * `DisplayId` — like every identity — does not cross the boundary.
 */
internal fun windowSpecDisplayToken(spec: WindowSpec): String? =
    (spec.fullscreen as? FullscreenMode.Exclusive)?.let { KadreWebInterop.displayKey(it.displayId) }

/**
 * Encodes [spec] as the JSON of its DTO, with [displayId] naming the display an exclusive fullscreen
 * request targets (a `null` only when the request names no display).
 */
internal fun webWindowSpecDtoJson(spec: WindowSpec, displayId: String?): String = buildString {
    append('{')
    quoted("title", spec.title)
    append(",\"contentSize\":").append(spec.contentSize.toDto())
    append(",\"minimumSize\":").append(spec.minimumSize.toDtoOrNull())
    append(",\"maximumSize\":").append(spec.maximumSize.toDtoOrNull())
    append(",\"outerPosition\":").append(spec.outerPosition.toDtoOrNull())
    append(",\"resizable\":").append(spec.resizable)
    append(",\"fullscreen\":").append(spec.fullscreen.toDto(displayId))
    append(",\"decorations\":\"").append(spec.decorations.interopName()).append('"')
    append(",\"systemButtons\":\"").append(spec.systemButtons.interopName()).append('"')
    append(",\"level\":\"").append(spec.level.interopName()).append('"')
    append(",\"transparent\":").append(spec.transparent)
    append(",\"blurBehind\":").append(spec.blurBehind)
    append(",\"icon\":").append(spec.icon.toDto())
    append(",\"contentProtection\":").append(spec.contentProtection)
    append('}')
}

private fun LogicalSize.toDto(): String =
    """{"width":${jsonNumber(width)},"height":${jsonNumber(height)}}"""

private fun LogicalSize?.toDtoOrNull(): String = this?.toDto() ?: "null"

private fun PhysicalPoint?.toDtoOrNull(): String =
    this?.let { """{"x":${it.x},"y":${it.y}}""" } ?: "null"

private fun FullscreenMode.toDto(displayId: String?): String = when (this) {
    FullscreenMode.Windowed -> """{"kind":"windowed"}"""
    FullscreenMode.Borderless -> """{"kind":"borderless"}"""
    is FullscreenMode.Exclusive -> buildString {
        append("{\"kind\":\"exclusive\",\"displayId\":\"")
        append(WebInteropJson.escape(displayId ?: ""))
        append('"')
        append(",\"physicalWidth\":").append(mode.physicalSize.width)
        append(",\"physicalHeight\":").append(mode.physicalSize.height)
        append(",\"refreshRateHz\":").append(mode.refreshRateHz?.let(::jsonNumber) ?: "null")
        append(",\"bitDepth\":").append(mode.bitDepth ?: "null")
        append('}')
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun BinaryImage?.toDto(): String = when (this) {
    null -> "null"
    else -> buildString {
        append("{\"format\":\"").append(format.interopName())
        append("\",\"bytes\":\"").append(Base64.encode(bytes))
        // `bytes` itself is a fresh copy (`BinaryImage.bytes` copies on every read), and the glue's
        // `Uint8Array` is built from this string — the provider shares bytes with nobody.
        append("\",\"pixelSize\":")
        append(pixelSize?.let { """{"width":${it.width},"height":${it.height}}""" } ?: "null")
        append('}')
    }
}

private fun StringBuilder.quoted(name: String, value: String): StringBuilder =
    append('"').append(name).append("\":\"").append(WebInteropJson.escape(value)).append('"')

/** Renders a `Double` the constructors of the value model already validated as finite. */
private fun jsonNumber(value: Double): String = value.toString()
