package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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
 */
internal class UIKitWindowCommandPort(
    private val window: UIWindow,
    private val view: UIView,
    resources: ResourceBudgetPolicy,
    private val failureReporter: RuntimeFailureReporter,
    private val stimulusSink: (SurfaceStimulus) -> Boolean,
    private val windowStateSink: (WindowId, WindowState) -> Boolean,
    private val readySink: (SurfaceId) -> Unit,
) : WindowCommandPort, SurfaceCommandPort {
    // `resources` (paramètre constructeur ci-dessus) est filé pour la symétrie AppKit
    // (l.275-277) ; phase 1 n'a rien à pré-réserver, phase 2 en héritera.

    private val lock = RuntimeLock()
    private val byRequest = linkedMapOf<WindowRequestId, PeerEntry>()
    private val byWindow = linkedMapOf<WindowId, PeerEntry>()
    private val bySurface = linkedMapOf<SurfaceId, PeerEntry>()
    private var closed = false

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
        command.commit(
            entry.owner,
            effectiveSpec = command.spec,
            initialSurfaceSnapshot = initialSnapshot(),
        ) { readySink(entry.surfaceId) }
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
     * le porte s'arrête de toute façon). Puis marquer fermé pour refuser toute nouvelle
     * admission.
     */
    fun beginClose() {
        redrawScheduler.cancel()
        lock.withLock { closed = true }
    }

    /** Sortie du registre des entries restantes ; les owners ne touchent aucun objet UIKit. */
    fun finishClose() {
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
