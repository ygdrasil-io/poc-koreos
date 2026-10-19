# Design — Capture portal et PipeWire (phase 10 Wayland)

Autorités : `WAYLAND-IMPLEMENTATION-ROADMAP.md` §Phase 10,
[contrat ScreenCast](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html),
spécification D-Bus v1 (wire protocol de référence),
[lifecycle des streams PipeWire](https://docs.pipewire.org/page_streams.html).
Le schéma simplifié du document historique de capture est caduc : le fd
d'`OpenPipeWireRemote` est une **connexion PipeWire**, pas un framebuffer
à `mmap`.

## Décisions

### D1 — La disponibilité s'observe, ne se suppose pas

Le plan de contrôle capture est déclaré disponible uniquement si : bus
session joignable (`$XDG_RUNTIME_DIR/bus` ou `DBUS_SESSION_BUS_ADDRESS`
décomposée), propriétaire du nom `org.freedesktop.portal` présent,
interface ScreenCast versionnée interrogée. Toute absence produit l'échec
honnête (`Unavailable`) du SPI — jamais un inventaire vide muet. Un
portal présent sans ScreenCast reste `Unavailable`, pas `Supported`
silencieux.

### D2 — D-Bus en pur protocole, pas de binding C

Pas de sd-bus, pas de GDBus : le wire protocol D-Bus (marshalling aligné,
variantes, champs d'en-tête, SASL) est implémenté en pur Kotlin dans
`:kadre:backend:linux` sur des sockets `AF_UNIX` exposées par `kffi-posix`
(socket/connect/send/recv/recvmsg, `sockaddr_un`, `SCM_RIGHTS` pour le fd
PipeWire). Motifs : zéro dépendance native nouvelle pour le plan de
contrôle ; les conteneurs minimaux n'offrent pas toujours un sd-bus
utilisable ; le codec est prouvable O2 octet par octet contre des vecteurs
dérivés de la spécification. La frontière reste : FFM chez kffi, protocole
et politique chez kadre.

### D3 — Sous-tranches livrables

- **T1 — socle D-Bus** : codec de messages (boutisme, alignements,
  signatures, variantes, dict entries), handshake SASL `EXTERNAL`
  (uid hex) + `NEGOTIATE_UNIX_FD`, sérialisation monotone, corrélation
  requête→réponse par serial, réception des signaux.
- **T2 — contrôle portal ScreenCast** : `CreateSession` (tokens),
  `SelectSources`, `Start` (corrélation par `request_path` du signal
  `Response`), `OpenPipeWireRemote` → fd ; fermeture des requêtes et
  sessions ; révocation. Publication `CaptureSources.HostPickerOnly`
  (D4). Machine à états O2 sur fake bus.
- **T3 — données PipeWire** : bindings `libpipewire-0.3` via kextract
  (`pw_context_connect_fd`, stream SHM, négociation format/dimensions/
  stride/cadence, buffers, teardown). DMA-BUF seulement avec preuve de
  mapping — pas de promesse anticipée.
- **T4 — câblage et contrats** : `CapturePort` wayland branché sur
  T2+T3, budgets/backpressure/cancellation du runtime exercés,
  contrats enregistrés, gate étendue.

### D4 — HostPickerOnly d'abord

Tant que T3 n'est pas prouvé, l'inventaire expose
`CaptureSources.HostPickerOnly` ; `CaptureTarget.Source` et `Surface`
restent refusés (`Unsupported`) — le portal ne garantit pas de chemin
exact vers une cible arbitraire avant que le pipeline ne le prouve.

### D5 — Une connexion par session, I/O hors thread hôte

Chaque session capture possède sa connexion D-Bus (serials indépendants,
teardown simple). Les lectures bloquent sur un worker dédié (motif evdev
D3) ; l'API publique reste non bloquante. Les fd reçus (PipeWire) sont
propriétés de la session et fermés à son terme.

### D6 — Preuves

- **O2 (portable)** : vecteurs d'octets du codec contre la spécification
  (alignements, variantes, boutisme) ; machine à états portal sur fake
  bus (réponses asynchrones, timeout, refus utilisateur du picker,
  restart du service) ; corrélations jamais croisées.
- **O3 (conteneur)** : `dbus-daemon` réel + peer portal factice exposant
  ScreenCast (le harnais joue le portal, fd PipeWire simulé) — prouve
  auth, marshalling et corrélations sur un vrai bus. Le pipeline
  PipeWire réel (T3) exige `pipewire` dans le conteneur : harnais
  documenté, exécution locale privilégiée, gate sur preuves O2.
- Le `xdg-desktop-portal` complet n'est pas requis pour la gate : les
  variantes réelles GNOME/KDE/wlroots restent un résiduel documenté
  (cf. gate de sortie phase 10).

## Contrats prévus

À enregistrer en T4 après vérification des numéros libres : `CAP-002`
(contrôle portal honnête : disponibilité observée, refus picker,
fermeture), `CAP-003` (codec D-Bus conforme), `CAP-004` (lifecycle
stream PipeWire : buffers non réutilisés, fd non fui).

## Risques et garde-fous

- **SCM_RIGHTS** : le passage de fd exige `recvmsg` + `cmsghdr` —
  extension kffi-posix validée en O3 conteneur avant T2.
- **Boutisme et alignements** : le codec refuse ce qu'il ne sait pas
  réencoder à l'identique (round-trip proof O2).
- **Timeouts portal** : bornés par politique, jamais d'attente infinie
  sur un picker muet.
- **PipeWire API étendue** : T3 se limite au chemin SHM/CPU documenté ;
  tout le reste est `Unsupported` explicite.
