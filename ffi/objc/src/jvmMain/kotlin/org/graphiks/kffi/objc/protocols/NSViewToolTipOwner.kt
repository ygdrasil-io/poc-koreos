/**
 * Kotlin/JVM interface for Objective-C protocol: NSViewToolTipOwner
 * Inherits protocols: NSObject
 */
interface NSViewToolTipOwner : NSObject {
    fun view_stringForToolTip_point_userData(view: MemorySegment, tag: NSToolTipTag, point: NSPoint, `data`: MemorySegment): MemorySegment
    
}

