import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves phase 6 Task 3 — la session texte DÉLIVRÉE conduite contre le receiver UIKit VIVANT
/// (le champ alpha-0 1×1 posé sous la surfaceView à l'open), les deux familles. Noms de
/// scénarios FIGÉS pour Task 4 (contrat BCK-024) :
///
///   uikit-text-open-exclusive-rejects-second  → testUikitTextOpenExclusiveRejectsSecond
///   uikit-text-replace-mapped-from-ukit-range → testUikitTextReplaceMappedFromUikitRange
///   uikit-text-delete-grapheme-honest         → testUikitTextDeleteGraphemeHonest
///   uikit-text-stale-revision-refused         → testUikitTextStaleRevisionRefused
///   uikit-text-focus-suspend-resume           → testUikitTextFocusSuspendResume
///   uikit-text-close-no-orphan-composition    → testUikitTextCloseNoOrphanComposition
///   uikit-text-actions-mapped                 → testUikitTextActionsMapped
///   (+ mapping de config Task 2 vérifié sur le receiver — testUikitTextConfigMappedOntoTheReceiver)
///
/// ROUTES DE STIMULUS (ruling pré-vol : AUCUN événement système synthétisé) :
/// - **UIKeyInput programmatique** (`insertText:`/`deleteBackward:`/`setMarkedText:`) — des
///   appels RESPONDER réels sur le receiver découvert par la sonde (le spike Task 1 les a
///   prouvés vivants : le champ applique, `.editingChanged` part). Le hook utilisateur
///   `shouldChangeCharactersInRange` NE part JAMAIS sur ces appels (fait Task 1 (d)) ;
/// - **conduite des hooks propres de l'adaptateur** (`receiverDriveShouldChange` /
///   `receiverDriveShouldReturn`) — le port EST son propre delegate ; appeler le hook est un
///   appel UNITAIRE du routage propre de l'adaptateur, pas un événement système. C'est la
///   seule route honnête du driver vers l'observation Replace (le canal utilisateur), la
///   mutation du champ étant appliquée par le stimulus programmatique qui suit — exactement
///   l'ordre UIKit (shouldChange AVANT la modification, le range étant celui qu'UIKit nomme
///   pour cette édition, frontière de graphème prouvée au spike) ;
/// - **write-back** (`updateSurroundingTextProbe`) — la direction Kotlin → champ : le document
///   du runtime est écrit dans le champ sous la garde d'écho, la révision du runtime avance.
///
/// SÉMANTIQUE DÉLIVRÉE (assertée telle quelle, jamais contournée) : un `.editingChanged`
/// SANS candidat (`shouldChange` n'a pas eu lieu) SANS écho SANS marqué est le verdict
/// `Nothing` du routeur pur ([UIKitTextInputEditRouter]) — un fait non attribué ne publie
/// RIEN. Les édits programmatiques purs sont donc INOBSERVÉS par délivrance (le champ→Kotlin
/// est routé pour la saisie utilisateur seule — le charter du vrai clavier) : chaque scénario
/// asserte la direction qu'il prouve et le rapport Task 3 documente la route de chaque
/// assertion.
final class KadreUikitTextInputTests: XCTestCase {

    private let driver = KadreUikitSurfaceDriver.shared

