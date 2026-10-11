# iOS/tvOS Phase 2 — Surface, géométrie, redraw et accès UIKit — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre la surface UIKit observable et utilisable par un renderer du consumer — métriques dynamiques (taille logique/physique, scale, safe-area, visibilité, apparance), `requestRedraw()` coalescé sans boucle permanente, `HostSurface.apply` champ par champ sans faux succès, et l'opt-in `withUIKitView` — prouvé par le driver sur les deux familles, contrats BCK-015/BCK-016 activés.

**Architecture:** L'architecture port-stimulus de la phase 1 est conservée (PAS de classe HostSurface à la Android) : `UIKitWindowCommandPort` évolue — les observations alimentent `stimulusSink` (`SurfaceStimulus.MetricsChanged/VisibilityChanged/AppearanceChanged`), le redraw RETIENT le ticket du manager jusqu'au prochain tick du main queue (coalescing sans CADisplayLink), `apply` passe à l'échelle fermée par champ, et `publicSurfaceCapabilities` passe à `true`. Les observations d'un `UIView` fourni se font SANS sous-classe : KVO (`bounds`, `window.hidden`) + re-lectures sur notifications de scène (miroir Android : « une observation manquante n'invente jamais ») + `registerForTraitChanges` sur la scène en iOS/tvOS 17+ (repli re-lecture en 16). Le lease `withUIKitView` route par un registre technique par `SurfaceId` (la `RuntimeWindowSurface` est runtime-owned — miroir Android impossible directement ; roadmap §3.1 l.109 : « un registre technique de propriétaires peut vérifier l'exclusivité et router des callbacks identifiés »).

