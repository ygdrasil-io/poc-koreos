/**
 * Sous-classe Objective-C de NSApplication pour koreos.
 *
 * Override de `sendEvent:` pour intercepter les événements clavier (NSEventTypeKeyDown /
 * NSEventTypeKeyUp) et les dispatcher comme [WindowEvent.KeyboardInput] vers
 * l'[AppKitEventLoop] actif.
 *
 * GRA-154 : ajout du support clavier via sendEvent: NSEvent interception.
 */
package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.appkit.bindings.NSApplication
import io.ygdrasil.koreos.appkit.bindings.NSApplicationActivationPolicy
import io.ygdrasil.koreos.appkit.bindings.ObjCRuntime
import io.ygdrasil.koreos.core.KeyState
import io.ygdrasil.koreos.core.MouseButton
import io.ygdrasil.koreos.core.PhysicalPosition
import io.ygdrasil.koreos.core.WindowEvent
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class KoreosApplication private constructor(ptr: MemorySegment) : NSApplication(ptr) {

    /**
     * Définit la politique d'activation de l'application
     * (par défaut : `NSApplicationActivationPolicyRegular`).
     */
    fun setActivationPolicyRegular() {
        ObjCRuntime.msgSend(
            ValueLayout.JAVA_BOOLEAN,
            ptr,
            ObjCRuntime.sel("setActivationPolicy:"),
            NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular.value,
        )
    }

    companion object {
        /** Boucle d'événements active — initialisée par [runApp] avant [initialize]. */
        internal var eventLoop: AppKitEventLoop? = null

        /** Initialise la sous-classe ObjC une seule fois. */
        private val klass: MemorySegment by lazy {
            val cls = ObjCSubclassing.allocateClass("NSApplication", "KoreosApplication")

            // Register sendEvent: upcall
            val linker = Linker.nativeLinker()
            val sendEventMethod = MethodHandles.lookup().findStatic(
                Callbacks::class.java,
                "sendEvent",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            )
            val sendEventStub = linker.upcallStub(
                sendEventMethod,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                Arena.global(),
            )
            ObjCSubclassing.addMethod(cls, "sendEvent:", sendEventStub, "v@:@")

            ObjCSubclassing.registerClass(cls)
            cls
        }

        /**
         * Crée (ou récupère) l'instance unique partagée de `KoreosApplication`.
         *
         * Doit être appelé depuis le thread principal — l'invariant est validé
         * via [MainThreadCheck].
         */
        fun initialize(): KoreosApplication {
            MainThreadCheck.require()
            // Force la registration de la sous-classe avant le sharedApplication.
            klass
            val appClass = ObjCRuntime.getClass("KoreosApplication")
            val sharedApp = ObjCRuntime.msgSend(
                ValueLayout.ADDRESS,
                appClass,
                ObjCRuntime.sel("sharedApplication"),
            ) as MemorySegment
            return KoreosApplication(sharedApp)
        }
    }

    /**
     * Trampolines `@JvmStatic` invoqués par les upcall stubs Panama.
     *
     * `sendEvent:` est overridé pour intercepter keyDown/keyUp et les dispatcher
     * vers [AppKitEventLoop] comme [WindowEvent.KeyboardInput].
     */
    private object Callbacks {
        @JvmStatic
        fun sendEvent(self: MemorySegment, sel: MemorySegment, event: MemorySegment) {
            // 1. FIRST call super (objc_msgSendSuper) so AppKit processes normally
            callSuperSendEvent(self, sel, event)

            // 2. Then dispatch keyboard events to Koreos
            val loop = eventLoop ?: return

            // Get event type: [event type] → Long
            val eventType = ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, event, ObjCRuntime.sel("type")) as Long

            val isKeyDown = eventType == 10L   // NSEventTypeKeyDown
            val isKeyUp   = eventType == 11L   // NSEventTypeKeyUp

            // ── Keyboard ──────────────────────────────────────────────────────────────
            if (isKeyDown || isKeyUp) {
                // Get the NSWindow for this event: [event window]
                val eventWindow = ObjCRuntime.msgSend(ValueLayout.ADDRESS, event, ObjCRuntime.sel("window")) as MemorySegment
                if (eventWindow == MemorySegment.NULL) return

                // Find the AppKitWindow by NSWindow address (windowId.value == nsWindowPtr.address())
                val appKitWindow = loop.windows[eventWindow.address()] ?: return

                // Get keyCode: [event keyCode] → Short
                val keyCode = ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, event, ObjCRuntime.sel("keyCode")) as Short

                // Get modifierFlags: [event modifierFlags] → Long
                val modFlags = ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, event, ObjCRuntime.sel("modifierFlags")) as Long

                // Get isARepeat: [event isARepeat] → Boolean
                val isRepeat = ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, event, ObjCRuntime.sel("isARepeat")) as Boolean

                val key = AppKitKeyMapper.keyCode(keyCode)
                val modifiers = AppKitKeyMapper.modifierFlags(modFlags)
                val state = if (isKeyDown) KeyState.Pressed else KeyState.Released

                loop.handler.windowEvent(
                    loop,
                    appKitWindow.id,
                    WindowEvent.KeyboardInput(key, state, modifiers, isRepeat),
                )
                return
            }

            // ── Mouse ─────────────────────────────────────────────────────────────────
            val isLeftDown     = eventType == 1L
            val isLeftUp       = eventType == 2L
            val isRightDown    = eventType == 3L
            val isRightUp      = eventType == 4L
            val isMouseMoved   = eventType == 5L
            val isLeftDragged  = eventType == 6L
            val isRightDragged = eventType == 7L
            val isMouseEntered = eventType == 8L
            val isMouseExited  = eventType == 9L
            val isScrollWheel  = eventType == 22L
            val isOtherDown    = eventType == 25L
            val isOtherUp      = eventType == 26L
            val isOtherDragged = eventType == 27L

            val isAnyMouse = isLeftDown || isLeftUp || isRightDown || isRightUp ||
                isMouseMoved || isLeftDragged || isRightDragged ||
                isMouseEntered || isMouseExited || isScrollWheel ||
                isOtherDown || isOtherUp || isOtherDragged

            if (!isAnyMouse) return

            // Get the window for this event
            val eventWindow = ObjCRuntime.msgSend(ValueLayout.ADDRESS, event, ObjCRuntime.sel("window")) as MemorySegment
            if (eventWindow == MemorySegment.NULL) return

            val appKitWindow = loop.windows[eventWindow.address()] ?: return

            // ── Pointer enter/exit ────────────────────────────────────────────────────
            if (isMouseEntered) {
                loop.handler.windowEvent(loop, appKitWindow.id, WindowEvent.PointerEntered)
                return
            }
            if (isMouseExited) {
                loop.handler.windowEvent(loop, appKitWindow.id, WindowEvent.PointerLeft)
                return
            }

            // ── Scroll wheel ──────────────────────────────────────────────────────────
            if (isScrollWheel) {
                val deltaX = ObjCRuntime.msgSendDouble(event, ObjCRuntime.sel("scrollingDeltaX"))
                val deltaY = ObjCRuntime.msgSendDouble(event, ObjCRuntime.sel("scrollingDeltaY"))
                loop.handler.windowEvent(loop, appKitWindow.id, WindowEvent.MouseWheel(deltaX, deltaY))
                return
            }

            // ── Pointer position (shared for move and click) ───────────────────────────
            // locationInWindow returns NSPoint (struct { CGFloat x, y })
            val locPt = ObjCRuntime.msgSendReturningStruct(
                ObjCRuntime.nsPointLayout, event, ObjCRuntime.sel("locationInWindow"))
            val locX = locPt.getAtIndex(ValueLayout.JAVA_DOUBLE, 0)
            val locY = locPt.getAtIndex(ValueLayout.JAVA_DOUBLE, 1)

            // Flip Y: NSView origin is bottom-left, Koreos origin is top-left
            val scale = appKitWindow.scaleFactor
            // Get content view height in points from innerSize (already in physical pixels / scaleFactor)
            val contentHeightPoints = appKitWindow.innerSize.height / scale
            val physX = locX * scale
            val physY = (contentHeightPoints - locY) * scale
            val position = PhysicalPosition(physX, physY)

            // ── Mouse move / drag ─────────────────────────────────────────────────────
            if (isMouseMoved || isLeftDragged || isRightDragged || isOtherDragged) {
                loop.handler.windowEvent(loop, appKitWindow.id, WindowEvent.PointerMoved(position))
                return
            }

            // ── Mouse buttons ─────────────────────────────────────────────────────────
            val button: MouseButton = when {
                isLeftDown || isLeftUp   -> MouseButton.Left
                isRightDown || isRightUp -> MouseButton.Right
                isOtherDown || isOtherUp -> {
                    val btnNum = ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, event, ObjCRuntime.sel("buttonNumber")) as Long
                    if (btnNum == 2L) MouseButton.Middle else MouseButton.Other(btnNum.toInt())
                }
                else -> return
            }
            val state = if (isLeftDown || isRightDown || isOtherDown) KeyState.Pressed else KeyState.Released
            loop.handler.windowEvent(loop, appKitWindow.id, WindowEvent.MouseInput(button, state))
        }

        private fun callSuperSendEvent(self: MemorySegment, sel: MemorySegment, event: MemorySegment) {
            Arena.ofConfined().use { arena ->
                // struct objc_super { id receiver; Class super_class; }
                val superStruct = arena.allocate(16L, 8L)
                superStruct.setAtIndex(ValueLayout.ADDRESS, 0, self)
                superStruct.setAtIndex(ValueLayout.ADDRESS, 1, ObjCRuntime.getClass("NSApplication"))
                val msgSendSuperAddr = SymbolLookup.loaderLookup().find("objc_msgSendSuper")
                    .orElseGet {
                        SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global())
                            .find("objc_msgSendSuper").get()
                    }
                val desc = FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // struct objc_super *
                    ValueLayout.ADDRESS, // SEL
                    ValueLayout.ADDRESS, // id event
                )
                val handle = Linker.nativeLinker().downcallHandle(msgSendSuperAddr, desc)
                handle.invokeExact(superStruct, sel, event)
            }
        }
    }
}
