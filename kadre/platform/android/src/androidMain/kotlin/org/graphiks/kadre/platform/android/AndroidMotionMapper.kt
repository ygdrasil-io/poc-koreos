package org.graphiks.kadre.platform.android

import android.view.MotionEvent
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerButtonState
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.TouchPhase
import org.graphiks.kadre.internal.runtime.SurfaceStimulus
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.SurfaceId

/**
 * Observation motion copiée — coordonnées, ids, pressions, genres d'outil et axes lus AVANT tout
 * recyclage SDK (le `MotionEvent` est lu par la couture de Task 3 au point de dispatch, puis
 * recylé par le système : rien de ce qui traverse ici n'est une référence SDK, ce sont des
 * primitives copiées, le jumeau motion de [AndroidSurfaceObservation]).
 *
 * Les tableaux sont dimensionnés par [pointerCount] et indexés par pointeur — la même indexation
 * que `getPointerId(i)`/`getX(i)`. [actionMasked] est l'action MASQUÉE (sans les bits d'index),
 * [actionIndex] l'index du pointeur changé des actions DOWN de la famille pointeur ;
 * [buttons] est le `buttonState` (masque), [actionButton] le bouton qu'une action
 * `BUTTON_PRESS`/`BUTTON_RELEASE` nomme. Les axes de scroll sont pré-extraits : les axes
 * relatifs (`AXIS_*RELATIVE`, la souris relative « si observable ») et les notches
 * (`AXIS_HSCROLL`/`AXIS_VSCROLL`). [metaState] voyage pour la couture ; aucun stimulus motion
 * ne porte de modificateurs, le tracker ne le lit donc pas.
 *
 * L'égalité structurelle des tableaux n'est pas définie par valeur (comportement `data class`
 * sur `IntArray`/`FloatArray`) : la couture construit une observation fraîche par événement,
 * et le tracker ne s'y fie jamais au-delà de l'appel — il copie à l'entrée (cf.
 * [AndroidMotionTracker.onObservation]).
 */
internal data class AndroidMotionObservation(
    val actionMasked: Int,
    val actionIndex: Int,
    val pointerCount: Int,
    val pointerIds: IntArray,
    val xs: FloatArray,
    val ys: FloatArray,
    val pressures: Array<Float?>,
    val toolTypes: IntArray,
    val buttons: Int,
    val actionButton: Int,
    val axisHscroll: Float,
    val axisVscroll: Float,
    val axisXrelative: Float,
    val axisYrelative: Float,
    val deviceId: Int,
    val metaState: Int,
)

/**
 * Les deux tables totales du motion — chaque fonction répond pour tout `Int` qu'un
 * `MotionEvent` peut porter, aucune ne lance, aucune n'approxime : un bit hors table est porté
 * comme l'identité native qu'il est ([PointerButton.Other]), un outil non déclaré comme
 * [PointerKind.Unknown]. Les constantes `TOOL_TYPE_*`/`BUTTON_*` sont des constantes de
 * compilation : le mapper se teste sur l'hôte JVM sans instancier aucun `MotionEvent`.
 */
internal object AndroidMotionMapping {

    /**
     * Le genre du pointeur, tel que l'outil l'a déclaré. `TOOL_TYPE_FINGER` vu du lane pointeur
     * (le hover) est une souris : un doigt en hover n'ouvre pas un contact — le contact est le
     * lane touch — et le seul pointeur par surface (D11) porte son fait sous `Mouse`.
     */
    public fun pointerKind(toolType: Int): PointerKind = when (toolType) {
        MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.TOOL_TYPE_FINGER -> PointerKind.Mouse
        MotionEvent.TOOL_TYPE_STYLUS -> PointerKind.Pen
        MotionEvent.TOOL_TYPE_ERASER -> PointerKind.Eraser
        else -> PointerKind.Unknown
    }

