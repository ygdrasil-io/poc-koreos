# iOS/tvOS Phase 0 — Fondations Kotlin/Native et première preuve UIKit — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter les quatre targets Kotlin/Native iOS/tvOS à `kadre`, `foundation` et `runtime`, implémenter les primitives `expect` natives, créer `platform:uikit` avec une première observation UIKit réelle, et prouver cette observation par des applications hôtes Xcode exécutées sur les deux simulateurs, avec preuves de contrats corrélées JSON/JUnit.

**Architecture:** Branche verticale phase 0 de [IOS-IMPLEMENTATION-ROADMAP.md](../../../kadre/IOS-IMPLEMENTATION-ROADMAP.md) : le runtime commun reste l'autorité ; `platform:uikit` n'expose encore aucun attach public — seulement une sonde de conduite (`KadreUikitProbe`) consommée par deux applications hôtes Xcode (iOS + tvOS) qui créent scène/fenêtre/vue et vérifient depuis Swift ce que Kotlin lit des objets UIKit réels. Le driver vit sous `kadre/contracts/driver/uikit/` et réutilise le système de preuves existant (registre + validateur + JUnit).

**Tech Stack:** Kotlin 2.4.20 (Kotlin/Native, hiérarchie par défaut + source set partagé `uikitMain`), Gradle 9.8, Xcode 27.0 avec simulateurs iOS 27.0 et tvOS 27.0, XcodeGen (génération locale du `.xcodeproj`, projet committé), `xcresulttool` + Python 3 (export XCTest→JUnit), JUnitEvidence/validateur existants.

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` §2 (décisions) + §5 Phase 0 (checklist et critère de sortie). Le plan argumente depuis ce document ; les deux voyagent ensemble.

## Décisions fermées par ce plan (roadmap §2)

| Décision | Résultat fermé ici |
|---|---|
| Matrice toolchain | Kotlin 2.4.20, Gradle 9.8, JDK 25, Xcode 27.0 (27A266a), simulateurs `iPhone 17 / iOS 27.0` et `Apple TV 4K (3rd generation) / tvOS 27.0`. Déploiement minimum déclaré des apps hôtes : iOS 16.0 / tvOS 16.0 (UIWindowScene requiert 13+) ; la vérification des planchers sur OS minimum est un travail nightly/phase 10. Si Kotlin 2.4.20 refuse le SDK Xcode 27 : STOP + rapport complet au contrôleur (pas de contournement silencieux). |
| Disponibilité tvOS | Les noms iOS existants (`KadreIos`, façade `KadreHost`) sont étendus à tvOS ; aucune valeur `KadrePlatform` nouvelle (UIKit existe) ; pas de `KadreTvos`. Topologie tvOS : mono-scène. Fermé dans les docs normatifs (Task 3). |
| Observation d'une `UIView` arbitraire | Lecture directe des propriétés SDK via le cinterop intégré Kotlin/Native (`platform.UIKit`), aucune sous-classe publique exigée. Prévu par `KadreUikitProbe` (Task 4), prouvé par les apps hôtes (Task 5). |
| Fenêtre fournie par le host | Aucun code en phase 0 ; le minimum à extraire pour la phase 1 est décrit dans la note de Task 8. |
| Export Kotlin/Swift | Décision : framework Kotlin/Native par slice (Debug/Release × 4 targets) consommé par lien direct dans les apps hôtes ; XCFramework + module source `KadreHost` + consumer externe restent l'affaire de la phase 3 (roadmap §2, ligne 64). |
| Demandes de scènes supplémentaires | tvOS mono-scène retournera `Rejected(Unsupported(RequestWindow))` (phase 4) ; aucune overload publique ajoutée. Fermé dans les docs (Task 3). |
| Télécommande et focus tvOS | Mapping fermé dans les docs (Task 3 : pressions natives → types d'input existants, focus ≠ activation) ; implémentation phase 5. |

## Global Constraints

- Kotlin `2.4.20`, Gradle `9.8.0`, JDK 25 (épinglés par PR #428) ; `explicitApi()` sur tout module KMP ; `applyDefaultHierarchyTemplate()` partout.
- Pas de cinterop `.def` : les SDK Apple passent par les platform libs intégrées (`platform.UIKit`, `platform.Foundation`, `platform.posix`).
- Targets ajoutées : uniquement `iosArm64`, `iosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64`. Pas d'`iosX64`/`tvosX64` (roadmap §9).
- Aucune nouvelle valeur `KadrePlatform` ; les noms publics iOS existants sont préservés.
- `platform:uikit` reste hors publication officielle (pas d'entrée dans les publications maven consommateurs) tant que ses garanties structurelles ne sont pas satisfaites ; la publication vers le repo `contractTest` local reste autorisée.
- Activation de contrat : `planned → active` dans le MÊME commit que scénarios, mapping d'evidence, sentinelles, capability snapshot et preuves JSON/JUnit (règle du README du registre, confirmée phases Web 5–6).
- Ne pas câbler la preuve UIKit dans `:kadre:check` (macOS-only, comme AppKit) : elle passe par `scripts/test-uikit-simulator.sh` + workflow CI dédié.
- Budgets TEST-STRATEGY : p95 job ≤ 8 min, timeout dur 15 min, watchdog enfants ; aucun retry automatique, aucun `continue-on-error`, aucun skip.
- Gradle DOIT être invoqué depuis l'intérieur du worktree (`cd` absolu d'abord) ; chaque commande Gradle est une commande séparée de moins de ~9 minutes.
- Preuves par famille obligatoires et séparées : une preuve iOS ne prouve jamais tvOS ; device et simulator ne peuvent pas écraser leurs artifacts respectifs.
- `xcodegen` est installé localement (`/opt/homebrew/bin/xcodegen`) : il génère le `.xcodeproj` une fois, le projet généré EST committé (CI sans xcodegen).

## Review Focus

1. **Sentinelle à rayon de blast global** — une mutation qui fait échouer tous les scénarios via le préambule partagé ne prouve rien ; la mutation de la sentinelle doit tuer son scénario sur SA propre assertion (blast radius consigné). Test : Task 6, étapes de mutation avec sortie capturée.
2. **« La sonde compile » ≠ « la sonde s'exécute »** — toute déclaration bloquante sur la toolchain (compat Xcode 27/Kotlin 2.4.20, friendPaths natifs, disponibilité tvOS d'une API) doit être dérivée d'une exécution réelle ou signalée au contrôleur avec l'erreur exacte. Tests : Task 1 étape 3, Task 2 étapes de sonde, Task 5.
3. **Dérive des noms JUnit/XCTest** — `testClass`/`testName` d'`evidence.tsv` doivent correspondre mot pour mot aux attributs `classname`/`name` du JUnit produit ; on les relit dans le XML généré, jamais devinés. Test : Task 6 étape de relecture XML.
4. **Preuve mono-famille présentée comme double** — chaque scénario partagé doit réellement s'exécuter sur les deux destinations ; les artifacts iOS et tvOS vivent dans des répertoires distincts et le script échoue si l'un manque. Tests : Task 5 (deux `xcodebuild`), Task 7 (assertions d'artifacts).
5. **Synchronisation du fixture par sommeil** — le test attend que la scène soit `didBecomeActive` et que la fenêtre existe (polling borné sur l'état du fixture), jamais un `sleep` nu ; un fixture non prêt fait échouer le test avec son état. Test : Task 5, classe `DriverFixture`.

---

### Task 1: Native targets on `kadre:foundation` + toolchain probe

**Files:**
- Modify: `kadre/foundation/build.gradle.kts`
- Modify: `gradle.properties` (uniquement si nécessaire — cf. étape 5)
- Test: compilation des quatre targets + `iosSimulatorArm64Test` / `tvosSimulatorArm64Test` existants de foundation (commonTest s'exécute nativement).

**Interfaces:**
- Consumes: build foundation actuel (`kadre/foundation/build.gradle.kts`, 42 lignes, targets `jvm`/`js`/`wasmJs`).
- Produces: `kadre:foundation` compiles pour `iosArm64`, `iosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64` ; le pattern exact de déclaration des targets (copié tel quel) est réutilisé par Task 2 (runtime) et Task 4 (platform:uikit + umbrella). Le constat de compatibilité Kotlin 2.4.20 ↔ Xcode 27 SDK est l'entrée de la matrice toolchain.

- [ ] **Step 1: Déclarer les quatre targets**

Dans `kadre/foundation/build.gradle.kts`, bloc `kotlin { }`, après `wasmJs { browser() }` et avant `explicitApi()` :

```kotlin
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
```

`applyDefaultHierarchyTemplate()` est déjà appelé : `appleMain`/`nativeMain` sont créés automatiquement. Foundation n'a que `commonMain`/`commonTest` ; aucun code nouveau n'est requis.

- [ ] **Step 2: Compiler les quatre targets (sonde toolchain)**

Run (une commande par target — première exécution : téléchargement du toolchain Kotlin/Native, compte 3–9 min) :

```bash
cd /Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f
./gradlew :kadre:foundation:compileKotlinIosSimulatorArm64
./gradlew :kadre:foundation:compileKotlinTvosSimulatorArm64
./gradlew :kadre:foundation:compileKotlinIosArm64
./gradlew :kadre:foundation:compileKotlinTvosArm64
```

Expected: `BUILD SUCCESSFUL` ×4. Confirmer dans les logs que les chemins compilés sont bien ceux du worktree (leçon repo : Gradle lancé hors du worktree construit le mauvais arbre).

**Règle d'escalade :** si Kotlin 2.4.20 échoue sur le SDK Xcode 27 (erreur konan/SDK, pas une erreur de code), NE PAS contourner : capturer la sortie complète, STOP, rapport au contrôleur. Une erreur de code Kotlin dans foundation est un bug de cette tâche.

- [ ] **Step 3: Exécuter les tests natifs de foundation sur les deux simulateurs**

```bash
./gradlew :kadre:foundation:iosSimulatorArm64Test
./gradlew :kadre:foundation:tvosSimulatorArm64Test
```

Expected: `BUILD SUCCESSFUL` — Gradle boote les simulateurs (`iPhone 17`/iOS 27.0 et `Apple TV 4K`/tvOS 27.0) et exécute le commonTest. Si un runtime de simulateur manque, la commande échoue : c'est le comportement exigé (roadmap : « un simulateur manquant fait échouer le job correspondant »).

- [ ] **Step 4: Élaguer les targets désactivés si Gradle se plaint**

Si la compilation signale des targets par défaut non supportés par l'hôte (ex. `iosX64` fantôme), ne rien ajouter : `gradle.properties` contient déjà `kotlin.native.ignoreDisabledTargets=true`. Ne toucher `gradle.properties` QUE si une erreur explicite le demande, et documenter la ligne ajoutée dans le rapport de tâche.

- [ ] **Step 5: Commit**

```bash
git add kadre/foundation/build.gradle.kts
git commit -m "build(foundation): add ios/tvos native targets"
```

---

### Task 2: Native primitives in `kadre:runtime` + Native visibility probe

**Files:**
- Modify: `kadre/runtime/build.gradle.kts`
- Create: `kadre/runtime/src/appleMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeLockApple.kt`
- Create: `kadre/runtime/src/appleMain/kotlin/org/graphiks/kadre/internal/runtime/IdentityKeyedMapApple.kt`
- Create: `kadre/runtime/src/appleMain/kotlin/org/graphiks/kadre/internal/runtime/InteractionCallFrameApple.kt`
- Create: `kadre/runtime/src/appleMain/kotlin/org/graphiks/kadre/internal/runtime/ThrowableClassificationApple.kt`
- Test: `kadre/runtime/src/appleTest/kotlin/org/graphiks/kadre/internal/runtime/NativePrimitivesConcurrencyTest.kt`

**Interfaces:**
- Consumes: les quatre `expect` de `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/` — `RuntimeLock` (+ `withLock`, `isHeldByCurrentThread`), `IdentityKeyedMap<T>` (+ `get/set/remove/clear`), `InteractionCallFrame` (+ `current/set/clear`), `Throwable.isLinkageFailure()`. Signatures exactes dans ces fichiers, à respecter au mot.
- Produces: les actuals `appleMain` (compilent pour les 4 targets) ; le wiring friend-path natif dans `kadre/runtime/build.gradle.kts` ; le VERDICT de la sonde de visibilité Native (entrée de la note de design, réutilisée en phase 1).

- [ ] **Step 1: Déclarer les quatre targets dans runtime**

Même insertion que Task 1 Step 1 dans `kadre/runtime/build.gradle.kts`, après `wasmJs { browser() }` :

```kotlin
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
```

- [ ] **Step 2: Implémenter RuntimeLock natif (mutex récursif pthread)**

`RuntimeLockApple.kt` — le JVM `synchronized` est réentrant et `isHeldByCurrentThread` en est conscient ; le js/wasm no-op est réentrant. Un `pthread_mutex_t` par défaut sur Darwin N'EST pas récursif → initializer récursif obligatoire, plus bookkeeping owner/depth (sémantique consultative, miroir de `Thread.holdsLock`) :

```kotlin
package org.graphiks.kadre.internal.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_trylock
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_self
import kotlin.concurrent.AtomicInt

