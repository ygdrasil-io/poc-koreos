package org.graphiks.kadre.internal.win32

import java.lang.foreign.MemorySegment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kffi.win32.managed.Win32DisplaySnapshotValue
import org.graphiks.kffi.win32.managed.Win32GraphicsCaptureNativePort

class Win32CapturePortTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching { it.close() } }
        cleanups.clear()
    }

    private class FakeNative : Win32GraphicsCaptureNativePort {
        var initFailure = 0

        override fun roInitialize(initType: Int): Int = initFailure
        override fun roUninitialize() {}

        override fun roGetActivationFactory(classId: MemorySegment, iid: MemorySegment, factory: MemorySegment): Int = 0
        override fun windowsCreateString(source: MemorySegment, length: Int, hstring: MemorySegment): Int = 0
        override fun windowsDeleteString(hstring: MemorySegment): Int = 0
        override fun windowsGetStringRawBuffer(hstring: MemorySegment, length: MemorySegment): MemorySegment = MemorySegment.NULL

        override fun d3d11CreateDevice(
            adapter: MemorySegment, driverType: Int, software: MemorySegment, flags: Int,
            levels: MemorySegment, levelCount: Int, sdkVersion: Int,
            device: MemorySegment, featureLevel: MemorySegment, context: MemorySegment,
        ): Int = 0

        override fun createDirect3D11DeviceFromDXGIDevice(dxgiDevice: MemorySegment, graphicsDevice: MemorySegment): Int = 0
    }

    private fun mode(width: Int, height: Int): org.graphiks.kffi.win32.managed.Win32DisplayModeValue =
        org.graphiks.kffi.win32.managed.Win32DisplayModeValue(key = width * 1000L + height, width = width, height = height, hz = 60, bits = 32)

    private fun monitors(): List<Win32DisplaySnapshotValue> = listOf(
        Win32DisplaySnapshotValue(
            monitor = 0x10001L, left = 0, top = 0, right = 3440, bottom = 1440,
            workLeft = 0, workTop = 0, workRight = 3440, workBottom = 1392,
            scale = 1.0, modes = listOf(mode(3440, 1440)), current = mode(3440, 1440),
        ),
        Win32DisplaySnapshotValue(
            monitor = 0x10002L, left = 3440, top = 0, right = 5760, bottom = 1440,
            workLeft = 3440, workTop = 0, workRight = 5760, workBottom = 1392,
            scale = 1.0, modes = listOf(mode(2320, 1440)), current = mode(2320, 1440),
        ),
    )

    private fun port(native: Win32GraphicsCaptureNativePort = FakeNative()): Win32CapturePort {
        val p = Win32CapturePort(native, monitors = ::monitors)
        cleanups.add(p)
        return p
    }

    @Test fun activationFailurePublishesHonestUnavailable() {
        val snapshot = port(FakeNative().apply { initFailure = -2147024891 }).initialSnapshot
        val unavailable = assertIs<CapturePortSources.Unavailable>(snapshot.sources)
        assertIs<KadreFailure.PlatformFailure>(unavailable.failure)
        assertIs<Capability.Unsupported>(snapshot.capabilities.screen)
    }

    @Test fun monitorsEnumerateAsDisplaySourcesWithBounds() {
        val snapshot = port().initialSnapshot
        val enumerated = assertIs<CapturePortSources.Enumerated>(snapshot.sources)
        assertEquals(2, enumerated.values.size)
        val primary = enumerated.values.first()
        assertEquals(CaptureSourceKind.Display, primary.kind)
        assertEquals(0x10001L, primary.key.value)
        assertEquals(PhysicalSize(3440, 1440), primary.size)
        assertEquals("win32-display", primary.key.namespace)
    }

    @Test fun hostChoiceIsRefusedInsteadOfSatisfiedByAnArbitraryWindow() = runBlocking {
        val result = port().reserve(CapturePortTarget.HostChoice, CaptureRequest())
        val failure = assertIs<KadreResult.Failure>(result)
        assertIs<KadreFailure.Unsupported>(failure.reason)
        Unit
    }

    @Test fun regionAndForeignFormatRequestsAreRefused() = runBlocking {
        val p = port()
        val region = CaptureRequest(
            region = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(100, 100)).let {
                org.graphiks.kadre.capture.CaptureRegion(it)
            },
        )
        assertIs<KadreResult.Failure>(p.reserve(CapturePortTarget.HostChoice, region))
        val foreign = CaptureRequest(preferredFormats = listOf(PixelFormat.Rgba8))
        assertIs<KadreResult.Failure>(p.reserve(CapturePortTarget.HostChoice, foreign))
        Unit
    }

    @Test fun unknownSourceKeyIsAnInvalidTarget() = runBlocking {
        val result = port().reserve(
            CapturePortTarget.Source(CapturePortSourceKey("win32-display", 99999L)),
            CaptureRequest(),
        )
        val failure = assertIs<KadreResult.Failure>(result)
        assertIs<KadreFailure.InvalidRequest>(failure.reason)
        Unit
    }
}
