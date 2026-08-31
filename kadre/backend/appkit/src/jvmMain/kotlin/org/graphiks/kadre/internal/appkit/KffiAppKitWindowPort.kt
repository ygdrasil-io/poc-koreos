package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kffi.objc.CGWindowLevelForKey
import org.graphiks.kffi.objc.CGWindowLevelKey
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSAppearance
import org.graphiks.kffi.objc.NSBackingStoreType
import org.graphiks.kffi.objc.NSButton
import org.graphiks.kffi.objc.NSEdgeInsets
import org.graphiks.kffi.objc.NSEventModifierFlags
import org.graphiks.kffi.objc.NSEventType
import org.graphiks.kffi.objc.NSNotificationCenter
import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSRect
import org.graphiks.kffi.objc.NSSize
import org.graphiks.kffi.objc.NSThread
import org.graphiks.kffi.objc.NSView
import org.graphiks.kffi.objc.NSWindow
import org.graphiks.kffi.objc.NSWindowButton
import org.graphiks.kffi.objc.NSWindowCollectionBehavior
import org.graphiks.kffi.objc.NSWindowOcclusionState
import org.graphiks.kffi.objc.NSWindowStyleMask
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.effectiveAppearance
import org.graphiks.kffi.objc.safeAreaInsets
import org.graphiks.kffi.objc.managed.ObjCManagedClass
import org.graphiks.kffi.objc.managed.ObjCManagedInstance
import org.graphiks.kffi.objc.managed.ObjCMethodSignatures
import org.graphiks.kffi.objc.managed.ObjCNotificationObservation
import org.graphiks.kffi.objc.managed.ObjCPointerTracking
import org.graphiks.kffi.objc.managed.NSEventObservation
import org.graphiks.kffi.objc.managed.installPointerTracking
import org.graphiks.kffi.objc.managed.observe
import org.graphiks.kffi.objc.performSelectorOnMainThread_withObject_waitUntilDone
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Version-only guard evaluated before any fullscreen selector or managed callback class is loaded. */
internal class AppKitFullscreenAvailability(
    systemVersion: String = System.getProperty("os.version", ""),
) {
    val isAvailable: Boolean = systemVersion.numericVersionOrNull()
        ?.let { it >= APPKIT_FULLSCREEN_MINIMUM_VERSION }
        ?: false
}

private data class AppKitNumericVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
) : Comparable<AppKitNumericVersion> {
    override fun compareTo(other: AppKitNumericVersion): Int =
        compareValuesBy(this, other, AppKitNumericVersion::major, AppKitNumericVersion::minor, AppKitNumericVersion::patch)
}

private fun String.numericVersionOrNull(): AppKitNumericVersion? {
    val parts = split('.')
    if (parts.isEmpty() || parts.size > 3) return null
    val values = parts.map { component ->
        component.toLongOrNull()?.takeIf { it >= 0L } ?: return null
    }
    return AppKitNumericVersion(
        major = values.getOrElse(0) { 0L },
        minor = values.getOrElse(1) { 0L },
        patch = values.getOrElse(2) { 0L },
    )
}

private val APPKIT_FULLSCREEN_MINIMUM_VERSION = AppKitNumericVersion(10L, 7L, 0L)

