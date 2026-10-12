package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.PI
import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.SurfaceId

/**
 * Le kernel de mapping input pur (Task 1) : primitives en forme UIKit (sans type UIKit) → valeurs
 * communes du modèle d'input. Miroir du contrat de `WebInputMapping` : totalité (aucune fonction ne
 * jette), pas d'approximation (une primitive non représentable est perdue, jamais bornée ni
 * rescalée).
 */
class UIKitInputMappingTest {
    // --------------------------------------------------------------------------------------------
    // uikitHidKey : la table HID porte la paire (usagePage, usageId) telle que rapportée.
    // --------------------------------------------------------------------------------------------

    @Test
    fun hidKeyCarriesTheReportedUsagePairAsCode() {
        // Page 0x07 (Keyboard/Keypad) : la lettre A. Page 0x0C (Consumer) : Volume Up.
        // Page 0x01 (Generic Desktop) : case non clavier — la page rapportée est portée, jamais
        // forcée en 0x07 (GCKeyboard délivre la page du GCKey, media keys comprises).
        assertEquals(PhysicalKey.Code(0x07, 0x04), uikitHidKey(0x07, 0x04))
        assertEquals(PhysicalKey.Code(0x0C, 0xE9), uikitHidKey(0x0C, 0xE9))
        assertEquals(PhysicalKey.Code(0x01, 0x30), uikitHidKey(0x01, 0x30))
    }

    @Test
    fun hidKeyAcceptsTheFullUnsignedSixteenBitDomain() {
        assertEquals(PhysicalKey.Code(0, 0), uikitHidKey(0, 0))
        assertEquals(PhysicalKey.Code(65_535, 65_535), uikitHidKey(65_535, 65_535))
    }

    @Test
    fun hidKeyOutsideTheUsageDomainIsUnidentifiedWithoutToken() {
        // Une paire hors domaine HID n'a pas de token honnête à porter : `Unidentified(null)`,
        // jamais un usage tronqué ni une page réinventée.
        assertEquals(PhysicalKey.Unidentified(null), uikitHidKey(-1, 0x04))
        assertEquals(PhysicalKey.Unidentified(null), uikitHidKey(0x07, -1))
        assertEquals(PhysicalKey.Unidentified(null), uikitHidKey(65_536, 0x04))
        assertEquals(PhysicalKey.Unidentified(null), uikitHidKey(0x07, 65_536))
        assertEquals(PhysicalKey.Unidentified(null), uikitHidKey(Int.MIN_VALUE, Int.MAX_VALUE))
    }

    // --------------------------------------------------------------------------------------------
    // uikitLogicalKey : les caractères d'une touche clavier (GCKeyboard, UIWindow key commands).
    // --------------------------------------------------------------------------------------------

    @Test
    fun logicalKeyNamesTheControlCharactersTheKeyboardReports() {
        assertEquals(LogicalKey.Named(NamedKey.Enter), uikitLogicalKey("\r"))
        assertEquals(LogicalKey.Named(NamedKey.Tab), uikitLogicalKey("\t"))
        assertEquals(LogicalKey.Named(NamedKey.Space), uikitLogicalKey(" "))
        assertEquals(LogicalKey.Named(NamedKey.Escape), uikitLogicalKey("\u001B"))
        assertEquals(LogicalKey.Named(NamedKey.Backspace), uikitLogicalKey("\u007F"))
    }

