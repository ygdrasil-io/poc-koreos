package org.graphiks.kadre.internal.linux

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kadre.internal.linux.capture.LinuxCapturePort
import org.graphiks.kadre.internal.linux.dbus.DbusConnection
import org.graphiks.kadre.internal.linux.dbus.DbusMessage
import org.graphiks.kadre.internal.linux.dbus.DbusValue
import org.graphiks.kadre.internal.linux.dbus.KffiDbusTransport
import org.graphiks.kadre.internal.linux.portal.ScreenCastPortal
import org.graphiks.kadre.internal.runtime.CapturePortSources

/**
 * O3 proofs of the capture control plane against a REAL session bus
 * (design D6): dbus-daemon on a private socket, the production
 * kffi-posix transport, the D-Bus codec and SASL EXTERNAL on the wire,
 * and a scripted portal peer driving the ScreenCast correlation
 * end-to-end. Linux-only — the dedicated linuxEvdevTests-style task
 * runs it where dbus-daemon exists.
 */
class LinuxCapturePortalLinuxTest {

    private fun daemonAvailable(): Boolean =
        System.getProperty("os.name") == "Linux" &&
            runCatching {
                ProcessBuilder("dbus-daemon", "--version").start().waitFor() == 0
            }.getOrDefault(false)

    private class Bus(
        val address: String,
        val process: Process,
    ) {
        fun stop() {
            process.destroy()
            process.waitFor()
        }
    }

    private fun startBus(): Bus? {
        if (!daemonAvailable()) return null
        val socket = File("/tmp/kadre-dbus-${System.nanoTime()}.bus")
        val process = ProcessBuilder(
            "dbus-daemon",
            "--session",
            "--address=unix:path=${socket.absolutePath}",
            "--nofork",
            "--nopidfile",
        ).apply {
            environment().remove("DBUS_SESSION_BUS_ADDRESS")
            environment().remove("DBUS_STARTER_ADDRESS")
            environment().remove("DBUS_STARTER_BUS_TYPE")
        }.start()
        // wait for the socket to exist
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

    private fun connect(address: String): DbusConnection? {
        // the socket file can exist a beat before the daemon listens
        val deadline = System.currentTimeMillis() + 3_000
        while (true) {
            val transport = KffiDbusTransport.connectToSessionBus(address)
            if (transport != null) return DbusConnection(transport, uidProvider = { LinuxCapturePort.effectiveUid() })
            if (System.currentTimeMillis() > deadline) return null
            Thread.sleep(25)
        }
    }

    @Test
    fun realBusAuthenticatesAndObservesTheMissingPortal() {
        val bus = startBus() ?: return
        try {
            val capturePort = LinuxCapturePort(connectionFactory = { connect(bus.address) })
            try {
                val sources = capturePort.initialSnapshot.sources
                val unavailable = sources as? CapturePortSources.Unavailable
                    ?: error("expected Unavailable on a portal-less bus, got $sources")
                assertTrue(unavailable.failure.toString().contains("portal-absent"), "failure=${unavailable.failure}")
                println("CAPTURE-O3 portal-absent=${unavailable.failure}")
            } finally {
                capturePort.close()
            }
        } finally {
            bus.stop()
        }
    }

    @Test
    fun scriptedPortalDrivesScreenCastEndToEnd() {
        val bus = startBus() ?: return
        try {
            // the peer: connect, Hello, own the portal name, answer calls
            val peer = connect(bus.address) ?: error("peer connect failed")
            peer.use { peerConnection ->
                assertTrue(peer.performHandshake(5_000), "peer SASL failed")
                val peerName = hello(peerConnection) ?: error("peer Hello failed")
                assertTrue(requestName(peerConnection, ScreenCastPortal.PORTAL_SERVICE), "RequestName failed")

                val client = connect(bus.address) ?: error("client connect failed")
                client.use { clientConnection ->
                    assertTrue(client.performHandshake(5_000), "client SASL failed")
                    val clientName = hello(clientConnection) ?: error("client Hello failed")
                    assertTrue(clientName.startsWith(":"), "expected a unique name, got $clientName")
                    // does the bus really see the portal name owned?
                    val ownerCall = DbusMessage(
                        type = DbusMessage.TYPE_METHOD_CALL,
                        flags = 0,
                        serial = 0,
                        fields = mapOf(
                            DbusMessage.FIELD_DESTINATION to DbusValue.StringValue("org.freedesktop.DBus"),
                            DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/org/freedesktop/DBus"),
                            DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("org.freedesktop.DBus"),
                            DbusMessage.FIELD_MEMBER to DbusValue.StringValue("GetNameOwner"),
                        ),
                        bodySignature = "s",
                        body = listOf(DbusValue.StringValue(ScreenCastPortal.PORTAL_SERVICE)),
                    )
                    val ownerReply = clientConnection.call(ownerCall, 2_000)

                    // answer CreateSession from a pump thread
                    val thread = Thread {
                        runCatching {
                            // skip bus bookkeeping signals (NameAcquired…) until
                            // the portal call arrives
                            var call = peerConnection.pollMessage(10_000)
                            while (call != null && !(call.type == DbusMessage.TYPE_METHOD_CALL && call.member() == "CreateSession")) {
                                call = peerConnection.pollMessage(2_000)
                            }
                            if (call == null) {
                                return@runCatching
                            }
                            if (true) {
                                // the spec pins the request path to the CALLER's
                                // unique name + handle token
                                val requestPath = "/org/freedesktop/portal/desktop/request/${clientName.removePrefix(":").replace('.', '_')}/kadre_session0"
                                // 1. the method reply carries the request handle
                                val replySent = peerConnection.send(
                                    DbusMessage(
                                        type = DbusMessage.TYPE_METHOD_RETURN,
                                        flags = 0,
                                        serial = 7001,
                                        fields = mapOf(DbusMessage.FIELD_REPLY_SERIAL to DbusValue.Uint32Value(call.serial.toLong())),
                                        bodySignature = "o",
                                        body = listOf(DbusValue.ObjectPathValue(requestPath)),
                                    ),
                                )
                                // 2. the Response signal completes the request
                                val signalSent = peerConnection.send(
                                    DbusMessage(
                                        type = DbusMessage.TYPE_SIGNAL,
                                        flags = 0,
                                        serial = 7002,
                                        fields = mapOf(
                                            DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue(requestPath),
                                            DbusMessage.FIELD_INTERFACE to DbusValue.StringValue(ScreenCastPortal.REQUEST_INTERFACE),
                                            DbusMessage.FIELD_MEMBER to DbusValue.StringValue("Response"),
                                        ),
                                        bodySignature = "ua{sv}",
                                        body = listOf(
                                            DbusValue.Uint32Value(0),
                                            DbusValue.ArrayValue(
                                                "{sv}",
                                                listOf(
                                                    DbusValue.DictEntryValue(
                                                        DbusValue.StringValue("session_handle"),
                                                        DbusValue.VariantValue("o", DbusValue.ObjectPathValue("/org/freedesktop/portal/desktop/session/peer/s1")),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                    }.apply { isDaemon = true; start() }

                    val portal = ScreenCastPortal(clientConnection, uniqueNameProvider = { clientName })
                    val session = portal.createSession(5_000)
                    thread.join(5_000)
                    assertTrue(
                        session.outcome is ScreenCastPortal.StepOutcome.Success,
                        "outcome=${session.outcome}",
                    )
                    assertEquals("/org/freedesktop/portal/desktop/session/peer/s1", session.sessionPath)
                    println("CAPTURE-O3 session=${session.sessionPath}")
                }
            }
        } finally {
            bus.stop()
        }
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
