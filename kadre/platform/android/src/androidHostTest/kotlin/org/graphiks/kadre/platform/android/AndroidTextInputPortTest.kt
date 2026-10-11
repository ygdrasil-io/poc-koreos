package org.graphiks.kadre.platform.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputState
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.RuntimeTextInputSession
import org.graphiks.kadre.internal.runtime.TextInputCursorCommand
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalRect
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Le pont IME → `TextInputObservation` (Task 1, phase 5 Android), prouvé contre la VRAIE
 * [RuntimeTextInputSession] — le miroir exact de `WebTextInputSurfaceTest`, avec l'écart de
 * capability que le plan épinglé : le port publie `Unsupported(TextInput)` TOUJOURS (le SDK
 * exige une sous-classe de View que kadre ne possède pas — le canal d'intégration conforme
 * n'existe pas sur la surface d'attach actuelle, le fait central de la phase 5), donc le
 * réducteur refuse `openTextInput` AVANT d'appeler `open` (RuntimeSurfaceInput:124-126).
 *
 * Les six premiers cas branchent donc la session EXACTEMENT comme le runtime la construit
 * dans `openTextInput` : `port.open(command)` rend le owner, la vraie session reçoit ce
 * owner et la callback d'observation de la commande — les observations IME coulent par
 * `command.onObservation` dans la machine réelle (révisions, rebasage, suspension, stamps).
 * Le septième cas prouve le refus on-row par la voie de PRODUCTION : l'
 * [AndroidHostSurface.installSessionConfiguration] réel — désormais construit autour du
 * [AndroidTextInputPort] — et `surface.input.openTextInput` répond le refus du port sans
 * jamais l'ouvrir.
 *
 * Les cas, bullets du gate :
 * 1. `commitReplacesAtSelectionWithUtf16Indices` — commit avec paire surrogate, offsets UTF-16 exacts,
 *    le document venant de l'app par `updateSurroundingText` (rond-trip) ;
 * 2. `multiStepCompositionRebases` — composition en plusieurs pas, l'app suit, le rebasage porte,
 *    la fin à chaîne finale DIFFÉRENTE du composé livre le Replace puis le terminal, la fin ÉGALE
 *    (la forme commune) le terminal seul, la sélection n'est jamais fabriquée (miroir web :327-374) ;
 * 3. `staleRevisionIsRejected` — révision périmée → `StaleRevision`, même révision + texte
 *    différent → `InvalidRequest("text")` ;
 * 4. `focusLossSuspendsWithoutDestroyingComposition` — `suspend()`/`resume()` sans détruire la
 *    composition (miroir DESIGN:1296) ;
 * 5. `deleteSurroundingMapsToComputedReplace` — le delete calculé depuis le document + la sélection ;
 * 6. `finishCompositionWithoutCompositionIsSilent` — aucun événement fabriqué ;
 * 7. `capabilityStaysUnsupportedUntilNormativeDecision` — la capability reste Unsupported, le
 *    refus on-row tient, le pont reste sans chemin d'activation public.
 *
 * Aucun type SDK constructible n'est instancié — l'implémentation concrète de la
 * `BaseInputConnection` vit côté SDK et reste délibérément non écrite (intestable en JVM
 * hôte) : le mapper pur est le livrable, les points d'entrée IME du owner sont la forme
 * transposée des overrides kadre-old.
 */
class AndroidTextInputPortTest {

    // -----------------------------------------------------------------------
    // Cas 1 — commit : Replace aux offsets UTF-16 exacts, document venu de l'app
    // -----------------------------------------------------------------------

    @Test
    fun commitReplacesAtSelectionWithUtf16Indices() = runBlocking {
        val harness = TextHarness(
            this,
            TextInputConfig(
                surroundingText = "a😀b",
                selection = TextRange(4, 4),
                documentRevision = TextDocumentRevision(7),
            ),
        )
        val events = harness.collect()

        // Le rond-trip : le document que le port mapperait vient de l'application,
        // accepté par la vraie session à la révision supérieure.
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("a😀bc", TextRange(4, 4), TextDocumentRevision(8)),
        )

