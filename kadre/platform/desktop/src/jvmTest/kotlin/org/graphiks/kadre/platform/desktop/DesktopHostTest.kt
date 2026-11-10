package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import org.graphiks.kadre.internal.runtime.desktop.DesktopRunRequest
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseCommand
import org.graphiks.kadre.internal.runtime.OpenedWindowCloseOutcome
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationCommand
import org.graphiks.kadre.internal.runtime.PendingWindowCancellationOutcome
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.RuntimeDesktopWindowHandleAccess
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeWindowManager
import org.graphiks.kadre.internal.runtime.WindowCommandPort
import org.graphiks.kadre.internal.runtime.WindowOpenCommand
import org.graphiks.kadre.internal.runtime.WindowPeerOwner
import org.graphiks.kadre.internal.runtime.WindowUpdateCommand
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.ExecutorService
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopHostTest {
    private val factory = KadreApplicationFactory { KadreApplication { } }

    @Test
    fun linuxAutoPriorityIsStableRegardlessOfDiscoveryOrder() {
        val x11 = RecordingProvider(DesktopBackendKind.X11)
        val wayland = RecordingProvider(DesktopBackendKind.Wayland)
        val facade = DesktopHostFacade(DesktopOperatingSystem.Linux) { listOf(x11, wayland) }

        val result = facade.run(factory, DesktopBackend.Auto, KadreRunOptions())

        assertIs<KadreResult.Success<SessionOutcome>>(result)
        assertEquals(1, wayland.standaloneRequests.size)
        assertEquals(0, x11.standaloneRequests.size)
    }

    @Test
    fun autoSkipsAnUnavailableProviderBeforeSelection() {
        val wayland = RecordingProvider(DesktopBackendKind.Wayland, available = false)
        val x11 = RecordingProvider(DesktopBackendKind.X11)
        val facade = DesktopHostFacade(DesktopOperatingSystem.Linux) { listOf(wayland, x11) }

        assertIs<KadreResult.Success<SessionOutcome>>(
            facade.run(factory, DesktopBackend.Auto, KadreRunOptions()),
        )
        assertEquals(0, wayland.standaloneRequests.size)
        assertEquals(1, x11.standaloneRequests.size)
    }

    @Test
    fun windowsAutoSelectsWin32() {
        val provider = RecordingProvider(DesktopBackendKind.Win32)
        val facade = DesktopHostFacade(DesktopOperatingSystem.Windows) { listOf(provider) }

        assertIs<KadreResult.Success<SessionOutcome>>(
            facade.run(factory, DesktopBackend.Auto, KadreRunOptions()),
        )
        assertEquals(1, provider.standaloneRequests.size)
    }

    @Test
    fun operatingSystemDetectionIsClosedAndLocaleIndependent() {
        assertEquals(DesktopOperatingSystem.MacOS, detectDesktopOperatingSystem("Mac OS X"))
        assertEquals(DesktopOperatingSystem.MacOS, detectDesktopOperatingSystem("Darwin"))
        assertEquals(DesktopOperatingSystem.Windows, detectDesktopOperatingSystem("Windows 11"))
        assertEquals(DesktopOperatingSystem.Linux, detectDesktopOperatingSystem("Linux"))
        assertEquals(DesktopOperatingSystem.Unsupported, detectDesktopOperatingSystem("FreeBSD"))
    }

    @Test
    fun unsupportedOperatingSystemIsRejectedBeforeDiscovery() {
        var catalogLoaded = false
        val facade = DesktopHostFacade(DesktopOperatingSystem.Unsupported) {
            catalogLoaded = true
            emptyList()
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            facade.run(factory, DesktopBackend.Auto, KadreRunOptions()),
        )
        assertFalse(catalogLoaded)
    }

    @Test
    fun incompatibleOsBackendIsRejectedBeforeProviderDiscovery() {
        var catalogLoaded = false
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) {
            catalogLoaded = true
            listOf(RecordingProvider(DesktopBackendKind.Win32))
        }

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("options")),
            facade.run(factory, DesktopBackend.Win32, KadreRunOptions()),
        )
        assertFalse(catalogLoaded)
    }

    @Test
    fun duplicateSelectedProviderIsAStablePlatformFailure() {
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) {
            listOf(
                RecordingProvider(DesktopBackendKind.AppKit),
                RecordingProvider(DesktopBackendKind.AppKit),
            )
        }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "desktop-provider", "duplicate-provider"),
            ),
            facade.run(factory, DesktopBackend.Auto, KadreRunOptions()),
        )
    }

    @Test
    fun providerExceptionDoesNotFallbackToAnotherBackend() {
        val wayland = RecordingProvider(DesktopBackendKind.Wayland, runError = IllegalStateException("provider"))
        val x11 = RecordingProvider(DesktopBackendKind.X11)
        val facade = DesktopHostFacade(DesktopOperatingSystem.Linux) { listOf(x11, wayland) }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Wayland, "desktop-provider", "run-exception"),
            ),
            facade.run(factory, DesktopBackend.Auto, KadreRunOptions()),
        )
        assertEquals(1, wayland.standaloneRequests.size)
        assertEquals(0, x11.standaloneRequests.size)
    }

    @Test
    fun providerCancellationIsNeverWrapped() {
        val cancellation = CancellationException("cancelled")
        val provider = RecordingProvider(DesktopBackendKind.AppKit, runError = cancellation)
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        val observed = assertFailsWith<CancellationException> {
            facade.run(factory, DesktopBackend.Auto, KadreRunOptions())
        }
        assertSame(cancellation, observed)
    }

    @Test
    fun providerFailureOutsideTheRunDomainIsNormalized() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit).apply {
            runResult = KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
        }
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "desktop-provider", "invalid-run-failure"),
            ),
            facade.run(factory, DesktopBackend.Auto, KadreRunOptions()),
        )
    }

    @Test
    fun standaloneReturnsTheOutcomeAndAnExplicitPreSessionFailure() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit)
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        val outcome = facade.run(factory, DesktopBackend.AppKit, KadreRunOptions(policy = KadrePolicies.Recording))
        assertEquals(KadreResult.Success(SessionOutcome.Completed), outcome)
        val request = provider.standaloneRequests.single()
        assertSame(factory, request.applicationFactory)
        assertSame(KadrePolicies.Recording, request.policy)
        assertNull(request.firstWindow)

        provider.runResult = KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))
        val failure = facade.run(factory, DesktopBackend.AppKit, KadreRunOptions())
        assertEquals(KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)), failure)
    }

    @Test
    fun runTransmitsTheRunOptionsAndAnOwnedDispatcherThroughRun() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit)
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }
        val firstWindow = WindowSpec(title = "première")

        val outcome = facade.run(
            factory,
            DesktopBackend.AppKit,
            KadreRunOptions(policy = KadrePolicies.Recording, firstWindow = firstWindow),
        )

        assertEquals(KadreResult.Success(SessionOutcome.Completed), outcome)
        val request = provider.standaloneRequests.single()
        assertSame(factory, request.applicationFactory)
        assertSame(KadrePolicies.Recording, request.policy)
        assertSame(firstWindow, request.firstWindow)
        val dispatcher = assertIs<ExecutorCoroutineDispatcher>(request.mainDispatcher)
        assertTrue(
            (dispatcher.executor as ExecutorService).isShutdown,
            "the facade must close the dispatcher it owns once the run has returned",
        )
    }

    @Test
    fun runKadreApplicationReturnsExplicitFailureWhenNoBackendIsAvailable() {
        var factoryInvoked = false
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { emptyList() }

        val result = facade.run(
            KadreApplicationFactory {
                factoryInvoked = true
                KadreApplication { }
            },
            DesktopBackend.AppKit,
            KadreRunOptions(),
        )

        assertEquals(KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.HostAttach)), result)
        assertFalse(factoryInvoked)
    }

    @Test
    fun runClosesTheOwnedDispatcherWhenTheProviderFailsExplicitly() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit)
            .apply { runResult = KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)) }
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        val result = facade.run(factory, DesktopBackend.AppKit, KadreRunOptions())

        assertEquals(KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)), result)
        val dispatcher = assertIs<ExecutorCoroutineDispatcher>(provider.standaloneRequests.single().mainDispatcher)
        assertTrue(
            (dispatcher.executor as ExecutorService).isShutdown,
            "a provider failure must not leak the facade-owned main dispatcher",
        )
    }

    @Test
    fun runClosesTheOwnedDispatcherWhenTheProviderThrows() {
        val provider = RecordingProvider(DesktopBackendKind.AppKit)
            .apply { runError = IllegalStateException("provider") }
        val facade = DesktopHostFacade(DesktopOperatingSystem.MacOS) { listOf(provider) }

        val result = facade.run(factory, DesktopBackend.AppKit, KadreRunOptions())

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "desktop-provider", "run-exception"),
            ),
            result,
        )
        val dispatcher = assertIs<ExecutorCoroutineDispatcher>(provider.standaloneRequests.single().mainDispatcher)
        assertTrue(
            (dispatcher.executor as ExecutorService).isShutdown,
            "a provider exception must not leak the facade-owned main dispatcher",
        )
    }

    @Test
    fun providerCatalogIsLoadedLazilyAndCached() {
        var loads = 0
        val provider = RecordingProvider(DesktopBackendKind.AppKit)
        val catalog = LazyDesktopProviderCatalog {
            loads += 1
            listOf(provider)
        }

        assertEquals(0, loads)
        assertEquals(listOf(provider), catalog.providers())
        assertEquals(listOf(provider), catalog.providers())
        assertEquals(1, loads)
    }

    @OptIn(org.graphiks.kadre.diagnostics.DelicateKadreApi::class, org.graphiks.kadre.diagnostics.KadrePlatformApi::class)
    @Test
    fun desktopHandleMapsTheScopedAppKitLeaseAndRejectsAccessAfterClose() = runBlocking {
        val owner = RecordingDesktopHandleOwner()
        val port = ImmediateDesktopHandlePort(owner)
        val manager = RuntimeWindowManager(
            resources = KadrePolicies.Default.resources,
            commandPort = port,
            platform = KadrePlatform.AppKit,
            failureReporter = RuntimeFailureReporter { throw AssertionError(it) },
            publicWindowCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(org.graphiks.kadre.window.WindowProperty.Title),
        )
        val window = assertIs<WindowRequestOutcome.OpenedHere>(
            manager.requestWindow(WindowSpec(title = "handle")).successValue().await(),
        ).window
        var callbackThread: Thread? = null

        val mapped = window.withDesktopHandle { value ->
            callbackThread = Thread.currentThread()
            assertEquals(
                DesktopNativeWindowHandle.AppKit(0xA11uL, 0xB22uL),
                value,
            )
            "mapped"
        }.successValue()

        assertEquals("mapped", mapped)
        assertSame(Thread.currentThread(), callbackThread)
        assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, window.close().successValue())
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window)),
            window.withDesktopHandle { error("closed windows must not invoke the callback") },
        )
    }

    @OptIn(org.graphiks.kadre.diagnostics.DelicateKadreApi::class, org.graphiks.kadre.diagnostics.KadrePlatformApi::class)
    @Test
    fun desktopHandleMapsTheScopedWaylandLease() = runBlocking {
        val manager = RuntimeWindowManager(
            resources = KadrePolicies.Default.resources,
            commandPort = ImmediateDesktopHandlePort(WaylandDesktopHandleOwner()),
            platform = KadrePlatform.Wayland,
            failureReporter = RuntimeFailureReporter { throw AssertionError(it) },
            publicWindowCapabilities = true,
            enabledWindowUpdateCapabilities = setOf(org.graphiks.kadre.window.WindowProperty.Title),
        )
        val window = assertIs<WindowRequestOutcome.OpenedHere>(
            manager.requestWindow(WindowSpec(title = "handle-wayland")).successValue().await(),
        ).window

        assertEquals(
            DesktopNativeWindowHandle.Wayland(0xD15AuL, 0x5FAuL),
            window.withDesktopHandle { it }.successValue(),
        )
    }
}

