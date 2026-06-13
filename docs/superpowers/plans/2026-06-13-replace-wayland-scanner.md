# Replace wayland-scanner + gcc + .so with Kotlin XML Parser

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate `wayland-scanner public-code`, `gcc`, and `libkadre-xdg.so` by parsing wayland protocol XML directly in Kotlin and generating `wl_interface` MemorySegments in-process.

**Architecture:** A Java standalone program (`ProtocolInterfaceGenerator.java`) reads wayland protocol XML files and emits `XdgShellProtocolInterfaces.kt` — a Kotlin singleton that constructs `wl_interface`/`wl_message` structs in an `Arena` with correct ABI layout. This replaces the `SymbolLookup.loaderLookup()`-based interface variables that currently depend on the `.so`.

**Tech Stack:** Java 25 (built-in XML parser, Panama FFM), Kotlin, Gradle, Docker

**Depends on:** kextract PR #34 being merged (already done — `isAggregateGlobal` fix is in kextract master)

---

## File Changes

| Action | File | Purpose |
|--------|------|---------|
| Create | `docker/wayland-codegen/ProtocolInterfaceGenerator.java` | Standalone Java CLI that parses protocol XML → Kotlin source |
| Modify | `docker/wayland-codegen/Dockerfile` | Compile `ProtocolInterfaceGenerator.java` into image |
| Modify | `docker/wayland-codegen/generate.sh` | Remove `public-code` wayland-scanner, remove gcc, remove `--include-var`, add Java generator invocation |
| Create | `ffi/wayland/src/jvmMain/kotlin/org/graphiks/kadre/ffi/wayland/generated/XdgShellProtocolInterfaces.kt` | Generated output: Arena-based interface MemorySegments |
| Delete | `ffi/wayland/.../native/` directory | Remove `libkadre-xdg.so` resources |
| Delete | `ffi/wayland/.../WaylandXdgLib.kt` | No longer needed |
| Modify | `kadre-wayland/.../WaylandRegistry.kt` | Remove `WaylandXdgLib.loaded` guards |
| Modify | `kadre-wayland/.../WaylandXdg.kt` | Remove `WaylandXdgLib.loaded` guard |
| Modify | `kadre-wayland/.../WaylandWindow.kt` | Remove `WaylandXdgLib.loaded` guard |

---

### Task 1: Write ProtocolInterfaceGenerator.java

**Files:**
- Create: `docker/wayland-codegen/ProtocolInterfaceGenerator.java`

A self-contained Java CLI program that parses wayland protocol XML and generates Kotlin source. No external dependencies - uses `javax.xml.parsers.DocumentBuilderFactory` (built into JDK).

**Contract:**
```
Usage: java ProtocolInterfaceGenerator <xml_files...> <output_kotlin_file>
```
- Takes 1+ XML file paths, last argument is output `.kt` path
- Reads each XML, extracts `<interface>` elements with their `<request>`/`<event>`/`<arg>` children
- Generates a single Kotlin file with object `XdgShellProtocolInterfaces`

**Input XML structure (from wayland-protocols):**
```xml
<protocol name="xdg_shell">
  <interface name="xdg_wm_base" version="6">
    <request name="destroy" type="destructor" since="1"></request>
    <request name="create_positioner" since="1">
      <arg name="id" type="new_id" interface="xdg_positioner"/>
    </request>
    <request name="get_xdg_surface" since="1">
      <arg name="id" type="new_id" interface="xdg_surface"/>
      <arg name="surface" type="object" interface="wl_surface"/>
    </request>
    ...
    <event name="ping" since="1">
      <arg name="serial" type="uint"/>
    </event>
  </interface>
  ...
</protocol>
```

The `type` attribute on `<arg>` maps to wayland wire encoding:
- `int` → `"i"`, `uint` → `"u"`, `string` → `"s"`, `object` → `"o"`, `new_id` → `"n"`, `array` → `"a"`, `fd` → `"h"`, `fixed` → `"f"`

