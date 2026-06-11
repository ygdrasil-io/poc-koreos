/**
 * Kotlin/JVM interface for Objective-C protocol: NSPathCellDelegate
 * Inherits protocols: NSObject
 */
interface NSPathCellDelegate : NSObject {
    // @optional
    fun pathCell_willDisplayOpenPanel(pathCell: MemorySegment, openPanel: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pathCell:willDisplayOpenPanel:' not implemented")
    
    // @optional
    fun pathCell_willPopUpMenu(pathCell: MemorySegment, menu: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pathCell:willPopUpMenu:' not implemented")
    
}

