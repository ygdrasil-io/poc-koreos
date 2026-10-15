import UIKit
import GameController
import KadreUikit

/*
 * Task 7 — LA SCÈNE DE COEXISTENCE tvOS du consumer : des contrôles natifs FOCUSABLES
 * (UIButton — UIControl) autour de la surface Kadre, dans LA MÊME hiérarchie, navigués par
 * le MOTEUR DE FOCUS SYSTÈME (la Siri Remote pilote le focus UIKit du host — jamais
 * Kadre). C'est la forme exacte de la preuve de coexistence phase 5, tenue dans une app :
 *
 *  — L'HONNÊTETÉ TÉLÉCOMMANDE (ruling, registre uikit.md lignes dédiées) : Kadre n'a
 *    AUCUN binding télécommande — ni `GCMicroGamepad` (non livré, « suivi enregistré »
 *    BACKEND-CAPABILITIES §6.2), ni observation `UIFocusEnvironment` (hors périmètre v1).
 *    La surface Kadre N'EST PAS un participant au focus (`canBecomeFocused == false` —
 *    une UIView plate) : le focus système navigue LES CONTRÔLES NATIFS de l'app et
 *    coexiste avec la surface sans jamais la viser. Ce que ce sample PROUVE sur le
 *    simulateur : l'attach, la coexistence hiérarchique, le câblage de focus réel. Le
 *    BOUT réel de la Siri Remote reste un charter manuel sur Apple TV physique
 *    (`XCUIRemote` est de l'UI-testing, pas synthétisable in-process) — DEFERRED-AUDIT.
 *
 *  — LA SAISIE SYSTÈME (BCK-024, livrée) : le bouton « Saisie » ouvre la session texte
 *    PAR LE CHEMIN DU CONSUMER — `SurfaceInput.openTextInput` (suspend Kotlin) atteint
 *    depuis Swift via `ConsumerTextInputBridge` (module applicatif consumer-kmp, artefacts
 *    publiés, jamais project(...)). Le receiver natif Kadre (UITextField caché) reçoit la
 *    saisie du SYSTÈME ; les événements `Replace` réaffichés par le label prouvent le
 *    write-back de bout en bout. Le clavier réel reste un charter manuel sur matériel.
 *
 *  — LA MANETTE CONDITIONNELLE PAR MATÉRIEL (BCK-027 adjacent) : le sample inventorie les
 *    `GCController` et classe par le MÊME règle que Kadre (`extendedGamepad` non-nil =
 *    manette complète ; sinon `microGamepad` non-nil = télécommande micro-only). La
 *    RÉPONSE de l'app au matériel est app-owned et conditionnelle par hardware — aucun
 *    comportement de plateforme n'est ajouté côté Kadre.
 *
 * LE GATE — mécanique phase 1 (`DriverSceneRequest`, miroir du gate SwiftUI) : la variable
 * d'environnement `KADRE_CONSUMER_TVOS_COEXIST` ou l'argument `-KADRE_CONSUMER_TVOS_COEXIST`
 * bascule la fenêtre primaire tvOS sur cette scène (priorité sur le gate SwiftUI — les
 * deux gates armés ensemble n'ont pas de sémantique). La factory est la factory TENUE
 * RÉACTIVE (`ConsumerCoexistenceApplicationFactory`) : le sample est une session LONGUE —
 * la self-stop de `ConsumerApplication` (~500 ms) fermerait la session pendant la
 * navigation — et la surface primaire y est capturée au PREMIER MOMENT OÙ ELLE EXISTE
 * (le pont de saisie lit ces faits ; voir le Kotlin).
 */

/// Le gate de lancement de la scène de coexistence tvOS.
enum ConsumerTvOsCoexistenceScene {

    /// Le gate — lu UNE fois (les ProcessInfo ne changent pas en cours de process).
    static let launchRequested: Bool = {
        ProcessInfo.processInfo.environment["KADRE_CONSUMER_TVOS_COEXIST"] != nil
            || ProcessInfo.processInfo.arguments.contains("-KADRE_CONSUMER_TVOS_COEXIST")
    }()

