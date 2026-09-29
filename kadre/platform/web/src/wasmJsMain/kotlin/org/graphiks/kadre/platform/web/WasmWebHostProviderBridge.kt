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
 * The facade's `windowProvider` bridge for the Kotlin/Wasm target (plan decision D8).
 *
 * The raw option the shim passes crosses as an opaque `JsAny`; this file is the only Wasm code that
 * reads it. It resolves the option into the closed union `attachSession` decides on, and wraps a
 * callable provider into the public [WebWindowProvider] the ordinary attach path consumes: the DTO
 * spec the provider receives is built from the shared webMain JSON, the call is ferried through one
 * glue envelope that also carries a provider exception as data, and the answer is read into the
 * discriminated union — opened hosts wrapped per target, rejections decoded by the shared webMain
 * decoder. This file is `JsWebHostProviderBridge.kt`'s mirror: same names, same shapes, one glue
 * idiom per target (`@JsFun` where the JS side uses `js()`).
 */

/**
 * Resolves the raw `windowProvider` option into its closed union: `null` is absent, an object whose
 * `open` is callable bridges into a [WebWindowProvider], and anything else is invalid — refused by
 * `attachSession`, never silently ignored.
 */
internal fun wasmWindowProviderOption(raw: JsAny?): WebWindowProviderOption<WebWindowProvider> = when {
    raw == null -> WebWindowProviderOption.Absent
    !wasmWindowProviderCallable(raw) -> WebWindowProviderOption.Invalid
    else -> WebWindowProviderOption.Provided(
        WebWindowProvider { _, spec -> wasmFacadeOpen(raw, spec) { element, policy, scope -> wasmFacadeHost(element, policy, scope) } },
    )
}

/**
 * Wraps one offered element into the host the public provider answers with.
 *
 * The cast is deliberately `unsafeCast` and not a checked conversion to `HTMLElement`: the element
 * lives in the browsing context the host offered — the distinctness the contract is about — and
 * every checked conversion answers against this window's own constructor, refusing the very
 * cross-realm element the provider is entitled to hand over. The invariant "this `JsAny` holds the
 * element the provider returned" is enforced by the glue that read it.
 */
private fun wasmFacadeHost(element: JsAny, attachmentPolicy: WebAttachmentPolicy, parentScope: CoroutineScope): WebWindowHost =
    WebWindowHost(element.unsafeCast<HTMLElement>(), parentScope, attachmentPolicy)

/**
 * One provider call: builds the spec DTO, calls `open` with the opaque request token, and reads the
 * answer.
 *
 * The exception the glue captured (`wasmOpenWindowRequest` never lets a provider throw into Kotlin)
 * is rethrown here as a Kotlin exception — the manager's own capture then reports it as the closed
 * `callback-exception`, exactly as it does for the Kotlin-direct provider.
 */
