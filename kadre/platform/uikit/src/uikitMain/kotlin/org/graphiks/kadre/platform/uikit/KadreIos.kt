package org.graphiks.kadre.platform.uikit

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.diagnostics.ExperimentalKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.KadreLaunchInfo
import org.graphiks.kadre.internal.runtime.RuntimeDeferredAdmissionWindowManager
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionRevocationHandler
import org.graphiks.kadre.internal.runtime.RuntimeSessionObserver
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.internal.uikit.UIKitDisplayPort
import org.graphiks.kadre.internal.uikit.UIKitLifecycleReducer
import org.graphiks.kadre.internal.uikit.UIKitLifecycleSource
import org.graphiks.kadre.internal.uikit.UIKitLifecycleSnapshot
import org.graphiks.kadre.internal.uikit.UIKitMemoryPressureClaim
import org.graphiks.kadre.internal.uikit.UIKitNotificationCenterMemoryPressureSeam
import org.graphiks.kadre.internal.uikit.UIKitOwnership
import org.graphiks.kadre.internal.uikit.UIKitProcessMemoryPressure
import org.graphiks.kadre.internal.uikit.UIKitSceneRegistry
import org.graphiks.kadre.internal.uikit.UIKitScreenDisplaySource
import org.graphiks.kadre.internal.uikit.UIKitWindowRuntime
import org.graphiks.kadre.internal.uikit.UIKitWindowSceneManager
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.window.WindowManager
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
 *  2bis. créneau mémoire process (phase 8) — AVANT le contrôleur, sa capability devient
 *        `initialLifecycleCapabilities` ; libéré au terminal ET au rollback ;
 *  3. installation des observers de lifecycle avec rollback par étape
 *     ([UIKitOwnership.releaseAfterAttachFailure]) ;
 *  4. controller + factory (le runtime est créé DANS la factory, au premier attach de session) ;
 *  5. `attach` — en cas d'échec, rollback complet (observers + réservation + scope) ; en cas
 *     de succès, le relais mémoire du créneau est branché (`claim.wire`).
 *
 * L'appelant DOIT être sur le thread principal — contrat vérifié à l'entrée
 * (`NSThread.isMainThread()`, sinon IllegalStateException) : l'installation
 * NSNotificationCenter, le snapshot initial de la vue et le commit de la fenêtre primaire
 * sont synchrones sur ce thread ; le scope de session est posé sur `Dispatchers.Main`
 * (natif kotlinx-coroutines).
 */
@OptIn(ExperimentalKadreApi::class, ExperimentalAtomicApi::class)
public object KadreIos {
    /**
     * La source de pression mémoire process-wide (phase 8, Task 3, modèle AppKit/Android) —
     * UNE source pour toutes les attaches du processus : installée à la première (la
     * registration `NSNotificationCenter` est un fait PROCESS, comme la scène), réutilisée
     * par les suivantes, fermée par la terminaison du DERNIER host vivant. Les scènes d'un
     * même processus (attach ordinaire + enfants d'une demande de fenêtre) sont autant de
     * créneaux de LA MÊME source : un seul observer, fan-out vers tous les hosts câblés.
     */
    private val memoryPressure = UIKitProcessMemoryPressure()

    /**
     * L'attach UIKit/tvOS d'une session sur la scène primaire du host, avec l'échelle optionnelle
     * multi-fenêtre : un [windowSceneProvider] non nul annonce `requestWindow` comme
     * `OpenedInNewSession` (chaque demande admise active une scène enfant qui devient SA propre
     * session, attachée par le chemin ordinaire ci-dessous avec l'identité
     * `AdditionalHostRequested` de la demande qui l'a causée) ; `null` — le défaut, tvOS et apps
     * mono-fenêtre — refuse toute demande AVANT réservation (`Unsupported(RequestWindow)`) et
     * laisse le chemin exact des phases 1-3.
     */
    public fun attach(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
        windowSceneProvider: UIKitWindowSceneProvider? = null,
    ): KadreResult<KadreSession> = attachScene(
        windowScene = windowScene,
        window = window,
        surfaceView = surfaceView,
        applicationFactory = applicationFactory,
        policy = policy,
        windowSceneProvider = windowSceneProvider,
        launch = null,
    )

