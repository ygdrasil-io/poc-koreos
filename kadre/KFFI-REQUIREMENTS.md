# New Kadre — Besoins de bindings KFFI

Ce registre contient uniquement les gaps rencontrés pendant l'implémentation de Kadre. Kadre ne génère pas de binding, ne possède pas de couche FFI et n'ajoute pas de downcall/upcall Panama local pour contourner un gap. Un appel Objective-C générique déjà fourni par KFFI peut servir temporairement lorsque son ownership et sa signature sont sûrs ; le besoin typé reste ouvert ici.

| ID | Statut | Priorité | Domaine | Besoin KFFI | Usage Kadre bloqué | Workaround autorisé | Référence |
|---|---|---:|---|---|---|---|---|
| KFFI-OBJC-001 | open | blocking | Objective-C callbacks | Bridge managé permettant d'enregistrer une méthode Objective-C implémentée par une lambda Kotlin/JVM, avec signature typée, lifetime closeable, routage thread-safe et libération après la dernière upcall | notifications lifecycle `NSApplication`, `NSWindowDelegate`, callbacks `NSView` input/IME et donc embedded AppKit, fenêtres et input réels | aucun upcall `java.lang.foreign.Linker` local dans Kadre | issue KFFI à créer |
| KFFI-OBJC-002 | open | high | AppKit scalars | Corriger les signatures générées qui exposent `NSApplicationActivationPolicy`, `NSEventType` et `NSEventModifierFlags` comme `MemorySegment` au lieu de scalaires/enums NSInteger/NSUInteger ; conserver `NSPoint` comme struct typée | activation policy standalone et événement synthétique de réveil AppKit sans `ObjCRuntime.msgSend` générique | appel générique KFFI documenté et borné dans `AppKitNativeApplication` ; aucun downcall Kadre | issue KFFI à créer ; reproduit par `NSEvent.otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2` |
| KFFI-OBJC-003 | open | high | notification observation | Fournir soit un owner `NSNotificationCenter.observe(...)`, soit les primitives block/callback managées nécessaires, avec retrait idempotent et garantie qu'aucune callback Kotlin ne commence après `close()` | lifecycle embedded et detach sur `NSApplicationWillTerminateNotification` | aucun polling hors main thread et aucun observer FFM local | couvert par KFFI-OBJC-001 si le bridge est assez général |

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
- `NSNotificationCenter` et les symboles de notifications sont générés, mais leur consommation callback reste bloquée par KFFI-OBJC-001/003.

Le host standalone doit poster un `NSEventTypeApplicationDefined` après `stop:` pour réveiller une boucle sans fenêtre. En attendant KFFI-OBJC-002, la création de cet événement est l'unique appel `ObjCRuntime.msgSend` générique du backend ; sa signature Objective-C est fixée, son autorelease pool englobe aussi `postEvent_atStart`, et aucun handle natif n'échappe à l'adapter.
