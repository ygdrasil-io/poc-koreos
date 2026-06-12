package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSNotificationCenter
 * Superclass: NSObject
 */
open class NSNotificationCenter(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNotificationCenter") }
        
        open fun defaultCenter(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultCenter")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun addObserver_selector_name_object(observer: MemorySegment, aSelector: MemorySegment, aName: NSNotificationName, anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObserver:selector:name:object:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, aSelector, aName, anObject)
    }
    
    open fun postNotification(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("postNotification:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    open fun postNotificationName_object(aName: NSNotificationName, anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("postNotificationName:object:")
        ObjCRuntime.msgSend(null, ptr, sel, aName, anObject)
    }
    
    open fun postNotificationName_object_userInfo(aName: NSNotificationName, anObject: MemorySegment, aUserInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("postNotificationName:object:userInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, aName, anObject, aUserInfo)
    }
    
    open fun removeObserver(observer: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObserver:")
        ObjCRuntime.msgSend(null, ptr, sel, observer)
    }
    
    open fun removeObserver_name_object(observer: MemorySegment, aName: NSNotificationName, anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObserver:name:object:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, aName, anObject)
    }
    
    /** @return id<NSObject> */
    open fun addObserverForName_object_queue_usingBlock(name: NSNotificationName, obj: MemorySegment, queue: MemorySegment, block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("addObserverForName:object:queue:usingBlock:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, obj, queue, block) as MemorySegment
    }
    
    // @property defaultCenter
    open fun defaultCenter(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultCenter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

