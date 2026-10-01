package org.graphiks.kadre.internal.runtime

internal expect class RuntimeLock()

internal expect inline fun <T> RuntimeLock.withLock(action: () -> T): T

/**
 * Whether the caller holds this lock through an enclosing [withLock].
 *
 * js and wasmJs are mono-threaded, so the answer is always `true` there — the event loop is the
 * only holder the lock can have.
 */
internal expect fun RuntimeLock.isHeldByCurrentThread(): Boolean
