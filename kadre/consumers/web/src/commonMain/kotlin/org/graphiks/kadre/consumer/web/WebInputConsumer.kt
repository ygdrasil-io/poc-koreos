package org.graphiks.kadre.consumer.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputCapabilities
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.InputStateResetReason
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.input.RawInputAccess
import org.graphiks.kadre.input.ScrollDelta
import org.graphiks.kadre.input.SurfaceInputState
import org.graphiks.kadre.input.TextInputConfig
import org.graphiks.kadre.input.TextInputSession
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.SurfaceCapabilities
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome

/**
 * Consumer compile test of the input surface Web Phase 3 activated, shared by both DOM targets.
 *
 * Nothing here reaches an internal: every value is read through the published API of
 * `org.graphiks.kadre:kadre` — `HostSurface.input`, `SurfaceInput.state`, `SurfaceInput.events`,
 * `InputCapabilities`, `SurfaceCapabilities` and the two `SurfaceUpdate` fields this phase turned
 * into promises — and the two `actual` declarations of the target source sets are the only places
 * that need the SDK element to attach. The exhaustive `when`s below are the load-bearing part: a new
 * `InputEvent`, `FeatureAvailability`, `Capability`, `SurfaceUpdateOutcome` or `SurfaceProperty`
 * variant, a renamed property, a nullability change or a lost opt-in stops this consumer from
 * compiling, which is what `:kadre:validateWebKotlinConsumer` (a dependency of `:kadre:check`)
 * asserts.
 *
 * The promises themselves are written out as exact values rather than paraphrases, so a reader can
 * check this file against `kadre/capabilities/web.md` §2 line by line. They are *not* executed by
 * this build — the consumer is compiled and not run — so what the compile proves is that a host can
 * read and name these values; the behavioural proof of the same values is `BCK-003`'s twelve browser
 * scenarios and the platform's own `webTest` suites (`kadre/contracts/driver/web/README.md`).
 */
public object WebInputConsumer {
    /** The pressed-key, modifier and pointer snapshot of a surface, exactly as it is published. */
    public fun inputState(surface: HostSurface): StateFlow<SurfaceInputState> = surface.input.state

    /** The input observations of a surface: one event per observation, ended by the lane's closure. */
    public fun inputEvents(surface: HostSurface): Flow<InputEvent> = surface.input.events

    /**
     * The first observation of a surface, collected from the published flow.
     *
     * Collecting is what this pins: `events` is a real `Flow`, so a host may suspend on it, and the
     * subscription it installs is the published one.
     */
    public suspend fun firstInputEvent(surface: HostSurface): InputEvent = surface.input.events.first()

    /** The same stream, rendered through the exhaustive description of [describeInputEvent]. */
    public fun eventDescriptions(surface: HostSurface): Flow<String> =
        surface.input.events.map(::describeInputEvent)

    /** The input capabilities of the current snapshot, without collecting anything. */
    public fun capabilitiesOf(surface: HostSurface): InputCapabilities = surface.input.state.value.capabilities

    /** The field capabilities of the same surface, read from its own flow. */
    public fun surfaceCapabilitiesOf(surface: HostSurface): SurfaceCapabilities = surface.capabilities.value

