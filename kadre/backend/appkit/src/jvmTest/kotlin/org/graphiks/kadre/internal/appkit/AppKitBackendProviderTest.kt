package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSApplicationActivationPolicy
import org.graphiks.kffi.objc.NSButton
import org.graphiks.kffi.objc.NSDefaultRunLoopMode
import org.graphiks.kffi.objc.NSDate_distantFuture
import org.graphiks.kffi.objc.NSDate_date
import org.graphiks.kffi.objc.NSEvent
import org.graphiks.kffi.objc.NSEventMask
import org.graphiks.kffi.objc.NSEventPhase
import org.graphiks.kffi.objc.NSEventModifierFlags
import org.graphiks.kffi.objc.NSEventType
import org.graphiks.kffi.objc.CGMomentumScrollPhase
import org.graphiks.kffi.objc.CGScrollEventUnit
import org.graphiks.kffi.objc.CGScrollPhase
import org.graphiks.kffi.objc.NSNotificationCenter
import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSSize
import org.graphiks.kffi.objc.NSView
import org.graphiks.kffi.objc.NSWindow
import org.graphiks.kffi.objc.NSWindowButton
import org.graphiks.kffi.objc.NSWindowStyleMask
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.appkit.AppKitScrollWheelEvent
import org.graphiks.kffi.objc.appkit.postScrollWheelEvent
import org.graphiks.kffi.objc.nextEventMatchingMask_untilDate_inMode_dequeue
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLifecycle
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.application.SessionStopReason
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.platform.desktop.DesktopNativeWindowHandle
import org.graphiks.kadre.platform.desktop.withDesktopHandle
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.window.FullscreenMode
import org.graphiks.kadre.window.LogicalSizeRange
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowCloseResponseOutcome
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowLevel
import org.graphiks.kadre.window.WindowManager
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowRequestState
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.window.WindowSystemButtons
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppKitBackendProviderTest {
    @Test
    fun discoveryAndAvailabilityDoNotTouchTheNativeBridge() {
        val providers = ServiceLoader.load(DesktopBackendProvider::class.java).toList()
        val provider = providers.single { it.backend == DesktopBackendKind.AppKit }

        assertEquals(setOf(DesktopIntegrationKind.AppKitMainLoop), provider.supportedIntegrations)
        assertEquals(isMacOs(), provider.isAvailable())
    }

    @Test
    fun embeddedAttachRejectsInvalidHostStateBeforeFactoryCreation() {
        var factoryInvoked = false
        val native = RecordingNativeApplication(mainThread = false)
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
        val factory = KadreApplicationFactory {
            factoryInvoked = true
            KadreApplication { }
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            provider.run(DesktopStandaloneRequest(factory, true, KadrePolicies.Default)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            provider.attach(
                DesktopEmbeddedRequest(
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
                    factory,
                    DesktopIntegrationKind.AppKitMainLoop,
                    KadrePolicies.Default,
                ),
            ),
        )
        assertFalse(factoryInvoked)
        assertEquals(0, native.runCount)
    }

    @Test
    fun embeddedAttachRejectsWrongIntegrationAndInactiveNativeLoopBeforeFactoryCreation() {
        var factoryInvoked = false
        val factory = KadreApplicationFactory {
            factoryInvoked = true
            KadreApplication { }
        }
        val inactiveProvider = AppKitBackendProvider.forTesting(
            RecordingNativeApplication(),
            AppKitProcessBroker(),
        ) { true }
        val inactiveRequest = DesktopEmbeddedRequest(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            factory,
            DesktopIntegrationKind.AppKitMainLoop,
            KadrePolicies.Default,
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true)),
            inactiveProvider.attach(inactiveRequest),
        )
        assertFalse(factoryInvoked)

        val activeProvider = AppKitBackendProvider.forTesting(
            EmbeddedNativeApplication(),
            AppKitProcessBroker(),
        ) { true }
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            activeProvider.attach(
                DesktopEmbeddedRequest(
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
                    factory,
                    DesktopIntegrationKind.AwtEventDispatchThread,
                    KadrePolicies.Default,
                ),
            ),
        )
        assertFalse(factoryInvoked)
    }

    @Test
    fun embeddedAttachBusyClosesItsObservationBeforeFactoryCreation() {
        val broker = AppKitProcessBroker()
        val standalone = assertIs<AppKitProcessBroker.StandaloneLease>(broker.tryAcquireStandalone())
        val native = EmbeddedNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, broker) { true }
        var factoryInvoked = false
        val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())

        try {
            assertEquals(
                KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
                provider.attach(
                    DesktopEmbeddedRequest(
                        parentScope,
                        KadreApplicationFactory {
                            factoryInvoked = true
                            KadreApplication { }
                        },
                        DesktopIntegrationKind.AppKitMainLoop,
                        KadrePolicies.Default,
                    ),
                ),
            )
            assertEquals(0, native.observerCount)
            assertFalse(factoryInvoked)
        } finally {
            standalone.close()
            parentScope.cancel()
        }
    }

    @Test
    fun embeddedSessionsReceiveLifecycleWithoutOwningTheNativeLoop() = kotlinx.coroutines.runBlocking {
        val native = EmbeddedNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
        val firstScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val secondScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val firstLifecycle = CompletableDeferred<KadreLifecycle>()
        val secondLifecycle = CompletableDeferred<KadreLifecycle>()

        try {
            val first = provider.attach(embeddedRequest(firstScope, firstLifecycle)).requireSession()
            val second = provider.attach(embeddedRequest(secondScope, secondLifecycle)).requireSession()
            val observedFirst = withTimeout(2.seconds) { firstLifecycle.await() }
            val observedSecond = withTimeout(2.seconds) { secondLifecycle.await() }
            assertEquals(2, native.observerCount)

            native.emit(AppKitLifecycleSignal.DidHide)
            assertEquals(org.graphiks.kadre.application.VisibilityState.Background, observedFirst.state.value.visibility)
            assertEquals(org.graphiks.kadre.application.VisibilityState.Background, observedSecond.state.value.visibility)

            first.close()
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                first.awaitTermination(),
            )
            native.awaitObserverCount(1)

            native.emit(AppKitLifecycleSignal.BecameActive)
            assertEquals(org.graphiks.kadre.application.ActivationState.Active, observedSecond.state.value.activation)

            native.emit(AppKitLifecycleSignal.HostTerminated)
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostDetached),
                second.awaitTermination(),
            )
            native.awaitObserverCount(0)
            assertEquals(0, native.runCount)
            assertEquals(0, native.stopCount)
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }

    @Test
    fun privateWindowDriversDoNotReplaceThePublicSessionOwnedManager() = kotlinx.coroutines.runBlocking {
        val native = EmbeddedNativeApplication()
        val publicPort = DeterministicAppKitNativeWindowPort("public-session")
        val provider = AppKitBackendProvider.forTesting(
            native,
            AppKitProcessBroker(),
            AppKitWindowRuntimeDriverFactory { publicPort },
        ) { true }
        val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val observedWindows = CompletableDeferred<WindowManager>()
        val first = AppKitWindowRuntimeDriverFactory {
            DeterministicAppKitNativeWindowPort("private-first")
        }.create(KadrePolicies.Default.resources)
        val second = AppKitWindowRuntimeDriverFactory {
            DeterministicAppKitNativeWindowPort("private-second")
        }.create(KadrePolicies.Default.resources)

        try {
            val session = provider.attach(
                DesktopEmbeddedRequest(
                    parentScope,
                    KadreApplicationFactory {
                        KadreApplication {
                            observedWindows.complete(windows)
                            kotlinx.coroutines.awaitCancellation()
                        }
                    },
                    DesktopIntegrationKind.AppKitMainLoop,
                    KadrePolicies.Default,
                ),
            ).requireSession()
            val ordinary = withTimeout(2.seconds) { observedWindows.await() }

            assertNotSame(first.manager, ordinary)
            assertNotSame(second.manager, ordinary)
            assertEquals("RuntimeWindowManager", ordinary::class.simpleName)
            assertIs<WindowRequestOutcome.OpenedHere>(
                ordinary.requestWindow(WindowSpec(title = "public-owned"))
                    .appKitSuccessValue()
                    .await(),
            )
            assertEquals(listOf("public-owned"), publicPort.createdWindowTitles)
            assertEquals(emptyList(), first.manager.state.value.windows)
            assertEquals(emptyList(), second.manager.state.value.windows)

            session.close()
            session.awaitTermination()
            Unit
        } finally {
            first.close()
            second.close()
            parentScope.cancel()
        }
    }

    @Test
    fun publicAppKitSessionCancelsAPendingRequestWithoutLeavingNativeOrManagerResidue() =
        kotlinx.coroutines.runBlocking {
            val preparationStarted = CountDownLatch(1)
            val allowPreparation = CountDownLatch(1)
            val port = DeterministicAppKitNativeWindowPort(
                name = "public-cancellation",
                beforeCreateWindow = {
                    preparationStarted.countDown()
                    check(allowPreparation.await(2, TimeUnit.SECONDS))
                },
            )
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                assertEquals(
                    Capability.Supported(setOf(WindowCreationMode.OpenedHere), FeatureAvailability.Available),
                    windows.state.value.capabilities.requestWindow,
                )
                val request = windows.requestWindow(WindowSpec(title = "cancel-before-commit")).appKitSuccessValue()
                assertEquals(WindowRequestState.Pending, request.state.value)
                assertTrue(preparationStarted.await(2, TimeUnit.SECONDS))

                assertEquals(
                    org.graphiks.kadre.window.WindowCancellationOutcome.CancellationRequested,
                    request.cancel(),
                )
                allowPreparation.countDown()

                assertEquals(WindowRequestOutcome.Cancelled, withTimeout(2.seconds) { request.await() })
                withTimeout(2.seconds) {
                    while (port.closedWindowTitles != listOf("cancel-before-commit")) yield()
                }
                assertEquals(emptyList(), windows.state.value.windows)
            } finally {
                allowPreparation.countDown()
                session.close()
                session.awaitTermination()
                parentScope.cancel()
            }
        }

    @Test
    fun publicAppKitSessionKeepsAdmissionOrderAndMovesPrimaryOnlyAfterClosure() =
        kotlinx.coroutines.runBlocking {
            val port = DeterministicAppKitNativeWindowPort("public-membership")
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                val first = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "first")).appKitSuccessValue().await(),
                ).window
                val second = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "second")).appKitSuccessValue().await(),
                ).window

                assertEquals(listOf(first, second), windows.state.value.windows)
                assertSame(first, windows.state.value.primary)
                assertEquals(
                    Capability.Supported(Unit, FeatureAvailability.Available),
                    first.capabilities.value.title,
                )
                assertIs<Capability.Unsupported>(first.surface.capabilities.value.platformAccess)

                first.close()
                withTimeout(2.seconds) { first.state.first { it.phase == WindowPhase.Closed } }

                assertEquals(listOf(second), windows.state.value.windows)
                assertSame(second, windows.state.value.primary)
            } finally {
                session.close()
                session.awaitTermination()
                parentScope.cancel()
            }
        }

    @Test
    fun publicAppKitSurfacePublishesSnapshotBeforeEventsAndCoalescesRedrawBursts() =
        kotlinx.coroutines.runBlocking {
            val initial = deterministicSurfaceSnapshot(
                logicalSize = LogicalSize(300.0, 200.0),
                scaleFactor = 2.0,
            ).copy(
                focus = SurfaceFocus.Focused,
                visibility = org.graphiks.kadre.surface.SurfaceVisibility.Hidden,
                occlusion = SurfaceOcclusion.Occluded,
                theme = SurfaceTheme.Dark,
            )
            val port = DeterministicAppKitNativeWindowPort(
                name = "public-surface-ordering",
                initialSurfaceSnapshot = initial,
            )
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                val window = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "surface-ordering"))
                        .appKitSuccessValue()
                        .await(),
                ).window
                val surface = window.surface
                val initialState = surface.state.value
                assertEquals(SurfaceAttachmentState.Attached, initialState.attachment)
                assertEquals(initial.metrics.logicalSize, initialState.logicalSize)
                assertEquals(initial.metrics.physicalSize, initialState.physicalSize)
                assertEquals(initial.metrics.scaleFactor, initialState.scaleFactor)
                assertEquals(initial.metrics.safeAreaInsets, initialState.safeAreaInsets)
                assertEquals(initial.focus, initialState.focus)
                assertEquals(initial.visibility, initialState.visibility)
                assertEquals(initial.occlusion, initialState.occlusion)
                assertEquals(initial.theme, initialState.theme)

                val events = Channel<SurfaceEvent>(Channel.UNLIMITED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    surface.events.collect(events::send)
                }
                try {
                    val resized = deterministicSurfaceSnapshot(
                        logicalSize = LogicalSize(420.0, 260.0),
                        scaleFactor = 1.5,
                    ).metrics
                    port.emitSurfaceMetrics("surface-ordering", resized)
                    val metricsEvent = withTimeout(2.seconds) {
                        assertIs<SurfaceEvent.MetricsChanged>(events.receive())
                    }
                    assertEquals(metricsEvent.state, surface.state.value)
                    assertEquals(resized.logicalSize, metricsEvent.state.logicalSize)
                    assertEquals(resized.physicalSize, metricsEvent.state.physicalSize)
                    assertTrue(metricsEvent.state.revision.value > initialState.revision.value)

                    port.emitSurfaceFocus("surface-ordering", SurfaceFocus.Unfocused)
                    val focusEvent = withTimeout(2.seconds) {
                        assertIs<SurfaceEvent.FocusChanged>(events.receive())
                    }
                    assertEquals(focusEvent.state, surface.state.value)
                    assertEquals(SurfaceFocus.Unfocused, focusEvent.state.focus)
                    assertTrue(focusEvent.state.revision.value > metricsEvent.state.revision.value)

                    repeat(8) {
                        assertEquals(KadreResult.Success(Unit), surface.requestRedraw())
                    }
                    withTimeout(2.seconds) {
                        while (port.requestedSurfaceRedrawGenerations != listOf(0L)) yield()
                    }
                    port.emitSurfaceRedrawConsumed("surface-ordering", 0L)
                    val redrawEvent = withTimeout(2.seconds) {
                        assertIs<SurfaceEvent.RedrawRequested>(events.receive())
                    }
                    assertEquals(surface.state.value.revision, redrawEvent.stateRevision)
                    yield()
                    assertTrue(events.tryReceive().isFailure)
                } finally {
                    collector.cancel()
                }
            } finally {
                session.close()
                session.awaitTermination()
                parentScope.cancel()
            }
        }

    @Test
    fun publicAppKitSurfaceIgnoresLateNativeValuesAfterTerminalClose() =
        kotlinx.coroutines.runBlocking {
            val port = DeterministicAppKitNativeWindowPort(
                name = "public-surface-terminal",
                initialSurfaceSnapshot = deterministicSurfaceSnapshot().copy(
                    focus = SurfaceFocus.Unfocused,
                ),
            )
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                val window = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "surface-terminal"))
                        .appKitSuccessValue()
                        .await(),
                ).window
                val surface = window.surface
                val events = async(start = CoroutineStart.UNDISPATCHED) { surface.events.toList() }

                port.emitSurfaceFocus("surface-terminal", SurfaceFocus.Focused)
                withTimeout(2.seconds) { surface.state.first { it.focus == SurfaceFocus.Focused } }
                assertIs<WindowCloseOutcome.Accepted>(window.close().appKitSuccessValue())
                val terminal = withTimeout(2.seconds) {
                    surface.state.first { it.attachment == SurfaceAttachmentState.Detached }
                }
                val terminalEvents = withTimeout(2.seconds) { events.await() }

                val lateMetrics = deterministicSurfaceSnapshot(
                    logicalSize = LogicalSize(999.0, 777.0),
                    scaleFactor = 1.0,
                ).metrics
                port.forceLateSurfaceMetrics("surface-terminal", lateMetrics)
                yield()

                assertEquals(terminal, surface.state.value)
                assertEquals(1, terminalEvents.size)
                assertIs<SurfaceEvent.FocusChanged>(terminalEvents.single())
                assertEquals(
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
                    surface.requestRedraw(),
                )
            } finally {
                session.close()
                session.awaitTermination()
                parentScope.cancel()
            }
        }

    @Test
    fun publicAppKitWindowStateDoesNotClaimUnsupportedRequestedPropertiesWereApplied() =
        kotlinx.coroutines.runBlocking {
            val port = DeterministicAppKitNativeWindowPort("public-effective-state")
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                val window = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(
                        WindowSpec(
                            title = "effective",
                            contentSize = LogicalSize(320.0, 180.0),
                            minimumSize = LogicalSize(100.0, 80.0),
                            maximumSize = LogicalSize(640.0, 360.0),
                            fullscreen = FullscreenMode.Borderless,
                            level = WindowLevel.Floating,
                            transparent = true,
                            blurBehind = true,
                            contentProtection = true,
                        ),
                    ).appKitSuccessValue().await(),
                ).window

                assertEquals("effective", window.state.value.title)
                assertEquals(LogicalSize(320.0, 180.0), window.state.value.contentSize)
                assertEquals(LogicalSize(100.0, 80.0), window.state.value.minimumSize)
                assertEquals(LogicalSize(640.0, 360.0), window.state.value.maximumSize)
                assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
                assertEquals(WindowDecorations.System, window.state.value.decorations)
                assertEquals(WindowSystemButtons.All, window.state.value.systemButtons)
                assertEquals(WindowLevel.Normal, window.state.value.level)
                assertFalse(window.state.value.transparent)
                assertFalse(window.state.value.blurBehind)
                assertFalse(window.state.value.contentProtection)

                val windowCapabilities = window.capabilities.value
                assertEquals(
                    Capability.Supported(Unit, FeatureAvailability.Available),
                    windowCapabilities.title,
                )
                assertEquals(
                    Capability.Supported(
                        setOf(WindowDecorations.System, WindowDecorations.Borderless),
                        FeatureAvailability.Available,
                    ),
                    windowCapabilities.decorations,
                )
                assertEquals(
                    Capability.Supported(
                        setOf(
                            WindowSystemButtons.All,
                            WindowSystemButtons.CloseOnly,
                            WindowSystemButtons.None,
                        ),
                        FeatureAvailability.Available,
                    ),
                    windowCapabilities.systemButtons,
                )
                listOf<Capability<*>>(
                    windowCapabilities.outerPosition,
                    windowCapabilities.fullscreen,
                    windowCapabilities.level,
                    windowCapabilities.transparency,
                    windowCapabilities.blurBehind,
                    windowCapabilities.icon,
                    windowCapabilities.attention,
                    windowCapabilities.contentProtection,
                ).forEach { capability -> assertIs<Capability.Unsupported>(capability) }
                assertIs<Capability.Supported<*>>(windowCapabilities.contentSize)
                assertIs<Capability.Supported<*>>(windowCapabilities.minimumSize)
                assertIs<Capability.Supported<*>>(windowCapabilities.maximumSize)
                assertIs<Capability.Supported<*>>(windowCapabilities.resizable)
                assertIs<Capability.Supported<*>>(windowCapabilities.closeInterception)
                assertIs<Capability.Supported<*>>(windowCapabilities.platformAccess)

                val surfaceCapabilities = window.surface.capabilities.value
                listOf<Capability<*>>(
                    surfaceCapabilities.cursor,
                    surfaceCapabilities.customCursor,
                    surfaceCapabilities.pointerCapture,
                    surfaceCapabilities.hitTesting,
                    surfaceCapabilities.inputDefaultBehavior,
                    surfaceCapabilities.handlerInteractions,
                    surfaceCapabilities.armedInteractions,
                    surfaceCapabilities.platformAccess,
                ).forEach { capability -> assertIs<Capability.Unsupported>(capability) }
                val stateBeforeUnsupportedUpdate = window.surface.state.value
                val unsupportedUpdate = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
                    window.surface.apply(
                        SurfaceUpdate(
                            cursor = PropertyChange.Set(CursorStyle.Hidden),
                            hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
                            inputDefaultBehavior = PropertyChange.Set(
                                InputDefaultBehavior.SuppressWhenPossible,
                            ),
                        ),
                    ).appKitSuccessValue(),
                )
                assertEquals(
                    setOf(SurfaceProperty.Cursor, SurfaceProperty.HitTesting, SurfaceProperty.InputDefaultBehavior),
                    unsupportedUpdate.rejected.map { it.field }.toSet(),
                )
                unsupportedUpdate.rejected.forEach { rejected ->
                    assertEquals(
                        KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                        rejected.failure,
                    )
                }
                assertEquals(stateBeforeUnsupportedUpdate, unsupportedUpdate.state)
                assertEquals(stateBeforeUnsupportedUpdate, window.surface.state.value)
                assertEquals(
                    KadreResult.Success(Unit),
                    window.surface.requestRedraw(),
                )
                val inputCapabilities = window.surface.input.state.value.capabilities
                assertEquals(FeatureAvailability.Unsupported, inputCapabilities.keyboard)
                assertEquals(FeatureAvailability.Unsupported, inputCapabilities.pointer)
                assertEquals(FeatureAvailability.Unsupported, inputCapabilities.touch)
                assertEquals(FeatureAvailability.Unsupported, inputCapabilities.gestures)
                assertEquals(FeatureAvailability.Unsupported, inputCapabilities.dragAndDrop)
                assertIs<Capability.Unsupported>(inputCapabilities.textInput)
                assertIs<Capability.Unsupported>(inputCapabilities.rawInput)
                Unit
            } finally {
                session.close()
                session.awaitTermination()
                parentScope.cancel()
            }
        }

    @Test
    fun publicAppKitWindowDefersNativeCloseAndUsesTheFirstApplicationResponse() =
        kotlinx.coroutines.runBlocking {
            val nativeCloseStarted = CountDownLatch(1)
            val allowNativeClose = CountDownLatch(1)
            val port = DeterministicAppKitNativeWindowPort(
                "public-native-close",
                beforeCloseWindow = {
                    nativeCloseStarted.countDown()
                    check(allowNativeClose.await(2, TimeUnit.SECONDS))
                },
            )
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                val window = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "intercepted")).appKitSuccessValue().await(),
                ).window
                val rejectedEvent = async(start = CoroutineStart.UNDISPATCHED) {
                    window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
                }

                assertFalse(port.requestNativeClose("intercepted"))
                val rejected = withTimeout(2.seconds) { rejectedEvent.await() }
                assertEquals(
                    WindowCloseResponseOutcome.KeptOpen,
                    window.respondToCloseRequest(rejected.requestId, WindowCloseDecision.Reject).appKitSuccessValue(),
                )
                assertEquals(WindowPhase.Open, window.state.value.phase)
                assertEquals(emptyList(), port.closedWindowTitles)

                val acceptedEvent = async(start = CoroutineStart.UNDISPATCHED) {
                    window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
                }
                assertFalse(port.requestNativeClose("intercepted"))
                val accepted = withTimeout(2.seconds) { acceptedEvent.await() }
                val response = assertIs<WindowCloseResponseOutcome.Closing>(
                    window.respondToCloseRequest(accepted.requestId, WindowCloseDecision.Accept).appKitSuccessValue(),
                )
                assertTrue(nativeCloseStarted.await(2, TimeUnit.SECONDS))
                assertEquals(
                    WindowCloseResponseOutcome.Closing(response.operationId),
                    window.respondToCloseRequest(accepted.requestId, WindowCloseDecision.Accept).appKitSuccessValue(),
                )
                assertEquals(
                    WindowCloseResponseOutcome.AlreadyResolved,
                    window.respondToCloseRequest(accepted.requestId, WindowCloseDecision.Reject).appKitSuccessValue(),
                )

                allowNativeClose.countDown()
                withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
                assertEquals(listOf("intercepted"), port.closedWindowTitles)
                assertEquals(listOf("intercepted"), port.windowWillCloseTitles)
                assertEquals(emptyList(), windows.state.value.windows)
            } finally {
                allowNativeClose.countDown()
                session.close()
                session.awaitTermination()
                parentScope.cancel()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun repeatedNativeCloseCallbacksStayCoalescedUntilThePendingRequestIsRejected() =
        kotlinx.coroutines.runBlocking {
            val blockerCloseStarted = CountDownLatch(1)
            val allowBlockerClose = CountDownLatch(1)
            val betweenCallbacksStarted = CountDownLatch(1)
            val allowBetweenCallbacks = CountDownLatch(1)
            val port = DeterministicAppKitNativeWindowPort(
                name = "coalesced-native-close",
                beforeCloseWindow = { title ->
                    if (title == "blocker") {
                        blockerCloseStarted.countDown()
                        check(allowBlockerClose.await(2, TimeUnit.SECONDS))
                    }
                },
            )
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()
            val closeRequests = Channel<WindowEvent.CloseRequested>(Channel.UNLIMITED)
            var collector: kotlinx.coroutines.Job? = null

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                val blocker = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "blocker")).appKitSuccessValue().await(),
                ).window
                val target = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "target")).appKitSuccessValue().await(),
                ).window
                collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    target.events.filterIsInstance<WindowEvent.CloseRequested>().collect(closeRequests::send)
                }

                assertIs<WindowCloseOutcome.Accepted>(blocker.close().appKitSuccessValue())
                assertTrue(blockerCloseStarted.await(2, TimeUnit.SECONDS))
                assertFalse(port.requestNativeClose("target"))
                val betweenCallbacks = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    target.withDesktopHandle {
                        betweenCallbacksStarted.countDown()
                        check(allowBetweenCallbacks.await(2, TimeUnit.SECONDS))
                    }
                }
                assertFalse(port.requestNativeClose("target"))

                allowBlockerClose.countDown()
                val first = withTimeout(2.seconds) { closeRequests.receive() }
                assertTrue(betweenCallbacksStarted.await(2, TimeUnit.SECONDS))
                assertEquals(
                    WindowCloseResponseOutcome.KeptOpen,
                    target.respondToCloseRequest(first.requestId, WindowCloseDecision.Reject).appKitSuccessValue(),
                )
                val afterCallbacks = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    target.withDesktopHandle { }
                }
                allowBetweenCallbacks.countDown()
                assertEquals(KadreResult.Success(Unit), betweenCallbacks.await())
                assertEquals(KadreResult.Success(Unit), afterCallbacks.await())
                assertNull(closeRequests.tryReceive().getOrNull())

                assertFalse(port.requestNativeClose("target"))
                val accepted = withTimeout(2.seconds) { closeRequests.receive() }
                assertIs<WindowCloseResponseOutcome.Closing>(
                    target.respondToCloseRequest(accepted.requestId, WindowCloseDecision.Accept).appKitSuccessValue(),
                )
                withTimeout(2.seconds) { target.state.first { it.phase == WindowPhase.Closed } }
                Unit
            } finally {
                allowBlockerClose.countDown()
                allowBetweenCallbacks.countDown()
                collector?.cancel()
                closeRequests.close()
                session.close()
                session.awaitTermination()
                parentScope.cancel()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun forcedNativeTerminalWinsBeforeAQueuedRejectResponse() = kotlinx.coroutines.runBlocking {
        val port = DeterministicAppKitNativeWindowPort("forced-native-terminal")
        val provider = AppKitBackendProvider.forTesting(
            EmbeddedNativeApplication(),
            AppKitProcessBroker(),
            windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
        ) { true }
        val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val observedWindows = CompletableDeferred<WindowManager>()
        val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()
        val leaseStarted = CountDownLatch(1)
        val allowLeaseToReturn = CountDownLatch(1)

        try {
            val windows = withTimeout(2.seconds) { observedWindows.await() }
            val window = assertIs<WindowRequestOutcome.OpenedHere>(
                windows.requestWindow(WindowSpec(title = "forced")).appKitSuccessValue().await(),
            ).window
            val closeRequested = async(start = CoroutineStart.UNDISPATCHED) {
                window.events.filterIsInstance<WindowEvent.CloseRequested>().first()
            }
            assertFalse(port.requestNativeClose("forced"))
            val request = withTimeout(2.seconds) { closeRequested.await() }
            val lease = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                window.withDesktopHandle {
                    leaseStarted.countDown()
                    check(allowLeaseToReturn.await(2, TimeUnit.SECONDS))
                }
            }
            assertTrue(leaseStarted.await(2, TimeUnit.SECONDS))

            port.emitNativeClosed("forced")

            assertEquals(
                WindowCloseResponseOutcome.TooLate,
                window.respondToCloseRequest(request.requestId, WindowCloseDecision.Reject)
                    .appKitSuccessValue(),
            )
            assertIs<WindowCloseOutcome.Accepted>(window.close().appKitSuccessValue())
            assertEquals(
                WindowCloseResponseOutcome.TooLate,
                window.respondToCloseRequest(request.requestId, WindowCloseDecision.Accept)
                    .appKitSuccessValue(),
            )
            allowLeaseToReturn.countDown()
            assertEquals(KadreResult.Success(Unit), lease.await())
            withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
            withTimeout(2.seconds) { windows.state.first { it.windows.isEmpty() } }
            Unit
        } finally {
            allowLeaseToReturn.countDown()
            session.close()
            session.awaitTermination()
            parentScope.cancel()
        }
    }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitDesktopHandleRunsOnTheOwnerThreadAndCloseWaitsForItsLease() =
        kotlinx.coroutines.runBlocking {
            val port = OwnerThreadAppKitNativeWindowPort("public-desktop-handle")
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()
            val callbackStarted = CountDownLatch(1)
            val allowCallbackToReturn = CountDownLatch(1)

            try {
                val windows = withTimeout(2.seconds) { observedWindows.await() }
                val window = assertIs<WindowRequestOutcome.OpenedHere>(
                    windows.requestWindow(WindowSpec(title = "public-leased")).appKitSuccessValue().await(),
                ).window
                val lease = async(Dispatchers.Default) {
                    window.withDesktopHandle { handle ->
                        assertTrue(port.isMainThread())
                        val appKitHandle = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                        assertEquals(0xA11uL, appKitHandle.nsWindowAddress)
                        assertEquals(0xB22uL, appKitHandle.nsViewAddress)
                        callbackStarted.countDown()
                        check(allowCallbackToReturn.await(2, TimeUnit.SECONDS))
                        "leased"
                    }
                }
                assertTrue(callbackStarted.await(2, TimeUnit.SECONDS))

                assertIs<WindowCloseOutcome.Accepted>(window.close().appKitSuccessValue())
                assertEquals(emptyList(), port.closedWindowTitles)

                allowCallbackToReturn.countDown()
                assertEquals(KadreResult.Success("leased"), lease.await())
                withTimeout(2.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
                assertEquals(listOf("public-leased"), port.closedWindowTitles)
                assertEquals(
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
                    window.withDesktopHandle { error("closed windows must not invoke the callback") },
                )
            } finally {
                allowCallbackToReturn.countDown()
                session.close()
                session.awaitTermination()
                parentScope.cancel()
                port.close()
            }
        }

    @Test
    fun standaloneLastWindowPolicyIgnoresHeadlessStartupAndStopsAfterItsArmedTransition() {
        val native = StopDrivenNativeApplication()
        val port = DeterministicAppKitNativeWindowPort("standalone-last-window")
        val provider = AppKitBackendProvider.forTesting(
            native,
            AppKitProcessBroker(),
            windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
        ) { true }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    KadreApplication {
                        assertEquals(0, native.stopCount)
                        val window = assertIs<WindowRequestOutcome.OpenedHere>(
                            windows.requestWindow(WindowSpec(title = "armed")).appKitSuccessValue().await(),
                        ).window
                        assertEquals(0, native.stopCount)
                        window.close()
                        window.state.first { it.phase == WindowPhase.Closed }
                        kotlinx.coroutines.awaitCancellation()
                    }
                },
                stopWhenLastWindowClosed = true,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostRequested)),
            result,
        )
        assertEquals(1, native.stopCount)
        assertEquals(listOf("armed"), port.closedWindowTitles)
    }

    @Test
    fun publicAppKitSessionTeardownDetachesPendingThenCommittedWindowsBeforeDelegateRevocation() =
        kotlinx.coroutines.runBlocking {
            val logicalStateAtRevocation = java.util.concurrent.CopyOnWriteArrayList<List<String>>()
            val pendingPreparationStarted = CountDownLatch(1)
            val allowPendingPreparation = CountDownLatch(1)
            lateinit var windows: WindowManager
            val port = DeterministicAppKitNativeWindowPort(
                name = "public-teardown",
                beforeCreateWindow = { spec ->
                    if (spec.title == "pending") {
                        pendingPreparationStarted.countDown()
                        check(allowPendingPreparation.await(2, TimeUnit.SECONDS))
                    }
                },
                onDelegateRevoked = {
                    logicalStateAtRevocation += windows.state.value.windows.map { it.state.value.title }
                },
            )
            val provider = AppKitBackendProvider.forTesting(
                EmbeddedNativeApplication(),
                AppKitProcessBroker(),
                windowDriverFactory = AppKitWindowRuntimeDriverFactory { port },
            ) { true }
            val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            val observedWindows = CompletableDeferred<WindowManager>()
            val session = provider.attach(publicWindowRequest(parentScope, observedWindows)).requireSession()

            try {
                windows = withTimeout(2.seconds) { observedWindows.await() }
                windows.requestWindow(WindowSpec(title = "first")).appKitSuccessValue().await()
                windows.requestWindow(WindowSpec(title = "second")).appKitSuccessValue().await()
                val pending = windows.requestWindow(WindowSpec(title = "pending")).appKitSuccessValue()
                assertTrue(pendingPreparationStarted.await(2, TimeUnit.SECONDS))

                session.close()
                session.awaitTermination()
                assertEquals(WindowRequestOutcome.RequesterDetached, pending.await())
                allowPendingPreparation.countDown()
                withTimeout(2.seconds) {
                    while (port.closedWindowTitles.size < 3 || logicalStateAtRevocation.size < 3) yield()
                }

                assertEquals(listOf("pending", "second", "first"), port.closedWindowTitles)
                assertTrue(logicalStateAtRevocation.all(List<String>::isEmpty))
                assertEquals(emptyList(), windows.state.value.windows)
            } finally {
                allowPendingPreparation.countDown()
                session.close()
                parentScope.cancel()
            }
        }

    @Test
    fun unavailableProviderDoesNotTouchTheNativeBridgeOrFactory() {
        var factoryInvoked = false
        val native = RecordingNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { false }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    factoryInvoked = true
                    KadreApplication { }
                },
                true,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)),
            result,
        )
        assertEquals(0, native.mainThreadCheckCount)
        assertEquals(0, native.runCount)
        assertFalse(factoryInvoked)
    }

    @Test
    fun applicationStopStopsTheNativeLoopAndReturnsTheSessionOutcome() {
        val native = StopDrivenNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory { KadreApplication { requestStop() } },
                stopWhenLastWindowClosed = true,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested)),
            result,
        )
        assertEquals(2, native.trace.size)
        assertEquals(1, native.trace.count { it == "run" })
        assertEquals(1, native.trace.count { it == "stop" })
        assertEquals(1, native.stopCount)
    }

    @Test
    fun nativeStopFailureBecomesASessionOutcomeAndReleasesOwnership() {
        val broker = AppKitProcessBroker()
        val native = FailingStopNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, broker) { true }
        val executor = Executors.newSingleThreadExecutor()

        try {
            val result = executor.submit<KadreResult<SessionOutcome>> {
                provider.run(
                    DesktopStandaloneRequest(
                        KadreApplicationFactory { KadreApplication { requestStop() } },
                        stopWhenLastWindowClosed = true,
                        KadrePolicies.Default,
                    ),
                )
            }.get(2, TimeUnit.SECONDS)

            assertEquals(
                KadreResult.Success(
                    SessionOutcome.Failed(
                        KadreFailure.PlatformFailure(
                            KadrePlatform.AppKit,
                            "appkit-host",
                            "stop-exception",
                        ),
                    ),
                ),
                result,
            )
            assertEquals(1, native.stopCount)
            assertEquals(1, native.emergencyStopCount)
            assertTrue(broker.tryAcquireStandalone()?.also { it.close() } != null)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun nativeCancellationAfterAdmissionReturnsHostDetachedAndReleasesOwnership() {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val applicationStarted = CountDownLatch(1)
        val applicationCancelled = CountDownLatch(1)
        val broker = AppKitProcessBroker()
        val native = CancellationNativeApplication(applicationStarted, cancellation)
        val provider = AppKitBackendProvider.forTesting(native, broker) { true }

        // The native loop waits for this application to start, proving that cancellation occurs
        // after session admission and must therefore be represented by the session outcome.
        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    KadreApplication {
                        applicationStarted.countDown()
                        try {
                            kotlinx.coroutines.awaitCancellation()
                        } finally {
                            applicationCancelled.countDown()
                        }
                    }
                },
                false,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostDetached)),
            result,
        )
        assertTrue(applicationCancelled.await(2, TimeUnit.SECONDS))
        assertEquals(0, native.stopCount)
        assertTrue(broker.tryAcquireStandalone()?.also { it.close() } != null)
    }

    @Test
    fun nativeFailureAfterAdmissionBecomesASessionOutcome() {
        val nativeFailure = IllegalStateException("native")
        val native = RecordingNativeApplication(runFailure = nativeFailure)
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory { KadreApplication { kotlinx.coroutines.awaitCancellation() } },
                stopWhenLastWindowClosed = false,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(
                SessionOutcome.Failed(
                    KadreFailure.PlatformFailure(KadrePlatform.AppKit, "appkit-host", "run-exception"),
                ),
            ),
            result,
        )
        assertEquals(0, native.stopCount)
    }

    @Test
    fun applicationFailureStopsTheNativeLoopAndReturnsTheSessionOutcome() {
        val native = StopDrivenNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    KadreApplication { throw IllegalStateException("application") }
                },
                stopWhenLastWindowClosed = false,
                KadrePolicies.Default,
            ),
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Failed(KadreFailure.ApplicationFailure)),
            result,
        )
        assertEquals(2, native.trace.size)
        assertEquals(1, native.trace.count { it == "run" })
        assertEquals(1, native.trace.count { it == "stop" })
        assertEquals(1, native.stopCount)
    }

    @Test
    fun standaloneOwnershipReturnsBusyAndCanBeReused() {
        val broker = AppKitProcessBroker()
        val first = broker.tryAcquireStandalone()
        assertIs<AppKitProcessBroker.StandaloneLease>(first)
        assertNull(broker.tryAcquireStandalone())

        val provider = AppKitBackendProvider.forTesting(RecordingNativeApplication(), broker) { true }
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            provider.run(
                DesktopStandaloneRequest(
                    KadreApplicationFactory { KadreApplication { } },
                    true,
                    KadrePolicies.Default,
                ),
            ),
        )

        first.close()
        val second = broker.tryAcquireStandalone()
        assertIs<AppKitProcessBroker.StandaloneLease>(second)
        first.close()
        assertNull(broker.tryAcquireStandalone())
        second.close()
        assertTrue(broker.tryAcquireStandalone()?.also { it.close() } != null)
    }

    @Test
    fun sequentialRunsReleaseAllProcessAndSessionState() {
        val native = RecordingNativeApplication()
        val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
        val request = DesktopStandaloneRequest(
            KadreApplicationFactory { KadreApplication { kotlinx.coroutines.awaitCancellation() } },
            true,
            KadrePolicies.Default,
        )

        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostDetached)),
            provider.run(request),
        )
        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.HostDetached)),
            provider.run(request),
        )
        assertEquals(2, native.runCount)
    }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitWindowActivatesOnlyTheSevenProvenUpdateCapabilitiesOnMacOs() =
        runPublicAppKitGeometrySession {
            val window = openPublicGeometryWindow("public-geometry-capabilities")
            val range = LogicalSizeRange(null, null, null)

            assertEquals(
                Capability.Supported(Unit, FeatureAvailability.Available),
                window.capabilities.value.title,
            )
            assertEquals(
                Capability.Supported(range, FeatureAvailability.Available),
                window.capabilities.value.contentSize,
            )
            assertEquals(
                Capability.Supported(range, FeatureAvailability.Available),
                window.capabilities.value.minimumSize,
            )
            assertEquals(
                Capability.Supported(range, FeatureAvailability.Available),
                window.capabilities.value.maximumSize,
            )
            assertEquals(
                Capability.Supported(Unit, FeatureAvailability.Available),
                window.capabilities.value.resizable,
            )
            assertEquals(
                Capability.Supported(
                    setOf(WindowDecorations.System, WindowDecorations.Borderless),
                    FeatureAvailability.Available,
                ),
                window.capabilities.value.decorations,
            )
            assertEquals(
                Capability.Supported(
                    setOf(
                        WindowSystemButtons.All,
                        WindowSystemButtons.CloseOnly,
                        WindowSystemButtons.None,
                    ),
                    FeatureAvailability.Available,
                ),
                window.capabilities.value.systemButtons,
            )
            assertNull(window.state.value.outerBounds)
            listOf<Capability<*>>(
                window.capabilities.value.outerPosition,
                window.capabilities.value.fullscreen,
                window.capabilities.value.level,
                window.capabilities.value.transparency,
                window.capabilities.value.blurBehind,
                window.capabilities.value.icon,
                window.capabilities.value.attention,
                window.capabilities.value.contentProtection,
            ).forEach { capability -> assertIs<Capability.Unsupported>(capability) }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitWindowChromeUsesGeneratedBindingsAndOneCorrelatedUpdateOnMacOs() =
        runPublicAppKitGeometrySession {
            val window = openPublicGeometryWindow(
                WindowSpec(
                    title = "public-chrome-before",
                    decorations = WindowDecorations.System,
                    systemButtons = WindowSystemButtons.All,
                ),
            )
            val events = Channel<WindowEvent>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.collect(events::send)
            }
            try {
                assertEquals(WindowSystemButtons.All, window.state.value.systemButtons)
                assertEquals(
                    NativePublicWindowChrome(
                        decorations = WindowDecorations.System,
                        resizable = true,
                        closeHidden = false,
                        miniaturizeHidden = false,
                        zoomHidden = false,
                    ),
                    readNativeWindowChrome(window),
                )

                val outcome = assertIs<WindowUpdateOutcome.Applied>(
                    window.apply(
                        WindowUpdate(
                            title = PropertyChange.Set("public-chrome-borderless"),
                            contentSize = PropertyChange.Set(LogicalSize(420.0, 260.0)),
                            decorations = PropertyChange.Set(WindowDecorations.Borderless),
                            systemButtons = PropertyChange.Set(WindowSystemButtons.CloseOnly),
                        ),
                    ).appKitSuccessValue(),
                )
                assertEquals(WindowDecorations.Borderless, outcome.state.decorations)
                assertEquals(WindowSystemButtons.None, outcome.state.systemButtons)
                assertEquals(outcome.state, window.state.value)
                assertEquals(
                    NativePublicWindowChrome(
                        decorations = WindowDecorations.Borderless,
                        resizable = true,
                        closeHidden = null,
                        miniaturizeHidden = null,
                        zoomHidden = null,
                    ),
                    readNativeWindowChrome(window),
                )
                val geometry = withTimeout(5.seconds) {
                    assertIs<WindowEvent.GeometryChanged>(events.receive())
                }
                val properties = withTimeout(5.seconds) {
                    assertIs<WindowEvent.PropertiesChanged>(events.receive())
                }
                assertEquals(outcome.operationId, geometry.operationId)
                assertEquals(outcome.operationId, properties.operationId)
                assertEquals(outcome.state, geometry.state)
                assertEquals(outcome.state, properties.state)
                assertEquals(
                    setOf(WindowProperty.Title, WindowProperty.Decorations, WindowProperty.SystemButtons),
                    properties.changed,
                )

                val systemOutcome = assertIs<WindowUpdateOutcome.Applied>(
                    window.apply(
                        WindowUpdate(
                            decorations = PropertyChange.Set(WindowDecorations.System),
                            systemButtons = PropertyChange.Set(WindowSystemButtons.CloseOnly),
                            resizable = PropertyChange.Set(false),
                        ),
                    ).appKitSuccessValue(),
                )
                assertEquals(WindowDecorations.System, systemOutcome.state.decorations)
                assertEquals(WindowSystemButtons.CloseOnly, systemOutcome.state.systemButtons)
                assertFalse(systemOutcome.state.resizable)
                assertEquals(
                    NativePublicWindowChrome(
                        decorations = WindowDecorations.System,
                        resizable = false,
                        closeHidden = false,
                        miniaturizeHidden = true,
                        zoomHidden = true,
                    ),
                    readNativeWindowChrome(window),
                )
                val systemProperties = withTimeout(5.seconds) {
                    assertIs<WindowEvent.PropertiesChanged>(events.receive())
                }
                assertEquals(systemOutcome.operationId, systemProperties.operationId)
                assertEquals(systemOutcome.state, systemProperties.state)
                assertEquals(
                    setOf(WindowProperty.Decorations, WindowProperty.SystemButtons, WindowProperty.Resizable),
                    systemProperties.changed,
                )
            } finally {
                collector.cancel()
                events.close()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitWindowTitleUsesTheGeneratedBindingAndOneCorrelatedUpdateOnMacOs() =
        runPublicAppKitGeometrySession {
            val window = openPublicGeometryWindow("public-title-before")
            val events = Channel<WindowEvent>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.collect(events::send)
            }
            try {
                assertEquals(
                    Capability.Supported(Unit, FeatureAvailability.Available),
                    window.capabilities.value.title,
                )

                val title = "public-title-after"
                val contentSize = LogicalSize(420.0, 260.0)
                val outcome = assertIs<WindowUpdateOutcome.Applied>(
                    window.apply(
                        WindowUpdate(
                            title = PropertyChange.Set(title),
                            contentSize = PropertyChange.Set(contentSize),
                        ),
                    ).appKitSuccessValue(),
                )

                assertEquals(title, outcome.state.title)
                assertEquals(contentSize, outcome.state.contentSize)
                assertEquals(outcome.state, window.state.value)
                assertEquals(
                    KadreResult.Success(Unit),
                    window.withDesktopHandle { handle ->
                        val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                        ObjCRuntime.autoreleasePool {
                            assertEquals(
                                title,
                                NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()))
                                    .titleAsString(),
                            )
                        }
                    },
                )

                val geometry = withTimeout(5.seconds) {
                    assertIs<WindowEvent.GeometryChanged>(events.receive())
                }
                val properties = withTimeout(5.seconds) {
                    assertIs<WindowEvent.PropertiesChanged>(events.receive())
                }
                assertEquals(outcome.operationId, geometry.operationId)
                assertEquals(outcome.operationId, properties.operationId)
                assertEquals(outcome.state, geometry.state)
                assertEquals(outcome.state, properties.state)
                assertEquals(setOf(WindowProperty.Title), properties.changed)
            } finally {
                collector.cancel()
                events.close()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitWindowApplyUsesGeneratedNativeGeometryAndCorrelatesOperationOnMacOs() =
        runPublicAppKitGeometrySession {
            val window = openPublicGeometryWindow("public-geometry-apply")
            val events = Channel<WindowEvent>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.collect(events::send)
            }
            try {
                val target = LogicalSize(420.0, 260.0)
                val minimum = LogicalSize(200.0, 120.0)
                val maximum = LogicalSize(800.0, 600.0)
                val outcome = assertIs<WindowUpdateOutcome.Applied>(
                    window.apply(
                        WindowUpdate(
                            contentSize = PropertyChange.Set(target),
                            minimumSize = PropertyChange.Set(minimum),
                            maximumSize = PropertyChange.Set(maximum),
                            resizable = PropertyChange.Set(false),
                        ),
                    ).appKitSuccessValue(),
                )
                assertEquals(target, outcome.state.contentSize)
                assertEquals(minimum, outcome.state.minimumSize)
                assertEquals(maximum, outcome.state.maximumSize)
                assertFalse(outcome.state.resizable)
                assertEquals(outcome.state, window.state.value)

                assertEquals(
                    KadreResult.Success(Unit),
                    window.withDesktopHandle { handle ->
                        val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                        ObjCRuntime.autoreleasePool {
                            val native = NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()))
                            val effectiveContentSize = native.contentRectForFrameRect(native.frame()).size
                            assertEquals(target.width, effectiveContentSize.width)
                            assertEquals(target.height, effectiveContentSize.height)
                            val effectiveMinimum = native.contentMinSize()
                            assertEquals(minimum.width, effectiveMinimum.width)
                            assertEquals(minimum.height, effectiveMinimum.height)
                            val effectiveMaximum = native.contentMaxSize()
                            assertEquals(maximum.width, effectiveMaximum.width)
                            assertEquals(maximum.height, effectiveMaximum.height)
                            assertFalse(
                                native.styleMask().contains(NSWindowStyleMask.NSWindowStyleMaskResizable),
                            )
                        }
                    },
                )
                val geometry = withTimeout(5.seconds) {
                    assertIs<WindowEvent.GeometryChanged>(events.receive())
                }
                val properties = withTimeout(5.seconds) {
                    assertIs<WindowEvent.PropertiesChanged>(events.receive())
                }
                assertEquals(outcome.operationId, geometry.operationId)
                assertEquals(outcome.operationId, properties.operationId)
                assertEquals(outcome.state, geometry.state)
                assertEquals(outcome.state, properties.state)
            } finally {
                collector.cancel()
                events.close()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitClearOfInitialConstraintsRestoresNativeDefaultsAndPublishesNullOnMacOs() =
        runPublicAppKitGeometrySession {
            val defaultWindow = openPublicGeometryWindow("public-geometry-native-defaults")
            val nativeDefaults = readNativeConstraints(defaultWindow)
            assertIs<WindowCloseOutcome.Accepted>(defaultWindow.close().appKitSuccessValue())
            withTimeout(5.seconds) {
                defaultWindow.state.first { it.phase == WindowPhase.Closed }
            }
            val minimum = LogicalSize(240.0, 160.0)
            val maximum = LogicalSize(720.0, 520.0)
            val window = openPublicGeometryWindow(
                WindowSpec(
                    title = "public-geometry-clear-initial",
                    contentSize = LogicalSize(420.0, 280.0),
                    minimumSize = minimum,
                    maximumSize = maximum,
                ),
            )
            assertEquals(
                NativeWindowConstraints(
                    minimumWidth = minimum.width,
                    minimumHeight = minimum.height,
                    maximumWidth = maximum.width,
                    maximumHeight = maximum.height,
                ),
                readNativeConstraints(window),
            )

            val outcome = assertIs<WindowUpdateOutcome.Applied>(
                window.apply(
                    WindowUpdate(
                        minimumSize = PropertyChange.Clear,
                        maximumSize = PropertyChange.Clear,
                    ),
                ).appKitSuccessValue(),
            )

            assertNull(outcome.state.minimumSize)
            assertNull(outcome.state.maximumSize)
            assertEquals(outcome.state, window.state.value)
            assertEquals(nativeDefaults, readNativeConstraints(window))
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitWindowRejectsInvalidGeometryBeforeNativeCommitOnMacOs() =
        runPublicAppKitGeometrySession {
            val window = openPublicGeometryWindow("public-geometry-invalid")
            val beforeState = window.state.value
            val beforeNativeSize = readNativeContentSize(window)
            val events = Channel<WindowEvent.GeometryChanged>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.filterIsInstance<WindowEvent.GeometryChanged>().collect(events::send)
            }
            try {
                assertEquals(
                    KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")),
                    window.apply(
                        WindowUpdate(
                            minimumSize = PropertyChange.Set(LogicalSize(600.0, 400.0)),
                            maximumSize = PropertyChange.Set(LogicalSize(500.0, 300.0)),
                        ),
                    ),
                )
                assertEquals(beforeState, window.state.value)
                assertEquals(beforeNativeSize, readNativeContentSize(window))
                assertNull(withTimeoutOrNull(200.milliseconds) { events.receive() })
            } finally {
                collector.cancel()
                events.close()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitWindowGeometryDoesNotCrossBetweenTwoWindowsOnMacOs() =
        runPublicAppKitGeometrySession {
            val first = openPublicGeometryWindow("public-geometry-first")
            val second = openPublicGeometryWindow("public-geometry-second")
            val firstEvents = Channel<WindowEvent.GeometryChanged>(Channel.UNLIMITED)
            val secondEvents = Channel<WindowEvent.GeometryChanged>(Channel.UNLIMITED)
            val firstCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                first.events.filterIsInstance<WindowEvent.GeometryChanged>().collect(firstEvents::send)
            }
            val secondCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                second.events.filterIsInstance<WindowEvent.GeometryChanged>().collect(secondEvents::send)
            }
            try {
                val beforeSecondState = second.state.value
                val observedFirstSize = LogicalSize(380.0, 240.0)
                setNativeContentSize(first, observedFirstSize)
                val observedFirstState = withTimeout(5.seconds) {
                    first.state.first { it.contentSize == observedFirstSize }
                }
                val firstObservation = withTimeout(5.seconds) { firstEvents.receive() }
                assertNull(firstObservation.operationId)
                assertEquals(observedFirstState, firstObservation.state)
                assertEquals(observedFirstSize, readNativeContentSize(first))
                assertEquals(beforeSecondState, second.state.value)
                assertNull(withTimeoutOrNull(200.milliseconds) { secondEvents.receive() })

                val requestedSecondSize = LogicalSize(520.0, 360.0)
                val outcome = assertIs<WindowUpdateOutcome.Applied>(
                    second.apply(
                        WindowUpdate(
                            contentSize = PropertyChange.Set(requestedSecondSize),
                            minimumSize = PropertyChange.Set(LogicalSize(400.0, 280.0)),
                            maximumSize = PropertyChange.Set(LogicalSize(640.0, 480.0)),
                        ),
                    ).appKitSuccessValue(),
                )
                val secondOperation = withTimeout(5.seconds) { secondEvents.receive() }
                assertEquals(outcome.operationId, secondOperation.operationId)
                assertEquals(outcome.state, secondOperation.state)
                assertEquals(requestedSecondSize, second.state.value.contentSize)
                assertEquals(requestedSecondSize, readNativeContentSize(second))
                assertEquals(observedFirstState, first.state.value)
                assertEquals(observedFirstSize, readNativeContentSize(first))
                assertNull(withTimeoutOrNull(200.milliseconds) { firstEvents.receive() })
            } finally {
                firstCollector.cancel()
                secondCollector.cancel()
                firstEvents.close()
                secondEvents.close()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun nativeExternalResizeUpdatesWindowStateWithNullOperationIdOnMacOs() =
        runPublicAppKitGeometrySession {
            val window = openPublicGeometryWindow("public-geometry-external")
            val events = Channel<WindowEvent>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.collect(events::send)
            }
            try {
                val resized = LogicalSize(380.0, 240.0)
                assertEquals(
                    KadreResult.Success(Unit),
                    window.withDesktopHandle { handle ->
                        val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                        ObjCRuntime.autoreleasePool {
                            NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()))
                                .setContentSize(NSSize(resized.width, resized.height))
                        }
                    },
                )
                val state = withTimeout(5.seconds) {
                    window.state.first { it.contentSize == resized }
                }
                val event = withTimeout(5.seconds) {
                    assertIs<WindowEvent.GeometryChanged>(events.receive())
                }
                assertEquals(state, event.state)
                assertNull(event.operationId)
            } finally {
                collector.cancel()
                events.close()
            }
        }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun publicAppKitWindowGeometryEventsFollowSessionPolicyOnMacOs() {
        val policyWasApplied = AtomicBoolean(false)
        runPublicAppKitGeometrySession(
            KadrePolicies.Default.copy(
                window = KadrePolicies.Default.window.copy(
                    geometryChanges = ContinuousDelivery.Buffered(
                        capacity = 2,
                        onOverflow = ContinuousOverflowAction.FailSession,
                    ),
                ),
            ),
        ) {
            val window = openPublicGeometryWindow("public-geometry-policy")
            val enteredFirstEvent = CompletableDeferred<Unit>()
            val releaseCollector = CompletableDeferred<Unit>()
            val events = mutableListOf<WindowEvent.GeometryChanged>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.filterIsInstance<WindowEvent.GeometryChanged>().collect { event ->
                    events += event
                    if (!enteredFirstEvent.isCompleted) {
                        enteredFirstEvent.complete(Unit)
                        releaseCollector.await()
                    }
                }
            }
            try {
                setNativeContentSize(window, LogicalSize(340.0, 220.0))
                withTimeout(5.seconds) { enteredFirstEvent.await() }
                setNativeContentSize(window, LogicalSize(390.0, 250.0))
                val finalSize = LogicalSize(440.0, 280.0)
                setNativeContentSize(window, finalSize)
                releaseCollector.complete(Unit)

                withTimeout(5.seconds) {
                    while (events.size < 3 || window.state.value.contentSize != finalSize) yield()
                }
                assertEquals(
                    listOf(LogicalSize(340.0, 220.0), LogicalSize(390.0, 250.0), finalSize),
                    events.map { it.state.contentSize },
                )
                assertTrue(events.all { it.operationId == null })
                policyWasApplied.set(true)
            } finally {
                releaseCollector.complete(Unit)
                collector.cancel()
            }
        }
        assertTrue(policyWasApplied.get(), "the public session must use its supplied WindowDeliveryPolicy")
    }

    @OptIn(
        org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
        org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    )
    @Test
    fun realKffiStandaloneLoopStartsAndStopsOnMacOs() {
        if (!isMacOs()) {
            assertFalse(AppKitBackendProvider().isAvailable())
            return
        }
        val native = KffiAppKitNativeApplication()
        val provider = AppKitBackendProvider.forTesting(
            native,
            AppKitProcessBroker(),
        ) { true }
        val stopRequestedOffMainThread = AtomicBoolean(false)
        val publicHandleObserved = AtomicBoolean(false)
        val nativeRejectObserved = AtomicBoolean(false)
        val terminalCloseObserved = AtomicBoolean(false)
        val nativeSurfaceResizeObserved = AtomicBoolean(false)
        val nativeSurfaceFocusObserved = AtomicBoolean(false)
        val nativeSurfaceVisibilityObserved = AtomicBoolean(false)
        val nativeSurfaceRedrawObserved = AtomicBoolean(false)
        val nativeSurfaceTerminalObserved = AtomicBoolean(false)
        val nativeInputEventStateObserved = AtomicBoolean(false)
        val nativeInputFocusResetObserved = AtomicBoolean(false)
        val nativeInputIsolationObserved = AtomicBoolean(false)
        val proofStage = AtomicReference("not-started")
        val proofFailure = AtomicReference<Throwable?>(null)

        val result = provider.run(
            DesktopStandaloneRequest(
                KadreApplicationFactory {
                    KadreApplication {
                        // Cross the native boundary before requesting stop so this test cannot
                        // accidentally exercise only the pre-run pending-stop handoff.
                        withTimeout(5.seconds) {
                            while (!native.isRunning()) yield()
                        }
                        val window = assertIs<WindowRequestOutcome.OpenedHere>(
                            windows.requestWindow(WindowSpec(title = "Kadre O3 public window proof"))
                                .appKitSuccessValue()
                                .await(),
                        ).window
                        proofStage.set("surface-resize")
                        try {
                        val events = Channel<WindowEvent>(Channel.UNLIMITED)
                        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                            window.events.collect(events::send)
                        }
                        val surfaceEvents = Channel<SurfaceEvent>(Channel.UNLIMITED)
                        val surfaceEventCount = AtomicInteger()
                        val surfaceEventStateWasVisible = AtomicBoolean(true)
                        val surfaceCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                            window.surface.events.collect { event ->
                                if (
                                    window.surface.state.value.revision.value < event.stateRevision.value
                                ) {
                                    surfaceEventStateWasVisible.set(false)
                                }
                                surfaceEventCount.incrementAndGet()
                                surfaceEvents.send(event)
                            }
                        }
                        proofStage.set("surface-visibility")
                        val beforeOrderOutRevision = window.surface.state.value.revision.value
                        assertEquals(
                            KadreResult.Success(Unit),
                            window.withDesktopHandle { handle ->
                                val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                                ObjCRuntime.autoreleasePool {
                                    NSWindow(
                                        MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()),
                                    ).orderOut(MemorySegment.NULL)
                                }
                            },
                        )
                        val hiddenEvent = withTimeout(5.seconds) {
                            surfaceEvents.receiveSurfaceEvent<SurfaceEvent.VisibilityChanged> {
                                it.state.visibility == org.graphiks.kadre.surface.SurfaceVisibility.Hidden &&
                                    it.state.revision.value > beforeOrderOutRevision
                            }
                        }
                        assertTrue(
                            window.surface.state.value.revision.value >= hiddenEvent.state.revision.value,
                        )
                        nativeSurfaceVisibilityObserved.set(true)

                        proofStage.set("surface-focus")
                        assertEquals(
                            KadreResult.Success(Unit),
                            window.withDesktopHandle { handle ->
                                val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                                ObjCRuntime.autoreleasePool {
                                    val application = NSApplication(NSApplication.sharedApplication())
                                    assertTrue(
                                        application.setActivationPolicy(
                                            NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular,
                                        ),
                                    )
                                    application.activateIgnoringOtherApps(true)
                                    NSWindow(
                                        MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()),
                                    ).makeKeyAndOrderFront(MemorySegment.NULL)
                                }
                            },
                        )
                        val focusEvent = withTimeout(5.seconds) {
                            surfaceEvents.receiveSurfaceEvent<SurfaceEvent.FocusChanged> {
                                it.state.focus == SurfaceFocus.Focused &&
                                    it.state.revision.value > hiddenEvent.state.revision.value
                            }
                        }
                        assertTrue(
                            window.surface.state.value.revision.value >= focusEvent.state.revision.value,
                        )
                        nativeSurfaceFocusObserved.set(true)

                        val injectedPhysicalKey = PhysicalKey.Code(usagePage = 0x07, usageId = 0x04)
                        val inputEvents = Channel<InputEvent>(Channel.UNLIMITED)
                        val inputEventStateWasVisible = AtomicBoolean(true)
                        val inputCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                            window.surface.input.events.collect { event ->
                                if (window.surface.input.state.value.revision.value < event.stateRevision.value) {
                                    inputEventStateWasVisible.set(false)
                                }
                                inputEvents.send(event)
                            }
                        }
                        proofStage.set("input-event-before-state")
                        assertEquals(
                            KadreResult.Success(Unit),
                            window.withDesktopHandle { handle ->
                                val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                                ObjCRuntime.autoreleasePool {
                                    NSView(
                                        MemorySegment.ofAddress(appKit.nsViewAddress.toLong()),
                                    ).keyDown(nativeKeyDownEvent())
                                }
                            },
                        )
                        val keyEvent = withTimeout(5.seconds) {
                            inputEvents.receiveInputEvent<InputEvent.Key> {
                                it.keyState == KeyState.Pressed && it.physicalKey == injectedPhysicalKey
                            }
                        }
                        assertEquals(KeyState.Pressed, keyEvent.keyState)
                        assertEquals(injectedPhysicalKey, keyEvent.physicalKey)
                        assertTrue(
                            window.surface.input.state.value.revision.value >= keyEvent.stateRevision.value,
                            "physical host input may advance the state after the key is delivered",
                        )
                        assertTrue(
                            injectedPhysicalKey in window.surface.input.state.value.keyboard.pressedKeys,
                            "the injected key must be represented in the source window state",
                        )
                        assertTrue(inputEventStateWasVisible.get())
                        nativeInputEventStateObserved.set(true)

                        proofStage.set("input-focus-reset")
                        assertEquals(
                            KadreResult.Success(Unit),
                            window.withDesktopHandle { handle ->
                                val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                                ObjCRuntime.autoreleasePool {
                                    NSWindow(
                                        MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()),
                                    ).resignKeyWindow()
                                }
                            },
                        )
                        val reset = withTimeout(5.seconds) {
                            var observed: InputEvent.StateReset? = null
                            while (observed == null) {
                                when (val event = inputEvents.receive()) {
                                    is InputEvent.StateReset -> observed = event
                                    is InputEvent.Key -> if (event.physicalKey == injectedPhysicalKey) {
                                        assertFalse(
                                            event.keyState == KeyState.Released,
                                            "focus reset must not be preceded by a synthetic key release: $event",
                                        )
                                    }
                                    else -> Unit // Unrelated physical host input may arrive concurrently.
                                }
                            }
                            checkNotNull(observed)
                        }
                        assertEquals(InputStateResetReason.FocusLost, reset.reason)
                        assertTrue(
                            window.surface.input.state.value.revision.value >= reset.stateRevision.value,
                            "physical host input may advance the state after the reset is delivered",
                        )
                        assertFalse(injectedPhysicalKey in window.surface.input.state.value.keyboard.pressedKeys)
                        assertNull(
                            withTimeoutOrNull(200.milliseconds) {
                                inputEvents.receiveInputEvent<InputEvent.Key> {
                                    it.keyState == KeyState.Released && it.physicalKey == injectedPhysicalKey
                                }
                            },
                        )
                        nativeInputFocusResetObserved.set(true)

                        proofStage.set("input-cross-surface")
                        val isolatedWindow = assertIs<WindowRequestOutcome.OpenedHere>(
                            windows.requestWindow(WindowSpec(title = "Kadre O3 input isolation proof"))
                                .appKitSuccessValue()
                                .await(),
                        ).window
                        val isolatedInputEvents = Channel<InputEvent>(Channel.UNLIMITED)
                        val isolatedInputCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                            isolatedWindow.surface.input.events.collect(isolatedInputEvents::send)
                        }
                        try {
                            assertEquals(
                                KadreResult.Success(Unit),
                                window.withDesktopHandle { handle ->
                                    val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                                    ObjCRuntime.autoreleasePool {
                                        NSView(
                                            MemorySegment.ofAddress(appKit.nsViewAddress.toLong()),
                                        ).keyDown(nativeKeyDownEvent())
                                    }
                                },
                            )
                            val sourceKeyEvent = withTimeout(5.seconds) {
                                inputEvents.receiveInputEvent<InputEvent.Key> {
                                    it.keyState == KeyState.Pressed && it.physicalKey == injectedPhysicalKey
                                }
                            }
                            assertEquals(injectedPhysicalKey, sourceKeyEvent.physicalKey)
                            assertTrue(
                                injectedPhysicalKey in window.surface.input.state.value.keyboard.pressedKeys,
                                "the injected key must remain attached to the source window",
                            )
                            assertFalse(
                                injectedPhysicalKey in isolatedWindow.surface.input.state.value.keyboard.pressedKeys,
                                "the injected key must not cross to the isolated window",
                            )
                            // Pointer motion belongs to the physical host desktop and may legitimately
                            // reach the newly opened window. The synthetic key dispatched to `window`
                            // must not cross to `isolatedWindow`.
                            assertNull(
                                withTimeoutOrNull(200.milliseconds) {
                                    isolatedInputEvents.receiveInputEvent<InputEvent.Key> {
                                        it.physicalKey == injectedPhysicalKey
                                    }
                                },
                            )
                            nativeInputIsolationObserved.set(true)
                        } finally {
                            isolatedInputCollector.cancel()
                            if (isolatedWindow.state.value.phase != WindowPhase.Closed) {
                                assertIs<WindowCloseOutcome.Accepted>(
                                    isolatedWindow.close().appKitSuccessValue(),
                                )
                                withTimeout(5.seconds) {
                                    isolatedWindow.state.first { it.phase == WindowPhase.Closed }
                                }
                            }
                        }

                        proofStage.set("surface-resize")
                        val resized = LogicalSize(360.0, 220.0)
                        val beforeResizeRevision = window.surface.state.value.revision.value
                        assertEquals(
                            KadreResult.Success(Unit),
                            window.withDesktopHandle { handle ->
                                val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                                ObjCRuntime.autoreleasePool {
                                    NSWindow(
                                        MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()),
                                    ).setContentSize(NSSize(resized.width, resized.height))
                                }
                            },
                        )
                        val resizeEvent = withTimeout(5.seconds) {
                            surfaceEvents.receiveSurfaceEvent<SurfaceEvent.MetricsChanged> {
                                it.state.logicalSize == resized &&
                                    it.state.revision.value > beforeResizeRevision
                            }
                        }
                        assertEquals(resized, resizeEvent.state.logicalSize)
                        assertTrue(
                            window.surface.state.value.revision.value >= resizeEvent.state.revision.value,
                        )
                        nativeSurfaceResizeObserved.set(true)

                        proofStage.set("surface-redraw")
                        repeat(8) {
                            assertEquals(KadreResult.Success(Unit), window.surface.requestRedraw())
                        }
                        val redrawEvent = withTimeout(5.seconds) {
                            surfaceEvents.receiveSurfaceEvent<SurfaceEvent.RedrawRequested>()
                        }
                        assertTrue(
                            window.surface.state.value.revision.value >= redrawEvent.stateRevision.value,
                        )
                        assertNull(
                            withTimeoutOrNull(200.milliseconds) {
                                surfaceEvents.receiveSurfaceEvent<SurfaceEvent.RedrawRequested>()
                            },
                        )
                        nativeSurfaceRedrawObserved.set(true)
                        proofStage.set("native-close-reject")
                        val performNativeUserClose: suspend () -> Unit = {
                            assertEquals(
                                KadreResult.Success(Unit),
                                window.withDesktopHandle { handle ->
                                    val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
                                    assertTrue(appKit.nsWindowAddress != 0uL)
                                    assertTrue(appKit.nsViewAddress != 0uL)
                                    publicHandleObserved.set(true)
                                    ObjCRuntime.autoreleasePool {
                                        NSWindow(
                                            MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()),
                                        ).performClose(MemorySegment.NULL)
                                    }
                                },
                            )
                        }

                        val closeEventTrace = ArrayDeque<WindowEvent>()
                        suspend fun awaitCloseRequested(stage: String): WindowEvent.CloseRequested =
                            withTimeout(5.seconds) {
                                while (true) {
                                    val event = events.receive()
                                    if (closeEventTrace.size == 8) closeEventTrace.removeFirst()
                                    closeEventTrace.addLast(event)
                                    when (event) {
                                        is WindowEvent.CloseRequested -> return@withTimeout event
                                        is WindowEvent.GeometryChanged -> Unit
                                        else -> error(
                                            "$stage expected CloseRequested after geometry observations; " +
                                                "received $event, trace=$closeEventTrace",
                                        )
                                    }
                                }
                                error("unreachable")
                            }

                        performNativeUserClose()
                        val rejected = awaitCloseRequested("native-close-reject")
                        assertEquals(
                            WindowCloseResponseOutcome.KeptOpen,
                            window.respondToCloseRequest(rejected.requestId, WindowCloseDecision.Reject)
                                .appKitSuccessValue(),
                        )
                        assertEquals(WindowPhase.Open, window.state.value.phase)
                        nativeRejectObserved.set(true)

                        proofStage.set("native-close-accept")
                        performNativeUserClose()
                        val accepted = awaitCloseRequested("native-close-accept")
                        val closing = assertIs<WindowCloseResponseOutcome.Closing>(
                            window.respondToCloseRequest(accepted.requestId, WindowCloseDecision.Accept)
                                .appKitSuccessValue(),
                        )
                        val terminal = withTimeout(5.seconds) {
                            assertIs<WindowEvent.Closing>(events.receive())
                        }
                        assertEquals(closing.operationId, terminal.operationId)
                        withTimeout(5.seconds) { window.state.first { it.phase == WindowPhase.Closed } }
                        val terminalSurface = withTimeout(5.seconds) {
                            window.surface.state.first {
                                it.attachment == SurfaceAttachmentState.Detached
                            }
                        }
                        assertEquals(terminalSurface, window.surface.state.value)
                        withTimeout(5.seconds) { surfaceCollector.join() }
                        val terminalEventCount = surfaceEventCount.get()
                        assertNull(
                            withTimeoutOrNull(200.milliseconds) {
                                window.surface.state.first {
                                    it.revision.value > terminalSurface.revision.value
                                }
                            },
                        )
                        assertEquals(terminalEventCount, surfaceEventCount.get())
                        assertTrue(surfaceEventStateWasVisible.get())
                        assertEquals(
                            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)),
                            window.surface.requestRedraw(),
                        )
                        withTimeout(5.seconds) { windows.state.first { it.windows.isEmpty() } }
                        terminalCloseObserved.set(true)
                        nativeSurfaceTerminalObserved.set(true)
                        collector.cancel()
                        inputCollector.cancel()
                        proofStage.set("session-stop")
                        stopRequestedOffMainThread.set(!native.isMainThread())
                        requestStop()
                        } catch (failure: Throwable) {
                            proofFailure.set(failure)
                            if (window.state.value.phase != WindowPhase.Closed) {
                                window.close()
                                withTimeout(5.seconds) {
                                    window.state.first { it.phase == WindowPhase.Closed }
                                }
                            }
                            throw failure
                        }
                    }
                },
                false,
                KadrePolicies.Default,
            ),
        )

        proofFailure.get()?.let { failure ->
            throw AssertionError("O3 proof stage: ${proofStage.get()}", failure)
        }
        assertEquals(
            KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested)),
            result,
        )
        assertTrue(stopRequestedOffMainThread.get())
        assertTrue(publicHandleObserved.get())
        assertTrue(nativeRejectObserved.get())
        assertTrue(terminalCloseObserved.get())
        assertTrue(nativeSurfaceResizeObserved.get())
        assertTrue(nativeSurfaceFocusObserved.get())
        assertTrue(nativeSurfaceVisibilityObserved.get())
        assertTrue(nativeSurfaceRedrawObserved.get())
        assertTrue(nativeSurfaceTerminalObserved.get())
        assertTrue(nativeInputEventStateObserved.get())
        assertTrue(nativeInputFocusResetObserved.get())
        assertTrue(nativeInputIsolationObserved.get())
    }

    @Test
    fun phase3SurfaceHarnessWritesCompleteNoninteractiveRecordOnMacOs() {
        if (!isMacOs()) return
        val record = Files.createTempFile("kadre-phase3-surface-harness", ".tsv")
        val output = Files.createTempFile("kadre-phase3-surface-harness", ".log")
        try {
            val process = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XstartOnFirstThread",
                "--enable-native-access=ALL-UNNAMED",
                "-cp",
                System.getProperty("java.class.path"),
                "org.graphiks.kadre.internal.appkit.manual.Phase3SurfaceHarnessKt",
                "--record=$record",
                "--build-id=automated-harness-proof",
            ).redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start()

            process.outputStream.bufferedWriter().use { commands ->
                commands.appendLine("redraw 8")
                commands.appendLine("unsupported")
                commands.appendLine("result M7 not-applicable premature result must be rejected")
                commands.appendLine("close")
                commands.appendLine("result M7 not-applicable automated proof does not satisfy manual M7")
                commands.appendLine("finish")
            }
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val processOutput = Files.readString(output)
            assertTrue(completed, processOutput)
            assertEquals(0, process.exitValue(), processOutput)

            val report = Files.readString(record)
            assertTrue(report.contains("RUN_METADATA\t"), report)
            listOf(
                "macOS=",
                "architecture=",
                "hardware=",
                "displays=",
                "initialScaleFactor=",
                "appearance=",
                "buildId=automated-harness-proof",
            ).forEach { field -> assertTrue(report.contains(field), "$field missing from:\n$report") }
            assertTrue(report.contains("SNAPSHOT\tinitial\t"), report)
            assertTrue(report.contains("SNAPSHOT\tupdate\t"), report)
            assertTrue(report.contains("COMMAND\tredraw\tcount=8"), report)
            assertTrue(report.contains("EVENT\tRedrawRequested\tstateRevisionVisible=true"), report)
            assertTrue(report.contains("COMMAND\tunsupported-surface-update\tSuccess(value=PartiallyApplied"), report)
            assertTrue(report.contains("Unsupported(operation=UpdateSurface)"), report)
            assertTrue(
                report.contains("TERMINAL_STABILITY\tnoLateRevision=true\tnoLateEvent=true"),
                report,
            )
            assertTrue(report.contains("TERMINAL\tSurfaceState(attachment=Detached"), report)
            assertTrue(report.contains("SESSION_OUTCOME\tStopped(reason=ApplicationRequested)"), report)
            assertTrue(
                report.contains("COMMAND\tresult-rejected\tM7 requires terminal observation before recording"),
                report,
            )
            assertFalse(report.contains("SCENARIO\tM7\tnot-applicable\tpremature result must be rejected"), report)
            assertTrue(
                report.contains("SCENARIO\tM7\tnot-applicable\tautomated proof does not satisfy manual M7"),
                report,
            )
            assertFalse(report.lineSequence().any { it.startsWith("SCENARIO\t") && "\tpass\t" in it }, report)
        } finally {
            Files.deleteIfExists(record)
            Files.deleteIfExists(output)
        }
    }

    @Test
    fun phase5WindowChromeHarnessWritesAnHonestNoninteractiveRecordOnMacOs() {
        if (!isMacOs()) return
        val record = Files.createTempFile("kadre-phase5-window-chrome-harness", ".tsv")
        val output = Files.createTempFile("kadre-phase5-window-chrome-harness", ".log")
        try {
            val process = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XstartOnFirstThread",
                "--enable-native-access=ALL-UNNAMED",
                "-cp",
                System.getProperty("java.class.path"),
                "org.graphiks.kadre.internal.appkit.manual.Phase5WindowChromeHarnessKt",
                "--record=$record",
                "--build-id=automated-chrome-harness-proof",
            ).redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start()

            process.outputStream.bufferedWriter().use { commands ->
                commands.appendLine("system-close-only")
                commands.appendLine("system-none")
                commands.appendLine("borderless")
                commands.appendLine("system-all")
                (1..4).forEach { scenario ->
                    commands.appendLine(
                        "result M$scenario not-applicable automated proof does not satisfy manual M$scenario",
                    )
                }
                commands.appendLine("close")
                commands.appendLine("finish")
            }
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val processOutput = Files.readString(output)
            assertTrue(completed, processOutput)
            assertEquals(0, process.exitValue(), processOutput)

            val report = Files.readString(record)
            assertTrue(report.contains("RUN_METADATA\t"), report)
            listOf(
                "macOS=",
                "architecture=",
                "hardware=",
                "displays=",
                "buildId=automated-chrome-harness-proof",
            ).forEach { field -> assertTrue(report.contains(field), "$field missing from:\n$report") }
            assertTrue(report.contains("SNAPSHOT\tinitial\tWindowState("), report)
            assertTrue(report.contains("CAPABILITIES\tWindowCapabilities("), report)
            listOf("system-close-only", "system-none", "borderless", "system-all").forEach { command ->
                assertTrue(report.contains("COMMAND\t$command\tSuccess(value="), report)
                assertTrue(report.contains("SNAPSHOT\tcommand-$command\tWindowState("), report)
            }
            assertTrue(report.contains("EVENT\tPropertiesChanged\tstateRevisionVisible=true"), report)
            assertTrue(
                report.contains("TERMINAL_STABILITY\tnoLateRevision=true\tnoLateEvent=true"),
                report,
            )
            assertTrue(report.contains("TERMINAL\tWindowState(phase=Closed"), report)
            assertTrue(report.contains("SESSION_OUTCOME\tStopped(reason=ApplicationRequested)"), report)
            (1..4).forEach { scenario ->
                assertTrue(
                    report.contains(
                        "SCENARIO\tM$scenario\tnot-applicable\t" +
                            "automated proof does not satisfy manual M$scenario",
                    ),
                    report,
                )
            }
            assertFalse(report.lineSequence().any { it.startsWith("SCENARIO\t") && "\tpass\t" in it }, report)
        } finally {
            Files.deleteIfExists(record)
            Files.deleteIfExists(output)
        }
    }

    @Test
    fun publishedKffiScrollPostingAcceptsDiscreteAndPreciseCoreGraphicsEventsOnMacOs() {
        if (!isMacOs()) return

        ObjCRuntime.autoreleasePool {
            val application = NSApplication(NSApplication.sharedApplication())
            val discrete = AppKitScrollWheelEvent(
                    CGScrollEventUnit.kCGScrollEventUnitLine,
                    deltaX = 3.0,
                    deltaY = -5.0,
                    phase = CGScrollPhase.kCGScrollPhaseChanged,
                    momentumPhase = CGMomentumScrollPhase.kCGMomentumScrollPhaseNone,
                    isContinuous = false,
                )
            val precise = AppKitScrollWheelEvent(
                    CGScrollEventUnit.kCGScrollEventUnitPixel,
                    deltaX = 12.0,
                    deltaY = -8.0,
                    phase = CGScrollPhase.kCGScrollPhaseChanged,
                    momentumPhase = CGMomentumScrollPhase.kCGMomentumScrollPhaseContinue,
                    isContinuous = true,
                )
            application.postScrollWheelEvent(discrete)
            application.postScrollWheelEvent(precise)
            val first = NSEvent(application.nextEventMatchingMask_untilDate_inMode_dequeue(
                NSEventMask.NSEventMaskScrollWheel, NSDate_distantFuture(), NSDefaultRunLoopMode, true,
            ))
            val second = NSEvent(application.nextEventMatchingMask_untilDate_inMode_dequeue(
                NSEventMask.NSEventMaskScrollWheel, NSDate_distantFuture(), NSDefaultRunLoopMode, true,
            ))
            val third = application.nextEventMatchingMask_untilDate_inMode_dequeue(
                NSEventMask.NSEventMaskScrollWheel, NSDate_date(), NSDefaultRunLoopMode, true,
            )
            assertEquals(org.graphiks.kffi.objc.NSEventType.NSEventTypeScrollWheel, first.type())
            assertEquals(org.graphiks.kffi.objc.NSEventType.NSEventTypeScrollWheel, second.type())
            assertEquals(3.0, first.deltaX())
            assertEquals(-5.0, first.deltaY())
            assertFalse(first.hasPreciseScrollingDeltas())
            assertEquals(12.0, second.deltaX())
            assertEquals(-8.0, second.deltaY())
            assertTrue(second.hasPreciseScrollingDeltas())
            assertEquals(0L, first.windowNumber())
            assertEquals(0L, second.windowNumber())
            assertEquals(NSEventPhase.NSEventPhaseChanged, first.phase())
            assertEquals(NSEventPhase.NSEventPhaseChanged, second.phase())
            assertEquals(NSEventPhase.NSEventPhaseNone, first.momentumPhase())
            assertEquals(NSEventPhase.NSEventPhaseChanged, second.momentumPhase())
            assertTrue(third.address() == 0L)
        }
    }

    @Test
    fun phase4InputHarnessWritesAnHonestNoninteractiveRecordOnMacOs() {
        if (!isMacOs()) return
        val record = Files.createTempFile("kadre-phase4-input-harness", ".tsv")
        val output = Files.createTempFile("kadre-phase4-input-harness", ".log")
        try {
            val process = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XstartOnFirstThread",
                "--enable-native-access=ALL-UNNAMED",
                "-cp",
                System.getProperty("java.class.path"),
                "org.graphiks.kadre.internal.appkit.manual.Phase4InputHarnessKt",
                "--record=$record",
                "--build-id=automated-input-harness-proof",
            ).redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start()

            process.outputStream.bufferedWriter().use { commands ->
                commands.appendLine("snapshot")
                commands.appendLine("result M7 pass premature terminal claim")
                commands.appendLine("result M1 not-applicable automated run cannot prove physical keyboard focus")
                commands.appendLine("finish")
            }
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val processOutput = Files.readString(output)
            assertTrue(completed, processOutput)
            assertEquals(0, process.exitValue(), processOutput)

            val report = Files.readString(record)
            assertTrue(report.contains("RUN_METADATA\t"), report)
            assertTrue(report.contains("INPUT_CAPABILITIES\tkeyboard=Available\tpointer=Available"), report)
            assertTrue(report.contains("\ttouch=Unsupported\tgestures=Unsupported\tdragAndDrop=Unsupported"), report)
            assertTrue(
                report.contains(
                    "\ttextInput=Unsupported(operation=TextInput)" +
                        "\trawInput=Unsupported(operation=RawInputAccess)",
                ),
                report,
            )
            assertTrue(report.contains("SNAPSHOT\tinitial\t"), report)
            assertTrue(report.contains("COMMAND\tsnapshot\t"), report)
            assertTrue(
                report.contains("SCENARIO\tM1\tnot-applicable\tautomated run cannot prove physical keyboard focus"),
                report,
            )
            assertTrue(report.contains("TERMINAL_STABILITY\tnoLateRevision=true\tnoLateEvent=true"), report)
            assertTrue(report.contains("COMMAND\tresult-rejected\tM7 requires terminal observation before recording"), report)
            assertFalse(report.contains("SCENARIO\tM7\tpass\tpremature terminal claim"), report)
            assertTrue(report.contains("SESSION_OUTCOME\tStopped(reason=ApplicationRequested)"), report)
            assertFalse(report.lineSequence().any { it.startsWith("SCENARIO\t") && "\tpass\t" in it }, report)
        } finally {
            Files.deleteIfExists(record)
            Files.deleteIfExists(output)
        }
    }

    @Test
    fun realKffiPendingStopIsConsumedOnMacOs() {
        if (!isMacOs()) return
        val native = KffiAppKitNativeApplication()

        // Request before run() on purpose: this proves the pending-stop handoff separately from
        // the active-loop wakeup scenario above.
        val stop = native.requestStop()
        native.run()

        assertEquals(AppKitStopResult.Accepted, stop.await())
    }

    @Test
    fun realKffiLifecycleSourceStopsDeliveringAfterCloseOnMacOs() {
        if (!isMacOs()) return
        val application = ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
        }
        val center = ObjCRuntime.autoreleasePool {
            NSNotificationCenter(NSNotificationCenter.defaultCenter())
        }
        val observed = AtomicReference<AppKitLifecycleSignal?>(null)
        val observation = KffiAppKitLifecycleSource().start { observed.set(it) }

        try {
            center.postAppKitHideNotification(application)
            assertEquals(AppKitLifecycleSignal.DidHide, observed.get())

            observation.close()
            observed.set(null)
            center.postAppKitHideNotification(application)
            assertNull(observed.get())
        } finally {
            observation.close()
        }
    }

    @Test
    fun realKffiNotificationRoutesThroughAnEmbeddedSessionOnMacOs() = kotlinx.coroutines.runBlocking {
        if (!isMacOs()) return@runBlocking
        val application = ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
        }
        val center = ObjCRuntime.autoreleasePool {
            NSNotificationCenter(NSNotificationCenter.defaultCenter())
        }
        val parentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val lifecycle = CompletableDeferred<KadreLifecycle>()
        val provider = AppKitBackendProvider.forTesting(
            NativeLifecycleApplication(),
            AppKitProcessBroker(),
        ) { true }

        try {
            val session = provider.attach(embeddedRequest(parentScope, lifecycle)).requireSession()
            val observed = withTimeout(2.seconds) { lifecycle.await() }

            center.postAppKitNotification("NSApplicationDidHideNotification", application)
            assertEquals(org.graphiks.kadre.application.VisibilityState.Background, observed.state.value.visibility)

            session.close()
            assertEquals(
                SessionOutcome.Stopped(SessionStopReason.HostRequested),
                session.awaitTermination(),
            )
        } finally {
            parentScope.cancel()
        }
    }
}

