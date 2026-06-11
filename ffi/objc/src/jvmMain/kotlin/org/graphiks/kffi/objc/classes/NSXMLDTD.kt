/**
 * Kotlin/JVM wrapper for Objective-C class: NSXMLDTD
 * Superclass: NSXMLNode
 */
open class NSXMLDTD(ptr: MemorySegment) : NSXMLNode(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSXMLDTD") }
        
        fun predefinedEntityDeclarationForName(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("predefinedEntityDeclarationForName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun predefinedEntityDeclarationForName(name: String): MemorySegment = predefinedEntityDeclarationForName(ObjCRuntime.newNSString(Arena.global(), name))
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithKind_options(kind: NSXMLNodeKind, options: NSXMLNodeOptions): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKind:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, kind, options) as MemorySegment
    }
    
    fun initWithContentsOfURL_options_error(url: MemorySegment, mask: NSXMLNodeOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfURL:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, mask, error) as MemorySegment
    }
    
    fun initWithData_options_error(`data`: MemorySegment, mask: NSXMLNodeOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, mask, error) as MemorySegment
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
    
    fun entityDeclarationForName(name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("entityDeclarationForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun entityDeclarationForName(name: String): MemorySegment = entityDeclarationForName(ObjCRuntime.newNSString(Arena.global(), name))
    
    fun notationDeclarationForName(name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("notationDeclarationForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun notationDeclarationForName(name: String): MemorySegment = notationDeclarationForName(ObjCRuntime.newNSString(Arena.global(), name))
    
    fun elementDeclarationForName(name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("elementDeclarationForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun elementDeclarationForName(name: String): MemorySegment = elementDeclarationForName(ObjCRuntime.newNSString(Arena.global(), name))
    
    fun attributeDeclarationForName_elementName(name: MemorySegment, elementName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("attributeDeclarationForName:elementName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, elementName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun attributeDeclarationForName_elementName(name: String, elementName: String): MemorySegment = attributeDeclarationForName_elementName(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), elementName))
    
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
    
}

