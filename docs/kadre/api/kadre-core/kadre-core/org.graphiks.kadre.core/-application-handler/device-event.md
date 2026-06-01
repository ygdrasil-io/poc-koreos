//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[ApplicationHandler](index.md)/[deviceEvent](device-event.md)

# deviceEvent

[common]\
open fun [deviceEvent](device-event.md)(eventLoop: [ActiveEventLoop](../-active-event-loop/index.md), deviceId: [DeviceId](../-device-id/index.md), event: [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html))

Called when an input device event is received.

#### Parameters

common

| | |
|---|---|
| eventLoop | Active event loop. |
| deviceId | Identifier of the device that emitted the event. |
| event | Received event (Any type pending GRA-123). |