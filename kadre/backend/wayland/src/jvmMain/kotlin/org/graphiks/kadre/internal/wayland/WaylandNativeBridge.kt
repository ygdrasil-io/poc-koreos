package org.graphiks.kadre.internal.wayland

/**
 * Résultat d'un flush `wl_display_flush` : `Done` (rc >= 0, y compris file
 * vide) ; `Eagain` (rc < 0 — sans capture d'errno côté kffi, un échec de
 * flush est traité comme EAGAIN : la pompe surveille alors l'écriture, et
 * une connexion réellement rompue est constatée par l'appel suivant).
 */
internal enum class WaylandFlushOutcome { Done, Eagain }

/**
 * Pont vers la connexion Wayland (tranche 1 : connexion par nom de socket,
 * registry durable, pompe). Seule couche autorisée à toucher les handles
 * kffi (audit `WAYLAND-PHASE-0-KFFI-AUDIT.md`, domaines 1-3). L'implémentation
 * réelle est [KffiWaylandBridge] ; les tests O2 injectent un fake.
 *
 * Ordre de teardown (F3) : quiescence des listeners (révocation des owners)
 * PUIS [close] (déconnexion du display, libération de l'arène d'upcalls).
 */
internal interface WaylandNativeBridge : AutoCloseable {
    /** fd du `wl_display`, pour le poll de la pompe. */
    val displayFd: Int

    /** Snapshot durable des globals connus (nom d'interface, version annoncée). */
    fun globals(): List<WaylandGlobal>

    /** Roundtrip bloquant — interdit pendant une lecture préparée. */
    fun roundtrip(): Boolean

    /** Vide les événements déjà reçus ; `false` = erreur display (F2). */
    fun dispatchPending(): Boolean

    /** `wl_display_prepare_read` : 0 = préparée, -1 = file non vide (chemin EAGAIN). */
    fun prepareRead(): Int

    /** Termine une lecture préparée ; `false` = erreur display (F2). */
    fun readEvents(): Boolean

    /** Annule une lecture préparée (chemin d'arrêt, réveil applicatif). */
    fun cancelRead()

    fun flush(): WaylandFlushOutcome

    /** Une erreur display a été constatée (toute primitive renvoyant -1). */
    fun connectionLost(): Boolean

    override fun close()
}

/** Échec de connexion (F1-1) — [code] est un identifiant stable ASCII. */
internal class WaylandConnectException(val code: String, cause: Throwable? = null) : Exception(cause)
