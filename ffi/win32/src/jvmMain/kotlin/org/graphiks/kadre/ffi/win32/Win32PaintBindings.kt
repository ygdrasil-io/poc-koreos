package org.graphiks.kadre.ffi.win32

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/** Win64 memory layout of PAINTSTRUCT. */
object PaintStructLayout {
    const val SIZEOF: Int = 72
    const val ALIGN: Int = 8

    val LAYOUT: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("hdc"),
        ValueLayout.JAVA_INT.withName("fErase"),
        ValueLayout.JAVA_INT.withName("rcPaint_left"),
        ValueLayout.JAVA_INT.withName("rcPaint_top"),
        ValueLayout.JAVA_INT.withName("rcPaint_right"),
        ValueLayout.JAVA_INT.withName("rcPaint_bottom"),
        ValueLayout.JAVA_INT.withName("fRestore"),
        ValueLayout.JAVA_INT.withName("fIncUpdate"),
        MemoryLayout.sequenceLayout(32, ValueLayout.JAVA_BYTE).withName("rgbReserved"),
        MemoryLayout.paddingLayout(4),
    ).withName("PAINTSTRUCT")
}

/** Allocates a zero-initialized Win64 PAINTSTRUCT in this arena. */
fun Arena.allocatePaintStruct(): MemorySegment =
    allocate(PaintStructLayout.SIZEOF.toLong(), PaintStructLayout.ALIGN.toLong())

internal fun lookupPaintUser32(openLibrary: () -> SymbolLookup): SymbolLookup? = try {
    openLibrary()
} catch (_: IllegalArgumentException) {
    null
}

private val paintUser32: SymbolLookup? by lazy {
    lookupPaintUser32 {
        SymbolLookup.libraryLookup("user32.dll", Arena.global())
    }
}

private fun paintDowncall(name: String, descriptor: FunctionDescriptor): MethodHandle? =
    paintUser32?.find(name)
        ?.map { Linker.nativeLinker().downcallHandle(it, descriptor) }
        ?.orElse(null)

internal val invalidateRectDescriptor: FunctionDescriptor = FunctionDescriptor.of(
    ValueLayout.JAVA_INT,
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
    ValueLayout.JAVA_INT,
)

internal val beginPaintDescriptor: FunctionDescriptor = FunctionDescriptor.of(
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
)

internal val endPaintDescriptor: FunctionDescriptor = FunctionDescriptor.of(
    ValueLayout.JAVA_INT,
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
)

private val invalidateRectHandle: MethodHandle? by lazy {
    paintDowncall("InvalidateRect", invalidateRectDescriptor)
}

private val beginPaintHandle: MethodHandle? by lazy {
    paintDowncall("BeginPaint", beginPaintDescriptor)
}

private val endPaintHandle: MethodHandle? by lazy {
    paintDowncall("EndPaint", endPaintDescriptor)
}

/** Invalidates a window rectangle through user32!InvalidateRect. */
fun invalidateRect(hwnd: MemorySegment, rect: MemorySegment, erase: Int): Int {
    val handle = invalidateRectHandle ?: return 0
    return try {
        handle.invokeExact(hwnd, rect, erase) as Int
    } catch (error: Error) {
        throw error
    } catch (error: RuntimeException) {
        throw error
    } catch (_: Throwable) {
        0
    }
}

/** Starts validation of a WM_PAINT update region through user32!BeginPaint. */
fun beginPaint(hwnd: MemorySegment, paintStruct: MemorySegment): MemorySegment {
    val handle = beginPaintHandle ?: return MemorySegment.NULL
    return try {
        handle.invokeExact(hwnd, paintStruct) as MemorySegment
    } catch (error: Error) {
        throw error
    } catch (error: RuntimeException) {
        throw error
    } catch (_: Throwable) {
        MemorySegment.NULL
    }
}

/** Ends validation of a WM_PAINT update region through user32!EndPaint. */
fun endPaint(hwnd: MemorySegment, paintStruct: MemorySegment): Int {
    val handle = endPaintHandle ?: return 0
    return try {
        handle.invokeExact(hwnd, paintStruct) as Int
    } catch (error: Error) {
        throw error
    } catch (error: RuntimeException) {
        throw error
    } catch (_: Throwable) {
        0
    }
}
