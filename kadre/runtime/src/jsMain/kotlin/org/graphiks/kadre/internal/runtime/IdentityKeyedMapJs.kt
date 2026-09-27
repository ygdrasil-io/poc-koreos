package org.graphiks.kadre.internal.runtime

/**
 * js actual: a linear scan over the entries, comparing keys with `===`.
 *
 * `java.util.IdentityHashMap` is JVM-only, and js has no hash map keyed by reference. A scan is
 * the honest replacement here rather than a simplified one: `===` on js is reference identity for
 * objects, which is what the pipeline keys by (native contact and peer identities, never value
 * types). The maps hold a handful of live entries on the single-threaded js main thread and this
 * type exposes no iteration, so a scan costs nothing measurable — while a value-keyed map would
 * silently merge two distinct native identities, which is the one thing this type exists to
 * prevent.
 */
internal actual class IdentityKeyedMap<T> actual constructor() {
    private val entries = mutableListOf<Pair<Any, T>>()

    internal actual operator fun get(key: Any): T? {
        val index = indexOfKey(key)
        return if (index >= 0) entries[index].second else null
    }

    internal actual operator fun set(key: Any, value: T) {
        val index = indexOfKey(key)
        if (index >= 0) {
            entries[index] = key to value
        } else {
            entries.add(key to value)
        }
    }

    internal actual fun remove(key: Any): T? {
        val index = indexOfKey(key)
        if (index < 0) return null
        return entries.removeAt(index).second
    }

    internal actual fun clear() {
        entries.clear()
    }

    private fun indexOfKey(key: Any): Int {
        for (index in entries.indices) {
            if (entries[index].first === key) return index
        }
        return -1
    }
}
