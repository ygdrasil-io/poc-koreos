package org.graphiks.kadre.internal.win32

import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendKind
import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider

/**
 * P-3 : la découverte globale trouve exactement un provider Win32, sans
 * jamais charger de natif (la construction du provider est inerte ; seul le
 * run résout des symboles — réglage prouvé par les suites phase 0).
 */
class Win32DiscoveryTest {
    @Test
    fun serviceLoaderFindsExactlyOneWin32Provider() {
        val providers = ServiceLoader
            .load(DesktopBackendProvider::class.java)
            .toList()
        val win32 = providers.filter { it.backend == DesktopBackendKind.Win32 }
        assertEquals(1, win32.size, "exactly one win32 provider expected, got $providers")
        assertTrue(win32.single() is Win32BackendProvider)
    }

    @Test
    fun availabilityIsExactForTheRunningOs() {
        val provider = Win32BackendProvider()
        assertEquals(Win32BackendProvider.isWindows(), provider.isAvailable())
    }
}
