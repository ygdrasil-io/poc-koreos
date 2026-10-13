import XCTest
import UIKit

// SPIKE phase 7 Task 1 — contenu RETENU (revue finale) pour sa mesure SDK unique : le fait
// Task 1 qu'un `NSItemProvider(object: NSString)` enregistre EXACTEMENT `public.utf8-plain-text`
// (première UTI enregistrée) — la prémisse de la ligne Texte de la table fermée UTI→mime
// (`UIKitDropMapping.kt`), épinglée ici sur la brute du provider, avant tout mapping Kadre.
//
// La FAUSSE de session drop du spike (`FakeDropSession`) est SUPPRIMÉE : doublon exact de
// `DriverDropSession` (KadreUikitDropIosTests.swift), la preuve COMPORTEMENTALE (drop stimulus
// → vrai delegate → port) vit là-bas, sur la classe du driver. La provenance Task 1 de la
// route y est documentée en tête : le conformer Kotlin de `UIDropSessionProtocol` est internal
// au module et ne traverse PAS l'en-tête du framework vers le driver Swift — les tests driver
// pilotent leurs drops avec une classe Swift qui ment comme la plateforme (un `NSObject`
// conforme au protocole `UIDropSession`, passable au hook `dropInteraction:canHandleSession:`
// du delegate).
//
// La garde `os(iOS)` reste la frontière de plateforme TELLE QUELLE : `UIDropSession` et
// `UIDropInteraction` sont `API_UNAVAILABLE(tvos)` (UIDragSession.h:19 — la même absence que le
// klib tvOS côté Kotlin, 0 occurrence vérifiée) ; `canImport(UIKit)` est VRAI sur tvOS et ne
// garde donc RIEN ici — `tests/Shared` compile aussi dans KadreUikitDriverTvOsTests, et ce
// fichier doit y rester invisible.
#if os(iOS)

/// La mesure Task 1 retenue : la brute du provider, avant tout mapping Kadre.
final class KadreUikitDropSpikeTests: XCTestCase {

    func testNSStringProviderRegistersUtf8PlainTextSpike() {
        let provider = NSItemProvider(object: "kadre-drop-spike" as NSString)
        XCTAssertEqual(provider.registeredTypeIdentifiers.first, "public.utf8-plain-text",
                       "an NSString provider registers its text as utf8 plain text — the exact "
                           + "Text row of the closed UTI table")
    }
}

#endif
