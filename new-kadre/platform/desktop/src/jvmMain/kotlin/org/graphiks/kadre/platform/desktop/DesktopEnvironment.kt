package org.graphiks.kadre.platform.desktop

import java.util.Locale

internal enum class DesktopOperatingSystem { MacOS, Windows, Linux, Unsupported }

internal fun detectDesktopOperatingSystem(osName: String): DesktopOperatingSystem {
    val normalized = osName.trim().lowercase(Locale.ROOT)
    return when {
        normalized.contains("mac") || normalized.contains("darwin") -> DesktopOperatingSystem.MacOS
        normalized.startsWith("windows") -> DesktopOperatingSystem.Windows
        normalized.contains("linux") -> DesktopOperatingSystem.Linux
        else -> DesktopOperatingSystem.Unsupported
    }
}
