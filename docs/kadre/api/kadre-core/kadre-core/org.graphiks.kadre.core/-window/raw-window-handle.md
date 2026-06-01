//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[Window](index.md)/[rawWindowHandle](raw-window-handle.md)

# rawWindowHandle

[common]\
abstract val [rawWindowHandle](raw-window-handle.md): [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)

Returns the native handle of the rendering surface.

The concrete type will be `RawWindowHandle` once GRA-122 is merged; declared `Any` so that commonMain remains platform-independent.