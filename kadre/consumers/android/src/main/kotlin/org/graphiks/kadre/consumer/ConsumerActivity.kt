package org.graphiks.kadre.consumer

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout

/** Host minimal : pose une View attachée que le test instrumenté revendique. */
class ConsumerActivity : Activity() {
    lateinit var consumerView: FrameLayout
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumerView = FrameLayout(this)
        setContentView(consumerView)
    }
}
