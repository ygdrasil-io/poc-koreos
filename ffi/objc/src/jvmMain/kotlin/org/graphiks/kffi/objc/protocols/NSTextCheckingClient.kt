/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextCheckingClient
 * Inherits protocols: NSTextInputClient, NSTextInputTraits
 */
interface NSTextCheckingClient : NSTextInputClient, NSTextInputTraits {
    fun annotatedSubstringForProposedRange_actualRange(range: NSRange, actualRange: MemorySegment): MemorySegment
    
    fun setAnnotations_range(annotations: MemorySegment, range: NSRange)
    
    fun addAnnotations_range(annotations: MemorySegment, range: NSRange)
    
    fun removeAnnotation_range(annotationName: NSAttributedStringKey, range: NSRange)
    
    fun replaceCharactersInRange_withAnnotatedString(range: NSRange, annotatedString: MemorySegment)
    
    fun selectAndShowRange(range: NSRange)
    
    fun viewForRange_firstRect_actualRange(range: NSRange, firstRect: MemorySegment, actualRange: MemorySegment): MemorySegment
    
    fun candidateListTouchBarItem(): MemorySegment
    
}

