package org.graphiks.kadre.contracts.driver.web

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.attachKadre
import org.w3c.dom.HTMLElement

public fun main() {
    val host = (document.createElement("div") as HTMLElement).also { element ->
        element.setAttribute("data-kadre-host", "phase0")
        document.body!!.appendChild(element)
    }
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
