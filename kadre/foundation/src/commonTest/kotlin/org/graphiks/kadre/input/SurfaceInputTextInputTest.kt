package org.graphiks.kadre.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.LogicalRect
import kotlin.test.Test
import kotlin.test.assertEquals

class SurfaceInputTextInputTest {
    @Test
    fun surfaceInputMemberDelegatesTextInputOpeningToItsRuntime() = runTest {
        val session = TestTextInputSession()
        val input = object : SurfaceInput {
            override val events = emptyFlow<InputEvent>()
            override val state = MutableStateFlow(testInputState())

            override suspend fun openTextInput(config: TextInputConfig): KadreResult<TextInputSession> =
                KadreResult.Success(session)
        }

        assertEquals(KadreResult.Success(session), input.openTextInput(TextInputConfig()))
    }
}

private class TestTextInputSession : TextInputSession {
    override val events = emptyFlow<TextInputEvent>()
    override val state = MutableStateFlow<TextInputState>(TextInputState.Active(TextDocumentRevision(0), null))

    override fun close() = Unit

    override suspend fun updateCursor(
        rect: LogicalRect,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit> = KadreResult.Success(Unit)

    override suspend fun updateSurroundingText(
        text: String,
        selection: TextRange,
        documentRevision: TextDocumentRevision,
    ): KadreResult<Unit> = KadreResult.Success(Unit)
}

private fun testInputState(): SurfaceInputState = SurfaceInputState(
    keyboard = KeyboardState(emptySet()),
    pointers = emptyList(),
    touches = emptyList(),
    modifiers = KeyboardModifiers(emptySet()),
    capabilities = InputCapabilities(
        keyboard = FeatureAvailability.Available,
        pointer = FeatureAvailability.Available,
        touch = FeatureAvailability.Unsupported,
        gestures = FeatureAvailability.Unsupported,
        dragAndDrop = FeatureAvailability.Unsupported,
        textInput = Capability.Supported(Unit, FeatureAvailability.Available),
        rawInput = Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RawInputAccess)),
    ),
    revision = InputStateRevision(0),
)
