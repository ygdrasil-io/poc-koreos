# Cahier manuel AppKit — Phase 5 fullscreen

Ce cahier complète les preuves automatisées `WIN-005` et `APK-010` par les
seules observations visuelles que la CI ne peut pas établir. Il n'est appelé
par aucune tâche Gradle, aucun script CI et aucune ligne d'`evidence.tsv` ; un
résultat manuel ne peut donc ni activer ni faire passer un contrat.

Pour chaque exécution, noter les valeurs réellement observées, sans préremplir
de succès : build id, version macOS, architecture, écran choisi, disposition
des écrans, `WindowState.fullscreen`, `WindowState.level`, `operationId` des
événements et état visuel du Space macOS. Chaque scénario reçoit `pass`, `fail`
ou `not applicable`, accompagné d'une note libre.

| ID | Manipulation | Valeurs et observation à consigner |
| --- | --- | --- |
| M1 | Depuis une fenêtre `Windowed`, appeler localement `Window.apply(Set(Borderless))`. | Issue terminale réelle, état et level effectifs, `operationId` corrélé, animation et Space d'arrivée. |
| M2 | Depuis `Borderless`, appeler localement `Window.apply(Set(Windowed))`. | Issue terminale réelle, état et level restaurés, `operationId` corrélé et retour visible au Space normal. |
| M3 | Entrer puis sortir via le menu ou le raccourci fullscreen macOS. | États et événements effectivement reçus, avec `operationId = null`, animation et Space observés. |
| M4 | Entrer en fullscreen, puis fermer la fenêtre sans demander de toggle compensatoire. | État terminal, absence de fenêtre ou callback résiduel et retour visible au Space normal. |

Après M4, attendre la fin de l'animation macOS et vérifier visuellement que le
bureau normal est de nouveau utilisable. Toute différence entre les valeurs
Kadre et l'état visible est un `fail` à relier à une issue ; une exécution sans
opérateur humain reste `not applicable`, jamais `pass`.