    /**
     * Every member of the closed event union, one by one.
     *
     * The `when` has no `else` on purpose: the union is closed, so a variant added to it makes this
     * consumer fail to compile instead of being lost silently.
     */
    public fun describeInputEvent(event: InputEvent): String {
        val subject: String = when (event) {
            is InputEvent.Key -> "key:" + describePhysicalKey(event.physicalKey) + "/" +
                describeLogicalKey(event.logicalKey) + ":" + event.location + ":" + event.keyState +
                ":repeat=" + event.repeat + ":modifiers=" + event.modifiers.pressed.size

            is InputEvent.PointerEntered -> "pointerEntered:" + event.pointerId + ":" + event.kind + ":" +
                describePosition(event.position)

            is InputEvent.PointerLeft -> "pointerLeft:" + event.pointerId + ":" + event.kind + ":" +
                (event.lastPosition?.let(::describePosition) ?: "no-position")

            is InputEvent.PointerMoved -> "pointerMoved:" + event.pointerId + ":" + event.kind + ":" +
                describePosition(event.position) + ":delta=" + event.delta.x + "," + event.delta.y +
                ":pressure=" + (event.pressure != null) + ":pen=" + (event.pen != null)

            is InputEvent.PointerButtonChanged -> "pointerButtonChanged:" + event.pointerId + ":" + event.kind +
                ":" + describePointerButton(event.button) + ":" + event.buttonState + ":" +
                describePosition(event.position) + ":pressure=" + (event.pressure != null)

            is InputEvent.Scrolled -> "scrolled:" + describeScrollDelta(event.delta)
            is InputEvent.TouchChanged -> "touchChanged:" + event.touchId + ":" + event.phase + ":" +
                describePosition(event.position) + ":pressure=" + (event.pressure != null)

            is InputEvent.Gesture -> "gesture:" + event.kind + ":" + event.phase +
                ":delta=" + (event.delta != null) + ":scale=" + (event.scale != null) +
                ":rotation=" + (event.rotationRadians != null) + ":pressure=" + (event.pressure != null)

            is InputEvent.DropEntered -> "dropEntered:" + event.offer.id + ":" + describePosition(event.position)
            is InputEvent.DropMoved -> "dropMoved:" + event.offerId + ":" + describePosition(event.position)
            is InputEvent.DropExited -> "dropExited:" + event.offerId
            is InputEvent.Dropped -> "dropped:" + event.offer.id + ":" + describePosition(event.position)
            is InputEvent.StateReset -> "stateReset:" + describeResetReason(event.reason)
        }
        return subject + ":revision=" + event.stateRevision.value + ":device=" + (event.deviceId != null)
    }

    /** Every member of the closed availability union. */
    public fun describeAvailability(availability: FeatureAvailability): String = when (availability) {
        FeatureAvailability.Available -> "available"
        FeatureAvailability.Unsupported -> "unsupported"
        is FeatureAvailability.RequiresPermission -> "requiresPermission:" + availability.permission
        is FeatureAvailability.RequiresInteraction -> "requiresInteraction:" + availability.kind
        is FeatureAvailability.Unavailable -> "unavailable:" + WebConsumer.describeFailure(availability.failure)
    }

    /** Both members of `Capability`, and both halves of the supported one. */
    public fun describeCapability(capability: Capability<*>): String = when (capability) {
        is Capability.Unsupported -> "unsupported:" + WebConsumer.describeFailure(capability.failure)
        is Capability.Supported<*> -> "supported:" + describeAvailability(capability.availability) + ":" +
            describeConstraints(capability.constraints)
    }

    /** Every member of the closed update-outcome union, with the rejected fields it carries. */
    public fun describeUpdateOutcome(outcome: SurfaceUpdateOutcome): String = when (outcome) {
        is SurfaceUpdateOutcome.Applied -> "applied:revision=" + outcome.state.revision.value
        is SurfaceUpdateOutcome.PartiallyApplied ->
            "partiallyApplied:" + outcome.rejected.joinToString(",") { rejected ->
                describeSurfaceProperty(rejected.field) + "=" + WebConsumer.describeFailure(rejected.failure)
            }
    }

    /** The four fields of `SurfaceUpdate`, in the order the update writes them. */
    public fun describeSurfaceProperty(property: SurfaceProperty): String = when (property) {
        SurfaceProperty.Cursor -> "cursor"
        SurfaceProperty.PointerCapture -> "pointerCapture"
        SurfaceProperty.HitTesting -> "hitTesting"
        SurfaceProperty.InputDefaultBehavior -> "inputDefaultBehavior"
    }

    /** The whole published input snapshot, destructured in its declared order. */
    public fun describeInputState(state: SurfaceInputState): String {
        val (keyboard, pointers, touches, modifiers, capabilities, revision) = state
        return "keys=" + keyboard.pressedKeys.size +
            ":modifiers=" + modifiers.pressed.joinToString("|") { modifier -> modifier.name } +
            ":pointers=" + pointers.joinToString("+") { pointer ->
                pointer.id.toString() + "/" + pointer.kind + "/" +
                    pointer.pressedButtons.joinToString("|") { button -> describePointerButton(button) } +
                    "/position=" + (pointer.position?.let(::describePosition) ?: "no-position") +
                    "/pressure=" + (pointer.pressure != null) + "/pen=" + (pointer.pen != null)
            } +
            ":touches=" + touches.size +
            ":capabilities=" + describeInputCapabilities(capabilities) +
            ":revision=" + revision.value
    }

