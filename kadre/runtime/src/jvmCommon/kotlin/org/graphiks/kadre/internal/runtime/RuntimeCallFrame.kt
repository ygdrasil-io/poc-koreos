package org.graphiks.kadre.internal.runtime

/**
 * JVM actual: a `ThreadLocal` created with `withInitial`, exactly what the window manager used
 * before the lift.
 */
internal actual class RuntimeCallFrame<T : Any> actual constructor(initial: () -> T) {
    private val frame = ThreadLocal.withInitial(initial)

    internal actual fun current(): T = frame.get()

    internal actual fun set(value: T) {
        frame.set(value)
    }

    internal actual fun clear() {
        frame.remove()
    }
}
