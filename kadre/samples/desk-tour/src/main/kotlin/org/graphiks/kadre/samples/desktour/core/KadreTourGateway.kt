package org.graphiks.kadre.samples.desktour.core

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayInventory
import org.graphiks.kadre.input.DeviceConnectionState
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.window.Window
import org.graphiks.kadre.window.WindowAttention
import org.graphiks.kadre.window.WindowCloseDecision
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowDecorations
import org.graphiks.kadre.window.WindowEvent
import org.graphiks.kadre.window.WindowProperty
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowUpdate
import org.graphiks.kadre.window.WindowUpdateOutcome
import org.graphiks.kadre.window.requestWindow

internal class KadreTourGateway(private val scope: KadreScope) : TourGateway {
    private val notes = mutableMapOf<NoteKey, Window>()
    private val nextKey = AtomicLong(0L)
    private val noteCloseRequests = MutableSharedFlow<NoteKey>(extraBufferCapacity = 8)
    /** La démo n'a qu'une surface primaire : le gateway retient celle qu'on lui fait observer. */
    private var primarySurface: HostSurface? = null
    private var textInputSession: TextInputSession? = null
    private val lastTextInputEvent = MutableStateFlow<String?>(null)
    private val textInputPresentation = MutableStateFlow(
        TextInputPresentation(open = false, stateLabel = textInputStateLabel(TextInputSessionState.Closed), lastEvent = null),
    )

    override fun lifecycleSummary(): Flow<String> =
        scope.lifecycle.state.map { state ->
            "Session : ${state.attachment} / ${state.visibility} / ${state.activation}"
        }

    override fun observeWindow(window: Window): Flow<DeskTourWindow> =
        combine(window.state, window.surface.state) { windowState, surfaceState ->
            DeskTourWindow(
                title = windowState.title,
                focusLabel = surfaceState.focus.name,
                logicalWidth = surfaceState.logicalSize.width,
                logicalHeight = surfaceState.logicalSize.height,
                physicalWidth = surfaceState.physicalSize.width,
                physicalHeight = surfaceState.physicalSize.height,
            )
        }

    override fun createNoteAvailability(): CapabilityPresentation =
        present(scope.windows.state.value.capabilities.requestWindow)

    override suspend fun openNote(): NoteOpenOutcome {
        val request = when (val result = scope.windows.requestWindow { title = "Notes" }) {
            is KadreResult.Success -> result.value
            is KadreResult.Failure -> return noteOpenOutcomeFor(result.reason)
        }
        return when (val outcome = request.await()) {
            is WindowRequestOutcome.OpenedHere -> {
                val opened = outcome.window
                val key = NoteKey(nextKey.incrementAndGet())
                notes[key] = opened
                // Le bouton rouge du système passe par le même chemin que l'action de la
                // démo : on accepte, et on ne publie la clé qu'après une acceptation réussie.
                scope.launch {
                    opened.events.filterIsInstance<WindowEvent.CloseRequested>().collect { closeRequest ->
                        val answered = opened.respondToCloseRequest(closeRequest.requestId, WindowCloseDecision.Accept)
                        if (answered is KadreResult.Success) {
                            notes.remove(key)
                            noteCloseRequests.emit(key)
                        }
                    }
                }
                NoteOpenOutcome.Opened(DeskTourNote(key, opened.state.value.title, noteControls(key)))
            }
            is WindowRequestOutcome.Rejected -> noteOpenOutcomeFor(outcome.failure)
            is WindowRequestOutcome.OpenedInNewSession -> NoteOpenOutcome.OpenedElsewhere
            WindowRequestOutcome.Cancelled -> NoteOpenOutcome.Cancelled
            WindowRequestOutcome.RequesterDetached -> NoteOpenOutcome.Cancelled
        }
    }

    override fun noteWindow(key: NoteKey): Window? = notes[key]

