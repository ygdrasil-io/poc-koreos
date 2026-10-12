package org.graphiks.kadre.platform.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.input.DropOfferState
import org.graphiks.kadre.input.DropOfferTerminationReason
import org.graphiks.kadre.input.DropTransfer
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
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
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le contrat drop Android (Task 2, phase 6) : la présentation, l'acceptation synchrone, le
 * claim à gagnant unique, le budget, le timeout de claim et le teardown — les six cas miroirs
 * du précédent web BCK-006 (`WebDropSurfaceTest`), joués à travers le VRAI réducteur
 * (`RuntimeSurfaceInput`, la machinerie fusionnée que la production construit déjà).
 *
 * CONSTRUCTION DE TEST (la sonde du brief) : le seam `AndroidHostSurface.
 * installSessionConfiguration` ne révèle pas `dragAndDropAvailable` (la production passe
 * `false` — AndroidHostSurface.kt:650, la décision normative garée du hook drag). Les cas 1-5
 * construisent donc `RuntimeSurfaceInput` DIRECTEMENT, argument pour argument le miroir exact
 * de `installSessionConfiguration` (:642-657 — même `AndroidTextInputPort`, mêmes gates du
 * même allocateur, même coordinateur raw input absent, même budget construit de la policy),
 * en ne permutant que le drapeau : `dragAndDropAvailable = true`. Le réducteur est drivé
 * standalone, sans la surface — la forme établie des tests de livraison d'entrée (phase 3,
 * `AndroidInputDeliveryTest`), friend du runtime. La production, elle, reste à `false` : le
 * cas 6 l'épingle.
 *
 * 1. `presentedSingleActiveAndReplacedWithLeftSurface` — deux `presentDrop` → la première
 *    offre `LeftSurface` (sa source fermée avec elle), la seconde Presented ; un SEUL offer
 *    actif : un `DropMoved` de l'offre morte n'est rien à réduire (web :
 *    `aSecondDragEnterTerminatesThePreviousOfferAsLeftSurface` +
 *    `aDragOverDeliversDropMovedOnlyWhileTheOfferIsActive`).
 * 2. `acceptSynchronousThroughTheInteractionShape` — `presentDrop` + `acceptDrop(offerId)`
 *    → Accepted : la forme synchrone que la glue armera en `Now(acceptDrop(offerId))` (le
 *    contrat invokeNative P4, miroir `WebHostSession.kt:1232`) ; l'entrée n'est PAS
 *    réinjectée en entrée ordinaire (`acceptInteraction(DropEntered) = false`) ; une offre
 *    déjà décidée ou une offre périmée est refusée EXACTEMENT `Closed(DropTransfer)` — le
 *    refus d'état du réducteur, l'analogue Android du `InvalidRequest("offerId")` web.
 * 3. `claimSingleWinnerAndBudget` — deux `claimTransfer` → un Success, un
 *    `AlreadyInUse(DropTransfer)` ; budget `maxConcurrentDropTransfers = 1` (policy de
 *    test) : un transfert vivant tient sa réservation, le second accept est refusé
 *    `ResourceLimitExceeded(DropTransfer, 1)` jusqu'à ce que le close la libère (web :
 *    `aHandlerThatAcceptsYieldsAnAcceptedOfferWhoseDropIsClaimableByASingleWinner`).
 * 4. `performedClaimTimedOut` — `DropPerformed` sans claim jamais pris →
 *    `Terminated(ClaimTimedOut)` après le `dropTransferClaimTimeout` court de la policy de
 *    test, la source fermée AVEC le transfert, le budget libéré (DESIGN §10.4).
 * 5. `teardownClosesOwnerClosed` — offre vivante + transfert ouvert (réclamé) puis
 *    `close(failure = null)` → l'offre vivante `OwnerClosed`, le transfert conservé fermé
 *    (lecture après → `Closed(DropTransfer)`, source derrière atteinte), les flux terminés
 *    (web : `aSessionCloseTerminatesTheOfferItStillHoldsAsOwnerClosed` +
 *    `aSessionCloseClosesTheTransfersItKeptAndTheSourcesBehindThem`).
 * 6. `productionStaysUnsupported` — le pin anti-régression : la construction de PRODUCTION
 *    (`installSessionConfiguration` réelle, `observationInstalled` comme un attach câblé)
 *    publie `dragAndDrop = Unsupported` dans l'état d'entrée — et la garde du réducteur
 *    construit avec l'argument de production (`dragAndDropAvailable = false`) refuse
 *    `presentDrop` : `null`, la source fermée exactement une fois, AUCUN `DropEntered` (la
 *    garde RuntimeSurfaceInput.kt:238 ; l'énoncé complet est dans le rapport : il n'existe
 *    AUCUNE entrée drop publique sur `AndroidHostSurface` — `presentDrop` n'y est jamais
 *    appelé).
 *
 * Aucun type SDK n'est instancié : les sources sont la vraie [AndroidDropTransferSource] de
 * Task 1 (cas 3, item texte — le resolver n'y est jamais consulté) et des fakes enregistreurs
 * (le miroir du `RecordedDropTransferSource` web). Les lectures bornées/single-use restent
 * l'affaire de Task 1 (`AndroidDropTransferSourceTest`) et du réducteur.
 */
class AndroidDropContractTest {

    // -----------------------------------------------------------------------
    // Cas 1 — présentation : une seule offre active, le remplacement LeftSurface
    // -----------------------------------------------------------------------

    @Test
    fun presentedSingleActiveAndReplacedWithLeftSurface() = runBlocking {
        val drop = reducer(dragAndDropAvailable = true)
        val collector = EventCollector(this, drop.input)
        collector.settle()

        val first = RecordedDropSource()
        val firstOffer = checkNotNull(drop.input.presentDrop(first.source, POSITION_ONE, drop.stamps()))
        val enteredFirst = assertIs<InputEvent.DropEntered>(collector.next())
        assertEquals(firstOffer.id, enteredFirst.offer.id, "the entry publishes the offer it presented")
        assertEquals(POSITION_ONE, enteredFirst.position)
        assertEquals(DropOfferState.Presented, firstOffer.state.value)

        // La seconde entrée REMPLACE la première : LeftSurface, source fermée avec elle.
        val second = RecordedDropSource()
        val secondOffer = checkNotNull(drop.input.presentDrop(second.source, POSITION_TWO, drop.stamps()))
        val enteredSecond = assertIs<InputEvent.DropEntered>(collector.next())
        assertEquals(secondOffer.id, enteredSecond.offer.id)
        assertEquals(
            DropOfferTerminationReason.LeftSurface,
            (firstOffer.state.value as? DropOfferState.Terminated)?.reason,
            "the one active offer of the reducer ends the offer it replaces",
        )
        assertEquals(1, first.closeCount, "the replaced source is closed with its offer — exactly once")
        assertEquals(DropOfferState.Presented, secondOffer.state.value, "the replacement is the one presented offer")

        // Un seul DropEntered actif : un DropMoved de l'offre morte n'est RIEN à réduire.
        val deadMoved = drop.input.accept(
            SurfaceStimulus.DropMoved(surfaceId = drop.surfaceId, offerId = firstOffer.id, position = POSITION_ONE),
        )
        assertFalse(deadMoved, "a drag over the replaced offer is nothing the reducer could reduce")
        val liveMoved = drop.input.accept(
            SurfaceStimulus.DropMoved(surfaceId = drop.surfaceId, offerId = secondOffer.id, position = POSITION_TWO),
        )
        assertTrue(liveMoved, "the drag over rides the one offer the entry presented")
        val moved = assertIs<InputEvent.DropMoved>(collector.next())
        assertEquals(secondOffer.id, moved.offerId)
        assertEquals(POSITION_TWO, moved.position)
        assertEquals(0, collector.drainCount(), "exactly one active offer, nothing else leaks")

        assertEquals(0, second.closeCount, "the living offer keeps its source")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 2 — acceptation : la forme synchrone acceptDrop(offerId), refus exact ailleurs
    // -----------------------------------------------------------------------

    @Test
    fun acceptSynchronousThroughTheInteractionShape() = runBlocking {
        val drop = reducer(dragAndDropAvailable = true)
        val collector = EventCollector(this, drop.input)
        collector.settle()

        val source = RecordedDropSource()
        val offer = checkNotNull(drop.input.presentDrop(source.source, POSITION_ONE, drop.stamps()))
        val entered = assertIs<InputEvent.DropEntered>(collector.next())
        assertEquals(DropOfferState.Presented, entered.offer.state.value, "the offer is presented while the host decides")

        // L'entrée de drop n'est PAS réinjectée en entrée ordinaire : la publication de
        // l'offre A DÉJÀ porté le DropEntered — le callback d'interaction ne le redelivre pas.
        val reinjected = drop.input.acceptInteraction(
            InteractionEvent.DropEntered(offer = offer, position = POSITION_ONE, stamp = drop.stamps()),
        )
        assertFalse(reinjected, "a drop entry is never republished as ordinary input")
        assertEquals(0, collector.drainCount(), "the interaction callback publishes nothing of its own")

        // LA forme synchrone — le contrat que la glue armera en Now(acceptDrop(offerId)):
        assertIs<KadreResult.Success<Unit>>(
            drop.input.acceptDrop(offer.id),
            "the host's synchronous accept commits the presented offer",
        )
        assertEquals(DropOfferState.Accepted, offer.state.value)

        // Une offre DÉJÀ décidée est refusée exactement : le jeton est à usage unique.
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer)),
            drop.input.acceptDrop(offer.id),
            "an offer the host already decided refuses a second accept with the reducer's exact state refusal",
        )

        // Seule l'offre de l'entrée COURANTE est acceptable : l'identité périmée est refusée
        // exactement — l'analogue Android du invokeNative web qui n'accepte que AcceptDrop de
        // CETTE offre (InvalidRequest sinon) ; le réducteur, lui, répond Closed(DropTransfer).
        val replaced = RecordedDropSource()
        val nextOffer = checkNotNull(drop.input.presentDrop(replaced.source, POSITION_TWO, drop.stamps()))
        assertIs<InputEvent.DropEntered>(collector.next())
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer)),
            drop.input.acceptDrop(offer.id),
            "a stale offer identity is refused exactly — only the current entry's offer is acceptable",
        )
        assertIs<KadreResult.Success<Unit>>(drop.input.acceptDrop(nextOffer.id))
        assertEquals(DropOfferState.Accepted, nextOffer.state.value)
        assertEquals(0, collector.drainCount(), "accepts publish nothing — the offer state carries the decision")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 3 — claim gagnant unique + budget maxConcurrent = 1 (policy de test)
    // -----------------------------------------------------------------------

    @Test
    fun claimSingleWinnerAndBudget() = runBlocking {
        val drop = reducer(dragAndDropAvailable = true, resources = dropTestResources())
        val collector = EventCollector(this, drop.input)
        collector.settle()

        // La source RÉELLE de Task 1 (item texte — le resolver n'y est jamais consulté) :
        // la forme exacte que la glue passera à presentDrop.
        val source = realTextSource()
        val offer = checkNotNull(drop.input.presentDrop(source, POSITION_ONE, drop.stamps()))
        assertIs<InputEvent.DropEntered>(collector.next())
        assertIs<KadreResult.Success<Unit>>(drop.input.acceptDrop(offer.id))

        val performed = drop.input.accept(
            SurfaceStimulus.DropPerformed(surfaceId = drop.surfaceId, offerId = offer.id, position = POSITION_ONE),
        )
        assertTrue(performed, "the drop completion is reduced through the accepted offer")
        val dropped = assertIs<InputEvent.Dropped>(collector.next())
        assertEquals(offer.id, dropped.offer.id, "the drop the consumer observes is the offer that was accepted")

        // Deux claims : un SEUL gagnant — le second AlreadyInUse(DropTransfer), la règle du
        // réducteur elle-même, inchangée par la couture Android.
        val claimed = offer.claimTransfer()
        val secondClaim = offer.claimTransfer()
        assertIs<KadreResult.Success<DropTransfer>>(claimed, "the first claim wins and takes the transfer")
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer)),
            secondClaim,
            "the single-winner claim is the reducer's own rule",
        )
        assertEquals(DropOfferState.Claimed, offer.state.value)

        // Les octets passent par la vraie source de Task 1 à travers le VRAI transfert —
        // l'assemblage bout-en-bout que la glue livrera.
        val chunks = mutableListOf<ByteArray>()
        assertEquals(KadreResult.Success(Unit), claimed.value.items.single().collectBytes(64L) { chunks += it })
        assertEquals(listOf(REAL_TEXT.encodeToByteArray().toList()), chunks.map { it.toList() })

        // Le budget de test (maxConcurrentDropTransfers = 1) : la réservation du transfert
        // vivant refuse le second accept — ResourceLimitExceeded(DropTransfer, 1).
        val next = RecordedDropSource()
        val nextOffer = checkNotNull(drop.input.presentDrop(next.source, POSITION_TWO, drop.stamps()))
        assertIs<InputEvent.DropEntered>(collector.next())
        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.DropTransfer, 1L)),
            drop.input.acceptDrop(nextOffer.id),
            "the live transfer holds the one budget slot — a second accept is refused with the exact limit",
        )
        assertEquals(DropOfferState.Presented, nextOffer.state.value, "a refused accept leaves the offer presented")

        // Le close du transfert libère SA réservation : le second accept passe alors.
        claimed.value.close()
        assertIs<KadreResult.Success<Unit>>(
            drop.input.acceptDrop(nextOffer.id),
            "the transfer's close released the budget slot it held",
        )
        assertEquals(0, collector.drainCount(), "nothing else leaks")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 4 — DropPerformed sans claim : ClaimTimedOut, source fermée, budget libéré
    // -----------------------------------------------------------------------

    @Test
    fun performedClaimTimedOut() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val drop = reducer(
            dragAndDropAvailable = true,
            resources = dropTestResources(),
            dropTransferScope = scope,
        )
        val collector = EventCollector(this, drop.input)
        collector.settle()
        try {
            val source = RecordedDropSource()
            val offer = checkNotNull(drop.input.presentDrop(source.source, POSITION_ONE, drop.stamps()))
            assertIs<InputEvent.DropEntered>(collector.next())
            assertIs<KadreResult.Success<Unit>>(drop.input.acceptDrop(offer.id))
            assertTrue(
                drop.input.accept(
                    SurfaceStimulus.DropPerformed(surfaceId = drop.surfaceId, offerId = offer.id, position = POSITION_ONE),
                ),
            )
            assertIs<InputEvent.Dropped>(collector.next())

            // Le claim n'arrive JAMAIS : le timeout court de la policy de test terminalise
            // l'offre — la machinerie lancée par perform() dans le scope du drop.
            withTimeout(5_000) { offer.state.first { it is DropOfferState.Terminated } }
            assertEquals(
                DropOfferTerminationReason.ClaimTimedOut,
                (offer.state.value as? DropOfferState.Terminated)?.reason,
                "an unclaimed transfer times out with the reducer's own claim timeout",
            )
            assertEquals(1, source.closeCount, "the timed-out transfer closed the source behind it — exactly once")
            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer)),
                offer.claimTransfer(),
                "an offer whose claim timed out claims nothing",
            )

            // Le budget est LIBÉRÉ par le timeout : un nouvel accept réserve le slot.
            val next = RecordedDropSource()
            val nextOffer = checkNotNull(drop.input.presentDrop(next.source, POSITION_TWO, drop.stamps()))
            assertIs<InputEvent.DropEntered>(collector.next())
            assertIs<KadreResult.Success<Unit>>(
                drop.input.acceptDrop(nextOffer.id),
                "the claim timeout released the budget slot it held",
            )
            assertEquals(0, collector.drainCount(), "nothing else leaks")
        } finally {
            collector.stop()
            scope.cancel()
        }
    }

    // -----------------------------------------------------------------------
    // Cas 5 — teardown : OwnerClosed + transferts conservés fermés, flux terminés
    // -----------------------------------------------------------------------

    @Test
    fun teardownClosesOwnerClosed() = runBlocking {
        val drop = reducer(dragAndDropAvailable = true)
        val collector = EventCollector(this, drop.input)
        collector.settle()

        // Une offre acceptée, performée, réclamée : le transfert vit dans la main du test.
        val first = RecordedDropSource()
        val firstOffer = checkNotNull(drop.input.presentDrop(first.source, POSITION_ONE, drop.stamps()))
        assertIs<InputEvent.DropEntered>(collector.next())
        assertIs<KadreResult.Success<Unit>>(drop.input.acceptDrop(firstOffer.id))
        assertTrue(
            drop.input.accept(
                SurfaceStimulus.DropPerformed(surfaceId = drop.surfaceId, offerId = firstOffer.id, position = POSITION_ONE),
            ),
        )
        assertIs<InputEvent.Dropped>(collector.next())
        val claimed = assertIs<KadreResult.Success<DropTransfer>>(firstOffer.claimTransfer()).value

        // L'offre vivante SUIVANTE — le owner ferme pendant qu'elle est présentée.
        val second = RecordedDropSource()
        val secondOffer = checkNotNull(drop.input.presentDrop(second.source, POSITION_TWO, drop.stamps()))
        assertIs<InputEvent.DropEntered>(collector.next())

        drop.input.close(failure = null)

        assertEquals(
            DropOfferTerminationReason.OwnerClosed,
            (secondOffer.state.value as? DropOfferState.Terminated)?.reason,
            "the owner that closes the surface terminates the offer it still holds",
        )
        assertEquals(1, second.closeCount, "the owner-closed offer's source is closed with it — exactly once")
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer)),
            secondOffer.claimTransfer(),
            "an offer the owner closed claims nothing",
        )

        // Le transfert conservé (réclamé avant le close) est fermé PAR le teardown : la
        // lecture après → Closed(DropTransfer), et la source derrière lui est atteinte.
        val chunks = mutableListOf<ByteArray>()
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropTransfer)),
            claimed.items.single().collectBytes(64L) { chunks += it },
            "a transfer the owner closed reads nothing",
        )
        assertTrue(chunks.isEmpty(), "the closed transfer delivers zero bytes")
        assertEquals(1, first.closeCount, "the session close reaches the source behind the claimed transfer")

        // Le flux d'entrée est terminé : un collecteur tardif reçoit un flux déjà clos.
        collector.stop()
        var lateEvents = 0
        drop.input.events.collect { lateEvents += 1 }
        assertEquals(0, lateEvents, "a closed surface's input flow terminates without further events")
        assertEquals(0, collector.drainCount(), "the teardown publishes nothing of its own")
    }

    // -----------------------------------------------------------------------
    // Cas 6 — le pin anti-régression : la production reste Unsupported, la garde refuse
    // -----------------------------------------------------------------------

    @Test
    fun productionStaysUnsupported() = runBlocking {
        // La construction de PRODUCTION, telle qu'un attach câblé la fait réellement tourner
        // (le miroir exact des tests de livraison d'entrée) : le stub pré-install, puis
        // l'install de la configuration de session, puis l'installation de l'observation.
        val reported = mutableListOf<Throwable>()
        val surface = AndroidHostSurface(
            id = RuntimeProcessIds.nextSurfaceId(),
            initialState = testSurfaceState(),
            frameClock = { DropContractInertFrameClock },
            failureReporter = { reported += it },
        )
        assertEquals(
            FeatureAvailability.Unsupported,
            surface.input.state.value.capabilities.dragAndDrop,
            "the pre-install input stub already refuses drag and drop",
        )
        installProductionConfiguration(surface)
        surface.observationInstalled(inputWired = true)

        // THE PIN — l'assertion exacte de Task 2 : la production publie le refus structurel.
        // Si quiconque retourne le drapeau d'AndroidHostSurface.kt:650 sans la décision
        // normative (le hook drag coopératif du host), CE test échoue.
        assertEquals(
            FeatureAvailability.Unsupported,
            surface.input.state.value.capabilities.dragAndDrop,
            "the production session configuration keeps dragAndDropUnsupported — there is no drag listener to promise",
        )

        // La garde du réducteur avec l'argument de PRODUCTION (false, l'argument exact de
        // installSessionConfiguration :650) : presentDrop refuse, ferme la source exactement
        // une fois, ne publie JAMAIS de DropEntered. Il n'existe aucune entrée drop publique
        // sur la surface — ce probe est la preuve mécanique du refus que la garde porte.
        val production = reducer(dragAndDropAvailable = false)
        val collector = EventCollector(this, production.input)
        collector.settle()
        val source = RecordedDropSource()
        assertNull(
            production.input.presentDrop(source.source, POSITION_ONE, production.stamps()),
            "the production construction never presents a drop offer — the capability guard refuses",
        )
        assertEquals(1, source.closeCount, "the refused source is closed exactly once — nothing leaks")
        assertEquals(
            0,
            collector.drainCount(),
            "no drop offer can ever appear through the production construction",
        )
        assertTrue(reported.isEmpty(), "the refusal is a structural no, not a failure report")
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Harnais — la construction de test (le miroir arg-pour-arg d'installSessionConfiguration)
    // -----------------------------------------------------------------------

    /**
     * Le réducteur de test : `RuntimeSurfaceInput` construit DIRECTEMENT avec les arguments
     * EXACTS d'[AndroidHostSurface.installSessionConfiguration] (:642-657) — le port texte
     * Android sur la même source de stamps, AUCUN coordinateur raw input (capability
     * Unsupported), le budget construit de la policy comme la production — en ne permutant
     * que `dragAndDropAvailable`. C'est LA sonde du brief : le seam de la surface n'expose
     * pas le drapeau, la construction directe le permet ; le réducteur est drivé standalone
     * sans la surface (la forme établie de la phase 3).
     */
    private fun reducer(
        dragAndDropAvailable: Boolean,
        resources: ResourceBudgetPolicy = KadrePolicies.Default.resources,
        dropTransferScope: CoroutineScope? = null,
    ): TestReducer {
        val stamps = stampSource()
        val allocator = RuntimeEventCollectorAllocator(TEST_COLLECTORS_PER_SESSION)
        return TestReducer(
            surfaceId = RuntimeProcessIds.nextSurfaceId(),
            stamps = stamps,
            input = RuntimeSurfaceInput(
                surfaceId = RuntimeProcessIds.nextSurfaceId(),
                deliveryPolicy = testInputPolicy(),
                eventStampSource = stamps,
                eventCollectorGate = allocator.newGate(TEST_COLLECTORS_PER_FLOW),
                textInputPort = AndroidTextInputPort(stamps),
                rawInputCoordinator = null,
                rawInputCapability = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
                dragAndDropAvailable = dragAndDropAvailable,
                resources = resources,
                dropTransferBudget = RuntimeDropTransferBudget(resources.maxConcurrentDropTransfers),
                dropTransferScope = dropTransferScope,
                textInputEventCollectorGate = allocator.newGate(TEST_COLLECTORS_PER_FLOW),
                failureReporter = { },
                sessionFailureHandler = { },
            ),
        )
    }

    private class TestReducer(
        val surfaceId: SurfaceId,
        val stamps: () -> EventStamp,
        val input: RuntimeSurfaceInput,
    )

    /** La policy de ressources du test : budget drop de 1, timeout de claim court. */
    private fun dropTestResources(): ResourceBudgetPolicy = KadrePolicies.Default.resources.copy(
        maxConcurrentDropTransfers = 1,
        dropTransferClaimTimeout = 250.milliseconds,
    )

    /** La policy d'entrée du test — la même forme que les tests de livraison d'entrée. */
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

    /** La vraie source Task 1 d'un item texte — le resolver n'y est JAMAIS consulté. */
    private fun realTextSource(): AndroidDropTransferSource = AndroidDropTransferSource(
        AndroidClipSnapshot(
            listOf(
                AndroidClipItem(
                    uri = null,
                    text = REAL_TEXT,
                    mimeTypes = listOf("text/plain"),
                    displayNameHint = null,
                ),
            ),
        ),
        AndroidDropStreamOpener { error("a text clip item never consults the resolver") },
    )

    /**
     * La source enregistreuse du test — le miroir du `RecordedDropTransferSource` web : un
     * item Replayable d'un chunk, et le compte EXACT de ses closes (la sentinelle
     * close-exactly-once des cas de remplacement, de timeout et de teardown).
     */
    private class RecordedDropSource {
        val source: DropTransferSource = object : DropTransferSource {
            override val items: List<DropItemSource> = listOf(
                object : DropItemSource {
                    override val descriptor = DropItemDescriptor(
                        displayName = "clip.txt",
                        sizeBytes = null,
                        mimeTypes = listOf("text/plain"),
                        kind = DropItemKind.Text,
                    )
                    override val readMode = DropItemReadMode.Replayable
                    override suspend fun collectBytes(
                        maxChunkBytes: Int,
                        collector: suspend (ByteArray) -> Unit,
                    ): KadreResult<Unit> {
                        collector(byteArrayOf(1, 2, 3, 4))
                        return KadreResult.Success(Unit)
                    }
                },
            )

            override fun close() {
                closeCount += 1
            }
        }

        var closeCount: Int = 0
            private set
    }

    /**
     * La collecte des événements du réducteur — le canal ET le job qui le nourrit (la forme
     * des tests de livraison d'entrée) : le job DOIT être annulé à la fin du cas ([stop]),
     * sinon `runBlocking` joint à jamais un collecteur suspendu dans un flux qui ne se
     * termine pas de lui-même.
     */
    private class EventCollector(
        scope: CoroutineScope,
        input: SurfaceInput,
    ) {
        val channel = Channel<InputEvent>(Channel.UNLIMITED)

        private val job = scope.launch {
            input.events.collect { channel.send(it) }
        }

        /** Laisse le collecteur s'enregistrer (l'enregistrement est synchronisé sur ce thread). */
        suspend fun settle() {
            repeat(8) { yield() }
        }

        /** Annule le collecteur — le flux d'entrée est quitté, le test peut se joindre. */
        fun stop() {
            job.cancel()
        }

        suspend fun next(): InputEvent = withTimeout(5_000) { channel.receive() }

        /** Draine ce qui reste sans bloquer — le compte de l'inattendu. */
        suspend fun drainCount(): Int {
            var count = 0
            while (true) {
                val extra = channel.tryReceive().getOrNull() ?: break
                count += 1
                assertFalse(true, "unexpected event: $extra")
            }
            return count
        }
    }

    // -----------------------------------------------------------------------
    // La construction de PRODUCTION du cas 6 — le miroir exact des tests d'entrée phase 3
    // -----------------------------------------------------------------------

    /**
     * L'installation de la configuration de session — argument pour argument la forme de
     * production ([AndroidHostSurface.installSessionConfiguration] l'interprète ; le drapeau
     * drop n'y figure PAS, c'est justement le fait que le cas épingle).
     */
    private fun installProductionConfiguration(on: AndroidHostSurface) {
        val discrete = EventDeliveryPolicy(
            ingressCapacity = 64,
            collectorCapacity = 16,
            ingressOverflow = IngressOverflowAction.CloseSource,
            collectorOverflow = CollectorOverflowAction.CancelSlowCollector,
        )
        on.installSessionConfiguration(
            deliveryPolicy = WindowDeliveryPolicy(
                discreteEvents = discrete,
                geometryChanges = ContinuousDelivery.Coalesced,
                redrawRequests = ContinuousDelivery.Latest,
            ),
            inputDeliveryPolicy = testInputPolicy(),
            source = stampSource(),
            sessionFailureHandler = { },
            collectorAllocator = RuntimeEventCollectorAllocator(TEST_COLLECTORS_PER_SESSION),
            maxCollectorsPerFlow = TEST_COLLECTORS_PER_FLOW,
            resources = KadrePolicies.Default.resources,
            dropTransferScope = null,
            diagnostics = { },
            rawInputPort = null,
        )
    }

    private companion object {
        const val REAL_TEXT = "drop-bytes"
        const val TEST_COLLECTORS_PER_SESSION = 64
        const val TEST_COLLECTORS_PER_FLOW = 4
        val POSITION_ONE = LogicalPoint(10.0, 12.0)
        val POSITION_TWO = LogicalPoint(22.0, 14.0)
    }
}

/** L'état initial de surface du test — le même instantané honnête que les autres tests hôtes. */
private fun testSurfaceState(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Focused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

/** La clock inerte de CE test : les frames y sont posées et n'y courent jamais (aucun SDK). */
private object DropContractInertFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
