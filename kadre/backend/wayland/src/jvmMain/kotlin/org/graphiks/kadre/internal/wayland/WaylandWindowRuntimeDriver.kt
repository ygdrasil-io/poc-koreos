package org.graphiks.kadre.internal.wayland

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeWindowManager
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.WindowUpdateCommand
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationCommand
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseCommand
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationOutcome
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.window.WindowId


/** File d'actions marshalées vers le thread pompe (discipline thread Wayland). */
internal interface WindowActionQueue {
    fun enqueue(action: () -> Unit)
}

/**
 * Registre des serials réellement éligibles (D1 phase 5) : un serial
 * d'événement d'entrée réel n'est consommé qu'une fois — un move/resize
 * hors callback native (aucun serial) est refusé avant toute primitive.
 */
internal class WaylandSerialLedger {
    @Volatile
    private var latest: Int = -1

    /** Record d'un serial d'événement natif (pointer button, key, enter). */
    fun record(serial: Int) {
        if (serial >= 0) latest = serial
    }

    /** Consomme le serial éligible courant une seule fois ; null si épuisé. */
    fun consumeEligible(): Int? {
        val value = latest
        latest = -1
        return value.takeIf { it >= 0 }
    }

    /** Peek sans consommer (diagnostic). */
    fun peek(): Int? = latest.takeIf { it >= 0 }
}

/**
 * Composition root Wayland : un [RuntimeWindowManager] porté par le port
 * fenêtre. Le runtime reste l'autorité d'état (primary, révisions,
 * succession, arming de stopWhenLastWindowClosed) ; le backend ne tient que
 * les pairs natifs.
 */
