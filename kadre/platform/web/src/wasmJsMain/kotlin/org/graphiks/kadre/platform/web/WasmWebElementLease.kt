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
 * returns, is outside the contract. A lease is not reentrant for the same surface: a lease started
 * while another is in flight returns `TemporarilyUnavailable(retryable = true)`, and once the
 * surface has closed every lease returns `Closed(Surface)`.
 *
 * Fails without invoking [block] when the surface is not attached, when another lease is in flight,
 * or when the receiver is not a web surface (`Unsupported(PlatformSurfaceAccess)`).
 */
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withWebElement(block: (HTMLElement) -> R): KadreResult<R> {
    val lease = this as? WebElementLeasePort
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
    return lease.lease { element -> block(element as HTMLElement) }
}
