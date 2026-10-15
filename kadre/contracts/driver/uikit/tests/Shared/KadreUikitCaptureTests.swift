import XCTest
import UIKit
import ReplayKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// Scénarios phase 9 CAPTURE du driver UIKit/tvOS — la preuve O3 de la couture capture sur
/// les DEUX familles, par la SURFACE PUBLIQUE du consommateur (`KadreScope.capture`, le même
/// accès qu'Android ; le round-trip uikitTest de Task 5 porte la version factice des seams,
/// ICI c'est le port RÉEL `UIKitReplayKitCaptureSource` + `UIKitLiveSurfaceSnapshotSource`
/// derrière le vrai runtime). Noms de scénarios FIGÉS (`test_uikit_capture_*` → ids
/// `uikit-capture-*`), miroir de la convention drop/driver.
///
/// LES FAITS QUI BORNENT L'HONNÊTETÉ DES SCÉNARIOS (le spike T1 + les rulings T3/T5) :
/// 1. **Simulator NE livre PAS de frames ReplayKit** (SPIKE-FACT T3 : `isAvailable == true`
///    mais la complétion du start ne revient JAMAIS, stop -5829) — AUCUN scénario ne prétend
///    des frames HostChoice ici ; le bras HostChoice y prouve son plan de CONTRÔLE et ses
///    refus honnêtes seulement. Les frames ReplayKit restent le charter DEVICE.
/// 2. **L'app hôte du driver rend des rasters COHÉRENTS** quand la vue capturée porte du
///    contenu RENDU (mesuré Task 6 sur la vue RÉELLE du fixture attachée : layer.render →
///    bitmapInfo 0x1, bytesPerRow = 4·w → rgba8). FAIT NOUVEAU MESURÉ : la vue VIDE du
///    fixture rend un raster MENTEUR (layer.render : bitmapInfo 0x1 — in-table — mais
///    bytesPerRow = 2·w ; drawHierarchy : 0x1101 hors table) — l'honnêteté du kernel
///    (`bytesPerRow ≥ 4·w`) répond alors le terminal `frame-format-out-of-table`, refus
///    CORRECT. Les scénarios de flux posent donc un STIMULUS DE CONTENU (background + layout)
///    avant l'ouverture — la capture d'une vue vide est de toute façon dégénérée.
/// 3. **`open(HostChoice)` est INATTEIGNABLE du consommateur** (ruling T5) : l'admission du
///    runtime exige `hostPicker == Available` — structurellement `Unsupported` sur
///    uikit/tvOS — donc le refus `Unsupported(CaptureOpen)` part AVANT toute réservation de
///    port (zéro appel de seam) ; `open(Source(...))` répond la forme consommatrice
///    `InvalidRequest("request.target")` (inventaire honnêtement indisponible) — la cible
///    Source passe par la sonde `captureSourceRefusalProbe` (`CaptureSourceId` est opaque
///    hors du module Kotlin — Swift ne peut pas le nommer, la sonde copie les faits, la
///    même discipline que les probes de bail). La surface est la SEULE cible ouvrable
///    publiquement.
///
/// MÉCANIQUE (précédents phases 5-8) : bundle.unit-test in-process, attach RÉEL via le
/// fixture, pompe du run loop JAMAIS de sommeil nu, compteurs de capture sur
/// `KadreUikitSurfaceDriver.shared` (deltas — les compteurs sont process-wide), runs
/// gradle-gated (`scripts/test-uikit-simulator.sh`).
final class KadreUikitCaptureTests: KadreUikitPhase8FixtureCase {

    // MARK: - Helpers

