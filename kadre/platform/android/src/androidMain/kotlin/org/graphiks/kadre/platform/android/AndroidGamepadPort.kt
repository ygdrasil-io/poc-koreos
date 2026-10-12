package org.graphiks.kadre.platform.android

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadAxisValue
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadButtonValue
import org.graphiks.kadre.input.GamepadCapabilities
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadEffectKind
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.internal.runtime.GamepadPort
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock

/**
 * Les tables kadre-old portées — la reconnaissance et la traduction des manettes Android. La
 * registration, le prédicat et les tables de `GamepadController.android.kt` (kadre-old) sont
 * réutilisés TELS QUELS ; ce qui n'était pas réutilisable (la file d'événements pollée, les
 * lectures bouton/axes à 0f, l'absence de rumble) est remplacé par le port d'état routé et les
 * effects par device réels.
 */
internal object AndroidGamepadMapping {
    /**
     * La table des BOUTONS — l'ordre exact de la table old (`nativeButtonCodes`), les noms du
     * modèle : L1/R1 (les gâchettes hautes) deviennent les épaules, L2/R2 (les gâchettes
     * analogiques) les triggers, THUMBL/R les sticks. C'est la disposition standard complète :
     * le SDK n'énumère pas les boutons d'un device (les motion ranges ne parlent que d'axes),
     * la table est donc LE descripteur boutons de toute manette — fermée, comme la table des
     * scroll-keys.
     */
    internal val buttonTable: List<Pair<Int, GamepadButton>> = listOf(
        KeyEvent.KEYCODE_BUTTON_A to GamepadButton.South,
        KeyEvent.KEYCODE_BUTTON_B to GamepadButton.East,
        KeyEvent.KEYCODE_BUTTON_X to GamepadButton.West,
        KeyEvent.KEYCODE_BUTTON_Y to GamepadButton.North,
        KeyEvent.KEYCODE_BUTTON_L1 to GamepadButton.LeftShoulder,
        KeyEvent.KEYCODE_BUTTON_R1 to GamepadButton.RightShoulder,
        KeyEvent.KEYCODE_BUTTON_L2 to GamepadButton.LeftTrigger,
        KeyEvent.KEYCODE_BUTTON_R2 to GamepadButton.RightTrigger,
        KeyEvent.KEYCODE_BUTTON_THUMBL to GamepadButton.LeftStick,
        KeyEvent.KEYCODE_BUTTON_THUMBR to GamepadButton.RightStick,
        KeyEvent.KEYCODE_BUTTON_START to GamepadButton.Start,
        KeyEvent.KEYCODE_BUTTON_SELECT to GamepadButton.Select,
        KeyEvent.KEYCODE_BUTTON_MODE to GamepadButton.Mode,
        KeyEvent.KEYCODE_DPAD_UP to GamepadButton.DpadUp,
        KeyEvent.KEYCODE_DPAD_DOWN to GamepadButton.DpadDown,
        KeyEvent.KEYCODE_DPAD_LEFT to GamepadButton.DpadLeft,
        KeyEvent.KEYCODE_DPAD_RIGHT to GamepadButton.DpadRight,
    )

    /** Le bouton d'un keycode, ou `null` hors table — un keycode hors table n'a pas de slot. */
    internal fun button(keycode: Int): GamepadButton? =
        buttonTable.firstOrNull { it.first == keycode }?.second

