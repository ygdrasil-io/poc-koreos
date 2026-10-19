# Win32 phase 9 — charter de réception manuelle des displays

**Objet :** réceptions matérielles non remplaçables par une preuve automatisée : topologie multi-moniteurs réelle, changements de résolution système, contraste accessibilité. Aucun verdict automatique : l'opérateur consigne résultat et limites.
**Harness :** session réelle via le provider (le test natif O3 prouve l'énumération ; la réception manuelle porte sur la topologie dynamique). Compteur conseillé : lire `displays.state` dans un harnais temporaire ou observer via une application consommatrice.
**Environnement à consigner par essai :** Windows 11 build, nombre/position/résolution des écrans, DPI par écran, résultat, limites.

## M1 — Topologie multi-moniteurs réelle

Sur un poste avec ≥ 2 écrans (DPI différents si possible), lire l'inventaire.

Attendu : un display par écran, bounds non se chevauchant mais contigus, workArea ⊆ bounds, scale par écran cohérent avec les paramètres Windows (100 %/125 %/150 %).

## M2 — Changement de résolution système

Modifier la résolution d'un écran dans les paramètres Windows pendant la session.

Attendu : `WM_DISPLAYCHANGE` → re-énumération → l'inventaire publié reflète la nouvelle résolution (Added/Changed) sans redémarrage de session.

## M3 — Branchement à chaud

Débrancher/rebrancher un écran externe.

Attendu : le retrait puis le retour du display sont publiés ; aucune entrée périmée ne subsiste après retrait.

## M4 — Contraste accessibilité

Activer/désactiver le mode contraste élevé (Paramètres → Accessibilité).

Attendu : aujourd'hui la lecture via `SPI_GETHIGHCONTRAST` a échoué silencieusement sur l'hôte observé (rc=0, err=0) — la publication de contraste reste `Normal`/`Unknown` et ce comportement est consigné comme limite ouverte tant qu'une voie documentée fiable n'est pas établie.

## Hors périmètre de ce charter

- Fullscreen exclusif : non supporté (travail ouvert, cf. design §7).
- Signaux mémoire : `Unsupported` tant qu'aucun mapping conforme n'est établi.
