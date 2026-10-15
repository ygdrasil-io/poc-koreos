package org.graphiks.kadre.platform.android

import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.diagnostics.KadreResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * La MACHINE d'état du seam présentation (Task 1, phase 11) contre un fake du seam
 * [AndroidPresentationSource] — JAMAIS le SDK (le stub android.jar lance à la construction des
 * types Presentation ; c'est pourquoi la machine [`AndroidPresentationStateMachine`] est
 * SDK-free et que la traduction SDK reste vérifiée sur émulateur, device tests Task 4). Le
 * fake délègue TOUT son protocole à la vraie machine — seuls les gestes sont les siens : ce
 * que ces tests prouvent est la machine, pas son double. Les quatre cas du plan :
 *
 * 1. `show fait passer Hidden a Shown et expose le jeton fenetre` — le premier show réussi
 *    publie `Shown(displayKey)` et retient le jeton fenêtre lu UNE fois, jeton opaque que la
 *    Task 3 passera à l'installateur de chaîne input ;
 * 2. `hide est idempotent et ne detruit pas le media` — STOP hôte : Hidden sans destruction
 *    (le showCount ne bouge pas — hide n'annule pas la vie du média, le show suivant
 *    réutilise le même couple (Presentation, display)) ;
 * 3. `Removed est final - aucun show ulterieur` — le retrait de l'écran (`onDisplayRemoved`)
 *    dismiss une fois et rend l'état FINAL : un show ultérieur répond la `PlatformFailure`
 *    Android du domaine présentation (la famille des autres ports), sans re-geste ;
 * 4. `close apres retrait ne re-dismiss pas` — le dismiss a déjà couru au retrait, la
 *    fermeture du seam ne le rejoue pas (compteur exactement 1).
 */
class AndroidPresentationStateMachineTest {

    /** Le double du seam SDK : la vraie machine, des gestes qui comptent — jamais de SDK. */
    private class FakePresentationSource(
        override val facts: AndroidPresentationFacts,
    ) : AndroidPresentationSource {
        private val machine = AndroidPresentationStateMachine(facts.displayKey)

        /** Le showCount observable — un show admis par la machine, un geste. */
        var showCount = 0

        /** Le dismissCount observable — retrait OU close, jamais deux fois. */
        var dismissCount = 0

        /** Le jeton que le SDK rendrait au premier show réussi — pilotable par le test. */
        var windowTokenImpl: () -> Any? = { null }

        override val state: StateFlow<AndroidPresentationState> get() = machine.state

        override fun show(): KadreResult<Unit> = machine.show(
            showSdk = { showCount += 1 },
            windowSdk = { windowTokenImpl() },
        )

        override fun hide() {
            machine.hide { }
        }

        /** Le fire `onDisplayRemoved(displayKey)` du DisplayManager, joué à la main. */
        fun simulateDisplayRemoved() {
            machine.displayRemoved { dismissCount += 1 }
        }

        override fun windowToken(): Any? = machine.windowToken()

        override fun close() {
            machine.close { dismissCount += 1 }
        }
    }

    /** Les faits d'un écran de présentation type — displayKey pilotable, le reste plausible. */
    private fun facts(
        displayKey: Long = 7L,
        realWidth: Int = 1920,
        realHeight: Int = 1080,
        scaleFactor: Double = 1.0,
    ): AndroidPresentationFacts = AndroidPresentationFacts(
        displayKey = displayKey,
        realWidth = realWidth,
        realHeight = realHeight,
        scaleFactor = scaleFactor,
    )

    private fun KadreResult<*>.assertSuccess() {
        assertIs<KadreResult.Success<*>>(this)
    }

    private fun KadreResult<*>.assertFailure() {
        assertIs<KadreResult.Failure>(this)
    }

    // -----------------------------------------------------------------------
    // Cas 1 — show : Hidden → Shown, et le jeton fenêtre retenu une fois
    // -----------------------------------------------------------------------

    @Test
    fun `show fait passer Hidden a Shown et expose le jeton fenetre`() {
        val fake = FakePresentationSource(facts(displayKey = 7L))
        val window = Any()
        fake.windowTokenImpl = { window }

        fake.show().assertSuccess()

        assertEquals(AndroidPresentationState.Shown(7L), fake.state.value)
        assertSame(window, fake.windowToken())
    }

    // -----------------------------------------------------------------------
    // Cas 2 — hide idempotent : Hidden sans destruction du média
    // -----------------------------------------------------------------------

    @Test
    fun `hide est idempotent et ne detruit pas le media`() {
        val fake = FakePresentationSource(facts(displayKey = 7L)).apply { show().assertSuccess() }

        fake.hide(); fake.hide()

        assertEquals(AndroidPresentationState.Hidden, fake.state.value)
        assertEquals(1, fake.showCount) // hide n'annule pas la vie du média
    }

    // -----------------------------------------------------------------------
    // Cas 3 — Removed est final : aucun show ultérieur
    // -----------------------------------------------------------------------

    @Test
    fun `Removed est final - aucun show ulterieur`() {
        val fake = FakePresentationSource(facts(displayKey = 7L)).apply { show().assertSuccess() }

        fake.simulateDisplayRemoved()
        val second = fake.show()

        assertEquals(AndroidPresentationState.Removed(7L), fake.state.value)
        second.assertFailure() // l'échec exact est la PlatformFailure Android du domaine présentation
        assertEquals(1, fake.showCount)
    }

    // -----------------------------------------------------------------------
    // Cas 4 — close après retrait : jamais de second dismiss
    // -----------------------------------------------------------------------

    @Test
    fun `close apres retrait ne re-dismiss pas`() {
        val fake = FakePresentationSource(facts(displayKey = 7L))
        fake.simulateDisplayRemoved()
        fake.close()
        assertEquals(1, fake.dismissCount)
    }
}
