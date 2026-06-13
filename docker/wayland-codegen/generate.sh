#!/usr/bin/env bash
# In-container generation steps. Run by scripts/gen-wayland-xdg.sh inside the
# kadre-wayland-codegen image (LLVM/libclang + wayland-scanner + gcc + JDK 25).
set -euo pipefail

JDK_HOME="${JDK_HOME:-/opt/java/openjdk}"
LLVM_HOME="${LLVM_HOME:-/usr/lib/llvm-18}"
REPO=/work
KEXTRACT_DIR="$REPO/third_party/kextract"
PROTO=/usr/share/wayland-protocols/stable/xdg-shell/xdg-shell.xml

ARCH="$(uname -m)"   # aarch64 | x86_64
GEN="$REPO/kadre-wayland/build/wayland-xdg"
OUT_KT="$REPO/ffi/wayland/src/jvmMain/kotlin"
echo "[gen] arch=$ARCH  llvm=$LLVM_HOME  jdk=$JDK_HOME"
mkdir -p "$GEN"

# ── 1. Build kextract (self-contained image under build/kextract/) ──────────────
echo "[gen] building kextract…"
( cd "$KEXTRACT_DIR" && ./gradlew --no-daemon -Pjdk_home="$JDK_HOME" -Pllvm_home="$LLVM_HOME" createKextractImage )
KEXTRACT="$KEXTRACT_DIR/build/kextract/bin/kextract"
"$KEXTRACT" --help >/dev/null 2>&1 && echo "[gen] kextract launcher OK"

# ── 2. wayland-scanner: protocol headers for kextract ────────────────────────────
PROTO_DECO=/usr/share/wayland-protocols/unstable/xdg-decoration/xdg-decoration-unstable-v1.xml
echo "[gen] wayland-scanner $PROTO + $PROTO_DECO"
wayland-scanner client-header "$PROTO"      "$GEN/xdg-shell-client-protocol.h"
wayland-scanner client-header "$PROTO_DECO" "$GEN/xdg-decoration-client-protocol.h"

# ── 3. kextract: header → Kotlin FFM bindings ───────────────────────────────────
# -ffreestanding is required: kextract's bundled libclang, in hosted mode, can't follow
# clang's stdint.h `#include_next <stdint.h>` chain into glibc in this image, so every
# sized int type (int32_t/uint32_t/intmax_t) comes out "unknown" and the parse aborts.
# Freestanding makes clang define those types itself from compiler builtins
# (__INT32_TYPE__ &c.) — verified to drop the error count to zero. The wayland headers
# only need the sized-int / size_t / va_list types, all available freestanding.
# No -l: emit loaderLookup()-based accessors. The xdg-shell interface structs
# are now generated from XML by ProtocolInterfaceGenerator (step 4), so they no
# longer require the libkadre-xdg.so at runtime.
"$KEXTRACT" \
  -t org.graphiks.kadre.ffi.wayland.generated \
  -o "$OUT_KT" \
  --include-struct wl_interface \
  --include-struct wl_message \
  -A -ffreestanding \
  -I "$GEN" \
  "$GEN/xdg-shell-client-protocol.h" \
  "$GEN/xdg-decoration-client-protocol.h"

# ── 4. Generate wl_interface MemorySegments from protocol XML ──────────────
echo "[gen] generating wl_interface MemorySegments from XML…"
java -cp /build ProtocolInterfaceGenerator \
    "$PROTO" "$PROTO_DECO" \
    "$OUT_KT/org/graphiks/kadre/ffi/wayland/generated/XdgShellProtocolInterfaces.kt"

echo "[gen] done. Generated:"
find "$OUT_KT/org/graphiks/kadre/ffi/wayland/generated" -name '*.kt' 2>/dev/null
