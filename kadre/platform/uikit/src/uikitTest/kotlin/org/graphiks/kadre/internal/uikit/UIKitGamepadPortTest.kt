package org.graphiks.kadre.internal.uikit

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadEffect
import org.graphiks.kadre.input.GamepadEffectConstraints
import org.graphiks.kadre.input.GamepadHapticLocality
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.GamepadRoutingState
import org.graphiks.kadre.input.GamepadState
import org.graphiks.kadre.internal.runtime.GamepadPortEffect
import org.graphiks.kadre.internal.runtime.GamepadPortEvent
import org.graphiks.kadre.internal.runtime.GamepadPortGamepad
import org.graphiks.kadre.internal.runtime.GamepadPortRouting
import org.graphiks.kadre.policy.DeviceEffectOwnership
import org.graphiks.kadre.policy.GamepadRouting

/**
 * Le port manette UIKit/tvOS (phase 8, Task 4) sur la source jeton injectée — JAMAIS un vrai
 * `GCController` (le Simulator ne peut pas en porter ; la traduction est prouvée ICI, sur le
 * seam, la livraison du vrai SDK est l'affaire du charter matériel, Task 6). Le moule est le
 * broker AppKit (`AppKitGameControllerBroker`) : UNE source process, des ports par attach, le
 * lifecycle fan-out vers TOUS les ports (suspendus compris), `StateChanged` SEULEMENT aux ports
 * routés, la table mise à jour AVANT la livraison, le descripteur et les capabilities GELÉS à
 * la connexion, UN effet vivant par manette physique, le teardown qui interrompt.
 */
class UIKitGamepadPortTest {

    // MARK: - les jetons du seam

    /** La source factice : des jetons `Any`, des faits mutables, un fire manuel. */
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

