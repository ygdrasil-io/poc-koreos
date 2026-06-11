/**
 * Kotlin/JVM wrapper for Objective-C class: NSLevelIndicatorCell
 * Superclass: NSActionCell
 */
open class NSLevelIndicatorCell(ptr: MemorySegment) : NSActionCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLevelIndicatorCell") }
        
    }
    
    fun initWithLevelIndicatorStyle(levelIndicatorStyle: NSLevelIndicatorStyle): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLevelIndicatorStyle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, levelIndicatorStyle) as MemorySegment
    }
    
    fun rectOfTickMarkAtIndex(index: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("rectOfTickMarkAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, index) as NSRect
    }
    
    fun tickMarkValueAtIndex(index: NSInteger): Double {
        val sel = ObjCRuntime.sel("tickMarkValueAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, index) as Double
    }
    
    // @property levelIndicatorStyle
    fun levelIndicatorStyle(): NSLevelIndicatorStyle {
        val sel = ObjCRuntime.sel("levelIndicatorStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLevelIndicatorStyle
    }
    fun setLevelIndicatorStyle(value: NSLevelIndicatorStyle) {
        val sel = ObjCRuntime.sel("setLevelIndicatorStyle:")
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
    
    // @property maxValue
    fun maxValue(): Double {
        val sel = ObjCRuntime.sel("maxValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setMaxValue(value: Double) {
        val sel = ObjCRuntime.sel("setMaxValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property warningValue
    fun warningValue(): Double {
        val sel = ObjCRuntime.sel("warningValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setWarningValue(value: Double) {
        val sel = ObjCRuntime.sel("setWarningValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property criticalValue
    fun criticalValue(): Double {
        val sel = ObjCRuntime.sel("criticalValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setCriticalValue(value: Double) {
        val sel = ObjCRuntime.sel("setCriticalValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tickMarkPosition
    fun tickMarkPosition(): NSTickMarkPosition {
        val sel = ObjCRuntime.sel("tickMarkPosition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTickMarkPosition
    }
    fun setTickMarkPosition(value: NSTickMarkPosition) {
        val sel = ObjCRuntime.sel("setTickMarkPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfTickMarks
    fun numberOfTickMarks(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfTickMarks")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfTickMarks(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfTickMarks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfMajorTickMarks
    fun numberOfMajorTickMarks(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfMajorTickMarks")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfMajorTickMarks(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfMajorTickMarks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

