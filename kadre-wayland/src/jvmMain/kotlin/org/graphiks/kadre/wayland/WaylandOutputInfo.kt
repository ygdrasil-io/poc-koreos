/**
 * WaylandOutputInfo — real monitor geometry and mode data from wl_output events.
 *
 * Prior to Sprint 3 (#272), Wayland monitor data was entirely synthetic (derived
 * from the first window's scale factor). This file collects real `wl_output.geometry`,
 * `wl_output.mode`, and `wl_output.scale` events for all advertised outputs and
 * exposes them as [MonitorHandle] instances.
 *
 * ### wl_output protocol (version ≤ 4)
 * The wl_output listener vtable order is: geometry, mode, done, scale.
 * - geometry: (x, y) position in compositor space, physical dimensions in mm,
 *   subpixel order, make/model strings, transform (rotation/reflection).
 * - mode: flags (current/preferred bitmask), width/height in pixels, refresh in mHz.
 * - done: signals the end of a batch of output state updates (v2+).
 * - scale: integer scale factor (v2+).
 *
 * ### Hotplug support
 * The compositor emits new global/global_remove events for output hotplug.
 * A complete implementation would listen for these via wl_registry, but the
 * current architecture binds outputs once at startup. Output geometry changes
 * within the lifetime of an output are communicated via new geometry+mode+done
 * sequences, which are handled by this listener.
 */
package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.VideoMode
import java.lang.foreign.MemorySegment

/**
 * Real monitor information collected from wl_output events.
 *
 * @property outputPtr Address of the wl_output proxy for this monitor.
 * @param outputVersion The protocol version this output is bound at.
 */
internal class WaylandOutputInfo(
    val outputPtr: Long,
    private var name: String?,
    private var outputVersion: Int,
) {
    /** Compositor-space position (x, y) from wl_output.geometry. */
    var positionX: Int = 0
    var positionY: Int = 0

    /** Physical dimensions in millimetres from wl_output.geometry. 0 when unknown. */
    var physicalWidthMm: Int = 0
    var physicalHeightMm: Int = 0

    /** Output make and model from wl_output.geometry. */
    var make: String? = null
    var model: String? = null

    /** Integer scale factor from wl_output.scale (v2+). Defaults to 1. */
    var scale: Int = 1

    /** Current video mode width in pixels. */
    var modeWidth: Int = 0
    /** Current video mode height in pixels. */
    var modeHeight: Int = 0
    /** Current video mode refresh rate in mHz, or 0 if unknown. */
    var modeRefresh: Int = 0
    /** Flags bitmask from wl_output.mode (WL_OUTPUT_MODE_CURRENT = 0x1, WL_OUTPUT_MODE_PREFERRED = 0x2). */
    var modeFlags: Int = 0

    /**
     * Updates geometry from a wl_output.geometry event.
     */
    fun updateGeometry(
        x: Int, y: Int, physW: Int, physH: Int,
        subpixel: Int, makePtr: Long, modelPtr: Long, transform: Int,
    ) {
        positionX = x
        positionY = y
        physicalWidthMm = physW
        physicalHeightMm = physH
        make = if (makePtr != 0L) {
            try { MemorySegment.ofAddress(makePtr).reinterpret(128).getString(0) } catch (_: Throwable) { null }
        } else null
        model = if (modelPtr != 0L) {
            try { MemorySegment.ofAddress(modelPtr).reinterpret(128).getString(0) } catch (_: Throwable) { null }
        } else null
    }

    /**
     * Updates current mode information from wl_output.mode.
     */
    fun updateMode(flags: Int, width: Int, height: Int, refresh: Int) {
        modeFlags = flags
        if ((flags and 0x1) != 0) {
            modeWidth = width
            modeHeight = height
            modeRefresh = refresh
        }
    }

    /**
     * Updates the output name from wl_output.name (v4+).
     */
    fun updateName(outputName: String) {
        if (name == null) name = outputName
    }

    /**
     * Converts this output info to a [MonitorHandle] for the kadre-core API.
     */
    fun toMonitorHandle(): MonitorHandle = object : MonitorHandle {
        override val id: Long get() = outputPtr
        override val name: String? get() = this@WaylandOutputInfo.name
        override val position: PhysicalPosition<Int> get() = PhysicalPosition(positionX, positionY)
        override val scaleFactor: Double get() = scale.toDouble()
        override val currentVideoMode: VideoMode get() = VideoMode(
            PhysicalSize(
                if (modeWidth > 0) modeWidth else 1920,
                if (modeHeight > 0) modeHeight else 1080,
            ),
            if (modeRefresh > 0) modeRefresh else null,
            null,
        )
        override val videoModes: List<VideoMode> get() = listOf(currentVideoMode)
    }

    companion object {
        fun isCurrentMode(flags: Int): Boolean = (flags and 0x1) != 0
        fun isPreferredMode(flags: Int): Boolean = (flags and 0x2) != 0
    }
}
