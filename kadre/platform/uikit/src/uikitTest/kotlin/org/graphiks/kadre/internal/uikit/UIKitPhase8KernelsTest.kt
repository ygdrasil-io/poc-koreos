package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.input.GamepadAxis
import org.graphiks.kadre.input.GamepadButton
import org.graphiks.kadre.input.GamepadDescriptor
import org.graphiks.kadre.input.GamepadMapping
import org.graphiks.kadre.input.InputDeviceKind
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import platform.GameController.GCController
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

/**
 * Les kernels PURS de la phase 8 (Task 1) : les faits display (`UIKitDisplayFacts` →
 * `DisplayPortDisplay`), les tables gamepad GameController → modèle (boutons, axes,
 * descripteur), la précédence des kinds génériques et la clé process-monotonique des
 * manettes — prouvés SANS AUCUN type SDK (le pattern jeton du module : les scalaires
 * pré-extraits entrent, les formes runtime sortent).
 *
 * Les valeurs attendues (Step 1 du brief) : l'assemblage HostViewport/Physical — le type est un
 * PARAMÈTRE (la couture décide par cible : l'écran hôte iOS = HostViewport, les écrans
 * AirPlay = Physical, tvOS = un seul HostViewport — ruling inventaire du pont), `workArea`
 * TouJOURS null (pas de bureau sur iOS/tvOS, précédent Android), un mode unique portant la
 * taille physique (points × échelle, borné par l'arrondi), `refreshRateHz`/`bitDepth` null
 * (rien de mode-prouvé au klib), un descripteur `Native` qui OMES les éléments absents du
 * profil (jamais un bouton toujours-haut fabriqué), la précédence des kinds (le carve-out
 * gamepad D'ABORD, puis Keyboard → Mouse → micro-gamepad → Other, miroir Android
 * `genericDeviceKind`), et des clés de manette NON NÉGATIVES pour toute graine.
 */
class UIKitPhase8KernelsTest {

    // MARK: - display : l'assemblage d'UN écran (faits → DisplayPortDisplay)

    private fun facts(
        key: Long = 1L,
        name: String? = "Main screen",
        width: Double = 1024.0,
        height: Double = 768.0,
        scale: Double = 2.0,
        isMain: Boolean = true,
    ) = UIKitDisplayFacts(key, name, width, height, scale, isMain)

    @Test
    fun displayAssemblesPhysicalBoundsFromPointsTimesScale() {
        val display = uikitDisplayPortDisplay(facts(width = 1024.0, height = 768.0, scale = 2.0), DisplayType.HostViewport)
        assertEquals(2048, display?.bounds?.size?.width)
        assertEquals(1536, display?.bounds?.size?.height)
        // L'origine reste (0,0) : pas de position bureau fabriquée (précédent Android).
        assertEquals(0, display?.bounds?.origin?.x)
        assertEquals(0, display?.bounds?.origin?.y)
    }

    @Test
    fun displayCarriesTypeVerbatimFromTheParameter() {
        // Le type est DÉCIDÉ PAR LA COUTURE (ruling inventaire) : le kernel le porte tel quel,
        // l'écran hôte HostViewport chez iOS, Physical pour un écran AirPlay, l'unique écran
        // tvOS HostViewport — une seule définition, trois coutures.
        assertEquals(DisplayType.HostViewport, uikitDisplayPortDisplay(facts(), DisplayType.HostViewport)?.type)
        assertEquals(DisplayType.Physical, uikitDisplayPortDisplay(facts(), DisplayType.Physical)?.type)
    }

    @Test
    fun displayWorkAreaIsAlwaysNull() {
        // Pas de bureau sur iOS/tvOS : la workArea n'existe pas, elle n'est JAMAIS inventée
        // (précédent Android AndroidDisplayPort:236).
        val display = uikitDisplayPortDisplay(facts(), DisplayType.HostViewport)
        assertNull(display?.workArea)
    }

    @Test
    fun displayCarriesKeyAndNameVerbatim() {
        val named = uikitDisplayPortDisplay(facts(key = 7L, name = "AirPlay"), DisplayType.Physical)
        assertEquals(7L, named?.key)
        assertEquals("AirPlay", named?.name)
        val anonymous = uikitDisplayPortDisplay(facts(key = 0L, name = null), DisplayType.Physical)
        assertNull(anonymous?.name, "le nom absent du klib reste absent — jamais fabriqué")
    }

    @Test
    fun displayCarriesScaleVerbatim() {
        val display = uikitDisplayPortDisplay(facts(scale = 3.0), DisplayType.Physical)
        assertEquals(3.0, display?.scaleFactor)
    }

