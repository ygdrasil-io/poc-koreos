# Wayland Phase 0 — Prérequis protocolaires et contractuels — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fermer les prérequis de la Phase 0 de la roadmap Wayland : audit des bindings KFFI publiés, baseline Linux/JVM/libc/protocoles, décisions contractuelles (décorations, frontière renderer/commit/lease, lifecycle), spécification du probe `isAvailable()` et design de première tranche — sans écrire une ligne de backend Wayland.

**Architecture:** Phase d'audit et de décisions (jalon M0 « Baseline, obstacles et frontières fermés »). Les livrables sont des documents normatifs suivant les conventions existantes : un audit KFFI adossé aux artifacts publiés, des décisions contractuelles alignées sur `BACKEND-CAPABILITIES.md`/`DESIGN.md`, et un design de tranche Phase 1 avec scénarios positifs/négatifs et allocation prévue de contrats. Aucun module, aucune dépendance et aucune ligne de `contracts.tsv` ne changent.

**Tech Stack:** Markdown normatif (français), Gradle (validation du registre de contrats), curl/unzip/sha256sum pour l'évidence des snapshots Maven, source KFFI de référence sur disque.

**Spec:** `kadre/WAYLAND-IMPLEMENTATION-ROADMAP.md` — §5 « Phase 0 », §3 (choix d'architecture), §4 (topologie des changements), §7 (preuves et stratégie de PRs).

## Global Constraints

- Aucun code backend Wayland, aucun module `kadre/backend/wayland` ni `contracts/driver/wayland`, aucune modification de `settings.gradle.kts` : la Phase 0 ne livre que des documents et décisions.
- `kadre/capabilities/wayland.md` n'est PAS créé (première phase : 1, topologie §4).
- Aucun ID de contrat n'est réservé ni écrit dans `kadre/contracts/registry/contracts.tsv` : la Phase 0 identifie les contrats à allouer, le plan de Phase 1 alloue les numéros (roadmap §7).
- Un manque de binding bloque sa tranche, pas les domaines indépendants (gate de sortie Phase 0).
- Aucun succès fictif, aucun fallback silencieux, aucune décision `Unsupported` muette : chaque limite est une décision explicite documentée (roadmap §1).
- Le probe `isAvailable()` ne consomme pas le fd transmis par `WAYLAND_SOCKET` ; le run le réutilise puis le ferme (roadmap Phase 0, item 4).
- Les documents sont en français et suivent les conventions du dépôt : `KFFI-REQUIREMENTS.md` = table unique à 8 colonnes ; designs = H1 + `## But` + `## Préconditions KFFI` (convention `APPKIT-PHASE-4-INPUT-DESIGN.md`) ; `BACKEND-CAPABILITIES.md` = légende §2 (`G`/`C`/`N`/`N(op)`/`S`/`H`).
- Ne pas modifier l'API publique, `DESIGN.md` §9.6 ni le statut des capabilities existantes ; une contradiction est résolue dans la spécification concernée avant activation (roadmap §1) — ici, seule la colonne Wayland de la matrice §4 de `BACKEND-CAPABILITIES.md` peut être ajustée si une décision la contredit.
- Cible : Linux sur JVM 25, glibc et musl (roadmap en-tête).

## Review Focus

Entrées et conditions que les décisions de cette phase doivent couvrir, et où chaque risque est fixé :

1. `WAYLAND_SOCKET` contenant un entier non valide, un fd fermé ou un fd non-Wayland — le probe ne fait qu'analyser la chaîne, n'émet aucun syscall sur le fd et n'échoue jamais brutalement ; le run seul réutilise puis ferme le fd. Fixé par la spécification du probe (Task 5, règles R3/R7) et un scénario négatif du design de tranche (Task 6, SN-2).
2. Compositor présent mais protocole requis absent (pas de `xdg_wm_base`, ex. kiosque/embedded) — erreur explicite avant session, jamais un succès dégradé. Fixé par la table « failures avant session » (Task 6).
3. Différence glibc/musl dans la résolution de symboles — la baseline enregistre les deux planchers et la règle « lookup process d'abord, candidats SONAME ensuite » (Task 3, B-4).
4. Lecture préparée abandonnée / flush saturé / wake perdu — risques §7 de la roadmap que le design de tranche doit énumérer en scénarios, pas les dissimuler (Task 6, scénarios SN-5/SN-6).
5. Une preuve AppKit `jvm` satisfaisant un contrat Wayland — l'allocation prévue de contrats exige la distinction adapter/libc/compositor/protocoles dans les targets de preuve (Task 6, §allocation, contrainte roadmap §7).

---

### Task 1: Audit des bindings KFFI publiés (wayland + posix)

**Files:**
- Create: `kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md`

**Interfaces:**
- Consumes: artifacts Maven `org.graphiks:kffi-wayland-jvm:1.0.0-SNAPSHOT` et `org.graphiks:kffi-posix-jvm:1.0.0-SNAPSHOT` (Central Portal Snapshots, `settings.gradle.kts:26`) ; source KFFI de référence si présente sur disque (chemin historique `/Users/chaos/workspace/graphiks-kffi`, à revérifier) ; inventaire des besoins de `kadre/WAYLAND-IMPLEMENTATION-ROADMAP.md` §3.2.
- Produces: le verdict par domaine (présent / gap confirmé) et la version exacte des snapshots, consommés par Task 2 (lignes KFFI-REQUIREMENTS), Task 3 (versions bindées) et Task 6 (préconditions KFFI du design).

- [ ] **Step 1: Résoudre et pigner les snapshots publiés**

Pour chaque artifact (`kffi-wayland-jvm`, `kffi-posix-jvm`), exécuter et consigner la sortie :

```bash
BASE=https://central.sonatype.com/repository/maven-snapshots/org/graphiks
curl -fsSL "$BASE/kffi-wayland-jvm/1.0.0-SNAPSHOT/maven-metadata.xml"
# extraire la <snapshotVersion><value> du jar, puis :
curl -fsSL -o /tmp/kffi-wayland-jvm.jar "$BASE/kffi-wayland-jvm/1.0.0-SNAPSHOT/kffi-wayland-jvm-<value>.jar"
sha256sum /tmp/kffi-wayland-jvm.jar
unzip -l /tmp/kffi-wayland-jvm.jar | awk '{print $4}' | grep '\.class$' | sort
```

(mêmes commandes pour `kffi-posix-jvm`). Consigner dans l'audit : version horodatée résolue, sha256, nombre de classes, date de publication.

- [ ] **Step 2: Inventorier l'API contre les besoins de la roadmap §3.2**

Vérifier chaque besoin par domaine — d'abord dans la source de référence si elle existe (`ls <kffi-src>/kffi-wayland/src`, `ls <kffi-src>/kffi-posix/src` ; consigner le chemin et le commit HEAD `git -C <kffi-src> rev-parse HEAD`), sinon par `javap -classpath <jar> <classe>` sur les classes candidates. Domaines et symboles à trancher :

1. Connexion/queues/registry/versions/erreurs/destruction de proxies : `wl_display_connect*`, `wl_display_get_registry`, `wl_registry_bind`, `wl_display_dispatch*`, `wl_display_roundtrip`, `wl_proxy_destroy`, `wl_proxy_marshal*`, gestion des erreurs (`wl_display_get_error`).
2. Pompe conforme `libwayland-client` : `wl_display_prepare_read`, `wl_display_read_events`, `wl_display_cancel_read`, `wl_display_flush`, `wl_display_dispatch_pending`, `wl_display_get_fd`.
3. Listeners révocables et ownership : mécanisme d'add/remove de listeners (`wl_*_add_listener`, `wl_proxy_*_listener`), copie des payloads, quiescence avant destruction.
4. POSIX glibc/musl : `PosixSymbols` (lookup process d'abord, candidats `libc.so.6`/`libc.so` ensuite), `PosixWakeup` (eventfd **et** repli pipe2 non bloquants close-on-exec), `PollFd`, `LinuxErrno`, fermeture idempotente.
5. Chargement paresseux : aucune résolution de symbole ni chargement natif à l'initialisation de classe — vérifier les initializers de `PosixSymbols`/`PosixWakeup` et des constantes Wayland (lookup au premier appel).
5. XKB : compilation keymap, état, keysym → présent ou absent dans kffi (aucun module `xkb` répertorié à ce jour : confirmer par grep `xkb` dans la source et le jar).
6. Interfaces cœur fenêtrage : `wl_compositor`, `xdg_wm_base`, `xdg_surface`, `xdg_toplevel` (états/erreurs), handles typés.
7. Input de base : `wl_seat`, `wl_pointer`, `wl_keyboard`, `wl_touch`, `wp_cursor_shape_device_v1`.
8. Extensions metrics/affichage : `wl_output`, `wp_viewporter`, `wp_fractional_scale_manager_v1`, `wp_presentation` (+feedback).
9. Décorations : `xdg-decoration` (`zxdg_decoration_manager_v1`/`zxdg_toplevel_decoration_v1`) — attendu absent, à confirmer.
10. Activation/icônes : `xdg_activation_token_v1`, `xdg_toplevel_icon_v1`.
11. Capture (phases 10+) : D-Bus asynchrone avec transfert de fd, PipeWire/SPA, `zwlr_screencopy` — attendu absent, à confirmer.

- [ ] **Step 3: Écrire l'audit avec un verdict par domaine**

Créer `kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md` : H1, paragraphe de mandat (portée Phase 0, artifacts pignés Step 1), puis la table de verdict :

```markdown
| # | Domaine (roadmap §3.2) | Besoin minimal tranche 1 | Présent dans kffi ? (symbole/classe, référence jar ou source) | Verdict | Gap ouvert (ID KFFI-REQ) | Bloque la tranche |
|---|---|---|---|---|---|---|
| 1 | Connexion, registry, proxies | connect/registry/bind/dispatch/destroy | … | usable \| gap | KFFI-WAYLAND-00x ou — | tranche 1 \| — |
```

une ligne par domaine 1-12 ci-dessus, chaque verdict justifié par l'évidence du Step 2 (nom de classe/symbole + où il a été vu). Conclure par : les bindings minimaux requis pour la tranche 1 (liste fermée), les blocages précis et les tranches qu'ils bloquent, et la version pignée retenue.

- [ ] **Step 4: Vérifier l'exhaustivité**

Vérifier : les 12 domaines ont une ligne ; aucune cellule « Présent ? » n'est vide ni « à confirmer » ; chaque `gap` a un futur ID listé (ou est volontairement sans ligne, justifié en note). Consigner les commandes réellement exécutées en fin de document (section `## Évidence`).

- [ ] **Step 5: Commit**

```bash
git add kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md
git commit -m "docs(wayland): audit the published KFFI wayland and posix bindings (phase 0)"
```

### Task 2: Ouvrir les gaps confirmés dans KFFI-REQUIREMENTS.md

**Files:**
- Modify: `kadre/KFFI-REQUIREMENTS.md` (table unique, 8 colonnes : `ID | Statut | Priorité | Domaine | Besoin KFFI | Usage Kadre bloqué | Workaround autorisé | Référence`)

**Interfaces:**
- Consumes: verdicts de Task 1 (une ligne par `gap`, jamais pour un `usable`).
- Produces: IDs `KFFI-WAYLAND-001..N` (et `KFFI-POSIX-00x` si un gap POSIX est confirmé) référencés par Task 3 et Task 6.

- [ ] **Step 1: Écrire une ligne par gap confirmé**

Pour chaque `gap` de l'audit (attendus d'après l'inventaire actuel : XKB ; xdg-decoration ; D-Bus/PipeWire ; tout manque cœur détecté — connexion/pompe/listeners), ajouter une ligne complète, format calqué sur `KFFI-OBJC-004` (ligne 14) :

```markdown
| KFFI-WAYLAND-001 | pending review | blocking | XKB keymap | lookup/compilation xkbcommon, état clavier et translation keysym, avec ownership et release déterministes | input clavier Wayland (phase 4) ; sans elle aucun mapping clavier promis | aucun mapping improvisé côté Kadre | WAYLAND-PHASE-0-KFFI-AUDIT.md §<domaine>, snapshot kffi-wayland <version pignée> |
```

Règles de remplissage : `Statut` ∈ `pending review`/`pending publication` (jamais `closed`) ; `Priorité` = `blocking` si la tranche dépendante est ≤ phase 3, sinon `later` avec la tranche nommée (ex. capture) ; `Workaround autorisé` = toujours une interdiction explicite (aucun binding local Panama, aucun XML de génération côté Kadre — roadmap §3.2) ; `Référence` = section de l'audit + version pignée.

- [ ] **Step 2: Vérifier la cohérence du registre**

```bash
grep -c "KFFI-WAYLAND" kadre/KFFI-REQUIREMENTS.md
awk -F'|' 'NR>9 && /^\| KFFI/ {print NF}' kadre/KFFI-REQUIREMENTS.md | sort -u
```

Attendu : le compte de lignes KFFI-WAYLAND égal au nombre de gaps de l'audit ; toutes les nouvelles lignes ont le même nombre de colonnes que les existantes.

- [ ] **Step 3: Commit**

```bash
git add kadre/KFFI-REQUIREMENTS.md
git commit -m "docs(kffi): open confirmed wayland binding gaps from the phase 0 audit"
```

### Task 3: Baseline Linux/JVM/libc, versions de protocoles et images de test

**Files:**
- Modify: `kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md` (ajout de la section `## Baseline`)

**Interfaces:**
- Consumes: Task 1 (versions pignées, symboles POSIX requis) ; héritage `kadre-old/scripts/ci-wayland-runtime.sh` (Weston headless pixman, `XDG_RUNTIME_DIR` 0700) et `kadre-old/docker/wayland-test/Dockerfile` (`eclipse-temurin:25-jdk-noble` + weston).
- Produces: la baseline B-1..B-6 citée par le design de tranche (Task 6) et future `capabilities/wayland.md`.

- [ ] **Step 1: Écrire la baseline avec les décisions B-1 à B-6**

Ajouter à `kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md` une section `## Baseline` contenant :

- **B-1 JVM** : plancher JVM 25 (Temurin), aligné sur l'en-tête de la roadmap et l'image CI historique.
- **B-2 libc** : glibc plancher = celui d'Ubuntu 24.04 (Noble, glibc 2.39) ; musl = version d'Alpine retenue à l'étape 2. Règle de résolution : `SymbolLookup.loaderLookup()` (process) d'abord, puis candidats SONAME ordonnés `libc.so.6`, `libc.so` — jamais de SONAME codé en dur hors de cette liste (reprise de la contrainte du plan 2026-07-17-04, encore valide).
- **B-3 images de test reproductibles** : glibc = `eclipse-temurin:25-jdk-noble` + `weston` (paquet distro, version relevée au premier run et inscrite ici) ; musl = image Alpine `:<version>` + `weston` (paquet `community`). Si weston n'est pas disponible en paquet Alpine à l'exécution de cette tâche, enregistrer la décision : preuves compositor sur glibc, preuves POSIX-symboles sur musl, et le blocage documenté (tranche 1 reste glibc-only pour la preuve O3, extensible dès l'image musl existe).
- **B-4 versions minimales de protocoles bindées** : règle de décision — version bindée = `min(annoncée par le global, max supportée par le binding kffi)` ; exigence d'admission = `annoncée ≥ minimal requis par Kadre`. Remplir le tableau à partir des constantes de version du jar kffi (Task 1) avec la fonctionnalité justifiant chaque plancher :

```markdown
| Interface | Minimal requis | Justification (fonction utilisée) | Source (constante kffi) |
|---|---|---|---|
| wl_compositor | v4 | `damage_buffer` | … |
| xdg_wm_base | v? | … | … |
| wl_seat | v? | `wl_pointer.frame` (v5) / curseur shape | … |
| wp_viewporter / wp_fractional_scale / wp_presentation | v1 | metrics et redraw (phase 3) | … |
```

chaque ligne justifiée par une fonction réellement utilisée par les phases 1-3 ; si un plancher dépend d'une décision de phase ultérieure, l'inscrire « décidé en phase N » avec la règle, jamais un chiffre inventé.
- **B-5 Weston headless** : reprise de la recette historique (`--backend=headless-backend.so --renderer=pixman --width=800 --height=600 --idle-time=0`, socket dédié, `XDG_RUNTIME_DIR` 0700) comme véhicule de preuve O3 de la tranche 1 — les scripts sont réécrits dans la nouvelle architecture en phase 1 (`scripts/ci-wayland-runtime.sh`, topologie §4), rien n'est porté de `kadre-old` tel quel.
- **B-6 hors baseline** : toute distro/libc non listée est non testée, pas non supportée — la phrase exacte de cette limite est écrite dans la section.

- [ ] **Step 2: Vérifier les planchers contre les constantes kffi**

Pour chaque ligne du tableau B-4, citer la constante du jar/source kffi qui porte la version max supportée (`javap` ou grep source). Une ligne sans source kffi citée = la ligne passe en « décidé en phase N ».

- [ ] **Step 3: Commit**

```bash
git add kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md
git commit -m "docs(wayland): record the linux/jvm/libc baseline and protocol floors (phase 0)"
```

### Task 4: Décisions contractuelles — décorations, renderer/commit/lease, lifecycle

**Files:**
- Create: `kadre/WAYLAND-PHASE-0-DECISIONS.md`
- Modify (si et seulement si contradiction): `kadre/BACKEND-CAPABILITIES.md` — cellules de la colonne Wayland de la matrice §4

**Interfaces:**
- Consumes: `PUBLIC-API-CATALOG.md:271,298` (`WindowSpec.decorations: WindowDecorations = System` ; `enum WindowDecorations { System, Borderless }`) ; `BACKEND-CAPABILITIES.md` §2 (légende `G/C/N/N(op)/S/H`) et ligne 61 (« lifecycle à trois axes », colonne Wayland = `G`) ; `DESIGN.md` §9 (surfaces/fenêtres) et §9.6 (interactions) ; roadmap §3.4-3.5.
- Produces: les décisions D1-D3 et la spec du probe (Task 5) que le design de tranche (Task 6) et les phases 1+ doivent respecter.

- [ ] **Step 1: Écrire D1 — défaut `WindowDecorations.System` sur Wayland**

Dans `kadre/WAYLAND-PHASE-0-DECISIONS.md` (H1 + `## But` + `## Préconditions KFFI`, convention des designs) : le défaut public du catalogue (`WindowDecorations.System`) est conservé, mais sur Wayland toute configuration exigeant `System` est refusée exactement — `Unsupported(Decorations)` sur le verbe concerné — tant qu'une négociation xdg-decoration côté serveur n'est pas prouvée ; jamais de remplacement silencieux par `Borderless`, jamais de chrome dessiné par Kadre. Noter la conséquence assumée : jusqu'à la levée de cette limite (gap KFFI xdg-decoration, Task 2), les specs de fenêtres Wayland passent `Borderless` explicitement ; la limite figure comme décision de départ du tableau §3.5 de la roadmap. Si la matrice §4 de `BACKEND-CAPABILITIES.md` porte une ligne decorations dont la cellule Wayland contredit D1, corriger la cellule avec le code de légende exact (`N(op)`) — sinon ne rien modifier et le noter.

- [ ] **Step 2: Écrire D2 — frontière renderer/commit/lease**

Table d'ownership de l'état `wl_surface` : le peer de surface du runtime est l'unique écrivain de l'état pending (`attach`, `commit`, frame callbacks, scale, viewport) ; un renderer externe possède ses buffers et ne reçoit `DesktopNativeWindowHandle.Wayland(displayAddress, surfaceAddress)` que dans le callback `Window.withDesktopHandle`, sur le thread host, sous lease de lifetime — aucune API `present` publique, aucun handle retenu après retour, un seul owner de commit (course lease/close : le lease est invalidé avant destruction des proxies ; un renderer qui retient les adresses viole le contrat, documenté comme tel). Alignement verbatim avec roadmap §3.4.

- [ ] **Step 3: Écrire D3 — représentation du lifecycle sans invention**

Table de mapping axe lifecycle → source protocolaire : activation/focus depuis `wl_keyboard` enter/leave uniquement ; états de fenêtre depuis les états `xdg_toplevel` configurés uniquement ; session headless sans fenêtre = `Background + Inactive` (roadmap phase 1) ; aucun événement d'occlusion ou de visibilité synthétisé — un axe non observable reçoit sa valeur fixe documentée, jamais un événement inventé. La ligne 61 de la matrice (« lifecycle à trois axes », `G`) reste valide : ce sont les sources des axes qui sont définies, pas une rétraction de garantie. Citer `DESIGN.md` pour la définition des trois axes (section lifecycle, à localiser par grep `trois axes` avant rédaction).

- [ ] **Step 4: Vérifier l'absence de contradiction**

```bash
grep -n -i "decor" kadre/BACKEND-CAPABILITIES.md | head
```

Confronter chaque ligne decorations/lifecycle avec D1/D3 ; corriger uniquement les cellules Wayland contredites, en citant la décision dans le texte d'accompagnement de la section concernée.

- [ ] **Step 5: Commit**

```bash
git add kadre/WAYLAND-PHASE-0-DECISIONS.md kadre/BACKEND-CAPABILITIES.md
git commit -m "docs(wayland): close decorations, renderer-lease and lifecycle decisions (phase 0)"
```

### Task 5: Spécification du probe `isAvailable()`

**Files:**
- Modify: `kadre/WAYLAND-PHASE-0-DECISIONS.md` (section `## Probe isAvailable()`)

**Interfaces:**
- Consumes: SPI interne `DesktopBackendProvider` (`kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/desktop/DesktopBackendProvider.kt:28-37`, `isAvailable(): Boolean`) ; précédent AppKit (`AppKitBackendProvider.kt:76`, probe environnement sans charge native) ; gestion des exceptions de découverte (`DesktopHostFacade.kt`, `availability-exception`) ; sélecteur Linux `Auto → [Wayland, X11]` (`DesktopHostFacade.kt:165`).
- Produces: le contrat R1-R8 que l'implémentation du provider en phase 1 devra satisfaire et tester.

- [ ] **Step 1: Écrire les règles R1-R8**

Section `## Probe isAvailable()` de `kadre/WAYLAND-PHASE-0-DECISIONS.md` :

- **R1** : aucune charge native — aucun `SymbolLookup`, aucun `System::loadLibrary`, aucune connexion : même famille d'exigences que le probe AppKit (environnement seulement).
- **R2** : plateforme ≠ Linux ⇒ `false` immédiatement.
- **R3** : endpoint — si `WAYLAND_SOCKET` est défini : analyse entière uniquement ; valeur non entière ou hors bornes de fd ⇒ `false` ; valeur valide ⇒ `true` sans AUCUN syscall sur ce fd (pas de `fcntl`, pas de `read`, pas de `stat` sur `/proc/self/fd`) ; le fd reste la propriété du host au stade du probe.
- **R4** : sinon, chemin du socket — `XDG_RUNTIME_DIR` défini et `WAYLAND_DISPLAY` défini ⇒ `<XDG_RUNTIME_DIR>/<WAYLAND_DISPLAY>` ; `WAYLAND_DISPLAY` absent ⇒ `<XDG_RUNTIME_DIR>/wayland-0` ; `XDG_RUNTIME_DIR` absent ⇒ `false`. Le chemin existe et est un socket (attribut POSIX) ⇒ `true`.
- **R5** : coût borné — O(1), aucun blocage, aucun fd ouvert, aucun état muté ; répétable et thread-safe ; pur (deux appels successifs renvoient la même valeur si l'environnement n'a pas changé).
- **R6** : diagnostic — chaque `false` porte une raison interne énumérée (`platform`, `wayland-socket-invalid`, `runtime-dir-absent`, `socket-absent`) publiée en diagnostic interne, jamais dans l'API publique.
- **R7** : au run — le fd `WAYLAND_SOCKET` est réutilisé, possédé par la session, et fermé exactement une fois au teardown ; le probe n'a ni dupliqué ni consommé le fd (reprise roadmap Phase 0, item 4).
- **R8** : totalité — le probe ne lève jamais (`Exception` et `LinkageError` attrapés en interne ⇒ `false` + raison), en complément du filet `availability-exception` du sélecteur.

- [ ] **Step 2: Vérifier la cohérence SPI**

Relire `DesktopBackendProvider.kt:28-37` et `AppKitBackendProvider.kt:69-76` : la spec ne exige rien que le SPI ne permette pas (retour `Boolean`, pas de paramètre, appel multiple autorisé). Noter dans la section le filet existant du sélecteur (`availability-exception`) et le rôle de R8 dedans.

- [ ] **Step 3: Commit**

```bash
git add kadre/WAYLAND-PHASE-0-DECISIONS.md
git commit -m "docs(wayland): specify the isAvailable probe contract including WAYLAND_SOCKET (phase 0)"
```

### Task 6: Design de première tranche (phase 1) — failures, panne, fermeture, scénarios, contrats

**Files:**
- Create: `kadre/WAYLAND-PHASE-1-HOST-DESIGN.md`

**Interfaces:**
- Consumes: audit (Task 1), baseline B-1..B-6 (Task 3), décisions D1-D3 et probe R1-R8 (Tasks 4-5) ; vocabulaire des failures de `kadre/OPERATION-CONTRACTS.md` (`Unsupported(op)`, `Invalid(field)`, …) ; `SessionOutcome { Completed, Failed, Stopped }` (`kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/application/Application.kt:66`, `DESIGN.md:185`) ; familles de contrats fermées (`kadre/TEST-STRATEGY.md` §3 : `API SES POL SUR DSP WIN INP GPD CAP BCK INT`) ; oracles O2/O3 (`TEST-STRATEGY.md` §2.1, lignes 53-54) ; pompe conforme `libwayland-client` (roadmap §3.3).
- Produces: le design ciblé que le plan exécutable de Phase 1 développera, avec la liste fermée de scénarios SN-x/P-x et le tableau d'allocation prévue de contrats.

- [ ] **Step 1: Écrire le squelette conforme**

H1 « Wayland Phase 1 — Host, standalone headless et pompe », `## But` (périmètre roadmap Phase 1 + exclusions explicites : pas de fenêtre, pas d'input, livrable limité au headless), `## Préconditions KFFI` (les IDs de Task 2 requis pour la tranche 1, statuts exigés à l'activation, et la règle « un manque bloque sa tranche, pas les domaines indépendants »).

- [ ] **Step 2: Écrire les trois tables de sémantique d'échec**

- **Failures avant session** (aucune ressource créée, aucun callback) : socket absent/inconnectable, `WAYLAND_SOCKET` invalide au run (R3/R7), protocole requis absent (`wl_compositor`, `xdg_wm_base` — B-4), primitive de wake absente (ni eventfd ni pipe2 — audit domaine 4), échec de négociation de version < minimal (B-4). Chaque ligne : condition ⇒ résultat `run()`/`attach()` (`KadreResult` failure, vocabulaire `OPERATION-CONTRACTS.md`) + invariant « zéro fd/zéro proxy fui ».
- **Panne après admission** : erreur `wl_display` ou déconnexion compositor ⇒ terminaison ordonnée des ressources avec la failure autorisée, puis `SessionOutcome.Failed` ; interdictions verbatim roadmap §3.3 : pas de reconnexion transparente, pas de migration X11, pas de réutilisation d'IDs, nouvelle connexion ⇒ nouveau host.
- **Fermeture** : `stop` idempotent ; ordre de teardown (plus aucun callback éligible ⇒ destruction des proxies dans l'ordre d'ownership ⇒ flush final ⇒ fermeture du fd `WAYLAND_SOCKET`/socket et des fds de wake, chacun un owner idempotent) ; callback tardive après retour de `stop` interdite ; arrêt depuis un autre thread et annulation couverts.

- [ ] **Step 3: Écrire les scénarios positifs/négatifs numérotés**

Table fermée de scénarios, chacun : id, entrée, observation attendue via l'API Kadre uniquement, oracle :

```markdown
| ID | Scénario | Observation attendue | Oracle |
|---|---|---|---|
| P-1 | start/stop headless ×3 cycles sur Weston (B-5) | session démarrée/arrêtée, zéro fd fui (`/proc/self/fd` compté), zéro callback tardive | O3 |
| P-2 | wake répété sans fenêtre ni événement compositor | pompe réarmée à chaque wake (roadmap §3.3) | O2 |
| SN-1 | compositor absent (socket inexistant) | failure avant session, zéro ressource | O3 |
| SN-2 | `WAYLAND_SOCKET="pas-un-fd"` puis run valide | run utilise l'endpoint normal, fd fictif jamais touché (R3) | O3 |
| SN-3 | `WAYLAND_SOCKET=<fd valide>` au run | fd réutilisé puis fermé exactement une fois (R7) | O3 |
| SN-4 | global `xdg_wm_base` absent (compositor amputé) | failure avant session « protocole requis absent » | O3 |
| SN-5 | stop pendant `poll` depuis un autre thread | réveil, teardown complet, `SessionOutcome.Stopped` | O2 |
| SN-6 | flush saturé (EAGAIN) puis drain | surveillance écriture, aucun blocage, aucun payload perdu | O2 |
| SN-7 | déconnexion compositor après admission | teardown + `SessionOutcome.Failed`, pas de reconnexion | O3 |
```

Couverture exigée des risques §7 de la roadmap : lecture préparée abandonnée, flush saturé, wake perdu, stop pendant poll, fds fermés — chaque risque figure dans au moins un scénario.

- [ ] **Step 4: Écrire l'allocation prévue de contrats**

Table famille → sujet → oracle, sans ID fermé (contrainte globale) :

```markdown
| Famille (TEST-STRATEGY §3) | Sujet prévu | Oracle visé | Distinction de target exigée |
|---|---|---|---|
| BCK | probe isAvailable et discovery Wayland (R1-R8) | O2 | target=adapter wayland, libc, compositor/version |
| SES | lifecycle start/stop headless, panne après admission | O2 | idem |
| SUR | pompe : prepare/read/cancel/flush, wake réarmable | O3 | idem |
```

avec la règle roadmap §7 verbatim : le gate Wayland distingue adapter, libc, compositor/version et protocoles testés ; aucune preuve AppKit `jvm` ne satisfait un contrat Wayland ; le schéma/agrégateur du validateur évolue avant activation si nécessaire (décision notée pour le plan de Phase 1, rien de modifié dans `contracts/validator` ici).

- [ ] **Step 5: Vérifier la couverture de gate**

Checklist interne : les tables des trois sémantiques d'échec sont fermées (aucune ligne « à définir ») ; tout risque §7 couvert par un scénario ; toute précondition KFFI cite un ID de Task 2 ; aucune référence à un type ou ID inexistant.

- [ ] **Step 6: Commit**

```bash
git add kadre/WAYLAND-PHASE-1-HOST-DESIGN.md
git commit -m "docs(wayland): write the phase 1 host tranche design with scenarios and contract plan (phase 0)"
```

### Task 7: Gate de sortie M0 — validation et clôture

**Files:**
- Modify: `kadre/WAYLAND-IMPLEMENTATION-ROADMAP.md` (cases Phase 0)

**Interfaces:**
- Consumes: Tasks 1-6.
- Produces: Phase 0 marquée terminée dans la roadmap ; l'état M0 vérifiable par un relecteur.

- [ ] **Step 1: Vérifier le registre de contrats**

```bash
./gradlew :kadre:contracts:validator:check --console=plain
```

Attendu : PASS (la Phase 0 ne touche pas `contracts.tsv` ; la commande prouve l'absence de régression accidentelle).

- [ ] **Step 2: Cocher la gate de sortie item par item**

Relire la gate Phase 0 de la roadmap : décisions documentées (Tasks 4-5), bindings minimaux vérifiés ou blocages KFFI identifiés précisément (Tasks 1-2), scénarios positifs/négatifs définis (Task 6). Cocher les cinq cases `- [ ]` de la Phase 0 dans `kadre/WAYLAND-IMPLEMENTATION-ROADMAP.md` (`- [x]`) — et uniquement si chaque item a bien son livrable.

- [ ] **Step 3: Relecture croisée des documents**

Relire les quatre livrables dans l'ordre audit → requirements → décisions → design : chaque ID cité existe, chaque version pignée apparaît identique partout, aucune promesse de code (aucun « sera livré en phase 1 » qui ressemble à un engagement de date), français cohérent.

- [ ] **Step 4: Commit de clôture**

```bash
git add kadre/WAYLAND-IMPLEMENTATION-ROADMAP.md
git commit -m "docs(wayland): close phase 0 — baseline, decisions and tranche design recorded (M0)"
```

---

## Hors périmètre (rappel)

- Toute écriture de code, module, workflow CI, script ou image (phases 1+).
- `kadre/capabilities/wayland.md`, `evidence.tsv`, workflows — première livraison en phase 1 (topologie §4).
- Allocation effective d'IDs dans `contracts.tsv` et évolution du validateur — au plan de Phase 1 (roadmap §7).
- Toute décision sur capture portal/PipeWire au-delà de l'ouverture du gap (phase 10).
