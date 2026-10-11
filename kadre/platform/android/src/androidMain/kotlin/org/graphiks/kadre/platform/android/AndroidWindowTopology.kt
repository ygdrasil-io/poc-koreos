package org.graphiks.kadre.platform.android

import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.RuntimeSessionWindowManager
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.RejectedWindowField
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowCapabilities
import org.graphiks.kadre.window.WindowCancellationOutcome
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowCloseRequestId
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.FullscreenKind
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowManagerCapabilities
import org.graphiks.kadre.window.WindowManagerRevision
import org.graphiks.kadre.window.WindowManagerState
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowRevision
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowState
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome

/**
 * Fenêtre primaire Activity (forme Activity uniquement) : `primary` exposée, surface partagée
 * par identité avec la surface primaire, `requestWindow` immédiatement
 * `Rejected(Unsupported(RequestWindow))`. La forme View n'installe PAS ce port : le manager
 * par défaut du runtime (`UnsupportedWindowManager`, celui que
 * `RuntimeHostController.withPrimarySurface` installe) laisse `primary == null` et la liste de
 * fenêtres vide.
 *
 * Le miroir exact du port que `RuntimeHostController.withComponents` consomme — le port
 * `WindowManager` de `WebHostWindowManager` — sur le modèle du web sans provider
 * (`WebHostWindowManager.resolveOutcome`, premier échelon) et du manager non supporté du
 * runtime (`UnsupportedWindowManager`) :
 *
 * - une fenêtre primaire unique, créée à la construction (le moment d'attach du manager) :
 *   sa `surface` est l'instance de `HostSurface` de la surface primaire, LA MÊME INSTANCE —
 *   l'assertion d'identité de la topologie (`WindowManagerState.primary ∈ windows`, invariant
 *   de `WindowManagerState`) ;
 * - `requestWindow` admet une requête dont l'outcome est déjà terminal
 *   `Rejected(Unsupported(RequestWindow))` avant que l'appelant ne la voie (le degenerate
 *   shape du modèle web, rejet synchrone sans primitive native) ;
 * - depuis la phase 4, la fenêtre primaire COMMITTE les champs supportés (`title`,
 *   `contentProtection`, `fullscreen` `Borderless`) par le seam natif [AndroidWindowNative]
 *   que la couture câble sur l'Activity réelle, publie les événements de commit sur un flux
 *   borné terminé au terminal de session, et rejette champ par champ `Unsupported(UpdateWindow)`
 *   les onze champs host-imposed ; `requestAttention` reste
 *   `Unsupported(RequestWindowAttention)`, `close` répond `Temporary(retryable = false)`
 *   (ruling fermé, voir [AndroidActivityWindow.close]) et `respondToCloseRequest` répond
 *   `Invalid("requestId")` ;
 * - sans seam natif (`native = null`, la construction des tests de topologie), la fenêtre
 *   retrouve l'instantané de la phase 1 : chaque champ demandé rejeté, aucune commande.
 *
 * Task 5 (composition) installe ce manager dans les composants de session de la forme
 * Activity, à côté de la même surface primaire ; la forme View continue d'utiliser
 * `withPrimarySurface` et ne voit jamais cette classe.
 */
