package org.graphiks.kadre.samples.desktour.appkit

import java.lang.foreign.MemorySegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.graphiks.kffi.objc.NSThread
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.managed.ObjCManagedClass
import org.graphiks.kffi.objc.managed.ObjCManagedInstance
import org.graphiks.kffi.objc.managed.ObjCMethodSignatures
import org.graphiks.kffi.objc.performSelectorOnMainThread_withObject_waitUntilDone

/** Posts to the AppKit run loop already owned by Kadre. Never borrows a window handle. */
internal class AppKitMainThread {
    val dispatcher = OwnerThreadExecutor(NSThread::isMainThread, ::enqueue, ::invokeAndWait)

    fun <T> call(action: () -> T): T = dispatcher.call { ObjCRuntime.autoreleasePool(action) }

    suspend fun shutdown(work: Job, cleanup: () -> Unit) {
        val outcome = withContext(NonCancellable + Dispatchers.Default) {
            runCatching { dispatcher.shutdown(work) { ObjCRuntime.autoreleasePool(cleanup) } }
        }
        outcome.getOrThrow()
    }

    private fun invokeAndWait(task: Runnable) = ObjCRuntime.autoreleasePool {
        // The caller owns this receiver until the native selector (including its FFM
        // trampoline) has returned. FutureTask completion alone is not that barrier.
        val invocation = receiverClass.createInstance {
            onVoidObject(INVOKE) { ObjCRuntime.autoreleasePool { task.run() } }
        }
        invocation.use {
            it.receiver.performSelectorOnMainThread_withObject_waitUntilDone(
                ObjCRuntime.sel(INVOKE), MemorySegment.NULL, true,
            )
        }
    }

    private fun enqueue(task: Runnable) = ObjCRuntime.autoreleasePool {
        lateinit var invocation: ObjCManagedInstance
        invocation = receiverClass.createInstance {
            onVoidObject(INVOKE) {
                try {
                    ObjCRuntime.autoreleasePool { task.run() }
                } finally {
                    // KFFI defers native release until this admitted callback has returned.
                    invocation.close()
                }
            }
        }
        try {
            invocation.receiver.performSelectorOnMainThread_withObject_waitUntilDone(
                ObjCRuntime.sel(INVOKE), MemorySegment.NULL, false,
            )
        } catch (failure: Throwable) {
            try { invocation.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    companion object {
        private const val INVOKE = "deskTourRun:"
        // Only the registered class is process-scoped; admission/lifecycle is per mount.
        private val receiverClass by lazy {
            ObjCManagedClass.registerOnce(methods = mapOf(INVOKE to ObjCMethodSignatures.VoidObject))
        }
    }
}