        fun updateFacts(token: Any, controllerFacts: UIKitControllerFacts) {
            facts[token] = controllerFacts
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

    /** Le seam d'effets factice : un handle gardé (l'idempotence est le contrat du handle réel). */
    private class FakeEffects : UIKitHapticsEffectSeam {
        val handles = mutableListOf<FakeHandle>()
        val started = mutableListOf<Pair<Any, GamepadEffect.LocalizedHaptic>>()
        var failure: KadreFailure? = null

        override fun startEffect(
            controller: Any,
            effect: GamepadEffect.LocalizedHaptic,
        ): KadreResult<UIKitGamepadEffectHandle> {
            failure?.let { return KadreResult.Failure(it) }
            started += controller to effect
            return KadreResult.Success(FakeHandle().also { handles += it })
        }
    }

    /** Le handle factice : la même garde atomique que le handle CHHaptics réel. */
    @OptIn(ExperimentalAtomicApi::class)
    private class FakeHandle : UIKitGamepadEffectHandle {
        private val stopped = AtomicBoolean(false)
        var nativeStops: Int = 0
            private set

        override fun requestStop(): KadreResult<Unit> = stop()
        override fun close() {
            stop()
        }

        private fun stop(): KadreResult<Unit> {
            if (!stopped.compareAndSet(false, true)) return KadreResult.Success(Unit)
            nativeStops += 1
            return KadreResult.Success(Unit)
        }
    }

    // MARK: - les faits

    private fun extendedFacts(
        buttons: Map<String, UIKitButtonReading> = mapOf(
            "buttonA" to UIKitButtonReading(0.0, false),
            "dpad.up" to UIKitButtonReading(0.0, false),
        ),
        axes: Map<String, Double> = mapOf("leftThumbstick.xAxis" to 0.0),
        localities: Set<GamepadHapticLocality> = setOf(GamepadHapticLocality.Default),
    ) = UIKitControllerFacts(
        isExtendedGamepad = true,
        buttonReadings = buttons,
        axisReadings = axes,
        hapticLocalities = localities,
    )

    private fun microFacts() = UIKitControllerFacts(isMicroGamepad = true)
    private fun keyboardFacts() = UIKitControllerFacts(isKeyboard = true)

    private fun routing(
        policy: GamepadRouting = GamepadRouting.AllForegroundSessions,
        foregroundActive: Boolean = true,
    ) = GamepadPortRouting(policy, foregroundActive, DeviceEffectOwnership.ExclusivePerPhysicalDevice)

    private fun connected(events: List<GamepadPortEvent>) = events.filterIsInstance<GamepadPortEvent.Connected>()
    private fun disconnected(events: List<GamepadPortEvent>) = events.filterIsInstance<GamepadPortEvent.Disconnected>()
    private fun stateChanges(events: List<GamepadPortEvent>) = events.filterIsInstance<GamepadPortEvent.StateChanged>()
    private fun routingChanges(events: List<GamepadPortEvent>) = events.filterIsInstance<GamepadPortEvent.RoutingChanged>()

    // MARK: - l'inventaire : extended SEUL dans gamepads, clés monotoniques

    @Test
    fun gamepadsListOnlyExtendedProfileControllers() {
        // Ruling Siri Remote : la micro-manette n'est JAMAIS une manette complète — elle vit
        // comme DEVICE du port générique ; le clavier n'est pas une manette non plus.
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("pad", extendedFacts())
        source.connect("siri", microFacts())
        source.connect("kbd", keyboardFacts())
        val port = inventory.openGamepadPort()
        assertEquals(1, port.gamepads.size, "gamepads ne liste QUE les profils étendus")
        assertEquals("pad", port.gamepads.single().descriptor.name)
        assertEquals(GamepadMapping.Native, port.gamepads.single().descriptor.mapping)
    }

    @Test
    fun controllerKeysAreMonotonicAndAReconnectGetsANewKey() {
        // Ruling des clés : process-monotoniques, JAMAIS dérivées de l'identité matérielle —
        // une manette revenue obtient une NOUVELLE clé (critère de sortie runtime « nouvel ID
        // après disparition terminale »). La monotonie PROCESS vient de l'objet unique
        // `KadreIos.controllerInventory` ; ce test prouve la monotonie PAR inventaire.
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        val port = inventory.openGamepadPort()
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        source.connect("pad", extendedFacts())
        source.fire()
        val firstKey = connected(events).single().gamepad.key
        source.disconnect("pad")
        source.fire()
        source.connect("pad-again", extendedFacts())
        source.fire()

        val secondKey = connected(events).last().gamepad.key
        assertTrue(secondKey > firstKey, "une reconnexion obtient une NOUVELLE clé ($secondKey > $firstKey)")
        assertNotEquals(firstKey, secondKey)
        assertEquals(listOf(firstKey), disconnected(events).map { it.key })
        assertEquals(secondKey, port.gamepads.single().key, "la projection porte la clé de la sighting vivante")
    }

    @Test
    fun aFireThatChangesNothingEmitsNothing() {
        // Full re-read JAMAIS un delta : un fire sans changement d'état canonique ne fabrique
        // aucun événement (miroir web : un pad hostile ne streame pas de no-ops).
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("pad", extendedFacts())
        val port = inventory.openGamepadPort()
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }
        source.fire()
        source.fire()
        assertTrue(events.isEmpty(), "aucun événement fabriqué : trouvé $events")
    }

    // MARK: - le lifecycle fan-out (règle AppKit : y compris aux suspendus)

    @Test
    fun lifecycleFansOutToEveryOpenPortIncludingSuspended() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("first", extendedFacts())
        val routed = inventory.openGamepadPort()
        val suspended = inventory.openGamepadPort()
        routed.updateRouting(routing(foregroundActive = true))
        suspended.updateRouting(routing(foregroundActive = false))
        val routedEvents = mutableListOf<GamepadPortEvent>()
        val suspendedEvents = mutableListOf<GamepadPortEvent>()
        routed.installObserver { routedEvents += it }
        suspended.installObserver { suspendedEvents += it }

        source.connect("second", extendedFacts())
        source.fire()

