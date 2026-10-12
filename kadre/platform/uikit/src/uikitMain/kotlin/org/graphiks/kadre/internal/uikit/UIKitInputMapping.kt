package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.GestureKind
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId

/**
 * Le kernel de mapping input des cibles iOS et tvOS : primitives en forme UIKit entrantes, valeurs
 * communes du modèle d'input sortantes.
 *
 * C'est le miroir d'iOS/tvOS de `WebInputMapping` : les deux ports d'une famille (iOS et tvOS)
 * lisent leurs propres objets natifs et appellent ces fonctions, de sorte que la famille ne peut pas
 * diverger sur quelle paire HID une touche porte, quel caractère nomme quelle clé, ou quel geste
 * porte quelle mesure. Rien ici n'importe un type UIKit, et chaque paramètre est une primitive
 * (`Int`, `Double`, `Boolean`, `String`) ou une primitive du kernel — c'est ce qui rend le core
 * testable dans `uikitTest`, la suite que les quatre cibles natives partagent, sans appareil.
 *
 * Deux propriétés font partie du contrat, héritées du template Web :
 *
 * - **Totalité.** Chaque fonction est définie pour toute entrée qu'UIKit peut lui remettre, y
 *   compris hostile et hors domaine. Un mapping ne jette jamais : là où le modèle valide un domaine,
 *   le kernel produit une valeur que le modèle accepte, ou `null` quand il n'y en a pas.
 * - **Pas d'approximation.** Une primitive que le modèle ne peut pas porter est perdue, jamais
 *   bornée, rescalée ni remisée dans le domaine ; une primitive que le modèle peut porter est gardée
 *   telle que la source l'a rapportée. Une observation non représentable devient `null`, afin que
 *   l'absence reste visible comme une absence plutôt que comme un nombre que Kadre a inventé.
 *
 * Les invariants de payload des gestes sont appliqués ICI, en amont du runtime : un stimulus hors
 * invariant serait réduit par rien au moment de la publication — une observation perdue
 * silencieusement. Le kernel perd explicitement l'observation (`null`), comme `webPenStateFor` perd
 * un état de plume qui n'appartient pas à une plume.
 */

/**
 * Mappe la paire HID qu'une touche rapporte sur la clé physique du modèle.
 *
 * La paire est portée telle quelle — [uikitHidKey] ne force aucune page : un `GCKey` de page 0x07
 * (Keyboard/Keypad) et une media key de page 0x0C (Consumer) sont deux clés physiques distinctes et
 * le modèle les distingue par la page. Une paire qui tient dans deux entiers non signés 16 bits est
 * un `Code` ; tout le reste (page ou usage négatif, l'un ou l'autre au-delà de 0xFFFF) n'est pas une
 * paire HID et n'a pas de token honnête à porter : `Unidentified(null)`, jamais un usage tronqué ni
 * une page réinventée.
 */
internal fun uikitHidKey(usagePage: Int, usageId: Int): PhysicalKey =
    if (usagePage in 0..65_535 && usageId in 0..65_535) {
        PhysicalKey.Code(usagePage = usagePage, usageId = usageId)
    } else {
        PhysicalKey.Unidentified(null)
    }

/**
 * Mappe les caractères qu'une touche clavier produit sur la clé logique du modèle.
 *
 * Les caractères sont la chaîne que la source a rapportée (`GCKey.characters`, ou les constantes
 * `UIKeyInput*` d'un event clavier UIKit). Les caractères de contrôle et du bloc des touches de
 * fonction que le modèle nomme gardent leur nom — le tableau plus bas couvre Return, Tab, Escape,
 * l'espace, le backspace, les quatre flèches, la rangée F1..F12 et les touches d'édition. Une valeur
 * d'exactement un caractère (compté en points de code, donc un astral livré en paire de substitution
 * aussi) est le caractère que la touche a produit et garde sa casse. Tout le reste — une valeur
 * multicharactère sans nom dans le modèle, une valeur vide, une absence — devient `Unidentified`,
 * avec la valeur assainie au token que le modèle accepte quand elle peut en être un, et sans token
 * quand elle ne le peut pas (mêmes règles que le token natif du Web).
 */
internal fun uikitLogicalKey(characters: String?): LogicalKey {
    uikitNamedKeyForCharacters(characters)?.let { named -> return LogicalKey.Named(named) }
    if (characters != null && characters.isSingleCharacter()) return LogicalKey.Character(characters)
    return LogicalKey.Unidentified(uikitNativeToken(characters))
}