**Tech Stack:** Kotlin 2.4.20 (uikitMain + friend-modules), `NSKeyValueObserving` + `NSNotificationCenter` + `registerForTraitChanges` (17+, présent dans les klibs ios/tvos 2.4.20, gated runtime), Xcode 27 / simulateurs iPad + Apple TV, pipeline evidence BCK-012…014 (gabarit).

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` §5 Phase 2 (checklist et critère de sortie l.200–214) ; `kadre/INTEROP-EXPORTS.md` §7 l.367–406 (règles du callback natif, verbatim) ; `kadre/OPERATION-CONTRACTS.md` §7.1 l.187/190 (outcomes fermés ; « le chemin tvOS est livré avec la phase 2 UIKit ») ; `kadre/BACKEND-CAPABILITIES.md` l.79 (row `platformAccess` : UIKit = `withUIKitView`).

## Décisions fermées par ce plan

| Décision | Résultat fermé ici |
|---|---|
| Architecture surface | Port-stimulus conservé : `UIKitWindowCommandPort` reste le seul point d'entrée natif ; les stimuli `SurfaceStimulus.*` alimentent `manager.acceptSurfaceStimulus` (publication state-avant-event = runtime). Aucune duplication du reducer. |
| Redraw | Le port RETIENT le `SurfaceRedrawGeneration` reçu et le consomme au prochain tick du main queue (`DispatchQueue.main.async`, un seul drain en vol) — coalescing par ticket du manager (requêtes en rafale pendant un ticket en vol = `Success` sans nouveau ticket). Pas de CADisplayLink, pas de 60 Hz, drain annulé à `beginClose` (jamais consommé après fermeture). |
| Métriques dynamiques | KVO sur `view.bounds` (conformité KVO non documentée par Apple — prouvée sur simulateur en Task 4 ; repli : re-lectures sur notifications de scène) + re-lecture initiale postée et re-lectures sur foreground/activate (miroir Android : lecture initiale `view.post`, champs `null` ne publiant rien). Génération taggée + `supersedeGeneration` au teardown (callbacks tardifs = `Stale`). |
| Apparence (theme/contrast) | iOS/tvOS **17+** : `registerForTraitChanges` sur la `UIWindowScene` (pas de sous-classe ; gated runtime `isOperatingSystemAtLeastVersion`) ; **16** : re-lecture sur les notifications de scène uniquement. `contrast` n'est JAMAIS inféré du theme (`SurfaceContrast.Unknown` conservé). Plancher déclaré reste iOS/tvOS 16.0. |
| Visibilité / occlusion / focus | KVO `window.isHidden` + re-lectures scène ; `occlusion` reste `Visible` (jamais publié dynamiquement — honest) ; `focus` reste `Unfocused` (input = phase 5) — focus-dans-la-surface ≠ activation de scène. |
| `apply` | Échelle fermée : `Closed(Surface)` → `StaleRevision` → `InvalidRequest(field)` pour `Clear` → `admitField` par champ → guards `requireRefused` → `Applied(current)` si tout `Unchanged`, sinon `PartiallyApplied(current, rejected)`. Les 4 champs (`cursor`, `pointerCapture`, `hitTesting`, `inputDefaultBehavior`) sont `Unsupported(UpdateSurface)` (miroir Android). JAMAIS `TemporarilyUnavailable` pour l'update entier. |
| `withUIKitView` | `UIKitSurfaceAccess.kt` : `@KadrePlatformApi @DelicateKadreApi public suspend fun <R> HostSurface.withUIKitView(block: (UIView) -> R): KadreResult<R>` — routage par `UIKitViewLeaseRegistry` (par `SurfaceId`, enregistré à la création du runtime, retiré au close). Admission d'abord (`Closed(Surface)` gagne sur la réentrance), single-flight `TemporarilyUnavailable(retryable = true)`, view re-lue à chaque lease (`null` → `Closed(Surface)`), block démarré sous `NonCancellable` sur le main (inline ou `Dispatchers.Main.immediate`), exceptions propagées telles quelles. |
| Capacités annoncées | `publicSurfaceCapabilities = true` ; `uikitSurfaceCapabilities()` = les 4 champs update `Unsupported(UpdateSurface)`, interactions `Unsupported`, **`platformAccess = Capability.Supported(Unit, FeatureAvailability.Available)`** (publiée quand observation+lease installés, retirée avant l'état détaché au close — miroir Android `observationInstalled()`). |
| Contrats | `BCK-015` « UIKit surface observations, redraw and native access » (8 scénarios partagés + 3 sentinelles, les DEUX targets) + `BCK-016` « UIKit iPadOS surface rotation » (rotation + sentinelle, iOS-only — la règle de couverture exige chaque scenario sur chaque requiredTarget). |
| Scale change (écran externe) | Manual charter (aucun stimulus programmeable fiable ; miroir Android `android-metrics-generation-stale` precedent) — documenté dans capabilities/uikit.md. |

## Global Constraints

- Le reducer de surface reste l'autorité (runtime) : UIKit ne publie QUE des `SurfaceStimulus` ; publication state-avant-event = `RuntimeWindowSurface`.
- Pas de sous-classe publique ni privée des types UIKit du host ; observateurs = objets Kadre propres (`NSObject` observer, listeners additifs, retirés au teardown — jamais sur la fenêtre elle-même pour les ressources Kadre, cf. `UIKitWindowPeerOwner.close()`).
- Champs d'observation manquants n'inventent jamais de valeur (`Unknown` reste `Unknown`, `contrast` jamais inféré) ; `SurfaceOcclusion`/`SurfaceFocus` non observés en phase 2 restent à leur valeur initiale.
- Callback natif (`withUIKitView`) : non réentrant pour la même surface, non suspendu, validity bornée au callback, cancellation avant = pas d'appel, après début = achève `NonCancellable`, résultat seulement au waiter actif, exceptions propagées telles quelles (INTEROP §7 l.394 verbatim). Kotlin-only : aucun type SDK dans Swift/diagnostics/snapshots.
- Aucune requête redraw ni invocation native après révocation (critère de sortie) : drain annulé à `beginClose`, registre de lease vidé au close, `platformAccess` retirée avant l'état détaché.
- Preuves séparées par famille ; activation `planned → active` dans le commit des scénarios+preuves+sentinelles ; gate = script + CI (hors `:kadre:check`) ; chaque Gradle < ~9 min, une commande par appel Bash.
- Identity UIKit = `isEqual` ; `SurfaceId.description` est rédigé (comparaisons par value equals/isEqual).

## Review Focus

1. **Ticket de redraw perdu ou double-consommé** — le drain doit consommer EXACTEMENT la génération en pending, une seule fois, jamais après `beginClose` ; un ticket non consommé bloque TOUT redraw futur (le manager ne ré-émet pas). Test : rafale de requêtes → un seul drain avec la DERNIÈRE génération ; close pendant pending → plus jamais de drain.
2. **KVO sur `bounds` non conforme** — la conformité KVO d'`UIView.bounds` n'est pas documentée Apple ; si elle ne fire pas, les métriques ne bougent plus jamais silencieusement. Test : le driver redimensionne la vue et le test ÉCHOUE si aucune `MetricsChanged` n'arrive (jamais de valeur figée déclarée verte) ; le repli notification-driven est testé séparément.
3. **Callbacks d'observation tardifs après teardown** — KVO/notifications non retirés = stimuli vers une surface détachée ou crash. Test : terminer la session, changer bounds/theme, rien ne publie et rien ne crashe ; sentinelle dédiée (génération stale).
4. **Lease qui retient la vue ou double-entrée** — single-flight réellement exclusif (deux leases concurrentes = la seconde `TemporarilyUnavailable` AVANT invocation), view non retenue après le callback (source effacée au close), block démarré achève `NonCancellable`. Test : lease pendant close concurrent → `Closed(Surface)`, jamais d'invocation.
5. **Faux succès de `apply`** — un champ `Clear` doit donner `InvalidRequest(field)`, un champ changé sur capability `Unsupported` doit finir dans `rejected` (jamais `Applied`), et l'outcome entier n'est JAMAIS `TemporarilyUnavailable`. Test : kernel pur sur les 4 champs × {Unchanged, Set, Clear}.

---

### Task 1: Redraw coalescé + échelle `apply` + capacités dans le port

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitRedrawScheduler.kt`
- Modify: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowCommandPort.kt` (requestRedraw pending, apply ladder, `uikitSurfaceCapabilities()`)
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitRedrawSchedulerTest.kt`, `UIKitApplyLadderTest.kt`

