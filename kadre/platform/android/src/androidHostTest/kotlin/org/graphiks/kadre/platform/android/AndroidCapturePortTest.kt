package org.graphiks.kadre.platform.android

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Le squelette du port de capture Android (Task 1, phase 8) contre les SEAMS injectés — JAMAIS
 * le SDK (le stub android.jar lance à la construction ; la surfaceView du registre est `Any`
 * pour cela). Les quatre cas du T1, chacun le miroir exact de `WebCapturePortTest` :
 *
 * 1. `capabilitiesFreezeAtConstruction` — la vérité des capabilities sondée UNE FOIS à la
 *    construction et gelée : screen/window `Supported` (un consentement MediaProjection — la
 *    fenêtre reflète l'écran mot pour mot, le miroir web), surface `Supported` à l'API 26+
 *    (PixelCopy du SurfaceView) et refusée en dessous (la garde P4 `Unsupported(CaptureOpen)`),
 *    `sourceEnumeration`/`hostPicker` dans la forme exacte du web (l'inventaire EST le picker) ;
 * 2. `sourcesAreHostPickerOnlyAndRefreshIsNoop` — sources TOUJOURS `HostPickerOnly` (MediaProjection
 *    n'énumère rien avant le consentement), `refreshSources()` est le no-op honnête du web :
 *    le MÊME objet snapshot, les capabilities gelées jamais re-sondées, rien de republié ;
 * 3. `sourceTargetRefusedBeforeAnySeam` — `reserve(Source(...))` → `Unsupported(CaptureOpen)`
 *    AVANT tout seam, zéro interaction avec le registre (miroir web :176) ;
 * 4. `surfaceTargetResolvesThroughTheRegistry` — l'id enregistré par l'attach atteint la
 *    fabrique PixelCopy (le seam injecté, bras stub de la Task 3) avec la surfaceView attachée ;
 *    l'id étranger → `InvalidRequest("request.target")` (miroir web :216-219) et n'atteint
 *    JAMAIS la fabrique.
 */
class AndroidCapturePortTest {

    /** Le double du registre : une map pilotable qui enregistre chaque interaction. */
    private class RecordingRegistry : AndroidCaptureSurfaceRegistry {
        val interactions = mutableListOf<String>()
        private val surfaces = HashMap<SurfaceId, Any>()

        override fun register(id: SurfaceId, surfaceView: Any) {
            interactions += "register"
            surfaces[id] = surfaceView
        }

        override fun unregister(id: SurfaceId) {
            interactions += "unregister"
            surfaces.remove(id)
        }

        override fun resolve(id: SurfaceId): Any? {
            interactions += "resolve"
            return surfaces[id]
        }
    }

    // -- (1) les capabilities gelées : une sonde, une fois, à la construction ---------------------------

    @Test
    fun capabilitiesFreezeAtConstruction() {
        val port = AndroidCapturePort(apiLevel = 34, surfaces = AndroidHostCaptureSurfaceRegistry())
        val snapshot = port.initialSnapshot

        assertIs<CapturePortSources.HostPickerOnly>(snapshot.sources)
        assertEquals(
            CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined),
            snapshot.permissions,
            "aucune lecture arrière d'un consentement MediaProjection — la demande (Task 2) répondra",
        )

        // La forme EXACTE du web : l'inventaire est le picker, et la capability d'énumération le
        // dit quoi qu'il arrive d'aucune autre primitive.
        assertEquals(
            Capability.Supported(Unit, FeatureAvailability.Available),
            snapshot.capabilities.sourceEnumeration,
        )
        assertEquals(FeatureAvailability.Available, snapshot.capabilities.hostPicker)

        val screen = assertIs<Capability.Supported<CaptureTargetConstraints>>(snapshot.capabilities.screen)
        assertEquals(setOf(PixelFormat.Rgba8), screen.constraints.formats, "l'ImageReader RGBA_8888, rien d'autre")
        assertEquals(
            setOf(CaptureCursorMode.EmbeddedWhenAvailable),
            screen.constraints.cursorModes,
            "MediaProjection ne commande pas le pointeur : le système le compose quand un device existe",
        )
        assertEquals(
            FeatureAvailability.Unsupported,
            screen.constraints.region,
            "MediaProjection capture l'affichage entier — la région n'est pas annoncée du tout",
        )
        assertEquals(FeatureAvailability.Available, screen.availability)
        assertEquals(
            snapshot.capabilities.screen,
            snapshot.capabilities.window,
            "le capability window reflète le capability screen exactement (un consentement, ce que le picker offre)",
        )

        val surface = assertIs<Capability.Supported<CaptureTargetConstraints>>(snapshot.capabilities.surface)
        assertEquals(setOf(PixelFormat.Rgba8), surface.constraints.formats)
        assertEquals(
            setOf(CaptureCursorMode.Hidden),
            surface.constraints.cursorModes,
            "le PixelCopy du propre buffer de la SurfaceView ne compose jamais le pointeur système",
        )
        assertEquals(FeatureAvailability.Unsupported, surface.constraints.region)
        assertEquals(FeatureAvailability.Available, surface.availability)

        // Sous l'API 26 : `PixelCopy.request(SurfaceView, ...)` n'existe pas — le champ est refusé
        // champ par champ (la garde P4), pendant que screen/window restent entiers.
        val legacy = AndroidCapturePort(apiLevel = 25, surfaces = AndroidHostCaptureSurfaceRegistry())
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            legacy.initialSnapshot.capabilities.surface,
        )
        assertIs<Capability.Supported<CaptureTargetConstraints>>(legacy.initialSnapshot.capabilities.screen)
        assertEquals(FeatureAvailability.Available, legacy.initialSnapshot.capabilities.hostPicker)
    }

    // -- (2) HostPickerOnly, et le no-op honnête --------------------------------------------------------

    @Test
    fun sourcesAreHostPickerOnlyAndRefreshIsNoop() = runBlocking {
        val port = AndroidCapturePort(apiLevel = 34, surfaces = AndroidHostCaptureSurfaceRegistry())
        assertIs<CapturePortSources.HostPickerOnly>(port.initialSnapshot.sources)

        val refreshed = assertIs<KadreResult.Success<CapturePortSnapshot>>(port.refreshSources()).value
        assertSame(port.initialSnapshot, refreshed, "le no-op honnête rend le MÊME objet snapshot")
        assertSame(
            port.initialSnapshot.capabilities,
            refreshed.capabilities,
            "les capabilities gelées à la construction ne sont jamais re-sondées",
        )

        // Rien n'a bougé : le port reste à son instantané initial et l'observateur ne reçoit rien —
        // le skeleton n'a AUCUN fait qui republie (le consentement de la Task 2 en apportera).
        val received = mutableListOf<KadreResult<CapturePortSnapshot>>()
        port.installObserver { received += it }
        assertTrue(received.isEmpty(), "un port encore à son instantané initial ne délivre rien")
        assertSame(
            refreshed,
            assertIs<KadreResult.Success<CapturePortSnapshot>>(port.refreshSources()).value,
            "le refresh suivant répond encore le même objet, sans effet",
        )
    }

    // -- (3) Source refusé avant tout seam --------------------------------------------------------------

    @Test
    fun sourceTargetRefusedBeforeAnySeam() = runBlocking {
        val registry = RecordingRegistry()
        val port = AndroidCapturePort(apiLevel = 34, surfaces = registry)
        val atConstruction = registry.interactions.toList()

        val result = port.reserve(
            CapturePortTarget.Source(CapturePortSourceKey("android-test", 0L)),
            CaptureRequest(),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            result,
            "l'inventaire Android n'existe pas avant le consentement : le refus est structurel",
        )
        assertEquals(
            atConstruction,
            registry.interactions,
            "le refus Source est structurel : zéro interaction avec le registre au-delà de la construction",
        )
    }

    // -- (4) Surface résolu par le registre, id étranger refusé -----------------------------------------

    @Test
    fun surfaceTargetResolvesThroughTheRegistry() = runBlocking {
        val registry = AndroidHostCaptureSurfaceRegistry()
        val handedToFactory = mutableListOf<Any>()
        val port = AndroidCapturePort(
            apiLevel = 34,
            surfaces = registry,
            pixelCopyStreamFactory = AndroidPixelCopyStreamFactory { surfaceView, _ ->
                handedToFactory += surfaceView
                KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureCollectFrames))
            },
        )
        val attachedSurface = Any()
        val id = RuntimeProcessIds.nextSurfaceId()
        port.registerHostCaptureSurface(id, attachedSurface)

        val result = port.reserve(CapturePortTarget.Surface(id), CaptureRequest())
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureCollectFrames)),
            result,
            "l'échec factice de la fabrique traverse : l'id enregistré atteint le bras PixelCopy (Task 3)",
        )
        assertEquals(1, handedToFactory.size, "l'id enregistré atteint la fabrique exactement une fois")
        assertSame(attachedSurface, handedToFactory.single(), "la fabrique reçoit LA surfaceView attachée")

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("request.target")),
            port.reserve(CapturePortTarget.Surface(RuntimeProcessIds.nextSurfaceId()), CaptureRequest()),
            "un id étranger ne nomme aucune surface que ce port capture honnêtement",
        )
        assertEquals(1, handedToFactory.size, "l'id étranger n'atteint JAMAIS la fabrique")
    }
}
