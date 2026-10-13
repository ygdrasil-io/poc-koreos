package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.interaction.InteractionKind
import platform.UIKit.UIView

/**
 * L'actual tvOS des coutures drop ([createDropBinding], [uikitHandlerInteractionKinds]) — la
 * surface RÉELLE du klib tvOS (2.4.20, vérifiée au compile ET dans le linkdata) :
 * `UIDropInteraction`, `UIDropInteractionDelegateProtocol`, `UIDropSessionProtocol` et
 * `UIDragDropSessionProtocol` sont ABSENTS du klib tvOS (0 occurrence — `API_UNAVAILABLE(tvos)`
 * de UIDragSession.h:19, Apple lui-même déclare la famille drag-and-drop indisponible sur
 * tvOS). Le binding ne peut pas exister structurellement — le précédent
 * `UIKitGestureRecognizersTvos.kt` répond pareil pour les recognizers absents : `null` et
 * l'ensemble vide sont les réponses honnêtes d'une plateforme sans la classe, jamais un binding
 * de substitution qui ne dropperait rien.
 *
 * Conséquences portées par la ladder de capabilities : [uikitHandlerInteractionKinds] rend
 * l'ensemble VIDE, `uikitSurfaceCapabilities()` garde le `handlerInteractions` refusé par
 * défaut, `MinimalWindowSurface` ne construit pas de `RuntimeInteractionHandler` drop, et
 * aucun dispatch drop ne peut atteindre le manager — l'absence est la vérité, bout en bout.
 * Les paramètres ne servent qu'à la signature commune (l'actual iOS construit le binding avec
 * eux) : tvOS n'a pas la classe à leur passer.
 */
internal actual fun createDropBinding(view: UIView, hooks: UIKitDropHooks, scale: Double): UIKitDropBinding? {
    @Suppress("UNUSED_EXPRESSION")
    view
    @Suppress("UNUSED_EXPRESSION")
    hooks
    @Suppress("UNUSED_EXPRESSION")
    scale
    return null
}

internal actual fun uikitHandlerInteractionKinds(): Set<InteractionKind> = emptySet()