**Interfaces:**
- Consumes: `SurfaceRedrawCommand(surfaceId, generation: SurfaceRedrawGeneration)` ; `SurfaceStimulus.RedrawConsumed(surfaceId, generation)` ; `SurfaceUpdateCommand`/`SurfaceUpdateCommandOutcome`/`SurfaceFieldOutcome` (runtime commonMain, désormais accessible) ; `SurfaceAdmission.kt` helpers (`admitField`, `normaliseFieldFailure`, `unsupportedSurfaceCapabilities()`).
- Produces (consommés par Tasks 3–4) : `internal class UIKitRedrawScheduler(onDrain: (SurfaceRedrawGeneration) -> Unit, schedule: (()->Unit) -> Unit)` — `fun request(generation: SurfaceRedrawGeneration)`, `fun cancel()` ; port : `requestRedraw` retient le ticket, `apply` implémente l'échelle fermée ; `internal fun uikitSurfaceCapabilities(): SurfaceCapabilities`.

- [ ] **Step 1: Le scheduler de drain (TDD, injectable)**

Test d'abord (`UIKitRedrawSchedulerTest.kt`) — scheduler pur, horloge manuelle :

```kotlin
package org.graphiks.kadre.internal.uikit

import kotlin.test.*
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration

class UIKitRedrawSchedulerTest {
    private class ManualSchedule : (() -> Unit) -> Unit {
        val pending = mutableListOf<() -> Unit>()
        override fun invoke(p1: () -> Unit) { pending.add(p1) }
        fun tick() { val work = pending.toList(); pending.clear(); work.forEach { it() } }
    }

    @Test
    fun burstOfRequestsDrainsOnceWithTheLastGeneration() {
        val schedule = ManualSchedule()
        val drained = mutableListOf<SurfaceRedrawGeneration>()
        val scheduler = UIKitRedrawScheduler(onDrain = { drained.add(it) }, schedule = schedule)
        scheduler.request(SurfaceRedrawGeneration(1))
        scheduler.request(SurfaceRedrawGeneration(2))
        scheduler.request(SurfaceRedrawGeneration(3))
        assertEquals(1, schedule.pending.size)          // coalescé : un seul drain programmé
        schedule.tick()
        assertEquals(listOf(SurfaceRedrawGeneration(3)), drained)  // la DERNIÈRE génération
        assertEquals(0, schedule.pending.size)
    }

    @Test
    fun drainRearmsForRequestsAfterIt() {
        val schedule = ManualSchedule()
        val drained = mutableListOf<SurfaceRedrawGeneration>()
        val scheduler = UIKitRedrawScheduler({ drained.add(it) }, schedule)
        scheduler.request(SurfaceRedrawGeneration(1))
        schedule.tick()
        scheduler.request(SurfaceRedrawGeneration(2))
        assertEquals(1, schedule.pending.size)
        schedule.tick()
        assertEquals(listOf(SurfaceRedrawGeneration(1), SurfaceRedrawGeneration(2)), drained)
    }

    @Test
    fun cancelStopsEverythingForever() {
        val schedule = ManualSchedule()
        val drained = mutableListOf<SurfaceRedrawGeneration>()
        val scheduler = UIKitRedrawScheduler({ drained.add(it) }, schedule)
        scheduler.request(SurfaceRedrawGeneration(1))
        scheduler.cancel()
        schedule.tick()
        assertTrue(drained.isEmpty())
        scheduler.request(SurfaceRedrawGeneration(2))
        schedule.tick()
        assertTrue(drained.isEmpty())
    }
}
```

Exécuter : `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test` → FAIL (type absent). Implémenter :

```kotlin
package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration

/**
 * Coalescing du redraw : au plus UN drain programmé ; les requêtes en rafale rejoignent le
 * drain en vol et seule la DERNIÈRE génération est consommée. `cancel()` (fermeture du port)
 * arrête tout pour toujours — aucun drain après fermeture. Pas de boucle permanente : le
 * scheduling est injecté (production : un `DispatchQueue.main.async` par drain).
 */
internal class UIKitRedrawScheduler(
    private val onDrain: (SurfaceRedrawGeneration) -> Unit,
    private val schedule: (() -> Unit) -> Unit,
) {
    private val lock = RuntimeLock()
    private var pending: SurfaceRedrawGeneration? = null
    private var scheduled = false
    private var cancelled = false

    fun request(generation: SurfaceRedrawGeneration) {
        lock.withLock {
            if (cancelled) return
            pending = generation
            if (scheduled) return
            scheduled = true
        }
        schedule { drainScheduled() }
    }

    fun cancel() {
        lock.withLock {
            cancelled = true
            pending = null
            scheduled = false
        }
    }

    private fun drainScheduled() {
        val generation = lock.withLock {
            if (cancelled) return
            scheduled = false
            pending.also { pending = null }
        }
        if (generation != null) onDrain(generation)
    }
}
```

