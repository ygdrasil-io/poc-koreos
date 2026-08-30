# AppKit Phase 5 — Window Fullscreen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** rendre `WindowUpdate.fullscreen` effectif sur AppKit avec une
completion terminale corrélée, un état effectif et des preuves CI, sans exposer
de FFI ni de boucle native supplémentaire.

**Architecture:** le runtime possède la barrière fullscreen, ses phases et
`desiredLevel`; il sérialise les mutations et ne complète un `apply` qu'après un
terminal natif. Le bridge AppKit traduit les callbacks de `NSWindowDelegate`
vers ces stimuli, effectue `toggleFullScreen:` sur le main thread et relit le
niveau effectif. La dernière PR rend uniquement la capability prouvée publique.

**Tech Stack:** Kotlin Multiplatform/JVM 25, coroutines, AppKit, KFFI généré,
JUnit et evidence contracts.

**Spec:** `kadre/APPKIT-PHASE-5-WINDOW-FULLSCREEN-DESIGN.md`

## Global Constraints

- KFFI est l'unique owner des bindings natifs. `toggleFullScreen:` et les
  callbacks `NSWindowDelegate` existent déjà dans le source généré ; ne créer
  ni selector Panama, ni downcall, ni wrapper FFI Kadre.
- Tout appel AppKit, readback et callback reste sur le main thread et franchit
  ensuite la queue de commandes AppKit avant le runtime.
- Une seule barrière fullscreen appartient à une fenêtre ; aucun callback ou
  state n'est partagé entre peers.
- `Window.apply(fullscreen)` ne retourne jamais `Accepted` sur AppKit.
- `Exclusive` reste refusé par champ à l'update et par requête à la création ;
  Kadre ne touche ni `collectionBehavior`, ni presentation options, ni écran.
- Les callbacks réentrants sont bufferisés jusqu'au retour de
  `toggleFullScreen:` ; le premier terminal FIFO fixe le résultat local.
- `WindowState` contient toujours des valeurs natives effectives. Une valeur
  de niveau illisible ferme la fenêtre plutôt que d'inventer `Normal`.
- Chaque PR conserve `WIN-005` et `APK-010` en `planned` jusqu'à la dernière
  carte ; aucune evidence ni gate ne doit être activée prématurément.

---

## PR 1 — Runtime privé et contrat O2

### Task 1: Ajouter les stimuli terminalisés et la barrière sans AppKit public

**Files:**

- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`
- Modify: `kadre/runtime/contracts/evidence.tsv`

**Consumes:** `WindowUpdate.fullscreen`, `WindowState.fullscreen`,
`WindowState.level`, la matrice de callbacks et les règles de précédence de la
spec.

**Produces:** des stimuli internes capables de publier un snapshot puis une
failure exacte, une barrière par `RuntimeWindow`, `desiredLevel`, et des tests
O2 déterministes. `WindowCapabilities.fullscreen` reste `Unsupported`.

- [ ] **Step 1: Écrire les tests runtime rouges du contrat d'admission**

  Ajouter dans `RuntimeWindowManagerTest` les tests suivants, en activant
  `WindowProperty.Fullscreen` seulement pour le manager de test et en utilisant
  `DeterministicWindowCommandPort`. Ajouter les deux helpers de test suivants
  près de `levelUpdateProperties()` et de `commit()` :

  ```kotlin
  private fun fullscreenProperties(): Set<WindowProperty> =
      DEFAULT_RUNTIME_WINDOW_UPDATE_PROPERTIES +
          setOf(WindowProperty.Fullscreen, WindowProperty.Level)

  private suspend fun openFullscreenWindow(
      manager: RuntimeWindowManager,
      port: DeterministicWindowCommandPort,
  ): Window = commit(
      manager.requestWindow(WindowSpec()).successValue(),
      port.openCommands.single(),
  )
  ```

  Puis écrire :

  ```kotlin
  @Test
  fun fullscreenRejectsClearMixedUpdatesAndExclusiveBeforeNativeDispatch() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)

      assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("fullscreen")),
          window.apply(WindowUpdate(fullscreen = PropertyChange.Clear)))
      assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("fullscreen")),
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless), title = PropertyChange.Set("mixed"))))
      val exclusive = assertIs<WindowUpdateOutcome.PartiallyApplied>(
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(exclusiveFullscreenFixture()))).successValue(),
      )
      assertEquals(setOf(WindowProperty.Fullscreen), exclusive.rejected.map { it.field }.toSet())
      assertTrue(port.updateCommands.isEmpty())
  }

  @Test
  fun fullscreenWaitsForDidThenPublishesOneCorrelatedStateAndEvent() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)
      val result = async(start = CoroutineStart.UNDISPATCHED) {
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
      }

      val command = port.updateCommands.single()
      assertFalse(result.isCompleted)
      command.applied(window.state.value.copy(fullscreen = FullscreenMode.Borderless))

      assertEquals(FullscreenMode.Borderless,
          assertIs<WindowUpdateOutcome.Applied>(result.await().successValue()).state.fullscreen)
  }
  ```

  Ajouter `exclusiveFullscreenFixture()` dans le fixture de test avec un
  `DisplayId` et un `DisplayMode` obtenus du fake `DisplayManager` de la
  session, plutôt que de construire ou d'exposer un identifiant de display
  public. Ajouter également des assertions de précédence : `stale + Clear` est
  `InvalidRequest`, tandis que `stale + Exclusive`, `stale + barrière` et
  `stale + availability unavailable` sont `StaleRevision`.

- [ ] **Step 2: Vérifier le RED**

  Run:

  ```text
  rtk ./gradlew :kadre:runtime:jvmTest \
    --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest.fullscreenRejectsClearMixedUpdatesAndExclusiveBeforeNativeDispatch \
    --no-daemon --console=plain
  ```

  Expected: FAIL ; le runtime ne route pas encore `fullscreen` et n'a pas de
  terminalité corrélée.

- [ ] **Step 3: Étendre le SPI interne avec les deux seules nouvelles issues**

  Dans `WindowCommandPort.kt`, ajouter ces deux variantes à
  `WindowUpdateCommandStimulus` et les helpers correspondants sur
  `WindowUpdateCommand` :

  ```kotlin
  public data class Failed(
      public val operationId: WindowOperationId,
      public val failure: KadreFailure,
  ) : WindowUpdateCommandStimulus

  public data class CommittedFailure(
      public val operationId: WindowOperationId,
      public val effectiveState: WindowState,
      public val publicationOperationId: WindowOperationId?,
      public val failure: KadreFailure,
  ) : WindowUpdateCommandStimulus
  ```

  Ajouter aussi, sur `WindowUpdateCommand`, les trois helpers privés au SPI :

  ```kotlin
  public fun fullscreenWill(target: FullscreenMode)
  public fun fullscreenDid(effectiveState: WindowState)
  public fun fullscreenDidFail(target: FullscreenMode)
  ```

  Ils alimentent le même ingress sérialisé que
  `acceptWindowFullscreenObservation` et conservent l'`operationId` du
  command. L'ingress sans command est réservé aux notifications externes.

  Faire accepter les deux branches dans
  `RuntimeWindowManager.acceptWindowUpdateStimulus`. `Failed` complète le
  pending update sans snapshot. `CommittedFailure` publie dans l'ordre état,
  événement, puis `KadreResult.Failure(failure)` ; si le waiter est détaché,
  reporter exactement `failure` une fois au `RuntimeFailureReporter`.

- [ ] **Step 4: Implémenter la machine runtime sous le même verrou que la file**

  Dans `RuntimeWindowManager.kt`, introduire des types privés localisés près de
  `PendingWindowUpdate` :

  ```kotlin
  private enum class FullscreenPhase { PreparedLocal, InvokingSelector, AwaitingLocal, External }

  private data class FullscreenBarrier(
      val operationId: WindowOperationId?,
      val target: FullscreenMode,
      var phase: FullscreenPhase,
      val terminalCallbacks: ArrayDeque<FullscreenTerminalCallback> = ArrayDeque(),
      var conflictTarget: FullscreenMode? = null,
  )
  ```

  Ajouter un ingress interne `acceptWindowFullscreenObservation(windowId,
  observation)` pour `Will`, `Did(state)` et `DidFail(target)`. Il doit :

  1. appliquer la matrice de la spec avant toute comparaison au state ;
  2. retenir les terminaux réentrants FIFO tant que `InvokingSelector` est
     actif ;
  3. libérer une barrière corrélée même si `Did` répète le state, sans révision
     ni événement dans ce seul cas ;
  4. publier les transitions externes avec `operationId = null` ;
  5. bloquer les updates non-fullscreen derrière une barrière externe, et
     terminer immédiatement un fullscreen concurrent par
     `TemporarilyUnavailable(retryable = true)` après la vérification de
     révision ;
  6. drainer la file seulement après la publication et la completion terminales.

  Ajouter `desiredLevel` à `RuntimeWindow`, initialisé par `WindowSpec.level`.
  Mettre cette intention à jour après tout `Set(level)` effectivement réussi,
  y compris un champ level réussi dans `PartiallyApplied`. Si le niveau demandé
  égale le niveau effectif mais diffère de `desiredLevel`, retourner
  `Applied(operationId, state)` sans setter, révision ni événement, puis
  réaligner seulement l'intention.

- [ ] **Step 5: Écrire et faire passer les tests de liveness et de failure**

  Ajouter des tests unitaires nommés explicitement :

  ```kotlin
  @Test
  fun fullscreenReentrantDidDoesNotDrainUntilTheSelectorReturns() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)
      port.onUpdate = { command ->
          command.fullscreenWill(FullscreenMode.Borderless)
          command.fullscreenDid(window.state.value.copy(fullscreen = FullscreenMode.Borderless))
          assertEquals(1, port.updateCommands.size)
      }

      assertIs<WindowUpdateOutcome.Applied>(
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless))).successValue(),
      )
  }

  @Test
  fun fullscreenFirstReentrantTerminalWinsAndLaterConflictIsExternal() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)
      val update = async(start = CoroutineStart.UNDISPATCHED) {
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
      }
      val command = port.updateCommands.single()
      command.fullscreenWill(FullscreenMode.Borderless)
      command.fullscreenDid(window.state.value.copy(fullscreen = FullscreenMode.Borderless))
      command.fullscreenDidFail(FullscreenMode.Borderless)
      assertEquals(FullscreenMode.Borderless, assertIs<WindowUpdateOutcome.Applied>(update.await().successValue()).state.fullscreen)
      manager.acceptWindowFullscreenObservation(window.id, WindowFullscreenObservation.Did(window.state.value.copy(fullscreen = FullscreenMode.Windowed)))
      assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
  }

  @Test
  fun fullscreenExternalDidReturningToCurrentStateStillReleasesBarrier() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)
      manager.acceptWindowFullscreenObservation(window.id, WindowFullscreenObservation.Will(FullscreenMode.Borderless))
      manager.acceptWindowFullscreenObservation(window.id, WindowFullscreenObservation.Did(window.state.value))
      val result = async(start = CoroutineStart.UNDISPATCHED) {
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
      }
      assertEquals(1, port.updateCommands.size)
      port.updateCommands.single().applied(window.state.value.copy(fullscreen = FullscreenMode.Borderless))
      assertIs<WindowUpdateOutcome.Applied>(result.await().successValue())
  }

  @Test
  fun fullscreenCommittedLevelRestoreFailurePublishesEffectiveStateBeforeFailure() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)
      val result = async(start = CoroutineStart.UNDISPATCHED) {
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
      }
      port.updateCommands.single().committedFailure(
          effectiveState = window.state.value.copy(fullscreen = FullscreenMode.Borderless, level = WindowLevel.Normal),
          publicationOperationId = port.updateCommands.single().operationId,
          failure = KadreFailure.PlatformFailure(KadrePlatform.Fake, "fullscreen", "level-restore-failed"),
      )
      assertEquals(FullscreenMode.Borderless, window.state.value.fullscreen)
      assertIs<KadreResult.Failure>(result.await())
  }

  @Test
  fun fullscreenLevelReadbackFailureClosesInsteadOfPublishingAnInventedLevel() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)
      val result = async(start = CoroutineStart.UNDISPATCHED) {
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
      }
      port.updateCommands.single().failed(
          KadreFailure.PlatformFailure(KadrePlatform.Fake, "fullscreen", "level-readback-failed"),
      )
      assertIs<KadreResult.Failure>(result.await())
      port.openCommands.single().nativeClosed()
      assertEquals(WindowPhase.Closed, window.state.value.phase)
  }

  @Test
  fun fullscreenExplicitEffectiveLevelSetRealignsDesiredLevelWithoutPublication() = runTest {
      val port = DeterministicWindowCommandPort()
      val manager = manager(port, enabledWindowUpdateCapabilities = fullscreenProperties())
      val window = openFullscreenWindow(manager, port)
      val setFloating = async(start = CoroutineStart.UNDISPATCHED) {
          window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating)))
      }
      port.updateCommands.single().applied(window.state.value.copy(level = WindowLevel.Floating))
      assertIs<WindowUpdateOutcome.Applied>(setFloating.await().successValue())
      manager.acceptWindowFullscreenObservation(
          window.id,
          WindowFullscreenObservation.Did(
              window.state.value.copy(fullscreen = FullscreenMode.Borderless, level = WindowLevel.Normal),
          ),
      )
      val revision = window.state.value.revision
      val realignment = window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Normal))).successValue()
      assertEquals(revision, realignment.state.revision)
      assertEquals(1, port.updateCommands.size)
      val nextFullscreen = async(start = CoroutineStart.UNDISPATCHED) {
          window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Windowed)))
      }
      assertEquals(WindowLevel.Normal, port.updateCommands.last().desiredLevel)
      port.updateCommands.last().applied(window.state.value.copy(fullscreen = FullscreenMode.Windowed))
      assertIs<WindowUpdateOutcome.Applied>(nextFullscreen.await().successValue())
  }
  ```

  Le fake port doit exposer un hook qui exécute synchroniquement les callbacks
  `Will`/`Did` pendant `requestUpdate`, afin de prouver qu'aucune seconde
  commande n'est dispatchée avant le retour de cet appel.

  Run:

  ```text
  rtk ./gradlew :kadre:runtime:jvmTest --no-daemon --console=plain
  ```

  Expected: PASS ; les tests de la matrice échoueraient si le terminal était
  identifié seulement par l'égalité de `WindowState.fullscreen`.

- [ ] **Step 6: Déclarer l'evidence O2 sans activer WIN-005**

  Dans `kadre/runtime/contracts/evidence.tsv`, ajouter les scénarios
  `runtime-window-fullscreen-validation`,
  `runtime-window-fullscreen-terminal-correlation`,
  `runtime-window-fullscreen-callback-matrix`,
  `runtime-window-fullscreen-level-readback` et
  `runtime-window-fullscreen-policy`. Ajouter les sentinelles du design :
  no-toggle-before-commit, no-double-toggle, stale-callback, barrier-release,
  committed-effective-state, cross-window et policy-bypass. Toutes les entrées
  restent sous `WIN-005` planned.

- [ ] **Step 7: Vérifier et committer la PR runtime**

  Run:

  ```text
  rtk ./gradlew :kadre:runtime:jvmTest \
    :kadre:contracts:validator:validateContractRegistry \
    --no-daemon --console=plain
  rtk git diff --check
  ```

  Commit:

  ```text
  rtk git add kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt \
    kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt \
    kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt \
    kadre/runtime/contracts/evidence.tsv
  rtk git commit -m "feat(runtime): terminalize fullscreen transitions"
  ```

## PR 2 — Bridge AppKit/KFFI privé

### Task 2: Raccorder le peer AppKit aux callbacks fullscreen générés

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`

