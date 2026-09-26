package org.graphiks.kadre.internal.runtime

/**
 * jvm actual: the classification the pipeline already performed by catching `LinkageError`.
 *
 * `LinkageError` covers the whole family a broken bridge raises (`NoSuchMethodError`,
 * `UnsatisfiedLinkError`, `ExceptionInInitializerError`, `NoClassDefFoundError`, ...), and `is`
 * keeps the check free of any type-name lookup.
 */
internal actual fun Throwable.isLinkageFailure(): Boolean = this is LinkageError
