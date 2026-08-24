package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.HostSignal
import org.graphiks.kadre.application.KadreLifecycle
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.LifecycleEvent
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability

internal class RuntimeLifecycle(
    initialState: LifecycleState,
    initialCapabilities: LifecycleCapabilities,
    private val nextStamp: () -> EventStamp,
) : KadreLifecycle {
    private val mutableState = MutableStateFlow(initialState)
    private val mutableCapabilities = MutableStateFlow(initialCapabilities)
    private val mutableEvents = MutableSharedFlow<LifecycleEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private val mutableSignals = MutableSharedFlow<HostSignal>(extraBufferCapacity = SIGNAL_BUFFER_CAPACITY)

    override val state: StateFlow<LifecycleState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<LifecycleCapabilities> = mutableCapabilities.asStateFlow()
    override val events: Flow<LifecycleEvent> = mutableEvents.asSharedFlow()
    override val signals: Flow<HostSignal> = mutableSignals.asSharedFlow()

    @Synchronized
    fun updateState(next: LifecycleState): LifecycleState {
        val previous = mutableState.value
        if (previous == next) return previous
        if (previous.attachment == org.graphiks.kadre.application.AttachmentState.Detached) return previous

        mutableState.value = next
        check(mutableEvents.tryEmit(LifecycleEvent(previous, next, nextStamp()))) {
            "lifecycle event buffer overflow"
        }
        return next
    }

    @Synchronized
    fun updateCapabilities(next: LifecycleCapabilities) {
        if (mutableCapabilities.value != next) mutableCapabilities.value = next
    }

    @Synchronized
    fun emitMemoryPressure(level: MemoryPressureLevel) {
        require(mutableState.value.attachment != org.graphiks.kadre.application.AttachmentState.Detached) {
            "lifecycle is detached"
        }
        require(mutableCapabilities.value.memoryPressure == FeatureAvailability.Available) {
            "memory pressure is unavailable"
        }
        check(mutableSignals.tryEmit(HostSignal.MemoryPressure(level, nextStamp()))) {
            "host signal buffer overflow"
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY: Int = 64
        const val SIGNAL_BUFFER_CAPACITY: Int = 16
    }
}
