package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.ModifierKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey

/**
 * Le mapper clavier pur : `KEYCODE_*` Android vers le modèle d'entrée de Kadre, porté du
 * réservoir kadre-old (`org.graphiks.kadre.android.AndroidKeyMapper`) vers le modèle HID.
 *
 * L'ancien modèle recevait un `KeyCode` d'un autre âge ; le modèle actuel porte la touche
 * physique par son usage USB — page [KEYBOARD_USAGE_PAGE] (Keyboard/Keypad des USB HID
 * Usage Tables 1.12), usages exacts de la table web de référence `webHidUsageForCode`
 * (WebInputMapping.kt) : lettres 0x04-0x1D, chiffres 0x1E-0x27, Enter 0x28, Escape 0x29,
 * Backspace 0x2A, Tab 0x2B, Space 0x2C, F1-F12 0x3A-0x45, flèches 0x4F-0x52,
 * modificateurs gauche/droite 0xE0-0xE7. La table couvre le même ensemble que le
 * réservoir : A-Z, 0-9, F1-F12, le D-pad, Space/Enter/Escape/DEL/Tab et les huit
 * modificateurs L/R.
 *
 * Deux propriétés tiennent la même promesse que le cœur web :
 *
 * - **Totalité.** Chaque fonction répond pour tout `Int` qu'un `KeyEvent` peut porter ;
 *   aucune ne lance.
 * - **Aucune approximation.** Une touche hors table n'est pas perdue :
 *   `PhysicalKey.Unidentified("android-<code>")` la porte comme l'identité native qu'elle
 *   est — et l'événement, lui, est de toute façon forwardé par le wrapper, avec AUCUN
 *   stimulus émis depuis une valeur inventée. Un caractère non fiable n'invente pas un
 *   caractère ; un côté ambigu n'invente pas un côté.
 *
 * Les constantes `KEYCODE_*`/`META_*` sont des constantes de compilation : ce mapper est
 * pur et se teste sur l'hôte JVM sans instancier aucun `KeyEvent`.
 */
internal object AndroidKeyMapper {

    /** La page d'usage Keyboard/Keypad des USB HID Usage Tables. */
    private const val KEYBOARD_USAGE_PAGE: Int = 0x07

    /** Le préfixe du token natif d'une touche hors table — ASCII, dans le domaine du modèle. */
    private const val NATIVE_TOKEN_PREFIX: String = "android-"

    /**
     * La table KEYCODE → usage HID (page 0x07), construite une fois. Les blocs contigus
     * (lettres, chiffres, touches de fonction) suivent l'ordre des usages USB — l'ordre
     * alphabétique des lettres, `1..9,0` des chiffres, `F1..F12` des fonctions.
     */
    private val hidUsages: Map<Int, Int> = buildMap {
        // Usages 0x04..0x1D : Keyboard a..z.
        for (letter in 0..25) put(KeyEvent.KEYCODE_A + letter, 0x04 + letter)

        // Usages 0x1E..0x27 : Keyboard 1..9 puis 0 — le zéro EST le dernier usage.
        put(KeyEvent.KEYCODE_0, 0x27)
        for (digit in 1..9) put(KeyEvent.KEYCODE_1 + digit - 1, 0x1D + digit)

        // Usage 0x28..0x2C : Return, Escape, Delete (Backspace), Tab, Spacebar.
        put(KeyEvent.KEYCODE_SPACE, 0x2C)
        put(KeyEvent.KEYCODE_ENTER, 0x28)
        put(KeyEvent.KEYCODE_ESCAPE, 0x29)
        // KEYCODE_DEL est la touche Backspace (usage 0x2A), jamais la touche Delete (0x4C).
        put(KeyEvent.KEYCODE_DEL, 0x2A)
        put(KeyEvent.KEYCODE_TAB, 0x2B)

        // Usages 0x3A..0x45 : Keyboard F1..F12.
        for (function in 1..12) put(KeyEvent.KEYCODE_F1 + function - 1, 0x39 + function)

        // Usages 0x4F..0x52 : les flèches — Right, Left, Down, Up, dans l'ordre de la table.
        put(KeyEvent.KEYCODE_DPAD_RIGHT, 0x4F)
        put(KeyEvent.KEYCODE_DPAD_LEFT, 0x50)
        put(KeyEvent.KEYCODE_DPAD_DOWN, 0x51)
        put(KeyEvent.KEYCODE_DPAD_UP, 0x52)

        // Usages 0xE0..0xE7 : Control/Shift/Alt/Meta, gauche puis droite — la paire est
        // distincte par usage, ce qui distingue un modificateur gauche d'un droit.
        put(KeyEvent.KEYCODE_CTRL_LEFT, 0xE0)
        put(KeyEvent.KEYCODE_SHIFT_LEFT, 0xE1)
        put(KeyEvent.KEYCODE_ALT_LEFT, 0xE2)
        put(KeyEvent.KEYCODE_META_LEFT, 0xE3)
        put(KeyEvent.KEYCODE_CTRL_RIGHT, 0xE4)
        put(KeyEvent.KEYCODE_SHIFT_RIGHT, 0xE5)
        put(KeyEvent.KEYCODE_ALT_RIGHT, 0xE6)
        put(KeyEvent.KEYCODE_META_RIGHT, 0xE7)
    }

