package org.graphiks.kadre.ffi.win32.generated

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

private object kextract_runtime {
    val C_BOOL: ValueLayout = ValueLayout.JAVA_BOOLEAN
    val C_CHAR: ValueLayout = ValueLayout.JAVA_BYTE
    val C_SHORT: ValueLayout = ValueLayout.JAVA_SHORT
    val C_INT: ValueLayout = ValueLayout.JAVA_INT
    val C_LONG: ValueLayout = ValueLayout.JAVA_LONG
    val C_LONG_LONG: ValueLayout = ValueLayout.JAVA_LONG
    val C_FLOAT: ValueLayout = ValueLayout.JAVA_FLOAT
    val C_DOUBLE: ValueLayout = ValueLayout.JAVA_DOUBLE
    val C_POINTER: ValueLayout = ValueLayout.ADDRESS
}

private val _DLL_DWMAPI_DLL: SymbolLookup? = try {
    SymbolLookup.libraryLookup("Dwmapi.dll", Arena.global())
} catch (ex: Throwable) {
    null
}

private fun _lookup(symbol: String): SymbolLookup {
    return when (symbol) {
        "DwmSetWindowAttribute", "DwmEnableBlurBehindWindow", "DwmExtendFrameIntoClientArea" -> _DLL_DWMAPI_DLL ?: SymbolLookup.loaderLookup()
        else -> SymbolLookup.loaderLookup()
    }
}

/**
 * {@snippet lang=c : typedef Int BOOL;}
 */
typealias BOOL = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Char BYTE;}
 */
typealias BYTE = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Short WORD;}
 */
typealias WORD = Short

/**
 * {@snippet lang=c : typedef UNSIGNED = Long DWORD;}
 */
typealias DWORD = Long

/**
 * {@snippet lang=c : typedef Int INT;}
 */
typealias INT = Int

/**
 * {@snippet lang=c : typedef Long LONG;}
 */
typealias LONG = Long

/**
 * {@snippet lang=c : typedef LongLong LONGLONG;}
 */
typealias LONGLONG = Long

/**
 * {@snippet lang=c : typedef LongLong LONG_PTR;}
 */
typealias LONG_PTR = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong ULONG_PTR;}
 */
typealias ULONG_PTR = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Int UINT;}
 */
typealias UINT = Int

/**
 * {@snippet lang=c : typedef Short SHORT;}
 */
typealias SHORT = Short

/**
 * {@snippet lang=c : typedef UNSIGNED = Short ATOM;}
 */
typealias ATOM = Short

/**
 * {@snippet lang=c : typedef Long HRESULT;}
 */
typealias HRESULT = Long

/**
 * {@snippet lang=c : DwmSetWindowAttribute typedef HRESULT = Long(typedef HWND = (Void)*,typedef DWORD = UNSIGNED = Long,typedef LPCVOID = (Void)*,typedef DWORD = UNSIGNED = Long)
 */
private val DwmSetWindowAttribute_DESC: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
private val DwmSetWindowAttribute_ADDR: MemorySegment = _lookup("DwmSetWindowAttribute").find("DwmSetWindowAttribute").orElseThrow()
private val DwmSetWindowAttribute_HANDLE: MethodHandle = Linker.nativeLinker().downcallHandle(DwmSetWindowAttribute_ADDR, DwmSetWindowAttribute_DESC)

fun DwmSetWindowAttribute(arg0: MemorySegment, arg1: Long, arg2: MemorySegment, arg3: Long): Long {
    try {
        return DwmSetWindowAttribute_HANDLE.invokeExact(arg0, arg1, arg2, arg3) as Long
    } catch (ex: Error) {
        throw ex
    } catch (ex: RuntimeException) {
        throw ex
    } catch (ex: Throwable) {
        throw AssertionError("should not reach here", ex)
    }
}

/**
 * {@snippet lang=c : DwmEnableBlurBehindWindow typedef HRESULT = Long(typedef HWND = (Void)*,(Void)*)
 */
private val DwmEnableBlurBehindWindow_DESC: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val DwmEnableBlurBehindWindow_ADDR: MemorySegment = _lookup("DwmEnableBlurBehindWindow").find("DwmEnableBlurBehindWindow").orElseThrow()
private val DwmEnableBlurBehindWindow_HANDLE: MethodHandle = Linker.nativeLinker().downcallHandle(DwmEnableBlurBehindWindow_ADDR, DwmEnableBlurBehindWindow_DESC)

fun DwmEnableBlurBehindWindow(arg0: MemorySegment, arg1: MemorySegment): Long {
    try {
        return DwmEnableBlurBehindWindow_HANDLE.invokeExact(arg0, arg1) as Long
    } catch (ex: Error) {
        throw ex
    } catch (ex: RuntimeException) {
        throw ex
    } catch (ex: Throwable) {
        throw AssertionError("should not reach here", ex)
    }
}

/**
 * {@snippet lang=c : DwmExtendFrameIntoClientArea typedef HRESULT = Long(typedef HWND = (Void)*,(Void)*)
 */
private val DwmExtendFrameIntoClientArea_DESC: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val DwmExtendFrameIntoClientArea_ADDR: MemorySegment = _lookup("DwmExtendFrameIntoClientArea").find("DwmExtendFrameIntoClientArea").orElseThrow()
private val DwmExtendFrameIntoClientArea_HANDLE: MethodHandle = Linker.nativeLinker().downcallHandle(DwmExtendFrameIntoClientArea_ADDR, DwmExtendFrameIntoClientArea_DESC)

fun DwmExtendFrameIntoClientArea(arg0: MemorySegment, arg1: MemorySegment): Long {
    try {
        return DwmExtendFrameIntoClientArea_HANDLE.invokeExact(arg0, arg1) as Long
    } catch (ex: Error) {
        throw ex
    } catch (ex: RuntimeException) {
        throw ex
    } catch (ex: Throwable) {
        throw AssertionError("should not reach here", ex)
    }
}