    @Test
    fun logicalKeyNamesTheNavigationFunctionKeys() {
        // Bloc NSFunctionKey vérifié dans le SDK (NSEvent.h 0xF700..) : c'est le bloc qu'UIKit
        // livre via les constantes UIKeyInput* (UIKeyInputUpArrow = 0xF700, etc.).
        assertEquals(LogicalKey.Named(NamedKey.ArrowUp), uikitLogicalKey("\uF700"))
        assertEquals(LogicalKey.Named(NamedKey.ArrowDown), uikitLogicalKey("\uF701"))
        assertEquals(LogicalKey.Named(NamedKey.ArrowLeft), uikitLogicalKey("\uF702"))
        assertEquals(LogicalKey.Named(NamedKey.ArrowRight), uikitLogicalKey("\uF703"))
        assertEquals(LogicalKey.Named(NamedKey.Delete), uikitLogicalKey("\uF728")) // DeleteForward (0xF728)
        assertEquals(LogicalKey.Named(NamedKey.Home), uikitLogicalKey("\uF729"))
        assertEquals(LogicalKey.Named(NamedKey.End), uikitLogicalKey("\uF72B"))
        assertEquals(LogicalKey.Named(NamedKey.PageUp), uikitLogicalKey("\uF72C"))
        assertEquals(LogicalKey.Named(NamedKey.PageDown), uikitLogicalKey("\uF72D"))
    }

    @Test
    fun logicalKeyNamesTheFunctionRow() {
        assertEquals(LogicalKey.Named(NamedKey.F1), uikitLogicalKey("\uF704"))
        assertEquals(LogicalKey.Named(NamedKey.F2), uikitLogicalKey("\uF705"))
        assertEquals(LogicalKey.Named(NamedKey.F12), uikitLogicalKey("\uF70F"))
    }

    @Test
    fun logicalKeyKeepsASingleCharacterWithItsCase() {
        assertEquals(LogicalKey.Character("a"), uikitLogicalKey("a"))
        assertEquals(LogicalKey.Character("A"), uikitLogicalKey("A"))
        assertEquals(LogicalKey.Character("é"), uikitLogicalKey("é"))
        assertEquals(LogicalKey.Character("1"), uikitLogicalKey("1"))
    }

    @Test
    fun logicalKeyCountsAstralCharactersAsOneCharacter() {
        // Un astral est un caractère livré en paire de substitution : un caractère quand même.
        assertEquals(LogicalKey.Character("🌉"), uikitLogicalKey("🌉"))
    }

    @Test
    fun logicalKeyUnidentifiedCarriesTheValueWhenItCanBeAToken() {
        // Une valeur multicharactère sans nom dans le modèle reste une observation : le token est
        // la valeur elle-même quand elle est un identifiant ASCII acceptable (miroir web).
        assertEquals(LogicalKey.Unidentified("Compose"), uikitLogicalKey("Compose"))
    }

    @Test
    fun logicalKeyUnidentifiedLosesValuesThatCannotBeTokens() {
        assertEquals(LogicalKey.Unidentified(null), uikitLogicalKey(""))
        assertEquals(LogicalKey.Unidentified(null), uikitLogicalKey(null))
        // Espace intérieur + non-ASCII : hors domaine des tokens.
        assertEquals(LogicalKey.Unidentified(null), uikitLogicalKey("a b"))
        assertEquals(LogicalKey.Unidentified(null), uikitLogicalKey("éé"))
    }

    @Test
    fun nativeTokenLengthBoundIsTwoHundredFiftySixCodeUnits() {
        // La borne de longueur de `uikitNativeToken` est 256 unités de code (le domaine que le
        // modèle valide) : un token de 256 caractères ASCII valides reste LUI-MÊME — identité
        // préservée, au kernel comme dans la clé logique qu'il porte ; 257 n'a plus de token
        // honnête : `Unidentified(null)`, jamais une troncature. Cible de mutation : la borne
        // déplacée (`< 256` tue la première moitié, `<= 257` tue la seconde).
        val atBound = "k".repeat(256)
        assertEquals(atBound, uikitNativeToken(atBound))
        assertEquals(LogicalKey.Unidentified(atBound), uikitLogicalKey(atBound))
        val beyondBound = "k".repeat(257)
        assertNull(uikitNativeToken(beyondBound))
        assertEquals(LogicalKey.Unidentified(null), uikitLogicalKey(beyondBound))
    }

    // --------------------------------------------------------------------------------------------
    // uikitKeyState + uikitKeyboardModifiers : les booléens du binding → état du modèle.
    // --------------------------------------------------------------------------------------------

