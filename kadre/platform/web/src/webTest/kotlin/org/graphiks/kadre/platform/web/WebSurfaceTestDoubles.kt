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

    override fun installMetricsObserver(observer: (WebSurfaceMetrics) -> Unit) {
        metricsObserver = observer
    }

    /** Pushes [metrics] as if the target had just read the element back. */
    fun deliver(metrics: WebSurfaceMetrics) {
        metricsObserver?.invoke(metrics)
    }

    override fun release() {
        metricsObserver = null
    }
}
