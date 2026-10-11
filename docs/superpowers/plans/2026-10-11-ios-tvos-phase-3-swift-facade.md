# iOS/tvOS Phase 3 — Façade Swift `KadreHost`, controller hôte et SwiftUI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer le module source Swift `KadreHost` avec les types EXACTS d'INTEROP-EXPORTS §5 (`KadreIosHost`, `KadreSessionHandle`, `KadreObservation`, snapshots/outcomes, `KadreError`, 17 cases `KadreFailure` fermées), le `KadreHostViewController`, la distribution XCFramework (4 slices iOS/tvOS device+simulator), et des consumers Swift autonomes (UIKit + SwiftUI, iOS + tvOS) attachant avec une factory produite par leur propre module Kotlin — jalon A de la roadmap.

**Architecture:** Le module `KadreHost` est une façade source Swift (répertoire `kadre/platform/uikit/swift/Sources/KadreHost/`, inclus par chemin dans les consumers) qui enrobe le framework `KadreUikit` : `KadreIosHost.attach` traduit `KadreResult<KadreSession>` en `throws KadreError`, `KadreSessionHandle` serialise `session.state` sur `@MainActor` (émission immédiate, retrait idempotent), `awaitTermination` borne chaque waiter dans un Job propre (cancellation Swift = `CancellationError`, session intacte). La distribution est un **XCFramework STATIQUE** et le consumer Kotlin du consumer externe est lui aussi **statique** : les deux frameworks se lient dans l'app sans embed — une seule copie du runtime (anti-duplication) et les symboles Obj-C gardent le préfixe `KadreUikit` (identité de types par swift_name + lien). L'audit façade applique `@HiddenFromObjC` (pré-autorisé INTEROP §2 l.34) sur `withUIKitView` — le seul export `UIView*` du contrat — les sondes driver restent visibles et documentées comme scaffolding.

