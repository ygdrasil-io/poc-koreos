package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La machine à états drop d'une session (l'implémentation [UIKitDropSessionHooks] du seam pur
 * [UIKitDropHooks]), prouvée PUR — aucun type UIKit : un faux sink de dispatch et un faux sink de
 * publication enregistreurs tiennent les deux coutures. Les sources sont les VRAIES sources de la
 * couture ([UIKitDropTransferSource], sans payload) enveloppées dans un enregistreur de close —
 * le contrat « la source est owned par le runtime dès le dispatch » y devient observable.
 *
 * Contrats de la machine (le plan, cinq chemins) :
 *
 * - **entered-null n'émet RIEN.** Un dispatch refusé (surface inconnue, consumer sans
 *   AcceptDrop) rend `null` ET le runtime a déjà fermé la source : les hooks ne retiennent
 *   aucune offre, ne referment PAS une seconde fois, ne publient aucun stimulus — le
 *   DropEntered vit dans l'événement d'interaction du dispatch, jamais dans un stimulus ;
 * - **moved avant entered → faux.** Aucune offre active, aucun stimulus — un mouvement hors
 *   session est perdu, jamais résurrecté ;
 * - **exited/performed uniquement avec l'offre ACTIVE.** Un offerId étranger est un no-op
 *   silencieux ; l'offre active publie UNE fois puis disparaît (un second exited/performed pour
 *   le même id ne republie pas) ;
 * - **close en pleine offre n'émet plus rien.** Après close, holdsActiveOffer répond faux,
 *   moved répond faux, exited/performed sont des no-ops, et un entered tardif est refusé — la
 *   source qu'il porte est refermée PAR LES HOOKS (le dispatch n'ayant jamais eu lieu, le
 *   runtime ne la refermera jamais) ;
 * - **ré-entrée après exit est un chemin d'offre FRAIS.** La nouvelle offre remplace
 *   exactement, l'ancien id devient étranger.
 */
class UIKitDropHooksTest {

    /** Le surfaceId fixe des hooks sous test (la phase est mono-surface, le port n'a qu'un peer). */
    private val surfaceId = SurfaceId(42L)

    /** La position fixe des observations (pure, sans UIKit). */
    private val position = LogicalPoint(3.0, 4.0)

    /** La fausse source : les VRAIES coutures, plus le compte des closes (le contrat runtime). */
    private class RecordingSource : DropTransferSource {
        var closeCount: Int = 0
            private set

        private val entry = UIKitDropItem(
            checkNotNull(ukitDropSnapshot(listOf("public.utf8-plain-text"), displayName = null, sizeBytes = null)),
        )
        private val source = UIKitDropTransferSource(listOf(entry))

        override val items: List<DropItemSource>
            get() = source.items

        override fun close() {
            closeCount += 1
            source.close()
        }
    }

    /** Le faux sink de dispatch : tient la prochaine offre à rendre, imite le contrat runtime (close sur le chemin refusant), enregistre les dispatches. */
    private class FakeDropSink {
        val dispatched: MutableList<DropTransferSource> = mutableListOf()
        var nextOffer: DropOfferId? = null

        fun sink(surfaceId: SurfaceId, source: DropTransferSource, position: LogicalPoint): DropOfferId? {
            dispatched += source
            val offer = nextOffer
            if (offer == null) {
                // Le contrat de dispatchSynchronousDrop : la source est owned par le runtime dès
                // l'appel, fermée sur tout chemin qui ne rend pas d'offre.
                source.close()
            }
            return offer
        }
    }

    /** Le faux sink de publication : les stimuli émis, dans l'ordre. */
    private class FakePublication {
        val published: MutableList<SurfaceStimulus> = mutableListOf()

        fun publish(stimulus: SurfaceStimulus): Boolean {
            published += stimulus
            return true
        }
    }

    /** Le harnais d'un test : les deux faux + les hooks construits PAR-DESSUS, comme le port le fait. */
    private inner class Fixture {
        val dispatch = FakeDropSink()
        val publication = FakePublication()
        val hooks = UIKitDropSessionHooks(surfaceId, dispatch::sink, publication::publish)
    }

    // MARK: - entered-null : aucun stimulus, aucune offre retenue, aucune double close

    @Test
    fun enteredNullPathEmitsNoStimulusAndRetainsNothing() {
        val fixture = Fixture()
        fixture.dispatch.nextOffer = null
        val source = RecordingSource()

        val offer = fixture.hooks.entered(source, position)

        assertNull(offer, "a refused dispatch returns no offer")
        assertFalse(fixture.hooks.holdsActiveOffer(), "no offer is held on the refusing path")
        assertTrue(
            fixture.publication.published.isEmpty(),
            "the null path emits NO stimulus — admission lives in the dispatch's interaction event",
        )
        assertEquals(1, source.closeCount, "the runtime closed the source once; the hooks must not close it again")
        // Un mouvement tardif pour l'offre jamais allouée est perdu et silencieux.
        assertFalse(fixture.hooks.moved(DropOfferId(7L), position))
        assertTrue(fixture.publication.published.isEmpty())
    }

    // MARK: - moved avant entered : faux, rien publié

    @Test
    fun movedBeforeAnyEnteredIsFalseAndSilent() {
        val fixture = Fixture()
        assertFalse(fixture.hooks.moved(DropOfferId(0L), position))
        assertTrue(fixture.publication.published.isEmpty(), "a move without a session publishes nothing")
    }

    // MARK: - entered retient, moved publie pour l'offre ACTIVE seule

