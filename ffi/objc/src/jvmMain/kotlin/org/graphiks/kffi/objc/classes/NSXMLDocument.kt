/**
 * Kotlin/JVM wrapper for Objective-C class: NSXMLDocument
 * Superclass: NSXMLNode
 */
open class NSXMLDocument(ptr: MemorySegment) : NSXMLNode(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSXMLDocument") }
        
        fun replacementClassForClass(cls: Class): Class {
            val sel = ObjCRuntime.sel("replacementClassForClass:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cls) as Class
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithXMLString_options_error(string: MemorySegment, mask: NSXMLNodeOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithXMLString:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, mask, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithXMLString_options_error(string: String, mask: NSXMLNodeOptions, error: MemorySegment): MemorySegment = initWithXMLString_options_error(ObjCRuntime.newNSString(Arena.global(), string), mask, error)
    
    fun initWithContentsOfURL_options_error(url: MemorySegment, mask: NSXMLNodeOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfURL:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, mask, error) as MemorySegment
    }
    
    fun initWithData_options_error(`data`: MemorySegment, mask: NSXMLNodeOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, mask, error) as MemorySegment
    }
    
    fun initWithRootElement(element: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRootElement:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, element) as MemorySegment
    }
    
    fun setRootElement(root: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setRootElement:")
        ObjCRuntime.msgSend(null, ptr, sel, root)
    }
    
    fun rootElement(): MemorySegment {
        val sel = ObjCRuntime.sel("rootElement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun insertChild_atIndex(child: MemorySegment, index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("insertChild:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, child, index)
    }
    
    fun insertChildren_atIndex(children: MemorySegment, index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("insertChildren:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, children, index)
    }
    
    fun removeChildAtIndex(index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("removeChildAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun setChildren(children: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setChildren:")
        ObjCRuntime.msgSend(null, ptr, sel, children)
    }
    
    fun addChild(child: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addChild:")
        ObjCRuntime.msgSend(null, ptr, sel, child)
    }
    
    fun replaceChildAtIndex_withNode(index: NSUInteger, node: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceChildAtIndex:withNode:")
        ObjCRuntime.msgSend(null, ptr, sel, index, node)
    }
    
    fun XMLDataWithOptions(options: NSXMLNodeOptions): MemorySegment {
        val sel = ObjCRuntime.sel("XMLDataWithOptions:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options) as MemorySegment
    }
    
    fun objectByApplyingXSLT_arguments_error(xslt: MemorySegment, arguments: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectByApplyingXSLT:arguments:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, xslt, arguments, error) as MemorySegment
    }
    
    fun objectByApplyingXSLTString_arguments_error(xslt: MemorySegment, arguments: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectByApplyingXSLTString:arguments:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, xslt, arguments, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun objectByApplyingXSLTString_arguments_error(xslt: String, arguments: MemorySegment, error: MemorySegment): MemorySegment = objectByApplyingXSLTString_arguments_error(ObjCRuntime.newNSString(Arena.global(), xslt), arguments, error)
    
    fun objectByApplyingXSLTAtURL_arguments_error(xsltURL: MemorySegment, argument: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectByApplyingXSLTAtURL:arguments:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, xsltURL, argument, error) as MemorySegment
    }
    
    fun validateAndReturnError(error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("validateAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
    }
    
    // @property characterEncoding
    fun characterEncoding(): MemorySegment {
        val sel = ObjCRuntime.sel("characterEncoding")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCharacterEncoding(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCharacterEncoding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun characterEncodingAsString(): String = ObjCRuntime.toJavaString(characterEncoding())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCharacterEncoding(value: String) = setCharacterEncoding(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property version
    fun version(): MemorySegment {
        val sel = ObjCRuntime.sel("version")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setVersion(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVersion:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun versionAsString(): String = ObjCRuntime.toJavaString(version())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setVersion(value: String) = setVersion(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property standalone
    fun isStandalone(): BOOL {
        val sel = ObjCRuntime.sel("isStandalone")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setStandalone(value: BOOL) {
        val sel = ObjCRuntime.sel("setStandalone:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property documentContentKind
    fun documentContentKind(): NSXMLDocumentContentKind {
        val sel = ObjCRuntime.sel("documentContentKind")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSXMLDocumentContentKind
    }
    fun setDocumentContentKind(value: NSXMLDocumentContentKind) {
        val sel = ObjCRuntime.sel("setDocumentContentKind:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property MIMEType
    fun MIMEType(): MemorySegment {
        val sel = ObjCRuntime.sel("MIMEType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMIMEType(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMIMEType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun MIMETypeAsString(): String = ObjCRuntime.toJavaString(MIMEType())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setMIMEType(value: String) = setMIMEType(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property DTD
    fun DTD(): MemorySegment {
        val sel = ObjCRuntime.sel("DTD")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDTD(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDTD:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property XMLData
    fun XMLData(): MemorySegment {
        val sel = ObjCRuntime.sel("XMLData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _encoding: MemorySegment
    // ivar: _version: MemorySegment
    // ivar: _docType: MemorySegment
    // ivar: _children: MemorySegment
    // ivar: _childrenHaveMutated: BOOL
    // ivar: _standalone: BOOL
    // ivar: padding: MemorySegment
    // ivar: _rootElement: MemorySegment
    // ivar: _URI: MemorySegment
    // ivar: _extraIvars: MemorySegment
    // ivar: _fidelityMask: NSUInteger
    // ivar: _contentKind: NSXMLDocumentContentKind
}

