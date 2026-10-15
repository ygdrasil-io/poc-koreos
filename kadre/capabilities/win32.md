# Adapter Win32 — phases 1 à 4 et tranche 5A

Hôte exercé : Windows 11 x64, bureau interactif, JVM 25. Les nouveaux wrappers
KFFI de phase 4 sont publiés après la fusion de KFFI #126 et consommés depuis
le snapshot distant épinglé `1.0.0-20261015.194029-58`. Voir
[validation conjointe phase 4](../WIN32-PHASE-4-VALIDATION.md).
La tranche 5A utilise le KFFI local `1.0.0-win32-phase5-local-SNAPSHOT`
en attendant sa fusion/publication ; voir sa [validation](../WIN32-PHASE-5-VALIDATION.md).

| Fonction | État livré | Gate et limite |
|---|---|---|
| Standalone Desktop / pompe | Disponible sous Windows | BCK-025, SES-002, PMP-001 ; queue UI bornée, un port partagé |
| Embedded Desktop | `Unsupported(HostAttach)` | Aucun host AWT/JavaFX activé |
| Fenêtres top-level, close, lease HWND | Disponible | W32-004/005/006 ; owner unique |
| Titre, taille cliente, contraintes, position physique | Disponible dans la tranche 5A | W32-012 ; readback, corrélation, cancellation avant claim UI |
| Décorations, resizable, boutons système | Disponible dans la tranche 5A | W32-012 ; styles relus, taille cliente préservée lors du changement de cadre |
| Fullscreen borderless | Disponible après ouverture windowed | W32-013 ; rectangle du moniteur, sauvegarde/restauration styles et rectangle ; entrée depuis une fenêtre minimisée/maximisée refusée |
| Niveaux, icône, attention | `Unsupported` | Sous-tranches de phase 5 restantes ; aucun succès simulé |
| Visibilité / minimise / maximise | Observations et actions système natives | Aucune commande correspondante dans l'API Window actuelle ; qualification physique du menu non exécutée |
| Transparence, blur, protection, fullscreen exclusif | Refus explicite | Pas de garantie de confidentialité ni de changement de mode ajouté |
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