/** Public-KFFI-backed AppKit port. Native addresses remain private to this implementation. */
internal class KffiAppKitWindowPort(
    private val createUnconfiguredWindow: (WindowSpec) -> AppKitNativeWindowOwner =
        ::createKffiUnconfiguredWindow,
    private val configureWindow: (AppKitNativeWindowOwner, WindowSpec) -> Unit =
        ::configureKffiWindow,
    private val fullscreenAvailability: AppKitFullscreenAvailability = AppKitFullscreenAvailability(),
    private val collectionBehaviorWindow: (AppKitNativeWindowOwner) -> NSWindow =
        AppKitNativeWindowOwner::kffiWindow,
) : AppKitNativeWindowPort {
    fun prepare(
        id: AppKitWindowPeerId,
        spec: WindowSpec,
        acceptSurfaceStimulus: (AppKitSurfaceStimulus) -> Unit = {},
        acceptStimulus: (AppKitWindowStimulus) -> Unit,
    ): AppKitWindowPeer = AppKitWindowPeer.prepare(
        id = id,
        spec = spec,
        port = this,
        acceptStimulus = acceptStimulus,
        acceptSurfaceStimulus = acceptSurfaceStimulus,
    )

    override fun isMainThread(): Boolean = NSThread.isMainThread()

    override fun <T> onMainThread(block: () -> T): T = KffiAppKitMainThread.call(block)

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner {
        requireMainThread()
        val owner = createUnconfiguredWindow(spec)
        return try {
            configureWindow(owner, spec)
            if (fullscreenAvailability.isAvailable) {
                val window = collectionBehaviorWindow(owner)
                window.setCollectionBehavior(
                    window.collectionBehavior() +
                        NSWindowCollectionBehavior.NSWindowCollectionBehaviorFullScreenPrimary,
                )
            }
            owner
        } catch (failure: Throwable) {
            try {
                owner.close()
            } catch (closeFailure: Throwable) {
                if (closeFailure !== failure) failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    override fun updateWindow(
        window: AppKitNativeWindowOwner,
        target: AppKitWindowMutationTarget,
        commit: AppKitWindowMutationCommit,
    ): AppKitWindowMutationSnapshot? {
        requireMainThread()
        return window.kffiWindowOwner().updateWindow(target, commit)
    }

    override fun readWindow(window: AppKitNativeWindowOwner): AppKitWindowMutationSnapshot {
        requireMainThread()
        return window.kffiWindowOwner().readWindow()
    }

    override fun toggleFullscreen(
        window: AppKitNativeWindowOwner,
        target: AppKitWindowFullscreenTarget,
        commit: AppKitWindowMutationCommit,
    ): Boolean {
        requireMainThread()
        check(fullscreenAvailability.isAvailable) { "AppKit fullscreen requires macOS 10.7 or newer" }
        check(target.mode == org.graphiks.kadre.window.FullscreenMode.Borderless ||
            target.mode == org.graphiks.kadre.window.FullscreenMode.Windowed) {
            "AppKit supports only borderless fullscreen transitions"
        }
        if (!commit.beforeFirstSetter()) return false
        val owner = window.kffiWindowOwner()
        owner.restoreLevel(WindowLevel.Normal)
        check(appKitWindowLevel(owner.window.level()) == WindowLevel.Normal) {
            "AppKit fullscreen requires a normal window level before toggle"
        }
        owner.window.toggleFullScreen(MemorySegment.NULL)
        return true
    }

    override fun restoreWindowLevel(window: AppKitNativeWindowOwner, desiredLevel: WindowLevel) {
        requireMainThread()
        window.kffiWindowOwner().restoreLevel(desiredLevel)
    }

    override fun observeGeometry(
        window: AppKitNativeWindowOwner,
        callbacks: AppKitWindowGeometryCallbacks,
    ): AppKitNativeGeometryObserverOwner {
        requireMainThread()
        return window.kffiWindowOwner().installGeometryObserver(callbacks)
    }

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner {
        requireMainThread()
        val appearanceAdmission = KffiViewAppearanceAdmission()
        val inputAdmission = KffiViewInputAdmission()
        val instance = contentViewClass.createInstance {
            onVoid(VIEW_DID_CHANGE_EFFECTIVE_APPEARANCE) {
                appearanceAdmission.viewDidChangeEffectiveAppearance()
            }
            onBoolean(ACCEPTS_FIRST_RESPONDER, fallback = false) {
                inputAdmission.acceptsFirstResponder()
            }
            APPKIT_INPUT_EVENT_SELECTORS.forEach { selector ->
                onNSEvent(selector, inputAdmission::observe)
            }
        }
        return try {
            val view = NSView(instance.receiver.ptr)
            view.setFrame(contentRect(spec))
            KffiViewOwner(view, instance, appearanceAdmission, inputAdmission)
        } catch (failure: Throwable) {
            try {
                instance.close()
            } catch (closeFailure: Throwable) {
                if (closeFailure !== failure) failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner {
        requireMainThread()
        val admission = KffiDelegateAdmission(callbacks)
        val delegateClass = if (fullscreenAvailability.isAvailable) {
            fullscreenWindowDelegateClass
        } else {
            basicWindowDelegateClass
        }
        val instance = delegateClass.createInstance {
            onBooleanObject(WINDOW_SHOULD_CLOSE, fallback = false) {
                admission.windowShouldClose()
            }
            onVoidObject(WINDOW_WILL_CLOSE) {
                admission.windowWillClose()
            }
            if (fullscreenAvailability.isAvailable) {
                onVoidObject(WINDOW_WILL_ENTER_FULLSCREEN) { admission.windowWillEnterFullscreen() }
                onVoidObject(WINDOW_DID_ENTER_FULLSCREEN) { admission.windowDidEnterFullscreen() }
                onVoidObject(WINDOW_DID_FAIL_ENTER_FULLSCREEN) { admission.windowDidFailEnterFullscreen() }
                onVoidObject(WINDOW_WILL_EXIT_FULLSCREEN) { admission.windowWillExitFullscreen() }
                onVoidObject(WINDOW_DID_EXIT_FULLSCREEN) { admission.windowDidExitFullscreen() }
                onVoidObject(WINDOW_DID_FAIL_EXIT_FULLSCREEN) { admission.windowDidFailExitFullscreen() }
            }
        }
        return KffiDelegateOwner(
            receiver = instance.receiver.ptr,
            revokeAdmission = admission::revoke,
            closeReceiver = instance::close,
        )
    }

    override fun attachContentView(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ) {
        requireMainThread()
        window.kffiWindow().setContentView(view.kffiView().ptr)
    }

    override fun attachDelegate(
        window: AppKitNativeWindowOwner,
        delegate: AppKitNativeDelegateOwner,
    ) {
        requireMainThread()
        window.kffiWindow().setDelegate(delegate.kffiDelegate().receiver)
    }

    override fun present(window: AppKitNativeWindowOwner) {
        requireMainThread()
        window.kffiWindow().makeKeyAndOrderFront(MemorySegment.NULL)
    }

    override fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner {
        requireMainThread()
        return KffiSurfaceObserverOwner.create(
            window = window.kffiWindow(),
            viewOwner = view.kffiViewOwner(),
            geometryObserver = window.kffiWindowOwner().geometryObserver(),
            callbacks = callbacks,
            requireMainThread = ::requireMainThread,
        )
    }

    override fun observeInput(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitInputCallbacks,
    ): AppKitNativeInputObserverOwner {
        requireMainThread()
        return KffiInputObserverOwner.create(
            window = window.kffiWindow(),
            viewOwner = view.kffiViewOwner(),
            callbacks = callbacks,
        )
    }

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        requireMainThread()
        window.kffiWindow().setDelegate(MemorySegment.NULL)
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        requireMainThread()
        window.kffiWindow().setContentView(MemorySegment.NULL)
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        requireMainThread()
        window.kffiWindow().close()
    }

    override fun desktopHandle(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ): RuntimeDesktopNativeWindowHandle.AppKit {
        requireMainThread()
        return RuntimeDesktopNativeWindowHandle.AppKit(
            nsWindowAddress = window.kffiWindow().ptr.address().toULong(),
            nsViewAddress = view.kffiView().ptr.address().toULong(),
        )
    }

    private fun requireMainThread() {
        check(NSThread.isMainThread()) { "AppKit window operations must run on the main thread" }
    }

    private companion object {
        const val WINDOW_SHOULD_CLOSE = "windowShouldClose:"
        const val WINDOW_WILL_CLOSE = "windowWillClose:"
        const val WINDOW_WILL_ENTER_FULLSCREEN = "windowWillEnterFullScreen:"
        const val WINDOW_DID_ENTER_FULLSCREEN = "windowDidEnterFullScreen:"
        const val WINDOW_DID_FAIL_ENTER_FULLSCREEN = "windowDidFailToEnterFullScreen:"
        const val WINDOW_WILL_EXIT_FULLSCREEN = "windowWillExitFullScreen:"
        const val WINDOW_DID_EXIT_FULLSCREEN = "windowDidExitFullScreen:"
        const val WINDOW_DID_FAIL_EXIT_FULLSCREEN = "windowDidFailToExitFullScreen:"
        const val VIEW_DID_CHANGE_EFFECTIVE_APPEARANCE = "viewDidChangeEffectiveAppearance"
        const val ACCEPTS_FIRST_RESPONDER = "acceptsFirstResponder"

        val basicWindowDelegateClass: ObjCManagedClass by lazy {
            ObjCManagedClass.registerOnce(
                protocols = setOf("NSWindowDelegate"),
                methods = mapOf(
                    WINDOW_SHOULD_CLOSE to ObjCMethodSignatures.BooleanObject,
                    WINDOW_WILL_CLOSE to ObjCMethodSignatures.VoidObject,
                ),
            )
        }

        val fullscreenWindowDelegateClass: ObjCManagedClass by lazy {
            ObjCManagedClass.registerOnce(
                protocols = setOf("NSWindowDelegate"),
                methods = mapOf(
                    WINDOW_SHOULD_CLOSE to ObjCMethodSignatures.BooleanObject,
                    WINDOW_WILL_CLOSE to ObjCMethodSignatures.VoidObject,
                    WINDOW_WILL_ENTER_FULLSCREEN to ObjCMethodSignatures.VoidObject,
                    WINDOW_DID_ENTER_FULLSCREEN to ObjCMethodSignatures.VoidObject,
                    WINDOW_DID_FAIL_ENTER_FULLSCREEN to ObjCMethodSignatures.VoidObject,
                    WINDOW_WILL_EXIT_FULLSCREEN to ObjCMethodSignatures.VoidObject,
                    WINDOW_DID_EXIT_FULLSCREEN to ObjCMethodSignatures.VoidObject,
                    WINDOW_DID_FAIL_EXIT_FULLSCREEN to ObjCMethodSignatures.VoidObject,
                ),
            )
        }

        val contentViewClass: ObjCManagedClass by lazy {
            ObjCManagedClass.registerOnce(
                superclassName = "NSView",
                methods = mapOf(
                    VIEW_DID_CHANGE_EFFECTIVE_APPEARANCE to ObjCMethodSignatures.Void,
                    ACCEPTS_FIRST_RESPONDER to ObjCMethodSignatures.Boolean,
                ) + APPKIT_INPUT_EVENT_SELECTORS.associateWith { ObjCMethodSignatures.VoidObject },
            )
        }

    }
}

private val APPKIT_INPUT_EVENT_SELECTORS = listOf(
    "keyDown:",
    "keyUp:",
    "flagsChanged:",
    "mouseDown:",
    "mouseUp:",
    "mouseMoved:",
    "mouseDragged:",
    "rightMouseDown:",
    "rightMouseUp:",
    "rightMouseDragged:",
    "otherMouseDown:",
    "otherMouseUp:",
    "otherMouseDragged:",
    "mouseEntered:",
    "mouseExited:",
    "mouseCancelled:",
)

/** Maps the published, address-free KFFI event snapshot into Kadre's private AppKit stimulus. */
internal fun NSEventObservation.toAppKitInput(): AppKitInput? = when (type) {
    NSEventType.NSEventTypeKeyDown,
    NSEventType.NSEventTypeKeyUp,
    NSEventType.NSEventTypeFlagsChanged,
    -> keyboardInput(details as? NSEventObservation.Details.Keyboard ?: return null)

    NSEventType.NSEventTypeMouseEntered ->
        position.toLogicalPointOrNull()?.let(AppKitInput::PointerEntered)

    NSEventType.NSEventTypeMouseExited -> AppKitInput.PointerLeft

    NSEventType.NSEventTypeMouseCancelled,
    NSEventType.NSEventTypeMouseMoved,
    NSEventType.NSEventTypeLeftMouseDragged,
    NSEventType.NSEventTypeRightMouseDragged,
    NSEventType.NSEventTypeOtherMouseDragged,
    NSEventType.NSEventTypeLeftMouseDown,
    NSEventType.NSEventTypeLeftMouseUp,
    NSEventType.NSEventTypeRightMouseDown,
    NSEventType.NSEventTypeRightMouseUp,
    NSEventType.NSEventTypeOtherMouseDown,
    NSEventType.NSEventTypeOtherMouseUp,
    -> pointerInput(details as? NSEventObservation.Details.Pointer ?: return null)

    else -> null
}

private fun NSEventObservation.keyboardInput(
    keyboard: NSEventObservation.Details.Keyboard,
): AppKitInput.KeyChanged {
    val modifier = macModifierFor(keyboard.keyCode)
    val state = when (type) {
        NSEventType.NSEventTypeKeyUp -> KeyState.Released
        NSEventType.NSEventTypeFlagsChanged -> if (modifier != null && modifiers().pressed.contains(modifier)) {
            KeyState.Pressed
        } else {
            KeyState.Released
        }
        else -> KeyState.Pressed
    }
    return AppKitInput.KeyChanged(
        physicalKey = macPhysicalKey(keyboard.keyCode),
        logicalKey = macLogicalKey(keyboard),
        location = macKeyLocation(keyboard.keyCode),
        keyState = state,
        repeat = type == NSEventType.NSEventTypeKeyDown && keyboard.isRepeat,
        modifiers = modifiers(),
    )
}

private fun NSEventObservation.pointerInput(
    pointer: NSEventObservation.Details.Pointer,
): AppKitInput? = when (type) {
    NSEventType.NSEventTypeMouseCancelled -> AppKitInput.PointerLeft

    NSEventType.NSEventTypeMouseMoved,
    NSEventType.NSEventTypeLeftMouseDragged,
    NSEventType.NSEventTypeRightMouseDragged,
    NSEventType.NSEventTypeOtherMouseDragged,
    -> {
        val position = position.toLogicalPointOrNull() ?: return null
        val delta = pointer.toLogicalDeltaOrNull() ?: return null
        AppKitInput.PointerMoved(position, delta, pointer.pressureOrNull())
    }

    NSEventType.NSEventTypeLeftMouseDown,
    NSEventType.NSEventTypeLeftMouseUp,
    NSEventType.NSEventTypeRightMouseDown,
    NSEventType.NSEventTypeRightMouseUp,
    NSEventType.NSEventTypeOtherMouseDown,
    NSEventType.NSEventTypeOtherMouseUp,
    -> {
        val position = position.toLogicalPointOrNull() ?: return null
        val button = pointerButton(type, pointer.buttonNumber) ?: return null
        val state = when (type) {
            NSEventType.NSEventTypeLeftMouseDown,
            NSEventType.NSEventTypeRightMouseDown,
            NSEventType.NSEventTypeOtherMouseDown,
            -> PointerButtonState.Pressed
            else -> PointerButtonState.Released
        }
        AppKitInput.PointerButtonChanged(button, state, position, pointer.pressureOrNull())
    }

    else -> null
}

private fun NSEventObservation.modifiers(): KeyboardModifiers = KeyboardModifiers(
    buildSet {
        if (modifierFlags.contains(NSEventModifierFlags.NSEventModifierFlagShift)) add(ModifierKey.Shift)
        if (modifierFlags.contains(NSEventModifierFlags.NSEventModifierFlagControl)) add(ModifierKey.Control)
        if (modifierFlags.contains(NSEventModifierFlags.NSEventModifierFlagOption)) add(ModifierKey.Alt)
        if (modifierFlags.contains(NSEventModifierFlags.NSEventModifierFlagCommand)) add(ModifierKey.Meta)
        if (modifierFlags.contains(NSEventModifierFlags.NSEventModifierFlagCapsLock)) add(ModifierKey.CapsLock)
    },
)

private fun NSEventObservation.Position.toLogicalPointOrNull(): LogicalPoint? =
    if (x.isFinite() && y.isFinite()) LogicalPoint(x, y) else null

private fun NSEventObservation.Details.Pointer.toLogicalDeltaOrNull(): LogicalDelta? =
    if (deltaX.isFinite() && deltaY.isFinite()) LogicalDelta(deltaX, deltaY) else null

private fun NSEventObservation.Details.Pointer.pressureOrNull(): Double? =
    pressure.toDouble().takeIf { it.isFinite() && it in 0.0..1.0 }

private fun pointerButton(type: NSEventType, buttonNumber: Long): PointerButton? = when (type) {
    NSEventType.NSEventTypeLeftMouseDown,
    NSEventType.NSEventTypeLeftMouseUp,
    -> PointerButton.Primary
    NSEventType.NSEventTypeRightMouseDown,
    NSEventType.NSEventTypeRightMouseUp,
    -> PointerButton.Secondary
    else -> when (buttonNumber) {
        2L -> PointerButton.Auxiliary
        3L -> PointerButton.Back
        4L -> PointerButton.Forward
        in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() -> PointerButton.Other(buttonNumber.toInt())
        else -> null
    }
}

private fun macPhysicalKey(keyCode: Int): PhysicalKey = macHidUsage[keyCode]
    ?.let { PhysicalKey.Code(usagePage = 0x07, usageId = it) }
    ?: PhysicalKey.Unidentified("mac:$keyCode")

private fun macLogicalKey(keyboard: NSEventObservation.Details.Keyboard): LogicalKey =
    macNamedKey[keyboard.keyCode]?.let(LogicalKey::Named)
        ?: keyboard.characters.takeIf(String::isNotEmpty)?.let(LogicalKey::Character)
        ?: keyboard.charactersIgnoringModifiers.takeIf(String::isNotEmpty)?.let(LogicalKey::Character)
        ?: LogicalKey.Unidentified("mac:${keyboard.keyCode}")

private fun macKeyLocation(keyCode: Int): KeyLocation = when (keyCode) {
    in macLeftModifierKeyCodes -> KeyLocation.Left
    in macRightModifierKeyCodes -> KeyLocation.Right
    in macNumpadKeyCodes -> KeyLocation.Numpad
    else -> KeyLocation.Standard
}

private fun macModifierFor(keyCode: Int): ModifierKey? = when (keyCode) {
    56, 60 -> ModifierKey.Shift
    59, 62 -> ModifierKey.Control
    58, 61 -> ModifierKey.Alt
    55, 54 -> ModifierKey.Meta
    57 -> ModifierKey.CapsLock
    else -> null
}

private val macLeftModifierKeyCodes = setOf(55, 56, 58, 59)
private val macRightModifierKeyCodes = setOf(54, 60, 61, 62)
private val macNumpadKeyCodes = setOf(65, 67, 69, 71, 75, 76, 78, 81, 82, 83, 84, 85, 86, 87, 88, 89, 91, 92, 95)

private val macHidUsage = mapOf(
    0 to 0x04, 11 to 0x05, 8 to 0x06, 2 to 0x07, 14 to 0x08, 3 to 0x09, 5 to 0x0a,
    4 to 0x0b, 34 to 0x0c, 38 to 0x0d, 40 to 0x0e, 37 to 0x0f, 46 to 0x10, 45 to 0x11,
    31 to 0x12, 35 to 0x13, 12 to 0x14, 15 to 0x15, 1 to 0x16, 17 to 0x17, 32 to 0x18,
    9 to 0x19, 13 to 0x1a, 7 to 0x1b, 16 to 0x1c, 6 to 0x1d,
    29 to 0x1e, 18 to 0x1f, 19 to 0x20, 20 to 0x21, 21 to 0x22, 23 to 0x23, 22 to 0x24,
    26 to 0x25, 28 to 0x26, 25 to 0x27,
    36 to 0x28, 53 to 0x29, 51 to 0x2a, 48 to 0x2b, 49 to 0x2c, 27 to 0x2d, 24 to 0x2e,
    33 to 0x2f, 30 to 0x30, 42 to 0x31, 41 to 0x33, 39 to 0x34, 50 to 0x35,
    122 to 0x3a, 120 to 0x3b, 99 to 0x3c, 118 to 0x3d, 96 to 0x3e, 97 to 0x3f,
    98 to 0x40, 100 to 0x41, 101 to 0x42, 109 to 0x43, 103 to 0x44, 111 to 0x45,
    57 to 0x39, 55 to 0xe3, 56 to 0xe1, 58 to 0xe2, 59 to 0xe0, 54 to 0xe7, 60 to 0xe5,
    61 to 0xe6, 62 to 0xe4, 126 to 0x52, 125 to 0x51, 124 to 0x4f, 123 to 0x50,
    65 to 0x63, 67 to 0x55, 69 to 0x57, 71 to 0x53, 75 to 0x54, 76 to 0x58, 78 to 0x56,
    81 to 0x67, 82 to 0x62, 83 to 0x59, 84 to 0x5a, 85 to 0x5b, 86 to 0x5c, 87 to 0x5d,
    88 to 0x5e, 89 to 0x5f, 91 to 0x60, 92 to 0x61, 95 to 0x85,
)

private val macNamedKey = mapOf(
    36 to NamedKey.Enter, 48 to NamedKey.Tab, 49 to NamedKey.Space, 51 to NamedKey.Backspace,
    53 to NamedKey.Escape, 117 to NamedKey.Delete, 114 to NamedKey.Insert, 115 to NamedKey.Home,
    119 to NamedKey.End, 116 to NamedKey.PageUp, 121 to NamedKey.PageDown, 123 to NamedKey.ArrowLeft,
    124 to NamedKey.ArrowRight, 125 to NamedKey.ArrowDown, 126 to NamedKey.ArrowUp, 56 to NamedKey.Shift,
    60 to NamedKey.Shift, 59 to NamedKey.Control, 62 to NamedKey.Control, 58 to NamedKey.Alt,
    61 to NamedKey.Alt, 55 to NamedKey.Meta, 54 to NamedKey.Meta, 57 to NamedKey.CapsLock,
    122 to NamedKey.F1, 120 to NamedKey.F2, 99 to NamedKey.F3, 118 to NamedKey.F4, 96 to NamedKey.F5,
    97 to NamedKey.F6, 98 to NamedKey.F7, 100 to NamedKey.F8, 101 to NamedKey.F9, 109 to NamedKey.F10,
    103 to NamedKey.F11, 111 to NamedKey.F12,
)

private fun createKffiUnconfiguredWindow(spec: WindowSpec): AppKitNativeWindowOwner {
    NSApplication(NSApplication.sharedApplication())
    val initialized = NSWindow(allocate("NSWindow"))
        .initWithContentRect_styleMask_backing_defer(
            contentRect(spec),
            styleMask(spec),
            NSBackingStoreType.NSBackingStoreBuffered,
            false,
        )
    check(initialized != MemorySegment.NULL) { "NSWindow initialization failed" }
    return KffiWindowOwner(NSWindow(initialized))
}

private fun contentRect(spec: WindowSpec): NSRect = NSRect(
    NSPoint(0.0, 0.0),
    NSSize(spec.contentSize.width, spec.contentSize.height),
)

private fun styleMask(spec: WindowSpec): NSWindowStyleMask {
    return NSWindowStyleMask(0L).withAppKitChrome(
        decorations = spec.decorations,
        systemButtons = spec.systemButtons.canonicalFor(spec.decorations),
        resizable = spec.resizable,
    )
}

private fun configureKffiWindow(owner: AppKitNativeWindowOwner, spec: WindowSpec) {
    owner.kffiWindowOwner().applyInitialGeometry(spec)
    owner.kffiWindowOwner().applyInitialChrome(spec)
    owner.kffiWindowOwner().applyInitialLevel(spec)
    owner.kffiWindow().apply {
        setReleasedWhenClosed(false)
        setTitle(spec.title)
    }
}

private class KffiWindowOwner(
    val window: NSWindow,
) : AppKitNativeWindowOwner {
    private val closed = AtomicBoolean(false)
    private val nativeMinimumDefault = window.contentMinSize().copy()
    private val nativeMaximumDefault = window.contentMaxSize().copy()
    private var requestedMinimumSize: LogicalSize? = null
    private var requestedMaximumSize: LogicalSize? = null
    private var geometryObserver: KffiGeometryObserverOwner? = null

    fun applyInitialGeometry(spec: WindowSpec) {
        requestedMinimumSize = spec.minimumSize
        requestedMaximumSize = spec.maximumSize
        window.setContentSize(spec.contentSize.toNSSize())
        window.setContentMinSize((spec.minimumSize?.toNSSize() ?: nativeMinimumDefault).copy())
        window.setContentMaxSize((spec.maximumSize?.toNSSize() ?: nativeMaximumDefault).copy())
        window.setStyleMask(window.styleMask().withResizable(spec.resizable))
    }

    fun applyInitialChrome(spec: WindowSpec) {
        applyChrome(
            decorations = spec.decorations,
            systemButtons = spec.systemButtons.canonicalFor(spec.decorations),
            resizable = spec.resizable,
        )
    }

    fun applyInitialLevel(spec: WindowSpec) {
        window.setLevel(spec.level.toAppKitWindowLevel())
    }

    fun restoreLevel(level: WindowLevel) {
        window.setLevel(level.toAppKitWindowLevel())
    }

    fun updateWindow(
        target: AppKitWindowMutationTarget,
        commit: AppKitWindowMutationCommit,
    ): AppKitWindowMutationSnapshot? {
        if (!commit.beforeFirstSetter()) return null
        when (val title = target.title) {
            is PropertyChange.Set -> window.setTitle(title.value)
            PropertyChange.Clear -> error("AppKit does not support clearing a window title")
            PropertyChange.Unchanged -> Unit
        }
        if (target.geometry.hasChange()) applyGeometry(target.geometry)
        if (target.chrome.hasChange()) {
            val current = readChrome()
            applyChrome(
                decorations = target.chrome.decorations.resolveValue(current.decorations),
                systemButtons = target.chrome.systemButtons.resolveValue(current.systemButtons),
                resizable = window.styleMask().contains(NSWindowStyleMask.NSWindowStyleMaskResizable),
            )
        }
        when (val level = target.level.level) {
            is PropertyChange.Set -> window.setLevel(level.value.toAppKitWindowLevel())
            PropertyChange.Clear -> error("AppKit does not support clearing a window level")
            PropertyChange.Unchanged -> Unit
        }
        return readWindow()
    }

    private fun applyGeometry(
        target: AppKitWindowGeometryTarget,
    ) {
        val contentSize = target.contentSize.resolveValue(readContentSize(window))
        val minimumSize = target.minimumSize.resolveOptional(requestedMinimumSize)
        val maximumSize = target.maximumSize.resolveOptional(requestedMaximumSize)
        val nativeMinimum = minimumSize?.toNSSize() ?: nativeMinimumDefault
        val nativeMaximum = maximumSize?.toNSSize() ?: nativeMaximumDefault
        val currentMinimum = window.contentMinSize()
        val currentMaximum = window.contentMaxSize()

        val minimumRelaxed = currentMinimum.exceeds(contentSize)
        if (minimumRelaxed) {
            window.setContentMinSize(nativeMinimum.copy())
            requestedMinimumSize = minimumSize
        }
        val maximumRelaxed = currentMaximum.fallsBelow(contentSize)
        if (maximumRelaxed) {
            window.setContentMaxSize(nativeMaximum.copy())
            requestedMaximumSize = maximumSize
        }

        window.setContentSize(contentSize.toNSSize())
        if (!minimumRelaxed) {
            window.setContentMinSize(nativeMinimum.copy())
            requestedMinimumSize = minimumSize
        }
        if (!maximumRelaxed) {
            window.setContentMaxSize(nativeMaximum.copy())
            requestedMaximumSize = maximumSize
        }

        val resizable = target.resizable.resolveValue(
            window.styleMask().contains(NSWindowStyleMask.NSWindowStyleMaskResizable),
        )
        window.setStyleMask(window.styleMask().withResizable(resizable))
        val chrome = readChrome()
        applyChrome(chrome.decorations, chrome.systemButtons, resizable)
    }

    fun readGeometry(): AppKitWindowGeometrySnapshot =
        readGeometrySnapshot(window, requestedMinimumSize, requestedMaximumSize)

    fun readWindow(): AppKitWindowMutationSnapshot = AppKitWindowMutationSnapshot(
        title = window.titleAsString(),
        geometry = readGeometry(),
        chrome = readChrome(),
        level = readLevel(),
    )

    private fun readLevel(): WindowLevel = appKitWindowLevel(window.level())

    private fun applyChrome(
        decorations: WindowDecorations,
        systemButtons: WindowSystemButtons,
        resizable: Boolean,
    ) {
        val effectiveButtons = systemButtons.canonicalFor(decorations)
        window.setStyleMask(
            window.styleMask().withAppKitChrome(decorations, effectiveButtons, resizable),
        )
        if (decorations == WindowDecorations.System) {
            window.setStandardButtonHidden(
                NSWindowButton.NSWindowCloseButton,
                hidden = effectiveButtons == WindowSystemButtons.None,
            )
            window.setStandardButtonHidden(
                NSWindowButton.NSWindowMiniaturizeButton,
                hidden = effectiveButtons != WindowSystemButtons.All,
            )
            window.setStandardButtonHidden(
                NSWindowButton.NSWindowZoomButton,
                hidden = effectiveButtons != WindowSystemButtons.All || !resizable,
            )
        }
    }

    private fun readChrome(): AppKitWindowChromeSnapshot {
        val style = window.styleMask()
        if (!style.contains(NSWindowStyleMask.NSWindowStyleMaskTitled)) {
            return AppKitWindowChromeSnapshot(
                decorations = WindowDecorations.Borderless,
                systemButtons = WindowSystemButtons.None,
            )
        }
        val closeHidden = window.standardButtonHidden(NSWindowButton.NSWindowCloseButton)
        val miniaturizeHidden = window.standardButtonHidden(NSWindowButton.NSWindowMiniaturizeButton)
        val zoomHidden = window.standardButtonHidden(NSWindowButton.NSWindowZoomButton)
        val resizable = style.contains(NSWindowStyleMask.NSWindowStyleMaskResizable)
        val buttons = when {
            closeHidden && miniaturizeHidden && zoomHidden -> WindowSystemButtons.None
            !closeHidden && miniaturizeHidden && zoomHidden -> WindowSystemButtons.CloseOnly
            !closeHidden && !miniaturizeHidden && (!resizable || !zoomHidden) -> WindowSystemButtons.All
            else -> error("AppKit returned an unsupported standard-window-button combination")
        }
        return AppKitWindowChromeSnapshot(WindowDecorations.System, buttons)
    }

    fun installGeometryObserver(callbacks: AppKitWindowGeometryCallbacks): KffiGeometryObserverOwner {
        check(geometryObserver == null) { "AppKit window geometry is already observed" }
        return KffiGeometryObserverOwner(callbacks) {
            readGeometrySnapshot(window, requestedMinimumSize, requestedMaximumSize)
        }.also { geometryObserver = it }
    }

    fun geometryObserver(): KffiGeometryObserverOwner? = geometryObserver

    override fun close() {
        if (closed.compareAndSet(false, true)) release(window.ptr)
    }
}

private fun LogicalSize.toNSSize(): NSSize = NSSize(width, height)

private fun NSSize.copy(): NSSize = NSSize(width, height)

private fun NSSize.exceeds(size: LogicalSize): Boolean = width > size.width || height > size.height

private fun NSSize.fallsBelow(size: LogicalSize): Boolean = width < size.width || height < size.height

private fun NSWindowStyleMask.withResizable(resizable: Boolean): NSWindowStyleMask {
    val rawValue = if (resizable) {
        rawValue or NSWindowStyleMask.NSWindowStyleMaskResizable.rawValue
    } else {
        rawValue and NSWindowStyleMask.NSWindowStyleMaskResizable.rawValue.inv()
    }
    return NSWindowStyleMask(rawValue)
}

private fun NSWindowStyleMask.withAppKitChrome(
    decorations: WindowDecorations,
    systemButtons: WindowSystemButtons,
    resizable: Boolean,
): NSWindowStyleMask {
    val effectiveButtons = systemButtons.canonicalFor(decorations)
    var owned = 0L
    if (decorations == WindowDecorations.System) {
        owned = owned or NSWindowStyleMask.NSWindowStyleMaskTitled.rawValue
        when (effectiveButtons) {
            WindowSystemButtons.All -> {
                owned = owned or NSWindowStyleMask.NSWindowStyleMaskClosable.rawValue
                owned = owned or NSWindowStyleMask.NSWindowStyleMaskMiniaturizable.rawValue
            }
            WindowSystemButtons.CloseOnly -> {
                owned = owned or NSWindowStyleMask.NSWindowStyleMaskClosable.rawValue
            }
            WindowSystemButtons.None -> Unit
        }
    }
    if (resizable) owned = owned or NSWindowStyleMask.NSWindowStyleMaskResizable.rawValue
    return NSWindowStyleMask(rawValue and APPKIT_OWNED_STYLE_MASK.inv() or owned)
}

private fun WindowSystemButtons.canonicalFor(
    decorations: WindowDecorations,
): WindowSystemButtons = if (decorations == WindowDecorations.Borderless) {
    WindowSystemButtons.None
} else {
    this
}

private fun NSWindow.setStandardButtonHidden(button: NSWindowButton, hidden: Boolean) {
    val nativeButton = standardWindowButton(button)
    check(nativeButton != MemorySegment.NULL) { "AppKit did not provide the requested standard window button" }
    NSButton(nativeButton).setHidden(hidden)
}

private fun NSWindow.standardButtonHidden(button: NSWindowButton): Boolean {
    val nativeButton = standardWindowButton(button)
    check(nativeButton != MemorySegment.NULL) { "AppKit did not provide the requested standard window button" }
    return NSButton(nativeButton).isHidden()
}

private fun AppKitWindowChromeTarget.hasChange(): Boolean =
    decorations !is PropertyChange.Unchanged || systemButtons !is PropertyChange.Unchanged

private fun WindowLevel.toAppKitWindowLevel(): Long = CGWindowLevelForKey(
    when (this) {
        WindowLevel.Normal -> CGWindowLevelKey.kCGNormalWindowLevelKey
        WindowLevel.Floating -> CGWindowLevelKey.kCGFloatingWindowLevelKey
        WindowLevel.Modal -> CGWindowLevelKey.kCGModalPanelWindowLevelKey
    },
).toLong()

private fun appKitWindowLevel(level: Long): WindowLevel = when (level) {
    WindowLevel.Normal.toAppKitWindowLevel() -> WindowLevel.Normal
    WindowLevel.Floating.toAppKitWindowLevel() -> WindowLevel.Floating
    WindowLevel.Modal.toAppKitWindowLevel() -> WindowLevel.Modal
    else -> error("AppKit returned an unsupported window level: $level")
}

private val APPKIT_OWNED_STYLE_MASK: Long =
    NSWindowStyleMask.NSWindowStyleMaskTitled.rawValue or
        NSWindowStyleMask.NSWindowStyleMaskClosable.rawValue or
        NSWindowStyleMask.NSWindowStyleMaskMiniaturizable.rawValue or
        NSWindowStyleMask.NSWindowStyleMaskResizable.rawValue

private fun readGeometrySnapshot(
    window: NSWindow,
    requestedMinimumSize: LogicalSize?,
    requestedMaximumSize: LogicalSize?,
): AppKitWindowGeometrySnapshot = AppKitWindowGeometrySnapshot(
    contentSize = readContentSize(window),
    minimumSize = requestedMinimumSize?.let { window.contentMinSize().toLogicalSize() },
    maximumSize = requestedMaximumSize?.let { window.contentMaxSize().toLogicalSize() },
    resizable = window.styleMask().contains(NSWindowStyleMask.NSWindowStyleMaskResizable),
)

private fun readContentSize(window: NSWindow): LogicalSize =
    window.contentRectForFrameRect(window.frame()).size.toLogicalSize()

private fun NSSize.toLogicalSize(): LogicalSize = LogicalSize(width, height)

private fun AppKitWindowGeometryTarget.hasChange(): Boolean =
    contentSize !is PropertyChange.Unchanged ||
        minimumSize !is PropertyChange.Unchanged ||
        maximumSize !is PropertyChange.Unchanged ||
        resizable !is PropertyChange.Unchanged

private fun <T> PropertyChange<T>.resolveValue(current: T): T = when (this) {
    PropertyChange.Unchanged -> current
    is PropertyChange.Set -> value
    PropertyChange.Clear -> current
}

private fun <T> PropertyChange<T>.resolveOptional(current: T?): T? = when (this) {
    PropertyChange.Unchanged -> current
    is PropertyChange.Set -> value
    PropertyChange.Clear -> null
}

private class KffiViewOwner(
    val view: NSView,
    private val instance: ObjCManagedInstance,
    val appearanceAdmission: KffiViewAppearanceAdmission,
    val inputAdmission: KffiViewInputAdmission,
) : AppKitNativeViewOwner {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) instance.close()
    }
}

private class KffiViewAppearanceAdmission {
    private val callback = AtomicReference<(() -> Unit)?>(null)

    fun observe(onAppearanceChanged: () -> Unit): AutoCloseable {
        check(callback.compareAndSet(null, onAppearanceChanged)) {
            "AppKit view appearance callback is already observed"
        }
        return KffiViewAppearanceObservation(this, onAppearanceChanged)
    }

    fun viewDidChangeEffectiveAppearance() {
        callback.get()?.invoke()
    }

    fun revoke(onAppearanceChanged: () -> Unit) {
        callback.compareAndSet(onAppearanceChanged, null)
    }
}

private class KffiViewAppearanceObservation(
    private val admission: KffiViewAppearanceAdmission,
    private val callback: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) admission.revoke(callback)
    }
}

/** Per-view admission for immutable KFFI input snapshots. */
private class KffiViewInputAdmission {
    private val observer = AtomicReference<KffiViewInputObserver?>(null)

    fun install(
        callbacks: AppKitInputCallbacks,
        pointerEnabled: Boolean,
    ): AutoCloseable {
        val observer = KffiViewInputObserver(callbacks, pointerEnabled)
        check(this.observer.compareAndSet(null, observer)) {
            "AppKit view input callbacks are already observed"
        }
        return KffiViewInputObservation(this, observer)
    }

    fun acceptsFirstResponder(): Boolean = observer.get() != null

    fun observe(observation: NSEventObservation) {
        val observer = observer.get() ?: return
        observation.toAppKitInput()?.takeIf(observer::accepts)?.let(observer.callbacks.input)
    }

    fun revoke(observer: KffiViewInputObserver) {
        this.observer.compareAndSet(observer, null)
    }
}

private class KffiViewInputObserver(
    val callbacks: AppKitInputCallbacks,
    private val pointerEnabled: Boolean,
) {
    fun accepts(input: AppKitInput): Boolean = input is AppKitInput.KeyChanged || pointerEnabled
}

private class KffiViewInputObservation(
    private val admission: KffiViewInputAdmission,
    private val observer: KffiViewInputObserver,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) admission.revoke(observer)
    }
}

