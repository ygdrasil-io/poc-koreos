/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableData
 * Superclass: NSData
 */
open class NSMutableData(ptr: MemorySegment) : NSData(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableData") }
        
    }
    
    // @property mutableBytes
    fun mutableBytes(): MemorySegment {
        val sel = ObjCRuntime.sel("mutableBytes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property length
    fun length(): NSUInteger {
        val sel = ObjCRuntime.sel("length")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setLength(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setLength:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSExtendedMutableData on NSMutableData ─────────────────────────────────────────

fun NSMutableData.appendBytes_length(bytes: MemorySegment, length: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("appendBytes:length:")
    ObjCRuntime.msgSend(null, ptr, sel, bytes, length)
}

fun NSMutableData.appendData(other: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendData:")
    ObjCRuntime.msgSend(null, ptr, sel, other)
}

fun NSMutableData.increaseLengthBy(extraLength: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("increaseLengthBy:")
    ObjCRuntime.msgSend(null, ptr, sel, extraLength)
}

fun NSMutableData.replaceBytesInRange_withBytes(range: NSRange, bytes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("replaceBytesInRange:withBytes:")
    ObjCRuntime.msgSend(null, ptr, sel, range, bytes)
}

fun NSMutableData.resetBytesInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("resetBytesInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableData.setData(`data`: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setData:")
    ObjCRuntime.msgSend(null, ptr, sel, `data`)
}

fun NSMutableData.replaceBytesInRange_withBytes_length(range: NSRange, replacementBytes: MemorySegment, replacementLength: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("replaceBytesInRange:withBytes:length:")
    ObjCRuntime.msgSend(null, ptr, sel, range, replacementBytes, replacementLength)
}

// ── Category: NSMutableDataCreation on NSMutableData ─────────────────────────────────────────

fun NSMutableData.initWithCapacity(capacity: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCapacity:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, capacity) as MemorySegment
}

fun NSMutableData.initWithLength(length: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("initWithLength:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, length) as MemorySegment
}

// Class method: +[NSMutableData dataWithCapacity:]
fun NSMutableData_dataWithCapacity(aNumItems: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("dataWithCapacity:")
    val cls = ObjCRuntime.getClass("NSMutableData")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, aNumItems) as MemorySegment
}

// Class method: +[NSMutableData dataWithLength:]
fun NSMutableData_dataWithLength(length: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("dataWithLength:")
    val cls = ObjCRuntime.getClass("NSMutableData")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, length) as MemorySegment
}

// ── Category: NSMutableDataCompression on NSMutableData ─────────────────────────────────────────

fun NSMutableData.decompressUsingAlgorithm_error(algorithm: NSDataCompressionAlgorithm, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("decompressUsingAlgorithm:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, algorithm, error) as BOOL
}

fun NSMutableData.compressUsingAlgorithm_error(algorithm: NSDataCompressionAlgorithm, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("compressUsingAlgorithm:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, algorithm, error) as BOOL
}

