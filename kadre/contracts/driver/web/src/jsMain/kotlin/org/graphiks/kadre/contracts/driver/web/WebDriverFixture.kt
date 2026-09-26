@file:OptIn(kotlin.js.ExperimentalJsExport::class)

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerState
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInputState
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
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement

/**
 * Runs the scenario named by the query string and then publishes the readiness flag the specs wait on.
 *
 * The flag means "the fixture can be driven", and it is an installation barrier: every command
 * listener a spec can dispatch is installed by its scenario function, synchronously, before the flag
 * is set here. A listener whose body drives the application's surface waits for the holder its
 * application block fills, because the runtime starts that block asynchronously — after this task —
 * while the listeners exist already.
 */
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
        "input-key" -> inputKeyScenario()
        "input-wheel" -> inputWheelScenario()
        "input-pointer" -> inputPointerScenario()
        "input-pointer-multi" -> inputPointerMultiScenario()
        "input-pointer-cancel" -> inputPointerCancelScenario()
        "input-focus" -> inputFocusScenario()
        "input-terminal" -> inputTerminalScenario()
        "input-default-behavior" -> inputDefaultBehaviorScenario()
        "input-pointer-capture" -> inputPointerCaptureScenario()
        "input-touch-deferred" -> inputTouchDeferredScenario()
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
    parentScope.installCommand("kadre-resize-surface") { host.style.width = "640px" }
    val attached = host.attachKadre(parentScope) {
        val surface = checkNotNull(primarySurface.value)
        launch {
            surface.state.collect { state ->
                host.setAttribute("data-kadre-surface-metrics", state.metricsEncoding())
                host.setAttribute("data-kadre-surface-physical", state.physicalEncoding())
            }
        }
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/** Records the admission results of three requests issued in one task, and of a request after removal. */
private fun surfaceRedrawScenario() {
    val host = createHost("surface-redraw")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val surfaceReady = CompletableDeferred<HostSurface>()
    var admitted = 0
    parentScope.installCommand("kadre-request-redraw") {
        val surface = surfaceReady.await()
        val results = List(3) { surface.requestRedraw() }
        host.setAttribute("data-kadre-redraw-admission", results.joinToString(",") { it.admission() })
    }
    parentScope.installCommand("kadre-remove-host") { host.remove() }
    parentScope.installCommand("kadre-request-redraw-detached") {
        // The host is gone by then, so the post-detach readback lands on the document body.
        val surface = surfaceReady.await()
        val body = document.body!!
        body.setAttribute("data-kadre-surface-attachment", surface.state.value.attachment.name.lowercase())
        body.setAttribute("data-kadre-redraw-detached", surface.requestRedraw().admission())
    }
    val attached = host.attachKadre(parentScope) {
        val surface = checkNotNull(primarySurface.value)
        surfaceReady.complete(surface)
        launch {
            surface.events.collect { event ->
                if (event is SurfaceEvent.RedrawRequested) {
                    admitted += 1
                    host.setAttribute("data-kadre-redraw-count", admitted.toString())
                }
            }
        }
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
    val surfaceReady = CompletableDeferred<HostSurface>()
    parentScope.installCommand("kadre-surface-activity") {
        val surface = surfaceReady.await()
        host.style.width = "480px"
        // The browser delivers the resize observation in a later rendering step than the frame
        // that this task arms, so the redraw is requested only once the observation exists.
        surface.state.first { it.revision.value > 0L }
        surface.requestRedraw()
    }
    val attached = host.attachKadre(parentScope) {
        val surface = checkNotNull(primarySurface.value)
        surfaceReady.complete(surface)
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
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/** The element escape hatch: one lease writes an attribute the spec reads straight off the element. */
private fun elementLeaseScenario() {
    val host = createHost("element-lease")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val surfaceReady = CompletableDeferred<HostSurface>()
    parentScope.installCommand("kadre-lease") {
        val surface = surfaceReady.await()
        host.setAttribute("data-kadre-lease-result", surface.leased { it.setAttribute("data-kadre-lease", "seen") })
    }
    val attached = host.attachKadre(parentScope) {
        surfaceReady.complete(checkNotNull(primarySurface.value))
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
    val surfaceReady = CompletableDeferred<HostSurface>()
    parentScope.installCommand("kadre-lease-concurrent-close") {
        val surface = surfaceReady.await()
        val first = surface.leased { element ->
            element.setAttribute("data-kadre-lease", "seen")
            var concurrent = "unobserved"
            parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
                concurrent = surface.leased { }
            }
            host.setAttribute("data-kadre-lease-concurrent", concurrent)
            surface.requestRedraw()
            surface.requestRedraw()
            var afterClose = "unobserved"
            parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
                afterClose = surface.leased { }
            }
            host.setAttribute("data-kadre-lease-closed", afterClose)
            host.setAttribute("data-kadre-surface-attachment", surface.state.value.attachment.name.lowercase())
        }
        host.setAttribute("data-kadre-lease-result", first)
    }
    val attached = host.attachKadre(parentScope, policy = policy) {
        surfaceReady.complete(checkNotNull(primarySurface.value))
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

/**
 * The Phase 3 input scenarios.
 *
 * Every one of them attaches a host through the public API, observes the surface's own input stream
 * — `HostSurface.input.state` and `HostSurface.input.events` — and publishes what it read as
 * `data-kadre-*` attributes of the host element. The specs drive real browser input (keyboard, mouse,
 * wheel, focus) or, where Chromium cannot produce it, a synthetic event, and correlate it with those
 * attributes: the observations are the public model's values, never an internal journal of the
 * fixture, so an attribute a spec reads is a fact a consumer of this API can read too.
 *
 * The commands are installed synchronously, before `main` publishes `data-kadre-ready` (the
 * installation barrier), and each body waits for the surface its application block publishes, because
 * the runtime starts that block after this task.
 */

/** The handles a fixture command waits for: the surface and the session the application block owns. */
private class InputHandles(val host: HTMLElement) {
    val surface: CompletableDeferred<HostSurface> = CompletableDeferred()
    val session: CompletableDeferred<KadreSession> = CompletableDeferred()
}

/**
 * Installs one input scenario: the host, its input observation and the commands [configure] adds.
 *
 * The scenario returns as soon as every listener exists, which is what makes the readiness flag
 * meaningful; the fixture's own attributes are read by the spec, never by the fixture.
 */
private fun inputScenario(
    name: String,
    configure: CoroutineScope.(InputHandles) -> Unit = {},
) {
    val host = createHost(name)
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val handles = InputHandles(host)
    // The observation belongs to the scenario's own scope rather than to the application block: a
    // spec observes the input stream *across* a close, which is where the flow's completion and the
    // frozen snapshot are read, so the collector may not be cancelled with the application.
    parentScope.launch { InputObservation(host).install(parentScope, handles.surface.await()) }
    parentScope.configure(handles)
    val attached = host.attachKadre(parentScope) {
        handles.surface.complete(checkNotNull(primarySurface.value))
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
    if (attached is KadreResult.Success) {
        handles.session.complete(attached.value)
        observeSession(attached.value, name, parentScope)
    }
}

/** One real key sequence; the two key specs assert the order and the modifiers it publishes. */
private fun inputKeyScenario() = inputScenario("input-key")

/** One real wheel, plus the wheel variants Chromium cannot produce; the two wheel specs read it. */
private fun inputWheelScenario() = inputScenario("input-wheel")

/** Real pointer input over the host: entry, motion, a primary press and its release. */
private fun inputPointerScenario() = inputScenario("input-pointer")

/** Real multi-button input and the one pointer identity the runtime keeps, whatever DOM pointer. */
private fun inputPointerMultiScenario() = inputScenario("input-pointer-multi")

/** A press the browser really reported, then a `pointercancel` Chromium never produces for a mouse. */
private fun inputPointerCancelScenario() = inputScenario("input-pointer-cancel")

/**
 * A key and a button held, then a real loss of activation.
 *
 * The loss is a real focus move: the fixture owns a second focusable element outside the host, and
 * the spec focuses it. No synthetic event is involved, and the focus observation travels the same
 * lifecycle path a host's own focus change does.
 */
private fun inputFocusScenario() {
    createFocusOutside()
    inputScenario("input-focus")
}

/** A held key across the session's own stop, which is the close the terminal spec freezes. */
private fun inputTerminalScenario() = inputScenario("input-terminal") { handles ->
    installCommand("kadre-stop-input") { handles.session.await().requestStop() }
}

/**
 * The page's own default behaviour under both members of `InputDefaultBehavior`.
 *
 * The document is made scrollable here, because the browser's wheel and arrow defaults act on the
 * scrollable ancestor: without a scrollable page there would be no default to observe.
 */
private fun inputDefaultBehaviorScenario() {
    document.body!!.style.height = "4000px"
    createFocusOutside()
    inputScenario("input-default-behavior") { handles ->
        installCommand("kadre-behavior-host-default") {
            handles.host.setAttribute(
                "data-kadre-behavior-host-default",
                handles.surface.await().applyDefaultBehavior(InputDefaultBehavior.HostDefault),
            )
        }
        installCommand("kadre-behavior-suppress") {
            handles.host.setAttribute(
                "data-kadre-behavior-suppress",
                handles.surface.await().applyDefaultBehavior(InputDefaultBehavior.SuppressWhenPossible),
            )
        }
    }
}

/** The two capture modes this backend promises, asked for with and without an owned pointer. */
private fun inputPointerCaptureScenario(): Unit {
    createFocusOutside()
    inputScenario("input-pointer-capture") { handles ->
        installCommand("kadre-capture-unowned") {
            handles.host.setAttribute(
                "data-kadre-capture-unowned",
                handles.surface.await().applyCapture(PointerCaptureMode.Confined),
            )
        }
        installCommand("kadre-capture-locked") {
            handles.host.setAttribute(
                "data-kadre-capture-locked",
                handles.surface.await().applyCapture(PointerCaptureMode.Locked),
            )
        }
        installCommand("kadre-capture-confined") {
            handles.host.setAttribute(
                "data-kadre-capture-confined",
                handles.surface.await().applyCapture(PointerCaptureMode.Confined),
            )
        }
    }
}

/**
 * The touch boundary of this phase (D12): a real touch on a touch-enabled page, observed as nothing.
 *
 * The spec drives `page.touchscreen` in a browsing context that declares touch, so the pointer events
 * the element receives are real ones; the port refuses the kind whole, so the surface publishes no
 * pointer, no touch and no event.
 */
private fun inputTouchDeferredScenario() = inputScenario("input-touch-deferred")

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

/**
 * Installs one of the fixture's command listeners, synchronously, before `main` publishes readiness.
 *
 * The body runs in its own task of [this] scope rather than inside the DOM dispatch, so a listener
 * that drives the surface can wait for the application block to publish it: the block runs
 * asynchronously, but — unlike a listener registered inside it — this registration exists as soon as
 * the scenario function returns, which is what makes the readiness flag an installation barrier.
 */
private fun CoroutineScope.installCommand(name: String, body: suspend () -> Unit) {
    document.addEventListener(name, { launch { body() } })
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
    is KadreFailure.InteractionRequired -> "interactionRequired:${reason.name.lowercase()}"
    KadreFailure.ParentScopeCancelled -> "parentScopeCancelled"
    KadreFailure.ApplicationFailure -> "applicationFailure"
    else -> "unexpected-failure"
}

/**
 * Hands the application's opaque factory key to the page, as `kadre/INTEROP-EXPORTS.md` section 6
 * describes: the application exports the key and JavaScript only carries it back to `KadreWeb.attach`.
 */
private fun publishApplicationFactoryKey(key: String): Unit = js("globalThis.kadreApplicationFactory = key")

/**
 * Publishes one input scenario's observations as attributes of the host, from the surface's own
 * stream.
 *
 * Four facts are read, and each is a public value rather than a fixture journal:
 *
 * - `data-kadre-input-state`: the whole `SurfaceInputState` the runtime published — pressed physical
 *   keys, modifiers, the pointers the runtime keeps with their kinds, buttons and positions, the
 *   number of touches, and the input revision;
 * - `data-kadre-input-caps`: the derived `InputCapabilities`, which is where the touch and gesture
 *   boundary of this phase is observable;
 * - `data-kadre-input-events`: every `InputEvent` the surface published, in order, each carrying the
 *   input revision it was published with — so a spec can read both the payload and its order. The
 *   entries are separated by `;`, because a scroll payload carries a comma of its own;
 * - `data-kadre-input-order`: for every event, whether the state cell already carried that event's
 *   revision and its change when the event was observed, which is the state-before-event claim;
 * - `data-kadre-input-resets`: how many `StateReset` events were published and why.
 *
 * `data-kadre-surface-state` is the committed `SurfaceState` (attachment, `inputDefaultBehavior` and
 * `pointerCapture`), and `data-kadre-input-flow-closed` is set when the input events flow completes —
 * the surface's close, observed as a consumer observes it.
 */
private class InputObservation(private val host: HTMLElement) {
    private val events: MutableList<String> = mutableListOf()
    private val order: MutableList<String> = mutableListOf()
    private val resets: MutableList<String> = mutableListOf()

    fun install(scope: CoroutineScope, surface: HostSurface) {
        // The empty observation is published first, so a spec that drives no input at all still reads
        // a value the fixture really produced rather than a missing attribute.
        host.setAttribute("data-kadre-input-events", "")
        host.setAttribute("data-kadre-input-count", "0")
        host.setAttribute("data-kadre-input-order", "")
        host.setAttribute("data-kadre-input-resets", "0:")
        scope.launch {
            surface.input.state.collect { state ->
                host.setAttribute("data-kadre-input-state", state.encoded())
                host.setAttribute("data-kadre-input-caps", state.capabilities.encoded())
            }
        }
        // The event subscription is registered undispatched, so it exists by the time this call
        // returns: the surface's event flow is cold, and an observation made before its collector
        // registered would be delivered to nobody. Reading the capability attribute a spec waits on
        // is therefore also the proof that every later event of the scenario reaches the observer.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            surface.input.events.collect { event ->
                val state = surface.input.state.value
                events += event.encoded()
                order += "${event.revisionless()}:${if (state.agreesWith(event)) "synced" else "unsynced"}"
                if (event is InputEvent.StateReset) {
                    resets += event.reason.name.replaceFirstChar(Char::lowercase)
                }
                host.setAttribute("data-kadre-input-events", events.joinToString(";"))
                host.setAttribute("data-kadre-input-count", events.size.toString())
                host.setAttribute("data-kadre-input-order", order.joinToString(";"))
                host.setAttribute("data-kadre-input-resets", "${resets.size}:${resets.joinToString(",")}")
                if (event is InputEvent.PointerMoved) {
                    // A capture confines the pointer to the element, so a motion beyond its logical box
                    // is exactly what a real capture delivers and a release-less capture ends with.
                    val size = surface.state.value.logicalSize
                    val outside = event.position.x < 0.0 || event.position.y < 0.0 ||
                        event.position.x > size.width || event.position.y > size.height
                    host.setAttribute("data-kadre-pointer-outside", if (outside) "true" else "false")
                    host.setAttribute("data-kadre-pointer-motion", event.position.encoded())
                }
            }
            host.setAttribute("data-kadre-input-flow-closed", "true")
        }
        scope.launch {
            surface.state.collect { state -> host.setAttribute("data-kadre-surface-state", state.encoded()) }
        }
    }
}

/**
 * The focus target the focus spec moves the focus to: a real, focusable element outside the host.
 *
 * It exists so the loss of activation is a real focus change of the page rather than a synthetic
 * event, and it is created by the fixture before readiness, like every other element of a scenario.
 */
private fun createFocusOutside() {
    val element = document.createElement("div") as HTMLElement
    element.setAttribute("data-kadre-focus-outside", "true")
    element.tabIndex = 0
    document.body!!.appendChild(element)
}

/** Applies one `InputDefaultBehavior` through the public update and renders the outcome. */
private suspend fun HostSurface.applyDefaultBehavior(behavior: InputDefaultBehavior): String =
    apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(behavior))).outcomeEncoding()

/** Asks for one `PointerCaptureMode` through the public update and renders the outcome. */
private suspend fun HostSurface.applyCapture(mode: PointerCaptureMode): String =
    apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(mode))).outcomeEncoding()