    /**
     * Le bouton natif vers le bouton du modèle. Le bit stylet secondaire est l'`Eraser` du
     * modèle (la gomme du stylet), le bit stylet principal le `Barrel` ; tout bit hors table
     * reste ce qu'il est — un code natif, porté tel quel.
     */
    public fun pointerButton(nativeButton: Int): PointerButton = when (nativeButton) {
        MotionEvent.BUTTON_PRIMARY -> PointerButton.Primary
        MotionEvent.BUTTON_SECONDARY -> PointerButton.Secondary
        MotionEvent.BUTTON_TERTIARY -> PointerButton.Auxiliary
        MotionEvent.BUTTON_BACK -> PointerButton.Back
        MotionEvent.BUTTON_FORWARD -> PointerButton.Forward
        MotionEvent.BUTTON_STYLUS_PRIMARY -> PointerButton.Barrel
        MotionEvent.BUTTON_STYLUS_SECONDARY -> PointerButton.Eraser
        else -> PointerButton.Other(nativeButton)
    }
}

/**
 * Les contacts touch que le tracker tient, clé sur le `getPointerId` natif — le miroir direct
 * de `WebTouchContacts` (`WebInputTracking.kt:130-157`) : une identité objet mintée au down
 * (et `POINTER_DOWN`), rendue au up/cancel, jamais réémise. Les trois réductions du miroir
 * tiennent ici aussi : un down doublon d'un contact vivant ne répond rien, un move d'un
 * contact jamais commencé ne répond rien, et un événement d'un contact retiré ne répond plus
 * rien — exactement les réductions que le réducteur partagé opère sur les mêmes formes.
 *
 * La clé est l'id NATIF, stable pour la vie du contact — le fait de la plateforme Android sur
 * lequel toute la règle repose (`getPointerId` d'un pointeur d'un geste ne change pas), cité
 * là où on s'y fie, comme le miroir web le fait pour le `pointerId` du DOM.
 */
internal class AndroidTouchContacts {
    private val identitiesByPointerId = mutableMapOf<Int, Any>()

    /**
     * Ouvre un contact et répond son identité stable, ou `null` quand un contact vit déjà sous
     * cet id — un second down d'un contact vivant n'est pas un second contact.
     */
    fun begin(pointerId: Int): Any? {
        if (identitiesByPointerId.containsKey(pointerId)) return null
        val identity = Any()
        identitiesByPointerId[pointerId] = identity
        return identity
    }

    /** L'identité du contact vivant sous [pointerId], ou `null` quand aucun ne l'est. */
    fun identity(pointerId: Int): Any? = identitiesByPointerId[pointerId]

    /**
     * Retire le contact sous [pointerId] et répond l'identité qu'il tenait, ou `null` quand
     * aucun contact ne vit sous cet id. L'identité n'est plus jamais rendue : le contact est fini.
     */
    fun retire(pointerId: Int): Any? = identitiesByPointerId.remove(pointerId)

    /** Oublie chaque contact : plus aucun ne peut être continué, le prochain down re-mint. */
    fun clear() {
        identitiesByPointerId.clear()
    }
}

/**
 * La dernière position observée du pointeur unique, et le mouvement entre deux observations —
 * le miroir direct de `WebPointerMotion` (`WebInputTracking.kt:84-107`). Android ne porte pas
 * plus de mouvement relatif fiable qu'un `pointermove` du DOM (les axes relatifs sont la souris
 * en mode capture, pas le fait du pointeur), donc le modèle se mesure ici : l'écart entre cette
 * observation et la précédente — sommeable par le runtime, qui coalesce en sommant.
 *
 * Une entrée (hover enter) et une transition de bouton enregistrent la position sans porter de
 * mouvement ; un left oublie, pour qu'une ré-entrée mesure depuis SA propre entrée. Le premier
 * mouvement sans précédent est `(0, 0)` : aucun mouvement n'a été observé, en inventer un
 * serait une approximation. Le runtime garde une identité de pointeur par surface (D11) — la
 * souris ET le stylet fusionnent dans CET état unique, et le delta d'un stylet se mesure depuis
 * la dernière position de la souris, preuve qu'un seul pointeur existe.
 */
internal class AndroidPointerMotion {
    private var previous: LogicalPoint? = null

