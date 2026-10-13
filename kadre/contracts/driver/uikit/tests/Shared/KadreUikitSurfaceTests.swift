import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves phase 2 de la surface UIKit/tvOS DYNAMIQUE — métriques, thème, redraw coalescé,
/// échelle `apply` et bail natif (`withUIKitView`), sur des STIMULI RÉELS, les deux familles.
/// Noms de scénarios FIGÉS pour Task 6 (contrat Task-5-proofs).
///
/// MÉCANIQUE DE DÉCLENCHEMENT (ruling contrôleur, Task 3) : le KVO est IMPOSSIBLE en K/N
/// 2.4.20 (aucun sélecteur émis) et `registerForTraitChanges` est injoignable — la liaison ne
/// relit que sur des déclencheurs SDK visibles : `UISceneWillEnterForeground` +
/// `UISceneDidActivate` (patron observer de phase 1) + `UIScreenModeDidChange` + la lecture
/// initiale postée. APRÈS un stimulus (resize/thème), chaque test poste donc
/// `UIScene.didActivateNotification` sur la scène VIVANTE (même pattern ruled que le repli
/// de disconnect de phase 1) — JAMAIS d'assertion verte sur une observation non déclenchée.
final class KadreUikitSurfaceTests: XCTestCase {

    /// Filet de sécurité ENTRE tests (même discipline que KadreUikitAttachTests) : toute
    /// session encore Starting/Running est arrêtée et attendue (borné).
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

    // MARK: - Helpers

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

