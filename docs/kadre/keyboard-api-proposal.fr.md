# Proposition d'API clavier Kadre

Date: 2026-06-01  
Statut: incubation, breaking changes autorisés  
Objectif: remplacer l'API clavier actuelle par un modèle inspiré de winit, idiomatique Kotlin, sans couche de compatibilité legacy.

## Décision

Kadre étant en incubation, on ne garde pas `WindowEvent.KeyboardInput` ni l'ancien enum `Key` comme API publique principale.

On remplace le modèle clavier par:

- un seul event: `WindowEvent.KeyInput`;
- une séparation stricte entre touche physique et touche logique;
- un modèle ouvert pour le texte, les dead keys et les touches inconnues;
- des modifieurs plus riches;
- une conservation systématique des infos natives.

## API cible

### Window event

```kotlin
sealed interface WindowEvent {
    data class KeyInput(
        val event: KeyEvent,
    ) : WindowEvent
}
```

`WindowEvent.KeyboardInput` disparaît. Le code applicatif doit migrer vers `KeyInput`.

### KeyEvent

```kotlin
data class KeyEvent(
    val physicalKey: PhysicalKey,
    val logicalKey: LogicalKey,
    val state: KeyState,
    val modifiers: KeyboardModifiers,
    val location: KeyLocation = KeyLocation.Standard,
    val repeat: Boolean = false,
    val synthetic: Boolean = false,
    val text: String? = null,
    val textWithAllModifiers: String? = null,
    val keyWithoutModifiers: LogicalKey? = null,
    val native: NativeKeyInfo = NativeKeyInfo(),
) {
    val isPressed: Boolean get() = state == KeyState.Pressed
    val isReleased: Boolean get() = state == KeyState.Released
    val character: String? get() = (logicalKey as? LogicalKey.Character)?.text
}
```

Rôle des champs:

| Champ | Usage |
|---|---|
| `physicalKey` | Bindings de jeu, raccourcis indépendants du layout, remapping. |
| `logicalKey` | Raccourcis orientés utilisateur, touches nommées, texte symbolique. |
| `text` | Texte produit par l'appui, hors cas où la touche ne produit pas de texte. |
| `textWithAllModifiers` | Texte produit en tenant compte aussi de Ctrl, utile pour terminaux/éditeurs. |
| `keyWithoutModifiers` | Variante logique sans Shift/Caps/Ctrl, utile pour raccourcis. |
| `native` | Trace backend pour debug et fallback. |

### Key state

```kotlin
enum class KeyState {
    Pressed,
    Released,
}
```

### PhysicalKey

```kotlin
sealed interface PhysicalKey {
    data class Code(val code: KeyCode) : PhysicalKey
    data class Native(val platform: KeyPlatform, val code: Long) : PhysicalKey
    data object Unidentified : PhysicalKey
}
```

`PhysicalKey.Code` est la voie normale. `Native` est volontairement public: il permet de ne pas perdre un scancode/HID usage que Kadre ne sait pas encore normaliser.

### KeyCode

`KeyCode` doit être aligné sur le vocabulaire winit/DOM autant que possible.

