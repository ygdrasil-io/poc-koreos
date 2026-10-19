package org.graphiks.kadre.internal.wayland

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind

/** Minimal face of a wl_data_offer the presenter drives (test seam). */
internal interface WaylandOfferFace {
    fun accept(serial: Int, mime: String?)
    fun receive(mime: String): Int
    fun finish()
    fun close()
}

/** Adapts the managed kffi offer to the face. */
internal class KffiOfferFace(
    private val offer: org.graphiks.kffi.wayland.managed.WaylandDataOffer,
) : WaylandOfferFace {
    override fun accept(serial: Int, mime: String?) = offer.accept(serial, mime)
    override fun receive(mime: String): Int = offer.receive(mime)
    override fun finish() = offer.finish()
    override fun close() = offer.close()
}

/**
 * Translate `wl_data_device` events into drop presentations for the
 * driver (design phase 7, D1/D3): offers are immutable (MIME frozen at
 * announce), a leave terminalises the live offer as LeftSurface, a drop
 * without a prior accept terminalises as Rejected (F1-2), a receive on
 * an unannounced mime is refused before any fd (F2-3), and destroyed
 * offers never serve a second transfer (F3-1).
 */
internal class WaylandDropPresenter {

    private val lock = Any()
    private var liveOffer: LiveOffer? = null
    private val closed = AtomicBoolean(false)

    /** The driver-installed sink: presents the immutable transfer source. */
    internal var onPresentDrop: (
        source: WaylandDropTransferSource,
        position: Pair<Double, Double>,
    ) -> Unit = { _, _ -> }

    internal var onOfferTerminated: (WaylandDropTerminal) -> Unit = { }

    private class LiveOffer(
        val proxy: WaylandOfferFace,
        val mimes: MutableSet<String>,
        val serial: Int,
        var acceptedMime: String? = null,
        val terminated: AtomicBoolean = AtomicBoolean(false),
    )

    /** `data_offer` — the compositor announces the proxy before `enter`. */
    fun onOfferProxy(offer: WaylandOfferFace) {
        // track the newest announced proxy: the offer events collect MIME types
        synchronized(lock) {
            pendingOffer = offer
            pendingMimes.clear()
        }
    }

    private var pendingOffer: WaylandOfferFace? = null
    private val pendingMimes = LinkedHashSet<String>()

    /** MIME announce on the pending offer. */
    fun onOfferMime(mime: String) {
        synchronized(lock) { pendingMimes.add(mime) }
    }

    /** `enter` — freeze the offer and present the immutable source. */
    fun onEnter(
        offerProxy: WaylandOfferFace,
        serial: Int,
        surfaceAddress: Long,
        x: Double,
        y: Double,
    ) {
        val mimes = synchronized(lock) {
            val collected = LinkedHashSet(pendingMimes)
            pendingMimes.clear()
            pendingOffer = null
            collected
        }
        val live = LiveOffer(offerProxy, mimes.toMutableSet(), serial)
        synchronized(lock) { liveOffer = live }
        val items = mimes.map { mime ->
            WaylandDropItemSource(
                descriptor = DropItemDescriptor(
                    displayName = null,
                    sizeBytes = null,
                    mimeTypes = listOf(mime),
                    kind = mimeToKind(mime),
                ),
                openFd = { receiveFor(live, mime) },
                onTerminal = { terminal -> terminalise(live, terminal) },
            )
        }
        if (items.isEmpty()) {
            // an offer with no announced mime is unusable: refuse it early
            offerProxy.accept(serial, null)
            terminalise(live, WaylandDropTerminal.Rejected)
            return
        }
        val source = WaylandDropTransferSource(items) {
            terminalise(live, WaylandDropTerminal.Rejected)
        }
        onPresentDrop(source, x to y)
    }

    /** `motion` — position update of the live offer (no state change). */
    fun onMotion(x: Double, y: Double) {
        // the runtime tracks positions through its own input events; the
        // presenter stays stateless here (no synthetic publications)
    }

    /** `leave` — the user abandoned the drag: LeftSurface (D2). */
    fun onLeave() {
        val live = synchronized(lock) { liveOffer } ?: return
        terminalise(live, WaylandDropTerminal.LeftSurface)
    }

    /** `drop` — the compositor delivered on the accepted offer. */
    fun onDrop(): KadreResult<Unit> {
        val live = synchronized(lock) { liveOffer } ?: run {
            return KadreResult.Failure(
                KadreFailure.InvalidRequest("drop-without-offer"),
            )
        }
        val mime = live.acceptedMime
        if (mime == null) {
            // F1-2: no prior accept — deterministic refusal, no receive
            live.proxy.accept(live.serial, null)
            terminalise(live, WaylandDropTerminal.Rejected)
            return KadreResult.Failure(
                KadreFailure.InvalidRequest("drop-without-accept"),
            )
        }
        live.proxy.finish()
        terminalise(live, WaylandDropTerminal.Completed)
        return KadreResult.Success(Unit)
    }

    /** Accepts the negotiated mime for the live offer (drop sink path). */
    fun accept(mime: String): KadreResult<Unit> {
        val live = synchronized(lock) { liveOffer }
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("accept-without-offer"))
        if (mime !in live.mimes) {
            // F2-3: never receive an unannounced mime
            return KadreResult.Failure(KadreFailure.InvalidRequest("mime-not-announced"))
        }
        live.acceptedMime = mime
        live.proxy.accept(live.serial, mime)
        return KadreResult.Success(Unit)
    }

    /** receive fd for the accepted mime, refused outside a live offer. */
    private fun receiveFor(live: LiveOffer, mime: String): Int {
        if (live.terminated.get()) return -1 // F3-1: destroyed offers never serve
        if (mime != live.acceptedMime) return -1
        return live.proxy.receive(mime)
    }

    private fun terminalise(live: LiveOffer, terminal: WaylandDropTerminal) {
        if (!live.terminated.compareAndSet(false, true)) return
        synchronized(lock) {
            if (liveOffer === live) liveOffer = null
        }
        live.proxy.close()
        onOfferTerminated(terminal)
    }

    /** Full teardown (session end): terminalises the live offer. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val live = synchronized(lock) { liveOffer } ?: return
        terminalise(live, WaylandDropTerminal.RecipientClosed)
    }

    private fun mimeToKind(mime: String): DropItemKind = when {
        mime.startsWith("text/") -> DropItemKind.Text
        mime.startsWith("text/uri-list") -> DropItemKind.Uri
        mime.startsWith("image/") -> DropItemKind.Binary
        else -> DropItemKind.Binary
    }
}
