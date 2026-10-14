package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.SurfaceId

/**
 * Le port capture UIKit/tvOS (phase 9, Task 2) prouvé PAR JETONS de seam — le port ne nomme
 * JAMAIS un type ReplayKit (le pattern jeton du module, miroir `UIKitDisplayPortTest`) : une
 * source factice pilote la disponibilité, journalise CHAQUE appel de seam et compte ses closes,
 * et ce qui est prouvé est la TRADUCTION du seam en formes port.
 *
 * Les rulings du contrôleur épinglés ici :
 *
 * - **la table des capabilities par cible, les DEUX branches de disponibilité** (ruling 1) :
 *   écran = `Supported(Bgra8, EmbeddedWhenAvailable, région Unsupported)` gated par la
 *   disponibilité LUE (jamais codée en dur — le pin du spike : un Simulator qui répond `false`
 *   reste honnête par la même lecture) ; fenêtre = `Unsupported(CaptureOpen)` (aucune primitive
 *   n'énumère des fenêtres capturables) ; surface = `Supported(Rgba8, Hidden, région
 *   Unsupported)` (le format MESURÉ de la voie renderer, le propre buffer ne compose jamais un
 *   curseur) ; `sourceEnumeration = Unsupported(CaptureRefreshSources)`, `hostPicker =
 *   Unsupported` (JAMAIS `HostPickerOnly`), sources = `Unavailable(même échec)` ;
 * - **requestPermission ne prompt JAMAIS** (ruling 5) : le journal du seam ne porte QUE des
 *   lectures `recorderAvailable` — aucun fait de dialogue n'existe —, le scope Screen rend le
 *   snapshot de disponibilité (répétable, idempotent, VIVANT : un basculement du seam entre
 *   deux demandes se lit), le scope Window est refusé `Unsupported(CapturePermission)` ;
 * - **refreshSources** répond l'échec `Unsupported(CaptureRefreshSources)` (ruling 4) ;
 * - **la cible Source est refusée avant TOUT seam** (ruling 6, précédent AND-009) : le journal
 *   du seam reste VIDE ;
 * - **l'observer** : un seul à la fois, le remplaçant gagne, la livraison sync-once ne part que
 *   lorsque le port a quitté son snapshot initial, le handle périmé et le close sont sans effet
 *   sur l'install vivante ;
 * - **le close** est idempotent et ferme le seam possédé EXACTEMENT une fois ; après lui toute
 *   voie répond `Closed(Host)` et un install lève.
 */
class UIKitCapturePortTest {

    /**
     * Le seam factice : la disponibilité pilotée, le JOURNAL de chaque appel (la matière du pin
     * « ne prompt jamais » — un appel qui ne serait pas une lecture de disponibilité est visible),
     * le compteur de closes (la propriété du port : exactement une fois).
     */
    private class FakeCaptureSource : UIKitCaptureSource {
        var available: Boolean = true
        val calls = ArrayList<String>()
        var closedCount: Int = 0
            private set

        override fun recorderAvailable(): Boolean {
            calls += "recorderAvailable"
            return available
        }

        override fun close() {
            closedCount += 1
        }
    }

    /** La vue jeton du registre — le port ne voit jamais une vraie UIView (pattern jeton). */
    private class ViewToken(val label: String) {
        override fun toString(): String = "View($label)"
    }

    private fun availablePort(source: FakeCaptureSource = FakeCaptureSource()): UIKitCapturePort =
        UIKitCapturePort(source)

    private fun snapshotOf(port: UIKitCapturePort): CapturePortSnapshot = port.initialSnapshot

    // --------------------------------------------------------------------------
    // La table des capabilities par cible — les DEUX branches de disponibilité.
    // --------------------------------------------------------------------------

