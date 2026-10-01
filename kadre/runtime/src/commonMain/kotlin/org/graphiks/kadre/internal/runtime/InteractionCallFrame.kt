package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.surface.SurfaceId

/**
 * The surface whose interaction callback is running on the current dispatch frame.
 *
 * [RuntimeInteractionHandler] reads the frame to enforce the `WrongSurface` rule: a context
 * retained past its callback may only request while the frame still belongs to its own surface.
 * This is the common replacement for the `ThreadLocal<SurfaceId?>` the handler used before the
 * lift, and the frame state is shared by every handler of one thread (or one event loop), exactly
 * as the static `ThreadLocal` was: a nested dispatch on another surface overwrites the frame,
 * which is what keeps that retained context `WrongSurface` during the nested callback.
 *
 * Per target:
 * - jvm: a shared `ThreadLocal<SurfaceId?>`, unchanged from the pre-lift handler.
 * - js, wasmJs: one shared field. Both targets are mono-threaded and the DOM never re-enters
 *   synchronously, so a single field carries the frame the same way a `ThreadLocal` does per
 *   thread.
 */
internal expect class InteractionCallFrame() {
    /** The surface of the innermost running interaction callback, or `null` outside one. */
    internal fun current(): SurfaceId?

    /** Marks [surfaceId] as the surface of the running callback for this dispatch frame. */
    internal fun set(surfaceId: SurfaceId?)

    /** Clears the frame when the restored previous surface is absent. */
    internal fun clear()
}
