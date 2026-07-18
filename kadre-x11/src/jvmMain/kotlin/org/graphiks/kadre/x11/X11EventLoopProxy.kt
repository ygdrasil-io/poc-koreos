package org.graphiks.kadre.x11

import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.ffi.posix.PosixWakeup

/** Thread-safe proxy that wakes the loop without invoking Xlib. */
class X11EventLoopProxy internal constructor(
    private val wakeup: PosixWakeup,
) : EventLoopProxy {
    override fun wakeUp() {
        check(wakeup.signal()) { "X11 wake-up failed: wake fd is closed" }
    }
}
