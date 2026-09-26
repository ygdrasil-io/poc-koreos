package org.graphiks.kadre.internal.runtime

import java.util.IdentityHashMap

/**
 * JVM actual: the map the pipeline used before it became common, unchanged.
 *
 * [java.util.IdentityHashMap] is exactly the semantics the expect declaration documents, so this
 * actual adds nothing to it: no wrapper, no extra allocation, no weaker comparison.
 *
 * It inherits the identity map rather than holding one, so the live map a caller observes is the
 * reference-keyed `java.util.Map` the ordinary-input contract tests exercise (reference-keyed
 * `get`/`set`/`remove`/`clear` with the map contract around them).
 */
internal actual class IdentityKeyedMap<T> actual constructor() : IdentityHashMap<Any, T>() {
    actual override operator fun get(key: Any): T? = super.get(key)

    actual operator fun set(key: Any, value: T) {
        put(key, value)
    }

    actual override fun remove(key: Any): T? = super.remove(key)

    actual override fun clear() {
        super.clear()
    }
}
