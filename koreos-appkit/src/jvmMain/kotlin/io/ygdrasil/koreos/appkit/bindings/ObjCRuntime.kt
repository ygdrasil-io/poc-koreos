package io.ygdrasil.koreos.appkit.bindings

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.GroupLayout
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentHashMap

/**
 * Wraps a struct [MemorySegment] with its [MemoryLayout] for by-value struct passing
 * via Panama FFM [ObjCRuntime.msgSend]. Use this for ObjC methods that take a C struct
 * argument by value (e.g. NSRect, CGPoint, NSSize).
 *
 * Example:
 * ```kotlin
 * ObjCRuntime.msgSend(returnLayout, ptr, sel, ObjCStructArg(nsRectSegment, ObjCRuntime.nsRectLayout))
 * ```
 */
data class ObjCStructArg(val segment: MemorySegment, val layout: MemoryLayout)

/**
 * Low-level Objective-C runtime bridge via Panama FFI.
 *
 * Use [sel], [getClass] and [msgSend] to call Objective-C methods from Kotlin/JVM.
 * All ObjC object references are represented as [MemorySegment].
 *
 * JVM threads have no implicit autorelease pool. Wrap calls to factory methods
 * (those not starting with alloc/new/copy/mutableCopy) inside [autoreleasePool] to
 * ensure autoreleased objects are reclaimed correctly.
 */
object ObjCRuntime {

    private val arena: Arena = Arena.global()
    private val objcLib: SymbolLookup = run {
        // On macOS the JVM links libobjc, so try the process loader first.
        val loaderSymbol = SymbolLookup.loaderLookup().find("objc_msgSend")
        if (loaderSymbol.isPresent) {
            SymbolLookup.loaderLookup()
        } else {
            // Fallback: load by absolute path (macOS 12+)
            SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", arena)
        }
    }
    private val linker: Linker = Linker.nativeLinker()

    // ── Caches ────────────────────────────────────────────────────────────────

    private val selectorCache = ConcurrentHashMap<String, MemorySegment>()
    private val classCache    = ConcurrentHashMap<String, MemorySegment>()

    // ── Bootstrapped handles ──────────────────────────────────────────────────

    private val selRegisterNameAddr: MemorySegment =
        objcLib.find("sel_registerName").orElseThrow { UnsatisfiedLinkError("sel_registerName not found in libobjc") }

    private val objcGetClassAddr: MemorySegment =
        objcLib.find("objc_getClass").orElseThrow { UnsatisfiedLinkError("objc_getClass not found in libobjc") }

    /** Address of objc_msgSend — exposed so generated code can build typed handles. */
    val objcMsgSendAddr: MemorySegment =
        objcLib.find("objc_msgSend").orElseThrow { UnsatisfiedLinkError("objc_msgSend not found in libobjc") }

    private val ARCH: String = System.getProperty("os.arch", "")

    /** Address of objc_msgSend_stret — only valid on x86-64; null on ARM64. */
    val objcMsgSendStretAddr: MemorySegment? = if (ARCH == "x86_64")
        objcLib.find("objc_msgSend_stret").orElse(null)
    else null

