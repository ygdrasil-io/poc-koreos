package org.graphiks.kadre.platform.android

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.VisibilityState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponentsFactory
import org.graphiks.kadre.internal.runtime.RuntimeSessionRevocationHandler
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility

/**
 * Les points d'entrée publics d'attach Android (DESIGN §15.1) : les quatre formes
 * Activity/View, et depuis la phase 11 les deux formes présentation
 * ([attachKadreSecondaryDisplay]) — le média secondaire monté sur un écran dédié via le
 * seam `Presentation`.
 *
 * Contrat de thread (décision de phase 0 maintenue) : appel sur le thread principal Android,
 * non suspendant, sans blocage ni relocation. La violation de thread est une erreur de
 * programmation — `IllegalStateException` rejetée avant toute mutation d'état ; elle n'est
 * PAS un `KadreResult`.
 *
 * Toute autre admission refusée est un `KadreResult.Failure` sans exception — l'échelle
 * normative nomme le field fautif (`InvalidRequest(field)`, `ParentScopeCancelled`,
 * `Closed(Host)` pour un owner détruit, `AlreadyInUse(Host)` pour une identité déjà
 * réservée). La session retournée par un succès a démarré et reste `Running` jusqu'à la
 * terminaison effective (stop coopératif, parent annulé, détachement du host — view
 * détachée ou owner détruit —, échec) ; l'ownership des identités natives est libéré à
 * cette terminaison, sur tous les chemins.
 */
public fun ComponentActivity.attachKadre(
    surfaceView: View,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleScope,
): KadreResult<KadreSession> = AndroidHostSession.attach(
    request = activityAttachRequest(surfaceView, parentScope),
    owner = this,
    view = surfaceView,
    parentScope = parentScope,
    applicationFactory = applicationFactory,
    policy = policy,
)

/** La variante « application » : délègue à la variante factory (le modèle exact des overloads web). */
public fun ComponentActivity.attachKadre(
    surfaceView: View,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleScope,
    application: KadreApplication,
): KadreResult<KadreSession> = attachKadre(
    surfaceView = surfaceView,
    applicationFactory = KadreApplicationFactory { application },
    policy = policy,
    parentScope = parentScope,
)

public fun View.attachKadre(
    lifecycleOwner: LifecycleOwner,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleOwner.lifecycleScope,
): KadreResult<KadreSession> = AndroidHostSession.attach(
    request = viewAttachRequest(lifecycleOwner, parentScope),
    owner = lifecycleOwner,
    view = this,
    parentScope = parentScope,
    applicationFactory = applicationFactory,
    policy = policy,
)

/** La variante « application » : délègue à la variante factory (le modèle exact des overloads web). */
public fun View.attachKadre(
    lifecycleOwner: LifecycleOwner,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleOwner.lifecycleScope,
    application: KadreApplication,
): KadreResult<KadreSession> = attachKadre(
    lifecycleOwner = lifecycleOwner,
    applicationFactory = KadreApplicationFactory { application },
    policy = policy,
    parentScope = parentScope,
)

/**
 * La forme présentation (phase 11) : monte `contentView` dans un média `Presentation` sur
 * l'écran dédié `presentationDisplay` et y attache une session Kadre SECONDAIRE — la session
 * principale de la même Activity peut vivre en parallèle (la forme présentation ne réserve
 * JAMAIS l'identité de l'Activity ; elle revendique le contentView — qui doit être LIBRE,
 * Kadre le monte dans la présentation — et le displayId).
 *
 * L'admission : thread principal (erreur de programmation sinon) ; `parentScope` actif ;
 * contentView détaché sinon `InvalidRequest("contentView")` ; écran résolvable
 * (`displayManager.getDisplay(displayId) != null`) sinon `InvalidRequest("presentationDisplay")` ;
 * Activity non détruite sinon `Closed(Host)` ; contentView OU displayId déjà réservés sinon
 * `AlreadyInUse(Host)` — tout ou rien.
 *
 * Un hôte déjà STARTED montre le média dès l'attach (premier show level-triggered) ; sinon
 * le premier START le montre. Le STOP le cache — le média survit aux cycles, jamais deux
 * présentations ; le retrait de l'écran ferme la surface et termine la session sans
 * résurrection. L'entrée de la fenêtre du média est observée par la chaîne `Window.Callback`
 * posée au premier show réussi (une seule fois pour la vie du média — la fenêtre survit à
 * hide/show) et alimente l'input de LA session présentation, jamais la session principale ;
 * la fermeture du média restitue la callback d'origine exacte. La session ne revendique
 * AUCUN champ fenêtre en v1 (`native = null` — la table des capabilities non supportées) ;
 * appelable après `onCreate` (l'installation observe le lifecycle et le decorView de
 * l'Activity).
 */
public fun ComponentActivity.attachKadreSecondaryDisplay(
    presentationDisplay: Display,
    contentView: View,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleScope,
): KadreResult<KadreSession> = AndroidHostSession.attachSecondaryDisplay(
    request = presentationAttachRequest(presentationDisplay, contentView, parentScope),
    parentScope = parentScope,
    applicationFactory = applicationFactory,
    policy = policy,
    sourceFactory = { AndroidSdkPresentationSource(this, presentationDisplay, contentView) },
    installHostLifecycle = { lifecycle -> lifecycle.install(this, window.decorView) },
    displayPortFactory = { AndroidDisplayPort(AndroidDisplayManagerSource(contentView.context)) },
    // L'installateur d'entrée de la forme présentation : la fenêtre réelle du JETON du média
    // (le contentView y vit — le focus observé est le sien), le câblage de LA session
    // présentation — le même code d'installation que la forme Activity, une seule vérité.
    inputChainFactory = { windowToken, wiring ->
        windowInputChainFor(windowToken as? Window, contentView, wiring)
    },
)

/**
 * L'installateur FENÊTRE-PARAMÉTRÉ de la chaîne d'entrée (phase 11, Task 3) : le MÊME
 * forwarder T1 pour les deux formes fenêtrées — la forme Activity passe la fenêtre de
 * l'Activity ([AndroidHostSession] y délègue), la forme présentation la fenêtre du jeton du
 * média (`attachKadreSecondaryDisplay`) — un seul code d'installation, jamais deux.
 *
 * Le forwarder enchaîne le callback EXISTANT de la fenêtre (la paire publique get/set — la
 * discipline « no listener replacement », jamais de remplacement, jamais de réflexion),
 * observe la porte de focus de la vue ciblée (`view.hasFocus()` AU MOMENT de l'événement —
 * une présentation ne prend PAS le focus par décret, la porte l'observe), et route
 * observations/consommation/motion vers le câblage de SESSION que la couture remet — chaque
 * session reçoit son input, jamais celui d'une autre.
 *
 * `null` (pas de fenêtre, ou fenêtre sans callback) : pas de chaîne — l'observation
 * clavier/tactile vit là où la couture peut enchaîner, jamais une promesse vide.
 */
internal fun windowInputChainFor(
    window: Window?,
    view: View,
    wiring: AndroidSessionInputWiring,
): AndroidWindowInputChain? {
    val wrapped = window?.callback ?: return null
    return AndroidForwarderInputChain(
        AndroidWindowCallbackForwarder(
            wrapped = wrapped,
            getWindowCallback = { window.callback },
            setWindowCallback = { window.callback = it },
            isTargetFocused = { view.hasFocus() },
            onObservation = wiring.onObservation,
            shouldConsumeKey = wiring.shouldConsumeKey,
            onMotion = { observation, offsetX, offsetY ->
                wiring.onMotion(observation, view.width, view.height, offsetX, offsetY)
            },
            // L'origine de la View DANS l'espace fenêtre — la paire publique
            // `getLocationOnScreen` de la View et du decorView de CETTE fenêtre, lue à
            // chaque dispatch (les positions fenêtre et parent-relatives peuvent diverger
            // à tout layout) : le tracker convertit les coordonnées fenêtre du dispatch
            // `Window.Callback` en view-local avant ses gates.
            viewOffsetInWindow = {
                val viewLocation = IntArray(2)
                view.getLocationOnScreen(viewLocation)
                val decorLocation = IntArray(2)
                window.decorView.getLocationOnScreen(decorLocation)
                viewLocation[0] - decorLocation[0] to viewLocation[1] - decorLocation[1]
            },
            onPointerCaptureState = wiring.onPointerCaptureState,
        ),
    )
}

/** La variante « application » : délègue à la variante factory (le modèle exact des overloads web). */
public fun ComponentActivity.attachKadreSecondaryDisplay(
    presentationDisplay: Display,
    contentView: View,
    policy: KadrePolicy = KadrePolicies.Default,
    parentScope: CoroutineScope = lifecycleScope,
    application: KadreApplication,
): KadreResult<KadreSession> = attachKadreSecondaryDisplay(
    presentationDisplay = presentationDisplay,
    contentView = contentView,
    applicationFactory = KadreApplicationFactory { application },
    policy = policy,
    parentScope = parentScope,
)

