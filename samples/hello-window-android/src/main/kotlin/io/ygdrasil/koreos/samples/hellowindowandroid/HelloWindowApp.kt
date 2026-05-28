package io.ygdrasil.koreos.samples.hellowindowandroid

import android.app.Application
import io.ygdrasil.koreos.EventLoop

class HelloWindowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLoop().runApp(HelloApp())
    }
}