/**
 * Les caractères de touche que le modèle nomme.
 *
 * Sources vérifiées dans le SDK : les caractères de contrôle sont ceux du texte système — `\r` est
 * le Return (`NSCarriageReturnCharacter`), `\t` la Tab, `0x1B` l'Escape et `0x7F` le DEL que produit
 * la touche backspace (`NSDeleteCharacter`, NSText.h). Le bloc `0xF700..0xF72D` est celui des
 * constantes `NSFunctionKey` (NSEvent.h) qu'UIKit livre via `UIKeyInputUpArrow` et ses voisines.
 */
private fun uikitNamedKeyForCharacters(characters: String?): NamedKey? = when (characters) {
    "\r" -> NamedKey.Enter
    "\t" -> NamedKey.Tab
    " " -> NamedKey.Space
    "\u001B" -> NamedKey.Escape
    "\u007F" -> NamedKey.Backspace
    "\uF700" -> NamedKey.ArrowUp
    "\uF701" -> NamedKey.ArrowDown
    "\uF702" -> NamedKey.ArrowLeft
    "\uF703" -> NamedKey.ArrowRight
    "\uF728" -> NamedKey.Delete // suppression vers l'avant (NSDeleteFunctionKey)
    "\uF729" -> NamedKey.Home
    "\uF72B" -> NamedKey.End
    "\uF72C" -> NamedKey.PageUp
    "\uF72D" -> NamedKey.PageDown
    "\uF704" -> NamedKey.F1
    "\uF705" -> NamedKey.F2
    "\uF706" -> NamedKey.F3
    "\uF707" -> NamedKey.F4
    "\uF708" -> NamedKey.F5
    "\uF709" -> NamedKey.F6
    "\uF70A" -> NamedKey.F7
    "\uF70B" -> NamedKey.F8
    "\uF70C" -> NamedKey.F9
    "\uF70D" -> NamedKey.F10
    "\uF70E" -> NamedKey.F11
    "\uF70F" -> NamedKey.F12
    else -> null
}

/** La valeur est un caractère, compté en points de code : un astral est une paire de substitution. */
private fun String.isSingleCharacter(): Boolean =
    length == 1 || (length == 2 && this[0].isHighSurrogate() && this[1].isLowSurrogate())

/**
 * Assainit un token natif opaque à la règle que le modèle lui valide : un identifiant ASCII non vide
 * d'au plus 256 unités de code, chacune dans `0x21..0x7e`.
 *
 * Une valeur qui ne peut pas être un tel token est `null` plutôt qu'une approximation tronquée ou
 * translittérée d'elle-même.
 */
internal fun uikitNativeToken(value: String?): String? =
    value?.takeIf { token -> token.isNotEmpty() && token.length <= 256 && token.all { it.code in UIKIT_TOKEN_CHARACTERS } }

/**
 * Mappe l'appui ou le relâchement qu'une source rapporte sur l'état de touche du modèle.
 *
 * `GCKeyboard.keyChangedHandler` rapporte un booléen ; l'état du modèle est exactement ce booléen,
 * sans dérivé : `true` est un appui, `false` un relâchement.
 */
internal fun uikitKeyState(pressed: Boolean): KeyState =
    if (pressed) KeyState.Pressed else KeyState.Released

/**
 * Mappe les drapeaux modificateurs d'une source clavier sur l'ensemble du modèle.
 *
 * Un drapeau faux ne contribue aucun modificateur : l'ensemble est exactement ce que la source a
 * rapporté comme actif, rien n'est inventé et rien n'est par défaut (miroir de `webKeyboardModifiers`).
 */
internal fun uikitKeyboardModifiers(
    shift: Boolean,
    control: Boolean,
    alt: Boolean,
    meta: Boolean,
    capsLock: Boolean,
    numLock: Boolean,
): KeyboardModifiers = KeyboardModifiers(
    buildSet {
        if (shift) add(ModifierKey.Shift)
        if (control) add(ModifierKey.Control)
        if (alt) add(ModifierKey.Alt)
        if (meta) add(ModifierKey.Meta)
        if (capsLock) add(ModifierKey.CapsLock)
        if (numLock) add(ModifierKey.NumLock)
    },
)