    /**
     * La table des AXES — la table old portée sur le modèle : X/Y le stick gauche, RX/RY le
     * stick droit (la table old), Z/RZ la DÉCLARATION ALTERNATIVE du stick droit (la
     * disposition Xbox-Android commune), HAT_X/HAT_Y la croix, LTRIGGER/RTRIGGER les triggers
     * analogiques. La table est FERMÉE (les dix axes de la table old ; PRESSURE, ORIENTATION,
     * GAS, BRAKE… restent dehors) : un axe hors table n'a pas d'identité du modèle.
     */
    internal fun axis(nativeAxis: Int): GamepadAxis? = when (nativeAxis) {
        MotionEvent.AXIS_X -> GamepadAxis.LeftX
        MotionEvent.AXIS_Y -> GamepadAxis.LeftY
        MotionEvent.AXIS_RX -> GamepadAxis.RightX
        MotionEvent.AXIS_RY -> GamepadAxis.RightY
        MotionEvent.AXIS_Z -> GamepadAxis.RightX
        MotionEvent.AXIS_RZ -> GamepadAxis.RightY
        MotionEvent.AXIS_HAT_X -> GamepadAxis.DpadX
        MotionEvent.AXIS_HAT_Y -> GamepadAxis.DpadY
        MotionEvent.AXIS_LTRIGGER -> GamepadAxis.LeftTrigger
        MotionEvent.AXIS_RTRIGGER -> GamepadAxis.RightTrigger
        else -> null
    }

    /**
     * Les dix axes de la table, dans l'ordre de déclaration — lus AU POINT DE DISPATCH par le
     * wrapper pour toute motion source gamepad/joystick (`MotionEvent.getAxisValue` par axe,
     * avant que le système ne recycle l'événement).
     */
    internal val nativeAxes: List<Int> = listOf(
        MotionEvent.AXIS_X,
        MotionEvent.AXIS_Y,
        MotionEvent.AXIS_Z,
        MotionEvent.AXIS_RX,
        MotionEvent.AXIS_RY,
        MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_HAT_X,
        MotionEvent.AXIS_HAT_Y,
        MotionEvent.AXIS_LTRIGGER,
        MotionEvent.AXIS_RTRIGGER,
    )
}

/**
 * Le prédicat manette — la FORME de kadre-old (`GamepadController.android.kt:38-39` : les
 * deux flags source, masqués, comparés à zéro) sur les bits d'IDENTITÉ, les bits de CLASSE
 * retirés ([InputDevice.SOURCE_CLASS_MASK]) : `SOURCE_GAMEPAD` (0x401) et `SOURCE_KEYBOARD`
 * (0x101) partagent le bit `SOURCE_CLASS_BUTTON` (0x1) — le masque littéral de la table old
 * sur les sources brutes connecterait chaque clavier comme manette. Une manette réelle
 * déclare toujours `SOURCE_GAMEPAD|SOURCE_JOYSTICK` parmi ses sources : le prédicat la voit.
 */
internal fun isGamepadDevice(sources: Int): Boolean {
    val identityBits = sources and InputDevice.SOURCE_CLASS_MASK.inv()
    val gamepadFlags = (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) and InputDevice.SOURCE_CLASS_MASK.inv()
    return identityBits and gamepadFlags != 0
}

/**
 * Les règles d'effects Android — le miroir de `WebGamepadEffects` (:66) : ce que les moteurs
 * D'UNE MANETTE donnée peuvent promettre, gelé à la connexion. Le vibreur du téléphone n'est
 * JAMAIS la manette d'un jeu : la capacité naît des faits vibreur du device RÉEL, lus par le
 * seam ([AndroidInputDeviceInfo]) — des moteurs observables (API 31+, le VibratorManager du
 * device, le chemin du dual-rumble) ou un vibreur simple (API 26-30, le chemin one-shot), et
 * SANS RIEN → `Unsupported(GamepadEffect)` épinglé. La primitive Android est le one-shot : les
 * magnitudes fortes/faibles n'ont pas de moteur nommé par rôle (limite enregistrée du SDK), le
 * kind offert est donc DualRumble seule — LocalizedHaptic et TriggerRumble n'ont aucune
 * primitive par device, jamais annoncés, jamais lancés. `maximumDuration` reste `null` : la
 * plateforme borne, la contrainte n'enregistre aucune limite non mesurée.
 */
internal object AndroidGamepadEffects {
    internal fun capabilities(info: AndroidInputDeviceInfo, apiLevel: Int): GamepadCapabilities {
        val unsupported = GamepadCapabilities(Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect)))
        if (apiLevel < 26) return unsupported
        val observable = if (apiLevel >= 31) info.vibratorIds.isNotEmpty() else info.hasVibrator
        if (!observable) return unsupported
        return GamepadCapabilities(
            Capability.Supported(
                GamepadEffectConstraints(
                    kinds = setOf(GamepadEffectKind.DualRumble),
                    localizedHaptics = null,
                    maximumDuration = null,
                ),
                FeatureAvailability.Available,
            ),
        )
    }
}

