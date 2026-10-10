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
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeHostSurface
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
 *    `Success(session)` ; échec : **rollback intégral** (teardown du binding, rollback de la
 *    réservation) puis `Failure(reason)` — plus jamais d'exception sur ce chemin (le gap de
 *    sûreté d'exception de la phase 0 est comblé).
 *
 * Le terminal du binding (view détachée, owner détruit) emprunte la proposition d'arrêt de
 * host du runtime : [RuntimeHostController.detach] publie l'état détaché et propose
 * `SessionOutcome.Stopped(SessionStopReason.HostDetached)` à la session — aucun chemin
 * parallèle n'est inventé. Les deux callbacks que le binding reçoit sont des wrappers
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
        val controller = when (request.form) {
            AndroidAttachForm.Activity -> RuntimeHostController.withComponents(
                platform = KadrePlatform.Android,
                sessionRevocationHandler = RuntimeSessionRevocationHandler { _ -> binding?.teardown() },
                componentsFactory = RuntimeSessionComponentsFactory { _, _ ->
                    // UNE instance de surface par session, passée au manager de fenêtres ET à
                    // la surface primaire : l'invariant de topologie
                    // `windows.state.value.primary?.surface === primarySurface` est un fait de
                    // session, pas une coïncidence de construction.
                    val surface = RuntimeHostSurface(
                        RuntimeProcessIds.nextSurfaceId(),
                        initialSurfaceState(view, initial),
                    )
                    sessionSurface.surface = surface
                    RuntimeSessionComponents(
                        windows = AndroidActivityWindowManager(surface),
                        primarySurface = RuntimePrimarySurface(surface, surface::close),
                    )
                },
                initialLifecycleState = initialLifecycleState(initial),
                sessionObserver = RuntimeSessionObserver { _, _ -> reservation.release() },
            )

            AndroidAttachForm.View -> RuntimeHostController.withPrimarySurface(
                platform = KadrePlatform.Android,
                sessionRevocationHandler = RuntimeSessionRevocationHandler { _ -> binding?.teardown() },
                initialLifecycleState = initialLifecycleState(initial),
                sessionObserver = RuntimeSessionObserver { _, _ -> reservation.release() },
                primarySurfaceFactory = { id ->
                    val surface = RuntimeHostSurface(id, initialSurfaceState(view, initial))
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
                        is AndroidLifecycleDecision.Publish -> publish(decision.snapshot, sessionSurface)
                        is AndroidLifecycleDecision.Terminate -> publish(decision.snapshot, sessionSurface)
                    }
                } catch (cause: Throwable) {
                    // Absorbé : un callback ordinaire ne lance jamais dans la boucle lifecycle
                    // du host ; l'observation perdue est un diagnostic, pas un état corrompu —
                    // la publication suivante (ou le terminal) rétablit l'état canonique.
                }
            },
            onTerminal = {
                try {
                    binding?.teardown() // aussi couvert par la révocation — idempotent
                    reservation.release() // la terminaison effective libère l'ownership — idempotent
                    // La proposition d'arrêt de host du runtime : `Stopped(HostDetached)`.
                    // Avant `controller.attach` (relecture terminale pendant l'install), elle
                    // marque seulement le contrôleur détaché et l'attach échouera `Closed(Host)`.
                    controller.detach()
                } catch (cause: Throwable) {
                    // Absorbé : le terminal doit toujours traverser — teardown, libération et
                    // proposition d'arrêt sont déjà individuellement idempotents et protégés.
                }
            },
        )
        binding = lifecycleBinding
        lifecycleBinding.install(owner, view)

        // 5. L'attach du runtime — succès : la session vit ; échec : rollback intégral puis
        //    `Failure`, jamais d'exception.
        return when (val attached = controller.attach(parentScope, applicationFactory, policy, launch = null)) {
            is KadreResult.Success -> KadreResult.Success(attached.value)

            is KadreResult.Failure -> {
                lifecycleBinding.teardown()
                reservation.rollback()
                KadreResult.Failure(attached.reason)
            }
        }
    }

    /** La surface de la session, créée par la factory de composants au moment de l'attach. */
    private class SessionSurface {
        var surface: RuntimeHostSurface? = null
    }

    /**
     * Publie un instantané du réducteur dans l'état de la surface — le mécanisme d'instantané
     * de la phase 0, tenu courant. Minimal et honnête : attachment/visibility/focus seulement,
     * les Unknown restent Unknown, aucun input ni IME. La surface peut ne pas exister encore
     * (publication plus précoce que l'attach) : l'instantané est alors simplement perdu, le
     * suivant rétablit l'état — et `RuntimeHostSurface.publish` absorbe tout ce qui suit la
     * fermeture.
     */
    private fun publish(snapshot: AndroidHostSnapshot, sessionSurface: SessionSurface) {
        val surface = sessionSurface.surface ?: return
        surface.publish { current ->
            current.copy(
                attachment = if (snapshot.attachment) {
                    SurfaceAttachmentState.Attached
                } else {
                    SurfaceAttachmentState.Detached
                },
                visibility = if (snapshot.foreground) SurfaceVisibility.Visible else SurfaceVisibility.Hidden,
                focus = if (snapshot.active) SurfaceFocus.Focused else SurfaceFocus.Unfocused,
            )
        }
    }

    /** L'état lifecycle initial du contrôleur, dérivé du même instantané honnête que la surface. */
    private fun initialLifecycleState(snapshot: AndroidHostSnapshot): LifecycleState = LifecycleState(
        attachment = if (snapshot.attachment) AttachmentState.Attached else AttachmentState.Detached,
        visibility = if (snapshot.foreground) VisibilityState.Foreground else VisibilityState.Background,
        activation = if (snapshot.active) ActivationState.Active else ActivationState.Inactive,
    )

    /**
     * L'instantané initial de la surface, copié de la View comme le port web copie le sien
     * (`WebHostPort.initialSnapshot`), borné au minimum valide (base 1×1 — une View attachée
     * mais pas encore mesurée rapporte des dimensions nulles et le modèle de surface exige des
     * tailles strictement positives). Les champs observés (attachment/visibility/focus)
     * viennent du même instantané que le réducteur ; l'observation réelle des métriques
     * suivra les mêmes callbacks.
     */
    private fun initialSurfaceState(view: View, snapshot: AndroidHostSnapshot): SurfaceState {
        val density = view.resources.displayMetrics.density.toDouble()
        val scaleFactor = if (density.isFinite() && density > 0.0) density else 1.0
        val physical = PhysicalSize(maxOf(1, view.width), maxOf(1, view.height))
        return SurfaceState(
            attachment = if (snapshot.attachment) {
                SurfaceAttachmentState.Attached
            } else {
                SurfaceAttachmentState.Detached
            },
            logicalSize = LogicalSize(physical.width / scaleFactor, physical.height / scaleFactor),
            physicalSize = physical,
            scaleFactor = scaleFactor,
            safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
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