/**
 * Le seam natif de la fenêtre Activity — les primitives SDK des SEULS champs committables,
 * et rien d'autre. L'inertie des champs refusés est STRUCTURELLE : le seam ne déclare
 * aucune primitive pour eux, la fenêtre ne peut pas en appeler, les tests hôtes injectent
 * un recorder qui prouve que seules les méthodes déclarées sont jamais posées.
 *
 * Les 11 champs `WindowUpdate` SANS primitive ici, chacun avec sa raison (le refus champ
 * par champ `Unsupported(UpdateWindow)` reste la forme on-row) :
 *
 * - `outerPosition` : host-imposed — le système place la fenêtre de l'Activity ; aucun
 *   primitive d'Activity ne déplace sa fenêtre ;
 * - `contentSize` : host-imposed — la taille du contenu dérive de la fenêtre système et
 *   n'est publiée que dans un sens (les métriques de la surface) ;
 * - `minimumSize` / `maximumSize` : host-imposed — aucune contrainte de taille par fenêtre
 *   pour une Activity ;
 * - `resizable` : host-imposed — `resizeableActivity` est lu par le système (multi-fenêtres),
 *   pas posable par fenêtre ;
 * - `decorations` / `systemButtons` : host-imposed — le chrome (barres système, boutons)
 *   appartient à l'Activity du host, pas à Kadre ;
 * - `level` : pas de primitive — le z-order/le type de fenêtre d'une Activity n'est pas
 *   posable par mise à jour ;
 * - `transparency` : host-imposed — la translucidité de la fenêtre d'Activity est une
 *   décision de thème/format à la création, pas par mise à jour ;
 * - `blurBehind` : host-imposed — le blur trans-fenêtres exige des fenêtres ajoutées
 *   (API 31), pas la fenêtre de l'Activity ;
 * - `icon` : pas de primitive par fenêtre — l'icône vit au manifest/niveau task ;
 * - `fullscreen` `Exclusive` : pas de primitive — le changement de mode d'affichage
 *   discret n'est pas atteignable depuis la fenêtre d'une Activity (seul `Borderless`
 *   l'est, par [setBorderlessFullscreen]).
 *
 * Back et la fermeture sont hors du seam par décision du contrôleur (phase 4, ruling
 * fermé) : Back est host-owned (Kadre n'intercepte jamais Back, n'appelle jamais
 * `finish()`, n'installe aucun callback Back) et `close()` reste
 * `TemporarilyUnavailable(retryable = false)` sans primitive — `session.close()` n'est
 * jamais une demande de fermeture native (invariant P1).
 */
internal interface AndroidWindowNative {
    public fun setTitle(title: String)
    public fun title(): String
    public fun setContentProtection(enabled: Boolean)
    public fun isContentProtectionEnabled(): Boolean
    public fun setBorderlessFullscreen(enabled: Boolean)
    public fun isSystemBarVisible(): Boolean
}

/**
 * L'implémentation PRODUCTION du seam — l'Activity et sa fenêtre, rien d'autre. Câblée par la
 * couture d'attach dans la forme Activity seule (`AndroidHostSession.attach`, branche
 * Activity) ; la forme View ne construit ni manager ni seam.
 *
 * - `title` → la paire publique `Activity.setTitle`/`getTitle` (le readback relit le titre) ;
 * - `contentProtection` → `FLAG_SECURE` ajouté/retiré aux layout flags de la fenêtre ; le
 *   readback relit `(attributes.flags and FLAG_SECURE) != 0` ;
 * - `fullscreen` `Borderless` → `WindowInsetsController.hide/show(systemBars())` — API 30+
 *   (`Window.getInsetsController` n'existe pas avant) ; en dessous, la demande est un no-op
 *   et le readback rapporte « visible » : le readback de la fenêtre répondra
 *   `TemporarilyUnavailable` plutôt que de mentir sur un commit qui n'a pas eu lieu. Avant
 *   l'attach de la fenêtre (`insetsController` absent), même discipline : rien n'est posé,
 *   le readback dit ce qu'il sait.
 *
 * Aucune primitive de plus : la structure même de [AndroidWindowNative] interdit à la
 * fenêtre d'intercepter Back, d'appeler `finish()` ou de fermer l'Activity.
 */
