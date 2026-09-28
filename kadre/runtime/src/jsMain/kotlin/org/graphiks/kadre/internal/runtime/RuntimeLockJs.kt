package org.graphiks.kadre.internal.runtime

internal actual class RuntimeLock actual constructor()

internal actual inline fun <T> RuntimeLock.withLock(action: () -> T): T = action()

internal actual fun RuntimeLock.isHeldByCurrentThread(): Boolean = true
