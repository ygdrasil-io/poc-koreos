# Cahier manuel AppKit — Phase 4 input

`APK-005` est actif pour les preuves automatisées O3. Le routing réel des périphériques
reste une observation manuelle: le scroll synthétique KFFI est créé sans fenêtre
(`windowNumber == 0`) et la CI ne prétend donc jamais qu’il atteint `scrollWheel:`.

Lancer le harness visible avec un clavier, une souris et si possible un trackpad:

```shell
./gradlew :kadre:backend:appkit:phase4InputHarness \
  --args='--record=kadre/backend/appkit/build/manual/phase-4-input.tsv --build-id=<commit-ou-artefact>'
```

Commandes: `snapshot`, `result M1..M8 pass|fail|not-applicable <note>`, `close`,
`finish`. Le TSV conserve les métadonnées machine, les snapshots input, les événements,
les résultats et une fenêtre contrôlée de 250 ms après fermeture. Un run non interactif
doit être marqué `not-applicable`, jamais `pass`.

| ID | Manipulation | Attendu |
|---|---|---|
| M1 | Donner le focus, taper et relâcher une touche. | Focus acquis; `Key` et snapshot cohérents, sans état bloqué. |
| M2 | Maintenir une touche et observer la répétition; changer les modifiers. | Repeat et modifiers observables; une release n’est jamais repeat. |
| M3 | Entrer/sortir, déplacer et dragger la souris; presser/relâcher les boutons. | Position, boutons et sortie restent cohérents. |
| M4 | Utiliser une roue discrète. | Scroll reçu par le responder réel, sans promesse de coalescing inter-phase. |
| M5 | Utiliser un trackpad, avec fractions puis momentum. | Les fractions et momentum observés sont consignés; ne pas les arrondir. |
| M6 | Perdre le focus en maintenant une touche ou un bouton. | Reset neutre sans release synthétique; aucune touche/bouton reste bloqué. |
| M7 | Fermer pendant une interaction. | Après le snapshot terminal, aucun événement input tardif. |
| M8 | Répéter sur une seconde fenêtre si disponible. | Aucun callback ne traverse de surface. |

Inclure macOS, architecture, matériel, écrans, périphériques employés et build id dans
le compte rendu. Toute absence de périphérique, de second écran ou de contrôle fiable
du momentum est `not-applicable` avec sa contrainte explicite.
