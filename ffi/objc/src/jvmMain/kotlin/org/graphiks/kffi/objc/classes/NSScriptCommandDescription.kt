/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptCommandDescription
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScriptCommandDescription(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptCommandDescription") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithSuiteName_commandName_dictionary(suiteName: MemorySegment, commandName: MemorySegment, commandDeclaration: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSuiteName:commandName:dictionary:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, suiteName, commandName, commandDeclaration) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithSuiteName_commandName_dictionary(suiteName: String, commandName: String, commandDeclaration: MemorySegment): MemorySegment = initWithSuiteName_commandName_dictionary(ObjCRuntime.newNSString(Arena.global(), suiteName), ObjCRuntime.newNSString(Arena.global(), commandName), commandDeclaration)
    
    fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
    fun typeForArgumentWithName(argumentName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("typeForArgumentWithName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, argumentName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun typeForArgumentWithNameAsString(argumentName: MemorySegment): String = ObjCRuntime.toJavaString(typeForArgumentWithName(argumentName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun typeForArgumentWithName(argumentName: String): MemorySegment = typeForArgumentWithName(ObjCRuntime.newNSString(Arena.global(), argumentName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun typeForArgumentWithNameAsString(argumentName: String): String = ObjCRuntime.toJavaString(typeForArgumentWithName(ObjCRuntime.newNSString(Arena.global(), argumentName)))
    
    fun appleEventCodeForArgumentWithName(argumentName: MemorySegment): FourCharCode {
        val sel = ObjCRuntime.sel("appleEventCodeForArgumentWithName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, argumentName) as FourCharCode
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun appleEventCodeForArgumentWithName(argumentName: String): FourCharCode = appleEventCodeForArgumentWithName(ObjCRuntime.newNSString(Arena.global(), argumentName))
    
    fun isOptionalArgumentWithName(argumentName: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isOptionalArgumentWithName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, argumentName) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun isOptionalArgumentWithName(argumentName: String): BOOL = isOptionalArgumentWithName(ObjCRuntime.newNSString(Arena.global(), argumentName))
    
    fun createCommandInstance(): MemorySegment {
        val sel = ObjCRuntime.sel("createCommandInstance")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun createCommandInstanceWithZone(zone: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("createCommandInstanceWithZone:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, zone) as MemorySegment
    }
    
    // @property suiteName
    fun suiteName(): MemorySegment {
        val sel = ObjCRuntime.sel("suiteName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun suiteNameAsString(): String = ObjCRuntime.toJavaString(suiteName())
    
    // @property commandName
    fun commandName(): MemorySegment {
        val sel = ObjCRuntime.sel("commandName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun commandNameAsString(): String = ObjCRuntime.toJavaString(commandName())
    
    // @property appleEventClassCode
    fun appleEventClassCode(): FourCharCode {
        val sel = ObjCRuntime.sel("appleEventClassCode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as FourCharCode
    }
    
    // @property appleEventCode
    fun appleEventCode(): FourCharCode {
        val sel = ObjCRuntime.sel("appleEventCode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as FourCharCode
    }
    
    // @property commandClassName
    fun commandClassName(): MemorySegment {
        val sel = ObjCRuntime.sel("commandClassName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun commandClassNameAsString(): String = ObjCRuntime.toJavaString(commandClassName())
    
    // @property returnType
    fun returnType(): MemorySegment {
        val sel = ObjCRuntime.sel("returnType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun returnTypeAsString(): String = ObjCRuntime.toJavaString(returnType())
    
    // @property appleEventCodeForReturnType
    fun appleEventCodeForReturnType(): FourCharCode {
        val sel = ObjCRuntime.sel("appleEventCodeForReturnType")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as FourCharCode
    }
    
    // @property argumentNames
    /** @return NSArray<NSString *> * */
    fun argumentNames(): MemorySegment {
        val sel = ObjCRuntime.sel("argumentNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _suiteName: MemorySegment
    // ivar: _plistCommandName: MemorySegment
    // ivar: _classAppleEventCode: FourCharCode
    // ivar: _idAppleEventCode: FourCharCode
    // ivar: _objcClassName: MemorySegment
    // ivar: _resultTypeNameOrDescription: MemorySegment
    // ivar: _plistResultTypeAppleEventCode: FourCharCode
    // ivar: _moreVars: MemorySegment
}

