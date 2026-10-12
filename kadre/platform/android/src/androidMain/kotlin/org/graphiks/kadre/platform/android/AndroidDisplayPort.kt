package org.graphiks.kadre.platform.android

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize

/**
 * Le seam SDK du port display — le vrai [DisplayManager] en production, un fake pilotable en
 * test hôte (le stub android.jar lance à la construction, donc les tests du port passent par
 * des jetons `Any` ; le port ne nomme JAMAIS un type SDK — c'est la traduction de ce seam en
 * formes port qui est prouée, pas les lectures SDK elles-mêmes, vérifiées sur émulateur).
 */
internal interface AndroidDisplaySource : AutoCloseable {
    /**
     * L'inventaire COMPLET des displays du système, au moment de l'appel. Le port relit cet
     * inventaire à chaque demande ET à chaque fire de [registerListener] — jamais un delta.
     */
    public fun displays(): List<Any>

    /**
     * Enregistre [listener] pour chaque moment où le DisplayManager signale un display ajouté,
     * retiré ou changé (le listener multi-cast du SDK, contracté en UNE callback). L'argument
     * est informationnel (le display affecté, au mieux du SDK) : le port ne le lit JAMAIS —
     * il relit l'inventaire complet.
     */
    public fun registerListener(listener: (Any) -> Unit)

    /** Retire exactement l'instance [listener] donnée à [registerListener]. */
    public fun unregisterListener(listener: (Any) -> Unit)

    /** La lecture SDK brute d'un display d'[displays] — les faits, pas leur interprétation. */
    public fun displayInfo(display: Any): AndroidDisplayInfo
}

    /** Les faits SDK d'un display, traduits sans interprétation par la réalisation du seam. */
    internal data class AndroidDisplayInfo(
        /** Le displayId du SDK — stable à vie du display ; c'est la clé de corrélation du port. */
        val key: Long,
        /**
         * FLAG_PRESENTATION / FLAG_PRIVATE — la règle Physical/Virtual du plan. (TYPE_OVERLAY,
         * cité par la règle, n'est PAS de l'API SDK publique — ni `getType()` ni la constante
         * TYPE_* ne sont dans android.jar ; un display overlay qui ne porte pas ces flags
         * resterait Physical. Le fait vit ici : une extension future du seam n'a pas à toucher
         * au port.)
         */
        val isVirtual: Boolean,
    /** Le nom du SDK, ou null quand il n'en rapporte pas — jamais fabriqué. */
    val name: String?,
    /** La taille réelle du display (getRealSize) — les bounds du port. */
    val realWidth: Int,
    val realHeight: Int,
    /** densityDpi/160 — le facteur d'échelle observé. */
    val scaleFactor: Double,
    /** Les modes supportés (getSupportedModes, API 23+) — refreshRateHz observé, bitDepth null si inconnu. */
    val modes: List<AndroidDisplayModeInfo>,
    /** Le modeId courant (getMode), ou null si le SDK n'en rapporte pas. */
    val currentModeId: Int?,
)

/** Un mode d'[AndroidDisplayInfo] : la taille physique, le refresh observé, la profondeur si le SDK la rapporte. */
internal data class AndroidDisplayModeInfo(
    val physicalSize: PhysicalSize,
    val refreshRateHz: Double?,
    val bitDepth: Int?,
    val modeId: Int,
)

/** Le displayId du display par défaut du système — `android.view.Display.DEFAULT_DISPLAY`. */
private const val DEFAULT_DISPLAY_KEY = 0L

/**
 * Le port display obligatoire de la plateforme Android : l'inventaire COMPLET du multi-cast
 * [DisplayManager], sans slot-conflict — l'observateur runtime et les relectures coexistent.
 *
 * Le miroir exact de `WebDisplayPort` : l'inventaire initial est publié SYNCHRONEMENT à
 * `installSnapshotObserver` (:114) — une session qui s'attache et ne fait rien doit déjà
 * énoncer les displays que le système lui donne, sans attendre un événement qui ne viendra
 * peut-être jamais — et chaque fire du listener republie l'inventaire RELIT EN ENTIER auprès
 * du seam (:82→156) : le port ne fabrique jamais un delta entre deux états du SDK. `workArea`
 * reste `null` TOUJOURS (Android n'a pas de bureau — inventer un rectangle serait un mensonge),
 * les bounds sont la taille réelle du display à l'ORIGINE (pas de position bureau fabriquée),
 * et les clés de corrélation sont les displayIds — stables à vie du display ; le runtime
 * alloue ses propres identités publiques par-dessus.
 *
 * Ce qui n'est pas mesurable est un échec, pas une supposition : un inventaire vide, ou un
 * display dont la lecture ne porte pas une géométrie portable (dimensions non positives,
 * échelle invalide, zéro mode), répond `TemporarilyUnavailable(retryable=true)` — à la
 * demande ET via l'observer — qui RETIRE l'inventaire précédent (miroir web :156-159) ;
 * un snapshot partiel n'est jamais une réponse. `enumerationCapability` est
 * `Supported(Unit, Available)` dès la construction : le DisplayManager est structurellement
 * observable (listener multi-cast, pas de permission, pas de geste).
 *
 * Le port est scoped à la session : le runtime installe exactement un observer et ferme le
 * port avec la session, ce qui désenregistre le listener du seam et ferme le seam possédé.
 *
 * @param source le seam DisplayManager que ce port lit ; possédé par le port, fermé avec lui.
 */
