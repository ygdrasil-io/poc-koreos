package org.graphiks.kadre.input

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.surface.LogicalDelta
import org.graphiks.kadre.surface.LogicalPoint
import kotlin.math.PI

public interface SurfaceInput {
    public val events: Flow<InputEvent>
    public val state: StateFlow<SurfaceInputState>
}

public data class SurfaceInputState(
    public val keyboard: KeyboardState,
    public val pointers: List<PointerState>,
    public val touches: List<TouchState>,
    public val modifiers: KeyboardModifiers,
    public val capabilities: InputCapabilities,
    public val revision: InputStateRevision,
) {
    init {
        require(pointers.map(PointerState::id).distinct().size == pointers.size) { "pointer IDs must be unique" }
        require(touches.map(TouchState::id).distinct().size == touches.size) { "touch IDs must be unique" }
    }
}

public enum class ModifierKey { Shift, Control, Alt, Meta, CapsLock, NumLock }
public data class KeyboardModifiers(public val pressed: Set<ModifierKey>)
public enum class KeyState { Pressed, Released }
public enum class KeyLocation { Standard, Left, Right, Numpad }

public sealed interface PhysicalKey {
    public data class Code(public val usagePage: Int, public val usageId: Int) : PhysicalKey {
        init {
            require(usagePage in 0..65_535 && usageId in 0..65_535) { "HID usages must fit unsigned 16-bit values" }
        }
    }

    public data class Unidentified(public val nativeCode: String?) : PhysicalKey {
        init { validateNativeCode(nativeCode) }
    }
}

public sealed interface LogicalKey {
    public data class Character(public val value: String) : LogicalKey {
        init { require(value.isNotEmpty()) { "logical character must not be empty" } }
    }

    public data class Named(public val value: NamedKey) : LogicalKey

    public data class Unidentified(public val nativeCode: String?) : LogicalKey {
        init { validateNativeCode(nativeCode) }
    }
}

public enum class NamedKey {
    Enter,
    Tab,
    Space,
    Backspace,
    Escape,
    Delete,
    Insert,
    Home,
    End,
    PageUp,
    PageDown,
    ArrowLeft,
    ArrowRight,
    ArrowUp,
    ArrowDown,
    Shift,
    Control,
    Alt,
    Meta,
    CapsLock,
    NumLock,
    ContextMenu,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    MediaPlayPause,
    MediaStop,
    MediaNext,
    MediaPrevious,
    VolumeUp,
    VolumeDown,
    VolumeMute,
}

public data class KeyboardState(public val pressedKeys: Set<PhysicalKey>)
public enum class PointerKind { Mouse, Touchpad, Pen, Eraser, Unknown }

public sealed interface PointerButton {
    public data object Primary : PointerButton
    public data object Secondary : PointerButton
    public data object Auxiliary : PointerButton
    public data object Back : PointerButton
    public data object Forward : PointerButton
    public data object Barrel : PointerButton
    public data object Eraser : PointerButton
    public data class Other(public val nativeCode: Int) : PointerButton
}

public enum class PointerButtonState { Pressed, Released }

public class PenState(
    tiltXDegrees: Double?,
    tiltYDegrees: Double?,
    twistRadians: Double?,
    tangentialPressure: Double?,
) {
    public val tiltXDegrees: Double? = canonicalOptionalRange(tiltXDegrees, -90.0, 90.0, "tiltXDegrees")
    public val tiltYDegrees: Double? = canonicalOptionalRange(tiltYDegrees, -90.0, 90.0, "tiltYDegrees")
    public val twistRadians: Double? = twistRadians?.let {
        require(it.isFinite() && it >= 0.0 && it < 2.0 * PI) {
            "twistRadians must be in [0, 2π)"
        }
        if (it == 0.0) 0.0 else it
    }
    public val tangentialPressure: Double? =
        canonicalOptionalRange(tangentialPressure, -1.0, 1.0, "tangentialPressure")

    public operator fun component1(): Double? = tiltXDegrees
    public operator fun component2(): Double? = tiltYDegrees
    public operator fun component3(): Double? = twistRadians
    public operator fun component4(): Double? = tangentialPressure
    public fun copy(
        tiltXDegrees: Double? = this.tiltXDegrees,
        tiltYDegrees: Double? = this.tiltYDegrees,
        twistRadians: Double? = this.twistRadians,
        tangentialPressure: Double? = this.tangentialPressure,
    ): PenState = PenState(tiltXDegrees, tiltYDegrees, twistRadians, tangentialPressure)
    override fun equals(other: Any?): Boolean =
        other is PenState &&
            tiltXDegrees == other.tiltXDegrees &&
            tiltYDegrees == other.tiltYDegrees &&
            twistRadians == other.twistRadians &&
            tangentialPressure == other.tangentialPressure
    override fun hashCode(): Int {
        var result = tiltXDegrees?.hashCode() ?: 0
        result = 31 * result + (tiltYDegrees?.hashCode() ?: 0)
        result = 31 * result + (twistRadians?.hashCode() ?: 0)
        return 31 * result + (tangentialPressure?.hashCode() ?: 0)
    }
    override fun toString(): String =
        "PenState(tiltXDegrees=$tiltXDegrees, tiltYDegrees=$tiltYDegrees, twistRadians=$twistRadians, tangentialPressure=$tangentialPressure)"
}

