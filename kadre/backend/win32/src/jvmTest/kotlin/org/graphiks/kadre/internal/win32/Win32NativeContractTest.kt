package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.contracts.driver.win32.Win32MessageInjector
import org.graphiks.kffi.win32.managed.Win32ControlWindow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * W32-001/002/003 native half (spec R4, R6): real Windows message queue,
 * injection through the external driver only, observation through the
 * Kadre-internal dispatch records.
 */
class Win32NativeContractTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun drainUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !condition()) {
            Thread.sleep(25)
        }
        assertTrue(condition(), "expected condition within 5s")
    }

    @Test
    fun controlWindowCreated() {
        val host = Win32ControlHost()
        cleanups.add(host)
        host.open { _, _, _, _ -> 0L }
        assertTrue(host.hwnd != 0L, "control window must expose a non-null hwnd")
    }

    @Test
    fun injectedMessageIsObserved() {
        val host = Win32ControlHost()
        cleanups.add(host)
        val injector = Win32MessageInjector()
        cleanups.add(injector)
        val observed = CountDownLatch(1)
        host.open { _, message, _, _ ->
            assertEquals(Win32ControlWindow.TEST_MESSAGE, message)
            observed.countDown()
            0L
        }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 1L, lParam = 2L))
        drainUntil { observed.await(0, TimeUnit.MILLISECONDS) }
    }

    @Test
    fun injectedPayloadIsCopied() {
        val host = Win32ControlHost()
        cleanups.add(host)
        val injector = Win32MessageInjector()
        cleanups.add(injector)
        host.open { _, message, wParam, lParam ->
            assertEquals(Win32ControlWindow.TEST_MESSAGE, message)
            assertEquals(777L, wParam)
            assertEquals(42L, lParam)
            0L
        }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 777L, lParam = 42L))
        drainUntil { host.dispatches.isNotEmpty() }
        val dispatch = host.dispatches.single()
        assertEquals(777L, dispatch.wParam)
        assertEquals(42L, dispatch.lParam)
    }

    @Test
    fun injectedMessageIsDispatchedExactlyOnce() {
        val host = Win32ControlHost()
        cleanups.add(host)
        val injector = Win32MessageInjector()
        cleanups.add(injector)
        host.open { _, _, _, _ -> 0L }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 9L, lParam = 9L))
        drainUntil { host.dispatches.isNotEmpty() }
        val countAfterFirst = host.dispatches.size
        // Drain complet sans nouvelle injection : aucune redelivery.
        val deadline = System.currentTimeMillis() + 500
        while (System.currentTimeMillis() < deadline) {
            if (!host.pollOne(50)) break
        }
        assertEquals(1, countAfterFirst, "exactly-once delivery required")
        assertEquals(countAfterFirst, host.dispatches.size)
    }

    @Test
    fun closeReachesQuiescenceWithoutLateDelivery() {
        val host = Win32ControlHost()
        val injector = Win32MessageInjector()
        host.open { _, _, _, _ -> 0L }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 5L, lParam = 5L))
        drainUntil { host.dispatches.isNotEmpty() }
        host.close()
        assertTrue(host.isClosed)
        // Quiescence : plus aucune livraison après fermeture (décisions D2).
        val before = host.dispatches.size
        val deadline = System.currentTimeMillis() + 300
        while (System.currentTimeMillis() < deadline) {
            if (!host.pollOne(50)) break
        }
        assertEquals(before, host.dispatches.size)
    }

    @Test
    fun postCloseInjectionDeliversNothing() {
        val host = Win32ControlHost()
        val injector = Win32MessageInjector()
        host.open { _, _, _, _ -> 0L }
        host.close()
        val queued = injector.postTestMessage(host.hwnd, wParam = 6L, lParam = 6L)
        // Le hwnd est fermé : PostMessageW échoue OU le message reste non distribué.
        if (queued) {
            val deadline = System.currentTimeMillis() + 300
            while (System.currentTimeMillis() < deadline) {
                if (!host.pollOne(50)) break
            }
        }
        assertTrue(host.dispatches.isEmpty(), "closed host must never dispatch")
    }

    @Test
    fun staleHwndAfterCloseDeliversNothing() {
        val host = Win32ControlHost()
        val injector = Win32MessageInjector()
        host.open { _, _, _, _ -> 0L }
        val staleHwnd = host.hwnd
        host.close()
        // Réutilisation d'adresse (sentinelle win32-no-hwnd-resurrection) : un
        // message posté vers l'adresse fermée ne rejoint aucun owner Kadre.
        injector.postTestMessage(staleHwnd, wParam = 7L, lParam = 7L)
        val deadline = System.currentTimeMillis() + 300
        while (System.currentTimeMillis() < deadline) {
            if (!host.pollOne(50)) break
        }
        assertTrue(host.dispatches.isEmpty(), "stale hwnd must not resurrect a dispatch")
    }

    @Test
    fun doubleCloseLeavesWindowDestroyedWithoutError() {
        val host = Win32ControlHost()
        cleanups.add(host)
        host.open { _, _, _, _ -> 0L }
        val hwnd = host.hwnd
        host.close()
        host.close() // idempotent : même outcome, pas d'erreur
        assertTrue(host.isClosed)
        assertEquals(
            0,
            org.graphiks.kffi.win32.generated.IsWindow(
                java.lang.foreign.MemorySegment.ofAddress(hwnd),
            ),
        )
    }
}
