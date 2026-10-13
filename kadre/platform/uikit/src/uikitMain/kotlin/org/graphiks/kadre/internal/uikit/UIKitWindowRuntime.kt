@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.useContents
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimePrimarySurface
import org.graphiks.kadre.internal.runtime.RuntimeWindowManager
import org.graphiks.kadre.internal.runtime.RuntimeWindowSurface
import org.graphiks.kadre.internal.runtime.TextInputPortFactory
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.WindowSpec
import platform.UIKit.UIView
import platform.UIKit.UIWindow

/**
 * Composition root UIKit d'une session (miroir d'`AppKitWindowRuntimeDriver` l.72-159) :
 * le port est créé d'abord avec des sinks qui pointent vers le manager, le manager est créé
 * ensuite avec le même objet comme `commandPort` ET `surfaceCommandPort` (driver l.126-144).
 * Différences AppKit assumées :
 *  - `publicWindowCapabilities = false`, `enabledWindowUpdateCapabilities = emptySet()` — la
 *   session UIKit est mono-fenêtre imposée par le host : rien n'est annoncé comme
 *   créable/modifiable côté fenêtre ;
 *  - pas d'attention, pas de fullscreen ;
 *  - phase 6 Task 2 : le port texte EST branché — `textInputPortFactory =
 *   TextInputPortFactory(commandPort::textInputPort)` (miroir AppKit driver l.139), résolu par
 *   surfaceId au commit du peer, le port vivant mémoïsé dans le command port (le CAS de l'open
 *   exclusif tient sur une instance stable) ; la capacité est structurellement `Supported`
 *   (miroir web D-X2).
 *
 * Phase 2 : `publicSurfaceCapabilities = true` — les commands surface sont OUVERTS (le runtime
 * alloue des tickets de redraw et transmet les `SurfaceUpdateCommand` au port) et les
 * capabilities annoncées sont celles d'[uikitSurfaceCapabilities] : redraw coalescé et accès
 * plateforme supportés, les quatre champs update refusés. La contrainte du manager
 * (`RuntimeWindowManager.commitPendingLocked` l.1461 : capabilities publiées exigent un
 * snapshot initial) est satisfaite — le port commite `initialSnapshot()` à l'open.
 *
 * La fenêtre primaire est DEMANDÉE au manager DANS [admitPrimaryWindow] — jamais d'écriture
 * directe dans l'état (le reducer du manager reste l'autorité) — appelé par
 * `RuntimeDeferredAdmissionWindowManager` APRÈS `installSessionConfiguration` : le manager
 * refuse toute admission avant l'installation du stamp source (« window event stamp source must
 * be installed before admission »), et la construction des components précède cette
 * installation dans `SessionRuntime`. Le pont `runBlocking` est sûr ici : tout se joue sur le
 * thread appelant (le main, garantie de l'attach — l'installation EST synchrone dans la
 * construction de la session) et la seule reprise suspendue (`suspendCancellableCoroutine` de
 * `requestWindow`) est drainée par la boucle bloquante elle-même — aucun dispatch vers une
 * autre file, donc aucun interblocage possible avec le main run loop. `requestWindow` rend la
 * main après le handoff (pas après le commit) ; le commit est garanti fait (et `primary`
 * publié) car le port commite pendant le dispatch — vérifié par le check sur
 * `manager.state.primary`. Le primary est lu par la session APRÈS l'installation (le provider
 * de `RuntimeSessionComponents` n'est évalué qu'à cet instant) : la fenêtre primaire est donc
 * committée AVANT Running mais APRÈS la configuration.
 */
