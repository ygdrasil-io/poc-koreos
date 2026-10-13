@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPort
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import platform.Foundation.NSThread
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenDidConnectNotification
import platform.UIKit.UIScreenDidDisconnectNotification
import platform.UIKit.UIScreenModeDidChangeNotification
import platform.darwin.NSObjectProtocol

/**
 * Le seam SDK du port display UIKit/tvOS (phase 8, Task 2) — le vrai inventaire `UIScreen` en
 * production, une source factice pilotable en test hôte (le pattern jeton du module : le port
 * ne nomme JAMAIS un type UIScreen — c'est la traduction de ce seam en formes port qui est
 * prouvée dans `uikitTest`, pas les lectures SDK elles-mêmes, charterées sur device).
 */
internal interface UIKitDisplaySource : AutoCloseable {
    /**
     * L'inventaire COMPLET des écrans du process (`UIScreen.screens`), au moment de l'appel.
     * Le port relit cet inventaire à chaque demande ET à chaque fire de [registerListener] —
     * jamais un delta.
     */
    public fun screens(): List<Any>

    /**
     * L'écran donné est-il l'écran principal du process (`UIScreen.mainScreen` — le fait klib
     * disponible sur les DEUX cibles) ? Le dernier recours du ruling quand le port ne peut pas
     * résoudre l'écran hôte de SA session — jamais la décision seule : l'hôte d'une session
     * est l'écran de SA fenêtre (couture d'attach), pas forcément l'écran principal.
     */
    public fun isMain(screen: Any): Boolean

    /** La lecture SDK brute d'un écran de [screens] — les faits, pas leur interprétation. */
    public fun factsOf(screen: Any): UIKitDisplayFacts?

    /**
     * Enregistre [listener] pour chaque moment où le système signale un écran branché,
     * débranché ou changé (`UIScreenDidConnect/DidDisconnect/ModeDidChange`, UN seam multi-cast
     * contracté en UNE callback). L'argument est vide : le port ne consomme JAMAIS l'événement
     * comme un delta — il relit l'inventaire complet.
     */
    public fun registerListener(listener: () -> Unit)

    /** Retire exactement l'instance [listener] donnée à [registerListener]. */
    public fun unregisterListener(listener: () -> Unit)
}