/**
 * Le port gamepad Android : l'inventaire des manettes du multi-cast [InputManager], l'état
 * routé depuis la chaîne d'entrée P3, et les effects par device réels.
 *
 * **L'inventaire** — le prédicat kadre-old (`isGamepadDevice`) sur chaque device de
 * `getDeviceIds()` ; la clé de corrélation est le deviceId (stable à vie du device). Le
 * descripteur est GELÉ à la connexion (le miroir du hub web) : la table old des boutons, les
 * axes de `getMotionRanges()` lus par la table old portée (premier déclaré gagne quand deux
 * axes natifs parlent au même axe du modèle — Z et RX sont deux déclarations du même stick
 * droit), `mapping == Standard`. La capability d'effects est gelée à la MÊME connexion (le
 * miroir `WebGamepadEffects` :51) ; la RECONNEXION est une connexion neuve, qui gèle des faits
 * frais et repart à l'état neutre — jamais l'état mort de la connexion précédente (la règle de
 * neutralisation du runtime, tenue aussi côté port).
 *
 * **L'état routé** — la couture de la phase 7 : les events source gamepad/joystick SORTENT du
 * chemin ordinaire `KeyChanged` ([AndroidInputDelivery] consulte ce port D'ABORD) et
 * alimentent [onGamepadKeyEvent]/[onGamepadMotion] — le modèle web : l'état gamepad n'entre
 * JAMAIS dans SurfaceInput, il vit session-scoped dans ce port. La lecture est enregistrée
 * quel que soit le routage (un resume trouve les valeurs que la manette lit maintenant) ;
 * l'événement `StateChanged` ne rejoint que le port routé (le miroir web) ; le diff est contre
 * l'état canonique (pas de no-op streamé, pas de lecture non finie, clamp honnête). Le routage
 * est le foreground-active du snapshot lifecycle P1, porté par [updateRouting] — la
 * suspension projette l'état NEUTRE, le retour au premier plan livre la lecture fraîche.
 *
 * **Les effects** — par device RÉEL : [startEffect] cible les moteurs du device nommé par la
 * clé, jamais un autre vibreur (jamais celui du téléphone) ; le chemin suit l'[apiLevel] du
 * port (31+ le rumble managé du VibratorManager du device, 26-30 le one-shot createOneShot du
 * vibreur du device) ; un refus natif est la `PlatformFailure("gamepad-effect", "refused")`
 * honnête, jamais une exception (le contrat du port runtime). Le close du port révoque les
 * owners vivants — un effect ne survit pas à la session qui l'a demandé.
 *
 * Le port est scoped à la session : le runtime installe exactement un observer et ferme le port
 * avec la session. Le seam est PARTAGÉ avec [AndroidInputDevicePort] — le port ne le ferme
 * JAMAIS, chaque port ne désenregistre que son listener.
 *
 * @param deviceSource le seam InputManager que ce port lit ; partagé, jamais fermé par le port.
 * @param apiLevel le niveau d'API de la plateforme — l'autorité du chemin d'effects du port.
 */
