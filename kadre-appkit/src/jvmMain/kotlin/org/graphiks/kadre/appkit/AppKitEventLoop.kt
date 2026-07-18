/**
 * AppKit implementation of [ActiveEventLoop] and the [runApp] entry point.
 *
 * [AppKitEventLoop] implements [ActiveEventLoop] and is passed to each
 * [ApplicationHandler] callback. The top-level [runApp] function orchestrates
 * AppKit initialization (KadreApplication + KadreAppDelegate + NSApp.run).
 *
 * GRA-128: first complete wiring — M1.
 * GRA-136: effective ControlFlow + CFRunLoopObserver + thread-safe proxy.
 */
package org.graphiks.kadre.appkit

import org.graphiks.kffi.objc.NSApplicationActivationPolicy
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.CursorImage
import org.graphiks.kadre.core.CustomCursor
import org.graphiks.kadre.core.DeviceEvents
import org.graphiks.kadre.core.EventLoopProxy
import org.graphiks.kadre.core.MonitorHandle
import org.graphiks.kadre.core.OwnedDisplayHandle
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowId
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Global lock guaranteeing that only a single AppKit event loop is active
 * at a time within the process. DoD #3.
 *
 * Uses [java.util.concurrent.atomic.AtomicBoolean] for thread-safety:
 * [runApp] performs an atomic CAS false→true at startup and raises
 * [IllegalStateException] if the value was already true.
 */
internal val appKitRunning = java.util.concurrent.atomic.AtomicBoolean(false)

private fun terminateAppKitApplication() {
    val nsAppClass = ObjCRuntime.getClass("NSApplication")
    val nsApp = ObjCRuntime.msgSend(
        ValueLayout.ADDRESS,
        nsAppClass,
        ObjCRuntime.sel("sharedApplication"),
    ) as MemorySegment
    ObjCRuntime.msgSend(null, nsApp, ObjCRuntime.sel("terminate:"), MemorySegment.NULL)
}

/**
 * Internal implementation of [ActiveEventLoop] for the AppKit platform (macOS).
 *
 * One instance is created per call to [runApp] and passed as the receiver
 * to all [ApplicationHandler] callbacks.
 *
 * M1 scope:
 * - [createWindow]: creates an [AppKitWindow] and installs a
 *   [KadreWindowDelegate] on it for close handling.
 * - [exit]: raises the [isExiting] flag then triggers
 *   `[NSApp terminate:nil]` to quit the AppKit loop.
 * - [controlFlow] / [setControlFlow]: state driven by [CFRunLoopOwner] (GRA-136).
 * - [createProxy]: implemented via [AppKitEventLoopProxy] (GRA-136) — thread-safe wakeUp.
 */
