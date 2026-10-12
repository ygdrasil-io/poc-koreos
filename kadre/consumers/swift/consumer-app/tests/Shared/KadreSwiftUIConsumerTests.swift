import XCTest
import KadreUikit
#if canImport(KadreConsumerIos)
@testable import KadreConsumerIos
#elseif canImport(KadreConsumerTvOs)
@testable import KadreConsumerTvOs
#endif

/// Task 6 — la preuve §9 SwiftUI, LES DEUX familles (iOS + tvOS) : « wrapper
/// `UIViewControllerRepresentable` possédé par l'host controller, sans layout Kadre ».
///
/// Les preuves, toutes sur session RÉELLE (le wrapper crée le controller, l'attach reste
/// controller-driven — pattern Task 5 : fenêtre propre au test sur la scène de l'app hôte,
/// factory `HeldApplicationFactory` (la session ne s'arrête jamais seule — la self-stop de
/// `ConsumerApplication` ~500 ms arrêterait la session pendant les assertions), antichambre
/// de lancement, filet `settle`/`tearDown` borné) :
///  1. uikit-swiftui-consumer-attaches-once — le hosting SwiftUI (`UIHostingController`
///     racine, code de production de l'app) attache UNE fois ; UNE réévaluation du body
///     (bump `@Published` — le mécanisme RÉEL de re-render SwiftUI, prouvé positivement par
///     l'époque rendue) ne recrée PAS le controller (même instance `===`, compteur make
///     inchangé) et n'attache PAS davantage (`attachAttempts == 1`) ; requestStop VIA LE
///     CONTROLLER → `.terminated(.stopped(.hostRequested))` ;
///  2. la ride-along Task 4-M3 : `observeState` inscrit APRÈS la terminaison (handle vivant,
///     NON clos — la promesse pré-close §5 couvre l'inscription) → première livraison =
///     terminated (l'émission immédiate du snapshot courant), PUIS silence (le StateFlow
///     terminal n'émet plus jamais) ;
///  3. le chemin gated de lancement (env `KADRE_CONSUMER_SWIFTUI` — mécanique phase 1
///     `DriverSceneRequest`) pose la fenêtre SwiftUI À LA PLACE du store UIKit : l'app de
///     lancement héberge le wrapper, l'attach du store ne passe jamais, le controller du
///     wrapper attache exactement une fois DANS la fenêtre de lancement. Prouvé dans le run
///     dédié (`TEST_RUNNER_`-env + `-only-testing`) ; skippé dans les lancements standard
///     (gate off — un skip est un garde d'environnement, jamais une assertion affaiblie).
///
/// NB de nommage : ce bundle importe DEUX modules qui exportent des noms communs — le
/// fichier n'écrit jamais ces noms nus pour le KOTLIN (`KadreUikit.X` partout si besoin),
/// les noms nus restant les types du module lié (`HeldApplicationFactory`,
/// `KadreSessionSnapshot`…) ou de l'app (`@testable`). L'extension de lisibilité
/// `isTerminated` est celle déjà déclarée par `KadreSessionHandleTests` (interne au
/// module — toute re-déclaration serait un doublon).
private enum KadreSwiftUIConsumerTestsError: Error {
    case hostSceneUnavailable
    case hostControllerMissing
}

@MainActor
final class KadreSwiftUIConsumerTests: XCTestCase {

    /// Les controllers encore vivants de CE test — le filet ENTRE tests (miroir
    /// `KadreHostViewControllerTests.tearDown`) : toute session encore ouverte est fermée
    /// et attendue au tearDown, sinon un test fautif laisserait la réservation de scène
    /// posée et ferait cascader les tests SUIVANTS (`AlreadyInUse(Host)`).
    private var liveControllers: [KadreHostViewController] = []

    override func tearDown() async throws {
        for controller in liveControllers {
            await settle(controller)
        }
        liveControllers.removeAll()
        try await super.tearDown()
    }

    // MARK: — Helpers (miroirs Task 5)

    /// Attente bornée (poll 10 ms sur le MainActor — jamais de sommeil nu bloquant).
    private func waitUntil(
        timeout: Double = 20,
        _ condition: () -> Bool
    ) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() {
            if Date() > deadline { return false }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        return true
    }