(Le `RuntimeLock` est déjà importable dans uikitMain via friend-modules — phase 1.)

- [ ] **Step 2: L'échelle `apply` (TDD, kernel pur)**

Test `UIKitApplyLadderTest.kt` — l'échelle par champ sur les 4 propriétés (miroir Android, kernel pur sans UIKit) :

```kotlin
package org.graphiks.kadre.internal.uikit

import kotlin.test.*
import org.graphiks.kadre.failure.*
import org.graphiks.kadre.surface.*

class UIKitApplyLadderTest {
    // Le kernel testé : fun uikitSurfaceApplyOutcome(
    //     current: SurfaceState, update: SurfaceUpdate, capabilities: SurfaceCapabilities
    // ): SurfaceUpdateOutcome  — Closed/StaleRevision restent dans le port (état vivant).

    @Test
    fun allUnchangedAppliesTheCurrentState() { /* update tout Unchanged → Applied(current) */ }

    @Test
    fun setFieldsAreRejectedPerFieldWithUnsupportedUpdateSurface() {
        // update.cursor = Set(System(Default)), autres Unchanged
        // → PartiallyApplied(current, rejected == [RejectedSurfaceField(Cursor, Unsupported(UpdateSurface))])
    }

    @Test
    fun clearIsInvalidRequestNotRejected() { /* update.hitTesting = Clear → le port répond InvalidRequest("hitTesting") — kernel lève/retourne la failure fermée, assertion sur le field name */ }

    @Test
    fun mixedUnchangedAndSetGivesPartiallyAppliedWithOnlyTheSetFields() { /* 2 Set + 2 Unchanged → PartiallyApplied, rejected.size == 2, ordre SurfaceProperty */ }
}
```

Écrire les 4 tests RÉELS (pas les commentaires — avec les constructeurs réels de `SurfaceUpdate`/`PropertyChange` lus dans `kadre/foundation/.../surface/Surface.kt`), les voir échouer (fonction absente), puis implémenter `uikitSurfaceApplyOutcome(current, update, capabilities)` dans `UIKitWindowCommandPort.kt` : `admitField` par champ → si un `Clear` admis arrive → `InvalidRequest(fieldName)` (le port le convertit) → `requireRefused` guards → `Applied(current)` si rejected vide sinon `PartiallyApplied(current, rejected.sorté par inSurfaceUpdateOrder si l'helper existe, sinon ordre de déclaration)`. Le `apply(command)` du port l'enveloppe : `Closed(Surface)` si le peer est absent, sinon `SurfaceUpdateCommandOutcome` avec les quatre `SurfaceFieldOutcome` (`Unchanged`/`Rejected`) — miroir `UnsupportedSurfaceCommandPort` l.87–100 pour la forme des quatre champs.

- [ ] **Step 3: requestRedraw retient le ticket + wiring du scheduler**

Dans `UIKitWindowCommandPort` : construire le `UIKitRedrawScheduler(onDrain = { g -> stimulusSink(SurfaceStimulus.RedrawConsumed(peerSurfaceId, g)) }, schedule = { block -> dispatch_async(dispatch_get_main_queue(), block) })` (imports `platform.darwin.dispatch_async/dispatch_get_main_queue`) ; `requestRedraw(command)`: admission (peer présent, `!closed`) → `scheduler.request(command.generation)` → `Success(Unit)` ; `beginClose` → `scheduler.cancel()` AVANT `closed = true` (aucun drain après fermeture). Retirer l'auto-consommation de phase 1 (l.185–192). Le peer `surfaceId` est celui du peer primaire (le registre `bySurface` le porte — premier/unique peer en phase 2).

- [ ] **Step 4: Capacités annoncées**

Nouvelle fonction `internal fun uikitSurfaceCapabilities(): SurfaceCapabilities` dans le port ou un fichier `UIKitSurfaceCapabilities.kt` : les 4 champs update `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))`, `handlerInteractions`/`armedInteractions` `Unsupported`, `platformAccess = Capability.Supported(Unit, FeatureAvailability.Available)`. Dans `UIKitWindowRuntime` : `publicSurfaceCapabilities = true`, `enabledSurfaceCapabilities = uikitSurfaceCapabilities()`. Vérifier la contrainte runtime : `publicSurfaceCapabilities=true` exige que l'initial snapshot existe (il existe — commit à l'open, RuntimeWindowManager l.1461).

- [ ] **Step 5: Tests natifs + compiles device + vert**

```bash
./gradlew :kadre:platform:uikit:iosSimulatorArm64Test :kadre:platform:uikit:tvosSimulatorArm64Test
./gradlew :kadre:platform:uikit:compileKotlinIosArm64 :kadre:platform:uikit:compileKotlinTvosArm64
```

- [ ] **Step 6: Commit** — `git add kadre/platform/uikit && git commit -m "feat(uikit): coalesce redraw, declare surface capabilities and close the apply ladder"`

