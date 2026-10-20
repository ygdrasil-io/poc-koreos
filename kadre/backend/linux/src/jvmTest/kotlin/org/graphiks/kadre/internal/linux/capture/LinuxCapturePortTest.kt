package org.graphiks.kadre.internal.linux.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.linux.dbus.DbusChunk
import org.graphiks.kadre.internal.linux.dbus.DbusConnection
import org.graphiks.kadre.internal.linux.dbus.DbusMessage
import org.graphiks.kadre.internal.linux.dbus.DbusTransport
import org.graphiks.kadre.internal.linux.dbus.DbusValue
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot

/**
 * O2 proofs of the capture control plane (design D1/D4 +
 * LINUX-PIPEWIRE-STREAM-DESIGN D9): availability is observed through the
 * scripted bus seam, and a HostChoice reservation walks the whole ScreenCast
 * chain — CreateSession, SelectSources, Start, OpenPipeWireRemote — ending
 * in a reservation that owns the fd and the node id. Stream negotiation
 * itself needs libpipewire and lives in the container oracle.
 */
class LinuxCapturePortTest {

    /** A scripted session bus playing org.freedesktop.portal.ScreenCast. */
    internal class BusScript(
        val authOk: Boolean,
        val portalOwned: Boolean,
        val pickerRefused: Boolean = false,
        val honestPipewireFd: Boolean = true,
        val sourceType: Long = 1,
    ) : DbusTransport {
        val sent = ArrayList<ByteArray>()
        var closed = false
        private val incoming = ArrayDeque<ByteArray>()
        private val fdBatches = ArrayDeque<IntArray>()

        override fun send(bytes: ByteArray): Boolean {
            sent.add(bytes)
            val text = String(bytes, Charsets.US_ASCII)
            return when {
                text.startsWith("AUTH") -> {
                    if (authOk) enqueueLine("OK guid")
                    true
                }
                text.startsWith("NEGOTIATE_UNIX_FD") -> {
                    enqueueLine("ERROR")
                    true
                }
                else -> true
            }
        }

        override fun receive(timeoutMillis: Long): DbusChunk? {
            val last = sent.lastOrNull() ?: return null
            if (last.size > 16) {
                val decoded = runCatching { DbusMessage.decode(last) }.getOrNull()
                if (decoded != null && incoming.isEmpty()) {
                    when (decoded.member()) {
                        "Hello" -> enqueue(methodReturn(decoded.serial, DbusValue.StringValue(":1.99")))
                        "AddMatch" -> enqueue(methodReturn(decoded.serial))
                        "GetNameOwner" ->
                            if (portalOwned) {
                                enqueue(methodReturn(decoded.serial, DbusValue.StringValue(":1.5")))
                            } else {
                                enqueue(error(decoded.serial, "org.freedesktop.DBus.Error.NameHasNoOwner"))
                            }
                        "CreateSession" -> {
                            val token = handleToken(decoded) ?: "kadre_session0"
                            enqueue(
                                responseSignal(
                                    path = "/org/freedesktop/portal/desktop/request/1_99/$token",
                                    code = 0,
                                    "session_handle" to variant("o", DbusValue.ObjectPathValue("/org/freedesktop/portal/desktop/session/1_99/$token")),
                                ),
                            )
                        }
                        "SelectSources" -> {
                            val token = handleToken(decoded) ?: "kadre0"
                            enqueue(
                                responseSignal(
                                    path = "/org/freedesktop/portal/desktop/request/1_99/$token",
                                    code = if (pickerRefused) 1 else 0,
                                ),
                            )
                        }
                        "Start" -> {
                            val token = handleToken(decoded) ?: "kadre1"
                            val stream = DbusValue.ArrayValue(
                                elementSignature = "{sv}",
                                elements = listOf(
                                    entry("source_id", variant("u", DbusValue.Uint32Value(42))),
                                    entry("source_type", variant("u", DbusValue.Uint32Value(sourceType))),
                                    entry("size", variant("(ii)", DbusValue.StructValue(listOf(DbusValue.Int32Value(640), DbusValue.Int32Value(480))))),
                                ),
                            )
                            enqueue(
                                responseSignal(
                                    path = "/org/freedesktop/portal/desktop/request/1_99/$token",
                                    code = 0,
                                    "streams" to variant("aa{sv}", DbusValue.ArrayValue(elementSignature = "a{sv}", elements = listOf(stream))),
                                ),
                            )
                        }
                        "OpenPipeWireRemote" -> {
                            enqueue(methodReturn(decoded.serial, DbusValue.UnixFdValue(0)))
                            if (honestPipewireFd) fdBatches.add(intArrayOf(777))
                        }
                        "Close" -> Unit // fire-and-forget: the portal expects no reply
                        else -> enqueue(error(decoded.serial, "org.freedesktop.DBus.Error.ServiceUnknown"))
                    }
                }
            }
            return DbusChunk(incoming.removeFirstOrNull() ?: ByteArray(0), fdBatches.removeFirstOrNull() ?: IntArray(0))
        }

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

        private fun variant(signature: String, value: DbusValue) = DbusValue.VariantValue(signature, value)

        private fun entry(key: String, value: DbusValue.VariantValue) =
            DbusValue.DictEntryValue(DbusValue.StringValue(key), value)

        private fun enqueue(bytes: ByteArray) {
            incoming.add(bytes)
        }

        private fun enqueueLine(line: String) = enqueue((line + "\r\n").toByteArray())

        private fun methodReturn(replySerial: Int, vararg body: DbusValue): ByteArray =
            DbusMessage(
                type = DbusMessage.TYPE_METHOD_RETURN,
                flags = 0,
                serial = 800 + replySerial,
                fields = mapOf(DbusMessage.FIELD_REPLY_SERIAL to DbusValue.Uint32Value(replySerial.toLong())),
                bodySignature = body.joinToString("") { DbusMessage.signatureOf(it) }.takeIf { body.isNotEmpty() },
                body = body.toList(),
            ).encode()

        private fun responseSignal(path: String, code: Int, vararg results: Pair<String, DbusValue.VariantValue>): ByteArray =
            DbusMessage(
                type = DbusMessage.TYPE_SIGNAL,
                flags = 0,
                serial = 900,
                fields = mapOf(
                    DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue(path),
                    DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("org.freedesktop.portal.Request"),
                    DbusMessage.FIELD_MEMBER to DbusValue.StringValue("Response"),
                ),
                bodySignature = "ua{sv}",
                body = listOf(
                    DbusValue.Uint32Value(code.toLong()),
                    DbusValue.ArrayValue(
                        elementSignature = "{sv}",
                        elements = results.map { (key, value) -> DbusValue.DictEntryValue(DbusValue.StringValue(key), value) },
                    ),
                ),
            ).encode()

        private fun error(replySerial: Int, name: String): ByteArray =
            DbusMessage(
                type = DbusMessage.TYPE_ERROR,
                flags = 0,
                serial = 801 + replySerial,
                fields = mapOf(
                    DbusMessage.FIELD_REPLY_SERIAL to DbusValue.Uint32Value(replySerial.toLong()),
                    DbusMessage.FIELD_ERROR_NAME to DbusValue.StringValue(name),
                ),
                bodySignature = "s",
                body = listOf(DbusValue.StringValue("nope")),
            ).encode()

        override fun close() {
            closed = true
        }
    }

