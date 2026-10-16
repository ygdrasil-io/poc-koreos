# Win32 phase 5 — validation complète conjointe

La PR Kadre #474 couvre tous les points de la phase 5. KFFI #127 fournit les propriétés/rectangles ; KFFI #128 fournit les images, niveaux, attention, affinité, confinement et actions natives. Les deux PR KFFI sont fusionnées. Le snapshot Win32 `1.0.0-20261015.232828-61`, issu du commit `3b94c959`, est publié dans Central Portal et épinglé dans le catalogue. La [publication](https://github.com/Graphiks-org/kffi/actions/runs/38823603711) a réussi ; Maven local reste un opt-in de développement.

## Matrice de qualification

| Point de la roadmap | Comportement ou décision finale | Preuve |
| --- | --- | --- |
| Titre, taille cliente, min/max, position physique | Setters natifs, DPI effectif, prévalidation, readback et événements corrélés | W32-012 |
| Décorations, resizable, boutons | Styles réels, recalcul du cadre et track limits clients | W32-012 |
| Visibilité, min/max/restore | Commandes système, visibilité et métriques publiques ; dernières dimensions positives conservées minimisées | W32-016 |
| Fullscreen borderless | Sauvegarde/restauration styles et rectangle, contraintes suspendues, rollback ; exclusif réservé à la phase 9 | W32-013 |
| Niveaux | Normal/Floating = non-topmost/topmost relu ; Modal exclu des contraintes, aucun alias | W32-014 |
| Icônes | RGBA8 ≤256×256 ; HICON propre aux deux slots, set/clear/remplacement, libération après retrait/destruction | W32-014 |
| Attention | FlashWindowEx du HWND vivant, None arrête ; aucun changement de focus ni frappe simulée | W32-014 |
| Curseurs | 13 mappings système exacts, Hidden, RGBA8 personnalisé ≤128×128 avec hotspot ; WM_SETCURSOR/HTCLIENT, handles partagés jamais détruits | W32-015 |
| Hit testing | Enabled = DefWindowProc ; Disabled Unsupported car HTTRANSPARENT ne prouve pas le pass-through entre processus | Sentinelle W32-014 |
| Confinement | Bail exclusif ClipCursor au premier plan ; révocation sur focus perdu, hide, minimize, cancel, teardown ou remplacement étranger observable ; aucune reprise implicite | W32-015 |
| Lock/raw input | Locked/raw Unsupported jusqu’à la phase 8 ; SetCapture seul ne représente jamais un pointer lock | Sentinelle W32-014 |
| Menu et move/resize | Worker pendant un vrai TrackPopupMenu ; payload WM_NCLBUTTONDOWN ; vraies boucles SC_MOVE/SC_SIZE ; admission synchrone Primary, resize resizable/windowed | W32-016 |
| Transparence/blur | Unsupported qualifié faute de contrat renderer/compositeur exact ; aucun alpha arbitraire présenté comme succès | Sentinelle W32-014 |
| Protection du contenu | Set/GetWindowDisplayAffinity = WDA_MONITOR exact, sans promesse universelle de confidentialité | W32-014 |
| Host embedded | Droit d’attention lié au host de phase 12 ; aucune admission embedded inventée dans Standalone | Roadmap phase 12 |

Ces décisions Unsupported terminent la qualification des variantes concernées ; aucun reliquat « 5B » n’est créé. Formats encodés et curseurs sans équivalent exact sont refusés avant allocation native.

## Reproduction

La qualification du snapshot distant, avec `-PkadreKffiLocal=false`, passe les 649 tests Kadre (103 Win32 portables, 32 natifs, 426 runtime, 18 Desktop, 70 validator), zéro failure/error/skip, ainsi que le watchdog et les 19 JSON de contrats. Les corrections ont leurs régressions RED→GREEN et sont décrites dans les [décisions de revue](WIN32-PHASE-5-REVIEW.md).

Le contrôle natif hors écran a confirmé que User32 borne le rectangle ClipCursor au bureau virtuel. KFFI utilise donc l’intersection visible du client, refuse un client entièrement hors écran et enregistre l’ownership avant readback pour permettre le rollback. La régression native KFFI vérifie cette normalisation et la restauration du clip précédent.

Windows x64/JDK 25 : la qualification distante consomme le snapshot du catalogue avec Maven local explicitement désactivé.

Depuis Kadre, exécuter `:kadre:backend:win32:win32ContractTests :kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest :kadre:contracts:validator:jvmTest :kadre:contracts:validator:validateContractRegistry :kadre:contracts:validator:generateWin32ContractEvidence` avec `-PkadreKffiLocal=false --refresh-dependencies`.

Le watchdog `scripts/test-kadre-win32-contracts.sh` reçoit `-PkadreKffiLocal=false`. La gate produit 19 JSON : W32-001..016 et BCK-025/SES-002/PMP-001. Chaque scénario/sentinelle doit correspondre à un test sans échec ni skip et au SHA contrôlé.

Pour un développement conjoint local uniquement : dans KFFI, activer `KFFI_WIN32_INTEGRATION=1`, exécuter les suites JVM/natives et publier les jars testés avec `scripts/publish-win32-local.ps1 -Version 1.0.0-win32-phase5-complete-local-SNAPSHOT`. Kadre peut alors activer explicitement `-PkadreKffiLocal=true -PkadreKffiWin32Version=1.0.0-win32-phase5-complete-local-SNAPSHOT`. Ces coordonnées locales ne sont pas utilisées par la qualification distante.

## Régressions et limites

Le menu natif a révélé que la fenêtre de contrôle KFFI filtrait WM_APP+2. La régression native échoue avant correction ; COMMAND_MESSAGE rejoint maintenant son callback dans la boucle imbriquée User32. La minimisation publie Hidden, la restauration sa visibilité native. Le bouton consommé par un drag n’est pas réinséré en input ordinaire après la boucle, sans fabriquer perte de focus ni reset.

Les images partagent un budget entre icônes/curseurs, demandes en attente et remplacements. Les tests couvrent identité partagée, limites par ressource/session, annulation avant claim, fermeture/detach, readback initial inattendu et refus avant allocation. Une perte de confinement révoque aussi un ack Confined en cours.

Non exécutés : souris physique pendant le drag, écrans à DPI mixtes, retrait/reconnexion de moniteur. Un message posté ne pose pas l’état physique du bouton : le test vérifie séparément le payload et les vraies boucles SC_MOVE/SC_SIZE, sans injection matérielle. ClipCursor ne peut être acquis que si Windows accorde le premier plan ; le test consigne succès ou refus et vérifie toujours la préservation d’un clip étranger. Des écritures étrangères identiques au rectangle détenu ne sont pas détectables et nécessitent la coopération des autres usagers.

KFFI génère 96 fonctions, `--check` identique. Le SDK installé conserve son diagnostic préexistant `excpt.h` manquant ; aucune génération sans diagnostic n’est revendiquée. La publication Maven locale ne prouve pas une publication Central.
