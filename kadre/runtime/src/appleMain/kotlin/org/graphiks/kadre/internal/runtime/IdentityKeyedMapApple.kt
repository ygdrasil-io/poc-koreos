package org.graphiks.kadre.internal.runtime

/**
 * Apple actual: a linear identity scan, mirroring the js actual.
 *
 * Kotlin/Native has no `IdentityHashMap`; the map is small and keyed by opaque references the
 * backend handed out, so a reference-identity scan carries the exact observable semantics of
 * `java.util.IdentityHashMap` without inventing a hash table keyed by identity.
 */
internal actual class IdentityKeyedMap<T> actual constructor() {
    private val entries = mutableListOf<Pair<Any, T>>()

    internal actual operator fun get(key: Any): T? =
        entries.firstOrNull { it.first === key }?.second

    internal actual operator fun set(key: Any, value: T) {
        remove(key)
        entries.add(key to value)
    }

    internal actual fun remove(key: Any): T? {
        val index = entries.indexOfFirst { it.first === key }
        if (index < 0) return null
        val removed = entries[index].second
        entries.removeAt(index)
        return removed
    }

    internal actual fun clear() = entries.clear()
}