private class RecordingNativeApplication(
    private val mainThread: Boolean = true,
    private val runFailure: RuntimeException? = null,
) : AppKitNativeApplication {
    var mainThreadCheckCount: Int = 0
        private set
    var runCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean {
        mainThreadCheckCount += 1
        return mainThread
    }

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        runCount += 1
        runFailure?.let { throw it }
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() = Unit
}

private class EmbeddedNativeApplication : AppKitNativeApplication {
    private val observers = mutableListOf<(AppKitLifecycleSignal) -> Unit>()

    val observerCount: Int
        get() = observers.size
    var runCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = true

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        observe(listener)

    override fun run() {
        runCount += 1
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() = Unit

    fun emit(signal: AppKitLifecycleSignal) {
        observers.toList().forEach { it(signal) }
    }

    fun observe(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable {
        observers += listener
        return AutoCloseable { observers -= listener }
    }
}

private class NativeLifecycleApplication : AppKitNativeApplication {
    private val lifecycleSource = KffiAppKitLifecycleSource()

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = true

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        lifecycleSource.start(listener)

    override fun run(): Nothing = error("embedded test host must not run an AppKit loop")

    override fun requestStop(): AppKitStopRequest =
        error("embedded test host must not request AppKit stop")

    override fun emergencyStop(): Nothing = error("embedded test host must not stop AppKit")
}

private class StopDrivenNativeApplication : AppKitNativeApplication {
    val trace = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val stop = CountDownLatch(1)
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        trace += "run"
        stop.await()
    }

    override fun requestStop(): AppKitStopRequest {
        trace += "stop"
        stopCount += 1
        stop.countDown()
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() {
        stop.countDown()
    }
}

private class CancellationNativeApplication(
    private val applicationStarted: CountDownLatch,
    private val cancellation: kotlinx.coroutines.CancellationException,
) : AppKitNativeApplication {
    var stopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        check(applicationStarted.await(2, TimeUnit.SECONDS)) { "application did not start" }
        throw cancellation
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        return AppKitStopRequest { AppKitStopResult.Accepted }
    }

    override fun emergencyStop() = Unit
}

private class FailingStopNativeApplication : AppKitNativeApplication {
    private val stop = CountDownLatch(1)
    var stopCount: Int = 0
        private set
    var emergencyStopCount: Int = 0
        private set

