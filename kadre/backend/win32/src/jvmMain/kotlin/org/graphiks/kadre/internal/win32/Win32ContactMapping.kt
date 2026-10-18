package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.input.GestureKind
import java.util.concurrent.atomic.AtomicBoolean

/** Owned Kotlin snapshots. Native storage and layouts never cross this interface. */
internal data class Win32ContactSample(val id: Int, val type: Int, val flags: Int, val x: Int, val y: Int,
    val pressure: Double? = null, val tiltX: Double? = null, val tiltY: Double? = null,
    val twistRadians: Double? = null, val eraser: Boolean = false, val buttonChange: Int = 0, val target: Long = 0)
internal data class Win32ContactFrame(val id: Int, val samples: List<Win32ContactSample>)
internal class Win32ContactBatch(val frames: List<Win32ContactFrame>, private val lease: AutoCloseable) : AutoCloseable {
    private val closed = AtomicBoolean()
    override fun close() { if (closed.compareAndSet(false, true)) lease.close() }
}
internal data class Win32GestureOutput(val interactionId: Int, val flags: Int, val inputType: Int,
    val translationX: Double?, val translationY: Double?, val scale: Double?, val rotationRadians: Double?, val tapCount: Int?)
internal interface Win32GesturePort : AutoCloseable {
    val kinds: Set<GestureKind>
    fun process(batch: Win32ContactBatch)
    fun reset()
    override fun close()
}
