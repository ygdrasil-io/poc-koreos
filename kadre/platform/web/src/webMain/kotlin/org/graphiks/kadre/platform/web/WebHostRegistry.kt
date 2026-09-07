package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult

/**
 * Claims a target-provided stable host identity for the lifetime of one live web session.
 *
 * The target owns the identity representation.  It can therefore be an element identity on JS
 * and Wasm without allowing either DOM SDK type into this common source set.
 */
internal class WebHostRegistry {
    private val reservations = mutableListOf<Any>()

    fun reserve(identity: Any): KadreResult<WebHostReservation> {
        if (reservations.any { reserved -> reserved === identity }) {
            return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        }
        reservations.add(identity)
        return KadreResult.Success(WebHostReservation(this, identity))
    }

    internal fun release(identity: Any) {
        val reservationIndex = reservations.indexOfFirst { reserved -> reserved === identity }
        if (reservationIndex >= 0) reservations.removeAt(reservationIndex)
    }

    internal companion object {
        val shared: WebHostRegistry = WebHostRegistry()
    }
}

internal class WebHostReservation internal constructor(
    private val registry: WebHostRegistry,
    private val identity: Any,
) {
    private var released: Boolean = false

    fun release() {
        if (released) return
        released = true
        registry.release(identity)
    }
}
