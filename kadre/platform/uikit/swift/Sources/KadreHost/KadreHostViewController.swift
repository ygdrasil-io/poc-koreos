import UIKit
import KadreUikit

/*
 * `KadreHostViewController` — verbatim §5 (INTEROP l.140) : « KadreHostViewController
 * attache exactement une fois lorsque sa view appartient à une UIWindow et une
 * UIWindowScene ; il utilise cette view comme surfaceView, ne la remplace pas et ne crée
 * aucun layout. Un retrait temporaire ne ferme pas la scène ; deinit, sceneDidDisconnect ou
 * close explicite ferment la session. »
 *
 * LE MOMENT VALIDE D'ATTACH — sémantique « au PREMIER moment valide » :
 *  — le hook est `didMoveToWindow` : la view vient de rejoindre une fenêtre ; le moment
 *    est VALIDE quand cette fenêtre porte une UIWindowScene (les deux conditions du
 *    verbatim §5 — la validation `surfaceView.window == window` de `KadreIos.attach` est
 *    vraie par construction, la view EST dans la fenêtre lue) ;
 *  — la scène peut néanmoins être encore INVALIDE pour l'attach — l'iPadOS 27 laisse la
 *    scène `Unattached` au `willConnectTo` (finding phase 1, miroir du
 *    `ConsumerSceneDelegate` : le premier attach passe à `sceneDidBecomeActive`) — le
 *    premier essai répond alors `InvalidRequest("window")` (le `KadreError` le dit) ;
 *  — CE cas, et lui seul, arme UN réessai à l'activation de la scène
 *    (`UIScene.didActivateNotification`, object = la scène — pattern prouvé du driver
 *    phase 1) : l'attach a donc lieu au PREMIER MOMENT VALIDE, jamais avant, jamais deux
 *    fois. Tout autre refus (`AlreadyInUse(Host)` — la scène porte déjà une session, etc.)
 *    est DÉFINITIF : le controller n'insiste jamais ;
 *  — l'attach est EXACTEMENT UNE FOIS par controller, à vie : après le succès, après un
 *    échec définitif et APRÈS close, aucun re-add à une hiérarchie ne ré-attache ; un
 *    close AVANT tout attach consomme lui aussi ce budget (aucun join postérieur — ni
 *    activation armée — ne pose de session).
 *
 * LE ROUTAGE DES NOTIFICATIONS — le miroir Swift d'`UIKitLifecycleSource` (phase 1) : UN
 * observer par notification, filtré `object = la scène attachée` ET double vérification
 * `isEqual` dans le block (défense en profondeur — `===` ne pont pas l'identité Obj-C,
 * preuve O3 phase 0), livré sur la file principale ; les tokens sont retirés au deinit (les
 * observers PAR BLOCK sont détenus par le centre PAR TOKEN — `removeObserver(self)` ne les
 * retirerait pas, et le block capture self FAIBLE pour ne jamais repousser le deinit).
 *
 * CONTRAT DE THREAD : toute la vie du controller est principale (UIKit — le didMoveToWindow
 * de la vue, les notifications de scène) ; le `precondition` main-thread de
 * `KadreIosHost.attach` est donc satisfait par construction. `close()` est délégable
 * d'ailleurs : le close du handle est thread-safe (`KadreSessionHandle`).
 */
public final class KadreHostViewController: UIViewController {

    /// La factory et la politique du consommateur — capturées à l'init, consommées par le
    /// (au plus deux) appels d'attach.
    private let applicationFactory: any KadreUikit.KadreApplicationFactory
    private let policy: KadrePolicyProfile

    /// La session vivante — posée à l'attach réussi, JAMAIS délevée : un handle clos reste
    /// lisible (son `state` dit terminated — rien n'est dissimulé). Le consommateur la
    /// ferme explicitement (`close()`) ou la laisse fermer par deinit / disconnect.
    public private(set) var kadreSession: KadreSessionHandle?

    /// La machine EXACTEMENT-UNE-FOIS :
    ///  — `waiting` : la view n'a pas encore rencontré le moment valide ;
    ///  — `retryArmed(scene)` : premier essai refusé `InvalidRequest("window")` — la scène
    ///    n'était pas encore valide (Unattached) ; UN réessai armé à son activation ;
    ///  — `settled` : attach réussi OU échec définitif OU réessai consommé OU close sans
    ///    session (le budget est consommé) — plus AUCUNE tentative, à vie (un re-add
    ///    après close ne ré-attache pas).
    private enum AttachState {
        case waiting
        case retryArmed(scene: UIWindowScene)
        case settled
    }

