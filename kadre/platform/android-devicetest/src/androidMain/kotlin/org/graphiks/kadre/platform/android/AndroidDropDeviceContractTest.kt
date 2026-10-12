package org.graphiks.kadre.platform.android

import android.content.ContentResolver
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.InputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropTransfer
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.internal.runtime.RuntimeDropTransferBudget
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.RuntimeSurfaceInput
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.RawInputDeliveryPolicy
import org.graphiks.kadre.policy.RawInputOverflowAction
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les DEUX scénarios drop ON-DEVICE de l'AND-007 (Task 3, phase 6) — le contenu du gate de
 * sortie roadmap : « Les tests incluent un transfert réel et la sentinelle qui détecte une
 * permission ou un stream conservé après fermeture ». Ils tournent dans l'APK androidTest du
 * CONSUMER (ce module est consommé `androidTestImplementation` — voir le build.gradle.kts),
 * donc sur l'émulateur, avec le `ContentResolver` RÉEL de l'app hôte et la construction
 * runtime-friend du Task 2 (`AndroidDropContractTest`) : `RuntimeSurfaceInput` construit
 * directement, arguments EXACTS d'`installSessionConfiguration`, en ne permutant que
 * `dragAndDropAvailable = true` (la décision normative du hook drag reste garée — la
 * production reste `false`, épinglée par le cas 6 du Task 2).
 *
 * | scenario id (registre)                    | méthode                        | contenu |
 * |-------------------------------------------|--------------------------------|---------|
 * | android-drop-real-transfer-bounded        | androidDropRealTransferBounded | fichier RÉEL → clip uri → source Task 1 + opener RESOLVER de production → presentDrop → acceptDrop → DropPerformed → claim → collectBytes — lecture complète (octets identiques au fichier) puis lecture bornée (préfixe exact, failure `Limit(DropItem)` exacte), chaque chunk ≤ `maxDropChunkBytes` |
 * | android-drop-stream-released-on-close     | androidDropStreamReleasedOnClose | stream OUVERT (première chunk lue, lecture suspendue dans le collector) → close() owner → le stream est fermé EXACTEMENT UNE FOIS (preuve comportementale par l'enveloppeur de l'opener, le compteur host-side du Task 1 n'existe pas ici) ET la relecture répond exactement `Closed(DropItem)` |
 *
 * La sonde `Uri.fromFile` vs `FileProvider` : le fichier vit dans le propre `filesDir` de
 * l'app et le clip porte son uri `file://` — same-app, la lecture `ContentResolver` d'un uri
 * file du propre dir marche sur API 24+ sans grant ni provider (le `FileProvider` du brief
 * n'est nécessaire que cross-app). LE TEST EST LA SONDE : toute la lecture passe par le
 * resolver réel — un `fromFile` inadéquat échouerait le scénario, il passe.
 *
 * L'exécution des scénarios est mono-thread et déterministe (runBlocking + `CompletableDeferred`
 * comme point de parking — aucun Thread.sleep, aucune course) ; les E/S du resolver restent
 * hors de tout verrou côté source (Task 1).
 */
public class AndroidDropDeviceContractTest {

    // -----------------------------------------------------------------------
    // android-drop-real-transfer-bounded — le transfert RÉEL borné
    // -----------------------------------------------------------------------

    /**
     * Le chemin drop COMPLET sur le contenu RÉEL : un fichier écrit dans le propre dir de
     * l'app, snapshot clip uri-backed, la source RÉELLE du Task 1 ouverte par l'opener de
     * PRODUCTION ([androidDropResolverOpener]) sur le `ContentResolver` réel, le réducteur
     * du Task 2 (`dragAndDropAvailable = true`), et la chaîne exacte que la glue future
     * livrera : `presentDrop` → `acceptDrop` → `DropPerformed` → `claimTransfer` →
     * `collectBytes`. La lecture COMPLÈTE rend des octets IDENTIQUES au fichier (chunks de
     * `maxDropChunkBytes` au plus) ; la lecture BORNÉE (`maxBytes` < taille) livre le préfixe
     * EXACT et échoue exactement `ResourceLimitExceeded(DropItem, maxBytes)` — une lecture
     * taille-inconnue peut livrer un préfixe avant d'échouer (DESIGN §10.4), le runtime porte
     * sa failure admise. L'item uri est single-use : la lecture bornée passe par une offre
     * FRAÎCHE sur le même fichier réel.
     */
    @Test
    public fun androidDropRealTransferBounded(): Unit = runBlocking {
        val context = instrumentationContext()
        val file = dropFile(context, REAL_TRANSFER_FILE_NAME, REAL_TRANSFER_FILE_SIZE)
        try {
            val drop = reducer(dropResources())
            val collector = EventCollector(this, drop.input)
            collector.settle()

            // — Offre 1 : lecture COMPLÈTE, octets identiques au fichier. —
            val source = realFileSource(context.contentResolver, file)
            val offer = checkNotNull(drop.input.presentDrop(source, POSITION_ONE, drop.stamps()))
            val entered = assertEvent<InputEvent.DropEntered>(collector)
            assertEquals("the entry publishes the offer it presented", offer.id, entered.offer.id)
            // Le descripteur traduit du clip : kind uri, mimes canonisés, taille TOUJOURS
            // inconnue (un stream ContentResolver ne promet rien avant d'être lu).
            val descriptor = entered.offer.items.single()
            assertEquals("an uri-backed clip item is a Uri offer", DropItemKind.Uri, descriptor.kind)
            assertEquals(listOf(REAL_TRANSFER_MIME), descriptor.mimeTypes)
            assertNull("a content resolver stream promises no size", descriptor.sizeBytes)
            assertEquals(file.name, descriptor.displayName)

            assertUnit(drop.input.acceptDrop(offer.id), "the synchronous accept commits the presented offer")
            assertTrue(
                "the drop completion is reduced through the accepted offer",
                drop.input.accept(
                    SurfaceStimulus.DropPerformed(surfaceId = drop.surfaceId, offerId = offer.id, position = POSITION_ONE),
                ),
            )
            assertEvent<InputEvent.Dropped>(collector)

            val claimed = claimOf(offer.claimTransfer(), "the first claim wins")
            assertEquals(
                "the single-winner claim is the reducer's own rule",
                KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer)),
                offer.claimTransfer(),
            )

            val fullChunks = mutableListOf<List<Byte>>()
            assertEquals(
                "the full bounded read succeeds",
                KadreResult.Success(Unit),
                claimed.items.single().collectBytes(REAL_TRANSFER_FILE_SIZE.toLong()) { fullChunks += it.toList() },
            )
            val fullBytes = fullChunks.flatten().toByteArray()
            assertArrayEquals("the delivered bytes are identical to the real file", file.readBytes(), fullBytes)
            assertTrue(
                "every delivered chunk respects maxDropChunkBytes (${drop.resources.maxDropChunkBytes})",
                fullChunks.all { it.size <= drop.resources.maxDropChunkBytes },
            )

            // Le close du transfert libère SA réservation de budget — le slot unique redevient
            // disponible pour l'offre suivante (le miroir budget du Task 2, cas 3).
            claimed.close()

            // — Offre 2 : lecture BORNÉE — l'item uri est single-use, la seconde lecture passe
            // par une source fraîche sur le MÊME fichier réel. —
            val secondSource = realFileSource(context.contentResolver, file)
            val secondOffer = checkNotNull(drop.input.presentDrop(secondSource, POSITION_TWO, drop.stamps()))
            assertEvent<InputEvent.DropEntered>(collector)
            assertUnit(drop.input.acceptDrop(secondOffer.id), "the released budget slot admits the second accept")
            assertTrue(
                drop.input.accept(
                    SurfaceStimulus.DropPerformed(surfaceId = drop.surfaceId, offerId = secondOffer.id, position = POSITION_TWO),
                ),
            )
            assertEvent<InputEvent.Dropped>(collector)
            val secondClaimed = claimOf(secondOffer.claimTransfer(), "the second transfer claims")

            val boundedChunks = mutableListOf<List<Byte>>()
            val bounded = secondClaimed.items.single().collectBytes(BOUNDED_MAX_BYTES.toLong()) { boundedChunks += it.toList() }
            assertEquals(
                "the bounded read fails with the runtime's exact admitted failure",
                KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.DropItem, BOUNDED_MAX_BYTES.toLong())),
                bounded,
            )
            val boundedBytes = boundedChunks.flatten().toByteArray()
            assertEquals(
                "the bound delivers exactly the prefix it admitted, nothing beyond maxBytes",
                BOUNDED_MAX_BYTES,
                boundedBytes.size,
            )
            assertArrayEquals("the prefix is the exact head of the real file", file.readBytes().copyOf(BOUNDED_MAX_BYTES), boundedBytes)
            assertTrue(
                "the bounded read respects maxDropChunkBytes too",
                boundedChunks.all { it.size <= drop.resources.maxDropChunkBytes },
            )

            secondClaimed.close()
            drop.input.close(failure = null)
            collector.stop()
            assertEquals("nothing else leaks", 0, collector.drainCount())
        } finally {
            file.delete()
        }
    }

    // -----------------------------------------------------------------------
    // android-drop-stream-released-on-close — la sentinelle
    // -----------------------------------------------------------------------

    /**
     * LA SENTINELLE roadmap (« un stream conservé après fermeture » détecté = échec). La
     * lecture est SUSPENDUE dans son collector après la première chunk — le stream opener
     * est ALORS encore ouvert (précondition affirmée par le test) — et le owner ferme la
     * source DANS cet état. Les assertions placées exactement là où la mutation doit mourir :
     *
     * 1. LE POINT DE KILL — immédiatement après `source.close()`, AVANT de relâcher le
     *    collector : le compteur de l'enveloppeur de l'opener vaut déjà 1 (le close du owner
     *    a fermé le stream ouvert, exactement une fois). La preuve est COMPORTEMENTALE (le
     *    compteur host-side du Task 1 n'existe pas sur l'émulateur) : l'enveloppeur délègue
     *    au stream réel du resolver et compte SES propres closes. La mutation « `close()` ne
     *    ferme pas le stream » laisse ce compteur à 0 — CE SEUL scénario échoue ici.
     * 2. La lecture reprise répond exactement `Closed(DropItem)` : le `stream.read` sur le
     *    stream refermé par le owner jette, et la source honnêtifie l'IOException en
     *    `Closed(DropItem)` PENDANT que le owner est fermé (Task 1, `readUri`) — la mutation
     *    (stream jamais fermé par le owner) lirait au contraire jusqu'à l'EOF et répondrait
     *    `Success` : second point de kill, seul ici aussi.
     * 3. La relecture post-close du même item répond exactement `Closed(DropItem)` (garde
     *    `closedByOwner`/single-use) — et le compteur de l'enveloppeur ne bouge plus
     *    (exactement une fois, jamais deux, teardown compris).
     */
    @Test
    public fun androidDropStreamReleasedOnClose(): Unit = runBlocking {
        val context = instrumentationContext()
        val file = dropFile(context, SENTINEL_FILE_NAME, SENTINEL_FILE_SIZE)
        try {
            val drop = reducer(dropResources())
            val collector = EventCollector(this, drop.input)
            collector.settle()

            val tracker = StreamCloseTracker(context.contentResolver)
            val source = AndroidDropTransferSource(
                AndroidClipSnapshot(
                    listOf(
                        AndroidClipItem(
                            uri = androidUriFor(file),
                            text = null,
                            mimeTypes = listOf(SENTINEL_MIME),
                            displayNameHint = file.name,
                        ),
                    ),
                ),
                tracker.opener,
            )
            val offer = checkNotNull(drop.input.presentDrop(source, POSITION_ONE, drop.stamps()))
            assertEvent<InputEvent.DropEntered>(collector)
            assertUnit(drop.input.acceptDrop(offer.id), "the synchronous accept commits the presented offer")
            assertTrue(
                drop.input.accept(
                    SurfaceStimulus.DropPerformed(surfaceId = drop.surfaceId, offerId = offer.id, position = POSITION_ONE),
                ),
            )
            assertEvent<InputEvent.Dropped>(collector)
            val claimed = claimOf(offer.claimTransfer(), "the claim wins")

            val firstChunk = CompletableDeferred<ByteArray>()
            val releaseCollector = CompletableDeferred<Unit>()
            val readResult = CompletableDeferred<KadreResult<Unit>>()
            val read = launch {
                val result = claimed.items.single().collectBytes(SENTINEL_FILE_SIZE.toLong()) { chunk ->
                    firstChunk.complete(chunk)
                    releaseCollector.await()
                }
                readResult.complete(result)
            }

            // La première chunk du fichier réel passe par le resolver — le stream est ouvert.
            val head = withTimeout(10_000) { firstChunk.await() }
            assertArrayEquals("the first chunk is the exact head of the real file", file.readBytes().copyOf(head.size), head)
            assertFalse("the precondition: the opener's stream is still open mid-read", tracker.isClosed)

            // LE CLOSE DU OWNER, pendant la lecture suspendue.
            source.close()
            assertEquals(
                "SENTINEL: the owner close released the still-open stream exactly once — a close that " +
                    "kept the stream would fail here, alone",
                1,
                tracker.closeCount,
            )

            // La lecture reprend : le stream refermé jette, la source répond le close owner.
            releaseCollector.complete(Unit)
            assertEquals(
                "the interrupted read answers the owner's exact Closed(DropItem)",
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem)),
                withTimeout(10_000) { readResult.await() },
            )
            read.join()

            // La relecture post-close du même item : refus exact, et toujours UN SEUL close.
            assertEquals(
                "a post-close read of the same item is refused exactly",
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem)),
                claimed.items.single().collectBytes(SENTINEL_FILE_SIZE.toLong()) { },
            )
            assertEquals("the stream was closed exactly once, never twice", 1, tracker.closeCount)

            drop.input.close(failure = null)
            collector.stop()
            assertEquals("the teardown re-closes nothing — the release is final", 1, tracker.closeCount)
        } finally {
            file.delete()
        }
    }

    // -----------------------------------------------------------------------
    // Harnais — la construction runtime-friend du Task 2, sur le device.
    // -----------------------------------------------------------------------

    /**
     * Le réducteur de test : `RuntimeSurfaceInput` construit DIRECTEMENT avec les arguments
     * EXACTS d'[AndroidHostSurface.installSessionConfiguration] (:642-657) — le miroir
     * arg-pour-arg du harnais `AndroidDropContractTest` (Task 2) — en ne permutant que
     * `dragAndDropAvailable`.
     */
    private fun reducer(resources: ResourceBudgetPolicy): TestReducer {
        val stamps = stampSource()
        val allocator = RuntimeEventCollectorAllocator(TEST_COLLECTORS_PER_SESSION)
        return TestReducer(
            surfaceId = RuntimeProcessIds.nextSurfaceId(),
            stamps = stamps,
            resources = resources,
            input = RuntimeSurfaceInput(
                surfaceId = RuntimeProcessIds.nextSurfaceId(),
                deliveryPolicy = testInputPolicy(),
                eventStampSource = stamps,
                eventCollectorGate = allocator.newGate(TEST_COLLECTORS_PER_FLOW),
                textInputPort = AndroidTextInputPort(stamps),
                rawInputCoordinator = null,
                rawInputCapability = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
                dragAndDropAvailable = true,
                resources = resources,
                dropTransferBudget = RuntimeDropTransferBudget(resources.maxConcurrentDropTransfers),
                dropTransferScope = null,
                textInputEventCollectorGate = allocator.newGate(TEST_COLLECTORS_PER_FLOW),
                failureReporter = { },
                sessionFailureHandler = { },
            ),
        )
    }

    private class TestReducer(
        val surfaceId: SurfaceId,
        val stamps: () -> EventStamp,
        val resources: ResourceBudgetPolicy,
        val input: RuntimeSurfaceInput,
    )

    /** La policy de ressources du test : budget drop de 1, chunks de 256, timeout court. */
    private fun dropResources(): ResourceBudgetPolicy = KadrePolicies.Default.resources.copy(
        maxConcurrentDropTransfers = 1,
        maxDropChunkBytes = TEST_CHUNK_BYTES,
        dropTransferClaimTimeout = 250.milliseconds,
    )

    /** La policy d'entrée du test — la même forme que les tests du Task 2. */
    private fun testInputPolicy(): InputDeliveryPolicy {
        val discrete = EventDeliveryPolicy(
            ingressCapacity = 64,
            collectorCapacity = 16,
            ingressOverflow = IngressOverflowAction.CloseSource,
            collectorOverflow = CollectorOverflowAction.CancelSlowCollector,
        )
        return InputDeliveryPolicy(
            discreteEvents = discrete,
            pointerMotion = ContinuousDelivery.Coalesced,
            touchMotion = ContinuousDelivery.Coalesced,
            scroll = ContinuousDelivery.Coalesced,
            gestureChanges = ContinuousDelivery.Coalesced,
            gamepadChanges = ContinuousDelivery.Latest,
            rawInput = RawInputDeliveryPolicy(16, RawInputOverflowAction.DropOldestAndReport),
        )
    }

    /** La source de stamps de session — séquentielle, la même instance pour toute la surface. */
    private fun stampSource(): () -> EventStamp {
        var sequence = 0L
        return {
            sequence += 1
            EventStamp(SessionSequence(sequence), SessionInstant((sequence - 1).nanoseconds), null)
        }
    }

    /** La source RÉELLE du Task 1 sur le fichier réel, ouverte par l'OPENER DE PRODUCTION. */
    private fun realFileSource(resolver: ContentResolver, file: File): AndroidDropTransferSource =
        AndroidDropTransferSource(
            AndroidClipSnapshot(
                listOf(
                    AndroidClipItem(
                        uri = androidUriFor(file),
                        text = null,
                        mimeTypes = listOf(REAL_TRANSFER_MIME),
                        displayNameHint = file.name,
                    ),
                ),
            ),
            androidDropResolverOpener(resolver),
        )

    // -----------------------------------------------------------------------
    // Les faits device — le fichier réel, l'uri réelle, l'enveloppeur de la sentinelle.
    // -----------------------------------------------------------------------

    private fun instrumentationContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** L'uri `file://` du propre dir — la sonde du brief (same-app, pas de grant requis). */
    private fun androidUriFor(file: File): String = android.net.Uri.fromFile(file).toString()

    /** Le fichier réel du scénario, écrit dans le propre `filesDir` de l'app hôte. */
    private fun dropFile(context: Context, name: String, size: Int): File {
        val file = File(context.filesDir, name)
        file.writeBytes(ByteArray(size) { index -> ((index * 31 + 7) % 251).toByte() })
        return file
    }

    /**
     * L'enveloppeur comportemental de la sentinelle : elle délègue TOUT au stream réel du
     * resolver de production et compte SES propres closes — la preuve on-device que le
     * compteur host-side du Task 1 remplace (l'assertion porte sur un close RÉELLEMENT
     * arrivé au stream du resolver, pas sur un compteur de test).
     */
    private class StreamCloseTracker(resolver: ContentResolver) {
        val opener: AndroidDropStreamOpener = AndroidDropStreamOpener { uri ->
            val production = checkNotNull(androidDropResolverOpener(resolver).open(uri)) {
                "the production resolver refused the test's own file uri: $uri"
            }
            TrackingStream(production)
        }

        var closeCount: Int = 0
            private set

        val isClosed: Boolean get() = closeCount > 0

        private inner class TrackingStream(private val delegate: InputStream) : InputStream() {
            override fun read(): Int = delegate.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate.read(buffer, offset, length)

            override fun close() {
                closeCount += 1
                delegate.close()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Les observateurs — la forme du Task 2 (canal + job, settle/stop).
    // -----------------------------------------------------------------------

    private suspend inline fun <reified T : InputEvent> assertEvent(collector: EventCollector): T {
        val event = withTimeout(10_000) { collector.receive() }
        if (event !is T) throw AssertionError("expected ${T::class.simpleName}, got $event")
        return event
    }

    private fun assertUnit(result: KadreResult<Unit>, message: String) {
        assertTrue("$message — got $result", result is KadreResult.Success)
    }

    @Suppress("UNCHECKED_CAST")
    private fun claimOf(result: KadreResult<DropTransfer>, message: String): DropTransfer {
        assertTrue("$message — got $result", result is KadreResult.Success)
        return (result as KadreResult.Success<DropTransfer>).value
    }

    /**
     * La collecte des événements du réducteur — le canal ET le job qui le nourrit (la forme
     * du Task 2) : le job DOIT être annulé à la fin du cas ([stop]).
     */
    private class EventCollector(
        scope: CoroutineScope,
        input: SurfaceInput,
    ) {
        private val channel = Channel<InputEvent>(Channel.UNLIMITED)

        private val job = scope.launch {
            input.events.collect { channel.send(it) }
        }

        suspend fun settle() {
            repeat(8) { yield() }
        }

        fun stop() {
            job.cancel()
        }

        suspend fun receive(): InputEvent = channel.receive()

        suspend fun drainCount(): Int {
            var count = 0
            while (true) {
                channel.tryReceive().getOrNull() ?: break
                count += 1
                assertFalse("unexpected event", true)
            }
            return count
        }
    }

    private companion object {
        const val REAL_TRANSFER_FILE_NAME = "kadre-drop-real-transfer.bin"
        const val REAL_TRANSFER_FILE_SIZE = 1000
        const val REAL_TRANSFER_MIME = "application/octet-stream"
        const val BOUNDED_MAX_BYTES = 512
        const val SENTINEL_FILE_NAME = "kadre-drop-sentinel.bin"
        const val SENTINEL_FILE_SIZE = 4096
        const val SENTINEL_MIME = "application/octet-stream"
        const val TEST_CHUNK_BYTES = 256
        const val TEST_COLLECTORS_PER_SESSION = 64
        const val TEST_COLLECTORS_PER_FLOW = 4
        val POSITION_ONE = LogicalPoint(10.0, 12.0)
        val POSITION_TWO = LogicalPoint(22.0, 14.0)
    }
}
