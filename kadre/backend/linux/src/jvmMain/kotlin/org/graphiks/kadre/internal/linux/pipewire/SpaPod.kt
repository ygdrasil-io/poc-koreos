package org.graphiks.kadre.internal.linux.pipewire

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * SPA POD wire-format values (`spa/pod/pod.h`, SPA 0.2 as shipped by
 * PipeWire 1.0). The codec is pure Kotlin — no FFM: native stream calls live
 * behind kffi-pipewire, the pod policy lives here (design
 * LINUX-PIPEWIRE-STREAM-DESIGN D8).
 *
 * Layout truth follows the pinned headers: every pod is an 8-byte header
 * (`uint32 size` of the body, `uint32 type`) followed by the body; iteration
 * advances pods, object properties and struct members to the next 8-byte
 * boundary (`spa_pod_next`/`spa_pod_prop_next` round up to 8). A choice body
 * is `type, flags` plus a full child pod header, then tightly packed
 * child-size values; an array body is a child pod header plus packed values.
 */
internal sealed interface SpaValue {
    data object NoneValue : SpaValue

    data class BoolValue(val value: Boolean) : SpaValue
    data class IdValue(val value: Int) : SpaValue
    data class IntValue(val value: Int) : SpaValue
    data class LongValue(val value: Long) : SpaValue
    data class FloatValue(val value: Float) : SpaValue
    data class DoubleValue(val value: Double) : SpaValue
    data class StringValue(val value: String) : SpaValue
    data class BytesValue(val value: ByteArray) : SpaValue {
        override fun equals(other: Any?): Boolean = other is BytesValue && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()
    }

    data class RectangleValue(val width: Int, val height: Int) : SpaValue
    data class FractionValue(val numerator: Int, val denominator: Int) : SpaValue
    data class FdValue(val value: Int) : SpaValue

    /** Uniform fixed-size children; [childType] is a SpaPodTypes id. */
    data class ArrayValue(val children: List<SpaValue>) : SpaValue

    data class StructValue(val fields: List<SpaValue>) : SpaValue

    /** [choiceType] is a SPA_CHOICE_* id; [values] are uniform, first is the default. */
    data class ChoiceValue(
        val choiceType: Int,
        val flags: Int,
        val values: List<SpaValue>,
    ) : SpaValue

    data class PropValue(
        val key: Int,
        val flags: Int,
        val value: SpaValue,
    ) : SpaValue

    /** [objectType] is a SPA_TYPE_OBJECT_* id; [id] is the param id. */
    data class ObjectValue(
        val objectType: Int,
        val id: Int,
        val properties: List<PropValue>,
    ) : SpaValue
}

/**
 * SPA_TYPE_* type ids, pinned from the SPA 0.2 headers through the
 * kffi-pipewire generated constants (see `KFFI_SPA_TYPE_*`).
 */
internal object SpaPodTypes {
    const val NONE: Int = 1
    const val BOOL: Int = 2
    const val ID: Int = 3
    const val INT: Int = 4
    const val LONG: Int = 5
    const val FLOAT: Int = 6
    const val DOUBLE: Int = 7
    const val STRING: Int = 8
    const val BYTES: Int = 9
    const val RECTANGLE: Int = 10
    const val FRACTION: Int = 11
    const val BITMAP: Int = 12
    const val ARRAY: Int = 13
    const val STRUCT: Int = 14
    const val OBJECT: Int = 15
    const val SEQUENCE: Int = 16
    const val POINTER: Int = 17
    const val FD: Int = 18
    const val CHOICE: Int = 19

    /** Body size of the fixed-size primitives, or null when variable. */
    fun fixedBodySize(type: Int): Int? = when (type) {
        NONE -> 0
        BOOL, ID, INT, FLOAT -> 4
        LONG, DOUBLE, RECTANGLE, FRACTION, FD -> 8
        else -> null
    }
}

/**
 * POD encoder. [toByteArray] emits the pod followed by the iteration
 * padding, so the bytes can be placed at any 8-aligned address and walked by
 * the native iterator.
 */
internal object SpaPodWriter {
    private const val HEADER_SIZE = 8

    fun toByteArray(value: SpaValue): ByteArray {
        val body = BodyWriter()
        writeBody(body, value)
        val podSize = roundUp8(HEADER_SIZE + body.size)
        val out = ByteArray(podSize)
        writeIntAt(out, 0, body.size)
        writeIntAt(out, 4, typeOf(value))
        body.copyInto(out, HEADER_SIZE)
        return out
    }

