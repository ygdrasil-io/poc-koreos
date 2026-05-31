/**
 * CFRunLoopObserver for dispatching WindowEvent.RedrawRequested (GRA-134)
 * and calling ApplicationHandler.aboutToWait (GRA-135).
 *
 * Installed on kCFRunLoopBeforeWaiting: for each AppKitWindow with
 * needsRedraw=true, dispatches WindowEvent.RedrawRequested and resets the flag.
 * Then calls aboutToWait(eventLoop) — after all the RedrawRequested events.
 * Native coalescing: several requestRedraw() → a single event per iteration.
 *
 * GRA-136: following aboutToWait, applies the current [ControlFlow]:
 *  - [ControlFlow.Poll]: wakes up the run loop immediately (never waits).
 *  - [ControlFlow.WaitUntil]: schedules a one-shot CFRunLoopTimer.
 *  - [ControlFlow.Wait]: cancels any pending timer (indefinite blocking).
 */
package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.WindowEvent
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

/**
 * Installs a CFRunLoopObserver on the current run loop to:
 * 1. Dispatch WindowEvent.RedrawRequested (GRA-134) for the pending windows
 * 2. Call ApplicationHandler.aboutToWait(eventLoop) (GRA-135) — after the redraws
 * 3. Apply the current [ControlFlow] (GRA-136)
 *
 * Must be created and installed from the main thread, before app.run().
 */
