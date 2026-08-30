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
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceUpdate
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

/** External AppKit observation tool. It logs Kadre state; it does not render application content. */
public fun main(args: Array<String>) {
    val options = HarnessOptions.parse(args)
    val recorder = HarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase3-harness-input").start {
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
                val opened = when (val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 3 surface"))) {
                    is KadreResult.Failure -> error("window request failed: ${request.reason}")
                    is KadreResult.Success -> request.value.await()
                }
                val window = when (opened) {
                    is WindowRequestOutcome.OpenedHere -> opened.window
                    else -> error("window did not open: $opened")
                }
                val surface = window.surface
                recorder.metadata(
                    options = options,
                    initialScaleFactor = surface.state.value.scaleFactor,
                    initialAppearance = surface.state.value.theme.toString(),
                )
                recorder.line("SNAPSHOT\tinitial\t${surface.state.value}")
                recorder.line("CAPABILITIES\t${surface.capabilities.value}")
                printHelp(recorder)

                val latestStateRevision = AtomicLong(surface.state.value.revision.value)
                val observedEventCount = AtomicInteger()
                val redrawEvents = Channel<Unit>(Channel.UNLIMITED)
                val stateCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    surface.state.collect { state ->
                        latestStateRevision.set(state.revision.value)
                        recorder.line("SNAPSHOT\tupdate\t$state")
                    }
                }
                val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    surface.events.collect { event ->
                        observedEventCount.incrementAndGet()
                        recorder.line(
                            "EVENT\t${event::class.simpleName}" +
                                "\tstateRevisionVisible=${stateRevisionVisible(surface.state.value, event)}" +
                                "\tcurrentRevision=${surface.state.value.revision.value}" +
                                "\teventRevision=${event.stateRevision.value}\t$event",
                        )
                        if (event is SurfaceEvent.RedrawRequested) redrawEvents.trySend(Unit)
                    }
                }

                var terminalState: org.graphiks.kadre.surface.SurfaceState? = null
                suspend fun closeAndObserveTerminal(): org.graphiks.kadre.surface.SurfaceState {
                    terminalState?.let { return it }
                    recorder.line("COMMAND\tclose\t${window.close()}")
                    val terminal = withTimeout(5.seconds) {
                        surface.state.first {
                            it.attachment == SurfaceAttachmentState.Detached
                        }
                    }
                    val terminalEventCount = observedEventCount.get()
                    delay(250.milliseconds)
                    val noLateRevision =
                        latestStateRevision.get() == terminal.revision.value && surface.state.value == terminal
                    val noLateEvent = observedEventCount.get() == terminalEventCount
                    recorder.line(
                        "TERMINAL_STABILITY\tnoLateRevision=$noLateRevision\tnoLateEvent=$noLateEvent" +
                            "\tobservationMillis=250",
                    )
                    check(noLateRevision) { "surface revision changed after terminal detachment" }
                    check(noLateEvent) { "surface event arrived after terminal detachment" }
                    recorder.line("TERMINAL\t$terminal")
                    terminalState = terminal
                    return terminal
                }

                for (line in commands) {
                    val command = line.trim()
                    when {
                        command.isEmpty() -> Unit
                        command == "help" -> printHelp(recorder)
                        command == "snapshot" -> recorder.line("SNAPSHOT\tmanual\t${surface.state.value}")
                        command.startsWith("redraw") -> {
                            val count = command.substringAfter(' ', "1").toIntOrNull()?.coerceIn(1, 10_000) ?: 1
                            val outcomes = List(count) { surface.requestRedraw() }
                            recorder.line("COMMAND\tredraw\tcount=$count\toutcomes=${outcomes.toSet()}")
                            withTimeout(5.seconds) { redrawEvents.receive() }
                        }
                        command == "unsupported" -> {
                            val result = surface.apply(
                                SurfaceUpdate(
                                    cursor = PropertyChange.Set(CursorStyle.Hidden),
                                    hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
                                    inputDefaultBehavior = PropertyChange.Set(
                                        InputDefaultBehavior.SuppressWhenPossible,
                                    ),
                                ),
                            )
                            recorder.line("COMMAND\tunsupported-surface-update\t$result")
                        }
                        command.startsWith("result ") -> {
                            val scenarioId = command.split(' ', limit = 3).getOrNull(1)
                            if (scenarioId == "M7" && terminalState == null) {
                                recorder.line(
                                    "COMMAND\tresult-rejected" +
                                        "\tM7 requires terminal observation before recording",
                                )
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

private fun stateRevisionVisible(
    current: org.graphiks.kadre.surface.SurfaceState,
    event: SurfaceEvent,
): Boolean = current.revision.value >= event.stateRevision.value

private fun printHelp(recorder: HarnessRecorder) {
    recorder.line(
        "HELP\tsnapshot | redraw [count] | unsupported | result M1..M7 pass|fail|not-applicable note | close | finish",
    )
}

private data class HarnessOptions(
    val recordPath: Path,
    val buildId: String,
) {
    companion object {
        fun parse(args: Array<String>): HarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return HarnessOptions(
                recordPath = Path.of(
                    value("--record=") ?: "kadre/backend/appkit/build/manual/phase-3-surface.tsv",
                ),
                buildId = value("--build-id=") ?: commandOutput("git", "rev-parse", "HEAD").ifBlank {
                    "unknown"
                },
            )
        }
    }
}

private class HarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    fun metadata(options: HarnessOptions, initialScaleFactor: Double, initialAppearance: String) {
        line(
            listOf(
                "RUN_METADATA",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${commandOutput("sysctl", "-n", "hw.model")}",
                "displays=${commandOutput("system_profiler", "SPDisplaysDataType", "-detailLevel", "mini")}",
                "initialScaleFactor=$initialScaleFactor",
                "appearance=$initialAppearance",
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
        line("SCENARIO\t$id\t$status\t${sanitise(note)}")
    }

    fun line(value: String) {
        val safe = sanitise(value)
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

private fun commandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching "unavailable: timed out"
    }
    process.inputStream.bufferedReader().use { it.readText() }.trim().ifBlank { "unavailable" }
}.getOrElse { "unavailable: ${it::class.simpleName}" }

private fun sanitise(value: String): String = value.replace('\n', ' ').replace('\r', ' ')
