/**
 * Bindings FFM pour les fonctions Wayland nécessaires à la gestion de fenêtres.
 *
 * Charge libwayland-client.so.0 et libxkbcommon.so.0 via SymbolLookup.libraryLookup
 * avec un pattern tryCreate (try/catch Throwable) pour que le build passe sur
 * macOS/Windows sans les bibliothèques Wayland installées.
 *
 * Fonctions exposées (libwayland-client) :
 *  - wl_display_connect
 *  - wl_display_disconnect
 *  - wl_display_get_fd
 *  - wl_display_dispatch
 *  - wl_display_dispatch_pending
 *  - wl_display_prepare_read
 *  - wl_display_read_events
 *  - wl_display_cancel_read
 *  - wl_display_flush
 *  - wl_display_get_registry
 *  - wl_registry_add_listener
 *  - wl_registry_bind
 *  - wl_proxy_destroy
 *  - wl_proxy_add_listener
 *  - wl_proxy_marshal_flags (variante xdg_wm_base_get_xdg_surface)
 *
 * Référence : https://wayland.freedesktop.org/docs/html/
 */
package io.ygdrasil.koreos.wayland

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

// ── Lazy loading des bibliothèques ────────────────────────────────────────────

/**
 * Lookup libwayland-client.so.0 — null sur les plateformes non-Wayland.
 *
 * Le try/catch sur Throwable est intentionnel : SymbolLookup.libraryLookup
 * peut lever IllegalArgumentException ou UnsatisfiedLinkError sur macOS/Windows,
 * et on veut que le build reste vert dans tous les cas.
 */
internal val libWaylandClient: SymbolLookup? by lazy {
    try {
        SymbolLookup.libraryLookup("libwayland-client.so.0", Arena.global())
    } catch (e: Throwable) {
        null
    }
}

/**
 * Lookup libxkbcommon.so.0 — null sur les plateformes non-Wayland.
 */
internal val libXkbCommon: SymbolLookup? by lazy {
    try {
        SymbolLookup.libraryLookup("libxkbcommon.so.0", Arena.global())
    } catch (e: Throwable) {
        null
    }
}

private val linker: Linker = Linker.nativeLinker()

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Recherche un symbole dans un SymbolLookup et crée un MethodHandle downcall.
 * Retourne null si le lookup est null ou si le symbole est introuvable.
 */
private fun SymbolLookup?.downcall(name: String, desc: FunctionDescriptor): MethodHandle? {
    this ?: return null
    return this.find(name).map { linker.downcallHandle(it, desc) }.orElse(null)
}

// ── wl_display_connect ────────────────────────────────────────────────────────

/**
 * struct wl_display *wl_display_connect(const char *name);
 *
 * Se connecte au serveur Wayland. Passer NULL pour utiliser WAYLAND_DISPLAY
 * (habituellement "wayland-0"). Retourne un pointeur wl_display* ou NULL en
 * cas d'échec.
 */
internal val wlDisplayConnect: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_connect",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS, // wl_display*
            ValueLayout.ADDRESS, // const char* name (ou NULL)
        )
    )
}

// ── wl_display_disconnect ─────────────────────────────────────────────────────

/**
 * void wl_display_disconnect(struct wl_display *display);
 *
 * Ferme la connexion au serveur Wayland et libère les ressources associées.
 */
internal val wlDisplayDisconnect: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_disconnect",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, // wl_display*
        )
    )
}

// ── wl_display_get_fd ─────────────────────────────────────────────────────────

/**
 * int wl_display_get_fd(struct wl_display *display);
 *
 * Retourne le descripteur de fichier du socket de connexion Wayland.
 * Utile pour intégrer la boucle d'événements Wayland dans un sélecteur (epoll).
 */
internal val wlDisplayGetFd: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_get_fd",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, // int fd
            ValueLayout.ADDRESS,  // wl_display*
        )
    )
}

// ── wl_display_dispatch ───────────────────────────────────────────────────────

/**
 * int wl_display_dispatch(struct wl_display *display);
 *
 * Traite les événements en attente et bloque jusqu'à en recevoir un.
 * Retourne le nombre d'événements traités, ou -1 en cas d'erreur.
 */
internal val wlDisplayDispatch: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_dispatch",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, // int (nb événements ou -1)
            ValueLayout.ADDRESS,  // wl_display*
        )
    )
}

// ── wl_display_dispatch_pending ───────────────────────────────────────────────

/**
 * int wl_display_dispatch_pending(struct wl_display *display);
 *
 * Traite uniquement les événements déjà en file d'attente, sans bloquer.
 * Retourne le nombre d'événements traités, ou -1 en cas d'erreur.
 */
internal val wlDisplayDispatchPending: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_dispatch_pending",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, // int (nb événements ou -1)
            ValueLayout.ADDRESS,  // wl_display*
        )
    )
}

// ── wl_display_prepare_read ───────────────────────────────────────────────────

/**
 * int wl_display_prepare_read(struct wl_display *display);
 *
 * Annonce l'intention de lire des événements depuis le socket Wayland.
 * Doit être suivi de wl_display_read_events() ou wl_display_cancel_read().
 * Retourne 0 en cas de succès, -1 si des événements sont déjà en attente.
 */
