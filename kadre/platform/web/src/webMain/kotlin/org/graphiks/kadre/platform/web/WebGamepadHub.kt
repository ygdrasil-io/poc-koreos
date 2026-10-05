package org.graphiks.kadre.platform.web

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.GamepadRouting

/**
 * The page's gamepad broker, in the role of the AppKit process broker
 * (`AppKitGameControllerBroker`): one owner of the browsing context's physical gamepads, with
 * session-owned [WebGamepadPort] projections opened into it. A browser exposes one page-wide
 * `navigator.getGamepads()` to every script in it — the physical truth is process-wide — so the
 * broker, not the session, owns the poll.
 *
 * **The poll is authoritative and frame-driven.** `navigator.getGamepads()` is the only state
 * source there is; the DOM's connection events carry no pad data and mean one thing — poll again
 * now. So the hub registers both connection listeners (through the [dom] seam, which hears them
 * wherever the browser fires them) and runs exactly one pending animation frame at a time while at
 * least one port is open: each frame polls, then re-arms. The last port close cancels the pending
 * frame and withdraws the listeners; a page that hides fires no frames and therefore polls not at
 * all, which is why a resume diffs against the last *observed* values — nothing was observed in
 * between, and nothing pretends otherwise.
 *
 * **The diff is against the last observed canonical state, per DOM index.** The browser's array
 * positions are the DOM gamepad indices and its nulls are the absent ones, so a poll maps the
 * connected pads against the table: a new index is a connection (descriptor frozen from that very
 * poll — a reconnect is a new connection and freezes a fresh descriptor), a reading that changes
 * the pad's canonical state is a state change, a vanished index a disconnection. The comparison is
 * against the canonical state — what the model publishes — never the raw readings, which need not
 * equal themselves: a hostile pad (persistent `NaN`, over-window values, signed zeros) publishes
 * exactly the events its canonical states differ by, never a per-frame stream of no-ops. Holes
 * produce nothing. Every lifecycle fact fans out to
 * every open port, suspended ones included (the AppKit precedent: lifecycle is always fanned); a
 * state change fans out only to routed ports, because a suspended projection publishes neutral
 * controls and a real reading would contradict the snapshot it sits beside.
 *
 * **State before event, always.** The table is updated inside the lock, the events are delivered
 * outside it — by the time an observer could see a `Connected`, `gamepads` already includes the
 * pad, so the snapshot and the event agree for a synchronous observer and the runtime's
 * deduplication against its initial read needs no cooperation from here.
 *
 * The [dom] seam and the [frames] scheduler are owned by the hub and closed with it. The shared
 * hub is page-global and lives as long as the page; `close` exists for symmetry with the broker it
 * mirrors and for the tests that own a hub of their own.
 */