/**
 * Le port display UIKit/tvOS : l'inventaire COMPLET du multi-cast `UIScreen`, sans
 * slot-conflict — le miroir exact d'`AndroidDisplayPort` et de `WebDisplayPort`.
 *
 * L'inventaire initial est publié SYNCHRONEMENT à `installSnapshotObserver` — une session qui
 * s'attache et ne fait rien doit déjà énoncer les écrans que le système lui donne, sans
 * attendre un événement qui ne viendra peut-être jamais — et chaque fire du listener republie
 * l'inventaire RELIT EN ENTIER : le port ne fabrique jamais un delta entre deux états du seam.
 * `workArea` reste `null` TOUJOURS (iOS/tvOS n'ont pas de bureau — le kernel T1 ne l'invente
 * pas), les bounds sont la géométrie physique de l'écran à l'ORIGINE (points × échelle,
 * arrondies par le kernel), et les clés de corrélation sont CELLES DES FAITS — l'identité que
 * le seam attribue à l'écran (l'`ObjectIdentifier` du `UIScreen` en production) portée telle
 * quelle ; le runtime alloue ses propres identités publiques par-dessus.
 *
 * Ce qui n'est pas mesurable est un échec, pas une supposition : un inventaire vide, ou UN
 * SEUL écran dont la lecture ne porte pas une géométrie portable (le kernel répond `null` —
 * dimension/échelle non finie ou non positive), répond
 * `TemporarilyUnavailable(retryable=true)` — à la demande ET via l'observer — qui RETIRE
 * l'inventaire précédent ; un snapshot réussi ne contient jamais un inventaire partiel (le
 * risque n°2 du plan, miroir Android `translatedInventory`). `enumerationCapability` est
 * `Supported(Unit, Available)` dès la construction : les trois notifications `UIScreen` sont
 * structurellement observables (spike T1 — les DEUX cibles, pas de permission, pas de geste).
 *
 * Le ruling du type est INJECTÉ (la décision par cible vit à la couture d'attach, pas dans le
 * kernel ni ici) : l'écran résolu par [hostScreenResolver] — l'écran de LA fenêtre de la
 * session, lu à chaque relecture — reçoit `displayTypeRuling(true)` (le HostViewport de CETTE
 * scène), les autres `displayTypeRuling(false)` (les écrans AirPlay, `Physical` ; tvOS, un
 * seul écran, l'unique HostViewport). Quand le resolver ne rend rien, le fait `isMain` du
 * seam (`UIScreen.mainScreen`) est le dernier recours — le fait T1 rendu vivant, jamais la
 * règle seule.
 *
 * Le port est scoped à la session : le runtime installe exactement un observer et ferme le
 * port avec les composants de la session, ce qui désenregistre le listener du seam et ferme
 * le seam possédé.
 *
 * THREADING (revue Task 2, Important-1) — `UIScreen` est un objet main-thread-only (contrat
 * UIKit), donc TOUTE lecture du seam l'est aussi, par transitivité :
 *  - [requestSnapshot] est la voie APPLICATIVE (`RuntimeDisplayManager.requestAccess`, tout
 *    thread) : un appelant hors du thread principal est FUNNELÉ sur le main
 *    (`Dispatchers.Main.immediate` — le patron du bail d'[UIKitWindowCommandPort.lease] :
 *    inline quand l'appelant y est déjà, un seul saut de file sinon), jamais une lecture
 *    UIScreen depuis le thread appelant ;
 *  - [installSnapshotObserver] et la publication initiale, comme le chemin de fire, sont
 *    main-confined PAR LA COMPOSITION : le seul install de production vit dans la construction
 *    de la session (`RuntimeDisplayManager.init`, attach UIKit/tvOS — `KadreIos.attach` refuse
 *    tout appel hors main) et la source de production livre les fires sur la file principale
 *    (`NSOperationQueue.mainQueue()`, cf. [UIKitScreenDisplaySource]).
 *
 * @param source le seam `UIScreen` que ce port lit ; possédé par le port, fermé avec lui.
 * @param hostScreenResolver les faits de l'écran hôte de la session (l'écran de SA fenêtre),
 *   relus à chaque relecture ; `null` quand la couture ne peut pas les résoudre.
 * @param displayTypeRuling la décision HostViewport/Physical de la couture, par écran hôte ou non.
 */
