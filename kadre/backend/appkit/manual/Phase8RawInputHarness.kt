package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.input.RawInputState
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * External manual-observation tool for the AppKit global raw-input bridge.
 *
 * The tool records only public capability, access state and device-count deltas. It never records
 * key values, screen coordinates, native pointers or any other potentially sensitive input.
 */
@OptIn(DelicateKadreApi::class)
public fun main(args: Array<String>) {
    val options = Phase8RawInputHarnessOptions.parse(args)
    val recorder = Phase8RawInputHarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase8-raw-input-harness-input").start {
        generateSequence(::readlnOrNull).forEach { commands.trySend(it) }
        commands.trySend("finish")
        commands.close()
    }

    try {
        val outcome = runKadreApplication(
            options = DesktopHostOptions.Standalone(
                backend = DesktopBackend.AppKit,
                stopWhenLastWindowClosed = false,
            ),
            application = KadreApplication {
                val opened = when (val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 8 raw input"))) {
                    is KadreResult.Failure -> error("window request failed: ${request.reason}")
                    is KadreResult.Success -> request.value.await()
                }
                val window = when (opened) {
                    is WindowRequestOutcome.OpenedHere -> opened.window
                    else -> error("window did not open: $opened")
                }
                val input = window.surface.input
                val accesses = linkedMapOf<Int, RawInputAccess>()
                val accessCollectors = linkedMapOf<Int, List<kotlinx.coroutines.Job>>()
                val ordinaryEvents = AtomicInteger()
                var nextAccessId = 1
                var terminalObserved = false

                recorder.metadata(options)
                recorder.line("INPUT_SNAPSHOT\tinitial\t${input.state.value}")
                printPhase8RawInputHelp(recorder)

                val inputStateCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    input.state.collect { state ->
                        recorder.line("INPUT_SNAPSHOT\tupdate\t$state")
                    }
                }
                val ordinaryInputCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    input.events.collect { event ->
                        ordinaryEvents.incrementAndGet()
                        recorder.line("ORDINARY_INPUT_EVENT\t${event::class.simpleName}")
                    }
                }

                suspend fun requestAccess() {
                    when (val requested = input.requestRawInput()) {
                        is KadreResult.Failure -> recorder.line("REQUEST_RAW_INPUT\tfailure\t${requested.reason}")
                        is KadreResult.Success -> {
                            val id = nextAccessId++
                            val access = requested.value
                            accesses[id] = access
                            recorder.line("REQUEST_RAW_INPUT\tsuccess\taccess=$id\tstate=${access.state.value}")
                            accessCollectors[id] = listOf(
                                launch(start = CoroutineStart.UNDISPATCHED) {
                                    access.state.collect { state ->
                                        recorder.line("RAW_ACCESS_STATE\taccess=$id\t$state")
                                    }
                                },
                                launch(start = CoroutineStart.UNDISPATCHED) {
                                    access.events.collect { event ->
                                        recorder.line(
                                            "RAW_INPUT_EVENT\taccess=$id\tdeltaX=${event.deltaX}" +
                                                "\tdeltaY=${event.deltaY}\tunit=${event.unit}" +
                                                "\tdeviceId=${event.deviceId}",
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                fun closeAccess(id: Int) {
                    val access = accesses.remove(id)
                    if (access == null) {
                        recorder.line("CLOSE_RAW_ACCESS\taccess=$id\tunknown")
                        return
                    }
                    access.close()
                    accessCollectors.remove(id).orEmpty().forEach { it.cancel() }
                    recorder.line("CLOSE_RAW_ACCESS\taccess=$id\tstate=${access.state.value}")
                }

                suspend fun closeAndObserveTerminal() {
                    if (terminalObserved) return
                    recorder.line("COMMAND\tclose\t${window.close()}")
                    withTimeout(5.seconds) {
                        window.surface.state.first { it.attachment == SurfaceAttachmentState.Detached }
                    }
                    val closedAccesses = accesses.all { (_, access) -> access.state.value == RawInputState.Closed }
                    val ordinaryEventCount = ordinaryEvents.get()
                    delay(250.milliseconds)
                    val noLateOrdinaryEvent = ordinaryEvents.get() == ordinaryEventCount
                    recorder.line(
                        "TERMINAL_STABILITY\tallRawAccessesClosed=$closedAccesses" +
                            "\tnoLateOrdinaryEvent=$noLateOrdinaryEvent\tobservationMillis=250",
                    )
                    check(closedAccesses) { "a raw-input access survived its surface teardown" }
                    check(noLateOrdinaryEvent) { "ordinary input arrived after terminal detachment" }
                    terminalObserved = true
                }

                for (line in commands) {
                    val command = line.trim()
                    when {
                        command.isEmpty() -> Unit
                        command == "help" -> printPhase8RawInputHelp(recorder)
                        command == "snapshot" -> recorder.line(
                            "COMMAND\tsnapshot\tinput=${input.state.value}\taccesses=" +
                                accesses.mapValues { (_, access) -> access.state.value },
                        )
                        command == "request" -> requestAccess()
                        command.startsWith("close-access ") -> closeAccess(command.substringAfter(' ').toIntOrNull() ?: -1)
                        command.startsWith("result ") -> {
                            val scenario = command.split(' ', limit = 3).getOrNull(1)
                            if (scenario == "M7" && !terminalObserved) {
                                recorder.line("COMMAND\tresult-rejected\tM7 requires terminal observation before recording")
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
                accesses.keys.toList().forEach(::closeAccess)
                closeAndObserveTerminal()
                accessCollectors.values.flatten().forEach { it.cancel() }
                inputStateCollector.cancel()
                ordinaryInputCollector.cancel()
                requestStop()
            },
        )
        recorder.line("SESSION_OUTCOME\t$outcome")
    } finally {
        recorder.close()
    }
}

private fun printPhase8RawInputHelp(recorder: Phase8RawInputHarnessRecorder) {
    recorder.line("HELP\tsnapshot | request | close-access <id> | result M1..M7 pass|fail|not-applicable note | close | finish")
}

private data class Phase8RawInputHarnessOptions(
    val recordPath: Path,
    val buildId: String,
) {
    companion object {
        fun parse(args: Array<String>): Phase8RawInputHarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase8RawInputHarnessOptions(
                recordPath = Path.of(value("--record=") ?: "kadre/backend/appkit/build/manual/phase-8-raw-input.tsv"),
                buildId = value("--build-id=") ?: phase8RawInputCommandOutput("git", "rev-parse", "HEAD").ifBlank {
                    "unknown"
                },
            )
        }
    }
}

private class Phase8RawInputHarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase8RawInputHarnessOptions) {
        line(
            listOf(
                "RUN_METADATA",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${phase8RawInputCommandOutput("sysctl", "-n", "hw.model")}",
                "displays=${phase8RawInputCommandOutput("system_profiler", "SPDisplaysDataType", "-detailLevel", "mini")}",
                "buildId=${options.buildId}",
            ).joinToString("\t"),
        )
    }

    fun scenario(command: String) {
        val fields = command.split(' ', limit = 4)
        val id = fields.getOrNull(1).orEmpty()
        val status = fields.getOrNull(2).orEmpty()
        val note = fields.getOrNull(3).orEmpty()
        require(id in (1..7).map { "M$it" }) { "scenario must be M1 through M7" }
        require(status in setOf("pass", "fail", "not-applicable")) {
            "status must be pass, fail or not-applicable"
        }
        require(note.isNotBlank()) { "scenario result requires a short note" }
        line("SCENARIO\t$id\t$status\t${phase8RawInputSanitise(note)}")
    }

    fun line(value: String) {
        val safe = phase8RawInputSanitise(value)
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

private fun phase8RawInputCommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        "timeout"
    } else {
        process.inputStream.bufferedReader().readText().trim()
    }
}.getOrElse { "unavailable" }

private fun phase8RawInputSanitise(value: String): String = value.replace('\n', ' ').replace('\r', ' ')
