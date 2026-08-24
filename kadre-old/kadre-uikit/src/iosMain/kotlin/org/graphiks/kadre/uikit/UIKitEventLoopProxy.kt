package org.graphiks.kadre.uikit

import org.graphiks.kadre.core.EventLoopProxy
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.AtomicInt

/**
 * Thread-safe [EventLoopProxy] for the UIKit backend.
 *
 * [wakeUp] posts a block on the main queue via `dispatch_async` so the
 * loop-level [UIKitScheduler] processes the wake on the main thread.
 *
 * ## Coalescing
 * An [AtomicInt] flag (`pending`) prevents redundant dispatches: if [wakeUp]
 * is called several times before the posted block executes, only one block
 * runs. The flag is reset inside the block, before calling the scheduler, so
 * a concurrent [wakeUp] issued *during* the callback correctly re-schedules
 * another tick.
 *
 * ## Why dispatch_async instead of CFRunLoopWakeUp
 * `CFRunLoopWakeUp(CFRunLoopGetMain())` only unblocks a `CFRunLoopRun` call.
 * On iOS, UIKit owns the run loop and `CADisplayLink` drives frame callbacks —
 * there is no blocking `CFRunLoopRun` to wake. Using `dispatch_async` on the
 * main queue is the idiomatic way to schedule work on the main thread from any
 * thread on Apple platforms, and it naturally coalesces via the flag above.
 */
@OptIn(ExperimentalForeignApi::class)
internal class UIKitEventLoopProxy(
    private val scheduler: UIKitScheduler,
    private val dispatchMain: (() -> Unit) -> Unit = { block ->
        dispatch_async(dispatch_get_main_queue()) { block() }
    },
) : EventLoopProxy {

    /** 1 = a dispatch is already pending, 0 = idle. */
    private val pending = AtomicInt(0)

    override fun wakeUp() {
        // CAS: only enqueue if no dispatch is already pending.
        if (!pending.compareAndSet(0, 1)) return

        try {
            dispatchMain {
                // Reset before the scheduler tick so a wakeUp() issued from one of
                // its callbacks can enqueue the following tick independently.
                pending.value = 0
                scheduler.wakeExternal()
            }
        } catch (failure: Throwable) {
            pending.value = 0
            throw failure
        }
    }
}
