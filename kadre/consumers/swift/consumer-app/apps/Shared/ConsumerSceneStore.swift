import UIKit
import KadreUikit

/// Le store de la scène consumer : TOUT ce que les XCTest lisent, exposé en faits Swift
/// primitifs (chaînes, AnyObject). Les frameworks Kotlin STATIQUES sont liés exactement
/// une fois — dans l'app hôte ; un bundle de test qui lierait ses propres copies
/// dupliquerait le runtime Kotlin dans le process de test (classes Obj-C en double).
/// Les lectures typées (`session.state.value`, `awaitTermination`, outcome) vivent donc
/// ICI, dans la cible app ; les tests ne voient que les descriptions enregistrées.
///
/// `import KadreUikit` résout le module du fragment consumer (premier répertoire des
/// FRAMEWORK_SEARCH_PATHS) : il exporte l'API uikit (`KadreIos`, `KadreSession`…) ET
/// porte la factory du module applicatif (`ConsumerApplicationFactory`) — le préfixe
/// Obj-C `KadreUikit` est identique des deux fragments (distribution + consumer), voir
/// consumer-kmp/build.gradle.kts.
final class ConsumerSceneStore: NSObject {
    static let shared = ConsumerSceneStore()

    private(set) var window: UIWindow?
    private(set) var surfaceView: UIView?

    /// La session capturée à l'attach (type exact derrière le lien statique de l'app).
    private(set) var sessionObject: AnyObject?

    /// La raison d'échec d'attach exportée, le cas échéant (l'attach ne doit pas échouer).
    private(set) var attachFailureDescription: String?

    /// Historique des états de session OBSERVÉS (poll par timer sur le main, démarré à
    /// l'attach) : `Starting` → `Running` → `Stopping` → `Terminated`. Le timer tourne
    /// indépendamment des tests — un lancement de test tardif ne peut pas rater le
    /// passage à `Running` (l'app consumer s'arrête elle-même après ~0,5 s).
    private(set) var observedStateDescriptions: [String] = []
    private(set) var lastStateDescription: String = "none"

    /// L'outcome de terminaison capturé par `awaitTermination` côté app :
    /// « Stopped » + raison « ApplicationRequested » attendus (l'app consumer s'arrête
    /// elle-même — PAS le host, PAS un timeout).
    private(set) var outcomeDescription: String?
    private(set) var stopReasonDescription: String?
    private(set) var terminationErrorDescription: String?

    /// La classe de la factory passée à l'attach, telle que vue par le runtime Obj-C :
    /// elle doit porter le nom Kotlin exporté avec le préfixe `KadreUikit` — la factory
    /// du module applicatif Kotlin traverse Swift jusqu'à l'attach avec l'identité de
    /// types du module distribution (même préfixe, même lien).
    private(set) var factoryClassName: String?

    private var statePollTimer: Timer?
    private var attachedScene: UIWindowScene?

    /// Idempotent : appelé au premier `sceneDidBecomeActive`, ignore les suivants.
    func attachPrimarySceneOnce() {
        guard let windowScene = attachedScene else { return }
        guard sessionObject == nil && attachFailureDescription == nil else { return }
        attachPrimaryScene(windowScene)
    }

    func install(windowScene: UIWindowScene) {
        attachedScene = windowScene
        let window = UIWindow(windowScene: windowScene)
        let surfaceView = UIView(frame: window.bounds)
        window.addSubview(surfaceView)
        window.isHidden = false
        window.makeKeyAndVisible()
        self.window = window
        self.surfaceView = surfaceView
    }

    /// L'attach lui-même : la factory du module applicatif Kotlin (lié statiquement
    /// dans cette app) passe en Swift jusqu'à `KadreIos.shared.attach` — le compilateur
    /// Swift n'accepte le passage que si les deux fragments exposent le MÊME type
    /// Obj-C `KadreApplicationFactory` ; l'exécution ne poursuit que si l'identité
    /// survit au lien statique deux-frameworks. Puis l'attach au sens strict — le
    /// résultat exporté (`KadreResult<KadreSession>`) est déballé ici, jamais dans les tests.
    func attachPrimaryScene(_ windowScene: UIWindowScene) {
        guard let window, let surfaceView else {
            attachFailureDescription = "window or surfaceView missing at attach"
            return
        }
        let factory = ConsumerApplicationFactory()
        factoryClassName = NSStringFromClass(type(of: factory))
        let result = KadreIos.shared.attach(
            windowScene: windowScene,
            window: window,
            surfaceView: surfaceView,
            applicationFactory: factory,
            policy: KadrePolicies.shared.Default,
            // Le framework est STATIQUE : aucune convenience Swift des arguments par défaut
            // Kotlin — l'argument optionnel du provider de scènes (phase 4) doit être passé.
            // `nil` = mono-fenêtre ; le driver multi-fenêtre le remplira (Task 3).
            windowSceneProvider: nil,
        )
        if let failure = (result as? KadreResultFailure)?.reason {
            attachFailureDescription = "attach failed: \(failure)"
            return
        }
        guard let success = result as? KadreResultSuccess<AnyObject>,
              let session = success.value as? (any KadreSession) else {
            attachFailureDescription = "attach returned neither failure nor session"
            return
        }
        sessionObject = session
        startStatePolling(session: session)
        // L'outcome de terminaison est capturé côté app (l'arrêt vient de l'application
        // consumer elle-même : `Stopped(ApplicationRequested)` attendu).
        session.awaitTermination { [weak self] outcome, error in
            guard let self else { return }
            self.terminationErrorDescription = error.map { "\($0)" }
            guard let outcome else {
                self.outcomeDescription = "no-outcome"
                return
            }
            if let stopped = outcome as? SessionOutcomeStopped {
                self.outcomeDescription = "Stopped"
                if stopped.reason === SessionStopReason.applicationrequested {
                    self.stopReasonDescription = "ApplicationRequested"
                } else {
                    self.stopReasonDescription = "\(stopped.reason)"
                }
            } else {
                self.outcomeDescription = "\(outcome)"
            }
        }
    }

    /// Poll du `StateFlow` exporté de la session — sur le main, par timer RunLoop (le
    /// même patron que le timer coopératif du driver : jamais de sommeil nu, jamais de
    /// lecture hors main).
    private func startStatePolling(session: any KadreSession) {
        let timer = Timer(timeInterval: 0.02, repeats: true) { [weak self] _ in
            guard let self else { return }
            guard let value = session.state.value as? (any SessionState) else { return }
            let name = Self.stateName(value)
            self.lastStateDescription = name
            if self.observedStateDescriptions.last != name {
                self.observedStateDescriptions.append(name)
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        statePollTimer = timer
    }

    private static func stateName(_ state: any SessionState) -> String {
        if state is SessionStateStarting { return "Starting" }
        if state is SessionStateRunning { return "Running" }
        if state is SessionStateStopping { return "Stopping" }
        if state is SessionStateTerminated { return "Terminated" }
        return "Unknown"
    }

    // MARK: - Attentes bornées (pumping du RunLoop — jamais de sommeil nu)

    /// Attente bornée d'un état de session par son nom enregistré. Échec avec l'historique.
    func waitUntilState(_ name: String, timeout: TimeInterval = 15) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while lastStateDescription != name {
            if Date() > deadline {
                throw NSError(domain: "KadreConsumer", code: 1, userInfo: [
                    "what": "session state \(name) not reached in \(timeout)s",
                    "last": lastStateDescription,
                    "history": observedStateDescriptions.joined(separator: " → "),
                ])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }
}
