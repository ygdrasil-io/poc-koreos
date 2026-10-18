package org.graphiks.kadre.internal.wayland

/**
 * Conversions explicites entre offsets UTF-8 (octets, wire text-input-v3)
 * et indices UTF-16 (String Kadre) — D2 du design phase 6 : jamais couper
 * un code point, bornes de payload respectées, clamps honnêtes.
 */
internal object Utf8Utf16 {

    /**
     * Paires (indice UTF-16 de début de code point, offset octet). Un code
     * point astral occupe deux indices UTF-16 (surrogate pair) : ses
     * indices intérieurs n'apparaissent pas comme limites — un offset
     * octet au milieu de sa séquence clamps au début du code point.
     */
    private fun boundaries(text: String): List<Pair<Int, Int>> {
        val points = ArrayList<Pair<Int, Int>>()
        var byte = 0
        var index = 0
        while (index < text.length) {
            points.add(index to byte)
            val codePoint = text.codePointAt(index)
            byte += when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            index += if (codePoint > 0xFFFF) 2 else 1
        }
        points.add(text.length to byte)
        return points
    }

    /**
     * Convertit un offset octet UTF-8 en indice UTF-16, clampé à la limite
     * de code point la plus proche (un offset au milieu d'une séquence est
     * ramené au début de cette séquence — F2-1).
     */
    fun utf8ByteToUtf16Index(text: String, byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        val points = boundaries(text)
        if (byteOffset >= points.last().second) return text.length
        // la limite la plus grande dont l'offset octet est <= byteOffset
        var best = 0
        for ((index, byte) in points) {
            if (byte <= byteOffset) best = index else break
        }
        return best
    }

    /** Convertit un indice UTF-16 en offset octet UTF-8 (bornes respectées). */
    fun utf16IndexToUtf8Byte(text: String, index: Int): Int {
        if (index <= 0) return 0
        if (index >= text.length) return boundaries(text).last().second
        // clamp au début de code point si l'indice tombe dans une paire
        for ((pointIndex, byte) in boundaries(text)) {
            if (pointIndex >= index) return byte
        }
        return boundaries(text).last().second
    }

    /** Longueur en octets UTF-8 du texte. */
    fun utf8ByteLength(text: String): Int = boundaries(text).last().second
}
