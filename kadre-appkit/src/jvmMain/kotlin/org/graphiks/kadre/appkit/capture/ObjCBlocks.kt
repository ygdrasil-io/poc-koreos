package org.graphiks.kadre.appkit.capture

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object ObjCBlocks {

    private val NSConcreteGlobalBlock: MemorySegment

    init {
        val libobjc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
        NSConcreteGlobalBlock = libobjc.find("_NSConcreteGlobalBlock")
            .orElseThrow { UnsatisfiedLinkError("_NSConcreteGlobalBlock not found") }
    }

    private val blockStruct = MemoryLayout.structLayout(
        ADDRESS.withName("isa"),
        JAVA_INT.withName("flags"),
        JAVA_INT.withName("reserved"),
        ADDRESS.withName("invoke"),
        ADDRESS.withName("descriptor"),
    )

    private val descriptorStruct = MemoryLayout.structLayout(
        JAVA_LONG.withName("reserved"),
        JAVA_LONG.withName("size"),
    )

    fun create(invokeFn: MemorySegment, arena: Arena): MemorySegment {
        val descriptor = arena.allocate(descriptorStruct)
        descriptorStruct.varHandle(MemoryLayout.PathElement.groupElement("reserved"))
            .set(descriptor, 0L)
        descriptorStruct.varHandle(MemoryLayout.PathElement.groupElement("size"))
            .set(descriptor, blockStruct.byteSize())

        val block = arena.allocate(blockStruct)
        blockStruct.varHandle(MemoryLayout.PathElement.groupElement("isa"))
            .set(block, NSConcreteGlobalBlock)
        blockStruct.varHandle(MemoryLayout.PathElement.groupElement("flags"))
            .set(block, 0)
        blockStruct.varHandle(MemoryLayout.PathElement.groupElement("invoke"))
            .set(block, invokeFn)
        blockStruct.varHandle(MemoryLayout.PathElement.groupElement("descriptor"))
            .set(block, descriptor)
        return block
    }
}

internal class ObjCCallback2(
    private val onResult: (MemorySegment?, MemorySegment?) -> Unit = { _, _ -> },
) {
    private val latch = CountDownLatch(1)
    var result: MemorySegment? = null
    var error: MemorySegment? = null

    @Suppress("unused")
    fun onCallback(block: MemorySegment, arg1: MemorySegment, arg2: MemorySegment) {
        result = if (arg1 == MemorySegment.NULL) null else arg1
        error = if (arg2 == MemorySegment.NULL) null else arg2
        onResult(result, error)
        latch.countDown()
    }

    val methodHandle: MethodHandle by lazy {
        MethodHandles.lookup().findVirtual(
            ObjCCallback2::class.java,
            "onCallback",
            MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java),
        ).bindTo(this)
    }

    val fnDescriptor: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS)

    fun await(timeoutMs: Long = 5000): Boolean = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
}

internal class ObjCCallback1(
    private val onResult: (MemorySegment?) -> Unit = {},
) {
    private val latch = CountDownLatch(1)
    var result: MemorySegment? = null

    @Suppress("unused")
    fun onCallback(block: MemorySegment, arg1: MemorySegment) {
        result = if (arg1 == MemorySegment.NULL) null else arg1
        onResult(result)
        latch.countDown()
    }

    val methodHandle: MethodHandle by lazy {
        MethodHandles.lookup().findVirtual(
            ObjCCallback1::class.java,
            "onCallback",
            MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java),
        ).bindTo(this)
    }

    val fnDescriptor: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS)

    fun await(timeoutMs: Long = 5000): Boolean = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
}