    /** The seven input capabilities of `SurfaceInputState`, each named as the register names it. */
    public fun describeInputCapabilities(capabilities: InputCapabilities): String =
        "keyboard=" + describeAvailability(capabilities.keyboard) +
            ":pointer=" + describeAvailability(capabilities.pointer) +
            ":touch=" + describeAvailability(capabilities.touch) +
            ":gestures=" + describeCapability(capabilities.gestures) +
            ":dragAndDrop=" + describeAvailability(capabilities.dragAndDrop) +
            ":textInput=" + describeCapability(capabilities.textInput) +
            ":rawInput=" + describeCapability(capabilities.rawInput)

    /**
     * The input capabilities an attached Web surface must publish, as exact values.
     *
     * `keyboard` and `pointer` are `Available` because the installation of their observers is
     * structural and happens once, at the end of the session configuration; receiving a keystroke
     * still depends on the host's element being focusable, which is a host responsibility and not a
     * capability. `touch`, `gestures`, `dragAndDrop`, `textInput` and `rawInput` are absent
     * structurally, each with the value its own type publishes — an `Unsupported` availability, or a
     * `Capability.Unsupported` carrying the exact failure. A mismatch is returned as a finding, never
     * silently accepted.
     */
    public fun unhonouredInputPromises(capabilities: InputCapabilities): List<String> {
        // The ingress-overflow arm is the register's documented second absence state
        // (`capabilities/web.md` §2.1): on the terminal arm of a discrete or buffered overflow the
        // four passive capabilities are `Unavailable(SourceOverflow(InputSource))` instead of
        // `Unsupported`, and that is a value the register names, not a broken promise.
        if (isIngressOverflowArm(capabilities)) return emptyList()
        val findings = mutableListOf<String>()
        if (capabilities.keyboard != FeatureAvailability.Available) {
            findings += "keyboard must be Available after installation, was " +
                describeAvailability(capabilities.keyboard)
        }
        if (capabilities.pointer != FeatureAvailability.Available) {
            findings += "pointer must be Available after installation, was " +
                describeAvailability(capabilities.pointer)
        }
        if (capabilities.touch != FeatureAvailability.Unsupported) {
            findings += "touch must be Unsupported, was " + describeAvailability(capabilities.touch)
        }
        if (capabilities.gestures != unsupportedCapability(KadreOperation.GestureInput)) {
            findings += "gestures must be Unsupported(GestureInput), was " +
                describeCapability(capabilities.gestures)
        }
        if (capabilities.dragAndDrop != FeatureAvailability.Unsupported) {
            findings += "dragAndDrop must be Unsupported, was " + describeAvailability(capabilities.dragAndDrop)
        }
        if (capabilities.textInput != unsupportedCapability(KadreOperation.TextInput)) {
            findings += "textInput must be Unsupported(TextInput), was " +
                describeCapability(capabilities.textInput)
        }
        if (capabilities.rawInput != unsupportedCapability(KadreOperation.RawInputAccess)) {
            findings += "rawInput must be Unsupported(RawInputAccess), was " +
                describeCapability(capabilities.rawInput)
        }
        return findings
    }

    /**
     * The field capabilities an attached Web surface must publish, as exact values.
     *
     * `pointerCapture` promises exactly `None` and `Confined` — `Locked` needs a transient activation
     * and belongs to a later phase — and `inputDefaultBehavior` promises both of its members.
     * `cursor`, `customCursor` and `hitTesting` are outside this phase and stay
     * `Unsupported(UpdateSurface)`; the whole snapshot is unavailable once the surface is terminal.
     */
    public fun unhonouredSurfacePromises(capabilities: SurfaceCapabilities): List<String> {
        val findings = mutableListOf<String>()
        val unsupportedUpdateSurface = unsupportedCapability(KadreOperation.UpdateSurface)
        if (capabilities.pointerCapture != Capability.Supported(
                setOf(PointerCaptureMode.None, PointerCaptureMode.Confined),
                FeatureAvailability.Available,
            )
        ) {
            findings += "pointerCapture must be Supported({None, Confined}, Available), was " +
                describeCapability(capabilities.pointerCapture)
        }
        if (capabilities.inputDefaultBehavior != Capability.Supported(
                setOf(InputDefaultBehavior.HostDefault, InputDefaultBehavior.SuppressWhenPossible),
                FeatureAvailability.Available,
            )
        ) {
            findings += "inputDefaultBehavior must be Supported({HostDefault, SuppressWhenPossible}, Available), was " +
                describeCapability(capabilities.inputDefaultBehavior)
        }
        if (capabilities.cursor != unsupportedUpdateSurface) {
            findings += "cursor must stay Unsupported(UpdateSurface), was " +
                describeCapability(capabilities.cursor)
        }
        if (capabilities.customCursor != unsupportedUpdateSurface) {
            findings += "customCursor must stay Unsupported(UpdateSurface), was " +
                describeCapability(capabilities.customCursor)
        }
        if (capabilities.hitTesting != unsupportedUpdateSurface) {
            findings += "hitTesting must stay Unsupported(UpdateSurface), was " +
                describeCapability(capabilities.hitTesting)
        }
        return findings
    }

