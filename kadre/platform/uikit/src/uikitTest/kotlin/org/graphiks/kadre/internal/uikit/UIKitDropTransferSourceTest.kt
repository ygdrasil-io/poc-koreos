@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.failureOrNull
import org.graphiks.kadre.diagnostics.isSuccess
import org.graphiks.kadre.diagnostics.message
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La source de transfert DROP des cibles iOS et tvOS (`UIKitDropTransferSource`), prouvée PUR :
 * la logique d'item ([UIKitDropItem] — fente à payload, sémantique de [UIKitDropItem.collectBytes])
 * et de source ([UIKitDropTransferSource] — le close détache) tourne sans aucun type UIKit. Le pont
 * réel `NSItemProvider` → payload ([ukitPayloadFromProvider]) n'est ici que la PREUVE DE
 * COMPILATION (construction du payload, `totalBytes` honnêtement inconnu, route sans type lisible) —
 * sa preuve de COMPORTEMENT asynchrone vit dans les tests driver de la Task 4 (la completion
 * `loadItem` n'est pas pilotable en pur Kotlin, cf. spike Task 1).
 *
 * Cibles de mutation : l'échec fermé `payload-unavailable` (un payload non attaché n'est PAS une
 * lecture vide), le texte exact du message, le tranchage (au plus `maxChunkBytes`, queue plus
 * courte), l'EOF de taille inconnue (premier `null` ET tranche courte — sans seconde lecture), la
 * reprise par `Replayable` (le précédent AppKit), l'annulation de la coroutine collectrice (la
 * livraison s'arrête), le round-trip UTF-8 par `ofBytes`, et le close idempotent qui détache TOUT
 * en gardant les descripteurs (les snapshots restent, les handles meurent avec le drag).
 */
class UIKitDropTransferSourceTest {

    /** Un snapshot TEXTE garanti non nul — la table fermée mappe `public.utf8-plain-text`. */
    private fun utf8Snapshot(displayName: String?, sizeBytes: Long?): UIKitDropItemSnapshot =
        checkNotNull(ukitDropSnapshot(listOf("public.utf8-plain-text"), displayName, sizeBytes))

    // MARK: - l'item : le descripteur porte le snapshot TEL QUEL, le mode est Replayable

    @Test
    fun descriptorCarriesTheSnapshotVerbatimAndReadModeIsReplayable() {
        val snapshot = checkNotNull(
            ukitDropSnapshot(
                providerRegisteredTypes = listOf("public.file-url", "public.url"),
                displayName = "rapport.pdf",
                sizeBytes = 1024L,
            ),
        )
        val item = UIKitDropItem(snapshot)
        assertEquals("rapport.pdf", item.descriptor.displayName)
        assertEquals(1024L, item.descriptor.sizeBytes)
        assertEquals(listOf("application/octet-stream", "text/uri-list"), item.descriptor.mimeTypes)
        assertEquals(DropItemKind.File, item.descriptor.kind)
        assertEquals(
            DropItemReadMode.Replayable,
            item.readMode,
            "the AppKit precedent: drop reads replay until the source is closed",
        )
    }

    // MARK: - collectBytes : le payload absent est l'échec FERMÉ de la couture