/**
 * Mutex récursif pthread + owner/depth consultatifs. L'identité d'owner est le hashCode du
 * pthread_self() courant — consultatif, même contrat que Thread.holdsLock côté JVM.
 * Pas de destroy() : le JVM actual ne détruit jamais son monitor ; durée de vie = durée de session.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual class RuntimeLock actual constructor() {
    private val mutex: pthread_mutex_t = nativeHeap.alloc()
    private val ownerTid = AtomicInt(0)
    private val depth = AtomicInt(0)

    init {
        memScoped {
            val attr = alloc<pthread_mutexattr_t>()
            pthread_mutexattr_init(attr.ptr)
            pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_RECURSIVE)
            pthread_mutex_init(mutex.ptr, attr.ptr)
            pthread_mutexattr_destroy(attr.ptr)
        }
    }

    internal actual inline fun <T> withLock(action: () -> T): T {
        val self = pthread_self().hashCode()
        if (ownerTid.value == self && depth.value > 0) {
            depth.value += 1
            pthread_mutex_lock(mutex.ptr) // récursif : la réentrance est réelle aussi côté pthread
        } else {
            pthread_mutex_lock(mutex.ptr)
            ownerTid.value = self
            depth.value = 1
        }
        try {
            return action()
        } finally {
            val d = depth.value - 1
            depth.value = d
            if (d == 0) ownerTid.value = 0
            pthread_mutex_unlock(mutex.ptr)
        }
    }

    internal actual fun RuntimeLock.isHeldByCurrentThread(): Boolean {
        if (depth.value > 0 && ownerTid.value == pthread_self().hashCode()) return true
        // Sonde trylock : mutex libre → on le prend et le rend immédiatement (réponse false) ;
        // mutex occupé → la réponse vient de l'owner enregistré.
        val acquired = pthread_mutex_trylock(mutex.ptr) == 0
        if (acquired) {
            pthread_mutex_unlock(mutex.ptr)
            return false
        }
        return depth.value > 0 && ownerTid.value == pthread_self().hashCode()
    }
}
```

Notes d'implémentation obligatoires :
- Extension réelle : `internal expect inline fun <T> RuntimeLock.withLock` est une extension — l'actual est aussi une extension ; idem `isHeldByCurrentThread`. Ne pas les mettre membres.
- `nativeHeap.alloc<pthread_mutex_t>()` : `pthread_mutex_t` est mappé comme struct cinterop (`CStructVar`), `alloc()` est donc la forme attendue. Si le compilateur K/N 2.4.20 exige une autre forme d'allocation pour ce type précis, l'implémenteur prouve la forme compilable (ex. `nativeHeap.alloc<pthread_mutex_tVar>()`) SANS changer les signatures expect ni la sémantique, et note la forme retenue dans le rapport.
- L'ordre owner/depth vs `pthread_mutex_unlock` dans le `finally` est volontaire (l'owner est effacé AVANT la libération pthread) : conserver cet ordre.
- Ne jamais élargir la sémantique : pas de `tryLock` public, pas de timeout.

- [ ] **Step 3: Implémenter IdentityKeyedMap natif**

`IdentityKeyedMapApple.kt` — pas d'IdentityHashMap en K/N ; scan linéaire par identité (miroir de l'actual JS, sémantique identique) :

```kotlin
package org.graphiks.kadre.internal.runtime

internal actual class IdentityKeyedMap<T> actual constructor() {
    private val entries = mutableListOf<Pair<Any, T>>()

    internal actual operator fun get(key: Any): T? =
        entries.firstOrNull { it.first === key }?.second

    internal actual operator fun set(key: Any, value: T) {
        remove(key)
        entries.add(key to value)
    }

    internal actual fun remove(key: Any): T? {
        val index = entries.indexOfFirst { it.first === key }
        if (index < 0) return null
        val removed = entries[index].second
        entries.removeAt(index)
        return removed
    }

    internal actual fun clear() = entries.clear()
}
```

- [ ] **Step 4: Implémenter InteractionCallFrame natif**

`InteractionCallFrameApple.kt` — miroir du JVM (ThreadLocal), via `kotlin.concurrent.ThreadLocal` de la stdlib commune (réelle par thread native) :

```kotlin
package org.graphiks.kadre.internal.runtime

import kotlin.concurrent.ThreadLocal
import org.graphiks.kadre.surface.SurfaceId

internal actual class InteractionCallFrame actual constructor() {
    private companion object {
        @ThreadLocal
        val active: ThreadLocal<SurfaceId?> = ThreadLocal()
    }

    internal actual fun current(): SurfaceId? = active.value

    internal actual fun set(surfaceId: SurfaceId?) {
        active.value = surfaceId
    }

    internal actual fun clear() {
        active.value = null
    }
}
```

- [ ] **Step 5: Implémenter Throwable.isLinkageFailure natif**

`ThrowableClassificationApple.kt` :

```kotlin
package org.graphiks.kadre.internal.runtime

internal actual fun Throwable.isLinkageFailure(): Boolean = false
```

KDoc obligatoire, miroir de la politique js/wasm : « Kotlin/Native n'expose pas d'équivalent de `LinkageError` ; les échecs d'interop Objective-C ne traversent pas Kotlin comme exceptions rattrapables. Classé `false` exactement comme js/wasmJs — jamais élargi. » (Si l'implémenteur découvre en exécutant un type K/N documenté comme échec de liaison rattrapable, il le signale au contrôleur au lieu de l'élargir seul.)

- [ ] **Step 6: Écrire les tests de concurrence natifs (TDD : d'abord échouer, si possible avant l'actual)**

`kadre/runtime/src/appleTest/kotlin/org/graphiks/kadre/internal/runtime/NativePrimitivesConcurrencyTest.kt` — écrire CE FICHIER AVANT les actuals des Steps 2–5 quand c'est possible (la compilation échoue : absence d'actual), puis implémenter jusqu'au vert :

```kotlin
package org.graphiks.kadre.internal.runtime

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import org.graphiks.kadre.surface.SurfaceId

class NativePrimitivesConcurrencyTest {

    @Test
    fun lockProvidesRealMutualExclusionAcrossNativeThreads() {
        val lock = RuntimeLock()
        var counter = 0
        val threads = (1..4).map { worker ->
            thread {
                repeat(10_000) {
                    lock.withLock { counter += 1 }
                }
            }
        }
        threads.forEach { it.join() }
        assertEquals(40_000, counter)
    }

    @Test
    fun lockIsReentrantWithinOneThread() {
        val lock = RuntimeLock()
        val nested = lock.withLock {
            lock.withLock { lock.isHeldByCurrentThread() }
        }
        assertTrue(nested)
        lock.withLock { assertTrue(lock.isHeldByCurrentThread()) }
        assertFalse(lock.isHeldByCurrentThread())
    }

    @Test
    fun isHeldByCurrentThreadIsPerThread() {
        val lock = RuntimeLock()
        val captureLock = RuntimeLock()
        val results = mutableListOf<Boolean>()
        lock.withLock {
            val other = thread {
                val held = lock.isHeldByCurrentThread()
                captureLock.withLock { results.add(held) }
            }
            other.join()
        }
        assertEquals(listOf(false), results)
        // libéré : personne ne le tient
        assertFalse(lock.isHeldByCurrentThread())
    }

    @Test
    fun identityKeyedMapKeysByReferenceIdentity() {
        val map = IdentityKeyedMap<String>()
        val a = Any()
        val b = Any()
        map[a] = "a"
        map[b] = "b"
        assertEquals("a", map[a])
        assertEquals("b", map[b])
        // une copie « égale » n'est pas la même clé
        data class Key(val v: Int)
        val k1 = Key(1)
        val k2 = Key(1)
        map[k1] = "k1"
        map[k2] = "k2"
        assertEquals("k1", map[k1])
        assertEquals("k2", map[k2])
        assertSame("k1", map.remove(k1))
        assertNull(map.remove(k1))
        map.clear()
        assertNull(map[a])
    }

    @Test
    fun interactionCallFrameIsPerThread() {
        val frame = InteractionCallFrame()
        val surface = SurfaceId("s1") // vérifier le constructeur réel de SurfaceId avant (foundation)
        assertNull(frame.current())
        frame.set(surface)
        assertSame(surface, frame.current())
        val results = mutableListOf<SurfaceId?>()
        val capture = RuntimeLock()
        val other = thread {
            val seen = frame.current()
            capture.withLock { results.add(seen) }
        }
        other.join()
        assertEquals(listOf(null), results)
        frame.clear()
        assertNull(frame.current())
    }

    @Test
    fun linkageFailureClassificationStaysFalseOnNative() {
        assertFalse(RuntimeException("x").isLinkageFailure())
        assertFalse(NullPointerException().isLinkageFailure())
    }
}
```

Remarques d'implémentation :
- Vérifier la vraie forme de `SurfaceId` dans `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/surface/Identity.kt` avant d'écrire le test (constructeur/value class) et adapter UNIQUEMENT la construction, jamais les assertions.
- Un `assertEquals(40_000, counter)` qui échoue de façon intermittente = le mutex n'est pas un vrai mutex : c'est un échec de tâche, pas un flake à relancer.
- Borne de temps : le test d'exclusion peut prendre quelques secondes sur simulateur ; rester sous 30 s total (mesurer avec `measureTime` dans un test séparé si besoin de diagnostic).

- [ ] **Step 7: Exécuter les tests natifs sur les deux simulateurs**

```bash
./gradlew :kadre:runtime:iosSimulatorArm64Test
./gradlew :kadre:runtime:tvosSimulatorArm64Test
./gradlew :kadre:runtime:jvmTest
```

Expected: les deux premiers bootent leurs simulateurs et passent ; `jvmTest` reste vert (aucune régression JVM). Si KGP ne sait pas exécuter un test natif tvOS faute de destination (message explicite), capturer l'erreur — c'est un critère de sortie qui doit échouer, pas un skip.

- [ ] **Step 8: Sonde de visibilité Native (foundation internals ↔ runtime), verdict consigné**

Contexte vérifié en exploration : `foundation` n'a qu'UN `internal` top-level (`requireFinitePositive`, `DeliveryPolicies.kt:111`), utilisé uniquement à l'intérieur de foundation. Le besoin cross-module réel (consommé par `platform:web` aujourd'hui, par `platform:uikit` en phase 1) porte sur les `internal` de **runtime**. Les `friendPaths` JVM (runtime/build.gradle.kts:48–53) et `-Xfriend-modules` JS (62–96) n'ont pas d'équivalent automatique en Native.

Procédure de sonde (temporaire, à revert) :
1. Ajouter temporairement dans `kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/policy/DeliveryPolicies.kt` : `internal fun nativeFriendProbe(): Int = 42`.
2. Ajouter temporairement dans `kadre/runtime/src/appleMain/.../RuntimeLockApple.kt` : un import de `org.graphiks.kadre.policy.nativeFriendProbe` + un `private val probe = nativeFriendProbe()`.
3. Tenter `./gradlew :kadre:runtime:compileKotlinIosSimulatorArm64` SANS friend path → noter l'erreur exacte (attendu : résolution impossible d'une déclaration internal d'un autre module).
4. Ajouter dans `kadre/runtime/build.gradle.kts`, après le bloc JVM friendPaths (lignes 48–53), le wiring natif candidat :

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn(foundationAllMetadataJar)
    friendPaths.from(foundationCommonMainMetadata)
}
```

   (Si `friendPaths` n'existe pas sur `KotlinNativeCompile`, essayer la forme par property de compilation ou `freeCompilerArgs += "-Xfriend-modules=<klib path>"` avec le klib de foundation : `kadre/foundation/build/classes/kotlin/iosSimulatorArm64/main`.)
5. Recompiler → noter le verdict. Verdict POSSIBLE : (a) friendPaths/friend-modules fonctionne en Native → garder le wiring (dormant, miroir du JVM, avec un commentaire qui renvoie à cette sonde) ; (b) aucun mécanisme natif → revert intégral, et la note de phase 1 dira : « les `internal` de runtime consommés par uikit devront être relocalisés dans un module/niveau accessible — jamais rendus publics ».
6. REVERT du code de sonde (foundation + runtime appleMain) dans tous les cas ; ne garder QUE le wiring de build si verdict (a), avec commentaire :

```kotlin
// Native friend probe (2026-10-10, phase 0) : <verdict>. Dormant jusqu'à ce que platform:uikit
// consomme des internals de runtime en phase 1 (miroir du wiring JVM ci-dessus).
```

- [ ] **Step 9: Commit**

```bash
git add kadre/runtime/build.gradle.kts kadre/runtime/src/appleMain kadre/runtime/src/appleTest
git commit -m "feat(runtime): native apple actuals for runtime primitives"
```

---

### Task 3: Normative docs — tvOS perimeter closure (roadmap §2 + Phase 0 checklist item 1)

**Files:**
- Modify: `kadre/DESIGN.md` (§15.2, lignes ~1881–1903)
- Modify: `kadre/PUBLIC-API-CATALOG.md` (ligne 30 et §9 ligne ~1012)
- Modify: `kadre/PROJECT-ARCHITECTURE.md` (lignes 33, 55, 80, 104, 134, 160–171, 217)
- Modify: `kadre/BACKEND-CAPABILITIES.md` (§6.2 lignes 223–237)
- Modify: `kadre/INTEROP-EXPORTS.md` (§5 ligne ~98+, §9 ligne 421)
- Modify: `kadre/OPERATION-CONTRACTS.md` (§7.1 ligne 187)
- Modify: `kadre/TEST-STRATEGY.md` (§9 ligne 303, §10)
- Test: relecture croisée — chaque édition cite l'ancre exacte ; grep `tvos` ne retourne plus zéro dans ces fichiers ; AUCUN nom public iOS renommé.

**Interfaces:**
- Consumes: les décisions fermées (tableau en tête de plan) et le verdict de la sonde de Task 2 (citè dans PROJECT-ARCHITECTURE).
- Produces: le périmètre tvOS fermé textuellement — les tasks 4–8 s'appuient sur ces formulations sans rouvrir les décisions.

Les textes suivants sont à insérer/adapder (FR, style des documents existants ; l'implémenteur relit chaque ancre avant édition) :

- [ ] **Step 1: DESIGN.md §15.2** — après le bullet « Une fenêtre iPadOS supplémentaire crée une nouvelle scène » (~ligne 1901), ajouter :

```markdown
- La disponibilité de cet attach et de la façade Swift s'étend à tvOS avec les mêmes noms (`KadreIos`, `KadreHost`) ; le parcours tvOS est mono-scène : une seule session par application, aucune seconde `UIWindowScene` demandée.
- Sur tvOS, la télécommande est une source d'input propre (directions, sélection, retour), et la navigation par focus reste la responsabilité du moteur de focus du host ; focus et activation de scène ne se confondent pas.
```

- [ ] **Step 2: PUBLIC-API-CATALOG.md** — ligne 30, étendre la ligne du package :

```markdown
| org.graphiks.kadre.platform.uikit | KadreIos; callback withUIKitView (disponibilité iOS/iPadOS et tvOS) |
```

Et §9 (~ligne 1012), après la phrase sur `BACKEND-CAPABILITIES.md`, ajouter : « Les points d'attachement UIKit s'appliquent à iOS/iPadOS et tvOS ; le parcours tvOS est mono-scène. »

- [ ] **Step 3: PROJECT-ARCHITECTURE.md** — ligne 134 : `platform:uikit | iOS Kotlin/Native` devient `platform:uikit | iOS et tvOS Kotlin/Native`. Ligne 33/55/80/104 : vérifier que `uikit` apparaît déjà (arbre aspirational) et ajouter tvOS là où iOS est cité. §6.2 (~166) : ajouter après les coordonnées `org.graphiks.kadre:uikit` : « Publication officielle différée jusqu'à satisfaction des garanties structurelles (phase 1) ; la phase 0 ne publie l'adaptateur que vers le dépôt contractTest. » Consigner le verdict de la sonde de visibilité Native (Task 2) dans une phrase du §4 ou §5.

- [ ] **Step 4: BACKEND-CAPABILITIES.md §6.2** — après le paragraphe « Il n'existe ni overload `UIApplication`... » (~ligne 237), ajouter :

```markdown
Disponibilité tvOS : les mêmes contrats d'attach s'appliquent à tvOS, avec une topologie mono-scène ; `requestWindow` y retourne un état `Rejected(Unsupported(RequestWindow))` (livré en phase 4). Les sources d'input tvOS (télécommande, focus) et leurs limites sont décrites par phase ; aucune parité IME ou touch n'est supposée. Les preuves iOS et tvOS sont séparées et obligatoires ; une preuve iOS ne couvre pas tvOS.
```

- [ ] **Step 5: INTEROP-EXPORTS.md** — en tête de §5 (~ligne 98), ajouter : « Le module `KadreHost` cible iOS/iPadOS et tvOS ; les slices d'appareil et de simulateur des deux familles sont distinctes (assemblage et consumers : phase 3). » §9 (ligne 421), ligne du tableau Swift UIKit : « attach une factory Kotlin... » → préciser « (iOS et tvOS) ».

- [ ] **Step 6: OPERATION-CONTRACTS.md §7.1** — ligne 187, après la ligne `HostSurface.withAndroidView, withUIKitView, withWebElement...`, ajouter une phrase : « La ligne `withUIKitView` couvre iOS/iPadOS et tvOS ; le chemin tvOS est livré avec la phase 2 UIKit et n'est pas déduit d'une preuve iOS. »

- [ ] **Step 7: TEST-STRATEGY.md §9** — la ligne 303 réserve déjà `uikit-contracts | Kotlin/Native + simulateur booté, scene/lifecycle/surface | 8 min` : la marquer implémentée en phase 0 en précisant « deux jobs simulateur indépendants (iOS 27.0, tvOS 27.0) ; compilation et liaison des slices appareil iosArm64/tvosArm64 ». §10 : rien à changer (la preuve reste régie par les règles existantes).

- [ ] **Step 8: Vérification**

```bash
grep -rn "tvOS" kadre/DESIGN.md kadre/PUBLIC-API-CATALOG.md kadre/PROJECT-ARCHITECTURE.md kadre/BACKEND-CAPABILITIES.md kadre/INTEROP-EXPORTS.md kadre/OPERATION-CONTRACTS.md kadre/TEST-STRATEGY.md | wc -l
git diff --stat
```

Expected: comptage > 10 ; diff limité aux sept fichiers ; AUCUN nom public renommé (`git diff | grep -E "^-.*(KadreIos|KadreHost)"` vide, hors déplacements).

- [ ] **Step 9: Commit**

```bash
git add kadre/DESIGN.md kadre/PUBLIC-API-CATALOG.md kadre/PROJECT-ARCHITECTURE.md kadre/BACKEND-CAPABILITIES.md kadre/INTEROP-EXPORTS.md kadre/OPERATION-CONTRACTS.md kadre/TEST-STRATEGY.md
git commit -m "docs: close the tvOS perimeter of the UIKit contracts"
```

---

### Task 4: `kadre:platform:uikit` — module, sonde Kotlin, agrégation umbrella, frameworks

**Files:**
- Modify: `settings.gradle.kts` (include)
- Create: `kadre/platform/uikit/build.gradle.kts`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreUikitProbe.kt`
- Modify: `kadre/build.gradle.kts` (targets natifs + agrégation appleMain)
- Modify: `kadre/build.gradle.kts` (agrégation `check`/publication : NON modifiées pour uikit — contrainte « hors publication officielle »)
- Test: compilation 4 targets + liaison des 4 slices de framework + `KadreUikitProbe` compiles (l'exécution réelle est la preuve O3 de Task 5).

**Interfaces:**
- Consumes: pattern de build `kadre/platform/desktop/build.gradle.kts` ; les types `platform.UIKit.UIWindow`/`UIView` du cinterop intégré.
- Produces: `org.graphiks.kadre.platform.uikit.KadreUikitProbe.observe(window: UIWindow, view: UIView): KadreUikitObservation` — signature EXACTE consommée par les apps Swift de Task 5 ; les tâches `:kadre:platform:uikit:linkDebugFramework<target>` consommées par Task 5/6/7 ; targets natifs de l'umbrella `kadre`.

- [ ] **Step 1: Inclure le module**

`settings.gradle.kts`, après `include(":kadre:platform:web")` :

```kotlin
include(":kadre:platform:uikit")
```

- [ ] **Step 2: Build du module**

`kadre/platform/uikit/build.gradle.kts` :

```kotlin
plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }

    // Source set partagé iOS+tvOS, déclaré explicitement (le template par défaut ne crée pas "uikitMain").
    sourceSets {
        val uikitMain = create("uikitMain")
        iosMain.get().dependsOn(uikitMain)
        tvosMain.get().dependsOn(uikitMain)

        uikitMain.dependencies {
            api(project(":kadre:foundation"))
            implementation(project(":kadre:runtime"))
        }
    }

    binaries {
        framework {
            baseName = "KadreUikit"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = uri(rootProject.layout.buildDirectory.dir("kadre-contract-repository"))
        }
    }
}
```

- [ ] **Step 3: La sonde Kotlin (source d'evidence O3 de Task 5/6)**

`kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreUikitProbe.kt` — lecture directe des objets fournis, aucune sous-classe, aucune rétention :

```kotlin
package org.graphiks.kadre.platform.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIView
import platform.UIKit.UIWindow

/** État d'appartenance de la vue observée à la fenêtre fournie. */
public enum class KadreUikitWindowMembership {
    /** `view.window` est exactement la fenêtre fournie. */
    Attached,

