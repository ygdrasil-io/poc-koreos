package org.graphiks.kadre.internal.linux.pipewire

import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * O2 proofs for the SPA POD codec (design LINUX-PIPEWIRE-STREAM-DESIGN D8):
 * byte layouts pinned against the wire format, round-trips for every
 * supported value, and explicit refusal of what the codec cannot re-encode.
 *
 * Ids below are pinned from the SPA 0.2 headers through the kffi-pipewire
 * generated constants; the test keeps its own copies so the codec stays
 * pure Kotlin and runs wherever the JVM runs.
 */
internal class SpaPodTest {

    // SPA_TYPE_* / SPA_PARAM_* / SPA_FORMAT_* / SPA_CHOICE_* / SPA_VIDEO_FORMAT_*
    private val typeNone = 1
    private val typeId = 3
    private val typeInt = 4
    private val typeLong = 5
    private val typeFloat = 6
    private val typeDouble = 7
    private val typeString = 8
    private val typeBytes = 9
    private val typeRectangle = 10
    private val typeFraction = 11
    private val typeBitmap = 12
    private val typeArray = 13
    private val typeStruct = 14
    private val typeObject = 15
    private val typeSequence = 16
    private val typePointer = 17
    private val typeFd = 18
    private val typeChoice = 19

    private val paramEnumFormat = 3
    private val objectFormat = 262147
    private val keyMediaType = 1
    private val keyMediaSubtype = 2
    private val keyVideoFormat = 131073
    private val keyVideoSize = 131075
    private val keyVideoFramerate = 131076
    private val choiceEnum = 3
    private val choiceRange = 1
    private val mediaTypeVideo = 2
    private val mediaSubtypeRaw = 1
    private val videoFormatBgrx = 8

    @Test
    fun idPodGoldenBytesArePinned() {
        val bytes = SpaPodWriter.toByteArray(SpaValue.IdValue(0x2f))
        // header: body size 4, type Id — body 0x2f — 8-byte iteration padding
        assertContentEquals(
            byteArrayOf(
                4, 0, 0, 0, typeId.toByte(), 0, 0, 0,
                0x2f, 0, 0, 0, 0, 0, 0, 0,
            ),
            bytes,
        )
    }

    @Test
    fun longAndDoubleBodiesAreLittleEndian64Bit() {
        val long = SpaPodWriter.toByteArray(SpaValue.LongValue(0x0102030405060708L))
        assertEquals(typeLong.toByte(), long[4])
        assertEquals(0x08, long[8].toInt() and 0xff)
        assertEquals(0x07, long[9].toInt() and 0xff)
        assertEquals(0x01, long[15].toInt() and 0xff)

        val double = SpaPodWriter.toByteArray(SpaValue.DoubleValue(1.0))
        assertEquals(typeDouble.toByte(), double[4])
        assertEquals(0, double[8].toInt() and 0xff)
        assertEquals(0xf0, double[14].toInt() and 0xff)
        assertEquals(0x3f, double[15].toInt() and 0xff)
    }

    @Test
    fun enumFormatObjectLayoutMatchesTheWireFormat() {
        val pod = enumFormatPod()
        val bytes = SpaPodWriter.toByteArray(pod)
        // object body: 8 (type,id) + mediaType prop 24 + mediaSubtype prop 24
        // + format choice prop 48 + size choice prop 56 + framerate choice 48
        assertEquals(216, bytes.size)
        assertEquals(208, readInt(bytes, 0))
        assertEquals(typeObject, readInt(bytes, 4))
        assertEquals(objectFormat, readInt(bytes, 8))
        assertEquals(paramEnumFormat, readInt(bytes, 12))
        // first prop: key, flags, then an Id pod (size 4, type Id)
        assertEquals(keyMediaType, readInt(bytes, 16))
        assertEquals(0, readInt(bytes, 20))
        assertEquals(4, readInt(bytes, 24))
        assertEquals(typeId, readInt(bytes, 28))
        assertEquals(mediaTypeVideo, readInt(bytes, 32))
        // round trip through the reader
        assertEquals(pod, SpaPodReader.read(MemorySegment.ofArray(bytes)))
    }

