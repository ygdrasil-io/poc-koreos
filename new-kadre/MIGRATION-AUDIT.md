# New Kadre — Audit humain de la baseline publique

**Baseline :** `497a2c2e812c7d8234eb3ebbccfb3fe91058a528`  
**Audit :** 23 août 2026  
**Résultat :** aucun propriétaire public connu ne dépend encore d’une décision résiduelle non approuvée.

## 1. Entrées auditées

- 12 dumps ABI trackés sous `kadre*/api/`, inchangés depuis la baseline ;
- 58 506 lignes de dump ;
- tous les top-level publics des source sets production publiés, y compris les modules sans dump ABI ;
- l’artifact public `kadre-test` ;
- les déclarations imbriquées et membres, héritant de la décision de leur propriétaire top-level sauf règle plus spécifique.

Comptes des dumps :

| Artifact / target | Propriétaires sémantiques top-level distincts | Note |
|---|---:|---|
| `kadre` common/JVM/Android | 1 | `EventLoop`; les typealiases sont audités depuis les sources |
| `kadre-core` common | 100 | superset des formes JVM/Android |
| `kadre-coroutines` JVM | 3 classes + 1 fonction top-level | fusion/remplacement complet |
| `kadre-android` Android | 6 propriétaires sémantiques + façade de fonctions | aucune implémentation concrète conservée publique |
| `kadre-uikit` iOS | 13 | fonctions UIKit comprises |
| `kadre-appkit` JVM | 9 propriétaires sémantiques + façades de fonctions | plus 4 539 owners ABI déclarés sous `bindings` |

Les 4 539 lignes déclarant un owner ABI public sous `org/graphiks/kadre/appkit/bindings/**` — companions et owners imbriqués compris — et les 31 409 lignes référençant ce package sont une famille FFI générée unique. La ligne publique supplémentaire qui référence ce package est `KadreApplication`, owner sémantique AppKit déjà classé séparément ; elle ne doit pas être comptée comme déclaration du package bindings. La décision humaine est `internalize` sans exception : aucune enum, constante, struct, fonction, companion ou wrapper généré de cette famille ne survit dans un artifact public Kadre. Les quatre `DesktopNativeWindowHandle` documentés sont de nouveaux DTO écrits à la main, pas des réexports de bindings.

## 2. Scan source des modules sans couverture ABI suffisante

Le scan top-level de production a relevé les comptes suivants avant classification :

| Module | Déclarations top-level potentiellement publiques |
|---|---:|
| `kadre` | 18 |
| `kadre-core` | 105 |
| `kadre-coroutines` | 4 |
| `kadre-android` | 9 |
| `kadre-uikit` | 13 |
| `kadre-appkit` hors bindings | 18 |
| `kadre-js` | 1 |
| `kadre-wasm` | 1 |
| `kadre-web-common` | 38 |
| `kadre-win32` | 20 |
| `kadre-x11` | 17 |
| `kadre-wayland` | 30 |
| `kadre-test` | 36 |

Ce scan est volontairement conservateur : un constructeur `internal` n’empêche pas sa classe d’être un propriétaire public, une interface `external` reste publique, et les fonctions génériques top-level sont comptées comme les autres. Pour `kadre` et `kadre-core`, le compte porte sur `commonMain`; les copies `expect`/`actual` déjà reliées au même owner ne sont pas recomptées. Pour les modules backend, tous les source sets target publiés sont comptés. Les 38 déclarations Web comprennent les neuf externals DOM/capture Wasm, explicitement internalisés dans `API-MIGRATION.md`. Les règles « Modules backend sans dump ABI complet » et « Artifact `kadre-test` actuel » de `API-MIGRATION.md` donnent une décision à chaque famille de cette table.

Le scan représente 310 lignes de déclaration et 281 noms simples distincts. Le nom qui fait passer le compte distinct de 280 à 281 est le `val` top-level public `dwmSetWindowAttribute`, compté en plus des classes, interfaces, fonctions et typealiases ; il est explicitement `internalize`. Après développement des règles de famille en listes nominatives, ces 281 noms possèdent tous un match nominal entier dans `API-MIGRATION.md`. Le scan équivalent des dumps trouve 127 noms d’owner ABI distincts hors wrappers `*Kt` et package bindings ; son résidu nominal est également vide.

## 3. Résidus nominaux traités un par un

Le premier passage, après exclusion de `appkit.bindings`, a trouvé exactement ces 22 propriétaires absents des règles spécifiques. Leur décision est maintenant inscrite avant les catch-all :

| # | Propriétaire actuel | Décision | Règle cible |
|---:|---|---|---|
| 1 | `AppKitCaptureSession` | internalize | implémentation `CaptureSession` |
| 2 | `AppKitScreenCapturer` | internalize | implémentation `CaptureManager` |
| 3 | `CGDisplayCaptureSession` | internalize | implémentation capture AppKit |
| 4 | `InputCapabilities` | replace | `SurfaceInputState.capabilities` |
| 5 | `KadreWindowDelegate` | internalize | bridge AppKit |
| 6 | `KeyChordModifierMatch` | remove | composition de raccourcis consumer |
| 7 | `MAX_CURSOR_SIZE` | remove | contrainte dynamique de capability |
| 8 | `MainThreadCheck` | internalize | invariant adapter AppKit |
| 9 | `PlatformEventType` | internalize | broker gamepad |
| 10 | `TabletToolButton` | replace | `PointerButton.Barrel/Eraser` |
| 11 | `TabletToolData` | replace | `PenState` |
| 12 | `TabletToolKind` | replace | `PointerKind.Pen/Eraser` |
| 13 | `contentRect` | replace | `HostSurface.state` |
| 14 | `defaultLogicalKey` | internalize | mapping backend |
| 15 | `defaultText` | internalize | mapping backend |
| 16 | `location` | internalize | mapping backend |
| 17 | `resolveScreenCapturer` | replace | `KadreScope.capture` |
| 18 | `setPreferredStatusBarStyle` | remove | chrome host UIKit |
| 19 | `setPrefersHomeIndicatorHidden` | remove | chrome host UIKit |
| 20 | `setPrefersStatusBarHidden` | remove | chrome host UIKit |
| 21 | `startKadreApplication` | replace | `KadreIos.attach(windowScene, window, surfaceView, …)` |
| 22 | `toLogical` / `toPhysical` | replace | quatre conversions monomorphes exactes |

