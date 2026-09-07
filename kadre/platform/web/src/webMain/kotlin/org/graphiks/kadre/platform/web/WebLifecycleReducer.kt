package org.graphiks.kadre.platform.web

import org.graphiks.kadre.application.ActivationState
import org.graphiks.kadre.application.AttachmentState
import org.graphiks.kadre.application.LifecycleState
import org.graphiks.kadre.application.VisibilityState

/** A DOM-free copy of the facts observed by a target-specific browser port. */
internal data class WebLifecycleSnapshot(
    val connected: Boolean,
    val inOriginDocument: Boolean,
    val documentVisible: Boolean,
    val browsingContextFocused: Boolean,
    val subtreeFocused: Boolean,
    val pageHidden: Boolean = false,
)

/** The one-way result of reducing an immutable browser observation. */
internal sealed interface WebLifecycleReduction {
    data class Update(val state: LifecycleState) : WebLifecycleReduction
    data object Terminate : WebLifecycleReduction
}

/**
 * Reduces target observations to the common lifecycle state machine.
 *
 * A terminal observation latches permanently.  Consequently a `pageshow`, a later reconnect,
 * or a late callback cannot resurrect an already terminated session.
 */
internal class WebLifecycleReducer(
    private val attachmentPolicy: WebAttachmentPolicy,
) {
    private var terminated: Boolean = false

    fun reduce(snapshot: WebLifecycleSnapshot): WebLifecycleReduction {
        if (terminated) return WebLifecycleReduction.Terminate
        if (snapshot.pageHidden || !snapshot.inOriginDocument) {
            terminated = true
            return WebLifecycleReduction.Terminate
        }
        if (attachmentPolicy == WebAttachmentPolicy.StopWhenDetached && !snapshot.connected) {
            terminated = true
            return WebLifecycleReduction.Terminate
        }
        if (!snapshot.connected || !snapshot.documentVisible) {
            return WebLifecycleReduction.Update(backgroundInactive())
        }
        return WebLifecycleReduction.Update(
            LifecycleState(
                attachment = AttachmentState.Attached,
                visibility = VisibilityState.Foreground,
                activation = if (snapshot.browsingContextFocused && snapshot.subtreeFocused) {
                    ActivationState.Active
                } else {
                    ActivationState.Inactive
                },
            ),
        )
    }

    private fun backgroundInactive(): LifecycleState = LifecycleState(
        attachment = AttachmentState.Attached,
        visibility = VisibilityState.Background,
        activation = ActivationState.Inactive,
    )
}
