package org.graphiks.kadre.platform.android

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * Les quatre points d'entrée publics d'attach Android (DESIGN §15.1).
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

    /** Le registre d'ownership process-wide — une seule instance pour toutes les attaches. */
    private val ownership = AndroidViewOwnership()

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
                    val surface = AndroidHostSurface(
                        RuntimeProcessIds.nextSurfaceId(),
                        initialSurfaceState(view, initial),
                        leasedView = { view },
                        cursorNative = AndroidViewCursorNative(view),
                        pointerCaptureNative = captureNative,
                    )
                    sessionSurface.surface = surface
                    // Le seam natif de la fenêtre (phase 4, Task 1) câblé sur CETTE Activity —
                    // la forme Activity seule (le cast est le même que `inputForwarderFor`).
                    // Le manager implémente `RuntimeSessionWindowManager` : le runtime lui
                    // installe la configuration de session (LA MÊME source de stamps que la
                    // surface) pendant l'attach, et `closeAction` termine le flux d'événements
                    // de la fenêtre à la teardown (après la fermeture de la surface).
                    val manager = AndroidActivityWindowManager(
                        primarySurface = surface,
                        native = AndroidActivityWindowNative(owner as ComponentActivity),
                    )
                    RuntimeSessionComponents(
                        windows = manager,
                        primarySurface = RuntimePrimarySurface(surface, surface::close),
                        closeAction = { manager.terminalise() },
                    )
                },
                initialLifecycleState = lifecycleStateOf(initial),
                failureReporter = AndroidAttachDiagnostics,
                sessionObserver = RuntimeSessionObserver { _, _ -> reservation.release() },
            )

            AndroidAttachForm.View -> RuntimeHostController.withPrimarySurface(
                platform = KadrePlatform.Android,
                sessionRevocationHandler = RuntimeSessionRevocationHandler { _ -> binding?.teardown() },
                initialLifecycleState = lifecycleStateOf(initial),
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
                    RuntimePrimarySurface(surface, surface::close)
                },
            )
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
        )
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
     * réelle.
     */
    internal fun installBindingAndAttach(
        reservation: AndroidAttachReservation,
        binding: AndroidLifecycleBinding,
        installBinding: () -> Unit,
        attachController: () -> KadreResult<KadreSession>,
    ): KadreResult<KadreSession> {
        try {
            installBinding()
            return when (val attached = attachController()) {
                is KadreResult.Success -> attached

                is KadreResult.Failure -> {
                    binding.teardown()
                    reservation.rollback()
                    KadreResult.Failure(attached.reason)
                }
            }
        } catch (cause: Throwable) {
            binding.teardown()
            reservation.rollback()
            throw cause
        }
    }

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
     * Le wrapper d'entrée de CETTE attach, et l'état qui rend son retrait sûr : l'installation
     * n'a lieu qu'après un attach réussi (la delivery existe alors — construite pendant
     * l'attach par `installSessionConfiguration`), et le retrait suit l'installation pas à
     * pas — un [restore][AndroidWindowCallbackForwarder.restore] qui lance (le callback de la
     * fenêtre a été remplacé par un tiers) est REPORTÉ sur le journal de la couture, jamais
     * propagé dans la boucle de teardown.
     */
    private class InputForwarder(private val forwarder: AndroidWindowCallbackForwarder) {
        var installed: Boolean = false
            private set

        fun install() {
            forwarder.install()
            installed = true
        }

        fun restoreOrReport() {
            if (!installed) return
            installed = false
            try {
                forwarder.restore()
            } catch (cause: Throwable) {
                runCatching { AndroidAttachDiagnostics.report(cause) }
            }
        }
    }

    /**
     * Le wrapper T1 construit pour la forme ACTIVITY : enchaîné sur le callback EXISTANT de
     * la fenêtre de l'activité (la paire publique get/set — jamais de remplacement), nourri
     * des trois décisions que la couture seule peut répondre :
     *
     * - [AndroidInputWindowCallback.isTargetFocused] = `view.hasFocus()` — l'observation
     *   clavier ne vit que sur la vue attachée focalisée (le câblage documenté du wrapper) ;
     * - l'observation (touches, perte de focus) et la motion (les primitives copiées au
     *   dispatch) partent vers la delivery de la surface — qui n'existe pas avant l'attach,
     *   mais le wrapper n'est POSÉ qu'après un attach réussi : la course est structurellement
     *   impossible, le thread principal sérialise l'attach tout entier ;
     * - [AndroidInputWindowCallback.shouldConsumeKey] lit l'état COMMIS de la surface (le
     *   `suppressDefaultFor` web côté surface — depuis la tâche 4, les scroll-keys sous
     *   `SuppressWhenPossible`) — sans surface, rien n'est consommé.
     *
     * La forme View n'a pas de fenêtre : `null`, l'observation clavier/tactile y est dépourvue
     * à cette phase (documenté au point d'appel).
     */
    private fun inputForwarderFor(
        form: AndroidAttachForm,
        owner: LifecycleOwner,
        view: View,
        sessionSurface: SessionSurface,
    ): InputForwarder? {
        if (form != AndroidAttachForm.Activity) return null
        val window = (owner as ComponentActivity).window
        val wrapped = window.callback ?: return null
        return InputForwarder(
            AndroidWindowCallbackForwarder(
                wrapped = wrapped,
                getWindowCallback = { window.callback },
                setWindowCallback = { window.callback = it },
                isTargetFocused = { view.hasFocus() },
                onObservation = { observation -> sessionSurface.surface?.onInputObservation(observation) },
                shouldConsumeKey = { nativeKeyCode -> sessionSurface.surface?.shouldConsumeKey(nativeKeyCode) ?: false },
                onMotion = { observation, offsetX, offsetY ->
                    sessionSurface.surface?.onMotionObservation(observation, view.width, view.height, offsetX, offsetY)
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
                // Le canal d'état de la capture (Task 3) : la notification du SDK livrée au
                // seam retenu par la session — le listener posé par
                // [AndroidPointerCapture] reçoit la confirmation (le commit en deux temps).
                onPointerCaptureState = { hasCapture ->
                    sessionSurface.capture?.dispatchCaptureState(hasCapture)
                },
            ),
        )
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
