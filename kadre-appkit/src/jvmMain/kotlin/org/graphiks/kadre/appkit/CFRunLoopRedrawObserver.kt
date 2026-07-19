package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Injectable Core Foundation operations owned by [CFRunLoopOwner]. */
internal interface CFRunLoopApi : AutoCloseable {
    fun createObserver(activities: Long): Long

    fun addObserver(observer: Long)

    fun removeObserver(observer: Long)

    fun invalidateObserver(observer: Long)

    fun createTimer(deadlineEpochMillis: Long): Long

    fun addTimer(timer: Long)

    fun invalidateTimer(timer: Long)

    fun removeTimer(timer: Long)

    fun wakeUp()

    fun release(ref: Long)
}

/**
 * Owns the AppKit event-loop observer, its timers, callback routes, and callback arena.
 *
 * Native callbacks are trampolined through the companion router. Every trampoline
 * captures failures so no Kotlin exception can cross a Core Foundation upcall.
 */
internal class CFRunLoopOwner private constructor(
    private val api: CFRunLoopApi,
    private val state: AppKitLoopState,
    private val onAfterWaiting: (StartCause) -> Unit,
    private val onBeforeWaiting: () -> ControlFlow,
    private val observer: Long,
) : AutoCloseable {
    private data class ArmedTimer(
        val ref: Long,
        val generation: Long,
    )

    private val lock = Any()
    private val callbackFailures = ConcurrentLinkedQueue<Throwable>()
    private var currentTimer: ArmedTimer? = null
    private var closed = false

    /** Marks an external event before waking Core Foundation. */
    fun wakeUp() {
        synchronized(lock) {
            if (closed) return
            state.signalExternalEvent()
            api.wakeUp()
        }
    }

    /** Consumes the startup iteration already delivered by [AppKitEventLoop.didLaunch]. */
    fun consumeLaunchIteration() {
        synchronized(lock) {
            check(!closed) { "AppKit run-loop owner is closed" }
            check(state.beginIteration() === StartCause.Init) {
                "AppKit launch must consume the initial run-loop iteration"
            }
        }
    }

    fun closeWindow(windowId: org.graphiks.kadre.core.WindowId) {
        synchronized(lock) {
            if (closed) return
            state.closeWindow(windowId)
        }
    }

    /** Kotlin-safe boundary for failures captured by native callbacks. */
    fun throwPendingCallbackFailure() {
        val primary = drainPendingCallbackFailure() ?: return
        throw primary
    }

    /** Preserves a native-loop failure while retaining queued callback context. */
    fun suppressPendingCallbackFailureOnto(primary: Throwable) {
        val callbackFailure = drainPendingCallbackFailure() ?: return
        if (callbackFailure !== primary) primary.addSuppressed(callbackFailure)
    }

    private fun drainPendingCallbackFailure(): Throwable? {
        val primary = callbackFailures.poll() ?: return null
        while (true) {
            val additional = callbackFailures.poll() ?: break
            if (additional !== primary) primary.addSuppressed(additional)
        }
        return primary
    }

    override fun close() {
        val timerToRelease: ArmedTimer?
        synchronized(lock) {
            if (closed) return
            closed = true
            timerToRelease = currentTimer
            currentTimer = null
            timerToRelease?.let { timerRoutes.remove(it.ref, TimerRoute(this, it.generation)) }
            observerRoutes.remove(observer, this)
        }

        var failure: Throwable? = null
        timerToRelease?.let { timer ->
            failure = cleanupStep(failure) { api.invalidateTimer(timer.ref) }
            failure = cleanupStep(failure) { api.removeTimer(timer.ref) }
            failure = cleanupStep(failure) { api.release(timer.ref) }
        }
        val removeObserverFailure = runCatching { api.removeObserver(observer) }.exceptionOrNull()
        if (removeObserverFailure != null) {
            failure = cleanupStep(failure) { throw removeObserverFailure }
            failure = cleanupStep(failure) { api.invalidateObserver(observer) }
        }
        failure = cleanupStep(failure) { api.release(observer) }
        failure = cleanupStep(failure) { api.close() }
        failure?.let { throw it }
    }

    private fun observerCallback(activity: Long) {
        if (activity and AFTER_WAITING != 0L) {
            val cause = synchronized(lock) {
                if (closed) return
                state.classifyWake(currentTimer?.generation)
                state.beginIteration()
            }
            onAfterWaiting(cause)
        }

        if (activity and BEFORE_WAITING != 0L) {
            synchronized(lock) {
                if (closed) return
            }
            val controlFlow = onBeforeWaiting()
            synchronized(lock) {
                if (closed) return
                applyTimerDecision(state.arm(controlFlow))
            }
        }
    }

    private fun timerCallback(ref: Long, generation: Long) {
        synchronized(lock) {
            if (closed) return
            val timer = currentTimer?.takeIf {
                it.ref == ref && it.generation == generation
            } ?: return
            currentTimer = null
            timerRoutes.remove(ref, TimerRoute(this, generation))
            releaseTimer(timer)?.let { throw it }
        }
    }

    private fun applyTimerDecision(decision: TimerDecision) {
        cancelCurrentTimer()
        when (decision) {
            TimerDecision.Cancel -> Unit
            TimerDecision.FireNow -> api.wakeUp()
            is TimerDecision.Arm -> {
                val timerRef = api.createTimer(decision.deadline)
                check(timerRef != 0L) { "CFRunLoopTimerCreate returned NULL" }
                val timer = ArmedTimer(timerRef, decision.generation)
                timerRoutes[timerRef] = TimerRoute(this, decision.generation)
                try {
                    api.addTimer(timerRef)
                    currentTimer = timer
                } catch (failure: Throwable) {
                    timerRoutes.remove(timerRef, TimerRoute(this, decision.generation))
                    var primary = failure
                    primary = cleanupStep(primary) { api.invalidateTimer(timerRef) }!!
                    primary = cleanupStep(primary) { api.removeTimer(timerRef) }!!
                    primary = cleanupStep(primary) { api.release(timerRef) }!!
                    throw primary
                }
            }
        }
    }

    private fun cancelCurrentTimer() {
        val timer = currentTimer ?: return
        currentTimer = null
        timerRoutes.remove(timer.ref, TimerRoute(this, timer.generation))
        releaseTimer(timer)?.let { throw it }
    }

    private fun releaseTimer(timer: ArmedTimer): Throwable? {
        var failure: Throwable? = null
        failure = cleanupStep(failure) { api.invalidateTimer(timer.ref) }
        failure = cleanupStep(failure) { api.removeTimer(timer.ref) }
        failure = cleanupStep(failure) { api.release(timer.ref) }
        return failure
    }

    private fun recordCallbackFailure(failure: Throwable) {
        callbackFailures.add(failure)
        try {
            api.wakeUp()
        } catch (wakeFailure: Throwable) {
            if (wakeFailure !== failure) failure.addSuppressed(wakeFailure)
        }
    }

    private data class TimerRoute(
        val owner: CFRunLoopOwner,
        val generation: Long,
    )

    companion object {
        internal const val BEFORE_WAITING = 0x20L
        internal const val AFTER_WAITING = 0x40L
        internal const val OBSERVED_ACTIVITIES = BEFORE_WAITING or AFTER_WAITING

        private val observerRoutes = ConcurrentHashMap<Long, CFRunLoopOwner>()
        private val timerRoutes = ConcurrentHashMap<Long, TimerRoute>()

        internal fun registeredObserverCount(): Int = observerRoutes.size

        internal fun registeredTimerCount(): Int = timerRoutes.size

        fun install(
            api: CFRunLoopApi,
            state: AppKitLoopState,
            onAfterWaiting: (StartCause) -> Unit,
            onBeforeWaiting: () -> ControlFlow,
        ): CFRunLoopOwner {
            var observer: Long? = null
            var owner: CFRunLoopOwner? = null
            try {
                val createdObserver = api.createObserver(OBSERVED_ACTIVITIES)
                check(createdObserver != 0L) { "CFRunLoopObserverCreate returned NULL" }
                observer = createdObserver
                val installedOwner = CFRunLoopOwner(
                    api,
                    state,
                    onAfterWaiting,
                    onBeforeWaiting,
                    createdObserver,
                )
                owner = installedOwner
                observerRoutes[createdObserver] = installedOwner
                api.addObserver(createdObserver)
                return installedOwner
            } catch (failure: Throwable) {
                var primary = failure
                observer?.let { createdObserver ->
                    owner?.let { observerRoutes.remove(createdObserver, it) }
                    primary = cleanupStep(primary) {
                        api.invalidateObserver(createdObserver)
                    }!!
                    primary = cleanupStep(primary) { api.removeObserver(createdObserver) }!!
                    primary = cleanupStep(primary) { api.release(createdObserver) }!!
                }
                primary = cleanupStep(primary) { api.close() }!!
                throw primary
            }
        }

        fun install(
            handler: ApplicationHandler,
            eventLoop: ActiveEventLoop,
            windows: ConcurrentHashMap<Long, AppKitWindow>,
        ): CFRunLoopOwner {
            val state = AppKitLoopState(System::currentTimeMillis)
            return install(
                api = NativeCFRunLoopApi.create(),
                state = state,
                onAfterWaiting = { cause ->
                    (eventLoop as? AppKitEventLoop)?.drainDeferredNativeCallbackCleanup()
                    if (!eventLoop.isExiting) handler.newEvents(eventLoop, cause)
                },
                onBeforeWaiting = {
                    if (eventLoop.isExiting) {
                        ControlFlow.Wait
                    } else {
                        windows.values.forEach { window ->
                            if (window.needsRedraw) state.requestRedraw(window.id)
                        }
                        state.takeRedraws().forEach { windowId ->
                            windows[windowId.value]?.let { window ->
                                window.needsRedraw = false
                                handler.windowEvent(
                                    eventLoop,
                                    windowId,
                                    WindowEvent.RedrawRequested,
                                )
                            }
                        }
                        handler.aboutToWait(eventLoop)
                        eventLoop.controlFlow
                    }
                },
            )
        }

        /** Called only by the native adapter or deterministic tests. */
        @JvmStatic
        internal fun dispatchObserverCallback(observer: Long, activity: Long) {
            val owner = observerRoutes[observer] ?: return
            try {
                owner.observerCallback(activity)
            } catch (failure: Throwable) {
                try {
                    owner.recordCallbackFailure(failure)
                } catch (_: Throwable) {
                    // Never let an exception escape a Core Foundation upcall.
                }
            }
        }

        /** Called only by the native adapter or deterministic tests. */
        @JvmStatic
        internal fun dispatchTimerCallback(timer: Long) {
            val route = timerRoutes[timer] ?: return
            try {
                route.owner.timerCallback(timer, route.generation)
            } catch (failure: Throwable) {
                try {
                    route.owner.recordCallbackFailure(failure)
                } catch (_: Throwable) {
                    // Never let an exception escape a Core Foundation upcall.
                }
            }
        }

        private fun cleanupStep(primary: Throwable?, step: () -> Unit): Throwable? =
            try {
                step()
                primary
            } catch (failure: Throwable) {
                if (primary == null) failure else primary.also {
                    if (failure !== it) it.addSuppressed(failure)
                }
            }
    }
}

