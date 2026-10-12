import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves O3 de `requestWindow` (phase 4) — noms de scénarios FIGÉS pour Task 4. La classe
/// porte les scénarios PARTAGÉS (les deux familles : le refus sans provider est le chemin
/// tvOS ET le chemin mono-fenêtre iOS) ; les scénarios multi-scènes corrélés (provider réél)
/// vivent dans l'extension iOS-only (`tests/Ios/KadreUikitWindowRequestIosTests.swift`,
/// compilée uniquement dans la cible de tests iOS — topologie multi-scènes refusée ailleurs).
final class KadreUikitWindowRequestTests: XCTestCase {

    /// Les enfants suivis par LE test courant (scénettes corrélées) — tearDown les détache.
    var children: [(fixture: SceneFixture, record: DriverSessionRecord)] = []

    /// Filet de sécurité ENTRE tests (miroir `KadreUikitAttachTests.tearDown`) : les sessions
    /// attachées par le test sont arrêtées et attendues (borné), les enfants suivis sont
    /// détachés par le VRAI nom de notification (routage par identité — callback tardif sans
    /// effet sur une session déjà terminée), le provider est remis à zéro (tests quiescents :
    /// aucun activation ni corrélation en vol, le reset n'orpheline rien).
    override func tearDown() {
        super.tearDown()
        for child in children {
            NotificationCenter.default.post(name: UIScene.didDisconnectNotification, object: child.fixture.scene)
            try? pumpUntil(timeout: 10) { child.record.lifecycleAttachment() === AttachmentState.detached }
        }
        children.removeAll()
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
        DriverWindowSceneProvider.shared.reset()
    }

    // MARK: - Helpers (dupliqués de KadreUikitAttachTests : méthodes internal d'instance,
    // non héritables sans rejouer ses scénarios — une classe XCTest distincte se les redonne)

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

    /// attach brut (résultat exporté, sans unwrap) — `windowSceneProvider` non nul sur la
    /// session REQUÉRANTE d'un scénario multi-scènes (iOS), nil pour le chemin sans provider.
    func attachRaw(
        fixture: SceneFixture,
        window: UIWindow,
        view: UIView,
        windowSceneProvider: (any UIKitWindowSceneProvider)? = nil,
    ) -> any KadreResult {
        KadreIos.shared.attach(
            windowScene: fixture.scene,
            window: window,
            surfaceView: view,
            applicationFactory: DriverApplication(fixture: fixture),
            policy: KadrePolicies.shared.Default,
            windowSceneProvider: windowSceneProvider,
        )
    }