internal class UIKitWindowRuntime internal constructor(
    window: UIWindow,
    private val view: UIView,
    policy: KadrePolicy,
    failureReporter: RuntimeFailureReporter,
) : AutoCloseable {
    private val commandPort: UIKitWindowCommandPort = UIKitWindowCommandPort(
        window = window,
        view = view,
        resources = policy.resources,
        failureReporter = failureReporter,
        stimulusSink = { stimulus -> manager.acceptSurfaceStimulus(stimulus) },
        windowStateSink = { id, state -> manager.acceptWindowGeometryObservation(id, state) },
        // La surface est déjà visible dans la vue du host dès l'attach : rien à faire natif.
        readySink = { },
    )

    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = policy.resources,
        commandPort = commandPort,
        surfaceCommandPort = commandPort,
        platform = KadrePlatform.UIKit,
        failureReporter = failureReporter,
        publicWindowCapabilities = false,
        enabledWindowUpdateCapabilities = emptySet(),
        publicSurfaceCapabilities = true,
        enabledSurfaceCapabilities = uikitSurfaceCapabilities(),
        // Phase 6, Task 2 : le port texte (miroir AppKit driver l.139). Résolu par surfaceId au
        // commit (`commitPendingLocked` l.1478), mémoïsé côté command port.
        textInputPortFactory = TextInputPortFactory(commandPort::textInputPort),
    )

    /** Le RuntimePrimarySurface de la session, construit par [admitPrimaryWindow] (mémoïsé). */
    internal var attachedPrimarySurface: RuntimePrimarySurface? = null
        private set

    /**
     * Le surfaceId sous lequel le port de lease est enregistré dans
     * [UIKitViewLeaseRegistry.shared] — posé au commit du peer, lu par [close] pour le retrait.
     * `null` tant que la fenêtre primaire n'est pas committée (rien n'est alors routable).
     */
    private var leaseSurfaceId: SurfaceId? = null

    /**
     * Admission différée de la fenêtre primaire — exactement une fois, appelé par le delegate
     * d'admission juste après avoir transmis `installSessionConfiguration` au manager. Le
     * RuntimePrimarySurface est construit ICI (mémoïsé) : la surface n'existe qu'après le
     * commit, et `RuntimePrimarySurface` lie sa surface à la construction.
     */
    internal fun admitPrimaryWindow() {
        check(attachedPrimarySurface == null) { "primary window already admitted" }
        // Spécification minimale : la taille logique réelle de la vue ; tout le reste par
        // défaut (`WindowSpec(title = "", contentSize = LogicalSize(800, 600), …)` — fondation
        // window/Window.kt l.81-100). `publicWindowCapabilities = false` neutralise la
        // vérification des propriétés initiales du manager (`unsupportedInitialWindowProperty`
        // rend null, RuntimeWindowManager l.1675-1685).
        val bounds = view.bounds.useContents { size.width to size.height }
        val spec = WindowSpec(
            contentSize = LogicalSize(
                bounds.first.coerceAtLeast(1.0),
                bounds.second.coerceAtLeast(1.0),
            ),
        )
        val request = runBlocking { manager.requestWindow(spec) }
        check(request is KadreResult.Success) { "primary window admission failed: $request" }
        // `requestWindow` retourne Success même pour une demande rejetée après admission
        // (le rejet vit dans l'état de la requête) : la preuve d'ouverture réelle est la
        // publication de la fenêtre primaire par le commit du port.
        check(manager.state.value.primary != null) { "primary window was not committed: $request" }
        // Task 4 : le peer est committé — le port de lease (`withUIKitView`) est enregistré
        // pour la surface primaire AVANT que la surface ne devienne joignable par la session :
        // un bail ne peut jamais résoudre une surface dont le peer n'est pas committé. Le
        // runtime seul connaît le surfaceId à ce point (alloué par le manager au commit).
        val primary = surface
        UIKitViewLeaseRegistry.shared.register(primary.id, commandPort)
        leaseSurfaceId = primary.id
        attachedPrimarySurface = RuntimePrimarySurface(primary) { primary.detach() }
    }

    /** La surface primaire (la vue du host), publiée par le commit de la fenêtre primaire. */
    internal val surface: RuntimeWindowSurface
        get() {
            val primary = manager.state.value.primary
                ?: error("uikit primary window is not committed")
            return primary.surface as RuntimeWindowSurface
        }

    /**
     * Ordre AppKit (driver l.146-158) : le port cesse d'admettre (`beginClose`), la route du
     * bail sort du registre (Task 4 — AVANT la publication de l'état détaché par
     * [manager.close], l'ordre Android l.415-416 : « les capabilities indisponibles au plus tard
     * au détached », la route du bail ne survit jamais à un état détaché publié), le manager
     * clos ses requêtes et fenêtres (`manager.close()` — les owners no-op ne touchent jamais aux
     * objets UIKit), le port vide son registre (`finishClose`). Idempotent : le retrait du
     * registre l'est aussi, et `leaseSurfaceId` remis à `null` absorbe un second close. La
     * UIWindow/UIView du host n'est ni détruite ni cachée : seul l'état Kadre est relâché.
     */
    override fun close() {
        commandPort.beginClose()
        leaseSurfaceId?.let { UIKitViewLeaseRegistry.shared.unregister(it) }
        leaseSurfaceId = null
        try {
            manager.close()
        } finally {
            commandPort.finishClose()
        }
    }
}
