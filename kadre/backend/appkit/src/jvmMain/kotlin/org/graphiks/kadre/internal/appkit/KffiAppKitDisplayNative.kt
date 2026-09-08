@file:OptIn(org.graphiks.kffi.objc.PlatformAvailability::class)

package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.internal.runtime.DisplayPortDisplay
import org.graphiks.kadre.internal.runtime.DisplayPortMode
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kffi.objc.appkit.AppKitScreenServices
import org.graphiks.kffi.objc.appkit.CGDisplayBoundsSnapshot
import org.graphiks.kffi.objc.appkit.CGDisplayReconfigurationObserver
import org.graphiks.kffi.objc.appkit.AppKitDisplayServices
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayLease
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayLeaseOpenResult
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayNativeFailure
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayReleaseResult
import org.graphiks.kffi.objc.appkit.ExclusiveDisplayTerminal
import kotlin.math.abs
import kotlin.math.roundToLong

/** Runtime guard for the public NSScreen-to-CoreGraphics identity available on macOS 26+. */
internal class AppKitDisplayAvailability(
    systemVersion: String = System.getProperty("os.version", ""),
) {
    val isAvailable: Boolean = systemVersion.numericVersionOrNull()
        ?.let { it >= APPKIT_DISPLAY_MINIMUM_VERSION }
        ?: false
}

private val APPKIT_DISPLAY_MINIMUM_VERSION = AppKitNumericVersion(26L, 0L, 0L)

/** KFFI-only adapter that snapshots AppKit/CoreGraphics display state into Kadre's private SPI. */
internal class KffiAppKitDisplayNative(
    private val services: KffiAppKitDisplayServices = SystemKffiAppKitDisplayServices,
) : AppKitDisplayNative {
    private val mappingLock = Any()
    private var displayIdsByKey: Map<Long, Int> = emptyMap()

    override val enumerationCapability: Capability<Unit> =
        Capability.Supported(Unit, FeatureAvailability.Available)

    override fun snapshot(): DisplayPortSnapshot {
        synchronized(mappingLock) { displayIdsByKey = emptyMap() }
        return KffiAppKitMainThread.call {
        val displays = services.enumerateDisplays()
        val screensByDisplayId = services.enumerateScreens().associateBy(KffiAppKitNativeScreen::displayId)
        check(screensByDisplayId.size == displays.size && displays.all { it.id in screensByDisplayId }) {
            "AppKit and CoreGraphics display inventories differ"
        }
        check(screensByDisplayId.values.count(KffiAppKitNativeScreen::isPrimary) == 1) {
            "AppKit must report exactly one primary screen"
        }

        val snapshot = DisplayPortSnapshot(
            primaryKey = screensByDisplayId.values.single(KffiAppKitNativeScreen::isPrimary).displayId.displayKey(),
            displays = displays.map { display ->
                val screen = checkNotNull(screensByDisplayId[display.id])
                display.toPortDisplay(screen)
            },
        )
        synchronized(mappingLock) {
            displayIdsByKey = displays.associate { display -> display.id.displayKey() to display.id }
        }
        snapshot
        }
    }

    internal fun nativeDisplayId(displayKey: Long): Int? = synchronized(mappingLock) {
        displayIdsByKey[displayKey]
    }

    override fun observeReconfiguration(listener: () -> Unit): AutoCloseable =
        KffiAppKitMainThread.call { services.observeReconfiguration(listener) }

    override fun close() = Unit
}