private class KffiInputObserverOwner private constructor(
    private val observation: AutoCloseable,
    private val pointerTracking: ObjCPointerTracking,
) : AppKitNativeInputObserverOwner {
    private val closed = AtomicBoolean(false)

    override val keyboardInstalled: Boolean = true
    override val pointerInstalled: Boolean = true

    override fun revokeCallbacks() {
        observation.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var failure: Throwable? = null
        try {
            revokeCallbacks()
        } catch (revokeFailure: Throwable) {
            failure = revokeFailure
        }
        try {
            pointerTracking.close()
        } catch (trackingFailure: Throwable) {
            if (failure != null && failure !== trackingFailure) {
                failure.addSuppressed(trackingFailure)
            } else {
                failure = trackingFailure
            }
        }
        failure?.let { throw it }
    }

    companion object {
        fun create(
            window: NSWindow,
            viewOwner: KffiViewOwner,
            callbacks: AppKitInputCallbacks,
        ): KffiInputObserverOwner {
            val observation = viewOwner.inputAdmission.install(callbacks, pointerEnabled = true)
            var pointerTracking: ObjCPointerTracking? = null
            return try {
                check(window.makeFirstResponder(viewOwner.view.ptr)) {
                    "AppKit refused the Kadre content view as first responder"
                }
                pointerTracking = viewOwner.view.installPointerTracking(window)
                KffiInputObserverOwner(observation, checkNotNull(pointerTracking))
            } catch (failure: Throwable) {
                try {
                    pointerTracking?.close()
                } catch (closeFailure: Throwable) {
                    if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                }
                try {
                    observation.close()
                } catch (closeFailure: Throwable) {
                    if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        }
    }
}

private class KffiGeometryObserverOwner(
    private val callbacks: AppKitWindowGeometryCallbacks,
    private val readSnapshot: () -> AppKitWindowGeometrySnapshot,
) : AppKitNativeGeometryObserverOwner {
    private val accepting = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)

    fun emit() {
        if (accepting.get()) callbacks.geometryChanged(readSnapshot())
    }

    override fun revokeCallbacks() {
        accepting.set(false)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) revokeCallbacks()
    }
}