    /**
     * Everything one attached reading must satisfy, as a list of findings.
     *
     * Empty means the reading honoured every value this phase promised. The two update probes are
     * exact: setting `inputDefaultBehavior` on an attached surface is `Applied`, and a `Clear` of a
     * field that has no unset value is refused whole with `InvalidRequest` naming that field — the
     * same pair the platform's own
     * `WebInputSurfaceTest.aClearAndAStaleRevisionAreRefusedBeforeAdmission` asserts without a
     * browser.
     */
    @OptIn(DelicateKadreApi::class)
    public fun findingsOf(reading: WebInputReading): List<String> {
        val findings = mutableListOf<String>()
        findings += unhonouredInputPromises(reading.input.capabilities)
        findings += unhonouredSurfacePromises(reading.surface)
        when (val applied = reading.defaultBehaviorSet) {
            is KadreResult.Success ->
                if (applied.value !is SurfaceUpdateOutcome.Applied) {
                    findings += "setting inputDefaultBehavior on an attached surface must be Applied, was " +
                        describeUpdateOutcome(applied.value)
                }

            is KadreResult.Failure ->
                findings += "setting inputDefaultBehavior failed with " + WebConsumer.describeFailure(applied.reason)
        }
        if (reading.defaultBehaviorClear != KadreResult.Failure(KadreFailure.InvalidRequest("inputDefaultBehavior"))) {
            findings += "clearing inputDefaultBehavior must be refused by field name, was " +
                describeUpdateResult(reading.defaultBehaviorClear)
        }
        if (reading.textInput != KadreResult.Failure(unsupportedFailure(KadreOperation.TextInput))) {
            findings += "opening a text input must fail Unsupported(TextInput), was " +
                describeObjectResult(reading.textInput)
        }
        if (reading.rawInput != KadreResult.Failure(unsupportedFailure(KadreOperation.RawInputAccess))) {
            findings += "requesting raw input must fail Unsupported(RawInputAccess), was " +
                describeObjectResult(reading.rawInput)
        }
        return findings
    }

    /**
     * Attaches a real element on the target, observes the surface it publishes and returns the
     * reading. The attach itself is target-specific, so it lives in the target source set.
     */
    public suspend fun reading(scope: CoroutineScope): KadreResult<WebInputReading> =
        attachedInputReading(scope)

    /**
     * Reads an attached surface: the input snapshot, the two capability snapshots, and the four
     * observations this phase promised a consumer can make.
     *
     * The raw-input request is the one deliberate opt-in of this consumer, exactly as the element
     * lease is: the escape hatch keeps its name, its shape and its marker.
     */
    @OptIn(DelicateKadreApi::class)
    public suspend fun readInput(surface: HostSurface): WebInputReading = WebInputReading(
        input = surface.input.state.value,
        surface = surface.capabilities.value,
        defaultBehaviorSet = surface.apply(
            SurfaceUpdate(
                pointerCapture = PropertyChange.Set(PointerCaptureMode.None),
                inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
            ),
        ),
        defaultBehaviorClear = surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Clear)),
        textInput = surface.input.openTextInput(TextInputConfig()),
        rawInput = surface.input.requestRawInput(),
        events = surface.input.events,
    )
}

