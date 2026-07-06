package org.graphiks.kadre.wayland

import org.graphiks.kadre.ffi.wayland.*
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Manages `xdg_activation_v1` for window activation tokens.
 *
 * When [activationManagerPtr] is 0 (protocol extension unavailable), all operations
 * silently no-op.
 *
 * @param activationManagerPtr Address of the bound `xdg_activation_v1*` proxy, or 0.
 * @param seatPtr              Address of the `wl_seat*` proxy for serial-based activation, or 0.
 * @param displayPtr           Address of the `wl_display*` for flushing and roundtrips.
 */
internal class WaylandActivationToken(
    private val activationManagerPtr: Long,
    private val seatPtr: Long = 0L,
    private val displayPtr: Long = 0L,
) {
    private var lastSerial: Int = 0

    internal fun updateSerial(serial: Int) {
        lastSerial = serial
    }

    /**
     * Activates the surface at [surfacePtr] using the given [token] string.
     * Calls xdg_activation_v1.activate(token, surface) directly.
     */
    fun activate(token: String, surfacePtr: Long) {
        if (activationManagerPtr == 0L || surfacePtr == 0L) return
        val marshal = wlProxyMarshalFlagsStringObject ?: return
        try {
            Arena.ofConfined().use { arena ->
                val tokenSeg = arena.allocateFrom(token)
                marshal.invokeExact(
                    MemorySegment.ofAddress(activationManagerPtr),
                    2, // opcode: activate
                    MemorySegment.NULL,
                    1, // version
                    0, // flags
                    tokenSeg,
                    MemorySegment.ofAddress(surfacePtr),
                )
                flushDisplay()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Requests user attention by generating an xdg_activation_v1 token,
     * then activating the surface with it.
     *
     * Performs the full token flow:
     *   1. xdg_activation_v1.get_activation_token → new token proxy
     *   2. Set up a listener for the done event
     *   3. xdg_activation_token_v1.set_serial (when seat/serial available)
     *   4. xdg_activation_token_v1.set_surface
     *   5. xdg_activation_token_v1.commit
     *   6. Flush + roundtrip to force done event delivery
     *   7. Read the token string from the done event
     *   8. xdg_activation_v1.activate(token, surface)
     *
     * @return true when activation was successfully requested.
     */
    fun requestAttention(surfacePtr: Long, seatPtr: Long, serial: Int): Boolean {
        if (activationManagerPtr == 0L || surfacePtr == 0L) return false

        val tokenObj = createToken() ?: return false
        val tokenPtr = tokenObj.address()

        val listenerArena = Arena.ofShared()
        try {
            val tokenHolder = TokenHolder()
            val lookup = MethodHandles.lookup()
            val doneHandle = lookup.findVirtual(
                TokenHolder::class.java, "onDone",
                MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java),
            ).bindTo(tokenHolder)

            val doneStub = upcallStub(
                doneHandle,
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                listenerArena,
            )

            val listener = listenerArena.allocate(ValueLayout.ADDRESS.byteSize())
            listener.set(ValueLayout.ADDRESS, 0L, doneStub)

            val addListener = wlProxyAddListener ?: return false
            val rc = addListener.invokeExact(tokenObj, listener, MemorySegment.NULL) as Int
            if (rc != 0) return false

            if (seatPtr != 0L && serial > 0) {
                setTokenSerial(tokenPtr, serial, seatPtr)
            }
            setTokenSurface(tokenPtr, surfacePtr)
            commitToken(tokenPtr)
            flushDisplay()
            roundtrip()

            val token = tokenHolder.value
            return if (token != null && token.isNotEmpty()) {
                activate(token, surfacePtr)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            return false
        } finally {
            destroyTokenObject(tokenPtr)
            listenerArena.close()
        }
    }

    /**
     * Creates an xdg_activation_token_v1 proxy via xdg_activation_v1.get_activation_token.
     * xdg_activation_v1 opcode 1: get_activation_token (new_id xdg_activation_token_v1)
     */
    private fun createToken(): MemorySegment? {
        val marshal = wlProxyMarshalNewId ?: return null
        val iface = xdgActivationTokenV1Interface
        return try {
            marshal.invokeExact(
                MemorySegment.ofAddress(activationManagerPtr),
                1, // opcode: get_activation_token
                iface,
                1, // version
                0, // flags
                MemorySegment.NULL,
            ) as MemorySegment
        } catch (_: Throwable) { null }
    }

    /**
     * Sets the serial on the activation token.
     * xdg_activation_token_v1 opcode 0: set_serial (uint serial, object seat)
     */
    private fun setTokenSerial(tokenPtr: Long, serial: Int, seatPtr: Long) {
        val marshal = wlProxyMarshalFlagsUintObject ?: return
        try {
            marshal.invokeExact(
                MemorySegment.ofAddress(tokenPtr),
                0, // opcode: set_serial
                MemorySegment.NULL,
                1, // version
                0, // flags
                serial,
                MemorySegment.ofAddress(seatPtr),
            )
        } catch (_: Throwable) {}
    }

    /**
     * Sets the surface on the activation token.
     * xdg_activation_token_v1 opcode 2: set_surface (object surface)
     */
    private fun setTokenSurface(tokenPtr: Long, surfacePtr: Long) {
        val marshal = wlProxyMarshalFlagsObject ?: return
        try {
            marshal.invokeExact(
                MemorySegment.ofAddress(tokenPtr),
                2, // opcode: set_surface
                MemorySegment.NULL,
                1, // version
                0, // flags
                MemorySegment.ofAddress(surfacePtr),
            )
        } catch (_: Throwable) {}
    }

    /**
     * Commits the activation token.
     * xdg_activation_token_v1 opcode 3: commit
     */
    private fun commitToken(tokenPtr: Long) {
        val marshal = wlProxyMarshalFlagsVoid ?: return
        try {
            marshal.invokeExact(
                MemorySegment.ofAddress(tokenPtr),
                3, // opcode: commit
                MemorySegment.NULL,
                1, // version
                0, // flags
            )
        } catch (_: Throwable) {}
    }

    /**
     * Destroys the activation token object.
     * xdg_activation_token_v1 opcode 4: destroy (destructor)
     */
    private fun destroyTokenObject(tokenPtr: Long) {
        val marshal = wlProxyMarshalFlagsVoid ?: return
        try {
            marshal.invokeExact(
                MemorySegment.ofAddress(tokenPtr),
                4, // opcode: destroy
                MemorySegment.NULL,
                1, // version
                WL_MARSHAL_FLAG_DESTROY, // flags
            )
        } catch (_: Throwable) {}
    }

    private fun roundtrip(): Int {
        if (displayPtr == 0L) return -1
        val rt = wlDisplayRoundtrip ?: return -1
        return try {
            rt.invokeExact(MemorySegment.ofAddress(displayPtr)) as Int
        } catch (_: Throwable) { -1 }
    }

    private fun flushDisplay() {
        if (displayPtr == 0L) return
        try {
            wlDisplayFlush?.invokeExact(MemorySegment.ofAddress(displayPtr)) as Int
        } catch (_: Throwable) {}
    }

    /**
     * Holds the token string received from the compositor's done event.
     * Method referenced via MethodHandles for the upcall stub.
     */
    private class TokenHolder {
        @JvmField
        var value: String? = null

        @Suppress("UNUSED_PARAMETER")
        fun onDone(data: MemorySegment, tokenObj: MemorySegment, tokenPtr: MemorySegment) {
            value = try {
                if (tokenPtr.address() != 0L) tokenPtr.getString(0L) else null
            } catch (_: Throwable) {
                null
            }
        }
    }

    companion object {
        internal fun create(
            globals: WaylandGlobals,
            displayPtr: Long,
        ): WaylandActivationToken? {
            if (globals.activationManagerPtr == 0L) return null
            return WaylandActivationToken(
                activationManagerPtr = globals.activationManagerPtr,
                seatPtr = globals.seatPtr,
                displayPtr = displayPtr,
            )
        }
    }
}
