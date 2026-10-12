import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves phase 5 (Task 2) du touch UIKit/tvOS — STRUCTURELLES, les deux familles. Le
/// recognizer Kadre (`UIKitTouchRecognizer`, sous-classe Kotlin de `UIGestureRecognizer`) est
/// ajouté ADDITIVEMENT à la vue du host à l'attach, retiré au close. Le touch n'est PAS
/// synthétisable in-process honnêtement (ruling pré-vol) : la livraison réelle reste le
/// charter manuel — ces tests prouvent donc CE QUI EST PROUVABLE SANS VRAIE TOUCHE :
///
/// - **Le SPIKE d'exécution, DISCRIMINANT (revue I2)** : l'IMP du sélecteur de livraison
///   `touchesBegan:withEvent:` sur la classe PROPRE de l'instance diffère de celui de la base
///   (`class_getMethodImplementation` via la sonde Kotlin) — vrai UNIQUEMENT quand l'export
///   `@ExportObjCClass` + `@ObjCAction` est vivant. Un `respondsToSelector:` ne discriminait
///   rien (tout recognizer y répond : la base implémente les hooks) ; le CONTRÔLE en bas de
///   classe prouve que le prédicat sait ÉCHOUER ;
/// - **L'additivité** : `cancelsTouchesInView == false` (décision tracée — le host garde ses
///   touches), le recognizer est actif, et le compte total des recognizers de la vue ne
///   bouge que de UN (Kadres seul ajout, rien du host n'est modifié) ;
/// - **L'annonce honnête** : `InputObservationChanged(touchInstalled = true)` publiée après
///   l'installation, all-false à la révocation — prouvée au SEIN du sink par le compteur
///   d'annonces de la sonde (la réception par le runtime est la preuve contractuelle Task 4).
final class KadreUikitInputTests: XCTestCase {

    /// Filet de sécurité ENTRE tests (miroir `KadreUikitSurfaceTests.tearDown`).
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

    // MARK: - Preuves

    /// L'IMP du sélecteur pour la CLASSE PROPRE de l'objet — le même prédicat que la sonde
    /// Kotlin (`object_getClass` + `class_getMethodImplementation`), côté Swift pour le
    /// CONTRÔLE qui prouve que le prédicat peut échouer (revue I2).
    private func ownClassImp(_ object: NSObject, _ selector: Selector) -> UnsafeRawPointer {
        let imp = class_getMethodImplementation(object_getClass(object), selector)
        return unsafeBitCast(imp, to: UnsafeRawPointer.self)
    }

