/**
 * Kotlin implementation of `NSWindowDelegate` via ObjC runtime subclassing.
 *
 * Uses Panama FFM upcall stubs to expose Kotlin static functions
 * (`@JvmStatic`) as Objective-C method implementations.
 *
 * Dispatch strategy: the ObjC class `KadreWindowDelegateNative` does not embed
 * a pointer to the Kotlin delegate. Instead, instances are indexed in a
 * global [java.util.concurrent.ConcurrentHashMap] keyed by the memory
 * address (`MemorySegment.address()`) of the ObjC `self`. Since the first
 * argument of any ObjC upcall is `self`, this is enough to retrieve the
 * target Kotlin instance during a native callback.
 *
 * GRA-127: dispatch of WindowEvent.CloseRequested to ApplicationHandler.
 * GRA-132: dispatch of WindowEvent.Resized + update of CAMetalLayer.drawableSize.
 * GRA-133: dispatch of WindowEvent.ScaleFactorChanged + update of CAMetalLayer.contentsScale.
 */
package org.graphiks.kadre.appkit

import org.graphiks.kffi.objc.NSWindow
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.ObjCSubclassing
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal interface AppKitWindowDelegateCallbacks {
    fun onWindowShouldClose(): Byte = 1
    fun onWindowWillClose() = Unit
    fun onWindowDidMove() = Unit
    fun onWindowDidBecomeKey() = Unit
    fun onWindowDidResignKey() = Unit
    fun onWindowDidMiniaturize() = Unit
    fun onWindowDidDeminiaturize() = Unit
    fun onWindowDidResize() = Unit
    fun onWindowDidChangeBackingProperties() = Unit
    fun captureCallbackFailure(context: String, failure: Throwable) = Unit
}

/**
 * macOS window delegate implementing `NSWindowDelegate` via FFM.
 *
 * Intercepts `windowShouldClose:` to dispatch [WindowEvent.CloseRequested],
 * `windowDidResize:` to dispatch [WindowEvent.Resized], and
 * `windowDidChangeBackingProperties:` to dispatch [WindowEvent.ScaleFactorChanged]
 * to the [ApplicationHandler]. Also updates the CAMetalLayer properties
 * (`drawableSize`, `contentsScale`) on each change.
 *
 * @param handler        Application handler receiving the events.
 * @param eventLoop      Active event loop at the time the delegate is created.
 * @param windowId       Identifier of the watched window.
 * @param nsWindowPtr    Native pointer to the associated NSWindow.
 * @param metalLayerPtr  Native pointer to the window's CAMetalLayer.
 */
