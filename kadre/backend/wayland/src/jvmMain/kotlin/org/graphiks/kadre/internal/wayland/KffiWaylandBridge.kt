package org.graphiks.kadre.internal.wayland

import org.graphiks.kffi.wayland.managed.WaylandClient

/**
 * Adaptateur kadre sur la facade managée kffi (`KFFI-WAYLAND-004`) : Kadre
 * ne touche aucun segment, stub ou descriptor FFM — la politique (quand
 * révoquer, quand admettre, quels globals sont requis) reste ici, la
 * mécanique de binding vit dans kffi. Un fake injectable remplace cette
 * classe dans les tests O2.
 */
internal class KffiWaylandBridge private constructor(
    private val client: WaylandClient,
) : WaylandNativeBridge {

    private val globalsLock = Any()
    private val globals = ArrayList<WaylandGlobal>()
    private val outputsLock = Any()
    private val outputs = LinkedHashMap<Int, org.graphiks.kffi.wayland.managed.WaylandOutput>()
    private val outputStates = LinkedHashMap<Int, org.graphiks.kffi.wayland.managed.WaylandOutputState>()
    private val outputChangeListeners = ArrayList<() -> Unit>()
    private val registryListener = client.registry.addGlobalsListener(
        onGlobal = { name, interfaceName, version ->
            synchronized(globalsLock) {
                globals.removeAll { it.name == interfaceName }
                globals.add(WaylandGlobal(interfaceName, version))
            }
            if (interfaceName == "wl_output") {
                bindOutput(name, version)
            }
            if (interfaceName == "wl_seat") {
                synchronized(seatLock) { seatGlobalName = name }
            }
        },
    )

    override val displayFd: Int get() = client.fileDescriptor

    override fun globals(): List<WaylandGlobal> = synchronized(globalsLock) { ArrayList(globals) }

    override fun roundtrip(): Boolean = client.roundtrip()

    override fun dispatchPending(): Boolean = client.dispatchPending()

    override fun prepareRead(): Int = client.prepareRead()

    override fun readEvents(): Boolean = client.readEvents()

    override fun cancelRead() {
        client.cancelRead()
    }

    override fun flush(): WaylandFlushOutcome =
        if (client.flush() >= 0) WaylandFlushOutcome.Done else WaylandFlushOutcome.Eagain

    override fun pollEvents(wakeFd: Int, watchWrite: Boolean): PollOutcome = when (client.pollForEvents(wakeFd, watchWrite)) {
        org.graphiks.kffi.wayland.managed.WaylandPollOutcome.DisplayReady -> PollOutcome.DisplayReady
        org.graphiks.kffi.wayland.managed.WaylandPollOutcome.WakeOnly -> PollOutcome.WakeOnly
        org.graphiks.kffi.wayland.managed.WaylandPollOutcome.Interrupted -> PollOutcome.Interrupted
        org.graphiks.kffi.wayland.managed.WaylandPollOutcome.Failed -> PollOutcome.Lost
    }

    override fun connectionLost(): Boolean = client.hasError()

    /**
     * Binds one advertised output, tracks its done-batched state and notifies
     * change listeners (each wl_output.done is a batching point). Safe to
     * call from the dispatching thread.
     */
    private fun bindOutput(globalName: Int, advertised: Int) {
        val output = client.bindOutput(globalName, minOf(advertised, 4)) ?: return
        synchronized(outputsLock) { outputs[globalName] = output }
        output.addListener(
            onState = { state ->
                synchronized(outputsLock) { outputStates[globalName] = state }
                synchronized(outputsLock) { ArrayList(outputChangeListeners) }.forEach { it() }
            },
        )
    }

    /** Cached output observations; assembly reads only these copies. */
    override fun outputSnapshots(): List<WaylandOutputSnapshot> = synchronized(outputsLock) {
        outputs.mapNotNull { (name, output) ->
            outputStates[name]?.let { WaylandOutputSnapshot(name.toLong(), it) }
        }
    }

    /** Registers a change hook invoked at every output batching point. */
    override fun onOutputsChanged(listener: () -> Unit): AutoCloseable {
        synchronized(outputsLock) { outputChangeListeners.add(listener) }
        return AutoCloseable {
            synchronized(outputsLock) { outputChangeListeners.remove(listener) }
        }
    }

    override fun createWindowing(width: Int, height: Int): WaylandNativeWindow =
        KffiWaylandWindowAdapter.create(client, width, height)

    private var seatGlobalName: Int? = null
    private var boundSeat: org.graphiks.kffi.wayland.managed.WaylandSeat? = null
    private val seatLock = Any()

    /**
     * Binds the advertised seat and fans its raw events into [router].
     * Idempotent; called on the pump thread before the loop starts.
     */
    override fun attachSeat(router: WaylandInputRouter) {
        val seat = synchronized(seatLock) {
            if (boundSeat != null) return
            val name = seatGlobalName ?: return
            val bound = client.bindSeat(name) ?: return
            boundSeat = bound
            bound
        }
        seat.addPointerListener { event -> router.offer(event) }
        seat.addKeyboardListener { event -> router.offer(event) }
    }

    /** Releases the bound seat (session teardown). */
    override fun detachSeat() {
        synchronized(seatLock) {
            boundSeat?.close()
            boundSeat = null
        }
    }

    override fun close() {
        detachSeat()
        registryListener.revoke()
        client.close()
    }

    internal companion object {
        /** [name] = nom de socket (chemin absolu accepté) ; `null` = défaut libwayland. */
        internal fun connect(name: String?): KffiWaylandBridge {
            val client = WaylandClient.connect(name)
                ?: throw WaylandConnectException("display-connect-failed")
            return try {
                val bridge = KffiWaylandBridge(client)
                // les globals n'arrivent qu'au dispatch : le roundtrip initial
                // alimente le snapshot durable AVANT toute négociation
                if (!bridge.roundtrip()) {
                    throw WaylandConnectException("registry-roundtrip-failed")
                }
                bridge
            } catch (failure: Throwable) {
                client.close()
                if (failure is WaylandConnectException) throw failure
                throw WaylandConnectException("registry-listener-failed", failure)
            }
        }
    }
}