    fun typeOf(value: SpaValue): Int = when (value) {
        is SpaValue.NoneValue -> SpaPodTypes.NONE
        is SpaValue.BoolValue -> SpaPodTypes.BOOL
        is SpaValue.IdValue -> SpaPodTypes.ID
        is SpaValue.IntValue -> SpaPodTypes.INT
        is SpaValue.LongValue -> SpaPodTypes.LONG
        is SpaValue.FloatValue -> SpaPodTypes.FLOAT
        is SpaValue.DoubleValue -> SpaPodTypes.DOUBLE
        is SpaValue.StringValue -> SpaPodTypes.STRING
        is SpaValue.BytesValue -> SpaPodTypes.BYTES
        is SpaValue.RectangleValue -> SpaPodTypes.RECTANGLE
        is SpaValue.FractionValue -> SpaPodTypes.FRACTION
        is SpaValue.FdValue -> SpaPodTypes.FD
        is SpaValue.ArrayValue -> SpaPodTypes.ARRAY
        is SpaValue.StructValue -> SpaPodTypes.STRUCT
        is SpaValue.ChoiceValue -> SpaPodTypes.CHOICE
        is SpaValue.ObjectValue -> SpaPodTypes.OBJECT
        is SpaValue.PropValue -> throw IllegalArgumentException("a prop only lives inside an object pod")
    }

    /** Writes a complete embedded pod (header + body), without tail padding. */
    private fun writePodInto(out: BodyWriter, value: SpaValue) {
        val start = out.size
        out.writeInt(0)
        out.writeInt(typeOf(value))
        writeBody(out, value)
        out.writeIntAt(start, out.size - start - HEADER_SIZE)
    }

    private fun writeBody(out: BodyWriter, value: SpaValue) {
        when (value) {
            is SpaValue.NoneValue -> Unit
            is SpaValue.BoolValue -> out.writeInt(if (value.value) 1 else 0)
            is SpaValue.IdValue -> out.writeInt(value.value)
            is SpaValue.IntValue -> out.writeInt(value.value)
            is SpaValue.LongValue -> out.writeInt64(value.value)
            is SpaValue.FloatValue -> out.writeInt(value.value.toRawBits())
            is SpaValue.DoubleValue -> out.writeInt64(value.value.toRawBits())
            is SpaValue.StringValue -> {
                require(!value.value.contains('\u0000')) { "pod strings must not embed NUL" }
                for (byte in value.value.encodeToByteArray()) out.writeByte(byte.toInt())
                out.writeByte(0)
            }
            is SpaValue.BytesValue -> for (byte in value.value) out.writeByte(byte.toInt())
            is SpaValue.RectangleValue -> {
                out.writeInt(value.width)
                out.writeInt(value.height)
            }
            is SpaValue.FractionValue -> {
                out.writeInt(value.numerator)
                out.writeInt(value.denominator)
            }
            is SpaValue.FdValue -> {
                out.writeInt(value.value)
                out.writeInt(0)
            }
            is SpaValue.ArrayValue -> writePackedValues(out, value.children)
            is SpaValue.StructValue -> {
                value.fields.forEach { field ->
                    writePodInto(out, field)
                    out.padTo(roundUp8(out.size))
                }
            }
            is SpaValue.ChoiceValue -> {
                require(value.values.isNotEmpty()) { "a choice needs at least the default value" }
                out.writeInt(value.choiceType)
                out.writeInt(value.flags)
                writePackedValues(out, value.values)
            }
            is SpaValue.ObjectValue -> {
                out.writeInt(value.objectType)
                out.writeInt(value.id)
                value.properties.forEach { prop ->
                    out.writeInt(prop.key)
                    out.writeInt(prop.flags)
                    writePodInto(out, prop.value)
                    out.padTo(roundUp8(out.size))
                }
            }
            is SpaValue.PropValue -> throw IllegalArgumentException("a prop only lives inside an object pod")
        }
    }

    /** Array/Choice bodies: a full child pod header then tightly packed child bodies. */
    private fun writePackedValues(out: BodyWriter, children: List<SpaValue>) {
        val childType = typeOf(children.first())
        require(children.all { typeOf(it) == childType }) {
            "array/choice children must share one primitive type"
        }
        val childSize = SpaPodTypes.fixedBodySize(childType)
        requireNotNull(childSize) { "array/choice children must be fixed-size primitives" }
        out.writeInt(childSize)
        out.writeInt(childType)
        children.forEach { child -> writeBody(out, child) }
    }

