package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.input.*
import org.graphiks.kadre.internal.runtime.RuntimeSynchronousInteraction
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.*

/** UI-owned, per-window normalization. The common runtime owns all public state and identities. */
internal class Win32InputRouter(
    private val hwnd: Long,
    private val surfaceId: SurfaceId,
    private val port: Win32NativeHostPort,
    private val emit: (SurfaceStimulus) -> Boolean,
    private val interact: (RuntimeSynchronousInteraction) -> Unit,
    private val isAlive: () -> Boolean,
    private val onFocusLost: () -> Unit = {},
    private val onPrimaryPointerBoundary: () -> Unit = {},
    private val contacts: Win32ContactRouter? = null,
) : AutoCloseable {
    private var ready = false
    private var closed = false
    private var focused = false
    private var focusGeneration = 0L
    private var inside = false
    private var lastPosition: LogicalPoint? = null
    private val held = mutableMapOf<PhysicalKey, Pair<LogicalKey, KeyLocation>>()
    private var boundary = 0L
    private var wheelSignature: Triple<Boolean, Win32WheelSettings, Int>? = null
    var defaultBehavior: InputDefaultBehavior = InputDefaultBehavior.HostDefault

    fun activate() {
        if (closed || ready || !isAlive()) return
        ready = true
        emit(SurfaceStimulus.InputObservationChanged(surfaceId, keyboardInstalled = true, pointerInstalled = true))
        contacts?.activate()
    }

    private fun barrier() { boundary++; wheelSignature = null }
    private fun defaultResult(consumed: Long = 0L): Long? =
        if (!isAlive() || defaultBehavior == InputDefaultBehavior.SuppressWhenPossible) consumed else null

    fun handle(message: Int, wParam: Long, lParam: Long): Long? {
        if (!ready || !isAlive()) return null
        contacts?.handle(message, wParam, lParam)?.let { return it }
        if (contacts?.installed == true && (message in 0x200..0x20e || message == 0x2a3) && port.isPromotedMouseMessage()) return 0L
        if (message == 0x1f || message == WM_SHOWWINDOW && wParam == 0L || message == WM_SIZE && wParam == 1L) contacts?.reset()
        return when (message) {
            0x100, 0x101, 0x104, 0x105 -> { keyboard(message, wParam, lParam); defaultResult() }
            0x102, 0x103, 0x106, 0x107, 0x109, 0x286 ->
                port.handleTextCharacter(hwnd, message, wParam, lParam) ?: defaultResult()
            0x7 -> { focus(true); 0L }
            0x8 -> { focus(false); 0L }
            WM_ACTIVATE -> { focus(wParam and 0xffffL != 0L); null }
            0x200 -> {
                val position = position(lParam)
                enter(position)
                val previous = lastPosition
                emit(SurfaceStimulus.PointerMoved(surfaceId, PointerKind.Mouse, position,
                    LogicalDelta(position.x - (previous?.x ?: position.x), position.y - (previous?.y ?: position.y)), null, null))
                lastPosition = position
                defaultResult()
            }
            in 0x201..0x209, in 0x20b..0x20d -> {
                val position = position(lParam)
                enter(position)
                val button = when (message) {
                    in 0x201..0x203 -> PointerButton.Primary
                    in 0x204..0x206 -> PointerButton.Secondary
                    in 0x207..0x209 -> PointerButton.Auxiliary
                    else -> when (val x = ((wParam ushr 16) and 0xffff).toInt()) {
                        1 -> PointerButton.Back
                        2 -> PointerButton.Forward
                        else -> PointerButton.Other(x)
                    }
                }
                val pressed = message !in setOf(0x202, 0x205, 0x208, 0x20c)
                barrier()
                lastPosition = position
                if (button == PointerButton.Primary) onPrimaryPointerBoundary()
                if (pressed) interact(RuntimeSynchronousInteraction.PointerPressed(button, position, null))
                else emit(SurfaceStimulus.PointerButtonChanged(surfaceId, PointerKind.Mouse, button,
                    PointerButtonState.Released, position, null, null))
                defaultResult(if (message in 0x20b..0x20d) 1L else 0L)
            }
            0x2a3 -> {
                if (inside) emit(SurfaceStimulus.PointerLeft(surfaceId, PointerKind.Mouse))
                inside = false
                lastPosition = null
                barrier()
                defaultResult()
            }
            0x20a, 0x20e -> {
                val horizontal = message == 0x20e
                val settings = port.readWheelSettings()
                val signature = Triple(horizontal, settings, (wParam and 0xffff).toInt())
                if (signature != wheelSignature) { barrier(); wheelSignature = signature }
                win32WheelDelta(horizontal, wParam, settings)?.let {
                    emit(SurfaceStimulus.Scroll(surfaceId, it, boundary))
                }
                defaultResult()
            }
            else -> null
        }
    }

    private fun keyboard(message: Int, wParam: Long, lParam: Long) {
        val key = port.readKeyboardInput(hwnd, message, wParam, lParam) ?: return
        val packetFocusGeneration = focusGeneration
        val physical = win32PhysicalKey(key)
        val identity = held[physical] ?: (win32LogicalKey(key) to win32KeyLocation(key))
        barrier()
        if (key.pressed) held[physical] = identity else held.remove(physical)
        // The native count is an unsigned word. Runtime overflow/terminal rejection stops expansion.
        val count = if (key.pressed) key.repeatCount.coerceIn(1, 65535) else 1
        repeat(count) { index ->
            if (!ready || !isAlive() || packetFocusGeneration != focusGeneration) return
            val repeating = key.repeat || index > 0
            if (key.pressed && !repeating) {
                interact(RuntimeSynchronousInteraction.KeyPressed(physical, identity.first, identity.second, key.modifiers))
            } else if (!emit(SurfaceStimulus.KeyChanged(surfaceId, physical, identity.first, identity.second,
                    if (key.pressed) KeyState.Pressed else KeyState.Released, repeating, key.modifiers))) return
        }
    }

    private fun position(lParam: Long): LogicalPoint = win32ClientPosition(lParam, port.readScaleFactor(hwnd))

    private fun enter(position: LogicalPoint) {
        if (inside) return
        port.trackMouseLeave(hwnd)
        inside = true
        lastPosition = null
        emit(SurfaceStimulus.PointerEntered(surfaceId, PointerKind.Mouse, position))
    }

    private fun focus(value: Boolean) {
        port.setTextFocus(hwnd, value)
        if (!value) onFocusLost()
        if (!value) contacts?.reset()
        if (focused == value) return
        focused = value
        barrier()
        if (!value) { focusGeneration++; held.clear(); lastPosition = null }
        emit(SurfaceStimulus.FocusChanged(surfaceId, if (value) SurfaceFocus.Focused else SurfaceFocus.Unfocused))
    }

    override fun close() {
        if (closed) return
        closed = true
        ready = false
        contacts?.close()
        port.closeTextInput(hwnd)
        if (inside) port.cancelMouseLeave(hwnd)
        held.clear()
        // Terminal snapshots are frozen by the runtime; no invented reset or key release.
    }
}
