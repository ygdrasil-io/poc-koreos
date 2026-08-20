/**
 * Wayland text-input (IME) integration via zwp_text_input_v3.
 *
 * ### Protocol flow
 * 1. `zwp_text_input_manager_v3.create_text_input` → get a `zwp_text_input_v3` proxy per seat.
 * 2. Listeners on `zwp_text_input_v3` receive compositor-driven IME events.
 * 3. `done(serial)` signals the end of a batch — stored events are dispatched.
 * 4. Window IME methods (`setImeAllowed`, `setImeCursorArea`, `setImePurpose`)
 *    call the corresponding `zwp_text_input_v3` requests.
 *
 * ### Thread safety
 * All Wayland protocol calls happen on the loop thread.
 * The state holder ([WaylandTextInput]) is effectively single-threaded.
 */
package org.graphiks.kadre.wayland

import org.graphiks.kffi.wayland.*
import org.graphiks.kffi.wayland.generated.zwp_text_input_v3_interface
import org.graphiks.kadre.core.ImePurpose
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.WindowEvent
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.atomic.AtomicBoolean

// ── zwp_text_input_v3 request opcodes ─────────────────────────────────────────

internal const val ZWP_TEXT_INPUT_V3_DESTROY: Int = 0
internal const val ZWP_TEXT_INPUT_V3_ENABLE: Int = 1
internal const val ZWP_TEXT_INPUT_V3_DISABLE: Int = 2
internal const val ZWP_TEXT_INPUT_V3_SET_CONTENT_TYPE: Int = 5
internal const val ZWP_TEXT_INPUT_V3_SET_CURSOR_RECTANGLE: Int = 6
internal const val ZWP_TEXT_INPUT_V3_COMMIT: Int = 7

// zwp_text_input_manager_v3 request opcodes
private const val ZWP_TEXT_INPUT_MANAGER_V3_DESTROY: Int = 0
private const val ZWP_TEXT_INPUT_MANAGER_V3_CREATE_TEXT_INPUT: Int = 1

// ── zwp_text_input_v3 input_purpose enum values ───────────────────────────────

private const val ZWP_TEXT_INPUT_PURPOSE_DEFAULT: Int = 0
private const val ZWP_TEXT_INPUT_PURPOSE_PASSWORD: Int = 8
private const val ZWP_TEXT_INPUT_PURPOSE_TERMINAL: Int = 13

internal interface WaylandTextInputRequestOperations {
    fun marshalVoid(proxyPtr: Long, opcode: Int, version: Int, flags: Int)
    fun marshalTwoUint(
        proxyPtr: Long,
        opcode: Int,
        version: Int,
        flags: Int,
        first: Int,
        second: Int,
    )
    fun marshalFourInt(
        proxyPtr: Long,
        opcode: Int,
        version: Int,
        flags: Int,
        first: Int,
        second: Int,
        third: Int,
        fourth: Int,
    )
    fun flush(displayPtr: Long): Int
}

internal interface WaylandTextInputListenerRegistration : AutoCloseable {
    fun install(proxyPtr: Long): Int
}

internal interface WaylandTextInputCreationOperations : WaylandTextInputRequestOperations {
    fun createTextInput(managerPtr: Long, seatPtr: Long): Long
    fun getVersion(proxyPtr: Long): Int
    fun createListenerRegistration(
        onEvent: (surfacePtr: Long, event: WindowEvent) -> Unit,
        onNativeFailure: (Throwable) -> Unit,
    ): WaylandTextInputListenerRegistration
}

private object NativeWaylandTextInputRequestOperations : WaylandTextInputRequestOperations {
    override fun marshalVoid(proxyPtr: Long, opcode: Int, version: Int, flags: Int) {
        checkNotNull(wlProxyMarshalFlagsVoid) { "wl_proxy_marshal_flags(void) unavailable" }
            .invokeExact(
                MemorySegment.ofAddress(proxyPtr),
                opcode,
                MemorySegment.NULL,
                version,
                flags,
            )
    }

    override fun marshalTwoUint(
        proxyPtr: Long,
        opcode: Int,
        version: Int,
        flags: Int,
        first: Int,
        second: Int,
    ) {
        checkNotNull(wlProxyMarshalFlagsTwoUint) { "wl_proxy_marshal_flags(two uint) unavailable" }
            .invokeExact(
                MemorySegment.ofAddress(proxyPtr),
                opcode,
                MemorySegment.NULL,
                version,
                flags,
                first,
                second,
            )
    }

