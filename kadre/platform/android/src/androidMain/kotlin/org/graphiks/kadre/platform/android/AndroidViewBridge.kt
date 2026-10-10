package org.graphiks.kadre.platform.android

import android.view.View

/**
 * Installe les bridges Kadre sur la View hôte et les retire exactement une fois.
 * Chaque installation est journalisée dans [state] ; `teardown` la vide — la
 * vérification zéro-résidu de l'émulateur (AND-001) s'appuie sur ce journal.
 */
internal class AndroidViewBridge private constructor(
    private val view: View,
    private val state: AndroidAttachState,
) {
    private var tornDown = false

    fun teardown() {
        if (tornDown) return
        tornDown = true
        view.tag = null // placeholder phase 0 : aucun listener résiduel ne doit subsister
        state.markTerminated()
    }

    companion object {
        fun install(view: View, state: AndroidAttachState): AndroidViewBridge {
            state.recordBridge("view-bridge")
            return AndroidViewBridge(view, state)
        }
    }
}
