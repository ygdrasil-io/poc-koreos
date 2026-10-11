package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseCommand
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationCommand
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationOutcome
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.SurfaceCommandPort
import org.graphiks.kadre.internal.runtime.SurfaceFieldOutcome
import org.graphiks.kadre.internal.runtime.SurfaceInitialSnapshot
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.internal.runtime.SurfaceRedrawCommand
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommand
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommandOutcome
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationCommand
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationOutcome
import org.graphiks.kadre.internal.runtime.WindowUpdateCommand
import org.graphiks.kadre.internal.runtime.admitField
import org.graphiks.kadre.internal.runtime.fieldName
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowState
import platform.Foundation.NSThread
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Le port de fenêtre/surface unique d'un attach UIKit (miroir d'`AppKitWindowCommandPort`,
 * driver AppKit l.274-320). Un seul objet implémente `WindowCommandPort` et
 * `SurfaceCommandPort` ; les sinks pointent vers le `RuntimeWindowManager`, créé après le
 * port puis filé dans les closures (même patron de composition différée que le driver AppKit
 * l.98-102 : les lambdas lisent le manager au moment de l'invocation, pas à la création).
 *
 * Adaptation documentée de la signature du brief : la fenêtre et la vue sont des paramètres
 * constructeur (rôle du `nativePort` AppKit, porteur de l'accès natif l.75/l.275) — c'est ce
 * qui permet de lire le snapshot initial sur la vue au commit. Les références ne servent qu'à
 * LIRE ce snapshot : aucune écriture native, jamais.
 *
 * Différence structurelle assumée avec AppKit (plan Task 4 Step 2) : le window UIKit existe
 * DÉJÀ au moment de l'attach — le host fournit `UIWindow` + `UIView`. Il n'y a donc pas de
 * préparation asynchrone (`prepare` AppKit l.623-711) ni de file de commandes : `requestOpen`
 * enregistre l'entry sous verrou puis committe IMMÉDIATEMENT avec le snapshot initial de la
 * vue. Toute la vie du port reste sur le thread principal (l'appelant d'`attach` EST sur le
 * main ; les observers de scène sont installés sur la main queue).
 *
 * Contrainte roadmap l.120 : la fermeture ne détruit JAMAIS la `UIWindow`/`UIView` du host —
 * `UIKitWindowPeerOwner.close()` ne fait rien côté natif (destruct endogène) et
 * `requestOpenedClose` retire seulement l'entry du registre : le host garde sa fenêtre.
 *
 * Task 4 a ajouté `: UIKitViewLeasePort` à cette déclaration (miroir Android, `AndroidHostSurface`
 * l.66-67/l.89) : la UIView du host n'est exposée qu'au block du bail (`withUIKitView`, double
 * opt-in), jamais ailleurs. L'enregistrement du port dans le registre par surface
 * ([UIKitViewLeaseRegistry]) vit dans `UIKitWindowRuntime` — au commit du peer, retiré à la
 * fermeture AVANT la publication de l'état détaché (ordre Android l.415-416).
 */
internal class UIKitWindowCommandPort(
    private val window: UIWindow,
    private val view: UIView,
    resources: ResourceBudgetPolicy,
    private val failureReporter: RuntimeFailureReporter,
    private val stimulusSink: (SurfaceStimulus) -> Boolean,
    private val windowStateSink: (WindowId, WindowState) -> Boolean,
    private val readySink: (SurfaceId) -> Unit,
) : WindowCommandPort, SurfaceCommandPort, UIKitViewLeasePort {
    // `resources` (paramètre constructeur ci-dessus) est filé pour la symétrie AppKit
    // (l.275-277) ; phase 1 n'a rien à pré-réserver, phase 2 en héritera.

    private val lock = RuntimeLock()
    private val byRequest = linkedMapOf<WindowRequestId, PeerEntry>()
    private val byWindow = linkedMapOf<WindowId, PeerEntry>()
    private val bySurface = linkedMapOf<SurfaceId, PeerEntry>()
    private var closed = false

    /**
     * Le bail unique en vol (Task 4, miroir `AndroidHostSurface.lease` l.120-125), sous [lock] :
     * vrai entre l'admission d'un lease ([lease]) et la fin du block qu'il a admis — retour,
     * exception ou cancellation. Un second bail pendant ce créneau répond
     * `TemporarilyUnavailable(retryable = true)` sans invoquer son block.
     */
    private var leaseHeld: Boolean = false

    /**
     * Le branchement d'observation (phase 2, Task 3) : installé par [requestOpen] après le commit
     * (la surface doit être vivante avant qu'une publication n'arrive), retiré par
     * [requestOpenedClose] / [beginClose] / [finishClose]. Une seule surface en phase 2 — un seul
     * couple binding/observer par port, jamais réinstallé. Tout se joue sur la file principale.
     *
     * [surfaceMetrics] / [surfaceAppearance] sont le MIROIR du dernier état publié (la place que
     * le réducteur Android tient dans son binding, AndroidSurfaceMetrics.kt l.152-193) : le
     * kernel pur [`UIKitSurfaceObservationKernel`] déduplique et reconstruit le candidat CONTRE
     * cet état, et la publication route `MetricsChanged` / `AppearanceChanged` par différence —
     * le résultat combiné du kernel ne dit pas QUI a bougé, le diff contre le miroir le dit. Le
     * runtime reste l'autorité d'état (« publication state-before-event ») : ce miroir ne sert
     * qu'au jugement local, jamais de source de vérité.
     */
    private var observationBinding: UIKitSurfaceObservationBinding? = null
    private var appearanceObserver: UIKitAppearanceObserver? = null
    private var surfaceMetrics: SurfaceMetrics? = null
    private var surfaceAppearance: SurfaceAppearance? = null

    /**
     * Le drain coalescé du redraw (phase 2) : le ticket d'une commande admise est RETENU
     * (pending), au plus un drain est programmé sur la file principale, et la consommation —
     * le stimulus `RedrawConsumed` portant la génération exacte — n'arrive qu'au drain. Le
     * `surfaceId` est relu au moment du drain sous le verrou : le registre `bySurface` porte
     * le peer primaire (premier/unique peer en phase 2) ; un drain qui survient quand le
     * registre est déjà vide (fenêtre retirée, port en fermeture) n'émet rien — la surface
     * qui portait le ticket s'arrête de toute façon, il n'y a plus personne à réveiller.
     */
    private val redrawScheduler = UIKitRedrawScheduler(
        onDrain = { generation -> consumeRedraw(generation) },
        schedule = { block -> dispatch_async(dispatch_get_main_queue(), block) },
    )

    private class PeerEntry(
        val command: WindowOpenCommand,
        val owner: WindowPeerOwner,
    ) {
        val windowId: WindowId get() = command.windowId
        val surfaceId: SurfaceId get() = command.surfaceId
    }

    /** Réservation idempotente du peer ; NE touche JAMAIS aux objets UIKit (roadmap l.120). */
    internal class UIKitWindowPeerOwner(
        private val window: UIWindow,
        private val view: UIView,
    ) : WindowPeerOwner {
        // Phase 1 : aucun objet natif possédé par Kadre (la fenêtre et la vue restent au host).
        // La fermeture Kadre ne peut donc rien révoquer côté UIKit ; le pont libère les
        // wrappers Kotlin. Toute future ressource Kadre-owned (observer de layout, geste) sera
        // retirée ICI, jamais sur la fenêtre elle-même.
        override fun close() = Unit
    }

    override fun requestOpen(command: WindowOpenCommand) {
        val entry = PeerEntry(command, UIKitWindowPeerOwner(window, view))
        val admitted = lock.withLock {
            if (closed) {
                false
            } else {
                check(byRequest.put(command.requestId, entry) == null) { "duplicate UIKit window request" }
                check(byWindow.put(entry.windowId, entry) == null) { "duplicate UIKit window" }
                check(bySurface.put(entry.surfaceId, entry) == null) { "duplicate UIKit window surface" }
                true
            }
        }
        if (!admitted) {
            command.fail(KadreFailure.Closed(KadreResourceKind.Host))
            return
        }
        // Commit immédiat : la UIWindow fournie existe, aucune préparation native. Le manager,
        // en train de dispatcher requestOpen sous son verrou récursif, replanifie ce commit via
        // `finishOpenDispatchLocked` — la surface devient `primary` avant que le
        // `requestWindow` de l'init du runtime ne rende (sémantique synchrone exigée par
        // l'attach, cf. `RuntimeWindowManager.requestWindow` l.452-536).
        val snapshot = initialSnapshot()
        command.commit(
            entry.owner,
            effectiveSpec = command.spec,
            initialSurfaceSnapshot = snapshot,
        ) { readySink(entry.surfaceId) }
        // Task 3 : le binding d'observation est installé APRÈS le commit, avec le surfaceId du
        // peer pour le routage des stimuli. `UIKitWindowPeerOwner.close()` reste NEUTRE (aucun
        // objet UIKit touché) — le binding, lui, est Kadre-owned : c'est le PORT qui le retire
        // (requestOpenedClose / beginClose), jamais le destruct endogène.
        installSurfaceObservation(entry.surfaceId, snapshot)
    }

    /**
     * L'installation du couple binding d'observation + observer d'apparence (une seule fois, une
     * seule surface en phase 2). Le miroir démarre sur le snapshot EXACT passé au commit — le
     * premier kernel apply juge donc contre l'état réellement publié, jamais contre une re-lecture
     * divergente. Toute exception d'installation est rapportée ([failureReporter]) et absorbée :
     * un attach ne casse jamais pour un observer (posture « never crash from an observation »).
     */
    private fun installSurfaceObservation(surfaceId: SurfaceId, snapshot: SurfaceInitialSnapshot) {
        lock.withLock {
            if (observationBinding != null) return
            surfaceMetrics = snapshot.metrics
            surfaceAppearance = snapshot.appearance
        }
        val binding = UIKitSurfaceObservationBinding(
            view = view,
            scene = window.windowScene,
            window = window,
            publish = { observation -> onSurfaceObservation(surfaceId, observation) },
            onVisibility = { visible -> onVisibility(surfaceId, visible) },
        )
        val observer = window.windowScene?.let { scene ->
            UIKitAppearanceObserver(scene, onTheme = { theme -> onAppearancePush(surfaceId, theme) })
        }
        lock.withLock {
            observationBinding = binding
            appearanceObserver = observer
        }
        runCatching {
            binding.install()
            observer?.install()
        }.onFailure(failureReporter::report)
    }

    /**
     * Le chemin de publication du binding (Task 3) : le kernel pur juge l'observation contre le
     * miroir et la génération COURANTE du binding (lue au moment du traitement — une livraison
     * tardive après teardown est `Stale`, jamais publiée). Sur `Metrics`, le diff contre le
     * miroir route chaque stimulus MOUVÉ — les DEUX si les deux ont bougé (le résultat du kernel
     * est combiné, la cause vient du diff) — puis le miroir avance. Enveloppé dans `runCatching`
     * (ride-along Task 2 : la totalité du kernel n'est pas garantie sur des valeurs énormes) —
     * une observation qui jette est ABANDONNÉE et rapportée, jamais fatale.
     */
    private fun onSurfaceObservation(surfaceId: SurfaceId, observation: UIKitSurfaceObservation) {
        val binding = lock.withLock { observationBinding } ?: return
        val (currentMetrics, currentAppearance, currentGeneration) = lock.withLock {
            Triple(surfaceMetrics, surfaceAppearance, binding.generation)
        }
        runCatching {
            val metrics = currentMetrics ?: return
            val appearance = currentAppearance ?: return
            when (val reduction = UIKitSurfaceObservationKernel.apply(
                current = metrics,
                appearance = appearance,
                currentGeneration = currentGeneration,
                observation = observation,
            )) {
                is UIKitSurfaceReduction.Metrics -> {
                    val metricsMoved = reduction.metrics != metrics
                    val appearanceMoved = reduction.appearance != appearance
                    lock.withLock {
                        surfaceMetrics = reduction.metrics
                        surfaceAppearance = reduction.appearance
                    }
                    if (metricsMoved) {
                        stimulusSink(SurfaceStimulus.MetricsChanged(surfaceId, reduction.metrics))
                    }
                    if (appearanceMoved) {
                        stimulusSink(SurfaceStimulus.AppearanceChanged(surfaceId, reduction.appearance))
                    }
                }
                UIKitSurfaceReduction.Ignored -> Unit
                UIKitSurfaceReduction.Stale -> Unit
            }
        }.onFailure(failureReporter::report)
    }

    /**
     * La visibilité observée (re-lue de `window.isHidden` sur les déclencheurs du binding) : le
     * stimulus porte `Visible`/`Hidden` et l'occlusion INTOUCHÉE (`Visible`, aucune notion
     * d'occlusion UIKit — phase 1). Les valeurs répétées sont dédupliquées par le runtime.
     */
    private fun onVisibility(surfaceId: SurfaceId, visible: Boolean) {
        runCatching {
            stimulusSink(
                SurfaceStimulus.VisibilityChanged(
                    surfaceId,
                    if (visible) SurfaceVisibility.Visible else SurfaceVisibility.Hidden,
                    SurfaceOcclusion.Visible,
                ),
            )
        }.onFailure(failureReporter::report)
    }

    /**
     * Le seam du push 17+ (route du `UIKitAppearanceObserver`) : le thème poussé rejoint le
     * MÊME chemin de publication (thème seul, tous les autres champs `null` — le kernel les
     * conserve) ; la génération courante du binding étiquette l'observation. Aujourd'hui le push
     * ne peut pas s'enregistrer (API absente du mapping K/N, cf. rapport) — la route reste
     * câblée, prête, et le kernel dédupliquerait de toute façon un thème inchangé.
     */
    private fun onAppearancePush(surfaceId: SurfaceId, theme: SurfaceTheme) {
        val binding = lock.withLock { observationBinding } ?: return
        onSurfaceObservation(
            surfaceId,
            UIKitSurfaceObservation(
                logicalWidth = null,
                logicalHeight = null,
                scaleFactor = null,
                safeAreaTop = null,
                safeAreaRight = null,
                safeAreaBottom = null,
                safeAreaLeft = null,
                theme = theme,
                generation = binding.generation,
            ),
        )
    }

    /**
     * Le retrait du branchement (requestOpenedClose / beginClose / finishClose) : zéro observer
     * résiduel (teardown du binding = tokens retirés + génération périmée), miroir remis à zéro.
     * Idempotent — les trois chemins peuvent converger.
     */
    private fun teardownSurfaceObservation() {
        val stale = lock.withLock {
            if (observationBinding == null && appearanceObserver == null) {
                null
            } else {
                val binding = observationBinding
                val observer = appearanceObserver
                observationBinding = null
                appearanceObserver = null
                surfaceMetrics = null
                surfaceAppearance = null
                binding to observer
            }
        } ?: return
        stale.first?.teardown()
        stale.second?.teardown()
    }

    /**
     * Phase 1 = snapshot initial figé (plan : les updates arrivent en phase 2). Posture AppKit
     * pour une mise à jour non applicable (`AppKitWindowCommandPort.apply` l.593-598) :
     * `TemporarilyUnavailable(retryable = false)` — l'application reçoit une failure explicite
     * plutôt qu'un silence.
     */
    override fun requestUpdate(command: WindowUpdateCommand) {
        command.failed(KadreFailure.TemporarilyUnavailable(retryable = false))
    }

    override fun requestUpdateCancellation(
        command: WindowUpdateCancellationCommand,
    ): WindowUpdateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate

    /**
     * Le commit est immédiat : au moment où une annulation peut arriver, il n'y a plus rien de
     * pendant à retirer — l'arbitrage AppKit (entry pendant, `CancellationRequested`, l.506-530)
     * n'a pas d'équivalent phase 1.
     */
    override fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome = PendingWindowCancellationOutcome.TooLate

    /**
     * Fermeture d'une fenêtre ouverte : retirer l'entry du registre et accepter. Aucun objet
     * UIKit n'est touché (le host garde sa fenêtre — contrainte roadmap l.120). Posture fidèle
     * à AppKit l.532-548 côté registre (l'entry sort, le owner sera clos par le manager), la
     * partie native-close en moins.
     */
    override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
        val entry = lock.withLock {
            byRequest.remove(command.requestId)?.also {
                byWindow.remove(it.windowId)
                bySurface.remove(it.surfaceId)
            }
        } ?: return OpenedWindowCloseOutcome.PlatformFailure(
            KadreFailure.PlatformFailure(KadrePlatform.UIKit, "uikit-window", "missing-peer"),
        )
        // Task 3 : l'entry sort → le branchement d'observation de CETTE surface est retiré
        // (une seule surface en phase 2 : l'entry retirée EST la surface observée). Aucun objet
        // UIKit n'est touché — seuls les observers Kadre-owned sont libérés.
        teardownSurfaceObservation()
        return OpenedWindowCloseOutcome.Accepted
    }

    /**
     * Posture phase 2 (ticket RETENU, miroir Android `AndroidHostSurface.requestRedraw`
     * l.262-274) : l'admission — peer présent, port ouvert, vérifiée sous verrou — retient la
     * génération de la commande dans le scheduler coalescé et répond `Success(Unit)` ; la
     * consommation n'est PAS immédiate. Le drain (file principale) émettra UN
     * `RedrawConsumed` portant exactement la génération pendante, exactement une fois — le
     * runtime ne réalloue un ticket qu'après consommation, et un ticket jamais consommé
     * fermerait le robinet de redraw pour toujours. Les rafales rejoignent le drain en vol
     * (le coalescing ne consomme que la dernière génération).
     */
    override fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit> {
        val present = lock.withLock { !closed && bySurface.containsKey(command.surfaceId) }
        if (!present) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        }
        redrawScheduler.request(command.generation)
        return KadreResult.Success(Unit)
    }

    /** Le corps de drain : émet le stimulus pour le peer primaire, s'il vit encore. */
    private fun consumeRedraw(generation: SurfaceRedrawGeneration) {
        val surfaceId = lock.withLock { bySurface.values.firstOrNull()?.surfaceId } ?: return
        stimulusSink(SurfaceStimulus.RedrawConsumed(surfaceId, generation))
    }

    /**
     * Le bail d'accès natif (Task 4) : prête la UIView du host pour la durée de [block], et pas
     * plus. L'échelle EXACTE du miroir Android (`AndroidHostSurface.lease` l.305-333, template
     * du plan) :
     *
     * 1. l'admission d'abord, sous [lock] — un port fermé, ou sans peer vivant, répond
     *    `Closed(Surface)` sans invoquer [block] ; elle GAGNE sur la garde de réentrance : un
     *    bail après close n'est jamais re-tentable, le décrire retryable serait un mensonge ;
     * 2. le bail unique — un bail commencé pendant qu'un autre est en vol répond
     *    `TemporarilyUnavailable(retryable = true)`, [block] jamais invoqué : se rentrer dedans
     *    est une erreur de programmation, pas une contre-pression ;
     * 3. la cancellation précoce — `ensureActive` sous le verrou, AVANT la pose du bail : le
     *    chemin main (inline) n'a ensuite AUCUN point de suspension avant la première
     *    instruction du block, et l'invocation démarrée, elle, s'achève (NonCancellable). Un
     *    rejet ici sort avant `leaseHeld = true` : le `finally` n'a rien à libérer ;
     * 4. la vue est RELUE hors de la section critique d'admission — l'entry du registre du port
     *    EST la source vivante de la vue (l'analogue UIKit du `viewSource` Android effacé par le
     *    terminal, M-3) : retirée par `requestOpenedClose` ou vidée par `finishClose`, ou le
     *    port fermé par `beginClose`, la relecture trouve vide et répond `Closed(Surface)` SANS
     *    invoquer le block. Si la relecture a gagné la course, la UIView était celle d'une
     *    surface vivante à l'instant de la remise (et la UIView du host n'est de toute façon
     *    jamais détruite par Kadre — roadmap l.120) ;
     * 5. le block tourne sur le thread host (le principal UIKit, le même que l'attach) : inline
     *    si l'appelant y est déjà (`NSThread.isMainThread()`, le précédent `KadreIos.attach`
     *    l.95), sinon `withContext(Dispatchers.Main.immediate)` — et l'invocation DÉMARRÉE est
     *    enveloppée de `NonCancellable` : un callback commencé s'achève même si la coroutine
     *    appelante est annulée pendant lui, et la cancellation se manifeste chez l'appelant
     *    après (le résultat n'est remis qu'à un waiter resté actif) ;
     * 6. l'exception applicative du block se propage TELLE QUELLE — jamais enveloppée en
     *    `KadreFailure` (la taxonomie n'admets pas ce mensonge) — et le `finally` rend le bail
     *    sous le verrou sur tous les chemins : un bail suivant réussit après un block lançant
     *    comme après un block annulé. La UIView n'est retenue par personne après le retour.
     *
     * Le peer servi est le PRIMAIRE (premier/unique peer en phase 2 — le miroir de
     * [consumeRedraw] : toutes les entries de ce port pointent de toute façon la MÊME
     * `UIWindow`/`UIView` du constructeur).
     */
    override suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R> {
        lock.withLock {
            if (closed || bySurface.isEmpty()) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
            }
            if (leaseHeld) {
                return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
            }
            currentCoroutineContext().ensureActive()
            leaseHeld = true
        }
        try {
            // La source vivante de la vue est RELUE ICI, hors de la section critique
            // d'admission mais APRÈS elle (M-3) : si le teardown a passé sa section critique,
            // la relecture répond Closed SANS invoquer le block — une vue d'une surface qui ne
            // vit plus n'est jamais remise.
            val viewLive = lock.withLock { !closed && bySurface.isNotEmpty() }
            if (!viewLive) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
            }
            return if (NSThread.isMainThread()) {
                KadreResult.Success(withContext(NonCancellable) { block(view) })
            } else {
                withContext(Dispatchers.Main.immediate) {
                    KadreResult.Success(withContext(NonCancellable) { block(view) })
                }
            }
        } finally {
            lock.withLock { leaseHeld = false }
        }
    }

    /**
     * L'échelle fermée `apply` (phase 2, miroir `AndroidHostSurface.apply` l.180-260) :
     *
     * 1. la porte d'admission — un port fermé, ou une surface inconnue du registre, répond
     *    `Closed(Surface)` ;
     * 2. la garde `Clear` — un `Clear` est un `InvalidRequest(field)` AVANT toute admission
     *    (le port le porte, PAS le kernel : c'est la forme d'Android, où l'`apply` extérieur
     *    intercepte par `invalidClearField` l.214-216/l.496-505 et rend la failure
     *    `KadreResult` ; le kernel pur, sans canal de failure, exige qu'aucun Clear ne lui
     *    arrive) ;
     * 3. le kernel pur [`uikitSurfaceApplyOutcome`] — admission champ par champ contre les
     *    capabilities publiées, verdict par champ ;
     * 4. la réponse — quatre [`SurfaceFieldOutcome`] explicites (miroir de la forme
     *    `UnsupportedSurfaceCommandPort` l.87-100) : `Unchanged` pour un champ non posé,
     *    `Rejected` pour un champ posé que la capability refuse. Jamais de failure externe
     *    pour l'update entier : le rejet est PAR CHAMP, dans le succès.
     *
     * `Closed` (ci-dessus) et un éventuel `StaleRevision` (le runtime le garde AVANT de
     * construire la commande — la commande ne porte pas de révision) restent hors kernel :
     * ce sont des juges d'état vivant, pas de l'échelle.
     */
    override suspend fun apply(
        command: SurfaceUpdateCommand,
    ): KadreResult<SurfaceUpdateCommandOutcome> {
        val present = lock.withLock { !closed && bySurface.containsKey(command.surfaceId) }
        if (!present) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        }
        val update = SurfaceUpdate(
            cursor = command.cursor,
            pointerCapture = command.pointerCapture,
            hitTesting = command.hitTesting,
            inputDefaultBehavior = command.inputDefaultBehavior,
        )
        invalidSurfaceClearField(update)?.let { field ->
            return KadreResult.Failure(KadreFailure.InvalidRequest(field))
        }
        val outcome = uikitSurfaceApplyOutcome(
            current = APPLY_LADDER_STATE,
            update = update,
            capabilities = uikitSurfaceCapabilities(),
        )
        val rejectedByField = when (outcome) {
            is SurfaceUpdateOutcome.Applied -> emptyMap()
            is SurfaceUpdateOutcome.PartiallyApplied -> outcome.rejected.associate { it.field to it.failure }
        }
        return KadreResult.Success(
            SurfaceUpdateCommandOutcome(
                cursor = command.cursor.fieldOutcome(SurfaceProperty.Cursor, rejectedByField[SurfaceProperty.Cursor]),
                pointerCapture = command.pointerCapture.fieldOutcome(
                    SurfaceProperty.PointerCapture,
                    rejectedByField[SurfaceProperty.PointerCapture],
                ),
                hitTesting = command.hitTesting.fieldOutcome(
                    SurfaceProperty.HitTesting,
                    rejectedByField[SurfaceProperty.HitTesting],
                ),
                inputDefaultBehavior = command.inputDefaultBehavior.fieldOutcome(
                    SurfaceProperty.InputDefaultBehavior,
                    rejectedByField[SurfaceProperty.InputDefaultBehavior],
                ),
            ),
        )
    }

    /**
     * Fermeture du port (ordre AppKit l.600-608 adapté) : le scheduler est annulé AVANT le
     * verrou — aucun drain après fermeture, un ticket pendant est abandonné (la surface qui
     * le porte s'arrête de toute façon). Puis le branchement d'observation est retiré (Task 3 :
     * tokens + génération périmée — aucune publication résiduelle) et le port marqué fermé pour
     * refuser toute nouvelle admission.
     */
    fun beginClose() {
        redrawScheduler.cancel()
        teardownSurfaceObservation()
        lock.withLock { closed = true }
    }

    /** Sortie du registre des entries restantes ; les owners ne touchent aucun objet UIKit. */
    fun finishClose() {
        // Défense en profondeur (idempotent) : si un chemin de close oubliait beginClose, le
        // branchement d'observation ne survivrait pas au port.
        teardownSurfaceObservation()
        val remaining = lock.withLock {
            val entries = byRequest.values.toList()
            byRequest.clear()
            byWindow.clear()
            bySurface.clear()
            entries
        }
        remaining.forEach { it.owner.close() }
    }

    /**
     * Snapshot d'attach copié hors des structs Obj-C (`useContents` — aucune rétention native),
     * la matière du `SurfaceInitialSnapshot` (AppKit `toRuntimeSnapshot` l.2378-2384).
     */
    @OptIn(ExperimentalForeignApi::class)
    internal fun initialSnapshot(): SurfaceInitialSnapshot {
        val bounds = view.bounds.useContents { size.width to size.height }
        // `LogicalSize` exige des dimensions > 0 : une vue pas encore layoutée peut rapporter
        // 0×0 à l'attach. Le bornage à 1 pt est une garde de validité, pas une sémantique.
        val logical = LogicalSize(bounds.first.coerceAtLeast(1.0), bounds.second.coerceAtLeast(1.0))
        // `displayScale` vaut 0 tant que la vue n'a pas de fenêtre effective ; la validation
        // d'attach garantit une vue windowée, la garde ne couvre que l'intervalle de lecture.
        val scale = view.traitCollection.displayScale
        val scaleFactor = if (scale > 0.0) scale else 1.0
        val insets = view.safeAreaInsets.useContents { LogicalInsets(top, right, bottom, left) }
        val visibility = if (window.hidden) SurfaceVisibility.Hidden else SurfaceVisibility.Visible
        // Aucune notion d'occlusion UIKit en phase 1 : la scène attachée est Visible.
        val occlusion = SurfaceOcclusion.Visible
        // UIKit n'a pas de focus surface (la télécommande tvOS est un input, pas un focus
        // surface) : la valeur « aucun focus » de l'enum foundation.
        val focus = SurfaceFocus.Unfocused
        val theme = if (view.traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark) {
            SurfaceTheme.Dark
        } else {
            SurfaceTheme.Light
        }
        val appearance = SurfaceAppearance(theme = theme, contrast = SurfaceContrast.Normal)
        return SurfaceInitialSnapshot(
            metrics = SurfaceMetrics(
                logicalSize = logical,
                physicalSize = logical.toPhysical(scaleFactor),
                scaleFactor = scaleFactor,
                safeAreaInsets = insets,
            ),
            focus = focus,
            visibility = visibility,
            occlusion = occlusion,
            appearance = appearance,
        )
    }
}

