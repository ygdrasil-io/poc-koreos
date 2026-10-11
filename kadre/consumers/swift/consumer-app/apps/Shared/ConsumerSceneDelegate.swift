import UIKit

/// Un delegate PAR scène ; le squelette phase 3 n'attache que la scène primaire.
final class ConsumerSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        ConsumerSceneStore.shared.install(windowScene: windowScene)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // L'attach exige une scène ATTACHÉE (jamais `Unattached`) : au `willConnectTo`
        // d'iPadOS 27 la scène l'est encore — le premier passage à Active est le
        // premier moment où la validation de l'attach passe (le driver phase 1
        // attendait `becameActive` pour la même raison).
        ConsumerSceneStore.shared.attachPrimarySceneOnce()
    }
}
