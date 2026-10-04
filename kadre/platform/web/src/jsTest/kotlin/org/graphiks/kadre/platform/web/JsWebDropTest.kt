package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.surface.LogicalPoint
import org.graphiks.kadre.internal.runtime.DropTransferSource
import org.w3c.dom.DataTransfer
import org.w3c.dom.DragEvent
import org.w3c.dom.DragEventInit
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The JS DOM port's drop listeners, driven by real browser events on a real element with a real
 * `DataTransfer`.
 *
 * What is proven here is the port's own work and nothing above it: a drag entry is snapshotted into
 * the DOM-free source the drop seam presents — the descriptors the store reports, canonicalised by
 * the pure rules the `webTest` suite pins — the offer question is asked of the very `dragover`/`drop`
 * event in hand and its default dropped only on an active offer (D-D3), and the drop re-reads the
 * store so the bytes the drag protected become the payload a read resolves. The `File` of the drag
 * stays inside the glue: the source the surface sees is Kotlin-only, and its `collectBytes` is the
 * only read it can ask for.
 *
 * A constructed `DataTransfer` is the browser's own read-write store, which is what lets these cases
 * dispatch a whole drag — entry, overs, drop — in one page: a real drag would arrive in the same
 * shapes, with the store protecting its payload until the drop, which is exactly the re-read the drop
 * performs.
 */
