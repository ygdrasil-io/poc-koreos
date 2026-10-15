import XCTest
import GameController
import KadreUikit
#if canImport(KadreConsumerIos)
@testable import KadreConsumerIos
#elseif canImport(KadreConsumerTvOs)
@testable import KadreConsumerTvOs
#endif

#if os(tvOS)
/*
 * Task 7 — la preuve de coexistence tvOS du consumer : des contrôles natifs FOCUSABLES
 * (UIControl) autour de la surface Kadre, dans LA MÊME hiérarchie, le focus restant au
 * MOTEUR SYSTÈME (la Siri Remote pilote le focus UIKit du host — Kadre n'a AUCUN binding
 * télécommande : registre uikit.md, lignes dédiées non livrées/hors périmètre v1).
 *
 * Les preuves, sur session RÉELLE pour la seconde (idiome `KadreSwiftUIConsumerTests` :
 * fenêtre propre au test sur la scène de l'app hôte, factory tenue, antichambre de
 * lancement, filet `settle`/`tearDown` borné) :
 *  1. la STRUCTURE de focus — trois UIButtons `canBecomeFocused`, la surface Kadre
 *     (`KadreHostViewController.view` — la surfaceView §5) `canBecomeFocused == false`
 *     et JAMAIS dans `preferredFocusEnvironments` : le câblage de focus est RÉEL, la
 *     surface n'est pas un participant (l'honnêteté est une ASSERTION, pas une phrase) ;
 *  2. le chemin BCK-024 PAR LE CONSUMER — l'attach de la scène de coexistence
 *     (controller-driven, exactement une fois), puis `openTextInput` atteint depuis Swift
 *     via `ConsumerTextInputBridge` (le module applicatif Kotlin du consumer — les
 *     artefacts publiés, jamais project(...)) : la session texte s'ouvre `Active`, se
 *     ferme, et le requestStop passe par le handle. Le clavier réel et le BOUT de la Siri
 *     Remote restent des charters manuels sur Apple TV physique (DEFERRED-AUDIT).
 *
 * Le BOUT réel n'est PAS prouvé ici — c'est la décision fermée du registre (`XCUIRemote`
 * est de l'UI-testing, jamais synthétisable in-process) ; ce que ce bundle prouve est la
 * COEXISTENCE : un moteur de focus système qui ne voit QUE les contrôles natifs, avec la
 * surface Kadre vivante à côté dans la même hiérarchie.
 */

/// La raison d'échec typée du fixture (miroir `KadreSwiftUIConsumerTestsError`).
private enum ConsumerTvOsCoexistenceTestsError: Error {
    case hostSceneUnavailable
    case hostControllerMissing
}

@MainActor
final class ConsumerTvOsCoexistenceTests: XCTestCase {

    /// Les controllers encore vivants de CE test — le filet ENTRE tests (miroir
    /// `KadreSwiftUIConsumerTests.tearDown`) : toute session encore ouverte est fermée et
    /// attendue, sinon un test fautif laisserait la réservation de scène posée.
    private var liveControllers: [KadreHostViewController] = []

    override func tearDown() async throws {
        for controller in liveControllers {
            await settle(controller)
        }
        liveControllers.removeAll()
        try await super.tearDown()
    }

    // MARK: — Helpers (miroirs KadreSwiftUIConsumerTests)

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

    /// Le filet de sécurité ENTRE tests : close explicite du controller PUIS attente
    /// bornée de la terminaison — la réservation de la scène est libérée pour le suivant.
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

    /// La scène de l'app hôte — l'idiome Task 5 (une fenêtre par test, sur LA scène).
    private func hostScene(
        file: StaticString = #filePath,
        line: UInt = #line
    ) throws -> UIWindowScene {
        guard let scene = UIApplication.shared.connectedScenes.first(where: { $0 is UIWindowScene })
                as? UIWindowScene else {
            XCTFail("the host app scene is unavailable — the app did not finish launching",
                    file: file, line: line)
            throw ConsumerTvOsCoexistenceTestsError.hostSceneUnavailable
        }
        return scene
    }

    // MARK: — 1. La STRUCTURE de focus (honnêteté en assertion)

