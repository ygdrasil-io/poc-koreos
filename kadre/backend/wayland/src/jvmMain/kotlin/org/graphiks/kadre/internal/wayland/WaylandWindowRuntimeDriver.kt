package org.graphiks.kadre.internal.wayland

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
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
        // tranche 2 : aucun update natif — rejet explicite de la demande
        command.rejected(
            IllegalStateException("window updates are unsupported on Wayland phase 2"),
        )
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
