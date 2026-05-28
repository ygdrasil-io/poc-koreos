package io.ygdrasil.koreos.samples.hellotouchandroid

import io.ygdrasil.koreos.android.AndroidKoreosRuntime
import io.ygdrasil.koreos.android.KoreosActivity
import io.ygdrasil.koreos.core.ApplicationHandler

class HelloTouchActivity : KoreosActivity() {
    override fun createHandler(): ApplicationHandler =
        AndroidKoreosRuntime.currentHandler
            ?: error("EventLoop.runApp() must be called in Application.onCreate() before HelloTouchActivity starts")
}
