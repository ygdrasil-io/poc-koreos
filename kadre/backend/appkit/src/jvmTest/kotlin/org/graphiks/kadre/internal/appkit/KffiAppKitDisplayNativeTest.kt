package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kffi.objc.appkit.CGDisplayBoundsSnapshot
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayLeaseOpenResult
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayNativeFailure
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayNativeOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KffiAppKitDisplayNativeTest {
    @Test
    fun exclusiveBridgeRejectsMissingAndStaleDisplayMappingsBeforeKffiCapture() {
        val native = KffiAppKitDisplayNative(
            RecordingKffiAppKitDisplayServices(
                displays = listOf(
                    KffiAppKitNativeDisplay(
                        id = 17,
                        pixelWidth = 1920,
                        pixelHeight = 1080,
                        bounds = CGDisplayBoundsSnapshot(0.0, 0.0, 1920.0, 1080.0),
                        modes = listOf(KffiAppKitNativeDisplayMode(701L, 1920, 1080, 60.0, 0)),
                        currentMode = KffiAppKitNativeCurrentMode(701L, 1920, 1080, 60.0, 0),
                    ),
                ),
                screens = listOf(
                    KffiAppKitNativeScreen(
                        displayId = 17,
                        isPrimary = true,
                        frame = CGDisplayBoundsSnapshot(0.0, 0.0, 1920.0, 1080.0),
                        visibleFrame = CGDisplayBoundsSnapshot(0.0, 0.0, 1920.0, 1080.0),
                        backingScaleFactor = 1.0,
                        name = "Display",
                    ),
                ),
            ),
        )
        val opens = mutableListOf<Pair<Int, Long>>()
        val bridge = KffiAppKitExclusiveDisplayBridge(
            displaySource = native,
            platformAvailability = AppKitDisplayAvailability("26.0"),
            openLease = { displayId, modeKey ->
                opens += displayId to modeKey
                ExclusiveDisplayLeaseOpenResult.FailedBeforeCapture(
                    ExclusiveDisplayNativeFailure(ExclusiveDisplayNativeOperation.Capture, "denied"),
                )
            },
        )

        assertIs<AppKitExclusiveDisplayOpenResult.FailedBeforeCapture>(bridge.open(17L, 701L))
        native.snapshot()
        assertIs<AppKitExclusiveDisplayOpenResult.FailedBeforeCapture>(bridge.open(18L, 701L))
        assertIs<AppKitExclusiveDisplayOpenResult.FailedBeforeCapture>(bridge.open(17L, 701L))

        assertEquals(listOf(17 to 701L), opens)
    }

    @Test
    fun displayInventoryRequiresThePublicMacOs26ScreenIdentity() {
        assertFalse(AppKitDisplayAvailability("25.6").isAvailable)
        assertTrue(AppKitDisplayAvailability("26.0").isAvailable)
        assertFalse(AppKitDisplayAvailability("not-a-version").isAvailable)
    }

    @Test
    fun snapshotAssociatesTheCurrentModeAndMapsTheUsableAreaIntoCoreGraphicsPhysicalSpace() {
        val native = KffiAppKitDisplayNative(
            RecordingKffiAppKitDisplayServices(
                displays = listOf(
                    KffiAppKitNativeDisplay(
                        id = 17,
                        pixelWidth = 3000,
                        pixelHeight = 2000,
                        bounds = CGDisplayBoundsSnapshot(-3000.0, 0.0, 3000.0, 2000.0),
                        modes = listOf(
                            KffiAppKitNativeDisplayMode(701L, 1500, 1000, 60.0, 1),
                            KffiAppKitNativeDisplayMode(409L, 3000, 2000, 120.0, 9),
                        ),
                        currentMode = KffiAppKitNativeCurrentMode(409L, 3000, 2000, 120.0, 9),
                    ),
                ),
                screens = listOf(
                    KffiAppKitNativeScreen(
                        displayId = 17,
                        isPrimary = true,
                        frame = CGDisplayBoundsSnapshot(-1500.0, 300.0, 1500.0, 1000.0),
                        visibleFrame = CGDisplayBoundsSnapshot(-1500.0, 300.0, 1500.0, 978.0),
                        backingScaleFactor = 2.0,
                        name = "Studio Display",
                    ),
                ),
            ),
        )

        assertEquals(
            DisplayPortSnapshot(
                primaryKey = 17,
                displays = listOf(
                    DisplayPortDisplay(
                        key = 17,
                        type = DisplayType.Physical,
                        name = "Studio Display",
                        bounds = PhysicalRect(PhysicalPoint(-3000, 0), PhysicalSize(3000, 2000)),
                        workArea = PhysicalRect(PhysicalPoint(-3000, 44), PhysicalSize(3000, 1956)),
                        scaleFactor = 2.0,
                        currentModeKey = 409,
                        modes = listOf(
                            DisplayPortMode(701, PhysicalSize(1500, 1000), 60.0, null),
                            DisplayPortMode(409, PhysicalSize(3000, 2000), 120.0, null),
                        ),
                    ),
                ),
            ),
            native.snapshot(),
        )
    }

    @Test
    fun snapshotRefusesToPublishWhenAppKitAndCoreGraphicsDoNotDescribeTheSameInventory() {
        val native = KffiAppKitDisplayNative(
            RecordingKffiAppKitDisplayServices(
                displays = listOf(
                    KffiAppKitNativeDisplay(
                        id = 17,
                        pixelWidth = 1920,
                        pixelHeight = 1080,
                        bounds = CGDisplayBoundsSnapshot(0.0, 0.0, 1920.0, 1080.0),
                        modes = listOf(KffiAppKitNativeDisplayMode(701L, 1920, 1080, 60.0, 0)),
                        currentMode = KffiAppKitNativeCurrentMode(701L, 1920, 1080, 60.0, 0),
                    ),
                ),
                screens = emptyList(),
            ),
        )

        assertFailsWith<IllegalStateException> { native.snapshot() }
    }

    @Test
    fun snapshotKeepsStableModeIdentityWhenEnumerationOrderChanges() {
        val native = KffiAppKitDisplayNative(
            RecordingKffiAppKitDisplayServices(
                displays = listOf(
                    KffiAppKitNativeDisplay(
                        id = 17,
                        pixelWidth = 1920,
                        pixelHeight = 1080,
                        bounds = CGDisplayBoundsSnapshot(0.0, 0.0, 1920.0, 1080.0),
                        modes = listOf(
                            KffiAppKitNativeDisplayMode(0L, 1920, 1080, 60.0, 0),
                            KffiAppKitNativeDisplayMode(401L, 1920, 1080, 60.0, 0),
                        ),
                        currentMode = KffiAppKitNativeCurrentMode(401L, 1920, 1080, 60.0, 0),
                    ),
                ),
                screens = listOf(
                    KffiAppKitNativeScreen(
                        displayId = 17,
                        isPrimary = true,
                        frame = CGDisplayBoundsSnapshot(0.0, 0.0, 1920.0, 1080.0),
                        visibleFrame = CGDisplayBoundsSnapshot(0.0, 0.0, 1920.0, 1080.0),
                        backingScaleFactor = 1.0,
                        name = "Display",
                    ),
                ),
            ),
        )

        val snapshot = native.snapshot()

        assertEquals(listOf(0L, 401L), snapshot.displays.single().modes.map(DisplayPortMode::key))
        assertEquals(401L, snapshot.displays.single().currentModeKey)
    }

    @Test
    fun reconfigurationObservationUsesTheSameKffiOwnerAndStopsDeliveryWhenClosed() {
        val services = RecordingKffiAppKitDisplayServices(emptyList(), emptyList())
        val native = KffiAppKitDisplayNative(services)
        val events = mutableListOf<Unit>()
        val observation = native.observeReconfiguration { events += Unit }

        services.emitReconfiguration()
        assertEquals(listOf(Unit), events)

        observation.close()
        assertEquals(1, services.observationCloseCount)
        assertNull(services.listener)
    }

    @Test
    fun nativeAdapterEnumeratesTheCompleteCurrentMacOsInventory() {
        if (!System.getProperty("os.name", "").contains("Mac", ignoreCase = true)) return
        if (!AppKitDisplayAvailability().isAvailable) return

        val snapshot = KffiAppKitDisplayNative().snapshot()

        assertTrue(snapshot.displays.isNotEmpty())
        assertEquals(1, snapshot.displays.count { it.key == snapshot.primaryKey })
        snapshot.displays.forEach { display ->
            assertTrue(display.modes.isNotEmpty())
            assertTrue(display.currentModeKey in display.modes.map { it.key })
            val workArea = checkNotNull(display.workArea)
            assertTrue(workArea.origin.x >= display.bounds.origin.x)
            assertTrue(workArea.origin.y >= display.bounds.origin.y)
            assertTrue(workArea.origin.x.toLong() + workArea.size.width <=
                display.bounds.origin.x.toLong() + display.bounds.size.width)
            assertTrue(workArea.origin.y.toLong() + workArea.size.height <=
                display.bounds.origin.y.toLong() + display.bounds.size.height)
        }
    }
}

private class RecordingKffiAppKitDisplayServices(
    private val displays: List<KffiAppKitNativeDisplay>,
    private val screens: List<KffiAppKitNativeScreen>,
) : KffiAppKitDisplayServices {
    var listener: (() -> Unit)? = null
        private set
    var observationCloseCount: Int = 0
        private set

    override fun enumerateDisplays(): List<KffiAppKitNativeDisplay> = displays

    override fun enumerateScreens(): List<KffiAppKitNativeScreen> = screens

    override fun observeReconfiguration(listener: () -> Unit): AutoCloseable {
        check(this.listener == null)
        this.listener = listener
        return AutoCloseable {
            if (this.listener != null) {
                this.listener = null
                observationCloseCount += 1
            }
        }
    }

    fun emitReconfiguration() = checkNotNull(listener).invoke()
}
