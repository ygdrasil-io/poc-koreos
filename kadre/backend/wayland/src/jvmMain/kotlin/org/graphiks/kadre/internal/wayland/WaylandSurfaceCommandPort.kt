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
        surfaces[surfaceId] = SurfaceEntry(peer)
    }

    fun unregister(surfaceId: SurfaceId) {
        surfaces.remove(surfaceId)
    }

    override fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit> {
        val entry = surfaces[command.surfaceId]
            ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        entry.dirty.set(true)
        if (!entry.frameArmed.compareAndSet(false, true)) {
            // a frame callback is already pending: this request coalesces
            return KadreResult.Success(Unit)
        }
        entry.peer.window.onNextFrame {
            val restart = runCatching {
                if (entry.dirty.compareAndSet(true, false)) {
                    entry.peer.window.commitCurrent()
                }
                false
            }.getOrDefault(false)
            if (restart) {
                entry.frameArmed.set(false)
            }
        }
        return KadreResult.Success(Unit)
    }

    override suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome> =
        KadreResult.Failure(
            KadreFailure.Unsupported(org.graphiks.kadre.diagnostics.KadreOperation.UpdateSurface),
        )
}
