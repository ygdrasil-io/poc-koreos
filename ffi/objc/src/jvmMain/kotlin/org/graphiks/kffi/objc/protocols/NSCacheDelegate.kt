/**
 * Kotlin/JVM interface for Objective-C protocol: NSCacheDelegate
 * Inherits protocols: NSObject
 */
interface NSCacheDelegate : NSObject {
    // @optional
    fun cache_willEvictObject(cache: MemorySegment, obj: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'cache:willEvictObject:' not implemented")
    
}