    override fun noteControls(key: NoteKey): NoteControls = notes[key]?.let { window ->
        val capabilities = window.capabilities.value
        val rename = present(capabilities.title)
        val attention = present(capabilities.attention)
        val decorations = present(capabilities.decorations)
        NoteControls(
            canRename = rename.enabled,
            canRequestAttention = attention.enabled,
            canChangeDecorations = decorations.enabled,
            // Aucune capability publique ne conditionne la fermeture : elle est donc toujours
            // offerte, et c'est le refus éventuel du host qui portera le motif.
            canClose = true,
            motifs = listOfNotNull(
                rename.motif?.let { "Renommer — $it" },
                attention.motif?.let { "Attention — $it" },
                decorations.motif?.let { "Décoration — $it" },
            ),
        )
    } ?: NoteControls(false, false, false, false, emptyList())

    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = notes[key]?.let { window ->
        combine(window.state, window.surface.state) { state, _ ->
            DeskTourNote(key, state.title, noteControls(key))
        }
    } ?: flowOf()

    override fun observeNoteCloseRequests(): Flow<NoteKey> = noteCloseRequests

    override fun observeDisplays(): Flow<DisplayPresentation> =
        scope.displays.state.map { state ->
            when (val inventory = state.inventory) {
                is DisplayInventory.Enumerated -> DisplayPresentation.Enumerated(
                    inventory.displays.map { display ->
                        val displayState = display.state.value
                        screenEntryOf(
                            name = displayState.name,
                            width = displayState.bounds.size.width,
                            height = displayState.bounds.size.height,
                            scale = displayState.scaleFactor,
                            mode = displayState.currentMode?.let { mode ->
                                DisplayModeLabel(
                                    mode.physicalSize.width,
                                    mode.physicalSize.height,
                                    mode.refreshRateHz,
                                )
                            },
                            isPrimary = display.id == inventory.primary?.id,
                        )
                    },
                )
                DisplayInventory.PermissionRequired -> DisplayPresentation.NeedsPermission
                is DisplayInventory.PermissionDenied -> DisplayPresentation.Denied(inventory.canRequestAgain)
                is DisplayInventory.Unavailable -> displayPresentationFor(inventory.failure)
            }
        }

    override fun displayAccessAvailability(): CapabilityPresentation =
        present(scope.displays.state.value.capabilities.enumeration)

    override suspend fun requestDisplayAccess(): DisplayPresentation =
        when (val result = scope.displays.requestAccess()) {
            is KadreResult.Failure -> displayPresentationFor(result.reason)
            is KadreResult.Success -> observeDisplays().first()
        }

    override fun observeInput(surface: HostSurface): Flow<InputPresentation> {
        primarySurface = surface
        // Flux froid : aucun collecteur n'est lancé tant que personne ne collecte. Appeler
        // cette fonction ne consomme donc pas de bail de collecteur chez le host, et rien ne
        // survit à l'annulation du collecteur.
        val events = flow<String?> {
            emit(null)
            surface.input.events.collect { emit(renderEventSummary(describeInputEvent(it))) }
        }
        return combine(surface.input.state, events) { state, event ->
            val keyboardPublished = state.capabilities.keyboard is FeatureAvailability.Available
            InputPresentation(
                // Un compteur n'affirme que ce que le host a publié (spec §4.4, §8.4).
                modifiers = if (keyboardPublished) modifierLabels(state.modifiers) else null,
                pointers = pointerSummaries(
                    state.pointers.map { pointer ->
                        PointerSnapshot(
                            kind = pointer.kind,
                            x = pointer.position?.x,
                            y = pointer.position?.y,
                            buttons = pointer.pressedButtons.toList(),
                        )
                    },
                ),
                pressedKeyCount = if (keyboardPublished) state.keyboard.pressedKeys.size else null,
                lastEvent = event,
                features = inputFeatures(state.capabilities),
            )
        }
    }

    override fun observeDevices(): Flow<DevicePresentation> =
        scope.devices.state.map { state ->
            devicePresentationOf(state.inventory) { enumerated ->
                enumerated.devices.map { device ->
                    deviceEntryOf(
                        name = device.descriptor.name,
                        kind = device.descriptor.kind,
                        connected = device.connection.value == DeviceConnectionState.Connected,
                    )
                }
            }
        }

    override fun observeCapture(): Flow<CapturePresentation> =
        scope.capture.state.map { state ->
            CapturePresentation(
                screenLabel = permissionLabel(state.permissions.screen),
                windowLabel = permissionLabel(state.permissions.window),
                canRequest = present(state.capabilities.screen).requestable,
            )
        }

