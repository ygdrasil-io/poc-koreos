/**
 * Kotlin/JVM wrapper for Objective-C class: NSMorphologyCustomPronoun
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSMorphologyCustomPronoun(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMorphologyCustomPronoun") }
        
        fun isSupportedForLanguage(language: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("isSupportedForLanguage:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, language) as BOOL
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun isSupportedForLanguage(language: String): BOOL = isSupportedForLanguage(ObjCRuntime.newNSString(Arena.global(), language))
        
        /** @return NSArray<NSString *> * */
        fun requiredKeysForLanguage(language: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("requiredKeysForLanguage:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, language) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun requiredKeysForLanguage(language: String): MemorySegment = requiredKeysForLanguage(ObjCRuntime.newNSString(Arena.global(), language))
        
    }
    
    // @property subjectForm
    fun subjectForm(): MemorySegment {
        val sel = ObjCRuntime.sel("subjectForm")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubjectForm(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubjectForm:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun subjectFormAsString(): String = ObjCRuntime.toJavaString(subjectForm())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSubjectForm(value: String) = setSubjectForm(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property objectForm
    fun objectForm(): MemorySegment {
        val sel = ObjCRuntime.sel("objectForm")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setObjectForm(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setObjectForm:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun objectFormAsString(): String = ObjCRuntime.toJavaString(objectForm())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setObjectForm(value: String) = setObjectForm(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property possessiveForm
    fun possessiveForm(): MemorySegment {
        val sel = ObjCRuntime.sel("possessiveForm")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPossessiveForm(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPossessiveForm:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun possessiveFormAsString(): String = ObjCRuntime.toJavaString(possessiveForm())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPossessiveForm(value: String) = setPossessiveForm(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property possessiveAdjectiveForm
    fun possessiveAdjectiveForm(): MemorySegment {
        val sel = ObjCRuntime.sel("possessiveAdjectiveForm")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPossessiveAdjectiveForm(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPossessiveAdjectiveForm:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun possessiveAdjectiveFormAsString(): String = ObjCRuntime.toJavaString(possessiveAdjectiveForm())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPossessiveAdjectiveForm(value: String) = setPossessiveAdjectiveForm(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property reflexiveForm
    fun reflexiveForm(): MemorySegment {
        val sel = ObjCRuntime.sel("reflexiveForm")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setReflexiveForm(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setReflexiveForm:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun reflexiveFormAsString(): String = ObjCRuntime.toJavaString(reflexiveForm())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setReflexiveForm(value: String) = setReflexiveForm(ObjCRuntime.newNSString(Arena.global(), value))
    
}

