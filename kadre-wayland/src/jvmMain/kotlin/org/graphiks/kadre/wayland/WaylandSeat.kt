/**
 * Wayland seat: keyboard, pointer and touch input devices.
 *
 * Binds the `wl_seat` global, installs a `wl_seat_listener` to receive the capabilities
 * bitmask, then requests the individual input devices (`wl_keyboard`, `wl_pointer`,
 * `wl_touch`) and installs their respective event listeners.
 *
 * ### Events wired
 * | Interface   | Callback      | WindowEvent emitted                          |
 * |-------------|---------------|----------------------------------------------|
 * | wl_keyboard | enter         | Focused(true)                                |
 * | wl_keyboard | leave         | Focused(false)                               |
 * | wl_keyboard | key           | KeyInput (via WaylandKeyMapper)              |
 * | wl_pointer  | enter         | PointerEntered                               |
 * | wl_pointer  | leave         | PointerLeft                                  |
 * | wl_pointer  | motion        | PointerMoved (via WaylandMouseMapper)        |
 * | wl_pointer  | button        | MouseInput (via WaylandMouseMapper)          |
 * | wl_pointer  | axis          | MouseWheel (via WaylandMouseMapper)          |
 * | wl_touch    | down          | PointerEntered + PointerButton(Touch)        |
 * | wl_touch    | up            | PointerButton(Touch) + PointerLeft           |
 * | wl_touch    | motion        | PointerMoved(Touch)                          |
 * | wl_touch    | cancel        | PointerButton(Touch) + PointerLeft           |
 *
 * ### wl_output (scale)
 * Binds `wl_output` and installs a `wl_output_listener` to receive the `scale` integer event.
 * On each scale change, updates [WaylandSeatBinding.onScaleChanged] and emits
 * `ScaleFactorChanged`.
 *
 * ### Arena lifetime
 * All upcall stubs live in a single [Arena.ofShared] created by [installSeatListeners].
 * The returned [WaylandSeatBinding] keeps the arena and listener owners alive for the
 * Wayland session. [WaylandSeatBinding.close] releases them only after the display has
 * disconnected, so no compositor can call a freed stub.
 */
package org.graphiks.kadre.wayland
import org.graphiks.kadre.ffi.wayland.*

import org.graphiks.kadre.core.DeviceEvent
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PointerKind
import org.graphiks.kadre.core.WindowEvent
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

private val xkbStateUpdateMask: MethodHandle? by lazy {
    try {
        SymbolLookup.libraryLookup("libxkbcommon.so.0", Arena.global()).find("xkb_state_update_mask")
            .map { Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)) }
            .orElse(null)
    } catch (_: Throwable) { null }
}

private typealias RoutedWindowEventSink = (surfacePtr: Long, event: WindowEvent) -> Unit
private typealias RoutedDeviceEventSink = (event: DeviceEvent) -> Unit

/**
 * Global XKB compose state for dead-key reset, lazily initialized from
 * [WlKeyboardListener.onKeymap]. Written on the loop thread; read from
 * [WaylandWindow.resetDeadKeys] on the same thread.
 */
@Volatile
internal var waylandComposeState: Long = 0L

internal const val WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1: Int = 1

/** Native operations injected into [WaylandKeymapLoader] for deterministic ownership tests. */
internal interface WaylandKeymapOperations {
    fun mmap(fd: Int, size: Int): MemorySegment?
    fun munmap(mapping: MemorySegment, size: Int): Int
    fun close(fd: Int): Int
    fun contextNew(): MemorySegment?
    fun keymapNewFromString(context: MemorySegment, mapping: MemorySegment): MemorySegment?
    fun stateNew(keymap: MemorySegment): MemorySegment?
    fun composeTableNewFromLocale(context: MemorySegment, locale: MemorySegment): MemorySegment?
    fun composeStateNew(table: MemorySegment): MemorySegment?
    fun composeStateUnref(state: MemorySegment)
    fun composeTableUnref(table: MemorySegment)
    fun stateUnref(state: MemorySegment)
    fun keymapUnref(keymap: MemorySegment)
    fun contextUnref(context: MemorySegment)
}

private object NativeWaylandKeymapOperations : WaylandKeymapOperations {
    override fun mmap(fd: Int, size: Int): MemorySegment? =
        nativeMmap?.invokeExact(
            MemorySegment.NULL,
            size.toLong(),
            PROT_READ,
            MAP_SHARED,
            fd,
            0L,
        ) as? MemorySegment

    override fun munmap(mapping: MemorySegment, size: Int): Int {
        val munmap = checkNotNull(nativeMunmap) { "munmap is unavailable" }
        return munmap.invokeExact(mapping, size.toLong()) as Int
    }

    override fun close(fd: Int): Int {
        val close = checkNotNull(nativeClose) { "close is unavailable" }
        return close.invokeExact(fd) as Int
    }

    override fun contextNew(): MemorySegment? =
        xkbContextNew?.invokeExact(0) as? MemorySegment

    override fun keymapNewFromString(context: MemorySegment, mapping: MemorySegment): MemorySegment? =
        xkbKeymapNewFromString?.invokeExact(context, mapping, 0, 0) as? MemorySegment

    override fun stateNew(keymap: MemorySegment): MemorySegment? =
        xkbStateNew?.invokeExact(keymap) as? MemorySegment

    override fun composeTableNewFromLocale(context: MemorySegment, locale: MemorySegment): MemorySegment? =
        xkbComposeTableNewFromLocale?.invokeExact(context, locale, 0) as? MemorySegment

    override fun composeStateNew(table: MemorySegment): MemorySegment? =
        xkbComposeStateNew?.invokeExact(table, 0) as? MemorySegment