    /// uikit-input-touch-recognizer-additive — l'attach pose LE recognizer Kadre sur la vue du
    /// host : son IMP propre surcharge le hook de la base (le SPIKE, prouvé de façon
    /// DISCRIMINANTE), il ne peut pas annuler les touches du host (`cancelsTouchesInView ==
    /// false`), il est actif, et il est le SEUL recognizer ajouté (compte total = baseline + 1).
    /// L'annonce d'installation est publiée (compteur d'annonces, au-delà du baseline).
    func testTouchRecognizerInstalledAdditivelyWithHonestAnnouncement() throws {
        let fixture = try primaryFixture()
        let view = try XCTUnwrap(fixture.observedView)
        let baseline = KadreUikitSurfaceDriver.shared.touchRecognitionProbe(view: view)
        XCTAssertFalse(baseline.recognizerAttached,
                       "no Kadre touch recognizer before attach")
        let installsBefore = KadreUikitSurfaceDriver.shared.touchInstallCount
        let announcementsBefore = KadreUikitSurfaceDriver.shared.touchAnnouncementCount

        let (_, _, _) = try attachedSurface()

        let probe = KadreUikitSurfaceDriver.shared.touchRecognitionProbe(view: view)
        XCTAssertTrue(probe.recognizerAttached,
                      "the Kadre touch recognizer must be on the host view after attach")
        XCTAssertTrue(probe.recognizerOverridesTouchesBeganImp,
                      "the Kadre subclass's OWN imp must override the base hook — a recognizer "
                          + "that merely inherits the empty base hook (a dropped @ObjCAction "
                          + "export) FAILS this discriminating check")
        XCTAssertEqual(probe.recognizerObjCClassName, "UIKitTouchRecognizer",
                       "the @ExportObjCClass runtime registration name")
        XCTAssertFalse(probe.cancelsTouchesInView,
                       "the traced additive policy: the host view keeps its touches")
        XCTAssertTrue(probe.recognizerEnabled, "the recognizer must be enabled")
        XCTAssertEqual(probe.totalGestureRecognizers, baseline.totalGestureRecognizers + 1,
                       "the Kadre recognizer is the only recognizer added to the host view")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.touchInstallCount, installsBefore,
                             "the install counter must have moved")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.touchAnnouncementCount, announcementsBefore,
                             "the InputObservationChanged(touchInstalled = true) must have been published")

        try stopAndAwaitTermination(fixture)
    }

    /// Le CONTRÔLE de la revue I2 : un recognizer de BASE ne surcharge rien — son IMP propre
    /// EST celui de la base, donc le prédicat du probe (différence des IMP, le même que la
    /// sonde Kotlin calcule pour le recognizer Kadre) sait ÉCHOUER. La vérification du SPIKE
    /// n'est pas toujours-verte : elle distingue une sous-classe qui exporte ses hooks d'un
    /// recognizer qui hérite le hook vide — la livraison y mourrait silencieusement.
    func testExportProbeDiscriminatesABaseRecognizer() {
        let touchSelector = NSSelectorFromString("touchesBegan:withEvent:")
        let control = UIGestureRecognizer()
        XCTAssertEqual(NSStringFromClass(type(of: control)), "UIGestureRecognizer",
                       "control sanity: a base recognizer, not a subclass")
        XCTAssertEqual(ownClassImp(control, touchSelector),
                       ownClassImp(UIGestureRecognizer(), touchSelector),
                       "a base recognizer only inherits the base imp — the probe's imp-difference "
                           + "predicate is not always-true")
    }

    /// uikit-input-touch-recognizer-revoked — le close retire le recognizer de la vue du host
    /// (zéro recognizer résiduel : retour au baseline EXACT), publie l'annonce de révocation,
    /// et le compteur d'events de livraison reste figé (aucun handler résiduel — sur une suite
    /// automatisée il n'y de toute façon pas de vrai touch, donc zéro partout).
    func testTouchRecognizerRemovedAndAnnouncementRevokedAtClose() throws {
        let (fixture, _, _) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let baseline = KadreUikitSurfaceDriver.shared.touchRecognitionProbe(view: view)
        XCTAssertTrue(baseline.recognizerAttached,
                      "precondition: the recognizer is installed by the attach")
        let teardownsBefore = KadreUikitSurfaceDriver.shared.touchTeardownCount
        let announcementsBefore = KadreUikitSurfaceDriver.shared.touchAnnouncementCount

        try stopAndAwaitTermination(fixture)

        let probe = KadreUikitSurfaceDriver.shared.touchRecognitionProbe(view: view)
        XCTAssertFalse(probe.recognizerAttached,
                       "the Kadre touch recognizer must be gone after session close")
        XCTAssertEqual(probe.totalGestureRecognizers, baseline.totalGestureRecognizers - 1,
                       "the host view is back to its own recognizers, nothing residual")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.touchTeardownCount, teardownsBefore,
                             "the teardown counter must have moved")
        XCTAssertGreaterThanOrEqual(KadreUikitSurfaceDriver.shared.touchAnnouncementCount,
                                    announcementsBefore + 1,
                                    "the all-false revocation announcement must have been published")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.touchEventCount, 0,
                       "no UITouch delivery can occur on an automated suite (charter manual for real touch)")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.touchStimulusCount, 0,
                       "no TouchChanged stimulus without a real touch — never fabricated")
    }
}