/** L'entrée de l'échelle pour la forme Activity — identités natives = instances. */
private fun ComponentActivity.activityAttachRequest(
    surfaceView: View,
    parentScope: CoroutineScope,
): AndroidAttachRequest = AndroidAttachRequest(
    form = AndroidAttachForm.Activity,
    isMainThread = Looper.myLooper() == Looper.getMainLooper(),
    view = surfaceView,
    isViewAttachedToWindow = surfaceView.isAttachedToWindow,
    viewRootIdentity = surfaceView.rootView,
    activityIdentity = this,
    decorRootIdentity = window?.decorView,
    ownerState = lifecycle.currentState.ordinal,
    parentScopeJob = parentScope.coroutineContext[Job],
    parentScopeJobIsActive = parentScope.coroutineContext[Job]?.isActive == true,
)

/** L'entrée de l'échelle pour la forme View — pas d'identité Activity à réserver. */
private fun View.viewAttachRequest(
    lifecycleOwner: LifecycleOwner,
    parentScope: CoroutineScope,
): AndroidAttachRequest = AndroidAttachRequest(
    form = AndroidAttachForm.View,
    isMainThread = Looper.myLooper() == Looper.getMainLooper(),
    view = this,
    isViewAttachedToWindow = isAttachedToWindow,
    viewRootIdentity = rootView,
    activityIdentity = null,
    decorRootIdentity = null,
    ownerState = lifecycleOwner.lifecycle.currentState.ordinal,
    parentScopeJob = parentScope.coroutineContext[Job],
    parentScopeJobIsActive = parentScope.coroutineContext[Job]?.isActive == true,
)

/**
 * L'entrée de l'échelle pour la forme présentation — les faits SDK y sont lus UNE fois,
 * avant l'échelle pure : le drapeau d'attachement du contentView (qui doit être LIBRE), la
 * clé de l'écran visé et sa résolvabilité (le rung `presentationDisplay` de l'échelle).
 */
private fun ComponentActivity.presentationAttachRequest(
    presentationDisplay: Display,
    contentView: View,
    parentScope: CoroutineScope,
): AndroidPresentationAttachRequest = AndroidPresentationAttachRequest(
    isMainThread = Looper.myLooper() == Looper.getMainLooper(),
    contentView = contentView,
    isContentViewAttachedToWindow = contentView.isAttachedToWindow,
    displayKey = presentationDisplay.displayId.toLong(),
    isDisplayResolvable = presentationDisplayResolvable(presentationDisplay),
    ownerState = lifecycle.currentState.ordinal,
    parentScopeJob = parentScope.coroutineContext[Job],
    parentScopeJobIsActive = parentScope.coroutineContext[Job]?.isActive == true,
)

