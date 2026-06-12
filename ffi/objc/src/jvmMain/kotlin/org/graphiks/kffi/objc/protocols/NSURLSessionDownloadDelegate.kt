package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSURLSessionDownloadDelegate
 * Inherits protocols: NSURLSessionTaskDelegate
 */
interface NSURLSessionDownloadDelegate : NSURLSessionTaskDelegate {
    fun URLSession_downloadTask_didFinishDownloadingToURL(session: MemorySegment, downloadTask: MemorySegment, location: MemorySegment)
    
    // @optional
    fun URLSession_downloadTask_didWriteData_totalBytesWritten_totalBytesExpectedToWrite(session: MemorySegment, downloadTask: MemorySegment, bytesWritten: int64_t, totalBytesWritten: int64_t, totalBytesExpectedToWrite: int64_t): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'URLSession:downloadTask:didWriteData:totalBytesWritten:totalBytesExpectedToWrite:' not implemented")
    
    // @optional
    fun URLSession_downloadTask_didResumeAtOffset_expectedTotalBytes(session: MemorySegment, downloadTask: MemorySegment, fileOffset: int64_t, expectedTotalBytes: int64_t): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'URLSession:downloadTask:didResumeAtOffset:expectedTotalBytes:' not implemented")
    
}

