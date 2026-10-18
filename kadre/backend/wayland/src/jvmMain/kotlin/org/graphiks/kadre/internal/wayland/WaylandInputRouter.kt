package org.graphiks.kadre.internal.wayland

import java.util.concurrent.ConcurrentHashMap
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kffi.wayland.managed.WaylandKeyboardEvent
import org.graphiks.kffi.wayland.managed.WaylandPointerEvent
import org.graphiks.kffi.wayland.managed.WaylandSeatCapabilities

/** One coalesced pointer batch, published at the wl_pointer.frame boundary. */
internal data class WaylandPointerBatch(
    val surfaceId: SurfaceId,
    val entered: Boolean,
    val left: Boolean,
    val x: Double?,
    val y: Double?,
    /** buttons pressed in this batch, keyed by linux button code */
    val pressedButtons: List<Int>,
    val releasedButtons: List<Int>,
    /** scroll units keyed by axis (0 vertical, 1 horizontal): value120 preferred */
    val scrollValue120: Map<Int, Int>,
    val scrollContinuous: Map<Int, Double>,
    /** axis_stop axes ending a scroll sequence in this batch */
    val scrollStopped: List<Int>,
)

/** One keyboard command routed to the owning surface. */
internal sealed interface WaylandKeyCommand {
    val surfaceId: SurfaceId

    data class Key(
        override val surfaceId: SurfaceId,
        val physicalKeycode: Int,
        val pressed: Boolean,
        val time: Int,
    ) : WaylandKeyCommand

    data class Modifiers(
        override val surfaceId: SurfaceId,
        val depressed: Int,
        val latched: Int,
        val locked: Int,
        val group: Int,
    ) : WaylandKeyCommand

    data class FocusLost(
        override val surfaceId: SurfaceId,
        /** physical keycodes held at focus loss, released synthetically */
        val synthesizedReleases: List<Int>,
    ) : WaylandKeyCommand
}

/**
 * Routes raw seat events to their owning surface (design
 * `WAYLAND-PHASE-4-INPUT-DESIGN.md`): pointer events accumulate into ONE
 * batch per wl_pointer.frame (without frame support each event is its own
 * batch), keyboard events route by the focused surface, and a leave with
 * held keys or buttons neutralizes exactly the held ones (SN-1) — a present
 * keyboard is never a synthesized focus (SN-3: keys outside focus are
 * dropped, never routed to a substitute).
 */
