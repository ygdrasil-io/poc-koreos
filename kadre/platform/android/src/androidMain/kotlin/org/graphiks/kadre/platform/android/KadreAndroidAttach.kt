package org.graphiks.kadre.platform.android

import android.os.Looper
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeHostSurface
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimeSessionRevocationHandler
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility

/**
 * Point d'entrée minimal de phase 0 — remplacé en phase 1 par les quatre overloads
 * `attachKadre` de DESIGN §15.1. Ne pas documenter comme API stable.
 *
 * Contrat de thread (décision de phase 0) : appel sur le thread principal Android,
 * non suspendant, sans blocage ni relocation. Violation = rejet avant toute mutation
 * d'état (le champ fautif est nommé par [validateAttachPreconditions], ordre normatif
 * thread → attachement → session terminée → ownership).
 *
 * La taxonomie complète des failures d'admission (`InvalidRequest`,
 * `AlreadyInUse(Host)`, …) arrive en phase 1 ; ici, rejet par exception
 * `IllegalStateException` nommant le champ fautif.
 *
 * Observation du cycle de vie (API `KadreSession` réelle) : [KadreAndroidViewSession.session]
 * expose `state : StateFlow<SessionState>` — `SessionState.Starting`, `Running`, `Stopping`,
 * `Terminated(outcome : SessionOutcome)`. Une session retournée par ce point d'entrée a
 * démarré ; elle reste `Running` jusqu'au stop coopératif de `stop()`/`close()`, qui publie
 * `Stopping` puis `Terminated(SessionOutcome.Stopped(SessionStopReason.HostRequested))`
 * (`session.awaitTermination()` suspend jusqu'à l'état terminal).
 */
@OptIn(DelicateKadreApi::class)
public fun attachKadre(
    view: View,
    parentScope: CoroutineScope,
): KadreAndroidViewSession {
    val field = validateAttachPreconditions(
        isMainThread = Looper.myLooper() == Looper.getMainLooper(),
        isAttachedToWindow = view.isAttachedToWindow,
        alreadyClaimed = AndroidViewOwnership.isClaimed(view),
    )
    check(field == null) { "attachKadre precondition violated (field: $field)" }
    check(AndroidViewOwnership.claim(view)) { "view is already claimed by an active session (field: view)" }
    val state = AndroidAttachState()
    check(state.tryClaim(view)) { "view is already claimed by an active session (field: view)" }
    // La construction de session exacte est alignée sur WebHostSession.kt (runtime internals
    // via friend) : RuntimeSessionComponents + KadreLaunchInfo + RuntimeHostController, avec
    // un RuntimePrimarySurface minimal adossé à la View. Les observers lifecycle complets,
    // les inputs, les interactions et les fenêtres sont différés (phases 1-4).
    val bridge = AndroidViewBridge.install(view, state)
    val session = createViewSession(view, parentScope, bridge)
    return KadreAndroidViewSession(view, state, bridge, session)
}

/**
 * The phase-0 handle over one attached View session.
 *
 * [stop] is the single teardown: it removes the View bridges (zero-residue journal of
 * [AndroidAttachState]), closes the Kadre session — the runtime's cooperative stop, which
 * publishes `Stopping` then `Terminated(SessionOutcome.Stopped(SessionStopReason.HostRequested))`
 * on `session.state` — and frees the process-wide ownership of the View. It is idempotent, and
 * `close()` delegates to it so the handle is usable from try-with-resources.
 */
public class KadreAndroidViewSession internal constructor(
    private val view: View,
    private val state: AndroidAttachState,
    private val bridge: AndroidViewBridge,
    public val session: KadreSession,
) : AutoCloseable {
    private var stopped = false

    /** Idempotent. Ferme la session, retire les bridges, libère l'ownership de la View. */
    public fun stop() {
        if (stopped) return
        stopped = true
        bridge.teardown()
        session.close()
        AndroidViewOwnership.release(view)
    }

    override fun close(): Unit = stop()
}

