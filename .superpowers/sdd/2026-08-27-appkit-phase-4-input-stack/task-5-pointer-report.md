# Task 5 — suivi pointeur AppKit natif

## Fichiers modifiés

- `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
  - `KffiInputObserverOwner` possède désormais l'admission immuable et le
    `ObjCPointerTracking` opaque renvoyé par
    `NSView.installPointerTracking(window)`.
  - L'installation active l'admission pointeur seulement après le premier
    répondant, et l'échec ferme les ressources en ordre inverse tout en
    conservant les exceptions supprimées.
  - La révocation ferme d'abord l'admission des callbacks ; `close()` libère
    ensuite le tracking tant que la vue et la fenêtre sont vivantes.
- `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`
  - preuve native que la vue préparée annonce le pointeur installé et route un
    `mouseDown` vers `PointerInput` ;
  - extension de la preuve de révocation : les messages clavier et pointeur
    postérieurs à `revokeCallbacks()` ne sont pas livrés ;
  - mise à jour de l'assertion clavier existante sur le contrat
    `pointerInstalled = true`.

## Évidence RED

Commande exécutée avant toute modification de production :

```text
./gradlew :kadre:backend:appkit:jvmTest --rerun-tasks --console=plain --tests 'org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest.nativeContentViewAdvertisesPointerTrackingAndRoutesPointerEventsOnMacOs'
```

Résultat attendu et observé : échec de
`nativeContentViewAdvertisesPointerTrackingAndRoutesPointerEventsOnMacOs` avec
`java.lang.AssertionError` à `KffiAppKitWindowPortMacOsTest.kt:386`, car
l'implémentation publiée annonçait encore `pointerInstalled = false` et
refusait les entrées pointeur.

## Évidence GREEN

Après l'intégration minimale :

```text
./gradlew :kadre:backend:appkit:jvmTest --rerun-tasks --console=plain --tests 'org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest.nativeContentViewAdvertisesPointerTrackingAndRoutesPointerEventsOnMacOs' --tests 'org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest.nativeInputObserverRevocationStopsKeyboardAndPointerCallbacksWhileTheManagedViewRemainsAliveOnMacOs'
```

Résultat : `BUILD SUCCESSFUL` ; les deux preuves natives passent.

## Vérification complète

```text
./gradlew :kadre:backend:appkit:jvmTest --rerun-tasks --console=plain
```

Résultat : `BUILD SUCCESSFUL`, 94 tests exécutés, 0 échec.

```text
git diff --check
```

Résultat : sortie vide, code de retour 0.

## Commits

- `c312bd395af307a6e2ecffd386faf33e8a3c5e0d` —
  `feat(appkit): install managed pointer tracking`

## Concerns

- Kadre ne gère pas directement `NSTrackingArea` : l'attachement et la
  libération bas niveau restent volontairement dans la façade KFFI publiée.
- Les preuves natives nécessitent macOS/AppKit et le snapshot KFFI
  `1.0.0-SNAPSHOT:20260828.190104-19` résolu par Gradle.
