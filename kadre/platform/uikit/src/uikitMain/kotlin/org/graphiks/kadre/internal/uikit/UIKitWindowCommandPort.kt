package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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
import org.graphiks.kadre.internal.runtime.SurfaceInitialSnapshot
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.internal.runtime.SurfaceRedrawCommand
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommand
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommandOutcome
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationCommand
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationOutcome
import org.graphiks.kadre.internal.runtime.WindowUpdateCommand
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowState
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIWindow

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
     * Posture AppKit-fidèle (choix documenté, plan Task 4 Step 2) : AppKit ADMET le redraw
     * (driver l.564-579, `Success`) et le confirme par un stimulus natif. Phase 1, la vue du
     * host se rend elle-même : l'invalidation n'a aucun effet natif, mais l'admission —
     * vérifiée sous verrou — et la confirmation (`RedrawConsumed` portant la génération exacte
     * de la commande) préservent le protocole surface↔runtime — `HostSurface.requestRedraw()`
     * reste utilisable par l'application au lieu de lever `Unsupported`.
     */
    override fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit> {
        val present = lock.withLock { !closed && bySurface.containsKey(command.surfaceId) }
        if (!present) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        }
        stimulusSink(SurfaceStimulus.RedrawConsumed(command.surfaceId, command.generation))
        return KadreResult.Success(Unit)
    }

    /** Même posture que `requestUpdate` : snapshot figé en phase 1 (AppKit l.593-598). */
    override suspend fun apply(
        command: SurfaceUpdateCommand,
    ): KadreResult<SurfaceUpdateCommandOutcome> =
        KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false))

    /**
     * Fermeture du port (ordre AppKit l.600-608 adapté) : marquer fermé pour refuser toute
     * nouvelle admission. Il n'y a ni file de commandes ni drain asynchrone côté UIKit phase 1
     * — tout est déjà synchrone sur le thread principal.
     */
    fun beginClose() {
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
