package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.ScrollDelta
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared mapping core: browser primitives in, common Kadre input values out.
 *
 * Every case here drives the pure functions of `WebInputMapping.kt` with primitives only — a `code`
 * string as `KeyboardEvent.code` reports it, a `key` string as `KeyboardEvent.key` reports it, the
 * numeric `location`, the four modifier flags plus the two toggle states, `WheelEvent.deltaMode`,
 * the `MouseEvent.button` index, the pen angles and pressures, and the raw token the model validates.
 *
 * Two properties are asserted as much as the values: the functions are *total* (hostile browser input
 * is mapped, never thrown on) and never *approximate* (a value the model cannot carry is dropped to
 * `Unidentified(null)`/`null`, never clamped, rescaled or wrapped into range).
 */
class WebInputMappingTest {
    // --- Physical keys ---------------------------------------------------------------------------

    @Test
    fun theNamedKeysMapToTheirKeyboardKeypadUsages() {
        assertEquals(PhysicalKey.Code(0x07, 0x04), webPhysicalKey("KeyA"), "KeyA is HID 0x04")
        assertEquals(PhysicalKey.Code(0x07, 0x1e), webPhysicalKey("Digit1"), "Digit1 is HID 0x1E")
        assertEquals(PhysicalKey.Code(0x07, 0x50), webPhysicalKey("ArrowLeft"), "ArrowLeft is HID 0x50")
        assertEquals(PhysicalKey.Code(0x07, 0x58), webPhysicalKey("NumpadEnter"), "NumpadEnter is HID 0x58")
        assertEquals(PhysicalKey.Code(0x07, 0x45), webPhysicalKey("F12"), "F12 is HID 0x45")
        assertEquals(PhysicalKey.Code(0x07, 0x28), webPhysicalKey("Enter"), "Enter is HID 0x28")
        assertEquals(PhysicalKey.Code(0x07, 0x2c), webPhysicalKey("Space"), "Space is HID 0x2C")
    }

    @Test
    fun theLettersAndDigitsMapToTheirUsages() {
        listOf(
            "KeyA" to 0x04, "KeyB" to 0x05, "KeyC" to 0x06, "KeyD" to 0x07, "KeyE" to 0x08,
            "KeyF" to 0x09, "KeyG" to 0x0a, "KeyH" to 0x0b, "KeyI" to 0x0c, "KeyJ" to 0x0d,
            "KeyK" to 0x0e, "KeyL" to 0x0f, "KeyM" to 0x10, "KeyN" to 0x11, "KeyO" to 0x12,
            "KeyP" to 0x13, "KeyQ" to 0x14, "KeyR" to 0x15, "KeyS" to 0x16, "KeyT" to 0x17,
            "KeyU" to 0x18, "KeyV" to 0x19, "KeyW" to 0x1a, "KeyX" to 0x1b, "KeyY" to 0x1c,
            "KeyZ" to 0x1d,
            "Digit1" to 0x1e, "Digit2" to 0x1f, "Digit3" to 0x20, "Digit4" to 0x21, "Digit5" to 0x22,
            "Digit6" to 0x23, "Digit7" to 0x24, "Digit8" to 0x25, "Digit9" to 0x26, "Digit0" to 0x27,
        ).forEach { (code, usageId) ->
            assertEquals(PhysicalKey.Code(0x07, usageId), webPhysicalKey(code), "$code is HID $usageId")
        }
    }

    @Test
    fun thePunctuationKeysMapToTheirUsages() {
        listOf(
            "Minus" to 0x2d, "Equal" to 0x2e, "BracketLeft" to 0x2f, "BracketRight" to 0x30,
            "Backslash" to 0x31, "Semicolon" to 0x33, "Quote" to 0x34, "Backquote" to 0x35,
            "Comma" to 0x36, "Period" to 0x37, "Slash" to 0x38, "CapsLock" to 0x39,
            "IntlBackslash" to 0x64, "ContextMenu" to 0x65, "IntlRo" to 0x87, "IntlYen" to 0x89,
        ).forEach { (code, usageId) ->
            assertEquals(PhysicalKey.Code(0x07, usageId), webPhysicalKey(code), "$code is HID $usageId")
        }
    }

