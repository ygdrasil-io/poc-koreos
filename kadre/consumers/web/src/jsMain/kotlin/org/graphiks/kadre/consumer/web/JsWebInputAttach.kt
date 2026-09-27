package org.graphiks.kadre.consumer.web

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.WebAttachmentPolicy
import org.graphiks.kadre.platform.web.attachKadre
import org.graphiks.kadre.policy.KadrePolicies
import org.w3c.dom.HTMLElement

/**
 * The Kotlin/JS half of the input consumer: attaches its own element, then reads the surface it
 * publishes through [WebInputConsumer.readInput].
 *
 * The attach is the only target-specific act. The session's application completes the deferred scope
 * as soon as it starts, so the reading happens on the live surface and the surface is closed with the
 * session, exactly as the platform's own tests drive it.
 */
internal actual suspend fun attachedInputReading(scope: CoroutineScope): KadreResult<WebInputReading> {
    val scopeReady = CompletableDeferred<KadreScope>()
    val attached = hostElement().attachKadre(
        parentScope = scope,
        applicationFactory = KadreApplicationFactory {
            KadreApplication {
                scopeReady.complete(this)
                awaitCancellation()
            }
        },
        policy = KadrePolicies.Default,
        attachmentPolicy = WebAttachmentPolicy.StopWhenDetached,
    )
    val session = when (attached) {
        is KadreResult.Success -> attached.value
        is KadreResult.Failure -> return attached
    }
    return try {
        val surface = scopeReady.await().primarySurface.value
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("primarySurface"))
        KadreResult.Success(WebInputConsumer.readInput(surface))
    } finally {
        session.close()
    }
}

private fun hostElement(): HTMLElement = document.createElement("div") as HTMLElement
