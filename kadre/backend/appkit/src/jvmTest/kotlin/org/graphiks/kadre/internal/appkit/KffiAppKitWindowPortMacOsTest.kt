package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSAppearance
import org.graphiks.kffi.objc.NSBackingStoreType
import org.graphiks.kffi.objc.NSEdgeInsets
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
}

private class CountingWindowOwner : AppKitNativeWindowOwner {
    var closeCount: Int = 0
        private set

    override fun close() {
        closeCount += 1
    }
}

private fun isMacOsHost(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}
