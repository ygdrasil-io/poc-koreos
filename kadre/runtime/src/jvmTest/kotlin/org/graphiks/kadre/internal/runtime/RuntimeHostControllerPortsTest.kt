package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayConnectionState
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RuntimeHostControllerPortsTest {
    @Test
    fun `withPrimarySurface forwards display and gamepad ports to the session`() = runTest {
        val displayPort = StubDisplayPort(
            snapshot = DisplayPortSnapshot(
                primaryKey = 0L,
                displays = listOf(hostViewportDisplay()),
            ),
        )
        val gamepadPort = StubGamepadPort(initialGamepads = listOf(connectedGamepad(key = 11L)))
        val host = RuntimeHostController.withPrimarySurface(
            platform = KadrePlatform.Fake,
            sessionRevocationHandler = RuntimeSessionRevocationHandler { },
            primarySurfaceFactory = ::stubRuntimePrimarySurface,
            displayPort = displayPort,
            gamepadPort = gamepadPort,
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        observed.displays.requestAccess()
        val displays = assertIs<DisplayInventory.Enumerated>(observed.displays.state.value.inventory)
        val display = displays.displays.single()
        assertEquals("Host viewport", display.state.value.name)
        assertEquals(DisplayConnectionState.Connected, display.state.value.connection)
        val devices = assertIs<DeviceInventory.Enumerated>(observed.devices.state.value.inventory)
        assertEquals(listOf("Controller 11"), devices.gamepads.map { it.state.value.descriptor.name })

        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun `withPrimarySurface forwards the input device port to the session`() = runTest {
        val inputPort = StubInputDevicePort(
            devices = listOf(inputDevice(key = 3L, name = "Keyboard", kind = InputDeviceKind.Keyboard)),
        )
        val host = RuntimeHostController.withPrimarySurface(
            platform = KadrePlatform.Fake,
            sessionRevocationHandler = RuntimeSessionRevocationHandler { },
            primarySurfaceFactory = ::stubRuntimePrimarySurface,
            inputDevicePort = inputPort,
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        val devices = assertIs<DeviceInventory.Enumerated>(observed.devices.state.value.inventory)
        assertEquals(listOf("Keyboard"), devices.devices.map { it.descriptor.name })

        session.close()
        testScheduler.runCurrent()
    }

    @Test
    fun `withPrimarySurface without ports keeps the unsupported display and device managers`() = runTest {
        val host = RuntimeHostController.withPrimarySurface(
            platform = KadrePlatform.Fake,
            sessionRevocationHandler = RuntimeSessionRevocationHandler { },
            primarySurfaceFactory = ::stubRuntimePrimarySurface,
        )
        lateinit var observed: KadreScope

        val session = attach(host) {
            observed = this
            awaitCancellation()
        }
        testScheduler.runCurrent()

        val access = observed.displays.requestAccess()
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.DisplayAccess)),
            access,
        )
        assertIs<DisplayInventory.Unavailable>(observed.displays.state.value.inventory)
        assertEquals(DeviceInventory.Unsupported, observed.devices.state.value.inventory)

        session.close()
        testScheduler.runCurrent()
    }

    private fun kotlinx.coroutines.test.TestScope.attach(
        host: RuntimeHostController,
        application: suspend KadreScope.() -> Unit,
    ): KadreSession = assertIs<KadreResult.Success<KadreSession>>(
        host.attach(this, KadreApplicationFactory { KadreApplication(application) }, KadrePolicies.Default),
    ).value

    private fun stubRuntimePrimarySurface(id: SurfaceId): RuntimePrimarySurface {
        val surface = RuntimeHostSurface(id, initialSurfaceState())
        return RuntimePrimarySurface(surface, surface::close)
    }

    private fun initialSurfaceState(): SurfaceState = SurfaceState(
        attachment = SurfaceAttachmentState.Attached,
        logicalSize = LogicalSize(1.0, 1.0),
        physicalSize = PhysicalSize(1, 1),
        scaleFactor = 1.0,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
        visibility = SurfaceVisibility.Visible,
        occlusion = SurfaceOcclusion.Visible,
        focus = SurfaceFocus.Focused,
        appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
        cursor = org.graphiks.kadre.surface.CursorStyle.System(org.graphiks.kadre.surface.CursorIcon.Default),
        pointerCapture = PointerCaptureMode.None,
        hitTesting = HitTestingMode.Enabled,
        inputDefaultBehavior = InputDefaultBehavior.HostDefault,
        revision = SurfaceRevision(0),
    )

    private fun hostViewportDisplay(): DisplayPortDisplay = DisplayPortDisplay(
        key = 0L,
        type = DisplayType.Physical,
        name = "Host viewport",
        bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1280, 720)),
        workArea = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1280, 720)),
        scaleFactor = 1.0,
        currentModeKey = 1L,
        modes = listOf(DisplayPortMode(key = 1L, physicalSize = PhysicalSize(1280, 720), refreshRateHz = 60.0, bitDepth = 24)),
    )

    private fun connectedGamepad(key: Long): GamepadPortGamepad = GamepadPortGamepad(
        key = key,
        descriptor = GamepadDescriptor(
            name = "Controller $key",
            mapping = GamepadMapping.Standard,
            buttons = listOf(GamepadButton.South),
            axes = emptyList(),
        ),
        state = GamepadState(
            buttons = listOf(GamepadButtonValue(GamepadButton.South, 0.0, false)),
            axes = emptyList(),
        ),
        routing = GamepadRoutingState.Routed,
        capabilities = GamepadCapabilities(
            effects = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)),
        ),
    )

    private fun inputDevice(
        key: Long,
        name: String,
        kind: InputDeviceKind,
    ): InputDevicePortDevice = InputDevicePortDevice(
        key = key,
        descriptor = InputDeviceDescriptor(name = name, kind = kind),
    )

    private class StubDisplayPort(
        private val snapshot: DisplayPortSnapshot,
    ) : DisplayPort {
        override val enumerationCapability: Capability<Unit> =
            Capability.Supported(Unit, FeatureAvailability.Available)

        override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> = KadreResult.Success(snapshot)

        override fun installSnapshotObserver(observer: (KadreResult<DisplayPortSnapshot>) -> Unit): AutoCloseable =
            AutoCloseable { }

        override fun close() = Unit
    }

    private class StubGamepadPort(
        initialGamepads: List<GamepadPortGamepad> = emptyList(),
    ) : GamepadPort {
        override val gamepads: List<GamepadPortGamepad> = initialGamepads

        override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable = AutoCloseable { }

        override fun updateRouting(routing: GamepadPortRouting) = Unit

        override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> =
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.GamepadEffect))

        override fun close() = Unit
    }

    private class StubInputDevicePort(
        override val devices: List<InputDevicePortDevice> = emptyList(),
    ) : InputDevicePort {
        override fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable = AutoCloseable { }

        override fun close() = Unit
    }
}
