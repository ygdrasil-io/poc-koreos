import XCTest
import UIKit
import ReplayKit

// SPIKE phase 9 Task 1 — les faits Simulator du chemin capture, mesurés sur la brute SDK
// AVANT toute couture Kadre (autonome : aucun import Kadre). TROIS mesures retenues, dont
// DEUX ont FALSIFIÉ l'attente du brief — c'est le rôle du spike : enregistrer la vérité.
//
// 1. `RPScreenRecorder.shared().isAvailable` — le brief attendait `false` ; la MESURE sur
//    cet hôte (Xcode 27.0 27A266a, runtime iOS 27.0) rend `true`. Enregistré verbatim, la
//    valeur n'est PAS épinglée : elle appartient au SDK/runtime de l'hôte (les runtimes
//    plus anciens ou un device peuvent légitimement répondre autrement). Le ruling reste
//    entier : la disponibilité GOUVERNE (Simulator n'est plus automatiquement Unavailable ;
//    `requestPermission` retourne CE fait, il ne prompt JAMAIS). NOTE d'interop : le
//    sélecteur Obj-C `sharedRecorder` arrive `RPScreenRecorder.shared()` dans l'overlay
//    Swift du SDK 27 (le klib Kotlin garde `sharedRecorder`).
//
// 2/3. Le bitmap info RÉEL du `CGImage` produit par `UIGraphicsImageRenderer` — la vérité
//    Bgra8/Rgba8 est LUE sur les voies de rendu :
//    - `layer.render(in:)` (la voie hors écran déterministe, mesurée 32bpp DANS la table) ;
//    - `drawHierarchy(in:afterScreenUpdates:)` — qui EST `drawViewHierarchyInRect:
//      afterScreenUpdates:` sous son renommage Swift standard (NS_SWIFT_NAME, TOUS SDK ;
//      l'erreur « has been renamed » du premier jet était un mauvais nom K/N, PAS un
//      retrait SDK — correction de revue Important-3) : ces mesures portent donc
//      DIRECTEMENT sur le sélecteur de la pompe Kotlin.
//    FAITS de spike (iOS/tvOS 27.0 Simulator, scale 1) : `layer.render` produit
//    `bitmapInfo = 0x1` = premultipliedLast + byteOrderDefault(big-endian) = **RGBA8** en
//    mémoire — PAS le BGRA classique ; les DEUX variantes `drawHierarchy` sortent de la
//    table 32bpp (`false` → 2 octets/pixel, `true` → 16-bit composantes order16Little).
//    L'assertion retenue est STRUCTURELLE : la voie pompe-éligible (layer.render) décode
//    vers UN membre de la table portable et la formule plancher bytesPerRow tient — la
//    décision Bgra8/Rgba8 exacte appartient à la couture qui lit au runtime (jamais
//    supposée). Le décodeur est ÉPINGLÉ par test sur les valeurs CGImage.h (byteOrder :
//    32Little = 0x3000, 32Big = 0x4000 — revue Important-1).
//
// La frontière `tests/Shared` compile dans les DEUX targets de test (iOS ET tvOS) :
// `RPScreenRecorder` est klib-présent tvOS (vérifié § rapport) — aucune garde os() n'est
// nécessaire ; le broadcast picker (absent tvOS) n'est référencé NULLE PART (ruling).
final class KadreUikitCaptureSpikeTests: XCTestCase {

    func testScreenRecorderAvailabilityOnSimulatorRecordsVerbatim() {
        let recorder = RPScreenRecorder.shared()
        let available = recorder.isAvailable
        print("SPIKE-FACT: RPScreenRecorder.shared().isAvailable = \(available)")
        // Enregistrement seul : la valeur appartient au runtime de l'hôte (le brief
        // attendait false, la mesure iOS 27.0 Simulator rend true — cf. en-tête).
    }

