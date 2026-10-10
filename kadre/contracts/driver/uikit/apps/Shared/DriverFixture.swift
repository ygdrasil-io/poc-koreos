import UIKit
import KadreUikit

/// Fixture D'UNE scène : la fenêtre et les vues que le host fournit, plus tout ce que
/// l'application Kadre (`DriverKadreApplication`) a capturé pour la session de cette scène.
/// Identité : `ObjectIdentifier(scene)` — le store `DriverFixture` est la source de vérité.
final class SceneFixture {
    let scene: UIWindowScene

    // Objets natifs fournis par le host (forme phase 0, une instance par scène).
    private(set) var window: UIWindow?
    private(set) var observedView: UIView?
    private(set) var detachedView: UIView?
    private(set) var becameActive = false

    // Faits capturés par l'application Kadre au `run(scope:)` (après le passage à Running).
    private(set) var applicationRuns = 0
    private(set) var launchSessionIdDescription: String?
    private(set) var scope: (any KadreScope)?
    private(set) var primarySurfaceId: String?
    private(set) var primaryWindowSurfaceId: String?
    private(set) var primarySurfaceIsPrimaryWindowSurface = false
    private(set) var primaryWindowContentWidth: Double?
    private(set) var primaryWindowContentHeight: Double?
    private(set) var primarySurfaceLogicalWidth: Double?
    private(set) var primarySurfaceLogicalHeight: Double?

    // Enregistrée par le test après un attach réussi (valeur de retour de `attach`).
    var session: (any KadreSession)?

    // Compteur proxy des callbacks tardifs : dérives du snapshot de lifecycle figé à la
    // terminaison. La sentinelle `onLateCallback` de l'attach est interne à Kotlin ; le
    // proxy Swift honnête est le gel de l'état de lifecycle après Terminated.
    private(set) var frozenLifecycleDescription: String?
    private(set) var lateCallbackProxyCount = 0

    init(scene: UIWindowScene) {
        self.scene = scene
    }

    var isReady: Bool { window != nil && observedView != nil && becameActive }
    var hasApplicationFacts: Bool {
        applicationRuns > 0 && primarySurfaceId != nil && primaryWindowSurfaceId != nil
    }

    /// Fenêtre + vues du host. `observedView` est DANS la fenêtre ; `detachedView` n'y est
    /// JAMAIS ajoutée (cible du refus `InvalidRequest("surfaceView")`).
    func installWindow() {
        let window = UIWindow(windowScene: scene)
        let frame = CGRect(x: 0, y: 0, width: 320, height: 240)
        let observed = UIView(frame: frame)
        let detached = UIView(frame: frame)
        window.addSubview(observed)
        window.isHidden = false
        window.makeKeyAndVisible()
        self.window = window
        self.observedView = observed
        self.detachedView = detached
    }

    func markActive() { becameActive = true }

    /// Capture des faits de scope par l'application Kadre (appelée au `run`, sur le main).
    /// L'identité `primarySurface === primary.windowSurface` passe par `===` Swift — objets
    /// originaires de Kotlin, identité stable (sanctionné phase 0 / Task 4).
    func capture(scope: any KadreScope, launchSessionId: String) {
        applicationRuns += 1
        launchSessionIdDescription = launchSessionId
        self.scope = scope

        let primarySurface = scope.primarySurface.value as? (any HostSurface)
        let managerState = scope.windows.state.value as? WindowManagerState
        let primaryWindow = managerState?.primary
        let windowSurface = primaryWindow?.surface
        primarySurfaceId = primarySurface?.id.description
        primaryWindowSurfaceId = windowSurface?.id.description
        switch (primarySurface, windowSurface) {
        case (let surface?, let windowSurface?):
            primarySurfaceIsPrimaryWindowSurface = (surface as AnyObject) === (windowSurface as AnyObject)
        default:
            primarySurfaceIsPrimaryWindowSurface = false
        }
        if let windowState = primaryWindow?.state.value as? WindowState {
            primaryWindowContentWidth = windowState.contentSize.width
            primaryWindowContentHeight = windowState.contentSize.height
        }
        if let surfaceState = primarySurface?.state.value as? SurfaceState {
            primarySurfaceLogicalWidth = surfaceState.logicalSize.width
            primarySurfaceLogicalHeight = surfaceState.logicalSize.height
        }
    }

