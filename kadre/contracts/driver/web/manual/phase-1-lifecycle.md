# Web Phase 1 — Lifecycle manual charter

This is a short manual complement to the deterministic public browser tests.
It covers browser behaviour that headless automation cannot honestly make
repeatable: a real back-forward cache (bfcache) traversal, browser/tab focus,
and host-owned Shadow DOM. It is informative in Phase 1; it is never a
substitute for automated JS and Wasm evidence, and it is not wired into the
contract validator.

## Scope and prerequisites

Phase 1 covers lifecycle of a host-owned `HTMLElement` attached through
`HTMLElement.attachKadre` only. Kadre creates no DOM node, popup, iframe or
rendering surface. In particular, a Web session has
`scope.windows.state.value.primary == null`, and window requests are
unsupported in this phase. An iframe mentioned below is created and owned by
the test host solely to exercise browser document transfer; it is never
created by Kadre.

Use a small same-origin host page that retains references to its supplied
elements and exposes the following observable values in its UI or console:

- the session state and terminal outcome;
- the lifecycle as `Attached + Foreground + Active`, `Attached + Foreground +
  Inactive`, or `Attached + Background + Inactive`;
- the result of a duplicate attach and of a new attach after termination;
- the supplied element's child count and own-property names before and after
  attach.

The page must hold an active `parentScope`, attach an application that remains
alive until stopped, and log `pagehide.persisted` and `pageshow.persisted`.
Do not add instrumentation to Kadre for this charter.

### Clean-checkout setup

`installPlaywright` only performs the locked `npm ci --ignore-scripts`; it does
not install a browser. Provision the exact Chromium revision pinned by that
local package, then build both public driver bundles:

```shell
rtk ./gradlew :kadre:contracts:driver:web:installPlaywright
(cd kadre/contracts/driver/web && npx --no-install playwright install chromium)
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserDistribution :kadre:contracts:driver:web:wasmJsBrowserDistribution
```

Create a disposable static host outside the checkout. It loads the existing
public JS and Wasm driver bundles; no fixture, dependency or source is added to
the repository:

```shell
kadre_manual_dir="$(mktemp -d)"
mkdir "$kadre_manual_dir/js" "$kadre_manual_dir/wasm"
cp kadre/contracts/driver/web/build/dist/js/productionExecutable/kadre-web-phase0.js "$kadre_manual_dir/js/"
cp -R kadre/contracts/driver/web/build/dist/wasmJs/productionExecutable/. "$kadre_manual_dir/wasm/"
cat > "$kadre_manual_dir/js/index.html" <<'EOF'
<!doctype html><meta charset="utf-8"><script src="./kadre-web-phase0.js"></script>
EOF
cat > "$kadre_manual_dir/wasm/index.html" <<'EOF'
<!doctype html><meta charset="utf-8"><script src="./kadre-web-phase0-wasm.js"></script>
EOF
(cd "$kadre_manual_dir" && python3 -m http.server 8080)
```

Keep the static server running in that terminal and open one target at a time,
for example:

```text
http://127.0.0.1:8080/js/index.html?scenario=manual-reconnect
http://127.0.0.1:8080/wasm/index.html?scenario=manual-reconnect
```

Replace `manual-reconnect` with the public fixture scenario needed by a
procedure, such as `pagehide`, `focus`, `shadow-root`, `inter-document` or
`duplicate`. The query is read by the public driver bundle, so the same
temporary host page can be reused for all observations.

| Target | Browser setup | Automated prerequisite |
| --- | --- | --- |
| Kotlin/JS IR | Chromium provisioned by the local pinned Playwright package | `rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke --rerun-tasks` |
| Kotlin/Wasm-JS | Chromium provisioned by the local pinned Playwright package | `rtk ./gradlew :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks` |

Run both target commands before the manual pass. Their Playwright lifecycle
smokes cover deterministic attach, ownership, lifecycle and teardown paths.
The manual observations below may reveal a browser-specific issue, but may not
be used to fabricate contract evidence or to claim a missing automated branch
has passed.

## Procedures

### 1. Page lifecycle and bfcache

