package org.graphiks.kadre.platform.android

import android.os.Build
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
import org.graphiks.kadre.diagnostics.KadrePlatform
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
import org.graphiks.kadre.internal.runtime.capabilityFailure
import org.graphiks.kadre.internal.runtime.fieldName
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.PointerCaptureMode
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
 * phase 2 ; depuis la phase 3 (Task 4), `inputDefaultBehavior` y est le PREMIER champ
 * committable (la capability publiée à l'installation, le commit immédiat à une révision — le
 * miroir web) — depuis la phase 4 (Task 2), `cursor` y est le SECOND (annoncé à l'installation
 * quand le seam du pointeur est câblé — exactement l'ensemble mappable de
 * [AndroidCursorIcons] — committé par [AndroidCursorNative] : `System(icon)` pose le TYPE_* de
 * la table, `Hidden` pose l'icône null, `Custom(image)` reste refusé champ par champ) — depuis
 * la phase 4 (Task 3), `pointerCapture` y est le TROISIÈME (annoncé à l'installation quand le
 * seam de capture est câblé — exactement `{None, Confined}`, la disponibilité décidée au
 * niveau d'API — committé EN DEUX TEMPS par [AndroidPointerCapture] : la primitive est posée à
 * l'admission, le LISTENER du SDK confirme — le retour de la requête n'est JAMAIS la
 * confirmation ; `None` se libère et se commit immédiatement, `Locked` reste refusé champ par
 * champ, D13) — et
 * un `requestRedraw` dont Task 3 branchera le scheduling à la place de la
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
     * Le seam natif du curseur (phase 4, Task 2) — la primitive [AndroidCursorNative] que le
     * commit du champ `cursor` pose, câblé par la couture sur la View du host pour LES DEUX
     * formes (`pointerIcon` est une propriété de `View`). `null` (constructions sans seam —
     * tests) : la capability `cursor` reste à son refus à TOUTE installation — la présence du
     * seam est un fait de construction, la même discipline que le `native` de la fenêtre
     * (Task 1) et le `inputWired` de l'entrée : on n'annonce jamais un commit qui ne peut pas
     * arriver. Les tests de commit injectent un recorder.
     */
    private val cursorNative: AndroidCursorNative? = null,
    /**
     * Le seam natif de la capture (phase 4, Task 3) — la primitive [AndroidPointerCapture.Native]
     * que le commit DU LISTENER du champ `pointerCapture` confirme, câblé par la couture sur la
     * View du host pour LES DEUX formes (la capture est une propriété de `View`). `null`
     * (constructions sans seam — tests) : la capability `pointerCapture` reste à son refus à
     * TOUTE installation — la présence du seam est un fait de construction, la même discipline
     * que le `native` de la fenêtre (Task 1), le `cursorNative` du curseur (Task 2) et le
     * `inputWired` de l'entrée : on n'annonce jamais un commit qui ne peut pas arriver. Les
     * tests de commit injectent un recorder.
     */
    private val pointerCaptureNative: AndroidPointerCapture.Native? = null,
    /**
     * La révision de la plateforme, lue au construction en production ([Build.VERSION.SDK_INT])
     * et injectée par les tests : le niveau d'API décide de la DISPONIBILITÉ publiée de
     * `pointerCapture` — `Available` à partir de 26 (`requestPointerCapture`/`releasePointerCapture`
     * et la notification d'état `Window.Callback.onPointerCaptureChanged` sont API 26+, le
     * minSdk du module est 24), `Unavailable` avec
     * la raison d'API en dessous. Un fait de construction, comme la présence du seam.
     */
    private val apiLevel: Int = Build.VERSION.SDK_INT,
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

    /**
     * Le commit en deux temps du champ `pointerCapture` (phase 4, Task 3) — construit avec le
     * seam, absent sans lui (la capability refuse alors avant que quiconque puisse l'appeler).
     * Les callbacks sont les PUBLICATIONS de cette surface : la confirmation du listener
     * ([AndroidPointerCapture.listenerChanged] → `onCommitted`) publie `Confined` à une révision
     * nouvelle, la perte (`onLost`) revient à `None` et publie — [publishMetrics] porte la
     * discipline commune (révision = marqueur de changement, publication absorbée après la
     * fermeture). Le listener du SDK est enregistré à la construction du seam — une réponse ne
     * peut jamais trouver le seam sourd.
     */
    private val pointerCapture: AndroidPointerCapture? = pointerCaptureNative?.let { native ->
        AndroidPointerCapture(
            apiLevel = apiLevel,
            native = native,
            onCommitted = { mode -> publishMetrics(update = { it.copy(pointerCapture = mode) }) },
            onLost = { publishMetrics(update = { it.copy(pointerCapture = PointerCaptureMode.None) }) },
        )
    }

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
 *    runtime ([admitField], celle-là même de la surface web et de la surface de
 *    référence) et, pour `cursor` et `pointerCapture`, les admissions DÉDIÉE de la
 *    référence ([admitCursor], le miroir `MinimalWindowSurface.admitCursor` : `Custom(image)`
 *    consulte `customCursor`, `System(icon)` consulte `cursor` contre l'ensemble
 *    annoncé) et DU WEB ([admitPointerCapture], le miroir `admitCaptureAttempt` :
 *    `Locked` refusé D13, sous l'API 26 `Confined` aussi — zéro appel natif — puis la
 *    TENTATIVE lancée). `inputDefaultBehavior` est SUPPORTED depuis la phase 3 (publiée à
 *    l'installation, `Supported({HostDefault, SuppressWhenPossible}, Available)` — le
 *    miroir web `WebHostSession.kt:2102-2105`) ; `cursor` l'est depuis la phase 4 quand
 *    le seam du pointeur est câblé (`Supported(<exactement les icônes mappables>,
 *    Available)`) ; `pointerCapture` l'est depuis la phase 4 quand le seam de capture
 *    est câblé (`Supported({None, Confined})`, `Available` à partir de l'API 26,
 *    `Unavailable` avec la raison d'API en dessous) : un champ posé est admis ; les
 *    capabilities restantes restent `Unsupported(UpdateSurface)` : un champ posé est rejeté
 *    `RejectedSurfaceField(Unsupported(UpdateSurface))`, un champ non posé ne l'est
 *    jamais — et un rejet ne bloque jamais les champs du même update qui peuvent être
 *    honorés ;
 * 5. le verdict — `inputDefaultBehavior`, `cursor` et `pointerCapture` ont un chemin de commit
 *    (`inputDefaultBehavior` : la surface est son propre backend ; `cursor` : le seam
 *    [AndroidCursorNative] pose le TYPE_* mappé, ou l'icône null pour `Hidden` ;
 *    `pointerCapture` : EN DEUX TEMPS — `None` se commit DANS CETTE PASSE, le release
 *    synchrone côté SDK ayant été posé à l'admission, `Confined` ne se commit JAMAIS ici :
 *    le listener du SDK confirme, la publication suit
 *    [AndroidPointerCapture.listenerChanged] à une révision nouvelle — « le retour d'une
 *    requête SDK n'est pas la confirmation de la capture du pointeur ») — un champ admis est
 *    commis immédiatement, UNE révision nouvelle, l'outcome portant
 *    l'état publié (le miroir du commit web `:1612-1613`) ; le champ sans chemin
 *    de commit reste gardé par [requireRefused]. Rien de posé ni de commis →
 *    `Applied(current)` ; un champ rejeté →
 *    `PartiallyApplied(published, rejected)`. Jamais une failure externe `Unsupported`
 *    pour l'update entier : le rejet est PAR CHAMP, dans la liste du succès.
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
        val cursor = admitCursor(update.cursor, capabilities, rejected)
        val pointerCapture = admitPointerCapture(update.pointerCapture, capabilities, rejected)
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
        // Le champ d'update sans chemin de commit reste gardé plutôt qu'assumé absent : sa
        // capability refuse toute valeur aujourd'hui, mais le jour où elle deviendrait
        // `Supported` le changement atterrirait ici non commis et serait répondu d'un `Applied`
        // pour un état qui ne l'a jamais pris (la même garde que la surface web pose sur ses
        // propres champs sans commit).
        hitTesting.requireRefused(SurfaceProperty.HitTesting)
        // L'instantané du verdict est relu APRÈS les admissions : la tentative de capture est
        // lancée à l'admission (le mot du brief), et une confirmation que le SDK délivrerait
        // dans la foulée de l'appel publierait déjà à travers le verrou réentrant — l'instantané
        // la verrait.
        val current = mutableState.value
        var committed = current
        // `inputDefaultBehavior` (phase 3), `cursor` (phase 4, tâche 2) et `pointerCapture`
        // (phase 4, tâche 3) sont les champs committables de cette surface — la surface est le
        // backend du premier, le seam du pointeur ceux des deux autres. Le commit du curseur
        // : l'admission dédiée ([admitCursor]) n'a pu rendre un `Set`
        // que pour `Hidden` ou `System(icon mappable)` — `Custom` consulte `customCursor`
        // (Unsupported) et une icône hors de l'ensemble annoncé est refusée avant. Le seam
        // reçoit le TYPE_* de la table, ou null pour `Hidden` ; l'état porte le style
        // demandé — le readback EST ce que le seam a reçu (l'implémentation production pose
        // exactement le type reçu, cf. [AndroidViewCursorNative]).
        if (cursor is PropertyChange.Set) {
            val seam = checkNotNull(cursorNative) {
                "cursor arrived admitted, but the android surface has no pointer-icon seam"
            }
            val style = cursor.value
            when (style) {
                is CursorStyle.Hidden -> seam.setPointerIcon(null)
                is CursorStyle.System -> seam.setPointerIcon(
                    checkNotNull(AndroidCursorIcons.systemIconType(style.icon)) {
                        "a mapped icon lost its pointer type between admission and commit"
                    },
                )
                is CursorStyle.Custom -> error("CursorStyle.Custom arrived admitted, but the customCursor capability refuses it")
            }
            committed = committed.copy(cursor = style)
        }
        if (inputDefaultBehavior is PropertyChange.Set) {
            committed = committed.copy(inputDefaultBehavior = inputDefaultBehavior.value)
        }
        // Le commit de `pointerCapture` : `None` seul se commit DANS CETTE PASSE — le release
        // est SYNCHRONE côté SDK ([AndroidPointerCapture.applyMode] l'a déjà posé à
        // l'admission), le commit est donc immédiat, une révision avec les autres champs (le
        // miroir du commit en une passe web). `Confined` ne se commit JAMAIS ici : la tentative
        // lancée à l'admission n'est pas la confirmation — « le retour d'une requête SDK n'est
        // pas la confirmation de la capture du pointeur » — l'état se commit au listener
        // ([AndroidPointerCapture.listenerChanged] → publication à révision nouvelle), et une
        // demande déjà en effet ne bouge rien.
        if (pointerCapture is PropertyChange.Set && pointerCapture.value == PointerCaptureMode.None) {
            committed = committed.copy(pointerCapture = PointerCaptureMode.None)
        }
        // Un commit, une révision, et seulement pour un état qui a réellement bougé :
        // `next != current` est la règle de la référence (`commitUpdateLocked`), celle qui
        // fait de la révision la marque d'un changement d'état plutôt que d'un appel.
        // Demander la valeur déjà en effet est admis et répondu `Applied` de l'état — et
        // donc de la révision — intacts. La publication de l'état est l'événement du commit
        // : aucun champ de ce commit n'a de variante `SurfaceEvent` typée SUR AUCUNE
        // plateforme (la surface web commet le même champ sans en émettre) — les
        // observateurs de `state` voient la valeur, les observateurs d'`events` ne sont pas
        // doublement notifiés.
        val next = if (committed != current) {
            check(current.revision.value < Long.MAX_VALUE) { "surface revision space exhausted" }
            committed.copy(revision = SurfaceRevision(current.revision.value + 1L))
        } else {
            current
        }
        if (next != current) mutableState.value = next
        val published = mutableState.value
        KadreResult.Success(
            if (rejected.isEmpty()) {
                SurfaceUpdateOutcome.Applied(published)
            } else {
                SurfaceUpdateOutcome.PartiallyApplied(published, rejected)
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
     * Depuis la phase 4 (Task 3), le MÊME fait porte la capability `pointerCapture` : le canal
     * d'état du SDK est la notification `Window.Callback.onPointerCaptureChanged`, enchaînée
     * par ce wrapper-là — sans lui, la confirmation du commit en deux temps ne peut jamais
     * arriver et la capability reste à son refus, même le seam des primitives câblé.
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
            // Le fait câblé de la capture (Task 3) : le SEAM porte les primitives, mais le
            // commit vit au LISTENER d'état du SDK — le canal `Window.Callback.
            // onPointerCaptureChanged`, enchaîné par le MÊME wrapper que l'observation d'entrée.
            // `inputWired` nomme CE canal : sans lui (forme View, ou Activity dont `window.
            // callback` est null) la confirmation ne peut jamais arriver et la capability reste
            // à son refus — on n'annonce jamais un commit qui ne peut pas arriver.
            mutableCapabilities.value = androidSurfaceCapabilities(
                cursorCommittable = cursorNative != null,
                pointerCapture = pointerCaptureCapability(
                    wired = pointerCaptureNative != null && inputWired,
                    apiLevel = apiLevel,
                ),
            )
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

    /**
     * L'admission DÉDIÉE du champ `cursor` — le miroir exact de
     * `MinimalWindowSurface.admitCursor` (la référence) : `SurfaceUpdate.cursor` porte un
     * [CursorStyle] tandis que la capability `cursor` contraint les [org.graphiks.kadre.surface.CursorIcon]
     * — l'admission partagée [admitField] ne peut pas les croiser, la référence la scinde
     * donc en deux capabilities et ce champ la suit :
     *
     * - `Custom(image)` consulte `customCursor` — `Unsupported(UpdateSurface)` en phase 4
     *   (aucun chemin image→bitmap prouvé) : refus champ par champ, le seam jamais appelé ;
     * - `Hidden` et `System(icon)` consultent `cursor` ; un `System` dont l'icône est hors de
     *   l'ensemble annoncé (les non-mappables de [AndroidCursorIcons]) est refusé
     *   `Unsupported(UpdateSurface)` — l'annonce et la table ne peuvent pas diverger.
     *
     * Un `Clear` n'atteint jamais ici : la garde [invalidClearField] le rejette avant
     * l'admission. Un champ non posé n'est jamais rejeté, et un refus n'bloque jamais les
     * champs du même update qui peuvent être honorés (la forme commune [admitField]).
     */
    private fun admitCursor(
        change: PropertyChange<CursorStyle>,
        capabilities: SurfaceCapabilities,
        rejected: MutableList<RejectedSurfaceField>,
    ): PropertyChange<CursorStyle> {
        if (change is PropertyChange.Unchanged) return change
        val capability = if (change is PropertyChange.Set && change.value is CursorStyle.Custom) {
            capabilities.customCursor
        } else {
            capabilities.cursor
        }
        val failure = capabilityFailure(capability)
        if (failure != null) {
            rejected += RejectedSurfaceField(SurfaceProperty.Cursor, failure)
            return PropertyChange.Unchanged
        }
        if (
            change is PropertyChange.Set &&
            change.value is CursorStyle.System &&
            capability is Capability.Supported<*> &&
            capability.constraints is Set<*> &&
            change.value.icon !in capability.constraints
        ) {
            rejected += RejectedSurfaceField(
                SurfaceProperty.Cursor,
                KadreFailure.Unsupported(KadreOperation.UpdateSurface),
            )
            return PropertyChange.Unchanged
        }
        return change
    }

    /**
     * L'admission DÉDIÉE du champ `pointerCapture` — le miroir Android de
     * `admitCaptureAttempt` (la surface web) : les règles de CETTE surface d'abord, la
     * capability commune ([admitField]) ensuite, puis la TENTATIVE — l'admission la LANCE (le
     * mot du brief) de sorte qu'une confirmation que le SDK délivrerait dans la foulée de
     * l'appel atterrisse avant l'instantané du verdict (le verrou JVM est réentrant) :
     *
     * - `Locked` (D13) : il appartient à `InteractionAction.LockPointer` — la capture Android
     *   est une confinement de souris, le verrou passe par le chemin d'interaction (phase 4,
     *   tâche 4). Refus champ par champ `Unsupported(UpdateSurface)`, tout niveau d'API, zéro
     *   appel natif. Inatteignable tant que la capability refuse le mode (`Locked` est hors de
     *   l'ensemble annoncé) — énoncé ici plutôt qu'assumé absent, le mot du web ;
     * - sous l'API 26 (le minSdk du module) : `requestPointerCapture`/`releasePointerCapture`
     *   et la notification d'état `Window.Callback.onPointerCaptureChanged` n'existent pas —
     *   `Confined` comme `Locked` sont refusés
     *   champ par champ `Unsupported(UpdateSurface)`, zéro appel natif. Le mot de l'ADMISSION ;
     *   le mot de la PUBLICATION est celui de la capability ([androidSurfaceCapabilities]) :
     *   les modes que la plateforme porte, `Unavailable` avec la raison d'API — et un `None`
     *   qui atteint ici sous l'API 26 est refusé PAR LA CAPABILITY avec cette raison.
     *
     * Divergence web DÉLIBÉRÉE, sans bras Android : le web refuse un `Confined` sans pointeur
     * possédé (`InteractionRequired(Missing)`) avant de demander quoi que ce soit au
     * navigateur — ici le SDK est le faiseur de vérité de la capture (il accorde ou refuse
     * lui-même, le listener d'état le rapporte), et le pont de focus + la réversion du
     * listener couvrent l'état publié.
     *
     * Un champ non posé n'est jamais rejeté ; un refus ne bloque jamais les champs du même
     * update qui peuvent être honorés ; un `Clear` n'atteint jamais ici (la garde
     * [invalidClearField] le rejette avant l'admission).
     */
    private fun admitPointerCapture(
        change: PropertyChange<PointerCaptureMode>,
        capabilities: SurfaceCapabilities,
        rejected: MutableList<RejectedSurfaceField>,
    ): PropertyChange<PointerCaptureMode> {
        if (change is PropertyChange.Unchanged) return change
        if (change is PropertyChange.Set && change.value != PointerCaptureMode.None) {
            if (change.value == PointerCaptureMode.Locked || apiLevel < 26) {
                rejected += RejectedSurfaceField(
                    SurfaceProperty.PointerCapture,
                    KadreFailure.Unsupported(KadreOperation.UpdateSurface),
                )
                return PropertyChange.Unchanged
            }
        }
        val admitted = admitField(change, SurfaceProperty.PointerCapture, capabilities.pointerCapture, rejected)
        if (admitted !is PropertyChange.Set) return admitted
        // La tentative : le seam est nécessairement câblé (la capability n'annonce le champ
        // que le long de sa présence) — un refus du seam à ce stade serait une divergence
        // d'admission (les règles sont les mêmes des deux côtés) et est rapporté tel quel.
        val seam = checkNotNull(pointerCapture) {
            "pointerCapture arrived admitted, but the android surface has no pointer-capture seam"
        }
        val failure = seam.applyMode(admitted.value)
        if (failure != null) {
            rejected += RejectedSurfaceField(SurfaceProperty.PointerCapture, failure)
            return PropertyChange.Unchanged
        }
        return admitted
    }

    // ------------------------------------------------------------------
    // La couture d'entrée (Task 3) — les funnels que le wrapper T1 et le tracker T2
    // nourrissent, et la lecture de suppression que le wrapper consulte. Thread principal
    // uniquement ; sans configuration installée, il n'y a rien à nourrir (le wrapper,
    // installé après l'attach, ne peut de toute façon pas observer plus tôt).
    // ------------------------------------------------------------------

    /** Route une observation clavier/focus du wrapper T1 vers la delivery. */
    internal fun onInputObservation(observation: AndroidInputObservation) {
        // Le pont P1 de la capture (phase 4, tâche 3) : la perte de focus fenêtre que la
        // couture observe déjà (phase 3, le `focusLost` de la delivery) libère la capture côté
        // SDK — le listener la rapportera de lui-même ; le pont couvre la cohérence du seam
        // propre en empruntant le MÊME chemin que la perte rapportée
        // ([AndroidPointerCapture.focusLost] → réversion + publication).
        if (observation is AndroidInputObservation.WindowFocusLost) pointerCapture?.focusLost()
        inputDelivery?.onObservation(observation)
    }

    /**
     * Route une observation motion du wrapper T1 vers la delivery, avec les bornes de la View
     * et son origine DANS l'espace fenêtre (la conversion view-locale du dispatch
     * `Window.Callback` — voir [AndroidMotionTracker.onObservation]).
     */
    internal fun onMotionObservation(
        observation: AndroidMotionObservation,
        width: Int,
        height: Int,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ) {
        inputDelivery?.onMotion(observation, width, height, offsetX, offsetY)
    }

    /**
     * La lecture de suppression (le `suppressDefaultFor` web, côté surface — le seam T1
     * câblé, Task 4) : la réponse vient de l'état COMMIS de la surface —
     * [SurfaceState.inputDefaultBehavior] tel que `apply` l'a commis — et de la table de la
     * plateforme ([AndroidInputTranslation.shouldSuppress], le miroir de `shouldSuppress`
     * web) : sous `HostDefault` rien n'est consommé, sous `SuppressWhenPossible` les NEUF
     * scroll-keys documentaires seules le sont ([ANDROID_DOCUMENT_SCROLL_KEYS], keyé sur le
     * keycode natif — carry de la review Task 1 : les touches de page, Home et End
     * n'existent pas comme touches logiques nommées dans la table Android). La décision du
     * dispatch vit dans [AndroidInputWindowCallback], SOUS LA MÊME PORTE DE FOCUS que
     * l'observation : la vue attachée focalisée seule — une touche consommée répond `true`
     * SANS forward et reste OBSERVÉE, la delivery d'abord, la suppression ensuite (le
     * précédent web « delivered then suppressed ») ; une touche alors que la vue N'A PAS
     * le focus n'est ni observée ni consommée — le host la reçoit entière. Pure lecture :
     * rien n'y commet rien.
     * L'écart press+release contre le web (keydown seul) est documenté dans la KDoc de
     * [AndroidInputTranslation.shouldSuppress] : la consommation Android retire l'événement
     * du flux du host — une presse consommée sans sa relève y laisserait une relève
     * orpheline.
     */
    internal fun shouldConsumeKey(nativeKeyCode: Int): Boolean =
        AndroidInputTranslation.shouldSuppress(nativeKeyCode, mutableState.value.inputDefaultBehavior)
}

/** Affirme qu'un champ sans chemin de commit a bien été refusé par sa capability. */
private fun PropertyChange<*>.requireRefused(property: SurfaceProperty) {
    check(this is PropertyChange.Unchanged) {
        "${property.fieldName} arrived admitted, but the android surface has no commit for it"
    }
}

/**
 * Le snapshot honnête d'une surface Android observée : les refus de la phase 2
 * ([unsupportedSurfaceCapabilities]) avec les annonces d'installation — `platformAccess`
 * `Supported` (la rangée Android de `capabilities/android.md` §Phase 2, qui couvre ensemble les
 * métriques publiées, le redraw coalescé et l'accès natif borné du bail), depuis la
 * phase 3,
 * `inputDefaultBehavior` `Supported({HostDefault, SuppressWhenPossible}, Available)` — le
 * miroir EXACT de la capability web (`WebHostSession.kt:2102-2105`) : les deux valeurs du
 * comportement, disponibles — et, depuis la phase 4, `cursor`
 * `Supported(<exactement les icônes mappables>, Available)` QUAND le seam du pointeur est
 * câblé ([cursorNative] non-null — un fait de construction, la même discipline que le
 * `inputWired` de l'entrée : on n'annonce pas un commit qui ne peut jamais arriver ; le
 * miroir de la fenêtre Task 1, `native != null`), les icônes étant EXACTEMENT celles de la
 * table [AndroidCursorIcons] — l'annonce et la table sont une seule vérité. Publié par
 * [AndroidHostSurface.observationInstalled] quand
 * l'observation et le scheduling vivent (le moment structurel où la surface qui honore — le
 * wrapper de dispatch, la lecture de l'état commis — existe, la même sémantique « `Available`
 * seulement après une installation réelle » que partout ailleurs), retiré au terminal
 * ([AndroidHostSurface.close]) — jamais après. `customCursor` et le dernier champ
 * d'update sans chemin de commit (`hitTesting`) restent `Unsupported(UpdateSurface)` : leurs
 * refus de la phase 2 ne sont pas affaiblis.
 */
private fun androidSurfaceCapabilities(
    cursorCommittable: Boolean,
    pointerCapture: Capability<Set<PointerCaptureMode>>,
): SurfaceCapabilities =
    unsupportedSurfaceCapabilities().copy(
        platformAccess = Capability.Supported(Unit, FeatureAvailability.Available),
        inputDefaultBehavior = Capability.Supported(
            setOf(InputDefaultBehavior.HostDefault, InputDefaultBehavior.SuppressWhenPossible),
            FeatureAvailability.Available,
        ),
        cursor = if (cursorCommittable) {
            Capability.Supported(AndroidCursorIcons.committableIcons, FeatureAvailability.Available)
        } else {
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
        },
        pointerCapture = pointerCapture,
    )

/**
 * La capability `pointerCapture` publiée — les DEUX modes que la plateforme peut honorer,
 * écrits un à un (la capability est la promesse de ce backend, le miroir web
 * `WebHostSession.webSurfaceCapabilities` : `Locked` est DEHORS — D13, la capture Android est
 * une confinement de souris, le verrou appartient à `InteractionAction.LockPointer`, phase 4,
 * tâche 4), avec la disponibilité décidée AU CONSTRUCTION :
 *
 * - sans seam OU sans canal d'état ([wired] — le seam des primitives ET l'enchaînement du
 *   `Window.Callback` qui livre les notifications d'état du SDK, le fait qu'[inputWired] porte
 *   à [AndroidHostSurface.observationInstalled]) — le refus de la phase 2 tient à toute
 *   installation : sans le listener, la confirmation ne peut jamais arriver et la capture
 *   demandée resterait à jamais non committée (on n'annonce jamais un commit qui ne peut pas
 *   arriver, le miroir du curseur sans seam) ;
 * - à partir de l'API 26 — `Available` : `requestPointerCapture`/`releasePointerCapture`
 *   existent et le commit vit au listener d'état du SDK ;
 * - en dessous — `Unavailable` portant la raison d'API (`PlatformFailure(Android,
 *   "pointer-capture", "api-26-required")`, l'équivalent Android de l'indisponibilité web) :
 *   le mot de la PUBLICATION ; l'admission refuse `Confined`/`Locked` champ par champ
 *   `Unsupported(UpdateSurface)` — son propre mot ([AndroidHostSurface.admitPointerCapture]).
 */
private fun pointerCaptureCapability(
    wired: Boolean,
    apiLevel: Int,
): Capability<Set<PointerCaptureMode>> = when {
    !wired -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface))
    apiLevel >= 26 -> Capability.Supported(
        setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
        FeatureAvailability.Available,
    )

    else -> Capability.Supported(
        setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
        FeatureAvailability.Unavailable(
            KadreFailure.PlatformFailure(KadrePlatform.Android, "pointer-capture", "api-26-required"),
        ),
    )
}

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