```kotlin
enum class KeyCode {
    Backquote,
    Backslash,
    BracketLeft,
    BracketRight,
    Comma,
    Digit0,
    Digit1,
    Digit2,
    Digit3,
    Digit4,
    Digit5,
    Digit6,
    Digit7,
    Digit8,
    Digit9,
    Equal,
    IntlBackslash,
    IntlRo,
    IntlYen,
    KeyA,
    KeyB,
    KeyC,
    KeyD,
    KeyE,
    KeyF,
    KeyG,
    KeyH,
    KeyI,
    KeyJ,
    KeyK,
    KeyL,
    KeyM,
    KeyN,
    KeyO,
    KeyP,
    KeyQ,
    KeyR,
    KeyS,
    KeyT,
    KeyU,
    KeyV,
    KeyW,
    KeyX,
    KeyY,
    KeyZ,
    Minus,
    Period,
    Quote,
    Semicolon,
    Slash,
    AltLeft,
    AltRight,
    Backspace,
    CapsLock,
    ContextMenu,
    ControlLeft,
    ControlRight,
    Enter,
    MetaLeft,
    MetaRight,
    ShiftLeft,
    ShiftRight,
    Space,
    Tab,
    Delete,
    End,
    Help,
    Home,
    Insert,
    PageDown,
    PageUp,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    ArrowUp,
    NumLock,
    Numpad0,
    Numpad1,
    Numpad2,
    Numpad3,
    Numpad4,
    Numpad5,
    Numpad6,
    Numpad7,
    Numpad8,
    Numpad9,
    NumpadAdd,
    NumpadBackspace,
    NumpadClear,
    NumpadComma,
    NumpadDecimal,
    NumpadDivide,
    NumpadEnter,
    NumpadEqual,
    NumpadMultiply,
    NumpadSubtract,
    Escape,
    Fn,
    FnLock,
    PrintScreen,
    ScrollLock,
    Pause,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    F13,
    F14,
    F15,
    F16,
    F17,
    F18,
    F19,
    F20,
    F21,
    F22,
    F23,
    F24,
    F25,
    F26,
    F27,
    F28,
    F29,
    F30,
    F31,
    F32,
    F33,
    F34,
    F35,
    AudioVolumeDown,
    AudioVolumeMute,
    AudioVolumeUp,
    BrowserBack,
    BrowserFavorites,
    BrowserForward,
    BrowserHome,
    BrowserRefresh,
    BrowserSearch,
    BrowserStop,
    Eject,
    LaunchApp1,
    LaunchApp2,
    LaunchMail,
    MediaPlayPause,
    MediaSelect,
    MediaStop,
    MediaTrackNext,
    MediaTrackPrevious,
    Power,
    Sleep,
    WakeUp,
}
```

### LogicalKey

```kotlin
sealed interface LogicalKey {
    data class Character(val text: String) : LogicalKey
    data class Named(val key: NamedKey) : LogicalKey
    data class Dead(val accent: String?) : LogicalKey
    data class Unidentified(val native: NativeKeyInfo = NativeKeyInfo()) : LogicalKey
}
```

Pourquoi sealed ici:

- `Character("é")`, `Character("你")` et `Character("\r")` sont représentables sans agrandir un enum.
- `Dead("^")` est différent de `Character("^")`.
- `Unidentified(native)` garde l'information de diagnostic.

### NamedKey

```kotlin
enum class NamedKey {
    Alt,
    AltGraph,
    CapsLock,
    Control,
    Fn,
    FnLock,
    Hyper,
    Meta,
    NumLock,
    ScrollLock,
    Shift,
    Super,
    Symbol,
    SymbolLock,
    Enter,
    Tab,
    Space,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    ArrowUp,
    End,
    Home,
    PageDown,
    PageUp,
    Backspace,
    Clear,
    Copy,
    CrSel,
    Cut,
    Delete,
    EraseEof,
    ExSel,
    Insert,
    Paste,
    Redo,
    Undo,
    Accept,
    Again,
    Attn,
    Cancel,
    ContextMenu,
    Escape,
    Execute,
    Find,
    Help,
    Pause,
    Play,
    Props,
    Select,
    ZoomIn,
    ZoomOut,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    F13,
    F14,
    F15,
    F16,
    F17,
    F18,
    F19,
    F20,
    F21,
    F22,
    F23,
    F24,
    F25,
    F26,
    F27,
    F28,
    F29,
    F30,
    F31,
    F32,
    F33,
    F34,
    F35,
    AudioVolumeDown,
    AudioVolumeMute,
    AudioVolumeUp,
    MediaPlay,
    MediaPause,
    MediaPlayPause,
    MediaStop,
    MediaTrackNext,
    MediaTrackPrevious,
    BrowserBack,
    BrowserFavorites,
    BrowserForward,
    BrowserHome,
    BrowserRefresh,
    BrowserSearch,
    BrowserStop,
    LaunchApp1,
    LaunchApp2,
    LaunchMail,
    PrintScreen,
}
```

### Modifiers

