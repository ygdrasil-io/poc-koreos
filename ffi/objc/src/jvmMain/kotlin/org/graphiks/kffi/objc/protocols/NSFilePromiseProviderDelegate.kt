/**
 * Kotlin/JVM interface for Objective-C protocol: NSFilePromiseProviderDelegate
 * Inherits protocols: NSObject
 */
interface NSFilePromiseProviderDelegate : NSObject {
    fun filePromiseProvider_fileNameForType(filePromiseProvider: MemorySegment, fileType: MemorySegment): MemorySegment
    
    fun filePromiseProvider_writePromiseToURL_completionHandler(filePromiseProvider: MemorySegment, url: MemorySegment, completionHandler: MemorySegment)
    
    // @optional
    fun operationQueueForFilePromiseProvider(filePromiseProvider: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'operationQueueForFilePromiseProvider:' not implemented")
    
}

