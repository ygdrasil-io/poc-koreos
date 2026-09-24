package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.HostSurface
import org.w3c.dom.HTMLElement

/**
 * Lends the attached element for the duration of [block].
 *
 * The element is valid only while [block] runs: retaining it, or using it after the callback
 * returns, is outside the contract. A lease is not reentrant for the same surface and a second
 * concurrent lease returns `TemporarilyUnavailable(retryable = true)`.
 *
 * Fails without invoking [block] when the surface is not attached, when another lease is held,
 * or when the receiver is not a web surface (`Unsupported(PlatformSurfaceAccess)`).
 */
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withWebElement(block: (HTMLElement) -> R): KadreResult<R> {
    val lease = this as? WebElementLeasePort
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
    return lease.lease { element -> block(element as HTMLElement) }
}
