import XCTest
import UIKit
import GameController

// Les probes Swift de la spike phase 8 (Task 1) — le miroir DRIVER des probes Kotlin
// (`UIKitPhase8SpikeIos`/`UIKitPhase8SpikeTvos`) : ce fichier est compilé dans les DEUX
// familles de tests (tests/Shared), ne dépend d'aucun framework Kadre, et prouve côté Swift
// ce que les ports des Tasks 2-4 liront. Décision de pont : consommé ou supprimé par les
// tasks suivantes (le devoir delete-or-consume de la spike).
//
// Quatre mesures, une par décision de la spike (`task1-spike-report.md`) :
// 1. l'énumération des contrôleurs est VIDE sur Simulator (aucune manette ne ponte depuis le
//    Mac hôte) — l'inventaire honnête que les Task 2-4 énoncent tel quel ;
// 2. UIScreen énumère au moins un écran et les constantes de notification (memory-warning,
//    DidConnect/DidDisconnect/ModeDidChange) se résolvent — le chemin d'abonnement des ports ;
// 3. les symboles du chemin haptique (`GCHapticsLocality`, la classe GCDeviceHaptics — la
//    création est controller-owned : `controller.haptics`, jamais un createLocally libre) ;
// 4. (iOS seul) `preferredMode`/`availableModes` se résolvent — iOS-only, le klib tvOS les
//    ignore (0-hit) : l'écran unique tvOS n'a qu'un mode.
final class KadreUikitPhase8SpikeTests: XCTestCase {

    /// Décision 3 : `GCController.controllers()` sur Simulator — vide, l'inventaire honnête.
    func test_gccontroller_inventory_is_empty_on_simulator() {
        let controllers = GCController.controllers()
        XCTAssertTrue(
            controllers.isEmpty,
            "sur Simulator, GCController.controllers() doit être vide (trouvé \(controllers.count))",
        )
    }

    /// Décision 2 : l'inventaire UIScreen et les constantes de notification — les symboles
    /// que l'abonnement NSNotificationCenter des ports display consommera.
    func test_uiscreen_inventory_and_notification_constants_resolve() {
        XCTAssertGreaterThanOrEqual(UIScreen.screens.count, 1, "un Simulator a toujours au moins un écran")
        XCTAssertFalse(
            UIApplication.didReceiveMemoryWarningNotification.rawValue.isEmpty,
            "la constante memory-warning (le canal du mapping mémoire, Task 5) se résout",
        )
        XCTAssertFalse(UIScreen.didConnectNotification.rawValue.isEmpty)
        XCTAssertFalse(UIScreen.didDisconnectNotification.rawValue.isEmpty)
        XCTAssertFalse(UIScreen.modeDidChangeNotification.rawValue.isEmpty)
    }

    /// Décision 1 : les symboles du chemin haptique — la localité et la classe GCDeviceHaptics
    /// (sans device réel : la création est controller-owned, il n'existe pas de GCDeviceHaptics
    /// libre ; le klib Kotlin ne porte d'ailleurs aucun createLocally*).
    func test_haptics_creation_path_symbols_resolve() {
        XCTAssertEqual(GCHapticsLocality.default, GCHapticsLocality.default)
        XCTAssertNotNil(
            NSClassFromString("GCDeviceHaptics"),
            "la classe GCDeviceHaptics existe au runtime GameController",
        )
    }

    #if os(iOS)
    /// Décision 4 (iOS seul) : les modes d'écran — `availableModes`/`preferredMode` existent
    /// côté iOS (l'écran AirPlay externe peut en porter plusieurs) ; tvOS est amputé (0-hit
    /// klib, cf. le probe Kotlin tvosMain).
    func test_screen_modes_resolve_on_ios() {
        let screen = try! XCTUnwrap(UIScreen.screens.first)
        XCTAssertFalse(screen.availableModes.isEmpty, "un écran iOS expose au moins un mode")
        let preferred = screen.preferredMode
        if let preferred = preferred {
            XCTAssertTrue(screen.availableModes.contains(preferred), "le mode préféré est un mode disponible")
        }
    }
    #endif
}
