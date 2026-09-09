package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionId
import org.graphiks.kadre.diagnostics.KadreDiagnostic
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
    /**
     * Optional session-owned raw-input port consumed by [RuntimeSessionWindowManager].
     *
     * Components retain ownership until their manager has completed configuration. Closing the
     * component therefore closes the port too; implementations must make [RawInputPort.close]
     * idempotent.
     */
    public val rawInputPort: RawInputPort?,
    private val closeAction: () -> Unit,
    primarySurface: RuntimePrimarySurface?,
) : AutoCloseable {
    public constructor(
        windows: WindowManager,
        rawInputPort: RawInputPort? = null,
        closeAction: () -> Unit = {},
    ) : this(windows, rawInputPort, closeAction, null)

    public constructor(
        windows: WindowManager,
        primarySurface: RuntimePrimarySurface,
        rawInputPort: RawInputPort? = null,
        closeAction: () -> Unit = {},
    ) : this(windows, rawInputPort, closeAction, primarySurface)

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

private fun Throwable?.withSuppressed(cause: Throwable): Throwable = when (this) {
    null -> cause
    else -> apply { addSuppressed(cause) }
}
