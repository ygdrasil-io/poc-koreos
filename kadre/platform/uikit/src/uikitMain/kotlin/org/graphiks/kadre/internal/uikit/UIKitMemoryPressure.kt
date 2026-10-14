@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Le seam d'installation de la pression mémoire — la registration
 * `NSNotificationCenter.defaultCenter.addObserverForName(name, null, mainQueue, block)` en
 * production, un fake pilotable en test hôte (miroir exact des seams display et devices :
 * la traduction est prouvée sur le seam, la livraison du VRAI centre est l'affaire du
 * driver, Task 6).
 *
 * Le seam est un fait PROCESS : le centre de notifications est global au processus, la
 * registration l'est donc aussi — c'est ce que [UIKitProcessMemoryPressure] garantit en ne
 * construisant la source qu'à la première session.
 */
internal interface UIKitMemoryPressureSeam {
    /**
     * `addObserverForName(name, null, NSOperationQueue.mainQueue(), block)` — la forme
     * block-based du binding surface (phase 2). Peut lancer : un échec d'install est
     * l'état normatif `PlatformFailure` (voir la matrice de [UIKitMemoryPressureSource]).
     */
    public fun observe(name: String, onEvent: () -> Unit): NSObjectProtocol

    /** `removeObserver(token)` — retire exactement le token posé par [observe]. */
    public fun removeObserver(token: NSObjectProtocol)

    /**
     * Le relais HORS stack — `dispatch_async(main queue)` en production. Le block observer ne
     * court JAMAIS le code hôte dans sa propre pile (miroir Android : le post main-looper ;
     * miroir AppKit : le bloc Dispatch) ; le test hôte en injectant une file à drainer.
     */
    public fun post(block: () -> Unit)
}

/**
 * La kernel de mapping — NORMATIVE (ruling du ledger, roadmap l.305, documentée AVANT
 * l'activation du relais). La matrice complète :
 *
 * | Fait UIKit/tvOS                                   | Niveau publié         |
 * |---------------------------------------------------|-----------------------|
 * | `UIApplicationDidReceiveMemoryWarningNotification` | `Moderate`            |
 *
 * - `Moderate` est l'UNIQUE niveau : la notification est non-graduée (pas de palier
 *   warning/critical dans le SDK) ; le palier WARN d'AppKit en est l'analogique honnête.
 *   `Critical` est une gravity INVENTÉE — cette kernel ne la produit JAMAIS, et rien
 *   d'autre que cette notification n'est une source (la compile des DEUX cibles est le
 *   fait : la constante existe et n'existe qu'ici).
 * - AUCUN signal dérivé du lifecycle ou de la visibilité (entrées/sorties de fond,
 *   activations de scène) n'est une pression — la perte de visibilité n'est pas de la
 *   pression (miroir Android : les trims background ne sont jamais des signaux).
 * - La notification ne porte aucun payload exploitable : la kernel ne lit RIEN d'elle.
 */
internal fun uikitMemoryPressureLevelOfMemoryWarning(): MemoryPressureLevel = MemoryPressureLevel.Moderate