private fun wasmFacadeOpen(
    raw: JsAny,
    spec: WindowSpec,
    host: (element: JsAny, attachmentPolicy: WebAttachmentPolicy, parentScope: CoroutineScope) -> WebWindowHost,
): KadreResult<WebWindowHost> {
    val envelope = wasmOpenWindowRequest(
        raw,
        KadreWebInterop.nextWindowRequestIdentity(),
        webWindowSpecDtoJson(spec, windowSpecDisplayToken(spec)),
    )
    return when (wasmEnvelopeDelivered(envelope)) {
        "opened" -> {
            val element = wasmEnvelopeElement(envelope)
                ?: return KadreResult.Failure(invalidWindowProviderResult())
            val attachmentPolicy = when (val policy = wasmEnvelopePolicy(envelope)) {
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
            val decoded = decodeWindowRequestFailure(wasmEnvelopeFailureFields(envelope))
                ?: return KadreResult.Failure(invalidWindowProviderResult())
            KadreResult.Failure(decoded)
        }

        "thrown" -> throw IllegalStateException(wasmEnvelopeMessage(envelope))
        // A result whose kind is neither `opened` nor `rejected` is not a member of the promised
        // union: reported as the closed `invalid-failure`, never leaked.
        else -> KadreResult.Failure(invalidWindowProviderResult())
    }
}

private fun wasmEnvelopeFailureFields(envelope: JsAny): WebWindowFailureFields = WebWindowFailureFields(
    kind = wasmEnvelopeFailureKind(envelope),
    operation = wasmEnvelopeFailureOperation(envelope),
    field = wasmEnvelopeFailureField(envelope),
    reason = wasmEnvelopeFailureReason(envelope),
    resource = wasmEnvelopeFailureResource(envelope),
    retryable = wasmEnvelopeFailureRetryable(envelope),
    limit = wasmEnvelopeFailureLimit(envelope),
    platform = wasmEnvelopeFailurePlatform(envelope),
    domain = wasmEnvelopeFailureDomain(envelope),
    code = wasmEnvelopeFailureCode(envelope),
)

/** Whether [raw] carries a callable `open` — the one shape the option union admits. */
@JsFun("(raw) => typeof raw.open === 'function'")
private external fun wasmWindowProviderCallable(raw: JsAny): Boolean

/**
 * Calls the raw provider's `open` with the DTO parsed from [json] and normalises the answer into one
 * envelope: the opened host (element and attachment policy), the rejected failure's closed-set
 * fields, an out-of-union result, or the exception `open` threw, carried as data. Nothing this glue
 * runs — the parse, the icon's `Uint8Array` copy, the call, the reads — escapes as an exception.
 */
@JsFun(
    """(raw, requestId, json) => {
         const stringOrNull = (value) => (typeof value === "string" ? value : null);
         try {
           const dto = JSON.parse(json);
           const icon = dto.icon;
           if (icon != null) icon.bytes = Uint8Array.from(atob(icon.bytes), (c) => c.charCodeAt(0));
           const result = raw.open(requestId, dto);
           const kind = result == null ? null : stringOrNull(result.kind);
           if (kind === "opened") {
             const host = result.host == null ? {} : result.host;
             return {
               delivered: "opened",
               element: host.element === undefined ? null : host.element,
               policy: host.attachmentPolicy === undefined ? null : stringOrNull(host.attachmentPolicy),
             };
           }
           if (kind === "rejected") {
             const failure = result.failure == null ? {} : result.failure;
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
       }""",
)
private external fun wasmOpenWindowRequest(raw: JsAny, requestId: String, json: String): JsAny

@JsFun("(envelope) => envelope.delivered")
private external fun wasmEnvelopeDelivered(envelope: JsAny): String

@JsFun("(envelope) => envelope.message")
private external fun wasmEnvelopeMessage(envelope: JsAny): String

@JsFun("(envelope) => (envelope.element === undefined ? null : envelope.element)")
private external fun wasmEnvelopeElement(envelope: JsAny): JsAny?

@JsFun("(envelope) => (envelope.policy === undefined ? null : envelope.policy)")
private external fun wasmEnvelopePolicy(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureKind === undefined ? null : envelope.failureKind)")
private external fun wasmEnvelopeFailureKind(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureOperation === undefined ? null : envelope.failureOperation)")
private external fun wasmEnvelopeFailureOperation(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureField === undefined ? null : envelope.failureField)")
private external fun wasmEnvelopeFailureField(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureReason === undefined ? null : envelope.failureReason)")
private external fun wasmEnvelopeFailureReason(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureResource === undefined ? null : envelope.failureResource)")
private external fun wasmEnvelopeFailureResource(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureRetryable === undefined ? null : envelope.failureRetryable)")
private external fun wasmEnvelopeFailureRetryable(envelope: JsAny): Boolean?

@JsFun("(envelope) => (envelope.failureLimit === undefined ? null : envelope.failureLimit)")
private external fun wasmEnvelopeFailureLimit(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failurePlatform === undefined ? null : envelope.failurePlatform)")
private external fun wasmEnvelopeFailurePlatform(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureDomain === undefined ? null : envelope.failureDomain)")
private external fun wasmEnvelopeFailureDomain(envelope: JsAny): String?

@JsFun("(envelope) => (envelope.failureCode === undefined ? null : envelope.failureCode)")
private external fun wasmEnvelopeFailureCode(envelope: JsAny): String?