internal class WaylandInputRouter(
    /** false for pointer < v5: no frame events exist, each event publishes its own batch. */
    private val frameAware: Boolean = true,
) {

    private val lock = Any()

    /** surface registry: wl_surface proxy address -> surface id */
    private val surfacesByAddress = ConcurrentHashMap<Long, SurfaceId>()

    /** the keyboard-focused surface address (null = no keyboard focus) */
    private var keyboardFocusAddress: Long? = null

    /** keys held per focused surface address (physical keycode -> true) */
    private val heldKeys = HashMap<Long, MutableSet<Int>>()

    /** pointer buttons held per entered surface address */
    private val heldButtons = HashMap<Long, MutableSet<Int>>()

    /** persistent entered surface (across batches): the pointer stays entered until leave */
    private var enteredAddress: Long? = null
    private var enteredSurface: SurfaceId? = null

    /** accumulation state of the in-flight pointer batch */
    private var batchSurface: SurfaceId? = null
    private var batchAddress: Long? = null
    private var batchEntered = false
    private var batchLeft = false
    private var batchX: Double? = null
    private var batchY: Double? = null
    private val batchPressed = ArrayList<Int>()
    private val batchReleased = ArrayList<Int>()
    private val batchValue120 = HashMap<Int, Int>()
    private val batchContinuous = HashMap<Int, Double>()
    private val batchStopped = ArrayList<Int>()

    /** Emits batches; replaced per test. Called under [lock]. */
    internal var onBatch: (WaylandPointerBatch) -> Unit = {}
    internal var onKey: (WaylandKeyCommand) -> Unit = {}

    fun registerSurface(address: Long, surfaceId: SurfaceId) {
        surfacesByAddress[address] = surfaceId
    }

    fun unregisterSurface(address: Long) {
        synchronized(lock) {
            // neutralize BEFORE the registry entry goes: the FocusLost must
            // still carry the surface identity
            neutralizeKeyboardLocked(address, publish = true)
            heldButtons.remove(address)
        }
        surfacesByAddress.remove(address)
    }

    /** True when the seat capabilities advertise a keyboard. */
    fun keyboardAvailable(capabilities: Int): Boolean =
        capabilities and WaylandSeatCapabilities.KEYBOARD != 0

    /** True when the seat capabilities advertise a pointer. */
    fun pointerAvailable(capabilities: Int): Boolean =
        capabilities and WaylandSeatCapabilities.POINTER != 0

    fun offer(event: WaylandPointerEvent) {
        synchronized(lock) {
            when (event.kind) {
                WaylandPointerEvent.Kind.Enter -> {
                    val id = surfacesByAddress[event.surfaceAddress] ?: return
                    enteredAddress = event.surfaceAddress
                    enteredSurface = id
                    batchSurface = id
                    batchAddress = event.surfaceAddress
                    batchEntered = true
                    batchLeft = false
                    batchX = event.x
                    batchY = event.y
                }
                WaylandPointerEvent.Kind.Leave -> {
                    val address = event.surfaceAddress
                    val id = surfacesByAddress[address] ?: return
                    batchSurface = id
                    batchAddress = address
                    batchLeft = true
                    batchEntered = false
                    enteredAddress = null
                    enteredSurface = null
                    // neutralize held buttons on leave (SN-1)
                    heldButtons.remove(address)?.forEach { button ->
                        if (button !in batchReleased) batchReleased.add(button)
                    }
                }
                WaylandPointerEvent.Kind.Motion -> {
                    batchX = event.x
                    batchY = event.y
                    if (batchSurface == null) {
                        batchSurface = enteredSurface
                        batchAddress = enteredAddress
                    }
                }
                WaylandPointerEvent.Kind.Button -> {
                    // button events carry no surface: they belong to the
                    // persistently entered surface (survives batch publishes)
                    val address = enteredAddress ?: return
                    val id = enteredSurface ?: return
                    if (batchSurface == null) {
                        batchSurface = id
                        batchAddress = address
                    }
                    if (event.state == 1) {
                        batchPressed.add(event.button)
                        heldButtons.getOrPut(address) { mutableSetOf() }.add(event.button)
                    } else {
                        batchReleased.add(event.button)
                        heldButtons[address]?.remove(event.button)
                    }
                }
                WaylandPointerEvent.Kind.Axis -> batchContinuous[event.axis] =
                    (batchContinuous[event.axis] ?: 0.0) + event.value
                WaylandPointerEvent.Kind.AxisValue120 -> batchValue120[event.axis] =
                    (batchValue120[event.axis] ?: 0) + event.discrete
                WaylandPointerEvent.Kind.AxisStop -> {
                    batchStopped.add(event.axis)
                    batchContinuous.remove(event.axis)
                }
                WaylandPointerEvent.Kind.AxisSource, WaylandPointerEvent.Kind.AxisDiscrete -> Unit
                WaylandPointerEvent.Kind.Frame -> publishBatchLocked()
            }
            if (!frameAware && event.kind != WaylandPointerEvent.Kind.Frame) {
                publishBatchLocked()
            }
        }
    }

    private fun publishBatchLocked() {
        val id = batchSurface ?: run {
            resetBatchLocked()
            return
        }
        onBatch(
            WaylandPointerBatch(
                surfaceId = id,
                entered = batchEntered,
                left = batchLeft,
                x = batchX,
                y = batchY,
                pressedButtons = ArrayList(batchPressed),
                releasedButtons = ArrayList(batchReleased),
                scrollValue120 = HashMap(batchValue120),
                scrollContinuous = HashMap(batchContinuous),
                scrollStopped = ArrayList(batchStopped),
            ),
        )
        resetBatchLocked()
    }

    private fun resetBatchLocked() {
        batchSurface = null
        batchAddress = null
        batchEntered = false
        batchLeft = false
        batchX = null
        batchY = null
        batchPressed.clear()
        batchReleased.clear()
        batchValue120.clear()
        batchContinuous.clear()
        batchStopped.clear()
    }

    /** Publishes an unterminated batch (leave or teardown boundary). */
    fun flush() {
        synchronized(lock) { publishBatchLocked() }
    }

    fun offer(event: WaylandKeyboardEvent) {
        synchronized(lock) {
            when (event.kind) {
                WaylandKeyboardEvent.Kind.Enter -> keyboardFocusAddress = event.surfaceAddress
                WaylandKeyboardEvent.Kind.Leave -> neutralizeKeyboardLocked(event.surfaceAddress, publish = true)
                WaylandKeyboardEvent.Kind.Key -> {
                    val focus = keyboardFocusAddress ?: return // SN-3: no focus, no routing
                    val id = surfacesByAddress[focus] ?: return
                    val held = heldKeys.getOrPut(focus) { mutableSetOf() }
                    if (event.state == 1) held.add(event.key) else held.remove(event.key)
                    onKey(
                        WaylandKeyCommand.Key(
                            surfaceId = id,
                            physicalKeycode = event.key,
                            pressed = event.state == 1,
                            time = event.time,
                        ),
                    )
                }
                WaylandKeyboardEvent.Kind.Modifiers -> {
                    val focus = keyboardFocusAddress ?: return
                    val id = surfacesByAddress[focus] ?: return
                    onKey(
                        WaylandKeyCommand.Modifiers(id, event.modsDepressed, event.modsLatched, event.modsLocked, event.group),
                    )
                }
                WaylandKeyboardEvent.Kind.Keymap, WaylandKeyboardEvent.Kind.RepeatInfo -> Unit
            }
        }
    }

    /** Neutralizes the held keys of one surface (leave, device loss, teardown). */
    private fun neutralizeKeyboardLocked(address: Long, publish: Boolean) {
        val held = heldKeys.remove(address) ?: emptySet()
        if (keyboardFocusAddress == address) keyboardFocusAddress = null
        if (!publish) return
        val id = surfacesByAddress[address] ?: return
        // the blur observation publishes even with no held keys: the runtime
        // must observe the focus loss itself
        onKey(WaylandKeyCommand.FocusLost(surfaceId = id, synthesizedReleases = held.toList()))
    }
}
