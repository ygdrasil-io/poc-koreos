package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.interaction.InteractionAction

/**
 * The terminal result a native backend produces for one [InteractionAction] inside the
 * [RuntimeInteractionHandler] dispatch frame.
 *
 * [Now] is the synchronous answer: the action committed or was refused by the time the native
 * call returned, and the handler publishes the outcome in the dispatch `finally`, unchanged from
 * the pre-lift behaviour. [Deferred] is the asynchronous answer a browser primitive needs: the
 * action was already emitted synchronously inside the frame (the transient-activation window),
 * and the outcome is terminal only when the browser confirms or refuses the primitive later —
 * [RuntimeInteractionHandler.completePending] publishes it then, through the
 * [Deferred.complete] callback the backend keeps for its own terminal notification. A deferred
 * request occupies `maxPendingInteractionRequests` until that terminal callback fires.
 */
internal sealed interface NativeInteractionOutcome {
    class Now(val result: KadreResult<Unit>) : NativeInteractionOutcome

    class Deferred(val complete: (committed: Boolean, failure: KadreFailure?) -> Unit) : NativeInteractionOutcome
}
