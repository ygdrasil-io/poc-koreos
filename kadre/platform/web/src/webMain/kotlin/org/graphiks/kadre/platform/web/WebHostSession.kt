package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.KeyboardState
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimeSessionRevocationHandler
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility

internal data class WebSurfaceSnapshot(
    val logicalWidth: Double,
    val logicalHeight: Double,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val scaleFactor: Double,
)

internal interface WebHostPort {
    val initialSnapshot: WebSurfaceSnapshot

    /**
     * Stable target-owned identity used for admission.  Target ports override this with their
     * element identity; the default preserves the existing inert ports until they do so.
     */
    val stableIdentity: Any get() = this

    /** Initial browser facts copied by the target before common admission starts. */
    val initialLifecycleSnapshot: WebLifecycleSnapshot
        get() = WebLifecycleSnapshot(
            connected = true,
            inOriginDocument = true,
            documentVisible = true,
            browsingContextFocused = true,
            subtreeFocused = true,
        )

    /**
     * Installs target-owned observation after ownership has been reserved.
     * [release] removes this observer together with every other target resource.
     */
    fun installLifecycleObserver(observer: (WebLifecycleSnapshot) -> Unit) = Unit

    fun release()
}

internal class WebHostSession(
    private val port: WebHostPort,
    private val registry: WebHostRegistry = WebHostRegistry.shared,
) {
    fun attach(
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy,
        attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    ): KadreResult<KadreSession> {
        val reducer = WebLifecycleReducer(attachmentPolicy)
        val initialLifecycle = when (val reduction = reducer.reduce(port.initialLifecycleSnapshot)) {
            is WebLifecycleReduction.Update -> reduction.state
            WebLifecycleReduction.Terminate -> return KadreResult.Failure(KadreFailure.InvalidRequest("element"))
        }
        val parentJob = parentScope.coroutineContext[Job]
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("parentScope"))
        if (!parentJob.isActive) return KadreResult.Failure(KadreFailure.ParentScopeCancelled)

        val reservation = when (val result = registry.reserve(port.stableIdentity)) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure -> return result
        }
        val ownership = WebHostOwnership(port, reservation)
        val controller = createController(initialLifecycle, ownership)
        val installed = runCatching {
            port.installLifecycleObserver { snapshot ->
                when (val reduction = reducer.reduce(snapshot)) {
                    is WebLifecycleReduction.Update -> controller.updateLifecycle(reduction.state)
                    WebLifecycleReduction.Terminate -> {
                        if (snapshot.pageHidden) controller.detachImmediately() else controller.detach()
                    }
                }
            }
        }
        if (installed.isFailure) {
            ownership.releaseAfterAttachFailure()
            return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "web-host", "lifecycle-install-failed"),
            )
        }

        val attached = controller.attach(parentScope, applicationFactory, policy)
        if (attached is KadreResult.Failure) ownership.releaseAfterAttachFailure()
        return attached
    }

    private fun createController(
        initialLifecycle: LifecycleState,
        ownership: WebHostOwnership,
    ): RuntimeHostController = RuntimeHostController.withPrimarySurface(
        platform = KadrePlatform.Web,
        initialLifecycleState = initialLifecycle,
        sessionRevocationHandler = RuntimeSessionRevocationHandler { ownership.releasePort() },
        sessionObserver = RuntimeSessionObserver { _, _ -> ownership.releaseReservation() },
        primarySurfaceFactory = { id ->
            val surface = WebHostSurface(id, port, ownership)
            RuntimePrimarySurface(surface, surface::detach)
        },
    )
}

private class WebHostOwnership(
    private val port: WebHostPort,
    private val reservation: WebHostReservation,
) {
    private var portReleased: Boolean = false
    private var reservationReleased: Boolean = false

    fun releaseAfterAttachFailure() {
        releasePort()
        releaseReservation()
    }

    fun releasePort() {
        if (portReleased) return
        portReleased = true
        runCatching { port.release() }
    }

    fun releaseReservation() {
        if (reservationReleased) return
        reservationReleased = true
        reservation.release()
    }
}

