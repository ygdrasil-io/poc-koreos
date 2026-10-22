import KadreUikit

/*
 * Le noyau de mapping Kotlin → Swift du value model fermé (§5 + §8 : « leur mapping vers
 * la variante Kotlin est exhaustif et testé » — les tests vivent dans le consumer,
 * `KadreHostMappingTests.swift`).
 *
 * RÉSOLUTION DE COLLISION DE NOMS (délibérée) : le framework Kotlin exporte sous ses
 * `swift_name` des types qui portent les MÊMES noms que les enums façade de ce module —
 * `KadreFailure` (protocole Kotlin), `KadreOperation`, `KadrePermission`, `KadrePlatform`,
 * `KadreResourceKind`, `KadrePolicyComponent`, `InteractionFailureReason`, et les
 * familles `SessionState`/`SessionOutcome`/`SessionStopReason` (renommées côté façade).
 * La convention de CE fichier : le nom non qualifié désigne TOUJOURS le type façade de
 * ce module (une déclaration du module courant masque l'import de même nom — pas
 * d'ambiguïté dans les fichiers de la façade) ; le type Kotlin se lit TOUJOURS qualifié
 * `KadreUikit.X`. Aucun typealias n'est introduit : la qualification explicite reste
 * lisible aux revues et interdit toute confusion dans les 17 branches du mapper.
 *
 * EXHAUSTIVITÉ : la hiérarchie Kotlin est un protocole + classes finales — Swift ne
 * vérifie pas l'exhaustivité d'un test de type. La garde est donc DOUBLE :
 *  1. la chaîne couvre UNE branche par variante Kotlin, SANS `default` ; le tail
 *     `fatalError` est inatteignable tant que la hiérarchie est fermée — une variante
 *     Kotlin nouvelle qui traverse le mapper est une erreur de programmation (crash
 *     immédiat, jamais une valeur silencieuse ni un `nil`) ;
 *  2. pour les VRAIES enums Kotlin, les tests comparent la table de mapping à
 *     `entries.count` : toute entrée nouvelle fait échouer la table tant qu'elle n'est
 *     ni mappée ni testée.
 */

/// Le mapper `KadreFailure` Kotlin → enum façade `KadreFailure` (§5 l.164–186).
enum KadreFailureMapper {

    /// Mappe la hiérarchie Kotlin fermée vers la case façade promises. `nil` est
    /// impossible par construction : toute variante inconnue est un crash (fatalError),
    /// pas une valeur — la fermeture est une précondition, pas un cas d'exécution.
    static func make(_ failure: any KadreUikit.KadreFailure) -> KadreFailure {
        if let typed = failure as? KadreUikit.KadreFailureUnsupported {
            return .unsupported(operation: KadreOperationMapper.make(typed.operation))
        }
        if let typed = failure as? KadreUikit.KadreFailurePermissionDenied {
            return .permissionDenied(permission: KadrePermissionMapper.make(typed.permission))
        }
        if let typed = failure as? KadreUikit.KadreFailureUserCancelled {
            return .userCancelled(operation: KadreOperationMapper.make(typed.operation))
        }
        if let typed = failure as? KadreUikit.KadreFailureTemporarilyUnavailable {
            return .temporarilyUnavailable(retryable: typed.retryable)
        }
        if let typed = failure as? KadreUikit.KadreFailureInvalidRequest {
            return .invalidRequest(field: typed.field)
        }
        if let typed = failure as? KadreUikit.KadreFailureAlreadyInUse {
            return .alreadyInUse(resource: KadreResourceKindMapper.make(typed.resource))
        }
        if let typed = failure as? KadreUikit.KadreFailureClosed {
            return .closed(resource: KadreResourceKindMapper.make(typed.resource))
        }
        if let typed = failure as? KadreUikit.KadreFailureResourceLimitExceeded {
            return .resourceLimitExceeded(
                resource: KadreResourceKindMapper.make(typed.resource),
                limit: typed.limit)
        }
        if let typed = failure as? KadreUikit.KadreFailureSourceOverflow {
            return .sourceOverflow(resource: KadreResourceKindMapper.make(typed.resource))
        }
        if let typed = failure as? KadreUikit.KadreFailureStaleRevision {
            return .staleRevision(expected: typed.expected, received: typed.received)
        }
        if let typed = failure as? KadreUikit.KadreFailureInteractionRequired {
            return .interactionRequired(reason: InteractionFailureReasonMapper.make(typed.reason))
        }
        if let typed = failure as? KadreUikit.KadreFailureUnsupportedPolicy {
            return .unsupportedPolicy(component: KadrePolicyComponentMapper.make(typed.component))
        }
        if failure is KadreUikit.KadreFailureParentScopeCancelled {
            return .parentScopeCancelled
        }
        if let typed = failure as? KadreUikit.KadreFailureShutdownTimedOut {
            // `Duration` Kotlin traverse déjà en nanoseconds `int64_t` dans l'export
            // Obj-C (aucun type `Duration` dans le header généré) — §3 : Int64 direct.
            return .shutdownTimedOut(timeoutNanoseconds: typed.timeout)
        }
        if let typed = failure as? KadreUikit.KadreFailureSourceLost {
            // L'ID capture traverse en string OPAQUE (§3) : la description rédigée est
            // l'unique représentation exportée (`value` est internal côté Kotlin) ;
            // précédent web : `failure.source.toString()` (WebHostInterop.kt).
            return .sourceLost(sourceId: kadreOpaqueSourceId(typed.source))
        }
        if failure is KadreUikit.KadreFailureApplicationFailure {
            return .applicationFailure
        }
        if let typed = failure as? KadreUikit.KadreFailurePlatformFailure {
            return .platformFailure(
                platform: KadrePlatformMapper.make(typed.platform),
                domain: typed.domain,
                code: typed.code)
        }
        // Inatteignable : la surface §5 est fermée à 17 variantes, toutes couvertes
        // ci-dessus. L'absence de `default` fait du tail le SEUL chemin d'échappement :
        // il crie au lieu de renvoyer une valeur mensongère.
        fatalError("KadreFailure Kotlin élargi hors du masque fermé §5 : \(failure)")
    }
}

