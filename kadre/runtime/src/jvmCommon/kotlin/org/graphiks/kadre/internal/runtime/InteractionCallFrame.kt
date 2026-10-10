package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.surface.SurfaceId

/**
 * JVM actual: the shared `ThreadLocal<SurfaceId?>` the interaction handler used before the lift,
 * unchanged. The state stays shared by every handler of one thread — a nested dispatch on another
 * surface overwrites the frame, which is what keeps a context retained from an outer callback
 * `WrongSurface` during that nested callback.
 */
internal actual class InteractionCallFrame actual constructor() {
    private companion object {
        val activeSurface = ThreadLocal<SurfaceId?>()
    }

    internal actual fun current(): SurfaceId? = activeSurface.get()

    internal actual fun set(surfaceId: SurfaceId?) {
        activeSurface.set(surfaceId)
    }

    internal actual fun clear() {
        activeSurface.remove()
    }
}
