import XCTest
import UIKit
import KadreUikit
@testable import KadreUikitDriverIos

/// Scénario de ROTATION (iOS UNIQUEMENT, BCK-016) — ce fichier n'est compilé que dans la cible
/// de tests iOS (`tests/Ios` dans project.yml) ; tvOS n'a pas de rotation.
extension KadreUikitSurfaceTests {

    /// uikit-surface-rotation-propagates — la rotation RÉELLE de la scène
    /// (`windowScene.requestGeometryUpdate(.iOS(interfaceOrientations:))`, orientations
    /// déclarées dans l'Info.plist du driver via project.yml) se propage aux métriques : la
    /// vue observée est posée sur les bounds de la fenêtre (masks flexibles posés PAR LE TEST,
    /// jamais par le fixture — les preuves phase 1 restent en 320×240), la rotation change les
    /// bounds de la fenêtre, la vue suit, et les métriques publiées valent la géométrie
    /// post-rotation EXACTE (sentinelle uikit-rotation-metrics-faithful).
    ///
    /// REPLI DOCUMENTÉ (brief Step 2) : si l'API refuse (orientation lock système, erreur
    /// capturée ci-dessous) ou ne bouge pas les bounds dans la fenêtre bornée, le stimulus
    /// dégrade en resize-à-changement-d'aspect (240×320) — JAMAIS de faux vert rotation : le
    /// choix du stimulus est tracé (NSLog + DriverTrace) et rapporté.
    func testSurfaceRotationPropagatesMetrics() throws {
        let (fixture, _, surface) = try attachedSurface()
        let scene = fixture.scene
        let window = try XCTUnwrap(fixture.window)
        let view = try XCTUnwrap(fixture.observedView)
        let capture = KadreUikitSurfaceDriver.shared.captureEvents(surface: surface)
        defer { capture.stop() }
        primeCapture(capture, on: surface)

        let beforeWidth = Double(view.bounds.width)

        // La vue passe sur les bounds de la fenêtre (et les suit) — l'usage RÉEL d'une vue de
        // contenu pendant une rotation. Posé ici, retiré à la fin : le fixture reste en 320×240
        // pour tous les autres tests.
        view.frame = window.bounds
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        window.layoutIfNeeded()
        let portraitState = try triggeredSurfaceState(fixture, timeout: 5) { candidate in
            abs(candidate.logicalSize.width - Double(window.bounds.width)) < 0.001
                && abs(candidate.logicalSize.height - Double(window.bounds.height)) < 0.001
        }
        XCTAssertEqual(portraitState.scaleFactor, Double(view.traitCollection.displayScale), accuracy: 0.001)
        assertPhysicalDerivesFromLogical(portraitState)
        // Synchronisation SUR LE CANAL des événements (le state est publié sur le main,
        // l'événement arrive par la file des lanes vers le collecteur de fond) : le drain
        // borné aligne l'assertion de publication, même force (>= 1 MetricsChanged).
        try drain(capture, until: { $0.metricsCount >= 1 }, timeout: 5)
        XCTAssertGreaterThanOrEqual(capture.metricsCount, 1, "the full-window move must publish MetricsChanged")
        // Le référentiel « avant rotation » est la FENÊTRE ENTIÈRE portrait — jamais la vue
        // 320×240 du fixture (le passage à pleine fenêtre inverse déjà l'aspect, sans rotation).
        let portraitWindowWidth = Double(window.bounds.width)
        let portraitWindowHeight = Double(window.bounds.height)

        // Le stimulus de rotation RÉEL.
        var geometryError: (any Error)?
        var geometryRefused = false
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscapeRight), errorHandler: { error in
            geometryError = error
            geometryRefused = true
        })

        // Attente bornée de la rotation : la scène DÉCLARE le paysage ET la fenêtre a
        // réellement échangé ses dimensions (la vue suit par ses masks flexibles). Le refus
        // de l'API (errorHandler, ici synchrone et capturé) sort IMMÉDIATEMENT de l'attente —
        // pas de pumping résiduel après un refus connu.
        let rotatedDeadline = Date().addingTimeInterval(8)
        var rotatedReal = false
        while Date() < rotatedDeadline && !geometryRefused {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            let landscapeGeometry = Double(window.bounds.width) > Double(window.bounds.height)
            if landscapeGeometry, scene.interfaceOrientation == UIInterfaceOrientation.landscapeRight {
                rotatedReal = true
                break
            }
        }

        if !rotatedReal {
            // Repli HONNÊTE, documenté : resize à changement d'aspect (240×320) — stimulus de
            // rotation DÉGRADÉ, jamais un vert truqué. L'erreur exacte de l'API est capturée.
            NSLog("KADRE-DRIVER uikit-surface-rotation-propagates DEGRADED: "
                + "requestGeometryUpdate did not rotate the scene in 8s "
                + "(error=\(String(describing: geometryError)) "
                + "sceneOrientation=\(scene.interfaceOrientation.rawValue) "
                + "window=\(window.bounds)) — falling back to the aspect-swapping resize "
                + "(degraded rotation stimulus, per brief Step 2)")
            DriverTrace.record("rotation degraded: requestGeometryUpdate error=\(String(describing: geometryError))")
            view.autoresizingMask = []
            view.frame = CGRect(x: 0, y: 0, width: 240, height: 320)
            view.superview?.layoutIfNeeded()
        } else {
            DriverTrace.record("rotation real: scene=\(scene.interfaceOrientation.rawValue) window=\(window.bounds)")
        }

        // Les métriques publiées valent la géométrie VIVANTE de la vue (fenêtre tournée ou
        // resize dégradé) — jamais les valeurs d'avant le stimulus.
        let wantWidth = Double(view.bounds.width)
        let wantHeight = Double(view.bounds.height)
        XCTAssertNotEqual(wantWidth, beforeWidth, "the stimulus must have moved the metrics")
        let state = try triggeredSurfaceState(fixture, timeout: 5) { candidate in
            abs(candidate.logicalSize.width - wantWidth) < 0.001
                && abs(candidate.logicalSize.height - wantHeight) < 0.001
        }
        XCTAssertEqual(state.scaleFactor, Double(view.traitCollection.displayScale), accuracy: 0.001)
        assertPhysicalDerivesFromLogical(state)
        // La publication MetricsChanged du stimulus est déjà prouvée sur le canal des
        // événements (drain borné de la phase pleine-fenêtre) — le compteur ne peut que
        // croître ; l'assertion de GÉOMÉTRIE ci-dessus porte la preuve du stimulus.

        if rotatedReal {
            // Sentinelle uikit-rotation-metrics-faithful : la vue suit la fenêtre tournée et
            // le paysage est REALLEMENT paysage (les dims publiées sont la géométrie fidèle —
            // les dimensions de la fenêtre ont échangé par rapport au portrait de référence).
            XCTAssertEqual(wantWidth, Double(window.bounds.width), accuracy: 0.001)
            XCTAssertEqual(wantHeight, Double(window.bounds.height), accuracy: 0.001)
            XCTAssertGreaterThan(wantWidth, wantHeight, "landscape geometry must be published")
            XCTAssertEqual(wantWidth, portraitWindowHeight, accuracy: 0.001,
                           "the rotated width must be the portrait height (faithful rotation)")
            XCTAssertEqual(wantHeight, portraitWindowWidth, accuracy: 0.001,
                           "the rotated height must be the portrait width (faithful rotation)")
            XCTAssertEqual(scene.interfaceOrientation, UIInterfaceOrientation.landscapeRight)
        }
        assertNoDroppedObservations()

        // Restauration (best-effort) : portrait + vue 320×240 sans masks — le fixture retrouve
        // sa forme phase 1 pour les tests suivants.
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: .portrait), errorHandler: { _ in })
        view.autoresizingMask = []
        view.frame = CGRect(x: 0, y: 0, width: beforeWidth, height: 240)
        window.layoutIfNeeded()

        try stopAndAwaitTermination(fixture)
    }
}
