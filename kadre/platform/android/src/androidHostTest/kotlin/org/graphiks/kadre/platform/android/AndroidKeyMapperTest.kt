package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La table clavier portée du réservoir kadre-old vers le modèle HID (page 0x07,
 * `PhysicalKey.Code`) — tests purs, aucun KeyEvent instancié (les constantes
 * `KEYCODE_*`/`META_*` sont des constantes de compilation).
 *
 * Les usages attendus sont ceux de la table web de référence (`webHidUsageForCode`,
 * WebInputMapping.kt) : lettres 0x04-0x1D, chiffres 0x1E-0x27, Enter 0x28, Escape 0x29,
 * Backspace 0x2A, Tab 0x2B, Space 0x2C, F1-F12 0x3A-0x45, flèches 0x4F-0x52,
 * modificateurs L/R 0xE0-0xE7. Une touche hors table reste honnête :
 * `PhysicalKey.Unidentified("android-<code>")`, aucun stimulus inventé.
 */
class AndroidKeyMapperTest {

    // -----------------------------------------------------------------------
    // hidUsage — la table KEYCODE → usage HID, chaque entrée épinglée
    // -----------------------------------------------------------------------

    @Test
    fun everyLetterMapsToItsKeyboardUsageInOrder() {
        // Usages 0x04..0x1D : Keyboard a..z, dans l'ordre alphabétique des KEYCODE_A..Z.
        for (offset in 0..25) {
            assertEquals(0x07 to (0x04 + offset), AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_A + offset))
        }
    }

    @Test
    fun everyDigitMapsToItsKeyboardUsage() {
        // Usage 0x1E = Keyboard 1, …, usage 0x27 = Keyboard 0 — le zéro EST le dernier.
        assertEquals(0x07 to 0x27, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_0))
        for (digit in 1..9) {
            assertEquals(0x07 to (0x1D + digit), AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_1 + digit - 1))
        }
    }

    @Test
    fun everyFunctionKeyMapsToItsKeyboardUsage() {
        // Usages 0x3A..0x45 : Keyboard F1..F12.
        for (index in 0..11) {
            assertEquals(0x07 to (0x3A + index), AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_F1 + index))
        }
    }

    @Test
    fun everyDpadKeyMapsToItsArrowUsage() {
        // Usages 0x4F..0x52 : Right, Left, Down, Up — l'ordre de la table HID.
        assertEquals(0x07 to 0x52, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(0x07 to 0x51, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0x07 to 0x50, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(0x07 to 0x4F, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun everySpecialKeyMapsToItsKeyboardUsage() {
        assertEquals(0x07 to 0x28, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_ENTER))
        assertEquals(0x07 to 0x29, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_ESCAPE))
        // KEYCODE_DEL est la touche Backspace (usage 0x2A), pas la touche Delete (0x4C).
        assertEquals(0x07 to 0x2A, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_DEL))
        assertEquals(0x07 to 0x2B, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_TAB))
        assertEquals(0x07 to 0x2C, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun everyModifierMapsToItsLeftOrRightUsage() {
        // Usages 0xE0..0xE7 : Control/Shift/Alt/Meta, gauche puis droite.
        assertEquals(0x07 to 0xE0, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(0x07 to 0xE1, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(0x07 to 0xE2, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_ALT_LEFT))
        assertEquals(0x07 to 0xE3, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_META_LEFT))
        assertEquals(0x07 to 0xE4, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_CTRL_RIGHT))
        assertEquals(0x07 to 0xE5, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertEquals(0x07 to 0xE6, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_ALT_RIGHT))
        assertEquals(0x07 to 0xE7, AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_META_RIGHT))
    }

    @Test
    fun keysOutsideTheTableHaveNoUsage() {
        assertNull(AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_BACK))
        assertNull(AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_MENU))
        assertNull(AndroidKeyMapper.hidUsage(KeyEvent.KEYCODE_SHIFT_LEFT + 1000))
        assertNull(AndroidKeyMapper.hidUsage(-1))
    }

    // -----------------------------------------------------------------------
    // physicalKey — Code HID ou Unidentification honnête
    // -----------------------------------------------------------------------

    @Test
    fun mappedKeysBecomeHidPhysicalKeys() {
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0x04), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_A))
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0x27), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_0))
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0x52), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0x28), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_ENTER))
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0xE7), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_META_RIGHT))
    }

    @Test
    fun unmappedKeysStayUnidentifiedWithTheirNativeToken() {
        assertEquals(PhysicalKey.Unidentified("android-4"), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_BACK))
        assertEquals(PhysicalKey.Unidentified("android-24"), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(PhysicalKey.Unidentified("android-82"), AndroidKeyMapper.physicalKey(KeyEvent.KEYCODE_MENU))
        assertEquals(PhysicalKey.Unidentified("android-9999"), AndroidKeyMapper.physicalKey(9999))
    }

    // -----------------------------------------------------------------------
    // logicalKey — table nommée, caractère fiable, sinon Unidentified (miroir webLogicalKey)
    // -----------------------------------------------------------------------

    @Test
    fun namedKeysUseTheNamedTable() {
        assertEquals(LogicalKey.Named(NamedKey.Enter), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_ENTER, null))
        assertEquals(LogicalKey.Named(NamedKey.Tab), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_TAB, null))
        assertEquals(LogicalKey.Named(NamedKey.Backspace), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_DEL, null))
        assertEquals(LogicalKey.Named(NamedKey.Escape), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_ESCAPE, null))
        assertEquals(LogicalKey.Named(NamedKey.ArrowUp), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_DPAD_UP, null))
        assertEquals(LogicalKey.Named(NamedKey.ArrowDown), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_DPAD_DOWN, null))
        assertEquals(LogicalKey.Named(NamedKey.ArrowLeft), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_DPAD_LEFT, null))
        assertEquals(LogicalKey.Named(NamedKey.ArrowRight), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_DPAD_RIGHT, null))
        assertEquals(LogicalKey.Named(NamedKey.F1), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_F1, null))
        assertEquals(LogicalKey.Named(NamedKey.F12), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_F12, null))
    }

    @Test
    fun modifiersUseTheirSharedNameRegardlessOfSide() {
        assertEquals(LogicalKey.Named(NamedKey.Shift), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_SHIFT_LEFT, null))
        assertEquals(LogicalKey.Named(NamedKey.Shift), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_SHIFT_RIGHT, null))
        assertEquals(LogicalKey.Named(NamedKey.Control), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_CTRL_LEFT, null))
        assertEquals(LogicalKey.Named(NamedKey.Control), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_CTRL_RIGHT, null))
        assertEquals(LogicalKey.Named(NamedKey.Alt), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_ALT_LEFT, null))
        assertEquals(LogicalKey.Named(NamedKey.Alt), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_ALT_RIGHT, null))
        assertEquals(LogicalKey.Named(NamedKey.Meta), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_META_LEFT, null))
        assertEquals(LogicalKey.Named(NamedKey.Meta), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_META_RIGHT, null))
    }

    @Test
    fun theNamedTableWinsOverTheReliableCharacter() {
        // Espace : la table nommée donne Named(Space), exactement comme webLogicalKey(" ").
        assertEquals(LogicalKey.Named(NamedKey.Space), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_SPACE, ' '.code))
    }

    @Test
    fun reliableCharactersKeepExactlyWhatThePlatformReported() {
        // La casse est celle du caractère, pas celle de la touche : 'a' et 'A' diffèrent.
        assertEquals(LogicalKey.Character("a"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_A, 'a'.code))
        assertEquals(LogicalKey.Character("A"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_A, 'A'.code))
        assertEquals(LogicalKey.Character("1"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_1, '1'.code))
        assertEquals(LogicalKey.Character("é"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_A, 'é'.code))
    }

    @Test
    fun charactersOutsideTheTableStillCarryTheProducedCharacter() {
        // Une touche hors table qui PRODUIT un caractère (ponctuation IME) reste un caractère.
        assertEquals(LogicalKey.Character("'"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_APOSTROPHE, '\''.code))
        assertEquals(LogicalKey.Character(","), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_COMMA, ','.code))
        // Un astral (surrogate pair) est un caractère d'un seul code point.
        assertEquals(LogicalKey.Character("\uD83D\uDE00"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_APOSTROPHE, 0x1F600))
    }

    @Test
    fun keysWithNeitherNameNorCharacterStayUnidentified() {
        assertEquals(
            LogicalKey.Unidentified("android-24"),
            AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_VOLUME_UP, null),
        )
        // Une lettre sans unicode fiable n'invente pas de caractère.
        assertEquals(LogicalKey.Unidentified("android-54"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_Z, null))
        // Un code point invalide n'est pas fiable : il ne produit pas de caractère.
        assertEquals(LogicalKey.Unidentified("android-75"), AndroidKeyMapper.logicalKey(KeyEvent.KEYCODE_APOSTROPHE, -1))
    }

    // -----------------------------------------------------------------------
    // location — le côté du modificateur quand le metaState le dit sans ambiguïté
    // -----------------------------------------------------------------------

    @Test
    fun locationFollowsTheUnambiguousModifierSide() {
        assertEquals(
            KeyLocation.Left,
            AndroidKeyMapper.location(false, KeyEvent.META_SHIFT_LEFT_ON),
        )
        assertEquals(
            KeyLocation.Left,
            AndroidKeyMapper.location(false, KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON),
        )
        assertEquals(
            KeyLocation.Right,
            AndroidKeyMapper.location(false, KeyEvent.META_SHIFT_RIGHT_ON or KeyEvent.META_SHIFT_ON),
        )
        assertEquals(
            KeyLocation.Right,
            AndroidKeyMapper.location(false, KeyEvent.META_CTRL_RIGHT_ON),
        )
        assertEquals(
            KeyLocation.Left,
            AndroidKeyMapper.location(false, KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON),
        )
        assertEquals(
            KeyLocation.Left,
            AndroidKeyMapper.location(false, KeyEvent.META_META_LEFT_ON),
        )
    }

    @Test
    fun ambiguousOrAbsentSidesStayStandard() {
        assertEquals(KeyLocation.Standard, AndroidKeyMapper.location(false, 0))
        // Côté générique seul (SHIFT_ON sans flag L/R) : aucune position inventée.
        assertEquals(KeyLocation.Standard, AndroidKeyMapper.location(false, KeyEvent.META_SHIFT_ON))
        // Les deux côtés à la fois : indécidable → Standard.
        assertEquals(
            KeyLocation.Standard,
            AndroidKeyMapper.location(false, KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON),
        )
        assertEquals(
            KeyLocation.Standard,
            AndroidKeyMapper.location(false, KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_META_RIGHT_ON),
        )
        // Une touche système n'a jamais de position physique revendiquée.
        assertEquals(KeyLocation.Standard, AndroidKeyMapper.location(true, KeyEvent.META_SHIFT_LEFT_ON))
    }

    // -----------------------------------------------------------------------
    // modifiers — le metaState vers l'ensemble du modèle
    // -----------------------------------------------------------------------

    @Test
    fun anEmptyMetaStateCarriesNoModifier() {
        assertEquals(KeyboardModifiers(emptySet()), AndroidKeyMapper.modifiers(0))
    }

    @Test
    fun eachGenericMaskCarriesItsModifier() {
        assertEquals(setOf(ModifierKey.Shift), AndroidKeyMapper.modifiers(KeyEvent.META_SHIFT_ON).pressed)
        assertEquals(setOf(ModifierKey.Control), AndroidKeyMapper.modifiers(KeyEvent.META_CTRL_ON).pressed)
        assertEquals(setOf(ModifierKey.Alt), AndroidKeyMapper.modifiers(KeyEvent.META_ALT_ON).pressed)
        assertEquals(setOf(ModifierKey.Meta), AndroidKeyMapper.modifiers(KeyEvent.META_META_ON).pressed)
        assertEquals(setOf(ModifierKey.CapsLock), AndroidKeyMapper.modifiers(KeyEvent.META_CAPS_LOCK_ON).pressed)
        assertEquals(setOf(ModifierKey.NumLock), AndroidKeyMapper.modifiers(KeyEvent.META_NUM_LOCK_ON).pressed)
    }

    @Test
    fun sideFlagsAloneDoNotInventAModifier() {
        // Les masques génériques sont toujours posés à côté des flags L/R sur un vrai
        // événement ; un flag L/R orphelin ne suffit pas à déclarer un modificateur pressé.
        assertTrue(AndroidKeyMapper.modifiers(KeyEvent.META_SHIFT_LEFT_ON).pressed.isEmpty())
        assertTrue(AndroidKeyMapper.modifiers(KeyEvent.META_CTRL_RIGHT_ON).pressed.isEmpty())
    }

    @Test
    fun combinedMetaStatesCarryEveryActiveModifier() {
        val combined = KeyEvent.META_SHIFT_ON or
            KeyEvent.META_CTRL_ON or
            KeyEvent.META_CAPS_LOCK_ON or
            KeyEvent.META_NUM_LOCK_ON
        assertEquals(
            setOf(ModifierKey.Shift, ModifierKey.Control, ModifierKey.CapsLock, ModifierKey.NumLock),
            AndroidKeyMapper.modifiers(combined).pressed,
        )
    }
}
