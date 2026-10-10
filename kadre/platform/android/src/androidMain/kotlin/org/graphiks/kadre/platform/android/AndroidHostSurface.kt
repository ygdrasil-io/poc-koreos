package org.graphiks.kadre.platform.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreDiagnostic
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputStateRevision
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.KeyboardState
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.input.SurfaceInput
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.internal.runtime.RawInputPort
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurfaceConfiguration
import org.graphiks.kadre.internal.runtime.admitField
import org.graphiks.kadre.internal.runtime.fieldName
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceEvent
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome

/**
 * La surface Android observable — le miroir plateforme de la surface interne web (la classe de
 * `WebHostSession.kt` qui implémente `RuntimePrimarySurfaceConfiguration`), l'homologue observable
 * du `RuntimeHostSurface` minimal de la phase 0. Implémente [HostSurface] intégralement :
 * `state`/`capabilities`/`events`, un `apply` conforme à sa ligne §3 — aucun champ supporté en
 * phase 2, la ligne n'admets donc jamais un champ et ne sort jamais une failure externe pour
 * l'update entier — et un `requestRedraw` dont Task 3 branchera le scheduling à la place de la
 * branche on-row. Task 4 ajoutera `: AndroidViewLeasePort` à cette déclaration (port défini en
 * Task 4). La View n'est jamais exposée ici.
 *
 * La source des stamps est celle de la session : comme la surface web, cette surface reçoit la
 * configuration de session par [RuntimePrimarySurfaceConfiguration.installSessionConfiguration],
 * que le runtime appelle pendant `controller.attach` — après la factory de surface (qui ne
 * construit que l'identité allouée par le runtime), avant que le code applicatif puisse jamais
 * tenir la session. Aucune source de stamp n'est donc captée à la construction : un `EventStamp`
 * inventé ici serait une seconde séquence concurrente de celle de la session, et les événements
 * ne peuvent pas précéder l'attach. Task 2 consomme le holder pour ses événements typés.
 *
 * Le contrat de thread est celui de la couture : l'attach et les publications du binding
 * (lifecycle) vivent sur le thread principal ; `apply` est suspendante et garde toute décision
 * sous [RuntimeLock], la même discipline que la surface interne de la phase 0.
 */
