import XCTest
import UIKit
import KadreUikit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

// Preuves phase 7 (Task 4) du drop UIKit — iOS SEUL : `UIDropSession`/`UIDropInteraction` sont
// `API_UNAVAILABLE(tvos)` (UIDragSession.h:19 ; `canImport(UIKit)` est VRAI sur tvOS et ne
// garde donc RIEN — la garde est `!os(tvOS)`, le fichier doit rester invisible de
// `KadreUikitDriverTvOsTests`).
//
// LIMITE SIMULATEUR (project.yml, note drop) : un drag CROSS-APP n'est PAS synthétisable
// in-process honnêtement (pas d'API de drag système programmable). La preuve du driver pilote
// donc le VRAI delegate installé par l'attach — l'objet exact qu'UIKit appellerait, fetché de la
// vue (`view.interactions.compactMap { $0 as? UIDropInteraction }.first`) — avec une FAUSSE de
// session conforme au protocole `UIDropSession` (la route ÉPINGLÉE par la spike Task 1 : le
// conformer Kotlin `UIDropSessionProtocol` est internal au module et ne traverse PAS l'en-tête).
// Chaque hook du protocole est appelé UNITAIREMENT dans l'ordre UIKit (canHandle → enter →
// update → exit/perform) : stimulus in-process honnête, jamais un événement fabriqué.
//
// Le côté CONSUMER est observé par un handler d'interaction ENREGISTREUR (DropConsumerRecorder,
// installé sur la surface par `installInteractionHandler`) : c'est le canal réel du runtime —
// l'`InteractionEvent.DropEntered` part DANS la callback du delegate, l'`AcceptDrop` du consumer
// est la décision d'admission (`context.request(action:)`), et l'état de l'offre (Presented →
// Accepted → TransferAvailable/Claimed/Terminated) est le StateFlow exporté de l'offre retenue.
#if canImport(UIKit) && !os(tvOS)

/// La fausse de session drop du driver (miroir du placeholder de la spike Task 1, portée ici par
/// les providers du test) : un `NSObject` conforme au protocole `UIDropSession`, dont les items
/// sont de VRAIS `UIDragItem` autour de VRAIS `NSItemProvider`. Les types auxquels la fausse
/// répond (`hasItemsConforming`) sont les types ENREGISTRÉS de ses providers — le même mensonge
/// que la plateforme, jamais plus.
final class DriverDropSession: NSObject, UIDropSession {

    let items: [UIDragItem]
    /// La position du drag dans la vue — lue par le delegate (`session.locationInView`) pour la
    /// conversion en point logique. Finie par construction (la règle M4 : une composante non
    /// finie n'est pas une position, le delegate n'ouvrirait rien).
    let dragPosition: CGPoint

    init(items: [UIDragItem], position: CGPoint) {
        self.items = items
        self.dragPosition = position
    }

    // MARK: - Fabriques (les trois payloads du plan + les deux formes refusées)

    /// Un drag TEXTE : `NSItemProvider(object: NSString)` — l'UTI enregistrée est
    /// `public.utf8-plain-text` (fait de la spike, asserté au canhandle-types).
    static func text(_ string: String) -> DriverDropSession {
        DriverDropSession(
            items: [UIDragItem(itemProvider: NSItemProvider(object: string as NSString))],
            position: CGPoint(x: 42, y: 17),
        )
    }

    /// Un drag URL : `NSItemProvider(item:typeIdentifier:)` — la route ÉPINGLÉE par la spike
    /// Task 1 (mesurée au rapport). FAIT DE MESURE SDK 27 (Task 4) : l'init moderne
    /// `NSItemProvider(object: NSURL)` enregistre bien `public.url` mais livre un NSData
    /// (représentation data de 86 octets, pas une NSURL) ; l'init typé livre LUI une `NSURL` —
    /// la livraison NORMALE de ce type (fait Task 3 : la route de lecture est la chaîne
    /// absolue, pas un mort-né byte-read). La fausse ment comme la plateforme sur CETTE route.
    static func url(_ url: URL) -> DriverDropSession {
        let provider = NSItemProvider(item: url as NSURL, typeIdentifier: "public.url")
        return DriverDropSession(items: [UIDragItem(itemProvider: provider)], position: CGPoint(x: 7, y: 9))
    }

    /// Un drag FILE-URL : `NSItemProvider(item:typeIdentifier:)` (init déprécié — la route de la
    /// spike pour poser une UTI exacte) — le descripteur File lit `public.file-url` et livre une
    /// `NSURL` dont la chaîne absolue est le contenu lisible (le contenu disque reste l'exit
    /// roadmap security-scoped, miroir AppKit).
    static func fileUrl(_ path: String) -> DriverDropSession {
        let provider = NSItemProvider(
            item: NSURL(fileURLWithPath: path),
            typeIdentifier: "public.file-url",
        )
        return DriverDropSession(items: [UIDragItem(itemProvider: provider)], position: CGPoint(x: 3, y: 4))
    }

