package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
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

    override public fun close()
}