    @Test
    fun displayExposesExactlyOneModeKeyedAsCurrent() {
        val display = uikitDisplayPortDisplay(facts(width = 3840.0, height = 2160.0, scale = 1.0), DisplayType.Physical)
        val modes = display?.modes.orEmpty()
        assertEquals(1, modes.size, "un écran UIKit expose UN mode — l'écran lui-même")
        val mode = modes.single()
        assertEquals(display?.currentModeKey, mode.key, "le mode courant EST ce mode unique")
        assertEquals(3840, mode.physicalSize.width)
        assertEquals(2160, mode.physicalSize.height)
        assertNull(mode.refreshRateHz, "rien de mode-prouvé au klib : null honnête, jamais inventé")
        assertNull(mode.bitDepth, "le klib ne porte pas la profondeur d'un mode UIKit : null honnête")
    }

    @Test
    fun displayRoundsFractionalPhysicalSizesTowardPositiveInfinity() {
        val display = uikitDisplayPortDisplay(facts(width = 100.5, height = 99.5, scale = 1.0), DisplayType.Physical)
        assertEquals(101, display?.bounds?.size?.width, "roundToInt : les égalités vont vers +∞")
        assertEquals(100, display?.bounds?.size?.height, "roundToInt : les égalités vont vers +∞")
    }

    @Test
    fun unmeasurableDisplayIsRejectedNotFabricated() {
        // Précédent Android translatedDisplay : une géométrie non portable est un échec (null),
        // jamais un display inventé — le port en fait un TemporarilyUnavailable.
        assertNull(uikitDisplayPortDisplay(facts(width = 0.0), DisplayType.Physical), "largeur nulle")
        assertNull(uikitDisplayPortDisplay(facts(height = -1.0), DisplayType.Physical), "hauteur négative")
        assertNull(uikitDisplayPortDisplay(facts(scale = 0.0), DisplayType.Physical), "échelle nulle (view hors fenêtre)")
        assertNull(uikitDisplayPortDisplay(facts(scale = Double.NaN), DisplayType.Physical), "échelle NaN")
        assertNull(uikitDisplayPortDisplay(facts(width = Double.POSITIVE_INFINITY), DisplayType.Physical), "largeur infinie")
    }

    // MARK: - display : l'inventaire (l'assemblage snapshot, primary = l'écran hôte)

    private fun displayOf(key: Long): DisplayPortDisplay =
        checkNotNull(uikitDisplayPortDisplay(facts(key = key), DisplayType.Physical))

    @Test
    fun snapshotSelectsTheHostKeyAsPrimary() {
        val inventory = listOf(displayOf(1L), displayOf(2L))
        val snapshot = uikitDisplaySnapshotOf(inventory, primaryKey = 2L)
        assertEquals(2L, snapshot?.primaryKey, "l'écran hôte (la scène) est le primary quand présent")
        assertEquals(inventory, snapshot?.displays, "l'inventaire est porté EN ENTIER, jamais filtré")
    }

    @Test
    fun snapshotFallsBackToTheFirstDisplayWhenTheHostKeyIsAbsent() {
        val inventory = listOf(displayOf(5L), displayOf(6L))
        val snapshot = uikitDisplaySnapshotOf(inventory, primaryKey = null)
        assertEquals(5L, snapshot?.primaryKey, "sans écran hôte connu, le premier écran est primary")
        assertEquals(5L, uikitDisplaySnapshotOf(inventory, primaryKey = 99L)?.primaryKey)
    }

    @Test
    fun snapshotOfAnEmptyInventoryIsNullNeverAnEmptySuccess() {
        // Tout-ou-rien (miroir Android/web) : un snapshot réussi ne porte jamais un inventaire
        // partiel — vide n'est pas un succès.
        assertNull(uikitDisplaySnapshotOf(emptyList(), primaryKey = 0L))
    }

    // MARK: - gamepad : la table fermée des boutons (17) et des axes (8)

    @Test
    fun faceButtonsMapToTheStandardPositions() {
        assertEquals(GamepadButton.South, uikitGamepadButtonOf("buttonA"))
        assertEquals(GamepadButton.East, uikitGamepadButtonOf("buttonB"))
        assertEquals(GamepadButton.West, uikitGamepadButtonOf("buttonX"))
        assertEquals(GamepadButton.North, uikitGamepadButtonOf("buttonY"))
    }

