package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.internal.runtime.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.surface.*

internal class Win32ContactRouter(private val hwnd: Long, private val surfaceId: SurfaceId,
    private val port: Win32NativeHostPort, private val resources: ResourceBudgetPolicy,
    private val budget: RuntimePayloadBudget, private val capacity: Int,
    private val emit: (SurfaceStimulus) -> Boolean, private val interact: (RuntimeSynchronousInteraction) -> Unit,
    private val isAlive: () -> Boolean, private val onFailure: (Throwable) -> Unit,
    private val onReset: () -> Unit = {}) : AutoCloseable {
    private class Contact(val token: Any, val type: Int, val kind: PointerKind,
        var position: LogicalPoint, var pressure: Double?, var pen: PenState?) {
        val buttons = linkedSetOf<PointerButton>()
    }
    private val contacts = mutableMapOf<Int, Contact>()
    private class Packet(val batch: Win32ContactBatch, val isLeave: Boolean, val leaving: Contact?, val pointerId: Int)
    private val queue = ArrayDeque<Packet>()
    private var lastFrame: Int? = null
    private var generation = 0L
    private var processing = false
    private var resetPending = false
    private var ready = false
    private var closed = false
    private var nativeGestures: Win32GesturePort? = null
    private var gestures: Win32GestureRouter? = null
    private var processingGeneration = 0L
    val installed: Boolean get() = ready && !closed

    fun activate() {
        if (closed || ready || !isAlive() || !port.pointerPacketsAvailable) return
        ready = true
        try {
            val native = port.createGesturePort(hwnd, resources.maxCollectionElementsPerValue) { output ->
                if (ready && !closed && !resetPending && processingGeneration == generation) gestures?.accept(output)
            }
            nativeGestures = native
            gestures = Win32GestureRouter(surfaceId, native.kinds.toSet(), { port.readScaleFactor(hwnd) }, { emit(it) }, isAlive)
            publish()
        } catch (cause: Throwable) { failGestures(cause) }
    }
    private fun publish() { emit(SurfaceStimulus.InputObservationChanged(surfaceId, true, true, installed, gestures?.kinds.orEmpty())) }
    fun handle(message: Int, wParam: Long, lParam: Long): Long? {
        if (!installed || !isAlive()) return null
        when (message) {
            0x24b -> return 1L // PA_ACTIVATE: acknowledge the coherent pointer stream.
            0x24c -> { reset(); return 0L }
            0x245, 0x246, 0x247, 0x249, 0x24a -> Unit
            else -> return null
        }
        try {
            val pointerId = (wParam and 0xffffL).toInt()
            val leaving = contacts[pointerId].takeIf { message == 0x24a }
            val batch = port.readPointerBatch(pointerId, resources.maxCollectionElementsPerValue,
                minOf(capacity, resources.maxCollectionElementsPerValue), budget)
            if (queue.size >= minOf(capacity, resources.maxCollectionElementsPerValue)) {
                batch.close(); throw KadreException(KadreFailure.SourceOverflow(KadreResourceKind.InputSource))
            }
            queue += Packet(batch, message == 0x24a, leaving, pointerId)
            if (!processing) drain()
        } catch (cause: Throwable) { fail(cause) }
        return 0L
    }
    private fun drain() {
        processing = true
        try {
            while (queue.isNotEmpty() && installed && isAlive()) {
                val packet = queue.removeFirst()
                val batch = packet.batch
                processingGeneration = generation
                try {
                    for (frame in batch.frames.asReversed()) {
                        if (generation != processingGeneration || !installed || !isAlive()) break
                        if (lastFrame?.let { !after(frame.id, it) } == true) continue
                        lastFrame = frame.id
                        for (sample in frame.samples) {
                            if (generation != processingGeneration || !installed || !isAlive()) break
                            if (sample.target == hwnd && !(packet.isLeave && sample.id == packet.pointerId)) accept(sample)
                        }
                    }
                    if (generation == processingGeneration && installed && isAlive()) {
                        packet.leaving?.let { retire(packet.pointerId, it) }
                        try { nativeGestures?.process(batch) }
                        catch (cause: Throwable) { failGestures(cause) }
                    }
                } finally { batch.close() }
                if (resetPending && installed) resetNative()
            }
        } finally {
            processing = false
            if (resetPending && installed) resetNative()
        }
    }
    private fun accept(sample: Win32ContactSample) {
        if (sample.type !in 2..3) return
        val position = port.pointerClientPosition(hwnd, sample.x, sample.y)
        val pen = if (sample.type == 3) PenState(sample.tiltX, sample.tiltY, sample.twistRadians, null) else null
        var contact = contacts[sample.id]
        val newContact = contact == null
        if (sample.flags and 0x8000 != 0) { contact?.let { retire(sample.id, it) }; return }
        val down = sample.flags and 0x10000 != 0
        val up = sample.flags and 0x40000 != 0
        if (contact == null) {
            if (!down && (sample.type != 3 || sample.flags and 2 == 0)) return
            if (contacts.size >= resources.maxCollectionElementsPerValue) throw KadreException(KadreFailure.SourceOverflow(KadreResourceKind.InputSource))
            contact = Contact(Any(), sample.type, if (sample.eraser) PointerKind.Eraser else PointerKind.Pen, position, sample.pressure, pen)
            contacts[sample.id] = contact
            if (sample.type == 3) emit(SurfaceStimulus.PointerEntered(surfaceId, contact.kind, position, nativeIdentity=contact.token))
        }
        val previous = contact.position
        contact.position = position; contact.pressure = sample.pressure; contact.pen = pen
        if (sample.type == 2) {
            if (down && newContact) interact(RuntimeSynchronousInteraction.TouchContactStarted(contact.token, position, sample.pressure))
            else {
                if (up) contacts.remove(sample.id)
                emit(SurfaceStimulus.TouchChanged(surfaceId, contact.token, if (up) TouchPhase.Ended else TouchPhase.Moved, position, sample.pressure))
            }
        } else {
            val desired = buildSet {
                if (!up && sample.flags and 4 != 0) add(PointerButton.Primary)
                if (!up && sample.flags and 0x20 != 0) add(PointerButton.Barrel)
            }
            val before = contact.buttons.toSet()
            contact.buttons.clear(); contact.buttons.addAll(desired)
            val epoch = generation
            for (button in before - desired) {
                emit(SurfaceStimulus.PointerButtonChanged(surfaceId, contact.kind, button, PointerButtonState.Released,
                    position, sample.pressure, pen, nativeIdentity=contact.token))
                if (epoch != generation || !installed || !isAlive()) return
            }
            for (button in desired - before) {
                interact(RuntimeSynchronousInteraction.PointerPressed(button, position, sample.pressure, contact.kind, pen, contact.token))
                if (epoch != generation || !installed || !isAlive()) return
            }
            if (before == desired) emit(SurfaceStimulus.PointerMoved(surfaceId, contact.kind, position,
                LogicalDelta(position.x - previous.x, position.y - previous.y), sample.pressure, pen, nativeIdentity=contact.token))
            if (up && sample.flags and 2 == 0) { contacts.remove(sample.id); emit(SurfaceStimulus.PointerLeft(surfaceId, contact.kind, nativeIdentity=contact.token)) }
        }
    }
    private fun retire(id: Int, contact: Contact) {
        if (contacts[id] !== contact) return
        contacts.remove(id)
        cancelContact(contact)
    }
    private fun cancelContact(contact: Contact) {
        if (contact.type == 2) emit(SurfaceStimulus.TouchChanged(surfaceId, contact.token, TouchPhase.Cancelled, contact.position, contact.pressure))
        else {
            for (button in contact.buttons) emit(SurfaceStimulus.PointerButtonChanged(surfaceId, contact.kind, button,
                PointerButtonState.Released, contact.position, contact.pressure, contact.pen, nativeIdentity=contact.token))
            emit(SurfaceStimulus.PointerLeft(surfaceId, contact.kind, nativeIdentity=contact.token))
        }
    }
    fun reset() {
        if (closed || !ready) return
        cancelState()
        resetPending = true
        if (!processing) try { resetNative() } catch (cause: Throwable) { fail(cause) }
    }
    private fun cancelState() {
        generation++; onReset()
        while (queue.isNotEmpty()) queue.removeFirst().batch.close()
        val retired = contacts.toMap(); contacts.clear()
        retired.values.forEach(::cancelContact)
        gestures?.reset()
    }
    private fun resetNative() {
        resetPending = false
        try { nativeGestures?.reset() } catch (cause: Throwable) { failGestures(cause) }
    }
    private fun failGestures(cause: Throwable) {
        val native = nativeGestures; nativeGestures = null
        val router = gestures; gestures = null
        try { router?.reset() }
        finally {
            try { native?.close() }
            finally { onFailure(Win32GestureFailure(cause)) }
        }
    }
    private fun fail(cause: Throwable) {
        if (closed) return
        try { close() } finally { onFailure(cause) }
    }
    override fun close() {
        if (closed) return
        closed = true; ready = false; resetPending = false
        try { cancelState() }
        finally {
            val native = nativeGestures; nativeGestures = null; gestures = null
            native?.close()
        }
    }
    private fun after(newer: Int, older: Int): Boolean = ((newer.toLong() - older.toLong()) and 0xffffffffL) in 1..0x7fffffffL
}

internal class Win32GestureFailure(val nativeCause: Throwable) : RuntimeException()
