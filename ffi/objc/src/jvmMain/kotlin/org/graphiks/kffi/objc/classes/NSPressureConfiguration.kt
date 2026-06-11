/**
 * Kotlin/JVM wrapper for Objective-C class: NSPressureConfiguration
 * Superclass: NSObject
 */
open class NSPressureConfiguration(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPressureConfiguration") }
        
    }
    
    fun initWithPressureBehavior(pressureBehavior: NSPressureBehavior): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPressureBehavior:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pressureBehavior) as MemorySegment
    }
    
    fun `set`(): Unit {
        val sel = ObjCRuntime.sel("set")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property pressureBehavior
    fun pressureBehavior(): NSPressureBehavior {
        val sel = ObjCRuntime.sel("pressureBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPressureBehavior
    }
    
}

