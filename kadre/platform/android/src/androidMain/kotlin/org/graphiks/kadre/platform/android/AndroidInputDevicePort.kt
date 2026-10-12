package org.graphiks.kadre.platform.android

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import kotlin.math.roundToInt
import org.graphiks.kadre.input.InputDeviceDescriptor
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.InputDevicePort
import org.graphiks.kadre.internal.runtime.InputDevicePortDevice
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock

/**
 * Le seam SDK des périphériques d'entrée — le vrai [InputManager] en production, un fake
 * pilotable en test hôte (le stub android.jar lance à la construction ; le port ne nomme JAMAIS
 * un type SDK, c'est la traduction de ce seam en formes port qui est prouvée, pas les lectures
 * SDK elles-mêmes, vérifiées sur émulateur — le miroir exact d'[AndroidDisplaySource]).
 *
 * Le seam est PARTAGÉ par les deux ports de la Task 2 — [AndroidInputDevicePort] (les devices
 * génériques) et [AndroidGamepadPort] (les manettes : le même InputManager, la même
 * énumération, le même listener multi-cast) — c'est pourquoi AUCUN des deux ports ne le ferme :
 * `close` appartient au propriétaire du seam (la couture d'attach, la tâche suivante). Chaque
 * port ne désenregistre que SON listener.
 */
internal interface AndroidInputDeviceSource : AutoCloseable {
    /**
     * Les ids COMPLETS des périphériques connus du système, au moment de l'appel — le miroir
     * d'`InputDevice.getDeviceIds()`. Les ports relisent cette énumération (à l'install et à
     * chaque fire du listener) — jamais un delta mémorisé.
     */
    public fun deviceIds(): List<Int>

    /** La lecture SDK brute d'un device — les faits, pas leur interprétation ; `null` si l'id a disparu. */
    public fun deviceInfo(deviceId: Int): AndroidInputDeviceInfo?

    /**
     * Enregistre [listener] pour chaque moment où l'InputManager signale un device ajouté,
     * retiré ou changé (le listener MULTI-CAST du SDK, `onInputDeviceAdded/Removed/Changed`,
     * contracté en UNE callback portant l'id). L'id est informationnel : les ports relisent
     * l'énumération complète, ils ne consomment jamais l'événement comme un delta.
     */
    public fun registerListener(listener: (Int) -> Unit)

    /** Retire exactement l'instance [listener] donnée à [registerListener]. */
    public fun unregisterListener(listener: (Int) -> Unit)

    /**
     * Le dual-rumble du device RÉEL — le chemin API 31+ du [AndroidGamepadPort] : les moteurs
     * observables du device (les ids de son VibratorManager). Ne cible JAMAIS un autre
     * vibreur (jamais celui du téléphone). Répond `false` quand la plateforme refuse.
     */
    public fun startManagedRumble(deviceId: Int, durationMs: Long, strongMagnitude: Float, weakMagnitude: Float): Boolean

    /**
     * Le one-shot du vibreur du device RÉEL — le chemin API 26-30 : `VibrationEffect.
     * createOneShot` sur le vibreur du device, [amplitude] dans 0..1 (0 = l'amplitude par
     * défaut de la plateforme). Ne cible JAMAIS un autre vibreur.
     */
    public fun startOneShot(deviceId: Int, durationMs: Long, amplitude: Float): Boolean

    /** L'arrêt de la vibration du device — idempotent, jamais lancé hors du device nommé. */
    public fun cancelVibration(deviceId: Int)
}

/**
 * Les faits SDK d'un périphérique d'entrée, traduits sans interprétation par la réalisation du
 * seam. Le descripteur et le kind se DÉCIDENT côté ports (le carve-out gamepad du port
 * générique, les tables kadre-old du port gamepad) — le seam ne porte que les faits bruts.
 */
internal data class AndroidInputDeviceInfo(
    /** Le deviceId du SDK — stable à vie du device ; la clé de corrélation des deux ports. */
    val deviceId: Int,
    /** Le nom du SDK, ou null quand il n'en rapporte pas — jamais fabriqué. */
    val name: String?,
    /** Le bitmask `getSources()` brut — le kind générique ET le prédicat manette le lisent. */
    val sources: Int,
    /** Les ids d'axes de `getMotionRanges()`, dans l'ordre de déclaration du device. */
    val motionRangeAxes: List<Int>,
    /** Les moteurs observables du device — `getVibratorManager().getVibratorIds()` (API 31+). */
    val vibratorIds: List<Int>,
    /** Le vibreur simple du device — `getVibrator().hasVibrator()` (le chemin API 26-30). */
    val hasVibrator: Boolean,
)

