/**
 * Kotlin/JVM wrapper for Objective-C class: NSException
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSException(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSException") }
        
        fun exceptionWithName_reason_userInfo(name: NSExceptionName, reason: MemorySegment, userInfo: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("exceptionWithName:reason:userInfo:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, reason, userInfo) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun exceptionWithName_reason_userInfo(name: NSExceptionName, reason: String, userInfo: MemorySegment): MemorySegment = exceptionWithName_reason_userInfo(name, ObjCRuntime.newNSString(Arena.global(), reason), userInfo)
        
    }
    
    fun initWithName_reason_userInfo(aName: NSExceptionName, aReason: MemorySegment, aUserInfo: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithName:reason:userInfo:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aName, aReason, aUserInfo) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithName_reason_userInfo(aName: NSExceptionName, aReason: String, aUserInfo: MemorySegment): MemorySegment = initWithName_reason_userInfo(aName, ObjCRuntime.newNSString(Arena.global(), aReason), aUserInfo)
    
    fun raise(): Unit {
        val sel = ObjCRuntime.sel("raise")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property name
    fun name(): NSExceptionName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSExceptionName
    }
    
    // @property reason
    fun reason(): MemorySegment {
        val sel = ObjCRuntime.sel("reason")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun reasonAsString(): String = ObjCRuntime.toJavaString(reason())
    
    // @property userInfo
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property callStackReturnAddresses
    /** @return NSArray<NSNumber *> * */
    fun callStackReturnAddresses(): MemorySegment {
        val sel = ObjCRuntime.sel("callStackReturnAddresses")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property callStackSymbols
    /** @return NSArray<NSString *> * */
    fun callStackSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("callStackSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: name: MemorySegment
    // ivar: reason: MemorySegment
    // ivar: userInfo: MemorySegment
    // ivar: reserved: MemorySegment
}

// ── Category: NSExceptionRaisingConveniences on NSException ─────────────────────────────────────────

// Class method: +[NSException raise:format:]
fun NSException_raise_format(name: NSExceptionName, format: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("raise:format:")
    val cls = ObjCRuntime.getClass("NSException")
    ObjCRuntime.msgSend(null, cls, sel, name, format)
}

// Class method: +[NSException raise:format:arguments:]
fun NSException_raise_format_arguments(name: NSExceptionName, format: MemorySegment, argList: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("raise:format:arguments:")
    val cls = ObjCRuntime.getClass("NSException")
    ObjCRuntime.msgSend(null, cls, sel, name, format, argList)
}

