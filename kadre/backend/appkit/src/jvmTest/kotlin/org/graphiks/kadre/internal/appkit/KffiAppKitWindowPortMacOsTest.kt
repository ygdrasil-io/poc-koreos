package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSAppearance
import org.graphiks.kffi.objc.NSBackingStoreType
import org.graphiks.kffi.objc.NSEdgeInsets
import org.graphiks.kffi.objc.NSEvent
import org.graphiks.kffi.objc.NSEventModifierFlags
import org.graphiks.kffi.objc.NSEventType
import org.graphiks.kffi.objc.NSNotificationCenter
import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSRect
import org.graphiks.kffi.objc.NSSize
import org.graphiks.kffi.objc.NSView
import org.graphiks.kffi.objc.NSWindow
import org.graphiks.kffi.objc.NSWindowStyleMask
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.effectiveAppearance
import org.graphiks.kffi.objc.setAppearance
import org.graphiks.kffi.objc.managed.ObjCManagedClass
import org.graphiks.kffi.objc.managed.ObjCMethodSignatures
import org.graphiks.kffi.objc.managed.NSEventObservation
import org.graphiks.kffi.objc.managed.observe
import org.graphiks.kffi.objc.safeAreaInsets
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KffiAppKitWindowPortMacOsTest {
    @Test
    fun generatedKffiWindowAppliesInitialContentConstraintsAndResizableMaskOnMacOs() {
        if (!isMacOsHost()) return

        val peer = KffiAppKitWindowPort().prepare(
            id = AppKitWindowPeerId(83L),
            spec = WindowSpec(
                contentSize = LogicalSize(420.0, 280.0),
                minimumSize = LogicalSize(320.0, 200.0),
                maximumSize = LogicalSize(640.0, 480.0),
                resizable = false,
            ),
            acceptSurfaceStimulus = { },
            acceptStimulus = { },
        )

        try {
            assertEquals(
                KadreResult.Success(
                    NativeWindowGeometry(
                        contentWidth = 420.0,
                        contentHeight = 280.0,
                        minimumWidth = 320.0,
                        minimumHeight = 200.0,
                        maximumWidth = 640.0,
                        maximumHeight = 480.0,
                        resizable = false,
                    ),
                ),
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    NSWindow(MemorySegment.ofAddress(handle.appKitWindowAddress()))
                        .readGeneratedNativeGeometry()
                },
            )
        } finally {
            peer.close()
        }
    }

    @Test
    fun generatedKffiWindowUpdatesTitleAndReturnsTheNativeReadbackOnMacOs() {
        if (!isMacOsHost()) return

        val peer = KffiAppKitWindowPort().prepare(
            id = AppKitWindowPeerId(86L),
            spec = WindowSpec(title = "before"),
            acceptSurfaceStimulus = { },
            acceptStimulus = { },
        )

        try {
            assertEquals(
                "after",
                peer.updateWindow(
                    AppKitWindowMutationTarget(
                        title = PropertyChange.Set("after"),
                        geometry = AppKitWindowGeometryTarget(
                            contentSize = PropertyChange.Unchanged,
                            minimumSize = PropertyChange.Unchanged,
                            maximumSize = PropertyChange.Unchanged,
                            resizable = PropertyChange.Unchanged,
                        ),
                    ),
                )?.title,
            )
            assertEquals(
                KadreResult.Success("after"),
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    NSWindow(MemorySegment.ofAddress(handle.appKitWindowAddress())).titleAsString()
                },
            )
        } finally {
            peer.close()
        }
    }

    @Test
    fun generatedKffiWindowUpdatesContentConstraintsAndRestoresNativeDefaultsOnMacOs() {
        if (!isMacOsHost()) return

        val port = KffiAppKitWindowPort()
        val defaultPeer = port.prepare(
            id = AppKitWindowPeerId(84L),
            spec = WindowSpec(contentSize = LogicalSize(300.0, 180.0)),
            acceptSurfaceStimulus = { },
            acceptStimulus = { },
        )
        val nativeDefaults = try {
            assertIs<KadreResult.Success<NativeWindowGeometry>>(
                defaultPeer.withDesktopHandle(admitCallback = { true }) { handle ->
                    NSWindow(MemorySegment.ofAddress(handle.appKitWindowAddress()))
                        .readGeneratedNativeGeometry()
                },
            ).value
        } finally {
            defaultPeer.close()
        }
        val peer = port.prepare(
            id = AppKitWindowPeerId(85L),
            spec = WindowSpec(
                contentSize = LogicalSize(420.0, 280.0),
                minimumSize = LogicalSize(320.0, 200.0),
                maximumSize = LogicalSize(640.0, 480.0),
                resizable = true,
            ),
            acceptSurfaceStimulus = { },
            acceptStimulus = { },
        )

        try {
            val unrelatedStyleBits = assertIs<KadreResult.Success<Long>>(
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    NSWindow(MemorySegment.ofAddress(handle.appKitWindowAddress()))
                        .styleMask()
                        .rawValue and NSWindowStyleMask.NSWindowStyleMaskResizable.rawValue.inv()
                },
            ).value

            assertEquals(
                AppKitWindowGeometrySnapshot(
                    contentSize = LogicalSize(520.0, 360.0),
                    minimumSize = LogicalSize(500.0, 340.0),
                    maximumSize = LogicalSize(560.0, 400.0),
                    resizable = false,
                ),
                peer.updateGeometry(
                    AppKitWindowGeometryTarget(
                        contentSize = PropertyChange.Set(LogicalSize(520.0, 360.0)),
                        minimumSize = PropertyChange.Set(LogicalSize(500.0, 340.0)),
                        maximumSize = PropertyChange.Set(LogicalSize(560.0, 400.0)),
                        resizable = PropertyChange.Set(false),
                    ),
                ),
            )
            assertEquals(
                AppKitWindowGeometrySnapshot(
                    contentSize = LogicalSize(520.0, 360.0),
                    minimumSize = null,
                    maximumSize = null,
                    resizable = false,
                ),
                peer.updateGeometry(
                    AppKitWindowGeometryTarget(
                        contentSize = PropertyChange.Unchanged,
                        minimumSize = PropertyChange.Clear,
                        maximumSize = PropertyChange.Clear,
                        resizable = PropertyChange.Unchanged,
                    ),
                ),
            )
            assertEquals(
                KadreResult.Success(
                    NativeWindowGeometry(
                        contentWidth = 520.0,
                        contentHeight = 360.0,
                        minimumWidth = nativeDefaults.minimumWidth,
                        minimumHeight = nativeDefaults.minimumHeight,
                        maximumWidth = nativeDefaults.maximumWidth,
                        maximumHeight = nativeDefaults.maximumHeight,
                        resizable = false,
                    ),
                ),
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    NSWindow(MemorySegment.ofAddress(handle.appKitWindowAddress()))
                        .readGeneratedNativeGeometry()
                },
            )
            assertEquals(
                unrelatedStyleBits,
                assertIs<KadreResult.Success<Long>>(
                    peer.withDesktopHandle(admitCallback = { true }) { handle ->
                        NSWindow(MemorySegment.ofAddress(handle.appKitWindowAddress()))
                            .styleMask()
                            .rawValue and NSWindowStyleMask.NSWindowStyleMaskResizable.rawValue.inv()
                    },
                ).value,
            )
        } finally {
            peer.close()
        }
    }

    @Test
    fun immutableKffiKeyboardObservationPreservesUnknownKeyAndEffectiveModifiers() {
        val observation = NSEventObservation(
            type = NSEventType.NSEventTypeKeyDown,
            modifierFlags = NSEventModifierFlags.NSEventModifierFlagShift +
                NSEventModifierFlags.NSEventModifierFlagCommand,
            position = NSEventObservation.Position(4.0, 8.0),
            details = NSEventObservation.Details.Keyboard(
                keyCode = 0xffff,
                characters = "Ω",
                charactersIgnoringModifiers = "ω",
                isRepeat = false,
            ),
        )

        assertEquals(
            AppKitInput.KeyChanged(
                physicalKey = PhysicalKey.Unidentified("mac:65535"),
                logicalKey = LogicalKey.Character("Ω"),
                location = KeyLocation.Standard,
                keyState = KeyState.Pressed,
                repeat = false,
                modifiers = KeyboardModifiers(setOf(ModifierKey.Shift, ModifierKey.Meta)),
            ),
            observation.toAppKitInput(),
        )
    }

    @Test
    fun immutableKffiKeyboardObservationsMapKeyReleaseAndModifierTransitions() {
        val keyUp = NSEventObservation(
            type = NSEventType.NSEventTypeKeyUp,
            modifierFlags = NSEventModifierFlags(0L),
            position = NSEventObservation.Position(0.0, 0.0),
            details = NSEventObservation.Details.Keyboard(0, "A", "a", isRepeat = true),
        )
        val shiftReleased = NSEventObservation(
            type = NSEventType.NSEventTypeFlagsChanged,
            modifierFlags = NSEventModifierFlags(0L),
            position = NSEventObservation.Position(0.0, 0.0),
            details = NSEventObservation.Details.Keyboard(56, "", "", isRepeat = true),
        )

        assertEquals(
            listOf(
                AppKitInput.KeyChanged(
                    PhysicalKey.Code(0x07, 0x04),
                    LogicalKey.Character("A"),
                    KeyLocation.Standard,
                    KeyState.Released,
                    repeat = false,
                    KeyboardModifiers(emptySet()),
                ),
                AppKitInput.KeyChanged(
                    PhysicalKey.Code(0x07, 0xe1),
                    LogicalKey.Named(org.graphiks.kadre.input.NamedKey.Shift),
                    KeyLocation.Left,
                    KeyState.Released,
                    repeat = false,
                    KeyboardModifiers(emptySet()),
                ),
            ),
            listOf(keyUp.toAppKitInput(), shiftReleased.toAppKitInput()),
        )
    }

    @Test
    fun immutableKffiPointerObservationPreservesPositionDeltaAndPressure() {
        val observation = NSEventObservation(
            type = NSEventType.NSEventTypeMouseMoved,
            modifierFlags = NSEventModifierFlags(0L),
            position = NSEventObservation.Position(4.0, 8.0),
            details = NSEventObservation.Details.Pointer(
                buttonNumber = 0L,
                clickCount = 0L,
                pressure = 0.5f,
                deltaX = -3.5,
                deltaY = 7.25,
            ),
        )

        assertEquals(
            AppKitInput.PointerMoved(
                position = LogicalPoint(4.0, 8.0),
                delta = LogicalDelta(-3.5, 7.25),
                pressure = 0.5,
            ),
            observation.toAppKitInput(),
        )
    }

    @Test
    fun immutableKffiPointerObservationMapsOtherButtonWithoutInventingItsNativeCode() {
        val observation = NSEventObservation(
            type = NSEventType.NSEventTypeOtherMouseDown,
            modifierFlags = NSEventModifierFlags(0L),
            position = NSEventObservation.Position(12.0, 16.0),
            details = NSEventObservation.Details.Pointer(
                buttonNumber = 17L,
                clickCount = 1L,
                pressure = 0.0f,
                deltaX = 0.0,
                deltaY = 0.0,
            ),
        )

        assertEquals(
            AppKitInput.PointerButtonChanged(
                button = PointerButton.Other(17),
                buttonState = PointerButtonState.Pressed,
                position = LogicalPoint(12.0, 16.0),
                pressure = 0.0,
            ),
            observation.toAppKitInput(),
        )
    }

    @Test
    fun enterAndExitInputMappingRequiresOnlyTheEventTypeAndPosition() {
        val entered = NSEventObservation(
            type = NSEventType.NSEventTypeMouseEntered,
            modifierFlags = NSEventModifierFlags(0L),
            position = NSEventObservation.Position(9.0, 13.0),
            details = NSEventObservation.Details.None,
        )
        val exited = entered.copy(type = NSEventType.NSEventTypeMouseExited)

        assertEquals(
            listOf(AppKitInput.PointerEntered(LogicalPoint(9.0, 13.0)), AppKitInput.PointerLeft),
            listOf(entered.toAppKitInput(), exited.toAppKitInput()),
        )
    }

    @Test
    fun typedNsEdgeInsetsMapToKadreLogicalEdgeOrder() {
        assertEquals(
            LogicalInsets(top = 11.0, right = 44.0, bottom = 33.0, left = 22.0),
            NSEdgeInsets(top = 11.0, left = 22.0, bottom = 33.0, right = 44.0).toLogicalInsets(),
        )
    }

    @Test
    fun nativeInitialSnapshotObservesTheContentViewSafeAreaOnMacOs() {
        if (!isMacOsHost()) return

        val port = KffiAppKitWindowPort()
        val peer = port.prepare(
            id = AppKitWindowPeerId(78L),
            spec = WindowSpec(contentSize = LogicalSize(240.0, 135.0)),
            acceptSurfaceStimulus = { },
            acceptStimulus = { },
        )

        try {
            val observed = checkNotNull(peer.initialSurfaceSnapshot).metrics.safeAreaInsets
            val fromPublishedKffiView = peer.withDesktopHandle(admitCallback = { true }) { handle ->
                val appKitHandle = assertIs<RuntimeDesktopNativeWindowHandle.AppKit>(handle)
                NSView(MemorySegment.ofAddress(appKitHandle.nsViewAddress.toLong()))
                    .safeAreaInsets()
                    .toLogicalInsets()
            }
            assertEquals(KadreResult.Success(observed), fromPublishedKffiView)
        } finally {
            peer.close()
        }
    }

    @Test
    fun managedContentViewOverridePublishesEffectiveAppearanceChangeOnMacOs() {
        if (!isMacOsHost()) return

        val peerId = AppKitWindowPeerId(79L)
        val stimuli = mutableListOf<AppKitSurfaceStimulus>()
        val port = KffiAppKitWindowPort()
        val peer = port.prepare(
            id = peerId,
            spec = WindowSpec(contentSize = LogicalSize(240.0, 135.0)),
            acceptSurfaceStimulus = stimuli::add,
            acceptStimulus = { },
        )

        try {
            val initialTheme = checkNotNull(peer.initialSurfaceSnapshot).theme
            val (appearanceName, expectedTheme) = if (initialTheme == SurfaceTheme.Dark) {
                "NSAppearanceNameAqua" to SurfaceTheme.Light
            } else {
                "NSAppearanceNameDarkAqua" to SurfaceTheme.Dark
            }
            val appearance = NSAppearance.appearanceNamed(
                ObjCRuntime.newNSString(Arena.global(), appearanceName),
            )

            assertEquals(
                KadreResult.Success(Unit),
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    val appKitHandle = assertIs<RuntimeDesktopNativeWindowHandle.AppKit>(handle)
                    val view = NSView(MemorySegment.ofAddress(appKitHandle.nsViewAddress.toLong()))
                    view.setAppearance(appearance)
                    view.viewDidChangeEffectiveAppearance()
                },
            )
            assertEquals(
                listOf<AppKitSurfaceStimulus>(
                    AppKitSurfaceStimulus.ThemeChanged(peerId, expectedTheme),
                ),
                stimuli.filterIsInstance<AppKitSurfaceStimulus.ThemeChanged>(),
            )
        } finally {
            peer.close()
        }
    }

    @Test
    fun publishedKffiManagedNsViewAnswersAcceptsFirstResponderThroughBooleanSignatureOnMacOs() {
        if (!isMacOsHost()) return

        val invocations = AtomicInteger()
        val viewClass = ObjCManagedClass.registerOnce(
            superclassName = "NSView",
            methods = mapOf(
                "acceptsFirstResponder" to ObjCMethodSignatures.Boolean,
            ),
        )

        ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
            val viewInstance = viewClass.createInstance {
                onBoolean("acceptsFirstResponder", fallback = false) {
                    invocations.incrementAndGet()
                    true
                }
            }

            try {
                assertTrue(NSView(viewInstance.receiver.ptr).acceptsFirstResponder())
                assertEquals(1, invocations.get())
            } finally {
                viewInstance.close()
            }
        }
    }

    @Test
    fun nativeContentViewBecomesFirstResponderAndRoutesPublishedKeyboardObservationsOnMacOs() {
        if (!isMacOsHost()) return

        val peerId = AppKitWindowPeerId(80L)
        val stimuli = mutableListOf<AppKitSurfaceStimulus>()
        val peer = KffiAppKitWindowPort().prepare(
            id = peerId,
            spec = WindowSpec(contentSize = LogicalSize(240.0, 135.0)),
            acceptSurfaceStimulus = stimuli::add,
            acceptStimulus = { },
        )

        try {
            assertEquals(
                KadreResult.Success(Unit),
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    val appKitHandle = assertIs<RuntimeDesktopNativeWindowHandle.AppKit>(handle)
                    val view = NSView(MemorySegment.ofAddress(appKitHandle.nsViewAddress.toLong()))
                    assertTrue(view.acceptsFirstResponder())
                    assertEquals(
                        appKitHandle.nsViewAddress.toLong(),
                        NSWindow(MemorySegment.ofAddress(appKitHandle.nsWindowAddress.toLong()))
                            .firstResponder().address(),
                    )
                    val event = NSEvent.keyEventWithType_location_modifierFlags_timestamp_windowNumber_context_characters_charactersIgnoringModifiers_isARepeat_keyCode(
                        type = NSEventType.NSEventTypeKeyDown,
                        location = NSPoint(12.5, 4.0),
                        flags = NSEventModifierFlags.NSEventModifierFlagShift,
                        time = 1.0,
                        wNum = 0L,
                        unusedPassNil = MemorySegment.NULL,
                        keys = "A",
                        ukeys = "a",
                        flag = false,
                        code = 0x00,
                    )
                    view.keyDown(event)
                },
            )
            assertEquals(
                listOf(
                    AppKitSurfaceStimulus.InputObservationChanged(peerId, keyboardInstalled = true, pointerInstalled = true),
                    AppKitSurfaceStimulus.KeyChanged(
                        peerId,
                        AppKitInput.KeyChanged(
                            physicalKey = PhysicalKey.Code(0x07, 0x04),
                            logicalKey = LogicalKey.Character("A"),
                            location = KeyLocation.Standard,
                            keyState = KeyState.Pressed,
                            repeat = false,
                            modifiers = KeyboardModifiers(setOf(ModifierKey.Shift)),
                        ),
                    ),
                ),
                stimuli.filterIsInstance<AppKitSurfaceStimulus.InputObservationChanged>() +
                    stimuli.filterIsInstance<AppKitSurfaceStimulus.KeyChanged>(),
            )
        } finally {
            peer.close()
        }
    }

    @Test
    fun nativeContentViewAdvertisesPointerTrackingAndRoutesPointerEventsOnMacOs() {
        if (!isMacOsHost()) return

        val peerId = AppKitWindowPeerId(81L)
        val stimuli = mutableListOf<AppKitSurfaceStimulus>()
        val peer = KffiAppKitWindowPort().prepare(
            id = peerId,
            spec = WindowSpec(contentSize = LogicalSize(240.0, 135.0)),
            acceptSurfaceStimulus = stimuli::add,
            acceptStimulus = { },
        )

        try {
            assertEquals(
                KadreResult.Success(Unit),
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    val appKitHandle = assertIs<RuntimeDesktopNativeWindowHandle.AppKit>(handle)
                    val view = NSView(MemorySegment.ofAddress(appKitHandle.nsViewAddress.toLong()))
                    val event = NSEvent.mouseEventWithType_location_modifierFlags_timestamp_windowNumber_context_eventNumber_clickCount_pressure(
                        type = NSEventType.NSEventTypeLeftMouseDown,
                        location = NSPoint(12.5, 4.0),
                        flags = NSEventModifierFlags(0L),
                        time = 1.0,
                        wNum = 0L,
                        unusedPassNil = MemorySegment.NULL,
                        eNum = 1L,
                        cNum = 1L,
                        pressure = 0.0f,
                    )
                    view.mouseDown(event)
                },
            )
            assertEquals(
                listOf(
                    AppKitSurfaceStimulus.InputObservationChanged(
                        peerId,
                        keyboardInstalled = true,
                        pointerInstalled = true,
                    ),
                ),
                stimuli.filterIsInstance<AppKitSurfaceStimulus.InputObservationChanged>(),
            )
            assertEquals(
                listOf(
                    AppKitSurfaceStimulus.PointerInput(
                        peerId,
                        AppKitInput.PointerButtonChanged(
                            button = PointerButton.Primary,
                            buttonState = PointerButtonState.Pressed,
                            position = LogicalPoint(12.5, 4.0),
                            pressure = 0.0,
                        ),
                    ),
                ),
                stimuli.filterIsInstance<AppKitSurfaceStimulus.PointerInput>(),
            )
        } finally {
            peer.close()
        }
    }

    @Test
    fun nativeInputObserverRevocationStopsKeyboardAndPointerCallbacksWhileTheManagedViewRemainsAliveOnMacOs() {
        if (!isMacOsHost()) return

        val port = KffiAppKitWindowPort()
        val received = mutableListOf<AppKitInput>()
        var window: AppKitNativeWindowOwner? = null
        var view: AppKitNativeViewOwner? = null
        var observer: AppKitNativeInputObserverOwner? = null

        try {
            port.onMainThread {
                window = port.createWindow(WindowSpec(contentSize = LogicalSize(240.0, 135.0)))
                view = port.createContentView(WindowSpec(contentSize = LogicalSize(240.0, 135.0)))
                port.attachContentView(checkNotNull(window), checkNotNull(view))
                port.present(checkNotNull(window))
                observer = port.observeInput(
                    checkNotNull(window),
                    checkNotNull(view),
                    AppKitInputCallbacks(received::add),
                )
                val handle = port.desktopHandle(checkNotNull(window), checkNotNull(view))
                val nativeView = NSView(MemorySegment.ofAddress(handle.nsViewAddress.toLong()))

                nativeView.keyDown(testKeyEvent())
                assertEquals(1, received.size)

                nativeView.mouseDown(testPointerEvent())
                assertEquals(2, received.size)

                checkNotNull(observer).revokeCallbacks()
                nativeView.keyDown(testKeyEvent())
                nativeView.mouseDown(testPointerEvent())
                assertEquals(2, received.size)
            }
        } finally {
            port.onMainThread {
                observer?.close()
                window?.let { nativeWindow ->
                    port.detachContentView(nativeWindow)
                    view?.close()
                    port.closeWindow(nativeWindow)
                    nativeWindow.close()
                }
            }
        }
    }

    @Test
    fun nativeContentViewRevokesPublishedKffiKeyboardRouteBeforePeerCloseReleasesTheViewOnMacOs() {
        if (!isMacOsHost()) return

        val peerId = AppKitWindowPeerId(82L)
        val stimuli = mutableListOf<AppKitSurfaceStimulus>()
        val port = KffiAppKitWindowPort()
        val peer = port.prepare(
            id = peerId,
            spec = WindowSpec(contentSize = LogicalSize(240.0, 135.0)),
            acceptSurfaceStimulus = stimuli::add,
            acceptStimulus = { },
        )
        var retainedView: MemorySegment? = null

        try {
            assertEquals(
                KadreResult.Success(Unit),
                peer.withDesktopHandle(admitCallback = { true }) { handle ->
                    val appKitHandle = assertIs<RuntimeDesktopNativeWindowHandle.AppKit>(handle)
                    retainedView = MemorySegment.ofAddress(appKitHandle.nsViewAddress.toLong())
                    retain(checkNotNull(retainedView))
                    NSView(checkNotNull(retainedView)).keyDown(testKeyEvent())
                },
            )
            assertTrue(stimuli.any { it is AppKitSurfaceStimulus.KeyChanged })
            stimuli.clear()

            peer.close()
            port.onMainThread {
                ObjCRuntime.autoreleasePool {
                    NSView(checkNotNull(retainedView)).keyDown(testKeyEvent())
                }
            }

            assertEquals(emptyList(), stimuli)
        } finally {
            peer.close()
            retainedView?.let { view -> port.onMainThread { release(view) } }
        }
    }

    @Test
    fun publicKffiSurfaceObservationAndRedrawProofCompilesAndClosesOnMacOs() {
        if (!isMacOsHost()) return

        val rect = NSRect(NSPoint(0.0, 0.0), NSSize(320.0, 180.0))
        val style = NSWindowStyleMask.NSWindowStyleMaskTitled +
            NSWindowStyleMask.NSWindowStyleMaskClosable +
            NSWindowStyleMask.NSWindowStyleMaskResizable
        val appearanceChangedCount = AtomicInteger()
        val viewClass = ObjCManagedClass.registerOnce(
            superclassName = "NSView",
            methods = mapOf(
                "viewDidChangeEffectiveAppearance" to ObjCMethodSignatures.Void,
            ),
        )

        ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
            val window = allocateWindow(rect, style)
            val viewInstance = viewClass.createInstance {
                onVoid("viewDidChangeEffectiveAppearance") {
                    appearanceChangedCount.incrementAndGet()
                }
            }
            val view = NSView(viewInstance.receiver.ptr).also { it.setFrame(rect) }
            val center = NSNotificationCenter(NSNotificationCenter.defaultCenter())
            val notificationNames = listOf(
                "NSWindowDidResizeNotification" to window.ptr,
                "NSWindowDidChangeBackingPropertiesNotification" to window.ptr,
                "NSWindowDidBecomeKeyNotification" to window.ptr,
                "NSWindowDidResignKeyNotification" to window.ptr,
                "NSWindowDidOrderOnScreenNotification" to window.ptr,
                "NSWindowDidOrderOffScreenNotification" to window.ptr,
                "NSWindowDidMiniaturizeNotification" to window.ptr,
                "NSWindowDidDeminiaturizeNotification" to window.ptr,
                "NSWindowDidChangeOcclusionStateNotification" to window.ptr,
            )
            val observations = notificationNames.map { (name, objectFilter) ->
                center.observe(
                    name = ObjCRuntime.newNSString(Arena.global(), name),
                    objectFilter = objectFilter,
                ) { }
            }

            try {
                window.setReleasedWhenClosed(false)
                window.setContentView(view.ptr)

                val contentSize = view.bounds().size
                assertEquals(320.0, contentSize.width)
                assertEquals(180.0, contentSize.height)
                assertTrue(window.backingScaleFactor() > 0.0)
                val windowAppearance = NSAppearance(window.effectiveAppearance())
                val viewAppearance = NSAppearance(view.effectiveAppearance())
                assertTrue(ObjCRuntime.toJavaString(windowAppearance.name()).isNotEmpty())
                assertTrue(ObjCRuntime.toJavaString(viewAppearance.name()).isNotEmpty())

                val appearanceCallbacksBeforeExplicitInvocation = appearanceChangedCount.get()
                view.viewDidChangeEffectiveAppearance()
                assertEquals(appearanceCallbacksBeforeExplicitInvocation + 1, appearanceChangedCount.get())
                view.setNeedsDisplay(true)
                assertTrue(view.needsDisplay())
            } finally {
                observations.asReversed().forEach(AutoCloseable::close)
                window.setContentView(MemorySegment.NULL)
                window.close()
                viewInstance.close()
                release(window.ptr)
            }
        }
    }

    @Test
    fun kffiDelegateOwnerRetainsItselfOnceAndCannotReleaseAfterFailedDetachment() {
        val retained = mutableListOf<KffiDelegateOwner>()
        var revokeCount = 0
        var releaseCount = 0
        val owner = KffiDelegateOwner(
            receiver = MemorySegment.NULL,
            revokeAdmission = { revokeCount += 1 },
            closeReceiver = { releaseCount += 1 },
            retainForProcessLifetime = { retained += it },
        )

        owner.revokeCallbacks()
        owner.retainAfterFailedDetachment()
        owner.retainAfterFailedDetachment()
        owner.close()
        owner.close()

        assertEquals(1, revokeCount)
        assertEquals(0, releaseCount)
        assertEquals(1, retained.size)
        assertSame(owner, retained.single())
    }

    @Test
    fun kffiDelegateOwnerReleasesItsReceiverOnceWhileOwned() {
        val retained = mutableListOf<KffiDelegateOwner>()
        var revokeCount = 0
        var releaseCount = 0
        val owner = KffiDelegateOwner(
            receiver = MemorySegment.NULL,
            revokeAdmission = { revokeCount += 1 },
            closeReceiver = { releaseCount += 1 },
            retainForProcessLifetime = { retained += it },
        )

        owner.close()
        owner.close()
        owner.retainAfterFailedDetachment()

        assertEquals(1, revokeCount)
        assertEquals(1, releaseCount)
        assertEquals(emptyList(), retained)
    }

    @Test
    fun kffiWindowSurfaceCompilesAndClosesOnMacOs() {
        if (!isMacOsHost()) return

        val spec = WindowSpec(contentSize = LogicalSize(320.0, 180.0))
        val shouldCloseCount = AtomicInteger()
        val willCloseCount = AtomicInteger()
        val delegateClass = ObjCManagedClass.registerOnce(
            protocols = setOf("NSWindowDelegate"),
            methods = mapOf(
                "windowShouldClose:" to ObjCMethodSignatures.BooleanObject,
                "windowWillClose:" to ObjCMethodSignatures.VoidObject,
            ),
        )

        ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
            val rect = NSRect(
                NSPoint(0.0, 0.0),
                NSSize(spec.contentSize.width, spec.contentSize.height),
            )
            val style = NSWindowStyleMask.NSWindowStyleMaskTitled +
                NSWindowStyleMask.NSWindowStyleMaskClosable +
                NSWindowStyleMask.NSWindowStyleMaskResizable
            val window = allocateWindow(rect, style)
            val view = allocateView(rect)
            val delegate = delegateClass.createInstance {
                onBooleanObject("windowShouldClose:", fallback = false) {
                    shouldCloseCount.incrementAndGet()
                    false
                }
                onVoidObject("windowWillClose:") {
                    willCloseCount.incrementAndGet()
                }
            }

            try {
                window.setReleasedWhenClosed(false)
                window.setContentView(view.ptr)
                window.setDelegate(delegate.receiver.ptr)
                window.makeKeyAndOrderFront(MemorySegment.NULL)

                window.performClose(MemorySegment.NULL)
                assertEquals(1, shouldCloseCount.get())
                assertEquals(0, willCloseCount.get())

                window.close()
                assertEquals(1, willCloseCount.get())
            } finally {
                window.setDelegate(MemorySegment.NULL)
                delegate.close()
                window.setContentView(MemorySegment.NULL)
                release(view.ptr)
                release(window.ptr)
            }
        }
    }

    @Test
    fun kffiPortPreparesAndClosesARealWindowPeerOnMacOs() {
        if (!isMacOsHost()) return

        val peerId = AppKitWindowPeerId(73L)
        val port = KffiAppKitWindowPort()
        val peer = port.prepare(
            peerId,
            WindowSpec(contentSize = LogicalSize(240.0, 135.0)),
        ) { }

        assertEquals(peerId, peer.id)
        assertEquals(
            KadreResult.Success(Unit),
            peer.withDesktopHandle(admitCallback = { true }) { handle ->
                assertTrue(port.isMainThread())
                val appKitHandle = assertIs<RuntimeDesktopNativeWindowHandle.AppKit>(handle)
                assertTrue(appKitHandle.nsWindowAddress != 0uL)
                assertTrue(appKitHandle.nsViewAddress != 0uL)
            },
        )
        peer.close()
    }

    @Test
    fun windowOwnerIsReleasedWhenConfigurationFails() {
        if (!isMacOsHost()) return

        val owner = CountingWindowOwner()
        val expected = IllegalStateException("window configuration")
        val port = KffiAppKitWindowPort(
            createUnconfiguredWindow = { owner },
            configureWindow = { _, _ -> throw expected },
        )

        val actual = assertFailsWith<IllegalStateException> {
            port.createWindow(WindowSpec())
        }

        assertSame(expected, actual)
        assertEquals(1, owner.closeCount)
    }

    private fun allocateWindow(rect: NSRect, style: NSWindowStyleMask): NSWindow {
        val allocated = allocate("NSWindow")
        val initialized = NSWindow(allocated).initWithContentRect_styleMask_backing_defer(
            rect,
            style,
            NSBackingStoreType.NSBackingStoreBuffered,
            false,
        )
        check(initialized != MemorySegment.NULL) { "NSWindow initialization failed" }
        return NSWindow(initialized)
    }

    private fun allocateView(rect: NSRect): NSView {
        val initialized = NSView(allocate("NSView")).initWithFrame(rect)
        check(initialized != MemorySegment.NULL) { "NSView initialization failed" }
        return NSView(initialized)
    }

    private fun allocate(className: String): MemorySegment = ObjCRuntime.msgSend(
        ValueLayout.ADDRESS,
        ObjCRuntime.getClass(className),
        ObjCRuntime.sel("alloc"),
    ) as MemorySegment

    private fun release(receiver: MemorySegment) {
        ObjCRuntime.msgSend(null, receiver, ObjCRuntime.sel("release"))
    }

    private fun retain(receiver: MemorySegment) {
        ObjCRuntime.msgSend(ValueLayout.ADDRESS, receiver, ObjCRuntime.sel("retain"))
    }

    private fun testKeyEvent(): MemorySegment = NSEvent.keyEventWithType_location_modifierFlags_timestamp_windowNumber_context_characters_charactersIgnoringModifiers_isARepeat_keyCode(
        type = NSEventType.NSEventTypeKeyDown,
        location = NSPoint(12.5, 4.0),
        flags = NSEventModifierFlags.NSEventModifierFlagShift,
        time = 1.0,
        wNum = 0L,
        unusedPassNil = MemorySegment.NULL,
        keys = "A",
        ukeys = "a",
        flag = false,
        code = 0x00,
    )

    private fun testPointerEvent(): MemorySegment = NSEvent.mouseEventWithType_location_modifierFlags_timestamp_windowNumber_context_eventNumber_clickCount_pressure(
        type = NSEventType.NSEventTypeLeftMouseDown,
        location = NSPoint(12.5, 4.0),
        flags = NSEventModifierFlags(0L),
        time = 1.0,
        wNum = 0L,
        unusedPassNil = MemorySegment.NULL,
        eNum = 1L,
        cNum = 1L,
        pressure = 0.0f,
    )
}