class JsWebDropTest {
    @Test
    fun aDragEnterSnapshotsTheDescriptorsTheStoreReportsAndNoStimulusAtAll() {
        val harness = JsDropHarness()
        try {
            val origin = harness.elementOrigin()
            val dataTransfer = newDataTransfer()
            dataTransfer.items.add("hello", "text/plain")
            dataTransfer.items.add("https://kadre.example", "text/uri-list")
            dataTransfer.items.add(newFile("PNG-DATA", "picture.png", "image/png"))

            dispatchDrag(harness.element, "dragenter", dataTransfer, origin.x + 10.0, origin.y + 20.0)

            assertEquals(
                1,
                harness.entered.size,
                "the entry is reported once, through the drop seam and nothing else",
            )
            assertTrue(
                harness.delivered.isEmpty(),
                "there is no DropEntered stimulus: the entry is the drop seam's, and the model has no such stimulus",
            )
            val (source, position) = harness.entered[0]
            assertEquals(
                LogicalPoint(10.0, 20.0),
                position,
                "the position the entry carried is the element's own, not the viewport's",
            )
            assertEquals(
                listOf(DropItemKind.Text, DropItemKind.Uri, DropItemKind.File),
                source.items.map { it.descriptor.kind },
                "the closed vocabulary of the snapshot: the plain text, the URI list, and the file",
            )
            assertEquals(listOf("text/plain"), source.items[0].descriptor.mimeTypes)
            assertNull(source.items[0].descriptor.sizeBytes, "a string payload is readable at no earlier moment")
            assertEquals(listOf("text/uri-list"), source.items[1].descriptor.mimeTypes)
            assertEquals(listOf("image/png"), source.items[2].descriptor.mimeTypes)
            assertEquals("picture.png", source.items[2].descriptor.displayName)
            assertEquals(
                8L,
                source.items[2].descriptor.sizeBytes,
                "the file's own size is what the store reported, not a guess from its format",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun theDropTargetIsActivatedOnlyWhileAnOfferIsHeld() {
        val harness = JsDropHarness()
        try {
            val origin = harness.elementOrigin()
            val dataTransfer = newDataTransfer()
            dataTransfer.items.add("hello", "text/plain")

            val firstOver = dispatchDrag(harness.element, "dragover", dataTransfer, origin.x, origin.y)
            assertTrue(
                !firstOver.defaultPrevented,
                "a drag over an element that presented no offer keeps the browser's refusal",
            )

            dispatchDrag(harness.element, "dragenter", dataTransfer, origin.x, origin.y)
            val secondOver = dispatchDrag(harness.element, "dragover", dataTransfer, origin.x, origin.y)
            assertTrue(
                !secondOver.defaultPrevented,
                "an entry nobody answered is not an offer: the browser keeps its default, whatever the policy",
            )

            harness.offerActive = true
            val thirdOver = dispatchDrag(harness.element, "dragover", dataTransfer, origin.x, origin.y)
            assertTrue(
                thirdOver.defaultPrevented,
                "the active offer is what makes the element a drop target: the refusal is dropped",
            )

            val leave = dispatchDrag(harness.element, "dragleave", dataTransfer, origin.x, origin.y)
            assertTrue(
                !leave.defaultPrevented,
                "a leave has no default to drop, and none is asked about",
            )

            val firstDrop = dispatchDrag(harness.element, "drop", dataTransfer, origin.x, origin.y)
            assertTrue(
                !firstDrop.defaultPrevented,
                "an offer the leave already spent prevents nothing: the browser keeps its refusal",
            )

            harness.offerActive = true
            val secondDrop = dispatchDrag(harness.element, "drop", dataTransfer, origin.x, origin.y)
            assertTrue(secondDrop.defaultPrevented, "a drop over an active offer is not a navigation")

            assertEquals(
                1,
                harness.delivered.filterIsInstance<WebInputStimulus.DropExited>().size,
                "the leave is the one observation a leave delivers",
            )
            assertEquals(
                2,
                harness.delivered.filterIsInstance<WebInputStimulus.DropPerformed>().size,
                "both drops delivered their observation, whatever became of their default",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aDropReReadsTheStoreSoTheDroppedBytesBecomeReadable() = runTest {
        val harness = JsDropHarness()
        try {
            val origin = harness.elementOrigin()
            val dataTransfer = newDataTransfer()
            dataTransfer.items.add(newFile("PNG-DATA", "picture.png", "image/png"))
            dataTransfer.items.add("hello", "text/plain")

            dispatchDrag(harness.element, "dragenter", dataTransfer, origin.x, origin.y)
            harness.offerActive = true
            dispatchDrag(harness.element, "drop", dataTransfer, origin.x, origin.y)

            assertIs<WebInputStimulus.DropPerformed>(harness.delivered.last())
            val source = harness.entered[0].first

            val fileChunks = mutableListOf<ByteArray>()
            withContext(Dispatchers.Default) {
                source.items[0].collectBytes(4) { chunk -> fileChunks += chunk }
            }
            assertEquals(
                "PNG-DATA".encodeToByteArray().toList(),
                fileChunks.flatMap { it.toList() },
                "the bytes of the dropped file, resolved lazily from the handle the drop released",
            )
            assertTrue(fileChunks.all { it.size <= 4 }, "every chunk is inside the bound the read named")

            val textChunks = mutableListOf<ByteArray>()
            withContext(Dispatchers.Default) {
                source.items[1].collectBytes(2) { chunk -> textChunks += chunk }
            }
            assertEquals(
                "hello".encodeToByteArray().toList(),
                textChunks.flatMap { it.toList() },
                "the dropped text, encoded once and delivered through the same bounded reads",
            )
            val replay: KadreResult<Unit> = withContext(Dispatchers.Default) {
                source.items[0].collectBytes(4) { }
            }
            assertIs<KadreResult.Success<Unit>>(
                replay,
                "the reads are the source's own: replayable, and never consumed by the one before them",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aStoreTheBrowserHandsAsNothingProducesNoEntryAtAll() {
        val harness = JsDropHarness()
        try {
            val origin = harness.elementOrigin()
            val empty = newDataTransfer()

            dispatchDrag(harness.element, "dragenter", empty, origin.x, origin.y)

            assertTrue(harness.entered.isEmpty(), "no item the store describes is no snapshot, and no offer")
        } finally {
            harness.close()
        }
    }

    @Test
    fun aDragOverAfterTheChildChurnRePresentsTheOfferTheDropCompletesOn() = runTest {
        val harness = JsDropHarness()
        try {
            // The mutation this case kills: a port that builds the drop source only at `dragenter`.
            // The DOM's own bubbling makes a move onto a child fire the child's enter BEFORE the
            // parent's leave, so the churn of an ordinary nested target spends the offer the entry
            // presented — and the next over arrives with no offer in hand. Without the re-present
            // rule (D-D1's parenthetical), that over prevents nothing, the browser refuses the
            // target, and no drop can ever complete on an element with children.
            harness.autoAccept = true
            val origin = harness.elementOrigin()
            val child = (document.createElement("div") as HTMLElement).also {
                it.style.position = "absolute"
                it.style.left = "0px"
                it.style.top = "0px"
                it.style.width = "160px"
                it.style.height = "90px"
                harness.element.appendChild(it)
            }
            val dataTransfer = newDataTransfer()
            dataTransfer.items.add("hello", "text/plain")

            dispatchDrag(harness.element, "dragenter", dataTransfer, origin.x, origin.y)
            dispatchDrag(child, "dragenter", dataTransfer, origin.x, origin.y)
            dispatchDrag(harness.element, "dragleave", dataTransfer, origin.x, origin.y)
            assertTrue(
                !harness.offerActive,
                "the churn's leave spent the offer the child's bubbled entry presented",
            )

            val over = dispatchDrag(child, "dragover", dataTransfer, origin.x, origin.y)
            assertTrue(
                over.defaultPrevented,
                "the first over after the churn re-presents the offer: an element with children stays a drop target",
            )
            val drop = dispatchDrag(child, "drop", dataTransfer, origin.x, origin.y)
            assertTrue(
                drop.defaultPrevented,
                "the drop completes on the re-presented offer, and its navigation default is dropped",
            )

            assertEquals(
                3,
                harness.entered.size,
                "the parent entry, the child's bubbled entry, and the re-presentation the over made",
            )
            assertEquals(
                1,
                harness.delivered.filterIsInstance<WebInputStimulus.DropExited>().size,
                "the churn's leave is the one exit the port delivered",
            )
            assertIs<WebInputStimulus.DropPerformed>(harness.delivered.last())

            // The drop re-read the store against the re-presented snapshot: the bytes the drag
            // protected belong to the offer the over re-made, not to an earlier entry's source.
            val chunks = mutableListOf<ByteArray>()
            withContext(Dispatchers.Default) {
                harness.entered.last().first.items.single().collectBytes(2) { chunk -> chunks += chunk }
            }
            assertEquals(
                "hello".encodeToByteArray().toList(),
                chunks.flatMap { it.toList() },
                "the re-presented source is the one the drop fed",
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun aReleasedPortDeliversNoDropFactAtAll() {
        val harness = JsDropHarness()
        harness.close()

        val dataTransfer = newDataTransfer()
        dataTransfer.items.add("hello", "text/plain")
        dispatchDrag(harness.element, "dragenter", dataTransfer, 1.0, 1.0)
        dispatchDrag(harness.element, "dragover", dataTransfer, 1.0, 1.0)
        dispatchDrag(harness.element, "drop", dataTransfer, 1.0, 1.0)

        assertTrue(harness.entered.isEmpty())
        assertTrue(harness.delivered.isEmpty())
    }
}

/**
 * One element of the real page and the port that observes it, with every drop fact the port reported.
 *
 * The [JsDropHarness.offerActive] flag is the surface's half of the offer question, played by the
 * case: the port asks and applies, and what the surface answers is proven by `WebDropSurfaceTest`.
 * [JsDropHarness.autoAccept] plays the other half of a real surface — the synchronous accept that
 * commits *inside* the entry dispatch itself, which is what the offer question a port asks right
 * after a re-presentation reads; the cases that drive the question one dispatch at a time leave it
 * off and set [JsDropHarness.offerActive] by hand.
 */
private class JsDropHarness {
    val element: HTMLElement = (document.createElement("div") as HTMLElement).also {
        it.style.position = "absolute"
        it.style.left = "80px"
        it.style.top = "60px"
        it.style.width = "320px"
        it.style.height = "180px"
        document.body!!.appendChild(it)
    }

    /** Every drop observation the port delivered, in the order the browser reported the events. */
    val delivered: MutableList<WebInputStimulus> = mutableListOf()

    /** Every drag entry the port reported, with the source it snapshotted and the position it carried. */
    val entered: MutableList<Pair<DropTransferSource, LogicalPoint>> = mutableListOf()

    /** What the channel answers to the offer question, as the surface would. */
    var offerActive: Boolean = false

    /** Whether every entry the port reports is accepted inside the dispatch that carries it. */
    var autoAccept: Boolean = false

    private val port: JsWebDomPort = JsWebDomPort(element)
    private var released: Boolean = false

    init {
        port.installLifecycleObserver { }
        port.installInputObserver(object : WebInputObserver {
            override fun onObservation(stimulus: WebInputStimulus) {
                delivered += stimulus
                // The surface's own spend: an observation that ends the drag ends the offer it held,
                // which is why the port asks its question BEFORE delivering — the answer it gets is
                // the state the drag arrived on, never the one the observation leaves behind.
                when (stimulus) {
                    is WebInputStimulus.DropExited, is WebInputStimulus.DropPerformed -> offerActive = false
                    else -> Unit
                }
            }

            override fun onDropEntered(source: DropTransferSource, position: LogicalPoint) {
                entered += source to position
                if (autoAccept) offerActive = true
            }

            override fun holdsActiveDropOffer(): Boolean = offerActive
        })
    }

    /** Releases the port and takes the element out of the page. Idempotent, so a `finally` can call it. */
    fun close() {
        if (released) return
        released = true
        port.release()
        element.remove()
    }

    /** The viewport position the element's own origin sits at, read back from the browser. */
    fun elementOrigin(): LogicalPoint {
        val box = element.getBoundingClientRect()
        return LogicalPoint(box.left + element.clientLeft, box.top + element.clientTop)
    }
}

/** Constructs the browser's own read-write drag data store. */
private fun newDataTransfer(): DataTransfer = js("new DataTransfer()")

/** Constructs a real file of [content], named and typed as the case needs. */
private fun newFile(content: String, name: String, type: String): org.w3c.files.File = js(
    """new File([content], name, {type: type})""",
)

/** Dispatches a real drag event of [type] on [element], carrying [dataTransfer], and returns it. */
private fun dispatchDrag(
    element: HTMLElement,
    type: String,
    dataTransfer: DataTransfer,
    clientX: Double,
    clientY: Double,
): DragEvent {
    val event = DragEvent(
        type,
        DragEventInit(
            dataTransfer = dataTransfer,
            clientX = clientX.toInt(),
            clientY = clientY.toInt(),
            bubbles = true,
            cancelable = true,
        ),
    )
    element.dispatchEvent(event)
    return event
}
