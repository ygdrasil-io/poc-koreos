package org.graphiks.kadre.platform.android

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceId
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Le port de capture Android (phase 8) contre les SEAMS injectés — JAMAIS le SDK (le stub
 * android.jar lance à la construction ; la surfaceView du registre est `Any` pour cela).
 * Les quatre cas du T1, puis les quatre de la Task 2 — chacun le miroir exact du web :
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
 *    JAMAIS la fabrique ;
 * 5. `deniedConsentIsAStatefulSuccess` — le refus utilisateur (`RESULT_CANCELED`) est un
 *    SUCCÈS stateful (`Denied(canRequestAgain = true)`, CONTRACTS :64), publié comme état ;
 *    `open` ensuite est refusé SANS nouveau prompt ; un port fermé répond `Closed(Host)`
 *    AVANT tout fait (web :130, l'item m1) ;
 * 6. `tokenConsumedOnceNoPromptOnReadback` — le grant consommé lance UNE session (configuration
 *    dérivée de la première image AVANT toute frame, strides honests) ; un second `open` sans
 *    nouveau consentement → `PermissionDenied`, JAMAIS le grant stocké ni un second prompt ;
 *    `refreshSources`/les lectures d'état ne lancent JAMAIS le launcher (compteur launch) ;
 *    le close du stream → `Stopped(Requested)` exactement une fois, FGS/display/projection
 *    libérés au terminal ;
 * 7. `lateRegistryRegistrationIsLegal` — le registre DIRECT (SANS LifecycleOwner) appelé
 *    après RESUMED simulé ne throw pas — l'anti-pattern kadre-old épinglé :
 *    `ComponentActivity.registerForActivityResult` THROWS après STARTED, la forme trois-
 *    arguments du registre ne consulte aucun état lifecycle ; la clé est créée par jeton de
 *    session et désenregistrée à la consommation du résultat ;
 * 8. `serviceStartedBeforeVirtualDisplayAndStoppedAtTerminal` — le recorder FGS : le
 *    `startForeground(type=mediaProjection)` précède `createVirtualDisplay` (la responsabilité
 *    Kadre, ruling P8-0), le jeton du FGS EST la clé du consentement, et le terminal arrête le
 *    service — l'arrêt propre ET la révocation `onStop` (`Stopped(PermissionRevoked)`).
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
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

    // -- (5) le refus du consentement est un succès stateful — jamais une failure -----------------------

    @Test
    fun deniedConsentIsAStatefulSuccess() = runTest {
        val events = mutableListOf<String>()
        val consent = FakeConsentSource()
        val port = capturedPort(
            consent,
            FakeProjectionSource(events),
            FakeImageReaderSource(),
            FakeForeground(events),
        )
        val received = mutableListOf<KadreResult<CapturePortSnapshot>>()
        port.installObserver { received += it }

        val request = async { port.requestPermission(CapturePermissionScope.Screen) }
        testScheduler.runCurrent()
        assertEquals(1, consent.launches.size, "la demande lance le dialogue exactement une fois")

        // Le refus utilisateur : RESULT_CANCELED, sans données.
        consent.refuse()
        val snapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(
            request.await(),
        ).value
        assertEquals(
            CapturePermissionState(PermissionState.Denied(canRequestAgain = true), PermissionState.Denied(canRequestAgain = true)),
            snapshot.permissions,
            "un refus est un état — les DEUX scopes le miroient (le miroir web)",
        )
        assertEquals(1, received.size, "le refus fut PUBLIÉ comme état, pas seulement retourné")
        val publishedSnapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(received.single()).value
        val published = assertIs<PermissionState.Denied>(publishedSnapshot.permissions.screen)
        assertTrue(published.canRequestAgain, "l'utilisateur peut redemander après un refus")

        // `open` ensuite : le refus state — jamais un second prompt, jamais une failure de refus.
        assertEquals(
            KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen)),
            port.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
            "sans grant, la réservation HostChoice est refusée en permission — elle ne re-prompt jamais",
        )
        assertEquals(1, consent.launches.size, "le refus state : la réservation ne relance AUCUN dialogue")

        // L'item m1 (carry de la Task 1) : la garde Closed(Host) précède tout fait — le miroir web :130.
        port.close()
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.requestPermission(CapturePermissionScope.Screen),
        )
        assertEquals(1, consent.launches.size, "un port fermé ne lance jamais le dialogue")
    }

    // -- (6) le jeton est single-use, et aucune lecture d'état ne re-prompt ------------------------------

    @Test
    fun tokenConsumedOnceNoPromptOnReadback() = runTest {
        val events = mutableListOf<String>()
        val consent = FakeConsentSource()
        val projections = FakeProjectionSource(events)
        val readers = FakeImageReaderSource()
        val foreground = FakeForeground(events)
        val port = capturedPort(consent, projections, readers, foreground)

        grantedConsent(port, consent, expectedLaunchCount = 1)

        val listener = RecordingListener()
        val reservation = assertIs<KadreResult.Success<CapturePortReservation>>(
            port.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
        ).value
        assertEquals("android-host-choice", reservation.source.key.namespace)
        assertEquals(1, consent.launches.size, "réserver NE relance JAMAIS le dialogue")

        val start = startStream(reservation, listener, readers)
        assertEquals(
            CaptureConfiguration(
                revision = CaptureConfigurationRevision(0L),
                size = PhysicalSize(1080, 2400),
                format = PixelFormat.Rgba8,
                colorEncoding = ColorEncoding(
                    ColorPrimaries.Unknown,
                    TransferFunction.Unknown,
                    MatrixCoefficients.Unknown,
                    ColorRange.Unknown,
                    HdrMetadata.Unknown,
                ),
                alphaMode = AlphaMode.Unknown,
                orientation = CaptureOrientation.Upright,
                cadence = CaptureCadence.Unknown,
                region = null,
                cursorMode = CaptureCursorMode.EmbeddedWhenAvailable,
            ),
            start.configuration,
            "la configuration complète dérivée de la PREMIÈRE image, révision 0 (web Pump :111-196)",
        )
        advanceUntilIdle()
        assertEquals(listOf("frame"), listener.events, "la première frame suit le start — JAMAIS un callback avant")
        val first = listener.frames.single()
        assertEquals(0L, first.configurationRevision)
        assertEquals(4320, first.planes.single().layout.rowStride, "le stride serré de l'image")
        assertEquals(2400 * 4320, first.planes.single().layout.byteCount)
        assertEquals(1.milliseconds, first.sourceTimestamp, "le timestamp SDK honest")

        // Le stride honest du SDK : un device-aligné Pad se rapporte TEL QUEL, jamais resserré.
        readers.reader?.stage(rowStride = 1080 * 4 + 64)
        readers.reader?.fireAvailable()
        advanceUntilIdle()
        assertEquals(listOf("frame", "frame"), listener.events)
        val padded = listener.frames.last().planes.single().layout
        assertEquals(1080 * 4 + 64, padded.rowStride, "le rowStride device-aligné du SDK est rapporté TEL QUEL")
        assertEquals((2400 - 1) * (1080 * 4 + 64) + 1080 * 4, padded.byteCount, "la dernière ligne serrée seulement")

        // Le jeton est consommé : un second open sans nouveau consentement → PermissionDenied,
        // JAMAIS le grant stocké, JAMAIS un second prompt.
        assertEquals(
            KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen)),
            port.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
        )
        assertEquals(1, consent.launches.size, "un second open ne re-prompt JAMAIS")

        // Les lectures d'état ne lancent JAMAIS le launcher (compteur launch).
        assertIs<KadreResult.Success<CapturePortSnapshot>>(port.refreshSources())
        assertEquals(1, consent.launches.size, "refreshSources ne re-prompt JAMAIS")

        // Une demande pendant la session : l'interaction est consommée — pas le grant stocké.
        val second = async { port.requestPermission(CapturePermissionScope.Screen) }
        advanceUntilIdle()
        assertEquals(
            KadreResult.Failure(KadreFailure.InteractionRequired(InteractionFailureReason.Consumed)),
            second.await(),
            "une demande pendant une session vivante est une Interaction, jamais le grant stocké",
        )
        assertEquals(1, consent.launches.size)

        // Le terminal de l'arrêt propre : `Stopped(Requested)` exactement une fois, tout libéré.
        start.stream.close()
        assertEquals(listOf("terminated"), listener.events.takeLast(1))
        assertEquals(1, listener.terminations.size, "le terminal exactement une fois")
        assertEquals(
            CaptureOutcome.Stopped(CaptureStopReason.Requested),
            (listener.terminations.single() as CapturePortTermination.Outcome).outcome,
        )
        assertEquals(1, readers.reader?.closeCount)
        assertEquals(1, projections.lastPending?.stopCount)
        assertEquals(1, foreground.stopCount, "le FGS s'arrête au terminal (ruling P8-0)")
        assertEquals(
            listOf("releaseVirtualDisplay", "projectionStop", "stopService"),
            events.takeLast(3),
            "l'ordre de release kadre-old : display → reader → projection → FGS",
        )
    }

    // -- (7) le registre direct, sans LifecycleOwner, est légal après RESUMED -----------------------------

    @Test
    fun lateRegistryRegistrationIsLegal() = runTest {
        val events = mutableListOf<String>()
        val consent = FakeConsentSource()
        val port = capturedPort(
            consent,
            FakeProjectionSource(events),
            FakeImageReaderSource(),
            FakeForeground(events),
        )

        // La demande arrive APRÈS le simulated RESUMED (toute la couture est déjà attachée) :
        // le registre DIRECT — la forme trois-arguments, sans LifecycleOwner — ne consulte
        // aucun état lifecycle et ne throw pas. L'anti-pattern kadre-old épinglé :
        // `ComponentActivity.registerForActivityResult` THROWS après STARTED ; le point exact
        // de la correction est le registre direct du KDoc d'AndroidActivityCaptureConsentSource.
        val request = async { port.requestPermission(CapturePermissionScope.Screen) }
        testScheduler.runCurrent()
        val registration = consent.registrations.single()
        assertNull(registration.owner, "le registre direct : AUCUN LifecycleOwner ne traverse le consentement")
        assertTrue(
            registration.key.startsWith("kadre-capture-"),
            "la clé est créée par jeton de session — jamais réutilisée",
        )
        consent.grant()
        assertIs<KadreResult.Success<CapturePortSnapshot>>(request.await())
        assertEquals(1, consent.unregisterCount, "le launcher est consommé avec le jeton : la clé est libérée")

        // Une demande pendant qu'un grant pèse : JAMAIS un second enregistrement ni un second
        // dialogue — l'interaction est refusée, le grant stocké n'est jamais re-servi.
        val second = async { port.requestPermission(CapturePermissionScope.Screen) }
        advanceUntilIdle()
        assertIs<KadreResult.Failure>(second.await())
        assertEquals(1, consent.registrations.size, "une demande busy n'enregistre jamais un second launcher")
        assertEquals(1, consent.launches.size, "une demande busy ne lance jamais un second dialogue")
    }

    // -- (8) le FGS Kadre : startForeground AVANT createVirtualDisplay, stop au terminal ------------------

    @Test
    fun serviceStartedBeforeVirtualDisplayAndStoppedAtTerminal() = runTest {
        val events = mutableListOf<String>()
        val consent = FakeConsentSource()
        val projections = FakeProjectionSource(events)
        val readers = FakeImageReaderSource()
        val foreground = FakeForeground(events)
        val port = capturedPort(consent, projections, readers, foreground)

        grantedConsent(port, consent, expectedLaunchCount = 1)
        val listener = RecordingListener()
        val reservation = assertIs<KadreResult.Success<CapturePortReservation>>(
            port.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
        ).value
        val start = startStream(reservation, listener, readers)

        // L'ordre P8-0 : le startForeground(type=mediaProjection) du service Kadre précède le
        // createVirtualDisplay — la responsabilité de la bibliothèque, pas celle du host.
        val foregroundAt = events.indexOf("startForeground(type=mediaProjection)")
        val displayAt = events.indexOf("createVirtualDisplay")
        assertTrue(foregroundAt in 0 until displayAt, "startForeground précède createVirtualDisplay — l'ordre: $events")
        // Le jeton du FGS EST la clé du consentement — un seul jeton de session traverse.
        assertEquals(consent.registrations.single().key, foreground.startedTokens.single())

        // Terminal 1 — l'arrêt propre : le service s'arrête exactement une fois.
        start.stream.close()
        reservation.close() // le runtime teardropperait la réservation — le créneau de session se libère
        val stopAt = events.indexOf("stopService")
        assertTrue(stopAt > displayAt, "le stopService du terminal suit le display: $events")
        assertEquals(1, foreground.stopCount)

        // Terminal 2 — la révocation `MediaProjection.Callback.onStop` : un nouveau consentement
        // (l'ancien jeton est consommé), puis onStop système → `Stopped(PermissionRevoked)`, FGS arrêté.
        grantedConsent(port, consent, expectedLaunchCount = 2)
        val secondReservation = assertIs<KadreResult.Success<CapturePortReservation>>(
            port.reserve(CapturePortTarget.HostChoice, CaptureRequest()),
        ).value
        val secondStart = startStream(secondReservation, listener, readers)
        advanceUntilIdle()
        listener.terminations.clear()
        projections.lastPending?.systemRevokes()
        assertEquals(1, listener.terminations.size, "la révocation termine exactement une fois")
        assertEquals(
            CaptureOutcome.Stopped(CaptureStopReason.PermissionRevoked),
            (listener.terminations.single() as CapturePortTermination.Outcome).outcome,
        )
        assertEquals(2, foreground.stopCount, "la révocation arrête le FGS au terminal aussi")
        assertEquals(
            "terminated",
            listener.events.last(),
            "aucune frame ne suit le terminal — la pompe est scellée",
        )
        secondStart.stream.close()
        assertEquals(1, listener.terminations.size, "un close après le terminal ne raconte plus rien")
    }

    // -- (l'item m3) le backstop API 26 du bras Surface, éprouvé au port ---------------------------------

    @Test
    fun surfaceBackstopBelowApi26RefusesEvenWhenRegistered() = runBlocking {
        val registry = AndroidHostCaptureSurfaceRegistry()
        val port = AndroidCapturePort(apiLevel = 25, surfaces = registry)
        val id = RuntimeProcessIds.nextSurfaceId()
        port.registerHostCaptureSurface(id, Any())
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            port.reserve(CapturePortTarget.Surface(id), CaptureRequest()),
            "sous l'API 26, la surface enregistrée elle-même atteint le backstop — le même verdict que la capability gelée",
        )
    }

    // -- les doubles et les coutures de pilotage ----------------------------------------------------------

    /** Le budget de frame des tests — une frame entière + le pool tiennent largement. */
    private val streamBudget: Long = 64L * 1024L * 1024L

    /** Le port piloté par les doubles, la pompe sur le scheduler du runTest. */
    private fun TestScope.capturedPort(
        consent: FakeConsentSource,
        projections: FakeProjectionSource,
        readers: FakeImageReaderSource,
        foreground: FakeForeground,
    ): AndroidCapturePort = AndroidCapturePort(
        apiLevel = 34,
        surfaces = AndroidHostCaptureSurfaceRegistry(),
        consent = consent,
        projections = projections,
        imageReaders = readers,
        foreground = foreground,
        displayFacts = AndroidCaptureDisplayFacts(PhysicalSize(1080, 2400), 420),
        loopContext = StandardTestDispatcher(testScheduler),
    )

    /** Un consentement ACCORDÉ — le dialogue lancé de plus en plus (compteur cumulé), le grant consommé en pending. */
    private suspend fun TestScope.grantedConsent(
        port: AndroidCapturePort,
        consent: FakeConsentSource,
        expectedLaunchCount: Int,
    ) {
        val request = async { port.requestPermission(CapturePermissionScope.Screen) }
        testScheduler.runCurrent()
        assertEquals(expectedLaunchCount, consent.launches.size)
        consent.grant()
        val snapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(request.await()).value
        assertEquals(
            CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
            snapshot.permissions,
        )
    }

    /** Le démarrage du flux : la pompe suspend sur la première image, le test la stage et la lâche. */
    private suspend fun TestScope.startStream(
        reservation: CapturePortReservation,
        listener: RecordingListener,
        readers: FakeImageReaderSource,
    ): CapturePortStreamStart {
        val started = async { reservation.start(listener, streamBudget) }
        testScheduler.runCurrent() // la pompe a créé reader + display et attend la première image
        val reader = readers.reader ?: error("l'ImageReader n'a jamais été créé")
        reader.stage(timestampNanos = 1_000_000L)
        reader.fireAvailable()
        return assertIs<KadreResult.Success<CapturePortStreamStart>>(started.await()).value
    }
}