    /**
     * Les touches nommées du modèle — le miroir de la couverture `webNamedKeyForValue`
     * pour l'ensemble mappé : la table web nomme Espace, les flèches, F1-F12 et chaque
     * modificateur, et un modificateur garde son nom quel que soit son côté.
     */
    private val namedKeys: Map<Int, NamedKey> = mapOf(
        KeyEvent.KEYCODE_ENTER to NamedKey.Enter,
        KeyEvent.KEYCODE_TAB to NamedKey.Tab,
        KeyEvent.KEYCODE_SPACE to NamedKey.Space,
        KeyEvent.KEYCODE_DEL to NamedKey.Backspace,
        KeyEvent.KEYCODE_ESCAPE to NamedKey.Escape,
        KeyEvent.KEYCODE_DPAD_UP to NamedKey.ArrowUp,
        KeyEvent.KEYCODE_DPAD_DOWN to NamedKey.ArrowDown,
        KeyEvent.KEYCODE_DPAD_LEFT to NamedKey.ArrowLeft,
        KeyEvent.KEYCODE_DPAD_RIGHT to NamedKey.ArrowRight,
        KeyEvent.KEYCODE_SHIFT_LEFT to NamedKey.Shift,
        KeyEvent.KEYCODE_SHIFT_RIGHT to NamedKey.Shift,
        KeyEvent.KEYCODE_CTRL_LEFT to NamedKey.Control,
        KeyEvent.KEYCODE_CTRL_RIGHT to NamedKey.Control,
        KeyEvent.KEYCODE_ALT_LEFT to NamedKey.Alt,
        KeyEvent.KEYCODE_ALT_RIGHT to NamedKey.Alt,
        KeyEvent.KEYCODE_META_LEFT to NamedKey.Meta,
        KeyEvent.KEYCODE_META_RIGHT to NamedKey.Meta,
        KeyEvent.KEYCODE_F1 to NamedKey.F1,
        KeyEvent.KEYCODE_F2 to NamedKey.F2,
        KeyEvent.KEYCODE_F3 to NamedKey.F3,
        KeyEvent.KEYCODE_F4 to NamedKey.F4,
        KeyEvent.KEYCODE_F5 to NamedKey.F5,
        KeyEvent.KEYCODE_F6 to NamedKey.F6,
        KeyEvent.KEYCODE_F7 to NamedKey.F7,
        KeyEvent.KEYCODE_F8 to NamedKey.F8,
        KeyEvent.KEYCODE_F9 to NamedKey.F9,
        KeyEvent.KEYCODE_F10 to NamedKey.F10,
        KeyEvent.KEYCODE_F11 to NamedKey.F11,
        KeyEvent.KEYCODE_F12 to NamedKey.F12,
    )

    /** La page et l'usage HID d'un `KEYCODE_*`, ou `null` quand la table ne l'énumère pas. */
    public fun hidUsage(keyCode: Int): Pair<Int, Int>? =
        hidUsages[keyCode]?.let { usage -> KEYBOARD_USAGE_PAGE to usage }

    /**
     * La touche physique : `PhysicalKey.Code` à son usage HID quand la table l'énumère,
     * sinon `PhysicalKey.Unidentified("android-<code>")` — l'identité native honnête.
     */
    public fun physicalKey(keyCode: Int): PhysicalKey =
        hidUsage(keyCode)
            ?.let { (usagePage, usageId) -> PhysicalKey.Code(usagePage = usagePage, usageId = usageId) }
            ?: PhysicalKey.Unidentified(NATIVE_TOKEN_PREFIX + keyCode)

