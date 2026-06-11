/**
 * Kotlin/JVM wrapper for Objective-C class: NSTouch
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSTouch(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTouch") }
        
    }
    
    // @property identity
    /** @return id<NSObject,NSCopying> */
    fun identity(): MemorySegment {
        val sel = ObjCRuntime.sel("identity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property phase
    fun phase(): NSTouchPhase {
        val sel = ObjCRuntime.sel("phase")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchPhase
    }
    
    // @property normalizedPosition
    fun normalizedPosition(): NSPoint {
        val sel = ObjCRuntime.sel("normalizedPosition")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
    // @property resting
    fun isResting(): BOOL {
        val sel = ObjCRuntime.sel("isResting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property device
    fun device(): MemorySegment {
        val sel = ObjCRuntime.sel("device")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deviceSize
    fun deviceSize(): NSSize {
        val sel = ObjCRuntime.sel("deviceSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
}

// ── Category: NSTouchBar on NSTouch ─────────────────────────────────────────

fun NSTouch.locationInView(view: MemorySegment): NSPoint {
    val sel = ObjCRuntime.sel("locationInView:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, view) as NSPoint
}

fun NSTouch.previousLocationInView(view: MemorySegment): NSPoint {
    val sel = ObjCRuntime.sel("previousLocationInView:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, view) as NSPoint
}

fun NSTouch.type(): NSTouchType {
    val sel = ObjCRuntime.sel("type")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchType
}

// @property type
fun NSTouch.type(): NSTouchType {
    val sel = ObjCRuntime.sel("type")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchType
}

