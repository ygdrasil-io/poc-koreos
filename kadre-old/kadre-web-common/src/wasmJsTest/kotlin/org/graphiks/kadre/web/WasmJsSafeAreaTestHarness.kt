@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.web

@JsFun("""(top, bottom, left, right, dpr, throwOnComputedStyle) => {
    const originalComputedStyle = globalThis.getComputedStyle;
    const originalDpr = Object.getOwnPropertyDescriptor(window, 'devicePixelRatio') || null;
    const patch = {
        setDpr(value) {
            Object.defineProperty(window, 'devicePixelRatio', {
                configurable: true,
                value: value
            });
        },
        restore() {
            globalThis.getComputedStyle = originalComputedStyle;
            if (originalDpr === null) delete window.devicePixelRatio;
            else Object.defineProperty(window, 'devicePixelRatio', originalDpr);
        }
    };
    patch.setDpr(dpr);
    globalThis.getComputedStyle = () => {
        if (throwOnComputedStyle) throw new Error('computed style failure requested by test');
        return {
            paddingTop: String(top) + 'px',
            paddingBottom: String(bottom) + 'px',
            paddingLeft: String(left) + 'px',
            paddingRight: String(right) + 'px'
        };
    };
    return patch;
}""")
private external fun installSafeAreaPatch(
    top: Double,
    bottom: Double,
    left: Double,
    right: Double,
    dpr: Double,
    throwOnComputedStyle: Boolean,
): JsAny

@JsFun("(patch, dpr) => patch.setDpr(dpr)")
private external fun setSafeAreaDpr(patch: JsAny, dpr: Double)

@JsFun("(patch) => patch.restore()")
private external fun restoreSafeAreaPatch(patch: JsAny)

@JsFun("() => document.body ? document.body.children.length : -1")
private external fun bodyChildCount(): Int

internal actual fun platformSafeAreaReadings(
    cssInsets: CssSafeAreaTestInsets,
    firstDpr: Double,
    secondDpr: Double,
): PlatformSafeAreaReadings {
    val patch = installSafeAreaPatch(
        cssInsets.top,
        cssInsets.bottom,
        cssInsets.left,
        cssInsets.right,
        firstDpr,
        throwOnComputedStyle = false,
    )
    return try {
        val bridge = WasmJsWebDomBridge()
        val first = bridge.getSafeAreaInsets()
        setSafeAreaDpr(patch, secondDpr)
        val second = bridge.getSafeAreaInsets()
        PlatformSafeAreaReadings(first, second, sameBridgeInstance = true)
    } finally {
        restoreSafeAreaPatch(patch)
    }
}

internal actual fun platformSafeAreaElementIsRemovedAfterFailure(): Boolean {
    val before = bodyChildCount()
    val patch = installSafeAreaPatch(
        top = 1.0,
        bottom = 2.0,
        left = 3.0,
        right = 4.0,
        dpr = 2.0,
        throwOnComputedStyle = true,
    )
    return try {
        runCatching { WasmJsWebDomBridge().getSafeAreaInsets() }
        bodyChildCount() == before
    } finally {
        restoreSafeAreaPatch(patch)
    }
}