/**
 * La source de pression mémoire UIKit/tvOS — le modèle AppKit/Android : UNE source
 * process-wide, installée à la première session, qui traduit l'UNIQUE notification memory
 * warning en niveau du modèle ([uikitMemoryPressureLevelOfMemoryWarning]) et la relaie HORS
 * de la pile du block observer. La matrice normative (voir la kernel — l'artefact revu) :
 *
 * - `UIApplicationDidReceiveMemoryWarningNotification` → `Moderate`, l'UNIQUE mapping ;
 *   `Critical` n'existe JAMAIS côté UIKit/tvOS, aucun signal lifecycle/visibilité n'est une
 *   pression ;
 * - un échec d'install (le block observer lance à la pose) répond `false` — la capability
 *   correspondante, posée par [UIKitProcessMemoryPressure], est
 *   `Unavailable(PlatformFailure(UIKit, "memory-pressure", "source-exception"))` ; la
 *   registration sur le centre ne lance en pratique jamais pour un nom valide — la branche
 *   garde `runCatching` PAR PARITÉ avec les sources AppKit/Android (un échec est une
 *   réponse, pas une exception traversante) ;
 * - jamais tentée (pas de seam) → la capability par défaut `Unsupported` ;
 * - fermer une session ne ferme pas la source process ; la terminaison du host oui —
 *   [close] est appelé par [UIKitProcessMemoryPressure.hostTerminated] quand le dernier
 *   host vivant se termine, et retire l'EXACT token posé (une source jamais enregistrée ne
 *   désenregistre rien).
 *
 * LA RÈGLE PRE-WIRE (la décision explicite, miroir AppKit relu) : une notification observée
 * ALORS QU'AUCUN relais n'est branché ([install] pose le relais de fan-out process, chaque
 * host branche LE SIEN à son attach réussi) est PERDUE — jamais bufférisée. Les raisons :
 *
 * 1. la pression mémoire est un ÉTAT transient du processus, pas une file d'événements —
 *    un niveau rejoué à un host câblé plus tard mentirait sur le présent ;
 * 2. le précédent AppKit ne dit pas autre chose : son buffer `openingLevels` ne couvre que
 *    l'INTÉRIEUR de son `open()` synchrone (rejoué immédiatement après le retour) — or la
 *    registration NSNotificationCenter ne livre JAMAIS pendant l'appel (le centre poste sur
 *    la queue ENREGISTRÉE, après le retour), donc la fenêtre AppKit est ICI vide par
 *    construction ;
 * 3. cette source vit À TRAVERS LES HOSTS (contrairement au broker AppKit, par-attach) : un
 *    buffer pré-wire pourrait survivre à la terminaison de son host et livrer un niveau
 *    périmé à un host sans rapport ;
 * 4. rien n'est perdu : UIKit re-tire la notification à chaque épisode réel de pression —
 *    le prochain warning rétablit le tableau.
 *
 * Le relais de la source est unique ([install]) : le fan-out vers les hosts vivants est le
 * travail du [UIKitProcessMemoryPressure], qui passe SA closure d'un seul mouvement. Le
 * coalescing most-severe n'est pas ici : il est du runtime (`SessionRuntime.emitMemoryPressure`).
 *
 * @param observe la registration injectée (peut lancer).
 * @param removeObserver la déregistration injectée.
 * @param post le relais hors stack injecté — le main queue en production.
 */
internal class UIKitMemoryPressureSource(
    private val observe: (String, onEvent: () -> Unit) -> NSObjectProtocol,
    private val removeObserver: (NSObjectProtocol) -> Unit,
    private val post: (() -> Unit) -> Unit,
    private val captureDelivery: ((MemoryPressureLevel) -> (() -> Unit))? = null,
) : AutoCloseable {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var installed: Boolean = false

    /** Vrai seulement si [observe] a répondu — [close] ne désenregistre jamais à l'aveugle. */
    private var registered: Boolean = false

    /** Le token posé par [install] — retenu pour le retrait exact au close. */
    private var token: NSObjectProtocol? = null

    /** Le relais posé par [install] — unique, le fan-out multi-hosts vit au-dessus. */
    private var relay: ((MemoryPressureLevel) -> Unit)? = null

    /**
     * Installe la source : pose l'UNIQUE observer auprès du seam pour
     * [MEMORY_WARNING_NOTIFICATION_NAME] et retient le relais. Un observe qui lance est
     * l'échec normatif — `false`, la source reste dans l'état « jamais enregistrée »
     * ([close] n'appellera pas removeObserver) ; un double appel (après close ou après une
     * install vivante) est un refus honnête, jamais une seconde registration.
     */
    public fun install(relay: (MemoryPressureLevel) -> Unit): Boolean = lock.withLock {
        if (closed || installed) return@withLock false
        installed = try {
            token = observe(MEMORY_WARNING_NOTIFICATION_NAME, ::onMemoryWarning)
            registered = true
            true
        } catch (_: Throwable) {
            // L'échec d'install est une réponse, pas une exception : la capability
            // `Unavailable(PlatformFailure)` est posée par le propriétaire au-dessus.
            false
        }
        if (installed) this.relay = relay
        installed
    }

    /**
     * Le callback notification (le block posé l'appelle). La mapping est normative (voir la
     * kernel) ; le niveau mappé est relayé HORS de cette pile — le block posté relit l'état
     * vivant, mais ses destinataires sont photographiés À L'ARRIVÉE (DROP avant wire).
     * Ainsi un warning qui glisse derrière un close (le centre peut consommer un
     * block déjà en file après le retrait demandé du token) ne relaie rien.
     */
    public fun onMemoryWarning() {
        val mapped = uikitMemoryPressureLevelOfMemoryWarning()
        val observedRelay = lock.withLock { if (closed) null else relay } ?: return
        val delivery = captureDelivery?.invoke(mapped) ?: { observedRelay(mapped) }
        post {
            if (lock.withLock { closed || relay !== observedRelay }) return@post
            delivery()
        }
    }

    /**
     * La terminaison du DERNIER host ferme la source process : retire l'exact token posé et
     * interdit tout relais ultérieur. Idempotent ; une source jamais enregistrée (install
     * absent ou échoué) ne désenregistre rien.
     */
    override fun close() {
        val stale: NSObjectProtocol? = lock.withLock {
            if (closed) return@withLock null
            closed = true
            relay = null
            if (registered) token else null
        }
        stale?.let(removeObserver)
    }

    internal companion object {
        /**
         * L'UNIQUE notification de la source — le fait klib (spike Task 1 : la constante
         * compile depuis les DEUX cibles, iOS et tvOS). Le `!!` est le fait d'interop des
         * constantes `String?` du klib (même pattern que le binding surface).
         */
        internal val MEMORY_WARNING_NOTIFICATION_NAME: String =
            UIApplicationDidReceiveMemoryWarningNotification!!
    }
}