    override fun isMainThread(): Boolean = true

    override fun isRunning(): Boolean = false

    override fun startLifecycleObservation(listener: (AppKitLifecycleSignal) -> Unit): AutoCloseable =
        AutoCloseable { }

    override fun run() {
        stop.await()
    }

    override fun requestStop(): AppKitStopRequest {
        stopCount += 1
        throw IllegalStateException("native stop")
    }

    override fun emergencyStop() {
        emergencyStopCount += 1
        stop.countDown()
    }
}

private fun isMacOs(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}

private fun embeddedRequest(
    parentScope: kotlinx.coroutines.CoroutineScope,
    captureLifecycle: CompletableDeferred<KadreLifecycle>,
): DesktopEmbeddedRequest =
    DesktopEmbeddedRequest(
        parentScope,
        KadreApplicationFactory {
            KadreApplication {
                captureLifecycle.complete(lifecycle)
                kotlinx.coroutines.awaitCancellation()
            }
        },
        DesktopIntegrationKind.AppKitMainLoop,
        KadrePolicies.Default,
    )

private fun publicWindowRequest(
    parentScope: kotlinx.coroutines.CoroutineScope,
    captureWindows: CompletableDeferred<WindowManager>,
): DesktopEmbeddedRequest = DesktopEmbeddedRequest(
    parentScope,
    KadreApplicationFactory {
        KadreApplication {
            captureWindows.complete(windows)
            kotlinx.coroutines.awaitCancellation()
        }
    },
    DesktopIntegrationKind.AppKitMainLoop,
    KadrePolicies.Default,
)

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private fun runPublicAppKitGeometrySession(
    policy: KadrePolicy = KadrePolicies.Default,
    exercise: suspend KadreScope.() -> Unit,
) {
    if (!isMacOs()) return
    val native = KffiAppKitNativeApplication()
    val provider = AppKitBackendProvider.forTesting(native, AppKitProcessBroker()) { true }
    val exerciseFailure = AtomicReference<Throwable?>(null)
    val result = provider.run(
        DesktopStandaloneRequest(
            KadreApplicationFactory {
                KadreApplication {
                    withTimeout(5.seconds) {
                        while (!native.isRunning()) yield()
                    }
                    try {
                        exercise()
                    } catch (failure: Throwable) {
                        exerciseFailure.set(failure)
                        throw failure
                    } finally {
                        windows.state.value.windows.forEach { window ->
                            if (window.state.value.phase != WindowPhase.Closed) {
                                window.close().appKitSuccessValue()
                                withTimeout(5.seconds) {
                                    window.state.first { it.phase == WindowPhase.Closed }
                                }
                            }
                        }
                        requestStop()
                    }
                }
            },
            stopWhenLastWindowClosed = false,
            policy = policy,
        ),
    )
    exerciseFailure.get()?.let { throw it }
    assertEquals(
        KadreResult.Success(SessionOutcome.Stopped(SessionStopReason.ApplicationRequested)),
        result,
    )
}

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private suspend fun KadreScope.openPublicGeometryWindow(title: String): Window =
    openPublicGeometryWindow(WindowSpec(title = title))

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private suspend fun KadreScope.openPublicGeometryWindow(spec: WindowSpec): Window =
    assertIs<WindowRequestOutcome.OpenedHere>(
        windows.requestWindow(spec).appKitSuccessValue().await(),
    ).window

private data class NativePublicWindowChrome(
    val decorations: WindowDecorations,
    val resizable: Boolean,
    val closeHidden: Boolean?,
    val miniaturizeHidden: Boolean?,
    val zoomHidden: Boolean?,
)

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private suspend fun readNativeWindowChrome(window: Window): NativePublicWindowChrome =
    assertIs<KadreResult.Success<NativePublicWindowChrome>>(
        window.withDesktopHandle { handle ->
            val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
            ObjCRuntime.autoreleasePool {
                val native = NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()))
                val style = native.styleMask()
                val decorations = if (style.contains(NSWindowStyleMask.NSWindowStyleMaskTitled)) {
                    WindowDecorations.System
                } else {
                    WindowDecorations.Borderless
                }
                if (decorations == WindowDecorations.Borderless) {
                    NativePublicWindowChrome(
                        decorations = decorations,
                        resizable = style.contains(NSWindowStyleMask.NSWindowStyleMaskResizable),
                        closeHidden = null,
                        miniaturizeHidden = null,
                        zoomHidden = null,
                    )
                } else {
                    NativePublicWindowChrome(
                        decorations = decorations,
                        resizable = style.contains(NSWindowStyleMask.NSWindowStyleMaskResizable),
                        closeHidden = NSButton(
                            native.standardWindowButton(NSWindowButton.NSWindowCloseButton),
                        ).isHidden(),
                        miniaturizeHidden = NSButton(
                            native.standardWindowButton(NSWindowButton.NSWindowMiniaturizeButton),
                        ).isHidden(),
                        zoomHidden = NSButton(
                            native.standardWindowButton(NSWindowButton.NSWindowZoomButton),
                        ).isHidden(),
                    )
                }
            }
        },
    ).value

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private data class NativeWindowConstraints(
    val minimumWidth: Double,
    val minimumHeight: Double,
    val maximumWidth: Double,
    val maximumHeight: Double,
)

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private suspend fun readNativeConstraints(window: Window): NativeWindowConstraints =
    assertIs<KadreResult.Success<NativeWindowConstraints>>(
        window.withDesktopHandle { handle ->
            val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
            ObjCRuntime.autoreleasePool {
                val native = NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()))
                val minimum = native.contentMinSize()
                val maximum = native.contentMaxSize()
                NativeWindowConstraints(
                    minimumWidth = minimum.width,
                    minimumHeight = minimum.height,
                    maximumWidth = maximum.width,
                    maximumHeight = maximum.height,
                )
            }
        },
    ).value

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private suspend fun setNativeContentSize(window: Window, size: LogicalSize) {
    assertEquals(
        KadreResult.Success(Unit),
        window.withDesktopHandle { handle ->
            val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
            ObjCRuntime.autoreleasePool {
                NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()))
                    .setContentSize(NSSize(size.width, size.height))
            }
        },
    )
}

