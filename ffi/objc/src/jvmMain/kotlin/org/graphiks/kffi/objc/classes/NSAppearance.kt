/**
 * Kotlin/JVM wrapper for Objective-C class: NSAppearance
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSAppearance(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAppearance") }
        
        fun appearanceNamed(name: NSAppearanceName): MemorySegment {
            val sel = ObjCRuntime.sel("appearanceNamed:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        fun currentAppearance(): MemorySegment {
            val sel = ObjCRuntime.sel("currentAppearance")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun setCurrentAppearance(currentAppearance: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setCurrentAppearance:")
            ObjCRuntime.msgSend(null, _class, sel, currentAppearance)
        }
        
        fun currentDrawingAppearance(): MemorySegment {
            val sel = ObjCRuntime.sel("currentDrawingAppearance")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun performAsCurrentDrawingAppearance(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performAsCurrentDrawingAppearance:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    fun initWithAppearanceNamed_bundle(name: NSAppearanceName, bundle: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithAppearanceNamed:bundle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, bundle) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun bestMatchFromAppearancesWithNames(appearances: MemorySegment): NSAppearanceName {
        val sel = ObjCRuntime.sel("bestMatchFromAppearancesWithNames:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, appearances) as NSAppearanceName
    }
    
    // @property name
    fun name(): NSAppearanceName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAppearanceName
    }
    
    // @property currentAppearance
    fun currentAppearance(): MemorySegment {
        val sel = ObjCRuntime.sel("currentAppearance")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrentAppearance(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrentAppearance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentDrawingAppearance
    fun currentDrawingAppearance(): MemorySegment {
        val sel = ObjCRuntime.sel("currentDrawingAppearance")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property allowsVibrancy
    fun allowsVibrancy(): BOOL {
        val sel = ObjCRuntime.sel("allowsVibrancy")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