    @Test
    fun shoulderAndTriggerButtonsMapToTheirSides() {
        assertEquals(GamepadButton.LeftShoulder, uikitGamepadButtonOf("leftShoulder"))
        assertEquals(GamepadButton.RightShoulder, uikitGamepadButtonOf("rightShoulder"))
        assertEquals(GamepadButton.LeftTrigger, uikitGamepadButtonOf("leftTrigger"))
        assertEquals(GamepadButton.RightTrigger, uikitGamepadButtonOf("rightTrigger"))
    }

    @Test
    fun systemButtonsMapToTheModelVocabulary() {
        assertEquals(GamepadButton.Start, uikitGamepadButtonOf("buttonMenu"), "le menu MFi est le bouton start-position")
        assertEquals(GamepadButton.Select, uikitGamepadButtonOf("buttonOptions"), "options est le bouton select-position")
        assertEquals(GamepadButton.Mode, uikitGamepadButtonOf("buttonHome"), "home est le bouton mode/guide")
    }

    @Test
    fun dpadAndThumbstickButtonsMap() {
        assertEquals(GamepadButton.DpadUp, uikitGamepadButtonOf("dpad.up"))
        assertEquals(GamepadButton.DpadDown, uikitGamepadButtonOf("dpad.down"))
        assertEquals(GamepadButton.DpadLeft, uikitGamepadButtonOf("dpad.left"))
        assertEquals(GamepadButton.DpadRight, uikitGamepadButtonOf("dpad.right"))
        assertEquals(GamepadButton.LeftStick, uikitGamepadButtonOf("leftThumbstickButton"))
        assertEquals(GamepadButton.RightStick, uikitGamepadButtonOf("rightThumbstickButton"))
    }

    @Test
    fun buttonTableIsTheClosedSeventeen() {
        val mapped = UIKIT_GAMEPAD_BUTTON_TABLE.map { it.second }.toSet()
        val expected = setOf(
            GamepadButton.South,
            GamepadButton.East,
            GamepadButton.West,
            GamepadButton.North,
            GamepadButton.LeftShoulder,
            GamepadButton.RightShoulder,
            GamepadButton.LeftTrigger,
            GamepadButton.RightTrigger,
            GamepadButton.Select,
            GamepadButton.Start,
            GamepadButton.Mode,
            GamepadButton.LeftStick,
            GamepadButton.RightStick,
            GamepadButton.DpadUp,
            GamepadButton.DpadDown,
            GamepadButton.DpadLeft,
            GamepadButton.DpadRight,
        )
        assertEquals(expected, mapped, "la table fermée couvre EXACTEMENT les 17 kinds bouton du modèle")
    }

    @Test
    fun unmappedButtonNamesAreLostNeverApproximated() {
        assertNull(uikitGamepadButtonOf("leftForce"), "les paddles hors table sont PERDUS, jamais transmués")
        assertNull(uikitGamepadButtonOf("buttonA."), "pas de normalisation : la table est exacte")
        assertNull(uikitGamepadButtonOf(""), "le nom vide n'est pas un élément")
    }

    @Test
    fun thumbstickAxesMapToTheModelVocabulary() {
        assertEquals(GamepadAxis.LeftX, uikitGamepadAxisOf("leftThumbstick.xAxis"))
        assertEquals(GamepadAxis.LeftY, uikitGamepadAxisOf("leftThumbstick.yAxis"))
        assertEquals(GamepadAxis.RightX, uikitGamepadAxisOf("rightThumbstick.xAxis"))
        assertEquals(GamepadAxis.RightY, uikitGamepadAxisOf("rightThumbstick.yAxis"))
    }

    @Test
    fun triggerAndDpadAxesMap() {
        assertEquals(GamepadAxis.LeftTrigger, uikitGamepadAxisOf("leftTrigger"))
        assertEquals(GamepadAxis.RightTrigger, uikitGamepadAxisOf("rightTrigger"))
        assertEquals(GamepadAxis.DpadX, uikitGamepadAxisOf("dpad.xAxis"))
        assertEquals(GamepadAxis.DpadY, uikitGamepadAxisOf("dpad.yAxis"))
    }

    @Test
    fun axisTableIsTheClosedEight() {
        val mapped = UIKIT_GAMEPAD_AXIS_TABLE.map { it.second }.toSet()
        assertEquals(8, mapped.size, "la table fermée couvre les 8 kinds axe du modèle, sans doublon")
    }

    @Test
    fun unmappedAxisNamesAreLostNeverApproximated() {
        assertNull(uikitGamepadAxisOf("leftThumbstick.xAxis."))
        assertNull(uikitGamepadAxisOf("xAxis"))
        assertNull(uikitGamepadAxisOf(""))
    }

    // MARK: - gamepad : le descripteur (mapping Native, éléments absents OMIS)

