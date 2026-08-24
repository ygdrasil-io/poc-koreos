package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.CFRunLoopGetMain
import org.graphiks.kffi.objc.CFRunLoopStop
import org.graphiks.kffi.objc.CFRunLoopWakeUp
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSThread
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.performSelectorOnMainThread_withObject_waitUntilDone
import org.graphiks.kffi.objc.postEvent_atStart
import java.lang.foreign.GroupLayout
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.CompletableFuture

internal sealed interface AppKitStopResult {
    data object Accepted : AppKitStopResult

    data class Failed(val cause: Throwable) : AppKitStopResult
}

internal fun interface AppKitStopRequest {
    fun await(): AppKitStopResult
}

internal interface AppKitNativeApplication {
    fun isMainThread(): Boolean

    // Use AppKit's own state when synchronizing with run(); Kadre assigning the application
    // reference only proves setup has started, not that the native loop is pumping events.
    fun isRunning(): Boolean

    fun run()

    fun requestStop(): AppKitStopRequest

    fun emergencyStop()
}

internal class KffiAppKitNativeApplication : AppKitNativeApplication {
    private val lock = Any()
    private var application: NSApplication? = null
    private var stopRequested = false
    private var stopScheduled = false
    private var stopCompletion: CompletableFuture<AppKitStopResult>? = null

    override fun isMainThread(): Boolean = NSThread.isMainThread()

    override fun isRunning(): Boolean = synchronized(lock) { application?.isRunning() == true }

    override fun run() {
        check(isMainThread()) { "the AppKit event loop must run on the process main thread" }
        val current = ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
        }
        val pendingStop = synchronized(lock) {
            check(application == null) { "the AppKit event loop is already running" }
            application = current
            takeStopTarget()
        }

        val pendingStopThread = pendingStop?.let { target ->
            Thread.ofPlatform()
                .name("kadre-appkit-pending-stop")
                .start { completeStop(target) }
        }
        try {
            current.run()
            pendingStopThread?.join()
        } finally {
            synchronized(lock) {
                if (application === current) {
                    application = null
                    stopRequested = false
                    stopScheduled = false
                    stopCompletion = null
                }
            }
        }
    }

    override fun requestStop(): AppKitStopRequest {
        val request: AppKitStopRequest
        val target = synchronized(lock) {
            stopRequested = true
            // Concurrent callers share one attempt so every waiter observes the same native
            // acceptance or failure instead of racing independent stop operations.
            val completion = stopCompletion ?: CompletableFuture<AppKitStopResult>().also {
                stopCompletion = it
            }
            request = AppKitStopRequest { completion.get() }
            takeStopTarget()
        }
        target?.let(::completeStop)
        return request
    }

    override fun emergencyStop() {
        // This bypasses NSApplication.stop: and the synthetic event path; it is only used when
        // that normal path failed and can no longer be trusted to release run().
        val mainRunLoop = CFRunLoopGetMain()
        CFRunLoopStop(mainRunLoop)
        CFRunLoopWakeUp(mainRunLoop)
    }

    private fun takeStopTarget(): NSApplication? {
        val current = application
        return if (stopRequested && !stopScheduled && current != null) {
            stopScheduled = true
            current
        } else {
            null
        }
    }

    private fun completeStop(target: NSApplication) {
        val completion = checkNotNull(synchronized(lock) { stopCompletion })
        val result = try {
            target.scheduleStop()
            AppKitStopResult.Accepted
        } catch (cause: Exception) {
            failedStop(target, completion, cause)
        } catch (cause: LinkageError) {
            failedStop(target, completion, cause)
        }
        completion.complete(result)
    }

    private fun failedStop(
        target: NSApplication,
        completion: CompletableFuture<AppKitStopResult>,
        cause: Throwable,
    ): AppKitStopResult.Failed {
        try {
            emergencyStop()
        } catch (fallbackFailure: Exception) {
            cause.addSuppressed(fallbackFailure)
        } catch (fallbackFailure: LinkageError) {
            cause.addSuppressed(fallbackFailure)
        }
        synchronized(lock) {
            if (application === target && stopCompletion === completion) {
                // Release only this failed reservation so a later caller can retry without
                // clearing a newer stop request installed by another thread.
                stopScheduled = false
                stopCompletion = null
            }
        }
        return AppKitStopResult.Failed(cause)
    }

    private fun NSApplication.scheduleStop() {
        performSelectorOnMainThread_withObject_waitUntilDone(
            ObjCRuntime.sel("stop:"),
            ptr,
            true,
        )
        // stop: only changes AppKit's run state. A synthetic event wakes a windowless loop so
        // run() gets another iteration in which it can observe that state and return.
        ObjCRuntime.autoreleasePool {
            postEvent_atStart(createWakeEvent(), false)
        }
    }

    private fun createWakeEvent(): MemorySegment =
        ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            ObjCRuntime.getClass("NSEvent"),
            ObjCRuntime.sel(
                "otherEventWithType:location:modifierFlags:timestamp:" +
                    "windowNumber:context:subtype:data1:data2:",
            ),
            APPLICATION_DEFINED_EVENT_TYPE,
            ObjCRuntime.ObjCStructArg(ZERO_POINT, POINT_LAYOUT),
            NO_MODIFIER_FLAGS,
            0.0,
            0L,
            MemorySegment.NULL,
            0.toShort(),
            0L,
            0L,
        ) as MemorySegment

    private companion object {
        const val APPLICATION_DEFINED_EVENT_TYPE: Long = 15L
        const val NO_MODIFIER_FLAGS: Long = 0L
        val POINT_LAYOUT: GroupLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("x"),
            ValueLayout.JAVA_DOUBLE.withName("y"),
        )
        val ZERO_POINT: MemorySegment = MemorySegment.ofArray(doubleArrayOf(0.0, 0.0))
    }
}
