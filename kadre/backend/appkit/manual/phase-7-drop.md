# Cahier manuel AppKit — Phase 7 drag-and-drop

La CI prouve l’admission synchrone, le handoff runtime, les budgets, le claim unique,
le timeout et le teardown. Ce cahier vérifie uniquement les phénomènes qui nécessitent
un glisser-déposer réel AppKit. Le harness n’enregistre ni octet de contenu, ni chemin,
ni URL : il consigne seulement les descripteurs publics et les compteurs de lecture.

Lancer le harness visible :

```shell
./gradlew :kadre:backend:appkit:phase7DropHarness \
  --args='--record=kadre/backend/appkit/build/manual/phase-7-drop.tsv --build-id=<commit-ou-artefact>'
```

Déposer le contenu sur la fenêtre créée. Le handler accepte chaque offre pendant la callback
native, puis le lecteur coroutine réclame le transfer et le ferme après lecture. Commandes :
`snapshot`, `result M1..M5 pass|fail|not-applicable <note>`, `close`, `finish`.
Un run non interactif est `not-applicable`, jamais `pass`.

| ID | Manipulation | Attendu |
|---|---|---|
| M1 | Déposer un texte depuis une application distincte. | `DropEntered`, acceptation native, transfer réclamé puis item `Text` lu sans contenu inscrit dans le TSV. |
| M2 | Déposer un fichier local, puis un ensemble de plusieurs fichiers. | Descripteurs `File`, lecture complète et fermeture du transfer ; aucun chemin n’est enregistré. |
| M3 | Entrer dans la fenêtre puis en sortir sans lâcher. | La destination cesse d’annoncer une copie et aucune lecture n’est réclamée. |
| M4 | Déposer une URL depuis un navigateur ou une autre application. | Item `Uri` si AppKit le fournit, avec lecture comptabilisée sans URL publiée dans le TSV. |
| M5 | Fermer la fenêtre pendant ou juste après une lecture active. | `TERMINAL_STABILITY` confirme zéro transfer actif et l’absence d’événement tardif. |

Inclure macOS, architecture, matériel, écrans, applications source, types réellement observés
et build id dans le compte rendu. Toute restriction sandbox, absence de source adaptée ou
comportement propre à l’application source doit être explicitement marqué `not-applicable`.
