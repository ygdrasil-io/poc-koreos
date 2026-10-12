import UIKit
import KadreUikit

/*
 * `KadreIosHost` — verbatim §5 : le point d'entrée de la façade vivante. L'attach
 * délègue au driver normatif `KadreIos.shared.attach` (phase 1 — DESIGN.md §15.2 : une
 * session par `UIWindowScene`, réservation AVANT installation, rollback complet en cas
 * d'échec) et déballe le `KadreResult` exporté en la frontière §5 :
 *  — `Success` → `KadreSessionHandle(session)` — le billet vivant (`KadreSessionHandle`) ;
 *  — `Failure` → `throws KadreError(reason)` — la traduction Swift du `KadreFailure`
 *    Kotlin (message DÉRIVÉ — sémantique du `KadreFailure.message`, pas texte exact —
 *    via le mapper Task 3 et l'initialisateur canonique `KadreError.init(failure:)`).
 *
 * La POLICY est la traduction du profil §5 vers les politiques partagées du Kotlin
 * (`KadrePolicies.shared.Default/Realtime/Recording` — les accesseurs exportés du
 * header, l.5441+) : le profil est une enum fermée façade, la politique reste l'objet
 * Kotlin consommé par le driver — la façade ne la reconstruit pas.
 *
 * CONTRAT DE THREAD (miroir de phase 1) : l'appel DOIT être sur le thread principal —
 * `KadreIos.attach` l'exige (`NSThread.isMainThread`, les lectures UIKit, le snapshot de
 * la vue et le pont d'admission sont synchrones sur ce fil) ; la façade le documente ET
 * le vérifie à l'entrée (`precondition` — même sémantique que le `check` Kotlin : un
 * appel hors contrat est une erreur de programmation, pas une valeur).
 *
 * M5 (note de suivi Task 3) : le chemin d'attach ne rend observable AUCUN
 * `CaptureSourceId` réel (la capture est phase 9) — la preuve runtime-payload de
 * `sourceLost` reste donc différée jusqu'à la capture ; la DÉRIVATION du payload est
 * déjà prouvée côté mapper (`KadreHostMappingTests.testSourceLostMapsOpaqueSourceId`).
 */
public enum KadreIosHost {

    /// L'attach §5 : scène, fenêtre primaire et vue de contenu choisis par le host, la
    /// factory d'application fournie par le consommateur (le module applicatif Kotlin du
    /// consumer, ici — le profil de politique optionnel vaut `.default`).
    public static func attach(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: any KadreUikit.KadreApplicationFactory,
        policy: KadrePolicyProfile = .default
    ) throws -> KadreSessionHandle {
        precondition(
            Thread.isMainThread,
            "KadreIosHost.attach doit être appelé depuis le thread principal (contrat KadreIos.attach — les lectures UIKit et le pont d'admission l'exigent)")

        let kadrePolicy: KadreUikit.KadrePolicy
        switch policy {
        case .default:
            kadrePolicy = KadreUikit.KadrePolicies.shared.Default
        case .realtime:
            kadrePolicy = KadreUikit.KadrePolicies.shared.Realtime
        case .recording:
            kadrePolicy = KadreUikit.KadrePolicies.shared.Recording
        }

        let result = KadreUikit.KadreIos.shared.attach(
            windowScene: windowScene,
            window: window,
            surfaceView: surfaceView,
            applicationFactory: applicationFactory,
            policy: kadrePolicy)

        if let failure = (result as? KadreUikit.KadreResultFailure)?.reason {
            throw KadreError(failure: KadreFailureMapper.make(failure))
        }
        guard let success = result as? KadreUikit.KadreResultSuccess<AnyObject>,
              let session = success.value as? (any KadreUikit.KadreSession) else {
            // Inatteignable tant que le `KadreResult` Kotlin est l'union fermée
            // Success/Failure (posture du masque : crier, jamais une valeur mensongère).
            fatalError("KadreResult ni Success<KadreSession> ni Failure — surface Kotlin élargie hors du contrat §5 : \(String(describing: result))")
        }
        return KadreSessionHandle(session: session)
    }
}
