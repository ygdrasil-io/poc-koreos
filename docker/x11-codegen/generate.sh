#!/usr/bin/env bash
# In-container X11 binding generation for Kadre.
set -euo pipefail

JDK_HOME="${JDK_HOME:-/opt/java/openjdk}"
LLVM_HOME="${LLVM_HOME:-/usr/lib/llvm-18}"
REPO=/work

KEXTRACT_DIR="$REPO/third_party/kextract"
X11_INC=/usr/include
OUT_GEN="$REPO/build/x11-codegen"

echo "[x11-gen] arch=$(uname -m)  llvm=$LLVM_HOME  jdk=$JDK_HOME"
mkdir -p "$OUT_GEN"

# ── 1. Build kextract ──────────────────────────────────────────────────────────
echo "[x11-gen] building kextract…"
( cd "$KEXTRACT_DIR" && ./gradlew --no-daemon -Pjdk_home="$JDK_HOME" -Pllvm_home="$LLVM_HOME" createKextractImage )
KEXTRACT="$KEXTRACT_DIR/build/kextract/bin/kextract"
"$KEXTRACT" --help >/dev/null 2>&1 && echo "[x11-gen] kextract launcher OK"

# ── 2. Generate X11 FFM bindings ──────────────────────────────────────────────
# XIM.h is not available on Ubuntu 24.04 Noble (X Input Method removed).
# XEvent is too complex for kextract (union with 30+ member structs);
# Kadre uses hand-written LP64 offset constants for event access instead.

"$KEXTRACT" \
    -t org.graphiks.kadre.ffi.x11 \
    -o "$OUT_GEN" \
    -l :libX11.so.6 \
    -l :libXext.so.6 \
    -l :libXcomposite.so.1 \
    -I "$X11_INC" \
    -D _GNU_SOURCE \
    -D _DEFAULT_SOURCE \
    \
    --include-function XOpenDisplay \
    --include-function XCloseDisplay \
    --include-function XCreateSimpleWindow \
    --include-function XSelectInput \
    --include-function XDestroyWindow \
    --include-function XFlush \
    --include-function XPending \
    --include-function XNextEvent \
    --include-function XStoreName \
    --include-function XInternAtom \
    --include-function XSetWMProtocols \
    --include-function XMapWindow \
    --include-function XRaiseWindow \
    --include-function XSendEvent \
    --include-function XResizeWindow \
    --include-function XMoveWindow \
    --include-function XIconifyWindow \
    --include-function XChangeProperty \
    --include-function XDeleteProperty \
    --include-function XGetWindowProperty \
    --include-function XFree \
    --include-function XGetGeometry \
    --include-function XTranslateCoordinates \
    --include-function XUnmapWindow \
    --include-function XResourceManagerString \
    --include-function XCreateFontCursor \
    --include-function XDefineCursor \
    --include-function XUndefineCursor \
    --include-function XFreeCursor \
    --include-function XCreateBitmapFromData \
    --include-function XCreatePixmapCursor \
    --include-function XFreePixmap \
    --include-function XGrabPointer \
    --include-function XUngrabPointer \
    --include-function XQueryPointer \
    --include-function XWarpPointer \
    --include-function XGetWMHints \
    --include-function XAllocWMHints \
    --include-function XSetWMHints \
    --include-function XChangeWindowAttributes \
    --include-function XDestroyIC \
    --include-function XFilterEvent \
    --include-function XConvertSelection \
    --include-function XDefaultScreen \
    --include-function XDefaultRootWindow \
    --include-function XDefaultVisual \
    --include-function XDefaultDepth \
    --include-function XGetImage \
    --include-function XDestroyImage \
    --include-function XQueryTree \
    --include-function XGetWindowAttributes \
    --include-function XSync \
    --include-function XRootWindow \
    --include-function XShapeCombineRectangles \
    --include-function XShmQueryExtension \
    --include-function XShmCreateImage \
    --include-function XShmAttach \
    --include-function XShmDetach \
    --include-function XShmGetImage \
    --include-function XCompositeNameWindowPixmap \
    --include-function XkbSetDetectableAutoRepeat \
    --include-function XKeysymToKeycode \
    --include-function XLookupKeysym \
    --include-function XLookupString \
    --include-function XGetKeyboardMapping \
    --include-function XFreeStringList \
    --include-function XQueryKeymap \
    --include-function XSetICValues \
    --include-function XCreateIC \
    --include-function XSetICFocus \
    --include-function XUnsetICFocus \
    --include-function XOpenIM \
    --include-function XCloseIM \
    \
    --variadic-args XCreateIC:11 \
    --variadic-args XSetICValues:3 \
    \
    --include-typedef Display \
    --include-typedef XID \
    --include-typedef Atom \
    --include-typedef Window \
    --include-typedef Cursor \
    --include-typedef Pixmap \
    --include-typedef Drawable \
    --include-typedef Time \
    --include-typedef Bool \
    --include-typedef Status \
    --include-typedef XRectangle \
    --include-typedef XPoint \
    \
    --include-struct XGC \
    --include-struct XWindowAttributes \
    --include-struct XSetWindowAttributes \
    --include-struct XColor \
    --include-struct XWMHints \
    \
    "$X11_INC/X11/Xlib.h" \
    "$X11_INC/X11/Xutil.h" \
    "$X11_INC/X11/Xresource.h" \
    "$X11_INC/X11/XKBlib.h" \
    "$X11_INC/X11/cursorfont.h" \
    "$X11_INC/X11/extensions/XShm.h" \
    "$X11_INC/X11/extensions/shape.h" \
    "$X11_INC/X11/extensions/Xcomposite.h" \
    "$X11_INC/X11/Xatom.h" 2>&1

echo "[x11-gen] ======== Generated files ========"
find "$OUT_GEN" -name '*.kt' -type f 2>/dev/null | sort
echo "[x11-gen] =================================="

if [ -d "$OUT_GEN/org/graphiks/kadre/ffi/x11" ]; then
  echo "[x11-gen] Output file sizes:"
  du -sh "$OUT_GEN/org/graphiks/kadre/ffi/x11/"* 2>/dev/null
fi
echo "[x11-gen] generation complete."
