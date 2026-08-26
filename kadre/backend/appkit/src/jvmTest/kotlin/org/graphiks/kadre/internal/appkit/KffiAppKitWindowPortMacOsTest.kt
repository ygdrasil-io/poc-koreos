package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSBackingStoreType
import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSRect
import org.graphiks.kffi.objc.NSSize
import org.graphiks.kffi.objc.NSView
import org.graphiks.kffi.objc.NSWindow
import org.graphiks.kffi.objc.NSWindowStyleMask
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.managed.ObjCManagedClass
import org.graphiks.kffi.objc.managed.ObjCMethodSignatures
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KffiAppKitWindowPortMacOsTest {
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
        val peer = KffiAppKitWindowPort().prepare(
            peerId,
            WindowSpec(contentSize = LogicalSize(240.0, 135.0)),
        ) { }

        assertEquals(peerId, peer.id)
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
