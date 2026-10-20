package org.graphiks.kadre.internal.linux.dbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.internal.linux.portal.ScreenCastPortal

/**
 * O2 proofs of the D-Bus connection over a scripted transport (design
 * D2/D5): SASL EXTERNAL with fd negotiation, serial correlation,
 * error replies, signal dispatch and bounded waits. No socket, no
 * daemon — the peer is a queue of programmed chunks.
 */
class DbusConnectionTest {

    /** A transport whose peer answers with a script, one chunk per poll. */
    private class ScriptedTransport : DbusTransport {
        val sent = ArrayList<ByteArray>()
        private val incoming = ArrayDeque<ByteArray>()
        var closed = false

        fun enqueue(bytes: ByteArray) {
            incoming.add(bytes)
        }

        fun enqueueChunk(bytes: ByteArray, unixFds: IntArray) {
            incoming.add(bytes)
            fdBatches.add(unixFds)
        }

        private val fdBatches = ArrayDeque<IntArray>()

        fun enqueueLine(line: String) = enqueue((line + "\r\n").toByteArray())

        override fun send(bytes: ByteArray): Boolean {
            sent.add(bytes)
            return true
        }

        override fun receive(timeoutMillis: Long): org.graphiks.kadre.internal.linux.dbus.DbusChunk? {
            val bytes = incoming.removeFirstOrNull() ?: ByteArray(0)
            // fds ride the chunk that carries the frame they annotate
            return org.graphiks.kadre.internal.linux.dbus.DbusChunk(bytes, fdBatches.removeFirstOrNull() ?: IntArray(0))
        }
        override fun close() {
            closed = true
        }
    }

    private fun methodReturn(replySerial: Int, vararg body: DbusValue): ByteArray =
        DbusMessage(
            type = DbusMessage.TYPE_METHOD_RETURN,
            flags = 0,
            serial = 900 + replySerial,
            fields = mapOf(DbusMessage.FIELD_REPLY_SERIAL to DbusValue.Uint32Value(replySerial.toLong())),
            bodySignature = body.joinToString("") { DbusMessage.signatureOf(it) }.takeIf { body.isNotEmpty() },
            body = body.toList(),
        ).encode()

    private fun responseSignal(path: String, code: Int, results: Map<String, DbusValue>): ByteArray =
        DbusMessage(
            type = DbusMessage.TYPE_SIGNAL,
            flags = 0,
            serial = 500,
            fields = mapOf(
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue(path),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue(ScreenCastPortal.REQUEST_INTERFACE),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("Response"),
            ),
            bodySignature = "ua{sv}",
            body = listOf(
                DbusValue.Uint32Value(code.toLong()),
                DbusValue.ArrayValue(
                    "{sv}",
                    results.map { (key, value) ->
                        DbusValue.DictEntryValue(DbusValue.StringValue(key), DbusValue.VariantValue(DbusMessage.signatureOf(value), value))
                    },
                ),
            ),
        ).encode()

