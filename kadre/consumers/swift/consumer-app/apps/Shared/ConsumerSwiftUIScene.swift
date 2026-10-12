import SwiftUI
import UIKit
import KadreUikit

/*
 * Le chemin SwiftUI du consumer (Task 6) — la preuve §9 INTEROP : « wrapper
 * `UIViewControllerRepresentable` possédé par l'host controller, sans layout Kadre ».
 *
 * Deux couches APP-OWNED (le wrapper ne vit PAS dans KadreHost — INTEROP §5 l.139 : le
 * `UIViewControllerRepresentable` est laissé au consommateur) :
 *  1. `KadreHostView` (ConsumerRepresentable.swift) — le representable lui-même,
 *     volontairement minimal (~10 lignes autour de `KadreHostViewController`) ;
 *  2. CE fichier — le câblage de scène : la store des faits (patron `ConsumerSceneStore` :
 *     tout ce que les XCTest lisent, capturé côté app), la racine SwiftUI qui héberge le
 *     wrapper, et le gate de lancement.
 *
 * LE GATE — mécanique phase 1 (`DriverSceneRequest`) : la variable d'environnement de
 * lancement `KADRE_CONSUMER_SWIFTUI` (transmise à l'app hôte par `xcodebuild test` avec le
 * préfixe `TEST_RUNNER_`) ou l'argument `-KADRE_CONSUMER_SWIFTUI` bascule la fenêtre
 * primaire du consumer sur le chemin SwiftUI : un `UIHostingController` racine héberge
 * `KadreHostView` — l'attach est ALORS controller-driven (le `didMoveToWindow` du
 * `KadreHostViewController`, Task 5), et l'attach du store UIKit NE DOIT JAMAIS passer
 * (une seule session par scène — `AlreadyInUse(Host)` est refusé de façon DÉFINITIVE par
 * le controller).
 */

/// Le gate de lancement du chemin SwiftUI.
enum ConsumerSwiftUIScene {

    /// Le gate — lu UNE fois (les ProcessInfo ne changent pas en cours de process).
    static let launchRequested: Bool = {
        ProcessInfo.processInfo.environment["KADRE_CONSUMER_SWIFTUI"] != nil
            || ProcessInfo.processInfo.arguments.contains("-KADRE_CONSUMER_SWIFTUI")
    }()

    /// La factory du chemin SwiftUI : `held` = l'application TENUE (session longue — les
    /// preuves qui font vivre la session au-delà de ~500 ms), défaut = l'application
    /// consumer qui s'arrête elle-même (même sémantique que le chemin UIKit du store).
    static func makeFactory() -> any KadreApplicationFactory {
        if ProcessInfo.processInfo.environment["KADRE_CONSUMER_SWIFTUI_FACTORY"] == "held" {
            return HeldApplicationFactory()
        }
        return ConsumerApplicationFactory()
    }
}

/// La store du chemin SwiftUI — le miroir `ConsumerSceneStore` : TOUT ce que les XCTest
/// lisent sur le wrapper, capturé ICI au fil des callbacks SwiftUI (le bundle de test ne
/// lie AUCUN framework ; `@testable` lit l'app). Main-only par construction —
/// `makeUIViewController`, les réévaluations de body et les lectures des tests partagent
/// le thread principal (aucun verbe atomique nécessaire, même posture que
/// `ConsumerSceneStore`).
final class ConsumerSwiftUIStore: ObservableObject {

    static let shared = ConsumerSwiftUIStore()

    /// La fenêtre du chemin gated (posée par `install`, si le gate est actif) — nil sinon.
    private(set) var window: UIWindow?

    /// Le nombre d'appels RÉELS à `makeUIViewController` — EXACTEMENT 1 attendu sur toute
    /// vie correcte du wrapper : la preuve anti-recréation de Task 6 (une réévaluation du
    /// body ne repasse JAMAIS par make).
    private(set) var makeUIViewControllerCount = 0

    /// LE controller fabriqué par `makeUIViewController` (le dernier) — l'identité (`===`)
    /// de CE controller à travers les réévaluations du body est LA preuve §9 : le wrapper
    /// possédé par l'app survit aux re-renders, SwiftUI ne recrée jamais le controller.
    private(set) var hostController: KadreHostViewController?

    /// L'époque de rendu — `@Published` : chaque bump déclenche `objectWillChange` → la
    /// racine (qui lit CE compteur dans son body) est réévaluée par SwiftUI. LE mécanisme
    /// réel du re-render — pas un hack de test.
    @Published private(set) var renderEpoch = 0

    /// Le fait du fixture : la DERNIÈRE époque vue par une réévaluation du body de la
    /// racine — le test y lit la preuve POSITIVE que la réévaluation a eu lieu (sans elle,
    /// la preuve attaches-once serait vide de sens).
    private(set) var lastRenderedEpoch = 0

    /// Le déclencheur du test : réévalue le body de la racine (état observable + 1).
    func forceBodyReevaluation() {
        renderEpoch += 1
    }

    /// Le fait lu par le body — side-effect DOCUMENTÉ du fixture, idempotent (SwiftUI peut
    /// évaluer un body plusieurs fois par passe : la dernière réévaluation gagne, aucune
    /// hypothèse d'ordre).
    func noteBodyEvaluated(epoch: Int) {
        lastRenderedEpoch = epoch
    }

    /// L'enregistrement du make — appelé PAR `KadreHostView.makeUIViewController` :
    /// compteur + instance, puis le controller rendu INCHANGÉ (le make reste « create & own
    /// » — la store ne fait que le lire, comme `ConsumerFixture` côté Kotlin).
    func recordMakeUIViewController(_ controller: KadreHostViewController) -> KadreHostViewController {
        makeUIViewControllerCount += 1
        hostController = controller
        return controller
    }

    /// Le contrôleur racine du chemin SwiftUI — LE code de production : un
    /// `UIHostingController` dont la racine héberge `KadreHostView`. Utilisé par le gate de
    /// lancement (`install`) ET par les tests (fenêtre propre au test, idiome Task 5).
    func makeRootController(factory: any KadreApplicationFactory) -> UIViewController {
        UIHostingController(rootView: ConsumerSwiftUIRootView(state: self, factory: factory))
    }

    /// Le chemin gated : la fenêtre primaire de l'app devient la scène SwiftUI (le store
    /// UIKit n'est PAS installé — voir `ConsumerSceneDelegate`).
    func install(windowScene: UIWindowScene) {
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = makeRootController(factory: ConsumerSwiftUIScene.makeFactory())
        window.isHidden = false
        window.makeKeyAndVisible()
        self.window = window
    }
}

/// La racine SwiftUI du consumer — lit l'époque (LA lecture observable qui enregistre la
/// dépendance SwiftUI : chaque bump réévalue CE body, le mécanisme réel du re-render),
/// note le fait du fixture, et héberge le wrapper. AUCUNE entrée du wrapper ne change
/// jamais : la réévaluation passe par `updateUIViewController` (contractuellement no-op) —
/// JAMAIS par une recréation (c'est exactement l'invariant prouvé par
/// `uikit-swiftui-consumer-attaches-once`).
struct ConsumerSwiftUIRootView: View {
    @ObservedObject var state: ConsumerSwiftUIStore
    let factory: any KadreApplicationFactory

    var body: some View {
        let epoch = state.renderEpoch
        state.noteBodyEvaluated(epoch: epoch)
        return KadreHostView(applicationFactory: factory, policy: .default)
    }
}
