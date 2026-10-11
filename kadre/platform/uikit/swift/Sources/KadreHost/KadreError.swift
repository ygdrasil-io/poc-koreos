/*
 * `KadreError` — verbatim §5 : la traduction de toute failure directe en erreur Swift
 * (le `attach` de la façade, Task 4, la lève). Le `message` reflète la sémantique de
 * `KadreFailure.message` Kotlin : lisible par un humain, DÉRIVÉ du failure, et il
 * n'entre jamais dans le branching (§3). Particularité déclarative : la structure EST
 * `Equatable` (§5) et le message PARTICIPE donc à l'égalité telle qu'écrite — comme la
 * dérivation est déterministe (même failure → même message), deux `KadreError` d'un même
 * failure restent égaux ; l'initialisateur `init(failure:)` est la forme canonique qui
 * garantit cette propriété.
 */
public struct KadreError: Error, Equatable, Sendable {
    public let failure: KadreFailure
    public let message: String

    /// La forme littérale de la déclaration §5 : un failure et son message explicite.
    public init(failure: KadreFailure, message: String) {
        self.failure = failure
        self.message = message
    }

    /// La forme canonique : le message est DÉRIVÉ du failure (miroir de l'extension
    /// Kotlin `KadreFailure.message`, foundation/diagnostics/Results.kt). Écart documenté :
    /// Kotlin interpole les noms d'entrées PascalCase (`HostAttach`) ; la façade interpole
    /// les cases lower camel case promises par §8 (`hostAttach`). Lecture seule, jamais
    /// du branching.
    public init(failure: KadreFailure) {
        self.init(failure: failure, message: kadreMessage(for: failure))
    }
}

/// Le miroir des dix-huit lignes de texte de `KadreFailure.message` Kotlin — un texte
/// par variante, dérivé des seuls payloads. Interne : la surface publique est `KadreError`.
func kadreMessage(for failure: KadreFailure) -> String {
    switch failure {
    case .unsupported(let operation):
        return "Operation \(operation) is unsupported"
    case .permissionDenied(let permission):
        return "Permission \(permission) is denied"
    case .userCancelled(let operation):
        return "Operation \(operation) was cancelled by the user"
    case .temporarilyUnavailable:
        return "Resource is temporarily unavailable"
    case .invalidRequest(let field):
        return "Request is invalid\(field.map { ": \($0)" } ?? "")"
    case .alreadyInUse(let resource):
        return "Resource \(resource) is already in use"
    case .closed(let resource):
        return "Resource \(resource) is closed"
    case .resourceLimitExceeded(let resource, _):
        return "Resource limit exceeded for \(resource)"
    case .sourceOverflow(let resource):
        return "Source overflow for \(resource)"
    case .staleRevision(let expected, let received):
        return "Stale revision: expected \(expected), received \(received)"
    case .interactionRequired(let reason):
        return "Interaction is required: \(reason)"
    case .unsupportedPolicy(let component):
        return "Policy component \(component) is unsupported"
    case .parentScopeCancelled:
        return "Parent scope is cancelled"
    case .shutdownTimedOut:
        return "Shutdown timed out"
    case .sourceLost:
        return "Capture source was lost"
    case .applicationFailure:
        return "Application failed"
    case .platformFailure(let platform, let domain, let code):
        return "Platform operation failed: \(platform)/\(domain)/\(code)"
    }
}
