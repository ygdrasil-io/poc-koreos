import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

// Preuves phase 7 (Task 4) de l'ABSENCE drop tvOS — et le CONTRÔLE iOS qui prouve que la sonde
// sait réussir : LES DEUX familles (tests/Shared compile dans les deux targets de test).
//
// La garde de TYPE est la frontière plateforme TELLE QUELLE : `UIDropInteraction` est
// `API_UNAVAILABLE(tvos)` (UIDragSession.h:19 — 0 symbole dans le klib tvOS, fait vérifié Task 3)
// — ce fichier ne NOMME donc JAMAIS le type. La sonde STRUCTURALE compte les interactions de la
// vue dont la CLASSE est une interaction drop (`NSStringFromClass` — le nom de classe Apple n'a
// aucune raison d'exister en runtime tvOS : zéro, par construction du klib) ; sur iOS le contrôle
// la voit, ce qui prouve qu'elle ne serait pas toujours-verte. La capacité est lue sur le
// SNAPSHOT de capabilities EXPORTÉ (`SurfaceCapabilities.handlerInteractions` — driver-visible
// par construction, le même StateFlow que lit le runtime pour construire son handler
// d'interaction) : c'est la voie retenue ici, avec la sonde zéro-install en second cordon.
final class KadreUikitDropAbsenceTests: XCTestCase {

    /// Filet de sécurité ENTRE tests (miroir `KadreUikitInputTests.tearDown`).
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

    // MARK: - Helpers (dupliqués des classes sœurs : méthodes internal d'instance)

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

    func attachedSurface(
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> (SceneFixture, any HostSurface) {
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
            windowSceneProvider: nil, // static framework : pas de convenience du défaut Kotlin
        )
        if let failure = (result as? KadreResultFailure)?.reason {
            XCTFail("attach failed unexpectedly: \(failure)", file: file, line: line)
        }
        let success = try XCTUnwrap(result as? KadreResultSuccess<AnyObject>, file: file, line: line)
        let session = try XCTUnwrap(success.value as? (any KadreSession), file: file, line: line)
        fixture.session = session
        try fixture.waitUntilApplicationFacts(minimumRuns: runsBeforeAttach + 1, timeout: 10)
        let surface = try XCTUnwrap(fixture.primarySurface, file: file, line: line)
        return (fixture, surface)
    }

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

    /// La sonde STRUCTURALE sans type : le compte d'interactions de [view] dont la classe
    /// Objective-C est une interaction drop. AUCUNE référence au type `UIDropInteraction`
    /// (interdit à la compilation tvOS) — le NOM de classe est le fait runtime : présent sur
    /// iOS (le contrôle le voit), inexistant en runtime tvOS (0 symbole dans le klib, la
    /// classe ne peut pas y être instanciée).
    private func dropInteractionCount(in view: UIView) -> Int {
        view.interactions.filter { NSStringFromClass(type(of: $0)) == "UIDropInteraction" }.count
    }

    // MARK: - Preuve

    /// uikit-drop-tvos-absent — tvOS : ZÉRO interaction drop sur la vue du host avant ET après
    /// l'attach (le klib ne peut pas la construire), `dropInstallCount` et `dropTeardownCount`
    /// figés (le binding n'y existe pas), `dropEventCount` figé après pompe (aucune livraison
    /// possible), et la capacité `handlerInteractions` REFUSÉE sur le snapshot de capabilities
    /// exporté (le ladder Task 3 : l'ensemble des kinds est vide → Unsupported). iOS (le
    /// CONTRÔLE qui prouve la sonde discriminante) : l'interaction EST installée, les compteurs
    /// bougent, la capacité est `Supported` portant EXACTEMENT `AcceptDrop`.
    func test_uikit_drop_tvos_absent() throws {
        let fixture = try primaryFixture()
        let view = try XCTUnwrap(fixture.observedView)

        // Avant attach : zéro interaction drop, les deux familles (le host n'en pose jamais).
        XCTAssertEqual(dropInteractionCount(in: view), 0,
                       "no drop interaction before attach, either family")
        let installsBefore = KadreUikitSurfaceDriver.shared.dropInstallCount
        let teardownsBefore = KadreUikitSurfaceDriver.shared.dropTeardownCount
        let eventsBefore = KadreUikitSurfaceDriver.shared.dropEventCount

        let (attachedFixture, surface) = try attachedSurface()
        XCTAssertTrue(attachedFixture === fixture, "the primary fixture is the attached one")

        // Le snapshot de capabilities EXPORTÉ — la voie choisie pour la capacité (driver-visible
        // par construction ; la sonde zéro-install reste le second cordon structurel).
        let capabilities = try XCTUnwrap(surface.capabilities.value as? SurfaceCapabilities,
                                         "the surface capabilities snapshot is readable")

        #if os(tvOS)
        // Zéro interaction drop après attach — le binding n'existe pas dans ce klib.
        XCTAssertEqual(dropInteractionCount(in: view), 0,
                       "tvOS never installs a UIDropInteraction — the klib carries zero drop symbols")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropInstallCount, installsBefore,
                       "no drop install counter can move on tvOS (the binding is null by construction)")
        XCTAssertTrue(capabilities.handlerInteractions is CapabilityUnsupported,
                      "the handlerInteractions capability is REFUSED on tvOS, got "
                          + "\(capabilities.handlerInteractions)")

        // Pompe d'une fenêtre de quiescence : aucune livraison ne peut arriver.
        RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropEventCount, eventsBefore,
                       "zero drop deliveries after pumping — there is no delegate to deliver to")

        try stopAndAwaitTermination(fixture)
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropTeardownCount, teardownsBefore,
                       "no drop teardown counter can move on tvOS")
        XCTAssertEqual(dropInteractionCount(in: view), 0,
                       "still zero drop interactions after the session close")
        #else
        // Le CONTRÔLE iOS : la sonde structurelle VOIT l'interaction installée — elle n'est pas
        // toujours-verte, l'absence tvOS ci-dessus est donc discriminante.
        XCTAssertEqual(dropInteractionCount(in: view), 1,
                       "iOS control: the probe sees the interaction the attach installed")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.dropInstallCount, installsBefore,
                             "iOS control: the install counter moves")
        let supported = try XCTUnwrap(
            capabilities.handlerInteractions as? CapabilitySupported<NSSet>,
            "iOS control: handlerInteractions is Supported, got \(capabilities.handlerInteractions)",
        )
        XCTAssertEqual(supported.constraints?.count, 1,
                       "iOS advertises exactly one handler interaction kind")
        XCTAssertEqual(supported.constraints?.filter {
            ($0 as? InteractionKind) == InteractionKind.acceptdrop
        }.count, 1, "the advertised kind is AcceptDrop — nothing else exists on UIKit")

        try stopAndAwaitTermination(fixture)
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropTeardownCount, teardownsBefore + 1,
                       "iOS control: the teardown counter moves exactly once per close")
        XCTAssertEqual(dropInteractionCount(in: view), 0,
                       "iOS control: the interaction is gone after the session close")
        #endif
    }
}
