package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.capture.CaptureFrame
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSources
import org.graphiks.kadre.capture.CaptureTarget
import org.graphiks.kadre.diagnostics.KadreResult
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * External manual-observation tool for AppKit ScreenCaptureKit support.
 *
 * It records only detached public state and frame metadata. Pixel bytes, source identifiers,
 * source titles, and native handles are intentionally never persisted.
 */
public fun main(args: Array<String>) {
    val options = Phase11CaptureHarnessOptions.parse(args)
    val recorder = Phase11CaptureHarnessRecorder(options.recordPath)
    val commands = Channel<String>(Channel.UNLIMITED)
    Thread.ofPlatform().daemon().name("kadre-phase11-capture-harness-input").start {
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
                val opened = when (val request = windows.requestWindow(WindowSpec(title = "Kadre Phase 11 capture"))) {
                    is KadreResult.Failure -> error("window request failed: ${request.reason}")
                    is KadreResult.Success -> request.value.await()
                }
                val window = when (opened) {
                    is WindowRequestOutcome.OpenedHere -> opened.window
                    else -> error("window did not open: $opened")
                }
                val sessions = linkedMapOf<Int, CaptureSession>()
                val collectors = linkedMapOf<Int, List<Job>>()
                val frameCounts = linkedMapOf<Int, AtomicLong>()
                var nextSessionId = 1

                recorder.metadata(options)
                recorder.snapshot("initial", capture.state.value.sources)
                printPhase11CaptureHelp(recorder)

                suspend fun open(request: CaptureRequest) {
                    when (val openedCapture = capture.open(request)) {
                        is KadreResult.Failure -> recorder.line("OPEN\tfailure\t${openedCapture.reason}")
                        is KadreResult.Success -> {
                            val id = nextSessionId++
                            val session = openedCapture.value
                            sessions[id] = session
                            val count = AtomicLong()
                            frameCounts[id] = count
                            recorder.line("OPEN\tsuccess\tsession=$id\tsource=${session.source.kind}\tsize=${session.source.size}")
                            collectors[id] = listOf(
                                launch(start = CoroutineStart.UNDISPATCHED) {
                                    session.events.collect { event ->
                                        recorder.line("CAPTURE_EVENT\tsession=$id\t${event::class.simpleName}")
                                    }
                                },
                                launch(start = CoroutineStart.UNDISPATCHED) {
                                    session.diagnostics.collect { diagnostic ->
                                        recorder.line("CAPTURE_DIAGNOSTIC\tsession=$id\t${diagnostic::class.simpleName}")
                                    }
                                },
                                launch(start = CoroutineStart.UNDISPATCHED) {
                                    val terminal = session.collectFrames { frame ->
                                        recordFrame(id, count.incrementAndGet(), frame, recorder)
                                        if (options.frameProcessingDelayMillis > 0L) {
                                            delay(options.frameProcessingDelayMillis.milliseconds)
                                        }
                                    }
                                    recorder.line("COLLECT_TERMINAL\tsession=$id\t$terminal")
                                },
                            )
                        }
                    }
                }

                suspend fun closeSession(id: Int) {
                    val session = sessions.remove(id)
                    if (session == null) {
                        recorder.line("CLOSE\tsession=$id\tunknown")
                        return
                    }
                    session.close()
                    val outcome = withTimeoutOrNull(5.seconds) { session.awaitTermination() }
                    recorder.line("CLOSE\tsession=$id\toutcome=$outcome\tframes=${frameCounts[id]?.get() ?: 0L}")
                    collectors.remove(id).orEmpty().forEach(Job::cancel)
                    frameCounts.remove(id)
                }

                suspend fun openSource(index: Int) {
                    val sources = (capture.state.value.sources as? CaptureSources.Enumerated)?.values
                    val source = sources?.getOrNull(index - 1)
                    if (source == null) {
                        recorder.line("OPEN_SOURCE\tindex=$index\tunavailable")
                    } else {
                        open(CaptureRequest(CaptureTarget.Source(source.id, source.managerRevision)))
                    }
                }

                for (line in commands) {
                    val command = line.trim()
                    when {
                        command.isEmpty() -> Unit
                        command == "help" -> printPhase11CaptureHelp(recorder)
                        command == "snapshot" -> recorder.snapshot("command", capture.state.value.sources)
                        command == "permission" -> {
                            val result = capture.requestPermission(org.graphiks.kadre.capture.CapturePermissionScope.Screen)
                            recorder.line("REQUEST_PERMISSION\t$result")
                            recorder.snapshot("after-permission", capture.state.value.sources)
                        }
                        command == "refresh" -> {
                            recorder.line("REFRESH\t${capture.refreshSources()}")
                            recorder.snapshot("after-refresh", capture.state.value.sources)
                        }
                        command == "open-picker" -> open(CaptureRequest(CaptureTarget.HostChoice))
                        command.startsWith("open-source ") -> openSource(command.substringAfter(' ').toIntOrNull() ?: -1)
                        command.startsWith("close ") -> closeSession(command.substringAfter(' ').toIntOrNull() ?: -1)
                        command.startsWith("stress ") -> {
                            val parts = command.split(' ')
                            val id = parts.getOrNull(1)?.toIntOrNull()
                            val durationSeconds = parts.getOrNull(2)?.toLongOrNull()
                            val count = id?.let(frameCounts::get)
                            if (durationSeconds == null || durationSeconds <= 0L || count == null) {
                                recorder.line("STRESS\tinvalid")
                            } else {
                                val before = count.get()
                                delay(durationSeconds.seconds)
                                recorder.line(
                                    "STRESS\tsession=$id\tdurationSeconds=$durationSeconds" +
                                        "\tframes=${count.get() - before}\tstate=${sessions[id]?.state?.value}",
                                )
                            }
                        }
                        command.startsWith("result ") -> recorder.scenario(command)
                        command == "finish" -> break
                        else -> recorder.line("COMMAND\tunknown")
                    }
                }
                for (id in sessions.keys.toList()) closeSession(id)
                window.close()
                requestStop()
            },
        )
        recorder.line("SESSION_OUTCOME\t$outcome")
    } finally {
        recorder.close()
    }
}