/** Production adapter for the managed KFFI CoreGraphics lease; no Kadre FFI is involved. */
internal class KffiAppKitExclusiveDisplayBridge(
    private val displaySource: KffiAppKitDisplayNative,
    private val platformAvailability: AppKitDisplayAvailability = AppKitDisplayAvailability(),
    private val openLease: (Int, Long) -> ExclusiveDisplayLeaseOpenResult = AppKitDisplayServices::openExclusiveLease,
) : AppKitExclusiveDisplayBridge {
    override val availability: AppKitExclusiveBridgeAvailability
        get() = if (platformAvailability.isAvailable) AppKitExclusiveBridgeAvailability.Available
        else AppKitExclusiveBridgeAvailability.Unavailable(
            KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "os-version-unavailable"),
        )

    override fun open(displayKey: Long, modeKey: Long): AppKitExclusiveDisplayOpenResult {
        val displayId = displaySource.nativeDisplayId(displayKey)
            ?: return AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(
                KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "display-mapping-unavailable"),
            )
        return try {
            openLease(displayId, modeKey).toKadreResult(displayKey)
        } catch (_: Exception) {
            AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(coreGraphicsFailure("open-exception"))
        } catch (_: LinkageError) {
            AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(coreGraphicsFailure("open-exception"))
        }
    }
}

private fun ExclusiveDisplayLeaseOpenResult.toKadreResult(displayKey: Long): AppKitExclusiveDisplayOpenResult = when (this) {
    is ExclusiveDisplayLeaseOpenResult.Opened -> AppKitExclusiveDisplayOpenResult.Opened(lease.toKadreLease(displayKey))
    is ExclusiveDisplayLeaseOpenResult.FailedBeforeCapture ->
        AppKitExclusiveDisplayOpenResult.FailedBeforeCapture(failure.toKadreFailure())
    is ExclusiveDisplayLeaseOpenResult.FailedAfterCapture -> AppKitExclusiveDisplayOpenResult.FailedAfterCapture(
        terminal = terminal.toKadreTerminal(),
        cleanup = cleanup.toKadreRelease(),
        recovery = recovery?.toKadreLease(displayKey),
        failure = failure.toKadreFailure(),
    )
}

private fun ExclusiveDisplayLease.toKadreLease(displayKey: Long): AppKitExclusiveDisplayLease =
    object : AppKitExclusiveDisplayLease {
        override val displayKey: Long = displayKey
        override val displayId: Int = this@toKadreLease.displayId

        override fun readback(): AppKitExclusiveDisplayReadback =
            AppKitExclusiveDisplayReadback(this@toKadreLease.readback().terminal.toKadreTerminal())

        override fun release(): AppKitExclusiveDisplayReleaseResult =
            this@toKadreLease.release().toKadreRelease()
    }

private fun ExclusiveDisplayReleaseResult.toKadreRelease(): AppKitExclusiveDisplayReleaseResult =
    AppKitExclusiveDisplayReleaseResult(terminal.toKadreTerminal(), failures.map(ExclusiveDisplayNativeFailure::toKadreFailure))

private fun ExclusiveDisplayTerminal.toKadreTerminal(): AppKitExclusiveDisplayTerminal = when (this) {
    is ExclusiveDisplayTerminal.Captured -> AppKitExclusiveDisplayTerminal.Captured(modeIdentity)
    is ExclusiveDisplayTerminal.Released -> AppKitExclusiveDisplayTerminal.Released(modeIdentity)
    ExclusiveDisplayTerminal.Unknown -> AppKitExclusiveDisplayTerminal.Unknown
}

private fun ExclusiveDisplayNativeFailure.toKadreFailure(): KadreFailure.PlatformFailure =
    coreGraphicsFailure(operation.name.toKebabCase())

private fun String.toKebabCase(): String = replace(Regex("(?<!^)([A-Z])"), "-$1").lowercase()

private fun coreGraphicsFailure(code: String): KadreFailure.PlatformFailure =
    KadreFailure.PlatformFailure(KadrePlatform.AppKit, "exclusive-fullscreen", "coregraphics-$code")

/** Detached KFFI data source, allowing the mapping invariants to be unit tested without AppKit. */
internal interface KffiAppKitDisplayServices {
    fun enumerateDisplays(): List<KffiAppKitNativeDisplay>

