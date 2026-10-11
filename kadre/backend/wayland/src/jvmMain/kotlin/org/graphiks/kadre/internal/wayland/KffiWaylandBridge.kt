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
    private val registryListener = client.registry.addGlobalsListener(
        onGlobal = { _, interfaceName, version ->
            synchronized(globalsLock) {
                globals.removeAll { it.name == interfaceName }
                globals.add(WaylandGlobal(interfaceName, version))
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

    override fun close() {
        registryListener.revoke()
        client.close()
    }

    internal companion object {
        /** [name] = nom de socket (chemin absolu accepté) ; `null` = défaut libwayland. */
        internal fun connect(name: String?): KffiWaylandBridge {
            val client = WaylandClient.connect(name)
                ?: throw WaylandConnectException("display-connect-failed")
            return try {
                KffiWaylandBridge(client)
            } catch (failure: Throwable) {
                client.close()
                throw WaylandConnectException("registry-listener-failed", failure)
            }
        }
    }
}
