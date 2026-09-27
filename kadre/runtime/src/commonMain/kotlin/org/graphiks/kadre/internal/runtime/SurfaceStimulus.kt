package org.graphiks.kadre.internal.runtime

import kotlin.jvm.JvmInline

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical

/** Opaque generation echoed by the backend when it consumes a redraw command. */
@JvmInline
public value class SurfaceRedrawGeneration internal constructor(public val value: Long) {
    init {
        require(value >= 0L) { "value must be non-negative" }
    }

    public companion object {
        /** Reconstructs the opaque generation echoed by a native backend. */
        public fun fromNative(value: Long): SurfaceRedrawGeneration = SurfaceRedrawGeneration(value)
    }
}

/** Immutable effective metrics captured together by the native backend. */
public data class SurfaceMetrics(
    public val logicalSize: LogicalSize,
    public val physicalSize: PhysicalSize,
    public val scaleFactor: Double,
    public val safeAreaInsets: LogicalInsets,
) {
    init {
        require(scaleFactor.isFinite() && scaleFactor > 0.0) {
            "scaleFactor must be finite and positive"
        }
        require(physicalSize == logicalSize.toPhysical(scaleFactor)) {
            "physicalSize must be derived from logicalSize and scaleFactor"
        }
    }
}

/**
 * Complete immutable ingress understood by the runtime.
 *
 * Backends may safely submit duplicate and late values. The runtime performs deduplication,
 * revision allocation and terminal rejection.
 */
public sealed interface SurfaceStimulus {
    public val surfaceId: SurfaceId

    public data class MetricsChanged(
        override val surfaceId: SurfaceId,
        public val metrics: SurfaceMetrics,
    ) : SurfaceStimulus

    public data class FocusChanged(
        override val surfaceId: SurfaceId,
        public val focus: SurfaceFocus,
    ) : SurfaceStimulus

    public data class VisibilityChanged(
        override val surfaceId: SurfaceId,
        public val visibility: SurfaceVisibility,
        public val occlusion: SurfaceOcclusion,
    ) : SurfaceStimulus

    public data class AppearanceChanged(
        override val surfaceId: SurfaceId,
        public val appearance: SurfaceAppearance,
    ) : SurfaceStimulus

    /** Acknowledges the exact native redraw generation that was consumed. */
    public data class RedrawConsumed(
        override val surfaceId: SurfaceId,
        public val generation: SurfaceRedrawGeneration,
    ) : SurfaceStimulus

    /** One immutable keyboard observation. Identity, stamps and revisions stay runtime-owned. */
    public data class KeyChanged(
        override val surfaceId: SurfaceId,
        public val physicalKey: PhysicalKey,
        public val logicalKey: LogicalKey,
        public val location: KeyLocation,
        public val keyState: KeyState,
        public val repeat: Boolean,
        public val modifiers: KeyboardModifiers,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus {
        init {
            require(keyState == KeyState.Pressed || !repeat) { "a key release cannot repeat" }
        }
    }

    /**
     * Reports which native input observers have been structurally installed for this surface.
     *
     * This is a capability observation, not an input packet: it neither fabricates an input
     * event nor asserts that a callback is currently delivering native input.
     */
    public data class InputObservationChanged(
        override val surfaceId: SurfaceId,
        public val keyboardInstalled: Boolean,
        public val pointerInstalled: Boolean,
        public val touchInstalled: Boolean = false,
        public val gestureKinds: Set<GestureKind> = emptySet(),
    ) : SurfaceStimulus

    /**
     * One touch observation whose backend token is meaningful only for the lifetime of the
     * native contact. The runtime assigns and owns the public touch identity.
     */
    public data class TouchChanged(
        override val surfaceId: SurfaceId,
        public val nativeIdentity: Any,
        public val phase: TouchPhase,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One backend-normalized gesture observation; stamps and revisions stay runtime-owned. */
    public data class Gesture(
        override val surfaceId: SurfaceId,
        public val kind: GestureKind,
        public val phase: TouchPhase,
        public val delta: LogicalDelta? = null,
        public val scale: Double? = null,
        public val rotationRadians: Double? = null,
        public val pressure: Double? = null,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One pointer-entry observation; the runtime assigns the public [PointerId]. */
    public data class PointerEntered(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One pointer-motion observation; the runtime retains pointer identity and state. */
    public data class PointerMoved(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        public val delta: LogicalDelta,
        public val pressure: Double?,
        public val pen: PenState?,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One pointer-button observation. */
    public data class PointerButtonChanged(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val button: PointerButton,
        public val buttonState: PointerButtonState,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public val pen: PenState?,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** One pointer-exit observation; its last position comes from runtime state. */
    public data class PointerLeft(
        override val surfaceId: SurfaceId,
        public val kind: PointerKind,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /**
     * One scroll observation. Equal [coalescingBoundary] values may merge; a backend changes it
     * whenever its native phase or momentum boundary changes. The boundary itself is not public
     * input state.
     */
    public data class Scroll(
        override val surfaceId: SurfaceId,
        public val delta: ScrollDelta,
        public val coalescingBoundary: Long,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus {
        init {
            require(coalescingBoundary >= 0L) { "coalescingBoundary must be non-negative" }
        }
    }

    /** One movement for the runtime-owned offer that the backend was synchronously allowed to retain. */
    public data class DropMoved(
        override val surfaceId: SurfaceId,
        public val offerId: DropOfferId,
        public val position: LogicalPoint,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** Native drag exit for a runtime-owned offer. */
    public data class DropExited(
        override val surfaceId: SurfaceId,
        public val offerId: DropOfferId,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** Native drop completion after its synchronous acceptance. */
    public data class DropPerformed(
        override val surfaceId: SurfaceId,
        public val offerId: DropOfferId,
        public val position: LogicalPoint,
        public val deviceId: DeviceId? = null,
    ) : SurfaceStimulus

    /** Closes all ingress while preserving the last effective snapshot. */
    public data class Detached(override val surfaceId: SurfaceId) : SurfaceStimulus
}

internal sealed interface FlowTerminal {
    data object Closed : FlowTerminal
    data class Failed(val failure: KadreFailure) : FlowTerminal
}

internal sealed interface QueueOfferResult {
    data object Accepted : QueueOfferResult
    data class Dropped(val latestWasDropped: Boolean) : QueueOfferResult
    data object DiscreteOverflow : QueueOfferResult
    data class ContinuousOverflow(val action: ContinuousOverflowAction) : QueueOfferResult
}