@OptIn(
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
)
private suspend fun readNativeContentSize(window: Window): LogicalSize =
    assertIs<KadreResult.Success<LogicalSize>>(
        window.withDesktopHandle { handle ->
            val appKit = assertIs<DesktopNativeWindowHandle.AppKit>(handle)
            ObjCRuntime.autoreleasePool {
                NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong()))
                    .contentRectForFrameRect(
                        NSWindow(MemorySegment.ofAddress(appKit.nsWindowAddress.toLong())).frame(),
                    )
                    .size
                    .let { size -> LogicalSize(size.width, size.height) }
            }
        },
    ).value

private fun KadreResult<KadreSession>.requireSession(): KadreSession =
    (this as? KadreResult.Success)?.value ?: error("Expected a Kadre session, got $this")

private suspend fun EmbeddedNativeApplication.awaitObserverCount(expected: Int) {
    withTimeout(2.seconds) {
        while (observerCount != expected) yield()
    }
}

private fun NSNotificationCenter.postAppKitHideNotification(application: NSApplication) {
    postAppKitNotification("NSApplicationDidHideNotification", application)
}

private fun NSNotificationCenter.postAppKitNotification(name: String, application: NSApplication) {
    ObjCRuntime.autoreleasePool {
        postNotificationName_object(
            ObjCRuntime.newNSString(Arena.global(), name),
            application.ptr,
        )
    }
}

private fun nativeKeyDownEvent(): MemorySegment =
    NSEvent.keyEventWithType_location_modifierFlags_timestamp_windowNumber_context_characters_charactersIgnoringModifiers_isARepeat_keyCode(
        type = NSEventType.NSEventTypeKeyDown,
        location = NSPoint(12.5, 4.0),
        flags = NSEventModifierFlags.NSEventModifierFlagShift,
        time = 1.0,
        wNum = 0L,
        unusedPassNil = MemorySegment.NULL,
        keys = "A",
        ukeys = "a",
        flag = false,
        code = 0x00,
    )

private suspend inline fun <reified T : InputEvent> Channel<InputEvent>.receiveInputEvent(
    predicate: (T) -> Boolean = { true },
): T {
    while (true) {
        val event = receive()
        if (event is T && predicate(event)) return event
    }
}

private suspend inline fun <reified T : SurfaceEvent> Channel<SurfaceEvent>.receiveSurfaceEvent(
    predicate: (T) -> Boolean = { true },
): T {
    while (true) {
        val event = receive()
        if (event is T && predicate(event)) return event
    }
}
