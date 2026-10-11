package org.graphiks.kadre.internal.runtime

import kotlin.native.concurrent.ThreadLocal

/**
 * Apple actual: one slot per native thread, through the `@ThreadLocal` annotation — the native
 * counterpart of the JVM actual's `ThreadLocal.withInitial`.
 *
 * The annotation only decorates top-level state, and the frame is generic, so each frame
 * instance registers a private identity key and stores its per-thread value in one shared
 * per-thread map. Two frames never collide (distinct keys), and two threads never collide (each
 * thread initialises its own map).
 */
@ThreadLocal
private val runtimeCallFrameSlots = HashMap<Any, Any?>()

internal actual class RuntimeCallFrame<T : Any> actual constructor(initial: () -> T) {
    private val key = Any()
    private val initialFrame = initial

    internal actual fun current(): T {
        @Suppress("UNCHECKED_CAST")
        val stored = runtimeCallFrameSlots[key] as T?
        if (stored != null) return stored
        val created = initialFrame()
        runtimeCallFrameSlots[key] = created
        return created
    }

    internal actual fun set(value: T) {
        runtimeCallFrameSlots[key] = value
    }

    internal actual fun clear() {
        runtimeCallFrameSlots.remove(key)
    }
}
