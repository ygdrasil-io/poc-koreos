import XCTest
import UIKit
import KadreUikit
@testable import KadreUikitDriverIos

/// Scénarios multi-scènes corrélés du provider (iOS UNIQUEMENT — ce fichier n'est compilé que
/// dans la cible de tests iOS ; le multi-scène est refusé par l'idiome téléphone et par la
/// topologie tvOS). Extension de `KadreUikitWindowRequestTests` : les noms figés restent dans
/// la même classe pour l'énumération XCTest.
///
/// Mécanique provider (règle contrôleur phase 1 adaptée) : chaque `requestWindow` admis
/// appelle `DriverWindowSceneProvider.open` — UNE activation à la fois (sérialisée, la
/// suivante part à la connexion de la précédente) ; la scène connectée est corrélée FIFO à sa
/// demande (registre `correlation`), la résolution attend la sortie d'Unattached (ForegroundInactive
/// suffit — preuve phase 1) puis `resolver.resolve` — l'adaptateur attache la session enfant
/// par le chemin ordinaire. Une paire « concurrente » est donc deux demandes pendantes EN
/// MÊME TEMPS, activées en série, chaque corrélation aboutissant à SA scène.
extension KadreUikitWindowRequestTests {

    // MARK: - Helpers provider-driven

    /// La poignée de test d'une session enfant : la scène qui la porte (fixture), son
    /// enregistrement de faits (recherché PAR IDENTITÉ de session), et la trace de résolution.
    struct ChildHandle {
        let fixture: SceneFixture
        let record: DriverSessionRecord
        let resolved: DriverWindowSceneProvider.ResolvedCall
    }

