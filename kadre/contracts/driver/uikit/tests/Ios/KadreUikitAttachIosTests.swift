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

    /// Attend borné la n-ième scène secondaire demandée PAR L'APP (mécanique launch-time :
    /// `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=<n>` — la demande part de
    /// `DriverAppDelegate` après la première activation, jamais du bundle de test), puis sa
    /// disponibilité complète. Toute erreur d'activation enregistrée par l'app est remontée
    /// telle quelle dans l'échec (aucun contournement factice).
    func secondaryFixture(
        at index: Int = 1,
        timeout: TimeInterval = 20,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> SceneFixture {
        let deadline = Date().addingTimeInterval(timeout)
        while DriverFixture.shared.connected.count <= index {
            if Date() > deadline {
                let errors = DriverFixture.shared.sceneActivationErrors
                    .map(String.init(describing:)).joined(separator: "; ")
                XCTFail("secondary scene #\(index) never connected in \(timeout)s — "
                    + "activation errors=[\(errors)] "
                    + "connected=\(DriverFixture.shared.connected.count)", file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 2,
                              userInfo: ["what": "secondary scene did not connect"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        let fixture = DriverFixture.shared.connected[index]
        // Scène demandée par l'app : reste ForegroundInactive (pas de didBecomeActive) —
        // l'attach l'accepte par design (voir SceneFixture.waitUntilReady).
        try fixture.waitUntilReady(requireActivation: false, timeout: timeout)
        return fixture
    }

    /// Destruction RÉELLE de la session de scène — déclenchée par le code de l'APP
    /// (`DriverSceneRequest`, même mécanique que l'activation) : la demande équivalente depuis
    /// le bundle de test est un no-op silencieux dans cet environnement (30 s, aucune erreur,
    /// aucun DidDisconnect — cf. rapport). UIKit poste alors le VRAI DidDisconnect.
    func destroySceneSession(_ sceneSession: UISceneSession, timeout: TimeInterval = 15, file: StaticString = #filePath, line: UInt = #line) throws {
        let refused = expectation(description: "requestSceneSessionDestruction must not error")
        refused.isInverted = true
        DriverSceneRequest.destroySceneSession(sceneSession) { error in
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
        let second = try secondaryFixture(at: 1)
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

        // Scène secondaire #2 : la #1 a été DÉTRUITE par testSceneDisconnection (ordre
        // alphabétique) — la session de scène détruite ne se reconnecte pas.
        let second = try secondaryFixture(at: 2)
        let session2 = try attach(second)

        // Fixtures indépendants : scènes, sessions et surfaces toutes distinctes (identités —
        // les descriptions d'ids sont rédigées par Kotlin, jamais comparées en chaînes).
        XCTAssertFalse(first.scene.isEqual(second.scene))
        XCTAssertFalse(session1.id.isEqual(session2.id), "two sessions must have distinct ids")
        guard let firstSurface = first.primarySurfaceRef, let secondSurface = second.primarySurfaceRef else {
            return XCTFail("both fixtures must have captured their primary surface")
        }
        XCTAssertFalse(firstSurface === secondSurface, "two sessions must not share a primary surface")
        XCTAssertTrue(second.primarySurfaceIsPrimaryWindowSurface)

        // requestStop sur la session 1 uniquement.
        session1.requestStop()
        try first.waitUntilSessionState(timeout: 10) { !($0 is SessionStateRunning) }

        // La session 2 reste Running, le fixture 2 reste intact.
        try second.waitUntilSessionState(timeout: 5) { $0 is SessionStateRunning }
        XCTAssertTrue(second.primarySurfaceIdMatchesWindowSurfaceId)
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

        let second = try secondaryFixture(at: 1)
        _ = try attach(second)

        try destroySceneSession(try XCTUnwrap(second.scene.session))

        // La destruction UIKit est asynchrone (coalescing) : attendre d'abord la preuve
        // UIKit-side (session invalidée), puis la terminaison Kadre par le disconnect réel.
        let uikitDeadline = Date().addingTimeInterval(30)
        while second.scene.session != nil {
            if Date() > uikitDeadline {
                XCTFail("UIKit never invalidated the destroyed scene session in 30s")
                break
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }

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
