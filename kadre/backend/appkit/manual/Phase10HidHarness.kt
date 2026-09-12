package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DeviceLifecycleEvent
import org.graphiks.kadre.input.Gamepad
import org.graphiks.kadre.input.GamepadId
import org.graphiks.kadre.input.InputDevice
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

/**
 * External manual-observation tool for the AppKit Phase 10 HID inventory MVP.
 *
 * It only records the detached [DeviceInventory] exposed by Kadre. It never opens HID reports,
 * injects input, remaps a device, or sends an output report.
 */
@OptIn(DelicateKadreApi::class)
public fun main(args: Array<String>) {
    val options = Phase10HidOptions.parse(args)
    val recorder = Phase10HidRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase10-hid-harness-input").start {
        generateSequence(::readlnOrNull).forEach { commands.trySend(it) }
        commands.trySend("finish")
        commands.close()
    }

    try {
        val outcome = runKadreApplication(
            options = DesktopHostOptions.Standalone(DesktopBackend.AppKit, stopWhenLastWindowClosed = false),
            application = KadreApplication {
                val opened = when (val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 10 HID"))) {
                    is KadreResult.Failure -> error("window request failed: ${request.reason}")
                    is KadreResult.Success -> request.value.await()
                }
                check(opened is WindowRequestOutcome.OpenedHere) { "window did not open: $opened" }
                recorder.metadata(options)
                recorder.snapshot("initial", devices.state.value.inventory)
                recorder.line("HELP\tsnapshot | result H1..H3 pass|fail|not-applicable note | close | finish")
                val stateCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    devices.state.collect { state -> recorder.snapshot("update", state.inventory) }
                }
                val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    devices.events.collect(recorder::event)
                }

                for (line in commands) {
                    when (val command = line.trim()) {
                        "" -> Unit
                        "snapshot" -> recorder.snapshot("command", devices.state.value.inventory)
                        "close", "finish" -> {
                            recorder.line("COMMAND\t$command")
                            check(opened.window.close() is KadreResult.Success)
                            break
                        }
                        else -> when {
                            command.startsWith("result ") -> recorder.scenario(Phase10HidScenarioResult.parse(command))
                            else -> recorder.line("COMMAND\tunknown\t$command")
                        }
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

internal data class Phase10HidOptions(val recordPath: Path, val buildId: String) {
    internal companion object {
        fun parse(args: Array<String>): Phase10HidOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            return Phase10HidOptions(
                recordPath = Path.of(value("--record=") ?: "kadre/backend/appkit/build/manual/phase-10-hid.tsv"),
                buildId = value("--build-id=") ?: phase10HidCommandOutput("git", "rev-parse", "HEAD").ifBlank { "unknown" },
            )
        }
    }
}

internal data class Phase10HidScenarioResult(
    val id: String,
    val status: String,
    val note: String,
) {
    internal companion object {
        fun parse(command: String): Phase10HidScenarioResult {
            val fields = command.split(' ', limit = 4)
            require(fields.firstOrNull() == "result") { "scenario result must start with result" }
            require(fields.getOrNull(1) in setOf("H1", "H2", "H3")) { "scenario must be H1 through H3" }
            require(fields.getOrNull(2) in setOf("pass", "fail", "not-applicable")) { "invalid scenario status" }
            require(!fields.getOrNull(3).isNullOrBlank()) { "scenario result requires a note" }
            return Phase10HidScenarioResult(fields[1], fields[2], fields[3])
        }
    }
}

private class Phase10HidRecorder(private val path: Path) : AutoCloseable {
    private val formatter = Phase10HidInventoryFormatter()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase10HidOptions) = line(
        "RUN_METADATA\tstartedAt=${Instant.now()}\tmacOS=${System.getProperty("os.version", "unknown")}" +
            "\tarchitecture=${System.getProperty("os.arch", "unknown")}" +
            "\thardware=${phase10HidCommandOutput("sysctl", "-n", "hw.model")}\tbuildId=${options.buildId}",
    )

    fun snapshot(label: String, inventory: DeviceInventory) =
        line("DEVICE_SNAPSHOT\t$label\t${formatter.formatInventory(inventory)}")

    fun event(event: DeviceLifecycleEvent) = line("DEVICE_EVENT\t${formatter.formatEvent(event)}")

    fun scenario(result: Phase10HidScenarioResult) =
        line("SCENARIO\t${result.id}\t${result.status}\t${result.note}")

    fun line(value: String) {
        val safe = value.replace('\n', ' ').replace('\r', ' ')
        println(safe)
        writer.appendLine(safe)
        writer.flush()
    }

    override fun close() = writer.close()
}

/** Formats public device data without relying on implementation `toString()` or exposing raw IDs. */
internal class Phase10HidInventoryFormatter {
    private val deviceTokens = linkedMapOf<DeviceId, String>()
    private val gamepadTokens = linkedMapOf<GamepadId, String>()

    fun formatInventory(inventory: DeviceInventory): String = when (inventory) {
        DeviceInventory.Unsupported -> "unsupported"
        is DeviceInventory.Unavailable -> "unavailable failure=${inventory.failure}"
        is DeviceInventory.Enumerated -> buildString {
            append("enumerated devices=")
            append(inventory.devices.joinToString(prefix = "[", postfix = "]", transform = ::formatDevice))
            append(" gamepads=")
            append(inventory.gamepads.joinToString(prefix = "[", postfix = "]", transform = ::formatGamepad))
        }
    }

    fun formatEvent(event: DeviceLifecycleEvent): String = when (event) {
        is DeviceLifecycleEvent.DeviceAdded ->
            "DeviceAdded ${formatDevice(event.device)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"

        is DeviceLifecycleEvent.DeviceRemoved ->
            "DeviceRemoved ${deviceToken(event.deviceId)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"

        is DeviceLifecycleEvent.GamepadAdded ->
            "GamepadAdded ${formatGamepad(event.gamepad)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"

        is DeviceLifecycleEvent.GamepadRemoved ->
            "GamepadRemoved ${gamepadToken(event.gamepadId)} revision=${event.managerRevision.value} sequence=${event.stamp.sequence.value}"
    }

    private fun formatDevice(device: InputDevice): String = with(device) {
        "${deviceToken(id)}{name=${quoted(descriptor.name)},kind=${descriptor.kind},connection=${connection.value}}"
    }

    private fun formatGamepad(gamepad: Gamepad): String = with(gamepad) {
        val state = state.value
        "${gamepadToken(id)}{name=${quoted(state.descriptor.name)},mapping=${state.descriptor.mapping}," +
            "connection=${state.connection},routing=${state.routing}}"
    }

    private fun deviceToken(id: DeviceId): String = deviceTokens.getOrPut(id) { "d${deviceTokens.size + 1}" }

    private fun gamepadToken(id: GamepadId): String = gamepadTokens.getOrPut(id) { "g${gamepadTokens.size + 1}" }

    private fun quoted(value: String?): String = value
        ?.replace("\\", "\\\\")
        ?.replace("\"", "\\\"")
        ?.replace("\t", "\\t")
        ?.replace("\n", "\\n")
        ?.replace("\r", "\\r")
        ?.let { escaped -> "\"$escaped\"" }
        ?: "null"
}

private fun phase10HidCommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) "timeout" else process.inputStream.bufferedReader().readText().trim()
}.getOrElse { "unavailable" }
