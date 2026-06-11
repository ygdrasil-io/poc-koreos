#!/usr/bin/env bash
#
# Regenerates ObjC FFM bindings for all frameworks useful for desktop apps/games.
# Uses --split-output and --include-framework for per-class files with framework
# source-location filtering.
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
TYPES_FILE="$OUT_PKG/types/KffiTypes.kt"
if [ -f "$TYPES_FILE" ]; then
    perl -i -ne 'print unless /^typealias (Boolean|Byte) = Any\s*$/' "$TYPES_FILE"
fi

# 4. Strip typealias NSUInteger = Any
if [ -f "$TYPES_FILE" ]; then
    perl -i -ne 'print unless /^typealias NSUInteger = Any\s*$/' "$TYPES_FILE"
fi

echo "✓ Done. Regenerated bindings at $OUT_PKG/"
echo "  Files:"
find "$OUT_PKG" -name "*.kt" -type f | sort | head -50
echo "  ... and more"
