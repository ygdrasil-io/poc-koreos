#!/usr/bin/env bash
#
# Regenerates the AppKit FFM bindings using a local kextract install.
#
# Usage:
#   scripts/regen-appkit-bindings.sh /path/to/kextract/bin/kextract
#
# After running this script, apply the manual fixups documented in
# kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/bindings/AppKit_h.kt
# (search for "Manual fixups" at the top of the file) or use the perl/sed lines
# at the bottom of this script.
#
# Requires:
#   - macOS with Xcode installed (xcrun must work)
#   - kextract v0.0.2 or newer
#
set -euo pipefail

KEXTRACT="${1:?Usage: $0 /path/to/kextract/bin/kextract}"
[ -x "$KEXTRACT" ] || { echo "kextract binary not executable: $KEXTRACT" >&2; exit 1; }

SDK=$(xcrun --sdk macosx --show-sdk-path)
APPKIT_H="$SDK/System/Library/Frameworks/AppKit.framework/Headers/AppKit.h"
OUT=$(cd "$(dirname "$0")/.." && pwd)/kadre-appkit/src/jvmMain/kotlin
OUT_PKG="$OUT/org/graphiks/kadre/appkit/bindings"

echo "→ Regenerating AppKit bindings via kextract"
echo "  SDK     = $SDK"
echo "  Output  = $OUT"

"$KEXTRACT" \
    --objc \
    --split-output \
    -A "-F$SDK/System/Library/Frameworks" \
    -A "-isysroot" -A "$SDK" \
    -o "$OUT" \
    -t org.graphiks.kadre.appkit.bindings \
    --include-objc-class NSApplication \
    --include-objc-class NSWindow \
    --include-objc-class NSView \
    --include-objc-class NSEvent \
    --include-objc-class CAMetalLayer \
    --include-objc-protocol NSApplicationDelegate \
    --include-objc-protocol NSWindowDelegate \
    "$APPKIT_H"

echo "→ Applying manual fixups"

# Helper: apply pattern to all .kt files matching a glob
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

# 3. Strip evil shadowing typealiases (types file only)
if [ -f "$OUT_PKG/types/AppKitTypes.kt" ]; then
    perl -i -ne 'print unless /^typealias (Boolean|Byte) = Any\s*$/' "$OUT_PKG/types/AppKitTypes.kt"
fi

# 4. Strip typealias NSUInteger = Any (types file only)
if [ -f "$OUT_PKG/types/AppKitTypes.kt" ]; then
    perl -i -ne 'print unless /^typealias NSUInteger = Any\s*$/' "$OUT_PKG/types/AppKitTypes.kt"
fi

echo "✓ Done. Regenerated split bindings at $OUT_PKG/"
echo "  Files:"
find "$OUT_PKG" -name "*.kt" -type f | sort
