# Cahier manuel AppKit — Phase 9 géométrie externe

Ce cahier vérifie la propriété qui ne peut pas être certifiée par un runner
CI : les coordonnées `outerBounds` restent des pixels physiques du Window
Server lorsqu'une fenêtre traverse deux écrans réels d'échelles différentes.

## Préparation

- Utiliser macOS 26 ou ultérieur et deux écrans physiques actifs, dont un écran
  Retina et un écran non-Retina.
- Placer les écrans dans Disposition de sorte qu'ils aient un offset horizontal
  ou vertical non nul ; conserver une copie de cette topologie dans le compte
  rendu.
- Démarrer une session AppKit qui journalise `Window.state`,
  `WindowEvent.GeometryChanged` et les résultats de `Window.apply`.
- Préparer une sonde **indépendante de Kadre** qui interroge le Window Server
  par `CGWindowListCopyWindowInfo`, sélectionne `kCGWindowNumber` égal au
  `NSWindow.windowNumber` de la fenêtre cible, puis conserve les quatre valeurs
  numériques de `kCGWindowBounds` (`x`, `y`, `width`, `height`). Cette sonde
  est la référence native : elle ne doit ni lire `Window.state`, ni appeler les
  services de géométrie Kadre/KFFI.

## Scénario

1. Ouvrir une fenêtre puis relever simultanément son `outerBounds` initial et
   les quatre valeurs de référence Window Server lorsqu'ils sont disponibles.
2. Déplacer la fenêtre sur le premier écran, sur le second, puis à cheval sur
   les deux écrans. Après chaque déplacement, relever le snapshot public,
   l'événement associé et la référence Window Server.
3. Demander `Window.apply(WindowUpdate(outerPosition = Set(...)))` avec une
   origine située sur chaque écran. Vérifier que le résultat contient le
   readback certifié, plutôt que la position demandée si AppKit l'a ajustée ;
   relever également la référence Window Server après chaque demande.
4. Débrancher puis rebrancher l'écran secondaire sans fermer la session.
   Relever la capability et vérifier qu'aucun `outerBounds` obsolète n'est
   publié.
5. Fermer la fenêtre, provoquer ensuite un déplacement natif si le système le
   permet, puis vérifier qu'aucun état ni événement Kadre ne réapparaît.

## Critères de réussite

- Les valeurs restent des `Int` physiques exacts : aucune multiplication ou
  division par backing scale n'est visible. Pour chaque ligne où le readback
  est disponible, `outerBounds` et l'événement doivent être égaux champ à champ
  à la référence `kCGWindowBounds` (après vérification qu'elle est intégrale).
- Une origine négative et une fenêtre à cheval conservent leur repère global ;
  aucun écran n'est choisi comme origine artificielle.
- Une absence de readback est publiée comme `outerBounds = null`, jamais par
  arrondi, extrapolation ou conservation d'une valeur périmée.
- Pour chaque mutation, l'état est publié avant son `GeometryChanged` et le
  résultat est corrélé à l'opération.
- Après fermeture, aucun callback tardif ne modifie l'état ou ne livre
  d'événement.

## Trace à conserver

Conserver la version macOS, les échelles et la disposition des écrans, le
`windowNumber` natif, les valeurs `kCGWindowBounds` de la sonde et les
snapshots/événements Kadre dans leur ordre de réception, ainsi que les
`WindowOperationId`. Pour chaque étape, noter l'égalité exacte ou la divergence
entre les deux jeux de valeurs. Une absence de `outerBounds` n'est pas un succès
implicite : elle doit rester explicitement enregistrée comme inconnue.
