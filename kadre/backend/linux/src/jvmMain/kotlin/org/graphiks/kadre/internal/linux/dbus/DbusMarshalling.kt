package org.graphiks.kadre.internal.linux.dbus

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * D-Bus wire-format values (spec v1). The codec is pure Kotlin — no FFM:
 * sockets live behind [org.graphiks.kffi.posix.LinuxPosix] in kffi, the
 * protocol policy lives here (design LINUX-CAPTURE-PORTAL-DESIGN D2).
 */
internal sealed interface DbusValue {
    data class ByteValue(val value: Byte) : DbusValue
    data class BooleanValue(val value: Boolean) : DbusValue
    data class Uint16Value(val value: Int) : DbusValue
    data class Int32Value(val value: Int) : DbusValue
    data class Uint32Value(val value: Long) : DbusValue
    data class Int64Value(val value: Long) : DbusValue
    data class DoubleValue(val value: Double) : DbusValue
    data class StringValue(val value: String) : DbusValue
    data class ObjectPathValue(val value: String) : DbusValue
    data class SignatureValue(val signature: String) : DbusValue

    /** [value] marshals with [signature] as its type. */
    data class VariantValue(val signature: String, val value: DbusValue) : DbusValue

    data class ArrayValue(val elementSignature: String, val elements: List<DbusValue>) : DbusValue
    data class StructValue(val members: List<DbusValue>) : DbusValue
    data class DictEntryValue(val key: DbusValue, val value: DbusValue) : DbusValue

    /** Index into the fd array delivered with the message (SCM_RIGHTS). */
    data class UnixFdValue(val index: Int) : DbusValue
}

/** One complete D-Bus signature string, iterable type by type. */
internal class DbusSignature(private val text: String) {
    fun typeAt(offset: Int): Pair<String, Int> {
        require(offset < text.length) { "signature cursor out of range: $text" }
        return when (val code = text[offset]) {
            'y', 'b', 'n', 'q', 'i', 'u', 'x', 't', 'd', 's', 'o', 'g', 'v', 'h' -> code.toString() to offset + 1
            'a' -> {
                val (element, next) = typeAt(offset + 1)
                "a$element" to next
            }
            '(' -> {
                var depth = 1
                var cursor = offset + 1
                while (depth > 0) {
                    require(cursor < text.length) { "unbalanced struct in signature: $text" }
                    if (text[cursor] == '(') depth++
                    if (text[cursor] == ')') depth--
                    cursor++
                }
                text.substring(offset, cursor) to cursor
            }
            '{' -> {
                var depth = 1
                var cursor = offset + 1
                while (depth > 0) {
                    require(cursor < text.length) { "unbalanced dict entry in signature: $text" }
                    if (text[cursor] == '{') depth++
                    if (text[cursor] == '}') depth--
                    cursor++
                }
                text.substring(offset, cursor) to cursor
            }
            else -> throw IllegalArgumentException("unknown type code '$code' in signature: $text")
        }
    }
}

/** Alignment rules of the wire format: strings 4, signatures 1, the rest by width, containers 8. */
internal object DbusAlignment {
    fun forTypeCode(code: Char): Int = when (code) {
        'y', 'b', 'g' -> 1
        'n', 'q' -> 2
        'i', 'u', 's', 'o', 'h' -> 4
        'x', 't', 'd' -> 8
        'a', '(', '{', 'v' -> 8
        else -> throw IllegalArgumentException("unknown type code '$code'")
    }
}

/** Growable little/big-endian writer enforcing D-Bus padding rules. */
internal class DbusWriter(endianness: ByteOrder = ByteOrder.LITTLE_ENDIAN) {
    private var buffer = ByteBuffer.allocate(64).order(endianness)
    val endiannessCode: Char = if (endianness == ByteOrder.LITTLE_ENDIAN) 'l' else 'B'

    fun size(): Int = buffer.position()
    fun toByteArray(): ByteArray = buffer.array().copyOf(buffer.position())

    private fun ensure(extra: Int) {
        if (buffer.position() + extra > buffer.capacity()) {
            val grown = ByteBuffer.allocate(maxOf(buffer.capacity() * 2, buffer.position() + extra)).order(buffer.order())
            grown.put(buffer.flip() as ByteBuffer)
            buffer = grown
        }
    }

    fun align(alignment: Int) {
        while (buffer.position() % alignment != 0) {
            ensure(1)
            buffer.put(0.toByte())
        }
    }

    fun byte(value: Byte) {
        ensure(1); buffer.put(value)
    }

    fun boolean(value: Boolean) {
        ensure(1); buffer.put(if (value) 1 else 0)
    }

    fun uint16(value: Int) {
        ensure(2); align(2); buffer.putShort(value.toShort())
    }

    fun int32(value: Int) {
        ensure(4); align(4); buffer.putInt(value)
    }

    fun uint32(value: Long) {
        ensure(4); align(4); buffer.putInt(value.toInt())
    }

    fun int64(value: Long) {
        ensure(8); align(8); buffer.putLong(value)
    }

    fun double(value: Double) {
        ensure(8); align(8); buffer.putDouble(value)
    }

    fun string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 0x3FFFFFFF) { "string exceeds the D-Bus limit" }
        uint32(bytes.size.toLong())
        bytes.forEach { byte(it) }
        byte(0)
    }

    fun signature(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 255) { "signature must be 1..255 bytes" }
        byte(bytes.size.toByte())
        bytes.forEach { byte(it) }
        byte(0)
    }

    fun raw(bytes: ByteArray) = bytes.forEach { byte(it) }
}

/** Cursor reader mirroring [DbusWriter]; every read advances past padding. */
internal class DbusReader(private val bytes: ByteArray, private val endianness: ByteOrder = ByteOrder.LITTLE_ENDIAN) {
    private var position = 0

    fun position(): Int = position

    fun remaining(): Int = bytes.size - position

    fun align(alignment: Int) {
        while (position % alignment != 0) {
            position++
        }
    }

    fun byte(): Byte {
        position += 1
        return bytes[position - 1]
    }

    fun boolean(): Boolean = byte() != 0.toByte()

    fun uint16(): Int {
        align(2)
        val value = ByteBuffer.wrap(bytes, position, 2).order(endianness).short.toInt() and 0xFFFF
        position += 2
        return value
    }

    fun int32(): Int {
        align(4)
        val value = ByteBuffer.wrap(bytes, position, 4).order(endianness).int
        position += 4
        return value
    }

    fun uint32(): Long = int32().toLong() and 0xFFFFFFFFL

    fun int64(): Long {
        align(8)
        val value = ByteBuffer.wrap(bytes, position, 8).order(endianness).long
        position += 8
        return value
    }

    fun double(): Double = Double.fromBits(int64())

    fun string(): String {
        val length = uint32().toInt()
        require(length >= 0 && position + length < bytes.size) { "string length out of range" }
        val value = String(bytes, position, length, Charsets.UTF_8)
        position += length
        require(byte() == 0.toByte()) { "string not NUL-terminated" }
        return value
    }

    fun signature(): String {
        val length = byte().toInt() and 0xFF
        val value = String(bytes, position, length, Charsets.UTF_8)
        position += length
        require(byte() == 0.toByte()) { "signature not NUL-terminated" }
        return value
    }
}
