package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.RuntimeDesktopWindowHandleAccess
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import org.graphiks.kadre.surface.toPhysical
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequest
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppKitWindowRuntimeDriverTest {
    @Test
    fun fullscreenToggleWaitsForTheDelegateTerminalAndRestoresTheDesiredLevel() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen",
            effectiveLevel = WindowLevel.Floating,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen", level = WindowLevel.Floating))
            val update = async(start = CoroutineStart.UNDISPATCHED) {
                window.apply(
                    WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)),
                )
            }

            awaitFullscreenToggle(port)
            assertEquals(listOf<FullscreenMode>(FullscreenMode.Borderless), port.fullscreenToggleTargets)
            assertFalse(update.isCompleted)
            port.emitDidEnter("fullscreen")

            assertEquals(
                FullscreenMode.Borderless,
                assertIs<WindowUpdateOutcome.Applied>(update.await().successValue()).state.fullscreen,
            )
            assertEquals(WindowLevel.Floating, port.level("fullscreen"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun fullscreenRestoreFailureStillPublishesSuccessfulReadbackWithoutClosing() = runBlocking {
        val restoreFailure = IllegalStateException("restore")
        val reported = CopyOnWriteArrayList<Throwable>()
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-restore-failure",
            fullscreenRestoreFailure = restoreFailure,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter(reported::add),
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(
                driver,
                WindowSpec(title = "fullscreen-restore-failure", level = WindowLevel.Floating),
            )
            val update = async {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            awaitFullscreenToggle(port)

            port.emitDidEnter("fullscreen-restore-failure")

            assertEquals(
                fullscreenFailureFixture("level-restore-failed"),
                assertIs<KadreResult.Failure>(update.await()).reason,
            )
            assertEquals(FullscreenMode.Borderless, window.state.value.fullscreen)
            assertEquals(WindowLevel.Normal, window.state.value.level)
            assertEquals(WindowPhase.Open, window.state.value.phase)
            assertEquals(listOf(WindowLevel.Floating), port.fullscreenRestoreLevels)
            assertEquals(listOf("fullscreen-restore-failure"), port.fullscreenReadbackTitles)
            assertTrue(reported.isEmpty())
            assertTrue(port.closedWindowTitles.isEmpty())
        } finally {
            driver.close()
        }
    }

    @Test
    fun detachedFullscreenRestoreFailureIsReportedExactlyOnce() = runBlocking {
        val restoreFailure = IllegalStateException("restore")
        val reported = CopyOnWriteArrayList<Throwable>()
        val port = DeterministicAppKitNativeWindowPort(
            name = "detached-restore-failure",
            fullscreenRestoreFailure = restoreFailure,
        )
        val driver = fullscreenDriver(port, reported)

        try {
            val window = openedWindow(
                driver,
                WindowSpec(title = "detached-restore-failure", level = WindowLevel.Floating),
            )
            val update = async {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }

            awaitFullscreenToggle(port)
            update.cancelAndJoin()
            port.emitDidEnter("detached-restore-failure")

            withTimeout(2.seconds) {
                window.state.first { it.fullscreen == FullscreenMode.Borderless }
            }
            withTimeout(2.seconds) {
                while (reported.none { it is KadreException }) yield()
            }
            assertEquals(1, reported.size)
            assertEquals(
                fullscreenFailureFixture("level-restore-failed"),
                assertIs<KadreException>(reported.single()).failure,
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun fullscreenReadbackFailureRemainsDistinctAndClosesAfterRestoreFailure() = runBlocking {
        val restoreFailure = IllegalStateException("restore")
        val readbackFailure = IllegalStateException("readback")
        val reported = CopyOnWriteArrayList<Throwable>()
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-readback-failure",
            fullscreenRestoreFailure = restoreFailure,
            fullscreenReadbackFailure = readbackFailure,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter(reported::add),
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(
                driver,
                WindowSpec(title = "fullscreen-readback-failure", level = WindowLevel.Floating),
            )
            val update = async {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            awaitFullscreenToggle(port)

            port.emitDidEnter("fullscreen-readback-failure")

            assertEquals(
                fullscreenFailureFixture("level-readback-failed"),
                assertIs<KadreResult.Failure>(update.await()).reason,
            )
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
            assertEquals(listOf(WindowLevel.Floating), port.fullscreenRestoreLevels)
            assertEquals(listOf("fullscreen-readback-failure"), port.fullscreenReadbackTitles)
            assertEquals(listOf<Throwable>(readbackFailure), reported)
            assertEquals(listOf<Throwable>(restoreFailure), readbackFailure.suppressed.toList())
        } finally {
            driver.close()
        }
    }

    @Test
    fun mixedFullscreenTitleIsRejectedBeforeNativeDispatch() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-with-title")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(
                WindowProperty.Fullscreen,
                WindowProperty.Level,
                WindowProperty.Title,
            ),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "before-fullscreen"))
            val failure = assertIs<KadreResult.Failure>(
                window.apply(
                    WindowUpdate(
                        title = PropertyChange.Set("after-fullscreen"),
                        fullscreen = PropertyChange.Set(FullscreenMode.Borderless),
                    ),
                ),
            )

            assertEquals(KadreFailure.InvalidRequest("fullscreen"), failure.reason)
            assertTrue(port.fullscreenToggleLevels.isEmpty())
            assertEquals("before-fullscreen", window.state.value.title)
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
        } finally {
            driver.close()
        }
    }

    @Test
    fun floatingLevelIsNormalizedForFullscreenEntryAndExit() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-floating")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(
                driver,
                WindowSpec(title = "fullscreen-floating", level = WindowLevel.Floating),
            )
            val enter = async {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            awaitFullscreenToggle(port)
            assertEquals(listOf(WindowLevel.Normal), port.fullscreenToggleLevels)
            port.emitDidEnter("fullscreen-floating")
            assertIs<WindowUpdateOutcome.Applied>(enter.await().successValue())

            val exit = async {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Windowed)))
            }
            withTimeout(2.seconds) {
                while (port.fullscreenToggleLevels.size < 2) yield()
            }
            assertEquals(listOf(WindowLevel.Normal, WindowLevel.Normal), port.fullscreenToggleLevels)
            port.emitDidExit("fullscreen-floating")

            assertEquals(WindowLevel.Floating, assertIs<WindowUpdateOutcome.Applied>(exit.await().successValue()).state.level)
            assertEquals(WindowLevel.Floating, port.level("fullscreen-floating"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun selectorFailureRestoresLevelAndPublishesReadback() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-selector-readback",
            fullscreenToggleFailure = IllegalStateException("selector"),
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(
                WindowProperty.Fullscreen,
                WindowProperty.Level,
            ),
        )

        try {
            val window = openedWindow(
                driver,
                WindowSpec(title = "before-selector", level = WindowLevel.Floating),
            )
            val failure = assertIs<KadreResult.Failure>(
                window.apply(
                    WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)),
                ),
            ).reason

            assertEquals(fullscreenFailureFixture("selector-threw"), failure)
            assertEquals(listOf(WindowLevel.Normal), port.fullscreenToggleLevels)
            assertEquals("before-selector", window.state.value.title)
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
            assertEquals(WindowLevel.Floating, port.level("before-selector"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun selectorFailureDeduplicatesLateDidFailAndKeepsLateDidExternal() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-selector-terminal",
            fullscreenToggleFailure = IllegalStateException("selector"),
        )
        val reported = CopyOnWriteArrayList<Throwable>()
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter(reported::add),
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-selector-terminal"))
            val events = CopyOnWriteArrayList<WindowEvent>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { window.events.collect(events::add) }

            val failure = assertIs<KadreResult.Failure>(
                window.apply(
                    WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)),
                ),
            ).reason
            assertEquals(fullscreenFailureFixture("selector-threw"), failure)

            port.emitDidFailEnter("fullscreen-selector-terminal")
            port.emitDidEnter("fullscreen-selector-terminal")
            withTimeout(2.seconds) {
                window.state.first { it.fullscreen == FullscreenMode.Borderless }
                while (events.isEmpty()) yield()
            }

            assertTrue(reported.isEmpty())
            val event = assertIs<WindowEvent.PropertiesChanged>(events.single())
            assertEquals(setOf(WindowProperty.Fullscreen), event.changed)
            assertEquals(null, event.operationId)
            collector.cancelAndJoin()
        } finally {
            driver.close()
        }
    }

    @Test
    fun didFailPreservesWindowedStateWithoutOrdinaryMutation() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-did-fail-readback")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "before-did-fail"))
            val events = mutableListOf<WindowEvent>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { window.events.collect(events::add) }
            val update = async {
                window.apply(
                    WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)),
                )
            }

            awaitFullscreenToggle(port)
            port.emitDidFailEnter("before-did-fail")

            assertEquals(fullscreenFailureFixture("enter-failed"), assertIs<KadreResult.Failure>(update.await()).reason)
            assertEquals("before-did-fail", window.state.value.title)
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
            assertTrue(events.isEmpty())
            collector.cancelAndJoin()
        } finally {
            driver.close()
        }
    }

    @Test
    fun fullscreenDidFailEnterPreservesTheLastEffectiveWindowedState() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-failure")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-failure"))
            val update = async(start = CoroutineStart.UNDISPATCHED) {
                window.apply(
                    WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)),
                )
            }

            awaitFullscreenToggle(port)
            port.emitDidFailEnter("fullscreen-failure")

            val failure = assertIs<KadreResult.Failure>(update.await()).reason
            assertEquals(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "fullscreen", "enter-failed"),
                failure,
            )
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
        } finally {
            driver.close()
        }
    }

    @Test
    fun externalFullscreenWillAndDidPublishAnUncorrelatedEffectiveObservation() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-external")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-external"))
            val events = mutableListOf<WindowEvent>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.collect(events::add)
            }

            port.emitWillEnter("fullscreen-external")
            port.emitDidEnter("fullscreen-external")
            withTimeout(2.seconds) {
                window.state.first { it.fullscreen == FullscreenMode.Borderless }
                while (events.isEmpty()) yield()
            }

            val event = assertIs<WindowEvent.PropertiesChanged>(events.single())
            assertEquals(setOf(WindowProperty.Fullscreen), event.changed)
            assertEquals(null, event.operationId)
            collector.cancelAndJoin()
        } finally {
            driver.close()
        }
    }

    @Test
    fun runtimeOnlyLevelRealignmentControlsTheNextExternalFullscreenDid() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-external-realignment",
            effectiveLevel = WindowLevel.Normal,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(
                driver,
                WindowSpec(title = "fullscreen-external-realignment", level = WindowLevel.Floating),
            )
            port.emitWillEnter("fullscreen-external-realignment")
            port.emitDidEnter("fullscreen-external-realignment")
            withTimeout(2.seconds) {
                window.state.first {
                    it.fullscreen == FullscreenMode.Borderless && it.level == WindowLevel.Normal
                }
            }

            val revision = window.state.value.revision
            val realignment = assertIs<WindowUpdateOutcome.Applied>(
                window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Normal))).successValue(),
            )
            assertEquals(revision, realignment.state.revision)
            assertTrue(port.mutationTargets.isEmpty())
            port.forceEffectiveLevel(null)

            port.emitWillExit("fullscreen-external-realignment")
            port.emitDidExit("fullscreen-external-realignment")
            withTimeout(2.seconds) {
                window.state.first { it.fullscreen == FullscreenMode.Windowed }
            }

            assertEquals(
                listOf(WindowLevel.Floating, WindowLevel.Normal),
                port.fullscreenRestoreLevels,
            )
            assertEquals(WindowLevel.Normal, window.state.value.level)
        } finally {
            driver.close()
        }
    }

    @Test
    fun fullscreenDelegateCallbacksAreRevokedBeforeTheReceiverIsReleased() = runBlocking {
        val teardown = CopyOnWriteArrayList<String>()
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-teardown",
            onDelegateRevoked = { teardown += "revoked:$it" },
            onDelegateReleased = { teardown += "released:$it" },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        openedWindow(driver, WindowSpec(title = "fullscreen-teardown"))
        driver.close()

        assertEquals(
            listOf("revoked:fullscreen-teardown", "released:fullscreen-teardown"),
            teardown,
        )
    }

    @Test
    fun fullscreenSelectorThrowBeforeWillFailsWithoutPublishingAFalseState() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-selector-throw",
            fullscreenToggleFailure = IllegalStateException("selector"),
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-selector-throw"))
            val failure = assertIs<KadreResult.Failure>(
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless))),
            ).reason

            assertEquals(fullscreenFailureFixture("selector-threw"), failure)
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
        } finally {
            driver.close()
        }
    }

    @Test
    fun reentrantDidWithoutWillWinsBeforeSelectorThrow() = runBlocking {
        val reported = CopyOnWriteArrayList<Throwable>()
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-did-then-throw",
            fullscreenToggleFailure = IllegalStateException("selector"),
            reentrantFullscreenCallback = AppKitFullscreenCallback.DidEnter,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter(reported::add),
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-did-then-throw"))

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless))).successValue(),
            )

            assertEquals(FullscreenMode.Borderless, outcome.state.fullscreen)
            assertEquals(outcome.state, window.state.value)
            assertTrue(reported.isEmpty())
        } finally {
            driver.close()
        }
    }

    @Test
    fun reentrantDidFailWithoutWillWinsBeforeSelectorThrow() = runBlocking {
        val reported = CopyOnWriteArrayList<Throwable>()
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-did-fail-then-throw",
            fullscreenToggleFailure = IllegalStateException("selector"),
            reentrantFullscreenCallback = AppKitFullscreenCallback.DidFailEnter,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter(reported::add),
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-did-fail-then-throw"))

            val failure = assertIs<KadreResult.Failure>(
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless))),
            ).reason

            assertEquals(fullscreenFailureFixture("enter-failed"), failure)
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
            assertTrue(reported.isEmpty())
        } finally {
            driver.close()
        }
    }

    @Test
    fun fullscreenCancellationAfterToggleDetachesOnlyTheWaiterAndStillPublishesDid() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-cancel")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-cancel"))
            val update = async {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            awaitFullscreenToggle(port)
            update.cancelAndJoin()

            port.emitDidEnter("fullscreen-cancel")
            withTimeout(2.seconds) {
                window.state.first { it.fullscreen == FullscreenMode.Borderless }
            }

            assertEquals(listOf<FullscreenMode>(FullscreenMode.Borderless), port.fullscreenToggleTargets)
        } finally {
            driver.close()
        }
    }

    @Test
    fun fullscreenCancellationWhileAppKitQueueIsBusyStillWinsBeforeFirstSetter() = runBlocking {
        val beforeFirstSetter = CountDownLatch(1)
        val allowFirstSetter = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-pre-setter-cancellation",
            beforeFullscreenSetter = {
                beforeFirstSetter.countDown()
                check(allowFirstSetter.await(2, TimeUnit.SECONDS))
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(
                driver,
                WindowSpec(title = "fullscreen-pre-setter-cancellation", level = WindowLevel.Floating),
            )
            val before = window.state.value
            val update = async(Dispatchers.Default) {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            assertTrue(beforeFirstSetter.await(2, TimeUnit.SECONDS))

            update.cancelAndJoin()
            allowFirstSetter.countDown()
            assertIs<RuntimeDesktopWindowHandleAccess>(window).withDesktopHandle { Unit }.successValue()

            assertEquals(before, window.state.value)
            assertTrue(port.fullscreenToggleTargets.isEmpty())
            assertTrue(port.fullscreenToggleLevels.isEmpty())
        } finally {
            allowFirstSetter.countDown()
            driver.close()
        }
    }

    @Test
    fun cancellationWinningBeforeRuntimeSelectorAdmissionDoesNotToggle() = runBlocking {
        val port = pausingFullscreenPort("commit-cancellation")
        val driver = fullscreenDriver(port)

        try {
            val window = openedWindow(driver, WindowSpec(title = "commit-cancellation"))
            val update = async(Dispatchers.Default) {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }

            port.awaitCommitArbitration()
            update.cancelAndJoin()
            port.releaseCommitArbitration()
            assertIs<RuntimeDesktopWindowHandleAccess>(window).withDesktopHandle { Unit }.successValue()

            assertTrue(port.fullscreenToggleTargets.isEmpty())
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
        } finally {
            port.releaseCommitArbitration()
            driver.close()
        }
    }

    @Test
    fun cancelledFullscreenJobCannotClearTheNextPendingCommand() = runBlocking {
        val port = pausingFullscreenPort("commit-cancellation-next")
        val driver = fullscreenDriver(port)

        try {
            val window = openedWindow(driver, WindowSpec(title = "commit-cancellation-next"))
            val first = async(Dispatchers.Default) {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            port.awaitCommitArbitration()
            first.cancelAndJoin()

            val second = async(start = CoroutineStart.UNDISPATCHED) {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            port.releaseCommitArbitration()
            awaitFullscreenToggle(port)
            port.emitDidEnter("commit-cancellation-next")

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                withTimeout(2.seconds) { second.await() }.successValue(),
            )
            assertEquals(FullscreenMode.Borderless, outcome.state.fullscreen)
            assertEquals(listOf<FullscreenMode>(FullscreenMode.Borderless), port.fullscreenToggleTargets)
        } finally {
            port.releaseCommitArbitration()
            driver.close()
        }
    }

    @Test
    fun externalWillDuringCommitArbitrationWinsWithoutToggle() = runBlocking {
        val port = pausingFullscreenPort("commit-external-will")
        val driver = fullscreenDriver(port)

        try {
            val window = openedWindow(driver, WindowSpec(title = "commit-external-will"))
            val event = async(start = CoroutineStart.UNDISPATCHED) {
                window.events.filterIsInstance<WindowEvent.PropertiesChanged>().first()
            }
            val update = async(Dispatchers.Default) {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }

            port.awaitCommitArbitration()
            port.emitWillEnter("commit-external-will")
            port.releaseCommitArbitration()
            assertIs<RuntimeDesktopWindowHandleAccess>(window).withDesktopHandle { Unit }.successValue()
            port.emitDidEnter("commit-external-will")

            assertEquals(
                KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
                withTimeout(2.seconds) { update.await() },
            )
            assertEquals(null, withTimeout(2.seconds) { event.await() }.operationId)
            assertTrue(port.fullscreenToggleTargets.isEmpty())
        } finally {
            port.releaseCommitArbitration()
            driver.close()
        }
    }

    @Test
    fun externalWillClaimPreventsCancellationFromDispatchingASecondFullscreenToggle() = runBlocking {
        val willClaimed = CountDownLatch(1)
        val allowWillFollowUp = CountDownLatch(1)
        val reported = CopyOnWriteArrayList<Throwable>()
        val port = pausingFullscreenPort("commit-external-claim")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter(reported::add),
            publicAppKitCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
            beforeFullscreenFollowUpEnqueue = { callback ->
                if (callback == AppKitFullscreenCallback.WillEnter) {
                    willClaimed.countDown()
                    check(allowWillFollowUp.await(2, TimeUnit.SECONDS))
                }
            },
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "commit-external-claim"))
            val first = async(Dispatchers.Default) {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            port.awaitCommitArbitration()

            val externalWill = async(Dispatchers.Default) {
                port.emitWillEnter("commit-external-claim")
            }
            assertTrue(willClaimed.await(2, TimeUnit.SECONDS))
            first.cancelAndJoin()

            val second = async(start = CoroutineStart.UNDISPATCHED) {
                window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            port.releaseCommitArbitration()
            assertIs<RuntimeDesktopWindowHandleAccess>(window).withDesktopHandle { Unit }.successValue()

            assertTrue(port.fullscreenToggleTargets.isEmpty())

            allowWillFollowUp.countDown()
            withTimeout(2.seconds) { externalWill.await() }
            assertIs<RuntimeDesktopWindowHandleAccess>(window).withDesktopHandle { Unit }.successValue()
            port.emitDidEnter("commit-external-claim")

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                withTimeout(2.seconds) { second.await() }.successValue(),
            )
            assertEquals(FullscreenMode.Borderless, outcome.state.fullscreen)
            withTimeout(2.seconds) {
                while (reported.isEmpty()) yield()
            }
            assertEquals(
                KadreFailure.TemporarilyUnavailable(retryable = true),
                assertIs<KadreException>(reported.single()).failure,
            )
            assertTrue(port.fullscreenToggleTargets.isEmpty())
        } finally {
            port.releaseCommitArbitration()
            allowWillFollowUp.countDown()
            driver.close()
        }
    }

    @Test
    fun externalWillQueuedBeforeFullscreenCommitWinsWithoutADoubleToggle() = runBlocking {
        val queueBlocked = CountDownLatch(1)
        val allowQueue = CountDownLatch(1)
        val blockOnce = AtomicBoolean(true)
        val port = DeterministicAppKitNativeWindowPort(
            name = "fullscreen-pre-selector-will",
            beforeGeometrySetter = {
                if (blockOnce.compareAndSet(true, false)) {
                    queueBlocked.countDown()
                    check(allowQueue.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(
                WindowProperty.Fullscreen,
                WindowProperty.Level,
                WindowProperty.Title,
            ),
        )

        try {
            val blocker = openedWindow(driver, WindowSpec(title = "fullscreen-queue-blocker"))
            val target = openedWindow(driver, WindowSpec(title = "fullscreen-pre-selector-will"))
            val blockingUpdate = async(Dispatchers.Default) {
                blocker.apply(WindowUpdate(title = PropertyChange.Set("fullscreen-queue-released")))
            }
            assertTrue(queueBlocked.await(2, TimeUnit.SECONDS))

            port.emitWillEnter("fullscreen-pre-selector-will")
            val fullscreenUpdate = async(start = CoroutineStart.UNDISPATCHED) {
                target.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            }
            allowQueue.countDown()

            assertIs<WindowUpdateOutcome.Applied>(withTimeout(2.seconds) { blockingUpdate.await() }.successValue())
            assertEquals(
                KadreFailure.TemporarilyUnavailable(retryable = true),
                assertIs<KadreResult.Failure>(withTimeout(2.seconds) { fullscreenUpdate.await() }).reason,
            )
            assertIs<RuntimeDesktopWindowHandleAccess>(target).withDesktopHandle { Unit }.successValue()
            assertTrue(port.fullscreenToggleTargets.isEmpty())
            assertEquals(FullscreenMode.Windowed, target.state.value.fullscreen)
        } finally {
            allowQueue.countDown()
            driver.close()
        }
    }

    @Test
    fun fullscreenEnterThenExitUsesOneDirectionalTogglePerTerminal() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-exit")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val window = openedWindow(driver, WindowSpec(title = "fullscreen-exit"))
            val enter = async { window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless))) }
            awaitFullscreenToggle(port)
            port.emitDidEnter("fullscreen-exit")
            assertIs<WindowUpdateOutcome.Applied>(enter.await().successValue())

            val exit = async { window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Windowed))) }
            withTimeout(2.seconds) {
                while (port.fullscreenToggleTargets.size < 2) yield()
            }
            port.emitDidExit("fullscreen-exit")

            assertEquals(
                FullscreenMode.Windowed,
                assertIs<WindowUpdateOutcome.Applied>(exit.await().successValue()).state.fullscreen,
            )
            assertEquals(
                listOf<FullscreenMode>(FullscreenMode.Borderless, FullscreenMode.Windowed),
                port.fullscreenToggleTargets,
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun externalFullscreenCallbacksStayIsolatedToTheirNativePeer() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "fullscreen-cross-window")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
        )

        try {
            val first = openedWindow(driver, WindowSpec(title = "fullscreen-first"))
            val second = openedWindow(driver, WindowSpec(title = "fullscreen-second"))

            port.emitWillEnter("fullscreen-first")
            port.emitDidEnter("fullscreen-first")
            withTimeout(2.seconds) {
                first.state.first { it.fullscreen == FullscreenMode.Borderless }
            }

            assertEquals(FullscreenMode.Borderless, first.state.value.fullscreen)
            assertEquals(FullscreenMode.Windowed, second.state.value.fullscreen)
        } finally {
            driver.close()
        }
    }

    @Test
    fun peerForwardsGeometryUpdatesAndReturnsTheEffectiveNativeSnapshot() = runBlocking {
        val requestedSize = LogicalSize(640.0, 400.0)
        val effective = AppKitWindowGeometrySnapshot(
            contentSize = LogicalSize(624.0, 390.0),
            minimumSize = LogicalSize(200.0, 120.0),
            maximumSize = LogicalSize(900.0, 700.0),
            resizable = false,
        )
        val port = DeterministicAppKitNativeWindowPort(
            name = "effective-geometry",
            effectiveGeometry = effective,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "effective-geometry"))
                    .successValue()
                    .await(),
            ).window

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                window.apply(
                    WindowUpdate(
                        contentSize = PropertyChange.Set(requestedSize),
                        minimumSize = PropertyChange.Set(LogicalSize(200.0, 120.0)),
                        maximumSize = PropertyChange.Set(LogicalSize(900.0, 700.0)),
                        resizable = PropertyChange.Set(false),
                    ),
                ).successValue(),
            )

            assertEquals(effective.contentSize, outcome.state.contentSize)
            assertEquals(effective.minimumSize, outcome.state.minimumSize)
            assertEquals(effective.maximumSize, outcome.state.maximumSize)
            assertEquals(effective.resizable, outcome.state.resizable)
            assertEquals(
                listOf(
                    AppKitWindowGeometryTarget(
                        contentSize = PropertyChange.Set(requestedSize),
                        minimumSize = PropertyChange.Set(LogicalSize(200.0, 120.0)),
                        maximumSize = PropertyChange.Set(LogicalSize(900.0, 700.0)),
                        resizable = PropertyChange.Set(false),
                    ),
                ),
                port.geometryTargets,
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun peerForwardsTitleUpdatesAndReturnsTheNativeReadback() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "title-readback")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Title),
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "original"))
                    .successValue()
                    .await(),
            ).window

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                window.apply(WindowUpdate(title = PropertyChange.Set("requested"))).successValue(),
            )

            assertEquals("requested", outcome.state.title)
            assertEquals("requested", window.state.value.title)
        } finally {
            driver.close()
        }
    }

    @Test
    fun peerForwardsWindowLevelUpdatesAndReturnsTheEffectiveNativeReadback() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(
            name = "level-readback",
            effectiveLevel = WindowLevel.Modal,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Level),
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "level-readback"))
                    .successValue()
                    .await(),
            ).window

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating))).successValue(),
            )

            assertEquals(WindowLevel.Modal, outcome.state.level)
            assertEquals(WindowLevel.Modal, window.state.value.level)
            assertEquals(
                AppKitWindowLevelTarget(PropertyChange.Set(WindowLevel.Floating)),
                port.mutationTargets.single().level,
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun initialWindowLevelIsForwardedOnlyWhenThePrivateCapabilityIsEnabled() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "initial-level")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Level),
        )

        try {
            assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(
                    WindowSpec(title = "initial-level", level = WindowLevel.Floating),
                ).successValue().await(),
            )

            assertEquals(listOf(WindowLevel.Floating), port.createdWindowLevels)
        } finally {
            driver.close()
        }
    }

    @Test
    fun cancellationBeforeTheFirstLevelSetterLeavesThePeerUntouched() = runBlocking {
        val beforeFirstSetter = CountDownLatch(1)
        val allowFirstSetter = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "level-pre-setter-cancellation",
            beforeGeometrySetter = {
                beforeFirstSetter.countDown()
                check(allowFirstSetter.await(2, TimeUnit.SECONDS))
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Level),
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "level-pre-setter-cancellation"))
                    .successValue()
                    .await(),
            ).window
            val before = window.state.value
            val update = async(Dispatchers.Default) {
                window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating)))
            }
            assertTrue(beforeFirstSetter.await(2, TimeUnit.SECONDS))

            update.cancel()
            update.join()
            allowFirstSetter.countDown()

            assertTrue(update.isCancelled)
            assertEquals(before, window.state.value)
            assertEquals(WindowLevel.Normal, port.level("level-pre-setter-cancellation"))
        } finally {
            allowFirstSetter.countDown()
            driver.close()
        }
    }

    @Test
    fun levelUpdatesRemainPeerLocalAfterTheNativeLevelChanges() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "level-peer-isolation")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Level),
        )

        try {
            val first = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "level-first"))
                    .successValue()
                    .await(),
            ).window
            val second = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "level-second"))
                    .successValue()
                    .await(),
            ).window

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                first.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Modal))).successValue(),
            )

            assertEquals(WindowLevel.Modal, outcome.state.level)
            assertEquals(WindowLevel.Modal, first.state.value.level)
            assertEquals(WindowLevel.Normal, second.state.value.level)
            assertEquals(WindowLevel.Modal, port.level("level-first"))
            assertEquals(WindowLevel.Normal, port.level("level-second"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun peerForwardsCanonicalChromeUpdatesAndReadsBackTheEffectiveSnapshot() = runBlocking {
        val title = "chrome-readback"
        val unrelatedBits = 0b1_0000_0000L
        val port = DeterministicAppKitNativeWindowPort(
            name = title,
            initialStyleMask = unrelatedBits or APPKIT_RESIZABLE_STYLE_MASK,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = chromeUpdateProperties(),
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = title)).successValue().await(),
            ).window

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                window.apply(
                    WindowUpdate(
                        decorations = PropertyChange.Set(WindowDecorations.Borderless),
                        systemButtons = PropertyChange.Set(WindowSystemButtons.CloseOnly),
                    ),
                ).successValue(),
            )

            assertEquals(WindowDecorations.Borderless, outcome.state.decorations)
            assertEquals(WindowSystemButtons.None, outcome.state.systemButtons)
            assertEquals(
                AppKitWindowChromeTarget(
                    decorations = PropertyChange.Set(WindowDecorations.Borderless),
                    systemButtons = PropertyChange.Set(WindowSystemButtons.None),
                ),
                port.mutationTargets.single().chrome,
            )
            assertEquals(
                unrelatedBits or APPKIT_RESIZABLE_STYLE_MASK,
                port.styleMask(title),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun cancellationBeforeTheFirstChromeSetterLeavesThePeerUntouched() = runBlocking {
        val beforeFirstSetter = CountDownLatch(1)
        val allowFirstSetter = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "chrome-pre-setter-cancellation",
            beforeGeometrySetter = {
                beforeFirstSetter.countDown()
                check(allowFirstSetter.await(2, TimeUnit.SECONDS))
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = chromeUpdateProperties(),
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "chrome-pre-setter-cancellation"))
                    .successValue()
                    .await(),
            ).window
            val before = window.state.value
            val update = async(Dispatchers.Default) {
                window.apply(WindowUpdate(decorations = PropertyChange.Set(WindowDecorations.Borderless)))
            }
            assertTrue(beforeFirstSetter.await(2, TimeUnit.SECONDS))

            update.cancel()
            update.join()
            allowFirstSetter.countDown()

            assertTrue(update.isCancelled)
            assertEquals(before, window.state.value)
            assertEquals(WindowDecorations.System, port.chrome("chrome-pre-setter-cancellation").decorations)
            assertEquals(WindowSystemButtons.All, port.chrome("chrome-pre-setter-cancellation").systemButtons)
        } finally {
            allowFirstSetter.countDown()
            driver.close()
        }
    }

    @Test
    fun initialBorderlessChromeIsCanonicalizedBeforeThePeerIsCreated() = runBlocking {
        val title = "initial-borderless-chrome"
        val port = DeterministicAppKitNativeWindowPort(name = title)
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(
                    WindowSpec(
                        title = title,
                        decorations = WindowDecorations.Borderless,
                        systemButtons = WindowSystemButtons.CloseOnly,
                    ),
                ).successValue().await(),
            ).window

            assertEquals(WindowDecorations.Borderless, window.state.value.decorations)
            assertEquals(WindowSystemButtons.None, window.state.value.systemButtons)
            assertEquals(
                AppKitWindowChromeSnapshot(WindowDecorations.Borderless, WindowSystemButtons.None),
                port.chrome(title),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun cancellationWhileOwnerThreadWaitsBeforeFirstTitleSetterWithdrawsTheUpdate() = runBlocking {
        val beforeFirstSetter = CountDownLatch(1)
        val allowFirstSetter = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "title-pre-setter-cancellation",
            beforeGeometrySetter = {
                beforeFirstSetter.countDown()
                check(allowFirstSetter.await(2, TimeUnit.SECONDS))
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Title),
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "before-cancellation"))
                    .successValue()
                    .await(),
            ).window
            val before = window.state.value
            val update = async(Dispatchers.Default) {
                window.apply(WindowUpdate(title = PropertyChange.Set("must-not-commit")))
            }
            assertTrue(beforeFirstSetter.await(2, TimeUnit.SECONDS))

            update.cancel()
            update.join()
            allowFirstSetter.countDown()

            assertTrue(update.isCancelled)
            assertEquals(before, window.state.value)
        } finally {
            allowFirstSetter.countDown()
            driver.close()
        }
    }

    @Test
    fun titleUpdatesRemainPeerLocalAfterTheNativeTitleChanges() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "title-peer-isolation")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = setOf(WindowProperty.Title),
        )

        try {
            val first = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "first"))
                    .successValue()
                    .await(),
            ).window
            val second = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "second"))
                    .successValue()
                    .await(),
            ).window

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                first.apply(WindowUpdate(title = PropertyChange.Set("first-updated"))).successValue(),
            )

            assertEquals("first-updated", outcome.state.title)
            assertEquals("first-updated", first.state.value.title)
            assertEquals("second", second.state.value.title)
            assertEquals(1, port.mutationTargets.size)
        } finally {
            driver.close()
        }
    }

    @Test
    fun chromeUpdatesRemainPeerLocalAfterTheNativeStyleChanges() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort(name = "chrome-peer-isolation")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            enabledWindowUpdateCapabilities = chromeUpdateProperties(),
        )

        try {
            val first = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "chrome-first"))
                    .successValue()
                    .await(),
            ).window
            val second = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "chrome-second"))
                    .successValue()
                    .await(),
            ).window

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                first.apply(WindowUpdate(decorations = PropertyChange.Set(WindowDecorations.Borderless)))
                    .successValue(),
            )

            assertEquals(WindowDecorations.Borderless, outcome.state.decorations)
            assertEquals(WindowSystemButtons.None, first.state.value.systemButtons)
            assertEquals(WindowDecorations.System, second.state.value.decorations)
            assertEquals(WindowSystemButtons.All, second.state.value.systemButtons)
            assertEquals(
                AppKitWindowChromeSnapshot(WindowDecorations.Borderless, WindowSystemButtons.None),
                port.chrome("chrome-first"),
            )
            assertEquals(
                AppKitWindowChromeSnapshot(WindowDecorations.System, WindowSystemButtons.All),
                port.chrome("chrome-second"),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun peerSuppressesManagedResizeCallbacksButForwardsExternalGeometry() = runBlocking {
        val managed = AppKitWindowGeometrySnapshot(
            contentSize = LogicalSize(480.0, 300.0),
            minimumSize = null,
            maximumSize = null,
            resizable = true,
        )
        val external = managed.copy(contentSize = LogicalSize(512.0, 320.0))
        val port = DeterministicAppKitNativeWindowPort(
            name = "geometry-callbacks",
            effectiveGeometry = managed,
            emitGeometryDuringUpdate = true,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "geometry-callbacks"))
                    .successValue()
                    .await(),
            ).window

            window.apply(
                WindowUpdate(contentSize = PropertyChange.Set(managed.contentSize)),
            ).successValue()
            assertEquals(1L, window.state.value.revision.value)

            port.emitExternalGeometry("geometry-callbacks", external)
            val observed = withTimeout(2.seconds) {
                window.state.first { it.contentSize == external.contentSize }
            }

            assertEquals(2L, observed.revision.value)
            assertEquals(external.contentSize, observed.contentSize)
        } finally {
            driver.close()
        }
    }

    @Test
    fun staleReentrantGeometryCannotRollbackManagedCompletionAndLaterExternalGeometrySurvives() = runBlocking {
        val managed = AppKitWindowGeometrySnapshot(
            contentSize = LogicalSize(480.0, 300.0),
            minimumSize = null,
            maximumSize = null,
            resizable = true,
        )
        val stale = managed.copy(contentSize = LogicalSize(440.0, 280.0))
        val external = managed.copy(contentSize = LogicalSize(520.0, 340.0))
        val port = DeterministicAppKitNativeWindowPort(
            name = "reentrant-external-geometry",
            effectiveGeometry = managed,
            emitGeometryDuringUpdate = true,
            reentrantGeometryDuringUpdate = stale,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "reentrant-external-geometry"))
                    .successValue()
                    .await(),
            ).window

            window.apply(
                WindowUpdate(contentSize = PropertyChange.Set(managed.contentSize)),
            ).successValue()
            port.emitExternalGeometry("reentrant-external-geometry", external)
            val observed = withTimeout(2.seconds) {
                window.state.first { it.contentSize == external.contentSize }
            }

            assertEquals(2L, observed.revision.value)
            assertEquals(external.contentSize, observed.contentSize)
        } finally {
            driver.close()
        }
    }

    @Test
    fun cancellationWhileOwnerThreadWaitsBeforeFirstGeometrySetterWithdrawsTheUpdate() = runBlocking {
        val beforeFirstSetter = CountDownLatch(1)
        val allowFirstSetter = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "pre-setter-cancellation",
            beforeGeometrySetter = {
                beforeFirstSetter.countDown()
                check(allowFirstSetter.await(2, TimeUnit.SECONDS))
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "pre-setter-cancellation"))
                    .successValue()
                    .await(),
            ).window
            val before = window.state.value
            val update = async(Dispatchers.Default) {
                window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(480.0, 300.0))))
            }
            assertTrue(beforeFirstSetter.await(2, TimeUnit.SECONDS))

            update.cancel()
            update.join()
            allowFirstSetter.countDown()
            assertIs<RuntimeDesktopWindowHandleAccess>(window).withDesktopHandle { Unit }.successValue()

            assertTrue(update.isCancelled)
            assertEquals(before, window.state.value)
        } finally {
            allowFirstSetter.countDown()
            driver.close()
        }
    }

    @Test
    fun errorAfterContentSizeSetterPublishesReadbackAndRejectsOnlyUnappliedField() = runBlocking {
        val failure = IllegalStateException("resizable setter failed")
        val port = DeterministicAppKitNativeWindowPort(
            name = "post-setter-readback",
            geometryFailureAfterContentSize = failure,
        )
        val reported = mutableListOf<Throwable>()
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter(reported::add),
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "post-setter-readback"))
                    .successValue()
                    .await(),
            ).window
            val target = LogicalSize(480.0, 300.0)

            val outcome = assertIs<WindowUpdateOutcome.PartiallyApplied>(
                window.apply(
                    WindowUpdate(
                        contentSize = PropertyChange.Set(target),
                        resizable = PropertyChange.Set(false),
                    ),
                ).successValue(),
            )

            assertEquals(target, outcome.state.contentSize)
            assertTrue(outcome.state.resizable)
            assertEquals(outcome.state, window.state.value)
            assertEquals(WindowProperty.Resizable, outcome.rejected.single().field)
            assertIs<KadreFailure.PlatformFailure>(outcome.rejected.single().failure)
            assertEquals(listOf<Throwable>(failure), reported)
        } finally {
            driver.close()
        }
    }

    @Test
    fun queuedGeometryUpdateCompletesWhenCloseIsAdmittedBeforeNativeCommit() = runBlocking {
        val enteredHandle = CountDownLatch(1)
        val releaseHandle = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort("geometry-close-fence")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "geometry-close-fence"))
                    .successValue()
                    .await(),
            ).window
            val access = assertIs<RuntimeDesktopWindowHandleAccess>(window)
            val handle = async(Dispatchers.Default) {
                access.withDesktopHandle {
                    enteredHandle.countDown()
                    check(releaseHandle.await(2, TimeUnit.SECONDS))
                }
            }
            assertTrue(enteredHandle.await(2, TimeUnit.SECONDS))

            val update = async(start = CoroutineStart.UNDISPATCHED) {
                window.apply(WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(480.0, 300.0))))
            }
            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())

            releaseHandle.countDown()
            handle.await()
            assertIs<KadreResult.Failure>(withTimeout(2.seconds) { update.await() })
            assertEquals(emptyList(), port.geometryTargets)
        } finally {
            releaseHandle.countDown()
            driver.close()
        }
    }

    @Test
    fun peerPreservesUnrelatedStyleMaskBitsWhenChangingResizable() = runBlocking {
        val title = "style-mask"
        val unrelatedBits = 0b1010_0000L
        val port = DeterministicAppKitNativeWindowPort(
            name = title,
            initialStyleMask = unrelatedBits or APPKIT_RESIZABLE_STYLE_MASK,
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = title)).successValue().await(),
            ).window

            window.apply(WindowUpdate(resizable = PropertyChange.Set(false))).successValue()

            assertEquals(unrelatedBits, port.styleMask(title))
        } finally {
            driver.close()
        }
    }

    @Test
    fun inputForOneLivePeerDoesNotCrossIntoAnotherSurfaceOfTheSameDriver() = runBlocking {
        val firstPhysical = PhysicalKey.Unidentified("first-native-key")
        val firstPosition = LogicalPoint(7.0, 11.0)
        val port = DeterministicAppKitNativeWindowPort(
            name = "multi-window-input-isolation",
            inputObservationInstalledFor = setOf("first"),
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val first = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "first")).successValue().await(),
            ).window
            val second = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "second")).successValue().await(),
            ).window

            assertEquals(1L, first.surface.input.state.value.revision.value)
            assertEquals(0L, second.surface.input.state.value.revision.value)

            port.emitInput(
                "first",
                AppKitInput.KeyChanged(
                    firstPhysical,
                    LogicalKey.Unidentified("first-native-key"),
                    KeyLocation.Standard,
                    KeyState.Pressed,
                    repeat = false,
                    KeyboardModifiers(emptySet()),
                ),
            )
            port.emitInput("first", AppKitInput.PointerEntered(firstPosition))

            val firstInput = withTimeout(2.seconds) {
                first.surface.input.state.first { it.revision.value == 3L }
            }

            assertEquals(FeatureAvailability.Available, firstInput.capabilities.keyboard)
            assertEquals(FeatureAvailability.Available, firstInput.capabilities.pointer)
            assertEquals(setOf(firstPhysical), firstInput.keyboard.pressedKeys)
            assertEquals(firstPosition, firstInput.pointers.single().position)

            val secondInput = second.surface.input.state.value
            assertEquals(0L, secondInput.revision.value)
            assertEquals(FeatureAvailability.Unsupported, secondInput.capabilities.keyboard)
            assertEquals(FeatureAvailability.Unsupported, secondInput.capabilities.pointer)
            assertEquals(emptySet(), secondInput.keyboard.pressedKeys)
            assertEquals(emptyList(), secondInput.pointers)
        } finally {
            driver.close()
        }
    }

    @Test
    fun inputCallbacksBeforeCommitDrainInOrderAfterTheInitialSnapshot() = runBlocking {
        val physical = PhysicalKey.Unidentified("native-key-91")
        val logical = LogicalKey.Unidentified("native-key-91")
        val enteredAt = LogicalPoint(17.0, 23.0)
        val port = DeterministicAppKitNativeWindowPort(
            name = "pre-commit-input-callback",
            inputObservationInstalled = true,
            afterInputObservationBeforeCommit = { native ->
                native.emitInput(
                    "pre-commit",
                    AppKitInput.KeyChanged(
                        physical,
                        logical,
                        KeyLocation.Standard,
                        KeyState.Pressed,
                        repeat = false,
                        KeyboardModifiers(emptySet()),
                    ),
                )
                native.emitInput("pre-commit", AppKitInput.PointerEntered(enteredAt))
                native.emitSurfaceFocus("pre-commit", SurfaceFocus.Unfocused)
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "pre-commit"))
                    .successValue()
                    .await(),
            ).window
            val input = withTimeout(2.seconds) {
                window.surface.input.state.first { it.revision.value == 4L }
            }

            assertEquals(SurfaceFocus.Unfocused, window.surface.state.value.focus)
            assertEquals(FeatureAvailability.Available, input.capabilities.keyboard)
            assertEquals(FeatureAvailability.Available, input.capabilities.pointer)
            assertEquals(emptySet(), input.keyboard.pressedKeys)
            assertEquals(emptyList(), input.pointers)
        } finally {
            driver.close()
        }
    }

    @Test
    fun surfaceCallbacksAfterPeerActivationAndBeforeCommitAreDrainedSequentiallyAfterInitialSnapshot() = runBlocking {
        val initial = deterministicSurfaceSnapshot().copy(focus = SurfaceFocus.Unfocused)
        val resized = deterministicSurfaceSnapshot(
            logicalSize = LogicalSize(480.0, 270.0),
            scaleFactor = 1.5,
        ).metrics
        val port = DeterministicAppKitNativeWindowPort(
            name = "pre-commit-surface-callback",
            initialSurfaceSnapshot = initial,
            afterSurfaceActivationBeforeCommit = { native ->
                native.emitSurfaceFocus("pre-commit", SurfaceFocus.Focused)
                native.emitSurfaceMetrics("pre-commit", resized)
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
            publicSurfaceCapabilities = true,
        )

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "pre-commit"))
                    .successValue()
                    .await(),
            ).window

            val drained = withTimeout(2.seconds) {
                window.surface.state.first { it.revision.value == 2L }
            }
            assertEquals(SurfaceFocus.Focused, drained.focus)
            assertEquals(resized.logicalSize, drained.logicalSize)
            assertEquals(resized.physicalSize, drained.physicalSize)
        } finally {
            driver.close()
        }
    }

    @Test
    fun asynchronousDrainReservationKeepsWorkerAliveUntilCleanupIsSealed() {
        val taskStarted = CountDownLatch(1)
        val allowTaskToFinish = CountDownLatch(1)
        val taskFinished = CountDownLatch(1)
        val cleanupFinished = CountDownLatch(1)
        val queue = AppKitWindowCommandQueue { throw AssertionError(it) }

        assertTrue(queue.submit {
            taskStarted.countDown()
            check(allowTaskToFinish.await(2, TimeUnit.SECONDS))
            taskFinished.countDown()
        })
        assertTrue(taskStarted.await(2, TimeUnit.SECONDS))
        assertFalse(queue.beginMainThreadDrain())

        allowTaskToFinish.countDown()
        assertTrue(taskFinished.await(2, TimeUnit.SECONDS))
        assertTrue(queue.submitFollowUp { cleanupFinished.countDown() })

        queue.finishAsynchronousDrain()

        assertTrue(cleanupFinished.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun closeAbortsPreparedPeerThenClosesCommittedPeersInReverseAdmissionOrder() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort("session")
        val driver = AppKitWindowRuntimeDriverFactory { port }
            .create(KadrePolicies.Default.resources)
        driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
        driver.manager.requestWindow(WindowSpec(title = "second")).successValue().awaitOpened()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        assertFalse(pending.isCompleted)
        assertEquals(listOf("first", "second", "pending"), port.createdWindowTitles)

        driver.close()
        yield()

        assertEquals(WindowRequestOutcome.RequesterDetached, pending.await().successValue().await())
        assertEquals(listOf("pending", "second", "first"), port.closedWindowTitles)
        assertEquals(emptyList(), driver.manager.state.value.windows)
    }

    @Test
    fun closeAbortsInFlightPendingPeerBeforeCommittedPeers() = runBlocking {
        val preparationStarted = CountDownLatch(1)
        val allowPreparation = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "in-flight-pending",
            beforeCreateWindow = { spec ->
                if (spec.title == "pending") {
                    preparationStarted.countDown()
                    check(allowPreparation.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val driver = AppKitWindowRuntimeDriverFactory { port }
            .create(KadrePolicies.Default.resources)
        driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
        driver.manager.requestWindow(WindowSpec(title = "second")).successValue().awaitOpened()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        try {
            assertTrue(preparationStarted.await(2, TimeUnit.SECONDS))

            driver.close()
            allowPreparation.countDown()

            assertEquals(WindowRequestOutcome.RequesterDetached, pending.await().successValue().await())
            withTimeout(2.seconds) {
                driver.manager.state.first { state -> state.windows.isEmpty() }
                while (port.closedWindowTitles.size < 3) yield()
            }
            assertEquals(listOf("pending", "second", "first"), port.closedWindowTitles)
        } finally {
            allowPreparation.countDown()
            driver.close()
        }
    }

    @Test
    fun closeReservesCommitIssuedPendingCleanupBeforeCommittedPeers() = runBlocking {
        val commitReserved = CountDownLatch(1)
        val allowCommitDelivery = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort("commit-issued-pending")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            beforeCommitDelivery = { spec ->
                if (spec.title == "pending") {
                    commitReserved.countDown()
                    check(allowCommitDelivery.await(2, TimeUnit.SECONDS))
                }
            },
        )
        driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
        driver.manager.requestWindow(WindowSpec(title = "second")).successValue().awaitOpened()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        try {
            assertTrue(commitReserved.await(2, TimeUnit.SECONDS))
            val pendingRequest = withTimeout(2.seconds) { pending.await().successValue() }
            assertEquals(
                org.graphiks.kadre.window.WindowCancellationOutcome.TooLate,
                pendingRequest.cancel(),
            )

            driver.close()
            allowCommitDelivery.countDown()

            assertEquals(WindowRequestOutcome.RequesterDetached, pendingRequest.await())
            withTimeout(2.seconds) {
                while (port.closedWindowTitles.size < 3) yield()
            }
            assertEquals(listOf("pending", "second", "first"), port.closedWindowTitles)
        } finally {
            allowCommitDelivery.countDown()
            driver.close()
        }
    }

    @Test
    fun requesterCancellationAfterCommitReservationDoesNotRollBackTheOpenedWindow() = runBlocking {
        val commitReserved = CountDownLatch(1)
        val allowCommitDelivery = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort("commit-issued-cancellation")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            resources = KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
            beforeCommitDelivery = { spec ->
                if (spec.title == "committing") {
                    commitReserved.countDown()
                    check(allowCommitDelivery.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "committing"))
        }

        try {
            assertTrue(commitReserved.await(2, TimeUnit.SECONDS))
            val request = withTimeout(2.seconds) { pending.await().successValue() }

            assertEquals(org.graphiks.kadre.window.WindowCancellationOutcome.TooLate, request.cancel())
            allowCommitDelivery.countDown()

            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                withTimeout(2.seconds) { request.await() },
            ).window
            val access = assertIs<RuntimeDesktopWindowHandleAccess>(window)
            assertEquals(
                KadreResult.Success(Unit),
                access.withDesktopHandle { },
            )
            assertEquals(emptyList(), port.closedWindowTitles)
            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
            Unit
        } finally {
            allowCommitDelivery.countDown()
            driver.close()
        }
    }

    @Test
    fun factoryCreatesOneNativePortAndOneManagerForEachDriver() = runBlocking {
        val ports = ArrayDeque(
            listOf(
                DeterministicAppKitNativeWindowPort("first"),
                DeterministicAppKitNativeWindowPort("second"),
            ),
        )
        val createdPorts = mutableListOf<DeterministicAppKitNativeWindowPort>()
        val factory = AppKitWindowRuntimeDriverFactory {
            ports.removeFirst().also(createdPorts::add)
        }
        val first = factory.create(KadrePolicies.Default.resources)
        val second = factory.create(KadrePolicies.Default.resources)

        try {
            first.manager.requestWindow(WindowSpec(title = "only-first")).successValue().awaitOpened()

            assertEquals(2, createdPorts.size)
            assertEquals(listOf("only-first"), createdPorts[0].createdWindowTitles)
            assertEquals(emptyList(), createdPorts[1].createdWindowTitles)
            assertEquals(1, first.manager.state.value.windows.size)
            assertEquals(emptyList(), second.manager.state.value.windows)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun mainThreadNativeCloseCannotDeadlockAConcurrentWindowOpen() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("concurrent")
        val driver = AppKitWindowRuntimeDriverFactory { port }
            .create(KadrePolicies.Default.resources)
        val requestExecutor = newDaemonSingleThreadExecutor("kadre-request-test")
        val callbackEntered = CountDownLatch(1)
        val allowCallback = CountDownLatch(1)
        var completed = false
        var callback: Future<*>? = null
        var secondOpen: Future<*>? = null

        try {
            driver.manager.requestWindow(WindowSpec(title = "first")).successValue().awaitOpened()
            callback = port.submitOnOwnerThread {
                callbackEntered.countDown()
                check(allowCallback.await(2, TimeUnit.SECONDS))
                port.emitNativeClosed("first")
            }
            assertTrue(callbackEntered.await(2, TimeUnit.SECONDS))
            val foreignMainThreadCall = port.observeNextForeignMainThreadCall()
            secondOpen = requestExecutor.submit {
                runBlocking {
                    driver.manager.requestWindow(WindowSpec(title = "second"))
                        .successValue()
                        .awaitOpened()
                }
            }
            assertTrue(foreignMainThreadCall.await(2, TimeUnit.SECONDS))

            allowCallback.countDown()

            callback.get(2, TimeUnit.SECONDS)
            secondOpen.get(2, TimeUnit.SECONDS)
            withTimeout(2.seconds) {
                driver.manager.state.first { state ->
                    state.windows.singleOrNull()?.state?.value?.title == "second"
                }
            }
            completed = true
        } finally {
            allowCallback.countDown()
            callback?.cancel(true)
            secondOpen?.cancel(true)
            requestExecutor.shutdownNow()
            if (completed) driver.close()
            port.close()
        }
    }

    @Test
    fun programmaticClosePublishesOneTerminalNativeCloseAndReleasesTheWindowSlot() = runBlocking {
        val port = DeterministicAppKitNativeWindowPort("programmatic")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources.copy(maxWindowsPerSession = 1),
        )

        try {
            val request = driver.manager.requestWindow(WindowSpec(title = "first")).successValue()
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window

            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }

            assertEquals(emptyList(), driver.manager.state.value.windows)
            assertEquals(listOf("first"), port.closedWindowTitles)
            driver.manager.requestWindow(WindowSpec(title = "replacement")).successValue().awaitOpened()
        } finally {
            driver.close()
        }
    }

    @Test
    fun openedCleanupFailureStillTerminalisesOnceAndIsNeverPresentedAsReusable() = runBlocking {
        val cleanupFailure = IllegalStateException("native close failed")
        val port = DeterministicAppKitNativeWindowPort(
            name = "cleanup-failure",
            closeFailures = mapOf("failing" to cleanupFailure),
        )
        val reported = mutableListOf<Throwable>()
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            RuntimeFailureReporter(reported::add),
        )

        try {
            val request = driver.manager.requestWindow(WindowSpec(title = "failing")).successValue()
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window

            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }

            assertEquals(WindowCloseOutcome.Closed, window.close().successValue())
            assertEquals(emptyList(), driver.manager.state.value.windows)
            assertTrue(reported.contains(cleanupFailure))
        } finally {
            driver.close()
        }
    }

    @Test
    fun pendingCleanupFailureDuringDriverCloseDetachesInsteadOfReportingFalseCancellation() = runBlocking {
        val cleanupFailure = IllegalStateException("pending native close failed")
        val preparationStarted = CountDownLatch(1)
        val allowPreparation = CountDownLatch(1)
        val port = DeterministicAppKitNativeWindowPort(
            name = "pending-cleanup-failure",
            closeFailures = mapOf("pending" to cleanupFailure),
            beforeCreateWindow = { spec ->
                if (spec.title == "pending") {
                    preparationStarted.countDown()
                    check(allowPreparation.await(2, TimeUnit.SECONDS))
                }
            },
        )
        val reported = CopyOnWriteArrayList<Throwable>()
        val cleanupReported = CompletableDeferred<Unit>()
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            RuntimeFailureReporter { cause ->
                reported += cause
                if (cause === cleanupFailure) cleanupReported.complete(Unit)
            },
        )
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            driver.manager.requestWindow(WindowSpec(title = "pending"))
        }

        try {
            assertTrue(preparationStarted.await(2, TimeUnit.SECONDS))
            driver.close()
            allowPreparation.countDown()

            assertEquals(WindowRequestOutcome.RequesterDetached, pending.await().successValue().await())
            withTimeout(2.seconds) { cleanupReported.await() }
            assertTrue(reported.contains(cleanupFailure))
            assertEquals(emptyList(), driver.manager.state.value.windows)
        } finally {
            allowPreparation.countDown()
            driver.close()
        }
    }

    @Test
    fun nativeMainThreadDriverCloseDoesNotJoinAWorkerWaitingOnThatThread() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("main-thread-close")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(KadrePolicies.Default.resources)
        driver.manager.requestWindow(WindowSpec(title = "owned")).successValue().awaitOpened()
        val close = port.submitOnOwnerThread(driver::close)

        try {
            close.get(2, TimeUnit.SECONDS)
            assertEquals(listOf("owned"), port.closedWindowTitles)
            assertEquals(emptyList(), driver.manager.state.value.windows)
        } finally {
            close.cancel(true)
            port.close()
        }
    }

    @Test
    fun desktopHandleLeaseRunsOnTheOwnerThreadAndDelaysCloseUntilTheCallbackReturns() = runBlocking {
        val port = OwnerThreadAppKitNativeWindowPort("desktop-handle")
        val driver = AppKitWindowRuntimeDriverFactory { port }.create(
            KadrePolicies.Default.resources,
            publicAppKitCapabilities = true,
        )
        val callbackStarted = CountDownLatch(1)
        val allowCallbackToReturn = CountDownLatch(1)

        try {
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                driver.manager.requestWindow(WindowSpec(title = "leased")).successValue().await(),
            ).window
            val access = assertIs<RuntimeDesktopWindowHandleAccess>(window)
            val lease = async(Dispatchers.Default) {
                access.withDesktopHandle { handle ->
                    assertTrue(port.isMainThread())
                    assertEquals(RuntimeDesktopNativeWindowHandle.AppKit(0xA11uL, 0xB22uL), handle)
                    callbackStarted.countDown()
                    check(allowCallbackToReturn.await(2, TimeUnit.SECONDS))
                    "leased"
                }
            }
            assertTrue(callbackStarted.await(2, TimeUnit.SECONDS))

            assertIs<WindowCloseOutcome.Accepted>(window.close().successValue())
            assertEquals(emptyList(), port.closedWindowTitles)

            allowCallbackToReturn.countDown()
            assertEquals(KadreResult.Success("leased"), lease.await())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
            assertEquals(listOf("leased"), port.closedWindowTitles)
            assertEquals(
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
                access.withDesktopHandle { error("closed windows must not invoke a callback") },
            )
        } finally {
            allowCallbackToReturn.countDown()
            driver.close()
            port.close()
        }
    }
}

