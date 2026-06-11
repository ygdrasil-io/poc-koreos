/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextInputContext
 * Superclass: NSObject
 */
open class NSTextInputContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextInputContext") }
        
        fun localizedNameForInputSource(inputSourceIdentifier: NSTextInputSourceIdentifier): MemorySegment {
            val sel = ObjCRuntime.sel("localizedNameForInputSource:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, inputSourceIdentifier) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun localizedNameForInputSourceAsString(inputSourceIdentifier: NSTextInputSourceIdentifier): String = ObjCRuntime.toJavaString(localizedNameForInputSource(inputSourceIdentifier))
        
        fun currentInputContext(): MemorySegment {
            val sel = ObjCRuntime.sel("currentInputContext")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithClient(client: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithClient:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, client) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun activate(): Unit {
        val sel = ObjCRuntime.sel("activate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun deactivate(): Unit {
        val sel = ObjCRuntime.sel("deactivate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun handleEvent(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("handleEvent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun discardMarkedText(): Unit {
        val sel = ObjCRuntime.sel("discardMarkedText")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun invalidateCharacterCoordinates(): Unit {
        val sel = ObjCRuntime.sel("invalidateCharacterCoordinates")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun textInputClientWillStartScrollingOrZooming(): Unit {
        val sel = ObjCRuntime.sel("textInputClientWillStartScrollingOrZooming")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun textInputClientDidEndScrollingOrZooming(): Unit {
        val sel = ObjCRuntime.sel("textInputClientDidEndScrollingOrZooming")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun textInputClientDidUpdateSelection(): Unit {
        val sel = ObjCRuntime.sel("textInputClientDidUpdateSelection")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun textInputClientDidScroll(): Unit {
        val sel = ObjCRuntime.sel("textInputClientDidScroll")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property currentInputContext
    fun currentInputContext(): MemorySegment {
        val sel = ObjCRuntime.sel("currentInputContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property client
    /** @return id<NSTextInputClient> */
    fun client(): MemorySegment {
        val sel = ObjCRuntime.sel("client")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property acceptsGlyphInfo
    fun acceptsGlyphInfo(): BOOL {
        val sel = ObjCRuntime.sel("acceptsGlyphInfo")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAcceptsGlyphInfo(value: BOOL) {
        val sel = ObjCRuntime.sel("setAcceptsGlyphInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedInputSourceLocales
    /** @return NSArray<NSString *> * */
    fun allowedInputSourceLocales(): MemorySegment {
        val sel = ObjCRuntime.sel("allowedInputSourceLocales")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAllowedInputSourceLocales(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAllowedInputSourceLocales:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property keyboardInputSources
    /** @return NSArray<NSTextInputSourceIdentifier> * */
    fun keyboardInputSources(): MemorySegment {
        val sel = ObjCRuntime.sel("keyboardInputSources")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedKeyboardInputSource
    fun selectedKeyboardInputSource(): NSTextInputSourceIdentifier {
        val sel = ObjCRuntime.sel("selectedKeyboardInputSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextInputSourceIdentifier
    }
    fun setSelectedKeyboardInputSource(value: NSTextInputSourceIdentifier) {
        val sel = ObjCRuntime.sel("setSelectedKeyboardInputSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

