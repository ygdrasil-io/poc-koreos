import XCTest
import KadreUikit
#if canImport(KadreConsumerIos)
@testable import KadreConsumerIos
#elseif canImport(KadreConsumerTvOs)
@testable import KadreConsumerTvOs
#endif

/// Task 5 — le controller hôte (`INTEROP-EXPORTS.md` §5 l.140, verbatim) :
/// « KadreHostViewController attache exactement une fois lorsque sa view appartient à une
/// UIWindow et une UIWindowScene ; il utilise cette view comme surfaceView, ne la remplace
/// pas et ne crée aucun layout. Un retrait temporaire ne ferme pas la scène ; deinit,
/// sceneDidDisconnect ou close explicite ferment la session. »
///
/// Les preuves, toutes sur session RÉELLE (le controller attache LUI-MÊME, par
/// `didMoveToWindow`, sur une fenêtre neuve de la scène de l'app hôte — pattern Task 4 :
/// une session à la fois, la réservation de scène libérée à la terminaison, d'où le settle
/// final de chaque test et le filet `settle` borné) :
///  1. ajout à une fenêtre → attach AU PREMIER MOMENT VALIDE, UNE fois
///     (`attachAttempts == 1`), session non-nil qui atteint Running (l'application held ne
///     s'arrête jamais seule — contrairement à la self-stop de `ConsumerApplication`) ;
///  2. retrait de superview PUIS re-add → PAS de second attach, MÊME handle (identité —
///     l'id exporté est rédigé constant par Kotlin « SessionId(<redacted>) », l'égalité
///     d'ids serait vide de sens : la preuve passe par `===`) ;
///  3. `close()` idempotent → Terminated(.stopped(.hostRequested)), re-add APRÈS close →
///     rien (le re-attach est interdit à vie) ;
///  4. notification `UIScene.didDisconnectNotification` (object = la scène attachée —
///     postée synthétiquement, patron prouvé phase 1
///     `KadreUikitAttachTests.testDisconnectDuringStopTerminatesExactlyOnce`) → session
///     fermée ;
///  5. deinit → session fermée (le test capture le handle AVANT de lâcher le controller ;
///     le weak tracking prouve la désallocation — la view ne retient JAMAIS son
///     controller) ;
///  6. la view du controller EST la surfaceView : le bail natif de la session
///     (`KadreUikitSurfaceDriver.leaseProbe`, la sonde phase 2) remet la vue du port de
///     lease — la surfaceView passée à l'attach — et elle doit `isEqual` LA VIEW DU
///     CONTROLLER (`===` ne pont pas l'identité Obj-C, preuve O3 phase 0 : isEqual est LA
///     comparaison). Les faits applicatifs (la surface primaire capturée par
///     `HeldApplication`) sont lus avec la sentinelle de fraîcheur `runCount` — jamais un
///     reliquat d'une session précédente ;
///  7. un retrait temporaire NE ferme PAS la session (roadmap l.227) : non-nil, toujours
///     Running, même handle au re-add.
///
/// NB de nommage : ce bundle importe DEUX modules qui exportent des noms communs — le
/// fichier n'écrit jamais ces noms nus pour le KOTLIN (`KadreUikit.X` partout dans la
/// façade), les noms nus restant les types du module lié (`ConsumerFixture`,
/// `HeldApplicationFactory`, `KadreUikitSurfaceDriver`) ou de l'app (`@testable`).
private enum KadreHostViewControllerTestsError: Error {
    case hostSceneUnavailable
}

@MainActor
final class KadreHostViewControllerTests: XCTestCase {

    /// Les controllers encore vivants de CE test — le filet ENTRE tests (miroir
    /// `KadreUikitAttachTests.tearDown`) : toute session encore ouverte est fermée et
    /// attendue au tearDown, sinon un test fautif laisserait la réservation de scène posée
    /// et ferait cascader les tests SUIVANTS (`AlreadyInUse(Host)`).
    private var liveControllers: [KadreHostViewController] = []

    override func tearDown() async throws {
        for controller in liveControllers {
            await settle(controller)
        }
        liveControllers.removeAll()
        try await super.tearDown()
    }

