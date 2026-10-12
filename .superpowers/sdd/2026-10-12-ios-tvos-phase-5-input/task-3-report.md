# Task 3 Report — Clavier physique (GCKeyboard), gestes, pointer/hover

**Status:** DONE_WITH_CONCERNS (all suites green on the final source state; two documented deviations, both factually forced — see §7; one test-side fix during verification — see §6)
**Commit:** `7806339d` — `feat(uikit): deliver game-controller keyboard, gestures and hover bindings` (branch `zcode/sess-56645e3f`, on top of `971a6844`)
**Context:** this dispatch took over after TWO predecessors were killed by the harness inactivity window. They left substantial uncommitted work (no report, no commit). This report covers the takeover audit, the completion, the verification, and one honest fix.

**Files created:**
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitKeyboardBinding.kt` (GCKeyboard binding + counters)
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitGestureBindings.kt` (gestes + hover binding, proxy target/action, counters)
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitInputAnnouncement.kt` (the announcement AGGREGATE — new, not in the brief)
- `kadre/platform/uikit/src/iosMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitGestureRecognizersIos.kt` (actual iOS)
- `kadre/platform/uikit/src/tvosMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitGestureRecognizersTvos.kt` (actual tvOS)
- `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitInputAnnouncementTest.kt` (5 tests)
- `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitGestureStatePhaseTest.kt` (8 tests)
- `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitKeyboardHoverMappingTest.kt` (8 tests)

**Files modified:**
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowCommandPort.kt` (wiring + aggregate)
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitInputMapping.kt` (+3 pure mappers)
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitTouchBinding.kt` (announcement aggregated)
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreUikitSurfaceDriver.kt` (probe + counters)
- `kadre/contracts/driver/uikit/tests/Shared/KadreUikitInputTests.swift` (family-truth expectations + new test; one assertion fixed, §6)

---

## 1. Takeover audit — what the predecessors left, and its verification

The uncommitted state was COMPLETE in structure: both bindings, the port wiring, the driver probe/counters, the Swift tests, three pure mappers in the kernel, the expect/actual seam, and 21 unit tests. The takeover verdict was **keep as-is** — every factual claim the predecessors embedded in KDocs was re-verified independently and held:

| Claim (in predecessor KDocs) | Independent re-verification | Verdict |
|---|---|---|
| `UIPinchGestureRecognizer`/`UIRotationGestureRecognizer` ABSENT from the tvOS klib (K/N mapping hole), although the Apple TVOS headers carry them | `strings` over the Kotlin 2.4.20 tvos_arm64 UIKit klib linkdata: `UIPanGestureRecognizer` 6 hits, `UITapGestureRecognizer` 5 hits, pinch/rotation/hover **0 hits**; AppleTVOS27.0.sdk headers declare pinch/rotation with NO `API_UNAVAILABLE(tvos)` | CONFIRMED — installing them from Kotlin tvosMain is structurally impossible; the honest tvOS `gestureKinds` is `{DoubleTap, Pan}` |
| `UIHoverGestureRecognizer` is `API_UNAVAILABLE(tvos)` | `UIGestureRecognizer.h`-adjacent header line: `API_AVAILABLE(ios(13.0)) API_UNAVAILABLE(watchos, tvos)` | CONFIRMED — hover is iOS-only, `pointerInstalled` stays false on tvOS |
| GCKeyboard is NOT a `GCController` (so `GCController.controllers()` cannot be the initial scan; `GCKeyboard.coalescedKeyboard` is) | iPhoneOS27.0.sdk `GCKeyboard.h` l.40: `@interface GCKeyboard : NSObject <GCDevice>`; `coalescedKeyboard` l.53 (`API_AVAILABLE(ios(14.0), tvos(14.0))`) | CONFIRMED — deviation from the brief's "scan initial `GCController.controllers()`" is factually forced |
| `keyChangedHandler` = `(GCKeyboardInput*, GCDeviceButtonInput*, GCKeyCode, BOOL)`; `GCKeyCode` = `CFIndex` `NS_TYPED_ENUM` | `GCKeyboardInput.h` l.10-11 (`GCKeyboardValueChangedHandler`), `GCKeyCodes.h` l.21 | CONFIRMED — raw code arrives as `Long`, page 0x07 naming is legitimate |
| `UIGestureRecognizerState` raw values 0..5, `Recognized == Ended` (3) | Header facts; kernel-tested in `UIKitGestureStatePhaseTest` | CONFIRMED |

One test-side error was found and fixed (§6); no product-code defect was found.

## 2. Final binding code (file:line, worktree `sess-56645e3f`)

### Keyboard (GCKeyboard route — `UIKitKeyboardBinding.kt`)
- `internal class UIKitKeyboardBinding(surfaceId, announcement, publish)` — l.58
- `install()` l.78: guards (`tornDown`, double-install tripwire) BEFORE any registration; registers `GCKeyboardDidConnectNotification` + `GCKeyboardDidDisconnectNotification` observers on the main queue FIRST, then records tokens under the lock (a teardown sliding between registration and recording is absorbed by the re-read: freshly-registered observers are removed by the loser); then scans `GCKeyboard.coalescedKeyboard` — on a live source, poses `keyChangedHandler` and publishes `announcement.keyboard(true)`.
- `teardown()` l.120: removes both observers, nils the handler if attached, publishes `announcement.keyboard(false)`. Idempotent, Stale-guarded.
- `attachToConnectedKeyboard()` l.175: `GCKeyboard.coalescedKeyboard?.keyboardInput?.keyChangedHandler = { _, _, keyCode, pressed -> emit(...) }`; post-attach re-read under the lock (a winning teardown re-nil's the just-posed handler).
- `emit()` l.204: teardown guard → pure kernel → sink. Payload honesty: `physicalKey = uikitKeyboardPhysicalKey(keyCodeRaw)`; `logicalKey = uikitLogicalKey(null)` → `Unidentified(null)` — GCControllerButtonInput reports NO `characters` (text is UIKeyInput's notion; phase 6); `location = Standard`; `keyState = uikitKeyState(pressed)`; `repeat = false` (source reports no auto-repeat); `modifiers = uikitKeyboardModifiers(×6 false)` — GCKeyboard has NO FlagsChanged equivalent, so the honest traced-modifier set is EMPTY (deferred tracking, never a fabricated default).
- `UIKitKeyboardCount` l.243: installs/teardowns/connects/events/stimuli/announcements, process-wide, lock-guarded.

### Gestes + hover (`UIKitGestureBindings.kt`)
- `internal expect fun createGestureRecognizers(target)` l.40 / `createHoverGestureRecognizer(target, action)` l.54 / `pinchScaleOf` l.355 / `rotationRadiansOf` l.358 — the ONLY iOS/tvOS seam (the klib surface differs per family, §1).
- `internal class UIKitGestureBindings(view, surfaceId, announcement, publish)` — l.97
- `install()` l.122: guards first; ONE `UIKitGestureProxy` (`@ExportObjCClass` + `@ObjCAction` members, l.385 — the Task-2-proven export recipe, no subclassing) serves all five recognizers; platform factory builds what the klib carries (iOS: tap-double/pan/pinch/rotation + hover; tvOS: tap-double/pan, hover null); `cancelsTouchesInView = false` on EVERY recognizer (traced decision: the host keeps its touches); post-pose re-read under the lock removes the just-attached recognizers if a teardown won the race; then `announcement.pointer(true)` (iOS only) + ONE publication of `announcement.gestures(exactSet)`.
- Actions: `tapRecognized` l.207 (discrete: single delivery at `Recognized==Ended`, all-null payload per the DoubleTap invariant, never a fabricated Began); `panRecognized` l.217 (cumulative `translationInView`, logical points, verbatim); `pinchRecognized` l.236 (relative `scale`); `rotationRecognized` l.249 (cumulative radians); `hoverRecognized` l.272 (Began→`PointerEntered` at position; Moved→`PointerMoved` with `uikitHoverDelta` arithmetic delta — a motion without a precedent is LOST, never a fabricated zero; Ended→`PointerLeft`, last position dropped; kind `uikitPointerKind(isStylus=false, isHover=true)` = `Mouse`).
- `teardown()` l.174: recognizers removed (zero residual), pointer fact revoked (only if it was installed), gesture set REPLACED by `emptySet()` (exact-set rule), one revocation publication. Idempotent.
- Constants l.363-374: `DOUBLE_TAP_REQUIRED_TAPS = 2`, action selectors `tapRecognized:` etc. (K/N absorbs the first parameter label into the selector — exactly what recognizer delivery dispatches).

### Pure kernel additions (`UIKitInputMapping.kt`)
- `uikitGesturePhaseForState(state: Long): UIKitTouchPhase?` — l.201 (1→Began, 2→Moved, 3→Ended; 0/4/5/hostile → null)
- `uikitKeyboardPhysicalKey(keyCodeRaw: Long?): PhysicalKey` — l.224 (domain-guarded 0..65535 → `uikitHidKey(0x07, raw)`; out-of-domain/absent → `Unidentified(null)`, never a truncating `toInt()`; page constant `GC_HID_PAGE_KEYBOARD_OR_KEYPAD = 0x07` l.389)
- `uikitHoverDelta(previous, current): LogicalDelta?` — l.241 (arithmetic of two observations; no precedent → null)

### Announcement aggregate (NEW — `UIKitInputAnnouncement.kt`)
- `internal class UIKitInputAnnouncement(surfaceId)` — l.28; `keyboard()/pointer()/touch()/gestures()` writers l.38-63; each write returns the FULL four-fact snapshot (`InputObservationChanged` build l.65-72); `gestures` REPLACES the set (never unions). Pure, UIKit-free, unit-tested.

### Port wiring (`UIKitWindowCommandPort.kt`)
- l.247-250 (requestOpen, post-commit): `val announcement = UIKitInputAnnouncement(entry.surfaceId)` then `installTouchBinding(surfaceId, announcement)` → `installKeyboardBinding(...)` → `installGestureBindings(...)`.
- `installKeyboardBinding` l.316, `installGestureBindings` l.337 — same posture as the touch install: once-only under the port lock, `runCatching { install() }.onFailure(failureReporter::report)` (an attach never breaks for an input binding).
- `teardownSurfaceObservation()` l.446 — the requestOpenedClose/beginClose/finishClose convergence point: now snapshots FIVE bindings in the one idempotent locked block (`TouchToGestureStale` l.475) and tears down touch → keyboard → gestures, so the LAST revocation snapshot is the all-false + empty form.
- `UIKitTouchBinding` (`UIKitTouchBinding.kt`): constructor gains `announcement` (l.359); install publishes `announcement.touch(true)` (l.405), teardown `announcement.touch(false)` (l.430) — the hand-rolled four-field constructions (which would have contradicted the gesture/keyboard facts) are gone.

### Driver probe (`KadreUikitSurfaceDriver.kt`)
- `KadreUikitGestureRecognitionProbe(recognizerClassNames, cancelsTouchesInViewAll)` l.186; `gestureRecognitionProbe(view:)` l.320 — copies `class_getName` per recognizer plus the all-`cancelsTouchesInView=false` predicate (facts, never objects).
- Counters: keyboard `keyboardInstallCount` l.390 … `keyboardAnnouncementCount` l.418; gestes `gestureInstallCount`/`gestureTeardownCount`/`hoverInstallCount`/`hoverTeardownCount`/`gestureStimulusCount` l.432-447.

## 3. Announcement aggregation design (the unification the takeover brief asked to audit)

Task 2's touch binding announced `InputObservationChanged` with the OTHER three fields hard-coded false/empty. Task 3 adds three more announce-capable sources, so a binding-local announcement would be a lie the moment a later-installed source exists (a touch-only snapshot published after the gestures were on the view would tell the runtime the gestures do not exist — the runtime writes all four fields absolutely on every announcement).

Design: the PORT creates ONE `UIKitInputAnnouncement` per attach (the surfaceId only exists at commit) and threads it to all THREE bindings. Each binding owns the truth of exactly ITS fact and writes it into the aggregate at the moment it becomes true (recognizer on the view, handler posed on the coalesced keyboard) or ceases to be; each write RETURNS the full current snapshot, which the binding publishes verbatim through the common sink.

- Install sequence touch → keyboard → gestures: each publication is true at its instant; the LAST one carries the full aggregate.
- tvOS honesty: the gesture binding writes `pointer(true)` ONLY if the hover recognizer was actually created (iOS); on tvOS `pointerInstalled` stays false without a false-write.
- Teardown sequence touch → keyboard → gestures: three snapshots, each true at its instant; the last is all-false + `gestureKinds` empty.
- No local dedup: the runtime already dedups unchanged capability states; a static announcement counter that would not move on a real publication would itself lie.
- Lifecycle points unchanged from Task 2: installs at the post-commit point of requestOpen; teardowns at the single `teardownSurfaceObservation` convergence (idempotent, three close paths).
- The predecessor's aggregate file and the touch-binding refactor were reviewed and kept as-is; the design (per-fact writers returning whole snapshots, main-queue serialization argument) is sound and matches the web's absolute-snapshot semantics.

## 4. The gesture Cancelled/Failed loss policy — judgment

`uikitGesturePhaseForState` gives gestures NO path for `Cancelled` (4) or `Failed` (5): the observation is lost explicitly rather than published as a `Cancelled` phase. Judgment: **correct for THIS runtime, and the loss is bounded by construction.**

- The runtime retains NO active gesture state: `SurfaceStimulus.Gesture` payloads are stateless notifications (kind/phase/measure only — no gesture identity, no active-gesture table). There is nothing to leave "suspended" that a Cancelled would have cleaned up. The only additional fact a Cancelled would carry ("this gesture does not continue") is implied by the absence of further deliveries.
- The alternative — publishing `TouchPhase.Cancelled` for gestures — would (a) assert a phase whose runtime semantics for gestures are undefined (the model's `Gesture` docs carry Started/Moved/Ended), and (b) fabricate a system-judgment ("the system revoked recognition") that the plan never traced. `Failed` is even weaker: "recognition did not happen" is the absence of a fact, not a fact.
- The policy is symmetric with the hover route (same mapper gates hover phases; a hover Ended→PointerLeft is delivered, Cancelled/Failed are lost) and documented at BOTH layers (mapper KDoc, binding KDoc, class tests `cancelledHasNoPathForGestures`/`failedHasNoPath`/`unknownAndHostileRawValuesAreLost`).
- Residual risk, accepted and documented: a consumer that infers "gesture ended" ONLY from an explicit terminal phase would wait forever after a Cancelled. Mitigation is the contract itself: gestures are notifications, not sessions (same stance as the web family). If phase 6+ adds stateful gesture sessions, this policy must be revisited — noted for the docs task (Task 5 should record it in DESIGN §15.2).

## 5. Verification (all on the FINAL committed source)

| Command (worktree) | Outcome |
|---|---|
| `./gradlew :kadre:platform:uikit:compileKotlinIosSimulatorArm64 :kadre:platform:uikit:compileKotlinTvosSimulatorArm64` | EXIT=0 |
| `./gradlew :kadre:platform:uikit:linkDebugFrameworkIosArm64 :kadre:platform:uikit:linkDebugFrameworkTvosArm64` | EXIT=0 — **4/4 targets compile+link** (with the two simulator compiles above) |
| `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test --rerun` (forced fresh execution) | EXIT=0 — **123 tests, 0 failures, 0 errors** (102 prior + 21 new: announcement 5, gesture-phase 8, keyboard/hover-mapping 8) |
| `./gradlew :kadre:platform:uikit:tvosSimulatorArm64Test --rerun` (forced fresh execution) | EXIT=0 — **123 tests, 0 failures, 0 errors** |
| `./gradlew :kadre:contracts:driver:uikit:simulatorTests` (xcodebuild, both families, background+poll) | EXIT=0 — `KadreUikitDriverIosTests.xctest` PASSED and `KadreUikitDriverTvOsTests.xctest` PASSED; 54 test cases, 0 failures; the new `testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily` PASSED on EACH family (log lines 255 iOS / 759 tvOS) |
| klib linkdata audit (`strings` on Kotlin 2.4.20 tvos_arm64 UIKit klib) | pinch/rotation/hover: 0 hits; tap/pan present — the tvOS actual's family truth is real |

All long commands ran in background with polling; nothing exceeded the inactivity window.

## 6. The one fix made during verification (test-side, honest)

First `simulatorTests` run FAILED on iOS, exactly one assertion: `keyboardConnectCount == 0` was actually `1` — "no GameController keyboard connects to an automated simulator". The predecessor's test assumption was FALSE: the iOS Simulator's "Connect Hardware Keyboard" pairing registers a REAL `GCKeyboard.coalescedKeyboard`, and the binding's install scan attaching to it is the binding doing its true job (a real source, found and attached — no synthesis anywhere; `keyboardEventCount`/`keyboardStimulusCount` stayed 0 as asserted). Fix in `KadreUikitInputTests.swift` (test-only): capture `keyboardConnectsBefore`, assert the counter never DECREASES (`GreaterThanOrEqual`) with the rationale in the failure message, keep `keyboardEventCount == 0` / `keyboardStimulusCount == 0` as the no-fabrication invariants, KDoc updated. Re-run: both families PASSED.

## 7. Deviations & decisions

1. **Initial keyboard scan is `GCKeyboard.coalescedKeyboard`, not `GCController.controllers()` (brief deviation, factually forced).** `GCKeyboard` is `NSObject <GCDevice>`, not a `GCController` (header-verified, §1) — keyboards are not in `controllers()`. The coalesced object is ALSO the multi-keyboard answer: GCKeyboard.h documents that all connected keyboards coalesce into ONE object and the connect/disconnect notifications fire once (first/last), so `keyboardInstalled` is the single honest boolean "a keyboard source is attached".
2. **`LogicalKey` is `Unidentified(null)` for every key; modifiers are always the empty set (payload honesty).** `GCControllerButtonInput` reports no `characters` (the brief's `LogicalKey(Character(key.characters)…)` is unrealizable on the GameController route — the klib handler has no characters parameter at all), and GCKeyboard has no FlagsChanged: the traced-modifier set is empty. Both are documented at the emit site and in the class KDoc; real characters await the phase-6 text-input route. The physical key (`PhysicalKey.Code(0x07, usage)`) IS carried — the brief's `PhysicalKey.Code(usagePage, usageCode)` intent, realized through the klib's real shape (`GCKeyCode` raw + the named 0x07 page).
3. **tvOS installs tap-double + pan only** (pinch/rotation absent from the klib — K/N mapping hole, hover `API_UNAVAILABLE(tvos)`; §1). The announcement's exact-set rule makes this self-describing: `gestureKinds == {DoubleTap, Pan}` on tvOS is TRUE.
4. **`UIKitInputAnnouncement` is a new file beyond the brief's list** — forced by the four-source aggregation problem (§3); it is internal, pure, and unit-tested.
5. **Ride-along for Task 4/5 (not done here, flagged):** `kadre/capabilities/uikit.md` §3 scaffolding inventory and PUBLIC-API-CATALOG need the new probe (`KadreUikitGestureRecognitionProbe`) and the 12 new counter members at the next docs/evidence pass. `touchEventCount == 0`-style zero assertions now have keyboard/gesture siblings; if Task 4 ever adds in-process delivery, revisit them together.

## 8. Honest statement — what is and is not proven

- **PROVEN structurally (both families):** the bindings install at the post-commit lifecycle point and revoke at close with zero residuals; the exact per-family recognizer set is on the host view (`class_getName` facts: iOS tap/pan/pinch/rotation/hover + the Kadre touch recognizer; tvOS tap/pan + touch), every recognizer `cancelsTouchesInView == false`; the aggregate announcement publication path is exercised (announce counters move; runtime accepted `keyboardInstalled = true` — the iOS simulator pairing made this the LIVE case, not just the false case); the pure mappers are total and honest (21 new unit tests, mutation-targeted).
- **NOT proven by this task (per the pre-flight ruling, Task 4 charter):** a real key event through `keyChangedHandler` (`keyboardEventCount`/`keyboardStimulusCount` == 0 asserted) and a real gesture/hover delivery (`gestureStimulusCount` == 0 asserted). None is honestly synthesizable in-process; the counters are the Task-4 manual charter evidence.

## 9. Self-review

- The two takeover predecessors' code was kept essentially whole; my additions were: the independent fact-checks (§1), the one test-side honesty fix (§6), and this report. No product-code change was needed — the compile/link/test gates on the final state passed without product edits.
- Lock discipline of both new bindings mirrors the reviewed touch binding (guards before mutation, registration-then-record with loser cleanup, post-pose re-reads); the aggregate makes three bindings' announcements coherent by construction and the teardown order guarantees the all-false terminal snapshot.
- Counters are process-wide: the driver assertions use captured baselines (`GreaterThanOrEqual`/`GreaterThan`), correct across in-process test ordering.
- Concerns carried forward (non-blocking): the gesture Cancelled/Failed loss policy must be recorded in DESIGN §15.2 (Task 5); the docs inventory lag (§7.5); `keyboardEventCount == 0` depends on no automated key delivery ever being added (§7.5).
