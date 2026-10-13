package org.graphiks.kadre.platform.android

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Le port display Android (Task 1, phase 7) contre un fake du seam [AndroidDisplaySource] —
 * JAMAIS le SDK (le stub android.jar lance à la construction ; la couture `Any` existe pour
 * cela, cf. `AndroidDisplayPort`). Les huit cas jouent les six du plan plus le miroir exact
 * de `WebDisplayPortTest` sur l'install :
 *
 * 1. `initialSnapshotEnumeratesRealDisplaysWithNullWorkArea` — l'inventaire COMPLET à la
 *    demande : primaire = le display par défaut (DEFAULT_DISPLAY, pas le premier énuméré),
 *    `workArea` JAMAIS inventée (Android n'a pas de bureau), bounds = la taille réelle à
 *    l'origine (pas de position bureau fabriquée), modes traduits (refreshRateHz observé,
 *    bitDepth `null` quand le SDK ne le rapporte pas — Unknown honnête) ;
 * 2. `listenerEventsRepublishSnapshots` — l'inventaire initial publié à l'install, JAMAIS
 *    d'événement avant, puis added/changed/removed du fake republient l'inventaire relit
 *    EN ENTIER (le port ne fabrique pas de delta) ;
 * 3. `keysAreStableAcrossSnapshots` — le displayId garde la même clé de corrélation à travers
 *    deux snapshots, quel que soit l'ordre d'énumération ;
 * 4. `virtualDisplaysAreTypedVirtual` — FLAG_PRESENTATION/FLAG_PRIVATE/TYPE_OVERLAY
 *    (traduits `isVirtual` par le seam) → `DisplayType.Virtual` ; le défaut → `Physical` ;
 * 5. `unmeasurableSnapshotIsTemporaryUnavailable` — source vide OU display imesurable →
 *    `TemporarilyUnavailable(retryable=true)` à la demande ET via l'observer, l'inventaire
 *    précédent RETIRÉ (miroir web :156-159) ;
 * 6. `enumerationCapabilityIsSupported` — `Supported(Unit, Available)` dès la construction ;
 * 7. `closeUnregistersExactlyOnceAndClosesItsSource` — `close()` désenregistre le listener
 *    SDK exactement une fois, ferme la source possédée, et un fire tardif ne publie rien ;
 * 8. `secondInstallPublishesToItsOwnObserverAndReplacesTheFirst` — le miroir web : le
 *    remplacement publie à son propre observateur, le handle périmé devient un no-op, le
 *    handle vivant est celui qui se désabonne.
 */
class AndroidDisplayPortTest {

    /** Le double du seam SDK : un inventaire pilotable et des fires manuels — jamais de SDK. */
    private class FakeDisplaySource : AndroidDisplaySource {
        val listeners = mutableListOf<(Any) -> Unit>()
        var unregisterCount = 0
        var closed = false
        var inventory: List<AndroidDisplayInfo> = emptyList()
        private val tokens = HashMap<Long, Any>()

        /** Le jeton display d'un id — stable pour la vie du test, comme le displayId du SDK. */
        private fun token(key: Long): Any = tokens.getOrPut(key) { Any() }

        override fun displays(): List<Any> = inventory.map { token(it.key) }

        override fun displayInfo(display: Any): AndroidDisplayInfo =
            inventory.single { token(it.key) === display }

        override fun registerListener(listener: (Any) -> Unit) {
            listeners += listener
        }

        override fun unregisterListener(listener: (Any) -> Unit) {
            unregisterCount += 1
            listeners.remove(listener)
        }

        override fun close() {
            closed = true
        }
    }

    /** Le display par défaut du fake — le DEFAULT_DISPLAY (0) qu'un téléphone énumère toujours. */
    private fun integratedDisplay(
        realWidth: Int = 1080,
        realHeight: Int = 2400,
        densityDpi: Int = 420,
        modes: List<AndroidDisplayModeInfo> = listOf(
            AndroidDisplayModeInfo(PhysicalSize(1080, 2400), refreshRateHz = 120.0, bitDepth = null, modeId = 1),
            AndroidDisplayModeInfo(PhysicalSize(1080, 2400), refreshRateHz = 60.0, bitDepth = null, modeId = 2),
        ),
        currentModeId: Int? = 1,
    ): AndroidDisplayInfo = AndroidDisplayInfo(
        key = 0L,
        isVirtual = false,
        name = "Écran intégré",
        realWidth = realWidth,
        realHeight = realHeight,
        scaleFactor = densityDpi / 160.0,
        modes = modes,
        currentModeId = currentModeId,
    )

