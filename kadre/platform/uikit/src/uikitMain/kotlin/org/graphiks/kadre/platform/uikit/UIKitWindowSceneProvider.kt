package org.graphiks.kadre.platform.uikit

import org.graphiks.kadre.window.WindowRequestId
import org.graphiks.kadre.window.WindowSpec
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Le provider de scènes app-owned d'une session UIKit/iPadOS multi-fenêtre (phase 4, miroir du
 * `WebWindowProvider` web) : l'app — seule à connaître sa topologie de scènes — active une
 * nouvelle `UIWindowScene` à chaque demande admise.
 *
 * Appelé sur le thread principal à chaque `requestWindow` admis. L'app active la scène
 * (`UIApplication.requestSceneSessionActivation`) et la corréle à [requestId] (l'identité voyage
 * dans l'`userActivity`/les options d'activation) ; à la connexion de cette scène, l'app crée la
 * fenêtre et la vue de contenu puis résout via le [UIKitWindowSceneRequestResolver] de la demande.
 *
 * La fonction ne rend RIEN : l'ouverture d'une scène est réellement asynchrone (FrontBoard), sans
 * timeout synthétique — la demande reste `Pending` jusqu'à la résolution, l'abandon app-side ou
 * l'annulation du requérant. Un **throw** de [open] est capturé par le manager et devient l'outcome
 * fermé `Rejected(PlatformFailure(UIKit, "UIKitWindowSceneProvider", "callback-exception"))` —
 * jamais une failure de l'appel `requestWindow`.
 */
public fun interface UIKitWindowSceneProvider {
    public fun open(requestId: WindowRequestId, spec: WindowSpec)
}

/**
 * Le sink internal auquel un [UIKitWindowSceneRequestResolver] route les résolutions app-side —
 * le manager de la session productrice en est l'implémentation de production. Séparer l'interface
 * du manager garde le resolver public sans exposer le manager (internal) et permet aux tests de
 * conduire le routage avec un double.
 */
internal interface UIKitWindowSceneRequestSink {
    /** La scène corrélée a connecté avec ses objets host — voir [UIKitWindowSceneRequestResolver.resolve]. */
    public fun resolve(
        requestId: WindowRequestId,
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
    )

    /** La scène demandée ne connectera jamais — voir [UIKitWindowSceneRequestResolver.abandon]. */
    public fun abandon(requestId: WindowRequestId, reason: String)
}

/**
 * La résolution app-side d'une demande de scène admise : la scène corrélée à cette demande a
 * connecté avec ses objets host, ou ne connectera jamais.
 *
 * Une instance est émise par le manager pour chaque demande admise (`issue`, internal) ; l'app la
 * rejoint par le `WindowRequestId` que [UIKitWindowSceneProvider.open] lui a remis. Toute route
 * arrive au manager, qui la garde : une résolution ou un abandon **tardif** (demande déjà
 * terminale — annulée, détachée, close) est un no-op — jamais une seconde résolution d'une requête
 * terminale (roadmap l.239). Les appels se font sur le thread principal, comme
 * [UIKitWindowSceneProvider.open].
 */
public class UIKitWindowSceneRequestResolver private constructor(
    /** L'identité de la demande corrélée — la même que [UIKitWindowSceneProvider.open] a reçue. */
    internal val id: WindowRequestId,
    private val sink: UIKitWindowSceneRequestSink,
) {
    /**
     * La scène corrélée a connecté : l'app a créé [window] dans [windowScene] avec [surfaceView]
     * comme vue de contenu. Le manager attache alors la session enfant de la scène et termine la
     * demande `OpenedInNewSession(childId)` — ou ignore tout si la demande est déjà terminale.
     */
    public fun resolve(windowScene: UIWindowScene, window: UIWindow, surfaceView: UIView) {
        sink.resolve(id, windowScene, window, surfaceView)
    }

    /**
     * La scène demandée ne connectera jamais (échec d'activation, discard système) : la demande
     * pendante est terminée `Cancelled`. [reason] est diagnostique (journaux app-side) et transite
     * tel quel au manager ; l'outcome est le même quel que soit le motif.
     */
    public fun abandon(reason: String) {
        sink.abandon(id, reason)
    }

    public companion object {
        /** Émise par le manager à l'admission d'une demande — jamais par l'app. */
        internal fun issue(id: WindowRequestId, sink: UIKitWindowSceneRequestSink): UIKitWindowSceneRequestResolver =
            UIKitWindowSceneRequestResolver(id, sink)
    }
}
