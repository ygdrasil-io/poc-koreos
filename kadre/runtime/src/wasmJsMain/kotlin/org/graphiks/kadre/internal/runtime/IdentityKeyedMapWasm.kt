package org.graphiks.kadre.internal.runtime

/**
 * wasmJs actual: a linear scan over the entries, comparing keys with `===`.
 *
 * Same reasoning as the js actual: `java.util.IdentityHashMap` is JVM-only and wasmJs has no hash
 * map keyed by reference. `===` is reference identity for objects, which is the only kind of key
 * the pipeline stores (native contact and peer identities), and the counts stay tiny on a
 * single-threaded wasm main thread, so a scan is the honest implementation rather than a weaker
 * one: a value-keyed map would merge two distinct native identities.
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
