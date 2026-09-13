package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState

/** One detached native gamepad projection before the runtime allocates its public identity. */
public data class GamepadPortGamepad(
    public val key: Long,
    public val descriptor: GamepadDescriptor,
    public val state: GamepadState,
    public val routing: GamepadRoutingState,
    public val capabilities: GamepadCapabilities,
) {
    init {
        require(key >= 0L) { "gamepad key must be non-negative" }
    }
}

/** One per-session gamepad observation from a backend. */
public sealed interface GamepadPortEvent {
    public data class Connected(public val gamepad: GamepadPortGamepad) : GamepadPortEvent
    public data class Disconnected(public val key: Long) : GamepadPortEvent {
        init {
            require(key >= 0L) { "gamepad key must be non-negative" }
        }
    }

    public data class StateChanged(
        public val key: Long,
        public val state: GamepadState,
    ) : GamepadPortEvent {
        init {
            require(key >= 0L) { "gamepad key must be non-negative" }
        }
    }

    public data class RoutingChanged(
        public val key: Long,
        public val routing: GamepadRoutingState,
        public val state: GamepadState,
    ) : GamepadPortEvent {
        init {
            require(key >= 0L) { "gamepad key must be non-negative" }
        }
    }
}

/**
 * Unstable backend SPI for one session's detached gamepad projection.
 *
 * Keys are usable only across this backend/runtime boundary. The runtime allocates each public
 * `GamepadId`, validates state against the advertised descriptor, and owns public flows.
 */
public interface GamepadPort : AutoCloseable {
    /**
     * Current complete projection visible to this session.
     *
     * Backends must serialize changes to this snapshot with their observer notifications. Runtime
     * consumers install the observer before reading this property, so an event received during
     * installation may be safely deduplicated against the subsequent snapshot.
     */
    public val gamepads: List<GamepadPortGamepad>

    /** Installs a listener for changes that occur after installation begins. */
    public fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable

    /**
     * Starts one already-admitted effect for the gamepad identified by [key].
     *
     * The backend owns physical-device arbitration and returns a closeable owner only after the
     * native effect has been accepted. The runtime owns the public effect session and duration.
     * Native failures must be mapped to [KadreResult.Failure], never thrown across this seam.
     */
    public fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect>

    override public fun close()
}

/** Private physical effect owner returned across the backend/runtime seam. */
public interface GamepadPortEffect : AutoCloseable {
    /** Requests the native effect stop and reports any backend failure without throwing. */
    public fun requestStop(): KadreResult<Unit>

    /**
     * Releases the physical owner and guarantees that its effect is no longer active.
     *
     * Implementations must be idempotent because teardown may follow an explicit stop request.
     */
    override public fun close()
}
