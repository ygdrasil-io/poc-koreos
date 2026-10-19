package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.internal.runtime.RuntimePayloadBudget
import org.graphiks.kffi.win32.managed.*
import java.util.IdentityHashMap

/** Owns the native batch counterpart; adapters receive only copied Kotlin scalars. */
internal class KffiWin32ContactServices : AutoCloseable {
    private val batches = IdentityHashMap<Win32ContactBatch, Win32PointerBatch>()
    private val recognizers = linkedSetOf<Win32GesturePort>()
    fun read(pointerId: Int, maxPointers: Int, maxFrames: Int, budget: RuntimePayloadBudget): Win32ContactBatch {
        val source = Win32PointerPackets.read(pointerId, maxPointers, maxFrames, Win32PayloadBudget { bytes ->
            when (val reservation = budget.retain(bytes)) {
                is KadreResult.Success -> reservation.value
                is KadreResult.Failure -> throw KadreException(reservation.reason)
            }
        })
        var copyLease: AutoCloseable? = null
        try {
            val bytes = Math.addExact(Math.multiplyExact(source.frames.sumOf { it.pointers.size.toLong() }, 112L), source.frames.size * 24L)
            copyLease = when (val result = budget.retain(bytes)) {
                is KadreResult.Success -> result.value
                is KadreResult.Failure -> throw KadreException(result.reason)
            }
            val frames = source.frames.map { frame -> Win32ContactFrame(frame.frameId, frame.pointers.map { value ->
                Win32ContactSample(value.id, value.type, value.flags, value.position.x, value.position.y, value.pressure,
                    value.tiltX, value.tiltY, value.twistRadians, value.eraser, value.buttonChange, value.target)
            }) }
            val reservation = copyLease
            lateinit var result: Win32ContactBatch
            result = Win32ContactBatch(frames, AutoCloseable {
                batches.remove(result)
                try { source.close() } finally { reservation.close() }
            })
            batches[result] = source
            return result
        } catch (cause: Throwable) {
            try { source.close() } finally { copyLease?.close() }
            throw cause
        }
    }
    fun open(hwnd: Long, maxPointers: Int, output: (Win32GestureOutput) -> Unit): Win32GesturePort {
        val context = Win32InteractionContext.open(maxPointers, targetHwnd=hwnd) { value ->
            output(Win32GestureOutput(value.interactionId, value.flags, value.inputType, value.translationX,
                value.translationY, value.scale, value.rotationRadians, value.tapCount))
        }
        val config = context.configuration
        check(config.screenPixels)
        return object : Win32GesturePort {
            override val kinds = buildSet {
                if (config.pan) add(GestureKind.Pan)
                if (config.pinch) add(GestureKind.Pinch)
                if (config.rotation) add(GestureKind.Rotation)
                if (config.doubleTap) add(GestureKind.DoubleTap)
            }
            override fun process(batch: Win32ContactBatch) { context.process(checkNotNull(batches[batch])) }
            override fun reset() { context.reset() }
            override fun close() { recognizers.remove(this); context.close() }
        }.also { recognizers += it }
    }
    override fun close() {
        recognizers.toList().forEach { it.close() }
        batches.keys.toList().forEach { it.close() }
    }
}
