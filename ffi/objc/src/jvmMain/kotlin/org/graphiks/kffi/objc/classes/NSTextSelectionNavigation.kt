/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextSelectionNavigation
 * Superclass: NSObject
 */
open class NSTextSelectionNavigation(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextSelectionNavigation") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithDataSource(dataSource: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDataSource:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dataSource) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun flushLayoutCache(): Unit {
        val sel = ObjCRuntime.sel("flushLayoutCache")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun destinationSelectionForTextSelection_direction_destination_extending_confined(textSelection: MemorySegment, direction: NSTextSelectionNavigationDirection, destination: NSTextSelectionNavigationDestination, extending: BOOL, confined: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("destinationSelectionForTextSelection:direction:destination:extending:confined:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textSelection, direction, destination, extending, confined) as MemorySegment
    }
    
    /** @return NSArray<NSTextSelection *> * */
    fun textSelectionsInteractingAtPoint_inContainerAtLocation_anchors_modifiers_selecting_bounds(point: CGPoint, containerLocation: MemorySegment, anchors: MemorySegment, modifiers: NSTextSelectionNavigationModifier, selecting: BOOL, bounds: CGRect): MemorySegment {
        val sel = ObjCRuntime.sel("textSelectionsInteractingAtPoint:inContainerAtLocation:anchors:modifiers:selecting:bounds:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), containerLocation, anchors, modifiers, selecting, ObjCRuntime.ObjCStructArg(bounds, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun textSelectionForSelectionGranularity_enclosingTextSelection(selectionGranularity: NSTextSelectionGranularity, textSelection: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textSelectionForSelectionGranularity:enclosingTextSelection:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, selectionGranularity, textSelection) as MemorySegment
    }
    
    fun textSelectionForSelectionGranularity_enclosingPoint_inContainerAtLocation(selectionGranularity: NSTextSelectionGranularity, point: CGPoint, location: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textSelectionForSelectionGranularity:enclosingPoint:inContainerAtLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, selectionGranularity, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), location) as MemorySegment
    }
    
    /** @return id<NSTextLocation> */
    fun resolvedInsertionLocationForTextSelection_writingDirection(textSelection: MemorySegment, writingDirection: NSTextSelectionNavigationWritingDirection): MemorySegment {
        val sel = ObjCRuntime.sel("resolvedInsertionLocationForTextSelection:writingDirection:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textSelection, writingDirection) as MemorySegment
    }
    
    /** @return NSArray<NSTextRange *> * */
    fun deletionRangesForTextSelection_direction_destination_allowsDecomposition(textSelection: MemorySegment, direction: NSTextSelectionNavigationDirection, destination: NSTextSelectionNavigationDestination, allowsDecomposition: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("deletionRangesForTextSelection:direction:destination:allowsDecomposition:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textSelection, direction, destination, allowsDecomposition) as MemorySegment
    }
    
    // @property textSelectionDataSource
    /** @return id<NSTextSelectionDataSource> */
    fun textSelectionDataSource(): MemorySegment {
        val sel = ObjCRuntime.sel("textSelectionDataSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property allowsNonContiguousRanges
    fun allowsNonContiguousRanges(): BOOL {
        val sel = ObjCRuntime.sel("allowsNonContiguousRanges")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsNonContiguousRanges(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsNonContiguousRanges:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rotatesCoordinateSystemForLayoutOrientation
    fun rotatesCoordinateSystemForLayoutOrientation(): BOOL {
        val sel = ObjCRuntime.sel("rotatesCoordinateSystemForLayoutOrientation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRotatesCoordinateSystemForLayoutOrientation(value: BOOL) {
        val sel = ObjCRuntime.sel("setRotatesCoordinateSystemForLayoutOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

