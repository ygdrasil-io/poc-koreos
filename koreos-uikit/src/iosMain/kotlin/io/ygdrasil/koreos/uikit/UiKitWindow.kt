package io.ygdrasil.koreos.uikit

import io.ygdrasil.koreos.core.PhysicalSize
import io.ygdrasil.koreos.core.RawDisplayHandle
import io.ygdrasil.koreos.core.RawWindowHandle
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import io.ygdrasil.koreos.core.WindowId
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIViewMeta
import platform.UIKit.UIWindow
import kotlinx.cinterop.objcPtr

/**
 * KoreosMetalView : UIView backed by CAMetalLayer.
 *
 * +layerClass override ensures UIKit uses CAMetalLayer as the backing store
 * from the very first layout pass — no sublayer attachment needed.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class KoreosMetalView(frame: CValue<CGRect>) : UIView(frame = frame) {
    companion object : UIViewMeta() {
        override fun layerClass(): ObjCClass = CAMetalLayer.`class`()!!
    }

    val metalLayer: CAMetalLayer get() = layer as CAMetalLayer
}

/**
 * UiKitWindow — implémente Window pour iOS.
 *
 * Crée UIWindow → UIViewController → KoreosMetalView (plein écran).
 * CAMetalLayer est le backing layer de la vue (via +layerClass).
 */
@OptIn(ExperimentalForeignApi::class)
class UiKitWindow(attrs: WindowAttributes) : Window {

    private val uiWindow: UIWindow
    private val viewController: UIViewController
    private val metalView: KoreosMetalView

    override val id: WindowId

    init {
        val screen = UIScreen.mainScreen
        val screenBounds = screen.bounds

        // 1. KoreosMetalView plein écran
        metalView = KoreosMetalView(frame = screenBounds)

        // 2. contentsScale pour HiDPI / Retina
        metalView.metalLayer.setContentsScale(screen.scale)

        // 3. Root view controller hébergeant la metal view
        viewController = UIViewController(nibName = null, bundle = null)
        viewController.setView(metalView)

        // 4. UIWindow
        uiWindow = UIWindow(frame = screenBounds)
        uiWindow.rootViewController = viewController
        if (attrs.visible) {
            uiWindow.makeKeyAndVisible()
        }

        // Window ID depuis le pointeur UIWindow
        id = WindowId(uiWindow.objcPtr().toLong())
    }

    override val rawWindowHandle: Any
        get() = RawWindowHandle.UiKit(
            uiView = metalView.objcPtr().toLong(),
            uiViewController = viewController.objcPtr().toLong(),
        )

    override val rawDisplayHandle: Any
        get() = RawDisplayHandle.UiKit

    override fun requestRedraw() {
        // Redraw signaling — no-op pour M3 ; le loop CADisplayLink (GRA-144+) cadence les frames.
    }

    override fun setTitle(title: String) {
        viewController.title = title
    }

    override val innerSize: PhysicalSize<Int>
        get() {
            val scale = UIScreen.mainScreen.scale
            return metalView.bounds.useContents {
                PhysicalSize(
                    (size.width * scale).toInt(),
                    (size.height * scale).toInt(),
                )
            }
        }

    override val outerSize: PhysicalSize<Int>
        get() {
            val scale = UIScreen.mainScreen.scale
            return uiWindow.bounds.useContents {
                PhysicalSize(
                    (size.width * scale).toInt(),
                    (size.height * scale).toInt(),
                )
            }
        }

    override val scaleFactor: Double
        get() = UIScreen.mainScreen.scale

    override fun setVisible(visible: Boolean) {
        uiWindow.setHidden(!visible)
        if (visible) uiWindow.makeKeyAndVisible()
    }

    override fun close() {
        uiWindow.setHidden(true)
        uiWindow.resignKeyWindow()
    }
}