**Tech Stack:** Swift 5 (module source `KadreHost`), KGP 2.4.20 `XCFramework` DSL (static), xcodegen, consumer Kotlin minimal (KMP, resolves `org.graphiks.kadre:uikit` depuis le repo contractTest), XCTest sur simulateurs iPad/Apple TV (destinations phase 1), `@HiddenFromObjC` (`kotlin.native`, `@OptIn(ExperimentalObjCRefinement)`).

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` §5 Phase 3 (l.216–231) ; `kadre/INTEROP-EXPORTS.md` §5 (l.95–188, types verbatim), §7 (règles callback + Kotlin-only), §8 (nommage), §9 (consumer tests obligatoires) ; `kadre/POLICY-PROFILES.md` (mapping profils).

## Décisions fermées par ce plan

| Décision | Résultat fermé ici |
|---|---|
| Distribution | `XCFramework("KadreUikit")` DSL KGP : `xcf.add(this)` dans le bloc framework existant ; tasks `assembleDebugXCFramework`/`assembleReleaseXCFramework`, sortie `build/XCFrameworks/<buildType>/KadreUikit.xcframework`, 4 slices (IOS_DEVICE, IOS_SIMULATOR, TVOS_DEVICE, TVOS_SIMULATOR). **STATIQUE** (`isStatic = true` sur un framework de distribution séparé — voir D-duplication). |
| Anti-duplication du runtime | Le framework de distribution et le framework Kotlin du consumer sont **statiques** : l'app lie les deux à l'édition de liens (zéro embed) — une seule copie de chaque symbole ; les classes gardent le préfixe `KadreUikit` (compilées contre les mêmes headers). Le driver continue sur son framework DYNAMIQUE embeddé (phase 1 — intouché). |
| Factory du consumer externe | Produite par le module applicatif Kotlin du consumer (`consumers/swift/consumer-kmp/`, resolves `org.graphiks.kadre:uikit` depuis le repo contractTest, `export()` uikit dans son framework statique). La conformité Swift directe du driver (phase 1) reste supportée et non-régressée (INTEROP §5 l.140 : non promise, pas interdite). |
| Audit façade (le F1 de phase 2) | `@HiddenFromObjC` sur `withUIKitView` (Swift ne l'appelle pas — la preuve phase 2 passe par la sonde Kotlin ; INTEROP §2 l.34 pré-autorise). Les sondes driver (`KadreUikitProbe`, `KadreUikitSurfaceDriver`) restent visibles, documentées comme scaffolding opt-in dans capabilities/uikit.md — la surface PROMISE (§5) n'en contient aucune, l'en-tête complet est inventorié dans le rapport d'audit. |
| Profils Swift | `KadrePolicyProfile.default/.realtime/.recording` → `KadrePolicies.Default/Realtime/Recording` (les trois values exactes ; `Realtime`/`Recording` peuvent échouer à l'attach avec `UnsupportedPolicy` — POLICY-PROFILES, traduit en `KadreError`). |
| SwiftUI | `integration:swiftui` NON matérialisé (contenu réel absent du module Kadre) : le wrapper `UIViewControllerRepresentable` (~10 lignes, possédé par l'app) vit dans le consumer SwiftUI — conformément INTEROP §5 l.139 et roadmap l.229. |
| Contracts | `BCK-017` « Swift KadreHost facade attach, observation and termination » (iOS+tvOS) + `BCK-018` « Swift KadreHost SwiftUI consumer » (iOS+tvOS) — preuves par les consumers Swift. |

## Global Constraints

- La surface Swift promise est FERMÉE (§5 verbatim) : aucun `Flow`, `CoroutineScope`, `Continuation`, `Throwable`, `Any`, manager, callback input, type FFI généré ou adresse native n'apparaît dans la façade (§9 l.429 : le test échoue sinon — les sources KadreHost ne référencent que les types fermés).
- Les noms publics Swift commencent par `Kadre` ; cases lower-camel ; mapping exhaustif et testé vers les variantes Kotlin (§8) ; `ShutdownTimedOut.timeout` → `timeoutNanoseconds: Int64` ; IDs → strings opaques.
- `KadreApplicationFactory` Swift : recevoir et repasser OUI ; conformance Swift directe NON promise v1 (le consumer externe utilise sa factory Kotlin) ; le driver en conformance directe reste vert.
- Aucun layout Kadre dans `KadreHostViewController` ; sa view EST la surfaceView ; attach exactement une fois (fenêtre + scène requises) ; `deinit`/disconnect/`close` ferment ; un retrait temporaire de la hiérarchie ne ferme pas.
- `observeState` : `@MainActor`, émission IMMÉDIATE du snapshot courant, sérialisé, retrait idempotent. `awaitTermination` : cancellation de la Task → `CancellationError`, retire UNIQUEMENT le waiter, la session continue.
- Le driver phase 1/2 reste vert : destinations, projets et tests intouchés sauf ajout ; le framework dynamique du driver n'est pas remplacé.
- Preuves séparées iOS/tvOS obligatoires ; gate macOS-only hors `:kadre:check` ; chaque commande Bash < 8 min (longues : `run_in_background` + polling — deux dispatchs sont morts autrement).
- Activation de contrat : `planned → active` dans le commit des scénarios+preuves+sentinelles ; preuves target-aware (iOS ≠ tvOS).

## Review Focus

1. **Duplication du runtime dans l'app consumer** — deux frameworks Kotlin dynamiques embarqués = crash de classes dupliquées ; le lien statique des DEUX frameworks (distribution + consumer Kotlin) est la garde. Test : l'app consumer lie exactement 2 frameworks statiques, zéro embed, et s'exécute (le crash de duplication est un échec immédiat au lancement).
2. **Race cancellation/terminaison de `awaitTermination`** — la continuation ne doit jamais être reprise deux fois (terminaison pendant cancellation, cancellation pendant reprise) ; annuler la Task ne doit JAMAIS arrêter la session. Test : race exécutée en boucle (50 itérations), une seule reprise, session toujours terminée exactement une fois.
3. **Sérialisation `@MainActor` et émission immédiate de `observeState`** — l'observer reçoit le snapshot courant IMMÉDIATEMENT à l'inscription (avant tout changement), les callbacks ne s'entrelacent pas, le retrait double est idempotent, observer après terminaison reçoit `terminated` et pas de crash. Test : ordonnancement prouvé par ordre d'arrivée.
4. **`KadreHostViewController` : attach unique et cycles** — re-add à la hiérarchie après close ne doit PAS re-attacher ni crasher ; deinit sans attach ne fuite pas ; la view du controller est bien celle passée à `KadreIos.attach` (isEqual via le fixture).
5. **Traduction exhaustive des 17 `KadreFailure`** — chaque cas Kotlin a SA case Swift (mapping testé un par un — une case manquante = fatal au premier failure réel) ; `ShutdownTimedOut` → nanoseconds Int64 ; les data objects (`parentScopeCancelled`, `applicationFailure`) mappés sans payload.

---

### Task 1: Distribution XCFramework statique (4 slices)

**Files:**
- Modify: `kadre/platform/uikit/build.gradle.kts` (XCFramework DSL + bloc framework de distribution statique)
- Test: existence et structure des `.xcframework` produits (probe)

**Interfaces:**
- Consumes: bloc `binaries { framework { baseName = "KadreUikit"; export(project(":kadre:foundation")) } }` existant (l.43–57).
- Produces: tasks `assembleDebugXCFramework`/`assembleReleaseXCFramework` → `kadre/platform/uikit/build/XCFrameworks/{Debug,Release}/KadreUikit.xcframework` (4 slices : ios-arm64, ios-arm64_x86_64? non — ios-arm64 simulator + device, tvos arm64 ×2) consommés par Task 2/5.

- [ ] **Step 1: Câbler le DSL XCFramework (distribution statique)**

Dans `kadre/platform/uikit/build.gradle.kts` — le framework DYNAMIQUE existant (driver, embed) reste tel quel ; AJOUTER un framework de distribution statique par target :

```kotlin
// Distribution phase 3 : XCFramework STATIQUE — l'app consumer lie (sans embed) le
// xcframework ET le framework Kotlin du consumer (lui aussi statique) : une seule copie
// du runtime à l'édition de liens, les symboles gardent le préfixe KadreUikit.
val kadreUikitXcFramework = org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework("KadreUikit")