/**
 * Mappe la phase d'un contact ou d'un geste UIKit sur la phase du modèle.
 *
 * Les quatre phases d'`UITouchPhase` (et les quatre états terminaux d'un recognizer) sont les quatre
 * phases du modèle, une à une : le mapper est total, aucune phase ne se perd et aucune ne se
 * transforme en une autre. C'est le même mapper pour les touches et pour les gestes — un recognizer
 * livre les mêmes phases qu'un contact — ce qui est pourquoi la primitive geste porte la même
 * [UIKitTouchPhase] et pourquoi il n'y a pas de `uikitGesturePhase` séparé.
 */
internal fun uikitTouchPhase(phase: UIKitTouchPhase): TouchPhase = when (phase) {
    UIKitTouchPhase.Began -> TouchPhase.Started
    UIKitTouchPhase.Moved -> TouchPhase.Moved
    UIKitTouchPhase.Ended -> TouchPhase.Ended
    UIKitTouchPhase.Cancelled -> TouchPhase.Cancelled
}

/**
 * Mappe l'état BRUT d'un recognizer de geste sur la phase du modèle, ou `null` quand l'état n'est
 * pas une observation transmissible.
 *
 * Les valeurs brutes sont celles d'`UIGestureRecognizerState` (UIGestureRecognizer.h, vérifiées dans
 * le SDK) : `Possible` = 0, `Began` = 1, `Changed` = 2, `Ended` = 3 — avec `Recognized`, l'alias des
 * gestes discrets, ÉGAL à `Ended` (un tap n'archive jamais Began/Changed : il arrive `Ended`) —
 * `Cancelled` = 4 et `Failed` = 5. Le klib livre l'état en `Long` brut (la même forme que
 * `UITouch.type`).
 *
 * La politique des gestes reconnus (décision tracée du plan) : seules les trois phases PORTABLES
 * passent — [UIKitTouchPhase.Began], [UIKitTouchPhase.Moved], [UIKitTouchPhase.Ended].
 * `Possible` n'est pas une observation (l'état de repos d'un recognizer attaché). `Cancelled` et
 * `Failed` n'ont PAS de chemin pour les gestes : l'observation est perdue explicitement plutôt que
 * publiée en `Cancelled` — et cette perte est sûre par construction, le runtime ne retient AUCUN
 * état de geste actif (les stimuli `Gesture` sont des notifications sans état), il ne peut donc
 * rien rester suspendu sans fin ; ce qu'un `Cancelled` eût dit de plus (« le geste ne continue
 * pas ») est le seul fait perdu, documenté ICI comme limite.
 */
internal fun uikitGesturePhaseForState(state: Long): UIKitTouchPhase? = when (state) {
    1L -> UIKitTouchPhase.Began
    2L -> UIKitTouchPhase.Moved
    3L -> UIKitTouchPhase.Ended
    else -> null
}

/**
 * Mappe le code brut d'une touche GameController (`GCKeyCode` du `keyChangedHandler`, un `CFIndex`
 * livré en `Long?` par le klib) sur la clé physique du modèle.
 *
 * **La page 0x07 est portée, pas inventée.** L'énumération `GCKeyCode` (GCKeyCodes.h, 137
 * constantes) EST la table HID de la page Keyboard/Keypad (0x07) — les noms se correspondent un à
 * un, des lettres A..Z (usages 0x04..0x1D) aux huit modificateurs gauche/droite (0xE0..0xE7), en
 * passant par le keypad (0x53..0x65) et les blocs International (0x87..0x8F) et LANG (0x90..0x98)
 * qui n'existent sur AUCUNE autre page — vérifié nom pour nom dans le SDK iPhoneOS27.0. Nommer la
 * page est donc porter l'invariant documenté de la source ; l'USAGE, lui, est le brut de la
 * source, jamais recalculé.
 *
 * Un brut hors du domaine d'une paire HID (0..65535), ou absent, n'a pas de token honnête :
 * `Unidentified(null)` — jamais le `toInt()` silencieux de Kotlin, qui tronquerait un `Long` hors
 * domaine en une paire FAUSSE.
 */
internal fun uikitKeyboardPhysicalKey(keyCodeRaw: Long?): PhysicalKey =
    if (keyCodeRaw != null && keyCodeRaw in 0..65_535L) {
        uikitHidKey(GC_HID_PAGE_KEYBOARD_OR_KEYPAD, keyCodeRaw.toInt())
    } else {
        PhysicalKey.Unidentified(null)
    }