1. Attach a `StopWhenDetached` host in the foreground and confirm it is
   active after focusing it.
2. Navigate to a same-origin page without deliberately disabling bfcache, then
   use the browser Back action to return. Record the `pagehide.persisted` and
   `pageshow.persisted` values in the original page's log or breakpoint.
3. Repeat with a non-persisted navigation, for example a navigation for which
   the browser reports `pagehide.persisted == false`.

For both cases, `pagehide` terminates the original session immediately with
`Stopped(HostDetached)`. A later `pageshow`, including a persisted one, never
resurrects that session. A return to the page may create no session unless the
host application explicitly performs a new attach.

### 2. Browser/tab focus and document visibility

1. Attach two host elements in the same document and focus the first, then the
   second.
2. Switch to another browser tab or window, then return; also background the
   browser if the operating system makes that observable.
3. Inspect lifecycle reports after each transition.

Only the focused host in a visible, focused document is
`Attached + Foreground + Active`. The other host is foreground but inactive;
every host is `Attached + Background + Inactive` while the document is hidden
or its browsing context is unfocused. Returning focus can make a currently
connected host foreground again, but cannot resurrect a terminated session.

### 3. External Shadow DOM container removal

1. Have the host create a container and place the attached element in an open
   ShadowRoot. Remove the *external container* from the document.
2. Repeat using a closed ShadowRoot. Keep the element reference in the host
   before closing the root; do not rely on querying into the closed root.

In both cases a `StopWhenDetached` session terminates with
`Stopped(HostDetached)`. This validates external host removal, not merely
removal of the attached element from inside its shadow root. Kadre neither
created nor owns the container or either ShadowRoot.

### 4. `Manual` disconnection and original-document reconnection

1. Attach a disconnected element with `WebAttachmentPolicy.Manual`; it should
   report `Attached + Background + Inactive`.
2. Insert it into its original document and, after a browser frame, focus it.
3. Remove it long enough for observer delivery, then reinsert the same element
   in its original document.

The session remains alive through both disconnections. It becomes foreground
when connected to a visible origin document and active only when focused. No
terminal outcome is produced by a Manual disconnection alone. An explicit
`KadreSession.requestStop()` produces `Stopped(HostRequested)`; an application
call to `KadreScope.requestStop()` produces `Stopped(ApplicationRequested)`.
For a Manual session, an inter-document transfer or `pagehide` produces
`Stopped(HostDetached)`.

### 5. Cross-document adoption through a host-created iframe

1. Attach a connected element in the top-level document.
2. Have the test host create a same-origin iframe, adopt the attached element
   into `iframe.contentDocument`, and append it there.

The origin session terminates with `Stopped(HostDetached)` because its element
left the origin document. The iframe is setup owned by the host for this
experiment; this does not mean Kadre opens an iframe, popup or a new window.

### 6. Duplicate attach and cleanup

1. Attach an element, then attempt a second `attachKadre` on that exact element
   while the first session is alive.
2. Confirm the second request is rejected as `AlreadyInUse(Host)` and inspect
   that the element's child count and own-property names did not change.
3. Call `KadreSession.requestStop()` on the first session, await
   `Stopped(HostRequested)`, and only then attach the same element again.

The duplicate does not take ownership, install a second observer, or mutate a
host DOM property/expando. Reservation cleanup occurs only after the first
session is terminal, after which a new attach can own the element normally.

## Limits and diagnostic record

Record failures with the browser name and version, target and bundle (JS IR or
Wasm-JS), exact reproduction steps, lifecycle/session observations, and all
console errors. Include whether navigation reported `persisted` for `pagehide`
and `pageshow`, and whether the ShadowRoot was open or closed.

Do not infer support from one manual run, edit generated evidence, or add this
charter to the validator. Report the observation alongside the deterministic
target command result so that it can be reproduced and turned into an automated
test when the browser permits it.

## References

- [Web browser driver](../README.md)
- [Phase 1 roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md)
- [Kadre lifecycle design](../../../../DESIGN.md)
- [Public lifecycle smoke](../playwright/web-lifecycle.spec.mjs)
