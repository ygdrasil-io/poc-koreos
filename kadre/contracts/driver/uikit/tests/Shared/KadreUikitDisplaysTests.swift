import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Preuves phase 8 (Task 6) — l'inventaire display RÉEL du Simulator conduit contre la session
/// VIVANTE, les deux familles (tests/Shared compile dans les deux targets de test). Noms de
/// scénarios FIGÉS pour Task 7 (corrélation JUnit — la résolution phase 7 : les ids hyphénés du
/// registre ne sont PAS des noms de méthode, la méthode est le snake_case `test_uikit_*`) :
///
///   uikit-display-inventory-real      → test_uikit_display_inventory_real()
///   uikit-display-notifications-wired → test_uikit_display_notifications_wired()
///   uikit-display-offmain-access      → test_uikit_display_offmain_access()
///
/// FAITS ÉPELÉS PAR SCÉNARIO :
///
/// 1. INVENTAIRE RÉEL (uikit-display-inventory-real) — l'inventaire publié reflète les écrans
///    RÉELS du Simulator : chaque fait est recollé à la lecture SDK du scénario lui-même
///    (`UIScreen.screens`/`bounds`/`scale`, la fenêtre de la session porte SON écran) — type
///    HostViewport pour L'ÉCRAN DE LA FENÊTRE du host (le ruling de la couture), Physical pour
///    tout autre écran (AirPlay — aucun sur Simulator), bounds = géométrie physique à l'origine
///    (points × échelle, arrondi), échelle = l'échelle UIScreen, name null (le klib n'expose
///    AUCUN nom — honest null), workArea null TOUJOURS (iOS/tvOS n'ont pas de bureau — jamais
///    une géométrie inventée), UN mode par écran portant la même taille physique, refresh et
///    profondeur honnêtement null. tvOS : l'unique écran EST l'écran de la fenêtre — un seul
///    HostViewport. `requestAccess` répond le succès portant le même inventaire (la voie
///    applicative, appelée DEPUIS le main — la branche inline du funnel).
/// 2. NOTIFICATIONS WIRED (uikit-display-notifications-wired) — la sonde STRUCTURALE (compte
///    d'observateurs, l'option du brief — AUCUNE revendication de livraison sans matériel) : le
///    source production pose TROIS tokens `NSNotificationCenter` à l'attach
///    (`UIScreenDidConnect/DidDisconnect/ModeDidChange` — le fait code de sa table), les retire
///    exactement au teardown de la session (le retrait exact des MÊMES tokens), et l'install
///    publie l'inventaire initial (le contrat de publication synchrone du port). Les livraisons
///    réelles des notifications (écran branché/débranché) restent le charter matériel.
/// 3. OFF-MAIN (uikit-display-offmain-access) — l'OBLIGATION TRANSPORTÉE de la revue Task 2
///    (Important-1) : `UIScreen` est main-thread-only, la voie applicative `requestAccess`
///    funnelle ses lectures sur le main (`Dispatchers.Main.immediate`, le patron du bail
///    `UIKitWindowCommandPort.lease`) — INPINNABLE en uikitTest (le runner KN y tourne sur le
///    main et ne draine jamais la file), la preuve est ICI, sur un hôte à run loop : la demande
///    part d'un thread NON-main (`DispatchQueue.global`), le run loop du test draine la file
///    principale, la réponse porte l'inventaire RÉEL — et le compteur du driver prouve la
///    CONFINEMENT : zéro lecture du seam hors main (l'épingle discriminante — sans funnel, la
///    lecture hors main incrémenterait `displaySeamReadOffMainCount` et CE scénario seul
///    échouerait).
final class KadreUikitDisplaysTests: KadreUikitPhase8FixtureCase {

