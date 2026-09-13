package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.display.DisplayId
import org.graphiks.kadre.display.DisplayModeId
import org.graphiks.kadre.input.GamepadId
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowCloseRequestId
import org.graphiks.kadre.window.WindowOperationId
import org.graphiks.kadre.window.WindowRequestId
internal object RuntimeProcessIds {
    private val lock = RuntimeLock()
    private var sessionIds = 0L
    private var windowRequestIds = 0L
    private var windowIds = 0L
    private var windowOperationIds = 0L
    private var windowCloseRequestIds = 0L
    private var surfaceIds = 0L
    private var displayIds = 0L
    private var displayModeIds = 0L
    private var gamepadIds = 0L

    fun nextSessionId(): SessionId = SessionId(nextValue("session ID", { sessionIds }, { sessionIds += 1L }))

    fun nextWindowRequestId(): WindowRequestId =
        WindowRequestId(nextValue("window request ID", { windowRequestIds }, { windowRequestIds += 1L }))

    fun nextWindowId(): WindowId = WindowId(nextValue("window ID", { windowIds }, { windowIds += 1L }))

    fun nextWindowOperationId(): WindowOperationId =
        WindowOperationId(nextValue("window operation ID", { windowOperationIds }, { windowOperationIds += 1L }))

    fun nextWindowCloseRequestId(): WindowCloseRequestId =
        WindowCloseRequestId(nextValue("window close request ID", { windowCloseRequestIds }, { windowCloseRequestIds += 1L }))

    fun nextSurfaceId(): SurfaceId = SurfaceId(nextValue("surface ID", { surfaceIds }, { surfaceIds += 1L }))

    fun nextDisplayId(): DisplayId = DisplayId(nextValue("display ID", { displayIds }, { displayIds += 1L }))

    fun nextDisplayModeId(): DisplayModeId =
        DisplayModeId(nextValue("display mode ID", { displayModeIds }, { displayModeIds += 1L }))

    fun nextGamepadId(): GamepadId = GamepadId(nextValue("gamepad ID", { gamepadIds }, { gamepadIds += 1L }))

    private inline fun nextValue(
        name: String,
        current: () -> Long,
        increment: () -> Unit,
    ): Long = lock.withLock {
        val value = current()
        check(value >= 0L && value < Long.MAX_VALUE) { "$name space exhausted" }
        increment()
        value
    }
}
