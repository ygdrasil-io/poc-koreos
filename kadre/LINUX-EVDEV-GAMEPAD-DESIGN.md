# Linux — Gamepads evdev : design du service

Design du service gamepad Linux (roadmap kadre phase 9, sous-tranche
gamepad ; détaché du backend Wayland — voir la décision D6). Couche
physique : **evdev** (ioctls `/dev/input/event*`) ; énumération et
hotplug : **libudev** ; effets : force feedback `EV_FF`.

## Décisions fermées

### D1 — evdev est la source de vérité, udev n'est que le notifiant
- La détection « ce nœud est-il un gamepad » se fait par les
  capabilities du noyau (`EVIOCGBIT` : plage `BTN_GAMEPAD`/`BTN_JOYSTICK`
  ou `BTN_TRIGGER_HAPPY`, axes `ABS_*`), jamais par les propriétés udev
  (`ID_INPUT_JOYSTICK` dépend de règles udev exécutées — vides dans un
  conteneur). udev sert uniquement à lister les nœuds et à notifier
  add/remove.
- L'inventaire physique complet remplace la projection seats de la
  tranche 1 (le port `WaylandInputDevicePort` reste pour les seats).

### D2 — Identité stable après reconnexion
- Clé du port = (bustype, vendor, product, uniq-si-non-vide) sinon
  (bustype, vendor, product) + ordinal d'apparition par triplet durant
  la vie du broker. Jamais le numéro de nœud (change au rebranchement).
- Le runtime assigne les `DeviceId` publics ; la clé du port n'a pas à
  fuger hors de la couture backend/runtime.

### D3 — Broker process-wide, vues par session
- Le broker possède les fds (open `O_RDONLY|O_NONBLOCK`) et les poll sur
  un worker IO dédié (motif de la pompe embarquée : jamais de poll sur
  un thread hôte). Chaque session reçoit une vue `GamepadPort` avec
  `updateRouting` (policy + foreground) qui arbitre la réception des
  `StateChanged` — le routing est une politique kadre, pas une donnée du
  device.
- Deltas bruts jamais réinjectés dans `SurfaceInput.events` (roadmap).

### D4 — Effets FF avec owners closeables
- Upload : `ioctl(EVIOCSFF)` d'un `ff_effect` RUMBLE (strong/weak) ;
  lecture de l'id assigné ; play par `write` d'un `input_event`
  `EV_FF(effect_id, repeats)` ; stop par `value=0` ; suppression par
  `EVIOCRMFF` à la fermeture de l'owner (idempotent).
- La durée reste propriété du runtime (SPI) ; le backend n'arme aucun
  timer — il stoppe à la fermeture de l'owner.

### D5 — Permissions honnêtes
- `EACCES` à l'open (pas de groupe input / pas d'uaccess logind) ⇒ le
  device apparaît dans l'inventaire avec un statut d'accès refusé et
  `inventoryFailure` porte la sémantique de permission — pas
  d'inventaire muet, pas de crash.

### D6 — Placement : module Linux partagé
- Nouveau module Gradle `:kadre:backend:linux` — service
  process-wide indépendant du backend de fenêtrage, câblé par le
  provider Wayland aujourd'hui (remplace le refus honnête GMP-002),
  réutilisable par un futur backend X11. Bindings dans kffi :
  nouveau module `kffi-evdev` (ioctls + layouts + libudev), tests
  d'intégration Linux-only comme kffi-wayland.

## Oracle d'injection : manette virtuelle uinput
- `/dev/uinput` (module noyau standard) permet de créer un gamepad
  virtuel (UI_SET_KEYBIT/UI_SET_ABSBIT), d'émettre des événements par
  `write` d'`input_event`, et de recevoir les uploads FF — le broker la
  voit par le chemin evdev normal. Preuves O3 sans matériel :
  inventaire, hotplug, état, cycle FF upload/play/stop.
- Le cahier manuel versionné (roadmap) reste requis pour la preuve du
  ressenti physique du rumble sur matériel réel.

## Tables de pannes

| ID | Panne | Garde-fou |
|---|---|---|
| F1-1 | device retiré pendant lecture | close idempotent, plus d'événements |
| F1-2 | événements après close de la vue session | routing révoqué, publication annulée |
| F2-1 | ff_effect upload mais device sans FF | EINVAL → échec d'effet explicite |
| F2-2 | owner d'effet jamais fermé | stop + EVIORMFF au close session |
| F3-1 | deux sessions, un seul gamepad | routing : une seule reçoit les states |

## Scénarios de gate

| ID | Scénario | Oracle |
|---|---|---|
| P-1 | inventaire honnête (uinput détecté, claviers exclus) | O3 |
| P-2 | hotplug add/remove → Connected/Disconnected, clé stable au rebranchement simulé | O3 |
| P-3 | état boutons/axes → GamepadState | O3 |
| P-4 | cycle FF upload → play → stop → destroy | O3 |
| SN-1 | EACCES → inventoryFailure permission | O2 |
| SN-2 | effet refusé sur device sans FF | O2 |

## Allocation de contrats prévue
`GMP-003` (inventaire + hotplug + identité), `GMP-004` (état + effets
FF + routing multi-sessions). Numéros à revérifier au registre.

## Hors périmètre (tranche 1)
hidraw (gâchettes adaptatives, LEDs), mapping DB longue traîne,
pointeur/clavier evdev (les seats couvrent déjà), battery, LEDs player.
