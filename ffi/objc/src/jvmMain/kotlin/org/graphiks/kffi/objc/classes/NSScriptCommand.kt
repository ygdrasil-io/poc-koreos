/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptCommand
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScriptCommand(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptCommand") }
        
        fun currentCommand(): MemorySegment {
            val sel = ObjCRuntime.sel("currentCommand")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithCommandDescription(commandDef: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCommandDescription:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, commandDef) as MemorySegment
    }
    
    fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
    fun performDefaultImplementation(): MemorySegment {
        val sel = ObjCRuntime.sel("performDefaultImplementation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun executeCommand(): MemorySegment {
        val sel = ObjCRuntime.sel("executeCommand")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun suspendExecution(): Unit {
        val sel = ObjCRuntime.sel("suspendExecution")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resumeExecutionWithResult(result: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("resumeExecutionWithResult:")
        ObjCRuntime.msgSend(null, ptr, sel, result)
    }
    
    // @property commandDescription
    fun commandDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("commandDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property directParameter
    fun directParameter(): MemorySegment {
        val sel = ObjCRuntime.sel("directParameter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDirectParameter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDirectParameter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property receiversSpecifier
    fun receiversSpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("receiversSpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setReceiversSpecifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setReceiversSpecifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property evaluatedReceivers
    fun evaluatedReceivers(): MemorySegment {
        val sel = ObjCRuntime.sel("evaluatedReceivers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property arguments
    /** @return NSDictionary<NSString *,id> * */
    fun arguments(): MemorySegment {
        val sel = ObjCRuntime.sel("arguments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setArguments(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setArguments:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property evaluatedArguments
    /** @return NSDictionary<NSString *,id> * */
    fun evaluatedArguments(): MemorySegment {
        val sel = ObjCRuntime.sel("evaluatedArguments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property wellFormed
    fun isWellFormed(): BOOL {
        val sel = ObjCRuntime.sel("isWellFormed")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property scriptErrorNumber
    fun scriptErrorNumber(): NSInteger {
        val sel = ObjCRuntime.sel("scriptErrorNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setScriptErrorNumber(value: NSInteger) {
        val sel = ObjCRuntime.sel("setScriptErrorNumber:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scriptErrorOffendingObjectDescriptor
    fun scriptErrorOffendingObjectDescriptor(): MemorySegment {
        val sel = ObjCRuntime.sel("scriptErrorOffendingObjectDescriptor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setScriptErrorOffendingObjectDescriptor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setScriptErrorOffendingObjectDescriptor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scriptErrorExpectedTypeDescriptor
    fun scriptErrorExpectedTypeDescriptor(): MemorySegment {
        val sel = ObjCRuntime.sel("scriptErrorExpectedTypeDescriptor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setScriptErrorExpectedTypeDescriptor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setScriptErrorExpectedTypeDescriptor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scriptErrorString
    fun scriptErrorString(): MemorySegment {
        val sel = ObjCRuntime.sel("scriptErrorString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setScriptErrorString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setScriptErrorString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun scriptErrorStringAsString(): String = ObjCRuntime.toJavaString(scriptErrorString())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setScriptErrorString(value: String) = setScriptErrorString(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property appleEvent
    fun appleEvent(): MemorySegment {
        val sel = ObjCRuntime.sel("appleEvent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _commandDescription: MemorySegment
    // ivar: _directParameter: MemorySegment
    // ivar: _receiversSpecifier: MemorySegment
    // ivar: _evaluatedReceivers: MemorySegment
    // ivar: _arguments: MemorySegment
    // ivar: _evaluatedArguments: MemorySegment
    // ivar: _flags: MemorySegment
    // ivar: _moreVars: MemorySegment
    // ivar: _reserved: MemorySegment
}

