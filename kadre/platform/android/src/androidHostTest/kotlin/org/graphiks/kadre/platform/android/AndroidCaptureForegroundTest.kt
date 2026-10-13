package org.graphiks.kadre.platform.android

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * La machinerie FGS du capture (phase 8) contre les objets process-wide purs — JAMAIS le
 * SDK (`AndroidServiceCaptureForeground` exige un `Context`, que le stub android.jar lance ;
 * sa câblure réelle vit sous l'émulateur, scénario `androidCaptureFgsLifecycle`). Les trois
 * épingles :
 *
 * 1. `registryReadyConsumesTheArmedWait` — le signal `ready` CONSOMME l'entrée armée (le
 *    retrait de la review T2) : le registre process-wide n'accumule jamais une entrée par
 *    session au fil des runs, et un ré-armement du même jeton crée une attente NEUVE qui
 *    ne sera jamais signalée (l'admission d'une réservation empêche ce ré-armement) ;
 * 2. `ownershipStopsTheServiceAtTheLastReleaseOnly` — l'item I-3 de la review T2 : le
 *    service est UNIQUE par composant et la politique autorise quatre sessions de capture
 *    simultanées — le terminal de la première ne doit pas tuer le FGS sous les autres ;
 *    le compteur de détention ne décrète l'arrêt qu'à la DERNIÈRE relâche ;
 * 3. `foregroundRefusedCarriesTheCaughtCause` — le refus du contrôle FGS porte le
 *    `Throwable` attrapé en `cause` (l'item mineur de la review T2) pour le diagnostic.
 */
class AndroidCaptureForegroundTest {

    // -- (1) le signal `ready` consomme l'attente armée -------------------------------------------------

    @Test
    fun registryReadyConsumesTheArmedWait() = runBlocking {
        val armed = AndroidCaptureForegroundRegistry.arm("kadre-capture-1")
        assertFalse(armed.isCompleted, "l'attente armée ne se complète pas sans le signal du service")
        AndroidCaptureForegroundRegistry.ready("kadre-capture-1")
        armed.await() // le signal startForeground est délivré à l'attente

        // L'entrée est RETIRÉE au signal : un ré-armement du même jeton créerait une attente
        // neuve, jamais signalée — la preuve observable du retrait (aucun accumulateur
        // process-wide au fil des sessions).
        val reArmed = AndroidCaptureForegroundRegistry.arm("kadre-capture-1")
        assertFalse(reArmed.isCompleted, "l'entrée consommée n'est plus dans le registre : l'attente neuve n'est pas signalée")
        AndroidCaptureForegroundRegistry.forget("kadre-capture-1")

        // Un signal sans attente armée est absorbé (le chemin du refus a déjà oublié le jeton).
        AndroidCaptureForegroundRegistry.ready("kadre-capture-1")
    }

    // -- (2) l'arrêt du service n'a lieu qu'à la DERNIÈRE relâche (I-3) ----------------------------------

    @Test
    fun ownershipStopsTheServiceAtTheLastReleaseOnly() {
        // Deux sessions simultanées démarrent : deux acquisitions du service unique.
        AndroidCaptureForegroundOwnership.acquire()
        AndroidCaptureForegroundOwnership.acquire()
        assertFalse(
            AndroidCaptureForegroundOwnership.release(),
            "le terminal de la PREMIÈRE session ne stoppe pas le FGS sous la seconde",
        )
        assertTrue(
            AndroidCaptureForegroundOwnership.release(),
            "le terminal de la DERNIÈRE session déclenche l'arrêt réel du service",
        )

        // Le compteur revient à l'équilibre : un cycle complet suivant est indépendant.
        AndroidCaptureForegroundOwnership.acquire()
        assertTrue(AndroidCaptureForegroundOwnership.release(), "le cycle suivant part d'un compteur vide")
    }

    // -- (3) le refus porte sa cause ---------------------------------------------------------------------

    @Test
    fun foregroundRefusedCarriesTheCaughtCause() {
        val cause = IllegalStateException("the host is in the background")
        val refused = AndroidCaptureForegroundRefused("foreground-start-refused", cause)
        assertEquals("foreground-start-refused", refused.code)
        assertSame(cause, refused.cause)

        val bare = AndroidCaptureForegroundRefused("foreground-start-timeout")
        assertEquals("foreground-start-timeout", bare.code)
        assertEquals(null, bare.cause)
    }
}
