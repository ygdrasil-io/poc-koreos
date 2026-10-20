# Design — Stream PipeWire (phase 10 Wayland, tranche T4)

**Mandat.** Livrer la donnée de la capture : connecter le fd rendu par
`OpenPipeWireRemote` à PipeWire (`pw_context_connect_fd`), négocier
format/dimensions/stride, recevoir les buffers SHM et les remettre au runtime
comme frames détachées. Prolonge `LINUX-CAPTURE-PORTAL-DESIGN.md`
(décisions D1–D6 livrées par #485) ; les numéros continuent.

## Décisions

### D7 — Bindings `kffi-pipewire` via kextract, fonctions `pw_*` uniquement

Nouveau module kffi généré par le pipeline kextract (motif evdev/posix) :
`docker/kffi-pipewire-codegen/` sur l'image pinée noble + `libpipewire-0.3-dev`,
kextract piné à `e1d922e`. Le compat header inclut les en-têtes ciblés
(`pipewire/thread-loop.h`, `pipewire/context.h`, `pipewire/core.h`,
`pipewire/stream.h`, `pipewire/properties.h`, `pipewire/init.h`,
`spa/buffer/buffer.h`) et ré-exporte les constantes d'enum utiles en macros
`KFFI_PW_*`/`KFFI_SPA_*` — kextract n'a pas de filtre enum, le compat header
garde l'extraction fidèle aux en-têtes pinés. Aucune fonction `spa_*` :
les structs SPA (`spa_buffer`, `spa_data`, `spa_chunk`, `pw_buffer`) sont des
données lues/écrites directement ; `spa_hook` est un stockage zéro initialisé
par `pw_*_add_listener`. Librairie liée : `:libpipewire-0.3.so.0`.

### D8 — Codec SPA POD pur Kotlin (motif D2)

Le POD builder/reader SPA est écrit en pur Kotlin, comme le codec D-Bus :
types primitifs (`Id`, `Int`, `Rectangle`, `Choice`, `Object`/`Prop`),
alignements et tailles conformes à `spa/pod/pod.h`. Le reader refuse ce que le
writer ne sait pas réencoder (preuve round-trip O2). Périmètre : parser
`EnumFormat`/`Format`/`Buffers` reçus de `param_changed`, construire l'objet
`EnumFormat` de `stream_connect` et le `Buffers` fixé de
`update_params`. Les types POD hors périmètre sont refusés explicitement.

### D9 — Consommateur sur `pw_thread_loop`, frames copiées avant remise

Le client stream possède `pw_thread_loop` + `pw_context` + `pw_core` +
`pw_stream`. Toute mutation sous `pw_thread_loop_lock` ; les transitions
attendues via `wait`/`signal` bornés. Dans `process`, le buffer est déquéué,
**copié** en mémoire Kotlin (planes détachées, budget `maxFrameBytes`),
puis réenquéué avant tout retour — un buffer SPA n'est jamais détenu hors du
callback ni réutilisé après remise. File de livraison bornée : pleine ⇒ frame
droppée comptée, jamais de blocage du thread loop. Teardown idempotent
stream → core → context → thread-loop, fd fermé par la réservation.

### D10 — fds D-Bus : SCM_RIGHTS jusqu'au corps `'h'`

La seam transport (`DbusTransport.receive`) rend désormais les fds reçus par
SCM_RIGHTS avec les octets ; le codec décode `'h'` en indice validé contre
`FIELD_UNIX_FDS` et la liste réelle (indice hors domaine ⇒ refus explicite,
jamais de fd inventé). `OpenPipeWireRemote` répond `'h'` : le fd devient
propriété de la réservation capture (D5), fermé exactement une fois.

### D11 — O2 sur seam, O3 sur vrai graphe PipeWire

Le client stream vit derrière une seam `PipeWireNative` (transitions,
négociation, buffers pilotables) : les preuves O2 (gate) portent honnêteté,
budgets, backpressure, cancellation et teardown sans libpipewire. La vérité
O3 tourne en conteneur : `dbus-daemon` + peer portal factice + **daemon
pipewire réel** (config dédiée créant un node `videotestsrc`) ; le peer
répond à `OpenPipeWireRemote` par un fd connecté à la socket PipeWire —
chimiquement identique à ce que fait le vrai portal. Pixels réels reçus,
négociation vérifiée, zéro fuite. Les variantes portal réelles GNOME/KDE/
wlroots restent un résiduel documenté (gate de sortie phase 10).

### D12 — Formats livrés : le sous-ensemble portable prouvé

Mapping livré : `SPA_VIDEO_FORMAT_BGRx`→`Bgrx8`, `BGRA`→`Bgra8`,
`RGBA`→`Rgba8`, `NV12`→`Nv12`, `I420`→`I420` (plans détachés conformes à
`PixelPlaneLayout`). Tout autre format négocié par la source ⇒ terminaison
honnête `Unsupported`, jamais de transcodage implicite. Un changement de
format en cours de stream publie `onReconfigured` (revision incrémentée) si
le nouveau format est livrable, terminaison sinon.

## Contrats prévus

`CAP-004` (lifecycle stream : buffers non réutilisés après remise, fds non
fuis, backpressure bornée, cancellation avant/après handoff) ; `CAP-002`
gardé tel quel (contrôle portal), son scénario de réservation
`pipewire-stream-pending` devient : réservation menée jusqu'au fd et au
descripteur de source (le refus subsiste quand le portal l'impose).

## Risques et garde-fous

- **Layouts d'events** : `pw_stream_events`/`pw_core_events` générés par
  kextract depuis les en-têtes pinés (pas de constante magique) ; les champs
  non consommés reçoivent des stubs, le champ `version` fixe la profondeur.
- **Varargs C** (`pw_properties_new`) évités : `pw_properties_new_string`.
- **Upcalls** : stubs par `Linker.upcallStub` dans une arena détruite après
  arrêt du thread loop (aucun callback après close — motif wayland).
- **Daemon en conteneur** : videotestsrc via module adapter documenté ;
  si le plugin manque dans l'image noble, résiduel documenté sans faux vert.
