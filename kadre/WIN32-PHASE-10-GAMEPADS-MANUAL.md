# Win32 phase 10 — charter de réception manuelle des gamepads XInput

**Objet :** réceptions matérielles non remplaçables par une preuve automatisée : présence réelle d'une manette, mapping Standard observé, dual rumble physique, débranchement à chaud, quatrième slot. Aucun verdict automatique : l'opérateur consigne résultat et limites.
**Harness :** session réelle via le provider (le test natif O3 prouve l'inventaire honnête à vide ; la réception manuelle porte sur le matériel présent). Compteur conseillé : observer `devices.state` (inventaire + événements GamepadAdded/GamepadRemoved) et `gamepad.state` (GamepadSnapshot.controls) dans un harnais temporaire ou une application consommatrice.
**Environnement à consigner par essai :** Windows 11 build, type de manette (Xbox/Series/compatible XInput), filaire/Bluetooth, pilote, slot XInput occupé, résultat, limites.

## M1 — Mapping Standard d'une manette réelle

Brancher une manette XInput (filaire de préférence), lire l'inventaire puis appuyer boutons/sticks/déclencheurs.

Attendu : un gamepad apparaît (GamepadAdded), mapping `Standard`, 14 boutons et 6 axes canoniques ; chaque pression publie le bouton attendu (A→South, B→East, X→West, Y→North, LB/RB, Start/Select, sticks clics, croix) ; sticks gauche/droite publiant LeftX/LeftY/RightX/RightY en [-1, 1] avec la convention Standard (Y positif vers le bas : haut = -1, comme evdev) ; déclencheurs LeftTrigger/RightTrigger en [0, 1].

## M2 — Dual rumble physique

Depuis le harnais, jouer `playEffect(DualRumble(strong, weak, durée))` avec (1.0, 0.0), (0.0, 1.0) puis (1.0, 1.0), et arrêter via `stopEffects()` avant la fin d'un quatrième effet.

Attendu : moteur grave seul, moteur léger seul, puis les deux ; l'arrêt coupe les moteurs immédiatement ; aucune erreur publiée ; après stop, les moteurs restent silencieux.

## M3 — Débranchement à chaud

Débrancher la manette pendant qu'un effet roule, puis la rebrancher.

Attendu : GamepadRemoved publié, l'inventaire repasse vide, le dernier XInputSetState actif cesse (les moteurs s'arrêtent côté OS) ; au rebranchement, GamepadAdded republie la manette et un nouveau rumble repart.

## M4 — Quatrième slot

Avec deux manettes XInput + un adaptateur (ou toute combinaison atteignant le slot 3), vérifier l'énumération des quatre slots simultanés.

Attendu : jusqu'à 4 gamepads inventoriés, clés stables par slot (0..3), rumble indépendant par manette ; un cinquième contrôleur (via adaptateur) n'invente pas de cinquième entrée — XInput s'y refuse par conception (consigné comme limite).

## Hors périmètre de ce charter

- Rumble par déclencheur (TriggerRumble) et haptiques localisées : non mappables via XInput (cf. design §7).
- Niveaux de batterie : lecture disponible côté KFFI mais publication conditionnelle, non contractée.
- DirectInput / GameInput / manettes non-XInput : hors périmètre.
