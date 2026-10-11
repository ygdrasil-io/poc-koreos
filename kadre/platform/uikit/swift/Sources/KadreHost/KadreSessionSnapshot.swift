/*
 * `KadreSessionSnapshot` — verbatim §5 : l'état observé d'une session. Le masque Swift
 * fusionne `SessionState` Kotlin avec son outcome : `terminated` PORTE le
 * `KadreSessionOutcome` mappé (§5). Les IDs (`sessionId`) sont des strings opaques (§3).
 */
public enum KadreSessionSnapshot: Equatable, Sendable {
    case starting
    case running
    case stopping
    case terminated(KadreSessionOutcome)
}
