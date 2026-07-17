/**
 * WaylandEventLoopProxy — thread-safe proxy to WaylandEventLoop.
 *
 * The POSIX wake owner coalesces concurrent signals and rearms itself only
 * after the event loop drains its readable descriptor.
 */
package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.ffi.posix.PosixWakeup

/** Thread-safe proxy sharing the event loop's owned wake descriptor. */
class WaylandEventLoopProxy(
    private val wakeup: PosixWakeup,
) : EventLoopProxy {
    override fun wakeUp() {
        wakeup.signal()
    }
}
