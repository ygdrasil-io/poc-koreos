import UIKit

/// Demande de scènes supplémentaires PAR L'APP — jamais par le bundle de test.
///
/// Mécanique (règle contrôleur, fix round 1) : l'environnement de lancement
/// `KADRE_DRIVER_REQUEST_SECOND_SCENE=<n>` — transmis à l'app hôte par `xcodebuild test` avec
/// le préfixe `TEST_RUNNER_` — demande n scènes secondaires. Chaque demande part APRÈS
/// l'activation de la première scène (contexte de cycle de vie réel, pas le milieu d'un test)
/// et EN SÉRIE (la suivante quand la précédente est connectée). Les erreurs d'activation sont
/// enregistrées dans le store de fixtures pour les diagnostics des tests.
enum DriverSceneRequest {
    private static var started = false
    private static var requested = 0
    private static var inFlight = false

    /// Nombre de scènes secondaires demandées (0 = aucune requête ; l'app reste mono-scène).
    static func requestedSceneCount() -> Int {
        guard let raw = ProcessInfo.processInfo.environment["KADRE_DRIVER_REQUEST_SECOND_SCENE"],
              !raw.isEmpty else { return 0 }
        return Int(raw) ?? 1
    }

    static func startIfNeeded() {
        guard !started else { return }
        started = true
        guard requestedSceneCount() > 0 else { return }
        // La première demande attend la première scène ForegroundActive (chemin (a)+(b) du
        // contrôleur : appel côté app, différé jusqu'à l'activation).
        NotificationCenter.default.addObserver(
            forName: UIScene.didActivateNotification, object: nil, queue: .main
        ) { _ in
            requestNextIfNeeded()
        }
    }

    /// Une scène de plus est connectée (hook du `willConnectTo`) : la suivante peut partir.
    static func sceneConnected() {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { sceneConnected() }
            return
        }
        inFlight = false
        requestNextIfNeeded()
    }

    private static func requestNextIfNeeded() {
        guard requested < requestedSceneCount(), !inFlight else { return }
        inFlight = true
        requested += 1
        UIApplication.shared.requestSceneSessionActivation(nil, userActivity: nil, options: nil) { error in
            // Refus d'activation : enregistrer et ne pas insister — les tests échoueront avec
            // l'erreur exacte (aucun contournement factice).
            DriverFixture.shared.recordSceneActivationError(error)
            inFlight = false
        }
    }

    /// Destruction d'une session de scène par le code de l'APP (le test déclenche, l'app
    /// appelle UIKit) — les erreurs sont enregistrées pour les diagnostics des tests.
    static func destroySceneSession(_ sceneSession: UISceneSession, errorHandler: @escaping (any Error) -> Void) {
        UIApplication.shared.requestSceneSessionDestruction(sceneSession, options: nil) { error in
            DriverFixture.shared.recordSceneDestructionError(error)
            errorHandler(error)
        }
    }
}
