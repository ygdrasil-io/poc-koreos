@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.Foundation.NSItemProvider
import platform.Foundation.NSItemProviderReadingProtocol
import platform.Foundation.NSProgress
import platform.darwin.NSObject
import platform.UIKit.UIDragItem
import platform.UIKit.UIDragSessionProtocol
import platform.UIKit.UIDropInteraction
import platform.UIKit.UIDropInteractionDelegateProtocol
import platform.UIKit.UIDropOperationCopy
import platform.UIKit.UIDropProposal
import platform.UIKit.UIDropSessionProgressIndicatorStyle
import platform.UIKit.UIDropSessionProtocol
import platform.UIKit.UIView

/**
 * Le probe de CONFORMANCE drop du spike Task 1 (phase 7 iOS) — la vérité de compilation du klib
 * iOS (2.4.20, compilée iosSimulatorArm64 ET rejouée au gate Gradle), écrite ici pour que les
 * Tasks 2-3 bâtissent dessus sans re-sonder :
 *
 * - **Décision 1 — OUI.** `@ExportObjCClass class ... : NSObject(), UIDropInteractionDelegateProtocol`
 *   compile (patron `UIKitTextInputReceiver`/`UITextFieldDelegateProtocol` confirmé). Une courbe :
 *   les cinq sélecteurs du delegate partagent la base Kotlin `dropInteraction` (l'étiquette du
 *   premier paramètre du sélecteur Obj-C), donc Kotlin les voit en surcharges CONFLICTUELLES —
 *   chaque méthode exige `@ObjCSignatureOverride` (`dropInteraction:canHandleSession:` vs
 *   `dropInteraction:sessionDidEnter:` …). Erreur brute du compilateur sans l'annotation :
 *   « conflicting overloads … Add @ObjCSignatureOverride to allow collision for functions
 *   inherited from Objective-C ».
 * - **Décision 2 — la session est `UIDropSessionProtocol`, pas une classe.** Il n'existe PAS de
 *   `platform.UIKit.UIDropSession` (import résolu → « unresolved reference 'UIDropSession' ») :
 *   le protocole Obj-C `UIDropSession` arrive en Kotlin comme interface `UIDropSessionProtocol`,
 *   ET c'est LUI le type des paramètres `session` du delegate (les signatures potentielles que le
 *   compilateur énumère : `fun dropInteraction(interaction: UIDropInteraction, canHandleSession:
 *   UIDropSessionProtocol): Boolean` etc. — le paramètre porte le nom du TAIL du sélecteur).
 *   Un conformer Kotlin complet compile (classe [UIKitFakeDropSessionProbe] ci-dessous) : tous
 *   les membres requis sont implémentables en `override fun` FORME FONCTION (le compilateur
 *   exige `fun items(): List<*>`, `fun allowsMoveOperation(): Boolean`,
 *   `fun canLoadObjectsOfClass(aClass: NSItemProviderReadingProtocol): Boolean`,
 *   `fun progressIndicatorStyle()` + `fun setProgressIndicatorStyle(...)`,
 *   `fun loadObjectsOfClass(aClass: NSItemProviderReadingProtocol, completion: (List<*>?) -> Unit): NSProgress`,
 *   `fun progress(): NSProgress`… — les `Class<NSItemProviderReading>` d'Obj-C arrivent en
 *   `NSItemProviderReadingProtocol`, donc PRATIQUES ; l'enum du style d'indicateur passe par
 *   `byValue` déprécié, le SEUL chemin, même suppressions locales que
 *   `UIKitTextInputReceiver.applyTraits`). La classe fake est donc TENABLE côté Kotlin
 *   ([uikitDropConformanceSmoke] la construit et la passe COMME ARGUMENT `session` au delegate) —
 *   mais le FAUX de session des tests driver reste la classe SWIFT épinglée par le plan
 *   (`KadreUikitDropSpikeTests.swift` / Task 4) : une classe `internal` Kotlin ne traverse PAS
 *   l'en-tête du framework vers le driver Swift.
 * - **tvOS : la couture est iosMain seule.** `UIDropInteraction`,
 *   `UIDropInteractionDelegateProtocol`, `UIDropSessionProtocol`, `UIDragDropSessionProtocol`
 *   sont ABSENTS du klib tvOS (0 occurrence, `API_UNAVAILABLE(tvos)` de UIDragSession.h:19) — ce
 *   fichier vit dans iosMain et est INVISIBLE de tvosArm64 : le gate
 *   `compileKotlinTvosArm64` reste vert sans toucher un symbole drop côté tvOS.
 * - **Les cinq hooks du delegate** (les méthodes `@optional` du protocole, portées avec
 *   implémentations par défaut par le klib — seules les SURCHARGES sont nécessaires) sont
 *   redéfinies en stubs à valeurs fixes : le squelette que la Task 2 remplit de sémantique.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@ExportObjCClass
internal class UIKitDropInteractionDelegateProbe : NSObject(), UIDropInteractionDelegateProtocol {

    /** Le hook d'admission : la session répond `hasItemsConformingToTypeIdentifiers` — le probe appelle DANS le paramètre session, la preuve que la route est praticable. */
    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, canHandleSession: UIDropSessionProtocol): Boolean =
        canHandleSession.hasItemsConformingToTypeIdentifiers(listOf("public.utf8-plain-text"))

    /** L'entrée du drag dans l'interaction — stub à valeur fixe (Task 2 : l'événement Entered). */
    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, sessionDidEnter: UIDropSessionProtocol) {
    }

    /** La mise à jour (position) — la proposition `Copy` fixe (Task 2 : la table des opérations). */
    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, sessionDidUpdate: UIDropSessionProtocol): UIDropProposal =
        UIDropProposal(dropOperation = UIDropOperationCopy)

    /** La sortie du drag — stub à valeur fixe. */
    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, sessionDidExit: UIDropSessionProtocol) {
    }

    /** Le drop effectué — stub à valeur fixe (Task 2 : le snapshot des items, la lecture paresseuse). */
    @ObjCSignatureOverride
    override fun dropInteraction(interaction: UIDropInteraction, performDrop: UIDropSessionProtocol) {
    }
}