    /// Un hosting SwiftUI de l'APP (code de production : `makeRootController`) posé dans une
    /// fenêtre NEUVE de la scène de l'app hôte — idiome Task 5 (une fenêtre par test,
    /// `isHidden = false` SANS makeKey). L'attach est controller-driven (`didMoveToWindow`
    /// du controller créé PAR LE WRAPPER dans `makeUIViewController`) : le test l'attend de
    /// façon bornée — jamais un échec instantané. L'antichambre (miroir Task 5) attend la
    /// session de LANCEMENT de l'app hôte (self-stop ~500 ms) : l'attach refusant
    /// `AlreadyInUse(Host)` de façon DÉFINITIVE, cette attente est la seule place honnête.
    @discardableResult
    private func makeHostedController(
        file: StaticString = #filePath,
        line: UInt = #line
    ) async throws -> (controller: KadreHostViewController, window: UIWindow, makeCountBefore: Int) {
        guard let scene = UIApplication.shared.connectedScenes.first(where: { $0 is UIWindowScene })
                as? UIWindowScene else {
            XCTFail("the host app scene is unavailable — the app did not finish launching",
                    file: file, line: line)
            throw KadreSwiftUIConsumerTestsError.hostSceneUnavailable
        }
        // L'ANTICHAMBRE (miroir `KadreHostViewControllerTests.makeAttachedController`).
        let store = ConsumerSceneStore.shared
        let launchGone = await waitUntil {
            store.sessionObject == nil || store.lastStateDescription == "Terminated"
        }
        XCTAssertTrue(launchGone,
                      "the launch session must be terminated before a wrapper attach; "
                          + "state=\(store.lastStateDescription)",
                      file: file, line: line)

        let swiftUIStore = ConsumerSwiftUIStore.shared
        let makeCountBefore = swiftUIStore.makeUIViewControllerCount
        let hosting = swiftUIStore.makeRootController(factory: HeldApplicationFactory())
        let window = UIWindow(windowScene: scene)
        window.rootViewController = hosting
        _ = hosting.view // construit l'arbre SwiftUI (makeUIViewController) — l'attach attendra le windowing
        window.isHidden = false

        let attached = await waitUntil {
            swiftUIStore.makeUIViewControllerCount == makeCountBefore + 1
                && swiftUIStore.hostController?.kadreSession != nil
        }
        XCTAssertTrue(attached,
                      "the wrapper-made controller must attach at the first valid moment; "
                          + "makeCount=\(swiftUIStore.makeUIViewControllerCount) "
                          + "expected=\(makeCountBefore + 1) "
                          + "attempts=\(swiftUIStore.hostController?.attachAttempts ?? -1) "
                          + "failure=\(swiftUIStore.hostController?.lastAttachFailureDescription ?? "-")",
                      file: file, line: line)
        guard let controller = swiftUIStore.hostController,
              controller.kadreSession != nil else {
            throw KadreSwiftUIConsumerTestsError.hostControllerMissing
        }
        liveControllers.append(controller)
        return (controller, window, makeCountBefore)
    }

    /// Le filet de sécurité ENTRE tests (miroir Task 5) : close explicite du controller PUIS
    /// attente bornée de la terminaison — la réservation de la scène est libérée pour le
    /// test suivant.
    private func settle(
        _ controller: KadreHostViewController,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        liveControllers.removeAll { $0 === controller }
        guard let handle = controller.kadreSession else { return }
        controller.close()
        let terminated = await waitUntil { handle.state.isTerminated }
        if !terminated {
            XCTFail("the session must terminate after close; state=\(handle.state)",
                    file: file, line: line)
        }
    }

    // MARK: — 1. uikit-swiftui-consumer-attaches-once

