package org.graphiks.kadre.samples.simulation.scenarios.touch

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*
import kotlin.math.sqrt

class GesturesScenario : TouchScenario(
    id = "touch-gestures",
    title = "Gestes tactiles",
    description = "Reconnaît des gestes simples : tap, double-tap, swipe.",
    priority = 80
) {
    override val requiredCapabilities: Set<Capability> = setOf(Capability.MULTI_TOUCH)

    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var isTracking = false
    private var gestureCount = 0

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        super.start(window, eventLoop, onEvent)
        onEvent(ScenarioEvent.Message("Essayez des gestes : tap, double-tap, swipe", MessageSeverity.INFO))
    }

    override fun onTouchEvent(event: WindowEvent.Touch) {
        eventsReceived++

        when (event.phase) {
            TouchPhase.STARTED -> {
                gestureStartX = event.x
                gestureStartY = event.y
                isTracking = true

                onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                    isRunning = true,
                    message = "🟢 Touch début à (${event.x.toInt()}, ${event.y.toInt()})",
                    data = mapOf("phase" to "started", "x" to event.x.toInt(), "y" to event.y.toInt())
                )))
            }
            TouchPhase.ENDED -> {
                if (isTracking) {
                    val dx = event.x - gestureStartX
                    val dy = event.y - gestureStartY
                    val distance = sqrt(dx * dx + dy * dy)

                    gestureCount++
                    val gestureName = if (distance > 50f) {
                        val direction = when {
                            kotlin.math.abs(dx) > kotlin.math.abs(dy) ->
                                if (dx > 0) "swipe_droite" else "swipe_gauche"
                            else -> if (dy > 0) "swipe_bas" else "swipe_haut"
                        }
                        direction
                    } else {
                        "tap"
                    }

                    onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                        isRunning = true,
                        message = "✅ Geste #$gestureCount: $gestureName (${distance.toInt()}px)",
                        data = mapOf("gesture" to gestureName, "count" to gestureCount)
                    )))
                    isTracking = false
                }
            }
            TouchPhase.MOVED -> {
                if (isTracking) {
                    val dx = event.x - gestureStartX
                    val dy = event.y - gestureStartY
                    onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                        isRunning = true,
                        message = "✋ Déplacement: (${dx.toInt()}, ${dy.toInt()})",
                        data = mapOf("phase" to "moved", "dx" to dx.toInt(), "dy" to dy.toInt())
                    )))
                }
            }
            TouchPhase.CANCELLED -> {
                isTracking = false
                onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                    isRunning = true,
                    message = "❌ Touch annulé",
                    data = mapOf("phase" to "cancelled")
                )))
            }
            null -> {}
        }
    }
}
