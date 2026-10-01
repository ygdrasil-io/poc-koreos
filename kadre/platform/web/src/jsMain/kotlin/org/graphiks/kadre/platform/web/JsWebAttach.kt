package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.KadreLaunchInfo
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement
import org.w3c.dom.Window
import kotlin.js.unsafeCast

public data class WebWindowHost(
    public val element: HTMLElement,
    public val parentScope: CoroutineScope,
    public val attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
)

public fun interface WebWindowProvider {
    public fun open(requestId: WindowRequestId, spec: WindowSpec): KadreResult<WebWindowHost>
}

public fun HTMLElement.attachKadre(
    parentScope: CoroutineScope,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    windowProvider: WebWindowProvider? = null,
): KadreResult<KadreSession> {
    // The origin browsing context is the requester's own: an offered element living in this very
    // window (or in no window at all) is exactly the context confusion the contract refuses.
    val originWindow = ownerDocument?.defaultView
    return WebHostSession(JsWebDomPort(this)).attach(
        parentScope,
        applicationFactory,
        policy,
        attachmentPolicy,
        windowProvider = windowProvider?.let { provider -> jsProviderBridge(provider) },
        childSessionFactory = WebChildSessionFactory { offer, requestId ->
            jsChildSessionFactory(offer, requestId, applicationFactory, policy)
        },
        windowHostProbe = WebWindowHostProbe { offer -> jsWindowHostProbe(offer, originWindow) },
    )
}

public fun HTMLElement.attachKadre(
    parentScope: CoroutineScope,
    policy: KadrePolicy = KadrePolicies.Default,
    attachmentPolicy: WebAttachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    application: KadreApplication,
): KadreResult<KadreSession> = attachKadre(
    parentScope = parentScope,
    applicationFactory = KadreApplicationFactory { application },
    policy = policy,
    attachmentPolicy = attachmentPolicy,
)

/** Maps the public provider's DOM-typed answer onto the opaque offer the manager consumes. */
private fun jsProviderBridge(provider: WebWindowProvider): WebHostWindowProvider =
    WebHostWindowProvider { requestId, spec ->
        when (val result = provider.open(requestId, spec)) {
            is KadreResult.Success -> KadreResult.Success(
                WebHostWindowOffer(result.value.element, result.value.parentScope, result.value.attachmentPolicy),
            )

            is KadreResult.Failure -> KadreResult.Failure(result.reason)
        }
    }

/**
 * Unwraps the element the target's own bridge placed in the offer.
 *
 * The cast is deliberately `unsafeCast` and not `as`: an offered element lives in the *offered*
 * browsing context — that distinctness is the contract's whole point — and an `as` cast would test
 * `instanceof` against this window's `HTMLElement` constructor, failing every cross-realm element
 * the provider is entitled to hand over. The invariant "this `Any` holds the `HTMLElement` the
 * bridge put there" is enforced by construction, at the only boundary that can see the type.
 */
private fun WebHostWindowOffer.unwrapElement(): HTMLElement = element.unsafeCast<HTMLElement>()

/**
 * Opens the child session through the ordinary attach path (plan decision D6): the host's own
 * application factory and policy, the offer's scope and attachment policy, the launch identity of
 * the request that caused it, and the shared ownership registry — there is no second registration
 * mechanism, and an element a live session owns fails the child attach with `Busy(Host)`.
 */
private fun jsChildSessionFactory(
    offer: WebHostWindowOffer,
    requestId: WindowRequestId,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy,
): KadreResult<KadreSession> = WebHostSession(JsWebDomPort(offer.unwrapElement())).attach(
    offer.parentScope,
    applicationFactory,
    policy,
    offer.attachmentPolicy,
    launch = KadreLaunchInfo(KadreLaunchReason.AdditionalHostRequested, requestId),
)

/**
 * Reads the DOM facts the validation ladder decides on: connectedness, a browsing context that is
 * real and distinct from the requester's, and the offered scope's own job state.
 */
private fun jsWindowHostProbe(offer: WebHostWindowOffer, originWindow: Window?): WebWindowHostChecks {
    val element = offer.unwrapElement()
    val defaultView = element.ownerDocument?.defaultView
    val job = offer.parentScope.coroutineContext[Job]
    return WebWindowHostChecks(
        elementConnected = element.isConnected,
        distinctDefaultView = defaultView != null && defaultView !== originWindow,
        scopeHasJob = job != null,
        scopeActive = job?.isActive == true,
    )
}