    @Test
    fun snapshotWithAvailableRecorderAnnouncesTheFullTargetTable() {
        val port = availablePort()

        val snapshot = snapshotOf(port)

        // Permissions « issues de la disponibilité » (ruling 1/5) : l'écran est utilisable sans
        // aucun dialogue (l'état availability EST « rien ne se dresse devant la capture »), la
        // fenêtre n'a aucune permission obtenable (aucune primitive).
        assertEquals(
            CapturePermissionState(
                screen = PermissionState.Granted,
                window = PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            ),
            snapshot.permissions,
        )
        // Écran : ReplayKit documenté BGRA, curseur du système composé dans les frames,
        // région non annoncée (l'affichage entier).
        assertEquals(
            Capability.Supported(
                CaptureTargetConstraints(
                    formats = setOf(PixelFormat.Bgra8),
                    cursorModes = setOf(CaptureCursorMode.EmbeddedWhenAvailable),
                    region = FeatureAvailability.Unsupported,
                ),
                FeatureAvailability.Available,
            ),
            snapshot.capabilities.screen,
        )
        // Fenêtre : aucune primitive d'énumération de fenêtres capturables — Unsupported, pas Unavailable.
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            snapshot.capabilities.window,
        )
        // Surface : la voie renderer MESURÉE Rgba8 (spike décision 4), le propre buffer de la vue
        // ne compose jamais le pointeur système, région non annoncée dans ce squelette (Task 4).
        assertEquals(
            Capability.Supported(
                CaptureTargetConstraints(
                    formats = setOf(PixelFormat.Rgba8),
                    cursorModes = setOf(CaptureCursorMode.Hidden),
                    region = FeatureAvailability.Unsupported,
                ),
                FeatureAvailability.Available,
            ),
            snapshot.capabilities.surface,
        )
        // Énumération et picker : les rulings 4 — JAMAIS HostPickerOnly.
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            snapshot.capabilities.sourceEnumeration,
        )
        assertEquals(FeatureAvailability.Unsupported, snapshot.capabilities.hostPicker)
        // Sources : l'échec de la MÊME opération, porté comme inventaire indisponible.
        assertEquals(
            CapturePortSources.Unavailable(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            snapshot.sources,
        )
    }

    @Test
    fun snapshotWithUnavailableRecorderGatesScreenAvailabilityWithoutTouchingTheStructuralTable() {
        val source = FakeCaptureSource().apply { available = false }
        val port = availablePort(source)

        val snapshot = snapshotOf(port)

        // L'écran : Supported DES CONTRAINTES avec la disponibilité descendue — l'échec est la
        // forme honnête du fait du seam (« le recorder se déclare indisponible », sans raison
        // fabriquée : isAvailable ne porte AUCUN pourquoi), JAMAIS un Simulator codé en dur.
        val screen = assertIs<Capability.Supported<CaptureTargetConstraints>>(snapshot.capabilities.screen)
        assertEquals(
            setOf(PixelFormat.Bgra8),
            screen.constraints.formats,
        )
        assertEquals(
            FeatureAvailability.Unavailable(KadreFailure.TemporarilyUnavailable(retryable = true)),
            screen.availability,
        )
        // La permission écran porte le MÊME fait de disponibilité.
        assertEquals(
            PermissionState.Unavailable(KadreFailure.TemporarilyUnavailable(retryable = true)),
            snapshot.permissions.screen,
        )
        // La table STRUCTURELLE ne bouge pas : les refus de fenêtre/énumération/picker sont des
        // faits de plateforme, pas de disponibilité — ils ne changent pas avec le seam.
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            snapshot.capabilities.window,
        )
        assertEquals(
            CapturePortSources.Unavailable(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            snapshot.sources,
        )
    }

    // ------------------------------------------------------------------
    // requestPermission — ne prompt JAMAIS, répétable, vivant, Window refusé.
    // ------------------------------------------------------------------

    @Test
    fun requestPermissionScreenReturnsTheAvailabilitySnapshotWithoutEverPrompting(): Unit = runBlocking {
        val source = FakeCaptureSource()
        val port = availablePort(source)
        // La construction a DÉJÀ lu la disponibilité une fois (le snapshot initial).
        assertEquals(1, source.calls.size)

        val first = port.requestPermission(CapturePermissionScope.Screen)
        val second = port.requestPermission(CapturePermissionScope.Screen)

        // Deux demandes, DEUX snapshots de disponibilité égaux (répétable, idempotent).
        val firstSnapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(first).value
        val secondSnapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(second).value
        assertEquals(firstSnapshot, secondSnapshot)
        assertEquals(PermissionState.Granted, firstSnapshot.permissions.screen)
        // LE pin du ruling 5 : le journal du seam ne porte QUE des lectures de disponibilité —
        // aucun fait de dialogue n'existe, la demande n'a présenté AUCUNE interface système.
        assertEquals(
            listOf("recorderAvailable", "recorderAvailable", "recorderAvailable"),
            source.calls,
        )
    }

    @Test
    fun requestPermissionScreenReadsAvailabilityLiveEachTime(): Unit = runBlocking {
        val source = FakeCaptureSource()
        val port = availablePort(source)

        val first = assertIs<KadreResult.Success<CapturePortSnapshot>>(
            port.requestPermission(CapturePermissionScope.Screen),
        ).value
        source.available = false
        val second = assertIs<KadreResult.Success<CapturePortSnapshot>>(
            port.requestPermission(CapturePermissionScope.Screen),
        ).value

        // La disponibilité est LUE à chaque demande (jamais gelée à la construction) : un
        // basculement du seam entre deux demandes se lit dans le snapshot rendu.
        assertEquals(PermissionState.Granted, first.permissions.screen)
        assertEquals(
            PermissionState.Unavailable(KadreFailure.TemporarilyUnavailable(retryable = true)),
            second.permissions.screen,
        )
    }

    @Test
    fun requestPermissionWindowIsStructurallyRefusedWithoutTouchingTheSeam(): Unit = runBlocking {
        val source = FakeCaptureSource()
        val port = availablePort(source)
        val readsAtConstruction = source.calls.size

        val refused = port.requestPermission(CapturePermissionScope.Window)

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            refused,
        )
        // Aucun fait de seam DE PLUS — le refus est STRUCTUREL (ruling 5), pas une lecture ratée.
        assertEquals(readsAtConstruction, source.calls.size)
    }

    // ------------------------------------------------------------
    // refreshSources — l'échec Unsupported(CaptureRefreshSources).
    // ------------------------------------------------------------

    @Test
    fun refreshSourcesIsStructurallyRefusedWithTheRefreshFailure(): Unit = runBlocking {
        val source = FakeCaptureSource()
        val port = availablePort(source)
        val readsAtConstruction = source.calls.size

        val refused = port.refreshSources()

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureRefreshSources)),
            refused,
        )
        assertEquals(readsAtConstruction, source.calls.size)
    }

    // ---------------------------------------------------------------
    // reserve — Source refusé avant TOUT seam ; squelette HostChoice/Surface.
    // ---------------------------------------------------------------

    @Test
    fun sourceTargetIsRefusedBeforeAnySeamCall(): Unit = runBlocking {
        val source = FakeCaptureSource()
        val port = availablePort(source)
        val readsAtConstruction = source.calls.size

        val refused = port.reserve(
            CapturePortTarget.Source(
                org.graphiks.kadre.internal.runtime.CapturePortSourceKey("uikit-test", 1L),
            ),
            CaptureRequest(),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            refused,
        )
        // LE pin du ruling 6 (précédent AND-009) : ZÉRO fait de seam pour la demande elle-même —
        // le refus est STRUCTUREL, aucun fait natif n'a été tenté.
        assertEquals(readsAtConstruction, source.calls.size)
    }

    @Test
    fun hostChoiceReserveIsATask3SkeletonRefusal(): Unit = runBlocking {
        val port = availablePort()

        val refused = port.reserve(CapturePortTarget.HostChoice, CaptureRequest())

        // Le squelette Task 2 ne réserve pas encore : le refus honnête est le même que celui du
        // contrat par défaut (Unsupported(CaptureOpen)) — la réservation ReplayKit vit en Task 3.
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            refused,
        )
    }

    @Test
    fun surfaceReserveRefusesAnUnregisteredIdAsInvalidTargetAndARegisteredOneAsTask4Skeleton(): Unit = runBlocking {
        val port = availablePort()
        val registered = RuntimeProcessIds.nextSurfaceId()
        port.registerHostCaptureSurface(registered, ViewToken("primary"))

        val unregistered = port.reserve(CapturePortTarget.Surface(RuntimeProcessIds.nextSurfaceId()), CaptureRequest())
        val registeredResult = port.reserve(CapturePortTarget.Surface(registered), CaptureRequest())

        // Un id que le registre de CETTE session ne nomme pas : la forme du refus de cible
        // irrésoluble (miroir Android — InvalidRequest("request.target")).
        assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("request.target")), unregistered)
        // L'id enregistré atteint le squelette : le refus honnête en attendant la pompe (Task 4).
        assertEquals(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)), registeredResult)
    }

    // -----------------------------------------------------------------
    // Observer — remplacement, sync-once, no-op après close.
    // -----------------------------------------------------------------

    @Test
    fun observerAtInitialSnapshotReceivesNoSyncDeliveryAndReplaceWins(): Unit = runBlocking {
        val port = availablePort()
        val first = ArrayList<KadreResult<CapturePortSnapshot>>()
        val second = ArrayList<KadreResult<CapturePortSnapshot>>()

        val firstHandle = port.installObserver(first::add)
        val secondHandle = port.installObserver(second::add)

        // Un port encore à son snapshot initial ne délivre RIEN : le runtime tient déjà
        // exactement cela de sa lecture de construction.
        assertEquals(emptyList(), first)
        assertEquals(emptyList(), second)

        // La demande Screen produit un snapshot courant : seul l'observer VIVANT (le
        // remplaçant) le reçoit.
        port.requestPermission(CapturePermissionScope.Screen)
        assertEquals(emptyList(), first)
        assertEquals(1, second.size)

        firstHandle.close()
        // Le handle périmé ne désenregistre JAMAIS l'install vivante — l'observateur du
        // remplacement continue de recevoir.
        port.requestPermission(CapturePermissionScope.Screen)
        assertEquals(2, second.size)
        secondHandle.close()
        // Le retrait VIVANT, lui, arrête la livraison.
        port.requestPermission(CapturePermissionScope.Screen)
        assertEquals(2, second.size, "a withdrawn observer receives nothing further")
    }

    @Test
    fun observerInstalledAfterThePortLeftItsInitialSnapshotIsSyncedOnce(): Unit = runBlocking {
        val port = availablePort()
        port.requestPermission(CapturePermissionScope.Screen)

        val received = ArrayList<KadreResult<CapturePortSnapshot>>()
        val handle = port.installObserver(received::add)

        // L'install sur un port DÉJÀ passé son snapshot initial délivre la réponse courante
        // UNE FOIS — l'observateur voit la vérité dès qu'il existe, sans révision dupliquée.
        assertEquals(1, received.size)
        val snapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(received.single()).value
        // Le courant est le snapshot que la demande Screen venait d'accepter (disponibilité
        // inchangée depuis : écran Granted, fenêtre structurellement sans permission).
        assertEquals(PermissionState.Granted, snapshot.permissions.screen)
        assertEquals(
            PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            snapshot.permissions.window,
        )
        handle.close()
    }

    @Test
    fun observerDeliveryAndHandleAreNoOpsAfterClose(): Unit = runBlocking {
        val port = availablePort()
        val received = ArrayList<KadreResult<CapturePortSnapshot>>()
        val handle = port.installObserver(received::add)
        port.requestPermission(CapturePermissionScope.Screen)
        val afterInstall = received.size

        port.close()
        handle.close()

        // Rien n'arrive après le close — le handle périmé comme tout fait ultérieur.
        assertEquals(afterInstall, received.size)
    }

    // ---------------------------------------------------------------------------
    // Close — idempotent, ferme le seam possédé exactement une fois, portes fermées.
    // ---------------------------------------------------------------------------

    @Test
    fun closeIsIdempotentClosesTheOwnedSeamOnceAndBarsEveryPath(): Unit = runBlocking {
        val source = FakeCaptureSource()
        val port = availablePort(source)

        port.close()
        port.close()

        // La propriété du port : le seam possédé est fermé EXACTEMENT une fois.
        assertEquals(1, source.closedCount)

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.requestPermission(CapturePermissionScope.Screen),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.refreshSources(),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.reserve(CapturePortTarget.Source(org.graphiks.kadre.internal.runtime.CapturePortSourceKey("uikit-test", 2L)), CaptureRequest()),
        )
        // Un install après close lève (le miroir des ports uikit — jamais un observateur muet).
        assertFailsWith<IllegalStateException> {
            port.installObserver { }
        }
    }

    // -----------------------------------------------------------------
    // Le registre des surfaces — register/resolve/unregister (miroir Android).
    // -----------------------------------------------------------------

    @Test
    fun surfaceRegistryRegistersResolvesAndUnregistersSessionSurfaces() {
        val registry = UIKitHostCaptureSurfaceRegistry()
        val id = RuntimeProcessIds.nextSurfaceId()
        val view = ViewToken("primary")

        assertNull(registry.resolve(id))
        registry.register(id, view)
        assertSame(view, registry.resolve(id))

        // Réinscrire la MÊME vue (la MÊME instance) sous le même id est absorbé ; une AUTRE
        // vue lève — le pin « enregistrée une fois par port » (miroir exact
        // d'AndroidHostCaptureSurfaceRegistry).
        registry.register(id, view)
        assertSame(view, registry.resolve(id))
        assertFailsWith<IllegalStateException> {
            registry.register(id, ViewToken("other"))
        }

        registry.unregister(id)
        assertNull(registry.resolve(id))
        // Retirer un id absent est un no-op.
        registry.unregister(id)
    }

    @Test
    fun portRegisterDelegatesToItsRegistryAndDiesWithIt(): Unit = runBlocking {
        val port = availablePort()
        val id = RuntimeProcessIds.nextSurfaceId()
        val view = ViewToken("child")

        port.registerHostCaptureSurface(id, view)
        port.unregisterHostCaptureSurface(id)
        // Après retrait, la réservation Surface retombe sur le refus de cible irrésoluble.
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("request.target")),
            port.reserve(CapturePortTarget.Surface(id), CaptureRequest()),
        )
    }
}
