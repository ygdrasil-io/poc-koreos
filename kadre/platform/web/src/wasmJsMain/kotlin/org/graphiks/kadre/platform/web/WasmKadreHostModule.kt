@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, kotlin.js.ExperimentalJsExport::class)

// The JavaScript surface of `@kadre/host` for the Kotlin/Wasm target.
//
// Kotlin/Wasm exports functions only, so this target does not export classes or objects either: the
// module's JavaScript bindings are the top-level functions below, and the promised `KadreWeb` surface
// is presented by `types/kadre-host-wasm.mjs`, which the package ships as `index.mjs`. The shared half
// — registries, keys, encoding, option resolution — lives in `WebHostInterop.kt`.

package org.graphiks.kadre.platform.web

import kotlin.js.JsAny
import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicy
import org.w3c.dom.HTMLElement

// Kotlin/Wasm cannot name a non-external class in an exported signature and its DOM declarations
// cannot be translated into the generated declarations, so the element crosses as `JsAny` and is
// reclaimed as the SDK interface inside `attachElement`; the shim only ever passes an HTMLElement.

/**
 * Attaches a session to the element [element] of the browsing context.
 *
 * [factoryKey] is the opaque key of `KadreApplicationFactoryRef.hostKey`. Returns `"ok|<handle key>"`
 * or `"failed|<failure JSON>"`; the shim turns the latter into `KadreHostError`.
 */
@JsExport
public fun kadreWebAttach(
    element: JsAny,
    factoryKey: String,
    policy: String,
    attachmentPolicy: String,
): String = attachSession(element, factoryKey, policy, attachmentPolicy, ::attachElement)

/** The opaque identifier of the session behind [handleKey]. Not the Kotlin `SessionId`. */
@JsExport
public fun kadreWebSessionId(handleKey: Int): String = KadreWebInterop.handle(handleKey).id

/** The current snapshot of the session behind [handleKey], as a JSON object keyed by `kind`. */
@JsExport
public fun kadreWebSessionState(handleKey: Int): String = KadreWebInterop.handle(handleKey).state

/**
 * Subscribes [observer] to the state of the session behind [handleKey]; returns its subscription key.
 *
 * [observer] receives one snapshot JSON per notification, starting synchronously with the current
 * snapshot.
 */
@JsExport
public fun kadreWebSubscribeState(handleKey: Int, observer: (String) -> Unit): Int =
    KadreWebInterop.handle(handleKey).subscribe(observer)

/**
 * Subscribes [observer] to the terminal outcome of the session behind [handleKey]; returns its
 * subscription key. The registration is one-shot: it delivers the outcome JSON exactly once.
 */
@JsExport
public fun kadreWebSubscribeTermination(handleKey: Int, observer: (String) -> Unit): Int =
    KadreWebInterop.handle(handleKey).subscribeTermination(observer)

/** Cancels the registration behind [subscriptionKey]. Returns whether it was still registered. */
@JsExport
public fun kadreWebUnsubscribeState(subscriptionKey: Int): Boolean =
    KadreWebInterop.unsubscribe(subscriptionKey)

/** Asks the session behind [handleKey] to stop. */
@JsExport
public fun kadreWebRequestStop(handleKey: Int): Unit = KadreWebInterop.handle(handleKey).requestStop()

/** Closes the session behind [handleKey]; its terminal outcome stays observable. */
@JsExport
public fun kadreWebClose(handleKey: Int): Unit = KadreWebInterop.handle(handleKey).close()

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun attachElement(
    element: JsAny,
    factory: KadreApplicationFactory,
    policy: KadrePolicy,
    attachmentPolicy: WebAttachmentPolicy,
    scope: CoroutineScope,
): KadreResult<KadreSession> = (element as HTMLElement).attachKadre(
    parentScope = scope,
    applicationFactory = factory,
    policy = policy,
    attachmentPolicy = attachmentPolicy,
)
