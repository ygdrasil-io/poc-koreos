@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.useContents
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.SurfaceTheme
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIScreenModeDidChangeNotification
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneWillEnterForegroundNotification
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Le branchement SDK des métriques (phase 2, Task 3) : la couture entre la vue/fenêtre du host et
 * la surface observable — chaque déclencheur produit une re-lecture COMPLÈTE (bounds +
 * `displayScale` + `safeAreaInsets` via `useContents` — aucune rétention native — et thème via
 * `traitCollection`), copiée dans une [UIKitSurfaceObservation] étiquetée à la génération
 * courante et publiée vers [publish]. Le port (réducteur de rôle) applique le kernel pur
 * [`UIKitSurfaceObservationKernel`] et route les stimuli.
 *
 * GÉNÉRATION (miroir du réducteur Android, que le binding LIT — jamais un miroir lockstep) : le
 * compteur appartient À CE binding ; [generation] est l'étiquette lue par le port pour la gate du
 * kernel, et [teardown] appelle [supersedeGeneration] — les livraisons tardives d'une ressource
 * remplacée (bloc déjà en file au moment du close) ne publieraient jamais. Une lecture initiale
 * POSTÉE sur la file principale (template Android : `view.post`) couvre la vue attachée mais pas
 * encore repassée par layout ; un runnable posté qui suit un teardown est absorbé par le drapeau.
 *
 * POSTURE RELEVÉE (Task 3, preuves de compile dans le rapport) : le fire KVO prévu par le brief
 * (`view.addObserver(observer, forKeyPath = "bounds", …)` + override
 * `observeValueForKeyPath:ofObject:change:context:`) n'est PAS réalisable en Kotlin/Native pur —
 * la catégorie `NSKeyValueObserving` est mappée en extensions (appelables, non-overrideables),
 * `NSKeyValueObservingProtocol` n'existe pas dans le klib, et une méthode Kotlin d'une sous-classe
 * `NSObject` sans conformance formelle n'émet PAS de thunk Obj-C (framework de preuve : aucun
 * sélecteur dans `__objc_methname`). Enregistrer un observer sans callback servi ferait
 * lever l'implémentation de base d'`NSObject` au premier fire — AUCUN observer KVO n'est posé.
 * Le repli documenté du brief s'applique : re-lectures sur NOTIFICATIONS uniquement —
 * - `UISceneWillEnterForegroundNotification` / `UISceneDidActivateNotification` (même mécanisme
 *   `addObserverForName(name, scene, mainQueue)` que la phase 1, via un [UIKitLifecycleSource]
 *   privé — le close du source route les livraisons tardives) ;
 * - `UIScreenModeDidChangeNotification` (constante C du klib — la propriété de classe
 *   `UIScreen.modeChangeNotification` citée par le brief n'existe pas dans le mapping ; tvOS +
 *   iOS), sans filtre d'objet : une re-lecture déclenchée par un autre écran est une no-op
 *   absorbée par la dédup du kernel ;
 * - un thème `UIUserInterfaceStyleUnknown` publie `theme = null` — conservé, jamais inventé
 *   (miroir Android : « Unknown reste Unknown ») ;
 * - la visibilité (`window.isHidden`, KVO `hidden` du brief — même indisponibilité) est re-lue
 *   sur les MÊMES déclencheurs et routée vers [onVisibility] ; l'occlusion reste intouchée
 *   (`Visible`, phase 1). Le runtime déduplique les valeurs répétées.
 *
 * Tout le binding vit sur la file principale (l'appelant d'`attach` EST sur le main ; les
 * observers sont installés sur la main queue). Après [teardown], aucun callback résiduel n'agit :
 * tokens retirés, drapeau court-circuite, génération périmée — le zéro-résidu du binding
 * lifecycle, même discipline.
 */