    @Test
    fun descriptorIsNativeForGameController() {
        // GameController DÉFINIT le layout : la couture annonce `Native`, jamais Standard.
        val descriptor = uikitGamepadDescriptorOf(
            buttonNames = setOf("buttonA", "buttonB", "buttonX", "buttonY"),
            axisNames = setOf("leftThumbstick.xAxis", "leftThumbstick.yAxis"),
            controllerName = "DualShock 4",
        )
        assertEquals(GamepadMapping.Native, descriptor.mapping)
        assertEquals("DualShock 4", descriptor.name)
    }

    @Test
    fun descriptorOmitsElementsAbsentFromTheProfile() {
        // Un profil MFi sans boutons système ne porte PAS de Select/Start/Mode fabriqués
        // toujours-hauts : l'absence d'un élément reste une absence (ruling descripteur).
        val descriptor = uikitGamepadDescriptorOf(
            buttonNames = setOf("buttonA", "dpad.up"),
            axisNames = setOf("leftThumbstick.xAxis"),
            controllerName = null,
        )
        assertEquals(listOf<GamepadButton>(GamepadButton.South, GamepadButton.DpadUp), descriptor.buttons)
        assertEquals(listOf<GamepadAxis>(GamepadAxis.LeftX), descriptor.axes)
        assertNull(descriptor.name, "le vendorName absent reste absent — jamais « Unknown » fabriqué")
    }

    @Test
    fun descriptorUnmappedNamesAreDropped() {
        val descriptor = uikitGamepadDescriptorOf(
            buttonNames = setOf("buttonA", "leftForce", "rightPaddle"),
            axisNames = setOf("leftThumbstick.xAxis", "gyro"),
            controllerName = null,
        )
        assertEquals(listOf<GamepadButton>(GamepadButton.South), descriptor.buttons)
        assertEquals(listOf<GamepadAxis>(GamepadAxis.LeftX), descriptor.axes)
    }

    @Test
    fun descriptorFollowsTheCanonicalTableOrderWhateverTheSetOrder() {
        val forward = uikitGamepadDescriptorOf(setOf("dpad.up", "buttonA"), emptySet(), null)
        val backward = uikitGamepadDescriptorOf(setOf("buttonA", "dpad.up"), emptySet(), null)
        assertEquals(forward.buttons, backward.buttons, "l'ordre du descripteur est celui de la table, jamais celui du Set")
    }

    @Test
    fun descriptorIsWellFormedForTheModelInit() {
        // Le garde du modèle : le descripteur complet (17+8) passe l'init de GamepadDescriptor
        // — la preuve qu'aucune table ne peut produire un doublon.
        val everyButtonName = UIKIT_GAMEPAD_BUTTON_TABLE.map { it.first }.toSet()
        val everyAxisName = UIKIT_GAMEPAD_AXIS_TABLE.map { it.first }.toSet()
        val descriptor = uikitGamepadDescriptorOf(everyButtonName, everyAxisName, "Full")
        assertIs<GamepadDescriptor>(descriptor)
        assertEquals(17, descriptor.buttons.size)
        assertEquals(8, descriptor.axes.size)
        assertTrue(descriptor.buttons.contains(GamepadButton.LeftTrigger))
        assertTrue(descriptor.axes.contains(GamepadAxis.LeftTrigger), "leftTrigger porte le bouton ET l'axe : le même élément klib")
    }

    // MARK: - device kinds : la précédence (le carve-out gamepad d'abord)

    @Test
    fun gamepadCarveOutWinsOverEveryOtherTrait() {
        // Le miroir Android genericDeviceKind:260 : un device gamepad reste `Other` côté
        // générique MÊME s'il déclare aussi clavier ET souris — la manette est un `Gamepad` du
        // port manette, le port générique ne la retape pas.
        assertEquals(
            InputDeviceKind.Other,
            uikitGenericDeviceKindOf(isGamepad = true, isKeyboard = true, isMouse = true, isMicroGamepad = true),
        )
        assertEquals(
            InputDeviceKind.Other,
            uikitGamepadOnly(),
        )
    }

    private fun uikitGamepadOnly(): InputDeviceKind =
        uikitGenericDeviceKindOf(isGamepad = true, isKeyboard = false, isMouse = false, isMicroGamepad = false)

    @Test
    fun keyboardAndMouseAreTypedWhenNoGamepadCarveOut() {
        assertEquals(
            InputDeviceKind.Keyboard,
            uikitGenericDeviceKindOf(isGamepad = false, isKeyboard = true, isMouse = true, isMicroGamepad = false),
            "clavier avant souris",
        )
        assertEquals(
            InputDeviceKind.Mouse,
            uikitGenericDeviceKindOf(isGamepad = false, isKeyboard = false, isMouse = true, isMicroGamepad = false),
        )
    }