    /// La factory du sample : TOUJOURS la factory tenue RÉACTIVE (session longue — voir
    /// l'en-tête — et capture de la surface au premier moment où elle existe, dont le
    /// pont de saisie lit les faits).
    static func makeFactory() -> any KadreApplicationFactory {
        ConsumerCoexistenceApplicationFactory()
    }

    /// La racine de la scène — le controller de coexistence (utilisé par le gate ET par
    /// les tests, fenêtre propre au test, idiome Task 5).
    static func makeRootController() -> ConsumerTvOsCoexistenceController {
        ConsumerTvOsCoexistenceController(factory: makeFactory())
    }
}

/// La store du chemin gated — le miroir `ConsumerSwiftUIStore` : la fenêtre posée par le
/// délégué de scène et le controller hôte Kadre (l'attach est CONTROLLER-driven, le store
/// ne doubl jamais — `AlreadyInUse(Host)` est définitif). Main-only par construction.
final class ConsumerTvOsCoexistenceStore {

    static let shared = ConsumerTvOsCoexistenceStore()

    /// La fenêtre du chemin gated (posée par `install`, si le gate est actif) — nil sinon.
    private(set) var window: UIWindow?

    /// Le controller hôte Kadre de la scène de coexistence — l'identité lue par les tests.
    private(set) var hostController: KadreHostViewController?

    /// Le chemin gated : la fenêtre primaire tvOS devient la scène de coexistence (le
    /// store UIKit n'est PAS installé — voir `ConsumerSceneDelegate`).
    func install(windowScene: UIWindowScene) {
        let window = UIWindow(windowScene: windowScene)
        let controller = ConsumerTvOsCoexistenceScene.makeRootController()
        window.rootViewController = controller
        window.isHidden = false
        window.makeKeyAndVisible()
        self.window = window
        self.hostController = controller.hostController
    }
}

/// Le controller de coexistence — contrôles natifs focusables autour de la surface Kadre
/// (l'en-tête du fichier porte la sémantique complète). Tout sur le thread principal.
final class ConsumerTvOsCoexistenceController: UIViewController {

    /// La factory tenue — consommée par le controller hôte Kadre (enfant).
    private let factory: any KadreApplicationFactory

    /// LE controller hôte Kadre — sa view EST la surfaceView (§5) : posée DANS la
    /// hiérarchie de ce controller, entre les contrôles natifs. L'attach est
    /// controller-driven (`didMoveToWindow`, exactement une fois).
    private(set) var hostController: KadreHostViewController!

    /// Les contrôles natifs — les SEULS participants au focus de cette scène.
    private(set) var textButton: UIButton!
    private(set) var refreshButton: UIButton!
    private(set) var stopButton: UIButton!

    /// Les labels natifs — titre et état (hors focus, lecture seule).
    private(set) var titleLabel: UILabel!
    private(set) var statusLabel: UILabel!

    /// Le poll d'état (session Kadre + session texte + inventaire manettes) — le même
    /// patron que `ConsumerSceneStore` : timer RunLoop sur le main, jamais de sommeil nu.
    private var statusPollTimer: Timer?

    /// L'observation manette (connect/disconnect) — les tokens, retirés au deinit.
    private var controllerObservers: [any NSObjectProtocol] = []

    /// La dernière description d'inventaire manettes posée par un événement connect/
    /// disconnect — la classification CONDITIONNELLE PAR MATÉRIEL affichée au label.
    private(set) var controllerInventoryDescription: String = ""

