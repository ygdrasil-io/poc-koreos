package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureManagerRevision
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class RuntimeCaptureManagerTest {
    @Test
    fun refreshPublishesOneRevisionedSnapshotAndKeepsTheOpaqueSourceIdStable() = runTest {
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                sources = CapturePortSources.Enumerated(listOf(displaySource(name = "Primary"))),
            ),
        )
        val manager = RuntimeCaptureManager(port)
        val initial = assertIs<CaptureSources.Enumerated>(manager.state.value.sources).values.single()

        port.refreshResult = KadreResult.Success(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                sources = CapturePortSources.Enumerated(listOf(displaySource(name = "Primary display"))),
            ),
        )

        val result = manager.refreshSources()
        val refreshedState = successValue(result)
        val refreshed = assertIs<CaptureSources.Enumerated>(refreshedState.sources).values.single()

        assertEquals(CaptureManagerRevision(1), refreshedState.revision)
        assertEquals(initial.id, refreshed.id)
        assertEquals("Primary display", refreshed.name)
        assertEquals(refreshedState.revision, refreshed.managerRevision)
    }

    @Test
    fun requestPermissionReplacesTheEntireControlPlaneSnapshot() = runTest {
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined),
                sources = CapturePortSources.HostPickerOnly,
            ),
        )
        val manager = RuntimeCaptureManager(port)
        port.permissionResult = KadreResult.Success(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.NotDetermined),
                sources = CapturePortSources.Enumerated(listOf(displaySource(name = "Primary"))),
            ),
        )

        val result = manager.requestPermission(CapturePermissionScope.Screen)
        val refreshedState = successValue(result)

        assertEquals(CaptureManagerRevision(1), refreshedState.revision)
        assertEquals(PermissionState.Granted, refreshedState.permissions.screen)
        assertIs<CaptureSources.Enumerated>(refreshedState.sources)
    }

    @Test
    fun refreshFailureWithdrawsThePreviousSourceInventory() = runTest {
        val failure = KadreFailure.PlatformFailure(KadrePlatform.Fake, "capture", "enumeration-failed")
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                sources = CapturePortSources.Enumerated(listOf(displaySource(name = "Primary"))),
            ),
        )
        val manager = RuntimeCaptureManager(port)
        port.refreshResult = KadreResult.Failure(failure)

        val result = manager.refreshSources()
        val unavailable = assertIs<CaptureSources.Unavailable>(manager.state.value.sources)

        assertEquals(KadreResult.Failure(failure), result)
        assertEquals(failure, unavailable.failure)
        assertEquals(CaptureManagerRevision(1), manager.state.value.revision)
    }

    @Test
    fun persistentPermissionFailureMarksOnlyTheRequestedPermissionUnavailable() = runTest {
        val failure = KadreFailure.PlatformFailure(KadrePlatform.Fake, "capture", "permission-failed")
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined),
                sources = CapturePortSources.HostPickerOnly,
            ),
        )
        val manager = RuntimeCaptureManager(port)
        port.permissionResult = KadreResult.Failure(failure)

        val result = manager.requestPermission(CapturePermissionScope.Screen)

        assertEquals(KadreResult.Failure(failure), result)
        assertEquals(PermissionState.Unavailable(failure), manager.state.value.permissions.screen)
        assertEquals(PermissionState.NotDetermined, manager.state.value.permissions.window)
        assertIs<CaptureSources.HostPickerOnly>(manager.state.value.sources)
    }

    @Test
    fun retryableRefreshFailurePreservesThePublishedControlPlane() = runTest {
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                sources = CapturePortSources.Enumerated(listOf(displaySource(name = "Primary"))),
            ),
        )
        val manager = RuntimeCaptureManager(port)
        val before = manager.state.value
        val failure = KadreFailure.TemporarilyUnavailable(retryable = true)
        port.refreshResult = KadreResult.Failure(failure)

        val result = manager.refreshSources()

        assertEquals(KadreResult.Failure(failure), result)
        assertSame(before, manager.state.value)
    }

    @Test
    fun refreshPermissionDenialPublishesTheRequiredPermissionInsteadOfUnavailable() = runTest {
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined),
                sources = CapturePortSources.HostPickerOnly,
            ),
        )
        val manager = RuntimeCaptureManager(port)
        val failure = KadreFailure.PermissionDenied(KadrePermission.CaptureScreen)
        port.refreshResult = KadreResult.Failure(failure)

        val result = manager.refreshSources()
        val required = assertIs<CaptureSources.PermissionRequired>(manager.state.value.sources)

        assertEquals(KadreResult.Failure(failure), result)
        assertEquals(setOf(KadrePermission.CaptureScreen), required.required)
    }

    @Test
    fun refreshPermissionDenialPreservesEveryPermissionRequiredForTheCurrentInventory() = runTest {
        val initial = snapshot(
            permissions = CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined),
            sources = CapturePortSources.PermissionRequired(
                setOf(KadrePermission.CaptureScreen, KadrePermission.CaptureWindow),
            ),
        ).copy(
            capabilities = CaptureCapabilities(
                screen = supportedConstraints(FeatureAvailability.RequiresPermission(KadrePermission.CaptureScreen)),
                window = supportedConstraints(FeatureAvailability.RequiresPermission(KadrePermission.CaptureWindow)),
                surface = supportedConstraints(),
                sourceEnumeration = Capability.Supported(
                    Unit,
                    FeatureAvailability.RequiresPermission(KadrePermission.CaptureScreen),
                ),
                hostPicker = FeatureAvailability.RequiresPermission(KadrePermission.CaptureScreen),
            ),
        )
        val port = RecordingCapturePort(initial)
        val manager = RuntimeCaptureManager(port)
        port.refreshResult = KadreResult.Failure(KadreFailure.PermissionDenied(KadrePermission.CaptureScreen))

        manager.refreshSources()

        assertEquals(
            setOf(KadrePermission.CaptureScreen, KadrePermission.CaptureWindow),
            assertIs<CaptureSources.PermissionRequired>(manager.state.value.sources).required,
        )
    }

    @Test
    fun closedManagerReportsTheClosedHost() = runTest {
        val port = RecordingCapturePort(
            snapshot(
                permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
                sources = CapturePortSources.HostPickerOnly,
            ),
        )
        val manager = RuntimeCaptureManager(port)

        manager.close()
        val result = manager.refreshSources()

        assertEquals(KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host)), result)
    }
}

