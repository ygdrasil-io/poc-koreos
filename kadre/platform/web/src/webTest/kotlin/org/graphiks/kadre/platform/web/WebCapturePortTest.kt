package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The control plane of the web capture port, driven over a recording seam double: every browser
 * fact is staged, every seam touch is recorded, and the invariant the gate demands — no consent
 * machinery runs before the one explicit request — is pinned by the recording itself (a single
 * `pickDisplayMedia` entry outside that path fails these tests). The capability truth table is
 * asserted branch by branch, each staged precondition standing for its documented cause.
 */
class WebCapturePortTest {
    /** A track double counting the two release effects the discard flow must perform exactly once. */
    private class RecordingTrack : WebDomVideoTrack {
        var stopCount = 0
            private set
        var closeCount = 0
            private set

        override fun stop() {
            stopCount += 1
        }

        override fun close() {
            closeCount += 1
        }

        override fun addEndedListener(listener: () -> Unit) {
            // No discard-flow test hears an end; the streaming tests script their own track double.
        }
    }

    private class RecordingProcessorFactory : WebTrackProcessorFactory {
        override fun processorFor(track: WebDomVideoTrack): WebFrameReadable = object : WebFrameReadable {
            override suspend fun read(): WebFrameRead = error("no frame is scripted in the control-plane tests")
            override fun close() = Unit
        }
    }

    /**
     * The seam double. [interactions] records every member the port touched, in order — the
     * before-any-picker gate and the no-prompt readback invariant are assertions over this list.
     */
    private class RecordingCaptureDom(
        var secureContext: Boolean = true,
        var queryResult: WebCapturePermissionQueryResult? = WebCapturePermissionQueryResult.NotDetermined,
        var hasDisplayMedia: Boolean = true,
        var processor: WebTrackProcessorFactory? = RecordingProcessorFactory(),
        var canvas: WebDomCanvas? = null,
    ) : WebCaptureDom {
        val interactions = mutableListOf<String>()
        var pickCalls = 0
            private set
        val recordedHints = mutableListOf<Pair<String?, Double?>>()
        val pickResults = ArrayDeque<WebDisplayMediaPick>()
        var readbackListener: ((WebCapturePermissionQueryResult?) -> Unit)? = null
            private set
        var readbackRegistrations = 0
            private set
        var closeCount = 0
            private set

        fun fireReadback(answer: WebCapturePermissionQueryResult?) {
            queryResult = answer
            readbackListener?.invoke(answer)
        }

        override fun isSecureContext(): Boolean {
            interactions += "isSecureContext"
            return secureContext
        }

        override fun queryDisplayCapturePermission(): WebCapturePermissionQueryResult? {
            interactions += "queryDisplayCapturePermission"
            return queryResult
        }

        override fun readDisplayCapturePermission(listener: (WebCapturePermissionQueryResult?) -> Unit) {
            interactions += "readDisplayCapturePermission"
            readbackRegistrations += 1
            readbackListener = listener
        }

        override fun hasDisplayMedia(): Boolean {
            interactions += "hasDisplayMedia"
            return hasDisplayMedia
        }

        override suspend fun pickDisplayMedia(cursorHint: String?, frameRateHint: Double?): WebDisplayMediaPick {
            interactions += "pickDisplayMedia"
            pickCalls += 1
            recordedHints += cursorHint to frameRateHint
            return pickResults.removeFirstOrNull() ?: error("no staged pick result")
        }

        override fun processorFactory(): WebTrackProcessorFactory? {
            interactions += "processorFactory"
            return processor
        }

        override fun canvasForSurface(): WebDomCanvas? {
            interactions += "canvasForSurface"
            return canvas
        }

        override fun close() {
            closeCount += 1
        }
    }

    // -- (a) the initial snapshot: permissions from the readback, capabilities from the probe -------------

    @Test
    fun initialSnapshotCarriesTheProbedCapabilitiesAndTheReadbackPermissions() = runTest {
        val dom = RecordingCaptureDom(queryResult = WebCapturePermissionQueryResult.Granted)
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = true)