    fun enumerateScreens(): List<KffiAppKitNativeScreen>

    fun observeReconfiguration(listener: () -> Unit): AutoCloseable
}

internal data class KffiAppKitNativeDisplay(
    val id: Int,
    val pixelWidth: Long,
    val pixelHeight: Long,
    val bounds: CGDisplayBoundsSnapshot,
    val modes: List<KffiAppKitNativeDisplayMode>,
    val currentMode: KffiAppKitNativeCurrentMode,
)

internal data class KffiAppKitNativeDisplayMode(
    val modeIdentity: Long,
    val pixelWidth: Long,
    val pixelHeight: Long,
    val refreshRateHz: Double?,
    val ioFlags: Long,
)

internal data class KffiAppKitNativeCurrentMode(
    val modeIdentity: Long,
    val pixelWidth: Long,
    val pixelHeight: Long,
    val refreshRateHz: Double?,
    val ioFlags: Long,
)

internal data class KffiAppKitNativeScreen(
    val displayId: Int,
    val isPrimary: Boolean,
    val frame: CGDisplayBoundsSnapshot,
    val visibleFrame: CGDisplayBoundsSnapshot,
    val backingScaleFactor: Double,
    val name: String,
)

private object SystemKffiAppKitDisplayServices : KffiAppKitDisplayServices {
    override fun enumerateDisplays(): List<KffiAppKitNativeDisplay> = AppKitDisplayServices.enumerate().map { display ->
        val modes = AppKitDisplayServices.allModes(display.id).map { mode ->
            KffiAppKitNativeDisplayMode(
                modeIdentity = mode.modeIdentity,
                pixelWidth = mode.pixelWidth,
                pixelHeight = mode.pixelHeight,
                refreshRateHz = mode.refreshRateHz,
                ioFlags = mode.ioFlags,
            )
        }
        AppKitDisplayServices.currentMode(display.id).use { current ->
            KffiAppKitNativeDisplay(
                id = display.id,
                pixelWidth = display.pixelWidth,
                pixelHeight = display.pixelHeight,
                bounds = AppKitDisplayServices.bounds(display.id),
                modes = modes,
                currentMode = KffiAppKitNativeCurrentMode(
                    modeIdentity = current.modeIdentity,
                    pixelWidth = current.pixelWidth,
                    pixelHeight = current.pixelHeight,
                    refreshRateHz = current.refreshRateHz,
                    ioFlags = current.ioFlags,
                ),
            )
        }
    }

    override fun enumerateScreens(): List<KffiAppKitNativeScreen> = AppKitScreenServices.snapshots().map { screen ->
        KffiAppKitNativeScreen(
            displayId = screen.displayId,
            isPrimary = screen.isPrimary,
            frame = screen.frame,
            visibleFrame = screen.visibleFrame,
            backingScaleFactor = screen.backingScaleFactor,
            name = screen.localizedName,
        )
    }

    override fun observeReconfiguration(listener: () -> Unit): AutoCloseable =
        CGDisplayReconfigurationObserver { listener() }
}

private fun KffiAppKitNativeDisplay.toPortDisplay(screen: KffiAppKitNativeScreen): DisplayPortDisplay {
    val scale = screen.backingScaleFactor
    check(scale.isFinite() && scale > 0.0) { "AppKit reported an invalid backing scale" }
    val bounds = bounds.toPhysicalRect("CoreGraphics display bounds")
    check(bounds.size.width.toLong() == pixelWidth && bounds.size.height.toLong() == pixelHeight) {
        "CoreGraphics display bounds do not match display pixels"
    }

    val modes = modes.map { mode ->
        check(mode.modeIdentity >= 0L) { "CoreGraphics display mode identity must be non-negative" }
        DisplayPortMode(
            key = mode.modeIdentity,
            physicalSize = PhysicalSize(mode.pixelWidth.toPhysicalDimension("mode width"), mode.pixelHeight.toPhysicalDimension("mode height")),
            refreshRateHz = mode.refreshRateHz,
            bitDepth = null,
        )
    }
    check(modes.isNotEmpty()) { "CoreGraphics returned no display modes" }
    check(modes.map(DisplayPortMode::key).distinct().size == modes.size) {
        "CoreGraphics display mode identities must be unique"
    }
    val matchingMode = modes.singleOrNull { mode -> mode.key == currentMode.modeIdentity }
    check(matchingMode != null) { "Current CoreGraphics display mode identity is unavailable" }

    return DisplayPortDisplay(
        key = id.displayKey(),
        type = DisplayType.Physical,
        name = screen.name,
        bounds = bounds,
        workArea = screen.toWorkArea(bounds, scale),
        scaleFactor = scale,
        currentModeKey = matchingMode.key,
        modes = modes,
    )
}