/**
 * Le port des périphériques d'entrée GÉNÉRIQUES Android : l'inventaire complet du multi-cast
 * [InputManager] — claviers, souris, écrans tactiles, touchpads, stylets, et TOUT le reste en
 * `Other`. Le carve-out de la règle : un device porteur des sources gamepad/joystick est
 * `Other` ICI — la manette est un `Gamepad` côté [AndroidGamepadPort], jamais un device
 * générique typé ; le kind générique ne vol pas l'identité que l'autre port porte, même quand
 * une manette réelle déclare aussi des sources clavier.
 *
 * L'ordre des phases est celui du miroir display : l'énumération initiale (`getDeviceIds()`)
 * est l'INVENTAIRE que `devices` rend — jamais un événement ; le port ne s'abonne au seam QU'À
 * L'INSTALL, donc rien ne précède jamais son observateur ; chaque fire du listener relit
 * l'énumération complète et DIFFE contre ce que l'observateur a déjà reçu (Connected pour un id
 * neuf, Disconnected pour un id disparu — le `onInputDeviceChanged` du SDK n'est PAS une
 * connexion : il resynchronise la table sans événement). La table est mise à jour AVANT la
 * livraison (state before event — le snapshot et l'événement à côté ne peuvent pas se
 * contredire). Une lecture d'avant l'install rend la vérité vivante du seam ; une lecture
 * d'après l'install rend exactement ce que l'observateur sait (les fires le tiennent à jour).
 *
 * Les clés de corrélation sont les deviceIds — stables à vie du device ; le runtime alloue ses
 * identités publiques par-dessus.
 *
 * Le port est scoped à la session : le runtime installe exactement un observer et ferme le port
 * avec la session, ce qui désenregistre le listener du seam — et le seam seul, JAMAIS fermé par
 * le port (il est partagé avec [AndroidGamepadPort]).
 *
 * @param source le seam InputManager que ce port lit ; partagé, jamais fermé par le port.
 */
internal class AndroidInputDevicePort(private val source: AndroidInputDeviceSource) : InputDevicePort {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var observer: ((InputDevicePortEvent) -> Unit)? = null
    private var listener: ((Int) -> Unit)? = null

    /** La table complète re-traduite du seam — la vérité vivante, RE-BÂTIE à chaque relecture
     * (le miroir display : jamais un delta mémorisé ; un fire « changed » rafraîchit les faits
     * d'un device SANS événement — le port n'a que Connected/Disconnected, la lecture suivante
     * porte le descripteur neuf). */
    private val table = linkedMapOf<Int, InputDevicePortDevice>()

    /** Les ids que l'observateur a DÉJÀ reçus — la base du diff des fires (l'install la sème). */
    private val deliveredKeys = linkedSetOf<Int>()

    override val devices: List<InputDevicePortDevice>
        get() = lock.withLock {
            if (observer == null) {
                // Avant l'install (ou après le close) : la vérité vivante du seam, sans base
                // de diff — une lecture n'est jamais une observation.
                syncLocked()
                table.values.toList()
            } else {
                // Après l'install : exactement ce que l'observateur sait — un id ajouté entre
                // deux fires attend SON Connected pour apparaître ; les fires le tiennent à
                // jour, la lecture ne devance jamais ses événements.
                deliveredKeys.mapNotNull { table[it] }
            }
        }

    /**
     * Installe l'observer session-runtime et ENREGISTRE le listener du seam — c'est ici, jamais
     * avant, que le port commence à recevoir les fires. L'énumération courante devient la base
     * du diff (l'inventaire initial est la lecture de `devices`, jamais un événement — le
     * runtime dédup son initial read, le miroir du hub web). Un observer à la fois : le runtime
     * en installe exactement un, un second install est une erreur de programmation.
     */
    override fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable =
        lock.withLock {
            check(!closed) { "the android input-device port is closed" }
            check(this.observer == null) { "the android input-device port observer is already installed" }
            check(listener == null) { "the android input-device port is already subscribed to its source" }
            this.observer = observer
            syncLocked()
            deliveredKeys.clear()
            deliveredKeys.addAll(table.keys)
            val sdk: (Int) -> Unit = { onSourceFire(it) }
            listener = sdk
            source.registerListener(sdk)
            AutoCloseable {
                val live = lock.withLock {
                    if (closed) return@withLock null
                    if (this.listener === sdk) this.listener = null
                    if (this.observer === observer) this.observer = null
                    sdk
                }
                if (live != null) source.unregisterListener(live)
            }
        }

