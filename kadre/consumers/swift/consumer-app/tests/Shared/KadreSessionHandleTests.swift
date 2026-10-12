import XCTest
import KadreUikit
#if canImport(KadreConsumerIos)
@testable import KadreConsumerIos
#elseif canImport(KadreConsumerTvOs)
@testable import KadreConsumerTvOs
#endif

/// Task 4 — la façade VIVANTE (`kadre/INTEROP-EXPORTS.md` §5) : `KadreIosHost.attach`
/// débite un `KadreSessionHandle` (id opaque, snapshot courant, observation sérialisée
/// sur le MainActor, requestStop/close idempotents, `awaitTermination` asynchrone dont la
/// cancellation de Task n'annule QUE le waiter — la session, elle, continue).
///
/// Les preuves, toutes sur session RÉELLE (attach de la factory Kotlin du consumer sur la
/// scène de l'app hôte — une session à la fois : le registre de scène Kadre refuse le
/// doublon, la réservation est libérée à la terminaison de la session précédente, d'où la
/// boucle d'attente bornée de l'helper d'attach) :
///  1. attach → émission IMMÉDIATE du snapshot courant à l'inscription, puis
///     `.terminated(.stopped(.hostRequested))` après requestStop ; `awaitTermination`
///     déjà-terminé = retour direct ;
///  2. `awaitTermination` + `Task.cancel` → `CancellationError` ET la session VIT — la
///     preuve est dans la RAISON de l'arrêt : l'application consumer s'arrête elle-même
///     (~500 ms) → outcome `Stopped(ApplicationRequested)` — un arrêt déclenché par la
///     cancellation aurait dit `HostRequested` ;
///  3. l'ordre des callbacks : table d'arrivée strictement croissante dans le rang du
///     cycle de vie, chaque livraison sur le thread principal, zéro doublon adjacent ;
///  4. retrait idempotent (observation.close() deux fois = une seule résiliation, plus
///     aucune livraison ensuite) ; close() du handle idempotent ; observeState APRÈS close
///     ne livre plus rien (promesse §5 bornée au pré-close — documenté) ;
///  5. LA COURSE : terminaison ET cancellation concurrentes → la continuation reprise
///     EXACTEMENT une fois — boucle de 50 itérations, délai pré-cancellation aléatoire
///     0–20 ms tiré par `drand48` SEMÉ par itération (déterminisme) ; une double reprise
///     d'un `CheckedContinuation` trappe immédiatement : 50 itérations vertes = preuve.
///
/// NB de nommage : ce bundle importe DEUX modules qui exportent des noms communs — le
/// fichier n'écrit jamais ces noms nus pour le KOTLIN (`KadreUikit.X` partout), les noms
/// nus restant les déclarations façade du module app (`@testable`).
private enum KadreSessionHandleTestsError: Error {
    case hostSceneUnavailable
}

@MainActor
final class KadreSessionHandleTests: XCTestCase {

    // MARK: — Helpers