/**
 * L'état porteur du verdict du kernel UIKit. Le port ne possède PAS d'état de surface (le
 * runtime le possède) et la conversion en `SurfaceUpdateCommandOutcome` — quatre verdicts par
 * champ, sans état — abandonne le `current` du verdict : ce porteur est neutre, constant,
 * jamais observable. Il existe parce que la signature du kernel est celle du miroir Android
 * (`Applied(current)` / `PartiallyApplied(current, rejected)`), la forme que les tests du
 * kernel jugent directement.
 */
private val APPLY_LADDER_STATE = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Unfocused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

/**
 * Les capabilities annoncées du surface UIKit (phase 2) : les quatre champs update refusés
 * `Unsupported(UpdateSurface)` (aucun chemin de commit natif — la vue du host se rend
 * elle-même), `handlerInteractions`/`armedInteractions` refusés, `platformAccess` `Supported`
 * (la rangée UIKit du registre des capabilities, miroir d'`androidSurfaceCapabilities`
 * l.523-526 : les métriques publiées et le redraw coalescé vivent). Publiée par
 * `UIKitWindowRuntime` (`enabledSurfaceCapabilities`), consommée par le kernel d'`apply`.
 */
internal fun uikitSurfaceCapabilities(): SurfaceCapabilities =
    unsupportedSurfaceCapabilities().copy(
        platformAccess = Capability.Supported(Unit, FeatureAvailability.Available),
    )

