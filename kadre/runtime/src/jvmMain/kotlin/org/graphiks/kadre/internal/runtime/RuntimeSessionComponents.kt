package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.SessionId
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
public class RuntimeSessionComponents public constructor(
    public val windows: WindowManager,
    private val closeAction: () -> Unit = {},
) : AutoCloseable {
    private val lock = Any()
    private var closed = false

    override public fun close() {
        val shouldClose = synchronized(lock) {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (shouldClose) closeAction()
    }
}

internal object UnsupportedRuntimeSessionComponentsFactory : RuntimeSessionComponentsFactory {
    override fun create(sessionId: SessionId, rootScope: CoroutineScope): RuntimeSessionComponents =
        RuntimeSessionComponents(UnsupportedWindowManager(RuntimeProcessIds::nextWindowRequestId))
}
