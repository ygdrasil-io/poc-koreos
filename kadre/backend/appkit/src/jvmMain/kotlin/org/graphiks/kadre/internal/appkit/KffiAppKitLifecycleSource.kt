package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSNotificationCenter
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.managed.observe
import java.lang.foreign.Arena
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one set of AppKit lifecycle observations for an embedded Kadre session. */
internal fun interface AppKitLifecycleSource {
    fun start(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable
}

/** KFFI-backed [AppKitLifecycleSource] which never replaces AppKit's application delegate. */
internal class KffiAppKitLifecycleSource : AppKitLifecycleSource {
    override fun start(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        ObjCRuntime.autoreleasePool {
            val application = NSApplication(NSApplication.sharedApplication())
            val center = NSNotificationCenter(NSNotificationCenter.defaultCenter())
            val observations = mutableListOf<AutoCloseable>()

            try {
                APPKIT_NOTIFICATIONS.forEach { (name, signal) ->
                    observations += center.observe(ObjCRuntime.newNSString(Arena.global(), name), application.ptr) {
                        listener(signal)
                    }
                }
                AppKitLifecycleObservations(observations)
            } catch (failure: Throwable) {
                observations.closeAllSuppressing(failure)
                throw failure
            }
        }

    private companion object {
        val APPKIT_NOTIFICATIONS: List<Pair<String, AppKitLifecycleSignal>> = listOf(
            "NSApplicationDidBecomeActiveNotification" to AppKitLifecycleSignal.BecameActive,
            "NSApplicationDidResignActiveNotification" to AppKitLifecycleSignal.BecameInactive,
            "NSApplicationDidHideNotification" to AppKitLifecycleSignal.DidHide,
            "NSApplicationDidUnhideNotification" to AppKitLifecycleSignal.DidUnhide,
            "NSApplicationWillTerminateNotification" to AppKitLifecycleSignal.HostTerminated,
        )
    }
}

private class AppKitLifecycleObservations(
    private val observations: List<AutoCloseable>,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        observations.closeAllSuppressing()
    }
}

private fun Iterable<AutoCloseable>.closeAllSuppressing(primary: Throwable? = null) {
    var failure = primary
    forEach { observation ->
        try {
            observation.close()
        } catch (closeFailure: Throwable) {
            if (failure == null) {
                failure = closeFailure
            } else {
                failure.addSuppressed(closeFailure)
            }
        }
    }
    if (primary == null && failure != null) throw failure
}
