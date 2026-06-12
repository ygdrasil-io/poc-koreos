# kextract Split-Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modify kextract to generate one Kotlin file per ObjC class + shared files for enums/options/types, controlled by `--split-output`.

**Architecture:** `KotlinToplevelBuilder` maintains a `Map<String, SourceBuilder>` (one per slot). Each builder dispatches to the appropriate slot based on declaration type. Slot key → subdirectory mapping determines file paths.

**Tech Stack:** Kotlin/JVM, libclang, Panama FFM, Clikt CLI

---

### Task 1: Add `splitOutput` to Options, CLI, and pipeline

**Files:**
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/pipeline/Options.kt`
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/pipeline/KextractCommand.kt`
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/pipeline/KextractTool.kt`
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/kotlin/KotlinGenerator.kt`

- [ ] **Step 1: Add field to Options.kt**

```kotlin
data class Options(
    val clangArgs: List<String> = emptyList(),
    val libraries: List<Library> = emptyList(),
    val useSystemLoadLibrary: Boolean = false,
    val targetPackage: String = "",
    val outputDir: String = ".",
    val sharedClassName: String? = null,
    val splitOutput: Boolean = false,
    val includeHelper: IncludeHelper = IncludeHelper()
)
```

- [ ] **Step 2: Add --split-output flag to KextractCommand.kt**

After the `objc` flag (line 87), add:
```kotlin
    val splitOutput by option("--split-output",
        help = "Generate one file per ObjC class + separate files for enums/options/types"
    ).flag()
```

In `run()`, pass it to Options (add `splitOutput = splitOutput`):
```kotlin
        val options = Options(
            ...
            sharedClassName    = symbolsClass,
            splitOutput        = splitOutput,
            includeHelper      = includeHelper
        )
```

- [ ] **Step 3: Pass splitOutput through KotlinGenerator.kt**

```kotlin
    fun generate(
        scoped: Declaration.Scoped,
        headerName: String,
        targetPackage: String,
        libraries: List<Options.Library> = emptyList(),
        useSystemLoadLibrary: Boolean = false,
        splitOutput: Boolean = false
    ): List<KotlinSourceFile> {
        val className = sanitizeClassName(headerName)
        val toplevel = KotlinToplevelBuilder(
            targetPackage, className, headerName, libraries, useSystemLoadLibrary, splitOutput
        )
        ...
    }
```

- [ ] **Step 4: Pass splitOutput through KextractTool.kt**

In `generate()` private method, pass the option:
```kotlin
        return KotlinGenerator().generate(
            transformed, headerName, options.targetPackage,
            options.libraries, options.useSystemLoadLibrary,
            options.splitOutput
        )
```

- [ ] **Step 5: Commit**

```bash
git -C third_party/kextract add -A && git -C third_party/kextract commit -m "feat: add --split-output CLI option"
```

---

### Task 2: Add `subDirectory` to `KotlinSourceFile`

