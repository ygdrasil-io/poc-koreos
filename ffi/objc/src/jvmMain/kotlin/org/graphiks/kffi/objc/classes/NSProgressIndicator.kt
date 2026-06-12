package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSProgressIndicator
 * Superclass: NSView
 * Protocols: NSAccessibilityProgressIndicator
 */
open class NSProgressIndicator(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSProgressIndicator") }
        
    }
    
    fun incrementBy(delta: Double): Unit {
        val sel = ObjCRuntime.sel("incrementBy:")
        ObjCRuntime.msgSend(null, ptr, sel, delta)
    }
    
    fun startAnimation(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("startAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun stopAnimation(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("stopAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun sizeToFit(): Unit {
        val sel = ObjCRuntime.sel("sizeToFit")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property indeterminate
    fun isIndeterminate(): BOOL {
        val sel = ObjCRuntime.sel("isIndeterminate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIndeterminate(value: BOOL) {
        val sel = ObjCRuntime.sel("setIndeterminate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property controlSize
    fun controlSize(): NSControlSize {
        val sel = ObjCRuntime.sel("controlSize")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlSize
    }
    fun setControlSize(value: NSControlSize) {
        val sel = ObjCRuntime.sel("setControlSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doubleValue
    fun doubleValue(): Double {
        val sel = ObjCRuntime.sel("doubleValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setDoubleValue(value: Double) {
        val sel = ObjCRuntime.sel("setDoubleValue:")
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
    
    // @property observedProgress
    fun observedProgress(): MemorySegment {
        val sel = ObjCRuntime.sel("observedProgress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setObservedProgress(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setObservedProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesThreadedAnimation
    fun usesThreadedAnimation(): BOOL {
        val sel = ObjCRuntime.sel("usesThreadedAnimation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesThreadedAnimation(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesThreadedAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property style
    fun style(): NSProgressIndicatorStyle {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSProgressIndicatorStyle
    }
    fun setStyle(value: NSProgressIndicatorStyle) {
        val sel = ObjCRuntime.sel("setStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property displayedWhenStopped
    fun isDisplayedWhenStopped(): BOOL {
        val sel = ObjCRuntime.sel("isDisplayedWhenStopped")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDisplayedWhenStopped(value: BOOL) {
        val sel = ObjCRuntime.sel("setDisplayedWhenStopped:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSProgressIndicatorDeprecated on NSProgressIndicator ─────────────────────────────────────────

fun NSProgressIndicator.animationDelay(): NSTimeInterval {
    val sel = ObjCRuntime.sel("animationDelay")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
}

fun NSProgressIndicator.setAnimationDelay(delay: NSTimeInterval): Unit {
    val sel = ObjCRuntime.sel("setAnimationDelay:")
    ObjCRuntime.msgSend(null, ptr, sel, delay)
}

fun NSProgressIndicator.animate(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("animate:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSProgressIndicator.isBezeled(): BOOL {
    val sel = ObjCRuntime.sel("isBezeled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSProgressIndicator.setBezeled(bezeled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setBezeled:")
    ObjCRuntime.msgSend(null, ptr, sel, bezeled)
}

fun NSProgressIndicator.controlTint(): NSControlTint {
    val sel = ObjCRuntime.sel("controlTint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
}

fun NSProgressIndicator.setControlTint(controlTint: NSControlTint): Unit {
    val sel = ObjCRuntime.sel("setControlTint:")
    ObjCRuntime.msgSend(null, ptr, sel, controlTint)
}

// @property bezeled
    val sel = ObjCRuntime.sel("isBezeled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setBezeled:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property controlTint
    val sel = ObjCRuntime.sel("controlTint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
}
    val sel = ObjCRuntime.sel("setControlTint:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

