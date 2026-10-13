package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.input.DropItemKind

/**
 * Le kernel de mapping DROP des cibles iOS et tvOS : des UTI (`NSItemProvider` /
 * `UIDropSession`) entrantes, des valeurs du modèle de drop sortantes.
 *
 * Le miroir d'iOS/tvOS des tables AppKit (`KffiAppKitDropTransfer.kt:338-341` — les constantes
 * `public.file-url` / `public.url` / `public.utf8-plain-text` y portent le même fait) et du web
 * (`WebDropStimulus.kt` — le payload paresseux et le descripteur snapshot) : les ports de la
 * famille lisent leurs propres objets natifs et appellent ces fonctions, de sorte que la famille
 * ne peut pas diverger sur quelle UTI porte quel mime. RIEN ici n'importe un type UIKit : les UTI
 * arrivent en `String`/`List<String>`, ce qui rend le kernel testable dans `uikitTest`, la suite
 * que les quatre cibles natives partagent, sans appareil.
 *
 * Deux propriétés font partie du contrat, héritées du template ([UIKitInputMapping], web) :
 *
 * - **Totalité.** Chaque fonction est définie pour toute entrée qu'UIKit peut lui remettre, y
 *   compris hostile et hors domaine. Un mapping ne jette jamais : là où le modèle valide un
 *   domaine, le kernel produit une valeur que le modèle accepte, ou `null` quand il n'y en a pas.
 * - **Pas d'approximation.** Une UTI que la table ne nomme pas est PERDUE, jamais préfixée,
 *   normalisée ni transmuée en mime familier — `public.plain-text` n'est pas
 *   `public.utf8-plain-text`. Le guardien de canonicalité est l'init même de
 *   `DropItemDescriptor` (foundation) : un mime que cet init rejette ne peut jamais sortir du
 *   kernel par construction, et le test l'atteste en le construisant.
 */

/**
 * Mappe une UTI enregistrée d'un item provider sur le mime canonique du modèle, ou `null` quand
 * la table ne la nomme pas — le type est alors perdu, jamais fabriqué : l'appelant DROPE le type.
 *
 * La table est FERMÉE (cf. KDoc de classe) : exactement les trois UTI que le plan épingle, une à
 * une avec leur AppKit (`KffiAppKitDropTransfer.kt:338-341`). Une UTI hors table (`null`) n'est
 * PAS une erreur : un drop texte d'iOS porte des dizaines d'UTI décoratives
 * (`org.chromium.source-url`, `public.image`…) que le modèle ne peut pas porter.
 */
internal fun ukitDropMimeForUti(uti: String): String? = UIKIT_DROP_TABLE[uti]?.first

/**
 * Mappe une UTI enregistrée sur le genre d'item du modèle, ou `null` quand la table ne la nomme
 * pas — même table fermée que [ukitDropMimeForUti] : mime et genre se portent par paires, jamais
 * l'un sans l'autre (la coextensivité des deux colonnes est le contrat, testée par le kernel).
 */
internal fun ukitDropItemKindForUti(uti: String): DropItemKind? = UIKIT_DROP_TABLE[uti]?.second

/**
 * Le snapshot d'UN item provider, construit depuis les UTI que le provider a déclarées
 * (`registeredTypeIdentifiers`, portées en `List<String>` par le port) et les faits de
 * présentation que la source a pu lire — ou `null` quand AUCUN type ne mappe, signalant au port
 * de SAUTER ce provider (un item dont le modèle ne peut rien dire n'est pas un item ; un
 * descripteur à mimes vides en serait un mensonge).
 *
 * Déterministe par construction : les mimes sont dédupliqués dans l'ordre des premiers ports
 * (`distinct` garde la première occurrence — l'ordre enregistré du provider), les types hors
 * table sont perdus sans trace (l'absence reste une absence), et le genre suit la précédence du
 * routage AppKit par item — File > Uri > Text (`toKffiDropItemSourceOrNull` route file-url, puis
 * url, puis texte) — quand le provider porte plusieurs UTI mappées.
 *
 * Une taille négative n'est pas une taille : le fait est perdu (`sizeBytes = null`, la politique
 * du module — miroir d'[uikitPressure]), l'item survit ; le porter tel quel ferait jeter l'init
 * du descripteur par le port. Le nom et la taille portables sont gardés TELS QUELS.
 */
internal fun ukitDropSnapshot(
    providerRegisteredTypes: List<String>,
    displayName: String?,
    sizeBytes: Long?,
): UIKitDropItemSnapshot? {
    val mimeTypes = providerRegisteredTypes.mapNotNull(::ukitDropMimeForUti).distinct()
    if (mimeTypes.isEmpty()) return null
    val kinds = providerRegisteredTypes.mapNotNull(::ukitDropItemKindForUti)
    // Irréalisable tant que les deux colonnes de la table restent coextensives : la branche
    // garde la fonction totale si une UTI un jour ne nomme qu'un mime sans genre.
    val kind = UIKIT_DROP_KIND_PRECEDENCE.firstOrNull { it in kinds } ?: return null
    return UIKitDropItemSnapshot(
        displayName = displayName,
        sizeBytes = sizeBytes?.takeIf { it >= 0L },
        mimeTypes = mimeTypes,
        kind = kind,
    )
}

/**
 * Le snapshot d'un item provider — la forme que le port donne au descripteur du modèle
 * (`DropItemDescriptor` de foundation : mêmes champs, même exigence de canonicalité). Un type
 * pur, porté tel quel d'un kernel pur à l'init du modèle.
 */
internal data class UIKitDropItemSnapshot(
    val displayName: String?,
    val sizeBytes: Long?,
    val mimeTypes: List<String>,
    val kind: DropItemKind,
)

/** La table fermée UTI → (mime canonique, genre) — la vérité entière du mapping drop UIKit. */
private val UIKIT_DROP_TABLE: Map<String, Pair<String, DropItemKind>> = mapOf(
    // `public.utf8-plain-text` → text/plain : le texte système (AppKit APPKIT_UTF8_TEXT_TYPE).
    "public.utf8-plain-text" to ("text/plain" to DropItemKind.Text),
    // `public.url` → text/uri-list : le lien nu (AppKit APPKIT_URL_TYPE, kind Uri du web).
    "public.url" to ("text/uri-list" to DropItemKind.Uri),
    // `public.file-url` → application/octet-stream, kind File : le fait AppKit du descripteur
    // binaire (APPKIT_FILE_URL_TYPE ; le contenu réel nomme son propre format au transfer).
    "public.file-url" to ("application/octet-stream" to DropItemKind.File),
)

/** La précédence des genres du snapshot, l'ordre du routage AppKit par item : File, Uri, Text. */
private val UIKIT_DROP_KIND_PRECEDENCE: List<DropItemKind> =
    listOf(DropItemKind.File, DropItemKind.Uri, DropItemKind.Text)
