import SwiftUI
import KadreUikit

/*
 * Task 6 — LE wrapper §9 (ligne SwiftUI d'INTEROP, verbatim) : « wrapper
 * `UIViewControllerRepresentable` possédé par l'host controller, sans layout Kadre ».
 *
 * APP-OWNED — ~10 lignes autour de `KadreHostViewController` (INTEROP §5 l.139 : le
 * `UIViewControllerRepresentable` est laissé au consommateur, il ne vit JAMAIS dans
 * KadreHost) :
 *  — `makeUIViewController` CRÉE et POSSÈDE le controller (make = own) : SwiftUI le
 *    retient dans sa hiérarchie ; l'attach lui-même reste controller-driven
 *    (`didMoveToWindow` de la view du controller — Task 5) au premier moment valide
 *    (fenêtre + scène) ;
 *  — `updateUIViewController` NE FAIT RIEN : une réévaluation du body NE re-attache PAS et
 *    NE recrée PAS — le MÊME controller survit aux re-renders (l'identité `===` en est la
 *    preuve : `uikit-swiftui-consumer-attaches-once`), et la machine exactement-une-fois du
 *    controller (guard `.waiting` — Task 5) est le second garde-fou ;
 *  — AUCUN layout Kadre : ni contrainte, ni frame, ni subview posés ici — la view du
 *    controller EST la surfaceView (Task 5, preuve f) ; SwiftUI la dimensionne comme
 *    n'importe quelle vue.
 */

/// Le wrapper SwiftUI du consumer autour du controller hôte Kadre.
struct KadreHostView: UIViewControllerRepresentable {
    let applicationFactory: any KadreApplicationFactory
    let policy: KadrePolicyProfile

    func makeUIViewController(context: Context) -> KadreHostViewController {
        ConsumerSwiftUIStore.shared.recordMakeUIViewController(
            KadreHostViewController(applicationFactory: applicationFactory, policy: policy)
        )
    }

    func updateUIViewController(_ uiViewController: KadreHostViewController, context: Context) {
        // CONTRACTUELLEMENT VIDE (§9) : ni close, ni re-attach, ni layout — rien.
    }
}
