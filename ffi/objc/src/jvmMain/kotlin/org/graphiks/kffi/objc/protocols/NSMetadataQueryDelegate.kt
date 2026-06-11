/**
 * Kotlin/JVM interface for Objective-C protocol: NSMetadataQueryDelegate
 * Inherits protocols: NSObject
 */
interface NSMetadataQueryDelegate : NSObject {
    // @optional
    fun metadataQuery_replacementObjectForResultObject(query: MemorySegment, result: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'metadataQuery:replacementObjectForResultObject:' not implemented")
    
    // @optional
    fun metadataQuery_replacementValueForAttribute_value(query: MemorySegment, attrName: MemorySegment, attrValue: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'metadataQuery:replacementValueForAttribute:value:' not implemented")
    
}