    /// Attend la n-ième résolution du provider, l'outcome `OpenedInNewSession` de la demande
    /// et les faits de la session enfant (canal `sessionRecords`, identité `isEqual`).
    func waitForChild(
        _ request: any WindowRequest,
        resolvedIndex: Int,
        timeout: TimeInterval = 30,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> ChildHandle {
        let provider = DriverWindowSceneProvider.shared
        let resolved = try pumpUntil(timeout: timeout, file: file, line: line) {
            provider.resolvedCalls.count >= resolvedIndex + 1
        }
        XCTAssertTrue(resolved, "provider resolution #\(resolvedIndex) never happened in \(timeout)s — "
            + "activationErrors=\(provider.activationErrors) timeouts=\(provider.resolutionTimeouts) "
            + "system=\(DriverTrace.systemSnapshot()) trace=\(DriverTrace.recent())", file: file, line: line)
        let call = try XCTUnwrap(provider.resolvedCalls.count > resolvedIndex ? provider.resolvedCalls[resolvedIndex] : nil,
                                 file: file, line: line)

        XCTAssertTrue(call.requestId.isEqual(request.id),
                      "the resolved requestId must be the request's own", file: file, line: line)
        let opened = try XCTUnwrap(awaitOutcome(request, file: file, line: line) as? WindowRequestOutcomeOpenedInNewSession,
                                   "expected OpenedInNewSession, got the outcome of request \(request.id)",
                                   file: file, line: line)
        let recordKnown = try pumpUntil(timeout: 15, file: file, line: line) {
            DriverFixture.shared.sessionRecord(sessionId: opened.sessionId) != nil
        }
        XCTAssertTrue(recordKnown, "no session record for child sessionId=\(opened.sessionId)", file: file, line: line)
        let record = try XCTUnwrap(DriverFixture.shared.sessionRecord(sessionId: opened.sessionId), file: file, line: line)
        let fixture = try XCTUnwrap(DriverFixture.shared.fixture(for: call.scene), file: file, line: line)
        return ChildHandle(fixture: fixture, record: record, resolved: call)
    }

    /// Stop d'une session enfant par le VRAI nom de notification de déconnexion (routage par
    /// identité — frontière SDK que l'adaptateur consomme ; mécanique rulée phase 1) : la
    /// scène enfant se déconnecte → détach → terminaison. L'app enfant coopère sur SON propre
    /// lifecycle détaché (DriverKadreApplication.cooperate, canal sessionRecords).
    func stopChild(_ handle: ChildHandle, timeout: TimeInterval = 15, file: StaticString = #filePath, line: UInt = #line) throws {
        NotificationCenter.default.post(name: UIScene.didDisconnectNotification, object: handle.fixture.scene)
        let detached = try pumpUntil(timeout: timeout, file: file, line: line) {
            handle.record.lifecycleAttachment() === AttachmentState.detached
        }
        XCTAssertTrue(detached, "child session never detached after scene disconnect — \(handle.record.summary)",
                      file: file, line: line)
        let completed = try pumpUntil(timeout: 10, file: file, line: line) { handle.record.applicationCompleted }
        XCTAssertTrue(completed, "child application never cooperated — \(handle.record.summary)", file: file, line: line)
    }

    /// Les faits auto-cohérents d'une session enfant vivante : surface primaire = surface de
    /// SA fenêtre primaire (id + instance).
    func assertChildFactsCoherent(_ handle: ChildHandle, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(handle.record.primarySurfaceIdMatchesWindowSurfaceId,
                      "child primary surface id must match its window surface — \(handle.record.summary)",
                      file: file, line: line)
        XCTAssertTrue(handle.record.primarySurfaceIsPrimaryWindowSurface,
                      "child scope primarySurface must be its window's surface instance — \(handle.record.summary)",
                      file: file, line: line)
        XCTAssertNotNil(handle.record.lifecycleSnapshot(), file: file, line: line)
    }

    // MARK: - Scénarios (noms figés Task 4)

    /// BCK-021 / uikit-window-request-opens-correlated-scene — `requestWindow` sur le
    /// requérant : le provider active une NOUVELLE scène corrélée à la demande, l'enfant
    /// s'attache par le chemin ordinaire, l'outcome est `OpenedInNewSession(childId)`, les
    /// objets host résolus par l'app sont CEUX du fixture de la scène corrélée, et le
    /// requérant est inchangé (aucune fenêtre enfant dans SA liste, même surface primaire,
    /// son application n'a pas re-tourné).
    func testWindowRequestOpensTheCorrelatedScene() throws {
        let provider = DriverWindowSceneProvider.shared
        let primary = try primaryFixture()
        let requester = try attach(primary, windowSceneProvider: provider)
        let runsAfterAttach = primary.applicationRuns
        let surfaceAfterAttach = primary.primarySurfaceRef
        let stateAfterAttach = try XCTUnwrap(primary.scope?.windows.state.value as? WindowManagerState)

        let request = try requestWindow(primary, title: "window-request-correlated")
        XCTAssertTrue(request.state.value is WindowRequestStatePending,
                      "the request must stay Pending until the correlated scene resolves it")

        let child = try waitForChild(request, resolvedIndex: 0)
        children.append((child.fixture, child.record))
        assertChildFactsCoherent(child)
        XCTAssertFalse(child.record.fixtureBound,
                       "the child must run the requester's factory as a NEW session record, never re-run the requester's facts")

        // Les objets host résolus par l'app sont ceux du fixture de la scène corrélée.
        XCTAssertTrue(child.resolved.window === child.fixture.window,
                      "the resolved window must be the correlated scene's own fixture window")
        XCTAssertTrue(child.resolved.surfaceView === child.fixture.observedView,
                      "the resolved surface view must be the correlated scene's own fixture view")
        XCTAssertFalse(child.fixture.scene.isEqual(primary.scene), "the child scene must be a NEW scene")

        // Le requérant est inchangé : exactement sa fenêtre primaire (l'enfant n'y entre
        // JAMAIS — roadmap l.247), même surface primaire, aucune ré-exécution applicative.
        let stateAfterChild = try XCTUnwrap(primary.scope?.windows.state.value as? WindowManagerState)
        XCTAssertEqual(stateAfterChild.windows.count, 1, "the child must never join the requester's window list")
        XCTAssertTrue((stateAfterChild.primary?.surface as AnyObject?) === (stateAfterAttach.primary?.surface as AnyObject?),
                      "the requester's primary window must be untouched")
        XCTAssertEqual(primary.applicationRuns, runsAfterAttach,
                       "the child run must not re-run the requester's application facts")
        XCTAssertTrue((primary.primarySurfaceRef as AnyObject?) === (surfaceAfterAttach as AnyObject?))
        XCTAssertFalse((child.record.primarySurfaceRef as AnyObject?) === (primary.primarySurfaceRef as AnyObject?),
                       "the child must own its own primary surface")
        XCTAssertTrue(requester.state.value is SessionStateRunning, "the requester stays Running with its child alive")

        try stopChild(child)
        try stopAndAwaitTermination(primary)
    }

    /// BCK-021 / uikit-window-request-child-session-independent — les DEUX directions :
    /// (a) l'arrêt de la session enfant (déconnexion de SA scène) n'affecte pas le requérant
    /// (Running, faits intacts) ; (b) une nouvelle demande depuis le MÊME requérant (le slot
    /// est libéré), puis l'arrêt du requérant laisse l'enfant INTACT (attachée, app vivante).
    func testChildSessionIsIndependentFromTheRequester() throws {
        let provider = DriverWindowSceneProvider.shared
        let primary = try primaryFixture()
        let requester = try attach(primary, windowSceneProvider: provider)
        let runsAfterAttach = primary.applicationRuns

        // (a) premier enfant — son arrêt ne touche pas le requérant.
        let first = try waitForChild(try requestWindow(primary, title: "window-request-independence-1"), resolvedIndex: 0)
        children.append((first.fixture, first.record))
        try stopChild(first)

        XCTAssertTrue(requester.state.value is SessionStateRunning,
                      "the requester must stay Running after its child stopped")
        XCTAssertEqual(primary.applicationRuns, runsAfterAttach, "no extra application run on the requester")
        let stateAfterChildStop = try XCTUnwrap(primary.scope?.windows.state.value as? WindowManagerState)
        XCTAssertEqual(stateAfterChildStop.windows.count, 1)
        XCTAssertFalse((first.record.primarySurfaceRef as AnyObject?) === (primary.primarySurfaceRef as AnyObject?),
                       "child and requester surfaces must stay distinct")

        // (b) re-deemande depuis le MÊME requérant (slot libéré par la terminaison du
        // premier enfant), puis arrêt du requérant : l'enfant reste INTACT.
        let secondRequest = try requestWindow(primary, title: "window-request-independence-2")
        let second = try waitForChild(secondRequest, resolvedIndex: 1)
        children.append((second.fixture, second.record))
        assertChildFactsCoherent(second)
        XCTAssertFalse((second.record.primarySurfaceRef as AnyObject?) === (first.record.primarySurfaceRef as AnyObject?),
                       "the two children must own distinct surfaces")
        XCTAssertFalse(second.fixture.scene.isEqual(first.fixture.scene), "each request must correlate its own scene")

        let requesterTerminated = expectation(description: "requester terminated")
        requester.requestStop()
        requester.awaitTermination { _, _ in requesterTerminated.fulfill() }
        wait(for: [requesterTerminated], timeout: 20)
        try primary.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }

        // L'enfant survit au requérant : attaché, application NON cooperée, surface en main.
        // (Trempage borné : pendant une seconde, RIEN ne doit détacher l'enfant.)
        _ = try pumpUntil(timeout: 1.0) { second.record.lifecycleAttachment() === AttachmentState.detached }
        XCTAssertFalse(second.record.lifecycleAttachment() === AttachmentState.detached,
                       "the child must survive the requester stop — \(second.record.summary)")
        XCTAssertFalse(second.record.applicationCompleted,
                       "the child application must not be asked to complete — \(second.record.summary)")
        XCTAssertNotNil(second.record.lifecycleSnapshot())

        // Nettoyage : l'enfant orphelin part par la déconnexion de SA scène.
        try stopChild(second)
    }