    /// Filet de sécurité ENTRE tests (même discipline que KadreUikitSurfaceTests) : toute
    /// session encore Starting/Running est arrêtée et attendue (borné) — le shutdown du port
    /// texte au démontage ferme tout owner texte restant.
    override func tearDown() {
        super.tearDown()
        for fixture in DriverFixture.shared.connected {
            guard let session = fixture.session else { continue }
            let state = session.state.value as? (any SessionState)
            guard state is SessionStateStarting || state is SessionStateRunning else {
                fixture.session = nil
                continue
            }
            session.requestStop()
            let terminated = expectation(description: "teardown: session terminated")
            session.awaitTermination { _, _ in terminated.fulfill() }
            wait(for: [terminated], timeout: 15)
            try? fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }
            fixture.session = nil
        }
    }

    // MARK: - Helpers (les patterns des suites phase 2-5, adaptés au texte)

    /// Le fixture primaire, prêt (fenêtre + vue + activation) — existence attendue de façon
    /// bornée (l'app hôte peut connecter sa scène après le démarrage froid du process).
    func primaryFixture() throws -> SceneFixture {
        let connected = Date().addingTimeInterval(25)
        while DriverFixture.shared.primary == nil {
            if Date() > connected {
                XCTFail("no scene fixture connected after 25s — the host app never connected its scene")
                throw NSError(domain: "KadreUikitDriver", code: 4,
                              userInfo: ["what": "primary scene never connected"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        let fixture = try XCTUnwrap(DriverFixture.shared.primary)
        try fixture.waitUntilReady()
        return fixture
    }

    /// attach nominal + capture des faits — retourne (fixture, session, surface typée).
    func attachedSurface(
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> (SceneFixture, any KadreSession, any HostSurface) {
        let fixture = try primaryFixture()
        let window = try XCTUnwrap(fixture.window, file: file, line: line)
        let view = try XCTUnwrap(fixture.observedView, file: file, line: line)
        let runsBeforeAttach = fixture.applicationRuns
        let result = KadreIos.shared.attach(
            windowScene: fixture.scene,
            window: window,
            surfaceView: view,
            applicationFactory: DriverApplication(fixture: fixture),
            policy: KadrePolicies.shared.Default,
            windowSceneProvider: nil,
        )
        if let failure = (result as? KadreResultFailure)?.reason {
            XCTFail("attach failed unexpectedly: \(failure)", file: file, line: line)
        }
        let success = try XCTUnwrap(result as? KadreResultSuccess<AnyObject>, file: file, line: line)
        let session = try XCTUnwrap(success.value as? (any KadreSession), file: file, line: line)
        fixture.session = session
        try fixture.waitUntilApplicationFacts(minimumRuns: runsBeforeAttach + 1, timeout: 10)
        let surface = try XCTUnwrap(fixture.primarySurface, file: file, line: line)
        return (fixture, session, surface)
    }

    /// requestStop + awaitTermination + état exporté Terminated (pattern phase 1).
    func stopAndAwaitTermination(
        _ fixture: SceneFixture,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let session = try XCTUnwrap(fixture.session, file: file, line: line)
        let terminated = expectation(description: "session terminated")
        session.requestStop()
        session.awaitTermination { outcome, error in
            XCTAssertNil(error, "awaitTermination error", file: file, line: line)
            XCTAssertNotNil(outcome, "awaitTermination delivered no outcome", file: file, line: line)
            terminated.fulfill()
        }
        wait(for: [terminated], timeout: 20)
        try fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }
    }

    func pump(_ seconds: TimeInterval) {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// L'open texte conduit côté Kotlin (la sonde reconstruit la config et répond le
    /// KadreResult brut) — retourne la session vivante. La présentation éventuelle du clavier
    /// système est un runtime behavior NON asserté (ruling Task 1) : les assertions portent
    /// sur les faits du receiver et les événements.
    func openTextSession(
        _ surface: any HostSurface,
        purpose: TextInputPurpose = .text,
        action: TextInputAction = .default_,
        capitalization: TextCapitalization = .none,
        autocorrect: Bool = true,
        multiline: Bool = false,
        surroundingText: String = "",
        selectionStart: Int32 = 0,
        selectionEnd: Int32 = 0,
        revision: Int64 = 0,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> any TextInputSession {
        let result = driver.openTextInputProbe(
            surface: surface,
            purpose: purpose,
            action: action,
            capitalization: capitalization,
            autocorrect: autocorrect,
            multiline: multiline,
            surroundingText: surroundingText,
            selectionStartUtf16: selectionStart,
            selectionEndExclusiveUtf16: selectionEnd,
            documentRevision: revision,
        )
        if let failure = (result as? KadreResultFailure)?.reason {
            XCTFail("openTextInput failed unexpectedly: \(failure)", file: file, line: line)
        }
        let success = try XCTUnwrap(result as? KadreResultSuccess<AnyObject>, file: file, line: line)
        return try XCTUnwrap(success.value as? (any TextInputSession), file: file, line: line)
    }

    /// Le receiver dans un état attendu (prédicat sur la sonde) — attente bornée, pumping du
    /// run loop (le focus et le retrait natif peuvent passer par une passe du loop).
    func awaitProbeFact(
        _ view: UIView,
        _ what: String,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (KadreUikitTextInputProbe) -> Bool,
    ) throws -> KadreUikitTextInputProbe {
        let deadline = Date().addingTimeInterval(timeout)
        var last = driver.textInputProbe(surfaceView: view)
        while !predicate(last) {
            if Date() > deadline {
                XCTFail("\(what) not reached in \(timeout)s — last probe: text='\(last.fieldText)' "
                    + "utf16=\(last.fieldUtf16Count) attached=\(last.receiverAttached) "
                    + "inHierarchy=\(last.receiverInHierarchy) windowed=\(last.receiverWindowed) "
                    + "firstResponder=\(last.receiverFirstResponder) marked=(\(last.fieldMarkedStart),"
                    + "\(last.fieldMarkedLength)) selection=(\(last.fieldSelectionStart),"
                    + "\(last.fieldSelectionEnd))", file: file, line: line)
                return last
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
            last = driver.textInputProbe(surfaceView: view)
        }
        return last
    }

    /// Amorce de la capture texte : le flow de la session est FROID (aucun replay) — un
    /// événement publié avant l'inscription de l'abonné serait perdu. L'inscription suit le
    /// départ du collecteur SANS point de suspension intermédiaire ; le test attend
    /// `isCollectAlive` PUIS pompe une fenêtre de garde — la même rigueur que `primeCapture`
    /// (phase 5). Après l'amorce, aucun événement du test ne peut plus être perdu.
    func awaitCollector(
        _ capture: KadreUikitTextInputEventCapture,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while !capture.isCollectAlive {
            if Date() > deadline {
                XCTFail("text event collector never started in \(timeout)s "
                    + "error=\(capture.collectErrorDescription ?? "none")", file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 7,
                              userInfo: ["what": "text collector never started"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
        pump(0.2)
    }

    /// Drain borné de la capture texte (le pumping du run loop EST le drain — les publications
    /// vivent sur le main, la collecte sur le dispatcher de fond). L'échec embarque les faits.
    func drainTextInput(
        _ capture: KadreUikitTextInputEventCapture,
        until predicate: @escaping (KadreUikitTextInputEventCapture) -> Bool,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while !predicate(capture) {
            if Date() > deadline {
                XCTFail("text event drain not reached in \(timeout)s — total=\(capture.totalCount) "
                    + "replace=\(capture.replaceCount) selection=\(capture.selectionCount) "
                    + "composition=\(capture.compositionCount) action=\(capture.actionCount) "
                    + "alive=\(capture.isCollectAlive) error=\(capture.collectErrorDescription ?? "none") "
                    + "recent=\(capture.recentDescriptions)", file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 5,
                              userInfo: ["what": "text event drain not reached"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// Fenêtre de quiescence bornée : pendant `window` secondes, AUCUN événement texte ne doit
    /// être publié (la preuve d'ABSENCE — un fait `Nothing` du routeur n'émet rien) et le
    /// compteur de publications du port ne doit pas bouger.
    func assertTextQuiescence(
        _ capture: KadreUikitTextInputEventCapture,
        observationsBefore: Int64,
        window: TimeInterval = 0.4,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let baseline = capture.totalCount
        let deadline = Date().addingTimeInterval(window)
        while Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            if capture.totalCount != baseline {
                XCTFail("a text event was published during the quiescence window: "
                    + "\(capture.recentDescriptions)", file: file, line: line)
                return
            }
            if driver.textObservationCount != observationsBefore {
                XCTFail("the port published during the quiescence window: "
                    + "\(driver.textObservationCount) ≠ \(observationsBefore)", file: file, line: line)
                return
            }
        }
    }

    /// L'état de la session texte dans un état attendu — attente bornée, retourne la valeur capturée.
    func awaitTextState(
        _ session: any TextInputSession,
        _ what: String,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (any TextInputState) -> Bool,
    ) throws -> (any TextInputState)? {
        let deadline = Date().addingTimeInterval(timeout)
        var last = session.state.value as? (any TextInputState)
        while true {
            if let current = last, predicate(current) { return current }
            if Date() > deadline {
                XCTFail("\(what) not reached in \(timeout)s — last=\(String(describing: last))", file: file, line: line)
                return last
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
            last = session.state.value as? (any TextInputState)
        }
    }

    /// Le reporter réel : AUCUNE observation abandonnée sur une suite verte (process-wide).
    func assertNoDroppedObservations(
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        XCTAssertEqual(driver.reportedFailureCount, 0,
                       "the real failure reporter recorded dropped work: "
                           + "\(driver.recentFailureDescriptions)",
                       file: file, line: line)
    }

    /// Le texte du champ VIVANT, journal de bord (NSLog visible dans la sortie xcodebuild).
    func traceFieldFacts(_ label: String, _ probe: KadreUikitTextInputProbe) {
        NSLog("KADRE-DRIVER text \(label): text='\(probe.fieldText)' utf16=\(probe.fieldUtf16Count) "
            + "marked=(\(probe.fieldMarkedStart),\(probe.fieldMarkedLength)) "
            + "selection=(\(probe.fieldSelectionStart),\(probe.fieldSelectionEnd)) "
            + "firstResponder=\(probe.receiverFirstResponder) attached=\(probe.receiverAttached) "
            + "class=\(probe.receiverObjCClassName)")
    }

    // MARK: - Scénarios partagés (iOS + tvOS)

    /// uikit-text-open-exclusive-rejects-second — openTextInput ×2 : le second est refusé
    /// `AlreadyInUse(TextInputSession)` (l'admission du runtime — le CAS du port reste la
    /// défense en profondeur), sans installer de second receiver ; le premier reste Active,
    /// son receiver vivant, focus, invisible (alpha 0, PAS isHidden).
    func testUikitTextOpenExclusiveRejectsSecond() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let installsBefore = driver.textInstallCount
        let teardownsBefore = driver.textTeardownCount

        let textSession = try openTextSession(surface)
        defer { textSession.close() }

        // Le receiver du premier open : posé, dans la hiérarchie, windowé, focus, alpha 0.
        let probe = try awaitProbeFact(view, "receiver focus") { $0.receiverAttached && $0.receiverFirstResponder }
        traceFieldFacts("open-exclusive", probe)
        XCTAssertTrue(probe.receiverInHierarchy, "the receiver must live in the host hierarchy")
        XCTAssertTrue(probe.receiverWindowed, "the receiver must be addressable (window != nil)")
        XCTAssertEqual(probe.receiverAlpha, 0.0, accuracy: 0.001,
                       "the receiver is invisible by ALPHA 0, never isHidden (a hidden view cannot become first responder)")
        XCTAssertNotEqual(probe.receiverObjCClassName, "", "the receiver Obj-C class must be exported")
        XCTAssertEqual(driver.textInstallCount, installsBefore + 1, "exactly one install for the admitted open")

        // Le second open PENDANT la session vivante : AlreadyInUse(TextInputSession), zéro install.
        let second = driver.openTextInputProbe(
            surface: surface,
            purpose: .text,
            action: .default_,
            capitalization: .none,
            autocorrect: true,
            multiline: false,
            surroundingText: "",
            selectionStartUtf16: 0,
            selectionEndExclusiveUtf16: 0,
            documentRevision: 0,
        )
        guard let reason = (second as? KadreResultFailure)?.reason else {
            return XCTFail("the second open must fail, got success")
        }
        guard let already = reason as? KadreFailureAlreadyInUse else {
            return XCTFail("expected AlreadyInUse(TextInputSession), got \(reason)")
        }
        XCTAssertTrue(already.resource === KadreResourceKind.textinputsession,
                      "the refused resource is the text input session, got \(already.resource)")
        XCTAssertEqual(driver.textInstallCount, installsBefore + 1,
                       "the refused open must not install a second receiver")

        // Le premier reste Active à sa révision d'origine, sans aucun événement publié.
        let active = try awaitTextState(textSession, "first session Active") { $0 is TextInputStateActive }
        let activeState = try XCTUnwrap(active as? TextInputStateActive)
        XCTAssertEqual(activeState.documentRevision, 0, "the first session keeps its revision")
        let capture = driver.captureTextInputEvents(session: textSession)
        defer { capture.stop() }
        try awaitCollector(capture)
        try assertTextQuiescence(capture, observationsBefore: driver.textObservationCount)
        XCTAssertEqual(capture.totalCount, 0, "an open publishes nothing")

        assertNoDroppedObservations()
        textSession.close()
        try awaitProbeFact(view, "receiver withdrawn after close", timeout: 5) { !$0.receiverAttached }
        XCTAssertEqual(driver.textTeardownCount, teardownsBefore + 1, "the close tears the owner down")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-text-replace-mapped-from-ukit-range — le Replace mappé depuis la PLAGE UIKit :
    /// (1) la route UTILISATEUR conduite — le hook propre de l'adaptateur
    /// `shouldChangeCharactersInRange((0,0), "ab")` (la plage qu'UIKit nomme pour une insertion
    /// au caret d'un document vide) pose le candidat résolu contre l'ombre, la mutation du
    /// champ est appliquée par le stimulus programmatique qui suit (l'ordre UIKit), et
    /// l'`.editingChanged` commite : Replace((0,0), "ab") publié, drainé par la session, champ
    /// et ombre en accord ; (2) la route programmatique PURE — `insertText:` SANS hook ne
    /// publie RIEN (le verdict `Nothing` du routeur : un fait non attribué est une
    /// non-observation, la route champ→Kotlin est routée pour la saisie utilisateur seule) ;
    /// (3) le write-back Kotlin → champ réconcilie la révision du runtime.
    func testUikitTextReplaceMappedFromUikitRange() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let observationsBefore = driver.textObservationCount
        let textSession = try openTextSession(surface)
        defer { textSession.close() }
        let capture = driver.captureTextInputEvents(session: textSession)
        defer { capture.stop() }
        try awaitCollector(capture)

        // (1) La route utilisateur conduite : hook (0,0) + "ab", puis la même mutation du champ.
        XCTAssertTrue(driver.receiverDriveShouldChange(surfaceView: view, location: 0, length: 0, replacement: "ab"),
                      "the adapter's own shouldChange hook must be live on the discovered receiver")
        XCTAssertTrue(driver.receiverInsertText(surfaceView: view, text: "ab"))
        try drainTextInput(capture) { $0.replaceCount >= 1 }
        XCTAssertEqual(capture.replaceCount, 1, "exactly one Replace is published for the routed edit")
        XCTAssertEqual(driver.textObservationCount, observationsBefore + 1,
                       "the port published exactly one observation")
        // La PLAGE du Replace est celle nommée par UIKit, résolue contre l'ombre : (0,0) + "ab".
        XCTAssertEqual(capture.lastReplaceRangeStart, 0, "the Replace range start is the UIKit range location")
        XCTAssertEqual(capture.lastReplaceRangeEndExclusive, 0, "the Replace range end is the UIKit range end")
        XCTAssertEqual(capture.lastReplaceText, "ab", "the Replace text is the replacement")
        XCTAssertEqual(capture.lastReplaceBaseRevision, 0,
                       "the observation is stamped with the revision the runtime still accepts")
        var probe = try awaitProbeFact(view, "field text after routed insert") { $0.fieldText == "ab" }
        traceFieldFacts("replace-routed", probe)
        XCTAssertEqual(probe.fieldUtf16Count, 2)

        // (2) La route programmatique PURE : insertText SANS hook — le champ applique (fait
        // réel du responder), le routeur répond `Nothing` (pas de candidat) : AUCUN fait.
        XCTAssertTrue(driver.receiverInsertText(surfaceView: view, text: "ab"))
        XCTAssertTrue(driver.receiverDeleteBackward(surfaceView: view))
        XCTAssertTrue(driver.receiverDeleteBackward(surfaceView: view))
        probe = try awaitProbeFact(view, "field text restored after unattributed edits") { $0.fieldText == "ab" }
        traceFieldFacts("replace-unattributed", probe)
        try assertTextQuiescence(capture, observationsBefore: observationsBefore + 1)
        XCTAssertEqual(capture.totalCount, 1,
                       "the unattributed programmatic edits publish NOTHING (delivered router semantics)")

        // (3) Le write-back : la direction Kotlin → champ, la révision du runtime avance.
        let wb = driver.updateSurroundingTextProbe(
            session: textSession,
            text: "ab",
            selectionStartUtf16: 2,
            selectionEndExclusiveUtf16: 2,
            documentRevision: 1,
        )
        XCTAssertFalse(wb is KadreResultFailure, "the write-back must be applied, got \(String(describing: (wb as? KadreResultFailure)?.reason))")
        probe = try awaitProbeFact(view, "field text after write-back") { $0.fieldText == "ab" }
        XCTAssertEqual(probe.fieldSelectionStart, 2, "the write-back carries the runtime selection")
        XCTAssertEqual(probe.fieldSelectionEnd, 2)
        let state = try awaitTextState(textSession, "session Active after write-back") { $0 is TextInputStateActive }
        XCTAssertEqual((state as? TextInputStateActive)?.documentRevision, 1,
                       "the runtime revision advanced to the write-back revision")
        try assertTextQuiescence(capture, observationsBefore: observationsBefore + 1,
                                 window: 0.3)
        XCTAssertEqual(capture.totalCount, 1, "a write-back publishes no observation (it IS the runtime's own document)")

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-text-delete-grapheme-honest — la suppression honnête du graphème : (1) le champ
    /// portant "ab😀" (4 unités UTF-16), UN `deleteBackward` programmatique enlève la PAIRE
    /// surrogate ENTIÈRE (la frontière nommée par UIKit, preuve spike refaite sur le receiver
    /// vivant) — et ce fait NON attribué ne publie RIEN (verdict `Nothing` : la suppression
    /// programmatique est inobservée par délivrance, le canal utilisateur passe par le hook) ;
    /// (2) la route utilisateur conduite — le hook porte la plage qu'UIKit nomme pour CE backspace
    /// (location 2, length 2 : la paire entière, fait spike) et le Replace publié porte la PAIRE
    /// ENTIÈRE (2,4)+"" timbrée de la révision du runtime ; (3) champ et ombre restent en accord.
    func testUikitTextDeleteGraphemeHonest() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let observationsBefore = driver.textObservationCount
        let textSession = try openTextSession(surface)
        defer { textSession.close() }
        let capture = driver.captureTextInputEvents(session: textSession)
        defer { capture.stop() }
        try awaitCollector(capture)

        // "ab😀" écrit par la direction runtime → champ (le write-back EST le contrat).
        let seed = driver.updateSurroundingTextProbe(
            session: textSession,
            text: "ab😀",
            selectionStartUtf16: 4,
            selectionEndExclusiveUtf16: 4,
            documentRevision: 1,
        )
        XCTAssertFalse(seed is KadreResultFailure, "the seed write-back must be applied, got \(String(describing: (seed as? KadreResultFailure)?.reason))")
        var probe = try awaitProbeFact(view, "seed field text") { $0.fieldUtf16Count == 4 }
        traceFieldFacts("delete-seeded", probe)

        // (1) Le deleteBackward programmatique pur : la paire entière part du champ, RIEN n'est publié.
        XCTAssertTrue(driver.receiverDeleteBackward(surfaceView: view))
        probe = try awaitProbeFact(view, "surrogate pair removal") { $0.fieldUtf16Count == 2 && $0.fieldText == "ab" }
        traceFieldFacts("delete-unattributed", probe)
        try assertTextQuiescence(capture, observationsBefore: observationsBefore)
        XCTAssertEqual(capture.totalCount, 0,
                       "the unattributed programmatic deletion publishes NOTHING (delivered router semantics)")
        let stateAfterUnattributed = try awaitTextState(textSession, "state after unattributed delete") { $0 is TextInputStateActive }
        XCTAssertEqual((stateAfterUnattributed as? TextInputStateActive)?.documentRevision, 1,
                       "the runtime document is untouched by an unobserved edit")

        // (2) La route utilisateur conduite : le runtime restaure "ab😀" (rev 2), le hook porte
        // la plage d'UIKit pour CE backspace (2,2 → la paire entière, fait spike), le champ
        // applique par le même deleteBackward programmatique (une passe enlève la paire).
        let restore = driver.updateSurroundingTextProbe(
            session: textSession,
            text: "ab😀",
            selectionStartUtf16: 4,
            selectionEndExclusiveUtf16: 4,
            documentRevision: 2,
        )
        XCTAssertFalse(restore is KadreResultFailure, "the restore write-back must be applied")
        probe = try awaitProbeFact(view, "restored field text") { $0.fieldUtf16Count == 4 }
        XCTAssertTrue(driver.receiverDriveShouldChange(surfaceView: view, location: 2, length: 2, replacement: ""),
                      "the adapter's own shouldChange hook must accept the grapheme range")
        XCTAssertTrue(driver.receiverDeleteBackward(surfaceView: view))
        try drainTextInput(capture) { $0.replaceCount >= 1 }
        // Le Replace porte la PAIRE SURROGATE ENTIÈRE — la plage UIKit honnête (le contrat
        // honest-UIKit : UIKit nomme la frontière de graphème), timbrée de la révision runtime.
        XCTAssertEqual(capture.lastReplaceRangeStart, 2, "the Replace range starts at the pair")
        XCTAssertEqual(capture.lastReplaceRangeEndExclusive, 4, "the Replace range spans the WHOLE surrogate pair")
        XCTAssertEqual(capture.lastReplaceText, "", "the replacement of a deletion is empty")
        XCTAssertEqual(capture.lastReplaceBaseRevision, 2, "the observation carries the runtime revision")
        XCTAssertEqual(capture.totalCount, 1, "exactly one observation for the routed deletion")
        probe = try awaitProbeFact(view, "field after routed delete") { $0.fieldUtf16Count == 2 && $0.fieldText == "ab" }
        traceFieldFacts("delete-routed", probe)
        XCTAssertEqual(probe.fieldText, "ab", "field and shadow stay in accord after the routed deletion")
        let state = try awaitTextState(textSession, "state after routed delete") { $0 is TextInputStateActive }
        XCTAssertEqual((state as? TextInputStateActive)?.documentRevision, 2)

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-text-stale-revision-refused — `updateSurroundingText` à révision PÉRIMÉE répond
    /// `StaleRevision(expected, received)`, le champ et le document du runtime restent à
    /// l'état accepté, AUCUN fait n'est publié.
    func testUikitTextStaleRevisionRefused() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let textSession = try openTextSession(surface)
        defer { textSession.close() }
        let capture = driver.captureTextInputEvents(session: textSession)
        defer { capture.stop() }
        try awaitCollector(capture)

        // Le document du runtime avance " → "ab" à la révision 5.
        let advance = driver.updateSurroundingTextProbe(
            session: textSession,
            text: "ab",
            selectionStartUtf16: 2,
            selectionEndExclusiveUtf16: 2,
            documentRevision: 5,
        )
        XCTAssertFalse(advance is KadreResultFailure, "the advance write-back must be applied")
        _ = try awaitProbeFact(view, "field text at revision 5") { $0.fieldText == "ab" }

        // La révision périmée (3 < 5) : refus typé StaleRevision(5, 3).
        let stale = driver.updateSurroundingTextProbe(
            session: textSession,
            text: "abc",
            selectionStartUtf16: 3,
            selectionEndExclusiveUtf16: 3,
            documentRevision: 3,
        )
        guard let reason = (stale as? KadreResultFailure)?.reason else {
            return XCTFail("the stale write-back must be refused, got success")
        }
        guard let staleRevision = reason as? KadreFailureStaleRevision else {
            return XCTFail("expected StaleRevision, got \(reason)")
        }
        XCTAssertEqual(staleRevision.expected, 5, "the expected revision is the accepted one")
        XCTAssertEqual(staleRevision.received, 3, "the received revision is the stale one")

        // Le refus ne touche NI le champ NI le document : "ab", révision 5, aucun événement.
        let probe = try awaitProbeFact(view, "field unchanged after refusal") { $0.fieldText == "ab" }
        traceFieldFacts("stale-refused", probe)
        let state = try awaitTextState(textSession, "state after refusal") { $0 is TextInputStateActive }
        XCTAssertEqual((state as? TextInputStateActive)?.documentRevision, 5,
                       "the runtime keeps the accepted revision")
        try assertTextQuiescence(capture, observationsBefore: driver.textObservationCount)
        XCTAssertEqual(capture.totalCount, 0, "neither write-backs nor refusals publish observations")

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-text-focus-suspend-resume — le focus programmatique du receiver : resign →
    /// `textFieldDidEndEditing` (le compteur du port bouge, le champ n'est plus first
    /// responder), re-focus → `textFieldDidBeginEditing` ; la session reste Active aux deux
    /// états. SÉMANTIQUE DÉLIVRÉE (documentée au rapport) : le couplage receiver→Suspended
    /// N'EXISTE PAS côté UIKit — la suspension/resume runtime n'est branchée que sur
    /// `SurfaceStimulus.FocusChanged`, et le snapshot UIKit ne publie JAMAIS Focused
    /// (« UIKit n'a pas de focus surface » — la valeur reste Unfocused) ; la perte de focus du
    /// receiver ne fait QUE compter (Task 2, hooks onFirstResponderGained/Lost). Les legs
    /// Suspended/Active sont portés par le charter (vrai clavier détaché) / les familles qui
    /// branchent le couplage ; ICI la preuve honnête est le comptage des hooks + l'état Active
    /// stable + l'absence de tout fait.
    func testUikitTextFocusSuspendResume() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let textSession = try openTextSession(surface)
        defer { textSession.close() }
        let capture = driver.captureTextInputEvents(session: textSession)
        defer { capture.stop() }
        try awaitCollector(capture)

        // La vérité de la surface : UIKit ne publie jamais Focused — le couplage runtime
        // suspend/resume ne peut pas partir du focus surface côté UIKit.
        let surfaceState = try XCTUnwrap(fixture.primarySurface?.state.value as? SurfaceState)
        XCTAssertTrue(surfaceState.focus === SurfaceFocus.unfocused,
                      "the UIKit surface focus stays Unfocused by delivery — the runtime suspend coupling is receiver-independent here")

        _ = try awaitProbeFact(view, "receiver focused after open") { $0.receiverFirstResponder }
        let gainedBefore = driver.textFocusGainedCount
        let lostBefore = driver.textFocusLostCount
        XCTAssertGreaterThanOrEqual(gainedBefore, 1, "the open focused the receiver (textFieldDidBeginEditing counted)")

        // resign → le champ perd le first responder, le hook de perte est compté.
        XCTAssertTrue(driver.receiverResignFocus(surfaceView: view), "the resign must succeed")
        var probe = try awaitProbeFact(view, "receiver resigned") { !$0.receiverFirstResponder }
        traceFieldFacts("focus-resigned", probe)
        XCTAssertEqual(driver.textFocusLostCount, lostBefore + 1, "the focus loss is counted by the port")
        var state = try awaitTextState(textSession, "state after resign") { $0 is TextInputStateActive }
        XCTAssertTrue(state is TextInputStateActive,
                      "delivered semantics: the receiver resign does NOT suspend the session on UIKit (no receiver→suspend coupling)")
        try assertTextQuiescence(capture, observationsBefore: driver.textObservationCount)

        // re-focus → le champ redevient first responder, le hook de gain est compté.
        XCTAssertTrue(driver.receiverRefocus(surfaceView: view), "the re-focus must succeed")
        probe = try awaitProbeFact(view, "receiver refocused") { $0.receiverFirstResponder }
        traceFieldFacts("focus-regained", probe)
        XCTAssertEqual(driver.textFocusGainedCount, gainedBefore + 1, "the focus gain is counted by the port")
        state = try awaitTextState(textSession, "state after refocus") { $0 is TextInputStateActive }
        XCTAssertTrue(state is TextInputStateActive)
        try assertTextQuiescence(capture, observationsBefore: driver.textObservationCount)
        XCTAssertEqual(capture.totalCount, 0, "focus cycles publish no text event")

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-text-close-no-orphan-composition — le close PENDANT une composition (marqué
    /// programmatique vivant) : la session répond Closed, le receiver est RETIRÉ de la
    /// superview (plus aucun receiver Kadre sous la vue du host), AUCUN fait ne survit au
    /// close (garde de publication), la ré-ouverture après close est admise (le CAS du port
    /// est rendu). L'état marqué du champ vivait AU close (asserté avant) ; après le retrait
    /// le champ est HORS d'atteinte du driver (plus découvrable sous la vue) — l'absence
    /// d'orphelin CÔTÉ RUNTIME est l'état Closed + zéro événement (le rapport Task 3
    /// documente la part de champ couverte par le retrait).
    func testUikitTextCloseNoOrphanComposition() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let observationsBefore = driver.textObservationCount
        let textSession = try openTextSession(surface)
        let capture = driver.captureTextInputEvents(session: textSession)
        defer { capture.stop() }
        try awaitCollector(capture)

        // La composition vivante : setMarkedText programmatique (le fait spike (a), refait sur
        // le receiver vivant) — le champ porte le marqué, rien n'est publié (le fait de la
        // composition n'est jamais une observation de sa propre cuisine — règle web).
        XCTAssertTrue(driver.receiverSetMarkedText(surfaceView: view, markedText: "é", selectedLocation: 0, selectedLength: 0),
                      "the programmatic marked text must land on the discovered receiver")
        var probe = try awaitProbeFact(view, "marked state") { $0.fieldMarkedLength == 1 }
        traceFieldFacts("close-marked", probe)
        XCTAssertEqual(probe.fieldText, "é", "the marked text shows in the field")
        XCTAssertEqual(probe.fieldMarkedStart, 0, "the marked range starts at 0")
        try assertTextQuiescence(capture, observationsBefore: observationsBefore)

        // Le close pendant le marqué : session Closed, receiver retiré, zéro fait.
        textSession.close()
        probe = try awaitProbeFact(view, "receiver removed after close", timeout: 5) { !$0.receiverAttached }
        traceFieldFacts("close-done", probe)
        XCTAssertTrue(probe.receiverRemovedFromSuperview || !probe.receiverAttached,
                      "no Kadre receiver survives under the host view after the close")
        let state = try awaitTextState(textSession, "session Closed") { $0 is TextInputStateClosed }
        XCTAssertTrue(state is TextInputStateClosed, "the session must be Closed after close(): \(String(describing: state))")
        try assertTextQuiescence(capture, observationsBefore: observationsBefore)
        XCTAssertEqual(capture.totalCount, 0,
                       "no observation ever leaves this session: the composition never publishes and nothing survives the close")

        // Le port rend son CAS : une ré-ouverture est admise, un NOUVEAU receiver est posé.
        let reopened = try openTextSession(surface)
        defer { reopened.close() }
        let reopenProbe = try awaitProbeFact(view, "reopened receiver") { $0.receiverAttached && $0.receiverFirstResponder }
        traceFieldFacts("close-reopened", reopenProbe)
        XCTAssertNotEqual(reopenProbe.fieldText, "é", "the reopened field starts from its own config, no orphan mark")
        XCTAssertEqual(reopenProbe.fieldMarkedLength, -1, "the reopened receiver carries no marked range")

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-text-actions-mapped — la touche retour mappée : la conduite du hook propre
    /// `textFieldShouldReturn` (le port EST le delegate — appel unitaire du routage de
    /// l'adaptateur, documenté) publie `Action(config.action)` — drainé par la session avec la
    /// révision du runtime, exactement UN fait.
    func testUikitTextActionsMapped() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let observationsBefore = driver.textObservationCount
        let textSession = try openTextSession(surface, action: .done)
        defer { textSession.close() }
        let capture = driver.captureTextInputEvents(session: textSession)
        defer { capture.stop() }
        try awaitCollector(capture)

        XCTAssertTrue(driver.receiverDriveShouldReturn(surfaceView: view),
                      "the adapter's own return hook must be live on the discovered receiver")
        try drainTextInput(capture) { $0.actionCount >= 1 }
        XCTAssertEqual(capture.actionCount, 1, "exactly one Action is published")
        XCTAssertEqual(capture.totalCount, 1, "the return publishes nothing else")
        XCTAssertEqual(capture.lastActionName, "Done", "the action is the CONFIG action (Done), not a literal key")
        XCTAssertEqual(capture.lastActionBaseRevision, 0, "the Action carries the runtime revision")
        XCTAssertEqual(driver.textObservationCount, observationsBefore + 1)
        let state = try awaitTextState(textSession, "state after action") { $0 is TextInputStateActive }
        XCTAssertTrue(state is TextInputStateActive, "an Action closes nothing — the session stays Active")

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }

    /// Le mapping de config Task 2 vérifié À L'EXÉCUTION sur le receiver vivant : Email →
    /// clavier email + retour Done, Password → entrée sécurisée (les brutes lues SUR LE CHAMP,
    /// comparées aux constantes UIKit vivantes de la plateforme de test).
    func testUikitTextConfigMappedOntoTheReceiver() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let teardownsBefore = driver.textTeardownCount

        // Email / Done : clavier email (7), retour Done (9), saisie NON sécurisée.
        let email = try openTextSession(surface, purpose: .email, action: .done)
        var probe = try awaitProbeFact(view, "email receiver") { $0.receiverAttached && $0.receiverFirstResponder }
        traceFieldFacts("config-email", probe)
        XCTAssertEqual(probe.keyboardTypeRaw, Int64(UIKeyboardType.emailAddress.rawValue),
                       "Email purpose maps to the email keyboard, got raw \(probe.keyboardTypeRaw)")
        XCTAssertEqual(probe.returnKeyTypeRaw, Int64(UIReturnKeyType.done.rawValue),
                       "Done action maps to the Done return key, got raw \(probe.returnKeyTypeRaw)")
        XCTAssertFalse(probe.secureTextEntry, "Email is not a secure entry")
        email.close()
        _ = try awaitProbeFact(view, "email receiver withdrawn", timeout: 5) { !$0.receiverAttached }

        // Password / Default : entrée SÉCURISÉE, clavier par défaut.
        let password = try openTextSession(surface, purpose: .password)
        probe = try awaitProbeFact(view, "password receiver") { $0.receiverAttached && $0.receiverFirstResponder }
        traceFieldFacts("config-password", probe)
        XCTAssertTrue(probe.secureTextEntry, "Password purpose must set secureTextEntry")
        XCTAssertEqual(probe.keyboardTypeRaw, Int64(UIKeyboardType.default.rawValue),
                       "Password keeps the default keyboard, got raw \(probe.keyboardTypeRaw)")
        password.close()
        _ = try awaitProbeFact(view, "password receiver withdrawn", timeout: 5) { !$0.receiverAttached }
        XCTAssertEqual(driver.textTeardownCount, teardownsBefore + 2, "both closes tore their owner down")

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }
}
