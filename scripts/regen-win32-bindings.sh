#!/usr/bin/env bash
#
# Regenerates Win32 FFM bindings for DLLs needed by Kadre on Windows.
# Uses --win32 and --dll-map with <windows.h> from the Windows SDK.
# Creates one temp C header per DLL so kextract generates one file per DLL.
#
# Usage:
#   scripts/regen-win32-bindings.sh /path/to/kextract/bin/kextract
#
set -euo pipefail

KEXTRACT="${1:?Usage: $0 /path/to/kextract/bin/kextract}"
DIST_DIR="$(dirname "$KEXTRACT")/.."
JAVA="$DIST_DIR/runtime/bin/java"
LIBS_DIR="$DIST_DIR/lib"

# Build classpath
CLASSPATH=""
for jar in "$LIBS_DIR"/*.jar; do
    [ -n "$CLASSPATH" ] && CLASSPATH="$CLASSPATH;$jar" || CLASSPATH="$jar"
done
NATIVE_PATH="$LIBS_DIR"

PACKAGE="org.graphiks.kadre.ffi.win32.generated"
OUT_DIR="$PWD/ffi/win32/src/jvmMain/kotlin"
DLLS=(user32 kernel32 gdi32 dwmapi)

# Detect Windows SDK include paths
SDK_INCLUDE=""
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*)
    NATIVE_PATH="$NATIVE_PATH;$SYSTEMROOT/System32"
    # Try common Windows SDK include paths
    for base in \
        "C:/Program Files (x86)/Windows Kits/10/Include" \
        "C:/Program Files/Windows Kits/10/Include"; do
        [ -d "$base" ] || continue
        for ver in "$base"/*/; do
            [ -d "${ver}um" ] && SDK_INCLUDE="$SDK_INCLUDE -isystem ${ver}um"
            [ -d "${ver}shared" ] && SDK_INCLUDE="$SDK_INCLUDE -isystem ${ver}shared"
            [ -d "${ver}ucrt" ] && SDK_INCLUDE="$SDK_INCLUDE -isystem ${ver}ucrt"
        done
        break  # use first found
    done
;; esac
# Also try clang's built-in SDK detection by adding nothing
# (libclang on Windows can auto-detect SDK if INCLUDE env var is set)

echo "→ Regenerating Win32 bindings for ${#DLLS[@]} DLLs"
echo "  SDK include: ${SDK_INCLUDE:-(none)}"
echo "  Output: $OUT_DIR"
mkdir -p "$OUT_DIR"

for dll in "${DLLS[@]}"; do
    yaml="$PWD/ffi/win32/${dll}.yaml"
    # Temp header per DLL → kextract generates unique filenames (e.g. user32_h.kt)
    TMP_HDR="/tmp/${dll}.h"
    echo '#define WIN32_LEAN_AND_MEAN' > "$TMP_HDR"
    echo '#include <windows.h>' >> "$TMP_HDR"

    # Extract function names
    functions=()
    while IFS= read -r line; do
        functions+=("$line")
    done < <(sed -n '/^    functions:/,/^    structs:/{
        /^    functions:/d; /^    structs:/d; /^      - /{s/^      - //; p}
    }' "$yaml") || true
    echo "  $dll: ${#functions[@]} functions"

    kextractArgs=(
        --win32 --dll-map "$yaml" --verbose
        -o "$OUT_DIR" -t "$PACKAGE"
    )
    for fn in "${functions[@]}"; do
        kextractArgs+=(--include-function "$fn")
    done
    # Add SDK include paths so clang finds <windows.h>
    for arg in $SDK_INCLUDE; do
        kextractArgs+=(-A "$arg")
    done
    kextractArgs+=("$TMP_HDR")

    echo "    Running kextract for $dll..."
    "$JAVA" --enable-native-access=ALL-UNNAMED \
        "-Djava.library.path=$NATIVE_PATH" \
        -cp "$CLASSPATH" \
        org.graphiks.kextract.pipeline.KextractTool \
        "${kextractArgs[@]}" 2>&1 || { rc=$?; echo "ERROR: kextract failed for $dll (exit $rc)" >&2; exit $rc; }

    rm -f "$TMP_HDR"
done

echo "✓ Done. Regenerated bindings at $OUT_DIR/$PACKAGE/"