    @Test
    fun keyStateFollowsThePressedFlag() {
        assertEquals(KeyState.Pressed, uikitKeyState(true))
        assertEquals(KeyState.Released, uikitKeyState(false))
    }

    @Test
    fun keyboardModifiersCarriesExactlyTheActiveFlags() {
        assertEquals(emptySet(), uikitKeyboardModifiers(false, false, false, false, false, false).pressed)
        assertEquals(
            setOf(ModifierKey.Shift, ModifierKey.Control),
            uikitKeyboardModifiers(true, true, false, false, false, false).pressed,
        )
        assertEquals(
            setOf(
                ModifierKey.Shift,
                ModifierKey.Control,
                ModifierKey.Alt,
                ModifierKey.Meta,
                ModifierKey.CapsLock,
                ModifierKey.NumLock,
            ),
            uikitKeyboardModifiers(true, true, true, true, true, true).pressed,
        )
        assertEquals(
            setOf(ModifierKey.CapsLock),
            uikitKeyboardModifiers(false, false, false, false, true, false).pressed,
        )
    }

    // --------------------------------------------------------------------------------------------
    // uikitTouchPhase : 4/4, un à un.
    // --------------------------------------------------------------------------------------------

    @Test
    fun touchPhaseMapsAllFourPhasesOneToOne() {
        assertEquals(TouchPhase.Started, uikitTouchPhase(UIKitTouchPhase.Began))
        assertEquals(TouchPhase.Moved, uikitTouchPhase(UIKitTouchPhase.Moved))
        assertEquals(TouchPhase.Ended, uikitTouchPhase(UIKitTouchPhase.Ended))
        assertEquals(TouchPhase.Cancelled, uikitTouchPhase(UIKitTouchPhase.Cancelled))
    }

    // --------------------------------------------------------------------------------------------
    // uikitPressure : hors [0,1] → null, jamais borné (précédent web/appkit : pas d'approximation).
    // --------------------------------------------------------------------------------------------

    @Test
    fun pressureKeepsTheReportedValueInsideTheDomain() {
        assertNull(uikitPressure(null))
        assertEquals(0.0, uikitPressure(0.0))
        assertEquals(1.0, uikitPressure(1.0))
        assertEquals(0.42, uikitPressure(0.42))
    }

    @Test
    fun pressureOutsideTheDomainIsDroppedNotClamped() {
        assertNull(uikitPressure(-0.01))
        assertNull(uikitPressure(1.01))
        assertNull(uikitPressure(Double.NaN))
        assertNull(uikitPressure(Double.POSITIVE_INFINITY))
        assertNull(uikitPressure(Double.NEGATIVE_INFINITY))
    }

    // --------------------------------------------------------------------------------------------
    // uikitPointerKind + uikitIsStylus : le stylet est un Pen, le hover est un Mouse.
    // --------------------------------------------------------------------------------------------

    @Test
    fun pointerKindMapsStylusToPenAndHoverToMouse() {
        assertEquals(PointerKind.Pen, uikitPointerKind(isStylus = true, isHover = false))
        // Le stylet gagne : une observation de stylet reste un Pen même hover.
        assertEquals(PointerKind.Pen, uikitPointerKind(isStylus = true, isHover = true))
        assertEquals(PointerKind.Mouse, uikitPointerKind(isStylus = false, isHover = true))
        // Un contact direct n'est pas un pointeur : la route de la source est le path touch —
        // la valeur Unknown existe pour l'observation d'un périphérique resté sans nom.
        assertEquals(PointerKind.Unknown, uikitPointerKind(isStylus = false, isHover = false))
    }

