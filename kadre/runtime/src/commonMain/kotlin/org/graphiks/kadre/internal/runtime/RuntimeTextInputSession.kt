package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.input.TextInputState
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.surface.LogicalRect

internal class RuntimeTextInputSession(
    private val owner: TextInputOwner,
    initialConfig: TextInputConfig,
    private val port: TextInputPort,
    private val eventStampSource: () -> EventStamp,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: (Throwable) -> Unit,
    private val onClosed: (RuntimeTextInputSession) -> Unit,
) : TextInputSession {
    private val lock = RuntimeLock()
    private val updateMutex = Mutex()
    private var documentText = initialConfig.surroundingText
    private var selection = initialConfig.selection
    private var acceptedDocumentRevision = initialConfig.documentRevision
    private var activeComposition: RuntimeTextInputComposition? = null
    private var observationEpoch = 0L
    private var currentState: TextInputState = TextInputState.Active(acceptedDocumentRevision, null)
    private var closed = false
    private val mutableState = MutableStateFlow(currentState)
    private val subscribers = linkedMapOf<TextInputEventSubscriber, RuntimeEventCollectorLease>()

    override val state: StateFlow<TextInputState> = mutableState.asStateFlow()
    override val events: Flow<TextInputEvent> = flow {
        val subscriber = TextInputEventSubscriber()
        when (val registration = registerSubscriber(subscriber)) {
            TextInputCollectorRegistration.Closed -> return@flow
            is TextInputCollectorRegistration.Failed -> throw KadreException(registration.failure)
            TextInputCollectorRegistration.Registered -> Unit
        }
        try {
            while (true) {
                val event = subscriber.next() ?: break
                emit(event)
            }
        } finally {
            unregisterSubscriber(subscriber)
        }
    }

    override suspend fun updateCursor(
        rect: LogicalRect,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit> = updateMutex.withLock {
        val command = lock.withLock lock@ {
            when {
                closed -> return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
                documentRevision != acceptedDocumentRevision -> {
                    return@withLock KadreResult.Failure(
                        KadreFailure.StaleRevision(acceptedDocumentRevision.value, documentRevision.value),
                    )
                }

                else -> TextInputCursorCommand(owner, rect, documentRevision)
            }
        }
        when (val result = port.updateCursor(command)) {
            is KadreResult.Failure -> result
            is KadreResult.Success -> lock.withLock {
                if (closed) {
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
                } else {
                    KadreResult.Success(Unit)
                }
            }
        }
    }

    override suspend fun updateSurroundingText(
        text: String,
        selection: TextRange,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit> = updateMutex.withLock {
        val admission = lock.withLock lock@ {
            when {
                closed -> return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
                selection.endExclusiveUtf16 > text.length -> {
                    return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("selection"))
                }

                documentRevision.value < acceptedDocumentRevision.value -> {
                    return@withLock KadreResult.Failure(
                        KadreFailure.StaleRevision(acceptedDocumentRevision.value, documentRevision.value),
                    )
                }

                documentRevision == acceptedDocumentRevision && text == documentText && selection == this.selection -> {
                    return@withLock KadreResult.Success(Unit)
                }

                documentRevision == acceptedDocumentRevision && text != documentText -> {
                    return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("text"))
                }

                documentRevision == acceptedDocumentRevision -> {
                    return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("selection"))
                }

                else -> {
                    val composition = activeComposition
                    if (composition != null && composition.rebasedRange(documentText, text) == null) {
                        return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("text"))
                    }
                    TextInputDocumentAdmission(
                        command = TextInputDocumentCommand(owner, text, selection, documentRevision),
                        observationEpoch = observationEpoch,
                    )
                }
            }
        }
        when (val result = port.updateDocument(admission.command)) {
            is KadreResult.Failure -> result
            is KadreResult.Success -> {
                val commit = lock.withLock {
                    when {
                        closed -> TextInputDocumentCommit.Closed
                        observationEpoch != admission.observationEpoch -> TextInputDocumentCommit.NonReconcilable
                        else -> {
                            val composition = activeComposition
                            val rebasedComposition = composition?.rebasedRange(documentText, text)
                            if (composition != null && rebasedComposition == null) {
                                TextInputDocumentCommit.NonReconcilable
                            } else {
                                documentText = text
                                this.selection = selection
                                acceptedDocumentRevision = documentRevision
                                activeComposition = composition?.copy(range = checkNotNull(rebasedComposition))
                                publishStateLocked(
                                    composingRange = rebasedComposition,
                                    documentRevision = documentRevision,
                                )
                                TextInputDocumentCommit.Committed
                            }
                        }
                    }
                }
                when (commit) {
                    TextInputDocumentCommit.Committed -> KadreResult.Success(Unit)
                    TextInputDocumentCommit.Closed ->
                        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))

                    TextInputDocumentCommit.NonReconcilable -> {
                        close()
                        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
                    }
                }
            }
        }
    }

    fun acceptObservation(observation: TextInputObservation): Boolean {
        val event = lock.withLock {
            if (closed || observation.baseRevision != acceptedDocumentRevision) return@withLock null
            val stamp = eventStampSource()
            when (observation) {
                is TextInputObservation.Replace -> {
                    if (!observation.range.isWithin(documentText)) return@withLock null
                    activeComposition = null
                    publishStateLocked(composingRange = null)
                    TextInputEvent.Replace(observation.range, observation.text, observation.baseRevision, stamp)
                }

                is TextInputObservation.SelectionChanged -> {
                    if (!observation.selection.isWithin(documentText)) return@withLock null
                    TextInputEvent.SelectionChanged(observation.selection, observation.baseRevision, stamp)
                }

                is TextInputObservation.CompositionChanged -> {
                    if (observation.range != null && !observation.range.isWithin(documentText)) return@withLock null
                    if (observation.selection != null && !observation.selection.isWithin(observation.text)) {
                        return@withLock null
                    }
                    if ((observation.range == null) != (observation.selection == null)) return@withLock null
                    activeComposition = observation.range?.let { range ->
                        RuntimeTextInputComposition(range, observation.text)
                    }
                    publishStateLocked(composingRange = observation.range)
                    TextInputEvent.CompositionChanged(
                        observation.range,
                        observation.text,
                        observation.selection,
                        observation.baseRevision,
                        stamp,
                    )
                }

                is TextInputObservation.Action -> TextInputEvent.Action(observation.action, observation.baseRevision, stamp)
            }.also { observationEpoch = observationEpoch.next() }
        } ?: return false
        publish(event)
        return true
    }

    fun suspend() = lock.withLock {
        if (currentState is TextInputState.Active) publishStateLocked(currentState.composingRangeOrNull, suspended = true)
    }

    fun resume() = lock.withLock {
        if (currentState is TextInputState.Suspended) publishStateLocked(currentState.composingRangeOrNull, suspended = false)
    }

    override fun close() {
        val subscribersToClose = lock.withLock {
            if (closed) return
            closed = true
            currentState = TextInputState.Closed
            mutableState.value = currentState
            subscribers.toList().also { subscribers.clear() }
        }
        safeCloseOwner()
        subscribersToClose.forEach { (subscriber, lease) ->
            lease.close()
            subscriber.terminate()
        }
        onClosed(this)
    }

    private fun publishStateLocked(
        composingRange: TextRange?,
        documentRevision: TextDocumentRevision = acceptedDocumentRevision,
        suspended: Boolean = currentState is TextInputState.Suspended,
    ) {
        currentState = if (suspended) {
            TextInputState.Suspended(documentRevision, composingRange)
        } else {
            TextInputState.Active(documentRevision, composingRange)
        }
        mutableState.value = currentState
    }

    private fun publish(event: TextInputEvent) {
        val subscribersToNotify = lock.withLock { subscribers.keys.toList() }
        subscribersToNotify.forEach { it.offer(event) }
    }

    private fun registerSubscriber(subscriber: TextInputEventSubscriber): TextInputCollectorRegistration = lock.withLock {
        if (closed) return@withLock TextInputCollectorRegistration.Closed
        when (val admission = eventCollectorGate.tryAcquire()) {
            is KadreResult.Failure -> TextInputCollectorRegistration.Failed(admission.reason)
            is KadreResult.Success -> {
                check(subscribers.put(subscriber, admission.value) == null)
                TextInputCollectorRegistration.Registered
            }
        }
    }

    private fun unregisterSubscriber(subscriber: TextInputEventSubscriber) {
        val lease = lock.withLock { subscribers.remove(subscriber) }
        lease?.close()
        subscriber.dispose()
    }

    private fun safeCloseOwner() {
        try {
            owner.close()
        } catch (cause: Exception) {
            failureReporter(cause)
        } catch (cause: Throwable) {
            if (cause.isLinkageFailure()) {
                failureReporter(cause)
            } else {
                throw cause
            }
        }
    }
}

