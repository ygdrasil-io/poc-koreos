import UIKit
import KadreUikit

/// Journal de bord du driver : chaque fait de la chaîne scène (callbacks du delegate,
/// lifecycle des demandes de scène, lecture d'env de lancement) est ENREGISTRÉ (store
/// mémoire + NSLog visible dans la sortie console xcodebuild, donc dans le log CI). Un
/// échec CI doit MONTRER où la chaîne s'est arrêtée : le dump d'échec borné embarque la
/// trace récente et un snapshot système au moment du timeout.
enum DriverTrace {
    private static let lock = NSLock()
    private static let start = Date()
    private static var events: [String] = []
    private static var envRecorded = false

    static func record(_ line: String) {
        let stamped = String(format: "[+%05.2fs] ", Date().timeIntervalSince(start)) + line
        lock.lock()
        events.append(stamped)
        if events.count > 200 { events.removeFirst(events.count - 200) }
        lock.unlock()
        NSLog("KADRE-DRIVER %@", stamped)
    }

    /// La lecture de l'env de demande de scène est tracée UNE fois (les relectures muettes).
    static func recordSceneRequestEnvOnce(_ raw: String?) {
        guard !envRecorded else { return }
        envRecorded = true
        record("sceneRequest env KADRE_DRIVER_REQUEST_SECOND_SCENE=\(raw.map { "'\($0)'" } ?? "<unset>")")
    }

    /// Derniers événements horodatés — embarqués dans tout échec borné.
    static func recent(_ n: Int = 20) -> String {
        lock.lock()
        defer { lock.unlock() }
        return events.suffix(n).joined(separator: " || ")
    }