internal class AndroidDisplayPort(private val source: AndroidDisplaySource) : DisplayPort {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var observer: ((KadreResult<DisplayPortSnapshot>) -> Unit)? = null

    /** Le listener du seam de l'install VIVANTE — chaque install enregistre le sien, par instance. */
    private var listener: ((Any) -> Unit)? = null
    private var subscription: AutoCloseable? = null

    override val enumerationCapability: Capability<Unit> =
        Capability.Supported(Unit, FeatureAvailability.Available)

    override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> = snapshotResult()

    /**
     * Installe l'observer session-runtime, publie l'inventaire courant à lui exactement une
     * fois, et republie à chaque later fire du listener du seam.
     *
     * Un observer à la fois — le miroir web :100-128 : le runtime en installe exactement un,
     * un second install remplace le premier (le port désenregistre le listener qu'il avait
     * créé pour lui et s'enregistre à nouveau, et la publication initiale du remplacement
     * va à l'observer du remplacement). Rien d'autre n'est fermé en silence : le
     * `AutoCloseable` d'un install remplacé reste appelable et devient un no-op, et le close
     * d'un handle périmé ne désabonne jamais l'install qui l'a remplacé. La publication
     * initiale — le même message qu'un fire du seam livrerait, livré exactement une fois,
     * HORS du lock — est le gate de phase 6 lui-même : l'inventaire initial n'attend jamais
     * un événement que le système ne livrera peut-être pas.
     */
    override fun installSnapshotObserver(
        observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
    ): AutoCloseable {
        var replaced: AutoCloseable? = null
        val listener: (Any) -> Unit = { emitCurrent() }
        var withdrawn = false
        var handle: AutoCloseable? = null
        val registration = lock.withLock {
            check(!closed) { "the android display port is closed" }
            replaced = subscription
            this.observer = observer
            this.listener = listener
            // L'abonnement au seam EST l'install : c'est ici — jamais avant — que le port
            // commence à recevoir les fires du DisplayManager.
            source.registerListener(listener)
            handle = AutoCloseable {
                val live = lock.withLock {
                    if (withdrawn || closed) return@withLock null
                    withdrawn = true
                    if (this.listener === listener) this.listener = null
                    if (this.observer === observer) this.observer = null
                    if (subscription === handle) subscription = null
                    listener
                }
                if (live != null) source.unregisterListener(live)
            }.also { subscription = it }
            handle!!
        }
        replaced?.close()
        // La première poussée de l'install, hors du lock : la même réponse qu'un fire du seam
        // livrerait, livrée exactement une fois (miroir web :111-114).
        observer(snapshotResult())
        return registration
    }

    /**
     * Désenregistre le listener du seam vivant, ferme le seam que ce port possède, et oublie
     * l'observer. Idempotent : tout appel ultérieur, comme tout fire du seam après le premier,
     * ne publie rien. Le runtime ferme le port avec les composants de la session, donc le
     * listener DisplayManager meurt avec la session qui l'a installé.
     */
    override fun close() {
        val live = lock.withLock {
            if (closed) return@withLock null
            closed = true
            val liveListener = listener
            listener = null
            observer = null
            subscription = null
            liveListener
        }
        if (live != null) source.unregisterListener(live)
        source.close()
    }

    /** Livre la réponse courante à l'observer installé, quoi que le seam mesure maintenant. */
    private fun emitCurrent() {
        val installed = lock.withLock { observer } ?: return
        installed(snapshotResult())
    }

    private fun snapshotResult(): KadreResult<DisplayPortSnapshot> {
        val inventory = translatedInventory()
            ?: return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
        return KadreResult.Success(inventory)
    }

    /**
     * L'inventaire COMPLET re-traduit, ou null si UNE seule pièce est imesurable — un snapshot
     * réussi ne contient jamais un inventaire partiel. Le port ne fait jamais confiance à ce
     * que son seam devrait avoir filtré : la même défense que le miroir web (:162-168).
     */
    private fun translatedInventory(): DisplayPortSnapshot? {
        val displays = source.displays()
        if (displays.isEmpty()) return null
        val translated = displays.map { display -> translatedDisplay(display) ?: return null }
        val primary = translated.firstOrNull { it.key == DEFAULT_DISPLAY_KEY } ?: translated.first()
        return DisplayPortSnapshot(primaryKey = primary.key, displays = translated)
    }