internal class AndroidActivityWindowNative(
    private val activity: ComponentActivity,
) : AndroidWindowNative {

    override fun setTitle(title: String) {
        activity.title = title
    }

    override fun title(): String = activity.title?.toString().orEmpty()

    override fun setContentProtection(enabled: Boolean) {
        val window = activity.window ?: return
        if (enabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun isContentProtectionEnabled(): Boolean {
        val window = activity.window ?: return false
        return (window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
    }

    override fun setBorderlessFullscreen(enabled: Boolean) {
        val controller = systemBarsController() ?: return
        if (enabled) {
            controller.hide(WindowInsets.Type.systemBars())
        } else {
            controller.show(WindowInsets.Type.systemBars())
        }
    }

    override fun isSystemBarVisible(): Boolean {
        // Le readback : les insets COURANTS du decorView (`WindowInsets.isVisible` — la
        // plateforme n'expose `isVisible` ni sur le contrôleur ni ailleurs ; c'est la même
        // vérité que le hide/show vient de poser).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        val insets = activity.window?.decorView?.rootWindowInsets ?: return true
        return insets.isVisible(WindowInsets.Type.systemBars())
    }

    /** Le contrôleur d'insets des barres système — `null` sous l'API 30 ou hors fenêtre. */
    private fun systemBarsController(): WindowInsetsController? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return activity.window?.insetsController
    }
}

internal class AndroidActivityWindowManager(
    primarySurface: HostSurface,
    private val nextRequestId: () -> WindowRequestId = RuntimeProcessIds::nextWindowRequestId,
    native: AndroidWindowNative? = null,
) : WindowManager, RuntimeSessionWindowManager {

    /**
     * La source de stamps de session, installée par le runtime pendant l'attach via
     * [RuntimeSessionWindowManager] — LA MÊME instance que le runtime remet à la surface
     * primaire dans le même `installSessionConfiguration` (le miroir du web : une session,
     * une séquence de stamps). Le holder est lu à CHAQUE émission d'événement de la fenêtre
     * (jamais capté à la construction) : avant l'install, la fenêtre publie ses commits
     * SANS événement — un `EventStamp` inventé ici serait une seconde séquence concurrente
     * de celle de la session. Volatile : écrit une fois sur le thread principal de l'attach,
     * lu par les coroutines applicatives.
     */
    @Volatile
    private var stampSource: (() -> EventStamp)? = null

    private val activityWindow: AndroidActivityWindow = AndroidActivityWindow(
        primarySurface,
        native,
    ) { stampSource }

    private val primaryWindow: Window = activityWindow

    private val mutableState = MutableStateFlow(
        WindowManagerState(
            primary = primaryWindow,
            windows = listOf(primaryWindow),
            capabilities = WindowManagerCapabilities(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
            ),
            revision = WindowManagerRevision(0),
        ),
    )

    override val state: StateFlow<WindowManagerState> = mutableState.asStateFlow()

    override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> {
        // Le même degenerate shape que le web sans provider et que `UnsupportedWindowManager` :
        // la requête est admise, mais son outcome est publié avant que l'appelant ne la voie —
        // rejet synchrone `Unsupported(RequestWindow)`, aucun budget, aucune primitive native.
        val request = AndroidActivityWindowRequest(nextRequestId())
        return KadreResult.Success(request)
    }

    /**
     * L'installation de la configuration de session par le runtime ([RuntimeSessionComponents]
     * l'appelle sur le composant `windows` qui implémente ce SPI, juste avant de l'appeler
     * sur la surface primaire — les deux branches reçoivent LA MÊME source de stamps du même
     * appel). Seule la source de stamps intéresse la fenêtre (les événements de commit) : la
     * fenêtre publie son propre flux borné (le miroir surface P2), sans gate de collecteurs —
     * l'allocateur runtime n'y est donc pas consommé, mais sa validation reste le miroir de
     * la famille (un allocateur invalide est un bug de câblage, pas une policy). Une seconde
     * installation est une erreur de programmation — une configuration construit une séquence
     * de stamps, deux n'en peuvent pas partager une.
     */
    override fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: Any,
        maxCollectorsPerFlow: Int,
        dropTransferScope: CoroutineScope?,
        diagnostics: (KadreDiagnostic) -> Unit,
        rawInputPort: RawInputPort?,
    ) {
        sessionCollectorAllocator(collectorAllocator)
        check(stampSource == null) { "the window event stamp source was already installed" }
        stampSource = source
    }

    /**
     * Le terminal du flux d'événements de la fenêtre — idempotent. La couture le branche sur
     * la teardown de session (`RuntimeSessionComponents.closeAction`, qui court après la
     * fermeture de la surface primaire) : la fenêtre d'une session morte n'a plus rien à
     * publier, les collecteurs en cours reçoivent la fin, les collecteurs suivants voient un
     * flux déjà terminé.
     */
    internal fun terminalise() {
        activityWindow.terminalise()
    }
}

/**
 * La fenêtre primaire unique de la forme Activity : l'Activity elle-même vue comme fenêtre.
 *
 * Sa `surface` est l'instance de la surface primaire passée à la construction — l'identité
 * partagée que la topologie Activity garantit. Depuis la phase 4, `title`,
 * `contentProtection` et `fullscreen` (`Borderless`) COMMITTENT par le seam natif injecté
 * ([AndroidWindowNative] — la couture y passe l'Activity réelle ; les tests un recorder)
 * avec readback effectif, révision bumpée et événement `PropertiesChanged` stampé par la
 * source de session ; les onze champs host-imposed restent rejetés champ par champ
 * `Unsupported(UpdateWindow)` (les raisons dans le KDoc du seam). Chaque commande répond
 * dans la forme exacte que sa ligne `OPERATION-CONTRACTS` §4 admet (voir les KDoc de
 * commande ci-dessous), et l'instantané initial est dérivé de la surface primaire au lieu
 * d'inventer une géométrie.
 */
private class AndroidActivityWindow(
    surface: HostSurface,
    private val native: AndroidWindowNative?,
    private val stampSource: () -> (() -> EventStamp)?,
) : Window {

    override val id: WindowId = RuntimeProcessIds.nextWindowId()

    /** LA MÊME INSTANCE que la surface primaire de la session — l'assertion de topologie. */
    override val surface: HostSurface = surface

    /**
     * La décision entière d'[apply] tient sous ce verrou — le miroir de la surface Android
     * (la garde de révision relit l'état publié et le verdict le rend ; ils doivent voir le
     * même instantané que la publication d'événement écrit). Le corps est non suspendant
     * dans le verrou : les primitives du seam sont des appels SDK synchrones.
     */
    private val lock = RuntimeLock()

    private val mutableState = MutableStateFlow(initialState(surface))

    override val state: StateFlow<WindowState> = mutableState.asStateFlow()

    override val capabilities: StateFlow<WindowCapabilities> = MutableStateFlow(
        // La table honnête AU MOMENT DE LA CONSTRUCTION : la présence du seam natif est un
        // fait de construction (la couture câble l'Activity réelle ou le test un recorder) —
        // pas besoin d'attendre une installation pour l'annoncer.
        if (native != null) androidWindowCapabilities() else unsupportedWindowCapabilities(),
    )

    /**
     * Le flux borné des événements de fenêtre — le miroir exact de la surface P2 : au plus
     * 64 événements en attente de collecte, les plus anciens abandonnés au débordement, et
     * la complétion jumelée à [terminal] (un collecteur reçoit les événements publiés
     * jusqu'au terminal, un collecteur suivant voit un flux déjà terminé).
     */
    private val mutableEvents = MutableSharedFlow<WindowEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Le terminal du flux d'événements — complété par [terminalise], le miroir de la surface. */
    private val terminal = CompletableDeferred<Unit>()

    override val events: Flow<WindowEvent> = channelFlow {
        val forwarding = launch { mutableEvents.collect { send(it) } }
        terminal.await()
        forwarding.cancel()
    }

    /** Le terminal unique du flux d'événements — idempotent (voir le manager). */
    internal fun terminalise() {
        terminal.complete(Unit)
    }

    /**
     * L'admission d'un [WindowUpdate], dans l'ordre de la référence (la surface Android pour
     * les deux premières gardes, `RuntimeWindowManager.applyWindow` pour la garde `Clear`) :
     *
     * 1. la garde de révision — un `expectedRevision` qui n'est pas le courant est un
     *    `StaleRevision(expected, current)`, AVANT toute admission de champ (la forme P1
     *    inchangée) ;
     * 2. la garde `Clear` — un `Clear` sur l'un des trois champs supportés (sans valeur
     *    « non posée ») est un `InvalidRequest(field)` AVANT l'admission — le miroir exact
     *    d'`invalidRequiredClearField` de la référence, restreint aux champs que CETTE
     *    fenêtre supporte (sans seam natif, aucun champ ne l'est) ;
     * 3. l'admission CHAMP PAR CHAMP : `title`, `contentProtection` et `fullscreen`
     *    (`Borderless`/`Windowed`) COMMITTENT par le seam natif avec readback effectif —
     *    l'état publié porte CE QUE LE NATIF RAPPORETE (`title()` après `setTitle`,
     *    `isContentProtectionEnabled()` après `setContentProtection`, la visibilité des
     *    barres après `setBorderlessFullscreen`), jamais la valeur demandée ; `Exclusive`
     *    et les onze champs host-imposed/sans primitive sont rejetés
     *    `RejectedWindowField(Unsupported(UpdateWindow))` — un rejet ne bloque jamais les
     *    champs du même update qui peuvent être honorés, et un champ refusé n'appelle
     *    AUCUNE primitive (le seam ne les déclare pas — inertie structurelle). Un readback
     *    qui ne confirme pas un commit plein écran est rapporté
     *    `TemporarilyUnavailable(retryable = true)` : la demande a été posée, la plateforme
     *    ne la reflète pas (fenêtre pas encore attachée, API < 30), la ligne §4 l'admets ;
     * 4. le verdict — une révision nouvelle pour un état qui a réellement bougé (la règle
     *    de la référence : demander la valeur déjà en effet est admis et répond `Applied`
     *    de l'état — et de la révision — intacts), `Applied` si rien n'a été rejeté,
     *    `PartiallyApplied(published, rejected)` sinon — JAMAIS une failure externe
     *    `Unsupported` pour l'update entier (contrat §4 :106-110 : un update ne portant
     *    QUE du valid-but-unsupported est ce succès sans révision et sans événement).
     *
     * L'événement du commit est UN `PropertiesChanged` portant les champs dont l'état a
     * réellement différé (dérivé de la différence avant/après — le miroir de
     * `publishStatePublication` de la référence), l'état publié, l'operationId de l'update
     * et le stamp de session. Aucun `GeometryChanged` : aucun champ committable ici ne
     * touche la géométrie (host-imposed). L'émission est muette tant que la configuration
     * de session n'est pas installée (aucun `EventStamp` inventé — le miroir de la surface
     * P2) et `tryEmit` sur le flux borné (jamais bloquante, drop documenté au débordement).
     * Toute la décision tient sous [lock] : l'ordre du flux d'événements est l'ordre des
     * stamps, lui-même l'ordre des publications.
     */
    override suspend fun apply(update: WindowUpdate): KadreResult<WindowUpdateOutcome> = lock.withLock {
        val current = mutableState.value
        update.expectedRevision?.let { expected ->
            if (expected != current.revision) {
                return KadreResult.Failure(
                    KadreFailure.StaleRevision(expected.value, current.revision.value),
                )
            }
        }
        invalidClearOnSupportedField(update)?.let { field ->
            return KadreResult.Failure(KadreFailure.InvalidRequest(field))
        }
        val operationId = RuntimeProcessIds.nextWindowOperationId()
        val rejected = mutableListOf<RejectedWindowField>()
        var next = current

        // L'admission CHAMP PAR CHAMP en ordre de déclaration de `WindowUpdate` — le miroir
        // de `changedProperties` de la référence : les onze champs host-imposed/sans
        // primitive sont rejetés sur la passe même, un champ posé jamais silencieusement
        // abandonné, un champ non posé jamais rejeté.
        val title = update.title
        if (title is PropertyChange.Set) {
            val seam = native
            if (seam == null) {
                rejected += refusedField(WindowProperty.Title)
            } else {
                seam.setTitle(title.value)
                next = next.copy(title = seam.title())
            }
        }
        if (update.outerPosition !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.OuterPosition)
        }
        if (update.contentSize !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.ContentSize)
        }
        if (update.minimumSize !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.MinimumSize)
        }
        if (update.maximumSize !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.MaximumSize)
        }
        if (update.resizable !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.Resizable)
        }
        val fullscreen = update.fullscreen
        if (fullscreen is PropertyChange.Set) {
            when (val requested = fullscreen.value) {
                // Pas de primitive : le changement de mode d'affichage discret n'est pas
                // atteignable depuis la fenêtre d'une Activity — aucun appel natif.
                is FullscreenMode.Exclusive -> rejected += refusedField(WindowProperty.Fullscreen)
                else -> {
                    val seam = native
                    if (seam == null) {
                        rejected += refusedField(WindowProperty.Fullscreen)
                    } else {
                        val borderless = requested == FullscreenMode.Borderless
                        seam.setBorderlessFullscreen(borderless)
                        if (seam.isSystemBarVisible() == borderless) {
                            // Le readback ne confirme pas le commit : la demande n'a pas pu
                            // être faite (fenêtre pas encore attachée, API < 30) — on-row.
                            rejected += RejectedWindowField(
                                WindowProperty.Fullscreen,
                                KadreFailure.TemporarilyUnavailable(retryable = true),
                            )
                        } else {
                            next = next.copy(fullscreen = requested)
                        }
                    }
                }
            }
        }
        if (update.decorations !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.Decorations)
        }
        if (update.systemButtons !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.SystemButtons)
        }
        if (update.level !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.Level)
        }
        if (update.transparency !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.Transparency)
        }
        if (update.blurBehind !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.Blur)
        }
        if (update.icon !is PropertyChange.Unchanged) {
            rejected += refusedField(WindowProperty.Icon)
        }
        val contentProtection = update.contentProtection
        if (contentProtection is PropertyChange.Set) {
            val seam = native
            if (seam == null) {
                rejected += refusedField(WindowProperty.ContentProtection)
            } else {
                seam.setContentProtection(contentProtection.value)
                next = next.copy(contentProtection = seam.isContentProtectionEnabled())
            }
        }

        // Une révision pour un état qui a réellement bougé — la révision est la marque
        // d'un changement d'état, pas d'un appel.
        val nextRevision = if (next != current) {
            check(current.revision.value < Long.MAX_VALUE) { "window revision space exhausted" }
            next.copy(revision = WindowRevision(current.revision.value + 1L))
        } else {
            next
        }
        if (nextRevision != current) mutableState.value = nextRevision
        val published = mutableState.value
        if (published != current) {
            stampSource()?.let { source ->
                mutableEvents.tryEmit(
                    WindowEvent.PropertiesChanged(
                        state = published,
                        changed = changedBetween(current, published),
                        operationId = operationId,
                        stamp = source(),
                    ),
                )
            }
        }
        KadreResult.Success(
            if (rejected.isEmpty()) {
                WindowUpdateOutcome.Applied(operationId, published)
            } else {
                WindowUpdateOutcome.PartiallyApplied(operationId, published, rejected)
            },
        )
    }

    override suspend fun requestAttention(attention: WindowAttention): KadreResult<Unit> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention))

    /**
     * Ruling du contrôleur (décision FERMÉE avant le code de la phase 4 — roadmap :115) : la
     * fenêtre d'host Activity est structurellement non fermable par Kadre — l'Activity
     * possède son cycle de vie (Back host-owned, jamais de `finish()`, jamais de callback
     * Back installé) et Kadre ne peut ni la fermer ni répondre `Unsupported(CloseWindow)`,
     * failure que la ligne `Window.close` n'admets PAS (elle n'admets exactement que
     * `Temporary` et `Platform`). La réponse on-row reste donc
     * `Temporary(retryable = false)` : la seule failure de la ligne qui dit honnêtement « la
     * commande ne peut pas aboutir ici, et réessayer ne changera rien » sans réviser le
     * contrat. Idempotente : chaque appel reçoit la même réponse, AUCUNE primitive n'est
     * posée (le seam [AndroidWindowNative] ne déclare aucune primitive de fermeture —
     * inertie structurelle, et `session.close()` n'est jamais une demande de fermeture
     * native, invariant P1).
     */
    override suspend fun close(): KadreResult<WindowCloseOutcome> =
        KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false))

    /**
     * Véridique et terminal : aucun événement `CloseRequested` n'est jamais publié (Back est
     * host-owned — Kadre n'installe aucun callback Back, n'appelle jamais `finish()`, la
     * fenêtre du host ne demande jamais de fermeture à Kadre) — donc aucun `requestId` ne
     * peut jamais être valide. `Invalid("requestId")` est le field exact que le registre
     * `InvalidRequest` d'`OPERATION-CONTRACTS` réserve à cette opération, et la ligne
     * l'admets.
     */
    override suspend fun respondToCloseRequest(
        requestId: WindowCloseRequestId,
        decision: WindowCloseDecision,
    ): KadreResult<WindowCloseResponseOutcome> =
        KadreResult.Failure(KadreFailure.InvalidRequest("requestId"))

    /** L'instantané initial, dérivé de la surface primaire — aucune géométrie inventée.
     *
     * Les valeurs des trois champs committables sont les MINIMES de la phase 1 (`""`,
     * `false`, `Windowed`) : l'instantané honnête d'une fenêtre dont rien n'a été lu — la
     * première mise à jour d'un champ lit le natif et publie sa valeur effective (le
     * readback corrige ; demander la valeur déjà en effet ne bouge ni l'état ni la
     * révision). Une lecture initiale du `FLAG_SECURE`/du titre réel de l'Activity
     * inventerait un « apply » que personne n'a demandé.
     */
    private fun initialState(surface: HostSurface): WindowState {
        val snapshot = surface.state.value
        return WindowState(
            phase = WindowPhase.Open,
            title = "",
            outerBounds = null,
            contentSize = snapshot.logicalSize,
            minimumSize = null,
            maximumSize = null,
            resizable = false,
            fullscreen = FullscreenMode.Windowed,
            decorations = WindowDecorations.System,
            systemButtons = WindowSystemButtons.All,
            level = WindowLevel.Normal,
            transparent = false,
            blurBehind = false,
            icon = null,
            contentProtection = false,
            revision = WindowRevision(0),
        )
    }

    /**
     * La garde `Clear` des trois champs supportés — le miroir d'`invalidRequiredClearField`
     * de la référence, restreint aux champs que CETTE fenêtre supporte (sans seam natif,
     * aucun champ ne l'est et un `Clear` tombe dans le rejet champ par champ ordinaire).
     */
    private fun invalidClearOnSupportedField(update: WindowUpdate): String? {
        if (native == null) return null
        return when {
            update.title is PropertyChange.Clear -> "title"
            update.contentProtection is PropertyChange.Clear -> "contentProtection"
            update.fullscreen is PropertyChange.Clear -> "fullscreen"
            else -> null
        }
    }

    /** Le refus champ par champ d'un champ sans primitive — la forme on-row du contrat §4. */
    private fun refusedField(field: WindowProperty) =
        RejectedWindowField(field, KadreFailure.Unsupported(KadreOperation.UpdateWindow))

    /**
     * Les champs dont l'état a réellement différé — dérivés de la différence avant/après
     * (le miroir de `publishStatePublication` de la référence) : jamais un champ que
     * l'update n'a pas fait bouger, même s'il a été posé. La géométrie ne peut pas figurer
     * ici : aucun champ committable de cette fenêtre ne la touche.
     */
    private fun changedBetween(before: WindowState, after: WindowState): Set<WindowProperty> = buildSet {
        if (before.title != after.title) add(WindowProperty.Title)
        if (before.contentProtection != after.contentProtection) add(WindowProperty.ContentProtection)
        if (before.fullscreen != after.fullscreen) add(WindowProperty.Fullscreen)
    }
}

