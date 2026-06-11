/**
 * Kotlin/JVM interface for Objective-C protocol: NSSoundDelegate
 * Inherits protocols: NSObject
 */
interface NSSoundDelegate : NSObject {
    // @optional
    fun sound_didFinishPlaying(sound: MemorySegment, flag: BOOL): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'sound:didFinishPlaying:' not implemented")
    
}

