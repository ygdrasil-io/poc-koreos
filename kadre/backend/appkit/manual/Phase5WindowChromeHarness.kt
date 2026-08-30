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
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowSystemButtons
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
 * External AppKit observation tool for mutable native window chrome.
 *
 * The window is intentionally empty: Kadre owns the native window and devices, not a renderer or
 * widget system. Visual observations remain an operator responsibility and are recorded separately
 * from the automated contract proofs.
 */
public fun main(args: Array<String>) {
    val options = Phase5ChromeHarnessOptions.parse(args)
    val recorder = Phase5ChromeHarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase5-chrome-harness-input").start {
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
                    val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 5 window chrome"))
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
                printPhase5ChromeHelp(recorder)

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

                suspend fun applyChrome(
                    name: String,
                    decorations: WindowDecorations,
                    buttons: WindowSystemButtons,
                ) {
                    recorder.line(
                        "COMMAND\t$name\t" +
                            window.apply(
                                WindowUpdate(
                                    decorations = PropertyChange.Set(decorations),
                                    systemButtons = PropertyChange.Set(buttons),
                                ),
                            ),
                    )
                    recorder.line("SNAPSHOT\tcommand-$name\t${window.state.value}")
                }

                for (line in commands) {
                    val command = line.trim()
                    when {
                        command.isEmpty() -> Unit
                        command == "help" -> printPhase5ChromeHelp(recorder)
                        command == "snapshot" -> recorder.line("SNAPSHOT\tmanual\t${window.state.value}")
                        command == "system-all" -> applyChrome(
                            "system-all",
                            WindowDecorations.System,
                            WindowSystemButtons.All,
                        )
                        command == "system-close-only" -> applyChrome(
                            "system-close-only",
                            WindowDecorations.System,
                            WindowSystemButtons.CloseOnly,
                        )
                        command == "system-none" -> applyChrome(
                            "system-none",
                            WindowDecorations.System,
                            WindowSystemButtons.None,
                        )
                        command == "borderless" -> applyChrome(
                            "borderless",
                            WindowDecorations.Borderless,
                            WindowSystemButtons.None,
                        )
                        command.startsWith("result ") -> recorder.scenario(command)
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

private fun stateRevisionVisible(currentRevision: Long, event: WindowEvent): Boolean =
    currentRevision >= event.stateRevision.value

private fun printPhase5ChromeHelp(recorder: Phase5ChromeHarnessRecorder) {
    recorder.line(
        "HELP\tsnapshot | system-all | system-close-only | system-none | borderless | " +
            "result M1..M4 pass|fail|not-applicable note | close | finish",
    )
}

private data class Phase5ChromeHarnessOptions(
    val recordPath: Path,
    val buildId: String,
) {
    companion object {
        fun parse(args: Array<String>): Phase5ChromeHarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase5ChromeHarnessOptions(
                recordPath = Path.of(
                    value("--record=") ?: "kadre/backend/appkit/build/manual/phase-5-window-chrome.tsv",
                ),
                buildId = value("--build-id=") ?: phase5CommandOutput("git", "rev-parse", "HEAD").ifBlank {
                    "unknown"
                },
            )
        }
    }
}

private class Phase5ChromeHarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase5ChromeHarnessOptions) {
        line(
            listOf(
                "RUN_METADATA",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${phase5CommandOutput("sysctl", "-n", "hw.model")}",
                "displays=${phase5CommandOutput("system_profiler", "SPDisplaysDataType", "-detailLevel", "mini")}",
                "buildId=${options.buildId}",
            ).joinToString("\t"),
        )
    }

    fun scenario(command: String) {
        val fields = command.split(' ', limit = 4)
        val id = fields.getOrNull(1).orEmpty()
        val status = fields.getOrNull(2).orEmpty()
        val note = fields.getOrNull(3).orEmpty()
        require(id in (1..4).map { "M$it" }) { "scenario must be M1 through M4" }
        require(status in setOf("pass", "fail", "not-applicable")) {
            "status must be pass, fail or not-applicable"
        }
        require(note.isNotBlank()) { "scenario result requires a short note" }
        line("SCENARIO\t$id\t$status\t${phase5Sanitise(note)}")
    }

    fun line(value: String) {
        val safe = phase5Sanitise(value)
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

private fun phase5CommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        "timeout"
    } else {
        process.inputStream.bufferedReader().readText().trim()
    }
}.getOrElse { "unavailable" }

private fun phase5Sanitise(value: String): String = value.replace('\n', ' ').replace('\r', ' ')
