package org.graphiks.kadre.internal.linux.dbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * O2 proofs of the D-Bus codec (design LINUX-CAPTURE-PORTAL-DESIGN D2,
 * plan task 2): round-trip identity of messages and bodies, refusal of
 * what cannot be re-encoded, and the wire layout invariants — byte
 * order, alignments, NUL terminators — checked structurally.
 */
class DbusCodecTest {

    private fun roundTrip(message: DbusMessage): DbusMessage {
        val encoded = message.encode()
        val decoded = DbusMessage.decode(encoded)
        assertEquals(encoded.toList(), decoded.encode().toList())
        return decoded
    }

    @Test
    fun emptyMethodCallRoundTripsByteForByte() {
        val message = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 1,
            fields = mapOf(
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/org/freedesktop/DBus"),
                DbusMessage.FIELD_DESTINATION to DbusValue.StringValue("org.freedesktop.DBus"),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("org.freedesktop.DBus"),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("Hello"),
            ),
            bodySignature = null,
            body = emptyList(),
        )
        val decoded = roundTrip(message)
        assertEquals("/org/freedesktop/DBus", decoded.path())
        assertEquals("org.freedesktop.DBus", decoded.destination())
        assertEquals("Hello", decoded.member())
        assertEquals(1, decoded.serial)
    }

    @Test
    fun wireLayoutInvariantsHoldOnAMinimalCall() {
        val encoded = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 1,
            fields = mapOf(
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/a"),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("m"),
            ),
            bodySignature = null,
            body = emptyList(),
        ).encode()

        assertEquals('l', encoded[0].toInt().toChar())
        assertEquals(1, encoded[1].toInt())
        assertEquals(0, encoded[2].toInt())
        assertEquals(1, encoded[3].toInt())
        assertEquals(0, encoded.size % 8, "every message is a multiple of 8 bytes")

        val fieldsLength = readUint32(encoded, 12)
        // 12 fixed + u32 array length = 16, already 8-aligned
        val firstEntry = 16
        assertEquals(0, firstEntry % 8)
        assertEquals(1, encoded[firstEntry].toInt())
        // variant signature: length byte, 'o', NUL
        assertEquals(1, encoded[firstEntry + 1].toInt())
        assertEquals('o', encoded[firstEntry + 2].toInt().toChar())
        // object path "/a": u32 length 2, bytes, NUL
        assertEquals(2, readUint32(encoded, firstEntry + 4))
        assertEquals('/', encoded[firstEntry + 8].toInt().toChar())
        assertEquals('a', encoded[firstEntry + 9].toInt().toChar())
        assertEquals(0, encoded[firstEntry + 10].toInt())
        assertTrue(fieldsLength in 1 until encoded.size)
    }

    private fun readUint32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    @Test
    fun bodyValuesRoundTripAcrossEverySupportedType() {
        val signature = "ybqiuxdsogv"
        val values = listOf<DbusValue>(
            DbusValue.ByteValue(7),
            DbusValue.BooleanValue(true),
            DbusValue.Uint16Value(0x1234),
            DbusValue.Int32Value(-42),
            DbusValue.Uint32Value(0xFFFF_FFFFL),
            DbusValue.Int64Value(Long.MIN_VALUE / 2),
            DbusValue.DoubleValue(3.25),
            DbusValue.StringValue("héhé-∅-path"),
            DbusValue.ObjectPathValue("/org/a/b"),
            DbusValue.SignatureValue("a{sv}"),
            DbusValue.VariantValue("i", DbusValue.Int32Value(-5)),
        )
        val body = DbusMessage.encodeBody(signature, values, java.nio.ByteOrder.LITTLE_ENDIAN)
        val reader = DbusReader(body)
        val parsed = DbusMessage.signaturesOf(signature).map { DbusMessage.readValue(reader, it) }
        assertEquals(values.size, parsed.size)
        assertEquals("héhé-∅-path", (parsed[7] as DbusValue.StringValue).value)
        assertEquals("a{sv}", (parsed[9] as DbusValue.SignatureValue).signature)
        assertEquals(-5, ((parsed[10] as DbusValue.VariantValue).value as DbusValue.Int32Value).value)
        val reencoded = DbusMessage.encodeBody(signature, parsed, java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(body.toList(), reencoded.toList())
    }

    @Test
    fun dictEntryArrayRoundTrips() {
        val message = DbusMessage(
            type = DbusMessage.TYPE_METHOD_RETURN,
            flags = 0,
            serial = 9,
            fields = mapOf(
                DbusMessage.FIELD_REPLY_SERIAL to DbusValue.Uint32Value(4),
                DbusMessage.FIELD_SENDER to DbusValue.StringValue(":1.7"),
            ),
            bodySignature = "a{sv}",
            body = listOf(
                DbusValue.ArrayValue(
                    elementSignature = "{sv}",
                    elements = listOf(
                        DbusValue.DictEntryValue(
                            DbusValue.StringValue("session_handle"),
                            DbusValue.VariantValue("s", DbusValue.StringValue("/org/freedesktop/portal/desktop/session/1")),
                        ),
                        DbusValue.DictEntryValue(
                            DbusValue.StringValue("devices"),
                            DbusValue.VariantValue("u", DbusValue.Uint32Value(1)),
                        ),
                    ),
                ),
            ),
        )
        val decoded = roundTrip(message)
        val result = decoded.body.single() as DbusValue.ArrayValue
        assertEquals(2, result.elements.size)
        val first = result.elements[0] as DbusValue.DictEntryValue
        assertEquals("session_handle", (first.key as DbusValue.StringValue).value)
        val handle = first.value as DbusValue.VariantValue
        assertEquals("s", handle.signature)
        assertEquals("/org/freedesktop/portal/desktop/session/1", (handle.value as DbusValue.StringValue).value)
    }

    @Test
    fun uint64IsRefusedInsteadOfSilentlyRetyped() {
        assertFailsWith<IllegalArgumentException> {
            DbusMessage.encodeBody("t", listOf(DbusValue.Int64Value(1)), java.nio.ByteOrder.LITTLE_ENDIAN)
        }
    }

    @Test
    fun bigEndianRoundTripsIdentically() {
        val message = DbusMessage(
            type = DbusMessage.TYPE_SIGNAL,
            flags = DbusMessage.FLAG_NO_AUTO_START,
            serial = 77,
            fields = mapOf(
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/s"),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("i.f"),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("sig"),
            ),
            bodySignature = "i",
            body = listOf(DbusValue.Int32Value(-3)),
        )
        val encoded = message.encode(java.nio.ByteOrder.BIG_ENDIAN)
        assertEquals('B', encoded[0].toInt().toChar())
        val decoded = DbusMessage.decode(encoded)
        assertEquals(-3, (decoded.body.single() as DbusValue.Int32Value).value)
        val reencoded = decoded.encode(java.nio.ByteOrder.BIG_ENDIAN)
        assertEquals(encoded.toList(), reencoded.toList())
    }
}