    /** La vue n'est pas dans la fenêtre fournie (autre fenêtre ou aucune). */
    Detached,
}

/** Lecture immédiate et copiée d'un objet UIKit fourni par le host. Aucune rétention native. */
public data class KadreUikitObservation(
    public val windowMembership: KadreUikitWindowMembership,
    public val sceneConnected: Boolean,
    public val viewBoundsWidth: Double,
    public val viewBoundsHeight: Double,
    public val displayScale: Double,
)

/**
 * Sonde de conduite de la phase 0 : prouve que le code Kotlin/Native observe des objets UIKit
 * réels reçus du host. Scaffolding du driver — remplacé par `KadreIos.attach` en phase 1 ;
 * ne fait partie d'aucune façade promise. Marqué platform-api : opt-in explicite du consumer.
 */
@KadrePlatformApi // annoter avec l'annotation réelle du repo (foundation) ; vérifier le FQN exact
public object KadreUikitProbe {
    @OptIn(ExperimentalForeignApi::class)
    public fun observe(window: UIWindow, view: UIView): KadreUikitObservation {
        val membership =
            if (view.window === window) KadreUikitWindowMembership.Attached
            else KadreUikitWindowMembership.Detached
        val sceneConnected = window.windowScene != null
        val bounds = view.bounds.useContents { width to height }
        val scale = view.traitCollection.displayScale
        return KadreUikitObservation(
            windowMembership = membership,
            sceneConnected = sceneConnected,
            viewBoundsWidth = bounds.first,
            viewBoundsHeight = bounds.second,
            displayScale = scale,
        )
    }
}
```

Notes :
- Vérifier le FQN réel de `@KadrePlatformApi` (grep dans foundation) et l'opt-in requis ; si l'annotation impose `@DelicateKadreApi` conjointement, suivre la convention du repo.
- `view.traitCollection.displayScale` est déclaré iOS 8+/tvOS : identique des deux familles (si le compilateur refuse sur tvOS — l'implémenteur PROUVE avant de déclarer une indisponibilité, cf. Review Focus 2 — basculer sur `window.screen.scale` pour iOS et l'équivalent tvOS avec branches `iosMain`/`tvosMain` minimales).
- La sonde ne retient AUCUNE référence : lecture → copie → retour (contrainte roadmap §3.3 « les callbacks ordinaires copient le payload avant retour »).

- [ ] **Step 4: Agréger depuis l'umbrella**

`kadre/build.gradle.kts` — ajouter les quatre targets après `wasmJs { browser() }`, puis dans `sourceSets` :

```kotlin
        appleMain.dependencies {
            api(project(":kadre:platform:uikit"))
        }