private class KffiSurfaceObserverOwner private constructor(
    private val window: NSWindow,
    private val view: NSView,
    private val geometryObserver: KffiGeometryObserverOwner?,
    private val callbacks: AppKitSurfaceCallbacks,
    private val requireMainThread: () -> Unit,
    private val observations: List<ObjCNotificationObservation>,
) : AppKitNativeSurfaceObserverOwner {
    private val accepting = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private var appearanceObservation: AutoCloseable? = null
    override lateinit var initialSnapshot: AppKitSurfaceSnapshot
        private set

    override fun requestRedraw(generation: Long) {
        require(generation >= 0L) { "generation must be non-negative" }
        requireMainThread()
        if (!accepting.get()) return
        view.setNeedsDisplay(true)
        if (accepting.get()) callbacks.redrawConsumed(generation)
    }

    override fun revokeCallbacks() {
        accepting.set(false)
        appearanceObservation?.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        revokeCallbacks()
        var failure: Throwable? = null
        observations.asReversed().forEach { observation ->
            failure = try {
                observation.close()
                failure
            } catch (closeFailure: Throwable) {
                failure?.also {
                    if (it !== closeFailure) it.addSuppressed(closeFailure)
                } ?: closeFailure
            }
        }
        failure?.let { throw it }
    }

    private fun emitMetrics() {
        requireMainThread()
        if (accepting.get()) callbacks.metricsChanged(readMetrics(view, window))
    }

    private fun emitGeometry() {
        requireMainThread()
        geometryObserver?.emit()
    }

    private fun emitFocus() {
        requireMainThread()
        if (accepting.get()) callbacks.focusChanged(readFocus(window))
    }

    private fun emitVisibility() {
        requireMainThread()
        if (!accepting.get()) return
        val (visibility, occlusion) = readVisibility(window)
        callbacks.visibilityChanged(visibility, occlusion)
    }

    private fun emitTheme() {
        requireMainThread()
        if (accepting.get()) callbacks.themeChanged(readTheme(view))
    }

    private fun observeAppearance(admission: KffiViewAppearanceAdmission) {
        check(appearanceObservation == null) { "AppKit view appearance is already observed" }
        appearanceObservation = admission.observe(::emitTheme)
    }

    companion object {
        fun create(
            window: NSWindow,
            viewOwner: KffiViewOwner,
            geometryObserver: KffiGeometryObserverOwner?,
            callbacks: AppKitSurfaceCallbacks,
            requireMainThread: () -> Unit,
        ): KffiSurfaceObserverOwner {
            val view = viewOwner.view
            val observations = mutableListOf<ObjCNotificationObservation>()
            var owner: KffiSurfaceObserverOwner? = null
            try {
                val center = NSNotificationCenter(NSNotificationCenter.defaultCenter())
                fun observe(names: List<String>, objectFilter: MemorySegment, callback: () -> Unit) {
                    names.forEach { name ->
                        observations += center.observe(
                            name = ObjCRuntime.newNSString(Arena.global(), name),
                            objectFilter = objectFilter,
                        ) { callback() }
                    }
                }

                val installedOwner = KffiSurfaceObserverOwner(
                    window,
                    view,
                    geometryObserver,
                    callbacks,
                    requireMainThread,
                    observations,
                )
                owner = installedOwner
                installedOwner.observeAppearance(viewOwner.appearanceAdmission)
                observe(
                    listOf("NSWindowDidResizeNotification"),
                    window.ptr,
                ) {
                    installedOwner.emitMetrics()
                    installedOwner.emitGeometry()
                }
                observe(
                    listOf("NSWindowDidChangeBackingPropertiesNotification"),
                    window.ptr,
                    installedOwner::emitMetrics,
                )
                observe(
                    listOf(
                        "NSWindowDidBecomeKeyNotification",
                        "NSWindowDidResignKeyNotification",
                    ),
                    window.ptr,
                    installedOwner::emitFocus,
                )
                observe(
                    listOf(
                        "NSWindowDidOrderOnScreenNotification",
                        "NSWindowDidOrderOffScreenNotification",
                        "NSWindowDidMiniaturizeNotification",
                        "NSWindowDidDeminiaturizeNotification",
                        "NSWindowDidChangeOcclusionStateNotification",
                    ),
                    window.ptr,
                    installedOwner::emitVisibility,
                )
                installedOwner.initialSnapshot = readSnapshot(view, window)
                return installedOwner
            } catch (failure: Throwable) {
                val installedOwner = owner
                if (installedOwner != null) {
                    try {
                        installedOwner.close()
                    } catch (closeFailure: Throwable) {
                        if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                    }
                } else {
                    observations.asReversed().forEach { observation ->
                        try {
                            observation.close()
                        } catch (closeFailure: Throwable) {
                            if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                        }
                    }
                }
                throw failure
            }
        }
    }
}