/**
 * Le conformer KOTLIN de `UIDropSessionProtocol` — la preuve de compilation de la décision 2
 * (membres requis tous implémentables, en forme fonction, cf. KDoc de classe). Les stubs rendent
 * des valeurs fixes ; le FAUX de session des tests driver reste la classe Swift épinglée
 * (`FakeDropSession` dans `KadreUikitDropSpikeTests.swift`, Task 4), la classe internal Kotlin
 * étant invisible du driver.
 */
@ExportObjCClass
internal class UIKitFakeDropSessionProbe(
    private val types: List<String>,
) : NSObject(), UIDropSessionProtocol {

    /** `NSArray<UIDragItem *> *items` — un item provider vide suffit au probe (la construction d'items porteurs vit côté Task 2/driver). */
    override fun items(): List<*> = listOf(UIDragItem(itemProvider = NSItemProvider()))

    /** `- (CGPoint)locationInView:` — l'origine, tranche `CValue<CGPoint>` telle quelle. */
    override fun locationInView(view: UIView): CValue<CGPoint> = CGPointMake(0.0, 0.0)

    /** `allowsMoveOperation` — le stub refuse le move (la proposition fixe du probe est Copy). */
    override fun allowsMoveOperation(): Boolean = false

    /** `isRestrictedToDraggingApplication` (le getter Obj-C, sans préfixe `is` perdu). */
    override fun isRestrictedToDraggingApplication(): Boolean = false

    /** `hasItemsConformingToTypeIdentifiers:` — l'adhérence aux types du probe, éléments `List<*>` d'Obj-C. */
    override fun hasItemsConformingToTypeIdentifiers(typeIdentifiers: List<*>): Boolean =
        typeIdentifiers.filterIsInstance<String>().any { it in types }

    /** `canLoadObjectsOfClass:` — le `Class<NSItemProviderReading>` Obj-C arrive en `NSItemProviderReadingProtocol`. */
    override fun canLoadObjectsOfClass(aClass: NSItemProviderReadingProtocol): Boolean = false

    /** `localDragSession` — `id<UIDragSession>` nullable : le stub n'a pas de drag local. */
    override fun localDragSession(): UIDragSessionProtocol? = null

    /** `progressIndicatorStyle` — lecture ; l'enum par `byValue` déprécié (le seul chemin, cf. KDoc de classe). */
    @Suppress("DEPRECATION")
    override fun progressIndicatorStyle(): UIDropSessionProgressIndicatorStyle =
        UIDropSessionProgressIndicatorStyle.byValue(0uL)

    /** `setProgressIndicatorStyle:` — écriture séparée (le couple getter/setter du couple property Obj-C). */
    override fun setProgressIndicatorStyle(progressIndicatorStyle: UIDropSessionProgressIndicatorStyle) {
    }

    /** `loadObjectsOfClass:completion:` — le stub rend un `NSProgress` frais, la completion jamais appelée. */
    override fun loadObjectsOfClass(aClass: NSItemProviderReadingProtocol, completion: (List<*>?) -> Unit): NSProgress = NSProgress()

    /** `progress` (de `NSProgressReporting`) — un `NSProgress` frais. */
    override fun progress(): NSProgress = NSProgress()
}

/**
 * Le smoke du probe : construit le delegate ET la fake session Kotlin, pose l'interaction sur le
 * delegate, puis passe la fake COMME ARGUMENT `session` au hook d'admission — la route complète
 * « conformer Kotlin → paramètre session du delegate » est praticable (le hook y répond en
 * appelant DANS la session : `hasItemsConformingToTypeIdentifiers`). La preuve de compilation et
 * de construction que la Task 2 réutilise pour son delegate réel.
 */
internal fun uikitDropConformanceSmoke(): Boolean {
    val delegate = UIKitDropInteractionDelegateProbe()
    val fake = UIKitFakeDropSessionProbe(listOf("public.utf8-plain-text"))
    val interaction = UIDropInteraction(delegate = delegate)
    return delegate.dropInteraction(interaction, canHandleSession = fake)
}
