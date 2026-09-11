package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLifecycle
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.HostSignal
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.appkit.AppKitLifecycleSignal
import org.graphiks.kadre.internal.appkit.AppKitProcessBroker
import org.graphiks.kadre.internal.appkit.AppKitRuntimeHost
import org.graphiks.kadre.internal.appkit.KffiAppKitMemoryPressureNative
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.policy.KadrePolicies
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * External manual-observation tool for the process-wide AppKit memory-pressure source.
 *
 * It never allocates memory to manufacture an event. An operator must induce a controlled
 * system load separately, then classify the observed source events in the record.
 */
public fun main(args: Array<String>): Unit = runBlocking {
    val options = Phase9MemoryPressureHarnessOptions.parse(args)
    val recorder = Phase9MemoryPressureHarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase9-memory-pressure-harness-input").start {
        generateSequence(::readlnOrNull).forEach { commands.trySend(it) }
        commands.trySend("finish")
        commands.close()
    }

    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val broker = AppKitProcessBroker(memoryPressureNative = KffiAppKitMemoryPressureNative)
    val sessions = linkedMapOf<Int, Phase9MemoryPressureSession>()
    var terminalObserved = false
    var sourceTerminationRequested = false
    var sessionOneCloseSnapshot: Phase9MemoryPressureCounts? = null

    suspend fun closeSession(index: Int) {
        val entry = sessions[index]
        if (entry == null) {
            recorder.line("COMMAND\tclose-session\tindex=$index\tunknown")
            return
        }
        if (entry.closed) {
            recorder.line("COMMAND\tclose-session\tindex=$index\talready-closed")
            return
        }
        entry.session.close()
        entry.session.awaitTermination()
        entry.registration.close()
        entry.closed = true
        if (index == 1) {
            sessionOneCloseSnapshot = Phase9MemoryPressureCounts(
                sessionOne = entry.memoryEventCount.get(),
                sessionTwo = sessions[2]?.memoryEventCount?.get() ?: 0,
            )
        }
        recorder.line("COMMAND\tclose-session\tindex=$index\tclosed")
    }

    fun status(label: String) {
        recorder.line("CAPABILITY\t$label\t${broker.memoryPressureAvailability()}")
        sessions.forEach { (index, entry) ->
            recorder.line(
                "SESSION\tindex=$index\tclosed=${entry.closed}\tattachment=" +
                    entry.lifecycle.state.value.attachment +
                    "\tmemoryEvents=${entry.memoryEventCount.get()}",
            )
        }
    }

    suspend fun closeAndObserveTerminal() {
        if (terminalObserved) return
        for (index in sessions.keys.toList()) {
            closeSession(index)
        }
        broker.accept(AppKitLifecycleSignal.HostTerminated)
        sourceTerminationRequested = true
        val eventCountAtTermination = sessions.values.sumOf { it.memoryEventCount.get() }
        delay(250.milliseconds)
        val noLateMemoryPressure = sessions.values.sumOf { it.memoryEventCount.get() } == eventCountAtTermination
        recorder.line(
            "TERMINAL_STABILITY\tnoLateMemoryPressure=$noLateMemoryPressure" +
                "\tobservationMillis=250",
        )
        check(noLateMemoryPressure) { "memory pressure arrived after the process source was terminated" }
        recorder.line("SOURCE_TERMINATED\trequested")
        terminalObserved = true
    }

    try {
        val availability = broker.memoryPressureAvailability()
        recorder.metadata(options)
        recorder.line("CAPABILITY\tinitial\t$availability")
        (1..SESSION_COUNT).forEach { index ->
            sessions[index] = createPhase9MemoryPressureSession(
                index = index,
                broker = broker,
                availability = availability,
                parentScope = parentScope,
                recorder = recorder,
            )
        }
        printPhase9MemoryPressureHelp(recorder)

        fun rejectedPassReason(scenario: String): String? = when (scenario) {
            "M1" -> if (availability == FeatureAvailability.Available) null else {
                "M1 requires an Available memory-pressure capability"
            }
            "M2" -> if (sessions.values.all { MemoryPressureLevel.Moderate in it.observedLevels }) null else {
                "M2 requires Moderate observed by both sessions"
            }
            "M3" -> if (sessions.values.all { MemoryPressureLevel.Critical in it.observedLevels }) null else {
                "M3 requires Critical observed by both sessions"
            }
            "M4" -> when (val snapshot = sessionOneCloseSnapshot) {
                null -> "M4 requires session 1 to be closed after observed pressure"
                else -> when {
                snapshot.sessionOne == 0 || snapshot.sessionTwo == 0 -> {
                    "M4 requires pressure observed by both sessions before session 1 closes"
                }
                !checkNotNull(sessions[1]).closed -> "M4 requires session 1 to be closed"
                checkNotNull(sessions[1]).memoryEventCount.get() != snapshot.sessionOne -> {
                    "M4 rejects late pressure delivered to closed session 1"
                }
                checkNotNull(sessions[2]).memoryEventCount.get() <= snapshot.sessionTwo -> {
                    "M4 requires new pressure observed by session 2 after session 1 closed"
                }
                else -> null
            }
            }
            "M5" -> if (terminalObserved) null else "M5 requires terminal observation before recording"
            else -> null
        }

        for (line in commands) {
            val command = line.trim()
            when {
                command.isEmpty() -> Unit
                command == "help" -> printPhase9MemoryPressureHelp(recorder)
                command == "status" -> status("current")
                command.startsWith("close-session ") -> {
                    closeSession(command.substringAfter(' ').toIntOrNull() ?: -1)
                }
                command.startsWith("result ") -> {
                    val fields = command.split(' ', limit = 4)
                    val scenario = fields.getOrNull(1).orEmpty()
                    val status = fields.getOrNull(2).orEmpty()
                    if (options.automated && status == "pass") {
                        recorder.line("COMMAND\tresult-rejected\tautomated runs cannot record pass")
                    } else if (scenario == "M5" && !terminalObserved) {
                        recorder.line("COMMAND\tresult-rejected\tM5 requires terminal observation before recording")
                    } else if (status == "pass") {
                        rejectedPassReason(scenario)?.let { reason ->
                            recorder.line("COMMAND\tresult-rejected\t$reason")
                        } ?: recorder.scenario(command)
                    } else {
                        recorder.scenario(command)
                    }
                }
                command == "close" -> closeAndObserveTerminal()
                command == "finish" -> {
                    recorder.line("COMMAND\tfinish")
                    closeAndObserveTerminal()
                    break
                }
                else -> recorder.line("COMMAND\tunknown\t$command")
            }
        }
        closeAndObserveTerminal()
    } finally {
        sessions.values.forEach { entry ->
            if (!entry.closed) runCatching { entry.session.close() }
            runCatching { entry.registration.close() }
        }
        if (!sourceTerminationRequested) {
            runCatching {
                broker.accept(AppKitLifecycleSignal.HostTerminated)
                sourceTerminationRequested = true
            }
        }
        sessions.values.forEach { entry -> entry.collector.cancel() }
        parentScope.cancel()
        recorder.close()
    }
}

