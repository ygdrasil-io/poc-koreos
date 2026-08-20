package org.graphiks.kadre.x11.binding.capture

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
private val generatedLookup = MethodHandles.lookup()
private val generatedClass = Class.forName(
    "org.graphiks.kffi.x11.generated.Xlib_hKt",
    false,
    Thread.currentThread().contextClassLoader,
)

private fun generatedFunction(name: String): MethodHandle? = try {
    val method = generatedClass.methods.single { it.name == name }
    generatedLookup.unreflect(method)
} catch (_: Throwable) {
    null
}

val xShmQueryExtension: MethodHandle? by lazy { generatedFunction("XShmQueryExtension") }
val xShmCreateImage: MethodHandle? by lazy { generatedFunction("XShmCreateImage") }
val xShmAttach: MethodHandle? by lazy { generatedFunction("XShmAttach") }
val xShmDetach: MethodHandle? by lazy { generatedFunction("XShmDetach") }
val xShmGetImage: MethodHandle? by lazy { generatedFunction("XShmGetImage") }
val xGetImage: MethodHandle? by lazy { generatedFunction("XGetImage") }
val xDestroyImage: MethodHandle? by lazy { generatedFunction("XDestroyImage") }
val xDefaultScreen: MethodHandle? by lazy { generatedFunction("XDefaultScreen") }
val xDefaultVisual: MethodHandle? by lazy { generatedFunction("XDefaultVisual") }
val xDefaultDepth: MethodHandle? by lazy { generatedFunction("XDefaultDepth") }
val xQueryTree: MethodHandle? by lazy { generatedFunction("XQueryTree") }
val xGetWindowAttributes: MethodHandle? by lazy { generatedFunction("XGetWindowAttributes") }
val xSync: MethodHandle? by lazy { generatedFunction("XSync") }
val xCompositeNameWindowPixmap: MethodHandle? by lazy {
    generatedFunction("XCompositeNameWindowPixmap")
}

private val libc: SymbolLookup? by lazy {
    try {
        SymbolLookup.libraryLookup("libc.so.6", Arena.global())
    } catch (_: Throwable) {
        null
    }
}

private val linker = Linker.nativeLinker()

private fun SymbolLookup?.downcall(name: String, descriptor: FunctionDescriptor): MethodHandle? {
    this ?: return null
    return find(name).map { linker.downcallHandle(it, descriptor) }.orElse(null)
}

val shmget: MethodHandle? by lazy {
    libc.downcall(
        "shmget",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT),
    )
}
val shmat: MethodHandle? by lazy {
    libc.downcall(
        "shmat",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
    )
}
val shmdt: MethodHandle? by lazy {
    libc.downcall("shmdt", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
}
val shmctl: MethodHandle? by lazy {
    libc.downcall(
        "shmctl",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
    )
}

const val XIMAGE_DATA_OFFSET: Long = 16L
const val XIMAGE_BYTES_PER_LINE_OFFSET: Long = 44L
const val XIMAGE_BITS_PER_PIXEL_OFFSET: Long = 48L
const val XSHM_SEGINFO_SIZE: Long = 24L
const val XSHM_SHMPIX_OFFSET: Long = 0L
const val XSHM_SHMD_OFFSET: Long = 8L
const val XSHM_READONLY_OFFSET: Long = 12L
const val XSHM_ADDR_OFFSET: Long = 16L
const val XWINDOWATTR_MAP_STATE_OFFSET: Long = 84L

private fun generatedConstant(name: String, fallback: Any): Any = try {
    generatedClass.methods.first { it.name == name }.invoke(null)
} catch (_: Throwable) {
    fallback
}

val AnyPropertyType: Long = generatedConstant("AnyPropertyType", 0L) as Long
val CompositeRedirectAutomatic: Int = generatedConstant("CompositeRedirectAutomatic", 1) as Int
const val Xlib_ZPixmap: Int = 2
const val Xlib_AllPlanes: Long = -1L
const val XSHM_ZPIXMAP: Int = 2
const val IPC_PRIVATE: Int = 0
const val IPC_CREAT: Int = 512
const val IPC_RMID: Int = 0
const val IsViewable: Int = 2

fun bgraToRgba(data: ByteArray): ByteArray {
    val result = data.copyOf()
    var i = 0
    while (i + 4 <= result.size) {
        val b = result[i]
        val r = result[i + 2]
        result[i] = r
        result[i + 2] = b
        i += 4
    }
    return result
}
