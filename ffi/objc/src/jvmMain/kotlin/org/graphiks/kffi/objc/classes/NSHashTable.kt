package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSHashTable
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding, NSFastEnumeration
 */
open class NSHashTable(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHashTable") }
        
        /** @return NSHashTable<ObjectType> * */
        open fun hashTableWithOptions(options: NSPointerFunctionsOptions): MemorySegment {
            val sel = ObjCRuntime.sel("hashTableWithOptions:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, options) as MemorySegment
        }
        
        open fun hashTableWithWeakObjects(): MemorySegment {
            val sel = ObjCRuntime.sel("hashTableWithWeakObjects")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSHashTable<ObjectType> * */
        open fun weakObjectsHashTable(): MemorySegment {
            val sel = ObjCRuntime.sel("weakObjectsHashTable")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithOptions_capacity(options: NSPointerFunctionsOptions, initialCapacity: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithOptions:capacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options, initialCapacity) as MemorySegment
    }
    
    open fun initWithPointerFunctions_capacity(functions: MemorySegment, initialCapacity: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPointerFunctions:capacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, functions, initialCapacity) as MemorySegment
    }
    
    open fun member(`object`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("member:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `object`) as MemorySegment
    }
    
    /** @return NSEnumerator<ObjectType> * */
    open fun objectEnumerator(): MemorySegment {
        val sel = ObjCRuntime.sel("objectEnumerator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun addObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    open fun removeObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    open fun removeAllObjects(): Unit {
        val sel = ObjCRuntime.sel("removeAllObjects")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun containsObject(anObject: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsObject:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, anObject) as BOOL
    }
    
    open fun intersectsHashTable(other: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("intersectsHashTable:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
    }
    
    open fun isEqualToHashTable(other: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToHashTable:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
    }
    
    open fun isSubsetOfHashTable(other: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isSubsetOfHashTable:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
    }
    
    open fun intersectHashTable(other: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("intersectHashTable:")
        ObjCRuntime.msgSend(null, ptr, sel, other)
    }
    
    open fun unionHashTable(other: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("unionHashTable:")
        ObjCRuntime.msgSend(null, ptr, sel, other)
    }
    
    open fun minusHashTable(other: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("minusHashTable:")
        ObjCRuntime.msgSend(null, ptr, sel, other)
    }
    
    // @property pointerFunctions
    open fun pointerFunctions(): MemorySegment {
        val sel = ObjCRuntime.sel("pointerFunctions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property count
    open fun count(): NSUInteger {
        val sel = ObjCRuntime.sel("count")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property allObjects
    /** @return NSArray<ObjectType> * */
    open fun allObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("allObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property anyObject
    open fun anyObject(): MemorySegment {
        val sel = ObjCRuntime.sel("anyObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property setRepresentation
    /** @return NSSet<ObjectType> * */
    open fun setRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("setRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

