# Wayland Runtime CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real Wayland runtime coverage in CI by running `kadre-wayland` tests against a headless Weston compositor.

**Architecture:** Keep the runtime harness in a small reusable shell script so the GitHub Actions workflow stays readable and local/CI execution share the same startup sequence. Add a skip-aware JVM test that proves `wl_display_connect(null)` succeeds when a compositor is present, without requiring GPU rendering or a long-running application loop.

**Tech Stack:** Kotlin/JVM tests, Java 25 FFM bindings, Gradle, GitHub Actions, Weston headless compositor, Bash.

---

### Task 1: Add A Real Wayland Runtime Smoke Test

**Files:**
- Modify: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandSmokeTest.kt`

- [x] **Step 1: Write the failing test**

Add a test that attempts a real `wl_display_connect(null)` only when both Linux and `WAYLAND_DISPLAY` are present. It must skip outside a Wayland runtime so macOS/Windows/local non-Wayland runs stay green.

```kotlin
@Test
fun `wl_display_connect succeeds when WAYLAND_DISPLAY is configured`() {
    val os = System.getProperty("os.name", "").lowercase()
    if (!os.contains("linux")) return
    val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
    if (waylandDisplay.isNullOrBlank()) return
    if (waylandNativeDisabled()) return
    val connect = wlDisplayConnect ?: error("wl_display_connect binding is not available")
    val disconnect = wlDisplayDisconnect ?: error("wl_display_disconnect binding is not available")
    val display = connect.invokeExact(java.lang.foreign.MemorySegment.NULL) as java.lang.foreign.MemorySegment
    try {
        kotlin.test.assertFalse(display == java.lang.foreign.MemorySegment.NULL)
    } finally {
        if (display != java.lang.foreign.MemorySegment.NULL) {
            disconnect.invokeExact(display)
        }
    }
}
```

- [x] **Step 2: Run test to verify it fails or skips before script work**

Run: `./gradlew :kadre-wayland:jvmTest --tests 'org.graphiks.kadre.wayland.WaylandSmokeTest' --no-daemon --stacktrace`

Expected locally on macOS: PASS with the new test skipped by early return. On Linux without Wayland: PASS by early return. The CI-specific runtime assertion will be exercised by Task 2.

- [x] **Step 3: Keep implementation minimal**

Do not create windows in this test. The first runtime gate only proves the CI compositor is actually reachable through the FFM binding.

- [x] **Step 4: Run test again**

Run: `./gradlew :kadre-wayland:jvmTest --tests 'org.graphiks.kadre.wayland.WaylandSmokeTest' --no-daemon --stacktrace`

Expected: PASS.

### Task 2: Add A Reusable Weston CI Runner

**Files:**
- Create: `scripts/ci-wayland-runtime.sh`

- [x] **Step 1: Write the runner script**

Create a Bash script that starts Weston headless, waits for the socket, exports Wayland/Kadre env vars, and runs the given command.

```bash
#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "[ci-wayland-runtime] ERROR: missing command to execute" >&2
  exit 2
fi

if ! command -v weston >/dev/null 2>&1; then
  echo "[ci-wayland-runtime] ERROR: weston is not installed" >&2
  exit 1
fi

export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/kadre-wayland-runtime}"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

SOCKET_NAME="${KADRE_WAYLAND_SOCKET:-wayland-ci}"
SOCKET_PATH="$XDG_RUNTIME_DIR/$SOCKET_NAME"
WESTON_LOG="${WESTON_LOG:-/tmp/kadre-weston.log}"
WESTON_STDOUT="${WESTON_STDOUT:-/tmp/kadre-weston.stdout}"

cleanup_weston() {
  if [ -n "${WESTON_PID:-}" ] && kill -0 "$WESTON_PID" >/dev/null 2>&1; then
    kill "$WESTON_PID" >/dev/null 2>&1 || true
    wait "$WESTON_PID" >/dev/null 2>&1 || true
  fi
}

print_weston_logs() {
  echo "[ci-wayland-runtime] Weston log: $WESTON_LOG" >&2
  cat "$WESTON_LOG" 2>/dev/null || true
  echo "[ci-wayland-runtime] Weston stdout/stderr: $WESTON_STDOUT" >&2
  cat "$WESTON_STDOUT" 2>/dev/null || true
}