internal class WebGamepadHub(
    private val dom: WebGamepadDom,
    private val frames: WebFrameScheduler,
) : AutoCloseable {
    private val lock = RuntimeLock()
    private val pads = linkedMapOf<Int, HubPad>()
    private val ports = linkedSetOf<WebGamepadPort>()
    private var frameRegistration: AutoCloseable? = null
    private var appearedRegistration: AutoCloseable? = null
    private var disappearedRegistration: AutoCloseable? = null
    private var closed: Boolean = false

    internal companion object {
        /** The one broker of this page, over the browsing context the module runs in. */
        val shared: WebGamepadHub = WebGamepadHub(webGamepadDom(), webFrameScheduler())
    }

    /**
     * Opens one session projection and starts polling if this is the page's first.
     *
     * The open port polls once, immediately, before anything can observe it — a pad the browser
     * already reports is in the very first snapshot the session's runtime reads, with no event
     * emitted for it (the port has no observer yet; the runtime's initial read is the connection).
     * A closed hub opens nothing, exactly like the AppKit broker.
     */
    fun openPort(): WebGamepadPort {
        val port = WebGamepadPort(this)
        val deliveries = lock.withLock {
            check(!closed) { "the web gamepad hub is closed" }
            check(ports.add(port)) { "the web gamepad port is already registered" }
            if (ports.size == 1) {
                startPollingLocked()
                diffLocked()
            } else {
                emptyList()
            }
        }
        deliver(deliveries)
        return port
    }

    /** The complete projection visible to [port]: every hub pad, through its routing. */
    internal fun gamepads(port: WebGamepadPort): List<GamepadPortGamepad> = lock.withLock {
        if (closed || !port.isOpenLocked()) return@withLock emptyList()
        pads.values.map { pad -> sourceForLocked(port, pad) }
    }

    /** Installs the port's one observer; the runtime installs exactly one. */
    internal fun installObserver(port: WebGamepadPort, observer: (GamepadPortEvent) -> Unit): AutoCloseable =
        lock.withLock {
            check(!closed && port.isOpenLocked()) { "the web gamepad port is closed" }
            port.installObserverLocked(observer)
        }

    /**
     * Stores [routing] for [port] and re-arbitrates every port, because the active-session
     * arbitration may have moved with it — the AppKit broker's reconciliation. Every port whose
     * derived routing changed receives one `RoutingChanged` per pad: the fresh recorded state when
     * it became routed, the neutral one when it became suspended.
     */
    internal fun updateRouting(port: WebGamepadPort, routing: GamepadPortRouting) {
        val deliveries = lock.withLock {
            if (closed || !ports.contains(port)) return@withLock emptyList()
            port.updateRoutingLocked(routing)
            reconcileRoutingLocked()
        }
        deliver(deliveries)
    }

    /**
     * Closes [port] with the hub. Idempotent; a hub-closed port closes again as nothing.
     *
     * A port that closed while one of its effects ran revokes every owner it still holds — the
     * runtime is terminating the session (the `ParentSessionStopping` terminal), and an effect whose
     * session is gone stops here, once, with every later stop or close of the revoked owner a quiet
     * no-op.
     */
    internal fun closePort(port: WebGamepadPort) {
        val deliveries = lock.withLock {
            if (!ports.remove(port)) return@withLock emptyList()
            port.closeFromHubLocked()
            port.revokeEffectsLocked()
            // The port that left may have been the one the active-session arbitration had elected:
            // the survivors re-arbitrate before anything could observe the gap, exactly as they do
            // for an updateRouting — a port whose derived routing changed hears one RoutingChanged
            // per pad, so its observer and its snapshot can never disagree about what it is.
            val deliveries = reconcileRoutingLocked()
            if (ports.isEmpty()) stopPollingLocked()
            deliveries
        }
        deliver(deliveries)
    }

    /**
     * Closes every open port, stops the poll, closes the dom seam this hub owns. Idempotent: every
     * later call, like every late connection announcement, publishes nothing. Every port's effect
     * owners are revoked, exactly as a per-port close revokes them.
     */
    override fun close() {
        val owned = lock.withLock {
            if (closed) return
            closed = true
            ports.forEach { port ->
                port.closeFromHubLocked()
                port.revokeEffectsLocked()
            }
            ports.clear()
            stopPollingLocked()
            dom
        }
        owned.close()
    }

    /** One poll, from a connection announcement or an animation frame. No ports, nothing to diff. */
    private fun poll() {
        val deliveries = lock.withLock {
            if (closed || ports.isEmpty()) return@withLock emptyList()
            diffLocked()
        }
        deliver(deliveries)
    }

    /**
     * The one diff: the browser's answer against the table, per DOM index, holes skipped. Returns
     * the deliveries it produced; the table is up to date before any of them is delivered.
     */
    private fun diffLocked(): List<HubDelivery> {
        val deliveries = mutableListOf<HubDelivery>()
        val observed = mutableSetOf<Int>()
        for (pad in dom.getGamepads()) {
            if (pad == null || !pad.connected) continue
            observed += pad.index
            val existing = pads[pad.index]
            if (existing == null) {
                // Frozen here: one connection, one descriptor — and one effect capability, probed
                // from the actuator this very poll reports (or the honest Unsupported of one it does
                // not). A reconnect is a new connection and freezes fresh facts of its own.
                val descriptor = WebGamepadMapping.descriptor(pad)
                val fresh = HubPad(
                    key = pad.index,
                    descriptor = descriptor,
                    lastState = WebGamepadMapping.state(pad, descriptor),
                    hapticActuator = pad.hapticActuator,
                    capabilities = WebGamepadEffects.capabilities(pad, pad.hapticActuator, dom.secureContext),
                )
                pads[pad.index] = fresh
                ports.forEach { port ->
                    val observer = port.observerLocked() ?: return@forEach
                    deliveries += HubDelivery(observer, GamepadPortEvent.Connected(sourceForLocked(port, fresh)))
                }
            } else {
                // The diff is against the canonical state — the one the model publishes — never the
                // raw readings: a reading the canonicalization maps onto the state already
                // published (a NaN that need not equal itself, an over-window value, a signed zero)
                // is not a change, and a hostile pad must never stream no-op events.
                val next = WebGamepadMapping.state(pad, existing.descriptor)
                if (next != existing.lastState) {
                    // Recorded whether anyone is routed or not: a suspended session that resumes
                    // must find the values the pad reads now, not the ones it read when it was
                    // suspended.
                    existing.lastState = next
                    ports.forEach { port ->
                        val observer = port.observerLocked() ?: return@forEach
                        if (!routedLocked(port)) return@forEach
                        deliveries += HubDelivery(observer, GamepadPortEvent.StateChanged(pad.index.toLong(), next))
                    }
                }
            }
        }
        pads.keys.filterNot(observed::contains).forEach { index ->
            pads.remove(index)
            ports.forEach { port ->
                val observer = port.observerLocked() ?: return@forEach
                deliveries += HubDelivery(observer, GamepadPortEvent.Disconnected(index.toLong()))
            }
        }
        return deliveries
    }

    private fun reconcileRoutingLocked(): List<HubDelivery> = buildList {
        ports.forEach { port ->
            val observer = port.observerLocked() ?: return@forEach
            val next = routingForLocked(port)
            if (port.projectedRoutingLocked() == next) return@forEach
            port.setProjectedRoutingLocked(next)
            pads.values.forEach { pad ->
                add(
                    HubDelivery(
                        observer,
                        GamepadPortEvent.RoutingChanged(pad.key.toLong(), next, stateForLocked(next, pad)),
                    ),
                )
            }
        }
    }

    /** The projection of [pad] through [port]'s routing: the pad's own reading, or the neutral one. */
    private fun sourceForLocked(port: WebGamepadPort, pad: HubPad): GamepadPortGamepad = GamepadPortGamepad(
        key = pad.key.toLong(),
        descriptor = pad.descriptor,
        state = stateForLocked(routingForLocked(port), pad),
        routing = routingForLocked(port),
        capabilities = pad.capabilities,
    )

    /**
     * Starts one already-admitted effect for the pad [key] names, through [port].
     *
     * The rules are `WebGamepadEffects`'s (the browser's actuator, the honest preconditions); the hub
     * contributes only what ownership requires: the port must be open, the pad must still be
     * connected, and a launched owner is recorded against the port so a close while it runs revokes
     * it — an effect cannot outlive the session projection that asked for it.
     */
    internal fun startEffect(port: WebGamepadPort, key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> = lock.withLock {
        if (closed || !port.isOpenLocked()) {
            return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        }
        val pad = pads[key.toInt()] ?: return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        val started = WebGamepadEffects.startEffect(pad.key.toLong(), effect, pad.hapticActuator)
        if (started is KadreResult.Success) {
            // The only owner WebGamepadEffects constructs is the web one.
            port.attachEffectLocked(started.value as WebGamepadEffect)
        }
        started
    }

    /** The routed pad publishes the state it last observed; the suspended one, neutral, always. */
    private fun stateForLocked(routing: GamepadRoutingState, pad: HubPad): GamepadState =
        if (routing == GamepadRoutingState.Routed) pad.lastState else WebGamepadMapping.neutralState(pad.descriptor)

    /** The routing state [port]'s context derives, mirroring the AppKit broker's rule verbatim. */
    private fun routingForLocked(port: WebGamepadPort): GamepadRoutingState =
        if (routedLocked(port)) GamepadRoutingState.Routed else GamepadRoutingState.Suspended

    private fun routedLocked(port: WebGamepadPort): Boolean {
        val context = port.routingLocked() ?: return false
        if (!context.foregroundActive) return false
        return when (context.policy) {
            GamepadRouting.AllForegroundSessions -> true
            GamepadRouting.ActiveSessionOnly -> ports.firstOrNull { candidate ->
                candidate.routingLocked()?.let { routing ->
                    routing.policy == GamepadRouting.ActiveSessionOnly && routing.foregroundActive
                } == true
            } === port
        }
    }

    private fun startPollingLocked() {
        appearedRegistration = dom.onGamepadAppeared(::poll)
        disappearedRegistration = dom.onGamepadDisappeared(::poll)
        scheduleFrameLocked()
    }

    /** Arms the one pending frame; the callback polls and re-arms for as long as the hub polls. */
    private fun scheduleFrameLocked() {
        frameRegistration = frames.schedule {
            frameRegistration = null
            poll()
            lock.withLock {
                if (!closed && ports.isNotEmpty() && frameRegistration == null) scheduleFrameLocked()
            }
        }
    }

    /** Withdraws the frame and both connect listeners. Only ever meaningful at zero open ports. */
    private fun stopPollingLocked() {
        frameRegistration?.close()
        frameRegistration = null
        appearedRegistration?.close()
        appearedRegistration = null
        disappearedRegistration?.close()
        disappearedRegistration = null
    }

    private fun deliver(deliveries: List<HubDelivery>) {
        deliveries.forEach { delivery -> delivery.observer(delivery.event) }
    }

    private data class HubDelivery(val observer: (GamepadPortEvent) -> Unit, val event: GamepadPortEvent)

    /**
     * One connected pad: the descriptor and effect capability frozen at connection (the capability
     * probed once, from the actuator this connection reported) and the canonical state of the last
     * poll observed — the diff's base and the routed projection's state, so the snapshot and the
     * events always speak of the same reading. The actuator is frozen with them: effects launch onto
     * the actuator the connection offered, never onto one a later poll happens to wrap.
     */
    private class HubPad(
        val key: Int,
        val descriptor: GamepadDescriptor,
        var lastState: GamepadState,
        val hapticActuator: WebDomHapticActuator?,
        val capabilities: GamepadCapabilities,
    )
}

