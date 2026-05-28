package io.ygdrasil.koreos.samples.hellotouchandroid

import android.app.Application
import io.ygdrasil.koreos.EventLoop

class HelloTouchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLoop().runApp(HelloTouchHandler())
    }
}