internal class DeterministicAppKitNativeWindowPort(
    private val name: String,
    private val closeFailures: Map<String, Throwable> = emptyMap(),
    private val beforeCreateWindow: (WindowSpec) -> Unit = { },
    private val onDelegateRevoked: (String) -> Unit = { },
    private val onDelegateReleased: (String) -> Unit = { },
    private val beforeCloseWindow: (String) -> Unit = { },
    private val initialSurfaceSnapshot: AppKitSurfaceSnapshot = deterministicSurfaceSnapshot(),
    private val afterSurfaceActivationBeforeCommit: (DeterministicAppKitNativeWindowPort) -> Unit = { },
    private val inputObservationInstalled: Boolean = false,
    private val inputObservationInstalledFor: Set<String> = emptySet(),
    private val afterInputObservationBeforeCommit: (DeterministicAppKitNativeWindowPort) -> Unit = { },
    private val effectiveGeometry: AppKitWindowGeometrySnapshot? = null,
    private val emitGeometryDuringUpdate: Boolean = false,
    private val reentrantGeometryDuringUpdate: AppKitWindowGeometrySnapshot? = null,
    private val initialStyleMask: Long? = null,
    effectiveLevel: WindowLevel? = null,
    private val fullscreenToggleFailure: Throwable? = null,
    private val reentrantFullscreenCallback: AppKitFullscreenCallback? = null,
    private val fullscreenRestoreFailure: Throwable? = null,
    private val fullscreenReadbackFailure: Throwable? = null,
    private val beforeFullscreenSetter: () -> Unit = { },
    private val pauseFullscreenCommitArbitration: Boolean = false,
    private val beforeGeometrySetter: () -> Unit = { },
    private val geometryFailureAfterContentSize: Throwable? = null,
) : AppKitNativeWindowPort {
    @Volatile
    private var effectiveLevelOverride: WindowLevel? = effectiveLevel
    private val windows = linkedMapOf<String, RecordingNativeWindowOwner>()
    private val surfaceObservers = linkedMapOf<String, RecordingNativeSurfaceObserver>()
    private val inputObservers = linkedMapOf<String, RecordingNativeInputObserver>()
    private val geometryObservers = linkedMapOf<String, RecordingNativeGeometryObserver>()
    val createdWindowTitles = CopyOnWriteArrayList<String>()
    val closedWindowTitles = CopyOnWriteArrayList<String>()
    val windowWillCloseTitles = CopyOnWriteArrayList<String>()
    val createdPeerIds = CopyOnWriteArrayList<AppKitWindowPeerId>()
    val requestedSurfaceRedrawGenerations = CopyOnWriteArrayList<Long>()
    val geometryTargets = CopyOnWriteArrayList<AppKitWindowGeometryTarget>()
    val mutationTargets = CopyOnWriteArrayList<AppKitWindowMutationTarget>()
    val createdWindowLevels = CopyOnWriteArrayList<WindowLevel>()
    val fullscreenToggleTargets = CopyOnWriteArrayList<FullscreenMode>()
    val fullscreenToggleLevels = CopyOnWriteArrayList<WindowLevel>()
    val fullscreenRestoreLevels = CopyOnWriteArrayList<WindowLevel>()
    val fullscreenReadbackTitles = CopyOnWriteArrayList<String>()
    private val surfaceActivationHookDelivered = AtomicBoolean(false)
    private val fullscreenCommitArbitrationPaused = AtomicBoolean(false)
    private val fullscreenCommitArbitrationStarted = CountDownLatch(1)
    private val fullscreenCommitArbitrationRelease = CountDownLatch(1)

    override fun isMainThread(): Boolean = true

    override fun <T> onMainThread(block: () -> T): T {
        val result = block()
        if (surfaceObservers.isNotEmpty() && surfaceActivationHookDelivered.compareAndSet(false, true)) {
            afterSurfaceActivationBeforeCommit(this)
        }
        return result
    }

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner {
        beforeCreateWindow(spec)
        return RecordingNativeWindowOwner(
            identity = spec.title,
            title = spec.title,
            initialGeometry = AppKitWindowGeometrySnapshot(
                contentSize = spec.contentSize,
                minimumSize = spec.minimumSize,
                maximumSize = spec.maximumSize,
                resizable = spec.resizable,
            ),
            chrome = AppKitWindowChromeSnapshot(
                decorations = spec.decorations,
                systemButtons = spec.systemButtons.canonicalFor(spec.decorations),
            ),
            level = spec.level,
            styleMask = initialStyleMask ?: deterministicStyleMask(spec),
        ).also { window ->
            check(windows.put(spec.title, window) == null) { "$name duplicate test window title" }
            createdWindowTitles += spec.title
            createdWindowLevels += spec.level
        }
    }

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner = RecordingNativeViewOwner()

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner = RecordingNativeDelegateOwner(
        peerId,
        createdWindowTitles.last(),
        callbacks,
        onDelegateRevoked,
        onDelegateReleased,
    ).also {
        createdPeerIds += peerId
    }

    override fun attachContentView(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ) {
        window.recordingWindow().contentView = view.recordingView()
    }

    override fun attachDelegate(
        window: AppKitNativeWindowOwner,
        delegate: AppKitNativeDelegateOwner,
    ) {
        window.recordingWindow().delegate = delegate.recordingDelegate()
    }

    override fun present(window: AppKitNativeWindowOwner) {
        window.recordingWindow().presented = true
    }

    override fun updateWindow(
        window: AppKitNativeWindowOwner,
        target: AppKitWindowMutationTarget,
        commit: AppKitWindowMutationCommit,
    ): AppKitWindowMutationSnapshot? {
        val recording = window.recordingWindow()
        mutationTargets += target
        geometryTargets += target.geometry
        beforeGeometrySetter()
        if (!commit.beforeFirstSetter()) return null
        when (val title = target.title) {
            is PropertyChange.Set -> recording.title = title.value
            PropertyChange.Clear -> error("AppKit does not support clearing a window title")
            PropertyChange.Unchanged -> Unit
        }
        if (target.geometry.hasChange()) {
            geometryFailureAfterContentSize?.let { failure ->
                recording.geometry = recording.geometry.copy(
                    contentSize = target.geometry.contentSize.resolve(recording.geometry.contentSize),
                )
                throw failure
            }
            val requested = recording.geometry.updateFor(target.geometry)
            val effective = effectiveGeometry ?: requested
            recording.geometry = effective
            recording.styleMask = if (effective.resizable) {
                recording.styleMask or APPKIT_RESIZABLE_STYLE_MASK
            } else {
                recording.styleMask and APPKIT_RESIZABLE_STYLE_MASK.inv()
            }
            if (emitGeometryDuringUpdate) geometryObservers[recording.identity]?.emit(effective)
            reentrantGeometryDuringUpdate?.let { geometryObservers[recording.identity]?.emit(it) }
        }
        if (target.chrome.hasChange()) {
            recording.chrome = recording.chrome.updateFor(target.chrome).canonical()
            recording.styleMask = recording.styleMask.withChrome(
                recording.chrome,
                recording.geometry.resizable,
            )
        }
        if (target.level.level !is PropertyChange.Unchanged) {
            recording.level = effectiveLevelOverride ?: target.level.level.resolve(recording.level)
        }
        return AppKitWindowMutationSnapshot(recording.title, recording.geometry, recording.chrome, recording.level)
    }

    override fun readWindow(window: AppKitNativeWindowOwner): AppKitWindowMutationSnapshot =
        window.recordingWindow().let { recording ->
            if (fullscreenRestoreLevels.isNotEmpty()) {
                fullscreenReadbackTitles += recording.identity
                fullscreenReadbackFailure?.let { throw it }
            }
            AppKitWindowMutationSnapshot(recording.title, recording.geometry, recording.chrome, recording.level)
        }

    override fun toggleFullscreen(
        window: AppKitNativeWindowOwner,
        target: AppKitWindowFullscreenTarget,
        commit: AppKitWindowMutationCommit,
    ): Boolean {
        beforeFullscreenSetter()
        if (
            pauseFullscreenCommitArbitration &&
            fullscreenCommitArbitrationPaused.compareAndSet(false, true)
        ) {
            fullscreenCommitArbitrationStarted.countDown()
            check(fullscreenCommitArbitrationRelease.await(2, TimeUnit.SECONDS))
        }
        if (!commit.beforeFirstSetter()) return false
        val recording = window.recordingWindow()
        recording.level = WindowLevel.Normal
        fullscreenToggleLevels += recording.level
        when (reentrantFullscreenCallback) {
            AppKitFullscreenCallback.WillEnter -> checkNotNull(recording.delegate).callbacks.windowWillEnterFullscreen()
            AppKitFullscreenCallback.DidEnter -> checkNotNull(recording.delegate).callbacks.windowDidEnterFullscreen()
            AppKitFullscreenCallback.DidFailEnter -> checkNotNull(recording.delegate).callbacks.windowDidFailEnterFullscreen()
            AppKitFullscreenCallback.WillExit -> checkNotNull(recording.delegate).callbacks.windowWillExitFullscreen()
            AppKitFullscreenCallback.DidExit -> checkNotNull(recording.delegate).callbacks.windowDidExitFullscreen()
            AppKitFullscreenCallback.DidFailExit -> checkNotNull(recording.delegate).callbacks.windowDidFailExitFullscreen()
            null -> Unit
        }
        fullscreenToggleFailure?.let { throw it }
        fullscreenToggleTargets += target.mode
        return true
    }

    fun awaitCommitArbitration() {
        check(fullscreenCommitArbitrationStarted.await(2, TimeUnit.SECONDS))
    }

    fun releaseCommitArbitration() {
        fullscreenCommitArbitrationRelease.countDown()
    }

    override fun restoreWindowLevel(window: AppKitNativeWindowOwner, desiredLevel: WindowLevel) {
        fullscreenRestoreLevels += desiredLevel
        fullscreenRestoreFailure?.let { throw it }
        window.recordingWindow().level = effectiveLevelOverride ?: desiredLevel
    }

    override fun observeGeometry(
        window: AppKitNativeWindowOwner,
        callbacks: AppKitWindowGeometryCallbacks,
    ): AppKitNativeGeometryObserverOwner = RecordingNativeGeometryObserver(callbacks).also { observer ->
        val identity = window.recordingWindow().identity
        check(geometryObservers.put(identity, observer) == null) { "$name duplicate test geometry observer" }
    }

    override fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner = RecordingNativeSurfaceObserver(
        callbacks,
        initialSurfaceSnapshot,
        requestedSurfaceRedrawGenerations::add,
    ).also { observer ->
        check(surfaceObservers.put(window.recordingWindow().identity, observer) == null) {
            "$name duplicate test surface observer"
        }
    }

    override fun observeInput(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitInputCallbacks,
    ): AppKitNativeInputObserverOwner? = if (
        inputObservationInstalled || window.recordingWindow().identity in inputObservationInstalledFor
    ) {
        RecordingNativeInputObserver(callbacks).also { observer ->
        val identity = window.recordingWindow().identity
        check(inputObservers.put(identity, observer) == null) { "$name duplicate test input observer" }
        afterInputObservationBeforeCommit(this)
        }
    } else {
        null
    }

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        window.recordingWindow().delegateAttached = false
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        window.recordingWindow().contentViewAttached = false
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        val recording = window.recordingWindow()
        beforeCloseWindow(recording.identity)
        recordNativeClose(recording)
        closeFailures[recording.identity]?.let { throw it }
    }

    override fun desktopHandle(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ): RuntimeDesktopNativeWindowHandle.AppKit = RuntimeDesktopNativeWindowHandle.AppKit(0xA11uL, 0xB22uL)

    fun requestNativeClose(title: String): Boolean =
        checkNotNull(windows[title]?.delegate).callbacks.windowShouldClose()

    fun emitNativeClosed(title: String) {
        recordNativeClose(checkNotNull(windows[title]))
    }

    fun emitExternalGeometry(title: String, snapshot: AppKitWindowGeometrySnapshot) {
        val window = checkNotNull(windows[title])
        window.geometry = snapshot
        window.styleMask = if (snapshot.resizable) {
            window.styleMask or APPKIT_RESIZABLE_STYLE_MASK
        } else {
            window.styleMask and APPKIT_RESIZABLE_STYLE_MASK.inv()
        }
        checkNotNull(geometryObservers[title]).emit(snapshot)
    }

    fun emitWillEnter(title: String) {
        checkNotNull(windows[title]?.delegate).callbacks.windowWillEnterFullscreen()
    }

    fun emitDidEnter(title: String) {
        checkNotNull(windows[title]?.delegate).callbacks.windowDidEnterFullscreen()
    }

    fun emitDidFailEnter(title: String) {
        checkNotNull(windows[title]?.delegate).callbacks.windowDidFailEnterFullscreen()
    }

    fun emitWillExit(title: String) {
        checkNotNull(windows[title]?.delegate).callbacks.windowWillExitFullscreen()
    }

    fun emitDidExit(title: String) {
        checkNotNull(windows[title]?.delegate).callbacks.windowDidExitFullscreen()
    }

    fun emitDidFailExit(title: String) {
        checkNotNull(windows[title]?.delegate).callbacks.windowDidFailExitFullscreen()
    }

    fun styleMask(title: String): Long = checkNotNull(windows[title]).styleMask

    fun chrome(title: String): AppKitWindowChromeSnapshot = checkNotNull(windows[title]).chrome

    fun level(title: String): WindowLevel = checkNotNull(windows[title]).level

    fun forceEffectiveLevel(level: WindowLevel?) {
        effectiveLevelOverride = level
    }

    fun emitSurfaceMetrics(title: String, metrics: SurfaceMetrics) {
        checkNotNull(surfaceObservers[title]).emitMetrics(metrics)
    }

    fun emitSurfaceFocus(title: String, focus: SurfaceFocus) {
        checkNotNull(surfaceObservers[title]).emitFocus(focus)
    }

    fun emitSurfaceRedrawConsumed(title: String, generation: Long) {
        checkNotNull(surfaceObservers[title]).emitRedrawConsumed(generation)
    }

    fun emitInput(title: String, input: AppKitInput) {
        checkNotNull(inputObservers[title]).emit(input)
    }

    fun forceLateSurfaceMetrics(title: String, metrics: SurfaceMetrics) {
        checkNotNull(surfaceObservers[title]).forceMetrics(metrics)
    }

    private fun recordNativeClose(recording: RecordingNativeWindowOwner) {
        if (!recording.nativeClosed.compareAndSet(false, true)) return
        closedWindowTitles += recording.identity
        windowWillCloseTitles += recording.identity
        checkNotNull(recording.delegate).callbacks.windowWillClose()
    }

    private class RecordingNativeWindowOwner(
        val identity: String,
        var title: String,
        val initialGeometry: AppKitWindowGeometrySnapshot,
        var chrome: AppKitWindowChromeSnapshot,
        var level: WindowLevel,
        var styleMask: Long,
    ) : AppKitNativeWindowOwner {
        val nativeClosed = AtomicBoolean(false)
        var contentView: RecordingNativeViewOwner? = null
        var delegate: RecordingNativeDelegateOwner? = null
        var contentViewAttached: Boolean = true
        var delegateAttached: Boolean = true
        var presented: Boolean = false
        var geometry: AppKitWindowGeometrySnapshot = initialGeometry
        private val released = AtomicBoolean(false)

        override fun close() {
            released.compareAndSet(false, true)
        }
    }

    private class RecordingNativeViewOwner : AppKitNativeViewOwner {
        private val released = AtomicBoolean(false)

        override fun close() {
            released.compareAndSet(false, true)
        }
    }

    private class RecordingNativeDelegateOwner(
        val peerId: AppKitWindowPeerId,
        private val title: String,
        val callbacks: AppKitWindowDelegateCallbacks,
        private val onRevoked: (String) -> Unit,
        private val onReleased: (String) -> Unit,
    ) : AppKitNativeDelegateOwner {
        private val callbacksRevoked = AtomicBoolean(false)
        private val retained = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        override fun revokeCallbacks() {
            if (callbacksRevoked.compareAndSet(false, true)) onRevoked(title)
        }

        override fun retainAfterFailedDetachment() {
            retained.set(true)
        }

        override fun close() {
            if (released.compareAndSet(false, true)) onReleased(title)
        }
    }

    private class RecordingNativeSurfaceObserver(
        private val callbacks: AppKitSurfaceCallbacks,
        override val initialSnapshot: AppKitSurfaceSnapshot,
        private val recordRedrawRequest: (Long) -> Unit,
    ) : AppKitNativeSurfaceObserverOwner {
        private val accepting = AtomicBoolean(true)

        fun emitMetrics(metrics: SurfaceMetrics) {
            if (accepting.get()) callbacks.metricsChanged(metrics)
        }

        fun emitFocus(focus: SurfaceFocus) {
            if (accepting.get()) callbacks.focusChanged(focus)
        }

        fun emitRedrawConsumed(generation: Long) {
            if (accepting.get()) callbacks.redrawConsumed(generation)
        }

        fun forceMetrics(metrics: SurfaceMetrics) {
            callbacks.metricsChanged(metrics)
        }

        override fun requestRedraw(generation: Long) {
            recordRedrawRequest(generation)
        }

        override fun revokeCallbacks() {
            accepting.set(false)
        }

        override fun close() = Unit
    }

    private class RecordingNativeGeometryObserver(
        private val callbacks: AppKitWindowGeometryCallbacks,
    ) : AppKitNativeGeometryObserverOwner {
        private val accepting = AtomicBoolean(true)

        fun emit(snapshot: AppKitWindowGeometrySnapshot) {
            if (accepting.get()) callbacks.geometryChanged(snapshot)
        }

        override fun revokeCallbacks() {
            accepting.set(false)
        }

        override fun close() = Unit
    }

    private class RecordingNativeInputObserver(
        private val callbacks: AppKitInputCallbacks,
    ) : AppKitNativeInputObserverOwner {
        private val accepting = AtomicBoolean(true)
        override val keyboardInstalled: Boolean = true
        override val pointerInstalled: Boolean = true

        fun emit(input: AppKitInput) {
            if (accepting.get()) callbacks.input(input)
        }

        override fun revokeCallbacks() {
            accepting.set(false)
        }

        override fun close() = Unit
    }

    private fun AppKitNativeWindowOwner.recordingWindow(): RecordingNativeWindowOwner =
        this as? RecordingNativeWindowOwner ?: error("foreign test window owner")

    private fun AppKitNativeViewOwner.recordingView(): RecordingNativeViewOwner =
        this as? RecordingNativeViewOwner ?: error("foreign test view owner")

    private fun AppKitNativeDelegateOwner.recordingDelegate(): RecordingNativeDelegateOwner =
        this as? RecordingNativeDelegateOwner ?: error("foreign test delegate owner")
}

