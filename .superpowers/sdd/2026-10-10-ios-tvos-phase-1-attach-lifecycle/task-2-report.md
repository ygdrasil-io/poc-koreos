# Task 2 Report — RuntimeWindowManager + RuntimeWindowSurface to commonMain

**Status: DONE_WITH_CONCERNS** (concern: one deviation from the Task 1 binding facts — the JVM
`RuntimeLock` actual — plus two forced micro-restructures; all documented below with rationale)
**Commit: `b19f2cf3` — `refactor(runtime): move the window manager and surface to commonMain`**
Worktree: `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f` (branch `zcode/sess-56645e3f`)

Renames recorded by git: `MinimalWindowSurface.kt` (93% similarity), `RuntimeWindowManager.kt` (94%).

Test net (all green on the committed tree):

| Suite | Command | Result |
|---|---|---|
| runtime JVM | `./gradlew :kadre:runtime:jvmTest` | SUCCESS — 408 tests, 0 failures |
| runtime iOS sim | `./gradlew :kadre:runtime:iosSimulatorArm64Test` | SUCCESS — 82 tests, 0 failures |
| runtime tvOS sim | `./gradlew :kadre:runtime:tvosSimulatorArm64Test` | SUCCESS — 82 tests, 0 failures |
| AppKit (unmodified) | `./gradlew :kadre:backend:appkit:appKitNativeTests` | SUCCESS — 373 tests, 0 failures |

`git show HEAD --name-only` contains **zero** files under `kadre/backend/appkit` and zero files
under `kadre/runtime/src/jvmTest` — the AppKit suites and the JVM suites run exactly as before.
The only test changes are the two brief-mandated appleTest additions.

---

## Step 1 findings (read before touching)

### 1a. `RuntimeWindowManager.kt` — ThreadLocal / AtomicLong / nanoTime

Verbatim (pre-move, l.118–136):

```kotlin
private val lock = Any()
private val pendingCloseWork = ThreadLocal.withInitial { ArrayDeque<() -> Unit>() }
private val drainingCloseWork = ThreadLocal.withInitial { false }
...
private val eventSequence = AtomicLong(0L)
private val eventClockOrigin = System.nanoTime()
```

**What the ThreadLocals guard.** They are the per-thread deferral machinery for close-work, with
the invariant documented at l.1589–1591:

```
// Native callbacks may re-enter any manager operation. The thread which prepared
// cleanup runs it after its outermost manager critical section. A concurrent reader
// must never steal backend work, as it may hold an unrelated callback/native lock.
```

- `pendingCloseWork` — one `ArrayDeque<() -> Unit>` **per thread**. While a thread holds the
  manager monitor it may not run owner closes (`safeCloseOwner`, l.1575–1578) or native close
  dispatches (`dispatchClose` l.1284–1285 begins with
  `check(!Thread.holdsLock(lock)) { "native close must run outside the manager lock" }`), so
  `reserveCloseLocked` (l.1280), `releaseOwnerLocked` (l.1398) and `safeCloseOwner` enqueue
  closures onto the *preparing thread's* queue. `withManagerLock` (l.1592–1596) drains that
  thread's queue in its `finally`, after the outermost critical section on that thread exits.
- `drainingCloseWork` — per-thread re-entrancy guard: queued closures themselves call
  `withManagerLock`; their nested `finally` must not re-drain recursively (l.1598–1608), and
  `remove()` on both ThreadLocals in the `finally` resets the slots.