**Generated Kotlin structure:**
```kotlin
package org.graphiks.kadre.ffi.wayland.generated

import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.foreign.MemoryLayout.PathElement.*

object XdgShellProtocolInterfaces {
    private val ARENA = Arena.ofAuto()

    val xdg_wm_base_interface: MemorySegment by lazy { build_xdg_wm_base() }
    val xdg_surface_interface: MemorySegment by lazy { build_xdg_surface() }
    val xdg_toplevel_interface: MemorySegment by lazy { build_xdg_toplevel() }
    val xdg_popup_interface: MemorySegment by lazy { build_xdg_popup() }
    val xdg_positioner_interface: MemorySegment by lazy { build_xdg_positioner() }
    val zxdg_decoration_manager_v1_interface: MemorySegment by lazy { build_zxdg_decoration_manager_v1() }
    val zxdg_toplevel_decoration_v1_interface: MemorySegment by lazy { build_zxdg_toplevel_decoration_v1() }

    // Pre-requisite interfaces from libwayland-client (needed for types arrays)
    private val WL_ARENA = Arena.ofAuto()
    private val wl_lib: SymbolLookup = SymbolLookup.libraryLookup("libwayland-client.so.0", WL_ARENA)
    private val wl_surface_interface: MemorySegment = wl_lib.find("wl_surface_interface").orElseThrow()
    private val wl_seat_interface: MemorySegment = wl_lib.find("wl_seat_interface").orElseThrow()

    private val PTR: Long = ValueLayout.ADDRESS.byteSize() // 8

    private val MSG_LAYOUT: GroupLayout = MemoryLayout.structLayout(
        ADDRESS.withName("name"),        // 0-7
        ADDRESS.withName("signature"),   // 8-15
        ADDRESS.withName("types"),       // 16-23
    ) // size=24

    private val IFACE_LAYOUT: GroupLayout = MemoryLayout.structLayout(
        ADDRESS.withName("name"),        // 0-7
        JAVA_INT.withName("version"),    // 8-11
        JAVA_INT.withName("method_count"), // 12-15
        ADDRESS.withName("methods"),     // 16-23
        JAVA_INT.withName("event_count"), // 24-27
        ADDRESS.withName("events"),      // 32-39 (4 bytes padding auto-inserted)
    ) // size=40

    // For each interface, generate a builder function:
    private fun build_xdg_wm_base(): MemorySegment {
        val name = ARENA.allocateFrom("xdg_wm_base")

        // Build methods array (first count them, then allocate)
        // method 0: destroy → signature="", types=NULL
        // method 1: create_positioner → signature="n", types=[&xdg_positioner_interface, NULL]
        // method 2: get_xdg_surface → signature="no", types=[&xdg_surface_interface, &wl_surface_interface, NULL]
        val methods = ARENA.allocate(MSG_LAYOUT, 3L)

        val m0 = methods.asSlice(0 * 24)
        m0.set(ADDRESS, 0, ARENA.allocateFrom("destroy"))
        m0.set(ADDRESS, 8, ARENA.allocateFrom(""))
        m0.set(ADDRESS, 16, MemorySegment.NULL) // types = NULL-terminated empty → just NULL

        val m1 = methods.asSlice(1 * 24)
        m1.set(ADDRESS, 0, ARENA.allocateFrom("create_positioner"))
        m1.set(ADDRESS, 8, ARENA.allocateFrom("n"))
        val m1types = ARENA.allocate(ADDRESS, 2L) // 2 pointers: [&xdg_positioner, NULL]
        m1types.set(ADDRESS, 0, xdg_positioner_interface)
        m1types.set(ADDRESS, 8, MemorySegment.NULL)
        m1.set(ADDRESS, 16, m1types)

        val m2 = methods.asSlice(2 * 24)
        m2.set(ADDRESS, 0, ARENA.allocateFrom("get_xdg_surface"))
        m2.set(ADDRESS, 8, ARENA.allocateFrom("no"))
        val m2types = ARENA.allocate(ADDRESS, 3L) // 3 pointers
        m2types.set(ADDRESS, 0, xdg_surface_interface)
        m2types.set(ADDRESS, 8, wl_surface_interface)
        m2types.set(ADDRESS, 16, MemorySegment.NULL)
        m2.set(ADDRESS, 16, m2types)

        // Events: just "ping"
        val events = ARENA.allocate(MSG_LAYOUT, 1L)
        val e0 = events.asSlice(0 * 24)
        e0.set(ADDRESS, 0, ARENA.allocateFrom("ping"))
        e0.set(ADDRESS, 8, ARENA.allocateFrom("u"))  // uint serial
        e0.set(ADDRESS, 16, MemorySegment.NULL)  // no type refs

        // Build the interface struct
        val iface = ARENA.allocate(IFACE_LAYOUT)
        iface.set(ADDRESS, 0, name)                   // name
        iface.set(JAVA_INT, 8, 6)                      // version=6
        iface.set(JAVA_INT, 12, 3)                     // method_count=3
        iface.set(ADDRESS, 16, methods)                // methods
        iface.set(JAVA_INT, 24, 1)                     // event_count=1
        iface.set(ADDRESS, 32, events)                 // events
        return iface
    }
    // ... similar for each interface
}
```

