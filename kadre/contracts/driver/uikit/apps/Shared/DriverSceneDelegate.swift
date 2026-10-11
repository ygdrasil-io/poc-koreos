import UIKit

/// Un delegate PAR scène : chaque connexion enregistre son propre fixture
/// (`ObjectIdentifier(scene)`), chaque activation marque SON fixture.
/// TOUT callback reçu est tracé (`DriverTrace`) : un échec CI doit montrer la chaîne
/// willConnectTo → didBecomeActive, ou l'anneau manquant de la chaîne.
final class DriverSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        DriverTrace.record("scene willConnectTo role=\(session.role.rawValue) session=\(session.persistentIdentifier.prefix(8))")
        guard let windowScene = scene as? UIWindowScene else {
            DriverTrace.record("scene willConnectTo : PAS une UIWindowScene (\(String(describing: type(of: scene))))")
            return
        }
        DriverFixture.shared.install(windowScene: windowScene)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        DriverTrace.record("scene didBecomeActive session=\(sceneId(scene))")
        DriverFixture.shared.fixture(for: scene)?.markActive()
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        DriverTrace.record("scene didDisconnect session=\(sceneId(scene))")
    }

    private func sceneId(_ scene: UIScene) -> String {
        String(scene.session.persistentIdentifier.prefix(8))
    }
}
