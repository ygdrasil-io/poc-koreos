# Wayland Screen Capture via xdg-desktop-portal

## Overview

This document explains the **xdg-desktop-portal** implementation for screen capture on Wayland, which enables capture support on GNOME (Mutter), KDE (KWin), and other desktop environments that don't support the `wlr-screencopy-unstable-v1` protocol.

## Why xdg-desktop-portal?

### The Problem

The existing Wayland capture implementation in Kadre uses **`zwlr_screencopy_manager_v1`** (from the wlroots project). This protocol works well on compositors like:
- ✅ Sway
- ✅ Hyprland  
- ✅ River
- ✅ Cage

However, it **does NOT work** on:
- ❌ GNOME (Mutter compositor)
- ❌ KDE Plasma (KWin compositor)
- ❌ Weston (reference compositor)
- ❌ Most other non-wlroots compositors

This means screen capture was **completely broken** on the most popular Linux desktop environments!

### The Solution

**xdg-desktop-portal** is a Freedesktop standard that provides a unified way for sandboxed applications to access system services, including screen capture. It's supported by:
- ✅ GNOME (via xdg-desktop-portal-gnome)
- ✅ KDE (via xdg-desktop-portal-kde)
- ✅ wlroots-based compositors (Sway, Hyprland, etc.)
- ✅ Weston

By implementing xdg-desktop-portal support, Kadre now works on **all major Wayland compositors**!

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Kadre Application Layer                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  WaylandScreenCapturer.createSession(source, config)                         │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ Decision: Which backend to use?                                       │ │
│  │                                                                         │ │
│  │  if (XdpPortal.isAvailable()) {                                        │ │
│  │      return XdpPortalCaptureSession(...)  // ← NEW: Portal-based     │ │
│  │  } else {                                                               │ │
│  │      return WaylandCaptureSession(...)    // ← Existing: wlr-screencopy│ │
│  │  }                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        xdg-desktop-portal Layer                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  XdpPortal.kt (D-Bus communication)                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ 1. CreateSession() → session_handle                                    │ │
│  │ 2. SelectSources(session, types=["monitor"] or ["window"])              │ │
│  │ 3. Start(session) → stream_node_id + metadata                           │ │
│  │ 4. OpenPipeWireRemote(session, node_id) → file_descriptor               │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  XdpPipeWire.kt (Frame reception)                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ 1. mmap(file_descriptor) → MemorySegment                               │ │
│  │ 2. Read pixel data from shared memory                                   │ │
│  │ 3. Convert to CaptureFrame format                                       │ │
│  │ 4. Emit to SharedFlow<CaptureFrame>                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      System Layer (D-Bus + PipeWire)                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  D-Bus: org.freedesktop.portal.ScreenCast interface                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ Methods:                                                                  │ │
│  │   CreateSession(app_id: string) → session_handle: object_path           │ │
│  │   SelectSources(session: object_path, types: string[], options: dict)   │ │
│  │   Start(session: object_path, parent_window: string, options: dict)     │ │
│  │   OpenPipeWireRemote(session: object_path, node_id: uint32) → fd       │ │
│  │   Close(session: object_path)                                          │ │
│  │                                                                         │ │
│  │ Signals:                                                                 │ │
│  │   SessionCreated(session_handle)                                        │ │
│  │   SourceSelected(session_handle, source_type, source_handle)           │ │
│  │   Started(session_handle, stream_node_id)                              │ │
│  │   Closed(session_handle)                                               │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  PipeWire: Video streaming protocol                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ - Provides the actual frame data                                        │ │
│  │ - Supports DMA-BUF (zero-copy GPU) and SHM (shared memory)             │ │
│  │ - Used by the portal to stream frames to the client                    │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────┘
```

## Implementation Details

### 1. XdpPortal.kt - D-Bus Communication

This file handles all communication with the xdg-desktop-portal via D-Bus using the `dbus-send` command-line tool.

**Key Functions:**

```kotlin
// Check if portal is available on the system
fun isAvailable(): Boolean

// Check permission status
fun checkPermissionStatus(): CapturePermission

// Create a new screencast session
fun createSession(appId: String = "kadre"): String?

// Select sources (triggers user prompt)
fun selectSources(
    sessionHandle: String,
    types: List<String> = listOf("monitor", "window"),
    multiple: Boolean = false,
    cursorMode: String = "embedded"
): Boolean

// Start the session
fun startSession(
    sessionHandle: String,
    parentWindow: String? = null
): StartResult?