    @Test
    fun stylusDetectionFollowsTheSdkRawValuesOfUITouchType() {
        // Valeurs brutes vérifiées dans le SDK (UITouch.h) : Direct=0, Indirect=1, Pencil=2,
        // Stylus=ALIAS DE PENCIL (donc aussi 2), IndirectPointer=3.
        assertFalse(uikitIsStylus(0)) // UITouchTypeDirect — un doigt
        assertFalse(uikitIsStylus(1)) // UITouchTypeIndirect
        assertTrue(uikitIsStylus(2)) // UITouchTypePencil (UITouchTypeStylus = même valeur brute)
        assertFalse(uikitIsStylus(3)) // UITouchTypeIndirectPointer — pointeur indirect, PAS un stylet
        assertFalse(uikitIsStylus(4)) // valeur future inconnue
        assertFalse(uikitIsStylus(-1)) // hors domaine
    }

    // --------------------------------------------------------------------------------------------
    // uikitScrollDelta : le scroll UIKit est en points — Logical seul.
    // --------------------------------------------------------------------------------------------

    @Test
    fun scrollDeltaDeliversLogicalDeltasAsReported() {
        assertEquals(ScrollDelta.Logical(3.0, -12.5), uikitScrollDelta(3.0, -12.5))
        assertEquals(ScrollDelta.Logical(0.0, 0.0), uikitScrollDelta(0.0, 0.0))
    }

    @Test
    fun scrollDeltaDropsNonFiniteComponents() {
        assertNull(uikitScrollDelta(Double.NaN, 1.0))
        assertNull(uikitScrollDelta(1.0, Double.NEGATIVE_INFINITY))
    }

    // --------------------------------------------------------------------------------------------
    // uikitGesture : les invariants de payload de `validateGesture` (foundation), appliqués en
    // amont — une observation hors invariant vaut `null`, jamais un stimulus que le reducer
    // rejetterait silencieusement.
    // --------------------------------------------------------------------------------------------