**Consumes:** les stimuli runtime de PR 1, `NSWindow.toggleFullScreen:`,
`NSWindowDelegate.windowWill/Did/DidFail*FullScreen` déjà générés par KFFI.

**Produces:** un peer AppKit qui reporte les transitions locales et externes au
runtime, restaure `desiredLevel`, relit le snapshot natif et ne rend aucune
capability publique supportée.

- [ ] **Step 1: Écrire les tests de seam AppKit rouges**

  Étendre `DeterministicAppKitNativeWindowPort` pour enregistrer les demandes
  de toggle et émettre `WillEnter`, `DidEnter`, `DidFailEnter`, `WillExit`,
  `DidExit` et `DidFailExit`. Ajouter notamment :

  ```kotlin
  @Test
  fun fullscreenToggleWaitsForTheDelegateTerminalAndRestoresTheDesiredLevel() = runBlocking {
      val port = DeterministicAppKitNativeWindowPort(name = "fullscreen", effectiveLevel = WindowLevel.Floating)
      val driver = AppKitWindowRuntimeDriverFactory { port }.create(
          KadrePolicies.Default.resources,
          enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
      )
      val window = openedWindow(driver, WindowSpec(level = WindowLevel.Floating))
      val update = async { window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless))) }

      assertEquals(listOf(FullscreenMode.Borderless), port.fullscreenToggleTargets)
      assertFalse(update.isCompleted)
      port.emitDidEnter("fullscreen")
      assertEquals(FullscreenMode.Borderless, assertIs<WindowUpdateOutcome.Applied>(update.await().successValue()).state.fullscreen)
      assertEquals(WindowLevel.Floating, port.level("fullscreen"))
  }
  ```

  Ajouter le test d'un `DidFailEnter` qui préserve `Windowed`, celui d'un
  `Will`/`Did` externe avec `operationId = null`, et le test de fermeture qui
  révoque la delegate avant d'en libérer le receiver.

