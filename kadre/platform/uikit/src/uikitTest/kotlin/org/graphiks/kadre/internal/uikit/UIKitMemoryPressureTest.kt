package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol

/**
 * La source de pression mémoire UIKit/tvOS (phase 8, Task 3) prouvée PAR SEAM INJECTÉ — le
 * test n'exerce JAMAIS le vrai `NSNotificationCenter` : un fake pilotable pose le block
 * observer, le déclenche et draine le relais. La chaîne réelle observer→relay→session sur le
 * VRAI centre est l'affaire du driver (Task 6, stimulus charté).
 *
 * Les sémantiques épinglées (le moule Android `AndroidMemoryPressureSource` /
 * `AndroidProcessMemoryPressure`) :
 *
 * - les TROIS états de capability du créneau : jamais tentée → `Unsupported` ;
 *   installée → `Available` ; échec d'install →
 *   `Unavailable(PlatformFailure(UIKit, "memory-pressure", "source-exception"))` — et
 *   l'échec d'une source VIVANTE est collant (le second créneau reçoit la même
 *   `Unavailable`, sans re-registration) ;
 * - la garde de double registration : un second `newHost` réutilise la source vivante, ne
 *   re-registre JAMAIS ;
 * - arrival-before-wire : PERDU (règle documentée — la pression est un état transient, rien
 *   n'est câblé pour la recevoir ; le prochain warning rétablit le tableau), jamais rejoué ;
 * - wire-before-arrival : livré exactement une fois, `Moderate`, HORS de la pile du block
 *   observer (le relais ne court que sur un tour ULTÉRIEUR de la file — prouvé par l'ordre) ;
 * - fan-out vers TOUS les hosts vivants câblés (la réalité multi-scènes) ;
 * - `hostTerminated` coupe LE host terminé (et ignore un câblage tardif) ; les survivants
 *   reçoivent encore ;
 * - le DERNIER libéré ferme : le token posé est retiré EXACTEMENT (un fail-install ne
 *   désenregistre jamais à l'aveugle), un warning en vol derrière le close ne relaie rien,
 *   la capability retombe au défaut — et la session suivante réinstalle (cycle neuf) ;
 * - la mapping KERNEL : `UIApplicationDidReceiveMemoryWarningNotification` → `Moderate`,
 *   JAMAIS `Critical` (gravity inventée interdite), et c'est l'UNIQUE notification jamais
 *   enregistrée.
 */
class UIKitMemoryPressureTest {

    /** Le jeton d'observer — le seam factice ne voit jamais un vrai token du centre. */
    private class FakeObserverToken : NSObject(), NSObjectProtocol

    /**
     * Le seam factice : l'observation pilotée (succès ou lancer), le block capturé, le relais
     * HORS pile — `post` met en file, [drain] exécute. Aucune livraison ne peut donc courir
     * dans la pile de [fire] : c'est la preuve d'ordre du hors-pile.
     */
    private class FakeMemoryPressureSeam : UIKitMemoryPressureSeam {
        var failInstall: Boolean = false
        var attemptedNames = ArrayList<String>()
        var registeredNames = ArrayList<String>()
        var unregisteredCount = 0
        var observerBlock: (() -> Unit)? = null
            private set
        val posted = ArrayDeque<() -> Unit>()

        override fun observe(name: String, onEvent: () -> Unit): NSObjectProtocol {
            // La TENTATIVE est comptée avant le lancer : c'est elle que la garde de
            // double-registration observe (une install qui échoue a été tentée une fois).
            attemptedNames.add(name)
            if (failInstall) throw IllegalStateException("install failed")
            registeredNames.add(name)
            observerBlock = onEvent
            return FakeObserverToken()
        }

        override fun removeObserver(token: NSObjectProtocol) {
            unregisteredCount += 1
        }

        override fun post(block: () -> Unit) {
            posted.addLast(block)
        }

        /** Le stimulus : le block observer posé par la source, sur SA pile (comme le centre). */
        fun fire() {
            observerBlock?.invoke()
        }

        /** Le tour ULTÉRIEUR de la file : le relais posé par [post] court ici, pas avant. */
        fun drain() {
            while (posted.isNotEmpty()) posted.removeFirst().invoke()
        }
    }

    /** Le collecteur d'un host câblé — la liste des niveaux reçus. */
    private class Collector {
        val levels = ArrayList<MemoryPressureLevel>()
        val relay: (MemoryPressureLevel) -> Unit = { levels.add(it) }
    }

    /** Le nom UNIQUE que la source a le droit d'enregistrer — le fait notification de UIKit. */
    private val memoryWarningName = UIApplicationDidReceiveMemoryWarningNotification!!

    // ---- Les trois états de capability du créneau -------------------------------

