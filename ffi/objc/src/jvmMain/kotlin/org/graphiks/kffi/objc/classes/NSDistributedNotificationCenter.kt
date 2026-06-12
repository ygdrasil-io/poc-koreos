package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDistributedNotificationCenter
 * Superclass: NSNotificationCenter
 */
open class NSDistributedNotificationCenter(ptr: MemorySegment) : NSNotificationCenter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDistributedNotificationCenter") }
        
        fun notificationCenterForType(notificationCenterType: NSDistributedNotificationCenterType): MemorySegment {
            val sel = ObjCRuntime.sel("notificationCenterForType:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, notificationCenterType) as MemorySegment
        }
        
        override fun `defaultCenter`(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultCenter")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun addObserver_selector_name_object_suspensionBehavior(observer: MemorySegment, selector: MemorySegment, name: NSNotificationName, `object`: MemorySegment, suspensionBehavior: NSNotificationSuspensionBehavior): Unit {
        val sel = ObjCRuntime.sel("addObserver:selector:name:object:suspensionBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, selector, name, `object`, suspensionBehavior)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addObserver_selector_name_object_suspensionBehavior(observer: MemorySegment, selector: MemorySegment, name: NSNotificationName, `object`: String, suspensionBehavior: NSNotificationSuspensionBehavior): Unit = addObserver_selector_name_object_suspensionBehavior(observer, selector, name, ObjCRuntime.newNSString(Arena.global(), `object`), suspensionBehavior)
    
    fun postNotificationName_object_userInfo_deliverImmediately(name: NSNotificationName, `object`: MemorySegment, userInfo: MemorySegment, deliverImmediately: BOOL): Unit {
        val sel = ObjCRuntime.sel("postNotificationName:object:userInfo:deliverImmediately:")
        ObjCRuntime.msgSend(null, ptr, sel, name, `object`, userInfo, deliverImmediately)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun postNotificationName_object_userInfo_deliverImmediately(name: NSNotificationName, `object`: String, userInfo: MemorySegment, deliverImmediately: BOOL): Unit = postNotificationName_object_userInfo_deliverImmediately(name, ObjCRuntime.newNSString(Arena.global(), `object`), userInfo, deliverImmediately)
    
    fun postNotificationName_object_userInfo_options(name: NSNotificationName, `object`: MemorySegment, userInfo: MemorySegment, options: NSDistributedNotificationOptions): Unit {
        val sel = ObjCRuntime.sel("postNotificationName:object:userInfo:options:")
        ObjCRuntime.msgSend(null, ptr, sel, name, `object`, userInfo, options)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun postNotificationName_object_userInfo_options(name: NSNotificationName, `object`: String, userInfo: MemorySegment, options: NSDistributedNotificationOptions): Unit = postNotificationName_object_userInfo_options(name, ObjCRuntime.newNSString(Arena.global(), `object`), userInfo, options)
    
    override fun `addObserver_selector_name_object`(observer: MemorySegment, aSelector: MemorySegment, aName: NSNotificationName, anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObserver:selector:name:object:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, aSelector, aName, anObject)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addObserver_selector_name_object(observer: MemorySegment, aSelector: MemorySegment, aName: NSNotificationName, anObject: String): Unit = addObserver_selector_name_object(observer, aSelector, aName, ObjCRuntime.newNSString(Arena.global(), anObject))
    
    override fun `postNotificationName_object`(aName: NSNotificationName, anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("postNotificationName:object:")
        ObjCRuntime.msgSend(null, ptr, sel, aName, anObject)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun postNotificationName_object(aName: NSNotificationName, anObject: String): Unit = postNotificationName_object(aName, ObjCRuntime.newNSString(Arena.global(), anObject))
    
    override fun `postNotificationName_object_userInfo`(aName: NSNotificationName, anObject: MemorySegment, aUserInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("postNotificationName:object:userInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, aName, anObject, aUserInfo)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun postNotificationName_object_userInfo(aName: NSNotificationName, anObject: String, aUserInfo: MemorySegment): Unit = postNotificationName_object_userInfo(aName, ObjCRuntime.newNSString(Arena.global(), anObject), aUserInfo)
    
    override fun `removeObserver_name_object`(observer: MemorySegment, aName: NSNotificationName, anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObserver:name:object:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, aName, anObject)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun removeObserver_name_object(observer: MemorySegment, aName: NSNotificationName, anObject: String): Unit = removeObserver_name_object(observer, aName, ObjCRuntime.newNSString(Arena.global(), anObject))
    
    // @property suspended
    fun suspended(): BOOL {
        val sel = ObjCRuntime.sel("suspended")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSuspended(value: BOOL) {
        val sel = ObjCRuntime.sel("setSuspended:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

