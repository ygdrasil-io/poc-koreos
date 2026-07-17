package org.graphiks.kadre.android

import org.graphiks.kadre.core.ApplicationHandler

class SurfaceLifecycleTestActivity : KadreActivity() {
    override fun createHandler(): ApplicationHandler =
        checkNotNull(handlerFactory) { "Surface lifecycle test handler factory is not configured" }.invoke()

    override fun onDestroy() {
        try {
            super.onDestroy()
        } finally {
            handlerFactory = null
        }
    }

    companion object {
        @Volatile
        var handlerFactory: (() -> ApplicationHandler)? = null
    }
}