    override fun marshalFourInt(
        proxyPtr: Long,
        opcode: Int,
        version: Int,
        flags: Int,
        first: Int,
        second: Int,
        third: Int,
        fourth: Int,
    ) {
        checkNotNull(wlProxyMarshalFlagsFourInt) { "wl_proxy_marshal_flags(four int) unavailable" }
            .invokeExact(
                MemorySegment.ofAddress(proxyPtr),
                opcode,
                MemorySegment.NULL,
                version,
                flags,
                first,
                second,
                third,
                fourth,
            )
    }

    override fun flush(displayPtr: Long): Int =
        checkNotNull(wlDisplayFlush) { "wl_display_flush unavailable" }
            .invokeExact(MemorySegment.ofAddress(displayPtr)) as Int
}

private object NativeWaylandTextInputCreationOperations : WaylandTextInputCreationOperations,
    WaylandTextInputRequestOperations by NativeWaylandTextInputRequestOperations {
    override fun createTextInput(managerPtr: Long, seatPtr: Long): Long {
        val handle = checkNotNull(zwpInputManagerV3CreateTextInput) {
            "zwp_text_input_manager_v3.create_text_input not available"
        }
        val proxy = handle.invokeExact(
            MemorySegment.ofAddress(managerPtr),
            ZWP_TEXT_INPUT_MANAGER_V3_CREATE_TEXT_INPUT,
            zwp_text_input_v3_interface,
            1,
            0,
            MemorySegment.NULL,
            MemorySegment.ofAddress(seatPtr),
        ) as MemorySegment
        return proxy.address()
    }

    override fun getVersion(proxyPtr: Long): Int =
        checkNotNull(wlProxyGetVersion) { "wl_proxy_get_version not available for text input" }
            .invokeExact(MemorySegment.ofAddress(proxyPtr)) as Int

    override fun createListenerRegistration(
        onEvent: (surfacePtr: Long, event: WindowEvent) -> Unit,
        onNativeFailure: (Throwable) -> Unit,
    ): WaylandTextInputListenerRegistration =
        createNativeTextInputListenerRegistration(onEvent, onNativeFailure)
}

// ── State tracking ───────────────────────────────────────────────────────────

/**
 * Holds the per-seat `zwp_text_input_v3` proxy and the current IME state.
 *
 * Only one seat is typically present; this single instance is sufficient
 * for the common case. The pointer is 0 when the protocol extension is
 * unavailable on the compositor.
 */
internal object WaylandTextInput {
    private var owner: WaylandTextInputBinding? = null

    /** Address of the `zwp_text_input_v3` proxy (0 if not available). */
    var textInputPtr: Long = 0L

    /** The display pointer needed for flush after requests. */
    var displayPtr: Long = 0L

    /** Version of the bound zwp_text_input_v3. */
    var version: Int = 1

    /**
     * Current focused surface (set by `enter`, cleared by `leave`).
     * 0 when text input is not active on any of our surfaces.
     */
    var focusedSurfacePtr: Long = 0L

    /** Whether IME is currently enabled for the focused surface. */
    var imeEnabled: Boolean = false

    /** Event sink routed from the event loop to the window event queue. */
    var onImeEvent: ((surfacePtr: Long, event: WindowEvent) -> Unit)? = null

    fun prepareForCreation() {
        check(owner == null) { "Wayland text input is already owned" }
        resetState()
    }

    fun attach(
        binding: WaylandTextInputBinding,
        proxyPtr: Long,
        display: Long,
        proxyVersion: Int,
        eventSink: (surfacePtr: Long, event: WindowEvent) -> Unit,
    ) {
        check(owner == null) { "Wayland text input is already owned" }
        owner = binding
        textInputPtr = proxyPtr
        displayPtr = display
        version = proxyVersion
        onImeEvent = eventSink
    }

    fun detach(binding: WaylandTextInputBinding) {
        if (owner === binding) {
            owner = null
            resetState()
        }
    }

    fun resetUnownedState() {
        if (owner == null) resetState()
    }

    fun resetForTest() {
        owner = null
        resetState()
    }

    private fun resetState() {
        textInputPtr = 0L
        displayPtr = 0L
        version = 1
        focusedSurfacePtr = 0L
        imeEnabled = false
        onImeEvent = null
    }
}

internal class WaylandTextInputBinding(
    private val proxyPtr: Long,
    private val proxyVersion: Int,
    private val listenerLease: WaylandNativeListenerLease,
    private val operations: WaylandTextInputRequestOperations,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        WaylandTextInput.detach(this)
        operations.marshalVoid(
            proxyPtr,
            ZWP_TEXT_INPUT_V3_DESTROY,
            proxyVersion,
            1, // flags: destroy proxy
        )
        listenerLease.releaseAfterProxyDestroyed()
    }
}

