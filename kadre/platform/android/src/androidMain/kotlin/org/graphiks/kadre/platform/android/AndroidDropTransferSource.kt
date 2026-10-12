package org.graphiks.kadre.platform.android

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropItemReadMode
import org.graphiks.kadre.internal.runtime.DropItemSource
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock

/**
 * Le côté CONTENU du drag Android (phase 6) : la traduction du snapshot copié d'un clip en
 * offres communes, et les lectures bornées derrière elle. Aucun type SDK n'est retenu — ni
 * `DragEvent`, ni `ClipData`, ni `ContentResolver` public : la glue cible (Task 2) lit le clip
 * DANS sa callback et construit ici des [AndroidClipItem] de valeurs copiées, et la production
 * passe un [AndroidDropStreamOpener] autour du `ContentResolver` quand les tests passent un
 * fake — le miroir exact du partage web (`WebDropStimulus.kt`, plan D-D1).
 *
 * Les règles de traduction sont le miroir de `WebDropStimulus.kt:67-99` : un mime est gardé
 * canonisé en minuscules ou droppé (jamais normalisé au-delà, jamais fabriqué), un item dont
 * TOUS les mimes sont droppés est droppé entier, un item sans contenu (ni uri ni texte) est
 * droppé entier — aucune offre partielle (DESIGN §10.4). Un item uri-backed est
 * [DropItemKind.Uri] ([DropItemReadMode.SingleUse] — SON stream ne rejoue pas), un texte pur
 * sans uri est [DropItemKind.Text] en [DropItemReadMode.Replayable] (le texte d'un clip est
 * déjà en mémoire — il rejoue sans l'opener), et uri+texte ensemble donne l'uri pour le
 * contenu avec le texte devenant l'indice d'affichage quand aucun indice explicite n'existe.
 * Les tailles sont TOUJOURS inconnues (`sizeBytes = null`, le pattern P2/P3) : un stream
 * ContentResolver ne promet rien avant d'être lu, et une lecture taille-inconnue peut livrer
 * un préfixe avant d'échouer — le runtime porte alors son `Limit(DropItem)`.
 */

/** Le domaine des failures plateforme de cette couture — le miroir du `"drop"` web. */
private const val ANDROID_DROP_DOMAIN: String = "drop"

/** Le code du refus de l'opener : la permission derrière l'URI est absente. */
private const val ANDROID_DROP_REFUSED_CODE: String = "resolver-refused"

/** Le code d'une lecture du stream échouée pendant que le owner vivait. */
private const val ANDROID_DROP_READ_CODE: String = "byte-read"

/**
 * Snapshot copié d'un clip Android — aucun type SDK retenu (roadmap :216).
 *
 * La glue construit ces valeurs DANS sa callback de drag, depuis les faits que le `ClipData`
 * livrait à cet instant ; la source ne voit jamais l'original.
 */
internal data class AndroidClipSnapshot(
    val items: List<AndroidClipItem>,
)

/**
 * Un item du clip, en valeurs copiées : l'URI du contenu (`ClipData.Item.uri`, sinon `null`),
 * le texte pur (`ClipData.Item.text`, sinon `null`), les mimes du `ClipDescription` dans
 * l'ordre du SDK, et l'indice d'affichage que la glue a pu lire. Une item d'intent seule
 * arrive `uri = null, text = null` et est droppée par la traduction — kadre n'invente rien
 * de lisible derrière une intention.
 */
internal data class AndroidClipItem(
    val uri: String?,
    val text: String?,
    val mimeTypes: List<String>,
    val displayNameHint: String?,
)

/**
 * Resolver injecté — la production passe `ContentResolver`, les tests un fake.
 *
 * Le contrat est exactement celui du resolver réel assoupli : `open` rend le stream du
 * contenu, ou `null` quand le resolver refuse (permission absente), ou lance — les trois
 * formes sont LE MÊME refus pour l'item (le vrai `ContentResolver.openInputStream` lance
 * `SecurityException`/`FileNotFoundException` plutôt que de rendre `null` ; la glue de
 * production peut choisir l'une ou l'autre forme, l'item les honnêtifie de la même façon).
 */
internal fun interface AndroidDropStreamOpener {
    /** Ouvre le contenu de l'URI ; `null` si le resolver refuse (permission absente). */
    public fun open(uri: String): InputStream?
}

/**
 * La construction de PRODUCTION de l'opener — le `ContentResolver` réel de l'app : chaque
 * lecture opener-backed passe par `openInputStream` sur l'uri du clip, la seule voie honnête
 * du contenu droppé (les grants vivent dans le resolver, jamais dans kadre).
 *
 * Le resolver refuse par l'exception — `FileNotFoundException` d'un uri absent,
 * `SecurityException` d'un grant absent — jamais par `null` : les deux formes du contrat de
 * l'[AndroidDropStreamOpener] (`null`, exception) sont le MÊME refus pour l'item, et la
 * production choisit la forme du SDK, que l'item honnêtifie en `resolver-refused` fermé.
 */
