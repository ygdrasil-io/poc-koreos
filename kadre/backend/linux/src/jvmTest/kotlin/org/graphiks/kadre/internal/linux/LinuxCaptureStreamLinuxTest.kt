package org.graphiks.kadre.internal.linux

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.linux.capture.LinuxCapturePort
import org.graphiks.kadre.internal.linux.dbus.DbusConnection
import org.graphiks.kadre.internal.linux.dbus.DbusMessage
import org.graphiks.kadre.internal.linux.dbus.DbusValue
import org.graphiks.kadre.internal.linux.dbus.KffiDbusTransport
import org.graphiks.kadre.internal.linux.portal.ScreenCastPortal
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart

/**
 * O3 proof of the capture DATA path (design LINUX-PIPEWIRE-STREAM-DESIGN
 * D11): a real pipewire daemon with a videotestsrc node, a private
 * dbus-daemon, and a peer portal that answers OpenPipeWireRemote by
 * passing a socket fd connected to the real pipewire socket — chemically
 * what xdg-desktop-portal does. The production port then negotiates and
 * receives real frames through kffi-pipewire. Linux-only, dedicated
 * container oracle (the gate rides the O2 proofs).
 */
class LinuxCaptureStreamLinuxTest {

    private fun environmentReady(): Boolean {
        if (System.getProperty("os.name") != "Linux") return false
        val runtimeDir = System.getenv("XDG_RUNTIME_DIR") ?: return false
        if (!File("$runtimeDir/pipewire-0").exists()) return false
        return runCatching {
            ProcessBuilder("dbus-daemon", "--version").start().waitFor() == 0
        }.getOrDefault(false)
    }

    /** The kadre-testsrc node id, from pw-cli over the live daemon. */
    private fun testSourceNodeId(): Int? = runCatching {
        val output = ProcessBuilder("pw-cli", "ls", "Node")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText()
        var currentId: Int? = null
        output.lineSequence().forEach { line ->
            val idMatch = Regex("""id (\d+), type PipeWire:Interface:Node""").find(line)
            if (idMatch != null) {
                currentId = idMatch.groupValues[1].toInt()
            }
            if (line.contains("kadre-testsrc")) {
                return currentId
            }
        }
        null
    }.getOrNull()

    /** Opens a plain socket to a unix path and returns the fd. */
    private fun openUnixSocket(path: String): Int {
        val fd = org.graphiks.kffi.posix.LinuxPosix.socket(
            org.graphiks.kffi.posix.LinuxPosix.AF_UNIX,
            org.graphiks.kffi.posix.LinuxPosix.SOCK_STREAM,
        )
        check(fd >= 0) { "socket() failed" }
        java.lang.foreign.Arena.ofConfined().use { arena ->
            val address = org.graphiks.kffi.posix.generated.sockaddr_un.allocate(arena)
            address.set(
                java.lang.foreign.ValueLayout.JAVA_SHORT,
                org.graphiks.kffi.posix.generated.sockaddr_un.layout.byteOffset(
                    java.lang.foreign.MemoryLayout.PathElement.groupElement("sun_family"),
                ),
                org.graphiks.kffi.posix.LinuxPosix.AF_UNIX.toShort(),
            )
            val pathOffset = org.graphiks.kffi.posix.generated.sockaddr_un.layout.byteOffset(
                java.lang.foreign.MemoryLayout.PathElement.groupElement("sun_path"),
            )
            val bytes = path.toByteArray()
            for (index in bytes.indices) {
                address.set(java.lang.foreign.ValueLayout.JAVA_BYTE, pathOffset + index, bytes[index])
            }
            address.set(java.lang.foreign.ValueLayout.JAVA_BYTE, pathOffset + bytes.size, 0)
            val length = (pathOffset + bytes.size + 1).toInt()
            org.graphiks.kffi.posix.LinuxPosix.connect(fd, address, length)
        }
        return fd
    }

    private class Bus(val address: String, val process: Process) {
        fun stop() {
            process.destroy()
            process.waitFor()
        }
    }