    // MARK: — Helpers

    /// Un controller posé dans une fenêtre NEUVE de la scène de l'app hôte (idiome Task 4 :
    /// une fenêtre par test, `isHidden = false` SANS makeKey — le key reste à la fenêtre
    /// primaire de l'app). L'attach est controller-driven (`didMoveToWindow`) : le test
    /// l'attend de façon bornée — jamais un échec instantané. La sentinelle de fraîcheur
    /// (`runCount` de ConsumerFixture) est lue AVANT la création — les faits applicatifs
    /// attendus ensuite sont CEUX DE CETTE session.
    @discardableResult
    private func makeAttachedController(
        file: StaticString = #filePath,
        line: UInt = #line
    ) async throws -> (controller: KadreHostViewController, window: UIWindow, runsBefore: Int) {
        guard let scene = ConsumerSceneStore.shared.window?.windowScene else {
            XCTFail("the host app scene is unavailable — the app did not finish launching",
                    file: file, line: line)
            throw KadreHostViewControllerTestsError.hostSceneUnavailable
        }
        // L'ANTICHAMBRE : la session de LANCEMENT de l'app hôte (self-stop ~500 ms) peut
        // encore tenir la réservation de scène quand cette suite démarre seule
        // (-only-testing). L'attach controller-driven refuse `AlreadyInUse(Host)` de façon
        // DÉFINITIVE (le controller n'insiste jamais) — l'antichambre est donc la seule
        // place honnête pour l'attendre, bornée (le poll de la store tourne sur le main).
        let store = ConsumerSceneStore.shared
        let launchGone = await waitUntil {
            store.sessionObject == nil || store.lastStateDescription == "Terminated"
        }
        XCTAssertTrue(launchGone,
                      "the launch session must be terminated before a controller attach; "
                          + "state=\(store.lastStateDescription)",
                      file: file, line: line)
        let runsBefore = Int(ConsumerFixture.shared.runCount)
        let controller = KadreHostViewController(applicationFactory: HeldApplicationFactory())
        liveControllers.append(controller)
        let window = UIWindow(windowScene: scene)
        window.addSubview(controller.view)
        window.isHidden = false

        let attached = await waitUntil {
            controller.kadreSession != nil
        }
        XCTAssertTrue(attached,
                      "the controller must attach at the first valid moment; "
                          + "attempts=\(controller.attachAttempts) "
                          + "failure=\(controller.lastAttachFailureDescription ?? "-")",
                      file: file, line: line)
        return (controller, window, runsBefore)
    }

    /// Attente bornée (poll 10 ms sur le MainActor — jamais de sommeil nu bloquant ; les
    /// blocs main-queue des notifications sont livrés pendant les sommeils du MainActor).
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

    /// Le filet de sécurité ENTRE tests (miroir `KadreUikitAttachTests.tearDown`) : close
    /// explicite du controller PUIS attente bornée de la terminaison — la réservation de la
    /// scène est libérée pour le test suivant ; le test fautif reste le seul à porter sa
    /// défaillance (sinon cascade `AlreadyInUse(Host)`). Le controller réglé quitte le
    /// registre du tearDown.
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

    // MARK: — (a) L'attach au premier moment valide, EXACTEMENT une fois

    /// uikit-facade-controller-attaches-once-at-valid-moment — l'ajout à une fenêtre (scène
    /// déjà Active : le moment valide est immédiat) attache UNE fois ; la session vit et
    /// atteint Running.
    func testAttachesExactlyOnceWhenTheViewJoinsAWindowWithAScene() async throws {
        let (controller, _, _) = try await makeAttachedController()

        XCTAssertEqual(controller.attachAttempts, 1,
                       "the controller must attach EXACTLY once at the first valid moment")
        let handle = try XCTUnwrap(controller.kadreSession, "kadreSession must be set after attach")

        // La session vit : Running (l'application held ne demande jamais l'arrêt elle-même).
        let running = await waitUntil { handle.state == .running }
        XCTAssertTrue(running, "the session must reach Running; state=\(handle.state)")

        await settle(controller)
    }

