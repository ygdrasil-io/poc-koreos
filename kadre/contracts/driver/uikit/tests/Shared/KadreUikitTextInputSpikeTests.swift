import XCTest
import UIKit
#if canImport(KadreUikitDriverIos)
@testable import KadreUikitDriverIos
#elseif canImport(KadreUikitDriverTvOs)
@testable import KadreUikitDriverTvOs
#endif

/// SPIKE phase 6 Task 1 (jetable) — la vérité UIKit programmatique d'un `UITextField` simple,
/// AVANT d'écrire le receiver Kotlin. Chaque fait observé est ENREGISTRÉ (NSLog « KADRE-SPIKE »,
/// visible dans la sortie console xcodebuild) ET asserté quand la décision en dépend :
///
/// - (a) `setMarkedText:programmatique` produit-il un état marqué (`markedTextRange != nil`) ?
///   La décision receiver (UITextField vs UITextView) y pend ;
/// - (b) `deleteBackward` sur un emoji supprime-t-il la PAIRE surrogate entière ?
/// - (c) `becomeFirstResponder`/`resignFirstResponder` sont-ils déterministes sous XCTest
///   (deux allers-retours complets dans le même test) ;
/// - (d) les callbacks de changement (`textDidChangeNotification`, `.editingChanged`,
///   `textFieldDidChangeSelection`) et le hook `shouldChangeCharactersInRange` se déclenchent-ils
///   sur les appels UIKeyInput programmatiques (`insertText:`, `deleteBackward`) — le canal
///   d'observation du port en dépend.
final class KadreUikitTextInputSpikeTests: XCTestCase {

    /// Les faits de délégation d'UN champ, comptés avec horodatage dans le journal du driver.
    private final class SpikeDelegate: NSObject, UITextFieldDelegate {
        var shouldChangeCalls: [(range: NSRange, replacement: String)] = []
        var didChangeSelectionCount = 0
        var didBeginCount = 0
        var didEndCount = 0

        func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange,
                       replacementString text: String) -> Bool {
            shouldChangeCalls.append((range, text))
            DriverTrace.record("KADRE-SPIKE shouldChangeCharactersInRange range=\(NSStringFromRange(range)) "
                + "replacement='\(text)' (utf16=\(text.utf16.count))")
            return true
        }