    /// LE déclencheur ruled de re-lecture : `UIScene.didActivateNotification` posté sur la
    /// scène VIVANTE du fixture. Reposté à chaque itération de l'attente bornée — chaque post
    /// est une re-lecture SDK-visible réelle (une re-lecture dédupliquée ne publie rien, la
    /// suivante, après stabilisation du trait, publie) ; aucune lecture n'est inventée.
    /// `stimulus` (optionnel) est RE-POSÉ à chaque itération — le stimulus idempotent (un
    /// style imposé, une frame) reste VRAI pendant toute l'attente. L'échec embarque les faits
    /// de diagnostic : traits vivants de la vue, compteur de re-lectures Kotlin, journal du
    /// reporter réel et derniers événements capturés.
    /// `recordsTimeoutFailure` (fix CI xcode-27, échelle thème) : à `false`, une expiration NE
    /// pose PAS d'échec XCTest — c'est le mode des essais INTERMÉDIAIRES d'une échelle de
    /// stimuli (seul le verdict final de l'échelle fait foi) ; le diagnostic complet part dans
    /// l'erreur jetée (`userInfo["diagnostic"]`) et ressort au rethrow du palier final.
    func triggeredSurfaceState(
        _ fixture: SceneFixture,
        timeout: TimeInterval = 5,
        stimulus: (() -> Void)? = nil,
        recordsTimeoutFailure: Bool = true,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (SurfaceState) -> Bool,
    ) throws -> SurfaceState {
        let deadline = Date().addingTimeInterval(timeout)
        var last: SurfaceState?
        while true {
            stimulus?()
            NotificationCenter.default.post(name: UIScene.didActivateNotification, object: fixture.scene)
            if let surface = fixture.primarySurface, let state = surface.state.value as? SurfaceState {
                last = state
                if predicate(state) { return state }
            }
            if Date() > deadline {
                let viewTraits: String
                if let view = fixture.observedView, let window = fixture.window {
                    let style = view.traitCollection.userInterfaceStyle.rawValue
                    let scale = view.traitCollection.displayScale
                    let size = view.bounds.size
                    let inWindow = view.window.map { $0.isEqual(window) } ?? false
                    viewTraits = "style=\(style) scale=\(scale) bounds=\(size.width)x\(size.height) "
                        + "override=\(window.overrideUserInterfaceStyle.rawValue) "
                        + "windowTrait=\(window.traitCollection.userInterfaceStyle.rawValue) "
                        + "viewInWindow=\(inWindow) windowHidden=\(window.isHidden)"
                } else {
                    viewTraits = "nil"
                }
                let diagnostic = "triggered surface state not reached in \(timeout)s — last=\(String(describing: last)) "
                    + "viewTraits=\(viewTraits) "
                    + "observations=\(KadreUikitSurfaceDriver.shared.observationCount) "
                    + "failures=\(KadreUikitSurfaceDriver.shared.recentFailureDescriptions) "
                    + "trace=\(DriverTrace.recent())"
                if recordsTimeoutFailure {
                    XCTFail(diagnostic, file: file, line: line)
                }
                throw NSError(domain: "KadreUikitDriver", code: 6,
                              userInfo: ["what": "triggered surface state not reached",
                                         "diagnostic": diagnostic])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
    }

    /// Drain borné de la capture d'événements (le pumping du run loop EST le drain — les
    /// publications vivent sur le main de la session ; aucun sommeil nu). `liveState` (optionnel)
    /// embarque l'état VIVANT de la surface dans l'échec — un drain qui n'arrive pas doit dire
    /// si la surface a bougé (révision) ou pas.
    func drain(
        _ capture: KadreUikitSurfaceEventCapture,
        until predicate: @escaping (KadreUikitSurfaceEventCapture) -> Bool,
        timeout: TimeInterval = 5,
        liveState: (() -> SurfaceState?)? = nil,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while !predicate(capture) {
            if Date() > deadline {
                let live = liveState.map { $0() }.map { String(describing: $0) } ?? "n/a"
                XCTFail("event drain not reached in \(timeout)s — total=\(capture.totalCount) "
                    + "redraw=\(capture.redrawCount) metrics=\(capture.metricsCount) "
                    + "appearance=\(capture.appearanceCount) visibility=\(capture.visibilityCount) "
                    + "focus=\(capture.focusCount) alive=\(capture.isCollectAlive) "
                    + "error=\(capture.collectErrorDescription ?? "none") "
                    + "droppedReports=\(KadreUikitSurfaceDriver.shared.reportedFailureCount) "
                    + "dropJournal=\(KadreUikitSurfaceDriver.shared.recentFailureDescriptions) "
                    + "liveState=\(live) "
                    + "recent=\(capture.recentDescriptions)",
                    file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 5,
                              userInfo: ["what": "event drain not reached"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// Fenêtre de quiescence bornée : pendant `window` secondes, le compteur de redraw ne doit
    /// PAS bouger — la preuve d'ABSENCE (un coalescing truqué en émettrait un second).
    func assertSettled(
        _ capture: KadreUikitSurfaceEventCapture,
        ticks: Int32,
        window: TimeInterval = 0.5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let deadline = Date().addingTimeInterval(window)
        while Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            if capture.redrawCount != ticks {
                XCTFail("RedrawRequested moved during the settle window: \(capture.redrawCount) ≠ \(ticks)",
                        file: file, line: line)
                return
            }
        }
    }

    /// Le reporter réel (Task-3 follow-up) : AUCUNE observation ne peut être abandonnée ni
    /// aucun throwable avalé sur une suite verte — le compteur est process-wide, tout report
    /// d'un test antérieur criera ICI aussi.
    func assertNoDroppedObservations(
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.reportedFailureCount, 0,
                       "the real failure reporter recorded dropped work: "
                           + "\(KadreUikitSurfaceDriver.shared.recentFailureDescriptions)",
                       file: file, line: line)
    }

    /// Lifecycle Kadre attendu, borné (snapshot visibility/activation, comparaison `===`).
    func awaitLifecycle(
        _ fixture: SceneFixture,
        visibility: VisibilityState,
        activation: ActivationState,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
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

    /// L'invariant des métriques publiées : le physique est DÉRIVÉ du couple (logique, scale)
    /// avec l'arrondi Ceil de `LogicalSize.toPhysical` (Geometry.kt l.96-104).
    func assertPhysicalDerivesFromLogical(
        _ state: SurfaceState,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let wantWidth = Int32((state.logicalSize.width * state.scaleFactor).rounded(.up))
        let wantHeight = Int32((state.logicalSize.height * state.scaleFactor).rounded(.up))
        XCTAssertEqual(state.physicalSize.width, wantWidth, "physical width must derive (ceil)", file: file, line: line)
        XCTAssertEqual(state.physicalSize.height, wantHeight, "physical height must derive (ceil)", file: file, line: line)
    }

    /// Amorce de la capture : prouver que le collecteur du fixture est INSCRIT avant tout
    /// stimulus à coup unique. L'inscription vit sur un dispatcher de fond (asynchrone) et une
    /// publication antérieure à l'inscription est PERDUE (flow froid, pas de replay —
    /// `publishEvent` ne sert que les inscrits) : une demande de redraw d'amorce est donc
    /// REPOSBÉE (borné) jusqu'à preuve de livraison — la PREMIÈRE livraison prouve l'inscription
    /// (le collecteur ne se désinscrit qu'au `stop`). Après l'amorce, aucun événement du test ne
    /// peut plus être perdu. Chaque amorce est un `requestRedraw` réel (admission port réelle).
    func primeCapture(
        _ capture: KadreUikitSurfaceEventCapture,
        on surface: any HostSurface,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        var primed = false
        for _ in 0..<10 where !primed {
            _ = surface.requestRedraw()
            let deadline = Date().addingTimeInterval(2)
            while Date() < deadline && capture.redrawCount == 0 {
                RunLoop.current.run(until: Date().addingTimeInterval(0.02))
            }
            primed = capture.redrawCount >= 1
        }
        XCTAssertTrue(primed, "the event capture must prove delivery before any one-shot stimulus",
                      file: file, line: line)
    }

    // MARK: - Scénarios partagés (iOS + tvOS)

    /// uikit-surface-metrics-match-view — les métriques publiées à l'attach valent la lecture
    /// Swift de la vue observée : logical == bounds, physical == logical × scale (ceil),
    /// scale == displayScale, safeArea == insets Swift.
    func testSurfaceMetricsMatchTheObservedView() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)

        let state = try fixture.waitUntilSurfaceState(timeout: 5) { candidate in
            abs(candidate.logicalSize.width - Double(view.bounds.width)) < 0.001
                && abs(candidate.logicalSize.height - Double(view.bounds.height)) < 0.001
        }

        XCTAssertEqual(state.logicalSize.width, Double(view.bounds.width), accuracy: 0.001)
        XCTAssertEqual(state.logicalSize.height, Double(view.bounds.height), accuracy: 0.001)
        XCTAssertEqual(state.scaleFactor, Double(view.traitCollection.displayScale), accuracy: 0.001)
        XCTAssertEqual(state.safeAreaInsets.top, Double(view.safeAreaInsets.top), accuracy: 0.001)
        XCTAssertEqual(state.safeAreaInsets.right, Double(view.safeAreaInsets.right), accuracy: 0.001)
        XCTAssertEqual(state.safeAreaInsets.bottom, Double(view.safeAreaInsets.bottom), accuracy: 0.001)
        XCTAssertEqual(state.safeAreaInsets.left, Double(view.safeAreaInsets.left), accuracy: 0.001)
        assertPhysicalDerivesFromLogical(state)
        assertNoDroppedObservations()

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-surface-resize-propagates — un resize RÉEL de la vue (setFrame + layoutIfNeeded)
    /// suivi du déclencheur ruled (didActivate posté) propage les nouvelles métriques : state
    /// == nouvelles bounds, physique dérivé, et un `MetricsChanged` publié par différence.
    /// Tue la sentinelle uikit-metrics-stale-generation : des métriques figées à la génération
    /// d'attach ne porteraient jamais les nouvelles dimensions.
    func testSurfaceResizePropagatesMetrics() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let capture = KadreUikitSurfaceDriver.shared.captureEvents(surface: surface)
        defer { capture.stop() }
        primeCapture(capture, on: surface)

        view.frame = CGRect(x: 0, y: 0, width: 480, height: 200)
        view.superview?.layoutIfNeeded()
        XCTAssertEqual(view.bounds.width, 480, accuracy: 0.001)

        let state = try triggeredSurfaceState(fixture, timeout: 5) { candidate in
            abs(candidate.logicalSize.width - 480.0) < 0.001
                && abs(candidate.logicalSize.height - 200.0) < 0.001
        }
        assertPhysicalDerivesFromLogical(state)
        // Le STATE est publié sur le main (StateFlow direct) ; l'ÉVÉNEMENT voyage par la
        // file des lanes vers le collecteur de fond — il peut atterrir quelques battements
        // APRÈS l'état. Le drain borné synchronise l'assertion SUR LE CANAL des événements :
        // même force (>= 1 MetricsChanged publié par différence), zéro course de lecture.
        try drain(capture, until: { $0.metricsCount >= 1 }, timeout: 5)
        XCTAssertGreaterThanOrEqual(capture.metricsCount, 1, "the resize must publish MetricsChanged — "
            + "total=\(capture.totalCount) alive=\(capture.isCollectAlive) "
            + "error=\(capture.collectErrorDescription ?? "none") recent=\(capture.recentDescriptions)")
        assertNoDroppedObservations()
        _ = surface

        try stopAndAwaitTermination(fixture)
    }

    /// La SÉRIE de stimuli d'un changement de thème, en échelle de TROIS paliers, chacun
    /// tracé (NSLog + DriverTrace) et borné (fix CI xcode-27, runs 38225688267 : sur les
    /// boots sombres du runner, la publication d'une transition n'était pas déterministe —
    /// la propagation fenêtre→vue d'UIKit n'aboutit pas à tous les coups). Chaque palier est
    /// UNE tentative `triggeredSurfaceState` (essais intermédiaires NON-empoisonnés — seul
    /// le verdict du palier FINAL fait foi, par rethrow avec tout le diagnostic ; la preuve
    /// de publication ne BOUGE pas : thème visé ET révision qui avance) :
    /// 1. `window.overrideUserInterfaceStyle` — la route du brief (3s) ;
    /// 2. `view.traitOverrides.userInterfaceStyle` — iOS/tvOS 17+ : posé sur les overrides
    ///    de trait PROPRES de la vue, SANS dépendance de la propagation fenêtre→vue (3s) ;
    /// 3. `view.overrideUserInterfaceStyle` — le repli dégradé documenté (5s, échec final).
    /// Sur le plancher 16.0 déclaré, le palier 2 est sauté (gate `#available`) et l'échelle
    /// tombe au palier 3 exactement comme avant. Retourne l'état publié et la route gagnante.
    func themeStimulusLadder(
        _ style: UIUserInterfaceStyle,
        label: String,
        fixture: SceneFixture,
        window: UIWindow,
        view: UIView,
        base: SurfaceState,
    ) throws -> (state: SurfaceState, route: String) {
        let target: SurfaceTheme = style == .dark ? SurfaceTheme.dark : SurfaceTheme.light

        // Palier 1 — la route fenêtre du brief.
        NSLog("KADRE-DRIVER theme \(label): stage 1 window.overrideUserInterfaceStyle attempt")
        DriverTrace.record("theme \(label): stage 1 window override")
        do {
            let state = try triggeredSurfaceState(fixture, timeout: 3, stimulus: {
                window.overrideUserInterfaceStyle = style
                window.layoutIfNeeded()
            }, recordsTimeoutFailure: false) { $0.appearance.theme === target && $0.revision > base.revision }
            return (state, "window")
        } catch {
            NSLog("KADRE-DRIVER theme \(label): stage 1 window override never published "
                + "(windowTrait=\(window.traitCollection.userInterfaceStyle.rawValue) "
                + "view=\(view.traitCollection.userInterfaceStyle.rawValue)) — falling through")
        }

        // Palier 2 — les overrides de trait PROPRE de la vue (iOS/tvOS 17+) : le stimulus
        // intermédiaire FIABLE, indépendant de la propagation fenêtre→vue d'UIKit.
        if #available(iOS 17.0, tvOS 17.0, *) {
            NSLog("KADRE-DRIVER theme \(label): stage 2 view.traitOverrides attempt")
            DriverTrace.record("theme \(label): stage 2 view traitOverrides")
            do {
                let state = try triggeredSurfaceState(fixture, timeout: 3, stimulus: {
                    view.traitOverrides.userInterfaceStyle = style
                }, recordsTimeoutFailure: false) { $0.appearance.theme === target && $0.revision > base.revision }
                return (state, "view.traitOverrides")
            } catch {
                NSLog("KADRE-DRIVER theme \(label): stage 2 view traitOverrides never published "
                    + "(view traitCollection=\(view.traitCollection.userInterfaceStyle.rawValue)) — falling through")
            }
        }

        // Palier 3 — le repli dégradé documenté (l'override posé sur la vue elle-même).
        // DERNIER palier : son expiration est le verdict de la transition (échec XCTest
        // riche + rethrow, comportement des essais simples).
        NSLog("KADRE-DRIVER theme \(label): stage 3 view.overrideUserInterfaceStyle (degraded) attempt")
        DriverTrace.record("theme \(label): stage 3 view override (degraded)")
        let state = try triggeredSurfaceState(fixture, timeout: 5, stimulus: {
            view.overrideUserInterfaceStyle = style
            window.layoutIfNeeded()
        }) { $0.appearance.theme === target && $0.revision > base.revision }
        return (state, "view.overrideUserInterfaceStyle")
    }

    /// uikit-surface-theme-propagates — le thème publié suit l'override réel de l'interface :
    /// chaque transition passe par l'échelle de stimuli `themeStimulusLadder` (route fenêtre
    /// du brief, puis `view.traitOverrides` iOS/tvOS 17+, puis le repli dégradé sur la vue).
    /// Chaque transition EXIGE une publication réelle (thème visé ET révision qui avance,
    /// prouvée AUSSI par l'événement drainé) — jamais un match de l'état ambiant.
    ///
    /// BASELINE DU DRAIN (fix CI xcode-27, run 38225688267) : la baseline d'apparence est
    /// prise AVANT l'échelle — la publication de la transition (stimulus OU stabilisation
    /// ambiante, la MÊME publication du kernel) doit atterrir APRÈS elle. Prise après coup,
    /// elle ratait l'événement déjà collecté (boot sombre : la vue démarre Light et bascule
    /// Dark seule à +44ms — l'état publiait Dark rev 1, le drain exigeait une SECONDE
    /// publication qui ne viendrait jamais) : course de baseline, rouge déterministe.
    ///
    /// FAITS CAPTURÉS (Task 5) : sur iPadOS 27 la fenêtre propage vers la vue (route fenêtre
    /// prouvée) ; le thème AMBIANT du simulateur varie d'un boot à l'autre (Light ou Dark —
    /// l'état initial du runtime porte Dark) et les boots sombres du runner xcode-27
    /// stabilisent leur trait en course avec le test. Le test s'adapte honnêtement : le sol
    /// est la VÉRITÉ VIVANTE de la vue ; la transition Dark→Light est TOUJOURS exigée avec
    /// publication + événement ; la transition retour vers Dark n'est exigée QUE si le sol
    /// n'était pas déjà Dark (un thème inchangé est dédupliqué par le kernel — comportement
    /// ruled, jamais un événement fabriqué). La route retenue est tracée (NSLog + DriverTrace)
    /// et rapportée.
    func testSurfaceThemePropagatesFromTheWindow() throws {
        let (fixture, _, surface) = try attachedSurface()
        let window = try XCTUnwrap(fixture.window)
        let view = try XCTUnwrap(fixture.observedView)
        let capture = KadreUikitSurfaceDriver.shared.captureEvents(surface: surface)
        defer { capture.stop() }
        primeCapture(capture, on: surface)

        // La vérité vivante de la vue (style UIKit : 1 = light, 2 = dark).
        func viewTruthTheme() -> SurfaceTheme? {
            switch view.traitCollection.userInterfaceStyle.rawValue {
            case 2: return SurfaceTheme.dark
            case 1: return SurfaceTheme.light
            default: return nil
            }
        }

        // SOL : l'état publié reflète la traitCollection vivante de la vue (la re-lecture
        // d'attache a couru — publié, ou dédupliqué si l'ambiant vaut déjà l'état initial).
        let base = try triggeredSurfaceState(fixture, timeout: 5) { state in
            guard let truth = viewTruthTheme() else { return false }
            return state.appearance.theme === truth
        }

        // Transition Light → Dark (exigée SEULEMENT si le sol n'est pas déjà Dark — un thème
        // inchangé est dédupliqué par le kernel, comportement ruled sans événement fabriqué).
        // Baseline AVANT le stimulus : la publication de la transition est prouvée APRÈS elle.
        var darkRoute = "ambient (dedup — no transition)"
        if base.appearance.theme !== SurfaceTheme.dark {
            let eventsBeforeDark = capture.appearanceCount
            window.overrideUserInterfaceStyle = .dark
            window.layoutIfNeeded()
            let (dark, route) = try themeStimulusLadder(
                .dark, label: "dark", fixture: fixture, window: window, view: view, base: base)
            darkRoute = route
            try drain(capture, until: { $0.appearanceCount >= eventsBeforeDark + 1 }, timeout: 5, liveState: {
                fixture.primarySurface?.state.value as? SurfaceState
            })
            NSLog("KADRE-DRIVER theme Dark published (route=\(route)) state=\(dark)")
        } else {
            NSLog("KADRE-DRIVER theme Dark already ambient at base — the Dark transition is a "
                + "ruled dedup (no publication expected); Light→Dark proof is carried by the "
                + "next phase's publication")
        }

        // Transition → Light : TOUJOURS exigée avec publication réelle (le Dark est acquis —
        // ambiant ou publié — la publication Light est la preuve de propagation de la paire).
        // Baseline AVANT le stimulus, même discipline que la transition Dark.
        let eventsBeforeLight = capture.appearanceCount
        window.overrideUserInterfaceStyle = .light
        window.layoutIfNeeded()
        let (light, lightRoute) = try themeStimulusLadder(
            .light, label: "light", fixture: fixture, window: window, view: view, base: base)
        XCTAssertEqual(light.appearance.theme, SurfaceTheme.light)
        try drain(capture, until: { $0.appearanceCount >= eventsBeforeLight + 1 }, timeout: 5, liveState: {
            fixture.primarySurface?.state.value as? SurfaceState
        })
        XCTAssertGreaterThan(capture.appearanceCount, eventsBeforeLight,
                             "the Light publication must emit AppearanceChanged")
        NSLog("KADRE-DRIVER uikit-surface-theme-propagates routes: dark=\(darkRoute) light=\(lightRoute)")
        DriverTrace.record("theme routes: dark=\(darkRoute) light=\(lightRoute)")

        window.overrideUserInterfaceStyle = .unspecified
        view.overrideUserInterfaceStyle = .unspecified
        if #available(iOS 17.0, tvOS 17.0, *) {
            view.traitOverrides.userInterfaceStyle = .unspecified
        }
        assertNoDroppedObservations()

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-surface-redraw-coalesced — une rafale de 5 `requestRedraw()` en une passe du run
    /// loop produit EXACTEMENT UN `RedrawRequested` (fenêtre de quiescence bornée), une seconde
    /// rafale exactement un de plus : un par tick. Tue la sentinelle
    /// uikit-redraw-single-per-tick (un compteur de drains non coalescé émettrait 5+3).
    func testSurfaceRedrawBurstCoalescesToOnePerTick() throws {
        let (fixture, _, surface) = try attachedSurface()
        let capture = KadreUikitSurfaceDriver.shared.captureEvents(surface: surface)
        defer { capture.stop() }
        primeCapture(capture, on: surface)
        let priming = capture.redrawCount

        for _ in 0..<5 {
            XCTAssertFalse(surface.requestRedraw() is KadreResultFailure,
                           "every redraw of the burst must be admitted")
        }
        try drain(capture, until: { $0.redrawCount >= priming + 1 })
        assertSettled(capture, ticks: priming + 1)
        XCTAssertEqual(capture.redrawCount, priming + 1,
                       "a burst of 5 must coalesce to exactly one tick")

        for _ in 0..<3 {
            XCTAssertFalse(surface.requestRedraw() is KadreResultFailure)
        }
        try drain(capture, until: { $0.redrawCount >= priming + 2 })
        assertSettled(capture, ticks: priming + 2)
        XCTAssertEqual(capture.redrawCount, priming + 2, "each tick emits exactly one RedrawRequested")
        assertNoDroppedObservations()

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-surface-redraw-after-close — après close de la session : la surface est
    /// TERMINALE (Task-1-M2 : attachment Detached — aucune surface vivante ne survit à un
    /// ticket absorbé), `requestRedraw()` échoue fermé `Closed(Surface)`, et aucun événement
    /// de plus n'est publié (fenêtre de quiescence).
    func testSurfaceRedrawAfterCloseFailsClosed() throws {
        let (fixture, _, surface) = try attachedSurface()
        let capture = KadreUikitSurfaceDriver.shared.captureEvents(surface: surface)
        defer { capture.stop() }
        primeCapture(capture, on: surface)
        let priming = capture.redrawCount

        XCTAssertFalse(surface.requestRedraw() is KadreResultFailure)
        try drain(capture, until: { $0.redrawCount >= priming + 1 })
        let redrawsAtClose = capture.redrawCount

        try stopAndAwaitTermination(fixture)

        // Task-1-M2 : la surface est terminale après l'absorption du ticket.
        let state = try XCTUnwrap(surface.state.value as? SurfaceState)
        XCTAssertTrue(state.attachment === SurfaceAttachmentState.detached,
                      "the surface must be terminal after close: \(state)")

        let result = surface.requestRedraw()
        guard let failure = (result as? KadreResultFailure)?.reason else {
            return XCTFail("requestRedraw after close must fail, got \(result)")
        }
        guard let closed = failure as? KadreFailureClosed else {
            return XCTFail("expected Closed(Surface), got \(failure)")
        }
        XCTAssertTrue(closed.resource === KadreResourceKind.surface)

        assertSettled(capture, ticks: redrawsAtClose)
        XCTAssertEqual(capture.redrawCount, redrawsAtClose, "no redraw event may follow the close")
        assertNoDroppedObservations()
    }

    /// uikit-surface-apply-fields-refused — l'échelle `apply` fermée : tout champ POSÉ est
    /// rejeté `Unsupported(UpdateSurface)` par champ dans un `PartiallyApplied` (pas une
    /// failure externe) ; tout `Unchanged` donne `Applied`.
    func testSurfaceApplyRefusesSetFieldsAndAcceptsAllUnchanged() throws {
        let (fixture, _, surface) = try attachedSurface()

        let setUpdate = SurfaceUpdate(
            cursor: PropertyChangeSet(value: CursorStyleSystem(icon: CursorIcon.default_)),
            pointerCapture: PropertyChangeUnchanged(),
            hitTesting: PropertyChangeUnchanged(),
            inputDefaultBehavior: PropertyChangeUnchanged(),
            expectedRevision: nil,
        )
        let applied = expectation(description: "apply(cursor set) delivered")
        surface.apply(update: setUpdate) { result, error in
            defer { applied.fulfill() }
            XCTAssertNil(error, "apply must not error")
            guard let success = result as? KadreResultSuccess<AnyObject> else {
                return XCTFail("expected KadreResultSuccess, got \(String(describing: result))")
            }
            guard let partial = success.value as? SurfaceUpdateOutcomePartiallyApplied else {
                return XCTFail("expected PartiallyApplied, got \(String(describing: success.value))")
            }
            XCTAssertEqual(partial.rejected.count, 1, "exactly the set field is rejected")
            guard let rejected = partial.rejected.first else { return }
            XCTAssertTrue(rejected.field === SurfaceProperty.cursor, "the cursor field is the rejected one")
            guard let unsupported = rejected.failure as? KadreFailureUnsupported else {
                return XCTFail("expected Unsupported(UpdateSurface), got \(rejected.failure)")
            }
            XCTAssertTrue(unsupported.operation === KadreOperation.updatesurface)
        }
        wait(for: [applied], timeout: 10)

        let unchangedUpdate = SurfaceUpdate(
            cursor: PropertyChangeUnchanged(),
            pointerCapture: PropertyChangeUnchanged(),
            hitTesting: PropertyChangeUnchanged(),
            inputDefaultBehavior: PropertyChangeUnchanged(),
            expectedRevision: nil,
        )
        let unchanged = expectation(description: "apply(all unchanged) delivered")
        surface.apply(update: unchangedUpdate) { result, error in
            defer { unchanged.fulfill() }
            XCTAssertNil(error, "apply must not error")
            guard let success = result as? KadreResultSuccess<AnyObject> else {
                return XCTFail("expected KadreResultSuccess, got \(String(describing: result))")
            }
            XCTAssertTrue(success.value is SurfaceUpdateOutcomeApplied,
                          "an all-Unchanged apply must be Applied, got \(String(describing: success.value))")
        }
        wait(for: [unchanged], timeout: 10)

        assertNoDroppedObservations()
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-lease-boundaries — le bail natif (`withUIKitView`) : le premier bail est ADMIS et
    /// la vue remise EST la surfaceView (`isEqual`) ; le second bail PENDANT le vol (tenté dans
    /// la callback du premier) répond `TemporarilyUnavailable(retryable = true)` sans invoquer
    /// son block ; après terminaison, l'issue steady ruled est `Unsupported(PlatformSurfaceAccess)`
    /// (la route du registre est retirée au close — `Closed(Surface)` n'est que la course en vol).
    /// Tue la sentinelle uikit-lease-closed-after-terminal.
    ///
    /// VOIE (audit façade phase 3) : `withUIKitView` n'est PLUS exporté vers Swift — masqué
    /// des exports Obj-C/Swift (`@HiddenFromObjC`, Kotlin-only strict, INTEROP §7 ; la
    /// divergence F1 phase 2 est résolue par suppression). La preuve passe par la sonde
    /// Kotlin (`KadreUikitSurfaceDriver.leaseProbe`/`closedLeaseProbe`) — l'exigence de bail
    /// concurrent DANS le premier callback y est matériellement plus propre (closures +
    /// vérification single-flight) et c'est depuis la phase 3 la SEULE route Swift du bail.
    func testLeaseBoundariesHonoursFlightAndClose() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)

        let probe = KadreUikitSurfaceDriver.shared.leaseProbe(surface: surface, expectedView: view)
        XCTAssertNil(probe.threw, "the lease path must not throw: \(probe.threw ?? "")")
        XCTAssertTrue(probe.granted, "the first lease must be granted: \(probe.grantedFailureDescription ?? "")")
        XCTAssertTrue(probe.grantedViewMatches, "the leased view must isEqual the host surfaceView")
        XCTAssertFalse(probe.concurrentGranted, "a lease started during an in-flight one must not be granted")
        XCTAssertNotNil(probe.concurrentFailureDescription)
        XCTAssertTrue(probe.concurrentRetryable,
                      "the in-flight refusal must be retryable TemporarilyUnavailable: "
                          + "\(probe.concurrentFailureDescription ?? "")")
        assertNoDroppedObservations()

        try stopAndAwaitTermination(fixture)

        let closed = KadreUikitSurfaceDriver.shared.closedLeaseProbe(surface: surface)
        XCTAssertTrue(closed.unsupportedPlatformSurfaceAccess,
                      "the steady post-close lease outcome is Unsupported(PlatformSurfaceAccess), got: "
                          + closed.outcomeDescription)
    }

    /// uikit-surface-reprise-rereads — la reprise : background (notification réelle, patron
    /// phase 1) puis foreground+activation conduisent le lifecycle Kadre, et le chemin de
    /// RE-LECTURE est prouvé par le compteur d'observations du fixture (il s'incrémente) ;
    /// l'état reste cohérent avec les bounds vivantes après la reprise.
    func testSurfaceRepriseRereadsObservations() throws {
        let (fixture, _, surface) = try attachedSurface()
        let view = try XCTUnwrap(fixture.observedView)
        let observationsBefore = KadreUikitSurfaceDriver.shared.observationCount

        NotificationCenter.default.post(name: UIScene.didEnterBackgroundNotification, object: fixture.scene)
        try awaitLifecycle(fixture, visibility: .background, activation: .inactive)

        NotificationCenter.default.post(name: UIScene.willEnterForegroundNotification, object: fixture.scene)
        NotificationCenter.default.post(name: UIScene.didActivateNotification, object: fixture.scene)
        try awaitLifecycle(fixture, visibility: .foreground, activation: .active)

        let observationsAfter = KadreUikitSurfaceDriver.shared.observationCount
        XCTAssertGreaterThan(observationsAfter, observationsBefore,
                             "the reprise must have driven real re-reads")

        let width = Double(view.bounds.width)
        let height = Double(view.bounds.height)
        let state = try triggeredSurfaceState(fixture, timeout: 5) { candidate in
            abs(candidate.logicalSize.width - width) < 0.001
                && abs(candidate.logicalSize.height - height) < 0.001
        }
        XCTAssertEqual(state.scaleFactor, Double(view.traitCollection.displayScale), accuracy: 0.001)
        assertPhysicalDerivesFromLogical(state)
        _ = surface
        assertNoDroppedObservations()

        try stopAndAwaitTermination(fixture)
    }
}
