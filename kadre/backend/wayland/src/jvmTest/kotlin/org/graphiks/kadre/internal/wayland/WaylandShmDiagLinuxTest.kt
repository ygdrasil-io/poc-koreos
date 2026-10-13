package org.graphiks.kadre.internal.wayland

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

/** DIAGNOSTIC JETABLE : séquence kffi pure (thread test) dans l'env de la gate. */
class WaylandShmDiagLinuxTest {

    @Test
    fun directFacadeShmOnTestThread() {
        val display = System.getenv("WAYLAND_DISPLAY") ?: error("no WAYLAND_DISPLAY")
        println("DIAG display=$display xdg=${System.getenv("XDG_RUNTIME_DIR")}")
        val client = org.graphiks.kffi.wayland.managed.WaylandClient.connect(display)
        checkNotNull(client) { "connect failed" }
        val fd = org.graphiks.kffi.wayland.createMemFd("kadre-diag", 4096)
        println("DIAG fd=$fd open=" + Files.exists(Path.of("/proc/self/fd/" + fd)))
        try {
            val buffer = client.createShmBuffer(16, 16)
            println("DIAG buffer ok; roundtrip=" + client.roundtrip())
            buffer.close()
        } catch (t: Throwable) {
            println("DIAG FAILED: $t")
            throw t
        } finally {
            client.close()
            if (fd >= 0) org.graphiks.kffi.wayland.closeFd(fd)
        }
    }
}