/**
 * Les capacités de fenêtre SANS seam natif — l'instantané de la phase 1 : toutes non
 * supportées, chaque opération nommée. Les constructions sans recorder (tests de topologie,
 * un attach hypothétique sans fenêtre native) vivent ici ; la construction production
 * (Activity-form, seam câblé) publie [androidWindowCapabilities].
 */
private fun unsupportedWindowCapabilities(): WindowCapabilities {
    val unsupportedUpdate = KadreFailure.Unsupported(KadreOperation.UpdateWindow)
    return WindowCapabilities(
        title = Capability.Unsupported(unsupportedUpdate),
        outerPosition = Capability.Unsupported(unsupportedUpdate),
        contentSize = Capability.Unsupported(unsupportedUpdate),
        minimumSize = Capability.Unsupported(unsupportedUpdate),
        maximumSize = Capability.Unsupported(unsupportedUpdate),
        resizable = Capability.Unsupported(unsupportedUpdate),
        fullscreen = Capability.Unsupported(unsupportedUpdate),
        decorations = Capability.Unsupported(unsupportedUpdate),
        systemButtons = Capability.Unsupported(unsupportedUpdate),
        level = Capability.Unsupported(unsupportedUpdate),
        transparency = Capability.Unsupported(unsupportedUpdate),
        blurBehind = Capability.Unsupported(unsupportedUpdate),
        icon = Capability.Unsupported(unsupportedUpdate),
        contentProtection = Capability.Unsupported(unsupportedUpdate),
        attention = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention)),
        closeInterception = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RespondToCloseRequest)),
        platformAccess = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess)),
    )
}