    override fun composeStateUnref(state: MemorySegment) {
        checkNotNull(xkbComposeStateUnref) { "xkb_compose_state_unref is unavailable" }
            .invokeExact(state)
    }

    override fun composeTableUnref(table: MemorySegment) {
        checkNotNull(xkbComposeTableUnref) { "xkb_compose_table_unref is unavailable" }
            .invokeExact(table)
    }

    override fun stateUnref(state: MemorySegment) {
        checkNotNull(xkbStateUnref) { "xkb_state_unref is unavailable" }
            .invokeExact(state)
    }

    override fun keymapUnref(keymap: MemorySegment) {
        checkNotNull(xkbKeymapUnref) { "xkb_keymap_unref is unavailable" }
            .invokeExact(keymap)
    }

    override fun contextUnref(context: MemorySegment) {
        checkNotNull(xkbContextUnref) { "xkb_context_unref is unavailable" }
            .invokeExact(context)
    }
}

private class WaylandXkbResources {
    var context: MemorySegment? = null
    var keymap: MemorySegment? = null
    var state: MemorySegment? = null
    var composeTable: MemorySegment? = null
    var composeState: MemorySegment? = null
}

/** Owns one installed XKB keymap and all native resources derived from it. */
internal class WaylandKeymapLoader(
    private val operations: WaylandKeymapOperations = NativeWaylandKeymapOperations,
    private val localeProvider: () -> String = { System.getenv("LANG") ?: "en_US.UTF-8" },
) : AutoCloseable {
    private var current = WaylandXkbResources()

    val stateAddress: Long get() = current.state?.address() ?: 0L
    val composeStateAddress: Long get() = current.composeState?.address() ?: 0L

    fun load(format: Int, fd: Int, size: Int) {
        val pending = WaylandXkbResources()
        var failure: Throwable? = null

        try {
            require(format == WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1) {
                "unsupported Wayland keymap format: $format"
            }
            require(fd >= 0) { "invalid Wayland keymap fd: $fd" }
            require(size > 0) { "invalid Wayland keymap size: $size" }
            loadMappedKeymap(fd, size, pending)
        } catch (caught: Throwable) {
            failure = caught
        } finally {
            if (fd >= 0) {
                try {
                    requireNativeSuccess("close", operations.close(fd))
                } catch (closeFailure: Throwable) {
                    failure = combineFailures(failure, closeFailure)
                }
            }
        }

        if (failure != null) {
            val primary = releaseResources(pending, failure)
            throw checkNotNull(primary)
        }
        val previous = current
        current = pending
        releaseResources(previous)?.let { throw it }
    }

    private fun loadMappedKeymap(fd: Int, size: Int, target: WaylandXkbResources) {
        val mapping = operations.mmap(fd, size)
            ?.takeUnless { it.address() == 0L || it.address() == MAP_FAILED_PTR }
            ?: error("mmap failed for Wayland keymap")
        var failure: Throwable? = null

        try {
            target.context = requireResource("xkb_context_new", operations.contextNew())
            target.keymap = requireResource(
                "xkb_keymap_new_from_string",
                operations.keymapNewFromString(checkNotNull(target.context), mapping.reinterpret(size.toLong())),
            )
            target.state = requireResource("xkb_state_new", operations.stateNew(checkNotNull(target.keymap)))
            Arena.ofConfined().use { localeArena ->
                val locale = localeArena.allocateFrom(localeProvider())
                target.composeTable = requireResource(
                    "xkb_compose_table_new_from_locale",
                    operations.composeTableNewFromLocale(checkNotNull(target.context), locale),
                )
            }
            target.composeState = requireResource(
                "xkb_compose_state_new",
                operations.composeStateNew(checkNotNull(target.composeTable)),
            )
        } catch (caught: Throwable) {
            failure = caught
            throw caught
        } finally {
            try {
                requireNativeSuccess("munmap", operations.munmap(mapping, size))
            } catch (unmapFailure: Throwable) {
                val primary = combineFailures(failure, unmapFailure)
                if (failure == null) throw checkNotNull(primary)
            }
        }
    }

    private fun requireResource(operation: String, resource: MemorySegment?): MemorySegment =
        resource?.takeUnless { it.address() == 0L }
            ?: error("$operation failed")

    private fun requireNativeSuccess(operation: String, returnCode: Int) {
        check(returnCode == 0) { "$operation failed with return code $returnCode" }
    }

    private fun releaseCurrent() {
        val owned = current
        current = WaylandXkbResources()
        releaseResources(owned)?.let { throw it }
    }

    private fun releaseResources(resources: WaylandXkbResources, cause: Throwable? = null): Throwable? {
        var failure = cause

        fun release(resource: MemorySegment?, operation: (MemorySegment) -> Unit) {
            if (resource == null) return
            try {
                operation(resource)
            } catch (cleanupFailure: Throwable) {
                failure = combineFailures(failure, cleanupFailure)
            }
        }

        val composeState = resources.composeState.also { resources.composeState = null }
        val composeTable = resources.composeTable.also { resources.composeTable = null }
        val state = resources.state.also { resources.state = null }
        val keymap = resources.keymap.also { resources.keymap = null }
        val context = resources.context.also { resources.context = null }
        release(composeState, operations::composeStateUnref)
        release(composeTable, operations::composeTableUnref)
        release(state, operations::stateUnref)
        release(keymap, operations::keymapUnref)
        release(context, operations::contextUnref)
        return failure
    }

    override fun close() {
        releaseCurrent()
    }
}

