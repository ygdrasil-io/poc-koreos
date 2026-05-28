/**
 * Implémentation AppKit de l'interface Window pour macOS.
 *
 * Crée un NSWindow avec une contentView layer-backed CAMetalLayer,
 * conformément au pattern AppKit Metal (wantsLayer + setLayer).
 *
 * GRA-126 : fenêtre native macOS via FFM, zéro JNA/Rococoa.
 */
package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.appkit.bindings.NSBackingStoreType
import io.ygdrasil.koreos.appkit.bindings.NSRect
import io.ygdrasil.koreos.appkit.bindings.NSView
import io.ygdrasil.koreos.appkit.bindings.NSWindow
import io.ygdrasil.koreos.appkit.bindings.NSWindowStyleMask
import io.ygdrasil.koreos.appkit.bindings.ObjCRuntime
import io.ygdrasil.koreos.core.PhysicalSize
import io.ygdrasil.koreos.core.RawDisplayHandle
import io.ygdrasil.koreos.core.RawWindowHandle
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import io.ygdrasil.koreos.core.WindowId
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * Fenêtre macOS native implémentant [Window].
 *
 * Utilise NSWindow + CAMetalLayer via FFM (Foreign Function & Memory API).
 * Le pattern AppKit Metal respecté : `contentView.wantsLayer = true` PUIS
 * `contentView.layer = CAMetalLayer()` — jamais `+layerClass`.
 */
class AppKitWindow(attrs: WindowAttributes) : Window {

    private val arena = Arena.global()
    private val nsWindowPtr: MemorySegment
    private val contentViewPtr: MemorySegment
    private val metalLayerPtr: MemorySegment

    override val id: WindowId

    init {
        MainThreadCheck.require()

        // 1. Calculer styleMask depuis les attributs
        var styleMask = NSWindowStyleMask.NSWindowStyleMaskTitled +
                NSWindowStyleMask.NSWindowStyleMaskClosable +
                NSWindowStyleMask.NSWindowStyleMaskMiniaturizable
        if (attrs.resizable) {
            styleMask = styleMask + NSWindowStyleMask.NSWindowStyleMaskResizable
        }

        // 2. Taille de la fenêtre (en points logiques — la scaleFactor est 1.0 au moment de l'init,
        //    avant que la fenêtre ne soit rattachée à un écran)
        val width = attrs.size?.width?.toDouble() ?: 800.0
        val height = attrs.size?.height?.toDouble() ?: 600.0
        val contentRect: NSRect = allocNSRect(arena, 100.0, 100.0, width, height)

        // 3. Allouer + initialiser NSWindow via alloc/init
        val nsWindowClass = ObjCRuntime.getClass("NSWindow")
        val allocated = ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            nsWindowClass,
            ObjCRuntime.sel("alloc"),
        ) as MemorySegment

        val backing = NSBackingStoreType.NSBackingStoreBuffered

        val allocatedWindow = NSWindow(allocated)
        val initializedPtr = allocatedWindow.initWithContentRect_styleMask_backing_defer(
            contentRect,
            styleMask,
            backing,
            0.toByte(), // defer: NO
        )
        nsWindowPtr = initializedPtr
        id = WindowId(nsWindowPtr.address())

        // 4. Récupérer la contentView existante de la NSWindow
        val nsWindow = NSWindow(nsWindowPtr)
        contentViewPtr = nsWindow.contentView()

        // 5. Pattern AppKit Metal : wantsLayer = YES PUIS layer = CAMetalLayer()
        //    (JAMAIS +layerClass — voir review PR #1)
        val contentView = NSView(contentViewPtr)
        contentView.setWantsLayer(1.toByte()) // BOOL YES = 1

        val metalLayerClass = ObjCRuntime.getClass("CAMetalLayer")
        metalLayerPtr = ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            metalLayerClass,
            ObjCRuntime.sel("new"),
        ) as MemorySegment
        contentView.setLayer(metalLayerPtr)

        // 6. contentsScale = backingScaleFactor pour support HiDPI / Retina
        val scale = nsWindow.backingScaleFactor()
        ObjCRuntime.msgSend(
            null,
            metalLayerPtr,
            ObjCRuntime.sel("setContentsScale:"),
            scale,
        )

        // 7. Titre initial
        nsWindow.setTitle(attrs.title)

        // 8. Affichage si demandé
        if (attrs.visible) {
            nsWindow.makeKeyAndOrderFront(MemorySegment.NULL)
        }
    }

    override val rawWindowHandle: Any
        get() = RawWindowHandle.AppKit(
            nsView = contentViewPtr.address(),
            nsWindow = nsWindowPtr.address(),
        )

    override val rawDisplayHandle: Any
        get() = RawDisplayHandle.AppKit

    /** Stub M1 : le redraw est géré par le run-loop AppKit. */
    override fun requestRedraw() = Unit

    override fun setTitle(title: String) {
        NSWindow(nsWindowPtr).setTitle(title)
    }

    /**
     * Taille interne de la fenêtre (surface de rendu) en pixels physiques.
     *
     * Lit le frame de la contentView (en points logiques) et multiplie
     * par backingScaleFactor pour obtenir des pixels physiques.
     */
    override val innerSize: PhysicalSize<Int>
        get() {
            val scale = NSWindow(nsWindowPtr).backingScaleFactor()
            val frame = NSView(contentViewPtr).frame().reinterpret(32)
            val w = frame.getAtIndex(ValueLayout.JAVA_DOUBLE, 2)
            val h = frame.getAtIndex(ValueLayout.JAVA_DOUBLE, 3)
            return PhysicalSize((w * scale).toInt(), (h * scale).toInt())
        }

    /**
     * Taille externe de la fenêtre (y compris décorations) en pixels physiques.
     *
     * Lit le frame de la NSWindow (en points logiques) et multiplie
     * par backingScaleFactor.
     */
    override val outerSize: PhysicalSize<Int>
        get() {
            val scale = NSWindow(nsWindowPtr).backingScaleFactor()
            val frame = NSWindow(nsWindowPtr).frame().reinterpret(32)
            val w = frame.getAtIndex(ValueLayout.JAVA_DOUBLE, 2)
            val h = frame.getAtIndex(ValueLayout.JAVA_DOUBLE, 3)
            return PhysicalSize((w * scale).toInt(), (h * scale).toInt())
        }

    override val scaleFactor: Double
        get() = NSWindow(nsWindowPtr).backingScaleFactor()

    override fun setVisible(visible: Boolean) {
        if (visible) {
            NSWindow(nsWindowPtr).makeKeyAndOrderFront(MemorySegment.NULL)
        } else {
            NSWindow(nsWindowPtr).orderOut(MemorySegment.NULL)
        }
    }

    override fun close() {
        NSWindow(nsWindowPtr).close()
    }
}

/**
 * Alloue un NSRect (struct {CGFloat x, CGFloat y, CGFloat width, CGFloat height})
 * dans l'arena fourni.
 *
 * NSRect = 4 × CGFloat (Double 64-bit) = 32 bytes, alignement 8 bytes.
 */
private fun allocNSRect(arena: Arena, x: Double, y: Double, width: Double, height: Double): MemorySegment {
    val seg = arena.allocate(32L, 8L)
    seg.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, x)
    seg.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, y)
    seg.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, width)
    seg.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, height)
    return seg
}