/** Le rung « écran résolvable » : le DisplayManager du process doit encore retourner l'écran visé. */
private fun ComponentActivity.presentationDisplayResolvable(presentationDisplay: Display): Boolean =
    (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
        .getDisplay(presentationDisplay.displayId) != null

/**
 * La couture commune aux quatre overloads (DESIGN §15.1) : l'échelle d'admission, la
 * réservation atomique, le binding lifecycle, puis l'attach du runtime — avec rollback
 * intégral et `KadreResult` sur chaque échec.
 *
 * Le chemin nominal :
 * 1. l'échelle ([evaluateAndroidAttach], pure) rejette — violation de thread :
 *    `IllegalStateException` (erreur de programmation, décision de phase 0) ; toute autre
 *    denial : `Failure(denial.failure)` SANS exception ;
 * 2. la réservation atomique des identités ([AndroidAttachReservation.acquire]) — refus :
 *    `Failure(AlreadyInUse(Host))`, l'état d'avant la demande est intact ;
 * 3. le contrôleur de la forme est construit — Activity : `withComponents` +
 *    [AndroidActivityWindowManager] ; View : `withPrimarySurface` — avec la libération de la
 *    réservation branchée sur la terminaison effective de la session
 *    ([RuntimeSessionObserver], tous chemins, idempotent) et le retrait du binding branché
 *    sur la révocation (la couture de phase 0 : le runtime rend les ponts de la View avant
 *    de fermer la surface) ;
 * 4. LE binding lifecycle — un par attach, construit à frais et installé exactement une fois
 *    ici, ce qui encode la garde single-use au seul endroit qui crée des bindings — installe
 *    ses observateurs et relit les préconditions : un détachement ou une destruction survenus
 *    pendant l'installation produisent le terminal immédiatement ;
 * 5. `controller.attach(parentScope, applicationFactory, policy, launch = null)` — succès :
 *    `Success(session)` ; échec : **rollback intégral** (teardown du binding, rollback de
 *    la réservation) puis `Failure(reason)`. Le contrat d'exception exact de ce chemin
 *    ([installBindingAndAttach]) : les refus d'admission et les rejets du contrôleur
 *    deviennent des `KadreResult` ; les erreurs de programmation et les throwables SDK —
 *    installation du binding (`addObserver`/`addOnAttachStateChangeListener`), construction
 *    du contrôleur, `session.start()`/`clockFactory.start()` dans `controller.attach` — se
 *    propagent TELS QUELS, mais seulement APRÈS le rollback intégral : jamais avec la
 *    réservation encore tenue (la View resterait in-attachable jusqu'à la mort du
 *    processus), jamais reconverti en une failure que la taxonomie n'admets pas.
 *
 * Les publications ordinaires du réducteur deviennent l'état lifecycle du runtime
 * (`RuntimeHostController.updateLifecycle`, RUN-004 — le modèle de la couture web) en plus
 * de l'état de la surface ; la fenêtre post-detach est absorbée
 * ([propagateToRuntime]). Le terminal du binding (view détachée, owner détruit) emprunte la
 * proposition d'arrêt de host du runtime : [RuntimeHostController.detach] publie l'état
 * détaché et propose `SessionOutcome.Stopped(SessionStopReason.HostDetached)` à la session —
 * aucun chemin parallèle n'est inventé, et une proposition perdue est signalée sur le canal
 * de diagnostic de la couture ([AndroidAttachDiagnostics]) plutôt qu'avalaée en silence.
 * Les deux callbacks que le binding reçoit sont des wrappers
 * never-throw : un callback qui lance ne peut ni court-circuiter `onTerminal` ni remonter
 * dans la boucle lifecycle du host (roadmap §3.3 — les callbacks ordinaires ne suspendent
 * pas et ne lancent pas directement le code applicatif).
 */
internal object AndroidHostSession {

    /**
     * Le registre d'ownership process-wide — une seule instance pour toutes les attaches, les
     * formes Activity/View comme les formes présentation (c'est le PARTAGE des tables qui fait
     * la coexistence : un contentView ou un displayId pris par une forme refute l'autre).
     */
    private val ownership = AndroidViewOwnership()

    /**
     * La source de pression mémoire process-wide (Task 3, modèle AppKit) — UNE source pour
     * toutes les attaches du processus : installée à la première session, réutilisée par les
     * suivantes, fermée par la terminaison du DERNIER host vivant (une fermeture de session
     * n'y touche jamais).
     */
    private val memoryPressure = AndroidProcessMemoryPressure()

    /** Une denial de l'échelle devient le `KadreFailure` exact d'`attachKadre` — sans exception. */
    internal fun toFailure(denial: AndroidAttachDenial): KadreFailure = denial.failure

    internal fun attach(
        request: AndroidAttachRequest,
        owner: LifecycleOwner,
        view: View,
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy,
    ): KadreResult<KadreSession> {
        // 1. L'échelle normative : thread → scope → view/decor → owner détruit → conflit.
        val denial = evaluateAndroidAttach(request, ownership)
        if (denial != null) return KadreResult.Failure(toFailure(denial))

        // 2. La réservation atomique des identités — refus : Busy(Host), état intact.
        val reservation = AndroidAttachReservation.acquire(ownership, request.activityIdentity, request.view)
            ?: return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))

        // La source mémoire process (Task 3, modèle AppKit) : le créneau de CETTE attach est
        // pris AVANT le contrôleur — sa capability (`Available` après l'install réussie de la
        // source process, `Unavailable(PlatformFailure(Android, "memory-pressure",
        // "source-exception"))` après un échec du register, le défaut runtime sans
        // Application atteignable) devient le `initialLifecycleCapabilities` du contrôleur :
        // la transition réelle exigée par la matrice C, publiée seulement après le succès
        // d'install. La source vit au-delà des sessions ; le créneau meurt avec le host
        // (ou le rollback d'un attach qui a fait installer).
        val pressureClaim = memoryPressure.newHost(memoryPressureSeamFor(request.form, owner, view))

        // L'instantané initial honnête, partagé par la surface ET le réducteur : les deux
        // démarrent des mêmes faits observés, au même moment, sur le thread principal — la
        // déduplication du réducteur et l'état publié de la surface ne peuvent pas diverger.
        val initial = AndroidHostSnapshot(
            attachment = view.isAttachedToWindow,
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )

        // 3. Le contrôleur de la forme. Le binding n'existe pas encore : la révocation passe
        //    par la variable locale, assignée avant tout chemin qui peut la lire (le thread
        //    principal est le seul à attacher, dispatcher et terminer).
        var binding: AndroidLifecycleBinding? = null
        val sessionSurface = SessionSurface()
        // Le branchement métriques (Task 5) : le réducteur démarre de la MÊME vérité initiale
        // que la surface (le même instantané, la même fonction, le même instant du thread
        // principal) — ses décisions et les publications de la surface ne peuvent pas diverger.
        // La surface peut ne pas exister encore au moment d'une observation (installation du
        // binding avant l'attach du contrôleur) : elle est alors perdue, la suivante rétablit.
        val metricsReducer = AndroidSurfaceMetricsReducer(initialSurfaceState(view, initial))
        val observationBinding = AndroidSurfaceObservationBinding(
            view = view,
            reducer = metricsReducer,
            surface = { sessionSurface.surface },
        )
        val controller = when (request.form) {
            AndroidAttachForm.Activity -> RuntimeHostController.withComponents(
                platform = KadrePlatform.Android,
                sessionRevocationHandler = RuntimeSessionRevocationHandler { _ -> binding?.teardown() },
                componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                    // UNE instance de surface par session, passée au manager de fenêtres ET à
                    // la surface primaire : l'invariant de topologie
                    // `windows.state.value.primary?.surface === primarySurface` est un fait de
                    // session, pas une coïncidence de construction. `leasedView` est LA source
                    // du bail natif (Task 4) : la View est relue à chaque lease, jamais capturée.
                    // Le seam du curseur (phase 4, Task 2) est câblé sur la MÊME View —
                    // `pointerIcon` est une propriété de `View`, la forme Activity n'y a aucun
                    // monopole (le miroir du branchement métriques, pas des commandes de
                    // fenêtre). Le seam de capture (phase 4, Task 3) l'est aussi — la capture
                    // est une propriété de `View` — et le canal d'état du SDK (la notification
                    // `Window.Callback.onPointerCaptureChanged`, enchaînée ci-dessous par le
                    // wrapper T1) est retenu pour le livrer au listener du seam.
                    val captureNative = AndroidViewPointerCaptureNative(view)
                    sessionSurface.capture = captureNative
                    // Le seam natif de la fenêtre (phase 4, Task 1) câblé sur CETTE Activity —
                    // construit UNE fois et remis aux DEUX consommateurs : le manager (les
                    // commandes de fenêtre) et la surface (les interactions
                    // `EnterFullscreen`/`ExitFullscreen` de Task 4) — le MÊME insets
                    // controller, une seule vérité de readback pour le champ et l'interaction.
                    val windowSeam = AndroidActivityWindowNative(owner as ComponentActivity)
                    // Les ports P7 (Task 3) : UN seam InputManager partagé par les DEUX ports
                    // d'entrée (devices génériques + manette — la même énumération, le même
                    // listener multi-cast), fermé par la couture à la teardown des composants
                    // (le contrat du seam : AUCUN des deux ports ne ferme). Le port display a
                    // le sien, qu'il possède et ferme avec lui. La forme ACTIVITY passe les
                    // trois : la chaîne d'entrée est câblée ici (le wrapper T1, Task 3 de la
                    // phase d'entrée), l'état gamepad y est donc routé — les touches et les
                    // axes source gamepad/joystick QUITTENT le chemin ordinaire pour nourrir
                    // le port ([AndroidHostSurface.attachGamepadRoute], branché ci-dessous
                    // avant l'installation de la configuration, la delivery qui naît le lira).
                    val inputSeam = AndroidInputManagerSource(view.context)
                    val displayPort = AndroidDisplayPort(AndroidDisplayManagerSource(view.context))
                    val inputDevicePort = AndroidInputDevicePort(inputSeam)
                    val gamepadPort = AndroidGamepadPort(inputSeam, Build.VERSION.SDK_INT)
                    val surface = AndroidHostSurface(
                        RuntimeProcessIds.nextSurfaceId(),
                        initialSurfaceState(view, initial),
                        leasedView = { view },
                        cursorNative = AndroidViewCursorNative(view),
                        pointerCaptureNative = captureNative,
                        windowNative = windowSeam,
                    )
                    sessionSurface.surface = surface
                    // Le port de capture de CETTE session (phase 8) : capabilities sondées UNE
                    // fois à cette construction (l'API courante gèle la capability surface —
                    // PixelCopy est 26+), sources HostPickerOnly, Source refusé avant tout seam.
                    // La surface primaire y est enregistrée à sa création — le miroir
                    // `registerPrimarySurface` du web, branche composants. Les coutures de la
                    // Task 2 : le consentement par le registre DIRECT de CETTE Activity (la
                    // correction de l'anti-pattern kadre-old), le FGS `mediaProjection` Kadre
                    // (le ruling P8-0 — le host ne déclare RIEN), l'ImageReader et les faits
                    // d'affichage de CETTE vue. La forme View câble les mêmes coutures, le
                    // consentement déplié du contexte de la vue.
                    val capturePort = AndroidCapturePort(
                        apiLevel = Build.VERSION.SDK_INT,
                        surfaces = AndroidHostCaptureSurfaceRegistry(),
                        consent = AndroidActivityCaptureConsentSource(owner as ComponentActivity),
                        projections = AndroidMediaProjectionSource(view.context.applicationContext),
                        imageReaders = AndroidImageReaderSource(),
                        foreground = AndroidServiceCaptureForeground(view.context.applicationContext),
                        displayFacts = AndroidCaptureDisplayFacts(
                            size = PhysicalSize(
                                maxOf(1, view.resources.displayMetrics.widthPixels),
                                maxOf(1, view.resources.displayMetrics.heightPixels),
                            ),
                            densityDpi = view.resources.displayMetrics.densityDpi,
                        ),
                    )
                    capturePort.registerHostCaptureSurface(surface.id, view)
                    // Le routage gamepad de CETTE session — la delivery (construite plus tard,
                    // pendant l'attach) copiera la route à sa naissance ; les events source
                    // gamepad/joystick ne rejoignent JAMAIS le chemin ordinaire de la surface.
                    surface.attachGamepadRoute(gamepadPort)
                    // Le manager implémente `RuntimeSessionWindowManager` : le runtime lui
                    // installe la configuration de session (LA MÊME source de stamps que la
                    // surface) pendant l'attach, et `closeAction` termine le flux d'événements
                    // de la fenêtre à la teardown (après la fermeture de la surface) ET ferme
                    // le seam InputManager partagé (les ports, fermés après lui par le
                    // runtime, désenregistrent alors dans un registre déjà vide — sans effet).
                    val manager = AndroidActivityWindowManager(
                        primarySurface = surface,
                        native = windowSeam,
                    )
                    RuntimeSessionComponents(
                        windows = manager,
                        primarySurface = RuntimePrimarySurface(surface, surface::close),
                        displayPort = displayPort,
                        gamepadPort = gamepadPort,
                        inputDevicePort = inputDevicePort,
                        capturePort = capturePort,
                        closeAction = { manager.terminalise(); inputSeam.close() },
                    )
                },
                initialLifecycleState = lifecycleStateOf(initial),
                initialLifecycleCapabilities = pressureClaim.capabilities,
                failureReporter = AndroidAttachDiagnostics,
                sessionObserver = RuntimeSessionObserver { _, _ -> reservation.release() },
            )

            // Les ports P7 de la forme View (Task 3) : le display et les devices génériques,
            // construits ici (leurs constructeurs ne posent AUCUNE registration SDK — les
            // listeners ne naissent qu'à l'installation de l'observateur runtime) et remis à
            // l'overload interne du runtime, qui les place dans les composants de la session :
            // fermés avec elle. Chaque port reçoit SON seam — le runtime ne donne pas de
            // `closeAction` ici, un port seul par seam fait de son close la désregistration
            // SDK entière (rien ne reste posé). PAS de port gamepad : la forme View n'a pas
            // de fenêtre, donc pas de `Window.Callback` à enchaîner — AUCUN canal d'état
            // gamepad n'existe sur cette forme ; inventer le port y promettrait des états
            // qui ne peuvent pas arriver. L'inventaire gamepad reste celui que le runtime
            // substitue (les devices génériques via le port vide), honnête et sans état.
            // Le display est observable depuis le contexte de la vue
            // (`getSystemService(DISPLAY_SERVICE)` n'exige pas de contexte d'activité).
            // Le port de capture de CETTE forme (phase 8) : construit AVANT le contrôleur —
            // l'overload interne le place dans les composants de la session, fermé avec elle —
            // et la surface primaire y est enregistrée à sa création dans la factory ci-dessous
            // (le miroir `registerPrimarySurface` du web, branche sans fenêtre). Mêmes
            // capabilities gelées, mêmes sources HostPickerOnly, même refus structurel de
            // `Source` ; les coutures de la Task 2 : le consentement déplié de l'activité qui
            // héberge la vue (`activityOf`, le registre DIRECT — la forme View n'a pas
            // d'activité typée ; sans activité hébergeante, le port répond `Unsupported
            // (CapturePermission)` à une demande plutôt que de deviner), le FGS Kadre sur le
            // contexte applicatif, l'ImageReader, les faits d'affichage de CETTE vue. Le bras
            // PixelCopy est la réservation de production par défaut (`androidPixelCopyReservation`,
            // le seam `AndroidPixelCopyNative` et son impl `AndroidLivePixelCopy`) : la surfaceView
            // enregistrée par la factory ci-dessous est copiée par le `PixelCopy.request` réel.
            AndroidAttachForm.View -> {
                val capturePort = AndroidCapturePort(
                    apiLevel = Build.VERSION.SDK_INT,
                    surfaces = AndroidHostCaptureSurfaceRegistry(),
                    consent = activityOf(view.context)?.let(::AndroidActivityCaptureConsentSource),
                    projections = AndroidMediaProjectionSource(view.context.applicationContext),
                    imageReaders = AndroidImageReaderSource(),
                    foreground = AndroidServiceCaptureForeground(view.context.applicationContext),
                    displayFacts = AndroidCaptureDisplayFacts(
                        size = PhysicalSize(
                            maxOf(1, view.resources.displayMetrics.widthPixels),
                            maxOf(1, view.resources.displayMetrics.heightPixels),
                        ),
                        densityDpi = view.resources.displayMetrics.densityDpi,
                    ),
                )
                RuntimeHostController.withPrimarySurface(
                    platform = KadrePlatform.Android,
                    sessionRevocationHandler = RuntimeSessionRevocationHandler { _ -> binding?.teardown() },
                    initialLifecycleState = lifecycleStateOf(initial),
                    initialLifecycleCapabilities = pressureClaim.capabilities,
                    failureReporter = AndroidAttachDiagnostics,
                    sessionObserver = RuntimeSessionObserver { _, _ -> reservation.release() },
                    primarySurfaceFactory = { id ->
                        // Le seam du curseur câblé sur la View de CETTE forme aussi : la
                        // propriété `View.pointerIcon` vit sur la vue, pas sur la fenêtre — la
                        // forme View committe le curseur exactement comme la forme Activity. Le
                        // seam de capture (phase 4, Task 3) de même pour les primitives — mais la
                        // forme View n'a pas de fenêtre enchaînée : le canal d'état du SDK
                        // (`Window.Callback.onPointerCaptureChanged`) n'existe pas ici, la
                        // capability suit le fait câblé (`inputWired`) et le champ y reste refusé
                        // plutôt que d'annoncer un commit qui ne peut pas arriver.
                        val surface = AndroidHostSurface(
                            id,
                            initialSurfaceState(view, initial),
                            leasedView = { view },
                            cursorNative = AndroidViewCursorNative(view),
                            pointerCaptureNative = AndroidViewPointerCaptureNative(view),
                        )
                        sessionSurface.surface = surface
                        // L'enregistrement miroir web : l'id que le runtime vient d'allouer est le
                        // seul fait contre lequel la route `Surface` du port résout — la surfaceView
                        // (en `Any` ici) est ce que la Task 3 lira par PixelCopy.
                        capturePort.registerHostCaptureSurface(surface.id, view)
                        RuntimePrimarySurface(surface, surface::close)
                    },
                    displayPort = AndroidDisplayPort(AndroidDisplayManagerSource(view.context)),
                    inputDevicePort = AndroidInputDevicePort(AndroidInputManagerSource(view.context)),
                    capturePort = capturePort,
                )
            }
        }

        // 4. Le binding — un par attach, construit à frais, installé exactement une fois.
        //    Les wrappers never-throw garantissent qu'un lancement dans la publication ne
        //    peut pas sauter le terminal (le binding appelle `onDecision` AVANT `onTerminal`).
        val lifecycleBinding = AndroidLifecycleBinding(
            reducer = AndroidLifecycleReducer(initial),
            onDecision = { decision ->
                try {
                    when (decision) {
                        is AndroidLifecycleDecision.Ignore -> Unit

                        // RUN-004 : la publication ordinaire devient l'état lifecycle du
                        // runtime (la couture web : `WebLifecycleReducer.Update` →
                        // `controller.updateLifecycle`), puis l'état de la surface.
                        is AndroidLifecycleDecision.Publish -> {
                            propagateToRuntime(decision.snapshot) { state ->
                                controller.updateLifecycle(state)
                            }
                            publish(decision.snapshot, sessionSurface)
                        }

                        is AndroidLifecycleDecision.Terminate ->
                            // Le côté lifecycle du terminal appartient à `detach()`
                            // (l'arrêt host du runtime) ; ici, seul l'instantané canonique
                            // de la surface est publié, avant l'événement terminal.
                            publish(decision.snapshot, sessionSurface)
                    }
                } catch (cause: Throwable) {
                    // Absorbé : un callback ordinaire ne lance jamais dans la boucle lifecycle
                    // du host ; l'observation perdue est un diagnostic, pas un état corrompu —
                    // la publication suivante (ou le terminal) rétablit l'état canonique.
                }
            },
            onTerminal = {
                // Teardown et libération d'abord — locaux, idempotents, un lancement éventuel
                // y est absorbé sans rien perdre d'autre.
                try {
                    binding?.teardown() // aussi couvert par la révocation — idempotent
                    reservation.release() // la terminaison effective libère l'ownership — idempotent
                } catch (cause: Throwable) {
                    // Absorbé : le terminal doit toujours traverser vers la proposition d'arrêt.
                }
                // La proposition d'arrêt de host du runtime : `Stopped(HostDetached)`.
                // Avant `controller.attach` (relecture terminale pendant l'install), elle
                // marque seulement le contrôleur détaché et l'attach échouera `Closed(Host)`.
                // Si elle est perdue (detach lançant), la perte est SIGNALÉE sur le canal de
                // reporting — jamais avalée en silence.
                proposeHostStop(detach = { controller.detach() }, reporter = AndroidAttachDiagnostics)
                // La terminaison du host libère le créneau mémoire (Task 3) : la source
                // process ne survit qu'à un host encore vivant — le DERNIER libéré ferme la
                // registration. Une fermeture de session seule ne passe JAMAIS ici (la
                // révocation démonte le binding sans terminer le host) : la source reste
                // enregistrée, miroir AppKit. Une déregistration lançante est SIGNALÉE sur
                // le canal de reporting, comme toute perte de terminal.
                try {
                    pressureClaim.hostTerminated()
                } catch (cause: Throwable) {
                    runCatching { AndroidAttachDiagnostics.report(cause) }
                }
            },
        )
        binding = lifecycleBinding
        // Le branchement d'entrée (Task 3) : le wrapper T1 (`Window.Callback` enchaîné) et,
        // derrière lui, le tracker T2 nourrissent la delivery que l'installSessionConfiguration
        // de la surface a construite pendant l'attach. La forme ACTIVITY seule a un
        // `Window.Callback` à enchaîner — la forme View n'a pas de fenêtre (l'admission ne
        // réserve un `decorRootIdentity` que pour l'Activity) : l'observation clavier/tactile
        // vit là où la couture peut enchaîner, la forme View en reste dépourvue à cette phase.
        val inputForwarder = inputForwarderFor(request.form, owner, view, sessionSurface)
        // Le retrait du wrapper ET du branchement métriques meurt avec le binding lifecycle :
        // tout chemin qui démonte celui-ci (rollback d'admission, terminal, révocation)
        // restaure le callback d'origine exact et démonte l'observation métrique — le zéro-
        // résidu tient à un seul endroit ([AndroidLifecycleBinding.onTeardown]), jamais une
        // exception dans la boucle de teardown.
        lifecycleBinding.onTeardown {
            inputForwarder?.restoreOrReport()
            observationBinding.teardown()
        }

        // 4+5. L'installation du binding puis l'attach du runtime, sous le garde post-
        //      réservation : rejet du contrôleur → rollback intégral puis `Failure` ;
        //      throwable (SDK d'installation, erreur de programmation, échec brut de
        //      `controller.attach`) → rollback intégral puis propagation TELLE QUELLE.
        return installBindingAndAttach(
            reservation = reservation,
            binding = lifecycleBinding,
            // Les deux installations SDK de la couture : le binding lifecycle PUIS le
            // branchement d'observation (listener de layout + lecture initiale). Un throwable
            // de l'une roule tout le chemin en arrière via le garde ci-dessus.
            installBinding = {
                lifecycleBinding.install(owner, view)
                observationBinding.install()
            },
            attachController = {
                val attached = controller.attach(parentScope, applicationFactory, policy, launch = null)
                if (attached is KadreResult.Success) {
                    // Le relais mémoire (Task 3) : branché sur l'attach RÉUSSI — chaque trim
                    // RUNNING mappé par la source process atteint CE host. Le runCatching
                    // absorbe la course signal/terminal (un `emitMemoryPressure` sur un host
                    // déjà détaché jette ; le signal perdu est une observation, la suivante
                    // rétablit — le même parti pris que la fenêtre post-detach du lifecycle).
                    pressureClaim.wire { level -> runCatching { controller.emitMemoryPressure(level) } }
                    // L'observation et le scheduling vivent (le listener est posé, la clock de
                    // frame est le Choreographer par défaut de la surface) : les capabilities
                    // deviennent honnêtes. La surface existe — la factory de composants a
                    // tourné pendant l'attach ; la double garde de `observationInstalled`
                    // absorbe une installation qui suivrait un terminal déjà passé.
                    //
                    // L'installation d'entrée (Task 3) courra DERRIÈRE, avec le FAIT du
                    // câblage : le wrapper est posé D'ABORD (toute observation qui glisserait
                    // entre install et install-métrique attend dans le buffer de la delivery —
                    // son unique raison d'être), et `observationInstalled` porte la présence
                    // du wrapper — PAS la forme déclarée. Un attach sans wrapper (forme View,
                    // ou Activity sans callback fenêtre) installe l'observation SANS
                    // observateur : le stimulus structurel « rien n'est câblé » est absorbé
                    // par le réducteur, les caps d'entrée restent Unsupported — jamais la
                    // promesse d'un clavier qui ne peut pas arriver.
                    inputForwarder?.install()
                    sessionSurface.surface?.observationInstalled(inputWired = inputForwarder != null)
                }
                attached
            },
            // Le rollback libère le créneau mémoire pris avant le contrôleur (Task 3) :
            // un attach qui échoue ne laisse la source process installée pour personne.
            // L'absorption est SYMÉTRIQUE au chemin terminal (onTerminal, ci-dessus) : une
            // déregistration lançante ne remplace JAMAIS l'issue de l'attach — le rejet du
            // contrôleur reste un `KadreResult.Failure` retourné, le throwable du chemin
            // catch reste CELUI qui est documenté — elle est SIGNALÉE sur le canal de
            // reporting, comme toute perte de terminal. Le MÊME lambda couvre les DEUX
            // chemins de rollback de `installBindingAndAttach`.
            onRollback = {
                try {
                    pressureClaim.hostTerminated()
                } catch (cause: Throwable) {
                    runCatching { AndroidAttachDiagnostics.report(cause) }
                }
            },
        )
    }

    /**
     * La couture d'attach de la forme PRÉSENTATION (`attachKadreSecondaryDisplay`, phase 11) :
     * l'échelle, la réservation (contentView identité + displayId valeur, JAMAIS l'Activity),
     * puis la composition du seam Task 1 avec le runtime — la structure de la branche Activity
     * SANS seam natif fenêtre.
     *
     * Le chemin nominal :
     * 1. l'échelle ([evaluateAndroidPresentationAttach], pure) — violation de thread :
     *    `IllegalStateException` (le rung existant) ; toute autre denial : `Failure` sans
     *    exception ;
     * 2. la réservation atomique contentView+displayId
     *    ([AndroidPresentationReservation.acquire]) — refus : `Failure(AlreadyInUse(Host))`,
     *    l'état d'avant la demande intact. La session principale de la même Activity reste
     *    libre : l'identité Activity n'est jamais revendiquée par cette forme ;
     * 3. la composition : le seam [AndroidPresentationSource] (fabrique injectée — la
     *    production y passe `AndroidSdkPresentationSource`, les tests hôtes un fake pilotable),
     *    la surface [AndroidHostSurface] construite sur les FAITS du média (attachée — le
     *    média existe dès la construction — mais CACHÉE jusqu'au premier START), le manager
     *    `AndroidActivityWindowManager(native = null)` (la table honnête : la session
     *    présentation ne revendique AUCUN champ fenêtre en v1), et `withComponents` — le
     *    teardown de la surface primaire ferme le seam (`presentationSource.close()`), le
     *    `closeAction` terminalise le flux d'événements du manager (l'exact miroir de la
     *    branche Activity) ;
     * 4. le pilotage lifecycle ([AndroidPresentationLifecycle], installé par l'étape injectée
     *    — la production pose le binding SDK sur l'Activity et son decorView) : START montre,
     *    STOP cache — le premier show est LEVEL-TRIGGERED (un hôte déjà STARTED à l'attach
     *    montre le média immédiatement, ruling de la phase) ; le retrait d'écran (état
     *    `Removed` du seam, observé en continu) et un show échoué ferment la surface puis
     *    terminent la session — le MÊME chemin que la disparition de l'hôte dans les formes
     *    existantes (`controller.detach()` → `Stopped(HostDetached)`), réservations libérées,
     *    jamais de résurrection. L'INPUT de la fenêtre du média (Task 3) : la chaîne
     *    `Window.Callback` est posée au PREMIER `Shown` du seam — une seule fois pour la vie
     *    du média, via l'étape injectée `inputChainFactory` (le jeton opaque + le câblage de
     *    LA session) — et restituée à la callback d'origine exacte par le teardown du
     *    binding, chaque chemin de démonte compris ;
     * 5. l'installation puis l'attach sous le garde post-réservation (l'overload présentation
     *    de [installBindingAndAttach]) : rollback intégral — `presentationSource.close()` +
     *    réservation rendue, throwable SDK propagé TEL QUEL.
     *
     * Les paramètres injectés (`sourceFactory`, `installHostLifecycle`, `ownership`,
     * `displayPortFactory`, `inputChainFactory`) sont les seuls qui touchent le SDK en
     * production : les tests hôtes conduisent toute la machinerie sans android.jar — le
     * modèle de `installBindingAndAttach`, dont l'étape d'installation reste LA seule couture
     * SDK. `inputChainFactory` (Task 3) construit la chaîne d'entrée du JETON de fenêtre
     * opaque avec le câblage de LA session présentation — la production y passe la fenêtre
     * réelle du média ([windowInputChainFor]), les tests hôtes une fake sans type SDK.
     */
    internal fun attachSecondaryDisplay(
        request: AndroidPresentationAttachRequest,
        parentScope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy,
        sourceFactory: () -> AndroidPresentationSource,
        installHostLifecycle: (AndroidPresentationLifecycle) -> Unit,
        registry: AndroidViewOwnership = ownership,
        displayPortFactory: () -> DisplayPort? = { null },
        inputChainFactory: (windowToken: Any?, wiring: AndroidSessionInputWiring) -> AndroidWindowInputChain? =
            { _, _ -> null },
    ): KadreResult<KadreSession> {
        // 1. L'échelle normative présentation : thread → scope → contentView LIBRE → écran →
        //    owner détruit → conflit contentView/displayId.
        val denial = evaluateAndroidPresentationAttach(request, registry)
        if (denial != null) return KadreResult.Failure(toFailure(denial))

        // 2. La réservation atomique contentView (identité) ET displayId (valeur) — refus :
        //    Busy(Host), état intact. L'Activity n'est JAMAIS revendiquée : la coexistence
        //    avec la session principale de la même Activity tient ici.
        val reservation = AndroidPresentationReservation.acquire(registry, request.contentView, request.displayKey)
            ?: return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))

        // 3. La composition. Le média naît ici : le seam enregistre son listener SDK
        //    (production) AVANT toute session — chaque chemin d'échec en dessous le ferme.
        //    Une fabrique lançante ne fuit JAMAIS la réservation : rollback puis propagation
        //    TELLE QUELLE — le contrat du garde post-réservation, étendu à sa première étape.
        val presentationSource = try {
            sourceFactory()
        } catch (cause: Throwable) {
            reservation.rollback()
            throw cause
        }
        val surface = AndroidHostSurface(
            RuntimeProcessIds.nextSurfaceId(),
            presentationSurfaceState(presentationSource.facts),
            leasedView = { request.contentView },
        )

        // Le binding n'existe pas encore : la révocation passe par la variable locale,
        // assignée avant tout chemin qui peut la lire (le modèle de la branche Activity).
        var installedBinding: AndroidLifecycleBinding? = null
        val controller = RuntimeHostController.withComponents(
            platform = KadrePlatform.Android,
            sessionRevocationHandler = RuntimeSessionRevocationHandler { _ ->
                installedBinding?.teardown()
            },
            componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                // Le manager SANS seam natif : la fenêtre d'une présentation n'expose aucune
                // des primitives de la fenêtre Activity — la table `unsupportedWindowCapabilities()`
                // est un fait de construction, pas un défaut d'installation.
                val manager = AndroidActivityWindowManager(primarySurface = surface, native = null)
                RuntimeSessionComponents(
                    windows = manager,
                    // Le teardown de la surface primaire (la machinerie runtime l'invoque à la
                    // terminaison de session, APRÈS la fermeture du flux d'événements côté
                    // surface) : la surface d'abord, le seam ensuite (son dismiss est
                    // idempotent — un retrait d'écran l'a peut-être déjà fait), le manager en
                    // closeAction — l'ordre exact de la branche Activity.
                    primarySurface = RuntimePrimarySurface(surface) {
                        surface.close()
                        presentationSource.close()
                    },
                    displayPort = displayPortFactory(),
                    closeAction = { manager.terminalise() },
                )
            },
            // La session présentation VIT : son média seulement naît caché. L'état lifecycle
            // initial dit la vérité de l'Activity hôte (le runtime termine l'host sur un
            // attachment Détaché — la « surface cachée » ne doit JAMAIS fuir ici).
            initialLifecycleState = LifecycleState(
                attachment = AttachmentState.Attached,
                visibility = if (request.ownerState >= LIFECYCLE_STARTED) {
                    VisibilityState.Foreground
                } else {
                    VisibilityState.Background
                },
                activation = if (request.ownerState >= LIFECYCLE_RESUMED) {
                    ActivationState.Active
                } else {
                    ActivationState.Inactive
                },
            ),
            failureReporter = AndroidAttachDiagnostics,
            sessionObserver = RuntimeSessionObserver { _, _ -> reservation.release() },
        )

        // 4. Le terminal partagé : retrait d'écran (Removed), show échoué, disparition du
        //    host — un seul chemin, idempotent, la MÊME proposition d'arrêt que les formes
        //    existantes (controller.detach() → Stopped(HostDetached)) ; la fermeture de la
        //    surface (closeSurface) n'accompagne que les morts du MÉDIA (« retrait display
        //    ferme la surface et termine la session ») — la disparition du host laisse la
        //    machinerie runtime faire son teardown ordonné.
        var terminalPassed = false
        fun sessionTerminal(closeSurface: Boolean) {
            if (terminalPassed) return
            terminalPassed = true
            try {
                if (closeSurface) surface.close()
                installedBinding?.teardown() // aussi couvert par la révocation — idempotent
                reservation.release() // la terminaison effective libère l'ownership — idempotent
            } catch (cause: Throwable) {
                // Absorbé : le terminal doit toujours traverser vers la proposition d'arrêt.
            }
            proposeHostStop(detach = { controller.detach() }, reporter = AndroidAttachDiagnostics)
        }

        val presentationLifecycle = AndroidPresentationLifecycle(
            initial = AndroidHostSnapshot(
                attachment = true,
                foreground = request.ownerState >= LIFECYCLE_STARTED,
                active = request.ownerState >= LIFECYCLE_RESUMED,
            ),
            showMedia = {
                val shown = presentationSource.show()
                if (shown is KadreResult.Success) {
                    surface.publishMetrics(update = { current ->
                        current.copy(visibility = SurfaceVisibility.Visible)
                    })
                }
                shown
            },
            hideMedia = {
                presentationSource.hide()
                surface.publishMetrics(update = { current ->
                    current.copy(visibility = SurfaceVisibility.Hidden)
                })
            },
            onMediaGone = { sessionTerminal(closeSurface = true) },
            onHostGone = { sessionTerminal(closeSurface = false) },
        )
        installedBinding = presentationLifecycle.binding
        // L'entrée de LA session présentation (Task 3) : le câblage SDK-free que l'étape
        // injectée consomme — les funnels de CETTE surface (la delivery existe dès l'attach
        // réussi, la chaîne n'est posée qu'après lui), jamais ceux d'une autre session.
        val inputWiring = AndroidSessionInputWiring(
            onObservation = { observation -> surface.onInputObservation(observation) },
            shouldConsumeKey = { nativeKeyCode -> surface.shouldConsumeKey(nativeKeyCode) },
            onMotion = { observation, viewWidth, viewHeight, offsetX, offsetY ->
                surface.onMotionObservation(observation, viewWidth, viewHeight, offsetX, offsetY)
            },
        )
        // La chaîne d'entrée du média : posée au PREMIER `Shown`, UNE fois pour la vie du
        // média — la fenêtre d'une Presentation survit à hide/show, la reposer à chaque show
        // doublerait les événements. La garde (`inputChainHandled`) vit sur le chemin du
        // collecteur ci-dessous, qui en production livre chaque état SOUS le RuntimeLock du
        // seam — l'idempotence est gardée par le verrou du média, comme la capture du jeton
        // dans la machine Task 1.
        var inputChain: AndroidWindowInputChain? = null
        var inputChainHandled = false
        fun installInputChainOnce() {
            if (inputChainHandled) return
            inputChainHandled = true
            inputChain = inputChainFactory(presentationSource.windowToken(), inputWiring)
            inputChain?.install()
            // Le fait câblé suit la chaîne RÉELLEMENT posée (une fenêtre sans callback ne
            // promet pas de clavier) — le miroir exact de la discipline de la forme Activity.
            surface.observationInstalled(inputWired = inputChain != null)
        }

        // L'observation de l'état du média : le retrait d'écran (`Removed`, FINAL — le
        // terminal) et le premier `Shown` (l'installation d'entrée UNE fois). Le collecteur
        // Unconfined livre le fait au thread qui l'émet (le listener SDK du seam comme le fake
        // du test hôte), la mort du média ne dépend d'aucun dispatcher. INVARIANT de
        // réentrance : en production, l'émission courut SOUS le RuntimeLock du seam, et le
        // terminal re-rentre dans ce même verrou via `presentationSource.close()` (le
        // teardown des composants) — sûr parce que le RuntimeLock JVM est `synchronized`,
        // donc réentrant ; le fake du test hôte, lui, n'a pas de verrou.
        val mediaWatch = parentScope.launch(Dispatchers.Unconfined) {
            presentationSource.state.collect { state ->
                when (state) {
                    AndroidPresentationState.Hidden -> Unit
                    is AndroidPresentationState.Shown -> installInputChainOnce()
                    is AndroidPresentationState.Removed -> sessionTerminal(closeSurface = true)
                }
            }
        }
        // Le retrait du binding emporte chaîne et observation : chaque chemin qui démonte
        // l'un (terminal, révocation, rollback) restitue la callback d'origine EXACTE puis
        // arrête l'autre — jamais de collecteur orphelin, jamais de chaîne résiduelle.
        presentationLifecycle.binding.onTeardown {
            inputChain?.restoreOrReport()
            mediaWatch.cancel()
        }

        // 4+5. L'installation puis l'attach, sous le garde post-réservation (l'overload
        //      présentation de [installBindingAndAttach]) : rollback intégral — la
        //      présentation fermée, la réservation rendue, le throwable propagé TEL QUEL.
        return installBindingAndAttach(
            reservation = reservation,
            binding = presentationLifecycle.binding,
            installBinding = { installHostLifecycle(presentationLifecycle) },
            attachController = {
                val attached = controller.attach(parentScope, applicationFactory, policy, launch = null)
                if (attached is KadreResult.Success) {
                    // Le premier show LEVEL-TRIGGERED (ruling de la phase) : attacher depuis
                    // un hôte déjà STARTED montre le média immédiatement — le cas nominal du
                    // Tour — sinon le premier START le montre (les cycles restent edge-
                    // triggered). Un échec ici emprunte le chemin du retrait d'écran.
                    presentationLifecycle.showIfHostStarted()
                }
                attached
            },
            onRollback = { presentationSource.close() },
        )
    }

    /**
     * L'overload présentation du garde post-réservation — le contrat exact de
     * [installBindingAndAttach] pour la réservation à deux clés (contentView + displayId) :
     * le rejet du contrôleur roule tout en arrière (teardown du binding, rollback de la
     * réservation, `onRollback` — qui ferme la présentation et son listener) puis
     * `Failure(reason)` ; un throwable SDK roule tout en arrière puis se propage TEL QUEL.
     */
    internal fun installBindingAndAttach(
        reservation: AndroidPresentationReservation,
        binding: AndroidLifecycleBinding,
        installBinding: () -> Unit,
        attachController: () -> KadreResult<KadreSession>,
        onRollback: () -> Unit = {},
    ): KadreResult<KadreSession> = installBindingAndAttach(
        binding = binding,
        installBinding = installBinding,
        attachController = attachController,
        rollback = { reservation.rollback(); onRollback() },
    )

    /**
     * Le corps commun du garde post-réservation, paramétré par le SEUL rollback (les deux
     * réservations — paires Activity/View et paires contentView/displayId — y passent leur
     * libération) : installer le binding, attacher le contrôleur, et garantir que CHAQUE
     * issue laisse l'ownership propre. Le rejet du contrôleur roule tout en arrière puis
     * `Failure(reason)` ; un throwable (SDK d'installation, erreur de programmation, échec
     * brut d'attach) roule tout en arrière puis se propage TEL QUEL.
     */
    private fun installBindingAndAttach(
        binding: AndroidLifecycleBinding,
        installBinding: () -> Unit,
        attachController: () -> KadreResult<KadreSession>,
        rollback: () -> Unit,
    ): KadreResult<KadreSession> {
        try {
            installBinding()
            return when (val attached = attachController()) {
                is KadreResult.Success -> attached

                is KadreResult.Failure -> {
                    binding.teardown()
                    rollback()
                    KadreResult.Failure(attached.reason)
                }
            }
        } catch (cause: Throwable) {
            binding.teardown()
            rollback()
            throw cause
        }
    }

    /**
     * La couture post-réservation ([AndroidHostSession.attach], étapes 4 et 5) : installer le
     * binding, attacher le contrôleur, et garantir que CHAQUE issue laisse l'ownership propre.
     *
     * - `attachController` rejette (`KadreResult.Failure`) : teardown du binding, rollback de
     *   la réservation, `Failure(reason)` retournée — le chemin nominal d'échec.
     * - un throwable se propage — SDK d'installation (`Lifecycle.addObserver`,
     *   `View.addOnAttachStateChangeListener` peuvent lancer après l'acquisition de la
     *   réservation), erreur de programmation, échec brut de `controller.attach` hors son
     *   `_KadreResult` interne — mais APRÈS teardown + rollback, et TEL QUEL : le
     *   reconvertir inventerait une failure hors taxonomie, le laisser fuir avec la
     *   réservation tenue rendrait la View in-attachable jusqu'à la mort du processus.
     *
     * `installBinding` est la seule étape injectée — c'est la seule qui touche le SDK
     * Android ; le test hôte `AndroidHostSessionResultTest.throwingInstallStepRollsBackAndRethrowsRaw`
     * la remplace par une étape lançante pour épingler le rollback sans construire de View
     * réelle. `onRollback` (Task 3) accompagne le rollback intégral sur ses deux chemins —
     * le rejet du contrôleur et le throwable : il libère ce que l'attach a pu prendre avant
     * l'échec (le créneau mémoire process). CONTRAT : `onRollback` absorbe ses propres
     * échecs (l'appel de la couture le passe enveloppé, symétrique au chemin terminal) —
     * un lancement de rollback ne doit ni remplacer le `Failure` retourné ni masquer le
     * throwable documenté du chemin catch.
     */
    internal fun installBindingAndAttach(
        reservation: AndroidAttachReservation,
        binding: AndroidLifecycleBinding,
        installBinding: () -> Unit,
        attachController: () -> KadreResult<KadreSession>,
        onRollback: () -> Unit = {},
    ): KadreResult<KadreSession> = installBindingAndAttach(
        binding = binding,
        installBinding = installBinding,
        attachController = attachController,
        rollback = { reservation.rollback(); onRollback() },
    )

    /** La surface de la session, créée par la factory de composants au moment de l'attach. */
    private class SessionSurface {
        var surface: AndroidHostSurface? = null

        /**
         * Le seam de capture (Task 3) retenu pour le canal d'état du SDK : la notification
         * `Window.Callback.onPointerCaptureChanged`, enchaînée par le wrapper T1 (forme
         * Activity seule), est livrée ICI — [AndroidViewPointerCaptureNative.
         * dispatchCaptureState] la route dans le listener posé par le seam. `null` sur la
         * forme View : pas de fenêtre enchaînée, pas de canal d'état — la capability suit le
         * fait câblé (`inputWired`) et le champ y reste refusé.
         */
        var capture: AndroidViewPointerCaptureNative? = null
    }

    /**
     * Le câblage d'entrée de LA session de cette attach (phase 11, Task 3) — les funnels de
     * sa surface, qui n'existe pas avant l'attach mais n'est lue qu'après (le wrapper n'est
     * POSÉ qu'après un attach réussi : la course est structurellement impossible, le thread
     * principal sérialise l'attach tout entier), la lecture de suppression depuis l'état
     * COMMIS, et le canal d'état de la capture (le seam retenu par la session). SDK-free par
     * construction : c'est ce câblage que l'installateur fenêtre-paramétré
     * ([windowInputChainFor]) consomme — la même forme pour les deux formes fenêtrées.
     */
    private fun sessionInputWiring(sessionSurface: SessionSurface) = AndroidSessionInputWiring(
        onObservation = { observation -> sessionSurface.surface?.onInputObservation(observation) },
        shouldConsumeKey = { nativeKeyCode -> sessionSurface.surface?.shouldConsumeKey(nativeKeyCode) ?: false },
        onMotion = { observation, viewWidth, viewHeight, offsetX, offsetY ->
            sessionSurface.surface?.onMotionObservation(observation, viewWidth, viewHeight, offsetX, offsetY)
        },
        // Le canal d'état de la capture (Task 3) : la notification du SDK livrée au
        // seam retenu par la session — le listener posé par
        // [AndroidPointerCapture] reçoit la confirmation (le commit en deux temps).
        onPointerCaptureState = { hasCapture ->
            sessionSurface.capture?.dispatchCaptureState(hasCapture)
        },
    )

    /**
     * La chaîne de la forme ACTIVITY : le constructeur partagé [windowInputChainFor] sur la
     * fenêtre de l'Activity, nourri du câblage de LA session de cette attach. La forme View
     * n'a pas de fenêtre : `null`, l'observation clavier/tactile y est dépourvue à cette
     * phase (documenté au point d'appel).
     */
    private fun inputForwarderFor(
        form: AndroidAttachForm,
        owner: LifecycleOwner,
        view: View,
        sessionSurface: SessionSurface,
    ): AndroidWindowInputChain? {
        if (form != AndroidAttachForm.Activity) return null
        return windowInputChainFor((owner as ComponentActivity).window, view, sessionInputWiring(sessionSurface))
    }

    /**
     * RUN-004 : l'instantané d'une publication ordinaire du réducteur devient l'état
     * lifecycle du runtime — la même publication que la couture web
     * (`WebLifecycleReducer.Update` → `controller.updateLifecycle`). Le mapping est celui du
     * runtime : Foreground signifie au moins STARTED, Active exige RESUMED
     * ([lifecycleStateOf]).
     *
     * La fenêtre post-detach est absorbée : `RuntimeHostController.updateLifecycle` exige un
     * host non détaché et le terminal peut l'avoir déjà emporté (le verrou du réducteur
     * l'absorbe aussi côté publications) — une publication tardive ne doit ni lancer ni
     * ressusciter l'host. Le terminal n'emprunte PAS ce chemin : son côté lifecycle est la
     * proposition d'arrêt `detach()` ([proposeHostStop]), comme la couture web.
     */
    internal fun propagateToRuntime(snapshot: AndroidHostSnapshot, update: (LifecycleState) -> Unit) {
        try {
            update(lifecycleStateOf(snapshot))
        } catch (cause: IllegalArgumentException) {
            // Le require(!detached) du runtime — `updateLifecycle` jette
            // IllegalArgumentException, pas IllegalStateException : le terminal a déjà gagné,
            // absorbé, la session est déjà en route vers `Stopped(HostDetached)`. Aucune
            // autre exception n'est absorbée ici : un échec inattendu du chemin de mise à
            // jour reste un diagnostic (il atteint le wrapper never-throw de §3.3, sans
            // jamais masquer la course post-detach sous le mauvais type).
        }
    }

    /**
     * La proposition d'arrêt terminal au runtime (`Stopped(HostDetached)`). Si `detach`
     * lance, la perte est SIGNALÉE sur le canal de reporting du runtime — jamais avalée en
     * silence : la réservation est déjà libérée à ce stade, le diagnostic reste la seule
     * trace d'une session qui ne terminerait pas toute seule. Un reporter qui lance à son
     * tour est absorbé (la convention du runtime : `runCatching { failureReporter.report }`)
     * — aucun chemin ne remonte dans la boucle lifecycle du host.
     */
    internal fun proposeHostStop(detach: () -> Unit, reporter: RuntimeFailureReporter) {
        try {
            detach()
        } catch (cause: Throwable) {
            runCatching { reporter.report(cause) }
        }
    }

    /**
     * Publie un instantané du réducteur dans l'état de la surface — le mécanisme d'instantané
     * de la phase 0, tenu courant. Minimal et honnête : attachment/visibility/focus seulement,
     * les Unknown restent Unknown, aucun input ni IME. La surface peut ne pas exister encore
     * (publication plus précoce que l'attach) : l'instantané est alors simplement perdu, le
     * suivant rétablit l'état — et `AndroidHostSurface.publishMetrics` absorbe tout ce qui suit
     * la fermeture.
     */
    private fun publish(snapshot: AndroidHostSnapshot, sessionSurface: SessionSurface) {
        val surface = sessionSurface.surface ?: return
        // Publication muette (pas d'eventFor) : les publications lifecycle sont des observations
        // d'état du binding, pas des stimuli — Task 2 n'émet des événements typés que pour les
        // observations métriques du réducteur.
        surface.publishMetrics(update = { current ->
            current.copy(
                attachment = if (snapshot.attachment) {
                    SurfaceAttachmentState.Attached
                } else {
                    SurfaceAttachmentState.Detached
                },
                visibility = if (snapshot.foreground) SurfaceVisibility.Visible else SurfaceVisibility.Hidden,
                focus = if (snapshot.active) SurfaceFocus.Focused else SurfaceFocus.Unfocused,
            )
        })
    }

    /** L'état lifecycle du runtime pour un instantané — initial ET publications (RUN-004). */
    private fun lifecycleStateOf(snapshot: AndroidHostSnapshot): LifecycleState = LifecycleState(
        attachment = if (snapshot.attachment) AttachmentState.Attached else AttachmentState.Detached,
        visibility = if (snapshot.foreground) VisibilityState.Foreground else VisibilityState.Background,
        activation = if (snapshot.active) ActivationState.Active else ActivationState.Inactive,
    )

    /**
     * Le seam mémoire de CETTE attach (Task 3) : l'Application est atteignable de la forme
     * Activity (`activity.application`) et, pour la forme View, du contexte de la vue
     * (`view.context.applicationContext as? Application` — l'Application est le SEUL
     * récepteur public de `registerComponentCallbacks`, `Context.registerComponentCallbacks`
     * n'est pas public). Un cast manqué reste honnête : `null`, l'attach ne tente RIEN et la
     * capability memoryPressure y reste le défaut runtime — jamais une `PlatformFailure`
     * inventée ; le prochain attach qui atteint l'Application installe.
     */
    private fun memoryPressureSeamFor(
        form: AndroidAttachForm,
        owner: LifecycleOwner,
        view: View,
    ): AndroidMemoryPressureSeam? = when (form) {
        AndroidAttachForm.Activity -> AndroidApplicationMemoryPressureSeam((owner as ComponentActivity).application)
        AndroidAttachForm.View -> (view.context.applicationContext as? Application)
            ?.let(::AndroidApplicationMemoryPressureSeam)
    }

    /**
     * L'état initial de la surface présentation, tiré des FAITS du média (Task 1) — pas de la
     * View : le contentView est posé dans la présentation dès la construction, la surface
     * naît donc ATTACHÉE (le média existe) mais CACHÉE (`visibility = false`, pas encore
     * montrée — le premier START la rend Visible), à la géométrie logique de l'écran cible
     * (`realWidth/scaleFactor × realHeight/scaleFactor`). Les insets d'une fenêtre de
     * présentation dédiée sont nuls en v1 ; l'échelle invalide retombe sur 1 (le miroir de la
     * garde densité de [initialSurfaceState]).
     */
    private fun presentationSurfaceState(facts: AndroidPresentationFacts): SurfaceState {
        val scale = if (facts.scaleFactor.isFinite() && facts.scaleFactor > 0.0) facts.scaleFactor else 1.0
        val physical = PhysicalSize(maxOf(1, facts.realWidth), maxOf(1, facts.realHeight))
        return SurfaceState(
            attachment = SurfaceAttachmentState.Attached,
            logicalSize = LogicalSize(physical.width / scale, physical.height / scale),
            physicalSize = physical,
            scaleFactor = scale,
            safeAreaInsets = LogicalInsets(top = 0.0, right = 0.0, bottom = 0.0, left = 0.0),
            visibility = SurfaceVisibility.Hidden,
            occlusion = SurfaceOcclusion.Visible,
            focus = SurfaceFocus.Unfocused,
            appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
            cursor = CursorStyle.System(CursorIcon.Default),
            pointerCapture = PointerCaptureMode.None,
            hitTesting = HitTestingMode.Enabled,
            inputDefaultBehavior = InputDefaultBehavior.HostDefault,
            revision = SurfaceRevision(0L),
        )
    }

    /**
     * L'instantané initial de la surface, copié de la View comme le port web copie le sien
     * (`WebHostPort.initialSnapshot`), borné au minimum valide (base 1×1 — une View attachée
     * mais pas encore mesurée rapporte des dimensions nulles et le modèle de surface exige des
     * tailles strictement positives). Les champs observés (attachment/visibility/focus)
     * viennent du même instantané que le réducteur ; les insets viennent de `rootWindowInsets`
     * — le même instantané partagé par la surface ET le réducteur, de sorte que la lecture
     * initiale du binding (installé avant que la surface n'existe) retombe sur une observation
     * identique : aucune divergence, aucune publication fantôme.
     */
    @Suppress("DEPRECATION")
    private fun initialSurfaceState(view: View, snapshot: AndroidHostSnapshot): SurfaceState {
        val density = view.resources.displayMetrics.density.toDouble()
        val scaleFactor = if (density.isFinite() && density > 0.0) density else 1.0
        val physical = PhysicalSize(maxOf(1, view.width), maxOf(1, view.height))
        val insets = view.rootWindowInsets
        return SurfaceState(
            attachment = if (snapshot.attachment) {
                SurfaceAttachmentState.Attached
            } else {
                SurfaceAttachmentState.Detached
            },
            logicalSize = LogicalSize(physical.width / scaleFactor, physical.height / scaleFactor),
            physicalSize = physical,
            scaleFactor = scaleFactor,
            safeAreaInsets = LogicalInsets(
                top = (insets?.systemWindowInsetTop ?: 0) / scaleFactor,
                right = (insets?.systemWindowInsetRight ?: 0) / scaleFactor,
                bottom = (insets?.systemWindowInsetBottom ?: 0) / scaleFactor,
                left = (insets?.systemWindowInsetLeft ?: 0) / scaleFactor,
            ),
            visibility = if (snapshot.foreground) SurfaceVisibility.Visible else SurfaceVisibility.Hidden,
            occlusion = SurfaceOcclusion.Visible,
            focus = if (snapshot.active) SurfaceFocus.Focused else SurfaceFocus.Unfocused,
            appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
            cursor = CursorStyle.System(CursorIcon.Default),
            pointerCapture = PointerCaptureMode.None,
            hitTesting = HitTestingMode.Enabled,
            inputDefaultBehavior = InputDefaultBehavior.HostDefault,
            revision = SurfaceRevision(0L),
        )
    }
}

