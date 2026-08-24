package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSThread
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.performSelectorOnMainThread_withObject_waitUntilDone
import org.graphiks.kffi.objc.postEvent_atStart
import java.lang.foreign.GroupLayout
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal interface AppKitNativeApplication {
    fun isMainThread(): Boolean

    fun run()

    fun requestStop()
}

internal class KffiAppKitNativeApplication : AppKitNativeApplication {
    private val lock = Any()
    private var application: NSApplication? = null
    private var stopRequested = false
    private var stopScheduled = false

    override fun isMainThread(): Boolean = NSThread.isMainThread()

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
                .start { target.scheduleStop() }
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
                }
            }
        }
    }

    override fun requestStop() {
        val target = synchronized(lock) {
            stopRequested = true
            takeStopTarget()
        }
        target?.scheduleStop()
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

    private fun NSApplication.scheduleStop() {
        performSelectorOnMainThread_withObject_waitUntilDone(
            ObjCRuntime.sel("stop:"),
            ptr,
            true,
        )
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
