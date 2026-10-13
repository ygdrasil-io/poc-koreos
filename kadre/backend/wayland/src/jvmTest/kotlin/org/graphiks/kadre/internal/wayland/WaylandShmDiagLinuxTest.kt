package org.graphiks.kadre.internal.wayland

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertTrue

/** DIAGNOSTIC JETABLE : flux d'intégration kffi exact (64×64, attach, frame). */
class WaylandShmDiagLinuxTest {

    @Test
    fun kffiExactFlowOnTestThread() {
        // DIAG3 : le même flux avec le NOM EXPLICITE que le provider kadre passe
        val explicitName = System.getenv("WAYLAND_DISPLAY")
        println("DIAG3 v2 explicitName=$explicitName")
        val client = org.graphiks.kffi.wayland.managed.WaylandClient.connect(explicitName)
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
            println("DIAG3 configure=$configureSerials")
            val buffer = client.createShmBuffer(64, 64)
            val pixels = buffer.content()
            for (index in 0 until 64 * 64) {
                pixels.set(java.lang.foreign.ValueLayout.JAVA_INT, index.toLong() * 4, 0xFF3366AA.toInt())
            }
            xdg.ackConfigure(configureSerials.last())
            val framed = CountDownLatch(1)
            surface.addFrameListener(onFrame = { framed.countDown() }, onFailure = {})
            surface.attachAndCommit(buffer)
            val deadline2 = System.nanoTime() + 10_000_000_000L
            while (System.nanoTime() < deadline2 && framed.count > 0L) {
                client.roundtrip()
                Thread.sleep(25)
            }
            println("DIAG3 framed=${framed.count > 0} healthy=${client.roundtrip()}")
            assertTrue(framed.count > 0, "DIAG3: surface did not map")
            buffer.close()
            toplevel.close()
            xdg.close()
            surface.close()
        } finally {
            client.close()
        }
    }
}
