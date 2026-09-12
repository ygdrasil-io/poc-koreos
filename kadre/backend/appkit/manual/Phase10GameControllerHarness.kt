package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * External manual-observation tool for AppKit GameController input and explicitly requested haptics.
 *
 * It never synthesizes input, reconnects a device, or starts an effect without an operator command.
 */
@OptIn(DelicateKadreApi::class)
public fun main(args: Array<String>) {
    val options = Phase10GameControllerOptions.parse(args)
    val recorder = Phase10GameControllerRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase10-gamecontroller-harness-input").start {
        generateSequence(::readlnOrNull).forEach { commands.trySend(it) }
        commands.trySend("finish")
        commands.close()
    }

    try {
        val outcome = runKadreApplication(
            options = DesktopHostOptions.Standalone(DesktopBackend.AppKit, stopWhenLastWindowClosed = false),
            application = KadreApplication {
                val opened = when (val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 10 GameController"))) {
                    is KadreResult.Failure -> error("window request failed: ${request.reason}")
                    is KadreResult.Success -> request.value.await()
                }
                check(opened is WindowRequestOutcome.OpenedHere) { "window did not open: $opened" }
                recorder.metadata(options)
                recorder.snapshot("initial", devices.state.value.inventory)
                recorder.line("HELP\tsnapshot | effect <index> <locality> | result M1..M4 pass|fail|not-applicable note | close | finish")
                val stateCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    devices.state.collect { state -> recorder.snapshot("update", state.inventory) }
                }
                val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    devices.events.collect(recorder::event)
                }

                suspend fun playEffect(index: Int, locality: GamepadHapticLocality) {
                    val gamepad = (devices.state.value.inventory as? DeviceInventory.Enumerated)
                        ?.gamepads?.getOrNull(index)
                    if (gamepad == null) {
                        recorder.line("EFFECT\tindex=$index\tunknown-gamepad")
                        return
                    }
                    when (val result = gamepad.playEffect(GamepadEffect.LocalizedHaptic(locality, 1.0, 1.seconds))) {
                        is KadreResult.Failure -> recorder.line("EFFECT\tindex=$index\tfailure\t${result.reason}")
                        is KadreResult.Success -> {
                            recorder.line("EFFECT\tindex=$index\taccepted\tlocality=$locality")
                            recorder.line("EFFECT_TERMINAL\tindex=$index\t${result.value.awaitTermination()}")
                        }
                    }
                }

                for (line in commands) {
                    val command = line.trim()
                    when {
                        command.isEmpty() -> Unit
                        command == "snapshot" -> recorder.snapshot("command", devices.state.value.inventory)
                        command.startsWith("effect ") -> {
                            val parts = command.split(' ')
                            val index = parts.getOrNull(1)?.toIntOrNull()
                            val locality = parts.getOrNull(2)?.let { value ->
                                GamepadHapticLocality.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                            }
                            if (index == null || locality == null) recorder.line("EFFECT\tinvalid-command")
                            else playEffect(index, locality)
                        }
                        command.startsWith("result ") -> recorder.scenario(command)
                        command == "close" || command == "finish" -> {
                            recorder.line("COMMAND\t$command")
                            check(opened.window.close() is KadreResult.Success)
                            break
                        }
                        else -> recorder.line("COMMAND\tunknown\t$command")
                    }
                }
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

private data class Phase10GameControllerOptions(val recordPath: Path, val buildId: String) {
    companion object {
        fun parse(args: Array<String>): Phase10GameControllerOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase10GameControllerOptions(
                recordPath = Path.of(value("--record=") ?: "kadre/backend/appkit/build/manual/phase-10-gamecontroller.tsv"),
                buildId = value("--build-id=") ?: phase10CommandOutput("git", "rev-parse", "HEAD").ifBlank { "unknown" },
            )
        }
    }
}

private class Phase10GameControllerRecorder(private val path: Path) : AutoCloseable {
    private val formatter = Phase10ManualInventoryFormatter()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase10GameControllerOptions) = line(
        "RUN_METADATA\tstartedAt=${Instant.now()}\tmacOS=${System.getProperty("os.version", "unknown")}" +
            "\tarchitecture=${System.getProperty("os.arch", "unknown")}" +
            "\thardware=${phase10CommandOutput("sysctl", "-n", "hw.model")}\tbuildId=${options.buildId}",
    )

    fun snapshot(label: String, inventory: DeviceInventory) =
        line("DEVICE_SNAPSHOT\t$label\t${formatter.formatInventory(inventory)}")

    fun event(event: org.graphiks.kadre.input.DeviceLifecycleEvent) =
        line("DEVICE_EVENT\t${formatter.formatEvent(event)}")

    fun scenario(command: String) {
        val fields = command.split(' ', limit = 4)
        require(fields.getOrNull(1) in setOf("M1", "M2", "M3", "M4")) { "scenario must be M1 through M4" }
        require(fields.getOrNull(2) in setOf("pass", "fail", "not-applicable")) { "invalid scenario status" }
        require(!fields.getOrNull(3).isNullOrBlank()) { "scenario result requires a note" }
        line("SCENARIO\t${fields[1]}\t${fields[2]}\t${fields[3]}")
    }

    @Synchronized
    fun line(value: String) {
        val safe = value.replace('\n', ' ').replace('\r', ' ')
        println(safe)
        writer.appendLine(safe)
        writer.flush()
    }

    override fun close() = writer.close()
}

private fun phase10CommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) "timeout" else process.inputStream.bufferedReader().readText().trim()
}.getOrElse { "unavailable" }