    private fun roundUp8(size: Int): Int = (size + 7) and 7.inv()

    private fun writeIntAt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value ushr 8) and 0xff).toByte()
        target[offset + 2] = ((value ushr 16) and 0xff).toByte()
        target[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    /** Growable little-endian byte sink. */
    internal class BodyWriter {
        private var buffer = ByteArray(64)
        var size: Int = 0
            private set

        fun writeByte(value: Int) {
            ensure(1)
            buffer[size] = value.toByte()
            size += 1
        }

        fun writeInt(value: Int) {
            ensure(4)
            buffer[size] = (value and 0xff).toByte()
            buffer[size + 1] = ((value ushr 8) and 0xff).toByte()
            buffer[size + 2] = ((value ushr 16) and 0xff).toByte()
            buffer[size + 3] = ((value ushr 24) and 0xff).toByte()
            size += 4
        }

        fun writeInt64(value: Long) {
            writeInt((value and 0xffffffffL).toInt())
            writeInt((value ushr 32).toInt())
        }

        fun writeIntAt(offset: Int, value: Int) {
            buffer[offset] = (value and 0xff).toByte()
            buffer[offset + 1] = ((value ushr 8) and 0xff).toByte()
            buffer[offset + 2] = ((value ushr 16) and 0xff).toByte()
            buffer[offset + 3] = ((value ushr 24) and 0xff).toByte()
        }

        fun padTo(target: Int) {
            require(target >= size) { "padding must not move backwards" }
            while (size < target) writeByte(0)
        }

        fun copyInto(target: ByteArray, offset: Int) {
            System.arraycopy(buffer, 0, target, offset, size)
        }

        private fun ensure(extra: Int) {
            if (size + extra > buffer.size) {
                var capacity = buffer.size
                while (capacity < size + extra) capacity *= 2
                buffer = buffer.copyOf(capacity)
            }
        }
    }
}

/**
 * POD decoder. Reads the pod at offset 0 of [segment]; the segment's extent
 * is the parse bound (callers size it from the pod header before handing
 * native memory over). Refuses what the writer cannot re-encode: pointers,
 * sequences, bitmaps and unknown type ids never decode into a value.
 */
internal object SpaPodReader {
    fun read(segment: MemorySegment): SpaValue {
        val reader = SegmentReader(segment, segment.byteSize())
        return readPod(reader, limit = segment.byteSize().toInt())
    }

    private fun readPod(reader: SegmentReader, limit: Int): SpaValue {
        val bodySize = reader.uint32().toInt()
        val type = reader.uint32().toInt()
        require(bodySize >= 0) { "pod body size must not be negative" }
        val bodyStart = reader.offset
        require(bodyStart + bodySize <= limit) { "pod body overruns its container" }
        val value = readBody(reader, type, bodySize, bodyStart + bodySize)
        require(reader.offset == bodyStart + bodySize) {
            "pod body of type $type left ${bodyStart + bodySize - reader.offset} unread bytes"
        }
        return value
    }