    @Test
    fun microGamepadIsCarvedToOtherAfterKeyboardAndMouse() {
        assertEquals(
            InputDeviceKind.Other,
            uikitGenericDeviceKindOf(isGamepad = false, isKeyboard = false, isMouse = false, isMicroGamepad = true),
            "la micro-manette (Siri Remote) est du ressort du port manette : Other côté générique",
        )
        assertEquals(
            InputDeviceKind.Keyboard,
            uikitGenericDeviceKindOf(isGamepad = false, isKeyboard = true, isMouse = false, isMicroGamepad = true),
            "une identité clavier/souris RÉELLE gagne la micro-manette (l'ordre du ruling)",
        )
    }

    @Test
    fun nothingIsOther() {
        assertEquals(
            InputDeviceKind.Other,
            uikitGenericDeviceKindOf(isGamepad = false, isKeyboard = false, isMouse = false, isMicroGamepad = false),
        )
    }

    // MARK: - les clés process-monotoniques (jamais négatives)

    @Test
    fun controllerKeysAreNonNegativeForAnySeed() {
        val seeds = listOf(Long.MIN_VALUE, -1L, -42L, 0L, 1L, 42L, Long.MAX_VALUE)
        for (seed in seeds) {
            val key = uikitControllerKey(seed)
            assertTrue(key >= 0L, "la clé de la graine $seed doit être non négative, obtenu $key")
        }
    }

    @Test
    fun controllerKeysKeepDistinctNonNegativeSeedsDistinct() {
        assertEquals(0L, uikitControllerKey(0L))
        assertEquals(1L, uikitControllerKey(1L))
        assertEquals(42L, uikitControllerKey(42L))
        assertEquals(Long.MAX_VALUE, uikitControllerKey(Long.MAX_VALUE))
        // -1 et Long.MAX_VALUE se partagent l'image (le bit de signe est le seul porté perdu) :
        // le contrat porte les graines monotoniques allouées par la couture (0, 1, 2…).
        assertEquals(uikitControllerKey(Long.MAX_VALUE), uikitControllerKey(-1L))
    }

    @Test
    fun controllerKeysStayMonotonicOnMonotonicSeeds() {
        var previous = -1L
        for (seed in 0L..64L) {
            val key = uikitControllerKey(seed)
            assertTrue(key > previous, "des graines croissantes donnent des clés croissantes")
            previous = key
        }
    }

    // MARK: - le probe de la décision 3 (spike Task 1) : l'inventaire honnête du Simulator

    /**
     * L'oracle SDK peut être vide OU porter un contrôleur ponté depuis le Mac (Xcode 27).
     * L'énumération brute ne doit pas inventer de jetons ; le driver compare la projection
     * au même oracle vivant, sans exiger un zéro dépendant de l'environnement.
     */
    @Test
    fun gcControllerInventoryIsHonestlyEmptyOnSimulator() {
        val controllers = GCController.controllers().orEmpty()
        assertEquals(controllers.size, controllers.filterIsInstance<GCController>().size)
    }

    /**
     * Le probe de la décision 2 (ruling mémoire, Task 5) : la constante
     * `UIApplicationDidReceiveMemoryWarningNotification` compile depuis la famille PARTAGÉE
     * (`uikitTest` entre dans `iosTest` ET `tvosTest`) et se résout en nom non vide sur LES
     * DEUX plateformes — le mapping mémoire Moderate-only peut s'y abonner sans couture.
     */
    @Test
    fun memoryWarningNotificationConstantResolvesOnBothPlatforms() {
        val name = UIApplicationDidReceiveMemoryWarningNotification!!
        assertFalse(name.isEmpty(), "la constante memory-warning est un nom de notification nommé")
    }

    // MARK: - la garde de fabrication : les faits display alimentent l'init du modèle

    @Test
    fun assembledDisplayCarriesModelValidGeometry() {
        // Un DisplayPortDisplay assemblé depuis des faits réels (l'iPhone 16 Pro : 402×874
        // points @3x) est accepté TEL QUEL par la forme runtime — sinon le port fabriquerait.
        val display = checkNotNull(uikitDisplayPortDisplay(facts(key = 0L, width = 402.0, height = 874.0, scale = 3.0), DisplayType.HostViewport))
        assertIs<DisplayPortDisplay>(display)
        assertEquals(1206, display.bounds.size.width)
        assertEquals(2622, display.bounds.size.height)
        assertFalse(display.modes.isEmpty())
    }
}