/** Le double du consentement : la clé, le propriétaire (toujours null), les lancements comptés. */
private class FakeConsentSource : AndroidCaptureConsentSource {
    data class Registration(
        val key: String,
        val owner: LifecycleOwner?,
    )

    val registrations = mutableListOf<Registration>()
    val launches = mutableListOf<Any>()
    var unregisterCount = 0
        private set
    var onResult: ((resultCode: Int, data: Any?) -> Unit)? = null
        private set

    override fun createScreenCaptureIntent(): Any = Any()

    override fun register(
        key: String,
        owner: LifecycleOwner?,
        onResult: (resultCode: Int, data: Any?) -> Unit,
    ): AndroidCaptureConsentLauncher {
        registrations += Registration(key, owner)
        this.onResult = onResult
        return object : AndroidCaptureConsentLauncher {
            override fun launch(intent: Any) {
                launches += intent
            }

            override fun close() {
                unregisterCount += 1
            }
        }
    }

    /** `RESULT_OK` + des données non nulles — le grant. */
    fun grant(resultCode: Int = -1, data: Any? = Any()) = onResult?.invoke(resultCode, data)

    /** `RESULT_CANCELED`, sans données — le refus utilisateur. */
    fun refuse() = onResult?.invoke(0, null)
}

/** Le double projection : les pendings pilotables, les faits d'affichage sur le recorder partagé. */
private class FakeProjectionSource(
    private val events: MutableList<String>,
) : AndroidCaptureProjectionSource {
    var lastPending: FakePendingProjection? = null
        private set

    override fun createPending(resultCode: Int, data: Any?): FakePendingProjection =
        FakePendingProjection(events).also { lastPending = it }

    override fun close() {}
}

