# New Kadre — Besoins de bindings KFFI

Ce registre contient uniquement les gaps rencontrés pendant l'implémentation de Kadre. Kadre ne génère pas de binding, ne possède pas de couche FFI et n'ajoute pas de downcall/upcall Panama local pour contourner un gap. Un appel Objective-C générique déjà fourni par KFFI peut servir temporairement lorsque son ownership et sa signature sont sûrs ; il est retiré dès que KFFI publie une API typée couvrant le besoin.

| ID | Statut | Priorité | Domaine | Besoin KFFI | Usage Kadre bloqué | Workaround autorisé | Référence |
|---|---|---:|---|---|---|---|---|
| KFFI-OBJC-001 | closed | blocking | Objective-C callbacks | `ObjCManagedClass` et `ObjCManagedInstance` fournissent une méthode Objective-C implémentée par une lambda Kotlin/JVM, avec signature finie typée, lifetime closeable, routage thread-safe et libération après la dernière upcall | notifications lifecycle `NSApplication`, `NSWindowDelegate`, callbacks `NSView` input/IME et donc embedded AppKit, fenêtres et input réels | aucun upcall `java.lang.foreign.Linker` local dans Kadre | [KFFI #35](https://github.com/Graphiks-org/kffi/pull/35), artifact `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` |
| KFFI-OBJC-002 | closed | high | AppKit scalars | `NSApplicationActivationPolicy`, `NSEventType` et `NSEventModifierFlags` sont exposés comme scalaires/enums ; `NSPoint` reste une struct typée et les factories `NSEvent` publient cette signature | activation policy standalone et événement synthétique de réveil AppKit sans `ObjCRuntime.msgSend` générique | aucun appel Objective-C générique dans Kadre pour créer l'événement | [Kextract #50](https://github.com/klang-toolkit/kextract/pull/50), [KFFI #35](https://github.com/Graphiks-org/kffi/pull/35), artifact `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` |
| KFFI-OBJC-003 | closed | high | notification observation | `NSNotificationCenter.observe(...)` fournit un owner closeable, retrait idempotent et révocation de l'admission avant libération | lifecycle embedded et detach sur `NSApplicationWillTerminateNotification` | aucun polling hors main thread et aucun observer FFM local | [KFFI #35](https://github.com/Graphiks-org/kffi/pull/35), artifact `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` |
| KFFI-OBJC-004 | closed | blocking | Objective-C callbacks | signature managée `BOOL(id, SEL)` publiée, avec fallback explicite, fermeture/quiescence et ABI `BOOL` sûrs arm64/x86_64 | sous-classe AppKit `NSView` qui répond `true` à `acceptsFirstResponder`; sans elle le clavier Phase 4 ne peut être promis | aucun upcall, `Linker`, block ou `msgSend` local dans Kadre | KFFI snapshot `1.0.0-SNAPSHOT:20260829.040320-20`, `APPKIT-PHASE-4-INPUT-DESIGN.md` |
| KFFI-OBJC-005 | closed | blocking | synthèse et injection d’événements AppKit | `NSApplication.postScrollWheelEvent(AppKitScrollWheelEvent)` public typé, avec unités et phases CoreGraphics | preuve O3 Phase 4 de la queue et de la conversion scroll native; la livraison au responder reste le cahier manuel car l’événement converti n’a pas de fenêtre associée | aucun `MemorySegment` brut, factory Objective-C générique, block ou downcall local dans Kadre | KFFI snapshot `1.0.0-SNAPSHOT:20260829.040320-20`, `APPKIT-PHASE-4-INPUT-DESIGN.md`, scénario O3 `appkit-input-native-scroll` |
| KFFI-OBJC-006 | pending publication | blocking | `NSTextInputClient` managé | signatures `void(id, SEL, SEL)` et `id(id, SEL)`, conversion sûre de `SEL`, résultats `NSAttributedString`/`NSArray` retenus jusqu’à la révocation du receiver | Phase 6 : IME AppKit par surface, sans fuite de `MemorySegment`, de struct ABI ni d’owner Objective-C dans Kadre | aucun callback Panama, `ObjCStructArg`, selector/downcall ou lifetime FFI local ; la PR KFFI est consommée uniquement depuis Maven local avant publication | [Kextract #60](https://github.com/klang-toolkit/kextract/pull/60), [KFFI #62](https://github.com/Graphiks-org/kffi/pull/62), branche KFFI `feat/managed-ime-callbacks` |
| KFFI-OBJC-007 | pending review | blocking | protocol Objective-C reçu en callback | Kextract génère, pour les protocoles explicitement demandés, un receiver privé `MemorySegment.as…()` afin que les méthodes typées du protocole soient appelées sans cast ni selector manuel dans le consommateur | Phase 7 : `NSDraggingInfo` emprunté par `NSDraggingDestination` doit être lu avant le retour de la callback ; Kadre ne peut pas exposer son pointeur ni réécrire les méthodes du protocole | aucun cast ou `msgSend` local ; Kadre ne consomme que le receiver généré, puis retient/copie ses valeurs avant de rendre la callback | [Kextract #61](https://github.com/klang-toolkit/kextract/pull/61), [KFFI #63](https://github.com/Graphiks-org/kffi/pull/63), branche KFFI `feat/objc-protocol-receivers` |

## Critères du bridge callback

- aucune adresse de fonction ou `MemorySegment` d'upcall à construire dans Kadre ;
- signatures Objective-C validées par KFFI, notamment `void(id, SEL, id)` pour les callbacks avec événement et `BOOL(id, SEL)` pour `acceptsFirstResponder` ;
- owner explicite et `close()` idempotent ;
- admission fermée avant retrait natif, attente de la dernière callback déjà admise, puis libération ;
- exception Kotlin capturée par un reporter configuré, jamais déroulée à travers Objective-C ;
- possibilité d'associer plusieurs instances Kotlin à une classe Objective-C enregistrée une fois ;
- tests KFFI de concurrence close/callback et de réutilisation séquentielle dans un même processus.

## Bindings déjà suffisants pour le host standalone headless

- `NSThread.isMainThread()` ;
- `NSApplication.sharedApplication()`, `isRunning()`, `run()` et `stop(sender)` ;
- `NSObject.performSelectorOnMainThread_withObject_waitUntilDone(...)` ;
- `NSApplication.postEvent_atStart(...)` ;
- `NSNotificationCenter.observe(...)` et les symboles de notifications générés, avec owner managé closeable.

Le host standalone poste un `NSEventTypeApplicationDefined` après `stop:` pour réveiller une boucle sans fenêtre. `AppKitNativeApplication` emploie la factory KFFI typée `NSEvent.otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2`; son autorelease pool englobe aussi `postEvent_atStart`, et aucun handle natif n'échappe à l'adapter.
