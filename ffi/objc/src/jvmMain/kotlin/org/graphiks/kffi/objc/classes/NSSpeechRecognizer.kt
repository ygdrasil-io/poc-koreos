/**
 * Kotlin/JVM wrapper for Objective-C class: NSSpeechRecognizer
 * Superclass: NSObject
 */
open class NSSpeechRecognizer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSpeechRecognizer") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun startListening(): Unit {
        val sel = ObjCRuntime.sel("startListening")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun stopListening(): Unit {
        val sel = ObjCRuntime.sel("stopListening")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSSpeechRecognizerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property commands
    /** @return NSArray<NSString *> * */
    fun commands(): MemorySegment {
        val sel = ObjCRuntime.sel("commands")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCommands(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCommands:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property displayedCommandsTitle
    fun displayedCommandsTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("displayedCommandsTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDisplayedCommandsTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDisplayedCommandsTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun displayedCommandsTitleAsString(): String = ObjCRuntime.toJavaString(displayedCommandsTitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setDisplayedCommandsTitle(value: String) = setDisplayedCommandsTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property listensInForegroundOnly
    fun listensInForegroundOnly(): BOOL {
        val sel = ObjCRuntime.sel("listensInForegroundOnly")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setListensInForegroundOnly(value: BOOL) {
        val sel = ObjCRuntime.sel("setListensInForegroundOnly:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property blocksOtherRecognizers
    fun blocksOtherRecognizers(): BOOL {
        val sel = ObjCRuntime.sel("blocksOtherRecognizers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBlocksOtherRecognizers(value: BOOL) {
        val sel = ObjCRuntime.sel("setBlocksOtherRecognizers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

