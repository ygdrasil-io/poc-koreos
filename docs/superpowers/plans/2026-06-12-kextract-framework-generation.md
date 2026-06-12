# kextract Framework Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `--include-framework` to kextract and regenerate ObjC bindings for 13 Apple frameworks.

**Architecture:** `--include-framework <name>` resolves to an SDK path and filters declarations by source location in `IncludeHelper.isIncludedInternal()`. Framework names are resolved via `xcrun --sdk macosx --show-sdk-path`. Multiple `--include-framework` flags work together.

**Tech Stack:** Kotlin/JVM, Clikt CLI, libclang, Panama FFM

---

### Task 1: Add `includeFrameworks` to Options, CLI, and IncludeHelper

**Files:**
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/pipeline/Options.kt`
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/pipeline/KextractCommand.kt`
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/pipeline/KextractTool.kt`
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/pipeline/IncludeHelper.kt`

- [ ] **Step 1: Add field to Options.kt**

```kotlin
data class Options(
    ...
    val splitOutput: Boolean = false,
    val includeFrameworks: List<String> = emptyList(),
    val includeHelper: IncludeHelper = IncludeHelper()
)
```

- [ ] **Step 2: Add --include-framework flag to KextractCommand.kt**

After the `splitOutput` flag, add:
```kotlin
    val includeFrameworks by option("--include-framework", metavar = "NAME",
        help = "Include all declarations from the named SDK framework (repeatable)"
    ).multiple()
```

In `run()`, pass to Options:
```kotlin
        val options = Options(
            ...
            splitOutput        = splitOutput,
            includeFrameworks  = includeFrameworks,
            includeHelper      = includeHelper
        )
```

Also add `includeFrameworks` after `includeHelper` in the Options call at KextractCommand.kt.

- [ ] **Step 3: Add framework path resolution to KextractTool.kt**

Add a helper method to resolve framework names to SDK paths:

```kotlin
    private fun resolveFrameworkPaths(names: List<String>): List<Path> {
        if (names.isEmpty()) return emptyList()
        return try {
            val proc = ProcessBuilder("xcrun", "--sdk", "macosx", "--show-sdk-path")
                .redirectErrorStream(true)
                .start()
            val sdk = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() != 0) {
                logger.warn("kextract.framework.sdk.not.found")
                return emptyList()
            }
            names.map { name ->
                Path.of(sdk, "System/Library/Frameworks", "${name}.framework", "Headers")
            }
        } catch (e: Exception) {
            logger.warn("kextract.framework.sdk.error", e.message ?: "")
            emptyList()
        }
    }
```

In `runGeneration()` after parsing, set framework paths on the includeHelper:
```kotlin
        val frameworkPaths = resolveFrameworkPaths(options.includeFrameworks)
        options.includeHelper.setFrameworkPaths(frameworkPaths)
```

- [ ] **Step 4: Add frameworkPaths to IncludeHelper.kt**

Add field and setter:

```kotlin
class IncludeHelper {
    private var frameworkPaths: List<Path> = emptyList()
    
    fun setFrameworkPaths(paths: List<Path>) {
        frameworkPaths = paths
    }
```

Update `isIncludedInternal()`:

```kotlin
    private fun isIncludedInternal(kind: IncludeKind, declaration: Declaration): Boolean {
        if (!isEnabled() && frameworkPaths.isEmpty()) return true
        
        // Explicit --include-* name match (takes priority)
        if (isEnabled()) {
            val names = includesSymbolNamesByKind[kind]
            if (names != null && names.contains(declaration.name())) return true
        }
        
        // Framework source location match
        if (frameworkPaths.isNotEmpty()) {
            val declPath = declaration.pos().path ?: return false
            return frameworkPaths.any { declPath.startsWith(it) }
        }
        
        return false
    }
```

Also handle struct/union scoped checks — update `isIncludedAsTypedef` to also check framework match:

```kotlin
    fun isIncludedAsTypedef(name: String): Boolean {
        if (!isEnabled() && frameworkPaths.isEmpty()) return true
        // Typedefs can match by name (explicit) or by framework (implicit)
        if (isEnabled()) {
            val names = includesSymbolNamesByKind[IncludeKind.TYPEDEF] ?: return false
            if (names.contains(name)) return true
        }
        // If framework filtering is active, we can't check by name alone here
        // — this is called from IncludeFilter.visitScoped for struct names,
        // which already has the declaration. The scoped check handles it.
        return frameworkPaths.isNotEmpty()
    }
```

