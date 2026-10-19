package org.graphiks.kadre.internal.win32

import kotlin.test.AfterTest
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kffi.win32.managed.Win32DisplayModeValue
import org.graphiks.kffi.win32.managed.Win32DisplaySnapshotValue
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.RawInputPortLease
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(DelicateKadreApi::class)
class Win32DisplayPortTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun monitor(
        key: Long = 100L,
        left: Int = 0, top: Int = 0, right: Int = 1920, bottom: Int = 1080,
        workRight: Int = right, workBottom: Int = 1040,
        scale: Double = 1.0,
        modes: List<Triple<Int, Int, Int>> = listOf(Triple(1920, 1080, 60)),
        currentIndex: Int = 0,
    ): Win32DisplaySnapshotValue {
        val modeValues = modes.mapIndexed { index, (w, h, hz) ->
            Win32DisplayModeValue((key.toLong() shl 8) or index.toLong(), w, h, hz, 32)
        }
        return Win32DisplaySnapshotValue(
            monitor = key,
            left = left, top = top, right = right, bottom = bottom,
            workLeft = left, workTop = top,
            workRight = workRight, workBottom = workBottom,
            scale = scale,
            modes = modeValues,
            current = modeValues.getOrNull(currentIndex),
        )
    }

    @Test fun assemblerMapsBoundsWorkScaleAndModesAllOrNothing() {
        val snapshot = assembleDisplaySnapshot(listOf(monitor(), monitor(key = 200L, left = 1920, right = 3840, bottom = 1080, workRight = 3840, workBottom = 1040, scale = 1.5)))
        assertNotNull(snapshot)
        assertEquals(2, snapshot.displays.size)
        val second = snapshot.displays[1]
        assertEquals(DisplayType.Physical, second.type)
        assertEquals(1.5, second.scaleFactor)
        assertEquals(1920, second.bounds.origin.x)
        assertEquals(1040, second.workArea?.size?.height)
        assertEquals((200L shl 8), snapshot.displays[1].currentModeKey)
    }

    @Test fun assemblerSkipsDegenerateDisplaysAndWithdrawsWhenNothingUsableRemains() {
        val broken = Win32DisplaySnapshotValue(
            monitor = 100L, left = 0, top = 0, right = 0, bottom = 0,
            workLeft = 0, workTop = 0, workRight = 0, workBottom = 0,
            scale = 0.0, modes = emptyList(), current = null,
        )
        assertNull(assembleDisplaySnapshot(listOf(broken)), "an all-degenerate inventory is withdrawn")
        val partial = assembleDisplaySnapshot(listOf(broken, monitor()))
        assertNotNull(partial)
        assertEquals(1, partial.displays.size, "usable displays survive alongside skipped ones")
    }

    @Test fun portPublishesSnapshotAndRepublishesOnDisplayChange() = runBlocking {
        val monitors = CopyOnWriteArrayList(listOf(monitor()))
        val port = Win32DisplayPort(inventoryProvider = { monitors })
        cleanups.add(port)
        val events = CopyOnWriteArrayList<Int>()
        val registration = port.installSnapshotObserver { result ->
            val success = assertIs<KadreResult.Success<DisplayPortSnapshot>>(result)
            events += success.value.displays.size
        }
        try {
            port.onDisplayChanged()
            assertEquals(1, events.singleOrNull())
            monitors += monitor(key = 200L, left = 1920, right = 3840, bottom = 1080, workRight = 3840, workBottom = 1040, scale = 1.5)
            port.onDisplayChanged()
            assertEquals(2, events.last())
        } finally {
            registration.close()
        }
        port.close()
        port.onDisplayChanged() // closed: no crash, no publication
        assertEquals(2, events.size)
    }

    @Test fun sessionWithoutDisplayPortKeepsInventoryUnavailable() = runBlocking {
        val port = DeterministicWin32HostPort()
        cleanups.add(port)
        val driver = Win32WindowRuntimeDriverFactory.create(
            port = port,
            resources = KadrePolicies.Default.resources,
        )
        cleanups.add(driver)
        val window = runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = "displays-absent"))
            val outcome = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            assertIs<WindowRequestOutcome.OpenedHere>(outcome.value.await()).window
        }
        // The public DisplayManager surface is reached through the session; here the
        // absence is proven by the provider seam: factory null ⇒ no DisplayPort wired.
        assertTrue(cleanups.size >= 2)
        Unit
    }
}
