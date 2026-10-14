package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.internal.runtime.InputDevicePort
import org.graphiks.kadre.internal.runtime.InputDevicePortDevice
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent

/**
 * Le port générique de périphériques d'entrée d'UNE session, projection de
 * l'[UIKitControllerInventory] process — le miroir `AndroidInputDevicePort` : relecture
 * ENTIÈRE de l'inventaire à chaque fire (jamais un delta), diff Connected/Disconnected,
 * la table mise à jour AVANT la livraison, les événements livrés HORS du lock.
 *
 * LA COMPOSITION DE L'INVENTAIRE (ruling phase 8) : les manettes (profils étendu ET micro) +
 * le clavier coalescé `GCKeyboard` quand connecté + les souris `GCMouse` quand le klib les
 * nomme (spike T1 : les DEUX cibles). Le kind vient du kernel [uikitGenericDeviceKindOf] —
 * la micro-manette (Siri Remote) y est `Other`, honnêteté tvOS : elle n'est JAMAIS une
 * manette complète (le port manette ne liste que les profils étendus).
 *
 * LA SÉPARATION DE PROPRIÉTÉ DU CLAVIER (ruling Task 4) : ce port publie le clavier comme
 * FAIT de device (connecté ou non) ; la LIVRAISON des touches reste exclusivement
 * `UIKitKeyboardBinding` (phase 5), qui pose `GCKeyboardInput.keyChangedHandler` sur l'objet
 * coalescé. AUCUN double canal : ce port ne nomme AUCUN handler de touche (le type
 * `InputDevicePort` ne porte aucune livraison), et le binding ne publie aucun fait device —
 * les deux surfaces se partagent le SEUL objet `GCKeyboard` sans jamais partager un flux.
 * L'assert driver « clavier connecté ⇒ une entrée de device ET zéro événement de touche
 * dupliqué » est l'affaire du Task 6 (charter matériel).
 *
 * Le port NE ferme JAMAIS la source de l'inventaire (la couture d'attach la possède).
 */
internal class UIKitInputDevicePort internal constructor(
    private val inventory: UIKitControllerInventory,
) : InputDevicePort {
    override val inventoryFailure get() = inventory.inventoryFailure
    private var closed: Boolean = false
    private var observer: ((InputDevicePortEvent) -> Unit)? = null
    private val known = linkedSetOf<Long>()

    override val devices: List<InputDevicePortDevice>
        get() = inventory.devices(this)

    override fun installObserver(observer: (InputDevicePortEvent) -> Unit): AutoCloseable =
        inventory.installObserver(this, observer)

    override fun close() {
        inventory.closeInputDevicePort(this)
    }

    // MARK: - le contexte de session, muté par l'inventaire SOUS son lock (le suffixe Locked)

    internal fun isOpenLocked(): Boolean = !closed
    internal fun observerLocked(): ((InputDevicePortEvent) -> Unit)? = observer

    /** L'inventaire initial de l'OUVERTURE — sans événement : la première lecture est la connexion. */
    internal fun installInitialLocked(devices: List<InputDevicePortDevice>) {
        devices.forEach { device -> known += device.key }
    }

    internal fun installObserverLocked(observer: (InputDevicePortEvent) -> Unit): AutoCloseable {
        check(this.observer == null) { "the uikit input-device port observer is already installed" }
        this.observer = observer
        return AutoCloseable {
            if (this.observer === observer) this.observer = null
        }
    }

    internal fun connectedLocked(device: InputDevicePortDevice): DeviceDelivery? {
        known += device.key
        return observer?.let { target -> DeviceDelivery(target, InputDevicePortEvent.Connected(device)) }
    }

    internal fun disconnectedLocked(key: Long): DeviceDelivery? {
        if (!known.remove(key)) return null
        return observer?.let { target -> DeviceDelivery(target, InputDevicePortEvent.Disconnected(key)) }
    }

    internal fun closeFromInventoryLocked() {
        closed = true
        observer = null
        known.clear()
    }
}