    /**
     * Désenregistre le listener du seam vivant et oublie l'observer. Idempotent. Le seam
     * partagé n'est PAS fermé : c'est le tour de son propriétaire.
     */
    override fun close() {
        val live = lock.withLock {
            if (closed) return@withLock null
            closed = true
            val liveListener = listener
            listener = null
            observer = null
            liveListener
        }
        if (live != null) source.unregisterListener(live)
    }

    /** Un fire du seam (added/removed/changed) : la table se RE-BÂTIT, le diff parle. */
    private fun onSourceFire(deviceId: Int) {
        val outcome = lock.withLock {
            val deliver = observer ?: return@withLock null
            if (closed) return@withLock null
            syncLocked()
            val events = buildList {
                for (id in table.keys) {
                    if (id !in deliveredKeys) {
                        deliveredKeys.add(id)
                        add(InputDevicePortEvent.Connected(checkNotNull(table[id])))
                    }
                }
                for (id in deliveredKeys.toList()) {
                    if (id !in table.keys) {
                        deliveredKeys.remove(id)
                        add(InputDevicePortEvent.Disconnected(id.toLong()))
                    }
                }
            }
            deliver to events
        } ?: return
        // La livraison HORS du lock — la table était à jour avant le premier événement.
        outcome.second.forEach(outcome.first)
    }

    /** L'énumération COMPLETE re-traduite depuis le seam — le miroir display, sans delta. */
    private fun syncLocked() {
        table.clear()
        for (id in source.deviceIds()) {
            translated(id)?.let { table[id] = it }
        }
    }

    /** La traduction d'un device : la clé = le deviceId, le kind = le carve-out de la règle. */
    private fun translated(deviceId: Int): InputDevicePortDevice? {
        val info = source.deviceInfo(deviceId) ?: return null
        return InputDevicePortDevice(
            key = info.deviceId.toLong(),
            descriptor = InputDeviceDescriptor(name = info.name, kind = genericDeviceKind(info.sources)),
        )
    }
}

/**
 * Le kind GÉNÉRIQUE d'un device depuis son bitmask `sources` — l'ordre de la règle :
 * Keyboard, Mouse, Touchscreen, Touchpad, Pen, sinon Other. Chaque flag est comparé sur ses
 * bits d'IDENTITÉ (les bits de CLASSE retirés — `SOURCE_TOUCHSCREEN` (0x1002) et `SOURCE_MOUSE`
 * (0x2002) partagent `SOURCE_CLASS_POINTER` (0x2), un test brut flaggerait chaque écran
 * tactile comme souris). Le carve-out D'ABORD : un device porteur des sources gamepad/joystick
 * est `Other` — la manette est un `Gamepad` du port gamepad, et le port générique ne la
 * retape pas en clavier quand elle déclare aussi des sources clavier (des manettes réelles le
 * font).
 */
internal fun genericDeviceKind(sources: Int): InputDeviceKind {
    val identityBits = sources and InputDevice.SOURCE_CLASS_MASK.inv()
    return when {
        isGamepadSource(sources) -> InputDeviceKind.Other
        identityBits and (InputDevice.SOURCE_KEYBOARD and InputDevice.SOURCE_CLASS_MASK.inv()) != 0 -> InputDeviceKind.Keyboard
        identityBits and (InputDevice.SOURCE_MOUSE and InputDevice.SOURCE_CLASS_MASK.inv()) != 0 -> InputDeviceKind.Mouse
        identityBits and (InputDevice.SOURCE_TOUCHSCREEN and InputDevice.SOURCE_CLASS_MASK.inv()) != 0 -> InputDeviceKind.Touchscreen
        identityBits and (InputDevice.SOURCE_TOUCHPAD and InputDevice.SOURCE_CLASS_MASK.inv()) != 0 -> InputDeviceKind.Touchpad
        identityBits and (InputDevice.SOURCE_STYLUS and InputDevice.SOURCE_CLASS_MASK.inv()) != 0 -> InputDeviceKind.Pen
        else -> InputDeviceKind.Other
    }
}