public data class PointerState private constructor(
    public val id: PointerId,
    public val kind: PointerKind,
    public val position: LogicalPoint?,
    public val pressedButtons: Set<PointerButton>,
    public val pressure: Double?,
    public val pen: PenState?,
    private val canonicalized: Unit,
) {
    public constructor(
        id: PointerId,
        kind: PointerKind,
        position: LogicalPoint?,
        pressedButtons: Set<PointerButton>,
        pressure: Double?,
        pen: PenState?,
    ) : this(id, kind, position, pressedButtons, canonicalPressure(pressure), pen, Unit)

    init {
        require(pen == null || kind == PointerKind.Pen || kind == PointerKind.Eraser) {
            "pen state requires a pen or eraser pointer"
        }
    }

    public fun copy(
        id: PointerId = this.id,
        kind: PointerKind = this.kind,
        position: LogicalPoint? = this.position,
        pressedButtons: Set<PointerButton> = this.pressedButtons,
        pressure: Double? = this.pressure,
        pen: PenState? = this.pen,
    ): PointerState = PointerState(id, kind, position, pressedButtons, pressure, pen)

    override fun toString(): String =
        "PointerState(id=$id, kind=$kind, position=$position, pressedButtons=$pressedButtons, " +
            "pressure=$pressure, pen=$pen)"
}

public data class TouchState private constructor(
    public val id: TouchId,
    public val position: LogicalPoint,
    public val pressure: Double?,
    private val canonicalized: Unit,
) {
    public constructor(id: TouchId, position: LogicalPoint, pressure: Double?) : this(
        id,
        position,
        canonicalPressure(pressure),
        Unit,
    )

    public fun copy(
        id: TouchId = this.id,
        position: LogicalPoint = this.position,
        pressure: Double? = this.pressure,
    ): TouchState = TouchState(id, position, pressure)

    override fun toString(): String = "TouchState(id=$id, position=$position, pressure=$pressure)"
}

public data class InputCapabilities(
    public val keyboard: FeatureAvailability,
    public val pointer: FeatureAvailability,
    public val touch: FeatureAvailability,
    public val gestures: FeatureAvailability,
    public val dragAndDrop: FeatureAvailability,
    public val textInput: Capability<Unit>,
    public val rawInput: Capability<Unit>,
)

public enum class TouchPhase { Started, Moved, Ended, Cancelled }
public enum class GestureKind { Pan, Pinch, Rotation, DoubleTap, TouchpadPressure }

public sealed interface ScrollDelta {
    public data class Logical private constructor(
        public val x: Double,
        public val y: Double,
        private val canonicalized: Unit,
    ) : ScrollDelta {
        public constructor(x: Double, y: Double) : this(
            canonicalFinite(x, "scroll x"),
            canonicalFinite(y, "scroll y"),
            Unit,
        )

        public fun copy(x: Double = this.x, y: Double = this.y): Logical = Logical(x, y)
        override fun toString(): String = "Logical(x=$x, y=$y)"
    }

    public data class Lines private constructor(
        public val x: Double,
        public val y: Double,
        private val canonicalized: Unit,
    ) : ScrollDelta {
        public constructor(x: Double, y: Double) : this(
            canonicalFinite(x, "scroll x"),
            canonicalFinite(y, "scroll y"),
            Unit,
        )

        public fun copy(x: Double = this.x, y: Double = this.y): Lines = Lines(x, y)
        override fun toString(): String = "Lines(x=$x, y=$y)"
    }
}

public sealed interface InputEvent {
    public val stamp: EventStamp
    public val deviceId: DeviceId?
    public val stateRevision: InputStateRevision

