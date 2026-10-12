package org.graphiks.kadre.internal.wayland

import java.lang.foreign.MemorySegment

/**
 * Fenêtre native vue par le driver (port pattern) : l'implémentation réelle
 * adapte la facade managée kffi, les tests O2 injectent un faux. Toutes les
 * méthodes s'exécutent sur le thread pompe.
 */
internal interface WaylandNativeWindow : AutoCloseable {
    public val width: Int
    public val height: Int

    /** Vue directe sur le contenu ARGB8888 (stride = width × 4). */
    public fun content(): MemorySegment

    /** Commit initial (sans buffer) : déclenche le configure côté compositor. */
    public fun commitInitial()

    /** Configure de l'xdg_surface, livré avec le serial à acquitter. */
    public fun onConfigure(listener: (serial: Int) -> Unit)

    /** Acknowledge du dernier configure observé. */
    public fun ackLatestConfigure()

    /** Événements du toplevel : configure (taille proposée, états) et close. */
    public fun onToplevelEvents(
        onConfigure: (width: Int, height: Int, states: List<Int>) -> Unit,
        onClose: () -> Unit,
    )

    /** Observateur d'entrée sur un output (diagnostic, voir registre phase 2). */
    public fun onEnter(listener: () -> Unit)

    /** Arme un callback de frame ; il tire à la prochaine présentation. */
    public fun onNextFrame(listener: () -> Unit)

    public fun setTitle(title: String)

    public fun setAppId(appId: String)
}