    @Test
    fun theNavigationAndSystemKeysMapToTheirUsages() {
        listOf(
            "Escape" to 0x29, "Tab" to 0x2b, "Backspace" to 0x2a, "PrintScreen" to 0x46,
            "ScrollLock" to 0x47, "Pause" to 0x48, "Insert" to 0x49, "Home" to 0x4a,
            "PageUp" to 0x4b, "Delete" to 0x4c, "End" to 0x4d, "PageDown" to 0x4e,
            "ArrowRight" to 0x4f, "ArrowLeft" to 0x50, "ArrowDown" to 0x51, "ArrowUp" to 0x52,
        ).forEach { (code, usageId) ->
            assertEquals(PhysicalKey.Code(0x07, usageId), webPhysicalKey(code), "$code is HID $usageId")
        }
    }

    @Test
    fun theFunctionRowMapsFromF1ToF24() {
        (1..12).forEach { number ->
            assertEquals(
                PhysicalKey.Code(0x07, 0x3a + (number - 1)),
                webPhysicalKey("F$number"),
                "F$number is HID ${0x3a + (number - 1)}",
            )
        }
        // The second block is not contiguous with the first in the usage table: 0x46..0x67 carries
        // the system, navigation, arrow and keypad keys, and F13 resumes at 0x68.
        (13..24).forEach { number ->
            assertEquals(
                PhysicalKey.Code(0x07, 0x68 + (number - 13)),
                webPhysicalKey("F$number"),
                "F$number is HID ${0x68 + (number - 13)}",
            )
        }
    }

    @Test
    fun theNumpadKeysMapToTheirUsages() {
        listOf(
            "NumLock" to 0x53, "NumpadDivide" to 0x54, "NumpadMultiply" to 0x55, "NumpadSubtract" to 0x56,
            "NumpadAdd" to 0x57, "NumpadEnter" to 0x58, "Numpad1" to 0x59, "Numpad2" to 0x5a,
            "Numpad3" to 0x5b, "Numpad4" to 0x5c, "Numpad5" to 0x5d, "Numpad6" to 0x5e,
            "Numpad7" to 0x5f, "Numpad8" to 0x60, "Numpad9" to 0x61, "Numpad0" to 0x62,
            "NumpadDecimal" to 0x63, "NumpadEqual" to 0x67, "NumpadComma" to 0x85,
        ).forEach { (code, usageId) ->
            assertEquals(PhysicalKey.Code(0x07, usageId), webPhysicalKey(code), "$code is HID $usageId")
        }
    }

    @Test
    fun theModifiersMapToDistinctLeftAndRightUsages() {
        listOf(
            "ShiftLeft" to 0xe1, "ShiftRight" to 0xe5,
            "ControlLeft" to 0xe0, "ControlRight" to 0xe4,
            "AltLeft" to 0xe2, "AltRight" to 0xe6,
            "MetaLeft" to 0xe3, "MetaRight" to 0xe7,
        ).forEach { (code, usageId) ->
            assertEquals(PhysicalKey.Code(0x07, usageId), webPhysicalKey(code), "$code is HID $usageId")
        }
        // The left/right pair is the substance of the entry: a table that collapsed it would still
        // satisfy the two assertions above in isolation, but not these.
        assertNotEquals(webPhysicalKey("ShiftLeft"), webPhysicalKey("ShiftRight"))
        assertNotEquals(webPhysicalKey("ControlLeft"), webPhysicalKey("ControlRight"))
        assertNotEquals(webPhysicalKey("AltLeft"), webPhysicalKey("AltRight"))
        assertNotEquals(webPhysicalKey("MetaLeft"), webPhysicalKey("MetaRight"))
        // The legacy names of the same keys are still reported by browsers in the field.
        assertEquals(PhysicalKey.Code(0x07, 0xe3), webPhysicalKey("OSLeft"), "OSLeft is the legacy MetaLeft")
        assertEquals(PhysicalKey.Code(0x07, 0xe7), webPhysicalKey("OSRight"), "OSRight is the legacy MetaRight")
    }