private class WaylandDesktopHandleOwner : WindowPeerOwner, RuntimeDesktopWindowHandleAccess {
    override suspend fun <R> withDesktopHandle(
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R> = KadreResult.Success(
        block(RuntimeDesktopNativeWindowHandle.Wayland(0xD15AuL, 0x5FAuL)),
    )

    override fun close() {}
}

private class ImmediateDesktopHandlePort(
    private val owner: WindowPeerOwner,
) : WindowCommandPort {
    private lateinit var open: WindowOpenCommand

    override fun requestOpen(command: WindowOpenCommand) {
        open = command
        command.commit(owner)
    }

    override fun requestUpdate(command: WindowUpdateCommand) {
        command.rejected(UnsupportedOperationException("desktop handle test port does not support window updates"))
    }

    override fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome = PendingWindowCancellationOutcome.CancelledBeforeCommit

    override fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome {
        owner.close()
        open.nativeClosed()
        return OpenedWindowCloseOutcome.Accepted
    }
}

private class RecordingDesktopHandleOwner : WindowPeerOwner, RuntimeDesktopWindowHandleAccess {
    private var closed = false

    override suspend fun <R> withDesktopHandle(
        block: (RuntimeDesktopNativeWindowHandle) -> R,
    ): KadreResult<R> = if (closed) {
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Window))
    } else {
        KadreResult.Success(block(RuntimeDesktopNativeWindowHandle.AppKit(0xA11uL, 0xB22uL)))
    }

    override fun close() {
        closed = true
    }
}

private fun <T> KadreResult<T>.successValue(): T = when (this) {
    is KadreResult.Success -> value
    is KadreResult.Failure -> error("expected success, got $reason")
}

private class RecordingProvider(
    override val backend: DesktopBackendKind,
    private val available: Boolean = true,
    runError: RuntimeException? = null,
) : DesktopBackendProvider {
    val standaloneRequests = mutableListOf<DesktopRunRequest>()
    var runResult: KadreResult<SessionOutcome> = KadreResult.Success(SessionOutcome.Completed)
    var runError: RuntimeException? = runError

    override fun isAvailable(): Boolean = available

    override fun run(request: DesktopRunRequest): KadreResult<SessionOutcome> {
        standaloneRequests += request
        runError?.let { throw it }
        return runResult
    }
}
