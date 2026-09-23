# Kadre Desk Tour — démo de référence

Kadre Desk Tour est une démo Kadre de référence. Elle montre comment intégrer une
UI Compose dans une fenêtre détenue par Kadre, sans créer de fenêtre top-level
Compose. AppKit est à ce jour le seul hôte implémenté : ce binaire est donc
exécutable sur macOS uniquement. Cela ne redéfinit pas la démo comme une démo
« macOS » ; le modèle d’intégration vise les futures plateformes Kadre.

La démo ne dépend que de l’API publique de Kadre. Elle est conçue pour une
exécution interactive et une vérification visible des routes de surface et
d’entrée.

## Exécuter la preuve

```bash
./gradlew :kadre:samples:desk-tour:run
```

Pendant la session visible, consigner une issue `pass`, `fail` ou `not applicable`
pour chacun des contrôles suivants :

1. Une seule fenêtre `Kadre Desk Tour` apparaît ; aucune fenêtre top-level créée par Compose n’apparaît.
2. Redimensionner la fenêtre deux fois ; les tailles logique et physique affichées par Compose suivent le `SurfaceState` courant de Kadre.
3. Donner puis retirer le focus ; l’UI reflète le `SurfaceFocus` observé par Kadre.
4. Cliquer dans le champ texte, saisir du texte ordinaire, puis utiliser backspace ; le champ visible reçoit seulement la route d’entrée réelle.
5. Fermer la fenêtre ; observer que le processus se termine sans erreur visible.

Si une session AppKit visible ne peut pas être exécutée, noter `not applicable`
pour la ou les observations impossibles, avec l’exception ou le motif. Ne pas
remplacer cette preuve par une fenêtre top-level détenue par Compose : cela ne
prouverait plus la propriété de fenêtre par Kadre.

## Limitation connue : fermeture Compose

L’API publique actuelle de Compose fournit `ComposeScene.close()`, qui annule les
effets asynchrones sans garantir qu’ils sont tous terminés à son retour. Kadre
libère correctement la scène lors de la fermeture, mais cette version ne peut
pas démontrer une barrière native stricte : un callback Compose très tardif peut
encore être planifié pendant l’arrêt de l’hôte AppKit.

La fermeture visible fait donc partie de la preuve opérateur, pas d’une garantie
de quiescence asynchrone (absence démontrée de travail tardif). Une évolution
amont de Compose, telle qu’une fermeture attendable, sera nécessaire pour
transformer cette propriété en contrat portable et vérifiable automatiquement.