    /// uikit-tvos-consumer-coexistence-focus-belongs-to-native-controls — la STRUCTURE de
    /// focus de la scène de coexistence : trois UIControls natifs, la surfaceView Kadre
    /// (`KadreHostViewController.view` — la surfaceView §5) dans LA MÊME hiérarchie mais
    /// JAMAIS dans les environnements de focus préférés. Hors fenêtre : aucun attach, et
    /// `canBecomeFocused` n'y est PAS lisible (hors hiérarchie de fenêtre, UIKit répond
    /// false pour TOUT le monde — la focusabilité réelle est prouvée dans le test
    /// windowed ci-dessous, où elle a un sens).
    func testCoexistenceFocusBelongsToNativeControlsAndNeverTheSurface() throws {
        let controller = ConsumerTvOsCoexistenceScene.makeRootController()
        _ = controller.view // construit la hiérarchie (viewDidLoad) — hors fenêtre, l'attach n'a PAS lieu

        // La coexistence LITTÉRALE : la surface Kadre (la view du controller hôte, §5)
        // vit dans la MÊME hiérarchie que les contrôles natifs.
        let surfaceView = try XCTUnwrap(controller.hostController?.view)
        XCTAssertTrue(surfaceView.isDescendant(of: controller.view),
                      "the Kadre surface must coexist in the coexistence hierarchy")

        // Les participants au focus de la scène sont les contrôles natifs — des UIControl
        // (le moteur de focus SYSTÈME navigue les UIControl ; aucun override du sample).
        for button in [controller.textButton, controller.refreshButton, controller.stopButton] {
            let focusable = try XCTUnwrap(button)
            XCTAssertTrue(focusable is UIControl,
                          "native controls must be UIControls (system focus engine participants)")
        }

        // LA SURFACE N'EST PAS UN PARTICIPANT — l'honnêteté télécommande en assertion :
        // elle n'entre JAMAIS dans les environnements de focus préférés de la scène, et
        // ce n'est structurellement pas un UIControl.
        XCTAssertFalse(surfaceView is UIControl,
                       "the Kadre surface is a plain UIView — no focus participant (no Kadre remote binding)")
        XCTAssertEqual(controller.preferredFocusEnvironments.count, 3,
                       "the scene must prefer exactly the three native controls")
        for environment in controller.preferredFocusEnvironments {
            XCTAssertFalse((environment as? UIView)?.isDescendant(of: surfaceView) == true,
                           "no preferred focus environment may live inside the Kadre surface")
            XCTAssertTrue(environment is UIControl,
                          "preferred focus environments must be the native controls")
        }

        // Hors fenêtre : l'attach controller-driven n'a PAS eu lieu (le moment valide
        // n'est pas venu — la machine exactement-une-fois reste en attente).
        XCTAssertNil(controller.hostController?.kadreSession,
                     "no window means no attach — the valid moment has not come")
    }

    // MARK: — 2. Le chemin BCK-024 PAR LE CONSUMER (session réelle)