- [ ] **Step 2: Vérifier le RED**

  Run:

  ```text
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest.fullscreenToggleWaitsForTheDelegateTerminalAndRestoresTheDesiredLevel \
    --no-daemon --console=plain
  ```

  Expected: FAIL ; le seam ne porte ni la cible fullscreen ni les callbacks de
  delegate.

- [ ] **Step 3: Étendre le seam et le peer sans contourner la queue AppKit**

  Dans `AppKitNativeWindowPort.kt`, ajouter une cible fullscreen privée et une
  opération native explicite :

  ```kotlin
  internal data class AppKitWindowFullscreenTarget(val mode: FullscreenMode)

  internal fun interface AppKitFullscreenCallbackSink {
      fun accept(callback: AppKitFullscreenCallback)
  }
  ```

  Le peer doit appeler `beforeFirstSetter()` avant `toggleFullScreen:` et ne
  pas compléter la mutation à ce point. Il conserve la commande jusqu'au
  callback terminal, réapplique le `desiredLevel` reçu du runtime, puis appelle
  `readWindow`. Une exception du selector avant tout `Will` devient
  `selector-threw`; après un `Will`, elle laisse le runtime attendre le terminal.

  Étendre `AppKitWindowDelegateCallbacks`, `AppKitWindowCallbackGate` et
  `AppKitWindowStimulus` avec les six callbacks directionnels. Les callbacks
  passent tous par `AppKitWindowCommandQueue.submitFollowUp`; aucun callback
  Objective-C ne modifie directement un `StateFlow`.