    /// Un provider HORS TABLE : un `NSData` porté sous l'UTI `public.data` — que la table
    /// fermée de la Task 1 ne nomme PAS (`ukitDropSnapshot` rend null, le delegate répond faux
    /// et SAUTE l'item). C'est la forme « unknown-type » du plan.
    static func unknownData() -> DriverDropSession {
        let provider = NSItemProvider(item: NSData(), typeIdentifier: "public.data")
        return DriverDropSession(items: [UIDragItem(itemProvider: provider)], position: CGPoint(x: 1, y: 2))
    }

    /// Un drag VIDE : aucun item — `canHandle` doit répondre faux (aucun provider sondatable).
    static func empty() -> DriverDropSession {
        DriverDropSession(items: [], position: .zero)
    }

    // MARK: - Les exigences restantes du protocole (formes fixes, honnêtes)

    func location(in view: UIView) -> CGPoint { dragPosition }

    var allowsMoveOperation: Bool { false }

    var isRestrictedToDraggingApplication: Bool { false }

    func hasItemsConforming(toTypeIdentifiers typeIdentifiers: [String]) -> Bool {
        let registered = items.flatMap { $0.itemProvider.registeredTypeIdentifiers }
        return typeIdentifiers.contains { registered.contains($0) }
    }

    func canLoadObjects(ofClass aClass: NSItemProviderReading.Type) -> Bool { false }

    var localDragSession: UIDragSession? { nil }

    var progressIndicatorStyle: UIDropSessionProgressIndicatorStyle = .none

    func loadObjects(ofClass aClass: NSItemProviderReading.Type,
                     completion: @escaping ([NSItemProviderReading]) -> Void) -> Progress {
        // L'exigence BRUTE importée d'Obj-C (la variante générique est un overlay Swift).
        Progress()
    }

    var progress: Progress { Progress() }
}

/// L'enregistreur du côté consumer : le handler d'interaction que le test installe sur la
/// surface (`installInteractionHandler`). Le runtime l'appelle SYNCHRONIQUEMENT dans la callback
/// du delegate (le dispatch du drop est synchrone) ; la décision `accept` y pose EXACTEMENT
/// l'action `InteractionAction.AcceptDrop(offerId)` via `context.request` — l'admission réelle,
/// pas un raccourci. Le résultat brut du request (Success = outcome Committed sur le chemin Now
/// du port UIKit, Failure = le refus typé) est ENREGISTRÉ, ainsi que chaque offre reçue.
final class DropConsumerRecorder: NSObject, InteractionHandler {

    enum Decision { case accept, ignore }

    private let decision: Decision

    init(decision: Decision) {
        self.decision = decision
    }

    /// Les événements `DropEntered` reçus, DANS L'ORDRE (une entrée par cycle enter).
    private(set) var dropEvents: [InteractionEventDropEntered] = []

    /// Le verdict de CHAQUE `context.request` posé (`nil` quand la décision était d'ignorer).
    private(set) var requestCommitted: [Bool?] = []
    private(set) var requestFailureDescriptions: [String] = []

    /// Tout événement d'interaction NON drop (aucun attendu sur cette couture — compté, jamais
    /// avalé en silence).
    private(set) var foreignEventCount = 0

    func onInteraction(context: InteractionContext, event: InteractionEvent) {
        guard let drop = event as? InteractionEventDropEntered else {
            foreignEventCount += 1
            return
        }
        dropEvents.append(drop)
        guard decision == .accept else {
            requestCommitted.append(nil)
            return
        }
        let result = context.request(action: InteractionActionAcceptDrop(offerId: drop.offer.id))
        if let failure = (result as? KadreResultFailure)?.reason {
            requestCommitted.append(false)
            requestFailureDescriptions.append(String(describing: failure))
        } else {
            requestCommitted.append(true)
        }
    }

    /// L'offre du DERNIER DropEntered (la poignée du consumer sur l'offre vivante du cycle).
    var lastOffer: DropOffer? { dropEvents.last?.offer }
}

/// Les preuves phase 7 du flux drop — les scénarios du plan, NOMS FIGÉS (les ids de scénario
/// sont des méthodes de test verbatim : Task 5 corréle le JUnit `testClass`/`testName`).
final class KadreUikitDropIosTests: XCTestCase {