private fun AppKitWindowGeometrySnapshot.updateFor(
    target: AppKitWindowGeometryTarget,
): AppKitWindowGeometrySnapshot = copy(
    contentSize = target.contentSize.resolve(contentSize),
    minimumSize = target.minimumSize.resolve(minimumSize),
    maximumSize = target.maximumSize.resolve(maximumSize),
    resizable = target.resizable.resolve(resizable),
)

private fun AppKitWindowGeometryTarget.hasChange(): Boolean =
    contentSize !is PropertyChange.Unchanged ||
        minimumSize !is PropertyChange.Unchanged ||
        maximumSize !is PropertyChange.Unchanged ||
        resizable !is PropertyChange.Unchanged

private fun AppKitWindowChromeTarget.hasChange(): Boolean =
    decorations !is PropertyChange.Unchanged || systemButtons !is PropertyChange.Unchanged

private fun AppKitWindowChromeSnapshot.updateFor(
    target: AppKitWindowChromeTarget,
): AppKitWindowChromeSnapshot = copy(
    decorations = target.decorations.resolve(decorations),
    systemButtons = target.systemButtons.resolve(systemButtons),
)

private fun AppKitWindowChromeSnapshot.canonical(): AppKitWindowChromeSnapshot = if (
    decorations == WindowDecorations.Borderless
) {
    copy(systemButtons = WindowSystemButtons.None)
} else {
    this
}

