# New Kadre — Besoins de bindings KFFI

Ce registre contient uniquement les gaps rencontrés pendant l'implémentation de Kadre. Kadre ne génère pas de binding, ne possède pas de couche FFI et n'ajoute pas de downcall/upcall Panama local pour contourner un gap. Un appel Objective-C générique déjà fourni par KFFI peut servir temporairement lorsque son ownership et sa signature sont sûrs ; il est retiré dès que KFFI publie une API typée couvrant le besoin.

| ID | Statut | Priorité | Domaine | Besoin KFFI | Usage Kadre bloqué | Workaround autorisé | Référence |
|---|---|---:|---|---|---|---|---|
| KFFI-OBJC-001 | closed | blocking | Objective-C callbacks | `ObjCManagedClass` et `ObjCManagedInstance` fournissent une méthode Objective-C implémentée par une lambda Kotlin/JVM, avec signature finie typée, lifetime closeable, routage thread-safe et libération après la dernière upcall | notifications lifecycle `NSApplication`, `NSWindowDelegate`, callbacks `NSView` input/IME et donc embedded AppKit, fenêtres et input réels | aucun upcall `java.lang.foreign.Linker` local dans Kadre | [KFFI #35](https://github.com/Graphiks-org/kffi/pull/35), artifact `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` |
| KFFI-OBJC-002 | closed | high | AppKit scalars | `NSApplicationActivationPolicy`, `NSEventType` et `NSEventModifierFlags` sont exposés comme scalaires/enums ; `NSPoint` reste une struct typée et les factories `NSEvent` publient cette signature | activation policy standalone et événement synthétique de réveil AppKit sans `ObjCRuntime.msgSend` générique | aucun appel Objective-C générique dans Kadre pour créer l'événement | [Kextract #50](https://github.com/klang-toolkit/kextract/pull/50), [KFFI #35](https://github.com/Graphiks-org/kffi/pull/35), artifact `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` |
| KFFI-OBJC-003 | closed | high | notification observation | `NSNotificationCenter.observe(...)` fournit un owner closeable, retrait idempotent et révocation de l'admission avant libération | lifecycle embedded et detach sur `NSApplicationWillTerminateNotification` | aucun polling hors main thread et aucun observer FFM local | [KFFI #35](https://github.com/Graphiks-org/kffi/pull/35), artifact `org.graphiks:kffi-objc:1.0.0-SNAPSHOT` |

## Critères du bridge callback

- aucune adresse de fonction ou `MemorySegment` d'upcall à construire dans Kadre ;
- signatures Objective-C validées par KFFI, au minimum `void(id, SEL, id)` et `BOOL(id, SEL, id)` ;
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