private fun readSnapshot(view: NSView, window: NSWindow): AppKitSurfaceSnapshot {
    val (visibility, occlusion) = readVisibility(window)
    return AppKitSurfaceSnapshot(
        metrics = readMetrics(view, window),
        focus = readFocus(window),
        visibility = visibility,
        occlusion = occlusion,
        theme = readTheme(view),
    )
}

private fun readMetrics(view: NSView, window: NSWindow): SurfaceMetrics {
    val size = view.bounds().size
    val logicalSize = LogicalSize(size.width, size.height)
    val scaleFactor = window.backingScaleFactor()
    return SurfaceMetrics(
        logicalSize = logicalSize,
        physicalSize = logicalSize.toPhysical(scaleFactor),
        scaleFactor = scaleFactor,
        safeAreaInsets = view.safeAreaInsets().toLogicalInsets(),
    )
}

internal fun NSEdgeInsets.toLogicalInsets(): LogicalInsets = LogicalInsets(
    top = top,
    right = right,
    bottom = bottom,
    left = left,
)

private fun readFocus(window: NSWindow): SurfaceFocus =
    if (window.isKeyWindow()) SurfaceFocus.Focused else SurfaceFocus.Unfocused

private fun readVisibility(window: NSWindow): Pair<SurfaceVisibility, SurfaceOcclusion> {
    val visible = window.isVisible() && !window.isMiniaturized()
    if (!visible) return SurfaceVisibility.Hidden to SurfaceOcclusion.Unknown
    val occlusion = if (
        window.occlusionState().contains(NSWindowOcclusionState.NSWindowOcclusionStateVisible)
    ) {
        SurfaceOcclusion.Visible
    } else {
        SurfaceOcclusion.Occluded
    }
    return SurfaceVisibility.Visible to occlusion
}

