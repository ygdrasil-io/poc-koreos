package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.ExclusiveFullscreenAvailability
import org.graphiks.kadre.internal.runtime.ExclusiveFullscreenCancellationOutcome
import org.graphiks.kadre.internal.runtime.ExclusiveFullscreenCommand
import org.graphiks.kadre.internal.runtime.ExclusiveFullscreenDisplayLoss
import org.graphiks.kadre.internal.runtime.ExclusiveFullscreenPort
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowId
import org.graphiks.kadre.window.WindowOperationId
import org.graphiks.kadre.window.WindowState

/** Pointer-free availability of the future KFFI exclusive-display bridge. */
internal sealed interface AppKitExclusiveBridgeAvailability {
    data object Available : AppKitExclusiveBridgeAvailability

    data class Unavailable(val failure: KadreFailure) : AppKitExclusiveBridgeAvailability
}

/** Pointer-free terminal readback for one native display capture. */
internal sealed interface AppKitExclusiveDisplayTerminal {
    data class Captured(val modeKey: Long) : AppKitExclusiveDisplayTerminal

    data class Released(val modeKey: Long?) : AppKitExclusiveDisplayTerminal

    data object Unknown : AppKitExclusiveDisplayTerminal
}

internal data class AppKitExclusiveDisplayReadback(
    val terminal: AppKitExclusiveDisplayTerminal,
)

internal data class AppKitExclusiveDisplayReleaseResult(
    val terminal: AppKitExclusiveDisplayTerminal,
    val failures: List<KadreFailure.PlatformFailure> = emptyList(),
)

/** Pointer-free owner; Task 6 adapts the managed KFFI lease to this boundary. */
internal interface AppKitExclusiveDisplayLease : AutoCloseable {
    val displayKey: Long

    /** The KFFI-certified CoreGraphics identity, never inferred from a public display handle. */
    val displayId: Int
        get() = displayKey.toInt()

    fun readback(): AppKitExclusiveDisplayReadback

    fun release(): AppKitExclusiveDisplayReleaseResult

    override fun close() {
        release()
    }
}

internal sealed interface AppKitExclusiveDisplayOpenResult {
    data class Opened(val lease: AppKitExclusiveDisplayLease) : AppKitExclusiveDisplayOpenResult

    data class FailedBeforeCapture(
        val failure: KadreFailure.PlatformFailure,
    ) : AppKitExclusiveDisplayOpenResult

    data class FailedAfterCapture(
        val terminal: AppKitExclusiveDisplayTerminal,
        val cleanup: AppKitExclusiveDisplayReleaseResult,
        val recovery: AppKitExclusiveDisplayLease?,
        val failure: KadreFailure.PlatformFailure,
    ) : AppKitExclusiveDisplayOpenResult
}

/** Native bridge kept fake-only until Task 6 connects the managed KFFI helper. */
internal interface AppKitExclusiveDisplayBridge {
    val availability: AppKitExclusiveBridgeAvailability

    fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult
}

/** Task 5 production placeholder; Task 6 replaces it with the managed KFFI lease adapter. */
internal object UnsupportedAppKitExclusiveDisplayBridge : AppKitExclusiveDisplayBridge {
    override val availability: AppKitExclusiveBridgeAvailability =
        AppKitExclusiveBridgeAvailability.Unavailable(KadreFailure.Unsupported(KadreOperation.UpdateWindow))

    override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult =
        AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(
            KadreFailure.PlatformFailure(
                org.graphiks.kadre.diagnostics.KadrePlatform.AppKit,
                "exclusive-fullscreen",
                "bridge-unavailable",
            ),
        )
}

/** Executor which serializes one session's AppKit work. */
internal fun interface AppKitExclusiveExecutor {
    fun dispatch(task: () -> Unit): Boolean
}

private val ImmediateAppKitExclusiveExecutor = AppKitExclusiveExecutor { task ->
    task()
    true
}

/** Opaque broker ticket delivered to the AppKit presentation boundary. */
internal data class AppKitExclusiveWindowRequest(
    val displayKey: Long,
    val modeKey: Long,
    val token: Long,
    val windowId: WindowId,
    val requestedFullscreen: FullscreenMode,
    val displayId: Int = displayKey.toInt(),
)

/** Authoritative presentation readback, optionally accompanied by a typed failure. */
internal sealed interface AppKitExclusiveWindowResult {
    data class Read(val effectiveState: WindowState) : AppKitExclusiveWindowResult

    data class Failed(
        val failure: KadreFailure.PlatformFailure,
        val effectiveState: WindowState?,
    ) : AppKitExclusiveWindowResult
}

/** A presentation snapshot is opened before CoreGraphics mutates a display. */
internal sealed interface AppKitExclusiveWindowPreparation {
    data object Prepared : AppKitExclusiveWindowPreparation

    data class Failed(
        val failure: KadreFailure.PlatformFailure,
        val effectiveState: WindowState?,
    ) : AppKitExclusiveWindowPreparation
}

