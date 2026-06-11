/**
 * Kotlin/JVM interface for Objective-C protocol: NSItemProviderWriting
 * Inherits protocols: NSObject
 */
interface NSItemProviderWriting : NSObject {
    // @optional
    fun itemProviderVisibilityForRepresentationWithTypeIdentifier(typeIdentifier: MemorySegment): NSItemProviderRepresentationVisibility =
        throw UnsupportedOperationException("Optional ObjC method 'itemProviderVisibilityForRepresentationWithTypeIdentifier:' not implemented")
    
    // @optional
    fun itemProviderVisibilityForRepresentationWithTypeIdentifier(typeIdentifier: MemorySegment): NSItemProviderRepresentationVisibility =
        throw UnsupportedOperationException("Optional ObjC method 'itemProviderVisibilityForRepresentationWithTypeIdentifier:' not implemented")
    
    fun loadDataWithTypeIdentifier_forItemProviderCompletionHandler(typeIdentifier: MemorySegment, completionHandler: MemorySegment): MemorySegment
    
    /** @return NSArray<NSString *> * */
    fun writableTypeIdentifiersForItemProvider(): MemorySegment
    
    /** @return NSArray<NSString *> * */
    // @optional
    fun writableTypeIdentifiersForItemProvider(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'writableTypeIdentifiersForItemProvider' not implemented")
    
    // @property writableTypeIdentifiersForItemProvider
    /** @return NSArray<NSString *> * */
    fun writableTypeIdentifiersForItemProvider(): MemorySegment
    
    // @property writableTypeIdentifiersForItemProvider
    /** @return NSArray<NSString *> * */
    fun writableTypeIdentifiersForItemProvider(): MemorySegment
    
}

