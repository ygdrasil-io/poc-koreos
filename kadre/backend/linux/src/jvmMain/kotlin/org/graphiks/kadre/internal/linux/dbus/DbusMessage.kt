package org.graphiks.kadre.internal.linux.dbus

import java.nio.ByteOrder

/**
 * One D-Bus message: fixed header, typed header fields, body values.
 * Round-trip identity (encode→decode→encode) is the O2 contract —
 * anything the codec cannot re-encode identically is refused.
 *
 * Supported body types: y b q i u x d s o g v a ( ) { } h. The uint64
 * code 't' is refused (not needed by the portal surface) rather than
 * silently re-encoded as a different type.
 */
internal class DbusMessage(
    val type: Int, // 1 method_call, 2 method_return, 3 error, 4 signal
    val flags: Int,
    val serial: Int,
    val fields: Map<Int, DbusValue>,
    val bodySignature: String?,
    val body: List<DbusValue>,
) {
    fun path(): String? = (fields[FIELD_PATH] as? DbusValue.ObjectPathValue)?.value
    fun interfaceName(): String? = (fields[FIELD_INTERFACE] as? DbusValue.StringValue)?.value
    fun member(): String? = (fields[FIELD_MEMBER] as? DbusValue.StringValue)?.value
    fun errorName(): String? = (fields[FIELD_ERROR_NAME] as? DbusValue.StringValue)?.value
    fun replySerial(): Int? = (fields[FIELD_REPLY_SERIAL] as? DbusValue.Uint32Value)?.value?.toInt()
    fun destination(): String? = (fields[FIELD_DESTINATION] as? DbusValue.StringValue)?.value
    fun sender(): String? = (fields[FIELD_SENDER] as? DbusValue.StringValue)?.value

    fun encode(endianness: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray {
        val bodyBytes = encodeBody(bodySignature, body, endianness)
        val writer = DbusWriter(endianness)
        writer.byte(if (endianness == ByteOrder.LITTLE_ENDIAN) 'l'.code.toByte() else 'B'.code.toByte())
        writer.byte(type.toByte())
        writer.byte(flags.toByte())
        writer.byte(PROTOCOL_VERSION.toByte())
        writer.uint32(bodyBytes.size.toLong())
        writer.uint32(serial.toLong())
        writeHeaderFields(writer, fields, bodySignature)
        // the body begins at the next 8-byte boundary
        writer.align(8)
        writer.raw(bodyBytes)
        return writer.toByteArray()
    }

    companion object {
        const val TYPE_METHOD_CALL = 1
        const val TYPE_METHOD_RETURN = 2
        const val TYPE_ERROR = 3
        const val TYPE_SIGNAL = 4

        const val FLAG_NO_REPLY_EXPECTED = 0x1
        const val FLAG_NO_AUTO_START = 0x2

        const val FIELD_PATH = 1
        const val FIELD_INTERFACE = 2
        const val FIELD_MEMBER = 3
        const val FIELD_ERROR_NAME = 4
        const val FIELD_REPLY_SERIAL = 5
        const val FIELD_DESTINATION = 6
        const val FIELD_SENDER = 7
        const val FIELD_SIGNATURE = 8
        const val FIELD_UNIX_FDS = 9

        const val PROTOCOL_VERSION = 1

        fun signatureOf(value: DbusValue): String = when (value) {
            is DbusValue.ByteValue -> "y"
            is DbusValue.BooleanValue -> "b"
            is DbusValue.Uint16Value -> "q"
            is DbusValue.Int32Value -> "i"
            is DbusValue.Uint32Value -> "u"
            is DbusValue.Int64Value -> "x"
            is DbusValue.DoubleValue -> "d"
            is DbusValue.StringValue -> "s"
            is DbusValue.ObjectPathValue -> "o"
            is DbusValue.SignatureValue -> "g"
            is DbusValue.VariantValue -> "v"
            is DbusValue.ArrayValue -> "a${value.elementSignature}"
            is DbusValue.StructValue -> "(${value.members.joinToString("") { signatureOf(it) }})"
            is DbusValue.DictEntryValue -> "{${signatureOf(value.key)}${signatureOf(value.value)}}"
            is DbusValue.UnixFdValue -> "h"
        }

        /** Splits a signature into its complete types. */
        fun signaturesOf(signature: String): List<String> {
            val types = ArrayList<String>()
            var offset = 0
            val parser = DbusSignature(signature)
            while (offset < signature.length) {
                val (type, next) = parser.typeAt(offset)
                types.add(type)
                offset = next
            }
            return types
        }

        /** Encodes values against [signature]; refuses arity and type mismatches loudly. */
        fun encodeBody(signature: String?, values: List<DbusValue>, endianness: ByteOrder): ByteArray {
            if (signature.isNullOrEmpty()) {
                require(values.isEmpty()) { "no signature but ${values.size} body values" }
                return ByteArray(0)
            }
            val parsed = signaturesOf(signature)
            require(parsed.size == values.size) { "signature '$signature' wants ${parsed.size} values, got ${values.size}" }
            val writer = DbusWriter(endianness)
            values.forEachIndexed { index, value -> writeValue(writer, parsed[index], value) }
            return writer.toByteArray()
        }

        fun writeValue(writer: DbusWriter, signature: String, value: DbusValue) {
            require(signature.isNotEmpty()) { "empty type in signature" }
            when (signature[0]) {
                'y' -> writer.byte((value as DbusValue.ByteValue).value)
                'b' -> writer.boolean((value as DbusValue.BooleanValue).value)
                'q' -> writer.uint16((value as DbusValue.Uint16Value).value)
                'i' -> writer.int32((value as DbusValue.Int32Value).value)
                'u' -> writer.uint32((value as DbusValue.Uint32Value).value)
                'x' -> writer.int64((value as DbusValue.Int64Value).value)
                'd' -> writer.double((value as DbusValue.DoubleValue).value)
                't' -> throw IllegalArgumentException("uint64 't' is not part of the supported surface")
                's' -> writer.string((value as DbusValue.StringValue).value)
                'o' -> writer.string((value as DbusValue.ObjectPathValue).value)
                'g' -> writer.signature((value as DbusValue.SignatureValue).signature)
                'v' -> {
                    val variant = value as DbusValue.VariantValue
                    // the variant signature must be one complete type and
                    // never 'v' itself (spec); the real bus drops violators
                    require(variant.signature.length == 1 || variant.signature.first() != 'v') {
                        "variant signature may not be 'v'"
                    }
                    require(variant.signature != "v") { "a variant cannot wrap another variant" }
                    writer.signature(variant.signature)
                    writeValue(writer, variant.signature, variant.value)
                }
                'a' -> writeArray(writer, signature.substring(1), value as DbusValue.ArrayValue)
                '(' -> writeStruct(writer, signature, value as DbusValue.StructValue)
                '{' -> writeDictEntry(writer, signature, value as DbusValue.DictEntryValue)
                'h' -> writer.uint32((value as DbusValue.UnixFdValue).index.toLong())
                else -> throw IllegalArgumentException("unknown type code '${signature[0]}'")
            }
        }

        /**
         * Array layout: u32 payload length, padding to the element
         * alignment, then elements back to back. The payload is built in
         * a scratch writer whose origin satisfies the element alignment,
         * so scratch-relative padding equals the absolute one (every D-Bus
         * alignment divides 8).
         */
        private fun writeArray(writer: DbusWriter, elementSignature: String, value: DbusValue.ArrayValue) {
            require(value.elementSignature == elementSignature) {
                "array element signature mismatch: ${value.elementSignature} vs $elementSignature"
            }
            val scratch = DbusWriter(orderOf(writer.endiannessCode))
            value.elements.forEach { writeValue(scratch, elementSignature, it) }
            val payload = scratch.toByteArray()
            writer.uint32(payload.size.toLong())
            writer.align(DbusAlignment.forTypeCode(elementSignature[0]))
            writer.raw(payload)
        }

        private fun writeStruct(writer: DbusWriter, signature: String, value: DbusValue.StructValue) {
            val memberSignatures = signaturesOf(signature.substring(1, signature.length - 1))
            require(memberSignatures.size == value.members.size) { "struct arity mismatch" }
            writer.align(8)
            value.members.forEachIndexed { index, member -> writeValue(writer, memberSignatures[index], member) }
        }

        private fun writeDictEntry(writer: DbusWriter, signature: String, value: DbusValue.DictEntryValue) {
            val inner = signature.substring(1, signature.length - 1)
            val (keyType, restOffset) = DbusSignature(inner).typeAt(0)
            val valueType = inner.substring(restOffset)
            writer.align(8)
            writeValue(writer, keyType, value.key)
            writeValue(writer, valueType, value.value)
        }

        /**
         * The a(yv) header array: u32 byte length, padding to 8, then
         * (code, signature, value) entries each starting 8-aligned. The
         * payload is assembled in a scratch writer at origin 0 — the
         * writer aligns each entry, and the origin-0 convention makes
         * the scratch alignments equal the absolute ones once the payload
         * lands 8-aligned in the parent.
         */
        private fun writeHeaderFields(writer: DbusWriter, fields: Map<Int, DbusValue>, signature: String?) {
            val scratch = DbusWriter(orderOf(writer.endiannessCode))
            fun entry(code: Int, value: DbusValue) {
                scratch.align(8)
                scratch.byte(code.toByte())
                val variantSignature = signatureOf(value)
                scratch.signature(variantSignature)
                writeValue(scratch, variantSignature, value)
            }
            fields.toSortedMap().forEach { (code, value) -> entry(code, value) }
            if (!signature.isNullOrEmpty()) {
                entry(FIELD_SIGNATURE, DbusValue.SignatureValue(signature))
            }
            val payload = scratch.toByteArray()
            writer.uint32(payload.size.toLong())
            writer.align(8)
            writer.raw(payload)
        }

        private fun orderOf(code: Char): ByteOrder =
            if (code == 'l') ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        /** Parses a complete message; [unixFdCount] rides alongside for 'h'. */
        fun decode(bytes: ByteArray, unixFdCount: Int = 0): DbusMessage {
            require(bytes.size >= 16) { "shorter than the fixed header" }
            val endianness = when (bytes[0].toInt().toChar()) {
                'l' -> ByteOrder.LITTLE_ENDIAN
                'B' -> ByteOrder.BIG_ENDIAN
                else -> throw IllegalArgumentException("unknown endianness byte '${bytes[0]}'")
            }
            val type = bytes[1].toInt()
            val flags = bytes[2].toInt()
            val version = bytes[3].toInt()
            require(version == PROTOCOL_VERSION) { "unsupported protocol version $version" }
            val reader = DbusReader(bytes, endianness)
            reader.byte(); reader.byte(); reader.byte(); reader.byte()
            val bodyLength = reader.uint32().toInt()
            val serial = reader.uint32().toInt()

            // a(yv): length, 8-padding, entries
            val fieldsLength = reader.uint32().toInt()
            reader.align(8)
            val fieldsEnd = reader.position() + fieldsLength
            val fields = HashMap<Int, DbusValue>()
            while (reader.position() < fieldsEnd) {
                reader.align(8)
                val code = reader.byte().toInt()
                val variantSignature = reader.signature()
                fields[code] = readValue(reader, variantSignature)
            }
            require(reader.position() == fieldsEnd) { "header fields length mismatch" }

            // the signature field is canonicalized into bodySignature —
            // re-encoding appends it from there, never from the map
            val signature = fields.remove(FIELD_SIGNATURE)?.let { it as DbusValue.SignatureValue }?.signature
            reader.align(8)
            val body = if (signature.isNullOrEmpty()) {
                require(bodyLength == 0) { "body present without signature" }
                emptyList()
            } else {
                val types = signaturesOf(signature)
                types.map { readValue(reader, it) }
            }
            return DbusMessage(
                type = type,
                flags = flags,
                serial = serial,
                fields = fields,
                bodySignature = signature?.takeIf { it.isNotEmpty() },
                body = body,
            )
        }

        fun readValue(reader: DbusReader, signature: String): DbusValue {
            require(signature.isNotEmpty()) { "empty type" }
            return when (signature[0]) {
                'y' -> DbusValue.ByteValue(reader.byte())
                'b' -> DbusValue.BooleanValue(reader.boolean())
                'q' -> DbusValue.Uint16Value(reader.uint16())
                'i' -> DbusValue.Int32Value(reader.int32())
                'u' -> DbusValue.Uint32Value(reader.uint32())
                'x' -> DbusValue.Int64Value(reader.int64())
                'd' -> DbusValue.DoubleValue(reader.double())
                's' -> DbusValue.StringValue(reader.string())
                'o' -> DbusValue.ObjectPathValue(reader.string())
                'g' -> DbusValue.SignatureValue(reader.signature())
                'v' -> {
                    val inner = reader.signature()
                    DbusValue.VariantValue(inner, readValue(reader, inner))
                }
                'a' -> {
                    val elementSignature = signature.substring(1)
                    val payloadLength = reader.uint32().toInt()
                    reader.align(DbusAlignment.forTypeCode(elementSignature[0]))
                    val end = reader.position() + payloadLength
                    val elements = ArrayList<DbusValue>()
                    while (reader.position() < end) {
                        elements.add(readValue(reader, elementSignature))
                    }
                    require(reader.position() == end) { "array payload length mismatch" }
                    DbusValue.ArrayValue(elementSignature, elements)
                }
                '(' -> {
                    reader.align(8)
                    val members = signaturesOf(signature.substring(1, signature.length - 1)).map { readValue(reader, it) }
                    DbusValue.StructValue(members)
                }
                '{' -> {
                    reader.align(8)
                    val inner = signature.substring(1, signature.length - 1)
                    val (keyType, restOffset) = DbusSignature(inner).typeAt(0)
                    val key = readValue(reader, keyType)
                    val value = readValue(reader, inner.substring(restOffset))
                    DbusValue.DictEntryValue(key, value)
                }
                'h' -> DbusValue.UnixFdValue(reader.uint32().toInt())
                else -> throw IllegalArgumentException("unknown type code '${signature[0]}'")
            }
        }
    }
}
