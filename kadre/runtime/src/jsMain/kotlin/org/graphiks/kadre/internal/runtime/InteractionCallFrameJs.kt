package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.surface.SurfaceId

/**
 * js actual: one shared field for the whole program.
 *
 * js is mono-threaded and the DOM never re-enters synchronously, so a single field carries the
 * frame exactly as the JVM `ThreadLocal` does per thread: the innermost running callback wins,
 * and the dispatch `finally` restores the previous surface. The field stays shared by every
 * handler, so a context retained from an outer callback still reads `WrongSurface` while another
 * surface's callback runs.
 */
internal actual class InteractionCallFrame actual constructor() {
    private companion object {
        private var activeSurface: SurfaceId? = null
    }

    internal actual fun current(): SurfaceId? = activeSurface

    internal actual fun set(surfaceId: SurfaceId?) {
        activeSurface = surfaceId
    }

    internal actual fun clear() {
        activeSurface = null
    }
}
