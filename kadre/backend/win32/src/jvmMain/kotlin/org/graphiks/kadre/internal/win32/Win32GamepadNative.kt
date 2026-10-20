package org.graphiks.kadre.internal.win32

import org.graphiks.kffi.win32.managed.Win32XInput
import org.graphiks.kffi.win32.managed.Win32XInputCapabilitiesValue
import org.graphiks.kffi.win32.managed.Win32XInputStateValue

/** Native XInput frontier behind an injectable seam, provable without Windows or hardware. */
internal interface Win32XInputNative {
    /** Polls one slot; null when the slot has no connected gamepad. */
    public fun getState(index: Int): Win32XInputStateValue?

    /** Sets both vibration motors; false when the slot has no connected gamepad. */
    public fun setVibration(index: Int, left: Int, right: Int): Boolean

    /** Reads capabilities; null when the slot has no connected gamepad. */
    public fun getCapabilities(index: Int): Win32XInputCapabilitiesValue?
}

/** The real KFFI XInput frontier. */
internal object KffiWin32XInputNative : Win32XInputNative {
    override fun getState(index: Int): Win32XInputStateValue? = Win32XInput.getState(index)
    override fun setVibration(index: Int, left: Int, right: Int): Boolean =
        Win32XInput.setVibration(index, left, right)

    override fun getCapabilities(index: Int): Win32XInputCapabilitiesValue? =
        Win32XInput.getCapabilities(index)
}