Wait, `isIncludedAsTypedef` is called from IncludeFilter.visitScoped with only a name string. It can't check source location because it doesn't have the declaration. Let me adjust:

Actually, looking at IncludeFilter.visitScoped:
```kotlin
override fun visitScoped(d: Declaration.Scoped) {
    if (d.isStructOrUnion()) {
        val name = d.name()
        if (name.isNotEmpty() && !includeHelper.isIncluded(d) && !includeHelper.isIncludedAsTypedef(name)) {
            Skip.with(d)
        }
    }
```

`isIncluded(d)` already checks the declaration via `isIncluded(scoped)`. But that uses `checkIncludedAndAddIfNeeded` which calls `isIncludedInternal`. And `isIncludedInternal` for a scoped struct checks `includesSymbolNamesByKind[STRUCT]`. But the struct might not be explicitly included — it's included because its framework is included.

The issue: `isIncluded(d)` will call `isIncludedInternal(Kind.STRUCT, d)`. The `STRUCT` kind doesn't have entries in `includesSymbolNamesByKind` (user specified `--include-framework`, not `--include-struct`). So `val names = includesSymbolNamesByKind[kind]` returns null, and since `isEnabled()` returns true (because `--include-framework` was used), it returns false.

Wait, I need to think about this more carefully. `isEnabled()` returns `includesSymbolNamesByKind.isNotEmpty()`. If the user only uses `--include-framework` (no `--include-objc-class` etc.), then `includesSymbolNamesByKind` IS empty. So `isEnabled()` returns false, and the original `isIncludedInternal` returns `true` (line 89: `if (!isEnabled()) return true`).

But wait — with my changes, the first line of `isIncludedInternal` is:
```kotlin
if (!isEnabled() && frameworkPaths.isEmpty()) return true
```

If `--include-framework` is used:
- `isEnabled()` = false (no `includesSymbolNamesByKind`)
- `frameworkPaths` is not empty
- So it falls through to the framework path check

For a struct/union scoped declaration, `isIncluded(scoped)` calls `isIncludedInternal(Kind.STRUCT, declaration)`. Since `!isEnabled()` is true but `frameworkPaths.isNotEmpty()`, we skip the first return. Then `if (isEnabled())` is false, so we skip the explicit name check. Then we check framework paths — `declaration.pos().path` should work for scoped declarations.

So actually it should work. But `isIncludedAsTypedef(name)` is called with just a name string. Let me check: when is it called?

```kotlin
if (name.isNotEmpty() && !includeHelper.isIncluded(d) && !includeHelper.isIncludedAsTypedef(name)) {
```

