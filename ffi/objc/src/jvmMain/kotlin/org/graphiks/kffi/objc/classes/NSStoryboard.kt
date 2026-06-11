/**
 * Kotlin/JVM wrapper for Objective-C class: NSStoryboard
 * Superclass: NSObject
 */
open class NSStoryboard(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStoryboard") }
        
        fun storyboardWithName_bundle(name: NSStoryboardName, storyboardBundleOrNil: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("storyboardWithName:bundle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, storyboardBundleOrNil) as MemorySegment
        }
        
        fun mainStoryboard(): MemorySegment {
            val sel = ObjCRuntime.sel("mainStoryboard")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun instantiateInitialController(): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateInitialController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun instantiateInitialControllerWithCreator(block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateInitialControllerWithCreator:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, block) as MemorySegment
    }
    
    fun instantiateControllerWithIdentifier(identifier: NSStoryboardSceneIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateControllerWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    fun instantiateControllerWithIdentifier_creator(identifier: NSStoryboardSceneIdentifier, block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("instantiateControllerWithIdentifier:creator:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier, block) as MemorySegment
    }
    
    // @property mainStoryboard
    fun mainStoryboard(): MemorySegment {
        val sel = ObjCRuntime.sel("mainStoryboard")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

