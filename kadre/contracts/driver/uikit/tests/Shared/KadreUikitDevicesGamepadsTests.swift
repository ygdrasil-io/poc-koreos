import XCTest
import UIKit
import GameController
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves phase 8 (Task 6) — l'inventaire devices/gamepads RÉEL du Simulator, les DEUX
/// familles, et la preuve non-appel raw input. Noms de scénarios FIGÉS pour Task 7 (la
/// résolution phase 7 : les ids hyphénés du registre ne sont PAS des noms de méthode) :
///
///   uikit-devices-inventory-empty-honest → test_uikit_devices_inventory_empty_honest()
///   uikit-gamepads-empty-honest          → test_uikit_gamepads_empty_honest()
///   uikit-raw-input-noncall              → test_uikit_raw_input_noncall()
///
/// FAITS ÉPELÉS PAR SCÉNARIO (le précédent consommateur Android AND-008, miroir exact) :
///
/// 1. DEVICES INVENTAIRE HONNÊTE (uikit-devices-inventory-empty-honest) — l'inventaire publié
///    est EXACTEMENT l'inventaire contrôleur RÉEL du process, l'oracle SDK relu par le
///    scénario lui-même (`GCController.controllers()` + `GCKeyboard.coalesced` +
///    `GCMouse.mice()` — la composition exacte du source production, noms compris :
///    `vendorName ?? productCategory` / `productCategory`) — Enumerated, jamais fabriqué,
///    jamais un Unsupported/Unavailable déguisé. LA FORME VIDE EST LA RÉFÉRENCE SANS
///    PÉRIPHÉRIQUE (le nom du scénario) ; le Simulator Xcode 27 PONTE les devices du Mac hôte
///    (reality épinglée au driver : une souris gamer arrive en GCController à profil étendu,
///    le clavier du Mac en GCKeyboard coalesced) — l'épingle EXÉCUTABLE est donc le MIROIR
///    PARFAIT, fait par fait (composition, kinds, noms, gamepads = profil étendu SEULEMENT) :
///    un device fabriqué ou omis échoue SUR TOUTE machine. La forme de référence sans
///    contrôleur est : inventaire devices VIDE et gamepads VIDES, tenue partout où la réalité
///    SDK est zéro.
/// 2. GAMEPADS = PROFIL ÉTENDU SEULEMENT (uikit-gamepads-empty-honest) — la liste manette
///    est TENUE DANS LE TEMPS (fenêtre d'observation bornée) à l'identité SDK du profil
///    étendu (compteur ET noms) — jamais une manette fabriquée, jamais une micro-manette
///    (Siri Remote) publiée comme manette complète, jamais un Unavailable déguisé. Sur hôte
///    sans manette l'énoncé EST le vide du nom du scénario (zéro gamepads, miroir du zéro SDK) ;
///    sur l'hôte ponté, l'identité tient sur la réalité non vide — une divergence d'un côté
///    comme de l'autre échoue.
/// 3. RAW INPUT NON-CALL (uikit-raw-input-noncall) — la preuve non-appel re-épinglée : la
///    capability publiée refuse AVANT l'appel (`rawInput = Unsupported(RawInputAccess)`), et
///    la demande répond le refus taxonomique EXACT — l'opération nommée par la taxonomie,
///    aucune primitive native atteinte (identité d'échec, pas un message).
final class KadreUikitDevicesGamepadsTests: KadreUikitPhase8FixtureCase {

