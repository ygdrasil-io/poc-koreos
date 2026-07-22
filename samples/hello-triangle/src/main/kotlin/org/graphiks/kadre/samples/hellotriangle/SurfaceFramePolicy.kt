package org.graphiks.kadre.samples.hellotriangle

import io.ygdrasil.webgpu.SurfaceTextureStatus

/** Actions for the stale wgpu4k surface texture status enum against wgpu-native v25. */
internal enum class SurfaceFrameAction {
    Render,
    RenderThenReconfigure,
    Skip,
    Reconfigure,
    Stop,
}

/**
 * wgpu4k 0.1.1 exposes the pre-v25 status ordinals, while wgpu-native v25 emits
 * SuccessOptimal through Error as 1u through 8u.
 */
internal fun surfaceFrameAction(status: SurfaceTextureStatus): SurfaceFrameAction = when (status) {
    SurfaceTextureStatus.timeout -> SurfaceFrameAction.Render
    SurfaceTextureStatus.outdated -> SurfaceFrameAction.RenderThenReconfigure
    SurfaceTextureStatus.lost -> SurfaceFrameAction.Skip
    SurfaceTextureStatus.outOfMemory,
    SurfaceTextureStatus.deviceLost -> SurfaceFrameAction.Reconfigure
    SurfaceTextureStatus.success -> SurfaceFrameAction.Stop
}

internal data class FrameSchedule(
    val requestRedraw: Boolean,
    val nextDeadlineMillis: Long,
)

/**
 * Schedules redraws near 60 FPS without requiring a clock dependency.
 *
 * [nowMillis] may come from an epoch clock used with `ControlFlow.WaitUntil`. If that
 * clock moves backward, the pacer redraws immediately and starts a fresh interval so
 * the event loop is not left waiting on a deadline from the previous epoch.
 */
internal class FramePacer {
    private var nextDeadlineMillis: Long? = null
    private var previousNowMillis: Long? = null

    internal fun schedule(nowMillis: Long): FrameSchedule {
        val deadline = nextDeadlineMillis
        val clockMovedBackward = previousNowMillis?.let { nowMillis < it } == true
        previousNowMillis = nowMillis

        if (deadline == null || nowMillis >= deadline || clockMovedBackward) {
            val nextDeadline = nowMillis + FRAME_INTERVAL_MILLIS
            nextDeadlineMillis = nextDeadline
            return FrameSchedule(requestRedraw = true, nextDeadlineMillis = nextDeadline)
        }

        return FrameSchedule(requestRedraw = false, nextDeadlineMillis = deadline)
    }

    private companion object {
        const val FRAME_INTERVAL_MILLIS = 16L
    }
}
