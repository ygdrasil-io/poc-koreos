package org.graphiks.kadre.platform.android

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.DeviceEffectOwnership
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.GamepadRouting
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.RawInputDeliveryPolicy
import org.graphiks.kadre.policy.RawInputOverflowAction
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Le port gamepad Android (Task 2, phase 7) contre un fake du seam [AndroidInputDeviceSource] —
 * JAMAIS le SDK (le stub android.jar lance à la construction, cf. `AndroidDisplayPortTest`). Les
 * cas du plan :
 *
 * 3. `inventoryFromMotionRangesAndMappingTables` (+ `axesOutsideThePortedTableStayAbsent`) — un
 *    device fake gamepad (motion ranges X/Y) → le descripteur : les boutons selon la table
 *    kadre-old portée (A→South, …), les axes selon la table kadre-old portée sur les motion
 *    ranges, `mapping == Standard`, l'état neutre exact ; la table est FERMÉE ;
 * 4. `disconnectNeutralizesState` — Connected (état non-neutre) puis Disconnected → l'inventaire
 *    retiré (avant la livraison), la reconnexion EST une connexion neuve (boutons 0.0, axes
 *    0.0 — la règle de neutralisation du runtime, tenue AUSSI côté port) ; la suspension porte
 *    l'état neutre tout en gardant la lecture enregistrée (un resume trouve les valeurs que la
 *    manette lit maintenant, le miroir du hub web) ;
 * 5. `effectsPerDeviceGatedByApiAndVibrator` (+ `capabilityIsFrozenAtConnection`,
 *    `startEffectRefusals`) — API 31+ avec des moteurs observés → `Supported` (DualRumble),
 *    API 26-30 avec getVibrator → `Supported` one-shot, sans vibreur → `Unsupported` épinglé ;
 *    `startEffect` cible le vibreur du device RÉEL (le fake enregistre l'id cible — jamais un
 *    autre vibreur, jamais le téléphone) ; la capability est GELÉE à la connexion ; les refus
 *    honnêtes précèdent tout appel natif ; un owner arrête exactement une fois ;
 * 6. `stateRoutedFromInputChain` — `onGamepadKeyEvent`/`onGamepadMotion` → `StateChanged`
 *    (routé), lecture enregistrée sans événement (suspendu), le diff canonique (pas de no-op
 *    streamé, pas de valeur non finie, clamp honnête) ; et le SOUS-EXACT de la règle :
 *    `gamepadSourceEventsLeaveTheOrdinaryKeyPath` — un `dispatchKeyEvent` source SOURCE_GAMEPAD
 *    n'apparaît PLUS comme `KeyChanged` dans `SurfaceInput.events` (il alimente le port), la
 *    MÊME touche source clavier suit le chemin ordinaire entière, une motion source joystick
 *    n'ouvre plus de lane pointeur/tactile ; et `withoutAGamepadPortTheOrdinaryPathStaysWhole`
 *    épinglant le rayon d'action : SANS port, le chemin ordinaire reste entier (le comportement
 *    pré-existant des events gamepad-sourcés).
 */
class AndroidGamepadPortTest {

    /** Le double du seam SDK — devices pilotables, fires manuels, vibreurs enregistrés par id. */
    private class FakeInputDeviceSource : AndroidInputDeviceSource {
        val listeners = mutableListOf<(Int) -> Unit>()
        val devices = linkedMapOf<Int, AndroidInputDeviceInfo>()
        val rumbleStarts = mutableListOf<Triple<Int, Long, Pair<Float, Float>>>()
        val oneShots = mutableListOf<Triple<Int, Long, Float>>()
        val cancels = mutableListOf<Int>()
        var vibrationAnswer = true

        override fun deviceIds(): List<Int> = devices.keys.toList()

        override fun deviceInfo(deviceId: Int): AndroidInputDeviceInfo? = devices[deviceId]

        override fun registerListener(listener: (Int) -> Unit) {
            listeners += listener
        }

        override fun unregisterListener(listener: (Int) -> Unit) {
            listeners.remove(listener)
        }