/**
 * Construit la session Kadre réelle de la View — la couture de `WebHostSession.createController`
 * + `WebHostSession.attach` limitée au strict nécessaire (`platform:web`, via friend) :
 *
 * - `RuntimeHostController.withPrimarySurface` : la variante sans fenêtre — le
 *   `UnsupportedWindowManager` par défaut du runtime reste en place, aucune port
 *   display/gamepad/input-device n'est passée (les managers restent à leurs états par
 *   défaut non supportés) ;
 * - la surface primaire est le pendant minimal de `WebHostSurface` : la surface portable
 *   du runtime ([RuntimeHostSurface], sous-systèmes explicitement non supportés), adossée
 *   à la View par son instantané initial (dimensions et densité) et fermée en `Detached` ;
 * - `launch = null` : l'attachement d'hôte ordinaire, l'application observe
 *   `KadreLaunchReason.InitialHostAttachment` (`KadreLaunchInfo` non fourni) ;
 * - aucun observer lifecycle, input, interaction ou IME n'est installé sur la View :
 *   l'instantané initial est figé, l'observation réelle arrive en phase 1.
 */
private fun createViewSession(
    view: View,
    parentScope: CoroutineScope,
    bridge: AndroidViewBridge,
): KadreSession {
    val controller = RuntimeHostController.withPrimarySurface(
        platform = KadrePlatform.Android,
        // Révocation : le runtime rend les ponts de la View avant de fermer la surface —
        // le pendant exact de `ownership.releasePort()` du modèle web.
        sessionRevocationHandler = RuntimeSessionRevocationHandler { _ -> bridge.teardown() },
        // Terminaison : la session qui se termine libère l'ownership process-wide de la
        // View — le pendant de `ownership.releaseReservation()`, même sans `stop()` du
        // consommateur. Les doubles libérations sont absorbées par l'idempotence.
        sessionObserver = RuntimeSessionObserver { _, _ -> AndroidViewOwnership.release(view) },
        primarySurfaceFactory = { id ->
            val surface = RuntimeHostSurface(id, initialSurfaceState(view))
            RuntimePrimarySurface(surface, surface::close)
        },
    )
    val attached = controller.attach(
        parentScope,
        // Phase 0 : application vide qui suspend — la session est réelle (elle démarre,
        // publie Starting → Running sur `session.state`) et reste Running jusqu'au stop
        // coopératif. Aucune application consommateur avant les overloads de phase 1.
        KadreApplicationFactory { KadreApplication { awaitCancellation() } },
        KadrePolicies.Default,
        launch = null,
    )
    return when (attached) {
        is KadreResult.Success -> attached.value

        is KadreResult.Failure -> {
            // Rejet post-admission (parentScope sans Job actif ou annulé, hôte fermé) :
            // zéro résidu — les ponts et l'ownership sont libérés AVANT l'exception.
            bridge.teardown()
            AndroidViewOwnership.release(view)
            throw IllegalStateException(
                "attachKadre could not start the session (${attached.reason})",
            )
        }
    }
}

/**
 * L'instantané initial de la surface, copié de la View comme le port web copie le sien
 * (`WebHostPort.initialSnapshot`).
 *
 * Une View attachée mais pas encore mesurée rapporte des dimensions nulles, et le modèle
 * de surface exige des tailles strictement positives : bornage au minimum valide — la même
 * base 1×1 que les tests hôtes du runtime. L'observation réelle des métriques arrive avec
 * les observers de phase 1.
 */
private fun initialSurfaceState(view: View): SurfaceState {
    val density = view.resources.displayMetrics.density.toDouble()
    val scaleFactor = if (density.isFinite() && density > 0.0) density else 1.0
    val physical = PhysicalSize(maxOf(1, view.width), maxOf(1, view.height))
    return SurfaceState(
        attachment = SurfaceAttachmentState.Attached,
        logicalSize = LogicalSize(physical.width / scaleFactor, physical.height / scaleFactor),
        physicalSize = physical,
        scaleFactor = scaleFactor,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
        visibility = SurfaceVisibility.Visible,
        occlusion = SurfaceOcclusion.Visible,
        focus = SurfaceFocus.Focused,
        appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
        cursor = CursorStyle.System(CursorIcon.Default),
        pointerCapture = PointerCaptureMode.None,
        hitTesting = HitTestingMode.Enabled,
        inputDefaultBehavior = InputDefaultBehavior.HostDefault,
        revision = SurfaceRevision(0L),
    )
}
