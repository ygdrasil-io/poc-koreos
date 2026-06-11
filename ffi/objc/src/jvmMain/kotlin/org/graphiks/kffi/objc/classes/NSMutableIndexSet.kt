/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableIndexSet
 * Superclass: NSIndexSet
 */
open class NSMutableIndexSet(ptr: MemorySegment) : NSIndexSet(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableIndexSet") }
        
    }
    
    fun addIndexes(indexSet: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, indexSet)
    }
    
    fun removeIndexes(indexSet: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, indexSet)
    }
    
    fun removeAllIndexes(): Unit {
        val sel = ObjCRuntime.sel("removeAllIndexes")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addIndex(value: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("addIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    fun removeIndex(value: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("removeIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    fun addIndexesInRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("addIndexesInRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun removeIndexesInRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("removeIndexesInRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun shiftIndexesStartingAtIndex_by(index: NSUInteger, delta: NSInteger): Unit {
        val sel = ObjCRuntime.sel("shiftIndexesStartingAtIndex:by:")
        ObjCRuntime.msgSend(null, ptr, sel, index, delta)
    }
    
}

