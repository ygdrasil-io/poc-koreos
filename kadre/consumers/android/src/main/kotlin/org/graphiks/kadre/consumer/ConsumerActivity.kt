package org.graphiks.kadre.consumer

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

/**
 * Host minimal : pose une View attachée que les tests instrumentés revendiquent.
 *
 * `ComponentActivity` (et plus `android.app.Activity`) : la forme Activity d'`attachKadre`
 * (phase 1) prend un récepteur `ComponentActivity`, et la forme View prend un
 * `LifecycleOwner` — les deux rôles exigent le lifecycle androidx.
 */
class ConsumerActivity : ComponentActivity() {
    lateinit var consumerView: FrameLayout
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumerView = FrameLayout(this)
        setContentView(consumerView)
    }
}
