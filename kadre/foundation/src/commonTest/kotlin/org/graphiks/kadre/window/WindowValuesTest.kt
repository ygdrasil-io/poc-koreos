package org.graphiks.kadre.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.LogicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowValuesTest {
    @Test
    fun directWindowSpecRejectsContradictorySizes() {
        assertFailsWith<IllegalArgumentException> {
            WindowSpec(
                contentSize = LogicalSize(800.0, 600.0),
                minimumSize = LogicalSize(900.0, 500.0),
                maximumSize = LogicalSize(1000.0, 700.0),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LogicalSizeRange(
                minimum = LogicalSize(100.0, 100.0),
                maximum = LogicalSize(50.0, 100.0),
                increments = null,
            )
        }
    }

    @Test
    fun builderUsesWindowSpecDefaultsAndDelegates() = runTest {
        val manager = RecordingWindowManager()

        val result = manager.requestWindow {
            title = "Kadre"
            contentSize = LogicalSize(1280.0, 720.0)
        }

        assertEquals(KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host)), result)
        assertEquals(WindowSpec().copy(title = "Kadre", contentSize = LogicalSize(1280.0, 720.0)), manager.received)
    }

    @Test
    fun builderContradictionReturnsInvalidRequestWithoutDelegating() = runTest {
        val manager = RecordingWindowManager()

        val result = manager.requestWindow {
            minimumSize = LogicalSize(900.0, 700.0)
            maximumSize = LogicalSize(800.0, 600.0)
        }

        assertEquals(KadreResult.Failure(KadreFailure.InvalidRequest("sizeConstraints")), result)
        assertEquals(null, manager.received)
    }

    private class RecordingWindowManager : WindowManager {
        private val managerState = MutableStateFlow(
            WindowManagerState(
                primary = null,
                windows = emptyList(),
                capabilities = WindowManagerCapabilities(
                    Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
                ),
                revision = WindowManagerRevision(0),
            ),
        )
        override val state: StateFlow<WindowManagerState> = managerState
        var received: WindowSpec? = null

        override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest> {
            received = spec
            return KadreResult.Failure(KadreFailure.Closed(org.graphiks.kadre.diagnostics.KadreResourceKind.Host))
        }
    }
}
