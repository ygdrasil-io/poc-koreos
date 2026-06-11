/**
 * Kotlin/JVM wrapper for Objective-C class: NSSpellServer
 * Superclass: NSObject
 */
open class NSSpellServer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSpellServer") }
        
    }
    
    fun registerLanguage_byVendor(language: MemorySegment, vendor: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("registerLanguage:byVendor:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, language, vendor) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun registerLanguage_byVendor(language: String, vendor: String): BOOL = registerLanguage_byVendor(ObjCRuntime.newNSString(Arena.global(), language), ObjCRuntime.newNSString(Arena.global(), vendor))
    
    fun isWordInUserDictionaries_caseSensitive(word: MemorySegment, flag: BOOL): BOOL {
        val sel = ObjCRuntime.sel("isWordInUserDictionaries:caseSensitive:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, word, flag) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun isWordInUserDictionaries_caseSensitive(word: String, flag: BOOL): BOOL = isWordInUserDictionaries_caseSensitive(ObjCRuntime.newNSString(Arena.global(), word), flag)
    
    fun run(): Unit {
        val sel = ObjCRuntime.sel("run")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSSpellServerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

