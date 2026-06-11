/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserInterfaceCompressionOptions
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSUserInterfaceCompressionOptions(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserInterfaceCompressionOptions") }
        
        fun hideImagesOption(): MemorySegment {
            val sel = ObjCRuntime.sel("hideImagesOption")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun hideTextOption(): MemorySegment {
            val sel = ObjCRuntime.sel("hideTextOption")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun reduceMetricsOption(): MemorySegment {
            val sel = ObjCRuntime.sel("reduceMetricsOption")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun breakEqualWidthsOption(): MemorySegment {
            val sel = ObjCRuntime.sel("breakEqualWidthsOption")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun standardOptions(): MemorySegment {
            val sel = ObjCRuntime.sel("standardOptions")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun initWithIdentifier(identifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithIdentifier(identifier: String): MemorySegment = initWithIdentifier(ObjCRuntime.newNSString(Arena.global(), identifier))
    
    fun initWithCompressionOptions(options: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCompressionOptions:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options) as MemorySegment
    }
    
    fun containsOptions(options: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsOptions:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, options) as BOOL
    }
    
    fun intersectsOptions(options: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("intersectsOptions:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, options) as BOOL
    }
    
    fun optionsByAddingOptions(options: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("optionsByAddingOptions:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options) as MemorySegment
    }
    
    fun optionsByRemovingOptions(options: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("optionsByRemovingOptions:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options) as MemorySegment
    }
    
    // @property empty
    fun isEmpty(): BOOL {
        val sel = ObjCRuntime.sel("isEmpty")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property hideImagesOption
    fun hideImagesOption(): MemorySegment {
        val sel = ObjCRuntime.sel("hideImagesOption")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hideTextOption
    fun hideTextOption(): MemorySegment {
        val sel = ObjCRuntime.sel("hideTextOption")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property reduceMetricsOption
    fun reduceMetricsOption(): MemorySegment {
        val sel = ObjCRuntime.sel("reduceMetricsOption")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property breakEqualWidthsOption
    fun breakEqualWidthsOption(): MemorySegment {
        val sel = ObjCRuntime.sel("breakEqualWidthsOption")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property standardOptions
    fun standardOptions(): MemorySegment {
        val sel = ObjCRuntime.sel("standardOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

