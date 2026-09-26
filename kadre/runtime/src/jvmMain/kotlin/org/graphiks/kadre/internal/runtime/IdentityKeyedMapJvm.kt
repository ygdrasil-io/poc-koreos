package org.graphiks.kadre.internal.runtime

import java.util.IdentityHashMap

/**
 * JVM actual: the map the pipeline used before it became common, unchanged.
 *
 * [java.util.IdentityHashMap] is exactly the semantics the expect declaration documents, so this
 * actual adds nothing to it: no wrapper, no extra allocation, no weaker comparison.
 */
internal actual class IdentityKeyedMap<T> actual constructor() {
    private val delegate = IdentityHashMap<Any, T>()

    internal actual operator fun get(key: Any): T? = delegate[key]

    internal actual operator fun set(key: Any, value: T) {
        delegate[key] = value
    }

    internal actual fun remove(key: Any): T? = delegate.remove(key)

    internal actual fun clear() {
        delegate.clear()
    }
}