// Close the session
fun closeSession(sessionHandle: String): Boolean
```

**D-Bus Message Flow:**

```
1. CreateSession
   Request: dbus-send --dest=org.freedesktop.portal.Desktop \
            /org/freedesktop/portal/desktop \
            org.freedesktop.portal.ScreenCast.CreateSession string:"kadre"
   
   Response: object path "/org/freedesktop/portal/desktop/session/abc123"

2. SelectSources
   Request: dbus-send --dest=org.freedesktop.portal.Desktop \
            /org/freedesktop/portal/desktop/session/abc123 \
            org.freedesktop.portal.ScreenCast.SelectSources \
            string:"monitor" a{sv}:...
   
   Response: (empty, but triggers user dialog)

3. Start
   Request: dbus-send --dest=org.freedesktop.portal.Desktop \
            /org/freedesktop/portal/desktop/session/abc123 \
            org.freedesktop.portal.ScreenCast.Start string:"" a{sv}:...
   
   Response: array [
              dict entry(string "stream_node_id", variant uint32 42),
              dict entry(string "types", variant uint32 1),
              dict entry(string "cursor_mode", variant uint32 2)
            ]

4. OpenPipeWireRemote
   Request: dbus-send --dest=org.freedesktop.portal.Desktop \
            /org/freedesktop/portal/desktop/session/abc123 \
            org.freedesktop.portal.ScreenCast.OpenPipeWireRemote h:42
   
   Response: h:5 (file descriptor), u:1920, u:1080, u:7680, u:2
           (fd=5, width=1920, height=1080, stride=7680, format=ARGB8888)
```

### 2. XdpPipeWire.kt - Frame Reception

This file handles receiving frames from PipeWire. For the MVP, we use a simplified approach:

**Approach 1: OpenPipeWireRemote (Current Implementation)**
- Call `OpenPipeWireRemote` to get a file descriptor
- `mmap()` the file descriptor to access the frame buffer
- Read pixel data directly from shared memory
- Convert to `CaptureFrame` format

**Approach 2: Full PipeWire Integration (Future)**
- Connect to PipeWire context
- Create a stream for the given node ID
- Set up callbacks for buffer reception
- Handle DMA-BUF for zero-copy GPU frames

**Key Functions:**

```kotlin
// Open PipeWire remote and get file descriptor
fun openPipeWireRemote(sessionHandle: String, nodeId: Int): PipeWireRemoteResult?

// Read frame from mmap'd file descriptor
fun readFrameFromFd(fd: Int, width: Int, height: Int, stride: Int, format: String): Frame?

// Convert to CaptureFrame
fun toCaptureFrame(frame: Frame, targetFormat: PixelFormat): CaptureFrame

// Release frame resources
fun releaseFrame(frame: Frame)
```

### 3. XdpPortalCaptureSession.kt - Session Management

This file orchestrates the complete capture flow:

```kotlin
class XdpPortalCaptureSession(source: CaptureSource, config: CaptureConfig) : CaptureSession {
    init {
        scope.launch {
            startPortalSession()  // 1. Create session
            captureLoop()          // 2. Capture frames in loop
        }
    }
    
    private suspend fun startPortalSession() {
        sessionHandle = XdpPortal.createSession("kadre")
        XdpPortal.selectSources(sessionHandle, types = listOf("monitor"))
        val result = XdpPortal.startSession(sessionHandle)
        streamNodeId = result.streamNodeId
        pipeWireRemote = XdpPipeWire.openPipeWireRemote(sessionHandle, streamNodeId)
    }
    
    private suspend fun captureLoop() {
        while (isCapturing) {
            val pwFrame = readNextFrame()
            val captureFrame = XdpPipeWire.toCaptureFrame(pwFrame, config.pixelFormat)
            _frames.tryEmit(captureFrame)
            delay(1000 / config.frameRate)
        }
    }
}
```

### 4. WaylandScreenCapturer.kt - Backend Selection

Updated to try portal first, then fall back to wlr-screencopy:

```kotlin
override suspend fun createSession(source: CaptureSource, config: CaptureConfig): CaptureSession {
    return if (XdpPortal.isAvailable()) {
        XdpPortalCaptureSession(source, config)
    } else {
        WaylandCaptureSession(source, config)  // Existing wlr-screencopy
    }
}

override suspend fun requestPermission(): CapturePermission =
    if (XdpPortal.isAvailable()) XdpPortal.checkPermissionStatus() else CapturePermission.Pending
