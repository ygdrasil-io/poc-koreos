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
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.SlowCollectorCancellationException
import org.graphiks.kadre.surface.LogicalRect

internal class RuntimeTextInputSession(
    private val owner: TextInputOwner,
    initialConfig: TextInputConfig,
    private val port: TextInputPort,
    private val eventStampSource: () -> EventStamp,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: (Throwable) -> Unit,
    private val onClosed: (RuntimeTextInputSession) -> Unit,
    private val resources: ResourceBudgetPolicy = KadrePolicies.Default.resources,
    private val payloadBudget: RuntimePayloadBudget = RuntimeRetainedPayloadBudget(resources.maxRetainedPayloadBytesPerSession),
    initialPayloadLease: AutoCloseable? = null,
    private val deliveryPolicy: EventDeliveryPolicy = KadrePolicies.Default.input.discreteEvents,
    private val onTerminated: (KadreFailure) -> Unit = {},
    private val sessionFailureHandler: (KadreFailure) -> Unit = {},
) : TextInputSession {
    private val lock = RuntimeLock()
    private val updateMutex = Mutex()
    private var documentText = initialConfig.surroundingText
    private var selection = initialConfig.selection
    private var acceptedDocumentRevision = initialConfig.documentRevision
    private var activeComposition: RuntimeTextInputComposition? = null
    private var documentLease: AutoCloseable? = initialPayloadLease ?: when (val reserved = payloadBudget.retain(documentText.length * 2L)) {
        is KadreResult.Success -> reserved.value
        is KadreResult.Failure -> throw KadreException(reserved.reason)
    }
    private var compositionLease: AutoCloseable? = null
    private var observationEpoch = 0L
    private var currentState: TextInputState = TextInputState.Active(acceptedDocumentRevision, null)
    private var closed = false
    private var terminalFailure: KadreFailure? = null
    private val publications = ArrayDeque<TextInputPublication>()
    private var publicationDrainActive = false
    private val mutableState = MutableStateFlow(currentState)
    private val subscribers = linkedMapOf<TextInputEventSubscriber, RuntimeEventCollectorLease>()

    override val state: StateFlow<TextInputState> = mutableState.asStateFlow()
    internal val isClosed: Boolean get() = lock.withLock { closed }
    override val events: Flow<TextInputEvent> = flow {
        val subscriber = TextInputEventSubscriber(deliveryPolicy)
        when (val registration = registerSubscriber(subscriber)) {
            TextInputCollectorRegistration.Closed -> return@flow
            is TextInputCollectorRegistration.Failed -> throw KadreException(registration.failure)
            TextInputCollectorRegistration.Registered -> Unit
        }
        try {
            while (true) {
                val event = subscriber.next() ?: break
                try { emit(event.event) } finally { event.lease.close() }
            }
        } finally {
            unregisterSubscriber(subscriber)
        }
    }

    override suspend fun updateCursor(
        rect: LogicalRect,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit> = updateMutex.withLock {
        // The explicit `lock@` label on `withLock` is load bearing: without it the lambda's own
        // implicit label is what the `return@withLock` statements below target, so they would
        // return from this lambda (yielding the block's value) instead of returning from the
        // enclosing `updateMutex.withLock` block as they did before the lift.
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
        // Same load-bearing `lock@` label as `updateCursor`: it suppresses the lambda's implicit
        // label so the `return@withLock` statements below keep returning from the enclosing
        // `updateMutex.withLock` block rather than from this lambda.
        val admission = lock.withLock lock@ {
            when {
                closed -> return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
                text.length > resources.maxTextCodeUnitsPerValue -> return@withLock KadreResult.Failure(
                    KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, resources.maxTextCodeUnitsPerValue.toLong()))
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
        val pendingLease = when (val reserved = payloadBudget.retain(text.length * 2L)) {
            is KadreResult.Failure -> return@withLock reserved
            is KadreResult.Success -> reserved.value
        }
        var adopted = false
        try {
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
                                documentLease?.close()
                                documentLease = pendingLease
                                adopted = true
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
                    TextInputDocumentCommit.Committed -> {
                        // Legacy owners need no acknowledgment; queued native writers use it to release only
                        // observations whose new base revision has now become accepted by this runtime.
                        try { owner.documentApplied(documentRevision) }
                        catch (_: Throwable) { terminate(KadreFailure.Closed(KadreResourceKind.TextInputSession)) }
                        if (isClosed) KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
                        else KadreResult.Success(Unit)
                    }
                    TextInputDocumentCommit.Closed ->
                        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))

                    TextInputDocumentCommit.NonReconcilable -> {
                        close()
                        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession))
                    }
                }
            }
        }
        } finally { if (!adopted) pendingLease.close() }
    }

    fun acceptObservation(observation: TextInputObservation): Boolean {
        var failure: KadreFailure? = null
        var failSession = false
        var drain = false
        val admitted = lock.withLock {
            if (closed || observation.baseRevision != acceptedDocumentRevision) return@withLock null
            if (!observation.isValid(documentText)) return@withLock null
            val text = observation.payloadText
            if (text.length > resources.maxTextCodeUnitsPerValue) {
                failure = KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, resources.maxTextCodeUnitsPerValue.toLong())
                return@withLock null
            }
            if (publications.size >= deliveryPolicy.ingressCapacity) {
                failure = KadreFailure.SourceOverflow(KadreResourceKind.TextInputSession)
                failSession = deliveryPolicy.ingressOverflow == IngressOverflowAction.FailSession
                return@withLock null
            }
            val payload = when (val reserved = payloadBudget.retain(text.length * 2L)) {
                is KadreResult.Failure -> { failure = reserved.reason; return@withLock null }
                is KadreResult.Success -> RuntimeSharedPayloadLease(reserved.value)
            }
            val nextCompositionLease = if (observation is TextInputObservation.CompositionChanged && observation.range != null) {
                when (val reserved = payloadBudget.retain(text.length * 2L)) {
                    is KadreResult.Failure -> { payload.close(); failure = reserved.reason; return@withLock null }
                    is KadreResult.Success -> reserved.value
                }
            } else null
            try {
            val stamp = eventStampSource()
            val event = when (observation) {
                is TextInputObservation.Replace -> {
                    activeComposition = null
                    compositionLease?.close(); compositionLease = null
                    publishStateLocked(composingRange = null)
                    TextInputEvent.Replace(observation.range, observation.text, observation.baseRevision, stamp)
                }

                is TextInputObservation.SelectionChanged -> {
                    TextInputEvent.SelectionChanged(observation.selection, observation.baseRevision, stamp)
                }

                is TextInputObservation.CompositionChanged -> {
                    compositionLease?.close(); compositionLease = nextCompositionLease
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
            publications.addLast(TextInputPublication(event, payload))
            drain = ensurePublicationDrainLocked()
            true
            } catch (cause: Throwable) { payload.close(); nextCompositionLease?.close(); throw cause }
        } ?: false
        failure?.let { reason ->
            try { terminate(reason) } finally { if (failSession) sessionFailureHandler(reason) }
            return false
        }
        if (drain) drainPublications()
        return admitted && !isClosed
    }

    fun suspend() = lock.withLock {
        if (currentState is TextInputState.Active) publishStateLocked(currentState.composingRangeOrNull, suspended = true)
    }

    fun resume() = lock.withLock {
        if (currentState is TextInputState.Suspended) publishStateLocked(currentState.composingRangeOrNull, suspended = false)
    }

    override fun close() = finish(null)

    internal fun terminate(failure: KadreFailure) = finish(failure)

    private fun finish(failure: KadreFailure?) {
        var storage: List<AutoCloseable> = emptyList()
        var subscribersToClose: List<Pair<TextInputEventSubscriber, RuntimeEventCollectorLease>> = emptyList()
        val drain = lock.withLock {
            if (closed) return
            closed = true
            terminalFailure = failure
            currentState = TextInputState.Closed
            mutableState.value = currentState
            storage = listOfNotNull(documentLease, compositionLease)
            documentLease = null; compositionLease = null
            documentText = ""; activeComposition = null
            if (failure != null) {
                while (publications.isNotEmpty()) publications.removeFirst().payload.close()
                subscribersToClose = subscribers.toList(); subscribers.clear()
            }
            ensurePublicationDrainLocked()
        }
        try {
            if (failure != null) onTerminated(failure)
        } finally {
            // Detach the exact target before native close can call back into its former owner.
            try { onClosed(this) } finally {
                try { safeCloseOwner() } finally {
                    storage.forEach(AutoCloseable::close)
                    subscribersToClose.forEach { (subscriber, lease) ->
                        lease.close(); subscriber.terminate(failure?.let(::KadreException), drain=false)
                    }
                    if (drain) drainPublications()
                }
            }
        }
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

    private fun ensurePublicationDrainLocked(): Boolean {
        if (publicationDrainActive) return false
        publicationDrainActive = true
        return true
    }

    private fun drainPublications() {
        while (true) {
            var terminalSubscribers: List<Pair<TextInputEventSubscriber, RuntimeEventCollectorLease>> = emptyList()
            val next = lock.withLock {
                val publication = publications.removeFirstOrNull()
                if (publication == null) {
                    publicationDrainActive = false
                    if (closed) terminalSubscribers = subscribers.toList().also { subscribers.clear() }
                    null
                } else publication to subscribers.keys.toList()
            }
            if (next == null) {
                terminalSubscribers.forEach { (subscriber, lease) ->
                    lease.close(); subscriber.terminate(terminalFailure?.let(::KadreException), drain=terminalFailure == null)
                }
                return
            }
            val (publication, targets) = next
            try {
                for (subscriber in targets) {
                    when (subscriber.offer(TextInputEnvelope(publication.event, publication.payload.fork()))) {
                        TextInputSubscriberOffer.Accepted -> Unit
                        TextInputSubscriberOffer.CloseSource -> terminate(KadreFailure.SourceOverflow(KadreResourceKind.TextInputSession))
                        TextInputSubscriberOffer.FailSession -> {
                            val reason = KadreFailure.SourceOverflow(KadreResourceKind.TextInputSession)
                            try { terminate(reason) } finally { sessionFailureHandler(reason) }
                        }
                    }
                }
            } finally { publication.payload.close() }
        }
    }

    private fun registerSubscriber(subscriber: TextInputEventSubscriber): TextInputCollectorRegistration = lock.withLock {
        if (closed) return@withLock terminalFailure?.let(TextInputCollectorRegistration::Failed) ?: TextInputCollectorRegistration.Closed
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

private data class TextInputPublication(val event: TextInputEvent, val payload: RuntimeSharedPayloadLease)
private data class TextInputEnvelope(val event: TextInputEvent, val lease: AutoCloseable)
private enum class TextInputSubscriberOffer { Accepted, CloseSource, FailSession }

private class TextInputEventSubscriber(private val policy: EventDeliveryPolicy) {
    private val lock = RuntimeLock()
    private val queue = ArrayDeque<TextInputEnvelope>()
    private val signal = Channel<Unit>(1)
    private var closed = false
    private var failure: Throwable? = null

    fun offer(value: TextInputEnvelope): TextInputSubscriberOffer {
        val result = lock.withLock {
            if (closed) { value.lease.close(); return@withLock TextInputSubscriberOffer.Accepted }
            if (queue.size >= policy.collectorCapacity) {
                value.lease.close()
                when (policy.collectorOverflow) {
                    CollectorOverflowAction.CancelSlowCollector -> {
                        clearLocked(); closed = true
                        failure = SlowCollectorCancellationException("text input collector exceeded its policy capacity")
                        TextInputSubscriberOffer.Accepted
                    }
                    CollectorOverflowAction.CloseSource -> TextInputSubscriberOffer.CloseSource
                    CollectorOverflowAction.FailSession -> TextInputSubscriberOffer.FailSession
                }
            } else { queue.addLast(value); TextInputSubscriberOffer.Accepted }
        }
        signal.trySend(Unit)
        return result
    }
    suspend fun next(): TextInputEnvelope? {
        while (true) {
            val done = lock.withLock {
                queue.removeFirstOrNull()?.let { return it }
                if (closed) { failure?.let { throw it }; true } else false
            }
            if (done) return null
            signal.receive()
        }
    }
    fun terminate(cause: Throwable?, drain: Boolean) {
        lock.withLock {
            if (closed) return
            if (!drain) clearLocked()
            closed = true; failure = cause
        }
        signal.trySend(Unit)
    }
    fun dispose() { lock.withLock { closed = true; clearLocked() }; signal.cancel() }
    private fun clearLocked() { while (queue.isNotEmpty()) queue.removeFirst().lease.close() }
}

private val TextInputObservation.payloadText: String get() = when (this) {
    is TextInputObservation.Replace -> text
    is TextInputObservation.CompositionChanged -> text
    else -> ""
}
private fun TextInputObservation.isValid(document: String): Boolean = when (this) {
    is TextInputObservation.Replace -> range.isWithin(document)
    is TextInputObservation.SelectionChanged -> selection.isWithin(document)
    is TextInputObservation.CompositionChanged -> (range == null) == (selection == null) &&
        (range == null || range.isWithin(document)) && (selection == null || selection.isWithin(text))
    is TextInputObservation.Action -> true
}

private fun TextRange.isWithin(text: String): Boolean = endExclusiveUtf16 <= text.length

private data class RuntimeTextInputComposition(
    val range: TextRange,
    val text: String,
) {
    fun rebasedRange(document: String, snapshot: String): TextRange? {
        if (!range.isWithin(document)) return null
        val expected = document.length.toLong() - (range.endExclusiveUtf16 - range.startUtf16) + text.length
        if (expected != snapshot.length.toLong()) return null
        if (!snapshot.regionMatches(0, document, 0, range.startUtf16) ||
            !snapshot.regionMatches(range.startUtf16, text, 0, text.length) ||
            !snapshot.regionMatches(range.startUtf16 + text.length, document, range.endExclusiveUtf16, document.length - range.endExclusiveUtf16)) return null
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
