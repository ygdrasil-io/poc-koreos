package org.graphiks.kadre.internal.runtime

import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.KeyLocation
import org.graphiks.kadre.input.KeyboardModifiers
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.PointerKind
import org.graphiks.kadre.input.PenState
import org.graphiks.kadre.input.DeviceId
import org.graphiks.kadre.input.TouchId
import org.graphiks.kadre.surface.LogicalPoint

/** Unstable backend-only callback payload for [RuntimeWindowManager.dispatchSynchronousInteraction]. */
public sealed interface RuntimeSynchronousInteraction {
    public data class PointerPressed(
        public val button: PointerButton,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public val kind: PointerKind = PointerKind.Mouse,
        public val pen: PenState? = null,
        public val nativeIdentity: Any? = null,
        public val deviceId: DeviceId? = null,
    ) : RuntimeSynchronousInteraction

    public data class KeyPressed(
        public val physicalKey: PhysicalKey,
        public val logicalKey: LogicalKey = LogicalKey.Unidentified("interaction-key"),
        public val location: KeyLocation = KeyLocation.Standard,
        public val modifiers: KeyboardModifiers = KeyboardModifiers(emptySet()),
    ) : RuntimeSynchronousInteraction

    public data class TouchStarted(
        public val touchId: TouchId,
        public val position: LogicalPoint,
    ) : RuntimeSynchronousInteraction

    public data class TouchContactStarted(
        public val nativeIdentity: Any,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public val deviceId: DeviceId? = null,
    ) : RuntimeSynchronousInteraction
}
