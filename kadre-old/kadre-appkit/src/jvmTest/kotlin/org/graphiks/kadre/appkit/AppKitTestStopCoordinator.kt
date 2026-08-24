package org.graphiks.kadre.appkit

internal class AppKitTestStopCoordinator<Event : Any>(
    private val operations: Operations<Event>,
) {
    private var state: State = State.Ready

    internal interface Operations<Event : Any> {
        fun stop()

        fun createApplicationDefinedEvent(): Event?

        fun postEventAtStart(event: Event)
    }

    @Synchronized
    fun requestStop() {
        when (val current = state) {
            State.Complete -> return
            is State.Failed -> throw current.failure
            State.Executing -> error("AppKit test stop request re-entered while executing")
            State.Ready -> state = State.Executing
        }

        try {
            operations.stop()
            val event = checkNotNull(operations.createApplicationDefinedEvent()) {
                "NSEventTypeApplicationDefined creation returned null"
            }
            operations.postEventAtStart(event)
            state = State.Complete
        } catch (failure: Throwable) {
            state = State.Failed(failure)
            throw failure
        }
    }

    private sealed interface State {
        data object Ready : State

        data object Executing : State

        data object Complete : State

        data class Failed(val failure: Throwable) : State
    }
}