    /// uikit-swiftui-consumer-attaches-once — la scène SwiftUI (UIHostingController de
    /// l'APP) héberge le wrapper : la session attache (fixture bornée) et atteint Running ;
    /// une réévaluation du body — provoquée par le mécanisme RÉEL de SwiftUI (l'état
    /// observable de la racine change) — NE recrée PAS le controller (même instance `===`,
    /// compteur `makeUIViewControllerCount` inchangé) et NE re-attache PAS
    /// (`attachAttempts == 1`) ; requestStop VIA LE CONTROLLER → terminated.
    func testSwiftUIConsumerAttachesExactlyOnceAcrossBodyReevaluations() async throws {
        let (controller, _, makeCountBefore) = try await makeHostedController()
        let store = ConsumerSwiftUIStore.shared
        let handle = try XCTUnwrap(controller.kadreSession)

        XCTAssertEqual(controller.attachAttempts, 1,
                       "the wrapper must attach EXACTLY once at the first valid moment")
        XCTAssertTrue(store.hostController === controller,
                      "the store must hold THIS wrapper-made controller")

        // La session vit : Running (l'application held ne s'arrête jamais seule).
        let running = await waitUntil { handle.state == .running }
        XCTAssertTrue(running, "the session must reach Running; state=\(handle.state)")

        // LA RÉÉVALUATION — deux passes du mécanisme RÉEL de SwiftUI : l'état observable de
        // la racine (`@Published`) change, SwiftUI réévalue le body et repousse une mise à
        // jour au representable (`updateUIViewController` — contractuellement no-op). La
        // preuve POSITIVE que les réévaluations ont eu lieu : l'époque rendue du fixture.
        let epochBefore = store.lastRenderedEpoch
        store.forceBodyReevaluation()
        store.forceBodyReevaluation()
        let reevaluated = await waitUntil { store.lastRenderedEpoch >= epochBefore + 2 }
        XCTAssertTrue(reevaluated,
                      "the root body must have re-evaluated (lastRenderedEpoch="
                          + "\(store.lastRenderedEpoch) expected>=\(epochBefore + 2)) — "
                          + "without this the attaches-once proof would be vacuous")

        // L'INVARIANT §9 : le MÊME controller (identité), make appelé EXACTEMENT une fois
        // (une recréation remplacerait l'instance de la store), attach EXACTEMENT une fois,
        // la MÊME session.
        XCTAssertEqual(store.makeUIViewControllerCount, makeCountBefore + 1,
                       "a body re-evaluation must NEVER recreate the representable's controller")
        XCTAssertTrue(store.hostController === controller,
                      "the SAME controller instance must survive the body re-evaluations")
        XCTAssertEqual(controller.attachAttempts, 1,
                       "a body re-evaluation must never re-attach")
        XCTAssertTrue(controller.kadreSession === handle,
                      "the re-evaluation must not disturb the session — same handle")

        // requestStop VIA LE CONTROLLER → terminated (l'app tenue ne s'arrête jamais seule :
        // la raison ne peut être QUE hostRequested).
        controller.kadreSession?.requestStop()
        let terminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(terminated, "requestStop must terminate the session; state=\(handle.state)")
        XCTAssertEqual(handle.state, .terminated(.stopped(.hostRequested)),
                       "the held application cannot have requested the stop itself")

        // ... et la terminaison non plus ne recrée ni ne ré-attache.
        XCTAssertEqual(store.makeUIViewControllerCount, makeCountBefore + 1,
                       "the termination must not recreate the controller either")
        XCTAssertEqual(controller.attachAttempts, 1,
                       "the termination must not re-attach")

        await settle(controller)
    }

    // MARK: — 2. Ride-along Task 4-M3 : observeState inscrit APRÈS la terminaison

    /// uikit-facade-observe-state-inscribed-after-termination-delivers-terminated-once-then-silence
    /// — l'inscription d'observation APRÈS la terminaison (handle NON clos — la promesse
    /// pré-close §5 couvre cette inscription, contrairement au post-close déjà épinglé en
    /// Task 4) : la PREMIÈRE livraison est `terminated` (l'émission immédiate du snapshot
    /// courant), puis le SILENCE (le StateFlow terminal n'émet plus jamais).
    func testObserveStateInscribedAfterTerminationDeliversTerminatedOnceThenSilence() async throws {
        let (controller, _, _) = try await makeHostedController()
        let handle = try XCTUnwrap(controller.kadreSession)

        let running = await waitUntil { handle.state == .running }
        XCTAssertTrue(running, "the session must reach Running before the stop; state=\(handle.state)")

        // La terminaison D'ABORD (requestStop via le controller), l'inscription ENSUITE.
        controller.kadreSession?.requestStop()
        let terminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(terminated, "requestStop must terminate the session; state=\(handle.state)")
        XCTAssertEqual(handle.state, .terminated(.stopped(.hostRequested)),
                       "the held application cannot have requested the stop itself")

        var deliveries: [KadreSessionSnapshot] = []
        let observation = handle.observeState { snapshot in
            deliveries.append(snapshot)
        }
        XCTAssertFalse(deliveries.isEmpty,
                       "the observation must deliver the current snapshot immediately")
        XCTAssertEqual(deliveries.first, .terminated(.stopped(.hostRequested)),
                       "the FIRST delivery must be terminated when inscribed after "
                           + "termination; got \(deliveries.first.map(String.init(describing:)) ?? "nil")")

        // PUIS LE SILENCE : plus aucune livraison après `terminated` (borné — jamais un
        // échec instantané), et EXACTEMENT une livraison au total (l'émission immédiate ;
        // la ré-émission propre aux nouveaux collecteurs StateFlow est dédupliquée).
        let countAtTerminated = deliveries.count
        try await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertEqual(deliveries.count, countAtTerminated,
                       "nothing may be delivered after the terminated first delivery")
        XCTAssertEqual(deliveries.count, 1,
                       "exactly the terminated snapshot must be delivered; got \(deliveries.count)")

        observation.close()
        await settle(controller)
    }