---

### Task 2: Noyau d'observation métrique (kernel pur)

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitSurfaceObservation.kt` (data class + kernel `uikitSurfaceApply`)
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitSurfaceObservationTest.kt`

**Interfaces:**
- Consumes: `SurfaceMetrics(logicalSize, physicalSize, scaleFactor, safeAreaInsets)` (invariant `physical == logical.toPhysical(scale)`), `LogicalSize`, `LogicalInsets` (foundation Geometry).
- Produces: `internal data class UIKitSurfaceObservation(logicalWidth: Double?, logicalHeight: Double?, scaleFactor: Double?, safeAreaTop/Right/Bottom/Left: Double?, theme: SurfaceTheme?, generation: Long)` + `internal object UIKitSurfaceObservationKernel { fun apply(current: SurfaceMetrics, appearance: SurfaceAppearance, observation: UIKitSurfaceObservation): UIKitSurfaceReduction }` avec `UIKitSurfaceReduction { Ignored; Stale; Metrics(metrics: SurfaceMetrics, appearance: SurfaceAppearance) }` — miroir direct de `androidSurfaceApply`/`AndroidSurfaceMetrics.kt:78–193` (lire ce fichier AVANT, c'est le template : division IEEE pour la logique, physique observée telle quelle, champs `null` = inchangés, dédup `Ignored` si égal, `Stale` si génération périmée, `theme == null` → apparition inchangée).

- [ ] **Step 1: Lire `kadre/platform/android/src/androidMain/.../AndroidSurfaceMetrics.kt` intégralement** (template verbatim) et citer les correspondances dans le rapport.
- [ ] **Step 2: Écrire les tests kernel d'abord** (6 tests : resize propagé, scale propagé change physical, safe-area propagée, thème seul → apparition changée contrast inchangé, champs null → inchangés, génération stale → `Stale`, valeurs non finies/<=0 → `Ignored`).
- [ ] **Step 3: Implémenter le kernel** (pur, zéro UIKit) jusqu'au vert : `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test`.
- [ ] **Step 4: Commit** — `git commit -m "feat(uikit): add the pure surface observation kernel"`.

---

### Task 3: Le binding d'observation (KVO + notifications + 17+) et l'apparence

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitSurfaceObservationBinding.kt`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitAppearanceObserver.kt`
- Modify: `UIKitWindowCommandPort.kt` (le peer owner installe/retire le binding ; re-lectures sur notifications scène via `UIKitLifecycleSource`-style observers additionnels)
- Test: `uikitTest` — kernel déjà testé ; le binding est prouvé par driver (Task 5).

**Interfaces:**
- Consumes: Task 2 kernel ; `stimulusSink` ; `UIKitLifecycleSource` (phase 1 — les observers de scène existent ; ajouter les noms de notification de re-lecture sur le MÊME mécanisme) ; `SurfaceRedrawGeneration`-style génération interne.
- Produces: `internal class UIKitSurfaceObservationBinding(view: UIView, scene: UIWindowScene?, window: UIWindow, publish: (UIKitSurfaceObservation) -> Unit)` — `install()`, `teardown()` ; `internal class UIKitAppearanceObserver(scene: UIWindowScene, onTheme: (SurfaceTheme) -> Unit)` — install/teardown avec gate 17+ (`NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(NSOperatingSystemVersion(majorVersion = 17, 0, 0))`).

- [ ] **Step 1: Le binding KVO** — observer NSObject privé (`class BoundsObserver : NSObject(), NSKeyValueObservingProtocol` override `observeValueForKeyPath(ofObject:change:context:)`) enregistré sur `view.addObserver(observer, forKeyPath = "bounds", options = NSKeyValueObservingOptionNew, context = null)` ; chaque fire → re-lecture COMPLÈTE (bounds + displayScale + safeAreaInsets via `useContents`, theme via traitCollection) → `publish(observation)` avec la génération courante. Plus : observers de re-lecture sur `UISceneWillEnterForegroundNotification`/`UISceneDidActivateNotification` (même mécanisme `addObserverForName(name, scene, mainQueue)` que phase 1) et sur `UIScreen.modeChangeNotification` (tvOS + iOS). `install()` fait une première lecture postée. `teardown()` : removeObserver + retirer les tokens + `supersedeGeneration()`.
- [ ] **Step 2: L'appearance observer** — 17+ : `scene.registerForTraitChanges(listOf(UITraitUserInterfaceStyle), handler)` (la forme K/N EXACTE du handler `{ traitEnvironment, previousCollection -> ... }` est à prouver au premier compile — si la conversion de bloc échoue, repli documenté : re-lecture sur notifications uniquement + rapport) ; < 17 : re-lecture sur les mêmes notifications que le binding (le theme est relu à chaque observation du binding — l'observer 17+ est un push ADDITIONNEL).
- [ ] **Step 3: Wiring dans le port** — `UIKitWindowPeerOwner.close()` ne change PAS (toujours neutre côté host) ; c'est le PORT qui installe le binding à `requestOpen` (après commit) et le retire à `requestOpenedClose`/`beginClose`. La publication route `kernel.apply(...)` → `MetricsChanged`/`AppearanceChanged` via `stimulusSink`.
- [ ] **Step 4: Vérifier** — compiles 4 targets + tests existants verts. Preuve runtime = Task 5.
- [ ] **Step 5: Commit** — `git commit -m "feat(uikit): observe host view metrics and appearance without subclassing"`.

