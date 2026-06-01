//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[Window](index.md)/[rawDisplayHandle](raw-display-handle.md)

# rawDisplayHandle

[common]\
abstract val [rawDisplayHandle](raw-display-handle.md): [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)

Returns the native handle of the display.

The concrete type will be `RawDisplayHandle` once GRA-122 is merged; declared `Any` so that commonMain remains platform-independent.