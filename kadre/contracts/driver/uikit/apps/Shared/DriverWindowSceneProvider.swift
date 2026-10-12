import UIKit
import KadreUikit

/// Le provider de scènes du driver (phase 4) — l'app-side de `UIKitWindowSceneProvider`.
///
/// Mécanique (miroir `DriverSceneRequest`, règle contrôleur phase 1) :
///  1. `open(requestId:spec:resolver:)` — appelé par le manager sur le main à chaque demande
///     admise — ENREGISTRE la demande (le resolver émis par le manager voyage avec l'identité,
///     voie unique Task 3) et déclenche UNE activation À LA FOIS (`requestSceneSessionActivation`,
///     gate `awaitingConnection` — la suivante part à la connexion de la précédente, jamais deux
///     activations concurrently). Une demande peut être RETENUE (`holdActivations`, gate de
///     test) sans activation : la demande reste pendante côté manager.
///  2. À la connexion d'une scène (`DriverSceneDelegate.willConnectTo` → `sceneConnected`),
///     la demande en attente d'activation est corrélée À CETTE SCÈNE (FIFO : les activations
///     partent dans l'ordre des `open`, donc les connexions arrivent dans le même ordre —
///     le registre `correlation` est la preuve d'identité lisible par les tests).
///  3. La résolution attend que la scène QUITTE `Unattached` (`session != nil` ET
///     `activationState != .unattached` — les scènes demandées programmatiquement restent
///     ForegroundInactive, jamais `didBecomeActive`, preuve phase 1) : la validation d'attach
///     refuse `Unattached` (`KadreIos.attachScene`, phase 1). La fenêtre et la vue sont CELLES
///     du fixture de la scène (créées par l'app à `willConnectTo` — `DriverFixture.install`),
///     puis `resolver.resolve(windowScene:window:surfaceView:)` — le manager attache la session
///     enfant. Une résolution tardive (demande déjà terminale — cancel-before-connect) est un
///     no-op CÔTÉ MANAGER (CAS Task 1) : le provider ne connaît pas l'état de la demande, il
///     résout — c'est exactement le chemin de résolution tardive qu'on veut exercer.
///
/// TOUT est tracé avec l'ordre d'open (les descriptions de `WindowRequestId` sont rédigées par
/// Kotlin avec rédaction — « WindowRequestId(<redacted>) » — l'identité passe par `isEqual`,
/// jamais par les chaînes). tvOS : compilé mais INERTE — aucun provider n'est attaché à une
/// session tvOS (topologie mono-scène), la file reste vide, aucune activation n'en part.
final class DriverWindowSceneProvider: NSObject, UIKitWindowSceneProvider {
    static let shared = DriverWindowSceneProvider()

    /// Une demande admise par le manager, avant sa connexion corrélée.
    private struct PendingRequest {
        let order: Int
        let requestId: WindowRequestId
        let spec: WindowSpec
        let resolver: UIKitWindowSceneRequestResolver
    }

    /// Une demande corrélée à sa scène connectée — la résolution attend la sortie d'Unattached.
    private struct CorrelatedRequest {
        let pending: PendingRequest
        let scene: UIWindowScene
        let connectedAt: Date
    }

    /// La preuve d'identité d'une résolution EFFECTUÉE (lisible par les tests) : quel
    /// requestId a été résolu sur quelle scène, avec quelles fenêtre/vue.
    struct ResolvedCall {
        let order: Int
        let requestId: WindowRequestId
        let scene: UIWindowScene
        let window: UIWindow
        let surfaceView: UIView
    }

    /// État de corrélation lisible par les tests : le registre requestId → scène (identité
    /// `isEqual`), posé À LA CONNEXION — avant la sortie d'Unattached.
    private(set) var correlation: [(requestId: WindowRequestId, scene: UIWindowScene)] = []
    /// Les résolutions effectuées, dans l'ordre (identité de la demande ET des objets host).
    private(set) var resolvedCalls: [ResolvedCall] = []
    /// Compteur d'appels `open` (le « compteur du provider » du scénario tvOS/no-provider).
    private(set) var openCallCount = 0
    /// Ordre du prochain `open` (les ids de demande sont rédigés — ce label est le token de trace).
    private(set) var openOrder = 0
    /// Les refus d'activation par FrontBoard (diagnostics des échecs CI, jamais silencieux).
    private(set) var activationErrors: [String] = []
    /// Les résolutions expirées (borne de sûreté interne ; les tests ont leurs propres bornes).
    private(set) var resolutionTimeouts: [String] = []

    /// Gate de test (cancel-before-connect) : toute activation est RETENUE tant qu'elle est
    /// levée — les demandes s'accumulent pendantes, aucune scène ne part. La RELÂCHE re-pompe
    /// (la demande retenue part alors RÉELLEMENT — c'est le scénario de la résolution tardive).
    var holdActivations = false {
        didSet {
            if !holdActivations {
                pumpNextActivation()
            }
        }
    }

    // État confiné au thread principal (open du manager, callbacks de scène, Timer).
    private var queued: [PendingRequest] = []
    private var awaitingConnection: PendingRequest?
    private var correlated: [CorrelatedRequest] = []
    private var pump: Timer?

    /// Borne de sûreté interne d'une résolution : au-delà, la demande est abandonnée
    /// (`Cancelled` côté manager) et l'échec est enregistré — jamais d'attente infinie.
    private static let resolutionTimeout: TimeInterval = 30

    private override init() {
        super.init()
    }

    // MARK: UIKitWindowSceneProvider — appelé par le manager, sur le main

