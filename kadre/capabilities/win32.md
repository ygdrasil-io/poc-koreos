# Adapter Win32 — phases 1 à 5 complètes

Hôte exercé : Windows x64, bureau interactif, JVM 25. Les primitives KFFI #127/#128 sont fusionnées et publiées ; le snapshot distant `1.0.0-20261015.232828-61` est épinglé dans le catalogue. Maven local reste un opt-in de développement. Voir la [matrice de qualification complète](../WIN32-PHASE-5-VALIDATION.md).

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
| Clavier | Available après installation | W32-009 ; HID/layout/repeat/release, aucune session texte |
| Pointeur et wheel | Available après installation | W32-010 ; souris promue, coordonnées signées/logiques, réglages lignes/caractères, page refusé |
| Focus et inputDefaultBehavior | Disponible | W32-011 ; reset commun, HostDefault/SuppressWhenPossible |
| handlerInteractions | OpenWindow, BeginWindowMove, BeginWindowResize | W32-011/016 ; même callback vivant, Primary, autorité révoquée sur focus/cancel/release ; resize windowed/resizable |
| armedInteractions | Unsupported(ArmInteraction) | Aucune activation armée |
| Curseurs | 13 mappings système exacts, Hidden, RGBA8 personnalisé ≤128×128 | W32-015 ; hotspot, WM_SETCURSOR/HTCLIENT, handles partagés préservés |
| pointerCapture | None, Confined | W32-015 ; bail ClipCursor au premier plan, retrait observable, aucune reprise implicite ; Locked réservé à la phase 8 |
| hitTesting | Enabled | Disabled Unsupported(UpdateSurface), aucun faux pass-through entre processus |
| Touch, gestures, IME/texte, drop, raw input | Non installés / refus explicite | Phases suivantes |

Les tests qualifient les appels natifs, les vrais menus et les boucles système de déplacement/redimensionnement. Les messages postés ne prouvent pas l’état matériel d’un bouton. Souris physique, claviers/layouts physiques, DPI mixtes et hotplug restent des qualifications manuelles non exécutées ; les limites précises sont consignées dans la validation.