        // "héllo😀" = 5 + 2 = 7 unités UTF-16 : le commit entre à la sélection [4,4).
        harness.owner.commitText("héllo😀")
        harness.settle()

        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.Replace(TextRange(4, 4), "héllo😀", TextDocumentRevision(8), events[0].stamp),
            ),
            events,
            "a commit is the Replace of the selection it lands in, at UTF-16 offsets of the accepted document",
        )

        // Les offsets qui suivent comptent la paire surrogate pour DEUX unités UTF-16 :
        // 4 + 7 = 11 — un comptage en points de code donnerait 10. L'application suit le
        // commit précédent en l'APPLIQUANT — "a😀bc" avec "héllo😀" substitué à [4,4) fait
        // "a😀bhéllo😀c", le caret à 11, devant le "c" resté en place (l'observation ne
        // déplace pas le document de la session : c'est l'acceptation qui le fait), et le
        // second commit est marqué de la révision acceptée, sa range dans CE document.
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("a😀bhéllo😀c", TextRange(11, 11), TextDocumentRevision(9)),
        )
        harness.owner.commitText("!")
        harness.settle()

        assertEquals(
            TextInputEvent.Replace(TextRange(11, 11), "!", TextDocumentRevision(9), events[1].stamp),
            events[1],
            "the second commit proves the UTF-16 arithmetic: the astral character counted for two units",
        )
        assertTrue(
            events.none { it is TextInputEvent.SelectionChanged },
            "a commit is never a SelectionChanged (DESIGN §10.3)",
        )
        harness.close()
    }

    // -----------------------------------------------------------------------
    // Cas 2 — composition en plusieurs pas, rebasage, fin : Replace + terminal
    // à chaîne différente, terminal seul à chaîne égale (le miroir web :327-374)
    // -----------------------------------------------------------------------

    @Test
    fun multiStepCompositionRebases() = runBlocking {
        val harness = TextHarness(
            this,
            TextInputConfig(
                surroundingText = "abc",
                selection = TextRange(3, 3),
                documentRevision = TextDocumentRevision(5),
            ),
        )
        val events = harness.collect()

        // Le départ : la substitution vide à la sélection (le miroir web :327-333).
        harness.owner.setComposingText("", 0)
        harness.settle()
        assertEquals(
            TextInputEvent.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), TextDocumentRevision(5), events[0].stamp),
            events[0],
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), TextRange(3, 3)),
            harness.session.state.value,
        )

        // Le premier pas : le span précédent — la sélection vide — remplacé par "compo" ;
        // la sélection dans le texte composé est sa fin, jamais une sélection de document.
        harness.owner.setComposingText("compo", 5)
        harness.settle()
        assertEquals(
            TextInputEvent.CompositionChanged(TextRange(3, 3), "compo", TextRange(5, 5), TextDocumentRevision(5), events[1].stamp),
            events[1],
        )

        // L'application suit : l'instantané accepté est la substitution du composé dans son
        // span — la vraie session rebasse la composition sur le document accepté.
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("abccompo", TextRange(8, 8), TextDocumentRevision(6)),
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(6), TextRange(3, 8)),
            harness.session.state.value,
            "the accepted snapshot rebased the composition onto the application's document",
        )

        // Le pas suivant part du span rebasé : [3,8), pas [3,3).
        harness.owner.setComposingText("composé", 7)
        harness.settle()
        assertEquals(
            TextInputEvent.CompositionChanged(TextRange(3, 8), "composé", TextRange(7, 7), TextDocumentRevision(6), events[2].stamp),
            events[2],
        )
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("abccomposé", TextRange(10, 10), TextDocumentRevision(7)),
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(7), TextRange(3, 10)),
            harness.session.state.value,
        )

        // La fin que le commit porte — le miroir web :355-374. L'IME commite une chaîne
        // finale qui DIFFÈRE du composé (la correction du suggestif : "composé" devient
        // "composait") : le Replace du span de la composition, puis le terminal clôt —
        // range et sélection tous deux nuls.
        harness.owner.commitText("composait")
        harness.settle()

        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.Replace(TextRange(3, 10), "composait", TextDocumentRevision(7), events[3].stamp),
                TextInputEvent.CompositionChanged(null, "", null, TextDocumentRevision(7), events[4].stamp),
            ),
            events.subList(3, events.size),
            "a final string the updates never carried is the Replace it is, then the terminal ends the composition",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(7), null),
            harness.session.state.value,
            "a finished composition leaves no composing range behind",
        )

        // La forme commune : l'application suit d'abord la correction, une nouvelle
        // composition s'ouvre et l'IME commite la chaîne MÊME qu'il a composée — le
        // composé est déjà dans le document, le commit ne change rien et le terminal
        // seul termine (aucun Replace fabriqué, le miroir de la fin web égale).
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("abccomposait", TextRange(12, 12), TextDocumentRevision(8)),
        )
        harness.owner.setComposingText("bon", 3)
        harness.settle()
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("abccomposaitbon", TextRange(15, 15), TextDocumentRevision(9)),
        )
        harness.owner.commitText("bon")
        harness.settle()

        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.CompositionChanged(TextRange(12, 12), "bon", TextRange(3, 3), TextDocumentRevision(8), events[5].stamp),
                TextInputEvent.CompositionChanged(null, "", null, TextDocumentRevision(9), events[6].stamp),
            ),
            events.subList(5, events.size),
            "a commit that IS the composed text changes nothing: the terminal alone ends it — no Replace is fabricated",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(9), null),
            harness.session.state.value,
            "the second composition ended with the same document the commit named",
        )
        assertTrue(
            events.none { it is TextInputEvent.SelectionChanged },
            "a pre-edition is never encoded as a SelectionChanged (DESIGN §10.3)",
        )
        harness.close()
    }

    // -----------------------------------------------------------------------
    // Cas 3 — révision périmée : StaleRevision ; même révision, texte différent : InvalidRequest
    // -----------------------------------------------------------------------

    @Test
    fun staleRevisionIsRejected() = runBlocking {
        val harness = TextHarness(
            this,
            TextInputConfig(
                surroundingText = "hello",
                selection = TextRange(5, 5),
                documentRevision = TextDocumentRevision(0),
            ),
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(0, 1)),
            harness.session.updateCursor(cursorRect(), TextDocumentRevision(1)),
            "a cursor at a revision the session never accepted is stale",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("text")),
            harness.session.updateSurroundingText("other", TextRange(5, 5), TextDocumentRevision(0)),
            "the same revision with a different text is the InvalidRequest of the contract",
        )

        // Les révisions courantes passent — le rond-trip reste vivant derrière les refus.
        assertEquals(KadreResult.Success(Unit), harness.session.updateCursor(cursorRect(), TextDocumentRevision(0)))
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("hello!", TextRange(6, 6), TextDocumentRevision(1)),
        )

        // Le garde propre au port : l'owner lui-même refuse une révision qu'il ne tient pas.
        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(1, 0)),
            harness.port.updateCursor(
                TextInputCursorCommand(harness.owner, cursorRect(), TextDocumentRevision(0)),
            ),
        )
        // Et un owner étranger n'est pas celui du port : la InvalidRequest qui le nomme.
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("textInputOwner")),
            harness.port.updateDocument(
                TextInputDocumentCommand(
                    ForeignTextInputOwner,
                    "next",
                    TextRange(0, 0),
                    TextDocumentRevision(1),
                ),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Cas 4 — perte de focus : Suspended SANS détruire la composition ; resume : Active
    // -----------------------------------------------------------------------

    @Test
    fun focusLossSuspendsWithoutDestroyingComposition() = runBlocking {
        val harness = TextHarness(
            this,
            TextInputConfig(
                surroundingText = "abc",
                selection = TextRange(3, 3),
                documentRevision = TextDocumentRevision(5),
            ),
        )
        val events = harness.collect()

        harness.owner.setComposingText("", 0)
        harness.owner.setComposingText("かん", 2)
        harness.settle()
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), TextRange(3, 3)),
            harness.session.state.value,
            "the composition is open before the focus moves: the range the state carries is the " +
                "observation's own, in the BASE document the application has not followed yet " +
                "(the web mirror :508)",
        )

        // Les points d'entrée que le runtime appelle lui-même (RuntimeSurfaceInput:436-444) :
        // la suspension ne publie aucun événement et garde la composition intacte.
        harness.session.suspend()
        assertEquals(
            TextInputState.Suspended(TextDocumentRevision(5), TextRange(3, 3)),
            harness.session.state.value,
            "a focus loss suspends the session without destroying the composition (DESIGN §10.3)",
        )
        harness.session.resume()
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), TextRange(3, 3)),
            harness.session.state.value,
            "a regained focus resumes the session with the composition it suspended",
        )

        // La composition reprend SUR LE SPAN SUSPENDU : l'app suit, le pas suivant rebasse.
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("abcかん", TextRange(5, 5), TextDocumentRevision(6)),
        )
        harness.owner.setComposingText("かんじ", 3)
        harness.settle()

        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), TextDocumentRevision(5), events[0].stamp),
                TextInputEvent.CompositionChanged(TextRange(3, 3), "かん", TextRange(2, 2), TextDocumentRevision(5), events[1].stamp),
                TextInputEvent.CompositionChanged(TextRange(3, 5), "かんじ", TextRange(3, 3), TextDocumentRevision(6), events[2].stamp),
            ),
            events,
            "the suspension and the resume published no event of their own and no synthetic reset: " +
                "the resumed composition continues from the span the accepted snapshot rebased it to",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(6), TextRange(3, 5)),
            harness.session.state.value,
            "the resumed composition continues at the span the accepted snapshot rebased it to: " +
                "the update's observation is admitted against THAT document, at THAT span",
        )
        harness.close()
    }

    // -----------------------------------------------------------------------
    // Cas 5 — deleteSurrounding : le Replace calculé depuis le document + la sélection
    // -----------------------------------------------------------------------

    @Test
    fun deleteSurroundingMapsToComputedReplace() = runBlocking {
        // before = 2, caret au milieu d'"abcd" : les deux unités de gauche partent.
        val before = TextHarness(
            this,
            TextInputConfig(surroundingText = "abcd", selection = TextRange(2, 2)),
        )
        val beforeEvents = before.collect()
        before.owner.deleteSurroundingText(2, 0)
        before.settle()
        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.Replace(TextRange(0, 2), "", TextDocumentRevision(0), beforeEvents[0].stamp),
            ),
            beforeEvents,
            "deleteSurrounding(before = 2) computes the Replace of the two units before the caret",
        )
        // L'application suit le Replace : le rond-trip accepte le document edité.
        assertEquals(
            KadreResult.Success(Unit),
            before.session.updateSurroundingText("cd", TextRange(0, 0), TextDocumentRevision(1)),
        )
        before.close()

        // after = 1 : l'unité de droite part — le miroir exact du bullet du gate.
        val after = TextHarness(
            this,
            TextInputConfig(surroundingText = "abcd", selection = TextRange(2, 2)),
        )
        val afterEvents = after.collect()
        after.owner.deleteSurroundingText(0, 1)
        after.settle()
        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.Replace(TextRange(2, 3), "", TextDocumentRevision(0), afterEvents[0].stamp),
            ),
            afterEvents,
            "deleteSurrounding(after = 1) computes the Replace of the unit after the caret",
        )
        after.close()

        // Les bornes du document : un delete hors bornes est RAMENÉ aux unités qui existent,
        // et un delete qui ne retire rien ne fabrique aucun événement.
        val clamped = TextHarness(
            this,
            TextInputConfig(surroundingText = "abcd", selection = TextRange(2, 2)),
        )
        val clampedEvents = clamped.collect()
        clamped.owner.deleteSurroundingText(99, 99)
        clamped.settle()
        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.Replace(TextRange(0, 4), "", TextDocumentRevision(0), clampedEvents[0].stamp),
            ),
            clampedEvents,
            "a delete reaching past the document bounds removes exactly the units that exist",
        )
        clamped.owner.deleteSurroundingText(0, 0)
        clamped.settle()
        assertEquals(1, clampedEvents.size, "an empty delete is silent: no observation is fabricated")
        clamped.close()
    }

    // -----------------------------------------------------------------------
    // Cas 6 — finish sans composition : silencieux
    // -----------------------------------------------------------------------

    @Test
    fun finishCompositionWithoutCompositionIsSilent() = runBlocking {
        val harness = TextHarness(
            this,
            TextInputConfig(surroundingText = "abc", selection = TextRange(3, 3)),
        )
        val events = harness.collect()

        harness.owner.finishComposingText()
        harness.settle()

        assertEquals(
            emptyList<TextInputEvent>(),
            events,
            "a finish with no active composition produces nothing: no observation is fabricated",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(0), null),
            harness.session.state.value,
            "the silent finish left the session exactly as it was",
        )
        assertEquals(
            KadreResult.Success(Unit),
            harness.session.updateSurroundingText("next", TextRange(4, 4), TextDocumentRevision(1)),
            "the session survives its own silence",
        )
        harness.close()
    }

    // -----------------------------------------------------------------------
    // Cas 7 — capability Unsupported TOUJOURS ; refus on-row ; pas de chemin public
    // -----------------------------------------------------------------------

    @Test
    fun capabilityStaysUnsupportedUntilNormativeDecision() = runBlocking {
        val port = AndroidTextInputPort(stampSource())
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.TextInput)),
            port.capability,
            "the port publishes Unsupported(TextInput) ALWAYS — the mapping machinery is proven, " +
                "the activation channel stays parked for the normative decision",
        )

        // La voie de PRODUCTION : la surface réelle, la configuration réelle — le réducteur
        // consulte la capability AVANT d'appeler open et répond le refus DU PORT.
        val surface = AndroidHostSurface(
            id = RuntimeProcessIds.nextSurfaceId(),
            initialState = testSurfaceState(),
            frameClock = { PortInertFrameClock },
            failureReporter = RuntimeFailureReporter { },
        )
        val config = TextInputConfig(surroundingText = "hello", selection = TextRange(5, 5))
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput)),
            surface.input.openTextInput(config),
            "the pre-install input refuses the open before any port exists",
        )
        installSession(surface)
        assertIs<Capability.Unsupported>(surface.input.state.value.capabilities.textInput)
        assertEquals(
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.TextInput)),
            surface.input.openTextInput(config),
            "the reducer refuses openTextInput before calling open: the port is never opened on-row",
        )
        assertEquals(
            0L,
            surface.input.state.value.revision.value,
            "the refusal moved no state: no session, no revision, no promise",
        )
    }

    // -----------------------------------------------------------------------
    // Le Disable du pont : closeConnection arrête l'owner et le port répond Closed
    // -----------------------------------------------------------------------

    @Test
    fun aClosedConnectionDisablesTheOwnerAndThePortRefusesFromThenOn() = runBlocking {
        val harness = TextHarness(this, TextInputConfig(surroundingText = "abc", selection = TextRange(3, 3)))
        val events = harness.collect()
        harness.owner.commitText("a")
        harness.settle()
        assertEquals(1, events.size, "the owner serves before the connection closes")

        harness.owner.closeConnection()
        assertTrue(harness.owner.isClosed, "closeConnection is the Disable of the transposed bridge")

        harness.owner.commitText("b")
        harness.settle()
        assertEquals(1, events.size, "a disabled owner publishes nothing further")

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            harness.port.updateCursor(
                TextInputCursorCommand(harness.owner, cursorRect(), TextDocumentRevision(0)),
            ),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            harness.port.updateDocument(
                TextInputDocumentCommand(harness.owner, "next", TextRange(0, 0), TextDocumentRevision(1)),
            ),
            "the disabled session answers the closed failure from then on",
        )
        harness.close()
    }

    // -----------------------------------------------------------------------
    // Outils de test — la session réelle câblée au port réel, comme le runtime le fait
    // -----------------------------------------------------------------------

    /** La source de stamps de session — séquentielle, la même instance pour toute une session. */
    private fun stampSource(): () -> EventStamp {
        var sequence = 0L
        return {
            sequence += 1
            EventStamp(SessionSequence(sequence), SessionInstant((sequence - 1).nanoseconds), null)
        }
    }

    private fun cursorRect(): LogicalRect = LogicalRect(LogicalPoint(1.0, 2.0), LogicalSize(3.0, 4.0))

    /**
     * Une session texte complète : le port réel, le owner qu'[open] rend, la VRAIE
     * [RuntimeTextInputSession] construite autour — exactement la forme de
     * `RuntimeSurfaceInput.openTextInput` (:148-176), le holder appartissant à l'appel.
     */
    private class TextHarness(scope: CoroutineScope, config: TextInputConfig) {
        private val scope: CoroutineScope = scope

        val port = AndroidTextInputPort(stampSourceOf())

        var session: RuntimeTextInputSession
            private set

        val owner: AndroidTextInputOwner

        private val collectorJobs = mutableListOf<Job>()

        init {
            // Le holder de l'appel : la callback ne route que vers LA session de cet open.
            val target = arrayOfNulls<RuntimeTextInputSession>(1)
            val opened = assertIs<KadreResult.Success<TextInputOwner>>(
                port.open(
                    TextInputOpenCommand(
                        surfaceId = RuntimeProcessIds.nextSurfaceId(),
                        config = config,
                        onObservation = { observation ->
                            target[0]?.acceptObservation(observation) ?: false
                        },
                    ),
                ),
            )
            owner = opened.value as AndroidTextInputOwner
            session = RuntimeTextInputSession(
                owner = owner,
                initialConfig = config,
                port = port,
                eventStampSource = stampSourceOf(),
                eventCollectorGate = RuntimeEventCollectorAllocator(
                    KadrePolicies.Default.resources.maxEventCollectorsPerSession,
                ).newGate(KadrePolicies.Default.resources.maxEventCollectorsPerFlow),
                failureReporter = {},
                onClosed = {},
            )
            target[0] = session
        }

        /** Enregistre le collecteur d'événements AVANT que le cas ne conduise quoi que ce soit. */
        suspend fun collect(): MutableList<TextInputEvent> {
            val received = mutableListOf<TextInputEvent>()
            collectorJobs += scope.launch { session.events.collect { received += it } }
            settle()
            return received
        }

        /** Laisse le collecteur s'enregistrer et les événements s'écouler (le même schéma T3). */
        suspend fun settle() {
            repeat(8) { yield() }
        }

        fun close() {
            collectorJobs.forEach(Job::cancel)
            session.close()
        }

        companion object {
            /** Une source de stamps PAR session (deux sessions ne partagent pas une séquence). */
            private fun stampSourceOf(): () -> EventStamp {
                var sequence = 0L
                return {
                    sequence += 1
                    EventStamp(SessionSequence(sequence), SessionInstant((sequence - 1).nanoseconds), null)
                }
            }
        }
    }

    /** Un owner qui n'est pas celui du port : la InvalidRequest qui le nomme. */
    private object ForeignTextInputOwner : TextInputOwner {
        override fun close() = Unit
    }

    /** L'installation de la configuration de session — les dix paramètres du seam (le schéma T3). */
    private fun installSession(on: AndroidHostSurface) {
        val discrete = org.graphiks.kadre.policy.EventDeliveryPolicy(
            ingressCapacity = 64,
            collectorCapacity = 16,
            ingressOverflow = org.graphiks.kadre.policy.IngressOverflowAction.CloseSource,
            collectorOverflow = org.graphiks.kadre.policy.CollectorOverflowAction.CancelSlowCollector,
        )
        on.installSessionConfiguration(
            deliveryPolicy = org.graphiks.kadre.policy.WindowDeliveryPolicy(
                discreteEvents = discrete,
                geometryChanges = org.graphiks.kadre.policy.ContinuousDelivery.Coalesced,
                redrawRequests = org.graphiks.kadre.policy.ContinuousDelivery.Latest,
            ),
            inputDeliveryPolicy = org.graphiks.kadre.policy.InputDeliveryPolicy(
                discreteEvents = discrete,
                pointerMotion = org.graphiks.kadre.policy.ContinuousDelivery.Coalesced,
                touchMotion = org.graphiks.kadre.policy.ContinuousDelivery.Coalesced,
                scroll = org.graphiks.kadre.policy.ContinuousDelivery.Coalesced,
                gestureChanges = org.graphiks.kadre.policy.ContinuousDelivery.Coalesced,
                gamepadChanges = org.graphiks.kadre.policy.ContinuousDelivery.Latest,
                rawInput = org.graphiks.kadre.policy.RawInputDeliveryPolicy(
                    16,
                    org.graphiks.kadre.policy.RawInputOverflowAction.DropOldestAndReport,
                ),
            ),
            source = stampSource(),
            sessionFailureHandler = {},
            collectorAllocator = RuntimeEventCollectorAllocator(64),
            maxCollectorsPerFlow = 4,
            resources = KadrePolicies.Default.resources,
            dropTransferScope = null,
            diagnostics = {},
            rawInputPort = null,
        )
    }
}

/** L'état initial de surface du test — le même instantané honnête que les autres tests hôtes. */
private fun testSurfaceState(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Focused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)

/** La clock inerte de CE test : les frames y sont posées et n'y courent jamais (aucun SDK). */
private object PortInertFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}
