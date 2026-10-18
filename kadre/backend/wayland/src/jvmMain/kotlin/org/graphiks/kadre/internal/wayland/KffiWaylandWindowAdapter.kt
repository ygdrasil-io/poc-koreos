package org.graphiks.kadre.internal.wayland

import java.lang.foreign.MemorySegment
import org.graphiks.kffi.wayland.managed.WaylandClient
import org.graphiks.kffi.wayland.managed.WaylandShmBuffer
import org.graphiks.kffi.wayland.managed.WaylandSurface
import org.graphiks.kffi.wayland.managed.WaylandToplevel
import org.graphiks.kffi.wayland.managed.WaylandXdgSurface

/**
 * Adaptateur kffi de [WaylandNativeWindow] : toute la mécanique FFM reste
 * dans kffi, l'adaptateur n'orchestre que la séquence protocolaire et le
 * suivi du dernier serial configuré.
 */
internal class KffiWaylandWindowAdapter private constructor(
    private val client: WaylandClient,
    private val seatProvider: () -> org.graphiks.kffi.wayland.managed.WaylandSeat?,
    private val surface: WaylandSurface,
    private val xdgSurface: WaylandXdgSurface,
    private val toplevel: WaylandToplevel,
    private val buffer: WaylandShmBuffer,
    override val width: Int,
    override val height: Int,
    private val displayProxyAddress: Long,
) : WaylandNativeWindow {

    override val nativeAddresses: Pair<Long, Long>
        get() = displayProxyAddress to surface.proxyAddress

    @Volatile
    private var latestConfigureSerial: Int = -1

    override fun content(): MemorySegment = buffer.content()

    override fun commitCurrent() {
        surface.commit()
    }

    override fun commitInitial() {
        surface.commit()
    }

    override fun onConfigure(listener: (serial: Int) -> Unit) {
        xdgSurface.addConfigureListener(
            onConfigure = { serial ->
                latestConfigureSerial = serial
                listener(serial)
            },
        )
    }

    override fun ackLatestConfigure() {
        check(latestConfigureSerial >= 0) { "no configure observed before ack" }
        xdgSurface.ackConfigure(latestConfigureSerial)
    }

    override fun onToplevelEvents(
        onConfigure: (width: Int, height: Int, states: List<Int>) -> Unit,
        onClose: () -> Unit,
    ) {
        toplevel.addEventsListener(
            onConfigure = { width, height, states -> onConfigure(width, height, states) },
            onClose = onClose,
        )
    }

    override fun onEnter(listener: () -> Unit) {
        surface.addEnterLeaveListener(onEnter = { listener() }, onLeave = { })
    }

    override fun onNextFrame(listener: () -> Unit) {
        surface.addFrameListener(onFrame = { listener() })
    }

    override fun setTitle(title: String) {
        toplevel.setTitle(title)
    }

    override fun setAppId(appId: String) {
        toplevel.setAppId(appId)
    }

    override fun setConstraints(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int) {
        toplevel.setMinSize(minWidth, minHeight)
        toplevel.setMaxSize(maxWidth, maxHeight)
    }

    override fun setFullscreenBorderless(enabled: Boolean) {
        if (enabled) toplevel.setFullscreen() else toplevel.unsetFullscreen()
    }

    override fun beginMove(serial: Int) {
        moveResizeSeat()?.let { seat -> toplevel.beginMove(seat, serial) }
    }

    override fun beginResize(serial: Int, edges: Int) {
        moveResizeSeat()?.let { seat -> toplevel.beginResize(seat, serial, edges) }
    }

    override fun close() {
        toplevel.close()
        xdgSurface.close()
        surface.close()
        buffer.close()
    }

    private fun moveResizeSeat() = seatProvider()

    internal companion object {
        fun create(
            client: WaylandClient,
            width: Int,
            height: Int,
            seatProvider: () -> org.graphiks.kffi.wayland.managed.WaylandSeat? = { null },
        ): KffiWaylandWindowAdapter {
            val surface = client.createSurface()
            val xdgSurface = client.createXdgSurface(surface)
            val toplevel = client.createToplevel(xdgSurface)
            val buffer = client.createShmBuffer(width, height)
            return KffiWaylandWindowAdapter(
                client,
                seatProvider,
                surface,
                xdgSurface,
                toplevel,
                buffer,
                width,
                height,
                client.displayProxyAddress,
            )
        }
    }
}
