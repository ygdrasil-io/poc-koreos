package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.display.DisplayManager
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.window.WindowManager

/**
 * Unstable backend SPI for supplying resources owned by one runtime session.
 *
 * This type is technically public only so backend modules can implement it. It is not part of
 * Kadre's supported public API and may change without compatibility guarantees.
 */
public fun interface RuntimeSessionComponentsFactory {
    public fun create(sessionId: SessionId, rootScope: CoroutineScope): RuntimeSessionComponents
}

/**
 * Unstable backend SPI containing resources owned by one runtime session.
 *
 * This type is technically public only for backend integration. It is not part of Kadre's
 * supported public API and may change without compatibility guarantees.
 */
public class RuntimeSessionComponents private constructor(
    public val windows: WindowManager,
    /**
     * Optional session-owned raw-input port consumed by [RuntimeSessionWindowManager].
     *
     * Components retain ownership until their manager has completed configuration. Closing the
     * component therefore closes the port too; implementations must make [RawInputPort.close]
     * idempotent.
     */
    public val rawInputPort: RawInputPort?,
    /** Optional session-owned display port projected as the public [DisplayManager]. */
    public val displayPort: DisplayPort?,
    /** Optional session-owned capture control-plane port projected as the public CaptureManager. */
    public val capturePort: CapturePort?,
    /** Optional session-owned gamepad port projected as the public [org.graphiks.kadre.input.DeviceManager]. */
    public val gamepadPort: GamepadPort?,
    /** Optional session-owned generic input-device port projected as the public DeviceManager. */
    public val inputDevicePort: InputDevicePort?,
    private val closeAction: () -> Unit,
    primarySurface: RuntimePrimarySurface?,
) : AutoCloseable {
    public constructor(
        windows: WindowManager,
        rawInputPort: RawInputPort? = null,
        displayPort: DisplayPort? = null,
        capturePort: CapturePort? = null,
        gamepadPort: GamepadPort? = null,
        inputDevicePort: InputDevicePort? = null,
        closeAction: () -> Unit = {},
    ) : this(windows, rawInputPort, displayPort, capturePort, gamepadPort, inputDevicePort, closeAction, null)

    public constructor(
        windows: WindowManager,
        primarySurface: RuntimePrimarySurface,
        rawInputPort: RawInputPort? = null,
        displayPort: DisplayPort? = null,
        capturePort: CapturePort? = null,
        gamepadPort: GamepadPort? = null,
        inputDevicePort: InputDevicePort? = null,
        closeAction: () -> Unit = {},
    ) : this(windows, rawInputPort, displayPort, capturePort, gamepadPort, inputDevicePort, closeAction, primarySurface)

    private val lock = RuntimeLock()
    private var closed = false

    public val primarySurface: HostSurface? = primarySurface?.surface
    private val closePrimarySurface: (() -> Unit)? = primarySurface?.let { it::close }

    override public fun close() {
        val shouldClose = lock.withLock {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (shouldClose) {
            var failure: Throwable? = null
            try {
                closePrimarySurface?.invoke()
            } catch (cause: Throwable) {
                failure = cause
            }
            try {
                closeAction()
            } catch (cause: Throwable) {
                failure = failure.withSuppressed(cause)
            }
            try {
                rawInputPort?.close()
            } catch (cause: Throwable) {
                failure = failure.withSuppressed(cause)
            }
            try {
                displayPort?.close()
            } catch (cause: Throwable) {
                failure = failure.withSuppressed(cause)
            }
            try {
                capturePort?.close()
            } catch (cause: Throwable) {
                failure = failure.withSuppressed(cause)
            }
            try {
                gamepadPort?.close()
            } catch (cause: Throwable) {
                failure = failure.withSuppressed(cause)
            }
            try {
                inputDevicePort?.close()
            } catch (cause: Throwable) {
                failure = failure.withSuppressed(cause)
            }
            failure?.let { throw it }
        }
    }

    internal fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: Any,
        maxCollectorsPerFlow: Int,
        resources: ResourceBudgetPolicy,
        dropTransferScope: CoroutineScope,
        diagnostics: (KadreDiagnostic) -> Unit,
        rawInputPort: RawInputPort?,
    ) {
        (windows as? RuntimeSessionWindowManager)?.installSessionConfiguration(
            deliveryPolicy,
            inputDeliveryPolicy,
            source,
            sessionFailureHandler,
            collectorAllocator,
            maxCollectorsPerFlow,
            dropTransferScope,
            diagnostics,
            rawInputPort,
        )
        // One parameter more than the components-side manager: the resource budgets. The components
        // branch receives its policy when its backend constructs the manager for this session, while
        // a host-provided primary surface is built by a factory that never sees the session policy.
        // Delivering it here is what keeps the primary surface able to build the same shared reducer
        // as the components branch instead of falling back to a built-in default.
        (primarySurface as? RuntimePrimarySurfaceConfiguration)?.installSessionConfiguration(
            deliveryPolicy,
            inputDeliveryPolicy,
            source,
            sessionFailureHandler,
            collectorAllocator,
            maxCollectorsPerFlow,
            resources,
            dropTransferScope,
            diagnostics,
            rawInputPort,
        )
    }

    internal fun installExclusiveDisplayTargetResolver(resolver: ExclusiveDisplayTargetResolver) {
        (windows as? RuntimeSessionWindowManager)?.installExclusiveDisplayTargetResolver(resolver)
    }
}

