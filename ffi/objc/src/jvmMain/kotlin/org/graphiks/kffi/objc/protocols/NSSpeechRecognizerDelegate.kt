/**
 * Kotlin/JVM interface for Objective-C protocol: NSSpeechRecognizerDelegate
 * Inherits protocols: NSObject
 */
interface NSSpeechRecognizerDelegate : NSObject {
    // @optional
    fun speechRecognizer_didRecognizeCommand(sender: MemorySegment, command: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'speechRecognizer:didRecognizeCommand:' not implemented")
    
}