/**
 * La capability par défaut — le défaut runtime (`DEFAULT_LIFECYCLE_CAPABILITIES`) : ce que
 * UIKit publie tant qu'aucune installation n'a été tentée (et après la fermeture de la
 * source, le prochain premier host réinstallant).
 */
private val DEFAULT_MEMORY_PRESSURE_CAPABILITIES = LifecycleCapabilities(FeatureAvailability.Unsupported)

/** La capability d'une source installée — la transition réelle de la matrice C. */
private val INSTALLED_MEMORY_PRESSURE_CAPABILITIES = LifecycleCapabilities(FeatureAvailability.Available)

/** L'échec d'install — le miroir AppKit/Android exact (`BACKEND-CAPABILITIES.md`, pression mémoire). */
private val FAILED_MEMORY_PRESSURE_CAPABILITIES = LifecycleCapabilities(
    FeatureAvailability.Unavailable(
        KadreFailure.PlatformFailure(KadrePlatform.UIKit, "memory-pressure", "source-exception"),
    ),
)

/**
 * Le propriétaire PROCESS de la source — le modèle AppKit/Android : UNE source pour N
 * attach (UNE session par `UIWindowScene` — les scènes d'un même processus sont autant de
 * hosts du MÊME avertissement), installée à la première, réutilisée par les suivantes,
 * fermée par la terminaison du DERNIER host vivant. Chaque attach détient un créneau
 * ([UIKitMemoryPressureClaim]) de l'installation : la capability du host est lue à la
 * création du créneau (elle devient `initialLifecycleCapabilities` du contrôleur), le relais
 * est branché à l'attach réussi, et la terminaison du host (ou le rollback d'un attach qui
 * a fait installer la source) libère le créneau — le dernier libéré ferme.
 *
 * La source n'est créée que pour un seam présent : le paramètre est nullable par parité
 * d'architecture avec Android (où l'`Application` peut être inatteignable) ; en production
 * UIKit/tvOS le centre de notifications est un fait process et le seam est TOUJOURS présent
 * — le chemin null reste le défaut honnête « aucune tentative » (capability par défaut,
 * jamais une `PlatformFailure` inventée), prouvé par le test hôte. L'état échec d'une
 * source vivante est collant (les attach suivants la réutilisent, échec compris) jusqu'à
 * la fermeture par le dernier host.
 */
internal class UIKitProcessMemoryPressure {
    private val lock = RuntimeLock()
    private var source: UIKitMemoryPressureSource? = null
    private var capabilities: LifecycleCapabilities = DEFAULT_MEMORY_PRESSURE_CAPABILITIES

    /** Les créneaux vivants — clé = l'identité du host, valeur = le relais (branché plus tard). */
    private val claims = linkedMapOf<Any, ((MemoryPressureLevel) -> Unit)?>()

    /** La capability vivante — l'observation de la transition (tests, diagnostics). */
    internal val currentCapabilities: LifecycleCapabilities
        get() = lock.withLock { capabilities }