/**
 * One reading of an attached Web surface, gathered from the published API only.
 *
 * `events` is the stream itself, not a collected sample: a host that needs more than the current
 * snapshot collects it, which is what [WebInputConsumer.firstInputEvent] does.
 *
 * The opt-in on the class is the same one the request needs, and it is the whole point of the raw
 * input escape hatch: `RawInputAccess` carries `@DelicateKadreApi`, so a host that merely *names* the
 * result of `requestRawInput` in one of its own signatures opts in as explicitly as the caller does.
 * The consumer takes it once, here.
 */
@OptIn(DelicateKadreApi::class)
public data class WebInputReading(
    public val input: SurfaceInputState,
    public val surface: SurfaceCapabilities,
    public val defaultBehaviorSet: KadreResult<SurfaceUpdateOutcome>,
    public val defaultBehaviorClear: KadreResult<SurfaceUpdateOutcome>,
    public val textInput: KadreResult<TextInputSession>,
    public val rawInput: KadreResult<RawInputAccess>,
    public val events: Flow<InputEvent>,
)

/** The target-specific half: creates an element, attaches it and reads the surface it publishes. */
internal expect suspend fun attachedInputReading(scope: CoroutineScope): KadreResult<WebInputReading>

private fun describePosition(position: LogicalPoint): String = position.x.toString() + "," + position.y.toString()

private fun describePhysicalKey(key: PhysicalKey): String = when (key) {
    is PhysicalKey.Code -> "code:" + key.usagePage + ":" + key.usageId
    is PhysicalKey.Unidentified -> "unidentified:" + (key.nativeCode ?: "no-code")
}

private fun describeLogicalKey(key: LogicalKey): String = when (key) {
    is LogicalKey.Character -> "character:" + key.value
    is LogicalKey.Named -> "named:" + key.value
    is LogicalKey.Unidentified -> "unidentified:" + (key.nativeCode ?: "no-code")
}

private fun describePointerButton(button: PointerButton): String = when (button) {
    PointerButton.Primary -> "primary"
    PointerButton.Secondary -> "secondary"
    PointerButton.Auxiliary -> "auxiliary"
    PointerButton.Back -> "back"
    PointerButton.Forward -> "forward"
    PointerButton.Barrel -> "barrel"
    PointerButton.Eraser -> "eraser"
    is PointerButton.Other -> "other:" + button.nativeCode
}

private fun describeScrollDelta(delta: ScrollDelta): String = when (delta) {
    is ScrollDelta.Logical -> "logical:" + delta.x + "," + delta.y
    is ScrollDelta.Lines -> "lines:" + delta.x + "," + delta.y
}

private fun describeResetReason(reason: InputStateResetReason): String = when (reason) {
    InputStateResetReason.FocusLost -> "focusLost"
    InputStateResetReason.DeviceDisconnected -> "deviceDisconnected"
    InputStateResetReason.PermissionRevoked -> "permissionRevoked"
}

private fun describeConstraints(constraints: Any?): String = when (constraints) {
    is Set<*> -> constraints.joinToString("|") { constraint ->
        (constraint as? Enum<*>)?.let { entry -> entry.name } ?: constraint.toString()
    }

    else -> constraints.toString()
}

private fun describeUpdateResult(result: KadreResult<SurfaceUpdateOutcome>): String = when (result) {
    is KadreResult.Success -> WebInputConsumer.describeUpdateOutcome(result.value)
    is KadreResult.Failure -> WebConsumer.describeFailure(result.reason)
}

private fun describeObjectResult(result: KadreResult<*>): String = when (result) {
    is KadreResult.Success -> "success"
    is KadreResult.Failure -> WebConsumer.describeFailure(result.reason)
}

private fun unsupportedCapability(operation: KadreOperation): Capability<Nothing> =
    Capability.Unsupported(unsupportedFailure(operation))

private fun unsupportedFailure(operation: KadreOperation): KadreFailure.Unsupported =
    KadreFailure.Unsupported(operation)

private fun isIngressOverflowArm(capabilities: InputCapabilities): Boolean {
    val overflow = FeatureAvailability.Unavailable(KadreFailure.SourceOverflow(KadreResourceKind.InputSource))
    return capabilities.keyboard == overflow &&
        capabilities.pointer == overflow &&
        capabilities.touch == overflow &&
        capabilities.dragAndDrop == overflow &&
        capabilities.gestures is Capability.Unsupported &&
        capabilities.textInput is Capability.Unsupported &&
        capabilities.rawInput is Capability.Unsupported
}
