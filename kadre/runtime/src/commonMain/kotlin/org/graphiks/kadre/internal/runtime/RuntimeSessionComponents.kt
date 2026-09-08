package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.policy.InputDeliveryPolicy
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
    private val closeAction: () -> Unit,
    primarySurface: RuntimePrimarySurface?,
) : AutoCloseable {
    public constructor(
        windows: WindowManager,
        closeAction: () -> Unit = {},
    ) : this(windows, closeAction, null)

    public constructor(
        windows: WindowManager,
        primarySurface: RuntimePrimarySurface,
        closeAction: () -> Unit = {},
    ) : this(windows, closeAction, primarySurface)

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
            try {
                closePrimarySurface?.invoke()
            } finally {
                closeAction()
            }
        }
    }

    internal fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: Any,
        maxCollectorsPerFlow: Int,
        dropTransferScope: CoroutineScope,
    ) {
        (windows as? RuntimeSessionWindowManager)?.installSessionConfiguration(
            deliveryPolicy,
            inputDeliveryPolicy,
            source,
            sessionFailureHandler,
            collectorAllocator,
            maxCollectorsPerFlow,
            dropTransferScope,
        )
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
    )
}

internal object UnsupportedRuntimeSessionComponentsFactory : RuntimeSessionComponentsFactory {
    override fun create(sessionId: SessionId, rootScope: CoroutineScope): RuntimeSessionComponents =
        RuntimeSessionComponents(UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId))
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
