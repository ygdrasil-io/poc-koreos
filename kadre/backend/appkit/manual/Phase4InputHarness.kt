package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.input.InputEvent
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * External manual-observation tool for Phase 4 input.
 *
 * It records Kadre's public input state and events.  It deliberately has no command that
 * synthesises input: responder routing, trackpad fractions and momentum require a real peripheral.
 */
public fun main(args: Array<String>) {
    val options = Phase4HarnessOptions.parse(args)
    val recorder = Phase4HarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase4-harness-input").start {
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
                val opened = when (val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 4 input"))) {
                    is KadreResult.Failure -> error("window request failed: ${request.reason}")
                    is KadreResult.Success -> request.value.await()
                }
                val window = when (opened) {
                    is WindowRequestOutcome.OpenedHere -> opened.window
                    else -> error("window did not open: $opened")
                }
                val input = window.surface.input
                recorder.metadata(options)
                recorder.line("SNAPSHOT\tinitial\t${input.state.value}")
                recorder.line(
                    "INPUT_CAPABILITIES\tkeyboard=${input.state.value.capabilities.keyboard}" +
                        "\tpointer=${input.state.value.capabilities.pointer}" +
                        "\ttouch=${input.state.value.capabilities.touch}" +
                        "\tgestures=${input.state.value.capabilities.gestures}" +
                        "\tdragAndDrop=${input.state.value.capabilities.dragAndDrop}" +
                        "\ttextInput=${phase4Capability(input.state.value.capabilities.textInput)}" +
                        "\trawInput=${phase4Capability(input.state.value.capabilities.rawInput)}",
                )
                printPhase4Help(recorder)

                val latestRevision = AtomicLong(input.state.value.revision.value)
                val observedEventCount = AtomicInteger()
                val stateCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    input.state.collect { state ->
                        latestRevision.set(state.revision.value)
                        recorder.line("SNAPSHOT\tupdate\t$state")
                    }
                }
                val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    input.events.collect { event ->
                        observedEventCount.incrementAndGet()
                        recorder.line(
                            "EVENT\t${event::class.simpleName}" +
                                "\tstateRevisionVisible=${input.state.value.revision.value >= event.stateRevision.value}" +
                                "\tcurrentRevision=${input.state.value.revision.value}" +
                                "\teventRevision=${event.stateRevision.value}\t$event",
                        )
                    }
                }

                var terminalObserved = false
                suspend fun closeAndObserveTerminal() {
                    if (terminalObserved) return
                    recorder.line("COMMAND\tclose\t${window.close()}")
                    withTimeout(5.seconds) {
                        window.surface.state.first { it.attachment == SurfaceAttachmentState.Detached }
                    }
                    val terminalRevision = latestRevision.get()
                    val terminalEventCount = observedEventCount.get()
                    delay(250.milliseconds)
                    val noLateRevision = latestRevision.get() == terminalRevision
                    val noLateEvent = observedEventCount.get() == terminalEventCount
                    recorder.line(
                        "TERMINAL_STABILITY\tnoLateRevision=$noLateRevision\tnoLateEvent=$noLateEvent" +
                            "\tobservationMillis=250",
                    )
                    check(noLateRevision) { "input revision changed after terminal detachment" }
                    check(noLateEvent) { "input event arrived after terminal detachment" }
                    terminalObserved = true
                }

                for (line in commands) {
                    val command = line.trim()
                    when {
                        command.isEmpty() -> Unit
                        command == "help" -> printPhase4Help(recorder)
                        command == "snapshot" -> recorder.line("COMMAND\tsnapshot\t${input.state.value}")
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
                closeAndObserveTerminal()
                stateCollector.cancel()
                eventCollector.cancel()
                requestStop()
            },
        )
        recorder.line("SESSION_OUTCOME\t$outcome")
    } finally {
        recorder.close()
    }
}

private fun printPhase4Help(recorder: Phase4HarnessRecorder) {
    recorder.line("HELP\tsnapshot | result M1..M8 pass|fail|not-applicable note | close | finish")
}

private fun phase4Capability(capability: Capability<*>): String = when (capability) {
    is Capability.Unsupported -> "Unsupported(operation=${capability.failure.operation})"
    is Capability.Supported<*> -> "Supported"
}

private data class Phase4HarnessOptions(
    val recordPath: Path,
    val buildId: String,
) {
    companion object {
        fun parse(args: Array<String>): Phase4HarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase4HarnessOptions(
                recordPath = Path.of(value("--record=") ?: "kadre/backend/appkit/build/manual/phase-4-input.tsv"),
                buildId = value("--build-id=") ?: phase4CommandOutput("git", "rev-parse", "HEAD").ifBlank { "unknown" },
            )
        }
    }
}

private class Phase4HarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase4HarnessOptions) {
        line(
            listOf(
                "RUN_METADATA",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${phase4CommandOutput("sysctl", "-n", "hw.model")}",
                "displays=${phase4CommandOutput("system_profiler", "SPDisplaysDataType", "-detailLevel", "mini")}",
                "buildId=${options.buildId}",
            ).joinToString("\t"),
        )
    }

    fun scenario(command: String) {
        val fields = command.split(' ', limit = 4)
        val id = fields.getOrNull(1).orEmpty()
        val status = fields.getOrNull(2).orEmpty()
        val note = fields.getOrNull(3).orEmpty()
        require(id in (1..8).map { "M$it" }) { "scenario must be M1 through M8" }
        require(status in setOf("pass", "fail", "not-applicable")) {
            "status must be pass, fail or not-applicable"
        }
        require(note.isNotBlank()) { "scenario result requires a short note" }
        line("SCENARIO\t$id\t$status\t${phase4Sanitise(note)}")
    }

    fun line(value: String) {
        val safe = phase4Sanitise(value)
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

private fun phase4CommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        "timeout"
    } else {
        process.inputStream.bufferedReader().readText().trim()
    }
}.getOrElse { "unavailable" }

private fun phase4Sanitise(value: String): String = value.replace('\n', ' ').replace('\r', ' ')
