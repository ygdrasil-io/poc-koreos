package org.graphiks.kadre.internal.runtime

internal actual class RuntimeLock actual constructor()

internal actual inline fun <T> RuntimeLock.withLock(action: () -> T): T = synchronized(this, action)

internal actual fun RuntimeLock.isHeldByCurrentThread(): Boolean = Thread.holdsLock(this)