targets.withType<KotlinNativeTarget>().configureEach {
    binaries {
        framework("KadreUikitDistribution") {
            isStatic = true
            kadreUikitXcFramework.add(this)
            export(project(":kadre:foundation"))
        }
    }
}
```

(Vérifier les noms réels : le deuxième `framework(baseName)` crée un second binaire par target ; les tasks deviennent `assembleDebugXCFramework` etc. Si KGP exige que TOUS les frameworks ajoutés à un XCFramework aient le même baseName — c'est le cas, ils l'ont. En cas de conflit avec le framework dynamique existant (deux `framework {}` par target), nommer le dynamique explicitement `framework { baseName = "KadreUikit" }` et le distribution `framework("KadreUikitDistribution")` — le XCFramework ne prend que le distribution.)

- [ ] **Step 2: Probe d'assemblage**

```bash
./gradlew :kadre:platform:uikit:assembleDebugXCFramework
```

(background si > 8 min). Expected: `build/XCFrameworks/Debug/KadreUikit.xcframework` avec `ios-arm64/`, `ios-arm64-simulator/`, `tvos-arm64/`, `tvos-arm64-simulator/` (4 slices, chacune avec le static archive + Headers). Vérifier `Info.plist` du xcframework (AvailableLibraries = 4).

- [ ] **Step 3: Vérifier l'absence de régression driver**

```bash
./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64 :kadre:platform:uikit:compileKotlinIosArm64 :kadre:platform:uikit:compileKotlinTvosArm64
```

(Le framework dynamique du driver doit rester intact.)

- [ ] **Step 4: Commit** — `git commit -m "build(uikit): assemble a static four-slice xcframework for distribution"`

---

### Task 2: Consumer externe — squelette consumer-kmp statique + lien deux-frameworks (LA tâche à risque)

**Files:**
- Create: `kadre/consumers/swift/settings.gradle.kts`, `kadre/consumers/swift/build.gradle.kts` (agrégateur : tasks Exec vers les sous-builds)
- Create: `kadre/consumers/swift/consumer-kmp/settings.gradle.kts`, `build.gradle.kts` (KMP : targets iosArm64/iosSimulatorArm64/tvosArm64/tvosSimulatorArm64, framework STATIQUE `ConsumerKadre`, resolves `org.graphiks.kadre:uikit:<version>` depuis `-PkadreRepository`)
- Create: `kadre/consumers/swift/consumer-kmp/src/commonMain/kotlin/org/graphiks/kadre/consumer/ConsumerApplicationFactory.kt` (factory Kotlin : `KadreApplicationFactory` produisant une `KadreApplication` qui requestStop après N frames cooperatives — miroir DriverKadreApplication mais côté Kotlin)
- Create: `kadre/consumers/swift/consumer-app/` (xcodegen project.yml : app UIKit iOS + app UIKit tvOS + 2 bundles XCTest, liant `ConsumerKadre.framework` (statique, link-only) + `KadreUikit.xcframework` (statique, link-only) + les sources `KadreHost` par chemin)
- Test: le consumer app TEST s'exécute sur les deux simulateurs (attach via la factory Kotlin → session Running → stop → Terminated).

**Interfaces:**
- Consumes: Task 1 (xcframework statique) ; le repo contractTest (uikit publié) ; destinations phase 1.
- Produces: le squelette dans lequel Tasks 3–5 écrivent la façade et les tests ; la preuve anti-duplication (l'app se lance — des classes dupliquées crashent au lancement).

- [ ] **Step 1: consumer-kmp** — KMP 4 targets natifs (même liste que uikit), framework `ConsumerKadre` STATIQUE par target (`isStatic = true`, `export("org.graphiks.kadre:uikit")`), dependencies `commonMain { api("org.graphiks.kadre:uikit:$kadreVersion") }` avec le repo contractTest en property (`-PkadreRepository`, `error()` si absent — miroir consumers/kotlin). `ConsumerApplicationFactory` :

```kotlin
package org.graphiks.kadre.consumer