    /// Filet de sécurité ENTRE tests (miroir `KadreUikitInputTests.tearDown`).
    override func tearDown() {
        super.tearDown()
        for fixture in DriverFixture.shared.connected {
            guard let session = fixture.session else { continue }
            let state = session.state.value as? (any SessionState)
            guard state is SessionStateStarting || state is SessionStateRunning else {
                fixture.session = nil
                continue
            }
            session.requestStop()
            let terminated = expectation(description: "teardown: session terminated")
            session.awaitTermination { _, _ in terminated.fulfill() }
            wait(for: [terminated], timeout: 15)
            try? fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }
            fixture.session = nil
        }
    }

    // MARK: - Helpers (dupliqués des classes sœurs : méthodes internal d'instance)

    func primaryFixture() throws -> SceneFixture {
        let connected = Date().addingTimeInterval(25)
        while DriverFixture.shared.primary == nil {
            if Date() > connected {
                XCTFail("no scene fixture connected after 25s — the host app never connected its scene")
                throw NSError(domain: "KadreUikitDriver", code: 4,
                              userInfo: ["what": "primary scene never connected"])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        let fixture = try XCTUnwrap(DriverFixture.shared.primary)
        try fixture.waitUntilReady()
        return fixture
    }

    /// attach nominal + installation du consumer + fetch de l'interaction drop VRAIE de la vue.
    /// Retourne (fixture, surface, interaction, delegate, recorder, registration) — la session
    /// est posée sur `fixture.session` (le filet tearDown la reprend).
    func attachedDropSurface(
        decision: DropConsumerRecorder.Decision,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> (SceneFixture, any HostSurface, UIDropInteraction, UIDropInteractionDelegate, DropConsumerRecorder, InteractionRegistration) {
        let fixture = try primaryFixture()
        let window = try XCTUnwrap(fixture.window, file: file, line: line)
        let view = try XCTUnwrap(fixture.observedView, file: file, line: line)
        let runsBeforeAttach = fixture.applicationRuns
        let result = KadreIos.shared.attach(
            windowScene: fixture.scene,
            window: window,
            surfaceView: view,
            applicationFactory: DriverApplication(fixture: fixture),
            policy: KadrePolicies.shared.Default,
            windowSceneProvider: nil, // static framework : pas de convenience du défaut Kotlin
        )
        if let failure = (result as? KadreResultFailure)?.reason {
            XCTFail("attach failed unexpectedly: \(failure)", file: file, line: line)
        }
        let success = try XCTUnwrap(result as? KadreResultSuccess<AnyObject>, file: file, line: line)
        let session = try XCTUnwrap(success.value as? (any KadreSession), file: file, line: line)
        fixture.session = session
        try fixture.waitUntilApplicationFacts(minimumRuns: runsBeforeAttach + 1, timeout: 10)
        let surface = try XCTUnwrap(fixture.primarySurface, file: file, line: line)

        // L'interaction drop VRAIE posée par l'attach — l'objet exact qu'UIKit appellerait.
        let interaction = try XCTUnwrap(
            view.interactions.compactMap { $0 as? UIDropInteraction }.first,
            "the attach must have installed one UIDropInteraction on the host view",
            file: file, line: line,
        )
        let delegate = try XCTUnwrap(interaction.delegate, "the interaction carries the Kadre delegate",
                                     file: file, line: line)

        // Le consumer ENREGISTREUR, installé sur la surface par la vraie API d'installation.
        let recorder = DropConsumerRecorder(decision: decision)
        let install = surface.installInteractionHandler(handler: recorder)
        if let failure = (install as? KadreResultFailure)?.reason {
            XCTFail("installInteractionHandler failed unexpectedly: \(failure)", file: file, line: line)
        }
        let installSuccess = try XCTUnwrap(install as? KadreResultSuccess<AnyObject>, file: file, line: line)
        let registration = try XCTUnwrap(
            installSuccess.value as? InteractionRegistration,
            "installInteractionHandler returns the registration", file: file, line: line,
        )
        return (fixture, surface, interaction, delegate, recorder, registration)
    }

    /// requestStop + awaitTermination + état exporté Terminated (pattern phase 1).
    func stopAndAwaitTermination(
        _ fixture: SceneFixture,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws {
        let session = try XCTUnwrap(fixture.session, file: file, line: line)
        let terminated = expectation(description: "session terminated")
        session.requestStop()
        session.awaitTermination { outcome, error in
            XCTAssertNil(error, "awaitTermination error", file: file, line: line)
            XCTAssertNotNil(outcome, "awaitTermination delivered no outcome", file: file, line: line)
            terminated.fulfill()
        }
        wait(for: [terminated], timeout: 20)
        try fixture.waitUntilSessionState(timeout: 10) { $0 is SessionStateTerminated }
    }

    /// L'état de l'offre, typé (le StateFlow exporté se lit en `.value`).
    func offerState(
        _ offer: DropOffer,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> DropOfferState {
        try XCTUnwrap(offer.state.value as? DropOfferState,
                      "the offer state flow must carry a DropOfferState", file: file, line: line)
    }

    /// Attente BORNÉE d'un état d'offre, en pompant le run loop (jamais de sommeil nu — pattern
    /// fixture). Retourne le PREMIER état satisfaisant ; l'échec embarque le dernier état vu.
    func waitOfferState(
        _ offer: DropOffer,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ predicate: @escaping (DropOfferState) -> Bool,
    ) throws -> DropOfferState {
        let deadline = Date().addingTimeInterval(timeout)
        var last: DropOfferState?
        while true {
            if let state = offer.state.value as? DropOfferState {
                last = state
                if predicate(state) { return state }
            }
            if Date() > deadline {
                throw NSError(domain: "KadreUikitDriver", code: 5, userInfo: [
                    "what": "offer state not reached in \(timeout)s",
                    "last": String(describing: last ?? nil),
                    "trace": DriverTrace.recent(),
                ])
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// Le claim de transfert conduit par la vraie API suspendue (completion + pompe XCTest —
    /// jamais un sommeil nu). Retourne le KadreResult brut : Swift y lit le transfert ou la
    /// failure typée (`AlreadyInUse`, `Closed(DropTransfer)`).
    @discardableResult
    func claimTransfer(
        _ offer: DropOffer,
        timeout: TimeInterval = 15,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> KadreResult {
        let claimed = expectation(description: "claimTransfer completed")
        var outcome: KadreResult?
        offer.claimTransfer { result, error in
            XCTAssertNil(error, "claimTransfer error", file: file, line: line)
            outcome = result
            claimed.fulfill()
        }
        wait(for: [claimed], timeout: timeout)
        return try XCTUnwrap(outcome, "claimTransfer delivered no result", file: file, line: line)
    }

    /// Le collecteur d'octets côté Swift — le `suspend (ByteArray) -> Unit` Kotlin est un
    /// `KotlinSuspendFunction1` à implémenter ; chaque tranche arrive en `KotlinByteArray`.
    final class ByteCollector: NSObject, KotlinSuspendFunction1 {
        private(set) var chunks: [Data] = []

        func invoke(p1: Any?, completionHandler: @escaping (Any?, Error?) -> Void) {
            if let bytes = p1 as? KotlinByteArray {
                var data = Data()
                data.reserveCapacity(Int(bytes.size))
                for index in 0..<bytes.size {
                    data.append(UInt8(bitPattern: bytes.get(index: index)))
                }
                chunks.append(data)
            }
            completionHandler(nil, nil)
        }

        var reassembled: String {
            String(data: chunks.reduce(Data(), +), encoding: .utf8) ?? "<non-utf8>"
        }

        var totalBytes: Int { chunks.reduce(0) { $0 + $1.count } }
    }

    /// La lecture d'un item droppé conduit par la vraie API suspendue (completion + pompe).
    /// Retourne (KadreResult brut, collecteur) — le collecteur porte les tranches reçues.
    @discardableResult
    func collectBytes(
        _ item: DroppedItem,
        maxBytes: Int64,
        timeout: TimeInterval = 15,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> (KadreResult, ByteCollector) {
        let collector = ByteCollector()
        let done = expectation(description: "collectBytes completed")
        var outcome: KadreResult?
        item.collectBytes(maxBytes: maxBytes, collector: collector) { result, error in
            XCTAssertNil(error, "collectBytes error", file: file, line: line)
            outcome = result
            done.fulfill()
        }
        wait(for: [done], timeout: timeout)
        return (try XCTUnwrap(outcome, "collectBytes delivered no result", file: file, line: line), collector)
    }

    /// Le cycle enter → (décision consumer) → perform : les TROIS hooks UIKit dans l'ordre réel
    /// (canHandle, enter, update) puis le drop. Retourne la proposition de la DERNIÈRE update.
    @discardableResult
    func enterUpdateAndPerform(
        interaction: UIDropInteraction,
        delegate: UIDropInteractionDelegate,
        session: DriverDropSession,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) throws -> UIDropProposal {
        let canHandle = delegate.dropInteraction?(interaction, canHandle: session)
        XCTAssertEqual(canHandle, true, "the text/url drag must be handleable", file: file, line: line)
        delegate.dropInteraction?(interaction, sessionDidEnter: session)
        let proposal = try XCTUnwrap(
            delegate.dropInteraction?(interaction, sessionDidUpdate: session),
            "sessionDidUpdate must propose an operation for a live drag", file: file, line: line,
        )
        delegate.dropInteraction?(interaction, performDrop: session)
        return proposal
    }

    // MARK: - Preuves (noms de scénarios FIGÉS pour Task 5)

    /// uikit-drop-canhandle-types — la sonde à types enregistrés du VRAI delegate : un provider
    /// texte (public.utf8-plain-text) → vrai ; un provider hors table (public.data, la forme
    /// unknown-type du plan) → faux ; une session vide → faux. Chaque livraison est comptée
    /// (delta du compteur = le nombre d'appels acceptés par le delegate détaché-jamais).
    func test_uikit_drop_canhandle_types() throws {
        let (_, _, interaction, delegate, _, _) = try attachedDropSurface(decision: .ignore)
        let view = try XCTUnwrap(DriverFixture.shared.primary?.observedView)
        XCTAssertEqual(view.interactions.compactMap { $0 as? UIDropInteraction }.count, 1,
                       "exactly one drop interaction is installed per attach")

        let eventsBefore = KadreUikitSurfaceDriver.shared.dropEventCount

        XCTAssertTrue(delegate.dropInteraction?(interaction, canHandle: DriverDropSession.text("hello")) ?? false,
                      "a utf8 text provider must be handleable")
        XCTAssertFalse(delegate.dropInteraction?(interaction, canHandle: DriverDropSession.unknownData()) ?? true,
                       "an out-of-table provider (public.data) must NOT be handleable — the closed "
                           + "table loses the type, it never approximates it")
        XCTAssertFalse(delegate.dropInteraction?(interaction, canHandle: DriverDropSession.empty()) ?? true,
                       "an empty session must NOT be handleable")

        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropEventCount, eventsBefore + 3,
                       "each canHandle delivery is counted exactly once")
    }

    /// uikit-drop-offer-presented — l'entrée d'un drag présente l'offre au consumer : le
    /// `InteractionEvent.DropEntered` arrive dans le handler ENREGISTREUR (le canal runtime
    /// réel), l'`AcceptDrop` du consumer y est COMMIS (le chemin Now du port : Success = outcome
    /// Committed), l'offre retenue passe Presented → Accepted, l'interaction est comptée UNE
    /// fois par attach, et la proposition native d'une session vivante est `.copy` — le rendu
    /// de `holdsActiveOffer` par la table des opérations du plan (la réponse native EST le fait
    /// « une offre vit » ; le prédicat interne Kotlin n'est pas exporté, son rendu l'est).
    func test_uikit_drop_offer_presented() throws {
        // La BASELINE du compteur avant l'attach (le delta d'installation se lit sur elle).
        let installsBefore = KadreUikitSurfaceDriver.shared.dropInstallCount
        let (fixture, _, interaction, delegate, recorder, registration) =
            try attachedDropSurface(decision: .accept)
        defer { registration.close() }

        let session = DriverDropSession.text("hello")
        XCTAssertTrue(delegate.dropInteraction?(interaction, canHandle: session) ?? false,
                      "the text drag must be handleable")
        delegate.dropInteraction?(interaction, sessionDidEnter: session)

        // Le DropEntered du consumer — l'événement d'interaction attendu, avec l'offre.
        XCTAssertEqual(recorder.dropEvents.count, 1,
                       "the drop enter must present exactly one InteractionEvent.DropEntered")
        let drop = try XCTUnwrap(recorder.dropEvents.first)
        let offer = try XCTUnwrap(drop.offer, "the event carries the runtime-owned offer")
        XCTAssertEqual(drop.position.x, 42.0, accuracy: 0.001,
                       "the drag position crosses as a logical point (view space IS logical space)")
        XCTAssertEqual(drop.position.y, 17.0, accuracy: 0.001, "the drag position y crosses as-is")
        XCTAssertEqual(recorder.requestCommitted, [true],
                       "the consumer's AcceptDrop must commit (the Now path of the UIKit port)")

        // L'offre retenue : acceptée (Presented → Accepted, synchronement dans la callback).
        let state = try waitOfferState(offer) { $0 is DropOfferStateAccepted }
        XCTAssertTrue(state is DropOfferStateAccepted, "the accepted offer state, last=\(state)")
        XCTAssertEqual(offer.items.count, 1, "the offer carries the one text item")
        let descriptor = try XCTUnwrap(offer.items.first)
        XCTAssertEqual(descriptor.mimeTypes, ["text/plain"],
                       "the closed table maps public.utf8-plain-text to text/plain")
        XCTAssertEqual(descriptor.kind, DropItemKind.text, "the item kind is Text")
        XCTAssertNil(descriptor.sizeBytes, "no provider size is readable pre-delivery — null, never guessed")

        // Le compteur d'installation : exactement UN par attach.
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropInstallCount, installsBefore + 1,
                       "exactly one drop interaction install per attach")

        // La proposition native : `.copy` pour une offre vivante — JAMAIS `.move`.
        let proposal = try XCTUnwrap(delegate.dropInteraction?(interaction, sessionDidUpdate: session))
        XCTAssertEqual(proposal.operation, .copy,
                       "a held offer proposes Copy (the plan table; Move is never proposed)")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-drop-accepted-bounded — le flux accepté BOUT EN BOUT, borné : claim UNE fois (le
    /// second échoue `AlreadyInUse(DropTransfer)`), lecture bornée `maxBytes` petit →
    /// `ResourceLimitExceeded(DropItem, maxBytes)`, lecture à taille inconnue streame à l'EOF
    /// ("hello" réassemblé). PUIS l'obligation de re-revue Task 3 : le descripteur Uri lit
    /// `public.url` (la chaîne ABSOLUE de l'URL — le mime text/uri-list lisible, jamais le
    /// byte-read mort-né) et le descripteur File lit `public.file-url` (la chaîne de l'URL
    /// fichier ; le contenu disque security-scoped reste l'exit roadmap).
    func test_uikit_drop_accepted_bounded() throws {
        let (fixture, _, interaction, delegate, recorder, registration) =
            try attachedDropSurface(decision: .accept)
        defer { registration.close() }

        // --- Cycle 1 : texte — claim unique, double claim refusé, lecture bornée, EOF ---
        _ = try enterUpdateAndPerform(interaction: interaction, delegate: delegate,
                                      session: DriverDropSession.text("hello"))
        let textOffer = try XCTUnwrap(recorder.lastOffer)
        try waitOfferState(textOffer) { $0 is DropOfferStateTransferAvailable }
        let textClaim = try claimTransfer(textOffer)
        let transfer = try XCTUnwrap((textClaim as? KadreResultSuccess<AnyObject>)?.value as? DropTransfer,
                                     "the first claim must succeed: \(textClaim)")

        let secondClaim = try claimTransfer(textOffer)
        let alreadyInUse = try XCTUnwrap((secondClaim as? KadreResultFailure)?.reason as? KadreFailureAlreadyInUse,
                                         "the second claim must fail AlreadyInUse, got \(secondClaim)")
        XCTAssertEqual(alreadyInUse.resource, KadreResourceKind.droptransfer,
                       "the contested resource is the drop transfer")

        let textItem = try XCTUnwrap(transfer.items.first)
        XCTAssertEqual(textItem.descriptor.mimeTypes, ["text/plain"], "the text item descriptor mime")
        XCTAssertEqual(textItem.descriptor.kind, DropItemKind.text, "the text item descriptor kind")

        let bounded = try collectBytes(textItem, maxBytes: 2)
        let limit = try XCTUnwrap((bounded.0 as? KadreResultFailure)?.reason as? KadreFailureResourceLimitExceeded,
                                  "a read bounded below the payload must fail ResourceLimitExceeded, got \(bounded.0)")
        XCTAssertEqual(limit.resource, KadreResourceKind.dropitem, "the limited resource is the drop item")
        XCTAssertEqual(limit.limit, 2, "the reported limit is the maxBytes of the read")
        XCTAssertEqual(bounded.1.totalBytes, 0, "the bounded read delivers nothing past the limit")

        let full = try collectBytes(textItem, maxBytes: 1_048_576)
        XCTAssertFalse(full.0 is KadreResultFailure,
                      "the unknown-size read streams to EOF (Replayable): \(full.0)")
        XCTAssertEqual(full.1.reassembled, "hello", "the text payload reassembles exactly")

        // --- Cycle 2 : URL — le descripteur Uri lit public.url, la chaîne ABSOLUE ---
        let url = URL(string: "https://kadre.example/drop-target")!
        _ = try enterUpdateAndPerform(interaction: interaction, delegate: delegate,
                                      session: DriverDropSession.url(url))
        let urlOffer = try XCTUnwrap(recorder.lastOffer)
        XCTAssertFalse(urlOffer.id.isEqual(textOffer.id), "a new drag opens a NEW offer id")
        try waitOfferState(urlOffer) { $0 is DropOfferStateTransferAvailable }
        let urlClaim = try claimTransfer(urlOffer)
        let urlTransfer = try XCTUnwrap((urlClaim as? KadreResultSuccess<AnyObject>)?.value as? DropTransfer,
                                        "the URL offer claim must succeed: \(urlClaim)")
        let urlItem = try XCTUnwrap(urlTransfer.items.first)
        XCTAssertEqual(urlItem.descriptor.mimeTypes, ["text/uri-list"],
                       "the closed table maps public.url to text/uri-list")
        XCTAssertEqual(urlItem.descriptor.kind, DropItemKind.uri, "the URL item kind is Uri")
        let urlRead = try collectBytes(urlItem, maxBytes: 1_048_576)
        XCTAssertFalse(urlRead.0 is KadreResultFailure,
                      "the uri-list mime is READABLE through the NSURL route (Task-3 fix): \(urlRead.0)")
        XCTAssertEqual(urlRead.1.reassembled, url.absoluteString,
                       "a URL drag reads back its ABSOLUTE STRING — the exact content text/uri-list announces")

        // --- Cycle 3 : file-url — le descripteur File lit public.file-url (la chaîne) ---
        _ = try enterUpdateAndPerform(interaction: interaction, delegate: delegate,
                                      session: DriverDropSession.fileUrl("/tmp/kadre-driver-drop.txt"))
        let fileOffer = try XCTUnwrap(recorder.lastOffer)
        try waitOfferState(fileOffer) { $0 is DropOfferStateTransferAvailable }
        let fileClaim = try claimTransfer(fileOffer)
        let fileTransfer = try XCTUnwrap((fileClaim as? KadreResultSuccess<AnyObject>)?.value as? DropTransfer,
                                         "the file-url offer claim must succeed: \(fileClaim)")
        let fileItem = try XCTUnwrap(fileTransfer.items.first)
        XCTAssertEqual(fileItem.descriptor.mimeTypes, ["application/octet-stream"],
                       "the closed table maps public.file-url to application/octet-stream")
        XCTAssertEqual(fileItem.descriptor.kind, DropItemKind.file, "the file-url item kind is File")
        let fileRead = try collectBytes(fileItem, maxBytes: 1_048_576)
        XCTAssertFalse(fileRead.0 is KadreResultFailure,
                      "the File descriptor reads through the NSURL route: \(fileRead.0)")
        XCTAssertEqual(fileRead.1.reassembled, NSURL(fileURLWithPath: "/tmp/kadre-driver-drop.txt").absoluteString,
                       "the File-kind UTI actually read is public.file-url → the absolute file URL string "
                           + "(the disk contents remain the documented roadmap exit)")

        try stopAndAwaitTermination(fixture)
    }

    /// uikit-drop-rejected — le consumer IGNORE l'offre : le runtime la TERMINE `Rejected` (la
    /// décision d'admission est celle du consumer — jamais une admission fabriquée), et la
    /// proposition native vire à `.cancel` (plus aucune offre tenue). C'est l'assertion
    /// PROPRIE de ce scénario que la sentinelle `uikit-drop-no-fabricated-admission` tue.
    func test_uikit_drop_rejected() throws {
        let (fixture, _, interaction, delegate, recorder, registration) =
            try attachedDropSurface(decision: .ignore)
        defer { registration.close() }

        let session = DriverDropSession.text("hello")
        XCTAssertTrue(delegate.dropInteraction?(interaction, canHandle: session) ?? false,
                      "canHandle answers on the type probe alone — the refusal is the consumer's")
        delegate.dropInteraction?(interaction, sessionDidEnter: session)

        XCTAssertEqual(recorder.dropEvents.count, 1,
                       "the offer IS presented to the consumer (the refusal is its decision)")
        XCTAssertEqual(recorder.requestCommitted, [nil],
                       "the ignoring recorder poses no request")
        let offer = try XCTUnwrap(recorder.dropEvents.first?.offer)
        let state = try waitOfferState(offer) { $0 is DropOfferStateTerminated }
        let terminated = try XCTUnwrap((state as? DropOfferStateTerminated)?.reason,
                                       "the ignored offer must terminate, got \(state)")
        XCTAssertTrue(terminated is DropOfferTerminationReasonRejected,
                      "an offer the consumer never accepts is terminated Rejected, got \(terminated)")
        XCTAssertTrue(delegate.dropInteraction?(interaction, canHandle: session) ?? false,
                      "sanity: canHandle keeps answering the type probe — the refusal was the "
                          + "consumer's decision, not the probe's")

        // La proposition native SANS offre tenue : `.cancel` — l'assertion propre du scénario.
        let proposal = try XCTUnwrap(delegate.dropInteraction?(interaction, sessionDidUpdate: session))
        XCTAssertEqual(proposal.operation, .cancel,
                       "a rejected drag proposes Cancel — never a fabricated Copy")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-drop-exit-reenter — la sortie termine l'offre `LeftSurface` (le claim de l'ancienne
    /// offre répond alors `Closed(DropTransfer)` — le chemin de lecture est fermé avec elle),
    /// et la RÉ-ENTRÉE ouvre une NOUVELLE offre (id non égal) ; l'entrée qui REMPLACE une offre
    /// encore active (sortie jamais livrée) termine l'ancienne `LeftSurface` à la présentation —
    /// le registre des offres ne retient jamais deux drags.
    func test_uikit_drop_exit_reenter() throws {
        let (fixture, _, interaction, delegate, recorder, registration) =
            try attachedDropSurface(decision: .accept)
        defer { registration.close() }

        // Entrée 1 : acceptée, vivante.
        let first = DriverDropSession.text("first")
        XCTAssertTrue(delegate.dropInteraction?(interaction, canHandle: first) ?? false)
        delegate.dropInteraction?(interaction, sessionDidEnter: first)
        let offer1 = try XCTUnwrap(recorder.dropEvents.last?.offer)
        try waitOfferState(offer1) { $0 is DropOfferStateAccepted }

        // Sortie : LeftSurface, et le chemin de lecture de l'ancienne offre est fermé.
        delegate.dropInteraction?(interaction, sessionDidExit: first)
        let exited = try waitOfferState(offer1) { $0 is DropOfferStateTerminated }
        XCTAssertTrue((exited as? DropOfferStateTerminated)?.reason is DropOfferTerminationReasonLeftSurface,
                      "the exited offer terminates LeftSurface, got \(exited)")
        let staleClaim = try claimTransfer(offer1)
        let closed = try XCTUnwrap((staleClaim as? KadreResultFailure)?.reason as? KadreFailureClosed,
                                   "the old transfer path is closed to claims, got \(staleClaim)")
        XCTAssertEqual(closed.resource, KadreResourceKind.droptransfer,
                       "the closed resource is the drop transfer")

        // Ré-entrée : une NOUVELLE offre (id non égal), fraîchement acceptée.
        let second = DriverDropSession.text("second")
        delegate.dropInteraction?(interaction, sessionDidEnter: second)
        XCTAssertEqual(recorder.dropEvents.count, 2, "the re-enter presents a new DropEntered")
        let offer2 = try XCTUnwrap(recorder.dropEvents.last?.offer)
        XCTAssertFalse(offer2.id.isEqual(offer1.id), "the re-enter carries a NEW offer id")
        try waitOfferState(offer2) { $0 is DropOfferStateAccepted }

        // Remplacement : une entrée SANS sortie (le drag remplacé) termine l'active
        // `LeftSurface` à la présentation de la suivante — une offre active au plus.
        let third = DriverDropSession.text("third")
        delegate.dropInteraction?(interaction, sessionDidEnter: third)
        let replaced = try waitOfferState(offer2) { $0 is DropOfferStateTerminated }
        XCTAssertTrue((replaced as? DropOfferStateTerminated)?.reason is DropOfferTerminationReasonLeftSurface,
                      "the replaced offer terminates LeftSurface at the new presentation, got \(replaced)")
        XCTAssertEqual(recorder.dropEvents.count, 3, "the replacing enter presents its own DropEntered")
        let offer3 = try XCTUnwrap(recorder.dropEvents.last?.offer)
        XCTAssertFalse(offer3.id.isEqual(offer2.id), "the replacing offer is a new id")
        try waitOfferState(offer3) { $0 is DropOfferStateAccepted }
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-drop-performed — le drop performé : l'offre passe `TransferAvailable` (le fait
    /// créé DANS le même réducteur qui publie `InputEvent.Dropped` — le flow d'input est
    /// Kotlin-seul, sans collecteur Swift ; l'état de l'offre est le fait runtime observable du
    /// driver), le transfert est claimable, et la « réponse vraie » du delegate — dont le
    /// sélecteur Obj-C rend VOID (UIDropInteraction.h:164, fait Task 3) — se lit sur l'offre
    /// consommée : une update après le drop propose `.cancel` (plus rien n'est tenu).
    func test_uikit_drop_performed() throws {
        let (fixture, _, interaction, delegate, recorder, registration) =
            try attachedDropSurface(decision: .accept)
        defer { registration.close() }

        let session = DriverDropSession.text("perform-me")
        let proposal = try enterUpdateAndPerform(interaction: interaction, delegate: delegate, session: session)
        XCTAssertEqual(proposal.operation, .copy, "the live drag proposed Copy before the drop")

        let offer = try XCTUnwrap(recorder.lastOffer)
        let state = try waitOfferState(offer) { $0 is DropOfferStateTransferAvailable }
        XCTAssertTrue(state is DropOfferStateTransferAvailable,
                      "the performed drop exposes the transfer (the InputEvent.Dropped-side fact)")

        // Transfert claimable — et le delegate a CONSOMMÉ l'offre : l'update suivante annule.
        let claim = try claimTransfer(offer)
        let transfer = try XCTUnwrap((claim as? KadreResultSuccess<AnyObject>)?.value as? DropTransfer,
                                     "the transfer must be claimable after the drop: \(claim)")
        XCTAssertEqual(transfer.items.count, 1, "the transfer carries the one dropped item")
        let afterProposal = try XCTUnwrap(delegate.dropInteraction?(interaction, sessionDidUpdate: session))
        XCTAssertEqual(afterProposal.operation, .cancel,
                       "after the performed drop nothing is held — the void selector's local truth")

        // Un SECOND perform (le drag est déjà consommé) ne republie rien : l'offre reste
        // Claimed, le transfert reste le même (le second claim répondrait AlreadyInUse).
        delegate.dropInteraction?(interaction, performDrop: session)
        let settled = try offerState(offer)
        XCTAssertTrue(settled is DropOfferStateClaimed,
                      "a second perform stimulus on a consumed offer changes nothing, got \(settled)")
        try stopAndAwaitTermination(fixture)
    }

    /// uikit-drop-teardown-mid-offer — le close EN PLEIN offre : l'offre vivante est terminée
    /// `OwnerClosed` (le propriétaire a fermé), la vue retrouve ZÉRO interaction drop, et toute
    /// livraison TARDIVE sur le delegate détaché est un no-op compté ZÉRO (canHandle faux, plus
    /// d'offre présentée, `dropEventCount` figé — jamais de stimulus après le teardown, jamais
    /// de crash).
    func test_uikit_drop_teardown_mid_offer() throws {
        let (fixture, _, interaction, delegate, recorder, registration) =
            try attachedDropSurface(decision: .accept)
        defer { registration.close() }
        let view = try XCTUnwrap(fixture.observedView)

        let session = DriverDropSession.text("torn-down-mid-offer")
        XCTAssertTrue(delegate.dropInteraction?(interaction, canHandle: session) ?? false)
        delegate.dropInteraction?(interaction, sessionDidEnter: session)
        let offer = try XCTUnwrap(recorder.dropEvents.last?.offer)
        try waitOfferState(offer) { $0 is DropOfferStateAccepted }

        let teardownsBefore = KadreUikitSurfaceDriver.shared.dropTeardownCount
        try stopAndAwaitTermination(fixture)

        // L'offre vivante au close : terminée OwnerClosed par le propriétaire.
        let state = try waitOfferState(offer) { $0 is DropOfferStateTerminated }
        XCTAssertTrue((state as? DropOfferStateTerminated)?.reason is DropOfferTerminationReasonOwnerClosed,
                      "an offer live at session close terminates OwnerClosed, got \(state)")

        // Zéro interaction drop résiduelle sur la vue du host.
        XCTAssertEqual(view.interactions.compactMap { $0 as? UIDropInteraction }.count, 0,
                       "the teardown removes the drop interaction — nothing residual")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropTeardownCount, teardownsBefore + 1,
                       "exactly one teardown per session close")

        // Les livraisons TARDIVES : le delegate détaché refuse tout, et ne COMPTE plus rien.
        let eventsAtTeardown = KadreUikitSurfaceDriver.shared.dropEventCount
        let eventsSeen = recorder.dropEvents.count
        XCTAssertFalse(delegate.dropInteraction?(interaction, canHandle: session) ?? true,
                       "a detached delegate answers false (the stale-guard)")
        delegate.dropInteraction?(interaction, sessionDidEnter: session)
        delegate.dropInteraction?(interaction, performDrop: session)
        delegate.dropInteraction?(interaction, sessionDidUpdate: session)
        RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        XCTAssertEqual(recorder.dropEvents.count, eventsSeen,
                       "no late enter can present a new offer after teardown")
        XCTAssertEqual(KadreUikitSurfaceDriver.shared.dropEventCount, eventsAtTeardown,
                       "late deliveries past the teardown are silent no-ops — the event counter freezes")
    }
}

#endif


