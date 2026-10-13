/*
 * `KadreStopReason` — §3 : « les cinq valeurs de `SessionStopReason` » Kotlin, lower
 * camel case (§8). Le mapping exhaustif vit dans `KadreFailureMapping.swift`.
 */
public enum KadreStopReason: Equatable, Sendable {
    case hostRequested
    case applicationRequested
    case applicationCancelled
    case parentCancelled
    case hostDetached
}
