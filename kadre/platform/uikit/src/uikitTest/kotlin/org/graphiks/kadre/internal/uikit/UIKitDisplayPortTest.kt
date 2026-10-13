package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import platform.Foundation.NSThread

/**
 * Le port display UIKit/tvOS (phase 8, Task 2) prouvé PAR JETONS de seam — le port ne nomme
 * JAMAIS un type UIScreen (le pattern jeton du module) : une source factice pilote l'inventaire,
 * les fires et les faillites, et ce qui est prouvé est la TRADUCTION du seam en formes port.
 *
 * Les sémantiques épinglées (le moule Android `AndroidDisplayPort`, le contrat runtime
 * `DisplayPort`) :
 *
 * - l'install publie l'inventaire courant SYNCHRONEMENT, exactement une fois, vers SON observer ;
 * - un SECOND install remplace le premier (le handle périmé devient un no-op qui ne
 *   désenregistre jamais l'install vivante) ;
 * - chaque fire relit l'inventaire EN ENTIER — un changement d'écrans est un NOUVEAU snapshot,
 *   jamais un delta ;
 * - une géométrie non portable (le kernel répond null) est un échec TOUT-OU-RIEN :
 *   `TemporarilyUnavailable(retryable=true)` qui RETIRE l'inventaire précédent — un snapshot
 *   réussi ne contient jamais un inventaire partiel (le risque nommé n°2 du plan, miroir
 *   Android `translatedInventory`) ;
 * - les clés sont celles des FAITS (le port ne réalloue jamais — le contrat T1 : la clé est
 *   portée telle quelle) ;
 * - le ruling du type est INJECTÉ : l'écran hôte de la session (la clé du resolver) reçoit
 *   `ruling(true)`, les autres `ruling(false)` ; sans resolver, le fait `isMain` du seam
 *   (UIScreen.mainScreen) est le dernier recours — le fait T1 rendu VIVANT ici ;
 * - le close est idempotent, ferme le seam possédé exactement une fois, et un install après
 *   close lève.
 */
class UIKitDisplayPortTest {

    /** Le jeton d'écran — le port ne voit jamais un vrai UIScreen. */
    private class ScreenToken(val label: String) {
        override fun toString(): String = "Screen($label)"
    }

    /**
     * Le seam factice : l'inventaire piloté, les faits par jeton, les listeners comptés. Le
     * compteur `mainThreadSeamReads` épingle OÙ la relecture a couru — la matière du pin du
     * funnel main (revue Task 2, Important-1).
     */
    private class FakeDisplaySource : UIKitDisplaySource {
        var inventory: List<Any> = emptyList()
        val factsByScreen = HashMap<Any, UIKitDisplayFacts>()
        val registered = ArrayList<() -> Unit>()
        var closedCount = 0
        var seamReads = 0
            private set
        var mainThreadSeamReads = 0
            private set

        fun screen(
            label: String,
            key: Long,
            name: String? = label,
            width: Double = 1920.0,
            height: Double = 1080.0,
            scale: Double = 2.0,
            isMain: Boolean = false,
        ): ScreenToken {
            val token = ScreenToken(label)
            factsByScreen[token] = UIKitDisplayFacts(key, name, width, height, scale, isMain)
            return token
        }

        fun fire() {
            for (listener in registered.toList()) listener()
        }

        fun listenerCount(): Int = registered.size

        override fun screens(): List<Any> {
            seamReads += 1
            if (NSThread.isMainThread()) mainThreadSeamReads += 1
            return inventory
        }

        override fun isMain(screen: Any): Boolean = factsByScreen[screen]?.isMain ?: false

        override fun factsOf(screen: Any): UIKitDisplayFacts? = factsByScreen[screen]

        override fun registerListener(listener: () -> Unit) {
            registered.add(listener)
        }

        override fun unregisterListener(listener: () -> Unit) {
            registered.remove(listener)
        }

        override fun close() {
            closedCount += 1
        }
    }

    /** Le collector de publications d'un observer — la vérité de CE que le port a livré. */
    private class Collector {
        val received = ArrayList<KadreResult<DisplayPortSnapshot>>()

        val last: KadreResult<DisplayPortSnapshot>? get() = received.lastOrNull()

        fun lastSnapshot(): DisplayPortSnapshot = assertIs<KadreResult.Success<DisplayPortSnapshot>>(received.last()).value

        fun lastFailure(): KadreFailure = assertIs<KadreResult.Failure>(received.last()).reason
    }

    /** Le ruling de production, fixé par le test : l'hôte EST le HostViewport, le reste Physical. */
    private val productionRuling: (Boolean) -> DisplayType =
        { isHostScreen -> if (isHostScreen) DisplayType.HostViewport else DisplayType.Physical }

