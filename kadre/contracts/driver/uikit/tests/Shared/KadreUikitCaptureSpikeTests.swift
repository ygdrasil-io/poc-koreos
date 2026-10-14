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
//    Bgra8/Rgba8 est LUE sur les DEUX voies de rendu :
//    - `layer.render(in:)` (la voie Swift hors écran déterministe) ;
//    - `drawHierarchy(in:afterScreenUpdates:)` (le plus proche analogue Swift de la pompe
//      Kotlin `drawViewHierarchyInRect`).
//    FAITS de spike (iOS 27.0 Simulator, scale 1) : `layer.render` produit
//    `bitmapInfo = 0x1` = premultipliedLast + byteOrderDefault(32Big) = **RGBA8** en
//    mémoire — PAS le BGRA classique. `drawViewHierarchyInRect:afterScreenUpdates:` est
//    DÉCLARÉ dans le header Obj-C (UIView.h:666) ET lié dans le klib K/N (les probes
//    Kotlin compilent), mais l'overlay Swift du SDK 27 ne l'exporte PLUS vers Swift —
//    d'où le mesure `drawHierarchy` comme analogue. L'assertion retenue est
//    STRUCTURELLE : chaque lecture décode vers UN membre de la table portable (jamais
//    « unknown ») et la formule plancher bytesPerRow tient — la décision Bgra8/Rgba8
//    exacte appartient à la couture qui lit au runtime (jamais supposée).
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
        // voie layer.render. La Task 3 Kotlin lira AUSSI son propre readback
        // (drawViewHierarchyInRect — le klib K/N le porte) : jamais supposée.
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
    /// composantes, byteOrder l'endianness de l'unité 32-bit).
    private static func decodePortableFormat(_ raw: UInt32) -> String {
        let alphaInfo = raw & 0x1F
        let byteOrder = raw & 0x7000
        switch (alphaInfo, byteOrder) {
        case (2, 0x2000):
            return "bgra8" // premultipliedFirst + order32Little : [B][G][R][A] en mémoire
        case (1, 0x3000), (1, 0):
            return "rgba8" // premultipliedLast + 32Big/default : [R][G][B][A] en mémoire
        case (5, 0x2000):
            return "bgrx8" // noneSkipFirst + order32Little
        case (4, 0x3000), (4, 0):
            return "bgrx8" // noneSkipLast + 32Big/default
        default:
            return "unknown"
        }
    }
}
