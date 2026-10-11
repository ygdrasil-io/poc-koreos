# Kadre Desk Tour — démo de référence

Kadre Desk Tour est une démo Kadre de référence. Elle montre comment intégrer une
UI Compose dans une fenêtre détenue par Kadre, sans créer de fenêtre top-level
Compose — et, sur le web, comment la même visite vit dans une page, sans fenêtre
du tout. Le module est multiplateforme (jvm + wasmJs) avec deux hôtes livrés :
le **bureau** (host AppKit, binaire exécutable sur macOS) et le **web** (host
wasmJs, servi en statique dans un navigateur). Cela ne redéfinit pas la démo
comme une démo « macOS » ou « navigateur » : le modèle d'intégration vise les
plateformes Kadre, et les deux hôtes portent les mêmes scénarios.

La démo ne dépend que de l'API publique de Kadre, et son cœur ne connaît ni
Compose ni AppKit : `core` porte l'état, le journal et les présentations, `ui`
porte Compose, `appkit` porte le bridge de rendu bureau. Un test de frontière
refuse tout import `org.graphiks.kadre.internal.*` dans le sample.

## Architecture : le miroir

Le module est un miroir à deux faces qui partagent la même moitié :

- `commonMain` — `core` (le store, le dispatcher, les présentations, le
  contrat `NoteWindowHost`) et `ui` (l'application Compose entière, la note
  comprise). Compilés pour les deux cibles, ils ne connaissent ni le DOM ni
  AppKit.
- `jvmMain` — le host bureau : `DeskTourMain` (attach AppKit, la fenêtre
  principale EST une fenêtre Kadre), le bridge Compose–AppKit (`appkit/`) et
  `KadreNoteWindows` — chaque note est une **seconde fenêtre Kadre**.
- `wasmJsMain` — le host web : `DeskTourWebMain` (attach Kadre sur le
  conteneur de la page, qui EST la fenêtre de la démo), `WebTourGateway` et
  `DemoNoteWindows` — chaque note est un **div de la démo** avec son propre
  viewport Compose.

La couture est le contrat `NoteWindowHost` : le bureau l'implémente avec des
fenêtres Kadre, le web avec des divs du document et leurs viewports — Kadre
refuse une fenêtre enfant du même document (la validation `requestWindow`
exige un contexte de navigation distinct), donc les notes web appartiennent à
la démo, pas à Kadre. Le parcours, le journal et les refus sont identiques ;
seule la matière change.

Deux faits de la sonde (`PROBE-NOTES.md`) portent le portage :

1. **Le bouillonnement d'entrée.** Compose écoute sur le canvas qu'il crée,
   Kadre sur le conteneur ; un clic touche le canvas d'abord et remonte vers
   le conteneur — les DEUX piles voient chaque entrée, aucune ne supprime
   l'événement. Un clic qui crée une note prouve donc que l'attach Kadre est
   vivant : c'est le mécanisme du filet e2e.
2. **Les chemins de resize diffèrent.** Kadre suit le conteneur
   (ResizeObserver) ; Compose ne relit sa boîte qu'aux resizes de la
   FENÊTRE. La page fait donc du conteneur la fenêtre elle-même (plein
   viewport, couche à notes en surcouche qui n'entre jamais dans sa mise en
   page) : tout resize passe par la fenêtre et redessine les deux piles.

## Lancer

### Bureau (macOS)

```bash
./gradlew :kadre:samples:desk-tour:runJvm
```

Le flux de lancement desktop est inchangé ; seuls les noms de tâches ont suivi
le DSL « JVM binaries » de KGP 2.4 (le plugin `application` lui est
incompatible en multiplateforme) : `run` s'appelle désormais `runJvm`, la
distribution `installJvmDist`.

Le binaire est aussi installable (`:kadre:samples:desk-tour:installJvmDist`) ; il
démarre alors avec `build/install/desk-tour/bin/desk-tour`. La démo demande
`-XstartOnFirstThread`, déjà fourni par la tâche.

**Permissions.** L'inventaire des écrans dépend de l'autorisation
d'enregistrement d'écran du système. Tant qu'elle n'est pas accordée, l'espace
Écrans l'écrit noir sur blanc et propose le bouton qui déclenche la demande ;
si le système ne la présente pas, elle s'accorde dans Réglages Système →
Confidentialité et sécurité → Enregistrement de l'écran. Les permissions de
capture sont affichées, par portée, dans l'espace Périphériques.

### Web (navigateur)

```bash
./gradlew :kadre:samples:desk-tour:wasmJsBrowserDistribution
cd kadre/samples/desk-tour/build/dist/wasmJs/productionExecutable
python3 -m http.server 8080
# puis ouvrir http://127.0.0.1:8080
```

N'importe quel serveur statique convient sur
`build/dist/wasmJs/productionExecutable/` (~13 Mo : le wasm Kotlin, le wasm
Skiko, le chargeur webpack, la page) — aucun en-tête CORS/COOP/COEP, aucun
`skiko.js` à câbler, la page s'amorce seule (sonde, Q4). Le serveur doit
connaître le type MIME `application/wasm` : c'est le cas du `http.server` de
Python courant (vérifié en 3.14) et du serveur du filet e2e.