private fun recordFrame(
    sessionId: Int,
    count: Long,
    frame: CaptureFrame,
    recorder: Phase11CaptureHarnessRecorder,
) {
    val layouts = frame.planes.joinToString(separator = ",") { layout ->
        "${layout.width}x${layout.height}:stride=${layout.rowStride}:bytes=${layout.byteCount}"
    }
    recorder.line(
        "FRAME\tsession=$sessionId\tcount=$count\tsize=${frame.size}\tformat=${frame.format}" +
            "\trevision=${frame.configurationRevision.value}\tplanes=$layouts",
    )
}

private fun printPhase11CaptureHelp(recorder: Phase11CaptureHarnessRecorder) {
    recorder.line(
        "HELP\tsnapshot | permission | refresh | open-picker | open-source <index> | close <session> | " +
            "stress <session> <seconds> | result M1..M9 pass|fail|not-applicable note | finish",
    )
}

private data class Phase11CaptureHarnessOptions(
    val recordPath: Path,
    val buildId: String,
    val frameProcessingDelayMillis: Long,
) {
    companion object {
        fun parse(args: Array<String>): Phase11CaptureHarnessOptions {
            fun value(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter('=')
            val delay = value("--frame-delay-ms=")?.toLongOrNull() ?: 0L
            require(delay >= 0L) { "--frame-delay-ms must be non-negative" }
            return Phase11CaptureHarnessOptions(
                recordPath = Path.of(value("--record=") ?: "kadre/backend/appkit/build/manual/phase-11-capture.tsv"),
                buildId = value("--build-id=") ?: phase11CaptureCommandOutput("git", "rev-parse", "HEAD").ifBlank {
                    "unknown"
                },
                frameProcessingDelayMillis = delay,
            )
        }
    }
}

private class Phase11CaptureHarnessRecorder(private val path: Path) : AutoCloseable {
    private val lock = Any()
    private val writer = run {
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun metadata(options: Phase11CaptureHarnessOptions) {
        line(
            listOf(
                "RUN_METADATA",
                "startedAt=${Instant.now()}",
                "macOS=${System.getProperty("os.version", "unknown")}",
                "architecture=${System.getProperty("os.arch", "unknown")}",
                "hardware=${phase11CaptureCommandOutput("sysctl", "-n", "hw.model")}",
                "buildId=${options.buildId}",
                "frameDelayMillis=${options.frameProcessingDelayMillis}",
            ).joinToString("\t"),
        )
    }

    fun snapshot(label: String, sources: CaptureSources) {
        val summary = when (sources) {
            is CaptureSources.Enumerated -> sources.values.joinToString(prefix = "enumerated=", separator = ",") {
                "${it.kind}:${it.size}"
            }
            CaptureSources.HostPickerOnly -> "host-picker-only"
            is CaptureSources.PermissionRequired -> "permission-required=${sources.required}"
            is CaptureSources.Unavailable -> "unavailable=${sources.failure}"
        }
        line("CAPTURE_SNAPSHOT\t$label\t$summary")
    }

    fun scenario(command: String) {
        val fields = command.split(' ', limit = 4)
        val id = fields.getOrNull(1).orEmpty()
        val status = fields.getOrNull(2).orEmpty()
        val note = fields.getOrNull(3).orEmpty()
        require(id in (1..9).map { "M$it" }) { "scenario must be M1 through M9" }
        require(status in setOf("pass", "fail", "not-applicable")) {
            "status must be pass, fail or not-applicable"
        }
        require(note.isNotBlank()) { "scenario result requires a short note" }
        line("SCENARIO\t$id\t$status\t${phase11CaptureSanitise(note)}")
    }

    fun line(value: String) {
        val safe = phase11CaptureSanitise(value)
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

private fun phase11CaptureCommandOutput(vararg command: String): String = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        "timeout"
    } else {
        process.inputStream.bufferedReader().readText().trim()
    }
}.getOrElse { "unavailable" }

private fun phase11CaptureSanitise(value: String): String = value.replace('\n', ' ').replace('\r', ' ')