trap cleanup_weston EXIT INT TERM

rm -f "$SOCKET_PATH"

weston \
  --backend=headless-backend.so \
  --renderer=pixman \
  --width=800 \
  --height=600 \
  --socket="$SOCKET_NAME" \
  --idle-time=0 \
  --log="$WESTON_LOG" \
  >"$WESTON_STDOUT" 2>&1 &
WESTON_PID=$!

for _ in {1..60}; do
  if [ -S "$SOCKET_PATH" ]; then
    break
  fi
  if ! kill -0 "$WESTON_PID" >/dev/null 2>&1; then
    echo "[ci-wayland-runtime] ERROR: Weston exited before creating socket: $SOCKET_PATH" >&2
    print_weston_logs
    exit 1
  fi
  sleep 0.5
done

if [ ! -S "$SOCKET_PATH" ]; then
  echo "[ci-wayland-runtime] ERROR: Wayland socket did not appear: $SOCKET_PATH" >&2
  print_weston_logs
  exit 1
fi

export WAYLAND_DISPLAY="$SOCKET_NAME"
export KADRE_LINUX_BACKEND=wayland
unset KADRE_WAYLAND_DISABLE_NATIVE

(
  watched_pid=$$
  trap '' INT TERM
  while kill -0 "$watched_pid" >/dev/null 2>&1; do
    sleep 1
  done
  cleanup_weston
) &

trap - EXIT INT TERM

echo "[ci-wayland-runtime] Weston ready on WAYLAND_DISPLAY=$WAYLAND_DISPLAY"
exec "$@"
```

- [x] **Step 2: Make it executable**

Run: `chmod +x scripts/ci-wayland-runtime.sh`

- [x] **Step 3: Validate syntax**

Run: `bash -n scripts/ci-wayland-runtime.sh`

Expected: exit 0.

### Task 3: Wire The Runner Into GitHub Actions

**Files:**
- Modify: `.github/workflows/ci.yml`

- [x] **Step 1: Update Wayland package installation**

In `linux-wayland-build`, include `weston` in the installed packages:

```yaml
run: sudo apt-get install -y libwayland-dev wayland-protocols libxkbcommon-dev weston
```

- [x] **Step 2: Replace disabled-native test execution**

Remove:

```yaml
env:
  KADRE_WAYLAND_DISABLE_NATIVE: '1'
```

Replace the Gradle invocation with:

```yaml
run: |
  scripts/ci-wayland-runtime.sh ./gradlew \
    :kadre-core:jvmTest \
    :kadre-wayland:jvmTest \
    --no-daemon \
    --stacktrace
```

- [x] **Step 3: Update comments**

The job comments must say that Weston headless is started and that runtime connection tests execute against the compositor. They must not claim there is no compositor in CI.

- [x] **Step 4: Validate the workflow text**

Run: `grep -n "KADRE_WAYLAND_DISABLE_NATIVE" .github/workflows/ci.yml`

Expected: no matches.

Run: `grep -n "ci-wayland-runtime" .github/workflows/ci.yml`

Expected: one or more matches in `linux-wayland-build`.

### Task 4: Verify And Commit

**Files:**
- Test all files touched above.

- [x] **Step 1: Run local tests that do not require Linux**

Run: `./gradlew :kadre-wayland:jvmTest --no-daemon --stacktrace`

Expected on macOS: PASS; runtime test skips because host is not Linux.

- [x] **Step 2: Run script syntax validation**

Run: `bash -n scripts/ci-wayland-runtime.sh`

Expected: exit 0.

- [x] **Step 3: Inspect diff**

Run: `git diff -- .github/workflows/ci.yml scripts/ci-wayland-runtime.sh kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandSmokeTest.kt docs/superpowers/plans/2026-06-23-wayland-runtime-ci.md`

Expected: only the planned runtime CI changes.

- [ ] **Step 4: Commit**

Deferred to the controller; implementation subagent must not commit.

```bash
git add .github/workflows/ci.yml scripts/ci-wayland-runtime.sh kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandSmokeTest.kt docs/superpowers/plans/2026-06-23-wayland-runtime-ci.md
git commit -m "ci: run Wayland tests against headless Weston"
```
