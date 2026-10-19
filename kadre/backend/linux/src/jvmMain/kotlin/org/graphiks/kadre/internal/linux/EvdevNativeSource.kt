package org.graphiks.kadre.internal.linux

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kffi.evdev.EvdevEvent
import org.graphiks.kffi.evdev.EvdevFf
import org.graphiks.kffi.evdev.EvdevInputEvent
import org.graphiks.kffi.evdev.FF_EFFECT_LAYOUT
import org.graphiks.kffi.evdev.INPUT_EVENT_LAYOUT
import org.graphiks.kffi.evdev.UdevContext
import org.graphiks.kffi.evdev.UdevMonitor
import org.graphiks.kffi.evdev.evdevBitsetHasGamepadButton
import org.graphiks.kffi.evdev.evdevClose
import org.graphiks.kffi.evdev.evdevDecodeEvents
import org.graphiks.kffi.evdev.evdevEncodeEvent
import org.graphiks.kffi.evdev.evdevGetAbs
import org.graphiks.kffi.evdev.evdevGetBits
import org.graphiks.kffi.evdev.evdevGetId
import org.graphiks.kffi.evdev.evdevGetName
import org.graphiks.kffi.evdev.evdevGetUniq
import org.graphiks.kffi.evdev.evdevIoctlInt
import org.graphiks.kffi.evdev.evdevIoctlPtr
import org.graphiks.kffi.evdev.evdevOpen
import org.graphiks.kffi.evdev.evdevOpenReadWrite
import org.graphiks.kffi.evdev.evdevRead
import org.graphiks.kffi.evdev.evdevWrite
import org.graphiks.kffi.evdev.EVIOCSFF
import org.graphiks.kffi.evdev.EVIOCRMFF

/**
 * The native seam of the evdev backend (the wayland backend's bridge
 * pattern): kadre owns the policy — detection, identity, routing — while
 * the mechanics of the input stack live behind this interface. The kffi
 * implementation talks to /dev/input through kffi-evdev; the O2 suite
 * drives fakes.
 */
internal interface EvdevNativeSource : AutoCloseable {
    /** Non-null when enumeration itself failed (missing permission). */
    val enumerationFailure: EvdevEnumerationFailure?

    /** Current /dev/input/event* node paths, unordered. */
    fun listDevNodes(): List<String>

    /**
     * Opens and probes one node; null when it is not a gamepad (kernel
     * capabilities decide — never udev properties) or cannot be opened.
     */
    fun openGamepad(devNode: String): EvdevNativeGamepad?

    /** One pending hotplug action, or null; drained by the broker worker. */
    fun pollHotplug(): EvdevHotplug?
}

/** Enumeration could not read the input nodes at all. */
internal class EvdevEnumerationFailure(
    val permissionDenied: Boolean,
)

internal class EvdevHotplug(
    val action: String, // "add" | "remove"
    val devNode: String,
)

internal interface EvdevNativeGamepad : AutoCloseable {
    /** Stable per physical device (design D2): identity + uniqueness. */
    val identity: String
    val name: String?
    val buttonCodes: List<Int>
    val axisCodes: List<Int>
    val syntheticButtonCodes: List<Int>
    val forceFeedback: Boolean
    fun axisBounds(code: Int): Pair<Int, Int>?

    /** Decoded events readable right now (non-blocking). */
    fun readEvents(): List<EvdevInputEvent>

    /** Uploads a rumble effect; null when the device refuses it. */
    fun uploadRumble(strong: Double, weak: Double, durationMillis: Int): EvdevNativeEffect?
}

internal interface EvdevNativeEffect {
    fun play(): Boolean
    fun stop(): Boolean

    /** Stop and erase — idempotent. */
    fun release()
}

/** The kffi-backed source: libudev for discovery, evdev ioctls for state. */
internal class KffiEvdevNativeSource : EvdevNativeSource {
    private val closed = AtomicBoolean(false)
    private var context: UdevContext? = null
    private var monitor: UdevMonitor? = null

    override var enumerationFailure: EvdevEnumerationFailure? = null
        private set

    init {
        context = UdevContext.open()
        if (context == null) {
            // libudev missing entirely: no nodes to see, not a permission hit
            enumerationFailure = EvdevEnumerationFailure(permissionDenied = false)
        } else {
            monitor = UdevMonitor.open(context!!)
        }
    }

    override fun listDevNodes(): List<String> {
        if (closed.get()) return emptyList()
        val nodes = context?.listDevNodePaths() ?: return emptyList()
        return nodes
    }

    override fun openGamepad(devNode: String): EvdevNativeGamepad? {
        if (closed.get()) return null
        // read-write: effect playback writes EV_FF through the same node
        val fd = evdevOpenReadWrite(devNode)
        if (fd < 0) return null
        try {
            val keyBits = evdevGetBits(fd, EvdevEvent.KEY, KEY_BITSET_BYTES) ?: return null
            if (!evdevBitsetHasGamepadButton(keyBits)) return null
            val id = evdevGetId(fd)
            val uniq = evdevGetUniq(fd)
            val identity = buildString {
                append(id?.bustype ?: -1).append(':')
                append(id?.vendor ?: -1).append(':')
                append(id?.product ?: -1)
                if (uniq.isNotEmpty()) {
                    append(':').append(uniq)
                }
            }
            val absBits = evdevGetBits(fd, EvdevEvent.ABS, ABS_BITSET_BYTES)
            val buttonCodes = decodeSetBits(keyBits).filter { code -> EvdevCodes.mapButton(code) != null || isSynthetic(code) }
            val axisCodes = absBits?.let(::decodeSetBits)?.filter { code -> EvdevCodes.mapAxis(code) != null } ?: emptyList()
            val synthetic = decodeSetBits(keyBits).filter(::isSynthetic)
            val ffBits = evdevGetBits(fd, EvdevEvent.FF, 8)
            val forceFeedback = ffBits != null && decodeSetBits(ffBits).contains(EvdevFf.RUMBLE)
            val bounds = HashMap<Int, Pair<Int, Int>>()
            axisCodes.forEach { axis ->
                evdevGetAbs(fd, axis)?.let { info -> bounds[axis] = info.minimum to info.maximum }
            }
            return KffiEvdevNativeGamepad(fd, identity, evdevGetName(fd), buttonCodes, axisCodes, synthetic, forceFeedback, bounds)
        } catch (_: Throwable) {
            runCatching { evdevClose(fd) }
            return null
        }
    }

