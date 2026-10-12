@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.GestureKind
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UITapGestureRecognizer

/**
 * L'actual tvOS des fabriques de recognizers ([createGestureRecognizers],
 * [createHoverGestureRecognizer] et les lecteurs typés) — la surface RÉELLE du klib tvOS
 * (2.4.20, vérifiée au compile ET dans le linkdata) :
 *
 * - `UITapGestureRecognizer` et `UIPanGestureRecognizer` existent : tap double + pan.
 * - `UIPinchGestureRecognizer` et `UIRotationGestureRecognizer` sont `API_UNAVAILABLE(tvos)`
 *   (UIPinchGestureRecognizer.h:19 et UIRotationGestureRecognizer.h:19 du SDK AppleTVOS27.0 —
 *   Apple lui-même déclare ces deux recognizers indisponibles sur tvOS) : ABSENTS du klib par
 *   frontière de plateforme TELLE QUELLE, PAS un trou de mapping K/N. Les lecteurs typés
 *   répondent `null`, les fabriques ne les construisent pas, l'annonce `gestureKinds` dit
 *   EXACTEMENT {DoubleTap, Pan}.
 * - `UIHoverGestureRecognizer` est `API_UNAVAILABLE(tvos)` : PAS de route pointeur tvOS en v1
 *   (décision tracée), `pointerInstalled` reste à faux.
 */
internal actual fun createGestureRecognizers(target: UIKitGestureProxy): List<Pair<UIGestureRecognizer, GestureKind>> {
    val tap = UITapGestureRecognizer(target = target, action = NSSelectorFromString(GESTURE_TAP_ACTION))
    tap.numberOfTapsRequired = DOUBLE_TAP_REQUIRED_TAPS
    val pan = UIPanGestureRecognizer(target = target, action = NSSelectorFromString(GESTURE_PAN_ACTION))
    return listOf(tap to GestureKind.DoubleTap, pan to GestureKind.Pan)
}

internal actual fun createHoverGestureRecognizer(target: UIKitGestureProxy, action: String): UIGestureRecognizer? {
    // Les paramètres ne servent qu'à la signature commune (l'actual iOS construit le
    // recognizer avec eux) : tvOS n'a pas la classe à leur passer.
    @Suppress("UNUSED_EXPRESSION")
    target
    @Suppress("UNUSED_EXPRESSION")
    action
    return null
}

internal actual fun pinchScaleOf(recognizer: UIGestureRecognizer): Double? {
    // Le recognizer pinch n'existe pas dans le klib tvOS : jamais installé, jamais délivré.
    @Suppress("UNUSED_EXPRESSION")
    recognizer
    return null
}

internal actual fun rotationRadiansOf(recognizer: UIGestureRecognizer): Double? {
    @Suppress("UNUSED_EXPRESSION")
    recognizer
    return null
}