private fun <T> PropertyChange<T>.resolve(current: T): T = when (this) {
    is PropertyChange.Set -> value
    PropertyChange.Clear,
    PropertyChange.Unchanged,
    -> current
}

private const val APPKIT_RESIZABLE_STYLE_MASK: Long = 1L shl 3
private const val APPKIT_TITLED_STYLE_MASK: Long = 1L shl 4
private const val APPKIT_CLOSABLE_STYLE_MASK: Long = 1L shl 5
private const val APPKIT_MINIATURIZABLE_STYLE_MASK: Long = 1L shl 6
private const val APPKIT_OWNED_CHROME_STYLE_MASK: Long =
    APPKIT_RESIZABLE_STYLE_MASK or
        APPKIT_TITLED_STYLE_MASK or
        APPKIT_CLOSABLE_STYLE_MASK or
        APPKIT_MINIATURIZABLE_STYLE_MASK

private fun deterministicStyleMask(spec: WindowSpec): Long =
    0L.withChrome(
        AppKitWindowChromeSnapshot(
            decorations = spec.decorations,
            systemButtons = spec.systemButtons.canonicalFor(spec.decorations),
        ),
        spec.resizable,
    )

private fun Long.withChrome(
    chrome: AppKitWindowChromeSnapshot,
    resizable: Boolean,
): Long {
    var owned = if (resizable) APPKIT_RESIZABLE_STYLE_MASK else 0L
    if (chrome.decorations == WindowDecorations.System) {
        owned = owned or APPKIT_TITLED_STYLE_MASK
        when (chrome.systemButtons) {
            WindowSystemButtons.All -> {
                owned = owned or APPKIT_CLOSABLE_STYLE_MASK or APPKIT_MINIATURIZABLE_STYLE_MASK
            }
            WindowSystemButtons.CloseOnly -> owned = owned or APPKIT_CLOSABLE_STYLE_MASK
            WindowSystemButtons.None -> Unit
        }
    }
    return this and APPKIT_OWNED_CHROME_STYLE_MASK.inv() or owned
}

