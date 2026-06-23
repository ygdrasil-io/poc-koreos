# Partial Web, CI, and Deferred Documentation Design

> Status: approved for implementation on 2026-06-23.

## Goal

Close the first safe slice of Kadre's "partial feature" gaps in a single reviewable PR: Web cursor hit-testing and Pointer Lock wiring, `codex/**` deep CI coverage, and the missing/inconsistent deferred-feature documentation.

## Scope

This PR covers only changes that are low-risk and locally testable from the current macOS worktree:

- Web `Window.setCursorHittest(false/true)` delegates to the DOM bridge via CSS `pointer-events`.
- Web `Window.setCursorGrab(CursorGrabMode.Locked)` requests browser Pointer Lock instead of returning unsupported.
- JS and wasmJs DOM bridges implement CSS cursor, pointer-events hit-testing, Pointer Lock request, and Pointer Lock release.
- CI deep jobs run for `codex/**` branches as well as the existing `claude/**` and `master` paths.
- Documentation stops pointing at a missing `DEFERRED.md`; the repository gets a tracked root `DEFERRED.md` and capability tables are aligned with the current Web implementation.

## Non-Goals

This PR does not attempt high-risk native backend work:

- No X11 keyboard text/XIM work.
- No Wayland `wl_output` monitor registry storage.
- No UIKit asynchronous file-path resolution.
- No Android dead-key reset implementation.
- No AGP/Dokka migration.
- No real runtime compositor/browser E2E expansion beyond the existing test suite.

## Architecture

`WebWindow` remains DOM-free in `webMain`. It gains behavior by calling small methods on `WebDomBridge`, keeping JS and wasmJs target-specific DOM access inside their concrete bridge implementations.

The bridge API stays simple and synchronous from Kadre's point of view: Pointer Lock requests are asynchronous in browsers, but `WindowRequestResult.Success` means the request was submitted to the browser, not that the lock was granted. This matches the existing fullscreen pattern.

Documentation is treated as a public contract. `DEFERRED.md` becomes the authoritative short list for intentionally deferred or platform-limited features, while `docs/features/gaps.md`, `docs/kadre/index.md`, and `docs/kadre/specs.md` reference implemented Web behavior accurately.

## Test Strategy

Use TDD for behavior changes:

- Add `webTest` unit coverage for `WebWindow.setCursorGrab(Locked/None)` and `WebWindow.setCursorHittest(false/true)` with a recording bridge.
- Verify the new tests fail before implementation.
- Implement the minimal WebWindow and bridge changes.
- Run `:kadre-web-common:allTests`, plus JS/Wasm compile tasks for `kadre-js` and `kadre-wasm`.
- Run fast JVM tests before PR.

## Review Strategy

Use sub-agents where they are isolated and useful:

- A documentation/CI worker can update the doc and workflow files independently from the Web runtime code.
- A reviewer sub-agent reviews the final diff against this spec before pushing.
- The controller performs final verification and owns integration, push, PR creation, and merge.
