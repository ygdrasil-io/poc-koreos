package org.graphiks.kadre.platform.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import org.graphiks.kadre.internal.runtime.RuntimeDropTransferBudget
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurfaceConfiguration
import org.graphiks.kadre.internal.runtime.RuntimeSurfaceInput
import org.graphiks.kadre.internal.runtime.admitField
import org.graphiks.kadre.internal.runtime.fieldName
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
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
 * branche on-row. Task 4 a ajouté `: AndroidViewLeasePort` à cette déclaration : la View du host
 * n'est exposée qu'au block du bail ([withAndroidView], double opt-in), jamais ailleurs.
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
    private val frameClock: () -> AndroidRedrawScheduler.FrameClock = { ChoreographerFrameClock() },
    private val hostThread: AndroidHostThreadDispatch = AndroidMainThreadDispatch,
    private val leasedView: () -> Any? = { null },
    /**
     * Le canal des diagnostics d'entrée qui ne sont pas des failures de session — les
     * débordements que le réducteur runtime reporte (`SourceOverflow` et cie), le miroir du
     * `failureReporter` que la surface web reçoit à sa construction. Le défaut de production
     * est le journal de la couture d'attach ; les tests injectent le leur.
     */
    private val failureReporter: RuntimeFailureReporter = AndroidAttachDiagnostics,
) : HostSurface,
    RuntimePrimarySurfaceConfiguration,
    AndroidViewLeasePort,
    AutoCloseable {

    private val lock = RuntimeLock()

    /**
     * Le scheduler de redraw de Task 3 — construit paresseusement : la factory de clock n'est
     * jamais invoquée à la construction (aucun appel SDK au constructeur — les host tests qui
     * injectent une clock manuelle ne touchent jamais le Choreographer, et une surface fermée
     * sans avoir jamais demandé de redraw non plus). Toutes ses entrées vivent sous [lock] :
     * [requestRedraw] y appelle `request`, [close] y appelle `cancel`, et le corps de frame
     * ([emitRedrawFrame]) le prend à son tour — le coalescing est donc ordonné par le même
     * verrou que les publications.
     */
    private val redrawScheduler by lazy {
        AndroidRedrawScheduler(frameClock(), ::emitRedrawFrame)
    }

    /**
     * Le terminal unique, posé une fois par [close] — jamais déduit de l'état publié : le
     * binding publie l'instantané détaché canonique AVANT le terminal, et le terminal doit
     * quand même courir sa séquence (capabilities, fin du flux d'événements) une fois.
     *
     * Volatile : écrit sous [lock] par [close] seul — [publishMetrics] ne fait que le lire, sous
     * le même verrou — mais lu hors verrou par [installSessionConfiguration] (l'attach, thread
     * principal) — la visibilité croisée thread est portée par le modificateur, pas par la
     * chance.
     */
    @Volatile
    private var detached: Boolean = false

    /**
     * Le bail unique en vol, sous [lock] : vrai entre l'admission d'un lease ([lease]) et la fin
     * du block qu'il a admis — retour, exception ou cancellation. Un second bail pendant ce
     * créneau répond `TemporarilyUnavailable(retryable = true)` sans invoquer son block.
     */
    private var leaseHeld: Boolean = false

    /**
     * La source de la View du host, lue à CHAQUE bail (jamais capturée avec la surface) et
     * EFFACÉE par le terminal ([close], sous le verrou — le carry M-3 de la Task 4) : un bail
     * admis avant `close` mais dont la lecture suit le teardown trouve une source vide et
     * répond `Closed(Surface)` — une View démontée n'est jamais remise au block. Volatile :
     * écrit sous [lock] par [close] seul, lu hors verrou par [lease] — la visibilité croisée
     * thread est portée par le modificateur, pas par la chance.
     */
    @Volatile
    private var viewSource: (() -> Any?)? = leasedView

    private val mutableState = MutableStateFlow(initialState)
    private val mutableCapabilities = MutableStateFlow(unsupportedSurfaceCapabilities())

    /**
     * Le flux borné des observations : au plus 64 événements en attente de collecte, les plus
     * anciens abandonnés au débordement — un observateur lent ne peut ni bloquer la surface ni
     * lui faire perdre la mémoire (le miroir de la surface web, à sa capacité près). Task 2
     * émet ici les événements typés des publications métriques ; Task 3 y émet
     * `RedrawRequested` à la frame.
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

    /**
     * Le réducteur d'entrée partagé, construit par [installSessionConfiguration] — jamais à la
     * construction : il est fait de la configuration de session (policy d'entrée, source de
     * stamps, gates de collecteurs), rien de ce que la surface pourrait inventer seule. Avant
     * l'install, [input] expose l'entrée pré-install de cette surface ([preInstallInput]) ;
     * après, le réducteur. Volatile : écrit une fois sur le thread principal de l'attach, lu
     * par les coroutines applicatives — la visibilité croisée thread est portée par le
     * modificateur, pas par la chance.
     */
    @Volatile
    private var surfaceInput: RuntimeSurfaceInput? = null

    /**
     * Le pont d'observation → delivery (Task 3), construit avec le réducteur par
     * [installSessionConfiguration] et possédé par cette surface : les funnels
     * [onInputObservation]/[onMotionObservation] y routent les observations du wrapper T1 et
     * du tracker T2, [observationInstalled] y déclenche l'installation (stimulus structurel
     * + replay du buffer). Volatile, même discipline que [surfaceInput].
     */
    @Volatile
    private var inputDelivery: AndroidInputDelivery? = null

    /**
     * L'entrée pré-install de CETTE surface — un instantané honnête par surface (toutes les
     * capabilities indisponibles, aucun événement, chaque appel refuse), jamais l'objet
     * partagé d'avant la Task 3 : l'install remplace l'instance entière, et le swap est
     * observable (le réducteur vit, l'entrée pré-install ne vit pas).
     */
    private val preInstallInput: SurfaceInput = PreInstallAndroidSurfaceInput()

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

    /**
     * L'entrée de cette surface : l'entrée pré-install jusqu'à [installSessionConfiguration],
     * le réducteur runtime ensuite — le swap honnête, observable par identité d'instance.
     */
    override val input: SurfaceInput get() = surfaceInput ?: preInstallInput

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
     *
     * Toute la décision tient sous [RuntimeLock] — le miroir de la surface de phase 0
     * (`RuntimeHostSurface.kt:48-62`) : la garde de révision relit l'état publié et le verdict
     * le rend, ils doivent voir le même instantané que [close]/[publishMetrics] écrivent. Le
     * corps est non suspendant, le verrou n'est donc jamais tenu à travers une suspension.
     */
    override suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome> = lock.withLock {
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
        KadreResult.Success(
            if (rejected.isEmpty()) {
                SurfaceUpdateOutcome.Applied(current)
            } else {
                SurfaceUpdateOutcome.PartiallyApplied(current, rejected)
            },
        )
    }

    /**
     * La demande de redessin coalescée (Task 3) : la porte d'admission sous [RuntimeLock],
     * puis la demande au scheduler — au plus une frame en attente, les demandes qui la
     * rejoignent sont admises (`Success(Unit)`), le coalescing décide du reste. Le callback de
     * frame ([emitRedrawFrame]) émettra UN `RedrawRequested` à la frame. `request` ne peut
     * répondre faux que si le scheduler a été annulé — c'est-à-dire au terminal, que la porte
     * d'admission vient déjà de filtrer : un faux ici est une erreur de programmation criante.
     */
    override fun requestRedraw(): KadreResult<Unit> = lock.withLock {
        admissionFailure()?.let { return it }
        check(redrawScheduler.request()) { "the redraw scheduler was cancelled while the surface admits" }
        KadreResult.Success(Unit)
    }

    /**
     * Le bail d'accès natif (Task 4) : prête la View du host pour la durée de [block], et pas
     * plus. Les portes, dans l'ordre du miroir web (`WebHostSession.lease`) :
     *
     * 1. l'admission d'abord — une surface terminale (ou dont l'état publié est déjà détaché)
     *    répond `Closed(Surface)` sans invoquer [block] ; elle gagne sur la garde de
     *    réentrance : un bail après close n'est jamais re-tentable, le décrire retryable serait
     *    un mensonge ;
     * 2. le bail unique — un bail commencé pendant qu'un autre est en vol répond
     *    `TemporarilyUnavailable(retryable = true)`, [block] jamais invoqué : se rentrer dedans
     *    est une erreur de programmation, pas une contre-pression ;
     * 3. la View est relue à CHAQUE bail (la source [leasedView], jamais capturée avec la
     *    surface, effacée par le terminal sous le verrou) — un host parti ne prête rien :
     *    `Closed(Surface)`, le miroir du port web ;
     * 4. le block tourne sur le thread host (le principal Android) : inline si l'appelant y est
     *    déjà, sinon dispatché ([hostThread]) — et l'invocation DÉMARRÉE est enveloppée de
     *    `NonCancellable` : un callback commencé s'achève même si la coroutine appelante est
     *    annulée pendant lui, et la cancellation se manifeste chez l'appelant après (le
     *    transfert retourne une `CancellationException` ; le chemin inline, sans point de
     *    suspension entre l'admission et la première instruction du block, laisse l'appelant
     *    annulé PENDANT le block l'observer au prochain point de suspension — un appelant
     *    annulé AVANT l'admission n'invoque rien : `ensureActive` rejette sous le verrou,
     *    avant la pose du bail).
     *
     * L'exception applicative du block se propage TELLE QUELLE — jamais enveloppée en
     * `KadreFailure` (la taxonomie n'admets pas ce mensonge) — et le `finally` rend le bail sur
     * tous les chemins : un bail suivant réussit après un block lançant comme après un block
     * annulé. La View n'est retenue par personne après le retour du block.
     */
    override suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R> {
        lock.withLock {
            admissionFailure()?.let { return it }
            if (leaseHeld) return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
            // La cancellation précoce — la roadmap : « aucun callback après fermeture ou
            // cancellation précoce ». Le moniteur ne lit pas la cancellation : un appelant
            // déjà annulé doit être rejeté ICI, sous le verrou, avant la pose du bail — le
            // chemin inline n'a ensuite AUCUN point de suspension avant la première
            // instruction du block, et l'invocation démarrée, elle, s'achève (NonCancellable).
            // Un rejet ici sort avant `leaseHeld = true` : le `finally` n'a rien à libérer.
            currentCoroutineContext().ensureActive()
            leaseHeld = true
        }
        try {
            // La source est relue ICI, hors verrou mais APRÈS l'admission — le terminal l'a
            // effacée sous le verrou (M-3) : si `close` a passé sa section critique, la lecture
            // trouve `null` et le bail répond `Closed` SANS invoquer le block. Si la lecture a
            // gagné la course, la View était celle d'une surface vivante à l'instant de la
            // remise ; `close` tournant sur le thread principal, le block dispatché d'un bail
            // admis-puis-fermé s'ordonne derrière le teardown — et s'achève (NonCancellable).
            val view = viewSource?.invoke()
                ?: return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface))
            return hostThread.dispatch {
                KadreResult.Success(withContext(NonCancellable) { block(view) })
            }
        } finally {
            lock.withLock { leaseHeld = false }
        }
    }

    /**
     * Installe la configuration de session que cette surface publie.
     *
     * Task 3 : la configuration est ce dont le réducteur d'entrée est FAIT — la surface
     * construit ici son `RuntimeSurfaceInput` avec la sémantique exacte des arguments web
     * (`WebHostSession.kt:967-996`), puis le pont d'observation qui le nourrit :
     *
     * - `deliveryPolicy` (la policy FENÊTRE) : hors de cette phase — c'était la matière du
     *   moteur d'interactions du web, et le seam n'expose pas d'interactions armées en
     *   phase 3 (sonde du cas 6) ; elle n'est ni stockée ni interprétée ;
     * - `inputDeliveryPolicy` = la policy d'entrée du réducteur (l'ordonnancement discrete/
     *   continuous/scroll/gesture, telle quelle) ;
     * - `eventStampSource` = la source de stamps de session, telle quelle ;
     * - `eventCollectorGate` / `textInputEventCollectorGate` = DEUX gates du même allocateur
     *   de session (l'[Any] runtime-interne, rendu inchangé — le miroir
     *   `sessionCollectorAllocator` web, qui [error] sur un type invalide) ;
     * - `textInputPort` = le port qui refuse — le paramètre runtime n'est pas nullable et la
     *   phase 3 n'a AUCUN IME (le miroir inverse du port web structurellement supporté) : la
     *   capability dit `Unsupported(TextInput)` dès la construction ;
     * - `rawInputCoordinator = null` + `rawInputCapability = Unsupported(RawInputAccess)` —
     *   l'accès natif n'est pas activé, la capability le dit structurellement (le miroir web) ;
     * - `dragAndDropAvailable = false` — le paramètre est la capability `dragAndDrop` de
     *   l'ÉTAT d'entrée (et le garde de `presentDrop`) : il promet l'observation du drag, et
     *   la phase 3 n'a aucun listener de drag (le drop est phase 6) — pas de promesse que
     *   rien ne pourrait honorer ;
     * - `resources`, `dropTransferScope` : tels quels (le budget des transferts de drop est
     *   construit du budget de ressources, comme le web) ; `diagnostics` et `rawInputPort`
     *   restent hors de la phase 3 (aucun coordinateur raw input, aucun diagnostic typé
     *   KadreDiagnostic pour les throwables d'entrée — ils partent sur [failureReporter]) ;
     * - `sessionFailureHandler` : tel quel ; les diagnostics d'entrée (débordements) partent
     *   sur le [failureReporter] de la surface.
     *
     * Les interactions synchrones (`RuntimeSynchronousInteraction`) ne sont PAS câblées : le
     * seam ne les expose pas (elles vivent derrière `RuntimeWindowManager.
     * dispatchSynchronousInteraction` et le port d'interaction du port web, sans homologue
     * Android en phase 3) — le différé est épinglé par le test cas 6, les interactions armées
     * restent la phase 4.
     *
     * La source des stamps reste la seule valeur dont Task 1 a besoin au-delà de l'entrée.
     * Une surface qui a cessé d'admettre abandonne la configuration avec les stimuli qui
     * l'attendaient (le miroir de la surface web) ; une seconde installation est une erreur
     * de programmation — une configuration construit une séquence de stamps, deux n'en
     * peuvent pas partager une.
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
        val allocator = sessionCollectorAllocator(collectorAllocator)
        val input = RuntimeSurfaceInput(
            surfaceId = id,
            deliveryPolicy = inputDeliveryPolicy,
            eventStampSource = source,
            eventCollectorGate = allocator.newGate(maxCollectorsPerFlow),
            textInputPort = RefusingTextInputPort,
            rawInputCoordinator = null,
            rawInputCapability = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
            dragAndDropAvailable = false,
            resources = resources,
            dropTransferBudget = RuntimeDropTransferBudget(resources.maxConcurrentDropTransfers),
            dropTransferScope = dropTransferScope,
            textInputEventCollectorGate = allocator.newGate(maxCollectorsPerFlow),
            failureReporter = { cause -> failureReporter.report(cause) },
            sessionFailureHandler = sessionFailureHandler,
        )
        surfaceInput = input
        inputDelivery = AndroidInputDelivery(
            surfaceId = id,
            surfaceInput = input,
            committedState = { mutableState.value },
        )
    }

    /**
     * L'installation de l'observation et du scheduling (la couture du binding, Task 5) — le
     * moment où les capabilities deviennent honnêtes : le snapshot par défaut, toutes refus,
     * cède la place au snapshot [androidSurfaceCapabilities] qui annonce ce que la surface fait
     * désormais — `platformAccess` `Supported`, la rangée Android de `capabilities/android.md`
     * §Phase 2 (metrics + redraw + l'accès natif borné). Idempotent : la même annonce republiée
     * est absorbée par le StateFlow.
     *
     * [inputWired] (Task 3, fix review) est le fait que la couture seule peut porter : un
     * wrapper T1 a-t-il été CONSTRUIT pour cet attach (forme Activity avec un callback
     * fenêtre à enchaîner) ? Les faits du stimulus structurel d'entrée suivent CETTE
     * présence — pas la forme déclarée — de sorte qu'un attach sans observateur (forme View,
     * ou Activity dont `window.callback` est null) laisse les capabilities d'entrée du
     * réducteur à leur refus d'origine au lieu de promettre un clavier, un pointeur et un
     * tactile qui ne peuvent jamais arriver (la sémantique C de BACKEND-CAPABILITIES §4 —
     * `Available` seulement après une installation réelle). Le réducteur absorbe le
     * stimulus « rien n'est câblé » (dédoublonnage) : ni événement, ni mouvement de révision.
     *
     * « Jamais après la révocation » — la double garde de l'admission (`detached` OU état
     * publié détaché) : une installation qui suit [close] ou qui arrive dans la fenêtre de
     * teardown (l'instantané détaché publié par le binding, le terminal pas encore passé) est
     * absorbée — pas de promesse repeinte sur un état déjà détaché, et rien ne ressuscite.
     */
    internal fun observationInstalled(inputWired: Boolean) {
        lock.withLock {
            if (detached || mutableState.value.attachment == SurfaceAttachmentState.Detached) {
                return
            }
            mutableCapabilities.value = androidSurfaceCapabilities()
        }
        // L'installation de l'observation d'entrée (Task 3) : le stimulus structurel — avec
        // les faits câblés — puis le replay ordonné des observations bufferisées. Hors verrou :
        // la delivery vit sur le thread principal, et le réducteur runtime tient le sien.
        inputDelivery?.observationInstalled(
            keyboardInstalled = inputWired,
            pointerInstalled = inputWired,
            touchInstalled = inputWired,
        )
    }

    /**
     * Le terminal unique de cette surface — le miroir de la transition web, atteint par la
     * teardown du runtime ([org.graphiks.kadre.internal.runtime.RuntimePrimarySurface] →
     * `surface::close`) et idempotent :
     *
     * 1. le drapeau terminal est posé sous le verrou — toute admission suivante répond `Closed` ;
     * 2. le scheduler de redraw est annulé sous le même verrou et la source de la View du bail
     *    est effacée au même endroit (M-3) — plus jamais de callback de frame après le retour
     *    (zombie interdit ; [emitRedrawFrame] garde en plus son propre garde terminal pour un
     *    corps de frame que la course du SDK aurait déçu), plus jamais de View démontée louée ;
     * 3. les capabilities deviennent indisponibles AVANT la publication de l'état détaché (« les
     *    capabilities indisponibles au plus tard au détached » — un consommateur ne peut jamais
     *    lire une promesse sur un état déjà détaché) ;
     * 4. l'état détaché est publié — sauf s'il l'est déjà : le binding publie l'instantané
     *    détaché canonique AVANT le terminal, et la révision est le marqueur d'un changement
     *    d'état, pas d'un appel (la règle de la référence, celle de la phase 0) ;
     * 5. le flux d'événements se termine — les collecteurs en cours reçoivent la fin, les
     *    collecteurs suivants voient un flux déjà terminé.
     */
    override fun close() {
        lock.withLock {
            if (detached) return
            detached = true
            // M-3 : la source de la View meurt avec la surface — un bail admis avant close
            // mais dont la lecture suit le teardown ne reçoit jamais la View démontée.
            viewSource = null
            redrawScheduler.cancel()
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
        // Le réducteur d'entrée ferme EN DERNIER — l'état terminal qu'il ne peut plus
        // publier est déjà dehors ; sa fermeture achève le flux d'événements d'entrée et
        // refuse tout stimulus ultérieur, une fois et une seule (idempotent).
        surfaceInput?.close(failure = null)
        terminal.complete(Unit)
    }

    /**
     * Publie une observation du host dans l'état de la surface — la même publication que la
     * surface interne de la phase 0, tenue courante : la révision ne bouge que pour un état qui
     * bouge réellement (invariant P1 : même instance, révision = marqueur de changement), et
     * une publication qui suit la fermeture (ou l'état détaché déjà publié par le binding) est
     * absorbée exactement comme le fait `close`.
     *
     * Task 2 : l'événement typé. [eventFor] — quand il est fourni (le branchement métriques ;
     * les publications lifecycle du binding restent muettes avec le défaut `null`) — construit
     * l'événement depuis l'état AVANT, l'état publié et le stamp de session ([stampSource]). La
     * publication reste muette tant que la configuration de session n'est pas installée (un
     * `EventStamp` inventé ici serait une seconde séquence concurrente de celle de la session)
     * et quand l'état n'a pas bougé. Le stamp est pris et l'événement émis SOUS le verrou :
     * l'ordre du flux d'événements est l'ordre des stamps, lui-même l'ordre des publications.
     * L'émission est `tryEmit` sur le flux borné (jamais bloquante, drop documenté au
     * débordement — un drop sous policy n'est pas une failure annoncée, miroir web).
     */
    internal fun publishMetrics(
        update: (SurfaceState) -> SurfaceState,
        eventFor: ((before: SurfaceState, published: SurfaceState, stamp: EventStamp) -> SurfaceEvent)? = null,
    ) {
        lock.withLock {
            val current = mutableState.value
            if (detached || current.attachment == SurfaceAttachmentState.Detached) return
            val next = update(current)
            if (next == current) return
            check(current.revision.value < Long.MAX_VALUE) { "surface revision space exhausted" }
            val published = next.copy(revision = SurfaceRevision(current.revision.value + 1L))
            mutableState.value = published
            val source = stampSource ?: return
            val stamp = source()
            eventFor?.invoke(current, published, stamp)?.let { mutableEvents.tryEmit(it) }
        }
    }

    /**
     * Le corps de frame du scheduler (Task 3) : UN `RedrawRequested` par frame, portant la
     * révision courante et le stamp de session, émis SOUS le verrou (l'ordre du flux
     * d'événements est l'ordre des stamps, lui-même l'ordre des frames). Double garde terminal :
     * le scheduler est annulé par [close], et un corps de frame que la course du SDK aurait
     * déçu après le terminal est absorbé ici — aucun événement ne peut suivre le terminal.
     * Muette tant que la configuration de session n'est pas installée (pas de stamp inventé).
     */
    private fun emitRedrawFrame() {
        lock.withLock {
            if (detached) return
            val source = stampSource ?: return
            mutableEvents.tryEmit(
                SurfaceEvent.RedrawRequested(mutableState.value.revision, source()),
            )
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

    // ------------------------------------------------------------------
    // La couture d'entrée (Task 3) — les funnels que le wrapper T1 et le tracker T2
    // nourrissent, et la lecture de suppression que le wrapper consulte. Thread principal
    // uniquement ; sans configuration installée, il n'y a rien à nourrir (le wrapper,
    // installé après l'attach, ne peut de toute façon pas observer plus tôt).
    // ------------------------------------------------------------------

    /** Route une observation clavier/focus du wrapper T1 vers la delivery. */
    internal fun onInputObservation(observation: AndroidInputObservation) {
        inputDelivery?.onObservation(observation)
    }

    /** Route une observation motion du wrapper T1 vers la delivery, avec les bornes de la View. */
    internal fun onMotionObservation(observation: AndroidMotionObservation, width: Int, height: Int) {
        inputDelivery?.onMotion(observation, width, height)
    }

    /**
     * La lecture de suppression (le `suppressDefaultFor` web, côté surface) : la réponse
     * vient de l'état COMMIS de la surface — [SurfaceState.inputDefaultBehavior] tel que
     * `apply` l'a rendu (le chemin de commit est le sujet de la Task 4 ; jusqu'alors l'état
     * reste `HostDefault`, qui ne consomme jamais). La category est la famille des
     * scroll-keys documentaires — l'ensemble fermé [ANDROID_DOCUMENT_SCROLL_KEYS], le miroir
     * des 9 touches web, keyé sur le keycode natif (carry de la review Task 1 : les touches
     * de page, Home et End n'existent pas comme touches logiques nommées dans la table
     * Android). Pure lecture : rien n'y commet rien.
     */
    internal fun shouldConsumeKey(nativeKeyCode: Int): Boolean = when (mutableState.value.inputDefaultBehavior) {
        InputDefaultBehavior.HostDefault -> false
        InputDefaultBehavior.SuppressWhenPossible -> nativeKeyCode in ANDROID_DOCUMENT_SCROLL_KEYS
    }
}

/** Affirme qu'un champ sans chemin de commit a bien été refusé par sa capability. */
private fun PropertyChange<*>.requireRefused(property: SurfaceProperty) {
    check(this is PropertyChange.Unchanged) {
        "${property.fieldName} arrived admitted, but the android surface has no commit for it"
    }
}

/**
 * Le snapshot honnête d'une surface Android observée : les refus de la phase 2
 * ([unsupportedSurfaceCapabilities]) avec l'annonce d'installation — `platformAccess` `Supported`
 * (la rangée Android de `capabilities/android.md` §Phase 2, qui couvre ensemble les métriques
 * publiées, le redraw coalescé et l'accès natif borné du bail). Publié par
 * [AndroidHostSurface.observationInstalled] quand l'observation et le scheduling vivent, retiré
 * au terminal ([AndroidHostSurface.close]) — jamais après.
 */
private fun androidSurfaceCapabilities(): SurfaceCapabilities =
    unsupportedSurfaceCapabilities().copy(
        platformAccess = Capability.Supported(Unit, FeatureAvailability.Available),
    )

/**
 * L'entrée d'une surface dont la configuration de session n'a pas installé — l'état honnête
 * de la phase 2, INSTANCIÉ PAR SURFACE (le stub partagé d'avant la Task 3 n'existe plus :
 * l'install remplace l'instance entière, et le swap est observable). Les mêmes refus que le
 * runtime neuf : toutes les capabilities indisponibles, aucun événement, chaque appel refuse.
 */
private class PreInstallAndroidSurfaceInput : SurfaceInput {
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
