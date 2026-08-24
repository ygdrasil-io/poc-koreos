package org.graphiks.kadre.uikit

import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.StartCause
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExportObjCClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIResponder

/** Owns the production ordering shared by delegate callbacks and lifecycle tests. */
internal class UIKitLifecycleOrchestrator(
    private val eventLoop: UIKitActiveEventLoop,
) {
    private var launched = false
    private var active = false
    private var foreground = false
    private var terminating = false
    private var deliveringTransitions = false
    private val pendingTransitions = mutableListOf<() -> Unit>()

    internal fun didFinishLaunching() {
        if (launched || terminating) return
        launched = true
        active = true
        foreground = true
        submitTransition {
            runNonTerminalIteration(
                cause = StartCause.Init,
                beforeEvents = { eventLoop.handler.resumed(eventLoop) },
            ) {
                recreateSurfaces {
                    handler.canCreateSurfaces(this)
                }
            }
        }
    }

    internal fun didBecomeActive() {
        if (!launched || terminating || active || !foreground) return
        active = true
        submitTransition {
            runNonTerminalIteration(
                cause = StartCause.WaitCancelled(),
                beforeEvents = { eventLoop.handler.resumed(eventLoop) },
            ) {
                runNonTerminalStages(
                    { dispatchWindowFocused(gained = true) },
                    this::dispatchThemeChangedIfNeeded,
                )
            }
        }
    }

    internal fun willResignActive() {
        if (!launched || terminating || !active) return
        active = false
        submitTransition {
            runNonTerminalIteration(cause = StartCause.WaitCancelled()) {
                runNonTerminalStages(
                    { dispatchWindowFocused(gained = false) },
                    { handler.suspended(this) },
                )
            }
        }
    }

    internal fun didEnterBackground() {
        if (!launched || terminating || active || !foreground) return
        foreground = false
        submitTransition {
            runNonTerminalIteration(cause = StartCause.WaitCancelled()) {
                runNonTerminalStages(
                    { dispatchOccluded(occluded = true) },
                    this::destroySurfaces,
                )
            }
        }
    }

    internal fun willEnterForeground() {
        if (!launched || terminating || active || foreground) return
        foreground = true
        submitTransition {
            runNonTerminalIteration(cause = StartCause.WaitCancelled()) {
                runNonTerminalStages(
                    { dispatchOccluded(occluded = false) },
                    {
                        recreateSurfaces {
                            handler.canCreateSurfaces(this)
                        }
                    },
                )
            }
        }
    }

    internal fun willTerminate() {
        if (!launched || terminating) return
        terminating = true
        active = false
        foreground = false
        eventLoop.requestTerminalTeardown()
        submitTransition(terminal = true) {
            eventLoop.runLifecycleIteration(
                cause = StartCause.WaitCancelled(),
                callbacks = {
                    runAllUIKitCleanupStages(
                        this::destroySurfaces,
                        this::dispatchWindowsDestroyed,
                        { handler.suspended(this) },
                    )
                },
                afterAboutToWait = eventLoop::exit,
            )
        }
    }

    private fun runNonTerminalIteration(
        cause: StartCause,
        beforeEvents: () -> Unit = {},
        callbacks: UIKitActiveEventLoop.() -> Unit,
    ) {
        eventLoop.runLifecycleIteration(
            cause = cause,
            callbacks = callbacks,
            beforeEvents = beforeEvents,
            shouldRunStage = { !terminating },
        )
    }

    private fun runNonTerminalStages(vararg stages: () -> Unit) {
        var firstFailure: Throwable? = null
        stages.forEach { stage ->
            if (terminating) return@forEach
            try {
                stage()
            } catch (failure: Throwable) {
                val primary = firstFailure
                if (primary == null) {
                    firstFailure = failure
                } else if (primary !== failure) {
                    primary.addSuppressed(failure)
                }
            }
        }
        firstFailure?.let { throw it }
    }

    private fun submitTransition(terminal: Boolean = false, transition: () -> Unit) {
        if (terminal) pendingTransitions.clear()
        pendingTransitions += transition
        if (deliveringTransitions) return

        deliveringTransitions = true
        var firstFailure: Throwable? = null
        try {
            while (pendingTransitions.isNotEmpty()) {
                val next = pendingTransitions.removeAt(0)
                try {
                    next()
                } catch (failure: Throwable) {
                    val primary = firstFailure
                    if (primary == null) {
                        firstFailure = failure
                    } else if (primary !== failure) {
                        primary.addSuppressed(failure)
                    }
                }
            }
        } finally {
            deliveringTransitions = false
        }
        firstFailure?.let { throw it }
    }
}

