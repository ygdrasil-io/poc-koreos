import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves phase 8 (Task 6) — le relais mémoire RÉEL : la notification
/// `UIApplicationDidReceiveMemoryWarningNotification` POSTÉE dans le process est le stimulus
/// SANCTIONNÉ (ruling Task 6 : l'observateur réel → la source process → le relais hors pile →
/// `emitMemoryPressure` → les signaux de session — la chaîne ENTRE les extrémités ; la décision
/// de l'OS de tirer la notification elle-même reste le charter manuel). Noms de scénarios FIGÉS
/// pour Task 7 (la résolution phase 7 : les ids hyphénés du registre ne sont PAS des noms de
/// méthode) :
///
///   uikit-memory-pressure-real-mapping                → test_uikit_memory_pressure_real_mapping()
///   uikit-memory-no-lifecycle-signal                  → test_uikit_memory_no_lifecycle_signal()
///   uikit-memory-capability-refused-when-unregistered → test_uikit_memory_capability_refused_when_unregistered()
///
/// FAITS ÉPELÉS PAR SCÉNARIO :
///
/// 1. MAPPING RÉEL (uikit-memory-pressure-real-mapping) — la kernel normative est
///    UNE-ligne : le memory warning → `Moderate`, l'UNIQUE mapping (la notification est
///    non-graduée ; `Critical` est une gravité INVENTÉE que UIKit/tvOS ne porte pas). Un post
///    → EXACTEMENT un signal Moderate sur `KadreLifecycle.signals` ; deux posts → exactement
///    deux ; JAMAIS un Critical. La capability du scope est `Available` (posée par l'install
///    réel de la source process à l'attach — claim-based).
/// 2. LIFECYCLE JAMAIS (uikit-memory-no-lifecycle-signal) — les transitions réelles de scène
///    (les notifications `UIScene.didEnterBackground/willEnterForeground/didActivate/
///    willDeactivate` POSTÉES in-process — le canal exact que l'observateur de lifecycle
///    écoute, le précédent phase 1 `uikit-lifecycle-transitions`) ne produisent JAMAIS un
///    signal de pression mémoire : la perte de visibilité n'est pas de la pression (miroir
///    Android `android-memory-background-never-maps`). Le contrôle positif (un post mémoire)
///    prouve ensuite que le canal de réception était VIVANT — le silence n'était pas un
///    collecteur mort.
/// 3. REFUS HORS SESSION (uikit-memory-capability-refused-when-unregistered) — sans host
///    vivant, la source process est fermée par la terminaison du DERNIER host : la capability
///    process relit `Unsupported` (jamais tentée / fermée — JAMAIS une PlatformFailure
///    inventée), le chemin d'émission est structurellement INATTEIGNABLE (plus d'observateur,
///    plus de relais) et la règle pre-wire tient : un warning posté hors session est PERDU,
///    jamais bufférisé — la session suivante n'en reçoit RIEN (la pression est un état
///    transient, un niveau rejoué mentirait sur le présent), puis son propre install rétablit
///    le canal (contrôle positif : exactement un Moderate).
final class KadreUikitMemoryPressureTests: KadreUikitPhase8FixtureCase {

    /// Le stimulus sanctionné : le post RÉEL de la notification memory warning du process
    /// (l'observateur block-based du source production est le récepteur exact ; la livraison
    /// part sur la file principale de l'observateur, le relais hors pile la rejoue au tour de
    /// file suivant — deux sauts que le pumping du run loop draine).
    func postMemoryWarning() {
        NotificationCenter.default.post(name: UIApplication.didReceiveMemoryWarningNotification,
                                        object: nil)
    }

