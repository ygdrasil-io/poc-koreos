# New Kadre AppKit Host Implementation Plan

> **For agentic workers:** use `superpowers:executing-plans` and `superpowers:test-driven-development`. Sub-agents are forbidden in this side-conversation workspace.

**Goal:** livrer un provider AppKit JVM réellement découvert et un runner standalone headless possédant `NSApplication.run`, tout en gardant embedded explicitement indisponible tant que le bridge callback KFFI requis n'existe pas.

**Stack:** cette PR part de `codex/desktop-provider`, publie `codex/appkit-host` et cible la PR 4.

**Architecture:** le nouvel artifact interne `backend:appkit` implémente le SPI `DesktopBackendProvider` sans dépendre de `platform:desktop`. Sa classe ServiceLoader est légère et ne touche pas KFFI pendant la découverte ou `isAvailable()`. Un broker process-wide arbitre l'unique owner standalone. Le provider crée un `RuntimeHostController`, exécute l'application sur un scope structuré worker, possède la boucle AppKit sur le process main thread, puis transforme tout retour ou échec natif après admission en outcome de session.

## Décision de scope liée à KFFI

`KFFI-OBJC-001/003` bloque les notifications lifecycle managées nécessaires à embedded et aux delegates de fenêtre. Cette PR :

- supporte réellement `Standalone` headless ;
- publie `supportedIntegrations = emptySet()` ;
- laisse donc `Embedded(AppKitMainLoop)` échouer avec `InvalidRequest("options")` avant attach ;
- n'ajoute aucun `Linker.upcallStub`, subclass callback ou observer FFM local dans Kadre.

L'embedded entrera dans le scope dès que le bridge KFFI sera disponible, sans modifier l'API publique.

## Task 1 — Artifact AppKit et ServiceLoader

1. Inclure `:kadre-new:backend:appkit`, KMP JVM 25, groupe interne, dépendances `runtime` et `kffi-objc`.
2. Ajouter le service descriptor du provider.
3. Ajouter le backend en `runtimeOnly` de `platform:desktop` et aux publications contractuelles.
4. Prouver que découverte, lecture de `backend` et `isAvailable()` n'initialisent aucune primitive KFFI.

## Task 2 — Extension runtime pour terminaison host

1. Ajouter un observer technique de terminaison de session au contrôleur runtime.
2. Ajouter une entrée `fail(PlatformFailure)` qui produit un outcome de session stable après admission.
3. Garder les callbacks observer isolés : leur exception est reportée mais ne change pas l'outcome.
4. Tester observer unique, failure plateforme et absence de reverse cancellation.

## Task 3 — Broker standalone

1. Refuser hors main thread avec `InvalidRequest("options")` avant session/factory.
2. Garantir un seul owner standalone process-wide via CAS ; le concurrent reçoit `AlreadyInUse(Host)`.
3. Libérer le slot dans tous les chemins, y compris exception native et failure applicative.
4. Autoriser des runs séquentiels sans état de session courant.

## Task 4 — Boucle AppKit native bornée

1. Créer/récupérer `NSApplication.sharedApplication` sur le main thread seulement.
2. Appeler `NSApplication.run()` sans `terminate:`.
3. À la terminaison de session, scheduler `stop:` sur le main thread via le helper KFFI existant ; traiter aussi une demande arrivée avant l'entrée dans `run()`.
4. Au retour externe de la boucle, détacher le host puis attendre le même outcome terminal.
5. Une exception native après admission devient `Failed(PlatformFailure(AppKit, "appkit-host", code))`, jamais une failure externe du runner.

## Task 5 — Scénarios O2/O3 ciblés

1. Avec bridge injecté : main thread refusé, application stop, application failure, exception native, busy concurrent, réutilisation séquentielle et embedded non supporté.
2. Avec KFFI réel sur macOS et `-XstartOnFirstThread` : découverte ServiceLoader, main thread, entrée/sortie de `NSApplication.run()` avec application qui demande son arrêt.
3. Aucun sleep/retry ; synchronisation par latches et outcomes.
4. Activer seulement les contrats standalone réellement prouvés.

## Task 6 — Audit et PR

1. Exécuter les checks `backend:appkit`, runtime, Desktop ABI et `:kadre-new:check` avec `--rerun-tasks`.
2. Vérifier metadata common/JVM 25, service descriptor, POM runtime et absence de `java.lang.foreign` dans `backend:appkit`.
3. Pousser `codex/appkit-host` et ouvrir la PR contre `codex/desktop-provider`.

## Hors scope

- embedded AppKit tant que KFFI-OBJC-001/003 reste open ;
- lifecycle actif/inactif par notifications ;
- fenêtre, surface, delegate, input et IME ;
- activation policy nécessitant KFFI-OBJC-002 ;
- toute couche FFI ou binding local.
