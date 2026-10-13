package org.graphiks.kadre.internal.uikit

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSItemProvider
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.posix.usleep

/**
 * Le kernel de mapping DROP des cibles iOS et tvOS (`UIKitDropMapping`), prouvé PUR : les UTI
 * entrent en `List<String>`/`String`, les valeurs du modèle sortent — aucun type UIKit ici.
 *
 * Les valeurs attendues sont les PINCEaux du plan (task-1-brief Step 1) : `public.utf8-plain-text`
 * → `text/plain`, `public.url` → `text/uri-list`, `public.file-url` → kind File au mime
 * `application/octet-stream` (la table des constantes AppKit, `KffiAppKitDropTransfer.kt:338-341`
 * — le miroir macOS du même fait), une UTI inconnue → `null` (le type est PERDU, jamais fabriqué).
 *
 * La fabrication est testée par le GUARDIEN lui-même : l'init de [DropItemDescriptor] rejette un
 * mime non canonique (`type/subtype` minuscule) — construire un descripteur avec les mimes du
 * snapshot est l'assertion qu'aucun type hors table n'a été transmué en mime.
 *
 * Cibles de mutation : chaque entrée de la table (une UTI changée est tuée par son test), la
 * déduplication (ordre des premiers ports), la nullité hors table (une UTI inconnue devenue
 * `Binary` au lieu d'être perdue produirait un descripteur mensonger), la précédence des kinds
 * (File > Uri > Text — l'ordre AppKit du routage par item), et les tranches de lecture du
 * payload ([UIKitDropPayload.ofBytes] — offset au-delà de la fin, tranche finale courte).
 */
class UIKitDropMappingTest {

    // MARK: - UTI → mime (la table fermée)

    @Test
    fun utf8PlainTextUtiMapsToTextPlain() {
        assertEquals("text/plain", ukitDropMimeForUti("public.utf8-plain-text"))
    }

    @Test
    fun urlUtiMapsToTextUriList() {
        assertEquals("text/uri-list", ukitDropMimeForUti("public.url"))
    }

    @Test
    fun fileUrlUtiMapsToOctetStream() {
        assertEquals("application/octet-stream", ukitDropMimeForUti("public.file-url"))
    }

    @Test
    fun unknownUtiHasNoMime() {
        assertNull(ukitDropMimeForUti("com.example.whatever"))
    }

    @Test
    fun emptyUtiHasNoMime() {
        assertNull(ukitDropMimeForUti(""))
    }

    @Test
    fun nearMissUtiIsNotApproximated() {
        // `public.plain-text` n'est PAS `public.utf8-plain-text` : la table ne préfixe pas,
        // n'approxime pas — l'UTI inconnue est perdue, jamais transmuée.
        assertNull(ukitDropMimeForUti("public.plain-text"))
        assertNull(ukitDropMimeForUti("public.text"))
        assertNull(ukitDropMimeForUti("public.url-name"))
    }

    // MARK: - UTI → kind

    @Test
    fun utf8PlainTextUtiMapsToTextKind() {
        assertEquals(DropItemKind.Text, ukitDropItemKindForUti("public.utf8-plain-text"))
    }

    @Test
    fun urlUtiMapsToUriKind() {
        assertEquals(DropItemKind.Uri, ukitDropItemKindForUti("public.url"))
    }

    @Test
    fun fileUrlUtiMapsToFileKind() {
        assertEquals(DropItemKind.File, ukitDropItemKindForUti("public.file-url"))
    }

    @Test
    fun unknownUtiHasNoKind() {
        assertNull(ukitDropItemKindForUti("com.example.whatever"))
    }

    // MARK: - snapshot : les mimes dédupliqués dans l'ordre enregistré

    @Test
    fun snapshotMapsRegisteredTypesInOrder() {
        val snapshot = ukitDropSnapshot(
            providerRegisteredTypes = listOf(
                "public.utf8-plain-text",
                "public.url",
            ),
            displayName = "note.txt",
            sizeBytes = 42L,
        )
        assertEquals(listOf("text/plain", "text/uri-list"), snapshot?.mimeTypes)
    }

    @Test
    fun snapshotDedupesDuplicateUtiMappingsKeepingFirstOrder() {
        val snapshot = ukitDropSnapshot(
            providerRegisteredTypes = listOf(
                "public.url",
                "public.utf8-plain-text",
                "public.url",
                "public.utf8-plain-text",
            ),
            displayName = null,
            sizeBytes = null,
        )
        assertEquals(listOf("text/uri-list", "text/plain"), snapshot?.mimeTypes)
    }