        assertEquals(1, connected(routedEvents).size, "le port routé entend le lifecycle")
        assertEquals(1, connected(suspendedEvents).size, "le port SUSPENDU entend AUSSI le lifecycle (règle AppKit)")
        assertEquals(2, suspended.gamepads.size, "la projection du suspendu porte les deux manettes")
    }

    // MARK: - l'état : enregistré avant la livraison, livré aux routés SEULEMENT

    @Test
    fun stateIsRecordedBeforeDeliveryAndDeliveredOnlyToRoutedPorts() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("pad", extendedFacts())
        val routed = inventory.openGamepadPort()
        val suspended = inventory.openGamepadPort()
        routed.updateRouting(routing())
        suspended.updateRouting(routing(foregroundActive = false))

        val snapshotsAtDelivery = mutableListOf<List<GamepadPortGamepad>>()
        val routedEvents = mutableListOf<GamepadPortEvent>()
        val suspendedEvents = mutableListOf<GamepadPortEvent>()
        routed.installObserver { event ->
            // L'observateur lit le snapshot PENDANT la livraison : la table doit déjà porter
            // l'état qui cause l'événement (state-before-delivery, moule AppKit/web).
            snapshotsAtDelivery += routed.gamepads
            routedEvents += event
        }
        suspended.installObserver { suspendedEvents += it }

        source.updateFacts(
            source.controllers().single(),
            UIKitControllerFacts(
                isExtendedGamepad = true,
                buttonReadings = mapOf("buttonA" to UIKitButtonReading(1.0, true)),
                axisReadings = mapOf("leftThumbstick.xAxis" to -0.5),
                hapticLocalities = setOf(GamepadHapticLocality.Default),
            ),
        )
        source.fire()

        val changed = stateChanges(routedEvents).single()
        assertEquals(1.0, changed.state.buttons.single { it.button == GamepadButton.South }.value)
        assertEquals(-0.5, changed.state.axes.single { it.axis == GamepadAxis.LeftX }.value)
        assertEquals(changed.state, snapshotsAtDelivery.last().single().state, "le snapshot livré PORTE déjà le nouvel état")
        assertTrue(suspendedEvents.isEmpty(), "le port suspendu ne reçoit AUCUN StateChanged")
        // L'état est enregistré QUEL QUE SOIT le routage : la relecture du suspendu voit le présent.
        assertEquals(changed.state, suspended.gamepads.single().state)
    }

    // MARK: - le routage : projection Routed/Suspended, RoutingChanged porte routage+état

    @Test
    fun updateRoutingProjectsRoutedAndSuspendedCarryingState() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("pad", extendedFacts())
        val port = inventory.openGamepadPort()
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }

        port.updateRouting(routing())
        port.updateRouting(routing(foregroundActive = false))

        val routings = routingChanges(events)
        assertEquals(2, routings.size, "une RoutingChanged par transition de routage")
        assertEquals(GamepadRoutingState.Routed, routings[0].routing)
        assertEquals(GamepadRoutingState.Suspended, routings[1].routing)
        assertEquals(port.gamepads.single().state, routings[1].state, "la RoutingChanged porte l'état courant")
        assertEquals(GamepadRoutingState.Suspended, port.gamepads.single().routing)
    }

    @Test
    fun activeSessionOnlyArbitrationRoutesExactlyTheElectedPort() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("pad", extendedFacts())
        val first = inventory.openGamepadPort()
        val second = inventory.openGamepadPort()
        val policy = GamepadPortRouting(GamepadRouting.ActiveSessionOnly, true, DeviceEffectOwnership.SharedWhenSupported)
        first.updateRouting(policy)
        second.updateRouting(policy)
        assertEquals(GamepadRoutingState.Routed, first.gamepads.single().routing, "le PREMIER port éligible est élu")
        assertEquals(GamepadRoutingState.Suspended, second.gamepads.single().routing)
    }

    // MARK: - le gel : descripteur et capabilities figés à la connexion

    @Test
    fun descriptorAndCapabilitiesAreFrozenAtConnection() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        val token = source.connect(
            "pad",
            extendedFacts(
                buttons = mapOf("buttonA" to UIKitButtonReading(0.0, false)),
                axes = mapOf("leftThumbstick.xAxis" to 0.0),
                localities = setOf(GamepadHapticLocality.Default),
            ),
        )
        val port = inventory.openGamepadPort()
        val frozen = port.gamepads.single()
        assertEquals(listOf(GamepadButton.South), frozen.descriptor.buttons)
        assertEquals(listOf(GamepadAxis.LeftX), frozen.descriptor.axes)
        assertEquals(GamepadMapping.Native, frozen.descriptor.mapping)
        val constraints = assertIs<Capability.Supported<GamepadEffectConstraints>>(frozen.capabilities.effects).constraints
        assertEquals(setOf(GamepadHapticLocality.Default), constraints.localizedHaptics?.localities)

        // Le profil « change » ensuite : des éléments de PLUS apparaissent, une localité de
        // PLUS — le descripteur et les capabilities restent CEUX de la connexion (précédent
        // Android : une connexion fige UNE fois), seules les lectures d'état coulent.
        source.updateFacts(
            token,
            extendedFacts(
                buttons = mapOf(
                    "buttonA" to UIKitButtonReading(1.0, true),
                    "buttonB" to UIKitButtonReading(1.0, true),
                    "dpad.up" to UIKitButtonReading(0.0, false),
                ),
                axes = mapOf("leftThumbstick.xAxis" to 0.0, "rightThumbstick.xAxis" to 1.0),
                localities = setOf(GamepadHapticLocality.Default, GamepadHapticLocality.Triggers),
            ),
        )
        source.fire()

        val after = port.gamepads.single()
        assertEquals(frozen.descriptor, after.descriptor, "le descripteur ne mute JAMAIS après la connexion")
        assertEquals(frozen.capabilities, after.capabilities, "les capabilities ne mutent JAMAIS après la connexion")
        assertEquals(1.0, after.state.buttons.single { it.button == GamepadButton.South }.value, "l'état, LUI, coule")
        // Une lecture vivante hors descripteur gelé est perdue, jamais ajoutée :
        assertNull(after.state.buttons.firstOrNull { it.button == GamepadButton.East })
    }

    // MARK: - le kernel d'état : les lectures manquantes restent neutres

    @Test
    fun stateKernelDefaultsMissingReadingsToNeutral() {
        val descriptor = uikitGamepadDescriptorOf(
            buttonNames = setOf("buttonA", "dpad.up"),
            axisNames = setOf("leftThumbstick.xAxis", "dpad.xAxis"),
            controllerName = "Pad",
        )
        val state = uikitGamepadStateOf(
            descriptor = descriptor,
            buttonReadings = mapOf("dpad.up" to UIKitButtonReading(1.0, true)),
            axisReadings = mapOf("dpad.xAxis" to -1.0),
        )
        assertEquals(1.0, state.buttons.single { it.button == GamepadButton.DpadUp }.value)
        assertTrue(state.buttons.single { it.button == GamepadButton.DpadUp }.pressed)
        assertEquals(0.0, state.buttons.single { it.button == GamepadButton.South }.value, "lecture absente → neutre, jamais fabriquée")
        assertFalse(state.buttons.single { it.button == GamepadButton.South }.pressed)
        assertEquals(-1.0, state.axes.single { it.axis == GamepadAxis.DpadX }.value)
        assertEquals(0.0, state.axes.single { it.axis == GamepadAxis.LeftX }.value)
        assertEquals(2, state.buttons.size, "l'état parle EXACTEMENT le vocabulaire du descripteur")
        assertEquals(2, state.axes.size)
    }

    // MARK: - les effets : démarrage, stop/close idempotents, teardown interrupteur

    @Test
    fun startEffectStartsTheNativeHandleAndRequestStopIsIdempotent() {
        val source = FakeSource()
        val effects = FakeEffects()
        val inventory = UIKitControllerInventory(source, effects)
        source.connect("pad", extendedFacts())
        val port = inventory.openGamepadPort()
        val key = port.gamepads.single().key

        val started = port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 0.5, 120.milliseconds))
        val effect = assertIs<KadreResult.Success<GamepadPortEffect>>(started).value
        val handle = effects.handles.single()
        assertEquals(0, handle.nativeStops, "le démarrage n'arrête rien")
        assertEquals(key, 0L, "première sighting de l'inventaire : la clé part de 0")

        assertTrue(effect.requestStop() is KadreResult.Success)
        assertTrue(effect.requestStop() is KadreResult.Success, "un second stop reste un succès")
        assertEquals(1, handle.nativeStops, "le handle natif est gardé : UN seul stop natif")
        effect.close()
        effect.close()
        assertEquals(1, handle.nativeStops, "close après stop : toujours UN seul stop natif")
    }

    @Test
    fun aSecondLiveEffectOnTheSamePhysicalPadIsRefused() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("pad", extendedFacts())
        val port = inventory.openGamepadPort()
        val key = port.gamepads.single().key
        val first = port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 100.milliseconds))
        assertIs<KadreResult.Success<GamepadPortEffect>>(first)

        val second = port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 0.5, 100.milliseconds))
        assertEquals(
            KadreFailure.AlreadyInUse(KadreResourceKind.GamepadEffect),
            assertIs<KadreResult.Failure>(second).reason,
        )

        first.value.close()
        val third = port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 0.5, 100.milliseconds))
        assertIs<KadreResult.Success<GamepadPortEffect>>(third, "après le stop, l'effet suivant est admis")
    }

    @Test
    fun startEffectOnDisconnectedMicroOrUnknownKeyFailsClosed() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        source.connect("pad", extendedFacts())
        source.connect("siri", microFacts()) // sighting 2 : sa clé process est 1, JAMAIS dans gamepads
        val port = inventory.openGamepadPort()
        val padKey = port.gamepads.single().key

        source.disconnect("pad")
        source.fire()
        assertTrue(port.gamepads.isEmpty())

        for (badKey in listOf(padKey, 1L, 99L)) {
            val started = port.startEffect(badKey, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 100.milliseconds))
            val failure = assertIs<KadreResult.Failure>(started, "clé $badKey : manette absente du port → Closed")
            assertEquals(KadreFailure.Closed(KadreResourceKind.Gamepad), failure.reason)
        }
    }

    @Test
    fun startEffectRejectsUnadvertisedLocalityAndNonLocalizedEffects() {
        val source = FakeSource()
        val effects = FakeEffects()
        val inventory = UIKitControllerInventory(source, effects)
        source.connect("pad", extendedFacts(localities = setOf(GamepadHapticLocality.Default)))
        val port = inventory.openGamepadPort()
        val key = port.gamepads.single().key

        val wrongLocality = port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.LeftHandle, 1.0, 100.milliseconds))
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(wrongLocality).reason)

        val rumble = port.startEffect(key, GamepadEffect.DualRumble(1.0, 1.0, 100.milliseconds))
        assertEquals(KadreFailure.InvalidRequest("effect"), assertIs<KadreResult.Failure>(rumble).reason)
        assertTrue(effects.started.isEmpty(), "rien n'atteint le seam natif sans admission")
    }

    @Test
    fun controllerTeardownInterruptsLiveEffects() {
        // Critère de sortie roadmap : « effets interrompus au teardown » — une déconnexion
        // interrompt l'effet vivant de CETTE manette, pas celui des autres.
        val source = FakeSource()
        val effects = FakeEffects()
        val inventory = UIKitControllerInventory(source, effects)
        source.connect("pad", extendedFacts())
        source.connect("other", extendedFacts())
        val port = inventory.openGamepadPort()
        val events = mutableListOf<GamepadPortEvent>()
        port.installObserver { events += it }
        val padKey = port.gamepads.first { it.descriptor.name == "pad" }.key
        val otherKey = port.gamepads.first { it.descriptor.name == "other" }.key
        val padEffect = assertIs<KadreResult.Success<GamepadPortEffect>>(
            port.startEffect(padKey, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 5_000.milliseconds)),
        ).value
        val otherEffect = assertIs<KadreResult.Success<GamepadPortEffect>>(
            port.startEffect(otherKey, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 5_000.milliseconds)),
        ).value
        val padHandle = effects.handles.first()
        val otherHandle = effects.handles.last()

        source.disconnect("pad")
        source.fire()

        assertEquals(1, padHandle.nativeStops, "la déconnexion INTERROMPT l'effet vivant de la manette partie")
        assertEquals(0, otherHandle.nativeStops, "l'effet de l'autre manette survit")
        assertTrue(padEffect.requestStop() is KadreResult.Success, "le handle interrompu reste appelable, sans effet")
        assertEquals(1, padHandle.nativeStops)
        assertTrue(disconnected(events).isNotEmpty())
    }

    @Test
    fun portCloseRevokesItsLiveEffectsAndStaysIdempotent() {
        val source = FakeSource()
        val effects = FakeEffects()
        val inventory = UIKitControllerInventory(source, effects)
        source.connect("pad", extendedFacts())
        val port = inventory.openGamepadPort()
        val key = port.gamepads.single().key
        assertIs<KadreResult.Success<GamepadPortEffect>>(
            port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 5_000.milliseconds)),
        )
        val handle = effects.handles.single()

        port.close()
        assertEquals(1, handle.nativeStops, "le close du port interrompt l'effet vivant")
        port.close()
        assertEquals(1, handle.nativeStops, "le close est idempotent")
        assertEquals(0, source.closeCount, "le port ne ferme JAMAIS la source partagée (la couture d'attach la possède)")

        val after = port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 100.milliseconds))
        assertEquals(KadreFailure.Closed(KadreResourceKind.Gamepad), assertIs<KadreResult.Failure>(after).reason)
        assertTrue(port.gamepads.isEmpty(), "un port fermé projette l'inventaire vide")
    }

    @Test
    fun inventoryCloseInterruptsEverythingAndReleasesTheListenerNeverTheSource() {
        val source = FakeSource()
        val effects = FakeEffects()
        val inventory = UIKitControllerInventory(source, effects)
        source.connect("pad", extendedFacts())
        val port = inventory.openGamepadPort()
        val key = port.gamepads.single().key
        val effect = assertIs<KadreResult.Success<GamepadPortEffect>>(
            port.startEffect(key, GamepadEffect.LocalizedHaptic(GamepadHapticLocality.Default, 1.0, 5_000.milliseconds)),
        ).value

        inventory.close()
        assertEquals(1, effects.handles.single().nativeStops, "le close de l'inventaire interrompt les effets vivants")
        assertTrue(effect.requestStop() is KadreResult.Success)
        assertEquals(0, source.closeCount, "la source process n'est PAS fermée — elle appartient à la couture d'attach")
        assertEquals(0, source.listenerCount(), "l'observation du seam est RETIRÉE au close (miroir AppKit)")
        inventory.close()
        assertEquals(0, source.listenerCount(), "le close de l'inventaire est idempotent")
    }

    @Test
    fun aSecondObserverInstallIsRejectedLikeTheAppKitPort() {
        val source = FakeSource()
        val inventory = UIKitControllerInventory(source, FakeEffects())
        val port = inventory.openGamepadPort()
        port.installObserver { }
        assertFailsWith<IllegalStateException> { port.installObserver { } }
    }

    // MARK: - le tampon d'ouverture : un fire PENDANT l'ouverture est replié, jamais perdu

    @Test
    fun aFireDuringOpeningIsFoldedIntoTheFirstSnapshot() {
        val source = FakeSource()
        source.fireOnRegister = true // le registerListener du fake déclenche un fire SYNCHRONE
        source.connect("late", extendedFacts())
        val inventory = UIKitControllerInventory(source, FakeEffects())
        val port = inventory.openGamepadPort()
        assertEquals(1, port.gamepads.size, "le fire d'ouverture est replié dans le snapshot initial")
        assertEquals("late", port.gamepads.single().descriptor.name)
    }

    // MARK: - la garde de fabrication : un profil complet alimente l'init du modèle

    @Test
    fun fullProfileStateSatisfiesTheModelInvariants() {
        val descriptor = uikitGamepadDescriptorOf(
            buttonNames = UIKIT_GAMEPAD_BUTTON_TABLE.map { it.first }.toSet(),
            axisNames = UIKIT_GAMEPAD_AXIS_TABLE.map { it.first }.toSet(),
            controllerName = "Full",
        )
        val buttons = UIKIT_GAMEPAD_BUTTON_TABLE.associate { (name, _) -> name to UIKitButtonReading(0.0, false) }
        val axes = UIKIT_GAMEPAD_AXIS_TABLE.associate { (name, _) -> name to 0.0 }
        val state = uikitGamepadStateOf(descriptor, buttons, axes)
        assertIs<GamepadState>(state)
        assertEquals(17, state.buttons.size)
        assertEquals(8, state.axes.size)
    }

    @Test
    fun accessorTablesNameExactlyTheKernelTables() {
        // La garde de dérive : les accès SDK de la réalisation portent EXACTEMENT les noms des
        // tables fermées du kernel — un nom ajouté d'un côté sans l'autre perdrait ou
        // inventerait un élément.
        assertTrue(UIKIT_GAMEPAD_ACCESSOR_NAMES_MATCH_KERNEL_TABLES)
        assertEquals(UIKIT_GAMEPAD_BUTTON_TABLE.size, UIKIT_GAMEPAD_BUTTON_ACCESSORS.size)
        assertEquals(UIKIT_GAMEPAD_AXIS_TABLE.size, UIKIT_GAMEPAD_AXIS_ACCESSORS.size)
    }

    @Test
    fun hapticLocalityTableIsTheClosedEightBothWays() {
        // La table des localités est une bijection fermée : chaque constante du klib porte
        // SA localité, chaque localité porte SA constante (le miroir AppKit des 8 valeurs).
        // Le sens constant→localité a de plus son cas hors table ; le sens localité→constante
        // n'en a PAS (les 8 kinds du modèle sont tous en table — la fermeture EST le contrat).
        assertEquals(8, UIKIT_HAPTIC_LOCALITY_TABLE.size)
        for ((constant, locality) in UIKIT_HAPTIC_LOCALITY_TABLE) {
            assertEquals(locality, uikitHapticLocalityOf(constant), "aller : $constant")
            assertEquals(constant, uikitHapticLocalityConstantOf(locality), "retour : $locality")
        }
        assertNull(uikitHapticLocalityOf("GCNonexistentLocality"), "hors table : perdu, jamais approximé")
    }
}