internal class UIKitDisplayPort(
    private val source: UIKitDisplaySource,
    private val hostScreenResolver: () -> UIKitDisplayFacts?,
    private val displayTypeRuling: (isHostScreen: Boolean) -> DisplayType,
) : DisplayPort {
    private val lock = RuntimeLock()
    private var closed: Boolean = false
    private var observer: ((KadreResult<DisplayPortSnapshot>) -> Unit)? = null

    /** Le listener du seam de l'install VIVANTE — chaque install enregistre le sien, par instance. */
    private var listener: (() -> Unit)? = null
    private var subscription: AutoCloseable? = null

    override val enumerationCapability: Capability<Unit> =
        Capability.Supported(Unit, FeatureAvailability.Available)

    /**
     * La voie applicative (tout thread — `RuntimeDisplayManager.requestAccess`) : la relecture
     * du seam `UIScreen` est FUNNELÉE sur le thread principal, inline quand l'appelant y est
     * déjà (`Dispatchers.Main.immediate` — un seul saut de file sinon, le patron exact du bail
     * d'[UIKitWindowCommandPort.lease]). `UIScreen` est main-thread-only (contrat UIKit) : un
     * inventaire relu hors du main n'est pas une lecture, c'est un comportement indéfini.
     */
    override suspend fun requestSnapshot(): KadreResult<DisplayPortSnapshot> =
        if (NSThread.isMainThread()) {
            snapshotResult()
        } else {
            withContext(Dispatchers.Main.immediate) { snapshotResult() }
        }

    /**
     * Installe l'observer session-runtime, publie l'inventaire courant à lui exactement une
     * fois (HORS du lock — la même réponse qu'un fire du seam livrerait), et republie à chaque
     * fire ultérieur du listener du seam.
     *
     * Un observer à la fois (miroir Android/Web) : un second install remplace le premier — le
     * port désenregistre le listener qu'il avait créé pour l'install remplacée et s'enregistre
     * à nouveau, la publication initiale du remplacement va à l'observer du remplacement, et
     * le `AutoCloseable` d'un install remplacé reste appelable sans effet (il ne désabonne
     * jamais l'install qui l'a remplacé, ne ferme jamais le seam possédé).
     */
    override fun installSnapshotObserver(
        observer: (KadreResult<DisplayPortSnapshot>) -> Unit,
    ): AutoCloseable {
        var replaced: AutoCloseable? = null
        val listener: () -> Unit = { emitCurrent() }
        var withdrawn = false
        var handle: AutoCloseable? = null
        val registration = lock.withLock {
            check(!closed) { "the uikit display port is closed" }
            replaced = subscription
            this.observer = observer
            this.listener = listener
            // L'abonnement au seam EST l'install : c'est ici — jamais avant — que le port
            // commence à recevoir les fires des notifications UIScreen.
            source.registerListener(listener)
            handle = AutoCloseable {
                val live = lock.withLock {
                    if (withdrawn || closed) return@withLock null
                    withdrawn = true
                    if (this.listener === listener) this.listener = null
                    if (this.observer === observer) this.observer = null
                    if (subscription === handle) subscription = null
                    listener
                }
                if (live != null) source.unregisterListener(live)
            }.also { subscription = it }
            handle
        }
        replaced?.close()
        // La première poussée de l'install, hors du lock : l'inventaire initial n'attend jamais
        // un événement que le système ne livrera peut-être pas (miroir Android :175).
        observer(snapshotResult())
        return registration
    }

    /**
     * Désenregistre le listener du seam vivant, ferme le seam que ce port possède, et oublie
     * l'observer. Idempotent : tout appel ultérieur, comme tout fire du seam après le premier,
     * ne publie rien — le seam possédé n'est fermé que PAR LA PREMIÈRE transition (la
     * propriété du port : exactement une fois, sans compter sur l'idempotence du seam). Les
     * composants de la session ferment le port — le listener NSNotificationCenter meurt avec
     * la session qui l'a installé.
     */
    override fun close() {
        var firstClose = false
        val live = lock.withLock {
            if (closed) return@withLock null
            closed = true
            firstClose = true
            val liveListener = listener
            listener = null
            observer = null
            subscription = null
            liveListener
        }
        if (live != null) source.unregisterListener(live)
        if (firstClose) source.close()
    }

    /** Livre la réponse courante à l'observer installé, quoi que le seam mesure maintenant. */
    private fun emitCurrent() {
        val installed = lock.withLock { observer } ?: return
        installed(snapshotResult())
    }

    private fun snapshotResult(): KadreResult<DisplayPortSnapshot> {
        val inventory = translatedInventory()
            ?: return KadreResult.Failure(KadreFailure.TemporarilyUnavailable(retryable = true))
        return KadreResult.Success(inventory)
    }

    /**
     * L'inventaire COMPLET re-traduit, ou `null` si UNE seule pièce est imesurable — un
     * snapshot réussi ne contient jamais un inventaire partiel. Le resolver d'écran hôte est
     * relu À CHAQUE traduction (un fire relit tout, la couture y comprise).
     */
    private fun translatedInventory(): DisplayPortSnapshot? {
        val screens = source.screens()
        if (screens.isEmpty()) return null
        val host = hostScreenResolver()
        val translated = screens.map { screen ->
            val facts = source.factsOf(screen) ?: return null
            // L'écran hôte de CETTE session : la clé résolue par la couture gagne ; sans
            // resolver, le fait isMain du seam (UIScreen.mainScreen) est le dernier recours.
            val isHost = if (host != null) facts.key == host.key else facts.isMain
            uikitDisplayPortDisplay(facts, displayTypeRuling(isHost)) ?: return null
        }
        return uikitDisplaySnapshotOf(translated, host?.key)
    }
}

