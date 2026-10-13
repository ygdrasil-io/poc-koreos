@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import org.graphiks.kadre.input.GestureKind
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIHoverGestureRecognizer
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIPinchGestureRecognizer
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIRotationGestureRecognizer
import platform.UIKit.UITapGestureRecognizer

/**
 * L'actual iOS des fabriques de recognizers ([createGestureRecognizers],
 * [createHoverGestureRecognizer] et les lecteurs typés) : LE klib iOS porte les cinq classes —
 * les quatre gestes (tap/pan/pinch/rotation) et le survol `UIHoverGestureRecognizer`
 * (`API_AVAILABLE(ios(13.0))`, header vérifié). Chaque recognizer est construit en target/action
 * sur le proxy du binding, `cancelsTouchesInView` reste posé par le binding (la décision tracée
 * : le host garde ses touches).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun createGestureRecognizers(target: UIKitGestureProxy): List<Pair<UIGestureRecognizer, GestureKind>> {
    val tap = UITapGestureRecognizer(target = target, action = NSSelectorFromString(GESTURE_TAP_ACTION))
    tap.numberOfTapsRequired = DOUBLE_TAP_REQUIRED_TAPS
    val pan = UIPanGestureRecognizer(target = target, action = NSSelectorFromString(GESTURE_PAN_ACTION))
    val pinch = UIPinchGestureRecognizer(target = target, action = NSSelectorFromString(GESTURE_PINCH_ACTION))
    val rotation = UIRotationGestureRecognizer(target = target, action = NSSelectorFromString(GESTURE_ROTATION_ACTION))
    return listOf(
        tap to GestureKind.DoubleTap,
        pan to GestureKind.Pan,
        pinch to GestureKind.Pinch,
        rotation to GestureKind.Rotation,
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun createHoverGestureRecognizer(target: UIKitGestureProxy, action: String): UIGestureRecognizer? =
    UIHoverGestureRecognizer(target = target, action = NSSelectorFromString(action))

internal actual fun pinchScaleOf(recognizer: UIGestureRecognizer): Double? =
    (recognizer as? UIPinchGestureRecognizer)?.scale

internal actual fun rotationRadiansOf(recognizer: UIGestureRecognizer): Double? =
    (recognizer as? UIRotationGestureRecognizer)?.rotation
