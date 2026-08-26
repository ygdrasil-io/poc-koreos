package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSBackingStoreType
import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSRect
import org.graphiks.kffi.objc.NSSize
import org.graphiks.kffi.objc.NSThread
import org.graphiks.kffi.objc.NSView
import org.graphiks.kffi.objc.NSWindow
import org.graphiks.kffi.objc.NSWindowStyleMask
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.managed.ObjCManagedClass
import org.graphiks.kffi.objc.managed.ObjCMethodSignatures
import org.graphiks.kffi.objc.performSelectorOnMainThread_withObject_waitUntilDone
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Public-KFFI-backed AppKit port. Native addresses remain private to this implementation. */
internal class KffiAppKitWindowPort(
    private val createUnconfiguredWindow: (WindowSpec) -> AppKitNativeWindowOwner =
        ::createKffiUnconfiguredWindow,
    private val configureWindow: (AppKitNativeWindowOwner, WindowSpec) -> Unit =
        ::configureKffiWindow,
) : AppKitNativeWindowPort {
    fun prepare(
        id: AppKitWindowPeerId,
        spec: WindowSpec,
        acceptStimulus: (AppKitWindowStimulus) -> Unit,
    ): AppKitWindowPeer = AppKitWindowPeer.prepare(id, spec, this, acceptStimulus)

    override fun isMainThread(): Boolean = NSThread.isMainThread()

    override fun <T> onMainThread(block: () -> T): T = KffiAppKitMainThread.call(block)

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner {
        requireMainThread()
        val owner = createUnconfiguredWindow(spec)
        return try {
            configureWindow(owner, spec)
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

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner {
        requireMainThread()
        val initialized = NSView(allocate("NSView")).initWithFrame(contentRect(spec))
        check(initialized != MemorySegment.NULL) { "NSView initialization failed" }
        return KffiViewOwner(NSView(initialized))
    }

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner {
        requireMainThread()
        val admission = KffiDelegateAdmission(callbacks)
        val instance = windowDelegateClass.createInstance {
            onBooleanObject(WINDOW_SHOULD_CLOSE, fallback = false) {
                admission.windowShouldClose()
            }
            onVoidObject(WINDOW_WILL_CLOSE) {
                admission.windowWillClose()
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

        val windowDelegateClass: ObjCManagedClass by lazy {
            ObjCManagedClass.registerOnce(
                protocols = setOf("NSWindowDelegate"),
                methods = mapOf(
                    WINDOW_SHOULD_CLOSE to ObjCMethodSignatures.BooleanObject,
                    WINDOW_WILL_CLOSE to ObjCMethodSignatures.VoidObject,
                ),
            )
        }

    }
}

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
    var style = when (spec.decorations) {
        WindowDecorations.System -> NSWindowStyleMask.NSWindowStyleMaskTitled
        WindowDecorations.Borderless -> NSWindowStyleMask.NSWindowStyleMaskBorderless
    }
    if (spec.decorations == WindowDecorations.System) {
        style += when (spec.systemButtons) {
            WindowSystemButtons.All -> NSWindowStyleMask.NSWindowStyleMaskClosable +
                NSWindowStyleMask.NSWindowStyleMaskMiniaturizable
            WindowSystemButtons.CloseOnly -> NSWindowStyleMask.NSWindowStyleMaskClosable
            WindowSystemButtons.None -> NSWindowStyleMask.NSWindowStyleMaskBorderless
        }
    }
    if (spec.resizable) style += NSWindowStyleMask.NSWindowStyleMaskResizable
    return style
}

private fun configureKffiWindow(owner: AppKitNativeWindowOwner, spec: WindowSpec) {
    owner.kffiWindow().apply {
        setReleasedWhenClosed(false)
        setTitle(spec.title)
    }
}

private class KffiWindowOwner(
    val window: NSWindow,
) : AppKitNativeWindowOwner {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release(window.ptr)
    }
}

private class KffiViewOwner(
    val view: NSView,
) : AppKitNativeViewOwner {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release(view.ptr)
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

    fun revoke() {
        accepting.set(false)
    }
}

private fun AppKitNativeWindowOwner.kffiWindow(): NSWindow =
    (this as? KffiWindowOwner)?.window ?: error("foreign AppKit window owner")

private fun AppKitNativeViewOwner.kffiView(): NSView =
    (this as? KffiViewOwner)?.view ?: error("foreign AppKit view owner")

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