private suspend fun createPhase9MemoryPressureSession(
    index: Int,
    broker: AppKitProcessBroker,
    availability: FeatureAvailability,
    parentScope: CoroutineScope,
    recorder: Phase9MemoryPressureHarnessRecorder,
): Phase9MemoryPressureSession {
    val registration = checkNotNull(
        broker.createEmbeddedHost { initial ->
            AppKitRuntimeHost(
                RuntimeHostController(
                    platform = KadrePlatform.AppKit,
                    initialLifecycleState = initial,
                    initialLifecycleCapabilities = LifecycleCapabilities(availability),
                ),
            )
        },
    ) { "AppKit process broker rejected manual session $index" }
    val lifecycle = CompletableDeferred<KadreLifecycle>()
    val session = registration.host.controller.attach(
        parentScope = parentScope,
        applicationFactory = KadreApplicationFactory {
            KadreApplication {
                lifecycle.complete(this.lifecycle)
                awaitCancellation()
            }
        },
        policy = KadrePolicies.Default,
    ).phase9MemoryPressureSession()
    val observedLifecycle = lifecycle.await()
    val memoryEventCount = AtomicInteger()
    val observedLevels = ConcurrentHashMap.newKeySet<MemoryPressureLevel>()
    val collector = parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
        observedLifecycle.signals.filterIsInstance<HostSignal.MemoryPressure>().collect { signal ->
            memoryEventCount.incrementAndGet()
            observedLevels += signal.level
            recorder.line("MEMORY_PRESSURE\tindex=$index\tlevel=${signal.level}\tstamp=${signal.stamp}")
        }
    }
    return Phase9MemoryPressureSession(
        registration = registration,
        session = session,
        lifecycle = observedLifecycle,
        collector = collector,
        memoryEventCount = memoryEventCount,
        observedLevels = observedLevels,
    )
}

