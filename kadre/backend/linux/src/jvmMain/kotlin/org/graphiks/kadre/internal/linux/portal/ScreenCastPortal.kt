package org.graphiks.kadre.internal.linux.portal

import org.graphiks.kadre.internal.linux.dbus.DbusConnection
import org.graphiks.kadre.internal.linux.dbus.DbusMessage
import org.graphiks.kadre.internal.linux.dbus.DbusValue

/**
 * The xdg-desktop-portal ScreenCast control plane (design
 * LINUX-CAPTURE-PORTAL-DESIGN T2): CreateSession → SelectSources →
 * Start → OpenPipeWireRemote, each method returning a request handle
 * whose `Response` signal (code, results) completes the step. Timeouts
 * are bounded; every step closes its request on teardown.
 */
internal class ScreenCastPortal(
    private val connection: DbusConnection,
    private val uniqueNameProvider: () -> String,
) {
    init {
        // broadcast signals only reach receivers with a match rule; the
        // bus drops them silently otherwise
        installRequestMatch()
    }

    private fun installRequestMatch() {
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 0,
            fields = mapOf(
                DbusMessage.FIELD_DESTINATION to DbusValue.StringValue("org.freedesktop.DBus"),
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue("/org/freedesktop/DBus"),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue("org.freedesktop.DBus"),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("AddMatch"),
            ),
            bodySignature = "s",
            body = listOf(
                DbusValue.StringValue("type='signal',interface='$REQUEST_INTERFACE',member='Response'"),
            ),
        )
        connection.call(call, 2_000)
    }
    sealed interface StepOutcome {
        /** Response code 0 with the results dictionary. */
        data class Success(val results: Map<String, DbusValue>) : StepOutcome
        /** Response code non-zero (user refused the picker, cancelled…). */
        data class Refused(val code: Int, val results: Map<String, DbusValue>) : StepOutcome
        /** The bus, the service or the budget went away. */
        data object Failed : StepOutcome
    }

    private var tokenCounter = 0

    /** Observes the Response signals of the portal request objects. */
    private fun awaitResponse(requestPath: String, timeoutMillis: Long): StepOutcome {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return StepOutcome.Failed
            val message = connection.pollMessage(remaining) ?: return StepOutcome.Failed
            if (message.type != DbusMessage.TYPE_SIGNAL) continue
            if (message.path() != requestPath) continue
            if (message.interfaceName() != REQUEST_INTERFACE || message.member() != "Response") continue
            if (message.body.size != 2) continue
            val code = (message.body[0] as? DbusValue.Uint32Value)?.value?.toInt() ?: continue
            val results = readResults(message.body[1])
            return if (code == 0) StepOutcome.Success(results) else StepOutcome.Refused(code, results)
        }
    }

    private fun readResults(value: DbusValue): Map<String, DbusValue> {
        val array = value as? DbusValue.ArrayValue ?: return emptyMap()
        val results = LinkedHashMap<String, DbusValue>()
        array.elements.forEach { element ->
            val entry = element as? DbusValue.DictEntryValue ?: return@forEach
            val key = (entry.key as? DbusValue.StringValue)?.value ?: return@forEach
            val variant = entry.value as? DbusValue.VariantValue ?: return@forEach
            results[key] = variant.value
        }
        return results
    }

    /** One portal method call plus its Response correlation. */
    private fun portalCall(
        member: String,
        path: String,
        bodySignature: String,
        body: List<DbusValue>,
        timeoutMillis: Long,
    ): StepOutcome {
        val token = "kadre${tokenCounter++}"
        val senderPath = uniqueNameProvider().removePrefix(":").replace('.', '_')
        val expectedRequestPath = "$REQUEST_ROOT/$senderPath/$token"
        // fire-and-forget: the spec pins the request path to
        // /…/request/SENDER/TOKEN, so correlation rides the Response
        // signal alone (no method_return needed)
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = DbusMessage.FLAG_NO_REPLY_EXPECTED,
            serial = 0,
            fields = mapOf(
                DbusMessage.FIELD_DESTINATION to DbusValue.StringValue(PORTAL_SERVICE),
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue(path),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue(SCREENCAST_INTERFACE),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue(member),
            ),
            bodySignature = bodySignature,
            body = body,
        )
        if (!connection.send(call)) return StepOutcome.Failed
        return awaitResponse(expectedRequestPath, timeoutMillis)
    }

    /** CreateSession: yields the session_handle in the Response results. */
    fun createSession(timeoutMillis: Long = 5_000): SessionHandle {
        val token = "kadre_session${tokenCounter++}"
        val senderPath = uniqueNameProvider().removePrefix(":").replace('.', '_')
        val expectedSession = "$SESSION_ROOT/$senderPath/$token"
        // fire-and-forget like the other portal calls: the request path is
        // pinned to /…/request/SENDER/TOKEN, correlation rides the signal
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = DbusMessage.FLAG_NO_REPLY_EXPECTED,
            serial = 0,
            fields = mapOf(
                DbusMessage.FIELD_DESTINATION to DbusValue.StringValue(PORTAL_SERVICE),
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue(SCREENCAST_PATH),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue(SCREENCAST_INTERFACE),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("CreateSession"),
            ),
            bodySignature = "a{sv}",
            body = listOf(
                optionsDictionary("handle_token" to DbusValue.VariantValue("s", DbusValue.StringValue(token))),
            ),
        )
        if (!connection.send(call)) return SessionHandle(null, StepOutcome.Failed)
        val requestPath = "$REQUEST_ROOT/$senderPath/$token"
        val outcome = awaitResponse(requestPath, timeoutMillis)
        val session = when (outcome) {
            is StepOutcome.Success -> (outcome.results["session_handle"] as? DbusValue.ObjectPathValue)?.value
            else -> null
        }
        return SessionHandle(session ?: expectedSession.takeIf { outcome is StepOutcome.Success }, outcome)
    }

    fun selectSources(sessionHandle: String, timeoutMillis: Long = 15_000): StepOutcome =
        portalCall(
            member = "SelectSources",
            path = sessionHandle,
            bodySignature = "oa{sv}",
            body = listOf(
                DbusValue.ObjectPathValue(sessionHandle),
                optionsDictionary(
                    "handle_token" to DbusValue.VariantValue("s", DbusValue.StringValue("kadre${tokenCounter++}")),
                    "multiple" to DbusValue.VariantValue("b", DbusValue.BooleanValue(false)),
                ),
            ),
            timeoutMillis = timeoutMillis,
        )

    fun start(sessionHandle: String, timeoutMillis: Long = 60_000): StepOutcome =
        portalCall(
            member = "Start",
            path = sessionHandle,
            bodySignature = "osa{sv}",
            body = listOf(
                DbusValue.ObjectPathValue(sessionHandle),
                DbusValue.StringValue(""), // parent window: none
                optionsDictionary(
                    "handle_token" to DbusValue.VariantValue("s", DbusValue.StringValue("kadre${tokenCounter++}")),
                ),
            ),
            timeoutMillis = timeoutMillis,
        )

    /**
     * OpenPipeWireRemote: the actual fd (SCM_RIGHTS duplicate), owned by the
     * caller; null when the portal refuses or the index is dishonest.
     */
    fun openPipeWireRemote(sessionHandle: String, timeoutMillis: Long = 5_000): Int? {
        val call = DbusMessage(
            type = DbusMessage.TYPE_METHOD_CALL,
            flags = 0,
            serial = 0,
            fields = mapOf(
                DbusMessage.FIELD_DESTINATION to DbusValue.StringValue(PORTAL_SERVICE),
                DbusMessage.FIELD_PATH to DbusValue.ObjectPathValue(sessionHandle),
                DbusMessage.FIELD_INTERFACE to DbusValue.StringValue(SCREENCAST_INTERFACE),
                DbusMessage.FIELD_MEMBER to DbusValue.StringValue("OpenPipeWireRemote"),
            ),
            bodySignature = "oa{sv}",
            body = listOf(
                DbusValue.ObjectPathValue(sessionHandle),
                optionsDictionary(),
            ),
        )
        val replied = connection.call(call, timeoutMillis)
        val reply = replied.reply ?: return null
        val index = (reply.body.firstOrNull() as? DbusValue.UnixFdValue)?.index ?: return null
        // 'h' is an index into the SCM_RIGHTS descriptors of THIS frame
        val fds = replied.unixFds
        if (index !in fds.indices) return null
        return fds[index]
    }

    private fun optionsDictionary(vararg entries: Pair<String, DbusValue>): DbusValue.ArrayValue =
        DbusValue.ArrayValue(
            elementSignature = "{sv}",
            elements = entries.map { (key, value) ->
                // pre-wrapped variants pass through: wrapping again would
                // emit the spec-forbidden nested 'v' signature
                val variant = value as? DbusValue.VariantValue
                    ?: DbusValue.VariantValue(DbusMessage.signatureOf(value), value)
                DbusValue.DictEntryValue(DbusValue.StringValue(key), variant)
            },
        )

    class SessionHandle(
        val sessionPath: String?,
        val outcome: StepOutcome,
    )

    companion object {
        const val PORTAL_SERVICE = "org.freedesktop.portal"
        const val SCREENCAST_PATH = "/org/freedesktop/portal/desktop"
        const val SCREENCAST_INTERFACE = "org.freedesktop.portal.ScreenCast"
        const val REQUEST_INTERFACE = "org.freedesktop.portal.Request"
        const val REQUEST_ROOT = "/org/freedesktop/portal/desktop/request"
        const val SESSION_ROOT = "/org/freedesktop/portal/desktop/session"

        /** True when the portal owns its name on this bus (D1). */
        fun portalAvailable(connection: DbusConnection): Boolean {
            val call = DbusMessage(
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
                body = listOf(DbusValue.StringValue(PORTAL_SERVICE)),
            )
            val replied = connection.call(call, 2_000)
            return replied.reply != null
        }
    }
}