- [ ] **Step 4: Implémenter exclusivement par KFFI généré**

  Dans `KffiAppKitWindowPort.kt` :

  ```kotlin
  private const val WINDOW_WILL_ENTER_FULLSCREEN = "windowWillEnterFullScreen:"
  private const val WINDOW_DID_ENTER_FULLSCREEN = "windowDidEnterFullScreen:"
  private const val WINDOW_DID_FAIL_ENTER_FULLSCREEN = "windowDidFailToEnterFullScreen:"
  ```

  Ajouter les six selectors à `windowDelegateClass`, avec
  `ObjCMethodSignatures.VoidObject`, et les déléguer au
  `KffiDelegateAdmission`. Déclencher le toggle uniquement par
  `NSWindow.toggleFullScreen(MemorySegment.NULL)`. Après `Did`, appliquer le
  niveau demandé via les fonctions KFFI déjà utilisées par `readLevel` et
  relire `NSWindow.level()` ; ne jamais déduire un niveau depuis l'intention.

  Avant la création de delegate ou l'appel au selector, utiliser
  `AppKitFullscreenAvailability` injectable : `10.6.8` ne charge ni symbole
  fullscreen ni observer ; `10.7.0` et supérieur l'autorisent. Les tests de
  comparaison couvrent `10.6.8`, `10.7.0`, `11.0` et `26.0`.

