# Win32 phase 13 — cahier de fermeture : réceptions manuelles ouvertes et limites

**Objet :** consolider, à la clôture contractuelle du backend win32, les réceptions manuelles non exécutées et les limites documentées. Chaque entrée reste ouverte jusqu'à exécution consignée ; aucune n'est convertie en succès automatisé.

## Réceptions manuelles ouvertes (par phase)

| Phase | Cahier | Réceptions non exécutées |
| --- | --- | --- |
| 6 — input riche | WIN32-PHASE-6-MANUAL.md | IME réel (composition japonaise/coréenne), DPI mixtes avec IME |
| 7 — drop OLE | WIN32-PHASE-7-MANUAL.md | drag inter-applications physique, delayed rendering, fichiers virtuels |
| 8 — raw input | WIN32-PHASE-8-RAW-INPUT-MANUAL.md | souris physique (deltas réels hors injection) |
| 9 — displays | WIN32-PHASE-9-DISPLAYS-MANUAL.md | multi-moniteurs réels, changement de résolution, hotplug, contraste accessibilité |
| 10 — gamepads | WIN32-PHASE-10-GAMEPADS-MANUAL.md | manette physique (mapping, rumble, hotplug, 4e slot) |
| 11 — capture | design §6 | multi-moniteurs capture, resize live, contenu protégé (WDA), fermeture à chaud |
| 12 — embedded | design §5 | app AWT réelle (attention utilisateur, DPI mixtes, apartment COM selon hôte) |
| 13 — clôture | ce cahier | app réelle longue durée (SOH/réveil), réutilisation classloader dans un vrai consumer |

## Limites documentées (états `Unsupported` justifiés)

- **Pointer `Locked`** : travail ouvert (phase 8) — `Confined` livré.
- **Fullscreen exclusif** : `ChangeDisplaySettingsExW` = travail ouvert (WIN-008 runtime `planned`).
- **Apparence** : thème sombre sans API mappée ; contraste élevé (`SPI_GETHIGHCONTRAST`) échoue silencieusement sur l'hôte observé.
- **Signaux mémoire** : `GlobalMemoryStatusEx` sans seuils `Moderate`/`Critical` documentés — aucun mapping fabriqué.
- **Transparence/blur** : pas de contrat renderer/compositeur qualifié.
- **Capture** : TriggerRumble hors source ; DirectInput/GameInput hors source déclarée ; région/HDR refusés sans conversion.
- **Gamepads** : TriggerRumble/haptiques non mappables XInput ; DirectInput/GameInput/batterie hors périmètre.
- **Embedded** : JavaFX = intégration future ; apartment COM de l'EDT selon l'hôte ; consumer `integration:awt` requis avant bascule `supportedIntegrations`.

## Mesure des gates

`scripts/test-kadre-win32-contracts.sh` journalise désormais la durée de chaque phase (tests, evidence) dans `build/ci-diagnostics/<phase>.log` (`Kadre Win32 <phase>: passed in <n> s`). Cibles TEST-STRATEGY : p95 job ≤ 8 min, gate ≤ 10 min, timeout dur 15 min. Toute dérive répétée = ajout de capacité runner ou scission de gate, jamais de réduction du périmètre de preuve.

## Réceptions manuelles — exécution

Le template `WIN32-MANUAL-RECEPTIONS.tsv` (racine kadre) liste chaque réception ouverte avec sa phase et son charter ; l'opérateur remplit un essai par ligne (date, hôte, résultat, limites) et rapporte le contenu pour consignation ici.

## Réutilisation et classloaders

Preuve automatisée : `Win32ClassloaderReuseTest` (3 sessions séquentielles avec échange de context classloader, real provider). Limite assumée : la découverte ServiceLoader (`DesktopProviderCatalog`) est un cache process-wide paresseux — un rechargement d'application dans un classloader enfant ne re-découvre pas les providers ; le owner vivant reste le process. Consigné comme topologie supportée : un classloader par process pour les sessions kadre.