    @Test
    fun enteredRetainsTheOfferAndMovedPublishesOnlyForTheActiveOffer() {
        val fixture = Fixture()
        fixture.dispatch.nextOffer = DropOfferId(11L)

        val offer = assertNotNull(fixture.hooks.entered(RecordingSource(), position), "the offered id is returned")
        assertEquals(DropOfferId(11L), offer)
        assertTrue(fixture.hooks.holdsActiveOffer())

        assertTrue(fixture.hooks.moved(offer, position), "a move for the active offer is held")
        assertEquals(
            listOf<SurfaceStimulus>(SurfaceStimulus.DropMoved(surfaceId, offer, position)),
            fixture.publication.published,
        )

        // Un id étranger ne publie rien et ne répond pas « tenu ».
        assertFalse(fixture.hooks.moved(DropOfferId(99L), position), "a foreign offer id is not the held offer")
        assertEquals(1, fixture.publication.published.size, "a foreign move publishes nothing")
    }

    // MARK: - exited : uniquement avec l'offre active, une seule fois

    @Test
    fun exitedPublishesOnceForTheActiveOfferAndIsANoOpAfterwards() {
        val fixture = Fixture()
        fixture.dispatch.nextOffer = DropOfferId(21L)
        val offer = assertNotNull(fixture.hooks.entered(RecordingSource(), position))

        fixture.hooks.exited(DropOfferId(999L))
        assertTrue(fixture.publication.published.isEmpty(), "an exit for a foreign id publishes nothing")
        assertTrue(fixture.hooks.holdsActiveOffer(), "a foreign exit does not end the active offer")

        fixture.hooks.exited(offer)
        assertEquals(
            listOf<SurfaceStimulus>(SurfaceStimulus.DropExited(surfaceId, offer)),
            fixture.publication.published,
        )
        assertFalse(fixture.hooks.holdsActiveOffer())
        assertFalse(fixture.hooks.moved(offer, position), "a move after the exit is lost")

        fixture.hooks.exited(offer)
        assertEquals(1, fixture.publication.published.size, "a second exit for the same id does not republish")
    }

    // MARK: - performed : uniquement avec l'offre active, une seule fois

    @Test
    fun performedPublishesOnceForTheActiveOfferAndConsumesTheOffer() {
        val fixture = Fixture()
        fixture.dispatch.nextOffer = DropOfferId(31L)
        val offer = assertNotNull(fixture.hooks.entered(RecordingSource(), position))

        assertFalse(fixture.hooks.performed(DropOfferId(1000L), position), "a perform for a foreign id completes nothing")
        assertTrue(fixture.publication.published.isEmpty())

        assertTrue(fixture.hooks.performed(offer, position), "the drop completed into the live offer")
        assertEquals(
            listOf<SurfaceStimulus>(SurfaceStimulus.DropPerformed(surfaceId, offer, position)),
            fixture.publication.published,
        )
        assertFalse(fixture.hooks.holdsActiveOffer(), "the offer is consumed by the perform")
        assertFalse(fixture.hooks.moved(offer, position))
        assertFalse(fixture.hooks.performed(offer, position), "a second perform for the same id completes nothing")
        assertEquals(1, fixture.publication.published.size)
    }

    // MARK: - close en pleine offre : plus AUCUN stimulus, entered tardif refusé

    @Test
    fun closeMidOfferEmitsNoFurtherStimulusAndRefusesLateEntries() {
        val fixture = Fixture()
        fixture.dispatch.nextOffer = DropOfferId(41L)
        val offer = assertNotNull(fixture.hooks.entered(RecordingSource(), position))
        assertTrue(fixture.hooks.holdsActiveOffer())

        fixture.hooks.close()
        fixture.hooks.close() // idempotent

        assertFalse(fixture.hooks.holdsActiveOffer(), "a closed hooks holds nothing")
        assertFalse(fixture.hooks.moved(offer, position), "no move survives the close")
        fixture.hooks.exited(offer)
        assertFalse(fixture.hooks.performed(offer, position), "no perform survives the close")
        assertTrue(fixture.publication.published.isEmpty(), "close mid-offer emits NO further stimulus")

        val late = RecordingSource()
        assertNull(fixture.hooks.entered(late, position), "a late entry on closed hooks is refused")
        assertEquals(1, fixture.dispatch.dispatched.size, "only the pre-close entry ever reached the dispatch")
        assertFalse(
            fixture.dispatch.dispatched.contains(late),
            "the late source never reached the dispatch",
        )
        assertEquals(1, late.closeCount, "the hooks close the late source — the runtime never saw it")
    }

    // MARK: - ré-entrée après exit : un chemin d'offre FRAIS

    @Test
    fun reEnterAfterExitIsAFreshOfferPath() {
        val fixture = Fixture()

        fixture.dispatch.nextOffer = DropOfferId(51L)
        val first = assertNotNull(fixture.hooks.entered(RecordingSource(), position))
        fixture.hooks.exited(first)

        fixture.dispatch.nextOffer = DropOfferId(52L)
        val second = assertNotNull(fixture.hooks.entered(RecordingSource(), position), "a fresh entry opens a fresh offer")
        assertTrue(fixture.hooks.holdsActiveOffer())

        assertTrue(fixture.hooks.moved(second, position))
        assertFalse(fixture.hooks.moved(first, position), "the previous offer id is foreign now")
        assertEquals(
            listOf<SurfaceStimulus>(
                SurfaceStimulus.DropExited(surfaceId, first),
                SurfaceStimulus.DropMoved(surfaceId, second, position),
            ),
            fixture.publication.published,
        )
    }
}