    private fun port(script: BusScript): LinuxCapturePort =
        LinuxCapturePort(connectionFactory = { DbusConnection(script, uidProvider = { 1000 }) })

    private fun runPortTest(script: BusScript, block: suspend (LinuxCapturePort) -> Unit) {
        kotlinx.coroutines.test.runTest {
            val capturePort = port(script)
            try {
                block(capturePort)
            } finally {
                capturePort.close()
            }
        }
    }

    @Test
    fun livePortalPublishesHostPickerOnly() = runPortTest(BusScript(authOk = true, portalOwned = true)) {
        assertIs<CapturePortSources.HostPickerOnly>(it.initialSnapshot.sources)
    }

    @Test
    fun missingPortalNameOwnerIsUnavailableNotSilent() = runPortTest(BusScript(authOk = true, portalOwned = false)) {
        val unavailable = assertIs<CapturePortSources.Unavailable>(it.initialSnapshot.sources)
        assertTrue(unavailable.failure.toString().contains("portal-absent"), "failure=${unavailable.failure}")
    }

    @Test
    fun failedAuthIsUnavailable() = runPortTest(BusScript(authOk = false, portalOwned = true)) {
        val unavailable = assertIs<CapturePortSources.Unavailable>(it.initialSnapshot.sources)
        assertTrue(unavailable.failure.toString().contains("bus-auth-failed"))
    }

    @Test
    fun refreshReObservesTheBus() = runPortTest(BusScript(authOk = true, portalOwned = true)) {
        val refreshed = it.refreshSources()
        val success = assertIs<KadreResult.Success<*>>(refreshed)
        val snapshot = success.value as CapturePortSnapshot
        assertIs<CapturePortSources.HostPickerOnly>(snapshot.sources)
    }

