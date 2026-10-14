package org.graphiks.kadre.internal.wayland

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fenêtre native factice pour les tests O2: enregistre les stimuli (titre,
 * app_id, attach) et permet de délivrer configure/close manuellement.
 */
internal class FakeNativeWindow(
    override val width: Int = 64,
    override val height: Int = 64,
) : WaylandNativeWindow {

    private val arena = Arena.ofShared()
    private val segment: MemorySegment = arena.allocate(width.toLong() * height * 4)

    val titles = CopyOnWriteArrayList<String>()
    val appIds = CopyOnWriteArrayList<String>()
    val attachCount = AtomicInteger(0)
    val closeCount = AtomicInteger(0)
    val configureListeners = CopyOnWriteArrayList<(Int) -> Unit>()
    val closeListeners = CopyOnWriteArrayList<() -> Unit>()

    override val nativeAddresses: Pair<Long, Long>
        get() = 0xDL to 0x12L

    override fun content(): MemorySegment = segment

    private val serialCounter = java.util.concurrent.atomic.AtomicInteger(1)

    override fun commitInitial() {
        // simule la réponse du compositor: un configure arrive après le commit
        val serial = serialCounter.getAndIncrement()
        Thread {
            configureListeners.forEach { it(serial) }
        }.start()
    }

    override fun onConfigure(listener: (serial: Int) -> Unit) {
        configureListeners.add(listener)
    }

    override fun ackLatestConfigure() {
        // rien: le faux n'a pas de protocole
    }

    override fun onToplevelEvents(
        onConfigure: (width: Int, height: Int, states: List<Int>) -> Unit,
        onClose: () -> Unit,
    ) {
        closeListeners.add(onClose)
    }

    override fun onEnter(listener: () -> Unit) {
        // diagnostic uniquement (voir registre phase 2)
    }

    override fun onNextFrame(listener: () -> Unit) {
        // le mapping O2 est prouvé par l'attach enregistré
    }

    override fun setTitle(title: String) {
        titles.add(title)
    }

    override fun setAppId(appId: String) {
        appIds.add(appId)
    }

    /** Délivre un configure avec la taille du faux. */
    fun deliverConfigure(serial: Int = 1) {
        configureListeners.forEach { it(serial) }
    }

    /** Délivre la fermeture native (compositor side). */
    fun deliverNativeClose() {
        closeListeners.forEach { it() }
    }

    override fun close() {
        closeCount.incrementAndGet()
    }
}
