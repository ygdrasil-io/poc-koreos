import Foundation
import KadreUikit

/*
 * `KadreSessionHandle` — verbatim §5 : le billet vivant d'une session attachée. La
 * valeur-frontière du consommateur Swift : `id` opaque (§3 — la description exportée du
 * `SessionId` Kotlin, jamais structurée), `state` le snapshot courant MAPPÉ par le
 * mapper Task 3, `observeState` l'observation sérialisée sur le MainActor (émission
 * immédiate du courant, puis chaque changement DANS L'ORDRE — mécanisme documenté dans
 * `KadreObservation.swift`), `requestStop`/`close` les délégations, `awaitTermination`
 * l'attente asynchrone à waiter isolé.
 *
 * CONTRATS :
 *  — `close()` est idempotent : il résilie TOUTES les observations vivantes (plus rien
 *    n'est livré ensuite), ferme la session Kotlin (`close()` = arrêt host demandé ;
 *    no-op sur session déjà terminée) et marque le handle clos. La promesse
 *    `observeState` est bornée AU PRÉ-CLOSE (§5) : après close, une nouvelle observation
 *    ne livre jamais rien (forme muette d'office — documenté dans `KadreObservation`).
 *  — `awaitTermination()` : chaque appel arme SON waiter (un enregistrement Kotlin
 *    propre à l'appel + un slot de reprise sous verrou). La terminaison de session reprend
 *    le waiter avec l'outcome MAPPÉ (`KadreSessionOutcomeMapper`) ; la CANCELLATION de la
 *    Task Swift reprend le waiter avec `CancellationError` et NE TOUCHE PAS à la session —
 *    elle continue (l'enregistrement Kotlin du waiter annulé retombe sur un slot déjà
 *    réglé : no-op). Terminaison et cancellation concurrentes : la reprise est EXACTEMENT
 *    une fois (premier arrivé sous verrou, les suivants voient un slot réglé) — preuve en
 *    boucle de 50 itérations dans le consumer (`KadreSessionHandleTests`).
 *
 * `@unchecked Sendable` : `session` est un objet Kotlin thread-safe (StateFlow/locks) ;
 * l'état Swift (`observations`, `closedState`) ne vit que sous `NSLock`.
 */
public final class KadreSessionHandle: @unchecked Sendable {

    /// La session Kotlin attachée — la coutume interne du handle (le mapper et l'attach
    /// vivent dans CE module ; la surface PUBLIQUE ne référence que les types fermés §5).
    private let session: any KadreUikit.KadreSession

    private let lock = NSLock()
    private var watchers: [KadreStateFlowWatcher] = []
    private var closedState = false

    init(session: any KadreUikit.KadreSession) {
        self.session = session
    }

    /// L'identifiant opaque de la session (§3) : la description RÉDIGÉE exportée par le
    /// Kotlin (`toString()` constant) — le `SessionId` n'a aucune structure publiée.
    public var id: String {
        session.id.description
    }

    /// Le snapshot COURANT (§5) : la valeur du StateFlow Kotlin, mappée par le mapper
    /// Task 3. La valeur est toujours présente (le flux démarre à `Starting` et
    /// s'achève terminal) — son absence serait une surface Kotlin élargie hors contrat.
    public var state: KadreSessionSnapshot {
        guard let value = session.state.value as? (any KadreUikit.SessionState) else {
            fatalError("SessionState Kotlin absent du StateFlow de la session : \(String(describing: session.state.value))")
        }
        return KadreSessionSnapshotMapper.make(value)
    }

    /// L'observation sérialisée (§5). SUR LE MAINACTOR : l'émission IMMÉDIATE du snapshot
    /// courant est faite ici, AU RETOUR de l'appel ; les changements suivants arrivent
    /// dans l'ordre via le collecteur (`KadreStateFlowWatcher`). Après la livraison de
    /// `terminated`, plus rien n'émet (le StateFlow est terminal). Retrait idempotent via
    /// l'`KadreObservation` rendue. APRÈS `close()` : ne livre jamais rien (promesse §5
    /// bornée au pré-close — comportement documenté).
    @MainActor
    public func observeState(
        _ observer: @escaping @MainActor (KadreSessionSnapshot) -> Void
    ) -> KadreObservation {
        lock.lock()
        if closedState {
            lock.unlock()
            return KadreObservation(alreadyClosed: true)
        }
        lock.unlock()

        // L'émission IMMÉDIATE du courant — le watcher est SEMÉ avec CE snapshot (son
        // `lastDelivered`), pour que le premier emit du collecteur (la ré-émission de la
        // valeur courante propre aux nouveaux collecteurs StateFlow) soit dédupliqué.
        let current = state
        observer(current)

        let watcher = KadreStateFlowWatcher(flow: session.state, initial: current, observer: observer)
        let observation = KadreObservation { [weak self] in
            watcher.close()
            self?.forget(watcher)
        }
        lock.lock()
        if closedState {
            // close() est passé entre-temps (il ne connaissait pas CE watcher) : la
            // COLLECTE n'est jamais démarrée et l'observation rendue est muette
            // d'office. L'émission immédiate ci-dessus, elle, a déjà eu lieu — l'appel
            // a DÉBUTÉ pré-close (la promesse §5 est bornée au pré-close) : au plus CE
            // snapshot courant est parti, jamais rien ensuite.
            lock.unlock()
            watcher.close()
            return KadreObservation(alreadyClosed: true)
        }
        watchers.append(watcher)
        lock.unlock()
        watcher.start()
        return observation
    }