- [ ] **Step 5: Faire passer les preuves de bridge et les tests macOS ciblés**

  Ajouter à `KffiAppKitWindowPortMacOsTest` un test qui crée une fenêtre vraie,
  vérifie le main thread, entre/sort de fullscreen et constate les callbacks et
  le readback de niveau. Conserver une attente bornée et fermer la fenêtre dans
  `finally`; aucune assertion ne dépend d'une animation ou d'un espace visible.

  Run:

  ```text
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    :kadre:backend:appkit:appKitNativeTests \
    --no-daemon --console=plain
  ```

  Expected: PASS sur macOS ; les tests déterministes prouvent la machine, le
  test natif ne prouve que le bridge KFFI/AppKit.

- [ ] **Step 6: Déclarer l'evidence O3 privée et committer**

  Ajouter sous `APK-010` les scénarios
  `appkit-window-fullscreen-generated-delegate`,
  `appkit-window-fullscreen-terminal-callback`,
  `appkit-window-fullscreen-external-transition`,
  `appkit-window-fullscreen-level-readback` et
  `appkit-window-fullscreen-policy`, plus les sentinelles KFFI, cancellation,
  readback, cross-window et teardown. Le contrat reste planned.

  Commit:

  ```text
  rtk git add kadre/backend/appkit/src kadre/backend/appkit/contracts/evidence.tsv
  rtk git commit -m "feat(appkit): bridge terminal fullscreen callbacks"
  ```

## PR 3 — Activation publique, evidence et harness manuel

