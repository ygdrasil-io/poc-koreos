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
 * Kadre-internal dispatch records. Every owner is registered for teardown at
 * creation so one failing test can never leak the single WndProc route into
 * the following tests.
 */
class Win32NativeContractTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun newHost(): Win32ControlHost = Win32ControlHost().also(cleanups::add)

    private fun newInjector(): Win32MessageInjector = Win32MessageInjector().also(cleanups::add)

    /**
     * Pumps the thread message queue while waiting: a posted message only
     * reaches the WndProc when this thread drains it through pollOne
     * (PeekMessageW + DispatchMessageW).
     */
    private fun drainUntil(host: Win32ControlHost, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !condition()) {
            if (!host.pollOne(25)) Thread.sleep(25)
        }
        assertTrue(condition(), "expected condition within 5s")
    }

    @Test
    fun controlWindowCreated() {
        val host = newHost()
        host.open { _, _, _, _ -> 0L }
        assertTrue(host.hwnd != 0L, "control window must expose a non-null hwnd")
    }

    @Test
    fun injectedMessageIsObserved() {
        val host = newHost()
        val injector = newInjector()
        val observed = CountDownLatch(1)
        host.open { _, message, _, _ ->
            assertEquals(Win32ControlWindow.TEST_MESSAGE, message)
            observed.countDown()
            0L
        }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 1L, lParam = 2L))
        drainUntil(host) { observed.await(0, TimeUnit.MILLISECONDS) }
    }

    @Test
    fun injectedPayloadIsCopied() {
        val host = newHost()
        val injector = newInjector()
        host.open { _, message, wParam, lParam ->
            assertEquals(Win32ControlWindow.TEST_MESSAGE, message)
            assertEquals(777L, wParam)
            assertEquals(42L, lParam)
            0L
        }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 777L, lParam = 42L))
        drainUntil(host) { host.dispatches.isNotEmpty() }
        val dispatch = host.dispatches.single()
        assertEquals(777L, dispatch.wParam)
        assertEquals(42L, dispatch.lParam)
    }

    @Test
    fun injectedMessageIsDispatchedExactlyOnce() {
        val host = newHost()
        val injector = newInjector()
        host.open { _, _, _, _ -> 0L }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 9L, lParam = 9L))
        drainUntil(host) { host.dispatches.isNotEmpty() }
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
        val host = newHost()
        val injector = newInjector()
        host.open { _, _, _, _ -> 0L }
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 5L, lParam = 5L))
        drainUntil(host) { host.dispatches.isNotEmpty() }
        host.close()
        assertTrue(host.isClosed)
        // Quiescence (décisions D2) : après close, aucun dispatch ne peut plus
        // être enregistré. Pas de pompe après fermeture (pollOne y retourne
        // false par contrat) : une fuite de dispatch ne peut venir que d'une
        // livraison en vol au moment du close — on laisse le temps s'écouler
        // puis on constate l'immutabilité.
        val before = host.dispatches.size
        Thread.sleep(300)
        assertEquals(before, host.dispatches.size)
    }

    @Test
    fun postCloseInjectionDeliversNothing() {
        val host = newHost()
        val injector = newInjector()
        host.open { _, _, _, _ -> 0L }
        // Message en vol au moment du close : ni pompé avant, ni après.
        assertTrue(injector.postTestMessage(host.hwnd, wParam = 6L, lParam = 6L))
        host.close()
        Thread.sleep(300)
        assertTrue(host.dispatches.isEmpty(), "closed host must never dispatch")
    }

    @Test
    fun staleHwndAfterCloseDeliversNothing() {
        val host = newHost()
        val injector = newInjector()
        host.open { _, _, _, _ -> 0L }
        val staleHwnd = host.hwnd
        host.close()
        // Réutilisation d'adresse (sentinelle win32-no-hwnd-resurrection) : un
        // message posté vers l'adresse fermée ne rejoint aucun owner Kadre.
        // La fenêtre étant détruite par close(), Windows ne distribue plus rien
        // vers elle ; l'assertion constate qu'aucun chemin Kadre ne réenregistre.
        injector.postTestMessage(staleHwnd, wParam = 7L, lParam = 7L)
        Thread.sleep(300)
        assertTrue(host.dispatches.isEmpty(), "stale hwnd must not resurrect a dispatch")
    }

    @Test
    fun doubleCloseLeavesWindowDestroyedWithoutError() {
        val host = newHost()
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
