package org.graphiks.kadre.internal.linux.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.linux.dbus.DbusConnection
import org.graphiks.kadre.internal.linux.dbus.DbusMessage
import org.graphiks.kadre.internal.linux.dbus.DbusTransport
import org.graphiks.kadre.internal.linux.dbus.DbusValue
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget


/**
 * O2 proofs of the capture control plane (design D1/D4): availability is
 * observed through the scripted bus seam — auth failure, missing portal
 * name owner and a live portal each produce their exact snapshot — and
 * reservations stay refused until the PipeWire stream tranche lands.
 */
class LinuxCapturePortTest {

    private class BusScript(
        val authOk: Boolean,
        val portalOwned: Boolean,
    ) : DbusTransport {
        val sent = ArrayList<ByteArray>()
        private val incoming = ArrayDeque<ByteArray>()

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

        override fun receive(timeoutMillis: Long): org.graphiks.kadre.internal.linux.dbus.DbusChunk? {
            val last = sent.lastOrNull() ?: return null
            if (last.size > 16) {
                val decoded = runCatching { DbusMessage.decode(last) }.getOrNull()
                if (decoded != null && incoming.isEmpty()) {
                    when (decoded.member()) {
                        "Hello" -> enqueue(methodReturn(decoded.serial, DbusValue.StringValue(":1.99")))
                        "GetNameOwner" ->
                            if (portalOwned) {
                                enqueue(methodReturn(decoded.serial, DbusValue.StringValue(":1.5")))
                            } else {
                                enqueue(error(decoded.serial, "org.freedesktop.DBus.Error.NameHasNoOwner"))
                            }
                        else -> enqueue(error(decoded.serial, "org.freedesktop.DBus.Error.ServiceUnknown"))
                    }
                }
            }
            return org.graphiks.kadre.internal.linux.dbus.DbusChunk(incoming.removeFirstOrNull() ?: ByteArray(0))
        }

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

        override fun close() = Unit
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
        val snapshot = success.value as org.graphiks.kadre.internal.runtime.CapturePortSnapshot
        assertIs<CapturePortSources.HostPickerOnly>(snapshot.sources)
    }

    @Test
    fun reservationsStayRefusedUntilPipeWireLands() = runPortTest(BusScript(authOk = true, portalOwned = true)) {
        val hostChoice = it.reserve(CapturePortTarget.HostChoice, CaptureRequest())
        val pending = assertIs<KadreResult.Failure>(hostChoice)
        assertIs<KadreFailure.PlatformFailure>(pending.reason)
        assertTrue(pending.reason.toString().contains("pipewire-stream-pending"))

        val bySource = it.reserve(CapturePortTarget.Source(CapturePortSourceKey("portal", 1)), CaptureRequest())
        assertIs<KadreFailure.Unsupported>(assertIs<KadreResult.Failure>(bySource).reason)

        // Surface targets stay refused too — constructed reflectively
        // since the ids are runtime-allocated
        val surfaceId = org.graphiks.kadre.surface.SurfaceId::class.java.getDeclaredConstructor(Long::class.java).apply { isAccessible = true }.newInstance(1L)
        val bySurface = it.reserve(CapturePortTarget.Surface(surfaceId), CaptureRequest())
        assertIs<KadreResult.Failure>(bySurface)
    }
}