private fun readTheme(view: NSView): SurfaceTheme {
    val appearance = NSAppearance(view.effectiveAppearance())
    val name = ObjCRuntime.toJavaString(appearance.name())
    return when {
        name.contains("Dark", ignoreCase = true) -> SurfaceTheme.Dark
        name.isNotBlank() -> SurfaceTheme.Light
        else -> SurfaceTheme.Unknown
    }
}

internal class KffiDelegateOwner(
    internal val receiver: MemorySegment,
    private val revokeAdmission: () -> Unit,
    private val closeReceiver: () -> Unit,
    private val retainForProcessLifetime: (KffiDelegateOwner) -> Unit =
        KffiUndetachedDelegateQuarantine::retain,
) : AppKitNativeDelegateOwner {
    private val disposition = AtomicReference(KffiDelegateDisposition.Owned)

    override fun revokeCallbacks() {
        revokeAdmission()
    }

    override fun retainAfterFailedDetachment() {
        val quarantined = disposition.compareAndSet(
            KffiDelegateDisposition.Owned,
            KffiDelegateDisposition.Retained,
        )
        if (quarantined) {
            retainForProcessLifetime(this)
        }
    }

    override fun close() {
        val closing = disposition.compareAndSet(
            KffiDelegateDisposition.Owned,
            KffiDelegateDisposition.Closed,
        )
        if (closing) {
            revokeAdmission()
            closeReceiver()
        }
    }
}

