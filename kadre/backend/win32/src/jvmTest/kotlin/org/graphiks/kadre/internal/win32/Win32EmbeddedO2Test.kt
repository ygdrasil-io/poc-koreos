package org.graphiks.kadre.internal.win32

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.internal.runtime.desktop.DesktopEmbeddedRequest
import org.graphiks.kadre.internal.runtime.desktop.DesktopIntegrationKind
import org.graphiks.kadre.policy.KadrePolicies

class Win32EmbeddedO2Test {
    private val probeRestores = ArrayList<() -> Unit>()

    @AfterTest
    fun teardown() {
        probeRestores.reversed().forEach { it() }
        probeRestores.clear()
    }

    @kotlin.test.BeforeTest
    fun resetHostOwnership() {
        Win32HostOwnership.resetForTesting()
    }

    private fun probeEdt(value: Boolean) {
        val previous = Win32BackendProvider.awtDispatchThreadProbe
        Win32BackendProvider.awtDispatchThreadProbe = { value }
        probeRestores.add { Win32BackendProvider.awtDispatchThreadProbe = previous }
    }

    private fun embeddedRequest(): DesktopEmbeddedRequest = DesktopEmbeddedRequest(
        parentScope = CoroutineScope(Job() + EmptyCoroutineContext),
        applicationFactory = KadreApplicationFactory { KadreApplication { } },
        integration = DesktopIntegrationKind.AwtEventDispatchThread,
        policy = KadrePolicies.Default,
    )

    @Test
    fun wrongIntegrationIsRefusedWithHostAttach() {
        probeEdt(true)
        val provider = Win32BackendProvider.forTesting(availability = { true }, portFactory = { DeterministicWin32HostPort() })
        val request = DesktopEmbeddedRequest(
            parentScope = CoroutineScope(Job() + EmptyCoroutineContext),
            applicationFactory = KadreApplicationFactory { KadreApplication { } },
            integration = DesktopIntegrationKind.AppKitMainLoop,
            policy = KadrePolicies.Default,
        )
        val failure = assertIs<KadreResult.Failure>(provider.attach(request))
        assertEquals(KadreFailure.Unsupported(KadreOperation.HostAttach), failure.reason)
    }

    @Test
    fun attachOutsideTheEdtIsRejectedNormatively() {
        probeEdt(false)
        val provider = Win32BackendProvider.forTesting(availability = { true }, portFactory = { DeterministicWin32HostPort() })
        val failure = assertIs<KadreResult.Failure>(provider.attach(embeddedRequest()))
        assertEquals(KadreFailure.InvalidRequest("options"), failure.reason)
    }

    @Test
    fun ownershipRegistryKeepsStandaloneAndEmbeddedExclusive() {
        assertTrue(Win32HostOwnership.tryAcquireStandalone())
        assertFalse(Win32HostOwnership.tryRegisterEmbedded(), "embedded attach is refused while standalone owns the host")
        Win32HostOwnership.releaseStandalone()
        assertTrue(Win32HostOwnership.tryRegisterEmbedded())
        assertFalse(Win32HostOwnership.tryAcquireStandalone(), "standalone is refused while an embedded session lives")
        Win32HostOwnership.releaseEmbedded()
        assertTrue(Win32HostOwnership.tryAcquireStandalone())
        Win32HostOwnership.releaseStandalone()
    }

    @Test
    fun embeddedSessionsCoexistOnTheirOwnPortsAndDetachWithoutTouchingEachOther() {
        probeEdt(true)
        val provider = Win32BackendProvider.forTesting(availability = { true }, portFactory = { DeterministicWin32HostPort() })
        val scope1 = CoroutineScope(Job() + EmptyCoroutineContext)
        val scope2 = CoroutineScope(Job() + EmptyCoroutineContext)
        val before = Win32HostOwnership.embeddedSessions()
        try {
            val first = assertIs<KadreResult.Success<*>>(
                provider.attach(requestIn(scope1)),
            ).value
            val second = assertIs<KadreResult.Success<*>>(
                provider.attach(requestIn(scope2)),
            ).value
            assertEquals(before + 2, Win32HostOwnership.embeddedSessions(), "two embedded sessions coexist")
            val session1 = assertIs<org.graphiks.kadre.application.KadreSession>(first)
            val session2 = assertIs<org.graphiks.kadre.application.KadreSession>(second)
            session1.close()
            runBlocking { session1.awaitTermination() }
            println("COEX-DIAG s2=${session2.state.value} count=${Win32HostOwnership.embeddedSessions()}")
            awaitEmbeddedCount(before + 1)
            assertEquals(before + 1, Win32HostOwnership.embeddedSessions(), "closing one session releases exactly its registration")
            session2.close()
            runBlocking { session2.awaitTermination() }
            awaitEmbeddedCount(before)
            assertEquals(before, Win32HostOwnership.embeddedSessions())
        } finally {
            scope1.cancel()
            scope2.cancel()
        }
    }

    @Test
    fun closedEmbeddedPumpRejectsTicksAndBoundedTickSurvivesIdle() {
        val port = DeterministicWin32HostPort()
        val session = Win32HostSession.start(port)
        val pump = session.pump
        val posted = CountDownLatch(1)
        val embedded = Win32EmbeddedPump(pump = pump, postTick = { posted.countDown() }, idleMillis = 1)
        embedded.start()
        assertTrue(posted.await(5, TimeUnit.SECONDS), "the pump schedules its first EDT tick")
        embedded.close()
        assertFalse(embedded.tick(), "a closed pump rejects ticks")
        session.teardown()
    }

    private fun awaitEmbeddedCount(expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (Win32HostOwnership.embeddedSessions() > expected) {
            if (System.currentTimeMillis() > deadline) {
                error("embedded release timed out (count=${Win32HostOwnership.embeddedSessions()} expected=$expected)")
            }
            Thread.sleep(25)
        }
    }

    private fun requestIn(scope: CoroutineScope): DesktopEmbeddedRequest = DesktopEmbeddedRequest(
        parentScope = scope,
        applicationFactory = KadreApplicationFactory { KadreApplication {
            val requested = windows.requestWindow(
                WindowSpec(title = "kadre embedded o2", contentSize = org.graphiks.kadre.surface.LogicalSize(240.0, 160.0)),
            )
            check(requested is KadreResult.Success<*>) { "window request failed: $requested" }
            (requested as KadreResult.Success<*>).value.let { request ->
                if (request is org.graphiks.kadre.window.WindowRequest) runBlocking { request.await() }
            }
        } },
        integration = DesktopIntegrationKind.AwtEventDispatchThread,
        policy = KadrePolicies.Default,
    )
}