---

### Task 4: `withUIKitView` — registre technique, port de lease et façade

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitViewLeaseRegistry.kt`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/UIKitSurfaceAccess.kt`
- Modify: `UIKitWindowCommandPort.kt` (implémente le port de lease) + `UIKitWindowRuntime.kt` (register/unregister au cycle de vie)
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitViewLeaseRegistryTest.kt`

**Interfaces:**
- Consumes: INTEROP §7 l.394 (règles verbatim) ; `KadreOperation.PlatformSurfaceAccess` ; pattern `AndroidHostSurface.lease` (l.305–333, template).
- Produces: `internal interface UIKitViewLeasePort { suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R> }` ; `internal object UIKitViewLeaseRegistry { fun register(surfaceId: SurfaceId, port: UIKitViewLeasePort); fun unregister(surfaceId: SurfaceId); fun portFor(surfaceId: SurfaceId): UIKitViewLeasePort? }` (RuntimeLock, registre retiré AVANT l'état détaché) ; façade :

```kotlin
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> HostSurface.withUIKitView(block: (UIView) -> R): KadreResult<R> {
    val port = UIKitViewLeaseRegistry.portFor(id)
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
    return port.lease { view -> block(view as UIView) }
}
```

- [ ] **Step 1: Tests registre d'abord** (TDD — échec : type absent) :

```kotlin
package org.graphiks.kadre.internal.uikit

import kotlin.test.*
import org.graphiks.kadre.surface.SurfaceId