        val snapshot = port.initialSnapshot
        assertEquals(
            CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
            snapshot.permissions,
            "the window permission mirrors the screen permission (one browser consent, documented limit)",
        )
        assertIs<CapturePortSources.HostPickerOnly>(snapshot.sources)

        assertEquals(Capability.Supported(Unit, FeatureAvailability.Available), snapshot.capabilities.sourceEnumeration)
        assertEquals(FeatureAvailability.Available, snapshot.capabilities.hostPicker)

        val screen = assertIs<Capability.Supported<CaptureTargetConstraints>>(snapshot.capabilities.screen)
        assertEquals(setOf(PixelFormat.Rgba8, PixelFormat.I420, PixelFormat.Nv12), screen.constraints.formats)
        assertEquals(
            setOf(CaptureCursorMode.Hidden, CaptureCursorMode.Embedded, CaptureCursorMode.EmbeddedWhenAvailable),
            screen.constraints.cursorModes,
        )
        assertEquals(
            FeatureAvailability.Unsupported,
            screen.constraints.region,
            "the browser picks its own bounds — the AppKit refusal form for region on screen/window",
        )
        assertEquals(FeatureAvailability.Available, screen.availability)
        assertEquals(
            snapshot.capabilities.screen,
            snapshot.capabilities.window,
            "the window capability mirrors the screen capability exactly",
        )

        val surface = assertIs<Capability.Supported<CaptureTargetConstraints>>(snapshot.capabilities.surface)
        assertEquals(setOf(PixelFormat.Rgba8), surface.constraints.formats)
        assertEquals(setOf(CaptureCursorMode.Hidden), surface.constraints.cursorModes)
        assertEquals(
            FeatureAvailability.Available,
            surface.constraints.region,
            "the canvas surface can be cropped for real (decision 8's visibleRect path)",
        )
        assertEquals(FeatureAvailability.Available, surface.availability)

