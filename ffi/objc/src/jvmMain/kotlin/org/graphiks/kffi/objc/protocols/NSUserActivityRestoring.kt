/**
 * Kotlin/JVM interface for Objective-C protocol: NSUserActivityRestoring
 * Inherits protocols: NSObject
 */
interface NSUserActivityRestoring : NSObject {
    fun restoreUserActivityState(userActivity: MemorySegment)
    
}

