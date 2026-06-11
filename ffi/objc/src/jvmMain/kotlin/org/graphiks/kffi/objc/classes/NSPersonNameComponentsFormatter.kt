/**
 * Kotlin/JVM wrapper for Objective-C class: NSPersonNameComponentsFormatter
 * Superclass: NSFormatter
 */
open class NSPersonNameComponentsFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPersonNameComponentsFormatter") }
        
        fun localizedStringFromPersonNameComponents_style_options(components: MemorySegment, nameFormatStyle: NSPersonNameComponentsFormatterStyle, nameOptions: NSPersonNameComponentsFormatterOptions): MemorySegment {
            val sel = ObjCRuntime.sel("localizedStringFromPersonNameComponents:style:options:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, components, nameFormatStyle, nameOptions) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun localizedStringFromPersonNameComponents_style_optionsAsString(components: MemorySegment, nameFormatStyle: NSPersonNameComponentsFormatterStyle, nameOptions: NSPersonNameComponentsFormatterOptions): String = ObjCRuntime.toJavaString(localizedStringFromPersonNameComponents_style_options(components, nameFormatStyle, nameOptions))
        
    }
    
    fun stringFromPersonNameComponents(components: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromPersonNameComponents:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, components) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromPersonNameComponentsAsString(components: MemorySegment): String = ObjCRuntime.toJavaString(stringFromPersonNameComponents(components))
    
    fun annotatedStringFromPersonNameComponents(components: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("annotatedStringFromPersonNameComponents:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, components) as MemorySegment
    }
    
    fun personNameComponentsFromString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("personNameComponentsFromString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun personNameComponentsFromString(string: String): MemorySegment = personNameComponentsFromString(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun getObjectValue_forString_errorDescription(obj: MemorySegment, string: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getObjectValue:forString:errorDescription:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, obj, string, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun getObjectValue_forString_errorDescription(obj: MemorySegment, string: String, error: String): BOOL = getObjectValue_forString_errorDescription(obj, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), error))
    
    // @property style
    fun style(): NSPersonNameComponentsFormatterStyle {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPersonNameComponentsFormatterStyle
    }
    fun setStyle(value: NSPersonNameComponentsFormatterStyle) {
        val sel = ObjCRuntime.sel("setStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property phonetic
    fun isPhonetic(): BOOL {
        val sel = ObjCRuntime.sel("isPhonetic")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPhonetic(value: BOOL) {
        val sel = ObjCRuntime.sel("setPhonetic:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property locale
    fun locale(): MemorySegment {
        val sel = ObjCRuntime.sel("locale")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLocale(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _private: MemorySegment
}