/**
 * La réalisation production du seam : le vrai inventaire `UIScreen` du process et ses TROIS
 * notifications (`UIScreenDidConnectNotification`, `UIScreenDidDisconnectNotification`,
 * `UIScreenModeDidChangeNotification` — les constantes klib des DEUX cibles, spike T1 §2),
 * block-based sur la file principale, le mécanisme exact d'[UIKitSurfaceObservationBinding] et
 * d'[UIKitLifecycleSource].
 *
 * Le listener du SDK est contracté en UNE callback (le port relit l'inventaire complet, il ne
 * consomme jamais l'événement comme un delta) ; les livraisons arrivent sur la file principale
 * (l'attach EST sur le main), où l'anti-désenregistrement du port est sûr par son lock. Les
 * lectures (`screens`, `isMain`, `factsOf`) sont main-confined COMME les livraisons —
 * `UIScreen` est main-thread-only (contrat UIKit) : la voie applicative du port les y funnel
 * (contrat de threading du port, revue Task 2 Important-1). La
 * clé de corrélation d'un écran est son `hash` NSObject (l'ObjectIdentifier du `UIScreen` en
 * production) — stable à vie de l'écran, unique au process ; le runtime alloue ses propres
 * identités publiques par-dessus.
 *
 * Le close retire TOUTES les registrations restantes (idempotent) : un listener remis à
 * [unregisterListener] après le close n'a déjà plus de tokens à retirer.
 */
internal class UIKitScreenDisplaySource : UIKitDisplaySource {
    private val lock = RuntimeLock()
    private var registrations: HashMap<() -> Unit, List<NSObjectProtocol>> = HashMap()

    override fun screens(): List<Any> = UIScreen.screens.orEmpty().filterIsInstance<UIScreen>()

    override fun isMain(screen: Any): Boolean =
        (screen as? NSObjectProtocol)?.isEqual(UIScreen.mainScreen) == true

    override fun factsOf(screen: Any): UIKitDisplayFacts? {
        val uiScreen = screen as? UIScreen ?: return null
        val bounds = uiScreen.bounds.useContents { size.width to size.height }
        return UIKitDisplayFacts(
            key = keyOf(uiScreen),
            // Le klib PARTAGÉ n'expose AUCUN nom d'écran (`UIScreen.name` : 0-hit des deux
            // knm ; `localizedName` : iOS-seul, exclu de uikitMain par la discipline tvOS) —
            // null honnête, jamais fabriqué (le contrat T1 porte le nom nullable).
            name = null,
            boundsWidth = bounds.first,
            boundsHeight = bounds.second,
            scale = uiScreen.scale,
            isMain = isMain(uiScreen),
        )
    }

    override fun registerListener(listener: () -> Unit) {
        val center = NSNotificationCenter.defaultCenter
        val tokens = NOTIFICATION_NAMES.map { name ->
            center.addObserverForName(name, null, NSOperationQueue.mainQueue()) { _ -> listener() }
        }
        lock.withLock { registrations[listener] = tokens }
    }

    override fun unregisterListener(listener: () -> Unit) {
        val tokens = lock.withLock { registrations.remove(listener) } ?: return
        removeTokens(tokens)
    }

    override fun close() {
        val all: List<List<NSObjectProtocol>> = lock.withLock {
            val taken = registrations.values.toList()
            registrations = HashMap()
            taken
        }
        removeTokens(all.flatten())
    }

    /** La clé de corrélation — l'identité NSObject de l'écran, portée telle quelle par les faits. */
    private fun keyOf(screen: UIScreen): Long = screen.hash.toLong()

    private fun removeTokens(tokens: List<NSObjectProtocol>) {
        val center = NSNotificationCenter.defaultCenter
        for (token in tokens) center.removeObserver(token)
    }

    private companion object {
        /** Les trois noms de notification UIScreen (constantes `String?` du klib → `!!`). */
        val NOTIFICATION_NAMES = listOf(
            UIScreenDidConnectNotification!!,
            UIScreenDidDisconnectNotification!!,
            UIScreenModeDidChangeNotification!!,
        )
    }
}