```

NE PAS ajouter `:kadre:platform:uikit` aux listes `check`/`publishContractArtifacts` (lignes 32–53) : l'adaptateur reste hors des agrégations officielles en phase 0.

- [ ] **Step 5: Compiler et lier les quatre slices**

```bash
./gradlew :kadre:platform:uikit:compileKotlinIosSimulatorArm64 :kadre:platform:uikit:compileKotlinTvosSimulatorArm64
./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64 :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64
./gradlew :kadre:platform:uikit:linkDebugFrameworkIosArm64 :kadre:platform:uikit:linkDebugFrameworkTvosArm64
./gradlew :kadre:compileKotlinIosSimulatorArm64 :kadre:compileKotlinTvosSimulatorArm64
./gradlew :kadre:compileKotlinIosArm64 :kadre:compileKotlinTvosArm64
```

Expected: succès partout ; frameworks produits sous `kadre/platform/uikit/build/bin/<target>/debugFramework/KadreUikit.framework`. Vérifier l'existence réelle des 4 frameworks (`ls`).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts kadre/build.gradle.kts kadre/platform/uikit
git commit -m "feat(uikit): add platform module with the phase-0 UIKit probe"
```

---

### Task 5: Xcode host apps (iOS + tvOS) — la preuve O3 exécutable

