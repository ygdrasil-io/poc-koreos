import UIKit

/// État partagé app/test. La fenêtre et les vues sont créées à la connexion de la scène ;
/// `markActive` est appelé par `sceneDidBecomeActive`. Les tests attendent `waitUntilReady`.
final class DriverFixture {
    static let shared = DriverFixture()

    private(set) var window: UIWindow?
    private(set) var observedView: UIView?
    private(set) var detachedView: UIView?
    private(set) var becameActive = false

    func install(windowScene: UIWindowScene) {
        let window = UIWindow(windowScene: windowScene)
        let frame = CGRect(x: 0, y: 0, width: 320, height: 240)
        let observed = UIView(frame: frame)
        let detached = UIView(frame: frame)
        window.addSubview(observed) // observed est DANS la fenêtre
        // detached n'est JAMAIS ajoutée à la fenêtre
        window.isHidden = false
        window.makeKeyAndVisible()
        self.window = window
        self.observedView = observed
        self.detachedView = detached
    }

    func markActive() { becameActive = true }

    /// Attente bornée (5 s, pas de sommeil nu) : échoue avec l'état courant si non prêt.
    func waitUntilReady() throws {
        let deadline = Date().addingTimeInterval(5)
        while !(window != nil && observedView != nil && becameActive) {
            if Date() > deadline {
                throw NSError(domain: "KadreUikitDriver", code: 1,
                              userInfo: ["state": "window=\(String(describing: window)) active=\(becameActive)"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
    }
}
