package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.TextDocumentRevision
import org.graphiks.kadre.input.TextInputAction
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputEvent
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.input.TextInputState
import org.graphiks.kadre.input.TextRange
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.internal.runtime.TextInputDocumentCommand
import org.graphiks.kadre.internal.runtime.TextInputOpenCommand
import org.graphiks.kadre.internal.runtime.TextInputOwner
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.LogicalRect
import org.graphiks.kadre.surface.LogicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The text-input seam of the web surface, end to end over the real runtime session machine.
 *
 * Every case drives the real [WebTextInputPort] — the one `installSessionConfiguration` builds from
 * the target's element access — behind a real [HostSurface], so what is proven is the whole contract:
 * the structural capability the flip publishes, the one-session admission the runtime owns, the
 * observations the shadow computes against the configured document, the write-back that reaches the
 * element, the suspension a focus loss publishes without destroying a composition, and a teardown
 * that closes the session and withdraws the listeners.
 *
 * The element is the [FakeWebTextInputElementAccess] double (the browser targets run their own suites
 * against real elements); the runtime session machine, the revision contract and the observation
 * stamping are the real ones this phase must not touch. The one exception is the port's own failure
 * containment, driven on the bare [WebTextInputPort]: a listener whose observation callback throws
 * has no surface-level shape, because the runtime's callback cannot be made to throw from outside.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebTextInputSurfaceTest {
    @Test
    fun theSessionConfigurationInstallsATextPortWhoseCapabilityIsStructurallySupported() = runTest {
        val harness = TextInputHarness(this)
        harness.start()

        assertEquals(
            Capability.Supported(Unit, FeatureAvailability.Available),
            harness.surface().input.state.value.capabilities.textInput,
            "the port is installed structurally: the capability does not ask whether the element is editable (D-X2)",
        )
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aSecondOpenTextInputWhileOneIsLiveIsAlreadyInUse() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val surface = harness.surface()

        val first = surface.input.openTextInput(config())
        assertIs<KadreResult.Success<TextInputSession>>(first)

        val second = surface.input.openTextInput(config())
        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.TextInputSession)),
            second,
            "one session per surface: a second open is the runtime's own admission, never a replacement",
        )
        first.value.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun anInsertTextObservationIsStampedAtTheConfiguredRevisionAtUtf16Offsets() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        // "a😀b" is four UTF-16 code units: a, the surrogate pair, b. The caret sits after all of
        // them, so the range an insertion at the caret reports is 4 — not the three code points.
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(
                    surroundingText = "a😀b",
                    selection = TextRange(4, 4),
                    documentRevision = TextDocumentRevision(7),
                ),
            ),
        ).value
        val events = collect(session)

        harness.access.emitBeforeInput("insertText", "é")

        testScheduler.runCurrent()
        assertEquals(
            listOf<TextInputEvent>(TextInputEvent.Replace(TextRange(4, 4), "é", TextDocumentRevision(7), events[0].stamp)),
            events,
            "the edit is computed against the shadow, whose offsets are UTF-16 code units of the configured document",
        )
        harness.access.emitBeforeInput("insertText", null)
        testScheduler.runCurrent()
        assertTrue(
            events.none { it is TextInputEvent.SelectionChanged },
            "no selection is ever fabricated: an insertText is a Replace and nothing else (DESIGN §10.3)",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aCompositionLifecycleReportsRangeInTheDocumentAndSelectionInTheTextAndATerminalEnd() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(
                    surroundingText = "abc",
                    selection = TextRange(3, 3),
                    documentRevision = TextDocumentRevision(5),
                ),
            ),
        ).value
        val events = collect(session)

        harness.access.emitCompositionStart()
        harness.access.emitCompositionUpdate("かんじ")
        harness.access.emitCompositionEnd("かんじ")
        testScheduler.runCurrent()

        val revision = TextDocumentRevision(5)
        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), revision, events[0].stamp),
                TextInputEvent.CompositionChanged(TextRange(3, 3), "かんじ", TextRange(3, 3), revision, events[1].stamp),
                TextInputEvent.CompositionChanged(null, "", null, revision, events[2].stamp),
            ),
            events,
            "start opens at the shadow selection, update replaces that range with the composed text, " +
                "end is terminal: range and selection both null",
        )
        assertEquals(
            null,
            (session.state.value as TextInputState.Active).composingRange,
            "a terminal composition leaves no composing range behind",
        )
        assertTrue(
            events.none { it is TextInputEvent.SelectionChanged },
            "a pre-edition is never encoded as a SelectionChanged (DESIGN §10.3)",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aCompositionEndWithAFinalStringDifferentFromTheCompositionReportsTheCorrection() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(
                    surroundingText = "abc",
                    selection = TextRange(3, 3),
                    documentRevision = TextDocumentRevision(5),
                ),
            ),
        ).value
        val events = collect(session)

        harness.access.emitCompositionStart()
        harness.access.emitCompositionUpdate("かん")
        // The application follows the composition in step, the way the contract asks: the snapshot it
        // accepts is the substitution of the composed text in the range the observations carried, and
        // the correction the end carries is stamped at that accepted revision.
        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("abcかん", TextRange(5, 5), TextDocumentRevision(6)),
        )
        harness.access.emitCompositionEnd("漢字")
        testScheduler.runCurrent()

        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), TextDocumentRevision(5), events[0].stamp),
                TextInputEvent.CompositionChanged(TextRange(3, 3), "かん", TextRange(2, 2), TextDocumentRevision(5), events[1].stamp),
                TextInputEvent.Replace(TextRange(3, 5), "漢字", TextDocumentRevision(6), events[2].stamp),
                TextInputEvent.CompositionChanged(null, "", null, TextDocumentRevision(6), events[3].stamp),
            ),
            events,
            "the browser committed a final string the updates never carried: the text fact is reported " +
                "as the Replace it is, then the composition is terminated",
        )
        assertEquals(
            null,
            (session.state.value as TextInputState.Active).composingRange,
            "the correction ended the composition",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aSubmissionKeyOnASingleLineElementPublishesTheConfiguredActionAndNeverText() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "abc", selection = TextRange(3, 3), action = TextInputAction.Done),
            ),
        ).value
        val events = collect(session)

        harness.access.emitKeyDown("Enter")
        harness.access.emitKeyDown("a")
        testScheduler.runCurrent()

        assertEquals(
            listOf<TextInputEvent>(TextInputEvent.Action(TextInputAction.Done, TextDocumentRevision(0), events[0].stamp)),
            events,
            "Enter on a single-line element is the browser's submission key: the config's action, " +
                "and a letter keydown produces no observation at all",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aTextareaEnterIsALineBreakTheShadowFollowsAndTheKeydownIsSilent() = runTest {
        val harness = TextInputHarness(this, kind = "textarea")
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "abc", selection = TextRange(3, 3), action = TextInputAction.Done),
            ),
        ).value
        val events = collect(session)

        harness.access.emitKeyDown("Enter")
        harness.access.emitBeforeInput("insertLineBreak", null)
        testScheduler.runCurrent()

        val revision = TextDocumentRevision(0)
        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.Replace(TextRange(3, 3), "\n", revision, events[0].stamp),
                TextInputEvent.Action(TextInputAction.Done, revision, events[1].stamp),
            ),
            events,
            "a multiline element's Enter inserts the line the browser will perform and publishes the " +
                "AppKit action mapping — once, from the beforeinput, never from the keydown",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun anElementThatIsNotAnInputOrTextareaOpensASessionThatObservesNothing() = runTest {
        val harness = TextInputHarness(this, kind = "div")
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(config()),
        ).value
        val events = collect(session)

        assertTrue(
            !access.installed,
            "a non-addressable element gets no listeners: observing nothing is the D-X2 boundary",
        )
        access.emitBeforeInput("insertText", "x")
        access.emitCompositionStart()
        access.emitKeyDown("Enter")
        testScheduler.runCurrent()
        assertEquals(
            emptyList<TextInputEvent>(),
            events,
            "no observation of any kind is produced for an element the v1 contract does not address",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aDeleteTheShadowCannotComputePublishesNothingAtAll() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(
                    surroundingText = "a😀b",
                    selection = TextRange(4, 4),
                    documentRevision = TextDocumentRevision(7),
                ),
            ),
        ).value
        val events = collect(session)

        // The caret sits after the surrogate pair, the shape a backspace is aimed at in the wild: a
        // browser removes the whole grapheme cluster, and the shadow has no cluster boundary to
        // compute it from — the one-code-unit edit it could reach would report deleting the low
        // surrogate, silently splitting the character in the application's document.
        harness.access.emitBeforeInput("deleteContentBackward", null)
        harness.access.emitBeforeInput("deleteContentForward", null)
        testScheduler.runCurrent()

        assertEquals(
            emptyList<TextInputEvent>(),
            events,
            "a delete is a grapheme-cluster edit the shadow cannot compute: it produces no " +
                "observation, and the next accepted write-back rewrites the element and re-syncs",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aWriteBackOnAnElementThatCannotCarryItAnswersClosedWithoutTouchingTheElement() = runTest {
        val harness = TextInputHarness(this, kind = "div")
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(config()),
        ).value

        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            session.updateSurroundingText("next", TextRange(4, 4), TextDocumentRevision(1)),
            "the write-back is the contract and this element cannot carry it: the closed failure, " +
                "never a write of a non-contract property onto the host's element",
        )
        assertTrue(
            access.writes.isEmpty(),
            "the element is never asked: the port refuses before the write, on every target",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            session.updateSurroundingText("next", TextRange(4, 4), TextDocumentRevision(2)),
            "the session that could not honour its own write-back answers Closed from then on",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun theWriteBackReachesTheElementAndTheSameRevisionIsIdempotentOrRefused() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "hello", selection = TextRange(5, 5), documentRevision = TextDocumentRevision(7)),
            ),
        ).value

        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("hello world", TextRange(5, 5), TextDocumentRevision(8)),
        )
        assertEquals(
            listOf(Triple("hello world", 5, 5)),
            access.writes,
            "the accepted snapshot is applied to the element: value and selection, read back by the case",
        )

        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("hello world", TextRange(5, 5), TextDocumentRevision(8)),
            "the same revision with an identical payload is idempotent",
        )
        assertEquals(
            1,
            access.writes.size,
            "the idempotent call is the runtime's own short circuit: the element is written exactly once",
        )

        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("text")),
            session.updateSurroundingText("other", TextRange(5, 5), TextDocumentRevision(8)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.InvalidRequest("selection")),
            session.updateSurroundingText("hello world", TextRange(1, 2), TextDocumentRevision(8)),
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(8, 7)),
            session.updateSurroundingText("hello world", TextRange(5, 5), TextDocumentRevision(7)),
        )
        assertEquals(
            1,
            access.writes.size,
            "a refused snapshot never reaches the element",
        )

        assertEquals(
            KadreResult.Success(Unit),
            session.updateCursor(LogicalRect(LogicalPoint(1.0, 2.0), LogicalSize(3.0, 4.0)), TextDocumentRevision(8)),
            "the cursor rect is accepted and stored",
        )
        assertEquals(
            1,
            access.writes.size,
            "the cursor has no browser effect: the browser draws its own caret (recorded limit)",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aHigherRevisionRebasesTheCompositionAndANonReconciliableCommitClosesTheSession() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "ab", selection = TextRange(2, 2), documentRevision = TextDocumentRevision(1)),
            ),
        ).value

        access.emitCompositionStart()
        access.emitCompositionUpdate("かん")
        testScheduler.runCurrent()

        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("abかん", TextRange(4, 4), TextDocumentRevision(2)),
            "the app accepts the composition as the substitution of the composed text in its range",
        )
        assertEquals(
            Triple("abかん", 4, 4),
            access.writes.last(),
            "the accepted composition snapshot is applied to the element",
        )

        access.emitCompositionUpdate("かんじ")
        testScheduler.runCurrent()
        val compositionRange = (session.state.value as TextInputState.Active).composingRange
        assertEquals(
            TextRange(2, 4),
            compositionRange,
            "the composition continued: its range now spans the accepted composed text in the accepted snapshot",
        )

        // The commit that closes: the snapshot is a valid substitution, but while the port is
        // applying it the element observes something new — an observation the write provokes is
        // admitted against the runtime's still-open commit window, and the runtime refuses to claim
        // the port and itself are still reconciled: it closes the session and answers the closed
        // failure (DESIGN §10.3).
        access.onWrite = { access.emitCompositionEnd(null) }
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            session.updateSurroundingText("abかんじ", TextRange(5, 5), TextDocumentRevision(3)),
        )
        assertEquals(
            TextInputState.Closed,
            session.state.value,
            "a commit the runtime could not reconcile closes the session",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            session.updateSurroundingText("abかんじ", TextRange(5, 5), TextDocumentRevision(4)),
            "a closed session answers Closed to every later call",
        )
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aFocusLossSuspendsWithTheCompositionPreservedAndARegainedFocusResumesIt() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "abc", selection = TextRange(3, 3), documentRevision = TextDocumentRevision(5)),
            ),
        ).value
        val events = collect(session)

        harness.access.emitCompositionStart()
        harness.access.emitCompositionUpdate("かん")
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), TextRange(3, 3)),
            session.state.value,
            "the composition is open before the focus moves",
        )

        harness.port.deliverLifecycle(focusSnapshot(focused = false))
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Suspended(TextDocumentRevision(5), TextRange(3, 3)),
            session.state.value,
            "a focus loss suspends the session without destroying the composition (DESIGN §10.3)",
        )

        harness.port.deliverLifecycle(focusSnapshot(focused = true))
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), TextRange(3, 3)),
            session.state.value,
            "a regained focus resumes the session with the composition it suspended",
        )

        // The composition continues on its own terms: the application follows it in step, and the
        // next update replaces the span the composition already holds — rebased to the accepted
        // snapshot, never a reset back to an empty composition.
        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("abcかん", TextRange(5, 5), TextDocumentRevision(6)),
        )
        harness.access.emitCompositionUpdate("かんじ")
        testScheduler.runCurrent()
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

        // And a focus lost again mid-composition suspends the composition the session now holds,
        // still preserved, and hands it back the same way.
        harness.port.deliverLifecycle(focusSnapshot(focused = false))
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Suspended(TextDocumentRevision(6), TextRange(3, 5)),
            session.state.value,
            "a repeated focus loss suspends the composition the session now holds, still preserved",
        )
        harness.port.deliverLifecycle(focusSnapshot(focused = true))
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Active(TextDocumentRevision(6), TextRange(3, 5)),
            session.state.value,
            "and the second resume hands it back the same way",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aCancelledCompositionEndsWithoutACommitLeavesNoCompositionBehind() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(
                    surroundingText = "abc",
                    selection = TextRange(3, 3),
                    documentRevision = TextDocumentRevision(5),
                ),
            ),
        ).value
        val events = collect(session)

        harness.access.emitCompositionStart()
        harness.access.emitCompositionUpdate("かん")
        // Échap: the browser cancelled. The end event carries no data at all, and the application
        // never accepted a snapshot in between — the composition ends without a commit.
        harness.access.emitKeyDown("Escape")
        harness.access.emitCompositionEnd(null)
        testScheduler.runCurrent()

        val revision = TextDocumentRevision(5)
        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), revision, events[0].stamp),
                TextInputEvent.CompositionChanged(TextRange(3, 3), "かん", TextRange(2, 2), revision, events[1].stamp),
                TextInputEvent.CompositionChanged(null, "", null, revision, events[2].stamp),
            ),
            events,
            "a cancellation is the terminal composition observation and nothing else: no text fact is " +
                "fabricated for the removal the browser performed itself, and Échap publishes no action",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), null),
            session.state.value,
            "a cancelled composition leaves no composition behind: the session is active with none",
        )
        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("abc", TextRange(3, 3), TextDocumentRevision(6)),
            "the session survives its cancellation: the next accepted write-back restores the " +
                "element-and-shadow agreement",
        )
        assertEquals(
            Triple("abc", 3, 3),
            harness.access.writes.last(),
            "the restoring write-back reached the element",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The cancellation a real browser performs (Échap): the end event carries the empty final string
     * of the withdrawn composition, so the port reports the removal the browser made — and the
     * runtime that never accepted the composition refuses that removal by its own range check,
     * without losing the terminal end.
     */
    @Test
    fun aCancellationTheBrowserPerformedReportsTheRemovalTheRuntimeRefusesAndStillEndsClean() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(
                    surroundingText = "abc",
                    selection = TextRange(3, 3),
                    documentRevision = TextDocumentRevision(5),
                ),
            ),
        ).value
        val events = collect(session)

        harness.access.emitCompositionStart()
        harness.access.emitCompositionUpdate("かん")
        harness.access.emitCompositionEnd("")
        testScheduler.runCurrent()

        assertEquals(
            listOf<TextInputEvent>(
                TextInputEvent.CompositionChanged(TextRange(3, 3), "", TextRange(0, 0), TextDocumentRevision(5), events[0].stamp),
                TextInputEvent.CompositionChanged(TextRange(3, 3), "かん", TextRange(2, 2), TextDocumentRevision(5), events[1].stamp),
                TextInputEvent.CompositionChanged(null, "", null, TextDocumentRevision(5), events[2].stamp),
            ),
            events,
            "the removal the browser performed is an observation the runtime refuses — the range it " +
                "names is not within the document the application still holds — and the end still " +
                "terminates the composition: the session keeps the document it always had",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), null),
            session.state.value,
            "a cancelled composition leaves no composition behind and no refused-fact scar",
        )
        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("abc!", TextRange(4, 4), TextDocumentRevision(6)),
            "the application still writes its own document back, and the element agrees with it again",
        )
        assertEquals(
            Triple("abc!", 4, 4),
            harness.access.writes.last(),
            "the write-back after the cancellation reached the element",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun twoWriteBacksAcrossConsecutiveRevisionsReachTheElementInOrder() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "hello", selection = TextRange(5, 5), documentRevision = TextDocumentRevision(7)),
            ),
        ).value

        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("hello world", TextRange(11, 11), TextDocumentRevision(8)),
        )
        assertEquals(
            KadreResult.Success(Unit),
            session.updateSurroundingText("hello kadre", TextRange(5, 5), TextDocumentRevision(9)),
            "the revision that follows an accepted one is admitted: the runtime serialises the " +
                "write-backs across revisions n and n+1",
        )
        assertEquals(
            listOf(Triple("hello world", 11, 11), Triple("hello kadre", 5, 5)),
            access.writes,
            "both accepted snapshots reached the element, in the order the revisions applied them",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(9), null),
            session.state.value,
            "the session's revision is the last accepted one",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(9, 8)),
            session.updateSurroundingText("hello world", TextRange(11, 11), TextDocumentRevision(8)),
            "the earlier revision is stale once n+1 is accepted",
        )
        assertEquals(
            2,
            access.writes.size,
            "the stale retry never reached the element",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aStaleObservationFromAClosedSessionNeverReachesALaterSession() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        val first = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(config(surroundingText = "hello", selection = TextRange(5, 5))),
        ).value
        val firstEvents = collect(first)
        // The first session's own handlers, captured while it lived — what a port that defers its
        // observations (the AppKit queue precedent) would still hold after the close.
        val stale = checkNotNull(access.callbacks)

        first.close()
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Closed,
            first.state.value,
            "the first session is closed when the later one opens",
        )

        val second = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(config(surroundingText = "next", selection = TextRange(4, 4))),
        ).value
        val secondEvents = collect(second)

        stale.onCompositionStart()
        stale.onBeforeInput("insertText", "late")
        stale.onKeyDown("Enter")
        testScheduler.runCurrent()

        assertEquals(
            emptyList<TextInputEvent>(),
            secondEvents,
            "an observation the closed session's own channel still carries is rejected: the " +
                "call-scoped holder of the runtime and the port's own guard route it to no later session",
        )
        assertEquals(
            TextInputState.Active(TextDocumentRevision(0), null),
            second.state.value,
            "the later session is exactly what its own open built: the late observation touched nothing",
        )
        assertEquals(
            emptyList<TextInputEvent>(),
            firstEvents,
            "and the first session published nothing of its own before it closed",
        )
        second.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun aTeardownDuringCompositionClosesTheSessionAndDeliversNoLateComposition() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "abc", selection = TextRange(3, 3), documentRevision = TextDocumentRevision(5)),
            ),
        ).value
        val events = collect(session)

        access.emitCompositionStart()
        access.emitCompositionUpdate("かん")
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), TextRange(3, 3)),
            session.state.value,
            "the composition is in flight when the teardown arrives",
        )
        val stale = checkNotNull(access.callbacks)

        harness.stop()
        testScheduler.runCurrent()

        assertEquals(
            TextInputState.Closed,
            session.state.value,
            "the teardown closed a session that was composing",
        )
        assertTrue(
            access.withdrawals >= 1,
            "the teardown withdrew the listeners with the session",
        )
        stale.onCompositionUpdate("かんじ")
        stale.onCompositionEnd("かんじ")
        testScheduler.runCurrent()

        assertEquals(
            2,
            events.size,
            "the late composition is nothing: no callback survived the teardown",
        )
        assertEquals(
            TextInputState.Closed,
            session.state.value,
            "and the closed state was never revisited",
        )
    }

    @Test
    fun aLineBreakBeforeinputOnASingleLineElementProducesNothingBecauseTheHostCannotPerformIt() = runTest {
        val harness = TextInputHarness(this, kind = "input")
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(
                    surroundingText = "abc",
                    selection = TextRange(3, 3),
                    multiline = true,
                    action = TextInputAction.Done,
                ),
            ),
        ).value
        val events = collect(session)

        access.emitBeforeInput("insertLineBreak", null)
        access.emitBeforeInput("insertParagraph", null)
        testScheduler.runCurrent()

        assertEquals(
            emptyList<TextInputEvent>(),
            events,
            "the element kind is the host's boundary: a single-line input cannot perform the line " +
                "break such a beforeinput describes, so reporting one would lie about the document — " +
                "multiline is the config's wish, the element kind is the fact (D-X2)",
        )

        access.emitKeyDown("Enter")
        testScheduler.runCurrent()
        assertEquals(
            listOf<TextInputEvent>(TextInputEvent.Action(TextInputAction.Done, TextDocumentRevision(0), events[0].stamp)),
            events,
            "the submission key stays the single-line element's own fact, whatever the config's " +
                "multiline wish",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    @Test
    fun anInstallThatThrowsRefusesTheOpenWithTheSeamFailureAndLeavesNoSessionBehind() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        access.installThrows = true

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, TEXT_INPUT_SEAM_DOMAIN, TEXT_INPUT_INSTALL_CODE),
            ),
            harness.surface().input.openTextInput(config()),
            "an element that refused its listeners is the seam's own platform failure, never an " +
                "exception thrown through the runtime's open call",
        )
        assertTrue(
            access.withdrawals >= 1,
            "whatever listeners the failed installation landed were withdrawn with the refused open",
        )

        access.installThrows = false
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(config()),
            "the refused open left no session behind: the same port admits a later one",
        ).value
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    /**
     * The port-level containment of a listener's own failure: the observation path of a session is
     * the one thing a broken application callback can poison, so the listener closes its owner
     * instead of letting the exception escape — into the caller, which on a real target is the
     * browser's dispatch and, with it, the page.
     */
    @Test
    fun aThrowingObservationCallbackClosesTheOwnerAndNeverEscapesTheListener() = runTest {
        val access = FakeWebTextInputElementAccess()
        val port = WebTextInputPort(access)
        val owner = assertIs<WebTextInputOwner>(
            assertIs<KadreResult.Success<TextInputOwner>>(
                port.open(
                    TextInputOpenCommand(
                        surfaceId = RuntimeProcessIds.nextSurfaceId(),
                        config = TextInputConfig(surroundingText = "hello", selection = TextRange(5, 5)),
                        onObservation = { error("the application's collector broke") },
                    ),
                ),
            ).value,
        )

        access.emitBeforeInput("insertText", "x")
        // Reaching this line at all is the containment: the exception the handler threw became the
        // owner's close, never an exception thrown back into the caller.
        assertTrue(
            owner.isClosed,
            "the failed handler closed its own owner: a shadow whose observation path broke can no " +
                "longer be trusted with offsets",
        )
        assertTrue(
            access.withdrawals >= 1,
            "the closing owner withdrew the listeners it still had",
        )
        assertEquals(
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.TextInputSession)),
            port.updateDocument(
                TextInputDocumentCommand(owner, "next", TextRange(0, 0), TextDocumentRevision(1)),
            ),
            "the session the failed listener served answers the closed failure from then on",
        )
        access.emitBeforeInput("insertText", "y")

        assertIs<KadreResult.Success<TextInputOwner>>(
            port.open(
                TextInputOpenCommand(
                    surfaceId = RuntimeProcessIds.nextSurfaceId(),
                    config = TextInputConfig(surroundingText = "hello", selection = TextRange(5, 5)),
                    onObservation = { true },
                ),
            ),
        ).value.close()
        // a later open on the same port succeeds: the failed owner left no session behind
    }

    @Test
    fun terminatingTheSurfaceClosesTheSessionAndWithdrawsTheListeners() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(config()),
        ).value
        assertTrue(access.installed, "the addressable open installed the observation listeners")

        harness.stop()
        testScheduler.runCurrent()

        assertEquals(
            TextInputState.Closed,
            session.state.value,
            "the surface teardown closes the text session it owns",
        )
        assertTrue(
            access.withdrawals >= 1,
            "the teardown withdraws the listeners with the session",
        )
        access.emitBeforeInput("insertText", "x")
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Closed,
            session.state.value,
            "a late observation after the teardown is nothing: the listeners are gone",
        )
    }

    @Test
    fun anElementWriteThatRefusesReportsThePlatformFailureOfTheSeam() = runTest {
        val harness = TextInputHarness(this)
        harness.start()
        val access = harness.access
        val session = assertIs<KadreResult.Success<TextInputSession>>(
            harness.surface().input.openTextInput(
                config(surroundingText = "hello", selection = TextRange(5, 5), documentRevision = TextDocumentRevision(7)),
            ),
        ).value
        access.writeSucceeds = false

        assertEquals(
            KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.Web, TEXT_INPUT_SEAM_DOMAIN, TEXT_INPUT_WRITE_BACK_CODE),
            ),
            session.updateSurroundingText("next", TextRange(4, 4), TextDocumentRevision(8)),
            "a write-back the element refused is the seam's own platform failure, never a silent success",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
    }

    private fun config(
        surroundingText: String = "hello",
        selection: TextRange = TextRange(5, 5),
        documentRevision: TextDocumentRevision = TextDocumentRevision(0),
        action: TextInputAction = TextInputAction.Default,
        multiline: Boolean = false,
    ): TextInputConfig = TextInputConfig(
        surroundingText = surroundingText,
        selection = selection,
        documentRevision = documentRevision,
        action = action,
        multiline = multiline,
    )

    /** The lifecycle snapshot of a browsing context whose focus is [focused]. */
    private fun focusSnapshot(focused: Boolean): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = true,
        inOriginDocument = true,
        documentVisible = true,
        browsingContextFocused = focused,
        subtreeFocused = focused,
    )

    /** Collects the session events of [session] into a list the case reads after `runCurrent`. */
    private fun TestScope.collect(session: TextInputSession): MutableList<TextInputEvent> {
        val events = mutableListOf<TextInputEvent>()
        launch { session.events.collect { events += it } }
        testScheduler.runCurrent()
        return events
    }
}