    /**
     * La touche logique — le miroir `webLogicalKey` : la table nommée d'abord (Enter reste
     * un Enter nommé, un Espace un Espace nommé, même quand `getUnicodeChar()` produit un
     * caractère), puis le caractère fiable rapporté par la plateforme, avec sa casse —
     * le caractère de `a` n'est pas celui de `A` ; sinon `Unidentified` avec le token
     * natif. Une touche hors table qui PRODUIT un caractère (ponctuation IME) garde ce
     * caractère ; une lettre sans unicode fiable n'invente rien.
     */
    public fun logicalKey(keyCode: Int, unicodeChar: Int?): LogicalKey =
        namedKeys[keyCode]
            ?.let { LogicalKey.Named(it) }
            ?: characterFor(unicodeChar)
            ?: LogicalKey.Unidentified(NATIVE_TOKEN_PREFIX + keyCode)

    /**
     * La position physique de la touche, quand le `metaState` la rapporte sans ambiguïté :
     * un seul côté de modificateur levé dit son côté (celui de l'événement modificateur
     * lui-même — Android lève le flag de côté de la touche qui vient de bouger). Les
     * deux côtés à la fois, un masque générique seul, une touche système : aucune
     * position inventée, [KeyLocation.Standard]. Le code de la touche porterait aussi le
     * côté des `KEYCODE_*_LEFT/RIGHT` — la signature de cette phase ne le transporte pas,
     * la tâche qui câble les stimuli arbitrera.
     */
    public fun location(isSystemKey: Boolean, metaState: Int): KeyLocation {
        if (isSystemKey) return KeyLocation.Standard
        val left = metaState and KeyEvent.META_SHIFT_LEFT_ON != 0 ||
            metaState and KeyEvent.META_CTRL_LEFT_ON != 0 ||
            metaState and KeyEvent.META_ALT_LEFT_ON != 0 ||
            metaState and KeyEvent.META_META_LEFT_ON != 0
        val right = metaState and KeyEvent.META_SHIFT_RIGHT_ON != 0 ||
            metaState and KeyEvent.META_CTRL_RIGHT_ON != 0 ||
            metaState and KeyEvent.META_ALT_RIGHT_ON != 0 ||
            metaState and KeyEvent.META_META_RIGHT_ON != 0
        return when {
            left == right -> KeyLocation.Standard
            left -> KeyLocation.Left
            else -> KeyLocation.Right
        }
    }

    /**
     * Les modificateurs du `metaState` : les masques génériques — ceux qu'Android pose
     * toujours à côté des flags de côté sur un vrai événement — donnent Shift, Control,
     * Alt et Meta ; les états verrou donnent CapsLock et NumLock. Un flag de côté
     * orphelin ne déclare pas un modificateur pressé, et un `META_SYM_ON` est perdu :
     * le modèle n'a pas de membre Symbol, l'absence reste une absence.
     */
    public fun modifiers(metaState: Int): KeyboardModifiers = KeyboardModifiers(
        buildSet {
            if (metaState and KeyEvent.META_SHIFT_ON != 0) add(ModifierKey.Shift)
            if (metaState and KeyEvent.META_CTRL_ON != 0) add(ModifierKey.Control)
            if (metaState and KeyEvent.META_ALT_ON != 0) add(ModifierKey.Alt)
            if (metaState and KeyEvent.META_META_ON != 0) add(ModifierKey.Meta)
            if (metaState and KeyEvent.META_CAPS_LOCK_ON != 0) add(ModifierKey.CapsLock)
            if (metaState and KeyEvent.META_NUM_LOCK_ON != 0) add(ModifierKey.NumLock)
        },
    )

    /**
     * Le caractère fiable d'un code unicode, ou `null` : un code point valide non nul
     * produit exactement la chaîne rapportée (un astral devient sa paire de surrogates,
     * un seul caractère) ; tout le reste n'est pas un caractère, jamais un inventé.
     */
    private fun characterFor(unicodeChar: Int?): LogicalKey.Character? {
        if (unicodeChar == null || unicodeChar !in 1..MAX_CODE_POINT) return null
        val text = if (unicodeChar <= Char.MAX_VALUE.code) {
            String(charArrayOf(unicodeChar.toChar()))
        } else {
            val adjusted = unicodeChar - 0x10000
            String(
                charArrayOf(
                    ((adjusted shr 10) + 0xD800).toChar(),
                    ((adjusted and 0x3FF) + 0xDC00).toChar(),
                ),
            )
        }
        return LogicalKey.Character(text)
    }

    private const val MAX_CODE_POINT: Int = 0x10FFFF
}