    private fun startBus(): Bus? {
        val socket = File("/tmp/kadre-stream-dbus-${System.nanoTime()}.bus")
        val process = ProcessBuilder(
            "dbus-daemon",
            "--session",
            "--address=unix:path=${socket.absolutePath}",
            "--nofork",
            "--nopidfile",
        ).apply {
            environment().remove("DBUS_SESSION_BUS_ADDRESS")
        }.start()
        val deadline = System.currentTimeMillis() + 5_000
        while (!socket.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        if (!socket.exists()) {
            process.destroy()
            return null
        }
        return Bus("unix:path=${socket.absolutePath}", process)
    }

    private fun connect(address: String, deadline: Long = System.currentTimeMillis() + 5_000): DbusConnection? {
        while (true) {
            val transport = KffiDbusTransport.connectToSessionBus(address)
            if (transport != null) return DbusConnection(transport, uidProvider = { LinuxCapturePort.effectiveUid() })
            if (System.currentTimeMillis() > deadline) return null
            Thread.sleep(25)
        }
    }

    @Test
    fun realPipeWireDeliversFramesThroughThePortalChain() = kotlinx.coroutines.runBlocking {
        if (!environmentReady()) return@runBlocking
        val nodeId = testSourceNodeId() ?: run {
            println("CAPTURE-STREAM-O3 skipped: kadre-testsrc node absent")
            return@runBlocking
        }
        val runtimeDir = System.getenv("XDG_RUNTIME_DIR")!!
        val fdsBefore = File("/proc/self/fd").list()!!.size

        val bus = startBus() ?: error("dbus-daemon failed to start")
        var peerRef: DbusConnection? = null
        try {
            // the peer portal: owns the name, answers the ScreenCast chain,
            // and passes a REAL pipewire socket fd on OpenPipeWireRemote
            val peerTransport = KffiDbusTransport.connectToSessionBus(bus.address) ?: error("peer connect failed")
            val peer = DbusConnection(peerTransport, uidProvider = { LinuxCapturePort.effectiveUid() })
            peerRef = peer
            peer.use {
                assertTrue(peer.performHandshake(5_000), "peer SASL failed")
                val peerName = hello(peer) ?: error("peer Hello failed")
                assertTrue(requestName(peer, ScreenCastPortal.PORTAL_SERVICE), "RequestName failed")

                val pump = Thread { runCatching { pumpPortal(peer, peerTransport, peerName, nodeId, "$runtimeDir/pipewire-0") } }
                    .apply { isDaemon = true; start() }

                val capturePort = LinuxCapturePort(connectionFactory = { connect(bus.address) })
                capturePort.use { port ->
                    val reserved = port.reserve(
                        org.graphiks.kadre.internal.runtime.CapturePortTarget.HostChoice,
                        CaptureRequest(),
                    )
                    val reservation = (reserved as? KadreResult.Success<*>)?.value as? CapturePortReservation
                        ?: error("reservation failed: ${reserved.toString().take(300)}")
                    assertEquals("portal-screencast", reservation.source.key.namespace)
                    assertEquals(nodeId.toLong(), reservation.source.key.value)

                    val frames = ArrayList<CapturePortFrame>()
                    val firstFrame = java.util.concurrent.CountDownLatch(1)
                    val listener = object : CapturePortStreamListener {
                        override fun onFrame(frame: CapturePortFrame) {
                            synchronized(frames) { frames.add(frame) }
                            if (frames.size >= 5) firstFrame.countDown()
                        }

                        override fun onReconfigured(configuration: org.graphiks.kadre.capture.CaptureConfiguration) = Unit

                        override fun onTerminated(termination: org.graphiks.kadre.internal.runtime.CapturePortTermination) {
                            println("CAPTURE-STREAM-O3 terminated=$termination")
                        }
                    }
                    val started = reservation.start(listener, maxFrameBytes = 4L * 1024 * 1024)
                    val streamStart = (started as? KadreResult.Success<*>)?.value as? CapturePortStreamStart
                        ?: error("stream start failed: ${started.toString().take(300)}")
                    assertEquals(PixelFormat.Bgrx8, streamStart.configuration.format)
                    assertEquals(320, streamStart.configuration.size.width)
                    assertEquals(240, streamStart.configuration.size.height)

                    assertTrue(firstFrame.await(20, java.util.concurrent.TimeUnit.SECONDS), "no frames arrived")
                    reservation.close()
                    pump.join(2_000)

                    val received = synchronized(frames) { frames.toList() }
                    assertTrue(received.size >= 5, "expected >=5 frames, got ${received.size}")
                    val frame = received.last()
                    assertEquals(320, frame.size.width)
                    assertEquals(240, frame.size.height)
                    assertEquals(1, frame.planes.size)
                    val plane = frame.planes[0]
                    assertTrue(plane.layout.rowStride >= 320 * 4, "stride=${plane.layout.rowStride}")
                    assertTrue(plane.bytes.size >= 320 * 4 * 240)
                    // videotestsrc paints real geometry, not a flat fill
                    val distinct = plane.bytes.copyOf(320 * 4 * 8).toHashSet().size
                    assertTrue(distinct > 16, "frame looks flat ($distinct distinct byte values)")

                    println("CAPTURE-STREAM-O3 frames=${received.size} size=${frame.size.width}x${frame.size.height} stride=${plane.layout.rowStride} bytes=${plane.bytes.size}")
                }
            }
        } catch (t: Throwable) {
            println("CAPTURE-STREAM-O3 failure: ${t.message?.take(200)}")
            throw t
        } finally {
            bus.stop()
        }

        // teardown honesty: every fd the chain opened (bus sockets, the
        // reservation's fd) is closed again — small slack for the JVM itself
        val fdsAfter = File("/proc/self/fd").list()!!.size
        assertTrue(fdsAfter <= fdsBefore + 2, "fd leak: before=$fdsBefore after=$fdsAfter")
        println("CAPTURE-STREAM-O3 fds $fdsBefore -> $fdsAfter")
    }

    /** The scripted portal peer answering the whole ScreenCast chain. */
    private fun pumpPortal(
        peer: DbusConnection,
        peerTransport: KffiDbusTransport,
        peerName: String,
        nodeId: Int,
        pipewireSocket: String,
    ) {
        var serial = 20_000
        while (true) {
            val call = peer.pollMessage(20_000) ?: run {
                return
            }
            if (call.type != DbusMessage.TYPE_METHOD_CALL) continue
            val sender = call.sender()?.removePrefix(":")?.replace('.', '_') ?: continue
            // OpenPipeWireRemote replies directly with the fd — no request
            // handle, no Response signal
            if (call.member() == "OpenPipeWireRemote") {
                val pipewireFd = runCatching { openUnixSocket(pipewireSocket) }.getOrNull()
                if (pipewireFd == null) {
                    continue
                }
                // libdbus shape (verified against a marshalled reply):
                // a destination field naming the caller, the unix-fds count,
                // and the no-reply-expected flag on returns
                val reply = DbusMessage(
                    type = DbusMessage.TYPE_METHOD_RETURN,
                    flags = DbusMessage.FLAG_NO_REPLY_EXPECTED,
                    serial = ++serial,
                    fields = mapOf(
                        DbusMessage.FIELD_REPLY_SERIAL to DbusValue.Uint32Value(call.serial.toLong()),
                        DbusMessage.FIELD_DESTINATION to DbusValue.StringValue(call.sender() ?: ""),
                        DbusMessage.FIELD_UNIX_FDS to DbusValue.Uint32Value(1),
                    ),
                    bodySignature = "h",
                    body = listOf(DbusValue.UnixFdValue(0)),
                ).encode()
                peerTransport.sendWithUnixFd(reply, pipewireFd)
                org.graphiks.kffi.posix.LinuxPosix.close(pipewireFd)
                peerTransport.sendWithUnixFd(reply, pipewireFd)
                org.graphiks.kffi.posix.LinuxPosix.close(pipewireFd)
                continue
            }
            val token = handleToken(call) ?: run {
                continue
            }
            val requestPath = "/org/freedesktop/portal/desktop/request/$sender/$token"
            when (call.member()) {
                "CreateSession" -> {
                    // bisect: the Response signal alone (the method return is
                    // the suspected protocol offender)
                    peer.send(
                        responseSignal(
                            requestPath,
                            0,
                            "session_handle" to DbusValue.VariantValue(
                                "o",
                                DbusValue.ObjectPathValue(requestPath.replace("/request/", "/session/")),
                            ),
                        ),
                    )
                }
                "SelectSources" -> peer.send(responseSignal(requestPath, 0))
                "Start" -> peer.send(
                    responseSignal(
                        requestPath,
                        0,
                        "streams" to DbusValue.VariantValue(
                            "aa{sv}",
                            DbusValue.ArrayValue(
                                elementSignature = "a{sv}",
                                elements = listOf(
                                    DbusValue.ArrayValue(
                                        elementSignature = "{sv}",
                                        elements = listOf(
                                            dictEntry("source_id", DbusValue.VariantValue("u", DbusValue.Uint32Value(nodeId.toLong()))),
                                            dictEntry("source_type", DbusValue.VariantValue("u", DbusValue.Uint32Value(1))),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                else -> Unit
            }
        }
    }

    private fun responseSignal(path: String, code: Int, vararg results: Pair<String, DbusValue>): DbusMessage =
        DbusMessage(
            type = DbusMessage.TYPE_SIGNAL,
            flags = 0,
            serial = 90_000 + code,
            fields = mapOf(
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue(path),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue(ScreenCastPortal.REQUEST_INTERFACE),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("Response"),
            ),
            bodySignature = "ua{sv}",
            body = listOf(
                DbusValue.Uint32Value(code.toLong()),
                DbusValue.ArrayValue(
                    elementSignature = "{sv}",
                    elements = results.map { (key, value) ->
                        val variant = value as? DbusValue.VariantValue ?: DbusValue.VariantValue(DbusMessage.signatureOf(value), value)
                        dictEntry(key, variant)
                    },
                ),
            ),
        )

    private fun dictEntry(key: String, variant: DbusValue.VariantValue) =
        DbusValue.DictEntryValue(DbusValue.StringValue(key), variant)

    private fun handleToken(message: DbusMessage): String? {
        fun scan(value: DbusValue): String? = when (value) {
            is DbusValue.DictEntryValue ->
                if ((value.key as? DbusValue.StringValue)?.value == "handle_token") {
                    ((value.value as? DbusValue.VariantValue)?.value as? DbusValue.StringValue)?.value
                } else {
                    null
                }
            is DbusValue.ArrayValue -> value.elements.firstNotNullOfOrNull(::scan)
            is DbusValue.VariantValue -> scan(value.value)
            else -> null
        }
        return message.body.firstNotNullOfOrNull(::scan)
    }

    private fun hello(connection: DbusConnection): String? {
        val reply = connection.call(
            DbusMessage(
                type = DbusMessage.TYPE_METHOD_CALL,
                flags = 0,
                serial = 0,
                fields = mapOf(
                    DbusMessage.FIELD_DESTINATION to DbusValue.StringValue("org.freedesktop.DBus"),
                    DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/org/freedesktop/DBus"),
                    DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("org.freedesktop.DBus"),
                    DbusMessage.FIELD_MEMBER to DbusValue.StringValue("Hello"),
                ),
                bodySignature = null,
                body = emptyList(),
            ),
            5_000,
        )
        return (reply.reply?.body?.firstOrNull() as? DbusValue.StringValue)?.value
    }

    private fun requestName(connection: DbusConnection, name: String): Boolean {
        val reply = connection.call(
            DbusMessage(
                type = DbusMessage.TYPE_METHOD_CALL,
                flags = 0,
                serial = 0,
                fields = mapOf(
                    DbusMessage.FIELD_DESTINATION to DbusValue.StringValue("org.freedesktop.DBus"),
                    DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/org/freedesktop/DBus"),
                    DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("org.freedesktop.DBus"),
                    DbusMessage.FIELD_MEMBER to DbusValue.StringValue("RequestName"),
                ),
                bodySignature = "su",
                body = listOf(DbusValue.StringValue(name), DbusValue.Uint32Value(4)),
            ),
            5_000,
        )
        val code = (reply.reply?.body?.firstOrNull() as? DbusValue.Uint32Value)?.value
        return code == 1L || code == 4L
    }
}
