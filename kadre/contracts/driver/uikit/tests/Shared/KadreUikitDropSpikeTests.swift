import XCTest
import UIKit

// SPIKE phase 7 Task 1 (placeholder du plan, jetable en Task 4) — la FAUSSE de session drop du
// driver, la route ÉPINGLÉE par le spike : le conformer Kotlin de `UIDropSessionProtocol` compile
// (preuve au rapport Task 1, `UIKitDropSessionIos.kt`), mais une classe `internal` Kotlin ne
// traverse PAS l'en-tête du framework vers le driver Swift — les tests driver (Task 4) pilotent
// donc leurs drops avec une classe Swift qui ment comme la plateforme : un `NSObject` conforme au
// protocole `UIDropSession`, passable au hook `dropInteraction:canHandleSession:` du delegate.
//
// PLACEHOLDer : ce fichier prouve SEULEMENT que la fausse se construit et répond — la preuve
// COMPORTEMENTALE (drop stimulus → delegate → port) est la Task 4. Il est AUTONOME (aucun import
// KadreUikit) : il compile même sans le binding réel de la Task 3, et le driver scheme reste
// donc vert.
//
// La garde `os(iOS)` est la frontière de plateforme TELLE QUELLE : `UIDropSession` et
// `UIDropInteraction` sont `API_UNAVAILABLE(tvos)` (UIDragSession.h:19 — la même absence que le
// klib tvOS côté Kotlin, 0 occurrence vérifiée) ; `canImport(UIKit)` est VRAI sur tvOS et ne
// garde donc RIEN ici — `tests/Shared` compile aussi dans KadreUikitDriverTvOsTests, et ce
// fichier doit y rester invisible.
#if os(iOS)

/// La fausse de session drop du driver : chaque membre requis du protocole `UIDropSession`
/// (UIDragSession.h — les exigences `@required` : `items`, `locationInView:`,
/// `allowsMoveOperation`, `isRestrictedToDraggingApplication`, `hasItemsConformingToTypeIdentifiers:`,
/// `canLoadObjectsOfClass:`, plus `localDragSession`, `progressIndicatorStyle`,
/// `loadObjectsOfClass:completion:`, `progress` de `NSProgressReporting`), rendu fixe et
/// honnête : les types que l'init a reçus sont les seuls auxquels la fausse répond vrai.
final class FakeDropSession: NSObject, UIDropSession {

    private let conformingTypes: [String]

    /// Une fausse portée par ses types déclarés — un drop vide est une fausse sans type.
    init(conformingTypes: [String] = []) {
        self.conformingTypes = conformingTypes
        super.init()
    }

    var items: [UIDragItem] {
        // Un item RÉEL (provider d'une chaîne UTF-8) : la Task 4 lira le payload par ici.
        [UIDragItem(itemProvider: NSItemProvider(object: "kadre-drop-spike" as NSString))]
    }

    func location(in view: UIView) -> CGPoint { .zero }

    var allowsMoveOperation: Bool { false }

    var isRestrictedToDraggingApplication: Bool { false }

    func hasItemsConforming(toTypeIdentifiers typeIdentifiers: [String]) -> Bool {
        typeIdentifiers.contains { conformingTypes.contains($0) }
    }

    func canLoadObjects(ofClass aClass: NSItemProviderReading.Type) -> Bool { false }

    var localDragSession: UIDragSession? { nil }

    var progressIndicatorStyle: UIDropSessionProgressIndicatorStyle = .none

    func loadObjects(ofClass aClass: NSItemProviderReading.Type,
                     completion: @escaping ([NSItemProviderReading]) -> Void) -> Progress {
        // L'exigence BRUTE importée d'Obj-C : `(any NSItemProviderReading.Type, @escaping
        // ([any NSItemProviderReading]) -> Void) -> Progress` (la variante générique
        // `loadObjects<T>` est un overlay Swift, pas l'exigence du protocole).
        Progress()
    }

    var progress: Progress { Progress() }
}

/// Le placeholder du spike : la fausse se construit, répond à l'adhérence par ses types, et
/// transporte un item réel — le sol que la Task 4 piétinera d'événements.
final class KadreUikitDropSpikeTests: XCTestCase {

    func testFakeDropSessionConstructionSpike() {
        let session = FakeDropSession(conformingTypes: ["public.utf8-plain-text"])
        XCTAssertTrue(session.hasItemsConforming(toTypeIdentifiers: ["public.utf8-plain-text"]),
                      "the fake must answer conformance for the types it was built with")
        XCTAssertFalse(session.hasItemsConforming(toTypeIdentifiers: ["public.url"]),
                       "the fake must refuse the types it was not built with")
        XCTAssertEqual(session.items.count, 1, "the fake carries exactly one real drag item")
        XCTAssertEqual(session.items.first?.itemProvider.registeredTypeIdentifiers.first,
                       "public.utf8-plain-text",
                       "the drag item's provider registers its string as utf8 plain text")
        XCTAssertFalse(session.allowsMoveOperation)
        XCTAssertNil(session.localDragSession)
        NSLog("KADRE-SPIKE FakeDropSession constructed: items=%d conforming(utf8)=true "
            + "registeredTypes=%@", session.items.count,
              session.items.first?.itemProvider.registeredTypeIdentifiers ?? [])
    }
}

#endif
