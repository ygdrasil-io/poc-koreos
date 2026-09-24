@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asPromise
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreFailure as KadreFailureValue
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.w3c.dom.HTMLElement
import kotlin.js.Promise

/**
 * `@kadre/host` for the Kotlin/Wasm target: the same names as the Kotlin/JS facade over the Wasm
 * SDK `org.w3c.dom.HTMLElement`.
 *
 * Kotlin/Wasm 2.4 only allows `@JsExport` on functions, and only over external, primitive, string
 * and function types, so this facade cannot annotate its classes, its `KadreWeb` object or its
 * factory reference for JavaScript, and `Deferred.asPromise` is typed `Promise<JsAny?>` there. The
 * Wasm module therefore ships the same Kotlin surface but no JavaScript surface; see
 * `kadre/platform/web/types/kadre-host.d.ts` and the task report for the toolchain evidence.
 */

/** Raised by [KadreWeb.attach] when the host refuses the attachment. */
public class KadreHostError(public val failure: KadreFailure) : Throwable("Kadre host failure: ${failure.kind}")

/** Entry point shared by the JS and Wasm facades. */
public object KadreWeb {
    /**
     * Attaches a Kadre session to [element].
     *
     * The session owns a fresh `MainScope`, released after an attach failure or once the session
     * publishes its terminal outcome. A refused attachment throws [KadreHostError].
     */
    public fun attach(
        element: HTMLElement,
        applicationFactory: KadreApplicationFactoryRef,
        options: KadreWebOptions? = null,
    ): KadreSessionHandle {
        val policy = options.selectedPolicy()
        val attachmentPolicy = options.selectedAttachmentPolicy()
        val scope = MainScope()
        val attached = element.attachKadre(
            parentScope = scope,
            applicationFactory = applicationFactory.factory,
            policy = policy,
            attachmentPolicy = attachmentPolicy,
        )
        return when (attached) {
            is KadreResult.Success -> KadreSessionHandle(attached.value, scope)
            is KadreResult.Failure -> {
                scope.cancel()
                throw KadreHostError(attached.reason.toInterop())
            }
        }
    }
}

/**
 * Host handle over one attached session.
 *
 * The handle owns the `MainScope` of its session: the scope is cancelled once the session
 * publishes its terminal outcome, and the terminal outcome stays available afterwards.
 */
public class KadreSessionHandle internal constructor(
    private val session: KadreSession,
    private val scope: CoroutineScope,
    /**
     * Seam for the observer-failure path: the default reports the failure out of band, and the
     * contract tests inject a recorder so the assertion does not depend on a global error hook.
     */
    private val reportObserverFailure: (Throwable) -> Unit = ::observerFailure,
) {
    private val termination = CompletableDeferred<KadreSessionOutcome>()

    init {
        scope.launch {
            termination.complete(session.awaitTermination().toInterop())
            // The session is over: release the scope that hosted its observers and waiters.
            scope.cancel()
        }
    }

    /** Opaque session identity. It is not parseable and not stable across processes. */
    public val id: String get() = session.id.toString()

    /** The current snapshot. */
    public val state: KadreSessionSnapshot get() = session.state.value.toInterop()

    /**
     * Calls [observer] synchronously with the current snapshot, then once per state change.
     *
     * A throwing observer is unsubscribed and its failure is rethrown asynchronously; it never
     * terminates the session.
     */
    public fun subscribeState(observer: (KadreSessionSnapshot) -> Unit): () -> Unit {
        var delivered = session.state.value
        try {
            observer(delivered.toInterop())
        } catch (error: Throwable) {
            reportObserverFailure(error)
            return { }
        }
        val job = scope.launch {
            session.state.collect { state ->
                if (state == delivered) return@collect
                delivered = state
                observer(state.toInterop())
            }
        }
        return { job.cancel() }
    }

    /** Asks the session to stop. */
    public fun requestStop(): Unit = session.requestStop()

    /** Closes the session. The terminal outcome remains available through [awaitTermination]. */
    public fun close(): Unit = session.close()

    /**
     * Resolves with the terminal outcome; it resolves for every later caller as well.
     *
     * On Wasm `Deferred.asPromise` is typed `Promise<JsAny?>`; the resolved value is the outcome
     * object of this facade.
     */
    public fun awaitTermination(): Promise<JsAny?> = termination.asPromise()
}

internal fun KadreWebOptions?.selectedPolicy(): KadrePolicy = when (this?.policy) {
    null, "default" -> KadrePolicies.Default
    "realtime" -> KadrePolicies.Realtime
    "recording" -> KadrePolicies.Recording
    // The union is closed: an unknown profile is refused instead of silently replaced.
    else -> throw KadreHostError(KadreFailureValue.InvalidRequest("options.policy").toInterop())
}

internal fun KadreWebOptions?.selectedAttachmentPolicy(): WebAttachmentPolicy = when (this?.attachmentPolicy) {
    null, "stopWhenDetached" -> WebAttachmentPolicy.StopWhenDetached
    "manual" -> WebAttachmentPolicy.Manual
    else -> throw KadreHostError(KadreFailureValue.InvalidRequest("options.attachmentPolicy").toInterop())
}
