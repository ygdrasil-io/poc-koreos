import UIKit

@main
final class MainAppIos: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Scènes secondaires demandées par l'APP (env de lancement, préfixe TEST_RUNNER_).
        DriverSceneRequest.startIfNeeded()
        return true
    }
}
