# AppKit Phase 5 — Window Chrome Implementation Plan

**Goal:** rendre `decorations` et `systemButtons` mutables sur AppKit avec
canonisation explicite, readback KFFI, coroutines corrélées et preuves CI, sans
activer fullscreen, level ou effets visuels.

**Architecture:** le runtime construit un candidat titre + géométrie + chrome
canonique. Le port AppKit possède les style bits et les boutons natifs,
préserve les bits qu'il ne possède pas et retourne un snapshot effectif. Les
capabilities et contrats publics n'arrivent qu'en dernière carte.

**Prerequisite:** KFFI publié contient `NSWindow.styleMask`,
`NSWindow.setStyleMask`, `NSWindow.standardWindowButton` et `NSButton` hérité
de `NSView`. Ne pas écrire de FFI manuel ; une lacune remonte d'abord à
Kextract puis à KFFI.

## PR 1 — Contrat et réservation

- [x] Ajouter `APPKIT-PHASE-5-WINDOW-CHROME-DESIGN.md`.
- [x] Réserver `WIN-003` et `APK-008` dans le registre.
- [x] Mettre à jour la roadmap : titre livré, chrome prochaine tranche.
- [x] Valider `validateContractRegistry` sans activer les contrats réservés.

## PR 2 — Runtime privé

- [ ] Écrire les tests O2 rouges : canonisation, validation borderless,
  mutation combinée, cancellation, révision et event ordering.
- [ ] Étendre le set de routage et le candidat `WindowUpdate` à
  `decorations`/`systemButtons`, tout en gardant les capabilities publiques
  inchangées.
- [ ] Étendre le snapshot effectif et `PropertiesChanged` aux deux champs.
- [ ] Vérifier `:kadre:runtime:jvmTest` et les mappings O2 réservés.

## PR 3 — Port AppKit privé

- [ ] Écrire les tests O3 rouges pour le style mask, les boutons masqués,
  l'annulation avant setter et l'isolation de deux peers.
- [ ] Étendre la cible/snapshot privée et la barrière de commit du peer.
- [ ] Appliquer et relire style mask et boutons avec les bindings KFFI
  générés ; préserver tous les bits non possédés.
- [ ] Vérifier les tests déterministes et macOS réels de l'adapter AppKit.

## PR 4 — Activation publique et evidence

- [ ] Écrire le test rouge de capability publique, de mutation combinée et de
  state-before-event sur une vraie fenêtre macOS.
- [ ] Ajouter les deux capabilities publiques AppKit, sans modifier les autres
  champs hors scope.
- [ ] Activer `WIN-003` et `APK-008`, ajouter leurs mappings et les gates.
- [ ] Ajouter un harness manuel non bloquant qui enregistre les quatre états
  visuels définis par le contrat.
- [ ] Rejouer la suite AppKit, le validateur et `test-kadre-appkit-contracts.sh`.
