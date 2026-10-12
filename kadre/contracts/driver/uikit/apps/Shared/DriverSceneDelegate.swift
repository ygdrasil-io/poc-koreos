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
        // Phase 4 : si une activation du provider est en vol, CETTE scène lui est corrélée
        // (après l'install — la fenêtre/vue que le provider résoudra existent déjà).
        DriverWindowSceneProvider.shared.sceneConnected(windowScene)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        DriverTrace.record("scene didBecomeActive session=\(sceneId(scene))")
        DriverFixture.shared.fixture(for: scene)?.markActive()
        // Les demandes de scènes secondaires ne partent JAMAIS avant une activation réelle
        // (garde anti-course iPadOS 27 — voir DriverSceneRequest.sceneActivated).
        DriverSceneRequest.sceneActivated()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        // Phase 4 : la sortie d'Unattached des scènes demandées programmatiquement passe par
        // ici (elles restent ForegroundInactive — jamais didBecomeActive, preuve phase 1) ;
        // tracé pour la lisibilité des échecs CI, la résolution lit l'État réel (pump provider).
        DriverTrace.record("scene willEnterForeground session=\(sceneId(scene)) state=\(scene.activationState.rawValue)")
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        DriverTrace.record("scene didDisconnect session=\(sceneId(scene))")
    }

    private func sceneId(_ scene: UIScene) -> String {
        String(scene.session.persistentIdentifier.prefix(8))
    }
}