This is **decision-tree branch (b)**: genuinely required per-thread state, not a main-thread
confinement marker. It cannot move under the manager lock (the queued work must run *outside*
the monitor, and the inverting-order test at `RuntimeWindowManagerTest:1978` proves the
concurrency contract depends on that), and it cannot become a single shared queue (a concurrent
reader must never steal backend work — the file's own comment forbids it).

**What the AtomicLong guards.** `eventSequence` numbers runtime-generated event stamps when no
session stamp source is installed (`nextEventStamp`, l.1742–1748):

```kotlin
private fun nextEventStamp(): EventStamp {
    sessionEventStampSource?.let { return it() }
    val sequence = eventSequence.getAndIncrement()
    check(sequence >= 0L) { "window event sequence exhausted" }
    val elapsed = (System.nanoTime() - eventClockOrigin).coerceAtLeast(0L).nanoseconds
    return EventStamp(SessionSequence(sequence), SessionInstant(elapsed), null)
}
```

**What `System.nanoTime()` guards.** `eventClockOrigin` (l.136) is the fixed epoch; every
fallback stamp carries `SessionInstant` = monotonic nanos since manager construction,
`.coerceAtLeast(0L)` defending against non-monotonic raw readings.

### 1b. `MinimalWindowSurface.kt` — ReentrantLock + Condition (Review Focus 1)

Verbatim (pre-move, l.169–172):

```kotlin
private val interactionDispatchLock = ReentrantLock()
private val interactionDispatchAvailable = interactionDispatchLock.newCondition()
private var interactionDispatchActive = false
private var interactionDispatchThread: Thread? = null
```

The await site (private `dispatchSynchronousInteraction`, l.449–500):

```kotlin
interactionDispatchLock.lock()
val dispatchHandler = try {
    while (interactionDispatchActive && interactionDispatchThread !== Thread.currentThread()) {
        interactionDispatchAvailable.await()
    }
    if (interactionDispatchActive) {
        false                                  // active AND same thread → nested → defer
    } else {
        interactionDispatchActive = true
        interactionDispatchThread = Thread.currentThread()
        true                                   // claimed the slot
    }
} finally {
    interactionDispatchLock.unlock()
}
val admitted = eventFactory(stamp)
val stamped = admitted.event
if (dispatchHandler) {
    try {
        interactionHandler?.dispatch(stamped, supported) { action ->
            NativeInteractionOutcome.Now(invokeNative(action))
        }
    } finally {
        surfaceInput.acceptInteraction(stamped, admitted.pointerPressure)
        while (true) {
            val nested = interactionDispatchLock.run {
                lock(); try { deferredNestedInteractions.removeFirstOrNull() } finally { unlock() }
            } ?: break
            surfaceInput.acceptInteraction(nested.event, nested.pointerPressure)
        }
        interactionDispatchLock.lock()
        try {
            interactionDispatchActive = false
            interactionDispatchThread = null
            interactionDispatchAvailable.signalAll()
        } finally {
            interactionDispatchLock.unlock()
        }
    }
} else {
    interactionDispatchLock.lock()
    try { deferredNestedInteractions += admitted } finally { interactionDispatchLock.unlock() }
}
```

**Thread-by-thread analysis — who acquires, where `await()` is reached, from which thread.**

- **Acquirers.** The only entrants are the backend SPI paths:
  `RuntimeWindowManager.dispatchSynchronousInteraction` / `dispatchSynchronousDrop`
  (`kadre/runtime/.../RuntimeWindowManager.kt` l.406–436), which resolve the surface *outside*
  the manager monitor, then call the surface. On AppKit these are reached from the native event
  callbacks (`AppKitWindowPeer.pointerDown` l.860+, `dropEntered` l.930+), which run on the
  AppKit main thread — the driver marshals there first (`AppKitWindowRuntimeDriver.onMainThread`
  → `nativePort.onMainThread`, l.314; the command queue funnels through
  `AppKitWindowCommandPort`). One backend ⇒ one dispatching thread in production.
- **`await()` reachability.** The loop condition is
  `interactionDispatchActive && interactionDispatchThread !== Thread.currentThread()`.
  - *Same thread, slot active* → no await: `dispatchHandler=false`, the event is appended to
    `deferredNestedInteractions` and later drained by the outer dispatch's `finally` via
    `surfaceInput.acceptInteraction` (handler is NOT re-entered). This is the nested-callback
    case (e.g. `BeginWindowMove` pumping the native event loop inside a handler), exercised by
    the unmodified JVM test `nestedCallbackInputWaitsUntilTheOuterCallbackInputHasBeenAdmitted`
    (RuntimeInteractionHandlerTest l.119).
  - *Different thread, slot active* → **`await()` is reached**: a second *native* thread calling
    in while a dispatch is in flight. It parks on the condition, releasing the ReentrantLock so
    the claimant can re-acquire at l.484 to publish `active=false` + `signalAll`; the waiter
    wakes, re-checks the predicate (another waiter may have claimed), and either parks again or
    claims.
- **Who actually gets here.** In production (AppKit today, UIKit in phase 1) all interaction
  dispatch is marshalled onto the single main/event thread, so the cross-thread await is only
  reachable through the public backend SPI from arbitrary threads — i.e. the JVM concurrency
  tests. On js/wasm the single event loop can only ever take the same-thread branch.

**Is the await a genuine cross-thread handoff that cannot reduce?** No. The waiter performs no
work that the claimant depends on — it simply waits its turn to become the next claimant. The
slot is a *logical* state machine (`active` flag + owner thread) guarded by a lock; the wait
"who becomes next" is exactly what a mutex acquisition decides. The reduction:

- Hold one recursive `RuntimeLock` across the whole dispatch (claim → handler → accept → drain
  → release). A cross-thread caller then blocks **on the mutex** — same blocking, same
  hand-of-the-slot semantics as await/signalAll (mutex release wakes the next waiter; the
  waiters-race-then-recheck loop is subsumed by the mutex handoff).
- Inside the critical section, `interactionDispatchActive == true` can now only mean *this
  thread's own outer dispatch* (the mutex excludes every other thread) — precisely the original
  `active && thread === current` nested case → defer. The `interactionDispatchThread` field
  (and the JVM `Thread` type) disappears.
- Deadlock parity: in the original, any other thread entering during an active dispatch blocks
  until it completes (await); in the port it blocks on the mutex. Any handler that waits on
  another thread's dispatch call deadlocked before and deadlocks now; nothing else changed,
  because the lock is private to this file and used only by this method.
- A suspend/canal restructure (brief option b) is impossible without changing the public SPI
  signature (`dispatchSynchronousInteraction` is not `suspend`) — signature changes are
  forbidden — so the under-lock form is the only behavior-preserving shape.

Replacement chosen (verbatim after):

```kotlin
val admitted = eventFactory(stamp)
val stamped = admitted.event
interactionDispatchLock.withLock {
    if (interactionDispatchActive) {
        // Re-entry while this thread holds the dispatch: the mutex is exclusive, so an
        // active dispatch observed inside the critical section can only be this thread's
        // outer dispatch — defer instead of re-entering the handler, and the outer
        // dispatch drains the queue after its handler returns.
        deferredNestedInteractions += admitted
    } else {
        interactionDispatchActive = true
        try {
            interactionHandler?.dispatch(stamped, supported) { action ->
                NativeInteractionOutcome.Now(invokeNative(action))
            }
        } finally {
            surfaceInput.acceptInteraction(stamped, admitted.pointerPressure)
            while (true) {
                val nested = deferredNestedInteractions.removeFirstOrNull() ?: break
                surfaceInput.acceptInteraction(nested.event, nested.pointerPressure)
            }
            interactionDispatchActive = false
        }
    }
}
```

Field changes: `interactionDispatchLock = RuntimeLock()`; `interactionDispatchAvailable` and
`interactionDispatchThread` deleted; `interactionDispatchActive` and
`deferredNestedInteractions` unchanged. `eventFactory(stamp)` moved before the lock — it is
pure (builds `DeferredInteraction` from the already-evaluated `stamp` default arg), so the only
observable delta is that a blocked cross-thread caller computes it before blocking instead of
after; the stamp is still taken at call entry in both versions.

New native tests (`kadre/runtime/src/appleTest/.../RuntimeWindowSurfaceConcurrencyTest.kt`,
both green on iOS and tvOS simulators): a dispatch from a second `Worker` waits for the active
dispatch and keeps its outcome (2 handler entries, 2 native outcomes, 2 input deliveries), and
a nested same-thread dispatch defers without re-entering the handler.

---

## ThreadLocal decision-tree outcome: branch (b) — `RuntimeCallFrame<T>`

New expect (`kadre/runtime/src/commonMain/.../RuntimeCallFrame.kt`), mirroring
`InteractionCallFrame`:

```kotlin
internal expect class RuntimeCallFrame<T : Any>(initial: () -> T) {
    internal fun current(): T
    internal fun set(value: T)
    internal fun clear()
}
```

Actuals: jvm = `ThreadLocal.withInitial` (get/set/remove mapped to current/set/clear); apple =
`@ThreadLocal private val runtimeCallFrameSlots = HashMap<Any, Any?>()` keyed by a per-instance
identity key (the annotation only decorates top-level state and the frame is generic); js and
wasmJs = one field per frame instance (mono-threaded, so per-instance == per-thread).

Manager usage (exact ThreadLocal semantics: `get`→`current`, `set`→`set`, `remove`→`clear`):

```kotlin
private val pendingCloseWork = RuntimeCallFrame { ArrayDeque<() -> Unit>() }
private val drainingCloseWork = RuntimeCallFrame { false }
...
private fun drainCloseWork() {
    if (lock.isHeldByCurrentThread() || drainingCloseWork.current()) return
    val work = pendingCloseWork.current()
    drainingCloseWork.set(true)
    try {
        while (work.isNotEmpty()) work.removeFirst().invoke()
    } finally {
        drainingCloseWork.clear()
        if (work.isEmpty()) pendingCloseWork.clear()
    }
}
```

Per the brief, branch (b) requires native tests: `runtimeCallFrameIsPerThread` added to
`kadre/runtime/src/appleTest/.../NativePrimitivesConcurrencyTest.kt` (per-thread isolation,
`set`, and factory-recreation after `clear`; green on both simulators).

---

## Every replacement (before → after)

RuntimeWindowManager.kt (moved to commonMain):

1. `import java.util.concurrent.atomic.AtomicLong` → `import kotlin.concurrent.atomics.AtomicLong`
   (+ `import kotlin.time.TimeSource`, `import kotlin.time.Duration`; `nanoseconds` import
   removed — unused after the clock port). Class-level
   `@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)` on `RuntimeWindowManager`
   per the Task 1 binding fact (no file-level target).
2. `private val lock = Any()` → `private val lock = RuntimeLock()`; same for
   `outcomeLock` and `updateLock` in `RuntimeWindowRequest` / `RuntimeWindow`.
3. All 81 `synchronized(lock|outcomeLock|updateLock) { ... }` → `lock|outcomeLock|updateLock.withLock { ... }`;
   every `return@synchronized` → `return@withLock`. Non-local `return`s unchanged (withLock is
   inline, exactly like `synchronized`).
4. `Thread.holdsLock(lock)` ×3 → `lock.isHeldByCurrentThread()`
   (dispatchClose check, safeCloseOwner, drainCloseWork).
5. `eventSequence.getAndIncrement()` → `eventSequence.fetchAndAdd(1)` (same "return previous"
   semantics; both stdlib atomic ops).
6. `System.nanoTime()` clock →
   `private val eventClockOrigin = monotonic.markNow()` with
   `private companion object { val monotonic = TimeSource.Monotonic }`, and in `nextEventStamp`:
   `val elapsed = (System.nanoTime() - eventClockOrigin).coerceAtLeast(0L).nanoseconds` →
   `val elapsed = eventClockOrigin.elapsedNow().coerceAtLeast(Duration.ZERO)`.
   *Deviation from the brief's literal `markNow().elapsedNanos()` shape*: `TimeMark.elapsedNanos()`
   does not exist in common kotlin.time (JVM-only); the portable `TimeMark`/`elapsedNow()` form
   preserves the monotonic-nanos semantics (Duration in/out of `SessionInstant`, negative
   elapsed clamped to zero) — note this in review.
7. All 12 `catch (cause: LinkageError) { <H> }` →
   `catch (cause: Throwable) { if (cause.isLinkageFailure()) <H> else throw cause }`
   (Task 1's ratified idiom; on JVM `isLinkageFailure()` is exactly `is LinkageError`, so
   behavior is identical — other Errors still rethrow).

MinimalWindowSurface.kt (moved to commonMain):

8. `import java.util.concurrent.locks.ReentrantLock` removed; `lock`, `eventSubscribersLock`,
   `SurfaceEventSubscriber.lock` → `RuntimeLock()`; all 20 `synchronized(...) { ... }` →
   `withLock { ... }` with label renames.
9. Dispatch restructure — verbatim above (await region).
10. 4 `catch (LinkageError)` → isLinkageFailure idiom (requestRedraw, apply, safeFailSession,
    safeReport).

---

## Two forced micro-restructures (each behavior-preserving, documented for review)

**R1 — `apply()` label collision (MinimalWindowSurface).** The original body of
`updateMutex.withLock { ... }` used `return@withLock` from *inside* a `synchronized(lock)`
block — a non-local return escaping `apply` (l.268/272/278). After `synchronized` became
`lock.withLock`, that label would rebind to the inner lambda (both lambdas carry the label
`withLock`) and silently change semantics (the failure would no longer escape `apply` and
`admission`'s type would widen to `Any`). Fix: the outer lambda is now explicitly labeled
`updateMutex.withLock surfaceApply@{ ... }` and the five returns (three inner + two outer-level,
which would otherwise become unresolved) are `return@surfaceApply`. Returns keep their exact
original targets.

**R2 — `applyUpdate()` outer-val assignment (RuntimeWindowManager).** The original assigned
function-level `val pending` / `val immediate` inside `synchronized(updateLock)`; K2 permits
that for stdlib `synchronized` but NOT for assignments to outer local `val`s inside an
`expect inline` lambda ("val cannot be reassigned" at the first such site). The lambda now
*returns* the pair and the caller destructures:

```kotlin
val (pending, immediate) = updateLock.withLock {
    ...
    if (!mutationChanged && !deferBehindFullscreenBarrier) {
        ...
        PendingWindowUpdate(operationId, null, canonicalUpdate, rejected) to
            KadreResult.Success(updateOutcome(operationId, current, rejected))
    } else {
        val queued = PendingWindowUpdate(...)
        pendingUpdates.addLast(queued)
        queued to null
    }
}
immediate?.let { return it }
```

Control flow is identical: in the immediate case `pending` is never observed (the original
returned before using it too); in the queued case `immediate == null` and `pending == queued`.

---

## Concern: one deviation from the Task 1 binding facts — the JVM `RuntimeLock` actual

`RuntimeWindowManagerTest.exclusiveDisplayLossDoesNotInvertTheManagerAndWindowLockOrder` (jvmTest,
UNMODIFIED) grabs `RuntimeWindow.updateLock` reflectively and does `synchronized(updateLock)` —
valid pre-lift because the field *was* the monitor object (`Any()`). With Task 1's actual
(`val monitor = Any()` + `synchronized(monitor)`), the test's monitor is the wrapper while the
production code locks the inner object: no contention, the test's blocking precondition times
out. No other code (production or test) references `.monitor` (grep verified).

Resolution (commit includes it):
`kadre/runtime/src/jvmCommon/.../RuntimeLock.kt` now makes the lock object its own monitor:

```kotlin
internal actual class RuntimeLock actual constructor()

internal actual inline fun <T> RuntimeLock.withLock(action: () -> T): T = synchronized(this, action)

internal actual fun RuntimeLock.isHeldByCurrentThread(): Boolean = Thread.holdsLock(this)
```

This restores the pre-lift property that the monitor **is the field's value**, so external
`synchronized(field)` participates in the same exclusion exactly as before; the API, recursion,
and `isHeldByCurrentThread` semantics are unchanged, and the js/wasm/apple actuals are
untouched. Every test runs unmodified. Flagged because Task 1's binding facts describe the
actual as `synchronized(monitor)` — if the controller prefers the wrapper-monitor shape instead,
the alternative is adapting that one white-box test seam (test modification, which the task
rules forbid), so I chose the actual change.

---

## Other self-review findings

- **Duplicate `unsupported` resolved.** After the move, the file-private
  `private fun <T> unsupported(...)` (identical body) collided with the pre-existing internal
  one in `SurfaceAdmission.kt` (OVERLOAD_RESOLUTION_AMBIGUITY). Deleted the private duplicate;
  the file now uses the shared internal helper like every other common file.
- **Step ordering note.** The brief's Step 3 compile could not run between Steps 2 and 4:
  `RuntimeWindowManager` constructs `RuntimeWindowSurface`, so neither file compiles in
  commonMain without the other. Both were ported and moved first; the Step 3 triad command then
  ran green (plus js/wasm compiles, which the moved files also feed: `compileKotlinJs`,
  `compileKotlinWasmJs` SUCCESS).
- **AtomicBoolean** was never needed: `drainingCloseWork` is per-thread state, not shared
  (branch (b)), so it became `RuntimeCallFrame<Boolean>`.
- **`Math.addExact` / `checkedAdd`**: not present in either file (grepped); no
  `CheckedArithmetic` usage introduced.
- **Signature audit**: all public/internal signatures unchanged — constructor parameter lists,
  method names, parameter types, and return types of `RuntimeWindowManager`,
  `RuntimeWindowRequest`, `RuntimeWindow`, `RuntimeWindowSurface` are byte-identical; the only
  body-level signature change is the private `dispatchSynchronousInteraction` overload whose
  *body* was restructured (name/params/return unchanged). `eventFactory(stamp)` evaluation
  moved from after-the-wait to before-the-lock (pure factory — see above).
- **kotlinx `Mutex` untouched**: `updateMutex` and its `kotlinx.coroutines.sync.withLock`
  import stay (multiplatform already); the receiver-type distinction keeps the two `withLock`
  extensions unambiguous.
- **jvmTest/AppKit untouched**: verified via `git show HEAD --name-only`.
- **Labels**: every `return@withLock` in the moved manager file targets its own lock lambda
  (the file had no pre-existing `withLock` label usage — grepped the pre-move source).

## Gradle commands and outcomes (chronological)

| Command | Outcome |
|---|---|
| `./gradlew :kadre:runtime:compileKotlinJvm :kadre:runtime:compileKotlinIosSimulatorArm64 :kadre:runtime:compileKotlinTvosSimulatorArm64` | FAILED → `apply()` label collision (`Any` admission type) — fixed via `surfaceApply@` label |
| same triad | FAILED → `elapsedNanos` unresolved in common kotlin.time — ported to `TimeMark`/`elapsedNow()` |
| same triad | FAILED → OVERLOAD_RESOLUTION_AMBIGUITY on `unsupported` — removed duplicate private |
| same triad | FAILED → leftover `Any()` monitors (`outcomeLock`/`updateLock` from lost edits) — converted |
| same triad | FAILED → `val cannot be reassigned` in `applyUpdate` — pair restructure (R2) |
| same triad | FAILED (iOS) → `stored is T` erased check in apple `RuntimeCallFrame` — unchecked cast |
| `./gradlew :kadre:runtime:compileKotlinJs :kadre:runtime:compileKotlinWasmJs` | SUCCESS |
| same triad (final) | SUCCESS — BUILD SUCCESSFUL, all three targets |
| `./gradlew :kadre:runtime:iosSimulatorArm64Test` | FAILED ×3 (appleTest compile: bogus imports, missing import, Worker capture) — fixed each |
| `./gradlew :kadre:runtime:iosSimulatorArm64Test` | SUCCESS — 82 tests, 0 failures (incl. 3 new) |
| `./gradlew :kadre:runtime:tvosSimulatorArm64Test` | SUCCESS — 82 tests, 0 failures (incl. 3 new) |
| `./gradlew :kadre:runtime:jvmTest` | FAILED — 407/408, lock-order test (RuntimeLock monitor identity) |
| (after JVM actual fix) `./gradlew :kadre:runtime:jvmTest` | SUCCESS — 408 tests, 0 failures |
| `./gradlew :kadre:backend:appkit:appKitNativeTests` | SUCCESS — 373 tests, 0 failures |
| `./gradlew :kadre:runtime:iosSimulatorArm64Test` (final tree) | SUCCESS (up-to-date; native inputs unchanged by the JVM-only actual fix) |

Commit: `git add kadre/runtime/src && git commit -m "refactor(runtime): move the window manager and surface to commonMain"` → `b19f2cf3`.