```

## System Requirements

### Required Packages

```bash
# Ubuntu/Debian
sudo apt install xdg-desktop-portal xdg-desktop-portal-gnome pipewire

# Fedora
sudo dnf install xdg-desktop-portal xdg-desktop-portal-gnome pipewire

# Arch Linux
sudo pacman -S xdg-desktop-portal xdg-desktop-portal-gnome pipewire

# openSUSE
sudo zypper install xdg-desktop-portal xdg-desktop-portal-gnome pipewire
```

### Verifying Installation

```bash
# Check if portal is running
ps aux | grep xdg-desktop-portal

# Check ScreenCast interface availability
dbus-send --print-reply --dest=org.freedesktop.portal.Desktop \
  /org/freedesktop/portal/desktop \
  org.freedesktop.DBus.Properties.Get \
  string:"org.freedesktop.portal.ScreenCast" string:"version"

# Check PipeWire
pw-cli list-objects | grep "screen cast"
```

## User Experience Flow

### On GNOME/KDE (with portal)

1. **Create Session**: Kadre requests a new screencast session
2. **Select Sources**: Portal shows a dialog asking user to select display/window
   - User sees: "Kadre wants to capture your screen. Select what to share."
   - Options: Entire screen, Specific window, Specific region
3. **Start Session**: Portal starts streaming frames
4. **Receive Frames**: Kadre receives frames via PipeWire
5. **Capture**: Frames are converted to `CaptureFrame` and emitted via `SharedFlow`

### On Sway/Hyprland (without portal)

1. **Fallback**: `XdpPortal.isAvailable()` returns false
2. **Use wlr-screencopy**: Falls back to existing implementation
3. **Direct Capture**: Frames captured directly via Wayland protocol
4. **No User Prompt**: No permission dialog (compositor allows it)

## Permission Model

### xdg-desktop-portal Permissions

The portal uses a **user-mediated permission model**:

1. **First Request**: When an app requests screen capture, the portal shows a dialog
2. **User Selection**: User must explicitly select what to share
3. **Persistent Permission**: Once granted, permission persists for the session
4. **Revocation**: User can revoke via system settings

### Permission States

```kotlin
sealed interface CapturePermission {
    object Granted : CapturePermission      // Permission granted, capture works
    object Pending : CapturePermission      // Waiting for user to grant permission
    data class Denied(val reason: String) : CapturePermission  // User denied
}
```

### Checking Permission Status

```kotlin
val capturer = ScreenCapturer.resolve()
val status = capturer.permissionStatus()

when (status) {
    CapturePermission.Granted -> startCapture()
    CapturePermission.Pending -> showMessage("Please grant screen capture permission")
    is CapturePermission.Denied -> showError(status.reason)
}
```

## Error Handling

### CaptureError Types

```kotlin
sealed class CaptureError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(reason: String) : CaptureError(reason)
    class NoSuchSource(source: CaptureSource) : CaptureError("No such source: $source")
    class SourceLost(source: CaptureSource) : CaptureError("Source lost: $source")
    class Unsupported(reason: String) : CaptureError(reason)
    class Internal(cause: Throwable) : CaptureError("Internal error", cause)
}
```

### Common Error Scenarios

| Scenario | Error | Solution |
|----------|-------|----------|
| Portal not installed | `Internal` | Install xdg-desktop-portal |
| User denied permission | `PermissionDenied` | Request permission again |
| No displays available | `NoSuchSource` | Check display connection |
| PipeWire not available | `Internal` | Install PipeWire |
| Session timeout | `Internal` | Retry session creation |

## Performance Considerations

### Frame Transfer Methods

| Method | Performance | Memory Usage | GPU Support | Notes |
|--------|-------------|---------------|--------------|-------|
| SHM (Shared Memory) | Medium | High (CPU copy) | ❌ No | Works everywhere, simple |
| DMA-BUF | High | Low (zero-copy) | ✅ Yes | Requires GPU support |

### Current Implementation

- **MVP**: Uses SHM via `OpenPipeWireRemote` + `mmap()`
- **Future**: Add DMA-BUF support for zero-copy GPU frames

### Frame Rate Control

```kotlin
CaptureConfig(
    frameRate: 30,  // Target frame rate
    pixelFormat: PixelFormat.RGBA8,
    captureCursor: true,
    region: null
)
```

The capture loop respects the frame rate:
```kotlin
delay(1000L / config.frameRate)
```

## Testing

### Manual Testing

```bash
# List displays
./gradlew :samples:screen-capture-demo:run --args="--list-displays"

