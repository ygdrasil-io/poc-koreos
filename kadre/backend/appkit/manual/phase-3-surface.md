# Cahier manuel AppKit — Phase 3 surface

**Statut :** protocole de vérification ; `APK-004` reste `planned` jusqu'à son
activation avec les preuves automatisées O3.

## But et limites

Ce cahier vérifie les observations visuelles et matérielles que la CI ne peut
pas établir de manière fiable. Il s'exécute avec le harness AppKit de la
Phase 3, qui affiche les snapshots et événements de surface et permet de
demander un redraw, un cursor et un hit testing. Le harness ne rend aucun
contenu applicatif Kadre.

Ce cahier ne répète pas les comportements déjà établis par `APK-003` :
admission de fenêtre, cancellation pré-commit, ordre primary, interception de
fermeture, lease Desktop et teardown des fenêtres. Les assertions de contrat
automatisées de `APK-004` restent la source de preuve pour les révisions,
l'ordre state/event, le coalescing et les callbacks tardives.

## Préparation et enregistrement

Exécuter le harness avec une session AppKit et une fenêtre visible. Avant les
scénarios, consigner une ligne d'exécution contenant tous les champs suivants :

| Champ | Valeur observée |
|---|---|
| macOS | version complète |
| Architecture | par exemple `arm64` ou `x86_64` |
| Modèle d'affichage | modèle ou identifiant fourni par macOS |
| Résolution | résolution effective de chaque écran utilisé |
| Scale factor | valeur observée de chaque écran utilisé |
| Apparence | clair ou sombre |
| Build id | commit/artefact du harness |

Pour chaque scénario, inscrire `pass`, `fail` ou `not applicable`, puis une
observation courte. Une capture n'est requise qu'en cas de `fail`. L'absence
d'un second écran, d'une apparence alternative ou d'un contrôle fiable de la
visibilité/occlusion est `not applicable` ; elle ne doit jamais être présentée
comme un succès.

## Scénarios approuvés

| ID | Manipulation | Observation attendue |
|---|---|---|
| M1 | Redimensionner interactivement la fenêtre sur un écran standard, puis sur un écran HiDPI. | Le harness suit continûment les dimensions et le scale factor effectifs ; aucun artefact de snapshot ou blocage visuel n'est observé. |
| M2 | Déplacer la fenêtre entre deux écrans aux scale factors différents. | Les métriques affichées convergent vers l'écran de destination. Si aucun second écran de scale différent n'est disponible, marquer `not applicable`. |
| M3 | Donner puis retirer le focus, minimiser/restaurer et masquer/démasquer ou occlure la fenêtre. | Le harness reflète les transitions réellement observables. Si l'occlusion ou la visibilité ne peut pas être contrôlée fiablement, marquer la sous-observation `not applicable`. |
| M4 | Basculer l'apparence système clair/sombre pendant que la fenêtre est visible. | Le thème observé par le harness suit la notification AppKit. Si une seule apparence est disponible, marquer `not applicable`. |
| M5 | Produire des rafales de redraw pendant un resize et pendant une occlusion. | La fenêtre reste réactive et le harness ne révèle pas de cycles de redraw en rafale non coalescés. |
| M6 | Essayer cursor et hit testing au bord et dans les coins de la fenêtre. | Le comportement observé correspond à la demande du harness, sans zone morte ni application fictive. |
| M7 | Fermer la fenêtre pendant un resize, un redraw et un changement de focus. | La fermeture est propre ; aucun nouvel événement de surface n'apparaît après le snapshot terminal affiché par le harness. |

## Compte rendu

Joindre le compte rendu d'exécution à la PR d'activation Phase 3, sous une
forme qui conserve les champs de préparation et une ligne par scénario. Tout
`fail` doit être relié à une issue ou à un test automatisé avant de déclarer
la Phase 3 terminée. Les cas `not applicable` doivent mentionner explicitement
la contrainte d'environnement.