/**
 * The outcome of one `apply`, as the specs assert it: the fields the surface rejected are named with
 * the failure of each, so a spec can tell an admitted field from a refused one.
 */
private fun KadreResult<SurfaceUpdateOutcome>.outcomeEncoding(): String = when (this) {
    is KadreResult.Success -> when (val outcome = value) {
        is SurfaceUpdateOutcome.Applied -> "applied"
        is SurfaceUpdateOutcome.PartiallyApplied -> "partiallyApplied[" + outcome.rejected.joinToString(
            separator = ",",
        ) { rejection -> "${rejection.field.encoded()}=${rejection.failure.encoding()}" } + "]"
    }

    is KadreResult.Failure -> "failure:${reason.encoding()}"
}

/** The committed surface state, as the specs read it: attachment, behaviour, capture and revision. */
private fun SurfaceState.encoded(): String =
    "${attachment.name.lowercase()}|${inputDefaultBehavior.name.lowercase()}|" +
        "${pointerCapture.name.lowercase()}|rev=${revision.value}"

/** The whole input state, as the specs read it. */
private fun SurfaceInputState.encoded(): String =
    "rev=${revision.value}" +
        " keys=[${keyboard.pressedKeys.map { it.encoded() }.sorted().joinToString(",")}]" +
        " mods=[${modifiers.pressed.map { it.name }.sorted().joinToString(",")}]" +
        " pointers=[${pointers.joinToString(",") { it.encoded() }}]" +
        " touches=${touches.size}"

