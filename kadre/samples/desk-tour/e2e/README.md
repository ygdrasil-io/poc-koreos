# Desk-tour web — filet e2e grossier

Le filet de régression commis du demo desk-tour sur le web : il sert une distribution
**déjà construite**, la pilote en chromium headless avec de vrais clics aux coordonnées
canvas (méthode de la sonde, `../PROBE-NOTES.md`), et n'affirme que du grossier — zéro
`pageerror`, présence/absence d'éléments DOM host, géométrie. Aucun pixel, aucun texte
lu dans le canvas (opaque). Le détail de ce que chaque test tue est en tête du spec
(`desk-tour-web.spec.mjs`).

## Construire puis courir

```shell
# 1. La distribution (le filet ne construit jamais rien lui-même) :
./gradlew :kadre:samples:desk-tour:wasmJsBrowserDistribution

# 2. Le runner (depuis ce dossier e2e/) :
npm install
npm test          # = npx playwright test
```

Sans distribution, la config refuse de démarrer avec le chemin de construction — le
test échoue honnêtement au lieu de compiler à la place du Gradle.

Courir contre une autre distribution (par exemple un artefact de CI) :

```shell
KADRE_DEMO_DIST=/chemin/vers/productionExecutable npm test
```

Sorties (gitignorées, jamais commises — le spec EST le filet) :

- `playwright-output/screenshots/*.png` — une capture par étape du parcours ;
- `playwright-output/` — traces + captures des échecs (`retain-on-failure`).

## Câblage, versions, portée

- `@playwright/test` est épinglé sur **la même version que le driver de contrats**
  (`kadre/contracts/driver/web/package.json`, 1.60.0). Les deux projets partagent donc
  la même révision de navigateur du **cache Playwright par défaut de la machine**
  (`~/Library/Caches/ms-playwright` sur macOS) : aucun `PLAYWRIGHT_BROWSERS_PATH` à
  poser, aucun second téléchargement. Sur une machine qui n'a jamais couru le driver :
  `npx playwright install chromium` une fois.
- `node_modules/` est gitignoré (racine du dépôt) ; `package-lock.json` est commis,
  comme chez le driver.
- **Portée : chromium seul.** C'est le filet d'un sample, pas le driver de contrats —
  firefox/webkit hors périmètre, par décision (le driver, lui, couvre les trois moteurs
  pour les contrats kadre).
- Variables : `KADRE_DEMO_DIST` (distribution à servir), `KADRE_DEMO_PORT` (défaut
  4173), `KADRE_E2E_OUTPUT_DIR` (défaut `playwright-output/`).
- Dépannage du port : en local, un serveur déjà à l'écoute sur le port est
  **réutilisé** (`reuseExistingServer`, coupé en CI) — un serveur manuel oublié sur
  4173 servirait SON contenu à la place de la distribution fraîche. Tuez-le
  (`lsof -ti :4173 | xargs kill`) ou pointez `KADRE_DEMO_PORT` ailleurs.
