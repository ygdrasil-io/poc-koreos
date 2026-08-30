# Cahier manuel AppKit — Phase 5 chrome de fenêtre

APK-008 est actif pour les preuves automatisées O3. Le rendu de la title bar et
des boutons standard reste néanmoins une observation visuelle : la CI vérifie les
bindings KFFI, les masks, le readback et les événements corrélés, sans prétendre
connaître l'apparence réellement dessinée par macOS.

Lancer le harness visible :

    ./gradlew :kadre:backend:appkit:phase5WindowChromeHarness \
      --args='--record=kadre/backend/appkit/build/manual/phase-5-window-chrome.tsv --build-id=<commit-ou-artefact>'

Commandes : snapshot, system-all, system-close-only, system-none, borderless,
result M1..M4 pass|fail|not-applicable <note>, close et finish. Le TSV conserve les
métadonnées machine, les snapshots, les événements, les commandes et une fenêtre
contrôlée de 250 ms après la fermeture. Un run non interactif doit être marqué
not-applicable, jamais pass.

| ID | Manipulation | Attendu |
| --- | --- | --- |
| M1 | Lancer system-all, relever le snapshot et regarder la fenêtre. | Title bar native; close et miniaturize visibles; zoom visible quand la fenêtre est resizable. |
| M2 | Lancer system-close-only, relever le snapshot et regarder la fenêtre. | Title bar native; close visible; miniaturize et zoom masqués. |
| M3 | Lancer system-none, relever le snapshot et regarder la fenêtre. | Title bar native conservée; les trois boutons standards sont masqués. |
| M4 | Lancer borderless, relever le snapshot et regarder la fenêtre. | Absence de title bar et de boutons standards; le snapshot annonce systemButtons=None. |

Consigner un résultat par scénario, en indiquant toute contrainte (accessibilité,
configuration de macOS, écran distant, absence d'observation fiable). Inclure macOS,
architecture, matériel, écrans et build id dans le compte rendu. Kadre ne couvre ici
ni renderer ni widgets : seule la fenêtre native vide est intentionnellement observée.
