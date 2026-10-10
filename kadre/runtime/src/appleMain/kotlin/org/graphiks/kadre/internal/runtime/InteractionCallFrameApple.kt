package org.graphiks.kadre.internal.runtime

import kotlin.native.concurrent.ThreadLocal
import org.graphiks.kadre.surface.SurfaceId

/**
 * Apple actual: one frame slot per native thread, through the `@ThreadLocal` annotation — the
 * native counterpart of the JVM actual's `ThreadLocal<SurfaceId?>`.
 *
 * The state stays shared by every handler of one thread — a nested dispatch on another surface
 * overwrites the frame, which is what keeps a context retained from an outer callback
 * `WrongSurface` during that nested callback.
 */
@ThreadLocal
private var activeFrameSurface: SurfaceId? = null

internal actual class InteractionCallFrame actual constructor() {
    internal actual fun current(): SurfaceId? = activeFrameSurface

    internal actual fun set(surfaceId: SurfaceId?) {
        activeFrameSurface = surfaceId
    }

    internal actual fun clear() {
        activeFrameSurface = null
    }
}
