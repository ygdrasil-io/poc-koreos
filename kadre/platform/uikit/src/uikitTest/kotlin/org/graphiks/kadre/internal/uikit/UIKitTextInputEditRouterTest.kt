package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les décisions d'édition du port texte ([UIKitTextInputEditRouter]) — le noyau PUR des deux
 * hardenings du fix round 1, épinglés SANS appareil :
 *
 * - **l'écho ne consomme jamais un candidat** (`EchoDrop`) : un `shouldChange` sans
 *   `.editingChanged` de suite laisse un candidat qui traîne ; l'écho d'un write-back qui le
 *   consommerait publierait un `Replace` fantôme à la révision d'avant write-back ;
 * - **le commit IME resynchronise** (`CompositionCommit`) : la sortie de marqué du champ,
 *   exactement une fois, est le fait que l'ombre doit adopter — sans elle la plage honnête du
 *   prochain appui se résoudrait nulle ou fausse contre l'ombre restée en retard.
 *
 * Le CÂBLAGE de ces décisions aux lectures réelles du champ (`writeBackInFlight`,
 * `isMarkingText`) et leur EXÉCUTION (jet du candidat, resynchronisation d'ombre, publication)
 * ne sont pas épinglables ici — le receiver n'y est pas construit — : preuve driver Task 3
 * (documenté au rapport, §fix).
 */
class UIKitTextInputEditRouterTest {

    private fun router() = UIKitTextInputEditRouter()

    private fun decide(
        router: UIKitTextInputEditRouter,
        writeBackInFlight: Boolean = false,
        fieldMarked: Boolean = false,
        pendingPresent: Boolean = false,
    ) = router.onTextDidChange(writeBackInFlight, fieldMarked, pendingPresent)

    // MARK: - shouldRecordCandidate (garde du shouldChange)

    @Test
    fun shouldRecordCandidateMirrorsTheFieldMarkedState() {
        val router = router()
        assertTrue(router.shouldRecordCandidate(fieldMarked = false),
            "hors composition, le candidat honnête est enregistré")
        assertFalse(router.shouldRecordCandidate(fieldMarked = true),
            "pendant une composition vivante DANS LE CHAMP, le fait est à la composition")
    }

    // MARK: - les faits ordinaires

    @Test
    fun honestUserEditCommitsThePending() {
        assertEquals(UIKitTextInputEditDecision.CommitPending,
            decide(router(), pendingPresent = true))
    }

    @Test
    fun unattributedChangeProducesNothing() {
        assertEquals(UIKitTextInputEditDecision.Nothing, decide(router()))
    }

    // MARK: - Fix 1 : l'écho ne consomme jamais un candidat

    @Test
    fun echoDropsThePendingInsteadOfConsumingIt() {
        assertEquals(UIKitTextInputEditDecision.EchoDrop,
            decide(router(), writeBackInFlight = true, pendingPresent = true),
            "l'écho d'un write-back jette le candidat qui traîne — jamais un Replace fantôme")
    }

    @Test
    fun echoWinsOverEverything() {
        assertEquals(UIKitTextInputEditDecision.EchoDrop,
            decide(router(), writeBackInFlight = true, fieldMarked = true, pendingPresent = true),
            "le write-back possède le champ : l'écho gagne sur le marqué et sur le candidat")
    }

    @Test
    fun afterAnEchoTheNextHonestEditCommitsNormally() {
        val router = router()
        assertEquals(UIKitTextInputEditDecision.EchoDrop,
            decide(router, writeBackInFlight = true, pendingPresent = true))
        assertEquals(UIKitTextInputEditDecision.CommitPending,
            decide(router, pendingPresent = true),
            "le candidat jeté, le routeur revient au chemin ordinaire — le port n'a rien consommé")
    }

    // MARK: - Fix 2 : le commit IME resynchronise, exactement une fois

    @Test
    fun compositionAliveDropsAndRemembersTheMarking() {
        val router = router()
        assertEquals(UIKitTextInputEditDecision.CompositionDrop,
            decide(router, fieldMarked = true, pendingPresent = true))
        assertTrue(router.fieldWasMarking, "le marqué vivant est retenu pour la transition")
    }

    @Test
    fun theMarkedToNullTransitionCommitsTheCompositionExactlyOnce() {
        val router = router()
        assertEquals(UIKitTextInputEditDecision.CompositionDrop,
            decide(router, fieldMarked = true))
        assertEquals(UIKitTextInputEditDecision.CompositionCommit,
            decide(router, fieldMarked = false, pendingPresent = true),
            "le marqué passe à nul : le commit IME — resynchronisation de l'ombre, jamais une observation")
        assertFalse(router.fieldWasMarking, "la mémoire est consommée")
        assertEquals(UIKitTextInputEditDecision.CommitPending,
            decide(router, fieldMarked = false, pendingPresent = true),
            "le commit IME ne tire qu'UNE fois : le changement suivant redevient une édition ordinaire")
    }

    @Test
    fun echoResetsTheMarkingMemoryTheWriteBackConsumesTheTransition() {
        val router = router()
        assertEquals(UIKitTextInputEditDecision.CompositionDrop,
            decide(router, fieldMarked = true))
        assertEquals(UIKitTextInputEditDecision.EchoDrop,
            decide(router, writeBackInFlight = true),
            "le write-back réécrit le champ (le marqué tombe avec lui) et l'ombre avec : la transition est à lui")
        assertFalse(router.fieldWasMarking)
        assertEquals(UIKitTextInputEditDecision.Nothing,
            decide(router),
            "pas de commit IME fantôme après un write-back — l'ombre a déjà été reconciliée par lui")
    }
}