/**
 * Le pilotage du média présentation par le lifecycle du host (phase 11, Task 2) : chaque
 * décision du réducteur franchit la transition foreground — false→true : [showMedia] (le
 * START montre le média), true→false : [hideMedia] (le STOP le cache ; le média SURVIT aux
 * cycles, jamais deux présentations) — et le terminal du host (Activity détruite, fenêtre
 * disparue) emprunte [onHostGone]. Un show qui échoue (l'écran est parti entre-temps) et le
 * retrait d'écran observé en aval empruntent le MÊME chemin ([onMediaGone]).
 *
 * Le RÉDUCTEUR est partagé entre le binding SDK — [binding], que la production installe via
 * [install] sur (l'Activity, son decorView) : le contentView de la présentation est LIBRE à
 * l'attach, l'observer serait un terminal immédiat — et l'entrée [dispatch] des tests hôtes
 * (le même réducteur, les mêmes décisions, sans SDK : deux traducteurs, une seule vérité).
 * La transition foreground est gardée CÔTÉ pilote en plus de la déduplication du réducteur :
 * un RESUMED qui suit un STARTED republie foreground=true sans être un START — le média ne
 * se montre qu'aux transitions, une fois par cycle.
 *
 * Le premier show est LEVEL-TRIGGERED ([showIfHostStarted], ruling de la phase) : attacher
 * depuis un hôte déjà ≥ STARTED — le cas nominal, une Activity RESUMED — montre le média
 * immédiatement ; sinon le premier START le montre. Les cycles START/STOP suivants restent
 * edge-triggerés, un show et un hide par cycle.
 */