internal class AppKitEventLoop(
    internal val handler: ApplicationHandler,
    terminateApplication: () -> Unit = {},
) : ActiveEventLoop {

    private data class WindowCloseActions(
        val unregisterCallbacks: () -> Unit,
        val sendNativeClose: () -> Unit,
        val releaseNativeResources: () -> Unit,
        val releaseDelegate: () -> Unit,
    )

    private enum class TerminationState {
        RUNNING,
        EXIT_REQUESTED,
        TERMINATING,
        TERMINATED,
    }

    /** Live windows: windowId → AppKitWindow. */
    internal val windows = ConcurrentHashMap<Long, AppKitWindow>()
    private val windowCloseActions = ConcurrentHashMap<Long, WindowCloseActions>()

    @Volatile
    private var _isExiting = false

    override val isExiting: Boolean
        get() = _isExiting

    @Volatile
    private var _controlFlow: ControlFlow = ControlFlow.Wait

    override val controlFlow: ControlFlow
        get() = _controlFlow

    @Volatile
    private var runLoopOwner: CFRunLoopOwner? = null

    private var didLaunch = false
    private var isActive = false
    private var terminationState = TerminationState.RUNNING
    private val callbackFailures = ConcurrentLinkedQueue<Throwable>()
    private val deferredNativeCallbackCleanup = ConcurrentLinkedQueue<() -> Unit>()

    @Volatile
    private var terminateApplication: () -> Unit = terminateApplication

    internal fun didLaunch() {
        synchronized(this) {
            if (didLaunch) return
            didLaunch = true
            isActive = true
        }
        handler.resumed(this)
        handler.newEvents(this, StartCause.Init)
        handler.canCreateSurfaces(this)
    }

    internal fun didBecomeActive() {
        synchronized(this) {
            if (!didLaunch || isActive) return
            isActive = true
        }
        handler.resumed(this)
    }

    internal fun willResignActive() {
        synchronized(this) {
            if (!isActive) return
            isActive = false
        }
        handler.suspended(this)
    }

    internal fun willTerminate(closeWindows: () -> Unit = ::closeRemainingWindows) {
        if (deferWhileNativeCallbackActive { willTerminate(closeWindows) }) {
            noteApplicationWillTerminate()
            return
        }
        AppKitNativeCallbackBoundary.runExclusive {
            completeTermination(closeWindows)
        }
    }

    private fun completeTermination(closeWindows: () -> Unit) {
        synchronized(this) {
            _isExiting = true
            if (terminationState == TerminationState.TERMINATING ||
                terminationState == TerminationState.TERMINATED
            ) return
            terminationState = TerminationState.TERMINATING
        }
        var failure: Throwable? = null
        failure = appKitCleanupStep(failure) { handler.destroySurfaces(this) }
        failure = appKitCleanupStep(failure, closeWindows)
        failure = appKitCleanupStep(failure) { handler.suspended(this) }
        synchronized(this) {
            isActive = false
            terminationState = TerminationState.TERMINATED
        }
        failure?.let { throw it }
    }

    internal fun noteApplicationWillTerminate() {
        markExitRequested()
    }

    private fun closeRemainingWindows() {
        var failure: Throwable? = null
        while (true) {
            val pendingWindowIds = windowCloseActions.keys.toList()
            if (pendingWindowIds.isEmpty()) break
            pendingWindowIds.forEach {
                failure = appKitCleanupStep(failure) { closeWindow(WindowId(it)) }
            }
        }
        failure?.let { throw it }
    }

    internal fun registerWindowCloseActions(
        windowId: WindowId,
        unregisterCallbacks: () -> Unit,
        closeNative: () -> Unit,
    ) = registerWindowCloseActions(
        windowId = windowId,
        unregisterCallbacks = unregisterCallbacks,
        sendNativeClose = closeNative,
        releaseNativeResources = {},
        releaseDelegate = {},
    )

    internal fun registerWindowCloseActions(
        windowId: WindowId,
        unregisterCallbacks: () -> Unit,
        sendNativeClose: () -> Unit,
        releaseNativeResources: () -> Unit,
        releaseDelegate: () -> Unit,
    ) {
        windowCloseActions[windowId.value] = WindowCloseActions(
            unregisterCallbacks,
            sendNativeClose,
            releaseNativeResources,
            releaseDelegate,
        )
    }

    internal fun hasRegisteredWindow(windowId: WindowId): Boolean =
        windowCloseActions.containsKey(windowId.value)

    internal fun unregisterWindowCloseActions(windowId: WindowId) {
        windowCloseActions.remove(windowId.value)
    }

    internal fun closeWindow(windowId: WindowId) {
        if (deferWhileNativeCallbackActive { closeWindow(windowId) }) return
        AppKitNativeCallbackBoundary.runExclusive {
            closeWindow(windowId, nativeConfirmation = false)
        }
    }

    internal fun confirmWindowClosed(windowId: WindowId) {
        closeWindow(windowId, nativeConfirmation = true)
    }

    private fun closeWindow(windowId: WindowId, nativeConfirmation: Boolean) {
        val actions = windowCloseActions.remove(windowId.value) ?: return
        windows.remove(windowId.value)
        var failure: Throwable? = null
        failure = appKitCleanupStep(failure) { runLoopOwner?.closeWindow(windowId) }
        failure = appKitCleanupStep(failure, actions.unregisterCallbacks)
        failure = appKitCleanupStep(failure) {
            handler.windowEvent(this, windowId, org.graphiks.kadre.core.WindowEvent.Destroyed)
        }
        if (!nativeConfirmation) {
            failure = appKitCleanupStep(failure, actions.sendNativeClose)
        }
        if (nativeConfirmation) {
            deferredNativeCallbackCleanup.add {
                AppKitNativeCallbackBoundary.awaitQuiescence()
                var deferredFailure: Throwable? = null
                deferredFailure = appKitCleanupStep(deferredFailure, actions.releaseNativeResources)
                deferredFailure = appKitCleanupStep(deferredFailure, actions.releaseDelegate)
                deferredFailure?.let { throw it }
            }
            failure = appKitCleanupStep(failure) { runLoopOwner?.wakeUp() }
        } else {
            failure = appKitCleanupStep(failure) { AppKitNativeCallbackBoundary.awaitQuiescence() }
            failure = appKitCleanupStep(failure, actions.releaseNativeResources)
            failure = appKitCleanupStep(failure, actions.releaseDelegate)
        }
        failure?.let { throw it }
    }

    internal fun drainDeferredNativeCallbackCleanup() {
        AppKitNativeCallbackBoundary.runExclusive {
            var failure: Throwable? = null
            while (true) {
                val cleanup = deferredNativeCallbackCleanup.poll() ?: break
                failure = appKitCleanupStep(failure, cleanup)
            }
            failure?.let { recordCallbackFailure("deferredNativeCallbackCleanup", it) }
        }
    }

    private fun deferWhileNativeCallbackActive(action: () -> Unit): Boolean {
        if (!AppKitNativeCallbackBoundary.hasActiveCallbacks) return false
        deferredNativeCallbackCleanup.add(action)
        try {
            runLoopOwner?.wakeUp()
        } catch (wakeFailure: Throwable) {
            recordCallbackFailure("deferredNativeCallbackWake", wakeFailure)
        }
        return true
    }

    internal fun recordCallbackFailure(context: String, failure: Throwable) {
        val contextualFailure = IllegalStateException("AppKit callback $context failed", failure)
        callbackFailures.add(contextualFailure)
        if (markExitRequested()) {
            deferredNativeCallbackCleanup.add {
                try {
                    terminateApplication()
                } catch (terminationFailure: Throwable) {
                    if (terminationFailure !== contextualFailure) contextualFailure.addSuppressed(terminationFailure)
                    throw terminationFailure
                }
            }
        }
        try {
            runLoopOwner?.wakeUp()
        } catch (wakeFailure: Throwable) {
            if (wakeFailure !== contextualFailure) contextualFailure.addSuppressed(wakeFailure)
        }
    }

    internal fun throwPendingCallbackFailure() {
        drainPendingCallbackFailure()?.let { throw it }
    }

    internal fun suppressPendingCallbackFailureOnto(primary: Throwable) {
        val callbackFailure = drainPendingCallbackFailure() ?: return
        if (callbackFailure !== primary) primary.addSuppressed(callbackFailure)
    }

    private fun drainPendingCallbackFailure(): Throwable? {
        val primary = callbackFailures.poll() ?: return null
        while (true) {
            val additional = callbackFailures.poll() ?: break
            if (additional !== primary) primary.addSuppressed(additional)
        }
        return primary
    }

    override fun setControlFlow(controlFlow: ControlFlow) {
        _controlFlow = controlFlow
    }

    /**
     * Creates a new AppKit window and installs the close delegate.
     *
     * Must be called from the main thread (validated by [AppKitWindow.init]).
     */
    override fun createWindow(attributes: WindowAttributes): Window {
        check(!_isExiting) { "Cannot create an AppKit window while the event loop is terminating" }
        val window = AppKitWindow(attributes)
        window.setWindowDelegate(handler, this)
        windows[window.id.value] = window
        return window
    }

    /**
     * Creates a window with AppKit-specific attributes.
     *
     * Reuses the core [createWindow] and applies macOS-specific settings
     * such as activation policy, tabbing identifier, shadow, titlebar
     * transparency, and more.
     */
    fun createWindow(attrs: AppKitWindowAttributes): Window {
        val window = createWindow(attrs.core) as AppKitWindow

        // Apply activation policy (application-level, set once)
        attrs.activationPolicy?.let { policy ->
            val nsApp = objcSharedApplication()
            ObjCRuntime.msgSend(
                ValueLayout.JAVA_BOOLEAN,
                nsApp,
                ObjCRuntime.sel("setActivationPolicy:"),
                NSApplicationActivationPolicy.fromValue(policy.toAppKitValue()),
            )
        }

        // Apply AppKit-specific window settings
        attrs.hasShadow?.let { window.setHasShadow(it) }
        attrs.tabbingIdentifier?.let { window.setTabbingIdentifier(it) }
        if (attrs.titlebarTransparent) window.setTitlebarTransparent(true)
        if (attrs.titleHidden) window.setTitleHidden(true)
        if (attrs.titlebarHidden) window.setTitlebarHidden(true)
        if (attrs.fullSizeContentView) window.setFullSizeContentView(true)
        if (attrs.movableByWindowBackground) window.setMovableByWindowBackground(true)

        return window
    }

    /**
     * Requests shutdown of the AppKit event loop.
     *
     * Raises [isExiting] then calls `[NSApp terminate:nil]`, which triggers
     * `applicationShouldTerminate:` in [KadreAppDelegate] — which returns
     * `NSTerminateNow` because [isExiting] is already true.
     */
    override fun exit() {
        if (!markExitRequested()) return
        if (AppKitNativeCallbackBoundary.isInCallback) {
            deferredNativeCallbackCleanup.add(terminateApplication)
            runLoopOwner?.wakeUp()
        } else {
            terminateApplication()
        }
    }

    private fun markExitRequested(): Boolean = synchronized(this) {
        _isExiting = true
        if (terminationState != TerminationState.RUNNING) {
            false
        } else {
            terminationState = TerminationState.EXIT_REQUESTED
            true
        }
    }

    /**
     * Creates an [EventLoopProxy] whose [EventLoopProxy.wakeUp] is thread-safe
     * (GRA-136). The proxy delegates to the closeable owner installed for this
     * exact loop before `NSApp.run`, so wake state and native wake-up stay paired.
     */
    override fun createProxy(): EventLoopProxy = AppKitEventLoopProxy.create(
        checkNotNull(runLoopOwner) {
            "AppKit run-loop owner must be installed before createProxy()"
        },
    )

    internal fun installRunLoopOwner(owner: CFRunLoopOwner) {
        synchronized(this) {
            check(runLoopOwner == null) { "AppKit run-loop owner is already installed" }
            runLoopOwner = owner
        }
    }

    internal fun clearRunLoopOwner(owner: CFRunLoopOwner) {
        synchronized(this) {
            if (runLoopOwner === owner) runLoopOwner = null
        }
    }

    internal fun installTerminationRequest(requestTermination: () -> Unit) {
        terminateApplication = requestTermination
    }

    // ── Task 29: ownedDisplayHandle ─────────────────────────────────────────────

    /**
     * Returns an [OwnedDisplayHandle] wrapping [RawDisplayHandle.AppKit].
     */
    override fun ownedDisplayHandle(): OwnedDisplayHandle? =
        OwnedDisplayHandle(RawDisplayHandle.AppKit)

    // ── R2: monitor enumeration ───────────────────────────────────────────────

    /**
     * Returns all connected screens via NSScreen.screens.
     *
     * Falls back to an empty list if the AppKit bindings are unavailable
     * (e.g. non-macOS or headless CI).
     */
    override fun availableMonitors(): List<MonitorHandle> =
        AppKitMonitorHandle.allScreens()

    /**
     * Returns the primary monitor via NSScreen.mainScreen, or null if unavailable.
     */
    override fun primaryMonitor(): MonitorHandle? =
        AppKitMonitorHandle.primaryScreen()

    // ── R3: system theme ──────────────────────────────────────────────────────

    /**
     * Returns the current system-wide theme by reading `NSApp.effectiveAppearance`.
     *
     * Returns null if AppKit is not available or the call fails.
     */
    override fun systemTheme(): Theme? = try {
        val nsAppClass = ObjCRuntime.getClass("NSApplication")
        val nsApp = ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            nsAppClass,
            ObjCRuntime.sel("sharedApplication"),
        ) as MemorySegment
        AppKitThemeHelper.effectiveTheme(nsApp)
    } catch (_: Throwable) { null }

    // ── R4: device event filter ───────────────────────────────────────────────

    /**
     * No-op on AppKit: device events are dispatched independently of focus.
     *
     * AppKit dispatches raw device events (PointerMotion, Button, Key) via
     * `sendEvent:` which fires regardless of window focus. A proper filter
     * would require an NSEvent global monitor; out of scope for R4.
     */
    override fun listenDeviceEvents(mode: DeviceEvents) {
        // no-op on AppKit: all device events are always dispatched
    }

    // ── R5-CustomCursor ─────────────────────────────────────────────────────────

    /**
     * Creates a custom cursor from RGBA pixel data on AppKit.
     *
     * Delegates to [AppKitCursorHelper.createNSCursorFromImage] which uses
     * CoreGraphics to create a CGImage, then wraps it in NSImage → NSCursor.
     */
    override fun createCustomCursor(image: CursorImage): CustomCursor? {
        val nsCursor = AppKitCursorHelper.createNSCursorFromImage(image) ?: return null
        return CustomCursor(id = nsCursor.address())
    }
}

