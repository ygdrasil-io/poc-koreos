# Cahier manuel AppKit — Phase 9 fullscreen `Exclusive`

Ce cahier complète les preuves automatisées de `WIN-008` et `APK-016` par les
constats matériels qu'aucun runner CI géré ne peut établir sans prendre le
contrôle d'un écran. Il ne doit jamais être exécuté en CI, sur l'écran primaire,
sur un écran qui affiche une session distante, ni sur un écran utilisé pour un
travail non récupérable.

Il vise un Mac sous macOS 26 ou ultérieur, avec un écran physique de test
distinct de l'écran primaire. Avant de commencer, relever le build, la version
de macOS, l'architecture, la topologie complète des écrans, le nom de l'écran
de test et son mode courant. Conserver la trace des `DisplayManager.state`,
`DisplayEvent`, `WindowState`, `WindowEvent`, outcomes de `Window.apply` et
`operationId`; ne jamais préremplir un succès.

La fenêtre de test est d'abord ouverte `Windowed`. L'opérateur choisit ensuite,
dans le même inventaire courant de `DisplayManager`, un `Display` physique
secondaire et l'un de ses `DisplayMode`, puis appelle uniquement :

```kotlin
window.apply(
    WindowUpdate(
        fullscreen = PropertyChange.Set(FullscreenMode.Exclusive(display.id, mode)),
    ),
)
```

Il ne faut ni construire un `DisplayId`/`DisplayModeId`, ni réutiliser un mode
d'un inventaire précédent ou d'un autre display. Le choix d'un écran primaire,
virtuel, déconnecté ou absent de l'inventaire courant est `not-applicable` pour
ce cahier, pas une raison de contourner la barrière de sécurité.

Chaque scénario reçoit `pass`, `fail` ou `not-applicable`, avec une note libre
et les valeurs réellement observées. Un écran inaccessible à l'opérateur, y
compris un runner CI, est toujours `not-applicable`, jamais `pass`.

| ID | Manipulation opérateur | Valeurs et observation à consigner |
| --- | --- | --- |
| M1 | Depuis `Windowed`, choisir l'écran de test et un mode listé, puis demander `Exclusive`. | Outcome terminal, `operationId`, mode effectif, `WindowState.fullscreen`, frame, style et niveau effectifs. Vérifier visuellement que seule la fenêtre de test couvre l'écran ciblé. |
| M2 | Depuis l'état exclusif actif, demander `Windowed`. | Outcome terminal corrélé, retour à `Windowed`, restauration visible du mode, du frame et du style antérieurs, puis absence de capture persistante. |
| M3 | Entrer à nouveau en exclusif, puis déconnecter/reconnecter seulement l'écran de test ou provoquer une reconfiguration équivalente contrôlée. | Ordre `DisplayManager.state`/événement, terminal de l'opération ou de la lease, retrait éventuel de la capability `Exclusive`, et absence d'accès résiduel à l'écran perdu. |
| M4 | Entrer en exclusif puis fermer la fenêtre de test sans demander de sortie compensatoire. | État terminal, restauration du mode et du bureau de test après la fermeture, absence de fenêtre, callback ou capture résiduelle après l'attente de stabilisation. |

Après M2, M3 et M4, attendre la fin de toute reconfiguration système avant de
conclure. Toute divergence entre l'état Kadre, la trace des événements et l'état
physique est un `fail` à relier à une issue. Une trace `pass` complète sur un
écran de test dédié est le prérequis matériel avant d'activer `WIN-008` et
`APK-016` dans le registre ; le fichier lui-même ne contourne pas ce prérequis.
