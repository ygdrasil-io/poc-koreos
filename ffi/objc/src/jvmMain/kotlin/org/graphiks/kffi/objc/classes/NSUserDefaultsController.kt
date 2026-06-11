/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserDefaultsController
 * Superclass: NSController
 */
open class NSUserDefaultsController(ptr: MemorySegment) : NSController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserDefaultsController") }
        
        fun sharedUserDefaultsController(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedUserDefaultsController")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithDefaults_initialValues(defaults: MemorySegment, initialValues: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDefaults:initialValues:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaults, initialValues) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun revert(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("revert:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun save(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("save:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun revertToInitialValues(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("revertToInitialValues:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property sharedUserDefaultsController
    fun sharedUserDefaultsController(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedUserDefaultsController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property defaults
    fun defaults(): MemorySegment {
        val sel = ObjCRuntime.sel("defaults")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property initialValues
    /** @return NSDictionary<NSString *,id> * */
    fun initialValues(): MemorySegment {
        val sel = ObjCRuntime.sel("initialValues")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setInitialValues(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setInitialValues:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property appliesImmediately
    fun appliesImmediately(): BOOL {
        val sel = ObjCRuntime.sel("appliesImmediately")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAppliesImmediately(value: BOOL) {
        val sel = ObjCRuntime.sel("setAppliesImmediately:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasUnappliedChanges
    fun hasUnappliedChanges(): BOOL {
        val sel = ObjCRuntime.sel("hasUnappliedChanges")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property values
    fun values(): MemorySegment {
        val sel = ObjCRuntime.sel("values")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

