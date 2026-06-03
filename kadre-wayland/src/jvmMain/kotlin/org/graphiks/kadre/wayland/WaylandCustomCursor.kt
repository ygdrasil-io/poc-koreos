package org.graphiks.kadre.wayland

import org.graphiks.kadre.core.CursorImage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object WaylandCustomCursorStore {
    private val cursors = ConcurrentHashMap<Long, CursorImage>()
    private val nextId = AtomicLong(1L)

    fun store(image: CursorImage): Long {
        val id = nextId.getAndIncrement()
        cursors[id] = image
        return id
    }

    fun get(id: Long): CursorImage? = cursors[id]

    fun remove(id: Long) { cursors.remove(id) }
}
