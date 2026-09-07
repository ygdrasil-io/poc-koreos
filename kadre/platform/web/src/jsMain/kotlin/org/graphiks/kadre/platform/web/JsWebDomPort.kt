package org.graphiks.kadre.platform.web

import org.w3c.dom.Document
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import org.w3c.dom.ShadowRoot
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import kotlin.js.unsafeCast
import kotlin.math.max

internal class JsWebDomPort(element: HTMLElement) : WebHostPort {
    private var element: HTMLElement? = element
    private val originDocument: Document = checkNotNull(element.ownerDocument)
    private val originWindow: Window? = originDocument.defaultView
    private var lifecycleObserver: ((WebLifecycleSnapshot) -> Unit)? = null
    private var documentObserver: MutationObserver? = null
    private var shadowRootObserver: MutationObserver? = null
    private var observedShadowRoot: ShadowRoot? = null
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

        installDocumentObserver()
        val current = checkNotNull(element)
        if (current.isConnected) updateShadowRootObserver(current) else scheduleReconnect()
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
        runCatching { documentObserver?.disconnect() }
        runCatching { shadowRootObserver?.disconnect() }
        reconnectAnimationFrame?.let { animationFrame ->
            runCatching { originWindow?.cancelAnimationFrame(animationFrame) }
        }
        reconnectAnimationFrame = null
        documentObserver = null
        observedShadowRoot = null
        shadowRootObserver = null
        lifecycleObserver = null
        element = null
    }

    private fun handleMutationBatch() {
        val current = element ?: return
        deliverSnapshot()
        if (!active || current.ownerDocument !== originDocument) return
        if (current.isConnected) {
            cancelReconnect()
            updateShadowRootObserver(current)
        } else {
            disconnectShadowRootObserver()
            scheduleReconnect()
        }
    }

    private fun installDocumentObserver() {
        check(documentObserver == null)
        val next = MutationObserver { _, _ -> safely { handleMutationBatch() } }
        documentObserver = next
        next.observe(originDocument, MutationObserverInit(childList = true, subtree = true))
    }

    private fun updateShadowRootObserver(current: HTMLElement) {
        val shadowRoot = current.getRootNode() as? ShadowRoot
        if (observedShadowRoot === shadowRoot) return
        disconnectShadowRootObserver()
        if (shadowRoot == null) return
        val next = MutationObserver { _, _ -> safely { handleMutationBatch() } }
        shadowRootObserver = next
        observedShadowRoot = shadowRoot
        next.observe(shadowRoot, MutationObserverInit(childList = true, subtree = true))
    }

    private fun disconnectShadowRootObserver() {
        shadowRootObserver?.disconnect()
        shadowRootObserver = null
        observedShadowRoot = null
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
                if (current.isConnected) updateShadowRootObserver(current) else scheduleReconnect()
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
        documentVisible = originDocument.unsafeCast<JsDocumentVisibility>().visibilityState == "visible",
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

private external interface JsDocumentVisibility {
    val visibilityState: String
}
