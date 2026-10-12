# Win32 Phase 0 — Décisions contractuelles

## But

Fermer les décisions de la Phase 0 (`WIN32-IMPLEMENTATION-ROADMAP.md` §5) qui conditionnent la première frontière native : la matrice initiale (D1), le bridge `WNDPROC` managé révocable (D2), la fenêtre de contrôle interne (D3), et les règles de preuve R1-R6. Ce document ne modifie ni l'API publique, ni `DESIGN.md`, ni le statut des capabilities : il précise la lecture Win32 des contrats existants et sert de source aux contrats `W32-*` enregistrés avec cette phase.

Exclusions : le provider `Win32BackendProvider`, le dispatcher UI, le lifecycle standalone headless et toute sémantique de session vivent en Phase 1. Aucun type de ce document n'entre dans le catalogue public fermé ; la fenêtre de contrôle n'est pas une `Window` et n'apparaît dans aucun `WindowManager`.

## Préconditions KFFI

La livraison est séquencée en deux dépôts, KFFI avant Kadre (`WIN32-IMPLEMENTATION-ROADMAP.md` §2.1 : l'adapter managé est livré dans KFFI avant son consommateur Kadre) :

- Branche KFFI depuis la branche par défaut `master` du dépôt `kffi`, y portant les bindings de fenêtres de `feat/win32-surface-host-bindings` (`c5c841d` : validation de fenêtres et lookup d'enfants, `user32.yaml` + généré + tests) puis le travail managé de cette phase.
- La couche générée `org.graphiks.kffi.win32.generated` reste inchangée dans son principe (le README `kffi-win32` réserve les wrappers manuscrits à une couche séparée). Le package manuscrit `org.graphiks.kffi.win32.managed` complète la couche générée, sur le modèle de `kffi-wayland/managed` :
  - `Win32Structs` : layouts `WNDCLASSEXW` et `MSG` explicites, validés par tests ABI sur runner Windows ;
  - `Win32WndProc` : callback managée révocable construite sur la machinerie callbacks du cœur KFFI (D2) ;
  - `Win32ControlWindow` : owner minimal de fenêtre de contrôle (D3).
- Publication locale identifiable (`publishToMavenLocal`, `org.graphiks:kffi-win32-jvm:1.0.0-SNAPSHOT`) ; la révision exacte consommée par Kadre est consignée dans `WIN32-PHASE-0-KFFI-AUDIT.md` (modèle : `WAYLAND-PHASE-0-KFFI-AUDIT.md`), avec la vérification de résolution Gradle.

Verdict d'audit constaté au 12 octobre 2026 : le jar `kffi-win32-jvm` résolu (publié le 8 septembre) contient la couche générée (constantes, enums, downcalls FFM de `user32`/`kernel32`/`gdi32`/`dwmapi` initialisés paresseusement via `init()` et `check(_initialized)`) ; il ne contient ni layout de struct (`WNDCLASSEXW`, `MSG`), ni upcall `WNDPROC` managé, ni API de fenêtre. Ces trois gaps sont fermés par cette phase, dans l'ordre : structs, callback, fenêtre de contrôle.

## D1 — Matrice initiale et périmètre de preuve

La cible de référence reste Windows 11 x64 sur JVM 25 (`WIN32-IMPLEMENTATION-ROADMAP.md` §1.3). La phase 0 ne prouve que ce qu'elle exécute :

- La gate CI `windows-contracts` s'exécute sur un runner GitHub hébergé `windows-latest` (Windows Server). Cette preuve native vaut pour l'environnement exécuté ; elle ne démontre pas à elle seule le support client Windows 11, conformément à la roadmap §1.3.
- Windows 10, Windows ARM64 et Windows Server comme cibles supportées ne sont pas déduits : hors preuve, hors capabilities.
- Le cahier matériel Windows 11 client (DPI mixte, IME réel, interactions physiques) est reporté aux phases qui portent ces garanties ; la phase 0 enregistre cette limite sans la convertir en exclusion permanente.

## D2 — Bridge `WNDPROC` managé révocable

- La `WNDPROC` est un owner KFFI managé, révocable avec quiescence : après révocation, plus aucun dispatch applicatif n'est admis ; les supports natifs (stub d'upcall, mémoire associée) ne sont libérés qu'une fois les appels en cours terminés, sur le pattern des listeners Wayland managés (`AtomicBoolean` + révocation quiescente).
- Aucune exception Kotlin ne traverse la frontière native : les exceptions levées dans un handler sont capturées, rapportées au reporter KFFI et observables côté preuve ; la `WndProc` retourne toujours un `LRESULT` natif.
- Copie bornée avant dispatch : `MSG` (`message`, `wParam`, `lParam`, coordonnées) est copié en valeurs sûres avant tout traitement applicatif ; aucune donnée native n'est retenue au-delà de l'appel.
- Réutilisation d'adresse : une adresse `HWND` réutilisée par Windows ne réactive jamais un dispatch révoqué ni un owner détruit ; l'association owner/`HWND` est retirée avant toute libération du support natif.
- Les messages non traités par Kadre suivent le comportement natif par défaut (`DefWindowProcW`) ; la fenêtre de contrôle n'intercepte que son message privé de test.

## D3 — Fenêtre de contrôle interne

- Propriété : le module `:kadre:backend:win32` possède la fenêtre de contrôle ; elle n'est ni une `Window` publique, ni membre d'un `WindowManagerState`, ni annoncée dans une capability.
- Cycle : classe enregistrée sous nom unique → `CreateWindowExW` (fenêtre message-only, parent `HWND_MESSAGE`) → message privé de test `WM_APP + 1` injecté via `PostMessageW` par le driver O3 → dispatch observé → `DestroyWindow`. La fermeture est idempotente : un second appel produit le même outcome terminal, sans erreur ni double libération.
- Initialisation paresseuse : aucun symbole ni DLL résolu hors Windows (`init()` générée jamais appelée) ; hors Windows, toute utilisation produit un échec actionnable immédiat, jamais une erreur de réflexion ni un succès fictif.

## Règles de preuve R1-R6

- **R1** — Les tests natifs sont filtrés sur l'OS : hors Windows, la tâche `win32NativeTests` ne s'exécute pas et n'est jamais comptée comme réussie ; seuls les tests JVM portables tournent partout.
- **R2** — Le job CI échoue si son bureau ou ses prérequis natifs sont absents : assertion `if: always()` sur l'existence des fichiers `contract-evidence/W32-*.json` (pattern `kadre-wayland-contracts.yml`), sans skip, retry ni masquage.
- **R3** — Discriminant d'adapter `win32-jvm`, cible `jvm` : une preuve AppKit, Wayland ou runtime seule ne satisfait jamais un contrat `W32-*`.
- **R4** — Suites séparées : `jvmTest` portables (invariants O2 sur ports contrôlés et fakes) + `win32NativeTests` Windows-only (`--enable-native-access=ALL-UNNAMED`, `forkEvery = 1`), agrégées par `win32ContractTests`.
- **R5** — Les sentinelles sont enregistrées avec les scénarios dans le registre ; la suppression volontaire du dispatch fait échouer le scénario d'observation, et toute régression de révocation fait échouer ses sentinelles.
- **R6** — L'injection O3 passe par le driver externe `:kadre:contracts:driver:win32` (`PostMessageW` via KFFI, frontière Windows réelle) ; un appel direct au dispatch interne n'est pas une preuve OS.

## Contrats enregistrés

Enregistrés `active` dans le même commit que leurs scénarios, sentinelles, mappings `kadre/backend/win32/contracts/evidence.tsv` et génération de preuves :

- **W32-001** — Frontière native : un message injecté à la frontière Windows produit une observation vérifiée. Oracle O3, natif. Scénarios : `win32-control-window-created`, `win32-injected-message-observed`, `win32-injected-payload-copied`. Sentinelle : `win32-no-double-delivery`.
- **W32-002** — Révocation : le callback révoqué ne livre plus rien et n'est libéré qu'après quiescence. Oracle O3, natif. Scénarios : `win32-revoke-quiescent`, `win32-post-revoke-no-dispatch`. Sentinelles : `win32-no-late-callback`, `win32-no-hwnd-resurrection`.
- **W32-003** — Fermeture idempotente et portabilité : fermeture idempotente de la fenêtre de contrôle, échec actionnable hors Windows, zéro chargement natif hors Windows. Oracle O2, portable. Scénarios : `win32-close-idempotent`, `win32-init-off-windows-fails-actionably`. Sentinelle : `win32-no-native-load-off-windows`.

## Gate de sortie de la phase

Un message injecté à la frontière Windows produit une observation vérifiée (W32-001), le callback est révoqué avant libération (W32-002), la fermeture est idempotente (W32-003), et la suppression volontaire du dispatch fait échouer le scénario. Le job `windows-contracts` échoue si son bureau ou ses prérequis natifs sont absents. Le module compile sans charger de DLL Windows sur les autres OS.

## Hors périmètre

Provider SPI et headless standalone (Phase 1), fenêtres publiques, DPI, input, IME, drop, layouts de structs au-delà de `WNDCLASSEXW`/`MSG`, extension du générateur kextract, COM/OLE, raw input, capture. Les reliquets non suivis `kadre-win32/` et `ffi/win32/` à la racine du dépôt Kadre ne sont pas touchés.
