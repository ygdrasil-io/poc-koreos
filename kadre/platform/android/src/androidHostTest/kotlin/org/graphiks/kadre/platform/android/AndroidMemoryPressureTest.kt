package org.graphiks.kadre.platform.android

import android.content.ComponentCallbacks2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kadre.application.LifecycleCapabilities
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform

/**
 * La source de pression mémoire Android (Task 3, phase 7) contre un fake du seam
 * [AndroidMemoryPressureSeam] — JAMAIS le SDK (le stub android.jar lance à la construction ;
 * la registration `Application.registerComponentCallbacks` est injectée, le miroir exact des
 * ports display/devices qui passent par des seams). Les cinq cas du plan :
 *
 * 1. `runningLevelsMapAndBackgroundNeverDo` — 5→Moderate ; 10→Critical ; 15→Critical ;
 *    20/40/60/80→RIEN (jamais un signal — les niveaux background ne sont jamais des signaux
 *    de pression RUNNING, la perte de visibilité n'est pas de la pression) ;
 * 2. `installFailureIsUnavailable` — un register qui jette → la capability `memoryPressure` =
 *    `Unavailable(PlatformFailure(Android, "memory-pressure", "source-exception"))` (miroir
 *    AppKit), PAS Available, et `install` répond `false` ;
 * 3. `installPublishesAvailableTransition` — avant install : la capability par défaut
 *    (`Unsupported`, le défaut runtime) ; après un succès : `Available` — la transition
 *    réelle exigée par la matrice C de BACKEND-CAPABILITIES ;
 * 4. `sessionCloseKeepsProcessSource` — une session qui se ferme ne passe par AUCUN appel de
 *    déregistration (la source process reste enregistrée, les autres abonnés continuent) ;
 *    la terminaison d'un host parmi plusieurs ne ferme pas la source ; la terminaison du
 *    DERNIER host la ferme (un signal après ne relaie plus) ;
 * 5. `callbackRelayedOffItsStack` — le relais ne s'exécute PAS dans la pile du callback
 *    (miroir AppKit : relayé hors stack — le post du seam) ; rien avant le drain.
 */
class AndroidMemoryPressureTest {

    /**
     * Le fake du seam : la registration et le post sont ENREGISTRÉS, jamais exécutés —
     * `drain` joue les blocs postés, exactement ce qu'un looper ferait après le callback.
     */
    private class FakeSeam : AndroidMemoryPressureSeam {
        val registered = mutableListOf<ComponentCallbacks2>()
        val unregistered = mutableListOf<ComponentCallbacks2>()
        val posted = ArrayDeque<() -> Unit>()
        var registerFailure: Throwable? = null

        override fun register(callbacks: ComponentCallbacks2) {
            registerFailure?.let { throw it }
            registered += callbacks
        }

        override fun unregister(callbacks: ComponentCallbacks2) {
            unregistered += callbacks
        }

        override fun post(block: () -> Unit) {
            posted += block
        }

        /** Joue les blocs postés — le moment « plus tard » du looper principal. */
        fun drain() {
            while (posted.isNotEmpty()) posted.removeFirst().invoke()
        }
    }

    /** Le signal d'un trim, émis comme le SDK le ferait : sur l'instance callbacks posée. */
    private fun FakeSeam.trim(level: Int) {
        registered.single().onTrimMemory(level)
    }

