@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import org.graphiks.kadre.interaction.InteractionKind
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.LogicalPoint
import platform.UIKit.UIView

/**
 * Le branchement drop d'une surface (phase 7) — la classe abstraite partagée derrière la couture
 * iOS/tvOS. Comme pour les gestes ([UIKitGestureBindings]), la surface RÉELLE du klib décide :
 * `UIDropInteraction`/`UIDropInteractionDelegateProtocol` n'existent QUE sur iOS (0 symbole dans
 * le klib tvOS — `API_UNAVAILABLE(tvos)` de UIDragSession.h:19), donc l'actual tvOS ne construit
 * PAS de binding ([createDropBinding] répond `null`) et la pose ne peut pas y arriver.
 *
 * Le cycle est celui de la famille : [install] pose l'interaction Kadre-owned ADDITIVEMENT sur
 * la vue du host (jamais une modification de sa propre gestion), [teardown] la retire et éteint
 * la machine à états ([UIKitDropHooks.close]) — zéro résiduel, idempotent.
 */
internal abstract class UIKitDropBinding {
    abstract fun install()

    /** Retrait : l'interaction quitte la vue et les hooks s'éteignent (plus aucun stimulus). */
    abstract fun teardown()
}

/**
 * La fabrique plateforme du binding drop — la couture iOS/tvOS, même forme que
 * [createGestureRecognizers] : l'actual iOS construit le binding réel (delegate
 * `UIDropInteractionDelegateProtocol` + `UIDropInteraction` posée sur la vue), l'actual tvOS
 * répond `null` (la couture drop n'existe pas dans son klib — 0 symbole, cf. KDoc de classe
 * `[UIKitDropBinding]` et le précédent `UIKitGestureRecognizersTvos.kt`).
 *
 * `scale` est l'échelle d'affichage du moment de l'installation (`traitCollection.displayScale`
 * du port, bornée comme le snapshot d'attach). La CONVERSION de position ne l'applique JAMAIS :
 * l'espace d'une vue UIKit EST l'espace logique (règle M4 du touch, [uikitLogicalPoint]) — le
 * paramètre ne porte que la symétrie de signature avec le kernel d'input.
 *
 * `null` est la réponse honnête d'une plateforme sans la classe — jamais un binding de
 * substitution qui ne dropperait rien.
 */
internal expect fun createDropBinding(view: UIView, hooks: UIKitDropHooks, scale: Double): UIKitDropBinding?

/**
 * Les interactions de handler annoncées par CE target — la couture de la ladder de capabilities
 * (`uikitSurfaceCapabilities`) : iOS annonce EXACTEMENT `InteractionKind.AcceptDrop` (aucun
 * primitif fullscreen/pointer-lock/window-move n'existe sur UIKit, et le drag SORTANT n'est pas
 * annoncé — sortie roadmap), tvOS annonce l'ensemble VIDE (pas de drop sans `UIDropInteraction`).
 */
internal expect fun uikitHandlerInteractionKinds(): Set<InteractionKind>

/**
 * Le kernel de conversion d'UNE position de la vue — le même jugement que la lecture du touch et
 * du survol : l'espace de la vue EST l'espace logique (AUCUNE multiplication d'échelle — règle
 * M4), une composante non finie n'est pas une position (`null`, jamais une valeur inventée).
 * Une seule définition pour toute la famille drop : le delegate l'appelle sur la tranche
 * `useContents` du `CGPoint` livré.
 */
internal fun uikitLogicalPoint(x: Double, y: Double): LogicalPoint? =
    if (x.isFinite() && y.isFinite()) LogicalPoint(x, y) else null

/**
 * Le compteur process-wide du drop (le proxy du driver, pattern [UIKitGestureCount]) : les
 * installations/retraits d'interaction réellement exécutés ([recordInstall]/[recordTeardown] —
 * zéro côté tvOS par construction, le binding n'y existe pas) et les livraisons du delegate
 * réellement ACCEPTÉES ([recordEvent] — zéro attendu sur toute suite automatisée, aucun drag
 * n'est synthétisable in-process honnêtement ; le charter manuel Task 4 incrémentera).
 */
internal object UIKitDropCount {
    private val lock = RuntimeLock()
    private var installs: Long = 0L
    private var teardowns: Long = 0L
    private var events: Long = 0L

    fun recordInstall() {
        lock.withLock { installs += 1L }
    }

    fun recordTeardown() {
        lock.withLock { teardowns += 1L }
    }

    fun recordEvent() {
        lock.withLock { events += 1L }
    }

    fun totalInstalls(): Long = lock.withLock { installs }

    fun totalTeardowns(): Long = lock.withLock { teardowns }

    fun totalEvents(): Long = lock.withLock { events }
}