// ── Factory ───────────────────────────────────────────────────────────────────

/**
 * Creates a `zwp_text_input_v3` from the bound manager and installs
 * its event listener.
 *
 * @param managerPtr  Address of the bound `zwp_text_input_manager_v3`.
 * @param display     Display pointer for flush after requests.
 * @param onEvent     Sink for IME events routed to window event queues.
 */
internal fun createTextInput(
    managerPtr: Long,
    seatPtr: Long,
    display: Long,
    onEvent: (surfacePtr: Long, event: WindowEvent) -> Unit,
    nativeListenerLifetime: WaylandNativeListenerLifetime,
    onNativeFailure: (Throwable) -> Unit = {},
    failOnNativeError: Boolean = false,
    operations: WaylandTextInputCreationOperations = NativeWaylandTextInputCreationOperations,
): WaylandTextInputBinding? {
    try {
        WaylandTextInput.prepareForCreation()
    } catch (failure: Throwable) {
        if (failOnNativeError) throw failure
        return null
    }

    var proxyPtr = 0L
    var proxyVersion = 1
    var listener: WaylandTextInputListenerRegistration? = null
    var listenerLease: WaylandNativeListenerLease? = null
    try {
        proxyPtr = operations.createTextInput(managerPtr, seatPtr)
        check(proxyPtr != 0L) { "create_text_input returned NULL" }
        proxyVersion = operations.getVersion(proxyPtr)
        listener = operations.createListenerRegistration(onEvent, onNativeFailure)
        listenerLease = nativeListenerLifetime.register(listener)
        val listenerResult = listener.install(proxyPtr)
        check(listenerResult == 0) { "text input listener installation failed: $listenerResult" }

        val owner = WaylandTextInputBinding(
            proxyPtr = proxyPtr,
            proxyVersion = proxyVersion,
            listenerLease = listenerLease,
            operations = operations,
        )
        WaylandTextInput.attach(owner, proxyPtr, display, proxyVersion, onEvent)
        return owner
    } catch (failure: Throwable) {
        WaylandTextInput.resetUnownedState()
        var proxyDestroyed = proxyPtr == 0L
        if (!proxyDestroyed) {
            try {
                operations.marshalVoid(
                    proxyPtr,
                    ZWP_TEXT_INPUT_V3_DESTROY,
                    proxyVersion,
                    1, // flags: destroy proxy
                )
                proxyDestroyed = true
            } catch (destroyFailure: Throwable) {
                if (destroyFailure !== failure) failure.addSuppressed(destroyFailure)
            }
        }

        val listenerToRelease = listener
        if (listenerToRelease != null) {
            try {
                if (proxyDestroyed) {
                    val lease = listenerLease
                    if (lease != null) lease.releaseAfterProxyDestroyed()
                    else listenerToRelease.close()
                } else if (listenerLease == null) {
                    nativeListenerLifetime.deferUntilDisplayDisconnect(listenerToRelease)
                }
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
            }
        }

        if (failOnNativeError) throw failure
        return null
    }
}

/**
 * Installs the `zwp_text_input_v3` listener (vtable-based upcall).
 *
 * Listener vtable order (version 1):
 *   0: enter, 1: leave, 2: preedit_string, 3: commit_string,
 *   4: delete_surrounding, 5: done
 */
