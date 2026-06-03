//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[Window](index.md)/[dragWindow](drag-window.md)

# dragWindow

[common]\
open fun [dragWindow](drag-window.md)(): [WindowRequestResult](../-window-request-result/index.md)

Initiates a user-driven window drag from the current cursor position.

Intended to be called from a pointer-pressed event handler to allow dragging a custom title bar. Platform behaviour:

-
   AppKit   : `NSWindow.performWindowDragWithEvent(currentEvent)` on the AppKit main thread; returns [RequestError.Ignored](../-request-error/-ignored/index.md) when no current event is available.
-
   Win32    : posts a non-client move request to the window owner thread.
-
   Wayland  : `xdg_toplevel.move` when implemented.
-
   Others   : [WindowRequestResult.Failure](../-window-request-result/-failure/index.md) with [RequestError.Unsupported](../-request-error/-unsupported/index.md).

Default implementation returns [WindowRequestResult.Failure](../-window-request-result/-failure/index.md) with [RequestError.Unsupported](../-request-error/-unsupported/index.md). Never throws. TODO R5-MiscWindow: wire in X11/Wayland and AppKit off-main-thread marshalling.
