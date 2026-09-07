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
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
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
 * External manual-observation tool for the AppKit drag-and-drop bridge.
 *
 * The synchronous handler accepts a real native offer immediately, then reads only byte counts in
 * a child coroutine. It deliberately never records payload bytes, file paths or URLs.
 */
@OptIn(DelicateKadreApi::class)
public fun main(args: Array<String>) {
    val options = Phase7DropHarnessOptions.parse(args)
    val recorder = Phase7DropHarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase7-drop-harness-input").start {
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
                val opened = when (val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 7 drop"))) {
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
                recorder.line("INPUT_CAPABILITY\tdragAndDrop=${input.state.value.capabilities.dragAndDrop}")

                val activeTransfers = AtomicInteger()
                val installed = window.surface.installInteractionHandler(InteractionHandler { context, event ->
                        if (event !is InteractionEvent.DropEntered) return@InteractionHandler
                        recorder.line("OFFER\titems=${event.offer.items}")
                        val accepted = context.request(InteractionAction.AcceptDrop(event.offer.id))
                        recorder.line("ACCEPT\t$accepted")
                        if (accepted is KadreResult.Success) {
                            launch {
                                when (val claim = event.offer.claimTransfer()) {
                                    is KadreResult.Failure -> recorder.line("TRANSFER\tclaim-failed\t${claim.reason}")
                                    is KadreResult.Success -> {
                                        val transfer = claim.value
                                        activeTransfers.incrementAndGet()
                                        recorder.line("TRANSFER\tclaimed\titems=${transfer.items.size}")
                                        try {
                                            transfer.items.forEachIndexed { index, item ->
                                                var bytes = 0L
                                                val read = item.collectBytes(MAX_READ_BYTES) { chunk ->
                                                    bytes += chunk.size.toLong()
                                                }
                                                recorder.line(
                                                    "ITEM\tindex=$index\tkind=${item.descriptor.kind}" +
                                                        "\tbytesRead=$bytes\tresult=$read",
                                                )
                                            }
                                        } finally {
                                            transfer.close()
                                            activeTransfers.decrementAndGet()
                                            recorder.line("TRANSFER\tclosed")
                                        }
                                    }
                                }
                            }
                        }
                    })
                val registration = when (installed) {
                    is KadreResult.Failure -> error("drop interaction handler failed: ${installed.reason}")
                    is KadreResult.Success -> installed.value
                }
                recorder.line("HANDLER\tinstalled")
                printPhase7DropHelp(recorder)

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
                        val isDrop = event is InputEvent.DropEntered || event is InputEvent.DropMoved ||
                            event is InputEvent.DropExited || event is InputEvent.Dropped
                        recorder.line(
                            "EVENT\t${event::class.simpleName}" +
                                "\tstateRevisionVisible=${input.state.value.revision.value >= event.stateRevision.value}" +
                                "\tcurrentRevision=${input.state.value.revision.value}" +
                                "\teventRevision=${event.stateRevision.value}\tdrop=$isDrop",
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
                    withTimeout(5.seconds) {
                        while (activeTransfers.get() != 0) delay(10.milliseconds)
                    }
                    val terminalRevision = latestRevision.get()
                    val terminalEventCount = observedEventCount.get()
                    delay(250.milliseconds)
                    val noLateRevision = latestRevision.get() == terminalRevision
                    val noLateEvent = observedEventCount.get() == terminalEventCount
                    recorder.line(
                        "TERMINAL_STABILITY\tnoLateRevision=$noLateRevision\tnoLateEvent=$noLateEvent" +
                            "\tactiveTransfers=${activeTransfers.get()}\tobservationMillis=250",
                    )
                    check(noLateRevision) { "input revision changed after terminal detachment" }
                    check(noLateEvent) { "input event arrived after terminal detachment" }
                    check(activeTransfers.get() == 0) { "a drop transfer survived terminal detachment" }
                    terminalObserved = true
                }

                for (line in commands) {
                    val command = line.trim()
                    when {
                        command.isEmpty() -> Unit
                        command == "help" -> printPhase7DropHelp(recorder)
                        command == "snapshot" -> recorder.line("COMMAND\tsnapshot\t${input.state.value}")
                        command.startsWith("result ") -> {
                            val scenario = command.split(' ', limit = 3).getOrNull(1)
                            if (scenario == "M5" && !terminalObserved) {
                                recorder.line("COMMAND\tresult-rejected\tM5 requires terminal observation before recording")
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
                registration.close()
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

private fun printPhase7DropHelp(recorder: Phase7DropHarnessRecorder) {
    recorder.line("HELP\tsnapshot | result M1..M5 pass|fail|not-applicable note | close | finish")
}

private data class Phase7DropHarnessOptions(
    val recordPath: Path,
    val buildId: String,
) {
    companion object {
        fun parse(args: Array<String>): Phase7DropHarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase7DropHarnessOptions(
                recordPath = Path.of(value("--record=") ?: "kadre/backend/appkit/build/manual/phase-7-drop.tsv"),
                buildId = value("--build-id=") ?: phase7DropCommandOutput("git", "rev-parse", "HEAD").ifBlank {
                    "unknown"
                },
            )
        }
    }
}

private class Phase7DropHarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase7DropHarnessOptions) {
        line(
            listOf(
                "RUN_METADATA",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${phase7DropCommandOutput("sysctl", "-n", "hw.model")}",
                "displays=${phase7DropCommandOutput("system_profiler", "SPDisplaysDataType", "-detailLevel", "mini")}",
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
        line("SCENARIO\t$id\t$status\t${phase7DropSanitise(note)}")
    }

    fun line(value: String) {
        val safe = phase7DropSanitise(value)
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

private fun phase7DropCommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        "timeout"
    } else {
        process.inputStream.bufferedReader().readText().trim()
    }
}.getOrElse { "unavailable" }

private fun phase7DropSanitise(value: String): String = value.replace('\n', ' ').replace('\r', ' ')

private const val MAX_READ_BYTES = 16L * 1024L * 1024L