    /// Snapshot système au moment du dump : état de l'app hôte et TOUTES les scènes
    /// connues d'UIKit avec leur état d'activation — distingue « scène connectée jamais
    /// activée », « scène jamais connectée » et « app restée en background ».
    static func systemSnapshot() -> String {
        guard Thread.isMainThread else { return "system=<off-main>" }
        let app = UIApplication.shared
        let scenes = app.connectedScenes
            .sorted { $0.session.persistentIdentifier < $1.session.persistentIdentifier }
            .map { scene -> String in
                "\(String(describing: type(of: scene))) state=\(scene.activationState) "
                    + "role=\(scene.session.role.rawValue) session=\(scene.session.persistentIdentifier.prefix(8))"
            }
            .joined(separator: "; ")
        return "appState=\(app.applicationState) connectedScenes=\(app.connectedScenes.count) [\(scenes)]"
    }
}

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
    // NB : `SurfaceId.description` est VOLONTAIREMENT rédigié par Kotlin (« SurfaceId(<redacted>) »)
    // — l'égalité de CHAÎNES entre deux ids est donc VIDE DE SENS. Les preuves d'identité
    // passent par `isEqual` (même id) et `===` (même instance), pas par les descriptions.
    private(set) var primarySurfaceId: String?
    private(set) var primaryWindowSurfaceId: String?
    private(set) var primarySurfaceIdMatchesWindowSurfaceId = false
    private(set) var primarySurfaceIsPrimaryWindowSurface = false
    /// Référence objet de la surface primaire capturée (comparaisons d'identité entre fixtures).
    private(set) var primarySurfaceRef: AnyObject?
    private(set) var primaryWindowContentWidth: Double?
    private(set) var primaryWindowContentHeight: Double?
    private(set) var primarySurfaceLogicalWidth: Double?
    private(set) var primarySurfaceLogicalHeight: Double?
    /// La surface primaire TYPÉE de la session courante (Task 5) : les preuves de surface
    /// (state, events, requestRedraw, apply, bail via la sonde Kotlin) la lisent ICI. Recapturée
    /// à chaque `run` — toujours celle de la session vivante du fixture, jamais un reliquat.
    private(set) var primarySurface: (any HostSurface)?

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
    /// `makeKeyAndVisible` est réservé à la fenêtre de la scène PRIMAIRE (l'app) : une scène
    /// secondaire qui prend le key à la création reste « au premier plan » aux yeux de
    /// FrontBoard, qui diffère (ici : ignore) sa destruction.
    func installWindow(makeKey: Bool = true) {
        let window = UIWindow(windowScene: scene)
        let frame = CGRect(x: 0, y: 0, width: 320, height: 240)
        let observed = UIView(frame: frame)
        let detached = UIView(frame: frame)
        window.addSubview(observed)
        window.isHidden = false
        if makeKey {
            window.makeKeyAndVisible()
        } else {
            window.isHidden = false
        }
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
            primarySurfaceIdMatchesWindowSurfaceId = surface.id.isEqual(windowSurface.id)
            primarySurfaceIsPrimaryWindowSurface = (surface as AnyObject) === (windowSurface as AnyObject)
            primarySurfaceRef = surface as AnyObject
        default:
            primarySurfaceIdMatchesWindowSurfaceId = false
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
        self.primarySurface = primarySurface
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
            "system": DriverTrace.systemSnapshot(),
            "trace": DriverTrace.recent(),
        ])
    }

    /// Scène prête (fenêtre + vue + activation). Échec borné avec l'état courant si non prêt.
    /// `requireActivation: false` pour les scènes secondaires demandées par l'app : une scène
    /// demandée programmatiquement reste ForegroundInactive (la scène primaire garde
    /// l'activation) — `sceneDidBecomeActive` n'y fire jamais, et l'attach accepte par design
    /// ForegroundInactive (reducer + validation `!= Unattached`).
    func waitUntilReady(requireActivation: Bool = true, timeout: TimeInterval = 10) throws {
        let deadline = Date().addingTimeInterval(timeout)
        if !pump(until: deadline, while: {
            !(self.window != nil && self.observedView != nil
                && (!requireActivation || self.becameActive))
        }) {
            throw timeoutError("scene readiness", timeout: timeout)
        }
    }

    /// Faits applicatifs capturés (l'application Kadre a tourné au moins une fois).
    /// `minimumRuns` rend l'attente SENSIBLE À LA FRAÎCHEUR : les faits S'ACCUMULENT sur un
    /// fixture réutilisé d'un test à l'autre (le process hôte vit pendant toute la suite),
    /// donc sans ce plancher, un attach neuf « passerait » instantanément sur des faits
    /// périmés d'une session précédente.
    func waitUntilApplicationFacts(minimumRuns: Int? = nil, timeout: TimeInterval = 10) throws {
        let deadline = Date().addingTimeInterval(timeout)
        let floor = minimumRuns ?? (applicationRuns + 1)
        if !pump(until: deadline, while: {
            !(self.hasApplicationFacts && self.applicationRuns >= floor)
        }) {
            throw timeoutError("application facts (runs >= \(floor))", timeout: timeout)
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

    /// État de la surface primaire dans un état donné (Task 5) : attente bornée avec pumping,
    /// retourne le PREMIER état satisfaisant (les assertions portent sur cette valeur capturée).
    /// L'échec embarque le dernier état vu — un timeout doit dire CE QUE la surface publiait.
    func waitUntilSurfaceState(
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (SurfaceState) -> Bool,
    ) throws -> SurfaceState {
        let deadline = Date().addingTimeInterval(timeout)
        var last: SurfaceState?
        while true {
            if let surface = primarySurface, let state = surface.state.value as? SurfaceState {
                last = state
                if predicate(state) { return state }
            }
            if Date() > deadline {
                throw NSError(domain: "KadreUikitDriver", code: 3, userInfo: [
                    "what": "surface state not reached in \(timeout)s",
                    "last": String(describing: last ?? nil),
                    "state": stateSummary(),
                    "trace": DriverTrace.recent(),
                ])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    func stateSummary() -> String {
        let sessionState = session?.state.value.map(String.init(describing:)) ?? "no-session"
        // Les composants de `isReady` sont détaillés (win/view/active) : sur un échec CI,
        // `ready=false` doit dire QUEL latch manque — sur un fixture installé, win/view sont
        // posés de façon synchrone, donc le latch manquant est en pratique `active`.
        return "ready=\(isReady) win=\(window != nil) view=\(observedView != nil) active=\(becameActive) "
            + "sceneState=\(String(describing: scene.activationState)) runs=\(applicationRuns) "
            + "session=\(sessionState) primary=\(primarySurfaceId ?? "nil") window=\(primaryWindowSurfaceId ?? "nil")"
    }
}

/// Store des fixtures, indexé par l'identité de la scène connectée. Accès exclusif au
/// thread principal (callbacks de scène + tests unitaires main-thread).
final class DriverFixture {
    static let shared = DriverFixture()

    private var byScene: [ObjectIdentifier: SceneFixture] = [:]
    private(set) var connected: [SceneFixture] = []
    /// Erreurs d'activation de scène enregistrées par `DriverSceneRequest` — les tests les
    /// remontent telles quelles (aucun contournement factice).
    private(set) var sceneActivationErrors: [any Error] = []
    private(set) var sceneDestructionErrors: [any Error] = []

    private init() {}

    func recordSceneActivationError(_ error: any Error) {
        sceneActivationErrors.append(error)
    }

    func recordSceneDestructionError(_ error: any Error) {
        sceneDestructionErrors.append(error)
    }

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
    /// Seule la PREMIÈRE scène (primaire de l'app) prend le key ; les scènes secondaires
    /// demandées par l'app affichent leur fenêtre sans voler le key à la scène primaire.
    @discardableResult
    func install(windowScene: UIWindowScene) -> SceneFixture {
        let fixture = SceneFixture(scene: windowScene)
        fixture.installWindow(makeKey: connected.isEmpty)
        byScene[ObjectIdentifier(windowScene)] = fixture
        connected.append(fixture)
        DriverSceneRequest.sceneConnected()
        return fixture
    }
}
