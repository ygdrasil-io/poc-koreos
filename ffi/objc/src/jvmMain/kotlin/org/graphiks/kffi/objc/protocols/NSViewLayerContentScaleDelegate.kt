/**
 * Kotlin/JVM interface for Objective-C protocol: NSViewLayerContentScaleDelegate
 * Inherits protocols: NSObject
 */
interface NSViewLayerContentScaleDelegate : NSObject {
    // @optional
    fun layer_shouldInheritContentsScale_fromWindow(layer: MemorySegment, newScale: CGFloat, window: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'layer:shouldInheritContentsScale:fromWindow:' not implemented")
    
}

