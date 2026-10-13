package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.InputDevicePortEvent

/**
 * Le port générique de périphériques d'entrée UIKit/tvOS (phase 8, Task 4) sur la source jeton
 * injectée — le miroir `AndroidInputDevicePort` : relecture ENTIÈRE de l'inventaire à chaque
 * fire (jamais un delta), diff Connected/Disconnected, table mise à jour AVANT la livraison.
 *
 * La composition de l'inventaire (ruling) : les manettes (profils étendu ET micro) + le
 * clavier coalescé quand connecté + les souris quand le klib les nomme (spike : les DEUX
 * cibles). Le kind vient du kernel `uikitGenericDeviceKindOf` — la micro-manette (Siri
 * Remote) y est `Other`, honnêteté tvOS.
 *
 * LA SÉPARATION DE PROPRIÉTÉ DU CLAVIER (ruling Task 4) : ce port publie le clavier comme
 * FAIT de device (connecté ou non) ; la LIVRAISON des touches reste exclusivement
 * `UIKitKeyboardBinding` (phase 5) — les deux surfaces ne partagent AUCUN canal (le binding
 * pose `GCKeyboardInput.keyChangedHandler` sur l'objet coalescé, ce port ne nomme AUCUN
 * handler). L'assert driver « clavier connecté ⇒ une entrée de device ET zéro événement de
 * touche dupliqué » est l'affaire du Task 6 (aucun canal à doubler ici — le type du port ne
 * PORTE aucune livraison).
 */
class UIKitInputDevicePortTest {

    /** La source factice (même jeton que le test manette — un inventaire process partagé). */
    private class FakeSource : UIKitControllerSource {
        private val tokens = linkedMapOf<String, Any>()
        private val facts = linkedMapOf<Any, UIKitControllerFacts>()
        private val names = linkedMapOf<Any, String?>()
        private val listeners = mutableListOf<() -> Unit>()
        var closeCount: Int = 0
            private set
        var fireOnRegister: Boolean = false

        fun connect(name: String, controllerFacts: UIKitControllerFacts): Any {
            val token = Any()
            tokens[name] = token
            facts[token] = controllerFacts
            names[token] = name
            return token
        }

        fun disconnect(name: String) {
            val token = tokens.remove(name) ?: return
            facts.remove(token)
            names.remove(token)
        }

        fun fire() {
            listeners.toList().forEach { listener -> listener() }
        }

        fun listenerCount(): Int = listeners.size

        override fun controllers(): List<Any> = tokens.values.toList()
        override fun registerListener(listener: () -> Unit) {
            listeners += listener
            if (fireOnRegister) fire()
        }
        override fun unregisterListener(listener: () -> Unit) {
            listeners.remove(listener)
        }
        override fun profileFacts(controller: Any): UIKitControllerFacts? = facts[controller]
        override fun nameOf(controller: Any): String? = names[controller]
        override fun isExtendedGamepad(controller: Any): Boolean =
            facts[controller]?.isExtendedGamepad == true
        override fun isMicroGamepadOnly(controller: Any): Boolean =
            facts[controller]?.let { it.isMicroGamepad && !it.isExtendedGamepad } == true
        override fun close() {
            closeCount += 1
        }
    }

    private fun extendedFacts() = UIKitControllerFacts(
        isExtendedGamepad = true,
        buttonReadings = mapOf("buttonA" to UIKitButtonReading(0.0, false)),
        hapticLocalities = setOf(GamepadHapticLocality.Default),
    )

    private fun microFacts() = UIKitControllerFacts(isMicroGamepad = true)
    private fun keyboardFacts() = UIKitControllerFacts(isKeyboard = true)
    private fun mouseFacts() = UIKitControllerFacts(isMouse = true)

    private fun connected(events: List<InputDevicePortEvent>) = events.filterIsInstance<InputDevicePortEvent.Connected>()
    private fun disconnected(events: List<InputDevicePortEvent>) = events.filterIsInstance<InputDevicePortEvent.Disconnected>()

    // MARK: - l'inventaire : la composition et les kinds honnêtes

