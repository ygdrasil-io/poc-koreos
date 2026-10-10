import XCTest
import UIKit
import KadreUikit
@testable import KadreUikitDriverIos

/// Scénarios multi-scènes (iOS UNIQUEMENT) — ce fichier n'est compilé que dans la cible de
/// tests iOS (`tests/Ios` dans project.yml) ; la suite tvOS reste mono-scène et ne demande
/// JAMAIS de seconde scène. Extension de `KadreUikitAttachTests` : les noms figés restent
/// dans la même classe pour l'énumération XCTest.
extension KadreUikitAttachTests {

    // MARK: - Mécanique multi-scènes (UIKit réel)

    /// Demande une scène supplémentaire via `requestSceneSessionActivation` (mécanique UIKit
    /// réelle — aucune scène synthétique), attend borné la connexion de SON fixture au
    /// `willConnectTo` du delegate, puis sa disponibilité complète. Toute erreur de la
    /// requête est capturée et échoue le test avec son message exact (aucune expectation
    /// inversée : le diagnostic prime sur la mécanique d'attente).
    func requestSecondarySceneFixture(timeout: TimeInterval = 15, file: StaticString = #filePath, line: UInt = #line) throws -> SceneFixture {
        let countBefore = DriverFixture.shared.connected.count
        let errorLock = NSLock()
        var requestError: (any Error)?
        UIApplication.shared.requestSceneSessionActivation(
            nil,
            userActivity: NSUserActivity(activityType: "org.graphiks.kadre.driver.scene"),
            options: nil,
        ) { error in
            errorLock.lock()
            requestError = error
            errorLock.unlock()
        }

        func capturedError() -> (any Error)? {
            errorLock.lock(); defer { errorLock.unlock() }
            return requestError
        }

        var connected = false
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if DriverFixture.shared.connected.count > countBefore {
                connected = true
                // Laisse l' errorHandler éventuel arriver après la connexion (borné).
                let settle = Date().addingTimeInterval(1)
                while capturedError() == nil && Date() < settle {
                    RunLoop.current.run(until: Date().addingTimeInterval(0.05))
                }
                break
            }
            if capturedError() != nil { break }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        if let requestError = capturedError() {
            XCTFail("scene session activation refused: \(requestError) "
                + "(connected=\(DriverFixture.shared.connected.count))", file: file, line: line)
            throw NSError(domain: "KadreUikitDriver", code: 3,
                          userInfo: ["what": "secondary scene request refused"])
        }
        guard connected, DriverFixture.shared.connected.count > countBefore else {
            XCTFail("secondary scene never connected in \(timeout)s (connected=\(DriverFixture.shared.connected.count))",
                    file: file, line: line)
            throw NSError(domain: "KadreUikitDriver", code: 2,
                          userInfo: ["what": "secondary scene did not connect"])
        }
        let fixture = DriverFixture.shared.connected[countBefore]
        try fixture.waitUntilReady()
        return fixture
    }

    /// Destruction RÉELLE de la session de scène : UIKit poste alors le VRAI DidDisconnect.
    /// L' errorHandler inversé échoue le test si la destruction est refusée.
    func destroySceneSession(_ sceneSession: UISceneSession, timeout: TimeInterval = 15, file: StaticString = #filePath, line: UInt = #line) throws {
        let refused = expectation(description: "requestSceneSessionDestruction must not error")
        refused.isInverted = true
        UIApplication.shared.requestSceneSessionDestruction(sceneSession, options: nil) { error in
            refused.fulfill()
            _ = error
        }
        wait(for: [refused], timeout: timeout)
    }

    // MARK: - Scénarios iOS-only (multi-scène)

