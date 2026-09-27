package org.graphiks.kadre.internal.runtime

/**
 * wasmJs actual: `false` for every throwable, because wasmJs has no `LinkageError` type.
 *
 * See [isLinkageFailure] for the contract. Answering `false` is the exact answer on a target
 * where the condition cannot occur; answering `true` would be an invention.
 */
internal actual fun Throwable.isLinkageFailure(): Boolean = false