Le résidu 22 regroupe deux noms d’overload parce qu’ils partagent une seule décision et une seule forme cible fermée ; leurs huit owners JVM/KLIB éventuels restent couverts par cette règle.

## 4. Revue des surfaces backend publiques

### Android

- `AndroidWindow`, capture sessions et screen capturer : internalize.
- `AndroidWindowAttributes` : replace par `WindowSpec`/capabilities.
- `KadreActivity` : replace par les deux familles `attachKadre`.
- `AndroidKadreRuntime` : remove.
- `contentRect`, `config`, `androidApp` : replace par state ou callback target-specific ; aucun getter SDK arbitraire public.

### UIKit

- delegate, registry, Metal view et capture concrète : internalize ou replace par attach/manager.
- status bar, home indicator et orientations : remove, ownership host controller.
- `startKadreApplication` : replace par attachement de scène.

### AppKit

- application, delegates, loop, window et capture concrète : internalize.
- extension setters de décoration/process : internalize ou remove ; aucune convenience API target-specific v1.
- `ActivationPolicy` : remove de Kadre Standalone ; un host Embedded garde ce choix.
- bindings : internalize intégralement, comme compté ci-dessus.

### Web JS/Wasm

- façades bootstrap `KadreJs`/`KadreWasm` : replace.
- loops, bridges, captures et `WebWindow` : internalize.
- types événements Web : internalize après mapping vers le commun.
- poll/wait strategies : internalize.
- prevent-default, canvas et cursor async : replace par les trois contrats exacts du catalogue/interop.

### Win32, X11 et Wayland

- windows, loops, wndproc, proxies, mappers, probes et capture : internalize.
- setters et attributes spécifiques : internalize v1 ; le handle borné reste l’escape hatch.
- fonctions d’identification/raw handle : replace par `KadrePlatform` et `withDesktopHandle`.
- tous les `runApp` : replace par les deux APIs desktop fermées.

### `kadre-test`

- event loop scriptée et conformance callbacks : replace par fake host/clock/controllers.
- harness exhaustif de preuve clavier : internalize dans les tests backend.
- aucun symbole actuel n’est conservé par compatibilité nominale.

## 5. Ambiguïtés découvertes et fermées pendant l’audit

1. Les tablet tools auraient perdu tilt/twist/pression : `PenState` et les boutons barrel/eraser sont maintenant dans le catalogue.
2. Les helpers DPI génériques n’avaient pas de cible : quatre conversions monomorphes avec rounding déterministe sont maintenant publiques.
3. Le prevent-default Web n’avait pas de remplacement : `SurfaceUpdate.inputDefaultBehavior` est désormais capability-driven.
4. Les setters UIKit auraient laissé croire que Kadre possède le chrome de l’app : ils sont explicitement supprimés.
5. Les extensions Win32/X11/Wayland auraient pu survivre par inertie : elles sont explicitement internalisées, l’accès expert passant par le callback de handle borné.
6. Un premier contrôle par sous-chaîne confondait `AppKitWindow` avec `AppKitWindowAttributes` : la règle nominale `AppKitWindow` est maintenant explicite et le gate utilise des frontières de nom entier.

## 6. Gate de migration

Au chantier d’implémentation, `checkPublicApi` doit reproduire l’inventaire à partir de la baseline et produire trois ensembles :

- `specificRuleMatches` : tous les propriétaires de cet audit ;
- `generatedBindingMatches` : uniquement `org.graphiks.kadre.appkit.bindings.**` ;
- `residualMatches` : doit être vide.

Un owner nouveau, un fichier de dump nouveau ou une déclaration source publique nouvelle fait échouer le gate tant qu’une règle spécifique n’est pas ajoutée. Les comptes de ce document ne sont pas utilisés comme tolérance : ils servent à détecter une modification des entrées et déclenchent une nouvelle revue, jamais à ignorer le delta.

## 7. Conclusion de l’audit

- [x] Tous les dumps ABI trackés ont été lus.
- [x] Les modules publiés sans dump complet ont été scannés.
- [x] Les 281 noms source et 127 noms d’owner ABI non-binding ont un match nominal entier ; aucun résidu n’est masqué par une sous-chaîne ou une wildcard.
- [x] Les 22 résidus nominaux ont une décision spécifique.
- [x] Les 4 539 owners ABI déclarés sous bindings ont une décision de famille explicite sans survivor.
- [x] L’ancien `kadre-test` est entièrement classé.
- [x] Aucun symbole actuel n’est conservé uniquement parce qu’il était déjà public.
