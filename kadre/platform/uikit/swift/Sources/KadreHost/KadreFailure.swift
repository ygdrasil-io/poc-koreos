import Foundation

/*
 * Le value model FERMÉ de la façade Swift (`kadre/INTEROP-EXPORTS.md` §3 + §5) :
 * `KadreFailure` est une enum Swift avec EXACTEMENT les cases de `KadreFailure` Kotlin
 * (foundation/diagnostics/Results.kt) et les mêmes champs stables — verbatim §5
 * l.164–186, aucun cas en plus, aucun payload renommé. Les payloads énumérés sont les
 * enums fermées de §3 : mêmes variantes que Kotlin, orthographe sémantique identique,
 * noms de cases lower camel case (§8). Les IDs traversent en strings opaques et les
 * durées en nanoseconds `Int64` (§3) ; le mapping Kotlin → Swift vit DANS
 * `KadreFailureMapping.swift` (exhaustif et testé, §8).
 */

/// La famille fermée des échecs Kadre (§5 l.164–186). `Equatable` sur les payloads ;
/// le message humain dérivé (`KadreError`) n'entre jamais dans l'égalité ni le
/// branching (§3).
public enum KadreFailure: Equatable, Sendable {
    case unsupported(operation: KadreOperation)
    case permissionDenied(permission: KadrePermission)
    case userCancelled(operation: KadreOperation)
    case temporarilyUnavailable(retryable: Bool)
    case invalidRequest(field: String?)
    case alreadyInUse(resource: KadreResourceKind)
    case closed(resource: KadreResourceKind)
    case resourceLimitExceeded(resource: KadreResourceKind, limit: Int64)
    case sourceOverflow(resource: KadreResourceKind)
    case staleRevision(expected: Int64, received: Int64)
    case interactionRequired(reason: InteractionFailureReason)
    case unsupportedPolicy(component: KadrePolicyComponent)
    case parentScopeCancelled
    case shutdownTimedOut(timeoutNanoseconds: Int64)
    case sourceLost(sourceId: String)
    case applicationFailure
    case platformFailure(platform: KadrePlatform, domain: String, code: String)
}

/// Les opérations refusables (§3 : mêmes variantes que `KadreOperation` Kotlin).
public enum KadreOperation: Equatable, Sendable {
    case hostAttach
    case requestRedraw
    case displayAccess
    case requestWindow
    case updateWindow
    case requestWindowAttention
    case closeWindow
    case respondToCloseRequest
    case updateSurface
    case installInteractionHandler
    case armInteraction
    case interaction
    case gamepadEffect
    case stopGamepadEffects
    case textInput
    case updateTextInput
    case claimDropTransfer
    case readDropItem
    case capturePermission
    case captureRefreshSources
    case captureOpen
    case captureCollectFrames
    case rawInputAccess
    case gestureInput
    case platformSurfaceAccess
    case platformWindowAccess
}

/// Les permissions refusables (§3 : mêmes variantes que `KadrePermission` Kotlin).
public enum KadrePermission: Equatable, Sendable {
    case displayEnumeration
    case inputMonitoring
    case rawInput
    case captureScreen
    case captureWindow
}

/// Les plateformes d'un `platformFailure` (§3 : mêmes variantes que `KadrePlatform`).
public enum KadrePlatform: Equatable, Sendable {
    case android
    case uikit
    case web
    case appKit
    case win32
    case x11
    case wayland
    case linux
    case fake
}

/// Les ressources nommées par les échecs (§3 : mêmes variantes que `KadreResourceKind`).
public enum KadreResourceKind: Equatable, Sendable {
    case host
    case surface
    case window
    case windowRequest
    case display
    case inputSource
    case rawInputAccess
    case inputDevice
    case gamepad
    case eventCollector
    case interaction
    case dropTransfer
    case dropItem
    case cursorImage
    case gamepadEffect
    case textInputSession
    case captureSource
    case captureSession
    case captureCollector
    case captureBuffer
    case retainedPayload
    case imageResource
    case eventSequence
}

/// Les composants de politique (§3 : mêmes variantes que `KadrePolicyComponent`).
public enum KadrePolicyComponent: Equatable, Sendable {
    case execution
    case lifecycleEvents
    case hostSignals
    case windowEvents
    case deviceEvents
    case inputEvents
    case devicePolicy
    case captureEvents
    case captureFrames
    case diagnostics
    case resources
}

/// Les raisons d'interaction requise (§3 : `InteractionFailureReason`, mêmes variantes
/// que Kotlin — le nom Kotlin ne porte PAS le préfixe `Kadre`, la façade non plus).
public enum InteractionFailureReason: Equatable, Sendable {
    case missing
    case expired
    case consumed
    case wrongSurface
}