internal interface AppKitRunAppOperations {
    fun requestTermination() = Unit
    fun requireMainThread()
    fun initialize()
    fun attachApplicationDelegate()
    fun installRunLoopOwner()
    fun run()
    fun throwPendingCallbackFailure()
    fun suppressPendingCallbackFailureOnto(primary: Throwable)
    fun closeRunLoopOwner()
    fun detachApplicationDelegate()
    fun releaseApplicationDelegate()
    fun clearApplicationReferences()
}

private class NativeAppKitRunAppOperations(
    private val eventLoop: AppKitEventLoop,
) : AppKitRunAppOperations {
    private var app: KadreApplication? = null
    private var appDelegate: KadreAppDelegate? = null
    private var runLoopOwner: CFRunLoopOwner? = null

    override fun requestTermination() {
        terminateAppKitApplication()
    }

    override fun requireMainThread() {
        MainThreadCheck.require()
    }

    override fun initialize() {
        val initializedApp = KadreApplication.initialize()
        app = initializedApp
        initializedApp.eventLoop = eventLoop
        initializedApp.setActivationPolicyRegular()
    }

    override fun attachApplicationDelegate() {
        val delegate = KadreAppDelegate(eventLoop.handler, eventLoop)
        appDelegate = delegate
        checkNotNull(app).setDelegate(delegate.ptr)
    }

    override fun installRunLoopOwner() {
        val owner = CFRunLoopOwner.install(eventLoop.handler, eventLoop, eventLoop.windows)
        runLoopOwner = owner
        eventLoop.installRunLoopOwner(owner)
    }

    override fun run() {
        checkNotNull(app).run()
    }

    override fun throwPendingCallbackFailure() {
        var failure: Throwable? = null
        failure = appKitCleanupStep(failure) { runLoopOwner?.throwPendingCallbackFailure() }
        failure = appKitCleanupStep(failure) { eventLoop.throwPendingCallbackFailure() }
        failure?.let { throw it }
    }

    override fun suppressPendingCallbackFailureOnto(primary: Throwable) {
        runLoopOwner?.suppressPendingCallbackFailureOnto(primary)
        eventLoop.suppressPendingCallbackFailureOnto(primary)
    }

    override fun closeRunLoopOwner() {
        val owner = runLoopOwner ?: return
        runLoopOwner = null
        eventLoop.clearRunLoopOwner(owner)
        owner.close()
    }

    override fun detachApplicationDelegate() {
        app?.setDelegate(MemorySegment.NULL)
    }

    override fun releaseApplicationDelegate() {
        val delegate = appDelegate ?: return
        appDelegate = null
        delegate.releaseNative()
    }

    override fun clearApplicationReferences() {
        app?.eventLoop = null
        app = null
        KadreApplication.sharedApp = null
    }
}

