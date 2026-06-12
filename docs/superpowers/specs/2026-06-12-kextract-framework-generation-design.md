# kextract Framework Generation — Design

> Status: **Approved**
> Add `--include-framework` to kextract and regenerate bindings for all ObjC frameworks useful for desktop apps/games.

## Motivation

The previous `--split-output` feature added per-class file generation, but the regen script only covers 5 AppKit classes. To extract bindings as a standalone project, we need full coverage of all relevant Apple frameworks:

- Foundation, AppKit, CoreGraphics, CoreAnimation (QuartzCore), CoreImage
- Metal, AVFoundation, GameController, ModelIO, SceneKit
- UniformTypeIdentifiers, PDFKit, QuickLook

## Approach

Add `--include-framework <name>` flag to kextract that filters declarations by source location — keeping only declarations whose source file path falls under the specified framework's SDK directory.

## Changes to kextract

### 1. CLI: `--include-framework <name>` (repeatable)

New flag in `KextractCommand.kt`:
```kotlin
val includeFrameworks by option("--include-framework", metavar = "NAME",
    help = "Include all declarations from the named SDK framework (repeatable)"
).multiple()
```

### 2. Options / IncludeHelper: framework path resolution

`IncludeHelper` gets a `setFrameworkPaths()` method. Framework names are resolved to SDK paths at CLI time:

```kotlin
fun resolveFrameworkPaths(names: List<String>): List<Path> {
    val sdk = runProcess("xcrun", "--sdk", "macosx", "--show-sdk-path").trim()
    return names.map { name ->
        Path.of(sdk, "System/Library/Frameworks", "${name}.framework", "Headers")
    }
}
```

### 3. IncludeHelper.isIncludedInternal(): source location check

```kotlin
private fun isIncludedInternal(kind: IncludeKind, declaration: Declaration): Boolean {
    // If no filters active at all, include everything
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

Key behaviors:
- `--include-framework` and `--include-objc-class` can be combined (explicit wins)
- If only `--include-framework` is used (no `--include-objc-class`), the framework path check determines inclusion
- Declarations from non-listed frameworks (e.g., IOKit pulled in transitively) are skipped

### 4. KextractCommand.kt: wire to Options

```kotlin
val options = Options(
    ...
    includeFrameworks = includeFrameworks,
    ...
)
```

## Regen script update

`scripts/regen-appkit-bindings.sh → scripts/regen-objc-bindings.sh`

The script is renamed and expanded to cover all 13 frameworks:

```bash
FRAMEWORKS=(
    Foundation AppKit CoreGraphics QuartzCore CoreImage
    Metal AVFoundation GameController ModelIO SceneKit
    UniformTypeIdentifiers PDFKit QuickLook
)

for fw in "${FRAMEWORKS[@]}"; do
    includeFwArgs+="--include-framework $fw "
done

"$KEXTRACT" \
    --objc \
    --split-output \
    $includeFwArgs \
    -A "-F$SDK/System/Library/Frameworks" \
    -A "-isysroot" -A "$SDK" \
    -o "$OUT" \
    -t org.graphiks.kffi.objc \
    "$APPKIT_H"
```

## Output

After regeneration, `ffi/objc/src/jvmMain/kotlin/org/graphiks/kffi/objc/` contains:

```
├── classes/           # 1 file per ObjC class (all frameworks)
├── protocols/         # 1 file per ObjC protocol
├── enums/             # NS_ENUM from all frameworks
├── options/           # NS_OPTIONS (value classes) from all frameworks
├── types/             # Typedefs + structs from all frameworks
├── functions/         # C functions + globals (rare in ObjC)
├── ObjCRuntime.kt     # Runtime (generated)
├── ObjCSubclassing.kt # Runtime (hand-written)
└── FoundationTypes.kt # Manual overrides (NSUInteger, NSPoint, etc.)
```

## Validation

1. Build kextract with `--include-framework`
2. Run existing unit tests (non-regression)
3. Run regen script with a small test framework (e.g., just Foundation)
4. Verify generated files compile in a test project
5. Run full regen for all 13 frameworks
