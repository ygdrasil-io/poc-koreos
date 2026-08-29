# Task 2 — Window event scheduling and O2 evidence

## Delivered scope

- Added `RuntimeWindowEventFlow`, a Window-only scheduler. Geometry events are continuous and coalescable; property and close events are discrete FIFO barriers. The existing Surface scheduler is untouched.
- `RuntimeWindow` now installs the session `WindowDeliveryPolicy`, session event-stamp source, collector budget, and session failure handler.
- A managed native completion publishes `StateFlow` first, then `GeometryChanged`, then `PropertiesChanged` at the same revision with strictly increasing stamps and its `WindowOperationId`.
- `observeNativeUpdate` publishes a true external observation with `operationId = null` after publishing its effective state.
- `CloseSource` / `FailWindow` delivery overflow terminalises only that window with `KadreFailure.SourceOverflow(KadreResourceKind.Window)`; `FailSession` calls the session handler with the same failure.
- Recorded all reserved `WIN-001` O2 scenario and sentinel evidence in `kadre/runtime/contracts/evidence.tsv`. The registry row remains `planned`; no required IDs, CI gates, public capabilities, AppKit types, or FFI were changed.

## Tests

Added two JVM tests:

- `windowGeometryEventsFollowConfiguredDeliveryPolicy`
- `windowUpdatePublishesStateBeforeCorrelatedGeometryAndPropertiesEvents`

They exercise a real slow event collector for `Coalesced`, `Buffered(1, CloseSource)` and `Buffered(1, FailSession)`, plus state-before-event ordering, geometry-before-properties ordering, managed-operation correlation, and uncorrelated external observation.

## RED/GREEN record

The first RED execution, before production scheduler code, was:

```text
> Task :kadre:runtime:jvmTest FAILED

RuntimeWindowManagerTest[jvm] > windowUpdatePublishesStateBeforeCorrelatedGeometryAndPropertiesEvents[jvm] FAILED
    java.lang.AssertionError at RuntimeWindowManagerTest.kt:201

RuntimeWindowManagerTest[jvm] > windowGeometryEventsFollowConfiguredDeliveryPolicy[jvm] FAILED
    java.lang.AssertionError at RuntimeWindowManagerTest.kt:1642
45 tests completed, 2 failed

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':kadre:runtime:jvmTest'.
> There were failing tests. See the report at: file:///Users/chaos/.codex/worktrees/cf31/poc-koreos/kadre/runtime/build/reports/tests/jvmTest/index.html

BUILD FAILED in 2s
17 actionable tasks: 4 executed, 13 up-to-date
```

During that RED cycle, the first version of the new test helper asserted directly on `KadreResult` rather than its success value. The assertion was corrected before the scheduler implementation was compiled. The final GREEN command and exact output were:

```text
> Task :buildSrc:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :buildSrc:generateExternalPluginSpecBuilders UP-TO-DATE
> Task :buildSrc:extractPrecompiledScriptPluginPlugins UP-TO-DATE
> Task :buildSrc:generatePrecompiledScriptPluginAccessors UP-TO-DATE
> Task :buildSrc:generateScriptPluginAdapters UP-TO-DATE
> Task :buildSrc:compileKotlin UP-TO-DATE
> Task :buildSrc:compileJava NO-SOURCE
> Task :buildSrc:compileGroovy NO-SOURCE
> Task :buildSrc:pluginDescriptors UP-TO-DATE
> Task :buildSrc:processResources UP-TO-DATE
> Task :buildSrc:classes UP-TO-DATE
> Task :buildSrc:jar UP-TO-DATE
> Task :kadre:foundation:kmpPartiallyResolvedDependenciesChecker
> Task :kadre:foundation:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :kadre:foundation:compileKotlinJvm UP-TO-DATE
> Task :kadre:foundation:compileJvmMainJava NO-SOURCE
> Task :kadre:foundation:jvmProcessResources NO-SOURCE
> Task :kadre:foundation:processJvmMainResources SKIPPED
> Task :kadre:foundation:jvmMainClasses UP-TO-DATE
> Task :kadre:foundation:jvmJar UP-TO-DATE
> Task :kadre:runtime:kmpPartiallyResolvedDependenciesChecker
> Task :kadre:runtime:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :kadre:runtime:compileKotlinJvm UP-TO-DATE
> Task :kadre:runtime:compileJvmMainJava NO-SOURCE
> Task :kadre:runtime:jvmProcessResources NO-SOURCE
> Task :kadre:runtime:processJvmMainResources SKIPPED
> Task :kadre:runtime:jvmMainClasses UP-TO-DATE
> Task :kadre:runtime:jvmJar UP-TO-DATE
> Task :kadre:runtime:jvmTestProcessResources NO-SOURCE
> Task :kadre:runtime:processJvmTestResources SKIPPED
> Task :kadre:contracts:validator:kmpPartiallyResolvedDependenciesChecker
> Task :kadre:contracts:validator:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :kadre:contracts:validator:compileKotlinJvm UP-TO-DATE
> Task :kadre:contracts:validator:compileJvmMainJava NO-SOURCE
> Task :kadre:contracts:validator:jvmProcessResources NO-SOURCE
> Task :kadre:contracts:validator:processJvmMainResources SKIPPED
> Task :kadre:contracts:validator:jvmMainClasses UP-TO-DATE
> Task :kadre:contracts:validator:validateContractRegistry
> Task :kadre:runtime:compileTestKotlinJvm
> Task :kadre:runtime:compileJvmTestJava NO-SOURCE
> Task :kadre:runtime:jvmTestClasses
> Task :kadre:runtime:jvmTest

BUILD SUCCESSFUL in 1s
20 actionable tasks: 6 executed, 14 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

`git diff --check` also completed with exit code 0.

## Concern

The initial RED output is preserved above exactly. Its first assertion failure exposed a test-helper shape error before the old `MutableSharedFlow` behavior was reached; the corrected tests are included in the final GREEN suite.