internal fun androidDropResolverOpener(resolver: ContentResolver): AndroidDropStreamOpener =
    AndroidDropStreamOpener { uri -> resolver.openInputStream(Uri.parse(uri)) }

/**
 * Whether [value] is a media type the foundation's own rule accepts as canonical.
 *
 * La règle n'est pas restatée ici. Le seul propriétaire de « type de média canonique » est
 * l'exigence du constructeur [DropItemDescriptor] (`TextDropRaw.kt`), alors ceci la sonde :
 * un descripteur de sonde porte la valeur ou la refuse — le miroir exact de
 * `isCanonicalWebDropMediaType` (`WebDropStimulus.kt:67`). Un mime que la sonde refuse ne
 * peut jamais être fabriqué en descripteur par la glue qui consomme.
 */
internal fun isCanonicalAndroidDropMediaType(value: String): Boolean = runCatching {
    DropItemDescriptor(displayName = null, sizeBytes = null, mimeTypes = listOf(value), kind = DropItemKind.Binary)
}.isSuccess

/**
 * The mime of one clip item as a canonical media type, or `null` when the clip named none the
 * model can carry.
 *
 * Android ne promet pas la casse : le système écrit en minuscules, mais un clip d'application
 * tierce peut nommer `"TEXT/PLAIN"`. La canonisation de CETTE plateforme est donc la mise en
 * minuscules, puis la sonde ci-dessus — ce qui reste non canonique (vide, espace, pas de
 * `type/sub`) est droppé, jamais normalisé davantage et jamais fabriqué.
 */
internal fun canonicalAndroidDropMimeTypeOrNull(value: String): String? =
    value.lowercase().takeIf(::isCanonicalAndroidDropMediaType)

/**
 * The kind of one clip item, from what it actually carries: an uri wins the content —
 * [DropItemKind.Uri], whatever the uri names — a pure text is [DropItemKind.Text], and an
 * item that carries neither has no kind at all (`null` : l'item est droppé entier).
 */
internal fun androidDropItemKind(uri: String?, text: String?): DropItemKind? = when {
    uri != null -> DropItemKind.Uri
    text != null -> DropItemKind.Text
    else -> null
}

/**
 * The snapshot descriptor of one clip item, or `null` when the item is dropped entirely.
 *
 * Le kind vient de [androidDropItemKind], la liste de mimes est canonisée par
 * [canonicalAndroidDropMimeTypeOrNull] et dédupliquée à la première occurrence (le
 * descripteur refuse les doublons — `TextDropRaw.kt:107`), un item dont TOUS les mimes sont
 * droppés est droppé entier, et la taille reste TOUJOURS inconnue (`null`) : le stream n'a
 * rien promis avant d'être lu. L'indice d'affichage est l'indice explicite, ou — pour un item
 * uri+texte — le texte du clip devenu indice, rien d'inventé.
 */
internal fun androidDropItemDescriptor(item: AndroidClipItem): DropItemDescriptor? {
    val kind = androidDropItemKind(item.uri, item.text) ?: return null
    val mimeTypes = item.mimeTypes.mapNotNull(::canonicalAndroidDropMimeTypeOrNull).distinct()
    if (mimeTypes.isEmpty()) return null
    val displayName = item.displayNameHint ?: item.text.takeIf { item.uri != null }
    return DropItemDescriptor(
        displayName = displayName,
        sizeBytes = null,
        mimeTypes = mimeTypes,
        kind = kind,
    )
}

