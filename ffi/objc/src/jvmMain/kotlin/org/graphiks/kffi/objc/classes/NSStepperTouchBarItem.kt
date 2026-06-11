/**
 * Kotlin/JVM wrapper for Objective-C class: NSStepperTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSStepperTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStepperTouchBarItem") }
        
        fun stepperTouchBarItemWithIdentifier_formatter(identifier: NSTouchBarItemIdentifier, formatter: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("stepperTouchBarItemWithIdentifier:formatter:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, formatter) as MemorySegment
        }
        
        fun stepperTouchBarItemWithIdentifier_drawingHandler(identifier: NSTouchBarItemIdentifier, drawingHandler: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("stepperTouchBarItemWithIdentifier:drawingHandler:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, drawingHandler) as MemorySegment
        }
        
    }
    
    // @property maxValue
    fun maxValue(): Double {
        val sel = ObjCRuntime.sel("maxValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setMaxValue(value: Double) {
        val sel = ObjCRuntime.sel("setMaxValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minValue
    fun minValue(): Double {
        val sel = ObjCRuntime.sel("minValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setMinValue(value: Double) {
        val sel = ObjCRuntime.sel("setMinValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property increment
    fun increment(): Double {
        val sel = ObjCRuntime.sel("increment")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setIncrement(value: Double) {
        val sel = ObjCRuntime.sel("setIncrement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property value
    fun value(): Double {
        val sel = ObjCRuntime.sel("value")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setValue(value: Double) {
        val sel = ObjCRuntime.sel("setValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property target
    fun target(): MemorySegment {
        val sel = ObjCRuntime.sel("target")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property action
    fun action(): MemorySegment {
        val sel = ObjCRuntime.sel("action")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customizationLabel
    fun customizationLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomizationLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun customizationLabelAsString(): String = ObjCRuntime.toJavaString(customizationLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCustomizationLabel(value: String) = setCustomizationLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
}

