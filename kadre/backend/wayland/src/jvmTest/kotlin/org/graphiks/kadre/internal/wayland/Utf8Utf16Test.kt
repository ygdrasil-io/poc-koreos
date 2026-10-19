package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Preuves O2 de la conversion UTF-8 ↔ UTF-16 (D2/SN-1 phase 6) : emoji
 * (4 octets, surrogate pair), accents combinés, bornes et clamps.
 */
class Utf8Utf16Test {

    @Test
    fun asciiRoundTrips() {
        assertEquals(5, Utf8Utf16.utf8ByteToUtf16Index("hello", 5))
        assertEquals(5, Utf8Utf16.utf16IndexToUtf8Byte("hello", 5))
        assertEquals(5, Utf8Utf16.utf8ByteLength("hello"))
    }

    @Test
    fun emojiOffsetsNeverSplitACodePoint() {
        // 🎉 = F0 9F 8E 89 (4 octets, surrogate pair en UTF-16)
        val text = "a🎉b"
        assertEquals(6, Utf8Utf16.utf8ByteLength(text))
        assertEquals(1, Utf8Utf16.utf8ByteToUtf16Index(text, 1))
        // un offset au milieu de la séquence emoji clamp au début du code point
        assertEquals(1, Utf8Utf16.utf8ByteToUtf16Index(text, 2))
        assertEquals(1, Utf8Utf16.utf8ByteToUtf16Index(text, 3))
        assertEquals(1, Utf8Utf16.utf8ByteToUtf16Index(text, 2))
        assertEquals(3, Utf8Utf16.utf8ByteToUtf16Index(text, 5))
        assertEquals(4, Utf8Utf16.utf8ByteToUtf16Index(text, 6))
        assertEquals(5, Utf8Utf16.utf16IndexToUtf8Byte(text, 2))
        assertEquals(5, Utf8Utf16.utf16IndexToUtf8Byte(text, 3))
    }

    @Test
    fun combiningAccentsStayWhole() {
        // e + combining acute = 3 + 2 octets, 2 code points, 2 chars UTF-16
        val text = "e\u0301x"
        assertEquals(4, Utf8Utf16.utf8ByteLength(text))
        // byte 2 = milieu du combining : clamp au début du code point (index 1)
        assertEquals(1, Utf8Utf16.utf8ByteToUtf16Index(text, 2))
        // byte 3 = début de 'x' (index 2) ; byte 4 = fin de chaîne
        assertEquals(2, Utf8Utf16.utf8ByteToUtf16Index(text, 3))
        assertEquals(3, Utf8Utf16.utf8ByteToUtf16Index(text, 4))
    }

    @Test
    fun boundsAreClampedHonestly() {
        val text = "hé"
        assertEquals(0, Utf8Utf16.utf8ByteToUtf16Index(text, -5))
        assertEquals(2, Utf8Utf16.utf8ByteToUtf16Index(text, 500))
        assertEquals(0, Utf8Utf16.utf16IndexToUtf8Byte(text, -1))
        assertEquals(3, Utf8Utf16.utf16IndexToUtf8Byte(text, 99))
    }
}
