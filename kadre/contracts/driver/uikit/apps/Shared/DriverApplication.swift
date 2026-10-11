import UIKit
import KadreUikit

/// L'application du host : une fabrique Kadre par attach, liée au fixture de sa scène.
///
/// Forme RÉELLE du protocole exporté (header Task 4, `KadreApplicationFactory`) :
/// `create(context: KadreLaunchContext) -> any KadreApplication` — PAS
/// `create(sessionId:rootScope:)` ; l'identité de session vit dans le contexte de lancement
/// et le scope n'arrive qu'au `run`.
final class DriverApplication: NSObject, KadreApplicationFactory {
    private let fixture: SceneFixture

    init(fixture: SceneFixture) {
        self.fixture = fixture
    }

    func create(context: KadreLaunchContext) -> any KadreApplication {
        DriverKadreApplication(fixture: fixture, launchSessionId: context.sessionId)
    }
}

/// L'application Kadre du driver. Forme RÉELLE du protocole `KadreApplication` (header) :
/// `run(_ receiver: any KadreScope, completionHandler: (any Error)?) -> Void` — une fonction
/// suspend exportée : le handler de complétion doit être appelé exactement une fois.
final class DriverKadreApplication: NSObject, KadreApplication {
    private let fixture: SceneFixture
    private let launchSessionIdDescription: String
    private var pendingCompletion: (((any Error)?) -> Void)?
    private var cooperationTimer: Timer?

    init(fixture: SceneFixture, launchSessionId: SessionId) {
        self.fixture = fixture
        self.launchSessionIdDescription = launchSessionId.description
    }

    /// Appelé UNE fois, sur le dispatcher main, après le passage de la session à Running.
    /// Capture les faits de scope pour les tests puis REVIENT sans compléter : la coroutine
    /// applicative reste suspendue sur l'appel étranger et la session reste Running jusqu'à
    /// l'arrêt du host. La complétion coopérative (`cooperate`) est le seul chemin de sortie :
    /// le pont K/N d'une fonction suspend IMPLÉMENTÉE en Swift n'a pas de chemin d'annulation
    /// (rien ne peut appeler le handler détenu par Swift), donc sans coopération le runtime
    /// attendrait `shutdownTimeout` (5 s) et publierait `Failed(ShutdownTimedOut)`.
    func run(_ receiver: any KadreScope, completionHandler: @escaping ((any Error)?) -> Void) {
        fixture.capture(scope: receiver, launchSessionId: launchSessionIdDescription)
        pendingCompletion = completionHandler
        let timer = Timer(timeInterval: 0.02, repeats: true) { [weak self] _ in self?.cooperate() }
        RunLoop.main.add(timer, forMode: .common)
        cooperationTimer = timer
    }

    /// Application bien élevée : elle termine sa boucle quand le host arrête la session
    /// (état lu sur le `StateFlow` exporté de la session, enregistrée par le test).
    private func cooperate() {
        guard let completion = pendingCompletion, let session = fixture.session else { return }
        guard let state = session.state.value as? (any SessionState),
              state is SessionStateStopping || state is SessionStateTerminated else { return }
        pendingCompletion = nil
        cooperationTimer?.invalidate()
        cooperationTimer = nil
        completion(nil)
    }
}
