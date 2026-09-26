package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import org.graphiks.kadre.surface.LogicalPoint
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The JS half of the cross-target precision experiment for the pointer members whose declared DOM
 * type is narrower than the value the browser's accessor can return.
 *
 * `kotlin-dom-api-compat` types `MouseEvent.clientX`/`clientY` as `Int` and `PointerEvent.pressure`/
 * `tangentialPressure` as `Float`, while `PointerEvent` reports doubles in practice. A synthetic event
 * built through the constructor cannot carry such a value — WebIDL converts the init members to
 * `long`/`float` — so the event is built empty and its four reads are then redefined with
 * `Object.defineProperty`, which is what makes the question decidable: does the *declared type* of the
 * read truncate the value the port delivers, or is it nominal only?
 *
 * The twin of this case runs on the Wasm target (`WasmWebInputPrecisionTest` in `wasmJsTest`) with the
 * same event, the same overridden values and the same assertions, so the two ports' delivered numbers
 * can be compared directly.
 */
class JsWebInputPrecisionTest {
    @Test
    fun aFractionalPointerMemberReachesTheStimulusWithTheValueTheBrowserReports() {
        val harness = JsPrecisionHarness()
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
            // y=20.25), pressure=0.1, kind=Pen, pen=PenState(…, tangentialPressure=0.3))`. That is the
            // whole point of the case: the declared `Int` of `clientX`/`clientY` and the declared
            // `Float` of `pressure`/`tangentialPressure` are nominal — the runtime value is delivered
            // whole. A narrowing read would deliver `LogicalPoint(x=10.0, y=20.0)`, `0.1f`'s nearest
            // double (`0.10000000149011612`) and `0.3f`'s (`0.30000001192092896`); the three
            // assertions below pin the values that were measured instead of those.
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
private class JsPrecisionHarness {
    val element: HTMLElement = (document.createElement("div") as HTMLElement).also {
        it.style.position = "absolute"
        it.style.left = "80px"
        it.style.top = "60px"
        it.style.width = "320px"
        it.style.height = "180px"
        document.body!!.appendChild(it)
    }

    val delivered: MutableList<WebInputStimulus> = mutableListOf()

    private val port: JsWebDomPort = JsWebDomPort(element)
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
private fun elementOriginX(element: HTMLElement): Double =
    js("element.getBoundingClientRect().left + element.clientLeft")

/**
 * Dispatches a real pointer event of [type] whose four measured members carry the fractional values
 * handed in, whatever WebIDL would have made of them through the constructor.
 */
private fun dispatchPointerWithOverriddenReads(
    element: HTMLElement,
    type: String,
    pointerType: String,
    relativeX: Double,
    relativeY: Double,
    pressure: Double,
    tangentialPressure: Double,
): Unit = js(
    """(function () {
         var box = element.getBoundingClientRect();
         var originX = box.left + element.clientLeft;
         var originY = box.top + element.clientTop;
         var event = new PointerEvent(type, { pointerType: pointerType, bubbles: true, cancelable: true });
         Object.defineProperty(event, "clientX", { value: originX + relativeX, configurable: true });
         Object.defineProperty(event, "clientY", { value: originY + relativeY, configurable: true });
         Object.defineProperty(event, "pressure", { value: pressure, configurable: true });
         Object.defineProperty(event, "tangentialPressure", { value: tangentialPressure, configurable: true });
         element.dispatchEvent(event);
       }())""",
)
