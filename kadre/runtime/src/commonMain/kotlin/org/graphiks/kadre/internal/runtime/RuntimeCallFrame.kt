package org.graphiks.kadre.internal.runtime

/**
 * Per-thread storage for one value, created lazily from [initial] — the portable counterpart of
 * `ThreadLocal.withInitial`, extracted for the window manager's deferred close-work bookkeeping.
 *
 * The window manager defers owner closes and native close dispatches onto the queue of the
 * thread that prepared them while that thread held the manager monitor, and the same thread
 * drains its queue after its outermost critical section. The queue is genuinely per-thread
 * state, not a confinement marker: a concurrent reader must never steal backend work, as it may
 * hold an unrelated callback/native lock, so the state cannot live under the manager lock
 * either. See `RuntimeWindowManager.drainCloseWork`.
 *
 * Per target:
 * - jvm: a `ThreadLocal` created with `withInitial`, unchanged from the pre-lift manager.
 * - js, wasmJs: one field per frame instance. Both targets are mono-threaded, so per-instance
 *   state is per-thread state.
 * - apple (ios, tvos): one `@ThreadLocal` slot per native thread, keyed by frame instance — the
 *   native counterpart of the JVM `ThreadLocal`, real per-thread storage on real native threads.
 */
internal expect class RuntimeCallFrame<T : Any>(initial: () -> T) {
    /** The value stored for the current thread, created from [initial] on first access. */
    internal fun current(): T

    /** Replaces the value stored for the current thread. */
    internal fun set(value: T)

    /** Clears the value stored for the current thread; the next [current] recreates it. */
    internal fun clear()
}
