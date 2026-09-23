# Kadre Desk Tour — démo de référence

Kadre Desk Tour est une démo Kadre de référence. Elle montre comment intégrer une
UI Compose dans une fenêtre détenue par Kadre, sans créer de fenêtre top-level
Compose. AppKit est à ce jour le seul hôte implémenté : ce binaire est donc
exécutable sur macOS uniquement. Cela ne redéfinit pas la démo comme une démo
« macOS » ; le modèle d’intégration vise les futures plateformes Kadre.

La démo ne dépend que de l’API publique de Kadre, et son cœur ne connaît ni
Compose ni AppKit : `core` porte l’état, le journal et les présentations, `ui`
porte Compose, `appkit` porte le bridge de rendu. Un test de frontière refuse
tout import `org.graphiks.kadre.internal.*` dans le sample.

## Lancer

```bash
./gradlew :kadre:samples:desk-tour:run
```

Le binaire est aussi installable (`:kadre:samples:desk-tour:installDist`) ; il
démarre alors avec `build/install/desk-tour/bin/desk-tour`. La démo demande
`-XstartOnFirstThread`, déjà fourni par la tâche.

**Permissions.** L’inventaire des écrans dépend de l’autorisation
d’enregistrement d’écran du système. Tant qu’elle n’est pas accordée, l’espace
Écrans l’écrit noir sur blanc et propose le bouton qui déclenche la demande ;
si le système ne la présente pas, elle s’accorde dans Réglages Système →
Confidentialité et sécurité → Enregistrement de l’écran. Les permissions de
capture sont affichées, par portée, dans l’espace Périphériques.

## Le parcours

Cinq espaces dans la barre du bas, plus le tiroir « Détails API ». La fenêtre
principale s’ouvre sur le journal.

### Bureau

« Créer une note flottante » ouvre une **seconde fenêtre Kadre** (420 × 240), avec
sa propre scène Compose — c’est la démonstration centrale : Kadre possède les
fenêtres, Compose n’en crée aucune. Chaque note présente son titre dans un champ
éditable et quatre actions : renommer, demander l’attention, changer la
décoration, fermer. Un renommage se voit dans la fenêtre de la note et dans sa
barre de titre. Une action dont la capability manque est désactivée et porte son
motif. Sous les notes, la fenêtre principale affiche sa taille logique et son
état de focus observés.

### Activité

Le journal corrélé : une entrée par intention, avec son libellé en français, son
statut (`En cours`, `Terminé`, `Refusé`, `Annulé`, `Indisponible`), la surface
publique appelée et, pour un refus, son motif. Une entrée n’est résolue qu’après
l’`…Outcome` terminal de l’opération : rien n’est annoncé qui n’ait été publié.

### Écrans

L’inventaire réellement observé : nom, écran principal, taille, échelle, mode et
fréquence de rafraîchissement, connexion. Les états non énumérés ont chacun leur
phrase — permission pas encore accordée, refusée (avec ou sans nouvelle tentative
possible), inventaire pas encore disponible — et une liste vide ne les remplace
jamais.

### Interactions

- **Saisie de texte** : « Ouvrir une session de saisie » ouvre la session de
  saisie publique de Kadre (`SurfaceInput.openTextInput`) sur la surface de la
  fenêtre principale, et affiche son état puis le dernier événement reçu.
- **Entrée observée** : modificateurs, touches enfoncées, pointeurs avec leur
  position dans l’origine que le host rapporte, et le dernier événement décrit en
  français.
- **Capacités d’entrée** : clavier, pointeur, tactile, gestes, glisser-déposer,
  saisie de texte et entrées brutes, chacune avec son état réel et son motif
  lorsqu’elle n’est pas servie.

La démo **observe** l’entrée ; elle ne la synthétise jamais.

### Périphériques

Les périphériques rapportés par le host (nom et type), le nombre de gamepads, et
les permissions de capture par portée, écran et fenêtre.

### Détails API

Le bouton « Détails API » ouvre un tiroir (fermé par défaut, refermable en cliquant
à côté) qui donne, pour chaque action de la démo, la surface publique qu’elle
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
./gradlew :kadre:samples:desk-tour:test
```

116 tests couvrent la réduction du store, la corrélation du dispatcher, les
présentations (capabilities, permissions, écrans, périphériques, entrée, saisie
de texte), le bridge de montage, et trois frontières : `core` sans renderer, `ui`
sans AppKit ni KFFI ni AWT, et le sample sans import interne Kadre.

Ces tests ne remplacent pas l’exécution : la démo a déjà livré une tranche verte
mais inerte à l’écran. Un coup d’œil après chaque changement reste la seule preuve
qu’une fonction existe vraiment, et il faut **lire** ce qu’on voit — du vocabulaire
de développeur a déjà atteint l’interface sans que personne ne le remarque.

## Limites connues

1. **Le défilement.** L’hôte AppKit publie les événements de défilement, mais la
   scène Compose du sample ne les consomme pas encore : un contenu plus haut que
   la fenêtre reste inatteignable, et `Modifier.verticalScroll` n’y change rien.
   La conception des espaces s’y adapte — sections réparties en onglets plutôt
   qu’empilées. C’est un travail de l’hôte du sample, pas du backend.
2. **Deux chemins de saisie coexistent.** La session de saisie publique s’ouvre
   depuis l’espace Interactions et reçoit de vraies frappes — c’est ce chemin qui
   terminait le processus tant que la valeur envoyée par AppKit au callback IME
   était lue comme une chaîne attribuée. Le champ d’une note, lui, reçoit la
   saisie par la route de caractères logiques du bridge. Les deux chemins sont
   exercés, et aucun n’est l’autre.
3. **Fermeture.** Le processus se termine avec sa dernière fenêtre, notes
   comprises. Compose ne fournit pas de fermeture attendable :
   `ComposeScene.close()` annule les effets asynchrones sans garantir qu’ils sont
   terminés à son retour, donc un callback très tardif peut encore être planifié
   pendant l’arrêt de l’hôte AppKit. La fermeture visible fait partie de la preuve
   opérateur, pas d’une garantie de quiescence asynchrone ; une évolution amont de
   Compose sera nécessaire pour en faire un contrat portable.
4. **Un arrêt anormal intermittent** (SIGBUS à la fermeture) a été observé pendant
   le spike, sans erreur visible ni code de sortie parlant. Il n’est pas résolu.

## Ce que la démo n’est pas

Ce n’est pas une démo macOS : les scénarios sont ceux du « host AppKit courant »,
et les autres hôtes exécuteront les mêmes scénarios contractuels dès qu’ils
existeront. Ce n’est pas non plus un consommateur d’API internes, ni une
réimplémentation du bridge : le bridge Compose–AppKit appartient au sample et
n’est pas une API Kadre.
