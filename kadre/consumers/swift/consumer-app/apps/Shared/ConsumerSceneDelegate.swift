import UIKit

/// Un delegate PAR scène ; le squelette phase 3 n'attache que la scène primaire.
final class ConsumerSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        // Le gate SwiftUI (Task 6, mécanique phase 1 `DriverSceneRequest`) : la fenêtre
        // primaire devient le chemin SwiftUI — l'attach y est controller-driven (le
        // wrapper) et le store UIKit reste muet (une seule session par scène).
        if ConsumerSwiftUIScene.launchRequested {
            ConsumerSwiftUIStore.shared.install(windowScene: windowScene)
        } else {
            ConsumerSceneStore.shared.install(windowScene: windowScene)
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // L'attach exige une scène ATTACHÉE (jamais `Unattached`) : au `willConnectTo`
        // d'iPadOS 27 la scène l'est encore — le premier passage à Active est le
        // premier moment où la validation de l'attach passe (le driver phase 1
        // attendait `becameActive` pour la même raison).
        //
        // SAUF chemin SwiftUI (gate Task 6) : l'attach y est controller-driven (le wrapper
        // attache LUI-MÊME au premier moment valide) — le store ne doit JAMAIS doubler
        // (l'attach du wrapper refuserait `AlreadyInUse(Host)` de façon DÉFINITIVE).
        guard !ConsumerSwiftUIScene.launchRequested else { return }
        ConsumerSceneStore.shared.attachPrimarySceneOnce()
    }
}
