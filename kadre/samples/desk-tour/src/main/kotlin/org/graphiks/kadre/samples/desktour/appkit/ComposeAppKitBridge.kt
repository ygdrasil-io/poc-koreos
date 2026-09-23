package org.graphiks.kadre.samples.desktour.appkit

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton as ComposePointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerId
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.platform.desktop.DesktopNativeWindowHandle
import org.graphiks.kadre.platform.desktop.withDesktopHandle
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.Window
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.ObjCSubclassing
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.SurfaceProps
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.GroupLayout
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles

internal interface ComposeMount {
    val activity: String
    fun updateSurface(state: SurfaceState)
    fun surfaceEvent(event: SurfaceEvent): KadreResult<Unit>
    fun dispatch(event: InputEvent)
    suspend fun close()
}

@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
internal suspend fun Window.mountComposeAppKit(
    scope: CoroutineScope,
    content: @Composable () -> Unit,
): KadreResult<ComposeMount> {
    var candidate: ComposeAppKitMount? = null
    return try {
        // Finish the admitted lease before cancellation can hand off rollback. Only the
        // owned mount escapes this callback, never the root handle/address.
        val result = withContext(NonCancellable + Dispatchers.Default) {
            when (val leased = withDesktopHandle { handle ->
                runCatching<KadreResult<ComposeMount>> {
                    when (handle) {
                        is DesktopNativeWindowHandle.AppKit -> {
                            val mount = ComposeAppKitMount(
                                scope, surface::requestRedraw, surface.state.value, surface.input.state.value,
                            )
                            candidate = mount
                            mount.initialize(handle.nsViewAddress, content)
                            KadreResult.Success(mount)
                        }
                        else -> KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess))
                    }
                }
            }) {
                is KadreResult.Success -> leased.value.getOrThrow()
                is KadreResult.Failure -> leased
            }
        }
        if (result is KadreResult.Failure && candidate != null) throw KadreException(result.reason)
        result
    } catch (failure: Throwable) {
        try {
            // The lease has returned. Rollback owns only child/renderer resources and
            // waits off the AppKit thread, including when the original caller is cancelled.
            withContext(NonCancellable + Dispatchers.Default) { candidate?.rollback() }
        } catch (cleanup: Throwable) {
            if (failure !== cleanup) failure.addSuppressed(cleanup)
        }
        if (failure is CancellationException) throw failure
        nativeFailure("mount", failure)
    }
}