    func testRendererBitmapInfoReadbackDecodesIntoThePortableTable() {
        let view = UIView(frame: CGRect(x: 0, y: 0, width: 64, height: 48))
        view.backgroundColor = .red
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1 // pixels déterministes (la couture de production lira l'échelle réelle)
        let renderer = UIGraphicsImageRenderer(bounds: view.bounds, format: format)

        // La voie POMPE-ÉLIGIBLE (mesurée 32bpp packed sur cet hôte) : l'assertion porte ICI.
        let layerRendered = renderer.image { context in
            view.layer.render(in: context.cgContext)
        }
        verifyTableMembership(path: "layer.render(in:)", image: layerRendered)

        // Les voies SNAPSHOT (anomalie mesurée, enregistrement seul) : FAIT de spike —
        // `drawHierarchy(in:afterScreenUpdates: false)` produit sur cet hôte un raster
        // bytesPerRow=128 pour width=64 (2 octets/pixel — 16bpp, PAS la table portable
        // 32bpp) : la garde d'honnêteté du kernel (`bytesPerRow >= 4·w` sinon `null`)
        // rejetterait ce bitmap — la pompe doit soit relire et retomber, soit utiliser la
        // voie layer.render. `drawHierarchy` EST `drawViewHierarchyInRect` renommé en
        // Swift : ces mesures portent sur le sélecteur EXACT de la pompe Kotlin — la Task 3
        // lira AUSSI son propre readback, jamais supposée.
        let hierarchyDrawnImmediate = renderer.image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: false)
        }
        recordBitmapInfo(path: "drawHierarchy(in:afterScreenUpdates: false)", image: hierarchyDrawnImmediate)
        let hierarchyDrawnAfterUpdates = renderer.image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
        recordBitmapInfo(path: "drawHierarchy(in:afterScreenUpdates: true)", image: hierarchyDrawnAfterUpdates)
    }

    /// L'assertion structurelle de la voie pompe-éligible : raster 32bpp packed décodant
    /// vers UN membre de la table portable (jamais « unknown »), plancher bytesPerRow tenu.
    private func verifyTableMembership(path: String, image: UIImage) {
        guard let cg = image.cgImage else {
            return XCTFail("le renderer n'a produit aucun CGImage (voie \(path))")
        }
        let raw = cg.bitmapInfo.rawValue
        print("SPIKE-FACT: [\(path)] CGImage width=\(cg.width) height=\(cg.height) "
            + "bytesPerRow=\(cg.bytesPerRow) bitmapInfo=0x\(String(raw, radix: 16))")
        XCTAssertGreaterThanOrEqual(cg.bytesPerRow, cg.width * 4,
                                    "l'honnêteté bytesPerRow : des rangées paddées sont "
                                        + "légales, la formule plancher du layout tient (\(path))")
        let decoded = Self.decodePortableFormat(raw)
        print("SPIKE-FACT: [\(path)] decoded portable format = \(decoded)")
        XCTAssertTrue(decoded == "bgra8" || decoded == "rgba8" || decoded == "bgrx8",
                      "la lecture bitmap info décode vers UN membre de la table portable, "
                          + "jamais « unknown » (\(path)) — obtenu \(decoded)")
    }

    /// L'enregistrement verbatim des voies snapshot (aucune assertion — l'anomalie 16bpp
    /// mesurée est LE fait, cf. testRendererBitmapInfoReadbackDecodesIntoThePortableTable).
    private func recordBitmapInfo(path: String, image: UIImage) {
        guard let cg = image.cgImage else {
            print("SPIKE-FACT: [\(path)] aucun CGImage")
            return
        }
        let raw = cg.bitmapInfo.rawValue
        print("SPIKE-FACT: [\(path)] CGImage width=\(cg.width) height=\(cg.height) "
            + "bytesPerRow=\(cg.bytesPerRow) bitmapInfo=0x\(String(raw, radix: 16)) "
            + "decoded=\(Self.decodePortableFormat(raw))")
    }

    /// Le décodeur bitmapInfo → table portable (la VÉRITÉ mémoire : alphaInfo ordonne les
    /// composantes, byteOrder l'endianness de l'unité 32-bit). Valeurs CGImage.h ÉPINGLÉES
    /// par `testDecoderPinsTheCanonicalByteOrders` — revue Important-1 : byteOrder
    /// 32Little = 0x3000, 32Big = 0x4000 (l'ancien jet les inversait : le BGRA canonique
    /// 0x3002 décodait « unknown » et 0x3001 — mémoire [A][B][G][R] — décodait « rgba8 »).
    private static func decodePortableFormat(_ raw: UInt32) -> String {
        let alphaInfo = raw & 0x1F
        let byteOrder = raw & 0x7000
        switch (alphaInfo, byteOrder) {
        case (2, 0x3000):
            return "bgra8" // premultipliedFirst + order32Little (0x3000) : [B][G][R][A] en mémoire
        case (1, 0x4000), (1, 0):
            return "rgba8" // premultipliedLast + order32Big (0x4000)/default : [R][G][B][A] en mémoire
        case (6, 0x3000):
            return "bgrx8" // noneSkipFirst + order32Little : [B][G][R][X]
        case (5, 0x4000), (5, 0):
            return "bgrx8" // noneSkipLast + order32Big/default : [R][G][B][X]
        default:
            return "unknown"
        }
    }

    func testDecoderPinsTheCanonicalByteOrders() {
        // Les valeurs de CGImage.h (revue Important-1) : byteOrder 16Little=0x1000,
        // 16Big=0x2000, 32Little=0x3000, 32Big=0x4000 ; alphaInfo premultipliedLast=1,
        // premultipliedFirst=2, noneSkipLast=5, noneSkipFirst=6.
        XCTAssertEqual(Self.decodePortableFormat(0x1), "rgba8")      // mesuré : layer.render
        XCTAssertEqual(Self.decodePortableFormat(0x4001), "rgba8")   // premultipliedLast + 32Big
        XCTAssertEqual(Self.decodePortableFormat(0x3002), "bgra8")   // LE BGRA canonique (premultipliedFirst + 32Little)
        XCTAssertEqual(Self.decodePortableFormat(0x3001), "unknown") // premultipliedLast + 32Little = [A][B][G][R] — HORS table (le piège du swap de canaux)
        XCTAssertEqual(Self.decodePortableFormat(0x3006), "bgrx8")   // noneSkipFirst + 32Little
        XCTAssertEqual(Self.decodePortableFormat(0x5), "bgrx8")      // noneSkipLast + default
        XCTAssertEqual(Self.decodePortableFormat(0x1101), "unknown") // mesuré : drawHierarchy(true) — 16-bit composantes
        XCTAssertEqual(Self.decodePortableFormat(0x2002), "unknown") // 16Big : pas un ordre 32bpp portable
    }
}
