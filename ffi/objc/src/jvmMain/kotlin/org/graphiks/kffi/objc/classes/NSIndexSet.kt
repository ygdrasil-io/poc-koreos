/**
 * Kotlin/JVM wrapper for Objective-C class: NSIndexSet
 * Superclass: NSObject
 * Protocols: NSCopying, NSMutableCopying, NSSecureCoding
 */
open class NSIndexSet(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSIndexSet") }
        
        fun indexSet(): MemorySegment {
            val sel = ObjCRuntime.sel("indexSet")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun indexSetWithIndex(value: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("indexSetWithIndex:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, value) as MemorySegment
        }
        
        fun indexSetWithIndexesInRange(range: NSRange): MemorySegment {
            val sel = ObjCRuntime.sel("indexSetWithIndexesInRange:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
        }
        
    }
    
    fun initWithIndexesInRange(range: NSRange): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIndexesInRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
    }
    
    fun initWithIndexSet(indexSet: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIndexSet:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexSet) as MemorySegment
    }
    
    fun initWithIndex(value: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun isEqualToIndexSet(indexSet: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToIndexSet:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexSet) as BOOL
    }
    
    fun indexGreaterThanIndex(value: NSUInteger): NSUInteger {
        val sel = ObjCRuntime.sel("indexGreaterThanIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, value) as NSUInteger
    }
    
    fun indexLessThanIndex(value: NSUInteger): NSUInteger {
        val sel = ObjCRuntime.sel("indexLessThanIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, value) as NSUInteger
    }
    
    fun indexGreaterThanOrEqualToIndex(value: NSUInteger): NSUInteger {
        val sel = ObjCRuntime.sel("indexGreaterThanOrEqualToIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, value) as NSUInteger
    }
    
    fun indexLessThanOrEqualToIndex(value: NSUInteger): NSUInteger {
        val sel = ObjCRuntime.sel("indexLessThanOrEqualToIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, value) as NSUInteger
    }
    
    fun getIndexes_maxCount_inIndexRange(indexBuffer: MemorySegment, bufferSize: NSUInteger, range: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("getIndexes:maxCount:inIndexRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, indexBuffer, bufferSize, range) as NSUInteger
    }
    
    fun countOfIndexesInRange(range: NSRange): NSUInteger {
        val sel = ObjCRuntime.sel("countOfIndexesInRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSUInteger
    }
    
    fun containsIndex(value: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("containsIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, value) as BOOL
    }
    
    fun containsIndexesInRange(range: NSRange): BOOL {
        val sel = ObjCRuntime.sel("containsIndexesInRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as BOOL
    }
    
    fun containsIndexes(indexSet: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsIndexes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexSet) as BOOL
    }
    
    fun intersectsIndexesInRange(range: NSRange): BOOL {
        val sel = ObjCRuntime.sel("intersectsIndexesInRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as BOOL
    }
    
    fun enumerateIndexesUsingBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateIndexesUsingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    fun enumerateIndexesWithOptions_usingBlock(opts: NSEnumerationOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateIndexesWithOptions:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, opts, block)
    }
    
    fun enumerateIndexesInRange_options_usingBlock(range: NSRange, opts: NSEnumerationOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateIndexesInRange:options:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), opts, block)
    }
    
    fun indexPassingTest(predicate: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("indexPassingTest:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, predicate) as NSUInteger
    }
    
    fun indexWithOptions_passingTest(opts: NSEnumerationOptions, predicate: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("indexWithOptions:passingTest:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, opts, predicate) as NSUInteger
    }
    
    fun indexInRange_options_passingTest(range: NSRange, opts: NSEnumerationOptions, predicate: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("indexInRange:options:passingTest:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), opts, predicate) as NSUInteger
    }
    
    fun indexesPassingTest(predicate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("indexesPassingTest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, predicate) as MemorySegment
    }
    
    fun indexesWithOptions_passingTest(opts: NSEnumerationOptions, predicate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("indexesWithOptions:passingTest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, opts, predicate) as MemorySegment
    }
    
    fun indexesInRange_options_passingTest(range: NSRange, opts: NSEnumerationOptions, predicate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("indexesInRange:options:passingTest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), opts, predicate) as MemorySegment
    }
    
    fun enumerateRangesUsingBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateRangesUsingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    fun enumerateRangesWithOptions_usingBlock(opts: NSEnumerationOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateRangesWithOptions:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, opts, block)
    }
    
    fun enumerateRangesInRange_options_usingBlock(range: NSRange, opts: NSEnumerationOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateRangesInRange:options:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), opts, block)
    }
    
    // @property count
    fun count(): NSUInteger {
        val sel = ObjCRuntime.sel("count")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property firstIndex
    fun firstIndex(): NSUInteger {
        val sel = ObjCRuntime.sel("firstIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property lastIndex
    fun lastIndex(): NSUInteger {
        val sel = ObjCRuntime.sel("lastIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _indexSetFlags: MemorySegment
    // ivar: _internal: MemorySegment
}

