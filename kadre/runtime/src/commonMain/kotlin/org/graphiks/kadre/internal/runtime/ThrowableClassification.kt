package org.graphiks.kadre.internal.runtime

/**
 * Whether this throwable is a linkage failure: the kind of error a broken native or generated
 * bridge raises when the code it links against is missing or incompatible.
 *
 * The ordinary-input pipeline reports such a failure instead of letting it escape, so this
 * classification carries policy and must stay exact per target — never widened to "an Error" or
 * "an unexpected throwable", which would silently swallow real defects.
 *
 * Per target:
 * - jvm: `true` for a `LinkageError`, `false` for anything else.
 * - js, wasmJs: `false` for every throwable. Neither target has a `LinkageError` type at all
 *   (the closest thing is the stdlib-internal `IrLinkageError`), so there is nothing to
 *   classify: a browser build reports an ordinary failure where the JVM would report a broken
 *   bridge, and never claims a linkage failure it cannot observe.
 * - apple (ios, tvos): `false` for every throwable, exactly like js and wasmJs. Kotlin/Native
 *   exposes no `LinkageError`, and Objective-C interop failures do not reach Kotlin as catchable
 *   exceptions. Even the initialiser-failure error Kotlin/Native raises for a failed file or
 *   class initialiser (`FileFailedToInitializeException`) is an initialisation failure, not a
 *   linkage failure, so it stays classified `false`.
 */
internal expect fun Throwable.isLinkageFailure(): Boolean