/**
 * Le champ d'un `Clear`, ou `null` — la garde du port, AVANT toute admission (miroir exact
 * d'`AndroidHostSurface.invalidClearField` l.496-505 : les quatre noms du registre). Le
 * `apply` du port la consulte avant le kernel et rend `InvalidRequest(field)` ; le kernel
 * l'exige nul (un Clear qui lui arriverait est une erreur de programmation, pas un rejet).
 */
internal fun invalidSurfaceClearField(update: SurfaceUpdate): String? = when {
    update.cursor is PropertyChange.Clear -> SurfaceProperty.Cursor.fieldName
    update.pointerCapture is PropertyChange.Clear -> SurfaceProperty.PointerCapture.fieldName
    update.hitTesting is PropertyChange.Clear -> SurfaceProperty.HitTesting.fieldName
    update.inputDefaultBehavior is PropertyChange.Clear -> SurfaceProperty.InputDefaultBehavior.fieldName
    else -> null
}

/**
 * Le kernel pur de l'échelle `apply` — le miroir du corps d'admission champ par champ
 * d'`AndroidHostSurface.apply` (l.217-259), sans état vivant (pas de `Closed`, pas de
 * `StaleRevision` — le port et le runtime les jugent avant) et sans UIKit :
 *
 * 1. `admitField` champ par champ contre la capability publiée — chaque champ posé est soit
 *    admis, soit RAPPORTÉ dans `rejected` (jamais silencieusement abandonné), dans l'ordre de
 *    déclaration de `SurfaceProperty` (l'ordre où le kernel les marche — une seule passe,
 *    donc la liste sort déjà ordonnée : le tri `inSurfaceUpdateOrder` du web ne sert qu'aux
 *    refus en DEUX passes, forme que le kernel n'a pas) ;
 * 2. un Clear est refusé d'entrée (voir [invalidSurfaceClearField]) ;
 * 3. les gardes `requireRefused` — un champ ADMIS sans chemin de commit est une erreur de
 *    programmation criante, pas un `Applied` pour un état qui ne l'a jamais pris ;
 * 4. le verdict — rien de posé → `Applied(current)` ; un champ posé →
 *    `PartiallyApplied(current, rejected)`.
 */
