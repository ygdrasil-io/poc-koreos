package org.graphiks.kadre.platform.android

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.internal.runtime.RuntimeDropItemSource
import org.graphiks.kadre.internal.runtime.RuntimeDropTransfer
import org.graphiks.kadre.internal.runtime.RuntimeDropTransferBudget
import org.graphiks.kadre.policy.KadrePolicies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La source de transfert drop Android (Task 1, phase 6 Android) — le côté CONTENU du drag :
 * la traduction du snapshot copié d'un clip (aucun type SDK retenu — roadmap :216) en offres
 * communes, et les lectures bornées derrière elle, prouvées contre la vraie machinerie du
 * runtime ([RuntimeDropTransfer] construit comme le runtime le construit — friend du runtime).
 *
 * Les règles de traduction sont le miroir de `WebDropStimulus.kt:67-99` : un mime est gardé
 * canonisé en minuscules ou droppé (jamais fabriqué), un item dont TOUS les mimes sont droppés
 * est droppé entier, un item uri-backed est `DropItemKind.Uri` (contenu lisible via l'opener
 * injecté), un texte pur sans uri est `DropItemKind.Text` en `Replayable` (le texte d'un clip
 * est déjà en mémoire — il rejoue sans l'opener), et uri+texte ensemble donne l'uri pour le
 * contenu avec le texte devenant indice d'affichage quand aucun indice n'existe.
 *
 * Les huit cas, bullets du gate :
 * 1. `snapshotTranslationCanonicalizesAndDropsEmptyMimes` — canonisation minuscule, vide droppé,
 *    doublons dédupliqués (première occurrence), item sans contenu droppé, item tout-mimes-droppés
 *    droppé, uri+texte → l'uri gagne et le texte devient l'indice absent ;
 * 2. `collectBytesBoundedByChunkAndMaxBytes` — stream de 10 008 octets, chunk 4 096 → trois
 *    callbacks (4 096, 4 096, 1 816) ; à travers le VRAI `RuntimeDropTransfer`, `maxBytes`
 *    dépassé avec taille inconnue → préfixe livré puis `Limit(DropItem)` (DESIGN §10.4 : une
 *    lecture taille-inconnue peut livrer un préfixe avant d'échouer) ;
 * 3. `closeExactlyOnceAndReadAfterCloseFails` — le stream se ferme UNE fois (compteur) : à la
 *    fin naturelle, au close du owner pendant une lecture suspendue, jamais deux fois ; double
 *    close du source = no-op ; un item jamais lu ne possède AUCUN stream (l'ouverture est
 *    paresseuse) ; `collectBytes` après close owner → `Closed(DropItem)`, zéro octet ;
 * 4. `resolverRefusalIsARealFailureNotAFakeRead` — opener `null` (permission absente) ou
 *    lançant → `PlatformFailure(Android, "drop", "resolver-refused")`, ZÉRO octet livré —
 *    « une permission absente ne devient pas un faux drop lisible » ;
 * 5. `concurrentReadsAreRejected` — deux `collectBytes` sur le même item → le second
 *    `AlreadyInUse(DropTransfer)` (contrat runtime `RuntimeDropTransfer.kt:303`), sans
 *    interblocage : la garde refuse, elle n'attend pas ;
 * 6. `textItemReplaysWithoutResolver` — l'item Text rejoue ses octets snapshotés SANS jamais
 *    toucher l'opener (compteur d'ouvertures à zéro), deux lectures successives identiques.
 *
 * Aucun type SDK n'est instancié : l'opener est un fake, les streams sont des
 * [CloseCountingStream] du test, le snapshot est des valeurs copiées — exactement la forme
 * que la production transposera depuis `ClipData` côté glue.
 */
class AndroidDropTransferSourceTest {

    // -----------------------------------------------------------------------
    // Cas 1 — traduction : canonisation, drops, kind, readMode, indice d'affichage
    // -----------------------------------------------------------------------

    @Test
    fun snapshotTranslationCanonicalizesAndDropsEmptyMimes() {
        val opener = FakeOpener()
        val snapshot = AndroidClipSnapshot(
            listOf(
                // A — mimes canonisés en minuscules, vide droppé, ordre SDK préservé ; uri → Uri.
                AndroidClipItem(
                    uri = "content://kadre.test/a",
                    text = null,
                    mimeTypes = listOf("TEXT/PLAIN", "", "application/pdf"),
                    displayNameHint = null,
                ),
                // B — texte pur sans uri → Text, Replayable (le contenu est déjà en mémoire).
                AndroidClipItem(
                    uri = null,
                    text = "hello",
                    mimeTypes = listOf("TEXT/PLAIN"),
                    displayNameHint = null,
                ),
                // C — aucun contenu (ni uri ni texte) → l'item est droppé entier.
                AndroidClipItem(
                    uri = null,
                    text = null,
                    mimeTypes = listOf("application/pdf"),
                    displayNameHint = "c.pdf",
                ),
                // D — tous les mimes droppés → l'item est droppé entier.
                AndroidClipItem(
                    uri = null,
                    text = "hello",
                    mimeTypes = listOf("", "NOT/A MIME"),
                    displayNameHint = null,
                ),
                // E — uri+texte : l'uri gagne le contenu, le texte devient l'indice absent.
                AndroidClipItem(
                    uri = "content://kadre.test/e",
                    text = "E label",
                    mimeTypes = listOf("application/pdf"),
                    displayNameHint = null,
                ),
                // F — l'indice explicite gagne sur le texte.
                AndroidClipItem(
                    uri = "content://kadre.test/f",
                    text = "t",
                    mimeTypes = listOf("application/pdf"),
                    displayNameHint = "F.pdf",
                ),
                // G — doublons après canonisation : première occurrence gardée (le descripteur
                // refuse les doublons — TextDropRaw.kt:107).
                AndroidClipItem(
                    uri = null,
                    text = "dup",
                    mimeTypes = listOf("TEXT/PLAIN", "text/plain"),
                    displayNameHint = null,
                ),
            ),
        )

        val source = AndroidDropTransferSource(snapshot, opener)

        assertEquals(
            listOf(DropItemKind.Uri, DropItemKind.Text, DropItemKind.Uri, DropItemKind.Uri, DropItemKind.Text),
            source.items.map { it.descriptor.kind },
            "the uri wins the content when uri and text coexist; a text-only item is Text; the two content-less items are dropped entirely",
        )
        assertEquals(
            DropItemDescriptor(
                displayName = null,
                sizeBytes = null,
                mimeTypes = listOf("text/plain", "application/pdf"),
                kind = DropItemKind.Uri,
            ),
            source.items[0].descriptor,
            "mimes are canonicalized lowercase, the empty entry is dropped, SDK order preserved, the unknown size stays null",
        )
        assertEquals(DropItemReadMode.SingleUse, source.items[0].readMode)
        assertEquals(
            DropItemDescriptor(
                displayName = null,
                sizeBytes = null,
                mimeTypes = listOf("text/plain"),
                kind = DropItemKind.Text,
            ),
            source.items[1].descriptor,
            "a pure-text item carries its canonical mime, no display name, no invented size",
        )
        assertEquals(DropItemReadMode.Replayable, source.items[1].readMode)
        assertEquals("E label", source.items[2].descriptor.displayName, "the text becomes the display hint when no hint exists")
        assertEquals("F.pdf", source.items[3].descriptor.displayName, "an explicit hint wins over the text")
        assertEquals(
            listOf("text/plain"),
            source.items[4].descriptor.mimeTypes,
            "duplicates after canonicalization keep their first occurrence — the descriptor refuses duplicates",
        )
        assertEquals(0, opener.opens, "the translation consults no resolver — the content reads stay lazy")
    }

    // -----------------------------------------------------------------------
    // Cas 2 — lectures bornées : le chunk du caller, puis le total du runtime
    // -----------------------------------------------------------------------

    @Test
    fun collectBytesBoundedByChunkAndMaxBytes() = runBlocking {
        // La moitié CHUNK : l'item livre exactement ce que le stream donne, par tranches
        // d'au plus maxChunkBytes — 10 008 octets en 4 096 + 4 096 + 1 816.
        val stream = CloseCountingStream(ByteArray(4096 + 4096 + 1816))
        val source = singleUriSource(FakeOpener(stream))
        val chunks = mutableListOf<ByteArray>()

        assertEquals(
            KadreResult.Success(Unit),
            source.items.single().collectBytes(4096) { chunks += it },
        )
        assertEquals(listOf(4096, 4096, 1816), chunks.map { it.size }, "each chunk respects the caller's chunk bound, the tail is partial")
        assertEquals(1, stream.closeCount, "the natural end of the stream closes it — exactly once")
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("maxChunkBytes")),
            source.items.single().collectBytes(0) { },
            "a non-positive chunk bound is invalid before anything opens",
        )

        // La moitié MAX BYTES : à travers le VRAI RuntimeDropTransfer — taille inconnue
        // (sizeBytes null), le total du caller dépasse → préfixe livré puis Limit(DropItem).
        // Le budget de chunk du runtime est calé à 4 096 comme le cas de chunk, pour que le
        // préfixe livré soit exactement la première tranche.
        val resources = KadrePolicies.Default.resources.copy(maxDropChunkBytes = 4096)
        val budget = RuntimeDropTransferBudget(resources.maxConcurrentDropTransfers)
        val bounded = CloseCountingStream(ByteArray(4096 + 4096 + 1816))
        val boundedSource = singleUriSource(FakeOpener(bounded))
        val transfer = RuntimeDropTransfer(
            source = boundedSource,
            itemSources = boundedSource.items.map { RuntimeDropItemSource(it, it.descriptor, it.readMode) },
            resources = resources,
            reservation = checkNotNull(budget.tryReserve()),
            onClosed = { },
        )
        val delivered = mutableListOf<ByteArray>()

        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.DropItem, 5000L)),
            transfer.items.single().collectBytes(5000L) { delivered += it },
            "the unknown-size read delivers its prefix, then the runtime's total bound fails it with Limit(DropItem) — DESIGN §10.4",
        )
        assertEquals(listOf(4096), delivered.map { it.size }, "only the prefix below the total bound is delivered")
        assertEquals(1, bounded.closeCount, "the failed read did not leak the stream — it closed exactly once")
        transfer.close()
        assertEquals(1, bounded.closeCount, "the transfer's close does not close the already-closed stream again")
    }

    // -----------------------------------------------------------------------
    // Cas 3 — close exactement une fois ; lecture après close owner refusée
    // -----------------------------------------------------------------------

    @Test
    fun closeExactlyOnceAndReadAfterCloseFails() = runBlocking {
        // (a) La fin naturelle ferme le stream UNE fois ; le close du owner ne referme pas ;
        // le double close du source est un no-op.
        val readThrough = CloseCountingStream(ByteArray(10))
        val readThroughSource = singleUriSource(FakeOpener(readThrough))
        assertEquals(
            KadreResult.Success(Unit),
            readThroughSource.items.single().collectBytes(4096) { },
        )
        assertEquals(1, readThrough.closeCount)
        readThroughSource.close()
        readThroughSource.close()
        assertEquals(1, readThrough.closeCount, "neither the owner close nor the double close closes an already-closed stream again")

        // (b) L'ouverture est paresseuse : un item jamais lu ne possède AUCUN stream — rien à
        // fuiter — et la lecture après close owner échoue Closed(DropItem), zéro octet.
        val neverOpened = CloseCountingStream(ByteArray(10))
        val closedSource = singleUriSource(FakeOpener(neverOpened))
        closedSource.close()
        var delivered = false
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem)),
            closedSource.items.single().collectBytes(4096) { delivered = true },
        )
        assertFalse(delivered, "a closed owner never delivers bytes")
        assertEquals(0, neverOpened.closeCount, "a lazy stream never opened is never closed — and never leaked")

        // (c) Un stream gardé ouvert par une lecture suspendue est fermé PAR le close du
        // owner — exactement une fois (la sentinelle « stream conservé après close owner »),
        // et la lecture reprendue échoue honnêtement.
        val parked = CloseCountingStream(ByteArray(8192))
        val parkedSource = singleUriSource(FakeOpener(parked))
        val gate = CompletableDeferred<Unit>()
        var parkedRead: KadreResult<Unit>? = null
        val read = launch(start = CoroutineStart.UNDISPATCHED) {
            parkedRead = parkedSource.items.single().collectBytes(4096) { gate.await() }
        }
        parkedSource.close()
        assertEquals(1, parked.closeCount, "the owner close closed the stream the suspended read kept open")
        gate.complete(Unit)
        read.join()
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem)),
            parkedRead,
            "the read the owner closed fails with the closed item, not a fabricated success",
        )
        assertEquals(1, parked.closeCount, "the failed read does not close the owner-closed stream a second time")
    }

    // -----------------------------------------------------------------------
    // Cas 4 — refus du resolver : une vraie failure, jamais un faux drop lisible
    // -----------------------------------------------------------------------

    @Test
    fun resolverRefusalIsARealFailureNotAFakeRead() = runBlocking {
        val refused = FakeOpener(null as InputStream?)
        val source = singleUriSource(refused)
        val chunks = mutableListOf<ByteArray>()

        assertEquals(
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Android, "drop", "resolver-refused")),
            source.items.single().collectBytes(4096) { chunks += it },
            "the absent permission is the closed platform failure of this seam — the honest analogue of the web's payload-unavailable",
        )
        assertTrue(chunks.isEmpty(), "zero bytes are delivered on a refusal")
        assertEquals(
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Android, "drop", "resolver-refused")),
            source.items.single().collectBytes(4096) { chunks += it },
            "the refusal leaves the item unopened — the opener stays the authority, no fabricated closed state",
        )
        assertTrue(chunks.isEmpty())
        assertEquals(2, refused.opens, "each read consulted the opener once and nothing else")

        // Un opener qui LANCE au lieu de refuser silencieusement (la forme du vrai
        // ContentResolver — SecurityException sur permission absente) : la même failure,
        // jamais une exception à travers la frontière.
        val throwing = AndroidDropStreamOpener { throw FileNotFoundException("no grant") }
        val throwingSource = singleUriSource(throwing)
        assertEquals(
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Android, "drop", "resolver-refused")),
            throwingSource.items.single().collectBytes(4096) { chunks += it },
        )
        assertTrue(chunks.isEmpty(), "a throwing opener delivers zero bytes too")
    }

    // -----------------------------------------------------------------------
    // Cas 5 — lecture concurrente refusée, sans interblocage
    // -----------------------------------------------------------------------

    @Test
    fun concurrentReadsAreRejected() = runBlocking {
        val stream = CloseCountingStream(ByteArray(8192))
        val source = singleUriSource(FakeOpener(stream))
        val item = source.items.single()
        val gate = CompletableDeferred<Unit>()
        var first: KadreResult<Unit>? = null
        val reading = launch(start = CoroutineStart.UNDISPATCHED) {
            first = item.collectBytes(4096) { gate.await() }
        }

        // La première lecture est parquée DANS le collector, stream ouvert : la seconde
        // est refusée à la garde — elle n'attend jamais la première (pas d'interblocage).
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer)),
            item.collectBytes(4096) { },
            "the second concurrent read on the same item is refused with the runtime's own already-in-use contract",
        )
        gate.complete(Unit)
        reading.join()
        assertEquals(KadreResult.Success(Unit), first)

        // Le stream consommé, l'item épuisé refuse sa seconde lecture — le miroir item-level
        // du Closed(DropItem) que le runtime répond à un SingleUse consommé.
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem)),
            item.collectBytes(4096) { },
        )
        assertEquals(1, stream.closeCount, "the completed read closed the stream exactly once")
    }

    // -----------------------------------------------------------------------
    // Cas 6 — l'item Text rejoue sans jamais toucher le resolver
    // -----------------------------------------------------------------------

    @Test
    fun textItemReplaysWithoutResolver() = runBlocking {
        val opener = FakeOpener()
        val snapshot = AndroidClipSnapshot(
            listOf(
                AndroidClipItem(
                    uri = null,
                    text = "héllo",
                    mimeTypes = listOf("text/plain"),
                    displayNameHint = null,
                ),
            ),
        )
        val source = AndroidDropTransferSource(snapshot, opener)
        val item = source.items.single()
        assertEquals(DropItemReadMode.Replayable, item.readMode)

        val expected = "héllo".encodeToByteArray().toList()
        val first = mutableListOf<ByteArray>()
        assertEquals(
            KadreResult.Success(Unit),
            item.collectBytes(2) { first += it },
        )
        assertTrue(first.all { it.size <= 2 }, "the replay chunks respect the caller's chunk bound")
        assertEquals(expected, first.flatMap { it.toList() }, "the replay delivers the snapshotted bytes, UTF-8 intact")
        assertEquals(0, opener.opens, "a text clip is already in memory — the resolver is never consulted")

        val second = mutableListOf<ByteArray>()
        assertEquals(
            KadreResult.Success(Unit),
            item.collectBytes(64) { second += it },
        )
        assertEquals(expected, second.flatMap { it.toList() }, "the Replayable item replays its bytes identically")
        assertEquals(0, opener.opens)

        source.close()
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem)),
            item.collectBytes(64) { },
            "after the owner close, even the in-memory text refuses its reads",
        )
    }

    // -----------------------------------------------------------------------
    // Harnais
    // -----------------------------------------------------------------------

    /** Une source d'un seul item uri-backed — la forme minimale des cas de lecture. */
    private fun singleUriSource(opener: AndroidDropStreamOpener): AndroidDropTransferSource =
        AndroidDropTransferSource(
            AndroidClipSnapshot(
                listOf(
                    AndroidClipItem(
                        uri = "content://kadre.test/item",
                        text = null,
                        mimeTypes = listOf("application/pdf"),
                        displayNameHint = null,
                    ),
                ),
            ),
            opener,
        )

    /**
     * L'opener fake : rend les streams mis en file d'attente, un par ouverture, puis refuse
     * (`null`) — la même forme que le `ContentResolver` de production, sans aucun type SDK.
     */
    private class FakeOpener(private val queued: List<InputStream?>) : AndroidDropStreamOpener {
        constructor(vararg queued: InputStream?) : this(queued.toList())

        var opens: Int = 0
            private set

        override fun open(uri: String): InputStream? {
            opens += 1
            return queued.getOrNull(opens - 1)
        }
    }

    /**
     * Le stream du test : compte ses closes réels et refuse toute lecture après close — la
     * sémantique honnête d'un stream ContentResolver fermé, que le ByteArrayInputStream
     * réel ne porte pas (son close est un no-op tolérant).
     */
    private class CloseCountingStream(data: ByteArray) : ByteArrayInputStream(data) {
        var closeCount: Int = 0
            private set
        private var closed: Boolean = false

        override fun close() {
            if (closed) return
            closed = true
            closeCount += 1
            super.close()
        }

        override fun read(): Int {
            ensureOpen()
            return super.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            ensureOpen()
            return super.read(b, off, len)
        }

        private fun ensureOpen() {
            if (closed) throw IOException("stream closed")
        }
    }
}
