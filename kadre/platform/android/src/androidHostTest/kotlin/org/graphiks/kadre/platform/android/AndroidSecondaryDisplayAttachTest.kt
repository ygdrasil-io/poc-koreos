package org.graphiks.kadre.platform.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.policy.KadrePolicies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La couture d'attach présentation (Task 2, phase 11) : l'échelle d'admission pure, la
 * réservation (contentView identité ET displayId valeur, JAMAIS l'Activity), et la branche de
 * composition [`AndroidHostSession.attachSecondaryDisplay`] contre le même outillage que les
 * tests d'admission existants — des requêtes pures, un registre frais, et le seam Task 1
 * doublé par un fake pilotable. AUCUN type SDK n'est construit : la fabrique de source, le
 * port display et l'installation du binding lifecycle sont les étapes injectées (la seule
 * couture qui touche le SDK en production, le modèle de `installBindingAndAttach`).
 *
 * Les cas du plan :
 * 1. `contentView deja attache est refuse sans reserver` — le contentView doit être LIBRE,
 *    Kadre le monte dans la présentation ;
 * 2. `deux attaches sur le meme displayId` — la seconde `AlreadyInUse(Host)`, la première
 *    session intacte ;
 * 3. `memes cles view ET display` — chaque clé seule suffit au refus ;
 * 4. `coexistence` — session principale (réservation Activity) + session présentation sur la
 *    MÊME Activity : la forme présentation ne revendique jamais l'identité de l'Activity ;
 * 5. `retrait display` — la surface se ferme, la session devient TERMINALE, les réservations
 *    sont libérées (le même chemin qu'un show échoué) ;
 * 6. `stop start host` — un hide et un show PAR cycle, jamais deux médias ;
 * 7. `rollback de fabrique` — la présentation est fermée, les réservations libérées.
 */
class AndroidSecondaryDisplayAttachTest {

    // ------------------------------------------------------------------ échelle pure

    @Test
    fun `echelle d'admission presentation - champs et familles exacts`() {
        // La violation de thread reste une erreur de programmation (décision de phase 0, le
        // rung existant) — pas un KadreResult.
        assertFailsWithIllegalState {
            evaluateAndroidPresentationAttach(presentationRequest(isMainThread = false), AndroidViewOwnership())
        }
        assertNull(evaluateAndroidPresentationAttach(presentationRequest(), AndroidViewOwnership()))
        assertEquals(
            KadreFailure.InvalidRequest("parentScope"),
            evaluateAndroidPresentationAttach(
                presentationRequest(jobPresent = false), AndroidViewOwnership(),
            )!!.failure,
        )
        assertEquals(
            KadreFailure.ParentScopeCancelled,
            evaluateAndroidPresentationAttach(
                presentationRequest(jobActive = false), AndroidViewOwnership(),
            )!!.failure,
        )
        assertEquals(
            KadreFailure.InvalidRequest("contentView"),
            evaluateAndroidPresentationAttach(
                presentationRequest(isViewAttached = true), AndroidViewOwnership(),
            )!!.failure,
        )
        assertEquals(
            KadreFailure.InvalidRequest("presentationDisplay"),
            evaluateAndroidPresentationAttach(
                presentationRequest(isDisplayResolvable = false), AndroidViewOwnership(),
            )!!.failure,
        )
        assertEquals(
            KadreFailure.Closed(KadreResourceKind.Host),
            evaluateAndroidPresentationAttach(
                presentationRequest(ownerState = 0), AndroidViewOwnership(), // DESTROYED
            )!!.failure,
        )
    }

    @Test
    fun `conflits d'identites - AlreadyInUse Host dans les deux directions`() {
        val ownership = AndroidViewOwnership()
        assertNull(evaluateAndroidPresentationAttach(presentationRequest(), ownership))
        assertTrue(ownership.reservePresentation("view-1", 7L))
        // Chaque clé seule suffit : la View (identité)...
        assertEquals(
            KadreFailure.AlreadyInUse(KadreResourceKind.Host),
            evaluateAndroidPresentationAttach(
                presentationRequest(view = "view-1", displayKey = 8L), ownership,
            )!!.failure,
        )
        // ...et le displayId (valeur).
        assertEquals(
            KadreFailure.AlreadyInUse(KadreResourceKind.Host),
            evaluateAndroidPresentationAttach(
                presentationRequest(view = "view-2", displayKey = 7L), ownership,
            )!!.failure,
        )
        // Et réciproquement : le registre de la forme Activity refuse une View déjà réservée
        // par la présentation (reserve-both-or-nothing, rien n'est transféré).
        assertFalse(ownership.reserve("activity-1", "view-1"))
    }

    // ------------------------------------------------------------------ registre pur

    @Test
    fun `reservation presentation - contentView ET displayId ou rien`() {
        val ownership = AndroidViewOwnership()
        assertTrue(ownership.reservePresentation("view-1", 7L))
        // L'un ou l'autre déjà pris : refus, et RIEN de réservé pour la demande perdante.
        assertFalse(ownership.reservePresentation("view-1", 8L))
        assertFalse(ownership.reservePresentation("view-2", 7L))
        assertFalse(ownership.isViewClaimed("view-2"))
        assertFalse(ownership.isDisplayClaimed(8L))
        // L'Activity n'est JAMAIS revendiquée par la forme présentation : la forme Activity
        // passe pendant que la présentation vit (la coexistence tient au registre).
        assertTrue(ownership.reserve("activity-1", "other-view"))
        assertFalse(ownership.isEmpty())
        // Libération : les deux clés rendues, l'Activity de l'autre forme intacte.
        ownership.releasePresentation("view-1", 7L)
        assertFalse(ownership.isViewClaimed("view-1"))
        assertFalse(ownership.isDisplayClaimed(7L))
        assertTrue(ownership.isActivityClaimed("activity-1"))
        // Re-réservation possible après libération.
        assertTrue(ownership.reservePresentation("view-1", 7L))
    }

    @Test
    fun `reservation vivante presentation - liberations idempotentes`() {
        val ownership = AndroidViewOwnership()
        val reservation = AndroidPresentationReservation.acquire(ownership, "view-1", 7L)!!
        assertTrue(reservation.isStillHeld())
        reservation.release()
        reservation.release() // idempotente
        assertFalse(reservation.isStillHeld())
        assertTrue(ownership.isEmpty())
        assertTrue(AndroidPresentationReservation.acquire(ownership, "view-1", 7L) != null)
    }

    // ------------------------------------------------------------------ composition

    @Test
    fun `contentView deja attache est refuse sans reserver`() {
        val host = SecondaryDisplayHost()
        val outcome = host.attach(isViewAttached = true)
        assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("contentView")), outcome)
        assertTrue(host.registry.isEmpty()) // RIEN réservé
        assertNull(host.source) // la fabrique de source n'a jamais couru
    }

    @Test
    fun `deux attaches sur le meme displayId - la seconde refusee la premiere intacte`() {
        val host = SecondaryDisplayHost()
        val first = host.attach(displayKey = 7L, view = Any())
        val firstSession = first.session()
        val second = host.attach(displayKey = 7L, view = Any())
        assertEquals(KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)), second)
        assertTrue(firstSession.isRunning())
    }

    @Test
    fun `memes cles view ET display - refuse AlreadyInUse les deux fois`() {
        val host = SecondaryDisplayHost()
        val view = Any()
        host.attach(displayKey = 7L, view = view).session()
        // Les deux clés ensemble...
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            host.attach(displayKey = 7L, view = view),
        )
        // ...puis chaque clé seule : la View (identité)...
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            host.attach(displayKey = 8L, view = view),
        )
        // ...et le displayId (valeur).
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            host.attach(displayKey = 7L, view = Any()),
        )
    }

    @Test
    fun `coexistence - session principale et session presentation sur la meme activity`() {
        val host = SecondaryDisplayHost()
        // La forme Activity existante réserve (Activity, mainView) — reserve-both-or-nothing.
        assertTrue(host.registry.reserve("activity-1", "main-view"))
        // La session présentation sur la MÊME Activity, vue et écran libres : admise.
        val cast = host.attach(displayKey = 7L, view = Any())
        val castSession = cast.session()
        assertTrue(castSession.isRunning())
        // La réservation de la forme principale est intacte — jamais touchée.
        assertTrue(host.registry.isActivityClaimed("activity-1"))
        assertTrue(host.registry.isViewClaimed("main-view"))
        // La terminaison de la session présentation libère SES clés seules.
        castSession.close()
        assertTrue(host.registry.isActivityClaimed("activity-1"))
        assertTrue(host.registry.isViewClaimed("main-view"))
        assertFalse(host.registry.isDisplayClaimed(7L))
    }

    @Test
    fun `retrait display ferme la surface et termine la session`() {
        val host = SecondaryDisplayHost()
        val session = host.attach(displayKey = 7L, view = Any()).session()
        host.source!!.simulateDisplayRemoved()
        assertEquals(AndroidPresentationState.Removed(7L), host.source!!.state.value)
        assertFalse(session.isRunning()) // terminal, pas de résurrection
        assertTrue(host.registry.isEmpty()) // réservations libérées
    }

    @Test
    fun `show en echec - le meme chemin que le retrait d'ecran`() {
        val host = SecondaryDisplayHost()
        val session = host.attach(displayKey = 7L, view = Any()).session()
        host.source!!.showFails = true
        // Un cycle complet : le START suivant montre — l'écran est parti entre-temps.
        host.simulateLifecycle(AndroidHostEvent.LifecycleStopped)
        host.simulateLifecycle(AndroidHostEvent.LifecycleStarted)
        assertFalse(session.isRunning())
        assertTrue(host.registry.isEmpty())
    }

    @Test
    fun `stop start host - hide show sans seconde presentation`() {
        val host = SecondaryDisplayHost()
        val session = host.attach(displayKey = 7L, view = Any()).session()
        // Le premier show est LEVEL-TRIGGERED : l'hôte est RESUMED à l'attach, le média se
        // montre immédiatement (ruling de la phase — attacher depuis une Activity RESUMED est
        // le cas nominal du Tour, le média n'attendrait sinon aucun START).
        assertEquals(1, host.source!!.showCount)
        assertEquals(AndroidPresentationState.Shown(7L), host.source!!.state.value)
        host.simulateLifecycle(AndroidHostEvent.LifecycleStopped)
        host.simulateLifecycle(AndroidHostEvent.LifecycleStarted)
        host.simulateLifecycle(AndroidHostEvent.LifecycleStopped)
        host.simulateLifecycle(AndroidHostEvent.LifecycleStarted)
        // Le show initial plus deux cycles complets : un hide et un show PAR START/STOP,
        // jamais deux médias.
        assertEquals(3, host.source!!.showCount)
        assertEquals(2, host.source!!.hideCount)
        assertEquals(AndroidPresentationState.Shown(7L), host.source!!.state.value)
        assertTrue(session.isRunning())
    }

    @Test
    fun `hote non started a l'attach - le media attend le premier START`() {
        val host = SecondaryDisplayHost()
        val session = host.attach(displayKey = 7L, view = Any(), ownerState = 2).session() // CREATED
        assertEquals(0, host.source!!.showCount) // rien avant le premier START
        host.simulateLifecycle(AndroidHostEvent.LifecycleStarted)
        assertEquals(1, host.source!!.showCount)
        assertEquals(AndroidPresentationState.Shown(7L), host.source!!.state.value)
        assertTrue(session.isRunning())
    }

    @Test
    fun `fabrique de source lancante - reservation rendue puis propagation telle quelle`() {
        val host = SecondaryDisplayHost()
        val boom = IllegalStateException("source factory failed")
        val thrown = runCatching {
            host.attach(displayKey = 7L, view = Any(), sourceFactoryImpl = { throw boom })
        }.exceptionOrNull()
        assertTrue(thrown === boom) // TEL QUEL, jamais reconverti en KadreResult
        assertTrue(host.registry.isEmpty()) // AUCUNE clé tenue — l'écran reste attachable
    }

    @Test
    fun `rollback de fabrique - presentation fermee et reservations liberees`() {
        val host = SecondaryDisplayHost()
        val outcome = host.attach(
            displayKey = 7L,
            view = Any(),
            displayPortFactory = { throw IllegalStateException("components factory failed") },
        )
        assertTrue(outcome is KadreResult.Failure)
        assertTrue(host.registry.isEmpty())
        assertEquals(1, host.source!!.dismissCount) // la présentation est fermée une fois
    }

    @Test
    fun `etape d'installation lancante - rollback puis propagation telle quelle`() {
        val host = SecondaryDisplayHost()
        val boom = IllegalStateException("install failed")
        val thrown = runCatching {
            host.attach(displayKey = 7L, view = Any(), installHostLifecycle = { throw boom })
        }.exceptionOrNull()
        assertTrue(thrown === boom) // TEL QUEL, jamais reconverti en KadreResult
        assertTrue(host.registry.isEmpty())
        assertEquals(1, host.source!!.dismissCount)
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Le fake du seam Task 1 — pilote l'état du média sans android.jar : show/hide comptés à
     * l'interface (ce que la couture appelle), le retrait simulé publie l'état FINAL.
     */
    private class FakePresentationSource(
        override val facts: AndroidPresentationFacts,
    ) : AndroidPresentationSource {
        private val mutableState = MutableStateFlow<AndroidPresentationState>(AndroidPresentationState.Hidden)
        override val state: StateFlow<AndroidPresentationState> = mutableState.asStateFlow()

        var showCount = 0
            private set
        var hideCount = 0
            private set
        var dismissCount = 0
            private set
        var showFails = false

        override fun show(): KadreResult<Unit> {
            showCount++
            if (showFails || mutableState.value is AndroidPresentationState.Removed) {
                return KadreResult.Failure(
                    KadreFailure.PlatformFailure(KadrePlatform.Android, "presentation", "display-removed"),
                )
            }
            mutableState.value = AndroidPresentationState.Shown(facts.displayKey)
            return KadreResult.Success(Unit)
        }

        override fun hide() {
            hideCount++
            mutableState.value = AndroidPresentationState.Hidden
        }

        override fun windowToken(): Any? = null

        fun simulateDisplayRemoved() {
            dismissCount++
            mutableState.value = AndroidPresentationState.Removed(facts.displayKey)
        }

        override fun close() {
            dismissCount++
        }
    }

    /**
     * Le host de test : un registre frais, un scope Unconfined (les coroutines de session
     * tournent sur le thread du test — les terminaisons sont synchrones), et les trois étapes
     * injectées de la couture : fabrique de source, port display, installation du binding.
     */
    private class SecondaryDisplayHost {
        val registry = AndroidViewOwnership()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var source: FakePresentationSource? = null
            private set
        var presentationLifecycle: AndroidPresentationLifecycle? = null
            private set

        fun attach(
            displayKey: Long = 7L,
            view: Any = Any(),
            applicationFactory: KadreApplicationFactory = hangingApplication(),
            isViewAttached: Boolean = false,
            isDisplayResolvable: Boolean = true,
            ownerState: Int = 4, // RESUMED
            jobActive: Boolean = true,
            displayPortFactory: () -> DisplayPort? = { null },
            installHostLifecycle: (AndroidPresentationLifecycle) -> Unit = { presentationLifecycle = it },
            sourceFactoryImpl: (FakePresentationSource) -> AndroidPresentationSource = {
                source = it
                it
            },
        ): KadreResult<KadreSession> {
            val fake = FakePresentationSource(
                AndroidPresentationFacts(displayKey = displayKey, realWidth = 1920, realHeight = 1080, scaleFactor = 2.0),
            )
            return AndroidHostSession.attachSecondaryDisplay(
                request = AndroidPresentationAttachRequest(
                    isMainThread = true,
                    contentView = view,
                    isContentViewAttachedToWindow = isViewAttached,
                    displayKey = displayKey,
                    isDisplayResolvable = isDisplayResolvable,
                    ownerState = ownerState,
                    parentScopeJob = scope.coroutineContext[Job],
                    parentScopeJobIsActive = jobActive,
                ),
                parentScope = scope,
                applicationFactory = applicationFactory,
                policy = KadrePolicies.Default,
                sourceFactory = { sourceFactoryImpl(fake) },
                installHostLifecycle = installHostLifecycle,
                registry = registry,
                displayPortFactory = displayPortFactory,
            )
        }

        fun simulateLifecycle(event: AndroidHostEvent) {
            presentationLifecycle!!.dispatch(event)
        }
    }

    private fun presentationRequest(
        isMainThread: Boolean = true,
        view: Any = "content-view",
        isViewAttached: Boolean = false,
        displayKey: Long = 7L,
        isDisplayResolvable: Boolean = true,
        ownerState: Int = 4, // RESUMED
        jobPresent: Boolean = true,
        jobActive: Boolean = true,
    ) = AndroidPresentationAttachRequest(
        isMainThread = isMainThread,
        contentView = view,
        isContentViewAttachedToWindow = isViewAttached,
        displayKey = displayKey,
        isDisplayResolvable = isDisplayResolvable,
        ownerState = ownerState,
        parentScopeJob = if (jobPresent) Any() else null,
        parentScopeJobIsActive = jobActive,
    )

    private fun KadreResult<KadreSession>.session(): KadreSession =
        assertIs<KadreResult.Success<KadreSession>>(this).value

    private fun KadreSession.isRunning(): Boolean = state.value is SessionState.Running

    private fun assertFailsWithIllegalState(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalStateException)
    }
}

/** L'application qui ne termine pas — la session reste `Running` pour la vie du test. */
private fun hangingApplication(): KadreApplicationFactory = KadreApplicationFactory {
    KadreApplication { awaitCancellation() }
}