public class ConsumerApplicationFactory : KadreApplicationFactory {
    public override fun create(context: KadreLaunchContext): KadreApplication =
        ConsumerApplication()
}

internal class ConsumerApplication : KadreApplication {
    // run est suspend sur le dispatcher principal de la scène : JAMAIS de runBlocking ici
    // (deadlock — le pattern phase 1 utilisait un timer pour la même raison). L'application
    // demande l'arrêt depuis son propre scope après un court délai : le test observe
    // starting/running puis terminated. Lire la signature RÉELLE de KadreApplication dans
    // foundation Application.kt avant d'écrire (adapter, ne jamais deviner).
    public override suspend fun run(receiver: KadreScope) {
        kotlinx.coroutines.delay(200)
        receiver.requestStop()
    }
}
```

- [ ] **Step 2: xcodegen consumer-app** — 2 apps (iOS 16/tvOS 16) dont la scène crée `KadreHostViewController` (Task 5 le remplira ; pour ce squelette, la scène appelle directement `try KadreIosHost.attach(...)` avec `ConsumerApplicationFactory()`) + 2 bundles XCTest. Lien : `ConsumerKadre.framework` + `KadreUikit.xcframework` tous deux `embed: false` (statiques), `OTHER_LDFLAGS` si nécessaire. Les sources `../../platform/uikit/swift/Sources/KadreHost/**` incluses par chemin dans les targets app (le squelette KadreHost peut être vide au début : un seul fichier `KadreHost.swift` placeholder remplacé en Task 3).

- [ ] **Step 3: LA preuve anti-duplication** — xcodebuild build + test sur les deux destinations : l'app se LANCE (le crash de duplication — `Class X is implemented in both...` — survit au lancement), l'attach via la factory Kotlin produit une session Running, stop → Terminated. Les deux familles vertes.

- [ ] **Step 4: Commit** — `git commit -m "feat(uikit): stand up the static two-framework swift consumer skeleton"`

---

### Task 3: `KadreHost` — value model fermé (KadreFailure 17 cases, KadreError, snapshots/outcomes) TDD

**Files:**
- Create/Modify: `kadre/platform/uikit/swift/Sources/KadreHost/KadreFailure.swift` (+ `KadreError.swift`, `KadreSessionSnapshot.swift`, `KadreSessionOutcome.swift`, `KadreStopReason.swift`, `KadrePolicyProfile.swift`)
- Test: `kadre/consumers/swift/consumer-app/tests/Shared/KadreHostMappingTests.swift` (XCTest, mappant chaque case depuis les types Kotlin)

**Interfaces:**
- Consumes: les types Kotlin exportés (`KadreFailure*` classes générées par variant — `KadreUikitKadreFailureUnsupported` etc., `SessionState*`, `SessionOutcome*`, `SessionStopReason`, `KadrePlatform`, `KadreResourceKind`, `KadreOperation`, `KadrePolicyComponent`, `InteractionFailureReason`, `KadrePermission`, `Duration`→nanoseconds) — LIRE le header généré pour les formes exactes (initializers des classes générées, accesseurs des champs).
- Produces: les types Swift EXACTS de §5 (verbatim, `public enum KadreFailure` 17 cases avec payloads, `KadreError(failure, message)`, `KadreSessionSnapshot`, `KadreSessionOutcome`, `KadreStopReason` 5 cases, `KadrePolicyProfile`) + `init?(_ failure: KadreFailure)` / helpers de mapping internes `KadreFailureMapping.swift`.

- [ ] **Step 1: Écrire les tests de mapping d'abord** — UN test par case (17) construisant le type Kotlin généré correspondant et assertant la case Swift + payload (ex : `ShutdownTimedOut(Duration.Companion.invoke(seconds 2))` → `.shutdownTimedOut(timeoutNanoseconds: 2_000_000_000)`) + les data objects (`parentScopeCancelled`, `applicationFailure`) + un test `KadreError.Equatable` + `KadreStopReason` 5/5 + snapshot/outcome mapping (Starting→.starting etc.).
- [ ] **Step 2: Rouge** (types Swift absents) puis implémentation des types §5 verbatim + mapping exhaustif (switch sur les classes générées, `default: return nil` interdit — exhaustive ou fatal).
- [ ] **Step 3: Vert sur les deux destinations.**
- [ ] **Step 4: Commit** — `git commit -m "feat(uikit): map the closed kadre failure value model into the swift facade"`.

---

### Task 4: `KadreSessionHandle` + `KadreObservation` + `KadreIosHost.attach`

**Files:**
- Create: `kadre/platform/uikit/swift/Sources/KadreHost/KadreSessionHandle.swift`, `KadreObservation.swift`, `KadreIosHost.swift`
- Test: `kadre/consumers/swift/consumer-app/tests/Shared/KadreSessionHandleTests.swift`

**Interfaces:**
- Consumes: Task 3 (value model) ; `KadreIos.shared.attach` (header l.4992) ; `KadreSession` (id, state StateFlow, requestStop, close, awaitTerminationWithCompletionHandler) ; Task 2 (la factory Kotlin du consumer).
- Produces: les types §5 — `KadreIosHost.attach(windowScene:window:surfaceView:applicationFactory:policy:) throws -> KadreSessionHandle` ; `KadreSessionHandle` (id String opaque, state snapshot, observeState → KadreObservation, requestStop, close, awaitTermination async) ; `KadreObservation.close()` idempotent.

- [ ] **Step 1: `KadreIosHost.attach`** — appelle `KadreIos.shared.attach(...)`, unwrap `KadreResult` : Success.value → `KadreSessionHandle(session)` ; Failure.reason → `throws KadreError(reason)` (message = la traduction Swift du `KadreFailure.message` Kotlin — reproduire la sémantique du message, pas le texte exact). `policy` : switch profil → `KadrePolicies.Default/Realtime/Recording` (header l.500+ pour les accesseurs `KadrePolicies.shared.default` etc. — lire le header).
- [ ] **Step 2: `KadreSessionHandle`** — `state` : `session.state.value` mappé ; `observeState(observer)` : `@MainActor` — collecte `session.state` sur un scope propre (`CoroutineScope(SupervisorJob() + Dispatchers.Main)` possédé par le handle), émission IMMÉDIATE du snapshot courant à l'inscription, callbacks sérialisés (une file MainActor — les émissions consécutives s'exécutent dans l'ordre), retrait idempotent (`close()` deux fois = une fois), l'observation survit à `terminated` (dernier snapshot livré) mais plus rien n'émet après. `close()` (le handle) : ferme la session K + le scope d'observation ; idempotent.
- [ ] **Step 3: `awaitTermination`** — chaque appel : un `Job` waiter propre attaché au scope du handle ; la terminaison de session complète le waiter (résultat mappé `KadreSessionOutcome`) ; la CANCELLATION de la Task Swift annule UNIQUEMENT le waiter-job et lève `CancellationError` — la session continue (prouvé : après cancellation, `state` continue de progresser et requestStop fonctionne). Race : terminaison et cancellation concurrentes → la continuation reprise EXACTEMENT une fois (test en boucle 50 itérations avec barrières aléatoires).
- [ ] **Step 4: Tests consumer (XCTest)** — attach (factory Kotlin du consumer) → observeState reçoit `.starting` ou `.running` immédiatement → requestStop → observe `.terminated(.stopped(.hostRequested))` → awaitTermination (déjà terminé = retour direct) ; awaitTermination + Task.cancel → `CancellationError` ET la session vit ; ordre des callbacks MainActor prouvé (tableau d'arrivée) ; double retrait idempotent.
- [ ] **Step 5: Vert ×2 destinations. Commit** — `git commit -m "feat(uikit): deliver the swift session handle, observation and host attach"`.

---

### Task 5: `KadreHostViewController`

**Files:**
- Create: `kadre/platform/uikit/swift/Sources/KadreHost/KadreHostViewController.swift`
- Test: `kadre/consumers/swift/consumer-app/tests/Shared/KadreHostViewControllerTests.swift` (+ extension iOS pour la scène 2 si nécessaire)

**Interfaces:**
- Consumes: Task 4 (`KadreIosHost.attach`, `KadreSessionHandle`) ; `UIKitLifecycleSource`-pattern (notifications de disconnect — mais côté SWIFT : `NotificationCenter` `UIScene.didDisconnectNotification` filtré par `object`).
- Produces: `KadreHostViewController(applicationFactory:policy:)` — view UIView propre (aucun layout Kadre) ; attach EXACTEMENT une fois quand `view.window != nil && view.window?.windowScene != nil` (via `didMoveToWindow` + la scène vue) ; `kadreSession` privé-set ; `close()` explicite ; `deinit` ferme ; `UIScene.didDisconnectNotification` (object == la scène attachée) ferme ; re-add à la hiérarchie après close = PAS de re-attach.

- [ ] **Step 1: Tests d'abord** (le fixture crée une fenêtre+scène — pattern phase 1) : (a) controller ajouté à une fenêtre → `kadreSession` non-nil, attaché UNE fois (compteur) ; (b) retrait de superview puis re-add → PAS de second attach, session inchangée ; (c) `close()` → session Terminated, re-add → rien ; (d) post disconnect-notification (object = scène) → session fermée ; (e) deinit → session fermée (weak tracking) ; (f) la view du controller EST la surfaceView (via le fixture application qui capture primarySurface → isEqual) ; (g) un retrait temporaire NE ferme PAS la session (roadmap l.227).
- [ ] **Step 2: Implémentation** — `loadView` crée la view ; `didMoveToWindow` tente l'attach (guard window+scene, guard !attached) ; `NotificationCenter.addObserver(forName: UIScene.didDisconnectNotification, object: attachedScene)` → close ; `close()` idempotent (session.close()) ; `deinit` → close + removeObserver.
- [ ] **Step 3: Vert ×2 destinations. Commit** — `git commit -m "feat(uikit): deliver the kadre host view controller with single attach semantics"`.

---

### Task 6: Consumer SwiftUI + parcours complet (jalon A)

**Files:**
- Create: `kadre/consumers/swift/consumer-app/Sources/ConsumerRepresentable.swift` (le `UIViewControllerRepresentable` possédé par l'app — ~10 lignes autour de `KadreHostViewController`)
- Create: `kadre/consumers/swift/consumer-app/tests/Shared/KadreSwiftUIConsumerTests.swift`
- Modify: consumer-app (une scène SwiftUI qui utilise le representable — contrôlé par env var de lancement comme phase 1)

**Interfaces:**
- Consumes: Task 5 (`KadreHostViewController`).
- Produces: la preuve §9 SwiftUI — « wrapper `UIViewControllerRepresentable` possédant l'host controller, sans layout Kadre ».

- [ ] **Step 1: Le representable** — `struct KadreHostView: UIViewControllerRepresentable { let factory: KadreApplicationFactory; let policy: KadrePolicyProfile; func makeUIViewController -> KadreHostViewController; updateUIViewController { } }` — l'app possède le controller via le representable (make = own), `update` ne re-attache PAS.
- [ ] **Step 2: Le test** — scène SwiftUI (UIHostingController) hébergeant `KadreHostView(factory: ConsumerApplicationFactory(), policy: .default)` → la session attache (fixture), observe, requestStop → terminated ; une réévaluation de body (forcer `setNeedsUpdate` + re-render) NE déclenche PAS un nouvel attach (compteur). Nom figé : `uikit-swiftui-consumer-attaches-once` (iOS) — même test tvOS.
- [ ] **Step 3: Vert ×2 destinations. Commit** — `git commit -m "test(uikit): prove the swiftui consumer wrapper on both families"`.

---

### Task 7: Audit façade + packaging + gate

**Files:**
- Modify: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/UIKitSurfaceAccess.kt` (@HiddenFromObjC sur withUIKitView + KDoc mis à jour — la divergence F1 phase 2 est résolue par SUPPRESSION)
- Modify: `kadre/INTEROP-EXPORTS.md` §7 (amender la phrase finale : l'en-tête framework ne doit plus exporter les symboles surface-access — suppression effective ; la règle Kotlin-only vaut pour la façade ET l'en-tête)
- Modify: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreUikitSurfaceDriver.kt` + `KadreUikitProbe.kt` (KDoc : scaffolding driver, hors surface promise — VISIBLE volontairement, listé dans l'inventaire d'audit)
- Modify: `kadre/capabilities/uikit.md` (rows phase 3 : façade Swift, controller, lease export ; inventaire d'audit : cachés [withUIKitView] / visibles-scaffolding [probes] / contrat [attach, session, factory])
- Test: le header régénéré NE contient plus `withUIKitView` ; les tests phase 2 restent verts (la preuve passait par la sonde Kotlin) ; les drivers restent verts.

**Interfaces:**
- Consumes: phase 2 (KDoc divergence verbatim l.36–42) ; `@HiddenFromObjC` (`kotlin.native`, `@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)` — vérifié présent en 2.4.20).
- Produces: l'en-tête audité ; les rows capabilities ; le statut distribution documenté.

- [ ] **Step 1: @HiddenFromObjC sur `withUIKitView`** + KDoc (« Kotlin-only y compris dans l'en-tête framework — l'appel Swift passe par la façade KadreHost » si la façade l'expose ; sinon : Kotlin-only strict). Vérifier que la preuve phase 2 (sonde Kotlin) reste verte.
- [ ] **Step 2: INTEROP §7 amendment** (la phrase finale remplace « Aucun pointer/adresse n'apparaît dans Swift... » par la version : « Ces extensions sont Kotlin-only et masquées des exports Obj-C/Swift (`@HiddenFromObjC`) ; aucun pointer/adresse n'apparaît dans Swift, Java, TypeScript, diagnostics, equality ou snapshots communs. ») + inventory note dans capabilities.
- [ ] **Step 3: Gate** — les suites uikit complètes + driver ×2 familles + les consumers ×2 familles (Task 2-6 tests) : tout vert avec le header audité.
- [ ] **Step 4: Commit** — `git commit -m "feat(uikit): audit the framework header and hide the surface-access export"`.

---

### Task 8: Sweep du critère de sortie (jalon A)

**Files:** rapport SDD ; aucun produit obligatoire.

- [ ] **Step 1: Sweep à froid** (commandes séparées, longues en background) : git status propre ; runtime jvmTest ; natives runtime ×2 sims ; appkit ; gate uikit `all` ; consumers ×2 destinations (iOS+tvOS XCTest) — LES QUATRE parcours : attach, observe, stop, awaitTermination depuis les artifacts du consumer (XCFramework + KadreHost sources + factory Kotlin).
- [ ] **Step 2: Checklist jalon A** avec preuves : (1) hosts UIKit et SwiftUI externes iOS et tvOS attachent/observent/arrêtent/attendent depuis LEURS artifacts ; (2) race cancellation/terminaison : jamais deux reprises (boucle 50) ; (3) annuler une Task n'arrête pas la session ; (4) aucun cycle de rétention controller/session/observer après fermeture (weak tracking + deinit + profil instruments si disponible — sinon weak/strong assertions) ; (5) absence de duplication (le squelette Task 2) + identité de types (la factory du module Kotlin du consumer passe la façade).
- [ ] **Step 3: Rapport de sweep** + push (contrôleur).

---

## Séquencement et estimation

Tasks 1→2→3→4→5→6→7→8. T1 (xcframework) et T2 (squelette consumer + lien statique deux-frameworks) portent LE risque technique de la phase (duplication runtime, préfixes Obj-C) — front-loadés comme en phase 0. T3–T5 = la façade TDD dans le squelette. T6 = SwiftUI. T7 = audit/packaging. T8 = sweep. Budget attendu : ~40–50M tokens (la façade est nouvelle, le consumer Kotlin est nouveau, et les preuves sont des apps Xcode complètes).