    /// L'arrêt motivé (§5) : délégation directe — la terminaison portera
    /// `Stopped(HostRequested)`. No-op côté Kotlin sur session déjà terminée.
    public func requestStop() {
        session.requestStop()
    }

    /// La fermeture (§5) — idempotent : résilie toutes les observations vivantes (le
    /// handle ne livre plus rien), ferme la session Kotlin (arrêt host demandé si elle
    /// vivait encore), marque le handle clos.
    public func close() {
        lock.lock()
        guard !closedState else {
            lock.unlock()
            return
        }
        closedState = true
        let toClose = watchers
        watchers = []
        lock.unlock()
        for watcher in toClose {
            watcher.close()
        }
        session.close()
    }

    /// L'attente de terminaison (§5) — waiter isolé par appel :
    ///  — session déjà terminée : retour DIRECT avec l'outcome mappé (le wait Kotlin se
    ///    complète immédiatement) ;
    ///  — terminaison : reprise avec l'outcome mappé (`KadreSessionOutcomeMapper`) ;
    ///  — `Task.cancel()` : reprise avec `CancellationError`, la session CONTINUE —
    ///    l'enregistrement Kotlin du waiter annulé n'est pas démonté (le pont Obj-C
    ///    n'expose pas de handle de Job) et retombera, à la terminaison, sur un slot
    ///    déjà réglé : no-op prouvé exactement-une-fois.
    ///  — erreur Kotlin (hors outcome) : le NSError opaque traverse en `KadreError` du
    ///    modèle fermé (`platformFailure` — le domaine et le code du pont).
    public func awaitTermination() async throws -> KadreSessionOutcome {
        let waiter = KadreTerminationWaiter()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation {
                (continuation: CheckedContinuation<KadreSessionOutcome, Error>) in
                waiter.arm(continuation)
                session.awaitTermination { outcome, error in
                    switch (outcome, error) {
                    case (let outcome?, _):
                        waiter.settle(.success(KadreSessionOutcomeMapper.make(outcome)))
                    case (nil, let error?):
                        let nsError = error as NSError
                        waiter.settle(.failure(KadreError(failure: .platformFailure(
                            platform: .uikit,
                            domain: nsError.domain,
                            code: String(nsError.code)))))
                    case (nil, nil):
                        // Le contrat Kotlin d'`awaitTermination` est soit un outcome, soit
                        // une erreur — ne pas reprendre pendrait le waiter ; crier vaut
                        // mieux qu'un silence (posture du masque fermé).
                        waiter.settle(.failure(KadreError(failure: .applicationFailure)))
                    }
                }
            }
        } onCancel: {
            waiter.settle(.failure(CancellationError()))
        }
    }

    /// Le retrait d'un watcher de la liste vivante — appelé par la résiliation de son
    /// observation ; idempotent (`removeAll` sur une liste qui ne le contient plus).
    private func forget(_ watcher: KadreStateFlowWatcher) {
        lock.lock()
        watchers.removeAll { $0 === watcher }
        lock.unlock()
    }
}

/*
 * Le slot de reprise d'UN waiter — la discipline de course (§5) : terminaison et
 * cancellation concurrentes reprennent la continuation EXACTEMENT une fois. Le premier
 * `settle` (ou un `arm` sur slot déjà réglé — task annulée avant même l'armement) prélève
 * sous verrou ; un `CheckedContinuation` repris deux fois trappe le process, une
 * reprise manquante pend : l'invariant est testé en boucle de 50 itérations à barrières
 * aléatoires dans le consumer.
 */
private final class KadreTerminationWaiter: @unchecked Sendable {

    private let lock = NSLock()
    private var continuation: CheckedContinuation<KadreSessionOutcome, Error>?
    private var settledResult: Result<KadreSessionOutcome, Error>?

    /// L'armement — ou, si le sort est DÉJÀ réglé (cancellation gagnante survenue avant
    /// l'armement), la reprise immédiate avec CE résultat.
    func arm(_ continuation: CheckedContinuation<KadreSessionOutcome, Error>) {
        lock.lock()
        if let settled = settledResult {
            lock.unlock()
            continuation.resume(with: settled)
            return
        }
        assert(self.continuation == nil, "arm n'est appelé qu'une fois par waiter")
        self.continuation = continuation
        lock.unlock()
    }

    /// La reprise EXACTEMENT UNE FOIS : le premier arrivé prélève le slot sous verrou,
    /// les suivants ne voient plus rien.
    func settle(_ result: Result<KadreSessionOutcome, Error>) {
        lock.lock()
        guard settledResult == nil else {
            lock.unlock()
            return
        }
        settledResult = result
        guard let continuation else {
            lock.unlock()
            return
        }
        self.continuation = nil
        lock.unlock()
        continuation.resume(with: result)
    }
}
