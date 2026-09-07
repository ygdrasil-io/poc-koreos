package org.graphiks.kadre.platform.web

import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.VisibilityState
import kotlin.test.Test
import kotlin.test.assertEquals

class WebLifecycleReducerTest {
    @Test
    fun stopWhenDetachedKeepsASameDocumentReinsertedHostAlive() {
        val reducer = WebLifecycleReducer(WebAttachmentPolicy.StopWhenDetached)
        val attached = snapshot()

        assertEquals(WebLifecycleReduction.Update(foregroundActive()), reducer.reduce(attached))
        assertEquals(WebLifecycleReduction.Update(foregroundActive()), reducer.reduce(attached))
    }

    @Test
    fun stopWhenDetachedTerminatesForADurableDetachOrDocumentTransfer() {
        val reducer = WebLifecycleReducer(WebAttachmentPolicy.StopWhenDetached)

        assertEquals(WebLifecycleReduction.Terminate, reducer.reduce(snapshot(connected = false)))
        assertEquals(WebLifecycleReduction.Terminate, reducer.reduce(snapshot(inOriginDocument = false)))
    }

    @Test
    fun manualKeepsDisconnectedHostAttachedButBackgroundAndInactive() {
        val reducer = WebLifecycleReducer(WebAttachmentPolicy.Manual)

        assertEquals(
            WebLifecycleReduction.Update(backgroundInactive()),
            reducer.reduce(snapshot(connected = false)),
        )
    }

    @Test
    fun manualStillTerminatesForADocumentTransfer() {
        val reducer = WebLifecycleReducer(WebAttachmentPolicy.Manual)

        assertEquals(WebLifecycleReduction.Terminate, reducer.reduce(snapshot(inOriginDocument = false)))
    }

    @Test
    fun visibilityAndFocusReduceToDeterministicForegroundAndActiveStates() {
        val reducer = WebLifecycleReducer(WebAttachmentPolicy.Manual)

        assertEquals(
            WebLifecycleReduction.Update(backgroundInactive()),
            reducer.reduce(snapshot(documentVisible = false)),
        )
        assertEquals(
            WebLifecycleReduction.Update(foregroundInactive()),
            reducer.reduce(snapshot(browsingContextFocused = false)),
        )
        assertEquals(
            WebLifecycleReduction.Update(foregroundInactive()),
            reducer.reduce(snapshot(subtreeFocused = false)),
        )
        assertEquals(WebLifecycleReduction.Update(foregroundActive()), reducer.reduce(snapshot()))
    }

    @Test
    fun pagehideIsTerminalAndCannotBeReducedBackToAnAttachedState() {
        val reducer = WebLifecycleReducer(WebAttachmentPolicy.Manual)

        assertEquals(WebLifecycleReduction.Terminate, reducer.reduce(snapshot(pageHidden = true)))
        assertEquals(WebLifecycleReduction.Terminate, reducer.reduce(snapshot()))
    }

    private fun snapshot(
        connected: Boolean = true,
        inOriginDocument: Boolean = true,
        documentVisible: Boolean = true,
        browsingContextFocused: Boolean = true,
        subtreeFocused: Boolean = true,
        pageHidden: Boolean = false,
    ): WebLifecycleSnapshot = WebLifecycleSnapshot(
        connected = connected,
        inOriginDocument = inOriginDocument,
        documentVisible = documentVisible,
        browsingContextFocused = browsingContextFocused,
        subtreeFocused = subtreeFocused,
        pageHidden = pageHidden,
    )

    private fun foregroundActive(): LifecycleState = LifecycleState(
        AttachmentState.Attached,
        VisibilityState.Foreground,
        ActivationState.Active,
    )

    private fun foregroundInactive(): LifecycleState = LifecycleState(
        AttachmentState.Attached,
        VisibilityState.Foreground,
        ActivationState.Inactive,
    )

    private fun backgroundInactive(): LifecycleState = LifecycleState(
        AttachmentState.Attached,
        VisibilityState.Background,
        ActivationState.Inactive,
    )
}
