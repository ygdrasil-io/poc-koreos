package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.interaction.InteractionRegistration
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowUpdate
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
 * External AppKit observation tool for Phase 5 appearance, attention, and native window moves.
 *
 * It deliberately records only public state, events, and outcomes. An operator performs the
 * physical pointer-down after installing the handler; this tool never synthesises a gesture or
 * claims any compositor-visible effect.
 */
@OptIn(DelicateKadreApi::class)
public fun main(args: Array<String>) {
    val options = Phase5AdvancedWindowHarnessOptions.parse(args)
    val recorder = Phase5AdvancedWindowHarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase5-advanced-window-harness-input").start {
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
                val opened = when (
                    val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 5 advanced window"))
                ) {
                    is KadreResult.Failure -> error("window request failed: ${request.reason}")
                    is KadreResult.Success -> request.value.await()
                }
                val window = when (opened) {
                    is WindowRequestOutcome.OpenedHere -> opened.window
                    else -> error("window did not open: $opened")
                }
                recorder.metadata(options)
                recorder.line("SNAPSHOT\tinitial\t${window.state.value}")
                recorder.line("CAPABILITIES\t${window.capabilities.value}")
                recorder.line("SURFACE_CAPABILITIES\t${window.surface.capabilities.value}")
                printPhase5AdvancedWindowHelp(recorder)

                val latestRevision = AtomicLong(window.state.value.revision.value)
                val observedEventCount = AtomicInteger()
                val stateCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    window.state.collect { state ->
                        latestRevision.set(state.revision.value)
                        recorder.line("SNAPSHOT\tupdate\t$state")
                    }
                }
                val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    window.events.collect { event ->
                        observedEventCount.incrementAndGet()
                        recorder.line(
                            "EVENT\t${event::class.simpleName}" +
                                "\tstateRevisionVisible=${stateRevisionVisible(window.state.value.revision.value, event)}" +
                                "\tcurrentRevision=${window.state.value.revision.value}" +
                                "\teventRevision=${event.stateRevision.value}\t$event",
                        )
                    }
                }
                val inputCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    window.surface.input.events.collect { event: InputEvent ->
                        recorder.line(
                            "INPUT_EVENT\t${event::class.simpleName}\tstateRevision=${event.stateRevision.value}\t$event",
                        )
                    }
                }

                var registration: InteractionRegistration? = null
                var interactionCollector: Job? = null
                var terminalObserved = false

                suspend fun closeAndObserveTerminal() {
                    if (terminalObserved) return
                    recorder.line("COMMAND\tclose\t${window.close()}")
                    val terminal = withTimeout(5.seconds) {
                        window.state.first { it.phase == WindowPhase.Closed }
                    }
                    val terminalEventCount = observedEventCount.get()
                    delay(250.milliseconds)
                    val noLateRevision =
                        latestRevision.get() == terminal.revision.value && window.state.value == terminal
                    val noLateEvent = observedEventCount.get() == terminalEventCount
                    recorder.line(
                        "TERMINAL_STABILITY\tnoLateRevision=$noLateRevision\tnoLateEvent=$noLateEvent" +
                            "\tobservationMillis=250",
                    )
                    check(noLateRevision) { "window revision changed after terminal close" }
                    check(noLateEvent) { "window event arrived after terminal close" }
                    recorder.line("TERMINAL\t$terminal")
                    terminalObserved = true
                }

                suspend fun setTransparency(name: String, value: Boolean) {
                    recorder.line(
                        "COMMAND\t$name\t" +
                            window.apply(WindowUpdate(transparency = PropertyChange.Set(value))),
                    )
                    recorder.line("SNAPSHOT\tcommand-$name\t${window.state.value}")
                }

                suspend fun requestAttention(name: String, attention: WindowAttention) {
                    recorder.line("COMMAND\tattention-$name\t${window.requestAttention(attention)}")
                }

                fun installMoveHandler() {
                    if (registration != null) {
                        recorder.line("COMMAND\tinstall-move-handler\talready-installed")
                        return
                    }
                    val result = window.surface.installInteractionHandler(InteractionHandler { context, event ->
                        val request = context.request(InteractionAction.BeginWindowMove)
                        recorder.line(
                            "MOVE_HANDLER\tevent=${event::class.simpleName}\tstamp=${event.stamp}\trequest=$request",
                        )
                    })
                    recorder.line("COMMAND\tinstall-move-handler\t$result")
                    if (result is KadreResult.Success) {
                        registration = result.value
                        interactionCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                            result.value.outcomes.collect { outcome ->
                                recorder.line("INTERACTION_OUTCOME\t${outcome::class.simpleName}\t$outcome")
                            }
                        }
                    }
                }

                for (line in commands) {
                    when (val command = line.trim()) {
                        "" -> Unit
                        "help" -> printPhase5AdvancedWindowHelp(recorder)
                        "snapshot" -> recorder.line("SNAPSHOT\tmanual\t${window.state.value}")
                        "transparent" -> setTransparency("transparent", true)
                        "opaque" -> setTransparency("opaque", false)
                        "attention informational" -> requestAttention("informational", WindowAttention.Informational)
                        "attention critical" -> requestAttention("critical", WindowAttention.Critical)
                        "attention none" -> requestAttention("none", WindowAttention.None)
                        "install-move-handler" -> installMoveHandler()
                        "move" -> recorder.line(
                            if (registration == null) {
                                "COMMAND\tmove\trejected-handler-not-installed"
                            } else {
                                "COMMAND\tmove\tawaiting-real-pointer-down"
                            },
                        )
                        "close" -> closeAndObserveTerminal()
                        "finish" -> {
                            recorder.line("COMMAND\tfinish")
                            closeAndObserveTerminal()
                            break
                        }
                        else -> {
                            if (command.startsWith("result ")) recorder.scenario(command)
                            else recorder.line("COMMAND\tunknown\t$command")
                        }
                    }
                }
                closeAndObserveTerminal()
                interactionCollector?.cancel()
                registration?.close()
                inputCollector.cancel()
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

