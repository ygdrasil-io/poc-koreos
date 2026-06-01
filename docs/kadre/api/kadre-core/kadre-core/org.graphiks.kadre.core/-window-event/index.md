//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[WindowEvent](index.md)

# WindowEvent

sealed interface [WindowEvent](index.md)

Event emitted by a window.

Each variant corresponds to a state change or a user action on the targeted window.

### Typical usage

```kotlin
fun onWindowEvent(event: WindowEvent) {
    when (event) {
        WindowEvent.CloseRequested    -> quit()
        is WindowEvent.Resized        -> resize(event.size)
        is WindowEvent.Moved          -> move(event.position)
        is WindowEvent.ScaleFactorChanged -> updateDpi(event.factor)
        is WindowEvent.Focused        -> handleFocus(event.gained)
        is WindowEvent.KeyInput       -> handleKeyboard(event.event)
        is WindowEvent.PointerMoved   -> handlePointer(event.position)
        WindowEvent.PointerEntered    -> handleEnter()
        WindowEvent.PointerLeft       -> handleLeave()
        is WindowEvent.MouseInput     -> handleMouse(event.button, event.state)
        is WindowEvent.MouseWheel     -> handleWheel(event.deltaX, event.deltaY)
        is WindowEvent.Touch          -> handleTouch(event.phase, event.location, event.id)
        WindowEvent.RedrawRequested   -> redraw()
        WindowEvent.Destroyed         -> releaseResources()
    }
}
```

#### Inheritors

| |
|---|
| [CloseRequested](-close-requested/index.md) |
| [Resized](-resized/index.md) |
| [Moved](-moved/index.md) |
| [ScaleFactorChanged](-scale-factor-changed/index.md) |
| [Focused](-focused/index.md) |
| [KeyInput](-key-input/index.md) |
| [PointerMoved](-pointer-moved/index.md) |
| [PointerEntered](-pointer-entered/index.md) |
| [PointerLeft](-pointer-left/index.md) |
| [MouseInput](-mouse-input/index.md) |
| [MouseWheel](-mouse-wheel/index.md) |
| [Touch](-touch/index.md) |
| [ModifiersChanged](-modifiers-changed/index.md) |
| [RedrawRequested](-redraw-requested/index.md) |
| [Destroyed](-destroyed/index.md) |

## Types

| Name | Summary |
|---|---|
| [CloseRequested](-close-requested/index.md) | [common]<br>data object [CloseRequested](-close-requested/index.md) : [WindowEvent](index.md)<br>The user requested closing the window (× button, Alt+F4, ⌘W, etc.). |
| [Destroyed](-destroyed/index.md) | [common]<br>data object [Destroyed](-destroyed/index.md) : [WindowEvent](index.md)<br>The window has been destroyed and its native resources released. |
| [Focused](-focused/index.md) | [common]<br>data class [Focused](-focused/index.md)(val gained: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) : [WindowEvent](index.md)<br>The window gained or lost keyboard focus. |
| [KeyInput](-key-input/index.md) | [common]<br>data class [KeyInput](-key-input/index.md)(val event: [KeyEvent](../-key-event/index.md)) : [WindowEvent](index.md)<br>A keyboard event occurred while the window had focus. |
| [ModifiersChanged](-modifiers-changed/index.md) | [common]<br>data class [ModifiersChanged](-modifiers-changed/index.md)(val state: [KeyboardModifierState](../-keyboard-modifier-state/index.md)) : [WindowEvent](index.md)<br>The logical or physical keyboard modifier state changed. |
| [MouseInput](-mouse-input/index.md) | [common]<br>data class [MouseInput](-mouse-input/index.md)(val button: [MouseButton](../-mouse-button/index.md), val state: [KeyState](../-key-state/index.md)) : [WindowEvent](index.md)<br>A mouse button has been pressed or released. |
| [MouseWheel](-mouse-wheel/index.md) | [common]<br>data class [MouseWheel](-mouse-wheel/index.md)(val deltaX: [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html), val deltaY: [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)) : [WindowEvent](index.md)<br>The mouse wheel (or trackpad) produced a scroll. |
| [Moved](-moved/index.md) | [common]<br>data class [Moved](-moved/index.md)(val position: [PhysicalPosition](../-physical-position/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;) : [WindowEvent](index.md)<br>The window has been moved. |
| [PointerEntered](-pointer-entered/index.md) | [common]<br>data object [PointerEntered](-pointer-entered/index.md) : [WindowEvent](index.md)<br>The pointer just entered the window's client area. |
| [PointerLeft](-pointer-left/index.md) | [common]<br>data object [PointerLeft](-pointer-left/index.md) : [WindowEvent](index.md)<br>The pointer just left the window's client area. |
| [PointerMoved](-pointer-moved/index.md) | [common]<br>data class [PointerMoved](-pointer-moved/index.md)(val position: [PhysicalPosition](../-physical-position/index.md)&lt;[Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)&gt;) : [WindowEvent](index.md)<br>The pointer moved over the window. |
| [RedrawRequested](-redraw-requested/index.md) | [common]<br>data object [RedrawRequested](-redraw-requested/index.md) : [WindowEvent](index.md)<br>The window must be redrawn. |
| [Resized](-resized/index.md) | [common]<br>data class [Resized](-resized/index.md)(val size: [PhysicalSize](../-physical-size/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;) : [WindowEvent](index.md)<br>The window has been resized. |
| [ScaleFactorChanged](-scale-factor-changed/index.md) | [common]<br>data class [ScaleFactorChanged](-scale-factor-changed/index.md)(val factor: [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)) : [WindowEvent](index.md)<br>The window's DPI scale factor changed (e.g. moved to another monitor). |
| [Touch](-touch/index.md) | [common]<br>data class [Touch](-touch/index.md)(val phase: [TouchPhase](../-touch-phase/index.md), val location: [PhysicalPosition](../-physical-position/index.md)&lt;[Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)&gt;, val id: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) : [WindowEvent](index.md)<br>A touch contact changed state. |