package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult

import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeWindowManager
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationCommand
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationIntent
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationOutcome
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseCommand
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.CloseRequestRejectionOutcome
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.SurfaceCommandPort
import org.graphiks.kadre.internal.runtime.WindowUpdateCommand
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationCommand
import org.graphiks.kadre.internal.runtime.WindowUpdateCancellationOutcome
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.internal.runtime.SurfaceInitialSnapshot
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Private Win32 composition root for one windowed session (phase 2 tranche):
 * real top-level windows, `WM_CLOSE` close decisions, terminal ordering and
 * the desktop-handle lease. Window mutations, text input, fullscreen, raw
 * input and capture stay in their later phases — the runtime exposes the
 * exact `Unsupported`/rejected states for them.
 */
private fun win32SurfaceCapabilities(public: Boolean): org.graphiks.kadre.surface.SurfaceCapabilities {
    val unsupportedUpdate = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
    val unsupportedInteraction = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.InstallInteractionHandler))
    val unsupportedArm = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.ArmInteraction))
    return org.graphiks.kadre.surface.SurfaceCapabilities(
        cursor = unsupportedUpdate,
        customCursor = unsupportedUpdate,
        pointerCapture = unsupportedUpdate,
        hitTesting = unsupportedUpdate,
        inputDefaultBehavior = unsupportedUpdate,
        // Aucune interaction implémentée en phase 3 (déplacement = phase 5,
        // drop = phase 7) : la capability reste Unsupported.
        handlerInteractions = unsupportedInteraction,
        armedInteractions = unsupportedArm,
        platformAccess = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess)),
    )
}

/** Native message ids consumed by the driver's window handler. */
internal const val WM_CLOSE: Int = 0x0010
internal const val WM_DESTROY: Int = 0x0002
internal const val WM_SIZE: Int = 0x0005
internal const val WM_PAINT: Int = 0x000F
internal const val WM_SHOWWINDOW: Int = 0x0018
internal const val WM_ACTIVATE: Int = 0x0006
internal const val WM_DPICHANGED: Int = 0x02E0

