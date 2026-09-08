package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement
import kotlin.math.max

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
    if (!isConnected) return KadreResult.Failure(KadreFailure.InvalidRequest("element"))
    return WebHostSession(WasmWebHostPort(this)).attach(parentScope, applicationFactory, policy)
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

private class WasmWebHostPort(element: HTMLElement) : WebHostPort {
    private var element: HTMLElement? = element

    override val initialSnapshot: WebSurfaceSnapshot = element.snapshot()

    override fun release() {
        element = null
    }
}

private fun HTMLElement.snapshot(): WebSurfaceSnapshot {
    val logicalWidth = max(clientWidth.toDouble(), 1.0)
    val logicalHeight = max(clientHeight.toDouble(), 1.0)
    return WebSurfaceSnapshot(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        physicalWidth = logicalWidth.toInt(),
        physicalHeight = logicalHeight.toInt(),
        scaleFactor = 1.0,
    )
}
