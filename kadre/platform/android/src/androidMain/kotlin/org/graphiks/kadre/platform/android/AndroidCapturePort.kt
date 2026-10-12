package org.graphiks.kadre.platform.android

import org.graphiks.kadre.capture.CaptureCapabilities
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureTargetConstraints
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.internal.runtime.CapturePort
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSnapshot
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.SurfaceId

/**
 * La couture du bras Surface (Task 3) : la fabrique PixelCopy derrière un id résolu par le
 * registre. Le squelette l'injecte pour que la résolution du registre soit prouée sans SDK —
 * le défaut est le stub jetant de la Task 1, la Task 3 le remplace par le PixelCopy réel du
 * SurfaceView attaché. L'argument `Any` est le même parti pris que les seams du module : le
 * stub android.jar lance à la construction, le test hôte passe une valeur quelconque.
 */
internal fun interface AndroidPixelCopyStreamFactory {
    public fun create(surfaceView: Any, request: CaptureRequest): KadreResult<CapturePortReservation>
}

/**
 * Le port de capture Android — miroir de WebCapturePort : capabilities sondées UNE FOIS et
 * gelées à la construction, sources HostPickerOnly (MediaProjection n'énumère rien avant le
 * consentement), CaptureTarget.Source refusé avant tout seam (le precedent WEB-ROADMAP
 * :495-530), Surface résolu via le registre des surfaces hôtes attachées, HostChoice branché
 * au consentement (Task 2) et PixelCopy (Task 3).
 *
 * **Les capabilities sont la forme exacte du web (:100, :387-450), la sonde est présente et
 * jamais devinée.** `screen` et `window` sont le MÊME `Supported` — un seul consentement
 * MediaProjection gouverne ce que le picker système offre, la fenêtre le reflète mot pour mot
 * (la limite documentée du web, la même ici) : les frames RGBA_8888 d'un ImageReader, le
 * pointeur composé par le système quand un device existe (`EmbeddedWhenAvailable` —
 * MediaProjection ne commande pas le pointeur, promettre `Hidden` promettrait un effacement
 * impossible), la région non annoncée du tout (l'affichage entier est capté, la forme de refus
 * AppKit). `surface` est le PixelCopy du propre buffer de la SurfaceView : `Supported` à
 * l'API 26+ — le pointeur système n'y est jamais composé (`Hidden`, le miroir exact du canvas
 * web) — et refusé champ par champ en dessous (`Unsupported(CaptureOpen)`, la garde P4 du
 * pointerCapture ; `PixelCopy.request(SurfaceView, ...)` n'existe pas avant 26).
 * `sourceEnumeration` et `hostPicker` reprennent la forme web sans condition : l'inventaire
 * EST le picker, la capability d'énumération le dit quoi qu'il arrive d'aucune autre primitive.
 *
 * **Les sources sont toujours [CapturePortSources.HostPickerOnly]** : aucun inventaire Android
 * n'existe avant le consentement, donc `refreshSources` est le no-op honnête du web — le même
 * objet snapshot, jamais un appel de plus. `requestPermission` et le bras `HostChoice` de
 * `reserve` sont les stubs jetants de la Task 2 ; le bras `Surface` résout l'id dans le
 * registre puis atteint la fabrique PixelCopy (le stub jetant de la Task 3).
 *
 * Le port est session-scoped : les DEUX branches d'attach enregistrent leur surface hôte
 * (le miroir `registerPrimarySurface` du web), le runtime installe exactement un observateur
 * et ferme le port avec les composants de la session.
 */
