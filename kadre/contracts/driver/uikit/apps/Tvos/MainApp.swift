import UIKit

@main
final class MainAppTvos: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Inerte sur tvOS (variable absente ; manifeste mono-scène) — symétrie des deux apps.
        DriverSceneRequest.startIfNeeded()
        return true
    }
}