    @Test
    fun panGestureCarriesTheTranslationAsItsOnlyPayload() {
        val stimulus = uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationX = 1.5, translationY = -2.0))
        assertEquals(GestureKind.Pan, stimulus?.kind)
        assertEquals(TouchPhase.Moved, stimulus?.phase)
        assertEquals(LogicalDelta(1.5, -2.0), stimulus?.delta)
        assertNull(stimulus?.scale)
        assertNull(stimulus?.rotationRadians)
        assertNull(stimulus?.pressure)
        assertEquals(surfaceId, stimulus?.surfaceId)
    }

    @Test
    fun panGestureWithoutACompleteTranslationIsDropped() {
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationX = 1.0)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationY = 1.0)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved)))
        // Un payload étranger au kind rend l'observation hors invariant.
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationX = 1.0, translationY = 1.0, scale = 1.0)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationX = 1.0, translationY = 1.0, rotation = 0.1)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationX = 1.0, translationY = 1.0, pressure = 0.5)))
    }

    @Test
    fun pinchGestureCarriesAPositiveScaleAsItsOnlyPayload() {
        val stimulus = uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pinch, UIKitTouchPhase.Began, scale = 1.5))
        assertEquals(GestureKind.Pinch, stimulus?.kind)
        assertEquals(TouchPhase.Started, stimulus?.phase)
        assertNull(stimulus?.delta)
        assertEquals(1.5, stimulus?.scale)
        assertNull(stimulus?.rotationRadians)
        assertNull(stimulus?.pressure)
    }

    @Test
    fun pinchGestureWithANonPositiveOrNonFiniteScaleIsDropped() {
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pinch, UIKitTouchPhase.Moved, scale = 0.0)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pinch, UIKitTouchPhase.Moved, scale = -1.0)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pinch, UIKitTouchPhase.Moved, scale = Double.NaN)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pinch, UIKitTouchPhase.Moved)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pinch, UIKitTouchPhase.Moved, scale = 1.5, translationX = 1.0, translationY = 1.0)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Pinch, UIKitTouchPhase.Moved, scale = 1.5, pressure = 0.5)))
    }

    @Test
    fun rotationGestureCarriesFiniteRadiansAsItsOnlyPayload() {
        val stimulus = uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Rotation, UIKitTouchPhase.Ended, rotation = PI / 4))
        assertEquals(GestureKind.Rotation, stimulus?.kind)
        assertEquals(PI / 4, stimulus?.rotationRadians)
        assertNull(stimulus?.delta)
        assertNull(stimulus?.scale)
        assertNull(stimulus?.pressure)
    }

    @Test
    fun rotationGestureWithANonFiniteOrForeignPayloadIsDropped() {
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Rotation, UIKitTouchPhase.Moved, rotation = Double.NaN)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Rotation, UIKitTouchPhase.Moved, rotation = Double.POSITIVE_INFINITY)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Rotation, UIKitTouchPhase.Moved)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.Rotation, UIKitTouchPhase.Moved, rotation = 0.1, scale = 1.0)))
    }

    @Test
    fun touchpadPressureGestureCarriesPressureInsideTheDomainAsItsOnlyPayload() {
        val stimulus = uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.TouchpadPressure, UIKitTouchPhase.Began, pressure = 0.7))
        assertEquals(GestureKind.TouchpadPressure, stimulus?.kind)
        assertEquals(0.7, stimulus?.pressure)
        assertNull(stimulus?.delta)
        assertNull(stimulus?.scale)
        assertNull(stimulus?.rotationRadians)
    }

    @Test
    fun touchpadPressureGestureOutsideTheDomainIsDropped() {
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.TouchpadPressure, UIKitTouchPhase.Moved, pressure = 1.7)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.TouchpadPressure, UIKitTouchPhase.Moved, pressure = -0.5)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.TouchpadPressure, UIKitTouchPhase.Moved, pressure = Double.NaN)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.TouchpadPressure, UIKitTouchPhase.Moved)))
    }

    @Test
    fun doubleTapGestureCarriesNoPayloadAtAll() {
        val stimulus = uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.DoubleTap, UIKitTouchPhase.Ended))
        assertEquals(GestureKind.DoubleTap, stimulus?.kind)
        assertEquals(TouchPhase.Ended, stimulus?.phase)
        assertNull(stimulus?.delta)
        assertNull(stimulus?.scale)
        assertNull(stimulus?.rotationRadians)
        assertNull(stimulus?.pressure)
        // Le moindre payload rend un DoubleTap hors invariant.
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.DoubleTap, UIKitTouchPhase.Ended, scale = 1.0)))
        assertNull(uikitGesture(surfaceId, UIKitGesturePrimitive(UIKitGestureKind.DoubleTap, UIKitTouchPhase.Ended, pressure = 0.1)))
    }

    @Test
    fun gesturePrimitiveRouteAndFlatRouteAgree() {
        val primitive = UIKitGesturePrimitive(UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationX = 3.0, translationY = 4.0)
        val byCarrier = uikitGesture(surfaceId, primitive)
        val flat = uikitGesture(surfaceId, UIKitGestureKind.Pan, UIKitTouchPhase.Moved, translationX = 3.0, translationY = 4.0, scale = null, rotation = null, pressure = null)
        assertEquals(byCarrier, flat)
        // Identités distinctes : le stimulus porte la surface demandée, pas une autre.
        assertNotEquals(otherSurfaceId, byCarrier?.surfaceId)
    }

    @Test
    fun flatGestureRouteAppliesTheSameInvariants() {
        assertNull(uikitGesture(surfaceId, UIKitGestureKind.Pinch, UIKitTouchPhase.Began, translationX = null, translationY = null, scale = 0.0, rotation = null, pressure = null))
        assertNull(uikitGesture(surfaceId, UIKitGestureKind.Pan, UIKitTouchPhase.Began, translationX = null, translationY = null, scale = null, rotation = null, pressure = null))
    }

    private val surfaceId: SurfaceId = RuntimeProcessIds.nextSurfaceId()
    private val otherSurfaceId: SurfaceId = RuntimeProcessIds.nextSurfaceId()
}
