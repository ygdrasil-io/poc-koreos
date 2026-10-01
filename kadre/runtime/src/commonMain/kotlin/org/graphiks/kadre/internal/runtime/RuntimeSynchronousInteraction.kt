package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.TouchId
import org.graphiks.kadre.surface.LogicalPoint

/** Unstable backend-only callback payload for [RuntimeWindowManager.dispatchSynchronousInteraction]. */
public sealed interface RuntimeSynchronousInteraction {
    public data class PointerPressed(
        public val button: PointerButton,
        public val position: LogicalPoint,
        public val pressure: Double?,
    ) : RuntimeSynchronousInteraction

    public data class KeyPressed(public val physicalKey: PhysicalKey) : RuntimeSynchronousInteraction

    public data class TouchStarted(
        public val touchId: TouchId,
        public val position: LogicalPoint,
    ) : RuntimeSynchronousInteraction
}
