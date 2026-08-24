@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.web

internal actual fun platformWidenCursorRgba(rgba: ByteArray): List<Int> =
    rgba.map { byte -> byte.toInt() and 0xFF }

@JsFun("""(blockCanvasCreation) => {
    const originalDescriptor = Object.getOwnPropertyDescriptor(document, 'createElement');
    const originalCreateElement = document.createElement;
    const state = {
        canvasCreationCount: 0,
        canvas: null,
        restore() {
            if (originalDescriptor === undefined) {
                delete document.createElement;
            } else {
                Object.defineProperty(document, 'createElement', originalDescriptor);
            }
        },
        isRestored() {
            const current = Object.getOwnPropertyDescriptor(document, 'createElement');
            if (originalDescriptor === undefined) return current === undefined;
            return current !== undefined &&
                current.configurable === originalDescriptor.configurable &&
                current.enumerable === originalDescriptor.enumerable &&
                current.writable === originalDescriptor.writable &&
                current.value === originalDescriptor.value &&
                current.get === originalDescriptor.get &&
                current.set === originalDescriptor.set;
        }
    };
    document.createElement = function(tagName) {
        if (String(tagName).toLowerCase() === 'canvas') {
            state.canvasCreationCount += 1;
            if (blockCanvasCreation) throw new Error('cursor allocation blocked by test');
        }
        const element = originalCreateElement.apply(document, arguments);
        if (String(tagName).toLowerCase() === 'canvas') state.canvas = element;
        return element;
    };
    return state;
}""")
private external fun installCursorProbe(blockCanvasCreation: Boolean): JsAny

@JsFun("(probe) => probe.restore()")
private external fun restoreCursorProbe(probe: JsAny)

@JsFun("(probe) => probe.isRestored()")
private external fun cursorDocumentCreateElementRestored(probe: JsAny): Boolean

@JsFun("(probe) => probe.canvasCreationCount")
private external fun cursorCanvasCreationCount(probe: JsAny): Int

@JsFun("(probe) => probe.canvas === null ? 0 : 1")
private external fun cursorImageDataCreationCount(probe: JsAny): Int

@JsFun("(probe) => probe.canvas === null ? 0 : probe.canvas.getContext('2d').getImageData(0, 0, 1, 1).data.length")
private external fun cursorPixelCount(probe: JsAny): Int

@JsFun("(probe, index) => probe.canvas.getContext('2d').getImageData(0, 0, 1, 1).data[index]")
private external fun cursorPixelAt(probe: JsAny, index: Int): Int

internal actual fun platformCursorRgbaProbe(
    rgba: ByteArray,
    width: Int,
    height: Int,
    hotspotX: Int,
    hotspotY: Int,
    blockCanvasCreation: Boolean,
): CursorRgbaProbeResult {
    val probe = installCursorProbe(blockCanvasCreation)
    val result = try {
        val dataUrl = WasmJsWebDomBridge().createCursorDataUrl(rgba, width, height, hotspotX, hotspotY)
        CursorRgbaProbeResult(
            dataUrl = dataUrl,
            canvasCreationCount = cursorCanvasCreationCount(probe),
            imageDataCreationCount = cursorImageDataCreationCount(probe),
            imageDataRgba = List(cursorPixelCount(probe)) { index -> cursorPixelAt(probe, index) },
        )
    } finally {
        restoreCursorProbe(probe)
    }
    return result.copy(documentCreateElementRestored = cursorDocumentCreateElementRestored(probe))
}
