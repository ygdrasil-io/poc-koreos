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
SDK_ARGS=()
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*)
    NATIVE_PATH="$NATIVE_PATH;$SYSTEMROOT/System32"
    for base in \
        "C:/Program Files (x86)/Windows Kits/10/Include" \
        "C:/Program Files/Windows Kits/10/Include"; do
        [ -d "$base" ] || continue
        for ver in "$base"/*/; do
            [ -d "${ver}um" ]     && SDK_ARGS+=(-isystem "${ver}um")
            [ -d "${ver}shared" ] && SDK_ARGS+=(-isystem "${ver}shared")
            [ -d "${ver}ucrt" ]   && SDK_ARGS+=(-isystem "${ver}ucrt")
        done
        break
    done
;; esac

echo "→ Regenerating Win32 bindings for ${#DLLS[@]} DLLs"
echo "  SDK args (${#SDK_ARGS[@]}): ${SDK_ARGS[*]:-(none)}"
echo "  Output: $OUT_DIR"
mkdir -p "$OUT_DIR"

# Combine all DLL YAMLs into one --include-function list
ALL_FUNCTIONS=()
for dll in "${DLLS[@]}"; do
    yaml="$PWD/ffi/win32/${dll}.yaml"
    while IFS= read -r line; do
        ALL_FUNCTIONS+=("$line")
    done < <(sed -n '/^    functions:/,/^    structs:/{
        /^    functions:/d; /^    structs:/d; /^      - /{s/^      - //; p}
    }' "$yaml") || true
done
echo "  Total functions: ${#ALL_FUNCTIONS[@]}"

# Combine all DLL YAMLs into one --dll-map (merge all DLL mappings)
COMBINED_YAML="/tmp/combined_win32.yaml"
python3 -c "
import yaml, sys
data = {'dllMap': {}}
for dll in '${DLLS[@]}'.split():
    with open('$PWD/ffi/win32/' + dll + '.yaml') as f:
        d = yaml.safe_load(f)
        data['dllMap'].update(d.get('dllMap', {}))
with open('$COMBINED_YAML', 'w') as f:
    yaml.dump(data, f, default_flow_style=False)
"
echo "  Combined YAML: $(python3 -c "import yaml; d=yaml.safe_load(open('$COMBINED_YAML')); print(sum(len(v.get('functions',[])) for v in d['dllMap'].values()))") functions"

# Single temp header (name determines output filename)
TMP_HDR="/tmp/win32_all.h"
echo '#define WIN32_LEAN_AND_MEAN' > "$TMP_HDR"
echo '#define NOMINMAX' >> "$TMP_HDR"
echo '#include <windows.h>' >> "$TMP_HDR"

kextractArgs=(
    --win32 --dll-map "$COMBINED_YAML" --verbose
    -o "$OUT_DIR" -t "$PACKAGE"
)
for fn in "${ALL_FUNCTIONS[@]}"; do
    kextractArgs+=(--include-function "$fn")
done
for arg in "${SDK_ARGS[@]}"; do
    kextractArgs+=(-A "$arg")
done
kextractArgs+=("$TMP_HDR")

echo "  Running kextract for all DLLs..."
"$JAVA" --enable-native-access=ALL-UNNAMED \
    "-Djava.library.path=$NATIVE_PATH" \
    -cp "$CLASSPATH" \
    org.graphiks.kextract.pipeline.KextractTool \
    "${kextractArgs[@]}" 2>&1 || { rc=$?; echo "ERROR: kextract failed (exit $rc)" >&2; exit $rc; }

rm -f "$TMP_HDR" "$COMBINED_YAML"

echo "✓ Done. Regenerated bindings at $OUT_DIR/$PACKAGE/"
