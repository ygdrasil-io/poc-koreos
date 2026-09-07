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

Every procedure below uses only the existing public Web driver fixture selected
by `?scenario=`. The fixture is the host page: it creates the supplied host
elements, retains the session internally, and publishes its observable result
on `document.body` as `data-kadre-<scenario>-session`,
`data-kadre-<scenario>-outcome`, and
`data-kadre-<scenario>-lifecycle`. It also sets `data-kadre-ready="true"` when
the selected scenario is ready. Do not add an application, Kotlin handles, or
instrumentation to Kadre for this charter.

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
<!doctype html><meta charset="utf-8"><body><script src="./kadre-web-phase0.js"></script></body>
EOF
cat > "$kadre_manual_dir/wasm/index.html" <<'EOF'
<!doctype html><meta charset="utf-8"><body><script src="./kadre-web-phase0-wasm.js"></script></body>
EOF
for kadre_manual_target in js wasm; do
  cat > "$kadre_manual_dir/$kadre_manual_target/next.html" <<'EOF'
<!doctype html><meta charset="utf-8"><body>same-origin navigation target</body>
EOF
done
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

The procedures show the JS URL. Replace `/js/` with `/wasm/` to perform the
same check against the Wasm-JS bundle.

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

Open `http://127.0.0.1:8080/js/index.html?scenario=pagehide` (or replace `js`
with `wasm`) and wait for `document.body.dataset.kadreReady === "true"`.
Enable **Preserve log** in DevTools, then enter this command in the console:

```js
addEventListener("pagehide", (event) => console.log("pagehide", event.persisted));
addEventListener("pageshow", (event) => console.log("pageshow", event.persisted));
```

Navigate with the following command, then use the browser Back action. Record
the two logged `persisted` values. To request a non-persisted comparison,
repeat after entering the `unload` listener below before navigation:

```js
location.assign("http://127.0.0.1:8080/js/next.html");
```

```js
addEventListener("unload", () => console.log("unload"));
```

For both cases, `pagehide` terminates the original session immediately with
`Stopped(HostDetached)`. On a persisted return,
`document.body.dataset.kadrePagehideSession` remains `"terminated"` and
`document.body.dataset.kadrePagehideOutcome` remains `"host-detached"`; a
later `pageshow` does not resurrect the original session. On a non-persisted
return the fixture is a new page load, so do not confuse a newly created
fixture session with resurrection of the old one.

### 2. Browser/tab focus and document visibility

Open `http://127.0.0.1:8080/js/index.html?scenario=focus`, then run each
command in DevTools and inspect the reported `data-kadre-focus-a-lifecycle` and
`data-kadre-focus-b-lifecycle` body attributes:

```js
document.querySelector('[data-kadre-host="focus-a"]').focus();
await new Promise(requestAnimationFrame);
document.body.getAttribute("data-kadre-focus-a-lifecycle");
```

```js
document.querySelector('[data-kadre-host="focus-b"]').focus();
await new Promise(requestAnimationFrame);
document.body.getAttribute("data-kadre-focus-b-lifecycle");
```

Then switch to another browser tab or window and return. Observe the two body
attributes again after each transition.

Only the focused host in a visible, focused document is
`Attached + Foreground + Active`. The other host is foreground but inactive;
every host is `Attached + Background + Inactive` while the document is hidden
or its browsing context is unfocused. Returning focus can make a currently
connected host foreground again, but cannot resurrect a terminated session.

### 3. External Shadow DOM container removal

Open `http://127.0.0.1:8080/js/index.html?scenario=shadow-root`, then move the
fixture host into an open root:

```js
(() => {
  const host = document.querySelector('[data-kadre-host="shadow"]');
  const container = document.createElement("section");
  document.body.append(container);
  container.attachShadow({ mode: "open" }).append(host);
  window.__kadreManualShadowContainer = container;
})();
await new Promise(requestAnimationFrame);
```

Confirm `document.body.dataset.kadreShadowSession === "running"`, then run:

```js
window.__kadreManualShadowContainer.remove();
await new Promise(requestAnimationFrame);
document.body.dataset.kadreShadowSession;
document.body.dataset.kadreShadowOutcome;
```