    @Test
    fun snapshotDropsUnmappableTypes() {
        val snapshot = ukitDropSnapshot(
            providerRegisteredTypes = listOf(
                "com.example.whatever",
                "public.utf8-plain-text",
                "org.chromium.source-url",
            ),
            displayName = null,
            sizeBytes = null,
        )
        assertEquals(listOf("text/plain"), snapshot?.mimeTypes)
    }

    // MARK: - snapshot : aucun type mappable → l'item est PERDU (null), jamais un descripteur vide

    @Test
    fun snapshotIsNullWhenNoTypeMaps() {
        assertNull(
            ukitDropSnapshot(
                providerRegisteredTypes = listOf("com.example.whatever", "public.image"),
                displayName = "mystery.bin",
                sizeBytes = 7L,
            ),
            "a provider with zero mappable types must be SKIPPED, not carried as an empty mime list",
        )
    }

    @Test
    fun snapshotIsNullForEmptyTypeList() {
        assertNull(ukitDropSnapshot(providerRegisteredTypes = emptyList(), displayName = null, sizeBytes = null))
    }

    // MARK: - snapshot : la précédence des kinds (l'ordre AppKit du routage par item)

    @Test
    fun snapshotKindIsFileWhenFileUrlIsRegistered() {
        val snapshot = ukitDropSnapshot(
            providerRegisteredTypes = listOf("public.file-url", "public.url", "public.utf8-plain-text"),
            displayName = "a.pdf",
            sizeBytes = 4096L,
        )
        assertEquals(DropItemKind.File, snapshot?.kind)
    }

    @Test
    fun snapshotKindIsUriWhenOnlyUrlIsRegistered() {
        val snapshot = ukitDropSnapshot(
            providerRegisteredTypes = listOf("public.url", "public.utf8-plain-text"),
            displayName = null,
            sizeBytes = null,
        )
        assertEquals(DropItemKind.Uri, snapshot?.kind)
    }

    @Test
    fun snapshotKindIsTextWhenOnlyTextIsRegistered() {
        val snapshot = ukitDropSnapshot(
            providerRegisteredTypes = listOf("public.utf8-plain-text"),
            displayName = null,
            sizeBytes = null,
        )
        assertEquals(DropItemKind.Text, snapshot?.kind)
    }

    // MARK: - snapshot : les faits portés tels quels

    @Test
    fun snapshotCarriesDisplayNameAndSizeVerbatim() {
        val named = ukitDropSnapshot(listOf("public.file-url"), displayName = "rapport.pdf", sizeBytes = 1024L)
        assertEquals("rapport.pdf", named?.displayName)
        assertEquals(1024L, named?.sizeBytes)

        val anonymous = ukitDropSnapshot(listOf("public.utf8-plain-text"), displayName = null, sizeBytes = null)
        assertNull(anonymous?.displayName)
        assertNull(anonymous?.sizeBytes)
    }

    @Test
    fun snapshotLosesANegativeSizeButKeepsTheItem() {
        // La politique du module (miroir d'uikitPressure) : une primitive hors du domaine du
        // modèle est perdue, l'observation survit — un négatif n'est pas une taille ; le snapshot
        // porte `null`, jamais le fait qui ferait JETER l'init du descripteur.
        val snapshot = ukitDropSnapshot(
            providerRegisteredTypes = listOf("public.utf8-plain-text"),
            displayName = "note.txt",
            sizeBytes = -1L,
        )
        assertEquals("note.txt", snapshot?.displayName)
        assertNull(snapshot?.sizeBytes)
        assertEquals(listOf("text/plain"), snapshot?.mimeTypes)
    }

    // MARK: - le guardien de canonicalité : l'init de DropItemDescriptor accepte les mimes du kernel

    @Test
    fun snapshotMimesAreCanonicalForTheDescriptorInit() {
        val allMappedUtis = listOf("public.utf8-plain-text", "public.url", "public.file-url")
        val snapshot = ukitDropSnapshot(allMappedUtis, displayName = null, sizeBytes = null)
        val mimes = snapshot?.mimeTypes.orEmpty()
        assertEquals(3, mimes.size, "the closed table maps exactly three UTIs")
        mimes.forEach { mime ->
            assertTrue(mime == mime.lowercase(), "mime must be lowercase: $mime")
            assertFalse(mime.isEmpty())
            assertTrue(';' !in mime)
        }
        // Le descripteur du modèle accepte le snapshot TEL QUEL — sinon le port fabriquerait.
        val descriptor = DropItemDescriptor(
            displayName = snapshot?.displayName,
            sizeBytes = snapshot?.sizeBytes,
            mimeTypes = mimes,
            kind = snapshot?.kind ?: DropItemKind.Binary,
        )
        assertEquals(mimes, descriptor.mimeTypes)
    }

