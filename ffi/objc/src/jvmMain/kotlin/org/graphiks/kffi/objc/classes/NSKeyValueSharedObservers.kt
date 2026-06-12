package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSKeyValueSharedObservers
 * Superclass: NSObject
 */
open class NSKeyValueSharedObservers(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSKeyValueSharedObservers") }
        
        open fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithObservableClass(observableClass: Class<*>): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObservableClass:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, observableClass) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun addSharedObserver_forKey_options_context(observer: MemorySegment, key: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSharedObserver:forKey:options:context:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, key, options, context)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun addSharedObserver_forKey_options_context(observer: MemorySegment, key: String, options: NSKeyValueObservingOptions, context: MemorySegment): Unit = addSharedObserver_forKey_options_context(observer, ObjCRuntime.newNSString(Arena.global(), key), options, context)
    
    open fun addObserver_forKeyPath_options_context(observer: MemorySegment, keyPath: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObserver:forKeyPath:options:context:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, options, context)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun addObserver_forKeyPath_options_context(observer: MemorySegment, keyPath: String, options: NSKeyValueObservingOptions, context: MemorySegment): Unit = addObserver_forKeyPath_options_context(observer, ObjCRuntime.newNSString(Arena.global(), keyPath), options, context)
    
    open fun snapshot(): MemorySegment {
        val sel = ObjCRuntime.sel("snapshot")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