    @Test
    fun devicesEnumerateGamepadsKeyboardAndMouseWithHonestKinds() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        source.connect("pad", extendedFacts())
        source.connect("siri", microFacts())
        source.connect("kbd", keyboardFacts())
        source.connect("mouse", mouseFacts())
        val port = inventory.openInputDevicePort()

        val byName = port.devices.associateBy { it.descriptor.name }
        assertEquals(4, port.devices.size, "manettes (étendue ET micro) + clavier + souris")
        assertEquals(InputDeviceKind.Other, byName.getValue("pad").descriptor.kind, "la manette reste Other côté générique (carve-out)")
        assertEquals(InputDeviceKind.Other, byName.getValue("siri").descriptor.kind, "la micro-manette (Siri Remote) est Other — honnêteté tvOS")
        assertEquals(InputDeviceKind.Keyboard, byName.getValue("kbd").descriptor.kind)
        assertEquals(InputDeviceKind.Mouse, byName.getValue("mouse").descriptor.kind)
    }

    @Test
    fun theKeyboardAppearsOnlyWhileConnected() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        val port = inventory.openInputDevicePort()
        val events = mutableListOf<InputDevicePortEvent>()
        port.installObserver { events += it }

        source.connect("kbd", keyboardFacts())
        source.fire()
        assertEquals(listOf("kbd"), connected(events).map { it.device.descriptor.name })
        val keyboardKey = connected(events).single().device.key

        source.disconnect("kbd")
        source.fire()
        assertEquals(listOf(keyboardKey), disconnected(events).map { it.key })
        assertTrue(port.devices.isEmpty(), "le clavier coalescé disparu retire SON entrée, jamais une entrée fantôme")
    }

    // MARK: - le diff : Connected/Disconnected, nouvelle clé à la reconnexion

    @Test
    fun diffEmitsConnectedAndDisconnectedWithNewKeysOnReconnect() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        source.connect("pad", extendedFacts())
        val port = inventory.openInputDevicePort()
        val events = mutableListOf<InputDevicePortEvent>()
        port.installObserver { events += it }

        source.connect("kbd", keyboardFacts())
        source.fire()
        val kbdKey = connected(events).filter { it.device.descriptor.name == "kbd" }.single().device.key
        assertTrue(kbdKey >= 0L, "les clés de device sont non négatives")

        source.disconnect("kbd")
        source.fire()
        source.connect("kbd-again", keyboardFacts())
        source.fire()

        val secondKbdKey = connected(events).last().device.key
        assertTrue(secondKbdKey > kbdKey, "la reconnexion obtient une NOUVELLE clé ($secondKbdKey > $kbdKey)")
        assertEquals(listOf(kbdKey), disconnected(events).map { it.key })
    }

    @Test
    fun keysAreSharedAcrossTheTwoPortsForTheSamePhysicalPad() {
        // UNE vérité physique process : la même manette porte LA MÊME clé sur les deux ports —
        // aucune collision inter-domaines (review focus 5 du plan).
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        source.connect("pad", extendedFacts())
        val gamepadPort = inventory.openGamepadPort()
        val devicePort = inventory.openInputDevicePort()
        assertEquals(
            gamepadPort.gamepads.single().key,
            devicePort.devices.single().key,
            "les deux ports projettent la MÊME clé pour la même manette",
        )
    }

    @Test
    fun aFireThatChangesNothingEmitsNothing() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        source.connect("pad", extendedFacts())
        val port = inventory.openInputDevicePort()
        val events = mutableListOf<InputDevicePortEvent>()
        port.installObserver { events += it }
        source.fire()
        source.fire()
        assertTrue(events.isEmpty(), "relecture ENTIÈRE, jamais un delta fabriqué")
    }

    @Test
    fun theKeyboardIsADeviceFactOnlyNeverADeliveryChannel() {
        // La séparation de propriété (ruling Task 4), prouvée par les observables SPLIT du
        // seam : le clavier connecté produit (1) UN fait device côté générique et (2) AUCUN
        // état côté manette (pas de projection, pas de clé gamepad), et (3) les relectures
        // qui ne changent AUCUN fait ne livrent RIEN — un canal de livraison existerait
        // forcément comme flux supplémentaire ici, et le type `InputDevicePort` n'en porte
        // aucun. La LIVRAISON des touches reste exclusivement `UIKitKeyboardBinding`
        // (phase 5) ; l'assert driver « clavier connecté ⇒ une entrée ET zéro touche
        // dupliquée » est l'affaire du Task 6 (charter matériel).
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        source.connect("kbd", keyboardFacts())
        val devicePort = inventory.openInputDevicePort()
        val gamepadPort = inventory.openGamepadPort()
        val deviceEvents = mutableListOf<InputDevicePortEvent>()
        val gamepadEvents = mutableListOf<org.graphiks.kadre.internal.runtime.GamepadPortEvent>()
        devicePort.installObserver { deviceEvents += it }
        gamepadPort.installObserver { gamepadEvents += it }

        source.fire()
        source.fire()

        assertEquals(1, devicePort.devices.size, "le fait device : le clavier connecté est énoncé une fois")
        assertEquals(InputDeviceKind.Keyboard, devicePort.devices.single().descriptor.kind)
        assertTrue(gamepadPort.gamepads.isEmpty(), "AUCUN état côté manette pour le clavier — pas de projection volée")
        assertTrue(deviceEvents.isEmpty() && gamepadEvents.isEmpty(), "les relectures sans changement de fait ne livrent RIEN : aucun flux de « livraison » n'existe pour le clavier")
    }

    // MARK: - le cycle de vie du port

    @Test
    fun aFireDuringOpeningIsFoldedIntoTheFirstSnapshot() {
        val source = FakeSource()
        source.fireOnRegister = true
        source.connect("late", keyboardFacts())
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        val port = inventory.openInputDevicePort()
        assertEquals(1, port.devices.size, "le fire d'ouverture est replié dans l'inventaire initial")
        assertEquals("late", port.devices.single().descriptor.name)
    }

    @Test
    fun portCloseIsIdempotentAndNeverClosesTheSharedSource() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        source.connect("kbd", keyboardFacts())
        val port = inventory.openInputDevicePort()
        port.close()
        port.close()
        assertEquals(0, source.closeCount, "la source process appartient à la couture d'attach")
        assertTrue(port.devices.isEmpty(), "un port fermé projette l'inventaire vide")

        val events = mutableListOf<InputDevicePortEvent>()
        assertFailsWith<IllegalStateException> {
            port.installObserver { events += it }
        }
        assertTrue(events.isEmpty())
    }

    @Test
    fun aSecondObserverInstallIsRejected() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        val port = inventory.openInputDevicePort()
        port.installObserver { }
        assertFailsWith<IllegalStateException> { port.installObserver { } }
    }

    @Test
    fun closingTheLastPortReleasesTheListenerWithoutClosingTheSource() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffectsSeam())
        val gamepadPort = inventory.openGamepadPort()
        val devicePort = inventory.openInputDevicePort()
        devicePort.close()
        assertTrue(source.listenerCount() == 1, "le port device seul parti : l'observation vit (ports gamepad restants)")
        gamepadPort.close()
        assertEquals(0, source.listenerCount(), "le DERNIER port libère l'observation (miroir AppKit)")
        assertEquals(0, source.closeCount, "jamais le close de la source")
        assertIs<UIKitControllerSource>(source)
    }
}

/** Le seam d'effets factice minimal pour l'inventaire des tests device. */
private class FakeEffectsSeam : UIKitHapticsEffectSeam {
    override fun startEffect(
        controller: Any,
        effect: org.graphiks.kadre.input.GamepadEffect.LocalizedHaptic,
    ): org.graphiks.kadre.diagnostics.KadreResult<UIKitGamepadEffectHandle> =
        org.graphiks.kadre.diagnostics.KadreResult.Failure(
            org.graphiks.kadre.diagnostics.KadreFailure.Unsupported(org.graphiks.kadre.diagnostics.KadreOperation.GamepadEffect),
        )
}
