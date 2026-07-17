package org.graphiks.kadre.ffi.win32

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/** Predefined Win32 handle for the current user's registry hive. */
val HKEY_CURRENT_USER: MemorySegment = MemorySegment.ofAddress(-2147483647L)

/** Restricts RegGetValueW to a REG_DWORD result. */
const val RRF_RT_REG_DWORD: Int = 0x00000010

/** Successful Win32 status code. */
const val ERROR_SUCCESS: Int = 0

private const val ERROR_PROC_NOT_FOUND: Int = 127

private val advapi32: SymbolLookup? by lazy {
    try {
        SymbolLookup.libraryLookup("advapi32.dll", Arena.global())
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal val regGetValueWDescriptor: FunctionDescriptor = FunctionDescriptor.of(
    ValueLayout.JAVA_INT,
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
    ValueLayout.JAVA_INT,
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
    ValueLayout.ADDRESS,
)

private val regGetValueWHandle: MethodHandle? by lazy {
    advapi32?.find("RegGetValueW")
        ?.map { Linker.nativeLinker().downcallHandle(it, regGetValueWDescriptor) }
        ?.orElse(null)
}

internal fun invokeRegistryCall(invocation: (() -> Int)?): Int =
    invocation?.invoke() ?: ERROR_PROC_NOT_FOUND

/** Read-only wrapper for Advapi32!RegGetValueW. */
fun regGetValueW(
    hKey: MemorySegment,
    subKey: MemorySegment,
    value: MemorySegment,
    flags: Int,
    type: MemorySegment,
    data: MemorySegment,
    dataSize: MemorySegment,
): Int {
    val handle = regGetValueWHandle ?: return invokeRegistryCall(null)
    return invokeRegistryCall {
        handle.invokeExact(hKey, subKey, value, flags, type, data, dataSize) as Int
    }
}