private fun combineFailures(primary: Throwable?, cleanup: Throwable): Throwable = when {
    primary == null -> cleanup
    primary === cleanup -> primary
    else -> primary.also { it.addSuppressed(cleanup) }
}

/** Converts a throwing loader call into a queued loop failure at the native boundary. */
internal class WaylandKeymapCallback(
    private val loader: WaylandKeymapLoader,
    private val onLoaded: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    fun onKeymap(format: Int, fd: Int, size: Int) {
        try {
            loader.load(format, fd, size)
            onLoaded()
        } catch (failure: Throwable) {
            try {
                onFailure(failure)
            } catch (_: Throwable) {
                // Never let a Kotlin exception cross the native upcall boundary.
            }
        }
    }
}

// ── wl_seat opcodes ───────────────────────────────────────────────────────────

/** wl_seat.get_pointer opcode. */
internal const val WL_SEAT_GET_POINTER: Int = 0

/** wl_seat.get_keyboard opcode. */
internal const val WL_SEAT_GET_KEYBOARD: Int = 1

/** wl_seat.get_touch opcode. */
internal const val WL_SEAT_GET_TOUCH: Int = 2

// ── wl_seat capability bits ───────────────────────────────────────────────────

private const val WL_SEAT_CAPABILITY_POINTER: Int = 1
private const val WL_SEAT_CAPABILITY_KEYBOARD: Int = 2
private const val WL_SEAT_CAPABILITY_TOUCH: Int = 4

// ── wl_output listener (scale) ────────────────────────────────────────────────

/**
 * wl_output listener that collects real geometry, mode, scale, and done events.
 *
 * The `wl_output_listener` struct has multiple callbacks in the Wayland protocol:
 *   0: geometry, 1: mode, 2: done, 3: scale.
 *
 * ### Sprint 3 (#272)
 * Prior to Sprint 3, only `scale` was tracked; geometry and mode were no-ops and
 * monitor data was synthetic. This implementation now collects all events into
 * [WaylandOutputInfo] objects and notifies consumers on `done`.
 *
 * @param outputPtr Address of the wl_output proxy.
 * @param outputVersion Protocol version.
 * @param name Output name (from wl_registry or wl_output.name in v4+).
 * @param onOutputChanged Called on each `done` event with the updated [WaylandOutputInfo].
 * @param onScaleChanged Called on each `scale` event with the new integer factor.
 */
