package org.graphiks.kadre.internal.wayland

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec

/**
 * Preuves O2 des scénarios fenêtres de la tranche 2 (design
 * `WAYLAND-PHASE-2-WINDOWS-DESIGN.md`) : refus normatifs avant native
 * (SN-1/SN-2, WIN-010), création borderless, échec de création sans résidu
 * (SN-7), close compositor et close programmatique (L914), succession
 * primary et arming `stopWhenLastWindowClosed` (P-1/P-3, WIN-012), lease
 * `withDesktopHandle` borné (SN-8).
 */
class WaylandWindowO2Test {

    private fun session(bridge: FakeBridge): WaylandHostSession =
        WaylandHostSession.start({ _ -> bridge }, "wayland-test", testEnvironment(), { SessionFakeWakeup() })

    private fun testEnvironment(): WaylandProbeEnvironment =
        WaylandProbeEnvironment(
            isLinux = true,
            waylandSocket = null,
            xdgRuntimeDir = "/run/user/1000",
            waylandDisplay = "wayland-test",
            socketExists = { true },
        )

    private fun driver(
        bridge: FakeBridge,
        session: WaylandHostSession,
        onLastWindowClosed: (() -> Unit)? = null,
    ): WaylandWindowRuntimeDriver =
        WaylandWindowRuntimeDriver.create(
            session = session,
            resources = KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter { },
            publicWindowCapabilities = true,
            onLastWindowClosed = onLastWindowClosed,
        )

    /**
     * File d'actions pompée en arrière-plan, miroir de la boucle provider :
     * les créations et fermetures marshalées vers la file s'exécutent comme
     * sur le thread pompe réel.
     */
    private fun pump(session: WaylandHostSession): AutoCloseable {
        var running = true
        Thread {
            while (running) {
                session.drainWindowActions()
                Thread.sleep(5)
            }
        }.apply { isDaemon = true; start() }
        return AutoCloseable { running = false }
    }

    private suspend fun openedWindow(driver: WaylandWindowRuntimeDriver, title: String): Window =
        assertIs<WindowRequestOutcome.OpenedHere>(
            driver
                .manager
                .requestWindow(WindowSpec(title = title, decorations = WindowDecorations.Borderless))
                .orThrow()
                .await(),
        ).window

    private fun <T> KadreResult<T>.orThrow(): T = when (this) {
        is KadreResult.Success -> value
        is KadreResult.Failure -> error("expected success, got $reason")
    }

    @Test
    fun defaultDecorationsAreRejectedBeforeNative() {
        val bridge = FakeBridge()
        val session = session(bridge)
        pump(session).use {
        val driver = driver(bridge, session)
        // le spec par défaut porte decorations=System : rejet normatif avant
        // toute création native (SN-1)
        runBlocking {
            val outcome = driver.manager.requestWindow(WindowSpec()).orThrow().await()
            assertIs<WindowRequestOutcome.Rejected>(outcome)
        }
        assertEquals(0, bridge.createWindowingCalls.get(), "une fenêtre native a été créée")
        assertEquals(0, driver.registeredPeerCount())
        driver.close()
        }
        session.teardown()
    }

    @Test
    fun outerPositionIsRefusedBeforeTheNativeLayer() {
        val bridge = FakeBridge()
        val session = session(bridge)
        pump(session).use {
        val driver = driver(bridge, session)
        // SN-2 : une position externe demandée ne produit aucune primitive
        // native — le runtime refuse la propriété initiale non supportée
        runBlocking {
            val outcome = driver.manager
                .requestWindow(
                    WindowSpec(
                        title = "sn2",
                        decorations = WindowDecorations.Borderless,
                        outerPosition = org.graphiks.kadre.surface.PhysicalPoint(10, 20),
                    ),
                )
                .orThrow()
                .await()
            assertIs<WindowRequestOutcome.Rejected>(outcome)
        }
        assertEquals(0, bridge.createWindowingCalls.get())
        driver.close()
        }
        session.teardown()
    }

    @Test
    fun borderlessCreationReachesTheNativeLayer() {
        val bridge = FakeBridge()
        val session = session(bridge)
        pump(session).use {
        val driver = driver(bridge, session)
        runBlocking {
            val window = openedWindow(driver, "o2")
            assertEquals(WindowPhase.Open, window.state.value.phase)
        }
        val fake = bridge.createdWindows.single()
        assertTrue(fake.titles.contains("o2"), "title never set (${fake.titles})")
        assertTrue(fake.appIds.contains("org.graphiks.kadre.wayland"))
        assertEquals(1, bridge.createWindowingCalls.get())
        driver.close()
        assertEquals(1, fake.closeCount.get(), "destroy ordonné à la fermeture du driver")
        }
        session.teardown()
    }