        // Construction probes and reads back; it never prompts.
        assertFalse(dom.interactions.contains("pickDisplayMedia"))
        assertEquals(0, dom.pickCalls)
        assertEquals(
            1,
            dom.readbackRegistrations,
            "the settled-readback channel is registered exactly once, at construction",
        )
    }

    @Test
    fun permissionReadbackMapsAllThreeAnswersMirroredAcrossScopes() = runTest {
        val expected = mapOf(
            WebCapturePermissionQueryResult.Granted to PermissionState.Granted,
            WebCapturePermissionQueryResult.Denied to PermissionState.Denied(canRequestAgain = true),
            WebCapturePermissionQueryResult.NotDetermined to PermissionState.NotDetermined,
        )
        for ((answer, state) in expected) {
            val port = WebCapturePort(
                RecordingCaptureDom(queryResult = answer),
                primarySurfaceElementIsCanvas = false,
            )
            assertEquals(
                CapturePermissionState(state, state),
                port.initialSnapshot.permissions,
                "$answer must map to $state on both scopes",
            )
        }
    }

    @Test
    fun absentReadbackIsUnavailableOnBothScopesWithoutGatingThePipeline() = runTest {
        // The query rejects or the name is unknown (decision 3's second branch): both permissions
        // answer Unavailable(Unsupported(CapturePermission)) while the pipeline primitives — secure
        // context, picker, processor — still stand, because decision 5 gates on those, not on this.
        val port = WebCapturePort(
            RecordingCaptureDom(queryResult = null),
            primarySurfaceElementIsCanvas = false,
        )
        val unavailable = PermissionState.Unavailable(KadreFailure.Unsupported(KadreOperation.CapturePermission))
        assertEquals(CapturePermissionState(unavailable, unavailable), port.initialSnapshot.permissions)
        assertIs<Capability.Supported<CaptureTargetConstraints>>(port.initialSnapshot.capabilities.screen)
        assertEquals(FeatureAvailability.Available, port.initialSnapshot.capabilities.hostPicker)
    }

    // -- (a) the capability truth table: one test per documented cause -----------------------------------

    @Test
    fun insecureContextUnsupportedEverywhereWithSecureContextCauseOnThePicker() = runTest {
        val port = WebCapturePort(
            RecordingCaptureDom(secureContext = false, queryResult = null),
            primarySurfaceElementIsCanvas = true,
        )
        val capabilities = port.initialSnapshot.capabilities
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            capabilities.screen,
            "cause: secure-context",
        )
        assertEquals(capabilities.screen, capabilities.window)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            capabilities.surface,
            "cause: secure-context",
        )
        assertEquals(
            FeatureAvailability.Unavailable(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "capture-capability", "secure-context"),
            ),
            capabilities.hostPicker,
            "the picker's Unavailable carries the machine-readable cause",
        )
    }

    @Test
    fun missingGetDisplayMediaUnsupportedWithNoGetDisplayMediaCauseOnThePicker() = runTest {
        val port = WebCapturePort(
            RecordingCaptureDom(hasDisplayMedia = false),
            primarySurfaceElementIsCanvas = true,
        )
        val capabilities = port.initialSnapshot.capabilities
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            capabilities.screen,
            "cause: no-get-display-media",
        )
        assertEquals(capabilities.screen, capabilities.window)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            capabilities.surface,
            "cause: no-get-display-media",
        )
        assertEquals(
            FeatureAvailability.Unavailable(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "capture-capability", "no-get-display-media"),
            ),
            capabilities.hostPicker,
        )
    }

    @Test
    fun missingTrackProcessorUnsupportedWhileThePickerStaysAvailable() = runTest {
        val port = WebCapturePort(
            RecordingCaptureDom(processor = null),
            primarySurfaceElementIsCanvas = true,
        )
        val capabilities = port.initialSnapshot.capabilities
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            capabilities.screen,
            "cause: no-media-stream-track-processor",
        )
        assertEquals(capabilities.screen, capabilities.window)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            capabilities.surface,
            "cause: no-media-stream-track-processor",
        )
        assertEquals(
            FeatureAvailability.Available,
            capabilities.hostPicker,
            "the picker needs getDisplayMedia and a secure context only — the processor is the pump's primitive",
        )
    }

    @Test
    fun nonCanvasAttachElementUnsupportedSurfaceOnly() = runTest {
        val port = WebCapturePort(
            RecordingCaptureDom(),
            primarySurfaceElementIsCanvas = false,
        )
        val capabilities = port.initialSnapshot.capabilities
        assertIs<Capability.Supported<CaptureTargetConstraints>>(capabilities.screen)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            capabilities.surface,
            "cause: surface-not-a-canvas",
        )
        assertEquals(FeatureAvailability.Available, capabilities.hostPicker)
    }

    // -- (b) refreshSources: the same snapshot, and never the picker --------------------------------------

    @Test
    fun refreshSourcesReturnsTheSameSnapshotWithoutTouchingThePicker() = runTest {
        val dom = RecordingCaptureDom(queryResult = WebCapturePermissionQueryResult.Granted)
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)

        val refreshed = assertIs<KadreResult.Success<CapturePortSnapshot>>(port.refreshSources()).value
        assertSame(port.initialSnapshot, refreshed, "the honest no-op returns the same snapshot object")

        // The no-prompt invariant, stated so a single seam pick anywhere in the path fails it.
        assertEquals(0, dom.pickCalls)
        assertFalse(dom.interactions.contains("pickDisplayMedia"))

        // After the port has moved on (a readback answer landed), the refresh still answers the
        // CURRENT snapshot — the same object the observer was told about — and still prompts never.
        val received = mutableListOf<KadreResult<CapturePortSnapshot>>()
        port.installObserver { received += it }
        dom.fireReadback(WebCapturePermissionQueryResult.Denied)
        val updated = assertIs<KadreResult.Success<CapturePortSnapshot>>(received.single()).value
        assertSame(updated, assertIs<KadreResult.Success<CapturePortSnapshot>>(port.refreshSources()).value)
        assertEquals(0, dom.pickCalls)
    }

    // -- (c) requestPermission(Screen): the discard flow and its refusal table ----------------------------

    @Test
    fun requestPermissionScreenRunsTheDiscardFlowOnAPickedTrack() = runTest {
        val dom = RecordingCaptureDom(queryResult = WebCapturePermissionQueryResult.NotDetermined)
        val track = RecordingTrack()
        dom.pickResults += WebDisplayMediaPick.Picked(track)
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)

        val result = port.requestPermission(CapturePermissionScope.Screen)
        val snapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(result).value
        assertEquals(
            CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
            snapshot.permissions,
            "a picked stream is the consent verdict, mirrored across scopes",
        )
        assertSame(snapshot.capabilities, port.initialSnapshot.capabilities, "the discard flow changes no capability")
        assertIs<CapturePortSources.HostPickerOnly>(snapshot.sources)

        assertEquals(1, dom.pickCalls)
        assertEquals(
            listOf<Pair<String?, Double?>>(null to null),
            dom.recordedHints,
            "the discard flow states no picker hint",
        )
        assertEquals(1, track.stopCount, "the picked track is stopped exactly once")
        assertEquals(1, track.closeCount, "and the picked stream handle is released exactly once")
    }

    @Test
    fun requestPermissionScreenMapsTheRefusalTable() = runTest {
        // NotAllowedError — and the abort the dismissed picker answers with — is a real denial that
        // may be asked again.
        for (code in listOf("NotAllowedError", "AbortError")) {
            val dom = RecordingCaptureDom()
            dom.pickResults += WebDisplayMediaPick.Refused(code)
            val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)
            val snapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(port.requestPermission(CapturePermissionScope.Screen)).value
            assertEquals(
                CapturePermissionState(PermissionState.Denied(canRequestAgain = true), PermissionState.Denied(canRequestAgain = true)),
                snapshot.permissions,
                "$code must map to Denied(canRequestAgain = true) on both scopes",
            )
            assertEquals(1, dom.pickCalls)
        }

        // NotFoundError — the browser found no source — is a temporary unavailability, retryable.
        val noSource = RecordingCaptureDom()
        noSource.pickResults += WebDisplayMediaPick.Refused("NotFoundError")
        val noSourcePort = WebCapturePort(noSource, primarySurfaceElementIsCanvas = false)
        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
            noSourcePort.requestPermission(CapturePermissionScope.Screen),
        )
        assertEquals(1, noSource.pickCalls)

        // Any other code is a platform failure of this platform in the capture-permission domain.
        val other = RecordingCaptureDom()
        other.pickResults += WebDisplayMediaPick.Refused("UnknownError")
        val otherPort = WebCapturePort(other, primarySurfaceElementIsCanvas = false)
        assertEquals(
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Web, "capture-permission", "UnknownError")),
            otherPort.requestPermission(CapturePermissionScope.Screen),
        )
        assertEquals(1, other.pickCalls)
    }

    // -- (d) requestPermission(Window): the same flow, mirrored consent ------------------------------------

    @Test
    fun requestPermissionWindowRunsTheSameDiscardFlow() = runTest {
        val dom = RecordingCaptureDom()
        val track = RecordingTrack()
        dom.pickResults += WebDisplayMediaPick.Picked(track)
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)

        val snapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(port.requestPermission(CapturePermissionScope.Window)).value
        assertEquals(
            CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
            snapshot.permissions,
            "both scopes resolve — the browser's single consent governs whatever its picker offered",
        )
        assertEquals(1, dom.pickCalls)
        assertEquals(1, track.stopCount)
        assertEquals(1, track.closeCount)

        val refused = RecordingCaptureDom()
        refused.pickResults += WebDisplayMediaPick.Refused("NotAllowedError")
        val refusedPort = WebCapturePort(refused, primarySurfaceElementIsCanvas = false)
        val denied = assertIs<KadreResult.Success<CapturePortSnapshot>>(refusedPort.requestPermission(CapturePermissionScope.Window)).value
        assertEquals(
            CapturePermissionState(PermissionState.Denied(canRequestAgain = true), PermissionState.Denied(canRequestAgain = true)),
            denied.permissions,
        )
    }

    // -- (e) reserve(Source): refused before anything browser-facing --------------------------------------

    @Test
    fun reserveOfAnInventorySourceIsRefusedWithZeroSeamInteraction() = runTest {
        val dom = RecordingCaptureDom(queryResult = WebCapturePermissionQueryResult.Granted)
        // A pick is staged and MUST still be there afterwards: the refusal happens before any picker.
        dom.pickResults += WebDisplayMediaPick.Picked(RecordingTrack())
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)
        // Construction itself probes the seam; the refusal must add not one interaction to that.
        val atConstruction = dom.interactions.toList()

        val result = port.reserve(
            CapturePortTarget.Source(CapturePortSourceKey("web-test", 0L)),
            CaptureRequest(),
        )
        assertEquals(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)), result)
        assertEquals(0, dom.pickCalls, "the Source refusal precedes every picker call")
        assertEquals(1, dom.pickResults.size, "the staged pick is still there: it was never consumed")
        assertFalse(dom.interactions.contains("pickDisplayMedia"))
        assertEquals(atConstruction, dom.interactions, "the refusal is structural: zero seam interaction beyond the construction probe")
    }

    @Test
    fun reserveOfSurfaceRefusesAnUnregisteredIdWithZeroSeamInteraction() = runTest {
        // Task 4 routed the Surface row: the port resolves a target id against the registration
        // its session performed for the primary surface. A port whose session registered nothing —
        // as this one did not — refuses every id in the runtime's own unresolvable-target form,
        // before lending the canvas, and with not one seam interaction beyond the construction
        // probe. The streaming vertical itself is pinned by WebCaptureSurfaceTest.
        val dom = RecordingCaptureDom()
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = true)
        val atConstruction = dom.interactions.toList()

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("request.target")),
            port.reserve(CapturePortTarget.Surface(RuntimeProcessIds.nextSurfaceId()), CaptureRequest()),
            "an unregistered id names no surface this port can honestly capture",
        )
        assertEquals(atConstruction, dom.interactions, "the refusal touches no seam member")
        assertFalse(dom.interactions.contains("pickDisplayMedia"))
        assertFalse(dom.interactions.contains("canvasForSurface"), "the canvas is never lent behind a refused id")
    }

    // -- (f) the observation channel and the idempotent close ---------------------------------------------

    @Test
    fun observerReceivesLaterSnapshotsAndAWithdrawnRegistrationDeliversNothing() = runTest {
        val dom = RecordingCaptureDom(queryResult = WebCapturePermissionQueryResult.NotDetermined)
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)

        val received = mutableListOf<KadreResult<CapturePortSnapshot>>()
        val registration = port.installObserver { received += it }

        dom.fireReadback(WebCapturePermissionQueryResult.Granted)
        assertEquals(1, received.size, "the settled readback is republished as one complete snapshot")
        val snapshot = assertIs<KadreResult.Success<CapturePortSnapshot>>(received.single()).value
        assertEquals(
            CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
            snapshot.permissions,
        )
        assertSame(snapshot.capabilities, port.initialSnapshot.capabilities, "the republish changes permissions only")

        registration.close()
        dom.fireReadback(WebCapturePermissionQueryResult.Denied)
        assertEquals(1, received.size, "a withdrawn registration delivers nothing")
    }

    @Test
    fun observerInstalledAfterTheReadbackSeesTheCurrentSnapshot() = runTest {
        val dom = RecordingCaptureDom(queryResult = WebCapturePermissionQueryResult.NotDetermined)
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)

        // The readback settles before any observer exists: the answer is kept, not lost.
        dom.fireReadback(WebCapturePermissionQueryResult.Granted)
        val late = mutableListOf<KadreResult<CapturePortSnapshot>>()
        port.installObserver { late += it }
        assertEquals(
            1,
            late.size,
            "an install over a port that moved past its initial snapshot delivers that current snapshot",
        )
        assertEquals(
            CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
            assertIs<KadreResult.Success<CapturePortSnapshot>>(late.single()).value.permissions,
        )

        // A port still at its initial snapshot delivers nothing on install — the runtime already
        // holds exactly that snapshot from the construction read.
        val untouched = mutableListOf<KadreResult<CapturePortSnapshot>>()
        WebCapturePort(RecordingCaptureDom(), primarySurfaceElementIsCanvas = false).installObserver { untouched += it }
        assertTrue(untouched.isEmpty())
    }

    @Test
    fun closeIsIdempotentGatesEveryLaterCallAndStopsTheSeamExactlyOnce() = runTest {
        val dom = RecordingCaptureDom(queryResult = WebCapturePermissionQueryResult.Granted)
        val port = WebCapturePort(dom, primarySurfaceElementIsCanvas = false)
        val received = mutableListOf<KadreResult<CapturePortSnapshot>>()
        port.installObserver { received += it }
        dom.pickResults += WebDisplayMediaPick.Picked(RecordingTrack())

        port.close()
        port.close()
        assertEquals(1, dom.closeCount, "the seam is closed exactly once however often the port is")

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.refreshSources(),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.requestPermission(CapturePermissionScope.Screen),
            "a closed port never reaches the consent flow",
        )
        assertEquals(0, dom.pickCalls)
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)),
            port.reserve(CapturePortTarget.Source(CapturePortSourceKey("web-test", 0L)), CaptureRequest()),
        )
        assertFailsWith<IllegalStateException> { port.installObserver { } }

        // A readback answering after the close is dropped, not delivered.
        dom.fireReadback(WebCapturePermissionQueryResult.Denied)
        assertTrue(received.isEmpty(), "no snapshot is published after the close")
    }

    // -- the wiring: the session publishes the port's snapshot through the runtime on both paths -----------

    @Test
    fun theSessionWiresTheCapturePortOnTheDirectAttachmentPath() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(
                RecordingWebHostPort(WebSurfaceMetrics(64.0, 64.0, 1.0), element = Any()),
                WebHostRegistry(),
            ).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
            ),
        ).value
        testScheduler.runCurrent()

        try {
            assertIs<CaptureSources.HostPickerOnly>(
                scopeReady.await().capture.state.value.sources,
                "the session's own capture port publishes HostPickerOnly through the runtime",
            )
        } finally {
            session.requestStop()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun theSessionWiresTheCapturePortOnTheWindowProviderPath() = runTest {
        val scopeReady = CompletableDeferred<KadreScope>()
        val session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(
                RecordingWebHostPort(WebSurfaceMetrics(64.0, 64.0, 1.0), element = Any()),
                WebHostRegistry(),
            ).attach(
                this,
                KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                KadrePolicies.Default,
                // The components path: a provider present, with the factory and probe it requires.
                // Neither is invoked — the test only needs the components factory to have run.
                windowProvider = { _, _ -> error("no window is requested by this test") },
                childSessionFactory = { _, _ -> error("no child is opened by this test") },
                windowHostProbe = { _ ->
                    WebWindowHostChecks(elementConnected = true, distinctDefaultView = true, scopeHasJob = true, scopeActive = true)
                },
            ),
        ).value
        testScheduler.runCurrent()

        try {
            assertIs<CaptureSources.HostPickerOnly>(
                scopeReady.await().capture.state.value.sources,
                "the components path hands the runtime the same session-scoped capture port",
            )
        } finally {
            session.requestStop()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun theSeamProbeOfANonDomElementAnswersNoneWithoutThrowing() {
        // The real per-target realization runs here, in the browser, against the session double's
        // plain Kotlin element: the kind check must answer "not a canvas" rather than throw — the
        // phase-5 lesson about element-kind checks — and a port with no element lends none.
        val dom = webCaptureDom(element = { Any() })
        assertEquals(null, dom.canvasForSurface())
        assertEquals(null, webCaptureDom(element = { null }).canvasForSurface())
    }
}