internal class Win32WindowRuntimeDriver internal constructor(
    resources: ResourceBudgetPolicy,
    private val port: Win32NativeHostPort,
    failureReporter: RuntimeFailureReporter,
    onLastWindowClosed: (() -> Unit)?,
    enabledWindowUpdateCapabilities: Set<WindowProperty>,
) : AutoCloseable, WindowCommandPort, SurfaceCommandPort {
    private val closed = AtomicBoolean(false)
    private val entries = ConcurrentHashMap<org.graphiks.kadre.window.WindowRequestId, Win32WindowEntry>()
    private val maxWindows = resources.maxWindowsPerSession
    private val pendingRedraws = java.util.concurrent.ConcurrentHashMap<SurfaceId, java.util.ArrayDeque<SurfaceRedrawGeneration>>()
    @Volatile
    private var currentScale = 1.0

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = resources,
        commandPort = this,
        surfaceCommandPort = this,
        platform = KadrePlatform.Win32,
        failureReporter = failureReporter,
        publicWindowCapabilities = true,
        enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
        publicSurfaceCapabilities = true,
        enabledSurfaceCapabilities = win32SurfaceCapabilities(true),
        onLastWindowClosed = onLastWindowClosed,
    )

    override fun requestOpen(command: WindowOpenCommand) {
        if (closed.get()) {
            command.fail(KadreFailure.Closed(KadreResourceKind.Host))
            return
        }
        val window = try {
            port.createTopLevelWindow(
                title = command.spec.title,
                decorations = Win32WindowDecorations.System,
                width = command.spec.contentSize.width.toInt().coerceAtLeast(1),
                height = command.spec.contentSize.height.toInt().coerceAtLeast(1),
                messageHandler = { hwnd, message, wParam, lParam ->
                    when (message) {
                        WM_CLOSE -> command.closeRequested()
                        WM_DESTROY -> command.nativeClosed()
                        WM_SIZE -> {
                            val size = port.readClientPhysicalSize(hwnd)
                            val scale = port.readScaleFactor(hwnd)
                            val logical = org.graphiks.kadre.surface.LogicalSize(
                                size.first / scale,
                                size.second / scale,
                            )
                            val current = manager.state.value.windows
                                .firstOrNull { it.id == command.windowId }?.state?.value
                            val accepted = if (current != null) {
                                manager.acceptWindowGeometryObservation(
                                    command.windowId,
                                    current.copy(contentSize = logical),
                                )
                            } else {
                                false
                            }
                        }
                        WM_DPICHANGED -> {
                            val scale = port.readScaleFactor(hwnd)
                            val current = manager.state.value.windows
                                .firstOrNull { it.id == command.windowId }?.state?.value
                            if (current != null) {
                                val logical = org.graphiks.kadre.surface.LogicalSize(
                                    current.contentSize.width * currentScale / scale,
                                    current.contentSize.height * currentScale / scale,
                                )
                                manager.acceptWindowGeometryObservation(
                                    command.windowId,
                                    current.copy(contentSize = logical),
                                )
                            }
                        }
                        WM_ACTIVATE -> manager.acceptSurfaceStimulus(
                            org.graphiks.kadre.internal.runtime.SurfaceStimulus.FocusChanged(
                                surfaceId = command.surfaceId,
                                focus = if (wParam != 0L) org.graphiks.kadre.surface.SurfaceFocus.Focused
                                else org.graphiks.kadre.surface.SurfaceFocus.Unfocused,
                            ),
                        )
                        WM_SHOWWINDOW -> manager.acceptSurfaceStimulus(
                            org.graphiks.kadre.internal.runtime.SurfaceStimulus.VisibilityChanged(
                                surfaceId = command.surfaceId,
                                visibility = if (wParam != 0L) org.graphiks.kadre.surface.SurfaceVisibility.Visible
                                else org.graphiks.kadre.surface.SurfaceVisibility.Hidden,
                                occlusion = org.graphiks.kadre.surface.SurfaceOcclusion.Visible,
                            ),
                        )
                        WM_PAINT -> {
                            port.validateRect(hwnd)
                            val pending = pendingRedraws[command.surfaceId]?.pollFirst()
                            if (pending != null) {
                                manager.acceptSurfaceStimulus(
                                    org.graphiks.kadre.internal.runtime.SurfaceStimulus.RedrawConsumed(
                                        surfaceId = command.surfaceId,
                                        generation = pending,
                                    ),
                                )
                            }
                        }
                        else -> port.defWindowProc(hwnd, message, wParam, lParam)
                    }
                    0L
                },
            )
        } catch (failure: Throwable) {
            command.fail(
                KadreFailure.PlatformFailure(KadrePlatform.Win32, "open-window", "create-failed:${failure.message}"),
            )
            return
        }
        val entry = Win32WindowEntry(window, command)
        val existing = entries.putIfAbsent(command.requestId, entry)
        if (existing != null) {
            port.destroyWindow(window.hwnd)
            command.fail(KadreFailure.PlatformFailure(KadrePlatform.Win32, "open-window", "duplicate-request"))
            return
        }
        command.commit(
            owner = Win32PeerOwner(entry),
            initialSurfaceSnapshot = initialSnapshot(command.spec),
        )
    }

    private fun initialSnapshot(spec: WindowSpec): SurfaceInitialSnapshot {
        val logical = spec.contentSize
        return SurfaceInitialSnapshot(
            metrics = SurfaceMetrics(
                logicalSize = logical,
                physicalSize = PhysicalSize((logical.width * 1.0).toInt(), (logical.height * 1.0).toInt()),
                scaleFactor = 1.0,
                safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
            ),
            focus = SurfaceFocus.Unfocused,
            visibility = SurfaceVisibility.Hidden,
            occlusion = SurfaceOcclusion.Unknown,
            appearance = SurfaceAppearance(SurfaceTheme.Light, SurfaceContrast.Normal),
        )
    }

    override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
        val entry = entries[command.requestId]
            ?: return OpenedWindowCloseOutcome.PlatformFailure(
                KadreFailure.PlatformFailure(KadrePlatform.Win32, "close-window", "missing-peer"),
            )
        if (entry.closeAdmitted) {
            return OpenedWindowCloseOutcome.NativeCloseAlreadyCommitted
        }
        entry.closeAdmitted = true
        port.destroyWindow(entry.window.hwnd)
        // La destruction est synchrone et complète : le terminal natif est
        // publié immédiatement (le WM_DESTROY ne repasse plus par la route).
        entry.command.nativeClosed()
        return OpenedWindowCloseOutcome.Accepted
    }

    override fun closeRequestRejected(command: OpenedWindowCloseCommand): CloseRequestRejectionOutcome =
        CloseRequestRejectionOutcome.Rejected

    override fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome {
        val entry = entries[command.requestId] ?: return PendingWindowCancellationOutcome.TooLate
        if (entry.closeAdmitted) return PendingWindowCancellationOutcome.TooLate
        // Une fenêtre créée mais non remise au runtime est détruite : zéro
        // événement public, zéro fenêtre fantôme (roadmap phase 2).
        entries.remove(command.requestId)
        port.destroyWindow(entry.window.hwnd)
        return PendingWindowCancellationOutcome.CancelledBeforeCommit
    }

    override fun requestUpdate(command: WindowUpdateCommand) {
        command.rejected(IllegalStateException("Win32 window mutations arrive in phase 5"))
    }

    override fun requestUpdateCancellation(
        command: WindowUpdateCancellationCommand,
    ): WindowUpdateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate

    override fun requestRedraw(command: org.graphiks.kadre.internal.runtime.SurfaceRedrawCommand): KadreResult<Unit> {
        val entry = entries.values.firstOrNull { it.command.surfaceId == command.surfaceId }
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        if (entry.closeAdmitted || closed.get()) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        }
        // Coalescing natif : les invalidations rapprochées fusionnent dans la
        // file du message ; une seule génération (la dernière demandée) est
        // publiée consommée au drain.
        pendingRedraws.computeIfAbsent(command.surfaceId) { java.util.ArrayDeque() }.addLast(command.generation)
        port.invalidateRect(entry.window.hwnd)
        return KadreResult.Success(Unit)
    }

    override suspend fun apply(
        command: org.graphiks.kadre.internal.runtime.SurfaceUpdateCommand,
    ): KadreResult<org.graphiks.kadre.internal.runtime.SurfaceUpdateCommandOutcome> =
        KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = false))

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        entries.values.forEach { entry ->
            runCatching {
                port.destroyWindow(entry.window.hwnd)
            }
        }
        entries.clear()
    }

    private inner class Win32PeerOwner(
        private val entry: Win32WindowEntry,
    ) : WindowPeerOwner, org.graphiks.kadre.internal.runtime.RuntimeDesktopWindowHandleAccess {
        override fun close() {
            if (entries.remove(entry.command.requestId) != null) {
                port.destroyWindow(entry.window.hwnd)
                entry.command.nativeClosed()
            }
        }

        override suspend fun <R> withDesktopHandle(
            block: (RuntimeDesktopNativeWindowHandle) -> R,
        ): KadreResult<R> {
            if (entry.closeAdmitted || !entries.containsKey(entry.command.requestId)) {
                return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
            }
            return KadreResult.Success(
                port.onMainThread {
                    block(RuntimeDesktopNativeWindowHandle.Win32(entry.window.hwnd.toULong()))
                },
            )
        }
    }
}

internal class Win32WindowEntry(
    val window: Win32TopLevelWindowHandle,
    val command: WindowOpenCommand,
) {
    @Volatile
    var closeAdmitted = false

    /** Redraw generations requested but not yet drained by a WM_PAINT. */
    val pendingRedraws = java.util.ArrayDeque<SurfaceRedrawGeneration>()
}

internal object Win32WindowRuntimeDriverFactory {
    /** Tranche phase 2 : titre, taille initiale et décorations System. */
    val supportedWindowUpdateCapabilities: Set<WindowProperty> = setOf(
        WindowProperty.Title,
        WindowProperty.ContentSize,
        WindowProperty.Decorations,
    )

    fun create(
        port: Win32NativeHostPort,
        resources: ResourceBudgetPolicy,
        onLastWindowClosed: (() -> Unit)? = null,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        enabledWindowUpdateCapabilities: Set<WindowProperty> = supportedWindowUpdateCapabilities,
    ): Win32WindowRuntimeDriver = Win32WindowRuntimeDriver(
        resources = resources,
        port = port,
        failureReporter = failureReporter,
        onLastWindowClosed = onLastWindowClosed,
        enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
    )
}
