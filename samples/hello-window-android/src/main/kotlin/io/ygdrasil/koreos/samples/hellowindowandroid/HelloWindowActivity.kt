package io.ygdrasil.koreos.samples.hellowindowandroid

import io.ygdrasil.koreos.android.AndroidKoreosRuntime
import io.ygdrasil.koreos.android.KoreosActivity
import io.ygdrasil.koreos.core.ApplicationHandler

class HelloWindowActivity : KoreosActivity() {
    override fun createHandler(): ApplicationHandler =
        AndroidKoreosRuntime.currentHandler
            ?: error("EventLoop.runApp() must be called in Application.onCreate() before HelloWindowActivity starts")
}