    init(factory: any KadreApplicationFactory) {
        self.factory = factory
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) est sans objet — passez par init(factory:)")
    }

    deinit {
        statusPollTimer?.invalidate()
        for token in controllerObservers {
            NotificationCenter.default.removeObserver(token)
        }
    }

    // MARK: — La hiérarchie de coexistence

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        buildNativeControls()
        embedKadreSurface()
        installControllerInventory()
        startStatusPolling()
    }

    /// Les contrôles natifs focusables — des `UIButton` (UIControl : focusables par le
    /// moteur de focus système sur tvOS, sans override). La surface Kadre n'en fait
    /// JAMAIS partie (voir `preferredFocusEnvironments`).
    private func buildNativeControls() {
        titleLabel = UILabel()
        titleLabel.text = "Kadre tvOS — coexistence (focus système, saisie BCK-024)"
        titleLabel.textColor = .white
        titleLabel.font = .preferredFont(forTextStyle: .title2)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(titleLabel)

        func sampleButton(_ title: String, _ action: Selector) -> UIButton {
            let button = UIButton(type: .system)
            button.setTitle(title, for: .normal)
            button.setTitleColor(.white, for: .normal)
            button.backgroundColor = .darkGray
            button.layer.cornerRadius = 8
            button.translatesAutoresizingMaskIntoConstraints = false
            button.addTarget(self, action: action, for: .primaryActionTriggered)
            view.addSubview(button)
            return button
        }
        textButton = sampleButton("Saisie", #selector(toggleTextInput))
        refreshButton = sampleButton("Périphériques", #selector(refreshControllerInventory))
        stopButton = sampleButton("Arrêter", #selector(stopSession))

        statusLabel = UILabel()
        statusLabel.textColor = .lightGray
        statusLabel.font = .preferredFont(forTextStyle: .footnote)
        statusLabel.numberOfLines = 0
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(statusLabel)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            titleLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            textButton.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 16),
            textButton.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 120),
            textButton.widthAnchor.constraint(equalToConstant: 260),
            textButton.heightAnchor.constraint(equalToConstant: 60),

            refreshButton.centerYAnchor.constraint(equalTo: textButton.centerYAnchor),
            refreshButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            refreshButton.widthAnchor.constraint(equalToConstant: 260),
            refreshButton.heightAnchor.constraint(equalToConstant: 60),

            stopButton.centerYAnchor.constraint(equalTo: textButton.centerYAnchor),
            stopButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -120),
            stopButton.widthAnchor.constraint(equalToConstant: 260),
            stopButton.heightAnchor.constraint(equalToConstant: 60),

            statusLabel.topAnchor.constraint(equalTo: textButton.bottomAnchor, constant: 12),
            statusLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 120),
            statusLabel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -120),
        ])
    }

    /// La surface Kadre — la view du controller hôte (§5 : la view EST la surfaceView,
    /// jamais remplacée, aucun layout Kadre posé par elle) en enfant de ce controller,
    /// SOUS la rangée de boutons. La coexistence est LITTÉRALE : une hiérarchie, deux
    /// mondes — les contrôles natifs au-dessus, la surface Kadre en dessous.
    private func embedKadreSurface() {
        hostController = KadreHostViewController(applicationFactory: factory, policy: .default)
        addChild(hostController)
        hostController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hostController.view)
        hostController.didMove(toParent: self)
        NSLayoutConstraint.activate([
            hostController.view.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 12),
            hostController.view.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 120),
            hostController.view.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -120),
            hostController.view.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
        ])
    }

    /// LE CÂBLAGE DE FOCUS SYSTÈME — l'honnêteté en une override : la scène préfère la
    /// rangée de contrôles natifs ; la surface Kadre n'y figure JAMAIS (aucun binding
    /// télécommande Kadre — le focus de la surface reste piloté par les transitions de
    /// scène, registre uikit.md). La navigation entre les trois boutons est celle du
    /// MOTEUR SYSTÈME, pilotée par la Siri Remote — Kadre n'y participe pas.
    override var preferredFocusEnvironments: [UIFocusEnvironment] {
        [textButton, refreshButton, stopButton]
    }

    // MARK: — Les actions natives (app-owned)

    /// « Saisie » — l'aller-retour BCK-024 par le chemin consumer : open (session texte
    /// exclusive, receiver natif posé par le host) puis close au press suivant. La
    /// description d'échec éventuelle s'affiche au label (l'aplatissement du bridge).
    @objc private func toggleTextInput() {
        if ConsumerTextInputBridge.shared.stateDescription.value as? String == "Active"
            || ConsumerTextInputBridge.shared.stateDescription.value as? String == "Suspended" {
            ConsumerTextInputBridge.shared.closeTextInput()
        } else {
            // L'export Obj-C d'un `suspend fun` Kotlin porte DEUX paramètres de completion
            // (résultat, erreur) — le miroir `awaitTermination { outcome, error in }` de
            // `ConsumerSceneStore`. Le pont répond `nil` au succès (l'aplatissement) ; la
            // composante `error` reste le canal d'une exception Kotlin imprévue.
            ConsumerTextInputBridge.shared.openTextInput { [weak self] failure, _ in
                guard let self else { return }
                if let failure {
                    self.statusLabel.text = "openTextInput: \(failure)"
                }
            }
        }
    }

    /// « Périphériques » — le ré-inventaire manettte CONDITIONNEL par matériel (BCK-027 :
    /// la classification est la règle Kadre ; la réponse affichage est app-owned).
    @objc private func refreshControllerInventory() {
        controllerInventoryDescription = Self.classifyControllers()
    }

    /// « Arrêter la session » — requestStop VIA LE HANDLE du controller hôte : la raison
    /// attendue est `Stopped(HostRequested)` (la factory tenue ne s'arrête jamais seule).
    @objc private func stopSession() {
        hostController.kadreSession?.requestStop()
    }

    // MARK: — L'inventaire manette conditionnel par matériel

    /// La classification BCK-027 au niveau APP : `extendedGamepad` non-nil = manette
    /// complète ; sinon `microGamepad` non-nil = télécommande micro-only (la Siri Remote
    /// du simulateur n'apparaît PAS comme GCController — l'inventaire matériel réel se
    /// valide sur Apple TV physique, charter). Aucune entrée = aucun périphérique. La
    /// désignation reste PAR PROFIL (`extendedGamepad`/`microGamepad`/autre) — ni nom de
    /// produit ni index, la seule vérité classée est le profil du périphérique.
    static func classifyControllers() -> String {
        let controllers = GCController.controllers()
        if controllers.isEmpty { return "périphériques: aucun" }
        let descriptions = controllers.map { controller -> String in
            if controller.extendedGamepad != nil { return "manette complète" }
            if controller.microGamepad != nil { return "micro-only" }
            return "autre"
        }
        return "périphériques: " + descriptions.joined(separator: ", ")
    }

    private func installControllerInventory() {
        controllerInventoryDescription = Self.classifyControllers()
        let connect = NotificationCenter.default.addObserver(
            forName: .GCControllerDidConnect, object: nil, queue: .main
        ) { [weak self] _ in
            self?.controllerInventoryDescription = Self.classifyControllers()
        }
        let disconnect = NotificationCenter.default.addObserver(
            forName: .GCControllerDidDisconnect, object: nil, queue: .main
        ) { [weak self] _ in
            self?.controllerInventoryDescription = Self.classifyControllers()
        }
        controllerObservers = [connect, disconnect]
    }

    // MARK: — Le poll d'état (le même patron que ConsumerSceneStore)

    private func startStatusPolling() {
        let timer = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in
            self?.refreshStatus()
        }
        RunLoop.main.add(timer, forMode: .common)
        statusPollTimer = timer
    }

    private func refreshStatus() {
        var lines: [String] = []
        if let handle = hostController?.kadreSession {
            lines.append("session: \(handle.state)")
        } else {
            lines.append("session: pas encore attachée")
        }
        let textState = ConsumerTextInputBridge.shared.stateDescription.value as? String ?? "?"
        let text = ConsumerTextInputBridge.shared.text.value as? String ?? ""
        lines.append("saisie: \(textState)\(text.isEmpty ? "" : " — « \(text) »")")
        lines.append(controllerInventoryDescription.isEmpty ? Self.classifyControllers() : controllerInventoryDescription)
        lines.append("focus: contrôles natifs (moteur système) — la surface Kadre n'est pas un participant au focus")
        statusLabel.text = lines.joined(separator: "\n")
    }
}