/** The derived input capabilities: the feature availabilities and the gesture constraint set. */
private fun InputCapabilities.encoded(): String =
    "keyboard=${keyboard.encoded()}" +
        " pointer=${pointer.encoded()}" +
        " touch=${touch.encoded()}" +
        " gestures=${gestures.encoded()}"

private fun FeatureAvailability.encoded(): String = when (this) {
    FeatureAvailability.Available -> "available"
    FeatureAvailability.Unsupported -> "unsupported"
    is FeatureAvailability.RequiresPermission -> "requiresPermission"
    is FeatureAvailability.RequiresInteraction -> "requiresInteraction"
    is FeatureAvailability.Unavailable -> "unavailable"
}

private fun Capability<Set<GestureKind>>.encoded(): String = when (this) {
    is Capability.Unsupported -> "unsupported:${failure.operation.name.lowercase()}"
    is Capability.Supported -> "supported[${constraints.map { it.name }.sorted().joinToString("+")}]"
}

private fun PointerState.encoded(): String =
    "${kind.name.lowercase()}#${pressedButtons.map { it.encoded() }.sorted().joinToString("+")}" +
        "@${position?.encoded() ?: "none"}${if (pen == null) "" else ":pen"}"

private fun PointerButton.encoded(): String = when (this) {
    PointerButton.Primary -> "primary"
    PointerButton.Secondary -> "secondary"
    PointerButton.Auxiliary -> "auxiliary"
    PointerButton.Back -> "back"
    PointerButton.Forward -> "forward"
    PointerButton.Barrel -> "barrel"
    PointerButton.Eraser -> "eraser"
    is PointerButton.Other -> "other:$nativeCode"
}

