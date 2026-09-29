@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.BinaryImage
import org.graphiks.kadre.surface.ImageFormat
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.HTMLElement
import kotlin.js.JsAny
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The facade's `windowProvider` option, exercised end to end through the exported `kadreWebAttach`
 * binding on the Kotlin/Wasm target: the option the promise of `kadre/INTEROP-EXPORTS.md` section 6
 * declares, with the DTO copy, the closed failure decoding and the child session's own scope. This
 * file is `JsKadreHostWindowProviderTest.kt`'s mirror — same names, same shapes, one glue idiom per
 * target (`@JsFun` where the JS side uses `js()`).
 */
class WasmKadreHostWindowProviderTest {
    @Test
    fun aPresentWindowProviderOptionWithoutCallableOpenIsRefusedInsteadOfIgnored() {
        val host = existingHostElement()
        try {
            // The binding is exercised directly, without the shim's own guard: the closed option
            // union is enforced by the Kotlin half too, never by the shim alone.
            val refused = kadreWebAttach(host, factoryKey(), "default", "manual", providerWithoutOpenWasm())

            assertEquals(
                "{\"kind\":\"invalidRequest\",\"field\":\"options.windowProvider\"}",
                refused.removePrefix("failed|"),
            )
        } finally {
            host.remove()
        }
    }

    @Test
    fun anAbsentWindowProviderOptionKeepsTheProviderFreeBehaviour() = runTest {
        val host = existingHostElement()
        val harness = attachHarness(null)
        try {
            val scope = harness.scopeReady.await()
            awaitReal(1.seconds) { scope.windows.state.value.capabilities.requestWindow is Capability.Unsupported }
            assertTrue(
                scope.windows.state.value.capabilities.requestWindow is Capability.Unsupported,
                "no provider, no window capability",
            )
        } finally {
            harness.close()
            host.remove()
        }
    }

    @Test
    fun aProviderThatOpensCreatesTheChildSessionInTheOfferedContext() = runTest {
        // The host prepared, before Kadre ever ran, the second browsing context an offer must live
        // in: the iframe is its own window. Kadre is handed an element that already exists. Every
        // read of the offered document stays untyped (`JsAny` through `@JsFun`): a typed read of a
        // cross-realm object answers against this window's own constructor and would refuse the very
        // distinctness the contract requires.
        val host = existingHostElement()
        val iframe = document.createElement("iframe") as HTMLIFrameElement
        document.body!!.appendChild(iframe)
        val childDocument = assertNotNull(wasmContentDocumentWithBody(iframe.unsafeCast<JsAny>()))
        val childElement = wasmCreateAndAppendChildElement(childDocument)

        val hostScopeReady = CompletableDeferred<KadreScope>()
        val childScopeReady = CompletableDeferred<KadreScope>()
        val hostRelease = CompletableDeferred<Unit>()
        val childRelease = CompletableDeferred<Unit>()
        val launches = mutableListOf<org.graphiks.kadre.application.KadreLaunchContext>()
        val factory = KadreApplicationFactory { context ->
            launches += context
            KadreApplication {
                if (context.reason == KadreLaunchReason.AdditionalHostRequested) {
                    childScopeReady.complete(this)
                    childRelease.await()
                } else {
                    hostScopeReady.complete(this)
                    hostRelease.await()
                }
            }
        }
        // The provider records what it is handed and opens exactly once: the second request exists
        // to prove the per-call DTO copy, and is answered with a rejection nobody reads.
        val calls = mutableListOf<Pair<String, JsAny>>()
        val provider = facadeProviderWasm { requestId, dto ->
            calls += requestId to dto
            if (calls.size == 1) openedResultWasm(childElement, "manual")
            else rejectedResultWasm(invalidRequestFailureWasm())
        }

        val attached = kadreWebAttach(host, factory.asHostRef().hostKey, "default", "manual", provider)
        assertTrue(attached.startsWith("ok|"), "the facade accepts the callable provider: $attached")
        val handleKey = attached.removePrefix("ok|").toInt()
        try {
            val hostScope = hostScopeReady.await()

            val spec = WindowSpec(
                title = "facade",
                icon = BinaryImage(byteArrayOf(1, 2, 3), ImageFormat.Png, PhysicalSize(4, 5)),
            )
            val request = assertIs<KadreResult.Success<WindowRequest>>(hostScope.windows.requestWindow(spec)).value
            val opened = assertIs<WindowRequestOutcome.OpenedInNewSession>(request.await())

            // One call, one child session, opened in the context the host offered — and the launch
            // identity that says why the child was launched.
            assertEquals(1, calls.size, "the provider answered the one request")
            assertTrue(
                calls.single().first.startsWith("kadre-window-request-"),
                "the request token is the interop layer's own: ${calls.single().first}",
            )
            // The child session opens on its own fresh scope, so its launch identity is recorded a
            // moment after the request terminalises; the scope's readiness gates the read.
            childScopeReady.await()
            val childLaunch = launches.last()
            assertEquals(KadreLaunchReason.AdditionalHostRequested, childLaunch.reason)
            assertEquals(request.id, childLaunch.originatingRequestId)
            assertEquals(opened.sessionId, childLaunch.sessionId)

            // The DTO the provider received: the spec's fields, and the icon as a Uint8Array copy
            // with the requester's bytes.
            val dto = calls.single().second
            assertEquals("facade", dtoTitle(dto))
            assertTrue(dtoIconIsUint8Array(dto), "the icon payload is a Uint8Array")
            assertEquals(3, dtoIconByteLength(dto))
            assertEquals(1, dtoIconByteAt(dto, 0))
            assertEquals(2, dtoIconByteAt(dto, 1))
            assertEquals(3, dtoIconByteAt(dto, 2))

            // The copy is per call: what the provider does to one DTO never reaches the next one.
            mutateIconByte(dto, 0, 99)
            assertIs<KadreResult.Success<WindowRequest>>(hostScope.windows.requestWindow(spec)).value.await()
            assertEquals(2, calls.size, "the second request reached the provider too")
            assertEquals(1, dtoIconByteAt(calls[1].second, 0), "the second DTO carries the requester's bytes, not the mutated ones")

            // The requester stops; the child keeps its own scope and keeps running.
            kadreWebClose(handleKey)
            awaitReal(1.seconds) { !KadreWebInterop.isLiveHandle(handleKey) }
            assertFalse(
                childScopeReady.await().coroutineContext[Job]!!.isCancelled,
                "closing the requester never cancels a session opened in the offered context",
            )
        } finally {
            childRelease.complete(Unit)
            hostRelease.complete(Unit)
            iframe.remove()
            host.remove()
        }
    }