### Le filet e2e

Le filet de régression commis (chromium, 5 tests grossiers) sert une
distribution déjà construite — la recette complète est dans
[`e2e/README.md`](e2e/README.md) : construire la distribution, puis
`npm test` dans `e2e/`.

## Le parcours

Cinq espaces dans la barre du bas, plus le tiroir « Détails API ». La fenêtre
principale s'ouvre sur le journal. Le parcours est le même sur les deux hôtes ;
seule la matière des notes diffère.

### Bureau

« Créer une note flottante » ouvre une **seconde fenêtre Kadre** (420 × 240), avec
sa propre scène Compose — c'est la démonstration centrale : Kadre possède les
fenêtres, Compose n'en crée aucune. Chaque note présente son titre dans un champ
éditable et quatre actions : renommer, demander l'attention, changer la
décoration, fermer. Un renommage se voit dans la fenêtre de la note et dans sa
barre de titre. Une action dont la capability manque est désactivée et porte son
motif. Sous les notes, la fenêtre principale affiche sa taille logique et son
état de focus observés.

### Web

« Créer une note flottante » pose un **div de la démo** (420 × 240, cascade
depuis le coin supérieur gauche) dans la couche à notes de la page, et le host
y monte un viewport Compose dédié. Le renommage et la fermeture fonctionnent
comme au bureau ; « demander l'attention » et « changer la décoration » restent
affichés, désactivés, avec leur motif — une note web n'est pas une fenêtre à
signaler ni à décorer, et la démo dit pourquoi plutôt que de cacher le
contrôle.

### Activité

Le journal corrélé : une entrée par intention, avec son libellé en français, son
statut (`En cours`, `Terminé`, `Refusé`, `Annulé`, `Indisponible`), la surface
publique appelée et, pour un refus, son motif. Une entrée n'est résolue qu'après
l'`…Outcome` terminal de l'opération : rien n'est annoncé qui n'ait été publié.

### Écrans

L'inventaire réellement observé : nom, écran principal, taille, échelle, mode et
fréquence de rafraîchissement, connexion. Les états non énumérés ont chacun leur
phrase — permission pas encore accordée, refusée (avec ou sans nouvelle tentative
possible), inventaire pas encore disponible — et une liste vide ne les remplace
jamais.

### Interactions

- **Saisie de texte** : « Ouvrir une session de saisie » ouvre la session de
  saisie publique de Kadre (`SurfaceInput.openTextInput`) sur la surface de la
  fenêtre principale, et affiche son état puis le dernier événement reçu.
- **Entrée observée** : modificateurs, touches enfoncées, pointeurs avec leur
  position dans l'origine que le host rapporte, et le dernier événement décrit en
  français.
- **Capacités d'entrée** : clavier, pointeur, tactile, gestes, glisser-déposer,
  saisie de texte et entrées brutes, chacune avec son état réel et son motif
  lorsqu'elle n'est pas servie.

La démo **observe** l'entrée ; elle ne la synthétise jamais.

### Périphériques

Les périphériques rapportés par le host (nom et type), le nombre de gamepads, et
les permissions de capture par portée, écran et fenêtre. L'espace montre l'état
honnête du host courant : sur le web, la réponse du navigateur lui-même
(`permissions.query` sur `display-capture`), et l'indisponibilité là où le
navigateur ne peut pas répondre.

### Détails API

Le bouton « Détails API » ouvre un tiroir (fermé par défaut, refermable en cliquant
à côté) qui donne, pour chaque action de la démo, la surface publique qu'elle
exerce :

| Action utilisateur | Surface publique démontrée |
|---|---|
| Créer une note flottante | `WindowManager.requestWindow`, `WindowRequest`, `WindowRequestOutcome` |
| Modifier une fenêtre | `Window.apply`, `WindowUpdate`, `WindowUpdateOutcome` |
| Suivre les écrans | `DisplayManager.state`, `DisplayManager.events` |
| Suivre la surface | `HostSurface.state`, `HostSurface.events`, `HostSurface.apply` |
| Saisir ou déposer | `HostSurface.input`, `SurfaceInput.events`, `DropOffer` |
| Diagnostiquer une option | `Capability`, `KadreResult`, `KadreDiagnostics` |

Le tiroir rappelle aussi les entrées du journal et leur statut.

## Tests

```bash
./gradlew :kadre:samples:desk-tour:jvmTest
```

116 tests couvrent la réduction du store, la corrélation du dispatcher, les
présentations (capabilities, permissions, écrans, périphériques, entrée, saisie
de texte), le bridge de montage, et trois frontières : `core` sans renderer, `ui`
sans AppKit ni KFFI ni AWT, et le sample sans import interne Kadre. La suite
commune (`commonTest`) tourne sur jvm ; la tâche wasmJs de test navigateur est
désactivée explicitement (aucun harnais karma/webpack configuré pour ce sample —
voir `build.gradle.kts`), et le binaire wasm est couvert bout-en-bout par le
filet e2e.

```bash
cd kadre/samples/desk-tour/e2e && npm test   # distribution construite au préalable
```