    @Test
    fun collectWithoutPayloadIsTheClosedPayloadUnavailableFailure() = runBlocking {
        val item = UIKitDropItem(utf8Snapshot(displayName = "note.txt", sizeBytes = null))
        val failure = assertNotNull(
            item.collectBytes(8) { }.failureOrNull(),
            "a payload nobody attached is a FAILURE, never an empty read",
        )
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "drop", "payload-unavailable"),
            failure,
        )
        assertEquals(
            "Platform operation failed: UIKit/drop/payload-unavailable",
            failure.message,
            "the failure text names the platform, the domain and the code",
        )
    }

    // MARK: - collectBytes : maxChunkBytes hors domaine est une requête invalide

    @Test
    fun nonPositiveMaxChunkBytesIsAnInvalidRequestEvenBeforeThePayloadIsConsulted() = runBlocking {
        val bare = UIKitDropItem(utf8Snapshot(null, null))
        assertEquals(
            KadreFailure.InvalidRequest("maxChunkBytes"),
            bare.collectBytes(0) { }.failureOrNull(),
            "the invalid request wins over the missing payload (web parity)",
        )
        val fed = UIKitDropItem(utf8Snapshot(null, null))
        fed.attach(UIKitDropPayload.ofBytes(byteArrayOf(1, 2, 3)))
        assertEquals(KadreFailure.InvalidRequest("maxChunkBytes"), fed.collectBytes(-4) { }.failureOrNull())
    }

    // MARK: - collectBytes : le tranchage respecte maxChunkBytes, et Replayable rejoue

    @Test
    fun chunkSlicingRespectsMaxChunkBytesAndReplays() = runBlocking {
        val bytes = ByteArray(23) { (it + 1).toByte() }
        val item = UIKitDropItem(utf8Snapshot(displayName = "23.bin", sizeBytes = 23L))
        item.attach(UIKitDropPayload.ofBytes(bytes))
        val first = mutableListOf<ByteArray>()
        assertTrue(item.collectBytes(8) { first += it }.isSuccess)
        assertEquals(
            listOf(8, 8, 7),
            first.map { it.size },
            "at most maxChunkBytes per chunk, the tail shorter",
        )
        assertEquals(
            bytes.toList(),
            first.flatMap { it.toList() },
            "the chunks reassemble the source bytes in order",
        )
        val replay = mutableListOf<ByteArray>()
        assertTrue(item.collectBytes(8) { replay += it }.isSuccess)
        assertEquals(bytes.toList(), replay.flatMap { it.toList() }, "Replayable: a second read delivers everything again")
    }

    // MARK: - collectBytes : taille inconnue — l'EOF est le PREMIER chunk null

    @Test
    fun unknownSizeStreamingStopsAtTheFirstNullChunk() = runBlocking {
        val item = UIKitDropItem(utf8Snapshot(null, null))
        val requestedOffsets = mutableListOf<Long>()
        item.attach(
            UIKitDropPayload(totalBytes = null) { offset, count ->
                requestedOffsets += offset
                when (offset) {
                    0L -> ByteArray(count) { 1 }
                    count.toLong() -> ByteArray(count) { 2 }
                    else -> null
                }
            },
        )
        val delivered = mutableListOf<ByteArray>()
        assertTrue(item.collectBytes(8) { delivered += it }.isSuccess)
        assertEquals(
            listOf(0L, 8L, 16L),
            requestedOffsets,
            "offsets advance by the sizes actually delivered, one read past the last chunk asks for EOF",
        )
        assertEquals(2, delivered.size)
        assertEquals(16, delivered.sumOf { it.size })
    }

    @Test
    fun unknownSizeStreamingStopsAtAShortFinalChunkWithoutAnotherRead() = runBlocking {
        val item = UIKitDropItem(utf8Snapshot(null, null))
        var reads = 0
        item.attach(
            UIKitDropPayload(totalBytes = null) { _, count ->
                reads += 1
                ByteArray(count - 3) { 5 }
            },
        )
        val delivered = mutableListOf<ByteArray>()
        assertTrue(item.collectBytes(16) { delivered += it }.isSuccess)
        assertEquals(1, reads, "a short chunk IS the end of an unknown-size stream — no second read")
        assertEquals(1, delivered.size)
        assertEquals(13, delivered.single().size)
    }

    @Test
    fun anEmptyPayloadDeliversNothingAndSucceeds() = runBlocking {
        val item = UIKitDropItem(utf8Snapshot(null, null))
        item.attach(UIKitDropPayload.ofBytes(ByteArray(0)))
        val delivered = mutableListOf<ByteArray>()
        assertTrue(item.collectBytes(8) { delivered += it }.isSuccess)
        assertTrue(delivered.isEmpty(), "an empty source is a SUCCESS with zero chunks")
    }

    // MARK: - collectBytes : l'annulation de la coroutine collectrice arrête la livraison

    @Test
    fun cancellationOfTheCollectingCoroutineStopsChunkDelivery() = runBlocking {
        val item = UIKitDropItem(utf8Snapshot(null, null))
        val secondReadReached = CompletableDeferred<Unit>()
        item.attach(
            UIKitDropPayload(totalBytes = null) { offset, count ->
                if (offset == 0L) ByteArray(count) { 7 }
                else {
                    secondReadReached.complete(Unit)
                    awaitCancellation()
                }
            },
        )
        val delivered = mutableListOf<ByteArray>()
        val job = launch {
            try {
                item.collectBytes(8) { chunk -> delivered += chunk }
            } catch (_: CancellationException) {
                // attendu : la lecture s'arrête avec la coroutine collectrice
            }
        }
        secondReadReached.await()
        job.cancel()
        job.join()
        assertEquals(1, delivered.size, "cancellation stops delivery after the chunk already in flight")
    }

    // MARK: - collectBytes : une lecture qui jette est l'échec fermé byte-read

    @Test
    fun aFailingReadChunkIsTheClosedByteReadFailure() = runBlocking {
        val item = UIKitDropItem(utf8Snapshot(null, null))
        item.attach(
            UIKitDropPayload(totalBytes = null) { _, _ -> throw IllegalStateException("native read exploded") },
        )
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "drop", "byte-read"),
            item.collectBytes(8) { }.failureOrNull(),
        )
    }

    // MARK: - le round-trip UTF-8 du texte par ofBytes

    @Test
    fun utf8TextRoundTripsThroughOfBytes() = runBlocking {
        val text = "kilroy était ici — héllo drop, ☃ et vous"
        val bytes = text.encodeToByteArray()
        val item = UIKitDropItem(utf8Snapshot(displayName = "note.txt", sizeBytes = bytes.size.toLong()))
        item.attach(UIKitDropPayload.ofBytes(bytes))
        val collected = mutableListOf<ByteArray>()
        assertTrue(item.collectBytes(4) { chunk -> collected += chunk }.isSuccess)
        assertEquals(
            text,
            collected.flatMap { it.toList() }.toByteArray().decodeToString(),
            "the UTF-8 text round-trips exactly through the payload chunks",
        )
    }

    // MARK: - la fente à payload : le dernier attach gagne, attach(null) détache

    @Test
    fun theLastAttachWinsAndAttachNullDetaches() {
        val item = UIKitDropItem(utf8Snapshot(null, null))
        val first = UIKitDropPayload.ofBytes(byteArrayOf(1))
        val second = UIKitDropPayload.ofBytes(byteArrayOf(2))
        item.attach(first)
        assertEquals(first, item.payload)
        item.attach(second)
        assertEquals(second, item.payload, "the last attach wins, exactly as the drop re-reads it")
        item.attach(null)
        assertNull(item.payload)
    }

    // MARK: - la source : le close détache TOUT, idempotent, et garde les descripteurs

    @Test
    fun closeDetachesEveryEntryIsIdempotentAndKeepsTheDescriptors() = runBlocking {
        val first = UIKitDropItem(utf8Snapshot(displayName = "a.txt", sizeBytes = 3L))
        val second = UIKitDropItem(
            checkNotNull(ukitDropSnapshot(listOf("public.url"), null, null)),
        )
        first.attach(UIKitDropPayload.ofBytes(byteArrayOf(1, 2, 3)))
        second.attach(UIKitDropPayload.ofBytes(byteArrayOf(9)))
        val source = UIKitDropTransferSource(listOf(first, second))
        assertEquals(listOf(first, second), source.items, "the source carries exactly its entries")

        source.close()
        source.close() // idempotent : un second close n'atteint personne et ne jette pas

        assertNull(first.payload)
        assertNull(second.payload)
        assertEquals(
            "a.txt",
            first.descriptor.displayName,
            "the descriptors stay — the snapshots do, the handles die with the drag",
        )
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "drop", "payload-unavailable"),
            first.collectBytes(8) { }.failureOrNull(),
            "a read after close is the closed failure of the seam",
        )
    }

    // MARK: - le pont NSItemProvider : preuve de COMPILATION (le comportement est à la Task 4)

    @Test
    fun providerBridgeBuildsAPayloadOfUnknownSize() {
        val payload = ukitPayloadFromProvider(NSItemProvider(), preferredType = "public.utf8-plain-text")
        assertNull(
            payload.totalBytes,
            "provider sizes are unknown — the honest totalBytes stays null",
        )
    }

    @Test
    fun providerBridgeWithoutAnyReadableTypeResolvesAnHonestEmptyRead() = runBlocking {
        val payload = ukitPayloadFromProvider(NSItemProvider(), preferredType = null)
        assertNull(payload.totalBytes)
        assertNull(
            payload.readChunk(0, 8),
            "with no readable type the read resolves empty without ever touching the provider",
        )
    }

    // MARK: - les routes de livraison (ukitDropDeliveryBytes — la fix F-1 : l'URL n'est plus un mort-né)

    /**
     * La route URL : la livraison NORMALE de `public.url` ET de `public.file-url` (le fait du
     * spike Task 1) est l'NSURL — sa chaîne ABSOLUE devient les octets de la lecture. Avant la
     * fix, un drag d'URL annonçait `text/uri-list` dont AUCUNE lecture ne pouvait jamais rendre
     * compte (le `when` sans branche NSURL jetait → `byte-read`) : le mensonge que la couture
     * interdit.
     */
    @Test
    fun anNSURLDeliveryBecomesItsAbsoluteStringBytes() {
        val https = NSURL.URLWithString("https://example.com/kadre?q=1")!!
        assertEquals(
            "https://example.com/kadre?q=1".encodeToByteArray().toList(),
            checkNotNull(ukitDropDeliveryBytes(https)).toList(),
            "the absolute string of the URL is the honest content of a uri-list read",
        )
        val file = NSURL.URLWithString("file:///tmp/kadre-drop-spike.txt")!!
        assertEquals(
            "file:///tmp/kadre-drop-spike.txt".encodeToByteArray().toList(),
            checkNotNull(ukitDropDeliveryBytes(file)).toList(),
            "the file-url shape reads the same route — the consumer resolves the URL",
        )
    }

    @Test
    fun textAndDataDeliveriesKeepTheirRoutesAndAlienOnesStayNull() {
        assertEquals(
            "kilroy".encodeToByteArray().toList(),
            checkNotNull(ukitDropDeliveryBytes("kilroy")).toList(),
            "the text route is unchanged",
        )
        val raw = byteArrayOf(1, 2, 3)
        val data = raw.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), raw.size.toULong())
        }
        assertEquals(
            raw.toList(),
            checkNotNull(ukitDropDeliveryBytes(data)).toList(),
            "the data route is unchanged (owned bytes)",
        )
        assertNull(ukitDropDeliveryBytes(null), "a nil delivery is null — the caller makes the closed byte-read failure")
        assertNull(ukitDropDeliveryBytes(42), "an alien delivery is null, never a fabricated value")
    }

    /**
     * La forme exacte de la fix : un provider à `public.url` fait un descripteur qui annonce
     * `text/uri-list` (kind Uri, type préféré `public.url`), et la lecture — branchée sur les
     * octets qu'une livraison NSURL produit — RÉUSSIT et redonne l'URL. Le mort-né est parti.
     */
    @Test
    fun aUriDescriptorAdvertisedMimeIsReadableThroughTheNSURLRoute() = runBlocking {
        val types = listOf("public.url")
        val snapshot = checkNotNull(ukitDropSnapshot(types, displayName = "kadre", sizeBytes = null))
        assertEquals(listOf("text/uri-list"), snapshot.mimeTypes)
        val item = UIKitDropItem(snapshot)
        // Le chemin réel : le payload du port sert les octets de la livraison — ici la
        // livraison NSURL, convertie par le kernel ([ukitDropDeliveryBytes]).
        val delivered = checkNotNull(ukitDropDeliveryBytes(NSURL.URLWithString("https://kadre.example/drop")))
        item.attach(UIKitDropPayload.ofBytes(delivered))

        val chunks = mutableListOf<ByteArray>()
        assertTrue(item.collectBytes(8) { chunks += it }.isSuccess, "the advertised mime is READABLE")
        assertEquals("https://kadre.example/drop", chunks.flatMap { it.toList() }.toByteArray().decodeToString())
    }
}