    @Test
    fun claimCapabilitiesCarryTheThreeInstallOutcomes() {
        // Jamais tentée (pas de seam) : le défaut honnête — jamais une PlatformFailure inventée.
        val unattempted = UIKitProcessMemoryPressure().newHost(null)
        assertEquals(FeatureAvailability.Unsupported, unattempted.capabilities.memoryPressure)

        // Install réussie : Available.
        val installed = UIKitProcessMemoryPressure().newHost(FakeMemoryPressureSeam())
        assertEquals(FeatureAvailability.Available, installed.capabilities.memoryPressure)

        // Un register qui lance : Unavailable(PlatformFailure) — le triple exact.
        val failing = FakeMemoryPressureSeam().apply { failInstall = true }
        val failed = UIKitProcessMemoryPressure().newHost(failing)
        val availability = failed.capabilities.memoryPressure
        assertIs<FeatureAvailability.Unavailable>(availability)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "memory-pressure", "source-exception"),
            availability.failure,
        )
    }

    @Test
    fun ownerCapabilitiesMirrorTheClaimAtCreationAndAfterLastRelease() {
        val owner = UIKitProcessMemoryPressure()
        assertEquals(FeatureAvailability.Unsupported, owner.currentCapabilities.memoryPressure)
        val claim = owner.newHost(FakeMemoryPressureSeam())
        assertEquals(FeatureAvailability.Available, owner.currentCapabilities.memoryPressure)
        claim.hostTerminated()
        assertEquals(FeatureAvailability.Unsupported, owner.currentCapabilities.memoryPressure)
    }

    // ---- La garde de double registration -----------------------------------------

    @Test
    fun aSecondHostReusesTheLiveSourceWithoutReRegistering() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val first = owner.newHost(seam)
        val second = owner.newHost(seam)
        assertEquals(listOf(memoryWarningName), seam.registeredNames)
        assertEquals(FeatureAvailability.Available, first.capabilities.memoryPressure)
        assertEquals(FeatureAvailability.Available, second.capabilities.memoryPressure)
    }

    // ---- Arrival-before-wire vs wire-before-arrival (règle documentée : DROP) ----

    @Test
    fun anArrivalBeforeWireIsDroppedNeverReplayed() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val claim = owner.newHost(seam)
        val collector = Collector()
        // L'arrivée PREND avant le câblage : rien n'est câblé pour la recevoir — perdue.
        seam.fire()
        claim.wire(collector.relay)
        seam.drain()
        assertEquals(emptyList(), collector.levels)
        // Le prochain warning (un épisode RÉEL, jamais un replay du premier) est livré LUI.
        seam.fire()
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), collector.levels)
    }

    @Test
    fun wireBeforeArrivalDeliversExactlyOnce() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val claim = owner.newHost(seam)
        val collector = Collector()
        claim.wire(collector.relay)
        seam.fire()
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), collector.levels)
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), collector.levels)
    }

    @Test
    fun queuedWarningExcludesNewHostWiredBeforeDrainWhileSurvivorReceivesIt() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val first = owner.newHost(seam)
        val firstCollector = Collector()
        first.wire(firstCollector.relay)
        seam.fire()
        val second = owner.newHost(seam)
        val secondCollector = Collector()
        second.wire(secondCollector.relay)
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), firstCollector.levels)
        assertEquals(emptyList(), secondCollector.levels)
        seam.fire()
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), secondCollector.levels)
        first.hostTerminated()
        second.hostTerminated()
    }

    // ---- Le relais HORS de la pile du block observer ------------------------------

    @Test
    fun theRelayNeverRunsOnTheNotificationStack() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val claim = owner.newHost(seam)
        val collector = Collector()
        claim.wire(collector.relay)
        seam.fire()
        // Le block observer est REVENNU (fire a rendu) : aucune livraison n'a couru dans SA pile.
        assertEquals(emptyList(), collector.levels)
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), collector.levels)
    }

    // ---- Le fan-out vers TOUS les hosts vivants (réalité multi-scènes) ------------

    @Test
    fun fanOutReachesEveryWiredHostOfTheProcessSource() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val claims = List(3) { owner.newHost(seam) }
        val collectors = List(3) { Collector() }
        claims.forEachIndexed { index, claim -> claim.wire(collectors[index].relay) }
        seam.fire()
        seam.drain()
        collectors.forEach { collector ->
            assertEquals(listOf(MemoryPressureLevel.Moderate), collector.levels)
        }
    }

    // ---- Terminaison d'un host, survivants, câblage tardif refusé -----------------

    @Test
    fun hostTerminatedStopsItsRelayWhileSurvivorsKeepReceiving() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val ended = owner.newHost(seam)
        val survivor = owner.newHost(seam)
        val endedCollector = Collector()
        val survivorCollector = Collector()
        ended.wire(endedCollector.relay)
        survivor.wire(survivorCollector.relay)

        ended.hostTerminated()
        seam.fire()
        seam.drain()
        assertEquals(emptyList(), endedCollector.levels)
        assertEquals(listOf(MemoryPressureLevel.Moderate), survivorCollector.levels)
        // La source process survit au host terminé : rien n'est désenregistré.
        assertEquals(0, seam.unregisteredCount)
    }

    @Test
    fun wiringATerminatedClaimIsIgnoredAndTerminationIsIdempotent() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val claim = owner.newHost(seam)
        claim.hostTerminated()
        val lateCollector = Collector()
        claim.wire(lateCollector.relay)
        claim.hostTerminated()
        seam.fire()
        seam.drain()
        assertEquals(emptyList(), lateCollector.levels)
        // Le créneau était SEUL : la première libération EST le dernier libéré — l'exact token
        // est retiré UNE fois (la seconde terminaison est un no-op), et le fire d'après-close
        // ne relaie rien.
        assertEquals(1, seam.unregisteredCount)
    }

    // ---- Le DERNIER libéré ferme : retrait exact du token posé, cycle neuf ---------

    @Test
    fun theLastReleaseUnregistersExactlyThePlacedTokenAndClosesTheSource() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val first = owner.newHost(seam)
        val last = owner.newHost(seam)
        val collector = Collector()
        last.wire(collector.relay)

        first.hostTerminated()
        assertEquals(0, seam.unregisteredCount)
        last.hostTerminated()
        // L'EXACTE instance posée, une seule fois.
        assertEquals(1, seam.unregisteredCount)
        // Un warning en vol derrière le close ne relaie rien.
        seam.fire()
        seam.drain()
        assertEquals(emptyList(), collector.levels)
    }

    @Test
    fun aLiveFailedSourceHandsUnavailableToTheNextClaimantWithoutReRegistering() {
        // L'état échec d'une source vivante est COLLANT : le second créneau reçoit la MÊME
        // Unavailable(PlatformFailure), sans seconde registration.
        val owner = UIKitProcessMemoryPressure()
        val failing = FakeMemoryPressureSeam().apply { failInstall = true }
        owner.newHost(failing)
        val second = owner.newHost(failing)
        val availability = second.capabilities.memoryPressure
        assertIs<FeatureAvailability.Unavailable>(availability)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "memory-pressure", "source-exception"),
            availability.failure,
        )
        // UNE seule tentative d'install pour les DEUX créneaux : la source vivante est
        // réutilisée, échec compris — jamais une seconde registration (ni tentée).
        assertEquals(listOf(memoryWarningName), failing.attemptedNames)
    }

    @Test
    fun aFailedInstallNeverUnregistersAndTheNextHostReinstalls() {
        val owner = UIKitProcessMemoryPressure()
        val failing = FakeMemoryPressureSeam().apply { failInstall = true }
        val claim = owner.newHost(failing)
        claim.hostTerminated()
        assertEquals(0, failing.unregisteredCount)

        // La session suivante repart d'une source NEUVE : réinstallation réelle.
        val fresh = FakeMemoryPressureSeam()
        val freshClaim = owner.newHost(fresh)
        assertEquals(listOf(memoryWarningName), fresh.registeredNames)
        assertEquals(FeatureAvailability.Available, freshClaim.capabilities.memoryPressure)
    }

    // ---- La mapping KERNEL : Moderate SEUL, jamais Critical ------------------------

    @Test
    fun theMappingKernelIsModerateOnlyNeverCritical() {
        // La marche de la kernel — l'UNIQUE fait notification de UIKit donne l'UNIQUE niveau :
        // Moderate. Critical serait une gravity INVENTÉE — jamais produite, par construction.
        val mapped = uikitMemoryPressureLevelOfMemoryWarning()
        assertEquals(MemoryPressureLevel.Moderate, mapped)
        assertNotEquals(MemoryPressureLevel.Critical, mapped)
    }

    @Test
    fun everyDeliveredLevelThroughTheSourceIsModerateNeverCritical() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        val claim = owner.newHost(seam)
        val collector = Collector()
        claim.wire(collector.relay)
        repeat(4) {
            seam.fire()
            seam.drain()
        }
        assertEquals(4, collector.levels.size)
        assertTrue(collector.levels.all { it == MemoryPressureLevel.Moderate })
        assertFalse(collector.levels.any { it == MemoryPressureLevel.Critical })
    }

    @Test
    fun theSourceRegistersTheMemoryWarningNotificationAndNothingElse() {
        val owner = UIKitProcessMemoryPressure()
        val seam = FakeMemoryPressureSeam()
        owner.newHost(seam)
        assertEquals(listOf(memoryWarningName), seam.registeredNames)
        assertEquals(1, seam.registeredNames.size)
    }

    // ---- Le garde du harnais lui-même (le fake est honnête) ------------------------

    @Test
    fun theFakeSeamHarnessIsHonest() {
        val seam = FakeMemoryPressureSeam()
        assertNull(seam.observerBlock)
        val token = seam.observe(memoryWarningName) { }
        assertIs<NSObjectProtocol>(token)
        assertEquals(listOf(memoryWarningName), seam.registeredNames)
        seam.post { }
        assertEquals(1, seam.posted.size)
        seam.fire()
        seam.drain()
        assertTrue(seam.posted.isEmpty())
    }
}
