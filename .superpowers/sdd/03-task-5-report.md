# Rapport — Android tâche 5

## Périmètre livré

- `AndroidWindow.close()` délègue une seule fois à `AndroidEventLoop.closeWindow`.
- La fermeture est sérialisée sur le main Looper, terminale et idempotente : état,
  registry, référence pending, événements, redraw, wake, timer et frame sont purgés.
- Le raw surface handle est invalidé et un unique `WindowEvent.Destroyed` est émis
  directement avec le seul `WindowId` conservé localement.
- Les callbacks de `KadreActivity` demandent désormais la window encore ouverte à
  l'event loop ; `onDestroy` réutilise le chemin terminal sans émettre un second
  `Destroyed` et sans rappeler `finish()` pendant une recreation.
- Les deux constructions Android de `VideoMode` utilisent des arguments nommés et
  le refresh rate réel du display en millihertz, jamais `xdpi`.

## Invariant surface lifecycle

Lors d'un `close()` explicite, l'ID est fermé et retiré des registries avant tout
dispatch. Si le lifecycle surface est actif,
`ApplicationHandler.destroySurfaces(eventLoop)` est ensuite appelé synchroniquement
pendant que le raw handle est encore valide. `AndroidWindow.onSurfaceReleased()`
n'invalide le handle qu'après ce callback. `Destroyed` vient en dernier, puis
l'Activity mono-window est finie si la fermeture ne provient pas de son propre
`onDestroy`.

## TDD — preuves RED

Les deux tests ont été écrits et exécutés avant toute modification de production.

```text
rtk ./gradlew :kadre-android:testAndroidHostTest --tests '*AndroidVideoModeTest'
```

- Exit `1`.
- Échec attendu à la compilation : `Unresolved reference 'refreshRateMillihertz'`.

```text
rtk ./gradlew :kadre-android:connectedAndroidTest
```

- Exit `1`.
- Le nouveau device test échoue avec
  `close must immediately invalidate the raw window handle`, preuve du `close()`
  no-op initial.

## Couverture ajoutée

- Host : `60.0f -> 60_000`, `59.94f -> 59_940`; zéro, négatif, NaN et les deux
  infinis donnent `null`.
- Device : événements focus/occlusion et redraw mis en attente, puis séquence exacte
  `window.close(); window.close(); window.requestRedraw()`.
- Après fermeture : raw handle invalide, registry vide, aucun redraw/focus/occlusion
  ultérieur pendant une attente négative bornée à 750 ms, et exactement un
  `Destroyed` même après la destruction de l'Activity.
- Toutes les attentes positives restent bornées à cinq secondes via le harness
  existant.

## Régression lifecycle détectée et corrigée

Un premier GREEN du nouveau test a révélé une régression dans le test de recreation :
`onDestroy()` passait par la fermeture publique, qui rappelait `finish()` et empêchait
la nouvelle Activity d'atteindre `RESUMED`. Le chemin terminal accepte maintenant
`finishActivity = false` uniquement pour la destruction déjà engagée. Le test
`rawWindowHandleIsValidAcrossActivityRecreation` et les onze device tests sont ensuite
repassés.

## Gate final frais

Commande exacte :

```text
rtk ./gradlew :kadre-android:testAndroidHostTest :kadre-android:connectedAndroidTest :samples:hello-window-android:assembleDebug
```

- Exit `0`, `BUILD SUCCESSFUL in 26s`.
- Host : 77 tests, 0 failure, 0 error, 0 skipped, dont les 2 nouveaux tests vidéo.
- Device : 11 tests sur `Kadre_API_35(AVD) - 15`, 0 failure, 0 error, 0 skipped.
- Sample : `:samples:hello-window-android:assembleDebug` réussi.
- L'assertion device confirme qu'aucun événement du closed ID n'apparaît après
  `Destroyed`.
- `rtk git diff --check` : exit `0`.

## Commit

Message demandé : `fix(android): make close terminal and report refresh rate`.

## Statut

`PASS` — fermeture terminale/idempotente et refresh rate Android vérifiés sur host,
device réel émulé et sample Android.