    // MARK: — (b) Retrait + re-add : PAS de second attach

    /// uikit-facade-controller-remove-re-add-never-reattaches — le retrait puis le re-add
    /// de la view ne ré-attachent PAS : compteur d'appels réels inchangé (1), MÊME handle
    /// (`===`), session toujours vivante.
    func testRemoveAndReAddDoesNotAttachAgain() async throws {
        let (controller, window, _) = try await makeAttachedController()
        let handle = try XCTUnwrap(controller.kadreSession)

        controller.view.removeFromSuperview()
        window.addSubview(controller.view)

        XCTAssertEqual(controller.attachAttempts, 1,
                       "a re-add must never re-attach (attach EXACTLY once per controller)")
        XCTAssertTrue(controller.kadreSession === handle,
                      "the session must be unchanged — the same handle")

        // La session n'a jamais été dérangée : elle atteint Running normalement.
        let running = await waitUntil { handle.state == .running }
        XCTAssertTrue(running, "the session must survive the remove/re-add cycle; state=\(handle.state)")

        await settle(controller)
    }

    // MARK: — (c) Le close explicite : Terminated, et le re-add ne fait RIEN

    /// uikit-facade-controller-explicit-close-terminates-and-never-reattaches — `close()`
    /// (idempotent : deux appels) ferme la session → `.terminated(.stopped(.hostRequested))`
    /// (l'application held ne s'arrête jamais seule : la raison ne peut être que celle du
    /// host) ; le re-add APRÈS close ne ré-attache PAS (pas de nouveau compteur, même
    /// handle, toujours terminé).
    func testExplicitCloseTerminatesAndReAddDoesNotReattach() async throws {
        let (controller, window, _) = try await makeAttachedController()
        let handle = try XCTUnwrap(controller.kadreSession)

        controller.close()
        controller.close() // idempotent — ni crash, ni effet supplémentaire

        let terminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(terminated, "close() must close the session; state=\(handle.state)")
        XCTAssertEqual(handle.state, .terminated(.stopped(.hostRequested)),
                       "the explicit close must stop the session host-requested")

        controller.view.removeFromSuperview()
        window.addSubview(controller.view)

        XCTAssertEqual(controller.attachAttempts, 1,
                       "a re-add after close must never re-attach")
        XCTAssertTrue(controller.kadreSession === handle,
                      "the terminated handle stays — nothing is hidden")
        XCTAssertEqual(handle.state, .terminated(.stopped(.hostRequested)),
                       "the re-add must not touch the closed session")

        await settle(controller)
    }

    // MARK: — (d) La notification sceneDidDisconnect ferme la session

    /// uikit-facade-controller-scene-did-disconnect-closes — la notification réelle
    /// `UIScene.didDisconnectNotification`, postée avec object = LA scène attachée (poste
    /// synthétique, patron prouvé phase 1), ferme la session du controller.
    func testSceneDidDisconnectNotificationClosesTheSession() async throws {
        let (controller, _, _) = try await makeAttachedController()
        guard let scene = ConsumerSceneStore.shared.window?.windowScene else {
            return XCTFail("the host app scene disappeared")
        }
        let handle = try XCTUnwrap(controller.kadreSession)

        NotificationCenter.default.post(name: UIScene.didDisconnectNotification, object: scene)

        let terminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(terminated,
                      "the scene-did-disconnect routing must close the session; state=\(handle.state)")
        XCTAssertEqual(controller.attachAttempts, 1,
                       "the disconnect must close, not re-attach")

        // Le close explicite après coup : idempotent — ni crash, ni effet supplémentaire.
        controller.close()
        await settle(controller)
    }

    // MARK: — (e) Le deinit ferme la session

