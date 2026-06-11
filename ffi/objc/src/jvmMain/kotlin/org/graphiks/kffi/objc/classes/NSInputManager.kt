/**
 * Kotlin/JVM wrapper for Objective-C class: NSInputManager
 * Superclass: NSObject
 * Protocols: NSTextInput
 */
open class NSInputManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSInputManager") }
        
        fun currentInputManager(): MemorySegment {
            val sel = ObjCRuntime.sel("currentInputManager")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cycleToNextInputLanguage(sender: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("cycleToNextInputLanguage:")
            ObjCRuntime.msgSend(null, _class, sel, sender)
        }
        
        fun cycleToNextInputServerInLanguage(sender: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("cycleToNextInputServerInLanguage:")
            ObjCRuntime.msgSend(null, _class, sel, sender)
        }
        
    }
    
    fun initWithName_host(inputServerName: MemorySegment, hostName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithName:host:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inputServerName, hostName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithName_host(inputServerName: String, hostName: String): MemorySegment = initWithName_host(ObjCRuntime.newNSString(Arena.global(), inputServerName), ObjCRuntime.newNSString(Arena.global(), hostName))
    
    fun localizedInputManagerName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedInputManagerName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedInputManagerNameAsString(): String = ObjCRuntime.toJavaString(localizedInputManagerName())
    
    fun markedTextAbandoned(cli: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("markedTextAbandoned:")
        ObjCRuntime.msgSend(null, ptr, sel, cli)
    }
    
    fun markedTextSelectionChanged_client(newSel: NSRange, cli: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("markedTextSelectionChanged:client:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newSel, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), cli)
    }
    
    fun wantsToInterpretAllKeystrokes(): BOOL {
        val sel = ObjCRuntime.sel("wantsToInterpretAllKeystrokes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun language(): MemorySegment {
        val sel = ObjCRuntime.sel("language")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun languageAsString(): String = ObjCRuntime.toJavaString(language())
    
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun server(): MemorySegment {
        val sel = ObjCRuntime.sel("server")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun wantsToHandleMouseEvents(): BOOL {
        val sel = ObjCRuntime.sel("wantsToHandleMouseEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun handleMouseEvent(mouseEvent: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("handleMouseEvent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, mouseEvent) as BOOL
    }
    
    fun wantsToDelayTextChangeNotifications(): BOOL {
        val sel = ObjCRuntime.sel("wantsToDelayTextChangeNotifications")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

