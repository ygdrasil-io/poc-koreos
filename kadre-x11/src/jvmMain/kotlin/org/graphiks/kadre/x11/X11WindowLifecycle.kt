package org.graphiks.kadre.x11

import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.ffi.posix.PosixWakeup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the registry, publication queues, and terminal state for one X11 display loop. */
internal class X11WindowLifecycle(
    private val loop: X11EventLoop,
    private val displayPtr: Long,
    private val wakeup: PosixWakeup,
    private val nativeAdapter: X11NativeAdapter,
    private val checkLoopThread: () -> Unit,
    private val detachAuxiliaryState: (Long) -> Unit,
) {
    internal val windows = ConcurrentHashMap<Long, X11Window>()

    private val stateLock = Any()
    private val owners = HashMap<Long, X11WindowOwner>()
    private val eventQueue = ConcurrentLinkedQueue<X11QueueItem>()
    private val pendingCloseCommands = HashMap<X11WindowOwner, X11QueuedCloseCommand>()
    private val pendingRedrawItems = HashMap<X11WindowOwner, X11QueuedWindowEvent>()

    fun register(window: X11Window): X11Window = synchronized(stateLock) {
        val owner = X11WindowOwner(window)
        owners[window.id.value] = owner
        windows[window.id.value] = window
        window
    }

    /** Publishes one owner-scoped redraw and wakes only a newly queued synthetic request. */
    fun requestRedraw(windowId: WindowId): Boolean = synchronized(stateLock) {
        val owner = currentOwnerLocked(windowId) ?: return false
        if (pendingRedrawItems.containsKey(owner)) return true

        val queued = X11QueuedWindowEvent(owner, WindowEvent.RedrawRequested, isRedraw = true)
        pendingRedrawItems[owner] = queued
        eventQueue.add(queued)
        val signalled = try {
            wakeup.signal()
        } catch (failure: Throwable) {
            rollbackRedrawLocked(owner, queued)
            throw IllegalStateException("X11 redraw wake failed", failure)
        }
        if (!signalled) {
            rollbackRedrawLocked(owner, queued)
            error("X11 redraw wake failed: wake fd is closed")
        }
        true
    }

    /** Native Expose and synthetic redraw share the same pending owner slot. */
    fun enqueueExpose(windowId: WindowId): Boolean = synchronized(stateLock) {
        val owner = currentOwnerLocked(windowId) ?: return false
        if (pendingRedrawItems.containsKey(owner)) return true
        val queued = X11QueuedWindowEvent(owner, WindowEvent.RedrawRequested, isRedraw = true)
        pendingRedrawItems[owner] = queued
        eventQueue.add(queued)
        true
    }

    fun enqueueWindowEvent(windowId: WindowId, event: WindowEvent): Boolean = synchronized(stateLock) {
        val owner = currentOwnerLocked(windowId) ?: return false
        eventQueue.add(X11QueuedWindowEvent(owner, event))
        true
    }

    /** Publishes close plus wake as one transaction, rolling both records back on wake failure. */
    fun closeWindow(windowId: WindowId): Boolean = synchronized(stateLock) {
        val owner = currentOwnerLocked(windowId) ?: return false
        if (pendingCloseCommands.containsKey(owner)) return true

        val command = X11QueuedCloseCommand(owner)
        pendingCloseCommands[owner] = command
        eventQueue.add(command)
        val signalled = try {
            wakeup.signal()
        } catch (failure: Throwable) {
            rollbackCloseLocked(owner, command)
            throw IllegalStateException("X11 close wake failed", failure)
        }
        if (!signalled) {
            rollbackCloseLocked(owner, command)
            error("X11 close wake failed: wake fd is closed")
        }
        true
    }

    /** Handles DestroyNotify without issuing a second XDestroyWindow. */
    fun nativeWindowDestroyed(windowId: WindowId): Boolean {
        checkLoopThread()
        val owner = synchronized(stateLock) {
            val current = currentOwnerLocked(windowId) ?: return false
            if (!beginCloseLocked(current)) return false
            current
        }

        var failure: Throwable? = null
        try {
            owner.window.releaseLoopOwnedResources()
        } catch (thrown: Throwable) {
            failure = appendLifecycleFailure(failure, thrown)
        } finally {
            synchronized(stateLock) {
                eventQueue.add(
                    X11QueuedWindowEvent(
                        owner = owner,
                        event = WindowEvent.Destroyed,
                        terminalFailure = failure,
                    ),
                )
            }
        }
        return true
    }

    fun drain(handler: ApplicationHandler) {
        checkLoopThread()
        val batch = takeBoundaryBatch()
        var terminalFailure: Throwable? = null

        for (command in batch.filterIsInstance<X11QueuedCloseCommand>()) {
            val claimed = synchronized(stateLock) {
                if (pendingCloseCommands[command.owner] !== command) {
                    false
                } else {
                    pendingCloseCommands.remove(command.owner)
                    beginCloseLocked(command.owner)
                }
            }
            if (!claimed) continue

            terminalFailure = closeNative(command.owner, terminalFailure)
            try {
                deliverDestroyed(command.owner, handler)
            } catch (thrown: Throwable) {
                terminalFailure = appendLifecycleFailure(terminalFailure, thrown)
            }
        }

        for (queued in batch.filterIsInstance<X11QueuedWindowEvent>()) {
            if (queued.event != WindowEvent.Destroyed) continue
            terminalFailure = appendLifecycleFailure(terminalFailure, queued.terminalFailure)
            try {
                deliverDestroyed(queued.owner, handler)
            } catch (thrown: Throwable) {
                terminalFailure = appendLifecycleFailure(terminalFailure, thrown)
            }
        }

        terminalFailure?.let { throw it }

        for (item in batch) {
            if (item is X11QueuedCloseCommand) continue
            val queued = item as X11QueuedWindowEvent
            if (queued.event == WindowEvent.Destroyed) continue

            val deliver = synchronized(stateLock) {
                if (queued.isRedraw) {
                    if (pendingRedrawItems[queued.owner] !== queued) {
                        false
                    } else {
                        // Release immediately before the callback so a re-arm lands after this boundary.
                        pendingRedrawItems.remove(queued.owner)
                        isCurrentOwnerLocked(queued.owner)
                    }
                } else {
                    isCurrentOwnerLocked(queued.owner)
                }
            }
            if (deliver) {
                handler.windowEvent(loop, queued.owner.window.id, queued.event)
            }
        }
    }

    fun hasPendingWork(): Boolean = eventQueue.isNotEmpty()

    fun closeAllWindowsDirect() {
        checkLoopThread()
        val snapshot = synchronized(stateLock) {
            owners.values.sortedBy { it.window.id.value }
        }
        var failure: Throwable? = null
        for (owner in snapshot) {
            val claimed = synchronized(stateLock) { beginCloseLocked(owner) }
            if (claimed) {
                failure = closeNative(owner, failure)
            }
        }
        synchronized(stateLock) {
            eventQueue.clear()
            pendingCloseCommands.clear()
            pendingRedrawItems.clear()
        }
        failure?.let { throw it }
    }

    private fun takeBoundaryBatch(): List<X11QueueItem> = synchronized(stateLock) {
        val boundary = X11DispatchBoundary()
        eventQueue.add(boundary)
        buildList {
            while (true) {
                val item = eventQueue.poll() ?: break
                if (item === boundary) break
                add(item)
            }
        }
    }

    private fun closeNative(owner: X11WindowOwner, initialFailure: Throwable?): Throwable? {
        var failure = initialFailure
        val actions = listOf<() -> Unit>(
            owner.window::releaseLoopOwnedResources,
            { nativeAdapter.destroyWindow(displayPtr, owner.window.id.value) },
            { nativeAdapter.flush(displayPtr) },
        )
        for (action in actions) {
            try {
                action()
            } catch (thrown: Throwable) {
                failure = appendLifecycleFailure(failure, thrown)
            }
        }
        return failure
    }

    private fun beginCloseLocked(owner: X11WindowOwner): Boolean {
        if (!owner.closeStarted.compareAndSet(false, true)) return false
        val windowId = owner.window.id.value
        owners.remove(windowId, owner)
        windows.remove(windowId, owner.window)
        pendingCloseCommands.remove(owner)
        pendingRedrawItems.remove(owner)?.let(eventQueue::remove)
        detachAuxiliaryState(windowId)
        eventQueue.removeIf { item ->
            (item is X11QueuedWindowEvent && item.owner === owner) ||
                (item is X11QueuedCloseCommand && item.owner === owner)
        }
        return true
    }

    private fun currentOwnerLocked(windowId: WindowId): X11WindowOwner? =
        owners[windowId.value]?.takeIf { windows[windowId.value] === it.window }

    private fun isCurrentOwnerLocked(owner: X11WindowOwner): Boolean =
        owners[owner.window.id.value] === owner && windows[owner.window.id.value] === owner.window

    private fun rollbackRedrawLocked(owner: X11WindowOwner, queued: X11QueuedWindowEvent) {
        if (pendingRedrawItems[owner] === queued) {
            pendingRedrawItems.remove(owner)
            eventQueue.remove(queued)
        }
    }

    private fun rollbackCloseLocked(owner: X11WindowOwner, command: X11QueuedCloseCommand) {
        if (pendingCloseCommands[owner] === command) {
            pendingCloseCommands.remove(owner)
            eventQueue.remove(command)
        }
    }

    private fun deliverDestroyed(owner: X11WindowOwner, handler: ApplicationHandler) {
        if (owner.destroyedDelivered.compareAndSet(false, true)) {
            handler.windowEvent(loop, owner.window.id, WindowEvent.Destroyed)
        }
    }
}

private class X11WindowOwner(val window: X11Window) {
    val closeStarted = AtomicBoolean(false)
    val destroyedDelivered = AtomicBoolean(false)
}

private sealed interface X11QueueItem

private data class X11QueuedWindowEvent(
    val owner: X11WindowOwner,
    val event: WindowEvent,
    val isRedraw: Boolean = false,
    val terminalFailure: Throwable? = null,
) : X11QueueItem

private data class X11QueuedCloseCommand(val owner: X11WindowOwner) : X11QueueItem

private class X11DispatchBoundary : X11QueueItem

private fun appendLifecycleFailure(primary: Throwable?, additional: Throwable?): Throwable? {
    if (additional == null) return primary
    if (primary == null) return additional
    if (additional !== primary) primary.addSuppressed(additional)
    return primary
}
