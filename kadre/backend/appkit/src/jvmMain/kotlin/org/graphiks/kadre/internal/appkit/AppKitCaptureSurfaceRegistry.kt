package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.surface.SurfaceId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session-local association between Kadre surfaces and their AppKit window identities.
 *
 * A registration prevents future resolutions when it is closed. Leases already acquired
 * from that registration retain the immutable window identity until they are closed, so a
 * concurrent close cannot redirect an in-flight native capture reservation.
 */
internal class AppKitCaptureSurfaceRegistry {
    private val lock = Any()
    private val entries = mutableMapOf<SurfaceId, Entry>()
    private val revoked = mutableSetOf<SurfaceId>()

    fun register(surface: SurfaceId, windowNumber: Long): AutoCloseable {
        require(windowNumber > 0L) { "windowNumber must be positive" }

        val entry = Entry(windowNumber)
        synchronized(lock) {
            check(entries[surface] == null) {
                "Surface $surface is already registered for capture"
            }
            entries[surface] = entry
            revoked.remove(surface)
        }

        return AutoCloseable {
            synchronized(lock) {
                if (entries[surface] === entry) {
                    entries.remove(surface)
                    revoked += surface
                }
            }
        }
    }

    fun acquire(surface: SurfaceId): AppKitCaptureSurfaceLease? =
        (resolve(surface) as? AppKitCaptureSurfaceResolution.Available)?.lease

    fun resolve(surface: SurfaceId): AppKitCaptureSurfaceResolution = synchronized(lock) {
        entries[surface]?.let { entry ->
            entry.leases += 1
            return@synchronized AppKitCaptureSurfaceResolution.Available(
                AppKitCaptureSurfaceLease(entry.windowNumber) {
                    synchronized(lock) {
                        entry.leases -= 1
                    }
                },
            )
        }
        if (surface in revoked) AppKitCaptureSurfaceResolution.Revoked
        else AppKitCaptureSurfaceResolution.Unknown
    }

    /** Internal lifecycle diagnostic used to prove reservations do not retain a closed lease. */
    internal fun activeLeaseCount(surface: SurfaceId): Int = synchronized(lock) {
        entries[surface]?.leases ?: 0
    }

    private class Entry(
        val windowNumber: Long,
        var leases: Int = 0,
    )
}

internal sealed interface AppKitCaptureSurfaceResolution {
    data class Available(val lease: AppKitCaptureSurfaceLease) : AppKitCaptureSurfaceResolution

    data object Revoked : AppKitCaptureSurfaceResolution

    data object Unknown : AppKitCaptureSurfaceResolution
}

internal class AppKitCaptureSurfaceLease internal constructor(
    val windowNumber: Long,
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            release()
        }
    }
}