internal fun uikitSurfaceApplyOutcome(
    current: SurfaceState,
    update: SurfaceUpdate,
    capabilities: SurfaceCapabilities,
): SurfaceUpdateOutcome {
    check(invalidSurfaceClearField(update) == null) {
        "a Clear arrived at the UIKit apply kernel; the port's Clear guard must answer InvalidRequest first"
    }
    val rejected = mutableListOf<RejectedSurfaceField>()
    val cursor = admitField(update.cursor, SurfaceProperty.Cursor, capabilities.cursor, rejected)
    val pointerCapture = admitField(
        update.pointerCapture,
        SurfaceProperty.PointerCapture,
        capabilities.pointerCapture,
        rejected,
    )
    val hitTesting = admitField(
        update.hitTesting,
        SurfaceProperty.HitTesting,
        capabilities.hitTesting,
        rejected,
    )
    val inputDefaultBehavior = admitField(
        update.inputDefaultBehavior,
        SurfaceProperty.InputDefaultBehavior,
        capabilities.inputDefaultBehavior,
        rejected,
    )
    cursor.requireRefused(SurfaceProperty.Cursor)
    pointerCapture.requireRefused(SurfaceProperty.PointerCapture)
    hitTesting.requireRefused(SurfaceProperty.HitTesting)
    inputDefaultBehavior.requireRefused(SurfaceProperty.InputDefaultBehavior)
    return if (rejected.isEmpty()) {
        SurfaceUpdateOutcome.Applied(current)
    } else {
        SurfaceUpdateOutcome.PartiallyApplied(current, rejected)
    }
}

/** Affirme qu'un champ sans chemin de commit a bien été refusé par sa capability. */
private fun PropertyChange<*>.requireRefused(property: SurfaceProperty) {
    check(this is PropertyChange.Unchanged) {
        "${property.fieldName} arrived admitted, but the UIKit surface has no commit for it"
    }
}

/**
 * Le verdict par champ du port : non posé → `Unchanged` ; posé et refusé par sa capability →
 * `Rejected` ; posé et admis → impossible (le kernel l'a déjà refusé par `requireRefused`).
 */
private fun PropertyChange<*>.fieldOutcome(
    property: SurfaceProperty,
    rejected: KadreFailure?,
): SurfaceFieldOutcome<Nothing> = when {
    this is PropertyChange.Unchanged -> SurfaceFieldOutcome.Unchanged
    rejected != null -> SurfaceFieldOutcome.Rejected(rejected)
    else -> error("${property.fieldName} arrived admitted without a commit path")
}