    /// uikit-facade-controller-deinit-closes-the-session — le lâcher du controller (sa view
    /// ne le retient JAMAIS) exécute son deinit, qui ferme la session : le weak tracking
    /// prouve la désallocation, le handle capturé AVANT prouve la terminaison.
    func testDeinitClosesTheSession() async throws {
        var handle: KadreSessionHandle?
        weak var weakController: KadreHostViewController?

        do {
            let (controller, _, _) = try await makeAttachedController()
            handle = controller.kadreSession
            weakController = controller
            // Le controller quitte le registre du tearDown ICI — sinon CE registre (une
            // référence forte) l'empêcherait de se désallouer au sortir du scope.
            liveControllers.removeAll { $0 === controller }
        } // la dernière référence forte du controller meurt ICI

        let gone = await waitUntil(timeout: 5) { weakController == nil }
        XCTAssertTrue(gone, "the controller must deallocate once the test releases it")

        let captured = try XCTUnwrap(handle)
        let terminated = await waitUntil { captured.state.isTerminated }
        XCTAssertTrue(terminated,
                      "deinit must close the session; state=\(captured.state)")
    }

    // MARK: — (f) La view du controller EST la surfaceView

    /// uikit-facade-controller-view-is-the-surface-view — la preuve d'identité par le bail
    /// natif de la session (sonde phase 2) : la vue remise par le port de lease — la
    /// surfaceView passée à l'attach — doit `isEqual` LA VIEW DU CONTROLLER. Les faits sont
    /// CEUX de cette session (sentinelle `runCount`).
    func testControllerViewIsTheAttachedSurfaceView() async throws {
        let (controller, _, runsBefore) = try await makeAttachedController()

        let fresh = await waitUntil {
            Int(ConsumerFixture.shared.runCount) > runsBefore
                && ConsumerFixture.shared.primarySurface != nil
        }
        XCTAssertTrue(fresh, "the held application must have captured THIS session's primary surface; "
            + "runs=\(ConsumerFixture.shared.runCount) expected>\(runsBefore)")

        let surface = try XCTUnwrap(ConsumerFixture.shared.primarySurface)
        let view = try XCTUnwrap(controller.view)
        let probe = KadreUikitSurfaceDriver.shared.leaseProbe(surface: surface, expectedView: view)
        XCTAssertNil(probe.threw, "the lease path must not throw: \(probe.threw ?? "")")
        XCTAssertTrue(probe.granted,
                      "the lease must be granted: \(probe.grantedFailureDescription ?? "-")")
        XCTAssertTrue(probe.grantedViewMatches,
                      "the leased view must isEqual the controller's own view — "
                          + "the controller's view IS the surfaceView")

        await settle(controller)
    }

    // MARK: — (g) Le retrait temporaire NE ferme PAS la session

    /// uikit-facade-controller-temporary-removal-keeps-the-session — la view quitte la
    /// fenêtre : la session reste non-nil et Running (roadmap l.227 — un retrait temporaire
    /// ne ferme pas la scène), et le re-add retrouve LE MÊME handle sans second attach.
    func testTemporaryRemovalDoesNotCloseTheSession() async throws {
        let (controller, window, _) = try await makeAttachedController()
        let handle = try XCTUnwrap(controller.kadreSession)

        let running = await waitUntil { handle.state == .running }
        XCTAssertTrue(running, "the session must reach Running before the removal; state=\(handle.state)")

        // LE RETRAIT TEMPORAIRE — la session ne bouge pas.
        controller.view.removeFromSuperview()
        try? await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertNotNil(controller.kadreSession,
                        "the session must survive a temporary removal")
        XCTAssertEqual(handle.state, .running,
                       "a removal must not stop the session; state=\(handle.state)")

        // Le re-add : toujours rien de neuf — même handle, même session Running.
        window.addSubview(controller.view)
        XCTAssertEqual(controller.attachAttempts, 1,
                       "the re-add must not re-attach")
        XCTAssertTrue(controller.kadreSession === handle,
                      "the re-add must keep the same session")
        XCTAssertEqual(handle.state, .running,
                       "the session must still be Running after the re-add; state=\(handle.state)")

        await settle(controller)
    }
}

// NB : le `isTerminated` de lisibilité est celui déjà déclaré par `KadreSessionHandleTests`
// (interne au module — un seul dans le bundle, toute re-déclaration serait un doublon).