internal class UIKitSurfaceObservationBinding(
    private val view: UIView,
    private val scene: UIWindowScene?,
    private val window: UIWindow,
    private val publish: (UIKitSurfaceObservation) -> Unit,
    private val onVisibility: (visible: Boolean) -> Unit,
) {
    private val lock = RuntimeLock()
    private var currentGeneration: Long = 0L
    private var installed: Boolean = false
    private var tornDown: Boolean = false
    private var sceneSource: UIKitLifecycleSource? = null
    private var modeToken: NSObjectProtocol? = null

    /** La génération courante — l'étiquette que le port LIT pour la gate du kernel. */
    internal val generation: Long
        get() = lock.withLock { currentGeneration }

    /**
     * Pose les observers de re-lecture puis fait la lecture initiale. Idempotent. Toute exception
     * d'installation remonte à l'appelant (le port l'enveloppe dans `runCatching` → rapport, sans
     * jamais casser l'attach) ; les tokens déjà posés restent retirables par [teardown].
     */
    fun install() {
        lock.withLock {
            if (tornDown || installed) return
            installed = true
        }
        // Observers de scène de re-lecture (filtre object = scene + vérification isEqual, le
        // mécanisme exact de la phase 1) ; les livraisons post-close sont routées en tardif.
        scene?.let { scene ->
            val source = UIKitLifecycleSource(scene)
            REREAD_NOTIFICATION_NAMES.forEach { name ->
                source.observe(name, onEvent = { reread() }, onLateCallback = {})
            }
            // Fenêtre de réservation du token (même commentaire que UIKitLifecycleSource) :
            // installation et close sont uniquement sur la file principale.
            lock.withLock { sceneSource = source }
        }
        // Mode change (tvOS + iOS) : la constante C du klib (`UIScreenModeDidChangeNotification`,
        // `String?` — d'où le `!!`, mêmes faits d'interop que les constantes de scène).
        val token = NSNotificationCenter.defaultCenter.addObserverForName(
            UIScreenModeDidChangeNotification!!,
            null,
            NSOperationQueue.mainQueue(),
        ) { _ -> reread() }
        lock.withLock { modeToken = token }
        // Lecture initiale POSTÉE : elle ne peut courir qu'APRÈS le tour d'attach courant, une
        // observation consommée avant le commit diviserait kernel et surface (template Android).
        dispatch_async(dispatch_get_main_queue()) { reread() }
    }

    /** Retrait au teardown — zéro observer résiduel, génération invalidée. Idempotent. */
    fun teardown() {
        val stale = lock.withLock {
            if (tornDown) {
                null
            } else {
                tornDown = true
                val source = sceneSource
                val token = modeToken
                sceneSource = null
                modeToken = null
                source to token
            }
        } ?: return
        supersedeGeneration()
        stale.first?.close()
        stale.second?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
    }

    /**
     * Invalide la génération courante ([teardown]) : toute observation étiquetée avant le close
     * mais consommée après devient `Stale` au sens du kernel — jamais publiée.
     */
    private fun supersedeGeneration() {
        lock.withLock { currentGeneration += 1L }
    }

    /**
     * La re-lecture COMPLÈTE — la vérité courante de la vue (jamais le payload d'un événement),
     * copiée hors des structs Obj-C, étiquetée à la génération lue SOUS verrou. La publication
     * elle-même est hors verrou : le port (et son kernel) ne court-circuite jamais le binding.
     */
    private fun reread() {
        if (lock.withLock { tornDown }) return
        val bounds = view.bounds.useContents { size.width to size.height }
        val scale = view.traitCollection.displayScale
        val insets = view.safeAreaInsets.useContents { LogicalInsets(top, right, bottom, left) }
        // `Unknown` reste `Unknown` : un style indéchiffrable publie `null` — l'apparence est
        // conservée par le kernel, jamais inventée (miroir Android, `uiMode` indéchiffrable).
        val theme = when (view.traitCollection.userInterfaceStyle) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark -> SurfaceTheme.Dark
            UIUserInterfaceStyle.UIUserInterfaceStyleLight -> SurfaceTheme.Light
            else -> null
        }
        val observation = UIKitSurfaceObservation(
            logicalWidth = bounds.first,
            logicalHeight = bounds.second,
            // `displayScale` vaut 0 hors fenêtre : le kernel rejette l'observation entière
            // (précédent Android — une vue non mesurée ne publie rien).
            scaleFactor = scale,
            safeAreaTop = insets.top,
            safeAreaRight = insets.right,
            safeAreaBottom = insets.bottom,
            safeAreaLeft = insets.left,
            theme = theme,
            generation = lock.withLock { currentGeneration },
        )
        publish(observation)
        // Visibilité : re-lue sur les mêmes déclencheurs (le KVO `hidden` du brief est
        // indisponible, cf. KDoc de classe) ; l'occlusion n'est PAS déduite — le port route
        // `Visible` constant. Les valeurs répétées sont dédupliquées par le runtime.
        onVisibility(!window.hidden)
    }

    private companion object {
        /** Les noms de notification de re-lecture (constantes `String?` du klib → `!!`). */
        val REREAD_NOTIFICATION_NAMES = listOf(
            UISceneWillEnterForegroundNotification!!,
            UISceneDidActivateNotification!!,
        )
    }
}
