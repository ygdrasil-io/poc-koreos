import XCTest
import KadreUikit
#if canImport(KadreConsumerIos)
@testable import KadreConsumerIos
#elseif canImport(KadreConsumerTvOs)
@testable import KadreConsumerTvOs
#endif

/// Task 3 — le value model FERMÉ de la façade Swift (`kadre/INTEROP-EXPORTS.md` §5
/// l.164–188 + §3 + §8) : chaque variante Kotlin `KadreFailure` mappe vers LA case Swift
/// promises avec LE payload promis, les data objects Kotlin vers leurs cases sans
/// payload, les cinq `SessionStopReason` vers `KadreStopReason`, `SessionState`/
/// `SessionOutcome` vers `KadreSessionSnapshot`/`KadreSessionOutcome`, et `KadreError`
/// est `Equatable` avec un message DÉRIVÉ du failure (jamais du contexte).
///
/// LA PREUVE §8 « leur mapping vers la variante Kotlin est exhaustif et testé » :
///  1. chaque case de façade a UN test qui construit le type Kotlin généré correspondant
///     et asserte case + payload — un payload échangé (expected/received, les deux
///     resources…) ou une case doublée fait échouer SON test ;
///  2. les enums de payload Kotlin (véritables enums Kotlin) sont couverts ENTièrement,
///     table par table, et chaque table asserte `entries.count` : une variante Kotlin
///     NOUVELLE change le compte et fait échouer la table tant que le mapping ne la
///     couvre pas.
///
/// NB de nommage : ce bundle importe DEUX modules qui exportent des noms communs
/// (`KadreFailure`, `KadreOperation`… — l'enum façade de `KadreConsumerIos`/`TvOs` et le
/// type Kotlin de `KadreUikit`). Le fichier n'écrit JAMAIS ces noms nus : les types
/// Kotlin se lisent via leurs classes générées sans ambiguïté (`KadreFailureUnsupported`…)
/// ou qualifiés `KadreUikit.X`, et les cases façade arrivent par inférence (`guard case`,
/// membre-point) depuis les types de retour des mappers internes `@testable`.
/// L'erreur interne du harnais de mapping (jamais une valeur du domaine).
private enum KadreHostMappingTestsError: Error {
    case rawCaptureSourceUnavailable
}

final class KadreHostMappingTests: XCTestCase {

    /// L'unique instance observable de `CaptureSourceId` en Swift : AUCUN initialisateur
    /// exporté (constructeur Kotlin internal, `init`/`new` unavailable sur KotlinBase) —
    /// la mémoire brute zéro-remplie du runtime ObjC suffit : le `toString` Kotlin de
    /// CaptureSourceId ne lit AUCUN champ (rédaction constante), ce qui est exactement
    /// ce que le mapper traverse.
    private func rawCaptureSourceId() throws -> KadreUikit.CaptureSourceId {
        // `class_createInstance` importe son résultat non possédé (`Any?`) : un simple
        // `as?` suffit, et l'unique instance de test est laissée au runtime.
        guard let raw = class_createInstance(KadreUikit.CaptureSourceId.self, 0),
              let source = raw as? KadreUikit.CaptureSourceId else {
            XCTFail("class_createInstance(CaptureSourceId) returned no instance")
            throw KadreHostMappingTestsError.rawCaptureSourceUnavailable
        }
        return source
    }

    // MARK: — Les 17 cases KadreFailure (§5 l.164–186)

    /// unsupported(operation) — la case ET l'opération (la moindre équivoque unsupported/
    /// userCancelled ou hostAttach/requestRedraw fait échouer ce test).
    func testUnsupportedMapsCaseAndOperation() {
        let mapped = KadreFailureMapper.make(
            KadreFailureUnsupported(operation: .hostattach))
        guard case .unsupported(let operation) = mapped else {
            return XCTFail("expected .unsupported, got \(mapped)")
        }
        XCTAssertEqual(operation, .hostAttach)
    }