/**
 * The one session a text case drives: a real [WebHostSession] over a [RecordingWebHostPort] whose
 * element access is the [FakeWebTextInputElementAccess], so the surface installs the real text port
 * against an element a case controls.
 */
private class TextInputHarness(
    scope: TestScope,
    kind: String? = "input",
) {
    val access = FakeWebTextInputElementAccess().apply { this.kind = kind }
    val port = RecordingWebHostPort(WebSurfaceMetrics(48.0, 48.0, 1.0)).apply { textInputElementAccess = access }

    private val scopeReady = CompletableDeferred<KadreScope>()
    private val session: KadreSession

    init {
        session = assertIs<KadreResult.Success<KadreSession>>(
            WebHostSession(
                port = port,
                registry = WebHostRegistry(),
                failureReporter = RuntimeFailureReporter { },
            ).attach(
                parentScope = scope,
                applicationFactory = KadreApplicationFactory {
                    KadreApplication {
                        scopeReady.complete(this)
                        awaitCancellation()
                    }
                },
                policy = KadrePolicies.Default,
            ),
        ).value
    }

    /** Pumping the scheduler until the application scope has been handed to the test. */
    suspend fun start() = scopeReady.await()

    /** The surface this session owns, once its application scope exists. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun surface(): HostSurface = scopeReady.getCompleted().primarySurface.value
        ?: error("a web session exposes a primary surface")

    fun stop() = session.requestStop()
}