    @Test
    fun anUnknownCodeBecomesUnidentifiedWithoutATokenAndWithoutThrowing() {
        // An unmapped code carries no token, and a code that violates the model's stable-identifier
        // rule is never handed to the constructor that would validate and throw on it: both are
        // `Unidentified` without a token, because the physical mapping recognises a key by its code
        // and has nothing else to add.
        listOf(
            "KeyFoo", "Fn", "", " ", "é", "KeyA ", "日本語", "a b", "a\nb", "x".repeat(300), "\u0000", "𝄞",
        ).forEach { code ->
            assertEquals(
                PhysicalKey.Unidentified(null),
                webPhysicalKey(code),
                "an unmapped or non-conforming code carries no token: $code",
            )
        }
    }

    @Test
    fun hostileBrowserStringsMapWithoutEverThrowing() {
        val hostile = listOf(
            "", " ", "\n", "\t", "é", "𝄞", "a b", "Aé", "x".repeat(255), "x".repeat(256), "x".repeat(257),
            "KeyA ", " KeyA", "\u0000KeyA", "KeyA\u007f", "KeyA\u0080",
        )
        hostile.forEach { value ->
            // The call itself is the assertion: a mapping that let the model validate a raw token
            // would throw here and fail the test.
            webPhysicalKey(value)
            webLogicalKey(value)
            webNativeToken(value)
        }
    }

    // --- Logical keys ----------------------------------------------------------------------------

    @Test
    fun aSinglePrintableCharacterBecomesItsLogicalCharacterWithItsCasePreserved() {
        assertEquals(LogicalKey.Character("a"), webLogicalKey("a"))
        assertEquals(LogicalKey.Character("A"), webLogicalKey("A"))
        assertEquals(LogicalKey.Character("1"), webLogicalKey("1"))
        assertEquals(LogicalKey.Character("-"), webLogicalKey("-"))
        assertEquals(LogicalKey.Character("~"), webLogicalKey("~"))
        assertNotEquals(webLogicalKey("a"), webLogicalKey("A"), "the case is the logical key, never folded")
    }

    @Test
    fun theSpaceAndTheNamedKeysBecomeNamedLogicalKeys() {
        assertEquals(LogicalKey.Named(NamedKey.Space), webLogicalKey(" "))
        listOf(
            "Enter" to NamedKey.Enter,
            "Tab" to NamedKey.Tab,
            "Backspace" to NamedKey.Backspace,
            "Escape" to NamedKey.Escape,
            "Delete" to NamedKey.Delete,
            "Insert" to NamedKey.Insert,
            "Home" to NamedKey.Home,
            "End" to NamedKey.End,
            "PageUp" to NamedKey.PageUp,
            "PageDown" to NamedKey.PageDown,
            "ArrowLeft" to NamedKey.ArrowLeft,
            "ArrowRight" to NamedKey.ArrowRight,
            "ArrowUp" to NamedKey.ArrowUp,
            "ArrowDown" to NamedKey.ArrowDown,
            "Shift" to NamedKey.Shift,
            "Control" to NamedKey.Control,
            "Alt" to NamedKey.Alt,
            "Meta" to NamedKey.Meta,
            "CapsLock" to NamedKey.CapsLock,
            "NumLock" to NamedKey.NumLock,
            "ContextMenu" to NamedKey.ContextMenu,
            "F1" to NamedKey.F1,
            "F5" to NamedKey.F5,
            "F12" to NamedKey.F12,
            "MediaPlayPause" to NamedKey.MediaPlayPause,
            "MediaStop" to NamedKey.MediaStop,
            "MediaTrackNext" to NamedKey.MediaNext,
            "MediaTrackPrevious" to NamedKey.MediaPrevious,
            "AudioVolumeUp" to NamedKey.VolumeUp,
            "AudioVolumeDown" to NamedKey.VolumeDown,
            "AudioVolumeMute" to NamedKey.VolumeMute,
        ).forEach { (key, named) ->
            assertEquals(LogicalKey.Named(named), webLogicalKey(key), "the key $key is $named")
        }
        // The volume keys are still reported under their legacy names by browsers in the field.
        assertEquals(LogicalKey.Named(NamedKey.VolumeUp), webLogicalKey("VolumeUp"))
        assertEquals(LogicalKey.Named(NamedKey.VolumeDown), webLogicalKey("VolumeDown"))
        assertEquals(LogicalKey.Named(NamedKey.VolumeMute), webLogicalKey("VolumeMute"))
    }