private class WebHostSurface(
    override val id: SurfaceId,
    private val port: WebHostPort,
    private val ownership: WebHostOwnership,
) : HostSurface {
    private var detached: Boolean = false
    private val mutableState = MutableStateFlow(
        SurfaceState(
            attachment = SurfaceAttachmentState.Attached,
            logicalSize = LogicalSize(port.initialSnapshot.logicalWidth, port.initialSnapshot.logicalHeight),
            physicalSize = PhysicalSize(port.initialSnapshot.physicalWidth, port.initialSnapshot.physicalHeight),
            scaleFactor = port.initialSnapshot.scaleFactor,
            safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
            visibility = SurfaceVisibility.Visible,
            occlusion = SurfaceOcclusion.Visible,
            focus = SurfaceFocus.Focused,
            appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
            cursor = CursorStyle.System(CursorIcon.Default),
            pointerCapture = PointerCaptureMode.None,
            hitTesting = HitTestingMode.Enabled,
            inputDefaultBehavior = InputDefaultBehavior.HostDefault,
            revision = SurfaceRevision(0L),
        ),
    )
    private val mutableCapabilities = MutableStateFlow(unsupportedSurfaceCapabilities())

    override val state: StateFlow<SurfaceState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<SurfaceCapabilities> = mutableCapabilities.asStateFlow()
    override val events: Flow<SurfaceEvent> = emptyFlow()
    override val input: SurfaceInput = UnsupportedWebSurfaceInput

    override fun requestRedraw(): KadreResult<Unit> =
        KadreResult.Failure(
            if (detached) KadreFailure.Closed(KadreResourceKind.Surface)
            else KadreFailure.Unsupported(KadreOperation.RequestRedraw),
        )

    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> =
        KadreResult.Failure(
            if (detached) KadreFailure.Closed(KadreResourceKind.Surface)
            else KadreFailure.Unsupported(KadreOperation.UpdateSurface),
        )

    fun detach() {
        if (detached) return
        detached = true
        try {
            val current = mutableState.value
            mutableState.value = current.copy(
                attachment = SurfaceAttachmentState.Detached,
                revision = SurfaceRevision(current.revision.value + 1L),
            )
        } finally {
            ownership.releasePort()
        }
    }
}

private object UnsupportedWebSurfaceInput : SurfaceInput {
    private val unsupportedTextInput = KadreFailure.Unsupported(KadreOperation.TextInput)
    private val mutableState = MutableStateFlow(
        SurfaceInputState(
            keyboard = KeyboardState(emptySet()),
            pointers = emptyList(),
            touches = emptyList(),
            modifiers = KeyboardModifiers(emptySet()),
            capabilities = InputCapabilities(
                keyboard = FeatureAvailability.Unsupported,
                pointer = FeatureAvailability.Unsupported,
                touch = FeatureAvailability.Unsupported,
                gestures = FeatureAvailability.Unsupported,
                dragAndDrop = FeatureAvailability.Unsupported,
                textInput = Capability.Unsupported(unsupportedTextInput),
                rawInput = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            ),
            revision = InputStateRevision(0L),
        ),
    )

    override val events: Flow<org.graphiks.kadre.input.InputEvent> = emptyFlow()
    override val state: StateFlow<SurfaceInputState> = mutableState.asStateFlow()

    override suspend fun openTextInput(config: TextInputConfig): KadreResult<TextInputSession> =
        KadreResult.Failure(unsupportedTextInput)

    @OptIn(DelicateKadreApi::class)
    override suspend fun requestRawInput(): KadreResult<RawInputAccess> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))
}

private fun unsupportedSurfaceCapabilities(): SurfaceCapabilities = SurfaceCapabilities(
    cursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    customCursor = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    pointerCapture = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    hitTesting = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    inputDefaultBehavior = unsupportedSurfaceCapability(KadreOperation.UpdateSurface),
    handlerInteractions = unsupportedSurfaceCapability(KadreOperation.InstallInteractionHandler),
    armedInteractions = unsupportedSurfaceCapability(KadreOperation.ArmInteraction),
    platformAccess = unsupportedSurfaceCapability(KadreOperation.PlatformSurfaceAccess),
)

private fun <T> unsupportedSurfaceCapability(operation: KadreOperation): Capability<T> =
    Capability.Unsupported(KadreFailure.Unsupported(operation))
