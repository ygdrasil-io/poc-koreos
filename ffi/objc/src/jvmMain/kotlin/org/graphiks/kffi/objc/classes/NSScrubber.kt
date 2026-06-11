/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubber
 * Superclass: NSView
 */
open class NSScrubber(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubber") }
        
    }
    
    fun initWithFrame(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun reloadData(): Unit {
        val sel = ObjCRuntime.sel("reloadData")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun performSequentialBatchUpdates(updateBlock: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performSequentialBatchUpdates:")
        ObjCRuntime.msgSend(null, ptr, sel, updateBlock)
    }
    
    fun insertItemsAtIndexes(indexes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertItemsAtIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes)
    }
    
    fun removeItemsAtIndexes(indexes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeItemsAtIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes)
    }
    
    fun reloadItemsAtIndexes(indexes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reloadItemsAtIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes)
    }
    
    fun moveItemAtIndex_toIndex(oldIndex: NSInteger, newIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("moveItemAtIndex:toIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, oldIndex, newIndex)
    }
    
    fun scrollItemAtIndex_toAlignment(index: NSInteger, alignment: NSScrubberAlignment): Unit {
        val sel = ObjCRuntime.sel("scrollItemAtIndex:toAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, index, alignment)
    }
    
    fun itemViewForItemAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemViewForItemAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun registerClass_forItemIdentifier(itemViewClass: Class, itemIdentifier: NSUserInterfaceItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("registerClass:forItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, itemViewClass, itemIdentifier)
    }
    
    fun registerNib_forItemIdentifier(nib: MemorySegment, itemIdentifier: NSUserInterfaceItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("registerNib:forItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, nib, itemIdentifier)
    }
    
    fun makeItemWithIdentifier_owner(itemIdentifier: NSUserInterfaceItemIdentifier, owner: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("makeItemWithIdentifier:owner:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemIdentifier, owner) as MemorySegment
    }
    
    // @property dataSource
    /** @return id<NSScrubberDataSource> */
    fun dataSource(): MemorySegment {
        val sel = ObjCRuntime.sel("dataSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDataSource(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDataSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSScrubberDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scrubberLayout
    fun scrubberLayout(): MemorySegment {
        val sel = ObjCRuntime.sel("scrubberLayout")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setScrubberLayout(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setScrubberLayout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfItems
    fun numberOfItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property highlightedIndex
    fun highlightedIndex(): NSInteger {
        val sel = ObjCRuntime.sel("highlightedIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property selectedIndex
    fun selectedIndex(): NSInteger {
        val sel = ObjCRuntime.sel("selectedIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSelectedIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSelectedIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property mode
    fun mode(): NSScrubberMode {
        val sel = ObjCRuntime.sel("mode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrubberMode
    }
    fun setMode(value: NSScrubberMode) {
        val sel = ObjCRuntime.sel("setMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property itemAlignment
    fun itemAlignment(): NSScrubberAlignment {
        val sel = ObjCRuntime.sel("itemAlignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrubberAlignment
    }
    fun setItemAlignment(value: NSScrubberAlignment) {
        val sel = ObjCRuntime.sel("setItemAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property continuous
    fun isContinuous(): BOOL {
        val sel = ObjCRuntime.sel("isContinuous")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setContinuous(value: BOOL) {
        val sel = ObjCRuntime.sel("setContinuous:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property floatsSelectionViews
    fun floatsSelectionViews(): BOOL {
        val sel = ObjCRuntime.sel("floatsSelectionViews")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setFloatsSelectionViews(value: BOOL) {
        val sel = ObjCRuntime.sel("setFloatsSelectionViews:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionBackgroundStyle
    fun selectionBackgroundStyle(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionBackgroundStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectionBackgroundStyle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectionBackgroundStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionOverlayStyle
    fun selectionOverlayStyle(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionOverlayStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectionOverlayStyle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectionOverlayStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsArrowButtons
    fun showsArrowButtons(): BOOL {
        val sel = ObjCRuntime.sel("showsArrowButtons")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsArrowButtons(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsArrowButtons:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsAdditionalContentIndicators
    fun showsAdditionalContentIndicators(): BOOL {
        val sel = ObjCRuntime.sel("showsAdditionalContentIndicators")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsAdditionalContentIndicators(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsAdditionalContentIndicators:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundView
    fun backgroundView(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