private fun WindowSystemButtons.canonicalFor(
    decorations: WindowDecorations,
): WindowSystemButtons = if (decorations == WindowDecorations.Borderless) {
    WindowSystemButtons.None
} else {
    this
}

private fun chromeUpdateProperties(): Set<WindowProperty> =
    setOf(
        WindowProperty.ContentSize,
        WindowProperty.MinimumSize,
        WindowProperty.MaximumSize,
        WindowProperty.Resizable,
        WindowProperty.Decorations,
        WindowProperty.SystemButtons,
    )

internal class OwnerThreadAppKitNativeWindowPort(
    name: String,
) : AppKitNativeWindowPort, AutoCloseable {
    private val delegate = DeterministicAppKitNativeWindowPort(name)
    private val ownerThread = AtomicReference<Thread?>()
    private val executor = Executors.newSingleThreadExecutor { action ->
        Thread.ofPlatform().daemon().name("kadre-appkit-owner-test").unstarted {
            ownerThread.set(Thread.currentThread())
            action.run()
        }
    }
    private val nextForeignCall = AtomicReference<CountDownLatch?>()

    override fun isMainThread(): Boolean = Thread.currentThread() === ownerThread.get()

    override fun <T> onMainThread(block: () -> T): T {
        if (Thread.currentThread() === ownerThread.get()) return block()
        nextForeignCall.getAndSet(null)?.countDown()
        return executor.submit<T> { block() }.get()
    }

    override fun createWindow(spec: WindowSpec): AppKitNativeWindowOwner = delegate.createWindow(spec)

    override fun createContentView(spec: WindowSpec): AppKitNativeViewOwner = delegate.createContentView(spec)

    override fun createDelegate(
        peerId: AppKitWindowPeerId,
        callbacks: AppKitWindowDelegateCallbacks,
    ): AppKitNativeDelegateOwner = delegate.createDelegate(peerId, callbacks)

    override fun attachContentView(window: AppKitNativeWindowOwner, view: AppKitNativeViewOwner) {
        delegate.attachContentView(window, view)
    }

    override fun attachDelegate(window: AppKitNativeWindowOwner, delegate: AppKitNativeDelegateOwner) {
        this.delegate.attachDelegate(window, delegate)
    }

    override fun present(window: AppKitNativeWindowOwner) {
        delegate.present(window)
    }

    override fun observeSurface(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
        callbacks: AppKitSurfaceCallbacks,
    ): AppKitNativeSurfaceObserverOwner = delegate.observeSurface(window, view, callbacks)

    override fun detachDelegate(window: AppKitNativeWindowOwner) {
        delegate.detachDelegate(window)
    }

    override fun detachContentView(window: AppKitNativeWindowOwner) {
        delegate.detachContentView(window)
    }

    override fun closeWindow(window: AppKitNativeWindowOwner) {
        delegate.closeWindow(window)
    }

    override fun desktopHandle(
        window: AppKitNativeWindowOwner,
        view: AppKitNativeViewOwner,
    ): RuntimeDesktopNativeWindowHandle.AppKit = delegate.desktopHandle(window, view)

    fun submitOnOwnerThread(action: () -> Unit): Future<*> = executor.submit(action)

    fun observeNextForeignMainThreadCall(): CountDownLatch = CountDownLatch(1).also { latch ->
        check(nextForeignCall.compareAndSet(null, latch))
    }

    fun emitNativeClosed(title: String) {
        delegate.emitNativeClosed(title)
    }

    fun emitSurfaceMetrics(title: String, metrics: SurfaceMetrics) {
        delegate.emitSurfaceMetrics(title, metrics)
    }

    fun emitSurfaceRedrawConsumed(title: String, generation: Long) {
        delegate.emitSurfaceRedrawConsumed(title, generation)
    }

    val requestedSurfaceRedrawGenerations: List<Long>
        get() = delegate.requestedSurfaceRedrawGenerations

    val closedWindowTitles: List<String>
        get() = delegate.closedWindowTitles

    override fun close() {
        executor.shutdownNow()
    }
}

