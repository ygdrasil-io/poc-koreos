# Web Phase 2 — Surface and interop manual charter

This is a short manual complement to the deterministic Phase 2 browser tests. It
covers browser behaviour that headless automation cannot honestly produce: a real
browser zoom, a real back-forward cache (bfcache) traversal with a redraw request
still waiting for its frame, and a host-owned element moved between browsing
contexts while an element lease is held. It is informative; it creates no
validator evidence, it does not change any contract status, and it is not wired
into the contract validator.

## Scope and prerequisites

Phase 2 covers the surface of an attached host-owned `HTMLElement`: the metrics
published from that element's own layout box and the browsing context's device
pixel ratio, the per-animation-frame coalescing of `requestRedraw()`, and the
`withWebElement` lease of the element escape hatch. Kadre still creates no DOM
node, iframe, popup or rendering surface, and a Web session still has
`scope.windows.state.value.primary == null`. Any iframe mentioned below is
created and owned by the test host to move an element between browsing contexts;
Kadre never creates one.

Every procedure uses the existing public Web driver fixture selected by
`?scenario=`, exactly as the Phase 1 charter does. The temporary static host, the
target table and the served fixture pages are the ones of
[phase-1-lifecycle.md](phase-1-lifecycle.md); build both distributions and serve
`http://127.0.0.1:8080/js/index.html?scenario=...` (replace `js` with `wasm` for
the Wasm-JS bundle) before starting:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserDistribution :kadre:contracts:driver:web:wasmJsBrowserDistribution
```

Run both target smokes before the manual pass. Their automated suites
(`web-surface.spec.mjs`, `web-typescript.spec.mjs`) cover the deterministic
contract: the stubbed device pixel ratio, the coalesced admissions, the detached
rejection, the lease boundary and the closing order. The observations below may
reveal a browser-specific issue, but they may not be used to fabricate contract
evidence or to claim that an automated branch has passed.

## Procedures

### 1. Real browser zoom: a device-pixel-ratio change with no layout resize

Open `http://127.0.0.1:8080/js/index.html?scenario=surface-metrics` and wait for
`document.body.dataset.kadreReady === "true"`. The fixture host reports the state
it receives from the public surface on the element itself:

```js
const host = document.querySelector('[data-kadre-host="surface-metrics"]');
host.getAttribute("data-kadre-surface-metrics");   // "320x180@1#0"
window.devicePixelRatio;                           // the ratio the next observation reads
host.getAttribute("data-kadre-surface-physical");  // the logical size multiplied by that ratio
```

Zoom the page with the browser control (**Cmd +/+** / **Ctrl +/+**, or the browser
menu). A real zoom changes the browsing context's device pixel ratio. It may or
may not change the element's own box in CSS pixels, and Kadre samples the ratio
only when the browser delivers an observation of that element or its document.
So the honest check is:

```js
document.dispatchEvent(new Event("kadre-resize-surface"));  // the browser's own layout mutation
await new Promise(requestAnimationFrame);
host.getAttribute("data-kadre-surface-metrics");
host.getAttribute("data-kadre-surface-physical");
```

Confirm that the logical size follows the delivered layout box, that the physical
size equals the logical size multiplied by the ratio read at that observation, and
that the revision increments exactly once per delivered observation. A readback
that does not move when no observation is delivered is the documented boundary of
this phase, not a failure: this phase samples the ratio on observation and
installs no zoom or media-query listener of its own. Restore the zoom and repeat
once to confirm the physical size follows back.

### 2. Real bfcache traversal with a pending redraw

Open `http://127.0.0.1:8080/js/index.html?scenario=surface-redraw`, wait for
`data-kadre-ready`, then issue one request and navigate away before its frame, in
a single command so that the request is still pending when the navigation starts:

```js
document.dispatchEvent(new Event("kadre-request-redraw"));
location.assign("http://127.0.0.1:8080/js/next.html");
```

The `pagehide` of the traversal is terminal (`Stopped(HostDetached)`), exactly as
in the Phase 1 charter. Use the browser Back action and inspect the restored page
(preserved bfcache page, same document and same fixture state):

```js
const host = document.querySelector('[data-kadre-host="surface-redraw"]');
document.body.dataset.kadreRedrawSession;   // "terminated"
document.body.dataset.kadreRedrawOutcome;   // "host-detached"
host?.getAttribute("data-kadre-redraw-count");  // the admission count before the navigation
```

