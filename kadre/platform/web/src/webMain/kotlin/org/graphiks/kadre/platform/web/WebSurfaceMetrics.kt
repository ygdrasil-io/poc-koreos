package org.graphiks.kadre.platform.web

import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.KadreFailure
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
}

/** The session-owned facts a surface needs to publish an event for an observed stimulus. */
internal class WebSurfaceConfiguration(
    val deliveryPolicy: WindowDeliveryPolicy,
    val stampSource: () -> EventStamp,
    val sessionFailureHandler: (KadreFailure) -> Unit,
)
