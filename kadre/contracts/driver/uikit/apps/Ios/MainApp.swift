import UIKit

@main
final class MainAppIos: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        DriverTrace.record("app didFinishLaunching")
        // Scènes secondaires demandées par l'APP (env de lancement, préfixe TEST_RUNNER_).
        DriverSceneRequest.startIfNeeded()
        return true
    }

    /// Configuration demandée par UIKit AU MOMENT de connecter une scène : le callback est
    /// tracé et renvoie programmatiquement la même configuration que le manifeste
    /// (nom « default », même delegate). Si ce callback n'apparaît PAS dans la trace d'un
    /// échec CI, UIKit n'a jamais engagé la connexion de scène côté système. (SDK 27 :
    /// `delegateClass` est une propriété de l'instance, plus un paramètre d'init.)
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        DriverTrace.record("app configurationForConnecting role=\(connectingSceneSession.role.rawValue) session=\(connectingSceneSession.persistentIdentifier.prefix(8))")
        let configuration = UISceneConfiguration(name: "default", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = DriverSceneDelegate.self
        return configuration
    }
}