internal class AndroidCapturePort(
    private val apiLevel: Int,
    private val surfaces: AndroidCaptureSurfaceRegistry,
    private val pixelCopyStreamFactory: AndroidPixelCopyStreamFactory =
        AndroidPixelCopyStreamFactory { _, _ ->
            throw NotImplementedError("the Android PixelCopy surface arm lands with phase 8 task 3")
        },
) : CapturePort {
    private val lock = RuntimeLock()
    private var closed = false
    private var observer: ((KadreResult<CapturePortSnapshot>) -> Unit)? = null

    /** La vérité des capabilities, sondée une fois ici et gelée pour la vie du port. */
    private val probedCapabilities: CaptureCapabilities = probeCapabilities()

    override val initialSnapshot: CapturePortSnapshot = snapshotFor()

    /** Le snapshot derrière lequel le port se tient ; tout fait ultérieur le remplace entier. */
    private var current: CapturePortSnapshot = initialSnapshot

    /**
     * Enregistre la surface hôte de CETTE session — la seule surface que ce port capture
     * honnêtement — comme `registerPrimarySurface` du web, appelé dans les DEUX branches
     * d'attach. L'admission du runtime transfère l'id sans le vérifier, donc cet enregistrement
     * est le seul fait contre lequel la route `Surface` résout ; un port dont la session n'a
     * rien enregistré refuse toute réservation de surface au lieu de deviner.
     */
    public fun registerHostCaptureSurface(id: SurfaceId, surfaceView: Any) {
        surfaces.register(id, surfaceView)
    }

    /** Retire exactement la surface enregistrée sous [id] (le retrait suit la vie de la surface). */
    public fun unregisterHostCaptureSurface(id: SurfaceId) {
        surfaces.unregister(id)
    }

    /** Le consentement explicite — l'armature `activityResultRegistry` de la Task 2 y répond. */
    override suspend fun requestPermission(scope: CapturePermissionScope): KadreResult<CapturePortSnapshot> {
        throw NotImplementedError("the Android consent arm (host choice) lands with phase 8 task 2")
    }

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> {
        if (isClosed()) return closedFailure()
        // Le no-op honnête (miroir web :161-166) : le même objet snapshot, permissions et
        // capabilities gelées incluses, et pas un seam de plus — MediaProjection n'énumère
        // rien de ce qui n'a pas été consenti.
        return KadreResult.Success(lock.withLock { current })
    }

    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        if (isClosed()) return closedFailure()
        return when (target) {
            // Le miroir web :176 — aucun inventaire Android n'existe avant le consentement, donc
            // une cible source est refusée structurellement, ICI, avant tout seam, sans rien
            // réserver et sans rien demander.
            is CapturePortTarget.Source -> KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            // La surface hôte attachée de CETTE session : l'id résolu dans le registre que les
            // deux branches d'attach nourrissent, puis le bras PixelCopy de la Task 3.
            is CapturePortTarget.Surface -> surfaceReservation(target.id, request)

            // Le choix hôte : l'armature de consentement de la Task 2.
            CapturePortTarget.HostChoice ->
                throw NotImplementedError("the Android host-choice arm lands with phase 8 task 2")
        }
    }

    /**
     * L'admission et la réservation de la cible `Surface`, dans l'ordre :
     *
     * 1. **L'id doit être la surface hôte enregistrée par cette session.** L'admission du
     *    runtime ne vérifie que la capability et transfère l'id tel quel, donc cette vérification
     *    est celle du port, et sa forme reflète le refus de cible irrésoluble du runtime —
     *    `InvalidRequest("request.target")`, la forme que [RuntimeCaptureManager.sourceAdmission]
     *    répond pour un id que son inventaire ne nomme pas et que le web répond (:216-219). Le
     *    refus est structurel : pas une interaction de registre de plus que la résolution.
     * 2. **La garde d'API en recul.** Quand la capability gelée a déjà dit `Unsupported(CaptureOpen)`
     *    (sous l'API 26, le miroir du backstop canvas web), une réservation qui atteindrait le
     *    port par tout autre chemin répond la MÊME failure — le verdict constant, jamais un autre.
     * 3. **La fabrique PixelCopy** (Task 3) reçoit la surfaceView attachée et la requête ; le
     *    défaut du squelette est le stub jetant des Tasks 2-3.
     */
    private fun surfaceReservation(
        id: SurfaceId,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        val surfaceView = lock.withLock { surfaces.resolve(id) }
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("request.target"))
        if (apiLevel < 26) {
            return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen))
        }
        return pixelCopyStreamFactory.create(surfaceView, request)
    }

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable {
        val movedPastInitial = lock.withLock {
            check(!closed) { "the android capture port is closed" }
            check(this.observer == null) { "the android capture port observer is already installed" }
            this.observer = observer
            // Une installation sur un port déjà passé son snapshot initial (aucun fait ne le
            // fait bouger dans ce squelette ; la Task 2 en apportera) délivre cette réponse
            // courante une fois — l'observateur voit la vérité dès qu'il existe. Un port encore
            // à son snapshot initial ne délivre rien : le runtime tient déjà exactement cela de
            // sa lecture de construction.
            if (current !== initialSnapshot) current else null
        }
        movedPastInitial?.let { observer(KadreResult.Success(it)) }
        var withdrawn = false
        return AutoCloseable {
            lock.withLock {
                if (withdrawn) return@withLock
                withdrawn = true
                if (this.observer === observer) this.observer = null
            }
        }
    }

    override fun close() {
        lock.withLock {
            if (closed) {
                false
            } else {
                closed = true
                observer = null
                true
            }
        }
        // Le registre meurt avec le port : la session entière le possède, aucun autre détenteur
        // n'existe — rien à déposer ici (le web n'a pas de déregistration non plus).
    }

    /** Un snapshot complet — les permissions `NotDetermined` reflétées sur les deux scopes. */
    private fun snapshotFor(): CapturePortSnapshot = CapturePortSnapshot(
        permissions = CapturePermissionState(PermissionState.NotDetermined, PermissionState.NotDetermined),
        capabilities = probedCapabilities,
        sources = CapturePortSources.HostPickerOnly,
    )

    /**
     * La table de vérité des capabilities, dans la forme exacte du web (:387-450). Les
     * constraints d'écran/portent sur le chemin MediaProjection (l'ImageReader RGBA_8888, la
     * région refusée — l'affichage entier), la fenêtre est le miroir mot pour mot de l'écran,
     * la surface porte les constraints du PixelCopy du buffer propre de la vue, et l'énumération
     * comme le picker sont dans la forme web inconditionnelle.
     */
    private fun probeCapabilities(): CaptureCapabilities {
        val screenAndWindow = Capability.Supported(
            CaptureTargetConstraints(
                formats = setOf(PixelFormat.Rgba8),
                cursorModes = setOf(CaptureCursorMode.EmbeddedWhenAvailable),
                // La forme de refus AppKit, miroir web : MediaProjection capte l'affichage entier,
                // la région sur une cible écran/fenêtre n'est pas annoncée du tout.
                region = FeatureAvailability.Unsupported,
            ),
            FeatureAvailability.Available,
        )
        val surface = when {
            // La garde P4 (pointerCapture, champ par champ) : `PixelCopy.request(SurfaceView, ...)`
            // est API 26+, le module descend à 24 — le champ est refusé en dessous, zéro appel.
            apiLevel < 26 -> Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.CaptureOpen))

            else -> Capability.Supported(
                CaptureTargetConstraints(
                    formats = setOf(PixelFormat.Rgba8),
                    // Le propre buffer de la SurfaceView ne compose jamais le pointeur système —
                    // le miroir exact du canvas web et son `Hidden`.
                    cursorModes = setOf(CaptureCursorMode.Hidden),
                    // Aucun bras de recadrage n'est promis dans ce squelette : la Task 3 décidera.
                    region = FeatureAvailability.Unsupported,
                ),
                FeatureAvailability.Available,
            )
        }
        return CaptureCapabilities(
            screen = screenAndWindow,
            // La limite documentée du web : un seul consentement gouverne ce que le picker
            // système offre, la fenêtre est la même capability, mot pour mot.
            window = screenAndWindow,
            surface = surface,
            // La forme web, inconditionnelle : l'inventaire est le picker, la capability
            // d'énumération le dit quoi qu'il arrive d'aucune autre primitive.
            sourceEnumeration = Capability.Supported(Unit, FeatureAvailability.Available),
            // Le mécanisme de consentement (`createScreenCaptureIntent`) existe à tout minSdk
            // du module : le picker est disponible, sans condition.
            hostPicker = FeatureAvailability.Available,
        )
    }

    private fun isClosed(): Boolean = lock.withLock { closed }

    private fun closedFailure(): KadreResult.Failure =
        KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
}

