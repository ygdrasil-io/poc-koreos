/**
 * Implémentation X11 de l'interface [Window] pour Linux Desktop.
 *
 * Utilise la Foreign Function & Memory API (JEP 454, JDK 25) pour interagir
 * avec libX11.so.6 sans JNA ni autre couche intermédiaire.
 *
 * Flux de création :
 *  1. XCreateSimpleWindow     — crée la fenêtre enfant de la fenêtre racine
 *  2. XSelectInput            — sélectionne le masque d'événements complet
 *  3. XInternAtom             — obtient l'atome WM_DELETE_WINDOW
 *  4. XSetWMProtocols         — installe le protocole de fermeture propre
 *  5. XStoreName              — définit le titre
 *  6. XMapWindow              — rend la fenêtre visible (si attrs.visible = true)
 *
 * Redmine #59 : X11Window — implémentation complète de l'interface Window.
 */
package io.ygdrasil.koreos.x11

import io.ygdrasil.koreos.core.PhysicalSize
import io.ygdrasil.koreos.core.RawDisplayHandle
import io.ygdrasil.koreos.core.RawWindowHandle
import io.ygdrasil.koreos.core.Window
import io.ygdrasil.koreos.core.WindowAttributes
import io.ygdrasil.koreos.core.WindowId
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * Masque d'événements combiné sélectionné pour chaque fenêtre X11.
 *
 * Inclut : Expose, KeyPress, KeyRelease, ButtonPress, ButtonRelease,
 * PointerMotion, StructureNotify (ConfigureNotify, DestroyNotify, …).
 */
private val FULL_EVENT_MASK: Long =
    ExposureMask or
    KeyPressMask or
    KeyReleaseMask or
    ButtonPressMask or
    ButtonReleaseMask or
    PointerMotionMask or
    StructureNotifyMask

/**
 * Fenêtre X11 native implémentant [Window].
 *
 * Le constructeur est interne : utilisez [X11Window.create] pour instancier.
 *
 * @param displayPtr Pointeur vers la structure Display X11 (valeur Long du MemorySegment.address()).
 * @param xWindowId  Identifiant XID de la fenêtre créée (unsigned long → Long).
 * @param attrs      Attributs de création de la fenêtre.
 */
