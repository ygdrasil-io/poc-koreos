package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration

/**
 * Le scheduler de drain en horloge manuelle : la couture de scheduling est injectée
 * (`ManualSchedule`), la logique de coalescing est testée ici — le miroir du
 * `AndroidRedrawSchedulerTest` (clock injectée, sémantique testée).
 */
class UIKitRedrawSchedulerTest {
    private class ManualSchedule : (() -> Unit) -> Unit {
        val pending = mutableListOf<() -> Unit>()
        override fun invoke(p1: () -> Unit) {
            pending.add(p1)
        }

        fun tick() {
            val work = pending.toList()
            pending.clear()
            work.forEach { it() }
        }
    }

    @Test
    fun burstOfRequestsDrainsOnceWithTheLastGeneration() {
        val schedule = ManualSchedule()
        val drained = mutableListOf<SurfaceRedrawGeneration>()
        val scheduler = UIKitRedrawScheduler(onDrain = { drained.add(it) }, schedule = schedule)
        scheduler.request(SurfaceRedrawGeneration(1))
        scheduler.request(SurfaceRedrawGeneration(2))
        scheduler.request(SurfaceRedrawGeneration(3))
        assertEquals(1, schedule.pending.size) // coalescé : un seul drain programmé
        schedule.tick()
        assertEquals(listOf(SurfaceRedrawGeneration(3)), drained) // la DERNIÈRE génération
        assertEquals(0, schedule.pending.size)
    }

    @Test
    fun drainRearmsForRequestsAfterIt() {
        val schedule = ManualSchedule()
        val drained = mutableListOf<SurfaceRedrawGeneration>()
        val scheduler = UIKitRedrawScheduler({ drained.add(it) }, schedule)
        scheduler.request(SurfaceRedrawGeneration(1))
        schedule.tick()
        scheduler.request(SurfaceRedrawGeneration(2))
        assertEquals(1, schedule.pending.size)
        schedule.tick()
        assertEquals(listOf(SurfaceRedrawGeneration(1), SurfaceRedrawGeneration(2)), drained)
    }

    @Test
    fun cancelStopsEverythingForever() {
        val schedule = ManualSchedule()
        val drained = mutableListOf<SurfaceRedrawGeneration>()
        val scheduler = UIKitRedrawScheduler({ drained.add(it) }, schedule)
        scheduler.request(SurfaceRedrawGeneration(1))
        scheduler.cancel()
        schedule.tick()
        assertTrue(drained.isEmpty())
        scheduler.request(SurfaceRedrawGeneration(2))
        schedule.tick()
        assertTrue(drained.isEmpty())
    }
}