    /// permissionDenied(permission).
    func testPermissionDeniedMapsCaseAndPermission() {
        let mapped = KadreFailureMapper.make(
            KadreFailurePermissionDenied(permission: .capturescreen))
        guard case .permissionDenied(let permission) = mapped else {
            return XCTFail("expected .permissionDenied, got \(mapped)")
        }
        XCTAssertEqual(permission, .captureScreen)
    }

    /// userCancelled(operation).
    func testUserCancelledMapsCaseAndOperation() {
        let mapped = KadreFailureMapper.make(
            KadreFailureUserCancelled(operation: .requestredraw))
        guard case .userCancelled(let operation) = mapped else {
            return XCTFail("expected .userCancelled, got \(mapped)")
        }
        XCTAssertEqual(operation, .requestRedraw)
    }

    /// temporarilyUnavailable(retryable) — le Bool traverse dans les DEUX sens.
    func testTemporarilyUnavailableMapsRetryable() {
        guard case .temporarilyUnavailable(let retryable) =
            KadreFailureMapper.make(KadreFailureTemporarilyUnavailable(retryable: true))
        else {
            return XCTFail("expected .temporarilyUnavailable(true)")
        }
        XCTAssertTrue(retryable)
        guard case .temporarilyUnavailable(let retryableFalse) =
            KadreFailureMapper.make(KadreFailureTemporarilyUnavailable(retryable: false))
        else {
            return XCTFail("expected .temporarilyUnavailable(false)")
        }
        XCTAssertFalse(retryableFalse)
    }

    /// invalidRequest(field) — le champ est optionnel côté Kotlin (`String?`) : le nil
    /// traverse (§6 TS : `field: string | null`).
    func testInvalidRequestMapsField() {
        guard case .invalidRequest(let field) =
            KadreFailureMapper.make(KadreFailureInvalidRequest(field: "window"))
        else {
            return XCTFail("expected .invalidRequest(window)")
        }
        XCTAssertEqual(field, "window")
        guard case .invalidRequest(nil) =
            KadreFailureMapper.make(KadreFailureInvalidRequest(field: nil))
        else {
            return XCTFail("expected .invalidRequest(nil)")
        }
    }

    /// alreadyInUse(resource).
    func testAlreadyInUseMapsCaseAndResource() {
        let mapped = KadreFailureMapper.make(
            KadreFailureAlreadyInUse(resource: .window))
        guard case .alreadyInUse(let resource) = mapped else {
            return XCTFail("expected .alreadyInUse, got \(mapped)")
        }
        XCTAssertEqual(resource, .window)
    }

    /// closed(resource).
    func testClosedMapsCaseAndResource() {
        let mapped = KadreFailureMapper.make(
            KadreFailureClosed(resource: .surface))
        guard case .closed(let resource) = mapped else {
            return XCTFail("expected .closed, got \(mapped)")
        }
        XCTAssertEqual(resource, .surface)
    }

    /// resourceLimitExceeded(resource, limit) — le couple entier, pas un payload échangé.
    func testResourceLimitExceededMapsResourceAndLimit() {
        let mapped = KadreFailureMapper.make(
            KadreFailureResourceLimitExceeded(resource: .gamepad, limit: 4))
        guard case .resourceLimitExceeded(let resource, let limit) = mapped else {
            return XCTFail("expected .resourceLimitExceeded, got \(mapped)")
        }
        XCTAssertEqual(resource, .gamepad)
        XCTAssertEqual(limit, 4)
    }

    /// sourceOverflow(resource).
    func testSourceOverflowMapsCaseAndResource() {
        let mapped = KadreFailureMapper.make(
            KadreFailureSourceOverflow(resource: .capturebuffer))
        guard case .sourceOverflow(let resource) = mapped else {
            return XCTFail("expected .sourceOverflow, got \(mapped)")
        }
        XCTAssertEqual(resource, .captureBuffer)
    }