    /// Lecture directe du lifecycle Kadre de la session de cette scène.
    func lifecycleSnapshot() -> (visibility: VisibilityState, activation: ActivationState)? {
        guard let scope, let state = scope.lifecycle.state.value as? LifecycleState else { return nil }
        return (state.visibility, state.activation)
    }

    func lifecycleDescription() -> String? {
        guard let snapshot = lifecycleSnapshot() else { return nil }
        return "\(snapshot.visibility)|\(snapshot.activation)"
    }

    /// Gel du lifecycle à la terminaison, référence du proxy de callbacks tardifs.
    func freezeLifecycleAtTermination() {
        frozenLifecycleDescription = lifecycleDescription()
    }

    /// Une sonde post-terminaison : toute dérive du lifecycle gelé est un callback tardif
    /// qui aurait dû être ignoré.
    func probeLifecycleAfterTermination() {
        guard let frozen = frozenLifecycleDescription else { return }
        if lifecycleDescription() != frozen { lateCallbackProxyCount += 1 }
    }

    // MARK: Attentes bornées (pumping du RunLoop — jamais de sommeil nu)

    private func pump(until deadline: Date, while condition: () -> Bool) -> Bool {
        while condition() {
            if Date() > deadline { return false }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
        return true
    }

    private static let errorDomain = "KadreUikitDriver"

    private func timeoutError(_ what: String, timeout: TimeInterval) -> NSError {
        NSError(domain: Self.errorDomain, code: 1, userInfo: [
            "what": "\(what) not reached in \(timeout)s",
            "state": stateSummary(),
        ])
    }

    /// Scène prête (fenêtre + vue + activation). Échec borné avec l'état courant si non prêt.
    func waitUntilReady(timeout: TimeInterval = 10) throws {
        let deadline = Date().addingTimeInterval(timeout)
        if !pump(until: deadline) { !self.isReady } {
            throw timeoutError("scene readiness", timeout: timeout)
        }
    }

    /// Faits applicatifs capturés (l'application Kadre a tourné au moins une fois).
    func waitUntilApplicationFacts(timeout: TimeInterval = 10) throws {
        let deadline = Date().addingTimeInterval(timeout)
        if !pump(until: deadline) { !self.hasApplicationFacts } {
            throw timeoutError("application facts", timeout: timeout)
        }
    }

    /// Session dans un état donné (prédicat sur la valeur du StateFlow exporté).
    func waitUntilSessionState(
        timeout: TimeInterval = 15,
        _ predicate: @escaping (any SessionState) -> Bool,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        let reached = pump(until: deadline) {
            guard let value = self.session?.state.value as? (any SessionState) else { return true }
            return !predicate(value)
        }
        if !reached {
            throw timeoutError("session state", timeout: timeout)
        }
    }

    func stateSummary() -> String {
        let sessionState = session?.state.value.map(String.init(describing:)) ?? "no-session"
        return "ready=\(isReady) runs=\(applicationRuns) session=\(sessionState) "
            + "primary=\(primarySurfaceId ?? "nil") window=\(primaryWindowSurfaceId ?? "nil")"
    }
}

/// Store des fixtures, indexé par l'identité de la scène connectée. Accès exclusif au
/// thread principal (callbacks de scène + tests unitaires main-thread).
final class DriverFixture {
    static let shared = DriverFixture()

    private var byScene: [ObjectIdentifier: SceneFixture] = [:]
    private(set) var connected: [SceneFixture] = []

    private init() {}

    /// Ordre de connexion — la première scène est la scène primaire de l'app.
    var primary: SceneFixture? { connected.first }

    func fixture(for scene: UIScene) -> SceneFixture? {
        byScene[ObjectIdentifier(scene)]
    }

    /// Fixture d'une scène connectée APRÈS la scène donnée — la seconde scène demandée
    /// par les tests multi-scènes (iOS uniquement).
    func fixture(connectedAfter scene: UIScene) -> SceneFixture? {
        guard let index = connected.firstIndex(where: { $0.scene.isEqual(scene) }) else { return nil }
        return index + 1 < connected.count ? connected[index + 1] : nil
    }

    /// Installation au `willConnectTo` — une nouvelle identité de scène crée un nouveau fixture.
    @discardableResult
    func install(windowScene: UIWindowScene) -> SceneFixture {
        let fixture = SceneFixture(scene: windowScene)
        fixture.installWindow()
        byScene[ObjectIdentifier(windowScene)] = fixture
        connected.append(fixture)
        return fixture
    }
}