    /// attach nominal complet — (fixture, session, scope, manager capture, surface primaire).
    private func attachedCapture(
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> (SceneFixture, any KadreSession, any KadreScope, any CaptureManager, any HostSurface) {
        let (fixture, session, scope) = try attachedScope(file: file, line: line)
        let surface = try XCTUnwrap(fixture.primarySurface, "no primary surface captured", file: file, line: line)
        return (fixture, session, scope, scope.capture, surface)
    }

    /// L'état manager capture TYPÉ, relu borné (le StateFlow vit sur le main de la session).
    private func managerState(
        _ manager: any CaptureManager,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> CaptureManagerState {
        let deadline = Date().addingTimeInterval(timeout)
        while true {
            if let state = manager.state.value as? CaptureManagerState { return state }
            if Date() > deadline {
                return try XCTUnwrap(manager.state.value as? CaptureManagerState,
                                     "the capture manager state never projected", file: file, line: line)
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// L'état session capture relu borné avec prédicat (pompe du run loop, jamais un sommeil nu).
    private func captureSessionState(
        _ session: any CaptureSession,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (any CaptureSessionState) -> Bool,
    ) throws -> (any CaptureSessionState) {
        let deadline = Date().addingTimeInterval(timeout)
        var last: String = "none"
        while true {
            if let state = session.state.value as? (any CaptureSessionState) {
                last = String(describing: state)
                if predicate(state) { return state }
            }
            if Date() > deadline {
                XCTFail("capture session state not reached in \(timeout)s — last=\(last)",
                        file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 7,
                              userInfo: ["what": "capture session state not reached"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// La vraie API suspendue `open` (completion + pompe XCTest — jamais un sommeil nu).
    private func openCapture(
        _ manager: any CaptureManager,
        request: CaptureRequest,
        timeout: TimeInterval = 15,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> KadreResult {
        var outcome: KadreResult?
        let done = expectation(description: "capture open completed")
        manager.open(request: request) { result, error in
            XCTAssertNil(error, "capture open error", file: file, line: line)
            outcome = result
            done.fulfill()
        }
        wait(for: [done], timeout: timeout)
        return try XCTUnwrap(outcome, "capture open delivered no result", file: file, line: line)
    }

    /// La vraie API suspendue `requestPermission` (completion + pompe).
    private func requestPermission(
        _ manager: any CaptureManager,
        scope: CapturePermissionScope,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> KadreResult {
        var outcome: KadreResult?
        let done = expectation(description: "requestPermission completed")
        manager.requestPermission(scope: scope) { result, error in
            XCTAssertNil(error, "requestPermission error", file: file, line: line)
            outcome = result
            done.fulfill()
        }
        wait(for: [done], timeout: timeout)
        return try XCTUnwrap(outcome, "requestPermission delivered no result", file: file, line: line)
    }

    /// L'état de permission d'UN scope, projeté de la réponse (`Success` → son état).
    private func permissionState(
        _ manager: any CaptureManager,
        scope: CapturePermissionScope,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> CaptureManagerState {
        let result = try requestPermission(manager, scope: scope, file: file, line: line)
        return try XCTUnwrap((result as? KadreResultSuccess<AnyObject>)?.value as? CaptureManagerState,
                             "the permission readback succeeds: \(result)", file: file, line: line)
    }

    /// L'ouverture RÉELLE de la cible Surface sur l'id de la surface primaire du fixture —
    /// le même accès que le consommateur (`KadreScope.capture.open`). Retourne la session.
    private func openSurfaceSession(
        _ manager: any CaptureManager,
        surface: any HostSurface,
        timeout: TimeInterval = 15,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> any CaptureSession {
        let request = CaptureRequest(
            target: CaptureTargetSurface(id: surface.id),
            preferredSize: nil,
            preferredFormats: [],
            region: nil,
            cursorMode: CaptureCursorMode.hidden,
            minimumFrameInterval: nil,
        )
        let result = try openCapture(manager, request: request, timeout: timeout, file: file, line: line)
        let success = try XCTUnwrap(result as? KadreResultSuccess<AnyObject>,
                                    "the surface open must succeed, got \(result)", file: file, line: line)
        return try XCTUnwrap(success.value as? (any CaptureSession),
                             "the surface open must hand a CaptureSession", file: file, line: line)
    }

    /// L'outcome terminal d'`awaitTermination` — completion + pompe.
    private func awaitCaptureTermination(
        _ session: any CaptureSession,
        timeout: TimeInterval = 20,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> (any CaptureOutcome) {
        var outcome: (any CaptureOutcome)?
        let done = expectation(description: "capture awaitTermination")
        session.awaitTermination { terminated, error in
            XCTAssertNil(error, "awaitTermination error", file: file, line: line)
            outcome = terminated
            done.fulfill()
        }
        wait(for: [done], timeout: timeout)
        return try XCTUnwrap(outcome, "awaitTermination delivered no outcome", file: file, line: line)
    }

    /// La sortie du collect (`collectFrames` retourne à l'ARRÊT — le pin de Task 5).
    private func awaitCollectResult(
        _ collect: @escaping () -> KadreResult?,
        timeout: TimeInterval = 20,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> KadreResult {
        let deadline = Date().addingTimeInterval(timeout)
        var outcome = collect()
        while outcome == nil {
            if Date() > deadline {
                return try XCTUnwrap(outcome, "collectFrames never completed", file: file, line: line)
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
            outcome = collect()
        }
        return try XCTUnwrap(outcome, "collectFrames delivered no result", file: file, line: line)
    }

    /// Le drain borné du collecteur : pompe le run loop jusqu'au compte visé — LE canal de
    /// livraison traverse les dispatchers Kotlin, seule la pompe le fait atterrir.
    private func drainCollector(
        _ collector: CaptureFrameCollector,
        to count: Int,
        timeout: TimeInterval = 15,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let deadline = Date().addingTimeInterval(timeout)
        while collector.count < count {
            if Date() > deadline {
                XCTFail("collector never reached \(count) frames in \(timeout)s — got \(collector.count) "
                    + "failures=\(driver.recentFailureDescriptions)", file: file, line: line)
                throw NSError(domain: "KadreUikitDriver", code: 8,
                              userInfo: ["what": "frame collector drain not reached"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// Fenêtre de quiescence bornée : pendant `window` secondes, les DEUX comptes restent
    /// FIGÉS à leurs repères respectifs — la preuve d'ABSENCE (une frame post-arrêt, un
    /// callback tardif les feraient bouger). Le compteur du COLLECTEUR est le sien (les
    /// frames de CETTE session), le compteur de livraisons du driver est process-wide :
    /// chacun est comparé à SON repère, jamais l'un contre l'autre. Le pompage est RÉEL
    /// (le run loop tourne, les ticks en vol atterrissent).
    private func assertFramesFrozen(
        collector: CaptureFrameCollector,
        collectorBaseline: Int,
        deliveredBaseline: Int64,
        window: TimeInterval = 0.7,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        let deadline = Date().addingTimeInterval(window)
        while Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            if collector.count != collectorBaseline
                || driver.captureSurfaceFramesDelivered != deliveredBaseline {
                XCTFail("a frame moved after the terminal: collector=\(collector.count) (want \(collectorBaseline)) "
                    + "delivered=\(driver.captureSurfaceFramesDelivered) (want \(deliveredBaseline))",
                    file: file, line: line)
                return
            }
        }
    }

    /// Le journal de l'attach au repos : aucun report process-wide de plus (miroir du
    /// `assertJournalAtRest` du cycle de fuites Android).
    private func assertJournalAtRest(
        baseline: Int32,
        label: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        XCTAssertEqual(driver.reportedFailureCount, baseline,
                       "\(label): a clean cycle must not report any diagnostic — "
                           + "\(driver.recentFailureDescriptions)", file: file, line: line)
    }

    /// La sonde « zéro UI présentée » : AUCUN view controller présenté sur AUCUNE fenêtre de
    /// TOUTES les scènes connectées (la conséquence visible du zéro-prompt ; le pin
    /// structurel du journal de seam vit dans le test uikitTest de Task 5).
    private func assertNoPresentedUI(
        label: String,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows {
                XCTAssertNil(window.rootViewController?.presentedViewController,
                             "\(label): nothing may be presented by a permission readback", file: file, line: line)
            }
        }
    }

    /// L'égalité de formats portables par `isEqual` (les objets Kotlin — `===` n'est pas le
    /// contrat d'une data object, l'égalité Obj-C l'est, précédent fixture).
    private func formatsMatch(
        _ left: (any PixelFormat)?,
        _ right: (any PixelFormat)?,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) -> Bool {
        guard let left, let right else { return false }
        return (left as? NSObject)?.isEqual(right) ?? false
    }

    /// La lecture renderer VIVANTE : la même voie que la pompe (`UIGraphicsImageRenderer` +
    /// `drawViewHierarchy`, repli `layer.render`), le bitmap info décodé par LA MÊME table
    /// que `uikitSurfaceBitmapInfoFormatOf` (le pin du spike fix round 2 : byteOrder
    /// 32Little=0x2000, alpha-first little ⇒ BGRA, alpha-last ⇒ RGBA). Retourne
    /// (pixels, pixels, format portable) — échoue si HORS table (l'honnêteté du spike :
    /// JAMAIS de format supposé).
    private func liveRendererReadback(
        _ view: UIView,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> (width: Int32, height: Int32, format: (any PixelFormat)) {
        func decode(_ raw: UInt32) -> (any PixelFormat)? {
            let byteOrder = raw & 0xF000
            if byteOrder == 0x1000 || byteOrder == 0x3000 { return nil } // unités 16-bit
            if raw & 0x100 != 0 { return nil } // composantes flottantes
            let alpha = raw & 0xF
            switch (alpha, byteOrder) {
            case (0x2, 0x2000): return PixelFormatBgra8.shared
            case (0x1, 0x0000), (0x1, 0x4000): return PixelFormatRgba8.shared
            case (0x6, 0x2000): return PixelFormatBgrx8.shared
            default: return nil
            }
        }
        func renderPath(draw: (CGContext) -> Void) -> (CGImage, UInt32)? {
            let renderer = UIGraphicsImageRenderer(bounds: view.bounds,
                                                   format: UIGraphicsImageRendererFormat.default())
            let image = renderer.image { context in draw(context.cgContext) }
            guard let cg = image.cgImage else { return nil }
            return (cg, cg.bitmapInfo.rawValue)
        }
        // La voie PRIMAIRE de la pompe (drawViewHierarchy) puis le repli documenté
        // (layer.render) — le même ordre que `UIKitLiveSurfaceSnapshotSource`.
        let paths: [(String, (CGContext) -> Void)] = [
            ("drawHierarchy", { _ in view.drawHierarchy(in: view.bounds, afterScreenUpdates: true) }),
            ("layer.render", { context in view.layer.render(in: context) }),
        ]
        for (label, draw) in paths {
            guard let (cg, raw) = renderPath(draw: draw), let format = decode(raw) else { continue }
            NSLog("KADRE-DRIVER capture readback: path=\(label) width=\(cg.width) height=\(cg.height) "
                + String(format: "bitmapInfo=0x%lx", raw))
            return (Int32(cg.width), Int32(cg.height), format)
        }
        XCTFail("neither renderer path produced an in-table raster — the driver host lost its "
            + "coherent-renderer fact (spike T1)", file: file, line: line)
        throw NSError(domain: "KadreUikitDriver", code: 9,
                      userInfo: ["what": "renderer readback out of table"])
    }

    // MARK: - Le collecteur Swift (le `suspend (CaptureFrame) -> Unit` exporté)

    /// Le collecteur de frames côté Swift — le même pattern que le ByteCollector du drop :
    /// le `KotlinSuspendFunction1` à implémenter ; chaque frame arrive comme `CaptureFrame`,
    /// la COPIE vit DANS l'invocation (`doCopyPlanes` — la lease est refermée par le runtime
    /// au retour, le contrat §11.1). Chaque invocation répond Unit (on continue de collecter)
    /// ; l'arrêt vient de `requestStop` (le test, ou la course depuis l'invocation elle-même
    /// pour le scénario concurrent).
    final class CaptureFrameCollector: NSObject, KotlinSuspendFunction1 {

        /// Les faits d'UNE frame livrée — la copie est faite, seule la matière compte reste.
        struct Record {
            let revision: Int64
            let width: Int32
            let height: Int32
            let format: (any PixelFormat)?
            let rowStride: Int32
            let pixelStride: Int32
            let planeByteCount: Int32
            let copiedByteCount: Int
        }

        private let lock = NSLock()
        private var storage: [Record] = []
        /// La course du scénario stop-concurrent : au N-ième frame, l'invocation ELLE-MÊME
        /// tire `requestStop` (la course contre la boucle de livraison, sur SON fil).
        private var stopAfterCount: Int = .max
        private weak var stopTarget: (any CaptureSession)?

        var count: Int {
            lock.lock(); defer { lock.unlock() }
            return storage.count
        }

        var snapshot: [Record] {
            lock.lock(); defer { lock.unlock() }
            return storage
        }

        func arm(stopAfter: Int, target: any CaptureSession) {
            lock.lock(); defer { lock.unlock() }
            stopAfterCount = stopAfter
            stopTarget = target
        }

        func invoke(p1: Any?, completionHandler: @escaping (Any?, Error?) -> Void) {
            defer { completionHandler(nil, nil) }
            guard let frame = p1 as? CaptureFrame else { return }
            // LA COPIE dans la fenêtre de la lease (le runtime referme la frame au retour).
            let planes = frame.doCopyPlanes()
            let layout = frame.planes.first
            var copied = 0
            for plane in planes { copied += Int(plane.bytes.size) }
            let record = Record(
                revision: frame.configurationRevision,
                width: frame.size.width,
                height: frame.size.height,
                format: frame.format,
                rowStride: layout?.rowStride ?? 0,
                pixelStride: layout?.pixelStride ?? 0,
                planeByteCount: layout?.byteCount ?? 0,
                copiedByteCount: copied,
            )
            var shouldStop = false
            lock.lock()
            storage.append(record)
            if storage.count >= stopAfterCount { shouldStop = true }
            lock.unlock()
            if shouldStop { stopTarget?.requestStop() }
        }
    }

    /// Le démarrage du collect : la vraie API suspendue, la completion ARRIVE À L'ARRÊT
    /// (le runtime ne démarre le flux que DANS collectFrames — le pin de Task 5).
    private func startCollecting(
        _ session: any CaptureSession,
        _ collector: CaptureFrameCollector,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) -> () -> KadreResult? {
        // PAS d'expectation XCTest : la completion arrive sur un fil Kotlin arbitraire et
        // l'attente EST le pompage (awaitCollectResult lit le résultat sous verrou, le run
        // loop tourne à côté) — une expectation jamais attendue par `wait(for:)` empoisonnerait
        // le verdict du test (KadreUikitDriver « unwaited expectations »).
        var outcome: KadreResult?
        let lock = NSLock()
        session.collectFrames(collector: collector) { result, error in
            XCTAssertNil(error, "collectFrames error", file: file, line: line)
            lock.lock()
            outcome = result
            lock.unlock()
        }
        return {
            lock.lock(); defer { lock.unlock() }
            return outcome
        }
    }

    // MARK: - Scénarios (noms FIGÉS)

    /// uikit-capture-capabilities-honest — la table de vérité VIVANTE lue via
    /// `KadreScope.capture.state` : écran `Supported(Bgra8, EmbeddedWhenAvailable, région
    /// Unsupported)` GOUVERNÉ par la lecture LIVE de la disponibilité (relu par le test
    /// lui-même — jamais « Simulator ⇒ indisponible » codé en dur) ; surface
    /// `Supported(Rgba8, Hidden, région Unsupported)` ; fenêtre/picker/énumération
    /// structurellement refusés ; inventaire honnêtement `Unavailable` — JAMAIS
    /// `HostPickerOnly`. Les DEUX familles portent la même table (les absences documentées
    /// — fenêtre, picker, énumération — y sont ASSERTÉES, pas gardées) ; `RPScreenRecorder`
    /// est klib-présent tvOS (vérifié par le spike, aucune garde os nécessaire).
    func test_uikit_capture_capabilities_honest() throws {
        let (fixture, _, _, manager, _) = try attachedCapture()
        let journalBaseline = driver.reportedFailureCount

        let state = try managerState(manager)
        XCTAssertEqual(state.revision, 0, "the initial projection is revision 0")

        // LE GOUVERNAIL LIVE : la même lecture SDK que la couture (la fonction klib
        // `isAvailable()` — pas une propriété).
        let liveAvailable = RPScreenRecorder.shared().isAvailable
        NSLog("KADRE-DRIVER capture: live RPScreenRecorder.isAvailable = \(liveAvailable)")

        // Permissions DÉRIVÉES de la disponibilité (ruling 5) — jamais un prompt.
        if liveAvailable {
            XCTAssertTrue(state.permissions.screen is PermissionStateGranted,
                          "a live recorder is usable with no consent dialog: "
                              + String(describing: state.permissions.screen))
        } else {
            let unavailable = try XCTUnwrap(state.permissions.screen as? PermissionStateUnavailable)
            let temporary = try XCTUnwrap(unavailable.failure as? KadreFailureTemporarilyUnavailable,
                                          "the honest recorder fact carries no invented why: \(unavailable.failure)")
            XCTAssertTrue(temporary.retryable, "the live property can come back — retryable")
        }
        let windowPermission = try XCTUnwrap(state.permissions.window as? PermissionStateUnavailable)
        let windowUnsupported = try XCTUnwrap(windowPermission.failure as? KadreFailureUnsupported)
        XCTAssertTrue(windowUnsupported.operation === KadreOperation.capturepermission,
                      "the window permission can never become granted")

        // L'ÉCRAN — les contraintes ReplayKit documentées, la disponibilité LUE.
        let screen = try XCTUnwrap(state.capabilities.screen as? CapabilitySupported<CaptureTargetConstraints>)
        let screenFormats = try XCTUnwrap(screen.constraints?.formats, "the screen arm names its formats")
        XCTAssertEqual(screenFormats.count, 1, "exactly the documented 32BGRA output")
        XCTAssertTrue(screenFormats.first is PixelFormatBgra8,
                      "the screen output is Bgra8, got \(String(describing: screenFormats.first))")
        let screenCursors = try XCTUnwrap(screen.constraints?.cursorModes)
        XCTAssertTrue(screenCursors.contains(CaptureCursorMode.embeddedwhenavailable),
                      "the system cursor is composed into the full display capture")
        XCTAssertEqual(screenCursors.count, 1)
        XCTAssertTrue(screen.constraints?.region is FeatureAvailabilityUnsupported,
                      "the whole display is captured — no region primitive")
        if liveAvailable {
            XCTAssertTrue(screen.availability is FeatureAvailabilityAvailable)
        } else {
            let unavailable = try XCTUnwrap(screen.availability as? FeatureAvailabilityUnavailable)
            XCTAssertTrue(unavailable.failure is KadreFailureTemporarilyUnavailable,
                          "the same honest recorder fact gates the capability")
        }

        // LA SURFACE — les contraintes de la voie renderer MESURÉE (le fait du driver).
        let surface = try XCTUnwrap(state.capabilities.surface as? CapabilitySupported<CaptureTargetConstraints>)
        let surfaceFormats = try XCTUnwrap(surface.constraints?.formats)
        XCTAssertEqual(surfaceFormats.count, 1, "exactly the measured renderer format")
        XCTAssertTrue(surfaceFormats.first is PixelFormatRgba8,
                      "the surface arm names the measured renderer format of THIS host, got "
                          + String(describing: surfaceFormats.first))
        let surfaceCursors = try XCTUnwrap(surface.constraints?.cursorModes)
        XCTAssertTrue(surfaceCursors.contains(CaptureCursorMode.hidden),
                      "the view's own buffer never composes a pointer")
        XCTAssertTrue(surface.constraints?.region is FeatureAvailabilityUnsupported)
        XCTAssertTrue(surface.availability is FeatureAvailabilityAvailable,
                      "the renderer arm is not availability-gated")

        // LES REFUS STRUCTURELS (les absences documentées, assertées sur les deux familles).
        let window = try XCTUnwrap(state.capabilities.window as? CapabilityUnsupported)
        XCTAssertTrue(window.failure.operation === KadreOperation.captureopen,
                      "no primitive enumerates capturable windows")
        let enumeration = try XCTUnwrap(state.capabilities.sourceEnumeration as? CapabilityUnsupported)
        XCTAssertTrue(enumeration.failure.operation === KadreOperation.capturerefreshsources,
                      "no primitive enumerates capture sources")
        XCTAssertTrue(state.capabilities.hostPicker is FeatureAvailabilityUnsupported,
                      "never a picker promise — the fact that makes HostChoice unreachable")
        let sources = try XCTUnwrap(state.sources as? CaptureSourcesUnavailable)
        XCTAssertTrue((sources.failure as? KadreFailureUnsupported)?.operation
            === KadreOperation.capturerefreshsources,
            "the inventory is honestly unavailable — never HostPickerOnly")

        assertJournalAtRest(baseline: journalBaseline, label: "capabilities-honest")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-capture-permission-never-prompts — `requestPermission(Screen)` est
    /// répétable et idempotent (deux demandes relisent le MÊME état), retourne la
    /// disponibilité LUE, et AUCUNE interface ne se dresse (la sonde visible : zéro
    /// view controller présenté avant/après sur toutes les fenêtres ; le pin structurel
    /// du journal de seam vit dans uikitTest). Le scope Window reste structurellement refusé.
    func test_uikit_capture_permission_never_prompts() throws {
        let (fixture, _, _, manager, _) = try attachedCapture()
        let journalBaseline = driver.reportedFailureCount
        assertNoPresentedUI(label: "before")

        let before = try managerState(manager)
        let first = try permissionState(manager, scope: .screen)
        let second = try permissionState(manager, scope: .screen)
        XCTAssertTrue(first.isEqual(second), "the request is repeatable and idempotent: \(first) vs \(second)")
        XCTAssertTrue(first.isEqual(before) || first.revision > before.revision,
                      "the readback returns the read availability state (\(first) vs \(before))")
        XCTAssertEqual(String(describing: first.permissions.screen),
                       String(describing: before.permissions.screen),
                       "the permission facts come from the same live read")

        assertNoPresentedUI(label: "after")
        XCTAssertNotEqual(UIApplication.shared.applicationState, UIApplication.State.background,
                          "the process stayed foreground — a prompt would have left the app")

        let windowResult = try requestPermission(manager, scope: .window)
        let windowFailure = try XCTUnwrap((windowResult as? KadreResultFailure)?.reason,
                                          "the window scope is refused structurally")
        XCTAssertTrue((windowFailure as? KadreFailureUnsupported)?.operation
            === KadreOperation.capturepermission)

        assertJournalAtRest(baseline: journalBaseline, label: "permission-never-prompts")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-capture-source-refused — les refus consommateurs des cibles non ouvrables :
    /// `open(Source(...))` répond la forme consommatrice `InvalidRequest("request.target")`
    /// (AUCUNE source énumérée n'existe — l'inventaire est honnêtement indisponible), lu
    /// par la sonde `captureSourceRefusalProbe` (`CaptureSourceId` est opaque hors module
    /// Kotlin) ; `refreshSources()` — la vraie API publique — répond
    /// `Unsupported(CaptureRefreshSources)` et l'état reste `Unavailable` (la même forme
    /// aux deux places — jamais un inventaire fabriqué).
    func test_uikit_capture_source_refused() throws {
        let (fixture, _, scope, manager, _) = try attachedCapture()
        let journalBaseline = driver.reportedFailureCount

        // LA CIBLE SOURCE — la sonde porte le chemin consommateur réel (l'id est opaque).
        let probe = driver.captureSourceRefusalProbe(scope: scope)
        XCTAssertNil(probe.threw, "the refusal path must not throw: \(probe.threw ?? "")")
        XCTAssertTrue(probe.sourceOpenInvalidRequestTarget,
                      "the consumer-visible form is InvalidRequest(request.target), got: "
                          + probe.sourceOpenOutcomeDescription)

        // L'ÉNUMÉRATION — la vraie API publique du manager.
        var refreshedOutcome: KadreResult?
        let refreshed = expectation(description: "refreshSources completed")
        manager.refreshSources { result, error in
            XCTAssertNil(error, "refreshSources error")
            refreshedOutcome = result
            refreshed.fulfill()
        }
        wait(for: [refreshed], timeout: 10)
        let refreshFailure = try XCTUnwrap((refreshedOutcome as? KadreResultFailure)?.reason,
                                           "the enumeration is refused structurally")
        XCTAssertTrue((refreshFailure as? KadreFailureUnsupported)?.operation
            === KadreOperation.capturerefreshsources,
            "got \(String(describing: refreshFailure))")

        // L'ÉTAT reste honnête : l'inventaire Unavailable, la table inchangée.
        let after = try managerState(manager)
        let sources = try XCTUnwrap(after.sources as? CaptureSourcesUnavailable)
        XCTAssertTrue((sources.failure as? KadreFailureUnsupported)?.operation
            === KadreOperation.capturerefreshsources)
        XCTAssertTrue(after.capabilities.sourceEnumeration is CapabilityUnsupported)

        assertJournalAtRest(baseline: journalBaseline, label: "source-refused")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-capture-surface-stream-bounded — LA preuve bout en bout du driver : l'ouverture
    /// de la cible Surface par la SURFACE PUBLIQUE sur la vue du fixture livre de VRAIES
    /// frames du renderer (compte borné), les faits de format matchent la lecture renderer
    /// VIVANTE (rgba8 mesuré sur ces hôtes — jamais supposé), la discipline d'octets tient
    /// dans la lease (`doCopyPlanes` exactement le layout du plan), un resize RÉEL de la vue
    /// porte une reconfiguration AVANT la frame qui la transporte (révision +1), l'arrêt
    /// livré `Stopped(Requested)` — et ZÉRO frame après le terminal (comptes gelés).
    func test_uikit_capture_surface_stream_bounded() throws {
        let (fixture, _, _, manager, surface) = try attachedCapture()
        let view = try XCTUnwrap(DriverFixture.shared.primary?.observedView)
        // LE STIMULUS DE CONTENU (fait mesuré, cf. en-tête) : un raster mentirait sans lui.
        view.backgroundColor = .red
        view.superview?.layoutIfNeeded()
        let journalBaseline = driver.reportedFailureCount
        let openedBaseline = driver.captureSurfaceReservationsOpened
        let framesBaseline = driver.captureSurfaceFramesDelivered

        let session = try openSurfaceSession(manager, surface: surface)
        XCTAssertTrue(session.source.kind === CaptureSourceKind.hostsurface,
                      "the surface arm names its kind")
        XCTAssertTrue(session.state.value is CaptureSessionStateReady,
                      "no stream exists before the collector starts (the pin of Task 5)")

        let collector = CaptureFrameCollector()
        let collect = startCollecting(session, collector)

        // LA CONFIGURATION (révision 0) est dérivée de la première frame AVANT toute frame —
        // les faits doivent matcher la lecture renderer VIVANTE du test.
        _ = try captureSessionState(session) { $0 is CaptureSessionStateStreaming }
        let streamingState = try XCTUnwrap(session.state.value as? CaptureSessionStateStreaming)
        XCTAssertEqual(streamingState.configuration.revision, 0, "revision 0")
        let readback = try liveRendererReadback(view)
        XCTAssertEqual(streamingState.configuration.size.width, readback.width,
                       "the first copy derives the real size — width")
        XCTAssertEqual(streamingState.configuration.size.height, readback.height,
                       "the first copy derives the real size — height")
        XCTAssertTrue(formatsMatch(streamingState.configuration.format, readback.format),
                      "the frame format is the LIVE renderer readback (\(readback.format)), never assumed — "
                          + "got \(streamingState.configuration.format)")

        // Compte borné : trois frames réelles, chaque copie EXACTE du layout du plan.
        try drainCollector(collector, to: 3)
        for (index, record) in collector.snapshot.prefix(3).enumerated() {
            XCTAssertEqual(record.revision, 0, "frame \(index): revision 0")
            XCTAssertTrue(formatsMatch(record.format, readback.format),
                          "frame \(index): the carried format is the readback format")
            XCTAssertGreaterThanOrEqual(record.rowStride, record.width * 4,
                                        "frame \(index): the bytesPerRow honesty floor")
            XCTAssertEqual(record.pixelStride, 4, "frame \(index): packed 32bpp")
            XCTAssertEqual(record.copiedByteCount, Int(record.planeByteCount),
                           "frame \(index): the lease copy is exactly the plane layout")
            XCTAssertEqual(record.planeByteCount, record.rowStride * record.height,
                           "frame \(index): full rows copied (h·rowStride)")
        }

        // LA RECONFIGURATION AVANT LA FRAME PORTEUSE : un resize RÉEL de la vue change le
        // raster du tick SUIVANT (le snapshot lit bounds à chaque tick) — la révision +1
        // est publiée avant la première frame aux nouvelles dimensions.
        let newFrame = CGRect(x: 0, y: 0, width: 480, height: 200)
        let scale = view.traitCollection.displayScale
        let newPixelWidth = Int32((newFrame.width * scale).rounded())
        let newPixelHeight = Int32((newFrame.height * scale).rounded())
        view.frame = newFrame
        view.superview?.layoutIfNeeded()
        try drainCollector(collector, to: 4, timeout: 15)
        let records = collector.snapshot
        let firstResized = try XCTUnwrap(records.first { $0.width == newPixelWidth },
                                         "a frame at the new size must arrive: \(records.map { ($0.width, $0.height, $0.revision) })")
        XCTAssertEqual(firstResized.revision, 1,
                       "the reconfiguration precedes the frame that carries it (revision 1)")
        for record in records where record.width == newPixelWidth {
            XCTAssertEqual(record.revision, 1, "no frame at the new size may carry the old revision")
            XCTAssertEqual(record.height, newPixelHeight)
        }
        _ = try captureSessionState(session) { state in
            guard let streaming = state as? CaptureSessionStateStreaming else { return false }
            return streaming.configuration.revision == 1
                && streaming.configuration.size.width == newPixelWidth
        }

        // L'ARRÊT : `Stopped(Requested)`, le collect retourne le succès, et RIEN ne suit —
        // la fenêtre de quiescence pompe le run loop RÉEL (les ticks tardifs tomberaient
        // sur la garde du pump, jamais dessinés ni livrés).
        let deliveredAtStop = driver.captureSurfaceFramesDelivered
        XCTAssertEqual(Int64(collector.count), deliveredAtStop - framesBaseline,
                       "every delivered frame reached this collector")
        session.requestStop()
        let outcome = try awaitCaptureTermination(session)
        let stopped = try XCTUnwrap(outcome as? CaptureOutcomeStopped,
                                    "the app-initiated stop is the requested stop: \(outcome)")
        XCTAssertTrue(stopped.reason === CaptureStopReason.requested)
        XCTAssertTrue(try awaitCollectResult(collect) is KadreResultSuccess<AnyObject>,
                      "the collector returns the requested stop")

        assertFramesFrozen(collector: collector, collectorBaseline: collector.count,
                           deliveredBaseline: deliveredAtStop)
        XCTAssertEqual(driver.captureSurfaceReservationsOpened, openedBaseline + 1,
                       "exactly one surface reservation was admitted")

        session.close()
        XCTAssertEqual(driver.captureSurfaceReservationsInFlight, 0,
                       "the reservation is handed back at rest")
        assertJournalAtRest(baseline: journalBaseline, label: "surface-stream-bounded")

        // Politesse : la vue du fixture retrouve ses dimensions d'origine pour la suite.
        view.backgroundColor = nil
        view.frame = CGRect(x: 0, y: 0, width: 320, height: 240)
        view.superview?.layoutIfNeeded()
        NotificationCenter.default.post(name: UIScene.didActivateNotification, object: fixture.scene)
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-capture-stop-concurrent — `requestStop` EN COURSE avec `collectFrames` :
    /// l'invocation du collecteur ELLE-MÊME tire l'arrêt pendant que le ticker enfile des
    /// frames (la course sur le fil de livraison) — exactement UN terminal
    /// (`Stopped(Requested)`), pas de crash, le collect retourne le succès, et aucune frame
    /// ne suit l'arrêt (comptes gelés sur la fenêtre de quiescence).
    func test_uikit_capture_stop_concurrent() throws {
        let (fixture, _, _, manager, surface) = try attachedCapture()
        let view = try XCTUnwrap(DriverFixture.shared.primary?.observedView)
        view.backgroundColor = .red // le stimulus de contenu (fait mesuré, cf. en-tête)
        view.superview?.layoutIfNeeded()
        let journalBaseline = driver.reportedFailureCount

        let framesBaseline = driver.captureSurfaceFramesDelivered
        let session = try openSurfaceSession(manager, surface: surface)
        _ = try captureSessionState(session) { $0 is CaptureSessionStateReady }
        let collector = CaptureFrameCollector()
        collector.arm(stopAfter: 2, target: session) // l'invocation #2 tire requestStop EN COURSE
        let collect = startCollecting(session, collector)

        // L'arrêt est tiré DEPUIS la livraison : la complétion du collect arrive seule.
        try drainCollector(collector, to: 2)
        let outcome = try awaitCaptureTermination(session)
        let stopped = try XCTUnwrap(outcome as? CaptureOutcomeStopped, "exactly one terminal: \(outcome)")
        XCTAssertTrue(stopped.reason === CaptureStopReason.requested)
        XCTAssertTrue(try awaitCollectResult(collect) is KadreResultSuccess<AnyObject>,
                      "the racing stop resolves the collector with the requested stop")

        let deliveredAtStop = driver.captureSurfaceFramesDelivered
        XCTAssertEqual(Int64(collector.count), deliveredAtStop - framesBaseline,
                       "this test's frames are all the frames ever delivered")
        assertFramesFrozen(collector: collector, collectorBaseline: collector.count,
                           deliveredBaseline: deliveredAtStop)
        session.close()
        XCTAssertEqual(driver.captureSurfaceReservationsInFlight, 0)
        assertJournalAtRest(baseline: journalBaseline, label: "stop-concurrent")
        view.backgroundColor = nil
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-capture-late-callback-after-close — la session capture refermée EN PLEIN
    /// FLUX : le terminal est livré (`Stopped(Requested)`), puis les callbacks de pompe
    /// tardifs sont des no-ops (les comptes restent FIGÉS sur la fenêtre de quiescence).
    /// Le second flavor porte l'arrêt du PARENT (la session hôte) au milieu d'un flux
    /// neuf : le terminal est `Stopped(ParentSessionStopping)` — le pin T5 sur le vrai
    /// ordre de teardown du driver.
    func test_uikit_capture_late_callback_after_close() throws {
        let (fixture, _, _, manager, surface) = try attachedCapture()
        let view = try XCTUnwrap(DriverFixture.shared.primary?.observedView)
        view.backgroundColor = .red // le stimulus de contenu (fait mesuré, cf. en-tête)
        view.superview?.layoutIfNeeded()
        let journalBaseline = driver.reportedFailureCount

        // FLAVOR A — le close de la session capture, en plein flux.
        let first = try openSurfaceSession(manager, surface: surface)
        let firstCollector = CaptureFrameCollector()
        let firstCollect = startCollecting(first, firstCollector)
        try drainCollector(firstCollector, to: 2)
        first.close() // le close EST l'arrêt propre de l'application
        let firstOutcome = try awaitCaptureTermination(first)
        let firstStopped = try XCTUnwrap(firstOutcome as? CaptureOutcomeStopped,
                                         "the close delivers the terminal: \(firstOutcome)")
        XCTAssertTrue(firstStopped.reason === CaptureStopReason.requested)
        XCTAssertTrue(try awaitCollectResult(firstCollect) is KadreResultSuccess<AnyObject>)
        let frozenAtClose = driver.captureSurfaceFramesDelivered
        assertFramesFrozen(collector: firstCollector, collectorBaseline: firstCollector.count,
                           deliveredBaseline: frozenAtClose)
        assertJournalAtRest(baseline: journalBaseline, label: "late-callback close flavor")

        // FLAVOR B — l'arrêt du PARENT au milieu d'un flux neuf (le slot unique est revenu :
        // le slot de session est rendu à la terminaison de la première session).
        let second = try openSurfaceSession(manager, surface: surface)
        let secondCollector = CaptureFrameCollector()
        let secondCollect = startCollecting(second, secondCollector)
        try drainCollector(secondCollector, to: 1)
        try stopAndAwaitTermination(fixture) // l'arrêt de la session hôte
        let secondOutcome = try awaitCaptureTermination(second)
        let secondStopped = try XCTUnwrap(secondOutcome as? CaptureOutcomeStopped,
                                          "the parent stop interrupts the stream: \(secondOutcome)")
        XCTAssertTrue(secondStopped.reason === CaptureStopReason.parentsessionstopping,
                      "the parent-session stop carries its own reason: \(secondStopped.reason)")
        XCTAssertTrue(try awaitCollectResult(secondCollect) is KadreResultSuccess<AnyObject>)
        let frozenAfterParent = driver.captureSurfaceFramesDelivered
        assertFramesFrozen(collector: secondCollector, collectorBaseline: secondCollector.count,
                           deliveredBaseline: frozenAfterParent)
        XCTAssertEqual(driver.captureSurfaceReservationsInFlight, 0,
                       "both reservations handed back")
        assertJournalAtRest(baseline: journalBaseline, label: "late-callback parent flavor")
        view.backgroundColor = nil
    }

    /// uikit-capture-hostchoice-unreachable-honest — l'inatteignabilité consommatrice du
    /// bras HostChoice (ruling T5) : `open(HostChoice)` répond `Unsupported(CaptureOpen)`
    /// AVANT toute réservation de port (le delta des réservations est ZÉRO — l'admission
    /// refuse sur `hostPicker == Unsupported`, la table et le refus racontent LE MÊME
    /// fait), et AUCUNE frame n'est jamais fabriquée (le compteur de livraisons reste
    /// gelé ; les frames ReplayKit sont le charter DEVICE — Simulator n'en livre pas,
    /// SPIKE-FACT T3 : complétion du start jamais rendue).
    func test_uikit_capture_hostchoice_unreachable_honest() throws {
        let (fixture, _, _, manager, _) = try attachedCapture()
        let journalBaseline = driver.reportedFailureCount
        let openedBaseline = driver.captureSurfaceReservationsOpened
        let framesBaseline = driver.captureSurfaceFramesDelivered
        let stateBefore = try managerState(manager)

        // LA COHÉRENCE D'ABORD : la table annonce le picker Unsupported — LE fait qui rend
        // la cible inatteignable.
        XCTAssertTrue(stateBefore.capabilities.hostPicker is FeatureAvailabilityUnsupported)

        let request = CaptureRequest(
            target: CaptureTargetHostChoice.shared,
            preferredSize: nil,
            preferredFormats: [],
            region: nil,
            cursorMode: CaptureCursorMode.embeddedwhenavailable,
            minimumFrameInterval: nil,
        )
        let opened = try openCapture(manager, request: request)
        let failure = try XCTUnwrap((opened as? KadreResultFailure)?.reason,
                                    "the host-choice open is refused at admission, got \(opened)")
        let unsupported = try XCTUnwrap(failure as? KadreFailureUnsupported,
                                        "the honest refusal is Unsupported(CaptureOpen), got \(failure)")
        XCTAssertTrue(unsupported.operation === KadreOperation.captureopen)

        // AVANT TOUT FAIT DE PORT : zéro réservation admise (l'admission du runtime refuse
        // sans jamais joindre le port — le delta du compteur est la lecture driver du
        // journal de seam du pin T5).
        XCTAssertEqual(driver.captureSurfaceReservationsOpened, openedBaseline,
                       "the refused admission touched no port reserve")
        XCTAssertEqual(driver.captureSurfaceReservationsInFlight, 0)
        XCTAssertEqual(try managerState(manager).revision, stateBefore.revision,
                       "the refusal publishes nothing")

        // ZÉRO frame fabriquée, JAMAIS : rien à collecter (aucune session n'existe), le
        // compteur de livraisons reste gelé sur la fenêtre de quiescence pompage RÉEL.
        let deadline = Date().addingTimeInterval(0.5)
        while Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            XCTAssertEqual(driver.captureSurfaceFramesDelivered, framesBaseline,
                           "no frame may ever materialize from a refused HostChoice")
        }
        assertJournalAtRest(baseline: journalBaseline, label: "hostchoice-unreachable")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-capture-frames-release-bounded — LA SONDE de rétention (miroir driver du cycle
    /// de fuites Android Phase-10, SANS contrat inventé : documenté comme sonde). TROIS
    /// cycles réels open → collect (4 frames) → stop sur la MÊME vue : à CHAQUE cycle le
    /// système revient EXACTEMENT au repos — le slot unique de session capture est RENDU
    /// (l'open du cycle suivant réussit : un slot/réservation qui fuite échoue ICI), les
    /// réservations en vol retournent à zéro, le journal reste au repos, et les octets
    /// cumulés des frames livrées sont rapportés contre la résidence du process (le
    /// QUESTION +1-par-frame du `CGDataProviderCopyData` : la borne dure ne tue que la
    /// rétention en fuite ≥ UNE copie de tout ce qui a circulé ; les chiffres mesurés sont
    /// tracés pour la corrélation T7).
    func test_uikit_capture_frames_release_bounded() throws {
        let (fixture, _, _, manager, surface) = try attachedCapture()
        let view = try XCTUnwrap(DriverFixture.shared.primary?.observedView)
        view.backgroundColor = .red // le stimulus de contenu (fait mesuré, cf. en-tête)
        view.superview?.layoutIfNeeded()
        let journalBaseline = driver.reportedFailureCount
        let framesBaseline = driver.captureSurfaceFramesDelivered
        let residencyBaseline = Self.processResidentBytes()
        NSLog("KADRE-DRIVER capture probe: residency baseline = \(residencyBaseline) bytes")

        let cycles = 3
        var bytesMoved = 0
        for cycle in 0..<cycles {
            let openedBefore = driver.captureSurfaceReservationsOpened
            // LE SENTINELLE DU SLOT : le slot unique de session est revenu du cycle
            // précédent — un hold qui fuite rend cet open `ResourceLimitExceeded`.
            let session = try openSurfaceSession(manager, surface: surface)
            XCTAssertEqual(driver.captureSurfaceReservationsOpened, openedBefore + 1,
                           "cycle \(cycle): exactly one reservation admitted")
            let collector = CaptureFrameCollector()
            let collect = startCollecting(session, collector)
            try drainCollector(collector, to: 4)
            let cycleBytes = collector.snapshot.reduce(0) { $0 + $1.copiedByteCount }
            bytesMoved += cycleBytes
            session.requestStop()
            let outcome = try awaitCaptureTermination(session)
            let stopped = try XCTUnwrap(outcome as? CaptureOutcomeStopped, "cycle \(cycle): \(outcome)")
            XCTAssertTrue(stopped.reason === CaptureStopReason.requested)
            XCTAssertTrue(try awaitCollectResult(collect) is KadreResultSuccess<AnyObject>)

            // LE RETOUR AU REPOS DE CHAQUE CYCLE (le miroir Android `assertJournalAtRest`).
            XCTAssertEqual(driver.captureSurfaceReservationsInFlight, 0,
                           "cycle \(cycle): the reservation is handed back — no in-flight residue")
            assertJournalAtRest(baseline: journalBaseline, label: "release cycle \(cycle)")
            session.close()
        }

        let delivered = driver.captureSurfaceFramesDelivered - framesBaseline
        XCTAssertGreaterThanOrEqual(delivered, Int64(cycles * 4),
                                    "the cycles moved real frames: \(delivered) delivered")
        let residencyAfter = Self.processResidentBytes()
        let growth = residencyAfter - residencyBaseline
        // LA MESURE (tracée pour la corrélation T7) : la résidence du process porte le
        // BRUIT du GC Kotlin (mesuré : ~29 MB de croissance pour 14.7 MB circulés — les
        // copies transitoires des 12 frames attendent leur collecte), une borne serrée sur
        // la résidence serait donc FLASQUE. Le kill déterministe du probe vit AILLEURS
        // (le slot de session rendu à chaque cycle, les réservations en vol à zéro, le
        // journal au repos) ; la borne dure ici ne tue que la rétention DÉRAILLEE.
        NSLog("KADRE-DRIVER capture probe: bytes moved = \(bytesMoved), residency growth = \(growth) "
            + "(baseline \(residencyBaseline) → after \(residencyAfter)) — the +1-per-frame question "
            + "is answered by these measured numbers (PROBE, documented as such)")
        XCTAssertLessThan(growth, 256 * 1024 * 1024,
                          "runaway retention across the capture cycles: growth=\(growth) bytes "
                              + "for \(bytesMoved) bytes moved (PROBE bound — documented as such)")

        view.backgroundColor = nil
        try stopAndAwaitTermination(fixture)
    }

    /// Le proxy mémoire du probe : la résidence du process (mach task_info) — lisible,
    /// page-granulaire, suffisant pour une borne généreuse documentée.
    private static func processResidentBytes() -> Int64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) { infoPtr in
            withUnsafeMutablePointer(to: &count) { countPtr in
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO),
                          unsafeBitCast(infoPtr, to: task_info_t.self), countPtr)
            }
        }
        guard result == KERN_SUCCESS else { return 0 }
        return Int64(info.resident_size)
    }
}