    @Test
    fun everySupportedValueSurvivesTheRoundTrip() {
        val values = listOf(
            SpaValue.NoneValue,
            SpaValue.BoolValue(true),
            SpaValue.IdValue(7),
            SpaValue.IntValue(-3),
            SpaValue.LongValue(Long.MIN_VALUE),
            SpaValue.FloatValue(1.5f),
            SpaValue.DoubleValue(-2.25),
            SpaValue.StringValue("kadre"),
            SpaValue.BytesValue(byteArrayOf(1, 2, 3, 4, 5)),
            SpaValue.RectangleValue(320, 240),
            SpaValue.FractionValue(25, 1),
            SpaValue.FdValue(42),
            SpaValue.ArrayValue(listOf(SpaValue.IntValue(1), SpaValue.IntValue(2), SpaValue.IntValue(3))),
            SpaValue.StructValue(listOf(SpaValue.IntValue(1), SpaValue.StringValue("x"), SpaValue.NoneValue)),
            SpaValue.ChoiceValue(choiceRange, 0, listOf(SpaValue.FractionValue(0, 1), SpaValue.FractionValue(1, 60))),
            SpaValue.ObjectValue(
                objectFormat,
                paramEnumFormat,
                listOf(SpaValue.PropValue(keyMediaType, 0, SpaValue.IdValue(mediaTypeVideo))),
            ),
        )
        values.forEach { value ->
            val bytes = SpaPodWriter.toByteArray(value)
            assertEquals(0, bytes.size % 8, "pods must pad to the 8-byte iteration boundary")
            val decoded = SpaPodReader.read(MemorySegment.ofArray(bytes))
            assertEquals(value, decoded, "round trip lost fidelity for $value")
            assertContentEquals(bytes, SpaPodWriter.toByteArray(decoded), "re-encode must be byte-identical")
        }
    }

    @Test
    fun truncatedAndOverrunningPodsAreRefused() {
        // header itself truncated
        assertFailsWith<IllegalArgumentException> {
            SpaPodReader.read(MemorySegment.ofArray(ByteArray(4)))
        }
        // declared body overruns the buffer
        val truncated = SpaPodWriter.toByteArray(SpaValue.StructValue(listOf(SpaValue.IntValue(1)))).let {
            it.copyOf(it.size - 4)
        }
        assertFailsWith<IllegalArgumentException> {
            SpaPodReader.read(MemorySegment.ofArray(truncated))
        }
    }

    @Test
    fun outOfScopeAndHostilePodsAreRefusedExplicitly() {
        fun pod(type: Int, bodySize: Int, body: ByteArray): MemorySegment {
            val bytes = ByteArray(8 + bodySize + 4)
            writeInt(bytes, 0, bodySize)
            writeInt(bytes, 4, type)
            body.copyInto(bytes, 8)
            return MemorySegment.ofArray(bytes)
        }
        assertFailsWith<IllegalArgumentException>("pointer pods must be refused") {
            SpaPodReader.read(pod(typePointer, 16, ByteArray(16)))
        }
        assertFailsWith<IllegalArgumentException>("sequence pods must be refused") {
            SpaPodReader.read(pod(typeSequence, 8, ByteArray(8)))
        }
        assertFailsWith<IllegalArgumentException>("bitmap pods must be refused") {
            SpaPodReader.read(pod(typeBitmap, 4, ByteArray(4)))
        }
        assertFailsWith<IllegalArgumentException>("unknown types must be refused") {
            SpaPodReader.read(pod(77, 4, ByteArray(4)))
        }
        assertFailsWith<IllegalArgumentException>("bools must be 0 or 1") {
            SpaPodReader.read(pod(2, 4, byteArrayOf(2, 0, 0, 0)))
        }
        assertFailsWith<IllegalArgumentException>("strings must end with NUL") {
            SpaPodReader.read(pod(typeString, 3, "ab".encodeToByteArray() + 0x63))
        }
        assertFailsWith<IllegalArgumentException>("embedded NUL must be refused") {
            SpaPodReader.read(pod(typeString, 3, byteArrayOf(0x61, 0, 0x63)))
        }
        assertFailsWith<IllegalArgumentException>("fixed sizes are not negotiable") {
            SpaPodReader.read(pod(typeId, 8, ByteArray(8)))
        }
    }