/** Completes delegate-owned terminal cleanup before propagating any failure. */
internal fun runUIKitDelegateTermination(
    terminateLifecycle: () -> Unit,
    clearLifecycle: () -> Unit,
    clearRegistry: () -> Unit,
) {
    runAllUIKitCleanupStages(
        terminateLifecycle,
        clearLifecycle,
        clearRegistry,
    )
}

/**
 * Kadre AppDelegate for iOS.
 *
 * Declared `@ExportObjCClass` to be visible to the Objective-C runtime
 * (required for `UIApplicationMain`). The handler is injected via [KadreRegistry]
 * before the application starts.
 *
 * ## Strict order of UIKit callbacks
 *
 * ### Startup
 * ```
 * application(_:didFinishLaunchingWithOptions:) → resumed → newEvents(Init)
 *                                               → canCreateSurfaces → aboutToWait
 * ```
 *
 * ### Screen lock / short interruption (call, Control Center)
 * ```
 * applicationWillResignActive  → newEvents(WaitCancelled) → Focused(false) → suspended → aboutToWait
 * applicationDidBecomeActive   → resumed → newEvents(WaitCancelled) → Focused(true) → aboutToWait
 * ```
 *
 * ### Full backgrounding (Home button, App Switcher)
 * ```
 * applicationWillResignActive    → newEvents(WaitCancelled) → Focused(false) → suspended → aboutToWait
 * applicationDidEnterBackground  → newEvents(WaitCancelled) → Occluded(true) → destroySurfaces → aboutToWait
 * applicationWillEnterForeground → newEvents(WaitCancelled) → Occluded(false) → canCreateSurfaces → aboutToWait
 * applicationDidBecomeActive     → resumed → newEvents(WaitCancelled) → Focused(true) → aboutToWait
 * ```
 *
 * ### Termination
 * ```
 * applicationWillTerminate → newEvents(WaitCancelled) → destroySurfaces → Destroyed
 *                          → suspended → aboutToWait → exit()
 * ```
 *
 * ## Two parallel signalling channels
 * Each activation callback drives **two** things on purpose:
 * 1. an app-level [ApplicationHandler] lifecycle call (`resumed` / `suspended` /
 *    `destroySurfaces`) — coarse, process-scoped;
 * 2. a per-window [WindowEvent] (`Focused` / `Occluded` / `Destroyed`) via
 *    [UIKitActiveEventLoop.dispatchWindowFocused] / [dispatchOccluded] / [dispatchWindowsDestroyed].
 *
 * The [WindowEvent] channel exists for parity with the desktop/winit backends so
 * that a consumer which only switches on [WindowEvent] still observes focus and
 * destruction. On the single-window AppDelegate model, app activation and window
 * focus coincide, so both are emitted from the same callback.
 *
 * ## M3 decision
 * AppDelegate-only (no `UISceneDelegate`) — avoids `UISceneConfiguration`/Info.plist.
 * Scene-based considered post-V1 if iOS multi-window is required.
 */
@OptIn(BetaInteropApi::class)
@ExportObjCClass
class KadreAppDelegate : UIResponder(), UIApplicationDelegateProtocol {

