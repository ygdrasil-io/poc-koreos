@file:OptIn(DelicateKadreApi::class)

package org.graphiks.kadre.consumer

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.InputDevice
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.HostSignal
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayConnectionState
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayManagerState
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.DeviceConnectionState
import org.graphiks.kadre.input.DeviceInventory
import org.graphiks.kadre.input.DeviceLifecycleEvent
import org.graphiks.kadre.input.DeviceManagerState
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalSize
import org.junit.runner.RunWith

/**
 * Les six scénarios instrumentés du contrat AND-008 (inventaires display/device/gamepad réels et
 * pression mémoire) sur l'API publique résolue depuis l'umbrella PUBLIÉ — le miroir exact des
 * contrats AND-003..AND-007. Chaque nom de méthode est un `testName` contractuel du mapping
 * AND-008 — la correspondance :
 *
 * | scenario id (registre)                  | méthode                            | sentinelle gardée                   |
 * |-----------------------------------------|------------------------------------|-------------------------------------|
 * | android-display-inventory-real          | androidDisplayInventoryReal        | android-display-no-invented-workarea |
 * | android-devices-inventory-and-kinds     | androidDevicesInventoryAndKinds    | -                                   |
 * | android-gamepad-empty-inventory-honest  | androidGamepadEmptyInventoryHonest | -                                   |
 * | android-memory-pressure-real-mapping    | androidMemoryPressureRealMapping   | -                                   |
 * | android-memory-background-never-maps    | androidMemoryBackgroundNeverMaps   | android-memory-no-background-signal |
 * | android-raw-input-noncall               | androidRawInputNoncall             | -                                   |
 *
 * LES FAITS P7, ÉPELÉS PAR SCÉNARIO :
 *
 * 1. DISPLAY (android-display-inventory-real) — l'inventaire publié reflète le display RÉEL de
 *    l'émulateur : chaque fait est recollé à la lecture SDK du display par défaut
 *    (`DisplayManager.getDisplay(DEFAULT_DISPLAY)`) faite par le scénario lui-même — type
 *    Physical (un display de l'émulateur ne porte ni FLAG_PRESENTATION ni FLAG_PRIVATE), bounds
 *    = getRealSize à l'origine (0, 0), échelle = densityDpi/160, un mode par mode supporté avec
 *    le refresh observé, le mode courant membre de la liste. ET LA WORKAREA EST NULL : Android
 *    n'a pas de bureau, un rectangle y serait une géométrie inventée — c'est l'épingle que la
 *    sentinelle `android-display-no-invented-workarea` garde (workArea = bounds → ce scénario
 *    seul échoue). `requestAccess` répond le succès portant le même inventaire.
 * 2. DEVICES (android-devices-inventory-and-kinds) — l'inventaire publié porte les devices
 *    RÉELS de l'émulateur : un device par id de `InputDevice.getDeviceIds()`, le nom SDK exact,
 *    et le kind traduit de la table normative (identity bits de `sources`, l'ordre clavier →
 *    souris → écran tactile → touchpad → stylet, le carve-out gamepad/joystick en `Other`) —
 *    le clavier virtuel de l'émulateur (« Virtual », sources KEYBOARD|DPAD) est Keyboard. Le
 *    bit d'identité est l'oracle du scénario, dérivé indépendamment des constantes SDK.
 * 3. GAMEPAD (android-gamepad-empty-inventory-honest) — sans manette attachée, l'inventaire
 *    énonce gamepads=0 (le précédent `web-gamepad-empty-inventory-honest` : l'inventaire du
 *    système, jamais un device fabriqué, jamais un Unavailable déguisé) et cet énoncé tient
 *    dans le temps : zéro événement GamepadAdded sur la fenêtre d'observation. AUCUNE
 *    capability d'effets n'est annoncée : les constraints de vibration ne vivent que DANS le
 *    snapshot d'un Gamepad — zéro Gamepad publié, zéro annonce. L'inventaire est VÉRIFIÉ
 *    honnête : le scénario recolle l'absence au SDK lui-même (aucun id courant ne porte les
 *    sources gamepad/joystick).
 * 4. MÉMOIRE RÉELLE (android-memory-pressure-real-mapping) — la voie de livraison RÉELLE :
 *    `UiAutomation.executeShellCommand("am send-trim-memory <pkg> RUNNING_MODERATE")` —
 *    l'UiAutomation de l'instrumentation parle avec l'identité shell du device, le SEUL canal
 *    qui force la décision du system_server (`ActivityManagerService.setProcessMemoryTrimLevel`
 *    → `scheduleTrimMemory` → `ActivityThread.handleTrimMemory` → les callbacks enregistrés de
 *    l'Application — la chaîne entière, du processus système au signal de session). Les deux
 *    niveaux RUNNING montent le mapping normatif : MODERATE → `Moderate`, CRITICAL →
 *    `Critical`, observés sur `KadreLifecycle.signals`. CONTRAINTE SDK ÉPELÉE : la commande
 *    n'accepte qu'une ESCALADE par processus (`getTrimMemoryLevel() >= level` → refus), les
 *    niveaux sont donc envoyés en ordre strictement croissant (5 → 15), un seul envoi par
 *    niveau, dans CE scénario seul — les scénarios se tiennent dans un budget qui ne se
 *    dépense pas deux fois.
 * 5. BACKGROUND JAMAIS (android-memory-background-never-maps) — deux faits. (a) LA VOIE
 *    RÉELLE : les commandes shell `am send-trim-memory <pkg> COMPLETE` et `HIDDEN` (les DEUX
 *    noms de niveaux background que la commande accepte — relevé AOSP
 *    ActivityManagerShellCommand : HIDDEN, RUNNING_MODERATE, BACKGROUND, RUNNING_LOW,
 *    MODERATE, RUNNING_CRITICAL, COMPLETE) partent de l'identité shell de l'UiAutomation —
 *    la décision appartient au system_server, jamais au test. Sur l'API 35 de l'émulateur,
 *    la commande ACCEPTE un nom background sur l'app au premier plan (la sortie capturée est
 *    vide — le relevé « Unable to set a background trim level on a foreground process »
 *    d'ActivityManagerService.setProcessMemoryTrimLevel décrit des arbres AOSP plus anciens ;
 *    le refus observé est alors celui de l'ESCALADE, la contrainte monotone du (4)) — la
 *    règle du contrat est le SILENCE qui SUIT chaque envoi : un niveau background accepté et
 *    réellement délivré traverse `ActivityThread.handleTrimMemory` et la source kadre, dont
 *    la table de mapping ne le parle pas — la perte de visibilité n'est pas de la pression.
 *    (b) LA RÉCEPTION : la chaîne réceptrice est réellement vivante — le contrôle positif
 *    (`TRIM_MEMORY_RUNNING_MODERATE` dispatché par `Application.onTrimMemory`, la JAMBE
 *    Application de la chaîne réelle — `ActivityThread.handleTrimMemory` collecte
 *    l'Application parmi ses callbacks et son `onTrimMemory` public relaie à chaque callback
 *    enregistré, dont celui de la source kadre) monte `Moderate` — puis TOUS les niveaux
 *    background de la constante SDK (UI_HIDDEN 20, BACKGROUND 40, MODERATE 60, COMPLETE 80)
 *    passent par la MÊME chaîne SANS produire JAMAIS un signal. La sentinelle
 *    `android-memory-no-background-signal` garde ce silence (mapping background → signal →
 *    ce scénario seul échoue). Le budget d'escalade du (4) reste intact : les scénarios
 *    partagent le processus et l'ordre d'exécution déterministe de la classe exerce (4)
 *    AVANT (5) — COMPLETE (80) y est une escalade au-dessus du 15 du (4) et HIDDEN (20) y
 *    rencontre le refus d'escalade — et le dispatch Application du contrôle positif ne
 *    passe PAS par le niveau enregistré du system_server.
 * 6. RAW INPUT (android-raw-input-noncall) — la preuve non-appel P3 re-épinglée : la demande
 *    d'accès brut répond `Failure(Unsupported(RawInputAccess))` — la taxonomie nomme
 *    l'opération, aucune primitive native n'est atteinte — et la capability publiée dit la
 *    même chose AVANT l'appel (`rawInput = Unsupported(RawInputAccess)`).
 *
 * Les scénarios sont autonomes (ActivityScenario `use{}`) et ferment leur session
 * explicitement ; l'observation est un polling borné sur le thread d'instrumentation, les
 * appels View passent par le thread main.
 */
