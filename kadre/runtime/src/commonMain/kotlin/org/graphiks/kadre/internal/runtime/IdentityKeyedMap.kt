package org.graphiks.kadre.internal.runtime

/**
 * A map whose keys are compared by reference, never by [Any.equals].
 *
 * Two distinct-but-equal keys are different keys: the first `set` of a reference stores a new
 * entry, any later `set` of that same reference replaces its value. A key that is absent reads
 * back as `null`, and so does a key whose stored value is `null` — callers use this map for
 * opaque identities they hold, not for nullable values they must distinguish.
 *
 * This is the common replacement for `java.util.IdentityHashMap`, which the ordinary-input
 * pipeline uses to key a touch or a peer by the native identity the backend hands it. The
 * observable semantics are those of `java.util.IdentityHashMap`: `get`/`set`/`remove`/`clear`
 * keyed by reference, `remove` returning the value it dropped and reporting absence as `null`.
 */
internal expect class IdentityKeyedMap<T>() {
    /** The value stored under [key] by reference, or `null` when no such reference is present. */
    internal operator fun get(key: Any): T?

    /** Stores [value] under [key] by reference, replacing any value already held by that reference. */
    internal operator fun set(key: Any, value: T)

    /** Drops [key] by reference and returns the value it held, or `null` when it was absent. */
    internal fun remove(key: Any): T?

    /** Drops every entry. */
    internal fun clear()
}