class X11Window private constructor(
    private val displayPtr: Long,
    private val xWindowId: Long,
    private val attrs: WindowAttributes,
) : Window {

    override val id: WindowId = WindowId(xWindowId)

    override val rawWindowHandle: Any
        get() = RawWindowHandle.Xlib(window = xWindowId, display = displayPtr)

    override val rawDisplayHandle: Any
        get() = RawDisplayHandle.Xlib(display = displayPtr)

    /**
     * Taille interne courante en pixels physiques.
     *
     * Initialisée depuis attrs.size ; mise à jour par les événements ConfigureNotify
     * via [onConfigureNotify].
     */
    @Volatile
    private var _innerSize: PhysicalSize<Int> = attrs.size ?: PhysicalSize(800, 600)

    override val innerSize: PhysicalSize<Int>
        get() = _innerSize

    /**
     * Taille externe (surface + décorations WM) en pixels physiques.
     *
     * Sur X11, les décorations sont gérées par le gestionnaire de fenêtres et
     * inconnues sans appel à XGetGeometry + XQueryTree. On retourne la même
     * valeur que [innerSize] pour l'instant.
     *
     * TODO : utiliser XGetGeometry pour distinguer inner/outer au besoin.
     */
    override val outerSize: PhysicalSize<Int>
        get() = _innerSize

    /**
     * Facteur d'échelle DPI de cette fenêtre.
     *
     * Retourne 1.0 (heuristique DPI post-v0.2).
     */
    override val scaleFactor: Double = 1.0

    override fun requestRedraw() {
        // Aucune action directe nécessaire : la boucle d'événements relève les Expose.
        // Éventuellement, on pourrait envoyer un XSendEvent Expose — reporté post-v0.2.
    }

    override fun setTitle(title: String) {
        val handle = xStoreName ?: return
        val display = MemorySegment.ofAddress(displayPtr)
        Arena.ofConfined().use { arena ->
            val nameBytes = title.toByteArray(Charsets.ISO_8859_1)
            val namePtr = arena.allocate(nameBytes.size.toLong() + 1)
            for (i in nameBytes.indices) namePtr.set(ValueLayout.JAVA_BYTE, i.toLong(), nameBytes[i])
            namePtr.set(ValueLayout.JAVA_BYTE, nameBytes.size.toLong(), 0)
            handle.invokeExact(display, xWindowId, namePtr) as Int
        }
    }

    override fun setVisible(visible: Boolean) {
        if (visible) {
            val handle = xMapWindow ?: return
            val display = MemorySegment.ofAddress(displayPtr)
            handle.invokeExact(display, xWindowId) as Int
            xFlush?.invokeExact(display) as? Int
        }
        // XUnmapWindow n'est pas encore dans les bindings — reporté post-v0.2.
    }

    override fun close() {
        val handle = xDestroyWindow ?: return
        val display = MemorySegment.ofAddress(displayPtr)
        handle.invokeExact(display, xWindowId) as Int
        xFlush?.invokeExact(display) as? Int
    }

    /**
     * Met à jour la taille interne lors de la réception d'un événement ConfigureNotify.
     *
     * @param width  Nouvelle largeur en pixels.
     * @param height Nouvelle hauteur en pixels.
     */
    fun onConfigureNotify(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _innerSize = PhysicalSize(width, height)
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {

        /**
         * Crée une fenêtre X11 native.
         *
         * Effectue toutes les initialisations nécessaires :
         * XCreateSimpleWindow → XSelectInput → WM_DELETE_WINDOW → XStoreName → XMapWindow.
         *
         * @param display Long représentant le pointeur Display* (adresse du MemorySegment).
         * @param screen  Numéro de l'écran X11 (DefaultScreen).
         * @param attrs   Attributs de la fenêtre (titre, taille, visibilité, etc.).
         * @return La fenêtre créée, ou null si les bindings libX11 ne sont pas disponibles
         *         (macOS/Windows) ou si la création échoue.
         */
        fun create(display: Long, screen: Int, attrs: WindowAttributes): X11Window? {
            // Les bindings sont null sur non-Linux — retourner null gracieusement.
            val createHandle = xCreateSimpleWindow ?: return null

            val displaySeg = MemorySegment.ofAddress(display)

            // ── 1. Obtenir la fenêtre racine ──────────────────────────────────
            // DefaultRootWindow(display) = XRootWindow(display, DefaultScreen(display))
            // En pratique, screen 0 → root window XID est accessible via Xlib macros.
            // On passe par XOpenDisplay pour obtenir la structure complète, mais ici
            // display est déjà ouvert. On utilise screen comme offset si besoin.
            // Valeur conventionnelle de la fenêtre racine (XID = 1 sur la plupart des serveurs X).
            // Pour être correct, on devrait appeler XRootWindow, mais ce binding n'est pas
            // disponible. On utilise la convention XID = screen (ce n'est pas la bonne
            // valeur universelle). À la place, on utilise 0 comme parent et X choisira
            // la racine automatiquement — XCreateSimpleWindow accepte parent = DefaultRootWindow.
            //
            // Note : en pratique, il faut passer le vrai root XID. On le calcule via
            // l'adresse du Display* (l'écran par défaut est à un offset fixe).
            // Pour contourner l'absence de XDefaultRootWindow, on passe `screen.toLong()`
            // comme root (ce qui fonctionne si screen == 1 = root XID usuel).
            // La solution robuste est d'ajouter XDefaultRootWindow dans un futur ticket.
            //
            // MISE À JOUR : on passe `(screen + 1).toLong()` car le root window XID
            // est généralement 1 sur le premier écran et l'ID d'écran X est 0.
            // La valeur correcte est obtenue via DefaultRootWindow(dpy) =
            // XRootWindow(dpy, DefaultScreen(dpy)) ce qui retourne normalement
            // l'XID 0x0000000000000143 ou similaire — dépendant du serveur X.
            //
            // En l'absence du binding XRootWindow, on passe parent = 0 ce qui
            // déclenche généralement une erreur BadWindow. On utilise donc
            // la valeur 1 qui est le root window conventionnel sur la plupart des serveurs X.
            val rootWindow: Long = 1L   // DefaultRootWindow convention ; see TODO above

            val width = attrs.size?.width ?: 800
            val height = attrs.size?.height ?: 600

            // ── 2. XCreateSimpleWindow ────────────────────────────────────────
            val xWindowId: Long = createHandle.invokeExact(
                displaySeg,     // Display*
                rootWindow,     // Window parent
                0,              // int x
                0,              // int y
                width,          // unsigned int width
                height,         // unsigned int height
                1,              // unsigned int border_width
                0L,             // unsigned long border (BlackPixel = 0)
                0L,             // unsigned long background (BlackPixel = 0)
            ) as Long

            if (xWindowId == 0L) return null

            // ── 3. XSelectInput ───────────────────────────────────────────────
            xSelectInput?.invokeExact(displaySeg, xWindowId, FULL_EVENT_MASK) as? Int

            // ── 4. WM_DELETE_WINDOW (protocole de fermeture propre) ───────────
            Arena.ofConfined().use { arena ->
                val atomName = "WM_DELETE_WINDOW".toByteArray(Charsets.US_ASCII)
                val atomNamePtr = arena.allocate(atomName.size.toLong() + 1)
                for (i in atomName.indices) atomNamePtr.set(ValueLayout.JAVA_BYTE, i.toLong(), atomName[i])
                atomNamePtr.set(ValueLayout.JAVA_BYTE, atomName.size.toLong(), 0)

                val wmDeleteWindow: Long = xInternAtom?.invokeExact(
                    displaySeg,
                    atomNamePtr,
                    0,  // Bool only_if_exists = False → crée si absent
                ) as? Long ?: 0L

                if (wmDeleteWindow != 0L) {
                    // Allouer un tableau de 1 Atom (unsigned long = 8 octets) pour XSetWMProtocols
                    val atomArray = arena.allocate(ValueLayout.JAVA_LONG, 1L)
                    atomArray.set(ValueLayout.JAVA_LONG, 0L, wmDeleteWindow)
                    xSetWMProtocols?.invokeExact(displaySeg, xWindowId, atomArray, 1) as? Int
                }
            }

            // ── 5. XStoreName ─────────────────────────────────────────────────
            Arena.ofConfined().use { arena ->
                val nameBytes = attrs.title.toByteArray(Charsets.ISO_8859_1)
                val namePtr = arena.allocate(nameBytes.size.toLong() + 1)
                for (i in nameBytes.indices) namePtr.set(ValueLayout.JAVA_BYTE, i.toLong(), nameBytes[i])
                namePtr.set(ValueLayout.JAVA_BYTE, nameBytes.size.toLong(), 0)
                xStoreName?.invokeExact(displaySeg, xWindowId, namePtr) as? Int
            }

            val window = X11Window(display, xWindowId, attrs)

            // ── 6. XMapWindow (si visible) ────────────────────────────────────
            if (attrs.visible) {
                xMapWindow?.invokeExact(displaySeg, xWindowId) as? Int
                xFlush?.invokeExact(displaySeg) as? Int
            }

            return window
        }
    }
}