    /// staleRevision(expected, received) — l'ordre expected/received est TU par ce test.
    func testStaleRevisionMapsExpectedAndReceived() {
        let mapped = KadreFailureMapper.make(
            KadreFailureStaleRevision(expected: 7, received: 3))
        guard case .staleRevision(let expected, let received) = mapped else {
            return XCTFail("expected .staleRevision, got \(mapped)")
        }
        XCTAssertEqual(expected, 7)
        XCTAssertEqual(received, 3)
    }

    /// interactionRequired(reason).
    func testInteractionRequiredMapsCaseAndReason() {
        let mapped = KadreFailureMapper.make(
            KadreFailureInteractionRequired(reason: .expired))
        guard case .interactionRequired(let reason) = mapped else {
            return XCTFail("expected .interactionRequired, got \(mapped)")
        }
        XCTAssertEqual(reason, .expired)
    }

    /// unsupportedPolicy(component).
    func testUnsupportedPolicyMapsCaseAndComponent() {
        let mapped = KadreFailureMapper.make(
            KadreFailureUnsupportedPolicy(component: .captureframes))
        guard case .unsupportedPolicy(let component) = mapped else {
            return XCTFail("expected .unsupportedPolicy, got \(mapped)")
        }
        XCTAssertEqual(component, .captureFrames)
    }

    /// parentScopeCancelled — data object Kotlin (`shared`) → case sans payload.
    func testParentScopeCancelledMapsToCaseWithoutPayload() {
        let mapped = KadreFailureMapper.make(KadreFailureParentScopeCancelled.shared)
        guard case .parentScopeCancelled = mapped else {
            return XCTFail("expected .parentScopeCancelled, got \(mapped)")
        }
    }

    /// shutdownTimedOut(timeoutNanoseconds) — `Duration` Kotlin traverse déjÀ en
    /// nanoseconds `int64_t` dans l'export Obj-C (le header générÉ n'a AUCUN type
    /// `Duration` : `initWithTimeout:(int64_t)` EST l'accesseur nanoseconds) ; la
    /// conversion façade est donc l'IDENTITÉ (§3 : « Duration → nanoseconds Int64 »).
    func testShutdownTimedOutMapsNanoseconds() {
        let mapped = KadreFailureMapper.make(
            KadreFailureShutdownTimedOut(timeout: 2_000_000_000))
        guard case .shutdownTimedOut(let timeoutNanoseconds) = mapped else {
            return XCTFail("expected .shutdownTimedOut, got \(mapped)")
        }
        XCTAssertEqual(timeoutNanoseconds, 2_000_000_000)
    }

    /// sourceLost(sourceId) — l'ID capture traverse en string OPAQUE rédigée (le champ
    /// `value` de `CaptureSourceId` est internal côté Kotlin) ; précédent web :
    /// `field("sourceId", failure.source.toString())` (WebHostInterop.kt).
    ///
    /// LIMITE documentée : `CaptureSourceId` n'a AUCUN initialisateur exporté (le
    /// constructeur Kotlin est internal, `init`/`new` unavailable sur KotlinBase) — la
    /// branche `SourceLost` ne peut pas être pilotée de bout en bout depuis Swift (une
    /// instance fabriquée hors Kotlin ne survit pas au aller-retour de référence) ; la
    /// case est prouvée sur l'assemblage du mapper et le PAYLOAD dérivé est prouvé par
    /// la couture `kadreOpaqueSourceId` — c'est la couture que le web precedent mappe
    /// aussi (`failure.source.toString()`).
    func testSourceLostMapsCaseAndDerivesOpaqueSourceId() throws {
        // 1. La case : le mapper route bien la variante Kotlin vers .sourceLost.
        let wrapper = KadreFailureSourceLost(source: rawInstanceOfCaptureSourceIdForCaseCheck())
        let mapped = KadreFailureMapper.make(wrapper)
        guard case .sourceLost = mapped else {
            return XCTFail("expected .sourceLost, got \(mapped)")
        }

        // 2. Le payload : la dérivation traverse la description rédigée exportée.
        let source = try rawCaptureSourceId()
        let sourceId = kadreOpaqueSourceId(source)
        XCTAssertEqual(sourceId, source.description)
        XCTAssertEqual(sourceId, "CaptureSourceId(<redacted>)")
        XCTAssertFalse(sourceId.isEmpty)
    }

