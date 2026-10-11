import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves O3 de `KadreIos.attach` (phase 1, BCK-013 / BCK-014 / WIN-009) — noms de scénarios FIGÉS
/// pour Task 6. Les tests partagés tournent sur LES DEUX familles (iOS + tvOS, topologies
/// mono-scène) ; les scénarios multi-scènes vivent dans l'extension iOS-only
/// (`tests/Ios/KadreUikitAttachIosTests.swift`, compilée uniquement dans la cible de tests iOS).
final class KadreUikitAttachTests: XCTestCase {

    /// Filet de sécurité ENTRE tests : toute session encore Starting/Running est arrêtée et
    /// attendue (borné). Sans cela, un test qui échoue à mi-chemin laisse la réservation de
    /// scène posée et les tests SUIVANTS échouent en cascade (`AlreadyInUse(Host)`) — le test
    /// fautif doit rester le seul à porter sa défaillance.
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

    // MARK: - Helpers (internal : l'extension multi-scènes iOS les réutilise)

    func primaryFixture() throws -> SceneFixture {
        // La scène primaire peut n'être connectée qu'après le lancement (froid) de l'app hôte :
        // attendre SON EXISTENCE de façon bornée avant sa disponibilité — jamais un échec
        // instantané sur un process encore en train de démarrer.
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

    /// attach brut (résultat exporté, sans unwrap) — pour les refus attendus.
    func attachRaw(
        fixture: SceneFixture,
        window: UIWindow,
        view: UIView,
    ) -> any KadreResult {
        KadreIos.shared.attach(
            windowScene: fixture.scene,
            window: window,
            surfaceView: view,
            applicationFactory: DriverApplication(fixture: fixture),
            policy: KadrePolicies.shared.Default,
        )
    }

    /// attach nominal : retourne la session exportée et l'enregistre dans le fixture.
    @discardableResult
    func attach(_ fixture: SceneFixture, file: StaticString = #filePath, line: UInt = #line) throws -> any KadreSession {
        let window = try XCTUnwrap(fixture.window, file: file, line: line)
        let view = try XCTUnwrap(fixture.observedView, file: file, line: line)
        let runsBeforeAttach = fixture.applicationRuns
        let result = attachRaw(fixture: fixture, window: window, view: view)
        if let failure = (result as? KadreResultFailure)?.reason {
            XCTFail("attach failed unexpectedly: \(failure)", file: file, line: line)
        }
        let success = try XCTUnwrap(result as? KadreResultSuccess<AnyObject>, file: file, line: line)
        let session = try XCTUnwrap(success.value as? (any KadreSession), file: file, line: line)
        fixture.session = session
        // Les faits attendus sont CEUX DE CETTE session — jamais un reliquat d'un attach
        // précédent sur le même fixture (voir SceneFixture.waitUntilApplicationFacts).
        try fixture.waitUntilApplicationFacts(minimumRuns: runsBeforeAttach + 1, timeout: 10)
        return session
    }

    /// assertInvalidRequest : échec exporté `InvalidRequest(field)`.
    func assertInvalidRequest(
        _ result: any KadreResult,
        field: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        guard let failure = (result as? KadreResultFailure)?.reason else {
            XCTFail("expected KadreResultFailure, got \(result)", file: file, line: line)
            return
        }
        guard let invalid = failure as? KadreFailureInvalidRequest else {
            XCTFail("expected InvalidRequest, got \(failure)", file: file, line: line)
            return
        }
        XCTAssertEqual(invalid.field, field, "rejected field mismatch", file: file, line: line)
    }

    /// requestStop + awaitTermination (expectation XCTest — awaitTermination est suspend),
    /// puis attente bornée de l'état exporté Terminated.
    func stopAndAwaitTermination(
        _ fixture: SceneFixture,
        file: StaticString = #filePath,
        line: UInt = #line,
        outcomeCheck: ((any SessionOutcome) -> Void)? = nil,
    ) throws {
        let session = try XCTUnwrap(fixture.session, file: file, line: line)
        let terminated = expectation(description: "session terminated")
        session.requestStop()
        session.awaitTermination { outcome, error in
            XCTAssertNil(error, "awaitTermination error", file: file, line: line)
            XCTAssertNotNil(outcome, "awaitTermination delivered no outcome", file: file, line: line)
            if let outcome { outcomeCheck?(outcome) }
            terminated.fulfill()
        }
        wait(for: [terminated], timeout: 20)
        try fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }
    }

