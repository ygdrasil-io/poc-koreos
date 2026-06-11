/**
 * Kotlin/JVM wrapper for Objective-C class: NSXMLDTDNode
 * Superclass: NSXMLNode
 */
open class NSXMLDTDNode(ptr: MemorySegment) : NSXMLNode(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSXMLDTDNode") }
        
    }
    
    fun initWithXMLString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithXMLString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithXMLString(string: String): MemorySegment = initWithXMLString(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun initWithKind_options(kind: NSXMLNodeKind, options: NSXMLNodeOptions): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKind:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, kind, options) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property DTDKind
    fun DTDKind(): NSXMLDTDNodeKind {
        val sel = ObjCRuntime.sel("DTDKind")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSXMLDTDNodeKind
    }
    fun setDTDKind(value: NSXMLDTDNodeKind) {
        val sel = ObjCRuntime.sel("setDTDKind:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property external
    fun isExternal(): BOOL {
        val sel = ObjCRuntime.sel("isExternal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property publicID
    fun publicID(): MemorySegment {
        val sel = ObjCRuntime.sel("publicID")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPublicID(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPublicID:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun publicIDAsString(): String = ObjCRuntime.toJavaString(publicID())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPublicID(value: String) = setPublicID(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property systemID
    fun systemID(): MemorySegment {
        val sel = ObjCRuntime.sel("systemID")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSystemID(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSystemID:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun systemIDAsString(): String = ObjCRuntime.toJavaString(systemID())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSystemID(value: String) = setSystemID(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property notationName
    fun notationName(): MemorySegment {
        val sel = ObjCRuntime.sel("notationName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNotationName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNotationName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun notationNameAsString(): String = ObjCRuntime.toJavaString(notationName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNotationName(value: String) = setNotationName(ObjCRuntime.newNSString(Arena.global(), value))
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _DTDKind: NSXMLDTDNodeKind
    // ivar: _name: MemorySegment
    // ivar: _notationName: MemorySegment
    // ivar: _publicID: MemorySegment
    // ivar: _systemID: MemorySegment
}

