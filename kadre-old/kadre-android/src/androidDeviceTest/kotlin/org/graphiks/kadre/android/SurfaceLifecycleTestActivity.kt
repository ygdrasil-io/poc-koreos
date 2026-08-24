package org.graphiks.kadre.android

import org.graphiks.kadre.core.ApplicationHandler

class SurfaceLifecycleTestActivity : KadreActivity() {
    override fun createHandler(): ApplicationHandler =
        checkNotNull(handlerFactory) { "Surface lifecycle test handler factory is not configured" }.invoke()

    override fun onDestroy() {
        val changingConfigurations = isChangingConfigurations
        try {
            super.onDestroy()
        } finally {
            if (!changingConfigurations) {
                handlerFactory = null
            }
        }
    }

    companion object {
        @Volatile
        var handlerFactory: (() -> ApplicationHandler)? = null
    }
}
