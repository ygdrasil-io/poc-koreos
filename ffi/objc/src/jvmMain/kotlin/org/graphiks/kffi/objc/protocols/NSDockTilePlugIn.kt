/**
 * Kotlin/JVM interface for Objective-C protocol: NSDockTilePlugIn
 * Inherits protocols: NSObject
 */
interface NSDockTilePlugIn : NSObject {
    fun setDockTile(dockTile: MemorySegment)
    
    // @optional
    fun dockMenu(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'dockMenu' not implemented")
    
}

