package org.graphiks.kadre.appkit

import org.graphiks.kadre.core.EventLoopProxy

/** Thread-safe proxy backed by the CF owner installed for the running loop. */
internal class AppKitEventLoopProxy private constructor(
    private val owner: CFRunLoopOwner,
) : EventLoopProxy {
    override fun wakeUp() {
        owner.wakeUp()
    }

    companion object {
        fun create(owner: CFRunLoopOwner): AppKitEventLoopProxy =
            AppKitEventLoopProxy(owner)
    }
}