internal class AndroidHostSurface(
    override val id: SurfaceId,
    initialState: SurfaceState,
) : HostSurface,
    RuntimePrimarySurfaceConfiguration,
    AutoCloseable {

    private val lock = RuntimeLock()

    /**
     * Le terminal unique, posé une fois par [close] — jamais déduit de l'état publié : le
     * binding publie l'instantané détaché canonique AVANT le terminal, et le terminal doit
     * quand même courir sa séquence (capabilities, fin du flux d'événements) une fois.
     */
    private var detached: Boolean = false

    private val mutableState = MutableStateFlow(initialState)
    private val mutableCapabilities = MutableStateFlow(unsupportedSurfaceCapabilities())

    /**
     * Le flux borné des observations : au plus 64 événements en attente de collecte, les plus
     * anciens abandonnés au débordement — un observateur lent ne peut ni bloquer la surface ni
     * lui faire perdre la mémoire (le miroir de la surface web, à sa capacité près). Task 2
     * émet ici ; en Task 1 le flux reste vide jusqu'au terminal.
     */
    private val mutableEvents = MutableSharedFlow<SurfaceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Le terminal du flux d'événements — complété par [close], le miroir de la surface web. */
    private val terminal = CompletableDeferred<Unit>()

    /** Le stamp de session, installé pendant l'attach (cf. la KDoc de la classe). */
    private var stampSource: (() -> EventStamp)? = null

    override val state: StateFlow<SurfaceState> = mutableState.asStateFlow()
    override val capabilities: StateFlow<SurfaceCapabilities> = mutableCapabilities.asStateFlow()

    /**
     * Le flux d'observations de cette surface, qui se termine au terminal.
     *
     * Un shared flow ne porte pas de complétion, donc la diffusion est jumelée à [terminal] :
     * un collecteur reçoit les observations publiées jusqu'à la fermeture, et un collecteur qui
     * arrive après voit un flux déjà terminé — la façon dont une surface fermée répond `Closed`
     * à chaque appel.
     */
    override val events: Flow<SurfaceEvent> = channelFlow {
        val forwarding = launch { mutableEvents.collect { send(it) } }
        terminal.await()
        forwarding.cancel()
    }

    override val input: SurfaceInput = UnsupportedAndroidSurfaceInput

    /**
     * L'admission d'un [SurfaceUpdate], dans l'ordre de la référence (la surface web, elle-même
     * alignée sur la surface de référence et le ruling P1 des commandes de fenêtres) :
     *
     * 1. la porte d'admission — une surface terminale répond `Closed(Surface)`, partout pareil ;
     * 2. la garde de révision — un `expectedRevision` qui n'est pas le courant est un
     *    `StaleRevision(expected, current)`, AVANT toute admission de champ, de sorte qu'une
     *    demande à la fois périmée et mal formée soit répondue périmée ;
     * 3. la garde `Clear` — un `Clear` sur un champ sans valeur « non posée » est un
     *    `InvalidRequest(field)` (les quatre noms du registre §1.1), lui aussi avant admission ;
     * 4. l'admission CHAMP PAR CHAMP contre la capability publiée — l'admission partagée du
     *    runtime ([admitField], celle-là même de la surface web et de la surface de référence).
     *    En phase 2, les quatre capabilities sont `Unsupported(UpdateSurface)` : un champ posé
     *    est rejeté `RejectedSurfaceField(Unsupported(UpdateSurface))`, un champ non posé ne
     *    l'est jamais ;
     * 5. le verdict — la surface n'a aucun chemin de commit en phase 2 : l'état ne bouge pas, la
     *    révision non plus. Rien de posé → `Applied(current)` ; un champ posé →
     *    `PartiallyApplied(current, rejected)`. Jamais une failure externe `Unsupported` pour
     *    l'update entier : le rejet est PAR CHAMP, dans la liste du succès.
     */
    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> {
        admissionFailure()?.let { return it }
        update.expectedRevision?.let { expected ->
            val currentRevision = mutableState.value.revision
            if (expected != currentRevision) {
                return KadreResult.Failure(
                    KadreFailure.StaleRevision(expected.value, currentRevision.value),
                )
            }
        }
        invalidClearField(update)?.let { field ->
            return KadreResult.Failure(KadreFailure.InvalidRequest(field))
        }
        val rejected = mutableListOf<RejectedSurfaceField>()
        val capabilities = mutableCapabilities.value
        // Chaque champ est admis, y compris ceux qui ne peuvent pas être honorés : un champ que
        // la surface ne peut pas honorer est RAPPORTÉ, jamais silencieusement abandonné — et
        // l'admission est ce qui enregistre le `RejectedSurfaceField` dans la liste, dans l'ordre
        // des champs de l'update (une seule passe ici : aucun second refus au commit). Le champ
        // ADMIS est ce que l'admission rend — c'est lui que la garde de commit relit.
        val cursor = admitField(update.cursor, SurfaceProperty.Cursor, capabilities.cursor, rejected)
        val pointerCapture = admitField(
            update.pointerCapture,
            SurfaceProperty.PointerCapture,
            capabilities.pointerCapture,
            rejected,
        )
        val hitTesting = admitField(
            update.hitTesting,
            SurfaceProperty.HitTesting,
            capabilities.hitTesting,
            rejected,
        )
        val inputDefaultBehavior = admitField(
            update.inputDefaultBehavior,
            SurfaceProperty.InputDefaultBehavior,
            capabilities.inputDefaultBehavior,
            rejected,
        )
        // Les quatre champs n'ont aucun chemin de commit en phase 2 : la garde assure qu'un champ
        // arrivé admis (une capability qui deviendrait `Supported` sans commit ici) est une erreur
        // de programmation criante, pas un `Applied` pour un état qui ne l'a jamais pris.
        cursor.requireRefused(SurfaceProperty.Cursor)
        pointerCapture.requireRefused(SurfaceProperty.PointerCapture)
        hitTesting.requireRefused(SurfaceProperty.HitTesting)
        inputDefaultBehavior.requireRefused(SurfaceProperty.InputDefaultBehavior)
        // Aucun commit : l'état rendu est l'instantané courant, révision intacte — demander la
        // valeur déjà en effet serait admis et répondue `Applied` de la même façon.
        val current = mutableState.value
        return KadreResult.Success(
            if (rejected.isEmpty()) {
                SurfaceUpdateOutcome.Applied(current)
            } else {
                SurfaceUpdateOutcome.PartiallyApplied(current, rejected)
            },
        )
    }

    /**
     * La demande de redessin, branche on-row de la phase 2 : la porte d'admission, puis
     * `Unsupported(RequestRedraw)`. Task 3 remplace ce corps par le scheduler (le test normatif
     * admet explicitement les deux formes `Success`/`Unsupported` pour traverser le swap).
     */
    override fun requestRedraw(): KadreResult<Unit> {
        admissionFailure()?.let { return it }
        return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RequestRedraw))
    }

    /**
     * Installe la configuration de session que cette surface publie.
     *
     * La source des stamps est la seule valeur dont Task 1 a besoin — les neuf autres paramètres
     * sont ceux des Tasks 2-3 (les politiques du réducteur d'entrée, les gates de collecteurs, le
     * scope des transferts de drop). La surface web garde les dix verbatim dans sa configuration ;
     * ici, le holder de stamps est tout ce qui existe encore, posé pendant l'attach, avant que le
     * code applicatif puisse tenir la session — aucun événement ne peut précéder l'installation.
     *
     * Une surface qui a cessé d'admettre abandonne la configuration avec les stimuli qui
     * l'attendaient (le miroir de la surface web) ; une seconde installation est une erreur de
     * programmation — une configuration construit une séquence de stamps, deux n'en peuvent pas
     * partager une.
     */
    override fun installSessionConfiguration(
        deliveryPolicy: WindowDeliveryPolicy,
        inputDeliveryPolicy: InputDeliveryPolicy,
        source: () -> EventStamp,
        sessionFailureHandler: (KadreFailure) -> Unit,
        collectorAllocator: Any,
        maxCollectorsPerFlow: Int,
        resources: ResourceBudgetPolicy,
        dropTransferScope: CoroutineScope?,
        diagnostics: (KadreDiagnostic) -> Unit,
        rawInputPort: RawInputPort?,
    ) {
        if (detached) return
        check(stampSource == null) { "the session configuration was already installed" }
        stampSource = source
    }

    /**
     * Le terminal unique de cette surface — le miroir de la transition web, atteint par la
     * teardown du runtime ([org.graphiks.kadre.internal.runtime.RuntimePrimarySurface] →
     * `surface::close`) et idempotent :
     *
     * 1. le drapeau terminal est posé sous le verrou — toute admission suivante répond `Closed` ;
     * 2. les capabilities deviennent indisponibles AVANT la publication de l'état détaché (« les
     *    capabilities indisponibles au plus tard au détached » — un consommateur ne peut jamais
     *    lire une promesse sur un état déjà détaché) ;
     * 3. l'état détaché est publié — sauf s'il l'est déjà : le binding publie l'instantané
     *    détaché canonique AVANT le terminal, et la révision est le marqueur d'un changement
     *    d'état, pas d'un appel (la règle de la référence, celle de la phase 0) ;
     * 4. le flux d'événements se termine — les collecteurs en cours reçoivent la fin, les
     *    collecteurs suivants voient un flux déjà terminé.
     */
    override fun close() {
        lock.withLock {
            if (detached) return
            detached = true
            // Les capabilities d'abord : indisponibles au plus tard au détached, ici avant.
            mutableCapabilities.value = unsupportedSurfaceCapabilities()
            val current = mutableState.value
            if (current.attachment != SurfaceAttachmentState.Detached) {
                check(current.revision.value < Long.MAX_VALUE) { "surface revision space exhausted" }
                mutableState.value = current.copy(
                    attachment = SurfaceAttachmentState.Detached,
                    revision = SurfaceRevision(current.revision.value + 1L),
                )
            }
        }
        terminal.complete(Unit)
    }

    /**
     * Publie une observation du host dans l'état de la surface — la même publication que la
     * surface interne de la phase 0, tenue courante : la révision ne bouge que pour un état qui
     * bouge réellement, et une publication qui suit la fermeture (ou l'état détaché déjà publié
     * par le binding) est absorbée exactement comme le fait `close`.
     *
     * En Task 1 la publication est muette : elle est une observation d'état, pas un stimulus.
     * Task 2 choisit l'événement typé correspondant et l'émet sur [events] avec le stamp de
     * session ([stampSource]).
     */
    internal fun publishMetrics(update: (SurfaceState) -> SurfaceState) {
        lock.withLock {
            val current = mutableState.value
            if (detached || current.attachment == SurfaceAttachmentState.Detached) return
            val next = update(current)
            if (next == current) return
            check(current.revision.value < Long.MAX_VALUE) { "surface revision space exhausted" }
            mutableState.value = next.copy(revision = SurfaceRevision(current.revision.value + 1L))
        }
    }

    /**
     * La porte d'admission unique de cette surface : `null` tant qu'elle admet, son refus
     * sinon. [apply] et [requestRedraw] l'ouvrent, de sorte qu'une surface terminale — ou dont
     * l'état publié est déjà détaché (la sémantique d'admission de la phase 0) — répond le même
     * `Closed(Surface)` partout au lieu de diverger par site.
     */
    private fun admissionFailure(): KadreResult.Failure? =
        if (detached || mutableState.value.attachment == SurfaceAttachmentState.Detached) {
            KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
        } else {
            null
        }

    /** Le champ d'un `Clear`, ou `null` — la garde de la surface web, avant toute admission. */
    private fun invalidClearField(update: SurfaceUpdate): String? = when {
        update.cursor is PropertyChange.Clear -> SurfaceProperty.Cursor.fieldName
        update.pointerCapture is PropertyChange.Clear -> SurfaceProperty.PointerCapture.fieldName
        update.hitTesting is PropertyChange.Clear -> SurfaceProperty.HitTesting.fieldName
        update.inputDefaultBehavior is PropertyChange.Clear -> {
            SurfaceProperty.InputDefaultBehavior.fieldName
        }

        else -> null
    }
}

