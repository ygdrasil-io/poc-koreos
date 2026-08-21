package org.graphiks.kadre.x11.binding.capture

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

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
const val XWINDOWATTR_MAP_STATE_OFFSET: Long = 84L

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