    @Test
    fun runningLevelsMapAndBackgroundNeverDo() {
        val seam = FakeSeam()
        val source = AndroidMemoryPressureSource(seam::register, seam::unregister, seam::post)
        val relayed = mutableListOf<MemoryPressureLevel>()
        assertTrue(source.install { level -> relayed += level })

        // RUNNING_MODERATE (5) → Moderate.
        source.onTrimMemory(5)
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), relayed)

        // RUNNING_LOW (10) et RUNNING_CRITICAL (15) → Critical.
        source.onTrimMemory(10)
        source.onTrimMemory(15)
        seam.drain()
        assertEquals(
            listOf(MemoryPressureLevel.Moderate, MemoryPressureLevel.Critical, MemoryPressureLevel.Critical),
            relayed,
        )

        // Les niveaux background (UI_HIDDEN 20, BACKGROUND 40, MODERATE 60, COMPLETE 80) ne
        // produisent RIEN — jamais un signal : la perte de visibilité n'est pas de la pression.
        for (background in intArrayOf(20, 40, 60, 80)) {
            source.onTrimMemory(background)
        }
        // Un niveau hors table non plus (défense : tout ce qui n'est pas RUNNING ne parle pas).
        source.onTrimMemory(0)
        seam.drain()
        assertEquals(
            listOf(MemoryPressureLevel.Moderate, MemoryPressureLevel.Critical, MemoryPressureLevel.Critical),
            relayed,
        )
    }

    @Test
    fun installFailureIsUnavailable() {
        val seam = FakeSeam()
        seam.registerFailure = IllegalStateException("registration refused")
        val source = AndroidMemoryPressureSource(seam::register, seam::unregister, seam::post)

        // L'install répond l'échec — la capability correspondante est l'Unavailable AppKit.
        assertFalse(source.install { })
        val broker = AndroidProcessMemoryPressure()
        val capabilities = broker.newHost(seam).capabilities
        val memoryPressure = capabilities.memoryPressure
        assertIs<FeatureAvailability.Unavailable>(memoryPressure)
        assertEquals(
            KadreFailure.PlatformFailure(KadrePlatform.Android, "memory-pressure", "source-exception"),
            memoryPressure.failure,
        )
    }

    @Test
    fun installPublishesAvailableTransition() {
        val broker = AndroidProcessMemoryPressure()
        // AVANT install : la capability par défaut — le défaut runtime, `Unsupported`.
        assertEquals(
            LifecycleCapabilities(FeatureAvailability.Unsupported),
            broker.currentCapabilities,
        )

        val seam = FakeSeam()
        // APRÈS l'install (succès) : `Available` — la transition réelle de la matrice C.
        assertEquals(
            LifecycleCapabilities(FeatureAvailability.Available),
            broker.newHost(seam).capabilities,
        )
        assertEquals(1, seam.registered.size)
        assertEquals(
            LifecycleCapabilities(FeatureAvailability.Available),
            broker.currentCapabilities,
        )

        // Une session de plus réutilise la source process : PAS de seconde registration.
        assertEquals(
            LifecycleCapabilities(FeatureAvailability.Available),
            broker.newHost(seam).capabilities,
        )
        assertEquals(1, seam.registered.size)
    }

    @Test
    fun sessionCloseKeepsProcessSource() {
        val seam = FakeSeam()
        val broker = AndroidProcessMemoryPressure()
        // Deux hosts (deux attaches) : la source process est installée à la première
        // session et partagée — UNE seule registration.
        val hostA = broker.newHost(seam)
        val hostB = broker.newHost(seam)
        assertEquals(1, seam.registered.size)
        val receivedByHostA = mutableListOf<MemoryPressureLevel>()
        val receivedByHostB = mutableListOf<MemoryPressureLevel>()
        hostA.wire { level -> receivedByHostA += level }
        hostB.wire { level -> receivedByHostB += level }

        // Une session qui se ferme ne passe par AUCUN appel broker : la source reste
        // enregistrée, les deux hosts continuent de recevoir.
        assertEquals(0, seam.unregistered.size)
        seam.trim(5)
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), receivedByHostA)
        assertEquals(listOf(MemoryPressureLevel.Moderate), receivedByHostB)
        assertEquals(0, seam.unregistered.size)

        // La terminaison du host A : la source process reste (le host B vit encore).
        hostA.hostTerminated()
        assertEquals(0, seam.unregistered.size)
        seam.trim(15)
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), receivedByHostA)
        assertEquals(
            listOf(MemoryPressureLevel.Moderate, MemoryPressureLevel.Critical),
            receivedByHostB,
        )

        // La terminaison du DERNIER host ferme la source : la déregistration, une fois.
        hostB.hostTerminated()
        assertEquals(1, seam.unregistered.size)
        assertEquals(seam.registered.single(), seam.unregistered.single())
        // Et un signal tardif ne relaie plus rien — les reçus ne bougent plus.
        seam.trim(5)
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Moderate), receivedByHostA)
        assertEquals(
            listOf(MemoryPressureLevel.Moderate, MemoryPressureLevel.Critical),
            receivedByHostB,
        )
    }

    @Test
    fun callbackRelayedOffItsStack() {
        val seam = FakeSeam()
        val source = AndroidMemoryPressureSource(seam::register, seam::unregister, seam::post)
        val relayed = mutableListOf<MemoryPressureLevel>()
        assertTrue(source.install { level -> relayed += level })

        source.onTrimMemory(10)
        // Le relais n'a PAS couru dans la pile de `onTrimMemory` — il attend le post.
        assertTrue(relayed.isEmpty())
        // Le drain (le « plus tard » du looper) livre le niveau mappé, hors stack.
        seam.drain()
        assertEquals(listOf(MemoryPressureLevel.Critical), relayed)

        // Un signal sans relais d'install ne publie rien ; un signal après close non plus.
        val bare = AndroidMemoryPressureSource(FakeSeam()::register, FakeSeam()::unregister, FakeSeam()::post)
        bare.onTrimMemory(5)
        val closedSeam = FakeSeam()
        val relayedByClosed = mutableListOf<MemoryPressureLevel>()
        val closed = AndroidMemoryPressureSource(
            closedSeam::register,
            closedSeam::unregister,
            closedSeam::post,
        )
        closed.install { level -> relayedByClosed += level }
        closed.close()
        // Un trim livré à l'instance callbacks D'UNE source fermée ne relaie rien.
        closedSeam.registered.single().onTrimMemory(5)
        closedSeam.drain()
        assertTrue(relayedByClosed.isEmpty())
        seam.trim(15)
        seam.drain()
        // Deux trims postés, deux livraisons — le trim(15) d'après-close (c'est la source
        // `closed` qui est fermée, pas celle-ci) traverse toujours.
        assertEquals(listOf(MemoryPressureLevel.Critical, MemoryPressureLevel.Critical), relayed)
    }
}