/** Affirme qu'un champ sans chemin de commit a bien été refusé par sa capability. */
private fun PropertyChange<*>.requireRefused(property: SurfaceProperty) {
    check(this is PropertyChange.Unchanged) {
        "${property.fieldName} arrived admitted, but the android surface has no commit for it"
    }
}

/**
 * L'entrée d'une surface sans entrée — la copie de l'objet privé de la surface interne de la
 * phase 0 (le même état honnête, les mêmes refus), car celui du runtime est privé à son fichier.
 */
private object UnsupportedAndroidSurfaceInput : SurfaceInput {
    private val unsupportedTextInput = KadreFailure.Unsupported(KadreOperation.TextInput)
    private val mutableState = MutableStateFlow(
        SurfaceInputState(
            keyboard = KeyboardState(emptySet()),
            pointers = emptyList(),
            touches = emptyList(),
            modifiers = KeyboardModifiers(emptySet()),
            capabilities = InputCapabilities(
                keyboard = FeatureAvailability.Unsupported,
                pointer = FeatureAvailability.Unsupported,
                touch = FeatureAvailability.Unsupported,
                gestures = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GestureInput)),
                dragAndDrop = FeatureAvailability.Unsupported,
                textInput = Capability.Unsupported(unsupportedTextInput),
                rawInput = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            ),
            revision = InputStateRevision(0L),
        ),
    )

    override val events: Flow<org.graphiks.kadre.input.InputEvent> = emptyFlow()

    override val state: StateFlow<SurfaceInputState> = mutableState.asStateFlow()

    override suspend fun openTextInput(config: TextInputConfig): KadreResult<TextInputSession> =
        KadreResult.Failure(unsupportedTextInput)

    @OptIn(DelicateKadreApi::class)
    override suspend fun requestRawInput(): KadreResult<RawInputAccess> =
        KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.RawInputAccess))
}
