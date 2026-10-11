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
import org.graphiks.kadre.internal.runtime.unsupportedSurfaceCapabilities
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec
import platform.UIKit.UIView
import platform.UIKit.UIWindow

/**
 * Composition root UIKit d'une session (miroir d'`AppKitWindowRuntimeDriver` l.72-159) :
 * le port est créé d'abord avec des sinks qui pointent vers le manager, le manager est créé
 * ensuite avec le même objet comme `commandPort` ET `surfaceCommandPort` (driver l.126-144).
 * Différences AppKit assumées, phase 1 :
 *  - `publicWindowCapabilities = false`, `publicSurfaceCapabilities = false` — la session
 *   UIKit est mono-fenêtre imposée par le host : rien n'est annoncé comme créable/modifiable ;
 *  - `enabledWindowUpdateCapabilities = emptySet()` et capabilities surface « unsupported » —
 *    snapshot initial figé ;
 *  - pas d'attention, pas de fullscreen, pas de TextInput factory dédiée (défauts du manager).
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
        publicSurfaceCapabilities = false,
        enabledSurfaceCapabilities = unsupportedSurfaceCapabilities(),
    )

    /** Le RuntimePrimarySurface de la session, construit par [admitPrimaryWindow] (mémoïsé). */
    internal var attachedPrimarySurface: RuntimePrimarySurface? = null
        private set

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
        attachedPrimarySurface = RuntimePrimarySurface(surface) { surface.detach() }
    }

    /** La surface primaire (la vue du host), publiée par le commit de la fenêtre primaire. */
    internal val surface: RuntimeWindowSurface
        get() {
            val primary = manager.state.value.primary
                ?: error("uikit primary window is not committed")
            return primary.surface as RuntimeWindowSurface
        }

    /**
     * Ordre AppKit (driver l.146-158) : le port cesse d'admettre (`beginClose`), le manager
     * clos ses requêtes et fenêtres (`manager.close()` — les owners no-op ne touchent jamais
     * aux objets UIKit), le port vide son registre (`finishClose`). La UIWindow/UIView du host
     * n'est ni détruite ni cachée : seul l'état Kadre est relâché.
     */
    override fun close() {
        commandPort.beginClose()
        try {
            manager.close()
        } finally {
            commandPort.finishClose()
        }
    }
}