    /// Attente bornée d'un snapshot de lifecycle Kadre exact (pumping, aucun sommeil nu).
    func assertLifecycle(
        _ fixture: SceneFixture,
        visibility: VisibilityState,
        activation: ActivationState,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(5)
        while true {
            if let snapshot = fixture.lifecycleSnapshot(),
               snapshot.visibility === visibility,
               snapshot.activation === activation {
                return
            }
            if Date() > deadline {
                XCTFail("lifecycle not reached (want \(visibility)+\(activation)): "
                    + String(describing: fixture.lifecycleSnapshot()), file: file, line: line)
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    // MARK: - Scénarios partagés (iOS + tvOS)

    /// BCK-013 / uikit-attach-surfaceview-mismatch — une vue hors de la fenêtre fournie
    /// est refusée `InvalidRequest("surfaceView")`, sans réservation ni session.
    func testAttachRejectsViewOutsideTheProvidedWindow() throws {
        let fixture = try primaryFixture()
        let window = try XCTUnwrap(fixture.window)
        let detached = try XCTUnwrap(fixture.detachedView)

        let result = attachRaw(fixture: fixture, window: window, view: detached)

        assertInvalidRequest(result, field: "surfaceView")
        XCTAssertNil(fixture.session, "a refused attach must not leave a session")
    }

    /// BCK-013 / uikit-attach-scene-busy — le second attach sur la même scène est refusé
    /// `AlreadyInUse(Host)` ; la réservation échoue AVANT toute installation : aucun
    /// observer ni application en plus (compteur du fixture), la première session marche
    /// toujours (état inchangé).
    func testSecondAttachOnTheSameSceneIsAlreadyInUse() throws {
        let fixture = try primaryFixture()
        let first = try attach(fixture)
        let runsBefore = fixture.applicationRuns
        let firstSessionId = first.id.description

        let window = try XCTUnwrap(fixture.window)
        let view = try XCTUnwrap(fixture.observedView)
        let second = attachRaw(fixture: fixture, window: window, view: view)

        guard let failure = (second as? KadreResultFailure)?.reason else {
            return XCTFail("expected KadreResultFailure, got \(second)")
        }
        guard let inUse = failure as? KadreFailureAlreadyInUse else {
            return XCTFail("expected AlreadyInUse, got \(failure)")
        }
        XCTAssertTrue(inUse.resource === KadreResourceKind.host, "expected AlreadyInUse(Host)")

        // Aucune installation supplémentaire : pas de nouvelle application, pas d'observer.
        RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        XCTAssertEqual(fixture.applicationRuns, runsBefore, "no second application may install")
        // La première session marche toujours : état et identité inchangés.
        try fixture.waitUntilSessionState(timeout: 5) { $0 is SessionStateRunning }
        XCTAssertEqual(first.id.description, firstSessionId)

        try stopAndAwaitTermination(fixture)
    }

    /// WIN-009 / uikit-primary-is-provided-window + uikit-primary-surface-same-instance —
    /// la surface primaire EST la surface de la fenêtre primaire (même id, même instance
    /// `===`) et les métriques initiales valent la lecture Swift des bounds de la vue.
    func testPrimaryWindowAndSurfaceIdentity() throws {
        let fixture = try primaryFixture()
        _ = try attach(fixture)

        XCTAssertNotNil(fixture.primarySurfaceId, "primary surface id must be captured")
        // `isEqual` — les descriptions sont rédigées par Kotlin, l'égalité de chaînes serait vide.
        XCTAssertTrue(fixture.primarySurfaceIdMatchesWindowSurfaceId,
                      "primarySurface.id must isEqual the primary window surface's id")
        XCTAssertTrue(fixture.primarySurfaceIsPrimaryWindowSurface,
                      "scope.primarySurface.value must be the primary window's surface instance")

        let view = try XCTUnwrap(fixture.observedView)
        XCTAssertEqual(try XCTUnwrap(fixture.primaryWindowContentWidth), Double(view.bounds.width), accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(fixture.primaryWindowContentHeight), Double(view.bounds.height), accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(fixture.primarySurfaceLogicalWidth), Double(view.bounds.width), accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(fixture.primarySurfaceLogicalHeight), Double(view.bounds.height), accuracy: 0.001)

        try stopAndAwaitTermination(fixture)
    }

    /// BCK-013 / uikit-stop-then-reattach — requestStop IMMÉDIAT après attach (arrêt pendant
    /// Starting), awaitTermination livre un arrêt normal, et un nouvel attach sur la MÊME
    /// scène réussit (réservation libérée par la terminaison).
    func testStopThenReattachOnTheSameScene() throws {
        let fixture = try primaryFixture()
        let first = try attach(fixture)

        // requestStop pendant Starting : la startup est annulée avant tout démarrage.
        let terminated = expectation(description: "first session terminated")
        first.requestStop()
        first.awaitTermination { outcome, error in
            XCTAssertNil(error)
            XCTAssertNotNil(outcome)
            // Un arrêt normal du host — pas Failed(ShutdownTimedOut).
            XCTAssertTrue(outcome is SessionOutcomeStopped, "outcome=\(String(describing: outcome))")
            terminated.fulfill()
        }
        wait(for: [terminated], timeout: 20)
        try fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }

        // La réservation est libérée : re-attach sur la MÊME scène.
        fixture.session = nil
        let second = try attach(fixture)
        XCTAssertFalse(second.id.isEqual(first.id), "the re-attach must be a new session")
        try fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateRunning }

        try stopAndAwaitTermination(fixture)
    }

    /// BCK-013 / uikit-disconnect-during-stop (LES DEUX familles) — requestStop puis
    /// DidDisconnect posté IMMÉDIATEMENT (même passe du run loop) : exactement UN terminal
    /// (Stopped(HostRequested) — le disconnect tardif est ignoré), pas de crash, et un
    /// nouvel attach fonctionne ensuite.
    func testDisconnectDuringStopTerminatesExactlyOnce() throws {
        let fixture = try primaryFixture()
        let session = try attach(fixture)

        let terminals = expectation(description: "exactly one terminal outcome")
        terminals.assertForOverFulfill = true
        session.requestStop()
        // Même passe du run loop : la notification arrive sur des observers déjà fermés
        // par la révocation du stop — routée en callback tardif, jamais en double arrêt.
        NotificationCenter.default.post(name: UIScene.didDisconnectNotification, object: fixture.scene)
        session.awaitTermination { outcome, error in
            XCTAssertNil(error)
            XCTAssertTrue(outcome is SessionOutcomeStopped,
                          "exactly one terminal, a normal stop — got \(String(describing: outcome))")
            if let stopped = outcome as? SessionOutcomeStopped {
                XCTAssertTrue(stopped.reason === SessionStopReason.hostrequested,
                              "the stop outcome must be the host-requested one")
            }
            terminals.fulfill()
        }
        wait(for: [terminals], timeout: 20)
        try fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }

        // Le nouvel attach fonctionne (réservation libérée exactement une fois).
        fixture.session = nil
        _ = try attach(fixture)
        try stopAndAwaitTermination(fixture)
    }

