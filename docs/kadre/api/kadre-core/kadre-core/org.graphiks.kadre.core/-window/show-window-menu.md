//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[Window](index.md)/[showWindowMenu](show-window-menu.md)

# showWindowMenu

[common]\
open fun [showWindowMenu](show-window-menu.md)(position: [PhysicalPosition](../-physical-position/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;): [WindowRequestResult](../-window-request-result/index.md)

Shows the platform window menu (system / title-bar context menu) at the given position.

Platform behaviour:

-
   Win32  : `TrackPopupMenu(GetSystemMenu(...))`.
-
   Wayland: `xdg_toplevel.show_window_menu` when an active pointer serial is available.
-
   Others : [WindowRequestResult.Failure](../-window-request-result/-failure/index.md) with [RequestError.Unsupported](../-request-error/-unsupported/index.md).

Default implementation returns [WindowRequestResult.Failure](../-window-request-result/-failure/index.md) with [RequestError.Unsupported](../-request-error/-unsupported/index.md). Never throws.

#### Parameters

common

| | |
|---|---|
| position | Position in physical pixels (window-relative) at which to show the menu. |