    @Test
    fun descriptorInitThrowsOnNonCanonicalMimeTheKernelMustNeverProduce() {
        // Le guardien lui-même : la forme que la fabrication prendrait si le kernel transmuait
        // une UTI hors table — l'init jette, le test l'atteste comme garde de fabrication.
        assertFailsWith<IllegalArgumentException> {
            DropItemDescriptor(
                displayName = null,
                sizeBytes = null,
                mimeTypes = listOf("public.utf8-plain-text"), // une UTI n'est PAS un mime
                kind = DropItemKind.Text,
            )
        }
    }

    // MARK: - UIKitDropPayload.ofBytes (le miroir de WebDropPayload)

    @Test
    fun payloadOfBytesReportsTotalAndDeliversExactChunks() = runBlocking {
        val bytes = ByteArray(10) { it.toByte() }
        val payload = UIKitDropPayload.ofBytes(bytes)
        assertEquals(10L, payload.totalBytes)
        assertEquals(bytes.copyOfRange(0, 4).toList(), payload.readChunk(0, 4)?.toList())
        assertEquals(bytes.copyOfRange(4, 8).toList(), payload.readChunk(4, 4)?.toList())
        assertEquals(bytes.copyOfRange(8, 10).toList(), payload.readChunk(8, 4)?.toList())
    }

    @Test
    fun payloadOfBytesAnswersNullPastTheEnd() = runBlocking {
        val payload = UIKitDropPayload.ofBytes(byteArrayOf(1, 2, 3))
        assertEquals(3L, payload.totalBytes)
        assertNull(payload.readChunk(3, 4), "an offset at the end of the bytes is the end of the stream")
        assertNull(payload.readChunk(100, 4), "an offset past the end is not a read")
    }

    @Test
    fun payloadOfBytesOfEmptyArrayEndsImmediately() = runBlocking {
        val payload = UIKitDropPayload.ofBytes(ByteArray(0))
        assertEquals(0L, payload.totalBytes)
        assertNull(payload.readChunk(0, 8))
    }

    // MARK: - le probe de la décision 3 (spike Task 1, évidence pour le pont de suspension Task 2)

    /**
     * `NSItemProvider(item:typeIdentifier:)` + `loadItemForTypeIdentifier:options:completionHandler:`
     * depuis Kotlin : la completion part-elle, et sur quel fil, quand le run loop principal est
     * pompé ? La preuve dont la Task 2 a besoin pour son pont de suspension (le drop est
     * asynchrone ; le port doit attendre la livraison d'`NSItemProvider`).
     *
     * L'item est réel (`initWithItem:` — déprécié au profit d'`initWithObject:`, dont le klib
     * exige un conformer statique de `NSItemProviderWritingProtocol` qu'`NSURL`/`NSString` ne
     * déclarent pas ; l'init déprécié reste le seul chemin purement Kotlin, suppressions locales
     * comme pour `byValue`) : une `file://` `public.file-url`. Le fait mesure est le FEU de la
     * completion (pompé `limitDateForMode` + `usleep`, couvrant livraison main-queue ET
     * background) ; le fil réel est ENREGISTRÉ (println « KADRE-SPIKE », la convention du driver)
     * sans être figé — la Task 4 le re-mesurera sur les deux familles.
     */
    @Test
    fun loadItemCompletionFiresWhenTheRunLoopIsPumped() {
        @Suppress("DEPRECATION")
        val provider = NSItemProvider(
            item = NSURL.URLWithString("file:///tmp/kadre-drop-spike.txt") as NSURL?,
            typeIdentifier = "public.file-url",
        )
        val registered = provider.registeredTypeIdentifiers.filterIsInstance<String>()
        assertTrue(
            "public.file-url" in registered,
            "the provider must register the item's type; registered=$registered",
        )

        val fired = AtomicInt(0)
        val onMainThread = AtomicInt(0)
        val delivered = AtomicReference<Any?>(null)
        provider.loadItemForTypeIdentifier(
            typeIdentifier = "public.file-url",
            options = null,
            completionHandler = { item, _ ->
                fired.value = 1
                onMainThread.value = if (NSThread.isMainThread()) 1 else 0
                delivered.value = item
            },
        )

        var attempts = 0
        while (fired.value == 0 && attempts < 250) {
            attempts++
            NSRunLoop.mainRunLoop.limitDateForMode(NSDefaultRunLoopMode)
            usleep(20000u)
        }

        println(
            "KADRE-SPIKE loadItem fired=${fired.value == 1} onMainThread=${onMainThread.value == 1} " +
                "deliveredNonNull=${delivered.value != null} pumpedAttempts=$attempts",
        )
        assertEquals(1, fired.value, "the loadItem completion must fire when the run loop is pumped")
    }
}
