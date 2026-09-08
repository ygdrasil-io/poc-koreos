package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.Display
import org.graphiks.kadre.display.DisplayCapabilities
import org.graphiks.kadre.display.DisplayConnectionState
import org.graphiks.kadre.display.DisplayEvent
import org.graphiks.kadre.display.DisplayId
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.display.DisplayManager
import org.graphiks.kadre.display.DisplayManagerRevision
import org.graphiks.kadre.display.DisplayManagerState
import org.graphiks.kadre.display.DisplayMode
import org.graphiks.kadre.display.DisplayModeId
import org.graphiks.kadre.display.DisplayRevision
import org.graphiks.kadre.display.DisplayState
/** One ephemeral backend target derived from the manager's current display inventory. */
/** Unstable backend target derived from the manager's current display inventory. */
public data class ExclusiveDisplayTarget(
    public val displayKey: Long,
    public val modeKey: Long,
)

/** Resolves a public fullscreen request to the current opaque backend display target. */
public fun interface ExclusiveDisplayTargetResolver {
    /** A failure is terminal for the requested target and must be propagated without reservation. */
    public fun resolveExclusiveTarget(displayId: DisplayId, mode: DisplayMode): KadreResult<ExclusiveDisplayTarget>
}

/** Session-owned projection of complete backend display snapshots. */
internal class RuntimeDisplayManager(
    private val port: DisplayPort,
    private val eventStampSource: () -> EventStamp,
    collectorAllocator: RuntimeEventCollectorAllocator,
    maxCollectorsPerFlow: Int,
    private val platform: KadrePlatform = KadrePlatform.Fake,
) : DisplayManager, AutoCloseable, ExclusiveDisplayTargetResolver {
    private val lock = RuntimeLock()
    private val displaysByKey = linkedMapOf<Long, RuntimeDisplay>()
    private var closed = false
    private var observation: AutoCloseable? = null
    private val mutableState = MutableStateFlow(initialState(port.enumerationCapability))
    private val mutableEvents = MutableSharedFlow<DisplayEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

    override val state: StateFlow<DisplayManagerState> = mutableState.asStateFlow()
    override val events: Flow<DisplayEvent> = mutableEvents.asSharedFlow().withEventCollectorAdmission(
        collectorAllocator.newGate(maxCollectorsPerFlow),
    )

    init {
        observation = port.installSnapshotObserver(::acceptObservation)
    }

    override suspend fun requestAccess(): KadreResult<DisplayManagerState> {
        if (lock.withLock { closed }) {
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Display))
        }
        return when (val requested = port.requestSnapshot()) {
            is KadreResult.Failure -> {
                acceptFailure(requested.reason)
                requested
            }

            is KadreResult.Success -> {
                acceptSnapshot(requested.value)
                KadreResult.Success(state.value)
            }
        }
    }

    override fun resolveExclusiveTarget(
        displayId: DisplayId,
        mode: DisplayMode,
    ): KadreResult<ExclusiveDisplayTarget> = lock.withLock {
        if (closed) {
            return@withLock KadreResult.Failure(
                KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Display),
            )
        }
        if (mutableState.value.inventory !is DisplayInventory.Enumerated) {
            return@withLock KadreResult.Failure(
                KadreFailure.TemporarilyUnavailable(retryable = true),
            )
        }
        val entry = displaysByKey.entries.firstOrNull { (_, display) -> display.id == displayId }
            ?: return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("fullscreen"))
        val display = entry.value
        if (display.state.value.connection != DisplayConnectionState.Connected) {
            return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("fullscreen"))
        }
        val modeKey = display.modeKeyFor(mode)
            ?: return@withLock KadreResult.Failure(KadreFailure.InvalidRequest("fullscreen"))
        KadreResult.Success(ExclusiveDisplayTarget(entry.key, modeKey))
    }

    private fun acceptObservation(observation: KadreResult<DisplayPortSnapshot>) {
        when (observation) {
            is KadreResult.Success -> acceptSnapshot(observation.value)
            is KadreResult.Failure -> acceptFailure(observation.reason)
        }
    }

    override fun close() {
        val toClose = lock.withLock {
            if (closed) return
            closed = true
            observation.also { observation = null }
        }
        toClose?.close()
    }

    private fun acceptSnapshot(snapshot: DisplayPortSnapshot) {
        val publications = try {
            lock.withLock {
                if (closed) return
                reconcileLocked(snapshot)
            }
        } catch (_: IllegalArgumentException) {
            lock.withLock {
                publishUnavailableLocked(KadreFailure.PlatformFailure(platform, "display", "invalid-snapshot"))
            }
            return
        }
        publications.forEach { event -> mutableEvents.tryEmit(event) }
    }

    private fun acceptFailure(failure: KadreFailure) {
        lock.withLock {
            if (!closed) publishUnavailableLocked(failure)
        }
    }

    private fun reconcileLocked(snapshot: DisplayPortSnapshot): List<DisplayEvent> {
        validateSnapshot(snapshot)
        val incoming = snapshot.displays.associateBy(DisplayPortDisplay::key)
        val removed = displaysByKey.keys.filter { it !in incoming }
        val added = mutableListOf<RuntimeDisplay>()
        val changed = mutableListOf<RuntimeDisplay>()
        val removedEvents = mutableListOf<Pair<DisplayId, DisplayState>>()

        removed.forEach { key ->
            val display = checkNotNull(displaysByKey.remove(key))
            removedEvents += display.disconnect()
        }
        snapshot.displays.forEach { source ->
            val existing = displaysByKey[source.key]
            if (existing == null) {
                RuntimeDisplay(RuntimeProcessIds.nextDisplayId(), source).also { display ->
                    displaysByKey[source.key] = display
                    added += display
                }
            } else if (existing.update(source)) {
                changed += existing
            }
        }

        val inventory = DisplayInventory.Enumerated(
            primary = snapshot.primaryKey?.let(displaysByKey::get),
            displays = snapshot.displays.map { source -> checkNotNull(displaysByKey[source.key]) },
        )
        val previous = mutableState.value
        val membershipChanged = previous.inventory != inventory
        val displayChanged = added.isNotEmpty() || changed.isNotEmpty() || removedEvents.isNotEmpty()
        if (!membershipChanged && !displayChanged) return emptyList()

        val nextRevision = nextManagerRevision(previous.revision)
        val next = DisplayManagerState(
            inventory = inventory,
            capabilities = DisplayCapabilities(port.enumerationCapability),
            revision = nextRevision,
        )
        mutableState.value = next
        val stamp = eventStampSource()
        return buildList {
            added.forEach { display ->
                add(DisplayEvent.Added(display, display.state.value, nextRevision, stamp))
            }
            changed.forEach { display ->
                add(DisplayEvent.Changed(display, display.state.value, nextRevision, stamp))
            }
            removedEvents.forEach { (id, state) ->
                add(DisplayEvent.Removed(id, state, nextRevision, stamp))
            }
        }
    }

    private fun publishUnavailableLocked(failure: KadreFailure) {
        val previous = mutableState.value
        if (previous.inventory == DisplayInventory.Unavailable(failure)) return
        displaysByKey.values.forEach(RuntimeDisplay::disconnect)
        displaysByKey.clear()
        mutableState.value = DisplayManagerState(
            inventory = DisplayInventory.Unavailable(failure),
            capabilities = DisplayCapabilities(port.enumerationCapability),
            revision = nextManagerRevision(previous.revision),
        )
    }

    private fun validateSnapshot(snapshot: DisplayPortSnapshot) {
        require(snapshot.displays.map(DisplayPortDisplay::key).distinct().size == snapshot.displays.size) {
            "backend display keys must be unique"
        }
        require(snapshot.primaryKey == null || snapshot.displays.any { it.key == snapshot.primaryKey }) {
            "backend primary display must be present"
        }
        snapshot.displays.forEach { display ->
            require(display.key >= 0L) { "backend display key must be non-negative" }
            require(display.modes.map(DisplayPortMode::key).distinct().size == display.modes.size) {
                "backend display mode keys must be unique"
            }
            require(display.currentModeKey == null || display.modes.any { it.key == display.currentModeKey }) {
                "backend current mode must be present"
            }
            require(display.scaleFactor.isFinite() && display.scaleFactor > 0.0) {
                "backend display scale factor must be finite and positive"
            }
            display.modes.forEach { mode ->
                require(mode.key >= 0L) { "backend display mode key must be non-negative" }
                require(mode.refreshRateHz == null || mode.refreshRateHz.isFinite() && mode.refreshRateHz > 0.0) {
                    "backend display refresh rate must be finite and positive"
                }
                require(mode.bitDepth == null || mode.bitDepth > 0) { "backend display bit depth must be positive" }
            }
        }
    }

    private fun initialState(capability: Capability<Unit>): DisplayManagerState {
        val unavailable = when (capability) {
            is Capability.Unsupported -> capability.failure
            is Capability.Supported -> KadreFailure.TemporarilyUnavailable(retryable = true)
        }
        return DisplayManagerState(
            inventory = DisplayInventory.Unavailable(unavailable),
            capabilities = DisplayCapabilities(capability),
            revision = DisplayManagerRevision(0),
        )
    }

    private fun nextManagerRevision(current: DisplayManagerRevision): DisplayManagerRevision =
        DisplayManagerRevision(Math.incrementExact(current.value))

    private inner class RuntimeDisplay(
        override val id: DisplayId,
        initial: DisplayPortDisplay,
    ) : Display {
        private val modeIds = linkedMapOf<Long, DisplayModeId>()
        private val mutableState = MutableStateFlow(stateFor(initial, DisplayRevision(0)))

        override val state: StateFlow<DisplayState> = mutableState.asStateFlow()

        fun update(source: DisplayPortDisplay): Boolean {
            val previous = mutableState.value
            val candidate = stateFor(source, previous.revision)
            if (candidate == previous) return false
            mutableState.value = candidate.copy(revision = nextDisplayRevision(previous.revision))
            return true
        }

        fun disconnect(): Pair<DisplayId, DisplayState> {
            val previous = mutableState.value
            if (previous.connection != DisplayConnectionState.Disconnected) {
                mutableState.value = previous.copy(
                    connection = DisplayConnectionState.Disconnected,
                    revision = nextDisplayRevision(previous.revision),
                )
            }
            return id to mutableState.value
        }

        fun modeKeyFor(mode: DisplayMode): Long? {
            val registered = mutableState.value.modes.firstOrNull { it.id == mode.id } ?: return null
            if (registered != mode) return null
            return modeIds.entries.firstOrNull { (_, id) -> id == mode.id }?.key
        }

        private fun stateFor(source: DisplayPortDisplay, revision: DisplayRevision): DisplayState {
            modeIds.keys.retainAll(source.modes.map(DisplayPortMode::key).toSet())
            val modes = source.modes.map { mode ->
                DisplayMode(
                    id = modeIds.getOrPut(mode.key) { RuntimeProcessIds.nextDisplayModeId() },
                    physicalSize = mode.physicalSize,
                    refreshRateHz = mode.refreshRateHz,
                    bitDepth = mode.bitDepth,
                )
            }
            return DisplayState(
                type = source.type,
                connection = DisplayConnectionState.Connected,
                name = source.name,
                bounds = source.bounds,
                workArea = source.workArea,
                scaleFactor = source.scaleFactor,
                currentMode = source.currentModeKey?.let { key -> modes.first { it.id == modeIds[key] } },
                modes = modes,
                revision = revision,
            )
        }
    }

    private fun nextDisplayRevision(current: DisplayRevision): DisplayRevision =
        DisplayRevision(Math.incrementExact(current.value))

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 32
    }
}
