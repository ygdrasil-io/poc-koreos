package org.graphiks.kadre.uikit

import org.graphiks.kadre.core.ApplicationHandler
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExportObjCClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIResponder

/** Owns the production ordering shared by delegate callbacks and lifecycle tests. */
internal class UIKitLifecycleOrchestrator(
    private val eventLoop: UIKitActiveEventLoop,
) {
    internal fun didFinishLaunching() {
        eventLoop.recreateSurfaces {
            handler.canCreateSurfaces(this)
        }
    }

    internal fun didBecomeActive() {
        eventLoop.handler.resumed(eventLoop)
        eventLoop.dispatchWindowFocused(gained = true)
        eventLoop.dispatchThemeChangedIfNeeded()
    }

    internal fun willResignActive() {
        eventLoop.dispatchWindowFocused(gained = false)
        eventLoop.handler.suspended(eventLoop)
    }

    internal fun didEnterBackground() {
        eventLoop.dispatchOccluded(occluded = true)
        eventLoop.destroySurfaces()
    }

    internal fun willEnterForeground() {
        eventLoop.dispatchOccluded(occluded = false)
        eventLoop.recreateSurfaces {
            handler.canCreateSurfaces(this)
        }
    }

    internal fun willTerminate() {
        eventLoop.dispatchWindowsDestroyed()
        eventLoop.destroySurfaces()
    }
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
 * application(_:didFinishLaunchingWithOptions:) → canCreateSurfaces
 * applicationDidBecomeActive                   → resumed
 * ```
 *
 * ### Screen lock / short interruption (call, Control Center)
 * ```
 * applicationWillResignActive  → suspended
 * applicationDidBecomeActive   → resumed       (unlock / return)
 * ```
 *
 * ### Full backgrounding (Home button, App Switcher)
 * ```
 * applicationWillResignActive  → suspended + Focused(false)
 * applicationDidEnterBackground → Occluded(true) + destroySurfaces
 * applicationWillEnterForeground → Occluded(false) + canCreateSurfaces
 * applicationDidBecomeActive   → resumed + Focused(true)
 * ```
 *
 * ### Termination
 * ```
 * applicationWillTerminate     → destroySurfaces (if not already called)
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
        lifecycle?.willTerminate()
        lifecycle = null
        KadreRegistry.handler = null
    }
}