    private fun portWith(
        source: FakeDisplaySource,
        hostKey: Long? = null,
        ruling: (Boolean) -> DisplayType = productionRuling,
    ): UIKitDisplayPort = UIKitDisplayPort(
        source = source,
        hostScreenResolver = { hostKey?.let { key -> source.factsByScreen.values.firstOrNull { it.key == key } } },
        displayTypeRuling = ruling,
    )

    // MARK: - l'install publie l'inventaire courant SYNCHRONEMENT, une fois

    @Test
    fun installPublishesTheCurrentInventorySynchronouslyExactlyOnce() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true)
        val airplay = source.screen("airplay", key = 2L)
        source.inventory = listOf(host, airplay)
        val port = portWith(source, hostKey = 1L)
        val collector = Collector()

        port.installSnapshotObserver { collector.received.add(it) }

        // La publication initiale est DÉJÀ là, sans aucun fire du seam : l'inventaire n'attend
        // jamais un événement que le système ne livrera peut-être pas (miroir Android :175).
        assertEquals(1, collector.received.size)
        val snapshot = collector.lastSnapshot()
        assertEquals(1L, snapshot.primaryKey, "l'écran hôte de la session est le primary")
        assertEquals(2, snapshot.displays.size, "l'inventaire ENTIER des écrans énumérés")
        assertEquals(listOf(1L, 2L), snapshot.displays.map { it.key }, "les clés des faits, portées telles quelles")
    }

    @Test
    fun enumerationCapabilityIsSupportedFromConstruction() {
        val port = portWith(FakeDisplaySource())
        assertIs<Capability.Supported<*>>(port.enumerationCapability, "le seam notification est structurellement observable")
    }

    @Test
    fun requestSnapshotReturnsTheSameFullInventoryWithoutAnObserver() = runBlocking {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 7L, isMain = true)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 7L)

        val result = port.requestSnapshot()
        val snapshot = assertIs<KadreResult.Success<DisplayPortSnapshot>>(result).value
        assertEquals(7L, snapshot.primaryKey)
        assertEquals(1, snapshot.displays.size)
    }

    /**
     * Le pin du funnel main (revue Task 2, Important-1) — la relecture du seam `UIScreen` est
     * main-confined : appelé DEPUIS le main (le cas de la composition — l'attach et la boucle
     * main des sessions), `requestSnapshot` reste INLINE (zéro saut de file) et la lecture du
     * seam observe le thread principal, exactement une fois par demande.
     *
     * La branche HORS main (l'appelant applicatif d'un `requestAccess` sur un thread arbitraire,
     * funnelé par `Dispatchers.Main.immediate` — le patron du bail d'[UIKitWindowCommandPort.
     * lease]) n'est PAS épinglable ici, mesuré honnêtement : le runner de tests natif exécute
     * les cas SUR le thread principal et ne draine PAS la file principale (sonde Task 5 — un
     * `withContext(Dispatchers.Main)` depuis un Worker n'y est JAMAIS livré) ; l'épingle hors
     * main demande un hôte à run loop — l'affaire du driver (Task 6, stimulus charté), où un
     * appel bloqué hors main est vivant par construction.
     */
    @Test
    fun requestSnapshotReadsTheSeamOnTheMainThreadAndStaysInlineWhenAlreadyThere() = runBlocking {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 1L)

        assertTrue(NSThread.isMainThread(), "pré-requis : le runner natif exécute le test sur le main")
        val snapshot = assertIs<KadreResult.Success<DisplayPortSnapshot>>(port.requestSnapshot()).value
        assertEquals(1, snapshot.displays.size)

        assertEquals(1, source.seamReads, "exactement une relecture du seam par demande")
        assertEquals(
            source.seamReads,
            source.mainThreadSeamReads,
            "toute relecture du seam observe le thread principal (UIScreen est main-only)",
        )
    }

    // MARK: - le ruling injecté : l'écran hôte → ruling(true), les autres → ruling(false)

    @Test
    fun theHostScreenIsRuledThroughTheInjectedRuling() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L)
        val airplay = source.screen("airplay", key = 2L)
        val second = source.screen("second", key = 3L)
        source.inventory = listOf(host, airplay, second)
        val port = portWith(source, hostKey = 2L, ruling = productionRuling)

        val collector = Collector()
        port.installSnapshotObserver { collector.received.add(it) }

        val byKey = collector.lastSnapshot().displays.associateBy { it.key }
        assertEquals(DisplayType.HostViewport, byKey[2L]?.type, "l'écran de LA FENÊTRE de la session est son HostViewport")
        assertEquals(DisplayType.Physical, byKey[1L]?.type, "les écrans énumérés autres sont Physical")
        assertEquals(DisplayType.Physical, byKey[3L]?.type)
    }

    @Test
    fun withoutAResolvableHostScreenTheSeamIsMainFactIsTheLastResort() {
        // Le resolver ne rend rien (écran de fenêtre indisponible) : le fait `isMain` du seam
        // (UIScreen.mainScreen — le fait T1 rendu VIVANT) porte le ruling à sa place.
        val source = FakeDisplaySource()
        val main = source.screen("main", key = 1L, isMain = true)
        val external = source.screen("external", key = 2L, isMain = false)
        source.inventory = listOf(main, external)
        val port = UIKitDisplayPort(
            source = source,
            hostScreenResolver = { null },
            displayTypeRuling = productionRuling,
        )

        val collector = Collector()
        port.installSnapshotObserver { collector.received.add(it) }

        val byKey = collector.lastSnapshot().displays.associateBy { it.key }
        assertEquals(DisplayType.HostViewport, byKey[1L]?.type, "le fait isMain du seam gouverne sans resolver")
        assertEquals(DisplayType.Physical, byKey[2L]?.type)
    }

    // MARK: - un second install remplace le premier (le handle périmé devient un no-op)

    @Test
    fun aSecondInstallReplacesTheFirstAndTheStaleHandleBecomesANoOp() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 1L)

        val first = Collector()
        val firstHandle = port.installSnapshotObserver { first.received.add(it) }
        val second = Collector()
        val secondHandle = port.installSnapshotObserver { second.received.add(it) }

        // Le remplacement publie vers SON observer (la publication initiale du remplaçant).
        assertEquals(1, first.received.size)
        assertEquals(1, second.received.size)
        assertNotEquals(firstHandle, secondHandle)

        // Un fire du seam ne nourrit QUE l'install vivante — jamais la remplacée.
        source.fire()
        assertEquals(1, first.received.size, "l'observer remplacé ne reçoit plus rien")
        assertEquals(2, second.received.size)

        // Le close du handle PÉRIMÉ est un no-op : il ne désenregistre pas l'install vivante.
        firstHandle.close()
        source.fire()
        assertEquals(3, second.received.size, "le fire post-close-du-périmé atteint l'install vivante")
        assertEquals(1, source.listenerCount(), "exactement un listener de seam vivant")
        assertEquals(1, first.received.size, "l'observer remplacé reste muet après le close du handle périmé")

        secondHandle.close()
        assertEquals(0, source.listenerCount(), "le close du handle VIVANT désenregistre le listener du seam")
    }

    // MARK: - chaque fire relit l'inventaire EN ENTIER (jamais un delta)

    @Test
    fun everyFireRereadsTheFullInventoryAsANewSnapshot() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true, width = 1024.0, height = 768.0)
        val external = source.screen("external", key = 2L)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 1L)
        val collector = Collector()
        port.installSnapshotObserver { collector.received.add(it) }

        // Un écran RENTRE : le fire suivant publie le NOUVEL inventaire complet.
        source.inventory = listOf(host, external)
        source.fire()
        assertEquals(2, collector.lastSnapshot().displays.size, "l'arrivée est un inventaire relu, pas un delta")

        // L'écran hôte CHANGE de géométrie : le fire publie la nouvelle mesure, pas l'ancienne.
        source.factsByScreen[host] = UIKitDisplayFacts(1L, "host", 2048.0, 1536.0, 2.0, true)
        source.fire()
        val hostDisplay = collector.lastSnapshot().displays.single { it.key == 1L }
        assertEquals(4096, hostDisplay.bounds.size.width, "la relecture porte la géométrie COURANTE (points × échelle)")
        assertEquals(3072, hostDisplay.bounds.size.height)

        // Un écran SORT : le fire suivant ne le mentionne plus — jamais un état collé.
        source.inventory = listOf(host)
        source.fire()
        assertEquals(listOf(1L), collector.lastSnapshot().displays.map { it.key }, "le départ disparaît du snapshot ENTIER relu")
    }

    // MARK: - une géométrie non portable est un échec TOUT-OU-RIEN (jamais partiel)

    @Test
    fun oneUnmeasurableScreenWithdrawsTheWholeInventoryAsTemporarilyUnavailable() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 1L)
        val collector = Collector()
        port.installSnapshotObserver { collector.received.add(it) }
        assertEquals(1, collector.lastSnapshot().displays.size, "pré-requis : l'inventaire est vivant")

        // L'écran extérieur devient non portable (échelle nulle — le kernel répond null) : le
        // snapshot RÉUSSI est remplacé par un échec qui RETIRE l'inventaire précédent — un
        // snapshot réussi ne porte JAMAIS un inventaire partiel (le risque n°2 du plan).
        val external = source.screen("external", key = 2L, scale = 0.0)
        source.inventory = listOf(host, external)
        source.fire()

        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), collector.lastFailure())
        assertTrue(collector.received.size == 2, "exactement une publication de retrait, pas un succès partiel")
    }

    @Test
    fun allUnmeasurableScreensAndAnEmptyInventoryAreTheSameWithdrawal() = runBlocking {
        val source = FakeDisplaySource()
        val dead = source.screen("dead", key = 1L, width = 0.0, isMain = true)
        source.inventory = listOf(dead)
        val port = portWith(source, hostKey = 1L)

        val failure = assertIs<KadreResult.Failure>(port.requestSnapshot()).reason
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), failure, "tout-non-mesurable : retrait")

        val empty = FakeDisplaySource()
        empty.inventory = emptyList()
        val emptyPort = portWith(empty)
        val emptyFailure = assertIs<KadreResult.Failure>(emptyPort.requestSnapshot()).reason
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), emptyFailure, "inventaire vide : retrait, jamais un succès vide")

        val unknown = FakeDisplaySource()
        val token = ScreenToken("unknown")
        unknown.inventory = listOf(token)
        val unknownPort = portWith(unknown)
        val unreadable = assertIs<KadreResult.Failure>(unknownPort.requestSnapshot()).reason
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), unreadable, "lecture de fait impossible : retrait")
    }

    @Test
    fun aWithdrawalSurvivesUntilANextFireSucceeds() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 1L)
        val collector = Collector()
        port.installSnapshotObserver { collector.received.add(it) }

        // Échec : un écran non mesurable entre.
        val broken = source.screen("broken", key = 2L, height = -1.0)
        source.inventory = listOf(host, broken)
        source.fire()
        assertIs<KadreFailure.TemporarilyUnavailable>(collector.lastFailure())

        // Guérison : le même écran redevient mesurable, le prochain fire republie l'inventaire.
        source.factsByScreen[broken] = UIKitDisplayFacts(2L, "broken", 800.0, 600.0, 1.0, false)
        source.fire()
        assertEquals(2, collector.lastSnapshot().displays.size, "l'inventaire revient ENTIER au premier fire porteur")
    }

    // MARK: - les clés : celles des faits, jamais réallouées (contrat T1)

    @Test
    fun distinctScreensCarryTheirFactsKeysVerbatim() {
        val source = FakeDisplaySource()
        val a = source.screen("a", key = 11L)
        val b = source.screen("b", key = 42L)
        source.inventory = listOf(a, b)
        val port = portWith(source, hostKey = 42L)

        val collector = Collector()
        port.installSnapshotObserver { collector.received.add(it) }

        val snapshot = collector.lastSnapshot()
        assertEquals(listOf(11L, 42L), snapshot.displays.map { it.key }, "le port PORTE les clés des faits — il n'en alloue jamais")
        assertEquals(42L, snapshot.primaryKey, "le primary est l'écran hôte, retrouvé PAR la clé des faits")
    }

    // MARK: - le close : idempotent, possédé, et l'install post-close lève

    @Test
    fun closeIsIdempotentClosesTheOwnedSourceExactlyOnceAndSilencesEverything() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 1L)
        val collector = Collector()
        val handle = port.installSnapshotObserver { collector.received.add(it) }

        port.close()
        port.close()
        assertEquals(1, source.closedCount, "le seam possédé est fermé EXACTEMENT une fois")
        assertEquals(0, source.listenerCount(), "le listener du seam est désenregistré")

        // Un fire après le close ne publie rien (l'observer est oublié).
        source.fire()
        assertEquals(1, collector.received.size)

        // Le handle de l'install reste appelable après le close du port — no-op, pas de throw.
        handle.close()

        // Un install après le close lève (miroir Android : `check(!closed)`).
        val late = runCatching { port.installSnapshotObserver { } }
        assertTrue(late.isFailure, "installer sur un port fermé lève")
    }

    @Test
    fun theStaleInstallHandleDoesNotCloseTheSourceNorTheLiveInstall() {
        val source = FakeDisplaySource()
        val host = source.screen("host", key = 1L, isMain = true)
        source.inventory = listOf(host)
        val port = portWith(source, hostKey = 1L)
        val stale = Collector()
        val staleHandle = port.installSnapshotObserver { stale.received.add(it) }
        val live = Collector()
        port.installSnapshotObserver { live.received.add(it) }

        staleHandle.close()
        assertEquals(0, source.closedCount, "le handle périmé ne ferme JAMAIS le seam possédé")
        assertEquals(1, source.listenerCount(), "le listener de l'install vivante survit au handle périmé")
        source.fire()
        assertEquals(1, stale.received.size, "l'observer périmé reste muet")
        assertEquals(2, live.received.size, "l'install vivante continue de recevoir chaque fire")
    }
}