private sealed interface TextInputCollectorRegistration {
    data object Registered : TextInputCollectorRegistration
    data object Closed : TextInputCollectorRegistration
    data class Failed(val failure: KadreFailure) : TextInputCollectorRegistration
}

private class TextInputEventSubscriber {
    private val channel = Channel<TextInputEvent>(Channel.UNLIMITED)

    fun offer(event: TextInputEvent) {
        channel.trySend(event)
    }

    suspend fun next(): TextInputEvent? = channel.receiveCatching().getOrNull()

    fun terminate() {
        channel.close()
    }

    fun dispose() {
        channel.cancel()
    }
}

private fun TextRange.isWithin(text: String): Boolean = endExclusiveUtf16 <= text.length

private data class RuntimeTextInputComposition(
    val range: TextRange,
    val text: String,
) {
    fun rebasedRange(document: String, snapshot: String): TextRange? {
        if (!range.isWithin(document)) return null
        val rebasedDocument = document.substring(0, range.startUtf16) + text + document.substring(range.endExclusiveUtf16)
        if (rebasedDocument != snapshot) return null
        return TextRange(range.startUtf16, range.startUtf16 + text.length)
    }
}

private data class TextInputDocumentAdmission(
    val command: TextInputDocumentCommand,
    val observationEpoch: Long,
)

private enum class TextInputDocumentCommit { Committed, Closed, NonReconcilable }

private fun Long.next(): Long {
    check(this < Long.MAX_VALUE) { "text input observation epoch space exhausted" }
    return this + 1L
}

private val TextInputState.composingRangeOrNull: TextRange?
    get() = when (this) {
        is TextInputState.Active -> composingRange
        is TextInputState.Suspended -> composingRange
        TextInputState.Closed -> null
    }