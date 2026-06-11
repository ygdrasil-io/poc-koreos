/**
 * Kotlin/JVM interface for Objective-C protocol: NSFontChanging
 * Inherits protocols: NSObject
 */
interface NSFontChanging : NSObject {
    // @optional
    fun changeFont(sender: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'changeFont:' not implemented")
    
    // @optional
    fun validModesForFontPanel(fontPanel: MemorySegment): NSFontPanelModeMask =
        throw UnsupportedOperationException("Optional ObjC method 'validModesForFontPanel:' not implemented")
    
}