/** Only the owned child and GPU resources escape create(); the root address never does. */
private class ComposeAppKitMount(
    private val scope: CoroutineScope,
    private val requestRedraw: () -> KadreResult<Unit>,
    initialSurface: SurfaceState,
    initialInput: SurfaceInputState,
) : ComposeMount {
    private val appKit = AppKitMainThread()
    private val lifecycle = ComposeMountLifecycle()
    private val work = Job(scope.coroutineContext[Job])
    private val invalidations = Channel<Unit>(Channel.CONFLATED)
    private val frameClock = BroadcastFrameClock { invalidations.trySend(Unit) }
    // Only accessed on AppKitMainThread; producers may signal the channel from any thread.
    private var pendingRedraw = false
    private var surfaceState = initialSurface
    private var child = MemorySegment.NULL
    private var layer = MemorySegment.NULL
    private var device = MemorySegment.NULL
    private var queue = MemorySegment.NULL
    private var arena: Arena? = null
    private var context: DirectContext? = null
    private var scene: ComposeScene? = null
    private var textInput: PlatformTextInputMethodRequest? = null
    private val windowInfo = object : WindowInfo {
        override var isWindowFocused by mutableStateOf(initialSurface.focus == SurfaceFocus.Focused)
        override var containerSize by mutableStateOf(initialSurface.pixelSize())
        override var keyboardModifiers by mutableStateOf(initialInput.modifiers.composeModifiers())
    }
    private val buttons = initialInput.pointers.associate { it.id to it.pressedButtons }.toMutableMap()
    private var pointer: ObservedPointer? = initialInput.pointers.firstOrNull { it.kind.isMouse() && it.position != null }
        ?.let { ObservedPointer(it.id, it.kind, checkNotNull(it.position)) }
    @Volatile
    override var activity: String = "Waiting for input"
        private set

    fun initialize(rootViewAddress: ULong, content: @Composable () -> Unit) {
        requireMainThread()
        ObjCRuntime.autoreleasePool {
            arena = Arena.ofConfined()
            SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/QuartzCore.framework/QuartzCore", checkNotNull(arena),
            )
            // A hit-test-transparent child cannot take responder or drop routing from Kadre.
            child = newObject(RendererView.type)
            layer = newObject(ObjCRuntime.getClass("CAMetalLayer"))
            send(child, "setLayer:", layer)
            send(child, "setWantsLayer:", true)
            send(child, "setAutoresizingMask:", 18L) // NSViewWidthSizable | NSViewHeightSizable
            val metal = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/Metal.framework/Metal", checkNotNull(arena),
            )
            device = Linker.nativeLinker().downcallHandle(
                metal.find("MTLCreateSystemDefaultDevice").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS),
            ).invokeExact() as MemorySegment
            check(device != MemorySegment.NULL) { "No Metal device available" }
            send(layer, "setDevice:", device)
            send(layer, "setPixelFormat:", 80L) // MTLPixelFormatBGRA8Unorm
            send(layer, "setFramebufferOnly:", false)
            queue = address(device, "newCommandQueue")
            check(queue != MemorySegment.NULL) { "Metal command queue creation failed" }
            context = DirectContext.makeMetal(device.address(), queue.address())
            scene = CanvasLayersComposeScene(
                density = Density(surfaceState.scaleFactor.toFloat()),
                size = surfaceState.pixelSize(),
                // Kadre supplies the parent job, but its dispatcher may be Dispatchers.Default.
                coroutineContext = scope.coroutineContext + work + frameClock + appKit.dispatcher,
                platformContext = object : PlatformContext.Empty() {
                    override val windowInfo: WindowInfo get() = this@ComposeAppKitMount.windowInfo
                    override suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing {
                        textInput = request
                        try { awaitCancellation() } finally {
                            if (textInput === request) textInput = null
                        }
                    }
                },
                invalidate = { invalidations.trySend(Unit) },
            )
            applyMetrics(surfaceState)
            checkNotNull(scene).setContent(content)
            // This is the only use of the borrowed root pointer, within the scoped callback.
            send(MemorySegment.ofAddress(rootViewAddress.toLong()), "addSubview:", child)
            lifecycle.markMounted()
        }
        CoroutineScope(scope.coroutineContext + work + appKit.dispatcher).launch {
            for (ignored in invalidations) {
                requireMainThread()
                if (lifecycle.state == ComposeMountState.Mounted && !pendingRedraw) {
                    pendingRedraw = true
                    when (val result = requestRedraw()) {
                        is KadreResult.Success -> Unit
                        is KadreResult.Failure -> {
                            pendingRedraw = false
                            throw KadreException(result.reason)
                        }
                    }
                }
            }
        }
    }

    override fun updateSurface(state: SurfaceState) = appKit.call {
        updateSurfaceOnMainThread(state)
    }

    private fun updateSurfaceOnMainThread(state: SurfaceState) {
        requireMainThread()
        if (lifecycle.state != ComposeMountState.Mounted) return
        if (state.revision.value < surfaceState.revision.value) return
        if (state.physicalSize != surfaceState.physicalSize ||
            state.logicalSize != surfaceState.logicalSize || state.scaleFactor != surfaceState.scaleFactor
        ) applyMetrics(state)
        val lostFocus = surfaceState.focus == SurfaceFocus.Focused && state.focus == SurfaceFocus.Unfocused
        surfaceState = state
        windowInfo.isWindowFocused = state.focus == SurfaceFocus.Focused
        if (lostFocus) {
            buttons.clear()
            pointer = null
            // Cancels Compose gestures on actual focus loss; does not manufacture input.
            scene?.cancelPointerInput()
        }
    }

    private fun applyMetrics(state: SurfaceState) {
        scene?.density = Density(state.scaleFactor.toFloat())
        scene?.size = state.pixelSize()
        windowInfo.containerSize = state.pixelSize()
        structMessage(child, "setFrame:", RECT, 0.0, 0.0, state.logicalSize.width, state.logicalSize.height)
        send(layer, "setContentsScale:", state.scaleFactor)
        structMessage(layer, "setDrawableSize:", SIZE, state.physicalSize.width.toDouble(), state.physicalSize.height.toDouble())
    }

    override fun surfaceEvent(event: SurfaceEvent): KadreResult<Unit> = appKit.call {
        surfaceEventOnMainThread(event)
    }

    private fun surfaceEventOnMainThread(event: SurfaceEvent): KadreResult<Unit> {
        requireMainThread()
        if (lifecycle.state != ComposeMountState.Mounted) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        }
        return try {
            when (event) {
                is SurfaceEvent.MetricsChanged -> updateSurfaceOnMainThread(event.state)
                is SurfaceEvent.FocusChanged -> updateSurfaceOnMainThread(event.state)
                is SurfaceEvent.VisibilityChanged -> updateSurfaceOnMainThread(event.state)
                is SurfaceEvent.AppearanceChanged -> updateSurfaceOnMainThread(event.state)
                is SurfaceEvent.RedrawRequested -> {
                    pendingRedraw = false
                    if (surfaceState.attachment == SurfaceAttachmentState.Attached &&
                        surfaceState.visibility == SurfaceVisibility.Visible &&
                        surfaceState.physicalSize.width > 0 && surfaceState.physicalSize.height > 0
                    ) renderFrame()
                }
            }
            KadreResult.Success(Unit)
        } catch (failure: Exception) {
            nativeFailure("render", failure)
        }
    }

    private fun renderFrame() = ObjCRuntime.autoreleasePool {
        val drawable = address(layer, "nextDrawable")
        check(drawable != MemorySegment.NULL) { "Metal drawable unavailable" }
        val texture = address(drawable, "texture")
        check(texture != MemorySegment.NULL) { "Metal drawable has no texture" }
        val width = long(texture, "width").toInt()
        val height = long(texture, "height").toInt()
        check(width > 0 && height > 0) { "Metal drawable has empty dimensions" }
        BackendRenderTarget.makeMetal(width, height, texture.address()).use { target ->
            // sRGB is Skia's shared, unmanaged singleton; this mount must not close it.
            checkNotNull(Surface.makeFromBackendRenderTarget(
                checkNotNull(context), target, SurfaceOrigin.TOP_LEFT,
                SurfaceColorFormat.BGRA_8888, ColorSpace.sRGB, SurfaceProps(),
            )) { "Skia Metal surface creation failed" }.use { targetSurface ->
                Snapshot.sendApplyNotifications()
                val now = System.nanoTime()
                frameClock.sendFrame(now)
                checkNotNull(scene).render(targetSurface.canvas.asComposeCanvas(), now)
                targetSurface.flushAndSubmit()
                val command = address(queue, "commandBuffer")
                check(command != MemorySegment.NULL) { "Metal command buffer unavailable" }
                send(command, "presentDrawable:", drawable)
                send(command, "commit")
            }
        }
    }

    override fun dispatch(event: InputEvent) = appKit.call {
        dispatchOnMainThread(event)
    }

    private fun dispatchOnMainThread(event: InputEvent) {
        requireMainThread()
        if (lifecycle.state != ComposeMountState.Mounted) return
        val handled = when (event) {
            is InputEvent.Key -> dispatchKey(event)
            is InputEvent.PointerMoved -> dispatchPointer(event, event.pointerId, event.kind, event.position, PointerEventType.Move)
            is InputEvent.PointerEntered -> dispatchPointer(event, event.pointerId, event.kind, event.position, PointerEventType.Enter)
            is InputEvent.PointerLeft -> event.lastPosition?.let {
                dispatchPointer(event, event.pointerId, event.kind, it, PointerEventType.Exit)
            } ?: false
            is InputEvent.PointerButtonChanged -> {
                val button = event.button.composeButton()
                if (!event.kind.isMouse()) false else {
                    val previous = buttons[event.pointerId].orEmpty()
                    buttons[event.pointerId] = if (event.buttonState == PointerButtonState.Pressed) {
                        previous + event.button
                    } else previous - event.button
                    if (button == null) false else dispatchPointer(event, event.pointerId, event.kind, event.position,
                        if (event.buttonState == PointerButtonState.Pressed) PointerEventType.Press else PointerEventType.Release,
                        button = button)
                }
            }
            is InputEvent.Scrolled -> {
                // Scrolled has no position in Kadre's public payload: use only a real prior observation.
                val observed = pointer
                if (observed == null) false else {
                    val delta = when (val value = event.delta) {
                        // Compose 1.11 MacOSCocoaConfig applies 10.dp.toPx() to wheel units.
                        // Normalize logical deltas here so density is applied exactly once there.
                        is ScrollDelta.Logical -> Offset((value.x / 10.0).toFloat(), (value.y / 10.0).toFloat())
                        is ScrollDelta.Lines -> Offset(value.x.toFloat(), value.y.toFloat())
                    }
                    dispatchPointer(event, observed.id, observed.kind, observed.position, PointerEventType.Scroll, delta)
                }
            }
            else -> false
        }
        val detail = if (event is InputEvent.PointerButtonChanged) " ${event.button}" else ""
        activity = "${event::class.simpleName}$detail: ${if (handled) "forwarded" else "unhandled"} (${event.stateRevision})"
    }

    private fun dispatchKey(event: InputEvent.Key): Boolean {
        windowInfo.keyboardModifiers = event.modifiers.composeModifiers()
        val key = event.logicalKey.composeKey() ?: return false
        val characters = (event.logicalKey as? LogicalKey.Character)?.value
        val consumed = checkNotNull(scene).sendKeyEvent(KeyEvent(
            key = key,
            type = if (event.keyState == KeyState.Pressed) KeyEventType.KeyDown else KeyEventType.KeyUp,
            codePoint = characters?.codePointAt(0) ?: 0,
            isCtrlPressed = ModifierKey.Control in event.modifiers.pressed,
            isMetaPressed = ModifierKey.Meta in event.modifiers.pressed,
            isAltPressed = ModifierKey.Alt in event.modifiers.pressed,
            isShiftPressed = ModifierKey.Shift in event.modifiers.pressed,
            nativeEvent = event,
        ))
        // Desktop TextField expects AWT KEY_TYPED. Instead, feed its active edit callback from
        // the real Kadre logical character. This is ordinary text entry, not an IME bridge.
        if (!consumed && event.keyState == KeyState.Pressed && windowInfo.isWindowFocused &&
            ModifierKey.Control !in event.modifiers.pressed && ModifierKey.Meta !in event.modifiers.pressed
        ) {
            val text = characters ?: if (event.logicalKey == LogicalKey.Named(NamedKey.Space)) " " else null
            val input = textInput
            if (input != null && text != null && text.codePoints().noneMatch(Character::isISOControl)) {
                input.editText { commitText(text, 1) }
                return true
            }
        }
        return consumed
    }

    private fun dispatchPointer(
        event: InputEvent, id: PointerId, kind: PointerKind, position: LogicalPoint,
        type: PointerEventType, delta: Offset = Offset.Zero, button: ComposePointerButton? = null,
    ): Boolean {
        if (!kind.isMouse()) return false
        // Keep the source point unconverted, including for later scrolls after a resize.
        pointer = if (type == PointerEventType.Exit) null else ObservedPointer(id, kind, position)
        val pressed = buttons[id].orEmpty()
        if (pressed.any { it.composeButton() == null }) return false
        checkNotNull(scene).sendPointerEvent(
            eventType = type,
            // All supported pointer events reach this single bottom-left → top-left boundary.
            position = appKitPointToCompose(position, surfaceState.logicalSize.height, surfaceState.scaleFactor),
            scrollDelta = delta,
            timeMillis = event.stamp.timestamp.sinceStart.inWholeMilliseconds,
            type = PointerType.Mouse,
            buttons = PointerButtons(
                isPrimaryPressed = PointerButton.Primary in pressed,
                isSecondaryPressed = PointerButton.Secondary in pressed,
                isTertiaryPressed = PointerButton.Auxiliary in pressed,
                isBackPressed = PointerButton.Back in pressed,
                isForwardPressed = PointerButton.Forward in pressed,
            ),
            keyboardModifiers = windowInfo.keyboardModifiers,
            nativeEvent = event,
            button = button,
        )
        return true
    }

    override suspend fun close() = appKit.shutdown(work, ::closeOnMainThread)

    suspend fun rollback() = appKit.shutdown(work) {
        requireMainThread()
        if (lifecycle.state == ComposeMountState.New) {
            lifecycle.markFailed()
            disposeOwnedResources()
        } else {
            closeOnMainThread()
        }
    }

    private fun closeOnMainThread() {
        if (lifecycle.state == ComposeMountState.Closing || lifecycle.state == ComposeMountState.Closed ||
            lifecycle.state == ComposeMountState.Failed
        ) return
        requireMainThread()
        lifecycle.beginClose()
        try { disposeOwnedResources() } finally { lifecycle.markClosed() }
    }

    private fun disposeOwnedResources() {
        // Attempt every release, but preserve all failures for the caller.
        var problem: Throwable? = null
        fun release(action: () -> Unit) {
            try { action() } catch (failure: Throwable) {
                if (problem == null) problem = failure else if (problem !== failure) problem.addSuppressed(failure)
            }
        }
        release { work.cancel() }
        release { invalidations.close() }
        release { scene?.close() }
        scene = null
        textInput = null
        release { context?.close() }
        context = null
        release { if (queue != MemorySegment.NULL) send(queue, "release") }
        queue = MemorySegment.NULL
        release { if (device != MemorySegment.NULL) send(device, "release") }
        device = MemorySegment.NULL
        // Only owned objects are used here, even if Kadre already detached its content view.
        release { if (child != MemorySegment.NULL) send(child, "removeFromSuperview") }
        release { if (child != MemorySegment.NULL) send(child, "setLayer:", MemorySegment.NULL) }
        release { if (layer != MemorySegment.NULL) send(layer, "release") }
        layer = MemorySegment.NULL
        release { if (child != MemorySegment.NULL) send(child, "release") }
        child = MemorySegment.NULL
        release { arena?.close() }
        arena = null
        problem?.let { throw it }
    }

}