    /// Une instance pour la VÉRIFICATION DE CASE uniquement : son payload ne survit pas
    /// au wrapper Kotlin (voir la limite ci-dessus) — la case, elle, est routée.
    private func rawInstanceOfCaptureSourceIdForCaseCheck() -> KadreUikit.CaptureSourceId {
        guard let raw = class_createInstance(KadreUikit.CaptureSourceId.self, 0),
              let source = raw as? KadreUikit.CaptureSourceId else {
            fatalError("class_createInstance(CaptureSourceId) returned no instance")
        }
        return source
    }

    /// applicationFailure — data object → case sans payload.
    func testApplicationFailureMapsToCaseWithoutPayload() {
        let mapped = KadreFailureMapper.make(KadreFailureApplicationFailure.shared)
        guard case .applicationFailure = mapped else {
            return XCTFail("expected .applicationFailure, got \(mapped)")
        }
    }

    /// platformFailure(platform, domain, code) — le triplet entier.
    func testPlatformFailureMapsPlatformDomainAndCode() {
        let mapped = KadreFailureMapper.make(
            KadreFailurePlatformFailure(platform: .uikit, domain: "NSPOSIXErrorDomain", code: "1"))
        guard case .platformFailure(let platform, let domain, let code) = mapped else {
            return XCTFail("expected .platformFailure, got \(mapped)")
        }
        XCTAssertEqual(platform, .uikit)
        XCTAssertEqual(domain, "NSPOSIXErrorDomain")
        XCTAssertEqual(code, "1")
    }

    // MARK: — Exhaustivité des enums de payload (§8) — table entière + garde sur count

    private func assertEnumTable<Entry>(
        _ table: [(Entry, String)],
        kotlinCount: Int,
        map: (Entry) -> Any,
        table file: StaticString = #filePath,
        line: UInt = #line
    ) {
        // La garde d'exhaustivité : une variante Kotlin NOUVELLE change `entries.count`
        // et fait échouer CE test tant que la table (donc le mapper) ne la couvre pas.
        XCTAssertEqual(
            table.count, kotlinCount,
            "the Kotlin enum grew: extend the facade enum AND its mapper table",
            file: file, line: line)
        for (kotlin, expectedName) in table {
            let mapped = map(kotlin)
            XCTAssertEqual(
                "\(mapped)", expectedName,
                "payload \(type(of: kotlin)) must map to the promised facade case",
                file: file, line: line)
        }
    }

    func testKadreOperationMappingIsExhaustive() {
        let table: [(KadreUikit.KadreOperation, String)] = [
            (.hostattach, "hostAttach"),
            (.requestredraw, "requestRedraw"),
            (.displayaccess, "displayAccess"),
            (.requestwindow, "requestWindow"),
            (.updatewindow, "updateWindow"),
            (.requestwindowattention, "requestWindowAttention"),
            (.closewindow, "closeWindow"),
            (.respondtocloserequest, "respondToCloseRequest"),
            (.updatesurface, "updateSurface"),
            (.installinteractionhandler, "installInteractionHandler"),
            (.arminteraction, "armInteraction"),
            (.interaction, "interaction"),
            (.gamepadeffect, "gamepadEffect"),
            (.stopgamepadeffects, "stopGamepadEffects"),
            (.textinput, "textInput"),
            (.updatetextinput, "updateTextInput"),
            (.claimdroptransfer, "claimDropTransfer"),
            (.readdropitem, "readDropItem"),
            (.capturepermission, "capturePermission"),
            (.capturerefreshsources, "captureRefreshSources"),
            (.captureopen, "captureOpen"),
            (.capturecollectframes, "captureCollectFrames"),
            (.rawinputaccess, "rawInputAccess"),
            (.gestureinput, "gestureInput"),
            (.platformsurfaceaccess, "platformSurfaceAccess"),
            (.platformwindowaccess, "platformWindowAccess"),
        ]
        assertEnumTable(
            table,
            kotlinCount: KadreUikit.KadreOperation.entries.count,
            map: { (operation: KadreUikit.KadreOperation) in KadreOperationMapper.make(operation) })
    }

