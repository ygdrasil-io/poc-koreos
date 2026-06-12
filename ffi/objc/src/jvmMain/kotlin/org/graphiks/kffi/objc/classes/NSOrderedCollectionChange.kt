package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSOrderedCollectionChange
 * Superclass: NSObject
 */
open class NSOrderedCollectionChange(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOrderedCollectionChange") }
        
        /** @return NSOrderedCollectionChange<ObjectType> * */
        open fun changeWithObject_type_index(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("changeWithObject:type:index:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, anObject, type, index) as MemorySegment
        }
        
        /** @return NSOrderedCollectionChange<ObjectType> * */
        open fun changeWithObject_type_index_associatedIndex(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger, associatedIndex: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("changeWithObject:type:index:associatedIndex:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, anObject, type, index, associatedIndex) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithObject_type_index(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObject:type:index:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anObject, type, index) as MemorySegment
    }
    
    open fun initWithObject_type_index_associatedIndex(anObject: MemorySegment, type: NSCollectionChangeType, index: NSUInteger, associatedIndex: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObject:type:index:associatedIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anObject, type, index, associatedIndex) as MemorySegment
    }
    
    // @property object
    open fun object(): MemorySegment {
        val sel = ObjCRuntime.sel("object")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property changeType
    open fun changeType(): NSCollectionChangeType {
        val sel = ObjCRuntime.sel("changeType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionChangeType
    }
    
    // @property index
    open fun index(): NSUInteger {
        val sel = ObjCRuntime.sel("index")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property associatedIndex
    open fun associatedIndex(): NSUInteger {
        val sel = ObjCRuntime.sel("associatedIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