private class FakePendingProjection(
    private val events: MutableList<String>,
) : AndroidCapturePendingProjection {
    var revokedHandler: (() -> Unit)? = null
        private set
    var stopCount = 0
        private set
    private var revokedState = false

    override val revoked: Boolean
        get() = revokedState

    override fun onRevoked(handler: (() -> Unit)?) {
        revokedHandler = handler
    }

    override fun createVirtualDisplay(name: String, width: Int, height: Int, dpi: Int, surface: Any): Any {
        events += "createVirtualDisplay"
        return Any()
    }

    override fun releaseVirtualDisplay(display: Any) {
        events += "releaseVirtualDisplay"
    }

    override fun stop() {
        stopCount += 1
        events += "projectionStop"
    }

    override fun close() = stop()

    /** La révocation système — `MediaProjection.Callback.onStop` qui n'est pas la nôtre. */
    fun systemRevokes() {
        revokedState = true
        revokedHandler?.invoke()
    }
}

/** Le double ImageReader : les specs créées comptées, les images staged l'une après l'autre. */
private class FakeImageReaderSource : AndroidCaptureImageReaderSource {
    var reader: FakeImageReader? = null
        private set
    val specs = mutableListOf<Triple<Int, Int, Int>>()

    override fun createReader(width: Int, height: Int, maxImages: Int): FakeImageReader =
        FakeImageReader(width, height).also {
            reader = it
            specs += Triple(width, height, maxImages)
        }

