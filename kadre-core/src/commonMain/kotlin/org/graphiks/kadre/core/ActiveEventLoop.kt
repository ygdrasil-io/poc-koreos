/**
 * Interface representing the event loop that is active during a callback.
 *
 * Scope: pure Kotlin interface, no native reference.
 */
package org.graphiks.kadre.core

/**
 * Access to the event loop from [ApplicationHandler] callbacks.
 *
 * This interface is passed as a parameter on each incoming call into the
 * application handler, allowing the latter to create windows, control the
 * execution flow, and initiate shutdown of the loop.
 */
interface ActiveEventLoop {

    /**
     * Creates a new window with the specified attributes.
     *
     * @param attributes Configuration parameters of the window to create.
     * @return The created window.
     */
    fun createWindow(attributes: WindowAttributes): Window

    /**
     * Creates a platform custom cursor.
     *
     * Unsupported platforms return [WindowRequestResult.Failure] with
     * [RequestError.Unsupported] and leave [cursorOut] unused.
     */
    fun createCustomCursor(image: CursorImage, cursorOut: (CustomCursor) -> Unit): WindowRequestResult =
        WindowRequestResult.Failure(RequestError.Unsupported("Custom cursors are unsupported by this event loop"))

    /**
     * Returns all monitors visible to the event loop.
     */
    fun availableMonitors(): List<MonitorHandle> = emptyList()

    /**
     * Returns the primary monitor, if the platform can identify one.
     */
    fun primaryMonitor(): MonitorHandle? = null

    /**
     * Returns a persistent display handle usable independently from a window.
     */
    fun ownedDisplayHandle(): OwnedDisplayHandle? = null

    /**
     * Sets the waiting behavior of the event loop
     * after the end of the current iteration.
     *
     * @param controlFlow New waiting behavior.
     */
    fun setControlFlow(controlFlow: ControlFlow)

    /**
     * Returns the currently configured waiting behavior.
     */
    val controlFlow: ControlFlow

    /**
     * Requests shutdown of the event loop.
     *
     * The loop does not stop immediately; it finishes the current
     * iteration before stopping.
     */
    fun exit()

    /**
     * Indicates whether a shutdown request has been issued.
     *
     * @return true if [exit] has been called and the loop is going to stop.
     */
    val isExiting: Boolean

    /**
     * Creates a thread-safe proxy to this event loop.
     *
     * @return An [EventLoopProxy] usable from any thread.
     */
    fun createProxy(): EventLoopProxy
}
