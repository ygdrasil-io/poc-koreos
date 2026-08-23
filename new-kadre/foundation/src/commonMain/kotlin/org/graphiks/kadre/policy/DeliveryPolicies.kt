package org.graphiks.kadre.policy

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

public data class ExecutionPolicy(
    public val priority: ExecutionPriority,
    public val shutdownTimeout: Duration,
) {
    init {
        requireFinitePositive(shutdownTimeout, "shutdownTimeout")
    }
}

public enum class ExecutionPriority { Balanced, LatencyFirst, Throughput }

public data class EventDeliveryPolicy(
    public val ingressCapacity: Int,
    public val collectorCapacity: Int,
    public val ingressOverflow: IngressOverflowAction,
    public val collectorOverflow: CollectorOverflowAction,
) {
    init {
        require(ingressCapacity > 0) { "ingressCapacity must be positive" }
        require(collectorCapacity > 0) { "collectorCapacity must be positive" }
    }
}

public sealed interface ContinuousDelivery {
    public data object Latest : ContinuousDelivery
    public data object Coalesced : ContinuousDelivery

    public data class Buffered(
        public val capacity: Int,
        public val onOverflow: ContinuousOverflowAction,
    ) : ContinuousDelivery {
        init {
            require(capacity > 0) { "capacity must be positive" }
        }
    }
}

public sealed interface FrameDelivery {
    public data object Latest : FrameDelivery

    public data class Buffered(
        public val capacity: Int,
        public val onOverflow: ContinuousOverflowAction,
    ) : FrameDelivery {
        init {
            require(capacity > 0) { "capacity must be positive" }
        }
    }
}

public enum class IngressOverflowAction { CloseSource, FailSession }

public enum class CollectorOverflowAction { CancelSlowCollector, CloseSource, FailSession }

public enum class ContinuousOverflowAction {
    DropOldestAndReport,
    DropLatestAndReport,
    CloseSource,
    FailSession,
}

public class SlowCollectorCancellationException internal constructor(message: String) :
    CancellationException(message)

public data class InputDeliveryPolicy(
    public val discreteEvents: EventDeliveryPolicy,
    public val pointerMotion: ContinuousDelivery,
    public val touchMotion: ContinuousDelivery,
    public val scroll: ContinuousDelivery,
    public val gestureChanges: ContinuousDelivery,
    public val gamepadChanges: ContinuousDelivery,
)

public data class WindowDeliveryPolicy(
    public val discreteEvents: EventDeliveryPolicy,
    public val geometryChanges: ContinuousDelivery,
    public val redrawRequests: ContinuousDelivery,
)

public data class CaptureDeliveryPolicy(
    public val events: EventDeliveryPolicy,
    public val frames: FrameDelivery,
    public val maxBufferedBytesPerSession: Long,
) {
    init {
        require(maxBufferedBytesPerSession > 0) { "maxBufferedBytesPerSession must be positive" }
    }
}

internal fun requireFinitePositive(value: Duration, name: String) {
    require(value.isFinite() && value.isPositive()) { "$name must be finite and positive" }
}