/**
 * Mappe deux observations consécutives de survol sur la delta du modèle, ou `null` quand il n'y a
 * pas de delta honnête.
 *
 * Le stimulus `PointerMoved` du runtime EXIGE une `LogicalDelta` non nulle ; la delta du survol
 * (`UIHoverGestureRecognizer` ne rapporte pas de delta propre) est l'ARITHMÉTIQUE de deux
 * observations consécutives — courant − précédent, un calcul pur sur ce que la source a observé,
 * pas un fait inventé. Un mouvement sans précédent (l'entrée a été perdue) n'a pas de delta :
 * `null` — l'observation est perdue, jamais une zéro fabriquée.
 */
internal fun uikitHoverDelta(previous: LogicalPoint?, current: LogicalPoint): LogicalDelta? {
    previous ?: return null
    return LogicalDelta(current.x - previous.x, current.y - previous.y)
}

/**
 * Mappe la pression brute qu'une source rapporte sur la pression du modèle, ou `null` quand le
 * modèle ne peut pas la porter.
 *
 * La politique est celle du Web (`webPointerPressure`) et d'AppKit : UIKit rapporte une pression en
 * `0.0..1.0` ; une valeur hors de ce domaine n'est pas une pression, donc elle est perdue plutôt que
 * bornée dedans — serrer `1.7` sur `1.0` fabriquerait un fait que la source n'a pas rapporté. Une
 * valeur non finie est perdue de même, et une absence reste une absence.
 */
internal fun uikitPressure(pressure: Double?): Double? =
    pressure?.takeIf { it.isFinite() && it in 0.0..1.0 }

/**
 * Mappe la nature du périphérique d'une observation de pointeur sur le genre du modèle.
 *
 * Un stylet est un [PointerKind.Pen] — c'est la règle du plan : `UITouchTypePencil` livre les
 * observations de Pointer* avec leur pressure réelle, jamais un stylet déguisé en souris. Le hover
 * (`UIHoverGestureRecognizer`, la souris et le trackpad d'iPad) est un [PointerKind.Mouse]. Le
 * stylet gagne sur le hover : un Apple Pencil qui survole reste un Pen.
 *
 * La valeur [PointerKind.Unknown] répond pour un périphérique resté sans nom — le membre existe pour
 * ce cas, et perdre un mouvement parce que son périphérique n'a pas été nommé perdrait une
 * observation que le modèle peut porter. Un contact direct de doigt, lui, n'est PAS un pointeur : le
 * port le route vers le chemin touch (une observation de contact devient un état touch, jamais un
 * état pointer), et ne doit jamais appeler ce mapper pour lui.
 */
internal fun uikitPointerKind(isStylus: Boolean, isHover: Boolean): PointerKind = when {
    isStylus -> PointerKind.Pen
    isHover -> PointerKind.Mouse
    else -> PointerKind.Unknown
}

/**
 * Le stylet d'après la valeur brute du `type` d'un `UITouch`.
 *
 * Les valeurs brutes sont vérifiées dans le SDK (UITouch.h, énumération `UITouchType`) :
 * `UITouchTypeDirect` = 0 (un doigt sur l'écran), `UITouchTypeIndirect` = 1,
 * `UITouchTypePencil` = 2, **`UITouchTypeStylus` = alias de `UITouchTypePencil`** (nom déprécié de
 * la même valeur brute — il n'y a donc pas de « 3 pour Stylus »), et
 * `UITouchTypeIndirectPointer` = 3 (iOS 13.4+ / tvOS 13.4+ : le pointeur indirect d'un trackpad ou
 * d'une souris, qui n'est PAS un stylet). Répondre `true` pour la valeur 2 seule est donc exact
 * pour les deux noms, Pencil et Stylus, et laisse Direct, Indirect, IndirectPointer et toute valeur
 * future inconnue hors du stylet.
 */
internal fun uikitIsStylus(typeRaw: Int): Boolean = typeRaw == UI_TOUCH_TYPE_PENCIL

/**
 * Mappe les composantes d'un scroll UIKit sur le delta du modèle, ou `null` quand il ne peut pas le
 * porter.
 *
 * Le scroll d'UIKit (trackpad, deux doigts) rapporte des points : c'est un [ScrollDelta.Logical],
 * livré dans les unités mêmes de la source, signe intact. Une composante non finie est perdue, car
 * `ScrollDelta` exige des composantes finies — et le mapper ne jette pas : il répond `null`.
 */
internal fun uikitScrollDelta(deltaX: Double, deltaY: Double): ScrollDelta? {
    if (!deltaX.isFinite() || !deltaY.isFinite()) return null
    return ScrollDelta.Logical(deltaX, deltaY)
}