    /// Amorce de la capture de signaux : le flow est FROID (aucun replay) — le test attend le
    /// collecteur vivant PUIS pompe une fenêtre de garde (le pattern `awaitCollector` phase 6).
    func awaitCollector(
        _ capture: KadreUikitLifecycleSignalCapture,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while !capture.isCollectAlive {
            if Date() > deadline {
                XCTFail("lifecycle signal collector never started in \(timeout)s "
                    + "error=\(capture.collectErrorDescription ?? "none")", file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 7,
                              userInfo: ["what": "signal collector never started"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
        pump(0.2)
    }

    /// Le signal mémoire attendu — drain borné par pumping (la publication vit sur la file
    /// principale, la collecte sur le dispatcher de fond).
    func awaitMemorySignal(
        _ capture: KadreUikitLifecycleSignalCapture,
        minimum: Int,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while capture.totalCount < minimum {
            if Date() > deadline {
                XCTFail("only \(capture.totalCount) memory signal(s) within \(timeout)s (wanted >= \(minimum)) "
                    + "moderate=\(capture.moderateCount) critical=\(capture.criticalCount) "
                    + "alive=\(capture.isCollectAlive) error=\(capture.collectErrorDescription ?? "none") "
                    + "recent=\(capture.recentDescriptions)", file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 5,
                              userInfo: ["what": "memory signal drain not reached"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// Fenêtre bornée du silence : aucun signal supplémentaire (le vide est observé, jamais
    /// déduit d'un seul instant).
    func assertSignalSilence(
        _ capture: KadreUikitLifecycleSignalCapture,
        context: String,
        window: TimeInterval = 0.5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let baseline = capture.totalCount
        pump(window)
        XCTAssertEqual(capture.totalCount, baseline,
                       "\(context): a memory signal leaked: \(capture.recentDescriptions)",
                       file: file, line: line)
    }

    /// Le snapshot lifecycle de la session dans un état attendu — attente bornée (les
    /// notifications postées partent par l'observateur réel sur la file principale).
    func awaitLifecycle(
        _ fixture: SceneFixture,
        _ what: String,
        visibility: VisibilityState,
        activation: ActivationState,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while true {
            if let snapshot = fixture.lifecycleSnapshot(),
               snapshot.visibility == visibility, snapshot.activation == activation {
                return
            }
            if Date() > deadline {
                XCTFail("\(what) not reached in \(timeout)s — last=\(String(describing: fixture.lifecycleDescription()))",
                        file: file, line: line)
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// La capability process dans un état attendu — attente bornée (la fermeture de la source
    /// part de la traversée du terminal du DERNIER host).
    func awaitMemoryCapability(
        _ what: String,
        unavailable: Bool,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while true {
            let available = driver.memoryPressureCapability.memoryPressure is FeatureAvailabilityAvailable
            if available != unavailable { return }
            if Date() > deadline {
                XCTFail("\(what) not reached in \(timeout)s — capability=\(driver.memoryPressureCapability)",
                        file: file, line: line)
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    // MARK: - Scénarios

    /// uikit-memory-pressure-real-mapping — le post RÉEL de la notification memory warning
    /// traverse l'observateur → la source process → le relais hors pile → `emitMemoryPressure`
    /// → les signaux de la session : UN post → EXACTEMENT un `MemoryPressure(Moderate)` ;
    /// deux posts → exactement deux ; JAMAIS un Critical (la kernel ne produit que le niveau
    /// unique — `Critical` est une gravité inventée sur UIKit/tvOS). La capability du scope
    /// est `Available` : l'install réel de la source process à l'attach la pose.
    func test_uikit_memory_pressure_real_mapping() throws {
        let (fixture, _, scope) = try attachedScope()

        // La capability posée par l'install réel de la source process à l'attach.
        let capabilities = try XCTUnwrap(scope.lifecycle.capabilities.value as? LifecycleCapabilities)
        XCTAssertTrue(capabilities.memoryPressure is FeatureAvailabilityAvailable,
                      "the memory pressure source is installed by the real attach, got "
                          + "\(capabilities.memoryPressure)")

        let capture = driver.captureLifecycleSignals(scope: scope)
        defer { capture.stop() }
        try awaitCollector(capture)

        // UN post — EXACTEMENT un signal, `Moderate`, jamais autre chose.
        postMemoryWarning()
        try awaitMemorySignal(capture, minimum: 1)
        assertSignalSilence(capture, context: "after the first post")
        XCTAssertEqual(capture.totalCount, 1, "exactly ONE signal per post")
        XCTAssertEqual(capture.moderateCount, 1, "the warning maps to Moderate — the unique level")
        XCTAssertEqual(capture.criticalCount, 0, "Critical is never produced on UIKit/tvOS")

        // DEUX posts au total — exactement deux signaux, le second suit le second post.
        postMemoryWarning()
        try awaitMemorySignal(capture, minimum: 2)
        assertSignalSilence(capture, context: "after the second post")
        XCTAssertEqual(capture.totalCount, 2, "exactly one signal per post — never a fan-out duplication")
        XCTAssertEqual(capture.moderateCount, 2, "both warnings map to Moderate")
        XCTAssertEqual(capture.criticalCount, 0, "no Critical ever leaks from the real relay")

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-memory-no-lifecycle-signal — les transitions RÉELLES de scène (les notifications
    /// `UIScene` postées in-process — le canal exact de l'observateur de lifecycle, précédent
    /// phase 1) passent fond ↔ premier plan ↔ inactif SANS produire JAMAIS un signal mémoire ;
    /// le contrôle positif (un post mémoire) prouve ensuite que le canal de réception était
    /// VIVANT — le silence n'était pas un collecteur mort (miroir Android
    /// `android-memory-background-never-maps`).
    func test_uikit_memory_no_lifecycle_signal() throws {
        let (fixture, _, scope) = try attachedScope()
        let capture = driver.captureLifecycleSignals(scope: scope)
        defer { capture.stop() }
        try awaitCollector(capture)
        let scene = fixture.scene

        // Les transitions du lifecycle, par le canal réel — chaque étape est VÉRIFIÉE
        // (l'état Kadre suit la scène) puis couverte par une fenêtre de silence.
        NotificationCenter.default.post(name: UIScene.didEnterBackgroundNotification, object: scene)
        try awaitLifecycle(fixture, "background transition", visibility: .background, activation: .inactive)
        assertSignalSilence(capture, context: "after didEnterBackground")

        NotificationCenter.default.post(name: UIScene.willEnterForegroundNotification, object: scene)
        NotificationCenter.default.post(name: UIScene.didActivateNotification, object: scene)
        try awaitLifecycle(fixture, "foreground-active transition", visibility: .foreground, activation: .active)
        assertSignalSilence(capture, context: "after willEnterForeground+didActivate")

        NotificationCenter.default.post(name: UIScene.willDeactivateNotification, object: scene)
        try awaitLifecycle(fixture, "inactive transition", visibility: .foreground, activation: .inactive)
        assertSignalSilence(capture, context: "after willDeactivate")

        // Le contrôle positif : le canal de réception était vivant — un post mémoire monte
        // exactement son Moderate pendant que les transitions n'ont RIEN dit.
        XCTAssertEqual(capture.totalCount, 0, "no lifecycle transition may speak as memory pressure")
        postMemoryWarning()
        try awaitMemorySignal(capture, minimum: 1)
        assertSignalSilence(capture, context: "after the positive-control post")
        XCTAssertEqual(capture.totalCount, 1)
        XCTAssertEqual(capture.moderateCount, 1)
        XCTAssertEqual(capture.criticalCount, 0)

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-memory-capability-refused-when-unregistered — hors session vivante, la source
    /// process est fermée (la terminaison du DERNIER host) : la capability process relit
    /// `Unsupported` (le défaut honnête « jamais tentée », jamais une PlatformFailure
    /// inventée), le chemin d'émission est structurellement inatteignable — un warning posté
    /// hors session est PERDU (la règle pre-wire : la pression est un état, jamais une file)
    /// et la session suivante n'en reçoit RIEN, puis son install rétablit exactement le canal
    /// (contrôle positif : exactement un Moderate).
    func test_uikit_memory_capability_refused_when_unregistered() throws {
        // (1) Un host vivant : la source process est installée — capability `Available`.
        let (fixture, _, scope) = try attachedScope()
        let capabilities = try XCTUnwrap(scope.lifecycle.capabilities.value as? LifecycleCapabilities)
        XCTAssertTrue(capabilities.memoryPressure is FeatureAvailabilityAvailable,
                      "a live host carries the installed capability, got \(capabilities.memoryPressure)")
        try awaitMemoryCapability("capability Available while a host lives", unavailable: false)

        // (2) La terminaison du DERNIER host ferme la source process : capability `Unsupported`
        //     — le refus honnête, jamais une PlatformFailure inventée.
        try stopAndAwaitTermination(fixture)
        try awaitMemoryCapability("capability Unsupported after the last host terminated", unavailable: true)
        XCTAssertFalse(driver.memoryPressureCapability.memoryPressure is FeatureAvailabilityUnavailable,
                       "a clean close is the DEFAULT refusal, never a fabricated platform failure")

        // (3) Un warning posté hors session : structurellement inatteignable — pas
        //     d'observateur, pas de relais, RIEN à fan-out.
        postMemoryWarning()

        // (4) La session suivante ne reçoit RIEN du post pré-wire — la pression est un état
        //     transient, un niveau rejoué mentirait sur le présent.
        let (freshFixture, _, freshScope) = try attachedScope()
        let freshCapture = driver.captureLifecycleSignals(scope: freshScope)
        defer { freshCapture.stop() }
        try awaitCollector(freshCapture)
        try awaitMemoryCapability("capability Available again for the fresh host", unavailable: false)
        assertSignalSilence(freshCapture, context: "the pre-wire post must stay lost", window: 0.6)
        XCTAssertEqual(freshCapture.totalCount, 0,
                       "a warning observed while NO host was wired is never buffered, never replayed")

        // (5) Le contrôle positif : l'install de la session fraîche a rétabli le canal exact.
        postMemoryWarning()
        try awaitMemorySignal(freshCapture, minimum: 1)
        assertSignalSilence(freshCapture, context: "after the fresh-session control post")
        XCTAssertEqual(freshCapture.totalCount, 1)
        XCTAssertEqual(freshCapture.moderateCount, 1, "the reinstalled source maps to Moderate again")
        XCTAssertEqual(freshCapture.criticalCount, 0)

        try stopAndAwaitTermination(freshFixture)
        try awaitMemoryCapability("capability Unsupported after the fresh host terminated", unavailable: true)
    }
}