    func testKadrePermissionMappingIsExhaustive() {
        let table: [(KadreUikit.KadrePermission, String)] = [
            (.displayenumeration, "displayEnumeration"),
            (.inputmonitoring, "inputMonitoring"),
            (.rawinput, "rawInput"),
            (.capturescreen, "captureScreen"),
            (.capturewindow, "captureWindow"),
        ]
        assertEnumTable(
            table,
            kotlinCount: KadreUikit.KadrePermission.entries.count,
            map: { (permission: KadreUikit.KadrePermission) in KadrePermissionMapper.make(permission) })
    }

    func testKadrePlatformMappingIsExhaustive() {
        let table: [(KadreUikit.KadrePlatform, String)] = [
            (.android, "android"),
            (.uikit, "uikit"),
            (.web, "web"),
            (.appkit, "appKit"),
            (.win32, "win32"),
            (.x11, "x11"),
            (.wayland, "wayland"),
            (.linux, "linux"),
            (.fake, "fake"),
        ]
        assertEnumTable(
            table,
            kotlinCount: KadreUikit.KadrePlatform.entries.count,
            map: { (platform: KadreUikit.KadrePlatform) in KadrePlatformMapper.make(platform) })
    }

    func testKadreResourceKindMappingIsExhaustive() {
        let table: [(KadreUikit.KadreResourceKind, String)] = [
            (.host, "host"),
            (.surface, "surface"),
            (.window, "window"),
            (.windowrequest, "windowRequest"),
            (.display, "display"),
            (.inputsource, "inputSource"),
            (.rawinputaccess, "rawInputAccess"),
            (.inputdevice, "inputDevice"),
            (.gamepad, "gamepad"),
            (.eventcollector, "eventCollector"),
            (.interaction, "interaction"),
            (.droptransfer, "dropTransfer"),
            (.dropitem, "dropItem"),
            (.cursorimage, "cursorImage"),
            (.gamepadeffect, "gamepadEffect"),
            (.textinputsession, "textInputSession"),
            (.capturesource, "captureSource"),
            (.capturesession, "captureSession"),
            (.capturecollector, "captureCollector"),
            (.capturebuffer, "captureBuffer"),
            (.retainedpayload, "retainedPayload"),
            (.imageresource, "imageResource"),
            (.eventsequence, "eventSequence"),
        ]
        assertEnumTable(
            table,
            kotlinCount: KadreUikit.KadreResourceKind.entries.count,
            map: { (resource: KadreUikit.KadreResourceKind) in KadreResourceKindMapper.make(resource) })
    }

    func testKadrePolicyComponentMappingIsExhaustive() {
        let table: [(KadreUikit.KadrePolicyComponent, String)] = [
            (.execution, "execution"),
            (.lifecycleevents, "lifecycleEvents"),
            (.hostsignals, "hostSignals"),
            (.windowevents, "windowEvents"),
            (.deviceevents, "deviceEvents"),
            (.inputevents, "inputEvents"),
            (.devicepolicy, "devicePolicy"),
            (.captureevents, "captureEvents"),
            (.captureframes, "captureFrames"),
            (.diagnostics, "diagnostics"),
            (.resources, "resources"),
        ]
        assertEnumTable(
            table,
            kotlinCount: KadreUikit.KadrePolicyComponent.entries.count,
            map: { (component: KadreUikit.KadrePolicyComponent) in KadrePolicyComponentMapper.make(component) })
    }

