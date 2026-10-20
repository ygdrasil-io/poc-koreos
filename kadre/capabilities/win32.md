# Adapter Win32 — phases 1 à 10 livrées

Hôte exercé : Windows x64, bureau interactif, JVM 25. KFFI #135/#137/#138/#140/#141/#143 sont fusionnées et publiées ; le catalogue consomme `1.0.0-SNAPSHOT` (dernière publication Central Portal, capture WGC qualifiée au snapshot `1.0.0-20261020.130500-75`) et passe la gate complète avec Maven local désactivé. Maven local reste un opt-in de développement. Les phases 1–10 restent couvertes par leurs preuves antérieures ([phase 5](../WIN32-PHASE-5-VALIDATION.md), [phase 6](../WIN32-PHASE-6-VALIDATION.md), [phase 7](../WIN32-PHASE-7-DROP-DESIGN.md), [phase 8](../WIN32-PHASE-8-RAW-INPUT-DESIGN.md), [phase 9](../WIN32-PHASE-9-DISPLAYS-DESIGN.md), [phase 10](../WIN32-PHASE-10-GAMEPADS-DESIGN.md)).

La phase 7 installe le drop OLE ci-dessous avec le snapshot publié ([design](../WIN32-PHASE-7-DROP-DESIGN.md)) ; la [réception manuelle](../WIN32-PHASE-7-MANUAL.md) inter-applications reste ouverte avant l'annonce de phase complète.