    @Test
    fun aLongOrNonAsciiKeyBecomesUnidentifiedWithASanitisedToken() {
        assertEquals(LogicalKey.Unidentified("Dead"), webLogicalKey("Dead"))
        assertEquals(LogicalKey.Unidentified("Unidentified"), webLogicalKey("Unidentified"))
        assertEquals(LogicalKey.Unidentified("AltGraph"), webLogicalKey("AltGraph"))
        // Non-conforming tokens are dropped rather than carried: the model validates them, and the
        // mapping must not hand it a value it would reject.
        assertEquals(LogicalKey.Unidentified(null), webLogicalKey(""))
        assertEquals(LogicalKey.Unidentified(null), webLogicalKey("é"))
        assertEquals(LogicalKey.Unidentified(null), webLogicalKey("日本語"))
        assertEquals(LogicalKey.Unidentified(null), webLogicalKey("a b"))
        assertEquals(LogicalKey.Unidentified(null), webLogicalKey("x".repeat(257)))
        assertEquals(LogicalKey.Unidentified("x".repeat(256)), webLogicalKey("x".repeat(256)))
    }

    @Test
    fun theStableIdentifierRuleIsTheOneTheModelAppliesToAnOpaqueToken() {
        assertEquals("KeyA", webNativeToken("KeyA"))
        assertEquals("mac:12", webNativeToken("mac:12"))
        assertEquals("!", webNativeToken("!"))
        assertEquals("~", webNativeToken("~"))
        assertEquals("x".repeat(256), webNativeToken("x".repeat(256)))
        listOf(null, "", " ", "\t", "\n", "é", "a b", "𝄞", "x".repeat(257), "\u0000", "a\u007fb")
            .forEach { rejected -> assertNull(webNativeToken(rejected), "rejected token") }
    }

    // --- Location --------------------------------------------------------------------------------

    @Test
    fun theFourDomLocationsMapAndAnOutOfRangeOneFallsBackToStandard() {
        assertEquals(KeyLocation.Standard, webKeyLocation(0))
        assertEquals(KeyLocation.Left, webKeyLocation(1))
        assertEquals(KeyLocation.Right, webKeyLocation(2))
        assertEquals(KeyLocation.Numpad, webKeyLocation(3))
        listOf(4, 5, -1, Int.MIN_VALUE, Int.MAX_VALUE).forEach { location ->
            assertEquals(KeyLocation.Standard, webKeyLocation(location), "location $location is Standard")
        }
    }

    // --- Modifiers -------------------------------------------------------------------------------

    @Test
    fun everyModifierFlagMapsToExactlyItsOwnModifierAndNoneIsInvented() {
        assertEquals(KeyboardModifiers(emptySet()), modifiers())
        assertEquals(KeyboardModifiers(emptySet()), webKeyboardModifiers(false, false, false, false, false, false))
        assertEquals(KeyboardModifiers(setOf(ModifierKey.Shift)), modifiers(shift = true))
        assertEquals(KeyboardModifiers(setOf(ModifierKey.Control)), modifiers(control = true))
        assertEquals(KeyboardModifiers(setOf(ModifierKey.Alt)), modifiers(alt = true))
        assertEquals(KeyboardModifiers(setOf(ModifierKey.Meta)), modifiers(meta = true))
        assertEquals(KeyboardModifiers(setOf(ModifierKey.CapsLock)), modifiers(capsLock = true))
        assertEquals(KeyboardModifiers(setOf(ModifierKey.NumLock)), modifiers(numLock = true))
        assertEquals(
            KeyboardModifiers(
                setOf(ModifierKey.Shift, ModifierKey.Control, ModifierKey.Alt, ModifierKey.Meta),
            ),
            modifiers(shift = true, control = true, alt = true, meta = true),
            "the lock states are independent of the held modifiers",
        )
        // The argument order is the DOM's own reading order, so a port can pass them positionally.
        assertEquals(
            KeyboardModifiers(setOf(ModifierKey.Shift)),
            webKeyboardModifiers(true, false, false, false, false, false),
            "the first flag is shift",
        )
        assertEquals(
            KeyboardModifiers(setOf(ModifierKey.NumLock)),
            webKeyboardModifiers(false, false, false, false, false, true),
            "the last flag is NumLock",
        )
    }

