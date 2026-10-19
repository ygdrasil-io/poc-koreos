# Win32 phase 8 — raw input et arbitrage du processus

**Statut :** design en cours d'implémentation conjointe KFFI/Kadre ; aucune capability ni contrat activé par ce document.
**Date :** 19 octobre 2026.
**Base Kadre :** `66f7f971`, fusion de #480 (phase 7).
**Base KFFI auditée :** snapshot Win32 `1.0.0-SNAPSHOT` (dernière publication `1.0.0-20261019.082243-67`, source `2328fc08`, KFFI #135).
**Portée :** la phase 8 de [la roadmap](WIN32-IMPLEMENTATION-ROADMAP.md) — une source raw input souris (deltas relatifs) possédée par le broker du processus, exposée via `SurfaceInput.requestRawInput` et les ports runtime existants. Le mode pointeur `Locked` reste explicitement hors de cette livraison (travail ouvert nommé en §8).

## 1. Intention et critères de réussite

La demande est de continuer après la phase 7 avec le même mode de livraison conjointe : publier KFFI localement pour développer les deux côtés, livrer la phase entière, puis consommer la publication distante (le catalogue flottant `1.0.0-SNAPSHOT` suit la publication sans re-pin). Les changements KFFI restent formulés en termes Win32 génériques, sans référence au dépôt consommateur.

Le résultat attendu est exactement le contrat runtime déjà écrit : `RawInputPort`/`RawInputPortLease` alimentent `RawInputCoordinator` (INP-002 actif), chaque accès reste isolé, jamais injecté dans `SurfaceInput.events`, avec budgets et terminaisons conformes à `OPERATION-CONTRACTS.md`. La spécificité Windows est l'arbitrage du processus : **un seul enregistrement par classe de périphérique** (`RegisterRawInputDevices` remplace silencieusement l'enregistrement précédent du processus) — le broker doit donc refuser de voler un enregistrement tiers préexistant, ne retirer à la fermeture que l'enregistrement encore possédé, et garder l'échec typé lorsque l'arbitrage est impossible.

Un WM_INPUT reçu n'est pas une preuve : la phase prouve l'enregistrement réel, la copie immédiate du payload, l'isolation multi-accès et le retrait idempotent. Les deltas sont des comptes de périphérique (`RawInputUnit.DeviceCount`), jamais des coordonnées de surface.

## 2. Audit et choix d'architecture

Constats sur les sources examinées :