    @Test
    fun aProviderThatRejectsCarriesTheDecodedFailure() = runTest {
        val host = existingHostElement()
        val harness = attachHarness(facadeProviderWasm { _, _ -> rejectedResultWasm(failureWithResourceWasm("alreadyInUse", "host")) })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(
                harness.scopeReady.await().windows.requestWindow(WindowSpec()),
            ).value

            assertEquals(
                WindowRequestOutcome.Rejected(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
                request.await(),
                "a rejected answer decoding into the closed set is the request's rejection",
            )
        } finally {
            harness.close()
            host.remove()
        }
    }

    @Test
    fun aRejectionOutsideTheClosedSetIsReportedAsTheClosedInvalidFailure() = runTest {
        val host = existingHostElement()
        val harness = attachHarness(facadeProviderWasm { _, _ -> rejectedResultWasm(failureWithResourceWasm("alreadyInUse", "surface")) })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(
                harness.scopeReady.await().windows.requestWindow(WindowSpec()),
            ).value

            assertEquals(
                WindowRequestOutcome.Rejected(invalidWindowProviderResult()),
                request.await(),
                "a Busy naming another resource is undecodable, so the closed invalid-failure is reported",
            )
        } finally {
            harness.close()
            host.remove()
        }
    }

    @Test
    fun aBigIntLimitDecodesIntoTheClosedLimit() = runTest {
        val host = existingHostElement()
        val harness = attachHarness(facadeProviderWasm { _, _ -> rejectedResultWasm(limitFailureWasm()) })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(
                harness.scopeReady.await().windows.requestWindow(WindowSpec()),
            ).value

            assertEquals(
                WindowRequestOutcome.Rejected(KadreFailure.ResourceLimitExceeded(KadreResourceKind.Window, 7L)),
                request.await(),
                "the provider's bigint limit arrives as the closed Kotlin limit",
            )
        } finally {
            harness.close()
            host.remove()
        }
    }

    @Test
    fun aProviderThatThrowsIsReportedAsTheClosedCallbackException() = runTest {
        val host = existingHostElement()
        val harness = attachHarness(throwingProviderWasm("boom"))
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(
                harness.scopeReady.await().windows.requestWindow(WindowSpec()),
            ).value

            assertEquals(
                WindowRequestOutcome.Rejected(
                    KadreFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "callback-exception"),
                ),
                request.await(),
                "the provider's exception is captured and becomes the failure the contract promises",
            )
        } finally {
            harness.close()
            host.remove()
        }
    }

    @Test
    fun aResultOutsideTheUnionIsReportedAsTheClosedInvalidFailure() = runTest {
        listOf(
            "a result with no promised kind" to resultWithKindWasm("bogus"),
            "an opened result with no host element" to openedWithoutElementWasm("manual"),
            "an opened result whose host names another attachment policy" to
                openedResultWasm(foreignElementWasm(), "sometimes"),
        ).forEach { (description, answer) ->
            val host = existingHostElement()
            val harness = attachHarness(facadeProviderWasm { _, _ -> answer })
            try {
                val request = assertIs<KadreResult.Success<WindowRequest>>(
                    harness.scopeReady.await().windows.requestWindow(WindowSpec()),
                ).value

                assertEquals(
                    WindowRequestOutcome.Rejected(invalidWindowProviderResult()),
                    request.await(),
                    "the out-of-union answer is reported, never leaked: $description",
                )
            } finally {
                harness.close()
                host.remove()
            }
        }
    }

    /**
     * Attaches one harness host through the facade with [provider] as the raw option. No window is
     * ever opened against it, so the factory sees only the initial launch and the harness's
     * `close()` ends the one session it created.
     */
    private fun attachHarness(provider: JsAny?): Harness {
        val scopeReady = CompletableDeferred<KadreScope>()
        val factory = KadreApplicationFactory { KadreApplication { scopeReady.complete(this); awaitCancellation() } }
        val attached = kadreWebAttach(existingHostElement(), factory.asHostRef().hostKey, "default", "manual", provider)
        assertTrue(attached.startsWith("ok|"), "the harness attach must succeed: $attached")
        return Harness(attached.removePrefix("ok|").toInt(), scopeReady)
    }

    private class Harness(val handleKey: Int, val scopeReady: CompletableDeferred<KadreScope>) {
        fun close() {
            kadreWebClose(handleKey)
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
     * The facade owns its `MainScope`s, so its session transitions do not run on the virtual clock
     * of [runTest]; a bounded real wait is the only portable synchronisation for them.
     */
    private suspend fun awaitReal(timeout: Duration, condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (!condition() && deadline.hasNotPassedNow()) delay(5)
        }
        assertTrue(condition(), "the browser never reached the awaited state")
    }
}

