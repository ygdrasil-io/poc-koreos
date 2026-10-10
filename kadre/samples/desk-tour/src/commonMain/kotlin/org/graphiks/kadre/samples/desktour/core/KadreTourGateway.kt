package org.graphiks.kadre.samples.desktour.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
import org.graphiks.kadre.window.Window

/**
 * La moitié commune du gateway (cycle de vie, écrans, entrée, saisie, périphériques,
 * capture) : elle parle l'API publique commune de Kadre et reste partageable entre les
 * cibles. Les notes ne le sont pas — voir [NoteWindowHost] : la fenêtre d'une note est
 * l'affaire du host (fenêtre Kadre sur le bureau, div + viewport sur le web), donc cette
 * moitié-là est déléguée au support de notes que le host injecte.
 */
internal class KadreTourGateway(
    private val scope: KadreScope,
    private val noteWindows: NoteWindowHost,
) : TourGateway {
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
                focusLabel = windowFocusLabel(surfaceState.focus),
                logicalWidth = surfaceState.logicalSize.width,
                logicalHeight = surfaceState.logicalSize.height,
                physicalWidth = surfaceState.physicalSize.width,
                physicalHeight = surfaceState.physicalSize.height,
            )
        }

    override fun createNoteAvailability(): CapabilityPresentation =
        present(scope.windows.state.value.capabilities.requestWindow)

    override suspend fun openNote(): NoteOpenOutcome = noteWindows.openNote()

    override fun noteWindow(key: NoteKey): Window? = noteWindows.noteWindow(key)

    override fun noteControls(key: NoteKey): NoteControls = noteWindows.noteControls(key)

    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = noteWindows.observeNote(key)

    override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome =
        noteWindows.renameNote(key, title)

    override suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome =
        noteWindows.requestNoteAttention(key)

    override suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome =
        noteWindows.toggleNoteDecorations(key)

    override suspend fun closeNote(key: NoteKey): NoteUpdateOutcome = noteWindows.closeNote(key)

    override fun observeNoteCloseRequests(): Flow<NoteKey> = noteWindows.observeNoteCloseRequests()

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
}