/**
 * Mappe l'observation d'un geste reconnu sur le stimulus du runtime, ou `null` quand l'observation
 * est hors des invariants de payload du modèle.
 *
 * Le modèle exige que le payload corresponde au genre (`validateGesture` de foundation : un Pan ne
 * porte que sa delta, un Pinch qu'une échelle strictement positive, une Rotation que des radians
 * finis, une TouchpadPressure qu'une pression finie dans `[0,1]`, un DoubleTap rien du tout). Une
 * observation qui viole son invariant — un Pinch à échelle nulle, un Pan qui traîne une pression, un
 * DoubleTap avec une échelle — n'est pas transmise au runtime, qui la réduirait par rien : elle est
 * perdue ICI, explicitement, exactement comme le Web perd un état de plume hors domaine. Les
 * composantes non finies d'une translation rendent la delta absente, et donc le Pan hors invariant.
 *
 * [surfaceId] est celui de la surface qui a reçu l'observation — le runtime est la seule couche qui
 * possède les identités, les stamps et les révisions ; le kernel ne fait que décrire ce que la
 * source a observé.
 */
internal fun uikitGesture(
    surfaceId: SurfaceId,
    primitive: UIKitGesturePrimitive,
): SurfaceStimulus.Gesture? = uikitGesture(
    surfaceId = surfaceId,
    kind = primitive.kind,
    phase = primitive.phase,
    translationX = primitive.translationX,
    translationY = primitive.translationY,
    scale = primitive.scale,
    rotation = primitive.rotation,
    pressure = primitive.pressure,
)

/** La route plate de [uikitGesture] : les champs de la primitive, un à un. */
internal fun uikitGesture(
    surfaceId: SurfaceId,
    kind: UIKitGestureKind,
    phase: UIKitTouchPhase,
    translationX: Double?,
    translationY: Double?,
    scale: Double?,
    rotation: Double?,
    pressure: Double?,
): SurfaceStimulus.Gesture? {
    val delta =
        if (translationX != null && translationY != null && translationX.isFinite() && translationY.isFinite()) {
            LogicalDelta(translationX, translationY)
        } else {
            null
        }
    val mappedKind = uikitGestureKind(kind)
    val valid = when (kind) {
        UIKitGestureKind.Pan -> delta != null && scale == null && rotation == null && pressure == null
        UIKitGestureKind.Pinch -> delta == null && scale != null && scale.isFinite() && scale > 0.0 && rotation == null && pressure == null
        UIKitGestureKind.Rotation -> delta == null && scale == null && rotation != null && rotation.isFinite() && pressure == null
        UIKitGestureKind.TouchpadPressure -> delta == null && scale == null && rotation == null && pressure != null && pressure.isFinite() && pressure in 0.0..1.0
        UIKitGestureKind.DoubleTap -> delta == null && scale == null && rotation == null && pressure == null
    }
    if (!valid) return null
    return SurfaceStimulus.Gesture(
        surfaceId = surfaceId,
        kind = mappedKind,
        phase = uikitTouchPhase(phase),
        delta = delta,
        scale = scale,
        rotationRadians = rotation,
        pressure = pressure,
    )
}

/** Les genres de geste se correspondent un à un : la reconnaissance UIKit nomme les mêmes faits. */
private fun uikitGestureKind(kind: UIKitGestureKind): GestureKind = when (kind) {
    UIKitGestureKind.Pan -> GestureKind.Pan
    UIKitGestureKind.Pinch -> GestureKind.Pinch
    UIKitGestureKind.Rotation -> GestureKind.Rotation
    UIKitGestureKind.DoubleTap -> GestureKind.DoubleTap
    UIKitGestureKind.TouchpadPressure -> GestureKind.TouchpadPressure
}

/** Les unités de code que le modèle accepte dans un token natif opaque. */
private val UIKIT_TOKEN_CHARACTERS: IntRange = 0x21..0x7e

/** `UITouchTypePencil` (= `UITouchTypeStylus`, son alias déprécié) : la valeur brute 2 d'UITouch.h. */
private const val UI_TOUCH_TYPE_PENCIL: Int = 2

/**
 * La page HID des codes `GCKeyCode` : Keyboard/Keypad (0x07). L'invariant documenté de la source
 * (GCKeyCodes.h = la table de CETTE page, cf. [uikitKeyboardPhysicalKey]), jamais une page forcée.
 */
private const val GC_HID_PAGE_KEYBOARD_OR_KEYPAD: Int = 0x07