    // --- Scroll ----------------------------------------------------------------------------------

    @Test
    fun pixelScrollKeepsTheBrowsersSignWhichIsKadresOwnPositiveDownAndRight() {
        // Kadre's convention is positive down and positive right, and `WheelEvent` reports
        // `DOM_DELTA_PIXEL` in that same orientation, so the mapping is a copy and not a negation:
        // this assertion is what fails if the mapping ever flipped a sign.
        assertEquals(ScrollDelta.Logical(3.0, 5.0), webScrollDelta(0, deltaX = 3.0, deltaY = 5.0))
        assertEquals(
            ScrollDelta.Logical(-3.0, -5.0),
            webScrollDelta(0, deltaX = -3.0, deltaY = -5.0),
            "scrolling up and left stays negative",
        )
        assertEquals(ScrollDelta.Logical(0.0, 0.0), webScrollDelta(0, deltaX = 0.0, deltaY = 0.0))
    }

    @Test
    fun lineScrollBecomesLinesAndPageScrollIsNeverDelivered() {
        assertEquals(ScrollDelta.Lines(0.0, 3.0), webScrollDelta(1, deltaX = 0.0, deltaY = 3.0))
        assertEquals(ScrollDelta.Lines(-2.0, 0.0), webScrollDelta(1, deltaX = -2.0, deltaY = 0.0))
        // `DOM_DELTA_PAGE` is a declared non-normalisable variant of this phase: converting pages to
        // logical units would need a page size Kadre does not own, so it is dropped, never
        // approximated (D9).
        assertNull(webScrollDelta(2, deltaX = 0.0, deltaY = 1.0), "a page delta is not delivered")
        listOf(3, -1, Int.MIN_VALUE, Int.MAX_VALUE).forEach { mode ->
            assertNull(webScrollDelta(mode, deltaX = 1.0, deltaY = 1.0), "deltaMode $mode is not delivered")
        }
    }

    @Test
    fun aNonFiniteScrollDeltaIsDroppedInsteadOfReachingTheModel() {
        // `ScrollDelta` requires finite components; the mapping must not hand it a value that would
        // throw, so a non-finite delta is dropped like any other unrepresentable observation.
        assertNull(webScrollDelta(0, deltaX = Double.NaN, deltaY = 0.0))
        assertNull(webScrollDelta(0, deltaX = 0.0, deltaY = Double.POSITIVE_INFINITY))
        assertNull(webScrollDelta(1, deltaX = Double.NEGATIVE_INFINITY, deltaY = 0.0))
        assertNull(webScrollDelta(0, deltaX = Double.NaN, deltaY = Double.NaN))
    }

    // --- Pointer buttons -------------------------------------------------------------------------

    @Test
    fun theStandardButtonIndicesMapAndAnUnknownOneIsNeverPrimary() {
        assertEquals(PointerButton.Primary, webPointerButton(0))
        assertEquals(PointerButton.Auxiliary, webPointerButton(1))
        assertEquals(PointerButton.Secondary, webPointerButton(2))
        assertEquals(PointerButton.Back, webPointerButton(3))
        assertEquals(PointerButton.Forward, webPointerButton(4))
        assertEquals(PointerButton.Other(5), webPointerButton(5))
        assertEquals(PointerButton.Other(-1), webPointerButton(-1))
        assertEquals(PointerButton.Other(Int.MAX_VALUE), webPointerButton(Int.MAX_VALUE))
        listOf(5, 6, -1, -2, Int.MIN_VALUE, Int.MAX_VALUE).forEach { button ->
            assertNotEquals(
                PointerButton.Primary,
                webPointerButton(button),
                "an unmapped button is never silently reported as the primary one",
            )
        }
    }