    @Test
    fun writerRefusesWhatItCannotEncode() {
        assertFailsWith<IllegalArgumentException>("strings must not embed NUL") {
            SpaPodWriter.toByteArray(SpaValue.StringValue("a\u0000b"))
        }
        assertFailsWith<IllegalArgumentException>("choices need a default") {
            SpaPodWriter.toByteArray(SpaValue.ChoiceValue(choiceEnum, 0, emptyList()))
        }
        assertFailsWith<IllegalArgumentException>("choice children must be uniform") {
            SpaPodWriter.toByteArray(
                SpaValue.ChoiceValue(choiceEnum, 0, listOf(SpaValue.IdValue(1), SpaValue.IntValue(2))),
            )
        }
        assertFailsWith<IllegalArgumentException>("array children must be primitives") {
            SpaPodWriter.toByteArray(
                SpaValue.ArrayValue(listOf(SpaValue.StructValue(emptyList()))),
            )
        }
    }

    @Test
    fun paddingNeverCarriesPayloadBytes() {
        // a 4-byte body inside a struct leaves 4 padding bytes; the total
        // must stay a multiple of 8 and the payload starts at the next pod
        val pod = SpaPodWriter.toByteArray(
            SpaValue.StructValue(listOf(SpaValue.IntValue(5), SpaValue.IntValue(6))),
        )
        assertEquals(0, pod.size % 8)
        val decoded = SpaPodReader.read(MemorySegment.ofArray(pod)) as SpaValue.StructValue
        assertEquals(2, decoded.fields.size)
        assertEquals(5, (decoded.fields[0] as SpaValue.IntValue).value)
        assertEquals(6, (decoded.fields[1] as SpaValue.IntValue).value)
    }

    private fun enumFormatPod(): SpaValue.ObjectValue = SpaValue.ObjectValue(
        objectType = objectFormat,
        id = paramEnumFormat,
        properties = listOf(
            SpaValue.PropValue(keyMediaType, 0, SpaValue.IdValue(mediaTypeVideo)),
            SpaValue.PropValue(keyMediaSubtype, 0, SpaValue.IdValue(mediaSubtypeRaw)),
            SpaValue.PropValue(
                keyVideoFormat,
                0,
                SpaValue.ChoiceValue(
                    choiceEnum,
                    0,
                    listOf(SpaValue.IdValue(videoFormatBgrx), SpaValue.IdValue(12), SpaValue.IdValue(11)),
                ),
            ),
            SpaValue.PropValue(
                keyVideoSize,
                0,
                SpaValue.ChoiceValue(
                    choiceRange,
                    0,
                    listOf(
                        SpaValue.RectangleValue(320, 240),
                        SpaValue.RectangleValue(1, 1),
                        SpaValue.RectangleValue(1920, 1080),
                    ),
                ),
            ),
            SpaValue.PropValue(
                keyVideoFramerate,
                0,
                SpaValue.ChoiceValue(
                    choiceRange,
                    0,
                    listOf(SpaValue.FractionValue(0, 1), SpaValue.FractionValue(1, 60)),
                ),
            ),
        ),
    )

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    @Test
    fun goldenVectorTotalsStayAligned() {
        // sanity anchor for the EnumFormat golden: every prop padded to 8
        val pod = enumFormatPod()
        val bytes = SpaPodWriter.toByteArray(pod)
        assertTrue(bytes.size >= 8 + 200, "object pods carry their full body")
        assertEquals(208, bytes.size - 8)
    }
}