    @Test
    fun failedNativeCreationLeavesNoResidue() {
        val bridge = FakeBridge()
        bridge.failCreateWindowing = true
        val session = session(bridge)
        pump(session).use {
        val driver = driver(bridge, session)
        // SN-7 : l'échec de la création native rejette la demande et ne
        // laisse aucun pair résiduel
        runBlocking {
            val outcome = driver.manager
                .requestWindow(WindowSpec(title = "sn7", decorations = WindowDecorations.Borderless))
                .orThrow()
                .await()
            val rejected = assertIs<WindowRequestOutcome.Rejected>(outcome)
            assertEquals(
                KadreFailure.PlatformFailure(KadrePlatform.Wayland, "window-create", "creation-failed"),
                rejected.failure,
            )
        }
        assertEquals(1, bridge.createWindowingCalls.get())
        assertEquals(0, driver.registeredPeerCount(), "un pair résiduel subsiste après l'échec")
        driver.close()
        }
        session.teardown()
    }

    @Test
    fun compositorCloseTerminatesTheWindowAndStopsTheLastWindowPolicy() {
        val bridge = FakeBridge()
        val session = session(bridge)
        val stopped = CountDownLatch(1)
        pump(session).use {
        val driver = driver(bridge, session) { stopped.countDown() }
        val window = runBlocking { openedWindow(driver, "l914") }
        val fake = bridge.createdWindows.single()
        // L914 : le close compositor n'est pas interceptable — le stimulus
        // nativeClosed termine la fenêtre et déclenche l'arrêt (dernière
        // fenêtre), sans CloseRequested préalable
        fake.deliverNativeClose()
        assertTrue(stopped.await(5, TimeUnit.SECONDS), "stopWhenLastWindowClosed n'a jamais tiré")
        assertNull(driver.peer(window.id), "le pair n'a pas été retiré du registre")
        assertEquals(0, driver.registeredPeerCount())
        driver.close()
        }
        session.teardown()
    }

    @Test
    fun closingWindowsStopsTheSessionOnlyWhenTheLastOneCloses() {
        val bridge = FakeBridge()
        val session = session(bridge)
        val stopped = CountDownLatch(1)
        pump(session).use {
        val driver = driver(bridge, session) { stopped.countDown() }
        val (windowA, windowB) = runBlocking {
            val a = openedWindow(driver, "p1-a")
            val b = openedWindow(driver, "p1-b")
            a to b
        }
        assertEquals(2, bridge.createWindowingCalls.get())
        assertEquals(2, driver.registeredPeerCount())
        runBlocking {
            assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, windowA.close().orThrow())
        }
        // P-1 : la destruction de A n'affecte pas B ; P-3 : l'arrêt ne tire
        // pas tant que la transition n'est pas non-vide → vide
        assertNull(driver.peer(windowA.id))
        assertTrue(driver.peer(windowB.id) != null, "la fenêtre B a été perdue avec A")
        assertFalse(stopped.await(250, TimeUnit.MILLISECONDS), "arrêt prématuré alors que B vit")
        runBlocking {
            assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, windowB.close().orThrow())
        }
        assertTrue(stopped.await(5, TimeUnit.SECONDS), "l'arrêt n'a pas tiré après la dernière fermeture")
        assertEquals(0, driver.registeredPeerCount())
        driver.close()
        }
        session.teardown()
    }

    @Test
    fun handleLeaseIsHonoredWhileActiveAndRefusedAfterClose() {
        val bridge = FakeBridge()
        val session = session(bridge)
        pump(session).use {
        val driver = driver(bridge, session)
        val window = runBlocking { openedWindow(driver, "sn8") }
        val peer = driver.peer(window.id) ?: error("peer introuvable")
        val leaseStarted = java.util.concurrent.CountDownLatch(1)
        val releaseLease = java.util.concurrent.CountDownLatch(1)
        runBlocking {
            // SN-8 : un lease déjà admis court jusqu'au bout de son block —
            // le block est synchrone, il est parqué sur un worker dédié pendant
            // que la fermeture runtime s'exécute
            val activeLease = async(kotlinx.coroutines.Dispatchers.Default) {
                peer.withDesktopHandle { handle ->
                    leaseStarted.countDown()
                    releaseLease.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    handle
                }
            }
            assertTrue(leaseStarted.await(2, TimeUnit.SECONDS), "le lease actif n'a jamais démarré")
            assertEquals(org.graphiks.kadre.window.WindowCloseOutcome.Closed, window.close().orThrow())
            releaseLease.countDown()
            val honored = activeLease.await()
            assertIs<KadreResult.Success<*>>(honored)

            // tout nouveau lease est refusé une fois la fermeture admise
            val refused = peer.withDesktopHandle { error("un block ne doit plus jamais courir") }
            assertIs<KadreResult.Failure>(refused)
            assertEquals(KadreFailure.Closed(KadreResourceKind.Window), refused.reason)
        }
        driver.close()
        }
        session.teardown()
    }
}
