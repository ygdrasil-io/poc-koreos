# Phase 4 — cahier de test interactif

État : **non exécuté par l'agent**. Les tests de messages postés ne constituent
pas une preuve de saisie physique. Exécuter dans une session de bureau Windows
avec les layouts et périphériques indiqués ; noter OS, JDK, DPI, layout HKL,
réglages wheel, résultat et extrait du journal par scénario.

Avec le snapshot KFFI publié et épinglé dans le catalogue, lancer depuis
PowerShell (l'alternative locale est décrite dans
[la validation](WIN32-PHASE-4-VALIDATION.md)) :

```powershell
.\gradlew.bat :kadre:backend:win32:win32InputManual --console=plain
```

Le harness écrit les touches entrées et les snapshots dans son terminal ; ne
pas y saisir de secret. Escape ou la croix ferme la fenêtre. Ajouter
`--args=--suppress` pour vérifier la politique de suppression.

| Scénario physique | Résultat à vérifier |
|---|---|
| US puis AZERTY/non-US ; changer de layout pendant une touche tenue | HID stable, caractères du layout, release liée au press initial |
| Ctrl/Alt/Shift gauche/droite ; Enter/pavé, NumLock, Pause, PrintScreen | Localisation et identité distinctes ; inconnus explicites |
| AltGr + touche imprimable | Caractère imprimable conservé, modifiers observés |
| Dead key puis lettre / espace, y compris double dead key | Pas de caractère accent seul inventé ni double traduction ; aucune session texte prétendue |
| Maintenir puis relâcher une touche | Première pression non-repeat, repeats, une release finale |
| Souris sur écrans à coordonnées négatives et DPI 125 % | Positions/deltas logiques, entrée/leave puis nouvelle entrée |
| Boutons gauche/droite/milieu/X1/X2 | Boutons distincts et état publié avant livraison de l'événement |
| Wheel vertical/horizontal avec petits incréments | Signe bas/droite, fractions et réglages lignes/caractères conservés |
| Régler le wheel à zéro puis à une page | Zéro n'émet rien ; page non mappé explicitement, aucun faux pixel/ligne |
| Alt-Tab pendant touche/bouton tenu | Un `StateReset(FocusLost)`, état nettoyé sans releases synthétiques |
| F10 et Alt-F4 avec les deux politiques | Défaut Windows conservé en HostDefault, supprimé si possible pour les messages de cette fenêtre |
| Fermer pendant input | Lane terminée, dernier snapshot gelé, pas d'input tardif |

Les actions de capture/confinement, IME et touch attendent leurs phases dédiées.
