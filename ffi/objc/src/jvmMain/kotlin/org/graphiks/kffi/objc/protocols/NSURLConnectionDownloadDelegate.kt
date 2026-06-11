/**
 * Kotlin/JVM interface for Objective-C protocol: NSURLConnectionDownloadDelegate
 * Inherits protocols: NSURLConnectionDelegate
 */
interface NSURLConnectionDownloadDelegate : NSURLConnectionDelegate {
    // @optional
    fun connection_didWriteData_totalBytesWritten_expectedTotalBytes(connection: MemorySegment, bytesWritten: Long, totalBytesWritten: Long, expectedTotalBytes: Long): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'connection:didWriteData:totalBytesWritten:expectedTotalBytes:' not implemented")
    
    // @optional
    fun connectionDidResumeDownloading_totalBytesWritten_expectedTotalBytes(connection: MemorySegment, totalBytesWritten: Long, expectedTotalBytes: Long): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'connectionDidResumeDownloading:totalBytesWritten:expectedTotalBytes:' not implemented")
    
    fun connectionDidFinishDownloading_destinationURL(connection: MemorySegment, destinationURL: MemorySegment)
    
}