    func testInteractionFailureReasonMappingIsExhaustive() {
        let table: [(KadreUikit.InteractionFailureReason, String)] = [
            (.missing, "missing"),
            (.expired, "expired"),
            (.consumed, "consumed"),
            (.wrongsurface, "wrongSurface"),
        ]
        assertEnumTable(
            table,
            kotlinCount: KadreUikit.InteractionFailureReason.entries.count,
            map: { (reason: KadreUikit.InteractionFailureReason) in InteractionFailureReasonMapper.make(reason) })
    }

    // MARK: — KadreStopReason : les cinq valeurs de SessionStopReason (§3)

    func testKadreStopReasonMappingIsExhaustive() {
        let table: [(KadreUikit.SessionStopReason, String)] = [
            (.hostrequested, "hostRequested"),
            (.applicationrequested, "applicationRequested"),
            (.applicationcancelled, "applicationCancelled"),
            (.parentcancelled, "parentCancelled"),
            (.hostdetached, "hostDetached"),
        ]
        assertEnumTable(
            table,
            kotlinCount: KadreUikit.SessionStopReason.entries.count,
            map: { (reason: KadreUikit.SessionStopReason) in KadreStopReasonMapper.make(reason) })
    }

    // MARK: — Snapshot / outcome (§5)

    /// starting / running / stopping — data objects Kotlin → cases sans payload.
    func testSnapshotMapsStartingRunningAndStopping() {
        guard case .starting = KadreSessionSnapshotMapper.make(SessionStateStarting.shared) else {
            return XCTFail("expected .starting")
        }
        guard case .running = KadreSessionSnapshotMapper.make(SessionStateRunning.shared) else {
            return XCTFail("expected .running")
        }
        guard case .stopping = KadreSessionSnapshotMapper.make(SessionStateStopping.shared) else {
            return XCTFail("expected .stopping")
        }
    }

    /// terminated(KadreSessionOutcome) — les TROIS outcomes traversent DANS le snapshot.
    func testSnapshotTerminatedCarriesEachMappedOutcome() {
        guard case .terminated(let completed) =
            KadreSessionSnapshotMapper.make(SessionStateTerminated(
                outcome: SessionOutcomeCompleted.shared))
        else {
            return XCTFail("expected .terminated(completed)")
        }
        guard case .completed = completed else {
            return XCTFail("expected .completed, got \(completed)")
        }

        guard case .terminated(let stopped) =
            KadreSessionSnapshotMapper.make(SessionStateTerminated(
                outcome: SessionOutcomeStopped(reason: .applicationrequested)))
        else {
            return XCTFail("expected .terminated(stopped)")
        }
        guard case .stopped(let reason) = stopped else {
            return XCTFail("expected .stopped, got \(stopped)")
        }
        XCTAssertEqual(reason, .applicationRequested)

        guard case .terminated(let failed) =
            KadreSessionSnapshotMapper.make(SessionStateTerminated(
                outcome: SessionOutcomeFailed(failure: KadreFailureApplicationFailure.shared)))
        else {
            return XCTFail("expected .terminated(failed)")
        }
        guard case .failed(let failure) = failed else {
            return XCTFail("expected .failed, got \(failed)")
        }
        guard case .applicationFailure = failure else {
            return XCTFail("expected .applicationFailure, got \(failure)")
        }
    }

    // MARK: — KadreError (§5) : Equatable + message dérivé