Ces tests ne remplacent pas l'exécution : la démo a déjà livré une tranche verte
mais inerte à l'écran. Un coup d'œil après chaque changement reste la seule preuve
qu'une fonction existe vraiment, et il faut **lire** ce qu'on voit — du vocabulaire
de développeur a déjà atteint l'interface sans que personne ne le remarque. Sur le
web, le filet e2e prouve la structure (DOM, géométrie, zéro erreur), pas les
pixels : la charte manuelle, [`manual/web-charter.md`](manual/web-charter.md),
décrit ce qu'il reste à vérifier à l'œil et à la main.

## Limites connues

### Côté bureau

1. **Le défilement.** L'hôte AppKit publie les événements de défilement, mais la
   scène Compose du sample ne les consomme pas encore : un contenu plus haut que
   la fenêtre reste inatteignable, et `Modifier.verticalScroll` n'y change rien.
   La conception des espaces s'y adapte — sections réparties en onglets plutôt
   qu'empilées. C'est un travail de l'hôte du sample, pas du backend.
2. **Deux chemins de saisie coexistent.** La session de saisie publique s'ouvre
   depuis l'espace Interactions et reçoit de vraies frappes — c'est ce chemin qui
   terminait le processus tant que la valeur envoyée par AppKit au callback IME
   était lue comme une chaîne attribuée. Le champ d'une note, lui, reçoit la
   saisie par la route de caractères logiques du bridge. Les deux chemins sont
   exercés, et aucun n'est l'autre.
3. **Fermeture.** Le processus se termine avec sa dernière fenêtre, notes
   comprises. Compose ne fournit pas de fermeture attendable :
   `ComposeScene.close()` annule les effets asynchrones sans garantir qu'ils sont
   terminés à son retour, donc un callback très tardif peut encore être planifié
   pendant l'arrêt de l'hôte AppKit. La fermeture visible fait partie de la preuve
   opérateur, pas d'une garantie de quiescence asynchrone ; une évolution amont de
   Compose sera nécessaire pour en faire un contrat portable.
4. **Un arrêt anormal intermittent** (SIGBUS à la fermeture) a été observé pendant
   le spike, sans erreur visible ni code de sortie parlant. Il n'est pas résolu.

### Côté web

5. **Résidu par note fermée.** Compose n'offre aucune API publique de dispose
   pour un viewport (`ComposeViewport` rend `Unit` ; `ComposeWindow.dispose()` est
   interne, la sonde Q3). Fermer une note retire son div — c'est idempotent,
   silencieux, et le filet e2e l'épingle (y compris le resize post-fermeture qui
   réveille l'écouteur du viewport retiré sans erreur) — mais la scène de
   composition, la couche Skia et l'écouteur de resize de la note restent en
   mémoire pour la vie de la page. Résidu borné (une note fermée ne grandit plus),
   invisible, acceptable pour une démo et documenté.
6. **Les resizes passent par la fenêtre.** Kadre suit le conteneur
   (ResizeObserver), Compose ne relit sa boîte qu'aux resizes de la fenêtre
   (sonde, Q2-d). La page fait du conteneur la fenêtre entière, donc le chemin
   navigateur couvre tout le parcours ; un resize purement structurel du
   conteneur (jamais produit par la démo) laisserait le canvas Compose à sa
   taille périmée.
7. **Un filet e2e grossier, au couplage assumé.** Le filet (chromium seul)
   n'affirme que du DOM et de la géométrie — le canvas reste opaque : aucun
   pixel, aucun texte lu dedans ; il ne prouve pas le rendu, seulement la
   structure et l'absence d'erreur (la lecture visuelle est l'affaire de la
   charte manuelle). Le test de cascade vise la partie découverte du bouton de
   création quand une note le recouvre : si un jour la mise en page laissait une
   note couvrir le bouton ENTIÈREMENT, le test échouerait honnêtement
   (« aucune partie cliquable ») — couplage conscient à la mise en page actuelle.
8. **Huit places de cascade.** La place d'une note suit sa clé monotone modulo 8
   (fermer puis rouvrir ne rejoue jamais la place d'une note encore ouverte) ;
   une neuvième note VIVANTE réutiliserait la première place. Borne de démo,
   jamais atteinte par le parcours, non affirmée par le filet.
9. **Le formatage du partagé ne doit rien à une locale.** Le format des taux de
   rafraîchissement (espace Écrans) est écrit sans `String.format` — le common
   n'en a pas et le formatage JVM dépend d'une `Locale`. Le code partagé n'opère
   aucune casse ; le jour où il en opère, `lowercase()`/`uppercase()` communes
   sont invariantes par locale et n'héritent pas du piège du « i turc » de
   `java.lang.String.toLowerCase()` sous locale `tr`.

## Ce que la démo n'est pas

Ce n'est pas une démo macOS ni une démo navigateur : les scénarios sont ceux du
« host courant », et le miroir bureau/web les porte tels quels — même core, même
ui, seule la matière change. Ce n'est pas non plus un consommateur d'API
internes, ni une réimplémentation du bridge : le bridge Compose–AppKit appartient
au sample et n'est pas une API Kadre.