private class CountingWindowOwner : AppKitNativeWindowOwner {
    var closeCount: Int = 0
        private set

    override fun close() {
        closeCount += 1
    }
}

private fun RuntimeDesktopNativeWindowHandle.appKitWindowAddress(): Long =
    assertIs<RuntimeDesktopNativeWindowHandle.AppKit>(this).nsWindowAddress.toLong()

private data class NativeWindowGeometry(
    val contentWidth: Double,
    val contentHeight: Double,
    val minimumWidth: Double,
    val minimumHeight: Double,
    val maximumWidth: Double,
    val maximumHeight: Double,
    val resizable: Boolean,
)

private fun NSWindow.readGeneratedNativeGeometry(): NativeWindowGeometry {
    val contentSize = contentRectForFrameRect(frame()).size
    val minimumSize = contentMinSize()
    val maximumSize = contentMaxSize()
    return NativeWindowGeometry(
        contentWidth = contentSize.width,
        contentHeight = contentSize.height,
        minimumWidth = minimumSize.width,
        minimumHeight = minimumSize.height,
        maximumWidth = maximumSize.width,
        maximumHeight = maximumSize.height,
        resizable = styleMask().contains(NSWindowStyleMask.NSWindowStyleMaskResizable),
    )
}

private fun isMacOsHost(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}
