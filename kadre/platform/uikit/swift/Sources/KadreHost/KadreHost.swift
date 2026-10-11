/// Scaffolding phase 3 (Task 2) : ce répertoire `swift/Sources/KadreHost` est câblé PAR
/// CHEMIN dans les targets consumer de `kadre/consumers/swift/consumer-app` — le squelette
/// doit prouver que des sources Swift du dépôt Kadre compilent DANS le consumer externe.
/// Le contenu réel arrive en DEUX temps : le value model fermé (Task 3 — `KadreFailure`,
/// `KadreError`, snapshots/outcomes/stop-reason + le mapping Kotlin exhaustif, testés par
/// le consumer) puis la façade `KadreIosHost` elle-même (Task 4). Ce placeholder garde le
/// chemin vivant et le câblage compilable sans préjuger de la façade.
public enum KadreIosHost {
    // Task 4 : attach(windowScene:window:surfaceView:applicationFactory:)
}