private enum class KffiDelegateDisposition {
    Owned,
    Retained,
    Closed,
}

private object KffiUndetachedDelegateQuarantine {
    private val retained = ConcurrentLinkedQueue<KffiDelegateOwner>()

    fun retain(owner: KffiDelegateOwner) {
        retained.add(owner)
    }
}

private class KffiDelegateAdmission(
    private val callbacks: AppKitWindowDelegateCallbacks,
) {
    private val accepting = AtomicBoolean(true)

    fun windowShouldClose(): Boolean =
        if (accepting.get()) callbacks.windowShouldClose() else false

    fun windowWillClose() {
        if (accepting.get()) callbacks.windowWillClose()
    }

    fun windowWillEnterFullscreen() {
        if (accepting.get()) callbacks.windowWillEnterFullscreen()
    }

    fun windowDidEnterFullscreen() {
        if (accepting.get()) callbacks.windowDidEnterFullscreen()
    }

    fun windowDidFailEnterFullscreen() {
        if (accepting.get()) callbacks.windowDidFailEnterFullscreen()
    }

    fun windowWillExitFullscreen() {
        if (accepting.get()) callbacks.windowWillExitFullscreen()
    }

    fun windowDidExitFullscreen() {
        if (accepting.get()) callbacks.windowDidExitFullscreen()
    }

    fun windowDidFailExitFullscreen() {
        if (accepting.get()) callbacks.windowDidFailExitFullscreen()
    }

    fun revoke() {
        accepting.set(false)
    }
}