    /// attach nominal : retourne la session exportée et l'enregistre dans le fixture.
    @discardableResult
    func attach(
        _ fixture: SceneFixture,
        windowSceneProvider: (any UIKitWindowSceneProvider)? = nil,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> any KadreSession {
        let window = try XCTUnwrap(fixture.window, file: file, line: line)
        let view = try XCTUnwrap(fixture.observedView, file: file, line: line)
        let runsBeforeAttach = fixture.applicationRuns
        let result = attachRaw(fixture: fixture, window: window, view: view, windowSceneProvider: windowSceneProvider)
        if let failure = (result as? KadreResultFailure)?.reason {
            XCTFail("attach failed unexpectedly: \(failure)", file: file, line: line)
        }
        let success = try XCTUnwrap(result as? KadreResultSuccess<AnyObject>, file: file, line: line)
        let session = try XCTUnwrap(success.value as? (any KadreSession), file: file, line: line)
        fixture.session = session
        try fixture.waitUntilApplicationFacts(minimumRuns: runsBeforeAttach + 1, timeout: 10)
        return session
    }

    /// La spec d'une demande de fenêtre (init réel exporté — static framework, pas de
    /// conveniences d'arguments par défaut Kotlin ; miroir des défauts de `WindowSpec`).
    func makeWindowSpec(title: String) -> WindowSpec {
        WindowSpec(
            title: title,
            contentSize: LogicalSize(width: 320.0, height: 240.0),
            minimumSize: nil,
            maximumSize: nil,
            outerPosition: nil,
            resizable: true,
            fullscreen: FullscreenModeWindowed.shared,
            decorations: WindowDecorations.system,
            systemButtons: WindowSystemButtons.all,
            level: WindowLevel.normal,
            transparent: false,
            blurBehind: false,
            icon: nil,
            contentProtection: false,
        )
    }

    /// `requestWindow` du REQUÉRANT (appel Kotlin suspend → handler) : la demande exportée.
    @discardableResult
    func requestWindow(
        _ fixture: SceneFixture,
        title: String,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> any WindowRequest {
        let scope = try XCTUnwrap(fixture.scope, "the fixture must have captured its scope", file: file, line: line)
        let delivered = expectation(description: "requestWindow(\(title)) delivered")
        var boxedResult: (any KadreResult)?
        var boxedError: NSError?
        scope.windows.requestWindow(spec: makeWindowSpec(title: title)) { result, error in
            boxedResult = result
            boxedError = error as NSError?
            delivered.fulfill()
        }
        wait(for: [delivered], timeout: timeout)
        XCTAssertNil(boxedError, "requestWindow must not error", file: file, line: line)
        let success = try XCTUnwrap(boxedResult as? KadreResultSuccess<AnyObject>, file: file, line: line)
        return try XCTUnwrap(success.value as? (any WindowRequest), file: file, line: line)
    }

    /// `await` de l'outcome (suspend → handler) — immédiat sur une requête déjà terminale.
    @discardableResult
    func awaitOutcome(
        _ request: any WindowRequest,
        timeout: TimeInterval = 30,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> any WindowRequestOutcome {
        let delivered = expectation(description: "request outcome delivered")
        var boxedOutcome: (any WindowRequestOutcome)?
        var boxedError: NSError?
        request.await { outcome, error in
            boxedOutcome = outcome
            boxedError = error as NSError?
            delivered.fulfill()
        }
        wait(for: [delivered], timeout: timeout)
        XCTAssertNil(boxedError, "await must not error", file: file, line: line)
        return try XCTUnwrap(boxedOutcome, "await delivered no outcome", file: file, line: line)
    }

    /// `cancel` (suspend → handler) — l'outcome d'annulation du requérant.
    @discardableResult
    func cancelRequest(
        _ request: any WindowRequest,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> any WindowCancellationOutcome {
        let delivered = expectation(description: "request cancel delivered")
        var boxedOutcome: (any WindowCancellationOutcome)?
        var boxedError: NSError?
        request.cancel { outcome, error in
            boxedOutcome = outcome
            boxedError = error as NSError?
            delivered.fulfill()
        }
        wait(for: [delivered], timeout: timeout)
        XCTAssertNil(boxedError, "cancel must not error", file: file, line: line)
        return try XCTUnwrap(boxedOutcome, "cancel delivered no outcome", file: file, line: line)
    }

    /// Pumping du run loop (jamais de sommeil nu) : `true` quand le prédicat est atteint,
    /// `false` à la borne — les assertions portent sur la valeur APRÈS l'attente.
    @discardableResult
    func pumpUntil(
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping () -> Bool,
    ) throws -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while !predicate() {
            if Date() > deadline {
                return false
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
        return true
    }

    /// requestStop + awaitTermination (expectation), puis état exporté Terminated.
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

    // MARK: - Scénario partagé (tvOS + iOS) — le refus sans provider

    /// BCK-020 / uikit-window-request-tvos-rejected — `requestWindow` sur une session SANS
    /// provider (le défaut tvOS, et tout app mono-fenêtre) : l'appel réussit et rend au
    /// requérant une requête DÉJÀ terminale `Rejected(Unsupported(RequestWindow))`, avant
    /// TOUTE réservation — aucun provider invoqué (compteur), aucune scène activée, aucune
    /// corrélation, et l'état du manager (primaire, liste de fenêtres) est intact. Une
    /// seconde demande est refusée pareil : rien n'a été réservé par la première.
    func testWindowRequestWithoutProviderIsRejectedUnsupportedWithNoNativeEffect() throws {
        let providerCallsBefore = DriverWindowSceneProvider.shared.openCallCount
        // Le compteur `connected` est GLOBAL au processus (accumulé sur toute la suite) :
        // on fige la BASELINE au début du test — l'assertion porte sur le delta, pas sur
        // une valeur absolue (sur iOS, les scénarios multi-scènes antérieurs l'ont gonflé).
        let connectedBefore = DriverFixture.shared.connected.count

        let fixture = try primaryFixture()
        _ = try attach(fixture)

        // La capability annoncée : Unsupported(RequestWindow) (posée à l'attach, Task 2).
        let stateBefore = try XCTUnwrap(fixture.scope?.windows.state.value as? WindowManagerState)
        let capability = try XCTUnwrap(stateBefore.capabilities.requestWindow as? CapabilityUnsupported,
                                       "a session without a provider must announce requestWindow as Unsupported")
        XCTAssertTrue(capability.failure.operation === KadreOperation.requestwindow,
                      "expected Unsupported(RequestWindow), got \(capability.failure.operation)")

        // La demande : requête possédée par le requérant, déjà Rejected.
        let request = try requestWindow(fixture, title: "window-request-rejected")
        let outcome = try awaitOutcome(request, timeout: 5)
        let rejected = try XCTUnwrap(outcome as? WindowRequestOutcomeRejected,
                                     "expected Rejected, got \(String(describing: outcome))")
        let unsupported = try XCTUnwrap(rejected.failure as? KadreFailureUnsupported,
                                        "expected Unsupported, got \(rejected.failure)")
        XCTAssertTrue(unsupported.operation === KadreOperation.requestwindow)
        XCTAssertTrue(request.state.value is WindowRequestStateTerminated,
                      "the null-provider request must be terminal immediately")

        // Aucun provider invoqué, aucune scène corrélée, aucune résolution : le refus est
        // PUR (aucun appel d'activation ne peut en partir — le provider n'existe pas).
        XCTAssertEqual(DriverWindowSceneProvider.shared.openCallCount, providerCallsBefore,
                       "no provider callback may fire without a provider")
        XCTAssertTrue(DriverWindowSceneProvider.shared.correlation.isEmpty)
        XCTAssertTrue(DriverWindowSceneProvider.shared.resolvedCalls.isEmpty)

        // Aucune réservation : une seconde demande est refusée pareil (pas
        // ResourceLimitExceeded — le chemin sans provider ne consomme rien) et l'état du
        // manager est inchangé (primaire identique, exactement la fenêtre primaire).
        let second = try requestWindow(fixture, title: "window-request-rejected-2")
        let secondOutcome = try awaitOutcome(second, timeout: 5)
        XCTAssertNotNil(secondOutcome as? WindowRequestOutcomeRejected,
                        "the second request must be Rejected too, got \(String(describing: secondOutcome))")
        let stateAfter = try XCTUnwrap(fixture.scope?.windows.state.value as? WindowManagerState)
        XCTAssertEqual(stateAfter.windows.count, 1, "no window may join the requester's list")
        XCTAssertTrue((stateAfter.primary?.surface as AnyObject?) === (stateBefore.primary?.surface as AnyObject?),
                      "the primary window must be untouched")
        XCTAssertEqual(DriverFixture.shared.connected.count, connectedBefore,
                       "no additional scene may connect on the refusal path")

        try stopAndAwaitTermination(fixture)
    }
}