```kotlin
@JvmInline
value class KeyboardModifiers(val bits: Int) {
    val shift: Boolean get() = bits and SHIFT != 0
    val ctrl: Boolean get() = bits and CTRL != 0
    val alt: Boolean get() = bits and ALT != 0
    val meta: Boolean get() = bits and META != 0
    val altGraph: Boolean get() = bits and ALT_GRAPH != 0
    val capsLock: Boolean get() = bits and CAPS_LOCK != 0
    val numLock: Boolean get() = bits and NUM_LOCK != 0
    val symbol: Boolean get() = bits and SYMBOL != 0

    fun contains(other: KeyboardModifiers): Boolean = bits and other.bits == other.bits
    operator fun plus(other: KeyboardModifiers): KeyboardModifiers = KeyboardModifiers(bits or other.bits)
    operator fun minus(other: KeyboardModifiers): KeyboardModifiers = KeyboardModifiers(bits and other.bits.inv())

    companion object {
        const val SHIFT = 1 shl 0
        const val CTRL = 1 shl 1
        const val ALT = 1 shl 2
        const val META = 1 shl 3
        const val ALT_GRAPH = 1 shl 4
        const val CAPS_LOCK = 1 shl 5
        const val NUM_LOCK = 1 shl 6
        const val SYMBOL = 1 shl 7

        val NONE = KeyboardModifiers(0)
        val Shift = KeyboardModifiers(SHIFT)
        val Ctrl = KeyboardModifiers(CTRL)
        val Alt = KeyboardModifiers(ALT)
        val Meta = KeyboardModifiers(META)
        val AltGraph = KeyboardModifiers(ALT_GRAPH)
    }
}
```

État gauche/droite:

```kotlin
data class ModifierKeys(
    val leftShift: ModifierKeyState = ModifierKeyState.Unknown,
    val rightShift: ModifierKeyState = ModifierKeyState.Unknown,
    val leftCtrl: ModifierKeyState = ModifierKeyState.Unknown,
    val rightCtrl: ModifierKeyState = ModifierKeyState.Unknown,
    val leftAlt: ModifierKeyState = ModifierKeyState.Unknown,
    val rightAlt: ModifierKeyState = ModifierKeyState.Unknown,
    val leftMeta: ModifierKeyState = ModifierKeyState.Unknown,
    val rightMeta: ModifierKeyState = ModifierKeyState.Unknown,
)

enum class ModifierKeyState {
    Pressed,
    Released,
    Unknown,
}

data class KeyboardModifierState(
    val logical: KeyboardModifiers,
    val physical: ModifierKeys = ModifierKeys(),
)
```

`WindowEvent.ModifiersChanged` devient:

```kotlin
data class ModifiersChanged(
    val state: KeyboardModifierState,
) : WindowEvent
```

### Location et données natives

```kotlin
enum class KeyLocation {
    Standard,
    Left,
    Right,
    Numpad,
}

enum class KeyPlatform {
    AppKit,
    UIKit,
    Android,
    Win32,
    X11,
    Wayland,
    Web,
    Unknown,
}

data class NativeKeyInfo(
    val platform: KeyPlatform = KeyPlatform.Unknown,
    val scanCode: Long? = null,
    val virtualKey: Long? = null,
    val keyCode: String? = null,
    val keyValue: String? = null,
)
```

## Ergonomie Kotlin

### Raccourcis

```kotlin
data class KeyChord(
    val physicalKey: PhysicalKey? = null,
    val logicalKey: LogicalKey? = null,
    val modifiers: KeyboardModifiers = KeyboardModifiers.NONE,
    val allowRepeat: Boolean = false,
) {
    init {
        require(physicalKey != null || logicalKey != null) {
            "KeyChord requires either physicalKey or logicalKey"
        }
    }

    fun matches(event: KeyEvent): Boolean {
        if (!event.isPressed) return false
        if (!allowRepeat && event.repeat) return false
        if (!event.modifiers.contains(modifiers)) return false
        if (physicalKey != null) return event.physicalKey == physicalKey
        return event.logicalKey == logicalKey
    }
}
```

Exemples:

```kotlin
val save = KeyChord(
    logicalKey = LogicalKey.Character("s"),
    modifiers = KeyboardModifiers.Ctrl,
)

val moveForward = KeyChord(
    physicalKey = PhysicalKey.Code(KeyCode.KeyW),
)
```

`save` suit le layout utilisateur. `moveForward` suit la position physique.

### Helpers

```kotlin
fun KeyEvent.isPhysical(code: KeyCode): Boolean =
    physicalKey == PhysicalKey.Code(code)

fun KeyEvent.isNamed(key: NamedKey): Boolean =
    logicalKey == LogicalKey.Named(key)

fun KeyEvent.isTextCommitCandidate(): Boolean =
    state == KeyState.Pressed && text != null && !repeat
```

## Plan de refonte

### Phase 0 - Casser l'API core

