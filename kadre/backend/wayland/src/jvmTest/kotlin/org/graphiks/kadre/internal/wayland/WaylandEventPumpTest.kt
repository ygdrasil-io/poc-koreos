package org.graphiks.kadre.internal.wayland

import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kffi.posix.PosixWakeup

private class RecordingOperations : PumpOperations {
    var dispatchCount = 0
    var prepareCount = 0
    var readCount = 0
    var cancelCount = 0
    var flushCount = 0
    val pollWatchesWrite = ArrayList<Boolean>()
    var dispatchPendingResult = true
    var prepareReadResult = 0
    var readEventsResult = true
    var flushResult = WaylandFlushOutcome.Done
    var pollResults = ArrayDeque(listOf(PollOutcome.DisplayReady))
    var beforePoll: (() -> Unit)? = null

    override fun dispatchPending(): Boolean {
        dispatchCount++
        return dispatchPendingResult
    }

    override fun prepareRead(): Int {
        prepareCount++
        return prepareReadResult
    }

    override fun readEvents(): Boolean {
        readCount++
        return readEventsResult
    }

    override fun cancelRead() {
        cancelCount++
    }

    override fun flush(): WaylandFlushOutcome {
        flushCount++
        return flushResult
    }

    override fun poll(wakeFd: Int, watchWrite: Boolean): PollOutcome {
        pollWatchesWrite.add(watchWrite)
        beforePoll?.invoke()
        return pollResults.removeFirstOrNull() ?: PollOutcome.DisplayReady
    }
}

private class FakeWakeup : PosixWakeup {
    override val readFd: Int = 42
    var signalCount = 0
    var drainCount = 0
    var closed = false

    override fun signal(): Boolean {
        signalCount++
        return true
    }

    override fun drain(): Boolean {
        drainCount++
        return true
    }

    override fun close() {
        closed = true
    }
}

class WaylandEventPumpTest {

    @Test
    fun stopRequestedBeforeRunOnceDoesNothing() {
        val ops = RecordingOperations()
        val wake = FakeWakeup()
        val pump = WaylandEventPump(ops, wake)
        pump.requestStop()
        assertEquals(PumpStep.Stopped, pump.runOnce())
        assertEquals(0, ops.dispatchCount)
        assertEquals(0, ops.prepareCount)
        assertEquals(1, wake.signalCount)
    }

    @Test
    fun wakeOnlyReturnsToTheHostLoopForActionDraining() {
        val ops = RecordingOperations()
        ops.pollResults = ArrayDeque(listOf(PollOutcome.WakeOnly, PollOutcome.DisplayReady))
        val wake = FakeWakeup()
        val pump = WaylandEventPump(ops, wake)
        // Un réveil hors arrêt rend la main à la boucle hôte — c'est elle qui
        // draine les actions marshalées (créations de fenêtres) ; repoller
        // ici re-bloquerait le thread pompe sur un compositor silencieux.
        // Le wake est drainé (sinon la boucle tournerait à vide) et la
        // lecture préparée annulée (jamais deux prepare_read par cycle).
        assertEquals(PumpStep.Rearm, pump.runOnce())
        assertEquals(1, wake.drainCount)
        assertEquals(1, ops.cancelCount)
        assertEquals(0, ops.readCount)
        // le cycle suivant réarme proprement : prepare → poll → read
        assertEquals(PumpStep.Dispatched, pump.runOnce())
        assertEquals(2, ops.prepareCount)
        assertEquals(1, ops.readCount)
        assertEquals(1, wake.drainCount)
    }

    @Test
    fun stopDuringPollExitsThroughTheWakeWithoutReading() {
        val ops = RecordingOperations()
        val enteredPoll = CountDownLatch(1)
        val stopArmed = CountDownLatch(1)
        ops.beforePoll = {
            enteredPoll.countDown()
            stopArmed.await()
            // le vrai ops draine le wake ; le fake reflète juste l'issue
        }
        ops.pollResults = ArrayDeque(listOf(PollOutcome.WakeOnly))
        val wake = FakeWakeup()
        val pump = WaylandEventPump(ops, wake)
        val stopper = Thread {
            // La promesse « stop PENDANT le poll » doit être tenue par le
            // test : le stop est armé pendant que la pompe est bloquée dans
            // poll — ni avant runOnce (sortie immédiate sans lecture
            // préparée : cancelCount == 0), ni après le réveil (le chemin
            // WakeOnly-sans-arrêt rend la main à la boucle hôte).
            enteredPoll.await()
            pump.requestStop()
            stopArmed.countDown()
        }
        stopper.start()
        assertEquals(PumpStep.Stopped, pump.runOnce())
        stopper.join()
        assertEquals(1, ops.cancelCount)
        assertEquals(0, ops.readCount)
        assertTrue(pump.stopRequested())
    }

    @Test
    fun flushEagainWatchesWritability() {
        val ops = RecordingOperations()
        ops.flushResult = WaylandFlushOutcome.Eagain
        ops.pollResults = ArrayDeque(listOf(PollOutcome.DisplayReady))
        val pump = WaylandEventPump(ops, FakeWakeup())
        assertEquals(PumpStep.Dispatched, pump.runOnce())
        assertEquals(listOf(true), ops.pollWatchesWrite)
    }

    @Test
    fun flushDoneDoesNotWatchWritability() {
        val ops = RecordingOperations()
        ops.pollResults = ArrayDeque(listOf(PollOutcome.DisplayReady))
        val pump = WaylandEventPump(ops, FakeWakeup())
        assertEquals(PumpStep.Dispatched, pump.runOnce())
        assertEquals(listOf(false), ops.pollWatchesWrite)
    }

    @Test
    fun interruptedPollKeepsThePreparedRead() {
        val ops = RecordingOperations()
        ops.pollResults = ArrayDeque(
            listOf(PollOutcome.Interrupted, PollOutcome.Interrupted, PollOutcome.DisplayReady),
        )
        val pump = WaylandEventPump(ops, FakeWakeup())
        assertEquals(PumpStep.Dispatched, pump.runOnce())
        assertEquals(1, ops.prepareCount)
        assertEquals(0, ops.cancelCount)
        assertEquals(1, ops.readCount)
    }

    @Test
    fun dispatchFailureIsConnectionLost() {
        val ops = RecordingOperations()
        ops.dispatchPendingResult = false
        val pump = WaylandEventPump(ops, FakeWakeup())
        assertEquals(PumpStep.ConnectionLost, pump.runOnce())
        assertEquals(0, ops.prepareCount)
        assertEquals(0, ops.readCount)
    }

    @Test
    fun readFailureIsConnectionLost() {
        val ops = RecordingOperations()
        ops.readEventsResult = false
        ops.pollResults = ArrayDeque(listOf(PollOutcome.DisplayReady))
        val pump = WaylandEventPump(ops, FakeWakeup())
        assertEquals(PumpStep.ConnectionLost, pump.runOnce())
        assertEquals(0, ops.cancelCount) // lecture tentée puis erreur : pas d'annulation
    }

    @Test
    fun nonEmptyQueueRearmsWithoutPreparingTwice() {
        val ops = RecordingOperations()
        ops.prepareReadResult = -1
        val pump = WaylandEventPump(ops, FakeWakeup())
        assertEquals(PumpStep.Rearm, pump.runOnce())
        assertEquals(1, ops.prepareCount)
        assertEquals(0, ops.pollWatchesWrite.size)
        assertFalse(ops.readCount > 0)
    }
}