/** Entry point of the kadre event loop on macOS. */
fun runApp(handler: ApplicationHandler) {
    runApp(handler, ::NativeAppKitRunAppOperations)
}

internal fun runApp(
    handler: ApplicationHandler,
    operationsFactory: (AppKitEventLoop) -> AppKitRunAppOperations,
) {
    check(appKitRunning.compareAndSet(false, true)) {
        "AppKitEventLoop.runApp() can only be called once per process. An AppKit event loop is already active."
    }

    val eventLoop = AppKitEventLoop(handler)
    var operations: AppKitRunAppOperations? = null
    var primaryFailure: Throwable? = null
    try {
        val installedOperations = operationsFactory(eventLoop)
        operations = installedOperations
        eventLoop.installTerminationRequest(installedOperations::requestTermination)
        installedOperations.requireMainThread()
        installedOperations.initialize()
        installedOperations.attachApplicationDelegate()
        installedOperations.installRunLoopOwner()
        installedOperations.run()
        installedOperations.throwPendingCallbackFailure()
    } catch (failure: Throwable) {
        primaryFailure = failure
        try {
            operations?.suppressPendingCallbackFailureOnto(failure)
        } catch (callbackFailure: Throwable) {
            if (callbackFailure !== failure) failure.addSuppressed(callbackFailure)
        }
    } finally {
        var admissionClosed = false
        try {
            AppKitNativeCallbackBoundary.closeAdmissionForTeardown()
            admissionClosed = true
            primaryFailure = appKitCleanupStep(primaryFailure) { eventLoop.willTerminate() }
            primaryFailure = appKitCleanupStep(primaryFailure) { eventLoop.drainDeferredNativeCallbackCleanup() }
            primaryFailure = appKitCleanupStep(primaryFailure) { operations?.throwPendingCallbackFailure() }
            primaryFailure = appKitCleanupStep(primaryFailure) { operations?.closeRunLoopOwner() }
            primaryFailure = appKitCleanupStep(primaryFailure) { operations?.detachApplicationDelegate() }
            primaryFailure = appKitCleanupStep(primaryFailure) {
                AppKitNativeCallbackBoundary.awaitQuiescence()
            }
            primaryFailure = appKitCleanupStep(primaryFailure) { operations?.releaseApplicationDelegate() }
            primaryFailure = appKitCleanupStep(primaryFailure) { operations?.clearApplicationReferences() }
            primaryFailure = appKitCleanupStep(primaryFailure) { eventLoop.drainDeferredNativeCallbackCleanup() }
            primaryFailure = appKitCleanupStep(primaryFailure) { operations?.throwPendingCallbackFailure() }
        } finally {
            try {
                if (admissionClosed) {
                    AppKitNativeCallbackBoundary.finishTeardown { appKitRunning.set(false) }
                }
            } finally {
                appKitRunning.set(false)
            }
        }
    }
    primaryFailure?.let { throw it }
}

private inline fun appKitCleanupStep(primary: Throwable?, step: () -> Unit): Throwable? =
    try {
        step()
        primary
    } catch (failure: Throwable) {
        if (primary == null) failure else primary.also {
            if (failure !== it) it.addSuppressed(failure)
        }
    }
