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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreScope
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
import org.graphiks.kadre.display.DisplayEvent
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayManager
import org.graphiks.kadre.display.DisplayManagerState
import org.graphiks.kadre.display.DisplayMode
import org.graphiks.kadre.display.DisplayState
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropOffer
import org.graphiks.kadre.input.DropOfferId
import org.graphiks.kadre.input.DropOfferState
import org.graphiks.kadre.input.DropOfferTerminationReason
import org.graphiks.kadre.input.DropTransfer
import org.graphiks.kadre.input.DroppedItem
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
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputPurpose
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.input.TextInputState
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionActionOutcome
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.platform.web.WebAttachmentPolicy
import org.graphiks.kadre.platform.web.WebWindowHost
import org.graphiks.kadre.platform.web.WebWindowProvider
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
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.DataTransfer
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.unsafeCast

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
        "touch" -> touchScenario("touch")
        "touch-interaction" -> touchInteractionScenario()
        "touch-focus" -> touchFocusScenario()
        "drop" -> dropScenario(accepting = true)
        "drop-reject" -> dropScenario(accepting = false)
        "text-input" -> textInputScenario(singleLine = true)
        "text-area" -> textInputScenario(singleLine = false)
        "web-interaction" -> inputInteractionScenario()
        "typescript-consumer" -> typescriptConsumerScenario()
        "window-provider" -> windowProviderScenario()
        "shadow-late-reinsert" -> shadowLateReinsertScenario()
        "host-facade" -> hostFacadeScenario()
        "host-provider" -> hostProviderScenario()
        "display" -> displayScenario()
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
    val attached = attachAndObserve(host, "manual-detach", WebAttachmentPolicy.Manual)
    document.addEventListener("kadre-reconnect-manual", {
        if (!host.isConnected) document.body!!.appendChild(host)
    })
    // The stop leg of `web-manual-detach-and-stop`: a Manual session the host asked to stop while it
    // is still attached terminates with the outcome the stop requested.
    if (attached is KadreResult.Success) {
        document.addEventListener("kadre-stop-manual-detach", { attached.value.requestStop() })
    }
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
        body.setAttribute("data-kadre-window-caps", windows.state.value.capabilities.requestWindow.encoded())
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
 * scrollable ancestor: without a scrollable page there would be no default to observe. The focus
 * target is created *after* the host, so it is the next stop of the page's tab order and a spec can
 * prove that `Tab`'s default really ran by reading the focus it moved to.
 */