/// Les enums de payload (§3) : même parti pris — une branche par entrée, tail fatal.
/// Chaque mapper est couvert ENTIÈREMENT par une table de test qui garde `entries.count`.

enum KadreOperationMapper {
    static func make(_ operation: KadreUikit.KadreOperation) -> KadreOperation {
        if operation === KadreUikit.KadreOperation.hostattach { return .hostAttach }
        if operation === KadreUikit.KadreOperation.requestredraw { return .requestRedraw }
        if operation === KadreUikit.KadreOperation.displayaccess { return .displayAccess }
        if operation === KadreUikit.KadreOperation.requestwindow { return .requestWindow }
        if operation === KadreUikit.KadreOperation.updatewindow { return .updateWindow }
        if operation === KadreUikit.KadreOperation.requestwindowattention { return .requestWindowAttention }
        if operation === KadreUikit.KadreOperation.closewindow { return .closeWindow }
        if operation === KadreUikit.KadreOperation.respondtocloserequest { return .respondToCloseRequest }
        if operation === KadreUikit.KadreOperation.updatesurface { return .updateSurface }
        if operation === KadreUikit.KadreOperation.installinteractionhandler { return .installInteractionHandler }
        if operation === KadreUikit.KadreOperation.arminteraction { return .armInteraction }
        if operation === KadreUikit.KadreOperation.interaction { return .interaction }
        if operation === KadreUikit.KadreOperation.gamepadeffect { return .gamepadEffect }
        if operation === KadreUikit.KadreOperation.stopgamepadeffects { return .stopGamepadEffects }
        if operation === KadreUikit.KadreOperation.textinput { return .textInput }
        if operation === KadreUikit.KadreOperation.updatetextinput { return .updateTextInput }
        if operation === KadreUikit.KadreOperation.claimdroptransfer { return .claimDropTransfer }
        if operation === KadreUikit.KadreOperation.readdropitem { return .readDropItem }
        if operation === KadreUikit.KadreOperation.capturepermission { return .capturePermission }
        if operation === KadreUikit.KadreOperation.capturerefreshsources { return .captureRefreshSources }
        if operation === KadreUikit.KadreOperation.captureopen { return .captureOpen }
        if operation === KadreUikit.KadreOperation.capturecollectframes { return .captureCollectFrames }
        if operation === KadreUikit.KadreOperation.rawinputaccess { return .rawInputAccess }
        if operation === KadreUikit.KadreOperation.gestureinput { return .gestureInput }
        if operation === KadreUikit.KadreOperation.platformsurfaceaccess { return .platformSurfaceAccess }
        if operation === KadreUikit.KadreOperation.platformwindowaccess { return .platformWindowAccess }
        fatalError("KadreOperation Kotlin élargi hors du masque §3 : \(operation)")
    }
}

