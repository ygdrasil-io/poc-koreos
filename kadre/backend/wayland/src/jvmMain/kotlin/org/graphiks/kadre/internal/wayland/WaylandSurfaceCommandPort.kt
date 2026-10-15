package org.graphiks.kadre.internal.wayland

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.SurfaceCommandPort
import org.graphiks.kadre.internal.runtime.SurfaceRedrawCommand
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommand
import org.graphiks.kadre.internal.runtime.SurfaceUpdateCommandOutcome
import org.graphiks.kadre.surface.SurfaceId

/**
 * Coalesced redraw over `wl_surface.frame` pacing: N admitted requests within
 * one frame cycle produce exactly one damage+attach+commit. Requests arrive
 * on the runtime thread; frame callbacks fire on the pump thread. The frame
 * callback is a pacing signal only — never presentation proof, never a clock.
 */
internal class WaylandSurfaceCommandPort : SurfaceCommandPort {

    private class SurfaceEntry(val peer: WaylandWindowPeer) {
        val frameArmed = AtomicBoolean(false)
        val dirty = AtomicBoolean(false)
    }

    private val surfaces = ConcurrentHashMap<SurfaceId, SurfaceEntry>()

    fun register(surfaceId: SurfaceId, peer: WaylandWindowPeer) {
        val entry = SurfaceEntry(peer)
        surfaces[surfaceId] = entry
        // exactly ONE pacing chain per surface, armed at registration:
        // requestRedraw only marks dirty, so bursts coalesce by construction
        fun pace() {
            // re-arm the pacing listener for the next cycle (kffi frame
            // listeners are one-shot by contract)
            peer.window.onNextFrame { pace() }
            val shouldCommit = entry.frameArmed.getAndSet(false)
            if (shouldCommit && entry.dirty.compareAndSet(true, false)) {
                entry.peer.window.commitCurrent()
            }
        }
        peer.window.onNextFrame { pace() }
    }

    fun unregister(surfaceId: SurfaceId) {
        surfaces.remove(surfaceId)
    }

    override fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit> {
        val entry = surfaces[command.surfaceId]
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        entry.dirty.set(true)
        entry.frameArmed.set(true)
        return KadreResult.Success(Unit)
    }

    override suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome> =
        KadreResult.Failure(
            KadreFailure.Unsupported(org.graphiks.kadre.diagnostics.KadreOperation.UpdateSurface),
        )
}
