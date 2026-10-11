import XCTest
#if canImport(KadreConsumerIos)
@testable import KadreConsumerIos
#elseif canImport(KadreConsumerTvOs)
@testable import KadreConsumerTvOs
#endif

/// La preuve Task 2, LES DEUX familles (iOS + tvOS) — le consumer externe lie DEUX
/// frameworks Kotlin STATIQUES (`KadreUikit.xcframework` distribution + le framework du
/// module applicatif Kotlin du consumer, même nom/même préfixe Obj-C) sans embed :
///  1. LA PREUVE ANTI-DUPLICATION est le lancement lui-même — le lien deux-frameworks ne
///     doit survivre qu'avec UNE seule copie de chaque classe et du runtime (un doublon
///     est refusé par ld, ou loggé/crashé au lancement : `Class X is implemented in
///     both…`) ; ce test tourne DANS l'app lancée, donc le lancement a survécu.
///  2. LA PREUVE D'IDENTITÉ DE TYPES : la factory produite par le module applicatif
///     Kotlin (`ConsumerApplicationFactory`) a été instanciée en Swift et acceptée par
///     `KadreIos.shared.attach(applicationFactory:)` — le compilateur n'accepte le
///     passage que si les deux fragments exposent le même type Obj-C (préfixe
///     `KadreUikit` identique), et l'attach réussi prouve que l'identité survit au lien.
///  3. LE CYCLE DE VIE : la session passe à `Running` (l'application consumer tourne),
///     puis l'application s'arrête ELLE-MÊME (`Stopped(ApplicationRequested)`) et la
///     session atteint `Terminated`.
///
/// NB : ce bundle de test ne lie AUCUN framework Kotlin — les archives statiques sont
/// liées exactement une fois, dans l'app hôte ; les faits Kotlin sont exposés par
/// `ConsumerSceneStore` en descriptions Swift primitives.
final class ConsumerAttachTests: XCTestCase {

    /// uikit-consumer-attach-runs-and-stops-itself — le scénario de bout en bout du
    /// squelette (attach → Running → self-stop → Terminated).
    func testConsumerFactoryAttachesRunsAndStopsItself() throws {
        let store = ConsumerSceneStore.shared

        // La scène primaire se connecte au lancement de l'app hôte ; l'attach survient au
        // `willConnectTo`. Attente bornée de son résultat — jamais un échec instantané sur
        // un process encore en train de démarrer.
        let deadline = Date().addingTimeInterval(25)
        while store.sessionObject == nil && store.attachFailureDescription == nil {
            if Date() > deadline {
                XCTFail("no attach within 25s — the host app never attached its primary scene")
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }

        // 1. L'attach a réussi : session non-nil, aucune raison d'échec, fenêtre et vue posées.
        XCTAssertNil(store.attachFailureDescription,
                     "attach must succeed: \(store.attachFailureDescription ?? "-")")
        let session = try XCTUnwrap(store.sessionObject, "attach must deliver a session")
        XCTAssertNotNil(session)
        XCTAssertNotNil(store.window)
        XCTAssertNotNil(store.surfaceView)
        // La classe de la factory vient du module applicatif Kotlin (lié statiquement,
        // préfixe Obj-C `KadreUikit` identique à la distribution — consumer-kmp/build.gradle.kts).
        XCTAssertTrue(store.factoryClassName?.hasSuffix("ConsumerApplicationFactory") == true,
                      "the factory passed to attach must be the Kotlin application module's one, got \(store.factoryClassName ?? "nil")")

        // 2. L'application consumer s'est exécutée : la session a traversé Running.
        try store.waitUntilState("Terminated", timeout: 15)
        XCTAssertTrue(store.observedStateDescriptions.contains("Running"),
                      "the session must reach Running before the self-stop; states=\(store.observedStateDescriptions)")
        XCTAssertEqual(store.lastStateDescription, "Terminated")

        // 3. L'arrêt vient de l'APPLICATION consumer (pas du host, pas d'un timeout) :
        //    outcome `Stopped(ApplicationRequested)`, sans erreur de terminaison.
        XCTAssertNil(store.terminationErrorDescription,
                     "awaitTermination must complete without error")
        XCTAssertEqual(store.outcomeDescription, "Stopped",
                       "outcome=\(store.outcomeDescription ?? "nil")")
        XCTAssertEqual(store.stopReasonDescription, "ApplicationRequested",
                       "the consumer application must have requested the stop itself; got \(store.stopReasonDescription ?? "nil")")
    }
}