internal val wlDisplayPrepareRead: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_prepare_read",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, // int (0 ou -1)
            ValueLayout.ADDRESS,  // wl_display*
        )
    )
}

// ── wl_display_read_events ────────────────────────────────────────────────────

/**
 * int wl_display_read_events(struct wl_display *display);
 *
 * Lit les événements depuis le socket et les place dans la file d'attente.
 * À appeler après wl_display_prepare_read(). Retourne 0 ou -1.
 */
internal val wlDisplayReadEvents: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_read_events",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, // int (0 ou -1)
            ValueLayout.ADDRESS,  // wl_display*
        )
    )
}

// ── wl_display_cancel_read ────────────────────────────────────────────────────

/**
 * void wl_display_cancel_read(struct wl_display *display);
 *
 * Annule l'intention de lecture déclarée par wl_display_prepare_read().
 * À appeler si on décide de ne pas lire (ex. sélecteur timeout).
 */
internal val wlDisplayCancelRead: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_cancel_read",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, // wl_display*
        )
    )
}

// ── wl_display_flush ──────────────────────────────────────────────────────────

/**
 * int wl_display_flush(struct wl_display *display);
 *
 * Envoie les données en attente dans le tampon d'envoi vers le serveur.
 * Retourne le nombre d'octets envoyés, ou -1 en cas d'erreur (errno = EAGAIN
 * si le socket est non-bloquant et le tampon plein).
 */
internal val wlDisplayFlush: MethodHandle? by lazy {
    libWaylandClient.downcall(
        "wl_display_flush",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, // int (octets envoyés ou -1)
            ValueLayout.ADDRESS,  // wl_display*
        )
    )
}

// ── wl_display_get_registry ───────────────────────────────────────────────────

/**
 * struct wl_registry *wl_display_get_registry(struct wl_display *display);
 *
 * Retourne le registre global Wayland, point d'entrée pour lier des interfaces
 * globales (wl_compositor, xdg_wm_base, etc.) via wl_registry_bind.
 */
internal val wlDisplayGetRegistry: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_display_get_registry",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

// ── wl_registry_add_listener ──────────────────────────────────────────────────

/**
 * int wl_registry_add_listener(struct wl_registry *registry,
 *     const struct wl_registry_listener *listener, void *data);
 *
 * Enregistre un listener pour les événements du registre (global/global_remove).
 * Retourne 0 en cas de succès, -1 en cas d'erreur.
 */
internal val wlRegistryAddListener: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_registry_add_listener",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

// ── wl_registry_bind ──────────────────────────────────────────────────────────

/**
 * void *wl_registry_bind(struct wl_registry *registry, uint32_t name,
 *     const struct wl_interface *interface, uint32_t version);
 *
 * Lie une interface globale par son nom numérique (annoncé via wl_registry.global).
 * Retourne un proxy Wayland opaque (wl_compositor*, xdg_wm_base*, etc.).
 */
internal val wlRegistryBind: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_registry_bind",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
}

// ── wl_proxy_destroy ──────────────────────────────────────────────────────────

/**
 * void wl_proxy_destroy(struct wl_proxy *proxy);
 *
 * Détruit un proxy Wayland et libère ses ressources. À appeler avant de
 * déconnecter l'affichage pour tout proxy non détruit par une opération destroy.
 */
internal val wlProxyDestroy: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_destroy",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
}

// ── wl_proxy_add_listener ─────────────────────────────────────────────────────

/**
 * int wl_proxy_add_listener(struct wl_proxy *proxy,
 *     void (**implementation)(void), void *data);
 *
 * Associe une table de fonctions (vtable) et des données utilisateur à un proxy.
 * Utilisé pour recevoir les événements côté client (configure, ping, etc.).
 * Retourne 0 en cas de succès, -1 en cas d'erreur.
 */
internal val wlProxyAddListener: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_add_listener",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
}

// ── wl_proxy_marshal_flags (variante xdg_wm_base_get_xdg_surface) ────────────

/**
 * struct wl_proxy *wl_proxy_marshal_flags(struct wl_proxy *proxy,
 *     uint32_t opcode, const struct wl_interface *interface,
 *     uint32_t version, uint32_t flags,
 *     struct wl_proxy *new_id, struct wl_proxy *surface);
 *
 * Variante à 7 arguments pour wl_proxy_marshal_flags utilisée par
 * xdg_wm_base_get_xdg_surface (opcode XDG_WM_BASE_GET_XDG_SURFACE).
 *
 * La fonction C est variadique ; on bind ici la forme concrète à 2 arguments
 * supplémentaires (new_id + surface) qui correspond exactement à cet opcode.
 */
internal val wlProxyMarshalFlagsGetXdgSurface: MethodHandle? by lazy {
    libWaylandClient.downcall("wl_proxy_marshal_flags",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,   // proxy
            ValueLayout.JAVA_INT,  // opcode
            ValueLayout.ADDRESS,   // interface
            ValueLayout.JAVA_INT,  // version
            ValueLayout.JAVA_INT,  // flags
            ValueLayout.ADDRESS,   // arg: new_id
            ValueLayout.ADDRESS,   // arg: surface
        ))
}
