# Task 2 — Noyau d'observation métrique (kernel pur) — Rapport

**Statut : DONE** · Commit `1e99b7c6` (`feat(uikit): add the pure surface observation kernel`) sur `zcode/sess-56645e3f` (base `d0b9292b`).

**Fichiers créés** (worktree `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f`) :
- `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitSurfaceObservation.kt` (130 l.)
- `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitSurfaceObservationTest.kt` (270 l.)

---

## 1. Correspondances avec le template Android (`AndroidSurfaceMetrics.kt`, lu intégralement)

Le kernel UIKit est le miroir SÉMANTIQUE du template Android, avec le pont géométrique inversé.

| Sémantique | Android (`AndroidSurfaceMetrics.kt`) | UIKit (ce kernel) |
|---|---|---|
| Observation copiée, zéro type SDK | `AndroidSurfaceObservation` (l.25–37), « jamais le type SDK lui-même » | `UIKitSurfaceObservation` — jamais `UIView`/`UITraitCollection`/`CGRect` ; tout est `Double?`/`SurfaceTheme?` déjà mappé par l'appelant (Task 3) |
| Champs nullables = conservés | `visible/focused/nightMode: Boolean?` — « `null` = observation manquante, l'état correspondant est CONSERVÉ — jamais inventé » (l.18–19, l.94–108) | chaque champ `Double?`/`SurfaceTheme?` `null` laisse la valeur courante en place (`?: current.…`) |
| Génération = étiqette de ressource | `generation: Long` (l.36), doc l.20–23 ; le réducteur rejette `observation.generation != currentGeneration` → `Stale` (l.172) | même étiquette ; la gate `!=` est la PREMIÈRE instruction du kernel (le paramètre `currentGeneration` tient la place du compteur que le réducteur Android possède en propre) |
| Réduction à trois causes | `AndroidSurfaceChange { Metrics(rotated, densityChanged, resized); Ignored; Stale }` (l.47–56) | `UIKitSurfaceReduction { Metrics(metrics, appearance); Ignored; Stale }` — pas de booléens de cause : la cause géométrique fine (rotation…) n'existe pas sur UIKit/tvOS (pas de rotation de fenêtre en phase 2), le porteur porte les métriques ET l'apparence |
| Pont géométrique | « La densité est le pont : le physique est pris tel qu'observé, le logique et les insets sont la division IEEE de `toLogical` » (l.72–74, l.82–93) | pont INVERSÉ : UIKit observe le logique (`bounds`, `displayScale`) → le logique est pris tel qu'observé, le physique est DÉRIVÉ par `logical.toPhysical(scale)` (arrondi Ceil par défaut de foundation — le même appel que l'invariant de `SurfaceMetrics`, donc le candidat le satisfait par construction : scale seul → physique recalculé ; logique seul → physique = logique×scale ; insets seuls → taille/scale intacts) |
| Dédupe → Ignored | `if (application.candidate == state) return Ignored` (l.174) | `if (candidate == current && nextAppearance == appearance) return Ignored` (égalité structurelle des deux axes) |
| Validité = ne publie rien | précédent « une vue non mesurée ne publie rien » (attach borné, cf. aussi `initialSnapshot` UIKit l.335–341 qui borne à 1 pt) | dimension non finie ou <= 0, scale non fini ou <= 0 → `Ignored` ; extension même principe : inset non fini ou négatif → `Ignored` (contrat de `LogicalInsets` — le kernel total ne jette jamais) |
| Thème sans invention | `nightMode: null → current.appearance` ; jamais « le contraste n'est déduit de `uiMode` » (l.75–77) | `theme: null → appearance` inchangée ; non nul → `appearance.copy(theme = …)` : le contraste reste ce que `current` avait (Unknown à la première lecture) |
| Candidat à révision constante | le candidat est construit sans bump ; la publication (réducteur) décide du bump (l.168–178) | le kernel ne connaît pas `SurfaceRevision` : il retourne le verdict nu, le réducteur de la Task 3 allouera la révision |

**Amendement de signature (documenté)** : le brief donne `apply(current, appearance, observation)` ; la comparaison de génération a besoin de la génération courante, ajoutée en paramètre : `apply(current: SurfaceMetrics, appearance: SurfaceAppearance, currentGeneration: Long, observation: UIKitSurfaceObservation)`. C'est le même découpage qu'Android : là-bas le compteur vit DANS le réducteur (`onObservation` lit `currentGeneration` privé, l.171–172) ; ici le kernel pur reçoit la valeur, la Task 3 la lui passera depuis son propre compteur (même patron `supersedeGeneration`).

**Nom du kernel** : le Produces du brief nomme `internal object UIKitSurfaceObservationKernel { fun apply(...) }` (la ligne « Files » mentionne `uikitSurfaceApply`, nom du fichier d'observation). Retenu : la forme du Produces, `UIKitSurfaceObservationKernel.apply`.

---

## 2. Code final du kernel (`UIKitSurfaceObservation.kt`, intégral)

```kotlin
package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.internal.runtime.SurfaceMetrics
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.toPhysical

/**
 * Observation UIKit copiée (frontière : jamais le type UIKit lui-même — `UIView`, `UITraitCollection`
 * ou tout `CGRect` restent de l'autre côté ; ce ne sont ici que des valeurs copiées par le binding,
 * Task 3). Toutes les observations sont nullable : `null` = observation manquante, l'état
 * correspondant est CONSERVÉ — jamais inventé. Les dimensions et le scale sont en points logiques
 * UIKit (`bounds.size`, `displayScale`) : l'observation est logique d'abord, le physique est DÉRIVÉ
 * par le kernel (miroir inversé d'Android, dont l'observation est physique d'abord).
 *
 * [theme] est DÉJÀ mappé par l'appelant (`UIUserInterfaceStyle` → [SurfaceTheme]) : le kernel ne
 * voit aucun type SDK. Un `theme` non nul remplace le thème, un `theme` nul laisse l'apparence
 * intacte — et le contraste n'est JAMAIS déduit du thème (miroir Android : « le contraste n'est
 * jamais déduit de `uiMode` »).
 *
 * [generation] est l'étiqette de la ressource observée : les callbacks (`layoutSubviews`, traits,
 * safe area) d'une vue remplacée peuvent arriver après sa recréation ; seule l'observation dont la
 * génération est la génération COURANTE est admise.
 */
internal data class UIKitSurfaceObservation(
    val logicalWidth: Double?,
    val logicalHeight: Double?,
    val scaleFactor: Double?,
    val safeAreaTop: Double?,
    val safeAreaRight: Double?,
    val safeAreaBottom: Double?,
    val safeAreaLeft: Double?,
    val theme: SurfaceTheme?,
    val generation: Long,
)

/**
 * Ce que l'observation change, causes distinguées (roadmap §Phase 2). [Metrics] signifie
 * « l'observation a publié un état nouveau » : elle porte à la fois les métriques suivantes ET
 * l'apparence suivante (le thème traverse le même kernel ; le contraste reste ce que `current`
 * avait — Unknown à la première lecture). `Ignored` : le candidat est identique au courant, ou
 * l'observation est non mesurable (valeurs non finies / <= 0) → pas de publication. `Stale` : la
 * génération de l'observation n'est pas la courante → rejeté, état inchangé.
 */
internal sealed interface UIKitSurfaceReduction {
    public data class Metrics(
        public val metrics: SurfaceMetrics,
        public val appearance: SurfaceAppearance,
    ) : UIKitSurfaceReduction

    public data object Ignored : UIKitSurfaceReduction
    public data object Stale : UIKitSurfaceReduction
}

/**
 * Le noyau pur de l'observation métrique UIKit → [SurfaceMetrics] (aucun type UIKit : l'observation
 * est une copie, cf. [UIKitSurfaceObservation]). Miroir direct d'`androidSurfaceApply`
 * (AndroidSurfaceMetrics.kt:78–120) avec le pont INVERSÉ : Android observe le physique et divise
 * (`toLogical`, division IEEE) ; UIKit observe le logique et multiplie (`toPhysical`, arrondi Ceil
 * par défaut de foundation — le même appel que l'invariant de [SurfaceMetrics], donc le candidat le
 * satisfait par construction). `null` = champ inchangé ; une observation identique au courant est
 * [UIKitSurfaceReduction.Ignored] ; une génération non courante est [UIKitSurfaceReduction.Stale]
 * (gate PREMIÈRE, miroir du réducteur Android l.172 — le paramètre [currentGeneration] tient la
 * place du compteur que le réducteur Android possède ; le réducteur UIKit de la Task 3 lira
 * [currentGeneration] de la même façon).
 *
 * Contrat de pureté : zéro import UIKit, aucune mutation, jamais d'exception — une observation non
 * mesurable (dimension non finie ou <= 0 ; scale non fini ou <= 0 ; inset non fini ou négatif,
 * contrat de [LogicalInsets]) ne publie RIEN ([UIKitSurfaceReduction.Ignored]), précédent Android :
 * « une vue non mesurée ne publie rien ». Le rejet est entier, jamais partiel.
 */
internal object UIKitSurfaceObservationKernel {
    public fun apply(
        current: SurfaceMetrics,
        appearance: SurfaceAppearance,
        currentGeneration: Long,
        observation: UIKitSurfaceObservation,
    ): UIKitSurfaceReduction {
        if (observation.generation != currentGeneration) return UIKitSurfaceReduction.Stale

        // Validité d'abord (toute l'observation, jamais un champ) : une valeur non mesurable
        // rend l'observation entière non publiable.
        val width = observation.logicalWidth
        val height = observation.logicalHeight
        val scale = observation.scaleFactor
        if (width != null && !(width.isFinite() && width > 0.0)) return UIKitSurfaceReduction.Ignored
        if (height != null && !(height.isFinite() && height > 0.0)) return UIKitSurfaceReduction.Ignored
        if (scale != null && !(scale.isFinite() && scale > 0.0)) return UIKitSurfaceReduction.Ignored
        val top = observation.safeAreaTop
        val right = observation.safeAreaRight
        val bottom = observation.safeAreaBottom
        val left = observation.safeAreaLeft
        if (top != null && !(top.isFinite() && top >= 0.0)) return UIKitSurfaceReduction.Ignored
        if (right != null && !(right.isFinite() && right >= 0.0)) return UIKitSurfaceReduction.Ignored
        if (bottom != null && !(bottom.isFinite() && bottom >= 0.0)) return UIKitSurfaceReduction.Ignored
        if (left != null && !(left.isFinite() && left >= 0.0)) return UIKitSurfaceReduction.Ignored

        // Les `null` conservent la valeur courante : le candidat est reconstruit champ par champ.
        val logical = LogicalSize(
            width = width ?: current.logicalSize.width,
            height = height ?: current.logicalSize.height,
        )
        val effectiveScale = scale ?: current.scaleFactor
        // L'invariant de `SurfaceMetrics` (`physical == logical.toPhysical(scale)`) : que seul le
        // scale change, seul le logique change ou seuls les insets changent, le physique est
        // recalculé du couple effectif (logique, scale) — jamais repris tel quel du courant.
        val candidate = SurfaceMetrics(
            logicalSize = logical,
            physicalSize = logical.toPhysical(effectiveScale),
            scaleFactor = effectiveScale,
            safeAreaInsets = LogicalInsets(
                top = top ?: current.safeAreaInsets.top,
                right = right ?: current.safeAreaInsets.right,
                bottom = bottom ?: current.safeAreaInsets.bottom,
                left = left ?: current.safeAreaInsets.left,
            ),
        )
        // Le thème traverse le kernel, déjà mappé par l'appelant ; `null` = apparence inchangée,
        // et `copy(theme = …)` ne touche JAMAIS le contraste.
        val nextAppearance = when (observation.theme) {
            null -> appearance
            else -> appearance.copy(theme = observation.theme)
        }
        // Dédup (miroir Android l.174) : candidat identique au courant → pas de publication.
        if (candidate == current && nextAppearance == appearance) return UIKitSurfaceReduction.Ignored
        return UIKitSurfaceReduction.Metrics(metrics = candidate, appearance = nextAppearance)
    }
}
```

`SurfaceMetrics` vient de `org.graphiks.kadre.internal.runtime` (runtime, `SurfaceStimulus.kt:47`) — import déjà consommé par `UIKitWindowCommandPort.kt:20` ; friend-modules déjà câblés dans `build.gradle.kts`.

---

## 3. TDD rouge (citation exacte)

Tests écrits EN PREMIER (constructeurs réels `SurfaceMetrics`/`LogicalSize`/`LogicalInsets`/`PhysicalSize`/`SurfaceAppearance`), le type étant absent :

```
Execution failed for task ':kadre:platform:uikit:compileTestKotlinIosSimulatorArm64' (registered in build file 'kadre/platform/uikit/build.gradle.kts').
> Compilation finished with errors

e: .../UIKitSurfaceObservationTest.kt:239:13 Unresolved reference 'UIKitSurfaceReduction'.
e: .../UIKitSurfaceObservationTest.kt:240:13 Unresolved reference 'UIKitSurfaceObservationKernel'.
e: .../UIKitSurfaceObservationTest.kt:259:8 Unresolved reference 'UIKitSurfaceObservation'.
```

(`./gradlew :kadre:platform:uikit:iosSimulatorArm64Test`, BUILD FAILED en 2 s.) Implémentation ensuite → vert.

---

## 4. Liste des tests et mutation tuée par chacun

Fixture : `current = SurfaceMetrics(LogicalSize(800,600), PhysicalSize(1600,1200), 2.0, LogicalInsets(0,0,0,0))` (invariant respecté), `appearance = SurfaceAppearance(Unknown, Unknown)` (« Unknown on first read »), `generation = 7L` (non nul : une mutation « génération courante = littéral 0 » est tuée).

| Test | Sémantique du brief | Mutation tuée |
|---|---|---|
| `resizePropagatesLogicalAndDerivesPhysical` | resize propagé | garde `current.physicalSize` au lieu de dériver (invariant violée → throw `SurfaceMetrics`) ; prend le physique « tel qu'observé » à la Android ; substitue les champs null par des valeurs inventées. Sous-cas `100.25×2 = 200.5` → 201 : fige l'arrondi Ceil du pont (Floor/TowardZero/NearestTiesToEven donneraient 200) |
| `scaleOnlyChangeRecomputesPhysical` | scale propagé change physical | conserve le physique courant quand seul le scale change (le `Metrics` serait rejeté par l'invariant de `SurfaceMetrics`) |
| `safeAreaOnlyChangeLeavesSizeAndScaleUntouched` | safe-area propagée | rebuild de la taille/scale depuis les observations null (0.0 inventé) ; rebuild des insets entiers |
| `themeOnlyChangeMovesAppearanceAndNeverContrast` | thème seul → apparence changée, contrast inchangé | déduction du contraste depuis le thème (Normal par défaut, High sur Dark, réinitialisation) — testé depuis `(Unknown, Unknown)` ET depuis `(Light, High)` ; mutation qui déclencherait `Ignored` bien que le thème bouge ; mutation qui reconstruirait des métriques sur un mouvement de thème seul (`assertEquals(current, result.metrics)`) |
| `nullFieldsPublishNothingAndDuplicatesAreIgnored` | champs null → inchangés (+ dédup) | champs null remplacés par un défaut (0.0, Light…) — le candidat différerait → `Metrics` au lieu d'`Ignored` ; dédup manquant (publication d'un état identique, miroir Android l.174) ; mutation qui ne déduplique que les métriques sans l'apparence |
| `staleGenerationIsRejected` | génération stale → `Stale` | génération ignorée (publication d'une observation périmée — le callback tardif d'une ressource remplacée publierait) ; borne `<` au lieu d'inégalité `!=` (testé des deux côtés, +1 et −1) ; ordre des gates inversé — le cas « périmée ET invalide → `Stale` » (miroir Android l.172, premier test) distingue l'ordre Stale/validité |
| `nonFiniteOrNonPositiveValuesPublishNothing` | non fini / <= 0 → `Ignored` | gardes de validité absentes (0.0 léserait `LogicalSize` d'un throw, NaN propagerait) ; rejet partiel (champ valide appliqué à côté d'un champ invalide) ; kernel qui jette sur inset négatif/NaN au lieu de rejeter |

7 fonctions de test = les 7 sémantiques énumérées par le brief (le brief dit « 6 tests » et en liste 7 — les 7 sont écrits, aucun manquant, aucun fusionné).

---

## 5. Commandes Gradle et issues

| Commande | Issue |
|---|---|
| `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test` (rouge, tests d'abord) | BUILD FAILED en 2 s — `compileTestKotlinIosSimulatorArm64` : `Unresolved reference 'UIKitSurfaceObservation'/'UIKitSurfaceObservationKernel'/'UIKitSurfaceReduction'` (attendu, type absent) |
| `./gradlew :kadre:platform:uikit:iosSimulatorArm64Test` (après implémentation) | BUILD SUCCESSFUL en 7 s — `UIKitSurfaceObservationTest` : `tests="7" skipped="0" failures="0" errors="0"` (les 3 suites préexistantes passent toujours) |
| `./gradlew :kadre:platform:uikit:tvosSimulatorArm64Test` | BUILD SUCCESSFUL en 16 s — `UIKitSurfaceObservationTest` : `tests="7" skipped="0" failures="0" errors="0"` |

---

## 6. Auto-revue

1. **Pureté vérifiée** : `grep import` du fichier main → uniquement `org.graphiks.kadre.internal.runtime.SurfaceMetrics` + 5 types foundation ; zéro référence `platform.UIKit` (grep `UIKit.` = 0). Aucune mutation, aucune exception possible : toute valeur hors contrat est rejetée AVANT construction du candidat (`LogicalSize`/`LogicalInsets`/`toPhysical` ne peuvent pas jeter).
2. **Invariants des trois cas** couverts : scale seul → physique recalculé ; logique seul → physique = logique×scale ; insets seuls → taille/scale prouvés intacts ET physiques identiques (le candidat recalcule `current.logical.toPhysical(current.scale)` qui égale `current.physicalSize` par l'invariant courant — le dédup passe, la taille ne « bouge » jamais pour un mouvement d'insets).
3. **Contraste jamais inféré** : seule écriture `appearance.copy(theme = …)` ; prouvé depuis deux états contrastants (Unknown et High).
4. **Extensions documentées hors brief strict** (défendables, signalées) : (a) garde de validité aussi sur les insets (non fini / négatif → `Ignored`) — sinon `LogicalInsets` jetterait, violant la totalité du kernel ; le brief ne demandait que dimensions/scale ; (b) ordre des gates Stale-avant-validité épinglé par test (miroir exact du réducteur Android l.172) ; (c) génération de fixture = 7L et non 0L (tue une classe de mutation sans coût).
5. **Points laissés à la Task 3** (volontairement absents d'ici) : le compteur de génération et `supersedeGeneration` (l'équivalent Android du réducteur qui possède `currentGeneration`) — le kernel reçoit `currentGeneration` en paramètre, la doc le dit explicitement ; le mapping `UIUserInterfaceStyle → SurfaceTheme` reste côté appelant (le kernel ne voit que `SurfaceTheme?`) ; les booléens de cause géométrique d'Android (`rotated/densityChanged/resized`) n'ont pas d'équivalent demandé par le brief UIKit — le porteur `Metrics(metrics, appearance)` est conforme au Produces.
6. **Avertissement Gradle préexistant** : `-friend-modules` passé deux fois (warning), wiring du build tel quel, non déclenché par ce changement ; aucun impact sur le résultat.
7. Style maison respecté : KDoc français, `internal` partout (module en `explicitApi()` — les déclarations publiques internes au sealed interface sont marquées `public` comme dans le template Android).