    /// L'état du manager display dans un état attendu — attente bornée, pumping du run loop
    /// (la publication initiale de l'install est synchrone, mais l'export Swift du StateFlow
    /// est relu à chaque passe), retourne la valeur capturée.
    func awaitDisplayState(
        _ scope: any KadreScope,
        _ what: String,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (DisplayManagerState) -> Bool,
    ) throws -> DisplayManagerState? {
        let deadline = Date().addingTimeInterval(timeout)
        var last: DisplayManagerState?
        while true {
            if let state = scope.displays.state.value as? DisplayManagerState {
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

    /// Le prédiat « inventaire énuméré ».
    func isEnumerated(_ state: DisplayManagerState) -> Bool {
        state.inventory is DisplayInventoryEnumerated
    }

    /// La lecture SDK du scénario — la géométrie physique attendue d'un écran (points ×
    /// échelle, arrondi vers +∞ : le même arrondi que le kernel).
    func expectedPhysicalSize(of screen: UIScreen) -> (width: Int32, height: Int32) {
        let size = screen.bounds.size
        return (Int32((size.width * screen.scale).rounded(.up)),
                Int32((size.height * screen.scale).rounded(.up)))
    }

    // MARK: - Scénarios

    /// uikit-display-inventory-real — l'inventaire publié reflète les écrans RÉELS du
    /// Simulator, fait par fait recollé à la lecture SDK du scénario : un display par écran
    /// `UIScreen.screens`, l'écran de LA fenêtre du host en HostViewport (le seul), les autres
    /// Physical, bounds physiques à l'origine, échelle SDK, name null (le klib n'expose aucun
    /// nom d'écran), workArea null (jamais une géométrie de bureau inventée), UN mode par
    /// écran, refresh/profondeur honnêtement null, le mode courant membre des modes. tvOS :
    /// l'unique écran, un seul HostViewport. La capability d'énumération est Supported
    /// Available, et `requestAccess` (voie applicative DEPUIS le main — branche inline du
    /// funnel) répond le succès portant le même inventaire réel.
    func test_uikit_display_inventory_real() throws {
        let (fixture, _, scope) = try attachedScope()
        let window = try XCTUnwrap(fixture.window)
        let hostScreen = try XCTUnwrap(window.screen, "the host window carries its own screen")

        // La lecture SDK indépendante — l'oracle du scénario, au moment de l'assertion.
        let screens = UIScreen.screens
        XCTAssertFalse(screens.isEmpty, "the simulator process has at least one screen")
        #if os(tvOS)
        XCTAssertEqual(screens.count, 1, "tvOS is a single-screen system: exactly one UIScreen")
        #endif

        let state = try XCTUnwrap(try awaitDisplayState(scope, "enumerated inventory", isEnumerated))
        let inventory = try XCTUnwrap(state.inventory as? DisplayInventoryEnumerated)
        XCTAssertEqual(inventory.displays.count, screens.count,
                       "one published display per real UIScreen — never a fabricated one")
        let enumeration = try XCTUnwrap(
            state.capabilities.enumeration as? CapabilitySupported<AnyObject>,
            "the enumeration capability is Supported, got \(state.capabilities.enumeration)")
        XCTAssertTrue(enumeration.availability is FeatureAvailabilityAvailable,
                      "the UIScreen inventory is structurally observable on iOS/tvOS, got "
                          + "\(enumeration.availability)")

        // Chaque display publié est recollé à SON écran SDK, fait par fait.
        var hostViewports: [any Display] = []
        for (index, display) in inventory.displays.enumerated() {
            let screen = try XCTUnwrap(screens[safe: index],
                                       "the published inventory mirrors UIScreen.screens order")
            let displayState = try XCTUnwrap(display.state.value as? DisplayState)
            let expected = expectedPhysicalSize(of: screen)
            XCTAssertEqual(displayState.type, screen.isEqual(hostScreen) ? DisplayType.hostviewport
                                                                         : DisplayType.physical,
                           "the host window's screen is THE HostViewport, every other screen is Physical")
            XCTAssertEqual(displayState.connection, DisplayConnectionState.connected,
                           "an enumerated screen is connected")
            XCTAssertNil(displayState.name,
                         "the shared klib exposes NO screen name — an honest null, never manufactured")
            XCTAssertEqual(displayState.bounds.origin.x, 0, "the bounds sit at the origin — no desktop position")
            XCTAssertEqual(displayState.bounds.origin.y, 0, "the bounds sit at the origin — no desktop position")
            XCTAssertEqual(displayState.bounds.size.width, expected.width,
                           "the bounds are the physical width of the screen (points × scale, rounded)")
            XCTAssertEqual(displayState.bounds.size.height, expected.height,
                           "the bounds are the physical height of the screen (points × scale, rounded)")
            XCTAssertNil(displayState.workArea,
                         "iOS/tvOS have no desktop: the work area does not exist and is never invented")
            XCTAssertEqual(displayState.scaleFactor, screen.scale, accuracy: 1e-9,
                           "the scale factor is the observed UIScreen scale")
            XCTAssertEqual(displayState.modes.count, 1,
                           "one mode per screen — the shared klib carries a single mode")
            let mode = try XCTUnwrap(displayState.modes.first)
            XCTAssertEqual(mode.physicalSize.width, expected.width, "the mode carries the screen's physical size")
            XCTAssertEqual(mode.physicalSize.height, expected.height, "the mode carries the screen's physical size")
            XCTAssertNil(mode.refreshRateHz, "nothing mode-proven in the klib: the refresh is an honest null")
            XCTAssertNil(mode.bitDepth, "nothing mode-proven in the klib: the depth is an honest null")
            let current = try XCTUnwrap(displayState.currentMode, "the single mode is the current one")
            XCTAssertTrue(current.isEqual(mode), "the current mode belongs to the published modes")

            if screen.isEqual(hostScreen) {
                hostViewports.append(display)
            }
        }

        // EXACTEMENT l'écran de la scène du host en HostViewport — le ruling de la couture.
        XCTAssertEqual(hostViewports.count, 1,
                       "exactly ONE HostViewport: the screen of the session's own window")
        let primary = try XCTUnwrap(inventory.primary, "the host viewport is the primary display")
        let hostViewport = try XCTUnwrap(hostViewports.first, "the host viewport must exist")
        XCTAssertTrue((primary as AnyObject) === (hostViewport as AnyObject),
                      "the primary is the host viewport object itself")

        // requestAccess (voie applicative, DEPUIS le main — la branche inline du funnel)
        // répond le succès portant le même inventaire réel, toujours sans workArea inventée.
        let accessDone = expectation(description: "requestAccess completed on the main branch")
        var accessState: DisplayManagerState?
        var accessFailure: (any KadreFailure)?
        scope.displays.requestAccess { result, error in
            XCTAssertNil(error, "requestAccess completion error")
            if let failure = (result as? KadreResultFailure)?.reason {
                accessFailure = failure
            } else if let success = result as? KadreResultSuccess<AnyObject> {
                accessState = success.value as? DisplayManagerState
            }
            accessDone.fulfill()
        }
        wait(for: [accessDone], timeout: 10)
        XCTAssertNil(accessFailure, "requestAccess must succeed on a live session")
        let accessInventory = try XCTUnwrap(
            (accessState?.inventory as? DisplayInventoryEnumerated),
            "requestAccess carries the enumerated inventory, got \(String(describing: accessState))")
        XCTAssertEqual(accessInventory.displays.count, screens.count,
                       "requestAccess re-reads the same real screens")
        let accessPrimary = try XCTUnwrap(accessInventory.primary)
        let accessPrimaryState = try XCTUnwrap(accessPrimary.state.value as? DisplayState)
        XCTAssertEqual(accessPrimaryState.type, DisplayType.hostviewport,
                       "the access snapshot still rules the session's window screen as HostViewport")
        XCTAssertNil(accessPrimaryState.workArea,
                     "requestAccess carries the same honesty: no invented work area")

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-display-notifications-wired — la sonde STRUCTURALE (compte d'observateurs) : à
    /// l'attach, le source production pose UNE registration portant EXACTEMENT TROIS tokens
    /// (connect/disconnect/modeDidChange — la table du code) et l'install publie l'inventaire
    /// initial ; au teardown de la session, la registration est retirée (le retrait exact des
    /// MÊMES trois tokens) — rien ne fuit. AUCUNE revendication de livraison : les fires
    /// réels (écran branché/débranché) sont le charter matériel.
    func test_uikit_display_notifications_wired() throws {
        let installsBefore = driver.displayListenerInstallCount
        let teardownsBefore = driver.displayListenerTeardownCount
        let tokensInstalledBefore = driver.displayNotificationTokensInstalled
        let tokensRemovedBefore = driver.displayNotificationTokensRemoved

        let (fixture, _, scope) = try attachedScope()

        // L'install vivant : UNE registration, TROIS tokens posés, l'inventaire initial publié.
        XCTAssertEqual(driver.displayListenerInstallCount, installsBefore + 1,
                       "the attach installs exactly one display listener registration")
        XCTAssertEqual(driver.displayNotificationTokensInstalled, tokensInstalledBefore + 3,
                       "the registration carries exactly the three UIScreen notification tokens")
        _ = try awaitDisplayState(scope, "install published the initial inventory", isEnumerated)

        try stopAndAwaitTermination(fixture)

        // Le teardown : le retrait EXACT — la registration quitte, les trois tokens avec elle.
        XCTAssertEqual(driver.displayListenerTeardownCount, teardownsBefore + 1,
                       "the session teardown removes the display listener registration")
        XCTAssertEqual(driver.displayNotificationTokensRemoved, tokensRemovedBefore + 3,
                       "exactly the three tokens posed by the install are removed")
        XCTAssertEqual(driver.displayListenerInstallCount, driver.displayListenerTeardownCount,
                       "no display registration leaks past the session close")
    }

    /// uikit-display-offmain-access — l'OBLIGATION TRANSPORTÉE T5→T6 : la voie applicative
    /// `requestAccess` appelée d'un thread NON-main FUNNELLE ses lectures `UIScreen` sur le
    /// thread principal (`Dispatchers.Main.immediate`) et RÉPOND quand même — le run loop du
    /// test (l'hôte à run loop du driver) draine la file principale pendant l'attente, donc
    /// ni la demande ni le test ne se bloquent mutuellement. La réponse porte l'inventaire
    /// RÉEL, et le compteur du driver épingle la confinement : ZÉRO lecture du seam hors main
    /// (sans funnel, la lecture hors main incrémenterait `displaySeamReadOffMainCount` — ce
    /// scénario seul échouerait).
    func test_uikit_display_offmain_access() throws {
        let (fixture, _, scope) = try attachedScope()
        let readsBefore = driver.displaySeamReadCount
        let offMainReadsBefore = driver.displaySeamReadOffMainCount

        // La demande part d'un thread NON-main — le completion handler porte le verdict.
        let accessDone = expectation(description: "off-main requestAccess completed")
        var accessState: DisplayManagerState?
        var accessFailure: (any KadreFailure)?
        DispatchQueue.global(qos: .userInitiated).async {
            scope.displays.requestAccess { result, error in
                XCTAssertNil(error, "requestAccess completion error")
                if let failure = (result as? KadreResultFailure)?.reason {
                    accessFailure = failure
                } else if let success = result as? KadreResultSuccess<AnyObject> {
                    accessState = success.value as? DisplayManagerState
                }
                accessDone.fulfill()
            }
        }
        // L'attente EST le drain : `wait(for:)` pompe le run loop principal, qui exécute les
        // blocks du funnel dispatchés sur la file main — sans ce drainage mutuel, la demande
        // ne répondrait jamais (le deadlock que le funnel doit prouver absent).
        wait(for: [accessDone], timeout: 10)

        XCTAssertNil(accessFailure,
                     "the off-main request must complete through the main funnel, got \(String(describing: accessFailure))")
        let inventory = try XCTUnwrap(accessState?.inventory as? DisplayInventoryEnumerated,
                                      "the off-main access carries the enumerated inventory")
        XCTAssertGreaterThanOrEqual(inventory.displays.count, 1,
                                    "the off-main access carries the REAL screens of the process")
        let primary = try XCTUnwrap(inventory.primary)
        let primaryState = try XCTUnwrap(primary.state.value as? DisplayState)
        XCTAssertEqual(primaryState.type, DisplayType.hostviewport,
                       "the off-main access reads the same host viewport ruling")
        XCTAssertEqual(primaryState.scaleFactor, UIScreen.main.scale, accuracy: 1e-9,
                       "the off-main access reads the REAL screen scale (executed on main)")

        // L'épingle de confinement : TOUTES les lectures du seam sont passées par le main.
        XCTAssertGreaterThanOrEqual(driver.displaySeamReadCount, readsBefore + 1,
                                    "the access really read the seam (never answered from a cache)")
        XCTAssertEqual(driver.displaySeamReadOffMainCount, offMainReadsBefore,
                       "every UIScreen read was main-confined — the funnel executed the reads "
                       + "on the main thread, never on the calling thread")

        try stopAndAwaitTermination(fixture)
    }
}

/// L'accès borné d'un index de tableau — l'ordre d'énumération publié reflète
/// `UIScreen.screens` ; un décalage est un échec NOMMÉ, jamais un crash.
private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