    private val selRegisterNameHandle = linker.downcallHandle(
        selRegisterNameAddr,
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val objcGetClassHandle = linker.downcallHandle(
        objcGetClassAddr,
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the selector for [name], cached after the first lookup.
     * Example: `ObjCRuntime.sel("stringWithUTF8String:")`
     */
    fun sel(name: String): MemorySegment = selectorCache.getOrPut(name) {
        val cStr = arena.allocateFrom(name)
        selRegisterNameHandle.invokeExact(cStr) as MemorySegment
    }

    /**
     * Returns the Class object for [name], cached after the first lookup.
     * Example: `ObjCRuntime.getClass("NSString")`
     */
    fun getClass(name: String): MemorySegment = classCache.getOrPut(name) {
        val cStr = arena.allocateFrom(name)
        objcGetClassHandle.invokeExact(cStr) as MemorySegment
    }

    /**
     * Sends an ObjC message to [receiver] with selector [selector] and [args].
     *
     * - [returnLayout] = null for void-returning methods
     * - [returnLayout] = ValueLayout.ADDRESS for id/pointer-returning methods
     * - [returnLayout] = ValueLayout.JAVA_LONG / JAVA_INT / JAVA_DOUBLE etc. for primitives
     *
     * Each arg must be a Panama-compatible type: MemorySegment, Long, Int, Double, Float, Byte, Short, Boolean,
     * or a @JvmInline value class with a `rawValue` field of a supported primitive type.
     */
    fun msgSend(returnLayout: MemoryLayout?, receiver: MemorySegment, selector: MemorySegment, vararg args: Any): Any? {
        // Layouts must be computed BEFORE unwrapping so ObjCStructArg carries its GroupLayout.
        val argLayouts = args.map { layoutFor(it) }.toTypedArray()
        val unwrapped = args.map { unwrap(it) }.toTypedArray()
        val baseLayouts = arrayOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        val desc = if (returnLayout == null)
            FunctionDescriptor.ofVoid(*baseLayouts, *argLayouts)
        else
            FunctionDescriptor.of(returnLayout, *baseLayouts, *argLayouts)
        val handle = linker.downcallHandle(objcMsgSendAddr, desc)
        val allArgs: Array<Any> = arrayOf(receiver, selector, *unwrapped)
        return handle.invokeWithArguments(*allArgs)
    }

    /**
     * Like [msgSend] but uses objc_msgSend_stret on x86-64 for methods returning large structs.
     * On ARM64, delegates to [msgSend] (stret variant doesn't exist).
     */
    fun msgSendStret(returnLayout: MemoryLayout, receiver: MemorySegment, selector: MemorySegment, vararg args: Any): Any? {
        val addr = objcMsgSendStretAddr ?: objcMsgSendAddr
        // Layouts must be computed BEFORE unwrapping so ObjCStructArg carries its GroupLayout.
        val argLayouts = args.map { layoutFor(it) }.toTypedArray()
        val unwrapped = args.map { unwrap(it) }.toTypedArray()
        val baseLayouts = arrayOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        val desc = FunctionDescriptor.of(returnLayout, *baseLayouts, *argLayouts)
        val handle = linker.downcallHandle(addr, desc)
        val allArgs: Array<Any> = arrayOf(receiver, selector, *unwrapped)
        return handle.invokeWithArguments(*allArgs)
    }

    // ── Autorelease pool ──────────────────────────────────────────────────────

    private val autoreleasePoolPushAddr: MemorySegment =
        objcLib.find("objc_autoreleasePoolPush").orElseThrow { UnsatisfiedLinkError("objc_autoreleasePoolPush not found") }

    private val autoreleasePoolPopAddr: MemorySegment =
        objcLib.find("objc_autoreleasePoolPop").orElseThrow { UnsatisfiedLinkError("objc_autoreleasePoolPop not found") }

    @PublishedApi internal val autoreleasePoolPushHandle = linker.downcallHandle(
        autoreleasePoolPushAddr,
        FunctionDescriptor.of(ValueLayout.ADDRESS)
    )

    @PublishedApi internal val autoreleasePoolPopHandle = linker.downcallHandle(
        autoreleasePoolPopAddr,
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    )

    /**
     * Runs [block] within an ObjC autorelease pool.
     * Factory methods that return autoreleased objects should be called inside this scope.
     *
     * Example:
     * ```kotlin
     * val str = ObjCRuntime.autoreleasePool {
     *     NSString.stringWithUTF8String(cStr)
     * }
     * ```
     */
    inline fun <T> autoreleasePool(block: () -> T): T {
        val token = autoreleasePoolPushHandle.invokeExact() as MemorySegment
        return try {
            block()
        } finally {
            autoreleasePoolPopHandle.invokeExact(token)
        }
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /**
     * Creates an NSString from a Kotlin [String] using [NSString stringWithUTF8String:].
     * The returned MemorySegment is an autoreleased NSString object.
     */
    fun newNSString(alloc: Arena, value: String): MemorySegment {
        val cls = getClass("NSString")
        val cStr = alloc.allocateFrom(value)
        return msgSend(ValueLayout.ADDRESS, cls, sel("stringWithUTF8String:"), cStr) as MemorySegment
    }

    /**
     * Converts an NSString [MemorySegment] to a Kotlin [String]
     * by sending the [UTF8String] message and reading the C string.
     *
     * The raw pointer returned from native code has byteSize=0 in Panama's
     * safety model. We reinterpret it with Long.MAX_VALUE so [getString] can
     * scan for the null terminator.
     *
     * Returns an empty string if [nsString] is NULL or if [UTF8String] returns NULL
     * (e.g. deallocated object, encoding error).
     */
    fun toJavaString(nsString: MemorySegment): String {
        if (nsString == MemorySegment.NULL) return ""
        val utf8Ptr = (msgSend(ValueLayout.ADDRESS, nsString, sel("UTF8String")) as MemorySegment)
            .reinterpret(Long.MAX_VALUE)
        if (utf8Ptr == MemorySegment.NULL) return ""
        return utf8Ptr.getString(0)
    }

    // ── Struct layouts ────────────────────────────────────────────────────────

    /**
     * Memory layout for NSRect / CGRect: struct { CGFloat x, y, width, height }
     * = 4 × Double (32 bytes, 8-byte aligned).
     * On ARM64, NSRect is a Homogeneous Floating-point Aggregate (HFA) → passed / returned in d0-d3.
     */
    val nsRectLayout: GroupLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("x"),
        ValueLayout.JAVA_DOUBLE.withName("y"),
        ValueLayout.JAVA_DOUBLE.withName("width"),
        ValueLayout.JAVA_DOUBLE.withName("height"),
    )

    /**
     * Memory layout for NSPoint / CGPoint: struct { CGFloat x, y }
     * = 2 × Double (16 bytes, 8-byte aligned).
     * On ARM64, NSPoint is an HFA (Homogeneous Floating-point Aggregate) → passed/returned in d0-d1.
     */
    val nsPointLayout: GroupLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("x"),
        ValueLayout.JAVA_DOUBLE.withName("y"),
    )

    // ── msgSend returning scalar ──────────────────────────────────────────────

    /**
     * Sends an ObjC message that returns a `double` / CGFloat.
     * The generated bindings incorrectly use [ValueLayout.ADDRESS] for scalar returns;
     * use this helper for any property or method returning CGFloat/Double.
     */
    fun msgSendDouble(receiver: MemorySegment, selector: MemorySegment, vararg args: Any): Double {
        val argLayouts = args.map { layoutFor(it) }.toTypedArray()
        val unwrapped = args.map { unwrap(it) }.toTypedArray()
        val desc = FunctionDescriptor.of(
            ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, *argLayouts)
        val handle = linker.downcallHandle(objcMsgSendAddr, desc)
        val allArgs: Array<Any> = arrayOf(receiver, selector, *unwrapped)
        return handle.invokeWithArguments(*allArgs) as Double
    }

    // ── msgSend returning struct ───────────────────────────────────────────────

    /**
     * Sends an ObjC message that returns a C struct by value (e.g. NSRect).
     * [returnLayout] must be a [GroupLayout] matching the struct's memory layout.
     * Returns a [MemorySegment] containing the struct data.
     */
    fun msgSendReturningStruct(
        returnLayout: GroupLayout,
        receiver: MemorySegment,
        selector: MemorySegment,
        vararg args: Any,
    ): MemorySegment {
        val argLayouts = args.map { layoutFor(it) }.toTypedArray()
        val unwrapped = args.map { unwrap(it) }.toTypedArray()
        val baseLayouts = arrayOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        val desc = FunctionDescriptor.of(returnLayout, *baseLayouts, *argLayouts)
        val handle = linker.downcallHandle(objcMsgSendAddr, desc)
        // Panama FFM inserts a SegmentAllocator as the hidden first argument for struct-returning
        // method handles. The allocator provides memory for the returned struct on the JVM side.
        // Use DoubleArray to guarantee 8-byte alignment (required for structs containing doubles).
        val byteSize = returnLayout.byteSize().toInt()
        val heapDoubles = DoubleArray((byteSize + 7) / Double.SIZE_BYTES)
        val heapSeg = MemorySegment.ofArray(heapDoubles).asSlice(0, byteSize.toLong())
        val allocator = SegmentAllocator.prefixAllocator(heapSeg)
        val allArgs: Array<Any> = arrayOf(allocator, receiver, selector, *unwrapped)
        return handle.invokeWithArguments(*allArgs) as MemorySegment
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun layoutFor(arg: Any): MemoryLayout = when (arg) {
        is ObjCStructArg -> arg.layout
        is MemorySegment -> ValueLayout.ADDRESS
        is Long          -> ValueLayout.JAVA_LONG
        is Int           -> ValueLayout.JAVA_INT
        is Double        -> ValueLayout.JAVA_DOUBLE
        is Float         -> ValueLayout.JAVA_FLOAT
        is Byte          -> ValueLayout.JAVA_BYTE
        is Short         -> ValueLayout.JAVA_SHORT
        is Boolean       -> ValueLayout.JAVA_BOOLEAN
        // @JvmInline value classes and NS_ENUM classes (rawValue / value field)
        else             -> layoutForWrapped(arg)
    }

    private fun layoutForWrapped(arg: Any): MemoryLayout {
        val cls = arg::class.java
        val field = runCatching { cls.getDeclaredField("rawValue") }.getOrNull()
            ?: runCatching { cls.getDeclaredField("value") }.getOrNull()
            ?: throw IllegalArgumentException("Unsupported ObjC argument type: ${arg::class.qualifiedName}")
        field.isAccessible = true
        return when (field.type) {
            java.lang.Long.TYPE    -> ValueLayout.JAVA_LONG
            java.lang.Integer.TYPE -> ValueLayout.JAVA_INT
            java.lang.Double.TYPE  -> ValueLayout.JAVA_DOUBLE
            java.lang.Float.TYPE   -> ValueLayout.JAVA_FLOAT
            java.lang.Byte.TYPE    -> ValueLayout.JAVA_BYTE
            java.lang.Short.TYPE   -> ValueLayout.JAVA_SHORT
            else -> throw IllegalArgumentException(
                "Unsupported rawValue/value type (${field.type}) in: ${arg::class.qualifiedName}")
        }
    }

    /**
     * Unwraps Koreos binding types to their underlying JVM primitive so they can be
     * passed to Panama MethodHandle.invokeWithArguments.
     *
     * Three patterns exist in the generated bindings:
     * - [ObjCStructArg]: by-value C struct → unwrap to [MemorySegment]
     * - @JvmInline value classes: expose their primitive via `rawValue`
     * - Kotlin enum classes (NS_ENUM): expose their ObjC integer via `value`
     *
     * Already-primitive types (MemorySegment, Long, Int, …) are returned as-is.
     */
    private fun unwrap(arg: Any): Any = when (arg) {
        is ObjCStructArg -> arg.segment
        is MemorySegment, is Long, is Int, is Double,
        is Float, is Byte, is Short, is Boolean -> arg
        else -> {
            val cls = arg::class.java
            val field = runCatching { cls.getDeclaredField("rawValue") }.getOrNull()
                ?: runCatching { cls.getDeclaredField("value") }.getOrNull()
                ?: throw IllegalArgumentException("Cannot unwrap ObjC argument type: ${arg::class.qualifiedName}")
            field.isAccessible = true
            field.get(arg) ?: throw IllegalArgumentException("Field is null for: ${arg::class.qualifiedName}")
        }
    }
}