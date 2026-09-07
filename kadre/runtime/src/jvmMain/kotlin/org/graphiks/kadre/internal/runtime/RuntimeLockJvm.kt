package org.graphiks.kadre.internal.runtime

internal actual class RuntimeLock actual constructor() {
    val monitor = Any()
}

internal actual inline fun <T> RuntimeLock.withLock(action: () -> T): T = synchronized(monitor, action)
