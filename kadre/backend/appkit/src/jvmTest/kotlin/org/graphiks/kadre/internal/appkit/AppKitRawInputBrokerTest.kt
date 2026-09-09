package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.input.RawInputUnit
import org.graphiks.kadre.internal.runtime.RawInputPortLeaseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AppKitRawInputBrokerTest {
    @Test
    fun deniedPermissionPublishesRawInputAvailabilityAndReturnsTheTypedFailure() = runBlocking {
        val native = RecordingRawInputNative(preflightGranted = false, requestResult = false)
        val port = broker(native).openPort()

        assertEquals(
            Capability.Supported(Unit, FeatureAvailability.RequiresPermission(KadrePermission.InputMonitoring)),
            port.rawInputCapability,
        )

        val result = assertIs<KadreResult.Failure>(port.requestAccess())

        assertEquals(KadreFailure.PermissionDenied(KadrePermission.RawInput), result.reason)
        assertEquals(1, native.permissionRequests)
        assertEquals(
            Capability.Supported(
                Unit,
                FeatureAvailability.Unavailable(KadreFailure.PermissionDenied(KadrePermission.RawInput)),
            ),
            port.rawInputCapability,
        )
    }

    @Test
    fun concurrentRegistrationsShareOnePermissionPromptAndFanOutDeviceCounts() = runBlocking {
        val permissionResult = CompletableDeferred<Boolean>()
        val native = RecordingRawInputNative(
            preflightGranted = false,
            request = { permissionResult.await() },
        )
        val broker = broker(native)
        val firstPort = broker.openPort()
        val secondPort = broker.openPort()
        val firstRequest = async { firstPort.requestAccess() }

        withTimeout(2.seconds) { native.permissionRequestStarted.await() }
        val secondRequest = async(start = CoroutineStart.UNDISPATCHED) { secondPort.requestAccess() }
        permissionResult.complete(true)

        val first = firstRequest.await().requireValue()
        val second = secondRequest.await().requireValue()
        val firstEvent = async(start = CoroutineStart.UNDISPATCHED) { first.events.filterInput().first() }
        val secondEvent = async(start = CoroutineStart.UNDISPATCHED) { second.events.filterInput().first() }

        native.emit(AppKitRawInputNativeEvent.Motion(deltaX = 17L, deltaY = -3L))

        assertEquals(1, native.permissionRequests)
        assertEquals(1, native.installations)
        assertEquals(RawInputUnit.DeviceCount, firstEvent.await().unit)
        assertEquals(17.0, firstEvent.await().deltaX)
        assertEquals(-3.0, secondEvent.await().deltaY)
        assertNull(secondEvent.await().deviceId)
        assertTrue(native.lastTapEventWasReturnedUnchanged)
        first.close()
        second.close()
    }

    @Test
    fun timeoutSuspendsThenReenablesWithoutClosingRegistrations() = runBlocking {
        val native = RecordingRawInputNative(preflightGranted = true)
        val port = broker(native).openPort()
        val access = port.requestAccess().requireValue()

        native.emit(AppKitRawInputNativeEvent.DisabledByTimeout)

        assertEquals(1, native.reenableCount)
        assertEquals(Capability.Supported(Unit, FeatureAvailability.Available), port.rawInputCapability)
        assertFalse(native.latestTapClosed)
        access.close()
    }

    @Test
    fun userInputDisableReprobesRevocationBeforeStoppingTheSharedSource() = runBlocking {
        val native = RecordingRawInputNative(preflightGranted = true)
        val port = broker(native).openPort()
        val access = port.requestAccess().requireValue()
        val transition = async(start = CoroutineStart.UNDISPATCHED) {
            access.events.filterAvailability().drop(1).first()
        }
        native.preflightGranted = false

        native.emit(AppKitRawInputNativeEvent.DisabledByUserInput)

        assertEquals(FeatureAvailability.RequiresPermission(KadrePermission.InputMonitoring), transition.await())
        assertEquals(
            Capability.Supported(
                Unit,
                FeatureAvailability.Unavailable(KadreFailure.PermissionDenied(KadrePermission.RawInput)),
            ),
            port.rawInputCapability,
        )
        assertTrue(native.latestTapClosed)
        access.close()
    }

    @Test
    fun closingTheLastRegistrationClosesTheOneNativeTap() = runBlocking {
        val native = RecordingRawInputNative(preflightGranted = true)
        val port = broker(native).openPort()
        val first = port.requestAccess().requireValue()
        val second = port.requestAccess().requireValue()

        first.close()
        assertFalse(native.latestTapClosed)
        second.close()

        assertTrue(native.latestTapClosed)
        assertEquals(1, native.closeCount)
    }

    private fun broker(native: RecordingRawInputNative): AppKitRawInputBroker = AppKitRawInputBroker(
        permission = AppKitPermissionBroker(native),
        native = native,
    )
}

private suspend fun KadreResult<org.graphiks.kadre.internal.runtime.RawInputPortLease>.requireValue() =
    when (this) {
        is KadreResult.Success -> value
        is KadreResult.Failure -> error("expected success, got $reason")
    }

private fun kotlinx.coroutines.flow.Flow<RawInputPortLeaseEvent>.filterAvailability() =
    filterIsInstance<RawInputPortLeaseEvent.Availability>().let { observations ->
        observations.map { it.availability }
    }

private fun kotlinx.coroutines.flow.Flow<RawInputPortLeaseEvent>.filterInput() =
    filterIsInstance<RawInputPortLeaseEvent.Input>().let { observations ->
        observations.map { it.input }
    }

private class RecordingRawInputNative(
    var preflightGranted: Boolean,
    private val requestResult: Boolean = true,
    private val request: (suspend () -> Boolean)? = null,
) : AppKitRawInputNative {
    var permissionRequests = 0
        private set
    var installations = 0
        private set
    var reenableCount = 0
        private set
    var closeCount = 0
        private set
    val permissionRequestStarted = CompletableDeferred<Unit>()
    private lateinit var latestTap: RecordingRawInputTap
    val latestTapClosed: Boolean
        get() = latestTap.closed
    var lastTapEventWasReturnedUnchanged = false
        private set
    override fun preflightPermission(): Boolean = preflightGranted

    override suspend fun requestPermission(): Boolean {
        permissionRequests += 1
        permissionRequestStarted.complete(Unit)
        return request?.invoke() ?: requestResult
    }

    override fun installTap(
        listener: (AppKitRawInputNativeEvent) -> Unit,
    ): AppKitRawInputNativeTap {
        installations += 1
        return RecordingRawInputTap(listener).also { latestTap = it }
    }

    fun emit(event: AppKitRawInputNativeEvent) {
        val tap = latestTap
        tap.emit(event)
        lastTapEventWasReturnedUnchanged = tap.lastDelivered === event
    }

    inner class RecordingRawInputTap(
        private val listener: (AppKitRawInputNativeEvent) -> Unit,
    ) : AppKitRawInputNativeTap {
        var closed = false
            private set
        var lastDelivered: AppKitRawInputNativeEvent? = null
            private set

        fun emit(event: AppKitRawInputNativeEvent) {
            listener(event)
            lastDelivered = event
        }

        override fun reenable() {
            reenableCount += 1
        }

        override fun close() {
            if (!closed) {
                closed = true
                closeCount += 1
            }
        }
    }
}