    @Test
    fun saslHandshakeNegotiatesUnixFds() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK 0123abcd")
        transport.enqueueLine("AGREE_UNIX_FD")
        val connection = DbusConnection(transport, uidProvider = { 1000 })
        assertTrue(connection.performHandshake(1_000))
        assertTrue(connection.negotiatesUnixFds)
        // \0, AUTH EXTERNAL with hex uid, NEGOTIATE, BEGIN
        assertEquals(0, transport.sent[0][0])
        val auth = String(transport.sent[1], Charsets.US_ASCII).trim()
        // hex of the ASCII decimal "1000"
        assertEquals("AUTH EXTERNAL 31303030", auth)
        assertEquals("NEGOTIATE_UNIX_FD", String(transport.sent[2], Charsets.US_ASCII).trim())
        assertEquals("BEGIN", String(transport.sent[3], Charsets.US_ASCII).trim())
    }

    @Test
    fun refusedUnixFdNegotiationStillAuthenticates() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK guid")
        transport.enqueueLine("ERROR")
        val connection = DbusConnection(transport, uidProvider = { 7 })
        assertTrue(connection.performHandshake(1_000))
        assertEquals(false, connection.negotiatesUnixFds)
    }

    @Test
    fun callCorrelatesItsReplyBySerial() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK guid")
        val connection = DbusConnection(transport, uidProvider = { 1 })
        assertTrue(connection.performHandshake(1_000))

        // the call gets serial 1 (the counter starts there); reply targets it
        transport.enqueue(methodReturn(1, DbusValue.ObjectPathValue("/req/1")))
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 0,
            fields = mapOf(DbusMessage.FIELD_MEMBER to DbusValue.StringValue("M")),
            bodySignature = null,
            body = emptyList(),
        )
        val replied = connection.call(call, 1_000)
        assertNotNull(replied.reply)
        assertEquals("/req/1", (replied.reply.body.single() as DbusValue.ObjectPathValue).value)
    }

    @Test
    fun callDeliversTheReplyFrameUnixFds() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK guid")
        val connection = DbusConnection(transport, uidProvider = { 1 })
        assertTrue(connection.performHandshake(1_000))

        // the portal-style reply: one 'h' body plus the SCM_RIGHTS batch
        transport.enqueueChunk(methodReturn(1, DbusValue.UnixFdValue(0)), intArrayOf(777))
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 0,
            fields = mapOf(DbusMessage.FIELD_MEMBER to DbusValue.StringValue("OpenPipeWireRemote")),
            bodySignature = null,
            body = emptyList(),
        )
        val replied = connection.call(call, 1_000)
        assertNotNull(replied.reply)
        assertEquals(0, (replied.reply.body.single() as DbusValue.UnixFdValue).index)
        assertEquals(listOf(777), replied.unixFds)
        assertEquals(listOf(777), connection.receivedUnixFds)
    }

    @Test
    fun unrelatedSerialsDoNotAnswerTheCall() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK guid")
        val connection = DbusConnection(transport, uidProvider = { 1 })
        assertTrue(connection.performHandshake(1_000))

        transport.enqueue(methodReturn(42, DbusValue.Uint32Value(1)))
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 0,
            fields = mapOf(DbusMessage.FIELD_MEMBER to DbusValue.StringValue("M")),
            bodySignature = null,
            body = emptyList(),
        )
        val replied = connection.call(call, 200)
        assertNull(replied.reply)
        assertNull(replied.error)
    }

    @Test
    fun errorRepliesSurfaceAsErrors() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK guid")
        val connection = DbusConnection(transport, uidProvider = { 1 })
        assertTrue(connection.performHandshake(1_000))
        transport.enqueue(
            DbusMessage(
                type = DbusMessage.TYPE_ERROR,
                flags = 0,
                serial = 99,
                fields = mapOf(
                    DbusMessage.FIELD_REPLY_SERIAL to DbusValue.Uint32Value(1),
                    DbusMessage.FIELD_ERROR_NAME to DbusValue.StringValue("org.freedesktop.DBus.Error.ServiceUnknown"),
                ),
                bodySignature = "s",
                body = listOf(DbusValue.StringValue("nope")),
            ).encode(),
        )
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 0,
            fields = mapOf(DbusMessage.FIELD_MEMBER to DbusValue.StringValue("M")),
            bodySignature = null,
            body = emptyList(),
        )
        val replied = connection.call(call, 1_000)
        assertNull(replied.reply)
        assertNotNull(replied.error)
        assertEquals("org.freedesktop.DBus.Error.ServiceUnknown", replied.error.errorName())
    }

    @Test
    fun portalResponseCorrelatesByRequestPath() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK guid")
        val connection = DbusConnection(transport, uidProvider = { 1 })
        assertTrue(connection.performHandshake(1_000))

        val portal = ScreenCastPortal(connection, uniqueNameProvider = { ":1.42" })
        // serial 1: the portal's AddMatch (to the bus itself); the
        // CreateSession call itself is fire-and-forget on the derived path
        transport.enqueue(methodReturn(1))
        val requestPath = "/org/freedesktop/portal/desktop/request/1_42/kadre_session0"
        transport.enqueue(
            responseSignal(
                requestPath,
                0,
                mapOf("session_handle" to DbusValue.ObjectPathValue("/org/freedesktop/portal/desktop/session/1_42/s1")),
            ),
        )
        val session = portal.createSession(1_000)
        assertTrue(session.outcome is ScreenCastPortal.StepOutcome.Success)
        assertEquals("/org/freedesktop/portal/desktop/session/1_42/s1", session.sessionPath)
    }

    @Test
    fun refusedPickerSurfacesAsRefusal() {
        val transport = ScriptedTransport()
        transport.enqueueLine("OK guid")
        val connection = DbusConnection(transport, uidProvider = { 1 })
        assertTrue(connection.performHandshake(1_000))
        val portal = ScreenCastPortal(connection, uniqueNameProvider = { ":1.42" })
        transport.enqueue(methodReturn(1))
        val requestPath = "/org/freedesktop/portal/desktop/request/1_42/kadre_session0"
        transport.enqueue(responseSignal(requestPath, 1, emptyMap()))
        val session = portal.createSession(1_000)
        assertTrue(session.outcome is ScreenCastPortal.StepOutcome.Refused)
        assertNull(session.sessionPath)
    }
}
