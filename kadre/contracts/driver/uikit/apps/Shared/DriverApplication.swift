import UIKit
import KadreUikit

/// L'application du host : une fabrique Kadre par attach, liée au fixture de sa scène.
///
/// Forme RÉELLE du protocole exporté (header Task 4, `KadreApplicationFactory`) :
/// `create(context: KadreLaunchContext) -> any KadreApplication` — PAS
/// `create(sessionId:rootScope:)` ; l'identité de session vit dans le contexte de lancement
/// et le scope n'arrive qu'au `run`.
///
/// Phase 4 : la fabrique du REQUÉRANT est réutilisée par l'adaptateur pour attacher la session
/// ENFANT d'une demande de fenêtre (KadreLaunchReason.AdditionalHostRequested) — `create` est
/// donc rappelé sur la MÊME instance. La PREMIÈRE session créée est celle que le test a
/// attachée (le requérant) : ses faits alimentent le fixture (comportement phases 1-5). Les
/// exécutions SUIVANTES sont des sessions enfants : elles n'écrivent JAMAIS le fixture du
/// requérant (aucune contamination des faits primaires) — elles ouvrent un
/// `DriverSessionRecord` (canal des faits enfants, recherché par identité de session).
final class DriverApplication: NSObject, KadreApplicationFactory {
    private let fixture: SceneFixture
    /// La première session créée par cette instance est liée au fixture (le requérant du test).
    private var fixtureCaptureArmed = true

    init(fixture: SceneFixture) {
        self.fixture = fixture
    }

    func create(context: KadreLaunchContext) -> any KadreApplication {
        let bindToFacts = fixtureCaptureArmed
        fixtureCaptureArmed = false
        return DriverKadreApplication(fixture: fixture, launchSessionId: context.sessionId, fixtureBound: bindToFacts)
    }
}

/// L'application Kadre du driver. Forme RÉELLE du protocole `KadreApplication` (header) :
/// `run(_ receiver: any KadreScope, completionHandler: (any Error)?) -> Void` — une fonction
/// suspend exportée : le handler de complétion doit être appelé exactement une fois.
final class DriverKadreApplication: NSObject, KadreApplication {
    private let fixture: SceneFixture
    private let launchSessionIdDescription: String
    private let fixtureBound: Bool
    private var record: DriverSessionRecord?
    private var pendingCompletion: (((any Error)?) -> Void)?
    private var cooperationTimer: Timer?

    init(fixture: SceneFixture, launchSessionId: SessionId, fixtureBound: Bool) {
        self.fixture = fixture
        self.launchSessionIdDescription = launchSessionId.description
        self.fixtureBound = fixtureBound
    }

    /// Appelé UNE fois, sur le dispatcher main, après le passage de la session à Running.
    /// Ouvre l'enregistrement de session (identité + faits de scope), capture AUSSI les faits
    /// du fixture pour la session requérante, puis REVIENT sans compléter : la coroutine
    /// applicative reste suspendue sur l'appel étranger et la session reste Running jusqu'à
    /// l'arrêt du host. La complétion coopérative (`cooperate`) est le seul chemin de sortie :
    /// le pont K/N d'une fonction suspend IMPLÉMENTÉE en Swift n'a pas de chemin d'annulation
    /// (rien ne peut appeler le handler détenu par Swift), donc sans coopération le runtime
    /// attendrait `shutdownTimeout` (5 s) et publierait `Failed(ShutdownTimedOut)`.
    func run(_ receiver: any KadreScope, completionHandler: @escaping ((any Error)?) -> Void) {
        let record = DriverFixture.shared.beginSessionRecord(sessionId: receiver.sessionId, fixtureBound: fixtureBound)
        record.capture(scope: receiver)
        self.record = record
        if fixtureBound {
            // La session REQUÉRANTE : faits de fixture (le canal historique des phases 1-5,
            // lu par `waitUntilApplicationFacts`). Les enfants ne passent JAMAIS ici.
            fixture.capture(scope: receiver, launchSessionId: launchSessionIdDescription)
        }
        pendingCompletion = completionHandler
        let timer = Timer(timeInterval: 0.02, repeats: true) { [weak self] _ in self?.cooperate() }
        RunLoop.main.add(timer, forMode: .common)
        cooperationTimer = timer
    }

    /// Application bien élevée : elle termine sa boucle quand SA session s'arrête.
    ///
    /// Deux signaux, JAMAIS croisés entre sessions :
    ///  * session requérante (`fixtureBound`) — l'état du handle enregistré par le test
    ///    (`fixture.session`), mécanique phases 1-5 ;
    ///  * session enfant — le détachement du host de SA PROPRE session : la scène enfant qui
    ///    se déconnecte publie `AttachmentState.detached` dans le lifecycle de SON scope
    ///    (relais de SA scène, phase 1) avant de terminer la session. Le fixture.session du
    ///    requérant est VOLONTAIREMENT ignoré ici — l'enfant est une session INDÉPENDANTE
    ///    (roadmap l.240) : l'arrêt du requérant ne doit ni le toucher, ni le faire coopérer.
    private func cooperate() {
        guard let completion = pendingCompletion, let record else { return }
        let fixtureSessionStopping: Bool = {
            guard fixtureBound, let session = fixture.session, let state = session.state.value as? (any SessionState) else { return false }
            return state is SessionStateStopping || state is SessionStateTerminated
        }()
        let ownHostDetached = record.lifecycleAttachment() === AttachmentState.detached
        guard fixtureSessionStopping || ownHostDetached else { return }
        record.markCompleted()
        pendingCompletion = nil
        cooperationTimer?.invalidate()
        cooperationTimer = nil
        completion(nil)
    }
}
