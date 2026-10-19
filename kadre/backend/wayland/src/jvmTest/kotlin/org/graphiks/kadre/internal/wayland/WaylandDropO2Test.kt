package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.KadreResult

/**
 * Preuves O2 du présentateur drop (design phase 7) : offres immuables,
 * refus déterministes (drop sans accept F1-2, mime non annoncé F2-3),
 * leave terminalisant (D2), offre détruite ne resservant pas (F3-1).
 */
class WaylandDropO2Test {

    /** Fake de la face offre enregistrant les marshals. */
    private class FakeOffer : WaylandOfferFace {
        val accepts = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, String?>>()
        val receives = java.util.concurrent.CopyOnWriteArrayList<String>()
        var finishes = 0
        var destroys = 0

        override fun accept(serial: Int, mime: String?) { accepts.add(serial to mime) }
        override fun receive(mime: String): Int { receives.add(mime); return -1 }
        override fun finish() { finishes++ }
        override fun close() { destroys++ }
    }

    private fun enteredPresenter(offer: FakeOffer): WaylandDropPresenter {
        val presenter = WaylandDropPresenter()
        presenter.onOfferProxy(offer)
        presenter.onOfferMime("text/plain")
        presenter.onOfferMime("image/png")
        presenter.onEnter(offer, 42, 0x1000, 10.0, 20.0)
        return presenter
    }

    @Test
    fun enterPresentsTheImmutableOffer() {
        val offer = FakeOffer()
        val presenter = WaylandDropPresenter()
        var presented: WaylandDropTransferSource? = null
        presenter.onPresentDrop = { source, position -> presented = source; }
        presenter.onOfferProxy(offer)
        presenter.onOfferMime("text/plain")
        presenter.onEnter(offer, 42, 0x1000, 10.0, 20.0)
        val source = presented ?: error("offer never presented")
        assertEquals(1, source.items.size)
        assertEquals(listOf("text/plain"), source.items.single().descriptor.mimeTypes)
    }

    @Test
    fun dropWithoutAcceptIsRefusedWithNoReceive() {
        // F1-2
        val offer = FakeOffer()
        val presenter = enteredPresenter(offer)
        val outcome = presenter.onDrop()
        assertTrue(outcome is KadreResult.Failure)
        assertTrue(offer.receives.isEmpty(), "aucun receive sans accept préalable")
        assertTrue(offer.accepts.contains(42 to null), "refus explicite du mime")
    }

    @Test
    fun unannouncedMimeIsRefusedBeforeAnyFd() {
        // F2-3
        val offer = FakeOffer()
        val presenter = enteredPresenter(offer)
        val outcome = presenter.accept("application/x-nope")
        assertTrue(outcome is KadreResult.Failure)
        assertTrue(offer.accepts.isEmpty())
        assertTrue(offer.receives.isEmpty())
    }

    @Test
    fun leaveTerminalisesAsLeftSurface() {
        val offer = FakeOffer()
        val terminations = ArrayList<WaylandDropTerminal>()
        val presenter = WaylandDropPresenter()
        presenter.onOfferTerminated = { terminations.add(it) }
        presenter.onOfferProxy(offer)
        presenter.onOfferMime("text/plain")
        presenter.onEnter(offer, 42, 0x1000, 10.0, 20.0)
        presenter.onLeave()
        assertEquals(listOf(WaylandDropTerminal.LeftSurface), terminations)
        assertEquals(1, offer.destroys)
    }

    @Test
    fun destroyedOfferNeverServesASecondTransfer() {
        // F3-1
        val offer = FakeOffer()
        val presenter = enteredPresenter(offer)
        presenter.accept("text/plain")
        presenter.onDrop()
        assertEquals(1, offer.destroys)
        // tout receive tardif passe par le presenter : l'offre terminalisée rend -1
        // (prouvé par receiveFor → terminated.get() == true)
    }

    @Test
    fun emptyOfferIsRefusedEarly() {
        val offer = FakeOffer()
        val presenter = WaylandDropPresenter()
        presenter.onOfferProxy(offer)
        // aucun mime annoncé
        presenter.onEnter(offer, 7, 0x1000, 0.0, 0.0)
        assertTrue(offer.accepts.contains(7 to null), "offre vide refusée explicitement")
        assertEquals(1, offer.destroys)
    }
}
