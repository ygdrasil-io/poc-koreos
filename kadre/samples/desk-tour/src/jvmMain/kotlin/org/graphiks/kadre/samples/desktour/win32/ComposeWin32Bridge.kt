package org.graphiks.kadre.samples.desktour.win32

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton as ComposePointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
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
import org.graphiks.kadre.samples.desktour.appkit.ComposeMount
import org.graphiks.kadre.samples.desktour.appkit.ComposeMountLifecycle
import org.graphiks.kadre.samples.desktour.appkit.ComposeMountState
import org.graphiks.kadre.samples.desktour.appkit.ObservedPointer
import org.graphiks.kadre.samples.desktour.appkit.composeButton
import org.graphiks.kadre.samples.desktour.appkit.composeKey
import org.graphiks.kadre.samples.desktour.appkit.composeModifiers
import org.graphiks.kadre.samples.desktour.appkit.isMouse
import org.graphiks.kadre.samples.desktour.appkit.pixelSize
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.window.Window
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import androidx.compose.ui.graphics.asComposeCanvas

/**
 * The Windows mirror of the AppKit Compose bridge: the Kadre window's HWND
 * (leased through the public desktop handle API) is painted with a software
 * Skia surface — the raster frame is blitted to the client area with GDI
 * `StretchDIBits`, the same sample-side FFM precedent the AppKit host uses
 * for Metal. Every scene operation is serialized on a single render lane
 * (Skia surfaces are not thread-safe), and frames flow through Kadre's
 * redraw protocol exactly like the AppKit host: Compose invalidation →
 * requestRedraw → RedrawRequested → render.
 */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