**Files:**
- Create: `kadre/contracts/driver/uikit/project.yml` (XcodeGen)
- Create: `kadre/contracts/driver/uikit/KadreUikitDriver.xcodeproj/` (généré, COMMITTÉ)
- Create: `kadre/contracts/driver/uikit/apps/Shared/DriverAppDelegate.swift`
- Create: `kadre/contracts/driver/uikit/apps/Shared/DriverSceneDelegate.swift`
- Create: `kadre/contracts/driver/uikit/apps/Shared/DriverFixture.swift`
- Create: `kadre/contracts/driver/uikit/apps/Ios/MainApp.swift` (+ Info.plist via project.yml)
- Create: `kadre/contracts/driver/uikit/apps/Tvos/MainApp.swift`
- Create: `kadre/contracts/driver/uikit/tests/Shared/KadreUikitDriverTests.swift`
- Modify: `.gitignore` (artifacts Xcode)
- Test: `xcodebuild test` vert sur les deux destinations.

**Interfaces:**
- Consumes: `KadreUikit.framework` (Task 4) ; `KadreUikitProbe.observe` ; fixture : fenêtre créée au `sceneDidBecomeActive`.
- Produces: applications hôte par famille qui créent scène/fenêtre/vue et appellent la sonde Kotlin ; bundle XCTest par famille dont les méthodes `testKotlinProbeObservesRealWindow()` et `testProbeReportsViewOutsideWindowAsDetached()` deviennent les scénarios BCK-010 (noms EXACTS figés pour Task 6) ; `build/xcresult/ios.xcresult` et `build/xcresult/tvos.xcresult` consommés par Task 6.

- [ ] **Step 1: project.yml (XcodeGen)**

`kadre/contracts/driver/uikit/project.yml` :

```yaml
name: KadreUikitDriver
options:
  bundleIdPrefix: org.graphiks.kadre.driver
  deploymentTarget:
    iOS: "16.0"
    tvOS: "16.0"
settings:
  base:
    CODE_SIGNING_ALLOWED: "NO"
    CODE_SIGN_IDENTITY: ""
    SWIFT_VERSION: "5.0"
targets:
  KadreUikitDriverIos:
    type: application
    platform: iOS
    sources:
      - path: apps/Shared
      - path: apps/Ios
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: org.graphiks.kadre.driver.ios
        FRAMEWORK_SEARCH_PATHS: "$(inherited) $(SRCROOT)/../../../platform/uikit/build/bin/iosSimulatorArm64/debugFramework"
    dependencies:
      - framework: "$(SRCROOT)/../../../platform/uikit/build/bin/iosSimulatorArm64/debugFramework/KadreUikit.framework"
        embed: true
    info:
      path: apps/Ios/Info.plist
      properties:
        UILaunchStoryboardName: ""
        UIApplicationSceneManifest:
          UIApplicationSupportsMultipleScenes: false
          UISceneConfigurations:
            UIWindowSceneSessionRoleApplication:
              - UISceneConfigurationName: default
                UISceneDelegateClassName: $(PRODUCT_MODULE_NAME).DriverSceneDelegate
  KadreUikitDriverTvOs:
    type: application
    platform: tvOS
    sources:
      - path: apps/Shared
      - path: apps/Tvos
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: org.graphiks.kadre.driver.tvos
        FRAMEWORK_SEARCH_PATHS: "$(inherited) $(SRCROOT)/../../../platform/uikit/build/bin/tvosSimulatorArm64/debugFramework"
    dependencies:
      - framework: "$(SRCROOT)/../../../platform/uikit/build/bin/tvosSimulatorArm64/debugFramework/KadreUikit.framework"
        embed: true
    info:
      path: apps/Tvos/Info.plist
      properties:
        UILaunchStoryboardName: ""
        UIApplicationSceneManifest:
          UIApplicationSupportsMultipleScenes: false
          UISceneConfigurations:
            UIWindowSceneSessionRoleApplication:
              - UISceneConfigurationName: default
                UISceneDelegateClassName: $(PRODUCT_MODULE_NAME).DriverSceneDelegate
  KadreUikitDriverIosTests:
    type: bundle.unit-test
    platform: iOS
    sources:
      - path: tests/Shared
    dependencies:
      - target: KadreUikitDriverIos
  KadreUikitDriverTvOsTests:
    type: bundle.unit-test
    platform: tvOS
    sources:
      - path: tests/Shared
    dependencies:
      - target: KadreUikitDriverTvOs
schemes:
  KadreUikitDriverIos:
    build:
      targets:
        KadreUikitDriverIos: all
    test:
      targets:
        - KadreUikitDriverIosTests
  KadreUikitDriverTvOs:
    build:
      targets:
        KadreUikitDriverTvOs: all
    test:
      targets:
        - KadreUikitDriverTvOsTests
```

Le chemin de sortie KGP attendu est `build/bin/<target>/debugFramework` — l'implémenteur le CONFIRME par `ls kadre/platform/uikit/build/bin/` après Task 4 et ajuste les deux chemins si le layout réel diffère (une seule source de vérité : l'arborescence produite, jamais une supposition). xcodegen résout les chemins relatifs au fichier project.yml ; `xcodegen generate` s'exécute depuis `kadre/contracts/driver/uikit/`.

- [ ] **Step 2: Swift partagé — app, scène, fixture**

`apps/Shared/DriverAppDelegate.swift` :

```swift
import UIKit

final class DriverAppDelegate: NSObject, UIApplicationDelegate {}
```

`apps/Shared/DriverSceneDelegate.swift` :

```swift
import UIKit

final class DriverSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        DriverFixture.shared.install(windowScene: windowScene)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        DriverFixture.shared.markActive()
    }
}
```

`apps/Shared/DriverFixture.swift` — le fixture est l'AUTORITÉ Swift ; les tests y lisent fenêtre/vue et état, avec attente bornée (pas de sommeil nu) :

```swift
import UIKit

/// État partagé app/test. La fenêtre et les vues sont créées à la connexion de la scène ;
/// `markActive` est appelé par `sceneDidBecomeActive`. Les tests attendent `waitUntilReady`.
final class DriverFixture {
    static let shared = DriverFixture()

    private(set) var window: UIWindow?
    private(set) var observedView: UIView?
    private(set) var detachedView: UIView?
    private(set) var becameActive = false

    func install(windowScene: UIWindowScene) {
        let window = UIWindow(windowScene: windowScene)
        let frame = CGRect(x: 0, y: 0, width: 320, height: 240)
        let observed = UIView(frame: frame)
        let detached = UIView(frame: frame)
        window.addSubview(observed) // observed est DANS la fenêtre
        // detached n'est JAMAIS ajoutée à la fenêtre
        window.isHidden = false
        window.makeKeyAndVisible()
        self.window = window
        self.observedView = observed
        self.detachedView = detached
    }

    func markActive() { becameActive = true }

    /// Attente bornée (5 s, pas de sommeil nu) : échoue avec l'état courant si non prêt.
    func waitUntilReady() throws {
        let deadline = Date().addingTimeInterval(5)
        while !(window != nil && observedView != nil && becameActive) {
            if Date() > deadline {
                throw NSError(domain: "KadreUikitDriver", code: 1,
                              userInfo: ["state": "window=\(String(describing: window)) active=\(becameActive)"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
    }
}
```

`apps/Ios/MainApp.swift` et `apps/Tvos/MainApp.swift` (noms distincts par famille pour la lisibilité ; chaque target compile ses propres sources, donc aucune collision) :

```swift
import UIKit

@main
final class MainAppIos: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool { true }
}
```

(tvOS : `final class MainAppTvos`, même corps.)

- [ ] **Step 3: Le bundle de tests (scénarios BCK-010, noms FIGÉS)**

`tests/Shared/KadreUikitDriverTests.swift` — le bundle de tests importe le module de l'app hôte (pour `DriverFixture`) ET le framework Kotlin ; les noms de modules diffèrent par famille, d'où le garde `canImport` :

```swift
import XCTest
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

final class KadreUikitDriverTests: XCTestCase {

    private func fixture() throws -> DriverFixture {
        let f = DriverFixture.shared
        try f.waitUntilReady()
        return f
    }

    /// BCK-010 / uikit-driver-observes-real-window — la sonde Kotlin lit les MÊMES valeurs que Swift.
    func testKotlinProbeObservesRealWindow() throws {
        let f = try fixture()
        let window = try XCTUnwrap(f.window)
        let view = try XCTUnwrap(f.observedView)

        let observation = KadreUikitProbe.observe(window: window, view: view)

        XCTAssertEqual(observation.windowMembership, .attached)
        XCTAssertEqual(observation.sceneConnected, window.windowScene != nil)
        XCTAssertEqual(observation.viewBoundsWidth, Double(view.bounds.width), accuracy: 0.001)
        XCTAssertEqual(observation.viewBoundsHeight, Double(view.bounds.height), accuracy: 0.001)
        XCTAssertEqual(observation.displayScale, Double(view.traitCollection.displayScale), accuracy: 0.001)
    }

    /// BCK-010 / uikit-driver-reports-detached-view — une vue hors fenêtre est rapportée Detached.
    func testProbeReportsViewOutsideWindowAsDetached() throws {
        let f = try fixture()
        let window = try XCTUnwrap(f.window)
        let detached = try XCTUnwrap(f.detachedView)

        let observation = KadreUikitProbe.observe(window: window, view: detached)

        XCTAssertEqual(observation.windowMembership, .detached)
    }
}
```

