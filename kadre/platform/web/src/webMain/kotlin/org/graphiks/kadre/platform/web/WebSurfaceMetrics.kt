package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.toPhysical

/**
 * One immutable readback of the attached element.
 *
 * Values are copied by the target port from DOM primitives; no DOM type crosses this boundary.
 * `logicalWidth`/`logicalHeight` are CSS pixels, [scaleFactor] is the device pixel ratio of the
 * browsing context that owns the element.
 */
internal data class WebSurfaceMetrics(
    val logicalWidth: Double,
    val logicalHeight: Double,
    val scaleFactor: Double,
) {
    init {
        require(logicalWidth.isFinite() && logicalWidth > 0.0) { "logicalWidth must be finite and positive" }
        require(logicalHeight.isFinite() && logicalHeight > 0.0) { "logicalHeight must be finite and positive" }
        require(scaleFactor.isFinite() && scaleFactor > 0.0) { "scaleFactor must be finite and positive" }
    }

    val logicalSize: LogicalSize get() = LogicalSize(logicalWidth, logicalHeight)
    val physicalSize: PhysicalSize get() = logicalSize.toPhysical(scaleFactor)
}

/** A target-owned observation of the attached element, before the session configuration exists. */
internal sealed interface WebSurfaceStimulus {
    data class Metrics(val metrics: WebSurfaceMetrics) : WebSurfaceStimulus

    /** A redraw request admitted by the shared surface, pending its animation frame. */
    data object Redraw : WebSurfaceStimulus
}

/**
 * The session-owned facts a surface needs to publish an event for an observed stimulus, and the
 * session input configuration it needs to build the shared ordinary-input reducer.
 *
 * One immutable record of what the runtime installed: nothing here is derived or defaulted by the
 * surface, so the reducer this surface builds later is configured from the session's own policy
 * and collaborators, exactly like the components-side window manager.
 */
internal class WebSurfaceConfiguration(
    val deliveryPolicy: WindowDeliveryPolicy,
    val stampSource: () -> EventStamp,
    val sessionFailureHandler: (KadreFailure) -> Unit,
    /** The session input delivery policy feeding the reducer's ingress and lane behaviour. */
    val inputDeliveryPolicy: InputDeliveryPolicy,
    /** The session resource budgets feeding the reducer's payload bounds and drop transfer budget. */
    val resources: ResourceBudgetPolicy,
    /** The one session collector allocator the reducer's collector gates are derived from. */
    val collectorAllocator: Any,
    /** The per-flow collector limit of those gates. */
    val maxCollectorsPerFlow: Int,
    /** The session scope a drop transfer outlives its stimulus in. */
    val dropTransferScope: CoroutineScope?,
    /** The session diagnostic channel the reducer reports non-fatal input diagnostics through. */
    val diagnostics: (KadreDiagnostic) -> Unit,
    /** The session-owned raw-input port, or null when the session has no raw input. */
    val rawInputPort: RawInputPort?,
)