/**
 * La table honnête des capacités de fenêtre AVEC le seam natif câblé (Activity-form) :
 * `title`, `contentProtection` et `fullscreen` (`Borderless` seul — `Exclusive` n'a pas de
 * primitive) sont `Supported(..., Available)` ; les onze autres restent
 * `Unsupported(UpdateWindow)` avec les raisons portées par le KDoc d'[AndroidWindowNative]
 * (host-imposed ou pas de primitive). `attention`, `closeInterception` et
 * `platformAccess` ne sont pas affaiblis : la fenêtre ne publie aucun `CloseRequested`
 * (Back est host-owned), n'interpelle pas l'attention et n'expose aucun handle
 * `android.view.Window`.
 */
private fun androidWindowCapabilities(): WindowCapabilities {
    val unsupportedUpdate = KadreFailure.Unsupported(KadreOperation.UpdateWindow)
    return WindowCapabilities(
        title = Capability.Supported(Unit, FeatureAvailability.Available),
        outerPosition = Capability.Unsupported(unsupportedUpdate),
        contentSize = Capability.Unsupported(unsupportedUpdate),
        minimumSize = Capability.Unsupported(unsupportedUpdate),
        maximumSize = Capability.Unsupported(unsupportedUpdate),
        resizable = Capability.Unsupported(unsupportedUpdate),
        fullscreen = Capability.Supported(setOf(FullscreenKind.Borderless), FeatureAvailability.Available),
        decorations = Capability.Unsupported(unsupportedUpdate),
        systemButtons = Capability.Unsupported(unsupportedUpdate),
        level = Capability.Unsupported(unsupportedUpdate),
        transparency = Capability.Unsupported(unsupportedUpdate),
        blurBehind = Capability.Unsupported(unsupportedUpdate),
        icon = Capability.Unsupported(unsupportedUpdate),
        contentProtection = Capability.Supported(Unit, FeatureAvailability.Available),
        attention = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindowAttention)),
        closeInterception = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RespondToCloseRequest)),
        platformAccess = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess)),
    )
}

/**
 * La requête que `requestWindow` rend : son outcome est déjà terminal au rejet
 * `Unsupported(RequestWindow)` avant que l'appelant ne la voie, donc les sémantiques de
 * référence sont triviales — `cancel()` répond `AlreadyTerminated`, `await()` ne suspend pas
 * et `close()` ne libère rien (aucun budget n'a été consommé).
 */
private class AndroidActivityWindowRequest(
    override val id: WindowRequestId,
) : WindowRequest {
    private val outcome = WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))
    private val mutableState = MutableStateFlow<WindowRequestState>(WindowRequestState.Terminated(outcome))

    override val state: StateFlow<WindowRequestState> = mutableState.asStateFlow()

    override fun close() = Unit

    override suspend fun cancel(): WindowCancellationOutcome =
        WindowCancellationOutcome.AlreadyTerminated(outcome)

    override suspend fun await(): WindowRequestOutcome = outcome
}
