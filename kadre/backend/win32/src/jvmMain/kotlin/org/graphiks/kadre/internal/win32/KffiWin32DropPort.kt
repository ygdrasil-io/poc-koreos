package org.graphiks.kadre.internal.win32

import org.graphiks.kffi.win32.managed.Win32DropData
import org.graphiks.kffi.win32.managed.Win32DropFormats
import org.graphiks.kffi.win32.managed.Win32DropServices
import org.graphiks.kffi.win32.managed.Win32DropTarget
import org.graphiks.kffi.win32.managed.Win32DropTargetHandler
import org.graphiks.kadre.surface.LogicalPoint

/**
 * Synchronous KFFI admission for one HWND. `dragEnter` is the only callback
 * that snapshots the borrowed OLE data object: the returned effect is decided
 * before the native callback returns, and later callbacks only route the
 * already-admitted offer. No native layout, pointer or medium crosses this
 * adapter; COM work belongs to KFFI.
 */
internal class KffiWin32DropAdmission(
    private val hwnd: Long,
    private val positionReader: (hwnd: Long, x: Int, y: Int) -> LogicalPoint,
    private val budgets: Win32DropBudgets,
    private val urlFormat: Int,
    private val pump: ((() -> Unit) -> Unit),
    private val callbacks: Win32DropCallbacks,
) : Win32DropTargetHandler {
    override fun dragEnter(data: Win32DropData, x: Long, y: Long, keyState: Int, allowedEffects: Int): Int {
        val position = positionReader(hwnd, x.toInt(), y.toInt())
        val source = win32SnapshotDropSource(data, budgets.maxElements, budgets.maxCodeUnits, budgets.maxRetainedBytes, urlFormat, pump)
            ?: return Win32DropFormats.DROPEFFECT_NONE
        val admitted = try {
            callbacks.entered(source, position)
        } catch (failure: Throwable) {
            source.close()
            throw failure
        }
        if (admitted) return Win32DropFormats.DROPEFFECT_COPY
        source.close()
        return Win32DropFormats.DROPEFFECT_NONE
    }

    override fun dragOver(x: Long, y: Long, keyState: Int, allowedEffects: Int): Int {
        callbacks.moved(positionReader(hwnd, x.toInt(), y.toInt()))
        return Win32DropFormats.DROPEFFECT_COPY
    }

    override fun dragLeave() { callbacks.exited() }

    override fun drop(x: Long, y: Long, keyState: Int, allowedEffects: Int): Int {
        callbacks.performed(positionReader(hwnd, x.toInt(), y.toInt()))
        return Win32DropFormats.DROPEFFECT_COPY
    }
}