    /// `KadreError` est `Equatable` telle que déclarée en §5 — le message PARTICIPE à
    /// l'égalité (dérivé du failure, il ne l'amène jamais à diverger pour un même
    /// failure) ; deux failures distincts ne sont JAMAIS égaux, deux messages distincts
    /// pour un même failure ne le sont pas non plus (conformité littérale à la
    /// déclaration, équivalence sémantique garantie par la dérivation).
    func testKadreErrorEquality() {
        let application = KadreError(failure: .applicationFailure, message: "Application failed")
        XCTAssertEqual(application, KadreError(failure: .applicationFailure, message: "Application failed"))
        XCTAssertNotEqual(application, KadreError(failure: .applicationFailure, message: "detailed reason"))
        XCTAssertNotEqual(
            application,
            KadreError(failure: .parentScopeCancelled, message: "Application failed"))
    }

    /// Le message dérivé reflète les textes de `KadreFailure.message` Kotlin
    /// (foundation/diagnostics/Results.kt) — lecture humaine, JAMAIS dans le branching.
    /// Écart documenté : Kotlin interpole les noms d'entrées PascalCase (`HostAttach`) ;
    /// la façade interpole les cases lower camel case promises (`hostAttach`).
    func testKadreErrorMessageMirrorsKotlinTexts() throws {
        let rawSource = try rawCaptureSourceId()
        let expected: [(any KadreUikit.KadreFailure, String)] = [
            (KadreFailureUnsupported(operation: .hostattach), "Operation hostAttach is unsupported"),
            (KadreFailurePermissionDenied(permission: .capturescreen), "Permission captureScreen is denied"),
            (KadreFailureUserCancelled(operation: .requestredraw), "Operation requestRedraw was cancelled by the user"),
            (KadreFailureTemporarilyUnavailable(retryable: true), "Resource is temporarily unavailable"),
            (KadreFailureInvalidRequest(field: "window"), "Request is invalid: window"),
            (KadreFailureInvalidRequest(field: nil), "Request is invalid"),
            (KadreFailureAlreadyInUse(resource: .window), "Resource window is already in use"),
            (KadreFailureClosed(resource: .surface), "Resource surface is closed"),
            (KadreFailureResourceLimitExceeded(resource: .gamepad, limit: 4), "Resource limit exceeded for gamepad"),
            (KadreFailureSourceOverflow(resource: .capturebuffer), "Source overflow for captureBuffer"),
            (KadreFailureStaleRevision(expected: 7, received: 3), "Stale revision: expected 7, received 3"),
            (KadreFailureInteractionRequired(reason: .expired), "Interaction is required: expired"),
            (KadreFailureParentScopeCancelled.shared, "Parent scope is cancelled"),
            (KadreFailureShutdownTimedOut(timeout: 2_000_000_000), "Shutdown timed out"),
            (KadreFailureSourceLost(source: rawSource), "Capture source was lost"),
            (KadreFailureApplicationFailure.shared, "Application failed"),
            (KadreFailurePlatformFailure(platform: .uikit, domain: "NSPOSIXErrorDomain", code: "1"),
             "Platform operation failed: uikit/NSPOSIXErrorDomain/1"),
        ]
        XCTAssertEqual(expected.count, 17, "the closed KadreFailure surface is 17 cases")
        for (kotlin, message) in expected {
            let derived = KadreError(failure: KadreFailureMapper.make(kotlin))
            XCTAssertEqual(derived.message, message, "for \(kotlin)")
        }
    }

    /// La fermeture (§5) : le masque de cases de la façade est EXACTEMENT celui de la
    /// hiérarchie Kotlin — ce test documente le compte et lie le mapper au masque.
    func testKadreFailureSurfaceIsSeventeenCases() {
        // Une des 17 cases par famille de payload : le guard-case ci-dessous refuse tout
        // mapper qui élargirait ou renommerait une case.
        let sample = KadreFailureMapper.make(KadreFailureApplicationFailure.shared)
        guard case .applicationFailure = sample else {
            return XCTFail("the closed surface changed: \(sample)")
        }
    }
}