        func textFieldDidChangeSelection(_ textField: UITextField) {
            didChangeSelectionCount += 1
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            didBeginCount += 1
            DriverTrace.record("KADRE-SPIKE textFieldDidBeginEditing")
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            didEndCount += 1
            DriverTrace.record("KADRE-SPIKE textFieldDidEndEditing")
        }
    }

    private let delegate = SpikeDelegate()
    private var textDidChangeNotificationCount = 0
    private var editingChangedCount = 0

    private func pump(_ seconds: TimeInterval) {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.02))
        }
    }

    /// Le résumé de l'état d'un champ, pour le journal : texte (brut + comptages UTF-16 et
    /// scalaires), plage marquée (offsets depuis le début du document), sélection.
    private func fieldSummary(_ field: UITextField) -> String {
        let text = field.text ?? "<nil>"
        var marked = "<nil>"
        if let range = field.markedTextRange {
            let start = field.offset(from: field.beginningOfDocument, to: range.start)
            let end = field.offset(from: field.beginningOfDocument, to: range.end)
            marked = "(\(start),\(end - start))"
        }
        var selection = "<nil>"
        if let range = field.selectedTextRange {
            let start = field.offset(from: field.beginningOfDocument, to: range.start)
            let end = field.offset(from: field.beginningOfDocument, to: range.end)
            selection = "(\(start),\(end - start))"
        }
        return "text='\(text)' utf16=\(field.text?.utf16.count ?? -1) "
            + "scalars=\(field.text?.unicodeScalars.count ?? -1) "
            + "markedTextRange=\(marked) selectedTextRange=\(selection) "
            + "isFirstResponder=\(field.isFirstResponder)"
    }

    /// Le spike entier, en séquence : focus → insertText → emoji + deleteBackward →
    /// setMarkedText → unmarkText → resign → second aller-retour de focus.
    func testUITextFieldProgrammaticEditingSpike() throws {
        // Le fixture de la scène primaire (fenêtre key + active) — sans session Kadre : le
        // spike prouve le comportement UIKit NU, pas l'intégration.
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
        let window = try XCTUnwrap(fixture.window)

        let field = UITextField(frame: CGRect(x: 8, y: 8, width: 304, height: 44))
        field.borderStyle = .roundedRect
        field.autocorrectionType = .no
        field.autocapitalizationType = .none
        field.delegate = delegate
        window.addSubview(field)

        let center = NotificationCenter.default
        let observer = center.addObserver(forName: UITextField.textDidChangeNotification,
                                          object: field, queue: .main) { [weak self] _ in
            self?.textDidChangeNotificationCount += 1
        }
        field.addTarget(self, action: #selector(editingChanged(_:)), for: .editingChanged)

        defer {
            center.removeObserver(observer)
            field.resignFirstResponder()
            field.removeFromSuperview()
        }

        // MARK: (c) — focus avant toute édition

        DriverTrace.record("KADRE-SPIKE initial \(fieldSummary(field))")
        let became = field.becomeFirstResponder()
        pump(0.3)
        DriverTrace.record("KADRE-SPIKE (c) becomeFirstResponder -> \(became) \(fieldSummary(field))")
        XCTAssertTrue(field.isFirstResponder,
                      "(c) the field must hold first responder after becomeFirstResponder + pump; "
                          + "became=\(became) \(fieldSummary(field))")
        XCTAssertEqual(delegate.didBeginCount, 1, "(c) textFieldDidBeginEditing must fire once")

        // MARK: (d) — insertText programmatique

        field.insertText("ab")
        pump(0.2)
        DriverTrace.record("KADRE-SPIKE (d) after insertText('ab') \(fieldSummary(field)) "
            + "textDidChangeNotification=\(textDidChangeNotificationCount) "
            + "editingChanged=\(editingChangedCount) "
            + "didChangeSelection=\(delegate.didChangeSelectionCount) "
            + "shouldChangeCalls=\(delegate.shouldChangeCalls.count)")
        XCTAssertEqual(field.text ?? "", "ab",
                       "(d) programmatic insertText('ab') must produce text='ab'; actual '\(field.text ?? "<nil>")'")
        // La vérité (d) est ENREGISTRÉE, jamais inventée : les compteurs ci-dessus portent la
        // réponse par famille (le journal est la preuve) ; le silence de shouldChange est de plus
        // ASSERTÉ dans le bloc des totaux en fin de test (contrat de routage — voir là-bas).

        // MARK: (b) — deleteBackward sur une paire surrogate

        field.insertText("😀")
        pump(0.2)
        DriverTrace.record("KADRE-SPIKE (b) after insertText('😀') \(fieldSummary(field))")
        XCTAssertEqual(field.text?.utf16.count, 4, "text='ab😀' must span 4 UTF-16 code units")

        field.deleteBackward()
        pump(0.2)
        DriverTrace.record("KADRE-SPIKE (b) after deleteBackward over '😀' \(fieldSummary(field)) "
            + "textDidChangeNotification=\(textDidChangeNotificationCount) "
            + "editingChanged=\(editingChangedCount) "
            + "shouldChangeCalls=\(delegate.shouldChangeCalls.count)")
        XCTAssertEqual(field.text?.utf16.count, 2,
                       "(b) deleteBackward over the surrogate pair must remove the WHOLE character "
                           + "('ab', 2 code units); actual '\(field.text ?? "<nil>")' "
                           + "(\(field.text?.utf16.count ?? -1) code units)")

        field.deleteBackward()
        pump(0.2)
        DriverTrace.record("KADRE-SPIKE (b) after deleteBackward over 'b' \(fieldSummary(field))")
        XCTAssertEqual(field.text ?? "", "a", "the second backward delete removes 'b'")

        // MARK: (a) — setMarkedText programmatique + unmarkText

        field.setMarkedText("é", selectedRange: NSRange(location: 1, length: 0))
        pump(0.2)
        var markedDescription = "<nil>"
        if let marked = field.markedTextRange {
            let start = field.offset(from: field.beginningOfDocument, to: marked.start)
            let end = field.offset(from: field.beginningOfDocument, to: marked.end)
            markedDescription = "(\(start),\(end - start))"
        }
        DriverTrace.record("KADRE-SPIKE (a) after setMarkedText('é', selectedRange:(1,0)) "
            + "\(fieldSummary(field)) textDidChangeNotification=\(textDidChangeNotificationCount)")
        XCTAssertEqual(field.text ?? "", "aé",
                       "(a) programmatic setMarkedText must replace the empty selection at the end "
                           + "with the marked text; actual '\(field.text ?? "<nil>")'")
        XCTAssertNotNil(field.markedTextRange,
                        "(a) programmatic setMarkedText must produce a MARKED state "
                            + "(markedTextRange=\(markedDescription)) — otherwise the receiver "
                            + "decision moves to UITextView")

        field.unmarkText()
        pump(0.2)
        DriverTrace.record("KADRE-SPIKE (a) after unmarkText \(fieldSummary(field))")
        XCTAssertNil(field.markedTextRange, "(a) unmarkText must clear the marked state")
        XCTAssertEqual(field.text ?? "", "aé",
                       "(a) unmarkText must COMMIT the marked text, leaving no orphan composition; "
                           + "actual '\(field.text ?? "<nil>")'")

        // MARK: (c) — resign + second aller-retour (déterminisme sous XCTest)

        let resigned = field.resignFirstResponder()
        pump(0.3)
        DriverTrace.record("KADRE-SPIKE (c) resignFirstResponder -> \(resigned) \(fieldSummary(field))")
        XCTAssertFalse(field.isFirstResponder, "(c) the field must release first responder")
        XCTAssertEqual(delegate.didEndCount, 1, "(c) textFieldDidEndEditing must fire once")

        let becameAgain = field.becomeFirstResponder()
        pump(0.3)
        DriverTrace.record("KADRE-SPIKE (c) second becomeFirstResponder -> \(becameAgain) \(fieldSummary(field))")
        XCTAssertTrue(field.isFirstResponder, "(c) the second focus round must succeed")
        let resignedAgain = field.resignFirstResponder()
        pump(0.3)
        DriverTrace.record("KADRE-SPIKE (c) second resignFirstResponder -> \(resignedAgain) \(fieldSummary(field))")
        XCTAssertFalse(field.isFirstResponder, "(c) the second resign must succeed")

        DriverTrace.record("KADRE-SPIKE totals textDidChangeNotification=\(textDidChangeNotificationCount) "
            + "editingChanged=\(editingChangedCount) "
            + "didChangeSelection=\(delegate.didChangeSelectionCount) "
            + "shouldChangeCalls=\(delegate.shouldChangeCalls.count) "
            + "didBegin=\(delegate.didBeginCount) didEnd=\(delegate.didEndCount)")

        // Sentinelle du contrat de routage (task-1-report §3.4(2)) : l'édition UTILISATEUR passe par
        // `shouldChangeCharactersInRange` (range nommé par UIKit AVANT la modification), l'édition
        // PROGRAMMATIQUE (appels UIKeyInput ordinaires — le stimulus driver) ne doit JAMAIS y passer ;
        // son canal d'observation est `textDidChange`/`DidChangeSelection` APRÈS la modification, le
        // port réconciliant contre le document du shadow. Un seul appel ici inverserait ce routage.
        XCTAssertEqual(delegate.shouldChangeCalls.count, 0,
                       "(d) programmatic UIKeyInput calls (insertText/deleteBackward/setMarkedText) must NEVER "
                           + "fire shouldChangeCharactersInRange — routing contract: user edits route through "
                           + "shouldChange (range named by UIKit BEFORE the edit), programmatic driver stimuli "
                           + "route through textDidChange/DidChangeSelection (reconciled against the shadow AFTER "
                           + "the edit); any firing flips the port's routing "
                           + "(\(delegate.shouldChangeCalls.count) call(s): \(delegate.shouldChangeCalls))")
        // Plancher du canal d'observation programmatique : chacune des 6 éditions programmatiques de
        // la séquence (insertText ×2, deleteBackward ×2, setMarkedText, unmarkText) a tiré
        // `textFieldDidChangeSelection` AU MOINS une fois (7 observés, identiques ×2 familles).
        XCTAssertGreaterThanOrEqual(delegate.didChangeSelectionCount, 6,
                                    "(d) each of the 6 programmatic edits must fire textFieldDidChangeSelection at "
                                        + "least once (observed 7 on both families) — it is half of the programmatic "
                                        + "observation channel; got \(delegate.didChangeSelectionCount)")
    }

    @objc private func editingChanged(_ sender: UITextField) {
        editingChangedCount += 1
    }
}
