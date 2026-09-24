@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, kotlin.js.ExperimentalJsExport::class)

package org.graphiks.kadre.contracts.driver.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.WebAttachmentPolicy
import org.graphiks.kadre.platform.web.asHostRef
import org.graphiks.kadre.platform.web.attachKadre
import org.graphiks.kadre.platform.web.hostKey
import org.graphiks.kadre.platform.web.publishHostBindings
import org.graphiks.kadre.platform.web.withWebElement
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement

public fun main() {
    when (scenarioName()) {
        "initial-disconnected" -> initialDisconnectedScenario()
        "durable-detach" -> attachScenario("durable")
        "detach-reinsert" -> attachScenario("reinsert")
        "shadow-root" -> attachScenario("shadow")
        "shadow-inner-detach" -> attachScenario("shadow-inner")
        "inter-document" -> attachScenario("transfer")
        "manual-reconnect" -> manualReconnectScenario()
        "manual-shadow-reconnect" -> manualShadowReconnectScenario()
        "manual-detach-reconnect" -> manualDetachReconnectScenario()
        "independent" -> independentScenario()
        "duplicate" -> duplicateScenario()
        "focus" -> focusScenario()
        "pagehide" -> attachScenario("pagehide")
        "host-owned" -> hostOwnedScenario()
        "identity-no-expando" -> identityNoExpandoScenario()
        "surface-metrics" -> surfaceMetricsScenario()
        "surface-redraw" -> surfaceRedrawScenario()
        "surface-no-renderer" -> surfaceNoRendererScenario()
        "element-lease" -> elementLeaseScenario()
        "element-lease-close" -> elementLeaseCloseScenario()
        "typescript-consumer" -> typescriptConsumerScenario()
        else -> phaseZeroScenario()
    }
    document.body!!.setAttribute("data-kadre-ready", "true")
}

private fun initialDisconnectedScenario() {
    val host = createHost("initial-disconnected", connected = false)
    document.body!!.setAttribute("data-kadre-attach", describeAttach(attachAndObserve(host, "initial")))
    document.body!!.appendChild(host)
}

private fun attachScenario(key: String) {
    val host = createHost(key)
    document.body!!.setAttribute("data-kadre-attach", describeAttach(attachAndObserve(host, key)))
}

private fun manualReconnectScenario() {
    val host = createHost("manual", connected = false)
    document.body!!.setAttribute(
        "data-kadre-attach",
        describeAttach(attachAndObserve(host, "manual", WebAttachmentPolicy.Manual)),
    )
    document.addEventListener("kadre-connect-manual", {
        if (!host.isConnected) document.body!!.appendChild(host)
    })
}

private fun manualShadowReconnectScenario() {
    val host = createHost("manual-shadow", connected = false)
    document.body!!.setAttribute(
        "data-kadre-attach",
        describeAttach(attachAndObserve(host, "manual-shadow", WebAttachmentPolicy.Manual)),
    )
    document.addEventListener("kadre-connect-manual-shadow", {
        val shadowHost = document.querySelector("[data-kadre-shadow-container='manual-reconnect']")
        if (!host.isConnected) shadowHost?.shadowRoot?.appendChild(host)
    })
}

private fun manualDetachReconnectScenario() {
    val host = createHost("manual-detach")
    attachAndObserve(host, "manual-detach", WebAttachmentPolicy.Manual)
    document.addEventListener("kadre-reconnect-manual", {
        if (!host.isConnected) document.body!!.appendChild(host)
    })
}

private fun independentScenario() {
    attachAndObserve(createHost("independent-a"), "independent-a")
    attachAndObserve(createHost("independent-b"), "independent-b")
}

private fun duplicateScenario() {
    val host = createHost("duplicate")
    attachAndObserve(host, "duplicate")
    val duplicate = attachAndObserve(host, "duplicate-second")
    document.body!!.setAttribute("data-kadre-duplicate-attach", describeAttach(duplicate))
}

private fun focusScenario() {
    attachAndObserve(createHost("focus-a"), "focus-a")
    attachAndObserve(createHost("focus-b"), "focus-b")
}

private fun identityNoExpandoScenario() {
    val host = createHost("identity")
    document.addEventListener("kadre-attach-identity", {
        val attached = attachAndObserve(host, "identity")
        document.body!!.setAttribute("data-kadre-identity-attach", describeAttach(attached))
    })
}