internal suspend fun Window.mountComposeWin32(
    scope: CoroutineScope,
    content: @Composable () -> Unit,
): KadreResult<ComposeMount> {
    var candidate: ComposeWin32Mount? = null
    return try {
        val result = withContext(NonCancellable + Dispatchers.Default) {
            when (val leased = withDesktopHandle { handle ->
                runCatching<KadreResult<ComposeMount>> {
                    when (handle) {
                        is DesktopNativeWindowHandle.Win32 -> {
                            val mount = ComposeWin32Mount(
                                scope, surface::requestRedraw, surface.state.value, surface.input.state.value,
                            )
                            candidate = mount
                            mount.initialize(handle.hwnd.toLong(), content)
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
            withContext(NonCancellable + Dispatchers.Default) { candidate?.rollback() }
        } catch (cleanup: Throwable) {
            if (failure !== cleanup) failure.addSuppressed(cleanup)
        }
        if (failure is CancellationException) throw failure
        nativeFailure("mount", failure)
    }
}

private fun nativeFailure(stage: String, failure: Throwable): Nothing {
    // Le code de failure reste un identifiant stable ; le détail part sur stderr.
    failure.printStackTrace()
    throw KadreException(
        KadreFailure.PlatformFailure(KadrePlatform.Win32, "desk-tour-bridge", stage),
    )
}

private class ComposeWin32Mount(
    private val scope: CoroutineScope,
    private val requestRedraw: () -> KadreResult<Unit>,
    initialSurface: SurfaceState,
    initialInput: SurfaceInputState,
) : ComposeMount {
    private val lifecycle = ComposeMountLifecycle()
    private val work = Job(scope.coroutineContext[Job])
    private val renderLane = Dispatchers.IO.limitedParallelism(1)
    private val invalidations = Channel<Unit>(Channel.CONFLATED)
    private val frameClock = BroadcastFrameClock { invalidations.trySend(Unit) }
    private var pendingRedraw = false
    private var surfaceState = initialSurface
    private var hwnd: Long = 0
    private val gdi = GdiBlit()
    private var raster: Surface? = null
    private var rasterInfo: ImageInfo? = null
    private var scene: ComposeScene? = null
    private var textInput: PlatformTextInputMethodRequest? = null
    private val windowInfo = object : WindowInfo {
        override var isWindowFocused by androidx.compose.runtime.mutableStateOf(
            initialSurface.focus == SurfaceFocus.Focused,
        )
        override var containerSize by androidx.compose.runtime.mutableStateOf(initialSurface.pixelSize())
        override var keyboardModifiers by androidx.compose.runtime.mutableStateOf(initialInput.modifiers.composeModifiers())
    }
    private val buttons = initialInput.pointers.associate { it.id to it.pressedButtons }.toMutableMap()
    private var pointer: ObservedPointer? = initialInput.pointers.firstOrNull { it.kind.isMouse() && it.position != null }
        ?.let { ObservedPointer(it.id, it.kind, checkNotNull(it.position)) }

    @Volatile
    override var activity: String = "Waiting for input"
        private set

    fun initialize(windowHandle: Long, content: @Composable () -> Unit) {
        hwnd = windowHandle
        gdi.open(windowHandle)
        scene = CanvasLayersComposeScene(
            density = Density(surfaceState.scaleFactor.toFloat()),
            size = surfaceState.pixelSize(),
            coroutineContext = scope.coroutineContext + work + frameClock + renderLane,
            platformContext = object : PlatformContext.Empty() {
                override val windowInfo: WindowInfo get() = this@ComposeWin32Mount.windowInfo
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
        lifecycle.markMounted()
        CoroutineScope(scope.coroutineContext + work + renderLane).launch {
            for (ignored in invalidations) {
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

    override fun updateSurface(state: SurfaceState) {
        scope.launch(work + renderLane) { updateSurfaceOnLane(state) }
    }

    private fun updateSurfaceOnLane(state: SurfaceState) {
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
            scene?.cancelPointerInput()
        }
    }

    private fun applyMetrics(state: SurfaceState) {
        scene?.density = Density(state.scaleFactor.toFloat())
        scene?.size = state.pixelSize()
        windowInfo.containerSize = state.pixelSize()
        val width = state.physicalSize.width
        val height = state.physicalSize.height
        if (width <= 0 || height <= 0) return
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        raster?.close()
        rasterInfo = info
        gdi.resize(width, height)
        // Rendu direct dans le tampon DIB : aucune copie entre Skia et GDI.
        raster = Surface.makeRasterDirect(info, gdi.pixelsAddress(), width * 4)
    }

    override fun surfaceEvent(event: SurfaceEvent): KadreResult<Unit> {
        scope.launch(work + renderLane) {
            val result = surfaceEventOnLane(event)
            if (result is KadreResult.Failure) throw KadreException(result.reason)
        }
        return KadreResult.Success(Unit)
    }

    private fun surfaceEventOnLane(event: SurfaceEvent): KadreResult<Unit> {
        if (lifecycle.state != ComposeMountState.Mounted) {
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        }
        return try {
            when (event) {
                is SurfaceEvent.MetricsChanged -> updateSurfaceOnLane(event.state)
                is SurfaceEvent.FocusChanged -> updateSurfaceOnLane(event.state)
                is SurfaceEvent.VisibilityChanged -> updateSurfaceOnLane(event.state)
                is SurfaceEvent.AppearanceChanged -> updateSurfaceOnLane(event.state)
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

    private fun renderFrame() {
        val surface = raster ?: return
        Snapshot.sendApplyNotifications()
        val now = System.nanoTime()
        frameClock.sendFrame(now)
        checkNotNull(scene).render(surface.canvas.asComposeCanvas(), now)
        surface.flushAndSubmit()
        gdi.blit()
    }

    override fun dispatch(event: InputEvent) {
        scope.launch(work + renderLane) { dispatchOnLane(event) }
    }

    private fun dispatchOnLane(event: InputEvent) {
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
                    if (button == null) false else dispatchPointer(
                        event, event.pointerId, event.kind, event.position,
                        if (event.buttonState == PointerButtonState.Pressed) PointerEventType.Press else PointerEventType.Release,
                        button = button,
                    )
                }
            }
            is InputEvent.Scrolled -> {
                val observed = pointer
                if (observed == null) false else {
                    val delta = when (val value = event.delta) {
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
        pointer = if (type == PointerEventType.Exit) null else ObservedPointer(id, kind, position)
        val pressed = buttons[id].orEmpty()
        if (pressed.any { it.composeButton() == null }) return false
        checkNotNull(scene).sendPointerEvent(
            eventType = type,
            // Win32 client coordinates are already top-left origin: scale only.
            position = Offset(
                (position.x * surfaceState.scaleFactor).toFloat(),
                (position.y * surfaceState.scaleFactor).toFloat(),
            ),
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

    override suspend fun close() {
        withContext(NonCancellable + renderLane) { closeOnLane() }
        renderLane.cancel()
    }

    suspend fun rollback() {
        withContext(NonCancellable + renderLane) {
            if (lifecycle.state == ComposeMountState.New) {
                lifecycle.markFailed()
                disposeOwnedResources()
            } else {
                closeOnLane()
            }
        }
        renderLane.cancel()
    }

    private fun closeOnLane() {
        if (lifecycle.state == ComposeMountState.Closing || lifecycle.state == ComposeMountState.Closed ||
            lifecycle.state == ComposeMountState.Failed
        ) return
        lifecycle.beginClose()
        try { disposeOwnedResources() } finally { lifecycle.markClosed() }
    }

    private fun disposeOwnedResources() {
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
        release { raster?.close() }
        raster = null
        release { gdi.close() }
        problem?.let { throw it }
    }
}

/** Sample-side FFM (the AppKit host precedent): the raster frame is blitted to the HWND. */
private class GdiBlit {
    private val linker = Linker.nativeLinker()
    // Partagé : le montage vit sur Default, le rendu et la fermeture sur la render lane.
    private val arena = Arena.ofShared()
    private val user32 = SymbolLookup.libraryLookup("user32", arena)
    private val gdi32 = SymbolLookup.libraryLookup("gdi32", arena)
    private val getDc = linker.downcallHandle(
        user32.find("GetDC").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
    )
    private val releaseDc = linker.downcallHandle(
        user32.find("ReleaseDC").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
    )
    private val stretchDibBits = linker.downcallHandle(
        gdi32.find("StretchDIBits").orElseThrow(),
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ),
    )
    private var hwnd: MemorySegment = MemorySegment.NULL
    private var pixels: MemorySegment = MemorySegment.NULL
    private var info: MemorySegment = MemorySegment.NULL
    private var width = 0
    private var height = 0

    fun open(windowHandle: Long) {
        hwnd = MemorySegment.ofAddress(windowHandle)
    }

    fun pixelsAddress(): Long = pixels.address()

    fun resize(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        pixels = arena.allocate((width.toLong() * 4 * height).coerceAtLeast(1))
        info = arena.allocate(40)
        // BITMAPINFOHEADER Win64 : biPlanes/biBitCount sont des WORD (offsets 12/14).
        info.set(ValueLayout.JAVA_INT, 0, 40)          // biSize
        info.set(ValueLayout.JAVA_INT, 4, width)       // biWidth
        info.set(ValueLayout.JAVA_INT, 8, -height)     // biHeight négatif : top-down
        info.set(ValueLayout.JAVA_SHORT, 12, 1)        // biPlanes
        info.set(ValueLayout.JAVA_SHORT, 14, 32)       // biBitCount
        info.set(ValueLayout.JAVA_INT, 16, 0)          // biCompression = BI_RGB
        info.set(ValueLayout.JAVA_INT, 20, width * 4 * height) // biSizeImage
    }


    fun blit() {
        if (hwnd.address() == 0L || pixels.address() == 0L) return
        val dc = getDc.invokeExact(hwnd) as MemorySegment
        if (dc.address() == 0L) return
        try {
            val copied = stretchDibBits.invokeExact(
                dc, 0, 0, width, height, 0, 0, width, height,
                pixels, info, 0 /* DIB_RGB_COLORS */, 0x00CC0020 /* SRCCOPY */,
            ) as Int
            require(copied != 0) { "StretchDIBits failed" }
        } finally {
            ("UNCHECKED_CAST")
            val released = releaseDc.invokeExact(hwnd, dc) as Int
            require(released == 1) { "ReleaseDC failed" }
        }
    }

    fun close() {
        arena.close()
    }
}
