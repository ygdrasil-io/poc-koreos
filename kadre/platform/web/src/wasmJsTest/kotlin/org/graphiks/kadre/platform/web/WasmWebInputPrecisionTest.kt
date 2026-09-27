@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlinx.browser.document
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Wasm half of the cross-target precision experiment for the pointer members whose declared DOM
 * type is narrower than the value the browser's accessor can return.
 *
 * This is the twin of `JsWebInputPrecisionTest` in `jsTest`: the same event, the same four overridden
 * reads, the same fractional values and the same assertions, so the numbers the two ports deliver for
 * one event can be compared directly. The event is built through the constructor and its four reads
 * are then redefined with `Object.defineProperty`, because WebIDL would otherwise convert them to
 * `long`/`float` and no fractional value could reach the port at all.
 *
 * The point of the case is that the two targets must not disagree on a delivered value: what the
 * declared type of a read does to it is part of the DOM contract of the toolchain, and this target's
 * externals must follow the JS target's declarations rather than quietly carrying more precision than
 * the other port delivers.
 */
class WasmWebInputPrecisionTest {
    @Test
    fun aFractionalPointerMemberReachesTheStimulusWithTheValueTheBrowserReports() {
        val harness = WasmPrecisionHarness()
        try {
            val originX = elementOriginX(harness.element)
            assertTrue(
                originX != 0.0,
                "the element must sit away from the viewport origin for a surface-relative position to " +
                    "be distinguishable from a viewport one: $originX",
            )
            dispatchPointerWithOverriddenReads(
                harness.element,
                type = "pointermove",
                pointerType = "pen",
                relativeX = FRACTIONAL_X,
                relativeY = FRACTIONAL_Y,
                pressure = FRACTIONAL_PRESSURE,
                tangentialPressure = FRACTIONAL_TANGENTIAL,
            )

            val moved = harness.delivered.single() as WebInputStimulus.PointerMoved
            // Measured on this target for the event above: `PointerMoved(position=LogicalPoint(x=10.5,
            // y=20.25), pressure=0.1, kind=Pen, pen=PenState(…, tangentialPressure=0.3))` — identical to
            // what the JS target delivered for the same event, which is why the externals declare these
            // four members `Double` rather than the narrow types the JS DOM declarations use: a
            // narrowing read would deliver `LogicalPoint(x=10.0, y=20.0)`, `0.1f`'s nearest double
            // (`0.10000000149011612`) and `0.3f`'s (`0.30000001192092896`), and the two targets would
            // then disagree on a delivered value. The assertions below pin the measured ones.
            assertEquals(
                LogicalPoint(FRACTIONAL_X, FRACTIONAL_Y),
                moved.position,
                "the surface-relative position is the fractional value the browser reported: delivered=$moved",
            )
            assertEquals(
                FRACTIONAL_PRESSURE,
                moved.pressure,
                "the pointer pressure is the fractional value the browser reported: delivered=$moved",
            )
            assertEquals(
                FRACTIONAL_TANGENTIAL,
                moved.pen?.tangentialPressure,
                "the tangential pressure is the fractional value the browser reported: delivered=$moved",
            )
        } finally {
            harness.close()
        }
    }
}

/** The fractional values the experiment dispatches, none of them representable in the narrow types. */
private const val FRACTIONAL_X: Double = 10.5
private const val FRACTIONAL_Y: Double = 20.25

/** `0.1` is the smallest double above Float32's nearest value, so a Float read is visible in it. */
private const val FRACTIONAL_PRESSURE: Double = 0.1

/** `0.3` is likewise not exactly representable in Float32. */
private const val FRACTIONAL_TANGENTIAL: Double = 0.3

/** One element of the real page and the port that observes it, with every stimulus the port delivered. */
private class WasmPrecisionHarness {
    val element: HTMLElement = (document.createElement("div") as HTMLElement).also {
        it.style.position = "absolute"
        it.style.left = "80px"
        it.style.top = "60px"
        it.style.width = "320px"
        it.style.height = "180px"
        document.body!!.appendChild(it)
    }

    val delivered: MutableList<WebInputStimulus> = mutableListOf()

    private val port: WasmWebDomPort = WasmWebDomPort(element)
    private var released: Boolean = false

    init {
        port.installLifecycleObserver { }
        port.installInputObserver { delivered += it }
    }

    fun close() {
        if (released) return
        released = true
        port.release()
        element.remove()
    }
}

/** The x the element's own origin sits at, so the dispatch below is expressed as a surface offset. */
@JsFun("(element) => element.getBoundingClientRect().left + element.clientLeft")
private external fun elementOriginX(element: JsAny): Double

/**
 * Dispatches a real pointer event of [type] whose four measured members carry the fractional values
 * handed in, whatever WebIDL would have made of them through the constructor.
 *
 * The event is built and its reads redefined in one JavaScript function: Kotlin/Wasm cannot hold the
 * borrowed event between two `@JsFun` calls, and a raw `js(…)` snippet sees neither the element nor a
 * Kotlin value.
 */
@JsFun(
    """(element, type, pointerType, relativeX, relativeY, pressure, tangentialPressure) => {
         var box = element.getBoundingClientRect();
         var originX = box.left + element.clientLeft;
         var originY = box.top + element.clientTop;
         var event = new PointerEvent(type, { pointerType: pointerType, bubbles: true, cancelable: true });
         Object.defineProperty(event, "clientX", { value: originX + relativeX, configurable: true });
         Object.defineProperty(event, "clientY", { value: originY + relativeY, configurable: true });
         Object.defineProperty(event, "pressure", { value: pressure, configurable: true });
         Object.defineProperty(event, "tangentialPressure", { value: tangentialPressure, configurable: true });
         element.dispatchEvent(event);
       }""",
)
private external fun dispatchPointerWithOverriddenReads(
    element: JsAny,
    type: String,
    pointerType: String,
    relativeX: Double,
    relativeY: Double,
    pressure: Double,
    tangentialPressure: Double,
): Unit