private fun hostOwnedScenario() {
    val host = createHost("host-owned")
    val body = document.body!!
    body.setAttribute("data-kadre-dom-before", document.getElementsByTagName("*").length.toString())
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val attached = host.attachKadre(parentScope) {
        body.setAttribute("data-kadre-dom-after", document.getElementsByTagName("*").length.toString())
        body.setAttribute("data-kadre-window-primary", if (windows.state.value.primary == null) "null" else "present")
        body.setAttribute("data-kadre-window-count", windows.state.value.windows.size.toString())
        launch {
            val request = windows.requestWindow(WindowSpec())
            val outcome = when (request) {
                is KadreResult.Success -> request.value.await()
                is KadreResult.Failure -> null
            }
            body.setAttribute(
                "data-kadre-request-window",
                if (outcome == WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow))) {
                    "unsupported"
                } else {
                    "unexpected"
                },
            )
        }
        awaitCancellation()
    }
    body.setAttribute("data-kadre-attach", describeAttach(attached))
}

/**
 * The surface readback scenario: the browser's own size mutation, observed through the public state.
 *
 * The fixture drives the public API only: `HTMLElement.attachKadre`, `KadreScope.primarySurface` and
 * the state flow it publishes. The spec resizes the element (or overrides the browsing context's
 * device pixel ratio first) and reads back the encoding below.
 */