# Capture from display
./gradlew :samples:screen-capture-demo:run --args="--capture-display 0 --output /tmp/captures"

# Capture from window (if supported)
./gradlew :samples:screen-capture-demo:run --args="--capture-window <id> --output /tmp/captures"
```

### Testing on Different Compositors

| Composer | Portal Available | wlr-screencopy | Expected Backend |
|----------|------------------|----------------|------------------|
| GNOME (Mutter) | ✅ Yes | ❌ No | xdg-desktop-portal |
| KDE (KWin) | ✅ Yes | ❌ No | xdg-desktop-portal |
| Sway | ✅ Yes | ✅ Yes | xdg-desktop-portal (preferred) |
| Hyprland | ✅ Yes | ✅ Yes | xdg-desktop-portal (preferred) |
| Weston | ✅ Yes | ❌ No | xdg-desktop-portal |
| River | ❌ No | ✅ Yes | wlr-screencopy (fallback) |

### Debugging

Enable debug output:
```bash
# Set environment variable for verbose logging
export KADRE_CAPTURE_DEBUG=true
./gradlew :samples:screen-capture-demo:run --args="--capture-display 0"
```

Check D-Bus communication:
```bash
# Monitor D-Bus messages
dbus-monitor --profile "interface='org.freedesktop.portal.ScreenCast'"
```

## Future Enhancements

### 1. Full PipeWire Integration
- Use libpipewire FFM bindings instead of `dbus-send` + `mmap`
- Support DMA-BUF for zero-copy GPU frames
- Handle multiple streams (for multi-monitor setups)

### 2. Session Management
- Track multiple active sessions
- Handle session closure signals
- Implement proper cleanup on errors

### 3. Cursor Capture
- Support `cursor_mode` parameter:
  - `none`: No cursor
  - `hidden`: Cursor is hidden
  - `embedded`: Cursor embedded in frames
  - `metadata`: Cursor position/data separate

### 4. Region Capture
- Support `region` parameter in `CaptureConfig`
- Allow capturing specific screen regions
- Use `SelectRegion` portal method

### 5. Window Capture
- Properly implement window selection
- Handle window resizing/moving
- Track window lifecycle

### 6. Permission Persistence
- Store granted permissions
- Avoid re-prompting user on app restart
- Handle permission revocation

## References

- [xdg-desktop-portal Documentation](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html)
- [PipeWire Documentation](https://pipewire.pages.freedesktop.org/)
- [Wayland Protocol Documentation](https://wayland.freedesktop.org/docs/html/)
- [Freedesktop D-Bus Specification](https://dbus.freedesktop.org/doc/dbus-specification.html)

## Appendix: D-Bus Interface Definition

```xml
<interface name="org.freedesktop.portal.ScreenCast">
  <method name="CreateSession">
    <arg type="s" name="app_id" direction="in"/>
    <arg type="o" name="session_handle" direction="out"/>
  </method>
  
  <method name="SelectSources">
    <arg type="o" name="session_handle" direction="in"/>
    <arg type="as" name="types" direction="in"/>
    <arg type="a{sv}" name="options" direction="in"/>
  </method>
  
  <method name="Start">
    <arg type="o" name="session_handle" direction="in"/>
    <arg type="s" name="parent_window" direction="in"/>
    <arg type="a{sv}" name="options" direction="in"/>
    <arg type="a{sv}" name="results" direction="out"/>
  </method>
  
  <method name="OpenPipeWireRemote">
    <arg type="o" name="session_handle" direction="in"/>
    <arg type="h" name="node_id" direction="in"/>
    <arg type="h" name="fd" direction="out"/>
    <arg type="a{sv}" name="metadata" direction="out"/>
  </method>
  
  <method name="Close">
    <arg type="o" name="session_handle" direction="in"/>
  </method>
  
  <signal name="SessionCreated">
    <arg type="o" name="session_handle"/>
  </signal>
  
  <signal name="SourceSelected">
    <arg type="o" name="session_handle"/>
    <arg type="s" name="source_type"/>
    <arg type="s" name="source_handle"/>
  </signal>
  
  <signal name="Started">
    <arg type="o" name="session_handle"/>
    <arg type="u" name="stream_node_id"/>
  </signal>
  
  <signal name="Closed">
    <arg type="o" name="session_handle"/>
    <arg type="u" name="reason"/>
  </signal>
</interface>
```