private fun PhysicalKey.encoded(): String = when (this) {
    is PhysicalKey.Code -> "code:$usagePage:$usageId"
    is PhysicalKey.Unidentified -> "unidentified:$nativeCode"
}

private fun LogicalKey.encoded(): String = when (this) {
    is LogicalKey.Character -> value
    is LogicalKey.Named -> value.name
    is LogicalKey.Unidentified -> "unidentified:$nativeCode"
}

private fun ScrollDelta.encoded(): String = when (this) {
    is ScrollDelta.Logical -> "logical(${number(x)},${number(y)})"
    is ScrollDelta.Lines -> "lines(${number(x)},${number(y)})"
}

private fun LogicalPoint.encoded(): String = "(${number(x)},${number(y)})"

private fun LogicalDelta.encoded(): String = "(${number(x)},${number(y)})"

private fun SurfaceProperty.encoded(): String = when (this) {
    SurfaceProperty.Cursor -> "cursor"
    SurfaceProperty.PointerCapture -> "pointerCapture"
    SurfaceProperty.HitTesting -> "hitTesting"
    SurfaceProperty.InputDefaultBehavior -> "inputDefaultBehavior"
}

/** One published input event, with the input revision it carried. */
private fun InputEvent.encoded(): String = when (this) {
    is InputEvent.Key -> "key:${logicalKey.encoded()}:${keyState.name.lowercase()}" +
        ":mods[${modifiers.pressed.map { it.name }.sorted().joinToString("+")}]:rev=${stateRevision.value}"

    is InputEvent.PointerEntered -> "enter:${kind.name.lowercase()}@${position.encoded()}:rev=${stateRevision.value}"

    is InputEvent.PointerMoved -> "move:${kind.name.lowercase()}@${position.encoded()}" +
        ":d=${delta.encoded()}:rev=${stateRevision.value}"

    is InputEvent.PointerButtonChanged -> "button:${button.encoded()}:${buttonState.name.lowercase()}" +
        "@${position.encoded()}:rev=${stateRevision.value}"

    is InputEvent.PointerLeft -> "leave:${kind.name.lowercase()}:rev=${stateRevision.value}"

    is InputEvent.Scrolled -> "scroll:${delta.encoded()}:rev=${stateRevision.value}"

    is InputEvent.StateReset -> "reset:${reason.name.replaceFirstChar(Char::lowercase)}:rev=${stateRevision.value}"

    else -> "other:rev=${stateRevision.value}"
}