/**
 * Le registre des surfaces hôtes attachées de CETTE session — le fait contre lequel la route
 * `Surface` du port résout un id. L'argument surfaceView est `Any` (stub-safe en test hôte :
 * le stub android.jar lance à la construction, la Task 3 y lira le vrai SurfaceView). Une
 * surface est enregistrée UNE FOIS par port : réenregistrer le même id avec une autre vue est
 * une erreur de programmation — le même `check` que le `registerPrimarySurface` du web.
 */
internal interface AndroidCaptureSurfaceRegistry {
    public fun register(id: SurfaceId, surfaceView: Any)

    public fun unregister(id: SurfaceId)

    public fun resolve(id: SurfaceId): Any?
}

internal class AndroidHostCaptureSurfaceRegistry : AndroidCaptureSurfaceRegistry {
    private val lock = RuntimeLock()
    private val surfaces = HashMap<SurfaceId, Any>()

    override fun register(id: SurfaceId, surfaceView: Any) {
        lock.withLock {
            val existing = surfaces[id]
            check(existing == null || existing === surfaceView) {
                "the host capture surface is registered once per capture port"
            }
            surfaces[id] = surfaceView
        }
    }

    override fun unregister(id: SurfaceId) {
        lock.withLock { surfaces.remove(id) }
    }

    override fun resolve(id: SurfaceId): Any? = lock.withLock { surfaces[id] }
}