    /** Un second display physique — un HDMI externe, énuméré en tête pour casser l'ordre. */
    private fun hdmiDisplay(): AndroidDisplayInfo = AndroidDisplayInfo(
        key = 2L,
        isVirtual = false,
        name = "HDMI",
        realWidth = 3840,
        realHeight = 2160,
        scaleFactor = 1.0,
        modes = listOf(
            AndroidDisplayModeInfo(PhysicalSize(3840, 2160), refreshRateHz = 60.0, bitDepth = 10, modeId = 7),
        ),
        currentModeId = 7,
    )

    /** Un display virtuel — l'overlay de présentation FLAG_PRIVATE/TYPE_OVERLAY de la règle. */
    private fun overlayDisplay(): AndroidDisplayInfo = AndroidDisplayInfo(
        key = 3L,
        isVirtual = true,
        name = null,
        realWidth = 1280,
        realHeight = 720,
        scaleFactor = 1.0,
        modes = listOf(
            AndroidDisplayModeInfo(PhysicalSize(1280, 720), refreshRateHz = 60.0, bitDepth = null, modeId = 11),
        ),
        currentModeId = 11,
    )

    private fun snapshotOf(result: KadreResult<DisplayPortSnapshot>): DisplayPortSnapshot =
        assertIs<KadreResult.Success<DisplayPortSnapshot>>(result).value

    // -----------------------------------------------------------------------
    // Cas 1 — l'inventaire complet, honnête, à la demande
    // -----------------------------------------------------------------------

