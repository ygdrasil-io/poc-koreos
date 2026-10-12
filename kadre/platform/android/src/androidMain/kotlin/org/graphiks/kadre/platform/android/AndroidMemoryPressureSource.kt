package org.graphiks.kadre.platform.android

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock

/**
 * Le seam d'installation de la pression mémoire — la registration `Application.
 * registerComponentCallbacks(ComponentCallbacks2)` en production, un fake pilotable en test
 * hôte (le stub android.jar lance à la construction ; le miroir exact des seams display et
 * devices : la traduction est prouvée sur le seam, les lectures SDK sur émulateur).
 *
 * Le seam est un fait PROCESS : l'Application est unique au processus, la registration
 * l'est donc aussi — c'est ce que [AndroidProcessMemoryPressure] garantit en ne construisant
 * la source qu'à la première session.
 */
internal interface AndroidMemoryPressureSeam {
    /** `Application.registerComponentCallbacks` — peut lancer (échec d'install = PlatformFailure). */
    public fun register(callbacks: ComponentCallbacks2)

    /** `Application.unregisterComponentCallbacks` — retire exactement l'instance posée. */
    public fun unregister(callbacks: ComponentCallbacks2)

    /**
     * Le relais HORS stack — `Handler(mainLooper).post` en production. Le callback natif ne
     * court JAMAIS le code hôte dans sa propre pile (miroir AppKit : le bloc Dispatch), le
     * test hôte en injectant une file à drainer.
     */
    public fun post(block: () -> Unit)
}

/**
 * La source de pression mémoire Android — le modèle AppKit : UNE source process-wide,
 * installée à la première session, qui traduit les trims RUNNING du SDK en niveaux du
 * modèle et les relais HORS de la pile du callback. La matrice normative (fact sheet §3) :
 *
 * - `TRIM_MEMORY_RUNNING_MODERATE` (5) → `Moderate` ;
 * - `TRIM_MEMORY_RUNNING_LOW` (10) et `TRIM_MEMORY_RUNNING_CRITICAL` (15) → `Critical` ;
 * - les niveaux background (UI_HIDDEN 20, BACKGROUND 40, MODERATE 60, COMPLETE 80) ne sont
 *   JAMAIS des signaux — la perte de visibilité n'est pas de la pression, un niveau hors
 *   table ne parle pas non plus ;
 * - un échec d'install (le register lance) répond `false` — la capability correspondante,
 *   posée par [AndroidProcessMemoryPressure], est
 *   `Unavailable(PlatformFailure(Android, "memory-pressure", "source-exception"))` ;
 * - fermer une session ne ferme pas la source process ; la terminaison du host oui —
 *   [close] est appelé par [AndroidProcessMemoryPressure.hostTerminated] quand le dernier
 *   host vivant se termine, et désenregistre l'exacte instance posée.
 *
 * Le relais est unique ([install]) : le fan-out vers les hosts vivants est le travail du
 * [AndroidProcessMemoryPressure], qui passe SA closure d'un seul mouvement. Le coalescing
 * most-severe n'est pas ici : il est du runtime ([SessionRuntime.emitMemoryPressure]).
 *
 * @param register la registration Application injectée (peut lancer).
 * @param unregister la déregistration Application injectée.
 * @param post le relais hors stack injecté — le main looper en production.
 */
internal class AndroidMemoryPressureSource(
    private val register: (ComponentCallbacks2) -> Unit,
    private val unregister: (ComponentCallbacks2) -> Unit,
    private val post: (() -> Unit) -> Unit,
) : AutoCloseable {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var installed: Boolean = false

    /** Vrai seulement si [register] a répondu — [close] ne désenregistre jamais à l'aveugle. */
    private var registered: Boolean = false

    /** Le relais posé par [install] — unique, le fan-out multi-hosts vit au-dessus. */
    private var relay: ((MemoryPressureLevel) -> Unit)? = null

    /**
     * L'UNIQUE instance callbacks de la source : posée une fois à l'install, désenregistrée
     * une fois au close. Une classe anonyme — aucune construction de type SDK concret, le
     * test hôte peut exécuter exactement ce chemin.
     */
    private val callbacks: ComponentCallbacks2 = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) = this@AndroidMemoryPressureSource.onTrimMemory(level)
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() = Unit
    }

    /**
     * Installe la source : pose l'unique instance callbacks auprès du seam et retient le
     * relais. Un register qui lance est l'échec normatif — `false`, la source reste dans
     * l'état « jamais enregistrée » ([close] n'appellera pas unregister) ; un double appel
     * (après close ou après une install vivante) est un refus honnête, jamais une seconde
     * registration.
     */
    public fun install(relay: (MemoryPressureLevel) -> Unit): Boolean = lock.withLock {
        if (closed || installed) return@withLock false
        installed = try {
            register(callbacks)
            registered = true
            true
        } catch (cause: Throwable) {
            // L'échec d'install est une réponse, pas une exception : la capability
            // `Unavailable(PlatformFailure)` est posée par le broker au-dessus.
            false
        }
        if (installed) this.relay = relay
        installed
    }

    /**
     * Le callback SDK. La mapping est normative (voir la classe) ; un niveau mappé est
     * relayé HORS de cette pile — le bloc posté relit l'état vivant, ainsi un trim qui
     * glisse derrière un close (le SDK peut délivrer après la déregistration demandée)
     * ne relaie rien.
     */
    public fun onTrimMemory(level: Int) {
        val mapped = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> MemoryPressureLevel.Moderate
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            -> MemoryPressureLevel.Critical

            else -> null
        } ?: return
        post {
            val deliver = lock.withLock { if (closed) null else relay } ?: return@post
            deliver(mapped)
        }
    }

    /**
     * La terminaison du host ferme la source process : désenregistre l'exacte instance posée
     * et interdit tout relais ultérieur. Idempotent ; une source jamais enregistrée (install
     * absent ou échoué) ne désenregistre rien.
     */
    override fun close() {
        val live = lock.withLock {
            if (closed) return@withLock null
            closed = true
            relay = null
            if (registered) callbacks else null
        }
        if (live != null) unregister(live)
    }
}

