#!/usr/bin/env bash
#
# Regenerates Win32 FFM bindings for DLLs needed by Kadre on Windows.
# Uses --win32 and --dll-map to generate per-DLL bindings from win32_api.h.
#
# Usage:
#   scripts/regen-win32-bindings.sh /path/to/kextract/bin/kextract
#
# Requires:
#   - kextract with --win32 and --dll-map support
#
set -euo pipefail

KEXTRACT="${1:?Usage: $0 /path/to/kextract/bin/kextract}"
DIST_DIR="$(dirname "$KEXTRACT")/.."

# On Windows the distribution ships a .bat launcher - use it to find the JRE
JAVA="$DIST_DIR/runtime/bin/java"
LIBS_DIR="$DIST_DIR/lib"

# Build classpath manually (JARs may have version suffixes)
CLASSPATH=""
for jar in "$LIBS_DIR"/*.jar; do
    if [ -n "$CLASSPATH" ]; then
        CLASSPATH="$CLASSPATH;$jar"
    else
        CLASSPATH="$jar"
    fi
done
# Include System32 on Windows so System.loadLibrary("kernel32") works
NATIVE_PATH="$LIBS_DIR"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) NATIVE_PATH="$NATIVE_PATH;$SYSTEMROOT\\System32" ;; esac

# Quick test: verify kextract starts
echo "  Java: $JAVA"
echo "  NATIVE_PATH: $NATIVE_PATH"
echo "  Testing: $JAVA -cp ... KextractTool --help"
"$JAVA" --enable-native-access=ALL-UNNAMED \
    "-Djava.library.path=$NATIVE_PATH" \
    -cp "$CLASSPATH" \
    org.graphiks.kextract.pipeline.KextractTool \
    --help 2>&1 | head -20 || true
echo ""



SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DLL_MAP_DIR="$SCRIPT_DIR/ffi/win32"
OUT_DIR="$DLL_MAP_DIR/src/jvmMain/kotlin"
PACKAGE="org.graphiks.kadre.ffi.win32.generated"
HEADER="$SCRIPT_DIR/ffi/win32/win32_api.h"

DLLS=(user32 kernel32 gdi32 dwmapi)

echo "→ Regenerating Win32 bindings for ${#DLLS[@]} DLLs"
echo "  Output base: $OUT_DIR"
echo "  Package:     $PACKAGE"

mkdir -p "$OUT_DIR"

for dll in "${DLLS[@]}"; do
    yaml="$DLL_MAP_DIR/${dll}.yaml"
    echo ""
    echo "  Processing $dll..."

    # Extract function names from the YAML mapping
    IFS=$'\n' read -r -d '' -a functions < <(
        sed -n '/^    functions:/,/^    structs:/{
            /^    functions:/d
            /^    structs:/d
            /^      - /{
                s/^      - //
                p
            }
        }' "$yaml"
    )
    echo "    Functions: ${#functions[@]}"

    kextractArgs=(
        --win32
        --dll-map "$yaml"
        --verbose
        -o "$OUT_DIR"
        -t "$PACKAGE"
    )

    for fn in "${functions[@]}"; do
        kextractArgs+=(--include-function "$fn")
    done
    kextractArgs+=("$HEADER")

    echo "    Running: java -cp ... KextractTool"
    rc=0
    "$JAVA" -Xrs --enable-native-access=ALL-UNNAMED \
        "-Djava.library.path=$NATIVE_PATH" \
        -Dkextract.debug=true \
        -Dlibclang.debug=true \
        -cp "$CLASSPATH" \
        org.graphiks.kextract.pipeline.KextractTool \
        "${kextractArgs[@]}" > /tmp/kextract_stdout.txt 2>/tmp/kextract_stderr.txt || rc=$?
    echo "    stdout:"
    cat /tmp/kextract_stdout.txt 2>&1 || true
    echo "    stderr:"
    cat /tmp/kextract_stderr.txt 2>&1 || true
    if [ $rc -ne 0 ]; then
        echo "  ERROR: kextract failed for $dll (exit code $rc)" >&2
        exit $rc
    fi
done

echo ""
echo "✓ Done. Regenerated bindings at $OUT_DIR/$PACKAGE/"