The pending request must not admit a `RedrawRequested` after the traversal, no
frame may be scheduled on the restored document, and a later
`document.dispatchEvent(new Event("kadre-request-redraw"))` on the restored page
belongs to a terminated surface: it admits nothing. Repeat once with a
non-persisted return (enter `addEventListener("unload", () => {})` before
navigating) to compare the fresh-document case of the Phase 1 charter.

### 3. A host-owned element moved between browsing contexts while a lease is held

A lease callback cannot suspend, so no lease is ever observed across separate
tasks; the boundary this procedure exercises is the one the automated suite
cannot stage in Chromium's own timing: what the public facade answers when the
element leaves its origin browsing context in the same task as the lease.

Open `http://127.0.0.1:8080/js/index.html?scenario=element-lease` and wait for
`data-kadre-ready`. Move the host into a host-created iframe while the lease is
requested, in one command:

```js
(() => {
  const host = document.querySelector('[data-kadre-host="element-lease"]');
  const frame = document.createElement("iframe");
  document.body.append(frame);
  const foreignDocument = frame.contentDocument;
  document.dispatchEvent(new Event("kadre-lease"));
  foreignDocument.body.append(foreignDocument.adoptNode(host));
  window.__kadreManualLeaseFrame = frame;
  window.__kadreManualLeaseHost = host;   // the same element, now in the iframe document
})();
await new Promise(requestAnimationFrame);
window.__kadreManualLeaseHost.getAttribute("data-kadre-lease");         // "seen" only if the lease ran first
window.__kadreManualLeaseHost.getAttribute("data-kadre-lease-result");  // "granted" or a failure encoding
```

The `element-lease` scenario observes no session on `document.body`: it publishes
only the two element attributes read above. The session half of a lease is
observed on `?scenario=element-lease-close` below, as `data-kadre-lease-close-session`.

Then reload the scenario and run the same command with the lease dispatched
*after* the adoption, then a third time with the adoption reverted by moving the
element back into the origin document. In each case the element the callback may
receive is the host's own element in its origin browsing context or nothing: a
lease that cannot be granted is reported as a failure (`closed:surface`,
`temporarilyUnavailable:true`), never by handing the callback an element that
already belongs to another document. The origin session terminates with
`Stopped(HostDetached)` because its element left the origin document, while a
same-document move keeps it alive, as the Phase 1 charter describes. The origin
session outcome is observable on the Phase 1 scenarios (`data-kadre-durable-session`,
`data-kadre-transfer-session`), not on this one.

For the concurrent and closing orders the automated suite uses, open
`?scenario=element-lease-close` and run:

```js
document.dispatchEvent(new Event("kadre-lease-concurrent-close"));
await new Promise(requestAnimationFrame);
const host = document.querySelector('[data-kadre-host="element-lease-close"]');
host.getAttribute("data-kadre-lease-result");        // "granted"
host.getAttribute("data-kadre-lease-concurrent");    // "temporarilyUnavailable:true"
host.getAttribute("data-kadre-lease-closed");        // "closed:surface"
host.getAttribute("data-kadre-surface-attachment");  // "detached"
document.body.dataset.kadreLeaseCloseSession;        // "running"
```

Confirm that a lease in flight is not abandoned by the close, that a concurrent
lease is refused as retryable rather than granted, and that the closing redraw
overflow closes the surface without terminating the session: the overflow names
the surface as its owner.

## Limits and diagnostic record

Record failures with the browser name and version, target and bundle (JS IR or
Wasm-JS), exact reproduction steps, the readbacks above, and all console errors.
Include the zoom level and the device pixel ratio read from `window.devicePixelRatio`
before and after the zoom, whether the navigation reported `persisted` for
`pagehide` and `pageshow`, and the document the element belonged to when the lease
was requested.

Do not infer support from one manual run, do not edit generated evidence, and do
not add this charter to the validator. Report the observation alongside the
deterministic target smoke result so that it can be reproduced and turned into an
automated test when the browser permits it.

## References

- [Web browser driver](../README.md)
- [Web Phase 1 lifecycle charter](phase-1-lifecycle.md)
- [Web implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md)
- [Kadre design](../../../../DESIGN.md)
- [Operation contracts](../../../../OPERATION-CONTRACTS.md)