    /// Un attach RÉEL par la façade (§5) sur la scène de l'app hôte, dans une fenêtre
    /// neuve (idiome driver : `makeKey` réservé à la fenêtre primaire de la scène). La
    /// session précédente — du store au lancement ou du test précédent — peut encore
    /// tenir la réservation de scène au moment de l'appel : `AlreadyInUse(Host)` est
    /// réessayé borné (la terminaison libère la réservation), tout autre échec est
    /// renvoyé immédiatement.
    @discardableResult
    private func attachHandle(
        policy: KadrePolicyProfile = .default,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async throws -> KadreSessionHandle {
        guard let scene = ConsumerSceneStore.shared.window?.windowScene else {
            XCTFail("the host app scene is unavailable — the app did not finish launching",
                    file: file, line: line)
            throw KadreSessionHandleTestsError.hostSceneUnavailable
        }
        let deadline = Date().addingTimeInterval(25)
        while true {
            let window = UIWindow(windowScene: scene)
            let surfaceView = UIView(frame: window.bounds)
            window.addSubview(surfaceView)
            window.isHidden = false
            do {
                return try KadreIosHost.attach(
                    windowScene: scene,
                    window: window,
                    surfaceView: surfaceView,
                    applicationFactory: ConsumerApplicationFactory(),
                    policy: policy)
            } catch let error as KadreError {
                guard error.failure == .alreadyInUse(resource: .host), Date() < deadline else {
                    throw error
                }
                try? await Task.sleep(nanoseconds: 10_000_000)
            }
        }
    }

    /// Le rang du cycle de vie d'un snapshot — la colonne de TRI de la preuve d'ordre.
    private func lifecycleRank(_ snapshot: KadreSessionSnapshot) -> Int {
        switch snapshot {
        case .starting: return 0
        case .running: return 1
        case .stopping: return 2
        case .terminated: return 3
        }
    }

    /// `awaitTermination` sous chien de garde : nil = non terminé dans le délai (la
    /// session dépend de l'auto-arrêt de l'application consumer ~500 ms — un hang serait
    /// sinon un blocage de toute la suite). L'enfant est annulé après coup — la façade
    /// GARANTIT que sa cancellation reprend le waiter (c'est exactement le contrat §5
    /// testé ici), le groupe ne peut donc pas rester suspendu.
    private func terminationOutcomeWithin(
        _ handle: KadreSessionHandle,
        timeout: Double = 20
    ) async -> KadreSessionOutcome? {
        await withTaskGroup(of: KadreSessionOutcome?.self) { group in
            group.addTask {
                do { return try await handle.awaitTermination() } catch { return nil }
            }
            group.addTask {
                do { try await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000)) }
                catch {}
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    /// Attente bornée (poll 10 ms sur le MainActor — jamais de sommeil nu bloquant) d'une
    /// condition vue depuis le MainActor.
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

    // MARK: — 1. Le scénario de bout en bout (brief étape 4)

    /// uikit-facade-attach-observes-to-terminated — attach → émission immédiate du
    /// snapshot courant → requestStop → `.terminated(.stopped(.hostRequested))` livré →
    /// `awaitTermination` (déjà terminé) retourne l'outcome DIRECTEMENT.
    func testAttachEmitsCurrentSnapshotImmediatelyThenTerminatedHostRequested() async throws {
        let handle = try await attachHandle()
        defer { handle.close() }

        // Le snapshot COURANT est lisible tout de suite (Starting ou Running — l'attach
        // retourne avant le premier tick du scope de session, Running arrive ensuite).
        let initial = handle.state
        XCTAssertTrue(initial == .starting || initial == .running,
                      "the initial snapshot must be .starting or .running, got \(initial)")

        let arrival: KadreArrivalTable = KadreArrivalTable()
        let observation = handle.observeState { snapshot in
            arrival.record(snapshot)
        }
        // Émission IMMÉDIATE : la table est déjà remplie AU RETOUR de l'inscription.
        XCTAssertFalse(arrival.isEmpty,
                       "observeState must deliver the current snapshot immediately")

        handle.requestStop()
        let terminatedArrived = await waitUntil { arrival.containsTerminated }
        XCTAssertTrue(terminatedArrived,
                      "the terminated snapshot must arrive after requestStop; arrival=\(arrival)")

        // Le DERNIER snapshot livré est `terminated(.stopped(.hostRequested))`.
        XCTAssertEqual(arrival.last, .terminated(.stopped(.hostRequested)),
                       "the last delivered snapshot must be terminated(.stopped(.hostRequested))")

        // Après rien après : plus aucune livraison après `terminated` (le StateFlow est
        // terminal — l'observation n'émet plus).
        let countAtTerminated = arrival.count
        try await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertEqual(arrival.count, countAtTerminated,
                       "nothing may be delivered after terminated")

        // Déjà terminé : `awaitTermination` retourne DIRECTEMENT l'outcome mappé.
        let outcome = await terminationOutcomeWithin(handle)
        XCTAssertEqual(outcome, .stopped(.hostRequested),
                       "awaitTermination on a terminated session must return directly")

        observation.close()
    }

    // MARK: — 2. La cancellation n'annule QUE le waiter

    /// uikit-facade-await-termination-cancellation-keeps-session — `Task.cancel` sur le
    /// waiter lève `CancellationError` ET la session continue : la PREUVE est la raison
    /// de l'arrêt qui suit — l'application consumer s'arrête ELLE-MÊME (~500 ms) →
    /// `Stopped(ApplicationRequested)` ; un arrêt provoqué par la cancellation aurait
    /// dit `HostRequested`. Aucun requestStop n'est envoyé dans ce test.
    func testAwaitTerminationCancellationKeepsSessionAliveToApplicationRequested() async throws {
        let handle = try await attachHandle()
        defer { handle.close() }

        let waiter = Task { try await handle.awaitTermination() }
        // Laisse le waiter s'enregistrer côté Kotlin (le pont Obj-C n'est pas lié à la
        // Task Swift : la registration est asynchrone par rapport à elle).
        try await Task.sleep(nanoseconds: 100_000_000)

        waiter.cancel()
        var caught: Error?
        do { _ = try await waiter.value } catch { caught = error }
        XCTAssertTrue(caught is CancellationError,
                      "the cancelled waiter must throw CancellationError, got \(String(describing: caught))")

        // La session VIT : le snapshot reste non-terminal le temps que l'application
        // s'arrête elle-même, puis l'outcome appartient à l'APPLICATION.
        let outcome = await terminationOutcomeWithin(handle, timeout: 20)
        XCTAssertEqual(outcome, .stopped(.applicationRequested),
                       "the session must have survived the waiter cancellation and been stopped BY THE APPLICATION; got \(String(describing: outcome))")

        // La session vit SI le snapshot a progressé : après terminaison le handle le dit.
        XCTAssertEqual(handle.state, .terminated(.stopped(.applicationRequested)),
                       "the handle must expose the mapped terminated snapshot")
    }

    // MARK: — 3. L'ordre des callbacks MainActor

    /// uikit-facade-observation-delivers-in-order-on-main — la table d'arrivée est
    /// strictement croissante dans le rang du cycle de vie (démarrage → terminaison),
    /// chaque livraison est sur le thread principal, aucune valeur répétée.
    func testObserveStateDeliversDistinctSnapshotsInOrderOnMainThread() async throws {
        let handle = try await attachHandle()
        defer { handle.close() }

        let arrival: KadreArrivalTable = KadreArrivalTable()
        let observation = handle.observeState { snapshot in
            arrival.record(snapshot)
        }

        handle.requestStop()
        let terminatedArrived = await waitUntil { arrival.containsTerminated }
        XCTAssertTrue(terminatedArrived, "arrival=\(arrival)")

        // Toutes les livraisons sur le thread principal (la sérialisation MainActor).
        XCTAssertTrue(arrival.allOnMainThread,
                      "every delivery must land on the main thread; flags=\(arrival.mainThreadFlags)")

        // Ordre STRICTEMENT croissant — jamais de retour arrière, jamais de doublon.
        let ranks = arrival.snapshots.map(lifecycleRank)
        XCTAssertEqual(ranks, ranks.sorted(),
                       "the delivered snapshots must be in lifecycle order; arrival=\(arrival.arrivalDescription)")
        let unique = arrival.snapshots.allSatisfy { value in
            arrival.snapshots.filter { $0 == value }.count == 1
        }
        XCTAssertTrue(unique, "no snapshot may be delivered twice; arrival=\(arrival.arrivalDescription)")

        observation.close()
    }

    // MARK: — 4. Le retrait idempotent

    /// uikit-facade-observation-close-twice-idempotent — `close()` deux fois = une seule
    /// résiliation ; après retrait, AUCUNE livraison de plus (même quand la session
    /// continue de changer — le compteur reste figé).
    func testObservationCloseTwiceIsIdempotentAndStopsDelivery() async throws {
        let handle = try await attachHandle()
        defer { handle.close() }

        let arrival: KadreArrivalTable = KadreArrivalTable()
        let observation = handle.observeState { snapshot in
            arrival.record(snapshot)
        }
        XCTAssertFalse(arrival.isEmpty)

        observation.close()
        observation.close() // idempotent — ni crash, ni effet supplémentaire

        let countAtRemoval = arrival.count
        handle.requestStop()
        let sessionTerminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(sessionTerminated, "the session must terminate after requestStop")
        try await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertEqual(arrival.count, countAtRemoval,
                       "a removed observer must never be called again; arrival=\(arrival)")
    }

    // MARK: — 5. close() du handle : idempotent, observeState après close muet

    /// uikit-facade-handle-close-idempotent-and-post-close-observation-mute — le close du
    /// handle ferme la session Kotlin, résilie les observations et est idempotent ; la
    /// promesse observeState est bornée AU PRÉ-CLOSE (§5) : après close, la nouvelle
    /// observation ne livre plus rien (retirée d'office — comportement documenté).
    func testHandleCloseIsIdempotentAndObserveStateAfterCloseDeliversNothing() async throws {
        let handle = try await attachHandle()

        let preCloseArrival: KadreArrivalTable = KadreArrivalTable()
        let preCloseObservation = handle.observeState { snapshot in
            preCloseArrival.record(snapshot)
        }
        XCTAssertFalse(preCloseArrival.isEmpty)

        handle.close()
        handle.close() // idempotent

        // La session Kotlin a été fermée → arrêt host demandé → terminaison.
        let sessionTerminated = await waitUntil { handle.state.isTerminated }
        XCTAssertTrue(sessionTerminated, "handle.close() must close the Kotlin session")

        // Les observations d'avant le close ne livrent plus.
        let countAtClose = preCloseArrival.count
        try await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertEqual(preCloseArrival.count, countAtClose,
                       "pre-close observations must be torn down by handle.close()")

        // APRÈS close : observeState ne promet plus rien — l'observation rendue ne livre
        // jamais (close d'office, documenté) et son retrait est idempotent.
        let postCloseArrival: KadreArrivalTable = KadreArrivalTable()
        let postCloseObservation = handle.observeState { snapshot in
            postCloseArrival.record(snapshot)
        }
        XCTAssertTrue(postCloseArrival.isEmpty,
                      "an observation taken after close must deliver nothing immediately")
        postCloseObservation.close()
        postCloseObservation.close()
        try await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertTrue(postCloseArrival.isEmpty,
                      "an observation taken after close must never deliver")
    }

    // MARK: — 6. LA COURSE : terminaison ET cancellation, reprise EXACTEMENT une fois

    /// uikit-facade-termination-vs-cancellation-resumes-exactly-once — 50 itérations :
    /// waiter armé, délai aléatoire 0–20 ms (drand48 SEMÉ par itération — déterministe),
    /// requestStop PUIS cancellation concurrente. À chaque itération : la continuation est
    /// reprise UNE fois (outcome OU CancellationError — jamais les deux, jamais aucune :
    /// une double reprise de `CheckedContinuation` trappe le process, une absence pend).
    /// La session termine en `.stopped(.hostRequested)` dans TOUS les cas — le second
    /// `awaitTermination` retourne l'outcome directement, y compris dans la branche où la
    /// cancellation a gagné (la session a survécu à la cancellation du waiter).
    func testTerminationAndCancellationResumeExactlyOnceFiftyIterationRaceLoop() async throws {
        var outcomeWins = 0
        var cancellationWins = 0

        for iteration in 0..<50 {
            srand48(iteration + 1) // graine PAR itération — course rejouable
            let handle = try await attachHandle()
            defer { handle.close() }

            let waiter = Task { try await handle.awaitTermination() }
            let preCancellationDelayMs = drand48() * 20.0
            try await Task.sleep(nanoseconds: UInt64(preCancellationDelayMs * 1_000_000))

            handle.requestStop() // la terminaison part EN MÊME TEMPS que la cancellation
            waiter.cancel()

            var caught: Error?
            do { _ = try await waiter.value } catch { caught = error }

            switch caught {
            case nil:
                outcomeWins += 1 // la terminaison a repris le waiter avant la cancellation
            case is CancellationError:
                cancellationWins += 1 // la cancellation a repris le waiter
            default:
                XCTFail("iteration \(iteration): neither outcome nor CancellationError — \(String(describing: caught))")
            }

            // Dans LES DEUX branches : la session termine en hostRequested et le second
            // awaitTermination retourne l'outcome directement.
            let outcome = await terminationOutcomeWithin(handle)
            XCTAssertEqual(outcome, .stopped(.hostRequested),
                           "iteration \(iteration): the session must terminate hostRequested; got \(String(describing: outcome))")
        }

        XCTAssertEqual(outcomeWins + cancellationWins, 50,
                       "every iteration must resolve exactly once")
        // NB : on n'asserte PAS que les deux branches se réalisent (l'entrelacement dépend
        // du dispatch Kotlin) ; l'invariant testé est la reprise EXACTEMENT une fois.
        // La distribution des entrelacements réalisés — lisible dans le log xcodebuild.
        print("uikit-facade race loop: outcome-won=\(outcomeWins) cancellation-won=\(cancellationWins)")
    }
}

// MARK: — La table d'arrivée des observations

/// Le registre des livraisons d'une observation — fait Swift primitif (thread principal
/// uniquement : les livraisons de la façade y sont sérialisées, ce que le test PROUVE via
/// `allOnMainThread`). `Sendable` par confinement au MainActor (toutes les méthodes sont
/// appelées depuis les closures @MainActor des tests).
@MainActor
private final class KadreArrivalTable {
    private(set) var snapshots: [KadreSessionSnapshot] = []
    private(set) var mainThreadFlags: [Bool] = []

    var count: Int { snapshots.count }
    var last: KadreSessionSnapshot? { snapshots.last }

    /// Sentinelle de l'émission IMMÉDIATE : false dès la première livraison — le test
    /// asserte qu'AU RETOUR de `observeState` la table n'est plus vide.
    var isEmpty: Bool { snapshots.isEmpty }

    var containsTerminated: Bool {
        snapshots.contains { snapshot in
            if case .terminated = snapshot { return true }
            return false
        }
    }

    var allOnMainThread: Bool { mainThreadFlags.allSatisfy(\.self) }

    /// La lecture du registre pour les diagnostics d'échec — les snapshots ET le fil de
    /// chaque livraison, dans l'ordre d'arrivée.
    var arrivalDescription: String {
        let entries = zip(snapshots, mainThreadFlags).enumerated().map { index, pair in
            "#\(index) \(pair.0) main=\(pair.1)"
        }
        return "[\(entries.joined(separator: "; "))]"
    }

    func record(_ snapshot: KadreSessionSnapshot) {
        snapshots.append(snapshot)
        mainThreadFlags.append(Thread.isMainThread)
    }
}

extension KadreSessionSnapshot {
    /// La lisibilité des attendus de test : le masque porte-t-il la terminaison.
    var isTerminated: Bool {
        if case .terminated = self { return true }
        return false
    }
}