internal interface RuntimeSessionWindowManager {
    fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: Any,
        maxCollectorsPerFlow: Int,
        dropTransferScope: CoroutineScope?,
        diagnostics: (KadreDiagnostic) -> Unit,
        rawInputPort: RawInputPort?,
    )

    fun installExclusiveDisplayTargetResolver(resolver: ExclusiveDisplayTargetResolver) {}
}

internal object UnsupportedRuntimeSessionComponentsFactory : RuntimeSessionComponentsFactory {
    override fun create(sessionId: SessionId, rootScope: CoroutineScope): RuntimeSessionComponents =
        RuntimeSessionComponents(UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId))
}

/**
 * Unstable backend SPI implemented by a host-provided primary surface that consumes session
 * delivery configuration.
 *
 * This type is technically public only so backend modules can implement it. It is not part of
 * Kadre's supported public API and may change without compatibility guarantees.
 *
 * The runtime installs this configuration once per session, after the surface exists and before
 * any application code runs. A surface must therefore buffer stimuli that arrive earlier and
 * flush them, in order, once [installSessionConfiguration] has been called.
 *
 * The parameters are the session-owned collaborators an implementation cannot obtain on its own,
 * and they are the same ones [RuntimeSessionWindowManager] receives, so a primary surface builds
 * the same shared ordinary-input reducer as the components-side window manager. Each parameter
 * names the reducer field it feeds:
 *
 * - [deliveryPolicy] feeds the surface's own window-delivery policy (redraw and geometry
 *   publication), as it does for the components-side manager.
 * - [inputDeliveryPolicy] feeds `RuntimeSurfaceInput.deliveryPolicy`: the ingress capacity of
 *   discrete input, the per-lane delivery shape, and the overflow action every lane applies.
 * - [source] feeds `RuntimeSurfaceInput.eventStampSource`, the session-owned stamp of every
 *   reduced input event and of every surface observation.
 * - [sessionFailureHandler] feeds `RuntimeSurfaceInput.sessionFailureHandler`, the one path an
 *   input overflow takes to fail the session.
 * - [collectorAllocator] and [maxCollectorsPerFlow] feed `RuntimeSurfaceInput.eventCollectorGate`
 *   and `RuntimeSurfaceInput.textInputEventCollectorGate`: the allocator is typed [Any] because the
 *   concrete allocator is runtime-internal, and an implementation must hand the value back to the
 *   runtime unchanged rather than interpret it.
 * - [resources] feeds `RuntimeSurfaceInput.resources` (the payload bounds a drop snapshot is
 *   admitted against) and the drop-transfer budget derived from
 *   `ResourceBudgetPolicy.maxConcurrentDropTransfers`, exactly as the components-side manager
 *   derives it from the same policy. It is delivered here rather than at construction time because
 *   the components branch is handed its policy when its backend constructs the manager for the
 *   session, whereas a primary surface comes from a host factory that never sees the session
 *   policy; a surface that cannot receive it could only guess one, and a guessed budget is not the
 *   session's.
 * - [dropTransferScope] feeds `RuntimeSurfaceInput.dropTransferScope`, the scope a drop transfer
 *   outlives the stimulus that admitted it in.
 * - [diagnostics] feeds the diagnostic channel the reducer's raw-input coordinator reports
 *   through. It is not where the reducer's own reporter comes from: the reporter a failed input
 *   publication is announced on is the reporter of diagnostics that are not session failures, and a
 *   surface obtains that one from the backend that built its host — the Web host session's own
 *   `RuntimeFailureReporter`, adapted to `(Throwable) -> Unit`, is the worked example, and it is the
 *   same kind of value a components-side window manager receives from its backend.
 * - [rawInputPort] feeds `RuntimeSurfaceInput.rawInputCoordinator` when it is non-null, which is
 *   also where `RuntimeSurfaceInput.rawInputCapability` comes from; a session without raw input
 *   delivers `null` and the reducer stays unsupported for it.
 *
 * What the reducer also needs is deliberately not here, because it is not the session's to give: its
 * `RuntimeSurfaceInput.surfaceId` is the identity the runtime itself allocated to the surface, its
 * `textInputPort` and whether drag and drop is available at all are the surface's own activation
 * decisions, and its reporter of diagnostics that are not session failures is the failure reporter
 * of the backend that built the surface's host (a `RuntimeFailureReporter` the Web host session
 * already holds), not the session's [diagnostics] channel. The drop-transfer budget is derived from
 * [resources] instead of being passed because `RuntimeDropTransferBudget` is runtime-internal and
 * cannot appear in this signature.
 */
public interface RuntimePrimarySurfaceConfiguration {
    public fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: Any,
        maxCollectorsPerFlow: Int,
        resources: ResourceBudgetPolicy,
        dropTransferScope: CoroutineScope?,
        diagnostics: (KadreDiagnostic) -> Unit,
        rawInputPort: RawInputPort?,
    )
}

/**
 * Unstable backend SPI pairing a host surface with the mandatory teardown of Kadre's ownership.
 *
 * The host keeps ownership of its native element or view. The supplied teardown only releases
 * Kadre-owned listeners and transitions the Kadre surface to its terminal detached state.
 */
public class RuntimePrimarySurface public constructor(
    public val surface: HostSurface,
    private val teardown: () -> Unit,
) {
    internal fun close() {
        teardown()
    }
}

private fun Throwable?.withSuppressed(cause: Throwable): Throwable = when (this) {
    null -> cause
    else -> apply { addSuppressed(cause) }
}