- [ ] **Step 4: Générer le projet, construire, exécuter sur les deux destinations**

```bash
cd kadre/contracts/driver/uikit && xcodegen generate
cd /Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f
./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64 :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64
cd kadre/contracts/driver/uikit
xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverIos \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.0' \
  -resultBundlePath build/xcresult/ios.xcresult CODE_SIGNING_ALLOWED=NO test
xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverTvOs \
  -destination 'platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0' \
  -resultBundlePath build/xcresult/tvos.xcresult CODE_SIGNING_ALLOWED=NO test
```

Expected: `** TEST SUCCEEDED **` ×2 — chaque famille exécute ses 2 tests. Les destinations sont vérifiées par `xcrun simctl list devices available` au préalable ; une destination absente = échec explicite (pas de substitution).
Dépannage attendu (normal sur ce type d'intégration) : chemins de framework (FRAMEWORK_SEARCH_PATHS), rpath, nom de module Swift (`KadreUikit`), signature (CODE_SIGNING_ALLOWED=NO partout, y compris test host). Chaque correction passe par une regénération xcodegen ou une édition de project.yml — le `.xcodeproj` généré EST committé à la fin.

- [ ] **Step 5: .gitignore**

Ajouter à `.gitignore` racine :

```
# Xcode driver artifacts
xcuserdata/
*.xcuserstate
kadre/contracts/driver/uikit/build/
```

(`build/` est déjà couvert globalement ; la ligne explicite reste si `build/` est un jour scoping — l'implémenteur vérifie et n'ajoute que le nécessaire.)

- [ ] **Step 6: Commit**

```bash
git add kadre/contracts/driver/uikit .gitignore
git commit -m "feat(uikit): add iOS and tvOS Xcode driver host apps proving the Kotlin probe"
```

---

### Task 6: XCTest→JUnit export + contrat BCK-010 activé (registre, evidence, validateur, sentinelle)

**Files:**
- Create: `kadre/contracts/driver/uikit/tools/xctest_to_junit.py`
- Create: `kadre/contracts/driver/uikit/contracts/evidence.tsv`
- Create: `kadre/capabilities/uikit.md`
- Modify: `kadre/contracts/registry/contracts.tsv` (ligne BCK-010, `active`)
- Modify: `kadre/contracts/validator/build.gradle.kts` (liste `uikitContractIds` + tâches generate/validate + registry args)
- Modify: `kadre/contracts/driver/uikit/build.gradle.kts` (NOUVEAU fichier module driver — tâches Exec xcodebuild + conversion)
- Modify: `settings.gradle.kts` (include `:kadre:contracts:driver:uikit`)
- Test: validateur vert sur les artifacts produits ; preuves can-fail (mutation sentinelle + suppression de mapping) avec sorties citées.

**Interfaces:**
- Consumes: `build/xcresult/{ios,tvos}.xcresult` (Task 5) ; `JUnitEvidence.kt` lit `TEST-*.xml` (classname/name/time, `<failure>`, `<skipped>`) ; tâches appkit du validateur comme gabarit (build.gradle.kts:88–158).
- Produces: `kadre/contracts/driver/uikit/build/contract-evidence/<target>/contract-evidence/BCK-010.json` + `.../test-results/<target>/TEST-*.xml` ; tâches Gradle `generateUikitBCK010IosSimulatorArm64ContractEvidence` / `...TvosSimulatorArm64...` + `validateIosSimulatorArm64UikitContractEvidence` / `...Tvos...` + agrégat `generateUikitContractEvidence` ; ligne registre BCK-010 `active`.

- [ ] **Step 1: Module Gradle du driver + tâches xcodebuild**

`settings.gradle.kts` : `include(":kadre:contracts:driver:uikit")`.
`kadre/contracts/driver/uikit/build.gradle.kts` — module vide (pas de Kotlin) qui orchestre :

```kotlin
// Driver UIKit : orchestre la preuve O3 par famille. macOS-only, comme le gate AppKit ;
// jamais câblé dans :kadre:check.
val worktreeRoot = rootProject.projectDir

fun simulatorTestTask(name: String, scheme: String, destination: String, xcresult: String, frameworkTasks: List<String>) =
    tasks.register<Exec>(name) {
        dependsOn(frameworkTasks)
        workingDir = file("$worktreeRoot/kadre/contracts/driver/uikit")
        // xcodebuild refuse un -resultBundlePath déjà existant : purge avant chaque exécution.
        doFirst { delete(xcresult) }
        commandLine(
            "xcodebuild",
            "-project", "KadreUikitDriver.xcodeproj",
            "-scheme", scheme,
            "-destination", destination,
            "-resultBundlePath", xcresult,
            "CODE_SIGNING_ALLOWED=NO",
            "test",
        )
    }

val iosSimulatorTests = simulatorTestTask(
    "iosSimulatorTests", "KadreUikitDriverIos",
    "platform=iOS Simulator,name=iPhone 17,OS=27.0",
    "build/xcresult/ios.xcresult",
    listOf(":kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64"),
)
val tvosSimulatorTests = simulatorTestTask(
    "tvosSimulatorTests", "KadreUikitDriverTvOs",
    "platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0",
    "build/xcresult/tvos.xcresult",
    listOf(":kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64"),
)

tasks.register("simulatorTests") {
    dependsOn(iosSimulatorTests, tvosSimulatorTests)
}
```

(L'implémenteur vérifie que `xcodebuild` est atteignable depuis l'Exec (PATH) et que `-resultBundlePath` relatif résout depuis `workingDir` ; ajuster en chemin absolu si nécessaire.)

- [ ] **Step 2: Exporteur XCTest→JUnit (python3, stdlib seule)**

`kadre/contracts/driver/uikit/tools/xctest_to_junit.py` — ÉTAPE DE DÉCOUVERTE OBLIGATOIRE d'abord : exécuter

```bash
xcrun xcresulttool get test-results tests --path build/xcresult/ios.xcresult --format json > /tmp/xcresult-schema.json
```

et RELIRE le JSON réel (le schéma varie par Xcode ; on code contre le schéma observé à Xcode 27, avec un adaptateur isolé). Sortie JUnit exigée par `JUnitEvidence.kt` : un `TEST-<suite>.xml` par classe de test, `testsuite name=<classe> tests= failures= errors= skipped= time=`, `testcase classname=<classe> name=<méthode> time=<décimal secondes>`, échec = `<failure message="...">`, skip = `<skipped message="...">`.

```python
#!/usr/bin/env python3
"""Export XCTest xcresult to JUnit XML preserving names, failures, skips and duration.

Usage: xctest_to_junit.py <xcresult> <output-dir>
Reads `xcrun xcresulttool get test-results tests --format json` and writes one
TEST-<suite>.xml per test class into <output-dir>. Schema adapter lives in
`parse_nodes` — re-verify against the local Xcode's output when it drifts.
"""
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def parse_nodes(document):
    """Adaptateur de schéma xcresulttool (Xcode 27). Retourne [(classname, name, time, status, message)].

    status ∈ {"passed", "failed", "skipped"} ; message = texte d'échec/skip ou "".
    """
    cases = []

    def walk(node, suite):
        node_type = node.get("nodeType", "")
        name = node.get("name", "")
        if node_type == "Test Case":
            result = node.get("result", "").lower()
            duration = str(node.get("duration", "0"))
            message = ""
            if result == "failed":
                # les détails d'échec vivent dans les enfants (failure messages) ou `node.get("failures")`
                failures = node.get("failures") or []
                message = "; ".join(f.get("failureText", "") for f in failures if isinstance(f, dict))
            cases.append((suite, name, duration, result, message))
            return
        child_suite = name if node_type in ("Test Suite", "Suite") else suite
        for child in node.get("children", []):
            walk(child, child_suite)

    for top in document.get("testNodes", []):
        walk(top, "")
    return cases


def write_junit(cases, output_dir):
    by_class = {}
    for classname, name, duration, status, message in cases:
        by_class.setdefault(classname, []).append((name, duration, status, message))
    output_dir.mkdir(parents=True, exist_ok=True)
    for classname, items in by_class.items():
        suite = ET.Element("testsuite", {
            "name": classname,
            "tests": str(len(items)),
            "failures": str(sum(1 for _, _, s, _ in items if s == "failed")),
            "errors": "0",
            "skipped": str(sum(1 for _, _, s, _ in items if s == "skipped")),
        })
        for name, duration, status, message in items:
            case = ET.SubElement(suite, "testcase", {"classname": classname, "name": name, "time": duration})
            if status == "failed":
                ET.SubElement(case, "failure", {"message": message, "type": "XCTestFailure"})
            elif status == "skipped":
                ET.SubElement(case, "skipped", {"message": message})
        ET.ElementTree(suite).write(output_dir / f"TEST-{classname}.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    xcresult, output = Path(sys.argv[1]), Path(sys.argv[2])
    raw = subprocess.run(
        ["xcrun", "xcresulttool", "get", "test-results", "tests", "--path", str(xcresult), "--format", "json"],
        check=True, capture_output=True, text=True,
    ).stdout
    write_junit(parse_nodes(json.loads(raw)), output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

L'implémenteur ajuste `parse_nodes` au schéma réellement observé (étape découverte) et ajoute un test local : convertir `ios.xcresult`, relire le XML, vérifier 2 testcases nommés exactement `KadreUikitDriverTests/testKotlinProbeObservesRealWindow` etc. avec `time` non vide.

- [ ] **Step 3: Brancher la conversion dans le driver**

Dans `kadre/contracts/driver/uikit/build.gradle.kts`, après chaque tâche xcodebuild :

```kotlin
fun junitExportTask(name: String, xcresult: String, output: String, upstream: TaskProvider<Exec>) =
    tasks.register<Exec>(name) {
        dependsOn(upstream)
        workingDir = file("$worktreeRoot/kadre/contracts/driver/uikit")
        commandLine("python3", "tools/xctest_to_junit.py", xcresult, output)
    }

val iosJunit = junitExportTask(
    "iosSimulatorJunitExport", "build/xcresult/ios.xcresult",
    "build/test-results/iosSimulatorArm64", iosSimulatorTests,
)
val tvosJunit = junitExportTask(
    "tvosSimulatorJunitExport", "build/xcresult/tvos.xcresult",
    "build/test-results/tvosSimulatorArm64", tvosSimulatorTests,
)
// simulatorTests dépend aussi des exports :
tasks.named("simulatorTests") { dependsOn(iosJunit, tvosJunit) }
```

- [ ] **Step 4: evidence.tsv (mapping exact, lu depuis le XML produit — jamais deviné)**

`kadre/contracts/driver/uikit/contracts/evidence.tsv` (TAB réels ; `target` = la target Kotlin exécutante) :

```
contractId	target	kind	evidenceId	testClass	testName
BCK-010	iosSimulatorArm64	scenario	uikit-driver-observes-real-window	KadreUikitDriverTests	testKotlinProbeObservesRealWindow()
BCK-010	iosSimulatorArm64	scenario	uikit-driver-reports-detached-view	KadreUikitDriverTests	testProbeReportsViewOutsideWindowAsDetached()
BCK-010	tvosSimulatorArm64	scenario	uikit-driver-observes-real-window	KadreUikitDriverTests	testKotlinProbeObservesRealWindow()
BCK-010	tvosSimulatorArm64	scenario	uikit-driver-reports-detached-view	KadreUikitDriverTests	testProbeReportsViewOutsideWindowAsDetached()
BCK-010	iosSimulatorArm64	sentinel	uikit-driver-window-membership-sentinel	KadreUikitDriverTests	testProbeReportsViewOutsideWindowAsDetached()
BCK-010	tvosSimulatorArm64	sentinel	uikit-driver-window-membership-sentinel	KadreUikitDriverTests	testProbeReportsViewOutsideWindowAsDetached()
```

Étape obligatoire : relire `build/test-results/<target>/TEST-KadreUikitDriverTests.xml` et reporter les attributs `classname`/`name` EXACTS (les noms Swift ont peut-être des parenthèses/params — l'evidence.tsv doit les refléter mot pour mot).

- [ ] **Step 5: Registre — BCK-010 active**

`kadre/contracts/registry/contracts.tsv` — relire 3 lignes existantes pour le vocabulaire des colonnes `source`/`subject`/`risk`/`oracle`/`scenarios`/`conditionalCapabilities`/`sentinels`, puis appendre (TAB réels) :

```
BCK-010	active	kadre/contracts/driver/uikit	UIKit driver phase 0 first proof	<risk: même vocabulaire que les rows APK>	O3	uikit-driver-observes-real-window,uikit-driver-reports-detached-view	iosSimulatorArm64,tvosSimulatorArm64	-	uikit-driver-window-membership-sentinel	-
```

- [ ] **Step 6: Valideur — liste uikit + tâches generate/validate**

`kadre/contracts/validator/build.gradle.kts` — miroir du bloc AppKit (lignes 88–158) :

```kotlin
val uikitContractIds = listOf("BCK-010")
```

— l'ajouter à `contractEvidenceGateIds` (ligne 32) ; args de `validateContractRegistry` (lignes 69–71) : ajouter `kadre/contracts/driver/uikit/contracts/evidence.tsv` ; puis (adapter les noms d'arguments exacts en relisant `GenerateContractEvidence.kt`/`ValidateContractEvidence.kt` — mêmes arguments que les tâches AppKit, chemins et target différents) :

```kotlin
data class UikitTarget(val target: String, val junitDir: String)

val uikitTargets = listOf(
    UikitTarget("iosSimulatorArm64", "$rootDir/kadre/contracts/driver/uikit/build/test-results/iosSimulatorArm64"),
    UikitTarget("tvosSimulatorArm64", "$rootDir/kadre/contracts/driver/uikit/build/test-results/tvosSimulatorArm64"),
)

uikitContractIds.forEach { id ->
    uikitTargets.forEach { t ->
        val capitalized = t.target.replaceFirstChar { it.uppercase() }
        tasks.register<JavaExec>("generateUikit${id.replace("-", "")}${capitalized}ContractEvidence") {
            dependsOn(":kadre:contracts:driver:uikit:simulatorTests")
            classpath(sourceSets["main"].runtimeClasspath)
            mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
            // args = mêmes noms que la tâche AppKit correspondante, avec adapter "uikit-driver",
            // target t.target, evidence.tsv uikit, junit dir t.junitDir,
            // artifact dir "kadre/contracts/driver/uikit/build/contract-evidence/${t.target}",
            // commit = kadreContractCommit (déjà calculé lignes 46–58).
        }
    }
}
tasks.register("generateUikitContractEvidence") {
    dependsOn(uikitContractIds.flatMap { id -> uikitTargets.map { t -> "generateUikit${id.replace("-", "")}${t.target.replaceFirstChar { c -> c.uppercase() }}ContractEvidence" } })
}
// validateIosSimulatorArm64UikitContractEvidence / validateTvosSimulatorArm64UikitContractEvidence :
// miroir de validateAppKitContractEvidence (lignes 121–151) par target, executions "junit".
```

NE PAS ajouter les tâches uikit à `check` (macOS-only) ; le gate passe par le script de Task 7.

- [ ] **Step 7: capabilities/uikit.md (snapshot initial honnête)**

`kadre/capabilities/uikit.md` — même format que `web.md` (7 colonnes `feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests`), avec en tête la matrice toolchain épinglée (Kotlin 2.4.20, Xcode 27.0, iOS 27.0/tvOS 27.0, planchers déclarés iOS 16.0/tvOS 16.0) et UNE entrée :

```markdown
| observation de conduite (sonde driver) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-010 (2 simulateurs) | tout le reste : non livré (attach, surface, input, IME, drop, displays, devices, capture) | `KadreUikitDriverTests` |
```

- [ ] **Step 8: Exécuter le gate complet + preuves can-fail**

```bash
./gradlew :kadre:contracts:driver:uikit:simulatorTests
./gradlew :kadre:contracts:validator:generateUikitContractEvidence
./gradlew :kadre:contracts:validator:validateIosSimulatorArm64UikitContractEvidence :kadre:contracts:validator:validateTvosSimulatorArm64UikitContractEvidence
```

Expected: JSON produits dans `build/contract-evidence/<target>/contract-evidence/BCK-010.json` (le champ sentinel `Killed` est déclaré depuis le mapping une fois la mutation passée — cf. mémoire système : le JSON seul ne démontre pas le kill, la mutation ci-dessous en est la preuve).

Preuves can-fail OBLIGATOIRES (sorties citées dans le rapport de tâche) :
1. **Sentinelle** : dans `KadreUikitProbe.observe`, remplacer la lecture `view.window === window` par `KadreUikitWindowMembership.Attached` inconditionnel ; relancer `./gradlew :kadre:contracts:driver:uikit:simulatorTests` → `testProbeReportsViewOutsideWindowAsDetached()` échoue sur les DEUX familles, `testKotlinProbeObservesRealWindow()` reste VERT (blast radius = son scénario, pas le préambule). Revert.
2. **Mapping** : supprimer une ligne d'`evidence.tsv`, relancer le validate → message de rejet spécifique cité. Rétablir.
3. **JUnit** : renommer un testcase dans le XML et relancer le validate → rejet cité. Rétablir.

- [ ] **Step 9: Commit d'activation (code+registre+evidence dans le MÊME commit)**

```bash
git add kadre/contracts/driver/uikit kadre/contracts/registry/contracts.tsv kadre/contracts/validator/build.gradle.kts kadre/capabilities/uikit.md
git commit -m "feat(uikit): activate BCK-010 with iOS and tvOS simulator evidence"
```

---

### Task 7: `scripts/test-uikit-simulator.sh` + workflow CI `kadre-uikit-contracts.yml`

**Files:**
- Create: `scripts/test-uikit-simulator.sh` (exécutable)
- Create: `.github/workflows/kadre-uikit-contracts.yml`
- Test: script vert en local sur `ios`, `tvos`, puis `all` ; workflow validé structurellement (actionlint si disponible, sinon relecture) ; échec fidèle testé en simulant une destination absente (env override).

**Interfaces:**
- Consumes: tâches Gradle de Task 6 ; destinations et vérifications de Task 5.
- Produces: point d'entrée reproductible unique (roadmap §7 : interface famille obligatoire, vérification toolchain, échec fidèle) ; gate PR à deux jobs simulateur indépendants + compilation/liaison des slices appareil.

- [ ] **Step 1: Le script**

`scripts/test-uikit-simulator.sh` :

```bash
#!/usr/bin/env bash
# UIKit contract gate (phase 0). Usage: test-uikit-simulator.sh [ios|tvos|all]
# Sélectionne explicitement la famille et sa destination, vérifie la toolchain,
# lance les tests, valide les preuves et retourne un code d'échec fidèle.
set -euo pipefail

FAMILY="${1:?usage: test-uikit-simulator.sh [ios|tvos|all]}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IOS_DESTINATION="${KADRE_UIKIT_IOS_DESTINATION:-platform=iOS Simulator,name=iPhone 17,OS=27.0}"
TVOS_DESTINATION="${KADRE_UIKIT_TVOS_DESTINATION:-platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0}"

command -v xcodebuild >/dev/null || { echo "FAIL: xcodebuild indisponible" >&2; exit 1; }
command -v xcrun >/dev/null || { echo "FAIL: xcrun indisponible" >&2; exit 1; }

require_destination() {
  local name="$1" os="$2" destination="$3"
  if ! xcrun simctl list devices available | grep -q "$name"; then
    echo "FAIL: simulateur '$name' ($os) introuvable — le job correspondant doit échouer." >&2
    exit 1
  fi
  echo "OK: destination $destination"
}

run_family() {
  case "$1" in
    ios)
      require_destination "iPhone 17" "iOS" "$IOS_DESTINATION"
      ./gradlew ":kadre:contracts:validator:generateUikitBCK010IosSimulatorArm64ContractEvidence" \
                ":kadre:contracts:validator:validateIosSimulatorArm64UikitContractEvidence"
      ;;
    tvos)
      require_destination "Apple TV 4K (3rd generation)" "tvOS" "$TVOS_DESTINATION"
      ./gradlew ":kadre:contracts:validator:generateUikitBCK010TvosSimulatorArm64ContractEvidence" \
                ":kadre:contracts:validator:validateTvosSimulatorArm64UikitContractEvidence"
      ;;
    *) echo "FAIL: famille inconnue '$1'" >&2; exit 1;;
  esac
}