    /// BCK-013 / uikit-attach-window-mismatch — la fenêtre de la scène 2 avec la scène 1
    /// est refusée `InvalidRequest("window")` (la fenêtre appartient à une AUTRE scène).
    func testAttachRejectsWindowFromAnotherScene() throws {
        let first = try primaryFixture()
        let second = try requestSecondarySceneFixture()
        XCTAssertFalse(first.scene.isEqual(second.scene), "two distinct scenes are required")

        let window2 = try XCTUnwrap(second.window)
        let view2 = try XCTUnwrap(second.observedView)
        let result = attachRaw(fixture: first, window: window2, view: view2)

        assertInvalidRequest(result, field: "window")
        XCTAssertNil(first.session, "a refused attach must not leave a session on scene 1")
        XCTAssertNil(second.session, "a refused attach must not leave a session on scene 2")
    }

    /// BCK-013 / uikit-two-scenes-independent — les deux scènes attachées tournent des
    /// sessions INDÉPENDANTES : requestStop sur la session 1, la session 2 reste Running
    /// et le fixture 2 reste intact.
    func testTwoScenesRunIndependentSessions() throws {
        let first = try primaryFixture()
        let session1 = try attach(first)
        try first.waitUntilApplicationFacts()

        let second = try requestSecondarySceneFixture()
        let session2 = try attach(second)
        try second.waitUntilApplicationFacts()

        // Fixtures indépendants : identités, ids de surface et prédictions propres.
        XCTAssertFalse(first.scene.isEqual(second.scene))
        XCTAssertNotEqual(first.primarySurfaceId, second.primarySurfaceId)
        XCTAssertFalse(session1.id.isEqual(session2.id))
        XCTAssertTrue(second.primarySurfaceIsPrimaryWindowSurface)

        // requestStop sur la session 1 uniquement.
        session1.requestStop()
        try first.waitUntilSessionState(timeout: 10) { !($0 is SessionStateRunning) }

        // La session 2 reste Running, le fixture 2 reste intact.
        try second.waitUntilSessionState(timeout: 5) { $0 is SessionStateRunning }
        XCTAssertEqual(second.primarySurfaceId, second.primaryWindowSurfaceId)
        XCTAssertTrue(second.primarySurfaceIsPrimaryWindowSurface)
        XCTAssertNotNil(second.lifecycleSnapshot())

        // Nettoyage : terminer la session 1 (l'attente est couverte par la suite) et la 2.
        let terminated = expectation(description: "session 1 terminated")
        session1.awaitTermination { _, error in
            XCTAssertNil(error)
            terminated.fulfill()
        }
        wait(for: [terminated], timeout: 20)
        try stopAndAwaitTermination(second)
    }

    /// BCK-013 / uikit-disconnect-terminates — la destruction RÉELLE de la scène 2
    /// (`requestSceneSessionDestruction` → vrai DidDisconnect) termine SA session
    /// uniquement : session 2 Terminated, session 1 intacte, et les références Swift de
    /// la fenêtre/vue de la scène 2 restent utilisables (Kadre ne détruit jamais le natif).
    func testSceneDisconnectionTerminatesOnlyItsSession() throws {
        let first = try primaryFixture()
        _ = try attach(first)
        try first.waitUntilApplicationFacts()

        let second = try requestSecondarySceneFixture()
        _ = try attach(second)
        try second.waitUntilApplicationFacts()

        try destroySceneSession(try XCTUnwrap(second.scene.session))

        // La session de la scène 2 est terminée par le disconnect réel.
        try second.waitUntilSessionState(timeout: 15) { $0 is SessionStateTerminated }
        // La session de la scène 1 est intacte.
        try first.waitUntilSessionState(timeout: 5) { $0 is SessionStateRunning }
        XCTAssertNotNil(first.primarySurfaceId)

        // Les objets natifs de la scène 2 restent utilisables depuis Swift.
        let window2 = try XCTUnwrap(second.window)
        let view2 = try XCTUnwrap(second.observedView)
        XCTAssertEqual(view2.bounds.width, 320, accuracy: 0.001)
        XCTAssertEqual(view2.bounds.height, 240, accuracy: 0.001)
        XCTAssertFalse(window2.isHidden)
        window2.layoutIfNeeded()

        // Nettoyage : arrêt de la session 1 pour les tests suivants.
        try stopAndAwaitTermination(first)
    }
}
