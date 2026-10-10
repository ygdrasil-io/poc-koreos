package org.graphiks.kadre.internal.runtime

/**
 * Apple actual: `false` for every throwable, exactly like js and wasmJs.
 *
 * Kotlin/Native exposes no equivalent of `LinkageError`, and Objective-C interop failures do not
 * reach Kotlin as catchable exceptions, so there is nothing to classify. Answering `false` is the
 * exact answer on a target where the condition cannot be observed; widening it would silently
 * swallow real defects.
 */
internal actual fun Throwable.isLinkageFailure(): Boolean = false
