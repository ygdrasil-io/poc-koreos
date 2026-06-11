/**
 * Kotlin/JVM interface for Objective-C protocol: NSColorPickingCustom
 * Inherits protocols: NSColorPickingDefault
 */
interface NSColorPickingCustom : NSColorPickingDefault {
    fun supportsMode(mode: NSColorPanelMode): BOOL
    
    fun currentMode(): NSColorPanelMode
    
    fun provideNewView(initialRequest: BOOL): MemorySegment
    
    fun setColor(newColor: MemorySegment)
    
}