    /**
     * Le chemin ordinaire de l'attach — le SEUL : une session enfant d'une demande de fenêtre
     * admise s'y attache aussi ([launch] non nul), avec l'application et la policy du REQUÉRANT,
     * la réservation de sa scène dans le registre partagé (pas de second mécanisme
     * d'enregistrement — une scène qu'une session vivante possède refuse l'attach enfant) et le
     * relais de lifecycle de SA scène (déconnexion → détach → terminaison → le teardown des
     * components referme le runtime enfant). Sans provider : les enfants sont mono-fenêtre
     * (miroir web — la fabrique enfant n'ouvre pas de fenêtres).
     */
    private fun attachScene(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy,
        windowSceneProvider: UIKitWindowSceneProvider?,
        launch: KadreLaunchInfo?,
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
        val failureReporter = UIKitAttachDiagnostics

        // 2bis. Le créneau mémoire de CETTE attach (phase 8, Task 3, modèle Android) : pris
        //       AVANT le contrôleur — sa capability (`Available` après l'install réussie de
        //       la source process, `Unavailable(PlatformFailure(UIKit, "memory-pressure",
        //       "source-exception"))` après un échec d'install, le défaut runtime sans seam)
        //       devient le `initialLifecycleCapabilities` du contrôleur ci-dessous : la
        //       transition réelle exigée par la matrice C, publiée seulement après le succès
        //       d'install. La source vit au-delà des sessions ; le créneau meurt avec le host
        //       (terminal) ou avec l'attach (rollback — tous les chemins ci-dessous).
        val pressureClaim: UIKitMemoryPressureClaim =
            memoryPressure.newHost(UIKitNotificationCenterMemoryPressureSeam)

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
                releasePressureClaim(pressureClaim)
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
                releasePressureClaim(pressureClaim)
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
        //
        //    Le manager de demandes de scènes ENVELOPPE TOUJOURS le delegate (ordre :
        //    runtime.manager → admission différée → demandes de scènes) — la session installe sa
        //    configuration À TRAVERS lui (il implémente le fil `RuntimeSessionWindowManager` —
        //    sinon l'admission différée n'a jamais lieu). Avec un provider, il intercepte
        //    `requestWindow` et route provider ↔ session enfant ; sa fabrique enfant est le
        //    chemin ordinaire ci-dessus, `launch` portant l'identité de la demande. SANS
        //    provider (tvOS, apps mono-fenêtre), il refuse toute demande AVANT TOUTE réservation
        //    (`Rejected(Unsupported(RequestWindow))` — la décision figée du plan phase 4 et le
        //    contrat documenté de cet attach) : sans lui, la demande atteindrait le manager
        //    runtime qui ADMET puis committe un pair sur la UIWindow du host (fenêtre fantôme
        //    sans native propre — défaut capturé au driver, Task 3). `closeAction` : le drain
        //    des demandes pendantes (propre au manager de scènes) PUIS le close du runtime
        //    (chaîne de teardown des components, ordre inverse de la construction) — les deux
        //    à chaque terminaison ; le drain d'un manager sans pendante est un no-op.
        val controller = RuntimeHostController.withComponents(
            platform = KadrePlatform.UIKit,
            sessionRevocationHandler = RuntimeSessionRevocationHandler {
                // Admission révoquée EN PREMIER (roadmap l.120) : les observers cessent de
                // relayer ; la réservation reste à la terminaison.
                ownership.releaseObserversOnly()
            },
            componentsFactory = { _, rootScope ->
                val runtime = UIKitWindowRuntime(window, surfaceView, policy, failureReporter)
                val deferred = RuntimeDeferredAdmissionWindowManager(runtime.manager) {
                    runtime.admitPrimaryWindow()
                }
                val sceneManager = UIKitWindowSceneManager(
                    runtimeManager = deferred,
                    provider = windowSceneProvider,
                    policy = policy,
                    applicationFactory = applicationFactory,
                    failureReporter = failureReporter,
                    parentScope = rootScope,
                    childSessionFactory = { host ->
                        attachScene(
                            windowScene = host.windowScene,
                            window = host.window,
                            surfaceView = host.surfaceView,
                            applicationFactory = applicationFactory,
                            policy = policy,
                            windowSceneProvider = null,
                            launch = KadreLaunchInfo(
                                KadreLaunchReason.AdditionalHostRequested,
                                host.requestId,
                            ),
                        )
                    },
                )
                // L'ordre des wrappers, décidé au compile+test (plan Task 2) : la session porte sa
                // configuration par un cast `as? RuntimeSessionWindowManager` — le manager de
                // scènes la transmet au delegate d'admission différée SOUS lui, et son `state`
                // substitue la seule capability `requestWindow` (`Unsupported` sans provider —
                // le manager refuse ; `OpenedInNewSession` avec).
                val windows: WindowManager = sceneManager
                // Le port display de CETTE session (phase 8, Task 2) : l'inventaire `UIScreen`
                // relu EN ENTIER à chaque fire, l'écran de LA fenêtre de la session réglé HostViewport par la
                // ruling de la couture, les autres écrans Physical (tvOS : l'unique écran EST
                // l'écran de la fenêtre — un seul HostViewport). Le resolver est relu à chaque
                // relecture du port (un fire relit tout, la couture y comprise) ; le port est
                // FERMÉ par les composants (RuntimeSessionComponents.close → displayPort) — le
                // closeAction ci-dessous ne le referme jamais, et le seam qu'il possède meurt
                // avec lui.
                val displaySource = UIKitScreenDisplaySource()
                val displayPort = UIKitDisplayPort(
                    source = displaySource,
                    hostScreenResolver = { displaySource.factsOf(window.screen) },
                    displayTypeRuling = { isHostScreen ->
                        if (isHostScreen) DisplayType.HostViewport else DisplayType.Physical
                    },
                )
                RuntimeSessionComponents(
                    windows = windows,
                    primarySurfaceProvider = { runtime.attachedPrimarySurface },
                    displayPort = displayPort,
                    closeAction = {
                        sceneManager.close()
                        runtime.close()
                    },
                )
            },
            initialLifecycleState = toLifecycleState(initial),
            initialLifecycleCapabilities = pressureClaim.capabilities,
            failureReporter = failureReporter,
            sessionObserver = RuntimeSessionObserver { _, _ ->
                // Terminaison : observers puis réservation (ordre non négociable Task 3).
                ownership.release()
                // La terminaison du host libère le créneau mémoire (Task 3). Sur CE seam,
                // l'attach EST l'unité de host (une session par scène) : chaque terminaison du
                // runtime de session — close de session, hostDetached, arrêt — passe ICI et
                // libère le créneau ; le DERNIER libéré ferme la source process (retrait exact
                // du token posé). Une RÉVOCATION seule (admission révoquée, la session
                // terminant plus tard) ne passe PAS ici — le créneau vit jusqu'à la vraie
                // terminaison. Une déregistration lançante est SIGNALÉE sur le canal de
                // reporting, comme toute perte de terminal — elle ne remplace jamais la
                // traversée du terminal.
                try {
                    pressureClaim.hostTerminated()
                } catch (cause: Throwable) {
                    runCatching { failureReporter.report(cause) }
                }
            },
        )
        relay.store(UIKitSessionRelay(controller))

        // 5. attach — échec → rollback complet (patron Web : observers + réservation, le scope
        //    créé pour l'occasion est annulé). Le scope appartient à l'adaptateur. Le relais
        //    mémoire (Task 3) est branché sur l'attach RÉUSSI — chaque warning mappé par la
        //    source process atteint CE host ; le `runCatching` absorbe la course
        //    signal/terminal (un `emitMemoryPressure` sur un host détaché jette — le runtime
        //    exige un host attaché et `Available` ; le signal perdu est une observation, le
        //    prochain warning rétablit — le même parti pris que le relais de lifecycle).
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val attached = controller.attach(scope, applicationFactory, policy, launch)
        return when (attached) {
            is KadreResult.Success -> {
                pressureClaim.wire { level -> runCatching { controller.emitMemoryPressure(level) } }
                attached
            }
            is KadreResult.Failure -> {
                scope.cancel()
                ownership.releaseAfterAttachFailure()
                releasePressureClaim(pressureClaim)
                KadreResult.Failure(attached.reason)
            }
        }
    }