class KadreWindowDelegate(
    private val handler: ApplicationHandler,
    private val eventLoop: ActiveEventLoop,
    private val windowId: WindowId,
    private val nsWindowPtr: MemorySegment,
    private val metalLayerPtr: MemorySegment,
    private val windows: ConcurrentHashMap<Long, AppKitWindow>,
) {
    private val released = AtomicBoolean(false)
    private val routeCallbacks: AppKitWindowDelegateCallbacks

    /** Pointer to the Objective-C object wrapped by this delegate. */
    val ptr: MemorySegment

    init {
        ensureClassRegistered()

        val cls = ObjCRuntime.getClass("KadreWindowDelegateNative")
        val allocated = ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            cls,
            ObjCRuntime.sel("alloc"),
        ) as MemorySegment
        ptr = ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            allocated,
            ObjCRuntime.sel("init"),
        ) as MemorySegment

        routeCallbacks = object : AppKitWindowDelegateCallbacks {
            override fun onWindowShouldClose(): Byte = this@KadreWindowDelegate.onWindowShouldClose()
            override fun onWindowWillClose() = this@KadreWindowDelegate.onWindowWillClose()
            override fun onWindowDidMove() = this@KadreWindowDelegate.onWindowDidMove()
            override fun onWindowDidBecomeKey() = this@KadreWindowDelegate.onWindowDidBecomeKey()
            override fun onWindowDidResignKey() = this@KadreWindowDelegate.onWindowDidResignKey()
            override fun onWindowDidMiniaturize() = this@KadreWindowDelegate.onWindowDidMiniaturize()
            override fun onWindowDidDeminiaturize() = this@KadreWindowDelegate.onWindowDidDeminiaturize()
            override fun onWindowDidResize() = this@KadreWindowDelegate.onWindowDidResize()
            override fun onWindowDidChangeBackingProperties() =
                this@KadreWindowDelegate.onWindowDidChangeBackingProperties()
            override fun captureCallbackFailure(context: String, failure: Throwable) {
                (eventLoop as? AppKitEventLoop)?.recordCallbackFailure(context, failure)
            }
        }
        registerDelegateRoute(ptr.address(), routeCallbacks)
    }

    /**
     * Kotlin callback for `windowShouldClose:`.
     *
     * Dispatches [WindowEvent.CloseRequested] to the handler. If [eventLoop.exit]
     * was called during the callback, triggers `[NSApp terminate:nil]` to
     * stop the AppKit loop. Returns `0` (BOOL NO) — closing remains under
     * the application's control.
     */
    fun onWindowShouldClose(): Byte {
        handler.windowEvent(eventLoop, windowId, WindowEvent.CloseRequested)
        if (eventLoop.isExiting) {
            val nsAppClass = ObjCRuntime.getClass("NSApplication")
            val nsApp = ObjCRuntime.msgSend(
                ValueLayout.ADDRESS,
                nsAppClass,
                ObjCRuntime.sel("sharedApplication"),
            ) as MemorySegment
            ObjCRuntime.msgSend(null, nsApp, ObjCRuntime.sel("terminate:"), MemorySegment.NULL)
        }
        return 0 // NO — the application controls closing via exit()
    }

    /**
     * Kotlin callback for `windowWillClose:`.
     *
     * Removes this window from the [windows] map when the NSWindow is about
     * to be closed, guaranteeing that no subsequent event (redraw,
     * aboutToWait) targets an already-destroyed window.
     */
    fun onWindowWillClose() {
        val appKitEventLoop = eventLoop as? AppKitEventLoop
        if (appKitEventLoop == null) {
            handler.windowEvent(eventLoop, windowId, WindowEvent.Destroyed)
            windows.remove(windowId.value)
        } else {
            appKitEventLoop.confirmWindowClosed(windowId)
        }
    }

    internal fun releaseNative() {
        appKitReleaseWindowDelegateNative(
            released = released,
            address = ptr.address(),
            callbacks = routeCallbacks,
            releaseNative = { ObjCRuntime.msgSend(null, ptr, ObjCRuntime.sel("release")) },
        )
    }

    internal fun unregisterRoute() {
        unregisterDelegate(ptr.address(), routeCallbacks)
    }

    /** Kotlin callback for `windowDidMove:` — dispatches [WindowEvent.Moved]. */
    fun onWindowDidMove() {
        val nsWindow = NSWindow(nsWindowPtr)
        val scale = nsWindow.backingScaleFactor()
        // NSWindow.frame origin is in screen points (bottom-left). Reported in physical pixels;
        // exact top-left conversion isn't needed for consumers that just react to the move.
        val frame = sizedNSRect(nsWindow.frame())
        val x = (frame.getAtIndex(ValueLayout.JAVA_DOUBLE, 0) * scale).toInt()
        val y = (frame.getAtIndex(ValueLayout.JAVA_DOUBLE, 1) * scale).toInt()
        handler.windowEvent(eventLoop, windowId, WindowEvent.Moved(PhysicalPosition(x, y)))
    }

    /** Kotlin callback for `windowDidBecomeKey:` — dispatches [WindowEvent.Focused] (gained). */
    fun onWindowDidBecomeKey() {
        handler.windowEvent(eventLoop, windowId, WindowEvent.Focused(gained = true))
    }

    /** Kotlin callback for `windowDidResignKey:` — dispatches [WindowEvent.Focused] (lost). */
    fun onWindowDidResignKey() {
        handler.windowEvent(eventLoop, windowId, WindowEvent.Focused(gained = false))
    }

    /** Kotlin callback for `windowDidMiniaturize:` — dispatches [WindowEvent.Occluded] (true). */
    fun onWindowDidMiniaturize() {
        handler.windowEvent(eventLoop, windowId, WindowEvent.Occluded(true))
    }

    /** Kotlin callback for `windowDidDeminiaturize:` — dispatches [WindowEvent.Occluded] (false). */
    fun onWindowDidDeminiaturize() {
        handler.windowEvent(eventLoop, windowId, WindowEvent.Occluded(false))
    }

    /**
     * Kotlin callback for `windowDidResize:`.
     *
     * Computes the new physical size in pixels:
     *   physW = contentLayoutRect.width × backingScaleFactor
     *   physH = contentLayoutRect.height × backingScaleFactor
     *
     * Dispatches [WindowEvent.Resized] to the handler then updates
     * `CAMetalLayer.drawableSize` so the Metal surface follows the resize.
     *
     * Note: contentLayoutRect is read via ADDRESS layout introspection (MemorySegment
     * treated as pointer-or-sret depending on the platform) then reinterpret(32) to
     * access the four CGFloats {x, y, width, height}.
     */
    /**
     * Returns an NSRect [MemorySegment] guaranteed to be 32 bytes long so its four CGFloats
     * can be read.
     *
     * `contentLayoutRect()` goes through [ObjCRuntime.msgSendStret], which on struct returns
     * yields a **heap**-backed segment already sized to 32 bytes — calling `reinterpret` on it
     * throws `UnsupportedOperationException: Not a native segment`. Only a native segment with
     * an unknown (zero) size needs reinterpreting.
     */
    private fun sizedNSRect(rect: MemorySegment): MemorySegment =
        if (rect.byteSize() >= 32L) rect else rect.reinterpret(32)

    fun onWindowDidResize() {
        val nsWindow = NSWindow(nsWindowPtr)
        val scale = nsWindow.backingScaleFactor()

        // contentLayoutRect → NSRect (MemorySegment) → {x, y, width, height}
        // reinterpret(32) = 4 × 8 bytes to read the doubles
        val rect = sizedNSRect(nsWindow.contentLayoutRect())
        val w = rect.getAtIndex(ValueLayout.JAVA_DOUBLE, 2)
        val h = rect.getAtIndex(ValueLayout.JAVA_DOUBLE, 3)

        val physW = (w * scale).toInt()
        val physH = (h * scale).toInt()

        val newSize = PhysicalSize(physW, physH)
        handler.windowEvent(eventLoop, windowId, WindowEvent.Resized(newSize))

        // Update the CAMetalLayer drawableSize to follow the new size
        // CGSize = {width: Double, height: Double} passed by value (HFA ARM64)
        setMetalLayerDrawableSize(physW.toDouble(), physH.toDouble())
    }

    /**
     * Updates `CAMetalLayer.drawableSize` via a struct-typed ObjC call.
     *
     * CGSize (= {CGFloat, CGFloat}) is a 2-double HFA on ARM64 — it must be
     * passed by value via a [MemoryLayout.structLayout] so that Panama uses the
     * v0/v1 SIMD registers in accordance with the AArch64 ABI.
     */
    private fun setMetalLayerDrawableSize(width: Double, height: Double) {
        val cgSizeLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("width"),
            ValueLayout.JAVA_DOUBLE.withName("height"),
        )
        val arena = Arena.ofAuto()
        val cgSize = arena.allocate(cgSizeLayout)
        cgSize.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, width)
        cgSize.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, height)

        val linker = Linker.nativeLinker()
        val sel = ObjCRuntime.sel("setDrawableSize:")
        val desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,  // self (CAMetalLayer *)
            ValueLayout.ADDRESS,  // SEL
            cgSizeLayout,         // CGSize by value
        )
        val handle = linker.downcallHandle(ObjCRuntime.objcMsgSendAddr, desc)
        handle.invokeWithArguments(metalLayerPtr, sel, cgSize)
    }

    /**
     * Kotlin callback for `windowDidChangeBackingProperties:`.
     *
     * Triggered when the window is moved between a Retina screen and a standard screen.
     *
     * 1. Reads the new `backingScaleFactor`
     * 2. Updates `CAMetalLayer.contentsScale`
     * 3. Dispatches [WindowEvent.ScaleFactorChanged]
     * 4. Then dispatches [WindowEvent.Resized] because the drawableSize changes in pixels
     */
    fun onWindowDidChangeBackingProperties() {
        val nsWindow = NSWindow(nsWindowPtr)
        val newScale = nsWindow.backingScaleFactor()

        // 1. Update the CAMetalLayer contentsScale
        ObjCRuntime.msgSend(
            null,
            metalLayerPtr,
            ObjCRuntime.sel("setContentsScale:"),
            newScale,
        )

        // 2. Dispatch ScaleFactorChanged
        handler.windowEvent(eventLoop, windowId, WindowEvent.ScaleFactorChanged(newScale))

        // 3. Dispatch a subsequent Resized: the drawableSize in pixels changes with the scale
        val rect = sizedNSRect(nsWindow.contentLayoutRect())
        val w = rect.getAtIndex(ValueLayout.JAVA_DOUBLE, 2)
        val h = rect.getAtIndex(ValueLayout.JAVA_DOUBLE, 3)
        val physW = (w * newScale).toInt()
        val physH = (h * newScale).toInt()
        val newSize = PhysicalSize(physW, physH)
        handler.windowEvent(eventLoop, windowId, WindowEvent.Resized(newSize))
        setMetalLayerDrawableSize(physW.toDouble(), physH.toDouble())
    }

    companion object {
        /** Global table: ObjC memory address → associated Kotlin delegate. */
        private val delegateTable = ConcurrentHashMap<Long, AppKitWindowDelegateCallbacks>()

        internal fun registerDelegateRoute(address: Long, callbacks: AppKitWindowDelegateCallbacks) {
            delegateTable[address] = callbacks
        }

        internal fun unregisterDelegate(address: Long) {
            delegateTable.remove(address)
        }

        internal fun unregisterDelegate(address: Long, callbacks: AppKitWindowDelegateCallbacks) {
            delegateTable.remove(address, callbacks)
        }

        internal fun registeredDelegateCount(): Int = delegateTable.size

        @Volatile
        private var classRegistered: Boolean = false

        @Synchronized
        internal fun ensureClassRegistered() {
            if (classRegistered) return

            val arena = Arena.global()
            val linker: Linker = Linker.nativeLinker()
            val lookup = MethodHandles.lookup()

            val cls = ObjCSubclassing.allocateClass("NSObject", "KadreWindowDelegateNative")
            ObjCSubclassing.addProtocol(cls, "NSWindowDelegate")

            // Registers a `void method:(id notification)` delegate method (ObjC encoding "v@:@").
            fun addNotificationMethod(callbackName: String, selector: String) {
                val handle = lookup.findStatic(
                    Callbacks::class.java,
                    callbackName,
                    MethodType.methodType(
                        Void.TYPE,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                    ),
                )
                val stub = linker.upcallStub(
                    handle,
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                    arena,
                )
                ObjCSubclassing.addMethod(cls, selector, stub, "v@:@")
            }

            // BOOL windowShouldClose(id self, SEL _cmd, id sender)
            // Encoding: "c@:@" — BOOL is signed char (c) on macOS 64-bit ARM
            val windowShouldCloseHandle = lookup.findStatic(
                Callbacks::class.java,
                "windowShouldClose",
                MethodType.methodType(
                    java.lang.Byte.TYPE,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            )
            val windowShouldCloseStub = linker.upcallStub(
                windowShouldCloseHandle,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_BYTE,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                arena,
            )

            ObjCSubclassing.addMethod(
                cls,
                "windowShouldClose:",
                windowShouldCloseStub,
                "c@:@",
            )

            // void windowDidResize:(NSNotification *) — encoding "v@:@"
            val windowDidResizeHandle = lookup.findStatic(
                Callbacks::class.java,
                "windowDidResize",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java, // self
                    MemorySegment::class.java, // cmd
                    MemorySegment::class.java, // notification
                ),
            )
            val windowDidResizeStub = linker.upcallStub(
                windowDidResizeHandle,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                arena,
            )
            ObjCSubclassing.addMethod(
                cls,
                "windowDidResize:",
                windowDidResizeStub,
                "v@:@",
            )

            // void windowDidChangeBackingProperties:(NSNotification *) — encoding "v@:@"
            val windowDidChangeBackingPropertiesHandle = lookup.findStatic(
                Callbacks::class.java,
                "windowDidChangeBackingProperties",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java, // self
                    MemorySegment::class.java, // cmd
                    MemorySegment::class.java, // notification
                ),
            )
            val windowDidChangeBackingPropertiesStub = linker.upcallStub(
                windowDidChangeBackingPropertiesHandle,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                arena,
            )
            ObjCSubclassing.addMethod(
                cls,
                "windowDidChangeBackingProperties:",
                windowDidChangeBackingPropertiesStub,
                "v@:@",
            )

            // void windowWillClose:(NSNotification *) — encoding "v@:@"
            val windowWillCloseHandle = lookup.findStatic(
                Callbacks::class.java,
                "windowWillClose",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java, // self
                    MemorySegment::class.java, // cmd
                    MemorySegment::class.java, // notification
                ),
            )
            val windowWillCloseStub = linker.upcallStub(
                windowWillCloseHandle,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                arena,
            )
            ObjCSubclassing.addMethod(
                cls,
                "windowWillClose:",
                windowWillCloseStub,
                "v@:@",
            )

            // GRA: Moved + Focused events.
            addNotificationMethod("windowDidMove", "windowDidMove:")
            addNotificationMethod("windowDidBecomeKey", "windowDidBecomeKey:")
            addNotificationMethod("windowDidResignKey", "windowDidResignKey:")

            // Occluded events via miniaturize/deminiaturize.
            addNotificationMethod("windowDidMiniaturize", "windowDidMiniaturize:")
            addNotificationMethod("windowDidDeminiaturize", "windowDidDeminiaturize:")

            ObjCSubclassing.registerClass(cls)
            classRegistered = true
        }
    }

    /**
     * `@JvmStatic` trampolines invoked by the Panama upcall stubs.
     *
     * The static methods are essential: `Linker.upcallStub` cannot bind
     * instance methods because the ObjC `self` is passed as the first
     * argument, not via the Java receiver.
     */
    object Callbacks {
        @JvmStatic
        fun windowShouldClose(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") sender: MemorySegment,
        ): Byte {
            val callbacks = delegateTable[self.address()] ?: return 1
            return try {
                callbacks.onWindowShouldClose()
            } catch (failure: Throwable) {
                captureSafely(callbacks, "windowShouldClose", failure)
                1
            }
        }

        @JvmStatic
        fun windowDidResize(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowDidResize") { it.onWindowDidResize() }
        }

        @JvmStatic
        fun windowDidMove(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowDidMove") { it.onWindowDidMove() }
        }

        @JvmStatic
        fun windowDidBecomeKey(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowDidBecomeKey") { it.onWindowDidBecomeKey() }
        }

        @JvmStatic
        fun windowDidResignKey(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowDidResignKey") { it.onWindowDidResignKey() }
        }

        @JvmStatic
        fun windowDidChangeBackingProperties(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowDidChangeBackingProperties") {
                it.onWindowDidChangeBackingProperties()
            }
        }

        @JvmStatic
        fun windowWillClose(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowWillClose") { it.onWindowWillClose() }
        }

        @JvmStatic
        fun windowDidMiniaturize(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowDidMiniaturize") { it.onWindowDidMiniaturize() }
        }

        @JvmStatic
        fun windowDidDeminiaturize(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "windowDidDeminiaturize") { it.onWindowDidDeminiaturize() }
        }

        private inline fun invokeSafely(
            self: MemorySegment,
            context: String,
            callback: (AppKitWindowDelegateCallbacks) -> Unit,
        ) {
            val callbacks = delegateTable[self.address()] ?: return
            try {
                callback(callbacks)
            } catch (failure: Throwable) {
                captureSafely(callbacks, context, failure)
            }
        }

        private fun captureSafely(
            callbacks: AppKitWindowDelegateCallbacks,
            context: String,
            failure: Throwable,
        ) {
            try {
                callbacks.captureCallbackFailure(context, failure)
            } catch (_: Throwable) {
                // No Kotlin exception may cross an Objective-C upcall.
            }
        }
    }
}

internal fun appKitReleaseWindowDelegateNative(
    released: AtomicBoolean,
    address: Long,
    callbacks: AppKitWindowDelegateCallbacks,
    releaseNative: () -> Unit,
) {
    if (!released.compareAndSet(false, true)) return
    KadreWindowDelegate.unregisterDelegate(address, callbacks)
    releaseNative()
}

internal fun appKitReleaseFailedWindowDelegate(
    setDelegateFailure: Throwable,
    releaseDelegate: () -> Unit,
): Nothing {
    try {
        releaseDelegate()
    } catch (releaseFailure: Throwable) {
        if (releaseFailure !== setDelegateFailure) setDelegateFailure.addSuppressed(releaseFailure)
    }
    throw setDelegateFailure
}