- Supprimer `Key`.
- Supprimer `Modifiers`.
- Supprimer `WindowEvent.KeyboardInput`.
- Introduire `KeyEvent`, `PhysicalKey`, `KeyCode`, `LogicalKey`, `NamedKey`, `KeyboardModifiers`, `ModifierKeys`, `KeyboardModifierState`, `NativeKeyInfo`.
- Remplacer `WindowEvent.ModifiersChanged(val modifiers: Modifiers)` par `WindowEvent.ModifiersChanged(val state: KeyboardModifierState)`.
- Mettre à jour les API dumps après stabilisation de la compilation.

### Phase 1 - Réparer `kadre-test` et samples

- Remplacer `keyPress(Key.X)` par deux familles:
  - `physicalKeyPress(KeyCode.KeyW)` pour les jeux;
  - `logicalKeyPress(LogicalKey.Character("w"))` pour les raccourcis/texte.
- Migrer Pong vers `PhysicalKey.Code(KeyCode.ArrowUp)` ou `NamedKey.ArrowUp` selon l'intention.
- Migrer hello samples pour afficher `physicalKey`, `logicalKey`, `text`, `modifiers`.

### Phase 2 - Mapper Web en premier

Web est le backend de validation le plus simple:

| Champ Kadre | Source DOM |
|---|---|
| `physicalKey` | `KeyboardEvent.code` |
| `logicalKey` | `KeyboardEvent.key` |
| `text` | `key` si caractère imprimable |
| `repeat` | `KeyboardEvent.repeat` |
| `modifiers` | `shiftKey`, `ctrlKey`, `altKey`, `metaKey`, `getModifierState()` |
| `native.keyCode` | `KeyboardEvent.code` |
| `native.keyValue` | `KeyboardEvent.key` |

### Phase 3 - Mapper Wayland/X11

- Wayland: evdev keycode + xkbcommon pour `PhysicalKey.Code`, `LogicalKey`, dead keys, texte.
- X11: keycode/XKB si disponible; sinon fallback `PhysicalKey.Native`.
- Ajouter tests de mapping sur AZERTY/QWERTY synthétiques quand possible.

### Phase 4 - Mapper Win32/AppKit/UIKit/Android

- Win32: scancode + extended bit pour physique; `ToUnicode` pour texte; VK pour named fallback.
- AppKit: `keyCode` physique; `characters` et `charactersIgnoringModifiers` pour logique.
- UIKit: HID usage pour physique; `UIKey.characters` pour logique.
- Android: `scanCode` pour physique; `unicodeChar` / `keyCode` pour logique.

### Phase 5 - IME et composition

Le nouveau `KeyEvent` ne remplace pas les events IME. Règle cible:

- touches brutes: `KeyInput`;
- texte composé: `WindowEvent.Ime`;
- quand IME est actif, certains backends peuvent ne pas émettre de `KeyInput` textuel pour chaque étape, comme winit.

## Changements attendus dans le code

| Zone | Action |
|---|---|
| `kadre-core/src/commonMain/.../Events.kt` | Remplacer les types clavier. |
| `kadre-test` | Refaire le DSL clavier. |
| `samples/pong` | Migrer vers `KeyEvent` et `KeyChord`. |
| `kadre-web-common` | Mapper DOM `code`/`key` vers `PhysicalKey`/`LogicalKey`. |
| `kadre-win32` | Mapper scancode/VK/ToUnicode. |
| `kadre-appkit` | Mapper `keyCode`/`characters`. |
| `kadre-x11` / `kadre-wayland` | Mapper XKB et conserver fallback natif. |

## Critères d'acceptation

- Plus aucune référence publique à l'ancien `Key`.
- Plus aucune émission de `WindowEvent.KeyboardInput`.
- Les samples compilent avec `WindowEvent.KeyInput`.
- Le backend Web distingue correctement `physicalKey = KeyCode.KeyW` et `logicalKey = Character("z")` sur layout AZERTY lorsque le navigateur fournit ces valeurs.
- Les raccourcis peuvent être exprimés soit physiquement soit logiquement via `KeyChord`.
- Les infos natives sont conservées dans les events inconnus.

## Recommandation

Faire la refonte en une seule branche courte et cassante. Le modèle cible doit devenir la seule API clavier avant d'ajouter d'autres features d'input, sinon on devra maintenir deux vocabulaires concurrents dans les backends.