    override fun textInputAvailability(): CapabilityPresentation =
        primarySurface?.let { present(it.input.state.value.capabilities.textInput) }
            ?: CapabilityPresentation(false, "Aucune surface n'a encore été observée.")

    override fun observeTextInput(): Flow<TextInputPresentation> = textInputPresentation

    override suspend fun openTextInput(): TextInputOutcome {
        val surface = primarySurface ?: return TextInputOutcome.Refused("Aucune surface n'a encore été observée.")
        if (textInputSession != null) {
            return TextInputOutcome.Refused("Une session de saisie est déjà ouverte.")
        }
        return when (val result = surface.input.openTextInput(TextInputConfig())) {
            is KadreResult.Failure -> TextInputOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> {
                val session = result.value
                textInputSession = session
                scope.launch {
                    session.events.collect {
                        lastTextInputEvent.value = renderTextInputEvent(describeTextInputEvent(it))
                        textInputPresentation.value = textInputPresentation.value.copy(
                            lastEvent = lastTextInputEvent.value,
                        )
                    }
                }
                scope.launch {
                    session.state.collect { state ->
                        val state2 = textInputSessionStateOf(state)
                        textInputPresentation.value = TextInputPresentation(
                            open = state2 != TextInputSessionState.Closed,
                            stateLabel = textInputStateLabel(state2),
                            lastEvent = lastTextInputEvent.value,
                        )
                    }
                }
                TextInputOutcome.Opened
            }
        }
    }

    override suspend fun closeTextInput() {
        textInputSession?.close()
        textInputSession = null
        textInputPresentation.value = TextInputPresentation(
            open = false,
            stateLabel = textInputStateLabel(TextInputSessionState.Closed),
            lastEvent = lastTextInputEvent.value,
        )
    }

    override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        return when (val result = window.apply(WindowUpdate(title = PropertyChange.Set(title)))) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> when (val outcome = result.value) {
                is WindowUpdateOutcome.Applied -> NoteUpdateOutcome.Applied
                is WindowUpdateOutcome.Accepted -> NoteUpdateOutcome.Accepted
                is WindowUpdateOutcome.PartiallyApplied -> NoteUpdateOutcome.PartiallyApplied(
                    // Un renommage n'envoie que le titre : `applied` ne doit donc jamais
                    // annoncer les treize autres propriétés, qu'on n'a pas demandées.
                    applied = if (outcome.rejected.any { it.field == WindowProperty.Title }) {
                        emptyList()
                    } else {
                        listOf(WindowProperty.Title.readableName())
                    },
                    rejected = outcome.rejected.map {
                        RejectedFieldPresentation(it.field.readableName(), it.failure.userMotif())
                    },
                )
            }
        }
    }

    override suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        return when (val result = window.requestAttention(WindowAttention.Informational)) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> NoteUpdateOutcome.Applied
        }
    }

    override suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        val next = if (window.state.value.decorations == WindowDecorations.System) {
            WindowDecorations.Borderless
        } else {
            WindowDecorations.System
        }
        return when (val result = window.apply(WindowUpdate(decorations = PropertyChange.Set(next)))) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> when (val outcome = result.value) {
                is WindowUpdateOutcome.Applied -> NoteUpdateOutcome.Applied
                is WindowUpdateOutcome.Accepted -> NoteUpdateOutcome.Accepted
                is WindowUpdateOutcome.PartiallyApplied -> NoteUpdateOutcome.PartiallyApplied(
                    applied = emptyList(),
                    rejected = outcome.rejected.map {
                        RejectedFieldPresentation(it.field.readableName(), it.failure.userMotif())
                    },
                )
            }
        }
    }

    override suspend fun closeNote(key: NoteKey): NoteUpdateOutcome {
        val window = notes[key] ?: return NoteUpdateOutcome.Refused("Cette note n'existe plus.")
        return when (val result = window.close()) {
            is KadreResult.Failure -> NoteUpdateOutcome.Refused(result.reason.userMotif())
            is KadreResult.Success -> when (result.value) {
                WindowCloseOutcome.Closed -> {
                    notes.remove(key)
                    NoteUpdateOutcome.Applied
                }
                is WindowCloseOutcome.Accepted -> NoteUpdateOutcome.Accepted
            }
        }
    }
}
