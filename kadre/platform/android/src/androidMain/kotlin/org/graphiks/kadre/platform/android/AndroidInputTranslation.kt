package org.graphiks.kadre.platform.android

import org.graphiks.kadre.surface.InputDefaultBehavior

/**
 * La catégorie d'une observation clavier au point de dispatch — le miroir Android réduit de
 * `WebInputCategory` (WebInputTranslation.kt) : une touche y est soit une [ScrollingKey] (le
 * défaut DOCUMENTAIRE de la touche déplace le document lui-même), soit une [Key] ordinaire.
 *
 * Le wheel n'existe pas comme catégorie supprimable sur Android : le scroll roue/routeur y
 * entre par la motion (`ACTION_SCROLL`, [AndroidMotionObservation]) — une observation PASSIVE
 * que la phase 3 ne consomme jamais (touch/pointer jamais consommés). La catégorie manquante
 * n'est pas un écart avec le web : c'est la frontière de plateforme déclarée du plan (« le
 * wheel n'existe pas sur Android »).
 */
internal enum class AndroidInputCategory {
    ScrollingKey,
    Key,
}

/**
 * La table de suppression Android — le miroir de `WebInputTranslation.kt`, la décision du
 * comportement par défaut rendue du côté plateforme :
 *
 * - [suppressedDefaults] : les catégories dont le défaut d'hôte est supprimable sous
 *   `SuppressWhenPossible` — exactement {ScrollingKey}, le miroir de `SUPPRESSED_INPUT_DEFAULTS`
 *   web privé du wheel (cf. [AndroidInputCategory]) ;
 * - [isScrollingKey] : l'appartenance à la catégorie [AndroidInputCategory.ScrollingKey] — les
 *   NEUF keycodes documentaires [ANDROID_DOCUMENT_SCROLL_KEYS], la table FERMÉE de la delivery
 *   (Task 3), miroir exact des 9 touches `WEB_DOCUMENT_SCROLL_KEYS`
 *   (WebInputTranslation.kt:186-196 : les quatre flèches, les deux touches de page, Home, End
 *   et Space) ;
 * - [shouldSuppress] : LA décision, totale par construction — le miroir de `shouldSuppress`
 *   web (WebInputTranslation.kt:255). `HostDefault` répond `false` pour tout — la porte de
 *   sortie de la phase (« le host conserve son comportement par défaut tant que la policy ne
 *   demande pas explicitement son inhibition »), écrite comme sa propre branche pour qu'un
 *   troisième comportement doive s'y classer avant de compiler.
 *   `SuppressWhenPossible` répond vrai pour la catégorie supprimable seule — la membership
 *   `catégorie ∈ [suppressedDefaults]`, soit exactement [AndroidInputCategory.ScrollingKey] —
 *   de sorte qu'une touche hors catégorie se comporte
 *   exactement comme sous le défaut.
 *
 * L'ÉCART ASSUMÉ avec le web est la CLÉ de lecture : le web lit la touche LOGIQUE (celle que
 * le layout produit — le défaut du navigateur se définit par là) ; Android lit le KEYCODE
 * NATIF (carry de la review Task 1) : `PageUp`/`PageDown`/`Home`/`End` ne sont PAS dans la
 * table des touches nommées d'[AndroidKeyMapper] et n'existent comme touche logique que comme
 * `Unidentified` — le keycode natif, lui, les porte toujours. La fonction ne lit que ses deux
 * arguments : pure, prouvable sans fenêtre, indépendante du moment où on l'interroge.
 */
internal object AndroidInputTranslation {

    /** Catégories supprimables : {ScrollingKey} — le wheel n'existe pas sur Android. */
    public val suppressedDefaults: Set<AndroidInputCategory> = setOf(AndroidInputCategory.ScrollingKey)

    /** La catégorie d'un keycode natif au point de dispatch clavier. */
    public fun isScrollingKey(nativeKeyCode: Int): Boolean = nativeKeyCode in ANDROID_DOCUMENT_SCROLL_KEYS

    /**
     * La décision de suppression du défaut d'hôte pour une touche sous [behaviour] — la
     * question unique que le point de dispatch pose (`AndroidHostSurface.shouldConsumeKey`,
     * l'homologue du `suppressDefaultFor` web côté surface). Touch/pointer n'y entrent jamais :
     * leur observation est passive.
     */
    public fun shouldSuppress(
        nativeKeyCode: Int,
        behaviour: InputDefaultBehavior,
    ): Boolean = when (behaviour) {
        InputDefaultBehavior.HostDefault -> false
        InputDefaultBehavior.SuppressWhenPossible -> isScrollingKey(nativeKeyCode)
    }
}