    /**
     * Le rollback du créneau mémoire d'un attach qui échoue (Task 3) : un attach qui échoue
     * ne laisse la source process installée pour personne. L'absorption est SYMÉTRIQUE au
     * chemin terminal ([sessionObserver] ci-dessus) : une déregistration lançante ne
     * remplace JAMAIS l'issue de l'attach — le rejet du contrôleur reste un
     * `KadreResult.Failure` retourné — elle est SIGNALÉE sur le canal de reporting, comme
     * toute perte de terminal. Idempotent avec la terminaison : un créneau ne quitte qu'une
     * fois.
     */
    private fun releasePressureClaim(claim: UIKitMemoryPressureClaim) {
        try {
            claim.hostTerminated()
        } catch (cause: Throwable) {
            runCatching { UIKitAttachDiagnostics.report(cause) }
        }
    }
}

/**
 * Le canal de diagnostic de la couture d'attach UIKit/tvOS — l'[RuntimeFailureReporter] que le
 * runtime et le port reçoivent pour chaque session construite ici : les throwables reportés par
 * le chemin d'observation de surface (`UIKitWindowCommandPort` enveloppe publication et
 * installation dans `runCatching` — une observation abandonnée ou un observer qui jette y
 * funnelle) y sont ENREGISTRÉS, jamais avalés en silence (miroir exact
 * d'`AndroidAttachDiagnostics`, `KadreAndroidAttach.kt` l.551).
 *
 * Journal borné (les plus récents gagnent) et thread-safe : le runtime y reporte depuis les
 * contexts de coroutine des sessions, pas seulement depuis le thread principal de l'attach.
 * Interne au module — la surface lisible du driver passe par la sonde publique
 * [KadreUikitSurfaceDriver] (scaffolding du driver, même posture que [KadreUikitProbe]).
 */
internal object UIKitAttachDiagnostics : RuntimeFailureReporter {
    private val lock = RuntimeLock()
    private val recorded = ArrayDeque<Throwable>()
    private var reportedTotal = 0

    override fun report(cause: Throwable) {
        lock.withLock {
            reportedTotal += 1
            recorded.addLast(cause)
            while (recorded.size > JOURNAL_CAPACITY) recorded.removeFirst()
        }
    }

    /** L'instantané du journal — pour l'observation (diagnostics, tests hôtes). */
    internal fun failures(): List<Throwable> = lock.withLock { recorded.toList() }

    /** Le nombre TOTAL de reports depuis le lancement du process — chaque report compte. */
    internal fun totalReported(): Int = lock.withLock { reportedTotal }

    private const val JOURNAL_CAPACITY = 16
}
