# Win32 phase 10 — gamepads XInput, routing et effets

**Statut :** design en cours d'implémentation conjointe KFFI/Kadre ; aucune capability ni contrat activé par ce document.
**Date :** 19 octobre 2026.
**Base Kadre :** `ec1438ee`, fusion de #486 (phase 9).
**Base KFFI auditée :** snapshot Win32 `1.0.0-SNAPSHOT` (dernière publication, incluant #140 displays).
**Portée :** la phase 10 de [la roadmap](WIN32-IMPLEMENTATION-ROADMAP.md) restreinte à la **tranche XInput** annoncée par la roadmap elle-même (« XInput peut fournir une tranche limitée ; GameInput demande sa propre qualification ») — inventaire des 4 slots `XUSER_MAX_COUNT`, état polled (boutons/axes/triggers), routing `ActiveSessionOnly`/`AllForegroundSessions`, effets `DualRumble` uniquement. DirectInput, GameInput, HID bruts, hotplug udev-style et LocalizedHaptic/TriggerRumble restent hors périmètre (§7).

## 1. Intention et critères de réussite

Livrer le `GamepadPort` Win32 : un broker process-wide qui poll les 4 slots XInput, détecte connexions/déconnexions (`ERROR_DEVICE_NOT_CONNECTED`), publie l'état canonique mappé sur le descriptor Standard, route selon `GamepadRouting` (arbitrage AppKit : `ActiveSessionOnly` = exactement une session routée), et expose `DualRumble` via `XInputSetState`. La gate de la roadmap : « l'absence de manette ne devient pas un succès de vibration » — l'hôte sans manette doit exposer zéro gamepad et refuser les effets honnêtement, et c'est prouvé en CI sur le runner sans matériel.

Aucune fusion d'inventaire : XInput est l'unique source annoncée ; tout périphérique hors XInput est invisible (pas de stratégie VID/PID/name).

## 2. Audit et choix d'architecture

Constats :

- Le runtime est complet : `GamepadPort`/`GamepadPortEvent`/`GamepadPortRouting`/`GamepadPortEffect`, `RuntimeGamepadManager` (budgets, effets avec durée publique, neutralisation des contrôles suspendus), `RuntimeSessionComponents.gamepadPort` — win32 ne fournit pas ce port aujourd'hui.
- Références : le broker evdev Linux (worker daemon, cold-plug, clés stables par identité, effets à ownership exclusive) et l'arbitrage AppKit (`ActiveSessionOnly` = exactement la première session routée au premier plan).
- XInput (SDK 10.0.28000.0, `um/XInput.h`) : `XInputGetState/SetState/GetCapabilities/GetBatteryInformation`, structs **sans unions** (kextract les émettra intégralement), DLL runtime `xinput1_4.dll` (Win8+) ou `xinput9_1_0.dll`, `XUSER_MAX_COUNT = 4`, `ERROR_DEVICE_NOT_CONNECTED (1167)` quand le slot est vide.

| Décision | Justification |
| --- | --- |
| Polling daemon (30 ms état, 1 s hotplug) sur thread broker dédié | XInput est un API de polling, pas événementielle ; identique au patron evdev |
| Clé stable = index de slot (0–3) | Un slot XInput EST l'identité physique ; reconnexion = même slot, nouvelle session runtime alloue de nouveaux ids publics (contrat respecté) |
| DualRumble uniquement | `XINPUT_VIBRATION` n'a que 2 moteurs ; TriggerRumble/LocalizedHaptic ne sont pas mappables honnêtement |
| Mappage Standard | wButtons → boutons Standard (dpad/A/B/X/Y/sticks/shoulders), triggers → axes [0,1], sticks → [-1,1] |

## 3. Frontière KFFI et owners

| Owner ou valeur KFFI | Responsabilité |
| --- | --- |
| `Win32XInput` | Appels `XInputGetState`/`XInputSetState`/`XInputGetCapabilities`/`XInputGetBatteryInformation` copiés en scalaires ; `ERROR_DEVICE_NOT_CONNECTED (1167)` retourné comme valeur (pas une exception) ; thread-safe (XInput est thread-safe par design) |
| Structs copiées | `XINPUT_STATE` (dwPacketNumber, wButtons, triggers, thumbs), `XINPUT_CAPABILITIES` (type/subtype/flags), `XINPUT_BATTERY_INFORMATION` — aucun layout natif ne franchit la frontière |

Génération : nouveau `generation/xinput.yaml` (dllMap `Xinput9_1_0.dll` — toujours installé, forwarder officiel) + `#include <xinput.h>` dans le header temporaire du script.

## 4. Broker, port et effets (Kadre)

- `Win32XInputNative` (seam injectable) : `getState(index): Win32XInputStateValue?` (null = slot sans manette — pas de sonde `isConnected` séparée : la même lecture honnête fait les deux), `setVibration(index, left, right): Boolean` (false = absent), `getCapabilities(index): Win32XInputCapabilitiesValue?`.
- `Win32GamepadBroker` process-wide : daemon thread (état 30 ms, transitions Connected/Disconnected/StateChanged détectées au même passage, publication dédupliquée sur état identique) ; `openSessionView()` par session ; la fermeture d'une vue la détache seulement — le daemon est process-lifetime (contrainte OS, cf. phase 8).
- Routing : arbitré par le runtime (`RuntimeGamepadManager` + politiques `gamepadRouting`/`effectOwnership` existantes) ; `Win32GamepadPort.updateRouting` est documenté no-op côté backend (les événements sont fanned à toutes les vues, la suspension neutralise les contrôles au niveau runtime).
- Effets DualRumble : `XINPUT_VIBRATION` est un registre partagé (pas un handle exclusif) — pas d'`AlreadyInUse` backend ; la concurrence est bornée par les politiques runtime (`maxConcurrentGamepadEffects`, ownership) ; `requestStop` écrit (0, 0) une fois ; effet sur slot déconnecté → `Closed(Gamepad)`.
- Descriptor Standard honnête : 14 boutons mappés du bitmask XInput (masques 0x0001-0x8000), axes normalisés sticks /32768 avec Y inversé, triggers /255 ; capabilities = `DualRumble` `Supported`.

## 5. Budgets et teardown

Budgets runtime : `maxConcurrentGamepadEffects`, `gamepadRouting`, `effectOwnership` (politiques existantes). Teardown : la vue fermée retire ses effets (XInputSetState(0) au stop), le broker vit process-wide, la registration des vues est révoquée à la fermeture de session via `RuntimeSessionComponents.close()`.

## 6. Matrice de réception et gates

| Contrat proposé | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-025 — inventaire XInput win32 | 4 slots pollés, Connected/Disconnected honnêtes (absence = zéro gamepad, pas un succès), clés stables par slot | Une absence inventée ou un slot hors [0,4] est détecté |
| W32-026 — état, routing et DualRumble | État canonique conforme au descriptor Standard, routing ActiveSessionOnly/AllForegroundSessions arbitré, DualRumble ownership exclusive, stop natif à la fermeture | Un second effet vivant accepté ou un état publié à une session suspendue est détecté |

Tests portables : broker à FakeNative (transitions, routing, effets), mapping boutons/axes. Tests natifs O3 : réel sans manette (zéro gamepad, effets refusés), avec manette branchée si le runner en a une (sinon réception manuelle). Charter : manette physique XInput (boutons, sticks, triggers, rumble, déconnexion à chaud).

## 7. Hors périmètre assumé de cette livraison

- **DirectInput/HID brut** : invisibles — XInput seule source annoncée (la roadmap signale explicitement que XInput ≠ inventaire DirectInput complet).
- **GameInput** : qualification propre future.
- **TriggerRumble/LocalizedHaptic** : non mappables sur `XINPUT_VIBRATION` (2 moteurs) → capabilities honnêtes.
- **Batterie** : `XInputGetBatteryInformation` exposé si trivial, sinon §7.
- **Wireless N-charging, keystroke, audio device ids** : hors source déclarée.

## 8. Risques

- XInput nécessite que le signal utilisateur soit dans la session Windows pour les... non : XInputGetState fonctionne pour tout slot branché, sans contrainte de focus ✓ (contrairement à RawInput foreground-only).
- Le polling 30 ms tourne process-wide tant qu'une vue existe — coût CPU borné et documenté.
- Sans manette physique sur le runner CI : les tests O3 prouvent les branches sans matériel (absence honnête) ; la branche avec matériel = charter manuel.
