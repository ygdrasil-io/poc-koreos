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
 * stamping are the real ones this phase must not touch.
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

        harness.access.emitCompositionStart()
        harness.access.emitCompositionUpdate("かん")
        testScheduler.runCurrent()
        assertEquals(
            TextRange(3, 3),
            (session.state.value as TextInputState.Active).composingRange,
            "the composition is open before the focus moves",
        )

        harness.port.deliverLifecycle(
            WebLifecycleSnapshot(
                connected = true,
                inOriginDocument = true,
                documentVisible = true,
                browsingContextFocused = false,
                subtreeFocused = false,
            ),
        )
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Suspended(TextDocumentRevision(5), TextRange(3, 3)),
            session.state.value,
            "a focus loss suspends the session without destroying the composition (DESIGN §10.3)",
        )

        harness.port.deliverLifecycle(
            WebLifecycleSnapshot(
                connected = true,
                inOriginDocument = true,
                documentVisible = true,
                browsingContextFocused = true,
                subtreeFocused = true,
            ),
        )
        testScheduler.runCurrent()
        assertEquals(
            TextInputState.Active(TextDocumentRevision(5), TextRange(3, 3)),
            session.state.value,
            "a regained focus resumes the session with the composition it suspended",
        )
        session.close()
        harness.stop()
        testScheduler.runCurrent()
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
    ): TextInputConfig = TextInputConfig(
        surroundingText = surroundingText,
        selection = selection,
        documentRevision = documentRevision,
        action = action,
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