enum KadrePermissionMapper {
    static func make(_ permission: KadreUikit.KadrePermission) -> KadrePermission {
        if permission === KadreUikit.KadrePermission.displayenumeration { return .displayEnumeration }
        if permission === KadreUikit.KadrePermission.inputmonitoring { return .inputMonitoring }
        if permission === KadreUikit.KadrePermission.rawinput { return .rawInput }
        if permission === KadreUikit.KadrePermission.capturescreen { return .captureScreen }
        if permission === KadreUikit.KadrePermission.capturewindow { return .captureWindow }
        fatalError("KadrePermission Kotlin élargi hors du masque §3 : \(permission)")
    }
}

enum KadrePlatformMapper {
    static func make(_ platform: KadreUikit.KadrePlatform) -> KadrePlatform {
        if platform === KadreUikit.KadrePlatform.android { return .android }
        if platform === KadreUikit.KadrePlatform.uikit { return .uikit }
        if platform === KadreUikit.KadrePlatform.web { return .web }
        if platform === KadreUikit.KadrePlatform.appkit { return .appKit }
        if platform === KadreUikit.KadrePlatform.win32 { return .win32 }
        if platform === KadreUikit.KadrePlatform.x11 { return .x11 }
        if platform === KadreUikit.KadrePlatform.wayland { return .wayland }
        if platform === KadreUikit.KadrePlatform.linux { return .linux }
        if platform === KadreUikit.KadrePlatform.fake { return .fake }
        fatalError("KadrePlatform Kotlin élargi hors du masque §3 : \(platform)")
    }
}

enum KadreResourceKindMapper {
    static func make(_ resource: KadreUikit.KadreResourceKind) -> KadreResourceKind {
        if resource === KadreUikit.KadreResourceKind.host { return .host }
        if resource === KadreUikit.KadreResourceKind.surface { return .surface }
        if resource === KadreUikit.KadreResourceKind.window { return .window }
        if resource === KadreUikit.KadreResourceKind.windowrequest { return .windowRequest }
        if resource === KadreUikit.KadreResourceKind.display { return .display }
        if resource === KadreUikit.KadreResourceKind.inputsource { return .inputSource }
        if resource === KadreUikit.KadreResourceKind.rawinputaccess { return .rawInputAccess }
        if resource === KadreUikit.KadreResourceKind.inputdevice { return .inputDevice }
        if resource === KadreUikit.KadreResourceKind.gamepad { return .gamepad }
        if resource === KadreUikit.KadreResourceKind.eventcollector { return .eventCollector }
        if resource === KadreUikit.KadreResourceKind.interaction { return .interaction }
        if resource === KadreUikit.KadreResourceKind.droptransfer { return .dropTransfer }
        if resource === KadreUikit.KadreResourceKind.dropitem { return .dropItem }
        if resource === KadreUikit.KadreResourceKind.cursorimage { return .cursorImage }
        if resource === KadreUikit.KadreResourceKind.gamepadeffect { return .gamepadEffect }
        if resource === KadreUikit.KadreResourceKind.textinputsession { return .textInputSession }
        if resource === KadreUikit.KadreResourceKind.capturesource { return .captureSource }
        if resource === KadreUikit.KadreResourceKind.capturesession { return .captureSession }
        if resource === KadreUikit.KadreResourceKind.capturecollector { return .captureCollector }
        if resource === KadreUikit.KadreResourceKind.capturebuffer { return .captureBuffer }
        if resource === KadreUikit.KadreResourceKind.retainedpayload { return .retainedPayload }
        if resource === KadreUikit.KadreResourceKind.imageresource { return .imageResource }
        if resource === KadreUikit.KadreResourceKind.eventsequence { return .eventSequence }
        fatalError("KadreResourceKind Kotlin élargi hors du masque §3 : \(resource)")
    }
}

enum KadrePolicyComponentMapper {
    static func make(_ component: KadreUikit.KadrePolicyComponent) -> KadrePolicyComponent {
        if component === KadreUikit.KadrePolicyComponent.execution { return .execution }
        if component === KadreUikit.KadrePolicyComponent.lifecycleevents { return .lifecycleEvents }
        if component === KadreUikit.KadrePolicyComponent.hostsignals { return .hostSignals }
        if component === KadreUikit.KadrePolicyComponent.windowevents { return .windowEvents }
        if component === KadreUikit.KadrePolicyComponent.deviceevents { return .deviceEvents }
        if component === KadreUikit.KadrePolicyComponent.inputevents { return .inputEvents }
        if component === KadreUikit.KadrePolicyComponent.devicepolicy { return .devicePolicy }
        if component === KadreUikit.KadrePolicyComponent.captureevents { return .captureEvents }
        if component === KadreUikit.KadrePolicyComponent.captureframes { return .captureFrames }
        if component === KadreUikit.KadrePolicyComponent.diagnostics { return .diagnostics }
        if component === KadreUikit.KadrePolicyComponent.resources { return .resources }
        fatalError("KadrePolicyComponent Kotlin élargi hors du masque §3 : \(component)")
    }
}

