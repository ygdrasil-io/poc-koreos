package org.graphiks.kadre.samples.simulation.scenarios.integration

import org.graphiks.kadre.core.*
import org.graphiks.kadre.samples.simulation.*

class GameScenario : Scenario {
    override val id = "game-simple"
    override val title = "Jeu simple (clavier + souris)"
    override val description = "Démonstration d'un mini-jeu combinant clavier (ZQSD/ WASD pour déplacement) et souris (clic pour tir)."
    override val category = "Intégration"
    override val requiredCapabilities: Set<Capability> = setOf(Capability.KEYBOARD, Capability.MOUSE)
    override val priority: Int = 100

    private var window: Window? = null
    private var eventLoop: ActiveEventLoop? = null
    private var onEvent: ((ScenarioEvent) -> Unit)? = null
    private var isRunning = false

    // Player state
    private var playerX = 400f
    private var playerY = 300f
    private val playerSpeed = 5f
    private var score = 0
    private var targets = mutableListOf<Target>()
    private var totalShots = 0
    private var hits = 0

    data class Target(
        val x: Float,
        val y: Float,
        val id: Int
    )

    override fun start(window: Window, eventLoop: ActiveEventLoop, onEvent: (ScenarioEvent) -> Unit) {
        this.window = window
        this.eventLoop = eventLoop
        this.onEvent = onEvent
        this.isRunning = true
        this.score = 0
        this.targets.clear()
        this.totalShots = 0
        this.hits = 0

        // Generate random targets
        val rng = kotlin.random.Random
        for (i in 0 until 5) {
            targets.add(Target(
                x = rng.nextFloat() * 700f + 50f,
                y = rng.nextFloat() * 500f + 50f,
                id = i
            ))
        }

        onEvent(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "🎮 ZQSD/WASD: déplacement | Clic gauche: tirer sur les cibles",
            data = mapOf("score" to 0, "player_x" to playerX.toInt(), "player_y" to playerY.toInt())
        )))
    }

    override fun stop() {
        isRunning = false
    }

    override fun onKeyEvent(event: WindowEvent.KeyInput) {
        if (!isRunning || !event.pressed) return

        when (event.key) {
            Key.Z, Key.W -> playerY -= playerSpeed
            Key.S -> playerY += playerSpeed
            Key.Q, Key.A -> playerX -= playerSpeed
            Key.D -> playerX += playerSpeed
            Key.R -> {
                score = 0
                targets.clear()
                val rng = kotlin.random.Random
                for (i in 0 until 5) {
                    targets.add(Target(
                        x = rng.nextFloat() * 700f + 50f,
                        y = rng.nextFloat() * 500f + 50f,
                        id = i
                    ))
                }
            }
            else -> {}
        }

        // Clamp player position
        playerX = playerX.coerceIn(0f, 800f)
        playerY = playerY.coerceIn(0f, 600f)

        onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
            isRunning = true,
            message = "🎮 Position: (${playerX.toInt()}, ${playerY.toInt()}) | Score: $score",
            data = mapOf("player_x" to playerX.toInt(), "player_y" to playerY.toInt(), "score" to score)
        )))
    }

    override fun onMouseEvent(event: WindowEvent.Mouse) {
        if (!isRunning) return

        if (event is WindowEvent.Mouse.Pressed) {
            totalShots++
            val mouseX = event.x
            val mouseY = event.y

            val hit = targets.find { target ->
                val dx = mouseX - target.x
                val dy = mouseY - target.y
                kotlin.math.sqrt(dx * dx + dy * dy) < 30f
            }

            if (hit != null) {
                hits++
                score += 10
                targets.remove(hit)

                // Add new target
                val rng = kotlin.random.Random
                targets.add(Target(
                    x = rng.nextFloat() * 700f + 50f,
                    y = rng.nextFloat() * 500f + 50f,
                    id = targets.size + 1
                ))

                onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                    isRunning = true,
                    message = "🎯 Cible touchée! +10 points (Score: $score)",
                    data = mapOf("score" to score, "hit" to true)
                )))
            } else {
                onEvent?.invoke(ScenarioEvent.StateChanged(ScenarioState(
                    isRunning = true,
                    message = "💨 Tir raté... (Score: $score)",
                    data = mapOf("score" to score, "hit" to false)
                )))
            }
        }
    }

    override fun runHeadless(args: List<String>): ScenarioResult {
        return ScenarioResult(
            success = true,
            durationMs = 2000,
            eventsReceived = totalShots,
            eventsExpected = 10,
            errors = if (targets.isNotEmpty()) listOf("${targets.size} targets remaining") else emptyList(),
            platform = Platform.current()
        )
    }
}