/**
 * One event's observation, revision excluded: what the ordering attribute names its entry by.
 *
 * The payload is the whole encoded event without its revision, because the revision is what the entry's
 * value is about: a scroll delta carries a comma of its own, which is why the lists of the fixture are
 * separated by `;`.
 */
private fun InputEvent.revisionless(): String = encoded().substringBefore(":rev=")

/**
 * Whether the state cell the surface publishes already carried this event when the event was
 * observed: the revision the event names is the state's own, and the change the event describes is in
 * it — a key press is in the pressed set, a release is not, a button transition is (or is no longer)
 * among the pointer's buttons, and a reset left a neutral snapshot.
 *
 * That conjunction is the state-before-event claim: a consumer that reads `input.state` when it
 * receives an event reads the state that event produced, never the one before it.
 */
private fun SurfaceInputState.agreesWith(event: InputEvent): Boolean {
    if (revision != event.stateRevision) return false
    return when (event) {
        is InputEvent.Key -> when (event.keyState) {
            KeyState.Pressed -> event.physicalKey in keyboard.pressedKeys
            KeyState.Released -> event.physicalKey !in keyboard.pressedKeys
        }

        is InputEvent.PointerButtonChanged -> when (event.buttonState) {
            PointerButtonState.Pressed -> pointers.any { event.button in it.pressedButtons }
            PointerButtonState.Released -> pointers.none { event.button in it.pressedButtons }
        }

        is InputEvent.StateReset -> keyboard.pressedKeys.isEmpty() && pointers.isEmpty() && modifiers.pressed.isEmpty()

        else -> true
    }
}
