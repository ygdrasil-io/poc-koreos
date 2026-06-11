/**
 * Kotlin/JVM wrapper for Objective-C class: NSSortDescriptor
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSSortDescriptor(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSortDescriptor") }
        
        fun sortDescriptorWithKey_ascending(key: MemorySegment, ascending: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("sortDescriptorWithKey:ascending:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, key, ascending) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun sortDescriptorWithKey_ascending(key: String, ascending: BOOL): MemorySegment = sortDescriptorWithKey_ascending(ObjCRuntime.newNSString(Arena.global(), key), ascending)
        
        fun sortDescriptorWithKey_ascending_selector(key: MemorySegment, ascending: BOOL, selector: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sortDescriptorWithKey:ascending:selector:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, key, ascending, selector) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun sortDescriptorWithKey_ascending_selector(key: String, ascending: BOOL, selector: MemorySegment): MemorySegment = sortDescriptorWithKey_ascending_selector(ObjCRuntime.newNSString(Arena.global(), key), ascending, selector)
        
        fun sortDescriptorWithKey_ascending_comparator(key: MemorySegment, ascending: BOOL, cmptr: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sortDescriptorWithKey:ascending:comparator:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, key, ascending, cmptr) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun sortDescriptorWithKey_ascending_comparator(key: String, ascending: BOOL, cmptr: MemorySegment): MemorySegment = sortDescriptorWithKey_ascending_comparator(ObjCRuntime.newNSString(Arena.global(), key), ascending, cmptr)
        
    }
    
    fun initWithKey_ascending(key: MemorySegment, ascending: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKey:ascending:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, ascending) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithKey_ascending(key: String, ascending: BOOL): MemorySegment = initWithKey_ascending(ObjCRuntime.newNSString(Arena.global(), key), ascending)
    
    fun initWithKey_ascending_selector(key: MemorySegment, ascending: BOOL, selector: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKey:ascending:selector:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, ascending, selector) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithKey_ascending_selector(key: String, ascending: BOOL, selector: MemorySegment): MemorySegment = initWithKey_ascending_selector(ObjCRuntime.newNSString(Arena.global(), key), ascending, selector)
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun allowEvaluation(): Unit {
        val sel = ObjCRuntime.sel("allowEvaluation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun initWithKey_ascending_comparator(key: MemorySegment, ascending: BOOL, cmptr: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKey:ascending:comparator:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, ascending, cmptr) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithKey_ascending_comparator(key: String, ascending: BOOL, cmptr: MemorySegment): MemorySegment = initWithKey_ascending_comparator(ObjCRuntime.newNSString(Arena.global(), key), ascending, cmptr)
    
    fun compareObject_toObject(object1: MemorySegment, object2: MemorySegment): NSComparisonResult {
        val sel = ObjCRuntime.sel("compareObject:toObject:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, object1, object2) as NSComparisonResult
    }
    
    // @property key
    fun key(): MemorySegment {
        val sel = ObjCRuntime.sel("key")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun keyAsString(): String = ObjCRuntime.toJavaString(key())
    
    // @property ascending
    fun ascending(): BOOL {
        val sel = ObjCRuntime.sel("ascending")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property selector
    fun selector(): MemorySegment {
        val sel = ObjCRuntime.sel("selector")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property comparator
    fun comparator(): MemorySegment {
        val sel = ObjCRuntime.sel("comparator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property reversedSortDescriptor
    fun reversedSortDescriptor(): MemorySegment {
        val sel = ObjCRuntime.sel("reversedSortDescriptor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _sortDescriptorFlags: NSUInteger
    // ivar: _key: MemorySegment
    // ivar: _selector: MemorySegment
    // ivar: _selectorOrBlock: MemorySegment
}