// The one place Kotlin/Wasm and JavaScript meet for the offered document: untyped `JsAny` in and
// out, so no adapter ever answers against this window's own constructor for a cross-realm object
// (the precedent of `WasmWebAttachTest`).
@JsFun("(iframe) => { const doc = iframe.contentDocument; return doc && doc.body ? doc : null; }")
private external fun wasmContentDocumentWithBody(iframe: JsAny): JsAny?

@JsFun(
    "(doc) => { const element = doc.createElement('div'); " +
        "element.style.width = '160px'; element.style.height = '120px'; " +
        "doc.body.appendChild(element); return element; }",
)
private external fun wasmCreateAndAppendChildElement(doc: JsAny): JsAny

/** Wraps the Kotlin recording lambda into the raw `KadreWebWindowProvider` object the option carries. */
@JsFun("(open) => ({ open: (requestId, dto) => open(requestId, dto) })")
private external fun facadeProviderWasm(open: (requestId: String, dto: JsAny) -> JsAny): JsAny

/** Builds the opened answer for [element], with the attachment policy the host names. */
@JsFun(
    """(element, policy) => {
         const host = {};
         host.element = element;
         host.attachmentPolicy = policy;
         return { kind: 'opened', host: host };
       }""",
)
private external fun openedResultWasm(element: JsAny, policy: String): JsAny

/** The opened answer whose host carries no element at all. */
@JsFun(
    """(policy) => ({
         kind: 'opened',
         host: { attachmentPolicy: policy },
       })""",
)
private external fun openedWithoutElementWasm(policy: String): JsAny

@JsFun("(failure) => ({ kind: 'rejected', failure: failure })")
private external fun rejectedResultWasm(failure: JsAny): JsAny

@JsFun("() => ({ kind: 'invalidRequest', field: null })")
private external fun invalidRequestFailureWasm(): JsAny

@JsFun("(kind, resource) => ({ kind: kind, resource: resource })")
private external fun failureWithResourceWasm(kind: String, resource: String): JsAny

/** The closed limit as the promise types it: a `bigint`. */
@JsFun("() => ({ kind: 'resourceLimitExceeded', resource: 'window', limit: 7n })")
private external fun limitFailureWasm(): JsAny

@JsFun("(kind) => ({ kind: kind })")
private external fun resultWithKindWasm(kind: String): JsAny

@JsFun("(message) => ({ open: (requestId, dto) => { throw new Error(message); } })")
private external fun throwingProviderWasm(message: String): JsAny

@JsFun("() => ({ answer: 42 })")
private external fun providerWithoutOpenWasm(): JsAny

@JsFun("() => ({ nodeType: 1 })")
private external fun foreignElementWasm(): JsAny

@JsFun("(dto) => dto.title")
private external fun dtoTitle(dto: JsAny): String

@JsFun("(dto) => (dto.icon != null && dto.icon.bytes instanceof Uint8Array)")
private external fun dtoIconIsUint8Array(dto: JsAny): Boolean

@JsFun("(dto) => dto.icon.bytes.length")
private external fun dtoIconByteLength(dto: JsAny): Int

@JsFun("(dto, index) => dto.icon.bytes[index]")
private external fun dtoIconByteAt(dto: JsAny, index: Int): Int

@JsFun("(dto, index, value) => { dto.icon.bytes[index] = value; }")
private external fun mutateIconByte(dto: JsAny, index: Int, value: Int): Unit
