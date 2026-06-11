/**
 * Kotlin/JVM interface for Objective-C protocol: NSColorPickingDefault
 */
interface NSColorPickingDefault {
    fun initWithPickerMask_colorPanel(mask: NSUInteger, owningColorPanel: MemorySegment): MemorySegment
    
    fun provideNewButtonImage(): MemorySegment
    
    fun insertNewButtonImage_in(newButtonImage: MemorySegment, buttonCell: MemorySegment)
    
    fun viewSizeChanged(sender: MemorySegment)
    
    fun alphaControlAddedOrRemoved(sender: MemorySegment)
    
    fun attachColorList(colorList: MemorySegment)
    
    fun detachColorList(colorList: MemorySegment)
    
    fun setMode(mode: NSColorPanelMode)
    
    fun buttonToolTip(): MemorySegment
    
    fun minContentSize(): NSSize
    
}

