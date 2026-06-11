/**
 * Kotlin/JVM interface for Objective-C protocol: NSSpeechSynthesizerDelegate
 * Inherits protocols: NSObject
 */
interface NSSpeechSynthesizerDelegate : NSObject {
    // @optional
    fun speechSynthesizer_didFinishSpeaking(sender: MemorySegment, finishedSpeaking: BOOL): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'speechSynthesizer:didFinishSpeaking:' not implemented")
    
    // @optional
    fun speechSynthesizer_willSpeakWord_ofString(sender: MemorySegment, characterRange: NSRange, string: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'speechSynthesizer:willSpeakWord:ofString:' not implemented")
    
    // @optional
    fun speechSynthesizer_willSpeakPhoneme(sender: MemorySegment, phonemeOpcode: Short): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'speechSynthesizer:willSpeakPhoneme:' not implemented")
    
    // @optional
    fun speechSynthesizer_didEncounterErrorAtIndex_ofString_message(sender: MemorySegment, characterIndex: NSUInteger, string: MemorySegment, message: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'speechSynthesizer:didEncounterErrorAtIndex:ofString:message:' not implemented")
    
    // @optional
    fun speechSynthesizer_didEncounterSyncMessage(sender: MemorySegment, message: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'speechSynthesizer:didEncounterSyncMessage:' not implemented")
    
}