case "$FAMILY" in
  all)
    run_family ios
    run_family tvos
    # Les slices appareil compilent et se lient (pas une preuve d'exécution — roadmap §7).
    ./gradlew ":kadre:platform:uikit:linkDebugFrameworkIosArm64" ":kadre:platform:uikit:linkDebugFrameworkTvosArm64"
    for target in iosSimulatorArm64 tvosSimulatorArm64; do
      artifact="kadre/contracts/driver/uikit/build/contract-evidence/$target/contract-evidence/BCK-010.json"
      [ -f "$artifact" ] || { echo "FAIL: artifact manquant: $artifact" >&2; exit 1; }
      echo "OK: $artifact"
    done
    ;;
  ios|tvos) run_family "$FAMILY" ;;
  *) echo "usage: test-uikit-simulator.sh [ios|tvos|all]" >&2; exit 1;;
esac

echo "uikit gate ($FAMILY): SUCCESS"
```

(`chmod +x`. Noms de tâches : vérifier les noms réellement générés par Task 6 et aligner — le script ne devine pas.)

- [ ] **Step 2: Le workflow CI**

`.github/workflows/kadre-uikit-contracts.yml` :

```yaml
name: kadre-uikit-contracts

on:
  push:
    branches: [master]
  pull_request:

jobs:
  uikit-ios-simulator:
    runs-on: macos-26
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew scripts/test-uikit-simulator.sh
      - run: ./scripts/test-uikit-simulator.sh ios
      - if: always()
        uses: actions/upload-artifact@v4
        with:
          name: uikit-ios-contract-artifacts
          path: |
            kadre/contracts/driver/uikit/build/contract-evidence/
            kadre/contracts/driver/uikit/build/test-results/
            kadre/contracts/driver/uikit/build/xcresult/
  uikit-tvos-simulator:
    runs-on: macos-26
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "25" }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew scripts/test-uikit-simulator.sh
      - run: ./scripts/test-uikit-simulator.sh tvos
      - if: always()
        uses: actions/upload-artifact@v4
        with:
          name: uikit-tvos-contract-artifacts
          path: |
            kadre/contracts/driver/uikit/build/contract-evidence/
            kadre/contracts/driver/uikit/build/test-results/
            kadre/contracts/driver/uikit/build/xcresult/
