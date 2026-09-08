package org.graphiks.kadre.internal.runtime

internal expect class RuntimeLock()

internal expect inline fun <T> RuntimeLock.withLock(action: () -> T): T