internal class WaylandWindowRuntimeDriver private constructor(
    private val session: WaylandHostSession,
    resources: ResourceBudgetPolicy,
    failureReporter: RuntimeFailureReporter,
    publicWindowCapabilities: Boolean,
    onLastWindowClosed: (() -> Unit)?,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val peers = ConcurrentHashMap<WindowId, WaylandWindowPeer>()
    private val surfaceCommandPort = WaylandSurfaceCommandPort()
    internal val inputRouter = WaylandInputRouter()

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = resources,
        commandPort = WaylandWindowCommandPort(session, this),
        surfaceCommandPort = surfaceCommandPort,
        platform = KadrePlatform.Wayland,
        failureReporter = failureReporter,
        publicWindowCapabilities = publicWindowCapabilities,
        // propriétés initiales conformes tranche 2: titre, taille, resizable,
        // Borderless. decorations=System reste rejeté normativement par le
        // port (D1) même enabled ici (le runtime laisse passer le défaut).
        enabledWindowUpdateCapabilities = setOf(
            org.graphiks.kadre.window.WindowProperty.Title,
            org.graphiks.kadre.window.WindowProperty.ContentSize,
            org.graphiks.kadre.window.WindowProperty.Resizable,
            org.graphiks.kadre.window.WindowProperty.Decorations,
        ),
        onLastWindowClosed = onLastWindowClosed,
    )

    internal fun peer(windowId: WindowId): WaylandWindowPeer? = peers[windowId]

    internal fun registeredPeerCount(): Int = peers.size

    internal fun register(peer: WaylandWindowPeer, surfaceId: org.graphiks.kadre.surface.SurfaceId) {
        peers[peer.windowId] = peer
        surfaceCommandPort.register(surfaceId, peer)
        inputRouter.registerSurface(peer.window.nativeAddresses.second, surfaceId)
    }

    internal fun unregister(windowId: WindowId, surfaceId: org.graphiks.kadre.surface.SurfaceId? = null) {
        peers.remove(windowId)
        surfaceId?.let {
            surfaceCommandPort.unregister(it)
            peers[windowId]?.window?.let { window -> inputRouter.unregisterSurface(window.nativeAddresses.second) }
        }
    }

    internal fun createNativeWindow(width: Int, height: Int): WaylandNativeWindow =
        session.createWindowing(width, height)

    internal fun enqueueWindowAction(action: () -> Unit) {
        session.asActionQueue().enqueue(action)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        peers.values.forEach(WaylandWindowPeer::close)
        peers.clear()
        manager.close()
    }

    /**
     * Move interactif depuis une callback native : exige un serial
     * réellement éligible (consommé une fois). Hors callback native, rejet
     * déterministe sans émission (F1-1).
     */
    internal fun requestWindowMove(windowId: WindowId): KadreResult<Unit> {
        val serial = serialLedger.consumeEligible()
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("interaction-serial"))
        val peer = peer(windowId)
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        peer.window.beginMove(serial)
        return KadreResult.Success(Unit)
    }

    /** Resize interactif — mêmes règles de serial que [requestWindowMove]. */
    internal fun requestWindowResize(windowId: WindowId, edges: Int): KadreResult<Unit> {
        val serial = serialLedger.consumeEligible()
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("interaction-serial"))
        val peer = peer(windowId)
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        peer.window.beginResize(serial, edges)
        return KadreResult.Success(Unit)
    }

    internal val serialLedger = WaylandSerialLedger()

    /** Spec courant posé au commit (titre/contraints/mode), pour l'état publié. */
    internal fun currentSpecOf(windowId: WindowId): org.graphiks.kadre.window.WindowSpec? =
        appliedSpecs[windowId]

    /** État effectif publié après un update : champs demandés appliqués. */
    internal fun effectiveStateOf(
        windowId: WindowId,
        update: org.graphiks.kadre.window.WindowUpdate,
    ): org.graphiks.kadre.window.WindowState {
        val base = appliedSpecs[windowId] ?: org.graphiks.kadre.window.WindowSpec()
        fun <T> org.graphiks.kadre.surface.PropertyChange<T>?.or(base: T): T =
            (this as? org.graphiks.kadre.surface.PropertyChange.Set)?.value ?: base
        val spec = org.graphiks.kadre.window.WindowSpec(
            title = update.title.or(base.title),
            contentSize = update.contentSize.or(base.contentSize),
            minimumSize = update.minimumSize.or(base.minimumSize),
            maximumSize = update.maximumSize.or(base.maximumSize),
            outerPosition = base.outerPosition,
            resizable = update.resizable.or(base.resizable),
            fullscreen = update.fullscreen.or(base.fullscreen),
            decorations = base.decorations,
            systemButtons = base.systemButtons,
            level = base.level,
            transparent = base.transparent,
            blurBehind = base.blurBehind,
            icon = base.icon,
            contentProtection = base.contentProtection,
        )
        appliedSpecs[windowId] = spec
        return org.graphiks.kadre.window.WindowState(
            phase = org.graphiks.kadre.window.WindowPhase.Open,
            title = spec.title,
            outerBounds = null,
            contentSize = spec.contentSize,
            minimumSize = spec.minimumSize,
            maximumSize = spec.maximumSize,
            resizable = spec.resizable,
            fullscreen = spec.fullscreen,
            decorations = spec.decorations,
            systemButtons = spec.systemButtons,
            level = spec.level,
            transparent = spec.transparent,
            blurBehind = spec.blurBehind,
            icon = spec.icon,
            contentProtection = spec.contentProtection,
            revision = org.graphiks.kadre.window.WindowRevision(0L),
        )
    }

    private val appliedSpecs = java.util.concurrent.ConcurrentHashMap<WindowId, org.graphiks.kadre.window.WindowSpec>()

    internal fun recordAppliedSpec(windowId: WindowId, spec: org.graphiks.kadre.window.WindowSpec) {
        appliedSpecs[windowId] = spec
    }

    internal fun requestWindowClose(windowId: WindowId): OpenedWindowCloseOutcome {
        val peer = peer(windowId) ?: return OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted
        return peer.requestProgrammaticClose()
    }

    internal companion object {
        internal fun create(
            session: WaylandHostSession,
            resources: ResourceBudgetPolicy,
            failureReporter: RuntimeFailureReporter,
            publicWindowCapabilities: Boolean,
            onLastWindowClosed: (() -> Unit)?,
        ): WaylandWindowRuntimeDriver = WaylandWindowRuntimeDriver(
            session = session,
            resources = resources,
            failureReporter = failureReporter,
            publicWindowCapabilities = publicWindowCapabilities,
            onLastWindowClosed = onLastWindowClosed,
        )
    }
}