class UIKitViewLeaseRegistryTest {
    @Test
    fun registeredPortIsResolvableAndUnregisterRemovesIt() {
        val registry = UIKitViewLeaseRegistry()
        val id = SurfaceId(/* constructeur réel — même forme que Task 2 phase 1 */)
        val port = object : UIKitViewLeasePort {
            override suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R> = KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
        }
        assertNull(registry.portFor(id))
        registry.register(id, port)
        assertSame(port, registry.portFor(id))
        registry.unregister(id)
        assertNull(registry.portFor(id))
        registry.unregister(id) // idempotent
    }
}
```

(The registry must be instantiable — an `object` singleton violates testability : make it `internal class UIKitViewLeaseRegistry` with a `shared` companion for production wiring, mirroring `UIKitSceneRegistry`.)
- [ ] **Step 2: Le port de lease dans `UIKitWindowCommandPort`** — ladder EXACT (template Android l.305–333) : 1) admission sous `RuntimeLock` (peer absent/`closed` → `Closed(Surface)` — GAGNE sur la réentrance) ; 2) `leaseHeld` → `TemporarilyUnavailable(retryable = true)`, block jamais invoqué ; 3) `currentCoroutineContext().ensureActive()` sous verrou avant `leaseHeld = true` ; 4) re-lecture de la vue (`peer.view`) hors verrou, `null` → `Closed(Surface)` ; 5) dispatch main inline-si-main sinon `Dispatchers.Main.immediate` : `KadreResult.Success(withContext(NonCancellable) { block(view) })` ; 6) exceptions propagées telles quelles ; `finally { lock.withLock { leaseHeld = false } }`. `close()` → `unregister(surfaceId)` + vue source effacée.
- [ ] **Step 3: La façade** dans `UIKitSurfaceAccess.kt` (code ci-dessus) + KDoc reprenant INTEROP l.394.
- [ ] **Step 4: Wiring cycle de vie** — `UIKitWindowRuntime` register après commit du peer / unregister dans `close()` avant la publication détachée (ordre Android l.415–416).
- [ ] **Step 5: Vérifier** — compiles + tests natifs verts (le comportement runtime complet est prouvé en Task 5).
- [ ] **Step 6: Commit** — `git commit -m "feat(uikit): deliver the withUIKitView lease through a per-surface registry"`.

---

### Task 5: Driver — stimulation réelle (resize, rotation, thème, reprise) et lease

**Files:**
- Modify: `kadre/contracts/driver/uikit/apps/Ios/Info.plist` (ajouter `UISupportedInterfaceOrientations` = les 4 orientations, via project.yml `properties`)
- Modify: `kadre/contracts/driver/uikit/tests/Shared/` + `tests/Ios/` (nouvelle classe `KadreUikitSurfaceTests`, noms FIGÉS)
- Modify: `kadre/contracts/driver/uikit/KadreUikitDriver.xcodeproj/` (xcodegen regénéré, commité)

**Interfaces:**
- Consumes: Tasks 1–4 ; le fixture par scène de phase 1 ; `KadreIos.shared.attach` ; `withUIKitView` (exporté : `withUIKitView(block:)` sur `KadreUikitHostSurface`? — LIRE le header généré pour le nom exporté réel de l'extension suspend : les extensions sur HostSurface s'exportent comme méthodes `HostSurfaceExtension`? L'implémenteur lit `KadreUikit.h` et adapte l'appel Swift ; si l'extension n'est pas appelable depuis Swift, le test passe par l'application Kotlin `DriverKadreApplication` qui capture les observations dans le fixture (patron phase 1) — documenter la voie retenue).
- Produces: les noms de scénarios FIGÉS pour Task 6.

- [ ] **Step 1: Stimulation resize (réel, les deux familles)** — le test change `observedView.frame` (via `setFrame` + `layoutIfNeeded`) → la surface doit publier `MetricsChanged` (lu via le fixture capturé par `DriverKadreApplication` : `scope.primarySurface.value.state.value.logicalSize` — le fixture phase 1 capture déjà state ; étendre à un event flow drainé ou un polling borné sur state). Noms : `uikit-surface-metrics-match-view` (assertion initiale : logical == bounds Swift, physical == logical × scale, safeArea == insets Swift) et `uikit-surface-resize-propagates`.
- [ ] **Step 2: Rotation (iOS/iPad only)** — Info.plist orientations + `windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscapeRight))` depuis le test ; attendre (borné) le changement de bounds → metrics propagées. Nom : `uikit-surface-rotation-propagates`. Si l'API refuse (orientation lock système), capturer l'erreur exacte et basculer sur le resize-avec-changement-d'aspect (frame 240×320 ↔ 320×240) documenté comme « stimulus de rotation dégradé » — JAMAIS de faux vert rotation.
- [ ] **Step 3: Thème (les deux familles)** — `window.overrideUserInterfaceStyle = .dark` → `SurfaceAppearance(theme = Dark)` publié (via registration 17+ ou re-lecture) ; puis `.light`. Nom : `uikit-surface-theme-propagates`.
- [ ] **Step 4: Redraw (les deux familles)** — via `DriverKadreApplication` : appeler `scope.primarySurface.value.requestRedraw()` ×5 en rafale depuis un test qui fait drainer ; compter les `RedrawRequested` events sur `surface.events` (fixture drain) → EXACTEMENT 1 par tick (coalesced) ; après `close()` de la session → `requestRedraw()` retourne l'échec fermé et aucun event. Noms : `uikit-surface-redraw-coalesced`, `uikit-surface-redraw-after-close`.
- [ ] **Step 5: apply + lease (les deux familles)** — via l'application : `surface.apply(update)` avec cursor Set → `PartiallyApplied` avec 1 rejected `Unsupported(UpdateSurface)` ; avec tout Unchanged → `Applied`. Lease : `withUIKitView { view -> ... }` retourne Success et la vue EST `surfaceView` (`isEqual`), second lease concurrent → `TemporarilyUnavailable` ; après terminaison → `Closed(Surface)` / `Unsupported(PlatformSurfaceAccess)` (registre vidé). Noms : `uikit-surface-apply-fields-refused`, `uikit-lease-boundaries`.
- [ ] **Step 6: Reprise tvOS+iOS** — background (notification réelle, pattern phase 1) puis foreground → les metrics sont RE-LUES (modifier les bounds pendant le background n'est pas faisable ; la re-lecture publie l'état courant — assertion : state toujours cohérent avec les bounds après reprise, et le chemin de re-lecture est couvert par le compteur d'observations du fixture qui s'incrémente). Nom : `uikit-surface-reprise-rereads`. Rotation scenario iOS-only : `uikit-surface-rotation-propagates` (extension tests/Ios). **Changement de configuration d'affichage tvOS** (`UIScreen.modeChangeNotification`, câblé en Task 3) : aucun stimulus programmeable sur simulateur (le mode change exige une config d'affichage externe) — observation câblée mais preuve réelle = manual charter (capabilities/uikit.md, même statut que le scale change) ; NE PAS écrire de test factice.
- [ ] **Step 7: Exécuter les deux familles** (link frameworks, xcodebuild iPad + Apple TV) — `** TEST SUCCEEDED **` ×2. Toute valeur figée déclarée verte sans stimulus réel est un échec de tâche (Review Focus 2).
- [ ] **Step 8: Commit** — `git commit -m "test(uikit): prove dynamic surface observations, redraw and the native lease on both families"`.

---

### Task 6: Contrats BCK-015 + BCK-016 — registre, evidence, sentinelles

**Files:**
- Modify: `kadre/contracts/registry/contracts.tsv`, `kadre/contracts/driver/uikit/contracts/evidence.tsv`, `kadre/contracts/validator/build.gradle.kts` (uikitContractIds += BCK-015, BCK-016), `scripts/test-uikit-simulator.sh` (asserts +2 JSONs), `kadre/capabilities/uikit.md` (+3 rows : observations/redraw/apply, lease+platformAccess, rotation iPadOS ; état absent actualisé : IME/drop/displays/devices/capture restent)
- Test: mutations de sentinelles (sorties citées, reverts avant commit)

**Interfaces:**
- Consumes: Task 5 (noms figés) ; pipeline BCK-012…014.
- Produces: `generateUikitBCK015*`/`BCK016*` + validates (mécanique via la liste) ; asserts script.

- [ ] **Step 1: Registry rows** (vocabulaire voisin BCK-013/014 ; TAB réels) :

```
BCK-015	active	DESIGN.md#15.2	UIKit surface observations, redraw and native access	stale or invented metrics, zombie redraw drain, leaked view lease, fabricated apply success	O3	uikit-surface-metrics-match-view,uikit-surface-resize-propagates,uikit-surface-theme-propagates,uikit-surface-redraw-coalesced,uikit-surface-redraw-after-close,uikit-surface-apply-fields-refused,uikit-lease-boundaries,uikit-surface-reprise-rereads	iosSimulatorArm64,tvosSimulatorArm64	-	uikit-metrics-stale-generation,uikit-redraw-single-per-tick,uikit-lease-closed-after-terminal	-
BCK-016	active	DESIGN.md#15.2	UIKit iPadOS surface rotation	rotation stimulus faked or stale metrics after geometry change	O3	uikit-surface-rotation-propagates	iosSimulatorArm64	-	uikit-rotation-metrics-faithful	-
```

- [ ] **Step 2: evidence.tsv** — lire les XML produits (classnames/names exacts) ; rows partagées ×2 targets, rotation ×1 (iOS). 
- [ ] **Step 3: Validator + script asserts** (+2 ids, 9 JSONs au total : BCK-012/013/015/WIN-009 ×2, BCK-014/016 ×1).
- [ ] **Step 4: Gate vert + 5 preuves can-fail citées/revertées** : uikit-metrics-stale-generation (mutation = binding publie sans vérifier la génération → test resize échoue seul) ; uikit-redraw-single-per-tick (mutation = drain ne dé-arme pas pending → rafale = plusieurs drains, test coalesced échoue seul) ; uikit-lease-closed-after-terminal (mutation = unregister retiré du close → lease post-terminaison n'échoue pas, test lease-boundaries échoue seul) ; uikit-rotation-metrics-faithful (mutation = metrics lues depuis un cache au lieu de la vue → rotation échoue seul) ; mapping-row removal + testcase rename → rejets spécifiques.
- [ ] **Step 5: capabilities/uikit.md + UN commit d'activation** — `git commit -m "feat(uikit): activate BCK-015 and BCK-016 with both-families evidence"`.

---

### Task 7: Docs closure

**Files:**
- Modify: `kadre/DESIGN.md` §15.2 (bullet : surface dynamique + redraw + `withUIKitView` livrés phase 2), `kadre/BACKEND-CAPABILITIES.md` §6.2 (§ livré phase 2 : observations, redraw coalescé, apply refusé par champ, `withUIKitView`), `kadre/PUBLIC-API-CATALOG.md` l.30 (row : `withUIKitView` réelle), `kadre/INTEROP-EXPORTS.md` (si un statut « livré » y figure — sinon rien)
- Test: grep cohérence.

- [ ] **Step 1: Les 3–4 éditions chirurgicales** (ancres relues ; phase 3/4 restent au futur).
- [ ] **Step 2: Commit** — `git commit -m "docs: close the phase 2 UIKit surface perimeter"`.

---

### Task 8: Sweep du critère de sortie

**Files:** aucun produit obligatoire ; rapport SDD.

- [ ] **Step 1: Sweep à froid** (commandes séparées <9 min) : `git status` propre ; `:kadre:runtime:jvmTest` ; natives runtime 2 simulateurs ; appKitNativeTests ; `./scripts/test-uikit-simulator.sh all`.
- [ ] **Step 2: Checklist d'exit (roadmap l.214)** avec preuves citées : (1) la vue retournée est celle fournie à l'attach (`uikit-lease-boundaries` : isEqual) ; (2) state avant event pour chaque changement (runtime-owned — citer le drain de publication) ; (3) coordonnées d'input prêtes à utiliser les mêmes métriques (metrics == bounds Swift) ; (4) aucune requête redraw ni invocation native après révocation (`uikit-surface-redraw-after-close` + `uikit-lease-closed-after-terminal` + drain annulé) ; (5) les deux opt-ins vérifiés par consumers positifs et négatifs (platformAccess `Supported` + tests négatifs Unsupported/Closed/Temporary).
- [ ] **Step 3: Rapport de sweep** + push (contrôleur).

---

## Séquencement et estimation

Tasks 1→2→3→4→5→6→7→8 séquentielles. T1 (redraw/apply) et T2 (kernel) sont les plus testables unitairement ; T3 (binding KVO) et T5 (stimuli driver) portent le risque runtime (KVO bounds non documenté, forme du handler 17+ — preuves exigées, jamais de vert figé) ; T6/T7 = preuves et closure. Budget attendu : ~30–40M tokens de sous-agents (phase 1 : ~40M).
