//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[Window](index.md)/[requestUserAttention](request-user-attention.md)

# requestUserAttention

[common]\
open fun [requestUserAttention](request-user-attention.md)(requestType: [UserAttentionType](../-user-attention-type/index.md)?): [WindowRequestResult](../-window-request-result/index.md)

Requests the platform to attract the user's attention (taskbar / dock icon).

Passing null cancels an active attention request.

Platform behaviour:

-
   AppKit : `NSApp.requestUserAttention` / `cancelUserAttentionRequest`.
-
   Win32  : `FlashWindowEx` (FLASHW_TRAY / FLASHW_TIMER).
-
   Others : [WindowRequestResult.Failure](../-window-request-result/-failure/index.md) with [RequestError.Unsupported](../-request-error/-unsupported/index.md).

Default implementation returns [WindowRequestResult.Failure](../-window-request-result/-failure/index.md) with [RequestError.Unsupported](../-request-error/-unsupported/index.md). Never throws. AppKit is wired; Win32 attention remains deferred.

#### Parameters

common

| | |
|---|---|
| requestType | Attention level, or null to cancel the current request. |