    override fun pollHotplug(): EvdevHotplug? {
        val monitor = this.monitor ?: return null
        val node = runCatching { monitor.receive() }.getOrNull() ?: return null
        val action = node.action ?: return null
        if (action != "add" && action != "remove") return null
        return EvdevHotplug(action, node.devNode)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { monitor?.close() }
        runCatching { context?.close() }
    }

    private fun isSynthetic(code: Int): Boolean = when (code) {
        EvdevCodes.BTN_TL2, EvdevCodes.BTN_TR2,
        EvdevCodes.BTN_DPAD_UP, EvdevCodes.BTN_DPAD_DOWN,
        EvdevCodes.BTN_DPAD_LEFT, EvdevCodes.BTN_DPAD_RIGHT,
        -> true

        else -> false
    }

    private fun decodeSetBits(bits: ByteArray): List<Int> {
        val codes = ArrayList<Int>()
        for (index in bits.indices) {
            var byte = bits[index].toInt() and 0xFF
            var bit = 0
            while (byte != 0) {
                if (byte and 1 != 0) codes.add(index * 8 + bit)
                byte = byte ushr 1
                bit++
            }
        }
        return codes
    }

    private companion object {
        const val KEY_BITSET_BYTES = 256
        const val ABS_BITSET_BYTES = 8
    }
}

/** Udev node paths only — UdevInputNode carries no kadre policy. */
private fun UdevContext.listDevNodePaths(): List<String> =
    listInputNodes().map { it.devNode }

private class KffiEvdevNativeGamepad(
    private val fd: Int,
    override val identity: String,
    override val name: String?,
    override val buttonCodes: List<Int>,
    override val axisCodes: List<Int>,
    override val syntheticButtonCodes: List<Int>,
    override val forceFeedback: Boolean,
    private val bounds: Map<Int, Pair<Int, Int>>,
) : EvdevNativeGamepad {

    override fun axisBounds(code: Int): Pair<Int, Int>? = bounds[code]

    override fun readEvents(): List<EvdevInputEvent> {
        val buffer = readBuffer ?: return emptyList()
        synchronized(buffer) {
            val bytes = runCatching { evdevRead(fd, buffer) }.getOrDefault(-1)
            if (bytes <= 0) return emptyList()
            return evdevDecodeEvents(buffer, bytes)
        }
    }

    override fun uploadRumble(strong: Double, weak: Double, durationMillis: Int): EvdevNativeEffect? {
        if (!forceFeedback) return null
        val allocated = Arena.ofConfined().use { arena ->
            val segment = arena.allocate(FF_EFFECT_LAYOUT)
            fun offset(vararg name: String) = FF_EFFECT_LAYOUT.byteOffset(
                *name.map(MemoryLayout.PathElement::groupElement).toTypedArray(),
            )
            segment.set(ValueLayout.JAVA_SHORT, offset("type"), EvdevFf.RUMBLE.toShort())
            segment.set(ValueLayout.JAVA_SHORT, offset("id"), (-1).toShort())
            segment.set(ValueLayout.JAVA_SHORT, offset("replayLength"), durationMillis.coerceAtMost(0x7fff).toShort())
            segment.set(ValueLayout.JAVA_SHORT, offset("rumble", "strongMagnitude"), magnitude(strong))
            segment.set(ValueLayout.JAVA_SHORT, offset("rumble", "weakMagnitude"), magnitude(weak))
            if (evdevIoctlPtr(fd, EVIOCSFF, segment) < 0) return null
            segment.get(ValueLayout.JAVA_SHORT, offset("id")).toInt()
        }
        if (allocated < 0) return null
        return KffiEvdevNativeEffect(fd, allocated)
    }

    private fun magnitude(value: Double): Short = (value.coerceIn(0.0, 1.0) * 0xffff).toInt().coerceIn(0, 0xffff).toShort()

    override fun close() {
        runCatching { evdevClose(fd) }
    }

    private companion object {
        // one worker reads at a time; the buffer is reused across polls
        val readBuffer: MemorySegment? = Arena.global().allocate(INPUT_EVENT_LAYOUT.byteSize() * 16)
    }
}

private class KffiEvdevNativeEffect(
    private val fd: Int,
    private val effectId: Int,
) : EvdevNativeEffect {
    private val released = AtomicBoolean(false)

    override fun play(): Boolean = writeFf(1)
    override fun stop(): Boolean = writeFf(0)

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { stop() }
        runCatching { evdevIoctlInt(fd, EVIOCRMFF, effectId) }
    }

    private fun writeFf(repeats: Int): Boolean = Arena.ofConfined().use { arena ->
        runCatching { evdevWrite(fd, evdevEncodeEvent(arena, EvdevEvent.FF, effectId, repeats)) }.getOrDefault(-1) >= 0
    }
}