    /// uikit-tvos-consumer-coexistence-attaches-and-opens-text-through-consumer-bridge —
    /// la scène de coexistence attache exactement une fois (controller-driven, factory
    /// tenue RÉACTIVE), atteint Running, les faits de surface se capturent au premier
    /// moment où elle existe, puis la session texte s'ouvre par le chemin CONSUMER
    /// (`ConsumerTextInputBridge` — les artefacts publiés, jamais project(...)) : `Active`
    /// au pont, close idempotent, requestStop VIA LE HANDLE → terminated hostRequested.
    /// LA FOCUSABILITÉ RÉELLE s'asserte ICI (fenêtre posée — hors fenêtre UIKit répond
    /// false pour tout le monde) : les trois contrôles natifs `canBecomeFocused`, la
    /// surface Kadre jamais.
    func testCoexistenceAttachesAndOpensTextThroughConsumerBridge() async throws {
        // L'ANTICHAMBRE (miroir Task 5) : la session de LANCEMENT de l'app hôte (self-stop
        // ~500 ms) doit être terminée — l'attach refuserait `AlreadyInUse(Host)`.
        let store = ConsumerSceneStore.shared
        let launchGone = await waitUntil {
            store.sessionObject == nil || store.lastStateDescription == "Terminated"
        }
        XCTAssertTrue(launchGone,
                      "the launch session must be terminated before the coexistence attach; "
                          + "state=\(store.lastStateDescription)")

        // La fenêtre propre au test — la racine est la scène de coexistence DE L'APP (le
        // code de production), l'attach est controller-driven (l'enfant `KadreHostViewController`).
        let scene = try hostScene()
        let controller = ConsumerTvOsCoexistenceScene.makeRootController()
        let window = UIWindow(windowScene: scene)
        window.rootViewController = controller
        _ = controller.view
        window.isHidden = false
        defer { window.isHidden = true }

        guard let hostController = controller.hostController else {
            throw ConsumerTvOsCoexistenceTestsError.hostControllerMissing
        }
        liveControllers.append(hostController)

        let attached = await waitUntil { hostController.kadreSession != nil }
        XCTAssertTrue(attached,
                      "the coexistence child must attach at the first valid moment; "
                          + "attempts=\(hostController.attachAttempts) "
                          + "failure=\(hostController.lastAttachFailureDescription ?? "-")")
        XCTAssertEqual(hostController.attachAttempts, 1,
                       "the coexistence scene must attach EXACTLY once")
        guard let handle = hostController.kadreSession else {
            throw ConsumerTvOsCoexistenceTestsError.hostControllerMissing
        }
        let running = await waitUntil { handle.state == .running }
        XCTAssertTrue(running, "the session must reach Running; state=\(handle.state)")

        // LA FOCUSABILITÉ RÉELLE (fenêtre posée) : les trois contrôles natifs sont
        // focusables PAR LE MOTEUR SYSTÈME, la surface Kadre ne l'est JAMAIS (aucune
        // binding télécommande — l'honnêteté en assertion, dans le seul contexte où
        // `canBecomeFocused` a un sens).
        for button in [controller.textButton, controller.refreshButton, controller.stopButton] {
            XCTAssertTrue(button?.canBecomeFocused == true,
                          "the native control \(button?.currentTitle ?? "?") must be focusable "
                              + "in a windowed hierarchy")
        }
        let surfaceView = try XCTUnwrap(controller.hostController?.view)
        XCTAssertFalse(surfaceView.canBecomeFocused,
                       "the Kadre surface must NOT be a focus participant (no Kadre remote binding)")

        // Les faits de surface de l'application tenue RÉACTIVE — capturés au premier
        // moment où la surface existe (le pont de saisie les lit) ; attente bornée.
        let factsCaptured = await waitUntil { ConsumerFixture.shared.primarySurface != nil }
        XCTAssertTrue(factsCaptured,
                      "the reactive held application must capture the primary surface; "
                          + "runs=\(ConsumerFixture.shared.runCount)")

        // LA SAISIE SYSTÈME par le chemin consumer — `SurfaceInput.openTextInput` (Kotlin,
        // suspend) en completion handler Swift (DEUX paramètres : résultat, erreur — le
        // miroir `awaitTermination`). Le pont APLATIT le refus en description ; le succès
        // répond nil.
        var openFailure: String? = "completion not called"
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            ConsumerTextInputBridge.shared.openTextInput { failure, _ in
                openFailure = failure
                continuation.resume()
            }
        }
        XCTAssertNil(openFailure,
                     "openTextInput must succeed through the consumer bridge: \(openFailure ?? "-")")
        let active = await waitUntil {
            ConsumerTextInputBridge.shared.stateDescription.value as? String == "Active"
        }
        XCTAssertTrue(active,
                      "the text input session must be Active through the bridge; state="
                          + "\(ConsumerTextInputBridge.shared.stateDescription.value ?? "nil")")
        // La session Kadre, elle, vit toujours (la saisie ne dérange pas le lifecycle —
        // le couplage focus→Suspend n'existe pas côté UIKit, ligne BCK-024 du registre).
        XCTAssertEqual(handle.state, .running)

        // Le close du pont — idempotent, l'état retombe à Closed.
        ConsumerTextInputBridge.shared.closeTextInput()
        let closed = await waitUntil {
            ConsumerTextInputBridge.shared.stateDescription.value as? String == "Closed"
        }
        XCTAssertTrue(closed, "the text input session must fall back to Closed after close")

        // requestStop VIA LE HANDLE → terminated (la factory tenue ne s'arrête jamais
        // seule : la raison ne peut être QUE hostRequested).
        hostController.kadreSession?.requestStop()
        let terminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(terminated, "requestStop must terminate the session; state=\(handle.state)")
        XCTAssertEqual(handle.state, .terminated(.stopped(.hostRequested)),
                       "the held application cannot have requested the stop itself")

        await settle(hostController)
    }

    // MARK: — 3. La classification manette conditionnelle par matériel (BCK-027 côté app)

    /// uikit-tvos-consumer-coexistence-classifies-controllers-per-hardware — la
    /// classification CONDITIONNELLE PAR MATÉRIEL du sample : la RÈGLE de Kadre (BCK-027 :
    /// `extendedGamepad` non-nil = manette complète, sinon `microGamepad` = micro-only)
    /// appliquée aux `GCController` par l'APP (aucune entrée = aucun périphérique — sur
    /// simulateur l'inventaire est vide ; le matériel réel se valide sur Apple TV, charter).
    func testCoexistenceClassifiesControllersPerHardware() {
        let description = ConsumerTvOsCoexistenceController.classifyControllers()
        let controllers = GCController.controllers()
        if controllers.isEmpty {
            XCTAssertEqual(description, "périphériques: aucun",
                           "an empty inventory must be stated as such (simulator truth)")
        } else {
            XCTAssertTrue(description.hasPrefix("périphériques: "),
                          "each controller must be classified: \(description)")
        }
    }
}
#endif