/**
 * La capability par défaut — le défaut runtime (`DEFAULT_LIFECYCLE_CAPABILITIES`) : ce que
 * Android publie tant qu'aucune installation n'a été tentée (et après la fermeture de la
 * source, le prochain premier session réinstallant).
 */
private val DEFAULT_MEMORY_PRESSURE_CAPABILITIES = LifecycleCapabilities(FeatureAvailability.Unsupported)

/** La capability d'une source installée — la transition réelle de la matrice C. */
private val INSTALLED_MEMORY_PRESSURE_CAPABILITIES = LifecycleCapabilities(FeatureAvailability.Available)

/** L'échec d'install — le miroir AppKit exact (`BACKEND-CAPABILITIES.md`, pression mémoire). */
private val FAILED_MEMORY_PRESSURE_CAPABILITIES = LifecycleCapabilities(
    FeatureAvailability.Unavailable(
        KadreFailure.PlatformFailure(KadrePlatform.Android, "memory-pressure", "source-exception"),
    ),
)

/**
 * Le propriétaire PROCESS de la source — le modèle AppKit : UNE source pour N sessions,
 * installée à la première ; les sessions suivantes la réutilisent ; fermer une session n'y
 * touche pas ; la terminaison du DERNIER host vivant la ferme. Un host Android par attach,
 * donc chaque attach détient un créneau ([AndroidMemoryPressureClaim]) de l'installation :
 * la capability du host est lue à la création du créneau (elle devient
 * `initialLifecycleCapabilities` du contrôleur), le relais est branché à l'attach réussi,
 * et la terminaison du host (ou le rollback d'un attach qui a fait installer la source)
 * libère le créneau — le dernier libéré ferme.
 *
 * La source n'est créée que pour un seam présent : un attach qui n'atteint pas
 * l'Application (`view.context.applicationContext as? Application` échoué) tente RIEN et
 * reçoit la capability par défaut — pas de `PlatformFailure` inventée, le prochain attach
 * qui atteint l'Application installe. L'état échec d'une source vivante est collant
 * (les sessions suivantes la réutilisent, échec compris) jusqu'à la fermeture par le
 * dernier host.
 */
internal class AndroidProcessMemoryPressure {
    private val lock = RuntimeLock()
    private var source: AndroidMemoryPressureSource? = null
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
    public fun newHost(seam: AndroidMemoryPressureSeam?): AndroidMemoryPressureClaim {
        val key = Any()
        val hostCapabilities = lock.withLock {
            if (source == null) {
                if (seam == null) {
                    // Pas d'Application atteignable sur CETTE attach : aucune tentative, la
                    // capability par défaut — honnête, pas de PlatformFailure inventée.
                    capabilities = DEFAULT_MEMORY_PRESSURE_CAPABILITIES
                } else {
                    val created = AndroidMemoryPressureSource(seam::register, seam::unregister, seam::post)
                    source = created
                    capabilities = if (created.install { level -> fanOut(level) }) {
                        INSTALLED_MEMORY_PRESSURE_CAPABILITIES
                    } else {
                        FAILED_MEMORY_PRESSURE_CAPABILITIES
                    }
                }
            }
            claims[key] = null
            capabilities
        }
        return AndroidMemoryPressureClaim(this, key, hostCapabilities)
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
     * y lancer est l'UNREGISTER (le register n'intervient qu'à l'install, où son échec
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

    /** Le fan-out du relais unique vers les relais branchés — copié sous lock, livré hors lock. */
    private fun fanOut(level: MemoryPressureLevel) {
        val targets = lock.withLock { claims.values.filterNotNull().toList() }
        targets.forEach { relay -> relay(level) }
    }
}

/**
 * Le créneau d'un host dans la source process — la capability du host (lue à la création,
 * elle devient `initialLifecycleCapabilities` du contrôleur) et les deux gestes de la
 * couture : brancher le relais à l'attach réussi, libérer à la terminaison (idempotent :
 * une terminaison et un rollback du même attach ne libèrent qu'une fois).
 */
internal class AndroidMemoryPressureClaim internal constructor(
    private val owner: AndroidProcessMemoryPressure,
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
 * La réalisation production du seam : la registration auprès de l'Application du processus
 * (`activity.application` pour la forme Activity ; `view.context.applicationContext as?
 * Application` pour la forme View — le seul récepteur public de
 * `registerComponentCallbacks`, `Context.registerComponentCallbacks` n'est pas public) et
 * le relais hors stack sur le main looper — le thread même qui délivre les trims, lu
 * PARESSEUSEMENT par la couture pour ne jamais construire un type SDK au-delà du SDK réel.
 */
internal class AndroidApplicationMemoryPressureSeam(
    private val application: Application,
) : AndroidMemoryPressureSeam {
    override fun register(callbacks: ComponentCallbacks2) {
        application.registerComponentCallbacks(callbacks)
    }

    override fun unregister(callbacks: ComponentCallbacks2) {
        application.unregisterComponentCallbacks(callbacks)
    }

    override fun post(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }
}