internal class RecordingCapturePort(
    override val initialSnapshot: CapturePortSnapshot,
) : CapturePort {
    lateinit var permissionResult: KadreResult<CapturePortSnapshot>
    lateinit var refreshResult: KadreResult<CapturePortSnapshot>

    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> = permissionResult

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> = refreshResult

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable = AutoCloseable { }

    var closeCount = 0
        private set

    override fun close() {
        closeCount += 1
    }
}

internal fun snapshot(
    permissions: CapturePermissionState,
    sources: CapturePortSources,
): CapturePortSnapshot = CapturePortSnapshot(
    permissions = permissions,
    capabilities = CaptureCapabilities(
        screen = supportedConstraints(),
        window = supportedConstraints(),
        surface = supportedConstraints(),
        sourceEnumeration = Capability.Supported(Unit, FeatureAvailability.Available),
        hostPicker = FeatureAvailability.Available,
    ),
    sources = sources,
)

private fun supportedConstraints(
    availability: FeatureAvailability = FeatureAvailability.Available,
): Capability<CaptureTargetConstraints> = Capability.Supported(
    CaptureTargetConstraints(
        formats = setOf(PixelFormat.Bgra8),
        cursorModes = setOf(CaptureCursorMode.EmbeddedWhenAvailable),
        region = FeatureAvailability.Available,
    ),
    availability,
)

private fun displaySource(name: String): CapturePortSource = CapturePortSource(
    key = CapturePortSourceKey("display", 7L),
    kind = CaptureSourceKind.Display,
    name = name,
    size = PhysicalSize(1920, 1080),
)

private fun <T> successValue(result: KadreResult<T>): T = when (result) {
    is KadreResult.Success -> result.value
    is KadreResult.Failure -> error("expected success, got ${result.reason}")
}