    /// BCK-013 / uikit-lifecycle-transitions (tvOS + iOS) — les notifications de scène RÉELLES
    /// conduisent le lifecycle Kadre : DidEnterBackground → Background+Inactive ;
    /// WillEnterForeground+DidActivate → Foreground+Active ; WillDeactivate → Inactive.
    func testLifecycleTransitionsFollowTheScene() throws {
        let fixture = try primaryFixture()
        _ = try attach(fixture)
        let scene = fixture.scene

        NotificationCenter.default.post(name: UIScene.didEnterBackgroundNotification, object: scene)
        try assertLifecycle(fixture, visibility: .background, activation: .inactive)

        NotificationCenter.default.post(name: UIScene.willEnterForegroundNotification, object: scene)
        NotificationCenter.default.post(name: UIScene.didActivateNotification, object: scene)
        try assertLifecycle(fixture, visibility: .foreground, activation: .active)

        NotificationCenter.default.post(name: UIScene.willDeactivateNotification, object: scene)
        try assertLifecycle(fixture, visibility: .foreground, activation: .inactive)

        try stopAndAwaitTermination(fixture)
    }

    /// BCK-013 / uikit-late-callback-after-termination — terminer puis poster DidActivate ×3 :
    /// pas de crash, la session reste Terminated et le compteur proxy de callbacks tardifs du
    /// fixture (dérives du lifecycle gelé) vaut zéro.
    func testLateNativeCallbackAfterTerminationIsIgnored() throws {
        let fixture = try primaryFixture()
        let session = try attach(fixture)

        try stopAndAwaitTermination(fixture)
        fixture.freezeLifecycleAtTermination()
        let runsAtTermination = fixture.applicationRuns

        for _ in 0..<3 {
            NotificationCenter.default.post(name: UIScene.didActivateNotification, object: fixture.scene)
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            fixture.probeLifecycleAfterTermination()
        }

        XCTAssertEqual(fixture.lateCallbackProxyCount, 0, "no late callback may touch a terminated session")
        try fixture.waitUntilSessionState(timeout: 2) { $0 is SessionStateTerminated }
        XCTAssertEqual(fixture.applicationRuns, runsAtTermination, "no new application may run")
    }
}
