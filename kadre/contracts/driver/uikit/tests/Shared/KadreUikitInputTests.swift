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

    /// La vérité par famille de la Task 3 : le nombre de recognizers que l'attach de Kadre
    /// ajoute à la vue du host (touch + gestes + survol), et l'ENSEMBLE EXACT des classes
    /// attendues. iOS : tap/pan/pinch/rotation + survol ; tvOS : tap/pan seuls — le klib tvOS
    /// ne porte ni pinch ni rotation ni hover (vérifié linkdata/header, KDoc Kotlin).
    private static var kadreRecognizerCount: Int { expectedRecognizerClasses.count }

    /// Les recognizers de GESTE seuls (le compteur `gestureInstallCount` compte un pas par recognizer de geste, pas le touch ni le survol).
    private static var gestureRecognizerCount: Int {
        expectedRecognizerClasses.filter { $0 != "UIKitTouchRecognizer" && $0 != "UIHoverGestureRecognizer" }.count
    }

    private static var expectedRecognizerClasses: Set<String> {
        #if os(tvOS)
        return ["UITapGestureRecognizer", "UIPanGestureRecognizer", "UIKitTouchRecognizer"]
        #else
        return ["UITapGestureRecognizer", "UIPanGestureRecognizer", "UIPinchGestureRecognizer",
                "UIRotationGestureRecognizer", "UIHoverGestureRecognizer", "UIKitTouchRecognizer"]
        #endif
    }

    /// uikit-input-touch-recognizer-additive — l'attach pose LE recognizer Kadre sur la vue du
    /// host : son IMP propre surcharge le hook de la base (le SPIKE, prouvé de façon
    /// DISCRIMINANTE), il ne peut pas annuler les touches du host (`cancelsTouchesInView ==
    /// false`), il est actif, et les SEULS recognizers ajoutés sont ceux de Kadre
    /// (compte total = baseline + la vérité par famille de la Task 3 : tap/pan/pinch/rotation
    /// + survol en iOS, tap/pan seuls en tvOS — le klib tvOS ne porte ni pinch ni rotation ni
    /// hover, cf. le KDoc Kotlin de `UIKitGestureBindings`). L'annonce d'installation est
    /// publiée (compteurs d'annonces, au-delà du baseline).
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
        XCTAssertEqual(probe.totalGestureRecognizers, baseline.totalGestureRecognizers + Int32(Self.kadreRecognizerCount),
                       "the Kadre recognizers are the only recognizers added to the host view")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.touchInstallCount, installsBefore,
                             "the install counter must have moved")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.touchAnnouncementCount, announcementsBefore,
                             "the InputObservationChanged(touchInstalled = true) must have been published")

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-input-gesture-keyboard-sources-announced — Task 3 : les sources gestes/survol et
    /// clavier sont posées à l'attach avec la vérité par famille et la politique additive, et
    /// leurs compteurs d'installation/annonce bougent. Les recognizers de geste sont
    /// STANDARD (les noms Obj-C de la vue sont ceux des classes Apple) ; la SURVOL est iOS
    /// seul. Le clavier : une CONNEXION de source peut légitimement arriver sur un simulateur
    /// automatisé — le pairing « Connect Hardware Keyboard » du Simulator livre un VRAI
    /// `GCKeyboard.coalescedKeyboard`, que le scan d'install attache (un fait VRAI, jamais une
    /// fabrication) ; ce qui reste IMPOSSIBLE sans charter manuel, ce sont les LIVRAISONS de
    /// touches — `keyboardEventCount` et `keyboardStimulusCount` restent à zéro (aucune touche
    /// n'est synthétisable in-process honnêtement, le charter manuel Task 4 incrémentera). Le
    /// BINDING est installé et révoqué (compteurs).
    func testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily() throws {
        let fixture = try primaryFixture()
        let view = try XCTUnwrap(fixture.observedView)
        let baseline = KadreUikitSurfaceDriver.shared.gestureRecognitionProbe(view: view)
        XCTAssertTrue(baseline.recognizerClassNames.isEmpty,
                      "no recognizer at all before attach (the host fixture view carries none)")
        let keyboardInstallsBefore = KadreUikitSurfaceDriver.shared.keyboardInstallCount
        let keyboardAnnouncementsBefore = KadreUikitSurfaceDriver.shared.keyboardAnnouncementCount
        let keyboardConnectsBefore = KadreUikitSurfaceDriver.shared.keyboardConnectCount
        let gestureInstallsBefore = KadreUikitSurfaceDriver.shared.gestureInstallCount
        let hoverInstallsBefore = KadreUikitSurfaceDriver.shared.hoverInstallCount
        let gestureAnnouncementsBefore = KadreUikitSurfaceDriver.shared.gestureAnnouncementCount

        let (_, _, _) = try attachedSurface()

        let probe = KadreUikitSurfaceDriver.shared.gestureRecognitionProbe(view: view)
        XCTAssertEqual(Set(probe.recognizerClassNames), Self.expectedRecognizerClasses,
                       "the exact set of recognizers installed by Kadre, per family")
        XCTAssertTrue(probe.cancelsTouchesInViewAll,
                      "the traced additive policy: every installed recognizer lets the host keep its touches")
        XCTAssertGreaterThanOrEqual(KadreUikitSurfaceDriver.shared.gestureInstallCount,
                                    gestureInstallsBefore + Int64(Self.gestureRecognizerCount),
                                     "one counter step per gesture recognizer installed")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.hoverInstallCount > hoverInstallsBefore,
                       Self.expectedRecognizerClasses.contains("UIHoverGestureRecognizer"),
                       "the hover installs only where the platform klib carries the class")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.keyboardInstallCount, keyboardInstallsBefore,
                             "the keyboard binding install counter must have moved")
        XCTAssertGreaterThanOrEqual(KadreUikitSurfaceDriver.shared.keyboardAnnouncementCount,
                                    keyboardAnnouncementsBefore,
                                    "the keyboard announcement counter must have moved")
        XCTAssertGreaterThanOrEqual(KadreUikitSurfaceDriver.shared.gestureAnnouncementCount,
                                    gestureAnnouncementsBefore,
                                    "the gesture announcement counter must have moved")
        XCTAssertGreaterThanOrEqual(KadreUikitSurfaceDriver.shared.keyboardConnectCount,
                                    keyboardConnectsBefore,
                                    "a connect counter never decreases; it may legitimately grow "
                                        + "when the simulator pairs a real hardware keyboard — the "
                                        + "install scan attaching to a REAL coalesced GCKeyboard is a "
                                        + "true fact, not a fabrication")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.keyboardEventCount, 0,
                       "no GCKeyCode delivery can occur on an automated suite (charter manual Task 4)")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.keyboardStimulusCount, 0,
                       "no KeyChanged stimulus without a real key — never fabricated")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.gestureStimulusCount, 0,
                       "no Gesture/Pointer stimulus without a real gesture — never fabricated")

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
    /// automatisée il n'y de toute façon pas de vrai touch, donc zéro partout). Task 3 : les
    /// recognizers de geste et le survol suivent le même retrait (compteurs de retrait par
    /// famille), ainsi que le binding clavier.
    func testTouchRecognizerRemovedAndAnnouncementRevokedAtClose() throws {
        let (fixture, _, _) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let baseline = KadreUikitSurfaceDriver.shared.touchRecognitionProbe(view: view)
        XCTAssertTrue(baseline.recognizerAttached,
                      "precondition: the recognizer is installed by the attach")
        let teardownsBefore = KadreUikitSurfaceDriver.shared.touchTeardownCount
        let announcementsBefore = KadreUikitSurfaceDriver.shared.touchAnnouncementCount
        let keyboardTeardownsBefore = KadreUikitSurfaceDriver.shared.keyboardTeardownCount
        let gestureTeardownsBefore = KadreUikitSurfaceDriver.shared.gestureTeardownCount
        let hoverTeardownsBefore = KadreUikitSurfaceDriver.shared.hoverTeardownCount

        try stopAndAwaitTermination(fixture)

        let probe = KadreUikitSurfaceDriver.shared.touchRecognitionProbe(view: view)
        XCTAssertFalse(probe.recognizerAttached,
                       "the Kadre touch recognizer must be gone after session close")
        XCTAssertEqual(probe.totalGestureRecognizers, baseline.totalGestureRecognizers - Int32(Self.kadreRecognizerCount),
                       "the host view is back to its own recognizers, nothing residual")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.touchTeardownCount, teardownsBefore,
                             "the teardown counter must have moved")
        XCTAssertGreaterThanOrEqual(KadreUikitSurfaceDriver.shared.touchAnnouncementCount,
                                    announcementsBefore + 1,
                                    "the all-false revocation announcement must have been published")
        XCTAssertGreaterThan(KadreUikitSurfaceDriver.shared.keyboardTeardownCount, keyboardTeardownsBefore,
                             "the keyboard binding teardown counter must have moved")
        XCTAssertGreaterThanOrEqual(KadreUikitSurfaceDriver.shared.gestureTeardownCount,
                                    gestureTeardownsBefore + Int64(Self.gestureRecognizerCount),
                                    "one teardown step per gesture recognizer removed")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.hoverTeardownCount > hoverTeardownsBefore,
                       Self.expectedRecognizerClasses.contains("UIHoverGestureRecognizer"),
                       "the hover teardowns only where the platform klib carried the install")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.touchEventCount, 0,
                       "no UITouch delivery can occur on an automated suite (charter manual for real touch)")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.touchStimulusCount, 0,
                       "no TouchChanged stimulus without a real touch — never fabricated")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.keyboardEventCount, 0,
                       "no GCKeyCode delivery can occur on an automated suite (charter manual Task 4)")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.gestureStimulusCount, 0,
                       "no Gesture/Pointer stimulus without a real gesture — never fabricated")
    }
}
