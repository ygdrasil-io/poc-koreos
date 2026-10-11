import UIKit

@main
final class MainAppTvos: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        DriverTrace.record("app didFinishLaunching")
        // Inerte sur tvOS (variable absente ; manifeste mono-scène) — symétrie des deux apps.
        DriverSceneRequest.startIfNeeded()
        return true
    }

    /// Même instrumentation que l'app iOS : la configuration de scène demandée par UIKit
    /// est tracée et renvoyée programmatiquement (manifeste équivalent). (SDK 27 :
    /// `delegateClass` est une propriété de l'instance, plus un paramètre d'init.)
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        DriverTrace.record("app configurationForConnecting role=\(connectingSceneSession.role.rawValue) session=\(connectingSceneSession.persistentIdentifier.prefix(8))")
        let configuration = UISceneConfiguration(name: "default", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = DriverSceneDelegate.self
        return configuration
    }
}