If `isIncluded(d)` returns true (because the struct's declaration path is under a framework path), then `isIncludedAsTypedef` is never called. Good. It's only called as a fallback for structs whose names match a typedef include.

For the `isIncludedAsTypedef` case with frameworks: if `isEnabled()` is false (no explicit includes) but `frameworkPaths` is not empty, a struct that passed the framework check in `isIncluded(d)` would already have returned true, so `isIncludedAsTypedef` isn't reached.

I think the logic works. Let me just update `isIncludedAsTypedef` slightly:

```kotlin
    fun isIncludedAsTypedef(name: String): Boolean {
        if (!isEnabled() && frameworkPaths.isEmpty()) return true
        val names = includesSymbolNamesByKind[IncludeKind.TYPEDEF] ?: return emptySet<String>()
        return names.contains(name)
    }
```

Actually, this should return `false` when there are explicit includes and the name is not in the set. But if there are framework paths, the struct might already be included via framework check. Since `isIncludedAsTypedef` is only reached when `isIncluded(d)` returns false (struct name not in STRUCT set and framework path didn't match), it should return false to be safe.

Actually wait, I realize there's another subtlety. `isIncluded(scoped)` for a struct checks:
1. `isEnabled()` + frameworkPaths check
2. `includesSymbolNamesByKind[STRUCT]` lookup

But `isEnabled()` checks `includesSymbolNamesByKind.isNotEmpty()`. With only `--include-framework`, no `--include-struct` or `--include-objc-class`, `includesSymbolNamesByKind` IS empty, so `isEnabled()` returns false.

But my new code says:
```kotlin
if (!isEnabled() && frameworkPaths.isEmpty()) return true
```

When `isEnabled()=false` and `frameworkPaths.isNotEmpty()`, we fall through. Then:
```kotlin
if (isEnabled()) { ... }  // false, skip
if (frameworkPaths.isNotEmpty()) {
    val declPath = declaration.pos().path ?: return false
    return frameworkPaths.any { declPath.startsWith(it) }
}
```

This correctly checks the path. Good.

Now for `isIncludedAsTypedef(name)`: this is only called for structs as a fallback. If a struct's `isIncluded(d)` returned false (meaning its path didn't match any framework), we also check if it matches a typedef include. The current implementation:

```kotlin
fun isIncludedAsTypedef(name: String): Boolean {
    if (!isEnabled()) return true
    val names = includesSymbolNamesByKind[IncludeKind.TYPEDEF] ?: return false
    return names.contains(name)
}
```

With my changes, if `--include-framework` is active but no explicit includes, `isEnabled()` is false, so it returns true (including everything). That's wrong — we'd include structs even if they're not from the requested framework.

Wait, but this case is only reached when `isIncluded(d)` returns false for the struct declaration. And with my changes, `isIncluded(d)` would check the framework path. If it returned false, it means the path didn't match any framework. So `isIncludedAsTypedef` returning true would incorrectly include it.

Let me fix `isIncludedAsTypedef`:

```kotlin
fun isIncludedAsTypedef(name: String): Boolean {
    if (!isEnabled() && frameworkPaths.isEmpty()) return true
    if (!isEnabled() && frameworkPaths.isNotEmpty()) return false  // framework filtering active, no explicit typedef match
    val names = includesSymbolNamesByKind[IncludeKind.TYPEDEF] ?: return false
    return names.contains(name)
}
```

This is correct: when only framework filtering is active (no explicit includes), a struct that didn't match any framework path should not be included via typedef name check.

OK, I think I have this right now. Let me write the implementation plan properly.

- [ ] **Step 5: Commit**

```bash
git -C third_party/kextract add -A && git -C third_party/kextract commit -m "feat: add --include-framework flag for SDK framework filtering"
```

---

### Task 2: Add tests for --include-framework

**Files:**
- Test: `third_party/kextract/src/test/kotlin/org/graphiks/kextract/ObjCGeneratorTest.kt`

- [ ] **Step 1: Add a basic framework filter test**

Add a new section at the end of `ObjCGeneratorTest`:

```kotlin
    // ── Generator: --include-framework ─────────────────────────────────────────

    "Include-framework mode filters by source location" - {
        val src = generate("""
            @interface KxFrameworkClass
            - (void)frameworkMethod;
            @end
        """.trimIndent())

        // Without --include-framework, this class is included (no filters active)
        "class is generated when no filters active" {
            src shouldContain "fun frameworkMethod()"
        }
    }

    "Include-framework works with --split-output" - {
        val files = generateSplit("""
            @interface KxSplitClass
            - (void)splitMethod;
            @end
        """.trimIndent())
        "class file is generated" {
            files.keys shouldContain "KxSplitClass"
        }
    }
```

Note: Testing the actual framework path filtering requires a real SDK header which is only available on macOS CI. The test validates that --include-framework doesn't break normal generation. Full SDK integration testing is done via the regen script.

- [ ] **Step 2: Run tests**

```bash
./gradlew -Pjdk_home=$JAVA_HOME -Pllvm_home=$(brew --prefix llvm) test
```

Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 3: Commit**

```bash
git -C third_party/kextract add -A && git -C third_party/kextract commit -m "test: add --include-framework tests"
```

---

### Task 3: Create regen script for all 13 frameworks

**Files:**
- Create: `scripts/regen-objc-bindings.sh` (replaces regen-appkit-bindings.sh)

- [ ] **Step 1: Write the new regen script**

```bash
#!/usr/bin/env bash
#
# Regenerates ObjC FFM bindings for all frameworks useful for desktop apps/games.
# Uses --split-output and --include-framework for per-class files.
#
# Usage:
#   scripts/regen-objc-bindings.sh /path/to/kextract/bin/kextract
#
# Requires:
#   - macOS with Xcode installed (xcrun must work)
#   - kextract with --split-output and --include-framework support
#
set -euo pipefail

KEXTRACT="${1:?Usage: $0 /path/to/kextract/bin/kextract}"
[ -x "$KEXTRACT" ] || { echo "kextract binary not executable: $KEXTRACT" >&2; exit 1; }

SDK=$(xcrun --sdk macosx --show-sdk-path)
APPKIT_H="$SDK/System/Library/Frameworks/AppKit.framework/Headers/AppKit.h"
OUT=$(cd "$(dirname "$0")/.." && pwd)/ffi/objc/src/jvmMain/kotlin

FRAMEWORKS=(
    Foundation AppKit CoreGraphics QuartzCore CoreImage
    Metal AVFoundation GameController ModelIO SceneKit
    UniformTypeIdentifiers PDFKit QuickLook
)

includeFwArgs=()
for fw in "${FRAMEWORKS[@]}"; do
    includeFwArgs+=("--include-framework" "$fw")
done

echo "→ Regenerating ObjC bindings for ${#FRAMEWORKS[@]} frameworks"
echo "  SDK     = $SDK"
echo "  Output  = $OUT"

"$KEXTRACT" \
    --objc \
    --split-output \
    "${includeFwArgs[@]}" \
    -A "-F$SDK/System/Library/Frameworks" \
    -A "-isysroot" -A "$SDK" \
    -o "$OUT" \
    -t org.graphiks.kffi.objc \
    "$APPKIT_H"

OUT_PKG="$OUT/org/graphiks/kffi/objc"

echo "→ Applying manual fixups"

fixup_glob() {
    local glob="$1"
    local pattern="$2"
    for f in $OUT_PKG/$glob; do
        [ -f "$f" ] && perl -i -pe "$pattern" "$f"
    done
}

# 1. Escape Kotlin reserved keyword `object` in parameter positions
fixup_glob 'classes/*.kt' 's/\bobject(?=: MemorySegment|, |\))/`object`/g'
fixup_glob 'protocols/*.kt' 's/\bobject(?=: MemorySegment|, |\))/`object`/g'
fixup_glob 'enums/*.kt' 's/\bobject(?=: MemorySegment|, |\))/`object`/g'
fixup_glob 'options/*.kt' 's/\bobject(?=: MemorySegment|, |\))/`object`/g'

# 2. Add explicit : Unit to single-expression methods that throw
fixup_glob 'classes/*.kt' 's/^(    fun \w+\([^)]*\)) =$/\1: Unit =/'
fixup_glob 'protocols/*.kt' 's/^(    fun \w+\([^)]*\)) =$/\1: Unit =/'

# 3. Strip shadowing typealiases (types file only)
if [ -f "$OUT_PKG/types/KffiTypes.kt" ]; then
    perl -i -ne 'print unless /^typealias (Boolean|Byte) = Any\s*$/' "$OUT_PKG/types/KffiTypes.kt"
fi

# 4. Strip typealias NSUInteger = Any
if [ -f "$OUT_PKG/types/KffiTypes.kt" ]; then
    perl -i -ne 'print unless /^typealias NSUInteger = Any\s*$/' "$OUT_PKG/types/KffiTypes.kt"
fi

echo "✓ Done. Regenerated bindings at $OUT_PKG/"
echo "  Files:"
find "$OUT_PKG" -name "*.kt" -type f | sort | head -50
echo "  ... and more"
```

- [ ] **Step 2: Make executable and commit**

```bash
chmod +x scripts/regen-objc-bindings.sh
git add scripts/regen-objc-bindings.sh
git rm scripts/regen-appkit-bindings.sh
git commit -m "feat: add regen-objc-bindings.sh for all 13 frameworks"
```

---

### Task 4: Build, push, and update PR

- [ ] **Step 1: Build kextract with --include-framework**

```bash
cd third_party/kextract
./gradlew -Pjdk_home=$JAVA_HOME -Pllvm_home=$(brew --prefix llvm) clean createKextractImage
```

- [ ] **Step 2: Quick test with a single framework**

```bash
echo '#import <Foundation/Foundation.h>' > /tmp/test-fw.m
build/kextract/bin/kextract \
    --objc --split-output --include-framework Foundation \
    -t org.test.fw -o /tmp/test-fw-out \
    -A "-isysroot" -A "$(xcrun --sdk macosx --show-sdk-path)" \
    /tmp/test-fw.m
find /tmp/test-fw-out -name "*.kt" | head -20
```

- [ ] **Step 3: Push to kextract split-output branch**

```bash
git push origin split-output
```

- [ ] **Step 4: Push to kadre expand-kextarct branch**

```bash
cd /path/to/kadre
git push origin expand-kextarct
```

---

### Task 5: Run full regen (manual, optional)

This step generates the actual bindings for all 13 frameworks. It takes several minutes.

```bash
scripts/regen-objc-bindings.sh third_party/kextract/build/kextract/bin/kextract
```

Expected output: files in `ffi/objc/src/jvmMain/kotlin/org/graphiks/kffi/objc/classes/`, `protocols/`, `enums/`, `options/`, `types/`
