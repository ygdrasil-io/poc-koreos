package org.graphiks.kadre.contracts.driver.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.web.WebAttachmentPolicy
import org.graphiks.kadre.platform.web.attachKadre
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement

public fun main() {
    when (scenarioName()) {
        "initial-disconnected" -> initialDisconnectedScenario()
        "durable-detach" -> attachScenario("durable")
        "detach-reinsert" -> attachScenario("reinsert")
        "shadow-root" -> attachScenario("shadow")
        "inter-document" -> attachScenario("transfer")
        "manual-reconnect" -> manualReconnectScenario()
        "manual-detach-reconnect" -> manualDetachReconnectScenario()
        "independent" -> independentScenario()
        "duplicate" -> duplicateScenario()
        "focus" -> focusScenario()
        "pagehide" -> attachScenario("pagehide")
        "host-owned" -> hostOwnedScenario()
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
