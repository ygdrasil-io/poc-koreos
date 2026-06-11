/**
 * Kotlin/JVM wrapper for Objective-C class: NSDraggingSession
 * Superclass: NSObject
 */
open class NSDraggingSession(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDraggingSession") }
        
    }
    
    fun enumerateDraggingItemsWithOptions_forView_classes_searchOptions_usingBlock(enumOpts: NSDraggingItemEnumerationOptions, view: MemorySegment, classArray: MemorySegment, searchOptions: MemorySegment, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateDraggingItemsWithOptions:forView:classes:searchOptions:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, enumOpts, view, classArray, searchOptions, block)
    }
    
    // @property draggingFormation
    fun draggingFormation(): NSDraggingFormation {
        val sel = ObjCRuntime.sel("draggingFormation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDraggingFormation
    }
    fun setDraggingFormation(value: NSDraggingFormation) {
        val sel = ObjCRuntime.sel("setDraggingFormation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property animatesToStartingPositionsOnCancelOrFail
    fun animatesToStartingPositionsOnCancelOrFail(): BOOL {
        val sel = ObjCRuntime.sel("animatesToStartingPositionsOnCancelOrFail")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAnimatesToStartingPositionsOnCancelOrFail(value: BOOL) {
        val sel = ObjCRuntime.sel("setAnimatesToStartingPositionsOnCancelOrFail:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property draggingLeaderIndex
    fun draggingLeaderIndex(): NSInteger {
        val sel = ObjCRuntime.sel("draggingLeaderIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setDraggingLeaderIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setDraggingLeaderIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property draggingPasteboard
    fun draggingPasteboard(): MemorySegment {
        val sel = ObjCRuntime.sel("draggingPasteboard")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property draggingSequenceNumber
    fun draggingSequenceNumber(): NSInteger {
        val sel = ObjCRuntime.sel("draggingSequenceNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property draggingLocation
    fun draggingLocation(): NSPoint {
        val sel = ObjCRuntime.sel("draggingLocation")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
}