/** Presentation boundary implemented by the AppKit window port in Task 6. */
internal interface AppKitExclusiveWindowPort {
    fun prepare(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowPreparation =
        AppKitExclusiveWindowPreparation.Prepared

    fun enter(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowResult =
        error("exclusive presentation is not installed")

    fun exit(request: AppKitExclusiveWindowRequest): AppKitExclusiveWindowResult =
        error("exclusive presentation is not installed")

    fun readback(windowId: WindowId): WindowState? = null
}

/** Small command view which prevents the process registry from depending on runtime internals. */
internal interface AppKitExclusiveBrokerCommand {
    val windowId: WindowId
    val operationId: WindowOperationId
    val displayKey: Long
    val modeKey: Long
    val requestedFullscreen: FullscreenMode

    fun captureCommitted(): Boolean

    fun completed(effectiveState: WindowState)

    fun failed(failure: KadreFailure, effectiveState: WindowState? = null)
}

/** Process-wide arbitration for AppKit exclusive display ownership. */
internal class AppKitExclusiveDisplayBroker(
    private val bridge: AppKitExclusiveDisplayBridge,
    private val displayBroker: AppKitDisplayBroker? = null,
    private val recoveryExecutor: AppKitExclusiveExecutor = ImmediateAppKitExclusiveExecutor,
) : AutoCloseable {
    private val lock = Any()
    private val entries = linkedMapOf<Long, Entry>()
    private val ports = linkedSetOf<AppKitExclusiveFullscreenPort>()
    private var nextSessionId = 0L
    private var nextToken = 0L
    private var closed = false
    private var displayObservationAttempted = false
    private var displayObservation: AutoCloseable? = null
    private var displayObservationFailure: KadreFailure? = null
    private var latestInventory: DisplayPortSnapshot? = null
    private var publishedAvailability: ExclusiveFullscreenAvailability? = null

    private val quarantineFailure: KadreFailure.PlatformFailure
        get() = exclusiveFailure("release-unconfirmed")

    fun openPort(
        executor: AppKitExclusiveExecutor,
        windowPort: AppKitExclusiveWindowPort,
    ): AppKitExclusiveFullscreenPort {
        ensureDisplayObservation()
        return synchronized(lock) {
            check(!closed) { "AppKit exclusive display broker is closed" }
            val availability = currentAvailabilityLocked()
            if (publishedAvailability == null) publishedAvailability = availability
            AppKitExclusiveFullscreenPort(
                broker = this,
                sessionId = nextSessionIdLocked(),
                executor = executor,
                windowPort = windowPort,
                initialAvailability = availability,
            ).also { check(ports.add(it)) }
        }
    }

    fun reserve(
        port: AppKitExclusiveFullscreenPort,
        command: AppKitExclusiveBrokerCommand,
    ): KadreResult<Unit> {
        val entry = synchronized(lock) {
            if (closed || port !in ports || !port.isOpen()) {
                return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
            }
            when (val availability = currentAvailabilityLocked()) {
                ExclusiveFullscreenAvailability.Available -> Unit
                is ExclusiveFullscreenAvailability.Unavailable -> return KadreResult.Failure(availability.failure)
            }
            if (
                entries.containsKey(command.displayKey) ||
                entries.values.any { it.owner == Owner(port.sessionId, command.windowId) }
            ) {
                return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
            }
            Entry(
                displayKey = command.displayKey,
                modeKey = command.modeKey,
                owner = Owner(port.sessionId, command.windowId),
                token = nextTokenLocked(),
                operationId = command.operationId,
                command = command,
                port = port,
                state = LeaseState.Reserved,
            ).also { entries[command.displayKey] = it }
        }
        if (port.dispatch { commit(entry.displayKey, entry.token) }) return KadreResult.Success(Unit)
        val idleObservation = synchronized(lock) {
            entries[entry.displayKey]?.takeIf { it.token == entry.token }?.let {
                it.state = LeaseState.Released
                entries.remove(entry.displayKey)
            }
            detachIdleDisplayObservationLocked()
        }
        idleObservation?.close()
        return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
    }

    private fun commit(displayKey: Long, token: Long) {
        val entry = synchronized(lock) {
            entries[displayKey]?.takeIf { it.token == token && it.state == LeaseState.Reserved }
                ?.also { it.state = LeaseState.Committing }
        } ?: return
        when (val preparation = callPreparation { entry.port?.windowPort()?.prepare(entry.windowRequest(entry.command?.requestedFullscreen ?: FullscreenMode.Windowed)) }) {
            null,
            AppKitExclusiveWindowPreparation.Prepared,
            -> Unit
            is AppKitExclusiveWindowPreparation.Failed -> {
                terminalBeforeCapture(entry, preparation.failure)
                return
            }
        }
        when (val opened = callOpen(entry.displayKey, entry.modeKey)) {
            is AppKitExclusiveDisplayOpenResult.FailedBeforeCapture -> terminalBeforeCapture(
                entry,
                entry.terminalFailure ?: opened.failure,
            )
            is AppKitExclusiveDisplayOpenResult.Opened -> commitOpened(entry, opened.lease)
            is AppKitExclusiveDisplayOpenResult.FailedAfterCapture -> {
                val command = entry.command?.takeIf { it.captureCommitted() }
                if (command == null) {
                    synchronized(lock) {
                        entries[entry.displayKey]?.takeIf { it.token == entry.token }?.command = null
                    }
                }
                terminalAfterCapture(entry, opened, command)
            }
        }
    }

    private fun commitOpened(entry: Entry, lease: AppKitExclusiveDisplayLease) {
        val current = synchronized(lock) {
            entries[entry.displayKey]?.takeIf {
                it.token == entry.token && it.state == LeaseState.Committing
            }?.also { it.lease = lease }
        }
        if (current == null) {
            releaseLateLease(entry.displayKey, entry.token, lease)
            return
        }
        val command = current.command
        if (command == null || !command.captureCommitted()) {
            releaseCapturedWithoutRuntime(current, lease)
            return
        }
        val earlyReleaseCommand = synchronized(lock) {
            entries[current.displayKey]?.takeIf {
                it.token == current.token && it.state == LeaseState.Committing
            }?.takeIf { it.displayLossPending || it.releaseRequested }?.let {
                it.state = LeaseState.Releasing
                it.command
            }
        }
        if (earlyReleaseCommand != null || synchronized(lock) {
                entries[current.displayKey]?.let {
                    it.token == current.token && it.state == LeaseState.Releasing
                } == true
            }
        ) {
            terminalRelease(
                current,
                runCatching(lease::release).getOrNull(),
                earlyReleaseCommand,
                current.owner?.let { current.port?.windowPort()?.readback(it.windowId) },
                current.terminalFailure,
            )
            return
        }
        val request = current.windowRequest(command.requestedFullscreen, lease.displayId)
        val presentation = callPresentation { current.port?.windowPort()?.enter(request) }
        val readback = callReadback(lease)
        val success = presentation as? AppKitExclusiveWindowResult.Read
        if (
            success != null &&
            success.effectiveState.fullscreen == command.requestedFullscreen &&
            readback?.terminal == AppKitExclusiveDisplayTerminal.Captured(current.modeKey)
        ) {
            var releaseAfterPresentation = false
            val pendingReconfiguration = synchronized(lock) {
                entries[current.displayKey]?.takeIf {
                    it.token == current.token && it.state == LeaseState.Committing
                }?.let {
                    if (it.releaseRequested || it.displayLossPending) {
                        it.state = LeaseState.Releasing
                        releaseAfterPresentation = true
                        null
                    } else {
                        it.state = LeaseState.Active
                        it.reconfigurationPending
                    }
                }
            }
            if (releaseAfterPresentation) {
                releaseEntry(current.displayKey, current.token)
                return
            }
            if (pendingReconfiguration != null) {
                if (pendingReconfiguration && !reconcileCommittedEntry(current)) return
                val completion = synchronized(lock) {
                    entries[current.displayKey]?.takeIf {
                        it.token == current.token && it.state == LeaseState.Active
                    }?.let { it.command.also { commandToComplete -> it.command = null } }
                }
                completion?.completed(success.effectiveState)
                return
            }
            releaseLateLease(current.displayKey, current.token, lease)
            return
        }
        releaseAfterFailedCommit(current, command, presentation)
    }

    private fun releaseAfterFailedCommit(
        entry: Entry,
        command: AppKitExclusiveBrokerCommand,
        presentation: AppKitExclusiveWindowResult?,
    ) {
        val current = synchronized(lock) {
            entries[entry.displayKey]?.takeIf {
                it.token == entry.token && it.state == LeaseState.Committing
            }?.also { it.state = LeaseState.Releasing }
        } ?: run {
            entry.lease?.let { releaseLateLease(entry.displayKey, entry.token, it) }
            return
        }
        val release = runCatching { current.lease?.release() }.getOrNull()
        val exit = callPresentation {
            current.port?.windowPort()?.exit(current.windowRequest(FullscreenMode.Windowed))
        }
        val effective = when (exit) {
            is AppKitExclusiveWindowResult.Read -> exit.effectiveState
            is AppKitExclusiveWindowResult.Failed -> exit.effectiveState
            null -> when (presentation) {
                is AppKitExclusiveWindowResult.Read -> presentation.effectiveState
                is AppKitExclusiveWindowResult.Failed -> presentation.effectiveState
                null -> current.owner?.let { current.port?.windowPort()?.readback(it.windowId) }
            }
        }
        val failure = when (presentation) {
            is AppKitExclusiveWindowResult.Failed -> presentation.failure
            else -> exclusiveFailure("presentation-readback-failed")
        }
        terminalRelease(current, release, command, effective, failure)
    }

    private fun releaseCapturedWithoutRuntime(entry: Entry, lease: AppKitExclusiveDisplayLease) {
        val current = synchronized(lock) {
            entries[entry.displayKey]?.takeIf {
                it.token == entry.token && it.state == LeaseState.Committing
            }?.also { it.state = LeaseState.Releasing }
        }
        if (current == null) {
            releaseLateLease(entry.displayKey, entry.token, lease)
        } else {
            terminalRelease(current, runCatching(lease::release).getOrNull(), null, null, null)
        }
    }

    private fun terminalBeforeCapture(entry: Entry, failure: KadreFailure) {
        val terminal = synchronized(lock) {
            entries[entry.displayKey]?.takeIf { it.token == entry.token }?.let { current ->
                current.state = LeaseState.Released
                entries.remove(entry.displayKey)
                CommandDelivery(
                    port = current.port,
                    command = current.command,
                    idleObservation = detachIdleDisplayObservationLocked(),
                ).also {
                    current.command = null
                    current.port = null
                    current.owner = null
                }
            }
        }
        terminal?.command?.let { command ->
            terminal.port.dispatchOrRun { command.failed(failure) }
        }
        terminal?.idleObservation?.close()
    }

    private fun terminalAfterCapture(
        entry: Entry,
        opened: AppKitExclusiveDisplayOpenResult.FailedAfterCapture,
        command: AppKitExclusiveBrokerCommand?,
    ) {
        val terminalEntry = synchronized(lock) {
            entries[entry.displayKey]?.takeIf { it.token == entry.token }?.also { current ->
                current.state = LeaseState.Releasing
                current.command = command
            }
        } ?: run {
            opened.recovery?.release()
            return
        }
        val exit = callPresentation {
            terminalEntry.port?.windowPort()?.exit(terminalEntry.windowRequest(FullscreenMode.Windowed))
        }
        val effectiveState = when (exit) {
            is AppKitExclusiveWindowResult.Read -> exit.effectiveState
            is AppKitExclusiveWindowResult.Failed -> exit.effectiveState
            null -> terminalEntry.owner?.let { owner ->
                terminalEntry.port?.windowPort()?.readback(owner.windowId)
            }
        }
        terminalRelease(
            entry = terminalEntry,
            result = opened.cleanup,
            command = command,
            effectiveState = effectiveState,
            failure = opened.failure,
            recoveryOverride = opened.recovery,
        )
    }

    fun cancelReservation(
        port: AppKitExclusiveFullscreenPort,
        operationId: WindowOperationId,
    ): ExclusiveFullscreenCancellationOutcome {
        var idleObservation: AutoCloseable? = null
        val outcome = synchronized(lock) {
            val entry = entries.values.firstOrNull {
                it.owner?.sessionId == port.sessionId && it.operationId == operationId
            } ?: return@synchronized ExclusiveFullscreenCancellationOutcome.TooLate
            if (entry.state != LeaseState.Reserved) {
                return@synchronized ExclusiveFullscreenCancellationOutcome.TooLate
            }
            entry.state = LeaseState.Released
            entry.command = null
            entry.port = null
            entries.remove(entry.displayKey)
            idleObservation = detachIdleDisplayObservationLocked()
            ExclusiveFullscreenCancellationOutcome.CancelledBeforeCommit
        }
        idleObservation?.close()
        return outcome
    }

    fun release(port: AppKitExclusiveFullscreenPort, command: AppKitExclusiveBrokerCommand) {
        val entry = synchronized(lock) {
            entries[command.displayKey]?.takeIf {
                it.owner == Owner(port.sessionId, command.windowId) &&
                    it.modeKey == command.modeKey &&
                    it.state == LeaseState.Active
            }?.also {
                it.operationId = command.operationId
                it.command = command
                it.state = LeaseState.Releasing
            }
        }
        if (entry == null) {
            command.failed(exclusiveFailure("missing-lease"))
        } else if (!port.dispatch { releaseEntry(entry.displayKey, entry.token) }) {
            abandonToRecovery(entry.displayKey, entry.token)
            command.failed(exclusiveFailure("executor-closed"))
        }
    }

    fun releaseWindow(port: AppKitExclusiveFullscreenPort, windowId: WindowId) {
        var idleObservation: AutoCloseable? = null
        val action = synchronized(lock) {
            val entry = entries.values.firstOrNull { it.owner == Owner(port.sessionId, windowId) }
                ?: run {
                    idleObservation = detachIdleDisplayObservationLocked()
                    return@synchronized null
                }
            when (entry.state) {
                LeaseState.Reserved -> {
                    entry.state = LeaseState.Released
                    entry.command = null
                    entry.port = null
                    entries.remove(entry.displayKey)
                    idleObservation = detachIdleDisplayObservationLocked()
                    null
                }
                LeaseState.Committing -> {
                    entry.releaseRequested = true
                    entry.command = null
                    entry.displayKey to entry.token
                }
                LeaseState.Active -> {
                    entry.state = LeaseState.Releasing
                    entry.command = null
                    entry.displayKey to entry.token
                }
                LeaseState.Releasing,
                LeaseState.Released,
                LeaseState.Quarantined,
                -> null
            }
        }
        idleObservation?.close()
        if (action != null && !port.dispatch { releaseEntry(action.first, action.second) }) {
            abandonToRecovery(action.first, action.second)
        }
    }

    private fun releaseLateLease(
        displayKey: Long,
        token: Long,
        lease: AppKitExclusiveDisplayLease,
    ) {
        val result = runCatching(lease::release).getOrNull()
        val transition = synchronized(lock) {
            entries[displayKey]?.takeIf { it.token == token && it.state == LeaseState.Quarantined }?.let { entry ->
                if (result?.terminal is AppKitExclusiveDisplayTerminal.Released) {
                    entry.state = LeaseState.Released
                    entries.remove(displayKey)
                } else {
                    quarantineLocked(entry, lease)
                }
                BrokerTransition(
                    publication = availabilityPublicationLocked(),
                    idleObservation = detachIdleDisplayObservationLocked(),
                )
            }
        }
        publish(transition?.publication)
        transition?.idleObservation?.close()
    }

    private fun releaseEntry(displayKey: Long, token: Long) {
        val entry = synchronized(lock) {
            entries[displayKey]?.takeIf {
                it.token == token && (it.state == LeaseState.Releasing || it.releaseRequested && it.lease != null)
            }?.also { it.state = LeaseState.Releasing }
        } ?: return
        val result = runCatching { entry.lease?.release() }.getOrNull()
        val exit = callPresentation {
            entry.port?.windowPort()?.exit(entry.windowRequest(FullscreenMode.Windowed))
        }
        val effective = when (exit) {
            is AppKitExclusiveWindowResult.Read -> exit.effectiveState
            is AppKitExclusiveWindowResult.Failed -> exit.effectiveState
            null -> entry.owner?.let { entry.port?.windowPort()?.readback(it.windowId) }
        }
        val failure = when {
            exit is AppKitExclusiveWindowResult.Failed -> exit.failure
            result == null -> exclusiveFailure("release-exception")
            result.failures.isNotEmpty() -> result.failures.first()
            else -> null
        }
        terminalRelease(entry, result, entry.command, effective, failure)
    }

    private fun terminalRelease(
        entry: Entry,
        result: AppKitExclusiveDisplayReleaseResult?,
        command: AppKitExclusiveBrokerCommand?,
        effectiveState: WindowState?,
        failure: KadreFailure?,
        recoveryOverride: AppKitExclusiveDisplayLease? = null,
    ) {
        val released = result?.terminal is AppKitExclusiveDisplayTerminal.Released && recoveryOverride == null
        val outcome = synchronized(lock) {
            entries[entry.displayKey]?.takeIf { it.token == entry.token }?.let { current ->
                val targetPort = current.port
                val targetOwner = current.owner
                val lossDelivery = if (
                    current.displayLossPending && targetPort != null && targetOwner != null && effectiveState != null
                ) {
                    LossDelivery(
                        targetPort,
                        targetOwner.windowId,
                        current.lossOperationId,
                        effectiveState,
                    )
                } else {
                    null
                }
                val terminalCommand = command ?: current.command
                if (released) {
                    current.state = LeaseState.Released
                    entries.remove(entry.displayKey)
                } else {
                    quarantineLocked(current, recoveryOverride ?: current.lease ?: current.recovery)
                }
                current.command = null
                val publication = availabilityPublicationLocked()
                TerminalOutcome(
                    command = terminalCommand,
                    port = targetPort,
                    loss = lossDelivery,
                    publication = publication,
                    scheduleRecovery = !released && (closed || targetPort?.isOpen() == false),
                    idleObservation = detachIdleDisplayObservationLocked(),
                )
            }
        } ?: return
        publish(outcome.publication)
        val loss = outcome.loss
        if (loss != null) {
            loss.port.publishDisplayLoss(
                ExclusiveFullscreenDisplayLoss(
                    windowId = loss.windowId,
                    operationId = loss.operationId,
                    effectiveState = loss.effectiveState,
                ),
            )
        } else {
            outcome.command?.let { terminalCommand ->
                outcome.port.dispatchOrRun {
                    if (failure == null && released && effectiveState != null) {
                        terminalCommand.completed(effectiveState)
                    } else {
                        terminalCommand.failed(failure ?: quarantineFailure, effectiveState)
                    }
                }
            }
        }
        if (outcome.scheduleRecovery) scheduleRecovery(entry.displayKey, entry.token)
        outcome.idleObservation?.close()
    }

    fun closePort(port: AppKitExclusiveFullscreenPort) {
        val actions = synchronized(lock) {
            ports.remove(port)
            val releases = mutableListOf<Pair<Long, Long>>()
            entries.values.toList().forEach { entry ->
                if (entry.owner?.sessionId != port.sessionId) return@forEach
                when (entry.state) {
                    LeaseState.Reserved -> {
                        entry.state = LeaseState.Released
                        entries.remove(entry.displayKey)
                        entry.command = null
                        entry.port = null
                        entry.owner = null
                    }
                    LeaseState.Committing -> {
                        entry.releaseRequested = true
                        entry.command = null
                    }
                    LeaseState.Active -> {
                        entry.state = LeaseState.Releasing
                        entry.command = null
                        releases += entry.displayKey to entry.token
                    }
                    LeaseState.Releasing -> entry.command = null
                    LeaseState.Released,
                    LeaseState.Quarantined,
                    -> Unit
                }
            }
            PortCloseActions(
                releases = releases,
                quarantines = entries.values
                    .filter { it.state == LeaseState.Quarantined }
                    .map { it.displayKey to it.token },
                idleObservation = detachIdleDisplayObservationLocked(),
            )
        }
        actions.releases.forEach { (displayKey, token) ->
            if (!port.dispatch { releaseEntry(displayKey, token) }) {
                abandonToRecovery(displayKey, token)
            }
        }
        actions.quarantines.forEach { (displayKey, token) -> scheduleRecovery(displayKey, token) }
        actions.idleObservation?.close()
    }

    override fun close() {
        val targets = synchronized(lock) {
            if (closed) return
            closed = true
            val targetPorts = ports.toList().also { ports.clear() }
            val recoveries = mutableListOf<Pair<Long, Long>>()
            entries.values.toList().forEach { entry ->
                when (entry.state) {
                    LeaseState.Reserved -> {
                        entry.state = LeaseState.Released
                        entries.remove(entry.displayKey)
                        entry.command = null
                        entry.port = null
                        entry.owner = null
                    }
                    LeaseState.Committing,
                    LeaseState.Active,
                    LeaseState.Releasing,
                    -> {
                        quarantineLocked(entry, entry.lease)
                        recoveries += entry.displayKey to entry.token
                    }
                    LeaseState.Quarantined -> recoveries += entry.displayKey to entry.token
                    LeaseState.Released -> entries.remove(entry.displayKey)
                }
            }
            BrokerCloseActions(
                ports = targetPorts,
                observation = displayObservation.also { displayObservation = null },
                recoveries = recoveries,
            )
        }
        targets.ports.forEach(AppKitExclusiveFullscreenPort::closeFromBroker)
        targets.recoveries.forEach { (displayKey, token) -> scheduleRecovery(displayKey, token) }
        targets.observation?.close()
    }

    private fun quarantineLocked(entry: Entry, recovery: AppKitExclusiveDisplayLease?) {
        entry.state = LeaseState.Quarantined
        entry.recovery = recovery
        entry.lease = null
        entry.owner = null
        entry.operationId = null
        entry.command = null
        entry.port = null
        entry.releaseRequested = false
        entry.reconfigurationPending = false
        entry.displayLossPending = false
        entry.lossOperationId = null
        entry.terminalFailure = null
        entry.recoveryScheduled = false
    }

    private fun abandonToRecovery(displayKey: Long, token: Long) {
        val publication = synchronized(lock) {
            entries[displayKey]?.takeIf { it.token == token }?.let { entry ->
                quarantineLocked(entry, entry.lease ?: entry.recovery)
                availabilityPublicationLocked()
            }
        }
        publish(publication)
        scheduleRecovery(displayKey, token)
    }

    private fun scheduleRecovery(displayKey: Long, token: Long) {
        val recovery = synchronized(lock) {
            entries[displayKey]?.takeIf {
                it.token == token &&
                    it.state == LeaseState.Quarantined &&
                    !it.recoveryScheduled
            }?.recovery?.also {
                entries.getValue(displayKey).recoveryScheduled = true
            }
        } ?: return
        if (!recoveryExecutor.dispatch { recover(displayKey, token, recovery) }) {
            synchronized(lock) {
                entries[displayKey]?.takeIf { it.token == token }?.recoveryScheduled = false
            }
        }
    }

    private fun recover(
        displayKey: Long,
        token: Long,
        recovery: AppKitExclusiveDisplayLease,
    ) {
        val result = runCatching(recovery::release).getOrNull()
        val transition = synchronized(lock) {
            entries[displayKey]?.takeIf {
                it.token == token &&
                    it.state == LeaseState.Quarantined &&
                    it.recovery === recovery
            }?.let { entry ->
                entry.recoveryScheduled = false
                if (result?.terminal is AppKitExclusiveDisplayTerminal.Released) {
                    entry.state = LeaseState.Released
                    entry.recovery = null
                    entries.remove(displayKey)
                }
                BrokerTransition(
                    publication = availabilityPublicationLocked(),
                    idleObservation = detachIdleDisplayObservationLocked(),
                )
            }
        }
        publish(transition?.publication)
        transition?.idleObservation?.close()
    }

    private fun availabilityPublicationLocked(): AvailabilityPublication? {
        val availability = currentAvailabilityLocked()
        if (publishedAvailability == availability) return null
        publishedAvailability = availability
        return AvailabilityPublication(availability, ports.toList())
    }

    private fun publish(publication: AvailabilityPublication?) {
        publication?.ports?.forEach { it.publishAvailability(publication.availability) }
    }

    /** Detaches only after the last session port and the last lease/recovery entry are gone. */
    private fun detachIdleDisplayObservationLocked(): AutoCloseable? {
        if (ports.isNotEmpty() || entries.isNotEmpty()) return null
        val observation = displayObservation
        displayObservation = null
        displayObservationAttempted = false
        displayObservationFailure = null
        latestInventory = null
        publishedAvailability = null
        return observation
    }

    private fun AppKitExclusiveFullscreenPort?.dispatchOrRun(task: () -> Unit) {
        if (this == null || !dispatch(task)) task()
    }

    private fun currentAvailabilityLocked(): ExclusiveFullscreenAvailability = when {
        closed -> ExclusiveFullscreenAvailability.Unavailable(
            KadreFailure.TemporarilyUnavailable(retryable = true),
        )
        entries.values.any { it.state == LeaseState.Quarantined } ->
            ExclusiveFullscreenAvailability.Unavailable(quarantineFailure)
        bridge.availability is AppKitExclusiveBridgeAvailability.Available ->
            if (displayBroker == null || displayObservation != null && latestInventory != null) {
                ExclusiveFullscreenAvailability.Available
            } else {
                ExclusiveFullscreenAvailability.Unavailable(
                    displayObservationFailure ?: KadreFailure.TemporarilyUnavailable(retryable = true),
                )
            }
        else -> ExclusiveFullscreenAvailability.Unavailable(
            (bridge.availability as AppKitExclusiveBridgeAvailability.Unavailable).failure,
        )
    }

    private fun nextSessionIdLocked(): Long = nextSessionId.also {
        check(it != Long.MAX_VALUE) { "AppKit exclusive session identity exhausted" }
        nextSessionId += 1L
    }

    private fun nextTokenLocked(): Long = nextToken.also {
        check(it != Long.MAX_VALUE) { "AppKit exclusive token exhausted" }
        nextToken += 1L
    }

    private fun callPresentation(block: () -> AppKitExclusiveWindowResult?): AppKitExclusiveWindowResult? =
        try {
            block()
        } catch (_: Exception) {
            AppKitExclusiveWindowResult.Failed(exclusiveFailure("presentation-exception"), null)
        } catch (_: LinkageError) {
            AppKitExclusiveWindowResult.Failed(exclusiveFailure("presentation-exception"), null)
        }

    private fun callPreparation(block: () -> AppKitExclusiveWindowPreparation?): AppKitExclusiveWindowPreparation? =
        try {
            block()
        } catch (_: Exception) {
            AppKitExclusiveWindowPreparation.Failed(exclusiveFailure("presentation-exception"), null)
        } catch (_: LinkageError) {
            AppKitExclusiveWindowPreparation.Failed(exclusiveFailure("presentation-exception"), null)
        }

    private fun callOpen(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult =
        try {
            bridge.open(displayKey, modeKey)
        } catch (_: Exception) {
            AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(exclusiveFailure("capture-exception"))
        } catch (_: LinkageError) {
            AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(exclusiveFailure("capture-exception"))
        }

    private fun callReadback(lease: AppKitExclusiveDisplayLease): AppKitExclusiveDisplayReadback? =
        try {
            lease.readback()
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }

    private fun exclusiveFailure(code: String): KadreFailure.PlatformFailure =
        KadreFailure.PlatformFailure(org.graphiks.kadre.diagnostics.KadrePlatform.AppKit, "exclusive-fullscreen", code)

    private fun ensureDisplayObservation() {
        if (bridge.availability !is AppKitExclusiveBridgeAvailability.Available) return
        val source = displayBroker ?: return
        val publication = synchronized(lock) {
            if (displayObservationAttempted || closed) return
            displayObservationAttempted = true
            when (val retained = source.retainExclusiveObservation(::acceptDisplayReconfiguration)) {
                is KadreResult.Failure -> {
                    displayObservationAttempted = false
                    displayObservationFailure = retained.reason
                }
                is KadreResult.Success -> {
                    displayObservation = retained.value
                    when (val initial = source.requestExclusiveSnapshot()) {
                        is KadreResult.Failure -> displayObservationFailure = initial.reason
                        is KadreResult.Success -> {
                            latestInventory = initial.value
                            displayObservationFailure = null
                        }
                    }
                }
            }
            availabilityPublicationLocked()
        }
        publish(publication)
    }

    private fun acceptDisplayReconfiguration(result: KadreResult<DisplayPortSnapshot>) {
        val actions = synchronized(lock) {
            val previousAvailability = publishedAvailability ?: currentAvailabilityLocked()
            when (result) {
                is KadreResult.Failure -> {
                    latestInventory = null
                    displayObservationFailure = result.reason
                }
                is KadreResult.Success -> {
                    latestInventory = result.value
                    displayObservationFailure = null
                }
            }
            val nextAvailability = currentAvailabilityLocked()
            publishedAvailability = nextAvailability
            val availabilityTargets = if (nextAvailability != previousAvailability) ports.toList() else emptyList()
            val reserved = mutableListOf<ReservedTerminal>()
            val active = mutableListOf<Pair<Long, Long>>()
            val quarantines = mutableListOf<Pair<Long, Long>>()
            entries.values.toList().forEach { entry ->
                when (entry.state) {
                    LeaseState.Committing,
                    LeaseState.Releasing,
                    -> {
                        entry.reconfigurationPending = true
                        if (result is KadreResult.Failure) {
                            entry.displayLossPending = true
                            entry.lossOperationId = entry.operationId.takeIf { entry.command != null }
                            entry.terminalFailure = result.reason
                        }
                    }
                    LeaseState.Active -> if (!(result is KadreResult.Success && result.value.holds(entry))) {
                        entry.state = LeaseState.Releasing
                        entry.displayLossPending = true
                        entry.lossOperationId = null
                        entry.terminalFailure = (result as? KadreResult.Failure)?.reason
                        active += entry.displayKey to entry.token
                    }
                    LeaseState.Reserved -> if (result is KadreResult.Failure) {
                        entry.state = LeaseState.Released
                        entries.remove(entry.displayKey)
                        val command = entry.command
                        val port = entry.port
                        entry.command = null
                        entry.port = null
                        entry.owner = null
                        if (command != null && port != null) {
                            reserved += ReservedTerminal(port, command, result.reason)
                        }
                    }
                    LeaseState.Quarantined -> quarantines += entry.displayKey to entry.token
                    LeaseState.Released -> Unit
                }
            }
            ReconfigurationActions(
                availability = nextAvailability,
                availabilityTargets = availabilityTargets,
                reserved = reserved,
                active = active,
                quarantines = quarantines,
                idleObservation = detachIdleDisplayObservationLocked(),
            )
        }
        actions.availabilityTargets.forEach { it.publishAvailability(actions.availability) }
        actions.reserved.forEach { terminal ->
            terminal.port.dispatch { terminal.command.failed(terminal.failure) }
        }
        actions.active.forEach { (displayKey, token) -> scheduleDisplayLoss(displayKey, token) }
        actions.quarantines.forEach { (displayKey, token) -> scheduleRecovery(displayKey, token) }
        actions.idleObservation?.close()
    }

    private fun reconcileCommittedEntry(entry: Entry): Boolean {
        val result = displayBroker?.requestExclusiveSnapshot() ?: return true
        synchronized(lock) {
            entries[entry.displayKey]?.takeIf { it.token == entry.token }?.reconfigurationPending = false
        }
        acceptDisplayReconfiguration(result)
        return synchronized(lock) {
            entries[entry.displayKey]?.let { current ->
                current.token == entry.token && current.state == LeaseState.Active
            } == true
        }
    }

    private fun scheduleDisplayLoss(displayKey: Long, token: Long) {
        val dispatch = synchronized(lock) {
            entries[displayKey]?.takeIf { it.token == token && it.state == LeaseState.Releasing }?.let { entry ->
                entry.port
            }
        }
        dispatch?.dispatch { releaseEntry(displayKey, token) }
    }

    private fun DisplayPortSnapshot.holds(entry: Entry): Boolean = displays.any { display ->
        display.key == entry.displayKey && display.currentModeKey == entry.modeKey
    }

    private data class Owner(val sessionId: Long, val windowId: WindowId)

    private data class ReservedTerminal(
        val port: AppKitExclusiveFullscreenPort,
        val command: AppKitExclusiveBrokerCommand,
        val failure: KadreFailure,
    )

    private data class AvailabilityPublication(
        val availability: ExclusiveFullscreenAvailability,
        val ports: List<AppKitExclusiveFullscreenPort>,
    )

    private data class BrokerTransition(
        val publication: AvailabilityPublication?,
        val idleObservation: AutoCloseable?,
    )

    private data class CommandDelivery(
        val port: AppKitExclusiveFullscreenPort?,
        val command: AppKitExclusiveBrokerCommand?,
        val idleObservation: AutoCloseable?,
    )

    private data class TerminalOutcome(
        val command: AppKitExclusiveBrokerCommand?,
        val port: AppKitExclusiveFullscreenPort?,
        val loss: LossDelivery?,
        val publication: AvailabilityPublication?,
        val scheduleRecovery: Boolean,
        val idleObservation: AutoCloseable?,
    )

    private data class PortCloseActions(
        val releases: List<Pair<Long, Long>>,
        val quarantines: List<Pair<Long, Long>>,
        val idleObservation: AutoCloseable?,
    )

    private data class BrokerCloseActions(
        val ports: List<AppKitExclusiveFullscreenPort>,
        val observation: AutoCloseable?,
        val recoveries: List<Pair<Long, Long>>,
    )

    private data class ReconfigurationActions(
        val availability: ExclusiveFullscreenAvailability,
        val availabilityTargets: List<AppKitExclusiveFullscreenPort>,
        val reserved: List<ReservedTerminal>,
        val active: List<Pair<Long, Long>>,
        val quarantines: List<Pair<Long, Long>>,
        val idleObservation: AutoCloseable?,
    )

    private data class LossDelivery(
        val port: AppKitExclusiveFullscreenPort,
        val windowId: WindowId,
        val operationId: WindowOperationId?,
        val effectiveState: WindowState,
    )

    private data class Entry(
        val displayKey: Long,
        val modeKey: Long,
        var owner: Owner?,
        val token: Long,
        var operationId: WindowOperationId?,
        var command: AppKitExclusiveBrokerCommand?,
        var port: AppKitExclusiveFullscreenPort?,
        var state: LeaseState,
        var lease: AppKitExclusiveDisplayLease? = null,
        var recovery: AppKitExclusiveDisplayLease? = null,
        var releaseRequested: Boolean = false,
        var reconfigurationPending: Boolean = false,
        var displayLossPending: Boolean = false,
        var lossOperationId: WindowOperationId? = null,
        var terminalFailure: KadreFailure? = null,
        var recoveryScheduled: Boolean = false,
    ) {
        fun windowRequest(fullscreen: FullscreenMode, displayId: Int = displayKey.toInt()): AppKitExclusiveWindowRequest =
            AppKitExclusiveWindowRequest(displayKey, modeKey, token, checkNotNull(owner).windowId, fullscreen, displayId)
    }

    private enum class LeaseState {
        Reserved,
        Committing,
        Active,
        Releasing,
        Released,
        Quarantined,
    }
}

internal class AppKitExclusiveFullscreenPort internal constructor(
    private val broker: AppKitExclusiveDisplayBroker,
    internal val sessionId: Long,
    private val executor: AppKitExclusiveExecutor,
    private val windowPort: AppKitExclusiveWindowPort,
    initialAvailability: ExclusiveFullscreenAvailability,
) : ExclusiveFullscreenPort, AutoCloseable {
    private val lock = Any()
    private var open = true
    private var currentAvailability = initialAvailability
    private var availabilityObserver: ((ExclusiveFullscreenAvailability) -> Unit)? = null
    private var displayLossObserver: ((ExclusiveFullscreenDisplayLoss) -> Unit)? = null

    override val availability: ExclusiveFullscreenAvailability
        get() = synchronized(lock) { currentAvailability }

    override fun installAvailabilityObserver(
        observer: (ExclusiveFullscreenAvailability) -> Unit,
    ): AutoCloseable = synchronized(lock) {
        check(open) { "AppKit exclusive fullscreen port is closed" }
        check(availabilityObserver == null) { "exclusive availability observer is already installed" }
        availabilityObserver = observer
        AutoCloseable { synchronized(lock) { if (availabilityObserver === observer) availabilityObserver = null } }
    }

    override fun installDisplayLossObserver(
        observer: (ExclusiveFullscreenDisplayLoss) -> Unit,
    ): AutoCloseable = synchronized(lock) {
        check(open) { "AppKit exclusive fullscreen port is closed" }
        check(displayLossObserver == null) { "exclusive display-loss observer is already installed" }
        displayLossObserver = observer
        AutoCloseable { synchronized(lock) { if (displayLossObserver === observer) displayLossObserver = null } }
    }

    override fun reserve(command: ExclusiveFullscreenCommand): KadreResult<Unit> =
        reserve(RuntimeExclusiveBrokerCommand(command))

    internal fun reserve(command: AppKitExclusiveBrokerCommand): KadreResult<Unit> = broker.reserve(this, command)

    override fun release(command: ExclusiveFullscreenCommand) {
        broker.release(this, RuntimeExclusiveBrokerCommand(command))
    }

    internal fun release(command: AppKitExclusiveBrokerCommand) {
        broker.release(this, command)
    }

    override fun cancelReservation(
        operationId: WindowOperationId,
    ): ExclusiveFullscreenCancellationOutcome = broker.cancelReservation(this, operationId)

    override fun releaseWindow(windowId: WindowId) {
        broker.releaseWindow(this, windowId)
    }

    internal fun dispatch(task: () -> Unit): Boolean = executor.dispatch(task)

    internal fun windowPort(): AppKitExclusiveWindowPort = windowPort

    internal fun publishAvailability(value: ExclusiveFullscreenAvailability) {
        dispatch {
            val observer = synchronized(lock) {
                if (!open) return@dispatch
                currentAvailability = value
                availabilityObserver
            }
            observer?.invoke(value)
        }
    }

    internal fun publishDisplayLoss(loss: ExclusiveFullscreenDisplayLoss) {
        dispatch {
            val observer = synchronized(lock) {
                if (!open) return@dispatch
                displayLossObserver
            }
            observer?.invoke(loss)
        }
    }

    internal fun isOpen(): Boolean = synchronized(lock) { open }

    internal fun closeFromBroker() {
        synchronized(lock) {
            open = false
            availabilityObserver = null
            displayLossObserver = null
        }
    }

    override fun close() {
        val notify = synchronized(lock) {
            if (!open) false else {
                open = false
                availabilityObserver = null
                displayLossObserver = null
                true
            }
        }
        if (notify) broker.closePort(this)
    }
}

private class RuntimeExclusiveBrokerCommand(
    private val command: ExclusiveFullscreenCommand,
) : AppKitExclusiveBrokerCommand {
    override val windowId: WindowId get() = command.windowId
    override val operationId: WindowOperationId get() = command.operationId
    override val displayKey: Long get() = command.target.displayKey
    override val modeKey: Long get() = command.target.modeKey
    override val requestedFullscreen: FullscreenMode get() = command.requestedFullscreen

    override fun captureCommitted(): Boolean = command.captureCommitted()

    override fun completed(effectiveState: WindowState) = command.completed(effectiveState)

    override fun failed(failure: KadreFailure, effectiveState: WindowState?) =
        command.failed(failure, effectiveState)
}
