/**
 * Kotlin/JVM wrapper for Objective-C class: NSHashTable
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding, NSFastEnumeration
 */
open class NSHashTable(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHashTable") }
        
        /** @return NSHashTable<ObjectType> * */
        fun hashTableWithOptions(options: NSPointerFunctionsOptions): MemorySegment {
            val sel = ObjCRuntime.sel("hashTableWithOptions:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, options) as MemorySegment
        }
        
        fun hashTableWithWeakObjects(): MemorySegment {
            val sel = ObjCRuntime.sel("hashTableWithWeakObjects")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSHashTable<ObjectType> * */
        fun weakObjectsHashTable(): MemorySegment {
            val sel = ObjCRuntime.sel("weakObjectsHashTable")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithOptions_capacity(options: NSPointerFunctionsOptions, initialCapacity: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithOptions:capacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options, initialCapacity) as MemorySegment
    }
    
    fun initWithPointerFunctions_capacity(functions: MemorySegment, initialCapacity: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPointerFunctions:capacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, functions, initialCapacity) as MemorySegment
    }
    
    fun member(`object`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("member:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `object`) as MemorySegment
    }
    
    /** @return NSEnumerator<ObjectType> * */
    fun objectEnumerator(): MemorySegment {
        val sel = ObjCRuntime.sel("objectEnumerator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun addObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun removeObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun removeAllObjects(): Unit {
        val sel = ObjCRuntime.sel("removeAllObjects")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun containsObject(anObject: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsObject:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, anObject) as BOOL
    }
    
    fun intersectsHashTable(other: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("intersectsHashTable:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
    }
    
    fun isEqualToHashTable(other: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToHashTable:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
    }
    
    fun isSubsetOfHashTable(other: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isSubsetOfHashTable:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
    }
    
    fun intersectHashTable(other: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("intersectHashTable:")
        ObjCRuntime.msgSend(null, ptr, sel, other)
    }
    
    fun unionHashTable(other: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("unionHashTable:")
        ObjCRuntime.msgSend(null, ptr, sel, other)
    }
    
    fun minusHashTable(other: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("minusHashTable:")
        ObjCRuntime.msgSend(null, ptr, sel, other)
    }
    
    // @property pointerFunctions
    fun pointerFunctions(): MemorySegment {
        val sel = ObjCRuntime.sel("pointerFunctions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property count
    fun count(): NSUInteger {
        val sel = ObjCRuntime.sel("count")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property allObjects
    /** @return NSArray<ObjectType> * */
    fun allObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("allObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property anyObject
    fun anyObject(): MemorySegment {
        val sel = ObjCRuntime.sel("anyObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property setRepresentation
    /** @return NSSet<ObjectType> * */
    fun setRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("setRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

