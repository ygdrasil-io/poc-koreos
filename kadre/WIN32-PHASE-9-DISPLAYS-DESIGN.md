# Win32 phase 9 — inventaire displays, signaux système

**Statut :** design en cours d'implémentation conjointe KFFI/Kadre ; aucune capability ni contrat activé par ce document.
**Date :** 19 octobre 2026.
**Base Kadre :** `dbe7cc57`, fusion de #484 (phase 8).
**Base KFFI auditée :** snapshot Win32 `1.0.0-SNAPSHOT` (dernière publication `1.0.0-20261019.140415-69`, source `1f6e945`, #137/#138).
**Portée :** la phase 9 de [la roadmap](WIN32-IMPLEMENTATION-ROADMAP.md) restreinte aux sous-tranches prouvables par des API documentées — inventaire multi-moniteurs, notifications de changement et apparence (contraste). Le fullscreen exclusif et les signaux mémoire restent explicitement hors de cette livraison (§7) : la roadmap exige qu'un mode non garanti reste non supporté plutôt qu'approximé.

## 1. Intention et critères de réussite

Livrer le `DisplayPort` Win32 : un inventaire complet et honnête des moniteurs (bounds, work area, scale, modes réels), maintenu à jour par observation des notifications système, exposé via le `DisplayPort` runtime existant (DSP-001 actif côté runtime). L'apparence suit : la paire atomique `SurfaceAppearance` publiée par fenêtre, contraste lu via l'API documentée, thème conservé `Unknown` tant qu'aucune API documentée ne le fournit — la roadmap l'exige explicitement (« conserver Unknown lorsqu'une composante n'est pas lisible »).

Le résultat attendu respecte la gate de la roadmap : aucun inventaire partiel présenté comme complet, aucun `HostViewport` synthétique, aucun scale global de substitution. Un scale inconnu pour un moniteur rend l'inventaire indisponible plutôt que publié avec une valeur inventée.

## 2. Audit et choix d'architecture

Constats :

- Le runtime est complet : `DisplayPort`/`DisplayPortSnapshot` (SPI), `RuntimeDisplayManager` (réconciliation Added/Changed/Removed, résolution exclusive), `RuntimeSessionComponents.displayPort` — win32 ne fournit pas ce port aujourd'hui.
- Références : AppKit utilise un broker process-wide + dispatcher coalescé ; Wayland un port par session assemblé depuis l'état `wl_output` avec assembleur pur testable. Win32 se rapproche de Wayland : l'énumération est reproductible à la demande (`EnumDisplayMonitors`), le changement arrive par messages fenêtre.
- KFFI : `GetMonitorInfoW`/`MonitorFromWindow`/`GetSystemMetrics`/`SystemParametersInfoW` déjà générés (phase 3/5) ; manquent `EnumDisplayMonitors` (+ callback `MONITORENUMPROC`), `EnumDisplaySettingsExW` (+ `DEVMODEW`), `GetDpiForMonitor` (Shcore.dll — nouveau map).

| Approche | Avantage | Limite déterminante | Décision |
| --- | --- | --- | --- |
| Port par session, snapshot re-énuméré à la demande + observer messages | Testable (assembleur pur), pas d'état process caché, sémantique all-or-nothing native | Le re-enum doit être thread-sûr (UI owner) | Retenue (patron Wayland) |
| Broker process-wide (patron AppKit) | Coalescing des reconfigurations | Win32 n'a pas de callback système natif à coalescer : WM_DISPLAYCHANGE arrive déjà sérialisé dans la pompe | Non retenue |
| Virtual screens aggrégés comme un display | Simple | Fabriquerait un display synthétique — interdit par la gate | Non retenue |

## 3. Frontière KFFI et owners

| Owner ou valeur KFFI | Responsabilité et durée de vie |
| --- | --- |
| `Win32Displays` | `EnumDisplayMonitors` avec callback managée révocable (patron WNDPROC bridge : stubs process-lifetime, routes par contexte, quiescence) ; chaque HMONITOR résolu via `GetMonitorInfoW` (40 octets copiés) ; scale via `GetDpiForMonitor` (Shcore) ; lecture de la callback **pendant** l'énumération seulement |
| `Win32DisplayModes` | `EnumDisplaySettingsExW` par moniteur (énumérateur indexé, `ENUM_CURRENT_SETTINGS` + modes), `DEVMODEW` copié en valeurs (`dmPelsWidth/Height`, `dmDisplayFrequency`, `dmBitsPerPel`) ; aucun `DEVMODEW` ne franchit la frontière |
| Valeurs copiées | `Win32MonitorSnapshot(key=hmonito, bounds, workArea, scale, modes)` — scalaires et rectangles copiés ; HMONITOR conservé comme clé opaque Long jamais déréférencée après copie |
| Constantes typées | `WM_DISPLAYCHANGE` (0x007E), `WM_SETTINGCHANGE` (0x001A), `SPI_GETHIGHCONTRAST`, `ENUM_CURRENT_SETTINGS` |

Le callback d'énumération ne capte jamais d'état applicatif mutable : il collecte dans une liste locale et retourne TRUE. Les échecs natifs (`GetDpiForMonitor` indisponible sur vieux Windows, `EnumDisplaySettingsExW` sans mode courant) rendent l'inventaire indisponible ou l'entrée absente — jamais approximés.

## 4. Port, notifications et apparence (Kadre)

- `Win32DisplayNative` (seam injectable) + `Win32DisplayPort : DisplayPort` : snapshot assemblé par l'assembleur pur (testable), `enumerationCapability = Supported` lorsque l'énumération a réussi une fois, `requestSnapshot()` re-énumère, `installSnapshotObserver` observe le re-enum déclenché par les messages.
- Routage messages : `WM_DISPLAYCHANGE` (0x007E) dans le handler fenêtre → déclenche un re-enum asynchrone → nouveau snapshot publié (Added/Changed/Removed par réconciliation runtime). Le re-enum sur message plat reste borné (une demande en vol, coalescing booléen).
- Appearance : `WM_SETTINGCHANGE` + `SystemParametersInfoW(SPI_GETHIGHCONTRAST)` → paire `(theme=Unknown, contrast=Normal|High)` publiée par `SurfaceStimulus.AppearanceChanged` via `acceptSurfaceStimulus` — paire atomique et dédupliquée (RUN-007). Le snapshot initial remplace le `Light/Normal` codé en dur par la valeur lue.
- Provider : `displayPort = Win32DisplayPort(...)` dans `RuntimeSessionComponents`.

## 5. Budgets et teardown

Le snapshot est all-or-nothing : un échec de scale ou de modes pour un moniteur retire l'inventaire entier (`Unavailable(failure typée)`) plutôt que de publier un inventaire partiel — la sentinelle DSP-002 transposée à win32. Les collections sont bornées par `maxCollectionElementsPerValue`. Teardown : le port est fermé par `RuntimeSessionComponents.close()` ; le callback d'énumération est révoqué avec le port.

## 6. Matrice de réception et gates

| Contrat proposé | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-024 — inventaire displays win32 | Énumération réelle multi-moniteurs (au moins le primaire), modes réels lus via `EnumDisplaySettingsExW`, re-enum après `WM_DISPLAYCHANGE` injecté, scale Shcore relu | Un scale inventé, un inventaire partiel publié comme complet ou un display synthétique est détecté |
| W32-025 — apparence atomique win32 | Contraste lu via `SPI_GETHIGHCONTRAST` et publié en paire atomique, dédupliqué, thème `Unknown` assumé | Un thème inventé ou une publication non atomique/dupliquée est détecté |

Tests portables : assembleur pur (snapshot → `DisplayPortSnapshot`), bornes, refusal-on-failure, appearance dedup. Tests natifs O3 : vraie énumération multi-moniteurs si l'hôte en a plusieurs, sinon primaire seul + injection `WM_DISPLAYCHANGE`/`WM_SETTINGCHANGE` contrôlée → réaction publique observée.

Charter manuel : débrancher/rebrancher un écran, changer résolution et contraste dans les paramètres Windows, DPI mixtes.

## 7. Hors périmètre assumé de cette livraison

- **Fullscreen exclusif** : reste non supporté. Le contrat runtime WIN-008 est encore `planned` (même AppKit ne l'a pas activé) ; implémenter la win32 sans l'état machine runtime activé serait inversé. Changement de résolution via `ChangeDisplaySettingsExW` = travail ouvert nommé, à livrer avec WIN-008 activé.
- **Signaux mémoire** : `GlobalMemoryStatusEx` expose un pourcentage (`dwMemoryLoad`) sans seuils `Moderate`/`Critical` documentés par l'OS — la roadmap interdit le mapping arbitraire. Reste `Unavailable` avec cause typée documentée ; la primitive est auditée dans ce document.
- **Thème sombre** : aucune API documentée (registry Advapi32 non mappé, exports uxtheme non documentés) → thème conservé `Unknown`.

## 8. Risques

- `GetDpiForMonitor` (Shcore) absent sur Windows < 8.1 : hors matrice supportée (Win11 visé), échec = inventaire indisponible typé.
- Callback `EnumDisplayMonitors` : même discipline de révocation que la WNDPROC (stub process-lifetime, quiescence).
- Les messages `WM_DISPLAYCHANGE` arrivent sur les fenêtres top-level : le re-enum déclenché doit être coalescé pour ne pas inonder le runtime lors des transitions de mode.