    private var lifecycle: UIKitLifecycleOrchestrator? = null

    // ── Startup ─────────────────────────────────────────────────────────────

    /**
     * Application entry point.
     *
     * Retrieves the handler from [KadreRegistry], creates the [UIKitActiveEventLoop]
     * and triggers [ApplicationHandler.canCreateSurfaces].
     */
    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?,
    ): Boolean {
        println("[KadreAppDelegate] applicationDidFinishLaunching → resumed + newEvents(Init) + canCreateSurfaces + aboutToWait")
        val handler = KadreRegistry.handler
            ?: error("[KadreAppDelegate] No handler registered — call startKadreApplication before UIApplicationMain")
        val orchestrator = UIKitLifecycleOrchestrator(UIKitActiveEventLoop(handler))
        lifecycle = orchestrator
        orchestrator.didFinishLaunching()
        return true
    }

    // ── Active / Inactive ───────────────────────────────────────────────────────

    /**
     * The application becomes active (foreground, keyboard focus).
     *
     * Triggers [ApplicationHandler.resumed].
     */
    override fun applicationDidBecomeActive(application: UIApplication) {
        println("[KadreAppDelegate] applicationDidBecomeActive → resumed + newEvents(WaitCancelled) + Focused(true) + aboutToWait")
        lifecycle?.didBecomeActive()
    }

    /**
     * The application is about to become inactive (incoming call, Control Center, backgrounding).
     *
     * Triggers [ApplicationHandler.suspended].
     */
    override fun applicationWillResignActive(application: UIApplication) {
        println("[KadreAppDelegate] applicationWillResignActive → newEvents(WaitCancelled) + Focused(false) + suspended + aboutToWait")
        lifecycle?.willResignActive()
    }

    // ── Background / Foreground ───────────────────────────────────────────

    /**
     * The application has moved fully to the background (Home, App Switcher).
     *
     * Triggers [WindowEvent.Occluded]\(true) for all windows, then
     * [ApplicationHandler.destroySurfaces] to let the app release GPU resources
     * before the process is fully suspended.
     *
     * Note: called AFTER [applicationWillResignActive] → [ApplicationHandler.suspended].
     */
    override fun applicationDidEnterBackground(application: UIApplication) {
        println("[KadreAppDelegate] applicationDidEnterBackground → newEvents(WaitCancelled) + Occluded(true) + destroySurfaces + aboutToWait")
        lifecycle?.didEnterBackground()
    }

    /**
     * The application is about to return to the foreground (from App Switcher or app return).
     *
     * Triggers [WindowEvent.Occluded]\(false) for all windows, then
     * [ApplicationHandler.canCreateSurfaces] to allow re-initialization of GPU
     * surfaces.
     *
     * Note: called BEFORE [applicationDidBecomeActive] → [ApplicationHandler.resumed].
     */
    override fun applicationWillEnterForeground(application: UIApplication) {
        println("[KadreAppDelegate] applicationWillEnterForeground → newEvents(WaitCancelled) + Occluded(false) + canCreateSurfaces + aboutToWait")
        lifecycle?.willEnterForeground()
    }

    // ── Termination ───────────────────────────────────────────────────────────

    /**
     * The application is about to be terminated by the system.
     *
     * Triggers [ApplicationHandler.destroySurfaces] (if not already called from
     * [applicationDidEnterBackground]) then cleans up the registry.
     */
    override fun applicationWillTerminate(application: UIApplication) {
        println("[KadreAppDelegate] applicationWillTerminate → newEvents(WaitCancelled) + destroySurfaces + Destroyed + suspended + aboutToWait + exit")
        runUIKitDelegateTermination(
            terminateLifecycle = { lifecycle?.willTerminate() },
            clearLifecycle = { lifecycle = null },
            clearRegistry = { KadreRegistry.handler = null },
        )
    }
}