private fun KadreResult<KadreSession>.phase9MemoryPressureSession(): KadreSession =
    (this as? KadreResult.Success)?.value ?: error("manual session attachment failed: $this")

private fun printPhase9MemoryPressureHelp(recorder: Phase9MemoryPressureHarnessRecorder) {
    recorder.line("HELP\tstatus | close-session <1|2> | result M1..M5 pass|fail|not-applicable note | close | finish")
}

private data class Phase9MemoryPressureSession(
    val registration: AppKitProcessBroker.EmbeddedRegistration<AppKitRuntimeHost>,
    val session: KadreSession,
    val lifecycle: KadreLifecycle,
    val collector: kotlinx.coroutines.Job,
    val memoryEventCount: AtomicInteger,
    val observedLevels: Set<MemoryPressureLevel>,
    var closed: Boolean = false,
)

private data class Phase9MemoryPressureCounts(
    val sessionOne: Int,
    val sessionTwo: Int,
)

private data class Phase9MemoryPressureHarnessOptions(
    val recordPath: Path,
    val buildId: String,
    val automated: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): Phase9MemoryPressureHarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase9MemoryPressureHarnessOptions(
                recordPath = Path.of(
                    value("--record=") ?: "kadre/backend/appkit/build/manual/phase-9-memory-pressure.tsv",
                ),
                buildId = value("--build-id=") ?: phase9MemoryPressureCommandOutput("git", "rev-parse", "HEAD").ifBlank {
                    "unknown"
                },
                automated = "--automated" in args,
            )
        }
    }
}

private class Phase9MemoryPressureHarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase9MemoryPressureHarnessOptions) {
        line(
            listOf(
                "RUN_METADATA",
                "schemaVersion=1",
                "executionMode=${if (options.automated) "automated" else "manual"}",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${phase9MemoryPressureCommandOutput("sysctl", "-n", "hw.model")}",
                "sessionCount=$SESSION_COUNT",
                "buildId=${options.buildId}",
            ).joinToString("\t"),
        )
    }

    fun scenario(command: String) {
        val fields = command.split(' ', limit = 4)
        val id = fields.getOrNull(1).orEmpty()
        val status = fields.getOrNull(2).orEmpty()
        val note = fields.getOrNull(3).orEmpty()
        require(id in (1..5).map { "M$it" }) { "scenario must be M1 through M5" }
        require(status in setOf("pass", "fail", "not-applicable")) {
            "status must be pass, fail or not-applicable"
        }
        require(note.isNotBlank()) { "scenario result requires a short note" }
        line("SCENARIO\t$id\t$status\t${phase9MemoryPressureSanitise(note)}")
    }

    fun line(value: String) {
        val safe = phase9MemoryPressureSanitise(value)
        synchronized(lock) {
            println(safe)
            writer.appendLine(safe)
            writer.flush()
        }
    }

    override fun close() {
        synchronized(lock) { writer.close() }
    }
}

private fun phase9MemoryPressureCommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        "timeout"
    } else {
        process.inputStream.bufferedReader().readText().trim()
    }
}.getOrElse { "unavailable" }

private fun phase9MemoryPressureSanitise(value: String): String = value.replace('\n', ' ').replace('\r', ' ')

private const val SESSION_COUNT: Int = 2