/**
 * One dropped item as the common pipeline sees it — the item source behind an
 * [AndroidDropTransferSource].
 *
 * Le contenu est exactement l'un des deux : les octets d'un texte snapshoté
 * ([DropItemReadMode.Replayable] — en mémoire, rejouables sans l'opener, le miroir de la
 * règle « un texte de clip est déjà en mémoire »), ou l'uri d'un contenu
 * ([DropItemReadMode.SingleUse] — SON stream s'ouvre paresseusement à la PREMIÈRE
 * `collectBytes`, via l'opener injecté, et ne se rouvre jamais : un stream consommé ne
 * rejoue pas, et le runtime refuse la seconde lecture d'un `SingleUse` avant même d'arriver
 * ici).
 *
 * La durée de vie du stream est la promesse sentinelle de la phase : il se ferme UNE fois —
 * à la fin naturelle de la lecture, à la sortie sans succès de celle-ci (le collector qui
 * jette — dont les marqueurs `Limit`/`Closed` du runtime — ou une `IOException`), ou au
 * `close` du owner qui le surprend encore ouvert — jamais deux fois, jamais conservé après
 * le close du owner. La garde est un drapeau sous [RuntimeLock] : celui des deux chemins qui
 * arrive le premier ferme, l'autre constate. Les E/S restent HORS du verrou (un
 * `openInputStream` de production peut bloquer sur binder : le `close` du owner ne l'attend
 * jamais) et le collector est appelé hors du verrou : les marqueurs du runtime qui jettent
 * DANS le collector traversent intacts — c'est ainsi que `maxBytes` du runtime transforme une
 * lecture taille-inconnue en préfixe puis `Limit(DropItem)` (DESIGN §10.4).
 *
 * Un refus de l'opener (`null` ou exception) est la failure plateforme fermée de cette
 * couture — `resolver-refused`, l'analogue honnête du `payload-unavailable` web
 * (`WebDropStimulus.kt:158`) — avec ZÉRO octet livré : une permission absente ne devient pas
 * un faux drop lisible. `Platform` plutôt que `Temporary` parce que le refus est persistant
 * (le grant d'un drop ne revient pas en réessayant — `Temporary` promettrait une
 * disponibilité qui n'existe pas) et parce que `Platform` est admis par CONTRACTS §7 pour
 * `collectBytes`. Deux `collectBytes` sur le même item : le second est refusé
 * `AlreadyInUse(DropTransfer)` à la garde — le contrat du runtime (`RuntimeDropTransfer.kt:303`)
 * — sans jamais attendre la première.
 */
internal class AndroidDropItem(
    override val descriptor: DropItemDescriptor,
    /** L'uri du contenu opener-backed, ou `null` pour un item en mémoire. */
    private val uri: String?,
    /** Les octets snapshotés d'un texte pur, ou `null` pour un item uri-backed. */
    private val textBytes: ByteArray?,
    private val opener: AndroidDropStreamOpener,
) : DropItemSource {

    init {
        check((uri == null) != (textBytes == null)) { "an android drop item carries exactly one content" }
    }

    override val readMode: DropItemReadMode =
        if (textBytes != null) DropItemReadMode.Replayable else DropItemReadMode.SingleUse

    private val lock = RuntimeLock()

    /** La lecture en cours — la garde du `AlreadyInUse(DropTransfer)`. */
    private var reading: Boolean = false

    /** Le owner a fermé la source : aucun contenu n'est plus lisible. */
    private var closedByOwner: Boolean = false

    /** Le contenu opener-backed est épuisé : son stream s'est fermé (fin, échec ou owner). */
    private var exhausted: Boolean = false

    /** Le stream ouvert paresseusement — `null` avant la première lecture. */
    private var stream: InputStream? = null

    /** La garde du close-exactly-once du stream. */
    private var streamClosed: Boolean = false

    override suspend fun collectBytes(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        if (maxChunkBytes <= 0) return KadreResult.Failure(KadreFailure.InvalidRequest("maxChunkBytes"))
        lock.withLock {
            when {
                closedByOwner || exhausted ->
                    return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem))

                reading ->
                    return KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.DropTransfer))

                else -> reading = true
            }
        }
        try {
            val payload = textBytes
            return when {
                payload != null -> readInMemory(payload, maxChunkBytes, collector)
                else -> readUri(maxChunkBytes, collector)
            }
        } finally {
            lock.withLock { reading = false }
        }
    }

    /**
     * Le close du owner (via [AndroidDropTransferSource.close]) : le contenu devient
     * illisible et tout stream encore ouvert — par une lecture suspendue ou jamais finie —
     * est fermé maintenant, exactement une fois.
     */
    internal fun ownerClosed() {
        lock.withLock { closedByOwner = true }
        closeStreamOnce()
    }

    /** Le rejeu d'un texte snapshoté : des tranches d'au plus [maxChunkBytes], sans état. */
    private suspend fun readInMemory(
        payload: ByteArray,
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        var offset = 0
        while (offset < payload.size) {
            val end = min(offset + maxChunkBytes, payload.size)
            collector(payload.copyOfRange(offset, end))
            offset = end
        }
        return KadreResult.Success(Unit)
    }

    /** La lecture opener-backed : ouverture paresseuse, tranches bornées, close exactement une fois. */
    private suspend fun readUri(
        maxChunkBytes: Int,
        collector: suspend (ByteArray) -> Unit,
    ): KadreResult<Unit> {
        // Un stream n'est JAMAIS trouvé déjà ouvert ici : l'admission refuse toute lecture
        // concurrente, et toute sortie de lecture referme le stream et épuise l'item.
        val stream = openStream() ?: return run {
            when {
                // Le owner a fermé PENDANT l'ouverture : `Closed(DropItem)`, pas un refus de
                // resolver — la cause honnête porte.
                lock.withLock { closedByOwner } -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.DropItem))
                else -> KadreResult.Failure(
                    KadreFailure.PlatformFailure(KadrePlatform.Android, ANDROID_DROP_DOMAIN, ANDROID_DROP_REFUSED_CODE),
                )
            }
        }
        val buffer = ByteArray(maxChunkBytes)
        try {
            while (true) {
                val count = try {
                    stream.read(buffer, 0, maxChunkBytes)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (closed: IOException) {
                    // Une IOException pendant que le owner a fermé EST le close du owner —
                    // `Closed(DropItem)` ; sinon c'est la lecture plateforme qui a échoué.
                    val failure = lock.withLock { closedByOwner }
                    return KadreResult.Failure(
                        when {
                            failure -> KadreFailure.Closed(KadreResourceKind.DropItem)
                            else -> KadreFailure.PlatformFailure(
                                KadrePlatform.Android,
                                ANDROID_DROP_DOMAIN,
                                ANDROID_DROP_READ_CODE,
                            )
                        },
                    )
                }
                if (count <= 0) break
                // Le collector est appelé HORS du try d'E/S : les marqueurs du runtime qui
                // jettent dedans (`Limit(DropItem)`, `Closed(DropTransfer)`) traversent
                // intacts — le préfixe reste livré, le finally ci-dessous referme le stream.
                collector(buffer.copyOf(count))
            }
            return KadreResult.Success(Unit)
        } finally {
            closeStreamOnce()
            lock.withLock { exhausted = true }
        }
    }

    /**
     * Ouvre le stream par l'opener — HORS de tout verrou (un resolver de production bloque
     * sur binder), puis l'admet sous le verrou ; un stream que le owner a fermé pendant
     * l'ouverture est refermé immédiatement et la lecture échoue `Closed(DropItem)`. Un refus
     * (`null`) ou une exception de l'opener sont le même refus de permission —
     * `null` en retour signifie « pas de stream », l'appelant le traduit.
     */
    private fun openStream(): InputStream? {
        val opened: InputStream = try {
            opener.open(checkNotNull(uri)) ?: return null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (refused: Exception) {
            return null
        }
        val admitted = lock.withLock {
            when {
                closedByOwner || streamClosed -> false
                else -> {
                    stream = opened
                    true
                }
            }
        }
        if (!admitted) {
            runCatching { opened.close() }
            return null
        }
        return opened
    }

    /** Le close-exactly-once du stream : celui des deux chemins qui arrive le premier ferme. */
    private fun closeStreamOnce() {
        val toClose = lock.withLock {
            when {
                streamClosed -> null
                else -> {
                    streamClosed = true
                    stream
                }
            }
        }
        // Hors du verrou : `close()` d'un stream réel peut bloquer — et ne jette jamais
        // vers le owner (le close du source n'a pas de failure).
        toClose?.let { runCatching { it.close() } }
    }
}

