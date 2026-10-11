/*
 * `KadrePolicyProfile` — verbatim §5 : le profil de politique passé à `attach` et au
 * `KadreHostViewController` (Task 4). La traduction profil → `KadrePolicy` Kotlin vit
 * avec la façade d'attach, pas dans le value model.
 */
public enum KadrePolicyProfile: Sendable {
    case `default`
    case realtime
    case recording
}