private fun newDaemonSingleThreadExecutor(name: String): ExecutorService =
    Executors.newSingleThreadExecutor { action -> Thread.ofPlatform().daemon().name(name).unstarted(action) }

internal fun deterministicSurfaceSnapshot(
    logicalSize: LogicalSize = LogicalSize(320.0, 240.0),
    scaleFactor: Double = 2.0,
): AppKitSurfaceSnapshot = AppKitSurfaceSnapshot(
    metrics = SurfaceMetrics(
        logicalSize = logicalSize,
        physicalSize = logicalSize.toPhysical(scaleFactor),
        scaleFactor = scaleFactor,
        safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    ),
    focus = SurfaceFocus.Focused,
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Unknown,
    theme = SurfaceTheme.Light,
)

internal fun <T> KadreResult<T>.appKitSuccessValue(): T = when (this) {
    is KadreResult.Success -> value
    is KadreResult.Failure -> error("expected success, got $reason")
}

private fun <T> KadreResult<T>.successValue(): T = appKitSuccessValue()

private suspend fun openedWindow(
    driver: AppKitWindowRuntimeDriver,
    spec: WindowSpec,
): Window = assertIs<WindowRequestOutcome.OpenedHere>(
    driver.manager.requestWindow(spec).successValue().await(),
).window