/**
 * The source one Android drop presents to the common drop pipeline: the translated items of
 * the clip snapshot, and nothing else.
 *
 * Le runtime la retient pour l'offre qu'il a construite — à travers un accept et un drop
 * performed, ou jusqu'à la fin de l'offre — et la ferme sur tout chemin terminal qui n'a pas
 * remis un transfert. Le close est idempotent (le runtime n'appelle qu'une fois ; cette
 * réponse répond au double quand même), marque chaque item fermé et referme tout stream
 * encore ouvert — la sentinelle « stream conservé après close owner » ne trouve rien. Les
 * descripteurs restent, comme des snapshots le font. Le miroir exact de
 * `WebDropTransferSource` (`WebDropStimulus.kt:189`).
 */
internal class AndroidDropTransferSource(
    snapshot: AndroidClipSnapshot,
    private val opener: AndroidDropStreamOpener,
) : DropTransferSource {

    private val translated: List<AndroidDropItem> = snapshot.items.mapNotNull { item ->
        androidDropItemDescriptor(item)?.let { descriptor ->
            when (val uri = item.uri) {
                null -> AndroidDropItem(descriptor, uri = null, textBytes = checkNotNull(item.text).encodeToByteArray(), opener = opener)
                else -> AndroidDropItem(descriptor, uri = uri, textBytes = null, opener = opener)
            }
        }
    }

    override val items: List<DropItemSource> get() = translated

    private val lock = RuntimeLock()
    private var closed: Boolean = false

    override fun close() {
        val closeItems = lock.withLock {
            when {
                closed -> false
                else -> {
                    closed = true
                    true
                }
            }
        }
        if (closeItems) translated.forEach { item -> item.ownerClosed() }
    }
}