**File:**
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/kotlin/models/KotlinSourceFile.kt`

- [ ] **Step 1: Add `subDirectory` field**

```kotlin
data class KotlinSourceFile(
    val packageName: String,
    val className: String,
    val contents: String,
    val subDirectory: String = ""
) {
    fun getPath(): Path = if (subDirectory.isEmpty())
        Path.of(packageName.replace('.', '/'), "$className.kt")
    else
        Path.of(packageName.replace('.', '/'), subDirectory, "$className.kt")

    fun getQualifiedName(): String = "$packageName.$className"
}
```

All existing callers pass 3 args → `subDirectory` defaults to `""` → backward compat.

- [ ] **Step 2: Commit**

```bash
git -C third_party/kextract add -A && git -C third_party/kextract commit -m "refactor: add subDirectory field to KotlinSourceFile"
```

---

### Task 3: Refactor `KotlinToplevelBuilder` for multi-file dispatch

**File:**
- Modify: `third_party/kextract/src/main/kotlin/org/graphiks/kextract/kotlin/builders/KotlinToplevelBuilder.kt`

This is the core change. The builder manages a `Map<String, SourceBuilder>` with slot keys determining output subdirectories. The preamble (package + imports + kextract_runtime + LOOKUP) is emitted at `getFiles()` time rather than in `init`, so each file gets correct content in split mode.

- [ ] **Step 1: Replace class declaration and fields**

Replace from line 14 to line 30 (fields section):

```kotlin
class KotlinToplevelBuilder(
    private val targetPackage: String,
    val className: String,
    private val headerName: String,
    private val libraries: List<Options.Library> = emptyList(),
    private val useSystemLoadLibrary: Boolean = false,
    private val splitOutput: Boolean = false
) : Declaration.Visitor<Unit> {
    private val slots = LinkedHashMap<String, SourceBuilder>()
    private val files = mutableListOf<KotlinSourceFile>()
    private val headerBuilder = KotlinHeaderBuilder(mainSlot, this)
    private val structBuilder = KotlinStructBuilder(mainSlot, this)
    private val typedefBuilder = KotlinTypedefBuilder(mainSlot, this)
    private var objcClassBuilder = KotlinObjCClassBuilder(mainSlot, this)
    private val objcProtocolBuilder = KotlinObjCProtocolBuilder(mainSlot, this)
    private val objcCategoryBuilder = KotlinObjCCategoryBuilder(mainSlot, this)
    private val enumBuilder = KotlinEnumBuilder(mainSlot, this)

    /** Base name derived from header filename (e.g. "AppKit" from "AppKit_h"). */
    private val headerBaseName: String = className.removeSuffix("_h")

    /** True if any ObjC declaration was encountered — triggers ObjCRuntime.kt emission. */
    var needsObjCRuntime: Boolean = false
        private set

    /** True when a LOOKUP val was generated (libraries were provided). */
    val hasLookup: Boolean get() = libraries.isNotEmpty()

    private fun getOrCreateSlot(key: String): SourceBuilder = slots.getOrPut(key) { SourceBuilder() }

    /** In non-split mode: everything writes to _main. In split mode: dispatch by type. */
    private val mainSlot: SourceBuilder get() = getOrCreateSlot("_main")
```

- [ ] **Step 2: Replace `builder` → `mainSlot` in init block**

The init block (original lines 39-93) writes package + imports + `kextract_runtime` + LOOKUP to `builder`. Replace `builder` → `mainSlot` throughout:

The full init block after replacement:
```kotlin
    init {
        // Package declaration
        if (targetPackage.isNotEmpty()) {
            mainSlot.appendLine("package $targetPackage")
            mainSlot.appendLine()
        }

        // Standard imports
        mainSlot.appendLine("import java.lang.invoke.*")
        mainSlot.appendLine("import java.lang.foreign.*")
        mainSlot.appendLine("import java.lang.foreign.MemoryLayout.PathElement.*")
        mainSlot.appendLine()

        // Helper constants for layouts
        mainSlot.appendLine("private object kextract_runtime {")
        mainSlot.indent()
        mainSlot.appendLine("val C_BOOL: ValueLayout = ValueLayout.JAVA_BOOLEAN")
        mainSlot.appendLine("val C_CHAR: ValueLayout = ValueLayout.JAVA_BYTE")
        mainSlot.appendLine("val C_SHORT: ValueLayout = ValueLayout.JAVA_SHORT")
        mainSlot.appendLine("val C_INT: ValueLayout = ValueLayout.JAVA_INT")
        mainSlot.appendLine("val C_LONG: ValueLayout = ValueLayout.JAVA_LONG")
        mainSlot.appendLine("val C_LONG_LONG: ValueLayout = ValueLayout.JAVA_LONG")
        mainSlot.appendLine("val C_FLOAT: ValueLayout = ValueLayout.JAVA_FLOAT")
        mainSlot.appendLine("val C_DOUBLE: ValueLayout = ValueLayout.JAVA_DOUBLE")
        mainSlot.appendLine("val C_POINTER: ValueLayout = ValueLayout.ADDRESS")
        mainSlot.unindent()
        mainSlot.appendLine("}")
        mainSlot.appendLine()

        // Symbol lookup — loads native libraries and exposes a single LOOKUP
        if (libraries.isNotEmpty()) {
            mainSlot.appendLine("private val LOOKUP: SymbolLookup = run {")
            mainSlot.indent()
            if (useSystemLoadLibrary) {
                for (lib in libraries) {
                    mainSlot.appendLine("System.loadLibrary(\"${lib.libSpec}\")")
                }
                mainSlot.appendLine("SymbolLookup.loaderLookup()")
            } else {
                mainSlot.appendLine("var lu: SymbolLookup = SymbolLookup.loaderLookup()")
                for (lib in libraries) {
                    val lookup = when (lib.specKind) {
                        Options.Library.SpecKind.PATH ->
                            "SymbolLookup.libraryLookup(\"${Options.Library.toQuotedName(lib)}\", Arena.global())"
                        Options.Library.SpecKind.NAME ->
                            "SymbolLookup.libraryLookup(\"${lib.libSpec}\", Arena.global())"
                    }
                    mainSlot.appendLine("lu = $lookup.or(lu)")
                }
                mainSlot.appendLine("lu")
            }
            mainSlot.unindent()
            mainSlot.appendLine("}")
            mainSlot.appendLine()
        }
    }
```

In split mode, the `_main` slot ends up with just the preamble — no declarations write to it (they dispatch to typed slots). The preamble-only file is harmless dead code.

Also update the TOPLEVEL pre-scan section (around original line 148): change `builder` to `mainSlot`:
```kotlin
                    objcClassBuilder = KotlinObjCClassBuilder(mainSlot, this, generatedObjCClassNames)
```

- [ ] **Step 3: Update visitor methods to dispatch to per-class/protocol slots**

```kotlin
    override fun visitTypedef(decl: Declaration.Typedef) {
        if (Skip.isPresent(decl)) return
        val sb = if (splitOutput) getOrCreateSlot("types") else mainSlot
        KotlinTypedefBuilder(sb, this).visitTypedef(decl)
    }

    override fun visitObjCClass(decl: Declaration.ObjCClass) {
        if (Skip.isPresent(decl)) return
        needsObjCRuntime = true
        if (splitOutput) {
            val sb = getOrCreateSlot("class.${decl.name()}")
            val genNames = generatedClassNames()  // computed in visitScoped
            KotlinObjCClassBuilder(sb, this, genNames).visitClass(decl)
        } else {
            objcClassBuilder.visitClass(decl)
        }
    }

    override fun visitObjCProtocol(decl: Declaration.ObjCProtocol) {
        if (Skip.isPresent(decl)) return
        needsObjCRuntime = true
        if (splitOutput) {
            val sb = getOrCreateSlot("protocol.${decl.name()}")
            KotlinObjCProtocolBuilder(sb, this).visitProtocol(decl)
        } else {
            objcProtocolBuilder.visitProtocol(decl)
        }
    }

    override fun visitObjCCategory(decl: Declaration.ObjCCategory) {
        if (Skip.isPresent(decl)) return
        needsObjCRuntime = true
        if (splitOutput) {
            val sb = getOrCreateSlot("class.${decl.extendedClass()}")
            KotlinObjCCategoryBuilder(sb, this).visitCategory(decl)
        } else {
            objcCategoryBuilder.visitCategory(decl)
        }
    }
```

- [ ] **Step 5: Update enum dispatch in visitScoped for split mode**

In `visitScoped`, the `ENUM` case calls `enumBuilder.visitEnum(decl)`. Change to:
```kotlin
            Declaration.Scoped.Kind.ENUM -> {
                if (decl.name().isNotEmpty()) {
                    if (splitOutput) {
                        val slotKey = if (isOptionsStyle(decl.name())) "options" else "enums"
                        val sb = getOrCreateSlot(slotKey)
                        KotlinEnumBuilder(sb, this).visitEnum(decl)
                    } else {
                        enumBuilder.visitEnum(decl)
                    }
                }
            }
```

Add `isOptionsStyle` helper copied from `KotlinEnumBuilder`:
```kotlin
    private fun isOptionsStyle(name: String): Boolean =
        name.endsWith("Options") || name.endsWith("Flags") || name.endsWith("Mask")
```

- [ ] **Step 6: Update `visitScoped` struct/union dispatch for split mode**

```kotlin
            Declaration.Scoped.Kind.STRUCT -> {
                if (splitOutput) {
                    KotlinStructBuilder(getOrCreateSlot("types"), this).visitStruct(decl)
                } else {
                    structBuilder.visitStruct(decl)
                }
            }
            Declaration.Scoped.Kind.UNION -> {
                if (splitOutput) {
                    KotlinStructBuilder(getOrCreateSlot("types"), this).visitUnion(decl)
                } else {
                    structBuilder.visitUnion(decl)
                }
            }
```

- [ ] **Step 7: Rewrite `getFiles()` to map slots to KotlinSourceFile**

```kotlin
    fun getFiles(): List<KotlinSourceFile> {
        if (splitOutput) {
            return slots.map { (key, sb) ->
                val (subdir, name) = slotToFile(key)
                KotlinSourceFile(targetPackage, name, sb.toString(), subdir)
            }
        }
        return files
    }

    private fun slotToFile(key: String): Pair<String, String> = when {
        key == "_main" -> "" to className
        key == "types" -> "types" to "${headerBaseName}Types"
        key == "enums" -> "enums" to "${headerBaseName}Enums"
        key == "options" -> "options" to "${headerBaseName}Options"
        key.startsWith("class.") -> "classes" to key.removePrefix("class.")
        key.startsWith("protocol.") -> "protocols" to key.removePrefix("protocol.")
        else -> "" to key.replace('.', '_')
    }
```

- [ ] **Step 8: Add `generatedClassNames()` helper**

Replaces the inline computation in `visitScoped(TOPLEVEL)`:
```kotlin
    private fun generatedClassNames(): Set<String> {
        // This is called after the pre-scan in visitScoped(TOPLEVEL) fills the set
        return _generatedClassNames
    }

    private var _generatedClassNames: Set<String> = emptySet()
```

In the TOPLEVEL pre-scan section (around line 143-148), store the result:
```kotlin
                    val generatedObjCClassNames = decl.members()
                        .filterIsInstance<Declaration.ObjCClass>()
                        .filter { !Skip.isPresent(it) }
                        .map { it.name() }
                        .toSet()
                    _generatedClassNames = generatedObjCClassNames
                    objcClassBuilder = KotlinObjCClassBuilder(builder, this, generatedObjCClassNames)
```

Wait, the TOPLEVEL pre-scan section uses `builder` which no longer exists. Need to update that too. Let me re-read the code...

Lines 112-154 show the TOPLEVEL case. The `builder` reference is on line 148:
```kotlin
                    objcClassBuilder = KotlinObjCClassBuilder(builder, this, generatedObjCClassNames)
```

Change `builder` to `mainSlot`.

- [ ] **Step 9: Commit**

```bash
git -C third_party/kextract add -A && git -C third_party/kextract commit -m "feat: split output into per-class and per-category files"
```

---

### Task 4: Update `ObjCGeneratorTest` for multi-file assertions

**File:**
- Modify: `third_party/kextract/src/test/kotlin/org/graphiks/kextract/ObjCGeneratorTest.kt`

The test helpers `generateAll()` and `generate()` don't need changes — they already handle multiple `KotlinSourceFile` objects. The issue is that existing tests use `generate()` which concatenates all file contents. In split mode, assertions that check for things in different files would need updating.

But since `--split-output` defaults to `false`, existing tests pass unchanged. We only need to add new tests for the split feature.

- [ ] **Step 1: Add a helper for split-mode generation**

```kotlin
    /** Generate with --split-output enabled, returns map of filename → contents. */
    fun generateSplit(objcSource: String, pkg: String = "test"): Map<String, String> {
        val tmp = Files.createTempFile("kextract_split_test_", ".h")
        return try {
            tmp.toFile().writeText(objcSource)
            val headerName = tmp.fileName.toString()
            val parsed = KextractTool.parse(listOf(tmp.toString()), "-x", "objective-c")
            val mangled = NameMangler(headerName).scan(parsed)
            KotlinGenerator().generate(mangled, headerName, pkg, splitOutput = true)
                .associate { it.className to it.contents }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
```

- [ ] **Step 2: Add split-output integration tests**

```kotlin
    "Split-output mode produces per-class files" - {

        "Animal class appears in its own file, not in enums/types file" {
            val files = generateSplit("""
                @interface KxAnimal
                - (long)age;
                @end
                typedef long KxWeight;
            """.trimIndent())
            files.keys shouldContain "KxAnimal"
            files.keys shouldContain "KxBaseTypes"
            files["KxAnimal"] shouldContain "fun age()"
            files["KxBaseTypes"] shouldContain "typealias KxWeight"
        }

        "Multi-class generates separate files per class" {
            val files = generateSplit("""
                @interface KxA
                - (void)methodA;
                @end
                @interface KxB : KxA
                - (void)methodB;
                @end
            """.trimIndent())
            files.keys shouldContain "KxA"
            files.keys shouldContain "KxB"
            files["KxA"] shouldContain "fun methodA()"
            files["KxB"] shouldContain "fun methodB()"
        }

        "Protocol gets its own file in protocols/ subdir" {
            val files = generateSplit("""
                @protocol KxDrawable
                - (void)draw;
                @end
            """.trimIndent())
            files.keys shouldContain "KxDrawable"
            files["KxDrawable"] shouldContain "interface KxDrawable"
        }

        "Enum appears in Enums file, Options in Options file" {
            val files = generateSplit("""
                typedef enum : long { Red = 1, Green = 2 } KxColor;
                typedef enum : long { Readable = 1, Writable = 2 } KxFileOptions;
            """.trimIndent())
            files.keys shouldContain "KxBaseEnums"
            files.keys shouldContain "KxBaseOptions"
            files["KxBaseEnums"] shouldContain "enum class KxColor"
            files["KxBaseOptions"] shouldContain "value class KxFileOptions"
        }
    }
```

- [ ] **Step 3: Run the tests to verify they pass**

Run: (on macOS with JDK 25+)
```bash
./gradlew -Pjdk_home=$JAVA_HOME -Pllvm_home=$(brew --prefix llvm) test --tests "org.graphiks.kextract.ObjCGeneratorTest"
```

- [ ] **Step 4: Commit**

```bash
git -C third_party/kextract add -A && git -C third_party/kextract commit -m "test: add split-output mode tests"
```

---

### Task 5: Update regen script and apply fixups per-file

**File:**
- Modify: `scripts/regen-appkit-bindings.sh`

The fixups currently run on the monolithic file. With split output, each fixup must target the correct file.

- [ ] **Step 1: Add `--split-output` to kextract invocation**

```bash
"$KEXTRACT" \
    --objc \
    --split-output \
    -A "-F$SDK/System/Library/Frameworks" \
    ...
```

- [ ] **Step 2: Update output directory and fixup paths**

Change `AKH` and add per-file fixups:

```bash
OUT_BASE="$OUT/org/graphiks/kadre/appkit/bindings"

echo "→ Applying manual fixups per file"

# Fixup functions: apply regex to a file
fixup() {
    local file="$1"
    local pattern="$2"
    perl -i -pe "$pattern" "$file"
}

# 1. Escape Kotlin reserved keyword `object` in parameter positions — all files
for f in "$OUT_BASE"/classes/*.kt "$OUT_BASE"/protocols/*.kt "$OUT_BASE"/enums/*.kt "$OUT_BASE"/options/*.kt; do
    [ -f "$f" ] && fixup "$f" 's/\bobject(?=: MemorySegment|, |\))/`object`/g'
done

# 2. Add explicit : Unit to single-expression methods that throw — all files
for f in "$OUT_BASE"/classes/*.kt "$OUT_BASE"/protocols/*.kt; do
    [ -f "$f" ] && fixup "$f" 's/^(    fun \w+\([^)]*\)) =$/\1: Unit =/'
done

# 3. Strip evil shadowing typealiases in types file only
fixup "$OUT_BASE/types/AppKitTypes.kt" '^typealias (Boolean|Byte) = Any\s*$' && \
    perl -i -ne 'print unless /^typealias (Boolean|Byte) = Any\s*$/' "$OUT_BASE/types/AppKitTypes.kt" || true

# 4. Strip typealias NSUInteger = Any in types file
perl -i -ne 'print unless /^typealias NSUInteger = Any\s*$/' "$OUT_BASE/types/AppKitTypes.kt"
```

- [ ] **Step 3: Update output path message**

```bash
echo "✓ Done. Regenerated split bindings at $OUT_BASE/"
echo "  Files:"
find "$OUT_BASE" -name "*.kt" -type f | sort
```

- [ ] **Step 4: Commit**

```bash
git add scripts/regen-appkit-bindings.sh && git commit -m "feat: update regen script for --split-output"
```

---

### Task 6: Build kextract and run full regression

- [ ] **Step 1: Build kextract**

```bash
cd third_party/kextract
./gradlew -Pjdk_home=$JAVA_HOME -Pllvm_home=$(brew --prefix llvm) clean kmainClasses
```

- [ ] **Step 2: Run all tests**

```bash
./gradlew -Pjdk_home=$JAVA_HOME -Pllvm_home=$(brew --prefix llvm) test
```

- [ ] **Step 3: Run specific ObjC generator tests**

```bash
./gradlew -Pjdk_home=$JAVA_HOME -Pllvm_home=$(brew --prefix llvm) test --tests "org.graphiks.kextract.ObjCGeneratorTest"
```

- [ ] **Step 4: Verify the distribution builds**

```bash
./gradlew -Pjdk_home=$JAVA_HOME -Pllvm_home=$(brew --prefix llvm) clean kmainClasses
ls -la build/kextract/bin/kextract
```

---

### Task 7: Upstream PR

- [ ] **Step 1: Create a clean branch in kextract submodule**

```bash
cd third_party/kextract
git checkout -b split-output
```

- [ ] **Step 2: Push to fork and create PR**

```bash
# Add your fork remote if needed:
# git remote add fork https://github.com/<your-username>/kextract.git
git push fork split-output
# Then create PR via gh CLI or web
```

- [ ] **Step 3: Update Kadre's .gitmodules if needed (point to fork temporarily)**

```ini
[submodule "third_party/kextract"]
    path = third_party/kextract
    url = https://github.com/<your-username>/kextract.git
    branch = split-output
```

- [ ] **Step 4: Update git submodule to track the PR branch**

```bash
cd third_party/kextract
git fetch fork split-output
git checkout split-output
cd ../..
git add third_party/kextract
git commit -m "chore: point kextract submodule to split-output branch for testing"
```