@RunWith(AndroidJUnit4::class)
class KadreConsumerDisplaysContractTest {

    /**
     * android-display-inventory-real — l'inventaire DisplayManager.state reflète le display
     * réel de l'émulateur, fait par fait recollé à la lecture SDK du scénario, et la workArea
     * est null (jamais une géométrie de bureau inventée).
     */
    @Test
    fun androidDisplayInventoryReal() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)

            // La lecture SDK indépendante — l'oracle du scénario, sur le display par défaut
            // réel de l'émulateur, au moment de l'assertion.
            val sdkManager = ConsumerTestSupport.notNull(activityReference(scenario))
                .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val real = assertNotNull(sdkManager.getDisplay(Display.DEFAULT_DISPLAY))
            val realSize = Point()
            real.getRealSize(realSize)
            val metrics = DisplayMetrics()
            real.getMetrics(metrics)

            // L'inventaire publié : énuméré, le display par défaut primaire, jamais vide.
            val managerState = awaitDisplayManagerState(scope)
            val inventory = assertIs<DisplayInventory.Enumerated>(managerState.inventory)
            val primary = assertNotNull(inventory.primary, "the default display is the primary")
            assertTrue(inventory.displays.isNotEmpty(), "the emulator has at least one display")
            assertTrue(inventory.displays.contains(primary), "the primary is one of the displays")
            val capability = managerState.capabilities.enumeration
            assertIs<Capability.Supported<Unit>>(capability)
            assertEquals(
                FeatureAvailability.Available,
                capability.availability,
                "the DisplayManager is structurally observable on Android",
            )

            // L'état du display primaire, fait par fait contre la lecture SDK.
            val state = primary.state.value
            assertEquals(DisplayType.Physical, state.type, "the emulator display is physical")
            assertEquals(DisplayConnectionState.Connected, state.connection)
            assertEquals(real.name, state.name, "the name is the SDK's own, never manufactured")
            assertEquals(
                PhysicalPoint(0, 0),
                state.bounds.origin,
                "the bounds sit at the origin — no desktop position is invented",
            )
            assertEquals(
                PhysicalSize(realSize.x, realSize.y),
                state.bounds.size,
                "the bounds are the real size of the default display",
            )
            assertNull(
                state.workArea,
                "Android has no desktop: the work area does not exist and is never invented",
            )
            assertEquals(
                metrics.densityDpi / 160.0,
                state.scaleFactor,
                1e-9,
                "the scale factor is the observed density",
            )

            // Les modes : un mode porté par mode supporté, la géométrie et le refresh du SDK,
            // la profondeur honnêtement null (le SDK n'expose pas de profondeur par mode).
            assertEquals(
                real.supportedModes.size,
                state.modes.size,
                "one published mode per supported SDK mode — never a fabricated mode",
            )
            val expectedModes = real.supportedModes
                .map { mode -> PhysicalSize(mode.physicalWidth, mode.physicalHeight) to mode.refreshRate.toDouble() }
                .sortedWith(compareBy({ it.first.width }, { it.first.height }, { it.second }))
            val actualModes = state.modes
                .map { mode -> mode.physicalSize to mode.refreshRateHz }
                .sortedWith(compareBy({ it.first.width }, { it.first.height }, { it.second }))
            assertEquals(expectedModes, actualModes, "the modes are the SDK's own modes")
            assertTrue(
                state.modes.all { mode -> mode.bitDepth == null },
                "the SDK exposes no per-mode bit depth — honest nulls, never an invented depth",
            )
            assertTrue(
                state.modes.all { mode -> mode.refreshRateHz == null || mode.refreshRateHz!! > 0.0 },
                "a refresh rate is observed or honestly null",
            )
            val current = assertNotNull(state.currentMode, "the SDK reports a current mode")
            assertEquals(real.mode.physicalWidth.toLong(), current.physicalSize.width.toLong())
            assertEquals(real.mode.physicalHeight.toLong(), current.physicalSize.height.toLong())
            assertEquals(real.mode.refreshRate.toDouble(), current.refreshRateHz)
            assertTrue(current in state.modes, "the current mode belongs to the published modes")

            // requestAccess répond le succès, portant le même inventaire réel — et toujours
            // une workArea null (le refus d'invention tient à chaque lecture).
            val access = assertIs<KadreResult.Success<DisplayManagerState>>(scope.displays.requestAccess())
            val accessInventory = assertIs<DisplayInventory.Enumerated>(access.value.inventory)
            val accessPrimary = assertNotNull(accessInventory.primary)
            assertNull(
                accessPrimary.state.value.workArea,
                "requestAccess carries the same honesty: no invented work area",
            )
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-devices-inventory-and-kinds — l'inventaire DeviceManager.state porte les devices
     * réels de l'émulateur, les noms SDK exacts et les kinds de la table normative.
     */
    @Test
    fun androidDevicesInventoryAndKinds() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)

            // La lecture SDK indépendante — les devices RÉELS du système, au moment de l'assertion.
            val realDevices = InputDevice.getDeviceIds()
                .map { id -> InputDevice.getDevice(id) ?: error("device $id vanished mid-scenario") }

            // L'inventaire publié : un device porté par device réel, jamais un de moins.
            val managerState = awaitDeviceManagerState(scope)
            val inventory = assertIs<DeviceInventory.Enumerated>(managerState.inventory)
            assertEquals(
                realDevices.size,
                inventory.devices.size,
                "one published device per real SDK device id",
            )

            // Les noms SDK exacts (multiset, l'ordre d'énumération n'est pas contractuel).
            val expectedNames = realDevices.map(InputDevice::getName).sortedBy { it ?: "" }
            val actualNames = inventory.devices.map { it.descriptor.name }.sortedBy { it ?: "" }
            assertEquals(expectedNames, actualNames, "the descriptor names are the SDK's own names")

            // Les kinds : l'oracle du scénario est la table normative traduite depuis les
            // constantes SDK — les identity bits, l'ordre clavier → souris → écran tactile →
            // touchpad → stylet, le carve-out gamepad/joystick en Other.
            val expectedKinds = realDevices.map { device -> expectedGenericKind(device.sources) }.sorted()
            val actualKinds = inventory.devices.map { it.descriptor.kind }.sorted()
            assertEquals(expectedKinds, actualKinds, "the kinds follow the normative sources table")

            // Le fait nommé du brief : le clavier virtuel de l'émulateur (« Virtual »,
            // sources KEYBOARD|DPAD) est un Keyboard — pas un Other, pas un Touchscreen.
            val virtual = inventory.devices.firstOrNull { it.descriptor.name == "Virtual" }
            assertNotNull(virtual, "the emulator's virtual keyboard is part of the inventory")
            assertEquals(InputDeviceKind.Keyboard, virtual.descriptor.kind)

            // Tous les devices publiés sont connectés (l'énumération vivante du système).
            assertTrue(
                inventory.devices.all { it.connection.value == DeviceConnectionState.Connected },
                "an enumerated device is connected",
            )
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-gamepad-empty-inventory-honest — sans manette : gamepads=0 tenu dans le temps,
     * zéro GamepadAdded, aucune capability d'effets annoncée (les constraints de vibration ne
     * vivent que dans le snapshot d'un Gamepad publié — zéro Gamepad, zéro annonce).
     */
    @Test
    fun androidGamepadEmptyInventoryHonest() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)

            // Le fait SDK qui rend l'énoncé HONNÊTE : aucun device courant ne porte les
            // sources gamepad/joystick — le vide publié est la vérité du système, pas un
            // inventaire manqué.
            assertTrue(
                InputDevice.getDeviceIds().none { id -> isGamepadSource(InputDevice.getDevice(id)?.sources ?: 0) },
                "a gamepad-source device is attached — the emulator fact is no longer the empty fact",
            )

            // L'inventaire : énuméré, les devices réels présents, les manettes absentes.
            val managerState = awaitDeviceManagerState(scope)
            val inventory = assertIs<DeviceInventory.Enumerated>(managerState.inventory)
            assertTrue(inventory.devices.isNotEmpty(), "the emulator has devices — the inventory is not vacuous")
            assertTrue(inventory.gamepads.isEmpty(), "no gamepad is published — never a fabricated pad")

            // L'énoncé tient dans le temps : sur la fenêtre d'observation, aucun événement
            // n'annonce une manette et l'état reste exactement ce que le système énonce.
            val recorder = DeviceEventRecorder(scope.devices.events)
            val deadline = System.currentTimeMillis() + QUIET_WINDOW_MILLIS
            while (System.currentTimeMillis() < deadline) {
                val held = scope.devices.state.value.inventory
                assertIs<DeviceInventory.Enumerated>(held)
                assertTrue(
                    held.gamepads.isEmpty(),
                    "a gamepad was invented over the observation window",
                )
                assertTrue(
                    recorder.snapshot().none { it is DeviceLifecycleEvent.GamepadAdded },
                    "a GamepadAdded was announced: ${recorder.snapshot()}",
                )
                Thread.sleep(POLL_INTERVAL_MILLIS)
            }
            recorder.close()
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-memory-pressure-real-mapping — la voie RÉELLE : la commande shell
     * `am send-trim-memory` (l'identité shell de l'UiAutomation, la décision du
     * system_server, la chaîne entière jusqu'au signal de session). MODERATE → Moderate,
     * CRITICAL → Critical, exactement deux signaux, envoyés en escalade stricte (la
     * contrainte monotone du system_server).
     */
    @Test
    fun androidMemoryPressureRealMapping() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)

            // La capability posée par l'install réelle de la source process à l'attach.
            assertEquals(
                FeatureAvailability.Available,
                scope.lifecycle.capabilities.value.memoryPressure,
                "the memory pressure source is installed by the real attach",
            )

            val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            val recorder = SignalRecorder(scope.lifecycle.signals)

            // RUNNING_MODERATE (5) — la commande shell RÉELLE, le signal Moderate observé.
            val moderateOutput = shellCommand("am send-trim-memory $packageName RUNNING_MODERATE")
            awaitMemorySignal(recorder, MemoryPressureLevel.Moderate)

            // RUNNING_CRITICAL (15) — l'escalade stricte (5 < 15), le signal Critical observé.
            val criticalOutput = shellCommand("am send-trim-memory $packageName RUNNING_CRITICAL")
            awaitMemorySignal(recorder, MemoryPressureLevel.Critical)

            // Exactement ces deux signaux : le canal ne parle pas d'autre chose.
            val levels = recorder.snapshot().filterIsInstance<HostSignal.MemoryPressure>().map { it.level }
            assertEquals(
                listOf(MemoryPressureLevel.Moderate, MemoryPressureLevel.Critical),
                levels,
                "the real trims mapped exactly once each (outputs: '$moderateOutput', '$criticalOutput')",
            )
            recorder.close()
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-memory-background-never-maps — la voie shell RÉELLE des niveaux background
     * (l'identité shell de l'UiAutomation, la décision du system_server) ne produit JAMAIS un
     * signal, et la chaîne réceptrice vivante — contrôle positif compris — ne mappe JAMAIS un
     * niveau background.
     */
    @Test
    fun androidMemoryBackgroundNeverMaps() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            awaitPrimarySurface(captured)
            assertEquals(
                FeatureAvailability.Available,
                scope.lifecycle.capabilities.value.memoryPressure,
                "the memory pressure source is installed by the real attach",
            )

            // (a) LA VOIE RÉELLE : les commandes shell des niveaux background — la décision
            //     appartient au system_server (l'identité shell). La règle du contrat est le
            //     SILENCE après chaque envoi : un nom background accepté et réellement
            //     délivré (le relevé API 35 — sortie vide — montre que la commande accepte
            //     COMPLETE sur l'app au premier plan) traverse handleTrimMemory et la source
            //     kadre sans jamais parler ; un envoi refusé (l'escalade — la contrainte
            //     monotone du system_server — lève l'exacte « Unable to set a trim level »)
            //     n'écrit pas le niveau enregistré et n'envoie rien non plus. La sortie de
            //     chaque commande est retenue pour le diagnostic, jamais affirmée : ni le
            //     refus ni l'acceptation ne sont le contrat, le silence l'est.
            val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            val recorder = SignalRecorder(scope.lifecycle.signals)
            val completeOutput = shellCommand("am send-trim-memory $packageName COMPLETE")
            awaitSilence(recorder, "after the shell COMPLETE send (output: '$completeOutput')")
            val hiddenOutput = shellCommand("am send-trim-memory $packageName HIDDEN")
            awaitSilence(recorder, "after the shell HIDDEN send (output: '$hiddenOutput')")

            // (b) LA RÉCEPTION — le contrôle positif d'abord : la chaîne réceptrice est
            //     vivante. Le dispatch Application est l'appel exact que
            //     ActivityThread.handleTrimMemory fait sur chaque callback enregistré (ici,
            //     celui de la source kadre) ; un niveau RUNNING y monte son signal.
            val activity = ConsumerTestSupport.notNull(activityReference(scenario))
            dispatchTrimMemory(activity, ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
            awaitMemorySignal(recorder, MemoryPressureLevel.Moderate)

            // Les niveaux background de la constante SDK, par la MÊME chaîne : le silence.
            val backgroundLevels = listOf(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                ComponentCallbacks2.TRIM_MEMORY_MODERATE,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            )
            for (level in backgroundLevels) {
                dispatchTrimMemory(activity, level)
                awaitSilence(recorder, "after background trim level $level")
            }

            // Exactement le contrôle positif : aucun niveau background n'a jamais parlé.
            val levels = recorder.snapshot().filterIsInstance<HostSignal.MemoryPressure>().map { it.level }
            assertEquals(
                listOf(MemoryPressureLevel.Moderate),
                levels,
                "background trim levels never map to a signal: ${recorder.snapshot()}",
            )
            recorder.close()
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    /**
     * android-raw-input-noncall — la preuve non-appel P3 re-épinglée : la demande d'accès
     * brut répond `Unsupported(RawInputAccess)` (l'opération nommée par la taxonomie),
     * et la capability publiée refuse avant l'appel.
     */
    @Test
    fun androidRawInputNoncall() = runBlocking {
        ActivityScenario.launch(ConsumerActivity::class.java).use { scenario ->
            val captured = AtomicReference<KadreScope?>(null)
            val session = AtomicReference<KadreSession?>(null)
            scenario.onActivity { activity ->
                session.set(
                    ConsumerTestSupport.success(
                        activity.attachKadre(activity.consumerView, scopeCapturingFactory(captured)),
                    ),
                )
            }
            val scope = ConsumerTestSupport.awaitKadreScope(captured)
            val surface = awaitPrimarySurface(captured)

            // Avant tout appel : la capability publiée ne promet jamais l'accès brut.
            assertEquals(
                Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
                surface.input.state.value.capabilities.rawInput,
                "the published raw input capability is the Unsupported refusal",
            )

            // L'appel : le refus taxonomique exact — aucune primitive native n'est atteinte.
            val refusal = assertIs<KadreResult.Failure>(surface.input.requestRawInput())
            assertEquals(
                KadreFailure.Unsupported(KadreOperation.RawInputAccess),
                refusal.reason,
                "the raw input request is refused for the exact operation it names",
            )
            ConsumerTestSupport.onMain { ConsumerTestSupport.notNull(session).close() }
        }
    }

    // ------------------------------------------------------------------
    // Aides du fichier — le miroir des tests AND-002..AND-007.
    // ------------------------------------------------------------------

    /** La factory qui capture le [KadreScope] de la session pour l'observation du test. */
    private fun scopeCapturingFactory(captured: AtomicReference<KadreScope?>): KadreApplicationFactory =
        KadreApplicationFactory {
            KadreApplication {
                captured.set(this)
                kotlinx.coroutines.awaitCancellation()
            }
        }

    /** L'Activity du scénario, capturée par référence atomique depuis le thread main. */
    private fun activityReference(scenario: ActivityScenario<ConsumerActivity>): AtomicReference<ConsumerActivity?> {
        val reference = AtomicReference<ConsumerActivity?>(null)
        scenario.onActivity { activity -> reference.set(activity) }
        return reference
    }

    /** La surface primaire de la session — publiée par le looper main, attendue par polling. */
    private fun awaitPrimarySurface(captured: AtomicReference<KadreScope?>): HostSurface {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            captured.get()?.primarySurface?.value?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the primary surface was never published within ${DEFAULT_TIMEOUT_MILLIS}ms")
    }

    /** L'état du manager display, énuméré — l'inventaire initial n'attend aucun événement. */
    private fun awaitDisplayManagerState(scope: KadreScope): DisplayManagerState {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = "no display state yet"
        while (System.currentTimeMillis() < deadline) {
            val state = scope.displays.state.value
            if (state.inventory is DisplayInventory.Enumerated) return state
            last = "$state"
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the display inventory was never enumerated within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last")
    }

    /** L'état du manager devices, énuméré — l'inventaire initial n'attend aucun événement. */
    private fun awaitDeviceManagerState(scope: KadreScope): DeviceManagerState {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        var last = "no device state yet"
        while (System.currentTimeMillis() < deadline) {
            val state = scope.devices.state.value
            if (state.inventory is DeviceInventory.Enumerated) return state
            last = "$state"
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("the device inventory was never enumerated within ${DEFAULT_TIMEOUT_MILLIS}ms; last=$last")
    }

    /**
     * La commande shell du device — l'[UiAutomation][androidx.test.platform.app.
     * InstrumentationRegistry] de l'instrumentation parle avec l'identité shell, la seule
     * voie qui force la décision du system_server (`am send-trim-memory`, relevé AOSP :
     * « Only shell can call it » — un uid d'application se voit répondre SecurityException).
     * La fusion `2>&1` est POSÉE par le scénario : l'erreur d'un refus (la levée du
     * system_server sur stderr) doit figurer dans la sortie capturée. Le drainage garde un
     * PLAFOND BORNÉ : la livraison du trim est observée en quelques dizaines de
     * millisecondes et un refus s'imprime en un éclair, mais le descripteur de l'UiAutomation
     * peut rester ouvert sans jamais EOF (câblage émulateur, exécution asynchrone) — le
     * plafond ne coupe jamais une réponse réelle, il borne seulement un descripteur muet.
     */
    private fun shellCommand(command: String): String {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("$command 2>&1")
        descriptor.use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                val output = StringBuilder()
                val buffer = ByteArray(8192)
                val deadline = System.currentTimeMillis() + SHELL_DRAIN_TIMEOUT_MILLIS
                while (true) {
                    val available = stream.available()
                    if (available > 0) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        output.append(String(buffer, 0, read, Charsets.UTF_8))
                        continue
                    }
                    if (System.currentTimeMillis() > deadline) break
                    Thread.sleep(SHELL_DRAIN_POLL_MILLIS)
                }
                return output.toString()
            }
        }
    }

    /**
     * Le dispatch d'un trim sur la JAMBE Application de la chaîne réceptrice — l'appel
     * public exact que la chaîne réelle fait sur l'Application : `ActivityThread.
     * handleTrimMemory` collecte l'Application parmi les callbacks du processus et invoque
     * son `onTrimMemory` (public, @CallSuper — relevé android-35), qui relaie à chaque
     * callback enregistré — dont celui de la source kadre, inscrit par l'attach réel via
     * `registerComponentCallbacks`. C'est la même jambe que parcourent les niveaux
     * background du contrôle du silence : la réception est exercée telle quelle, sans
     * toucher au niveau enregistré du system_server (contrairement aux commandes shell, ce
     * dispatch n'y passe pas — il ne dépense donc RIEN du budget d'escalade du (4)).
     */
    private fun dispatchTrimMemory(activity: ConsumerActivity, level: Int) {
        activity.application.onTrimMemory(level)
    }

    /**
     * Le signal mémoire attendu — observé par polling borné sur le recueil de
     * [KadreLifecycle.signals] (les publications arrivent du looper main via le relais).
     */
    private fun awaitMemorySignal(
        recorder: SignalRecorder,
        level: MemoryPressureLevel,
    ): HostSignal.MemoryPressure {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            recorder.snapshot()
                .filterIsInstance<HostSignal.MemoryPressure>()
                .firstOrNull { it.level == level }
                ?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("no MemoryPressure($level) within ${DEFAULT_TIMEOUT_MILLIS}ms; observed=${recorder.snapshot()}")
    }

    /**
     * La fenêtre bornée du silence : aucun signal supplémentaire — le vide est observé,
     * jamais déduit d'un seul instant.
     */
    private fun awaitSilence(recorder: SignalRecorder, context: String) {
        val baseline = recorder.snapshot().size
        val deadline = System.currentTimeMillis() + SILENCE_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val current = recorder.snapshot()
            assertTrue(
                current.size == baseline,
                "$context: a signal leaked: $current",
            )
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    /**
     * Le prédicat gamepad du scénario — la constante SDK : les sources gamepad/joystick,
     * lues sur leurs bits d'identité (le masque de classe retiré).
     */
    private fun isGamepadSource(sources: Int): Boolean {
        val identity = (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) and InputDevice.SOURCE_CLASS_MASK.inv()
        return sources and identity != 0
    }

    /**
     * L'oracle kind du scénario — la table normative traduite depuis les constantes SDK :
     * identity bits (le masque de classe retiré — SOURCE_TOUCHSCREEN et SOURCE_MOUSE
     * partagent SOURCE_CLASS_POINTER), le carve-out gamepad/joystick d'abord, puis l'ordre
     * clavier → souris → écran tactile → touchpad → stylet, sinon Other.
     */
    private fun expectedGenericKind(sources: Int): InputDeviceKind {
        val classMask = InputDevice.SOURCE_CLASS_MASK
        val identityBits = sources and classMask.inv()
        return when {
            isGamepadSource(sources) -> InputDeviceKind.Other
            identityBits and (InputDevice.SOURCE_KEYBOARD and classMask.inv()) != 0 -> InputDeviceKind.Keyboard
            identityBits and (InputDevice.SOURCE_MOUSE and classMask.inv()) != 0 -> InputDeviceKind.Mouse
            identityBits and (InputDevice.SOURCE_TOUCHSCREEN and classMask.inv()) != 0 -> InputDeviceKind.Touchscreen
            identityBits and (InputDevice.SOURCE_TOUCHPAD and classMask.inv()) != 0 -> InputDeviceKind.Touchpad
            identityBits and (InputDevice.SOURCE_STYLUS and classMask.inv()) != 0 -> InputDeviceKind.Pen
            else -> InputDeviceKind.Other
        }
    }

    /**
     * Le recueil de signaux du test : souscrit en UNDISPATCHED (la souscription est engagée
     * au retour) sur un DISPATCHER DÉDIÉ ([Dispatchers.IO]) — le polling du test reste sur le
     * thread d'instrumentation (Thread.sleep, jamais une suspension) : un collecteur resté sur
     * la boucle runBlocking ne serait repris qu'AU RETOUR du bloc de test, chaque publication
     * dormirait dans le buffer pendant toute l'attente. Sur son dispatcher, le collecteur
     * traite chaque publication dès son arrivée ; l'accumulation sous verrou rend le recueil
     * visible au polling. Les publications arrivent du looper main via le relais hors pile de
     * la source mémoire. Le scope est CELUI DU RECUEIL ([ownScope]) : le scénario le plus
     * souvent passe par le scope runBlocking du test, dont le retour attend la fin des
     * enfants — un recueil vivant au retour (jamais atteint un point de suspension pendant
     * l'épilogue du runner) y est suspendu indéfiniment ; le scope dédié, ANNULÉ par le
     * scénario dès que la fenêtre d'observation est close ([close]), détache l'épilogue du
     * runner de la vie du collecteur.
     */
    private class SignalRecorder(flow: Flow<HostSignal>) {
        private val lock = Any()
        private val signals = mutableListOf<HostSignal>()
        private val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            ownScope.launch(start = CoroutineStart.UNDISPATCHED) {
                flow.collect { signal -> synchronized(lock) { signals += signal } }
            }
        }

        fun snapshot(): List<HostSignal> = synchronized(lock) { signals.toList() }

        /** Termine la fenêtre d'observation : le collecteur est annulé, le recueil figé. */
        fun close() {
            ownScope.cancel()
        }
    }

    /** Le recueil d'événements devices du test — le miroir du recueil de signaux. */
    private class DeviceEventRecorder(flow: Flow<DeviceLifecycleEvent>) {
        private val lock = Any()
        private val events = mutableListOf<DeviceLifecycleEvent>()
        private val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            ownScope.launch(start = CoroutineStart.UNDISPATCHED) {
                flow.collect { event -> synchronized(lock) { events += event } }
            }
        }

        fun snapshot(): List<DeviceLifecycleEvent> = synchronized(lock) { events.toList() }

        /** Termine la fenêtre d'observation : le collecteur est annulé, le recueil figé. */
        fun close() {
            ownScope.cancel()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 25L

        /** La fenêtre bornée du silence : un stimulus background, aucun signal — court. */
        const val SILENCE_WINDOW_MILLIS = 1_500L

        /** La fenêtre bornée du calme : l'inventaire gamepad tient, aucun événement. */
        const val QUIET_WINDOW_MILLIS = 1_500L

        /** Le plafond du drainage d'une commande shell : le refus ou l'accusé arrive en ms. */
        const val SHELL_DRAIN_TIMEOUT_MILLIS = 5_000L

        /** L'intervalle du drainage borné d'une commande shell. */
        const val SHELL_DRAIN_POLL_MILLIS = 20L
    }
}