    @Test
    fun aPointerPressureOutsideItsDomainIsDroppedAndNeverThrownOn() {
        assertEquals(0.0, webPointerPressure(0.0))
        assertEquals(0.5, webPointerPressure(0.5))
        assertEquals(1.0, webPointerPressure(1.0))
        assertNull(webPointerPressure(null))
        listOf(-0.1, 1.1, 2.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            .forEach { pressure -> assertNull(webPointerPressure(pressure), "dropped pressure: $pressure") }
    }

    // --- Pen -------------------------------------------------------------------------------------

    @Test
    fun thePenAnglesAndTangentialPressureBecomeAValidPenState() {
        val state = webPenState(
            tiltXDegrees = 30.0,
            tiltYDegrees = -45.0,
            twistDegrees = 90.0,
            tangentialPressure = 0.25,
        )
        assertEquals(30.0, state?.tiltXDegrees)
        assertEquals(-45.0, state?.tiltYDegrees)
        assertEquals(PI / 2.0, state?.twistRadians ?: 0.0, 1e-12, "the twist is converted from degrees")
        assertEquals(0.25, state?.tangentialPressure)

        val zeroed = webPenState(
            tiltXDegrees = 0.0,
            tiltYDegrees = 0.0,
            twistDegrees = 0.0,
            tangentialPressure = 0.0,
        )
        assertEquals(PenState(tiltXDegrees = 0.0, tiltYDegrees = 0.0, twistRadians = 0.0, tangentialPressure = 0.0), zeroed)

        val almostFullTurn = webPenState(
            tiltXDegrees = null,
            tiltYDegrees = null,
            twistDegrees = 359.99999999999994,
            tangentialPressure = null,
        )
        val twist = almostFullTurn?.twistRadians
        assertTrue(twist != null && twist < 2.0 * PI, "a pen twist stays inside [0, 2π): $twist")
        assertEquals(2.0 * PI, twist, 1e-9, "the last representable degree is a full turn, not 2π")
    }

    @Test
    fun aPenValueOutsideItsDomainIsDroppedRatherThanTransmittedOrThrown() {
        val outOfDomain = webPenState(
            tiltXDegrees = 120.0,
            tiltYDegrees = -30.0,
            twistDegrees = 360.0,
            tangentialPressure = 2.0,
        )
        assertNull(outOfDomain?.tiltXDegrees, "a tilt beyond 90 degrees is not a tilt")
        assertEquals(-30.0, outOfDomain?.tiltYDegrees, "its valid sibling is still carried")
        assertNull(outOfDomain?.twistRadians, "a twist of a full turn is outside [0, 2π)")
        assertNull(outOfDomain?.tangentialPressure, "a tangential pressure outside [-1, 1] is dropped")

        listOf(-91.0, 90.5, Double.NaN, Double.POSITIVE_INFINITY).forEach { tilt ->
            assertNull(webPenState(tilt, null, null, null)?.tiltXDegrees, "dropped tilt: $tilt")
        }
        listOf(-1.0, -0.001, 360.0, 720.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { twist ->
            assertNull(webPenState(null, null, twist, null)?.twistRadians, "dropped twist: $twist")
        }
        listOf(-1.5, 1.0001, Double.NaN, Double.NEGATIVE_INFINITY).forEach { tangential ->
            assertNull(webPenState(null, null, null, tangential)?.tangentialPressure, "dropped: $tangential")
        }

        // A pointer that observed nothing pen-like carries no pen state at all, rather than an empty
        // record the caller would have to interpret.
        assertNull(webPenState(null, null, null, null))
    }

    private companion object {
        /**
         * The modifier primitive of the mapping, in its declared order, so a case can name only the
         * flags it sets while one case still asserts the positional order directly.
         */
        fun modifiers(
            shift: Boolean = false,
            control: Boolean = false,
            alt: Boolean = false,
            meta: Boolean = false,
            capsLock: Boolean = false,
            numLock: Boolean = false,
        ): KeyboardModifiers = webKeyboardModifiers(shift, control, alt, meta, capsLock, numLock)
    }
}