private fun fullscreenDriver(
    port: DeterministicAppKitNativeWindowPort,
): AppKitWindowRuntimeDriver = AppKitWindowRuntimeDriverFactory { port }.create(
    KadrePolicies.Default.resources,
    publicAppKitCapabilities = true,
    enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
)

private fun fullscreenDriver(
    port: DeterministicAppKitNativeWindowPort,
    reported: MutableCollection<Throwable>,
): AppKitWindowRuntimeDriver = AppKitWindowRuntimeDriverFactory { port }.create(
    resources = KadrePolicies.Default.resources,
    failureReporter = RuntimeFailureReporter(reported::add),
    publicAppKitCapabilities = true,
    enabledWindowUpdateCapabilities = setOf(WindowProperty.Fullscreen, WindowProperty.Level),
)

private fun pausingFullscreenPort(name: String): DeterministicAppKitNativeWindowPort =
    DeterministicAppKitNativeWindowPort(
        name = name,
        pauseFullscreenCommitArbitration = true,
    )

private suspend fun awaitFullscreenToggle(port: DeterministicAppKitNativeWindowPort) {
    withTimeout(2.seconds) {
        while (port.fullscreenToggleTargets.isEmpty()) yield()
    }
}

private fun fullscreenFailureFixture(code: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.AppKit, "fullscreen", code)

private suspend fun WindowRequest.awaitOpened() {
    check(await() is WindowRequestOutcome.OpenedHere) { "expected an AppKit window to open" }
}
