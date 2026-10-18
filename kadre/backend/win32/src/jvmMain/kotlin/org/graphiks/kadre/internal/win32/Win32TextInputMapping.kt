package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.input.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.internal.runtime.TextInputObservation
import org.graphiks.kadre.surface.LogicalRect
import org.graphiks.kffi.win32.managed.*
import kotlin.math.ceil
import kotlin.math.floor

internal fun win32TextFailure(stage: String, cause: Throwable): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.Win32, "text-input", "$stage-" +
        if (cause is Win32TextException) "hresult-${cause.hresult.toUInt().toString(16)}" else "native-failed")

internal fun win32TextConfiguration(config: TextInputConfig): Win32TextConfiguration =
    Win32TextConfiguration(
        when (config.purpose) {
            TextInputPurpose.Text -> Win32TextPurpose.Text
            TextInputPurpose.Name -> Win32TextPurpose.Name
            TextInputPurpose.Email -> Win32TextPurpose.Email
            TextInputPurpose.Url -> Win32TextPurpose.Url
            TextInputPurpose.Telephone -> Win32TextPurpose.Telephone
            TextInputPurpose.Number -> Win32TextPurpose.Number
            TextInputPurpose.Decimal -> Win32TextPurpose.Decimal
            TextInputPurpose.Password -> Win32TextPurpose.Password
        },
        when (config.action) {
            TextInputAction.Default -> Win32TextAction.Default
            TextInputAction.Done -> Win32TextAction.Done
            TextInputAction.Go -> Win32TextAction.Go
            TextInputAction.Next -> Win32TextAction.Next
            TextInputAction.Search -> Win32TextAction.Search
            TextInputAction.Send -> Win32TextAction.Send
        },
        when (config.capitalization) {
            TextCapitalization.None -> Win32TextCapitalization.None
            TextCapitalization.Sentences -> Win32TextCapitalization.Sentences
            TextCapitalization.Words -> Win32TextCapitalization.Words
            TextCapitalization.Characters -> Win32TextCapitalization.Characters
        }, config.autocorrect, config.multiline,
    )

internal fun TextRange.win32Range(): Win32TextRange = Win32TextRange(startUtf16, endExclusiveUtf16)
private fun Win32TextRange.publicRange(): TextRange = TextRange(start, endExclusive)
internal val Win32TextChange.payloadText: String get() = when (this) {
    is Win32TextChange.Replace -> text
    is Win32TextChange.Composition -> text
    else -> ""
}
internal fun Win32TextChange.publicObservation(action: TextInputAction): TextInputObservation {
    val revision = TextDocumentRevision(epoch)
    return when (this) {
        is Win32TextChange.Replace -> TextInputObservation.Replace(range.publicRange(), text, revision)
        is Win32TextChange.Selection -> TextInputObservation.SelectionChanged(range.publicRange(), revision)
        is Win32TextChange.Composition -> TextInputObservation.CompositionChanged(range?.publicRange(), text, selection?.publicRange(), revision)
        is Win32TextChange.Submit -> TextInputObservation.Action(action, revision)
    }
}

internal fun win32CaretRect(rect: LogicalRect, scale: Double, origin: Win32Point): Win32WindowRect {
    require(scale.isFinite() && scale > 0) { "scale" }
    fun checked(value: Double): Int {
        require(value.isFinite() && value >= Int.MIN_VALUE.toDouble() && value <= Int.MAX_VALUE.toDouble()) { "rect" }
        return value.toInt()
    }
    val left = floor(rect.origin.x * scale)
    val top = floor(rect.origin.y * scale)
    val right = ceil((rect.origin.x + rect.size.width) * scale)
    val bottom = ceil((rect.origin.y + rect.size.height) * scale)
    checked(right + origin.x); checked(bottom + origin.y)
    return Win32WindowRect(checked(left + origin.x), checked(top + origin.y), checked(right - left), checked(bottom - top))
}