    /// BCK-021 / uikit-window-request-requester-close-detaches-child — `close()` du requérant
    /// (AutoCloseable) avec un enfant VIVANT : le close termine le requérant et NE touche PAS
    /// l'enfant déjà résolu (sessions indépendantes, roadmap l.240 — le drain du close ne
    /// termine que les demandes PENDANTES).
    func testRequesterCloseLeavesTheResolvedChildAlive() throws {
        let provider = DriverWindowSceneProvider.shared
        let primary = try primaryFixture()
        let requester = try attach(primary, windowSceneProvider: provider)

        let child = try waitForChild(try requestWindow(primary, title: "window-request-close"), resolvedIndex: 0)
        children.append((child.fixture, child.record))
        assertChildFactsCoherent(child)
        let childSurface = child.record.primarySurfaceRef

        // close() du requérant (l'arrêt host — le même chemin que requestStop).
        let requesterTerminated = expectation(description: "requester closed")
        requester.close()
        requester.awaitTermination { outcome, error in
            XCTAssertNil(error)
            XCTAssertNotNil(outcome)
            requesterTerminated.fulfill()
        }
        wait(for: [requesterTerminated], timeout: 20)
        try primary.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }

        // L'enfant déjà résolu est INTACT : attaché, app vivante, surface identique.
        // (Trempage borné : pendant une seconde, RIEN ne doit détacher l'enfant.)
        _ = try pumpUntil(timeout: 1.0) { child.record.lifecycleAttachment() === AttachmentState.detached }
        XCTAssertFalse(child.record.lifecycleAttachment() === AttachmentState.detached,
                       "the resolved child must survive the requester close — \(child.record.summary)")
        XCTAssertFalse(child.record.applicationCompleted, "the child application must stay alive — \(child.record.summary)")
        XCTAssertTrue((child.record.primarySurfaceRef as AnyObject?) === (childSurface as AnyObject?),
                      "the child surface must be untouched")
        XCTAssertNotNil(child.record.lifecycleSnapshot())

