# Adapter Win32 — tranches 1 à 4

Hôte exercé : Windows 11 x64, bureau interactif, JVM 25. Les nouveaux wrappers
KFFI de phase 4 sont validés par publication Maven locale ; leur publication
distante reste un prérequis de fusion Kadre. Voir
[validation conjointe](../WIN32-PHASE-4-VALIDATION.md).

| Fonction | État livré | Gate et limite |
|---|---|---|
| Standalone Desktop / pompe | Disponible sous Windows | BCK-025, SES-002, PMP-001 ; queue UI bornée, un port partagé |
| Embedded Desktop | `Unsupported(HostAttach)` | Aucun host AWT/JavaFX activé |
| Fenêtres top-level, close, lease HWND | Disponible | W32-004/005/006 ; mutations postérieures à l'ouverture en phase 5 |
| Métriques, DPI par fenêtre, redraw | Disponible | W32-007/008 ; métriques initiales et changements relus |
| `input.capabilities.keyboard` | `Available` après installation de la surface | W32-009 ; HID explicite, layout du thread, repeat/release ; pas de session texte |
| `input.capabilities.pointer` | `Available` après installation de la surface | W32-010 ; souris promue, coordonnées signées/logiques, boutons et leave |
| Wheel vertical/horizontal | Fractions en `ScrollDelta.Lines` | Réglages système lignes/caractères ; zéro désactive l'axe ; réglage page non mappé |
| Focus | Observation et reset atomique commun | W32-011 ; pas de release inventée, terminal gelé |
| `inputDefaultBehavior` | `HostDefault`, `SuppressWhenPossible` | W32-011 ; défaut des messages livrés à la fenêtre, pas des raccourcis globaux |
| `handlerInteractions` | `OpenWindow` seulement | W32-011 ; callback synchrone, un seul événement ordinaire après callback |
| `armedInteractions` | `Unsupported(ArmInteraction)` | Aucune activation armée |
| Cursor, capture, hit testing | `Unsupported(UpdateSurface)` | Phases ultérieures |
| Touch, gestures, IME/texte, drop, raw input | Non installés / refus commun explicite | Aucune activation ni faux succès ajoutés en phase 4 |

La gate automatique poste des messages dans la queue Windows. Elle ne prouve
pas les claviers physiques, AltGr/dead keys de chaque layout, ni le comportement
des menus système. Le [cahier manuel](../WIN32-PHASE-4-MANUAL.md) distingue ces
preuves et leur état non exécuté.