private data class ObservedPointer(val id: PointerId, val kind: PointerKind, val position: LogicalPoint)

private fun SurfaceState.pixelSize() = IntSize(physicalSize.width, physicalSize.height)
private fun PointerKind.isMouse() = this == PointerKind.Mouse || this == PointerKind.Touchpad
private fun PointerButton.composeButton(): ComposePointerButton? = when (this) {
    PointerButton.Primary -> ComposePointerButton.Primary
    PointerButton.Secondary -> ComposePointerButton.Secondary
    PointerButton.Auxiliary -> ComposePointerButton.Tertiary
    PointerButton.Back -> ComposePointerButton.Back
    PointerButton.Forward -> ComposePointerButton.Forward
    else -> null
}
private fun KeyboardModifiers.composeModifiers() = PointerKeyboardModifiers(
    isCtrlPressed = ModifierKey.Control in pressed, isMetaPressed = ModifierKey.Meta in pressed,
    isAltPressed = ModifierKey.Alt in pressed, isShiftPressed = ModifierKey.Shift in pressed,
    isCapsLockOn = ModifierKey.CapsLock in pressed, isNumLockOn = ModifierKey.NumLock in pressed,
)
private fun LogicalKey.composeKey(): Key? = when (this) {
    is LogicalKey.Character -> when (value.lowercase()) {
        "a" -> Key.A; "b" -> Key.B; "c" -> Key.C; "d" -> Key.D; "e" -> Key.E
        "f" -> Key.F; "g" -> Key.G; "h" -> Key.H; "i" -> Key.I; "j" -> Key.J
        "k" -> Key.K; "l" -> Key.L; "m" -> Key.M; "n" -> Key.N; "o" -> Key.O
        "p" -> Key.P; "q" -> Key.Q; "r" -> Key.R; "s" -> Key.S; "t" -> Key.T
        "u" -> Key.U; "v" -> Key.V; "w" -> Key.W; "x" -> Key.X; "y" -> Key.Y; "z" -> Key.Z
        else -> Key.Unknown
    }
    is LogicalKey.Named -> when (value) {
        NamedKey.Enter -> Key.Enter; NamedKey.Tab -> Key.Tab; NamedKey.Space -> Key.Spacebar
        NamedKey.Backspace -> Key.Backspace; NamedKey.Escape -> Key.Escape; NamedKey.Delete -> Key.Delete
        NamedKey.Insert -> Key.Insert; NamedKey.Home -> Key.MoveHome; NamedKey.End -> Key.MoveEnd
        NamedKey.PageUp -> Key.PageUp; NamedKey.PageDown -> Key.PageDown
        NamedKey.ArrowLeft -> Key.DirectionLeft; NamedKey.ArrowRight -> Key.DirectionRight
        NamedKey.ArrowUp -> Key.DirectionUp; NamedKey.ArrowDown -> Key.DirectionDown
        NamedKey.Shift -> Key.ShiftLeft; NamedKey.Control -> Key.CtrlLeft
        NamedKey.Alt -> Key.AltLeft; NamedKey.Meta -> Key.MetaLeft
        NamedKey.CapsLock -> Key.CapsLock; NamedKey.NumLock -> Key.NumLock
        NamedKey.F1 -> Key.F1; NamedKey.F2 -> Key.F2; NamedKey.F3 -> Key.F3; NamedKey.F4 -> Key.F4
        NamedKey.F5 -> Key.F5; NamedKey.F6 -> Key.F6; NamedKey.F7 -> Key.F7; NamedKey.F8 -> Key.F8
        NamedKey.F9 -> Key.F9; NamedKey.F10 -> Key.F10; NamedKey.F11 -> Key.F11; NamedKey.F12 -> Key.F12
        else -> null
    }
    is LogicalKey.Unidentified -> null
}