- Le runtime est complet et backend-agnostique : `RawInputPort` (SPI), `RawInputCoordinator`/`RuntimeRawInputAccess` (admission sous budget avant permission native, isolation par accès, overflow par politique, owner-scoped teardown), `RuntimeSessionComponents.rawInputPort` (absent côté win32 : `Unsupported(RawInputAccess)` bout en bout aujourd'hui).
- AppKit (phase 8 AppKit) fournit le patron de référence : broker process-wide (tap unique, fan-out vers chaque lease, reprise après timeout, capability dynamique) + port de session + lease à canal borné ; KFFI possède l'adaptateur natif (`CGListenOnlyEventTap`) ; les deltas copiés en scalaires ne rejoignent jamais l'input ordinaire.
- KFFI ne génère aujourd'hui **aucune** fonction raw input (`RegisterRawInputDevices`, `GetRawInputData`, `GetRawInputDeviceList`, `GetRegisteredRawInputDevices` absentes), ni les layouts `RAWINPUTDEVICE`/`RAWINPUT`. L'ancien backend ne les avait jamais implémentées.

| Approche | Avantage | Limite déterminante | Décision |
| --- | --- | --- | --- |
| Enregistrement par fenêtre Kadre | Simplicité apparente | `RegisterRawInputDevices` remplace l'enregistrement du processus : N fenêtres = N-1 vols ; aucune fenêtre étrangère préservée | Non retenue |
| Broker process-wide, enregistrement unique sur la fenêtre de contrôle | Un seul owner par classe, coexistence qualifiable (pré-vol tiers, retrait sous re-vérification), porte le `RIDEV_INPUTSINK` (source vivante hors focus, analogue honnête du tap global AppKit) | WM_INPUT arrive sur la fenêtre de contrôle : routage à ajouter côté host | Retenue |
| Pas de raw input (`Unsupported` permanent) | Zéro risque | Contredit la matrice `BACKEND-CAPABILITIES.md` (win32 = C) sans justification native | Non retenue |

Périmètre déclaré précisément : **souris en mouvement relatif uniquement** (`RAWMOUSE`, `usFlags == MOUSE_MOVE_RELATIVE`), usage `1/2` (generic desktop/mouse). Absolu (`MOUSE_MOVE_ABSOLUTE`, tables à stylet), clavier, HID autres et `RIDEV_NOLEGACY` sont listés mais non exposés : ils ne rejoignent jamais cette source. `deviceId` reste `null` — aucun `DeviceId` public n'est inventé depuis un handle natif (précédent phase 6).

## 3. Frontière KFFI et owners

| Owner ou valeur KFFI | Responsabilité et durée de vie |
| --- | --- |
| `Win32RawInput` | Enregistrement managé unique : pré-vol `GetRegisteredRawInputDevices` (un enregistrement tiers existant pour l'usage → failure typée `RAWINPUT_DEVICE_ALREADY_REGISTERED`-mappée, jamais de vol), `RegisterRawInputDevices` sur le HWND cible, retrait `RIDEV_REMOVE` sous **re-vérification d'ownership** (l'enregistrement courant pointe encore notre HWND ; un enregistrement volé n'est jamais retiré) ; close idempotent, thread-confined au thread d'enregistrement |
| `Win32RawInput.read(lParam)` | Lecture `GetRawInputData(RID_INPUT)` pendant le message dans un buffer borné : `RAWINPUTHEADER.dwType == RIM_TYPEMOUSE`, flags relatifs, deltas copiés en scalaires ; valeur nulle pour tout payload non conforme (absolu, clavier, taille inattendue) — jamais d'approximation |
| Valeurs copiées | `Win32RawMotion(deltaX: Int, deltaY: Int, device: Long)` — scalaires seulement ; aucun pointeur, layout ou buffer natif ne franchit la frontière |
| Constantes typées | `WM_INPUT`, `WM_INPUT_DEVICE_CHANGE`, `RID_INPUT`, `RIDEV_INPUTSINK`/`RIDEV_REMOVE`, `RIM_TYPEMOUSE`, `MOUSE_MOVE_RELATIVE`, usage page/usage souris comme scalaires commentés |

La génération gagne `RegisterRawInputDevices`, `GetRawInputData`, `GetRawInputDeviceList`, `GetRegisteredRawInputDevices` et les layouts `RAWINPUTDEVICE`/`RAWINPUTHEADER`/`RAWINPUT`/`RAWINPUTDEVICELIST` (l'union de `RAWMOUSE` reste inaccessible au générateur : lecture par offsets documentés, même traitement que l'union `STGMEDIUM`). Les échecs natifs deviennent `Win32RawInputException(hresult/code)` — HRESULT numérique seul, jamais de payload.

## 4. Broker du processus, port et leases (Kadre)

Patron AppKit transposé :

- `Win32RawInputBroker` (process-wide, lazy) : possède au plus un enregistrement KFFI vivant ; `requestAccess(port)` crée un lease par port, installe l'enregistrement au premier accès, publie la capability dynamique (`Capability.Supported(Unit, Available)` une fois l'enregistrement posé) ; fan-out d'un `RawInputPortInput(deltaX, deltaY, unit = DeviceCount, deviceId = null)` vers **chaque** lease vivant ; le dernier close retire l'enregistrement ; `close()` du broker (teardown process) force le retrait.
- `Win32RawInputPort` (session-owned, `RawInputPort`) : capability seedée par le broker, `requestAccess` délégué, close → `broker.closePort(this)` ; un seul observateur de capability par port.
- `Win32RawInputPortLease` : canal de `RawInputPortLeaseEvent` (`Input`/`Availability`/`Terminal`), close idempotent → `broker.closeRegistration`. Aucun multiplexage implicite : chaque lease appartient à un accès runtime exactement.
- Refus et pertes observables : enregistrement tiers préexistant → capability `Unsupported(RawInputAccess)` + failure typée par lease (pas de repli silencieux) ; échec d'enregistrement natif → `Unavailable(TemporarilyUnavailable(retryable = true))` ; aucune « reprise » n'est inventée pour un vol silencieux d'enregistrement — non observable sur Windows, consigné comme limite (`WM_INPUT` cesse sans notification ; le charter le consigne).

Le routage `WM_INPUT` (0x00FF) entre dans la chaîne fenêtre existante : le handler de fenêtre consulte `port.readRawInputMessage(lParam)` **avant** les routeurs d'input ordinaires ; le payload copié rejoint `broker.acceptNativeMotion` ; retour `0L` (le message est consommé). `WM_INPUT_DEVICE_CHANGE` est accepté et journalisé sans fabrication d'état. La chaîne d'input ordinaire (`Win32InputRouter`) reste propriétaire du legacy mouse ; aucun message n'est dupliqué.

## 5. Intégration, budgets et teardown

`Win32BackendProvider` gagne `rawInputPortFactory` (défaut : broker du processus sous Windows, `null` ailleurs) et le passe à `RuntimeSessionComponents(rawInputPort = ...)`. Les budgets sont ceux du runtime : `maxConcurrentRawInputAccesses` (réservé avant l'admission native par le coordonnateur), `RawInputDeliveryPolicy` (capacité + overflow par accès). Le raw input ne rejoint jamais `SurfaceInput.events`.

Le teardown respecte l'ordre normatif : coordonnateur (ferme les accès → leases → port), puis broker (dernier close → retrait sous re-vérification d'ownership), avant la fermeture du port hôte et de la fenêtre de contrôle. `RawInputPort.close` reste idempotent (contrat `RuntimeSessionComponents`).

`PointerCaptureMode.Locked` **reste `Unsupported`** dans cette livraison : son activation exige les preuves de restauration sur focus perdu et teardown de la roadmap (re-centrage + masquage curseur + deltas raw), qui constituent un travail ouvert nommé, pas un sous-produit du broker.

## 6. Matrice de réception et gates

| Contrat proposé | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-023 — broker raw input et arbitrage | Enregistrement réel sur HWND + deltas réels injectés (`SendInput` relatif) jusqu'aux `RawInputEvent` publics ; refus typé devant un enregistrement tiers préexistant ; retrait idempotent sous re-vérification ; multi-accès isolés | Un enregistrement tiers volé, un delta injecté dans `SurfaceInput.events` ou un retrait d'un enregistrement perdu est détecté |

Tests portables : broker à fake natif (admission/refus/reprise/close, fan-out multi-lease, capability), port/lease (canaux, idempotence), routage `WM_INPUT` via le port déterministe, `Unsupported` sans factory. Tests natifs obligatoires : vraie registration sur la fenêtre de contrôle, `SendInput` relatif → deltas publics, isolation deux accès, retrait vérifié par `GetRegisteredRawInputDevices` vide. Charter manuel : souris physique hors focus (`RIDEV_INPUTSINK`), souris absolue ignorée, coexistence avec un enregistrement tiers réel, consommation rapide (overflow), fermeture pendant flux.

La livraison conserve les phases 1–7 vertes et n'active W32-023 qu'avec preuves fraîches au SHA courant après publication KFFI (catalogue flottant).

## 7. Risques et décisions de sortie

- Le vol silencieux d'enregistrement par un tiers postérieur n'est **pas** détectable : limite documentée ; la défense est le pré-vol, la re-vérification au retrait et le charter. Aucune détection simulée.
- `RIDEV_INPUTSINK` reçoit hors focus : cohérent avec le tap global AppKit, mais la fenêtre de contrôle devient réceptrice système — le routing doit rester borné (copie immédiate, zéro allocation retenue).
- Un manque de bridge ou un comportement non terminé reste ouvert ; `Locked` et les usages non-souris ne deviennent pas des `Unsupported` permanents pour déclarer la phase complète.

La prochaine étape après revue de ce design est l'implémentation conjointe et les deux PRs (KFFI puis Kadre). Ce document ne revendique aucune exécution native ni activation de contrat.