private fun AppKitNativeWindowOwner.kffiWindow(): NSWindow =
    kffiWindowOwner().window

private fun AppKitNativeWindowOwner.kffiWindowOwner(): KffiWindowOwner =
    this as? KffiWindowOwner ?: error("foreign AppKit window owner")

private fun AppKitNativeViewOwner.kffiView(): NSView =
    kffiViewOwner().view

private fun AppKitNativeViewOwner.kffiViewOwner(): KffiViewOwner =
    this as? KffiViewOwner ?: error("foreign AppKit view owner")

private fun AppKitNativeDelegateOwner.kffiDelegate(): KffiDelegateOwner =
    (this as? KffiDelegateOwner) ?: error("foreign AppKit delegate owner")

private fun allocate(className: String): MemorySegment = ObjCRuntime.msgSend(
    ValueLayout.ADDRESS,
    ObjCRuntime.getClass(className),
    ObjCRuntime.sel("alloc"),
) as MemorySegment

private fun release(receiver: MemorySegment) {
    ObjCRuntime.msgSend(null, receiver, ObjCRuntime.sel("release"))
}

private object KffiAppKitMainThread {
    private const val INVOKE_SELECTOR = "kadreInvoke:"
    private val invokerClass: ObjCManagedClass by lazy {
        ObjCManagedClass.registerOnce(
            methods = mapOf(INVOKE_SELECTOR to ObjCMethodSignatures.VoidObject),
        )
    }

    fun <T> call(block: () -> T): T {
        if (NSThread.isMainThread()) return ObjCRuntime.autoreleasePool(block)

        val outcome = AtomicReference<MainThreadOutcome<T>?>(null)
        val invoker = invokerClass.createInstance {
            onVoidObject(INVOKE_SELECTOR) {
                outcome.set(MainThreadOutcome(runCatching { ObjCRuntime.autoreleasePool(block) }))
            }
        }
        try {
            invoker.receiver.performSelectorOnMainThread_withObject_waitUntilDone(
                ObjCRuntime.sel(INVOKE_SELECTOR),
                MemorySegment.NULL,
                true,
            )
            return checkNotNull(outcome.get()) { "AppKit main-thread invocation was not delivered" }
                .result
                .getOrThrow()
        } finally {
            invoker.close()
        }
    }
}

private class MainThreadOutcome<T>(
    val result: Result<T>,
)
