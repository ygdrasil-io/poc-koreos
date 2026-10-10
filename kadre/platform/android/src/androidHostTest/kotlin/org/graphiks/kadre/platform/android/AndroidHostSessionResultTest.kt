package org.graphiks.kadre.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult

class AndroidHostSessionResultTest {

    @Test
    fun admissionDenialsBecomeKadreResultsWithoutSession() {
        // La couture commune : une denial de l'échelle devient Failure SANS exception ni session.
        val failure = AndroidHostSession.toFailure(AndroidAttachDenial(KadreFailure.InvalidRequest("view")))
        assertEquals(KadreFailure.InvalidRequest("view"), failure)
    }

    @Test
    fun threadViolationRemainsAProgrammingError() {
        // Décision de phase 0 maintenue : la violation de thread n'est PAS un KadreResult.
        assertTrue(runCatching {
            evaluateAndroidAttach(
                AndroidAttachRequest(
                    form = AndroidAttachForm.View, isMainThread = false, view = Any(),
                    isViewAttachedToWindow = true, viewRootIdentity = null, activityIdentity = null,
                    decorRootIdentity = null, ownerState = 4, parentScopeJob = Any(),
                    parentScopeJobIsActive = true,
                ),
                AndroidViewOwnership(),
            )
        }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun throwingInstallStepRollsBackAndRethrowsRaw() {
        // Le garde post-réservation (`AndroidHostSession.installBindingAndAttach`) : un
        // throwable SDK après l'acquisition de la réservation — ici l'étape d'installation
        // injectée, la SEULE du chemin qui touche le SDK Android (le hook minimal honnête :
        // la même fonction que la production, seule l'étape `binding.install(owner, view)`
        // est remplacée par une version lançante, car aucune View réelle n'est constructible
        // hors émulateur) — se propage TEL QUEL, jamais reconverti en `KadreResult` (la
        // taxonomie n'admet pas cette failure), mais APRÈS un rollback intégral : le registre
        // d'ownership est propre et un re-reserve réussit.
        val ownership = AndroidViewOwnership()
        val reservation = AndroidAttachReservation.acquire(ownership, "activity-1", "view-1")!!
        val binding = hostBinding()
        val boom = IllegalStateException("SDK addObserver failed")
        var controllerReached = false
        val thrown = runCatching {
            AndroidHostSession.installBindingAndAttach(
                reservation = reservation,
                binding = binding,
                installBinding = { throw boom },
                attachController = {
                    controllerReached = true
                    throw AssertionError("attachController must not run after a failed install")
                },
            )
        }.exceptionOrNull()
        assertTrue(thrown === boom)
        assertFalse(controllerReached)
        // Ownership propre : plus aucune identité retenue, le re-reserve de la même paire réussit.
        assertFalse(ownership.isActivityClaimed("activity-1"))
        assertFalse(ownership.isViewClaimed("view-1"))
        assertTrue(AndroidAttachReservation.acquire(ownership, "activity-1", "view-1") != null)
        // Zéro résidu de binding.
        assertTrue(binding.installedCallbacks.isEmpty())
    }

    @Test
    fun controllerRejectionRollsBackAndReturnsFailure() {
        // Le rejet DU CONTRÔLEUR, lui, reste un `KadreResult` — rollback intégral puis
        // `Failure(reason)` sans exception (le chemin nominal d'échec de l'étape 5).
        val ownership = AndroidViewOwnership()
        val reservation = AndroidAttachReservation.acquire(ownership, "activity-1", "view-1")!!
        val binding = hostBinding()
        val result = AndroidHostSession.installBindingAndAttach(
            reservation = reservation,
            binding = binding,
            installBinding = { },
            attachController = {
                KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
            },
        )
        assertEquals(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host)), result)
        assertFalse(ownership.isActivityClaimed("activity-1"))
        assertFalse(ownership.isViewClaimed("view-1"))
        assertTrue(AndroidAttachReservation.acquire(ownership, "activity-1", "view-1") != null)
    }

    /** Un binding de test : la construction ne touche aucun type SDK Android. */
    private fun hostBinding() = AndroidLifecycleBinding(
        reducer = AndroidLifecycleReducer(
            AndroidHostSnapshot(attachment = true, foreground = true, active = true),
        ),
        onDecision = {},
        onTerminal = {},
    )
}
