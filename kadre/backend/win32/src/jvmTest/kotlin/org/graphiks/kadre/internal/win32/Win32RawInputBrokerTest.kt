package org.graphiks.kadre.internal.win32

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.internal.runtime.RawInputPortInput
import org.graphiks.kadre.internal.runtime.RawInputPortLease
import org.graphiks.kadre.internal.runtime.RawInputPortLeaseEvent
import org.graphiks.kadre.input.RawInputUnit
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateKadreApi::class)
class Win32RawInputBrokerTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private class FakeNative(var foreign: Boolean = false) : Win32RawInputNative {
        val registerCalls = AtomicInteger()
        var sink: ((deltaX: Int, deltaY: Int, device: Long) -> Unit)? = null
            private set

        override fun hasForeignRegistration(): Boolean = foreign
        override fun register(
            targetHwnd: Long,
            onMotion: (deltaX: Int, deltaY: Int, device: Long) -> Unit,
            onFailure: (Throwable) -> Unit,
        ): AutoCloseable {
            registerCalls.incrementAndGet()
            sink = onMotion
            return AutoCloseable { }
        }
    }

    @Test fun foreignRegistrationKeepsRawInputUnavailableWithoutStealing() = runBlocking {
        val native = FakeNative(foreign = true)
        val broker = Win32RawInputBroker(native)
        cleanups.add(broker)
        val port = broker.openPort()
        broker.activateOn(42L)
        val failure = assertIs<KadreResult.Failure>(port.requestAccess())
        val platform = assertIs<KadreFailure.PlatformFailure>(failure.reason)
        assertEquals("foreign-registration", platform.code)
        assertEquals(0, native.registerCalls.get(), "a foreign registration is never stolen")
        assertIs<Capability.Unsupported>(port.rawInputCapability)
        Unit
    }

    @Test fun admittedAccessFansNativeMotionOutToItsLease() = runBlocking {
        val native = FakeNative()
        val broker = Win32RawInputBroker(native)
        cleanups.add(broker)
        val portA = broker.openPort()
        val portB = broker.openPort()
        broker.activateOn(42L)
        val leaseA = assertIs<KadreResult.Success<RawInputPortLease>>(portA.requestAccess()).value
        val eventsA = Channel<RawInputPortLeaseEvent>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            leaseA.events.collect { eventsA.send(it) }
        }
        try {
            broker.onNativeMotion(4, -9, 12L)
            val event = withTimeout(3.seconds) { assertIs<RawInputPortLeaseEvent.Input>(eventsA.receive()) }
            assertEquals(RawInputPortInput(4.0, -9.0, RawInputUnit.DeviceCount, null), event.input)
        } finally {
            collector.cancel()
        }

        // A second access admits independently and the single registration is never repeated.
        val leaseB = assertIs<KadreResult.Success<RawInputPortLease>>(portB.requestAccess()).value
        assertEquals(1, native.registerCalls.get(), "the process registration is installed exactly once")
        leaseA.close()
        leaseB.close()
        assertEquals(1, native.registerCalls.get(), "no native mutation follows close (process-lifetime registration)")
    }

    @Test fun closingThePortClosesItsLeasesAndLaterAccessFailsClosed() = runBlocking {
        val native = FakeNative()
        val broker = Win32RawInputBroker(native)
        cleanups.add(broker)
        val port = broker.openPort()
        broker.activateOn(42L)
        val lease = assertIs<KadreResult.Success<RawInputPortLease>>(port.requestAccess()).value
        port.close()
        val after = assertIs<KadreResult.Failure>(port.requestAccess())
        assertIs<KadreFailure.Closed>(after.reason)
        // The admitted lease terminates with its port close.
        val terminated = kotlinx.coroutines.CompletableDeferred<Unit>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            kotlin.runCatching { lease.events.collect { } }
            terminated.complete(Unit)
        }
        withTimeout(3.seconds) { terminated.await() }
        watcher.cancel()
    }

    @Test fun providerWithoutRawInputPortKeepsPublicRequestUnsupported() = runBlocking {
        val port = DeterministicWin32HostPort()
        cleanups.add(port)
        val driver = Win32WindowRuntimeDriverFactory.create(
            port = port,
            resources = KadrePolicies.Default.resources,
        )
        cleanups.add(driver)
        val window = runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = "raw-input-absent"))
            val outcome = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            assertIs<WindowRequestOutcome.OpenedHere>(outcome.value.await()).window
        }
        val access = window.surface.input.requestRawInput()
        val failure = assertIs<KadreResult.Failure>(access)
        val unsupported = assertIs<KadreFailure.Unsupported>(failure.reason)
        assertEquals(KadreOperation.RawInputAccess, unsupported.operation)
    }
}
