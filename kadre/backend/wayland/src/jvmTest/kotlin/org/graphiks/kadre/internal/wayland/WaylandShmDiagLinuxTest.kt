package org.graphiks.kadre.internal.wayland

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertTrue

/** DIAGNOSTIC JETABLE : flux d'intégration kffi exact (64×64, attach, frame). */
class WaylandShmDiagLinuxTest {

    @Test
    fun kffiExactFlowOnTestThread() {
        // DIAG5 : le flux kffi exact, PUIS un cycle pollForEvents (le downcall
        // à tableau de structs de la pompe), PUIS une création de pool — si le
        // second pool échoue alors que le premier réussit, le downcall poll
        // corrompt le passage d'arguments des downcalls suivants.
        val client = org.graphiks.kffi.wayland.managed.WaylandClient.connect(null)
        checkNotNull(client) { "connect failed" }
        try {
            val surface = client.createSurface()
            val xdg = client.createXdgSurface(surface)
            val toplevel = client.createToplevel(xdg)
            val configureSerials = CopyOnWriteArrayList<Int>()
            xdg.addConfigureListener(onConfigure = { configureSerials.add(it) }, onFailure = {})
            surface.commit()
            val deadline = System.nanoTime() + 10_000_000_000L
            while (configureSerials.isEmpty() && System.nanoTime() < deadline) {
                client.roundtrip()
                Thread.sleep(25)
            }
            println("DIAG5 configure=$configureSerials")
            val buffer = client.createShmBuffer(64, 64)
            val pixels = buffer.content()
            for (index in 0 until 64 * 64) {
                pixels.set(java.lang.foreign.ValueLayout.JAVA_INT, index.toLong() * 4, 0xFF3366AA.toInt())
            }
            xdg.ackConfigure(configureSerials.last())
            // DIAG6 : wake seul (open + signal + close) SANS le cycle
            // prepare/poll/cancel — si le second pool échoue encore, le
            // coupable est PosixWakeup ; s'il réussit, c'est le cycle read
            // (le downcall poll à capture errno)
            val wake = org.graphiks.kffi.posix.PosixWakeup.open()
            wake.signal()
            wake.drain()
            wake.close()
            println("DIAG6 wake exercised")
            val framed = CountDownLatch(1)
            surface.addFrameListener(onFrame = { framed.countDown() }, onFailure = {})
            surface.attachAndCommit(buffer)
            val deadline2 = System.nanoTime() + 10_000_000_000L
            while (System.nanoTime() < deadline2 && framed.count > 0L) {
                client.roundtrip()
                Thread.sleep(25)
            }
            val secondBuffer = client.createShmBuffer(32, 32)
            println("DIAG5 framed=${framed.count > 0} secondPool=${secondBuffer != null} healthy=${client.roundtrip()}")
            secondBuffer.close()
            assertTrue(framed.count > 0, "DIAG5: surface did not map")
            buffer.close()
            toplevel.close()
            xdg.close()
            surface.close()
        } finally {
            client.close()
        }
    }
}
