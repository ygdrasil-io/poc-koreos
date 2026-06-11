/**
 * Kotlin/JVM wrapper for Objective-C class: NSOrderedCollectionChange
 * Superclass: NSObject
 */
open class NSOrderedCollectionChange(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOrderedCollectionChange") }
        
        /** @return NSOrderedCollectionChange<ObjectType> * */
        fun changeWithObject_type_index(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("changeWithObject:type:index:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, anObject, type, index) as MemorySegment
        }
        
        /** @return NSOrderedCollectionChange<ObjectType> * */
        fun changeWithObject_type_index_associatedIndex(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger, associatedIndex: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("changeWithObject:type:index:associatedIndex:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, anObject, type, index, associatedIndex) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithObject_type_index(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObject:type:index:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anObject, type, index) as MemorySegment
    }
    
    fun initWithObject_type_index_associatedIndex(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger, associatedIndex: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObject:type:index:associatedIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anObject, type, index, associatedIndex) as MemorySegment
    }
    
    // @property object
    fun `object`(): MemorySegment {
        val sel = ObjCRuntime.sel("object")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property changeType
    fun changeType(): NSCollectionChangeType {
        val sel = ObjCRuntime.sel("changeType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionChangeType
    }
    
    // @property index
    fun index(): NSUInteger {
        val sel = ObjCRuntime.sel("index")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property associatedIndex
    fun associatedIndex(): NSUInteger {
        val sel = ObjCRuntime.sel("associatedIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

