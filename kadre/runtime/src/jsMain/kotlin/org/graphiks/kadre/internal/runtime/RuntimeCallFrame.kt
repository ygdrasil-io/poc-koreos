package org.graphiks.kadre.internal.runtime

/**
 * js actual: one field per frame instance.
 *
 * js is mono-threaded, so per-instance state is per-thread state: the value the one program
 * thread stores is the only value there is, exactly as a JVM `ThreadLocal` would hold it.
 */
internal actual class RuntimeCallFrame<T : Any> actual constructor(initial: () -> T) {
    private val initialFrame = initial
    private var value: T? = null

    internal actual fun current(): T = value ?: initialFrame().also { value = it }

    internal actual fun set(value: T) {
        this.value = value
    }

    internal actual fun clear() {
        value = null
    }
}
