package org.graphiks.kadre.samples.hellotriangle

import io.ygdrasil.webgpu.SurfaceTextureStatus
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_DeviceLost
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_Error
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_Lost
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_OutOfMemory
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_Outdated
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_SuccessOptimal
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_SuccessSuboptimal
import io.ygdrasil.wgpu.WGPUSurfaceGetCurrentTextureStatus_Timeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(ExperimentalUnsignedTypes::class)
class SurfaceFramePolicyTest {
    @Test
    fun `stale wgpu4k statuses map to the pinned native v25 frame actions`() {
        assertEquals(SurfaceFrameAction.Render, surfaceFrameAction(SurfaceTextureStatus.timeout))
        assertEquals(SurfaceFrameAction.RenderThenReconfigure, surfaceFrameAction(SurfaceTextureStatus.outdated))
        assertEquals(SurfaceFrameAction.Skip, surfaceFrameAction(SurfaceTextureStatus.lost))
        assertEquals(SurfaceFrameAction.Reconfigure, surfaceFrameAction(SurfaceTextureStatus.outOfMemory))
        assertEquals(SurfaceFrameAction.Reconfigure, surfaceFrameAction(SurfaceTextureStatus.deviceLost))
        assertEquals(SurfaceFrameAction.Stop, surfaceFrameAction(SurfaceTextureStatus.success))
    }

    @Test
    fun `native v25 surface texture status ABI remains one through eight`() {
        assertContentEquals(
            uintArrayOf(1u, 2u, 3u, 4u, 5u, 6u, 7u, 8u),
            uintArrayOf(
                WGPUSurfaceGetCurrentTextureStatus_SuccessOptimal,
                WGPUSurfaceGetCurrentTextureStatus_SuccessSuboptimal,
                WGPUSurfaceGetCurrentTextureStatus_Timeout,
                WGPUSurfaceGetCurrentTextureStatus_Outdated,
                WGPUSurfaceGetCurrentTextureStatus_Lost,
                WGPUSurfaceGetCurrentTextureStatus_OutOfMemory,
                WGPUSurfaceGetCurrentTextureStatus_DeviceLost,
                WGPUSurfaceGetCurrentTextureStatus_Error,
            ),
        )
    }

    @Test
    fun `first tick requests redraw and advances the deadline by sixteen milliseconds`() {
        val pacer = FramePacer()

        assertEquals(
            FrameSchedule(requestRedraw = true, nextDeadlineMillis = 116L),
            pacer.schedule(nowMillis = 100L),
        )
    }

    @Test
    fun `early tick preserves the deadline without requesting redraw`() {
        val pacer = FramePacer()
        pacer.schedule(nowMillis = 100L)

        assertEquals(
            FrameSchedule(requestRedraw = false, nextDeadlineMillis = 116L),
            pacer.schedule(nowMillis = 115L),
        )
    }

    @Test
    fun `due tick requests redraw and advances the deadline again`() {
        val pacer = FramePacer()
        pacer.schedule(nowMillis = 100L)

        assertEquals(
            FrameSchedule(requestRedraw = true, nextDeadlineMillis = 132L),
            pacer.schedule(nowMillis = 116L),
        )
    }

    @Test
    fun `strongly overdue tick starts a fresh interval without a redraw burst`() {
        val pacer = FramePacer()
        pacer.schedule(nowMillis = 100L)

        assertEquals(
            FrameSchedule(requestRedraw = true, nextDeadlineMillis = 166L),
            pacer.schedule(nowMillis = 150L),
        )
    }

    @Test
    fun `epoch clock rollback starts a fresh interval immediately`() {
        val pacer = FramePacer()
        pacer.schedule(nowMillis = 100L)

        assertEquals(
            FrameSchedule(requestRedraw = true, nextDeadlineMillis = 66L),
            pacer.schedule(nowMillis = 50L),
        )
    }
}
