package org.graphiks.kadre.platform.uikit

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.diagnostics.ExperimentalKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDeferredAdmissionWindowManager
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionRevocationHandler
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.internal.uikit.UIKitLifecycleReducer
import org.graphiks.kadre.internal.uikit.UIKitLifecycleSource
import org.graphiks.kadre.internal.uikit.UIKitLifecycleSnapshot
import org.graphiks.kadre.internal.uikit.UIKitOwnership
import org.graphiks.kadre.internal.uikit.UIKitSceneRegistry
import org.graphiks.kadre.internal.uikit.UIKitWindowRuntime
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import platform.Foundation.NSThread
import platform.UIKit.UISceneActivationStateUnattached
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Relais de cycle de vie vers le controller, construit APRÈS lui (les closures d'observateur
 * installées avant lisent l'AtomicReference : `null` avant le set = rien à relayer). Une
 * notification mise en file avant `attach` ne trouve donc jamais un controller à moitié
 * monté. `update` cesse de relayer après `detach` : `RuntimeHostController.updateLifecycle`
 * exige un host non détaché, et une notification déjà en file ne doit jamais lever dans le
 * block NSNotificationCenter.
 */
private class UIKitSessionRelay(private val controller: RuntimeHostController) {
    private var detached = false

    fun update(snapshot: UIKitLifecycleSnapshot) {
        if (detached) return
        controller.updateLifecycle(toLifecycleState(snapshot))
    }

    fun detach() {
        detached = true
        controller.detach()
    }
}

/** Mapping snapshot réduit → état de lifecycle foundation (attaché par construction). */
private fun toLifecycleState(snapshot: UIKitLifecycleSnapshot): LifecycleState = LifecycleState(
    attachment = AttachmentState.Attached,
    visibility = snapshot.visibility,
    activation = snapshot.activation,
)

/**
 * L'attach UIKit/tvOS normatif (DESIGN.md §15.2). Une session par `UIWindowScene` ; le host
 * choisit explicitement la `UIWindow` primaire et la `UIView` de contenu ; Kadre ne détruit
 * jamais la hiérarchie native — le teardown ne relâche que les ressources Kadre (observers,
 * réservation, runtime).
 *
 * L'échelle (roadmap l.106-120, patron Web `WebHostSession`) :
 *  1. validations fermées sur les champs `window` / `surfaceView` ;
 *  2. réservation de la scène AVANT toute installation (`AlreadyInUse(Host)` sur doublon) ;
 *  3. installation des observers de lifecycle avec rollback par étape
 *     ([UIKitOwnership.releaseAfterAttachFailure]) ;
 *  4. controller + factory (le runtime est créé DANS la factory, au premier attach de session) ;
 *  5. `attach` — en cas d'échec, rollback complet (observers + réservation + scope).
 *
 * L'appelant DOIT être sur le thread principal — contrat vérifié à l'entrée
 * (`NSThread.isMainThread()`, sinon IllegalStateException) : l'installation
 * NSNotificationCenter, le snapshot initial de la vue et le commit de la fenêtre primaire
 * sont synchrones sur ce thread ; le scope de session est posé sur `Dispatchers.Main`
 * (natif kotlinx-coroutines).
 */
@OptIn(ExperimentalKadreApi::class, ExperimentalAtomicApi::class)
public object KadreIos {
    public fun attach(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession> {
        check(NSThread.isMainThread()) {
            "KadreIos.attach doit être appelé depuis le thread principal (les lectures UIKit et le pont d'admission l'exigent)"
        }

        // 1. Validations (roadmap l.106). `===` ne pont pas l'identité Obj-C (preuve O3,
        //    phase 0) : les comparaisons passent par NSObject.isEqual.
        val scene: UIWindowScene = window.windowScene
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
        if (!scene.isEqual(windowScene)) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
        }
        if (windowScene.session == null || windowScene.activationState == UISceneActivationStateUnattached) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
        }
        val viewWindow: UIView? = surfaceView.window
        if (viewWindow == null || !viewWindow.isEqual(window)) {
            return KadreResult.Failure(KadreFailure.InvalidRequest("surfaceView"))
        }

        // 2. Réservation AVANT toute installation (roadmap l.107). Le registre est le
        //    process-wide de la Task 3 : un fresh registry ne verrait jamais les doublons.
        val reservation = when (val r = UIKitSceneRegistry.shared.reserve(windowScene)) {
            is KadreResult.Success -> r.value
            is KadreResult.Failure -> return KadreResult.Failure(r.reason)
        }
        val ownership = UIKitOwnership(reservation)
        val failureReporter = RuntimeFailureReporter { }

        // 3. Source de lifecycle + observations, avec rollback par étape. Le relais pointe
        //    vers le controller créé plus bas : la référence atomique est lue par les closures
        //    (null avant le set = rien à relayer — jamais d'effet avant Running).
        val source = UIKitLifecycleSource(windowScene)
        // L'état initial se lit sur la scène AVANT d'installer les observers, pour que la
        // construction du controller parte du même état que le premier snapshot réduit.
        // Échec ici : la scène est unattached malgré la validation (course de terminaison) —
        // rollback de la réservation et refus.
        val initial = UIKitLifecycleReducer.initial(windowScene)
            ?: run {
                ownership.releaseAfterAttachFailure()
                return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
            }
        val relay = AtomicReference<UIKitSessionRelay?>(null)
        when (val observed = ownership.observeLifecycle(
            source,
            onSnapshot = { snapshot -> relay.load()?.update(snapshot) },
            onDisconnect = { relay.load()?.detach() },
            onLateCallback = { /* sentinelle : jamais d'effet après terminaison de l'attach */ },
        )) {
            is KadreResult.Failure -> {
                ownership.releaseAfterAttachFailure()
                return KadreResult.Failure(observed.reason)
            }
            is KadreResult.Success -> Unit
        }

        // 4. Controller + factory — le runtime est créé DANS la factory (patron AppKit
        //    l.307-340) : paresseux par session, exactement une fois, au moment de l'attach.
        //    L'admission de la fenêtre primaire est DIFFÉRÉE : le delegate
        //    `RuntimeDeferredAdmissionWindowManager` transmet d'abord `installSessionConfiguration`
        //    au manager (qui refuse toute admission avant le stamp source), PUIS appelle
        //    `admitPrimaryWindow` — commit synchrone, avant Running, après la configuration.
        //    `attachedPrimarySurface` n'est lu par la session qu'après cette installation.
        val controller = RuntimeHostController.withComponents(
            platform = KadrePlatform.UIKit,
            sessionRevocationHandler = RuntimeSessionRevocationHandler {
                // Admission révoquée EN PREMIER (roadmap l.120) : les observers cessent de
                // relayer ; la réservation reste à la terminaison.
                ownership.releaseObserversOnly()
            },
            componentsFactory = { _, _ ->
                val runtime = UIKitWindowRuntime(window, surfaceView, policy, failureReporter)
                RuntimeSessionComponents(
                    windows = RuntimeDeferredAdmissionWindowManager(runtime.manager) {
                        runtime.admitPrimaryWindow()
                    },
                    primarySurfaceProvider = { runtime.attachedPrimarySurface },
                    closeAction = runtime::close,
                )
            },
            initialLifecycleState = toLifecycleState(initial),
            failureReporter = failureReporter,
            sessionObserver = RuntimeSessionObserver { _, _ ->
                // Terminaison : observers puis réservation (ordre non négociable Task 3).
                ownership.release()
            },
        )
        relay.store(UIKitSessionRelay(controller))

        // 5. attach — échec → rollback complet (patron Web : observers + réservation, le scope
        //    créé pour l'occasion est annulé). Le scope appartient à l'adaptateur.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val attached = controller.attach(scope, applicationFactory, policy)
        return when (attached) {
            is KadreResult.Success -> attached
            is KadreResult.Failure -> {
                scope.cancel()
                ownership.releaseAfterAttachFailure()
                KadreResult.Failure(attached.reason)
            }
        }
    }
}
