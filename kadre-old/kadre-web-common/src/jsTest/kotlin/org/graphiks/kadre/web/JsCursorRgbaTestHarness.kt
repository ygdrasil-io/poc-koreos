package org.graphiks.kadre.web

internal actual fun platformWidenCursorRgba(rgba: ByteArray): List<Int> =
    rgba.map(::widenCursorRgbaByte)

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
        val dataUrl = JsWebDomBridge().createCursorDataUrl(rgba, width, height, hotspotX, hotspotY)
        val imageData: dynamic = if (probe.canvas == null) null else
            probe.canvas.getContext("2d").getImageData(0.0, 0.0, 1.0, 1.0)
        val rawPixels: dynamic = if (imageData == null) js("[]") else imageData.data
        val pixelCount = (rawPixels.length as Number).toInt()
        CursorRgbaProbeResult(
            dataUrl = dataUrl,
            canvasCreationCount = (probe.canvasCreationCount as Number).toInt(),
            imageDataCreationCount = if (imageData == null) 0 else 1,
            imageDataRgba = List(pixelCount) { index -> (rawPixels[index] as Number).toInt() },
        )
    } finally {
        probe.restore()
    }
    return result.copy(documentCreateElementRestored = probe.isRestored() as Boolean)
}

private fun installCursorProbe(blockCanvasCreation: Boolean): dynamic = js(
    """(() => {
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
    })()"""
)
