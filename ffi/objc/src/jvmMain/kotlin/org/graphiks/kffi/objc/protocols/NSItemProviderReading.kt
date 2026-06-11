/**
 * Kotlin/JVM interface for Objective-C protocol: NSItemProviderReading
 * Inherits protocols: NSObject
 */
interface NSItemProviderReading : NSObject {
    fun objectWithItemProviderData_typeIdentifier_error(`data`: MemorySegment, typeIdentifier: MemorySegment, outError: MemorySegment): MemorySegment
    
    /** @return NSArray<NSString *> * */
    fun readableTypeIdentifiersForItemProvider(): MemorySegment
    
    // @property readableTypeIdentifiersForItemProvider
    /** @return NSArray<NSString *> * */
    fun readableTypeIdentifiersForItemProvider(): MemorySegment
    
}

