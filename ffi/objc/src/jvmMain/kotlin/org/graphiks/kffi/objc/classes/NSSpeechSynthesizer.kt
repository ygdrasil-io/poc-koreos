/**
 * Kotlin/JVM wrapper for Objective-C class: NSSpeechSynthesizer
 * Superclass: NSObject
 */
open class NSSpeechSynthesizer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSpeechSynthesizer") }
        
        /** @return NSDictionary<NSVoiceAttributeKey,id> * */
        fun attributesForVoice(voice: NSSpeechSynthesizerVoiceName): MemorySegment {
            val sel = ObjCRuntime.sel("attributesForVoice:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, voice) as MemorySegment
        }
        
        fun isAnyApplicationSpeaking(): BOOL {
            val sel = ObjCRuntime.sel("isAnyApplicationSpeaking")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun defaultVoice(): NSSpeechSynthesizerVoiceName {
            val sel = ObjCRuntime.sel("defaultVoice")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSSpeechSynthesizerVoiceName
        }
        
        /** @return NSArray<NSSpeechSynthesizerVoiceName> * */
        fun availableVoices(): MemorySegment {
            val sel = ObjCRuntime.sel("availableVoices")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithVoice(voice: NSSpeechSynthesizerVoiceName): MemorySegment {
        val sel = ObjCRuntime.sel("initWithVoice:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, voice) as MemorySegment
    }
    
    fun startSpeakingString(string: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("startSpeakingString:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun startSpeakingString(string: String): BOOL = startSpeakingString(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun startSpeakingString_toURL(string: MemorySegment, url: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("startSpeakingString:toURL:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string, url) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun startSpeakingString_toURL(string: String, url: MemorySegment): BOOL = startSpeakingString_toURL(ObjCRuntime.newNSString(Arena.global(), string), url)
    
    fun stopSpeaking(): Unit {
        val sel = ObjCRuntime.sel("stopSpeaking")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun stopSpeakingAtBoundary(boundary: NSSpeechBoundary): Unit {
        val sel = ObjCRuntime.sel("stopSpeakingAtBoundary:")
        ObjCRuntime.msgSend(null, ptr, sel, boundary)
    }
    
    fun pauseSpeakingAtBoundary(boundary: NSSpeechBoundary): Unit {
        val sel = ObjCRuntime.sel("pauseSpeakingAtBoundary:")
        ObjCRuntime.msgSend(null, ptr, sel, boundary)
    }
    
    fun continueSpeaking(): Unit {
        val sel = ObjCRuntime.sel("continueSpeaking")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun voice(): NSSpeechSynthesizerVoiceName {
        val sel = ObjCRuntime.sel("voice")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSpeechSynthesizerVoiceName
    }
    
    fun setVoice(voice: NSSpeechSynthesizerVoiceName): BOOL {
        val sel = ObjCRuntime.sel("setVoice:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, voice) as BOOL
    }
    
    fun addSpeechDictionary(speechDictionary: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSpeechDictionary:")
        ObjCRuntime.msgSend(null, ptr, sel, speechDictionary)
    }
    
    fun phonemesFromText(text: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("phonemesFromText:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, text) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun phonemesFromTextAsString(text: MemorySegment): String = ObjCRuntime.toJavaString(phonemesFromText(text))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun phonemesFromText(text: String): MemorySegment = phonemesFromText(ObjCRuntime.newNSString(Arena.global(), text))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun phonemesFromTextAsString(text: String): String = ObjCRuntime.toJavaString(phonemesFromText(ObjCRuntime.newNSString(Arena.global(), text)))
    
    fun objectForProperty_error(property: NSSpeechPropertyKey, outError: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectForProperty:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, property, outError) as MemorySegment
    }
    
    fun setObject_forProperty_error(`object`: MemorySegment, property: NSSpeechPropertyKey, outError: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setObject:forProperty:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`, property, outError) as BOOL
    }
    
    // @property speaking
    fun isSpeaking(): BOOL {
        val sel = ObjCRuntime.sel("isSpeaking")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property delegate
    /** @return id<NSSpeechSynthesizerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rate
    fun rate(): Float {
        val sel = ObjCRuntime.sel("rate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    fun setRate(value: Float) {
        val sel = ObjCRuntime.sel("setRate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property volume
    fun volume(): Float {
        val sel = ObjCRuntime.sel("volume")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    fun setVolume(value: Float) {
        val sel = ObjCRuntime.sel("setVolume:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesFeedbackWindow
    fun usesFeedbackWindow(): BOOL {
        val sel = ObjCRuntime.sel("usesFeedbackWindow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesFeedbackWindow(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesFeedbackWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property anyApplicationSpeaking
    fun isAnyApplicationSpeaking(): BOOL {
        val sel = ObjCRuntime.sel("isAnyApplicationSpeaking")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property defaultVoice
    fun defaultVoice(): NSSpeechSynthesizerVoiceName {
        val sel = ObjCRuntime.sel("defaultVoice")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSpeechSynthesizerVoiceName
    }
    
    // @property availableVoices
    /** @return NSArray<NSSpeechSynthesizerVoiceName> * */
    fun availableVoices(): MemorySegment {
        val sel = ObjCRuntime.sel("availableVoices")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

