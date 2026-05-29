package io.ygdrasil.koreos.samples.hellotouchandroid

import android.app.Application
import io.ygdrasil.koreos.EventLoop
import io.ygdrasil.koreos.samples.hellotouch.HelloTouchHandler

/**
 * Application Android de démonstration.
 *
 * Instancie [HelloTouchHandler] depuis `:samples:hello-touch` (commonMain)
 * et démarre l'EventLoop Koreos.
 */
class HelloTouchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLoop().runApp(HelloTouchHandler())
    }
}
