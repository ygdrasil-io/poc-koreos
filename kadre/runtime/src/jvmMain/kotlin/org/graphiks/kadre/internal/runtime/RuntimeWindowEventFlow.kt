package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.graphiks.kadre.application.EventDeliverySpan
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.window.WindowEvent

/** Policy-controlled delivery for the events owned by one runtime window. */
internal class RuntimeWindowEventFlow(
    private val policy: WindowDeliveryPolicy,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionFailureHandler: (KadreFailure) -> Unit,
    private val closeWindow: () -> Unit,
) {
    private val ingressLock = Any()
    private val ingress = BoundedWindowEventScheduler(
        discreteCapacity = policy.discreteEvents.ingressCapacity,
        geometryDelivery = policy.geometryChanges,
    )
    private var ingressDrainActive = false
    private val subscribersLock = Any()
    private val subscribers = linkedMapOf<WindowEventSubscriber, RuntimeEventCollectorLease>()
    private var terminal: WindowEventFlowTerminal? = null

    val events: Flow<WindowEvent> = flow {
        val subscriber = WindowEventSubscriber(policy)
        when (val registration = register(subscriber)) {
            WindowSubscriberRegistration.Registered -> Unit
            WindowSubscriberRegistration.Closed -> return@flow
            is WindowSubscriberRegistration.Failed -> throw KadreException(registration.failure)
        }
        try {
            while (true) emit(subscriber.next() ?: break)
        } finally {
            unregister(subscriber)
        }
    }

    fun publish(event: WindowEvent) {
        var shouldDrain = false
        var reportOverflow = false
        var terminalOverflow: Boolean? = null
        synchronized(ingressLock) {
            if (terminal != null) return
            when (val offered = ingress.offer(event)) {
                WindowSchedulerOffer.Accepted -> shouldDrain = ensureIngressDrainLocked()
                WindowSchedulerOffer.Dropped -> {
                    reportOverflow = true
                    shouldDrain = ensureIngressDrainLockedIfNeeded()
                }

                WindowSchedulerOffer.DiscreteOverflow -> {
                    terminalOverflow = policy.discreteEvents.ingressOverflow == IngressOverflowAction.FailSession
                }

                is WindowSchedulerOffer.GeometryOverflow -> when (offered.action) {
                    ContinuousOverflowAction.DropOldestAndReport,
                    ContinuousOverflowAction.DropLatestAndReport,
                    -> error("drop handled by window event scheduler")

                    ContinuousOverflowAction.CloseSource -> terminalOverflow = false
                    ContinuousOverflowAction.FailSession -> terminalOverflow = true
                }
            }
        }
        if (reportOverflow) safeReport(KadreException(overflowFailure()))
        terminalOverflow?.let { failSession ->
            terminalise(failSession)
            return
        }
        if (shouldDrain) drainIngress()
    }

    private fun drainIngress() {
        while (true) {
            val event = synchronized(ingressLock) {
                if (terminal != null) {
                    ingressDrainActive = false
                    return
                }
                ingress.poll() ?: run {
                    ingressDrainActive = false
                    return
                }
            }
            deliver(event)
        }
    }

    private fun deliver(event: WindowEvent) {
        val subscribers = synchronized(ingressLock) {
            if (terminal != null) return
            synchronized(subscribersLock) { subscribers.keys.toList() }
        }
        var closeSource = false
        var failSession = false
        subscribers.forEach { subscriber ->
            when (subscriber.offer(event.copyForWindowDelivery())) {
                WindowSubscriberOffer.Accepted -> Unit
                WindowSubscriberOffer.Dropped -> safeReport(KadreException(overflowFailure()))
                WindowSubscriberOffer.CloseSource -> closeSource = true
                WindowSubscriberOffer.FailSession -> failSession = true
            }
        }
        if (closeSource || failSession) terminalise(failSession)
    }

    fun close() = terminalise(failSession = false, failure = null)

    private fun terminalise(
        failSession: Boolean,
        failure: KadreFailure? = overflowFailure(),
    ) {
        val subscribers = synchronized(ingressLock) {
            if (terminal != null) return
            ingress.clear()
            ingressDrainActive = false
            terminal = failure?.let(WindowEventFlowTerminal::Failed) ?: WindowEventFlowTerminal.Closed
            synchronized(subscribersLock) { subscribers.keys.toList() }
        }
        subscribers.forEach { subscriber ->
            subscriber.terminate(failure?.let(::KadreException), drain = true)
        }
        if (failure != null) safeReport(KadreException(failure))
        if (failSession) safeFailSession(checkNotNull(failure)) else if (failure != null) closeWindow()
    }

    private fun register(subscriber: WindowEventSubscriber): WindowSubscriberRegistration =
        synchronized(ingressLock) {
            when (val knownTerminal = terminal) {
                WindowEventFlowTerminal.Closed -> WindowSubscriberRegistration.Closed
                is WindowEventFlowTerminal.Failed -> WindowSubscriberRegistration.Failed(knownTerminal.failure)
                null -> synchronized(subscribersLock) {
                    when (val admission = eventCollectorGate.tryAcquire()) {
                        is org.graphiks.kadre.diagnostics.KadreResult.Failure ->
                            WindowSubscriberRegistration.Failed(admission.reason)

                        is org.graphiks.kadre.diagnostics.KadreResult.Success -> {
                            check(subscribers.put(subscriber, admission.value) == null)
                            WindowSubscriberRegistration.Registered
                        }
                    }
                }
            }
        }

    private fun unregister(subscriber: WindowEventSubscriber) {
        val lease = synchronized(subscribersLock) { subscribers.remove(subscriber) }
        lease?.close()
        subscriber.dispose()
    }

    private fun safeReport(cause: Throwable) {
        try {
            failureReporter.report(cause)
        } catch (_: Exception) {
            // Error reporting is best-effort and must not alter delivery terminality.
        } catch (_: LinkageError) {
            // Error reporting is best-effort and must not alter delivery terminality.
        }
    }

    private fun safeFailSession(failure: KadreFailure) {
        try {
            sessionFailureHandler(failure)
        } catch (cause: Exception) {
            safeReport(cause)
        } catch (cause: LinkageError) {
            safeReport(cause)
        }
    }

    private fun ensureIngressDrainLocked(): Boolean {
        if (ingressDrainActive) return false
        ingressDrainActive = true
        return true
    }

    private fun ensureIngressDrainLockedIfNeeded(): Boolean =
        if (ingress.isEmpty()) false else ensureIngressDrainLocked()
}