### Task 3: Publier seulement le fullscreen entièrement prouvé

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Create: `kadre/backend/appkit/manual/phase-5-fullscreen.md`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/contracts/validator/build.gradle.kts`
- Modify: `kadre/runtime/contracts/evidence.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `scripts/test-kadre-appkit-contracts.sh`
- Modify: `scripts/test-kadre-appkit-contract-driver.sh`
- Modify: `scripts/fixtures/fake-gradlew.sh`
- Modify: `kadre/APPKIT-PHASE-5-WINDOW-FULLSCREEN-DESIGN.md`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`

**Consumes:** les tests O2 de PR 1 et le bridge natif O3 de PR 2.

**Produces:** `WindowCapabilities.fullscreen` supportée pour `Borderless` sur
macOS >= 10.7, `WIN-005` et `APK-010` actifs, evidence JSON exigée par CI et un
harness manuel non bloquant.

- [ ] **Step 1: Écrire le test public rouge de capability et de parcours entier**

  Dans `AppKitBackendProviderTest`, vérifier en une seule session AppKit :

  ```kotlin
  @Test
  fun publicAppKitFullscreenCompletesWithOneCorrelatedEffectiveStateOnMacOs() = runBlocking {
      val window = openPublicAppKitWindow(WindowSpec(level = WindowLevel.Floating))
      assertEquals(
          Capability.Supported(setOf(FullscreenMode.Borderless), FeatureAvailability.Available),
          window.capabilities.value.fullscreen,
      )
      val result = window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
      assertEquals(FullscreenMode.Borderless, assertIs<WindowUpdateOutcome.Applied>(result.successValue()).state.fullscreen)
  }
  ```

  Couvrir aussi la capability `Unavailable(PlatformFailure(...,
  "os-version-unavailable"))` via l'injection de disponibilité, l'absence de
  support `Exclusive`, et l'événement externe `operationId = null`.

- [ ] **Step 2: Vérifier le RED**

  Run:

  ```text
  rtk ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest.publicAppKitFullscreenCompletesWithOneCorrelatedEffectiveStateOnMacOs \
    --no-daemon --console=plain
  ```

  Expected: FAIL ; `WindowProperty.Fullscreen` n'est pas encore dans les
  capabilities publiques AppKit.

- [ ] **Step 3: Activer la seule capability promise**

  Ajouter `WindowProperty.Fullscreen` à
  `APPKIT_PUBLIC_WINDOW_UPDATE_CAPABILITIES` seulement lorsque
  `AppKitFullscreenAvailability` est vraie. Faire exposer exactement
  `Supported({ Borderless }, Available)` ou `Supported({ Borderless },
  Unavailable(PlatformFailure(AppKit, "fullscreen", "os-version-unavailable")))`.
  L'initialisation `Borderless` et `Exclusive` reste rejetée par
  `requestWindow`, sans créer de peer ni observer.

- [ ] **Step 4: Ajouter le harness manuel honnête**

  Créer `manual/phase-5-fullscreen.md` avec une check-list courte : entrée
  locale, sortie locale, entrée par menu/raccourci, fermeture depuis fullscreen
  et vérification visuelle de retour à l'espace normal. Le harness écrit les
  valeurs réellement observées, ne peut pas activer un contrat et n'est jamais
  exécuté par CI.

- [ ] **Step 5: Activer les deux contrats atomiquement**

  Remplacer les lignes `planned` de `contracts.tsv` par les oracles, targets,
  scénarios et sentinelles réels. Ajouter `WIN-005` à `runtimeContractIds` et
  `APK-010` à `appKitContractIds`, puis `APK-010.json` aux deux scripts et à
  `fake-gradlew.sh`. Les identifiants de `evidence.tsv` doivent désigner les
  noms JUnit exacts écrits aux étapes précédentes.

- [ ] **Step 6: Exécuter les gates complètes et committer l'activation**

  Run:

  ```text
  rtk ./gradlew :kadre:runtime:jvmTest :kadre:backend:appkit:appKitNativeTests \
    :kadre:contracts:validator:generateRuntimeContractEvidence \
    :kadre:contracts:validator:generateAppKitContractEvidence \
    :kadre:contracts:validator:validateContractRegistry \
    --no-daemon --console=plain
  rtk ./scripts/test-kadre-appkit-contracts.sh
  rtk ./scripts/test-kadre-appkit-contract-driver.sh
  rtk git diff --check
  ```

  Expected: PASS ; les deux fichiers evidence sont non vides, les capabilities
  hors scope restent refusées et le harness manuel n'est pas une preuve CI.

  Commit:

  ```text
  rtk git add kadre/backend/appkit kadre/contracts kadre/runtime/contracts scripts \
    kadre/APPKIT-PHASE-5-WINDOW-FULLSCREEN-DESIGN.md \
    kadre/APPKIT-IMPLEMENTATION-ROADMAP.md
  rtk git commit -m "feat(appkit): activate correlated fullscreen"
  ```

## Self-review du plan

- La PR 1 couvre admission, précédence, buffering réentrant, callback externe,
  cancellation, failure committée, `desiredLevel` et preuves O2.
- La PR 2 couvre uniquement le bridge AppKit/KFFI, le main thread, les six
  callbacks, le readback et la révocation du delegate ; aucune capability ne
  devient publique.
- La PR 3 est le seul endroit où les capabilities et les gates changent. Elle
  ne confond pas harness manuel et evidence CI.
- Aucune tâche ne demande d'éditer des bindings générés. Si le preflight KFFI
  échoue sur une nouvelle version, arrêter cette stack et faire d'abord une
  évolution Kextract, puis une régénération et publication KFFI séparées.