    public data class Key(
        public val physicalKey: PhysicalKey,
        public val logicalKey: LogicalKey,
        public val location: KeyLocation,
        public val keyState: KeyState,
        public val repeat: Boolean,
        public val modifiers: KeyboardModifiers,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class PointerEntered(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class PointerLeft(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val lastPosition: LogicalPoint?,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class PointerMoved private constructor(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val position: LogicalPoint,
        public val delta: LogicalDelta,
        public val pressure: Double?,
        public val pen: PenState?,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
        private val canonicalized: Unit,
    ) : InputEvent {
        public constructor(
            pointerId: PointerId,
            kind: PointerKind,
            position: LogicalPoint,
            delta: LogicalDelta,
            pressure: Double?,
            pen: PenState?,
            stamp: EventStamp,
            deviceId: DeviceId?,
            stateRevision: InputStateRevision,
        ) : this(
            pointerId,
            kind,
            position,
            delta,
            canonicalPressure(pressure),
            pen,
            stamp,
            deviceId,
            stateRevision,
            Unit,
        )

        init { validatePointerPayload(kind, pressure, pen) }

        public fun copy(
            pointerId: PointerId = this.pointerId,
            kind: PointerKind = this.kind,
            position: LogicalPoint = this.position,
            delta: LogicalDelta = this.delta,
            pressure: Double? = this.pressure,
            pen: PenState? = this.pen,
            stamp: EventStamp = this.stamp,
            deviceId: DeviceId? = this.deviceId,
            stateRevision: InputStateRevision = this.stateRevision,
        ): PointerMoved = PointerMoved(
            pointerId,
            kind,
            position,
            delta,
            pressure,
            pen,
            stamp,
            deviceId,
            stateRevision,
        )

        override fun toString(): String =
            "PointerMoved(pointerId=$pointerId, kind=$kind, position=$position, delta=$delta, " +
                "pressure=$pressure, pen=$pen, stamp=$stamp, deviceId=$deviceId, stateRevision=$stateRevision)"
    }

    public data class PointerButtonChanged private constructor(
        public val pointerId: PointerId,
        public val kind: PointerKind,
        public val button: PointerButton,
        public val buttonState: PointerButtonState,
        public val position: LogicalPoint,
        public val pressure: Double?,
        public val pen: PenState?,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
        private val canonicalized: Unit,
    ) : InputEvent {
        public constructor(
            pointerId: PointerId,
            kind: PointerKind,
            button: PointerButton,
            buttonState: PointerButtonState,
            position: LogicalPoint,
            pressure: Double?,
            pen: PenState?,
            stamp: EventStamp,
            deviceId: DeviceId?,
            stateRevision: InputStateRevision,
        ) : this(
            pointerId,
            kind,
            button,
            buttonState,
            position,
            canonicalPressure(pressure),
            pen,
            stamp,
            deviceId,
            stateRevision,
            Unit,
        )

        init { validatePointerPayload(kind, pressure, pen) }

        public fun copy(
            pointerId: PointerId = this.pointerId,
            kind: PointerKind = this.kind,
            button: PointerButton = this.button,
            buttonState: PointerButtonState = this.buttonState,
            position: LogicalPoint = this.position,
            pressure: Double? = this.pressure,
            pen: PenState? = this.pen,
            stamp: EventStamp = this.stamp,
            deviceId: DeviceId? = this.deviceId,
            stateRevision: InputStateRevision = this.stateRevision,
        ): PointerButtonChanged = PointerButtonChanged(
            pointerId,
            kind,
            button,
            buttonState,
            position,
            pressure,
            pen,
            stamp,
            deviceId,
            stateRevision,
        )

        override fun toString(): String =
            "PointerButtonChanged(pointerId=$pointerId, kind=$kind, button=$button, " +
                "buttonState=$buttonState, position=$position, pressure=$pressure, pen=$pen, " +
                "stamp=$stamp, deviceId=$deviceId, stateRevision=$stateRevision)"
    }

    public data class Scrolled(
        public val delta: ScrollDelta,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class TouchChanged private constructor(
        public val touchId: TouchId,
        public val phase: TouchPhase,
        public val position: LogicalPoint,
        public val pressure: Double?,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
        private val canonicalized: Unit,
    ) : InputEvent {
        public constructor(
            touchId: TouchId,
            phase: TouchPhase,
            position: LogicalPoint,
            pressure: Double?,
            stamp: EventStamp,
            deviceId: DeviceId?,
            stateRevision: InputStateRevision,
        ) : this(touchId, phase, position, canonicalPressure(pressure), stamp, deviceId, stateRevision, Unit)

        public fun copy(
            touchId: TouchId = this.touchId,
            phase: TouchPhase = this.phase,
            position: LogicalPoint = this.position,
            pressure: Double? = this.pressure,
            stamp: EventStamp = this.stamp,
            deviceId: DeviceId? = this.deviceId,
            stateRevision: InputStateRevision = this.stateRevision,
        ): TouchChanged = TouchChanged(touchId, phase, position, pressure, stamp, deviceId, stateRevision)

        override fun toString(): String =
            "TouchChanged(touchId=$touchId, phase=$phase, position=$position, pressure=$pressure, " +
                "stamp=$stamp, deviceId=$deviceId, stateRevision=$stateRevision)"
    }

    public data class Gesture private constructor(
        public val kind: GestureKind,
        public val phase: TouchPhase,
        public val delta: LogicalDelta?,
        public val scale: Double?,
        public val rotationRadians: Double?,
        public val pressure: Double?,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
        private val canonicalized: Unit,
    ) : InputEvent {
        public constructor(
            kind: GestureKind,
            phase: TouchPhase,
            delta: LogicalDelta?,
            scale: Double?,
            rotationRadians: Double?,
            pressure: Double?,
            stamp: EventStamp,
            deviceId: DeviceId?,
            stateRevision: InputStateRevision,
        ) : this(
            kind,
            phase,
            delta,
            canonicalOptionalFinite(scale, "scale"),
            canonicalOptionalFinite(rotationRadians, "rotationRadians"),
            canonicalPressure(pressure),
            stamp,
            deviceId,
            stateRevision,
            Unit,
        )

        init { validateGesture(kind, delta, scale, rotationRadians, pressure) }

        public fun copy(
            kind: GestureKind = this.kind,
            phase: TouchPhase = this.phase,
            delta: LogicalDelta? = this.delta,
            scale: Double? = this.scale,
            rotationRadians: Double? = this.rotationRadians,
            pressure: Double? = this.pressure,
            stamp: EventStamp = this.stamp,
            deviceId: DeviceId? = this.deviceId,
            stateRevision: InputStateRevision = this.stateRevision,
        ): Gesture = Gesture(
            kind,
            phase,
            delta,
            scale,
            rotationRadians,
            pressure,
            stamp,
            deviceId,
            stateRevision,
        )

        override fun toString(): String =
            "Gesture(kind=$kind, phase=$phase, delta=$delta, scale=$scale, " +
                "rotationRadians=$rotationRadians, pressure=$pressure, stamp=$stamp, " +
                "deviceId=$deviceId, stateRevision=$stateRevision)"
    }

    public data class DropEntered(
        public val offer: DropOffer,
        public val position: LogicalPoint,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class DropMoved(
        public val offerId: DropOfferId,
        public val position: LogicalPoint,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class DropExited(
        public val offerId: DropOfferId,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class Dropped(
        public val offer: DropOffer,
        public val position: LogicalPoint,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent

    public data class StateReset(
        public val reason: InputStateResetReason,
        override val stamp: EventStamp,
        override val deviceId: DeviceId?,
        override val stateRevision: InputStateRevision,
    ) : InputEvent
}

public enum class InputStateResetReason { FocusLost, DeviceDisconnected, PermissionRevoked }

private fun validateNativeCode(value: String?) {
    require(value == null || value.isNotEmpty() && value.length <= 256 && value.all { it.code in 0x21..0x7e }) {
        "nativeCode must be a non-empty ASCII identifier of at most 256 code units"
    }
}

private fun validateOptionalRange(value: Double?, minimum: Double, maximum: Double, name: String) {
    require(value == null || value.isFinite() && value in minimum..maximum) {
        "$name must be finite and in [$minimum, $maximum]"
    }
}

private fun canonicalOptionalRange(value: Double?, minimum: Double, maximum: Double, name: String): Double? {
    validateOptionalRange(value, minimum, maximum, name)
    return if (value == 0.0) 0.0 else value
}

private fun canonicalPressure(value: Double?): Double? =
    canonicalOptionalRange(value, 0.0, 1.0, "pressure")

private fun canonicalFinite(value: Double, name: String): Double {
    require(value.isFinite()) { "$name must be finite" }
    return if (value == 0.0) 0.0 else value
}

private fun canonicalOptionalFinite(value: Double?, name: String): Double? =
    value?.let { canonicalFinite(it, name) }

private fun validatePointerPayload(kind: PointerKind, pressure: Double?, pen: PenState?) {
    validateOptionalRange(pressure, 0.0, 1.0, "pressure")
    require(pen == null || kind == PointerKind.Pen || kind == PointerKind.Eraser) {
        "pen state requires a pen or eraser pointer"
    }
}

private fun validateGesture(
    kind: GestureKind,
    delta: LogicalDelta?,
    scale: Double?,
    rotationRadians: Double?,
    pressure: Double?,
) {
    val valid = when (kind) {
        GestureKind.Pan -> delta != null && scale == null && rotationRadians == null && pressure == null
        GestureKind.Pinch -> delta == null && scale != null && scale.isFinite() && scale > 0.0 && rotationRadians == null && pressure == null
        GestureKind.Rotation -> delta == null && scale == null && rotationRadians != null && rotationRadians.isFinite() && pressure == null
        GestureKind.TouchpadPressure -> delta == null && scale == null && rotationRadians == null && pressure != null && pressure.isFinite() && pressure in 0.0..1.0
        GestureKind.DoubleTap -> delta == null && scale == null && rotationRadians == null && pressure == null
    }
    require(valid) { "gesture payload does not match kind" }
}