private fun createNativeTextInputListenerRegistration(
    onEvent: (surfacePtr: Long, event: WindowEvent) -> Unit,
    onNativeFailure: (Throwable) -> Unit,
): WaylandTextInputListenerRegistration {
    val addListener = checkNotNull(wlProxyAddListener) {
        "wl_proxy_add_listener not available for text input"
    }
    val listener = TextInputListener(onEvent, onNativeFailure)
    val arena = Arena.ofShared()
    try {
        val lookup = MethodHandles.lookup()
        val ptr = ValueLayout.ADDRESS.byteSize()
        val eventCount = 6

        val enterStub = upcallStub(
        lookup.findVirtual(TextInputListener::class.java, "onEnter",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
        val leaveStub = upcallStub(
        lookup.findVirtual(TextInputListener::class.java, "onLeave",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
        val preeditStub = upcallStub(
        lookup.findVirtual(TextInputListener::class.java, "onPreeditString",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                MemorySegment::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
        val commitStub = upcallStub(
        lookup.findVirtual(TextInputListener::class.java, "onCommitString",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                MemorySegment::class.java,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        arena,
    )
        val deleteStub = upcallStub(
        lookup.findVirtual(TextInputListener::class.java, "onDeleteSurrounding",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        arena,
    )
        val doneStub = upcallStub(
        lookup.findVirtual(TextInputListener::class.java, "onDone",
            MethodType.methodType(Void.TYPE,
                MemorySegment::class.java, MemorySegment::class.java,
                Int::class.javaPrimitiveType,
            )).bindTo(listener),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        arena,
    )

        val vtable = arena.allocate(ptr * eventCount)
        vtable.set(ValueLayout.ADDRESS, 0L, enterStub)
        vtable.set(ValueLayout.ADDRESS, ptr, leaveStub)
        vtable.set(ValueLayout.ADDRESS, ptr * 2, preeditStub)
        vtable.set(ValueLayout.ADDRESS, ptr * 3, commitStub)
        vtable.set(ValueLayout.ADDRESS, ptr * 4, deleteStub)
        vtable.set(ValueLayout.ADDRESS, ptr * 5, doneStub)

        return object : WaylandTextInputListenerRegistration {
            override fun install(proxyPtr: Long): Int = addListener.invokeExact(
                MemorySegment.ofAddress(proxyPtr),
                vtable,
                MemorySegment.NULL,
            ) as Int

            override fun close() {
                arena.close()
            }
        }
    } catch (failure: Throwable) {
        runWaylandCleanup(failure, listOf(arena::close))
        throw failure
    }
}

// ── Listener implementation ───────────────────────────────────────────────────

/**
 * wl_text_input_v3 listener that accumulates IME events in the batch
 * and dispatches them on `done(serial)`.
 */
internal class TextInputListener(
    private val onEvent: (surfacePtr: Long, event: WindowEvent) -> Unit,
    private val onNativeFailure: (Throwable) -> Unit,
) {
    // Pending events accumulated between preedit_string/commit_string/delete_surrounding
    // and the next done event.
    private val pending = mutableListOf<WindowEvent.Ime.ImeEvent>()

    @Suppress("UNUSED_PARAMETER")
    fun onEnter(data: MemorySegment, textInput: MemorySegment, surface: MemorySegment) {
        guardWaylandNativeUpcall(onNativeFailure) {
            val surfacePtr = surface.address()
            WaylandTextInput.focusedSurfacePtr = surfacePtr
            if (!WaylandTextInput.imeEnabled) {
                // The compositor entered our surface — emit Enabled if not already
                onEvent(surfacePtr, WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Enabled))
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLeave(data: MemorySegment, textInput: MemorySegment, surface: MemorySegment) {
        guardWaylandNativeUpcall(onNativeFailure) {
            val surfacePtr = surface.address().takeIf { it != 0L } ?: WaylandTextInput.focusedSurfacePtr
            WaylandTextInput.focusedSurfacePtr = 0L
            WaylandTextInput.imeEnabled = false
            onEvent(surfacePtr, WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Disabled))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onPreeditString(
        data: MemorySegment, textInput: MemorySegment,
        text: MemorySegment, cursorBegin: Int, cursorEnd: Int,
    ) {
        guardWaylandNativeUpcall(onNativeFailure) {
            val str = try {
                text.getString(0)
            } catch (_: Throwable) {
                ""
            }
            val cursorRange = if (cursorBegin >= 0 && cursorEnd >= 0) {
                Pair(cursorBegin, cursorEnd)
            } else {
                null
            }
            pending.add(WindowEvent.Ime.ImeEvent.Preedit(str, cursorRange))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onCommitString(
        data: MemorySegment, textInput: MemorySegment,
        text: MemorySegment,
    ) {
        guardWaylandNativeUpcall(onNativeFailure) {
            val str = try {
                text.getString(0)
            } catch (_: Throwable) {
                ""
            }
            pending.add(WindowEvent.Ime.ImeEvent.Commit(str))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDeleteSurrounding(
        data: MemorySegment, textInput: MemorySegment,
        beforeLength: Int, afterLength: Int,
    ) {
        guardWaylandNativeUpcall(onNativeFailure) {
            pending.add(WindowEvent.Ime.ImeEvent.DeleteSurrounding(beforeLength, afterLength))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDone(data: MemorySegment, textInput: MemorySegment, serial: Int) {
        guardWaylandNativeUpcall(onNativeFailure) {
            val surfacePtr = WaylandTextInput.focusedSurfacePtr
            if (surfacePtr == 0L) {
                pending.clear()
                return@guardWaylandNativeUpcall
            }
            // Dispatch all events accumulated since the last done.
            for (event in pending) {
                onEvent(surfacePtr, WindowEvent.Ime(event))
            }
            pending.clear()
        }
    }
}

// ── IME helper methods ────────────────────────────────────────────────────────

/**
 * Maps a kadre [ImePurpose] to the zwp_text_input_v3 `input_purpose` enum value.
 */
internal fun imePurposeToWayland(purpose: ImePurpose): Int = when (purpose) {
    ImePurpose.Normal -> ZWP_TEXT_INPUT_PURPOSE_DEFAULT
    ImePurpose.Password -> ZWP_TEXT_INPUT_PURPOSE_PASSWORD
    ImePurpose.Terminal -> ZWP_TEXT_INPUT_PURPOSE_TERMINAL
}

private inline fun performTextInputMutation(
    operations: WaylandTextInputRequestOperations,
    request: (proxyPtr: Long, version: Int) -> Unit,
) {
    val proxyPtr = WaylandTextInput.textInputPtr
    if (proxyPtr == 0L) return
    val version = WaylandTextInput.version
    request(proxyPtr, version)
    operations.marshalVoid(proxyPtr, ZWP_TEXT_INPUT_V3_COMMIT, version, 0)
    val flushResult = operations.flush(WaylandTextInput.displayPtr)
    check(flushResult >= 0) { "wl_display_flush failed after text-input mutation: $flushResult" }
}

/**
 * Sends `zwp_text_input_v3.enable` — must be called when the app requests IME.
 */
internal fun waylandTextInputEnable(
    operations: WaylandTextInputRequestOperations = NativeWaylandTextInputRequestOperations,
) {
    if (WaylandTextInput.textInputPtr == 0L) return
    try {
        performTextInputMutation(operations) { proxyPtr, version ->
            operations.marshalVoid(proxyPtr, ZWP_TEXT_INPUT_V3_ENABLE, version, 0)
        }
        WaylandTextInput.imeEnabled = true
    } catch (_: Throwable) {}
}

/**
 * Sends `zwp_text_input_v3.disable` — must be called when the app stops IME.
 */
internal fun waylandTextInputDisable(
    operations: WaylandTextInputRequestOperations = NativeWaylandTextInputRequestOperations,
) {
    if (WaylandTextInput.textInputPtr == 0L) return
    try {
        performTextInputMutation(operations) { proxyPtr, version ->
            operations.marshalVoid(proxyPtr, ZWP_TEXT_INPUT_V3_DISABLE, version, 0)
        }
        WaylandTextInput.imeEnabled = false
    } catch (_: Throwable) {}
}

/**
 * Sends `zwp_text_input_v3.set_cursor_rectangle(x, y, width, height)`.
 *
 * @param position Top-left corner in surface-local coordinates (physical pixels).
 * @param size     Size of the cursor area in physical pixels.
 * @param scale    Window scale factor for converting to buffer coordinates.
 */
internal fun waylandTextInputSetCursorRectangle(
    position: PhysicalPosition<Int>,
    size: PhysicalSize<Int>,
    scale: Double,
    operations: WaylandTextInputRequestOperations = NativeWaylandTextInputRequestOperations,
) {
    if (WaylandTextInput.textInputPtr == 0L) return
    val invScale = if (scale > 0.0) 1.0 / scale else 1.0
    val x = (position.x * invScale).toInt()
    val y = (position.y * invScale).toInt()
    val w = (size.width * invScale).toInt()
    val h = (size.height * invScale).toInt()
    try {
        performTextInputMutation(operations) { proxyPtr, version ->
            operations.marshalFourInt(
                proxyPtr,
                ZWP_TEXT_INPUT_V3_SET_CURSOR_RECTANGLE,
                version,
                0,
                x,
                y,
                w,
                h,
            )
        }
    } catch (_: Throwable) {}
}

/**
 * Maps Kadre's purpose-only API to `zwp_text_input_v3.set_content_type(hint, purpose)`.
 */
internal fun waylandTextInputSetPurpose(
    purpose: ImePurpose,
    operations: WaylandTextInputRequestOperations = NativeWaylandTextInputRequestOperations,
) {
    if (WaylandTextInput.textInputPtr == 0L) return
    val waylandPurpose = imePurposeToWayland(purpose)
    try {
        performTextInputMutation(operations) { proxyPtr, version ->
            operations.marshalTwoUint(
                proxyPtr,
                ZWP_TEXT_INPUT_V3_SET_CONTENT_TYPE,
                version,
                0,
                0,
                waylandPurpose,
            )
        }
    } catch (_: Throwable) {}
}