    /// L'état du manager devices dans un état attendu — attente bornée, pumping du run loop.
    func awaitDeviceState(
        _ scope: any KadreScope,
        _ what: String,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (DeviceManagerState) -> Bool,
    ) throws -> DeviceManagerState? {
        let deadline = Date().addingTimeInterval(timeout)
        var last: DeviceManagerState?
        while true {
            if let state = scope.devices.state.value as? DeviceManagerState {
                last = state
                if predicate(state) { return state }
            }
            if Date() > deadline {
                XCTFail("\(what) not reached in \(timeout)s — last=\(String(describing: last))",
                        file: file, line: line)
                return last
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    func isEnumerated(_ state: DeviceManagerState) -> Bool {
        state.inventory is DeviceInventoryEnumerated
    }

    /// La lecture SDK du scénario — l'inventaire contrôleur RÉEL du process, la composition
    /// exacte que le source production énumère (`UIKitGCControllerSource.controllers()`).
    func realControllerFacts() -> (controllers: [GCController], keyboard: GCKeyboard?, mice: [GCMouse]) {
        let controllers = (GCController.controllers() as? [GCController]) ?? []
        let mice = (GCMouse.mice() as? [GCMouse]) ?? []
        return (controllers, GCKeyboard.coalesced, mice)
    }

    /// Le nom attendu d'UN jeton SDK — le miroir EXACT du `nameOf` du source production
    /// (`vendorName ?? productCategory` pour un `GCController`, `productCategory` pour le
    /// clavier/souris GC ; le reste n'annonce rien).
    func sdkName(ofController controller: GCController) -> String? {
        controller.vendorName ?? controller.productCategory
    }

    /// La composition attendue — l'oracle SDK relu AU MOMENT DE L'APPEL (le pont Simulator
    /// fait vivre l'inventaire : la comparaison porte sur deux lectures VIVANTES, jamais sur
    /// un instantané gelé). Miroir exact de la composition du source production.
    func expectedDeviceFacts() -> [(name: String?, kind: InputDeviceKind, isExtended: Bool)] {
        let facts = realControllerFacts()
        return facts.controllers.map { controller in
            (sdkName(ofController: controller), InputDeviceKind.other,
             controller.extendedGamepad != nil)
        }
        + [facts.keyboard].compactMap { keyboard in
            keyboard.map { keyboardIn in
                (keyboardIn.productCategory as String?, InputDeviceKind.keyboard, false)
            }
        }
        + facts.mice.map { mouse in
            (mouse.productCategory as String?, InputDeviceKind.mouse, false)
        }
    }

    /// Le miroir tient-il ENTRE deux lectures vivantes ? — `true` ssi les devices publiés
    /// recollent EXACTEMENT à l'oracle (composition nom+kind en multiset, gamepads = les
    /// tokens à profil étendu SEULEMENT, noms compris). La fenêtre d'équivalence couvre le
    /// transit du pont (connect notification → relecture du port) ; une fabrication ou une
    /// omission échoue À CHAQUE lecture de la fenêtre, jamais rattrapée.
    func mirrorHolds(_ inventory: DeviceInventoryEnumerated) -> Bool {
        let expected = expectedDeviceFacts()
        guard inventory.devices.count == expected.count else { return false }
        guard inventory.gamepads.count == expected.filter({ $0.isExtended }).count else { return false }
        var unmatched = expected
        for device in inventory.devices {
            let descriptor = device.descriptor
            let match = unmatched.firstIndex {
                $0.name != nil && $0.name == descriptor.name && $0.kind == descriptor.kind
            } ?? unmatched.firstIndex { $0.name == nil && descriptor.name == nil && $0.kind == descriptor.kind }
            guard let match = match else { return false }
            unmatched.remove(at: match)
        }
        guard unmatched.isEmpty else { return false }
        let expectedExtendedNames = Set(expected.filter({ $0.isExtended }).compactMap { $0.name })
        return Set(inventory.gamepads.compactMap { ($0.state.value as? GamepadSnapshot)?.descriptor.name })
            == expectedExtendedNames
    }

    // MARK: - Scénarios

    /// uikit-devices-inventory-empty-honest — contrat d'acceptation amendé : l'inventaire
    /// publié est EXACTEMENT l'oracle SDK relu au moment de l'assertion (`GCController`
    /// enumeration + clavier/souris GameController) : UN device publié par token réel
    /// (composition, noms et kinds identiques — clavier coalescé → Keyboard, souris GC → Mouse, tout jeton
    /// `GCController` → `Other` côté générique : la manette vit au port manette, le carve-out
    /// du ruling), et gamepads = les tokens à profil étendu SEULEMENT. La forme vide est la
    /// référence sans périphérique (le nom du scénario — épinglée partout où la réalité est
    /// zéro) et c'est ce que ce scénario prouve sur un Simulator sans contrôleur ; sur un hôte
    /// dont les devices sont pontés par le Simulator, le MÊME miroir tient sur la réalité non
    /// vide — aucune fabrication au-delà de ce que le système rapporte, un device fabriqué ou
    /// omis échoue SUR TOUTE machine. La fenêtre d'équivalence (deux lectures VIVANTES comparées, bornée) absorbe le transit du
    /// pont — jamais une divergence de fond.
    func test_uikit_devices_inventory_empty_honest() throws {
        let (fixture, _, scope) = try attachedScope()

        // La fenêtre d'équivalence : le miroir complet (composition nom+kind, gamepads étendus
        // seuls) est comparé à des lectures fraîches des DEUX côtés jusqu'à concordance —
        // l'échec final porte le dernier écart nommé, fait par fait.
        let deadline = Date().addingTimeInterval(3.0)
        var lastMismatch = "the inventory never became enumerated"
        while Date() < deadline {
            let state = scope.devices.state.value as? DeviceManagerState
            if let inventory = (state?.inventory as? DeviceInventoryEnumerated) {
                if mirrorHolds(inventory) { lastMismatch = "" ; break }
                let expected = expectedDeviceFacts()
                lastMismatch = "published (devices=\(inventory.devices.map { "\($0.descriptor.name ?? "nil"):\($0.descriptor.kind)" }), "
                    + "gamepads=\(inventory.gamepads.compactMap { ($0.state.value as? GamepadSnapshot)?.descriptor.name })) "
                    + "vs SDK (devices=\(expected.map { "\($0.name ?? "nil"):\($0.kind)" }), "
                    + "extended=\(expected.filter { $0.isExtended }.map { $0.name ?? "nil" }))"
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        XCTAssertEqual(lastMismatch, "",
                       "the device inventory must mirror the REAL controller facts exactly — never a fabrication, never an omission: \(lastMismatch)")

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-gamepads-empty-honest — contrat d'acceptation amendé : la liste manette est TENUE
    /// DANS LE TEMPS (fenêtre d'observation bornée) à l'identité SDK du profil étendu :
    /// compteur ET noms comparés à l'oracle RELU à chaque passe (deux lectures vivantes). Sans manette, l'énoncé EST le
    /// vide que le nom du scénario épine (zéro, jamais fabriquée) et c'est ce que ce scénario
    /// prouve sur un Simulator sans contrôleur ; sur l'hôte ponté, l'identité tient sur la
    /// réalité non vide — aucune fabrication au-delà de ce que le système rapporte, toute
    /// divergence (fabrication, disparition, renommage, micro publiée) échoue.
    func test_uikit_gamepads_empty_honest() throws {
        let (fixture, _, scope) = try attachedScope()
        _ = try awaitDeviceState(scope, "enumerated device inventory", isEnumerated)

        // L'énoncé tient dans le temps : sur la fenêtre d'observation, l'inventaire reste
        // exactement ce que le système énonce — jamais une manette inventée, jamais perdue.
        // Chaque passe compare DEUX lectures vivantes (l'oracle est relu à chaque passe) ;
        // l'échec final porte la dernière divergence nommée.
        let deadline = Date().addingTimeInterval(1.0)
        var everHeld = false
        var lastMismatch = ""
        while Date() < deadline {
            let state = try XCTUnwrap(scope.devices.state.value as? DeviceManagerState)
            let inventory = try XCTUnwrap(state.inventory as? DeviceInventoryEnumerated)
            let extended = ((GCController.controllers() as? [GCController]) ?? [])
                .filter { $0.extendedGamepad != nil }
            let extendedNames = Set(extended.compactMap { sdkName(ofController: $0) })
            let publishedNames = Set(inventory.gamepads.compactMap { ($0.state.value as? GamepadSnapshot)?.descriptor.name })
            if inventory.gamepads.count == extended.count && publishedNames == extendedNames {
                everHeld = true
            } else {
                lastMismatch = "published=\(publishedNames.sorted()) vs SDK extended=\(extendedNames.sorted())"
            }
            pump(0.1)
        }
        XCTAssertTrue(everHeld,
                      "the gamepad list must mirror the system's extended-profile facts over the window: \(lastMismatch)")

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-raw-input-noncall — la preuve non-appel : la capability publiée refuse AVANT
    /// l'appel (`rawInput = Unsupported(RawInputAccess)`), et la demande répond le refus
    /// taxonomique exact — l'opération Nommée par la taxonomie (identité d'échec, jamais un
    /// message), aucune primitive native atteinte.
    func test_uikit_raw_input_noncall() throws {
        let (fixture, _, _) = try attachedScope()
        let surface = try XCTUnwrap(fixture.primarySurface, "the session published its primary surface")
        let input = surface.input

        // Avant tout appel : la capability publiée ne promet jamais l'accès brut.
        let state = try XCTUnwrap(input.state.value as? SurfaceInputState)
        let refusalCapability = try XCTUnwrap(
            state.capabilities.rawInput as? CapabilityUnsupported,
            "the published rawInput capability is the Unsupported refusal, got \(state.capabilities.rawInput)")
        XCTAssertEqual(refusalCapability.failure.operation, KadreOperation.rawinputaccess,
                       "the refusal names EXACTLY the raw input operation")

        // L'appel : le refus taxonomique exact — la même identité d'échec.
        let requestDone = expectation(description: "requestRawInput completed")
        var requestFailure: (any KadreFailure)?
        var requestSucceeded = false
        input.requestRawInput { result, error in
            XCTAssertNil(error, "requestRawInput completion error")
            if let failure = (result as? KadreResultFailure)?.reason {
                requestFailure = failure
            } else if result is KadreResultSuccess<AnyObject> {
                requestSucceeded = true
            }
            requestDone.fulfill()
        }
        wait(for: [requestDone], timeout: 10)

        XCTAssertFalse(requestSucceeded, "the raw input request must never be granted on iOS/tvOS")
        let failure = try XCTUnwrap(requestFailure, "the request must answer the typed refusal")
        let unsupported = try XCTUnwrap(failure as? KadreFailureUnsupported,
                                        "expected Unsupported(RawInputAccess), got \(failure)")
        XCTAssertEqual(unsupported.operation, KadreOperation.rawinputaccess,
                       "the raw input request is refused for the exact operation it names")

        try stopAndAwaitTermination(fixture)
    }
}