    /**
     * Le créneau d'un NOUVEAU host : installe la source process à la première session
     * (capability `Available` après succès, `Unavailable(PlatformFailure)` après échec du
     * register), réutilise la source vivante sinon, et ne tente RIEN sans seam.
     */
    public fun newHost(seam: UIKitMemoryPressureSeam?): UIKitMemoryPressureClaim {
        val key = Any()
        val hostCapabilities = lock.withLock {
            if (source == null) {
                if (seam == null) {
                    // Pas de seam sur CETTE attach : aucune tentative, la capability par
                    // défaut — honnête, pas de PlatformFailure inventée.
                    capabilities = DEFAULT_MEMORY_PRESSURE_CAPABILITIES
                } else {
                    val created = UIKitMemoryPressureSource(seam::observe, seam::removeObserver, seam::post, ::captureFanOut)
                    source = created
                    capabilities = if (created.install { }) {
                        INSTALLED_MEMORY_PRESSURE_CAPABILITIES
                    } else {
                        FAILED_MEMORY_PRESSURE_CAPABILITIES
                    }
                }
            }
            claims[key] = null
            capabilities
        }
        return UIKitMemoryPressureClaim(this, key, hostCapabilities)
    }

    /** Le relais d'un host — branché à l'attach réussi, ignoré si le créneau a déjà quitté. */
    internal fun hostWired(key: Any, relay: (MemoryPressureLevel) -> Unit) {
        lock.withLock {
            if (claims.containsKey(key)) claims[key] = relay
        }
    }

    /**
     * La terminaison du host (ou le rollback de son attach) : le créneau quitte. Le dernier
     * libéré ferme la source process — la fermeture est HORS du lock, et l'opération qui peut
     * y lancer est le removeObserver (l'observe n'intervient qu'à l'install, où son échec
     * répond `false`). La couture d'attach absorbe ce lancement sur son canal de reporting
     * sur TOUS ses chemins — terminal ET rollback — comme toute perte de terminal.
     */
    internal fun hostTerminated(key: Any) {
        val dying = lock.withLock {
            if (!claims.containsKey(key)) return@withLock null
            claims.remove(key)
            if (claims.isNotEmpty()) return@withLock null
            val closing = source
            source = null
            capabilities = DEFAULT_MEMORY_PRESSURE_CAPABILITIES
            closing
        } ?: return
        dying.close()
    }

    /** Destinataires à l'observation, terminal/identité revérifiés à la livraison hors lock. */
    private fun captureFanOut(level: MemoryPressureLevel): () -> Unit {
        val targets = lock.withLock { claims.filterValues { it != null }.toMap() }
        return {
            targets.forEach { (key, relay) ->
                val live = lock.withLock { claims[key] === relay }
                if (live) relay?.invoke(level)
            }
        }
    }
}

/**
 * Le créneau d'un host dans la source process — la capability du host (lue à la création,
 * elle devient `initialLifecycleCapabilities` du contrôleur) et les deux gestes de la
 * couture : brancher le relais à l'attach réussi, libérer à la terminaison (idempotent :
 * une terminaison et un rollback du même attach ne libèrent qu'une fois).
 */
internal class UIKitMemoryPressureClaim internal constructor(
    private val owner: UIKitProcessMemoryPressure,
    private val key: Any,
    /** La capability memoryPressure de CE host — le `initialLifecycleCapabilities` du contrôleur. */
    public val capabilities: LifecycleCapabilities,
) {
    /** Branche le relais du host — `controller.emitMemoryPressure` absorbant ses refus. */
    internal fun wire(relay: (MemoryPressureLevel) -> Unit) {
        owner.hostWired(key, relay)
    }

    /** La terminaison du host — libère le créneau ; le dernier libéré ferme la source process. */
    public fun hostTerminated() {
        owner.hostTerminated(key)
    }
}

/**
 * La réalisation production du seam : la registration auprès du centre de notifications par
 * défaut (block-based, SANS filtre d'objet — le warning est un fait du PROCESSUS, pas d'une
 * scène — sur la main queue, la queue où l'attach vit tout entier) et le relais hors stack
 * par `dispatch_async(main queue)` — le même mécanisme que la lecture initiale du binding
 * surface. Un block qui resterait dans la pile du centre réentrerait les chemins d'observer
 * ; posté, il ne court qu'au tour de file SUIVANT.
 */
internal object UIKitNotificationCenterMemoryPressureSeam : UIKitMemoryPressureSeam {
    override fun observe(name: String, onEvent: () -> Unit): NSObjectProtocol =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name,
            null,
            NSOperationQueue.mainQueue(),
        ) { _ -> onEvent() }

    override fun removeObserver(token: NSObjectProtocol) {
        NSNotificationCenter.defaultCenter.removeObserver(token)
    }

    override fun post(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }
}
