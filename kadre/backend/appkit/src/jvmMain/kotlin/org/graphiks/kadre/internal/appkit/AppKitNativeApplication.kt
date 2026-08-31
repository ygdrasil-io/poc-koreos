package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.CFRunLoopGetMain
import org.graphiks.kffi.objc.CFRunLoopStop
import org.graphiks.kffi.objc.CFRunLoopWakeUp
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSEvent
import org.graphiks.kffi.objc.NSEventModifierFlags
import org.graphiks.kffi.objc.NSEventType
import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSRequestUserAttentionType
import org.graphiks.kffi.objc.NSThread
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.performSelectorOnMainThread_withObject_waitUntilDone
import org.graphiks.kffi.objc.postEvent_atStart
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.util.concurrent.CompletableFuture
import org.graphiks.kadre.window.WindowAttention

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

    /** Starts one independently closeable lifecycle observation owner for an embedded session. */
    fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable

    fun requestUserAttention(attention: WindowAttention): Long =
        error("AppKit user attention is unavailable")

    fun cancelUserAttentionRequest(token: Long) = Unit

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
    private val lifecycleSource = KffiAppKitLifecycleSource()

    override fun isMainThread(): Boolean = NSThread.isMainThread()

    override fun isRunning(): Boolean = synchronized(lock) { application }?.isRunning()
        ?: ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication()).isRunning()
        }

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        lifecycleSource.start(listener)

    override fun requestUserAttention(attention: WindowAttention): Long {
        check(isMainThread()) { "AppKit user attention must be requested on the process main thread" }
        val requestType = when (attention) {
            WindowAttention.Informational -> NSRequestUserAttentionType.NSInformationalRequest
            WindowAttention.Critical -> NSRequestUserAttentionType.NSCriticalRequest
            WindowAttention.None -> error("WindowAttention.None is a broker cancellation request")
        }
        return ObjCRuntime.autoreleasePool {
            sharedApplicationOnMainThread().requestUserAttention(requestType)
        }
    }

    override fun cancelUserAttentionRequest(token: Long) {
        check(isMainThread()) { "AppKit user attention must be cancelled on the process main thread" }
        ObjCRuntime.autoreleasePool {
            sharedApplicationOnMainThread().cancelUserAttentionRequest(token)
        }
    }

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

    private fun sharedApplicationOnMainThread(): NSApplication = synchronized(lock) { application }
        ?: NSApplication(NSApplication.sharedApplication())

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

    private fun createWakeEvent(): MemorySegment = Arena.ofConfined().use { arena ->
        val origin = NSPoint.allocate(arena).also { point ->
            point.x = 0.0
            point.y = 0.0
        }
        NSEvent.otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2(
            NSEventType.NSEventTypeApplicationDefined,
            origin,
            NSEventModifierFlags(0L),
            0.0,
            0L,
            MemorySegment.NULL,
            0.toShort(),
            0L,
            0L,
        )
    }
}