    /** Enregistre une observation et répond le mouvement depuis la précédente, `(0, 0)` si nulle. */
    fun advance(position: LogicalPoint): LogicalDelta {
        val motion = previous?.let { LogicalDelta(position.x - it.x, position.y - it.y) }
            ?: LogicalDelta(0.0, 0.0)
        record(position)
        return motion
    }

    /**
     * Enregistre une observation dont aucun stimulus ne porte le mouvement : une entrée énonce
     * une position, pas un déplacement, et le mouvement d'une transition de bouton appartient
     * à la transition elle-même.
     */
    fun record(position: LogicalPoint) {
        previous = position
    }

    /** Oublie la précédente : la prochaine observation n'a plus rien à mesurer. */
    fun clear() {
        previous = null
    }
}

/**
 * La frontière de coalescing des scrolls du tracker — le miroir de `WebScrollBoundary`
 * (`WebInputTracking.kt:27-65`), dégénéré en sa forme honnête sur Android : la frontière
 * s'ouvre à CHAQUE `ACTION_SCROLL`. Le miroir web ouvre sur le changement d'unité de
 * livraison du navigateur (la frame) pour fusionner les molettes d'une même frame ; Android ne
 * livre aucun signal de frame à travers la couture — son unité de livraison EST le dispatch,
 * donc chaque observation est une unité nouvelle, et la règle du web « ouvre quand l'unité de
 * mesure change » dégénère en « ouvre toujours ».
 *
 * Les frontières strictement croissantes et jamais réutilisées — un consommateur ne peut
 * fusionner que ce que la plateforme a livré ensemble. Le compteur n'est pas remis à zéro par
 * un reset : une frontière déjà publiée n'est plus jamais distribuée, quoi qu'ait fait le
 * tracker entre-temps (le miroir web `clear`, appliqué au champ que ce tracker possède).
 */
internal class AndroidScrollBoundary {
    private var boundary: Long = 0L

    /** Enregistre un scroll observé et répond la frontière à laquelle il appartient. */
    fun advance(): Long {
        check(boundary != Long.MAX_VALUE) { "scroll boundary space exhausted" }
        boundary += 1L
        return boundary
    }
}

/**
 * Le tracker motion pur de la phase 3 : il traduit une [AndroidMotionObservation] copiée en
 * stimuli [SurfaceStimulus] ordonnés, sans aucun type SDK — le miroir motion du noyau clavier
 * de Task 1, avec les trois règles du plan :
 *
 * - **Contacts stables.** La famille touch (`DOWN`/`POINTER_DOWN`/`MOVE`/`UP`/`POINTER_UP`/
 *   `CANCEL`) est le lane CONTACT, clé par `getPointerId` dans [AndroidTouchContacts] — le
 *   réservoir kadre-old (`KadreActivity.kt:348-418`) porté au modèle : `MOVE` et `CANCEL`
 *   itèrent TOUS les pointeurs de l'événement, les autres actions utilisent l'[actionIndex].
 *   Le ciblage est le gate du plan : un contact dont le DOWN tombe dans les bornes de la View
 *   est livré sur TOUT son geste, au-delà des bornes ensuite ; un DOWN hors bornes ne minte
 *   rien — jamais — et son geste entier est muet. Le gate ne s'applique qu'au DOWN : la
 *   continuité est une identité, pas une géométrie.
 * - **Un seul pointeur (D11).** La famille hover, les boutons et le scroll sont le lane
 *   POINTEUR : souris, doigt-en-hover, stylet et gomme fusionnent dans UN état (souris + stylet
 *   simultanés = une identité, le genre porté par stimulus). Un contact et un pointeur restent
 *   deux lanes disjointes, comme le modèle les garde. Un stylet qui TOUCHE arrive par la
 *   famille touch et est livré en contact — le lane contact ne porte pas de genre ; l'identité
 *   de stylet du même outil est livrée par le lane pointeur tant qu'il survole. La frontière
 *   de ciblage hover est de la couture (le wrapper décide quand livrer) : le tracker livre ce
 *   qu'on lui donne.
 * - **Copies avant toute dérivation.** Les tableaux de l'observation sont copiés à l'entrée :
 *   muter l'array source après [onObservation] ne change ni les stimuli déjà émis, ni les clés
 *   du tracker — la preuve contractuelle du recyclage SDK.
 *
 * Totalité : chaque action répond une liste (vide pour une action que le modèle ne porte pas —
 * l'événement, lui, est de toute façon forwardé par le wrapper), un pointeur de position non
 * finie est sauté plutôt qu'inventé, et rien ne lance en-dehors du `scaleFactor` — un facteur
 * non positif est un bug de couture, pas une observation.
 *
 * Contrat de thread : le tracker appartient à la couture (Task 3), qui ne l'appelle que depuis
 * le thread principal Android — la même discipline que le réducteur de métriques de phase 2.
 */