private val SIZE: GroupLayout = MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
private val RECT: GroupLayout = MemoryLayout.structLayout(SIZE, SIZE)

/** Process-lifetime method implementation contains no mount/host reference. */
private object RendererView {
    val type: MemorySegment by lazy {
        val type = ObjCSubclassing.allocateClass("NSView", "KadreDeskTourRendererView")
        check(type != MemorySegment.NULL) { "Unable to allocate renderer NSView class" }
        val descriptor = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, SIZE)
        val method = MethodHandles.lookup().findStatic(RendererView::class.java, "hitTest", descriptor.toMethodType())
        val callback = Linker.nativeLinker().upcallStub(method, descriptor, Arena.global())
        check(ObjCSubclassing.addMethod(type, "hitTest:", callback, "@@:{CGPoint=dd}"))
        ObjCSubclassing.registerClass(type)
        type
    }
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun hitTest(receiver: MemorySegment, selector: MemorySegment, point: MemorySegment): MemorySegment = MemorySegment.NULL
}

private fun requireMainThread() {
    check(ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ObjCRuntime.getClass("NSThread"), ObjCRuntime.sel("isMainThread")) as Boolean) {
        "Compose mount must run on the Kadre AppKit main thread"
    }
}
private fun send(receiver: MemorySegment, selector: String, vararg args: Any) {
    ObjCRuntime.msgSend(null, receiver, ObjCRuntime.sel(selector), *args)
}
private fun address(receiver: MemorySegment, selector: String): MemorySegment =
    ObjCRuntime.msgSend(ValueLayout.ADDRESS, receiver, ObjCRuntime.sel(selector)) as MemorySegment
private fun long(receiver: MemorySegment, selector: String): Long =
    ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, receiver, ObjCRuntime.sel(selector)) as Long
private fun newObject(type: MemorySegment): MemorySegment {
    check(type != MemorySegment.NULL) { "Native class unavailable" }
    return address(address(type, "alloc"), "init").also { check(it != MemorySegment.NULL) { "Native allocation failed" } }
}
private fun structMessage(receiver: MemorySegment, selector: String, layout: GroupLayout, vararg values: Double) {
    Arena.ofConfined().use { arena ->
        val payload = arena.allocate(layout)
        values.forEachIndexed { index, value -> payload.setAtIndex(ValueLayout.JAVA_DOUBLE, index.toLong(), value) }
        Linker.nativeLinker().downcallHandle(ObjCRuntime.objcMsgSendAddr,
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, layout),
        ).invokeWithArguments(receiver, ObjCRuntime.sel(selector), payload)
    }
}
private fun nativeFailure(operation: String, failure: Throwable): KadreResult.Failure {
    System.err.println("Compose bridge $operation failed: $failure")
    failure.printStackTrace(System.err)
    return KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.AppKit, "desk-tour.compose", operation))
}