    @Test
    fun initialSnapshotEnumeratesRealDisplaysWithNullWorkArea() = runBlocking {
        val source = FakeDisplaySource()
        source.inventory = listOf(hdmiDisplay(), integratedDisplay())
        val port = AndroidDisplayPort(source)

        val snapshot = snapshotOf(port.requestSnapshot())
        assertEquals(0L, snapshot.primaryKey, "le primaire est le display par défaut, pas le premier énuméré")
        assertEquals(2, snapshot.displays.size)

        val primary = snapshot.displays.single { it.key == 0L }
        assertEquals(DisplayType.Physical, primary.type)
        assertEquals("Écran intégré", primary.name)
        assertEquals(PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1080, 2400)), primary.bounds)
        assertEquals(null, primary.workArea, "Android n'a pas de bureau — la workArea n'est jamais inventée")
        assertEquals(2.625, primary.scaleFactor, "densityDpi/160, observé")
        assertEquals(1L, primary.currentModeKey)
        assertEquals(2, primary.modes.size)
        val high = primary.modes.single { it.key == 1L }
        assertEquals(PhysicalSize(1080, 2400), high.physicalSize)
        assertEquals(120.0, high.refreshRateHz, "le refresh observé du SDK, non fabriqué")
        assertEquals(null, high.bitDepth, "le SDK ne rapporte pas la profondeur — Unknown honnête")
        val low = primary.modes.single { it.key == 2L }
        assertEquals(60.0, low.refreshRateHz)

        val external = snapshot.displays.single { it.key == 2L }
        assertEquals(PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(3840, 2160)), external.bounds)
        assertEquals(null, external.workArea)
        assertEquals(10, external.modes.single().bitDepth, "observé quand le SDK le rapporte")
    }

    // -----------------------------------------------------------------------
    // Cas 2 — l'inventaire initial à l'install, puis chaque fire relit TOUT
    // -----------------------------------------------------------------------

    @Test
    fun listenerEventsRepublishSnapshots() = runBlocking {
        val source = FakeDisplaySource()
        source.inventory = listOf(integratedDisplay())
        val port = AndroidDisplayPort(source)
        val received = mutableListOf<KadreResult<DisplayPortSnapshot>>()

        // Jamais d'événement avant l'install : le port ne s'abonne à la source qu'à l'install.
        assertTrue(source.listeners.isEmpty())
        port.installSnapshotObserver { received += it }
        assertEquals(1, received.size, "l'install publie l'inventaire courant, exactement une fois")
        assertEquals(1, snapshotOf(received.single()).displays.size)
        assertEquals(1, source.listeners.size)

        // Added — le port RELIT l'inventaire complet du SDK : la traduction entière repart.
        source.inventory = listOf(integratedDisplay(), hdmiDisplay())
        source.listeners.single().invoke(Any())
        assertEquals(2, received.size)
        val afterAdd = snapshotOf(received[1])
        assertEquals(2, afterAdd.displays.size)
        assertEquals(0L, afterAdd.primaryKey)

        // Changed — la traduction porte la NOUVELLE mesure (jamais un delta inventé).
        source.inventory = listOf(
            integratedDisplay(
                realWidth = 1440,
                realHeight = 3200,
                modes = listOf(
                    AndroidDisplayModeInfo(PhysicalSize(1440, 3200), refreshRateHz = 120.0, bitDepth = null, modeId = 1),
                ),
            ),
            hdmiDisplay(),
        )
        source.listeners.single().invoke(Any())
        assertEquals(3, received.size)
        val afterChange = snapshotOf(received[2])
        assertEquals(
            PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(1440, 3200)),
            afterChange.displays.single { it.key == 0L }.bounds,
        )

        // Removed — le display retiré du SDK disparaît du snapshot suivant.
        source.inventory = listOf(integratedDisplay())
        source.listeners.single().invoke(Any())
        assertEquals(4, received.size)
        val afterRemove = snapshotOf(received[3])
        assertEquals(1, afterRemove.displays.size)
        assertEquals(0L, afterRemove.displays.single().key)
    }

    // -----------------------------------------------------------------------
    // Cas 3 — le displayId est la clé de corrélation, stable à vie
    // -----------------------------------------------------------------------

    @Test
    fun keysAreStableAcrossSnapshots() = runBlocking {
        val source = FakeDisplaySource()
        source.inventory = listOf(hdmiDisplay(), integratedDisplay())
        val port = AndroidDisplayPort(source)

        val first = snapshotOf(port.requestSnapshot())
        assertEquals(listOf(2L, 0L), first.displays.map { it.key }, "la clé = le displayId, à travers l'ordre")

        // Un fire qui réordonne l'énumération et change les modes : les displayIds ne bougent
        // pas, donc les clés non plus — le port ne remint JAMAIS une clé.
        source.inventory = listOf(
            integratedDisplay(
                modes = listOf(
                    AndroidDisplayModeInfo(PhysicalSize(1080, 2400), refreshRateHz = 60.0, bitDepth = null, modeId = 2),
                ),
                currentModeId = 2,
            ),
            hdmiDisplay(),
        )
        val second = snapshotOf(port.requestSnapshot())

        assertEquals(listOf(0L, 2L), second.displays.map { it.key })
        assertEquals(
            first.displays.associate { it.key to it.type },
            second.displays.associate { it.key to it.type },
        )
        assertEquals(
            first.displays.associate { it.key to it.name },
            second.displays.associate { it.key to it.name },
        )
    }

    // -----------------------------------------------------------------------
    // Cas 4 — FLAG_PRESENTATION/FLAG_PRIVATE/TYPE_OVERLAY → Virtual
    // -----------------------------------------------------------------------

    @Test
    fun virtualDisplaysAreTypedVirtual() = runBlocking {
        val source = FakeDisplaySource()
        source.inventory = listOf(integratedDisplay(), overlayDisplay())
        val port = AndroidDisplayPort(source)

        val snapshot = snapshotOf(port.requestSnapshot())
        assertEquals(DisplayType.Physical, snapshot.displays.single { it.key == 0L }.type)
        assertEquals(DisplayType.Virtual, snapshot.displays.single { it.key == 3L }.type)
        assertEquals(null, snapshot.displays.single { it.key == 3L }.name, "le nom absent du SDK reste absent")
    }

    // -----------------------------------------------------------------------
    // Cas 5 — inmesurable = TemporarilyUnavailable, l'inventaire précédent retiré
    // -----------------------------------------------------------------------

    @Test
    fun unmeasurableSnapshotIsTemporaryUnavailable() = runBlocking {
        val source = FakeDisplaySource()
        val port = AndroidDisplayPort(source)

        val direct = assertIs<KadreResult.Failure>(port.requestSnapshot())
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), direct.reason)

        val received = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        port.installSnapshotObserver { received += it }
        assertEquals(1, received.size, "l'install livre l'échec honnête d'un inventaire vide")
        assertEquals(
            KadreFailure.TemporarilyUnavailable(retryable = true),
            assertIs<KadreResult.Failure>(received.single()).reason,
        )

        // L'inventaire s'allume : le fire suivant le porte, entier.
        source.inventory = listOf(integratedDisplay())
        source.listeners.single().invoke(Any())
        assertEquals(1, snapshotOf(received[1]).displays.size)

        // La source redevient vide : l'inventaire PRÉCÉDENT est retiré — jamais exposé comme
        // si la ré-énumération échouée restait autoritative (miroir web :156-159).
        source.inventory = emptyList()
        source.listeners.single().invoke(Any())
        assertEquals(3, received.size)
        assertEquals(
            KadreFailure.TemporarilyUnavailable(retryable = true),
            assertIs<KadreResult.Failure>(received[2]).reason,
        )

        // Un display IMESURABLE (dimensions non positives) échoue l'inventaire ENTIER —
        // un snapshot partiel n'est jamais une réponse.
        source.inventory = listOf(integratedDisplay(realWidth = 0))
        val broken = assertIs<KadreResult.Failure>(port.requestSnapshot())
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), broken.reason)
    }

    // -----------------------------------------------------------------------
    // Cas 6 — le DisplayManager est structurellement observable
    // -----------------------------------------------------------------------

    @Test
    fun enumerationCapabilityIsSupported() {
        val port = AndroidDisplayPort(FakeDisplaySource())
        assertEquals(Capability.Supported(Unit, FeatureAvailability.Available), port.enumerationCapability)
    }

    // -----------------------------------------------------------------------
    // Miroir web — close() désenregistre exactement une fois, ferme sa source
    // -----------------------------------------------------------------------

    @Test
    fun closeUnregistersExactlyOnceAndClosesItsSource() = runBlocking {
        val source = FakeDisplaySource()
        source.inventory = listOf(integratedDisplay())
        val port = AndroidDisplayPort(source)
        val received = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        port.installSnapshotObserver { received += it }
        assertEquals(1, received.size, "l'install publie l'inventaire courant")
        val lateFire = source.listeners.single()

        port.close()
        port.close()

        assertEquals(1, source.unregisterCount, "le désenregistrement SDK arrive exactement une fois")
        assertTrue(source.listeners.isEmpty(), "le listener du port est parti de la source")
        assertTrue(source.closed, "le port ferme la source qu'il possède")
        lateFire(Any())
        assertEquals(1, received.size, "un fire tardif après close ne publie rien")
    }

    // -----------------------------------------------------------------------
    // Miroir web — un second install remplace le premier
    // -----------------------------------------------------------------------

    @Test
    fun secondInstallPublishesToItsOwnObserverAndReplacesTheFirst() = runBlocking {
        val source = FakeDisplaySource()
        source.inventory = listOf(integratedDisplay())
        val port = AndroidDisplayPort(source)

        val first = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        val firstHandle = port.installSnapshotObserver { first += it }
        val second = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        val secondHandle = port.installSnapshotObserver { second += it }

        assertEquals(1, first.size, "le premier install a été servi SA publication initiale")
        assertEquals(1, second.size, "le remplacement publie à SON observateur, exactement une fois")

        source.inventory = listOf(integratedDisplay(), hdmiDisplay())
        source.listeners.single().invoke(Any())
        assertEquals(1, first.size, "un install remplacé est répondu rien de plus")
        assertEquals(2, snapshotOf(second.last()).displays.size)

        // Le handle périmé reste appelable et devient un no-op : il ne désabonne JAMAIS
        // l'install qui l'a remplacé, qui continue de recevoir les fires de la source.
        firstHandle.close()
        source.inventory = listOf(integratedDisplay())
        source.listeners.single().invoke(Any())
        assertEquals(3, second.size, "l'install vivant reçoit encore les fires après le no-op du périmé")

        // Le handle VIVANT est celui qui se désabonne : aucun listener ne lui survit.
        secondHandle.close()
        assertTrue(source.listeners.isEmpty(), "le handle vivant a retiré l'abonnement du port")
    }
}