private class WlOutputListener(
    val info: WaylandOutputInfo,
    private val onOutputChanged: ((WaylandOutputInfo) -> Unit)?,
    private val onScaleChanged: ((Int) -> Unit)?,
) {
    @Suppress("UNUSED_PARAMETER")
    fun onGeometry(
        data: MemorySegment, output: MemorySegment,
        x: Int, y: Int, physW: Int, physH: Int,
        subpixel: Int, make: MemorySegment, model: MemorySegment, transform: Int,
    ) {
        info.updateGeometry(
            x, y, physW, physH, subpixel,
            make.address(), model.address(), transform,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun onMode(
        data: MemorySegment, output: MemorySegment,
        flags: Int, width: Int, height: Int, refresh: Int,
    ) {
        info.updateMode(flags, width, height, refresh)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDone(data: MemorySegment, output: MemorySegment) {
        onOutputChanged?.invoke(info)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onScale(data: MemorySegment, output: MemorySegment, factor: Int) {
        info.scale = factor
        onScaleChanged?.invoke(factor)
    }

    /** wl_output v4: name event — vtable index 4. */
    @Suppress("UNUSED_PARAMETER")
    fun onName(data: MemorySegment, output: MemorySegment, namePtr: MemorySegment) {
        val nameStr = try { namePtr.reinterpret(128).getString(0) } catch (_: Throwable) { null }
        if (nameStr != null) info.updateName(nameStr)
    }

    /** wl_output v4: description event — vtable index 5. */
    @Suppress("UNUSED_PARAMETER")
    fun onDescription(data: MemorySegment, output: MemorySegment, descPtr: MemorySegment) { /* no-op */ }
}

// ── wl_keyboard listener ──────────────────────────────────────────────────────

/**
 * wl_keyboard listener that emits [WindowEvent.Focused], [WindowEvent.KeyInput],
 * [WindowEvent.ModifiersChanged], and [DeviceEvent.Key].
 *
 * wl_keyboard_listener vtable order:
 *   0: keymap, 1: enter, 2: leave, 3: key, 4: modifiers, 5: repeat_info.
 */
private class WlKeyboardListener(
    private val onEvent: RoutedWindowEventSink,
    onDeviceEvent: RoutedDeviceEventSink,
    private val seatPtr: Long,
    deviceFilter: WaylandDeviceFilter,
    onNativeFailure: (Throwable) -> Unit,
) : AutoCloseable {
    private var focusedSurfacePtr: Long = 0L
    private val modifiers = WaylandKeyboardModifierTracker()
    private var repeatRate: Int = 0
    private var repeatDelay: Int = 0

    private val keymapLoader = WaylandKeymapLoader()
    private val filteredDeviceEvent = deviceFilter.listener(onDeviceEvent)
    private val keymapCallback = WaylandKeymapCallback(
        loader = keymapLoader,
        onLoaded = { waylandComposeState = keymapLoader.composeStateAddress },
        onFailure = { failure ->
            waylandComposeState = keymapLoader.composeStateAddress
            onNativeFailure(failure)
        },
    )

    @Suppress("UNUSED_PARAMETER")
    fun onKeymap(
        data: MemorySegment, keyboard: MemorySegment,
        format: Int, fd: Int, size: Int,
    ) {
        keymapCallback.onKeymap(format, fd, size)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onEnter(
        data: MemorySegment, keyboard: MemorySegment,
        serial: Int, surface: MemorySegment, keys: MemorySegment,
    ) {
        focusedSurfacePtr = surface.address()
        val focusChanged = WaylandFocusState.addSeatFocus(focusedSurfacePtr, seatPtr)
        modifiers.mapFocusGained(waylandPressedKeysFromArray(keys)).forEach { event ->
            if (event !is WindowEvent.Focused || focusChanged) {
                onEvent(focusedSurfacePtr, event)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLeave(
        data: MemorySegment, keyboard: MemorySegment,
        serial: Int, surface: MemorySegment,
    ) {
        val surfacePtr = surface.address().takeIf { it != 0L } ?: focusedSurfacePtr
        val focusChanged = WaylandFocusState.removeSeatFocus(surfacePtr, seatPtr)
        modifiers.mapFocusLost().forEach { event ->
            if (event !is WindowEvent.Focused || focusChanged) {
                onEvent(surfacePtr, event)
            }
        }
        if (focusedSurfacePtr == surfacePtr) focusedSurfacePtr = 0L
    }

    @Suppress("UNUSED_PARAMETER")
    fun onKey(
        data: MemorySegment, keyboard: MemorySegment,
        serial: Int, time: Int, key: Int, state: Int,
    ) {
        modifiers.mapKey(keycode = key, state = state, xkbStatePtr = keymapLoader.stateAddress).forEach { event ->
            onEvent(focusedSurfacePtr, event)
        }
        filteredDeviceEvent(DeviceEvent.Key(linuxKeycodeToPhysicalKey(key), waylandKeyStateToKeyState(state)))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onModifiers(
        data: MemorySegment, keyboard: MemorySegment,
        serial: Int, modsDepressed: Int, modsLatched: Int, modsLocked: Int, group: Int,
    ) {
        // Keep the xkb state in sync so xkb_state_key_get_utf8 yields shifted /
        // dead-key characters for subsequent key events.
        if (keymapLoader.stateAddress != 0L) {
            try {
                xkbStateUpdateMask?.invokeExact(
                    MemorySegment.ofAddress(keymapLoader.stateAddress),
                    modsDepressed, modsLatched, modsLocked,
                    0, 0, group,
                ) as Int
            } catch (_: Throwable) {
            }
        }
        modifiers.mapModifiers(modsDepressed = modsDepressed).forEach { event ->
            onEvent(focusedSurfacePtr, event)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRepeatInfo(data: MemorySegment, keyboard: MemorySegment, rate: Int, delay: Int) {
        repeatRate = rate
        repeatDelay = delay
    }

    override fun close() {
        if (waylandComposeState == keymapLoader.composeStateAddress) {
            waylandComposeState = 0L
        }
        keymapLoader.close()
    }
}

// ── wl_pointer listener ───────────────────────────────────────────────────────

/**
 * wl_pointer listener that emits pointer/mouse events.
 *
 * wl_pointer_listener vtable order (core protocol):
 *   0: enter, 1: leave, 2: motion, 3: button, 4: axis,
 *   5: frame (v5+), 6: axis_source, 7: axis_stop, 8: axis_discrete.
 *
 * We install 9 entries (enter→axis_discrete) so wl_pointer remains safe when
 * the seat is bound at a modern protocol version.
 */
private class WlPointerListener(
    private val onEvent: RoutedWindowEventSink,
    private val seatPtr: Long,
) {
    private var lastPosition: PhysicalPosition<Double> = PhysicalPosition(0.0, 0.0)
    private var focusedSurfacePtr: Long = 0L

    @Suppress("UNUSED_PARAMETER")
    fun onEnter(
        data: MemorySegment, pointer: MemorySegment,
        serial: Int, surface: MemorySegment, xFixed: Int, yFixed: Int,
    ) {
        WaylandPointerState.updateSeat(seatPtr)
        focusedSurfacePtr = surface.address()
        WaylandPointerState.enterPointer(pointer.address(), focusedSurfacePtr, serial)
        if (!WaylandPointerState.isCursorVisible(focusedSurfacePtr)) {
            WaylandPointerState.hideCursorForSurface(focusedSurfacePtr)
        }
        lastPosition = PhysicalPosition(wlFixedToDouble(xFixed), wlFixedToDouble(yFixed))
        onEvent(focusedSurfacePtr, WindowEvent.PointerEntered(null, lastPosition, primary = true, kind = PointerKind.Mouse))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLeave(
        data: MemorySegment, pointer: MemorySegment,
        serial: Int, surface: MemorySegment,
    ) {
        val surfacePtr = surface.address().takeIf { it != 0L } ?: focusedSurfacePtr
        WaylandPointerState.leaveSurface(surfacePtr)
        onEvent(surfacePtr, WindowEvent.PointerLeft(null, lastPosition, primary = true, kind = PointerKind.Mouse))
        if (focusedSurfacePtr == surfacePtr) focusedSurfacePtr = 0L
    }

    @Suppress("UNUSED_PARAMETER")
    fun onMotion(
        data: MemorySegment, pointer: MemorySegment,
        time: Int, xFixed: Int, yFixed: Int,
    ) {
        val event = mapWaylandPointerMotion(xFixed, yFixed)
        lastPosition = event.position
        onEvent(focusedSurfacePtr, event)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onButton(
        data: MemorySegment, pointer: MemorySegment,
        serial: Int, time: Int, button: Int, state: Int,
    ) {
        WaylandPointerState.updateSeat(seatPtr)
        WaylandPointerState.recordButton(serial, button, state)
        onEvent(focusedSurfacePtr, mapWaylandPointerButton(button, state, lastPosition))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAxis(
        data: MemorySegment, pointer: MemorySegment,
        time: Int, axis: Int, valueFixed: Int,
    ) {
        onEvent(focusedSurfacePtr, mapWaylandPointerAxis(axis, valueFixed))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onFrame(data: MemorySegment, pointer: MemorySegment) { /* no-op */ }

    @Suppress("UNUSED_PARAMETER")
    fun onAxisSource(data: MemorySegment, pointer: MemorySegment, axisSource: Int) { /* no-op */ }

    @Suppress("UNUSED_PARAMETER")
    fun onAxisStop(data: MemorySegment, pointer: MemorySegment, time: Int, axis: Int) { /* no-op */ }

    @Suppress("UNUSED_PARAMETER")
    fun onAxisDiscrete(data: MemorySegment, pointer: MemorySegment, axis: Int, discrete: Int) { /* no-op */ }
}

// ── wl_touch listener ─────────────────────────────────────────────────────────

/**
 * wl_touch listener that emits pointer events.
 *
 * wl_touch_listener vtable order:
 *   0: down, 1: up, 2: motion, 3: frame, 4: cancel.
 *
 * Active contact positions are tracked in [lastPositions] so that [TouchPhase.Ended]
 * events can carry the last known location (wl_touch.up does not include coordinates).
 */
private class WlTouchListener(
    private val onEvent: RoutedWindowEventSink,
) {
    private data class TouchContact(
        val surfacePtr: Long,
        val position: Pair<Double, Double>,
    )

    /** Last known surface and position per touch id. */
    private val contacts = mutableMapOf<Int, TouchContact>()
    private var primaryTouchId: Int? = null

    @Suppress("UNUSED_PARAMETER")
    fun onDown(
        data: MemorySegment, touch: MemorySegment,
        serial: Int, time: Int, surface: MemorySegment, id: Int, xFixed: Int, yFixed: Int,
    ) {
        val x = wlFixedToDouble(xFixed)
        val y = wlFixedToDouble(yFixed)
        val surfacePtr = surface.address()
        contacts[id] = TouchContact(surfacePtr, x to y)
        if (primaryTouchId == null) primaryTouchId = id
        val primary = primaryTouchId == id
        mapWaylandTouchDown(id, xFixed, yFixed, primary).forEach { event -> onEvent(surfacePtr, event) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUp(
        data: MemorySegment, touch: MemorySegment,
        serial: Int, time: Int, id: Int,
    ) {
        val contact = contacts.remove(id) ?: return
        val (lx, ly) = contact.position
        val primary = primaryTouchId == id
        if (contacts.isEmpty()) primaryTouchId = null
        // Build terminal events with the last known location (wl_touch.up has no coords).
        mapWaylandTouchUp(id, PhysicalPosition(lx, ly), primary).forEach { event -> onEvent(contact.surfacePtr, event) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onMotion(
        data: MemorySegment, touch: MemorySegment,
        time: Int, id: Int, xFixed: Int, yFixed: Int,
    ) {
        val x = wlFixedToDouble(xFixed)
        val y = wlFixedToDouble(yFixed)
        val surfacePtr = contacts[id]?.surfacePtr ?: return
        contacts[id] = TouchContact(surfacePtr, x to y)
        onEvent(surfacePtr, mapWaylandTouchMotion(id, xFixed, yFixed, primaryTouchId == id))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onFrame(data: MemorySegment, touch: MemorySegment) { /* no-op — we dispatch eagerly */ }

    @Suppress("UNUSED_PARAMETER")
    fun onCancel(data: MemorySegment, touch: MemorySegment) {
        // Cancel ALL active contacts.
        for ((id, contact) in contacts.toMap()) {
            val (lx, ly) = contact.position
            mapWaylandTouchCancel(id, PhysicalPosition(lx, ly), primaryTouchId == id)
                .forEach { event -> onEvent(contact.surfacePtr, event) }
        }
        contacts.clear()
        primaryTouchId = null
    }

    @Suppress("UNUSED_PARAMETER")
    fun onShape(data: MemorySegment, touch: MemorySegment, id: Int, major: Int, minor: Int) { /* no-op */ }

    @Suppress("UNUSED_PARAMETER")
    fun onOrientation(data: MemorySegment, touch: MemorySegment, id: Int, orientation: Int) { /* no-op */ }
}

// ── WaylandSeatBinding ────────────────────────────────────────────────────────

// ── wl_seat listener (capabilities) ──────────────────────────────────────────

/**
 * wl_seat listener used to receive the capabilities bitmask before requesting input devices.
 *
 * wl_seat_listener vtable order:
 *   0: capabilities(data, seat, capabilities: uint32)
 *   1: name(data, seat, name: const char*)
 */
private class WlSeatCapabilitiesListener {
    /** Bitmask of WL_SEAT_CAPABILITY_* bits, set by the compositor's capabilities event. */
    @Volatile
    var capabilities: Int = 0

    @Suppress("UNUSED_PARAMETER")
    fun onCapabilities(data: MemorySegment, seat: MemorySegment, caps: Int) {
        capabilities = caps
    }

    @Suppress("UNUSED_PARAMETER")
    fun onName(data: MemorySegment, seat: MemorySegment, name: MemorySegment) { /* no-op */ }
}

/**
 * Returns true if [capBit] is set in [caps].
 *
 * Extracted as a standalone function so it can be unit-tested without FFM.
 */
internal fun seatHasCapability(caps: Int, capBit: Int): Boolean = (caps and capBit) != 0

// ── WaylandSeatBinding ────────────────────────────────────────────────────────

/**
 * Holds all listeners installed for the seat / output.
 * A strong reference prevents the arena (and the upcall stubs) from being GC'd.
 */
internal class WaylandSeatBinding internal constructor(
    arena: Arena,
    private val closeArena: () -> Unit = arena::close,
) : AutoCloseable {
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Keyboard/XKB owner, released after the display has disconnected. */
    internal var keyboardOwner: AutoCloseable? = null

    /** DnD handler, kept alive by the binding. */
    @JvmField
    internal var dnd: WaylandDragAndDrop? = null

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        seatBindings.remove(this)
        val ownedKeyboard = keyboardOwner
        keyboardOwner = null
        dnd = null
        runWaylandCleanup(
            primary = null,
            cleanupActions = listOf(
                { ownedKeyboard?.close(); Unit },
                closeArena,
            ),
        )
    }
}

/** Keeps seat bindings alive until their owning event loop disconnects. */
private val seatBindings = mutableListOf<WaylandSeatBinding>()

/**
 * Binds `wl_seat` and `wl_output` globals and installs all input listeners.
 *
 * Called from the event-loop init sequence after globals have been discovered.
 * Emits all input events via [onEvent]. Scale changes update [onScaleChanged].
 *
 * ### Capabilities check (BLOQUANT 1)
 * Before requesting any sub-device, this function installs a `wl_seat_listener` and
 * performs a `wl_display_roundtrip` to receive the `capabilities` event from the
 * compositor. Only the sub-devices whose capability bit is set in the bitmask are
 * then requested (`WL_SEAT_CAPABILITY_KEYBOARD=2`, `POINTER=1`, `TOUCH=4`).
 * Calling e.g. `wl_seat_get_touch` on a seat without touch capability is undefined
 * behaviour and can cause a SIGSEGV.
 *
 * Both `seatPtr` and `outputPtr` may be 0 if the respective global was absent;
 * this function is tolerant of 0-pointers.
 *
 * @param displayPtr  Address of the connected wl_display* (needed for the roundtrip).
 * @param seatPtr     Address of the already-bound wl_seat* (or 0).
 * @param outputPtr   Address of the already-bound wl_output* (or 0).
 * @param seatVersion Version of the bound wl_seat.
 * @param outputVersion Version of the bound wl_output.
 * @param onEvent     Sink for all emitted [WindowEvent]s (routed to the event queue).
 * @param onScaleChanged Callback invoked with the new integer scale factor.
 */
internal fun installSeatListeners(
    displayPtr: Long,
    seatPtr: Long,
    outputPtr: Long,
    seatVersion: Int,
    outputVersion: Int,
    onEvent: RoutedWindowEventSink,
    onDeviceEvent: RoutedDeviceEventSink = {},
    onScaleChanged: (Int) -> Unit,
    dataDeviceManagerPtr: Long = 0L,
    deviceFilter: WaylandDeviceFilter = WaylandDeviceFilter(),
    onNativeFailure: (Throwable) -> Unit = {},
    /** Callback invoked on each wl_output.done with the updated [WaylandOutputInfo]. */
    onOutputChanged: ((WaylandOutputInfo) -> Unit)? = null,
    /** Pre-allocated output info objects keyed by wl_output proxy address. */
    outputInfos: MutableMap<Long, WaylandOutputInfo>? = null,
): WaylandSeatBinding? {
    val addListener = wlProxyAddListener ?: return null
    val arena = Arena.ofShared()
    val binding = WaylandSeatBinding(arena)
    val lookup = MethodHandles.lookup()
    val ptr = ValueLayout.ADDRESS.byteSize()

    // Track whether at least one add_listener call succeeded; if so the arena must NOT
    // be closed on error (BLOQUANT 3): the compositor might already hold the stub pointers.
    var anyListenerInstalled = false

    try {
        if (seatPtr != 0L) {
            val seat = MemorySegment.ofAddress(seatPtr)

            // ── Step 1: install wl_seat_listener and roundtrip to get capabilities ──
            val capsListener = WlSeatCapabilitiesListener()
            val capsStub = upcallStub(
                lookup.findVirtual(WlSeatCapabilitiesListener::class.java, "onCapabilities",
                    MethodType.methodType(Void.TYPE,
                        MemorySegment::class.java, MemorySegment::class.java,
                        Int::class.javaPrimitiveType,
                    )).bindTo(capsListener),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                arena,
            )
            val nameStub = upcallStub(
                lookup.findVirtual(WlSeatCapabilitiesListener::class.java, "onName",
                    MethodType.methodType(Void.TYPE,
                        MemorySegment::class.java, MemorySegment::class.java,
                        MemorySegment::class.java,
                    )).bindTo(capsListener),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                arena,
            )
            val seatVtable = arena.allocate(ptr * 2)
            seatVtable.set(ValueLayout.ADDRESS, 0L,  capsStub)
            seatVtable.set(ValueLayout.ADDRESS, ptr, nameStub)
            val seatListenerRc = runCatching {
                addListener.invokeExact(seat, seatVtable, MemorySegment.NULL) as Int
            }.getOrDefault(-1)
            if (seatListenerRc == 0) {
                anyListenerInstalled = true
                // Roundtrip so the compositor delivers the capabilities event.
                runCatching {
                    wlDisplayRoundtrip?.invokeExact(MemorySegment.ofAddress(displayPtr)) as Int
                }
            }

            val caps = capsListener.capabilities

            // ── Step 2: bind sub-devices based on the capability bitmask ──────────

            // wl_keyboard (capability bit 2)
            if (seatHasCapability(caps, WL_SEAT_CAPABILITY_KEYBOARD)) {
                val keyboardIface = wlKeyboardInterface
                val getKeyboard = wlSeatGetKeyboard
                if (keyboardIface != null && getKeyboard != null) {
                    val kbSeg = runCatching {
                        getKeyboard.invokeExact(
                            seat, WL_SEAT_GET_KEYBOARD, keyboardIface, seatVersion, 0, MemorySegment.NULL,
                        ) as MemorySegment
                    }.getOrNull()
                    if (kbSeg != null && kbSeg.address() != 0L) {
                        binding.keyboardOwner = installKeyboardListener(
                            kbSeg, addListener, lookup, arena, seatPtr, onEvent,
                            onDeviceEvent, deviceFilter, onNativeFailure,
                        )
                        anyListenerInstalled = true
                    }
                }
            }

            // wl_pointer (capability bit 1)
            if (seatHasCapability(caps, WL_SEAT_CAPABILITY_POINTER)) {
                val pointerIface = wlPointerInterface
                val getPointer = wlSeatGetPointer
                if (pointerIface != null && getPointer != null) {
                    val ptrSeg = runCatching {
                        getPointer.invokeExact(
                            seat, WL_SEAT_GET_POINTER, pointerIface, seatVersion, 0, MemorySegment.NULL,
                        ) as MemorySegment
                    }.getOrNull()
                    if (ptrSeg != null && ptrSeg.address() != 0L) {
                        installPointerListener(ptrSeg, addListener, lookup, arena, seatPtr, onEvent)
                        anyListenerInstalled = true
                    }
                }
            }

            // wl_touch (capability bit 4)
            if (seatHasCapability(caps, WL_SEAT_CAPABILITY_TOUCH)) {
                val touchIface = wlTouchInterface
                val getTouch = wlSeatGetTouch
                if (touchIface != null && getTouch != null) {
                    val touchSeg = runCatching {
                        getTouch.invokeExact(
                            seat, WL_SEAT_GET_TOUCH, touchIface, seatVersion, 0, MemorySegment.NULL,
                        ) as MemorySegment
                    }.getOrNull()
                    if (touchSeg != null && touchSeg.address() != 0L) {
                        installTouchListener(touchSeg, addListener, lookup, arena, onEvent)
                        anyListenerInstalled = true
                    }
                }
            }
        }

        // ── wl_output (geometry, mode, scale) ─────────────────────────────────
        if (outputPtr != 0L && outputVersion >= 2) {
            val output = MemorySegment.ofAddress(outputPtr)
            val outputInfo = WaylandOutputInfo(
                outputPtr = outputPtr,
                name = null,
                outputVersion = outputVersion,
            )
            outputInfos?.put(outputPtr, outputInfo)
            installOutputListener(
                output, addListener, lookup, arena,
                outputInfo = outputInfo,
                onOutputChanged = onOutputChanged,
                onScaleChanged = onScaleChanged,
            )
            anyListenerInstalled = true
        }

        // ── wl_data_device (DnD) ─────────────────────────────────────────────
        if (dataDeviceManagerPtr != 0L && seatPtr != 0L) {
            val getDataDevice = wlDataDeviceManagerGetDataDevice
            val dataDeviceIface = wlDataDeviceInterface
            if (getDataDevice != null && dataDeviceIface != null) {
                val dataDevice = runCatching {
                    getDataDevice.invokeExact(
                        MemorySegment.ofAddress(dataDeviceManagerPtr),
                        1,                            // opcode: get_data_device
                        dataDeviceIface,
                        WL_DATA_DEVICE_MANAGER_VERSION,
                        0,                            // flags
                        MemorySegment.ofAddress(seatPtr),
                        MemorySegment.NULL,            // new_id = NULL
                    ) as MemorySegment
                }.getOrNull()
                if (dataDevice != null && dataDevice.address() != 0L) {
                    val dnd = WaylandDragAndDrop(dataDevice.address(), displayPtr, onEvent)
                    dnd.installListener(arena, addListener)
                    binding.dnd = dnd
                    anyListenerInstalled = true
                }
            }
        }

        seatBindings.add(binding)
        return binding
    } catch (t: Throwable) {
        System.err.println("[kadre-wayland] installSeatListeners failed: $t")
        // BLOQUANT 3: do NOT close the arena if any listener has already been registered
        // with the compositor — its stubs are already referenced by the compositor side and
        // closing the arena would free them, causing a SIGSEGV on the next event dispatch.
        if (anyListenerInstalled) {
            seatBindings.add(binding)
            return binding
        }
        runCatching { binding.close() }
        return null
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

private fun installKeyboardListener(
    keyboard: MemorySegment,
    addListener: java.lang.invoke.MethodHandle,
    lookup: MethodHandles.Lookup,
    arena: Arena,
    seatPtr: Long,
    onEvent: RoutedWindowEventSink,
    onDeviceEvent: RoutedDeviceEventSink,
    deviceFilter: WaylandDeviceFilter,
    onNativeFailure: (Throwable) -> Unit,
): AutoCloseable {
    val listener = WlKeyboardListener(onEvent, onDeviceEvent, seatPtr, deviceFilter, onNativeFailure)
    val ptr = ValueLayout.ADDRESS.byteSize()

    // vtable: keymap, enter, leave, key, modifiers, repeat_info — 6 entries.
    val keymapStub = upcallStub(
        lookup.findVirtual(WlKeyboardListener::class.java, "onKeymap",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val enterStub = upcallStub(
        lookup.findVirtual(WlKeyboardListener::class.java, "onEnter",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, MemorySegment::class.java, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
    val leaveStub = upcallStub(
        lookup.findVirtual(WlKeyboardListener::class.java, "onLeave",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
        arena,
    )
    val keyStub = upcallStub(
        lookup.findVirtual(WlKeyboardListener::class.java, "onKey",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val modsStub = upcallStub(
        lookup.findVirtual(WlKeyboardListener::class.java, "onModifiers",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val repeatInfoStub = upcallStub(
        lookup.findVirtual(WlKeyboardListener::class.java, "onRepeatInfo",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val vtable = arena.allocate(ptr * 6)
    vtable.set(ValueLayout.ADDRESS, 0L,       keymapStub)
    vtable.set(ValueLayout.ADDRESS, ptr,      enterStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 2,  leaveStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 3,  keyStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 4,  modsStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 5,  repeatInfoStub)
    runCatching { addListener.invokeExact(keyboard, vtable, MemorySegment.NULL) as Int }
    return listener
}

private fun installPointerListener(
    pointer: MemorySegment,
    addListener: java.lang.invoke.MethodHandle,
    lookup: MethodHandles.Lookup,
    arena: Arena,
    seatPtr: Long,
    onEvent: RoutedWindowEventSink,
) {
    val listener = WlPointerListener(onEvent, seatPtr)
    val ptr = ValueLayout.ADDRESS.byteSize()

    val enterStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onEnter",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val leaveStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onLeave",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
        arena,
    )
    val motionStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onMotion",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val buttonStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onButton",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val axisStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onAxis",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val frameStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onFrame",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
    val axisSourceStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onAxisSource",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        arena,
    )
    val axisStopStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onAxisStop",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val axisDiscreteStub = upcallStub(
        lookup.findVirtual(WlPointerListener::class.java, "onAxisDiscrete",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val vtable = arena.allocate(ptr * 9)
    vtable.set(ValueLayout.ADDRESS, 0L,       enterStub)
    vtable.set(ValueLayout.ADDRESS, ptr,      leaveStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 2,  motionStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 3,  buttonStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 4,  axisStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 5,  frameStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 6,  axisSourceStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 7,  axisStopStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 8,  axisDiscreteStub)
    runCatching { addListener.invokeExact(pointer, vtable, MemorySegment.NULL) as Int }
}

private fun installTouchListener(
    touch: MemorySegment,
    addListener: java.lang.invoke.MethodHandle,
    lookup: MethodHandles.Lookup,
    arena: Arena,
    onEvent: RoutedWindowEventSink,
) {
    val listener = WlTouchListener(onEvent)
    val ptr = ValueLayout.ADDRESS.byteSize()

    // vtable: down, up, motion, frame, cancel, shape, orientation — 7 entries for wl_touch v6+.
    val downStub = upcallStub(
        lookup.findVirtual(WlTouchListener::class.java, "onDown",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val upStub = upcallStub(
        lookup.findVirtual(WlTouchListener::class.java, "onUp",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val motionStub = upcallStub(
        lookup.findVirtual(WlTouchListener::class.java, "onMotion",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val frameStub = upcallStub(
        lookup.findVirtual(WlTouchListener::class.java, "onFrame",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
    val cancelStub = upcallStub(
        lookup.findVirtual(WlTouchListener::class.java, "onCancel",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
    val shapeStub = upcallStub(
        lookup.findVirtual(WlTouchListener::class.java, "onShape",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val orientationStub = upcallStub(
        lookup.findVirtual(WlTouchListener::class.java, "onOrientation",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val vtable = arena.allocate(ptr * 7)
    vtable.set(ValueLayout.ADDRESS, 0L,       downStub)
    vtable.set(ValueLayout.ADDRESS, ptr,      upStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 2,  motionStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 3,  frameStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 4,  cancelStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 5,  shapeStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 6,  orientationStub)
    runCatching { addListener.invokeExact(touch, vtable, MemorySegment.NULL) as Int }
}

private fun installOutputListener(
    output: MemorySegment,
    addListener: java.lang.invoke.MethodHandle,
    lookup: MethodHandles.Lookup,
    arena: Arena,
    outputInfo: WaylandOutputInfo,
    onOutputChanged: ((WaylandOutputInfo) -> Unit)?,
    onScaleChanged: ((Int) -> Unit)?,
) {
    val listener = WlOutputListener(outputInfo, onOutputChanged, onScaleChanged)
    val ptr = ValueLayout.ADDRESS.byteSize()

    // vtable: geometry, mode, done, scale, name, description — 6 entries (wl_output v4).
    val geometryStub = upcallStub(
        lookup.findVirtual(WlOutputListener::class.java, "onGeometry",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        arena,
    )
    val modeStub = upcallStub(
        lookup.findVirtual(WlOutputListener::class.java, "onMode",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
    val doneStub = upcallStub(
        lookup.findVirtual(WlOutputListener::class.java, "onDone",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
    val scaleStub = upcallStub(
        lookup.findVirtual(WlOutputListener::class.java, "onScale",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        arena,
    )
    val nameStub = upcallStub(
        lookup.findVirtual(WlOutputListener::class.java, "onName",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
    val descriptionStub = upcallStub(
        lookup.findVirtual(WlOutputListener::class.java, "onDescription",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
    val vtable = arena.allocate(ptr * 6)
    vtable.set(ValueLayout.ADDRESS, 0L,       geometryStub)
    vtable.set(ValueLayout.ADDRESS, ptr,      modeStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 2,  doneStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 3,  scaleStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 4,  nameStub)
    vtable.set(ValueLayout.ADDRESS, ptr * 5,  descriptionStub)
    runCatching { addListener.invokeExact(output, vtable, MemorySegment.NULL) as Int }
}
