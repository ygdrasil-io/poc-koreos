package org.graphiks.kadre.web

import kotlinx.browser.document
import kotlinx.browser.window

internal actual fun platformSafeAreaReadings(
    cssInsets: CssSafeAreaTestInsets,
    firstDpr: Double,
    secondDpr: Double,
): PlatformSafeAreaReadings {
    val patch = installSafeAreaPatch(cssInsets, firstDpr, throwOnComputedStyle = false)
    return try {
        val bridge = JsWebDomBridge()
        val first = bridge.getSafeAreaInsets()
        patch.setDpr(secondDpr)
        val second = bridge.getSafeAreaInsets()
        PlatformSafeAreaReadings(first, second, sameBridgeInstance = true)
    } finally {
        patch.restore()
    }
}

internal actual fun platformSafeAreaElementIsRemovedAfterFailure(): Boolean {
    val body = document.body ?: return false
    val before = body.children.length
    val patch = installSafeAreaPatch(
        CssSafeAreaTestInsets(1.0, 2.0, 3.0, 4.0),
        dpr = 2.0,
        throwOnComputedStyle = true,
    )
    return try {
        runCatching { JsWebDomBridge().getSafeAreaInsets() }
        body.children.length == before
    } finally {
        patch.restore()
    }
}

private class JsSafeAreaPatch(
    val setDpr: (Double) -> Unit,
    val restore: () -> Unit,
)

private fun installSafeAreaPatch(
    cssInsets: CssSafeAreaTestInsets,
    dpr: Double,
    throwOnComputedStyle: Boolean,
): JsSafeAreaPatch {
    val browserWindow = window.asDynamic()
    val objectConstructor = js("Object")
    val reflect = js("Reflect")
    val originalComputedStyle = browserWindow.getComputedStyle
    val originalDprDescriptor = objectConstructor.getOwnPropertyDescriptor(
        browserWindow,
        "devicePixelRatio",
    )

    fun setDpr(value: Double) {
        val descriptor = js("({})")
        descriptor.configurable = true
        descriptor.value = value
        objectConstructor.defineProperty(browserWindow, "devicePixelRatio", descriptor)
    }

    setDpr(dpr)
    browserWindow.getComputedStyle = { _: dynamic ->
        if (throwOnComputedStyle) {
            error("computed style failure requested by test")
        }
        val style = js("({})")
        style.paddingTop = "${cssInsets.top}px"
        style.paddingBottom = "${cssInsets.bottom}px"
        style.paddingLeft = "${cssInsets.left}px"
        style.paddingRight = "${cssInsets.right}px"
        style
    }

    return JsSafeAreaPatch(
        setDpr = ::setDpr,
        restore = {
            browserWindow.getComputedStyle = originalComputedStyle
            if (originalDprDescriptor == null) {
                reflect.deleteProperty(browserWindow, "devicePixelRatio")
            } else {
                objectConstructor.defineProperty(
                    browserWindow,
                    "devicePixelRatio",
                    originalDprDescriptor,
                )
            }
        },
    )
}