        try stopChild(child)
    }

    /// BCK-021 / uikit-window-request-concurrent-pair — deux `requestWindow` pendantes EN
    /// MÊME TEMPS (activations sérialisées côté app, corrélations parallèles) : deux scènes
    /// distinctes, chacune corrélée à SA demande (requestId ↔ scène ↔ session enfant — la
    /// chaîne d'identité au complet), jamais croisées.
    func testConcurrentWindowRequestsCorrelateEachSceneToItsOwnRequest() throws {
        let provider = DriverWindowSceneProvider.shared
        let primary = try primaryFixture()
        let requester = try attach(primary, windowSceneProvider: provider)

        let firstRequest = try requestWindow(primary, title: "window-request-pair-first")
        XCTAssertTrue(firstRequest.state.value is WindowRequestStatePending)
        let secondRequest = try requestWindow(primary, title: "window-request-pair-second")
        XCTAssertTrue(secondRequest.state.value is WindowRequestStatePending,
                      "the second request is admitted while the first is still pending")

        XCTAssertFalse(firstRequest.id.isEqual(secondRequest.id), "two admissions must have distinct request ids")

        let first = try waitForChild(firstRequest, resolvedIndex: 0)
        let second = try waitForChild(secondRequest, resolvedIndex: 1)
        children.append((first.fixture, first.record))
        children.append((second.fixture, second.record))

        // Les corrélations ne sont PAS croisées : résolution #1 ↔ demande #1 ↔ scène #1,
        // résolution #2 ↔ demande #2 ↔ scène #2 ; les deux scènes et les deux sessions
        // enfant sont deux à deux distinctes.
        XCTAssertTrue(first.resolved.requestId.isEqual(firstRequest.id))
        XCTAssertTrue(second.resolved.requestId.isEqual(secondRequest.id))
        XCTAssertFalse(first.resolved.scene.isEqual(second.resolved.scene), "each request must get its OWN scene")
        XCTAssertTrue(first.resolved.window === first.fixture.window)
        XCTAssertTrue(second.resolved.window === second.fixture.window)
        XCTAssertFalse(first.record.sessionId.isEqual(second.record.sessionId), "two children must be distinct sessions")
        XCTAssertFalse((first.record.primarySurfaceRef as AnyObject?) === (second.record.primarySurfaceRef as AnyObject?),
                       "two children must own distinct surfaces")
        assertChildFactsCoherent(first)
        assertChildFactsCoherent(second)
        XCTAssertFalse((first.record.primarySurfaceRef as AnyObject?) === (primary.primarySurfaceRef as AnyObject?))
        XCTAssertFalse((second.record.primarySurfaceRef as AnyObject?) === (primary.primarySurfaceRef as AnyObject?))

        // Le requérant reste unique-fenêtre pendant que ses DEUX enfants vivent.
        let state = try XCTUnwrap(primary.scope?.windows.state.value as? WindowManagerState)
        XCTAssertEqual(state.windows.count, 1, "no child may ever join the requester's window list")
        XCTAssertTrue(requester.state.value is SessionStateRunning)

        try stopChild(first)
        try stopChild(second)
        try stopAndAwaitTermination(primary)
    }

    /// BCK-021 / uikit-window-request-overlays-preserved — une UIWindow overlay posée sur la
    /// scène requérante AVANT `requestWindow` survit intégralement à l'opération : toujours
    /// visible, toujours key (la fenêtre enfant ne vole jamais le key), attachée à SA scène,
    /// racine de SA propre hiérarchie — et JAMAIS devenue une fenêtre Kadre.
    func testOverlayWindowIsPreservedAcrossTheWindowRequest() throws {
        let provider = DriverWindowSceneProvider.shared
        let primary = try primaryFixture()
        _ = try attach(primary, windowSceneProvider: provider)
        let runsAfterAttach = primary.applicationRuns

        // L'overlay de l'app sur la scène 1, AVANT la demande.
        let overlay = UIWindow(windowScene: primary.scene)
        overlay.frame = CGRect(x: 10, y: 10, width: 120, height: 90)
        overlay.isHidden = false
        overlay.makeKeyAndVisible()
        XCTAssertTrue(overlay.isKeyWindow, "the overlay must take the key before the request")
        XCTAssertFalse(try XCTUnwrap(primary.window).isHidden)

        let child = try waitForChild(try requestWindow(primary, title: "window-request-overlay"), resolvedIndex: 0)
        children.append((child.fixture, child.record))
        assertChildFactsCoherent(child)

        // L'overlay est inchangée : visible, toujours key, SA scène, racine de SA hiérarchie.
        XCTAssertFalse(overlay.isHidden, "the overlay must survive the window request")
        XCTAssertTrue(overlay.isKeyWindow, "the child window must not steal the key from the requester's overlay")
        XCTAssertTrue(overlay.windowScene?.isEqual(primary.scene) == true, "the overlay must stay on its own scene")
        XCTAssertNil(overlay.superview, "the overlay must stay the root of its own hierarchy")
        // La fenêtre du fixture et la liste Kadre du requérant : l'overlay n'y est JAMAIS entrée.
        XCTAssertFalse(try XCTUnwrap(primary.window).isHidden)
        let state = try XCTUnwrap(primary.scope?.windows.state.value as? WindowManagerState)
        XCTAssertEqual(state.windows.count, 1, "the overlay must never become a Kadre window")
        XCTAssertEqual(primary.applicationRuns, runsAfterAttach, "no extra application run on the requester")

        overlay.isHidden = true
        try stopChild(child)
        try stopAndAwaitTermination(primary)
    }

    /// BCK-020 / uikit-window-request-cancel-before-connect — `cancel` du requérant AVANT
    /// que le provider n'active la moindre scène (gate `holdActivations`) : l'annulation est
    /// `CancelledBeforeCommit`, la demande reste terminale `Cancelled`, et quand la gate est
    /// relâchée la résolution TARDIVE (scène connectée après coup, si FrontBoard l'honore)
    /// est un no-op : AUCUNE session enfant n'existe, l'outcome ne change jamais.
    func testWindowRequestCancelledBeforeTheSceneConnectsStaysTerminalWithNoChild() throws {
        let provider = DriverWindowSceneProvider.shared
        provider.holdActivations = true
        let primary = try primaryFixture()
        // Le store d'enregistrements s'accumule d'un test à l'autre : la ligne de base est
        // prise AVANT l'attach — le seul enregistrement nouveau de CE test doit être celui
        // du requérant (fixtureBound), jamais celui d'un enfant.
        let recordsBefore = DriverFixture.shared.sessionRecords.count
        _ = try attach(primary, windowSceneProvider: provider)

        let request = try requestWindow(primary, title: "window-request-cancelled")
        XCTAssertEqual(provider.openCallCount, 1, "the provider must have been called exactly once")
        XCTAssertTrue(request.state.value is WindowRequestStatePending)
        // Aucune activation n'est partie : la demande attend dans la file du provider.
        XCTAssertTrue(provider.correlation.isEmpty, "no scene may correlate while the activation is held")
        XCTAssertTrue(provider.resolvedCalls.isEmpty)

        // Annulation AVANT toute connexion : CancelledBeforeCommit, demande terminale Cancelled.
        let cancellation = try cancelRequest(request)
        XCTAssertTrue(cancellation is WindowCancellationOutcomeCancelledBeforeCommit,
                      "expected CancelledBeforeCommit, got \(String(describing: cancellation))")
        let outcome = try awaitOutcome(request, timeout: 5)
        XCTAssertTrue(outcome is WindowRequestOutcomeCancelled,
                      "the outcome must be Cancelled, got \(String(describing: outcome))")

        // La gate relâchée : l'activation part ; si la scène connecte, le provider résout une
        // demande DÉJÀ terminale — le no-op du manager (CAS Task 1) : aucun enfant n'apparaît.
        provider.holdActivations = false
        let lateResolve = try pumpUntil(timeout: 15) { provider.resolvedCalls.count >= 1 }
        // Dans les deux cas (scène connectée et résolue tardivement, ou activation refusée),
        // les invariants sont les mêmes : AUCUN enfant, l'outcome reste terminal Cancelled.
        // (Le store d'enregistrements s'accumule d'un test à l'autre : seuls les NOUVEAUX
        // comptent — exactement celui du requérant, jamais un enfant.)
        let newRecords = DriverFixture.shared.sessionRecords.dropFirst(recordsBefore)
        XCTAssertEqual(newRecords.count, 1, "only the requester's own record may exist")
        XCTAssertTrue(newRecords.allSatisfy { $0.fixtureBound },
                      "no child session may attach to a cancelled request")
        let outcomeAfterGate = try awaitOutcome(request, timeout: 5)
        XCTAssertTrue(outcomeAfterGate is WindowRequestOutcomeCancelled,
                      "the outcome must stay Cancelled forever, got \(String(describing: outcomeAfterGate))")
        XCTAssertTrue(request.state.value is WindowRequestStateTerminated)
        if lateResolve {
            // La scène tardive a bien été résolue sur SES objets host — mais sans enfant :
            // le no-op a eu lieu après la vérification d'identité app-side.
            let call = try XCTUnwrap(provider.resolvedCalls.first)
            XCTAssertTrue(call.requestId.isEqual(request.id))
            XCTAssertNotEqual(ObjectIdentifier(call.scene), ObjectIdentifier(primary.scene))
        }

        try stopAndAwaitTermination(primary)
    }
}
