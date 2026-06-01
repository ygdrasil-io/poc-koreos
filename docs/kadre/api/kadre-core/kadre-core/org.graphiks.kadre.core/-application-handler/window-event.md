//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[ApplicationHandler](index.md)/[windowEvent](window-event.md)

# windowEvent

[common]\
abstract fun [windowEvent](window-event.md)(eventLoop: [ActiveEventLoop](../-active-event-loop/index.md), windowId: [WindowId](../-window-id/index.md), event: [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html))

Called when a window event is received.

Mandatory — no default implementation.

The window event types will be defined in GRA-123.

#### Parameters

common

| | |
|---|---|
| eventLoop | Active event loop. |
| windowId | Identifier of the window that emitted the event. |
| event | Received event (Any type pending GRA-123). |