private fun KffiAppKitNativeScreen.toWorkArea(
    coreGraphicsBounds: PhysicalRect,
    scale: Double,
): PhysicalRect {
    check(visibleFrame.x >= frame.x && visibleFrame.y >= frame.y) { "AppKit visible frame starts outside its screen" }
    check(visibleFrame.x + visibleFrame.width <= frame.x + frame.width) { "AppKit visible frame exceeds screen width" }
    check(visibleFrame.y + visibleFrame.height <= frame.y + frame.height) { "AppKit visible frame exceeds screen height" }
    check(frame.width.toPhysicalDimension(scale, "screen width") == coreGraphicsBounds.size.width) {
        "AppKit screen width does not match CoreGraphics bounds"
    }
    check(frame.height.toPhysicalDimension(scale, "screen height") == coreGraphicsBounds.size.height) {
        "AppKit screen height does not match CoreGraphics bounds"
    }

    val localX = (visibleFrame.x - frame.x).toPhysicalCoordinate(scale, "visible frame x")
    val localTop = (frame.height - (visibleFrame.y - frame.y + visibleFrame.height))
        .toPhysicalCoordinate(scale, "visible frame top")
    val width = visibleFrame.width.toPhysicalDimension(scale, "visible frame width")
    val height = visibleFrame.height.toPhysicalDimension(scale, "visible frame height")
    return PhysicalRect(
        origin = PhysicalPoint(
            Math.addExact(coreGraphicsBounds.origin.x, localX),
            Math.addExact(coreGraphicsBounds.origin.y, localTop),
        ),
        size = PhysicalSize(width, height),
    )
}

private fun CGDisplayBoundsSnapshot.toPhysicalRect(name: String): PhysicalRect = PhysicalRect(
    origin = PhysicalPoint(x.toPhysicalCoordinate(name), y.toPhysicalCoordinate(name)),
    size = PhysicalSize(width.toPhysicalDimension(name), height.toPhysicalDimension(name)),
)

private fun Long.toPhysicalDimension(name: String): Int {
    require(this in 1L..Int.MAX_VALUE.toLong()) { "$name does not fit a physical size" }
    return toInt()
}

private fun Double.toPhysicalDimension(name: String): Int = toPhysicalCoordinate(name).also {
    require(it > 0) { "$name must be positive" }
}

private fun Double.toPhysicalDimension(scale: Double, name: String): Int =
    (this * scale).toPhysicalDimension(name)

private fun Double.toPhysicalCoordinate(name: String): Int {
    require(isFinite()) { "$name must be finite" }
    val rounded = roundToLong()
    require(abs(this - rounded) <= PHYSICAL_EPSILON) { "$name must be an exact physical pixel" }
    require(rounded in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$name does not fit a physical coordinate" }
    return rounded.toInt()
}

private fun Double.toPhysicalCoordinate(scale: Double, name: String): Int =
    (this * scale).toPhysicalCoordinate(name)

private fun Int.displayKey(): Long = toUInt().toLong()

private const val PHYSICAL_EPSILON = 1e-6