/** Port fenêtre: rejette les specs non conformes (D1) et marshalle les créations. */
internal class WaylandWindowCommandPort internal constructor(
    private val session: WaylandHostSession,
    private val driver: WaylandWindowRuntimeDriver,
) : WindowCommandPort {

    override fun requestOpen(command: WindowOpenCommand) {
        tranche2Rejection(command.spec)?.let { rejection ->
            command.fail(rejection)
            return
        }
        session.asActionQueue().enqueue { createWindow(command) }
    }

    override fun requestUpdate(command: WindowUpdateCommand) {
        // champs conformes tranche 5 : titre, contraintes, fullscreen
        // borderless ; min>max validé AVANT toute primitive (SN-1) ;
        // exclusive reste hors domaine (SN-2)
        val update = command.update
        val minimum = (update.minimumSize as? org.graphiks.kadre.surface.PropertyChange.Set)?.value
        val maximum = (update.maximumSize as? org.graphiks.kadre.surface.PropertyChange.Set)?.value
        if (minimum != null && maximum != null &&
            (minimum.width > maximum.width || minimum.height > maximum.height)
        ) {
            command.rejected(IllegalArgumentException("minimum exceeds maximum"))
            return
        }
        if ((update.fullscreen as? org.graphiks.kadre.surface.PropertyChange.Set)?.value
            is org.graphiks.kadre.window.FullscreenMode.Exclusive
        ) {
            command.rejected(IllegalArgumentException("exclusive fullscreen is outside the wayland domain"))
            return
        }
        session.asActionQueue().enqueue { applyUpdate(command) }
    }

    private fun applyUpdate(command: WindowUpdateCommand) {
        val update = command.update
        val peer = driver.peer(command.windowId) ?: run {
            command.rejected(IllegalStateException("window is closed"))
            return
        }
        try {
            (update.title as? org.graphiks.kadre.surface.PropertyChange.Set)?.value?.let { peer.window.setTitle(it) }
            val minSet = update.minimumSize as? org.graphiks.kadre.surface.PropertyChange.Set
            val maxSet = update.maximumSize as? org.graphiks.kadre.surface.PropertyChange.Set
            if (minSet != null || maxSet != null) {
                val current = driver.currentSpecOf(command.windowId)
                val min = minSet?.value ?: current?.minimumSize
                val max = maxSet?.value ?: current?.maximumSize
                peer.window.setConstraints(
                    min?.width?.toInt() ?: 0,
                    min?.height?.toInt() ?: 0,
                    max?.width?.toInt() ?: 0,
                    max?.height?.toInt() ?: 0,
                )
            }
            when ((update.fullscreen as? org.graphiks.kadre.surface.PropertyChange.Set)?.value) {
                org.graphiks.kadre.window.FullscreenMode.Borderless -> peer.window.setFullscreenBorderless(true)
                org.graphiks.kadre.window.FullscreenMode.Windowed -> peer.window.setFullscreenBorderless(false)
                else -> Unit
            }
            // l'état publié reflète les contraintes et le mode demandés ; la
            // géométrie effective reste autoritaire au configure suivant
            val state = driver.effectiveStateOf(command.windowId, update)
            command.applied(state)
        } catch (failure: Throwable) {
            command.failed(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "window-update", "update-failed"))
        }
    }

    // la création est synchrone dans l'action pompée: pas de cancellation
    // pendante observable côté Wayland en tranche 2
    override fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome = PendingWindowCancellationOutcome.TooLate

    override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
        val peer = driver.peer(command.windowId) ?: return OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted
        if (peer.isClosed()) {
            // fermeture déjà admise (compositor ou close programmatique en
            // course) : une seule terminaison, le stimulus nativeClosed a
            // déjà finalisé la fenêtre (SN-6)
            return OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted
        }
        // la destruction des proxies marshalée vers le thread pompe — aucun
        // proxy n'est touché hors thread pompe ; l'issue est rendue
        // immédiatement, la finalisation passe par le stimulus nativeClosed
        session.asActionQueue().enqueue { peer.requestProgrammaticClose() }
        return OpenedWindowCloseOutcome.Accepted
    }

    private fun createWindow(command: WindowOpenCommand) {
        try {
            val spec = command.spec
            val width = spec.contentSize.width.toInt().coerceAtLeast(1)
            val height = spec.contentSize.height.toInt().coerceAtLeast(1)
            val peer = WaylandWindowPeer.create(
                windowId = command.windowId,
                factory = driver::createNativeWindow,
                contentWidth = width,
                contentHeight = height,
                title = spec.title,
                appId = "org.graphiks.kadre.wayland",
                // le handshake vit sur le thread pompe : le roundtrip y
                // dispatche le configure attendu (aucune lecture préparée
                // n'est active pendant une action marshalée)
                dispatch = { session.bridge.roundtrip() },
                // le stimulus nativeClosed finalise la fenêtre côté runtime
                // (acceptNativeClose : fin de closing, membership, arrêt
                // stopWhenLastWindowClosed) ; les stimuli tardifs sont acceptés.
                onNativeClosed = { windowId ->
                    driver.unregister(windowId, command.surfaceId)
                    command.nativeClosed()
                },
            )
            driver.recordAppliedSpec(command.windowId, spec)
            driver.register(peer, command.surfaceId)
            command.commit(owner = peer, effectiveSpec = spec)
            peer.markCommitted()
        } catch (failure: Throwable) {
            driver.unregister(command.windowId)
            command.fail(KadreFailure.PlatformFailure(KadrePlatform.Wayland, "window-create", "creation-failed"))
        }
    }

    private fun tranche2Rejection(spec: org.graphiks.kadre.window.WindowSpec): KadreFailure? = when {
        spec.decorations == org.graphiks.kadre.window.WindowDecorations.System ->
            rejection("decorations-system")
        spec.outerPosition != null ->
            rejection("outer-position")
        spec.fullscreen != org.graphiks.kadre.window.FullscreenMode.Windowed ->
            rejection("fullscreen")
        spec.level != org.graphiks.kadre.window.WindowLevel.Normal ->
            rejection("level")
        spec.systemButtons != org.graphiks.kadre.window.WindowSystemButtons.All ->
            rejection("system-buttons")
        spec.transparent ->
            rejection("transparency")
        spec.blurBehind ->
            rejection("blur")
        spec.icon != null ->
            rejection("icon")
        spec.contentProtection ->
            rejection("content-protection")
        else -> null
    }

    private fun rejection(code: String): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Wayland, "window-create", code)
}