private fun surfaceMetricsScenario() {
    val host = createHost("surface-metrics")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val attached = host.attachKadre(parentScope) {
        val surface = checkNotNull(primarySurface.value)
        launch {
            surface.state.collect { state ->
                host.setAttribute("data-kadre-surface-metrics", state.metricsEncoding())
                host.setAttribute("data-kadre-surface-physical", state.physicalEncoding())
            }
        }
        document.addEventListener("kadre-resize-surface", { host.style.width = "640px" })
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/** Records the admission results of three requests issued in one task, and of a request after removal. */
private fun surfaceRedrawScenario() {
    val host = createHost("surface-redraw")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var admitted = 0
    val attached = host.attachKadre(parentScope) {
        val surface = checkNotNull(primarySurface.value)
        launch {
            surface.events.collect { event ->
                if (event is SurfaceEvent.RedrawRequested) {
                    admitted += 1
                    host.setAttribute("data-kadre-redraw-count", admitted.toString())
                }
            }
        }
        document.addEventListener("kadre-request-redraw", {
            val results = List(3) { surface.requestRedraw() }
            host.setAttribute("data-kadre-redraw-admission", results.joinToString(",") { it.admission() })
        })
        document.addEventListener("kadre-remove-host", { host.remove() })
        document.addEventListener("kadre-request-redraw-detached", {
            // The host is gone by then, so the post-detach readback lands on the document body.
            val body = document.body!!
            body.setAttribute("data-kadre-surface-attachment", surface.state.value.attachment.name.lowercase())
            body.setAttribute("data-kadre-redraw-detached", surface.requestRedraw().admission())
        })
        awaitCancellation()
    }
    if (attached is KadreResult.Success) observeSession(attached.value, "redraw", parentScope)
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/**
 * The no-renderer sentinel: attach, observe a real metrics change and admit a redraw.
 *
 * The DOM count is recorded after the observation was published and the redraw admitted, so any node
 * a renderer added would appear in the count; the primary window is read from the public manager.
 */
private fun surfaceNoRendererScenario() {
    val host = createHost("surface-no-renderer")
    val body = document.body!!
    body.setAttribute("data-kadre-dom-baseline", document.getElementsByTagName("*").length.toString())
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val attached = host.attachKadre(parentScope) {
        val surface = checkNotNull(primarySurface.value)
        launch {
            surface.state.collect { state ->
                host.setAttribute("data-kadre-observed-revision", state.revision.value.toString())
            }
        }
        launch {
            surface.events.collect { event ->
                if (event is SurfaceEvent.RedrawRequested) {
                    // The frame admitted the redraw, and the request waited for the browser-delivered
                    // observation, so the count is read here: a node a renderer created would be in it.
                    body.setAttribute("data-kadre-dom-count", document.getElementsByTagName("*").length.toString())
                    body.setAttribute(
                        "data-kadre-window-primary",
                        if (windows.state.value.primary == null) "null" else "present",
                    )
                    host.setAttribute("data-kadre-observed-redraw", "true")
                }
            }
        }
        document.addEventListener("kadre-surface-activity", {
            launch {
                host.style.width = "480px"
                // The browser delivers the resize observation in a later rendering step than the frame
                // that this task arms, so the redraw is requested only once the observation exists.
                surface.state.first { it.revision.value > 0L }
                surface.requestRedraw()
            }
        })
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/** The element escape hatch: one lease writes an attribute the spec reads straight off the element. */
private fun elementLeaseScenario() {
    val host = createHost("element-lease")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val attached = host.attachKadre(parentScope) {
        val surface = checkNotNull(primarySurface.value)
        document.addEventListener("kadre-lease", {
            launch {
                host.setAttribute("data-kadre-lease-result", surface.leased { it.setAttribute("data-kadre-lease", "seen") })
            }
        })
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/**
 * A lease in flight, a refused concurrent lease, and the close through the redraw overflow path.
 *
 * The policy is the fixture's own: a redraw buffer of one request whose overflow closes the surface
 * (`ContinuousOverflowAction.CloseSource`), so the surface is closed by the redraw path and not by a
 * host detach. The concurrent attempts run undispatched inside the first lease's callback, the only
 * way a non-suspend callback can observe the in-flight state through the public facade.
 */
private fun elementLeaseCloseScenario() {
    val host = createHost("element-lease-close")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val policy = KadrePolicies.Default.copy(
        window = KadrePolicies.Default.window.copy(
            redrawRequests = ContinuousDelivery.Buffered(
                capacity = 1,
                onOverflow = ContinuousOverflowAction.CloseSource,
            ),
        ),
    )
    val attached = host.attachKadre(parentScope, policy = policy) {
        val surface = checkNotNull(primarySurface.value)
        val applicationScope = this
        document.addEventListener("kadre-lease-concurrent-close", {
            launch {
                val first = surface.leased { element ->
                    element.setAttribute("data-kadre-lease", "seen")
                    var concurrent = "unobserved"
                    applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        concurrent = surface.leased { }
                    }
                    host.setAttribute("data-kadre-lease-concurrent", concurrent)
                    surface.requestRedraw()
                    surface.requestRedraw()
                    var afterClose = "unobserved"
                    applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        afterClose = surface.leased { }
                    }
                    host.setAttribute("data-kadre-lease-closed", afterClose)
                    host.setAttribute("data-kadre-surface-attachment", surface.state.value.attachment.name.lowercase())
                }
                host.setAttribute("data-kadre-lease-result", first)
            }
        })
        awaitCancellation()
    }
    if (attached is KadreResult.Success) observeSession(attached.value, "lease-close", parentScope)
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/**
 * The application's entry point for the `@kadre/host` scenario.
 *
 * This is the Kotlin half of `kadre/INTEROP-EXPORTS.md` section 6: the application's Kotlin module
 * owns the factories and sessions, publishes the JavaScript bindings of that instance into the shared
 * registry, and hands JavaScript the opaque key that `KadreWeb.attach` carries back. The consumer is
 * the only thing that attaches, subscribes, stops and awaits an outcome.
 */
@JsExport
public fun applicationFactory(): String {
    publishHostBindings()
    val reference = KadreApplicationFactory { KadreApplication { awaitCancellation() } }.asHostRef()
    return reference.hostKey
}

/**
 * The TypeScript scenario: this application publishes its bindings and its factory key, and the
 * consumer's own `KadreWeb.attach` call does everything else.
 */
private fun typescriptConsumerScenario() {
    publishApplicationFactoryKey(applicationFactory())
}

private fun phaseZeroScenario() {
    val host = createHost("phase0")
    val domBaseline = document.getElementsByTagName("*").length
    host.setAttribute("data-kadre-dom-baseline", domBaseline.toString())
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val applicationStarted = CompletableDeferred<Unit>()

    when (val attached = host.attachKadre(parentScope) {
        host.setAttribute("data-kadre-dom-count", document.getElementsByTagName("*").length.toString())
        applicationStarted.complete(Unit)
        awaitCancellation()
    }) {
        is KadreResult.Success -> {
            host.addEventListener("kadre-phase0-stop", {
                parentScope.launch {
                    attached.value.requestStop()
                    attached.value.awaitTermination()
                    host.setAttribute("data-kadre-state", "stopped")
                    parentScope.cancel()
                }
            })
            parentScope.launch {
                applicationStarted.await()
                host.setAttribute("data-kadre-state", "running")
            }
        }
        is KadreResult.Failure -> {
            host.setAttribute("data-kadre-state", "failed")
            parentScope.cancel()
        }
    }
}

private fun attachAndObserve(
    host: HTMLElement,
    key: String,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
): KadreResult<KadreSession> {
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val result = host.attachKadre(parentScope, attachmentPolicy = attachmentPolicy) {
        launch {
            lifecycle.state.collect { lifecycle ->
                val encoded = lifecycle.encoded()
                host.setAttribute("data-kadre-lifecycle", encoded)
                document.body!!.setAttribute("data-kadre-$key-lifecycle", encoded)
            }
        }
        awaitCancellation()
    }
    if (result is KadreResult.Success) observeSession(result.value, key, parentScope)
    return result
}

private fun observeSession(session: KadreSession, key: String, parentScope: CoroutineScope) {
    parentScope.launch {
        session.state.collect { state ->
            when (state) {
                SessionState.Starting -> document.body!!.setAttribute("data-kadre-$key-session", "starting")
                SessionState.Running -> document.body!!.setAttribute("data-kadre-$key-session", "running")
                SessionState.Stopping -> document.body!!.setAttribute("data-kadre-$key-session", "stopping")
                is SessionState.Terminated -> {
                    document.body!!.setAttribute("data-kadre-$key-session", "terminated")
                    document.body!!.setAttribute("data-kadre-$key-outcome", state.outcome.encoded())
                }
            }
        }
    }
}

private fun createHost(id: String, connected: Boolean = true): HTMLElement =
    (document.createElement("div") as HTMLElement).also { host ->
        host.setAttribute("data-kadre-host", id)
        host.tabIndex = 0
        host.style.width = "320px"
        host.style.height = "180px"
        if (connected) document.body!!.appendChild(host)
    }

private fun scenarioName(): String? = window.location.search
    .removePrefix("?")
    .split('&')
    .firstOrNull { it.startsWith("scenario=") }
    ?.substringAfter('=')

private fun describeAttach(result: KadreResult<KadreSession>): String = when (result) {
    is KadreResult.Success -> "success"
    is KadreResult.Failure -> when (result.reason) {
        KadreFailure.InvalidRequest("element") -> "invalid-element"
        KadreFailure.AlreadyInUse(KadreResourceKind.Host) -> "already-in-use-host"
        else -> "unexpected-failure"
    }
}

private fun LifecycleState.encoded(): String =
    "${attachment.toString().lowercase()}-${visibility.toString().lowercase()}-${activation.toString().lowercase()}"

private fun SessionOutcome.encoded(): String = when (this) {
    is SessionOutcome.Stopped -> reason.toString().replaceFirstChar(Char::lowercase).replace("D", "-d")
    SessionOutcome.Completed -> "completed"
    is SessionOutcome.Failed -> "failed"
}

/** The metrics readback as the specs read it: logical size, scale and revision. */
private fun SurfaceState.metricsEncoding(): String =
    "${number(logicalSize.width)}x${number(logicalSize.height)}@${number(scaleFactor)}#${revision.value}"

/** The derived physical size, so the device pixel ratio readback is observable on its own. */
private fun SurfaceState.physicalEncoding(): String = "${physicalSize.width}x${physicalSize.height}"

/** Renders a double without a trailing `.0`, so the encoding stays stable across targets. */
private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** Renders an admission result; the specs assert these strings. */
private fun KadreResult<*>.admission(): String = when (this) {
    is KadreResult.Success -> "success"
    is KadreResult.Failure -> reason.encoding()
}

/** Runs one lease through the public escape hatch and renders its outcome. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
private suspend fun HostSurface.leased(block: (HTMLElement) -> Unit): String =
    when (val result = withWebElement { element -> block(element); "ok" }) {
        is KadreResult.Success -> "granted"
        is KadreResult.Failure -> result.reason.encoding()
    }

/** The failure kinds the surface scenarios distinguish, in the encoding the specs assert. */
private fun KadreFailure.encoding(): String = when (this) {
    is KadreFailure.TemporarilyUnavailable -> "temporarilyUnavailable:$retryable"
    is KadreFailure.Closed -> "closed:${resource.name.lowercase()}"
    is KadreFailure.InvalidRequest -> "invalidRequest:${field ?: "unknown"}"
    is KadreFailure.Unsupported -> "unsupported:${operation.name.lowercase()}"
    is KadreFailure.AlreadyInUse -> "alreadyInUse:${resource.name.lowercase()}"
    is KadreFailure.SourceOverflow -> "sourceOverflow:${resource.name.lowercase()}"
    KadreFailure.ParentScopeCancelled -> "parentScopeCancelled"
    KadreFailure.ApplicationFailure -> "applicationFailure"
    else -> "unexpected-failure"
}

/**
 * Hands the application's opaque factory key to the page, as `kadre/INTEROP-EXPORTS.md` section 6
 * describes: the application exports the key and JavaScript only carries it back to `KadreWeb.attach`.
 */
private fun publishApplicationFactoryKey(key: String): Unit = js("globalThis.kadreApplicationFactory = key")
