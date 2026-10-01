@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement
import kotlin.js.JsAny
import kotlin.js.unsafeCast

/**
 * The facade's `windowProvider` bridge for the Kotlin/JS target (plan decision D8).
 *
 * The raw option the shim passes crosses as an opaque `JsAny`; this file is the only JS code that
 * reads it. It resolves the option into the closed union `attachSession` decides on, and wraps a
 * callable provider into the public [WebWindowProvider] the ordinary attach path consumes: the DTO
 * spec the provider receives is built from the shared webMain JSON, the call is ferried through one
 * glue envelope that also carries a provider exception as data, and the answer is read into the
 * discriminated union — opened hosts wrapped per target, rejections decoded by the shared webMain
 * decoder. The Wasm bridge (`WasmWebHostProviderBridge.kt`) is this file's mirror: same names, same
 * shapes, one glue idiom per target.
 */

/**
 * Resolves the raw `windowProvider` option into its closed union: `null` is absent, an object whose
 * `open` is callable bridges into a [WebWindowProvider], and anything else is invalid — refused by
 * `attachSession`, never silently ignored.
 */
internal fun jsWindowProviderOption(raw: JsAny?): WebWindowProviderOption<WebWindowProvider> = when {
    raw == null -> WebWindowProviderOption.Absent
    !jsWindowProviderCallable(raw) -> WebWindowProviderOption.Invalid
    else -> WebWindowProviderOption.Provided(
        WebWindowProvider { _, spec -> jsFacadeOpen(raw, spec) { element, policy, scope -> jsFacadeHost(element, policy, scope) } },
    )
}

/**
 * Wraps one offered element into the host the public provider answers with.
 *
 * The cast is deliberately `unsafeCast` and not `as`: the element lives in the browsing context the
 * host offered — the distinctness the contract is about — and an `as` cast would test `instanceof`
 * against this window's own constructor. The invariant "this `JsAny` holds the element the provider
 * returned" is enforced by the glue that read it.
 */
private fun jsFacadeHost(element: JsAny, attachmentPolicy: WebAttachmentPolicy, parentScope: CoroutineScope): WebWindowHost =
    WebWindowHost(element.unsafeCast<HTMLElement>(), parentScope, attachmentPolicy)

/**
 * One provider call: builds the spec DTO, calls `open` with the opaque request token, and reads the
 * answer.
 *
 * The exception the glue captured (`jsOpenWindowRequest` never lets a provider throw into Kotlin) is
 * rethrown here as a Kotlin exception — the manager's own capture then reports it as the closed
 * `callback-exception`, exactly as it does for the Kotlin-direct provider.
 */
private fun jsFacadeOpen(
    raw: JsAny,
    spec: WindowSpec,
    host: (element: JsAny, attachmentPolicy: WebAttachmentPolicy, parentScope: CoroutineScope) -> WebWindowHost,
): KadreResult<WebWindowHost> {
    val envelope = jsOpenWindowRequest(
        raw,
        KadreWebInterop.nextWindowRequestIdentity(),
        webWindowSpecDtoJson(spec, windowSpecDisplayToken(spec)),
    )
    return when (jsEnvelopeDelivered(envelope)) {
        "opened" -> {
            val element = jsEnvelopeElement(envelope)
                ?: return KadreResult.Failure(invalidWindowProviderResult())
            val attachmentPolicy = when (val policy = jsEnvelopePolicy(envelope)) {
                null -> WebAttachmentPolicy.StopWhenDetached
                "stopWhenDetached" -> WebAttachmentPolicy.StopWhenDetached
                "manual" -> WebAttachmentPolicy.Manual
                // The host union is closed: an opened result carrying another attachment policy is
                // out of the union, reported and never reinterpreted.
                else -> return KadreResult.Failure(invalidWindowProviderResult())
            }
            // The child session's scope is a fresh Kadre `MainScope`, created here per opened host
            // (plan decisions D6 and D8): never the requester's, so closing the requester never
            // reaches a session already opened in the offered context.
            KadreResult.Success(host(element, attachmentPolicy, MainScope()))
        }

        "rejected" -> {
            val decoded = decodeWindowRequestFailure(jsEnvelopeFailureFields(envelope))
                ?: return KadreResult.Failure(invalidWindowProviderResult())
            KadreResult.Failure(decoded)
        }

        "thrown" -> throw IllegalStateException(jsEnvelopeMessage(envelope))
        // A result whose kind is neither `opened` nor `rejected` is not a member of the promised
        // union: reported as the closed `invalid-failure`, never leaked.
        else -> KadreResult.Failure(invalidWindowProviderResult())
    }
}

/** Whether [raw] carries a callable `open` — the one shape the option union admits. */
private fun jsWindowProviderCallable(raw: JsAny): Boolean = js("typeof raw.open === 'function'")