Reload the same scenario for the closed-root pass, then enter this complete
command:

```js
(() => {
  const host = document.querySelector('[data-kadre-host="shadow"]');
  const container = document.createElement("section");
  document.body.append(container);
  container.attachShadow({ mode: "closed" }).append(host);
  window.__kadreManualShadowContainer = container;
})();
await new Promise(requestAnimationFrame);
```

The container reference is deliberately retained on `window`; do not query into
the closed root. Run the same `window.__kadreManualShadowContainer.remove()`
and attribute-inspection command used for the open-root pass.

In both cases a `StopWhenDetached` session terminates with
`Stopped(HostDetached)`. This validates external host removal, not merely
removal of the attached element from inside its shadow root. Kadre neither
created nor owns the container or either ShadowRoot.

### 4. `Manual` disconnection and original-document reconnection

Open `http://127.0.0.1:8080/js/index.html?scenario=manual-reconnect`. The
fixture initially reports
`document.body.dataset.kadreManualLifecycle ===
"attached-background-inactive"`. Reconnect its supplied host with:

```js
document.dispatchEvent(new Event("kadre-connect-manual"));
await new Promise(requestAnimationFrame);
document.body.dataset.kadreManualLifecycle;
```

After a browser frame, run:

```js
document.querySelector('[data-kadre-host="manual"]').focus();
await new Promise(requestAnimationFrame);
document.body.dataset.kadreManualLifecycle;
```

For a delivered detach, open
`http://127.0.0.1:8080/js/index.html?scenario=manual-detach-reconnect` and
run:

```js
document.querySelector('[data-kadre-host="manual-detach"]').remove();
await new Promise(requestAnimationFrame);
```

Confirm `data-kadre-manual-detach-session` is `"running"` and
`data-kadre-manual-detach-lifecycle` is `"attached-background-inactive"`, then
reconnect with:

```js
document.dispatchEvent(new Event("kadre-reconnect-manual"));
await new Promise(requestAnimationFrame);
document.body.dataset.kadreManualDetachLifecycle;
```

The session remains alive through both disconnections. It becomes foreground
when connected to a visible origin document and active only when focused. No
terminal outcome is produced by a Manual disconnection alone. The fixture does
not expose Kotlin stop handles; in its observable terminal scenarios,
inter-document transfer and `pagehide` produce `Stopped(HostDetached)`.

### 5. Cross-document adoption through a host-created iframe

Open `http://127.0.0.1:8080/js/index.html?scenario=inter-document`, then run:

```js
(() => {
  const host = document.querySelector('[data-kadre-host="transfer"]');
  const frame = document.createElement("iframe");
  document.body.append(frame);
  const foreignDocument = frame.contentDocument;
  foreignDocument.body.append(foreignDocument.adoptNode(host));
})();
await new Promise(requestAnimationFrame);
```

Inspect `document.body.dataset.kadreTransferSession` and
`document.body.dataset.kadreTransferOutcome`.

The origin session terminates with `Stopped(HostDetached)` because its element
left the origin document. The iframe is setup owned by the host for this
experiment; this does not mean Kadre opens an iframe, popup or a new window.

### 6. Duplicate attach and cleanup

Open `http://127.0.0.1:8080/js/index.html?scenario=duplicate`. The fixture has
already attempted the second attach. Inspect its result and the provided host
without requiring an unexposed Kotlin session handle:

```js
const host = document.querySelector('[data-kadre-host="duplicate"]');
document.body.dataset.kadreDuplicateAttach;
Object.getOwnPropertyNames(host).includes("kotlinHashCodeValue$");
```

Then detach the first owner and inspect the terminal fixture attributes:

```js
document.querySelector('[data-kadre-host="duplicate"]').remove();
await new Promise(requestAnimationFrame);
document.body.dataset.kadreDuplicateSession;
document.body.dataset.kadreDuplicateOutcome;
```

The duplicate does not take ownership, install a second observer, or mutate a
host DOM property/expando. The second attach is
`AlreadyInUse(Host)`; after the supplied host is detached, the first session
terminates with `Stopped(HostDetached)`.

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