/**
 * La réalisation production du seam : le vrai InputManager du système, construit depuis un
 * Context (`getSystemService(INPUT_SERVICE)`, sans permission, sans geste). Le listener du SDK
 * est contracté en UNE callback (`onInputDeviceAdded/Removed/Changed` → l'id, informationnel) ;
 * les fires arrivent sur le thread du [handler] (le main looper par défaut — la même discipline
 * main-thread que toute la fenêtre). Les lectures d'effects restent au niveau d'API du module
 * (minSdk 24) : les chemins 26+/31+ sont gardés par [Build.VERSION.SDK_INT], le même parti pris
 * que le seam de capture.
 */
internal class AndroidInputManagerSource(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : AndroidInputDeviceSource {
    private val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val registrations = HashMap<(Int) -> Unit, InputManager.InputDeviceListener>()

    override fun deviceIds(): List<Int> = InputDevice.getDeviceIds().toList()

    override fun deviceInfo(deviceId: Int): AndroidInputDeviceInfo? {
        val device = InputDevice.getDevice(deviceId) ?: return null
        return AndroidInputDeviceInfo(
            deviceId = deviceId,
            name = device.name,
            sources = device.sources,
            motionRangeAxes = device.motionRanges.map { it.axis },
            vibratorIds = if (Build.VERSION.SDK_INT >= 31) {
                device.vibratorManager?.vibratorIds?.toList() ?: emptyList()
            } else {
                emptyList()
            },
            hasVibrator = device.vibrator?.hasVibrator() == true,
        )
    }

    override fun registerListener(listener: (Int) -> Unit) {
        val sdk = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = listener(deviceId)
            override fun onInputDeviceRemoved(deviceId: Int) = listener(deviceId)
            override fun onInputDeviceChanged(deviceId: Int) = listener(deviceId)
        }
        synchronized(registrations) { registrations[listener] = sdk }
        manager.registerInputDeviceListener(sdk, handler)
    }

    override fun unregisterListener(listener: (Int) -> Unit) {
        val sdk = synchronized(registrations) { registrations.remove(listener) } ?: return
        manager.unregisterInputDeviceListener(sdk)
    }

    override fun startManagedRumble(deviceId: Int, durationMs: Long, strongMagnitude: Float, weakMagnitude: Float): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val device = InputDevice.getDevice(deviceId) ?: return false
        val vibratorManager = device.vibratorManager ?: return false
        // Les moteurs observables du device — JAMAIS le vibreur du téléphone. Le SDK n'expose
        // pas le rôle des moteurs (limite enregistrée) : chaque moteur observable reçoit le
        // one-shot de la durée de l'effet, à l'amplitude dominante — le composite des deux
        // moteurs est ce que le kind DualRumble décrit.
        val ids = vibratorManager.vibratorIds
        if (ids.isEmpty()) return false
        val amplitude = amplitude255(strongMagnitude, weakMagnitude)
        var started = false
        for (id in ids) {
            val vibrator = vibratorManager.getVibrator(id) ?: continue
            started = vibrateOneShot(vibrator, durationMs, amplitude) || started
        }
        return started
    }

    override fun startOneShot(deviceId: Int, durationMs: Long, amplitude: Float): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        val vibrator = device.vibrator ?: return false
        if (!vibrator.hasVibrator()) return false
        return vibrateOneShot(vibrator, durationMs, amplitude255(amplitude, 0f))
    }

    override fun cancelVibration(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            device.vibratorManager?.let { vibratorManager ->
                for (id in vibratorManager.vibratorIds) vibratorManager.getVibrator(id)?.cancel()
            }
        }
        device.vibrator?.cancel()
    }

    override fun close() {
        val all = synchronized(registrations) {
            val snapshot = registrations.values.toList()
            registrations.clear()
            snapshot
        }
        for (sdk in all) manager.unregisterInputDeviceListener(sdk)
    }

    /** Le one-shot sur UN vibreur — l'amplitude 0 est l'amplitude par défaut de la plateforme. */
    private fun vibrateOneShot(vibrator: Vibrator, durationMs: Long, amplitude: Int): Boolean = runCatching {
        val effect = if (amplitude <= 0) {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createOneShot(durationMs, amplitude)
        }
        vibrator.vibrate(effect)
    }.isSuccess

    /** Une magnitude 0..1 en amplitude SDK 1..255 ; la borne 0 reste le défaut plateforme. */
    private fun amplitude255(first: Float, second: Float): Int =
        (first.coerceIn(0f, 1f).coerceAtLeast(second.coerceIn(0f, 1f)) * 255f).roundToInt()
}