private enum class WindowEventLane { Discrete, Geometry }

private sealed interface WindowSchedulerOffer {
    data object Accepted : WindowSchedulerOffer
    data object Dropped : WindowSchedulerOffer
    data object DiscreteOverflow : WindowSchedulerOffer
    data class GeometryOverflow(val action: ContinuousOverflowAction) : WindowSchedulerOffer
}

private class BoundedWindowEventScheduler(
    private val discreteCapacity: Int,
    private val geometryDelivery: ContinuousDelivery,
) {
    private val entries = mutableListOf<WindowEvent>()

    fun offer(event: WindowEvent): WindowSchedulerOffer = when (event.windowLane()) {
        WindowEventLane.Discrete -> {
            if (entries.count { it.windowLane() == WindowEventLane.Discrete } >= discreteCapacity) {
                WindowSchedulerOffer.DiscreteOverflow
            } else {
                entries += event
                WindowSchedulerOffer.Accepted
            }
        }

        WindowEventLane.Geometry -> offerGeometry(event)
    }

    fun poll(): WindowEvent? {
        if (entries.isEmpty()) return null
        val index = entries.indices.minBy { entries[it].stamp.sequence.value }
        return entries.removeAt(index)
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    fun clear() = entries.clear()

    private fun offerGeometry(event: WindowEvent): WindowSchedulerOffer = when (val delivery = geometryDelivery) {
        ContinuousDelivery.Latest,
        ContinuousDelivery.Coalesced,
        -> {
            val sequence = event.stamp.sequence.value
            val lastBarrier = entries.asSequence()
                .filter { it.windowLane() == WindowEventLane.Discrete && it.stamp.sequence.value < sequence }
                .maxOfOrNull { it.stamp.sequence.value }
                ?: Long.MIN_VALUE
            val nextBarrier = entries.asSequence()
                .filter { it.windowLane() == WindowEventLane.Discrete && it.stamp.sequence.value > sequence }
                .minOfOrNull { it.stamp.sequence.value }
                ?: Long.MAX_VALUE
            val existingIndex = entries.indexOfFirst {
                it.windowLane() == WindowEventLane.Geometry &&
                    it.stamp.sequence.value > lastBarrier &&
                    it.stamp.sequence.value < nextBarrier
            }
            if (existingIndex >= 0) {
                entries[existingIndex] = coalesceWindowGeometry(entries[existingIndex], event)
            } else {
                entries += event
            }
            WindowSchedulerOffer.Accepted
        }

        is ContinuousDelivery.Buffered -> {
            val geometry = entries.indices.filter { entries[it].windowLane() == WindowEventLane.Geometry }
            if (geometry.size < delivery.capacity) {
                entries += event
                WindowSchedulerOffer.Accepted
            } else {
                when (delivery.onOverflow) {
                    ContinuousOverflowAction.DropOldestAndReport -> {
                        entries.removeAt(geometry.minBy { entries[it].stamp.sequence.value })
                        entries += event
                        WindowSchedulerOffer.Dropped
                    }

                    ContinuousOverflowAction.DropLatestAndReport -> WindowSchedulerOffer.Dropped
                    ContinuousOverflowAction.CloseSource,
                    ContinuousOverflowAction.FailSession,
                    -> WindowSchedulerOffer.GeometryOverflow(delivery.onOverflow)
                }
            }
        }
    }
}

private sealed interface WindowSubscriberRegistration {
    data object Registered : WindowSubscriberRegistration
    data object Closed : WindowSubscriberRegistration
    data class Failed(val failure: KadreFailure) : WindowSubscriberRegistration
}

private sealed interface WindowSubscriberOffer {
    data object Accepted : WindowSubscriberOffer
    data object Dropped : WindowSubscriberOffer
    data object CloseSource : WindowSubscriberOffer
    data object FailSession : WindowSubscriberOffer
}

private sealed interface WindowSubscriberTerminal {
    data object Closed : WindowSubscriberTerminal
    data class Failed(val cause: Throwable) : WindowSubscriberTerminal
}

private sealed interface WindowEventFlowTerminal {
    data object Closed : WindowEventFlowTerminal
    data class Failed(val failure: KadreFailure) : WindowEventFlowTerminal
}

private class WindowEventSubscriber(private val policy: WindowDeliveryPolicy) {
    private val lock = Any()
    private val signal = Channel<Unit>(capacity = 1)
    private val scheduler = BoundedWindowEventScheduler(
        discreteCapacity = policy.discreteEvents.collectorCapacity,
        geometryDelivery = policy.geometryChanges,
    )
    private var terminal: WindowSubscriberTerminal? = null

    fun offer(event: WindowEvent): WindowSubscriberOffer {
        val result = synchronized(lock) {
            if (terminal != null) return WindowSubscriberOffer.Accepted
            when (val offered = scheduler.offer(event)) {
                WindowSchedulerOffer.Accepted -> WindowSubscriberOffer.Accepted
                WindowSchedulerOffer.Dropped -> WindowSubscriberOffer.Dropped
                WindowSchedulerOffer.DiscreteOverflow -> when (policy.discreteEvents.collectorOverflow) {
                    CollectorOverflowAction.CancelSlowCollector -> {
                        scheduler.clear()
                        terminal = WindowSubscriberTerminal.Failed(
                            SlowCollectorCancellationException(
                                "window event collector exceeded its policy capacity",
                            ),
                        )
                        WindowSubscriberOffer.Accepted
                    }

                    CollectorOverflowAction.CloseSource -> WindowSubscriberOffer.CloseSource
                    CollectorOverflowAction.FailSession -> WindowSubscriberOffer.FailSession
                }

                is WindowSchedulerOffer.GeometryOverflow -> when (offered.action) {
                    ContinuousOverflowAction.DropOldestAndReport,
                    ContinuousOverflowAction.DropLatestAndReport,
                    -> WindowSubscriberOffer.Dropped

                    ContinuousOverflowAction.CloseSource -> WindowSubscriberOffer.CloseSource
                    ContinuousOverflowAction.FailSession -> WindowSubscriberOffer.FailSession
                }
            }
        }
        signal.trySend(Unit)
        return result
    }

    suspend fun next(): WindowEvent? {
        while (true) {
            val knownTerminal = synchronized(lock) {
                scheduler.poll()?.let { return it }
                terminal
            }
            when (knownTerminal) {
                WindowSubscriberTerminal.Closed -> return null
                is WindowSubscriberTerminal.Failed -> throw knownTerminal.cause
                null -> signal.receive()
            }
        }
    }

    fun terminate(cause: Throwable?, drain: Boolean) {
        synchronized(lock) {
            if (terminal != null) return
            if (!drain) scheduler.clear()
            terminal = cause?.let(WindowSubscriberTerminal::Failed) ?: WindowSubscriberTerminal.Closed
        }
        signal.trySend(Unit)
    }

    fun dispose() {
        synchronized(lock) { scheduler.clear() }
        signal.cancel()
    }
}

private fun WindowEvent.windowLane(): WindowEventLane = when (this) {
    is WindowEvent.GeometryChanged -> WindowEventLane.Geometry
    is WindowEvent.PropertiesChanged,
    is WindowEvent.CloseRequested,
    is WindowEvent.Closing,
    -> WindowEventLane.Discrete
}

private fun WindowEvent.copyForWindowDelivery(): WindowEvent = when (this) {
    is WindowEvent.GeometryChanged -> copy(state = state.copy(), stamp = stamp.copy())
    is WindowEvent.PropertiesChanged -> copy(state = state.copy(), changed = changed.toSet(), stamp = stamp.copy())
    is WindowEvent.CloseRequested -> copy(stamp = stamp.copy())
    is WindowEvent.Closing -> copy(stamp = stamp.copy())
}

private fun coalesceWindowGeometry(previous: WindowEvent, latest: WindowEvent): WindowEvent {
    check(previous is WindowEvent.GeometryChanged && latest is WindowEvent.GeometryChanged)
    val (earlier, later) = if (previous.stamp.sequence.value <= latest.stamp.sequence.value) {
        previous to latest
    } else {
        latest to previous
    }
    return later.copy(stamp = coalescedWindowStamp(earlier.stamp, later.stamp))
}

private fun coalescedWindowStamp(previous: EventStamp, latest: EventStamp): EventStamp {
    val first = previous.deliverySpan?.firstSequence ?: previous.sequence
    val previousCount = previous.deliverySpan?.eventCount ?: 1L
    val latestCount = latest.deliverySpan?.eventCount ?: 1L
    return latest.copy(
        deliverySpan = EventDeliverySpan(first, latest.sequence, Math.addExact(previousCount, latestCount)),
    )
}

private fun overflowFailure(): KadreFailure = KadreFailure.SourceOverflow(KadreResourceKind.Window)
