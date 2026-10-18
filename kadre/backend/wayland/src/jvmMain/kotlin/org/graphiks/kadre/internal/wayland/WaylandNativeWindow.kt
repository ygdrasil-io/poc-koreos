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

    /** Adresses natives (display, surface) pour le lease interop renderer. */
    public val nativeAddresses: Pair<Long, Long>

    /** Vue directe sur le contenu ARGB8888 (stride = width × 4). */
    public fun content(): MemorySegment

    /** Commit initial (sans buffer) : déclenche le configure côté compositor. */
    public fun commitInitial()

    /** Configure de l'xdg_surface, livré avec le serial à acquitter. */
    public fun onConfigure(listener: (serial: Int) -> Unit)

    /** Acknowledge du dernier configure observé. */
    public fun ackLatestConfigure()

    /** Re-commit du buffer courant sans nouvel attach (redraw pacing). */
    public fun commitCurrent()

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

    /** Contraintes xdg (0 = reset), effet au commit suivant. */
    public fun setConstraints(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int)

    /** Fullscreen borderless on/off (sortie Wayland = output au choix du compositor). */
    public fun setFullscreenBorderless(enabled: Boolean)

    /** Move interactif — serial d'un événement d'entrée réel. */
    public fun beginMove(serial: Int)

    /** Resize interactif pour un bitmask d'edges xdg. */
    public fun beginResize(serial: Int, edges: Int)
}