internal class AndroidPresentationLifecycle(
    initial: AndroidHostSnapshot,
    private val showMedia: () -> KadreResult<Unit>,
    private val hideMedia: () -> Unit,
    private val onMediaGone: () -> Unit,
    private val onHostGone: () -> Unit,
) {
    private val reducer = AndroidLifecycleReducer(initial)
    private var foreground = initial.foreground

    /** Le binding SDK de production — le même réducteur, les mêmes décisions, le même terminal. */
    val binding = AndroidLifecycleBinding(reducer, ::onDecision, ::onTerminal)

    /** L'installation de production : observer lifecycle + listener d'attachement du decorView. */
    fun install(owner: LifecycleOwner, view: View) {
        binding.install(owner, view)
    }

    /**
     * Le premier show level-triggered — à appeler UNE fois, après l'attach RÉUSSI de la
     * session : un hôte déjà ≥ STARTED montre le média immédiatement. Un échec emprunte le
     * chemin du retrait d'écran ([onMediaGone]), comme tout show.
     */
    fun showIfHostStarted() {
        if (foreground) showNow()
    }

    /** L'entrée de test hôte — le réducteur du binding, conduit sans toucher le SDK. */
    fun dispatch(event: AndroidHostEvent) {
        when (val decision = reducer.onEvent(event)) {
            is AndroidLifecycleDecision.Ignore -> Unit

            is AndroidLifecycleDecision.Publish -> onDecision(decision)

            is AndroidLifecycleDecision.Terminate -> {
                // Le même ordre que le binding : la décision AVANT l'événement terminal.
                onDecision(decision)
                onTerminal()
            }
        }
    }

    private fun onDecision(decision: AndroidLifecycleDecision) {
        when (decision) {
            is AndroidLifecycleDecision.Ignore -> Unit

            is AndroidLifecycleDecision.Publish -> {
                val target = decision.snapshot.foreground
                if (target == foreground) return
                foreground = target
                if (target) showNow() else hideMedia()
            }

            is AndroidLifecycleDecision.Terminate -> Unit
        }
    }

    private fun showNow() {
        val shown = showMedia()
        if (shown is KadreResult.Failure) onMediaGone()
    }

    private fun onTerminal() = onHostGone()
}

/**
 * Le canal de diagnostic de la couture d'attach — l'[RuntimeFailureReporter] que le runtime
 * reçoit pour chaque session construite ici : les throwables que le runtime reporte
 * (application, composants) et la perte éventuelle d'une proposition d'arrêt terminal
 * ([AndroidHostSession.proposeHostStop]) y sont ENREGISTRÉS, jamais avalés en silence.
 *
 * Journal borné (les plus récents gagnent) et thread-safe : le runtime y reporte depuis les
 * contexts de coroutine des sessions, pas seulement depuis le thread principal de l'attach.
 */
internal object AndroidAttachDiagnostics : RuntimeFailureReporter {
    private val lock = Any()
    private val recorded = ArrayDeque<Throwable>()

    override fun report(cause: Throwable) {
        synchronized(lock) {
            recorded.addLast(cause)
            while (recorded.size > JOURNAL_CAPACITY) recorded.removeFirst()
        }
    }

    /** L'instantané du journal — pour l'observation (diagnostics, tests hôtes). */
    internal fun failures(): List<Throwable> = synchronized(lock) { recorded.toList() }

    private const val JOURNAL_CAPACITY = 16
}
