/**
 * Kotlin/JVM interface for Objective-C protocol: NSPreviewRepresentableActivityItem
 * Inherits protocols: NSObject
 */
interface NSPreviewRepresentableActivityItem : NSObject {
    fun item(): MemorySegment
    
    // @optional
    fun title(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'title' not implemented")
    
    // @optional
    fun imageProvider(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'imageProvider' not implemented")
    
    // @optional
    fun iconProvider(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'iconProvider' not implemented")
    
    // @property item
    fun item(): MemorySegment
    
    // @property title
    fun title(): MemorySegment
    
    // @property imageProvider
    fun imageProvider(): MemorySegment
    
    // @property iconProvider
    fun iconProvider(): MemorySegment
    
}

