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

    internal fun didFinishLaunching() {
        if (launched || terminating) return
        launched = true
        active = true
        foreground = true
        eventLoop.handler.resumed(eventLoop)
        eventLoop.runLifecycleIteration(cause = StartCause.Init, callbacks = {
            recreateSurfaces {
                handler.canCreateSurfaces(this)
            }
        })
    }

    internal fun didBecomeActive() {
        if (!launched || terminating || active || !foreground) return
        active = true
        eventLoop.handler.resumed(eventLoop)
        eventLoop.runLifecycleIteration(cause = StartCause.WaitCancelled(), callbacks = {
            dispatchWindowFocused(gained = true)
            dispatchThemeChangedIfNeeded()
        })
    }

    internal fun willResignActive() {
        if (!launched || terminating || !active) return
        active = false
        eventLoop.runLifecycleIteration(cause = StartCause.WaitCancelled(), callbacks = {
            dispatchWindowFocused(gained = false)
            handler.suspended(this)
        })
    }

    internal fun didEnterBackground() {
        if (!launched || terminating || active || !foreground) return
        foreground = false
        eventLoop.runLifecycleIteration(cause = StartCause.WaitCancelled(), callbacks = {
            dispatchOccluded(occluded = true)
            destroySurfaces()
        })
    }

    internal fun willEnterForeground() {
        if (!launched || terminating || active || foreground) return
        foreground = true
        eventLoop.runLifecycleIteration(cause = StartCause.WaitCancelled(), callbacks = {
            dispatchOccluded(occluded = false)
            recreateSurfaces {
                handler.canCreateSurfaces(this)
            }
        })
    }

    internal fun willTerminate() {
        if (!launched || terminating) return
        terminating = true
        active = false
        foreground = false
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
        println("[KadreAppDelegate] applicationDidFinishLaunching → canCreateSurfaces")
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
        println("[KadreAppDelegate] applicationDidBecomeActive → resumed + Focused(true)")
        lifecycle?.didBecomeActive()
    }

    /**
     * The application is about to become inactive (incoming call, Control Center, backgrounding).
     *
     * Triggers [ApplicationHandler.suspended].
     */
    override fun applicationWillResignActive(application: UIApplication) {
        println("[KadreAppDelegate] applicationWillResignActive → Focused(false) + suspended")
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
        println("[KadreAppDelegate] applicationDidEnterBackground → Occluded(true) + destroySurfaces")
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
        println("[KadreAppDelegate] applicationWillEnterForeground → Occluded(false) + canCreateSurfaces")
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
        println("[KadreAppDelegate] applicationWillTerminate → Destroyed + destroySurfaces")
        runUIKitDelegateTermination(
            terminateLifecycle = { lifecycle?.willTerminate() },
            clearLifecycle = { lifecycle = null },
            clearRegistry = { KadreRegistry.handler = null },
        )
    }
}
