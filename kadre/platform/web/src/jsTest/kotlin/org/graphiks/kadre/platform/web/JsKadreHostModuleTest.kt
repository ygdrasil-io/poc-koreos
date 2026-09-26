package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The terminal snapshot of a session stopped by the host, as the shim receives it. */
private const val TERMINAL_STOPPED =
    "{\"kind\":\"terminated\",\"outcome\":{\"kind\":\"stopped\",\"reason\":\"hostRequested\"}}"

/**
 * The published JavaScript surface of `@kadre/host`, exercised through the exported functions the
 * `index.mjs` shim is built on. The Wasm target compiles the same test over its own SDK element.
 */
class JsKadreHostModuleTest {
    @Test
    fun attachPublishesTheStateAndTheTerminalOutcome() = runTest {
        val host = existingHostElement()
        try {
            val attached = kadreWebAttach(host, factoryKey(), "default", "stopWhenDetached")
            assertTrue(attached.startsWith("ok|"), "an accepted attachment is reported as ok: $attached")
            val handleKey = attached.removePrefix("ok|").toInt()

            assertTrue(kadreWebSessionId(handleKey).isNotEmpty(), "the session has an opaque identifier")
            awaitReal(1.seconds) { kadreWebSessionState(handleKey) == "{\"kind\":\"running\"}" }

            val observed = mutableListOf<String>()
            val subscription = kadreWebSubscribeState(handleKey) { observed += it }
            assertEquals(
                listOf("{\"kind\":\"running\"}"),
                observed,
                "the observer hears the current snapshot synchronously",
            )

            val outcomes = mutableListOf<String>()
            kadreWebSubscribeTermination(handleKey) { outcomes += it }

            kadreWebRequestStop(handleKey)
            awaitReal(1.seconds) { outcomes.size == 1 }
            assertEquals(listOf("{\"kind\":\"stopped\",\"reason\":\"hostRequested\"}"), outcomes)

            // A consumer that waits for `terminated` on the state subscription must not hang.
            awaitReal(1.seconds) { observed.lastOrNull() == TERMINAL_STOPPED }
            assertEquals(
                TERMINAL_STOPPED,
                observed.lastOrNull(),
                "the state observer hears the terminal snapshot",
            )
            assertTrue(observed.contains("{\"kind\":\"running\"}"), "the state observer heard the running snapshot")
            assertEquals(
                false,
                KadreWebInterop.isLiveHandle(handleKey),
                "the terminated handle is released, so the element and the session are not retained",
            )
            val late = mutableListOf<String>()
            kadreWebSubscribeState(handleKey) { late += it }
            assertEquals(
                listOf(TERMINAL_STOPPED),
                late,
                "the released session still answers the terminal snapshot",
            )

            assertEquals(
                false,
                kadreWebUnsubscribeState(subscription),
                "the terminated session dropped the subscription with it, so cancelling it cancels nothing",
            )
            assertEquals("{\"kind\":\"terminated\",\"outcome\":{\"kind\":\"stopped\",\"reason\":\"hostRequested\"}}", kadreWebSessionState(handleKey))
        } finally {
            host.remove()
        }
    }

    @Test
    fun aDisconnectedElementIsRefusedUnderStopWhenDetached() {
        val host = document.createElement("div") as HTMLElement

        val refused = kadreWebAttach(host, factoryKey(), "default", "stopWhenDetached")

        assertEquals("{\"kind\":\"invalidRequest\",\"field\":\"element\"}", refused.removePrefix("failed|"))
    }

    @Test
    fun aManualAttachmentAcceptsADisconnectedElement() = runTest {
        val host = document.createElement("div") as HTMLElement

        val attached = kadreWebAttach(host, factoryKey(), "realtime", "manual")
        try {
            assertTrue(attached.startsWith("ok|"), "manual attachment accepts a disconnected element: $attached")
            val handleKey = attached.removePrefix("ok|").toInt()
            assertTrue(kadreWebSessionState(handleKey) != "{\"kind\":\"terminated\"}")
        } finally {
            kadreWebClose(attached.removePrefix("ok|").toInt())
        }
    }

    @Test
    fun anUnknownOptionMemberOrFactoryIsRefusedInsteadOfIgnored() {
        val host = existingHostElement()
        try {
            assertEquals(
                "{\"kind\":\"invalidRequest\",\"field\":\"options.policy\"}",
                kadreWebAttach(host, factoryKey(), "turbo", "manual").removePrefix("failed|"),
            )
            assertEquals(
                "{\"kind\":\"invalidRequest\",\"field\":\"options.attachmentPolicy\"}",
                kadreWebAttach(host, factoryKey(), "default", "sometimes").removePrefix("failed|"),
            )
            assertEquals(
                "{\"kind\":\"invalidRequest\",\"field\":\"factoryKey\"}",
                kadreWebAttach(host, "kadre-factory-none", "default", "manual").removePrefix("failed|"),
            )
        } finally {
            host.remove()
        }
    }

    private fun factoryKey(): String =
        KadreApplicationFactory { KadreApplication { awaitCancellation() } }.asHostRef().hostKey

    private fun existingHostElement(): HTMLElement =
        (document.createElement("div") as HTMLElement).also {
            it.style.width = "320px"
            it.style.height = "180px"
            document.body!!.appendChild(it)
        }

    /**
     * Waits for the browser's own event loop.
     *
     * The interop layer owns the `MainScope` of its session, so its state transitions do not run on
     * the virtual clock of [runTest].
     */
    private suspend fun awaitReal(timeout: Duration, condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (!condition() && deadline.hasNotPassedNow()) delay(5)
        }
        assertTrue(condition(), "the browser never reached the awaited state")
    }
    /**
     * The registry follows the most recent publisher, so an application that publishes after a
     * stand-in instance takes the JavaScript host over instead of being answered by the stand-in.
     */
    @Test
    fun aLaterPublisherTakesOverTheRegistry() {
        installForeignHostRegistry()
        assertTrue(hostRegistryBindingIsForeign(), "the stand-in instance must hold the registry first")

        publishHostBindings()

        assertFalse(hostRegistryBindingIsForeign(), "the application's publication must take the registry over")
    }

}

/** A stand-in instance publishes the eight names first, with one shared stub function. */
private fun installForeignHostRegistry(): Unit = js(
    """(function () {
      var stub = function () { return "foreign"; };
      globalThis["kadre-test-foreign-binding"] = stub;
      var registry = {};
      var names = ["kadreWebAttach", "kadreWebSessionId", "kadreWebSessionState", "kadreWebSubscribeState",
        "kadreWebSubscribeTermination", "kadreWebUnsubscribeState", "kadreWebRequestStop", "kadreWebClose"];
      for (var index = 0; index < names.length; index += 1) {
        registry[names[index]] = stub;
      }
      globalThis["org.graphiks.kadre:web"] = registry;
    }())""",
)

/** Whether the registry still answers with the stand-in instance's binding. */
private fun hostRegistryBindingIsForeign(): Boolean = js(
    """globalThis["org.graphiks.kadre:web"].kadreWebAttach === globalThis["kadre-test-foreign-binding"]""",
)