    override fun close() {}
}

private class FakeImageReader(
    private val defaultWidth: Int,
    private val defaultHeight: Int,
) : AndroidCaptureImageReader {
    var availableListener: (() -> Unit)? = null
        private set
    var closeCount = 0
        private set
    private val staged = ArrayDeque<FakeImage>()

    override val surface: Any = Any()

    override fun setOnImageAvailable(listener: () -> Unit) {
        availableListener = listener
    }

    override fun acquireLatest(): AndroidCaptureImage? = staged.removeFirstOrNull()

    override fun close() {
        closeCount += 1
    }

    fun stage(
        width: Int = defaultWidth,
        height: Int = defaultHeight,
        rowStride: Int = width * 4,
        timestampNanos: Long = 0L,
    ): FakeImage = FakeImage(width, height, rowStride, timestampNanos).also { staged += it }

    fun fireAvailable() = availableListener?.invoke()
}

private class FakeImage(
    override val width: Int,
    override val height: Int,
    val rowStride: Int,
    override val timestampNanos: Long,
) : AndroidCaptureImage {
    var closeCount = 0
        private set

    override val planes = listOf(AndroidCaptureImagePlane(rowStride = rowStride, pixelStride = 4))

    override fun readPlane(index: Int, byteCount: Int): ByteArray = ByteArray(byteCount)

    override fun close() {
        closeCount += 1
    }
}

/** Le double FGS : l'ordre sur le recorder partagé, les jetons et les stops comptés. */
private class FakeForeground(
    private val events: MutableList<String>,
) : AndroidCaptureForeground {
    val startedTokens = mutableListOf<String>()
    var stopCount = 0
        private set

    override suspend fun start(token: String) {
        startedTokens += token
        events += "startForeground(type=mediaProjection)"
    }

    override fun stop() {
        stopCount += 1
        events += "stopService"
    }

    override fun close() = stop()
}

/** Le listener enregistreur : l'ordre exact des callbacks, ce que les invariants mutent contre. */
private class RecordingListener : CapturePortStreamListener {
    val events = mutableListOf<String>()
    val frames = mutableListOf<CapturePortFrame>()
    val reconfigured = mutableListOf<CaptureConfiguration>()
    val terminations = mutableListOf<CapturePortTermination>()

    override fun onFrame(frame: CapturePortFrame) {
        events += "frame"
        frames += frame
    }

    override fun onReconfigured(configuration: CaptureConfiguration) {
        events += "reconfigured"
        reconfigured += configuration
    }

    override fun onTerminated(termination: CapturePortTermination) {
        events += "terminated"
        terminations += termination
    }
}
