# Win32 phase 5 — window mutations, tranche A

## Scope and contract

This jointly delivered KFFI/Kadre tranche replaces the phase-4 mutation rejection with real owner-thread commands: title, physical outer position, logical client size, minimum/maximum client sizes, resizability, decorations/system buttons, and reversible borderless fullscreen. The phase-5 roadmap explicitly permits independently qualified sub-tranches. Levels, icons, attention, cursors, hit testing, native move/resize and pointer confinement remain separate follow-ups. Visibility and native minimize/maximize continue to be observed through the surface/native system menu; the public Window API has no show/minimize/maximize command. Exclusive mode remains phase 9. No public API is added.

## Native boundary

KFFI owns all new FFM calls, RECT/MINMAXINFO/MONITORINFO layouts and temporary UTF-16 buffers. A copied snapshot contains title, client size, outer rectangle, styles, DPI, visibility and minimized/maximized flags. Native operations validate their return values and capture GetLastError immediately; SetWindowLongPtrW clears last error first because a zero previous value is valid. AdjustWindowRectExForDpi converts client pixels to outer pixels; MonitorFromWindow/GetMonitorInfoW provide the actual current monitor for borderless. Bounds and dimensions are checked before narrowing to Windows signed 32-bit values. No new layout or symbol lookup enters Kadre.

KFFI MINMAXINFO handling first preserves the native default, then sets only the requested track limits. These constrain user resizing, not arbitrary foreign SetWindowPos calls. An external size outside those limits is reported faithfully with the inconsistent constraint removed from the snapshot, rather than inventing a client size. Minimized zero geometry preserves the last positive surface metrics.

## Ownership, admission and publication

The existing bounded UI queue is shared with the pump. requestUpdate returns promptly and defers even owner-thread admission until a later pump turn, outside the runtime admission lock; a pending command can be cancelled before UI claim. Claim is the mutation commit boundary. After claim cancellation is TooLate and readback still completes even if its coroutine detached. Teardown rejects queued commands; no future waits for a stopped pump.

During native setters Windows can reenter WndProc. The peer suppresses intermediate window geometry publications for that mutation; the final copied readback completes the admitted command with its operation ID. Surface metrics remain coherent with that same readback. Unrelated native observations outside a mutation carry no operation ID. A reentrant close wins ownership and prevents further setters or late resurrection. No callback retains borrowed memory.

Fields unsupported by this tranche retain the common runtime's per-field rejection outcomes. A failed native setter produces an explicit failure; after any native commit the driver attempts authoritative readback and uses committedFailure. Failure to obtain authoritative post-commit state is a session failure, not success with desired values.

## Chrome, constraints and fullscreen

System chrome is encoded with WS_CAPTION, WS_THICKFRAME, WS_SYSMENU, WS_MINIMIZEBOX and WS_MAXIMIZEBOX. Borderless has no system buttons. Frame changes preserve the requested client size by recomputing the outer rectangle at the actual window DPI. Client and constraint dimensions including the target frame are preflighted before any setter. After a move changes DPI, up to three bounded geometry attempts preserve the logical target using the actual destination DPI; intermediate readbacks are not published. This also applies to initial outerPosition. A constraint that becomes unrepresentable during a reentrant DPI callback is withdrawn safely and the owning command reports failure after native return. Readback derives effective chrome from styles, rather than echoing the command.

Entering borderless fullscreen saves the actual windowed styles and physical rectangle, removes chrome, and fills the nearest monitor. Exiting restores both. Constraints are suspended while fullscreen, restored on exit, and geometry/chrome mutations while fullscreen are explicitly rejected. This implementation uses synchronous Win32 setters, not the AppKit selector/callback protocol. The common runtime still serializes updates. Initial fullscreen continues to follow the existing runtime rule: open windowed, then apply fullscreen.

## Evidence and limits

Portable tests cover readback substitution, command correlation versus external events, constraints/clear, cancellation, reentrant close, partial native failure and fullscreen restore. KFFI and Kadre native tests inspect actual styles and rectangles on this Windows machine; a worker-driven public session exercises the shared queue. The contract gate includes existing phase-1..4 evidence. Manual mixed-DPI, physical resizing, and system modal menu qualification are recorded as unexecuted until actually performed.

Microsoft references: [SetWindowLongPtrW](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowlongptrw), [AdjustWindowRectExForDpi](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-adjustwindowrectexfordpi), [MINMAXINFO](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-minmaxinfo).
