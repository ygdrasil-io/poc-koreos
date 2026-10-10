import UIKit

/// Un delegate PAR scène : chaque connexion enregistre son propre fixture
/// (`ObjectIdentifier(scene)`), chaque activation marque SON fixture.
final class DriverSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        DriverFixture.shared.install(windowScene: windowScene)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        DriverFixture.shared.fixture(for: scene)?.markActive()
    }
}
