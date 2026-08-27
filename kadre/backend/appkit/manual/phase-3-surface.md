# Cahier manuel AppKit — Phase 3 surface

**Statut :** `APK-004` actif pour les preuves automatisées O3 ; gate de sortie
manuel standard-scale + HiDPI non satisfait au 27 août 2026.

## But et limites

Ce cahier vérifie les observations visuelles et matérielles que la CI ne peut
pas établir de manière fiable. Il s'exécute avec le harness AppKit de la
Phase 3, qui affiche les snapshots et événements de surface, permet de demander
une rafale de redraw et rend explicites les updates encore `Unsupported`. Le
harness ne rend aucun contenu applicatif Kadre.

Le harness est un exécutable externe compilé avec le classpath existant du
module, sans renderer Kadre ni build séparé :

```shell
./gradlew :kadre:backend:appkit:phase3SurfaceHarness \
  --args='--record=kadre/backend/appkit/build/manual/phase-3-surface.tsv --build-id=<commit-ou-artefact>'
```

Commandes interactives : `snapshot`, `redraw [count]`, `unsupported`,
`result M1..M7 pass|fail|not-applicable <note>` et `close`. Le fichier TSV
enregistre les métadonnées, snapshots, événements, commandes, résultats de
scénarios et outcome terminal.

Ce cahier ne répète pas les comportements déjà établis par `APK-003` :
admission de fenêtre, cancellation pré-commit, ordre primary, interception de
fermeture, lease Desktop et teardown des fenêtres. Les assertions de contrat
automatisées de `APK-004` restent la source de preuve pour les révisions,
l'ordre state/event, le coalescing et les callbacks tardives.

## Préparation et enregistrement

Exécuter le harness avec une session AppKit et une fenêtre visible. Avant les
scénarios, consigner une ligne d'exécution contenant tous les champs suivants :

| Champ | Valeur observée |
|---|---|
| macOS | version complète |
| Architecture | par exemple `arm64` ou `x86_64` |
| Modèle d'affichage | modèle ou identifiant fourni par macOS |
| Résolution | résolution effective de chaque écran utilisé |
| Scale factor | valeur observée de chaque écran utilisé |
| Apparence | clair ou sombre |
| Build id | commit/artefact du harness |

Pour chaque scénario, inscrire `pass`, `fail` ou `not applicable`, puis une
observation courte. Une capture n'est requise qu'en cas de `fail`. L'absence
d'un second écran, d'une apparence alternative ou d'un contrôle fiable de la
visibilité/occlusion est `not applicable` ; elle ne doit jamais être présentée
comme un succès.

## Scénarios approuvés

| ID | Manipulation | Observation attendue |
|---|---|---|
| M1 | Redimensionner interactivement la fenêtre sur un écran standard, puis sur un écran HiDPI. | Le harness suit continûment les dimensions et le scale factor effectifs ; aucun artefact de snapshot ou blocage visuel n'est observé. |
| M2 | Déplacer la fenêtre entre deux écrans aux scale factors différents. | Les métriques affichées convergent vers l'écran de destination. Si aucun second écran de scale différent n'est disponible, marquer `not applicable`. |
| M3 | Donner puis retirer le focus, minimiser/restaurer et masquer/démasquer ou occlure la fenêtre. | Le harness reflète les transitions réellement observables. Si l'occlusion ou la visibilité ne peut pas être contrôlée fiablement, marquer la sous-observation `not applicable`. |
| M4 | Basculer l'apparence système clair/sombre pendant que la fenêtre est visible. | Le thème observé par le harness suit la notification AppKit. Si une seule apparence est disponible, marquer `not applicable`. |
| M5 | Produire des rafales de redraw pendant un resize et pendant une occlusion. | La fenêtre reste réactive et le harness ne révèle pas de cycles de redraw en rafale non coalescés. |
| M6 | Exécuter `unsupported` et contrôler cursor/hit testing au bord si une future activation les annonce. | Pour cette activation, cursor, hit testing et input default behavior sont explicitement `Unsupported` et l'update retourne les rejets typés sans mutation d'état. Aucune application fictive n'est admise. |
| M7 | Fermer la fenêtre pendant un resize, un redraw et un changement de focus. | La fermeture est propre ; aucun nouvel événement de surface n'apparaît après le snapshot terminal affiché par le harness. |

## Compte rendu

Joindre le compte rendu d'exécution à la PR d'activation Phase 3, sous une
forme qui conserve les champs de préparation et une ligne par scénario. Tout
`fail` doit être relié à une issue ou à un test automatisé avant de déclarer
la Phase 3 terminée. Les cas `not applicable` doivent mentionner explicitement
la contrainte d'environnement.

## Exécution du 27 août 2026

Le harness a été exécuté avec `-XstartOnFirstThread` via la tâche ci-dessus.
Le harness a aussi été réexécuté après la correction du contrôle terminal. Le
record complet non versionné de ce run a été écrit dans
`kadre/backend/appkit/build/manual/task-4-fix-round-1-phase-3-surface.tsv`.

| Champ | Valeur observée |
|---|---|
| macOS | 26.6.2 |
| Architecture | aarch64 |
| Matériel | Mac14,13, Apple M2 Max |
| Écran principal | PL3467WQ, 3440 × 1440 @ 60 Hz |
| Second écran détecté | PL2209HD, 1920 × 1080 @ 60 Hz |
| Scale factor effectivement utilisé | 1.0 |
| Apparence initiale | `Unknown` |
| Build id | `task-4-fix-round-1`, basé sur la pile Task 4 issue de `1a4ed012c1d5b87bff98682561cccb723c1a11fb` |

La rafale de huit `requestRedraw()` a produit un seul `RedrawRequested` avec
`stateRevisionVisible=true`. L'update cursor/hit testing/input default behavior
a retourné `PartiallyApplied` avec trois rejets `Unsupported(UpdateSurface)`
et aucune mutation du snapshot. Après `Detached`, les collectors sont restés
actifs pendant la fenêtre contrôlée de 250 ms et ont consigné
`noLateRevision=true` et `noLateEvent=true`, puis la session s'est terminée
avec `ApplicationRequested`.

| ID | Résultat | Note |
|---|---|---|
| M1 | not applicable | Aucun opérateur humain n'a effectué de resize continu ni validé visuellement les deux classes d'écran ; seul un écran standard-scale à 1.0 a été effectivement utilisé. |
| M2 | not applicable | Deux écrans ont été détectés, mais aucun déplacement humain ni scale factor différent n'a été observé. |
| M3 | not applicable | L'exécution par terminal ne peut pas valider visuellement focus, minimisation/restauration et occlusion. |
| M4 | not applicable | Le thème initial était `Unknown` et aucune bascule clair/sombre humaine n'a été effectuée. |
| M5 | not applicable | La preuve automatisable de coalescing a été observée, mais pas la réactivité visuelle manuelle pendant resize/occlusion. |
| M6 | not applicable | Les trois mutations restent volontairement `Unsupported` dans cette activation ; le rejet typé a été observé. |
| M7 | not applicable | La fermeture propre a été observée, mais pas pendant un resize/focus/redraw humain concurrent. |

Aucun scénario n'est marqué `fail`, donc aucune capture n'est requise. Aucun
scénario manuel n'est marqué `pass` : le gate de sortie exige toujours une
exécution humaine sur standard-scale et HiDPI.