    func open(requestId: WindowRequestId, spec: WindowSpec, resolver: UIKitWindowSceneRequestResolver) {
        assert(Thread.isMainThread, "provider.open must run on the main thread")
        openOrder += 1
        openCallCount += 1
        DriverTrace.record("provider open #\(openOrder) title='\(spec.title)' — enqueued (hold=\(holdActivations))")
        queued.append(PendingRequest(order: openOrder, requestId: requestId, spec: spec, resolver: resolver))
        pumpNextActivation()
    }

    // MARK: Connexion corrélée — appelé par DriverSceneDelegate (willConnectTo), sur le main

    /// Une scène vient de se connecter (APRÈS `DriverFixture.shared.install`, le fixture et sa
    /// fenêtre existent). Si une activation du provider est en vol, CETTE scène lui est
    /// corrélée (FIFO). Sinon : connexion hors provider (primaire du lancement ou scènes
    /// secondaires demandées par `DriverSceneRequest` à l'env) — tracée, jamais corrélée.
    func sceneConnected(_ scene: UIWindowScene) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { self.sceneConnected(scene) }
            return
        }
        guard let pending = awaitingConnection else {
            DriverTrace.record("provider sceneConnected : NON corrélée (aucune activation provider en vol) role=\(scene.session.role.rawValue)")
            return
        }
        awaitingConnection = nil
        correlated.append(CorrelatedRequest(pending: pending, scene: scene, connectedAt: Date()))
        correlation.append((pending.requestId, scene))
        DriverTrace.record("provider correlation open #\(pending.order) → scene=\(scene.session.persistentIdentifier.prefix(8)) state=\(scene.activationState.rawValue)")
        startPump()
        pumpNextActivation()
    }

    // MARK: Attente bornée de la sortie d'Unattached — Timer du run loop principal (jamais de sommeil)

    private func startPump() {
        guard pump == nil else { return }
        let timer = Timer(timeInterval: 0.05, repeats: true) { [weak self] _ in self?.pumpResolutions() }
        RunLoop.main.add(timer, forMode: .common)
        pump = timer
    }

    private func stopPump() {
        pump?.invalidate()
        pump = nil
    }

    private func pumpResolutions() {
        guard Thread.isMainThread else { return }
        if correlated.isEmpty {
            if awaitingConnection == nil && queued.isEmpty { stopPump() }
            return
        }
        let front = correlated[0]
        if Date().timeIntervalSince(front.connectedAt) > Self.resolutionTimeout {
            resolutionTimeouts.append("open #\(front.pending.order)")
            DriverTrace.record("provider resolution TIMEOUT open #\(front.pending.order) state=\(front.scene.activationState.rawValue) — abandon")
            correlated.removeFirst()
            front.pending.resolver.abandon(reason: "scene-never-left-unattached")
            return
        }
        let scene = front.scene
        // La validation d'attach enfant refuse `Unattached` — la résolution n'a lieu QU'À la
        // sortie d'Unattached (ForegroundInactive suffit, preuve phase 1 : les scènes demandées
        // ne deviennent jamais ForegroundActive ; une scène connectée porte toujours SA session).
        guard scene.activationState != .unattached,
              let fixture = DriverFixture.shared.fixture(for: scene),
              let window = fixture.window,
              let view = fixture.observedView else {
            return // tick suivant — attente bornée par le deadline ci-dessus
        }
        DriverTrace.record("provider resolve open #\(front.pending.order) sceneState=\(scene.activationState.rawValue)")
        correlated.removeFirst()
        resolvedCalls.append(ResolvedCall(order: front.pending.order, requestId: front.pending.requestId, scene: scene, window: window, surfaceView: view))
        front.pending.resolver.resolve(windowScene: scene, window: window, surfaceView: view)
    }

    // MARK: Activations sérialisées (une seule en vol — miroir DriverSceneRequest)

    private func pumpNextActivation() {
        guard !holdActivations, awaitingConnection == nil, !queued.isEmpty else { return }
        guard !DriverSceneRequest.isBusy else {
            DriverTrace.record("provider activation différée : activation de lancement en vol")
            return
        }
        let pending = queued.removeFirst()
        awaitingConnection = pending
        DriverTrace.record("provider requestSceneSessionActivation open #\(pending.order)")
        UIApplication.shared.requestSceneSessionActivation(nil, userActivity: nil, options: nil) { [weak self] error in
            guard let self, Thread.isMainThread else { return }
            DriverTrace.record("provider activation ERREUR open #\(pending.order) : \(String(describing: error))")
            self.activationErrors.append("open #\(pending.order): \(String(describing: error))")
            if self.awaitingConnection?.requestId.isEqual(pending.requestId) == true {
                self.awaitingConnection = nil
                pending.resolver.abandon(reason: "activation-refused")
            }
            self.pumpNextActivation()
        }
    }

    // MARK: Isolement inter-tests

    /// Remise à zéro ENTRE tests (tearDown) : les tests sont QUIESCANTS en sortie (plus
    /// d'activation ni de corrélation en vol — chaque scénario attend ses outcomes), le reset
    /// ne peut pas orpheliner une mécanique vivante. Les compteurs de PREUVE repartent de zéro.
    func reset() {
        holdActivations = false
        queued.removeAll()
        awaitingConnection = nil
        correlated.removeAll()
        resolvedCalls.removeAll()
        correlation.removeAll()
        openCallCount = 0
        openOrder = 0
        activationErrors.removeAll()
        resolutionTimeouts.removeAll()
        stopPump()
    }
}
