/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextAlternatives
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSTextAlternatives(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextAlternatives") }
        
    }
    
    fun initWithPrimaryString_alternativeStrings(primaryString: MemorySegment, alternativeStrings: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPrimaryString:alternativeStrings:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, primaryString, alternativeStrings) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithPrimaryString_alternativeStrings(primaryString: String, alternativeStrings: MemorySegment): MemorySegment = initWithPrimaryString_alternativeStrings(ObjCRuntime.newNSString(Arena.global(), primaryString), alternativeStrings)
    
    fun noteSelectedAlternativeString(alternativeString: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("noteSelectedAlternativeString:")
        ObjCRuntime.msgSend(null, ptr, sel, alternativeString)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun noteSelectedAlternativeString(alternativeString: String): Unit = noteSelectedAlternativeString(ObjCRuntime.newNSString(Arena.global(), alternativeString))
    
    // @property primaryString
    fun primaryString(): MemorySegment {
        val sel = ObjCRuntime.sel("primaryString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun primaryStringAsString(): String = ObjCRuntime.toJavaString(primaryString())
    
    // @property alternativeStrings
    /** @return NSArray<NSString *> * */
    fun alternativeStrings(): MemorySegment {
        val sel = ObjCRuntime.sel("alternativeStrings")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

