package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSStoryboard
 * Superclass: NSObject
 */
open class NSStoryboard(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStoryboard") }
        
        open fun storyboardWithName_bundle(name: NSStoryboardName, storyboardBundleOrNil: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("storyboardWithName:bundle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, storyboardBundleOrNil) as MemorySegment
        }
        
        open fun mainStoryboard(): MemorySegment {
            val sel = ObjCRuntime.sel("mainStoryboard")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun instantiateInitialController(): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateInitialController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun instantiateInitialControllerWithCreator(block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateInitialControllerWithCreator:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, block) as MemorySegment
    }
    
    open fun instantiateControllerWithIdentifier(identifier: NSStoryboardSceneIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateControllerWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    open fun instantiateControllerWithIdentifier_creator(identifier: NSStoryboardSceneIdentifier, block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateControllerWithIdentifier:creator:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier, block) as MemorySegment
    }
    
    // @property mainStoryboard
}

