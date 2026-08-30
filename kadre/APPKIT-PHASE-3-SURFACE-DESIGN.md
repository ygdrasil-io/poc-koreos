# AppKit Phase 3 — Surface et redraw

## But

Rendre `Window.surface` observable et cohérente sur AppKit sans introduire de renderer ni de système de widgets. La phase active uniquement les capacités de surface explicitement listées ici ; tout le reste demeure `Unsupported` jusqu'à sa phase dédiée.

## Limites

La phase couvre le snapshot `HostSurface`, ses événements et `requestRedraw()`, ainsi que `cursor`, `hitTesting` et `inputDefaultBehavior`. Elle ne couvre ni clavier, ni pointeur, ni scroll, ni IME, ni mutabilité avancée de fenêtre. `SurfaceCapabilities.platformAccess` reste `Unsupported` sur Desktop.

## Architecture

Le runtime reste l'autorité des snapshots, révisions et résultats publics. Un peer AppKit par fenêtre transforme les notifications de vue et de fenêtre en stimuli immuables ; le driver de session les sérialise avant le runtime. Aucun callback Objective-C n'appelle un consumer sous un lock interne, et aucune notification tardive ne modifie une surface détachée.

Chaque stimulus qui modifie le snapshot publie d'abord le nouveau `SurfaceState`, puis l'événement qui le décrit. Les changements coalesçables conservent le dernier état effectif avant livraison. La fermeture détache la surface une fois, conserve le dernier snapshot, ferme les flows et rend toute opération tardive `Closed(Surface)` ; `requestRedraw()` fermé suit le contrat déjà établi.

## Capacités Phase 3

- `requestRedraw()` est available et coalescé : une demande pendant qu'un redraw est déjà planifié ne programme pas un second cycle. Le backend livre l'invalidation à la vue AppKit, sans dessiner lui-même.
- `SurfaceUpdate.cursor`, `hitTesting` et `inputDefaultBehavior` sont available uniquement lorsque la représentation AppKit peut appliquer la valeur demandée. Une valeur non applicable retourne le résultat contractuel explicite, jamais un succès fictif.
- Les métriques logiques/physiques, `scaleFactor`, safe areas, visibilité, occlusion, focus et thème sont reportés depuis AppKit. Les valeurs non observables sur une version macOS donnée restent des valeurs snapshot documentées, pas des capacités inventées.

## Invariants

- Toute taille physique est dérivée atomiquement de la taille logique effective et du scale factor observé.
- Un événement de resize, focus, occlusion, thème ou scale ne peut précéder le snapshot qui le rend observable.
- Après détachement, aucune notification AppKit ne peut réouvrir la surface ni augmenter sa révision.
- Le teardown conserve l'ordre établi : arrêt des stimuli, détachement de la surface, fermeture des handles natifs et des delegates.
- Le runtime ne bloque jamais le main thread AppKit sous son lock ; les notifications ne déposent que des stimuli session-locaux.

## Preuves automatisées

`APK-004` devient actif seulement lorsque des preuves O3 traversent une session AppKit publique, une vraie `NSWindow`/`NSView` et vérifient : resize, change de backing scale, focus, occlusion, redraw coalescé, cursor/hit testing appliqués, ordre snapshot/événement et snapshot terminal. Les tests purs couvrent les révisions, coalescing, opérations tardives, ordonnancement et cancellation ; les tests AppKit réels couvrent les notifications natives et le cycle de vie de la vue.

## Cahier manuel AppKit

Le cahier est versionné avec la phase et exécuté avec un harness interactif AppKit. Le harness affiche les snapshots/événements et permet de demander redraw, cursor et hit testing ; il ne rend pas de contenu applicatif.

Chaque exécution enregistre : version macOS, matériel, écran(s), scale factor, mode clair/sombre et résultat `pass`, `fail` ou `non applicable`, avec note courte et capture seulement en cas d'échec.

Scénarios manuels Phase 3 :

1. redimensionnement continu sur un écran standard et un écran HiDPI ;
2. déplacement entre écrans de scale factors différents ;
3. focus, perte de focus, minimisation/restauration et occlusion ;
4. bascule clair/sombre si AppKit la notifie ;
5. rafales de redraw sans activité de rendu Kadre ;
6. cursor et hit testing au bord de la fenêtre ;
7. fermeture pendant resize, redraw ou changement de focus.

Le cahier ne répète ni admission de fenêtre, ni fermeture native, ni leases desktop déjà prouvées par `APK-003`.

## Gate de sortie

Phase 3 sort lorsque `APK-004` O3 est vert sans skip, les flows/snapshots tardifs respectent le contrat fermé, le cahier manuel AppKit a une exécution `pass` sur au moins un écran standard et un écran HiDPI, et toutes les capacités hors scope restent explicitement non disponibles.