| Fonction | État livré | Gate et limite |
| --- | --- | --- |
| Standalone Desktop / pompe | Disponible Windows | BCK-025, SES-002, PMP-001 ; queue UI bornée, progression dans les boucles modales |
| Embedded Desktop | Unsupported(HostAttach) | Host de phase 12 |
| Fenêtres, close, lease HWND | Disponible | W32-004/005/006 ; owner unique |
| Titre, taille cliente, min/max, position physique | Disponible | W32-012 ; readback, DPI effectif, corrélation et annulation |
| Décorations, resizable, boutons | Disponible | W32-012 ; styles relus et cadre recalculé |
| Borderless fullscreen | Disponible après ouverture windowed | W32-013 ; restauration styles/rectangle/contraintes ; entrée minimisée/maximisée refusée |
| Niveaux | Normal, Floating | W32-014 ; topmost relu, Modal exclu |
| Icônes | RGBA8 ≤256×256 | W32-014 ; slots natifs et budget partagé icône/curseur, y compris attente et remplacement |
| Attention | None, Informational, Critical | W32-014 ; HWND vivant, aucune activation simulée |
| Visibilité / minimise / maximise / restore | Actions système et observations natives | W32-016 ; aucune commande ajoutée à Window, dimensions positives préservées minimisées |
| Protection du contenu | WDA_MONITOR exact posé/relu | W32-014 ; aucune garantie universelle de confidentialité |
| Transparence, blur | Unsupported(UpdateWindow) | Absence de contrat renderer/compositeur qualifié |
| Fullscreen exclusif | Unsupported(UpdateWindow) | Phase 9 |
| Métriques, DPI, redraw | Disponible | W32-007/008 |
| Clavier | Available après installation | W32-009 ; HID/layout/repeat/release, données copiées avant filtre TSF |
| Pointeur et wheel | Available après installation | W32-010 ; souris promue filtrée lorsque les contacts sont traités, coordonnées logiques, réglages lignes/caractères, page refusé |
| Focus et inputDefaultBehavior | Disponible | W32-011 ; reset commun, HostDefault/SuppressWhenPossible |
| handlerInteractions | OpenWindow, BeginWindowMove, BeginWindowResize, AcceptDrop (après installation du target OLE) | W32-011/016/021 ; même callback vivant, Primary, autorité révoquée sur focus/cancel/release ; resize windowed/resizable |
| armedInteractions | Unsupported(ArmInteraction) | Aucune activation armée |
| Curseurs | 13 mappings système exacts, Hidden, RGBA8 personnalisé ≤128×128 | W32-015 ; hotspot, WM_SETCURSOR/HTCLIENT, handles partagés préservés |
| pointerCapture | None, Confined | W32-015 ; bail ClipCursor au premier plan, retrait observable, aucune reprise implicite ; Locked réservé à la phase 8 |
| hitTesting | Enabled | Disabled Unsupported(UpdateSurface), aucun faux pass-through entre processus |
| Texte/TSF | Installé sur STA, échec d'initialisation typé sans couper souris/clavier | W32-017/018 ; révisions et budget commun ; IME installé et DPI mixtes restent à recevoir |
| Touch, pen, eraser | Route WM_POINTER installée, identités indépendantes | W32-019 ; injection touch et paquets pen automatisés ; réception physique non exécutée |
| Gestes | Pan, Pinch, Rotation, DoubleTap après configuration native relue | W32-020 ; Interaction Context, deltas logiques, pas d'inertia/TouchpadPressure/PTP2 |
| Drop (OLE) | Installé : `IDropTarget` managé par fenêtre, admission synchrone `AcceptDrop`, transferts bornés | W32-021/022 ; formats déclarés CF_HDROP (fichiers, Replayable), CF_UNICODETEXT (texte), `UniformResourceLocatorW` (URL), TYMED_ISTREAM (binaire SingleUse) ; sources à rendu différé = failure plateforme explicite ; fichiers virtuels et drag sortant non couverts ; réception inter-applications manuelle ouverte |
| Raw input | Installé : registration souris relative process-lifetime, fan-out par accès, pré-vol anti-vol | W32-023 ; foreground-only (`RIDEV_INPUTSINK` refusé par l'OS pour la souris), retrait refusé par l'OS (`RIDEV_REMOVE` → ERROR_INVALID_FLAGS) donc registration process-lifetime ; absolu/clavier/HID autres consommés sans publication ; `Locked` reste travail ouvert |
| Displays | Installé : inventaire multi-moniteurs via `DisplayPort` (bounds, workArea, scale par moniteur, modes réels) | W32-024 ; re-énumération sur `WM_DISPLAYCHANGE` ; all-or-nothing (un moniteur illisible retire l'inventaire) ; scale via `GetDpiForMonitor` (Shcore) ; hotplug/DPI mixtes = réception manuelle ouverte |
| Gamepads (XInput) | Installé : inventaire des 4 slots via `DeviceManager`, mapping `Standard` (14 boutons, 6 axes, Y convention Standard), DualRumble | W32-025/026 ; absence honnête (slot vide = zéro pad, jamais un succès de vibration) ; polling 30 ms process-lifetime (pas de retrait OS) ; TriggerRumble/haptiques localisées non mappables, DirectInput/GameInput hors source, batterie non contractée ; manette physique = [réception manuelle](../WIN32-PHASE-10-GAMEPADS-MANUAL.md) ouverte |
| Capture (WGC) | Installé : inventaire moniteurs via `DeviceManager.capture`, flux BGRA8 borné par copie staging, terminaisons typées | W32-027/028 ; disponibilité WGC sondée à l'exécution (jamais supposée) ; HostChoice refusé (pas de picker desktop) ; `Surface` = travail ouvert (registry de session non câblée) ; région/HDR/format refusés sans conversion implicite ; frames tirées par poller (FrameArrived = 0x8000001C observé, consigné) ; multi-moniteurs/hotplug = réception manuelle ouverte |
| Apparence | `Unknown`/`Normal` publié | Thème sombre sans API documentée mappée (registry non mappé) ; contraste élevé : `SPI_GETHIGHCONTRAST` échoue silencieusement sur l'hôte observé — voie ouverte |
| Signaux mémoire | `Unsupported` avec cause typée | `GlobalMemoryStatusEx` expose un pourcentage sans seuils `Moderate`/`Critical` documentés — aucun mapping fabriqué |
| Fullscreen exclusif | Non supporté | `ChangeDisplaySettingsExW` = travail ouvert (WIN-008 runtime encore `planned`) |

Les tests qualifient les appels natifs, les vrais menus et les boucles système de déplacement/redimensionnement. Les messages postés ne prouvent pas l’état matériel d’un bouton. Souris physique, claviers/layouts physiques, DPI mixtes et hotplug restent des qualifications manuelles non exécutées ; les limites précises sont consignées dans la validation.
