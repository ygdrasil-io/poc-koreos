import Foundation
import KadreUikit

/*
 * `KadreObservation` — verbatim §5 : le billet rendu par
 * `KadreSessionHandle.observeState`. `close()` est IDEMPOTENT (le deuxième appel est un
 * no-op — « retrait idempotent ») : le handler de résiliation est prélevé SOUS VERROU
 * exactement une fois, les appels suivants ne voient plus rien. La résiliation libère la
 * closure d'observer (la table de test peut disparaître) ; les livraisons déjà en file
 * dans la file principale trouvent l'observer `nil` et s'effacent — rien n'est jamais
 * livré après le retrait.
 *
 * `@unchecked Sendable` : l'état (`closeHandler`) ne vit que sous `NSLock`.
 */
public final class KadreObservation: @unchecked Sendable {

    private let lock = NSLock()
    private var closeHandler: (() -> Void)?

    init(closeHandler: @escaping () -> Void) {
        self.closeHandler = closeHandler
    }

    /// L'observation rendue APRÈS `KadreSessionHandle.close()` : la promesse
    /// `observeState` est bornée au pré-close (§5) — celle-ci ne livre RIEN (jamais,
    /// pas même le snapshot courant) et son retrait est d'office effectué ; le close()
    /// du consommateur reste idempotent et sans effet.
    init(alreadyClosed: Bool) {
        assert(alreadyClosed, "cette forme ne sert que l'observation post-close, muette d'office")
        self.closeHandler = nil
    }

    /// Le retrait — idempotent : le handler est prélevé une fois (`nil` ensuite) ; le
    /// deuxième appel ne fait déjà plus rien.
    public func close() {
        lock.lock()
        let handler = closeHandler
        closeHandler = nil
        lock.unlock()
        handler?()
    }
}

/*
 * Le mécanisme d'observation interne : un collecteur Swift du StateFlow Kotlin de la
 * session — le protocole exporté `Kotlinx_coroutines_coreFlowCollector` est implémenté
 * ICI, et la collecte est démarrée par `collect(collector:completionHandler:)` au moment
 * de l'inscription.
 *
 * SÉRIALISATION (le cœur de la promesse §5 « callbacks sérialisés, jamais entrelacés ») :
 *  1. la boucle de collecte Kotlin est UN coroutine : `emit` n'est appelé que
 *     séquentiellement (l'émission N+1 ne part qu'après le completionHandler de la N) ;
 *  2. chaque émission traverse en file principale via `DispatchQueue.main.async` — la
 *     file SÉRIE principale est FIFO : l'ordre de soumission (donc d'émission) EST
 *     l'ordre de livraison, et toute livraison est sur le MainActor ;
 *  3. la déduplication (`lastDelivered`) vit elle aussi sur le thread principal
 *     uniquement — le StateFlow Kotlin ré-émet sa valeur courante à chaque nouveau
 *     collecteur, que l'émission IMMÉDIATE de l'inscription a déjà livrée.
 *
 * L'ÉMISSION IMMÉDIATE du snapshot courant est faite par `KadreSessionHandle.observeState`
 * AVANT le démarrage de la collecte (lecture synchrone de `.value`, livraison directe sur
 * le MainActor) — l'inscription reçoit donc l'état courant AU RETOUR de l'appel, sans
 * attendre le premier tick de la collecte.
 *
 * ÉCART DOCUMENTÉ au croquis « CoroutineScope(SupervisorJob() + Dispatchers.Main) possédé
 * par le handle » : le pont Obj-C des suspend functions n'expose aucune fabrique de scope
 * ni aucun handle de Job annulable — la collecte est donc lancée par le pont (contexte
 * vide, non structurée) et la VRAIE résiliation est le drapeau `closed` côté Swift : le
 * retrait libère l'observer, les émissions suivantes (s'il y en a) sont ignorées en
 * silence. Après la terminaison de la session, le StateFlow n'émet plus jamais : le
 * coroutine de collecte reste suspendu (borné — un par observation, quelques octets) et
 * l'objet collecteur n'est désréférencé que par le pont, à la fin du process. Aucun
 * travail ni allocation par tick : un flux qui dort.
 */
final class KadreStateFlowWatcher: NSObject, KadreUikit.Kotlinx_coroutines_coreFlowCollector {

    private let lock = NSLock()
    private var observer: (@MainActor (KadreSessionSnapshot) -> Void)?
    private let flow: any KadreUikit.Kotlinx_coroutines_coreStateFlow

    /// Le dernier snapshot LIVRÉ — lu et écrit SUR LE THREAD PRINCIPAL UNIQUEMENT
    /// (invariant documenté : ni `emit` ni `deliver` n'y touchent hors file principale).
    /// SEMÉ par l'émission immédiate de l'inscription : le premier emit du collecteur
    /// ré-émet la valeur courante du StateFlow que l'inscription vient de livrer — sans
    /// cette graine, la même valeur serait livrée DEUX fois (preuve : table d'arrivée du
    /// test d'ordre du consumer).
    private var lastDelivered: KadreSessionSnapshot?

    init(
        flow: any KadreUikit.Kotlinx_coroutines_coreStateFlow,
        initial: KadreSessionSnapshot,
        observer: @escaping @MainActor (KadreSessionSnapshot) -> Void
    ) {
        self.flow = flow
        self.lastDelivered = initial
        self.observer = observer
    }

    /// Le démarrage de la collecte — après l'émission immédiate de l'inscription (le
    /// premier emit du collecteur est la valeur courante du StateFlow : la déduplication
    /// l'efface si l'inscription venait de la livrer).
    func start() {
        flow.collect(collector: self) { _ in
            // Fin de collecte : pour un StateFlow elle n'arrive jamais (flux infini) ; un
            // éventuel arrêt du pont laisse simplement la livraison cessée — rien à
            // signaler, la surface d'observation ne promet pas d'erreur de collecte.
        }
    }

    /// Le retrait : l'observer est libéré sous verrou (le close peut venir de tout fil) —
    /// idempotent par construction (observer `nil` le reste).
    func close() {
        lock.lock()
        observer = nil
        lock.unlock()
    }

    // MARK: — Le collecteur Kotlin → Swift

    /// Appelé SÉQUENTIELLEMENT par la boucle de collecte Kotlin — jamais en parallèle.
    /// La valeur traverse en file principale ; le completionHandler est appelé
    /// immédiatement (la boucle Kotlin n'a pas à attendre la livraison SwiftUI/MainActor
    /// — l'ordre est déjà garanti par la FIFO de la file série).
    func emit(value: Any?, completionHandler: ((any Error)?) -> Void) {
        guard let state = value as? (any KadreUikit.SessionState) else {
            // Inatteignable tant que la hiérarchie Kotlin est fermée : crier plutôt que
            // livrer une valeur mensongère (posture des mappers, KadreFailureMapping).
            fatalError("SessionState Kotlin élargi hors du masque fermé §5 : \(String(describing: value))")
        }
        let snapshot = KadreSessionSnapshotMapper.make(state)
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.deliver(snapshot)
        }
        completionHandler(nil)
    }

    /// SUR LE THREAD PRINCIPAL : déduplication puis livraison @MainActor.
    private func deliver(_ snapshot: KadreSessionSnapshot) {
        guard snapshot != lastDelivered else { return }
        lastDelivered = snapshot
        lock.lock()
        let observer = self.observer
        lock.unlock()
        guard let observer else { return } // retiré entre-temps : rien n'est livré
        MainActor.assumeIsolated {
            observer(snapshot)
        }
    }
}