internal class CFRunLoopRedrawObserver(
    private val handler: ApplicationHandler,
    private val eventLoop: ActiveEventLoop,
    private val windows: ConcurrentHashMap<Long, AppKitWindow>,
) {
    init {
        // Register this instance globally so the static callback can find it
        instance = this
    }

    // GRA-136 — handles/refs resolved in install() for applying ControlFlow.
    private var runLoopPtr: MemorySegment = MemorySegment.NULL
    private var cfRunLoopWakeUpHandle: MethodHandle? = null
    private var cfRunLoopTimerCreateHandle: MethodHandle? = null
    private var cfRunLoopAddTimerHandle: MethodHandle? = null
    private var cfRunLoopTimerInvalidateHandle: MethodHandle? = null
    private var kCFRunLoopCommonModes: MemorySegment = MemorySegment.NULL
    private var noopTimerCallout: MemorySegment = MemorySegment.NULL

    @Volatile
    private var currentTimer: MemorySegment? = null

    // Run-loop mode detection, used to render only while in AppKit's modal move/resize tracking
    // mode (the BeforeWaiting observer handles the default mode).
    private var cfRunLoopCopyCurrentModeHandle: MethodHandle? = null
    private var cfReleaseHandle: MethodHandle? = null
    private var kCFRunLoopDefaultMode: MemorySegment = MemorySegment.NULL

    /**
     * Called by the CFRunLoopObserver before each sleep of the run loop.
     *
     * Order guaranteed by the spec:
     * 1. Dispatch [WindowEvent.RedrawRequested] for each window with needsRedraw=true (GRA-134)
     * 2. Call [ApplicationHandler.aboutToWait] after all the redraws (GRA-135)
     * 3. Apply the current [ControlFlow] (GRA-136) — AFTER aboutToWait
     *    so that the handler may have updated it.
     *
     * Does not dispatch if [ActiveEventLoop.isExiting] — avoids spurious callbacks
     * between [ActiveEventLoop.exit] and the actual exit of the run loop.
     */
    fun onBeforeWaiting() {
        if (eventLoop.isExiting) return

        dispatchFrame()

        // ControlFlow handling — AFTER aboutToWait (GRA-136)
        when (val cf = eventLoop.controlFlow) {
            is ControlFlow.Poll -> {
                // Poll: never blocks — wakes up immediately.
                cancelScheduledTimer()
                cfRunLoopWakeUpHandle?.invokeExact(runLoopPtr)
            }
            is ControlFlow.WaitUntil -> {
                scheduleWakeUpAt(cf.instant)
            }
            is ControlFlow.Wait -> {
                // Indefinite blocking: no timer.
                cancelScheduledTimer()
            }
        }
    }

    /**
     * Dispatches one frame: [WindowEvent.RedrawRequested] for each window with needsRedraw=true,
     * then [ApplicationHandler.aboutToWait]. Called from the BeforeWaiting observer and, during
     * AppKit's modal move/resize loop, from the repeating tracking timer (see [onTrackingTick]).
     */
    private fun dispatchFrame() {
        windows.values.forEach { window ->
            if (window.needsRedraw) {
                window.needsRedraw = false
                handler.windowEvent(eventLoop, window.id, WindowEvent.RedrawRequested)
            }
        }
        handler.aboutToWait(eventLoop)
    }

    /**
     * Repeating-timer callback that keeps rendering alive during AppKit's modal move/resize loop.
     *
     * During that loop the run loop almost never reaches `kCFRunLoopBeforeWaiting` (measured:
     * 1–2 ticks over an ~800 ms gesture), so the BeforeWaiting observer can't drive frames — but
     * CFRunLoopTimers are still serviced. We render here only while in a tracking mode; in the
     * default mode the BeforeWaiting observer already handles it, so we no-op to avoid double work.
     */
    private fun onTrackingTick() {
        if (eventLoop.isExiting) return
        if (isInTrackingMode()) dispatchFrame()
    }

    private fun isInTrackingMode(): Boolean {
        val copyMode = cfRunLoopCopyCurrentModeHandle ?: return false
        val mode = copyMode.invokeExact(runLoopPtr) as MemorySegment
        val tracking = mode != MemorySegment.NULL && mode.address() != kCFRunLoopDefaultMode.address()
        if (mode != MemorySegment.NULL) cfReleaseHandle?.invokeExact(mode)
        return tracking
    }

    /**
     * Schedules a one-shot CFRunLoopTimer to wake up the run loop at [instant]
     * (milliseconds since the Unix epoch). Cancels any previous timer.
     *
     * CFAbsoluteTime is in seconds since January 1, 2001:
     * conversion `instant / 1000.0 - 978307200.0`.
     */
    private fun scheduleWakeUpAt(instant: Long) {
        cancelScheduledTimer()
        val createHandle = cfRunLoopTimerCreateHandle ?: return
        val addHandle = cfRunLoopAddTimerHandle ?: return

        val cfFireTime: Double = (instant / 1000.0) - 978_307_200.0

        val timer = createHandle.invoke(
            MemorySegment.NULL, // allocator
            cfFireTime,          // fireDate
            0.0,                 // interval (0 = one-shot)
            0L,                  // flags
            0L,                  // order
            noopTimerCallout,    // callout
            MemorySegment.NULL,  // context
        ) as MemorySegment

        addHandle.invokeExact(runLoopPtr, timer, kCFRunLoopCommonModes)
        currentTimer = timer
    }

    /**
     * Invalidates any pending CFRunLoopTimer.
     */
    private fun cancelScheduledTimer() {
        val timer = currentTimer ?: return
        currentTimer = null
        cfRunLoopTimerInvalidateHandle?.invokeExact(timer)
    }

    companion object {
        /** Singleton instance — a single observer per application. */
        @Volatile
        private var instance: CFRunLoopRedrawObserver? = null

        /** kCFRunLoopBeforeWaiting activity bit */
        private const val kCFRunLoopBeforeWaiting = 0x20L

        /**
         * Creates and installs the observer on the current run loop.
         * Returns the observer (retained in Arena.global()).
         */
        fun install(
            handler: ApplicationHandler,
            eventLoop: ActiveEventLoop,
            windows: ConcurrentHashMap<Long, AppKitWindow>,
        ): CFRunLoopRedrawObserver {
            val observer = CFRunLoopRedrawObserver(handler, eventLoop, windows)

            val arena = Arena.global()
            val linker = Linker.nativeLinker()
            val lookup = MethodHandles.lookup()

            // Load CoreFoundation
            val cfLib: SymbolLookup = SymbolLookup.loaderLookup().let { loader ->
                if (loader.find("CFRunLoopGetCurrent").isPresent) loader
                else SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena
                )
            }

            // Resolve CF functions
            val cfRunLoopGetCurrent = cfLib.find("CFRunLoopGetCurrent").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopGetCurrent not found")
            }
            val cfRunLoopObserverCreate = cfLib.find("CFRunLoopObserverCreate").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopObserverCreate not found")
            }
            val cfRunLoopAddObserver = cfLib.find("CFRunLoopAddObserver").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopAddObserver not found")
            }

            // kCFRunLoopCommonModes global constant (CFStringRef)
            val kCFRunLoopCommonModesPtr = cfLib.find("kCFRunLoopCommonModes").orElseThrow {
                UnsatisfiedLinkError("kCFRunLoopCommonModes not found")
            }
            // Dereference: the symbol IS a pointer to the CFStringRef, not the CFStringRef itself
            val kCFRunLoopCommonModes: MemorySegment = kCFRunLoopCommonModesPtr
                .reinterpret(8L)
                .get(ValueLayout.ADDRESS, 0L)

            // kCFRunLoopDefaultMode + CFRunLoopCopyCurrentMode + CFRelease, to detect when the loop
            // runs in AppKit's modal move/resize tracking mode (see the repeating timer below).
            val kCFRunLoopDefaultMode: MemorySegment = cfLib.find("kCFRunLoopDefaultMode")
                .map { it.reinterpret(8L).get(ValueLayout.ADDRESS, 0L) }
                .orElse(MemorySegment.NULL)
            val copyCurrentModeHandle: MethodHandle? = cfLib.find("CFRunLoopCopyCurrentMode")
                .map { linker.downcallHandle(it, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)) }
                .orElse(null)
            val cfReleaseHandle: MethodHandle? = cfLib.find("CFRelease")
                .map { linker.downcallHandle(it, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)) }
                .orElse(null)

            // Upcall stub: void callback(CFRunLoopObserverRef, CFRunLoopActivity, void*)
            val callbackHandle = lookup.findStatic(
                CFRunLoopRedrawObserver::class.java,
                "redrawCallback",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java, // observer
                    Long::class.javaPrimitiveType, // activity (CFRunLoopActivity = CFOptionFlags = long)
                    MemorySegment::class.java, // info
                ),
            )
            val callbackStub = linker.upcallStub(
                callbackHandle,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,  // observer
                    ValueLayout.JAVA_LONG, // activity
                    ValueLayout.ADDRESS,  // info
                ),
                arena,
            )

            // CFRunLoopGetCurrent()
            val getCurrentHandle = linker.downcallHandle(
                cfRunLoopGetCurrent,
                FunctionDescriptor.of(ValueLayout.ADDRESS),
            )
            val runLoop = getCurrentHandle.invokeExact() as MemorySegment

            // CFRunLoopObserverCreate(allocator, activities, repeats, order, callout, context)
            val createHandle = linker.downcallHandle(
                cfRunLoopObserverCreate,
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS,   // return: CFRunLoopObserverRef
                    ValueLayout.ADDRESS,   // allocator (NULL = kCFAllocatorDefault)
                    ValueLayout.JAVA_LONG, // activities (CFOptionFlags)
                    ValueLayout.JAVA_BYTE, // repeats (Boolean = unsigned char)
                    ValueLayout.JAVA_LONG, // order (CFIndex)
                    ValueLayout.ADDRESS,   // callout
                    ValueLayout.ADDRESS,   // context (NULL)
                ),
            )
            val observerRef = createHandle.invoke(
                MemorySegment.NULL,          // allocator
                kCFRunLoopBeforeWaiting,     // activities
                1.toByte(),                  // repeats = true
                0L,                          // order
                callbackStub,                // callout
                MemorySegment.NULL,          // context
            ) as MemorySegment

            // CFRunLoopAddObserver(runLoop, observer, kCFRunLoopCommonModes)
            val addHandle = linker.downcallHandle(
                cfRunLoopAddObserver,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // rl
                    ValueLayout.ADDRESS, // rlo
                    ValueLayout.ADDRESS, // mode
                ),
            )
            addHandle.invokeExact(runLoop, observerRef, kCFRunLoopCommonModes)

            // -------- GRA-136 — CFRunLoopWakeUp / CFRunLoopTimer setup --------

            val wakeUpSym = cfLib.find("CFRunLoopWakeUp").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopWakeUp not found")
            }
            val wakeUpHandle = linker.downcallHandle(
                wakeUpSym,
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
            )

            val timerCreateSym = cfLib.find("CFRunLoopTimerCreate").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopTimerCreate not found")
            }
            val timerCreateHandle = linker.downcallHandle(
                timerCreateSym,
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS,      // return: CFRunLoopTimerRef
                    ValueLayout.ADDRESS,      // allocator
                    ValueLayout.JAVA_DOUBLE,  // fireDate (CFAbsoluteTime)
                    ValueLayout.JAVA_DOUBLE,  // interval (CFTimeInterval)
                    ValueLayout.JAVA_LONG,    // flags (CFOptionFlags)
                    ValueLayout.JAVA_LONG,    // order (CFIndex)
                    ValueLayout.ADDRESS,      // callout
                    ValueLayout.ADDRESS,      // context
                ),
            )

            val addTimerSym = cfLib.find("CFRunLoopAddTimer").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopAddTimer not found")
            }
            val addTimerHandle = linker.downcallHandle(
                addTimerSym,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // rl
                    ValueLayout.ADDRESS, // timer
                    ValueLayout.ADDRESS, // mode
                ),
            )

            val invalidateSym = cfLib.find("CFRunLoopTimerInvalidate").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopTimerInvalidate not found")
            }
            val invalidateHandle = linker.downcallHandle(
                invalidateSym,
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
            )

            // No-op timer callback — the presence of the timer is enough to wake up the run loop;
            // the kCFRunLoopBeforeWaiting observer will do all the work.
            val noopHandle = lookup.findStatic(
                CFRunLoopRedrawObserver::class.java,
                "noopTimerCallback",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java, // timer
                    MemorySegment::class.java, // info
                ),
            )
            val noopStub = linker.upcallStub(
                noopHandle,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // timer
                    ValueLayout.ADDRESS, // info
                ),
                arena,
            )

            observer.runLoopPtr = runLoop
            observer.cfRunLoopWakeUpHandle = wakeUpHandle
            observer.cfRunLoopTimerCreateHandle = timerCreateHandle
            observer.cfRunLoopAddTimerHandle = addTimerHandle
            observer.cfRunLoopTimerInvalidateHandle = invalidateHandle
            observer.kCFRunLoopCommonModes = kCFRunLoopCommonModes
            observer.noopTimerCallout = noopStub
            observer.cfRunLoopCopyCurrentModeHandle = copyCurrentModeHandle
            observer.cfReleaseHandle = cfReleaseHandle
            observer.kCFRunLoopDefaultMode = kCFRunLoopDefaultMode

            // Repeating ~60 fps timer that keeps rendering alive during AppKit's modal move/resize
            // loop (where the BeforeWaiting observer barely fires). Added to common modes so it is
            // serviced in the tracking mode; its callback renders only while actually tracking.
            val trackingTickHandle = lookup.findStatic(
                CFRunLoopRedrawObserver::class.java,
                "trackingTickCallback",
                MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java),
            )
            val trackingTickStub = linker.upcallStub(
                trackingTickHandle,
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                arena,
            )
            val trackingTimer = timerCreateHandle.invoke(
                MemorySegment.NULL, // allocator
                0.0,                // fireDate (past → fire ASAP, then repeat)
                0.016,              // interval (~60 fps)
                0L,                 // flags
                0L,                 // order
                trackingTickStub,   // callout
                MemorySegment.NULL, // context
            ) as MemorySegment
            addTimerHandle.invokeExact(runLoop, trackingTimer, kCFRunLoopCommonModes)

            return observer
        }

        /** Static trampoline invoked by the upcall stub from CoreFoundation. */
        @JvmStatic
        fun redrawCallback(
            @Suppress("UNUSED_PARAMETER") observerRef: MemorySegment,
            @Suppress("UNUSED_PARAMETER") activity: Long,
            @Suppress("UNUSED_PARAMETER") info: MemorySegment,
        ) {
            instance?.onBeforeWaiting()
        }

        /**
         * No-op callback for CFRunLoopTimer (GRA-136). The timer's only purpose
         * is to trigger a wake-up of the run loop; the
         * `kCFRunLoopBeforeWaiting` observer then does all the work.
         */
        @JvmStatic
        fun noopTimerCallback(
            @Suppress("UNUSED_PARAMETER") timer: MemorySegment,
            @Suppress("UNUSED_PARAMETER") info: MemorySegment,
        ) {
            // intentionally empty
        }

        /** Repeating-timer trampoline that drives frames during AppKit's modal tracking loop. */
        @JvmStatic
        fun trackingTickCallback(
            @Suppress("UNUSED_PARAMETER") timer: MemorySegment,
            @Suppress("UNUSED_PARAMETER") info: MemorySegment,
        ) {
            instance?.onTrackingTick()
        }
    }
}