enum InteractionFailureReasonMapper {
    static func make(_ reason: KadreUikit.InteractionFailureReason) -> InteractionFailureReason {
        if reason === KadreUikit.InteractionFailureReason.missing { return .missing }
        if reason === KadreUikit.InteractionFailureReason.expired { return .expired }
        if reason === KadreUikit.InteractionFailureReason.consumed { return .consumed }
        if reason === KadreUikit.InteractionFailureReason.wrongsurface { return .wrongSurface }
        fatalError("InteractionFailureReason Kotlin élargi hors du masque §3 : \(reason)")
    }
}

/// `SessionStopReason` Kotlin → `KadreStopReason` (§3 : les cinq valeurs).
enum KadreStopReasonMapper {
    static func make(_ reason: KadreUikit.SessionStopReason) -> KadreStopReason {
        if reason === KadreUikit.SessionStopReason.hostrequested { return .hostRequested }
        if reason === KadreUikit.SessionStopReason.applicationrequested { return .applicationRequested }
        if reason === KadreUikit.SessionStopReason.applicationcancelled { return .applicationCancelled }
        if reason === KadreUikit.SessionStopReason.parentcancelled { return .parentCancelled }
        if reason === KadreUikit.SessionStopReason.hostdetached { return .hostDetached }
        fatalError("SessionStopReason Kotlin élargi hors des cinq valeurs §3 : \(reason)")
    }
}

/// `SessionOutcome` Kotlin → `KadreSessionOutcome` (§5).
enum KadreSessionOutcomeMapper {
    static func make(_ outcome: any KadreUikit.SessionOutcome) -> KadreSessionOutcome {
        if outcome is KadreUikit.SessionOutcomeCompleted {
            return .completed
        }
        if let typed = outcome as? KadreUikit.SessionOutcomeStopped {
            return .stopped(KadreStopReasonMapper.make(typed.reason))
        }
        if let typed = outcome as? KadreUikit.SessionOutcomeFailed {
            return .failed(KadreFailureMapper.make(typed.failure))
        }
        fatalError("SessionOutcome Kotlin élargi hors du masque §5 : \(outcome)")
    }
}

/// `SessionState` Kotlin → `KadreSessionSnapshot` (§5 : `terminated` PORTE l'outcome).
enum KadreSessionSnapshotMapper {
    static func make(_ state: any KadreUikit.SessionState) -> KadreSessionSnapshot {
        if state is KadreUikit.SessionStateStarting {
            return .starting
        }
        if state is KadreUikit.SessionStateRunning {
            return .running
        }
        if state is KadreUikit.SessionStateStopping {
            return .stopping
        }
        if let typed = state as? KadreUikit.SessionStateTerminated {
            return .terminated(KadreSessionOutcomeMapper.make(typed.outcome))
        }
        fatalError("SessionState Kotlin élargi hors du masque §5 : \(state)")
    }
}

/// L'ID opaque d'une source capture : la description RÉDIGÉE exportée par le Kotlin
/// (`toString()` constant — le `value` numérique est internal et jamais publié, §3).
/// Cousu à part pour être testable : `CaptureSourceId` n'a AUCUN initialisateur exporté
/// (constructeur Kotlin internal, `init`/`new` unavailable sur KotlinBase) — une instance
/// fabriquée hors Kotlin (`class_createInstance`) ne survit PAS au aller-retour de
/// référence Kotlin (son en-tête Kotlin est absente), donc la branche `SourceLost` ne
/// peut pas être pilotée de bout en bout depuis Swift ; la DÉRIVATION du payload, si.
func kadreOpaqueSourceId(_ source: KadreUikit.CaptureSourceId) -> String {
    source.description
}

// La surface consumériste promise par le plan : un `init?` public qui fabrique la valeur
// façade depuis l'objet Kotlin exporté. Le `nil` n'arrive JAMAIS tant que la hiérarchie
// Kotlin reste fermée — le mapper crie (`fatalError`) sur une variante hors masque au
// lieu de renvoyer une valeur silencieuse ; le failable documente que l'élargissement
// Kotlin est hors contrat, pas un cas d'exécution.
public extension KadreFailure {
    init?(_ failure: any KadreUikit.KadreFailure) {
        self = KadreFailureMapper.make(failure)
    }
}
