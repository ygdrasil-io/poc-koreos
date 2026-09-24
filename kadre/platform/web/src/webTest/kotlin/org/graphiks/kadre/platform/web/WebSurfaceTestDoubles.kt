package org.graphiks.kadre.platform.web

/**
 * The one host-port double every web surface test drives.
 *
 * It hands the surface a fixed initial readback and lets a test push later readbacks on demand
 * through the observer the port was given. A single class is shared so the surface tests of every
 * phase observe the same target-side behaviour; members are added as the surface starts consuming
 * more of the port.
 */
internal class RecordingWebHostPort(initial: WebSurfaceMetrics) : WebHostPort {
    override val initialSnapshot: WebSurfaceMetrics = initial
    override val stableIdentity: Any = Any()
    private var metricsObserver: ((WebSurfaceMetrics) -> Unit)? = null
    private var lifecycleObserver: ((WebLifecycleSnapshot) -> Unit)? = null
    private var frame: (() -> Unit)? = null
    private var released: Boolean = false

    /** How often the surface released this port; release is terminal and happens exactly once. */
    var releaseCount: Int = 0
        private set

    /** How often the surface cancelled a frame it had registered; a run frame is not a cancel. */
    var frameCancellations: Int = 0
        private set

    /**
     * Invoked by the first [release], before the port drops the target resources it holds.
     *
     * A test reads it back to observe what the surface had published at the moment Kadre let the
     * element go — an ordering the surface's own terminal transition is otherwise silent about.
     */
    var onRelease: (() -> Unit)? = null

    override fun installLifecycleObserver(observer: (WebLifecycleSnapshot) -> Unit) {
        lifecycleObserver = observer
    }

    override fun installMetricsObserver(observer: (WebSurfaceMetrics) -> Unit) {
        metricsObserver = observer
    }

    /** Pushes [metrics] as if the target had just read the element back; inert once released. */
    fun deliverMetrics(metrics: WebSurfaceMetrics) {
        metricsObserver?.invoke(metrics)
    }

    /** Pushes [snapshot] as if the target had just observed the browsing context. */
    fun deliverLifecycle(snapshot: WebLifecycleSnapshot) {
        lifecycleObserver?.invoke(snapshot)
    }

    /** The browsing context is gone, as a detached or removed document reports it. */
    fun disconnectedSnapshot(): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = false,
        inOriginDocument = true,
        documentVisible = true,
        browsingContextFocused = true,
        subtreeFocused = true,
    )

    /** The document is being hidden, as `pagehide` reports it. */
    fun pageHiddenSnapshot(): WebLifecycleSnapshot = disconnectedSnapshot().copy(pageHidden = true)

    override fun scheduleFrame(callback: () -> Unit): WebFrameHandle {
        frame = callback
        return WebFrameHandle {
            frameCancellations += 1
            frame = null
        }
    }

    /**
     * Runs the frame the surface has registered, as the browsing context would.
     *
     * A [release] does not take that registration away: the browser owns the queued callback and the
     * port only drops what it installed itself, so a frame the surface has not cancelled still fires
     * in the window a cooperative stop leaves between the release and the runtime's close.
     */
    fun runFrame() {
        val scheduled = frame ?: return
        frame = null
        scheduled()
    }

    /**
     * Drops the target's own bridges, as a released DOM port does: the readbacks stop here.
     *
     * The registered frame survives it, because only the surface cancels the frame it registered.
     */
    override fun release() {
        releaseCount += 1
        if (released) return
        released = true
        onRelease?.invoke()
        metricsObserver = null
        lifecycleObserver = null
    }
}
