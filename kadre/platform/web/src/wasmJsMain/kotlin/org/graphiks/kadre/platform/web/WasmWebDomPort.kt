@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import org.w3c.dom.Document
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import org.w3c.dom.Node
import org.w3c.dom.ShadowRoot
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsArray
import kotlin.js.unsafeCast
import kotlin.math.max

internal class WasmWebDomPort(element: HTMLElement) : WebHostPort {
    private var element: HTMLElement? = element
    private val originDocument: Document = checkNotNull(element.ownerDocument)
    private val originWindow: Window? = originDocument.defaultView
    private var lifecycleObserver: ((WebLifecycleSnapshot) -> Unit)? = null
    private var mutationObserver: MutationObserver? = null
    private var observedRoot: Node? = null
    private var reconnectAnimationFrame: Int? = null
    private var active: Boolean = false
    private var browsingContextFocused: Boolean = originDocument.hasFocus()
    private var subtreeFocused: Boolean = element.matches(":focus-within")

    private val visibilityListener: (Event) -> Unit = {
        safely { deliverSnapshot() }
    }
    private val windowFocusListener: (Event) -> Unit = {
        safely {
            browsingContextFocused = true
            deliverSnapshot()
        }
    }
    private val windowBlurListener: (Event) -> Unit = {
        safely {
            browsingContextFocused = false
            deliverSnapshot()
        }
    }
    private val subtreeFocusInListener: (Event) -> Unit = {
        safely {
            subtreeFocused = true
            deliverSnapshot()
        }
    }
    private val subtreeFocusOutListener: (Event) -> Unit = {
        safely {
            subtreeFocused = false
            deliverSnapshot()
        }
    }
    private val pagehideListener: (Event) -> Unit = {
        safely { deliverSnapshot(pageHidden = true) }
    }

    override val stableIdentity: Any get() = checkNotNull(element)
    override val initialSnapshot: WebSurfaceSnapshot = element.surfaceSnapshot()
    override val initialLifecycleSnapshot: WebLifecycleSnapshot = lifecycleSnapshot(element)

    override fun installLifecycleObserver(observer: (WebLifecycleSnapshot) -> Unit) {
        check(lifecycleObserver == null)
        lifecycleObserver = observer
        active = true
        originDocument.addEventListener("visibilitychange", visibilityListener)
        originWindow?.addEventListener("focus", windowFocusListener)
        originWindow?.addEventListener("blur", windowBlurListener)
        originWindow?.addEventListener("pagehide", pagehideListener)
        element?.addEventListener("focusin", subtreeFocusInListener)
        element?.addEventListener("focusout", subtreeFocusOutListener)

        val current = checkNotNull(element)
        if (current.isConnected) installObserverForCurrentRoot(current) else scheduleReconnect()
    }

    override fun release() {
        if (!active && element == null) return
        active = false
        runCatching { originDocument.removeEventListener("visibilitychange", visibilityListener) }
        runCatching { originWindow?.removeEventListener("focus", windowFocusListener) }
        runCatching { originWindow?.removeEventListener("blur", windowBlurListener) }
        runCatching { originWindow?.removeEventListener("pagehide", pagehideListener) }
        runCatching { element?.removeEventListener("focusin", subtreeFocusInListener) }
        runCatching { element?.removeEventListener("focusout", subtreeFocusOutListener) }
        runCatching { mutationObserver?.disconnect() }
        reconnectAnimationFrame?.let { animationFrame ->
            runCatching { originWindow?.cancelAnimationFrame(animationFrame) }
        }
        reconnectAnimationFrame = null
        observedRoot = null
        mutationObserver = null
        lifecycleObserver = null
        element = null
    }

    private fun handleMutationBatch() {
        val current = element ?: return
        deliverSnapshot()
        if (!active || current.ownerDocument !== originDocument) return
        if (current.isConnected) {
            cancelReconnect()
            installObserverForCurrentRoot(current)
        } else {
            mutationObserver?.disconnect()
            mutationObserver = null
            observedRoot = null
            scheduleReconnect()
        }
    }

    private fun installObserverForCurrentRoot(current: HTMLElement) {
        val root = current.observationRoot() ?: return
        if (observedRoot === root) return
        mutationObserver?.disconnect()
        val next = MutationObserver { _, _ -> safely { handleMutationBatch() } }
        mutationObserver = next
        observedRoot = root
        next.observe(
            root,
            MutationObserverInit(
                childList = true,
                attributes = true,
                subtree = true,
                attributeFilter = emptyList<JsString>().toJsArray(),
            ),
        )
    }

    private fun scheduleReconnect() {
        if (!active || reconnectAnimationFrame != null) return
        val browserWindow = originWindow ?: return
        reconnectAnimationFrame = browserWindow.requestAnimationFrame {
            reconnectAnimationFrame = null
            safely {
                val current = element ?: return@safely
                deliverSnapshot()
                if (!active || current.ownerDocument !== originDocument) return@safely
                if (current.isConnected) installObserverForCurrentRoot(current) else scheduleReconnect()
            }
        }
    }

    private fun cancelReconnect() {
        val animationFrame = reconnectAnimationFrame ?: return
        reconnectAnimationFrame = null
        runCatching { originWindow?.cancelAnimationFrame(animationFrame) }
    }

    private fun deliverSnapshot(pageHidden: Boolean = false) {
        val current = element ?: return
        subtreeFocused = current.matches(":focus-within")
        lifecycleObserver?.invoke(lifecycleSnapshot(current, pageHidden))
    }

    private fun lifecycleSnapshot(
        current: HTMLElement,
        pageHidden: Boolean = false,
    ): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = current.isConnected,
        inOriginDocument = current.ownerDocument === originDocument,
        documentVisible = originDocument.unsafeCast<WasmDocumentVisibility>().visibilityState.toString() == "visible",
        browsingContextFocused = browsingContextFocused,
        subtreeFocused = subtreeFocused,
        pageHidden = pageHidden,
    )

    private fun safely(block: () -> Unit) {
        if (!active) return
        runCatching(block)
    }
}

private fun HTMLElement.surfaceSnapshot(): WebSurfaceSnapshot {
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

private fun HTMLElement.observationRoot(): Node? = getRootNode().let { root ->
    if (root is Document || root is ShadowRoot) root else null
}

private external interface WasmDocumentVisibility : JsAny {
    val visibilityState: JsString
}
