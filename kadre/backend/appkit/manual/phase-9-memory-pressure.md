# Cahier manuel AppKit — Phase 9 pression mémoire

Ce cahier complète les preuves automatisées de `RUN-008` et `APK-018` par le
stress matériel qu'une CI ne doit pas provoquer : pression mémoire réelle,
coalescence éventuelle de `Dispatch`, et isolation après fermeture de session.
Il s'exécute sur un Mac de test, jamais dans un runner CI ni sur une machine
qui porte des données ou un travail non récupérable.

Le harness installe un unique source process-wide `DispatchMemoryPressureSource`
et deux sessions runtime distinctes. Il ne crée **jamais** de pression mémoire
lui-même. L'opérateur utilise dans un second terminal un outil de charge
contrôlée approuvé pour la machine de test, ou un workload reproductible et
réversible. Éviter une allocation Kotlin/Java non bornée : le niveau et la
coalescence des notifications restent la décision de macOS, pas celle du
harness.

Lancer le harness :

```shell
./gradlew :kadre:backend:appkit:phase9MemoryPressureHarness \
  --args='--record=kadre/backend/appkit/build/manual/phase-9-memory-pressure.tsv --build-id=<commit-ou-artefact>'
```

Commandes : `status`, `close-session <1|2>`,
`result M1..M5 pass|fail|not-applicable <note>`, `close`, `finish`.
`close` ferme les sessions encore vivantes, termine le host process-wide puis
observe 250 ms sans nouveau signal. `result M5` est refusé tant que cette
observation terminale n'a pas eu lieu. Le record contient macOS, architecture,
modèle matériel, build id, capability initiale, état des deux sessions et les
signaux publics `MEMORY_PRESSURE` réellement reçus.

Un run sans primitive disponible, sans autorisation de stress, ou sans niveau
reproductible est `not-applicable`, jamais `pass`. Ne jamais cocher un succès
à partir de l'ouverture du source, d'un test unitaire ou de l'absence de crash.

| ID | Manipulation opérateur | Attendu à consigner |
| --- | --- | --- |
| M1 | Démarrer, lancer `status` avant toute charge. | `LifecycleCapabilities.memoryPressure` est `Available` seulement si le source natif s'est ouvert. Une capability `Unavailable` ou `Unsupported` donne `not-applicable` ; aucun signal ne doit être inventé. |
| M2 | Appliquer une charge contrôlée modérée jusqu'à une notification système. | Les deux index de session reçoivent un `MEMORY_PRESSURE` public `Moderate` issu du même source process-wide. Conserver les deux lignes et leur stamp, sans exiger un nombre précis de callbacks. |
| M3 | Augmenter la charge de façon contrôlée jusqu'à un niveau critique, puis relâcher la charge. | Les deux sessions reçoivent `Critical` si macOS le signale. Une coalescence ou l'absence de niveau atteignable est consignée telle quelle, sans le remplacer par un événement synthétique. |
| M4 | Après au moins un signal réel observé par les deux sessions, exécuter `close-session 1`, recréer une condition de pression et lancer `status`. | De nouveaux signaux éventuels ne concernent que l'index 2 ; l'index 1 reste fermé et son compteur ne change pas. Le source reste utilisable pour la session 2. |
| M5 | Exécuter `close`, attendre le `TERMINAL_STABILITY`, puis `result M5 …` et `finish`. | Le record indique `noLateMemoryPressure=true` durant la fenêtre d'observation et `SOURCE_TERMINATED\trequested`. Toute ligne mémoire tardive, exception ou session résiduelle est un `fail`. |

Joindre au compte rendu le profil de charge exact, la mémoire physique, la
pression déjà présente sur la machine, les restrictions de sandbox/MDM et la
trace TSV brute. Ce cahier ne promet ni seuil, ni fréquence, ni ordre entre les
deux sessions : seuls les niveaux effectivement envoyés par macOS font foi.
