package org.graphiks.kadre.internal.uikit

import kotlin.math.min

/**
 * Une lecture d'octets paresseuse derrière un item droppé — le miroir d'iOS/tvOS de
 * `WebDropPayload` (`WebDropStimulus.kt:109-123`) : la même forme, le même contrat, porté par un
 * kernel PUR (aucun type UIKit — le handle natif de la source vit dans la closure du port qui
 * construit le payload, jamais ici).
 *
 * [totalBytes] est la taille que la lecture connaît, ou `null` quand elle doit découvrir la fin
 * à une lecture courte. [readChunk] livre UNE tranche fraîche, propriété de l'appelant, d'au plus
 * `count` octets depuis `offset` — `null` quand il n'y a plus rien — et peut suspendre : la
 * livraison d'`NSItemProvider` est asynchrone (la suspension est le pont de la Task 2). Le
 * handle lui-même ne quitte jamais la closure.
 */
internal class UIKitDropPayload(
    val totalBytes: Long?,
    internal val readChunk: suspend (offset: Long, count: Int) -> ByteArray?,
) {
    internal companion object {
        /** Construit un payload depuis un tableau d'octets déjà possédé, livré tranche par tranche. */
        internal fun ofBytes(bytes: ByteArray): UIKitDropPayload =
            UIKitDropPayload(bytes.size.toLong()) { offset, count ->
                when {
                    offset >= bytes.size.toLong() -> null
                    else -> bytes.copyOfRange(offset.toInt(), min(bytes.size.toLong(), offset + count).toInt())
                }
            }
    }
}
