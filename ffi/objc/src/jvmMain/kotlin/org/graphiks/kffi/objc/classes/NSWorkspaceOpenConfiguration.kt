package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSWorkspaceOpenConfiguration
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSWorkspaceOpenConfiguration(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWorkspaceOpenConfiguration") }
        
        open fun configuration(): MemorySegment {
            val sel = ObjCRuntime.sel("configuration")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property promptsUserIfNeeded
    open fun promptsUserIfNeeded(): BOOL {
        val sel = ObjCRuntime.sel("promptsUserIfNeeded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setPromptsUserIfNeeded(value: BOOL) {
        val sel = ObjCRuntime.sel("setPromptsUserIfNeeded:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property addsToRecentItems
    open fun addsToRecentItems(): BOOL {
        val sel = ObjCRuntime.sel("addsToRecentItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAddsToRecentItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setAddsToRecentItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property activates
    open fun activates(): BOOL {
        val sel = ObjCRuntime.sel("activates")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setActivates(value: BOOL) {
        val sel = ObjCRuntime.sel("setActivates:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hides
    open fun hides(): BOOL {
        val sel = ObjCRuntime.sel("hides")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHides(value: BOOL) {
        val sel = ObjCRuntime.sel("setHides:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hidesOthers
    open fun hidesOthers(): BOOL {
        val sel = ObjCRuntime.sel("hidesOthers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHidesOthers(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidesOthers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property forPrinting
    open fun isForPrinting(): BOOL {
        val sel = ObjCRuntime.sel("isForPrinting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setForPrinting(value: BOOL) {
        val sel = ObjCRuntime.sel("setForPrinting:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property createsNewApplicationInstance
    open fun createsNewApplicationInstance(): BOOL {
        val sel = ObjCRuntime.sel("createsNewApplicationInstance")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setCreatesNewApplicationInstance(value: BOOL) {
        val sel = ObjCRuntime.sel("setCreatesNewApplicationInstance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsRunningApplicationSubstitution
    open fun allowsRunningApplicationSubstitution(): BOOL {
        val sel = ObjCRuntime.sel("allowsRunningApplicationSubstitution")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsRunningApplicationSubstitution(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsRunningApplicationSubstitution:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property arguments
    /** @return NSArray<NSString *> * */
    open fun arguments(): MemorySegment {
        val sel = ObjCRuntime.sel("arguments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setArguments(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setArguments:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property environment
    /** @return NSDictionary<NSString *,NSString *> * */
    open fun environment(): MemorySegment {
        val sel = ObjCRuntime.sel("environment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setEnvironment(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEnvironment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property appleEvent
    open fun appleEvent(): MemorySegment {
        val sel = ObjCRuntime.sel("appleEvent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setAppleEvent(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAppleEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property architecture
    open fun architecture(): cpu_type_t {
        val sel = ObjCRuntime.sel("architecture")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as cpu_type_t
    }
    open fun setArchitecture(value: cpu_type_t) {
        val sel = ObjCRuntime.sel("setArchitecture:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property requiresUniversalLinks
    open fun requiresUniversalLinks(): BOOL {
        val sel = ObjCRuntime.sel("requiresUniversalLinks")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setRequiresUniversalLinks(value: BOOL) {
        val sel = ObjCRuntime.sel("setRequiresUniversalLinks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

