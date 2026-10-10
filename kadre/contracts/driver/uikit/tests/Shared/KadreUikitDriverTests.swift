import XCTest
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

final class KadreUikitDriverTests: XCTestCase {

    private func fixture() throws -> DriverFixture {
        let f = DriverFixture.shared
        try f.waitUntilReady()
        return f
    }

    /// BCK-012 / uikit-driver-observes-real-window — la sonde Kotlin lit les MÊMES valeurs que Swift.
    func testKotlinProbeObservesRealWindow() throws {
        let f = try fixture()
        let window = try XCTUnwrap(f.window)
        let view = try XCTUnwrap(f.observedView)

        let observation = KadreUikitProbe.shared.observe(window: window, view: view)

        XCTAssertEqual(observation.windowMembership, .attached)
        XCTAssertEqual(observation.sceneConnected, window.windowScene != nil)
        XCTAssertEqual(observation.viewBoundsWidth, Double(view.bounds.width), accuracy: 0.001)
        XCTAssertEqual(observation.viewBoundsHeight, Double(view.bounds.height), accuracy: 0.001)
        XCTAssertEqual(observation.displayScale, Double(view.traitCollection.displayScale), accuracy: 0.001)
    }

    /// BCK-012 / uikit-driver-reports-detached-view — une vue hors fenêtre est rapportée Detached.
    func testProbeReportsViewOutsideWindowAsDetached() throws {
        let f = try fixture()
        let window = try XCTUnwrap(f.window)
        let detached = try XCTUnwrap(f.detachedView)

        let observation = KadreUikitProbe.shared.observe(window: window, view: detached)

        XCTAssertEqual(observation.windowMembership, .detached)
    }
}