private fun stateRevisionVisible(currentRevision: Long, event: WindowEvent): Boolean =
    currentRevision >= event.stateRevision.value

private fun printPhase5AdvancedWindowHelp(recorder: Phase5AdvancedWindowHarnessRecorder) {
    recorder.line(
        "HELP\tsnapshot | transparent | opaque | attention informational|critical|none | " +
            "install-move-handler | move (then press the real pointer in the window) | " +
            "result M1..M5 pass|fail|not-applicable note | close | finish",
    )
}

private data class Phase5AdvancedWindowHarnessOptions(
    val recordPath: Path,
    val buildId: String,
) {
    companion object {
        fun parse(args: Array<String>): Phase5AdvancedWindowHarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase5AdvancedWindowHarnessOptions(
                recordPath = Path.of(
                    value("--record=") ?: "kadre/backend/appkit/build/manual/phase-5-advanced-window.tsv",
                ),
                buildId = value("--build-id=") ?: phase5AdvancedWindowCommandOutput("git", "rev-parse", "HEAD")
                    .ifBlank { "unknown" },
            )
        }
    }
}

private class Phase5AdvancedWindowHarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase5AdvancedWindowHarnessOptions) {
        line(
            listOf(
                "RUN_METADATA",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${phase5AdvancedWindowCommandOutput("sysctl", "-n", "hw.model")}",
                "displays=${phase5AdvancedWindowCommandOutput("system_profiler", "SPDisplaysDataType", "-detailLevel", "mini")}",
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
        line("SCENARIO\t$id\t$status\t${phase5AdvancedWindowSanitise(note)}")
    }

    fun line(value: String) {
        val safe = phase5AdvancedWindowSanitise(value)
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

private fun phase5AdvancedWindowCommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        "timeout"
    } else {
        process.inputStream.bufferedReader().readText().trim()
    }
}.getOrElse { "unavailable" }

private fun phase5AdvancedWindowSanitise(value: String): String =
    value.replace('\n', ' ').replace('\r', ' ')
