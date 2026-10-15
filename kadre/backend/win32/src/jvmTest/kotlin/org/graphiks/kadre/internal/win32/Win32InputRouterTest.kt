package org.graphiks.kadre.internal.win32

import kotlin.test.*
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.SurfaceId

class Win32InputRouterTest {
    @Test fun wheelBoundariesSeparateAxesSettingsFlagsAndDiscreteInput() {
        val port = DeterministicWin32HostPort()
        val stimuli = mutableListOf<SurfaceStimulus>()
        val router = Win32InputRouter(1, SurfaceId(1), port, { stimuli += it; true }, {}, { true })
        router.activate()
        fun wheel(message: Int = 0x20a, flags: Long = 0) = router.handle(message, (60L shl 16) or flags, 0)
        wheel(); wheel()
        wheel(0x20e)
        port.wheelSettings = Win32WheelSettings(7, 4)
        wheel(0x20e)
        wheel(0x20e, 8)
        router.handle(0x201, 1, 0)
        wheel(0x20e, 8)
        router.handle(0x7, 0, 0)
        wheel(0x20e, 8)
        val boundaries = stimuli.filterIsInstance<SurfaceStimulus.Scroll>().map { it.coalescingBoundary }
        assertEquals(boundaries[0], boundaries[1])
        assertTrue(boundaries.drop(1).zipWithNext().all { (a, b) -> b > a })
        router.close()
        val count = stimuli.size
        wheel()
        assertEquals(count, stimuli.size)
    }
}
