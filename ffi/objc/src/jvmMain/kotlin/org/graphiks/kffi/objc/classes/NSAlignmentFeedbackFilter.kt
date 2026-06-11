/**
 * Kotlin/JVM wrapper for Objective-C class: NSAlignmentFeedbackFilter
 * Superclass: NSObject
 */
open class NSAlignmentFeedbackFilter(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAlignmentFeedbackFilter") }
        
        fun inputEventMask(): NSEventMask {
            val sel = ObjCRuntime.sel("inputEventMask")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSEventMask
        }
        
    }
    
    fun updateWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("updateWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun updateWithPanRecognizer(panRecognizer: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("updateWithPanRecognizer:")
        ObjCRuntime.msgSend(null, ptr, sel, panRecognizer)
    }
    
    /** @return id<NSAlignmentFeedbackToken> */
    fun alignmentFeedbackTokenForMovementInView_previousPoint_alignedPoint_defaultPoint(view: MemorySegment, previousPoint: NSPoint, alignedPoint: NSPoint, defaultPoint: NSPoint): MemorySegment {
        val sel = ObjCRuntime.sel("alignmentFeedbackTokenForMovementInView:previousPoint:alignedPoint:defaultPoint:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view, ObjCRuntime.ObjCStructArg(previousPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(alignedPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(defaultPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    /** @return id<NSAlignmentFeedbackToken> */
    fun alignmentFeedbackTokenForHorizontalMovementInView_previousX_alignedX_defaultX(view: MemorySegment, previousX: CGFloat, alignedX: CGFloat, defaultX: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("alignmentFeedbackTokenForHorizontalMovementInView:previousX:alignedX:defaultX:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view, previousX, alignedX, defaultX) as MemorySegment
    }
    
    /** @return id<NSAlignmentFeedbackToken> */
    fun alignmentFeedbackTokenForVerticalMovementInView_previousY_alignedY_defaultY(view: MemorySegment, previousY: CGFloat, alignedY: CGFloat, defaultY: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("alignmentFeedbackTokenForVerticalMovementInView:previousY:alignedY:defaultY:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view, previousY, alignedY, defaultY) as MemorySegment
    }
    
    fun performFeedback_performanceTime(alignmentFeedbackTokens: MemorySegment, performanceTime: NSHapticFeedbackPerformanceTime): Unit {
        val sel = ObjCRuntime.sel("performFeedback:performanceTime:")
        ObjCRuntime.msgSend(null, ptr, sel, alignmentFeedbackTokens, performanceTime)
    }
    
    // @property inputEventMask
    fun inputEventMask(): NSEventMask {
        val sel = ObjCRuntime.sel("inputEventMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventMask
    }
    
}