/**
 * Calls the raw provider's `open` with the DTO parsed from [json] and normalises the answer into one
 * envelope: the opened host (element and attachment policy), the rejected failure's closed-set
 * fields, an out-of-union result, or the exception `open` threw, carried as data. Nothing this glue
 * runs — the parse, the icon's `Uint8Array` copy, the call, the reads — escapes as an exception.
 */
private fun jsOpenWindowRequest(raw: JsAny, requestId: String, json: String): JsAny = js(
    """(function () {
      var stringOrNull = function (value) { return typeof value === "string" ? value : null; };
      try {
        var dto = JSON.parse(json);
        var icon = dto.icon;
        if (icon != null) icon.bytes = Uint8Array.from(atob(icon.bytes), function (c) { return c.charCodeAt(0); });
        var result = raw.open(requestId, dto);
        var kind = result == null ? null : stringOrNull(result.kind);
        if (kind === "opened") {
          var host = result.host == null ? {} : result.host;
          return {
            delivered: "opened",
            element: host.element === undefined ? null : host.element,
            policy: host.attachmentPolicy === undefined ? null : stringOrNull(host.attachmentPolicy),
          };
        }
        if (kind === "rejected") {
          var failure = result.failure == null ? {} : result.failure;
          return {
            delivered: "rejected",
            failureKind: stringOrNull(failure.kind),
            failureOperation: stringOrNull(failure.operation),
            failureField: stringOrNull(failure.field),
            failureReason: stringOrNull(failure.reason),
            failureResource: stringOrNull(failure.resource),
            failureRetryable: typeof failure.retryable === "boolean" ? failure.retryable : null,
            failureLimit: failure.limit == null ? null : String(failure.limit),
            failurePlatform: stringOrNull(failure.platform),
            failureDomain: stringOrNull(failure.domain),
            failureCode: stringOrNull(failure.code),
          };
        }
        return { delivered: "unknown" };
      } catch (error) {
        return { delivered: "thrown", message: String((error && error.message) || error) };
      }
    })()""",
)

private fun jsEnvelopeDelivered(envelope: JsAny): String = js("envelope.delivered")

private fun jsEnvelopeMessage(envelope: JsAny): String = js("envelope.message")

private fun jsEnvelopeElement(envelope: JsAny): JsAny? = js("envelope.element === undefined ? null : envelope.element")

private fun jsEnvelopePolicy(envelope: JsAny): String? = js("envelope.policy === undefined ? null : envelope.policy")

private fun jsEnvelopeFailureFields(envelope: JsAny): WebWindowFailureFields = WebWindowFailureFields(
    kind = jsEnvelopeFailureKind(envelope),
    operation = jsEnvelopeFailureOperation(envelope),
    field = jsEnvelopeFailureField(envelope),
    reason = jsEnvelopeFailureReason(envelope),
    resource = jsEnvelopeFailureResource(envelope),
    retryable = jsEnvelopeFailureRetryable(envelope),
    limit = jsEnvelopeFailureLimit(envelope),
    platform = jsEnvelopeFailurePlatform(envelope),
    domain = jsEnvelopeFailureDomain(envelope),
    code = jsEnvelopeFailureCode(envelope),
)

private fun jsEnvelopeFailureKind(envelope: JsAny): String? = js("envelope.failureKind === undefined ? null : envelope.failureKind")

private fun jsEnvelopeFailureOperation(envelope: JsAny): String? = js("envelope.failureOperation === undefined ? null : envelope.failureOperation")

private fun jsEnvelopeFailureField(envelope: JsAny): String? = js("envelope.failureField === undefined ? null : envelope.failureField")

private fun jsEnvelopeFailureReason(envelope: JsAny): String? = js("envelope.failureReason === undefined ? null : envelope.failureReason")

private fun jsEnvelopeFailureResource(envelope: JsAny): String? = js("envelope.failureResource === undefined ? null : envelope.failureResource")

private fun jsEnvelopeFailureRetryable(envelope: JsAny): Boolean? = js("envelope.failureRetryable === undefined ? null : envelope.failureRetryable")

private fun jsEnvelopeFailureLimit(envelope: JsAny): String? = js("envelope.failureLimit === undefined ? null : envelope.failureLimit")

private fun jsEnvelopeFailurePlatform(envelope: JsAny): String? = js("envelope.failurePlatform === undefined ? null : envelope.failurePlatform")

private fun jsEnvelopeFailureDomain(envelope: JsAny): String? = js("envelope.failureDomain === undefined ? null : envelope.failureDomain")

private fun jsEnvelopeFailureCode(envelope: JsAny): String? = js("envelope.failureCode === undefined ? null : envelope.failureCode")