**Generator logic:**
1. Parse all interfaces from all input XML files
2. For each interface, for each request and event, compute:
   - The signature string (e.g., `"no"` for a `new_id` + `object`)
   - The types array elements (which interface each typed arg references)
3. Generate:
   - A reference for each external interface (wl_surface, wl_seat) loaded via `libraryLookup`
   - A `build_<name>()` function per interface
   - A lazy top-level `val` per interface

**Important ABI detail for types arrays:** In wayland, the `types` array is a NULL-terminated array of `const struct wl_interface *` pointers. For each message arg that has an `interface` attribute (for `object` and `new_id` types), the types array includes a pointer to that interface. For all other args (primitives), the types entry is NULL. The array is terminated by an additional NULL.

**Handling cross-references between generated interfaces:**
- The generated code builds all interfaces lazily
- Cross-references within the generated file (e.g., `xdg_positioner_interface` referenced from `xdg_wm_base`) use the lazy `val` from the same object
- Cross-references to external interfaces (`wl_surface`, `wl_seat`) use the `libraryLookup` at the top

The order of interface building in the lazy vals doesn't matter because `Arena.ofAuto()` keeps all segments alive as long as the object is reachable.

- [ ] **Step 1: Write ProtocolInterfaceGenerator.java** — complete CLI program as described
- [ ] **Step 2: Compile and test manually** — `javac ProtocolInterfaceGenerator.java && java ProtocolInterfaceGenerator path/to/xdg-shell.xml path/to/xdg-decoration.xml output.kt`
- [ ] **Step 3: Commit** — `git add docker/wayland-codegen/ProtocolInterfaceGenerator.java && git commit -m "feat(codegen): add ProtocolInterfaceGenerator — XML to Kotlin wl_interface MemorySegments"`

---

### Task 2: Update Docker build pipeline

**Files:**
- Modify: `docker/wayland-codegen/Dockerfile`
- Modify: `docker/wayland-codegen/generate.sh`

**Dockerfile changes:** Add step to compile `ProtocolInterfaceGenerator.java`:
```dockerfile
RUN javac docker/wayland-codegen/ProtocolInterfaceGenerator.java
```

**generate.sh changes:**

Remove:
- Lines with `wayland-scanner public-code` (both xdg-shell and xdg-decoration)
- Lines with `gcc -shared ...` compilation + `nm -D` verification
- Lines with `--include-var` flags from kextract invocation
- Reference to removed kextract patch application (already done in earlier PR)

Add after kextract:
```bash
# ── 5. Generate wl_interface structs from protocol XML (replaces public-code + gcc) ──
echo "[gen] generating wl_interface MemorySegments from XML…"
java ProtocolInterfaceGenerator \
    "$PROTO" "$PROTO_DECO" \
    "$OUT_KT/org/graphiks/kadre/ffi/wayland/generated/XdgShellProtocolInterfaces.kt"
```

