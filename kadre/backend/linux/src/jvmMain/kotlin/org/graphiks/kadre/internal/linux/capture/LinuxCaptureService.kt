package org.graphiks.kadre.internal.linux.capture

import org.graphiks.kadre.internal.runtime.CapturePort

/**
 * Public entry point of the Linux capture control plane for host
 * backends (the same seam as [org.graphiks.kadre.internal.linux.EvdevGamepadService]):
 * one port per session, availability observed per design D1.
 */
public object LinuxCaptureService {
    public fun create(): CapturePort = LinuxCapturePort.create()
}