    private var attachState: AttachState = .waiting

    /// La scène attachée — la cible du routage de notification (défense en profondeur).
    private var connectedScene: UIWindowScene?

    /// Les tokens d'observation PAR BLOCK — retirés au deinit par TOKEN.
    private var disconnectObserver: (any NSObjectProtocol)?
    private var activationObserver: (any NSObjectProtocol)?

    // MARK: Diagnostics internes (lisibles des tests via @testable — jamais dans la surface §5)

    /// Le nombre d'appels RÉELS à `KadreIosHost.attach` — 1 attendu sur toute vie correcte.
    private(set) var attachAttempts = 0

    /// La description du dernier échec d'attach — le « pourquoi » d'un échec CI.
    private(set) var lastAttachFailureDescription: String?

    /// L'initialisateur §5 : la factory d'application (Kotlin — l'application est le module
    /// applicatif du consumer) et le profil de politique (`.default` par défaut).
    public init(
        applicationFactory: any KadreUikit.KadreApplicationFactory,
        policy: KadrePolicyProfile = .default
    ) {
        self.applicationFactory = applicationFactory
        self.policy = policy
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) est sans objet — passez par init(applicationFactory:policy:)")
    }

    /// La view NEUTRE du host — AUCUN layout Kadre (§5 : la view du controller EST la
    /// surfaceView, il ne la remplace pas) : le controller ne pose ni contrainte ni subview.
    /// La sous-classe privée n'existe que pour le HOOK : UIViewController n'expose aucun
    /// callback de mouvement en fenêtre (didMoveToWindow est une méthode UIView) — la view
    /// relaie donc le sien. C'est LA view du controller (même instance, identité §5) : la
    /// session la consomme comme surfaceView, aucun layout ni dessin n'y est ajouté.
    override public func loadView() {
        let contentView = KadreHostContentView(frame: CGRect(x: 0, y: 0, width: 320, height: 240))
        contentView.onMovedToWindow = { [weak self] in
            self?.attemptAttachAtValidMoment()
        }
        view = contentView
    }

    // MARK: — Le moment valide

    /// Le moment valide (§5 : « lorsque sa view appartient à une UIWindow et une
    /// UIWindowScene ») : fenêtre posée ET scène de cette fenêtre. Sinon — view hors
    /// hiérarchie, ou fenêtre sans scène — ce n'est PAS le moment : le prochain
    /// `didMoveToWindow` de la view reviendra, rien n'est consommé.
    private func attemptAttachAtValidMoment() {
        guard case .waiting = attachState else { return }
        guard let window = view.window, let scene = window.windowScene else { return }
        performAttach(scene: scene, window: window, isFinalAttempt: false)
    }

    // MARK: — L'attach réel

    /// L'attach RÉEL — la délégation §5 (`KadreIosHost.attach`) avec LA view du controller
    /// comme surfaceView, jamais une autre vue. `isFinalAttempt` marque le réessai armé :
    /// lui seul ne peut plus ré-armer.
    private func performAttach(scene: UIWindowScene, window: UIWindow, isFinalAttempt: Bool) {
        attachAttempts += 1
        do {
            let handle = try KadreIosHost.attach(
                windowScene: scene,
                window: window,
                surfaceView: view,
                applicationFactory: applicationFactory,
                policy: policy)
            kadreSession = handle
            attachState = .settled
            connectedScene = scene
            installDisconnectObserver(for: scene)
        } catch let error as KadreError {
            lastAttachFailureDescription = "\(error.failure)"
            if !isFinalAttempt, case .invalidRequest(let field) = error.failure, field == "window" {
                // La scène n'était pas encore valide (`session == nil` ou `Unattached` —
                // l'iPadOS 27 au willConnectTo, finding phase 1) : ce n'est pas un refus
                // définitif mais le PREMIER moment valide manqué — UN réessai à l'activation.
                attachState = .retryArmed(scene: scene)
                armActivationRetry(for: scene)
            } else {
                // Tout autre refus — ou le réessai consommé — est DÉFINITIF.
                attachState = .settled
            }
        } catch {
            lastAttachFailureDescription = "\(error)"
            attachState = .settled
        }
    }

    // MARK: — Le réessai à l'activation (le premier moment valide manqué)

    private func armActivationRetry(for scene: UIWindowScene) {
        guard activationObserver == nil else { return }
        activationObserver = NotificationCenter.default.addObserver(
            forName: UIScene.didActivateNotification,
            object: scene,
            queue: .main) { [weak self] notification in
            guard let self else { return }
            // ONE-SHOT : le réessai consomme le token à la première livraison.
            if let token = self.activationObserver {
                NotificationCenter.default.removeObserver(token)
                self.activationObserver = nil
            }
            self.fireActivationRetry(notification: notification)
        }
    }

    private func fireActivationRetry(notification: Notification) {
        guard case .retryArmed(let armedScene) = attachState else { return }
        // Le réessai est le DERNIER essai — quoi qu'il réponde, plus AUCUNE tentative.
        attachState = .settled
        // Défense en profondeur (miroir `UIKitLifecycleSource.observe`) : la vérification de
        // scène passe par isEqual — `===` ne pont pas l'identité Obj-C (preuve O3, phase 0).
        guard (notification.object as? NSObjectProtocol)?.isEqual(armedScene) == true else {
            lastAttachFailureDescription = "activation retry: notification from a foreign scene"
            return
        }
        guard let window = view.window, let scene = window.windowScene else {
            lastAttachFailureDescription = "activation retry: the view lost its window/scene"
            return
        }
        performAttach(scene: scene, window: window, isFinalAttempt: true)
    }

    // MARK: — Le routage du disconnect

    /// L'observer `UIScene.didDisconnectNotification` — filtré PAR TOKEN sur la scène
    /// attachée (la délivrance du centre le garantit) + la double vérification isEqual,
    /// livré sur la file principale. « sceneDidDisconnect … ferme la session » (§5 l.140).
    /// Un retrait de la hiérarchie ne poste PAS cette notification : le retrait temporaire
    /// ne passe JAMAIS ici.
    private func installDisconnectObserver(for scene: UIWindowScene) {
        disconnectObserver = NotificationCenter.default.addObserver(
            forName: UIScene.didDisconnectNotification,
            object: scene,
            queue: .main) { [weak self] notification in
            guard let self else { return }
            guard let connected = self.connectedScene,
                  (notification.object as? NSObjectProtocol)?.isEqual(connected) == true else {
                return
            }
            self.close()
        }
    }

    // MARK: — La fermeture

    /// La fermeture explicite (§5) — idempotente : le close du handle l'est (il résilie les
    /// observations vivantes, ferme la session Kotlin — arrêt host demandé —, no-op sur
    /// session déjà terminée). `attachState` reste `settled` : AUCUN re-attach, y compris
    /// après un re-add à une hiérarchie. Un close SANS session — AVANT tout attach —
    /// consomme lui aussi le budget (revue finale F-3) : l'état non réglé devient réglé,
    /// afin qu'aucun join de fenêtre postérieur — ni l'activation armée d'un réessai — ne
    /// pose une session fraîche après une fermeture explicite (un réessai armé qui arrive
    /// ensuite retombe sur le garde `retryArmed` de `fireActivationRetry` : inerte, et le
    /// token part au deinit).
    public func close() {
        if case .settled = attachState {} else { attachState = .settled }
        kadreSession?.close()
    }

    /// « deinit … ferme la session » (§5 l.140) — plus le retrait des tokens PAR BLOCK. Le
    /// block capture self FAIBLE : le centre ne retient pas le controller, le deinit peut
    /// toujours venir ; les tokens restants sont retirés ICI (le centre les détenait par
    /// token, jamais par self).
    deinit {
        if let token = disconnectObserver {
            NotificationCenter.default.removeObserver(token)
        }
        if let token = activationObserver {
            NotificationCenter.default.removeObserver(token)
        }
        kadreSession?.close()
    }
}

/// La view du host — la sous-classe la plus plate qui soit : UNE override
/// (`didMoveToWindow`, le hook de mouvement en fenêtre qu'UIViewController n'expose pas),
/// qui relaie à son propriétaire. Aucun layout, aucun dessin, aucune autre réponse — pour
/// tout le reste, c'est une UIView (la surfaceView §5 du controller, jamais remplacée).
private final class KadreHostContentView: UIView {

    /// Le relais du mouvement en fenêtre — appelé sur le thread principal (garantie UIKit
    /// des callbacks de vue). Aucune rétention du controller (capturé faible côté owner).
    var onMovedToWindow: (() -> Void)?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        onMovedToWindow?()
    }
}