internal class AndroidGamepadPort(
    private val deviceSource: AndroidInputDeviceSource,
    private val apiLevel: Int,
) : GamepadPort, AndroidGamepadRoute {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var observer: ((GamepadPortEvent) -> Unit)? = null
    private var listener: ((Int) -> Unit)? = null

    /** Le routage PROJETÉ — le foreground-active du snapshot lifecycle P1, tel quel : le port
     * reçoit le routage de SA session (la policy y est déjà résolue par le runtime). */
    private var routed: Boolean = false

    /** Une manette connectée : le descripteur et la capability GELÉS, la lecture vivante. */
    private class Pad(
        val descriptor: GamepadDescriptor,
        val capabilities: GamepadCapabilities,
        var lastState: GamepadState,
    )

    private val pads = linkedMapOf<Long, Pad>()

    /** Les clés que l'observateur a DÉJÀ reçues — la base du diff des fires (l'install la sème). */
    private val deliveredKeys = linkedSetOf<Long>()

    private val activeEffects = linkedSetOf<AndroidGamepadEffect>()

    override val gamepads: List<GamepadPortGamepad>
        get() = lock.withLock {
            syncLocked()
            pads.map { (key, pad) -> projectionLocked(key, pad) }
        }

    override fun installObserver(observer: (GamepadPortEvent) -> Unit): AutoCloseable = lock.withLock {
        check(!closed) { "the android gamepad port is closed" }
        check(this.observer == null) { "the android gamepad port observer is already installed" }
        check(listener == null) { "the android gamepad port is already subscribed to its source" }
        this.observer = observer
        // L'énumération d'install est l'inventaire initial — JAMAIS un événement : la lecture
        // de `gamepads` par le runtime EST la connexion (le dédoublonnage est le contrat).
        syncLocked()
        deliveredKeys.clear()
        deliveredKeys.addAll(pads.keys)
        val sdk: (Int) -> Unit = { onSourceFire(it) }
        listener = sdk
        deviceSource.registerListener(sdk)
        AutoCloseable {
            val live = lock.withLock {
                if (closed) return@withLock null
                if (this.listener === sdk) this.listener = null
                if (this.observer === observer) this.observer = null
                sdk
            }
            if (live != null) deviceSource.unregisterListener(live)
        }
    }

    override fun updateRouting(routing: GamepadPortRouting) {
        val outcome = lock.withLock {
            if (closed) return@withLock null
            val next = routing.foregroundActive
            if (next == routed) return@withLock null
            routed = next
            val deliver = observer ?: return@withLock null
            val projected = if (next) GamepadRoutingState.Routed else GamepadRoutingState.Suspended
            val events = pads.map { (key, pad) ->
                GamepadPortEvent.RoutingChanged(key, projected, stateForLocked(projected, pad))
            }
            deliver to events
        } ?: return
        outcome.second.forEach { event -> outcome.first(event) }
    }

    /** Un fire du seam (added/removed/changed) : la table se resynchronise, le diff parle. */
    private fun onSourceFire(deviceId: Int) {
        val outcome = lock.withLock {
            val deliver = observer ?: return@withLock null
            if (closed) return@withLock null
            syncLocked()
            val events = buildList {
                for (key in pads.keys) {
                    if (key !in deliveredKeys) {
                        deliveredKeys.add(key)
                        add(GamepadPortEvent.Connected(projectionLocked(key, checkNotNull(pads[key]))))
                    }
                }
                for (key in deliveredKeys.toList()) {
                    if (key !in pads.keys) {
                        deliveredKeys.remove(key)
                        add(GamepadPortEvent.Disconnected(key))
                    }
                }
            }
            deliver to events
        } ?: return
        // La livraison HORS du lock — la table était à jour avant le premier événement.
        outcome.second.forEach { event -> outcome.first(event) }
    }

    /** La projection d'une manette à travers le routage : sa lecture, ou le neutre. */
    private fun projectionLocked(key: Long, pad: Pad): GamepadPortGamepad = GamepadPortGamepad(
        key = key,
        descriptor = pad.descriptor,
        state = stateForLocked(if (routed) GamepadRoutingState.Routed else GamepadRoutingState.Suspended, pad),
        routing = if (routed) GamepadRoutingState.Routed else GamepadRoutingState.Suspended,
        capabilities = pad.capabilities,
    )

    /** Le routage projeté publie la lecture enregistrée ; le suspendu, le neutre, toujours. */
    private fun stateForLocked(routing: GamepadRoutingState, pad: Pad): GamepadState =
        if (routing == GamepadRoutingState.Routed) pad.lastState else neutralState(pad.descriptor)

    /**
     * Alimente l'état depuis la chaîne d'input P3 (source gamepad/joystick filtrée) — le
     * keycode du dispatch d'un bouton. Un keycode hors table n'a pas de slot, un device
     * inconnu n'a pas de manette : les deux tombent en silence, jamais de manette inventée.
     */
    override public fun onGamepadKeyEvent(nativeKeyCode: Int, pressed: Boolean, deviceId: Int) {
        val button = AndroidGamepadMapping.button(nativeKeyCode) ?: return
        val outcome = lock.withLock {
            val pad = pads[deviceId.toLong()] ?: return@withLock null
            if (closed) return@withLock null
            val next = pad.lastState.withButton(button, pressed)
            if (next == pad.lastState) return@withLock null
            // Enregistré QUEL QUE SOIT le routage — un resume trouve la lecture vivante.
            pad.lastState = next
            val deliver = observer ?: return@withLock null
            if (!routed) return@withLock null
            deliver to listOf(GamepadPortEvent.StateChanged(deviceId.toLong(), next))
        } ?: return
        outcome.second.forEach { event -> outcome.first(event) }
    }

    /** La motion du dispatch joystick : les axes natifs lus au point de dispatch, canoniques. */
    override public fun onGamepadMotion(axes: List<Pair<Int, Float>>, deviceId: Int) {
        val outcome = lock.withLock {
            val pad = pads[deviceId.toLong()] ?: return@withLock null
            if (closed) return@withLock null
            var next = pad.lastState
            for ((nativeAxis, value) in axes) {
                val axis = AndroidGamepadMapping.axis(nativeAxis) ?: continue
                if (!value.isFinite()) continue // une lecture hostile n'invente rien à sa place
                next = next.withAxis(axis, value)
            }
            if (next == pad.lastState) return@withLock null // le diff est contre le canonique
            pad.lastState = next
            val deliver = observer ?: return@withLock null
            if (!routed) return@withLock null
            deliver to listOf(GamepadPortEvent.StateChanged(deviceId.toLong(), next))
        } ?: return
        outcome.second.forEach { event -> outcome.first(event) }
    }

    /**
     * Démarre un effet DÉJÀ ADMIS par le runtime pour la manette que [key] nomme — sur les
     * moteurs de CE device, jamais un autre vibreur. Les échecs natifs sont mappés, jamais
     * lancés : clé inconnue ou port fermé → `Closed(Gamepad)`, kind jamais annoncé ou device
     * sans moteur → `InvalidRequest("effect")`, refus plateforme → `PlatformFailure`. Le
     * chemin suit l'[apiLevel] du port : 31+ le rumble managé (le VibratorManager du device,
     * ses moteurs observables), 26-30 le one-shot createOneShot (amplitude = la magnitude
     * dominante — un seul moteur n'a pas de rôles).
     */
    override fun startEffect(key: Long, effect: GamepadEffect): KadreResult<GamepadPortEffect> = lock.withLock {
        if (closed) return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        // La table est la vérité vivante : un départ lit l'énumération courante, comme toute
        // lecture du port (l'install et les fires la maintiennent déjà, la défense est pour
        // le port qu'aucun observateur n'a jamais lu).
        syncLocked()
        val pad = pads[key] ?: return@withLock KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Gamepad))
        val rumble = effect as? GamepadEffect.DualRumble
            ?: return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
        if (pad.capabilities.effects is Capability.Unsupported) {
            return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("effect"))
        }
        val deviceId = key.toInt()
        val durationMs = rumble.duration.inWholeMilliseconds.coerceIn(1L, Int.MAX_VALUE.toLong())
        val started = if (apiLevel >= 31) {
            deviceSource.startManagedRumble(deviceId, durationMs, rumble.strong.toFloat(), rumble.weak.toFloat())
        } else {
            deviceSource.startOneShot(deviceId, durationMs, rumble.strong.coerceAtLeast(rumble.weak).toFloat())
        }
        if (!started) {
            return@withLock KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.Android, "gamepad-effect", "refused"))
        }
        // Le seul owner que ce port construit ; enregistré pour la révocation du close.
        val owner = AndroidGamepadEffect(deviceSource, deviceId)
        activeEffects.add(owner)
        KadreResult.Success(owner)
    }

    /**
     * Désenregistre le listener du seam vivant, révoque les owners vivants (un effect ne
     * survit pas à la session), oublie l'observer. Idempotent. Le seam partagé n'est PAS
     * fermé : c'est le tour de son propriétaire.
     */
    override fun close() {
        val outcome = lock.withLock {
            if (closed) return@withLock null
            closed = true
            val liveListener = listener
            listener = null
            observer = null
            val owners = activeEffects.toList()
            activeEffects.clear()
            liveListener to owners
        } ?: return
        if (outcome.first != null) deviceSource.unregisterListener(outcome.first!!)
        // La révocation — le même un-arrêt-par-owner que le hub web, le verdict de personne.
        outcome.second.forEach(AndroidGamepadEffect::revoke)
    }

    /** L'énumération COMPLETE re-filtrée — l'ajout et le retrait silencieux des manettes. */
    private fun syncLocked() {
        val ids = deviceSource.deviceIds()
        for (id in ids) {
            val key = id.toLong()
            if (pads.containsKey(key)) continue
            val info = deviceSource.deviceInfo(id) ?: continue
            if (!isGamepadDevice(info.sources)) continue
            pads[key] = connectedPad(info)
        }
        pads.keys.retainAll(ids.map { it.toLong() }.toSet())
    }

    /** Une connexion NEUVE : descripteur et capability gelés ici, état neutre (la règle runtime). */
    private fun connectedPad(info: AndroidInputDeviceInfo): Pad {
        val axes = linkedSetOf<GamepadAxis>()
        for (native in info.motionRangeAxes) AndroidGamepadMapping.axis(native)?.let(axes::add)
        val descriptor = GamepadDescriptor(
            name = info.name,
            mapping = GamepadMapping.Standard,
            buttons = AndroidGamepadMapping.buttonTable.map { it.second },
            axes = axes.toList(),
        )
        return Pad(descriptor, AndroidGamepadEffects.capabilities(info, apiLevel), neutralState(descriptor))
    }

    private fun neutralState(descriptor: GamepadDescriptor): GamepadState = GamepadState(
        buttons = descriptor.buttons.map { button -> GamepadButtonValue(button, 0.0, false) },
        axes = descriptor.axes.map { axis -> GamepadAxisValue(axis, 0.0) },
    )

    private fun GamepadState.withButton(button: GamepadButton, pressed: Boolean): GamepadState = GamepadState(
        buttons = buttons.map { current ->
            if (current.button == button) GamepadButtonValue(button, if (pressed) 1.0 else 0.0, pressed) else current
        },
        axes = axes,
    )

    private fun GamepadState.withAxis(axis: GamepadAxis, value: Float): GamepadState = GamepadState(
        buttons = buttons,
        axes = axes.map { current ->
            if (current.axis == axis) GamepadAxisValue(axis, value.coerceIn(-1f, 1f).toDouble()) else current
        },
    )
}

/**
 * Un effet lancé — le miroir de `WebGamepadEffect` : le départ a déjà eu lieu (le vibreur du
 * device a répondu) ; ce que l'owner doit au runtime est un arrêt, exactement une fois — le
 * second `requestStop`, comme le `close` et la révocation du port, ne retouche plus le
 * vibreur. La vibration s'arrête d'elle-même à sa durée : un arrêt tardif est un cancel d'un
 * moteur déjà silencieux — inoffensif, rapporté nulle part (la limite enregistrée du web, la
 * même).
 */
internal class AndroidGamepadEffect(
    private val deviceSource: AndroidInputDeviceSource,
    private val deviceId: Int,
) : GamepadPortEffect {
    private var stopped: Boolean = false

    override fun requestStop(): KadreResult<Unit> {
        if (stopped) return KadreResult.Success(Unit)
        stopped = true
        deviceSource.cancelVibration(deviceId)
        return KadreResult.Success(Unit)
    }

    /** Arrête l'effet une fois ; le second close, comme toute révocation tardive, ne touche plus rien. */
    override fun close() {
        requestStop()
    }

    /** La révocation du port fermé — le même un-arrêt que [requestStop], sans verdict lu. */
    internal fun revoke() {
        requestStop()
    }
}
