import UIKit

/// Un delegate PAR scène ; le squelette phase 3 n'attache que la scène primaire.
final class ConsumerSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        // Le gate tvOS de coexistence (Task 7, même mécanique phase 1 `DriverSceneRequest`)
        // : la fenêtre primaire tvOS devient la scène de coexistence — contrôles natifs
        // focusables autour de la surface Kadre, l'attach y est controller-driven (l'enfant
        // `KadreHostViewController`) et le store UIKit reste muet. Priorité sur le gate
        // SwiftUI : les deux gates armés ensemble n'ont pas de sémantique.
        #if os(tvOS)
        if ConsumerTvOsCoexistenceScene.launchRequested {
            ConsumerTvOsCoexistenceStore.shared.install(windowScene: windowScene)
            return
        }
        #endif
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
        // SAUF chemins controller-driven (gates Task 6/7) : l'attach y est controller-driven
        // (le wrapper / l'enfant `KadreHostViewController` attache LUI-MÊME au premier
        // moment valide) — le store ne doit JAMAIS doubler (l'attach du wrapper refuserait
        // `AlreadyInUse(Host)` de façon DÉFINITIVE).
        #if os(tvOS)
        guard !ConsumerTvOsCoexistenceScene.launchRequested else { return }
        #endif
        guard !ConsumerSwiftUIScene.launchRequested else { return }
        ConsumerSceneStore.shared.attachPrimarySceneOnce()
    }
}
