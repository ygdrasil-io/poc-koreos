# Win32 phase 8 — charter de réception manuelle du raw input

**Objet :** réceptions matérielles non remplaçables par une preuve automatisée : souris physique réelle, focus multi-applications, re-ciblage inter-fenêtres. Aucun verdict automatique : l'opérateur consigne résultat et limites.
**Harness :** `./gradlew :kadre:backend:win32:win32RawInputManual -PkadreKffiLocal=true -PkadreKffiWin32Version=<alias-local>` (fenêtre Kadre + panneau de compteurs). Le rapport n'affiche que des compteurs et deltas ; aucune identité périphérique n'est publiée.
**Environnement à consigner par essai :** Windows 11 build, écran(s)/DPI, accélération pointeur active ou non, résultat, limites.

## M1 — Souris physique fenêtre active

Bouger la souris physique sur la fenêtre Kadre active.

Attendu : `Événements` croît continûment ; les deltas suivent le mouvement (signe et direction) ; la somme (dx,dy) reste cohérente avec le trajet aller-retour. L'accélération pointeur de l'hôte module les comptes : la consigne est le comportement, pas une valeur exacte.

## M2 — Focus perdu puis repris

Activer une autre application (Alt+Tab), bouger la souris, puis revenir sur la fenêtre Kadre.

Attendu : hors focus, AUCUN delta n'arrive (registration foreground-only — comportement natif) ; au retour, le flux reprend sans re-admission. Aucune suspension n'est fabriquée par le backend pour ce cas.

## M3 — Re-ciblage entre deux fenêtres Kadre

Ouvrir deux fenêtres (adapter le harness au besoin), activer l'une puis l'autre.

Attendu : la registration process suit la dernière fenêtre activée ; les deltas ne parviennent que pendant qu'une fenêtre Kadre est au premier plan. Aucun doublon.

## M4 — Vitesse de flux et charge

Bouger la souris en mouvements rapides amples pendant 30 secondes.

Attendu : aucun crash, aucun délai perceptible ; la politique `RawInputDeliveryPolicy` (capacité 256 par défaut) absorbe les pics — un dépassement applique l'action configurée (drop+rapport par défaut) sans bloquer la pompe.

## M5 — Fermeture pendant flux actif

Fermer la fenêtre Kadre pendant que la souris bouge.

Attendu : la session termine l'accès, le processus s'arrête proprement ; la registration process meurt avec le processus (jamais de retrait explicite — refusé par l'OS, cf. design §3). Aucun WM_INPUT résiduel observé par un processus tiers de test.

## Hors périmètre de ce charter

- Souris absolue (tables à stylet, RDP sans absolu désactivé) : hors source déclarée — le payload est consommé sans publication.
- Clavier/HID autres : hors source déclarée.
- `PointerCaptureMode.Locked` : travail ouvert nommé (design §8) ; non activé dans cette livraison.
- Coexistence avec un composant tiers enregistrant le raw input AVANT Kadre : refus typé prouvé par test natif ; la réception physique d'un tel scénario reste à consigner si rencontrée.
