package org.graphiks.kadre.integration.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * La machine à états PURE du binding compose — create/keep/close/recreate — pilotée par les
 * seuls événements de cycle de vie de la vue hôte. Aucun type Android ni Compose : les View
 * ne sont pas constructibles hors émulateur (le stub android.jar lance), et le compose
 * runtime ne tourne pas dans androidHostTest — la wiring `KadreComposeHost` est prouvée par
 * l'instrumentation de la Task 4. Ici, la sémantique normative (roadmap §9 : UNE session par
 * host, fermer à la sortie, recréer après détachement terminal, une recomposition ne crée
 * pas) est épinglée sur la machine nue.
 */
class KadreComposeBindingTest {

    /** Le billet factice — le T de la machine générique (une session, pour les tests). */
    private class Ticket(val id: Int)

    /** La fabrique enregistrée : l'histoire complète des créations et fermetures. */
    private class Factory {
        val created = ArrayList<Ticket>()
        val closed = ArrayList<Ticket>()
        var refuse = false

        fun create(): Ticket? {
            if (refuse) return null
            return Ticket(created.size + 1).also(created::add)
        }

        fun binding(): KadreComposeBinding<Ticket> =
            KadreComposeBinding(create = ::create, close = { ticket -> closed.add(ticket) })
    }

    @Test
    fun firstAttachedEventCreatesTheSession() {
        val factory = Factory()
        val binding = factory.binding()

        binding.onSurfaceAttached()

        assertEquals(1, factory.created.size, "exactly ONE session per attach event")
        assertSame(factory.created.single(), binding.current)
    }

    @Test
    fun repeatedAttachedEventsKeepTheSameSession() {
        // La recomposition ne transporte AUCUN événement vers la machine (la wiring garde
        // la session dans remember) ; même un ÉVÉNEMENT d'attach répété (le listener est
        // re-armé, la fenêtre re-attache le même conteneur) ne recrée pas : le capture
        // l'emporte — même policy ou content lambdas changés, aucune re-attach.
        val factory = Factory()
        val binding = factory.binding()
        binding.onSurfaceAttached()
        val first = binding.current

        binding.onSurfaceAttached()
        binding.onSurfaceAttached()

        assertEquals(1, factory.created.size, "no second session on a kept host")
        assertSame(first, binding.current)
    }

    @Test
    fun disposeClosesTheLiveSession() {
        val factory = Factory()
        val binding = factory.binding()
        binding.onSurfaceAttached()
        val live = binding.current!!

        binding.onHostDisposed()

        assertEquals(listOf(live), factory.closed, "onDispose closes the session (host stop)")
        assertEquals(null, binding.current)
    }

    @Test
    fun disposeWithoutSessionClosesNothing() {
        val factory = Factory()
        val binding = factory.binding()

        binding.onHostDisposed()

        assertTrue(factory.closed.isEmpty(), "nothing to close — never a spurious stop")
    }

    @Test
    fun terminalDetachReopensTheSlotWithoutClosing() {
        // Le détachement de la vue est un terminal appartenant au RUNTIME (le binding P1
        // propose `Stopped(HostDetached)`) : la machine ne DOUBLE pas l'arrêt — elle rouvre
        // seulement le créneau pour la recréation.
        val factory = Factory()
        val binding = factory.binding()
        binding.onSurfaceAttached()

        binding.onSurfaceDetached()

        assertTrue(factory.closed.isEmpty(), "the detach stop belongs to the runtime, not the binding")
        assertEquals(null, binding.current, "the slot reopens — the next composition recreates")
    }

    @Test
    fun reattachAfterTerminalDetachCreatesANewSession() {
        val factory = Factory()
        val binding = factory.binding()
        binding.onSurfaceAttached()
        val first = binding.current!!
        binding.onSurfaceDetached()

        binding.onSurfaceAttached()

        assertEquals(2, factory.created.size, "a NEW session after the terminal detach")
        assertEquals(first.id + 1, binding.current?.id)
    }

    @Test
    fun attachRefusalLeavesTheSlotOpenAndTheNextEventRetries() {
        // Un refus d'admission (`KadreResult.Failure` — vue non attachée, réservation
        // vivante d'un host précédent…) ne laisse AUCUN billet : le créneau reste ouvert
        // et l'événement d'attach suivant retente — jamais de session fantôme.
        val factory = Factory().apply { refuse = true }
        val binding = factory.binding()
        binding.onSurfaceAttached()
        assertEquals(null, binding.current)

        factory.refuse = false
        binding.onSurfaceAttached()

        assertEquals(1, factory.created.size, "the retry creates the session")
        assertSame(factory.created.single(), binding.current)
    }

    @Test
    fun disposeAfterDetachClosesNothing() {
        // Sortie de composition APRÈS un détachement terminal : le runtime a déjà arrêté
        // la session au détachement — onDispose ne ferme RIEN (le créneau est vide).
        val factory = Factory()
        val binding = factory.binding()
        binding.onSurfaceAttached()
        binding.onSurfaceDetached()

        binding.onHostDisposed()

        assertTrue(factory.closed.isEmpty())
    }

    @Test
    fun disposeAfterRecreationClosesExactlyTheLiveSession() {
        val factory = Factory()
        val binding = factory.binding()
        binding.onSurfaceAttached()
        val first = binding.current!!
        binding.onSurfaceDetached()
        binding.onSurfaceAttached()
        val second = binding.current!!

        binding.onHostDisposed()

        assertEquals(listOf(second), factory.closed, "only the LIVE ticket is closed on dispose")
        assertTrue(first !in factory.closed, "the detached ticket was never the binding's to close")
    }
}
