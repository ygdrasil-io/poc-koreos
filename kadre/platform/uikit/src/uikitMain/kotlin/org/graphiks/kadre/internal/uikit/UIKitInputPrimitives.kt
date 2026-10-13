package org.graphiks.kadre.internal.uikit

/**
 * Les primitives d'observation d'UIKit, en forme Kadre : la mémoire du kernel de mapping
 * ([UIKitInputMapping.kt]) sans un seul type UIKit.
 *
 * C'est le jumeau input de la règle des autres plateformes : un port lit ses propres objets natifs
 * (un `UITouch`, une closure `GCKeyboard.keyChangedHandler`, un `UIGestureRecognizer`) et les copie
 * dans ces valeurs immuables, que le kernel pur traduit en valeurs communes du modèle d'input. La
 * forme de chaque primitive est celle de l'observation UIKit — un champ par fait natif, rien de
 * recalculé — et aucun type UIKit n'y apparaît, ce qui est ce qui rend le kernel testable dans
 * `uikitTest` sans appareil ni simulateur.
 *
 * Le découpage suit la source : un contact ([UIKitTouchPrimitive]) est l'observation d'un `UITouch`
 * par le binding touch ; une touche ([UIKitKeyPrimitive]) est l'observation d'une clé GameController
 * ; un geste ([UIKitGesturePrimitive]) est l'observation d'un recognizer. Les identités, la table de
 * vie des contacts et la règle « fin à position invalide → `Cancelled` à la dernière position
 * valide » restent du ressort du binding stateful (miroir de `AppKitTouchAdapter`) : le kernel ne
 * porte qu'une observation à la fois, jamais l'état d'une source.
 */

/** La phase d'un contact ou d'un geste, telle qu'UIKit la nomme (`UITouchPhase`, état du recognizer). */
internal enum class UIKitTouchPhase {
    /** Le contact ou le geste vient de commencer (`UITouchPhaseBegan`, `began`). */
    Began,

    /** Il a bougé ou changé depuis la dernière livraison (`UITouchPhaseMoved`, `changed`). */
    Moved,

    /** Il s'est terminé normalement (`UITouchPhaseEnded`, `ended`). */
    Ended,

    /** Le système l'a révoqué (`UITouchPhaseCancelled`, `cancelled`). */
    Cancelled,
}

/**
 * L'observation d'un contact, copiée d'un `UITouch`.
 *
 * [identityToken] est le jeton stable du contact natif — la valeur que le binding garde pour la
 * durée de vie du contact afin qu'un même doigt livre toujours la même identité (la règle du
 * `AppKitTouchAdapter` : un contact, une identité, jamais réallouée entre deux events). La position
 * est celle du contact dans l'espace de la vue, en points logiques ; [pressure] est la force
 * rapportée (`force`), ou `null` quand la source n'en rapporte pas ; [isStylus] vaut ce que
 * [uikitIsStylus] a répondu pour le `type` du touch (Apple Pencil).
 */
internal data class UIKitTouchPrimitive(
    val identityToken: Int,
    val phase: UIKitTouchPhase,
    val x: Double,
    val y: Double,
    val pressure: Double?,
    val isStylus: Boolean,
)

/**
 * L'observation d'une touche physique, copiée d'une clé GameController (`GCKey` d'un
 * `keyChangedHandler`) ou d'un event clavier UIKit.
 *
 * [usagePage] et [usageId] sont la paire HID que la clé rapporte — portés tels quels, la page n'est
 * jamais forcée en 0x07 (les media keys livrent leur propre page Consumer). [characters] est la
 * chaîne que la clé produit (`GCKey.characters`, les constantes `UIKeyInput*` comprises) et
 * [pressed] vaut si l'observation est un appui ou un relâchement.
 */
internal data class UIKitKeyPrimitive(
    val usagePage: Int,
    val usageId: Int,
    val characters: String?,
    val pressed: Boolean,
)

/** Le genre de geste reconnu, tel que le recognizer installé le nomme. */
internal enum class UIKitGestureKind {
    /** Un `UIPanGestureRecognizer` : la translation du geste. */
    Pan,

    /** Un `UIPinchGestureRecognizer` : l'échelle du geste. */
    Pinch,

    /** Un `UIRotationGestureRecognizer` : la rotation du geste, en radians. */
    Rotation,

    /** Un tap double : un fait de reconnaissance, sans mesure. */
    DoubleTap,

    /** La pression d'un trackpad : la force seule du geste. */
    TouchpadPressure,
}

/**
 * L'observation d'un geste reconnu, copiée d'un recognizer installé.
 *
 * [translationX]/[translationY] sont la translation cumulée du pan (en points logiques), [scale]
 * l'échelle relative du pinch, [rotation] la rotation en radians, [pressure] la force du trackpad.
 * Chaque champ est indépendant et `null` quand le geste n'en rapporte pas — le kernel applique les
 * invariants du modèle ([uikitGesture]) et perd ce qui ne peut pas être porté honnêtement.
 */
internal data class UIKitGesturePrimitive(
    val kind: UIKitGestureKind,
    val phase: UIKitTouchPhase,
    val translationX: Double? = null,
    val translationY: Double? = null,
    val scale: Double? = null,
    val rotation: Double? = null,
    val pressure: Double? = null,
)
