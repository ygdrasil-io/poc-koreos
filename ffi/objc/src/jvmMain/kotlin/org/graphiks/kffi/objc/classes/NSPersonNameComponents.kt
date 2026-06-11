/**
 * Kotlin/JVM wrapper for Objective-C class: NSPersonNameComponents
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSPersonNameComponents(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPersonNameComponents") }
        
    }
    
    // @property namePrefix
    fun namePrefix(): MemorySegment {
        val sel = ObjCRuntime.sel("namePrefix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNamePrefix(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNamePrefix:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun namePrefixAsString(): String = ObjCRuntime.toJavaString(namePrefix())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNamePrefix(value: String) = setNamePrefix(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property givenName
    fun givenName(): MemorySegment {
        val sel = ObjCRuntime.sel("givenName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setGivenName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setGivenName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun givenNameAsString(): String = ObjCRuntime.toJavaString(givenName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setGivenName(value: String) = setGivenName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property middleName
    fun middleName(): MemorySegment {
        val sel = ObjCRuntime.sel("middleName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMiddleName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMiddleName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun middleNameAsString(): String = ObjCRuntime.toJavaString(middleName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setMiddleName(value: String) = setMiddleName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property familyName
    fun familyName(): MemorySegment {
        val sel = ObjCRuntime.sel("familyName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFamilyName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFamilyName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun familyNameAsString(): String = ObjCRuntime.toJavaString(familyName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setFamilyName(value: String) = setFamilyName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property nameSuffix
    fun nameSuffix(): MemorySegment {
        val sel = ObjCRuntime.sel("nameSuffix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNameSuffix(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNameSuffix:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameSuffixAsString(): String = ObjCRuntime.toJavaString(nameSuffix())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNameSuffix(value: String) = setNameSuffix(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property nickname
    fun nickname(): MemorySegment {
        val sel = ObjCRuntime.sel("nickname")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNickname(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNickname:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nicknameAsString(): String = ObjCRuntime.toJavaString(nickname())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNickname(value: String) = setNickname(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property phoneticRepresentation
    fun phoneticRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("phoneticRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPhoneticRepresentation(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPhoneticRepresentation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _private: MemorySegment
}

