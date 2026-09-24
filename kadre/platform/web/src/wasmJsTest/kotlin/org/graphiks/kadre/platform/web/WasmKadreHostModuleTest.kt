@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The `@kadre/host` entry point of the Kotlin/Wasm target, exercised against a real element.
 *
 * The source mirrors the Kotlin/JS test; only the option literal and the promise type differ,
 * because the Wasm JS interop has no `js("...")` object literal and `Deferred.asPromise` is typed
 * `Promise<JsAny?>` there.
 */
class WasmKadreHostModuleTest {
    @Test
    fun attachPublishesTheStateAndResolvesTheTerminalOutcome() = runTest {
        val host = existingHostElement()
        try {
            val handle = KadreWeb.attach(host, awaitingFactory().asHostRef())
            assertTrue(handle.id.isNotEmpty(), "the session identity is an opaque non-empty string")
            awaitReal(1.seconds) { handle.state.kind == "running" }

            val observed = mutableListOf<String>()
            val unsubscribe = handle.subscribeState { observed += it.kind }
            assertEquals(listOf("running"), observed, "the observer hears the current snapshot first")

            handle.requestStop()
            val outcome = handle.awaitTermination().await<KadreSessionOutcome>()
            assertEquals("stopped", outcome.kind)
            assertEquals("hostRequested", outcome.reason)
            assertEquals(
                "stopped",
                handle.awaitTermination().await<KadreSessionOutcome>().kind,
                "the terminal outcome stays available after the terminal state",
            )
            unsubscribe()
        } finally {
            host.remove()
        }
    }

    @Test
    fun aDisconnectedElementIsRefusedUnderStopWhenDetached() {
        val host = document.createElement("div") as HTMLElement

        val error = assertFailsWith<KadreHostError> { KadreWeb.attach(host, awaitingFactory().asHostRef()) }

        assertEquals("invalidRequest", error.failure.kind)
        assertEquals("element", error.failure.field)
    }

    @Test
    fun aManualAttachmentAcceptsADisconnectedElement() = runTest {
        val host = document.createElement("div") as HTMLElement

        val handle = KadreWeb.attach(
            host,
            awaitingFactory().asHostRef(),
            wasmKadreOptions(policy = "realtime", attachmentPolicy = "manual"),
        )
        try {
            assertTrue(handle.state.kind != "terminated", "manual attachment accepts a disconnected element")
        } finally {
            handle.close()
        }
    }

    @Test
    fun anUnknownOptionMemberIsRefusedInsteadOfIgnored() {
        val host = existingHostElement()
        try {
            val refused = assertFailsWith<KadreHostError> {
                KadreWeb.attach(
                    host,
                    awaitingFactory().asHostRef(),
                    wasmKadreOptions(policy = "turbo", attachmentPolicy = "manual"),
                )
            }
            assertEquals("invalidRequest", refused.failure.kind)
            assertEquals("options.policy", refused.failure.field)

            val refusedAttachment = assertFailsWith<KadreHostError> {
                KadreWeb.attach(
                    host,
                    awaitingFactory().asHostRef(),
                    wasmKadreOptions(policy = "default", attachmentPolicy = "sometimes"),
                )
            }
            assertEquals("options.attachmentPolicy", refusedAttachment.failure.field)
        } finally {
            host.remove()
        }
    }

    @Test
    fun theClosedOptionUnionsMapToTheDeclaredPolicies() {
        assertEquals<KadrePolicy>(KadrePolicies.Default, wasmKadreOptions(null, null).selectedPolicy())
        assertEquals<KadrePolicy>(KadrePolicies.Realtime, wasmKadreOptions("realtime", null).selectedPolicy())
        assertEquals<KadrePolicy>(KadrePolicies.Recording, wasmKadreOptions("recording", null).selectedPolicy())
        assertEquals(WebAttachmentPolicy.Manual, wasmKadreOptions(null, "manual").selectedAttachmentPolicy())
        assertEquals(
            WebAttachmentPolicy.StopWhenDetached,
            wasmKadreOptions(null, "stopWhenDetached").selectedAttachmentPolicy(),
        )
    }

    /** The application never leaves its scope, so the session stays running until it is asked to stop. */
    private fun awaitingFactory(): KadreApplicationFactory =
        KadreApplicationFactory { KadreApplication { awaitCancellation() } }

    private fun existingHostElement(): HTMLElement =
        (document.createElement("div") as HTMLElement).also {
            it.style.width = "320px"
            it.style.height = "180px"
            document.body!!.appendChild(it)
        }

    /**
     * Waits for the browser's own event loop.
     *
     * The facade owns the `MainScope` of its session, so its state transitions do not run on the
     * virtual clock of [runTest].
     */
    private suspend fun awaitReal(timeout: Duration, condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (!condition() && deadline.hasNotPassedNow()) delay(5)
        }
        assertTrue(condition(), "the browser never reached the awaited state")
    }
}

/** Builds an option literal for the Wasm target. */
private fun wasmKadreOptions(policy: String?, attachmentPolicy: String?): KadreWebOptions =
    buildOptions(policy, attachmentPolicy)

@JsFun("(policy, attachmentPolicy) => ({ policy, attachmentPolicy })")
private external fun buildOptions(policy: String?, attachmentPolicy: String?): KadreWebOptions
