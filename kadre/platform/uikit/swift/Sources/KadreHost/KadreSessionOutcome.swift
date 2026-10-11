/*
 * `KadreSessionOutcome` — verbatim §5 : l'issue terminale d'une session, terminée par
 * achèvement, par arrêt motivé (`KadreStopReason`) ou par échec (`KadreFailure`).
 */
public enum KadreSessionOutcome: Equatable, Sendable {
    case completed
    case stopped(KadreStopReason)
    case failed(KadreFailure)
}