    @Test
    fun sourceAndSurfaceTargetsStayRefused() = runPortTest(BusScript(authOk = true, portalOwned = true)) {
        val bySource = it.reserve(CapturePortTarget.Source(CapturePortSourceKey("portal", 1)), CaptureRequest())
        assertIs<KadreFailure.Unsupported>(assertIs<KadreResult.Failure>(bySource).reason)

        // Surface targets stay refused too — constructed reflectively
        // since the ids are runtime-allocated
        val surfaceId = org.graphiks.kadre.surface.SurfaceId::class.java.getDeclaredConstructor(Long::class.java).apply { isAccessible = true }.newInstance(1L)
        val bySurface = it.reserve(CapturePortTarget.Surface(surfaceId), CaptureRequest())
        assertIs<KadreResult.Failure>(bySurface)
    }

    @Test
    fun hostChoiceReservationWalksThePortalToTheNodeAndFd() {
        val script = BusScript(authOk = true, portalOwned = true)
        runPortTest(script) {
            val reserved = it.reserve(CapturePortTarget.HostChoice, CaptureRequest())
            val reservation = assertIs<KadreResult.Success<*>>(reserved).value as CapturePortReservation
            assertEquals(CapturePortSourceKey("portal-screencast", 42), reservation.source.key)
            assertEquals(CaptureSourceKind.Display, reservation.source.kind)
            assertEquals(640, reservation.source.size?.width)
            assertEquals(480, reservation.source.size?.height)
            reservation.close()
            reservation.close()
            // the session Close rode the bus during teardown, idempotently
            val closeCalls = script.sent.count { bytes ->
                bytes.size > 16 && runCatching { DbusMessage.decode(bytes).member() == "Close" }.getOrDefault(false)
            }
            assertEquals(1, closeCalls)
            assertTrue(script.closed, "the reservation owns its connection")
        }
    }

    @Test
    fun windowKindMapsFromThePortalSourceType() = runPortTest(BusScript(authOk = true, portalOwned = true, sourceType = 2)) {
        val reserved = it.reserve(CapturePortTarget.HostChoice, CaptureRequest())
        val reservation = assertIs<KadreResult.Success<*>>(reserved).value as CapturePortReservation
        assertEquals(CaptureSourceKind.Window, reservation.source.kind)
        reservation.close()
    }

    @Test
    fun refusedPickerSurfacesAsPermissionDenied() = runPortTest(BusScript(authOk = true, portalOwned = true, pickerRefused = true)) {
        val reserved = it.reserve(CapturePortTarget.HostChoice, CaptureRequest())
        val failure = assertIs<KadreResult.Failure>(reserved).reason
        assertIs<KadreFailure.PermissionDenied>(failure)
    }

    @Test
    fun dishonestFdIndexRefusesTheReservation() = runPortTest(BusScript(authOk = true, portalOwned = true, honestPipewireFd = false)) {
        val reserved = it.reserve(CapturePortTarget.HostChoice, CaptureRequest())
        val failure = assertIs<KadreResult.Failure>(reserved).reason
        val platform = assertIs<KadreFailure.PlatformFailure>(failure)
        assertEquals("pipewire-remote-refused", platform.code)
    }
}

/** TEMPORARY wire debug: hexdumps the SelectSources frame. */
class ScratchWireDumpTest {
    @Test
    fun dumpSelectSourcesBytes() {
        val script = LinuxCapturePortTest.BusScript(authOk = true, portalOwned = true)
        kotlinx.coroutines.test.runTest {
            val capturePort = LinuxCapturePort(connectionFactory = { DbusConnection(script, uidProvider = { 1000 }) })
            try {
                capturePort.reserve(CapturePortTarget.HostChoice, CaptureRequest())
            } catch (_: Throwable) {
            } finally {
                capturePort.close()
            }
        }
        val body = DbusMessage.encodeBody(
            "a{sv}",
            listOf(
                DbusValue.ArrayValue(
                    "{sv}",
                    listOf(
                        DbusValue.DictEntryValue(
                            DbusValue.StringValue("handle_token"),
                            DbusValue.VariantValue("s", DbusValue.StringValue("kadre1")),
                        ),
                        DbusValue.DictEntryValue(
                            DbusValue.StringValue("multiple"),
                            DbusValue.VariantValue("b", DbusValue.BooleanValue(false)),
                        ),
                    ),
                ),
            ),
            java.nio.ByteOrder.LITTLE_ENDIAN,
        )
        println("WIRE body " + body.size + " hex " + body.joinToString("") { "%02x".format(it) })
    }
}