    private fun readBody(reader: SegmentReader, type: Int, bodySize: Int, bodyEnd: Int): SpaValue {
        fun fixed(expected: Int) {
            require(bodySize == expected) { "pod type $type expects body size $expected, got $bodySize" }
        }
        return when (type) {
            SpaPodTypes.NONE -> {
                fixed(0)
                SpaValue.NoneValue
            }
            SpaPodTypes.BOOL -> {
                fixed(4)
                when (val raw = reader.uint32().toInt()) {
                    0 -> SpaValue.BoolValue(false)
                    1 -> SpaValue.BoolValue(true)
                    else -> throw IllegalArgumentException("pod bool must be 0 or 1, got $raw")
                }
            }
            SpaPodTypes.ID -> {
                fixed(4)
                SpaValue.IdValue(reader.uint32().toInt())
            }
            SpaPodTypes.INT -> {
                fixed(4)
                SpaValue.IntValue(reader.uint32().toInt())
            }
            SpaPodTypes.LONG -> {
                fixed(8)
                SpaValue.LongValue(reader.uint32() or (reader.uint32() shl 32))
            }
            SpaPodTypes.FLOAT -> {
                fixed(4)
                SpaValue.FloatValue(Float.fromBits(reader.uint32().toInt()))
            }
            SpaPodTypes.DOUBLE -> {
                fixed(8)
                SpaValue.DoubleValue(Double.fromBits(reader.uint32() or (reader.uint32() shl 32)))
            }
            SpaPodTypes.STRING -> {
                val bytes = reader.bytes(bodySize)
                require(bytes.isNotEmpty() && bytes.last() == 0.toByte()) { "pod string must end with NUL" }
                require(bytes.indexOfFirst { it == 0.toByte() } == bytes.size - 1) {
                    "pod string must not embed NUL"
                }
                SpaValue.StringValue(bytes.decodeToString(0, bytes.size - 1))
            }
            SpaPodTypes.BYTES -> SpaValue.BytesValue(reader.bytes(bodySize))
            SpaPodTypes.RECTANGLE -> {
                fixed(8)
                SpaValue.RectangleValue(reader.uint32().toInt(), reader.uint32().toInt())
            }
            SpaPodTypes.FRACTION -> {
                fixed(8)
                SpaValue.FractionValue(reader.uint32().toInt(), reader.uint32().toInt())
            }
            SpaPodTypes.FD -> {
                fixed(8)
                val value = reader.uint32().toInt()
                reader.uint32()
                SpaValue.FdValue(value)
            }
            SpaPodTypes.ARRAY -> SpaValue.ArrayValue(readPackedValues(reader, bodyEnd).second)
            SpaPodTypes.CHOICE -> {
                val choiceType = reader.uint32().toInt()
                val flags = reader.uint32().toInt()
                val values = readPackedValues(reader, bodyEnd).second
                require(values.isNotEmpty()) { "a choice needs at least the default value" }
                SpaValue.ChoiceValue(choiceType, flags, values)
            }
            SpaPodTypes.STRUCT -> {
                val fields = ArrayList<SpaValue>()
                while (reader.offset < bodyEnd) {
                    reader.align8()
                    if (reader.offset >= bodyEnd) break
                    fields.add(readPod(reader, bodyEnd))
                }
                SpaValue.StructValue(fields)
            }
            SpaPodTypes.OBJECT -> {
                val objectType = reader.uint32().toInt()
                val id = reader.uint32().toInt()
                val properties = ArrayList<SpaValue.PropValue>()
                while (reader.offset < bodyEnd) {
                    reader.align8()
                    if (reader.offset >= bodyEnd) break
                    val key = reader.uint32().toInt()
                    val flags = reader.uint32().toInt()
                    val value = readPod(reader, bodyEnd)
                    reader.align8()
                    properties.add(SpaValue.PropValue(key, flags, value))
                }
                SpaValue.ObjectValue(objectType, id, properties)
            }
            SpaPodTypes.POINTER -> throw IllegalArgumentException("pod pointers never cross the codec boundary")
            SpaPodTypes.SEQUENCE -> throw IllegalArgumentException("pod sequences are out of codec scope")
            SpaPodTypes.BITMAP -> throw IllegalArgumentException("pod bitmaps are out of codec scope")
            else -> throw IllegalArgumentException("unknown pod type id $type")
        }
    }

    /** Array/Choice value regions: a child pod header then packed child bodies. */
    private fun readPackedValues(reader: SegmentReader, bodyEnd: Int): Pair<Int, List<SpaValue>> {
        val childSize = reader.uint32().toInt()
        val childType = reader.uint32().toInt()
        val fixed = SpaPodTypes.fixedBodySize(childType)
        requireNotNull(fixed) { "array/choice children must be fixed-size primitives" }
        require(childSize == fixed) { "child pod size $childSize disagrees with type $childType" }
        require((bodyEnd - reader.offset) % childSize == 0) {
            "packed values region is not a multiple of child size"
        }
        val values = ArrayList<SpaValue>()
        while (reader.offset < bodyEnd) {
            values.add(readBody(reader, childType, childSize, reader.offset + childSize))
        }
        return childType to values
    }

    /** Little-endian bounded reader over a memory segment. */
    internal class SegmentReader(private val segment: MemorySegment, private val bound: Long) {
        var offset: Int = 0
            private set

        fun uint32(): Long {
            require(offset + 4 <= bound) { "pod read overruns its buffer at $offset" }
            val value = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset.toLong()).toLong() and 0xffffffffL
            offset += 4
            return value
        }

        fun bytes(count: Int): ByteArray {
            require(offset + count <= bound) { "pod read overruns its buffer at $offset" }
            val result = ByteArray(count)
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset.toLong(), result, 0, count)
            offset += count
            return result
        }

        /** Advances to the next 8-byte boundary (native parsers ignore padding). */
        fun align8() {
            val target = ((offset + 7) and 7.inv()).toLong()
            require(target <= bound) { "pod padding overruns its container" }
            offset = target.toInt()
        }
    }
}