/** Core Foundation FFM adapter; owns every upcall stub in one shared arena. */
internal class NativeCFRunLoopApi private constructor(
    private val arena: Arena,
    private val runLoop: MemorySegment,
    private val commonModes: MemorySegment,
    private val observerCallbackStub: MemorySegment,
    private val timerCallbackStub: MemorySegment,
    private val observerCreate: MethodHandle,
    private val addObserver: MethodHandle,
    private val removeObserver: MethodHandle,
    private val invalidateObserver: MethodHandle,
    private val timerCreate: MethodHandle,
    private val addTimer: MethodHandle,
    private val removeTimer: MethodHandle,
    private val invalidateTimer: MethodHandle,
    private val wakeUp: MethodHandle,
    private val release: MethodHandle,
) : CFRunLoopApi {
    private val closed = AtomicBoolean(false)

    override fun createObserver(activities: Long): Long =
        (observerCreate.invoke(
            MemorySegment.NULL,
            activities,
            1.toByte(),
            0L,
            observerCallbackStub,
            MemorySegment.NULL,
        ) as MemorySegment).address()

    override fun addObserver(observer: Long) {
        addObserver.invokeExact(runLoop, MemorySegment.ofAddress(observer), commonModes)
    }

    override fun removeObserver(observer: Long) {
        removeObserver.invokeExact(runLoop, MemorySegment.ofAddress(observer), commonModes)
    }

    override fun invalidateObserver(observer: Long) {
        invalidateObserver.invokeExact(MemorySegment.ofAddress(observer))
    }

    override fun createTimer(deadlineEpochMillis: Long): Long {
        val cfAbsoluteTime = deadlineEpochMillis / 1_000.0 - CF_ABSOLUTE_TIME_UNIX_OFFSET
        return (timerCreate.invoke(
            MemorySegment.NULL,
            cfAbsoluteTime,
            0.0,
            0L,
            0L,
            timerCallbackStub,
            MemorySegment.NULL,
        ) as MemorySegment).address()
    }

    override fun addTimer(timer: Long) {
        addTimer.invokeExact(runLoop, MemorySegment.ofAddress(timer), commonModes)
    }

    override fun invalidateTimer(timer: Long) {
        invalidateTimer.invokeExact(MemorySegment.ofAddress(timer))
    }

    override fun removeTimer(timer: Long) {
        removeTimer.invokeExact(runLoop, MemorySegment.ofAddress(timer), commonModes)
    }

    override fun wakeUp() {
        wakeUp.invokeExact(runLoop)
    }

    override fun release(ref: Long) {
        release.invokeExact(MemorySegment.ofAddress(ref))
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) arena.close()
    }

    companion object {
        private const val CF_ABSOLUTE_TIME_UNIX_OFFSET = 978_307_200.0
        private const val CORE_FOUNDATION =
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"

        fun create(arenaFactory: () -> Arena = Arena::ofShared): NativeCFRunLoopApi {
            val arena = arenaFactory()
            try {
                val linker = Linker.nativeLinker()
                val symbols = SymbolLookup.loaderLookup().let { loader ->
                    if (loader.find("CFRunLoopGetCurrent").isPresent) loader
                    else SymbolLookup.libraryLookup(CORE_FOUNDATION, arena)
                }
                fun symbol(name: String): MemorySegment = symbols.find(name).orElseThrow {
                    UnsatisfiedLinkError("$name not found")
                }
                fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
                    linker.downcallHandle(symbol(name), descriptor)

                val lookup = MethodHandles.privateLookupIn(
                    NativeCFRunLoopApi::class.java,
                    MethodHandles.lookup(),
                )
                val observerCallback = lookup.findStatic(
                    NativeCFRunLoopApi::class.java,
                    "nativeObserverCallback",
                    MethodType.methodType(
                        Void.TYPE,
                        MemorySegment::class.java,
                        Long::class.javaPrimitiveType,
                        MemorySegment::class.java,
                    ),
                )
                val observerStub = linker.upcallStub(
                    observerCallback,
                    FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                    ),
                    arena,
                )
                val timerCallback = lookup.findStatic(
                    NativeCFRunLoopApi::class.java,
                    "nativeTimerCallback",
                    MethodType.methodType(
                        Void.TYPE,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                    ),
                )
                val timerStub = linker.upcallStub(
                    timerCallback,
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                    arena,
                )
                val getCurrent = downcall(
                    "CFRunLoopGetCurrent",
                    FunctionDescriptor.of(ValueLayout.ADDRESS),
                )
                val runLoop = getCurrent.invokeExact() as MemorySegment
                val commonModes = symbol("kCFRunLoopCommonModes")
                    .reinterpret(ValueLayout.ADDRESS.byteSize())
                    .get(ValueLayout.ADDRESS, 0L)

                return NativeCFRunLoopApi(
                    arena = arena,
                    runLoop = runLoop,
                    commonModes = commonModes,
                    observerCallbackStub = observerStub,
                    timerCallbackStub = timerStub,
                    observerCreate = downcall(
                        "CFRunLoopObserverCreate",
                        FunctionDescriptor.of(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_BYTE,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ),
                    addObserver = downcall(
                        "CFRunLoopAddObserver",
                        FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ),
                    removeObserver = downcall(
                        "CFRunLoopRemoveObserver",
                        FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ),
                    invalidateObserver = downcall(
                        "CFRunLoopObserverInvalidate",
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
                    ),
                    timerCreate = downcall(
                        "CFRunLoopTimerCreate",
                        FunctionDescriptor.of(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_DOUBLE,
                            ValueLayout.JAVA_DOUBLE,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ),
                    addTimer = downcall(
                        "CFRunLoopAddTimer",
                        FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ),
                    removeTimer = downcall(
                        "CFRunLoopRemoveTimer",
                        FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ),
                    invalidateTimer = downcall(
                        "CFRunLoopTimerInvalidate",
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
                    ),
                    wakeUp = downcall(
                        "CFRunLoopWakeUp",
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
                    ),
                    release = downcall(
                        "CFRelease",
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
                    ),
                )
            } catch (failure: Throwable) {
                runCatching { arena.close() }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        }

        @JvmStatic
        private fun nativeObserverCallback(
            observer: MemorySegment,
            activity: Long,
            @Suppress("UNUSED_PARAMETER") info: MemorySegment,
        ) {
            try {
                CFRunLoopOwner.dispatchObserverCallback(observer.address(), activity)
            } catch (_: Throwable) {
                // Last-resort guard: no exception may cross the upcall boundary.
            }
        }

        @JvmStatic
        private fun nativeTimerCallback(
            timer: MemorySegment,
            @Suppress("UNUSED_PARAMETER") info: MemorySegment,
        ) {
            try {
                CFRunLoopOwner.dispatchTimerCallback(timer.address())
            } catch (_: Throwable) {
                // Last-resort guard: no exception may cross the upcall boundary.
            }
        }
    }
}