- [ ] **Step 1: Update Dockerfile** — add `javac` compilation of generator
- [ ] **Step 2: Update generate.sh** — remove public-code/gcc, add generator invocation
- [ ] **Step 3: Commit** — `git add -A && git commit -m "refactor(build): replace public-code + gcc with Java XML-to-Kotlin generator"`

---

### Task 3: Remove .so, loader, and guards

**Files:**
- Delete: `ffi/wayland/src/jvmMain/resources/native/` (entire directory)
- Delete: `ffi/wayland/src/jvmMain/kotlin/org/graphiks/kadre/ffi/wayland/WaylandXdgLib.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandRegistry.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandXdg.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandWindow.kt`

**WaylandRegistry.kt changes:**

Current lines ~236-243:
```kotlin
if (collector.xdgWmBaseName >= 0 && WaylandXdgLib.loaded) {
```
→
```kotlin
if (collector.xdgWmBaseName >= 0) {
```

Same for the decoration manager check.

**WaylandXdg.kt changes:**

Current line ~298:
```kotlin
if (!WaylandXdgLib.loaded) return null
```
→ remove this guard.

**WaylandWindow.kt changes:**

Current line ~1034:
```kotlin
if (surface != 0L && xdgWmBase != 0L && WaylandXdgLib.loaded) {
```
→
```kotlin
if (surface != 0L && xdgWmBase != 0L) {
```

Remove the `import org.graphiks.kadre.ffi.wayland.WaylandXdgLib` from all three files.

- [ ] **Step 1: Remove `native/` resource dir and `WaylandXdgLib.kt`**
  ```bash
  git rm -r ffi/wayland/src/jvmMain/resources/
  git rm ffi/wayland/src/jvmMain/kotlin/org/graphiks/kadre/ffi/wayland/WaylandXdgLib.kt
  ```
- [ ] **Step 2: Edit WaylandRegistry.kt** — remove both `WaylandXdgLib.loaded` guards and import
- [ ] **Step 3: Edit WaylandXdg.kt** — remove guard and import
- [ ] **Step 4: Edit WaylandWindow.kt** — remove guard and import
- [ ] **Step 5: Commit** — `git add -A && git commit -m "refactor: remove libkadre-xdg.so and WaylandXdgLib loader (replaced by XML parser)"`

---

### Task 4: Generate and validate

**Files:**
- Generated: `ffi/wayland/src/jvmMain/kotlin/org/graphiks/kadre/ffi/wayland/generated/XdgShellProtocolInterfaces.kt`

- [ ] **Step 1: Run generate.sh** to produce the new generated file alongside the updated kextract output
  ```bash
  bash scripts/gen-wayland-xdg.sh
  ```
- [ ] **Step 2: Verify generated file** — check that `XdgShellProtocolInterfaces.kt` compiles. Try a Gradle build:
  ```bash
  ./gradlew :ffi:wayland:compileKotlinJvm
  ```
- [ ] **Step 3: Verify API usage** — confirm the generated interface vals have the same `MemorySegment` type and names as previously (match `xdg_wm_base_interface`, `xdg_surface_interface`, etc. — the names used in WaylandRegistry.kt, WaylandXdg.kt)
- [ ] **Step 4: Commit generated output**
  ```bash
  git add ffi/wayland/src/jvmMain/kotlin/org/graphiks/kadre/ffi/wayland/generated/XdgShellProtocolInterfaces.kt
  git commit -m "feat(codegen): initial XdgShellProtocolInterfaces generated from protocol XML"
  ```

---

### Task 5: Final validation and force-push

- [ ] **Step 1: Full project build**
  ```bash
  ./gradlew build
  ```
- [ ] **Step 2: Run tests**
  ```bash
  ./gradlew test
  ```
- [ ] **Step 3: Force push the branch**
  ```bash
  git push --force-with-lease
  ```