    // MARK: — 3. Le chemin gated de lancement (env KADRE_CONSUMER_SWIFTUI)

    /// uikit-swiftui-consumer-gated-launch-scene-hosts-the-wrapper — le gate pose la fenêtre
    /// SwiftUI À LA PLACE du store UIKit : l'app de lancement héberge le wrapper (racine
    /// `UIHostingController`), l'attach du store ne passe JAMAIS (muettité prouvée), le
    /// controller du wrapper attache exactement une fois DANS la fenêtre de lancement, et
    /// requestStop → terminated. Run dédié : `TEST_RUNNER_KADRE_CONSUMER_SWIFTUI=1
    /// TEST_RUNNER_KADRE_CONSUMER_SWIFTUI_FACTORY=held` + `-only-testing` (mécanique phase
    /// 1 `DriverSceneRequest`). Skippé ailleurs : le gate est off dans les lancements
    /// standard — un skip d'environnement, jamais une assertion affaiblie.
    func testLaunchGatedSwiftUISceneHostsTheWrapperAndAttachesOnce() async throws {
        guard ConsumerSwiftUIScene.launchRequested else {
            throw XCTSkip("this proof runs only in the KADRE_CONSUMER_SWIFTUI-gated launch")
        }
        let store = ConsumerSwiftUIStore.shared

        // La fenêtre gated est posée par le délégué de scène au lancement ; l'attach est
        // controller-driven : attente bornée (le test peut démarrer avant la pose).
        let installed = await waitUntil { store.window != nil && store.hostController != nil }
        XCTAssertTrue(installed,
                      "the gated launch must install the SwiftUI window and make the wrapper's controller")
        guard let controller = store.hostController else {
            throw KadreSwiftUIConsumerTestsError.hostControllerMissing
        }
        liveControllers.append(controller)

        let attached = await waitUntil { controller.kadreSession != nil }
        XCTAssertTrue(attached,
                      "the wrapper's controller must attach in the gated launch window; "
                          + "attempts=\(controller.attachAttempts) "
                          + "failure=\(controller.lastAttachFailureDescription ?? "-")")
        XCTAssertEqual(controller.attachAttempts, 1,
                       "the gated launch must attach EXACTLY once")
        XCTAssertTrue(controller.view.window === store.window,
                      "the wrapper's controller view must live in the gated launch window")

        // Le chemin UIKit du store est BIEN resté muet (une seule session par scène).
        XCTAssertNil(ConsumerSceneStore.shared.sessionObject,
                     "the UIKit store path must never attach under the SwiftUI gate")
        XCTAssertNil(ConsumerSceneStore.shared.attachFailureDescription,
                     "the UIKit store path must never even try to attach under the SwiftUI gate")

        // requestStop → terminated (factory held : la raison ne peut être QUE hostRequested).
        let handle = try XCTUnwrap(controller.kadreSession)
        controller.kadreSession?.requestStop()
        let terminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(terminated, "requestStop must terminate the session; state=\(handle.state)")
        XCTAssertEqual(handle.state, .terminated(.stopped(.hostRequested)),
                       "the held application cannot have requested the stop itself")

        await settle(controller)
    }
}