private fun inputDefaultBehaviorScenario() {
    document.body!!.style.height = "4000px"
    inputScenario("input-default-behavior") { handles ->
        createFocusOutside()
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
 * The touch boundary of this phase (D12 as rewritten): a real touch on a touch-enabled page, now
 * delivered.
 *
 * The spec drives `page.touchscreen` in a browsing context that declares touch, so the contact the
 * element receives is a real one, and it delivers through the common input observation every input
 * of this file rides — the tap's two phases are the journal's only entries, and no pointer fact of
 * any kind exists for it. What stays deferred is the gesture half (D-T2): the capability cell still
 * promises no recognizer, and the touch delivery contract itself is BCK-004's.
 */
private fun inputTouchDeferredScenario() = inputScenario("input-touch-deferred")

/**
 * The interaction seam of phase 4: a real click, whose press the port's own `pointerdown` listener
 * dispatches as a synchronous interaction, and whose handler asks the fullscreen primitive inside
 * that same frame of transient activation.
 *
 * What is recorded is exactly what the browser answered — committed when this Chromium honoured the
 * primitive, refused with the one honest code when it did not — never what the scenario hoped: a
 * headless refusal is an honest reading of this browser, and the real-screen proof of the primitive
 * belongs to the manual charter. The spec asserts the closed set of honest outcomes, so a wiring
 * that hangs, lies or publishes twice fails here.
 */
private fun inputInteractionScenario() = inputScenario("web-interaction") { handles ->
    launch { observeFullscreenInteraction(handles) }
}

/** Installs the fullscreen interaction handler once the surface exists, and records its outcomes. */
@OptIn(DelicateKadreApi::class)
private suspend fun observeFullscreenInteraction(handles: InputHandles) {
    val surface = handles.surface.await()
    val registration = when (
        val installed = surface.installInteractionHandler(
            InteractionHandler { context, _ ->
                context.request(InteractionAction.EnterFullscreen(FullscreenMode.Borderless))
            },
        )
    ) {
        is KadreResult.Success -> {
            // The armed flag is the spec's barrier: the handler is installed, and the next press of
            // the page is dispatched into it through the port's own listener.
            handles.host.setAttribute("data-kadre-interaction-armed", "true")
            installed.value
        }
        is KadreResult.Failure -> {
            handles.host.setAttribute(
                "data-kadre-interaction-fullscreen",
                "install-failure:${installed.reason.encoding()}",
            )
            return
        }
    }
    registration.outcomes.collect { outcome ->
        handles.host.setAttribute("data-kadre-interaction-fullscreen", outcome.encoding())
    }
}

/** The terminal outcome of one interaction request, as the specs read it. */
private fun InteractionActionOutcome.encoding(): String = when (this) {
    is InteractionActionOutcome.Committed -> "committed"
    is InteractionActionOutcome.Rejected -> "rejected:${failure.encoding()}"
    is InteractionActionOutcome.Expired -> "expired"
    is InteractionActionOutcome.OwnerClosed -> "owner-closed"
}

/**
 * The Phase 4 window-provider scenarios.
 *
 * The fixture is the host: it prepares the second browsing context — a same-origin `about:blank`
 * popup with a focusable host element inside it — **before** any Kadre call of its scenario, exactly
 * the way a host would (the roadmap exit gate: no test creates the context to make the capability
 * pass). No Kadre code creates a browsing context, an element or a popup, and the specs reach the
 * popup through `page.on("popup")`.
 *
 * `windowProviderScenario` attaches through the public provider overload with a `WebWindowProvider`
 * whose behaviour the spec selects per command, and the requester's command encodes the
 * `WindowRequestOutcome` the manager produced. Every session the application factory creates — the
 * requester and each child — records its own identity on the element it is attached to, read back
 * through the public element escape hatch: a child session in the popup is proven by its element
 * carrying the session hash the outcome named, never by a fixture journal.
 */

/** The second browsing context the fixture prepared before any Kadre call, plus the failing answers. */
private class PreparedProviderWindow(
    val popupElement: HTMLElement,
    val popupDetachedElement: HTMLElement,
    val sameDocumentElement: HTMLElement,
    val noContextElement: HTMLElement,
)

/** The provider behaviours the specs select, one per provider scenario of the contract. */
private enum class ProviderBehaviour {
    Prepared, SameDocument, Disconnected, NoContext, InvalidScope, Throwing
}

/**
 * Prepares the provider's second browsing context and the elements the failing provider modes answer
 * with. It must run before any Kadre call of its scenario.
 */
private fun prepareProviderWindow(): PreparedProviderWindow {
    val popupWindow = window.open("about:blank", "kadre-provider-window", "popup=true,width=480,height=320")
    val popupDocument = checkNotNull(popupWindow?.document) { "the host could not open the provider popup" }
    // The popup's elements live in the popup's own realm: an `as` cast would test instanceof against
    // this window's constructor, so the casts are unchecked, exactly as the platform's own bridge does.
    val popupElement = popupDocument.createElement("div").unsafeCast<HTMLElement>().also { element ->
        element.setAttribute("data-kadre-provider-host", "true")
        element.tabIndex = 0
        element.style.width = "240px"
        element.style.height = "120px"
        (popupDocument.body ?: popupDocument.documentElement!!.unsafeCast<HTMLElement>()).appendChild(element)
    }
    val popupDetachedElement = popupDocument.createElement("div").unsafeCast<HTMLElement>().also { element ->
        element.setAttribute("data-kadre-provider-detached", "true")
    }
    val sameDocumentElement = (document.createElement("div") as HTMLElement).also { element ->
        element.setAttribute("data-kadre-provider-same-document", "true")
        document.body!!.appendChild(element)
    }
    // An element of an inert implementation-created document: connected to its own node tree, whose
    // `defaultView` is null — the "no browsing context" reading the validation ladder refuses.
    val noContextElement = (document.implementation.createHTMLDocument("kadre-no-context").body as HTMLElement)
        .also { element -> element.setAttribute("data-kadre-provider-no-context", "true") }
    return PreparedProviderWindow(popupElement, popupDetachedElement, sameDocumentElement, noContextElement)
}

/** The answer each provider mode gives, one rung of the validation ladder per failing mode. */
private fun ProviderBehaviour.open(prepared: PreparedProviderWindow): KadreResult<WebWindowHost> = when (this) {
    ProviderBehaviour.Prepared -> KadreResult.Success(
        WebWindowHost(prepared.popupElement, CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    )

    ProviderBehaviour.SameDocument -> KadreResult.Success(
        WebWindowHost(prepared.sameDocumentElement, CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    )

    ProviderBehaviour.Disconnected -> KadreResult.Success(
        WebWindowHost(prepared.popupDetachedElement, CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    )

    ProviderBehaviour.NoContext -> KadreResult.Success(
        WebWindowHost(prepared.noContextElement, CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    )

    // A scope whose context carries no Job: the rung the ladder answers InvalidRequest("parentScope").
    ProviderBehaviour.InvalidScope -> KadreResult.Success(
        WebWindowHost(
            prepared.popupElement,
            object : CoroutineScope {
                override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            },
        ),
    )

    ProviderBehaviour.Throwing -> throw IllegalStateException("the provider exploded on purpose")
}

/** The provider scenario: one requester, one prepared popup, one switchable provider. */
private fun windowProviderScenario() {
    val prepared = prepareProviderWindow()
    val host = createHost("window-provider")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val requesterScope = CompletableDeferred<KadreScope>()
    var behaviour = ProviderBehaviour.Prepared
    listOf(
        "kadre-provider-same-document" to ProviderBehaviour.SameDocument,
        "kadre-provider-disconnected" to ProviderBehaviour.Disconnected,
        "kadre-provider-no-context" to ProviderBehaviour.NoContext,
        "kadre-provider-invalid-scope" to ProviderBehaviour.InvalidScope,
        "kadre-provider-throwing" to ProviderBehaviour.Throwing,
    ).forEach { (command, selected) ->
        parentScope.installCommand(command) {
            behaviour = selected
            host.setAttribute("data-kadre-provider-mode", command.removePrefix("kadre-provider-"))
        }
    }
    parentScope.installCommand("kadre-request-window") {
        val scope = requesterScope.await()
        host.setAttribute("data-kadre-window-caps", scope.windows.state.value.capabilities.requestWindow.encoded())
        host.setAttribute("data-kadre-window-outcome", scope.windows.requestWindow(WindowSpec()).requestEncoding())
    }
    val attached = host.attachKadre(
        parentScope,
        applicationFactory = sessionIdentityApplicationFactory(requesterScope, host, prepared.popupElement),
        windowProvider = WebWindowProvider { _, _ -> behaviour.open(prepared) },
    )
    host.setAttribute("data-kadre-attach", describeAttach(attached))
}

/** The late shadow-root reinsertion: a delivered detach stays terminal, whatever comes back later. */
private fun shadowLateReinsertScenario() {
    val host = createHost("shadow-late-reinsert")
    attachAndObserve(host, "shadow-late-reinsert")
    document.addEventListener("kadre-reinsert-shadow-late", {
        val container = document.querySelector("[data-kadre-shadow-container='late-reinsert']")
        if (!host.isConnected) container?.shadowRoot?.appendChild(host)
    })
}

/**
 * The `web-host-*` facade scenarios: the fixture publishes the factory keys the specs hand to
 * `KadreWeb.attach` and prepares the second browsing context before any Kadre call; the published
 * `@kadre/host` shim and the specs drive everything else.
 */
private fun hostFacadeScenario() {
    createHost("host-facade")
    publishApplicationFactoryKey(applicationFactory())
}

private fun hostProviderScenario() {
    val prepared = prepareProviderWindow()
    val host = createHost("host-provider")
    facadeRequesterElement = host
    facadeOfferedElement = prepared.popupElement
    publishWindowRequestFactoryKey(windowRequestApplicationFactory())
    publishProviderHost(prepared.popupElement)
}

/** The elements the facade scenario's factory records session identities on, set before the key is published. */
private var facadeRequesterElement: HTMLElement? = null
private var facadeOfferedElement: HTMLElement? = null

/**
 * The application factory behind the `web-host-provider` scenario.
 *
 * The session the facade created answers the fixture's own request command through the window manager
 * the facade's `windowProvider` option armed; only the requester installs that command. Every session
 * the factory creates records its launch identity on the element its host prepared — the requester's
 * own host for the initial attachment, the offered element for the child a provider opened.
 */
@JsExport
public fun windowRequestApplicationFactory(): String {
    publishHostBindings()
    val requesterElement = facadeRequesterElement
    val offeredElement = facadeOfferedElement
    val reference = KadreApplicationFactory { context ->
        KadreApplication {
            if (context.reason == KadreLaunchReason.InitialHostAttachment) {
                if (requesterElement != null) {
                    requesterElement.setAttribute("data-kadre-session-hash", context.sessionId.hashCode().toString())
                    requesterElement.setAttribute("data-kadre-session-reason", "initialHostAttachment")
                }
                document.addEventListener("kadre-facade-request-window", {
                    launch {
                        document.body!!.setAttribute(
                            "data-kadre-facade-window",
                            windows.requestWindow(WindowSpec()).requestEncoding(),
                        )
                    }
                })
            } else if (offeredElement != null) {
                offeredElement.setAttribute("data-kadre-session-hash", context.sessionId.hashCode().toString())
                offeredElement.setAttribute("data-kadre-session-reason", context.reason.name.replaceFirstChar(Char::lowercase))
                launch {
                    lifecycle.state.collect { state ->
                        offeredElement.setAttribute("data-kadre-session-lifecycle", state.encoded())
                    }
                }
            }
            awaitCancellation()
        }
    }.asHostRef()
    return reference.hostKey
}

/**
 * The application factory of the provider scenarios: every session it creates records its launch
 * identity on the element its host prepared — the requester's own host, or the offered element — and
 * the requester parks its scope for the fixture command that asks the window manager for a window.
 */
private fun sessionIdentityApplicationFactory(
    requesterScope: CompletableDeferred<KadreScope>?,
    requesterElement: HTMLElement,
    offeredElement: HTMLElement?,
): KadreApplicationFactory = KadreApplicationFactory { context ->
    KadreApplication {
        if (context.reason == KadreLaunchReason.InitialHostAttachment) {
            requesterScope?.complete(this)
            requesterElement.setAttribute("data-kadre-session-hash", context.sessionId.hashCode().toString())
            requesterElement.setAttribute("data-kadre-session-reason", "initialHostAttachment")
        } else if (offeredElement != null) {
            offeredElement.setAttribute("data-kadre-session-hash", context.sessionId.hashCode().toString())
            offeredElement.setAttribute("data-kadre-session-reason", context.reason.name.replaceFirstChar(Char::lowercase))
            launch {
                lifecycle.state.collect { state ->
                    offeredElement.setAttribute("data-kadre-session-lifecycle", state.encoded())
                }
            }
        }
        awaitCancellation()
    }
}

/** Hands the facade scenario's window-request application factory key to the page. */
private fun publishWindowRequestFactoryKey(key: String): Unit = js("globalThis.kadreWindowRequestFactory = key")

/** Hands the prepared provider host element to the page, for the facade's own provider object. */
private fun publishProviderHost(element: HTMLElement): Unit = js("globalThis.kadreProviderHost = element")

/** The window capability of the manager, as the specs read it. */
private fun Capability<Set<WindowCreationMode>>.encoded(): String = when (this) {
    is Capability.Unsupported -> "unsupported:${failure.operation.name.lowercase()}"
    is Capability.Supported -> "supported[${constraints.map { it.name }.sorted().joinToString("+")}]"
}

/** One window request, as the specs read it: the terminal outcome the manager already published. */
private suspend fun KadreResult<WindowRequest>.requestEncoding(): String = when (this) {
    is KadreResult.Success -> when (val outcome = value.await()) {
        is WindowRequestOutcome.OpenedHere -> "opened-here"
        is WindowRequestOutcome.OpenedInNewSession -> "opened-in-new-session:${outcome.sessionId.hashCode()}"
        is WindowRequestOutcome.Rejected -> "rejected:${outcome.failure.encoding()}"
        WindowRequestOutcome.Cancelled -> "cancelled"
        WindowRequestOutcome.RequesterDetached -> "requester-detached"
    }

    is KadreResult.Failure -> "request-failure:${reason.encoding()}"
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
    is KadreFailure.PlatformFailure -> "platformFailure:${platform.name.lowercase()}:${domain}:${code}"
    is KadreFailure.ResourceLimitExceeded -> "resourceLimitExceeded:${resource.name.lowercase()}:${limit}"
    is KadreFailure.StaleRevision -> "staleRevision:${expected}:${received}"
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
 * The Phase 5 scenarios: touch delivery, drag-and-drop and text input, on the `inputScenario`
 * pattern — the surface's own streams are the observation, every command listener exists before
 * the readiness flag, and every attribute a spec reads is a public value of the model, never a
 * fixture journal.
 */

/**
 * The touch scenarios: the shared input observation, with the touch contacts of the events journal
 * encoded by the shared encoder below.
 */
private fun touchScenario(name: String) = inputScenario(name)

/** The touch trigger scenario: a real touch press, dispatched as an interaction before the stimulus. */
private fun touchInteractionScenario() = inputScenario("touch-interaction") { handles ->
    launch { observeTouchInteraction(handles) }
}

/** The touch focus scenario: a held real contact, and the loss of activation that clears it. */
private fun touchFocusScenario() {
    createFocusOutside()
    touchScenario("touch-focus")
}

/**
 * Installs the touch trigger handler once the surface exists, and records what it dispatched.
 *
 * The record is the one fact the interaction model adds to the ordinary touch path — a
 * `TouchStarted` trigger reached the handler — plus the moment it was dispatched in: the published
 * input state at that moment, which is how the ordering claim (interaction first, ordinary stimulus
 * second) is observable rather than asserted. A surface whose handler refused to install records
 * that refusal where the armed flag would have been, so a capability break reads as one.
 */
@OptIn(DelicateKadreApi::class)
private suspend fun observeTouchInteraction(handles: InputHandles) {
    val surface = handles.surface.await()
    val registration = when (
        val installed = surface.installInteractionHandler(
            InteractionHandler { _, event ->
                if (event is InteractionEvent.TouchStarted) {
                    handles.host.setAttribute(
                        "data-kadre-touch-interaction",
                        "started@${event.position.encoded()}" +
                            ":touchesAtDispatch=${surface.input.state.value.touches.size}",
                    )
                }
            },
        )
    ) {
        is KadreResult.Success -> {
            handles.host.setAttribute("data-kadre-interaction-armed", "true")
            installed.value
        }

        is KadreResult.Failure -> {
            handles.host.setAttribute(
                "data-kadre-touch-interaction",
                "install-failure:${installed.reason.encoding()}",
            )
            return
        }
    }
    registration.outcomes.collect { outcome ->
        handles.host.setAttribute("data-kadre-touch-interaction", outcome.encoding())
    }
}

/**
 * The drop scenarios: the fixture is the host of the drag.
 *
 * Before any Kadre call it builds what the element will be handed — a drag source of its own and
 * the `DataTransfer` of the drag, carrying one file item and one text item of the host's data — and
 * installs the four drag-step commands the specs drive. No test builds the drag: the steps are the
 * host's own (the roadmap exit gate, exactly as the phase-4 popup is), and what the specs command
 * is which step the host performs, not what the drag carries.
 *
 * The accepting variant installs the interaction handler a real application would: every
 * `DropEntered` the seam dispatches in-frame is answered with the `AcceptDrop` of that offer. The
 * rejecting variant installs nothing, which is the "handler absent" arm of the seam: the offers it
 * presents die rejected and the browser keeps the default of the drag it was having.
 */
private fun dropScenario(accepting: Boolean) {
    val prepared = prepareDropDrag()
    val host = createHost("drop")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val handles = InputHandles(host)
    val registry = DropOfferRegistry()
    parentScope.launch { InputObservation(host).install(parentScope, handles.surface.await()) }
    val drop = DropObservation(host, parentScope, registry)
    parentScope.launch { drop.install(handles.surface.await()) }
    parentScope.installDropCommands(handles, prepared, drop)
    if (accepting) parentScope.launch { installDropHandler(handles, registry) }
    val attached = host.attachKadre(parentScope) {
        handles.surface.complete(checkNotNull(primarySurface.value))
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
    if (attached is KadreResult.Success) {
        handles.session.complete(attached.value)
        observeSession(attached.value, "drop", parentScope)
    }
}

/** The drag the host prepared before any Kadre call of its drop scenario. */
private class PreparedDropDrag(val store: DataTransfer)

/**
 * Prepares the drag the host will perform: its own source element and the drag data store whose
 * items are the host's own data — a 16-byte file and a string of plain text. It must run before any
 * Kadre call of its scenario.
 */
private fun prepareDropDrag(): PreparedDropDrag {
    (document.createElement("div") as HTMLElement).also { source ->
        source.setAttribute("data-kadre-drop-source", "kadre-drop.txt")
        document.body!!.appendChild(source)
    }
    return PreparedDropDrag(jsPreparedDragStore())
}

/** The drag data store the host builds for the drag it performs: one file item, one text item. */
private fun jsPreparedDragStore(): DataTransfer = js(
    """(function () {
        const store = new DataTransfer();
        store.items.add(new File(["kadre-drop-bytes"], "kadre-drop.txt", { type: "text/plain" }));
        store.items.add("kadre-drop-text", "text/plain");
        return store;
    })()""",
)

/** One step of the host's drag: a real `DragEvent` of the host's own store, at the position named. */
private fun jsDragEvent(type: String, store: DataTransfer, x: Double, y: Double): DragEvent = js(
    "new DragEvent(type, { dataTransfer: store, clientX: x, clientY: y, bubbles: true, cancelable: true, composed: true })",
)

/** One drag step the fixture performs: the command that asks for it, the event and its position. */
private class DragStep(val command: String, val type: String, val name: String, val x: Double, val y: Double)

/** The drag steps of the host, in the order the specs drive them, with their surface positions. */
private val dragSteps = listOf(
    DragStep(command = "kadre-drag-enter", type = "dragenter", name = "enter", x = 60.0, y = 40.0),
    DragStep(command = "kadre-drag-over", type = "dragover", name = "over", x = 70.0, y = 50.0),
    DragStep(command = "kadre-drag-leave", type = "dragleave", name = "leave", x = 70.0, y = 50.0),
    DragStep(command = "kadre-drag-drop", type = "drop", name = "drop", x = 80.0, y = 60.0),
)

/**
 * The drop commands: the four drag steps the host performs, then the claim, read and close of the
 * transfer a performed drop leaves claimable, and the session stop of the teardown scenario.
 *
 * Every step records the browser's own answer about its default (`event.defaultPrevented`, read on
 * the very event the host dispatched), which is the D-D3 claim made observable: a default is
 * prevented for an offer the surface holds and for nothing else.
 */
private fun CoroutineScope.installDropCommands(
    handles: InputHandles,
    prepared: PreparedDropDrag,
    drop: DropObservation,
) {
    val host = handles.host
    val defaults = mutableListOf<String>()
    dragSteps.forEach { step ->
        installCommand(step.command) {
            val box = host.getBoundingClientRect()
            val event = jsDragEvent(step.type, prepared.store, box.left + step.x, box.top + step.y)
            host.dispatchEvent(event)
            defaults += "${step.name}=${if (event.defaultPrevented) "prevented" else "kept"}"
            host.setAttribute("data-kadre-drop-default", defaults.joinToString(";"))
        }
    }
    var transfer: DropTransfer? = null
    val reads = mutableListOf<String>()
    installCommand("kadre-drop-claim") {
        when (val claimed = drop.awaitClaimable().claimTransfer()) {
            is KadreResult.Success -> {
                transfer = claimed.value
                host.setAttribute("data-kadre-drop-claim", "claimed")
                host.setAttribute(
                    "data-kadre-drop-transfer",
                    claimed.value.items.joinToString(";") { it.encoded() },
                )
            }

            is KadreResult.Failure -> host.setAttribute(
                "data-kadre-drop-claim",
                "failure:${claimed.reason.encoding()}",
            )
        }
    }
    installCommand("kadre-drop-claim-second") {
        val second = drop.awaitClaimable().claimTransfer()
        host.setAttribute(
            "data-kadre-drop-claim-second",
            when (second) {
                is KadreResult.Success -> "unexpected-success"
                is KadreResult.Failure -> "failure:${second.reason.encoding()}"
            },
        )
    }
    installCommand("kadre-drop-read") {
        val current = transfer
        reads += when (current) {
            null -> "no-transfer"
            else -> current.readItems()
        }
        host.setAttribute("data-kadre-drop-read", reads.joinToString(";"))
    }
    installCommand("kadre-drop-read-bounded") {
        // The bounded refusal: a read whose budget is below the item's known size is refused before
        // any byte moves, with the failure that names the item and the budget it was handed.
        val current = transfer
        val encoding = when (current) {
            null -> "no-transfer"
            else -> when (val read = current.items[0].collectBytes(maxBytes = 4) { }) {
                is KadreResult.Success -> "unexpected-success"
                is KadreResult.Failure -> "failure:${read.reason.encoding()}"
            }
        }
        host.setAttribute("data-kadre-drop-read-bounded", encoding)
    }
    installCommand("kadre-drop-close-transfer") {
        transfer?.close()
        host.setAttribute("data-kadre-drop-transfer-closed", "closed")
    }
    installCommand("kadre-stop-drop") {
        handles.session.await().requestStop()
    }
}

/** Reads every item of one claimed transfer, bounded to the session's own chunk size. */
private suspend fun DropTransfer.readItems(): String {
    val reads = mutableListOf<String>()
    items.forEachIndexed { index, item ->
        val chunks = mutableListOf<String>()
        val read = item.collectBytes(maxBytes = 64) { chunk -> chunks += chunk.decodeToString() }
        reads += when (read) {
            is KadreResult.Success -> "$index=${chunks.joinToString("|")}"
            is KadreResult.Failure -> "$index=failure:${read.reason.encoding()}"
        }
    }
    return reads.joinToString(",")
}

/**
 * Installs the drop handler once the surface exists, and records the outcomes it produced.
 *
 * The handler is the application's own decision point: every `DropEntered` the seam dispatches
 * inside the drag's DOM frame is answered with the `AcceptDrop` of that very offer — the synchronous
 * commitment D-D2 describes — and the outcome the engine published for it is recorded with the offer
 * it committed, which is what ties the interaction lane's answer to the offer the input stream
 * carries.
 */
@OptIn(DelicateKadreApi::class)
private suspend fun installDropHandler(handles: InputHandles, registry: DropOfferRegistry) {
    val surface = handles.surface.await()
    val registration = when (
        val installed = surface.installInteractionHandler(
            InteractionHandler { context, event ->
                if (event is InteractionEvent.DropEntered) {
                    context.request(InteractionAction.AcceptDrop(event.offer.id))
                }
            },
        )
    ) {
        is KadreResult.Success -> {
            handles.host.setAttribute("data-kadre-drop-armed", "true")
            installed.value
        }

        is KadreResult.Failure -> {
            handles.host.setAttribute(
                "data-kadre-drop-armed",
                "install-failure:${installed.reason.encoding()}",
            )
            return
        }
    }
    registration.outcomes.collect { outcome ->
        handles.host.setAttribute("data-kadre-drop-interaction", outcome.dropEncoding(registry))
    }
}

/** The offer identities of one drop scenario: opaque ids mapped to the ordinals the specs read. */
private class DropOfferRegistry {
    private val ordinals = HashMap<DropOfferId, Int>()

    /** The ordinal of [id], minted at its first observation and never reused. */
    fun ordinalOf(id: DropOfferId): Int = ordinals.getOrPut(id) { ordinals.size }

    /** The ordinal of an offer the interaction outcomes name back, if this scenario saw it. */
    fun ordinalOrNull(id: DropOfferId): Int? = ordinals[id]
}

/**
 * The drop observation of one scenario: the offers the input stream presented, each with the state
 * the runtime published for it, and the offer a performed drop left claimable.
 *
 * The offer objects arrive on the stream's own `DropEntered`/`Dropped` events — the public model,
 * not a fixture channel — and their state flows are the runtime's own publication of the offer
 * lifecycle, collected into one attribute per offer, named by the ordinal its id was minted.
 */
private class DropObservation(
    private val host: HTMLElement,
    private val scope: CoroutineScope,
    private val registry: DropOfferRegistry,
) {
    private val claimable = CompletableDeferred<DropOffer>()

    /** The offer a performed drop left claimable, resolved when its `Dropped` event was observed. */
    suspend fun awaitClaimable(): DropOffer = claimable.await()

    fun install(surface: HostSurface) {
        scope.launch {
            surface.input.events.collect { event ->
                when (event) {
                    is InputEvent.DropEntered -> observe(event.offer)
                    is InputEvent.Dropped -> {
                        observe(event.offer)
                        claimable.complete(event.offer)
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun observe(offer: DropOffer) {
        val ordinal = registry.ordinalOf(offer.id)
        scope.launch {
            offer.state.collect { state ->
                host.setAttribute("data-kadre-drop-offer-$ordinal", state.encoded())
            }
        }
    }
}

/** One offer state, as the specs read it. */
private fun DropOfferState.encoded(): String = when (this) {
    DropOfferState.Presented -> "presented"
    DropOfferState.Accepted -> "accepted"
    DropOfferState.TransferAvailable -> "transfer-available"
    DropOfferState.Claimed -> "claimed"
    is DropOfferState.Terminated -> "terminated:${reason.encoded()}"
}

/** One offer termination, as the specs read it. */
private fun DropOfferTerminationReason.encoded(): String = when (this) {
    DropOfferTerminationReason.Rejected -> "rejected"
    DropOfferTerminationReason.LeftSurface -> "left-surface"
    DropOfferTerminationReason.OfferExpired -> "offer-expired"
    DropOfferTerminationReason.ClaimTimedOut -> "claim-timed-out"
    DropOfferTerminationReason.OwnerClosed -> "owner-closed"
    is DropOfferTerminationReason.Failed -> "failed:${failure.encoding()}"
}

/** One item descriptor, as the specs read it: kind, canonical mimes, name and size, or their `none`. */
private fun DropItemDescriptor.encoded(): String =
    "${kind.name.lowercase()}:${mimeTypes.joinToString("+")}:${displayName ?: "none"}:" +
        "${sizeBytes?.toString() ?: "none"}"

/** One claimed item, as the specs read it: its descriptor with the read mode it honours. */
private fun DroppedItem.encoded(): String = "${descriptor.encoded()}:${readMode.name.lowercase()}"

/** The terminal outcome of one drop interaction request, with the offer it committed. */
private fun InteractionActionOutcome.dropEncoding(registry: DropOfferRegistry): String = when (this) {
    is InteractionActionOutcome.Committed -> when (val offer = dropOfferId) {
        null -> "committed"
        else -> "committed#${registry.ordinalOrNull(offer)}"
    }

    is InteractionActionOutcome.Rejected -> "rejected:${failure.encoding()}"
    is InteractionActionOutcome.Expired -> "expired"
    is InteractionActionOutcome.OwnerClosed -> "owner-closed"
}

/**
 * The element kinds the v1 text contract addresses: the fixture prepares one of these per scenario,
 * and the element is the attached host itself, because the element IS the surface's text document
 * on this target (D-X2, D-X3).
 */
private enum class WebTextKind(val tag: String, val element: String) {
    SingleLine("text-input", "input"),
    Multiline("text-area", "textarea"),
}

/** The document the fixture host writes on its editable element before any Kadre call. */
private const val KADRE_TEXT_DOCUMENT: String = "kadre"

/** The snapshot the fixture's write-back commands apply, one revision ahead of the accepted one. */
private const val KADRE_TEXT_WRITTEN: String = "kadre-written"

/**
 * The text-input scenarios: the host prepares the editable element — its document and selection —
 * before any Kadre call (the roadmap exit gate, as the phase-4 popup is), then opens sessions on
 * the fixture's own commands and encodes what the sessions published as `data-kadre-text-*`
 * attributes of the element: the open's answer, the session state, the observation journal, and the
 * write-backs the fixture asks for.
 */
private fun textInputScenario(singleLine: Boolean) {
    val kind = if (singleLine) WebTextKind.SingleLine else WebTextKind.Multiline
    val host = prepareEditableHost(kind)
    createFocusOutside()
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val handles = TextHandles(host, kind)
    parentScope.launch { InputObservation(host).install(parentScope, handles.surface.await()) }
    parentScope.installCommand("kadre-text-open") {
        val surface = handles.surface.await()
        host.setAttribute("data-kadre-text-events", "")
        when (val opened = surface.input.openTextInput(handles.config())) {
            is KadreResult.Success -> {
                handles.session = CompletableDeferred<TextInputSession>().also { it.complete(opened.value) }
                host.setAttribute("data-kadre-text-open", "success")
                observeTextInput(host, parentScope, opened.value)
            }

            is KadreResult.Failure -> host.setAttribute(
                "data-kadre-text-open",
                "failure:${opened.reason.encoding()}",
            )
        }
    }
    parentScope.installCommand("kadre-text-open-second") {
        val surface = handles.surface.await()
        val second = surface.input.openTextInput(handles.config())
        host.setAttribute(
            "data-kadre-text-open-second",
            when (second) {
                is KadreResult.Success -> "unexpected-success"
                is KadreResult.Failure -> "failure:${second.reason.encoding()}"
            },
        )
    }
    parentScope.installCommand("kadre-text-close") {
        handles.session.await().close()
    }
    parentScope.installCommand("kadre-text-writeback-current") {
        val session = handles.session.await()
        val revision = TextDocumentRevision(currentRevision(session) + 1L)
        val result = session.updateSurroundingText(
            KADRE_TEXT_WRITTEN,
            TextRange(KADRE_TEXT_WRITTEN.length, KADRE_TEXT_WRITTEN.length),
            revision,
        )
        host.setAttribute("data-kadre-text-writeback-current", result.encoded())
    }
    parentScope.installCommand("kadre-text-writeback-stale") {
        val session = handles.session.await()
        val revision = TextDocumentRevision((currentRevision(session) - 1L).coerceAtLeast(0L))
        val result = session.updateSurroundingText(
            KADRE_TEXT_WRITTEN,
            TextRange(KADRE_TEXT_WRITTEN.length, KADRE_TEXT_WRITTEN.length),
            revision,
        )
        host.setAttribute("data-kadre-text-writeback-stale", result.encoded())
    }
    val attached = host.attachKadre(parentScope) {
        handles.surface.complete(checkNotNull(primarySurface.value))
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
    if (attached is KadreResult.Success) observeSession(attached.value, kind.tag, parentScope)
}

/**
 * Prepares the editable element of one text scenario: the element the session will observe and the
 * write-back will write is the attached host itself, and the document it shows with the selection
 * it starts from are the host's to prepare before any Kadre call.
 */
private fun prepareEditableHost(kind: WebTextKind): HTMLElement =
    (document.createElement(kind.element) as HTMLElement).also { element ->
        element.setAttribute("data-kadre-host", kind.tag)
        element.style.width = "320px"
        element.style.height = if (kind == WebTextKind.Multiline) "120px" else "32px"
        jsSetEditableValue(element, KADRE_TEXT_DOCUMENT)
        document.body!!.appendChild(element)
        jsSetEditableSelection(element, 0, 0)
    }

/** The handles of one text scenario: the surface the application block publishes and its session. */
private class TextHandles(val host: HTMLElement, val kind: WebTextKind) {
    val surface: CompletableDeferred<HostSurface> = CompletableDeferred()
    var session: CompletableDeferred<TextInputSession> = CompletableDeferred()

    /** The config of one open, read from the document the element shows the moment it is asked. */
    fun config(): TextInputConfig {
        val text = jsEditableValue(host)
        return TextInputConfig(
            purpose = TextInputPurpose.Text,
            action = TextInputAction.Send,
            multiline = kind == WebTextKind.Multiline,
            surroundingText = text,
            selection = TextRange(jsEditableSelectionStart(host), jsEditableSelectionEnd(host)),
            documentRevision = TextDocumentRevision(0),
        )
    }
}

/** The accepted revision of one session, as the session's own state publishes it. */
private fun currentRevision(session: TextInputSession): Long = when (val state = session.state.value) {
    is TextInputState.Active -> state.documentRevision.value
    is TextInputState.Suspended -> state.documentRevision.value
    TextInputState.Closed -> 0L
}

/** Publishes one session's state and observation journal as attributes of the element. */
private fun observeTextInput(host: HTMLElement, scope: CoroutineScope, session: TextInputSession) {
    val events = mutableListOf<String>()
    scope.launch {
        session.state.collect { state -> host.setAttribute("data-kadre-text-state", state.encoded()) }
    }
    scope.launch {
        session.events.collect { event ->
            events += event.encoded()
            host.setAttribute("data-kadre-text-events", events.joinToString(";"))
        }
    }
}

/** One session state, as the specs read it: the revision it carries and the composition it holds. */
private fun TextInputState.encoded(): String = when (this) {
    is TextInputState.Active -> "active:rev=${documentRevision.value}:composing=${composingRange.encoded()}"
    is TextInputState.Suspended -> "suspended:rev=${documentRevision.value}:composing=${composingRange.encoded()}"
    TextInputState.Closed -> "closed"
}

/** One text range, as the specs read it: `(start,end)` in UTF-16 code units, or `none`. */
private fun TextRange?.encoded(): String = when (this) {
    null -> "none"
    else -> "($startUtf16,$endExclusiveUtf16)"
}

/** One text observation, as the specs read it: the edit it names at the revision it was stamped. */
private fun TextInputEvent.encoded(): String = when (this) {
    is TextInputEvent.Replace -> "replace:(${range.startUtf16},${range.endExclusiveUtf16})" +
        "=${text.quotedPayload()}:rev=${baseRevision.value}"

    is TextInputEvent.SelectionChanged -> "selection:(${selection.startUtf16},${selection.endExclusiveUtf16})" +
        ":rev=${baseRevision.value}"

    is TextInputEvent.CompositionChanged -> {
        val span = range
        when (span) {
            null -> "composition:end:rev=${baseRevision.value}"
            else -> "composition:(${span.startUtf16},${span.endExclusiveUtf16})=${text.quotedPayload()}" +
                ":sel=${selection.encoded()}:rev=${baseRevision.value}"
        }
    }

    is TextInputEvent.Action -> "action:${action.name.lowercase()}:rev=${baseRevision.value}"
}

/** One text payload, quoted and flattened for an attribute: the newline of a multiline edit is escaped. */
private fun String.quotedPayload(): String = "\"${replace("\n", "\\n")}\""

/** One write-back result, as the specs read it: applied, or the failure the runtime answered. */
private fun KadreResult<Unit>.encoded(): String = when (this) {
    is KadreResult.Success -> "applied"
    is KadreResult.Failure -> reason.encoding()
}

/** The value of the element the fixture itself created and knows the kind of. */
private fun jsEditableValue(element: HTMLElement): String = js("element.value")

/** The element's live selection start, the offset the shadow starts from. */
private fun jsEditableSelectionStart(element: HTMLElement): Int = js("element.selectionStart")

/** The element's live selection end, the offset the shadow starts from. */
private fun jsEditableSelectionEnd(element: HTMLElement): Int = js("element.selectionEnd")

/** Writes the document the element shows, the host's own preparation of its editable element. */
private fun jsSetEditableValue(element: HTMLElement, value: String): Unit = js("element.value = value")

/** Places the element's selection, the host's own preparation of its editable element. */
private fun jsSetEditableSelection(element: HTMLElement, start: Int, end: Int): Unit =
    js("element.setSelectionRange(start, end)")

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
 * The focus target a spec moves the focus to: a real, focusable element outside the host.
 *
 * It exists so a loss of activation is a real focus change of the page rather than a synthetic event.
 * It is created by the fixture before readiness, like every other element of a scenario, and the
 * moment the caller creates it decides the page's tab order: a scenario that proves `Tab`'s default
 * ran creates it after the host, so it is the stop the default moves the focus to.
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

/** The derived input capabilities: the feature availabilities and the capability constraint sets. */
private fun InputCapabilities.encoded(): String =
    "keyboard=${keyboard.encoded()}" +
        " pointer=${pointer.encoded()}" +
        " touch=${touch.encoded()}" +
        " gestures=${gestures.encoded()}" +
        " dragAndDrop=${dragAndDrop.encoded()}" +
        " textInput=${textInput.encoded()}" +
        " rawInput=${rawInput.encoded()}"

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

/** The unit capabilities, as the specs read them: supported, or the operation they refuse. */
private fun Capability<Unit>.encoded(): String = when (this) {
    is Capability.Unsupported -> "unsupported:${failure.operation.name.lowercase()}"
    is Capability.Supported -> "supported"
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

    is InputEvent.TouchChanged -> "touch:${phase.name.lowercase()}@${position.encoded()}" +
        ":rev=${stateRevision.value}"

    is InputEvent.DropEntered -> "drop:entered:items=[${offer.items.joinToString(",") { it.encoded() }}]" +
        ":@${position.encoded()}:rev=${stateRevision.value}"

    is InputEvent.DropMoved -> "drop:moved@${position.encoded()}:rev=${stateRevision.value}"

    is InputEvent.DropExited -> "drop:exited:rev=${stateRevision.value}"

    is InputEvent.Dropped -> "drop:performed:items=[${offer.items.joinToString(",") { it.encoded() }}]" +
        ":@${position.encoded()}:rev=${stateRevision.value}"

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

/**
 * The Phase 6 display scenario: the browsing context's own display inventory, observed through the
 * public `DisplayManager` the session publishes, on the `inputScenario` pattern — every command
 * listener exists before the readiness flag, and every attribute a spec reads is a public value of
 * the model, never a fixture journal.
 *
 * A session's display manager starts from the `Unavailable` snapshot every manager is constructed
 * with, because no browser event has told it anything yet; the scenario's application asks for the
 * inventory through the manager's own public admission — `requestAccess()` — and the port answers
 * with the exact `HostViewport` fallback the gate mandates. Every later browser-delivered fact (a
 * `resize`, a device pixel ratio change through the resolution query) republishes through the
 * observer the manager installed itself, and the specs drive those browser facts for real.
 */

/** The handles of the display scenario: the manager the application block publishes and its session. */
private class DisplayHandles(val host: HTMLElement) {
    val displays: CompletableDeferred<DisplayManager> = CompletableDeferred()
    val session: CompletableDeferred<KadreSession> = CompletableDeferred()
}

private fun displayScenario() {
    val host = createHost("display")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val handles = DisplayHandles(host)
    // The quiet sentinels of the teardown test are instrumented before any Kadre call, and their
    // counters are page facts, not a display journal: the page counts every animation frame it is
    // asked for — whoever asks — and the DOM node count is read on demand.
    host.setAttribute("data-kadre-display-raf", "0")
    host.setAttribute("data-kadre-display-dom-reads", "0")
    jsInstallAnimationFrameCounter(host)
    var domReads = 0
    parentScope.installCommand("kadre-display-dom-count") {
        domReads += 1
        host.setAttribute("data-kadre-display-dom-count", document.getElementsByTagName("*").length.toString())
        host.setAttribute("data-kadre-display-dom-reads", domReads.toString())
    }
    parentScope.installCommand("kadre-stop-display") { handles.session.await().requestStop() }
    // The observation belongs to the scenario's own scope rather than to the application block, so
    // the journal a spec reads across a close survives the session that produced it. The admission
    // is asked once the observation is registered, so the initial publication is caught like every
    // later one.
    parentScope.launch {
        val displays = handles.displays.await()
        DisplayObservation(host).install(parentScope, displays)
        host.setAttribute("data-kadre-display-request", displays.requestAccess().admission())
    }
    val attached = host.attachKadre(parentScope) {
        handles.displays.complete(checkNotNull(displays))
        awaitCancellation()
    }
    host.setAttribute("data-kadre-attach", describeAttach(attached))
    if (attached is KadreResult.Success) {
        handles.session.complete(attached.value)
        observeSession(attached.value, "display", parentScope)
    }
}

/**
 * Publishes one display scenario's observations as attributes of the host, from the manager's own
 * streams.
 *
 * Three facts are read, and each is a public value rather than a fixture journal:
 *
 * - `data-kadre-display-manager`: the whole `DisplayManagerState` — the manager revision, the
 *   inventory shape with the primary's membership index among the enumerated displays, and the
 *   enumeration capability;
 * - `data-kadre-display-display`: the primary display's `DisplayState`, with the current mode's
 *   membership index inside `modes` named explicitly;
 * - `data-kadre-display-events`: every `DisplayEvent` the manager published, in order, each naming
 *   the manager revision it was stamped with — so a spec can read both the payload and its order.
 */
private class DisplayObservation(private val host: HTMLElement) {
    private val events: MutableList<String> = mutableListOf()

    fun install(scope: CoroutineScope, displayManager: DisplayManager) {
        host.setAttribute("data-kadre-display-events", "")
        scope.launch {
            displayManager.state.collect { state ->
                host.setAttribute("data-kadre-display-manager", state.managerEncoding())
                host.setAttribute("data-kadre-display-display", state.primaryDisplayEncoding())
            }
        }
        // The event subscription is registered undispatched, so it exists by the time this call
        // returns: an observation made before its collector registered would be delivered to nobody.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            displayManager.events.collect { event ->
                events += event.encoded()
                host.setAttribute("data-kadre-display-events", events.joinToString(";"))
            }
        }
    }
}

/** The whole display-manager state, as the specs read it. */
private fun DisplayManagerState.managerEncoding(): String {
    val shape = when (val inventory = this.inventory) {
        is DisplayInventory.Enumerated -> {
            val primary = inventory.displays.indexOfFirst { it === inventory.primary }
            "enumerated:primary=${if (primary >= 0) primary.toString() else "none"}" +
                ":displays=${inventory.displays.size}"
        }

        DisplayInventory.PermissionRequired -> "permission-required:primary=none:displays=0"
        is DisplayInventory.PermissionDenied -> "permission-denied:primary=none:displays=0"
        is DisplayInventory.Unavailable -> "unavailable:${inventory.failure.encoding()}"
    }
    return "rev=${revision.value}:$shape:enumeration=${capabilities.enumeration.encoded()}"
}

/** The primary display's state, as the specs read it, or `none` while nothing is enumerated. */
private fun DisplayManagerState.primaryDisplayEncoding(): String = when (val inventory = inventory) {
    is DisplayInventory.Enumerated -> inventory.primary?.state?.value?.displayEncoding() ?: "none"
    else -> "none"
}

/** One display state, as the specs read it. */
private fun DisplayState.displayEncoding(): String =
    "type=${type.name}:connection=${connection.name.lowercase()}:name=${name ?: "none"}" +
        ":bounds=${bounds.rectEncoding()}:workArea=${workArea?.rectEncoding() ?: "none"}" +
        ":scale=${number(scaleFactor)}:modes=${modes.size}" +
        ":current=${currentMode?.let(modes::indexOf)?.toString() ?: "none"}" +
        ":mode=${currentMode.modeEncoding()}:rev=${revision.value}"

/** One display mode, as the specs read it: physical size, refresh rate and bit depth, or `none`. */
private fun DisplayMode?.modeEncoding(): String = when (this) {
    null -> "none"
    else -> "${physicalSize.width}x${physicalSize.height}:" +
        "${refreshRateHz?.let(::number) ?: "none"}:${bitDepth?.toString() ?: "none"}"
}

/** One physical rect, as the specs read it: origin and size, in physical pixels. */
private fun PhysicalRect.rectEncoding(): String = "${origin.x},${origin.y},${size.width},${size.height}"

/** One published display event, with the manager revision it was stamped with. */
private fun DisplayEvent.encoded(): String = when (this) {
    is DisplayEvent.Added -> "added@${managerRevision.value}"
    is DisplayEvent.Changed -> "changed@${managerRevision.value}"
    is DisplayEvent.Removed -> "removed@${managerRevision.value}"
}

/**
 * The page's own animation-frame counter: from this call on, every `requestAnimationFrame`
 * registration the page makes — whoever asks for it — is counted on [element]. The teardown
 * sentinel reads it as a page fact: a display path that polls keeps registering frames, and after
 * the session is gone no page machinery is alive to excuse a single one.
 */
private fun jsInstallAnimationFrameCounter(element: HTMLElement): Unit = js(
    """(function () {
        let count = 0;
        const registered = window.requestAnimationFrame.bind(window);
        window.requestAnimationFrame = function (callback) {
            count += 1;
            element.setAttribute("data-kadre-display-raf", String(count));
            return registered(callback);
        };
    })()""",
)
