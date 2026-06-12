# kextract Split-Output — Design

> Status: **Approved**
> Refactor kextract to generate one file per ObjC class + shared files for enums/types/protocols, enabling future extraction as a standalone project.

## Motivation

kextract v0.0.2 generates a single monolithic `AppKit_h.kt` (~36K lines) containing typealiases, enums, value-class options, ObjC class wrappers, and protocol interfaces. This is unwieldy for:

1. **Extraction as standalone project** — the generated bindings (+ ObjC runtime) should be publishable independently of Kadre
2. **Maintainability** — 36K-line files are hard to review, diff, and navigate
3. **Incremental generation** — covering Foundation + AppKit + Metal + CoreGraphics + CoreAnimation + CoreImage + AVFoundation + GameController + ModelIO + SceneKit + UniformTypeIdentifiers + PDFKit + QuickLook will produce even larger output

## Approach

**Modify kextract's KotlinToplevelBuilder** to produce multiple `KotlinSourceFile` objects instead of one, dispatching each declaration category to its own file slot. Controlled by a new `--split-output` CLI flag (default-off for backward compat).

## Output structure

```
-o <dir>/org/graphiks/kadre/ffi/objc/
├── classes/
│   ├── NSApplication.kt
│   ├── NSWindow.kt
│   ├── NSView.kt
│   ├── NSEvent.kt
│   ├── CAMetalLayer.kt
│   └── …
├── protocols/
│   ├── NSApplicationDelegate.kt
│   ├── NSWindowDelegate.kt
│   └── …
├── enums/
│   └── AppKitEnums.kt          # All NS_ENUM
├── options/
│   └── AppKitOptions.kt        # All NS_OPTIONS (value classes)
├── types/
│   └── AppKitTypes.kt          # All typealiases (typedefs)
├── _AppKit_h.kt                # Aggregator re-exporting everything (optional)
├── ObjCRuntime.kt              # Generated runtime (unchanged)
├── ObjCSubclassing.kt          # Hand-written (unchanged)
└── FoundationTypes.kt          # Hand-written (unchanged)
```

## Architecture changes in kextract

### 1. KotlinToplevelBuilder — multi-file slots

Replace the single `SourceBuilder` with a `Map<String, FileSlot>`:

```kotlin
data class FileSlot(
    val builder: SourceBuilder,
    val imports: MutableSet<String> = mutableSetOf()
)
```

Slot naming convention:
| Slot key | Destination file |
|---|---|
| `"types"` | `types/AppKitTypes.kt` |
| `"enums"` | `enums/AppKitEnums.kt` |
| `"options"` | `options/AppKitOptions.kt` |
| `"class.NSWindow"` | `classes/NSWindow.kt` |
| `"protocol.NSAppDel"` | `protocols/NSApplicationDelegate.kt` |
| `"_main"` | `_AppKit_h.kt` (aggregator) |

### 2. File naming derivation

- **Types/enums/options**: Use the header name (e.g. `AppKit`) → `AppKitTypes.kt`, `AppKitEnums.kt`, `AppKitOptions.kt`
- **Classes**: `className.kt` → `NSWindow.kt`, `NSApplication.kt`
- **Protocols**: `protocolName.kt` → `NSApplicationDelegate.kt`
- **Aggregator**: `_<headerName>_h.kt` → `_AppKit_h.kt`

### 3. Import management

Each `FileSlot` tracks its own imports. Builders register imports when they reference:
- `ObjCRuntime` (all class/protocol files)
- Other generated classes (method parameters/return types)
- `java.lang.foreign.*` (all files)
- Package-internal types from enums/types files

### 4. Builder changes

| Builder | Change |
|---|---|
| `KotlinObjCClassBuilder` | Writes to `slot("class." + className)` |
| `KotlinObjCProtocolBuilder` | Writes to `slot("protocol." + protocolName)` |
| `KotlinEnumBuilder` | Writes to `slot("enums")` |
| `KotlinTypedefBuilder` | Writes to `slot("types")` |
| `KotlinObjCCategoryBuilder` | Writes to `slot("class." + className)` alongside the class |
| `KotlinStructBuilder` | Writes to `slot("types")` |

### 5. CLI flag

Add `--split-output` / `-s` flag:
- Without flag: current behavior (single `AppKit_h.kt`)
- With flag: multi-file output structure

### 6. Aggregator file (optional)

When `--split-output` is active, generate a `_AppKit_h.kt` that re-exports all public declarations so existing consumers can import from a single location. This file contains only re-exports (`typealias`, `typealiases` of classes, etc.) and can be removed later.

## Runtime (unchanged)

- `ObjCRuntime.kt` is already a standalone file with `msgSend`, `sel`, `getClass`, `msgSendStret`
- `ObjCSubclassing.kt` is hand-written and independent
- Both support arm64 + x86_64 via `os.arch` detection in `msgSendStret`

## Regeneration script

`scripts/regen-appkit-bindings.sh` updated to:
1. Pass `--split-output` to kextract
2. Apply fixups (perl/sed) per-file instead of per-monolith
3. Additional fixups: strip `typealias Boolean = Any`, `typealias Byte = Any`, `typealias NSUInteger = Any` in `types/AppKitTypes.kt`

## Migration

1. Implement multi-file `KotlinToplevelBuilder` in kextract (this repo's fork)
2. Run regen script → generate new file tree
3. Verify existing tests still pass
4. Remove monolithic `AppKit_h.kt`
5. Update `kadre-appkit` imports if needed

## Future: standalone extraction

Once the split is complete:
- Copy `objc-runtime/` files (ObjCRuntime.kt + ObjCSubclassing.kt)
- Copy `classes/`, `protocols/`, `enums/`, `options/`, `types/`
- Publish as `org.klang.nativekit.objc` (package rename at extraction time via `-t` flag)