internal class AndroidMotionTracker(
    private val surfaceId: SurfaceId,
) {
    private val contacts = AndroidTouchContacts()
    private val pointerMotion = AndroidPointerMotion()
    private val scrollBoundary = AndroidScrollBoundary()

    /**
     * Traduit une observation en stimuli copiés, SDK-free, et répond la liste ordonnée — vide
     * quand rien n'est à livrer (contact hors bornes ou retiré, action non portée).
     *
     * [viewWidth]/[viewHeight] sont les bornes PHYSIQUES de la View attachée, le gate du DOWN
     * touch ; [scaleFactor] convertit vers le logique par division IEEE — la même conversion
     * déterministe que les métriques de phase 2 (`PhysicalPoint.toLogical`).
     */
    public fun onObservation(
        observation: AndroidMotionObservation,
        viewWidth: Int,
        viewHeight: Int,
        scaleFactor: Double,
    ): List<SurfaceStimulus> {
        require(scaleFactor.isFinite() && scaleFactor > 0.0) { "scaleFactor must be finite and positive" }

        // Copies défensives : TOUTE la dérivation lit ces instantanés, jamais les tableaux de
        // l'appelant — la preuve `payloadsAreCopiedNotReferenced` tient sur ce bloc. Le compte
        // est serré sur les tableaux réels : une observation mal formée de la couture est
        // tronquée, jamais indexée hors bornes.
        val count = minOf(
            observation.pointerCount,
            observation.pointerIds.size,
            observation.xs.size,
            observation.ys.size,
            observation.pressures.size,
            observation.toolTypes.size,
        )
        val ids = observation.pointerIds.copyOf(count)
        val xs = observation.xs.copyOf(count)
        val ys = observation.ys.copyOf(count)
        val pressures = observation.pressures.copyOf(count)
        val tools = observation.toolTypes.copyOf(count)
        val device = observation.deviceId.takeIf { it >= 0 }?.let { DeviceId(it.toLong()) }

        return when (observation.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> touchBegan(
                observation, ids, xs, ys, pressures, viewWidth, viewHeight, scaleFactor, device,
            )
            MotionEvent.ACTION_MOVE -> touchMoved(ids, xs, ys, pressures, scaleFactor, device)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> touchEnded(
                observation, ids, xs, ys, pressures, scaleFactor, device,
            )
            MotionEvent.ACTION_CANCEL -> touchCancelled(ids, xs, ys, pressures, scaleFactor, device)
            MotionEvent.ACTION_HOVER_ENTER -> pointerEntered(tools, xs, ys, scaleFactor, device)
            MotionEvent.ACTION_HOVER_MOVE -> pointerMoved(tools, xs, ys, pressures, scaleFactor, device)
            MotionEvent.ACTION_HOVER_EXIT -> pointerLeft(tools, device)
            MotionEvent.ACTION_BUTTON_PRESS -> pointerButtonChanged(
                observation, tools, xs, ys, pressures, scaleFactor, device, PointerButtonState.Pressed,
            )
            MotionEvent.ACTION_BUTTON_RELEASE -> pointerButtonChanged(
                observation, tools, xs, ys, pressures, scaleFactor, device, PointerButtonState.Released,
            )
            MotionEvent.ACTION_SCROLL -> scrolled(observation, device)
            else -> emptyList()
        }
    }

    /**
     * Perte de focus / déconnexion : oublie chaque contact actif et l'état du pointeur, SANS
     * émettre — le `StateReset(FocusLost)` est de la livraison (le `focusLost()` du runtime),
     * pas du tracker. Le prochain DOWN re-mint des identités fraîches ; la frontière de scroll
     * n'est pas remise à zéro (une frontière déjà publiée n'est plus jamais distribuée).
     */
    public fun reset() {
        contacts.clear()
        pointerMotion.clear()
    }

    // -----------------------------------------------------------------
    // Lane contact — la famille touch
    // -----------------------------------------------------------------

    private fun touchBegan(
        observation: AndroidMotionObservation,
        ids: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        pressures: Array<Float?>,
        viewWidth: Int,
        viewHeight: Int,
        scaleFactor: Double,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        if (ids.isEmpty()) return emptyList()
        val index = actionIndexOf(observation, ids.size)
        // Le gate des bornes s'applique au DOWN SEUL, AVANT de minter : un contact commencé
        // hors bornes n'existe pas pour cette surface — son geste entier est muet.
        if (!isInsideBounds(xs[index], ys[index], viewWidth, viewHeight)) return emptyList()
        val identity = contacts.begin(ids[index]) ?: return emptyList()
        return listOf(
            SurfaceStimulus.TouchChanged(
                surfaceId = surfaceId,
                nativeIdentity = identity,
                phase = TouchPhase.Started,
                position = logicalPoint(xs[index], ys[index], scaleFactor),
                pressure = pressureOf(pressures[index]),
                deviceId = device,
            ),
        )
    }

    private fun touchMoved(
        ids: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        pressures: Array<Float?>,
        scaleFactor: Double,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        // MOVE itère TOUS les pointeurs de l'événement (kadre-old :348-365) — un stimulus par
        // contact vivant, dans l'ordre des pointeurs ; un id retiré ou jamais commencé ne
        // répond rien.
        val stimuli = ArrayList<SurfaceStimulus>(ids.size)
        for (i in ids.indices) {
            val identity = contacts.identity(ids[i]) ?: continue
            if (!isFinitePosition(xs[i], ys[i])) continue
            stimuli += SurfaceStimulus.TouchChanged(
                surfaceId = surfaceId,
                nativeIdentity = identity,
                phase = TouchPhase.Moved,
                position = logicalPoint(xs[i], ys[i], scaleFactor),
                pressure = pressureOf(pressures[i]),
                deviceId = device,
            )
        }
        return stimuli
    }

    private fun touchEnded(
        observation: AndroidMotionObservation,
        ids: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        pressures: Array<Float?>,
        scaleFactor: Double,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        if (ids.isEmpty()) return emptyList()
        val index = actionIndexOf(observation, ids.size)
        // Exactement UN Ended, au pointeur de l'actionIndex ; l'identité est retirée — les
        // événements suivants de ce pointeur ne répondent plus rien.
        val identity = contacts.retire(ids[index]) ?: return emptyList()
        val position = if (isFinitePosition(xs[index], ys[index])) {
            logicalPoint(xs[index], ys[index], scaleFactor)
        } else {
            // Une position non finie n'est pas inventée ; le Ended porte le dernier point
            // logique fin du contact s'il en existe un, sinon l'action est muette.
            null
        }
        if (position == null) return emptyList()
        return listOf(
            SurfaceStimulus.TouchChanged(
                surfaceId = surfaceId,
                nativeIdentity = identity,
                phase = TouchPhase.Ended,
                position = position,
                pressure = pressureOf(pressures[index]),
                deviceId = device,
            ),
        )
    }

    private fun touchCancelled(
        ids: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        pressures: Array<Float?>,
        scaleFactor: Double,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        // CANCEL itère TOUS les pointeurs de l'événement et livre `Cancelled` TEL QUEL pour
        // chaque contact actif (le host a révoqué le geste — rien n'est compensé), puis retire
        // chaque identité. Les contacts résiduels hors événement (flux mal formé) sont lâchés
        // sans stimulus : un contact que l'événement ne nomme pas n'a pas de `Cancelled` à
        // livrer, et aucun contact ne doit survivre à un cancel.
        val stimuli = ArrayList<SurfaceStimulus>(ids.size)
        for (i in ids.indices) {
            val identity = contacts.retire(ids[i]) ?: continue
            if (!isFinitePosition(xs[i], ys[i])) continue
            stimuli += SurfaceStimulus.TouchChanged(
                surfaceId = surfaceId,
                nativeIdentity = identity,
                phase = TouchPhase.Cancelled,
                position = logicalPoint(xs[i], ys[i], scaleFactor),
                pressure = pressureOf(pressures[i]),
                deviceId = device,
            )
        }
        contacts.clear()
        return stimuli
    }

    // -----------------------------------------------------------------
    // Lane pointeur — hover, boutons, scroll : UN pointeur par surface (D11)
    // -----------------------------------------------------------------

    private fun pointerEntered(
        tools: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        scaleFactor: Double,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        val position = pointerPosition(xs, ys, scaleFactor) ?: return emptyList()
        // Une entrée énonce une position, pas un mouvement — elle l'enregistre.
        pointerMotion.record(position)
        return listOf(
            SurfaceStimulus.PointerEntered(
                surfaceId = surfaceId,
                kind = pointerKindOf(tools),
                position = position,
                deviceId = device,
            ),
        )
    }

    private fun pointerMoved(
        tools: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        pressures: Array<Float?>,
        scaleFactor: Double,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        val position = pointerPosition(xs, ys, scaleFactor) ?: return emptyList()
        return listOf(
            SurfaceStimulus.PointerMoved(
                surfaceId = surfaceId,
                kind = pointerKindOf(tools),
                position = position,
                delta = pointerMotion.advance(position),
                pressure = pressureOf(pressures.firstOrNull()),
                pen = penStateOf(tools.firstOrNull()),
                deviceId = device,
            ),
        )
    }

    private fun pointerLeft(
        tools: IntArray,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        // Un left oublie la position : une ré-entrée mesure depuis SA propre entrée.
        pointerMotion.clear()
        return listOf(
            SurfaceStimulus.PointerLeft(
                surfaceId = surfaceId,
                kind = pointerKindOf(tools),
                deviceId = device,
            ),
        )
    }

    private fun pointerButtonChanged(
        observation: AndroidMotionObservation,
        tools: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        pressures: Array<Float?>,
        scaleFactor: Double,
        device: DeviceId?,
        state: PointerButtonState,
    ): List<SurfaceStimulus> {
        val position = pointerPosition(xs, ys, scaleFactor) ?: return emptyList()
        // actionButton pilote ; sans bouton nommé, le bit bas du masque buttonState ; sans
        // aucun fait de bouton, aucun stimulus n'est inventé.
        val native = observation.actionButton.takeIf { it != 0 }
            ?: lowestSetBit(observation.buttons)
            ?: return emptyList()
        // Une transition de bouton enregistre la position : le mouvement qui suit ne répète
        // pas ce que la transition a déjà porté (miroir web).
        pointerMotion.record(position)
        return listOf(
            SurfaceStimulus.PointerButtonChanged(
                surfaceId = surfaceId,
                kind = pointerKindOf(tools),
                button = AndroidMotionMapping.pointerButton(native),
                buttonState = state,
                position = position,
                pressure = pressureOf(pressures.firstOrNull()),
                pen = penStateOf(tools.firstOrNull()),
                deviceId = device,
            ),
        )
    }

    private fun scrolled(
        observation: AndroidMotionObservation,
        device: DeviceId?,
    ): List<SurfaceStimulus> {
        // Une frontière par observation : l'unité de livraison d'Android est le dispatch (cf.
        // [AndroidScrollBoundary]). Les deux familles d'axes sont deux faits distincts du MÊME
        // événement — un stimulus chacune, la même frontière (le runtime ne fusionne jamais
        // deux unités de scroll différentes).
        val boundary = scrollBoundary.advance()
        val relativeX = axisOf(observation.axisXrelative)
        val relativeY = axisOf(observation.axisYrelative)
        val notchX = axisOf(observation.axisHscroll)
        val notchY = axisOf(observation.axisVscroll)
        val stimuli = ArrayList<SurfaceStimulus>(2)
        if (relativeX != 0.0 || relativeY != 0.0) {
            stimuli += SurfaceStimulus.Scroll(
                surfaceId = surfaceId,
                delta = ScrollDelta.Logical(relativeX, relativeY),
                coalescingBoundary = boundary,
                deviceId = device,
            )
        }
        if (notchX != 0.0 || notchY != 0.0) {
            stimuli += SurfaceStimulus.Scroll(
                surfaceId = surfaceId,
                delta = ScrollDelta.Lines(notchX, notchY),
                coalescingBoundary = boundary,
                deviceId = device,
            )
        }
        if (stimuli.isEmpty()) {
            // Un scroll dont aucun axe n'est observé reste une observation (miroir web : la
            // frontière avance ; le delta porté est le zéro honnête, pas une invention).
            stimuli += SurfaceStimulus.Scroll(
                surfaceId = surfaceId,
                delta = ScrollDelta.Logical(0.0, 0.0),
                coalescingBoundary = boundary,
                deviceId = device,
            )
        }
        return stimuli
    }

    // -----------------------------------------------------------------
    // Outils privés — conversion, sanitisation, genres
    // -----------------------------------------------------------------

    /** L'index du pointeur de l'action, borné aux tableaux réels (jamais hors bornes). */
    private fun actionIndexOf(observation: AndroidMotionObservation, count: Int): Int =
        observation.actionIndex.coerceIn(0, (count - 1).coerceAtLeast(0))

    private fun pointerKindOf(tools: IntArray): PointerKind =
        tools.firstOrNull()?.let(AndroidMotionMapping::pointerKind) ?: PointerKind.Unknown

    /**
     * L'état de stylet d'un pointeur : un payload présent pour un stylet ou une gomme, dont les
     * champs tilt/torsion/pression tangentielle sont `null` tant que cette observation ne les
     * porte pas — jamais inventés ; `null` pour tout autre outil (une souris ne porte jamais
     * d'état de stylet).
     */
    private fun penStateOf(toolType: Int?): PenState? {
        if (toolType != MotionEvent.TOOL_TYPE_STYLUS && toolType != MotionEvent.TOOL_TYPE_ERASER) return null
        return PenState(tiltXDegrees = null, tiltYDegrees = null, twistRadians = null, tangentialPressure = null)
    }

    private fun pressureOf(native: Float?): Double? =
        native?.takeIf { it.isFinite() }?.toDouble()

    /** Un axe de scroll : la valeur finie portée, ou le zéro neutre — un axe non finie n'est pas un fait. */
    private fun axisOf(native: Float): Double = if (native.isFinite()) native.toDouble() else 0.0

    private fun pointerPosition(xs: FloatArray, ys: FloatArray, scaleFactor: Double): LogicalPoint? {
        if (xs.isEmpty() || ys.isEmpty()) return null
        if (!isFinitePosition(xs[0], ys[0])) return null
        return logicalPoint(xs[0], ys[0], scaleFactor)
    }

    private fun logicalPoint(x: Float, y: Float, scaleFactor: Double): LogicalPoint =
        LogicalPoint(x / scaleFactor, y / scaleFactor)

    private fun isFinitePosition(x: Float, y: Float): Boolean = x.isFinite() && y.isFinite()

    /**
     * Le gate des bornes du DOWN : la position physique doit tomber DANS la View — le bord est
     * exclusif (`x == viewWidth` est hors bornes), une position non finie aussi.
     */
    private fun isInsideBounds(x: Float, y: Float, viewWidth: Int, viewHeight: Int): Boolean {
        if (!isFinitePosition(x, y)) return false
        return x >= 0f && y >= 0f && x < viewWidth && y < viewHeight
    }

    /** Le bit bas d'un masque de boutons, ou `null` quand le masque n'en porte aucun. */
    private fun lowestSetBit(mask: Int): Int? =
        if (mask == 0) null else mask and -mask
}
