@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * La source de transfert DROP des cibles iOS et tvOS : le miroir d'iOS/tvOS de la couture web
 * (`WebDropStimulus.kt:136-197` — l'item à fente de payload et la source qui porte les entries) et
 * du précédent AppKit (`KffiAppKitDropTransfer.kt` — `Replayable`, les échecs fermés de plateforme).
 *
 * La même discipline que la famille : la surface que le runtime voit est KOTLIN SEUL — un
 * [UIKitDropItem] ne porte que le snapshot pur de la Task 1 et une fente à [UIKitDropPayload] ; le
 * handle natif (l'`NSItemProvider` du drag) ne vit que DANS LA CLOSURE du payload que le port
 * attache (via [ukitPayloadFromProvider]), jamais dans un champ typé Obj-C de cette interface. Un
 * payload que personne n'a attaché se lit en l'échec fermé de la couture (`payload-unavailable`),
 * jamais en exception traversant une frontière.
 */

/**
 * Un item droppé vu du pipeline commun : le descripteur snapshot de l'entrée, le mode de lecture
 * qu'il honnore, et le payload qu'une lecture résout — attaché quand le port a pu lire le
 * provider, absent quand il ne l'a pas pu.
 *
 * Le descripteur est construit UNE fois, depuis [ukitDropSnapshot] : les mimes du snapshot sortent
 * de la table fermée de la Task 1, donc l'init de [DropItemDescriptor] (le guardien de
 * canonicalité) les accepte par construction — le port ne peut pas fabriquer un mime ici. La fente
 * `payload` est délibérément la SEULE chose mutable de la classe : le port la remplit depuis les
 * handles du drag, le runtime n'en voit jamais un, et un close ([UIKitDropTransferSource]) la vide
 * pour qu'un drag terminé ne soit plus lisible. La sémantique de [collectBytes] est le miroir web
 * exact : au plus `maxChunkBytes` par tranche, EOF au PREMIER chunk `null` OU court (la taille
 * inconnue se découvre à la lecture), l'annulation de la coroutine collectrice relance
 * [CancellationException] (la livraison s'arrête, le close vient du transfert), et toute autre
 * erreur de lecture est l'échec fermé `byte-read` — le contenu de l'erreur reste côté plateforme.
 */
internal class UIKitDropItem(
    snapshot: UIKitDropItemSnapshot,
    override val readMode: DropItemReadMode = DropItemReadMode.Replayable,
) : DropItemSource {
    override val descriptor: DropItemDescriptor = DropItemDescriptor(
        displayName = snapshot.displayName,
        sizeBytes = snapshot.sizeBytes,
        mimeTypes = snapshot.mimeTypes,
        kind = snapshot.kind,
    )

    internal var payload: UIKitDropPayload? = null

    /** Attache le payload qu'une lecture résout ; le dernier attach gagne, `null` détache. */
    internal fun attach(payload: UIKitDropPayload?) {
        this.payload = payload
    }

    /** Oublie le payload, avec le drag auquel il appartenait. */
    internal fun detach() {
        payload = null
    }

    override suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        if (maxChunkBytes <= 0) return KadreResult.Failure(KadreFailure.InvalidRequest("maxChunkBytes"))
        val payload = payload
            ?: return KadreResult.Failure(
                KadreFailure.PlatformFailure(KadrePlatform.UIKit, "drop", "payload-unavailable"),
            )
        val total = payload.totalBytes
        var offset = 0L
        return try {
            while (total == null || offset < total) {
                val count = if (total == null) maxChunkBytes else min(maxChunkBytes.toLong(), total - offset).toInt()
                val chunk = payload.readChunk(offset, count) ?: break
                if (chunk.isEmpty()) break
                collector(chunk)
                offset += chunk.size
                if (total == null && chunk.size < count) break
            }
            KadreResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            KadreResult.Failure(KadreFailure.PlatformFailure(KadrePlatform.UIKit, "drop", "byte-read"))
        }
    }
}

/**
 * La source qu'une entrée de drop présente au pipeline commun : les items que la session a
 * rapportés, et rien d'autre.
 *
 * Le runtime la retient pour l'offre qu'il a construite et la ferme sur tout chemin terminal qui
 * n'a pas remis un transfert à l'application. Le close détache les payloads — les handles du drag
 * s'en vont avec lui ; les descripteurs restent, comme tout snapshot. Comme toute
 * [DropTransferSource], un close n'arrive qu'une fois du runtime, et celui-ci répond de toute façon
 * à un répété sans y toucher ([detach] est son propre point fixe).
 */
internal class UIKitDropTransferSource(
    internal val entries: List<UIKitDropItem>,
) : DropTransferSource {
    override val items: List<DropItemSource> get() = entries

    override fun close() {
        entries.forEach { it.detach() }
    }
}

/**
 * La forme du seam de dispatch du pont : l'endroit où l'appel de chargement du provider est posé.
 * Le défaut le pose sur la main queue (`dispatch_async(dispatch_get_main_queue(), …)`, le patron de
 * `UIKitWindowCommandPort`), l'app réelle la pompant de toute façon ; les tests peuvent le remplacer
 * par un appel direct. La spike Task 1 a mesuré que la COMPLETION part sur un fil d'arrière-plan
 * indépendamment de ce choix (`fired=true onMainThread=false`) — la reprise de la continuation
 * n'exige donc aucun hop de fil.
 */
internal typealias UkitDropDispatch = (block: () -> Unit) -> Unit

/**
 * Le pont `NSItemProvider` → [UIKitDropPayload] : la lecture paresseuse qui suspend sur la
 * completion de `loadItemForTypeIdentifier` ([suspendCancellableCoroutine] — la reprise part du fil
 * de la completion, sans hop), puis sert des tranches depuis les octets livrés.
 *
 * - `totalBytes` reste `null` : les tailles de provider sont inconnues AVANT chargement, et le pont
 *   ne devine pas — l'EOF se découvre à la lecture, comme le contrat du payload le demande.
 * - La route texte : la completion livre un `String` → octets UTF-8. La route données : un
 *   `NSData` → octets possédés (copiés hors du handle natif, qui ne quitte jamais la closure).
 *   Toute autre livraison (y compris `nil`) est une lecture qui échoue — l'échec fermé `byte-read`
 *   de [UIKitDropItem.collectBytes], jamais une valeur fabriquée.
 * - Les octets livrés sont mis en cache DANS la closure : le provider livre l'item ENTIER (la
 *   plateforme ne sait pas trancher), une lecture `Replayable` relit donc le même cache sans
 *   recharger. La séquentialité est portée par l'appelant (le runtime n'admet qu'une lecture à la
 *   fois) ; une lecture échouée ne met rien en cache et réessaie.
 * - Le type chargé : le type préféré du port, sinon le premier type enregistré du provider — et
 *   s'il n'y en a aucun, une lecture résout vide SANS toucher le provider (un provider que
 *   [ukitDropSnapshot] a accepté en porte toujours un ; la route totale reste honnête).
 *
 * La preuve de COMPORTEMENT asynchrone (le feu réel de la completion sous coroutine, la livraison
 * hors thread principal) vit dans les tests driver de la Task 4 : depuis pur Kotlin, la completion
 * n'est pas pilotable deterministiquement (spike Task 1).
 */
internal fun ukitPayloadFromProvider(
    provider: NSItemProvider,
    preferredType: String?,
    dispatch: UkitDropDispatch = { block -> dispatch_async(dispatch_get_main_queue(), block) },
): UIKitDropPayload {
    val type = preferredType
        ?: provider.registeredTypeIdentifiers.filterIsInstance<String>().firstOrNull()
        ?: return UIKitDropPayload(totalBytes = null) { _, _ -> null }
    var cache: ByteArray? = null
    return UIKitDropPayload(totalBytes = null) { offset, count ->
        val bytes = cache ?: loadProviderBytes(provider, type, dispatch).also { cache = it }
        when {
            offset >= bytes.size.toLong() -> null
            else -> bytes.copyOfRange(offset.toInt(), min(bytes.size.toLong(), offset + count).toInt())
        }
    }
}

/** Livre les octets d'UN chargement du provider, par la completion suspendée. */
private suspend fun loadProviderBytes(
    provider: NSItemProvider,
    type: String,
    dispatch: UkitDropDispatch,
): ByteArray {
    // Le paramètre `item` de la completion arrive du klib en conformer de protocole
    // (`id<NSSecureCoding>`) — la lecture réélle est ramenée en `Any?` AVANT le `when`, sinon les
    // checks `is String`/`is NSData` seraient jugés impossibles à la compilation (la vérité du
    // runtime est le pontage K/N : un texte livré EST un String).
    val delivered: Any? = suspendCancellableCoroutine<Any?> { continuation ->
        dispatch {
            provider.loadItemForTypeIdentifier(
                typeIdentifier = type,
                options = null,
                completionHandler = { item, _ -> continuation.resume(item) },
            )
        }
    }
    return when (delivered) {
        is String -> delivered.encodeToByteArray()
        is NSData -> delivered.toOwnedByteArray()
        else -> throw IllegalStateException("uikit drop: the provider delivered no readable item for $type")
    }
}

/** Copie les octets d'un `NSData` livré hors du handle natif — le handle reste dans la closure. */
private fun NSData.toOwnedByteArray(): ByteArray {
    val byteCount = length
    when {
        byteCount == 0uL -> return ByteArray(0)
        byteCount > Int.MAX_VALUE.toULong() -> throw IllegalStateException(
            "uikit drop: the provider delivered $byteCount bytes, above the readable ceiling",
        )
    }
    val pointer = bytes ?: throw IllegalStateException("uikit drop: the provider delivered bytes it does not expose")
    return pointer.readBytes(byteCount.toInt())
}