    /** La traduction d'un display, ou null quand sa mesure ne porte pas de géométrie portable. */
    private fun translatedDisplay(display: Any): DisplayPortDisplay? {
        val info = source.displayInfo(display)
        val measurable = info.realWidth > 0 && info.realHeight > 0 &&
            info.scaleFactor.isFinite() && info.scaleFactor > 0.0 &&
            info.modes.isNotEmpty()
        if (!measurable) return null
        return DisplayPortDisplay(
            key = info.key,
            type = if (info.isVirtual) DisplayType.Virtual else DisplayType.Physical,
            name = info.name,
            bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(info.realWidth, info.realHeight)),
            // Android n'a pas de bureau : la workArea n'existe pas, elle n'est jamais inventée.
            workArea = null,
            scaleFactor = info.scaleFactor,
            // Un mode courant absent de la liste supportée serait une clé pendentive — null honnête.
            currentModeKey = info.currentModeId
                ?.takeIf { modeId -> info.modes.any { it.modeId == modeId } }
                ?.toLong(),
            modes = info.modes.map { mode ->
                DisplayPortMode(
                    key = mode.modeId.toLong(),
                    physicalSize = mode.physicalSize,
                    refreshRateHz = mode.refreshRateHz,
                    bitDepth = mode.bitDepth,
                )
            },
        )
    }
}

/**
 * La réalisation production du seam : le vrai DisplayManager du système, construit depuis un
 * Context. Dans la couture d'attach, le Context est l'Activity elle-même (la forme
 * `ComponentActivity.attachKadre` — `ComponentActivity` étend `Context`) ou le Context de la
 * View (la forme `View.attachKadre` — `view.context`, en pratique le même Activity context ;
 * `applicationContext` vaudrait aussi : `getSystemService(DISPLAY_SERVICE)` n'exige pas de
 * contexte d'activité). Le câblage du port dans les composants de session (l'overload INTERNAL
 * `withPrimarySurface` de `RuntimeHostController`, fait central §5 du fact sheet) est la
 * tâche d'attach suivante — ce port en est la brique.
 *
 * Le listener du SDK est contracté en UNE callback (`onDisplayAdded`/`Removed`/`Changed` →
 * l'id du display affecté, informationnel) : le port relit l'inventaire complet, il ne
 * consomme jamais l'événement comme un delta. Les fires arrivent sur le thread du [handler]
 * (le main looper par défaut) ; les relectures et l'anti-désenregistrement sont sûrs entre
 * threads côté port par son lock, et le SDK garantit ses propres lectures.
 */
internal class AndroidDisplayManagerSource(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : AndroidDisplaySource {
    private val manager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val registrations = HashMap<(Any) -> Unit, DisplayManager.DisplayListener>()

    override fun displays(): List<Any> = manager.displays.toList()

    override fun registerListener(listener: (Any) -> Unit) {
        val sdk = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = listener(displayId)
            override fun onDisplayRemoved(displayId: Int) = listener(displayId)
            override fun onDisplayChanged(displayId: Int) = listener(displayId)
        }
        synchronized(registrations) { registrations[listener] = sdk }
        manager.registerDisplayListener(sdk, handler)
    }

    override fun unregisterListener(listener: (Any) -> Unit) {
        val sdk = synchronized(registrations) { registrations.remove(listener) } ?: return
        manager.unregisterDisplayListener(sdk)
    }

    override fun displayInfo(display: Any): AndroidDisplayInfo {
        val d = display as Display
        val real = Point()
        d.getRealSize(real)
        val metrics = DisplayMetrics()
        d.getMetrics(metrics)
        return AndroidDisplayInfo(
            key = d.displayId.toLong(),
            isVirtual = (d.flags and (Display.FLAG_PRESENTATION or Display.FLAG_PRIVATE)) != 0,
            name = d.name,
            realWidth = real.x,
            realHeight = real.y,
            scaleFactor = metrics.densityDpi / 160.0,
            modes = d.supportedModes.map { mode ->
                AndroidDisplayModeInfo(
                    physicalSize = PhysicalSize(mode.physicalWidth, mode.physicalHeight),
                    refreshRateHz = mode.refreshRate.toDouble(),
                    // Le SDK n'expose pas la profondeur de bits d'un mode — Unknown honnête.
                    bitDepth = null,
                    modeId = mode.modeId,
                )
            },
            currentModeId = d.mode.modeId,
        )
    }

    override fun close() {
        val all = synchronized(registrations) {
            val snapshot = registrations.values.toList()
            registrations.clear()
            snapshot
        }
        for (sdk in all) manager.unregisterDisplayListener(sdk)
    }
}
