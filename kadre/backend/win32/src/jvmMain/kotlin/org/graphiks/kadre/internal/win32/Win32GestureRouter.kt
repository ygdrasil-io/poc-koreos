package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.input.*
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.*

internal class Win32GestureRouter(private val surfaceId: SurfaceId, val kinds: Set<GestureKind>,
    private val scale: () -> Double, private val emit: (SurfaceStimulus.Gesture) -> Boolean, private val isAlive: () -> Boolean) {
    private val active = linkedSetOf<GestureKind>()
    private var generation = 0L
    init { require(kinds.all { it in setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation, GestureKind.DoubleTap) }) }
    fun accept(output: Win32GestureOutput) {
        if (!isAlive() || output.inputType !in 2..3) return
        check(output.flags and 8 == 0) { "Unexpected native inertia" }
        val epoch = generation
        if (output.interactionId == 2) {
            if (output.tapCount == 2 && GestureKind.DoubleTap in kinds)
                emit(SurfaceStimulus.Gesture(surfaceId, GestureKind.DoubleTap, TouchPhase.Ended))
            return
        }
        if (output.interactionId != 1) return
        val dpi = scale().also { require(it.isFinite() && it > 0) }
        val dx = output.translationX?.also { require(it.isFinite()) }
        val dy = output.translationY?.also { require(it.isFinite()) }
        val factor = output.scale?.also { require(it.isFinite() && it > 0) }
        val angle = output.rotationRadians?.also { require(it.isFinite()) }
        val terminal = when { output.flags and 4 != 0 -> TouchPhase.Cancelled; output.flags and 2 != 0 -> TouchPhase.Ended; else -> null }
        for (kind in kinds) {
            if (!isAlive() || epoch != generation) return
            val changed = when (kind) {
                GestureKind.Pan -> dx != null && dy != null && (dx != 0.0 || dy != 0.0)
                GestureKind.Pinch -> factor != null && factor != 1.0
                GestureKind.Rotation -> angle != null && angle != 0.0
                else -> false
            }
            if (kind !in active && !changed) continue
            val phase = if (kind !in active) TouchPhase.Started else terminal ?: TouchPhase.Moved
            active += kind
            if (phase == TouchPhase.Ended || phase == TouchPhase.Cancelled) active -= kind
            emit(SurfaceStimulus.Gesture(surfaceId, kind, phase,
                delta=if (kind == GestureKind.Pan && dx != null && dy != null) LogicalDelta(dx / dpi, dy / dpi) else null,
                scale=if (kind == GestureKind.Pinch) factor else null,
                rotationRadians=if (kind == GestureKind.Rotation) angle else null))
            if (terminal != null && phase == TouchPhase.Started && epoch == generation && isAlive()) {
                active -= kind
                emit(SurfaceStimulus.Gesture(surfaceId, kind, terminal))
            }
        }
    }
    fun reset() {
        generation++
        val ended = active.toList(); active.clear()
        for (kind in ended) if (isAlive()) emit(SurfaceStimulus.Gesture(surfaceId, kind, TouchPhase.Cancelled))
    }
}
