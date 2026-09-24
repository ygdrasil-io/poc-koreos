@file:OptIn(kotlin.js.ExperimentalJsExport::class)

// The JavaScript surface of `@kadre/host` for the Kotlin/JS target.
//
// Kotlin/Wasm exports functions only, so this target does not export classes or objects either: the
// module's JavaScript bindings are the top-level functions below, and the promised `KadreWeb` surface
// is presented by `types/kadre-host-js.mjs`, which the package ships as `index.mjs`. The shared half
// — registries, keys, encoding, option resolution — lives in `WebHostInterop.kt`.

package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicy
import org.w3c.dom.HTMLElement

/**
 * Attaches a session to the element [element] of the browsing context.
 *
 * [factoryKey] is the opaque key of `KadreApplicationFactoryRef.hostKey`. Returns `"ok|<handle key>"`
 * or `"failed|<failure JSON>"`; the shim turns the latter into `KadreHostError`.
 */
@JsExport
public fun kadreWebAttach(
    element: HTMLElement,
    factoryKey: String,
    policy: String,
    attachmentPolicy: String,
): String = attachSession(element, factoryKey, policy, attachmentPolicy, ::attachElement)

/** The opaque identifier of the session behind [handleKey]. Not the Kotlin `SessionId`. */
@JsExport
public fun kadreWebSessionId(handleKey: Int): String = KadreWebInterop.session(handleKey).id

/** The current snapshot of the session behind [handleKey], as a JSON object keyed by `kind`. */
@JsExport
public fun kadreWebSessionState(handleKey: Int): String = KadreWebInterop.session(handleKey).state

/**
 * Subscribes [observer] to the state of the session behind [handleKey]; returns its subscription key.
 *
 * [observer] receives one snapshot JSON per notification, starting synchronously with the current
 * snapshot.
 */
@JsExport
public fun kadreWebSubscribeState(handleKey: Int, observer: (String) -> Unit): Int =
    KadreWebInterop.session(handleKey).subscribe(observer)

/**
 * Subscribes [observer] to the terminal outcome of the session behind [handleKey]; returns its
 * subscription key. The registration is one-shot: it delivers the outcome JSON exactly once.
 */
@JsExport
public fun kadreWebSubscribeTermination(handleKey: Int, observer: (String) -> Unit): Int =
    KadreWebInterop.session(handleKey).subscribeTermination(observer)

/** Cancels the registration behind [subscriptionKey]. Returns whether it was still registered. */
@JsExport
public fun kadreWebUnsubscribeState(subscriptionKey: Int): Boolean =
    KadreWebInterop.unsubscribe(subscriptionKey)

/** Asks the session behind [handleKey] to stop. */
@JsExport
public fun kadreWebRequestStop(handleKey: Int): Unit = KadreWebInterop.session(handleKey).requestStop()

/** Closes the session behind [handleKey]; its terminal outcome stays observable. */
@JsExport
public fun kadreWebClose(handleKey: Int): Unit = KadreWebInterop.session(handleKey).close()

private fun attachElement(
    element: HTMLElement,
    factory: KadreApplicationFactory,
    policy: KadrePolicy,
    attachmentPolicy: WebAttachmentPolicy,
    scope: CoroutineScope,
): KadreResult<KadreSession> = element.attachKadre(
    parentScope = scope,
    applicationFactory = factory,
    policy = policy,
    attachmentPolicy = attachmentPolicy,
)


/**
 * Publishes this Kotlin module instance's `@kadre/host` bindings into the shared registry.
 *
 * The registry is the `globalThis["org.graphiks.kadre:web"]` object the Kotlin library modules and the
 * application bundles already populate: the JavaScript shims of `@kadre/host` resolve their bindings
 * there at call time, so the Kotlin module that owns the factories and sessions — the application's
 * own module, not a second copy of this library — is also the instance the shim drives. A Kotlin
 * application calls this before it hands its opaque factory key to JavaScript
 * (`kadre/INTEROP-EXPORTS.md` section 6), and nothing in this library has to reference it for the
 * publication to happen.
 *
 * Idempotent, and it never replaces a binding another instance of this library already published.
 */
public fun publishHostBindings(): Unit = publishBindings(
    ::kadreWebAttach,
    ::kadreWebSessionId,
    ::kadreWebSessionState,
    ::kadreWebSubscribeState,
    ::kadreWebSubscribeTermination,
    ::kadreWebUnsubscribeState,
    ::kadreWebRequestStop,
    ::kadreWebClose,
)

/**
 * Writes the eight bindings into the registry. The snippet sees the parameters by name and runs as a
 * single expression, which both targets accept.
 */
private fun publishBindings(
    attach: (HTMLElement, String, String, String) -> String,
    sessionId: (Int) -> String,
    sessionState: (Int) -> String,
    subscribeState: (Int, (String) -> Unit) -> Int,
    subscribeTermination: (Int, (String) -> Unit) -> Int,
    unsubscribeState: (Int) -> Boolean,
    requestStop: (Int) -> Unit,
    close: (Int) -> Unit,
): Unit = js(
    """(function () {
      var registry = globalThis["org.graphiks.kadre:web"] || (globalThis["org.graphiks.kadre:web"] = {});
      registry.kadreWebAttach = attach;
      registry.kadreWebSessionId = sessionId;
      registry.kadreWebSessionState = sessionState;
      registry.kadreWebSubscribeState = subscribeState;
      registry.kadreWebSubscribeTermination = subscribeTermination;
      registry.kadreWebUnsubscribeState = unsubscribeState;
      registry.kadreWebRequestStop = requestStop;
      registry.kadreWebClose = close;
    }())""",
)
