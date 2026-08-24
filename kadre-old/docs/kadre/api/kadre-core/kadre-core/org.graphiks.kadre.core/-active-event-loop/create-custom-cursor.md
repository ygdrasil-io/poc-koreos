//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[ActiveEventLoop](index.md)/[createCustomCursor](create-custom-cursor.md)

# createCustomCursor

[common]\
open fun [createCustomCursor](create-custom-cursor.md)(image: [CursorImage](../-cursor-image/index.md)): [CustomCursor](../-custom-cursor/index.md)?

Creates a custom cursor from the provided RGBA pixel data.

Returns null if the backend does not support custom cursors (iOS, Android) or if cursor creation failed (e.g. invalid image dimensions).

Default implementation returns null. Backends that support custom cursors override this method. Never throws.

#### Return

An opaque [CustomCursor](../-custom-cursor/index.md) handle, or null on unsupported platforms.

#### Parameters

common

| | |
|---|---|
| image | RGBA image data with hot-spot information. |
