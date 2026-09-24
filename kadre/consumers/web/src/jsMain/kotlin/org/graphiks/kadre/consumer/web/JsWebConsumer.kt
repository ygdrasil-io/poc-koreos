package org.graphiks.kadre.consumer.web

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.KadreApplicationFactoryRef
import org.graphiks.kadre.platform.web.WebAttachmentPolicy
import org.graphiks.kadre.platform.web.attachKadre
import org.graphiks.kadre.platform.web.withWebElement
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.HostSurface
import org.w3c.dom.HTMLElement

/** The Kotlin/JS half of the consumer compile test: the only place that needs the JS SDK element. */
internal actual fun attachElement(
    scope: CoroutineScope,
    ref: KadreApplicationFactoryRef,
): KadreResult<KadreSession> = hostElement().attachKadre(
    parentScope = scope,
    applicationFactory = KadreApplicationFactory { WebConsumer.application() },
    policy = KadrePolicies.Default,
    attachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
)

internal actual fun attachElementWithApplication(scope: CoroutineScope): KadreResult<KadreSession> =
    hostElement().attachKadre(
        parentScope = scope,
        policy = KadrePolicies.Realtime,
        attachmentPolicy = WebAttachmentPolicy.Manual,
        application = KadreApplication { },
    )

@KadrePlatformApi
@DelicateKadreApi
internal actual suspend fun demonstrateElementLease(surface: HostSurface): KadreResult<Boolean> =
    surface.withWebElement { element -> element.tagName.isNotEmpty() }

private fun hostElement(): HTMLElement = document.createElement("div") as HTMLElement