```

Deux jobs indépendants et obligatoires (roadmap §7) ; aucun `continue-on-error` ; timeout dur 15 min.
Vérification runner : si `macos-26` n'a pas les runtimes iOS/tvOS 27.0, le `require_destination` du script fait échouer le job — comportement exigé ; signaler au contrôleur la version runner réellement disponible pour ajustement explicite (pas de downgrade silencieux de destination).

- [ ] **Step 3: Exécuter et prouver l'échec fidèle**

```bash
./scripts/test-uikit-simulator.sh ios
./scripts/test-uikit-simulator.sh tvos
./scripts/test-uikit-simulator.sh all
KADRE_UIKIT_IOS_DESTINATION="platform=iOS Simulator,name=Inexistant,OS=27.0" ./scripts/test-uikit-simulator.sh ios; echo "exit=$?"
```

Expected: trois succès ; la dernière commande imprime `FAIL: simulateur ...` et `exit=1`.

- [ ] **Step 4: Commit**

```bash
git add scripts/test-uikit-simulator.sh .github/workflows/kadre-uikit-contracts.yml
git commit -m "ci(uikit): add the two-simulator UIKit contract gate"
```

---

### Task 8: Sweep du critère de sortie + note de phase 1

**Files:**
- Modify: aucun fichier produit obligatoire (rapport de sweep) ; possibles retouches mineures découvertes.
- Test: la checklist d'exit de la roadmap Phase 0, exécutée de bout en bout depuis un état propre.

**Interfaces:**
- Consumes: toutes les tasks.
- Produces: le verdict d'exit par item + la note « minimum de fenêtre à extraire pour la phase 1 » (livrable roadmap phase 0, checklist item 7).

- [ ] **Step 1: Sweep à froid**

Depuis un checkout propre du worktree (`git status` vide) :

```bash
./gradlew :kadre:foundation:compileKotlinIosArm64 :kadre:foundation:compileKotlinIosSimulatorArm64 :kadre:foundation:compileKotlinTvosArm64 :kadre:foundation:compileKotlinTvosSimulatorArm64
./gradlew :kadre:runtime:compileKotlinIosArm64 :kadre:runtime:compileKotlinIosSimulatorArm64 :kadre:runtime:compileKotlinTvosArm64 :kadre:runtime:compileKotlinTvosSimulatorArm64
./gradlew :kadre:platform:uikit:compileKotlinIosArm64 :kadre:platform:uikit:compileKotlinIosSimulatorArm64 :kadre:platform:uikit:compileKotlinTvosArm64 :kadre:platform:uikit:compileKotlinTvosSimulatorArm64
./gradlew :kadre:compileKotlinIosArm64 :kadre:compileKotlinIosSimulatorArm64 :kadre:compileKotlinTvosArm64 :kadre:compileKotlinTvosSimulatorArm64
./scripts/test-uikit-simulator.sh all
./gradlew :kadre:runtime:jvmTest
```

Expected: tout vert. (Découper en commandes < 9 min — Gradle per-command rule.)

- [ ] **Step 2: Checklist d'exit (roadmap Phase 0, critère de sortie)**

Vérifier et consigner dans le rapport : (1) compilation et linking des quatre targets — linking = frameworks `linkDebugFramework*` 4 slices (Task 4/7) ; (2) tests des primitives natives exécutés sur les deux simulateurs (rapports `build/test-results/iosSimulatorArm64Test` / `tvosSimulatorArm64Test` de runtime) ; (3) une preuve O3 par app UIKit iOS et tvOS, JSON/JUnit corrélés, artifacts distincts par cible ; (4) simulateur manquant ⇒ échec du job (prouvé Task 7 step 3) ; (5) AUCUNE prétention d'attach public (grep `KadreIos.attach` dans le code livré = 0 hit hors docs normatifs).

- [ ] **Step 3: Note de design « extraction fenêtre minimale phase 1 »**

Rédiger dans le rapport de sweep (et en tête du futur plan phase 1) : le minimum à extraire de `kadre/runtime/src/jvmMain` vers commonMain pour brancher `RuntimeSessionComponentsFactory` + `RuntimePrimarySurface` sous UIKit — candidats identifiés en exploration : `RuntimeWindowManager` (public, composable, backend-agnostique — candidat direct), `RuntimeWindowEventFlow`, `WindowCommandPort`, `SurfaceCommandPort`, `MinimalWindowSurface` (RuntimeWindowSurface). Contrainte : préserver les tests AppKit, ne pas dupliquer le reducer de fenêtre (roadmap Phase 1 item 1). La note CITE les signatures publiques exactes de ces classes (relire les fichiers).

- [ ] **Step 4: Commit final (si retouches) + PR**

```bash
git status --short   # devrait être vide hors rapports non suivis
git push -u origin zcode/sess-56645e3f   # fait par le contrôleur à l'étape de PR
```

---

## Séquencement et estimation

Tasks 1→2→3→4→5→6→7→8, séquentielles (chaque task consomme les interfaces de la précédente ; c'est une branche verticale). Coût SDD attendu comparable aux phases Web (~8 tasks, budget ~150M tokens sous-agents) — la Task 5 (intégration Xcode) et la Task 6 (validateur + mutations) sont les plus risquées ; les tasks 1–2 contiennent les sondes toolchain qui déblayent le terrain.
