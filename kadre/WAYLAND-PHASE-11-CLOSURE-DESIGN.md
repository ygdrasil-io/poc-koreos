# Design — Compatibilité, distribution et fermeture (phase 11 Wayland)

**Mandat.** Clôturer la roadmap Wayland (`WAYLAND-IMPLEMENTATION-ROADMAP.md`
§5 phase 11) : prouver l'adapteur au-delà du compositor de gate, qualifier
les libc publiées et le chargement hors Linux, prouver les consumers
contre l'artefact publié, stresser les files et le teardown, et figer le
registre de capacités. État d'entrée : tranches 1–10 livrées (phase 10 =
contrôle portal #485 + stream PipeWire #488), la gate tourne sous
**Sway headless (famille wlroots)** — pas Weston.

## Décisions

### D1 — La matrice de compositors est versionnée et incrémentale

`scripts/ci-wayland-runtime.sh` gagne un sélecteur `KADRE_WAYLAND_COMPOSITOR`
(`sway` par défaut — comportement inchangé pour la gate existante). Chaque
famille ajoute un provisionneur qui respecte le même contrat
`start|stop|version` + fichier d'env (socket WAYLAND_DISPLAY, XDG_RUNTIME_DIR
dédié) :

- **sway (wlroots)** — déjà en place (gate quotidienne) ;
- **weston** — image pinée noble `weston --backend=headless`, le
  déterminisme historique des preuves P-* (B-5) revient comme famille
  indépendante ;
- **mutter** — conteneur noble + `mutter --headless --wayland` (famille
  GNOME) ;
- **kwin** — conteneur + `kwin_wayland --virtual` (famille KDE) — le plus
  lourd, livré en dernier.

La matrice tourne en **jobs d'acceptation dédiés** (`workflow_dispatch` +
nocturne), pas dans la gate bloquante : la gate reste sway (rapide,
déterministe), la matrice prouve la compatibilité et publie la version
réelle de chaque compositor dans la trace (`version` subcommand, preuve
B-3 étendue). **Une extension absente produit son résultat attendu, pas
un skip** : les preuves O3 existantes assument déjà ce contrat (refus
typés des features non annoncées) ; la matrice vérifie que chaque
compositor produit bien ces refus (ex. xdg-decoration absent de sway →
`Unsupported(Decorations)` documenté tranche 5), jamais un crash ni un
succès fictif.

### D2 — glibc/musl : les deux libc publiées sont qualifiées par conteneur

Noble (glibc 2.39) reste la gate. Un conteneur **Alpine (musl ≥ 1.2.5)**
exécute `:kadre:backend:wayland:waylandContractTests` + linux O2 contre un
Sway local : les symboles POSIX de kffi-posix et libwayland-client
doivent se charger sur les deux ABI. Les architectures restent amd64
(canonique de génération) — toute publication arm64 exigerait une passe
kextract dédiée, décision reportée hors phase 11.

### D3 — Hors Linux : aucune charge native, échec typé

Le provider `DesktopBackend.Wayland` répond `isAvailable() = false`
(raison `platform`) sans charger libwayland (prouvé tranche 1, probe
R1-R2 sans FFM). La phase 11 ajoute la preuve **exécutable** sur macOS :
le test portable instancie le provider et vérifie le refus — aucune
`UnsatisfiedLinkError` ne traverse la frontière. Les erreurs de linkage
sur Linux (lib absente) produisent le `PlatformFailure` typé de la
tranche 1, re-prouvé avec `LD_LIBRARY_PATH` amoindri.

### D4 — Consumers : compilation contre l'artefact publié uniquement

`kadre/consumers/kotlin` et `kadre/consumers/java` existent déjà (dépendance
unique `org.graphiks.kadre:kadre`). La phase 11 en fait des preuves de
publication : build **contre la version publiée** (pas les projets), les
backends wayland/linux/evdev/pipewire transitent dans le classpath, aucune
API retirée ne manque, et l'exécution headless du consumer Kotlin sous
Sway termine avec `SessionOutcome.Completed`. Un job dédié consomme le
dernier snapshot publié et fait tourner `Consumer` (policy copies,
result combinators, attach standalone → stop).

### D5 — Stress : files, budgets, interruption, teardown répété

Nouveau `WaylandStressLinuxTest` (tâche dédiée, hors gate bloquante) :
N fenêtres créées/détruites en rafale (queues du driver), un collector
lent sous budget frame (coalescence tranche 3), interruption brutale du
compositor en cours de session (`PlatformFailure(display,
connection-lost)` → `SessionOutcome.Failed`, zéro fuite de fd — comptage
`/proc/self/fd` avant/après), et teardown répé (50 cycles
start/stop). Les traces comparées aux invariants portables des tranches
1-3 (pas de callback tardif, pas de double terminaison).

### D6 — Fermeture documentaire : le registre est la spécification

`capabilities/wayland.md` passe en revue chaque ligne : minimum testé,
compile gate, runtime gate, état absent exact, limite connue. Les
résiduels des tranches 4-10 sont soit promus en preuve matrice (D1),
soit classés limite structurelle documentée (ex. focus pointer sway
headless), soit re-qualifiés non-support avec refus typé. La checklist
§9 de la roadmap est passée ligne par ligne dans le document de clôture.

## Résultats de qualification (D2/D4, conteneur noble/Alpine pinés)

- **musl : NON qualifié.** Alpine (musl 1.2.5, sway 1.12-rc3, wayland-client
  musl) : les preuves session passent, puis SIGSEGV déterministes dans
  `libwayland-client.so.0` (roundtrip du harnais metrics, chemins fenêtres —
  4 `hs_err_pid*.log` identiques). glibc 2.39 (Noble) reste l'unique libc
  qualifiée ; le job matrice reste glibc-only et musl est un résiduel
  d'investigation (UB tolérée par glibc, probable wire/kffi), pas un
  non-support définitif.
- **Consumers : compilation publiée prouvée, exécution résiduelle.**
  `validateKotlinConsumer`/`validateJavaConsumer` compilent contre le
  repository contractTest (job `consumers` du workflow matrice). La preuve
  d'exécution headless bloque sur le graphe runtime : le repo contractTest
  ne porte pas `win32`/`kffi-objc` et un POM déclare `win32:1.0.0` sans
  suffixe snapshot — couverture à étendre avant d'activer le test.

## Contrats

`CLS-001` est enregistré (gate `kadre-wayland-contracts`) : honnêteté du
lifecycle face au compositor réel — rafales de fenêtres, interruption
brutale (`connection-lost` typé), teardown répété borné sans fuite. La
matrice (quatre familles, job d'acceptation) REJOUE les mêmes preuves
par famille — la version de chaque compositor est tracée (B-3), une
extension absente produit son refus documenté.

`CLS-002` (portabilité de publication) n'est PAS enregistré : la preuve
de compilation transitive vit au niveau CI (job `consumers` de la
matrice — un build Gradle n'est pas une rangée d'evidence JUnit), musl
est non qualifié (ci-dessus) et l'exécution headless des consumers est
un résiduel. Le registre ne publie que ce que la gate prouve.

## Risques et garde-fous

- **KWin/mutter en conteneur** : dépendances lourdes ; si une famille ne
  démarre pas headless dans l'image pinée, le résiduel est documenté avec
  la procédure exacte — jamais un skip silencieux.
- **Coût CI** : matrice en nocturne/workflow_dispatch, la gate bloquante
  reste sway-only.
- **Stress** : bornes strictes (pas de boucle infinie sur le runner) et
  watchdog externe déjà en place (`lib/process-watchdog.sh`).