        override fun startManagedRumble(deviceId: Int, durationMs: Long, strongMagnitude: Float, weakMagnitude: Float): Boolean {
            rumbleStarts += Triple(deviceId, durationMs, strongMagnitude to weakMagnitude)
            return vibrationAnswer
        }

        override fun startOneShot(deviceId: Int, durationMs: Long, amplitude: Float): Boolean {
            oneShots += Triple(deviceId, durationMs, amplitude)
            return vibrationAnswer
        }

        override fun cancelVibration(deviceId: Int) {
            cancels += deviceId
        }

        override fun close() = Unit

        fun fire(deviceId: Int) = listeners.toList().forEach { it(deviceId) }
    }

    private fun gamepad(
        id: Int = 7,
        name: String? = "Fake Pad",
        axes: List<Int> = listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y),
        vibratorIds: List<Int> = emptyList(),
        hasVibrator: Boolean = false,
    ): AndroidInputDeviceInfo = AndroidInputDeviceInfo(
        deviceId = id,
        name = name,
        sources = InputDevice.SOURCE_GAMEPAD,
        motionRangeAxes = axes,
        vibratorIds = vibratorIds,
        hasVibrator = hasVibrator,
    )

    /** Le routage d'un port seul : le foreground-active du snapshot lifecycle P1, tel quel. */
    private fun routing(active: Boolean = true) = GamepadPortRouting(
        policy = GamepadRouting.AllForegroundSessions,
        foregroundActive = active,
        effectOwnership = DeviceEffectOwnership.ExclusivePerPhysicalDevice,
    )

    /** La table kadre-old des boutons portée — l'ordre de la table old, les noms du modèle. */
    private val standardButtons = listOf(
        GamepadButton.South, GamepadButton.East, GamepadButton.West, GamepadButton.North,
        GamepadButton.LeftShoulder, GamepadButton.RightShoulder,
        GamepadButton.LeftTrigger, GamepadButton.RightTrigger,
        GamepadButton.LeftStick, GamepadButton.RightStick,
        GamepadButton.Start, GamepadButton.Select, GamepadButton.Mode,
        GamepadButton.DpadUp, GamepadButton.DpadDown, GamepadButton.DpadLeft, GamepadButton.DpadRight,
    )

    // -----------------------------------------------------------------------
    // Cas 3 — l'inventaire : motion ranges + tables kadre-old portées, mapping Standard
    // -----------------------------------------------------------------------

    @Test
    fun inventoryFromMotionRangesAndMappingTables() {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(7, axes = listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y))
        val port = AndroidGamepadPort(source, apiLevel = 31)

        val pads = port.gamepads
        assertEquals(1, pads.size, "the initial enumeration is the inventory, never an event")
        val pad = pads.single()
        assertEquals(7L, pad.key, "the deviceId is the correlation key, stable at the device's lifetime")
        assertEquals("Fake Pad", pad.descriptor.name)
        assertEquals(GamepadMapping.Standard, pad.descriptor.mapping, "the ported tables ARE the standard mapping")
        assertEquals(standardButtons, pad.descriptor.buttons, "the kadre-old button table, ported verbatim")
        assertEquals(
            listOf(GamepadAxis.LeftX, GamepadAxis.LeftY),
            pad.descriptor.axes,
            "the kadre-old axis table over the device's motion ranges",
        )
        // L'état initial est NEUTRE et matche le descripteur exactement (la validation du runtime).
        assertEquals(standardButtons, pad.state.buttons.map { it.button })
        assertTrue(pad.state.buttons.all { it.value == 0.0 && !it.pressed })
        assertEquals(listOf(0.0, 0.0), pad.state.axes.map { it.value })
    }

    /** Les axes hors table kadre-old restent absents du descripteur — la table est FERMÉE. */
    @Test
    fun axesOutsideThePortedTableStayAbsent() {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(
            7,
            axes = listOf(
                MotionEvent.AXIS_X,
                MotionEvent.AXIS_Y,
                MotionEvent.AXIS_RX,
                MotionEvent.AXIS_RY,
                MotionEvent.AXIS_HAT_X,
                MotionEvent.AXIS_HAT_Y,
                MotionEvent.AXIS_LTRIGGER,
                MotionEvent.AXIS_RTRIGGER,
                MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_PRESSURE, // hors table kadre-old — jamais inventé
                MotionEvent.AXIS_ORIENTATION,
            ),
        )
        val port = AndroidGamepadPort(source, apiLevel = 31)

        val axes = port.gamepads.single().descriptor.axes
        assertEquals(
            listOf(
                GamepadAxis.LeftX, GamepadAxis.LeftY,
                GamepadAxis.RightX, GamepadAxis.RightY,
                GamepadAxis.DpadX, GamepadAxis.DpadY,
                GamepadAxis.LeftTrigger, GamepadAxis.RightTrigger,
            ),
            axes,
            "Z/RZ are the alternate right-stick declaration: first-declared wins, the table stays closed",
        )
    }

    // -----------------------------------------------------------------------
    // Cas 4 — la déconnexion neutralise ; la suspension porte l'état neutre
    // -----------------------------------------------------------------------

    @Test
    fun disconnectNeutralizesState() {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(7)
        val port = AndroidGamepadPort(source, apiLevel = 31)
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        // L'ordre du runtime : l'observer d'abord, le routage ensuite — l'observer entend DONC
        // le RoutingChanged initial (le miroir du hub web : la projection passe de « sans
        // routage » à Routed, et chaque manette vivante en reçoit un).
        port.updateRouting(routing(active = true))
        val initial = assertIs<GamepadPortEvent.RoutingChanged>(events.single())
        assertEquals(GamepadRoutingState.Routed, initial.routing)
        assertTrue(initial.state.buttons.all { it.value == 0.0 && !it.pressed })

        // Une pression A : l'état de l'inventaire n'est plus neutre.
        port.onGamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_A, pressed = true, deviceId = 7)
        val changed = assertIs<GamepadPortEvent.StateChanged>(events[1])
        assertEquals(7L, changed.key)
        val south = changed.state.buttons.single { it.button == GamepadButton.South }
        assertEquals(1.0, south.value)
        assertTrue(south.pressed)

        // La suspension (le foreground P1 est parti) : RoutingChanged porte l'état NEUTRE —
        // la règle de neutralisation du runtime, tenue AUSSI côté port — tandis que la
        // lecture reste enregistrée (un resume trouve les valeurs que la manette lit maintenant).
        port.updateRouting(routing(active = false))
        val suspended = assertIs<GamepadPortEvent.RoutingChanged>(events[2])
        assertEquals(GamepadRoutingState.Suspended, suspended.routing)
        assertTrue(suspended.state.buttons.all { it.value == 0.0 && !it.pressed })
        assertTrue(suspended.state.axes.all { it.value == 0.0 })

        // Un événement pendant la suspension : enregistré, JAMAIS streamé (le miroir web —
        // l'état change ne rejoint que les ports routés).
        port.onGamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_B, pressed = true, deviceId = 7)
        assertEquals(3, events.size, "a suspended gamepad records the reading without streaming it")

        // Le retour au premier plan : RoutingChanged(Routed) avec la lecture FRAÎCHE.
        port.updateRouting(routing(active = true))
        val resumed = assertIs<GamepadPortEvent.RoutingChanged>(events[3])
        assertEquals(GamepadRoutingState.Routed, resumed.routing)
        assertEquals(1.0, resumed.state.buttons.single { it.button == GamepadButton.East }.value)

        // La déconnexion : Disconnected, l'inventaire est retiré — AVANT la livraison
        // (state before event : le snapshot et l'événement ne peuvent pas se contredire).
        source.devices.remove(7)
        source.fire(7)
        assertEquals(7L, assertIs<GamepadPortEvent.Disconnected>(events[4]).key)
        assertTrue(port.gamepads.isEmpty(), "a disconnected gamepad leaves the inventory")

        // La reconnexion EST une connexion neuve : boutons 0.0, axes 0.0 (la règle runtime —
        // jamais l'état mort de la connexion précédente).
        source.devices[7] = gamepad(7)
        source.fire(7)
        val reconnected = assertIs<GamepadPortEvent.Connected>(events[5])
        assertEquals(7L, reconnected.gamepad.key)
        assertTrue(reconnected.gamepad.state.buttons.all { it.value == 0.0 && !it.pressed })
        assertTrue(reconnected.gamepad.state.axes.all { it.value == 0.0 })
    }

    // -----------------------------------------------------------------------
    // Cas 5 — les effects par device RÉEL, gatés par l'API et le vibreur
    // -----------------------------------------------------------------------

    @Test
    fun effectsPerDeviceGatedByApiAndVibrator() {
        val source = FakeInputDeviceSource()
        // Trois devices : moteurs observés (31+), vibreur simple (26-30), SANS vibreur.
        source.devices[7] = gamepad(7, vibratorIds = listOf(0, 1))
        source.devices[8] = gamepad(8, hasVibrator = true)
        source.devices[9] = gamepad(9)
        val port = AndroidGamepadPort(source, apiLevel = 31)

        fun effectsOf(key: Long): Capability<GamepadEffectConstraints> =
            port.gamepads.single { it.key == key }.capabilities.effects

        // API 31+ avec des moteurs observés → Supported, DualRumble (le dual-rumble des
        // moteurs observés), maximumDuration null (aucune borne mesurée, le miroir web).
        val dual = assertIs<Capability.Supported<GamepadEffectConstraints>>(effectsOf(7L))
        assertEquals(setOf(GamepadEffectKind.DualRumble), dual.constraints.kinds)
        assertEquals(null, dual.constraints.maximumDuration)
        assertEquals(null, dual.constraints.localizedHaptics)
        assertEquals(FeatureAvailability.Available, dual.availability)

        // API 26-30 avec getVibrator → Supported limité (one-shot) : le même kind, le
        // chemin one-shot n'a qu'un moteur, le DualRumble y est l'unique primitive. La
        // lecture vit sur un port DONT l'apiLevel est dans la bande — le chemin est le fait
        // du port, jamais du device.
        val oneShotPort = AndroidGamepadPort(source, apiLevel = 28)
        val oneshot = assertIs<Capability.Supported<GamepadEffectConstraints>>(
            oneShotPort.gamepads.single { it.key == 8L }.capabilities.effects,
        )
        assertEquals(setOf(GamepadEffectKind.DualRumble), oneshot.constraints.kinds)

        // Sans vibreur → Unsupported épinglé (le fake ne donne AUCUN moteur à ce device),
        // aux DEUX bandes d'API.
        assertIs<Capability.Unsupported>(effectsOf(9L))
        assertIs<Capability.Unsupported>(oneShotPort.gamepads.single { it.key == 9L }.capabilities.effects)

        // startEffect cible le vibreur du device RÉEL : le fake enregistre l'id cible —
        // chaque départ porte l'id de SA manette, jamais un autre vibreur.
        val rumble = assertIs<KadreResult.Success<GamepadPortEffect>>(
            port.startEffect(7L, GamepadEffect.DualRumble(strong = 1.0, weak = 0.5, duration = 100.milliseconds)),
        )
        assertEquals(listOf(Triple(7, 100L, 1.0f to 0.5f)), source.rumbleStarts, "API 31+: the managed rumble path")
        assertTrue(source.oneShots.isEmpty())

        assertIs<KadreResult.Success<GamepadPortEffect>>(
            oneShotPort.startEffect(8L, GamepadEffect.DualRumble(strong = 1.0, weak = 0.5, duration = 100.milliseconds)),
        )
        assertEquals(
            listOf(Triple(8, 100L, 1.0f)),
            source.oneShots,
            "API 26-30: the one-shot createOneShot path, amplitude = the dominant magnitude",
        )

        // L'owner : un stop exactement une fois — le second requestStop, comme le close,
        // ne retouche plus le vibreur.
        rumble.value.requestStop()
        rumble.value.requestStop()
        rumble.value.close()
        assertEquals(listOf(7), source.cancels, "exactly one cancel per effect owner")
    }

    /** La capability est GELÉE à la connexion — le miroir `WebGamepadEffects` (:51). */
    @Test
    fun capabilityIsFrozenAtConnection() {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(7)
        val port = AndroidGamepadPort(source, apiLevel = 31)
        assertIs<Capability.Unsupported>(port.gamepads.single().capabilities.effects)

        // Les faits vibreur du device CHANGENT après la connexion : la capability ne suit pas
        // — une reconnexion gèlerait des faits frais, la connexion vivante garde les siens.
        source.devices[7] = gamepad(7, vibratorIds = listOf(0, 1))
        source.fire(7)
        assertIs<Capability.Unsupported>(port.gamepads.single().capabilities.effects)
    }

    /** Les refus honnêtes de `startEffect` — avant tout appel natif, jamais une exception. */
    @Test
    fun startEffectRefusals() {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(7, vibratorIds = listOf(0, 1))
        source.devices[9] = gamepad(9)
        val port = AndroidGamepadPort(source, apiLevel = 31)

        // Un kind jamais annoncé → InvalidRequest AVANT tout appel natif (le miroir web :
        // LocalizedHaptic n'a aucune primitive Android par device, jamais annoncé, jamais lancé).
        val haptic = port.startEffect(
            7L,
            GamepadEffect.LocalizedHaptic(GamepadHapticLocality.LeftHandle, 1.0, 50.milliseconds),
        )
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(haptic).reason)
        assertTrue(source.rumbleStarts.isEmpty(), "the refusal precedes every native call")

        // Un device sans moteurs → InvalidRequest (la capability gelée Unsupported).
        val muted = port.startEffect(9L, GamepadEffect.DualRumble(1.0, 1.0, 50.milliseconds))
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(muted).reason)
        assertTrue(source.rumbleStarts.isEmpty() && source.oneShots.isEmpty())

        // Une clé inconnue → Closed(Gamepad).
        val unknown = port.startEffect(42L, GamepadEffect.DualRumble(1.0, 1.0, 50.milliseconds))
        assertEquals(KadreFailure.Closed(KadreResourceKind.Gamepad), assertIs<KadreResult.Failure>(unknown).reason)

        // Un refus natif → la PlatformFailure honnête du domaine gamepad-effect.
        source.vibrationAnswer = false
        val refused = port.startEffect(7L, GamepadEffect.DualRumble(1.0, 1.0, 50.milliseconds))
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Android, "gamepad-effect", "refused"),
            assertIs<KadreResult.Failure>(refused).reason,
        )

        // Le close du port révoque les owners vivants, un startEffect d'après est Closed.
        port.close()
        val afterClose = port.startEffect(7L, GamepadEffect.DualRumble(1.0, 1.0, 50.milliseconds))
        assertEquals(KadreFailure.Closed(KadreResourceKind.Gamepad), assertIs<KadreResult.Failure>(afterClose).reason)
    }

    // -----------------------------------------------------------------------
    // Cas 6 (port) — l'état routé depuis la chaîne d'entrée
    // -----------------------------------------------------------------------

    @Test
    fun stateRoutedFromInputChain() {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(7, axes = listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X))
        val port = AndroidGamepadPort(source, apiLevel = 31)
        // Le routage AVANT l'observer (légal — la projection s'applique) : les événements du
        // test commencent net, sur les StateChanged du chemin d'entrée.
        port.updateRouting(routing(active = true))
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        // Un événement pour un device inconnu : ignoré — jamais de gamepad inventé.
        port.onGamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_A, pressed = true, deviceId = 42)
        assertEquals(0, events.size)
        assertTrue(port.gamepads.none { it.key == 42L })

        // Un keycode hors table : ignoré — aucun slot de descripteur à alimenter.
        port.onGamepadKeyEvent(KeyEvent.KEYCODE_A, pressed = true, deviceId = 7)
        assertEquals(0, events.size)

        // Une pression bouton → StateChanged, l'état de l'inventaire suit.
        port.onGamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_A, pressed = true, deviceId = 7)
        val pressed = assertIs<GamepadPortEvent.StateChanged>(events[0])
        assertEquals(1.0, pressed.state.buttons.single { it.button == GamepadButton.South }.value)

        // Une motion → StateChanged par lot : les axes de l'appel, canoniques.
        port.onGamepadMotion(
            axes = listOf(MotionEvent.AXIS_X to 0.5f, MotionEvent.AXIS_HAT_X to -1.5f),
            deviceId = 7,
        )
        val moved = assertIs<GamepadPortEvent.StateChanged>(events[1])
        assertEquals(0.5, moved.state.axes.single { it.axis == GamepadAxis.LeftX }.value)
        assertEquals(
            -1.0,
            moved.state.axes.single { it.axis == GamepadAxis.DpadX }.value,
            "an out-of-window reading clamps, never lies",
        )

        // Une valeur non finie : ignorée — aucune lecture inventée à sa place.
        port.onGamepadMotion(axes = listOf(MotionEvent.AXIS_X to Float.NaN), deviceId = 7)
        assertEquals(2, events.size, "a hostile reading produces no event")

        // Le relâchement revient au neutre pour CE bouton, les autres intouchés.
        port.onGamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_A, pressed = false, deviceId = 7)
        val released = assertIs<GamepadPortEvent.StateChanged>(events[2])
        assertEquals(0.0, released.state.buttons.single { it.button == GamepadButton.South }.value)
        assertEquals(0.5, released.state.axes.single { it.axis == GamepadAxis.LeftX }.value)

        // La lecture canonique ne se re-stream pas : le même reading re-produit zéro
        // événement (le diff du hub web contre l'état canonique, pas contre le brut).
        port.onGamepadMotion(axes = listOf(MotionEvent.AXIS_X to 0.5f), deviceId = 7)
        assertEquals(3, events.size)
    }

    /**
     * I-1 — le miroir EXACT de la discipline du port devices génériques : post-install, la
     * lecture de `gamepads` est EXACTEMENT ce que l'observateur sait. Une manette branchée
     * ENTRE DEUX FIRES (la table du seam mute sans que le fire du SDK soit livré) attend SON
     * Connected pour paraître — le snapshot ne devance jamais ses événements ; le fire
     * (ou le poll) suivant livre le Connected, et la manette paraît.
     */
    @Test
    fun snapshotNeverRunsAheadOfDeliveredEvents() {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(7)
        val port = AndroidGamepadPort(source, apiLevel = 31)

        // Avant l'install : la vérité vivante du seam — personne à qui courir devant.
        assertEquals(listOf(7L), port.gamepads.map { it.key })

        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }
        assertEquals(listOf(7L), port.gamepads.map { it.key }, "the install's enumeration is the observer's base")

        // La manette branchée entre deux fires : la table du fake mute, AUCUN fire —
        // le snapshot NE LA CONTIENT PAS encore.
        source.devices[8] = gamepad(8, name = "Late Pad")
        assertEquals(
            listOf(7L),
            port.gamepads.map { it.key },
            "a between-fires gamepad waits for its Connected — the snapshot never runs ahead",
        )

        // Le fire livre le Connected ; SEULEMENT ALORS la manette paraît dans la lecture.
        source.fire(8)
        val connected = assertIs<GamepadPortEvent.Connected>(events.single())
        assertEquals(8L, connected.gamepad.key)
        assertEquals("Late Pad", connected.gamepad.descriptor.name)
        assertEquals(listOf(7L, 8L), port.gamepads.map { it.key })
    }

    // -----------------------------------------------------------------------
    // Cas 6 (intégration P3) — les events source gamepad SORTENT du chemin ordinaire
    // -----------------------------------------------------------------------

    private var sequence = 0L

    /** La source de stamps de session — séquentielle, le miroir des autres tests hôtes. */
    private fun stampSource(): () -> EventStamp {
        val started = sequence
        return {
            sequence += 1
            EventStamp(SessionSequence(sequence), SessionInstant((sequence - started).nanoseconds), null)
        }
    }

    private fun surface(): AndroidHostSurface = AndroidHostSurface(
        id = RuntimeProcessIds.nextSurfaceId(),
        initialState = routingTestSurfaceState(),
        frameClock = { RoutingInertFrameClock },
        failureReporter = RuntimeFailureReporter { },
    )

    private fun install(on: AndroidHostSurface) {
        val discrete = EventDeliveryPolicy(
            ingressCapacity = 64,
            collectorCapacity = 16,
            ingressOverflow = IngressOverflowAction.CloseSource,
            collectorOverflow = CollectorOverflowAction.CancelSlowCollector,
        )
        on.installSessionConfiguration(
            deliveryPolicy = WindowDeliveryPolicy(
                discreteEvents = discrete,
                geometryChanges = ContinuousDelivery.Coalesced,
                redrawRequests = ContinuousDelivery.Latest,
            ),
            inputDeliveryPolicy = InputDeliveryPolicy(
                discreteEvents = discrete,
                pointerMotion = ContinuousDelivery.Coalesced,
                touchMotion = ContinuousDelivery.Coalesced,
                scroll = ContinuousDelivery.Coalesced,
                gestureChanges = ContinuousDelivery.Coalesced,
                gamepadChanges = ContinuousDelivery.Latest,
                rawInput = RawInputDeliveryPolicy(16, RawInputOverflowAction.DropOldestAndReport),
            ),
            source = stampSource(),
            sessionFailureHandler = {},
            collectorAllocator = RuntimeEventCollectorAllocator(64),
            maxCollectorsPerFlow = 4,
            resources = KadrePolicies.Default.resources,
            dropTransferScope = null,
            diagnostics = {},
            rawInputPort = null,
        )
    }

    /** Le collecteur d'événements d'entrée — le miroir exact d'[AndroidInputDeliveryTest]. */
    private class EventCollector(scope: CoroutineScope, input: SurfaceInput) {
        val channel = Channel<InputEvent>(Channel.UNLIMITED)
        private val job = scope.launch { input.events.collect { channel.send(it) } }

        suspend fun settle() {
            repeat(8) { yield() }
        }

        fun stop() {
            job.cancel()
        }

        suspend fun next(): InputEvent = withTimeout(5_000) { channel.receive() }

        /** Ce qui reste sans bloquer — le test décide ce qui est inattendu. */
        suspend fun drain(): List<InputEvent> = buildList {
            while (true) {
                val extra = channel.tryReceive().getOrNull() ?: break
                add(extra)
            }
        }
    }

    /**
     * LE SOUS-EXACT de la règle de routage : un event source SOURCE_GAMEPAD n'apparaît PLUS
     * comme `KeyChanged` dans `SurfaceInput.events` — il alimente l'état du port — tandis que
     * la MÊME touche source clavier suit le chemin ordinaire entière, et qu'une motion source
     * joystick n'ouvre plus de lane pointeur/tactile mais rend ses axes au port.
     */
    @Test
    fun gamepadSourceEventsLeaveTheOrdinaryKeyPath() = runBlocking {
        val source = FakeInputDeviceSource()
        source.devices[7] = gamepad(7, axes = listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y))
        val port = AndroidGamepadPort(source, apiLevel = 31)
        // Le routage AVANT l'observer : les événements du test commencent net — le premier
        // StateChanged du chemin d'entrée est le premier événement du test.
        port.updateRouting(routing(active = true))
        val padEvents = mutableListOf<GamepadPortEvent>()
        port.installObserver { padEvents += it }

        val surface = surface()
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        // Le route est attaché — la couture que l'attach réel (Task 3) câblera avec le port
        // construit pour la session ; ici le port de CE test, l'exact même seam.
        surface.attachGamepadRoute(port)
        val collector = EventCollector(this, surface.input)
        collector.settle()

        // Une touche DPAD source GAMEPAD : elle QUITTE le chemin ordinaire — aucun
        // KeyChanged n'entre dans SurfaceInput — et alimente l'état du port (DpadUp).
        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_DPAD_UP,
                repeat = false,
                metaState = 0,
                unicodeChar = null,
                isSystemKey = false,
                keyState = KeyState.Pressed,
                source = InputDevice.SOURCE_GAMEPAD,
                deviceId = 7,
            ),
        )
        assertEquals(0, collector.drain().size, "a gamepad-sourced key never lands as KeyChanged")
        val routed = assertIs<GamepadPortEvent.StateChanged>(padEvents.single())
        assertEquals(1.0, routed.state.buttons.single { it.button == GamepadButton.DpadUp }.value)

        // La MÊME touche source CLAVIER suit le chemin ordinaire, entière.
        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_DPAD_UP,
                repeat = false,
                metaState = 0,
                unicodeChar = null,
                isSystemKey = false,
                keyState = KeyState.Pressed,
                source = InputDevice.SOURCE_KEYBOARD,
                deviceId = 1,
            ),
        )
        val ordinary = assertIs<InputEvent.Key>(collector.next())
        assertEquals(NamedKey.ArrowUp, (ordinary.logicalKey as LogicalKey.Named).value)

        // Une motion source JOYSTICK : ses axes rejoignent le port, AUCUN stimulus
        // pointeur/tactile n'entre dans SurfaceInput.
        surface.onMotionObservation(
            AndroidMotionObservation(
                actionMasked = MotionEvent.ACTION_HOVER_MOVE,
                actionIndex = 0,
                pointerCount = 1,
                pointerIds = intArrayOf(0),
                xs = floatArrayOf(1f),
                ys = floatArrayOf(1f),
                pressures = arrayOf(null),
                toolTypes = intArrayOf(MotionEvent.TOOL_TYPE_UNKNOWN),
                buttons = 0,
                actionButton = 0,
                axisHscroll = 0f,
                axisVscroll = 0f,
                axisXrelative = 0f,
                axisYrelative = 0f,
                deviceId = 7,
                metaState = 0,
                source = InputDevice.SOURCE_JOYSTICK,
                gamepadAxes = listOf(MotionEvent.AXIS_X to 0.25f, MotionEvent.AXIS_Y to -0.75f),
            ),
            width = 100,
            height = 100,
        )
        assertEquals(0, collector.drain().size, "a joystick-sourced motion opens no pointer lane")
        val moved = assertIs<GamepadPortEvent.StateChanged>(padEvents[1])
        assertEquals(0.25, moved.state.axes.single { it.axis == GamepadAxis.LeftX }.value)
        assertEquals(-0.75, moved.state.axes.single { it.axis == GamepadAxis.LeftY }.value)
        assertEquals(1.0, moved.state.buttons.single { it.button == GamepadButton.DpadUp }.value)

        collector.stop()
    }

    /**
     * Le rayon d'action du changement, ÉPINGLÉ : sans port gamepad attaché, les events source
     * gamepad suivent le chemin ordinaire ENTIER — le comportement pré-existant (les events
     * gamepad-sourcés atterrissaient comme KeyChanged) ne change que PAR LA PRÉSENCE du port.
     */
    @Test
    fun withoutAGamepadPortTheOrdinaryPathStaysWhole() = runBlocking {
        val surface = surface()
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        val collector = EventCollector(this, surface.input)
        collector.settle()

        surface.onInputObservation(
            AndroidInputObservation.Key(
                nativeKeyCode = KeyEvent.KEYCODE_DPAD_UP,
                repeat = false,
                metaState = 0,
                unicodeChar = null,
                isSystemKey = false,
                keyState = KeyState.Pressed,
                source = InputDevice.SOURCE_GAMEPAD,
                deviceId = 7,
            ),
        )
        val ordinary = assertIs<InputEvent.Key>(collector.next())
        assertEquals(NamedKey.ArrowUp, (ordinary.logicalKey as LogicalKey.Named).value)
        collector.stop()
    }

}

/** L'état initial de surface du test — le même instantané honnête que les autres tests hôtes. */
private fun routingTestSurfaceState(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Focused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

/** La clock inerte de CE test : les frames y sont posées et n'y courent jamais (aucun SDK). */
private object RoutingInertFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
