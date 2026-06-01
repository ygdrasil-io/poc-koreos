//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[Window](index.md)

# Window

[common]\
interface [Window](index.md)

Abstraction of a native window created by the event loop.

The concrete implementations are provided by the platform modules (kadre-appkit, etc.).

## Properties

| Name | Summary |
|---|---|
| [id](id.md) | [common]<br>abstract val [id](id.md): [WindowId](../-window-id/index.md)<br>Unique identifier of the window. |
| [innerSize](inner-size.md) | [common]<br>abstract val [innerSize](inner-size.md): [PhysicalSize](../-physical-size/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;<br>Returns the inner size of the window in physical pixels (rendering surface, without the decorations). |
| [outerSize](outer-size.md) | [common]<br>abstract val [outerSize](outer-size.md): [PhysicalSize](../-physical-size/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;<br>Returns the outer size of the window in physical pixels (rendering surface plus the platform decorations). |
| [rawDisplayHandle](raw-display-handle.md) | [common]<br>abstract val [rawDisplayHandle](raw-display-handle.md): [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)<br>Returns the native handle of the display. |
| [rawWindowHandle](raw-window-handle.md) | [common]<br>abstract val [rawWindowHandle](raw-window-handle.md): [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)<br>Returns the native handle of the rendering surface. |
| [scaleFactor](scale-factor.md) | [common]<br>abstract val [scaleFactor](scale-factor.md): [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)<br>Returns the scale factor between logical and physical pixels for this window. |

## Functions

| Name | Summary |
|---|---|
| [close](close.md) | [common]<br>abstract fun [close](close.md)()<br>Closes the window. |
| [requestRedraw](request-redraw.md) | [common]<br>abstract fun [requestRedraw](request-redraw.md)()<br>Requests a redraw of the window at the next iteration. |
| [setTitle](set-title.md) | [common]<br>abstract fun [setTitle](set-title.md)(title: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html))<br>Sets the title shown in the window's title bar. |
| [setVisible](set-visible.md) | [common]<br>abstract fun [setVisible](set-visible.md)(visible: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html))<br>Makes the window visible or invisible. |